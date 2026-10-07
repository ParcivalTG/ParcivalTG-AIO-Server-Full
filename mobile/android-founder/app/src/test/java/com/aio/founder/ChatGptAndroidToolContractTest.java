package com.aio.founder;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class ChatGptAndroidToolContractTest {
    @Test public void p0ToolSetIncludesWindowsAndAndroidOnly(){
        List<ChatGptResponsesContract.FunctionTool> tools=ChatGptAioToolContract.p0Tools();
        assertEquals(2,tools.size());
        assertEquals(ChatGptAioToolContract.WINDOWS_OBJECTIVE_TOOL,tools.get(0).name);
        assertEquals(ChatGptAndroidToolContract.TOOL,tools.get(1).name);
        assertTrue(ChatGptAndroidToolContract.LOCAL_PEER_ID.matches("[A-Za-z0-9_.:-]{1,128}"));
    }

    @Test public void androidToolForcesFounderOnlyAndDerivesCapability()throws Exception{
        ChatGptAndroidToolContract.Invocation invocation=ChatGptAndroidToolContract.parse(
            "{\"action\":\"resource.status\",\"args_json\":\"{}\"}");
        try{
            assertEquals("resource.status",invocation.action);
            assertEquals(AioAndroidNode.Capability.RESOURCE_STATUS,invocation.capability);
            assertTrue(AioProjectionQuantumCodec.looksLike(invocation.payload));
            AndroidCapabilityProtocol.Request request=AndroidCapabilityProtocol.parse(invocation.payload);
            assertEquals(AioAndroidNode.Capability.RESOURCE_STATUS,request.capability);
            assertEquals(AioAndroidNode.Privacy.FOUNDER_ONLY,request.privacy);
            assertEquals("resource.status",request.action);
            assertTrue(request.args.isEmpty());
        }finally{
            java.util.Arrays.fill(invocation.payload,(byte)0);
        }
    }

    @Test public void boundedArgumentsRoundTripThroughAndroidProtocol()throws Exception{
        ChatGptAndroidToolContract.Invocation invocation=ChatGptAndroidToolContract.parse(
            "{\"action\":\"gesture.tap\",\"args_json\":\"{\\\"x\\\":500,\\\"y\\\":250,\\\"durationMs\\\":80}\"}");
        try{
            assertEquals(AioAndroidNode.Capability.GESTURE_INPUT,invocation.capability);
            assertTrue(AioProjectionQuantumCodec.looksLike(invocation.payload));
            assertTrue(invocation.payload.length<32);
            AndroidCapabilityProtocol.Request request=AndroidCapabilityProtocol.parse(invocation.payload);
            assertEquals(500L,AndroidCapabilityProtocol.integer(request.args,"x",0,1000,0));
            assertEquals(250L,AndroidCapabilityProtocol.integer(request.args,"y",0,1000,0));
        }finally{
            java.util.Arrays.fill(invocation.payload,(byte)0);
        }
    }

    @Test public void directPhaseProjectionAvoidsIntermediateCapabilityJson()throws Exception{
        String providerArgs="{\"action\":\"gesture.tap\",\"args_json\":\"{\\\"x\\\":700,\\\"y\\\":300}\"}";
        ChatGptAndroidToolContract.Invocation invocation=ChatGptAndroidToolContract.parse(providerArgs);
        try{
            assertTrue(AioProjectionQuantumCodec.looksLike(invocation.payload));
            String conventional="{\"schema\":\""+AndroidCapabilityProtocol.REQUEST_SCHEMA+
                "\",\"capability\":\"GESTURE_INPUT\",\"action\":\"gesture.tap\","+
                "\"privacyClass\":\"FOUNDER_ONLY\",\"args\":{\"x\":700,\"y\":300}}";
            assertTrue(invocation.payload.length*4<
                conventional.getBytes(StandardCharsets.UTF_8).length);
            AndroidCapabilityProtocol.Request decoded=
                AndroidCapabilityProtocol.parse(invocation.payload);
            assertEquals(700L,AndroidCapabilityProtocol.integer(decoded.args,"x",0,1000,0));
            assertEquals(300L,AndroidCapabilityProtocol.integer(decoded.args,"y",0,1000,0));
        }finally{
            java.util.Arrays.fill(invocation.payload,(byte)0);
        }
    }

    @Test public void screenCaptureIsForcedIntoGptRequestBudget()throws Exception{
        ChatGptAndroidToolContract.Invocation invocation=ChatGptAndroidToolContract.parse(
            "{\"action\":\"screen.capture\",\"args_json\":\"{\\\"quality\\\":75,\\\"maxBytes\\\":320000}\"}");
        try{
            AndroidCapabilityProtocol.Request request=AndroidCapabilityProtocol.parse(invocation.payload);
            assertEquals(AioAndroidNode.Capability.SCREEN_OBSERVE,request.capability);
            assertEquals(55L,AndroidCapabilityProtocol.integer(request.args,"quality",30,75,0));
            assertEquals((long)AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES,
                AndroidCapabilityProtocol.integer(request.args,"maxBytes",
                    AndroidRemoteScreenPolicy.MIN_JPEG_BYTES,
                    AndroidRemoteScreenPolicy.MAX_JPEG_BYTES,0));
        }finally{
            java.util.Arrays.fill(invocation.payload,(byte)0);
        }
    }

    @Test public void unknownToolActionAndExtraTopLevelFieldsFailClosed()throws Exception{
        try{
            ChatGptAndroidToolContract.parse(
                "{\"action\":\"shell.exec\",\"args_json\":\"{}\"}");
            fail();
        }catch(SecurityException expected){
            assertEquals("CHATGPT_ANDROID_ACTION_NOT_ALLOWED",expected.getMessage());
        }

        try{
            ChatGptAndroidToolContract.parse(
                "{\"action\":\"resource.status\",\"args_json\":\"{}\",\"shell\":\"cmd\"}");
            fail();
        }catch(SecurityException expected){
            assertEquals("CHATGPT_ANDROID_TOOL_ARGUMENTS_FORBIDDEN",expected.getMessage());
        }
    }

    @Test public void toolSchemaIsStrictAndRequiresActionAndArgsJson()throws Exception{
        ChatGptResponsesContract.FunctionTool tool=ChatGptAndroidToolContract.tool();
        String json=tool.json();
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        try{
            StrictProjectionJson.ObjectValue row=StrictProjectionJson.object(bytes,64*1024,48*1024);
            assertEquals(ChatGptAndroidToolContract.TOOL,row.get("name"));
            assertEquals(Boolean.TRUE,row.get("strict"));
            StrictProjectionJson.ObjectValue parameters=(StrictProjectionJson.ObjectValue)row.get("parameters");
            assertEquals(Boolean.FALSE,parameters.get("additionalProperties"));
            assertEquals(List.of("action","args_json"),parameters.get("required"));
        }finally{
            java.util.Arrays.fill(bytes,(byte)0);
        }
    }
}
