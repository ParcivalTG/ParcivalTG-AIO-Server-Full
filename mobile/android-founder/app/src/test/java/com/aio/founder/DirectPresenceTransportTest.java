package com.aio.founder;

import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectPresenceTransportTest {
    @Test public void failedConnectDoesNotPoisonNextAttempt() throws Exception {
        int port;
        try(ServerSocket reservation=new ServerSocket(0)){port=reservation.getLocalPort();}
        DirectPresenceTransport transport=new DirectPresenceTransport("127.0.0.1",port);
        try{transport.connect();fail("first connect must fail while no listener exists");}
        catch(java.net.ConnectException expected){assertNotNull(expected);}

        try(ServerSocket server=new ServerSocket(port)){
            Thread acceptor=new Thread(() -> {
                try(Socket accepted=server.accept()){}catch(Exception ignored){}
            });
            acceptor.start();
            transport.connect();
            transport.close();
            acceptor.join(2000);
            assertFalse("retry acceptor should have completed",acceptor.isAlive());
        }
    }

    @Test public void closeThenConnectCreatesFreshSocket() throws Exception {
        try (ServerSocket server=new ServerSocket(0)) {
            DirectPresenceTransport transport=new DirectPresenceTransport("127.0.0.1",server.getLocalPort());
            Thread acceptor=new Thread(() -> {
                try {
                    Socket first=server.accept(); first.close();
                    Socket second=server.accept(); second.close();
                } catch (Exception ignored) {}
            });
            acceptor.start();
            transport.connect();
            transport.close();
            transport.connect();
            transport.close();
            acceptor.join(2000);
            assertFalse("acceptor should have completed",acceptor.isAlive());
        }
    }
}
