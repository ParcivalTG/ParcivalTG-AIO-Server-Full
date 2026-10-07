package com.aio.founder;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class ChatGptTokenContract {
    static final class Tokens {
        final String accessToken,refreshToken,idToken,tokenType;
        final long expiresInSeconds,savedAt,expiresAt,earliestRefreshAt;
        final Set<String> scopes;
        final boolean planUsageGranted;
        Tokens(String accessToken,String refreshToken,String idToken,String tokenType,
               long expiresInSeconds,long savedAt,long expiresAt,long earliestRefreshAt,
               Set<String> scopes){
            this.accessToken=accessToken;this.refreshToken=refreshToken;this.idToken=idToken;this.tokenType=tokenType;
            this.expiresInSeconds=expiresInSeconds;this.savedAt=savedAt;this.expiresAt=expiresAt;
            this.earliestRefreshAt=earliestRefreshAt;
            this.scopes=Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
            this.planUsageGranted=this.scopes.contains("chatgpt.tokens.use.direct");
        }
        boolean accessUsable(long nowEpochSeconds){
            return nowEpochSeconds+60<expiresAt;
        }
        boolean refreshAllowed(long nowEpochSeconds){
            return nowEpochSeconds>=earliestRefreshAt;
        }
    }

    private ChatGptTokenContract(){}

    static Tokens parse(byte[] payload,long savedAtEpochSeconds)throws Exception{
        if(savedAtEpochSeconds<=0)throw new IllegalArgumentException("CHATGPT_TOKEN_SAVE_TIME_INVALID");
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(payload,256*1024,128*1024);
        String access=required(root,"access_token",128*1024,"CHATGPT_ACCESS_TOKEN_REQUIRED");
        String refresh=required(root,"refresh_token",128*1024,"CHATGPT_REFRESH_TOKEN_REQUIRED");
        String id=required(root,"id_token",128*1024,"CHATGPT_ID_TOKEN_REQUIRED");
        String tokenType=required(root,"token_type",32,"CHATGPT_TOKEN_TYPE_INVALID");
        if(!"Bearer".equals(tokenType))throw new SecurityException("CHATGPT_TOKEN_TYPE_INVALID");

        long expires=integer(root.get("expires_in"),"CHATGPT_TOKEN_EXPIRY_INVALID");
        if(expires<60||expires>24*60*60)throw new SecurityException("CHATGPT_TOKEN_EXPIRY_INVALID");

        String scopeText=required(root,"scope",4096,"CHATGPT_SCOPE_INVALID");
        LinkedHashSet<String> scopes=new LinkedHashSet<>();
        for(String scope:scopeText.trim().split("\\s+")){
            if(!scope.matches("[A-Za-z0-9._:-]{1,128}"))throw new SecurityException("CHATGPT_SCOPE_INVALID");
            scopes.add(scope);
        }
        if(!scopes.contains("openid"))throw new SecurityException("CHATGPT_OPENID_SCOPE_REQUIRED");
        if(!scopes.contains("offline_access"))throw new SecurityException("CHATGPT_OFFLINE_SCOPE_REQUIRED");

        long earliest=savedAtEpochSeconds;
        if(root.containsKey("earliest_refresh_at")){
            earliest=integer(root.get("earliest_refresh_at"),"CHATGPT_EARLIEST_REFRESH_INVALID");
            if(earliest<savedAtEpochSeconds-300||earliest>savedAtEpochSeconds+expires)
                throw new SecurityException("CHATGPT_EARLIEST_REFRESH_INVALID");
        }
        long expiresAt=Math.addExact(savedAtEpochSeconds,expires);
        return new Tokens(access,refresh,id,tokenType,expires,savedAtEpochSeconds,expiresAt,earliest,scopes);
    }

    private static String required(StrictProjectionJson.ObjectValue root,String key,int max,String code){
        Object value=root.get(key);
        if(!(value instanceof String)||((String)value).isBlank()||((String)value).length()>max)
            throw new SecurityException(code);
        return (String)value;
    }

    private static long integer(Object value,String code){
        if(!(value instanceof Long))throw new SecurityException(code);
        return (Long)value;
    }
}
