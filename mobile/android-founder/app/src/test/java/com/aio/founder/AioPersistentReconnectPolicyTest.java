package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioPersistentReconnectPolicyTest {
    @Test public void backoffIsBoundedAndMonotonic(){
        long prior=-1;
        for(int i=0;i<20;i++){
            long value=AioPersistentReconnectPolicy.delayMillis(i);
            assertTrue(value>=prior);assertTrue(value<=30000L);prior=value;
        }
    }
    @Test public void firstReconnectIsImmediate(){
        assertEquals(0L,AioPersistentReconnectPolicy.delayMillis(0));
    }
    @Test public void invalidAttemptRejected(){
        try{AioPersistentReconnectPolicy.delayMillis(-1);fail();}
        catch(IllegalArgumentException expected){assertEquals("RECONNECT_ATTEMPT_INVALID",expected.getMessage());}
    }
}
