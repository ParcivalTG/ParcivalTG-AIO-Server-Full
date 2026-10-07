package com.aio.founder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidCapabilityContractSyncTest {
    @SuppressWarnings("unchecked")
    @Test public void sharedContractMatchesAndroidRuntimeCatalog()throws Exception{
        byte[] bytes;
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("founder-android-capability-contract-v1.json")){
            assertNotNull("shared capability contract missing from test resources",in);
            bytes=in.readAllBytes();
        }
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(bytes,64*1024);
        assertEquals("aio.android.capability.contract.v1",root.get("schema"));

        StrictProjectionJson.ObjectValue transport=(StrictProjectionJson.ObjectValue)root.get("transport");
        assertEquals((long)PresenceProtocol.ANDROID_CAPABILITY_REQUEST,transport.get("requestFrameType"));
        assertEquals((long)PresenceProtocol.ANDROID_CAPABILITY_REPLY,transport.get("replyFrameType"));
        assertEquals(AndroidCapabilityProtocol.REQUEST_SCHEMA,transport.get("requestSchema"));
        assertEquals(AndroidCapabilityProtocol.REPLY_SCHEMA,transport.get("replySchema"));
        assertEquals((long)AndroidCapabilityProtocol.MAX_REQUEST_BYTES,transport.get("maxRequestBytes"));
        assertEquals((long)AndroidCapabilityProtocol.MAX_REPLY_BYTES,transport.get("maxReplyBytes"));
        assertEquals(Boolean.FALSE,transport.get("grantBearerOnWire"));

        StrictProjectionJson.ObjectValue actions=(StrictProjectionJson.ObjectValue)root.get("actions");
        Set<String> expectedCapabilities=new HashSet<>();
        for(AioAndroidNode.Capability capability:AndroidCapabilityCatalog.remoteCapabilities())
            expectedCapabilities.add(capability.name());
        assertEquals(expectedCapabilities,actions.keySet());

        for(AioAndroidNode.Capability capability:AndroidCapabilityCatalog.remoteCapabilities()){
            AndroidCapabilityCatalog.Spec spec=AndroidCapabilityCatalog.spec(capability);
            Object raw=actions.get(capability.name());
            assertTrue(raw instanceof List);
            List<String> wire=new ArrayList<>();
            for(Object value:(List<Object>)raw){
                assertTrue(value instanceof String);
                wire.add((String)value);
            }
            assertEquals(new java.util.HashSet<>(spec.remoteActions),new java.util.HashSet<>(wire));
        }

        StrictProjectionJson.ObjectValue capabilities=(StrictProjectionJson.ObjectValue)root.get("capabilities");
        assertEquals(java.util.EnumSet.allOf(AioAndroidNode.Capability.class).size(),capabilities.size());
        for(AioAndroidNode.Capability capability:java.util.EnumSet.allOf(AioAndroidNode.Capability.class)){
            AndroidCapabilityCatalog.Spec spec=AndroidCapabilityCatalog.spec(capability);
            StrictProjectionJson.ObjectValue row=(StrictProjectionJson.ObjectValue)capabilities.get(capability.name());
            assertNotNull(capability.name(),row);
            assertEquals(spec.minimumTier.name(),row.get("tier"));
            assertEquals(spec.prerequisite,row.get("prerequisite"));
            assertEquals(spec.defaultGrantMs,row.get("defaultGrantMs"));
            assertEquals(spec.maxGrantMs,row.get("maxGrantMs"));
            assertEquals(Boolean.valueOf(spec.biometricRecommended),row.get("biometric"));
            assertEquals(Boolean.valueOf(spec.remoteEnabled),row.get("remote"));
            Object rawActions=row.get("actions");
            assertTrue(rawActions instanceof List);
            List<String> wireActions=new ArrayList<>();
            for(Object value:(List<Object>)rawActions)wireActions.add((String)value);
            assertEquals(new java.util.HashSet<>(spec.remoteActions),new java.util.HashSet<>(wireActions));
        }

        StrictProjectionJson.ObjectValue local=(StrictProjectionJson.ObjectValue)root.get("nonRemoteActions");
        assertEquals("LOCAL_FOUNDER_ONLY",local.get(AioAndroidNode.Capability.PACKAGE_STAGE.name()));

        StrictProjectionJson.ObjectValue authority=(StrictProjectionJson.ObjectValue)root.get("authority");
        assertEquals(Boolean.TRUE,authority.get("peerBound"));
        assertEquals("ANDROID_ENDPOINT_LOCAL",authority.get("grantCustody"));
        assertEquals(Boolean.TRUE,authority.get("requiresCurrentR5SourceSession"));
        assertEquals(Boolean.TRUE,authority.get("requiresPinnedE2ePeerProof"));
        assertEquals(Boolean.TRUE,authority.get("requiresFounderGrant"));
        assertEquals(Boolean.TRUE,authority.get("requiresTypedReceipt"));
    }
}
