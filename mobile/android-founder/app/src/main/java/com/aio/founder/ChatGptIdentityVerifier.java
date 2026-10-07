package com.aio.founder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

final class ChatGptIdentityVerifier {
    static final class Identity {
        final String subject,email,name;
        Identity(String subject,String email,String name){
            this.subject=subject;this.email=email;this.name=name;
        }
    }

    private ChatGptIdentityVerifier(){}

    static Identity verify(String jwt,byte[] jwksJson,String expectedClientId,String expectedNonce,
                           long nowEpochSeconds)throws Exception{
        if(expectedNonce==null||expectedNonce.isBlank())
            throw new SecurityException("CHATGPT_ID_NONCE_INVALID");
        return verifyCore(jwt,jwksJson,expectedClientId,expectedNonce,null,nowEpochSeconds);
    }

    static Identity verifyRefresh(String jwt,byte[] jwksJson,String expectedClientId,
                                  String expectedSubject,long nowEpochSeconds)throws Exception{
        if(expectedSubject==null||expectedSubject.isBlank())
            throw new SecurityException("CHATGPT_ID_SUBJECT_INVALID");
        return verifyCore(jwt,jwksJson,expectedClientId,null,expectedSubject,nowEpochSeconds);
    }

    private static Identity verifyCore(String jwt,byte[] jwksJson,String expectedClientId,
                                       String expectedNonce,String expectedSubject,long nowEpochSeconds)throws Exception{
        if(jwt==null||jwt.length()>128*1024)throw new SecurityException("CHATGPT_ID_TOKEN_INVALID");
        if(expectedClientId==null||!expectedClientId.startsWith("oaiapp_"))
            throw new SecurityException("CHATGPT_ID_AUDIENCE_INVALID");

        String[] parts=jwt.split("\\.",-1);
        if(parts.length!=3)throw new SecurityException("CHATGPT_ID_TOKEN_INVALID");
        byte[] headerBytes=decode(parts[0],"CHATGPT_ID_TOKEN_INVALID");
        byte[] payloadBytes=decode(parts[1],"CHATGPT_ID_TOKEN_INVALID");
        byte[] signatureBytes=decode(parts[2],"CHATGPT_ID_SIGNATURE_INVALID");
        try{
            StrictProjectionJson.ObjectValue header=StrictProjectionJson.object(headerBytes,32*1024,16*1024);
            StrictProjectionJson.ObjectValue payload=StrictProjectionJson.object(payloadBytes,96*1024,64*1024);
            if(!"RS256".equals(header.get("alg")))throw new SecurityException("CHATGPT_ID_ALGORITHM_INVALID");
            Object kidValue=header.get("kid");
            if(!(kidValue instanceof String)||((String)kidValue).isBlank()||((String)kidValue).length()>256)
                throw new SecurityException("CHATGPT_ID_KEY_INVALID");
            java.security.PublicKey key=key(jwksJson,(String)kidValue);

            Signature verifier=Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            if(!verifier.verify(signatureBytes))throw new SecurityException("CHATGPT_ID_SIGNATURE_INVALID");

            if(!ChatGptAuthContract.ISSUER.equals(payload.get("iss")))
                throw new SecurityException("CHATGPT_ID_ISSUER_INVALID");
            if(!audience(payload.get("aud"),expectedClientId))
                throw new SecurityException("CHATGPT_ID_AUDIENCE_INVALID");

            if(expectedNonce!=null&&!expectedNonce.equals(payload.get("nonce")))
                throw new SecurityException("CHATGPT_ID_NONCE_INVALID");

            long exp=integer(payload.get("exp"),"CHATGPT_ID_EXP_INVALID");
            long iat=integer(payload.get("iat"),"CHATGPT_ID_IAT_INVALID");
            if(exp+5<nowEpochSeconds)throw new SecurityException("CHATGPT_ID_EXPIRED");
            if(iat>nowEpochSeconds+5)throw new SecurityException("CHATGPT_ID_IAT_INVALID");
            Object subValue=payload.get("sub");
            if(!(subValue instanceof String)||((String)subValue).isBlank()||((String)subValue).length()>512)
                throw new SecurityException("CHATGPT_ID_SUBJECT_INVALID");
            String subject=(String)subValue;
            if(expectedSubject!=null&&!expectedSubject.equals(subject))
                throw new SecurityException("CHATGPT_ACCOUNT_MISMATCH");

            String email=optional(payload.get("email"),512);
            String name=optional(payload.get("name"),512);
            return new Identity(subject,email,name);
        }finally{
            java.util.Arrays.fill(headerBytes,(byte)0);
            java.util.Arrays.fill(payloadBytes,(byte)0);
            java.util.Arrays.fill(signatureBytes,(byte)0);
        }
    }

    private static java.security.PublicKey key(byte[] jwksJson,String kid)throws Exception{
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(jwksJson,512*1024,256*1024);
        Object keysValue=root.get("keys");
        if(!(keysValue instanceof List<?>))throw new SecurityException("CHATGPT_ID_JWKS_INVALID");
        for(Object value:(List<?>)keysValue){
            if(!(value instanceof StrictProjectionJson.ObjectValue))continue;
            StrictProjectionJson.ObjectValue row=(StrictProjectionJson.ObjectValue)value;
            if(!kid.equals(row.get("kid")))continue;
            if(!"RSA".equals(row.get("kty")))throw new SecurityException("CHATGPT_ID_KEY_INVALID");
            if(row.containsKey("alg")&&!"RS256".equals(row.get("alg")))
                throw new SecurityException("CHATGPT_ID_KEY_INVALID");
            if(row.containsKey("use")&&!"sig".equals(row.get("use")))
                throw new SecurityException("CHATGPT_ID_KEY_INVALID");
            Object nValue=row.get("n"),eValue=row.get("e");
            if(!(nValue instanceof String)||!(eValue instanceof String))
                throw new SecurityException("CHATGPT_ID_KEY_INVALID");
            byte[] n=decode((String)nValue,"CHATGPT_ID_KEY_INVALID");
            byte[] e=decode((String)eValue,"CHATGPT_ID_KEY_INVALID");
            try{
                if(n.length<256||n.length>1024||e.length<1||e.length>8)
                    throw new SecurityException("CHATGPT_ID_KEY_INVALID");
                BigInteger modulus=new BigInteger(1,n);
                BigInteger exponent=new BigInteger(1,e);
                return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent));
            }finally{
                java.util.Arrays.fill(n,(byte)0);
                java.util.Arrays.fill(e,(byte)0);
            }
        }
        throw new SecurityException("CHATGPT_ID_KEY_NOT_FOUND");
    }

    private static boolean audience(Object value,String expected){
        if(value instanceof String)return expected.equals(value);
        if(value instanceof List<?>){
            for(Object row:(List<?>)value)if(expected.equals(row))return true;
        }
        return false;
    }

    private static long integer(Object value,String code){
        if(!(value instanceof Long))throw new SecurityException(code);
        return (Long)value;
    }

    private static String optional(Object value,int max){
        if(value==null)return "";
        if(!(value instanceof String)||((String)value).length()>max)
            throw new SecurityException("CHATGPT_ID_PROFILE_INVALID");
        return (String)value;
    }

    private static byte[] decode(String value,String code){
        try{
            if(value==null||value.isBlank()||value.length()>256*1024)throw new IllegalArgumentException();
            return Base64.getUrlDecoder().decode(value);
        }catch(IllegalArgumentException invalid){
            throw new SecurityException(code);
        }
    }
}
