package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioAndroidNodeTest {
    @Test public void peerBindingAndRevocationFailClosed(){
        AioAndroidNode n=new AioAndroidNode();
        AioAndroidNode.Grant g=n.grant("windows-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
        assertEquals("ALLOW",n.authorize(g.id,"windows-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            "read",AioAndroidNode.Privacy.FOUNDER_ONLY).outcome);
        try{n.authorize(g.id,"other-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            "read",AioAndroidNode.Privacy.FOUNDER_ONLY);fail();}
        catch(SecurityException expected){assertEquals("peer-mismatch",expected.getMessage());}
        n.revoke(g.id);
        try{n.authorize(g.id,"windows-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            "read",AioAndroidNode.Privacy.FOUNDER_ONLY);fail();}
        catch(SecurityException expected){assertEquals("revoked",expected.getMessage());}
    }
    @Test public void packageStageCannotBeGrantedRemotely(){
        AioAndroidNode n=new AioAndroidNode();
        try{
            n.grant("windows-peer",AioAndroidNode.Capability.PACKAGE_STAGE,
                AioAndroidNode.Tier.UPDATE,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
            fail();
        }catch(SecurityException expected){
            assertEquals("ANDROID_CAPABILITY_LOCAL_ONLY",expected.getMessage());
        }
    }
    @Test public void fileWriteRequiresDataTier(){
        AioAndroidNode n=new AioAndroidNode();
        AioAndroidNode.Grant g=n.grant("windows-peer",AioAndroidNode.Capability.FILE_WRITE,
            AioAndroidNode.Tier.INTERACT,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
        try{n.authorize(g.id,"windows-peer",AioAndroidNode.Capability.FILE_WRITE,
            "write",AioAndroidNode.Privacy.FOUNDER_ONLY);fail();}
        catch(SecurityException expected){assertEquals("tier-insufficient",expected.getMessage());}
    }
    @Test public void privateGrantRejectsPrivacyDowngrade(){
        AioAndroidNode n=new AioAndroidNode();
        AioAndroidNode.Grant g=n.grant("windows-peer",AioAndroidNode.Capability.FILE_READ,
            AioAndroidNode.Tier.DATA,AioAndroidNode.Privacy.PRIVATE,60000);
        try{n.authorize(g.id,"windows-peer",AioAndroidNode.Capability.FILE_READ,
            "read",AioAndroidNode.Privacy.FOUNDER_ONLY);fail();}
        catch(SecurityException expected){assertEquals("privacy-downgrade",expected.getMessage());}
    }
    @Test public void peerBoundAuthorizationNeedsNoExportedGrantId(){
        AioAndroidNode n=new AioAndroidNode();
        n.grant("windows-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
        assertEquals("ALLOW",n.authorizePeer("windows-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            "resource.status",AioAndroidNode.Privacy.FOUNDER_ONLY).outcome);
        try{n.authorizePeer("other-peer",AioAndroidNode.Capability.RESOURCE_STATUS,
            "resource.status",AioAndroidNode.Privacy.FOUNDER_ONLY);fail();}
        catch(SecurityException expected){assertEquals("no-active-grant",expected.getMessage());}
    }
    @Test public void newerPeerCapabilityGrantReplacesOlderGrant(){
        AioAndroidNode n=new AioAndroidNode();
        AioAndroidNode.Grant first=n.grant("windows-peer",AioAndroidNode.Capability.FILE_READ,
            AioAndroidNode.Tier.DATA,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
        n.grant("windows-peer",AioAndroidNode.Capability.FILE_READ,
            AioAndroidNode.Tier.DATA,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
        try{n.authorize(first.id,"windows-peer",AioAndroidNode.Capability.FILE_READ,
            "file.read",AioAndroidNode.Privacy.FOUNDER_ONLY);fail();}
        catch(SecurityException expected){assertEquals("revoked",expected.getMessage());}
        assertEquals(1,n.activeGrantCount("windows-peer"));
    }
    @Test public void persistentGrantCapacityIsBounded(){
        AioAndroidNode n=new AioAndroidNode();
        for(int i=0;i<128;i++)n.grant("peer-"+i,AioAndroidNode.Capability.RESOURCE_STATUS,
            AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
        try{n.grant("overflow",AioAndroidNode.Capability.RESOURCE_STATUS,
            AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);fail();}
        catch(IllegalStateException expected){assertEquals("ANDROID_GRANT_CAPACITY",expected.getMessage());}
    }

    @Test public void receiptHistoryIsBounded(){
        AioAndroidNode n=new AioAndroidNode();
        for(int i=0;i<400;i++){
            AioAndroidNode.Grant g=n.grant("peer",AioAndroidNode.Capability.RESOURCE_STATUS,
                AioAndroidNode.Tier.OBSERVE,AioAndroidNode.Privacy.FOUNDER_ONLY,60000);
            n.revoke(g.id);
        }
        assertTrue(n.receipts().size()<=512);
    }

    @Test public void manifestIsBoundedAndTruthful(){
        AioAndroidNode.Manifest m=AioAndroidNode.manifest("phone");
        assertEquals("android",m.platform);assertEquals("r5",m.version);
        assertTrue(m.envelope.maxConcurrent>0);
        assertTrue(m.capabilities.contains(AioAndroidNode.Capability.RESOURCE_STATUS));
    }
}
