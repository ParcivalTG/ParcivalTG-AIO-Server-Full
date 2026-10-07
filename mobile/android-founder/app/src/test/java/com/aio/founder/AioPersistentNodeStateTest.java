package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioPersistentNodeStateTest {
    @Test public void remoteCapabilityNeedsAttachedAndVerifiedLink(){
        AioPersistentNodeState state=new AioPersistentNodeState();
        assertFalse(state.remoteCapabilityEligible());
        state.founderStart();
        assertFalse(state.remoteCapabilityEligible());
        state.linkAttached();
        assertFalse(state.remoteCapabilityEligible());
        state.peerVerified();
        assertTrue(state.remoteCapabilityEligible());
    }

    @Test public void replacingTransportSessionRevokesPeerVerification(){
        AioPersistentNodeState state=new AioPersistentNodeState();
        state.founderStart();state.linkAttached();state.peerVerified();
        assertTrue(state.remoteCapabilityEligible());
        state.linkAttached();
        assertFalse(state.remoteCapabilityEligible());
        assertEquals(AioPersistentNodeState.Phase.LINK_ATTACHED_UNVERIFIED,state.snapshot().phase);
    }

    @Test public void linkLossRevokesEligibility(){
        AioPersistentNodeState state=new AioPersistentNodeState();
        state.founderStart();state.linkAttached();state.peerVerified();
        state.linkLost();
        assertFalse(state.remoteCapabilityEligible());
        assertEquals(AioPersistentNodeState.Phase.ACTIVE_NO_LINK,state.snapshot().phase);
    }

    @Test public void stickyRestartNeverPreservesPeerVerification(){
        AioPersistentNodeState state=new AioPersistentNodeState();
        state.founderStart();state.linkAttached();state.peerVerified();
        state.stickyRestart(true);
        assertEquals(AioPersistentNodeState.Phase.ACTIVE_NO_LINK,state.snapshot().phase);
        assertFalse(state.remoteCapabilityEligible());
    }

    @Test public void stoppedNodeRejectsLinkAdmission(){
        AioPersistentNodeState state=new AioPersistentNodeState();
        try{state.linkAttached();fail();}
        catch(IllegalStateException expected){assertEquals("LINK_ATTACH_WHILE_STOPPED",expected.getMessage());}
    }

    @Test public void holdCodeIsBounded(){
        AioPersistentNodeState state=new AioPersistentNodeState();state.founderStart();
        try{state.hold("bad code");fail();}
        catch(IllegalArgumentException expected){assertEquals("NODE_HOLD_CODE_INVALID",expected.getMessage());}
    }
}
