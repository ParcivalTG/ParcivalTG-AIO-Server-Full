package com.aio.founder;

/**
 * AIO temporal pulse law.
 * Work density increases only as an authority/recovery event enters the local causal horizon.
 */
final class AioEventHorizonPulsePolicy {
    static final long MIN_PULSE_MS=5_000L;
    static final long NEAR_PULSE_MS=15_000L;
    static final long UNVERIFIED_PULSE_MS=10_000L;
    static final long NO_LINK_PULSE_MS=60_000L;
    static final long MAX_PULSE_MS=180_000L;

    private AioEventHorizonPulsePolicy(){}

    static long delayMillis(AioPersistentNodeState.Phase phase,long authorityExpiresAtUnixMs,long nowUnixMs){
        if(phase==null||nowUnixMs<0)throw new IllegalArgumentException("PULSE_INPUT_INVALID");
        switch(phase){
            case STOPPED: return MAX_PULSE_MS;
            case ACTIVE_NO_LINK: return NO_LINK_PULSE_MS;
            case LINK_ATTACHED_UNVERIFIED:
            case HOLD: return UNVERIFIED_PULSE_MS;
            case LINK_VERIFIED:
                if(authorityExpiresAtUnixMs<=0)return MIN_PULSE_MS;
                long refreshBoundary=authorityExpiresAtUnixMs-PresenceAuthorityClient.REFRESH_WINDOW_MS;
                long horizon=refreshBoundary-nowUnixMs;
                if(horizon<=0)return MIN_PULSE_MS;
                long quarter=Math.max(1,horizon/4);
                if(quarter<NEAR_PULSE_MS)return NEAR_PULSE_MS;
                return Math.min(MAX_PULSE_MS,quarter);
            default: throw new IllegalArgumentException("PULSE_PHASE_INVALID");
        }
    }
}
