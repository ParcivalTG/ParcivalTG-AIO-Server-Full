package com.aio.founder;

import org.junit.Test;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import static org.junit.Assert.*;

public class ChatGptProviderStateTest {
    private static ChatGptSessionCodec.Session session(boolean plan){
        Set<String> scopes=new LinkedHashSet<>(List.of("openid","offline_access","resource.invoke"));
        if(plan)scopes.add("chatgpt.tokens.use.direct");
        ChatGptTokenContract.Tokens tokens=new ChatGptTokenContract.Tokens(
            "a","r","id","Bearer",3600,1000,4600,4000,scopes);
        return new ChatGptSessionCodec.Session(
            "urn:uuid:11111111-2222-3333-4444-555555555555","oaiapp_123",
            "subject","founder@example.com","Founder",tokens);
    }

    @Test public void authorizedCatalogChoosesFirstVisibleModel(){
        ChatGptProviderState state=new ChatGptProviderState();
        state.authorized(session(true),List.of(
            new ChatGptResponsesContract.Model("gpt-a","A"),
            new ChatGptResponsesContract.Model("gpt-b","B")));
        ChatGptProviderState.Snapshot snapshot=state.snapshot();
        assertEquals(ChatGptProviderState.Phase.READY,snapshot.phase);
        assertEquals("gpt-a",snapshot.selectedModel);
        assertEquals(2,snapshot.models.size());
        assertTrue(snapshot.planUsageGranted);
        assertEquals("founder@example.com",snapshot.accountLabel);
    }

    @Test public void modelSelectionMustComeFromLiveCatalog(){
        ChatGptProviderState state=new ChatGptProviderState();
        state.authorized(session(true),List.of(new ChatGptResponsesContract.Model("gpt-a","A")));
        try{state.selectModel("gpt-hidden");fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_MODEL_NOT_AVAILABLE",expected.getMessage());}
        state.selectModel("gpt-a");
        assertEquals("gpt-a",state.snapshot().selectedModel);
    }

    @Test public void catalogRefreshPreservesSelectionWhenStillVisible(){
        ChatGptProviderState state=new ChatGptProviderState();
        state.authorized(session(true),List.of(
            new ChatGptResponsesContract.Model("gpt-a","A"),
            new ChatGptResponsesContract.Model("gpt-b","B")));
        state.selectModel("gpt-b");
        state.authorized(session(true),List.of(
            new ChatGptResponsesContract.Model("gpt-b","B"),
            new ChatGptResponsesContract.Model("gpt-c","C")));
        assertEquals("gpt-b",state.snapshot().selectedModel);
    }

    @Test public void identityWithoutPlanScopeRemainsSignedInButHeldForInference(){
        ChatGptProviderState state=new ChatGptProviderState();
        state.authorized(session(false),List.of(new ChatGptResponsesContract.Model("gpt-a","A")));
        assertEquals(ChatGptProviderState.Phase.SIGNED_IN_NO_PLAN,state.snapshot().phase);
        assertFalse(state.snapshot().planUsageGranted);
    }
}
