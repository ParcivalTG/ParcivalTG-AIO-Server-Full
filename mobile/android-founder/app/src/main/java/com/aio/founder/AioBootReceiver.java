package com.aio.founder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restores the persistent AIO remote-messaging node only when the Founder
 * explicitly left it enabled before reboot or package replacement.
 */
public final class AioBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(context==null||intent==null)return;
        try{
            AioPersistentNodeController.restoreFromSystemEvent(
                context.getApplicationContext(),intent.getAction());
        }catch(Exception ignored){
            // Fail closed. MainActivity readiness exposes that the node is not running;
            // no retry storm is scheduled from a broadcast receiver.
        }
    }
}
