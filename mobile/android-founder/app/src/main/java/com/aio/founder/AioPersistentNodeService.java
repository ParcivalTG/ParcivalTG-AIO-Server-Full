package com.aio.founder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AioPersistentNodeService extends Service {
    private static final String CHANNEL_ID="aio_node_remote_messaging";
    private static final int NOTIFICATION_ID=47105;

    public static final class Snapshot {
        public final String state;
        public final long updatedAtMs;
        public final String resourceSummary;
        Snapshot(String state,long updatedAtMs,String resourceSummary){
            this.state=state;this.updatedAtMs=updatedAtMs;this.resourceSummary=resourceSummary;
        }
    }

    public final class LocalBinder extends Binder {
        public Snapshot snapshot(){return current;}
        public void adoptCloudLink(CloudPresenceTransport cloud){
            AioPersistentNodeService.this.adoptCloudLink(cloud);
        }
        public void peerVerified(CloudPresenceTransport expected){
            AioPersistentNodeService.this.peerVerified(expected);
        }
        public boolean releaseCloudLink(CloudPresenceTransport expected){
            return AioPersistentNodeService.this.releaseExpectedCloudLink(expected,true,"FOUNDER_RELEASE");
        }
        public boolean returnCloudLinkToActivity(CloudPresenceTransport expected){
            return AioPersistentNodeService.this.releaseExpectedCloudLink(expected,false,"FOUNDER_RETURN");
        }
        public CloudPresenceTransport cloudLink(){synchronized(linkGate){return cloudLink;}}
        public boolean remoteCapabilityEligible(){return state.remoteCapabilityEligible();}
    }

    private final LocalBinder binder=new LocalBinder();
    private final AioPersistentNodeState state=new AioPersistentNodeState();
    private final Object linkGate=new Object();
    private ScheduledExecutorService worker;
    private ExecutorService linkWorker;
    private CloudPresenceTransport cloudLink;
    private long adoptedRemoteSessionEpoch;
    private int reconnectAttempt;
    private long reconnectGeneration;
    private boolean reconnectScheduled;
    private AioFounderStateStore auditStore;
    private volatile boolean auditReady;
    private volatile Snapshot current=new Snapshot("STOPPED",0,"NOT_MEASURED");
    private volatile boolean running;

    @Override public void onCreate(){
        super.onCreate();
        try{
            auditStore=AioAndroidRuntime.get(this).stateStore();
            auditStore.initialize();
            auditReady=true;
        }catch(Exception failure){
            auditReady=false;
            current=new Snapshot("HOLD_AUDIT_STATE",System.currentTimeMillis(),failure.getClass().getSimpleName());
        }
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        boolean enabled=getSharedPreferences(AioPersistentNodePolicy.PREFS,MODE_PRIVATE).getBoolean(AioPersistentNodePolicy.KEY_ENABLED,false);
        String action=intent==null?null:intent.getAction();
        AioPersistentNodePolicy.Decision decision=AioPersistentNodePolicy.decide(action,enabled);
        if(decision==AioPersistentNodePolicy.Decision.REJECT){
            current=new Snapshot("HOLD_UNKNOWN_ACTION",System.currentTimeMillis(),current.resourceSummary);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if(decision==AioPersistentNodePolicy.Decision.STOP){
            setEnabled(false);
            stopNode();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        setEnabled(true);
        if(AioPersistentNodePolicy.ACTION_START.equals(action))state.founderStart();
        else state.stickyRestart(true);
        startNode();
        if(action==null)scheduleReconnect("PROCESS_RESTART");
        else if(AioPersistentNodePolicy.ACTION_RESTORE.equals(action))
            scheduleReconnect("SYSTEM_RESTORE");
        else if(AioPersistentNodePolicy.ACTION_START.equals(action)){
            ScheduledExecutorService active=worker;
            if(active!=null&&!active.isShutdown())
                active.schedule(()->scheduleReconnect("FOUNDER_START"),1500,TimeUnit.MILLISECONDS);
        }
        return START_STICKY;
    }

    private synchronized void startNode(){
        if(running){
            updateSnapshot();
            return;
        }
        running=true;
        if(worker==null||worker.isShutdown())worker=Executors.newSingleThreadScheduledExecutor();
        startForegroundCompat(notification("AIO Android node active","Persistent process active; remote link and capabilities require fresh verification."));
        refreshFromState("MEASURING");
        worker.scheduleWithFixedDelay(this::updateSnapshot,0,AioPersistentNodePolicy.heartbeatMillis(),TimeUnit.MILLISECONDS);
    }

    private void updateSnapshot(){
        if(!running)return;
        try{
            AndroidResourceProjection.Snapshot resources=AndroidResourceProjection.capture(this);
            AioPersistentNodeState.Snapshot node=state.snapshot();
            current=new Snapshot(node.phase.name(),System.currentTimeMillis(),resources.summary());
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(manager!=null)manager.notify(NOTIFICATION_ID,
                notification("AIO Android node active",
                    "State: "+state.snapshot().phase+" | Resource admission: "+resources.contributionState));
        }catch(Exception failure){
            AioPersistentNodeState.Snapshot node=state.snapshot();
            current=new Snapshot(node.phase.name(),System.currentTimeMillis(),
                "RESOURCE_NOT_MEASURED:"+failure.getClass().getSimpleName());
        }
    }

    private synchronized void stopNode(){
        running=false;
        releaseCloudLink(true,"NODE_STOP");
        ScheduledExecutorService active=worker;if(active!=null)active.shutdownNow();
        worker=null;
        reconnectAttempt=0;reconnectGeneration++;reconnectScheduled=false;
        state.founderStop();
        current=new Snapshot("STOPPED",System.currentTimeMillis(),current.resourceSummary);
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void adoptCloudLink(CloudPresenceTransport cloud){
        if(cloud==null)throw new IllegalArgumentException("NODE_LINK_DEPENDENCY");
        synchronized(linkGate){
            if(!running)throw new IllegalStateException("NODE_NOT_ACTIVE");
            if(cloudLink!=null&&cloudLink!=cloud)releaseCloudLinkLocked(true,"LINK_REPLACED");
            reconnectGeneration++;reconnectScheduled=false;
            cloudLink=cloud;
            adoptedRemoteSessionEpoch=cloud.remoteSessionEpoch();
            state.linkAttached();refreshFromState();
            if(linkWorker==null||linkWorker.isShutdown())linkWorker=Executors.newSingleThreadExecutor();
            final CloudPresenceTransport adopted=cloud;
            final AndroidCapabilityDispatcher dispatcher=AioAndroidRuntime.get(this).dispatcher();
            linkWorker.execute(()->serveNodeLink(adopted,dispatcher));
        }
    }

    private void serveNodeLink(CloudPresenceTransport cloud,AndroidCapabilityDispatcher dispatcher){
        while(running&&isCurrentLink(cloud)){
            PresenceProtocol.Frame frame=null;byte[] reply=null;
            try{
                frame=cloud.pollNodeRequest(1000);
                if(frame==null)continue;
                long observedEpoch=cloud.remoteSessionEpoch();
                if(observedEpoch!=adoptedRemoteSessionEpoch){
                    adoptedRemoteSessionEpoch=observedEpoch;
                    try{state.linkAttached();}catch(Exception ignored){}
                    refreshFromState();
                }
                AndroidCapabilityDispatcher.Result result;
                if(!state.remoteCapabilityEligible()){
                    result=deniedResult("UNVERIFIED","UNVERIFIED","REMOTE_PEER_NOT_VERIFIED");
                }else if(!auditReady||auditStore==null){
                    result=deniedResult("UNKNOWN","UNKNOWN","AUDIT_STATE_UNAVAILABLE");
                }else{
                    AndroidCapabilityProtocol.Request preview=null;
                    try{
                        preview=AndroidCapabilityProtocol.parse(frame.payload);
                        auditStore.appendCapabilityAttempt(
                            frame.id,cloud.remotePeerId(),cloud.remoteSessionEpoch(),
                            preview.capability.name(),preview.action);
                        result=dispatcher.dispatch(cloud.remotePeerId(),frame.id,frame.payload);
                    }catch(Exception auditOrParseFailure){
                        if(preview==null)
                            result=dispatcher.dispatch(cloud.remotePeerId(),frame.id,frame.payload);
                        else{
                            auditReady=false;
                            result=deniedResult(preview.capability.name(),preview.action,"AUDIT_STATE_UNAVAILABLE");
                        }
                    }
                }
                if(auditReady&&auditStore!=null){
                    try{
                        auditStore.appendCapabilityResult(
                            frame.id,cloud.remotePeerId(),cloud.remoteSessionEpoch(),result);
                    }catch(Exception auditFailure){
                        auditReady=false;
                        current=new Snapshot(state.snapshot().phase.name(),System.currentTimeMillis(),
                            "AUDIT_RESULT_HOLD:"+auditFailure.getClass().getSimpleName());
                    }
                }
                reply=result.payload;
                cloud.sendNodeReply(frame.id,result.accepted,reply);
            }catch(Exception failure){
                if(running&&isCurrentLink(cloud)){
                    try{state.linkLost();}catch(Exception ignored){}
                    refreshFromState();
                    releaseCloudLink(false,"LINK_FAILURE");
                    scheduleReconnect("LINK_FAILURE");
                }
                break;
            }finally{
                if(frame!=null&&frame.payload!=null)Arrays.fill(frame.payload,(byte)0);
                if(reply!=null)Arrays.fill(reply,(byte)0);
            }
        }
    }

    private static AndroidCapabilityDispatcher.Result deniedResult(String capability,String action,String code){
        return new AndroidCapabilityDispatcher.Result(
            false,
            AndroidCapabilityProtocol.reply(false,code,null),
            capability,action,code,0,AioAndroidCausalPlanner.Dependency.values().length);
    }

    private void scheduleReconnect(String reason){
        synchronized(linkGate){
            if(!running||cloudLink!=null||reconnectScheduled)return;
            reconnectScheduled=true;
            long generation=reconnectGeneration;
            long delay=AioPersistentReconnectPolicy.delayMillis(reconnectAttempt);
            ScheduledExecutorService active=worker;
            if(active==null||active.isShutdown()){reconnectScheduled=false;return;}
            active.schedule(()->attemptReconnect(reason,generation),delay,TimeUnit.MILLISECONDS);
        }
    }

    private void attemptReconnect(String reason,long generation){
        synchronized(linkGate){
            if(generation!=reconnectGeneration)return;
            reconnectScheduled=false;
            if(!running||cloudLink!=null)return;
        }
        boolean enabled=getSharedPreferences(AioPersistentNodePolicy.PREFS,MODE_PRIVATE)
            .getBoolean(AioPersistentNodePolicy.KEY_ENABLED,false);
        if(!enabled)return;
        CloudPresenceTransport rebuilt=null;
        try{
            rebuilt=AioPersistentCloudBootstrap.connect(this);
            reconnectAttempt=0;
            adoptCloudLink(rebuilt);
            current=new Snapshot(state.snapshot().phase.name(),System.currentTimeMillis(),
                "R5_RECONNECTED_UNVERIFIED:"+reason);
        }catch(Exception failure){
            if(rebuilt!=null)try{rebuilt.close();}catch(Exception ignored){}
            reconnectAttempt=Math.min(reconnectAttempt+1,1000);
            current=new Snapshot(state.snapshot().phase.name(),System.currentTimeMillis(),
                "R5_RECONNECT_HOLD:"+failure.getClass().getSimpleName());
            scheduleReconnect("RETRY");
        }
    }

    private boolean isCurrentLink(CloudPresenceTransport cloud){
        synchronized(linkGate){return cloudLink==cloud;}
    }

    private void peerVerified(CloudPresenceTransport expected){
        synchronized(linkGate){
            if(expected==null||cloudLink!=expected)throw new IllegalStateException("NODE_LINK_REPLACED");
            state.peerVerified();refreshFromState();
        }
    }

    private boolean releaseExpectedCloudLink(CloudPresenceTransport expected,boolean close,String reason){
        synchronized(linkGate){
            if(expected==null||cloudLink!=expected)return false;
            releaseCloudLinkLocked(close,reason);return true;
        }
    }

    private void releaseCloudLink(boolean close,String reason){
        synchronized(linkGate){releaseCloudLinkLocked(close,reason);}
    }

    private void releaseCloudLinkLocked(boolean close,String reason){
        CloudPresenceTransport prior=cloudLink;cloudLink=null;adoptedRemoteSessionEpoch=0;
        reconnectGeneration++;reconnectScheduled=false;
        ExecutorService link=linkWorker;linkWorker=null;if(link!=null)link.shutdownNow();
        if(prior!=null&&close)try{prior.close();}catch(Exception ignored){}
        if(running&&state.snapshot().phase!=AioPersistentNodeState.Phase.ACTIVE_NO_LINK){
            try{state.linkLost();}catch(Exception ignored){}
        }
        refreshFromState();
    }

    private void refreshFromState(){
        refreshFromState(current.resourceSummary);
    }

    private void refreshFromState(String resourceSummary){
        AioPersistentNodeState.Snapshot node=state.snapshot();
        current=new Snapshot(node.phase.name(),System.currentTimeMillis(),resourceSummary);
    }

    private void setEnabled(boolean enabled){
        getSharedPreferences(AioPersistentNodePolicy.PREFS,MODE_PRIVATE).edit().putBoolean(AioPersistentNodePolicy.KEY_ENABLED,enabled).apply();
    }

    private void ensureChannel(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(
            CHANNEL_ID,"AIO Android node",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Persistent AIO remote-messaging endpoint status");
        manager.createNotificationChannel(channel);
    }

    private Notification notification(String title,String text){
        Intent open=new Intent(this,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL_ID);
        return builder.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build();
    }

    private void startForegroundCompat(Notification notification){
        if(Build.VERSION.SDK_INT>=34)
            startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        else
            startForeground(NOTIFICATION_ID,notification);
    }

    @Override public IBinder onBind(Intent intent){return binder;}

    @Override public void onDestroy(){
        running=false;
        releaseCloudLink(true,"SERVICE_DESTROY");
        ScheduledExecutorService active=worker;if(active!=null)active.shutdownNow();
        worker=null;
        current=new Snapshot("DESTROYED",System.currentTimeMillis(),current.resourceSummary);
        super.onDestroy();
    }
}
