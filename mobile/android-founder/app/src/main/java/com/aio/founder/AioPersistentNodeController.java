package com.aio.founder;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

final class AioPersistentNodeController {
    private AioPersistentNodeController(){}

    static boolean reconcileUserRequestedStop(Context context){
        if(Build.VERSION.SDK_INT<30)return false;
        try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager==null)return false;
            List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,1);
            if(exits==null||exits.isEmpty())return false;
            ApplicationExitInfo latest=exits.get(0);
            if(latest.getReason()!=ApplicationExitInfo.REASON_USER_REQUESTED)return false;
            android.content.SharedPreferences prefs=context.getSharedPreferences(
                AioPersistentNodePolicy.PREFS,Context.MODE_PRIVATE);
            long timestamp=latest.getTimestamp();
            long handled=prefs.getLong("handled_user_stop_timestamp",0);
            if(timestamp<=handled)return false;
            prefs.edit()
                .putLong("handled_user_stop_timestamp",timestamp)
                .putBoolean(AioPersistentNodePolicy.KEY_ENABLED,false).apply();
            return true;
        }catch(Exception ignored){return false;}
    }

    static boolean founderEnabled(Context context){
        return context.getSharedPreferences(AioPersistentNodePolicy.PREFS,Context.MODE_PRIVATE)
            .getBoolean(AioPersistentNodePolicy.KEY_ENABLED,false);
    }

    static void startFromVisibleFounder(Context context){
        Intent intent=new Intent(context,AioPersistentNodeService.class)
            .setAction(AioPersistentNodePolicy.ACTION_START);
        context.startForegroundService(intent);
    }

    static void stop(Context context){
        context.getSharedPreferences(AioPersistentNodePolicy.PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean(AioPersistentNodePolicy.KEY_ENABLED,false).apply();
        context.stopService(new Intent(context,AioPersistentNodeService.class));
    }
}
