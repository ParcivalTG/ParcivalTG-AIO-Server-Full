package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class FounderIntentCapsuleTest {
    @Test public void actionIdentifierRemainsStable() {
        assertEquals("windows.objective.submit", FounderIntentCapsule.ACTION);
        assertEquals("aio.founder-intent.v2", FounderIntentCapsule.SCHEMA);
    }

    @Test public void clearPairingFenceRejectsOldGenerationAndAcceptsNewGeneration() {
        ConnectionEpoch epoch = new ConnectionEpoch();
        long first = epoch.invalidate();
        assertTrue(epoch.accepts(first));
        epoch.invalidate();
        assertFalse(epoch.accepts(first));
        long reconnected = epoch.invalidate();
        assertTrue(epoch.accepts(reconnected));
        assertFalse(epoch.accepts(first));
    }
}
