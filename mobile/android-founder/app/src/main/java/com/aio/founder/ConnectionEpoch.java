package com.aio.founder;

import java.util.concurrent.atomic.AtomicLong;

/** Local causal fence: an old socket/result cannot reacquire authority after cancellation. */
final class ConnectionEpoch {
    private final AtomicLong epoch = new AtomicLong();
    long invalidate() { return epoch.incrementAndGet(); }
    boolean accepts(long value) { return epoch.get() == value; }
}
