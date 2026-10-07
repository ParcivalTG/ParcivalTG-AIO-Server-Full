package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioPersistentNodePolicyTest {
    @Test public void explicitStartAlwaysStarts(){
        assertEquals(AioPersistentNodePolicy.Decision.START,
            AioPersistentNodePolicy.decide(AioPersistentNodePolicy.ACTION_START,false));
    }

    @Test public void explicitStopAlwaysStops(){
        assertEquals(AioPersistentNodePolicy.Decision.STOP,
            AioPersistentNodePolicy.decide(AioPersistentNodePolicy.ACTION_STOP,true));
    }

    @Test public void stickyRestartRequiresFounderOptIn(){
        assertEquals(AioPersistentNodePolicy.Decision.START,AioPersistentNodePolicy.decide(null,true));
        assertEquals(AioPersistentNodePolicy.Decision.STOP,AioPersistentNodePolicy.decide(null,false));
    }

    @Test public void systemRestoreRequiresFounderOptIn(){
        assertEquals(AioPersistentNodePolicy.Decision.START,
            AioPersistentNodePolicy.decide(AioPersistentNodePolicy.ACTION_RESTORE,true));
        assertEquals(AioPersistentNodePolicy.Decision.STOP,
            AioPersistentNodePolicy.decide(AioPersistentNodePolicy.ACTION_RESTORE,false));
    }

    @Test public void unknownActionFailsClosed(){
        assertEquals(AioPersistentNodePolicy.Decision.REJECT,
            AioPersistentNodePolicy.decide("unexpected",true));
    }

    @Test public void heartbeatIsBounded(){
        assertTrue(AioPersistentNodePolicy.heartbeatMillis()>=10_000L);
        assertTrue(AioPersistentNodePolicy.heartbeatMillis()<=60_000L);
    }
}
