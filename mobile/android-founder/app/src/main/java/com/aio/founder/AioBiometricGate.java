package com.aio.founder;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;

import java.util.concurrent.Executor;

/**
 * Android biometric APIs are a device-bound proof projection into AIO identity state.
 */
final class AioBiometricGate {
    interface Callback {
        void onAccepted();
        void onRejected(String reason);
    }

    static void authenticate(Activity activity, String title, Callback callback) {
        Executor executor = activity.getMainExecutor();
        CancellationSignal cancel = new CancellationSignal();
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle("Unlock AIO Founder identity")
                    .setDescription("Use your device biometric to authorize this AIO session.")
                    .setNegativeButton("Cancel", executor, (dialog, which) ->
                            callback.onRejected("FOUNDER_CANCELLED"))
                    .build();

            prompt.authenticate(cancel, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    callback.onAccepted();
                }
                @Override public void onAuthenticationFailed() {
                    // Non-match is an attempt, not a terminal authorization decision.
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    callback.onRejected("BIOMETRIC_ERROR_" + errorCode);
                }
            });
        } catch (Exception failure) {
            callback.onRejected("BIOMETRIC_UNAVAILABLE");
        }
    }
}
