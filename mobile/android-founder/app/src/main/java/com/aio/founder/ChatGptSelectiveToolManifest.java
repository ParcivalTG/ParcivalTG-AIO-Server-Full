package com.aio.founder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Selective capability manifestation: GPT sees only actions that are causally executable now.
 * Authority expiry therefore contracts the model-visible action surface automatically.
 */
final class ChatGptSelectiveToolManifest {
    private ChatGptSelectiveToolManifest(){}

    static List<ChatGptResponsesContract.FunctionTool> project(
            boolean windowsReady,Set<AioAndroidNode.Capability> androidCapabilities){
        ArrayList<ChatGptResponsesContract.FunctionTool> out=new ArrayList<>();
        if(windowsReady)out.add(ChatGptAioToolContract.windowsObjectiveTool());

        LinkedHashSet<String> actions=new LinkedHashSet<>();
        if(androidCapabilities!=null){
            for(AioAndroidNode.Capability capability:AioAndroidNode.Capability.values()){
                if(!androidCapabilities.contains(capability))continue;
                AndroidCapabilityCatalog.Spec spec=AndroidCapabilityCatalog.spec(capability);
                if(!spec.remoteEnabled)continue;
                actions.addAll(spec.remoteActions);
            }
        }
        if(!actions.isEmpty())out.add(androidTool(actions));
        return List.copyOf(out);
    }

    private static ChatGptResponsesContract.FunctionTool androidTool(Set<String> actions){
        StringBuilder enumJson=new StringBuilder();
        boolean first=true;
        for(String action:actions){
            if(!first)enumJson.append(',');
            first=false;
            enumJson.append('"').append(AndroidCapabilityProtocol.escape(action)).append('"');
        }
        String parameters="{"+
            "\"type\":\"object\","+
            "\"properties\":{" +
              "\"action\":{\"type\":\"string\",\"enum\":["+enumJson+"]},"+
              "\"args_json\":{\"type\":\"string\",\"description\":"+
                "\"JSON object containing only bounded arguments for the selected action. Use {} when no arguments are needed.\"}"+
            "},"+
            "\"required\":[\"action\",\"args_json\"],\"additionalProperties\":false"+
            "}";
        String description=
            "Invoke one currently Founder-granted Android AIO action. Manifested now: "+
            String.join(", ",actions)+". "+
            "Screen observation supports whole-screen or foveated x1/y1/x2/y2 permille regions and previousSha256 unchanged suppression. "+
            "Successful gestures can carry a post-action hindsight image when SCREEN_OBSERVE is also manifested. "+
            "No non-manifested Android action, shell, permission bypass, raw filesystem path, or silent package install is available.";
        return new ChatGptResponsesContract.FunctionTool(
            ChatGptAndroidToolContract.TOOL,description,parameters);
    }
}
