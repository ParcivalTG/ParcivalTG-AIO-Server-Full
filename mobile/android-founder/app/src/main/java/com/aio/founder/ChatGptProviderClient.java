package com.aio.founder;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ChatGptProviderClient {
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    private static final int MAX_JSON_RESPONSE=512*1024;

    interface DeltaListener { void onDelta(String delta); }

    static final class Endpoints {
        final String discovery,token,jwks,models,responses,discoveryHost;
        final boolean discoveryHttps;
        Endpoints(String token,String jwks,String models,String responses){
            this(ChatGptDiscoveryContract.DISCOVERY_ENDPOINT,token,jwks,models,responses);
        }
        Endpoints(String discovery,String token,String jwks,String models,String responses){
            this.discovery=needUrl(discovery);this.token=needUrl(token);this.jwks=needUrl(jwks);
            this.models=needUrl(models);this.responses=needUrl(responses);
            try{
                java.net.URI uri=new java.net.URI(this.discovery);
                this.discoveryHost=uri.getHost();this.discoveryHttps="https".equals(uri.getScheme());
            }catch(Exception impossible){throw new IllegalArgumentException("CHATGPT_ENDPOINT_INVALID");}
        }
        static Endpoints production(){
            return new Endpoints(
                ChatGptDiscoveryContract.DISCOVERY_ENDPOINT,
                ChatGptAuthContract.TOKEN_ENDPOINT,
                ChatGptAuthContract.JWKS_URI,
                ChatGptResponsesContract.MODELS_ENDPOINT,
                ChatGptResponsesContract.RESPONSES_ENDPOINT);
        }
        private static String needUrl(String value){
            if(value==null||value.isBlank()||value.length()>1024)
                throw new IllegalArgumentException("CHATGPT_ENDPOINT_INVALID");
            try{
                java.net.URI uri=new java.net.URI(value);
                if(uri.getScheme()==null||uri.getHost()==null)
                    throw new IllegalArgumentException("CHATGPT_ENDPOINT_INVALID");
            }catch(java.net.URISyntaxException invalid){
                throw new IllegalArgumentException("CHATGPT_ENDPOINT_INVALID");
            }
            return value;
        }
    }

    private final OkHttpClient http;
    private final Endpoints endpoints;

    ChatGptProviderClient(){this(Endpoints.production());}

    ChatGptProviderClient(Endpoints endpoints){
        this.endpoints=endpoints;
        this.http=new OkHttpClient.Builder()
            .connectTimeout(15,TimeUnit.SECONDS)
            .writeTimeout(30,TimeUnit.SECONDS)
            .readTimeout(5,TimeUnit.MINUTES)
            .callTimeout(6,TimeUnit.MINUTES)
            .retryOnConnectionFailure(false)
            .build();
    }

    ChatGptSessionCodec.Session exchange(ChatGptAuthContract.Attempt attempt,
                                         ChatGptAuthContract.Callback callback,
                                         String expectedSubject,long nowEpochSeconds)throws Exception{
        if(attempt==null||callback==null)throw new IllegalArgumentException("CHATGPT_OAUTH_ATTEMPT_REQUIRED");
        Map<String,String> form=ChatGptAuthContract.authorizationCodeForm(
            callback.clientId,callback.code,attempt.codeVerifier,attempt.redirectUri);
        byte[] tokenPayload=postForm(endpoints.token,form);
        byte[] jwksPayload=null;
        try{
            ChatGptTokenContract.Tokens tokens=ChatGptTokenContract.parse(tokenPayload,nowEpochSeconds);
            jwksPayload=get(endpoints.jwks,null,MAX_JSON_RESPONSE);
            ChatGptIdentityVerifier.Identity identity=ChatGptIdentityVerifier.verify(
                tokens.idToken,jwksPayload,callback.clientId,attempt.nonce,nowEpochSeconds);
            if(expectedSubject!=null&&!expectedSubject.equals(identity.subject))
                throw new SecurityException("CHATGPT_ACCOUNT_MISMATCH");
            return new ChatGptSessionCodec.Session(
                attempt.hostId,callback.clientId,identity.subject,identity.email,identity.name,tokens);
        }finally{
            java.util.Arrays.fill(tokenPayload,(byte)0);
            if(jwksPayload!=null)java.util.Arrays.fill(jwksPayload,(byte)0);
        }
    }

    ChatGptSessionCodec.Session refresh(ChatGptSessionCodec.Session prior,long nowEpochSeconds)throws Exception{
        if(prior==null)throw new IllegalArgumentException("CHATGPT_SESSION_REQUIRED");
        if(!prior.tokens.refreshAllowed(nowEpochSeconds))
            throw new IllegalStateException("CHATGPT_REFRESH_TOO_EARLY");
        byte[] tokenPayload=postForm(endpoints.token,
            ChatGptAuthContract.refreshForm(prior.clientId,prior.tokens.refreshToken));
        byte[] jwksPayload=null;
        try{
            ChatGptTokenContract.Tokens tokens=ChatGptTokenContract.parse(tokenPayload,nowEpochSeconds);
            jwksPayload=get(endpoints.jwks,null,MAX_JSON_RESPONSE);
            ChatGptIdentityVerifier.Identity identity=ChatGptIdentityVerifier.verifyRefresh(
                tokens.idToken,jwksPayload,prior.clientId,prior.subject,nowEpochSeconds);
            String email=identity.email.isEmpty()?prior.email:identity.email;
            String name=identity.name.isEmpty()?prior.name:identity.name;
            return new ChatGptSessionCodec.Session(
                prior.hostId,prior.clientId,prior.subject,email,name,tokens);
        }finally{
            java.util.Arrays.fill(tokenPayload,(byte)0);
            if(jwksPayload!=null)java.util.Arrays.fill(jwksPayload,(byte)0);
        }
    }


    boolean revoke(ChatGptSessionCodec.Session session)throws Exception{
        if(session==null)throw new IllegalArgumentException("CHATGPT_SESSION_REQUIRED");
        byte[] discoveryPayload=get(endpoints.discovery,null,MAX_JSON_RESPONSE);
        try{
            ChatGptDiscoveryContract.Discovery discovery=ChatGptDiscoveryContract.parse(
                discoveryPayload,endpoints.discoveryHost,endpoints.discoveryHttps);
            FormBody form=new FormBody.Builder(StandardCharsets.UTF_8)
                .add("token",session.tokens.refreshToken)
                .add("token_type_hint","refresh_token")
                .add("client_id",session.clientId)
                .build();
            Request request=new Request.Builder().url(discovery.revocationEndpoint)
                .header("Accept","application/json")
                .post(form).build();
            try(Response response=http.newCall(request).execute()){
                requireSuccess(response);
                return true;
            }
        }finally{
            java.util.Arrays.fill(discoveryPayload,(byte)0);
        }
    }

    List<ChatGptResponsesContract.Model> models(String accessToken)throws Exception{
        byte[] payload=get(endpoints.models,bearer(accessToken),MAX_JSON_RESPONSE);
        try{return ChatGptResponsesContract.parseModels(payload);}
        finally{java.util.Arrays.fill(payload,(byte)0);}
    }

    ChatGptResponsesContract.Completion respond(String accessToken,String model,String instructions,
                                                List<ChatGptResponsesContract.InputItem> input,
                                                DeltaListener listener)throws Exception{
        return respond(accessToken,model,instructions,input,List.of(),listener);
    }

    ChatGptResponsesContract.Completion respond(String accessToken,String model,String instructions,
                                                List<ChatGptResponsesContract.InputItem> input,
                                                List<ChatGptResponsesContract.FunctionTool> tools,
                                                DeltaListener listener)throws Exception{
        if(listener==null)throw new IllegalArgumentException("CHATGPT_DELTA_LISTENER_REQUIRED");
        String bearer=bearer(accessToken);
        byte[] payload=ChatGptResponsesContract.request(model,instructions,input,tools);
        RequestBody body=RequestBody.create(payload,JSON);
        Request request=new Request.Builder().url(endpoints.responses)
            .header("Authorization","Bearer "+bearer)
            .header("Accept","text/event-stream")
            .post(body).build();
        try(Response response=http.newCall(request).execute()){
            requireSuccess(response);
            ResponseBody responseBody=response.body();
            if(responseBody==null)throw new IOException("CHATGPT_EMPTY_RESPONSE");
            ChatGptResponsesContract.Stream stream=new ChatGptResponsesContract.Stream();
            consumeSse(responseBody.source(),stream,listener);
            return stream.finish();
        }finally{
            java.util.Arrays.fill(payload,(byte)0);
        }
    }

    private byte[] postForm(String url,Map<String,String> values)throws IOException{
        FormBody.Builder form=new FormBody.Builder(StandardCharsets.UTF_8);
        for(Map.Entry<String,String> value:values.entrySet())form.add(value.getKey(),value.getValue());
        Request request=new Request.Builder().url(url)
            .header("Accept","application/json")
            .post(form.build()).build();
        try(Response response=http.newCall(request).execute()){
            requireSuccess(response);
            return readBounded(response.body(),MAX_JSON_RESPONSE);
        }
    }

    private byte[] get(String url,String bearer,int maximum)throws IOException{
        Request.Builder builder=new Request.Builder().url(url).header("Accept","application/json").get();
        if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
        try(Response response=http.newCall(builder.build()).execute()){
            requireSuccess(response);
            return readBounded(response.body(),maximum);
        }
    }

    private static void consumeSse(BufferedSource source,ChatGptResponsesContract.Stream stream,
                                   DeltaListener listener)throws Exception{
        StringBuilder data=new StringBuilder();
        String line;
        while((line=source.readUtf8Line())!=null){
            if(line.isEmpty()){
                flushEvent(data,stream,listener);
                continue;
            }
            if(line.startsWith(":"))continue;
            if(line.startsWith("data:")){
                String value=line.substring(5);
                if(value.startsWith(" "))value=value.substring(1);
                if(data.length()>0)data.append('\n');
                data.append(value);
                if(data.length()>MAX_JSON_RESPONSE)throw new IOException("CHATGPT_SSE_EVENT_BUDGET");
            }
        }
        flushEvent(data,stream,listener);
    }

    private static void flushEvent(StringBuilder data,ChatGptResponsesContract.Stream stream,
                                   DeltaListener listener)throws Exception{
        if(data.length()==0)return;
        String value=data.toString();data.setLength(0);
        if("[DONE]".equals(value))return;
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        try{
            String delta=stream.accept(bytes);
            if(delta!=null&&!delta.isEmpty())listener.onDelta(delta);
        }finally{java.util.Arrays.fill(bytes,(byte)0);}
    }

    private static void requireSuccess(Response response)throws IOException{
        if(response.isSuccessful())return;
        String providerCode=null;
        ResponseBody body=response.body();
        if(body!=null){
            byte[] payload=null;
            try{
                payload=readBounded(body,64*1024);
                StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(payload,64*1024,32*1024);
                Object error=root.get("error");
                if(error instanceof StrictProjectionJson.ObjectValue){
                    Object code=((StrictProjectionJson.ObjectValue)error).get("code");
                    if(code instanceof String&&((String)code).matches("[A-Za-z0-9_.:-]{1,128}"))
                        providerCode=(String)code;
                }
                if(providerCode==null){
                    Object detail=root.get("detail");
                    if(detail instanceof String){
                        String normalized=((String)detail).trim()
                            .replaceAll("[^A-Za-z0-9_.:-]+","_");
                        if(normalized.matches("[A-Za-z0-9_.:-]{1,128}"))providerCode=normalized;
                    }
                }
            }catch(Exception ignored){
                // Status remains authoritative when a provider body is absent or changes shape.
            }finally{
                if(payload!=null)java.util.Arrays.fill(payload,(byte)0);
            }
        }
        throw new IOException(providerCode==null?"CHATGPT_HTTP_"+response.code():providerCode);
    }

    private static byte[] readBounded(ResponseBody body,int maximum)throws IOException{
        if(body==null)throw new IOException("CHATGPT_EMPTY_RESPONSE");
        try(InputStream in=body.byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16*1024];int read;
            while((read=in.read(buffer))!=-1){
                if(out.size()+read>maximum)throw new IOException("CHATGPT_RESPONSE_BUDGET");
                out.write(buffer,0,read);
            }
            return out.toByteArray();
        }
    }

    private static String bearer(String token){
        if(token==null||token.isBlank()||token.length()>128*1024)
            throw new IllegalArgumentException("CHATGPT_ACCESS_TOKEN_INVALID");
        for(int i=0;i<token.length();i++){
            char c=token.charAt(i);
            if(c<=0x20||c==0x7f)throw new IllegalArgumentException("CHATGPT_ACCESS_TOKEN_INVALID");
        }
        return token;
    }
}
