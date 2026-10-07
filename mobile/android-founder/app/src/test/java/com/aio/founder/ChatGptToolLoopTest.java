package com.aio.founder;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ChatGptToolLoopTest {
    @Test public void terminalTextCompletesWithoutToolExecution()throws Exception{
        ChatGptResponsesContract.Completion completion=new ChatGptResponsesContract.Completion(
            "done",List.of(),List.of());
        ChatGptToolLoop.Step step=ChatGptToolLoop.advance(
            new ArrayList<>(List.of(ChatGptResponsesContract.message("user","x"))),
            completion,(name,args)->{throw new AssertionError();});
        assertTrue(step.terminal);
        assertSame(completion,step.completion);
    }

    @Test public void toolRoundReplaysReasoningCallAndOutput()throws Exception{
        String reasoning="{\"type\":\"reasoning\",\"id\":\"rs_1\",\"encrypted_content\":\"cipher\",\"summary\":[]}";
        ChatGptResponsesContract.FunctionCall call=new ChatGptResponsesContract.FunctionCall(
            "call_1","aio_windows_objective_submit",
            "{\"instruction\":\"Build and test Windows AIO\"}",
            "{\"id\":\"fc_1\",\"type\":\"function_call\",\"call_id\":\"call_1\","+
                "\"name\":\"aio_windows_objective_submit\",\"arguments\":\"{\\\"instruction\\\":\\\"Build and test Windows AIO\\\"}\"}");
        ChatGptResponsesContract.Completion completion=new ChatGptResponsesContract.Completion(
            "",List.of(reasoning),List.of(call));

        ArrayList<ChatGptResponsesContract.InputItem> prior=new ArrayList<>();
        prior.add(ChatGptResponsesContract.message("user","Do the Windows work"));
        ChatGptToolLoop.Step step=ChatGptToolLoop.advance(prior,completion,
            (name,args)->"{\"ok\":true,\"receipt\":\"verified\"}");

        assertFalse(step.terminal);
        assertEquals(4,step.nextInput.size());
        assertTrue(step.nextInput.get(1).json().contains("\"type\":\"reasoning\""));
        assertTrue(step.nextInput.get(2).json().contains("\"type\":\"function_call\""));
        assertTrue(step.nextInput.get(3).json().contains("\"type\":\"function_call_output\""));
        assertTrue(step.nextInput.get(3).json().contains("\"call_id\":\"call_1\""));
    }

    @Test public void parallelAndUnknownToolsFailClosed()throws Exception{
        ChatGptResponsesContract.FunctionCall a=new ChatGptResponsesContract.FunctionCall(
            "call_a","aio_windows_objective_submit","{\"instruction\":\"a\"}",
            "{\"type\":\"function_call\",\"call_id\":\"call_a\",\"name\":\"aio_windows_objective_submit\",\"arguments\":\"{\\\"instruction\\\":\\\"a\\\"}\"}");
        ChatGptResponsesContract.FunctionCall b=new ChatGptResponsesContract.FunctionCall(
            "call_b","aio_windows_objective_submit","{\"instruction\":\"b\"}",
            "{\"type\":\"function_call\",\"call_id\":\"call_b\",\"name\":\"aio_windows_objective_submit\",\"arguments\":\"{\\\"instruction\\\":\\\"b\\\"}\"}");
        try{
            ChatGptToolLoop.advance(new ArrayList<>(),
                new ChatGptResponsesContract.Completion("",List.of(),List.of(a,b)),(n,x)->"{}");
            fail();
        }catch(SecurityException expected){
            assertEquals("CHATGPT_PARALLEL_TOOL_CALL_DENIED",expected.getMessage());
        }

        ChatGptResponsesContract.FunctionCall unknown=new ChatGptResponsesContract.FunctionCall(
            "call_x","raw_shell","{}",
            "{\"type\":\"function_call\",\"call_id\":\"call_x\",\"name\":\"raw_shell\",\"arguments\":\"{}\"}");
        try{
            ChatGptToolLoop.advance(new ArrayList<>(),
                new ChatGptResponsesContract.Completion("",List.of(),List.of(unknown)),(n,x)->"{}");
            fail();
        }catch(SecurityException expected){
            assertEquals("CHATGPT_TOOL_NOT_ALLOWED",expected.getMessage());
        }
    }
}
