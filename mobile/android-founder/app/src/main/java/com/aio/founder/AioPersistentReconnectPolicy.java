package com.aio.founder;

final class AioPersistentReconnectPolicy {
    private static final long[] DELAYS={0L,2_000L,5_000L,10_000L,20_000L,30_000L};
    private AioPersistentReconnectPolicy(){}

    static long delayMillis(int attempt){
        if(attempt<0)throw new IllegalArgumentException("RECONNECT_ATTEMPT_INVALID");
        return DELAYS[Math.min(attempt,DELAYS.length-1)];
    }
}
