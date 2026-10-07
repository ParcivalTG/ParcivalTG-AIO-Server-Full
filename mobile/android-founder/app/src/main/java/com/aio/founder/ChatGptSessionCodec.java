package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ChatGptSessionCodec {
    static final String SCHEMA="aio.chatgpt.session.v1";

    static final class Session {
        final String hostId,clientId,subject,email,name;
        final ChatGptTokenContract.Tokens tokens;
        Session(String hostId,String clientId,String subject,String email,String name,
                ChatGptTokenContract.Tokens tokens){
            validateHost(hostId);validateClient(clientId);
            this.subject=required(subject,512,"CHATGPT_SESSION_SUBJECT_INVALID");
            this.email=optional(email,512,"CHATGPT_SESSION_EMAIL_INVALID");
            this.name=optional(name,512,"CHATGPT_SESSION_NAME_INVALID");
            if(tokens==null)throw new IllegalArgumentException("CHATGPT_SESSION_TOKENS_REQUIRED");
            this.hostId=hostId;this.clientId=clientId;this.tokens=tokens;
        }
    }

    private ChatGptSessionCodec(){}

    static String encode(Session session){
        if(session==null)throw new IllegalArgumentException("CHATGPT_SESSION_REQUIRED");
        StringBuilder out=new StringBuilder();
        out.append("{\"schema\":\"").append(SCHEMA)
            .append("\",\"host_id\":\"").append(escape(session.hostId))
            .append("\",\"client_id\":\"").append(escape(session.clientId))
            .append("\",\"subject\":\"").append(escape(session.subject))
            .append("\",\"email\":\"").append(escape(session.email))
            .append("\",\"name\":\"").append(escape(session.name))
            .append("\",\"access_token\":\"").append(escape(session.tokens.accessToken))
            .append("\",\"refresh_token\":\"").append(escape(session.tokens.refreshToken))
            .append("\",\"id_token\":\"").append(escape(session.tokens.idToken))
            .append("\",\"token_type\":\"").append(escape(session.tokens.tokenType))
            .append("\",\"expires_in\":").append(session.tokens.expiresInSeconds)
            .append(",\"saved_at\":").append(session.tokens.savedAt)
            .append(",\"expires_at\":").append(session.tokens.expiresAt)
            .append(",\"earliest_refresh_at\":").append(session.tokens.earliestRefreshAt)
            .append(",\"scopes\":[");
        boolean first=true;
        for(String scope:session.tokens.scopes){
            if(!first)out.append(',');first=false;
            out.append('\"').append(escape(scope)).append('\"');
        }
        return out.append("]}").toString();
    }

    static Session decode(String encoded)throws Exception{
        if(encoded==null||encoded.isBlank()||encoded.length()>512*1024)
            throw new SecurityException("CHATGPT_SESSION_INVALID");
        byte[] bytes=encoded.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,512*1024,256*1024);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!SCHEMA.equals(root.get("schema")))throw new SecurityException("CHATGPT_SESSION_SCHEMA_INVALID");

        String host=string(root,"host_id",512,"CHATGPT_SESSION_HOST_INVALID");
        String client=string(root,"client_id",256,"CHATGPT_SESSION_CLIENT_INVALID");
        validateHost(host);validateClient(client);
        String subject=string(root,"subject",512,"CHATGPT_SESSION_SUBJECT_INVALID");
        String email=nullableString(root,"email",512,"CHATGPT_SESSION_EMAIL_INVALID");
        String name=nullableString(root,"name",512,"CHATGPT_SESSION_NAME_INVALID");
        String access=string(root,"access_token",128*1024,"CHATGPT_SESSION_ACCESS_INVALID");
        String refresh=string(root,"refresh_token",128*1024,"CHATGPT_SESSION_REFRESH_INVALID");
        String id=string(root,"id_token",128*1024,"CHATGPT_SESSION_ID_INVALID");
        String tokenType=string(root,"token_type",32,"CHATGPT_SESSION_TOKEN_TYPE_INVALID");
        if(!"Bearer".equals(tokenType))throw new SecurityException("CHATGPT_SESSION_TOKEN_TYPE_INVALID");
        long expiresIn=integer(root,"expires_in","CHATGPT_SESSION_TIME_INVALID");
        long savedAt=integer(root,"saved_at","CHATGPT_SESSION_TIME_INVALID");
        long expiresAt=integer(root,"expires_at","CHATGPT_SESSION_TIME_INVALID");
        long earliest=integer(root,"earliest_refresh_at","CHATGPT_SESSION_TIME_INVALID");
        if(expiresIn<60||expiresIn>24*60*60||savedAt<=0||
            Math.addExact(savedAt,expiresIn)!=expiresAt||
            earliest<savedAt-300||earliest>expiresAt)
            throw new SecurityException("CHATGPT_SESSION_TIME_INCONSISTENT");

        Object scopesValue=root.get("scopes");
        if(!(scopesValue instanceof List<?>))throw new SecurityException("CHATGPT_SESSION_SCOPES_INVALID");
        LinkedHashSet<String> scopes=new LinkedHashSet<>();
        for(Object value:(List<?>)scopesValue){
            if(!(value instanceof String)||!((String)value).matches("[A-Za-z0-9._:-]{1,128}"))
                throw new SecurityException("CHATGPT_SESSION_SCOPES_INVALID");
            scopes.add((String)value);
        }
        if(!scopes.contains("openid")||!scopes.contains("offline_access"))
            throw new SecurityException("CHATGPT_SESSION_SCOPES_INVALID");

        ChatGptTokenContract.Tokens tokens=new ChatGptTokenContract.Tokens(
            access,refresh,id,tokenType,expiresIn,savedAt,expiresAt,earliest,scopes);
        return new Session(host,client,subject,email,name,tokens);
    }

    private static long integer(StrictProjectionJson.ObjectValue root,String key,String code){
        Object value=root.get(key);
        if(!(value instanceof Long))throw new SecurityException(code);
        return (Long)value;
    }

    private static String string(StrictProjectionJson.ObjectValue root,String key,int max,String code){
        Object value=root.get(key);
        if(!(value instanceof String)||((String)value).isBlank()||((String)value).length()>max)
            throw new SecurityException(code);
        return (String)value;
    }

    private static String nullableString(StrictProjectionJson.ObjectValue root,String key,int max,String code){
        Object value=root.get(key);
        if(!(value instanceof String)||((String)value).length()>max)throw new SecurityException(code);
        return (String)value;
    }

    private static void validateHost(String host){
        if(host==null||host.length()>512)throw new SecurityException("CHATGPT_SESSION_HOST_INVALID");
        if(host.startsWith("urn:uuid:")){
            try{java.util.UUID.fromString(host.substring("urn:uuid:".length()));return;}
            catch(Exception ignored){throw new SecurityException("CHATGPT_SESSION_HOST_INVALID");}
        }
        if(host.matches("urn:ietf:params:oauth:jwk-thumbprint:[A-Za-z0-9_-]{16,256}"))return;
        if(host.matches("did:key:[A-Za-z0-9._:-]{16,384}"))return;
        throw new SecurityException("CHATGPT_SESSION_HOST_INVALID");
    }

    private static void validateClient(String client){
        if(client==null||!client.matches("oaiapp_[A-Za-z0-9._:-]{1,240}"))
            throw new SecurityException("CHATGPT_SESSION_CLIENT_INVALID");
    }

    private static String required(String value,int max,String code){
        if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    private static String optional(String value,int max,String code){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    private static String escape(String value){
        StringBuilder out=new StringBuilder(value.length()+16);
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '\"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\b':out.append("\\b");break;
                case '\f':out.append("\\f");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default:
                    if(c<0x20)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
