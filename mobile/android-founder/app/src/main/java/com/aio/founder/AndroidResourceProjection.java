package com.aio.founder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.StatFs;

final class AndroidResourceProjection {
    static final class Snapshot {
        final int batteryPercent,thermalStatus;
        final boolean charging,validatedNetwork;
        final long availableMemoryBytes,totalMemoryBytes,availableStorageBytes,totalStorageBytes,capturedAtMs;
        final String contributionState;
        Snapshot(int batteryPercent,boolean charging,int thermalStatus,boolean validatedNetwork,
                 long availableMemoryBytes,long totalMemoryBytes,long availableStorageBytes,long totalStorageBytes,
                 long capturedAtMs,String contributionState){
            this.batteryPercent=batteryPercent;this.charging=charging;this.thermalStatus=thermalStatus;
            this.validatedNetwork=validatedNetwork;this.availableMemoryBytes=availableMemoryBytes;this.totalMemoryBytes=totalMemoryBytes;
            this.availableStorageBytes=availableStorageBytes;this.totalStorageBytes=totalStorageBytes;
            this.capturedAtMs=capturedAtMs;this.contributionState=contributionState;
        }
        String summary(){
            return "ANDROID RESOURCE NODE"+
                "\nBattery: "+batteryPercent+"% / charging="+charging+
                "\nThermal status: "+thermalStatus+
                "\nMemory: "+availableMemoryBytes+" available / "+totalMemoryBytes+" total"+
                "\nApp storage volume: "+availableStorageBytes+" available / "+totalStorageBytes+" total"+
                "\nValidated network: "+validatedNetwork+
                "\nResource contribution admission: "+contributionState+
                "\nRemote resource contribution: NOT_CONNECTED";
        }
    }

    static Snapshot capture(Context context){
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        int percent=(level>=0&&scale>0)?Math.round(level*100f/scale):-1;
        int status=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        boolean charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL;

        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        int thermal=Build.VERSION.SDK_INT>=29&&power!=null?power.getCurrentThermalStatus():-1;

        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ActivityManager activity=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(activity!=null)activity.getMemoryInfo(memory);

        StatFs fs=new StatFs(context.getFilesDir().getAbsolutePath());
        long availableStorage=fs.getAvailableBytes(),totalStorage=fs.getTotalBytes();

        boolean validated=false;
        ConnectivityManager connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivity!=null){
            Network network=connectivity.getActiveNetwork();
            NetworkCapabilities caps=network==null?null:connectivity.getNetworkCapabilities(network);
            validated=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                &&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        String admission=AndroidResourcePolicy.contributionState(charging,thermal,memory.availMem);
        return new Snapshot(percent,charging,thermal,validated,memory.availMem,memory.totalMem,
            availableStorage,totalStorage,System.currentTimeMillis(),admission);
    }
}
