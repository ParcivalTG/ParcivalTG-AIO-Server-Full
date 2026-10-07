package com.aio.founder;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChatGptSessionStoreInstrumentationTest {
    @Test public void issuedDynamicClientSurvivesFailedTokenExchangeWindow()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ChatGptSessionStore store=new ChatGptSessionStore(context);
        store.clearAll();
        try{
            String host=store.hostId();
            store.saveIssuedRegistration(host,"oaiapp_recovery_test");
            ChatGptSessionStore.Registration registration=store.registration();
            assertNotNull(registration);
            assertEquals(host,registration.hostId);
            assertEquals("oaiapp_recovery_test",registration.clientId);
            assertEquals("",registration.subject);
            assertEquals("",registration.email);

            // Clearing tokens/signing out must not discard the issued client mapping.
            store.clearSession();
            ChatGptSessionStore.Registration retained=store.registration();
            assertNotNull(retained);
            assertEquals("oaiapp_recovery_test",retained.clientId);
        }finally{
            store.clearAll();
        }
    }

    @Test public void issuedClientMayBindVerifiedSubjectExactlyOnce()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ChatGptSessionStore store=new ChatGptSessionStore(context);
        store.clearAll();
        try{
            String host=store.hostId();
            String client="oaiapp_subject_binding_test";
            store.saveIssuedRegistration(host,client);

            ChatGptTokenContract.Tokens tokens=new ChatGptTokenContract.Tokens(
                "access","refresh","id","Bearer",3600,1000,4600,4000,
                java.util.Set.of("openid","offline_access","resource.invoke","chatgpt.tokens.use.direct"));
            ChatGptSessionCodec.Session session=new ChatGptSessionCodec.Session(
                host,client,"verified-subject","founder@example.test","Founder",tokens);
            store.save(session);

            ChatGptSessionStore.Registration registration=store.registration();
            assertNotNull(registration);
            assertEquals(client,registration.clientId);
            assertEquals("verified-subject",registration.subject);
            assertEquals("founder@example.test",registration.email);
            assertNotNull(store.load());
        }finally{
            store.clearAll();
        }
    }

    @Test public void issuedClientReplacementFailsClosed()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ChatGptSessionStore store=new ChatGptSessionStore(context);
        store.clearAll();
        try{
            String host=store.hostId();
            store.saveIssuedRegistration(host,"oaiapp_first");
            try{
                store.saveIssuedRegistration(host,"oaiapp_second");
                fail();
            }catch(SecurityException expected){
                assertEquals("CHATGPT_REGISTRATION_CLIENT_MISMATCH",expected.getMessage());
            }
        }finally{
            store.clearAll();
        }
    }
}
