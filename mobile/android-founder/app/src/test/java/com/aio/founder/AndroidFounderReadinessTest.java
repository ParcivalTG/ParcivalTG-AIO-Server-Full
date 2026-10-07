package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidFounderReadinessTest {
    @Test public void releaseBuildWithoutPairingIsTruthfullyPairingRequired(){
        AndroidFounderReadiness.Report r=AndroidFounderReadiness.evaluate(new AndroidFounderReadiness.Snapshot(
            true,false,false,true,false,true,false,false,false,true,false,false,false));
        assertEquals("PAIRING_REQUIRED",r.overall);
        assertEquals("RELEASE_IDENTITY",r.installIdentity);
    }

    @Test public void authenticatedPresenceStillNeedsPinnedDialogueProof(){
        AndroidFounderReadiness.Report r=AndroidFounderReadiness.evaluate(new AndroidFounderReadiness.Snapshot(
            true,true,true,true,true,true,true,false,false,true,true,false,false));
        assertEquals("DIALOGUE_VERIFICATION_REQUIRED",r.overall);
        assertEquals("DIALOGUE_PROOF_REQUIRED",r.remoteAuthority);
    }

    @Test public void verifiedPeerStillRequiresChatGptForFounderFinishLine(){
        AndroidFounderReadiness.Report r=AndroidFounderReadiness.evaluate(new AndroidFounderReadiness.Snapshot(
            true,true,true,true,false,false,false,false,false,true,true,true,false));
        assertEquals("CHATGPT_AUTH_REQUIRED",r.overall);
        assertEquals("CHATGPT_AUTH_OR_PLAN_REQUIRED",r.chatProvider);
        assertEquals("1/6 local prerequisites active",r.optionalCapabilities);
    }

    @Test public void verifiedPeerAndChatGptQualifyFounderRemote(){
        AndroidFounderReadiness.Report r=AndroidFounderReadiness.evaluate(new AndroidFounderReadiness.Snapshot(
            true,true,true,true,true,true,false,false,false,true,true,true,true));
        assertEquals("FOUNDER_REMOTE_READY",r.overall);
        assertEquals("CHATGPT_PLAN_READY",r.chatProvider);
    }

    @Test public void debugBuildNeverClaimsInstallIdentityReady(){
        AndroidFounderReadiness.Report r=AndroidFounderReadiness.evaluate(new AndroidFounderReadiness.Snapshot(
            false,true,true,true,true,true,true,true,true,true,true,true,true));
        assertEquals("DEVELOPMENT_BUILD",r.overall);
        assertEquals("DEVELOPMENT_BUILD",r.installIdentity);
    }
}
