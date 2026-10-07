package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.Set;

final class AndroidCapabilityProtocol {
    static final String REQUEST_SCHEMA="aio.android.capability.request.v1";
    static final String REPLY_SCHEMA="aio.android.capability.reply.v1";
    static final int MAX_REQUEST_BYTES=64*1024;
    static final int MAX_REPLY_BYTES=512*1024;
    private static final Set<String> TOP_KEYS=Set.of("schema","capability","action","privacyClass","args");

    static final class Request {
        final String action;
        final AioAndroidNode.Capability capability;
        final AioAndroidNode.Privacy privacy;
        final StrictProjectionJson.ObjectValue args;
        Request(AioAndroidNode.Capability capability,String action,AioAndroidNode.Privacy privacy,
                StrictProjectionJson.ObjectValue args){
            this.capability=capability;this.action=action;this.privacy=privacy;this.args=args;
        }
    }

    private AndroidCapabilityProtocol(){}

    static Request parse(byte[] payload)throws Exception{
        if(AioProjectionQuantumCodec.looksLike(payload))
            return AioProjectionQuantumCodec.decode(payload);
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(payload,MAX_REQUEST_BYTES);
        if(!root.keySet().equals(TOP_KEYS))throw new IllegalArgumentException("ANDROID_CAPABILITY_KEYS");
        if(!REQUEST_SCHEMA.equals(string(root,"schema",96)))throw new IllegalArgumentException("ANDROID_CAPABILITY_SCHEMA");
        AioAndroidNode.Capability capability;
        try{capability=AioAndroidNode.Capability.valueOf(string(root,"capability",48));}
        catch(Exception failure){throw new IllegalArgumentException("ANDROID_CAPABILITY_UNKNOWN");}
        String action=string(root,"action",64);
        if(!supported(capability,action))throw new IllegalArgumentException("ANDROID_ACTION_UNSUPPORTED");
        AioAndroidNode.Privacy privacy;
        try{privacy=AioAndroidNode.Privacy.valueOf(string(root,"privacyClass",32));}
        catch(Exception failure){throw new IllegalArgumentException("ANDROID_PRIVACY_INVALID");}
        Object argsValue=root.get("args");
        if(!(argsValue instanceof StrictProjectionJson.ObjectValue))throw new IllegalArgumentException("ANDROID_ARGS_OBJECT_REQUIRED");
        return new Request(capability,action,privacy,(StrictProjectionJson.ObjectValue)argsValue);
    }

    static boolean supported(AioAndroidNode.Capability capability,String action){
        return AndroidCapabilityCatalog.supports(capability,action);
    }

    static byte[] reply(boolean accepted,String code,String resultJson){
        String safeCode=code==null?"UNKNOWN":code;
        if(!safeCode.matches("[A-Za-z0-9_.:-]{1,96}"))safeCode="INTERNAL_FAILURE";
        String result="null";
        if(resultJson!=null&&!resultJson.isBlank()){
            byte[] resultBytes=resultJson.getBytes(StandardCharsets.UTF_8);
            try{
                if(resultBytes.length>MAX_REPLY_BYTES-256)throw new IllegalArgumentException("ANDROID_REPLY_BUDGET");
                StrictProjectionJson.object(resultBytes,MAX_REPLY_BYTES,MAX_REPLY_BYTES-256);
                result=resultJson;
            }catch(IllegalArgumentException invalid){
                throw new IllegalArgumentException("ANDROID_RESULT_JSON_INVALID");
            }catch(Exception invalid){
                throw new IllegalArgumentException("ANDROID_RESULT_JSON_INVALID");
            }finally{java.util.Arrays.fill(resultBytes,(byte)0);}
        }
        String json="{\"schema\":\""+REPLY_SCHEMA+"\",\"accepted\":"+(accepted?"true":"false")+
            ",\"code\":\""+escape(safeCode)+"\",\"result\":"+result+"}";
        byte[] encoded=json.getBytes(StandardCharsets.UTF_8);
        if(encoded.length>MAX_REPLY_BYTES){
            java.util.Arrays.fill(encoded,(byte)0);
            throw new IllegalArgumentException("ANDROID_REPLY_BUDGET");
        }
        return encoded;
    }

    static String string(StrictProjectionJson.ObjectValue object,String key,int max){
        Object value=object.get(key);
        if(!(value instanceof String))throw new IllegalArgumentException("ANDROID_FIELD_TYPE_"+key);
        String text=(String)value;
        if(text.isEmpty()||text.length()>max)throw new IllegalArgumentException("ANDROID_FIELD_BOUNDS_"+key);
        return text;
    }

    static long integer(StrictProjectionJson.ObjectValue object,String key,long minimum,long maximum,long fallback){
        Object value=object.get(key);
        if(value==null)return fallback;
        if(!(value instanceof Long))throw new IllegalArgumentException("ANDROID_FIELD_TYPE_"+key);
        long number=(Long)value;
        if(number<minimum||number>maximum)throw new IllegalArgumentException("ANDROID_FIELD_BOUNDS_"+key);
        return number;
    }

    static String optionalString(StrictProjectionJson.ObjectValue object,String key,int max,String fallback){
        Object value=object.get(key);
        if(value==null)return fallback;
        if(!(value instanceof String))throw new IllegalArgumentException("ANDROID_FIELD_TYPE_"+key);
        String text=(String)value;
        if(text.length()>max)throw new IllegalArgumentException("ANDROID_FIELD_BOUNDS_"+key);
        return text;
    }

    static String escape(String value){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if(c<32)out.append(String.format("\\u%04x",(int)c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
