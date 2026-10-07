package com.aio.founder;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

final class PresenceAuthorityClient {
    static final String REQUEST_SCHEMA="aio.presence.authority.refresh.v1";
    static final long REFRESH_WINDOW_MS=10*60_000L;

    static final class Verified {
        final String leaseJson;
        final long leaseExpiresAtUnixMs;
        final long gatewayRoundTripMs;
        Verified(String leaseJson,long leaseExpiresAtUnixMs,long gatewayRoundTripMs){
            this.leaseJson=leaseJson;
            this.leaseExpiresAtUnixMs=leaseExpiresAtUnixMs;
            this.gatewayRoundTripMs=gatewayRoundTripMs;
        }
    }

    private PresenceAuthorityClient(){}

    static boolean needsRefresh(String leaseJson,long nowUnixMs)throws Exception{
        if(nowUnixMs<0)throw new IllegalArgumentException("AUTHORITY_TIME_INVALID");
        if(leaseJson==null||leaseJson.isBlank())return true;
        long expires=AioProjectionMembrane.leaseExpiresAtUnixMs(leaseJson);
        return expires<=nowUnixMs+REFRESH_WINDOW_MS;
    }

    static Verified refreshAndVerify(PresenceTransport transport,String pinnedGatewaySpki,long nowUnixMs)throws Exception{
        if(transport==null)throw new IllegalArgumentException("AUTHORITY_TRANSPORT_REQUIRED");
        E2eCodec.pinnedKey(pinnedGatewaySpki);

        byte[] authorityPayload=("{\"schema\":\""+REQUEST_SCHEMA+"\"}").getBytes(StandardCharsets.UTF_8);
        PresenceProtocol.Frame authority;
        try{
            authority=FounderPresenceSessionController.exchange(
                transport,
                PresenceProtocol.AUTHORITY_REFRESH,
                PresenceProtocol.AUTHORITY_REFRESH_REPLY,
                UUID.randomUUID(),
                authorityPayload);
        }finally{
            Arrays.fill(authorityPayload,(byte)0);
        }
        String lease=AioProjectionMembrane.absorbAuthorityLease(authority,nowUnixMs);

        E2eCodec.Request request=null;
        byte[] command=null,envelope=null,plaintext=null;
        UUID requestId=UUID.randomUUID();
        long started=System.nanoTime();
        try{
            command=AioProjectionMembrane.projectGatewayStatusCommand(lease,System.currentTimeMillis());
            request=E2eCodec.encrypt(pinnedGatewaySpki,requestId.toString(),System.currentTimeMillis(),command);
            envelope=AioProjectionMembrane.projectEnvelope(request);
            transport.setReadTimeoutMillis(15_000);
            PresenceProtocol.Frame response=FounderPresenceSessionController.exchange(
                transport,PresenceProtocol.E2E,PresenceProtocol.E2E_REPLY,requestId,envelope);
            if(response.flags!=0)throw new SecurityException("GATEWAY_STATUS_REJECTED");
            plaintext=request.context.decrypt(AioProjectionMembrane.absorbEncryptedResponse(response.payload));
            JSONObject receipt=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));
            AioProjectionMembrane.validateGatewayStatusReceipt(receipt,requestId.toString());
            return new Verified(
                lease,
                AioProjectionMembrane.leaseExpiresAtUnixMs(lease),
                (System.nanoTime()-started)/1_000_000L);
        }finally{
            if(request!=null)request.context.close();
            if(command!=null)Arrays.fill(command,(byte)0);
            if(envelope!=null)Arrays.fill(envelope,(byte)0);
            if(plaintext!=null)Arrays.fill(plaintext,(byte)0);
            try{transport.setReadTimeoutMillis(7000);}catch(Exception ignored){}
        }
    }
}
