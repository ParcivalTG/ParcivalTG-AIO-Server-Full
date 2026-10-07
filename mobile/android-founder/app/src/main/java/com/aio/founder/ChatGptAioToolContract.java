package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class ChatGptAioToolContract {
    static final String WINDOWS_OBJECTIVE_TOOL="aio_windows_objective_submit";
    private static final String PARAMETERS=
        "{\"type\":\"object\",\"properties\":{" +
        "\"instruction\":{\"type\":\"string\",\"description\":" +
        "\"The bounded Founder instruction AIO should submit to the Windows development ecosystem.\"}" +
        "},\"required\":[\"instruction\"],\"additionalProperties\":false}";

    private ChatGptAioToolContract(){}

    static ChatGptResponsesContract.FunctionTool windowsObjectiveTool(){
        return new ChatGptResponsesContract.FunctionTool(
            WINDOWS_OBJECTIVE_TOOL,
            "Submit one bounded development objective to the authenticated Windows AIO ecosystem. "+
            "Use this only when the Founder asks for work that requires the Windows PC/AIO environment. "+
            "This tool does not expose a shell; AIO preserves authority, leases, receipts, scheduling, and execution policy.",
            PARAMETERS);
    }

    static List<ChatGptResponsesContract.FunctionTool> p0Tools(){
        return List.of(windowsObjectiveTool());
    }

    static String parseInstruction(String arguments)throws Exception{
        if(arguments==null||arguments.isBlank()||arguments.length()>16*1024)
            throw new IllegalArgumentException("CHATGPT_TOOL_INSTRUCTION_INVALID");
        byte[] bytes=arguments.getBytes(StandardCharsets.UTF_8);
        StrictProjectionJson.ObjectValue root;
        try{root=StrictProjectionJson.object(bytes,16*1024,8*1024);}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
        if(!root.containsKey("instruction"))
            throw new IllegalArgumentException("CHATGPT_TOOL_INSTRUCTION_INVALID");
        Object value=root.get("instruction");
        if(!(value instanceof String)||((String)value).isBlank()||((String)value).length()>4096)
            throw new IllegalArgumentException("CHATGPT_TOOL_INSTRUCTION_INVALID");
        if(root.size()!=1)
            throw new SecurityException("CHATGPT_TOOL_ARGUMENTS_FORBIDDEN");
        String instruction=((String)value).trim();
        if(instruction.indexOf('\0')>=0)
            throw new IllegalArgumentException("CHATGPT_TOOL_INSTRUCTION_INVALID");
        return instruction;
    }

    static void requireKnown(String name){
        if(!WINDOWS_OBJECTIVE_TOOL.equals(name))
            throw new SecurityException("CHATGPT_TOOL_NOT_ALLOWED");
    }
}
