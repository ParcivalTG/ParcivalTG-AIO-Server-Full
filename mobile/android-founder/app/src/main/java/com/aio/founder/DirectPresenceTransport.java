package com.aio.founder;

import java.net.InetSocketAddress;
import java.net.Socket;

final class DirectPresenceTransport implements PresenceTransport {
    private final String host;
    private final int port;
    private Socket socket;

    DirectPresenceTransport(String host,int port){
        this.host=host;this.port=port;
    }
    @Override public synchronized void connect() throws Exception {
        close();
        Socket fresh=new Socket();
        try {
            fresh.setTcpNoDelay(true);
            fresh.connect(new InetSocketAddress(host,port),7000);
            fresh.setSoTimeout(7000);
            socket=fresh;
        } catch (Exception failure) {
            try { fresh.close(); } catch (Exception ignored) {}
            throw failure;
        }
    }
    @Override public PresenceProtocol.Frame exchange(byte[] frame) throws Exception {
        Socket active=socket;
        if(active==null||active.isClosed())throw new java.io.IOException("DIRECT_NOT_CONNECTED");
        active.getOutputStream().write(frame);
        active.getOutputStream().flush();
        return PresenceProtocol.readFrame(active.getInputStream());
    }
    @Override public void setReadTimeoutMillis(int timeoutMillis) throws Exception {
        Socket active=socket;
        if(active==null||active.isClosed())throw new java.io.IOException("DIRECT_NOT_CONNECTED");
        active.setSoTimeout(timeoutMillis);
    }
    @Override public String label(){return "DIRECT";}
    @Override public synchronized void close(){Socket active=socket;socket=null;try{if(active!=null)active.close();}catch(Exception ignored){}}
}
