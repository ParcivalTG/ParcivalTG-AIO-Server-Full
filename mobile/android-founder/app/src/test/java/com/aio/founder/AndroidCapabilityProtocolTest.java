package com.aio.founder;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidCapabilityProtocolTest {
    @Test public void parsesBoundedResourceRequest()throws Exception{
        String json="{\"schema\":\"aio.android.capability.request.v1\",\"capability\":\"RESOURCE_STATUS\",\"action\":\"resource.status\",\"privacyClass\":\"FOUNDER_ONLY\",\"args\":{}}";
        AndroidCapabilityProtocol.Request request=AndroidCapabilityProtocol.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(AioAndroidNode.Capability.RESOURCE_STATUS,request.capability);
        assertEquals("resource.status",request.action);
        assertEquals(AioAndroidNode.Privacy.FOUNDER_ONLY,request.privacy);
    }

    @Test public void unknownTopLevelKeyFailsClosed(){
        String json="{\"schema\":\"aio.android.capability.request.v1\",\"capability\":\"RESOURCE_STATUS\",\"action\":\"resource.status\",\"privacyClass\":\"FOUNDER_ONLY\",\"args\":{},\"extra\":true}";
        try{AndroidCapabilityProtocol.parse(json.getBytes(StandardCharsets.UTF_8));fail();}
        catch(Exception expected){assertEquals("ANDROID_CAPABILITY_KEYS",expected.getMessage());}
    }

    @Test public void unsupportedActionFailsClosed(){
        String json="{\"schema\":\"aio.android.capability.request.v1\",\"capability\":\"GESTURE_INPUT\",\"action\":\"gesture.text\",\"privacyClass\":\"FOUNDER_ONLY\",\"args\":{}}";
        try{AndroidCapabilityProtocol.parse(json.getBytes(StandardCharsets.UTF_8));fail();}
        catch(Exception expected){assertEquals("ANDROID_ACTION_UNSUPPORTED",expected.getMessage());}
    }

    @Test public void exportedGrantBearerIsRejected(){
        String json="{\"schema\":\"aio.android.capability.request.v1\",\"grantId\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"capability\":\"RESOURCE_STATUS\",\"action\":\"resource.status\",\"privacyClass\":\"FOUNDER_ONLY\",\"args\":{}}";
        try{AndroidCapabilityProtocol.parse(json.getBytes(StandardCharsets.UTF_8));fail();}
        catch(Exception expected){assertEquals("ANDROID_CAPABILITY_KEYS",expected.getMessage());}
    }

    @Test public void malformedReplyResultFailsClosed(){
        try{AndroidCapabilityProtocol.reply(true,"OK","not-json");fail();}
        catch(IllegalArgumentException expected){assertEquals("ANDROID_RESULT_JSON_INVALID",expected.getMessage());}
    }

    @Test public void oversizedReplyFailsClosed(){
        String huge="{\"x\":\""+("a".repeat(AndroidCapabilityProtocol.MAX_REPLY_BYTES))+"\"}";
        try{AndroidCapabilityProtocol.reply(true,"OK",huge);fail();}
        catch(IllegalArgumentException expected){
            assertTrue(expected.getMessage().equals("ANDROID_RESULT_JSON_INVALID")||
                expected.getMessage().equals("ANDROID_REPLY_BUDGET"));
        }
    }

    @Test public void boundedLargeGeneratedResultIsAllowed(){
        String payload="{\"contentB64\":\""+("A".repeat(64*1024))+"\"}";
        byte[] reply=AndroidCapabilityProtocol.reply(true,"OK",payload);
        assertTrue(reply.length>64*1024);
        assertTrue(reply.length<AndroidCapabilityProtocol.MAX_REPLY_BYTES);
    }

    @Test public void replyEscapesCodeAndKeepsStructuredResult(){
        byte[] payload=AndroidCapabilityProtocol.reply(true,"OK","{\"value\":1}");
        String text=new String(payload,StandardCharsets.UTF_8);
        assertTrue(text.contains("\"accepted\":true"));
        assertTrue(text.contains("\"result\":{\"value\":1}"));
    }
}
