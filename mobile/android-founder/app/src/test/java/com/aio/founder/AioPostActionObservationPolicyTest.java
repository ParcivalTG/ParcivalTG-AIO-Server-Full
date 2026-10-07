package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioPostActionObservationPolicyTest {
    @Test public void tapDelayTracksDurationWithinBounds(){
        assertEquals(260L,AioPostActionObservationPolicy.delayMillis("gesture.tap",80));
        assertEquals(220L,AioPostActionObservationPolicy.delayMillis("gesture.tap",40));
        assertEquals(500L,AioPostActionObservationPolicy.delayMillis("gesture.tap",1500));
    }

    @Test public void swipeCarriesMoreTemporalMass(){
        assertEquals(570L,AioPostActionObservationPolicy.delayMillis("gesture.swipe",350));
        assertEquals(350L,AioPostActionObservationPolicy.delayMillis("gesture.swipe",40));
        assertEquals(900L,AioPostActionObservationPolicy.delayMillis("gesture.swipe",1500));
    }

    @Test public void nonGestureActionsDoNotAcquireHindsightDelay(){
        try{AioPostActionObservationPolicy.delayMillis("file.read",100);fail();}
        catch(IllegalArgumentException expected){
            assertEquals("POST_ACTION_OBSERVATION_ACTION_INVALID",expected.getMessage());
        }
    }
}
