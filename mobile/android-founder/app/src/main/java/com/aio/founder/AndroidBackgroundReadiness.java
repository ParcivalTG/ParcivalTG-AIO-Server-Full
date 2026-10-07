package com.aio.founder;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

final class AndroidBackgroundReadiness {
    static final class Snapshot {
        final String manufacturer,model;
        final boolean samsung,backgroundRestricted,ignoringBatteryOptimizations;
        Snapshot(String manufacturer,String model,boolean samsung,boolean backgroundRestricted,boolean ignoringBatteryOptimizations){
            this.manufacturer=manufacturer;this.model=model;this.samsung=samsung;
            this.backgroundRestricted=backgroundRestricted;
            this.ignoringBatteryOptimizations=ignoringBatteryOptimizations;
        }
        String summary(){
            return "BACKGROUND READINESS"+
                "\nDevice: "+manufacturer+" "+model+
                "\nAndroid background restricted: "+backgroundRestricted+
                "\nBattery optimization exemption: "+ignoringBatteryOptimizations+
                (samsung?"\nGalaxy guidance: add AIO to Never sleeping apps for persistent-node reliability.":"");
        }
    }

    private AndroidBackgroundReadiness(){}

    static Snapshot capture(Context context){
        ActivityManager activity=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        boolean restricted=activity!=null&&activity.isBackgroundRestricted();
        boolean exempt=power!=null&&power.isIgnoringBatteryOptimizations(context.getPackageName());
        String manufacturer=Build.MANUFACTURER==null?"UNKNOWN":Build.MANUFACTURER;
        String model=Build.MODEL==null?"UNKNOWN":Build.MODEL;
        return new Snapshot(manufacturer,model,isSamsung(manufacturer),restricted,exempt);
    }

    static boolean isSamsung(String manufacturer){
        return manufacturer!=null&&"samsung".equalsIgnoreCase(manufacturer.trim());
    }
}
