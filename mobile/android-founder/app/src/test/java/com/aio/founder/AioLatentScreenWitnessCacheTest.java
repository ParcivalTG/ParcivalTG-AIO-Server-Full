package com.aio.founder;

import org.junit.Test;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class AioLatentScreenWitnessCacheTest {
    private static AndroidCapabilityProtocol.Request request(String args)throws Exception{
        String json="{\"schema\":\"aio.android.capability.request.v1\","+
            "\"capability\":\"SCREEN_OBSERVE\",\"action\":\"screen.capture\","+
            "\"privacyClass\":\"FOUNDER_ONLY\",\"args\":"+args+"}";
        return AndroidCapabilityProtocol.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void sameRegionRecallsPriorWitnessWithoutImageState()throws Exception{
        AioLatentScreenWitnessCache cache=new AioLatentScreenWitnessCache(4);
        AndroidCapabilityProtocol.Request first=request(
            "{\"quality\":45,\"maxBytes\":120000,\"x1\":100,\"y1\":200,\"x2\":900,\"y2\":800}");
        String sha="ab".repeat(32);
        assertFalse(cache.inject(first));
        cache.remember(first,sha);
        assertEquals(1,cache.size());

        AndroidCapabilityProtocol.Request second=request(
            "{\"quality\":45,\"maxBytes\":120000,\"x1\":100,\"y1\":200,\"x2\":900,\"y2\":800}");
        assertTrue(cache.inject(second));
        assertEquals(sha,AndroidCapabilityProtocol.string(second.args,"previousSha256",64));
    }

    @Test public void differentProjectionRegimeDoesNotBorrowWitness()throws Exception{
        AioLatentScreenWitnessCache cache=new AioLatentScreenWitnessCache(4);
        AndroidCapabilityProtocol.Request first=request("{\"quality\":45,\"maxBytes\":120000}");
        cache.remember(first,"cd".repeat(32));
        AndroidCapabilityProtocol.Request changed=request("{\"quality\":55,\"maxBytes\":120000}");
        assertFalse(cache.inject(changed));
    }

    @Test public void callerSuppliedWitnessWins()throws Exception{
        AioLatentScreenWitnessCache cache=new AioLatentScreenWitnessCache(4);
        AndroidCapabilityProtocol.Request first=request("{\"quality\":45,\"maxBytes\":120000}");
        cache.remember(first,"ef".repeat(32));
        AndroidCapabilityProtocol.Request explicit=request(
            "{\"quality\":45,\"maxBytes\":120000,\"previousSha256\":\""+ "11".repeat(32)+"\"}");
        assertFalse(cache.inject(explicit));
        assertEquals("11".repeat(32),
            AndroidCapabilityProtocol.string(explicit.args,"previousSha256",64));
    }

    @Test public void lruBoundPreventsUnboundedLatentState()throws Exception{
        AioLatentScreenWitnessCache cache=new AioLatentScreenWitnessCache(2);
        AndroidCapabilityProtocol.Request a=request("{\"quality\":45,\"maxBytes\":120000,\"x1\":0,\"y1\":0,\"x2\":500,\"y2\":500}");
        AndroidCapabilityProtocol.Request b=request("{\"quality\":45,\"maxBytes\":120000,\"x1\":500,\"y1\":0,\"x2\":1000,\"y2\":500}");
        AndroidCapabilityProtocol.Request c=request("{\"quality\":45,\"maxBytes\":120000,\"x1\":0,\"y1\":500,\"x2\":500,\"y2\":1000}");
        cache.remember(a,"aa".repeat(32));
        cache.remember(b,"bb".repeat(32));
        cache.remember(c,"cc".repeat(32));
        assertEquals(2,cache.size());
        AndroidCapabilityProtocol.Request a2=request("{\"quality\":45,\"maxBytes\":120000,\"x1\":0,\"y1\":0,\"x2\":500,\"y2\":500}");
        assertFalse(cache.inject(a2));
    }
}
