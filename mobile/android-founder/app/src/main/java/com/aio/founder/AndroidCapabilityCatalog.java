package com.aio.founder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class AndroidCapabilityCatalog {
    static final class Spec {
        final AioAndroidNode.Capability capability;
        final AioAndroidNode.Tier minimumTier;
        final Set<String> remoteActions;
        final String prerequisite;
        final long defaultGrantMs;
        final long maxGrantMs;
        final boolean biometricRecommended;
        final boolean remoteEnabled;

        Spec(AioAndroidNode.Capability capability,AioAndroidNode.Tier minimumTier,
             Set<String> remoteActions,String prerequisite,long defaultGrantMs,long maxGrantMs,
             boolean biometricRecommended,boolean remoteEnabled){
            this.capability=capability;this.minimumTier=minimumTier;
            this.remoteActions=Collections.unmodifiableSet(new LinkedHashSet<>(remoteActions));
            this.prerequisite=prerequisite;this.defaultGrantMs=defaultGrantMs;this.maxGrantMs=maxGrantMs;
            this.biometricRecommended=biometricRecommended;this.remoteEnabled=remoteEnabled;
        }
    }

    private static final EnumMap<AioAndroidNode.Capability,Spec> SPECS=
        new EnumMap<>(AioAndroidNode.Capability.class);

    static {
        put(AioAndroidNode.Capability.RESOURCE_STATUS,AioAndroidNode.Tier.OBSERVE,
            Set.of("resource.status"),"NONE",15*60_000L,30*60_000L,false,true);
        put(AioAndroidNode.Capability.RESOURCE_CONTRIBUTE,AioAndroidNode.Tier.DATA,
            Set.of("resource.sha256","resource.deflate"),"LIVE_RESOURCE_ADMISSION",15*60_000L,30*60_000L,false,true);
        put(AioAndroidNode.Capability.SCREEN_OBSERVE,AioAndroidNode.Tier.OBSERVE,
            Set.of("screen.capture"),"FRESH_MEDIA_PROJECTION_SESSION",5*60_000L,10*60_000L,true,true);
        put(AioAndroidNode.Capability.GESTURE_INPUT,AioAndroidNode.Tier.INTERACT,
            Set.of("gesture.tap","gesture.swipe"),"FOUNDER_ENABLED_ACCESSIBILITY_SERVICE",5*60_000L,10*60_000L,true,true);
        put(AioAndroidNode.Capability.CLIPBOARD,AioAndroidNode.Tier.DATA,
            Set.of("clipboard.read","clipboard.write"),"AIO_ACTIVITY_FOREGROUND_VISIBLE",5*60_000L,10*60_000L,true,true);
        put(AioAndroidNode.Capability.NOTIFICATIONS,AioAndroidNode.Tier.INTERACT,
            Set.of("notification.post"),"POST_NOTIFICATIONS_PERMISSION_WHEN_REQUIRED",15*60_000L,30*60_000L,false,true);
        put(AioAndroidNode.Capability.FILE_READ,AioAndroidNode.Tier.DATA,
            Set.of("file.list","file.read","file.sha256"),"FOUNDER_SELECTED_SAF_TREE",15*60_000L,30*60_000L,false,true);
        put(AioAndroidNode.Capability.FILE_WRITE,AioAndroidNode.Tier.DATA,
            Set.of("file.write","file.rename","file.delete"),"FOUNDER_SELECTED_SAF_TREE",5*60_000L,10*60_000L,true,true);
        put(AioAndroidNode.Capability.PACKAGE_STAGE,AioAndroidNode.Tier.UPDATE,
            Set.of(),"LOCAL_FOUNDER_ONLY",0L,0L,true,false);

        if(!SPECS.keySet().equals(EnumSet.allOf(AioAndroidNode.Capability.class)))
            throw new ExceptionInInitializerError("ANDROID_CAPABILITY_CATALOG_INCOMPLETE");
    }

    private AndroidCapabilityCatalog(){}

    private static void put(AioAndroidNode.Capability capability,AioAndroidNode.Tier tier,
                            Set<String> actions,String prerequisite,long defaultGrantMs,long maxGrantMs,
                            boolean biometricRecommended,boolean remoteEnabled){
        if(SPECS.containsKey(capability))throw new IllegalStateException("ANDROID_CAPABILITY_DUPLICATE");
        SPECS.put(capability,new Spec(capability,tier,actions,prerequisite,
            defaultGrantMs,maxGrantMs,biometricRecommended,remoteEnabled));
    }

    static Spec spec(AioAndroidNode.Capability capability){
        Spec spec=SPECS.get(capability);
        if(spec==null)throw new IllegalArgumentException("ANDROID_CAPABILITY_UNKNOWN");
        return spec;
    }

    static boolean supports(AioAndroidNode.Capability capability,String action){
        Spec spec=spec(capability);
        return spec.remoteEnabled&&spec.remoteActions.contains(action);
    }

    static Set<AioAndroidNode.Capability> remoteCapabilities(){
        EnumSet<AioAndroidNode.Capability> out=EnumSet.noneOf(AioAndroidNode.Capability.class);
        for(Map.Entry<AioAndroidNode.Capability,Spec> row:SPECS.entrySet())
            if(row.getValue().remoteEnabled)out.add(row.getKey());
        return Collections.unmodifiableSet(out);
    }

    static void validateGrant(AioAndroidNode.Capability capability,long ttlMs){
        Spec spec=spec(capability);
        if(!spec.remoteEnabled)throw new SecurityException("ANDROID_CAPABILITY_LOCAL_ONLY");
        if(ttlMs<=0||ttlMs>spec.maxGrantMs)throw new IllegalArgumentException("ANDROID_GRANT_TTL_INVALID");
    }

    static Map<AioAndroidNode.Capability,Spec> all(){
        return Collections.unmodifiableMap(new EnumMap<>(SPECS));
    }
}
