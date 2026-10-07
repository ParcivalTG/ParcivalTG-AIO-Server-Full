package com.aio.founder;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AndroidGestureAccessibilityService extends AccessibilityService {
    private final Handler main=new Handler(Looper.getMainLooper());

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        AndroidGestureRuntime.attach(this);
    }

    @Override public boolean onUnbind(android.content.Intent intent){
        AndroidGestureRuntime.detach(this);
        return super.onUnbind(intent);
    }

    @Override public void onDestroy(){
        AndroidGestureRuntime.detach(this);
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}

    boolean tap(int xPermille,int yPermille,long durationMs)throws Exception{
        int[] size=surface();
        AndroidGesturePolicy.Point p=AndroidGesturePolicy.pixels(xPermille,yPermille,size[0],size[1]);
        long duration=AndroidGesturePolicy.duration(durationMs);
        Path path=new Path();path.moveTo(p.x,p.y);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,duration);
        return dispatch(new GestureDescription.Builder().addStroke(stroke).build());
    }

    boolean swipe(int x1,int y1,int x2,int y2,long durationMs)throws Exception{
        int[] size=surface();
        AndroidGesturePolicy.Point a=AndroidGesturePolicy.pixels(x1,y1,size[0],size[1]);
        AndroidGesturePolicy.Point b=AndroidGesturePolicy.pixels(x2,y2,size[0],size[1]);
        long duration=AndroidGesturePolicy.duration(durationMs);
        Path path=new Path();path.moveTo(a.x,a.y);path.lineTo(b.x,b.y);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,duration);
        return dispatch(new GestureDescription.Builder().addStroke(stroke).build());
    }

    private boolean dispatch(GestureDescription gesture)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("GESTURE_DISPATCH_MAIN_THREAD");
        CountDownLatch done=new CountDownLatch(1);
        AtomicBoolean completed=new AtomicBoolean(false),success=new AtomicBoolean(false);
        main.post(()->{
            boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription description){
                    success.set(true);completed.set(true);done.countDown();
                }
                @Override public void onCancelled(GestureDescription description){
                    completed.set(true);done.countDown();
                }
            },main);
            if(!accepted){completed.set(true);done.countDown();}
        });
        if(!done.await(2,TimeUnit.SECONDS))throw new IllegalStateException("GESTURE_RESULT_TIMEOUT");
        return completed.get()&&success.get();
    }

    private int[] surface(){
        WindowManager window=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(window==null)throw new IllegalStateException("GESTURE_WINDOW_MANAGER_UNAVAILABLE");
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.graphics.Rect bounds=window.getMaximumWindowMetrics().getBounds();
            return new int[]{bounds.width(),bounds.height()};
        }
        DisplayMetrics metrics=new DisplayMetrics();
        window.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels,metrics.heightPixels};
    }
}
