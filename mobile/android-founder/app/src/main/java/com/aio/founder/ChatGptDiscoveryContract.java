package com.aio.founder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

final class ChatGptDiscoveryContract {
    static final String DISCOVERY_ENDPOINT="https://auth.openai.com/.well-known/openid-configuration";

    static final class Discovery {
        final String issuer,authorizationEndpoint,tokenEndpoint,jwksUri,revocationEndpoint;
        Discovery(String issuer,String authorizationEndpoint,String tokenEndpoint,
                  String jwksUri,String revocationEndpoint){
            this.issuer=issuer;this.authorizationEndpoint=authorizationEndpoint;
            this.tokenEndpoint=tokenEndpoint;this.jwksUri=jwksUri;this.revocationEndpoint=revocationEndpoint;
        }
    }

    private ChatGptDiscoveryContract(){}

    static Discovery parse(byte[] payload)throws Exception{
        return parse(payload,"auth.openai.com",true);
    }

    static Discovery parse(byte[] payload,String endpointHost,boolean requireHttps)throws Exception{
        if(endpointHost==null||endpointHost.isBlank())throw new IllegalArgumentException("CHATGPT_DISCOVERY_HOST_REQUIRED");
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(payload,256*1024,128*1024);
        String issuer=field(root,"issuer",512);
        if(!ChatGptAuthContract.ISSUER.equals(issuer))
            throw new SecurityException("CHATGPT_DISCOVERY_ISSUER_INVALID");
        String authorization=endpoint(field(root,"authorization_endpoint",1024),endpointHost,requireHttps);
        String token=endpoint(field(root,"token_endpoint",1024),endpointHost,requireHttps);
        String jwks=endpoint(field(root,"jwks_uri",1024),endpointHost,requireHttps);
        String revoke=endpoint(field(root,"revocation_endpoint",1024),endpointHost,requireHttps);
        return new Discovery(issuer,authorization,token,jwks,revoke);
    }

    private static String field(StrictProjectionJson.ObjectValue root,String key,int max){
        Object value=root.get(key);
        if(!(value instanceof String)||((String)value).isBlank()||((String)value).length()>max)
            throw new SecurityException("CHATGPT_DISCOVERY_FIELD_INVALID");
        return (String)value;
    }

    private static String endpoint(String value,String expectedHost,boolean requireHttps){
        try{
            URI uri=new URI(value);
            boolean scheme=requireHttps?"https".equals(uri.getScheme()):
                ("http".equals(uri.getScheme())||"https".equals(uri.getScheme()));
            if(!scheme||!expectedHost.equalsIgnoreCase(uri.getHost()))
                throw new SecurityException("CHATGPT_DISCOVERY_ENDPOINT_INVALID");
            return value;
        }catch(java.net.URISyntaxException invalid){
            throw new SecurityException("CHATGPT_DISCOVERY_ENDPOINT_INVALID");
        }
    }
}
