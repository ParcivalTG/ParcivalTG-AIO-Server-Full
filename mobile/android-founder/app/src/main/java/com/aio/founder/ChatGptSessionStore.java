package com.aio.founder;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Android boundary for AIO-owned ChatGPT credential custody. Secret values are always Keystore wrapped. */
final class ChatGptSessionStore {
    private static final String PREFS="aio_chatgpt_provider_v1";
    private static final String HOST_KEY="host_id";
    private static final String SESSION_SECRET="chatgpt_session";
    private static final String REGISTRATION_SECRET="chatgpt_registration";

    static final class Registration {
        final String hostId,clientId,subject,email,name;
        Registration(String hostId,String clientId,String subject,String email,String name){
            this.hostId=hostId;this.clientId=clientId;this.subject=subject;
            this.email=email==null?"":email;this.name=name==null?"":name;
        }
    }

    private final SharedPreferences prefs;
    private final SecretStore secrets;

    ChatGptSessionStore(Context context){
        Context app=context.getApplicationContext();
        prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        secrets=new SecretStore(app);
    }

    synchronized String hostId(){
        String existing=prefs.getString(HOST_KEY,null);
        if(existing!=null)return existing;
        String created="urn:uuid:"+UUID.randomUUID();
        if(!prefs.edit().putString(HOST_KEY,created).commit())
            throw new IllegalStateException("CHATGPT_HOST_ID_COMMIT_FAILED");
        return created;
    }

    synchronized void save(ChatGptSessionCodec.Session session)throws Exception{
        if(session==null||!hostId().equals(session.hostId))
            throw new SecurityException("CHATGPT_SESSION_HOST_MISMATCH");
        String encoded=ChatGptSessionCodec.encode(session);
        try{
            // Session is one sealed value so rotating access+refresh token replacement is atomic.
            secrets.saveText(SESSION_SECRET,encoded);
            secrets.saveText(REGISTRATION_SECRET,registrationJson(new Registration(
                session.hostId,session.clientId,session.subject,session.email,session.name)));
        }finally{
            encoded=null;
        }
    }

    synchronized ChatGptSessionCodec.Session load()throws Exception{
        String encoded=secrets.readText(SESSION_SECRET);
        if(encoded==null)return null;
        ChatGptSessionCodec.Session session=ChatGptSessionCodec.decode(encoded);
        if(!hostId().equals(session.hostId))throw new SecurityException("CHATGPT_SESSION_HOST_MISMATCH");
        return session;
    }

    synchronized Registration registration()throws Exception{
        String encoded=secrets.readText(REGISTRATION_SECRET);
        if(encoded==null)return null;
        return registration(encoded);
    }

    synchronized boolean hasSession(){return secrets.hasText(SESSION_SECRET);}

    synchronized void clearSession(){
        secrets.removeText(SESSION_SECRET);
    }

    synchronized void clearAll(){
        secrets.removeText(SESSION_SECRET);
        secrets.removeText(REGISTRATION_SECRET);
    }

    private static String registrationJson(Registration value){
        return "{\"schema\":\"aio.chatgpt.registration.v1\""+
            ",\"host_id\":\""+escape(value.hostId)+"\""+
            ",\"client_id\":\""+escape(value.clientId)+"\""+
            ",\"subject\":\""+escape(value.subject)+"\""+
            ",\"email\":\""+escape(value.email)+"\""+
            ",\"name\":\""+escape(value.name)+"\"}";
    }

    private static Registration registration(String encoded)throws Exception{
        byte[] bytes=encoded.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,64*1024,32*1024);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!"aio.chatgpt.registration.v1".equals(root.get("schema")))
            throw new SecurityException("CHATGPT_REGISTRATION_SCHEMA_INVALID");
        String host=field(root,"host_id",512);
        String client=field(root,"client_id",256);
        String subject=field(root,"subject",512);
        String email=field(root,"email",512);
        String name=field(root,"name",512);
        // Reuse the session codec validators without retaining token material.
        if(!host.startsWith("urn:uuid:")&&!host.startsWith("urn:ietf:params:oauth:jwk-thumbprint:")&&!host.startsWith("did:key:"))
            throw new SecurityException("CHATGPT_REGISTRATION_HOST_INVALID");
        if(!client.matches("oaiapp_[A-Za-z0-9._:-]{1,240}"))
            throw new SecurityException("CHATGPT_REGISTRATION_CLIENT_INVALID");
        if(subject.isBlank())throw new SecurityException("CHATGPT_REGISTRATION_SUBJECT_INVALID");
        return new Registration(host,client,subject,email,name);
    }

    private static String field(StrictProjectionJson.ObjectValue root,String key,int max){
        Object value=root.get(key);
        if(!(value instanceof String)||((String)value).length()>max)
            throw new SecurityException("CHATGPT_REGISTRATION_INVALID");
        return (String)value;
    }

    private static String escape(String value){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '\"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
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
