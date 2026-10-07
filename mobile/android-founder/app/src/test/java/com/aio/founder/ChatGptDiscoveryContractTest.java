package com.aio.founder;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ChatGptDiscoveryContractTest {
    @Test public void acceptsExactIssuerAndSecureEndpoints()throws Exception{
        String json="{"+
            "\"issuer\":\"https://auth.openai.com\","+
            "\"authorization_endpoint\":\"https://auth.openai.com/api/accounts/authorize\","+
            "\"token_endpoint\":\"https://auth.openai.com/api/accounts/oauth/token\","+
            "\"jwks_uri\":\"https://auth.openai.com/.well-known/jwks.json\","+
            "\"revocation_endpoint\":\"https://auth.openai.com/api/accounts/oauth/revoke\"}";
        ChatGptDiscoveryContract.Discovery d=ChatGptDiscoveryContract.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("https://auth.openai.com",d.issuer);
        assertEquals("https://auth.openai.com/api/accounts/oauth/revoke",d.revocationEndpoint);
    }

    @Test public void rejectsIssuerSwapOrInsecureRevocation()throws Exception{
        String swapped="{\"issuer\":\"https://evil.example\",\"authorization_endpoint\":\"https://auth.openai.com/a\","+
            "\"token_endpoint\":\"https://auth.openai.com/t\",\"jwks_uri\":\"https://auth.openai.com/j\","+
            "\"revocation_endpoint\":\"https://auth.openai.com/r\"}";
        try{ChatGptDiscoveryContract.parse(swapped.getBytes(StandardCharsets.UTF_8));fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_DISCOVERY_ISSUER_INVALID",expected.getMessage());}

        String insecure="{\"issuer\":\"https://auth.openai.com\",\"authorization_endpoint\":\"https://auth.openai.com/a\","+
            "\"token_endpoint\":\"https://auth.openai.com/t\",\"jwks_uri\":\"https://auth.openai.com/j\","+
            "\"revocation_endpoint\":\"http://auth.openai.com/r\"}";
        try{ChatGptDiscoveryContract.parse(insecure.getBytes(StandardCharsets.UTF_8));fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_DISCOVERY_ENDPOINT_INVALID",expected.getMessage());}
    }
}
