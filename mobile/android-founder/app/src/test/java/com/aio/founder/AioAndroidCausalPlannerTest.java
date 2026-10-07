package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioAndroidCausalPlannerTest {
    @Test public void eachCurrentTypedActionHasMinimalDependencyCone(){
        for(AndroidCapabilityCatalog.Spec spec:AndroidCapabilityCatalog.all().values()){
            if(!spec.remoteEnabled)continue;
            for(String action:spec.remoteActions){
                java.util.Set<AioAndroidCausalPlanner.Dependency> plan=AioAndroidCausalPlanner.plan(action);
                assertFalse(action,plan.isEmpty());
                assertEquals(action,1,plan.size());
            }
        }
    }

    @Test public void screenDoesNotManifestFileResourceGestureOrClipboardDependencies(){
        java.util.Set<AioAndroidCausalPlanner.Dependency> plan=AioAndroidCausalPlanner.plan("screen.capture");
        assertEquals(java.util.Set.of(AioAndroidCausalPlanner.Dependency.SCREEN_SESSION),plan);
    }

    @Test public void fileWriteDoesNotMeasureScreenOrResources(){
        java.util.Set<AioAndroidCausalPlanner.Dependency> plan=AioAndroidCausalPlanner.plan("file.write");
        assertEquals(java.util.Set.of(AioAndroidCausalPlanner.Dependency.SAF_TREE),plan);
    }

    @Test public void theoreticalDependencyNonManifestationIsFiveSixthsForCurrentActions(){
        AioAndroidCausalPlanner.Prepared p=new AioAndroidCausalPlanner.Prepared(
            java.util.Set.of(AioAndroidCausalPlanner.Dependency.RESOURCE_SNAPSHOT),null,null);
        assertEquals(1,p.manifestedDependencies());
        assertEquals(5,p.unmanifestedDependencies());
        assertEquals(5.0/6.0,p.nonManifestationFraction(),0.000001);
    }

    @Test public void unknownActionHasNoImplicitBroadFallback(){
        try{AioAndroidCausalPlanner.plan("shell.execute");fail();}
        catch(IllegalArgumentException expected){assertEquals("ANDROID_ACTION_UNSUPPORTED",expected.getMessage());}
    }
}
