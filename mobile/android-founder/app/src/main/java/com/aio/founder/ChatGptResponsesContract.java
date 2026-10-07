package com.aio.founder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChatGptResponsesContract {
    static final String MODELS_ENDPOINT="https://api.openai.com/v1/models";
    static final String RESPONSES_ENDPOINT="https://api.openai.com/v1/responses";
    private static final int MAX_REQUEST_BYTES=512*1024;
    private static final int MAX_EVENT_BYTES=512*1024;
    private static final int MAX_INPUT_IMAGE_BYTES=AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES;
    private static final int MAX_OUTPUT_CHARS=1_600_000;
    private static final int MAX_REASONING_ITEM_CHARS=256*1024;
    private static final int MAX_FUNCTION_ITEM_CHARS=128*1024;
    private static final int MAX_FUNCTION_ARGUMENT_CHARS=32*1024;
    private static final int MAX_INPUT_ITEMS=128;
    private static final Set<String> MESSAGE_ROLES=Set.of("user","assistant","developer");

    private ChatGptResponsesContract(){}

    interface InputItem { String json(); }

    static final class FunctionTool {
        final String name,description,parametersJson;
        FunctionTool(String name,String description,String parametersJson){
            if(name==null||!name.matches("[A-Za-z0-9_-]{1,64}"))
                throw new IllegalArgumentException("CHATGPT_TOOL_NAME_INVALID");
            if(description==null||description.isBlank()||description.length()>4096)
                throw new IllegalArgumentException("CHATGPT_TOOL_DESCRIPTION_INVALID");
            if(parametersJson==null||parametersJson.isBlank()||parametersJson.length()>32*1024)
                throw new IllegalArgumentException("CHATGPT_TOOL_SCHEMA_INVALID");
            this.name=name;this.description=description;this.parametersJson=parametersJson;
        }
        String json(){
            return "{\"type\":\"function\",\"name\":\""+escape(name)+
                "\",\"description\":\""+escape(description)+
                "\",\"parameters\":"+parametersJson+",\"strict\":true}";
        }
    }

    static final class FunctionCall {
        final String callId,name,arguments,itemJson;
        FunctionCall(String callId,String name,String arguments,String itemJson){
            this.callId=callId;this.name=name;this.arguments=arguments;this.itemJson=itemJson;
        }
        InputItem asInput(){return new RawInput(itemJson);}
    }

    static final class Model {
        final String slug,displayName;
        Model(String slug,String displayName){this.slug=slug;this.displayName=displayName;}
    }

    static final class Completion {
        final String text;
        final List<String> reasoningItems;
        final List<FunctionCall> functionCalls;
        Completion(String text,List<String> reasoningItems,List<FunctionCall> functionCalls){
            this.text=text;this.reasoningItems=List.copyOf(reasoningItems);
            this.functionCalls=List.copyOf(functionCalls);
        }
    }

    private static final class RawInput implements InputItem {
        private final String json;
        RawInput(String json){this.json=json;}
        @Override public String json(){return json;}
    }

    static InputItem message(String role,String text){
        if(!MESSAGE_ROLES.contains(role))throw new IllegalArgumentException("CHATGPT_INPUT_ROLE_INVALID");
        String body=bounded(text,64*1024,"CHATGPT_INPUT_TEXT_INVALID");
        return new RawInput("{\"role\":\""+escape(role)+"\",\"content\":\""+escape(body)+"\"}");
    }

    static InputItem jpegImage(String base64){
        if(base64==null||base64.isBlank()||base64.length()>((MAX_INPUT_IMAGE_BYTES+2)/3)*4+8)
            throw new IllegalArgumentException("CHATGPT_INPUT_IMAGE_INVALID");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(base64);}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("CHATGPT_INPUT_IMAGE_INVALID");}
        try{
            if(bytes.length<4||bytes.length>MAX_INPUT_IMAGE_BYTES||
               (bytes[0]&0xff)!=0xff||(bytes[1]&0xff)!=0xd8||
               !Base64.getEncoder().encodeToString(bytes).equals(base64))
                throw new IllegalArgumentException("CHATGPT_INPUT_IMAGE_INVALID");
        }finally{java.util.Arrays.fill(bytes,(byte)0);}
        return new RawInput(
            "{\"role\":\"user\",\"content\":["+
            "{\"type\":\"input_text\",\"text\":\"Current Android screen captured by the Founder-granted AIO screen tool.\"},"+
            "{\"type\":\"input_image\",\"image_url\":\"data:image/jpeg;base64,"+escape(base64)+"\",\"detail\":\"auto\"}]}");
    }

    static boolean isInputImage(InputItem item){
        if(item==null||item.json()==null)return false;
        String json=item.json();
        return json.contains("\"type\":\"input_image\"")&&
            json.contains("data:image/jpeg;base64,");
    }

    static InputItem reasoning(String rawJson)throws Exception{
        if(rawJson==null||rawJson.isBlank()||rawJson.length()>MAX_REASONING_ITEM_CHARS)
            throw new IllegalArgumentException("CHATGPT_REASONING_ITEM_INVALID");
        byte[] bytes=rawJson.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,MAX_REASONING_ITEM_CHARS,MAX_REASONING_ITEM_CHARS);}
        catch(Exception invalid){throw new IllegalArgumentException("CHATGPT_REASONING_ITEM_INVALID");}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!"reasoning".equals(root.get("type"))||
            !(root.get("encrypted_content") instanceof String)||
            ((String)root.get("encrypted_content")).isBlank())
            throw new IllegalArgumentException("CHATGPT_REASONING_ITEM_INVALID");
        return new RawInput(canonical(root));
    }

    static InputItem functionOutput(String callId,String output){
        if(callId==null||!callId.matches("[A-Za-z0-9_.:-]{1,256}"))
            throw new IllegalArgumentException("CHATGPT_FUNCTION_CALL_ID_INVALID");
        if(output==null||output.isBlank()||output.length()>128*1024)
            throw new IllegalArgumentException("CHATGPT_FUNCTION_OUTPUT_INVALID");
        return new RawInput("{\"type\":\"function_call_output\",\"call_id\":\""+
            escape(callId)+"\",\"output\":\""+escape(output)+"\"}");
    }

    static byte[] request(String model,String instructions,List<InputItem> input){
        return request(model,instructions,input,List.of());
    }

    static byte[] request(String model,String instructions,List<InputItem> input,List<FunctionTool> tools){
        String selected=bounded(model,128,"CHATGPT_MODEL_INVALID");
        if(!selected.matches("[A-Za-z0-9._:-]{1,128}"))throw new IllegalArgumentException("CHATGPT_MODEL_INVALID");
        String guidance=bounded(instructions,32*1024,"CHATGPT_INSTRUCTIONS_INVALID");
        if(input==null||input.isEmpty()||input.size()>MAX_INPUT_ITEMS)
            throw new IllegalArgumentException("CHATGPT_INPUT_COUNT_INVALID");
        if(tools==null||tools.size()>16)throw new IllegalArgumentException("CHATGPT_TOOL_COUNT_INVALID");

        StringBuilder out=new StringBuilder();
        out.append("{\"model\":\"").append(escape(selected))
            .append("\",\"instructions\":\"").append(escape(guidance))
            .append("\",\"input\":[");
        for(int i=0;i<input.size();i++){
            InputItem item=input.get(i);
            if(item==null)throw new IllegalArgumentException("CHATGPT_INPUT_ITEM_INVALID");
            if(i>0)out.append(',');
            String json=item.json();
            if(json==null||json.isBlank())throw new IllegalArgumentException("CHATGPT_INPUT_ITEM_INVALID");
            out.append(json);
        }
        out.append(']');
        if(!tools.isEmpty()){
            out.append(",\"tools\":[");
            for(int i=0;i<tools.size();i++){
                if(tools.get(i)==null)throw new IllegalArgumentException("CHATGPT_TOOL_INVALID");
                if(i>0)out.append(',');
                out.append(tools.get(i).json());
            }
            out.append("],\"parallel_tool_calls\":false");
        }
        out.append(",\"store\":false,\"stream\":true}");
        byte[] encoded=out.toString().getBytes(StandardCharsets.UTF_8);
        if(encoded.length>MAX_REQUEST_BYTES){
            java.util.Arrays.fill(encoded,(byte)0);
            throw new IllegalArgumentException("CHATGPT_REQUEST_BUDGET");
        }
        return encoded;
    }

    static List<Model> parseModels(byte[] payload)throws Exception{
        StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(payload,512*1024,128*1024);
        Object value=root.get("models");
        if(!(value instanceof List<?>))throw new IllegalArgumentException("CHATGPT_MODEL_CATALOG_INVALID");
        ArrayList<Model> out=new ArrayList<>();
        for(Object rowValue:(List<?>)value){
            if(!(rowValue instanceof StrictProjectionJson.ObjectValue))continue;
            StrictProjectionJson.ObjectValue row=(StrictProjectionJson.ObjectValue)rowValue;
            if(!"list".equals(row.get("visibility")))continue;
            if(!(row.get("slug") instanceof String)||!(row.get("display_name") instanceof String))
                throw new IllegalArgumentException("CHATGPT_MODEL_CATALOG_INVALID");
            String slug=(String)row.get("slug"),display=(String)row.get("display_name");
            if(!slug.matches("[A-Za-z0-9._:-]{1,128}")||display.isBlank()||display.length()>256)
                throw new IllegalArgumentException("CHATGPT_MODEL_CATALOG_INVALID");
            out.add(new Model(slug,display));
            if(out.size()>128)throw new IllegalArgumentException("CHATGPT_MODEL_CATALOG_BUDGET");
        }
        if(out.isEmpty())throw new IllegalArgumentException("CHATGPT_MODEL_CATALOG_EMPTY");
        return List.copyOf(out);
    }

    static final class Stream {
        private final StringBuilder text=new StringBuilder();
        private final ArrayList<String> reasoningItems=new ArrayList<>();
        private final ArrayList<FunctionCall> functionCalls=new ArrayList<>();
        private boolean completed,failed,incomplete,terminal;
        private String failureCode="unknown_error";

        String accept(byte[] eventJson)throws Exception{
            if(terminal)throw new IllegalStateException("CHATGPT_STREAM_TERMINAL_DUPLICATE");
            StrictProjectionJson.ObjectValue root=StrictProjectionJson.object(
                eventJson,MAX_EVENT_BYTES,MAX_EVENT_BYTES);
            Object typeValue=root.get("type");
            if(!(typeValue instanceof String))throw new IllegalArgumentException("CHATGPT_STREAM_EVENT_TYPE");
            String type=(String)typeValue;
            switch(type){
                case "response.output_text.delta":
                    Object delta=root.get("delta");
                    if(!(delta instanceof String))throw new IllegalArgumentException("CHATGPT_STREAM_DELTA_INVALID");
                    if(text.length()+((String)delta).length()>MAX_OUTPUT_CHARS)
                        throw new IllegalArgumentException("CHATGPT_STREAM_OUTPUT_BUDGET");
                    text.append((String)delta);
                    return (String)delta;
                case "response.output_item.done":
                    captureItem(root.get("item"));
                    break;
                case "response.completed":
                    completed=true;terminal=true;
                    break;
                case "response.failed":
                    failed=true;terminal=true;failureCode=extractFailureCode(root);
                    break;
                case "response.incomplete":
                    incomplete=true;terminal=true;
                    break;
                default:
                    break;
            }
            return null;
        }

        Completion finish(){
            if(failed)throw new IllegalStateException("CHATGPT_RESPONSE_FAILED:"+failureCode);
            if(incomplete)throw new IllegalStateException("CHATGPT_RESPONSE_INCOMPLETE");
            if(!completed)throw new IllegalStateException("CHATGPT_STREAM_INCOMPLETE");
            return new Completion(text.toString(),reasoningItems,functionCalls);
        }

        private void captureItem(Object value){
            if(!(value instanceof StrictProjectionJson.ObjectValue))return;
            StrictProjectionJson.ObjectValue item=(StrictProjectionJson.ObjectValue)value;
            Object type=item.get("type");
            if("reasoning".equals(type)){
                Object encrypted=item.get("encrypted_content");
                if(!(encrypted instanceof String)||((String)encrypted).isBlank())return;
                if(reasoningItems.size()>=64)throw new IllegalArgumentException("CHATGPT_REASONING_ITEM_COUNT");
                String encoded=canonical(item);
                if(encoded.length()>MAX_REASONING_ITEM_CHARS)
                    throw new IllegalArgumentException("CHATGPT_REASONING_ITEM_BUDGET");
                reasoningItems.add(encoded);
                return;
            }
            if("function_call".equals(type)){
                Object callId=item.get("call_id"),name=item.get("name"),arguments=item.get("arguments");
                if(!(callId instanceof String)||!(name instanceof String)||!(arguments instanceof String)||
                    !((String)callId).matches("[A-Za-z0-9_.:-]{1,256}")||
                    !((String)name).matches("[A-Za-z0-9_-]{1,64}")||
                    ((String)arguments).length()>MAX_FUNCTION_ARGUMENT_CHARS)
                    throw new IllegalArgumentException("CHATGPT_FUNCTION_CALL_INVALID");
                if(functionCalls.size()>=4)throw new IllegalArgumentException("CHATGPT_FUNCTION_CALL_COUNT");
                String encoded=canonical(item);
                if(encoded.length()>MAX_FUNCTION_ITEM_CHARS)
                    throw new IllegalArgumentException("CHATGPT_FUNCTION_CALL_INVALID");
                functionCalls.add(new FunctionCall((String)callId,(String)name,(String)arguments,encoded));
            }
        }

        private static String extractFailureCode(StrictProjectionJson.ObjectValue root){
            Object response=root.get("response");
            if(response instanceof StrictProjectionJson.ObjectValue){
                Object error=((StrictProjectionJson.ObjectValue)response).get("error");
                if(error instanceof StrictProjectionJson.ObjectValue){
                    Object code=((StrictProjectionJson.ObjectValue)error).get("code");
                    if(code instanceof String&&((String)code).matches("[A-Za-z0-9_.:-]{1,96}"))
                        return (String)code;
                }
            }
            return "unknown_error";
        }
    }

    private static String bounded(String value,int max,String code){
        if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    static String canonical(Object value){
        if(value==null)return "null";
        if(value instanceof String)return "\""+escape((String)value)+"\"";
        if(value instanceof Boolean||value instanceof Long||value instanceof BigDecimal)return value.toString();
        if(value instanceof Map<?,?>){
            StringBuilder out=new StringBuilder("{");boolean first=true;
            for(Map.Entry<?,?> row:((Map<?,?>)value).entrySet()){
                if(!(row.getKey() instanceof String))throw new IllegalArgumentException("CHATGPT_JSON_KEY_INVALID");
                if(!first)out.append(',');first=false;
                out.append('\"').append(escape((String)row.getKey())).append("\":")
                   .append(canonical(row.getValue()));
            }
            return out.append('}').toString();
        }
        if(value instanceof List<?>){
            StringBuilder out=new StringBuilder("[");boolean first=true;
            for(Object row:(List<?>)value){
                if(!first)out.append(',');first=false;out.append(canonical(row));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("CHATGPT_JSON_VALUE_INVALID");
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
