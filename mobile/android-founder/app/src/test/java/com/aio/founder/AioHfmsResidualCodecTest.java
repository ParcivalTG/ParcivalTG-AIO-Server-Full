package com.aio.founder;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class AioHfmsResidualCodecTest {
    @Test public void matchesPreservedPythonHfr1GoldenVector()throws Exception{
        byte[] base=new byte[16];for(int i=0;i<16;i++)base[i]=(byte)i;
        byte[] second=new byte[16];for(int i=0;i<16;i++)second[i]=(byte)(base[i]^5);
        byte[] residual=new byte[]{9,8,7,6,9,8,7,6,9,8,7,6,9,8,7,6};
        byte[] third=new byte[16];for(int i=0;i<16;i++)third[i]=(byte)(base[i]^residual[i]);
        byte[] source=new byte[48];
        System.arraycopy(base,0,source,0,16);System.arraycopy(second,0,source,16,16);System.arraycopy(third,0,source,32,16);

        AioHfmsResidualCodec.Encoded encoded=AioHfmsResidualCodec.encode(source,16,4,1);
        String expected="4846523110000000040000000300000001000000000102030405060708090a0b0c0d0e0f020804000000000400010e0f00000105040000010504000001050400000105040100010904010001090401000109040100010904";
        assertEquals(expected,hex(encoded.stream));
        assertArrayEquals(source,AioHfmsResidualCodec.decode(encoded.stream));
        assertEquals(2,encoded.uniqueClasses);
        assertEquals(8,encoded.occurrences);
    }

    @Test public void validatedFineCollisionStyleStructureTriggersSensorAndCompresses()throws Exception{
        int object=AioHfmsResidualCodec.DEFAULT_OBJECT_BYTES;
        int count=8;
        byte[] base=new byte[object];
        new Random(20260928).nextBytes(base);
        byte[] source=new byte[object*count];
        System.arraycopy(base,0,source,0,object);
        for(int n=1;n<count;n++){
            for(int i=0;i<object;i++){
                int block=i/64;
                int pattern=(block+n)%4;
                int value=(pattern*37+(block/4)*13+9*n)&0xff;
                source[n*object+i]=(byte)(base[i]^value);
            }
        }
        assertTrue(AioHfmsResidualCodec.sensorEligible(source));
        AioHfmsResidualCodec.Encoded encoded=AioHfmsResidualCodec.encodeDefault(source);
        assertArrayEquals(source,AioHfmsResidualCodec.decode(encoded.stream));
        assertTrue(encoded.stream.length<source.length);
    }

    @Test public void randomStateDoesNotTriggerExpensiveSpecialist(){
        byte[] source=new byte[AioHfmsResidualCodec.DEFAULT_OBJECT_BYTES*8];
        new Random(77).nextBytes(source);
        assertFalse(AioHfmsResidualCodec.sensorEligible(source));
    }

    @Test public void truncationFailsClosed()throws Exception{
        byte[] source=new byte[64];
        for(int i=0;i<source.length;i++)source[i]=(byte)(i%16);
        AioHfmsResidualCodec.Encoded encoded=AioHfmsResidualCodec.encode(source,16,4,1);
        byte[] truncated=Arrays.copyOf(encoded.stream,encoded.stream.length-1);
        try{AioHfmsResidualCodec.decode(truncated);fail();}
        catch(SecurityException expected){assertTrue(expected.getMessage().startsWith("HFMS_HFR1_"));}
    }

    private static String hex(byte[] bytes){
        StringBuilder out=new StringBuilder();
        for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
        return out.toString();
    }
}
