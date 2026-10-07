package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioNativeRepresentationAtlasTest {
    @Test public void qualifiedTargetedAndExperimentalFamiliesRemainDistinct(){
        int qualified=0,targeted=0,held=0;
        for(AioNativeRepresentationAtlas.Family family:AioNativeRepresentationAtlas.families()){
            switch(family.status){
                case QUALIFIED_ANDROID_CORE: qualified++; break;
                case VALIDATED_TARGETED_ANDROID_PORT_PENDING: targeted++; break;
                case EXPERIMENTAL_HELD: held++; break;
            }
            assertNotNull(family.promotionGate);
            assertFalse(family.promotionGate.isBlank());
        }
        assertTrue(qualified>=8);
        assertTrue(targeted>=3);
        assertTrue(held>=2);
    }

    @Test public void hfmsFamiliesPreservePriorValidationWithoutBecomingAndroidDefault(){
        for(AioNativeRepresentationAtlas.Family family:AioNativeRepresentationAtlas.families()){
            if(!family.name.contains("HFMS"))continue;
            assertEquals(AioNativeRepresentationAtlas.Status.VALIDATED_TARGETED_ANDROID_PORT_PENDING,family.status);
            assertNotEquals(AioNativeRepresentationAtlas.Status.QUALIFIED_ANDROID_CORE,family.status);
        }
    }

    @Test public void onlyQualifiedCoreFamiliesMayBeDefaultRuntimeMechanisms(){
        for(AioNativeRepresentationAtlas.Family family:AioNativeRepresentationAtlas.families())
            if(family.status!=AioNativeRepresentationAtlas.Status.QUALIFIED_ANDROID_CORE)
                assertTrue(family.promotionGate.toLowerCase(java.util.Locale.ROOT).contains("android"));
    }
}
