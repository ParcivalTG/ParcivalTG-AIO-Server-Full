package com.aio.founder;

interface PresenceTransport extends AutoCloseable {
    void connect() throws Exception;
    PresenceProtocol.Frame exchange(byte[] frame) throws Exception;
    void setReadTimeoutMillis(int timeoutMillis) throws Exception;
    String label();
    @Override void close();
}
