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
            AndroidCapabilityProtocol.Request request=AndroidCapabilityProtocol.parse(invocation.payload);
            assertEquals(500L,AndroidCapabilityProtocol.integer(request.args,"x",0,1000,0));
            assertEquals(250L,AndroidCapabilityProtocol.integer(request.args,"y",0,1000,0));
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
