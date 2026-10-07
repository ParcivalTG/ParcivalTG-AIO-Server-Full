package com.aio.founder;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class ChatGptToolCallingContractTest {
    @Test public void toolRequestIsStrictStatelessAndSingleCall()throws Exception{
        ChatGptResponsesContract.FunctionTool tool=ChatGptAioToolContract.windowsObjectiveTool();
        byte[] payload=ChatGptResponsesContract.request(
            "gpt-tool","Use AIO tools when Windows work is needed.",
            List.of(ChatGptResponsesContract.message("user","Build the Android app")),
            List.of(tool));
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(payload,512*1024,256*1024);
        assertEquals(Boolean.FALSE,root.get("store"));
        assertEquals(Boolean.TRUE,root.get("stream"));
        assertEquals(Boolean.FALSE,root.get("parallel_tool_calls"));
        List<?> tools=(List<?>)root.get("tools");
        assertEquals(1,tools.size());
        StrictProjectionJson.ObjectValue row=(StrictProjectionJson.ObjectValue)tools.get(0);
        assertEquals("function",row.get("type"));
        assertEquals("aio_windows_objective_submit",row.get("name"));
        assertEquals(Boolean.TRUE,row.get("strict"));
        StrictProjectionJson.ObjectValue parameters=(StrictProjectionJson.ObjectValue)row.get("parameters");
        assertEquals(Boolean.FALSE,parameters.get("additionalProperties"));
        assertEquals(List.of("instruction"),parameters.get("required"));
    }

    @Test public void streamCapturesExactFunctionCallForReplay()throws Exception{
        ChatGptResponsesContract.Stream stream=new ChatGptResponsesContract.Stream();
        stream.accept(("{\"type\":\"response.output_item.done\",\"item\":"+
            "{\"id\":\"fc_1\",\"type\":\"function_call\",\"status\":\"completed\","+
            "\"call_id\":\"call_1\",\"name\":\"aio_windows_objective_submit\","+
            "\"arguments\":\"{\\\"instruction\\\":\\\"Run the gateway court\\\"}\"}}")
            .getBytes(StandardCharsets.UTF_8));
        stream.accept("{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\"}}"
            .getBytes(StandardCharsets.UTF_8));
        ChatGptResponsesContract.Completion completed=stream.finish();
        assertEquals(1,completed.functionCalls.size());
        ChatGptResponsesContract.FunctionCall call=completed.functionCalls.get(0);
        assertEquals("call_1",call.callId);
        assertEquals("aio_windows_objective_submit",call.name);
        assertTrue(call.arguments.contains("Run the gateway court"));
        assertTrue(call.itemJson.contains("\"type\":\"function_call\""));

        ChatGptResponsesContract.InputItem replay=call.asInput();
        assertTrue(replay.json().contains("\"call_id\":\"call_1\""));
        ChatGptResponsesContract.InputItem output=ChatGptResponsesContract.functionOutput(
            call.callId,"{\"ok\":true}");
        assertTrue(output.json().contains("\"type\":\"function_call_output\""));
        assertTrue(output.json().contains("\"call_id\":\"call_1\""));
    }

    @Test public void objectiveToolAcceptsOnlyInstruction()throws Exception{
        assertEquals("Rebuild and test AIO",ChatGptAioToolContract.parseInstruction(
            "{\"instruction\":\"Rebuild and test AIO\"}"));
        try{
            ChatGptAioToolContract.parseInstruction(
                "{\"instruction\":\"x\",\"shell\":\"cmd.exe\"}");
            fail();
        }catch(SecurityException expected){
            assertEquals("CHATGPT_TOOL_ARGUMENTS_FORBIDDEN",expected.getMessage());
        }
        try{
            ChatGptAioToolContract.parseInstruction("{}");
            fail();
        }catch(IllegalArgumentException expected){
            assertEquals("CHATGPT_TOOL_INSTRUCTION_INVALID",expected.getMessage());
        }
    }

    @Test public void unknownOrMalformedFunctionCallFailsClosed()throws Exception{
        ChatGptResponsesContract.Stream unknown=new ChatGptResponsesContract.Stream();
        try{
            unknown.accept(("{\"type\":\"response.output_item.done\",\"item\":"+
                "{\"type\":\"function_call\",\"call_id\":\"bad id\",\"name\":\"x\",\"arguments\":\"{}\"}}")
                .getBytes(StandardCharsets.UTF_8));
            fail();
        }catch(IllegalArgumentException expected){
            assertEquals("CHATGPT_FUNCTION_CALL_INVALID",expected.getMessage());
        }
    }
}
