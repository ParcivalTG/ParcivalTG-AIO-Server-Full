package com.aio.founder;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class ChatGptAuthContract {
    static final String AUTHORIZATION_ENDPOINT="https://auth.openai.com/api/accounts/authorize";
    static final String TOKEN_ENDPOINT="https://auth.openai.com/api/accounts/oauth/token";
    static final String JWKS_URI="https://auth.openai.com/.well-known/jwks.json";
    static final String ISSUER="https://auth.openai.com";
    static final String RESOURCE="https://api.openai.com/v1";
    static final String DYNAMIC_CLIENT_ID="dynamic_agent_client";
    static final String AGENT_NAME="AIO for Android";
    static final String SCOPES="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";

    private ChatGptAuthContract(){}

    static final class Callback {
        final String code,clientId,scope;
        Callback(String code,String clientId,String scope){
            this.code=code;this.clientId=clientId;this.scope=scope;
        }
    }

    static final class Attempt {
        final String hostId,clientId,redirectUri,state,nonce,codeVerifier,codeChallenge,idTokenHint,loginHint;
        final boolean registration,forceConsent;

        private Attempt(String hostId,String clientId,int port,boolean registration,
                        String idTokenHint,String loginHint,boolean forceConsent){
            validateHost(hostId);validatePort(port);
            validateClient(clientId,registration);
            this.hostId=hostId;this.clientId=clientId;this.registration=registration;this.forceConsent=forceConsent;
            this.redirectUri="http://127.0.0.1:"+port+"/auth/callback";
            this.state=random(32);this.nonce=random(32);this.codeVerifier=random(64);
            this.codeChallenge=pkceChallenge(codeVerifier);
            this.idTokenHint=boundedOptional(idTokenHint,8192,"CHATGPT_ID_TOKEN_HINT_INVALID");
            this.loginHint=boundedOptional(loginHint,320,"CHATGPT_LOGIN_HINT_INVALID");
        }

        String authorizationUrl(){
            LinkedHashMap<String,String> q=new LinkedHashMap<>();
            q.put("client_id",clientId);
            if(registration)q.put("agent_name_hint",AGENT_NAME);
            q.put("ext_agent_host_id",hostId);
            if(!registration&&idTokenHint!=null)q.put("id_token_hint",idTokenHint);
            if(!registration&&loginHint!=null)q.put("login_hint",loginHint);
            if(!registration&&forceConsent)q.put("prompt","consent");
            q.put("response_type","code");
            q.put("redirect_uri",redirectUri);
            q.put("scope",SCOPES);
            q.put("resource",RESOURCE);
            q.put("state",state);
            q.put("nonce",nonce);
            q.put("code_challenge_method","S256");
            q.put("code_challenge",codeChallenge);
            return AUTHORIZATION_ENDPOINT+"?"+formEncode(q);
        }

        Callback validateCallback(String callbackUrl)throws Exception{
            URI expected=new URI(redirectUri),actual=new URI(callbackUrl);
            if(!"http".equals(actual.getScheme())||!"127.0.0.1".equals(actual.getHost())||
                actual.getPort()!=expected.getPort()||!"/auth/callback".equals(actual.getPath()))
                throw new SecurityException("CHATGPT_OAUTH_CALLBACK_ORIGIN_INVALID");
            Map<String,String> q=parseQuery(actual.getRawQuery());
            if(!state.equals(q.get("state")))throw new SecurityException("CHATGPT_OAUTH_STATE_MISMATCH");
            String error=q.get("error");
            if(error!=null&&!error.isBlank())throw new SecurityException("CHATGPT_OAUTH_"+safeCode(error));
            String code=q.get("code");
            if(code==null||code.isBlank()||code.length()>4096)throw new SecurityException("CHATGPT_OAUTH_CODE_MISSING");
            String returned=q.get("client_id");
            String selected=clientId;
            if(registration){
                if(returned==null||returned.isBlank()||DYNAMIC_CLIENT_ID.equals(returned))
                    throw new SecurityException("CHATGPT_OAUTH_REGISTRATION_INCOMPLETE");
                validateClient(returned,false);selected=returned;
            }else if(returned!=null&&!returned.equals(clientId)){
                throw new SecurityException("CHATGPT_OAUTH_CLIENT_MISMATCH");
            }
            return new Callback(code,selected,q.get("scope"));
        }
    }

    static Attempt newRegistrationAttempt(String hostId,int port){
        return new Attempt(hostId,DYNAMIC_CLIENT_ID,port,true,null,null,false);
    }

    static Attempt newReturningAttempt(String hostId,int port,String issuedClientId,
                                       String idTokenHint,String loginHint){
        return newReturningAttempt(hostId,port,issuedClientId,idTokenHint,loginHint,false);
    }

    static Attempt newReturningAttempt(String hostId,int port,String issuedClientId,
                                       String idTokenHint,String loginHint,boolean forceConsent){
        return new Attempt(hostId,issuedClientId,port,false,idTokenHint,loginHint,forceConsent);
    }

    static Map<String,String> authorizationCodeForm(String issuedClientId,String code,
                                                    String verifier,String redirectUri){
        validateClient(issuedClientId,false);
        bounded(code,4096,"CHATGPT_OAUTH_CODE_INVALID");
        bounded(verifier,256,"CHATGPT_PKCE_VERIFIER_INVALID");
        validateRedirect(redirectUri);
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        out.put("grant_type","authorization_code");
        out.put("client_id",issuedClientId);
        out.put("code",code);
        out.put("code_verifier",verifier);
        out.put("redirect_uri",redirectUri);
        out.put("resource",RESOURCE);
        return out;
    }

    static Map<String,String> refreshForm(String issuedClientId,String refreshToken){
        validateClient(issuedClientId,false);
        bounded(refreshToken,16*1024,"CHATGPT_REFRESH_TOKEN_INVALID");
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        out.put("grant_type","refresh_token");
        out.put("client_id",issuedClientId);
        out.put("refresh_token",refreshToken);
        out.put("resource",RESOURCE);
        return out;
    }

    static String formEncode(Map<String,String> values){
        StringBuilder out=new StringBuilder();
        for(Map.Entry<String,String> row:values.entrySet()){
            if(out.length()>0)out.append('&');
            out.append(URLEncoder.encode(row.getKey(),StandardCharsets.UTF_8))
               .append('=')
               .append(URLEncoder.encode(row.getValue(),StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    static String pkceChallenge(String verifier){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            try{return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);}
            finally{java.util.Arrays.fill(digest,(byte)0);}
        }catch(Exception impossible){throw new AssertionError(impossible);}
    }

    private static String random(int bytes){
        byte[] value=new byte[bytes];
        new SecureRandom().nextBytes(value);
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
        finally{java.util.Arrays.fill(value,(byte)0);}
    }

    private static void validateHost(String hostId){
        if(hostId==null||hostId.length()>512||
            !(hostId.startsWith("urn:uuid:")||
              hostId.startsWith("urn:ietf:params:oauth:jwk-thumbprint:")||
              hostId.startsWith("did:key:")))
            throw new IllegalArgumentException("CHATGPT_HOST_ID_INVALID");
    }

    private static void validatePort(int port){
        if(port<1024||port>65535)throw new IllegalArgumentException("CHATGPT_LOOPBACK_PORT_INVALID");
    }

    private static void validateClient(String clientId,boolean registration){
        if(registration){
            if(!DYNAMIC_CLIENT_ID.equals(clientId))throw new IllegalArgumentException("CHATGPT_CLIENT_ID_INVALID");
            return;
        }
        if(clientId==null||!clientId.startsWith("oaiapp_")||clientId.length()>256||
            !clientId.matches("[A-Za-z0-9._:-]+"))
            throw new IllegalArgumentException("CHATGPT_CLIENT_ID_INVALID");
    }

    private static void validateRedirect(String value){
        try{
            URI uri=new URI(value);
            if(!"http".equals(uri.getScheme())||!"127.0.0.1".equals(uri.getHost())||
                uri.getPort()<1024||uri.getPort()>65535||!"/auth/callback".equals(uri.getPath())||
                uri.getQuery()!=null||uri.getFragment()!=null)
                throw new IllegalArgumentException("CHATGPT_REDIRECT_INVALID");
        }catch(java.net.URISyntaxException invalid){
            throw new IllegalArgumentException("CHATGPT_REDIRECT_INVALID");
        }
    }

    private static String bounded(String value,int max,String code){
        if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    private static String boundedOptional(String value,int max,String code){
        if(value==null||value.isBlank())return null;
        if(value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    private static Map<String,String> parseQuery(String raw){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        if(raw==null||raw.isEmpty())return out;
        for(String pair:raw.split("&")){
            String[] kv=pair.split("=",2);
            String key=URLDecoder.decode(kv[0],StandardCharsets.UTF_8);
            String value=URLDecoder.decode(kv.length==2?kv[1]:"",StandardCharsets.UTF_8);
            if(out.put(key,value)!=null)throw new IllegalArgumentException("CHATGPT_OAUTH_DUPLICATE_PARAMETER");
        }
        return out;
    }

    private static String safeCode(String value){
        String normalized=value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]","_");
        return normalized.length()<=64?normalized:normalized.substring(0,64);
    }
}
