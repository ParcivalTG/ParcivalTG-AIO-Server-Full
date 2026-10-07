package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class CloudTunnelCodec {
    static final String DOMAIN="AIO_CLOUD_TUNNEL_V1";
    static final int MAX_PLAINTEXT_BYTES=PresenceProtocol.MAX_PAYLOAD+28;
    static final int MAX_CIPHERTEXT_B64=2_200_000;
    static final int MAX_ENVELOPE_JSON_CHARS=2_300_000;
    private static final Set<String> ENVELOPE_KEYS=Set.of("Version","From","To","Seq","ExpiresAtUnixMs","NonceB64","CiphertextB64","TagB64");
    static final class Envelope {
        final int version; final String from,to,nonceB64,ciphertextB64,tagB64; final long seq,expiresAtUnixMs;
        Envelope(int version,String from,String to,long seq,long expires,String nonce,String cipher,String tag){
            this.version=version;this.from=from;this.to=to;this.seq=seq;this.expiresAtUnixMs=expires;this.nonceB64=nonce;this.ciphertextB64=cipher;this.tagB64=tag;
        }
    }
    private static byte[] aad(String from,String to,long seq,long expires){
        return String.join("\n",DOMAIN,from,to,Long.toString(seq),Long.toString(expires)).getBytes(StandardCharsets.UTF_8);
    }
    static Envelope seal(byte[] key,String from,String to,long seq,long expires,byte[] plain,byte[] nonce)throws Exception{
        if(key==null||key.length!=32||nonce==null||nonce.length!=12||seq<1||expires<=0||
            plain==null||plain.length>MAX_PLAINTEXT_BYTES||!validPeer(from)||!validPeer(to))
            throw new SecurityException("TUNNEL_BOUNDS");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(aad(from,to,seq,expires));
        byte[] out=c.doFinal(plain),cipher=Arrays.copyOf(out,out.length-16),tag=Arrays.copyOfRange(out,out.length-16,out.length);
        try{return new Envelope(1,from,to,seq,expires,Base64.getEncoder().encodeToString(nonce),Base64.getEncoder().encodeToString(cipher),Base64.getEncoder().encodeToString(tag));}
        finally{Arrays.fill(out,(byte)0);Arrays.fill(cipher,(byte)0);Arrays.fill(tag,(byte)0);}
    }
    static byte[] open(byte[] key,Envelope e,String expectedFrom,String expectedTo,long lastSeq,long now)throws Exception{
        if(key==null||key.length!=32||e==null||e.version!=1||!validPeer(e.from)||!validPeer(e.to)||
            !e.from.equals(expectedFrom)||!e.to.equals(expectedTo)||e.seq<=lastSeq||e.expiresAtUnixMs<=now||
            e.nonceB64==null||e.nonceB64.length()>24||e.tagB64==null||e.tagB64.length()>32||
            e.ciphertextB64==null||e.ciphertextB64.length()>MAX_CIPHERTEXT_B64)
            throw new SecurityException("TUNNEL_ENVELOPE_REJECTED");
        byte[] nonce=Base64.getDecoder().decode(e.nonceB64),cipher=Base64.getDecoder().decode(e.ciphertextB64),tag=Base64.getDecoder().decode(e.tagB64),joined=new byte[cipher.length+tag.length];
        try{
            if(nonce.length!=12||tag.length!=16)throw new SecurityException("TUNNEL_ENVELOPE_ENCODING");
            System.arraycopy(cipher,0,joined,0,cipher.length);System.arraycopy(tag,0,joined,cipher.length,tag.length);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(aad(e.from,e.to,e.seq,e.expiresAtUnixMs));return c.doFinal(joined);
        }finally{Arrays.fill(nonce,(byte)0);Arrays.fill(cipher,(byte)0);Arrays.fill(tag,(byte)0);Arrays.fill(joined,(byte)0);}
    }
    static String toJsonString(Envelope e)throws Exception{
        if(e==null||!validPeer(e.from)||!validPeer(e.to))throw new SecurityException("TUNNEL_JSON_BOUNDS");
        return "{\"Version\":"+e.version+
            ",\"From\":\""+json(e.from)+"\""+
            ",\"To\":\""+json(e.to)+"\""+
            ",\"Seq\":"+e.seq+
            ",\"ExpiresAtUnixMs\":"+e.expiresAtUnixMs+
            ",\"NonceB64\":\""+json(e.nonceB64)+"\""+
            ",\"CiphertextB64\":\""+json(e.ciphertextB64)+"\""+
            ",\"TagB64\":\""+json(e.tagB64)+"\"}";
    }
    static Envelope fromJsonString(String text)throws Exception{
        if(text==null||text.isEmpty()||text.length()>MAX_ENVELOPE_JSON_CHARS)
            throw new SecurityException("TUNNEL_JSON_BOUNDS");
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        try{
            StrictProjectionJson.ObjectValue j=StrictProjectionJson.object(bytes,MAX_ENVELOPE_JSON_CHARS,MAX_CIPHERTEXT_B64);
            if(!j.keySet().equals(ENVELOPE_KEYS))throw new SecurityException("TUNNEL_JSON_KEYS");
            Object version=j.get("Version"),from=j.get("From"),to=j.get("To"),seq=j.get("Seq"),expires=j.get("ExpiresAtUnixMs");
            Object nonce=j.get("NonceB64"),cipher=j.get("CiphertextB64"),tag=j.get("TagB64");
            if(!(version instanceof Long)||!(from instanceof String)||!(to instanceof String)||!(seq instanceof Long)||
                !(expires instanceof Long)||!(nonce instanceof String)||!(cipher instanceof String)||!(tag instanceof String))
                throw new SecurityException("TUNNEL_JSON_TYPES");
            long versionLong=(Long)version;
            if(versionLong<Integer.MIN_VALUE||versionLong>Integer.MAX_VALUE)throw new SecurityException("TUNNEL_JSON_BOUNDS");
            Envelope e=new Envelope((int)versionLong,(String)from,(String)to,(Long)seq,(Long)expires,
                (String)nonce,(String)cipher,(String)tag);
            if(!validPeer(e.from)||!validPeer(e.to)||e.nonceB64.length()>24||e.tagB64.length()>32||e.ciphertextB64.length()>MAX_CIPHERTEXT_B64)
                throw new SecurityException("TUNNEL_JSON_BOUNDS");
            return e;
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    private static String json(String value){
        if(value==null)throw new IllegalArgumentException("TUNNEL_JSON_NULL");
        StringBuilder out=new StringBuilder(value.length()+8);
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch=='\\'||ch=='"')out.append('\\').append(ch);
            else if(ch=='\n')out.append("\\n");
            else if(ch=='\r')out.append("\\r");
            else if(ch=='\t')out.append("\\t");
            else if(ch<32)out.append(String.format("\\u%04x",(int)ch));
            else out.append(ch);
        }
        return out.toString();
    }
    private static boolean validPeer(String value){return value!=null&&value.matches("[A-Za-z0-9_.:-]{1,128}");}
    private CloudTunnelCodec(){}
}
