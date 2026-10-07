package com.aio.founder;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.*;

public class PresenceAuthorityClientTest {
    private static String lease(long nowMs,long expiresMs,String... capabilities)throws Exception{
        org.json.JSONArray caps=new org.json.JSONArray();
        for(String capability:capabilities)caps.put(capability);
        JSONObject lease=new JSONObject();
        lease.put("schema","aio.private-gateway.lease.v1");
        lease.put("leaseId",UUID.randomUUID().toString().replace("-",""));
        lease.put("principalId","android-founder-r5");
        lease.put("capabilities",caps);
        lease.put("dataClasses",new org.json.JSONArray().put("FOUNDER_ONLY"));
        lease.put("issuedAt",Instant.ofEpochMilli(nowMs).toString());
        lease.put("expiresAt",Instant.ofEpochMilli(expiresMs).toString());
        lease.put("maxUses",200);
        lease.put("signature","AA");
        return lease.toString();
    }

    @Test public void refreshWindowIsTenMinutesAndMissingAuthorityRefreshes()throws Exception{
        long now=1_800_000_000_000L;
        assertTrue(PresenceAuthorityClient.needsRefresh(null,now));
        assertTrue(PresenceAuthorityClient.needsRefresh(
            lease(now,now+PresenceAuthorityClient.REFRESH_WINDOW_MS,
                "gateway.status","windows.objective.submit"),now));
        assertFalse(PresenceAuthorityClient.needsRefresh(
            lease(now,now+PresenceAuthorityClient.REFRESH_WINDOW_MS+1,
                "gateway.status","windows.objective.submit"),now));
    }

    @Test public void authorityReplyRequiresStatusAndObjectiveCapabilities()throws Exception{
        long now=1_800_000_000_000L;
        String good=lease(now,now+3_000_000L,"gateway.status","windows.objective.submit");
        JSONObject root=new JSONObject()
            .put("schema","aio.presence.authority.refresh.reply.v1")
            .put("lease",new JSONObject(good));
        PresenceProtocol.Frame frame=new PresenceProtocol.Frame(
            PresenceProtocol.AUTHORITY_REFRESH_REPLY,(byte)0,UUID.randomUUID(),
            root.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(good,
            new JSONObject(AioProjectionMembrane.absorbAuthorityLease(frame,now)).toString());

        String bad=lease(now,now+3_000_000L,"windows.objective.submit");
        JSONObject badRoot=new JSONObject()
            .put("schema","aio.presence.authority.refresh.reply.v1")
            .put("lease",new JSONObject(bad));
        PresenceProtocol.Frame badFrame=new PresenceProtocol.Frame(
            PresenceProtocol.AUTHORITY_REFRESH_REPLY,(byte)0,UUID.randomUUID(),
            badRoot.toString().getBytes(StandardCharsets.UTF_8));
        try{
            AioProjectionMembrane.absorbAuthorityLease(badFrame,now);
            fail();
        }catch(SecurityException expected){
            assertEquals("CAPABILITY_NOT_GRANTED:gateway.status",expected.getMessage());
        }
    }

    @Test public void gatewayStatusProofMustBeCorrelatedAndReady()throws Exception{
        String requestId=UUID.randomUUID().toString();
        JSONObject receipt=new JSONObject()
            .put("schema","aio.private-gateway.receipt.v1")
            .put("requestId",requestId)
            .put("action","gateway.status")
            .put("success",true)
            .put("result",new JSONObject()
                .put("status","READY")
                .put("schema","aio.private-gateway.v1"));
        AioProjectionMembrane.validateGatewayStatusReceipt(receipt,requestId);

        try{
            AioProjectionMembrane.validateGatewayStatusReceipt(receipt,UUID.randomUUID().toString());
            fail();
        }catch(SecurityException expected){
            assertEquals("GATEWAY_STATUS_RECEIPT_INVALID",expected.getMessage());
        }
    }
}
