package com.aio.founder;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class R5LiveIntegrationInstrumentationTest {
    private static final String PRIVATE_FIXTURE="r5-live-pairing.json";

    @Test public void realAndroidTransportReachesWindowsCoreAndGetsObjectiveReceipt()throws Exception{
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File pairingFile=new File(target.getFilesDir(),PRIVATE_FIXTURE);
        Assume.assumeTrue("private live pairing fixture not injected",pairingFile.isFile());

        String pairingText=new String(Files.readAllBytes(pairingFile.toPath()),StandardCharsets.UTF_8);
        FounderPairingBundle bundle=FounderPairingBundle.parse(pairingText,System.currentTimeMillis());
        assertNotNull(bundle.cloud);

        byte[] admission=Base64.getDecoder().decode(bundle.cloud.peerAdmissionKeyB64);
        byte[] tunnel=Base64.getDecoder().decode(bundle.cloud.tunnelKeyB64);
        byte[] witnessSecret=PresenceProtocol.decodeWitnessSecret(bundle.witnessSecretB64);
        byte[] witness=null,command=null,envelope=null,plaintext=null;
        E2eCodec.Request request=null;
        PresenceTransport transport=null;
        try{
            witness=witness(bundle.clientId,witnessSecret);
            CloudPresenceTransport cloud=new CloudPresenceTransport(
                bundle.cloud.wssUrl,bundle.cloud.androidPeerId,
                admission,tunnel,bundle.cloud.windowsPeerId);

            FounderPresenceSessionController.Ready ready=
                new FounderPresenceSessionController().connect(null,()->cloud,witness);
            transport=ready.transport;
            assertEquals("R5_CLOUD",transport.label());
            assertEquals(0,ready.hello.flags);
            assertEquals(0,ready.status.flags);
            assertTrue(ready.pingRoundTripMs>=0);

            JSONObject hello=new JSONObject(new String(ready.hello.payload,StandardCharsets.UTF_8));
            assertEquals("aio.presence.fabric.v0",hello.getString("schema"));
            assertEquals(bundle.clientId,hello.getString("principalId"));
            assertEquals("VERIFIED",hello.getString("witness"));

            JSONObject status=new JSONObject(new String(ready.status.payload,StandardCharsets.UTF_8));
            assertEquals("aio.presence.status.v0",status.getString("schema"));
            assertEquals("READY",status.getString("status"));

            PresenceAuthorityClient.Verified authority=PresenceAuthorityClient.refreshAndVerify(
                transport,bundle.pinnedGatewaySpkiB64Url,System.currentTimeMillis());
            assertTrue(authority.leaseExpiresAtUnixMs>System.currentTimeMillis()+30*60_000L);
            AioProjectionMembrane.validateLeaseCapabilities(
                authority.leaseJson,System.currentTimeMillis(),
                "gateway.status",FounderIntentCapsule.ACTION);

            String intentId=UUID.randomUUID().toString();
            UUID requestId=UUID.randomUUID();
            FounderDialogueSubmit submit=new FounderDialogueSubmit(
                intentId,
                "R5 live Android integration court: persist this bounded objective proposal only.",
                "LOCAL_ONLY","AIO",authority.leaseJson,
                new String(witness,StandardCharsets.UTF_8));
            command=AioProjectionMembrane.projectIntentCommand(submit);
            request=E2eCodec.encrypt(bundle.pinnedGatewaySpkiB64Url,
                requestId.toString(),System.currentTimeMillis(),command);
            envelope=AioProjectionMembrane.projectEnvelope(request);

            transport.setReadTimeoutMillis(15_000);
            PresenceProtocol.Frame response=FounderPresenceSessionController.exchange(
                transport,PresenceProtocol.E2E,PresenceProtocol.E2E_REPLY,requestId,envelope);
            assertEquals(0,response.flags);

            plaintext=request.context.decrypt(
                AioProjectionMembrane.absorbEncryptedResponse(response.payload));
            JSONObject receipt=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));
            assertEquals("aio.private-gateway.receipt.v1",receipt.getString("schema"));
            assertEquals(requestId.toString(),receipt.getString("requestId"));
            assertEquals(FounderIntentCapsule.ACTION,receipt.getString("action"));
            assertTrue(receipt.getBoolean("success"));

            JSONObject result=receipt.getJSONObject("result");
            assertEquals("aio.founder-objective.accepted.v1",result.getString("schema"));
            assertEquals(intentId,result.getString("taskId"));
            assertTrue(result.getLong("coreRevision")>=1);
            JSONObject binding=result.getJSONObject("objectiveBinding");
            assertTrue(binding.getString("objectiveId").startsWith("AIO-FOUNDER-"));
            assertEquals(1,binding.getInt("objectiveVersion"));
            assertEquals(64,binding.getString("objectiveFingerprint").length());
        }finally{
            if(request!=null)request.context.close();
            if(transport!=null)transport.close();
            if(witness!=null)Arrays.fill(witness,(byte)0);
            if(command!=null)Arrays.fill(command,(byte)0);
            if(envelope!=null)Arrays.fill(envelope,(byte)0);
            if(plaintext!=null)Arrays.fill(plaintext,(byte)0);
            Arrays.fill(admission,(byte)0);
            Arrays.fill(tunnel,(byte)0);
            Arrays.fill(witnessSecret,(byte)0);
        }
    }

    private static byte[] witness(String clientId,byte[] secret)throws Exception{
        long timestamp=System.currentTimeMillis();
        byte[] random=new byte[16];new SecureRandom().nextBytes(random);
        StringBuilder nonce=new StringBuilder(32);
        try{
            for(byte value:random)nonce.append(String.format(Locale.ROOT,"%02x",value&0xff));
        }finally{Arrays.fill(random,(byte)0);}
        String canonical="AIO_PRESENCE_WITNESS_V0\n"+clientId+"\n"+timestamp+"\n"+nonce;
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        byte[] digest=mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        try{
            JSONObject root=new JSONObject();
            root.put("clientId",clientId);
            root.put("timestampUnixMs",timestamp);
            root.put("nonce",nonce.toString());
            root.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
            return root.toString().getBytes(StandardCharsets.UTF_8);
        }finally{Arrays.fill(digest,(byte)0);}
    }
}
