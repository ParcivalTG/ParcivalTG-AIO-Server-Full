package com.aio.founder;

import org.junit.Test;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ChatGptAuthContractTest {
    private static Map<String,String> query(String url)throws Exception{
        Map<String,String> out=new LinkedHashMap<>();
        String raw=new URI(url).getRawQuery();
        for(String pair:raw.split("&")){
            String[] kv=pair.split("=",2);
            out.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),
                URLDecoder.decode(kv.length==2?kv[1]:"",StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test public void firstRegistrationUsesDocumentedOpenSourcePkceContract()throws Exception{
        ChatGptAuthContract.Attempt a=ChatGptAuthContract.newRegistrationAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",1455);
        assertEquals("http://127.0.0.1:1455/auth/callback",a.redirectUri);
        assertTrue(a.state.length()>=32);
        assertTrue(a.nonce.length()>=32);
        assertTrue(a.codeVerifier.length()>=43);

        Map<String,String> q=query(a.authorizationUrl());
        assertEquals("dynamic_agent_client",q.get("client_id"));
        assertEquals("AIO for Android",q.get("agent_name_hint"));
        assertEquals("urn:uuid:11111111-2222-3333-4444-555555555555",q.get("ext_agent_host_id"));
        assertEquals("code",q.get("response_type"));
        assertEquals(a.redirectUri,q.get("redirect_uri"));
        assertEquals("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",q.get("scope"));
        assertEquals("https://api.openai.com/v1",q.get("resource"));
        assertEquals(a.state,q.get("state"));
        assertEquals(a.nonce,q.get("nonce"));
        assertEquals("S256",q.get("code_challenge_method"));
        assertEquals(ChatGptAuthContract.pkceChallenge(a.codeVerifier),q.get("code_challenge"));
    }

    @Test public void returningAccountReusesIssuedClientWithoutAgentName()throws Exception{
        ChatGptAuthContract.Attempt a=ChatGptAuthContract.newReturningAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",54321,
            "oaiapp_example","id.token.hint","user@example.com");
        Map<String,String> q=query(a.authorizationUrl());
        assertEquals("oaiapp_example",q.get("client_id"));
        assertFalse(q.containsKey("agent_name_hint"));
        assertEquals("id.token.hint",q.get("id_token_hint"));
        assertEquals("user@example.com",q.get("login_hint"));
    }

    @Test public void returningAccountCanForceFreshPlanConsent()throws Exception{
        ChatGptAuthContract.Attempt a=ChatGptAuthContract.newReturningAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",54321,
            "oaiapp_example","id.token.hint","user@example.com",true);
        Map<String,String> q=query(a.authorizationUrl());
        assertEquals("consent",q.get("prompt"));

        ChatGptAuthContract.Attempt ordinary=ChatGptAuthContract.newReturningAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",54321,
            "oaiapp_example","id.token.hint","user@example.com",false);
        assertFalse(query(ordinary.authorizationUrl()).containsKey("prompt"));
    }

    @Test public void callbackMustMatchStateAndIssuedClient()throws Exception{
        ChatGptAuthContract.Attempt a=ChatGptAuthContract.newRegistrationAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",1455);
        ChatGptAuthContract.Callback ok=a.validateCallback(
            "http://127.0.0.1:1455/auth/callback?code=abc&state="+a.state+"&client_id=oaiapp_123");
        assertEquals("abc",ok.code);
        assertEquals("oaiapp_123",ok.clientId);

        try{a.validateCallback("http://127.0.0.1:1455/auth/callback?code=abc&state=wrong&client_id=oaiapp_123");fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_OAUTH_STATE_MISMATCH",expected.getMessage());}
    }

    @Test public void tokenFormsNeverUseDynamicClientAfterRegistration()throws Exception{
        ChatGptAuthContract.Attempt a=ChatGptAuthContract.newRegistrationAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",1455);
        Map<String,String> form=ChatGptAuthContract.authorizationCodeForm(
            "oaiapp_123","abc",a.codeVerifier,a.redirectUri);
        assertEquals("authorization_code",form.get("grant_type"));
        assertEquals("oaiapp_123",form.get("client_id"));
        assertEquals("https://api.openai.com/v1",form.get("resource"));

        Map<String,String> refresh=ChatGptAuthContract.refreshForm("oaiapp_123","refresh-token");
        assertEquals("refresh_token",refresh.get("grant_type"));
        assertEquals("oaiapp_123",refresh.get("client_id"));
        assertEquals("https://api.openai.com/v1",refresh.get("resource"));
        assertFalse(refresh.containsKey("scope"));
    }

    @Test public void invalidHostOrLoopbackPortFailClosed(){
        try{ChatGptAuthContract.newRegistrationAttempt("not-a-host-id",1455);fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_HOST_ID_INVALID",expected.getMessage());}
        try{ChatGptAuthContract.newRegistrationAttempt("urn:uuid:11111111-2222-3333-4444-555555555555",80);fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_LOOPBACK_PORT_INVALID",expected.getMessage());}
    }
}
