package com.aio.founder;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChatGptAndroidCapabilityInstrumentationTest {
    @Test public void founderGrantAllowsRealLocalGptResourceDispatchThenRevocationDenies()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AioAndroidNode node=new AioAndroidNode();
        AndroidCapabilityBroker files=new AndroidCapabilityBroker(context);
        AndroidCapabilityDispatcher dispatcher=new AndroidCapabilityDispatcher(context,node,files);
        String peer=ChatGptAndroidToolContract.LOCAL_PEER_ID;

        AndroidCapabilityCatalog.Spec spec=
            AndroidCapabilityCatalog.spec(AioAndroidNode.Capability.RESOURCE_STATUS);
        node.grant(peer,AioAndroidNode.Capability.RESOURCE_STATUS,
            spec.minimumTier,AioAndroidNode.Privacy.FOUNDER_ONLY,5*60_000L);

        ChatGptAndroidToolContract.Invocation invocation=
            ChatGptAndroidToolContract.parse(
                "{\"action\":\"resource.status\",\"args_json\":\"{}\"}");
        byte[] payload=invocation.payload.clone();
        try{
            AndroidCapabilityDispatcher.Result accepted=
                dispatcher.dispatch(peer,UUID.randomUUID(),payload);
            assertTrue(accepted.accepted);
            assertEquals("OK",accepted.code);
            assertEquals("RESOURCE_STATUS",accepted.capability);
            assertEquals("resource.status",accepted.action);
            JSONObject reply=new JSONObject(new String(accepted.payload,StandardCharsets.UTF_8));
            assertEquals(AndroidCapabilityProtocol.REPLY_SCHEMA,reply.getString("schema"));
            assertTrue(reply.getBoolean("accepted"));
            assertEquals("OK",reply.getString("code"));
            JSONObject result=reply.getJSONObject("result");
            assertTrue(result.has("battery"));
            assertTrue(result.has("memory"));
            Arrays.fill(accepted.payload,(byte)0);

            node.revokePeerCapability(peer,AioAndroidNode.Capability.RESOURCE_STATUS);
            AndroidCapabilityDispatcher.Result denied=
                dispatcher.dispatch(peer,UUID.randomUUID(),payload);
            assertFalse(denied.accepted);
            assertEquals("no-active-grant",denied.code);
            JSONObject deniedReply=new JSONObject(new String(denied.payload,StandardCharsets.UTF_8));
            assertFalse(deniedReply.getBoolean("accepted"));
            assertEquals("no-active-grant",deniedReply.getString("code"));
            Arrays.fill(denied.payload,(byte)0);
        }finally{
            Arrays.fill(invocation.payload,(byte)0);
            Arrays.fill(payload,(byte)0);
        }
    }
}
