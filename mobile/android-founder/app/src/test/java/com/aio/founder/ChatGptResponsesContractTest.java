package com.aio.founder;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class ChatGptResponsesContractTest {
    @Test public void requestIsStatelessStreamingAndRejectsSystemRole()throws Exception{
        List<ChatGptResponsesContract.InputItem> input=List.of(
            ChatGptResponsesContract.message("user","Continue the AIO Android work"),
            ChatGptResponsesContract.message("assistant","Working from the verified checkpoint."),
            ChatGptResponsesContract.reasoning(
                "{\"type\":\"reasoning\",\"id\":\"rs_1\",\"encrypted_content\":\"opaque-ciphertext\",\"summary\":[]}")
        );
        String json=new String(ChatGptResponsesContract.request(
            "gpt-6.1-sol","You are the AIO Founder development provider.",input),
            StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(
            json.getBytes(StandardCharsets.UTF_8),256*1024,128*1024);
        assertEquals("gpt-6.1-sol",root.get("model"));
        assertEquals(Boolean.FALSE,root.get("store"));
        assertEquals(Boolean.TRUE,root.get("stream"));
        assertEquals("You are the AIO Founder development provider.",root.get("instructions"));
        assertFalse(root.containsKey("conversation"));
        assertFalse(root.containsKey("previous_response_id"));
        assertFalse(root.containsKey("temperature"));
        assertEquals(3,((java.util.List<?>)root.get("input")).size());

        try{ChatGptResponsesContract.message("system","forbidden");fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_INPUT_ROLE_INVALID",expected.getMessage());}
    }

    @Test public void boundedJpegProjectsAsResponsesInputImage()throws Exception{
        byte[] jpeg=new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0x00};
        String base64=java.util.Base64.getEncoder().encodeToString(jpeg);
        ChatGptResponsesContract.InputItem image=ChatGptResponsesContract.jpegImage(base64);
        assertTrue(image.json().contains("\"type\":\"input_image\""));
        assertTrue(image.json().contains("\"image_url\":\"data:image/jpeg;base64,"));
        assertTrue(image.json().contains("\"detail\":\"auto\""));

        String request=new String(ChatGptResponsesContract.request(
            "gpt-test","Inspect the Android screen.",
            List.of(ChatGptResponsesContract.message("user","What is visible?"),image)),
            StandardCharsets.UTF_8);
        assertTrue(request.contains("data:image/jpeg;base64,"));
        assertTrue(request.contains("\"store\":false"));
        assertTrue(request.contains("\"stream\":true"));
    }

    @Test public void malformedOrOversizeImageFailsClosed(){
        try{ChatGptResponsesContract.jpegImage("not-base64!");fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_INPUT_IMAGE_INVALID",expected.getMessage());}

        byte[] huge=new byte[AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES+1];
        huge[0]=(byte)0xff;huge[1]=(byte)0xd8;
        try{ChatGptResponsesContract.jpegImage(java.util.Base64.getEncoder().encodeToString(huge));fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_INPUT_IMAGE_INVALID",expected.getMessage());}
    }

    @Test public void modelCatalogKeepsOnlyVisibleModelsInServerOrder()throws Exception{
        String payload="{\"models\":["+
            "{\"slug\":\"gpt-a\",\"display_name\":\"A\",\"visibility\":\"list\"},"+
            "{\"slug\":\"internal\",\"display_name\":\"Hidden\",\"visibility\":\"hidden\"},"+
            "{\"slug\":\"gpt-b\",\"display_name\":\"B\",\"visibility\":\"list\"}]}";
        List<ChatGptResponsesContract.Model> models=ChatGptResponsesContract.parseModels(
            payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(2,models.size());
        assertEquals("gpt-a",models.get(0).slug);
        assertEquals("A",models.get(0).displayName);
        assertEquals("gpt-b",models.get(1).slug);
    }

    @Test public void streamRequiresCompletedAndPreservesReasoning()throws Exception{
        ChatGptResponsesContract.Stream stream=new ChatGptResponsesContract.Stream();
        stream.accept("{\"type\":\"response.output_text.delta\",\"delta\":\"Hello \"}".getBytes(StandardCharsets.UTF_8));
        stream.accept("{\"type\":\"response.output_item.done\",\"item\":{\"type\":\"reasoning\",\"id\":\"rs_1\",\"encrypted_content\":\"cipher\",\"summary\":[]}}".getBytes(StandardCharsets.UTF_8));
        stream.accept("{\"type\":\"response.output_text.delta\",\"delta\":\"Founder\"}".getBytes(StandardCharsets.UTF_8));
        stream.accept("{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\"}}".getBytes(StandardCharsets.UTF_8));
        ChatGptResponsesContract.Completion completed=stream.finish();
        assertEquals("Hello Founder",completed.text);
        assertEquals(1,completed.reasoningItems.size());
        assertTrue(completed.reasoningItems.get(0).contains("\"encrypted_content\":\"cipher\""));
        assertTrue(completed.reasoningItems.get(0).contains("\"type\":\"reasoning\""));
    }

    @Test public void interruptedOrFailedStreamsNeverLookSuccessful()throws Exception{
        ChatGptResponsesContract.Stream interrupted=new ChatGptResponsesContract.Stream();
        interrupted.accept("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}".getBytes(StandardCharsets.UTF_8));
        try{interrupted.finish();fail();}
        catch(IllegalStateException expected){assertEquals("CHATGPT_STREAM_INCOMPLETE",expected.getMessage());}

        ChatGptResponsesContract.Stream failed=new ChatGptResponsesContract.Stream();
        failed.accept(("{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":"+
            "\"subscription_sharing_usage_limit_exceeded\"}}}").getBytes(StandardCharsets.UTF_8));
        try{failed.finish();fail();}
        catch(IllegalStateException expected){
            assertEquals("CHATGPT_RESPONSE_FAILED:subscription_sharing_usage_limit_exceeded",expected.getMessage());
        }
    }

    @Test public void reasoningInputMustBeOpaqueReasoningOnly()throws Exception{
        try{ChatGptResponsesContract.reasoning("{\"type\":\"message\",\"content\":[]}");fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_REASONING_ITEM_INVALID",expected.getMessage());}
        try{ChatGptResponsesContract.reasoning("{\"type\":\"reasoning\",\"encrypted_content\":\"\"}");fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_REASONING_ITEM_INVALID",expected.getMessage());}
    }
}
