package com.aio.founder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.EnumSet;
import java.util.Set;

/**
 * Android endpoint causal light-cone planner.
 *
 * A typed action manifests only the platform dependencies capable of affecting
 * that action. Unrelated Android subsystems remain unmeasured/unmaterialized.
 */
final class AioAndroidCausalPlanner {
    enum Dependency {
        RESOURCE_SNAPSHOT,
        SCREEN_SESSION,
        GESTURE_SERVICE,
        FOREGROUND_VISIBILITY,
        NOTIFICATION_PERMISSION,
        SAF_TREE
    }

    static final class Prepared {
        final Set<Dependency> dependencies;
        final AndroidResourceProjection.Snapshot resources;
        final AndroidScreenProjectionService.Snapshot screen;
        final int totalDependencyUniverse;

        Prepared(Set<Dependency> dependencies,AndroidResourceProjection.Snapshot resources,
                 AndroidScreenProjectionService.Snapshot screen){
            this.dependencies=Set.copyOf(dependencies);this.resources=resources;this.screen=screen;
            this.totalDependencyUniverse=Dependency.values().length;
        }

        int manifestedDependencies(){return dependencies.size();}
        int unmanifestedDependencies(){return totalDependencyUniverse-dependencies.size();}
        double nonManifestationFraction(){
            return totalDependencyUniverse==0?0.0:
                (double)unmanifestedDependencies()/(double)totalDependencyUniverse;
        }
    }

    private AioAndroidCausalPlanner(){}

    static Set<Dependency> plan(String action){
        switch(action){
            case "resource.status":
            case "resource.sha256":
            case "resource.deflate":
                return EnumSet.of(Dependency.RESOURCE_SNAPSHOT);
            case "screen.capture":
                return EnumSet.of(Dependency.SCREEN_SESSION);
            case "gesture.tap":
            case "gesture.swipe":
                return EnumSet.of(Dependency.GESTURE_SERVICE);
            case "clipboard.read":
            case "clipboard.write":
                return EnumSet.of(Dependency.FOREGROUND_VISIBILITY);
            case "notification.post":
                return EnumSet.of(Dependency.NOTIFICATION_PERMISSION);
            case "file.list":
            case "file.read":
            case "file.sha256":
            case "file.write":
            case "file.rename":
            case "file.delete":
                return EnumSet.of(Dependency.SAF_TREE);
            default:
                throw new IllegalArgumentException("ANDROID_ACTION_UNSUPPORTED");
        }
    }

    static Prepared prepare(Context context,AndroidCapabilityBroker files,String action){
        Set<Dependency> dependencies=plan(action);
        AndroidResourceProjection.Snapshot resources=null;
        AndroidScreenProjectionService.Snapshot screen=null;

        if(dependencies.contains(Dependency.RESOURCE_SNAPSHOT))
            resources=AndroidResourceProjection.capture(context);

        if(dependencies.contains(Dependency.SCREEN_SESSION)){
            screen=AndroidScreenProjectionRuntime.snapshot();
            if(screen==null||!"ACTIVE".equals(screen.state))
                throw new SecurityException("SCREEN_CAPTURE_NOT_ACTIVE");
        }

        if(dependencies.contains(Dependency.GESTURE_SERVICE)&&!AndroidGestureRuntime.available())
            throw new SecurityException("GESTURE_SERVICE_NOT_ENABLED");

        if(dependencies.contains(Dependency.FOREGROUND_VISIBILITY))
            AndroidClipboardPolicy.requireForeground(AioAppVisibility.isForegroundVisible());

        if(dependencies.contains(Dependency.NOTIFICATION_PERMISSION)&&
            Build.VERSION.SDK_INT>=33&&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("NOTIFICATION_PERMISSION_REQUIRED");

        if(dependencies.contains(Dependency.SAF_TREE)&&!files.hasStorageGrant())
            throw new SecurityException("STORAGE_TREE_NOT_GRANTED");

        AioNativeRuntimeTelemetry.recordCausal(dependencies.size(),Dependency.values().length);
        return new Prepared(dependencies,resources,screen);
    }
}
