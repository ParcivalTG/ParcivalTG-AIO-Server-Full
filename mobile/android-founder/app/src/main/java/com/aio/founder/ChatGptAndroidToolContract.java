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
            "Invoke one bounded Android AIO capability on this device. "+
            "Use only when the Founder asks to inspect or act on the Android device. "+
            "The action is mediated by endpoint-local Founder grants, Android platform permissions, "+
            "fresh screen/Accessibility/SAF prerequisites, causal admission, and a typed receipt. "+
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

        String request="{\"schema\":\""+AndroidCapabilityProtocol.REQUEST_SCHEMA+"\""+
            ",\"capability\":\""+capability.name()+"\""+
            ",\"action\":\""+AndroidCapabilityProtocol.escape(action)+"\""+
            ",\"privacyClass\":\"FOUNDER_ONLY\""+
            ",\"args\":"+ChatGptResponsesContract.canonical(argsValue)+"}";
        byte[] payload=request.getBytes(StandardCharsets.UTF_8);
        if(payload.length>AndroidCapabilityProtocol.MAX_REQUEST_BYTES){
            java.util.Arrays.fill(payload,(byte)0);
            throw new IllegalArgumentException("CHATGPT_ANDROID_TOOL_REQUEST_BUDGET");
        }
        // Parse once before execution so tool-to-capability mapping and argument
        // representation are proven by the same protocol parser used at dispatch.
        AndroidCapabilityProtocol.Request parsed=AndroidCapabilityProtocol.parse(payload);
        if(parsed.capability!=capability||!parsed.action.equals(action)){
            java.util.Arrays.fill(payload,(byte)0);
            throw new SecurityException("CHATGPT_ANDROID_TOOL_MAPPING_INVALID");
        }
        return new Invocation(action,capability,payload);
    }

    static Map<String,AioAndroidNode.Capability> actions(){
        return java.util.Collections.unmodifiableMap(ACTIONS);
    }
}
