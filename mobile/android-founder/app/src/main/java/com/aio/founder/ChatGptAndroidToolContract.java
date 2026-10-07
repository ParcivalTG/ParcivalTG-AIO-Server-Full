package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ChatGptAndroidToolContract {
    static final String TOOL="aio_android_capability_invoke";
    static final String LOCAL_PEER_ID="chatgpt-local-provider";

    private static final LinkedHashMap<String,AioAndroidNode.Capability> ACTIONS=new LinkedHashMap<>();
    static {
        ACTIONS.put("resource.status",AioAndroidNode.Capability.RESOURCE_STATUS);
        ACTIONS.put("resource.sha256",AioAndroidNode.Capability.RESOURCE_CONTRIBUTE);
        ACTIONS.put("resource.deflate",AioAndroidNode.Capability.RESOURCE_CONTRIBUTE);
        ACTIONS.put("screen.capture",AioAndroidNode.Capability.SCREEN_OBSERVE);
        ACTIONS.put("gesture.tap",AioAndroidNode.Capability.GESTURE_INPUT);
        ACTIONS.put("gesture.swipe",AioAndroidNode.Capability.GESTURE_INPUT);
        ACTIONS.put("clipboard.read",AioAndroidNode.Capability.CLIPBOARD);
        ACTIONS.put("clipboard.write",AioAndroidNode.Capability.CLIPBOARD);
        ACTIONS.put("notification.post",AioAndroidNode.Capability.NOTIFICATIONS);
        ACTIONS.put("file.list",AioAndroidNode.Capability.FILE_READ);
        ACTIONS.put("file.read",AioAndroidNode.Capability.FILE_READ);
        ACTIONS.put("file.sha256",AioAndroidNode.Capability.FILE_READ);
        ACTIONS.put("file.write",AioAndroidNode.Capability.FILE_WRITE);
        ACTIONS.put("file.rename",AioAndroidNode.Capability.FILE_WRITE);
        ACTIONS.put("file.delete",AioAndroidNode.Capability.FILE_WRITE);
    }

    static final class Invocation {
        final String action;
        final AioAndroidNode.Capability capability;
        final byte[] payload;
        Invocation(String action,AioAndroidNode.Capability capability,byte[] payload){
            this.action=action;this.capability=capability;this.payload=payload;
        }
    }

    private static final String PARAMETERS=
        "{"+
        "\"type\":\"object\","+
        "\"properties\":{"+
          "\"action\":{\"type\":\"string\",\"enum\":["+
            "\"resource.status\",\"resource.sha256\",\"resource.deflate\","+
            "\"screen.capture\",\"gesture.tap\",\"gesture.swipe\","+
            "\"clipboard.read\",\"clipboard.write\",\"notification.post\","+
            "\"file.list\",\"file.read\",\"file.sha256\",\"file.write\",\"file.rename\",\"file.delete\""+
          "]},"+
          "\"args_json\":{\"type\":\"string\",\"description\":"+
            "\"JSON object containing only the bounded arguments required by the selected Android action. Use {} when the action takes no arguments.\"}"+
        "},"+
        "\"required\":[\"action\",\"args_json\"],\"additionalProperties\":false"+
        "}";

    private ChatGptAndroidToolContract(){}

    static ChatGptResponsesContract.FunctionTool tool(){
        return new ChatGptResponsesContract.FunctionTool(
            TOOL,
            "Invoke one bounded Android AIO capability on this device. Use only when the Founder asks to inspect or act on Android. "+
            "args_json contracts: resource.status {}; resource.sha256 {contentB64}; resource.deflate {contentB64,level? 1..9}; "+
            "screen.capture {quality? 30..55,maxBytes?}; AIO clamps GPT images to 160000 bytes and attaches the JPEG as a real next-turn image; "+
            "gesture.tap {x,y,durationMs?} and gesture.swipe {x1,y1,x2,y2,durationMs?}, where coordinates are permille 0..1000 across the current screen and duration is 40..1500 ms; "+
            "clipboard.read {}; clipboard.write {text}; notification.post {title,text}; "+
            "file.list {path?}; file.read {path,maxBytes?}; file.sha256 {path}; file.write {parentPath?,name,mimeType?,contentB64}; file.rename {path,newName}; file.delete {path}. "+
            "Every action is mediated by endpoint-local Founder grants, Android platform permissions, fresh screen/Accessibility/SAF prerequisites, causal admission, and a typed receipt. "+
            "No shell, arbitrary code, silent permission enablement, raw filesystem access, or silent app install is exposed.",
            PARAMETERS);
    }

    static boolean isTool(String name){return TOOL.equals(name);}

    static Invocation parse(String arguments)throws Exception{
        if(arguments==null||arguments.isBlank()||arguments.length()>64*1024)
            throw new IllegalArgumentException("CHATGPT_ANDROID_TOOL_ARGUMENTS_INVALID");
        byte[] bytes=arguments.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,64*1024,48*1024);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!root.keySet().equals(Set.of("action","args_json")))
            throw new SecurityException("CHATGPT_ANDROID_TOOL_ARGUMENTS_FORBIDDEN");
        Object actionValue=root.get("action"),argsJsonValue=root.get("args_json");
        if(!(actionValue instanceof String)||!(argsJsonValue instanceof String))
            throw new IllegalArgumentException("CHATGPT_ANDROID_TOOL_ARGUMENTS_INVALID");
        String action=(String)actionValue;
        String argsJson=(String)argsJsonValue;
        if(argsJson.isBlank()||argsJson.length()>32*1024)
            throw new IllegalArgumentException("CHATGPT_ANDROID_TOOL_ARGUMENTS_INVALID");
        byte[] argsBytes=argsJson.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue argsValue;
        try{argsValue=StrictProjectionJson.object(argsBytes,32*1024,24*1024);}
        finally{java.util.Arrays.fill(argsBytes,(byte)0);}
        AioAndroidNode.Capability capability=ACTIONS.get(action);
        if(capability==null||!AndroidCapabilityCatalog.supports(capability,action))
            throw new SecurityException("CHATGPT_ANDROID_ACTION_NOT_ALLOWED");

        if("screen.capture".equals(action)){
            long requested=AndroidCapabilityProtocol.integer(
                argsValue,"maxBytes",
                AndroidRemoteScreenPolicy.MIN_JPEG_BYTES,
                AndroidRemoteScreenPolicy.MAX_JPEG_BYTES,
                AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES);
            argsValue.put("maxBytes",Long.valueOf(Math.min(
                requested,AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES)));
            long requestedQuality=AndroidCapabilityProtocol.integer(
                argsValue,"quality",30,75,AndroidRemoteScreenPolicy.DEFAULT_QUALITY);
            argsValue.put("quality",Long.valueOf(Math.min(requestedQuality,55)));
        }

        // Direct phase projection: once provider arguments are validated into AIO
        // semantics, do not materialize an intermediate JSON capability envelope.
        AndroidCapabilityProtocol.Request nativeRequest=
            new AndroidCapabilityProtocol.Request(
                capability,action,AioAndroidNode.Privacy.FOUNDER_ONLY,argsValue);
        byte[] payload=AioProjectionQuantumCodec.encode(nativeRequest);
        if(payload.length>AndroidCapabilityProtocol.MAX_REQUEST_BYTES){
            java.util.Arrays.fill(payload,(byte)0);
            throw new IllegalArgumentException("CHATGPT_ANDROID_TOOL_REQUEST_BUDGET");
        }
        // Re-absorb through the same boundary parser so the compact representation
        // proves exact semantic identity before any capability can execute.
        AndroidCapabilityProtocol.Request parsed=AndroidCapabilityProtocol.parse(payload);
        if(parsed.capability!=capability||!parsed.action.equals(action)||
           parsed.privacy!=AioAndroidNode.Privacy.FOUNDER_ONLY||
           !ChatGptResponsesContract.canonical(parsed.args).equals(
               ChatGptResponsesContract.canonical(argsValue))){
            java.util.Arrays.fill(payload,(byte)0);
            throw new SecurityException("CHATGPT_ANDROID_TOOL_MAPPING_INVALID");
        }
        return new Invocation(action,capability,payload);
    }

    static Map<String,AioAndroidNode.Capability> actions(){
        return java.util.Collections.unmodifiableMap(ACTIONS);
    }
}
