package com.aio.founder;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidSigningIdentityTest {
    @Test public void fingerprintNormalizationIsStable(){
        assertEquals("aabbcc",AndroidSigningIdentity.normalize("AA:BB:CC"));
    }

    @Test public void exactSignerMatchIsRequired(){
        String signer="a".repeat(64);
        assertTrue(AndroidSigningIdentity.matches(signer,List.of(signer)));
        assertTrue(AndroidSigningIdentity.matches(signer.toUpperCase(),List.of(signer)));
        assertFalse(AndroidSigningIdentity.matches("b".repeat(64),List.of(signer)));
    }

    @Test public void malformedExpectedFingerprintFailsClosed(){
        assertFalse(AndroidSigningIdentity.matches("",List.of("a".repeat(64))));
        assertFalse(AndroidSigningIdentity.matches("abcd",List.of("a".repeat(64))));
    }
}
