package com.aio.founder;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RelayAdmission {
    static String sign(byte[] peerKey,String peerId,long ts,String nonce)throws Exception{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(peerKey,"HmacSHA256"));
        byte[] raw=mac.doFinal(String.join("\n","WS","/v1/peer",peerId,Long.toString(ts),nonce).getBytes(StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder(raw.length*2);for(byte b:raw)s.append(String.format("%02x",b&255));return s.toString();
    }
    private RelayAdmission(){}
}
