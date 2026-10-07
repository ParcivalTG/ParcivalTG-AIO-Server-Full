package com.aio.founder;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ChatGptSelectiveToolManifestTest {
    @Test public void androidToolContainsOnlyLiveGrantedActions(){
        AioAndroidNode node=new AioAndroidNode();
        String peer=ChatGptAndroidToolContract.LOCAL_PEER_ID;
        node.grant(peer,AioAndroidNode.Capability.RESOURCE_STATUS,
            AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60_000);
        node.grant(peer,AioAndroidNode.Capability.SCREEN_OBSERVE,
            AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60_000);

        List<ChatGptResponsesContract.FunctionTool> tools=
            ChatGptSelectiveToolManifest.project(false,node.activeCapabilities(peer));
        assertEquals(1,tools.size());
        String json=tools.get(0).json();
        assertTrue(json.contains("aio_android_capability_invoke"));
        assertTrue(json.contains("resource.status"));
        assertTrue(json.contains("screen.capture"));
        assertFalse(json.contains("gesture.tap"));
        assertFalse(json.contains("file.read"));

        node.revokePeerCapability(peer,AioAndroidNode.Capability.SCREEN_OBSERVE);
        tools=ChatGptSelectiveToolManifest.project(false,node.activeCapabilities(peer));
        json=tools.get(0).json();
        assertTrue(json.contains("resource.status"));
        assertFalse(json.contains("screen.capture"));
    }

    @Test public void windowsToolManifestsOnlyWhenWindowsReady(){
        List<ChatGptResponsesContract.FunctionTool> held=
            ChatGptSelectiveToolManifest.project(false,Set.of());
        assertTrue(held.isEmpty());

        List<ChatGptResponsesContract.FunctionTool> ready=
            ChatGptSelectiveToolManifest.project(true,Set.of());
        assertEquals(1,ready.size());
        assertEquals(ChatGptAioToolContract.WINDOWS_OBJECTIVE_TOOL,ready.get(0).name);
    }

    @Test public void multipleCapabilitiesCollapseIntoOneAndroidTool(){
        Set<AioAndroidNode.Capability> caps=Set.of(
            AioAndroidNode.Capability.FILE_READ,
            AioAndroidNode.Capability.FILE_WRITE,
            AioAndroidNode.Capability.CLIPBOARD);
        List<ChatGptResponsesContract.FunctionTool> tools=
            ChatGptSelectiveToolManifest.project(true,caps);
        assertEquals(2,tools.size());
        String android=tools.get(1).json();
        assertTrue(android.contains("file.list"));
        assertTrue(android.contains("file.write"));
        assertTrue(android.contains("clipboard.read"));
        assertFalse(android.contains("notification.post"));
    }
}
