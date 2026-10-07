package com.aio.founder;

import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * Universal compatibility membrane.
 * Conventional JSON/Android strings are edge shadows; AIO internal state remains AioPresenceField.
 */
final class AioProjectionMembrane {
    private AioProjectionMembrane() {}

    static byte[] projectWitnessCell(String clientId, long timestampUnixMs, String nonce, String signature) throws Exception {
        JSONObject j = new JSONObject();
        j.put("clientId", clientId);
        j.put("timestampUnixMs", timestampUnixMs);
        j.put("nonce", nonce);
        j.put("signature", signature);
        return j.toString().getBytes(StandardCharsets.UTF_8);
    }

    static void absorbHello(AioPresenceField field, PresenceProtocol.Frame frame) throws Exception {
        JSONObject root = new JSONObject(new String(frame.payload, StandardCharsets.UTF_8));
        if (!"aio.presence.fabric.v0".equals(root.getString("schema")) || root.getString("principalId").isEmpty())
            throw new SecurityException("HELLO_SCHEMA_INVALID");
        JSONObject core = root.optJSONObject("core");
        field.absorbHello(root.getString("principalId"), core == null ? "" : core.optString("schema"));
    }

    static void absorbStatus(AioPresenceField field, PresenceProtocol.Frame frame, long latencyMs) throws Exception {
        JSONObject root = new JSONObject(new String(frame.payload, StandardCharsets.UTF_8));
        if (!"aio.presence.status.v0".equals(root.getString("schema"))) throw new SecurityException("STATUS_SCHEMA_INVALID");
        JSONObject core = root.optJSONObject("core");
        field.absorbStatus(frame.flags == 0 && "READY".equals(root.getString("status")),
                core == null ? "" : core.optString("schema"), latencyMs);
    }

    static AioPresenceField.AndroidShadow projectAndroid(AioPresenceField field) {
        return field.projectAndroidShadow();
    }

    static void validateLeaseImport(String leaseText, long now) throws Exception {
        validateLeaseCapabilities(leaseText,now,FounderIntentCapsule.ACTION);
    }

    static void validateLeaseCapabilities(String leaseText,long now,String... required) throws Exception {
        if (leaseText == null || leaseText.length() > 32_768) throw new SecurityException("LEASE_IMPORT_BUDGET");
        JSONObject lease = new JSONObject(leaseText);
        if (!"aio.private-gateway.lease.v1".equals(lease.getString("schema")))
            throw new SecurityException("LEASE_SCHEMA_INVALID");
        if (lease.getString("leaseId").isEmpty() || lease.getString("principalId").isEmpty()
                || lease.getString("signature").isEmpty() || lease.getInt("maxUses") < 1)
            throw new SecurityException("LEASE_IMPORT_INCOMPLETE");
        JSONArray capabilities = lease.getJSONArray("capabilities");
        for(String need:required){
            boolean permitted=false;
            for(int i=0;i<capabilities.length();i++)
                if(need.equals(capabilities.getString(i)))permitted=true;
            if(!permitted)throw new SecurityException("CAPABILITY_NOT_GRANTED:"+need);
        }
        if (OffsetDateTime.parse(lease.getString("expiresAt")).toInstant().toEpochMilli() <= now)
            throw new SecurityException("LEASE_EXPIRED");
        // Only the gateway verifies its signature, revocation, principal binding, and remaining uses.
    }

    static long leaseExpiresAtUnixMs(String leaseText) throws Exception {
        if(leaseText==null||leaseText.length()>32_768)throw new SecurityException("LEASE_IMPORT_BUDGET");
        JSONObject lease=new JSONObject(leaseText);
        if(!"aio.private-gateway.lease.v1".equals(lease.getString("schema")))
            throw new SecurityException("LEASE_SCHEMA_INVALID");
        return OffsetDateTime.parse(lease.getString("expiresAt")).toInstant().toEpochMilli();
    }

