package com.aio.founder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

final class AndroidNotificationBroker {
    private static final String CHANNEL_ID="aio_remote_notifications";
    private static final AtomicInteger NEXT_ID=new AtomicInteger(48000);
    private final Context context;
    private final NotificationManager manager;
    private final ArrayDeque<Long> timestamps=new ArrayDeque<>();

    AndroidNotificationBroker(Context context){
        this.context=context.getApplicationContext();
        this.manager=(NotificationManager)this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)throw new IllegalStateException("NOTIFICATION_SERVICE_UNAVAILABLE");
        ensureChannel();
    }

    synchronized int post(String title,String text){
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("NOTIFICATION_PERMISSION_REQUIRED");
        String safeTitle=AndroidNotificationPolicy.title(title);
        String safeText=AndroidNotificationPolicy.text(text);
        AndroidNotificationPolicy.admit(timestamps,System.currentTimeMillis());

        Intent open=new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,2,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(context,CHANNEL_ID);
        Notification notification=builder
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(safeTitle)
            .setContentText(safeText)
            .setStyle(new Notification.BigTextStyle().bigText(safeText))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build();
        int id=NEXT_ID.updateAndGet(value->value==Integer.MAX_VALUE?48000:value+1);
        manager.notify(id,notification);
        return id;
    }

    private void ensureChannel(){
        NotificationChannel channel=new NotificationChannel(
            CHANNEL_ID,"AIO remote messages",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Founder-authorized notifications from the paired AIO Windows node");
        manager.createNotificationChannel(channel);
    }
}
