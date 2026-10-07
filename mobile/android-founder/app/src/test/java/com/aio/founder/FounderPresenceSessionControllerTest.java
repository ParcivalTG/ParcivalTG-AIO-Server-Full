package com.aio.founder;

import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class FounderPresenceSessionControllerTest {
    static final class Fake implements PresenceTransport {
        final Exception connectFailure;
        final boolean rejectHello;
        boolean connected,closed;
        Fake(Exception connectFailure,boolean rejectHello){this.connectFailure=connectFailure;this.rejectHello=rejectHello;}
        @Override public void connect() throws Exception { if(connectFailure!=null)throw connectFailure;connected=true; }
        @Override public PresenceProtocol.Frame exchange(byte[] frame)throws Exception{
            PresenceProtocol.Frame request=PresenceProtocol.readFrame(new ByteArrayInputStream(frame));
            byte responseType;
            byte[] payload=request.payload;
            byte flags=0;
            if(request.type==PresenceProtocol.HELLO){responseType=PresenceProtocol.HELLO_REPLY;if(rejectHello)flags=1;}
            else if(request.type==PresenceProtocol.STATUS){responseType=PresenceProtocol.STATUS_REPLY;payload="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
            else if(request.type==PresenceProtocol.PING)responseType=PresenceProtocol.PING_REPLY;
            else throw new IllegalStateException("unexpected request");
            return new PresenceProtocol.Frame(responseType,flags,request.id,payload);
        }
        @Override public void setReadTimeoutMillis(int timeoutMillis){}
        @Override public String label(){return "FAKE";}
        @Override public void close(){closed=true;}
    }

    @Test public void networkFailureFallsBackToCloud()throws Exception{
        Fake direct=new Fake(new ConnectException("refused"),false);
        Fake cloud=new Fake(null,false);
        FounderPresenceSessionController.Ready ready=new FounderPresenceSessionController().connect(
            direct,()->cloud,"witness".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertSame(cloud,ready.transport);
        assertTrue(direct.closed);
        assertTrue(cloud.connected);
        assertTrue(ready.pingRoundTripMs>=0);
    }

    @Test public void authorityRejectionNeverEscapesIntoCloud(){
        Fake direct=new Fake(null,true);
        AtomicBoolean factoryCalled=new AtomicBoolean(false);
        try{
            new FounderPresenceSessionController().connect(direct,()->{factoryCalled.set(true);return new Fake(null,false);},
                "bad".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fail();
        }catch(Exception expected){
            assertEquals("PRESENCE_WITNESS_REJECTED",expected.getMessage());
        }
        assertFalse(factoryCalled.get());
        assertTrue(direct.closed);
    }

    @Test public void cloudOnlyRouteIsSupported()throws Exception{
        Fake cloud=new Fake(null,false);
        FounderPresenceSessionController.Ready ready=new FounderPresenceSessionController().connect(
            null,()->cloud,"witness".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertSame(cloud,ready.transport);
        assertTrue(cloud.connected);
        assertTrue(ready.pingRoundTripMs>=0);
    }

    @Test public void postConnectNetworkFailureDoesNotDowngradeToCloud(){
        AtomicBoolean factoryCalled=new AtomicBoolean(false);
        PresenceTransport direct=new PresenceTransport(){
            @Override public void connect(){}
            @Override public PresenceProtocol.Frame exchange(byte[] frame)throws Exception{
                throw new java.net.SocketException("reset-after-connect");
            }
            @Override public void setReadTimeoutMillis(int timeoutMillis){}
            @Override public String label(){return "DIRECT";}
            @Override public void close(){}
        };
        try{
            new FounderPresenceSessionController().connect(direct,()->{
                factoryCalled.set(true);return new Fake(null,false);
            },"witness".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fail();
        }catch(Exception expected){
            assertTrue(expected instanceof java.net.SocketException);
        }
        assertFalse(factoryCalled.get());
    }

    @Test public void correlationMismatchFailsClosed()throws Exception{
        PresenceTransport bad=new PresenceTransport(){
            @Override public void connect(){}
            @Override public PresenceProtocol.Frame exchange(byte[] frame)throws Exception{
                PresenceProtocol.Frame req=PresenceProtocol.readFrame(new ByteArrayInputStream(frame));
                return new PresenceProtocol.Frame(PresenceProtocol.HELLO_REPLY,(byte)0,UUID.randomUUID(),new byte[0]);
            }
            @Override public void setReadTimeoutMillis(int timeoutMillis){}
            @Override public String label(){return "BAD";}
            @Override public void close(){}
        };
        try{
            FounderPresenceSessionController.exchange(bad,PresenceProtocol.HELLO,PresenceProtocol.HELLO_REPLY,UUID.randomUUID(),new byte[0]);
            fail();
        }catch(SecurityException expected){assertEquals("FRAME_CORRELATION_INVALID",expected.getMessage());}
    }
}
