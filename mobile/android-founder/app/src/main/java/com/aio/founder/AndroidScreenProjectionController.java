package com.aio.founder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;

final class AndroidScreenProjectionController {
    private AndroidScreenProjectionController(){}

    static Intent consentIntent(Activity activity){
        MediaProjectionManager manager=(MediaProjectionManager)activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if(manager==null)throw new IllegalStateException("MEDIA_PROJECTION_SERVICE_UNAVAILABLE");
        return manager.createScreenCaptureIntent();
    }

    static void startAfterConsent(Context context,int resultCode,Intent resultData){
        if(resultData==null)throw new IllegalArgumentException("MEDIA_PROJECTION_CONSENT_REQUIRED");
        Intent service=new Intent(context,AndroidScreenProjectionService.class)
            .setAction(AndroidScreenProjectionService.ACTION_START)
            .putExtra(AndroidScreenProjectionService.EXTRA_RESULT_CODE,resultCode)
            .putExtra(AndroidScreenProjectionService.EXTRA_RESULT_DATA,resultData);
        context.startForegroundService(service);
    }

    static void stop(Context context){
        context.stopService(new Intent(context,AndroidScreenProjectionService.class));
    }
}
