package com.aio.founder;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

final class AndroidUpdateInstaller {
    static final String ACTION_STATUS="com.aio.founder.UPDATE_STATUS";
    static final String EXTRA_SESSION_ID="aioSessionId";

    private AndroidUpdateInstaller(){}

    static String requestInstall(Activity activity,AndroidUpdateStager stager,AndroidUpdateStager.StageResult verified)throws Exception{
        PackageManager pm=activity.getPackageManager();
        if(!pm.canRequestPackageInstalls()){
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+activity.getPackageName()));
            activity.startActivity(settings);
            return "INSTALL_SOURCE_PERMISSION_REQUIRED";
        }

        File apk=stager.pendingFile();
        if(!apk.isFile()||apk.length()!=verified.bytes)throw new IllegalStateException("UPDATE_STAGED_FILE_CHANGED");

        PackageInstaller installer=pm.getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        params.setSize(apk.length());
        if(Build.VERSION.SDK_INT>=31)
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);

        int sessionId=installer.createSession(params);
        PackageInstaller.Session session=null;
        boolean committed=false;
        try{
            session=installer.openSession(sessionId);
            try(FileInputStream in=new FileInputStream(apk);
                OutputStream out=session.openWrite("base.apk",0,apk.length())){
                byte[] buffer=new byte[64*1024];
                try{
                    for(int read;(read=in.read(buffer))>=0;){
                        if(read==0)continue;
                        out.write(buffer,0,read);
                    }
                    session.fsync(out);
                }finally{java.util.Arrays.fill(buffer,(byte)0);}
            }

            Intent status=new Intent(activity,AndroidUpdateStatusActivity.class)
                .setAction(ACTION_STATUS)
                .putExtra(EXTRA_SESSION_ID,sessionId);
            int pendingFlags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)pendingFlags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent receiver=PendingIntent.getActivity(activity,sessionId,status,pendingFlags);
            session.commit(receiver.getIntentSender());
            committed=true;
            return "INSTALL_SESSION_COMMITTED:"+sessionId;
        }finally{
            if(session!=null)try{session.close();}catch(Exception ignored){}
            if(!committed)try{installer.abandonSession(sessionId);}catch(Exception ignored){}
        }
    }
}