    static String absorbAuthorityLease(PresenceProtocol.Frame frame,long now) throws Exception {
        if(frame.flags!=0)throw new SecurityException("AUTHORITY_REFRESH_REJECTED");
        JSONObject root=new JSONObject(new String(frame.payload,StandardCharsets.UTF_8));
        if(!"aio.presence.authority.refresh.reply.v1".equals(root.getString("schema")))
            throw new SecurityException("AUTHORITY_REFRESH_SCHEMA_INVALID");
        JSONObject lease=root.getJSONObject("lease");
        String encoded=lease.toString();
        validateLeaseCapabilities(encoded,now,"gateway.status",FounderIntentCapsule.ACTION);
        return encoded;
    }

    static byte[] projectGatewayStatusCommand(String leaseText,long now) throws Exception {
        validateLeaseCapabilities(leaseText,now,"gateway.status");
        JSONObject command=new JSONObject();
        command.put("action","gateway.status");
        command.put("args",new JSONObject());
        command.put("lease",new JSONObject(leaseText));
        return command.toString().getBytes(StandardCharsets.UTF_8);
    }

    static void validateGatewayStatusReceipt(JSONObject receipt,String requestId) throws Exception {
        if(!"aio.private-gateway.receipt.v1".equals(receipt.getString("schema"))||
           !requestId.equals(receipt.getString("requestId"))||
           !"gateway.status".equals(receipt.getString("action"))||
           !receipt.getBoolean("success"))
            throw new SecurityException("GATEWAY_STATUS_RECEIPT_INVALID");
        JSONObject result=receipt.getJSONObject("result");
        if(!"READY".equals(result.getString("status"))||
           !"aio.private-gateway.v1".equals(result.getString("schema")))
            throw new SecurityException("GATEWAY_STATUS_RESULT_INVALID");
    }

    static byte[] projectIntentCommand(FounderDialogueSubmit submit) throws Exception {
        validateLeaseImport(submit.lease, System.currentTimeMillis());
        JSONObject args = new JSONObject();
        args.put("schema", "aio.founder-dialogue.submit.v1");
        args.put("intentId", submit.intentId); args.put("text", submit.text);
        args.put("privacyClass", submit.privacyClass); args.put("provider", "AIO");
        JSONObject command = new JSONObject(); command.put("action", FounderIntentCapsule.ACTION);
        command.put("args", args);
        command.put("lease", new JSONObject(submit.lease));
        command.put("presenceWitness", new JSONObject(submit.presenceWitness));
        return command.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Rejects the old caller-authority shape if a boundary ever receives it. */
    static void rejectCallerObjectiveFields(JSONObject args) {
        if (args.has("targetObjectiveId") || args.has("expectedObjectiveVersion") || args.has("acceptanceCriteria"))
            throw new SecurityException("CALLER_OBJECTIVE_FIELDS_FORBIDDEN");
    }

    static byte[] projectEnvelope(E2eCodec.Request request) throws Exception {
        JSONObject envelope = new JSONObject();
        envelope.put("schema", E2eCodec.REQUEST_SCHEMA);
        envelope.put("requestId", request.requestId);
        envelope.put("clientPublicKeyB64", request.clientPublicKeyB64);
        envelope.put("nonceB64", request.nonceB64);
        envelope.put("ciphertextB64", request.ciphertextB64);
        envelope.put("tagB64", request.tagB64);
        envelope.put("createdAtUnixMs", request.createdAtUnixMs);
        envelope.put("expiresAtUnixMs", request.expiresAtUnixMs);
        return envelope.toString().getBytes(StandardCharsets.UTF_8);
    }

    static E2eCodec.Response absorbEncryptedResponse(byte[] payload) throws Exception {
        JSONObject value = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        return new E2eCodec.Response(value.getString("schema"), value.getString("requestId"),
                value.getString("serverPublicKeyB64"), value.getString("nonceB64"),
                value.getString("ciphertextB64"), value.getString("tagB64"), value.getLong("ciphertextBytes"));
    }
}
