package com.aio.founder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AioNativeCellStateCodecTest {
    private JSONArray repeatedEvidence(int count)throws Exception{
        JSONArray rows=new JSONArray();
        for(int i=0;i<count;i++){
            JSONObject row=new JSONObject();
            row.put("code","PRESENCE_READY");
            row.put("atUnixMs",1800000000000L+(i%4));
            row.put("requesterRoundTripMs",42);
            rows.put(row);
        }
        return rows;
    }

    @Test public void repeatedRowsPromoteCellPortfolioAndRoundTrip()throws Exception{
        JSONArray rows=repeatedEvidence(128);
        AioNativeCellStateCodec.Decision decision=AioNativeCellStateCodec.decide(rows);
        assertTrue(decision.cellArchive);
        assertTrue(decision.stored.startsWith("@AIOCELL1:"));
        assertEquals(rows.toString(),AioNativeCellStateCodec.decodeArray(decision.stored));
        assertTrue(decision.representationBytes<decision.logicalBytes);
    }

    @Test public void selectiveTailReadsOnlyRequestedLogicalRows()throws Exception{
        JSONArray rows=repeatedEvidence(256);
        String stored=AioNativeCellStateCodec.encodeArray(rows);
        JSONArray tail=AioNativeCellStateCodec.tail(stored,3);
        assertEquals(3,tail.length());
        assertEquals("PRESENCE_READY",tail.getJSONObject(2).getString("code"));
    }

    @Test public void heterogeneousRowsCanFallBackToWholeStatePortfolio()throws Exception{
        JSONArray rows=new JSONArray();
        java.util.Random random=new java.util.Random(771);
        for(int i=0;i<40;i++){
            byte[] bytes=new byte[180];random.nextBytes(bytes);
            rows.put(new JSONObject().put("code",java.util.Base64.getEncoder().encodeToString(bytes)));
        }
        AioNativeCellStateCodec.Decision decision=AioNativeCellStateCodec.decide(rows);
        assertEquals(rows.toString(),AioNativeCellStateCodec.decodeArray(decision.stored));
    }

    @Test public void legacyWholeStateStorageRemainsCompatible()throws Exception{
        JSONArray rows=repeatedEvidence(12);
        String old=AioNativeStateCodec.encodeForStorage(rows.toString());
        assertEquals(rows.toString(),AioNativeCellStateCodec.decodeArray(old));
    }

    @Test public void selectiveCellWitnessDetectsCorruptionWithoutFullDecode()throws Exception{
        JSONArray rows=repeatedEvidence(64);
        String stored=AioNativeCellStateCodec.encodeArray(rows);
        assertTrue(stored.startsWith("@AIOCELL1:"));
        String[] parts=stored.split(":",5);
        byte[] payload=java.util.Base64.getDecoder().decode(parts[4]);
        try{
            java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.BIG_ENDIAN);
            int count=Integer.parseInt(parts[2]);
            int offset=0,lastDigest=-1;
            for(int i=0;i<count;i++){
                buffer.position(offset);
                buffer.get();buffer.getInt();buffer.getInt();buffer.getInt();
                int length=buffer.getInt();
                lastDigest=offset+17;
                offset+=49+length;
            }
            assertTrue(lastDigest>=0&&lastDigest<payload.length);
            payload[lastDigest]^=0x01;
            String corrupt=parts[0]+":"+parts[1]+":"+parts[2]+":"+parts[3]+":"+
                java.util.Base64.getEncoder().encodeToString(payload);
            try{AioNativeCellStateCodec.tail(corrupt,1);fail();}
            catch(SecurityException expected){assertEquals("AIO_CELL_WITNESS_HASH",expected.getMessage());}
        }finally{java.util.Arrays.fill(payload,(byte)0);}
    }
}
