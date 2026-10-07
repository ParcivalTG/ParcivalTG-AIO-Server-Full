package com.aio.founder;

import org.junit.Test;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ChatGptTokenContractTest {
    @Test public void parsesCompleteRenewablePlanSession()throws Exception{
        String json="{"+
            "\"access_token\":\"access-1\","+
            "\"refresh_token\":\"refresh-1\","+
            "\"id_token\":\"id-1\","+
            "\"token_type\":\"Bearer\","+
            "\"expires_in\":3600,"+
            "\"scope\":\"chatgpt.tokens.use.direct email offline_access openid profile resource.invoke\","+
            "\"earliest_refresh_at\":1700003000}";
        ChatGptTokenContract.Tokens tokens=ChatGptTokenContract.parse(
            json.getBytes(StandardCharsets.UTF_8),1700000000L);
        assertEquals("access-1",tokens.accessToken);
        assertEquals("refresh-1",tokens.refreshToken);
        assertEquals("id-1",tokens.idToken);
        assertEquals(3600,tokens.expiresInSeconds);
        assertTrue(tokens.planUsageGranted);
        assertTrue(tokens.scopes.contains("offline_access"));
        assertEquals(1700003000L,tokens.earliestRefreshAt);
        assertEquals(1700003600L,tokens.expiresAt);
    }

    @Test public void validIdentityWithoutPlanScopeStaysSignedInButDisablesPlanUsage()throws Exception{
        String json="{"+
            "\"access_token\":\"access-1\","+
            "\"refresh_token\":\"refresh-1\","+
            "\"id_token\":\"id-1\","+
            "\"token_type\":\"Bearer\","+
            "\"expires_in\":3600,"+
            "\"scope\":\"email offline_access openid profile resource.invoke\","+
            "\"earliest_refresh_at\":1700003000}";
        ChatGptTokenContract.Tokens tokens=ChatGptTokenContract.parse(
            json.getBytes(StandardCharsets.UTF_8),1700000000L);
        assertFalse(tokens.planUsageGranted);
    }

    @Test public void rejectsMissingRefreshWrongTypeAndImpossibleExpiry()throws Exception{
        String base="{"+
            "\"access_token\":\"access-1\","+
            "\"id_token\":\"id-1\","+
            "\"token_type\":\"Bearer\","+
            "\"expires_in\":3600,"+
            "\"scope\":\"chatgpt.tokens.use.direct offline_access openid\","+
            "\"earliest_refresh_at\":1700003000}";
        try{ChatGptTokenContract.parse(base.getBytes(StandardCharsets.UTF_8),1700000000L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_REFRESH_TOKEN_REQUIRED",expected.getMessage());}

        String wrong=base.replace("\"token_type\":\"Bearer\"","\"token_type\":\"MAC\"")
            .replace("\"access_token\":\"access-1\"","\"access_token\":\"access-1\",\"refresh_token\":\"r\"");
        try{ChatGptTokenContract.parse(wrong.getBytes(StandardCharsets.UTF_8),1700000000L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_TOKEN_TYPE_INVALID",expected.getMessage());}

        String expiry=base.replace("\"expires_in\":3600","\"expires_in\":999999")
            .replace("\"access_token\":\"access-1\"","\"access_token\":\"access-1\",\"refresh_token\":\"r\"");
        try{ChatGptTokenContract.parse(expiry.getBytes(StandardCharsets.UTF_8),1700000000L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_TOKEN_EXPIRY_INVALID",expected.getMessage());}
    }
}
