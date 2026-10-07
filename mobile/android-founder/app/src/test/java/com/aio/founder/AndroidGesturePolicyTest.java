package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidGesturePolicyTest {
    @Test public void normalizedCoordinatesMapToSurface(){
        AndroidGesturePolicy.Point p=AndroidGesturePolicy.pixels(500,250,1001,2001);
        assertEquals(500,p.x);assertEquals(500,p.y);
    }
    @Test public void edgesRemainInsideSurface(){
        AndroidGesturePolicy.Point p=AndroidGesturePolicy.pixels(1000,1000,1080,2400);
        assertEquals(1079,p.x);assertEquals(2399,p.y);
    }
    @Test public void invalidCoordinateRejected(){
        try{AndroidGesturePolicy.pixels(1001,0,100,100);fail();}
        catch(IllegalArgumentException expected){assertEquals("GESTURE_COORD_INVALID",expected.getMessage());}
    }
    @Test public void durationIsBounded(){
        assertEquals(250,AndroidGesturePolicy.duration(250));
        try{AndroidGesturePolicy.duration(5000);fail();}
        catch(IllegalArgumentException expected){assertEquals("GESTURE_DURATION_INVALID",expected.getMessage());}
    }
}
