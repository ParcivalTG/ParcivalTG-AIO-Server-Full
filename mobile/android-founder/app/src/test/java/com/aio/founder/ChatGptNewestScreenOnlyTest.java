package com.aio.founder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ChatGptNewestScreenOnlyTest {
    @Test public void newerScreenImageReplacesPriorImageAcrossToolRounds()throws Exception{
        String jpegA=java.util.Base64.getEncoder().encodeToString(
            new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0x01});
        String jpegB=java.util.Base64.getEncoder().encodeToString(
            new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0x02});

        ChatGptResponsesContract.FunctionCall firstCall=new ChatGptResponsesContract.FunctionCall(
            "call_img_a","aio_android_capability_invoke",
            "{\"action\":\"screen.capture\",\"args_json\":\"{}\"}",
            "{\"type\":\"function_call\",\"call_id\":\"call_img_a\",\"name\":\"aio_android_capability_invoke\",\"arguments\":\"{\\\"action\\\":\\\"screen.capture\\\",\\\"args_json\\\":\\\"{}\\\"}\"}");
        ChatGptToolLoop.Step first=ChatGptToolLoop.advance(
            new ArrayList<>(List.of(ChatGptResponsesContract.message("user","Inspect"))),
            new ChatGptResponsesContract.Completion("",List.of(),List.of(firstCall)),
            (name,args)->new ChatGptToolLoop.Execution(
                "{\"ok\":true,\"screen\":\"a\"}",
                List.of(ChatGptResponsesContract.jpegImage(jpegA))));
        assertEquals(1,first.nextInput.stream().filter(ChatGptResponsesContract::isInputImage).count());

        ChatGptResponsesContract.FunctionCall secondCall=new ChatGptResponsesContract.FunctionCall(
            "call_img_b","aio_android_capability_invoke",
            "{\"action\":\"screen.capture\",\"args_json\":\"{}\"}",
            "{\"type\":\"function_call\",\"call_id\":\"call_img_b\",\"name\":\"aio_android_capability_invoke\",\"arguments\":\"{\\\"action\\\":\\\"screen.capture\\\",\\\"args_json\\\":\\\"{}\\\"}\"}");
        ChatGptToolLoop.Step second=ChatGptToolLoop.advance(
            first.nextInput,
            new ChatGptResponsesContract.Completion("",List.of(),List.of(secondCall)),
            (name,args)->new ChatGptToolLoop.Execution(
                "{\"ok\":true,\"screen\":\"b\"}",
                List.of(ChatGptResponsesContract.jpegImage(jpegB))));

        assertEquals(1,second.nextInput.stream().filter(ChatGptResponsesContract::isInputImage).count());
        assertFalse(second.nextInput.stream().anyMatch(x->x.json().contains(jpegA)));
        assertTrue(second.nextInput.stream().anyMatch(x->x.json().contains(jpegB)));
    }
}
