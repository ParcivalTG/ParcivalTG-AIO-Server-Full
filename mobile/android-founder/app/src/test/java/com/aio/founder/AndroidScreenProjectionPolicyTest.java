package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidScreenProjectionPolicyTest {
    @Test public void phonePortraitIsBounded(){
        AndroidScreenProjectionPolicy.Size s=AndroidScreenProjectionPolicy.target(1440,3120);
        assertTrue(s.width<=AndroidScreenProjectionPolicy.MAX_WIDTH);
        assertTrue(s.height<=AndroidScreenProjectionPolicy.MAX_HEIGHT);
        assertTrue((long)s.width*s.height<=AndroidScreenProjectionPolicy.MAX_PIXELS);
        assertTrue(s.height>s.width);
    }

    @Test public void smallScreenIsNotUpscaled(){
        AndroidScreenProjectionPolicy.Size s=AndroidScreenProjectionPolicy.target(800,600);
        assertEquals(800,s.width);assertEquals(600,s.height);
    }

    @Test public void absurdScreenRejected(){
        try{AndroidScreenProjectionPolicy.target(0,1080);fail();}
        catch(IllegalArgumentException expected){assertEquals("SCREEN_SIZE_INVALID",expected.getMessage());}
    }

    @Test public void densityIsBounded(){
        assertEquals(420,AndroidScreenProjectionPolicy.validateDensity(420));
        try{AndroidScreenProjectionPolicy.validateDensity(2000);fail();}
        catch(IllegalArgumentException expected){assertEquals("SCREEN_DENSITY_INVALID",expected.getMessage());}
    }
}
