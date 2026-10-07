package com.aio.founder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class AndroidScreenProjectionService extends Service {
    static final String ACTION_START="com.aio.founder.SCREEN_START";
    static final String ACTION_STOP="com.aio.founder.SCREEN_STOP";
    static final String EXTRA_RESULT_CODE="resultCode";
    static final String EXTRA_RESULT_DATA="resultData";
    private static final String CHANNEL_ID="aio_screen_projection";
    private static final int NOTIFICATION_ID=47106;
    static final int MAX_FRAME_BYTES=1_500_000;

    public static final class Snapshot {
        public final String state;
        public final int width,height,densityDpi;
        public final long updatedAtMs;
        Snapshot(String state,int width,int height,int densityDpi,long updatedAtMs){
            this.state=state;this.width=width;this.height=height;this.densityDpi=densityDpi;this.updatedAtMs=updatedAtMs;
        }
    }

    public final class LocalBinder extends Binder {
        public Snapshot snapshot(){return current;}
        public byte[] captureJpeg(int quality)throws Exception{return AndroidScreenProjectionService.this.captureJpegInternal(quality,MAX_FRAME_BYTES);}
    }

    private final LocalBinder binder=new LocalBinder();
    private final Object gate=new Object();
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private volatile Snapshot current=new Snapshot("STOPPED",0,0,0,0);

    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){
            stopSession("CONSENT_REVOKED",false);
            stopSelf();
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        AndroidScreenProjectionRuntime.attach(this);
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        String action=intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopSession("FOUNDER_STOP",true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(action)){
            current=new Snapshot("HOLD_UNKNOWN_ACTION",0,0,0,System.currentTimeMillis());
            stopSelf(startId);return START_NOT_STICKY;
        }
        int resultCode=intent.getIntExtra(EXTRA_RESULT_CODE,Integer.MIN_VALUE);
        Intent data;
        if(Build.VERSION.SDK_INT>=33)data=intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent.class);
        else data=intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if(resultCode==Integer.MIN_VALUE||data==null){
            current=new Snapshot("HOLD_CONSENT_REQUIRED",0,0,0,System.currentTimeMillis());
            stopSelf(startId);return START_NOT_STICKY;
        }
        try{
            startForegroundCompat(notification("Android screen observation active","Founder-approved MediaProjection session"));
            startSession(resultCode,data);
            return START_NOT_STICKY;
        }catch(Exception failure){
            current=new Snapshot("HOLD_START_FAILURE",0,0,0,System.currentTimeMillis());
            stopSession("START_FAILURE",true);stopSelf(startId);return START_NOT_STICKY;
        }
    }

    private void startSession(int resultCode,Intent data)throws Exception{
        synchronized(gate){
            stopSessionLocked(false);
            MediaProjection next=null;
            ImageReader nextReader=null;
            VirtualDisplay nextDisplay=null;
            try{
                MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
                if(manager==null)throw new IllegalStateException("MEDIA_PROJECTION_SERVICE_UNAVAILABLE");
                next=manager.getMediaProjection(resultCode,data);
                if(next==null)throw new SecurityException("MEDIA_PROJECTION_CONSENT_DENIED");

                int[] metrics=screenMetrics();
                AndroidScreenProjectionPolicy.Size size=AndroidScreenProjectionPolicy.target(metrics[0],metrics[1]);
                int density=AndroidScreenProjectionPolicy.validateDensity(metrics[2]);

                next.registerCallback(callback,new Handler(Looper.getMainLooper()));
                nextReader=ImageReader.newInstance(size.width,size.height,PixelFormat.RGBA_8888,2);
                nextDisplay=next.createVirtualDisplay(
                    "AIO-Android-Screen",
                    size.width,size.height,density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    nextReader.getSurface(),null,null);
                if(nextDisplay==null)throw new IllegalStateException("SCREEN_VIRTUAL_DISPLAY_FAILED");

                projection=next;reader=nextReader;display=nextDisplay;
                current=new Snapshot("ACTIVE",size.width,size.height,density,System.currentTimeMillis());
                next=null;nextReader=null;nextDisplay=null;
            }finally{
                if(nextDisplay!=null)try{nextDisplay.release();}catch(Exception ignored){}
                if(nextReader!=null)try{nextReader.close();}catch(Exception ignored){}
                if(next!=null){
                    try{next.unregisterCallback(callback);}catch(Exception ignored){}
                    try{next.stop();}catch(Exception ignored){}
                }
            }
        }
    }

    private int[] screenMetrics(){
        WindowManager window=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(window==null)throw new IllegalStateException("WINDOW_MANAGER_UNAVAILABLE");
        int width,height,density;
        if(Build.VERSION.SDK_INT>=30){
            android.graphics.Rect bounds=window.getMaximumWindowMetrics().getBounds();
            width=bounds.width();height=bounds.height();
            density=getResources().getConfiguration().densityDpi;
        }else{
            DisplayMetrics metrics=new DisplayMetrics();
            window.getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels;height=metrics.heightPixels;density=metrics.densityDpi;
        }
        return new int[]{width,height,density};
    }

    Snapshot snapshotInternal(){return current;}

    byte[] captureJpegInternal(int quality,int maxBytes)throws Exception{
        if(quality<30||quality>90)throw new IllegalArgumentException("SCREEN_JPEG_QUALITY_INVALID");
        if(maxBytes<32_768||maxBytes>MAX_FRAME_BYTES)throw new IllegalArgumentException("SCREEN_FRAME_BUDGET_INVALID");
        synchronized(gate){
            if(reader==null||display==null||projection==null)throw new IllegalStateException("SCREEN_CAPTURE_NOT_ACTIVE");
            Image image=reader.acquireLatestImage();
            if(image==null)return null;
            Bitmap padded=null,cropped=null;
            try{
                Image.Plane[] planes=image.getPlanes();
                if(planes.length<1)throw new IllegalStateException("SCREEN_PLANE_MISSING");
                ByteBuffer buffer=planes[0].getBuffer();
                int pixelStride=planes[0].getPixelStride(),rowStride=planes[0].getRowStride();
                if(pixelStride<4||rowStride<current.width*pixelStride)throw new IllegalStateException("SCREEN_PLANE_LAYOUT_INVALID");
                int paddedWidth=current.width+(rowStride-pixelStride*current.width)/pixelStride;
                padded=Bitmap.createBitmap(paddedWidth,current.height,Bitmap.Config.ARGB_8888);
                padded.copyPixelsFromBuffer(buffer);
                cropped=Bitmap.createBitmap(padded,0,0,current.width,current.height);
                return encodeJpegBounded(cropped,quality,maxBytes);
            }finally{
                if(cropped!=null)cropped.recycle();
                if(padded!=null)paddingSafeRecycle(padded,cropped);
                image.close();
            }
        }
    }

    private static byte[] encodeJpegBounded(Bitmap source,int requestedQuality,int maxBytes)throws Exception{
        Bitmap working=source;
        int quality=requestedQuality;
        try{
            for(int attempt=0;attempt<12;attempt++){
                ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(maxBytes,256*1024));
                if(!working.compress(Bitmap.CompressFormat.JPEG,quality,out))
                    throw new IllegalStateException("SCREEN_JPEG_ENCODE_FAILED");
                byte[] encoded=out.toByteArray();
                int encodedBytes=encoded.length;
                if(encodedBytes<=maxBytes)return encoded;
                java.util.Arrays.fill(encoded,(byte)0);

                if(quality>30){
                    quality=Math.max(30,quality-10);
                    continue;
                }

                if(working.getWidth()<=320||working.getHeight()<=320)break;
                double ratio=Math.sqrt((double)maxBytes/(double)encodedBytes)*0.88d;
                ratio=Math.max(0.50d,Math.min(0.85d,ratio));
                int nextWidth=Math.max(320,(int)Math.floor(working.getWidth()*ratio));
                int nextHeight=Math.max(320,(int)Math.floor(working.getHeight()*ratio));
                if(nextWidth>=working.getWidth()&&nextHeight>=working.getHeight())break;
                Bitmap scaled=Bitmap.createScaledBitmap(working,nextWidth,nextHeight,true);
                if(working!=source&&!working.isRecycled())working.recycle();
                working=scaled;
                quality=Math.min(requestedQuality,55);
            }
            throw new IllegalStateException("SCREEN_FRAME_BUDGET");
        }finally{
            if(working!=source&&!working.isRecycled())working.recycle();
        }
    }

    private static void paddingSafeRecycle(Bitmap padded,Bitmap cropped){
        if(padded!=cropped&&!padded.isRecycled())padded.recycle();
    }

    private void stopSession(String reason,boolean stopForeground){
        synchronized(gate){stopSessionLocked(stopForeground);}
        current=new Snapshot(reason,0,0,0,System.currentTimeMillis());
    }

    private void stopSessionLocked(boolean removeForeground){
        VirtualDisplay priorDisplay=display;display=null;
        ImageReader priorReader=reader;reader=null;
        MediaProjection priorProjection=projection;projection=null;
        if(priorDisplay!=null)try{priorDisplay.release();}catch(Exception ignored){}
        if(priorReader!=null)try{priorReader.close();}catch(Exception ignored){}
        if(priorProjection!=null){
            try{priorProjection.unregisterCallback(callback);}catch(Exception ignored){}
            try{priorProjection.stop();}catch(Exception ignored){}
        }
        if(removeForeground){
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }

    private void ensureChannel(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,"AIO screen observation",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Founder-approved Android screen observation session");
        manager.createNotificationChannel(channel);
    }

    private Notification notification(String title,String text){
        Intent open=new Intent(this,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,1,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL_ID);
        return builder.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(title).setContentText(text).setContentIntent(content)
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).setShowWhen(false).build();
    }

    private void startForegroundCompat(Notification notification){
        if(Build.VERSION.SDK_INT>=34)
            startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION_ID,notification);
    }

    @Override public IBinder onBind(Intent intent){return binder;}

    @Override public void onDestroy(){
        AndroidScreenProjectionRuntime.detach(this);
        stopSession("DESTROYED",true);
        super.onDestroy();
    }
}
