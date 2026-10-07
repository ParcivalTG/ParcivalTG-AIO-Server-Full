package com.aio.founder;

import java.util.Base64;
import java.util.Set;

final class FounderPairingBundle {
    static final String SCHEMA="aio.founder.android.pairing.v1";
    static final int MAX_BYTES=64*1024;
    private static final long MAX_LIFETIME_MS=60*60_000L;
    private static final Set<String> ROOT_KEYS=Set.of(
        "schema","issuedAtUnixMs","expiresAtUnixMs","clientId","directEndpoint",
        "witnessSecretB64","pinnedGatewaySpkiB64Url","signedLeaseJson","cloud");
    private static final Set<String> CLOUD_KEYS=Set.of(
        "wssUrl","androidPeerId","windowsPeerId","peerAdmissionKeyB64","tunnelKeyB64");

    static final class Cloud {
        final String wssUrl,androidPeerId,windowsPeerId,peerAdmissionKeyB64,tunnelKeyB64;
        Cloud(String wssUrl,String androidPeerId,String windowsPeerId,String peerAdmissionKeyB64,String tunnelKeyB64){
            this.wssUrl=wssUrl;this.androidPeerId=androidPeerId;this.windowsPeerId=windowsPeerId;
            this.peerAdmissionKeyB64=peerAdmissionKeyB64;this.tunnelKeyB64=tunnelKeyB64;
        }
    }

    final long issuedAtUnixMs,expiresAtUnixMs;
    final String clientId,directEndpoint,witnessSecretB64,pinnedGatewaySpkiB64Url,signedLeaseJson;
    final Cloud cloud;

    private FounderPairingBundle(long issuedAtUnixMs,long expiresAtUnixMs,String clientId,String directEndpoint,
                                 String witnessSecretB64,String pinnedGatewaySpkiB64Url,String signedLeaseJson,Cloud cloud){
        this.issuedAtUnixMs=issuedAtUnixMs;this.expiresAtUnixMs=expiresAtUnixMs;this.clientId=clientId;
        this.directEndpoint=directEndpoint;this.witnessSecretB64=witnessSecretB64;
        this.pinnedGatewaySpkiB64Url=pinnedGatewaySpkiB64Url;this.signedLeaseJson=signedLeaseJson;this.cloud=cloud;
    }

    static FounderPairingBundle parse(String json,long now)throws Exception{
        if(json==null)throw new IllegalArgumentException("PAIRING_BUNDLE_REQUIRED");
        byte[] bytes=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,MAX_BYTES,48*1024);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!root.keySet().equals(ROOT_KEYS))throw new IllegalArgumentException("PAIRING_BUNDLE_KEYS");
        if(!SCHEMA.equals(string(root,"schema",96)))throw new IllegalArgumentException("PAIRING_BUNDLE_SCHEMA");

        long issued=integer(root,"issuedAtUnixMs");
        long expires=integer(root,"expiresAtUnixMs");
        if(issued<=0||expires<=issued||expires-issued>MAX_LIFETIME_MS||now>=expires||issued>now+5*60_000L)
            throw new SecurityException("PAIRING_BUNDLE_EXPIRED_OR_TIME_INVALID");

        String client=string(root,"clientId",128);
        if(!client.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("CLIENT_ID_INVALID");

        String direct=nullableString(root,"directEndpoint",320);
        String witness=string(root,"witnessSecretB64",512);
        byte[] witnessBytes=PresenceProtocol.decodeWitnessSecret(witness);
        try{
            if(witnessBytes.length<16||witnessBytes.length>128)throw new SecurityException("WITNESS_LENGTH_INVALID");
        }finally{java.util.Arrays.fill(witnessBytes,(byte)0);}

        String pin=string(root,"pinnedGatewaySpkiB64Url",4096);
        E2eCodec.pinnedKey(pin);

        String lease=string(root,"signedLeaseJson",48*1024);
        AioProjectionMembrane.validateLeaseImport(lease,now);

        Cloud cloud=null;
        Object cloudValue=root.get("cloud");
        if(cloudValue!=null){
            if(!(cloudValue instanceof StrictProjectionJson.ObjectValue))
                throw new IllegalArgumentException("PAIRING_CLOUD_OBJECT_REQUIRED");
            StrictProjectionJson.ObjectValue row=(StrictProjectionJson.ObjectValue)cloudValue;
            if(!row.keySet().equals(CLOUD_KEYS))throw new IllegalArgumentException("PAIRING_CLOUD_KEYS");
            String wss=string(row,"wssUrl",512);
            String androidPeer=string(row,"androidPeerId",128);
            String windowsPeer=string(row,"windowsPeerId",128);
            if(!wss.startsWith("wss://")||
                !androidPeer.matches("[A-Za-z0-9_.:-]{1,128}")||
                !windowsPeer.matches("[A-Za-z0-9_.:-]{1,128}"))
                throw new SecurityException("PAIRING_CLOUD_IDENTITY_INVALID");
            String admission=string(row,"peerAdmissionKeyB64",128);
            String tunnel=string(row,"tunnelKeyB64",128);
            validateKey(admission,"PAIRING_CLOUD_ADMISSION_INVALID");
            validateKey(tunnel,"PAIRING_CLOUD_TUNNEL_INVALID");
            cloud=new Cloud(wss,androidPeer,windowsPeer,admission,tunnel);
        }
        if((direct==null||direct.isEmpty())&&cloud==null)throw new SecurityException("PRESENCE_ROUTE_REQUIRED");
        return new FounderPairingBundle(issued,expires,client,direct==null?"":direct,witness,pin,lease,cloud);
    }

    private static void validateKey(String value,String code){
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(value);}
        catch(IllegalArgumentException invalid){throw new SecurityException(code);}
        try{if(bytes.length!=32)throw new SecurityException(code);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
    }

    private static String string(StrictProjectionJson.ObjectValue object,String key,int max){
        Object value=object.get(key);
        if(!(value instanceof String))throw new IllegalArgumentException("PAIRING_FIELD_TYPE_"+key);
        String text=((String)value).trim();
        if(text.isEmpty()||text.length()>max)throw new IllegalArgumentException("PAIRING_FIELD_BOUNDS_"+key);
        return text;
    }

    private static String nullableString(StrictProjectionJson.ObjectValue object,String key,int max){
        Object value=object.get(key);
        if(value==null)return null;
        if(!(value instanceof String))throw new IllegalArgumentException("PAIRING_FIELD_TYPE_"+key);
        String text=((String)value).trim();
        if(text.length()>max)throw new IllegalArgumentException("PAIRING_FIELD_BOUNDS_"+key);
        return text;
    }

    private static long integer(StrictProjectionJson.ObjectValue object,String key){
        Object value=object.get(key);
        if(!(value instanceof Long))throw new IllegalArgumentException("PAIRING_FIELD_TYPE_"+key);
        return (Long)value;
    }
}
