package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatGptProjectionPolicyTest {
    @Test public void localClassesNeverCrossProviderBoundary(){
        assertFalse(ChatGptProjectionPolicy.mayProject("LOCAL_ONLY"));
        assertFalse(ChatGptProjectionPolicy.mayProject("TRADE_SECRET_LOCAL_ONLY"));
    }

    @Test public void externalClassesAreExplicitAndDistinct(){
        assertTrue(ChatGptProjectionPolicy.mayProject("EXTERNAL_MINIMIZED"));
        assertTrue(ChatGptProjectionPolicy.mayProject("EXTERNAL_ALLOWED"));
        assertTrue(ChatGptProjectionPolicy.requiresMinimization("EXTERNAL_MINIMIZED"));
        assertFalse(ChatGptProjectionPolicy.requiresMinimization("EXTERNAL_ALLOWED"));
    }

    @Test public void unknownClassFailsClosed(){
        try{ChatGptProjectionPolicy.requireProjection("PUBLIC");fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_PRIVACY_PROJECTION_DENIED",expected.getMessage());}
        try{ChatGptProjectionPolicy.requireProjection(null);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_PRIVACY_PROJECTION_DENIED",expected.getMessage());}
    }

    @Test public void minimizationRequiresExplicitSafeProjection(){
        try{ChatGptProjectionPolicy.projectText("EXTERNAL_MINIMIZED","raw secret",null);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_MINIMIZED_PROJECTION_REQUIRED",expected.getMessage());}
        assertEquals("safe summary",ChatGptProjectionPolicy.projectText(
            "EXTERNAL_MINIMIZED","raw secret","safe summary"));
        assertEquals("ordinary prompt",ChatGptProjectionPolicy.projectText(
            "EXTERNAL_ALLOWED","ordinary prompt",null));
    }
}
