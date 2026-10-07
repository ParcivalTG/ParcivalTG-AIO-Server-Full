package com.aio.founder;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ChatGptProviderAuthTest {
    private static String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static byte[] unsigned(BigInteger value){
        byte[] raw=value.toByteArray();
        return raw.length>1&&raw[0]==0?java.util.Arrays.copyOfRange(raw,1,raw.length):raw;
    }
    private static String jwt(KeyPair pair,String kid,String client,String nonce,long now)throws Exception{
        String h=b64(("{\"alg\":\"RS256\",\"kid\":\""+kid+"\"}").getBytes(StandardCharsets.UTF_8));
        String p=b64(("{\"iss\":\"https://auth.openai.com\",\"aud\":\""+client+
            "\",\"sub\":\"subject-123\",\"email\":\"founder@example.com\",\"name\":\"Founder\""+
            ",\"iat\":"+now+",\"exp\":"+(now+3600)+",\"nonce\":\""+nonce+"\"}").getBytes(StandardCharsets.UTF_8));
        String signing=h+"."+p;
        Signature signature=Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());signature.update(signing.getBytes(StandardCharsets.US_ASCII));
        return signing+"."+b64(signature.sign());
    }
    private static String jwks(KeyPair pair,String kid){
        RSAPublicKey key=(RSAPublicKey)pair.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\""+kid+"\",\"use\":\"sig\",\"alg\":\"RS256\""+
            ",\"n\":\""+b64(unsigned(key.getModulus()))+"\",\"e\":\""+b64(unsigned(key.getPublicExponent()))+"\"}]}";
    }
    private static String tokenJson(String idToken,String access,String refresh,long now){
        return "{\"access_token\":\""+access+"\",\"refresh_token\":\""+refresh+
            "\",\"id_token\":\""+idToken+"\",\"token_type\":\"Bearer\",\"expires_in\":3600"+
            ",\"scope\":\"chatgpt.tokens.use.direct email offline_access openid profile resource.invoke\""+
            ",\"earliest_refresh_at\":"+now+"}";
    }

    @Test public void exchangesCodeAndBindsVerifiedIdentity()throws Exception{
        long now=1700000000L;
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair();
        ChatGptAuthContract.Attempt attempt=ChatGptAuthContract.newRegistrationAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",1455);
        ChatGptAuthContract.Callback callback=new ChatGptAuthContract.Callback(
            "auth-code","oaiapp_123",ChatGptAuthContract.SCOPES);
        String id=jwt(pair,"kid-1","oaiapp_123",attempt.nonce,now);

        try(MockWebServer server=new MockWebServer()){
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","application/json")
                .setBody(tokenJson(id,"access-1","refresh-1",now)));
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","application/json")
                .setBody(jwks(pair,"kid-1")));
            server.start();
            ChatGptProviderClient client=new ChatGptProviderClient(new ChatGptProviderClient.Endpoints(
                server.url("/token").toString(),server.url("/jwks").toString(),
                server.url("/models").toString(),server.url("/responses").toString()));

            ChatGptSessionCodec.Session session=client.exchange(attempt,callback,null,now);
            assertEquals("oaiapp_123",session.clientId);
            assertEquals("subject-123",session.subject);
            assertEquals("founder@example.com",session.email);
            assertEquals("access-1",session.tokens.accessToken);
            assertTrue(session.tokens.planUsageGranted);

            RecordedRequest tokenRequest=server.takeRequest(2,TimeUnit.SECONDS);
            assertNotNull(tokenRequest);
            assertEquals("/token",tokenRequest.getPath());
            String form=tokenRequest.getBody().readUtf8();
            assertTrue(form.contains("grant_type=authorization_code"));
            assertTrue(form.contains("client_id=oaiapp_123"));
            assertTrue(form.contains("code=auth-code"));
            assertFalse(form.contains("client_secret"));
            RecordedRequest jwksRequest=server.takeRequest(2,TimeUnit.SECONDS);
            assertNotNull(jwksRequest);
            assertEquals("/jwks",jwksRequest.getPath());
        }
    }

    @Test public void refreshRotatesTokensAndRejectsSubjectSwitch()throws Exception{
        long now=1700000000L;
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair();
        ChatGptAuthContract.Attempt attempt=ChatGptAuthContract.newReturningAttempt(
            "urn:uuid:11111111-2222-3333-4444-555555555555",1455,
            "oaiapp_123",null,"founder@example.com");
        String id=jwt(pair,"kid-1","oaiapp_123",attempt.nonce,now);
        ChatGptTokenContract.Tokens old=new ChatGptTokenContract.Tokens(
            "old-access","old-refresh","old-id","Bearer",3600,now-3500,now+100,now-3000,
            new java.util.LinkedHashSet<>(java.util.Set.of(
                "openid","offline_access","resource.invoke","chatgpt.tokens.use.direct")));
        ChatGptSessionCodec.Session prior=new ChatGptSessionCodec.Session(
            attempt.hostId,"oaiapp_123","subject-123","founder@example.com","Founder",old);

        try(MockWebServer server=new MockWebServer()){
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","application/json")
                .setBody(tokenJson(id,"new-access","new-refresh",now)));
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","application/json")
                .setBody(jwks(pair,"kid-1")));
            server.start();
            ChatGptProviderClient client=new ChatGptProviderClient(new ChatGptProviderClient.Endpoints(
                server.url("/token").toString(),server.url("/jwks").toString(),
                server.url("/models").toString(),server.url("/responses").toString()));
            ChatGptSessionCodec.Session refreshed=client.refresh(prior,now);
            assertEquals("new-access",refreshed.tokens.accessToken);
            assertEquals("new-refresh",refreshed.tokens.refreshToken);
            assertEquals("subject-123",refreshed.subject);

            RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
            String form=request.getBody().readUtf8();
            assertTrue(form.contains("grant_type=refresh_token"));
            assertTrue(form.contains("refresh_token=old-refresh"));
            assertFalse(form.contains("scope="));
        }
    }
    @Test public void revokesRenewableSessionThroughDiscoveredEndpoint()throws Exception{
        long now=1700000000L;
        ChatGptTokenContract.Tokens tokens=new ChatGptTokenContract.Tokens(
            "access","refresh-secret","id","Bearer",3600,now,now+3600,now+3000,
            new java.util.LinkedHashSet<>(java.util.Set.of("openid","offline_access")));
        ChatGptSessionCodec.Session session=new ChatGptSessionCodec.Session(
            "urn:uuid:11111111-2222-3333-4444-555555555555","oaiapp_123",
            "subject-123","founder@example.com","Founder",tokens);

        try(MockWebServer server=new MockWebServer()){
            server.start();
            server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type","application/json")
                .setBody("{\"issuer\":\"https://auth.openai.com\","+
                    "\"authorization_endpoint\":\""+server.url("/authorize").toString()+"\","+
                    "\"token_endpoint\":\""+server.url("/token-discovered").toString()+"\","+
                    "\"jwks_uri\":\""+server.url("/jwks-discovered").toString()+"\","+
                    "\"revocation_endpoint\":\""+server.url("/revoke").toString()+"\"}"));
            server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
            ChatGptProviderClient client=new ChatGptProviderClient(new ChatGptProviderClient.Endpoints(
                server.url("/discovery").toString(),server.url("/token").toString(),
                server.url("/jwks").toString(),server.url("/models").toString(),
                server.url("/responses").toString()));
            assertTrue(client.revoke(session));
            RecordedRequest discovery=server.takeRequest(2,TimeUnit.SECONDS);
            assertEquals("/discovery",discovery.getPath());
            RecordedRequest revoke=server.takeRequest(2,TimeUnit.SECONDS);
            assertEquals("/revoke",revoke.getPath());
            String form=revoke.getBody().readUtf8();
            assertTrue(form.contains("token=refresh-secret"));
            assertTrue(form.contains("token_type_hint=refresh_token"));
            assertTrue(form.contains("client_id=oaiapp_123"));
        }
    }

}
