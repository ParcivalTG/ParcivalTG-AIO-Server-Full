package com.aio.founder;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidCapabilityCatalogTest {
    @Test public void everyCapabilityHasExactlyOneSpec(){
        assertEquals(java.util.EnumSet.allOf(AioAndroidNode.Capability.class),
            AndroidCapabilityCatalog.all().keySet());
    }

    @Test public void everyRemoteActionIsUniqueAndProtocolVisible(){
        Set<String> actions=new HashSet<>();
        for(AndroidCapabilityCatalog.Spec spec:AndroidCapabilityCatalog.all().values()){
            if(!spec.remoteEnabled){
                assertTrue(spec.remoteActions.isEmpty());
                continue;
            }
            assertFalse(spec.remoteActions.isEmpty());
            assertTrue(spec.defaultGrantMs>0);
            assertTrue(spec.defaultGrantMs<=spec.maxGrantMs);
            for(String action:spec.remoteActions){
                assertTrue(actions.add(action));
                assertTrue(AndroidCapabilityProtocol.supported(spec.capability,action));
            }
        }
    }

    @Test public void packageStageIsLocalFounderOnly(){
        AndroidCapabilityCatalog.Spec spec=AndroidCapabilityCatalog.spec(AioAndroidNode.Capability.PACKAGE_STAGE);
        assertFalse(spec.remoteEnabled);
        assertEquals("LOCAL_FOUNDER_ONLY",spec.prerequisite);
        try{AndroidCapabilityCatalog.validateGrant(AioAndroidNode.Capability.PACKAGE_STAGE,1000);fail();}
        catch(SecurityException expected){assertEquals("ANDROID_CAPABILITY_LOCAL_ONLY",expected.getMessage());}
    }

    @Test public void sensitiveCapabilitiesRequireShortBoundedGrants(){
        for(AioAndroidNode.Capability capability:new AioAndroidNode.Capability[]{
            AioAndroidNode.Capability.SCREEN_OBSERVE,
            AioAndroidNode.Capability.GESTURE_INPUT,
            AioAndroidNode.Capability.CLIPBOARD,
            AioAndroidNode.Capability.FILE_WRITE}){
            AndroidCapabilityCatalog.Spec spec=AndroidCapabilityCatalog.spec(capability);
            assertTrue(spec.biometricRecommended);
            assertTrue(spec.maxGrantMs<=10*60_000L);
        }
    }

    @Test public void oversizedGrantTtlFailsClosed(){
        try{AndroidCapabilityCatalog.validateGrant(AioAndroidNode.Capability.RESOURCE_STATUS,31*60_000L);fail();}
        catch(IllegalArgumentException expected){assertEquals("ANDROID_GRANT_TTL_INVALID",expected.getMessage());}
    }
}
