package com.aio.founder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * Transport-neutral Presence session bootstrap.
 * Direct transport is preferred. Cloud may replace it only after an eligible
 * network-path failure. Authority/protocol rejection never triggers fallback.
 */
final class FounderPresenceSessionController {
    interface CloudFactory { PresenceTransport create() throws Exception; }

    static final class Ready {
        final PresenceTransport transport;
        final PresenceProtocol.Frame hello;
        final PresenceProtocol.Frame status;
        final long pingRoundTripMs;
        Ready(PresenceTransport transport,PresenceProtocol.Frame hello,PresenceProtocol.Frame status,long pingRoundTripMs){
            this.transport=transport;this.hello=hello;this.status=status;this.pingRoundTripMs=pingRoundTripMs;
        }
    }

    Ready connect(PresenceTransport direct,CloudFactory cloudFactory,byte[] witnessPayload)throws Exception{
        if(direct==null&&cloudFactory==null)throw new IllegalArgumentException("PRESENCE_ROUTE_REQUIRED");
        boolean directOpened=false;
        if(direct!=null){
            try{
                direct.connect();
                directOpened=true;
            }catch(Exception directConnectFailure){
                direct.close();
                if(!PresenceFallbackPolicy.eligible(directConnectFailure)||cloudFactory==null)throw directConnectFailure;
            }
            if(directOpened){
                try{return qualify(direct,witnessPayload);}
                catch(Exception failure){direct.close();throw failure;}
            }
        }
        if(cloudFactory==null)throw new IOException("CLOUD_TRANSPORT_UNAVAILABLE");
        PresenceTransport cloud=cloudFactory.create();
        if(cloud==null)throw new IOException("CLOUD_TRANSPORT_UNAVAILABLE");
        try{
            cloud.connect();
            return qualify(cloud,witnessPayload);
        }catch(Exception failure){
            cloud.close();
            throw failure;
        }
    }

    private Ready qualify(PresenceTransport transport,byte[] witnessPayload)throws Exception{
        transport.setReadTimeoutMillis(7000);
        PresenceProtocol.Frame hello=exchange(transport,PresenceProtocol.HELLO,PresenceProtocol.HELLO_REPLY,
            UUID.randomUUID(),witnessPayload==null?new byte[0]:witnessPayload);
        if(hello.flags!=0)throw new SecurityException("PRESENCE_WITNESS_REJECTED");

        PresenceProtocol.Frame status=exchange(transport,PresenceProtocol.STATUS,PresenceProtocol.STATUS_REPLY,
            UUID.randomUUID(),new byte[0]);
        byte[] ping=PresenceProtocol.newNonce().getBytes(StandardCharsets.US_ASCII);
        long started=System.nanoTime();
        PresenceProtocol.Frame pong=exchange(transport,PresenceProtocol.PING,PresenceProtocol.PING_REPLY,
            UUID.randomUUID(),ping);
        long elapsed=(System.nanoTime()-started)/1_000_000L;
        if(pong.flags!=0||!Arrays.equals(ping,pong.payload))throw new SecurityException("PING_INVALID");
        return new Ready(transport,hello,status,elapsed);
    }

    static PresenceProtocol.Frame exchange(PresenceTransport transport,byte type,byte expected,UUID id,byte[] payload)throws Exception{
        byte[] frame=PresenceProtocol.frame(type,payload,id);
        PresenceProtocol.Frame response;
        try{response=transport.exchange(frame);}
        finally{Arrays.fill(frame,(byte)0);}
        if(!id.equals(response.id)||response.type!=expected)throw new SecurityException("FRAME_CORRELATION_INVALID");
        if((response.flags&~1)!=0)throw new SecurityException("FRAME_FLAGS_INVALID");
        return response;
    }
}
