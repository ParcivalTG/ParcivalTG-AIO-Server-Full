package com.aio.founder;

import org.junit.Test;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ChatGptSessionCodecTest {
    @Test public void roundTripsAtomicCredentialRecord()throws Exception{
        ChatGptTokenContract.Tokens tokens=new ChatGptTokenContract.Tokens(
            "access","refresh","id","Bearer",3600,1700000000L,1700003600L,1700003000L,
            new LinkedHashSet<>(Set.of("openid","offline_access","resource.invoke","chatgpt.tokens.use.direct")));
        ChatGptSessionCodec.Session session=new ChatGptSessionCodec.Session(
            "urn:uuid:11111111-2222-3333-4444-555555555555","oaiapp_123",
            "subject-1","user@example.com","Founder",tokens);
        String encoded=ChatGptSessionCodec.encode(session);
        ChatGptSessionCodec.Session decoded=ChatGptSessionCodec.decode(encoded);
        assertEquals(session.hostId,decoded.hostId);
        assertEquals(session.clientId,decoded.clientId);
        assertEquals(session.subject,decoded.subject);
        assertEquals("user@example.com",decoded.email);
        assertEquals("access",decoded.tokens.accessToken);
        assertEquals("refresh",decoded.tokens.refreshToken);
        assertTrue(decoded.tokens.planUsageGranted);
        assertEquals(1700003600L,decoded.tokens.expiresAt);
    }

    @Test public void corruptExpiryOrClientFailsClosed()throws Exception{
        ChatGptTokenContract.Tokens tokens=new ChatGptTokenContract.Tokens(
            "access","refresh","id","Bearer",3600,1700000000L,1700003600L,1700003000L,
            new LinkedHashSet<>(Set.of("openid","offline_access")));
        String encoded=ChatGptSessionCodec.encode(new ChatGptSessionCodec.Session(
            "urn:uuid:11111111-2222-3333-4444-555555555555","oaiapp_123",
            "subject-1","","",tokens));
        try{ChatGptSessionCodec.decode(encoded.replace("\"expires_at\":1700003600","\"expires_at\":1700009999"));fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_SESSION_TIME_INCONSISTENT",expected.getMessage());}
        try{ChatGptSessionCodec.decode(encoded.replace("oaiapp_123","dynamic_agent_client"));fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_SESSION_CLIENT_INVALID",expected.getMessage());}
    }
}
