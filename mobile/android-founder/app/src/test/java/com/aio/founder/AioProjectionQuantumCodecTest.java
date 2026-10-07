package com.aio.founder;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class AioProjectionQuantumCodecTest {
    private static AndroidCapabilityProtocol.Request json(String capability,String action,String args)throws Exception{
        String payload="{\"schema\":\"aio.android.capability.request.v1\",\"capability\":\""+capability+
            "\",\"action\":\""+action+"\",\"privacyClass\":\"FOUNDER_ONLY\",\"args\":"+args+"}";
        return AndroidCapabilityProtocol.parse(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static void exact(String capability,String action,String args)throws Exception{
        AndroidCapabilityProtocol.Request source=json(capability,action,args);
        byte[] packet=AioProjectionQuantumCodec.encode(source);
        try{
            assertTrue(AioProjectionQuantumCodec.looksLike(packet));
            AndroidCapabilityProtocol.Request decoded=AndroidCapabilityProtocol.parse(packet);
            assertEquals(source.capability,decoded.capability);
            assertEquals(source.action,decoded.action);
            assertEquals(source.privacy,decoded.privacy);
            assertEquals(ChatGptResponsesContract.canonical(source.args),
                ChatGptResponsesContract.canonical(decoded.args));
        }finally{
            java.util.Arrays.fill(packet,(byte)0);
        }
    }

    @Test public void exactRoundTripCoversEveryCurrentAndroidAction()throws Exception{
        exact("RESOURCE_STATUS","resource.status","{}");
        exact("SCREEN_OBSERVE","screen.capture",
            "{\"quality\":45,\"maxBytes\":120000,\"x1\":100,\"y1\":200,\"x2\":900,\"y2\":800,"+
            "\"previousSha256\":\""+"ab".repeat(32)+"\"}");
        exact("GESTURE_INPUT","gesture.tap","{\"x\":500,\"y\":250,\"durationMs\":80}");
        exact("GESTURE_INPUT","gesture.swipe",
            "{\"x1\":100,\"y1\":800,\"x2\":900,\"y2\":200,\"durationMs\":350}");
        exact("CLIPBOARD","clipboard.read","{}");
        exact("CLIPBOARD","clipboard.write","{\"text\":\"hello AIO\"}");
        exact("NOTIFICATIONS","notification.post","{\"title\":\"AIO\",\"text\":\"done\"}");
        exact("FILE_READ","file.list","{\"path\":\"docs\"}");
        exact("FILE_READ","file.read","{\"path\":\"docs/a.txt\",\"maxBytes\":4096}");
        exact("FILE_READ","file.sha256","{\"path\":\"docs/a.txt\"}");
        exact("FILE_WRITE","file.write",
            "{\"parentPath\":\"docs\",\"name\":\"a.txt\",\"mimeType\":\"text/plain\",\"contentB64\":\"YWlv\"}");
        exact("FILE_WRITE","file.rename","{\"path\":\"docs/a.txt\",\"newName\":\"b.txt\"}");
        exact("FILE_WRITE","file.delete","{\"path\":\"docs/b.txt\"}");
        exact("RESOURCE_CONTRIBUTE","resource.sha256","{\"contentB64\":\"YWlv\"}");
        exact("RESOURCE_CONTRIBUTE","resource.deflate","{\"contentB64\":\"YWlv\",\"level\":6}");
    }

    @Test public void hotGuiProjectionIsMuchSmallerThanJson()throws Exception{
        String jsonText="{\"schema\":\"aio.android.capability.request.v1\",\"capability\":\"GESTURE_INPUT\","+
            "\"action\":\"gesture.tap\",\"privacyClass\":\"FOUNDER_ONLY\","+
            "\"args\":{\"x\":500,\"y\":250,\"durationMs\":80}}";
        byte[] jsonBytes=jsonText.getBytes(StandardCharsets.UTF_8);
        AndroidCapabilityProtocol.Request source=AndroidCapabilityProtocol.parse(jsonBytes);
        byte[] quantum=AioProjectionQuantumCodec.encode(source);
        try{
            assertTrue(quantum.length*3<jsonBytes.length);
        }finally{
            java.util.Arrays.fill(jsonBytes,(byte)0);
            java.util.Arrays.fill(quantum,(byte)0);
        }
    }

    @Test public void singleBitCorruptionFailsClosed()throws Exception{
        AndroidCapabilityProtocol.Request source=json(
            "GESTURE_INPUT","gesture.tap","{\"x\":500,\"y\":250,\"durationMs\":80}");
        byte[] packet=AioProjectionQuantumCodec.encode(source);
        try{
            packet[6]^=0x01;
            try{
                AndroidCapabilityProtocol.parse(packet);
                fail();
            }catch(SecurityException expected){
                assertEquals("QUANTUM_CRC_INVALID",expected.getMessage());
            }
        }finally{
            java.util.Arrays.fill(packet,(byte)0);
        }
    }

    @Test public void quantumPacketRetainsFounderOnlyPrivacy()throws Exception{
        AndroidCapabilityProtocol.Request source=json(
            "RESOURCE_STATUS","resource.status","{}");
        byte[] packet=AioProjectionQuantumCodec.encode(source);
        try{
            AndroidCapabilityProtocol.Request decoded=AndroidCapabilityProtocol.parse(packet);
            assertEquals(AioAndroidNode.Privacy.FOUNDER_ONLY,decoded.privacy);
        }finally{
            java.util.Arrays.fill(packet,(byte)0);
        }
    }
}
