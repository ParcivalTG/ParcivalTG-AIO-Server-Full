package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidBackgroundReadinessTest {
    @Test public void samsungDetectionIsCaseInsensitive(){
        assertTrue(AndroidBackgroundReadiness.isSamsung("samsung"));
        assertTrue(AndroidBackgroundReadiness.isSamsung("SAMSUNG"));
        assertTrue(AndroidBackgroundReadiness.isSamsung(" Samsung "));
    }

    @Test public void otherManufacturersAreNotSamsung(){
        assertFalse(AndroidBackgroundReadiness.isSamsung("Google"));
        assertFalse(AndroidBackgroundReadiness.isSamsung("OnePlus"));
        assertFalse(AndroidBackgroundReadiness.isSamsung(null));
    }
}
