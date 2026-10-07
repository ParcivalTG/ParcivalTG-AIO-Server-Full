package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidLocalNetworkPolicyTest {
    @Test public void definiteLanRangesAreRecognized(){
        for(String host:new String[]{
            "localhost","printer.local","10.0.0.1","10.255.255.254",
            "172.16.0.1","172.31.255.254","192.168.1.1","169.254.2.3","127.0.0.1",
            "::1","fe80::1","fd12:3456::1","fc00::1"})
            assertTrue(host,AndroidLocalNetworkPolicy.likelyLocalHost(host));
    }

    @Test public void publicAndReservedRangesAreNotAssumedLan(){
        for(String host:new String[]{
            "8.8.8.8","1.1.1.1","100.64.0.1","192.0.2.1","198.51.100.1",
            "203.0.113.1","240.1.2.3","example.com","2001:4860:4860::8888"})
            assertFalse(host,AndroidLocalNetworkPolicy.likelyLocalHost(host));
    }

    @Test public void malformedAddressesFailClosed(){
        for(String host:new String[]{"","300.1.1.1","10.0.0","10..0.1","10.0.0.-1","gggg::1"})
            assertFalse(host,AndroidLocalNetworkPolicy.likelyLocalHost(host));
    }

    @Test public void permissionPolicyMatchesAndroidGeneration(){
        assertFalse(AndroidLocalNetworkPolicy.needsRuntimePermission(35,"192.168.1.2"));
        assertTrue(AndroidLocalNetworkPolicy.needsRuntimePermission(36,"192.168.1.2"));
        assertFalse(AndroidLocalNetworkPolicy.needsRuntimePermission(36,"8.8.8.8"));
        assertEquals("android.permission.NEARBY_WIFI_DEVICES",AndroidLocalNetworkPolicy.permissionName(36));
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK",AndroidLocalNetworkPolicy.permissionName(37));
    }
}
