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
    @Test public void foveatedRegionMapsPermilleToPixels(){
        AndroidRemoteScreenPolicy.Region region=
            AndroidRemoteScreenPolicy.region(250,100,750,900);
        assertEquals(250,region.x1);
        assertEquals(100,region.y1);
        assertEquals(750,region.x2);
        assertEquals(900,region.y2);
        assertEquals(250,AndroidRemoteScreenPolicy.pixelStart(region.x1,1000));
        assertEquals(750,AndroidRemoteScreenPolicy.pixelEndExclusive(region.x2,1000));
        assertFalse(region.full());
    }

    @Test public void fullRegionIsCanonical(){
        AndroidRemoteScreenPolicy.Region region=
            AndroidRemoteScreenPolicy.region(0,0,1000,1000);
        assertTrue(region.full());
        assertEquals(0,AndroidRemoteScreenPolicy.pixelStart(0,1440));
        assertEquals(1440,AndroidRemoteScreenPolicy.pixelEndExclusive(1000,1440));
    }

    @Test public void emptyOrInvertedRegionRejected(){
        try{AndroidRemoteScreenPolicy.region(500,100,500,900);fail();}
        catch(IllegalArgumentException expected){assertEquals("SCREEN_REGION_INVALID",expected.getMessage());}
        try{AndroidRemoteScreenPolicy.region(700,100,600,900);fail();}
        catch(IllegalArgumentException expected){assertEquals("SCREEN_REGION_INVALID",expected.getMessage());}
    }

    @Test public void remoteFrameBudgetFitsReplyEnvelope(){
        assertTrue(((AndroidRemoteScreenPolicy.MAX_JPEG_BYTES+2)/3)*4 < AndroidCapabilityProtocol.MAX_REPLY_BYTES);
    }
}
