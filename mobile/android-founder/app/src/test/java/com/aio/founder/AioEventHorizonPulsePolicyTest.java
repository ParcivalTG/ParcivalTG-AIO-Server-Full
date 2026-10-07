package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioEventHorizonPulsePolicyTest {
    @Test public void farVerifiedAuthorityUsesSparsePulse(){
        long now=1_800_000_000_000L;
        long expiry=now+60*60_000L;
        assertEquals(AioEventHorizonPulsePolicy.MAX_PULSE_MS,
            AioEventHorizonPulsePolicy.delayMillis(
                AioPersistentNodeState.Phase.LINK_VERIFIED,expiry,now));
    }

    @Test public void pulseDensityIncreasesTowardAuthorityHorizon(){
        long now=1_800_000_000_000L;
        long far=AioEventHorizonPulsePolicy.delayMillis(
            AioPersistentNodeState.Phase.LINK_VERIFIED,
            now+40*60_000L,now);
        long near=AioEventHorizonPulsePolicy.delayMillis(
            AioPersistentNodeState.Phase.LINK_VERIFIED,
            now+12*60_000L,now);
        long inside=AioEventHorizonPulsePolicy.delayMillis(
            AioPersistentNodeState.Phase.LINK_VERIFIED,
            now+9*60_000L,now);
        assertTrue(far>near);
        assertTrue(near>inside);
        assertEquals(AioEventHorizonPulsePolicy.MIN_PULSE_MS,inside);
    }

    @Test public void unverifiedAndNoLinkHaveDifferentMass(){
        long now=1_800_000_000_000L;
        assertEquals(AioEventHorizonPulsePolicy.UNVERIFIED_PULSE_MS,
            AioEventHorizonPulsePolicy.delayMillis(
                AioPersistentNodeState.Phase.LINK_ATTACHED_UNVERIFIED,0,now));
        assertEquals(AioEventHorizonPulsePolicy.NO_LINK_PULSE_MS,
            AioEventHorizonPulsePolicy.delayMillis(
                AioPersistentNodeState.Phase.ACTIVE_NO_LINK,0,now));
    }

    @Test public void oneHourAuthorityNeedsFarFewerPulsesThanFlatThirtySeconds(){
        long now=1_800_000_000_000L;
        long expiry=now+59*60_000L;
        long refreshBoundary=expiry-PresenceAuthorityClient.REFRESH_WINDOW_MS;
        long t=now;
        int pulses=0;
        while(t<refreshBoundary&&pulses<1000){
            long d=AioEventHorizonPulsePolicy.delayMillis(
                AioPersistentNodeState.Phase.LINK_VERIFIED,expiry,t);
            t+=d;
            pulses++;
        }
        int flat=(int)Math.ceil((refreshBoundary-now)/30_000.0);
        assertTrue(pulses<=30);
        assertTrue(flat>=90);
        assertTrue(pulses*4<flat*2); // comfortably below half the flat wake count
    }

    @Test public void missingVerifiedAuthorityPulsesImmediately(){
        assertEquals(AioEventHorizonPulsePolicy.MIN_PULSE_MS,
            AioEventHorizonPulsePolicy.delayMillis(
                AioPersistentNodeState.Phase.LINK_VERIFIED,0,1_800_000_000_000L));
    }
}
