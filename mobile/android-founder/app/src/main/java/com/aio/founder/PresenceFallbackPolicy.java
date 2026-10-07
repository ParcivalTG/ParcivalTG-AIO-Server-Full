package com.aio.founder;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

final class PresenceFallbackPolicy {
    private PresenceFallbackPolicy() {}
    static boolean eligible(Throwable failure) {
        if (failure instanceof SecurityException || failure instanceof IllegalArgumentException) return false;
        return failure instanceof ConnectException
            || failure instanceof NoRouteToHostException
            || failure instanceof SocketTimeoutException
            || failure instanceof UnknownHostException
            || failure instanceof SocketException;
    }
}
