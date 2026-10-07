package com.aio.founder;

import org.junit.Test;

import static org.junit.Assert.*;

public class AndroidReleaseIdentityTest {
    @Test public void candidateAdvancesPriorStableLineage(){
        assertEquals("com.aio.founder",BuildConfig.APPLICATION_ID);
        assertEquals(8,BuildConfig.VERSION_CODE);
        assertEquals("0.8.0-candidate-r5",BuildConfig.VERSION_NAME);
        assertTrue(BuildConfig.VERSION_CODE>7);
    }

    @Test public void governedUpdaterAcceptsVersionEightOverSevenWithSameSigner(){
        String signer="a".repeat(64);
        AndroidUpdatePolicy.validate(
            "com.aio.founder","com.aio.founder",
            7,BuildConfig.VERSION_CODE,
            4_000_000,true,signer,signer);
    }
}
