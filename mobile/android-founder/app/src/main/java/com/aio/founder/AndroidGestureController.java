package com.aio.founder;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

final class AndroidGestureController {
    private AndroidGestureController(){}
    static void openAccessibilitySettings(Activity activity){
        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
    static boolean available(){return AndroidGestureRuntime.available();}
}
