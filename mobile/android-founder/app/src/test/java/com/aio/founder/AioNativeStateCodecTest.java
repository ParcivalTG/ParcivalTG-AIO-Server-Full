package com.aio.founder;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class AioNativeStateCodecTest {
    private static String historyFixture(int rows){
        StringBuilder out=new StringBuilder("[");
        for(int i=0;i<rows;i++){
            if(i>0)out.append(',');
            out.append("{\"role\":\"Founder\",\"provider\":\"AIO\",\"privacyClass\":\"LOCAL_ONLY\",")
                .append("\"intentId\":\"intent-").append(i).append("\",\"requestId\":\"req-").append(i).append("\",")
                .append("\"state\":\"VERIFIED_RECEIPT\",\"timestampUnixMs\":").append(1800000000000L+i)
                .append(",\"text\":\"bounded AIO objective ").append(i).append("\"}");
        }
        return out.append(']').toString();
    }

    @Test public void structuralHistoryRoundTripsAndActuallyWins()throws Exception{
        String raw=historyFixture(200);
        AioNativeStateCodec.Decision decision=AioNativeStateCodec.decide(raw);
        assertTrue(decision.transformed);
        assertEquals(AioNativeStateCodec.Kind.STRUCTURAL_DEFLATE,decision.kind);
        assertTrue(decision.stored.length()<raw.length());
        assertEquals(raw,AioNativeStateCodec.decodeFromStorage(decision.stored));
    }

    @Test public void periodicStateUsesGeneratorWhenItWins()throws Exception{
        String raw="AIO_NATIVE_CELL|".repeat(512);
        AioNativeStateCodec.Decision decision=AioNativeStateCodec.decide(raw);
        assertTrue(decision.transformed);
        assertEquals(AioNativeStateCodec.Kind.REPEAT_GENERATOR,decision.kind);
        assertEquals(raw,AioNativeStateCodec.decodeFromStorage(decision.stored));
    }

    @Test public void mirrorRepresentationRoundTripsWhenSelected()throws Exception{
        String left="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(40);
        String raw=left+new StringBuilder(left).reverse();
        AioNativeStateCodec.Decision decision=AioNativeStateCodec.decide(raw);
        assertEquals(raw,AioNativeStateCodec.decodeFromStorage(decision.stored));
        assertTrue(decision.stored.length()<=raw.length());
    }

    @Test public void highEntropyLikeTextNeverExpandsStoredCharacters()throws Exception{
        Random random=new Random(0xA10);
        StringBuilder raw=new StringBuilder();
        for(int i=0;i<50000;i++)raw.append((char)(33+random.nextInt(94)));
        AioNativeStateCodec.Decision decision=AioNativeStateCodec.decide(raw.toString());
        assertTrue(decision.stored.length()<=raw.length());
        assertEquals(raw.toString(),AioNativeStateCodec.decodeFromStorage(decision.stored));
    }

    @Test public void sensorRoutedHfr1CanWinWholeStatePortfolio()throws Exception{
        int object=AioHfmsResidualCodec.DEFAULT_OBJECT_BYTES;
        int count=8;
        java.util.Random random=new java.util.Random(20260928);
        byte[] base=new byte[object];
        for(int i=0;i<base.length;i++)base[i]=(byte)(32+random.nextInt(64));
        byte[] all=new byte[object*count];
        System.arraycopy(base,0,all,0,object);
        for(int n=1;n<count;n++){
            int mask=(n*3)&31;
            for(int i=0;i<object;i++)all[n*object+i]=(byte)(base[i]^mask);
        }
        String raw=new String(all,java.nio.charset.StandardCharsets.US_ASCII);
        AioNativeStateCodec.Decision decision=AioNativeStateCodec.decide(raw);
        assertTrue(decision.transformed);
        assertEquals(AioNativeStateCodec.Kind.HFMS_HFR1_RESIDUAL,decision.kind);
        assertEquals(raw,AioNativeStateCodec.decodeFromStorage(decision.stored));
        java.util.Arrays.fill(base,(byte)0);java.util.Arrays.fill(all,(byte)0);
    }

    @Test public void highEntropyTextDoesNotPayHfr1SpecialistCost()throws Exception{
        java.util.Random random=new java.util.Random(441);
        StringBuilder raw=new StringBuilder();
        for(int i=0;i<AioHfmsResidualCodec.DEFAULT_OBJECT_BYTES*4;i++)
            raw.append((char)(33+random.nextInt(94)));
        byte[] bytes=raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(AioHfmsResidualCodec.sensorEligible(bytes));
        java.util.Arrays.fill(bytes,(byte)0);
        AioNativeStateCodec.Decision decision=AioNativeStateCodec.decide(raw.toString());
        assertNotEquals(AioNativeStateCodec.Kind.HFMS_HFR1_RESIDUAL,decision.kind);
    }

    @Test public void corruptionFailsClosed()throws Exception{
        String raw=historyFixture(100);
        String stored=AioNativeStateCodec.encodeForStorage(raw);
        assertTrue(stored.startsWith("@AIOREP1:"));
        char replacement=stored.charAt(stored.length()-2)=='A'?'B':'A';
        String corrupt=stored.substring(0,stored.length()-2)+replacement+stored.substring(stored.length()-1);
        try{AioNativeStateCodec.decodeFromStorage(corrupt);fail();}
        catch(Exception expected){assertTrue(expected instanceof SecurityException||expected instanceof java.util.zip.DataFormatException);}
    }

    @Test public void legacyRawTextIsBackwardCompatible()throws Exception{
        String raw="legacy plaintext projection";
        assertEquals(raw,AioNativeStateCodec.decodeFromStorage(raw));
    }
}
