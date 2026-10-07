package com.aio.founder;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.*;
public class CloudTunnelCodecTest {
 @Test public void matchesDotNetVector() throws Exception {
   byte[] key=new byte[32],nonce=new byte[12];for(int i=0;i<key.length;i++)key[i]=(byte)i;for(int i=0;i<nonce.length;i++)nonce[i]=(byte)i;
   CloudTunnelCodec.Envelope e=CloudTunnelCodec.seal(key,"android-founder","windows-founder",7,4102444800000L,"AIOP-test-frame".getBytes(StandardCharsets.UTF_8),nonce);
   assertEquals("BkuZS+iRp2j5bPH50IQd",e.ciphertextB64);assertEquals("yWIxAZlI7c50A2g0elmlmg==",e.tagB64);
   assertEquals("AIOP-test-frame",new String(CloudTunnelCodec.open(key,e,"android-founder","windows-founder",6,1800000000000L),StandardCharsets.UTF_8));
 }
 @Test public void extraEnvelopeFieldRejected() throws Exception {
   String json="{\"Version\":1,\"From\":\"a\",\"To\":\"w\",\"Seq\":1,\"ExpiresAtUnixMs\":4102444800000,"+
     "\"NonceB64\":\"AAAAAAAAAAAAAAAA\",\"CiphertextB64\":\"\",\"TagB64\":\"AAAAAAAAAAAAAAAAAAAAAA==\",\"extra\":true}";
   try{CloudTunnelCodec.fromJsonString(json);fail();}
   catch(SecurityException expected){assertEquals("TUNNEL_JSON_KEYS",expected.getMessage());}
 }
 @Test public void invalidPeerRejectedBeforeEncryption() throws Exception {
   byte[] key=new byte[32],nonce=new byte[12];
   try{CloudTunnelCodec.seal(key,"bad peer","w",1,4102444800000L,new byte[]{1},nonce);fail();}
   catch(SecurityException expected){assertEquals("TUNNEL_BOUNDS",expected.getMessage());}
 }
 @Test public void oversizedPlaintextRejected() throws Exception {
   byte[] key=new byte[32],nonce=new byte[12],plain=new byte[CloudTunnelCodec.MAX_PLAINTEXT_BYTES+1];
   try{CloudTunnelCodec.seal(key,"a","w",1,4102444800000L,plain,nonce);fail();}
   catch(SecurityException expected){assertEquals("TUNNEL_BOUNDS",expected.getMessage());}
 }
 @Test public void oversizedCiphertextEncodingRejectedBeforeDecode() throws Exception {
   CloudTunnelCodec.Envelope e=new CloudTunnelCodec.Envelope(1,"a","w",1,4102444800000L,
     "AAAAAAAAAAAAAAAA","A".repeat(CloudTunnelCodec.MAX_CIPHERTEXT_B64+1),"AAAAAAAAAAAAAAAAAAAAAA==");
   try{CloudTunnelCodec.open(new byte[32],e,"a","w",0,1800000000000L);fail();}
   catch(SecurityException expected){assertEquals("TUNNEL_ENVELOPE_REJECTED",expected.getMessage());}
 }
 @Test(expected=SecurityException.class) public void replaySequenceRejected() throws Exception {
   byte[] key=new byte[32],nonce=new byte[12];CloudTunnelCodec.Envelope e=CloudTunnelCodec.seal(key,"a","w",7,4102444800000L,new byte[]{1},nonce);
   CloudTunnelCodec.open(key,e,"a","w",7,1800000000000L);
 }
}
