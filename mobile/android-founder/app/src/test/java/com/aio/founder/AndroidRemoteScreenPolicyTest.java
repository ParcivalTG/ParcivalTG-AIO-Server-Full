package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidRemoteScreenPolicyTest {
    @Test public void defaultQualityIsBounded(){
        assertEquals(55,AndroidRemoteScreenPolicy.validateQuality(AndroidRemoteScreenPolicy.DEFAULT_QUALITY));
    }
    @Test public void excessiveQualityRejected(){
        try{AndroidRemoteScreenPolicy.validateQuality(90);fail();}
        catch(IllegalArgumentException expected){assertEquals("SCREEN_REMOTE_QUALITY_INVALID",expected.getMessage());}
    }
    @Test public void remoteFrameBudgetFitsReplyEnvelope(){
        assertTrue(((AndroidRemoteScreenPolicy.MAX_JPEG_BYTES+2)/3)*4 < AndroidCapabilityProtocol.MAX_REPLY_BYTES);
    }
}
