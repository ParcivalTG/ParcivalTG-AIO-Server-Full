package com.aio.founder;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersistentNodeBootCourtInstrumentationTest {
    @Test public void prepareFounderEnabledState(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context,AioPersistentNodeService.class));
        boolean committed=context.getSharedPreferences(
            AioPersistentNodePolicy.PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean(AioPersistentNodePolicy.KEY_ENABLED,true).commit();
        assertTrue(committed);
        assertTrue(AioPersistentNodeController.founderEnabled(context));
    }

    @Test public void cleanupFounderEnabledState(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(AioPersistentNodePolicy.PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean(AioPersistentNodePolicy.KEY_ENABLED,false).commit();
        context.stopService(new Intent(context,AioPersistentNodeService.class));
        assertFalse(AioPersistentNodeController.founderEnabled(context));
    }
}
