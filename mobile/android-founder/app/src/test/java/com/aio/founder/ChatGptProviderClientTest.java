package com.aio.founder;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ChatGptProviderClientTest {
    @Test public void listsVisibleModelsWithBearerCredential()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","application/json")
                .setBody("{\"models\":[{\"slug\":\"gpt-a\",\"display_name\":\"A\",\"visibility\":\"list\"}]}"));
            server.start();
            ChatGptProviderClient client=new ChatGptProviderClient(
                new ChatGptProviderClient.Endpoints(
                    server.url("/token").toString(),
                    server.url("/jwks").toString(),
                    server.url("/models").toString(),
                    server.url("/responses").toString()));
            List<ChatGptResponsesContract.Model> models=client.models("access-secret");
            assertEquals(1,models.size());
            RecordedRequest request=server.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("GET",request.getMethod());
            assertEquals("Bearer access-secret",request.getHeader("Authorization"));
            assertEquals("/models",request.getPath());
        }
    }

    @Test public void streamedInferenceRequiresCompletedAndDeliversDeltas()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            String sse=
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"AIO \"}\n\n"+
                "data: {\"type\":\"response.output_item.done\",\"item\":{\"type\":\"reasoning\",\"id\":\"rs_1\",\"encrypted_content\":\"opaque\",\"summary\":[]}}\n\n"+
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"ready\"}\n\n"+
                "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\"}}\n\n";
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","text/event-stream")
                .setBody(sse));
            server.start();
            ChatGptProviderClient client=new ChatGptProviderClient(
                new ChatGptProviderClient.Endpoints(
                    server.url("/token").toString(),
                    server.url("/jwks").toString(),
                    server.url("/models").toString(),
                    server.url("/responses").toString()));
            ArrayList<String> deltas=new ArrayList<>();
            ChatGptResponsesContract.Completion completed=client.respond(
                "access-secret","gpt-a","AIO instructions",
                List.of(ChatGptResponsesContract.message("user","Continue")),
                deltas::add);
            assertEquals(List.of("AIO ","ready"),deltas);
            assertEquals("AIO ready",completed.text);
            assertEquals(1,completed.reasoningItems.size());

            RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("POST",request.getMethod());
            assertEquals("Bearer access-secret",request.getHeader("Authorization"));
            StrictProjectionJson.ObjectValue body=StrictProjectionJson.object(
                request.getBody().readByteArray(),256*1024,128*1024);
            assertEquals(Boolean.FALSE,body.get("store"));
            assertEquals(Boolean.TRUE,body.get("stream"));
            assertFalse(body.containsKey("previous_response_id"));
        }
    }

    @Test public void missingCompletedEventFailsClosed()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","text/event-stream")
                .setBody("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"));
            server.start();
            ChatGptProviderClient client=new ChatGptProviderClient(
                new ChatGptProviderClient.Endpoints(
                    server.url("/token").toString(),
                    server.url("/jwks").toString(),
                    server.url("/models").toString(),
                    server.url("/responses").toString()));
            try{
                client.respond("access-secret","gpt-a","AIO instructions",
                    List.of(ChatGptResponsesContract.message("user","Continue")),delta->{});
                fail();
            }catch(IllegalStateException expected){
                assertEquals("CHATGPT_STREAM_INCOMPLETE",expected.getMessage());
            }
        }
    }
}
