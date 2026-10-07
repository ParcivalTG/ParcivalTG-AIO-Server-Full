package com.aio.founder;

import org.junit.Test;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.Assert.*;

public class ChatGptIdentityVerifierTest {
    private static String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static byte[] unsigned(BigInteger value){
        byte[] raw=value.toByteArray();
        if(raw.length>1&&raw[0]==0)return java.util.Arrays.copyOfRange(raw,1,raw.length);
        return raw;
    }
    private static String jwt(KeyPair pair,String kid,String client,String nonce,long exp,String alg)throws Exception{
        String h=b64(("{\"alg\":\""+alg+"\",\"kid\":\""+kid+"\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String p=b64(("{\"iss\":\"https://auth.openai.com\",\"aud\":\""+client+
            "\",\"sub\":\"subject-123\",\"email\":\"user@example.com\",\"name\":\"Founder\""+
            ",\"iat\":1700000000,\"exp\":"+exp+",\"nonce\":\""+nonce+"\"}").getBytes(StandardCharsets.UTF_8));
        String signing=h+"."+p;
        Signature signature=Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());signature.update(signing.getBytes(StandardCharsets.US_ASCII));
        return signing+"."+b64(signature.sign());
    }
    private static byte[] jwks(KeyPair pair,String kid){
        RSAPublicKey key=(RSAPublicKey)pair.getPublic();
        String json="{\"keys\":[{\"kty\":\"RSA\",\"kid\":\""+kid+"\",\"use\":\"sig\",\"alg\":\"RS256\""+
            ",\"n\":\""+b64(unsigned(key.getModulus()))+"\",\"e\":\""+b64(unsigned(key.getPublicExponent()))+"\"}]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test public void verifiesSignatureAudienceIssuerExpiryAndNonce()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair();
        String token=jwt(pair,"kid-1","oaiapp_123","nonce-1",1700003600L,"RS256");
        ChatGptIdentityVerifier.Identity identity=ChatGptIdentityVerifier.verify(
            token,jwks(pair,"kid-1"),"oaiapp_123","nonce-1",1700000100L);
        assertEquals("subject-123",identity.subject);
        assertEquals("user@example.com",identity.email);
        assertEquals("Founder",identity.name);
    }

    @Test public void rejectsWrongNonceAudienceExpiryAndAlgorithm()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair();
        byte[] keys=jwks(pair,"kid-1");

        String token=jwt(pair,"kid-1","oaiapp_123","nonce-1",1700003600L,"RS256");
        try{ChatGptIdentityVerifier.verify(token,keys,"oaiapp_123","wrong",1700000100L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_ID_NONCE_INVALID",expected.getMessage());}
        try{ChatGptIdentityVerifier.verify(token,keys,"oaiapp_other","nonce-1",1700000100L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_ID_AUDIENCE_INVALID",expected.getMessage());}
        try{ChatGptIdentityVerifier.verify(token,keys,"oaiapp_123","nonce-1",1700003700L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_ID_EXPIRED",expected.getMessage());}

        String badAlg=jwt(pair,"kid-1","oaiapp_123","nonce-1",1700003600L,"HS256");
        try{ChatGptIdentityVerifier.verify(badAlg,keys,"oaiapp_123","nonce-1",1700000100L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_ID_ALGORITHM_INVALID",expected.getMessage());}
    }

    @Test public void rejectsUnknownKeyAndSignatureTamper()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair(),other=generator.generateKeyPair();
        String token=jwt(pair,"kid-1","oaiapp_123","nonce-1",1700003600L,"RS256");
        try{ChatGptIdentityVerifier.verify(token,jwks(other,"kid-2"),"oaiapp_123","nonce-1",1700000100L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_ID_KEY_NOT_FOUND",expected.getMessage());}

        String tampered=token.substring(0,token.length()-2)+"AA";
        try{ChatGptIdentityVerifier.verify(tampered,jwks(pair,"kid-1"),"oaiapp_123","nonce-1",1700000100L);fail();}
        catch(SecurityException expected){assertEquals("CHATGPT_ID_SIGNATURE_INVALID",expected.getMessage());}
    }
}
