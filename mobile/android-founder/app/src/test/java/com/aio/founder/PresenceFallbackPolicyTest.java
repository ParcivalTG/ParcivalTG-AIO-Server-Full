package com.aio.founder;

import org.junit.Test;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import static org.junit.Assert.*;

public class PresenceFallbackPolicyTest {
    @Test public void networkConnectFailuresMayFallback(){
        assertTrue(PresenceFallbackPolicy.eligible(new ConnectException("refused")));
        assertTrue(PresenceFallbackPolicy.eligible(new SocketTimeoutException("timeout")));
    }
    @Test public void authorityAndConfigurationFailuresNeverFallback(){
        assertFalse(PresenceFallbackPolicy.eligible(new SecurityException("PRESENCE_WITNESS_REJECTED")));
        assertFalse(PresenceFallbackPolicy.eligible(new IllegalArgumentException("ENDPOINT_INVALID")));
    }
}
