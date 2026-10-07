package com.aio.founder;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;

public final class AndroidUpdateStatusActivity extends Activity {
    private static final String PREFS="aio_update_status_v1";

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);handle(intent);
    }

    private void handle(Intent intent){
        if(intent==null||!AndroidUpdateInstaller.ACTION_STATUS.equals(intent.getAction())){finish();return;}
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        String message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putInt("status",status)
            .putString("message",bounded(message))
            .putLong("at",System.currentTimeMillis()).apply();

        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm;
            if(Build.VERSION.SDK_INT>=33)confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);
            else confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm!=null){
                try{startActivity(confirm);}
                catch(Exception ignored){}
            }
            finish();return;
        }

        if(status==PackageInstaller.STATUS_SUCCESS){
            try{new AndroidUpdateStager(this).clear();}catch(Exception ignored){}
        }
        finish();
    }

    static String statusSummary(Activity activity){
        android.content.SharedPreferences prefs=activity.getSharedPreferences(PREFS,MODE_PRIVATE);
        if(!prefs.contains("status"))return "NO_INSTALL_ATTEMPT";
        return "status="+prefs.getInt("status",PackageInstaller.STATUS_FAILURE)+
            " | "+prefs.getString("message","")+" | at="+prefs.getLong("at",0);
    }

    private static String bounded(String value){
        if(value==null)return "";
        String clean=value.replace('\n',' ').replace('\r',' ');
        return clean.length()>256?clean.substring(0,256):clean;
    }
}
