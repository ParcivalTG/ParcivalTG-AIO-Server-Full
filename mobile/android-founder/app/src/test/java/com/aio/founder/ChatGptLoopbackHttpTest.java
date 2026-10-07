package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatGptLoopbackHttpTest {
    @Test public void reconstructsOnlyExactLoopbackCallback(){
        String url=ChatGptLoopbackHttp.callbackUrl(
            "GET /auth/callback?code=abc&state=xyz&client_id=oaiapp_123 HTTP/1.1",1455);
        assertEquals("http://127.0.0.1:1455/auth/callback?code=abc&state=xyz&client_id=oaiapp_123",url);
    }

    @Test public void rejectsWrongMethodPathHostFormAndOversize(){
        String[] invalid={
            "POST /auth/callback?code=abc HTTP/1.1",
            "GET /callback?code=abc HTTP/1.1",
            "GET http://evil.example/auth/callback?code=abc HTTP/1.1",
            "GET /auth/callback/extra?code=abc HTTP/1.1"
        };
        for(String row:invalid){
            try{ChatGptLoopbackHttp.callbackUrl(row,1455);fail(row);}
            catch(IllegalArgumentException expected){assertEquals("CHATGPT_LOOPBACK_REQUEST_INVALID",expected.getMessage());}
        }
        try{ChatGptLoopbackHttp.callbackUrl("GET /auth/callback?x="+"a".repeat(17000)+" HTTP/1.1",1455);fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_LOOPBACK_REQUEST_INVALID",expected.getMessage());}
    }

    @Test public void browserResponseContainsNoSecrets(){
        String response=ChatGptLoopbackHttp.successResponse();
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(response.contains("Return to AIO"));
        assertFalse(response.toLowerCase().contains("token"));
        assertFalse(response.toLowerCase().contains("code="));
    }
}
