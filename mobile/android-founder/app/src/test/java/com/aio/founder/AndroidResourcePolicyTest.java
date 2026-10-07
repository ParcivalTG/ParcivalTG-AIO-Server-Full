package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidResourcePolicyTest {
    @Test public void requiresCharging(){
        assertEquals("CHARGING_REQUIRED",AndroidResourcePolicy.contributionState(false,0,1024L*1024L*1024L));
    }
    @Test public void thermalHoldWinsWhenCharging(){
        assertEquals("THERMAL_HOLD",AndroidResourcePolicy.contributionState(true,4,1024L*1024L*1024L));
    }
    @Test public void memoryHoldIsBounded(){
        assertEquals("MEMORY_HOLD",AndroidResourcePolicy.contributionState(true,0,128L*1024L*1024L));
    }
    @Test public void eligibleRemainsLocalOnly(){
        assertEquals("ELIGIBLE_LOCAL_ONLY",AndroidResourcePolicy.contributionState(true,0,1024L*1024L*1024L));
    }
}
