package com.aio.founder;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

final class AndroidBackgroundSettingsController {
    private AndroidBackgroundSettingsController(){}

    static void open(Activity activity){
        if(AndroidBackgroundReadiness.isSamsung(android.os.Build.MANUFACTURER)){
            Intent samsung=new Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY");
            samsung.setPackage("com.samsung.android.lool");
            samsung.putExtra("activity_type",2);
            if(samsung.resolveActivity(activity.getPackageManager())!=null){
                activity.startActivity(samsung);
                return;
            }
        }
        activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }
}
