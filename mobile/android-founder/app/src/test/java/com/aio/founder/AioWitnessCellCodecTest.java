package com.aio.founder;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class AioWitnessCellCodecTest {
    private static JSONObject representative()throws Exception{
        return new JSONObject()
            .put("code","ANDROID_CAPABILITY_ALLOWED")
            .put("atUnixMs",1_800_000_000_000L)
            .put("requestId","11111111-2222-3333-4444-555555555555")
            .put("peerId","windows-founder-r5")
            .put("sourceSessionEpoch",1007L)
            .put("capability","SCREEN_OBSERVE")
            .put("action","screen.capture")
            .put("resultCode","OK")
            .put("manifestedDependencies",2)
            .put("unmanifestedDependencies",4)
            .put("dependencyUniverse",6)
            .put("nonManifestationPermille",667);
    }

    @Test public void representativeEvidenceRoundTripsExactly()throws Exception{
        JSONObject row=representative();
        String stored=AioWitnessCellCodec.encode(row);
        JSONObject decoded=AioWitnessCellCodec.decode(stored);
        java.util.LinkedHashSet<String> sourceKeys=new java.util.LinkedHashSet<>();
        java.util.Iterator<String> sourceIt=row.keys();
        while(sourceIt.hasNext())sourceKeys.add(sourceIt.next());
        java.util.LinkedHashSet<String> decodedKeys=new java.util.LinkedHashSet<>();
        java.util.Iterator<String> decodedIt=decoded.keys();
        while(decodedIt.hasNext())decodedKeys.add(decodedIt.next());
        assertEquals(sourceKeys,decodedKeys);
        for(String key:sourceKeys)
            assertEquals(String.valueOf(row.get(key)),String.valueOf(decoded.get(key)));
    }

    @Test public void commonEvidenceCellIsMuchSmallerThanJson()throws Exception{
        JSONObject row=representative();
        String raw=row.toString();
        String stored=AioWitnessCellCodec.encode(row);
        assertTrue(AioWitnessCellCodec.isCell(stored));
        assertTrue(stored.length()*2<raw.length());
    }

    @Test public void nestedEvidenceFallsBackToCompatibilityJson()throws Exception{
        JSONObject row=new JSONObject()
            .put("code","NESTED_TEST")
            .put("result",new JSONObject().put("ok",true));
        String stored=AioWitnessCellCodec.encode(row);
        assertFalse(AioWitnessCellCodec.isCell(stored));
        assertEquals(row.toString(),stored);
        assertEquals(true,AioWitnessCellCodec.decode(stored).getJSONObject("result").getBoolean("ok"));
    }

    @Test public void corruptionFailsClosed()throws Exception{
        String stored=AioWitnessCellCodec.encode(representative());
        assertTrue(AioWitnessCellCodec.isCell(stored));
        byte[] packet=java.util.Base64.getUrlDecoder().decode(
            stored.substring("@AIOW1:".length()));
        try{
            packet[Math.max(2,packet.length/2)]^=1;
            String corrupt="@AIOW1:"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(packet);
            try{
                AioWitnessCellCodec.decode(corrupt);
                fail();
            }catch(SecurityException expected){
                assertEquals("WITNESS_CELL_CRC",expected.getMessage());
            }
        }finally{
            java.util.Arrays.fill(packet,(byte)0);
        }
    }
}
