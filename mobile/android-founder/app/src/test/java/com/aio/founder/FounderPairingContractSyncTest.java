package com.aio.founder;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class FounderPairingContractSyncTest {
    @SuppressWarnings("unchecked")
    @Test public void sharedPairingContractMatchesParserConstants()throws Exception{
        byte[] bytes;
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("founder-android-pairing-contract-v1.json")){
            assertNotNull(in);bytes=in.readAllBytes();
        }
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(bytes,32*1024);
        assertEquals("aio.founder.android.pairing.contract.v1",root.get("schema"));
        assertEquals(FounderPairingBundle.SCHEMA,root.get("bundleSchema"));
        assertEquals((long)FounderPairingBundle.MAX_BYTES,root.get("maxBytes"));
        assertEquals(3600000L,root.get("maxLifetimeMs"));

        Set<String> rootFields=new HashSet<>();
        for(Object row:(List<Object>)root.get("rootFields"))rootFields.add((String)row);
        assertEquals(Set.of("schema","issuedAtUnixMs","expiresAtUnixMs","clientId","directEndpoint",
            "witnessSecretB64","pinnedGatewaySpkiB64Url","signedLeaseJson","cloud"),rootFields);

        Set<String> cloudFields=new HashSet<>();
        for(Object row:(List<Object>)root.get("cloudFields"))cloudFields.add((String)row);
        assertEquals(Set.of("wssUrl","androidPeerId","windowsPeerId","peerAdmissionKeyB64","tunnelKeyB64"),cloudFields);

        StrictProjectionJson.ObjectValue rules=(StrictProjectionJson.ObjectValue)root.get("rules");
        assertEquals("MUST_NEVER_BE_PRESENT",rules.get("r5MasterSecret"));
        assertEquals("ANDROID_KEYSTORE_AES_GCM",rules.get("androidSecretCustody"));
        assertEquals("GENERATION_FENCED_FAIL_CLOSED",rules.get("import"));
    }
}
