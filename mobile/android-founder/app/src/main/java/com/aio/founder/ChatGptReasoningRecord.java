package com.aio.founder;

import java.nio.charset.StandardCharsets;

final class ChatGptReasoningRecord {
    static final String SCHEMA="aio.chatgpt.reasoning-record.v1";
    final String intentId,itemJson;
    final long timestampUnixMs;

    private ChatGptReasoningRecord(String intentId,long timestampUnixMs,String itemJson){
        this.intentId=intentId;this.timestampUnixMs=timestampUnixMs;this.itemJson=itemJson;
    }

    static String encode(String intentId,long timestampUnixMs,String itemJson)throws Exception{
        String intent=bounded(intentId,512,"CHATGPT_REASONING_INTENT_INVALID");
        if(timestampUnixMs<=0)throw new IllegalArgumentException("CHATGPT_REASONING_TIME_INVALID");
        ChatGptResponsesContract.InputItem validated=ChatGptResponsesContract.reasoning(itemJson);
        return "{\"schema\":\""+SCHEMA+"\""+
            ",\"intentId\":\""+escape(intent)+"\""+
            ",\"timestampUnixMs\":"+timestampUnixMs+
            ",\"item\":"+validated.json()+"}";
    }

    static ChatGptReasoningRecord decode(String encoded)throws Exception{
        if(encoded==null||encoded.isBlank()||encoded.length()>512*1024)
            throw new SecurityException("CHATGPT_REASONING_RECORD_INVALID");
        byte[] bytes=encoded.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,512*1024,256*1024);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!SCHEMA.equals(root.get("schema")))throw new SecurityException("CHATGPT_REASONING_RECORD_SCHEMA");
        Object intentValue=root.get("intentId"),timeValue=root.get("timestampUnixMs"),itemValue=root.get("item");
        if(!(intentValue instanceof String)||((String)intentValue).isBlank()||((String)intentValue).length()>512)
            throw new SecurityException("CHATGPT_REASONING_RECORD_INTENT");
        if(!(timeValue instanceof Long)||(Long)timeValue<=0)
            throw new SecurityException("CHATGPT_REASONING_RECORD_TIME");
        String item=canonical(itemValue);
        ChatGptResponsesContract.reasoning(item);
        return new ChatGptReasoningRecord((String)intentValue,(Long)timeValue,item);
    }

    ChatGptResponsesContract.InputItem asInput()throws Exception{
        return ChatGptResponsesContract.reasoning(itemJson);
    }

    private static String bounded(String value,int max,String code){
        if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    private static String canonical(Object value){
        if(value==null)return "null";
        if(value instanceof String)return "\""+escape((String)value)+"\"";
        if(value instanceof Boolean||value instanceof Long||value instanceof java.math.BigDecimal)return value.toString();
        if(value instanceof java.util.Map<?,?>){
            StringBuilder out=new StringBuilder("{");boolean first=true;
            for(java.util.Map.Entry<?,?> row:((java.util.Map<?,?>)value).entrySet()){
                if(!(row.getKey() instanceof String))throw new SecurityException("CHATGPT_REASONING_RECORD_JSON");
                if(!first)out.append(',');first=false;
                out.append('\"').append(escape((String)row.getKey())).append("\":").append(canonical(row.getValue()));
            }
            return out.append('}').toString();
        }
        if(value instanceof java.util.List<?>){
            StringBuilder out=new StringBuilder("[");boolean first=true;
            for(Object row:(java.util.List<?>)value){
                if(!first)out.append(',');first=false;out.append(canonical(row));
            }
            return out.append(']').toString();
        }
        throw new SecurityException("CHATGPT_REASONING_RECORD_JSON");
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
