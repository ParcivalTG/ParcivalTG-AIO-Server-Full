package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidResourceWorkerTest {
    @Test public void sha256IsDeterministic()throws Exception{
        String input=Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AndroidResourceWorker.sha256(input));
    }

    @Test public void deflateRoundTripsBoundedInput()throws Exception{
        byte[] raw="AIO-AIO-AIO-AIO-AIO".getBytes(StandardCharsets.UTF_8);
        String input=Base64.getEncoder().encodeToString(raw);
        String compressed=AndroidResourceWorker.deflate(input,6);
        byte[] restored=AndroidResourceWorker.inflateForCourt(compressed,AndroidResourceWorker.MAX_INPUT_BYTES);
        try{assertArrayEquals(raw,restored);}
        finally{java.util.Arrays.fill(restored,(byte)0);java.util.Arrays.fill(raw,(byte)0);}
    }

    @Test public void oversizeInputRejected(){
        byte[] raw=new byte[AndroidResourceWorker.MAX_INPUT_BYTES+1];
        String input=Base64.getEncoder().encodeToString(raw);
        try{AndroidResourceWorker.sha256(input);fail();}
        catch(Exception expected){assertEquals("RESOURCE_INPUT_BUDGET",expected.getMessage());}
    }

    @Test public void invalidLevelRejected(){
        try{AndroidResourceWorker.deflate("YQ==",10);fail();}
        catch(Exception expected){assertEquals("RESOURCE_DEFLATE_LEVEL_INVALID",expected.getMessage());}
    }
}
