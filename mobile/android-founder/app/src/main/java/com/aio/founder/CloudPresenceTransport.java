package com.aio.founder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

final class CloudPresenceTransport implements PresenceTransport {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final String rendezvousWss,peerId,remotePeerId;
    private final byte[] peerAdmissionKey,tunnelKey;
    private final OkHttpClient client;
    private final Object sendGate=new Object();
    private final Object callbackGate=new Object();
    private final ConnectionEpoch socketEpoch=new ConnectionEpoch();
    private volatile CountDownLatch welcome=new CountDownLatch(1);
    private final BlockingQueue<Inbound> inbound=new ArrayBlockingQueue<>(8);
    private final BlockingQueue<PresenceProtocol.Frame> nodeInbox=new ArrayBlockingQueue<>(8);
    private final AtomicReference<Exception> terminal=new AtomicReference<>();
    private volatile WebSocket socket;
    private volatile boolean connected,closed;
    private volatile int readTimeoutMillis=7000;
    private long sendSeq,recvSeq;
    private volatile UUID pendingRequestId;
    private volatile long remoteSessionEpoch;
    private String remoteSourceSession;

    CloudPresenceTransport(String rendezvousWss,String peerId,byte[] peerAdmissionKey,byte[] tunnelKey,String remotePeerId){
        if(rendezvousWss==null||!rendezvousWss.startsWith("wss://"))throw new IllegalArgumentException("CLOUD_WSS_REQUIRED");
        if(!validPeer(peerId)||!validPeer(remotePeerId))throw new IllegalArgumentException("CLOUD_PEER_ID_INVALID");
        if(peerAdmissionKey==null||peerAdmissionKey.length!=32||tunnelKey==null||tunnelKey.length!=32)
            throw new IllegalArgumentException("CLOUD_KEY_LENGTH");
        this.rendezvousWss=rendezvousWss;this.peerId=peerId;this.remotePeerId=remotePeerId;
        this.peerAdmissionKey=peerAdmissionKey.clone();this.tunnelKey=tunnelKey.clone();
        this.client=new OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS).build();
    }
    private static boolean validPeer(String value){return value!=null&&value.matches("[A-Za-z0-9_.:-]{1,128}");}
    private static boolean validSession(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{16,64}");}
    static String admissionSignature(byte[] peerKey,String peerId,long ts,String nonce)throws Exception{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(peerKey,"HmacSHA256"));
        byte[] digest=mac.doFinal(String.join("\n","WS","/v1/peer",peerId,Long.toString(ts),nonce).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder(digest.length*2);for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&0xff));
        Arrays.fill(digest,(byte)0);return hex.toString();
    }
    private static String nonce(int count){
        byte[] raw=new byte[count];RANDOM.nextBytes(raw);
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);}
        finally{Arrays.fill(raw,(byte)0);}
    }
    @Override public synchronized void connect() throws Exception {
        if(closed)throw new IOException("CLOUD_CLOSED");
        if(connected)throw new IOException("CLOUD_ALREADY_CONNECTED");
        final long generation;
        final WebSocket prior;
        synchronized(callbackGate){
            generation=socketEpoch.invalidate();
            prior=socket;socket=null;connected=false;
            clearQueuedPayloads();
            terminal.set(null);
            welcome=new CountDownLatch(1);
        }
        if(prior!=null)prior.cancel();
        synchronized(sendGate){sendSeq=0;}
        long ts=System.currentTimeMillis();String n=nonce(16);String sig=admissionSignature(peerAdmissionKey,peerId,ts,n);
        String https="https://"+rendezvousWss.substring("wss://".length());
        HttpUrl url=HttpUrl.get(https).newBuilder()
            .addQueryParameter("peer_id",peerId).addQueryParameter("ts",Long.toString(ts))
            .addQueryParameter("nonce",n).addQueryParameter("sig",sig).build();
        Request request=new Request.Builder().url(url).build();
        WebSocket candidate=client.newWebSocket(request,new Listener(generation));
        synchronized(callbackGate){
            if(!socketEpoch.accepts(generation)){candidate.cancel();throw new IOException("CLOUD_CONNECT_CANCELLED");}
            socket=candidate;
        }
        try{
            if(!welcome.await(7000,TimeUnit.MILLISECONDS)){
                Exception failure=terminal.get();if(failure!=null)throw failure;
                throw new SocketTimeoutException("CLOUD_WELCOME_TIMEOUT");
            }
            Exception failure=terminal.get();if(failure!=null)throw failure;
            if(!connected)throw new IOException("CLOUD_NOT_CONNECTED");
        }catch(Exception failure){
            synchronized(callbackGate){
                if(socketEpoch.accepts(generation)){
                    socketEpoch.invalidate();connected=false;
                    if(socket==candidate)socket=null;
                    clearQueuedPayloads();
                }
            }
            candidate.cancel();
            throw failure;
        }
    }
    @Override public synchronized PresenceProtocol.Frame exchange(byte[] frame)throws Exception{
        if(closed||!connected||socket==null)throw new IOException("CLOUD_NOT_CONNECTED");
        PresenceProtocol.Frame request;
        ByteArrayInputStream requestInput=new ByteArrayInputStream(frame);
        request=PresenceProtocol.readFrame(requestInput);
        if(requestInput.available()!=0)throw new SecurityException("CLOUD_AIOP_TRAILING_BYTES");
        UUID requestId=request.id;Arrays.fill(request.payload,(byte)0);
        if(pendingRequestId!=null)throw new IOException("CLOUD_EXCHANGE_ALREADY_PENDING");
        pendingRequestId=requestId;
        try{
            sendEnvelope(frame);
            Exception priorFailure=terminal.get();if(priorFailure!=null)throw priorFailure;
            Inbound row=inbound.poll(readTimeoutMillis,TimeUnit.MILLISECONDS);
            if(row==null){Exception failure=terminal.get();if(failure!=null)throw failure;throw new SocketTimeoutException("CLOUD_RESPONSE_TIMEOUT");}
            if(row.failure!=null)throw row.failure;
            return row.frame;
        }finally{
            if(requestId.equals(pendingRequestId))pendingRequestId=null;
        }
    }

    private void sendEnvelope(byte[] frame)throws Exception{
        if(closed||!connected||socket==null)throw new IOException("CLOUD_NOT_CONNECTED");
        synchronized(sendGate){
            long seq=++sendSeq,expiry=System.currentTimeMillis()+30_000;
            byte[] nonceBytes=new byte[12];RANDOM.nextBytes(nonceBytes);
            CloudTunnelCodec.Envelope sealed;
            try{sealed=CloudTunnelCodec.seal(tunnelKey,peerId,remotePeerId,seq,expiry,frame,nonceBytes);}
            finally{Arrays.fill(nonceBytes,(byte)0);}
            String inner=CloudTunnelCodec.toJsonString(sealed);
            JSONObject outer=new JSONObject();
            outer.put("type","envelope");outer.put("to",remotePeerId);outer.put("seq",seq);outer.put("expiresAtUnixMs",expiry);
            outer.put("envelopeB64",Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8)));
            if(!socket.send(outer.toString()))throw new IOException("CLOUD_SEND_REJECTED");
        }
    }

    void sendNodeReply(UUID requestId,boolean accepted,byte[] payload)throws Exception{
        if(requestId==null)throw new IllegalArgumentException("CLOUD_NODE_REQUEST_ID_REQUIRED");
        byte[] frame=PresenceProtocol.frame(PresenceProtocol.ANDROID_CAPABILITY_REPLY,accepted?(byte)0:(byte)1,payload,requestId);
        try{sendEnvelope(frame);}
        finally{Arrays.fill(frame,(byte)0);}
    }

    String remotePeerId(){return remotePeerId;}
    long remoteSessionEpoch(){return remoteSessionEpoch;}

    PresenceProtocol.Frame pollNodeRequest(long timeoutMillis)throws Exception{
        if(timeoutMillis<1||timeoutMillis>120_000)throw new IllegalArgumentException("CLOUD_NODE_TIMEOUT_INVALID");
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while(true){
            if(closed)throw new IOException("CLOUD_CLOSED");
            Exception prior=terminal.get();if(prior!=null)throw prior;
            long remaining=deadline-System.nanoTime();
            if(remaining<=0)return null;
            // Poll in short bounded slices so a WebSocket failure wakes the
            // persistent-node path promptly instead of waiting out its full
            // caller timeout.
            PresenceProtocol.Frame row=nodeInbox.poll(
                Math.min(TimeUnit.NANOSECONDS.toMillis(remaining),250L),TimeUnit.MILLISECONDS);
            if(row!=null)return row;
        }
    }
    @Override public void setReadTimeoutMillis(int timeoutMillis){
        if(timeoutMillis<1||timeoutMillis>120_000)throw new IllegalArgumentException("CLOUD_TIMEOUT_INVALID");
        readTimeoutMillis=timeoutMillis;
    }
    @Override public String label(){return "R5_CLOUD";}
    @Override public void close(){
        WebSocket ws;
        synchronized(callbackGate){
            if(closed)return;
            closed=true;connected=false;socketEpoch.invalidate();
            ws=socket;socket=null;pendingRequestId=null;
            clearQueuedPayloads();
        }
        if(ws!=null)ws.close(1000,"endpoint closing");
        synchronized(sendGate){
            Arrays.fill(peerAdmissionKey,(byte)0);Arrays.fill(tunnelKey,(byte)0);
        }
        client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();
    }
    private void clearQueuedPayloads(){
        Inbound row;while((row=inbound.poll())!=null)if(row.frame!=null)Arrays.fill(row.frame.payload,(byte)0);
        PresenceProtocol.Frame node;while((node=nodeInbox.poll())!=null)Arrays.fill(node.payload,(byte)0);
    }
    private final class Listener extends WebSocketListener{
        private final long generation;
        Listener(long generation){this.generation=generation;}
        private boolean current(){return socketEpoch.accepts(generation);}
        @Override public void onOpen(WebSocket webSocket,Response response){
            synchronized(callbackGate){if(!current())webSocket.cancel();}
        }
        @Override public void onMessage(WebSocket webSocket,String text){
            synchronized(callbackGate){
                if(!current())return;
                try{
                JSONObject root=new JSONObject(text);String type=root.optString("type","");
                if("welcome".equals(type)){
                    if(!peerId.equals(root.optString("peerId",""))||!validSession(root.optString("sourceSessionId","")))
                        throw new SecurityException("CLOUD_WELCOME_INVALID");
                    connected=true;welcome.countDown();return;
                }
                if("superseded".equals(type)){fail(new SecurityException("CLOUD_SESSION_SUPERSEDED"));return;}
                if(!"envelope".equals(type))return;
                if(!remotePeerId.equals(root.getString("from"))||!peerId.equals(root.getString("to")))
                    throw new SecurityException("CLOUD_REMOTE_PEER_MISMATCH");
                long outerSeq=root.getLong("seq"),outerExpiry=root.getLong("expiresAtUnixMs");
                if(outerSeq<1||outerExpiry<=System.currentTimeMillis())throw new SecurityException("CLOUD_OUTER_ENVELOPE_REJECTED");
                String sourceSession=root.getString("sourceSessionId");
                if(!validSession(sourceSession))throw new SecurityException("CLOUD_SOURCE_SESSION_INVALID");
                if(remoteSourceSession==null||!remoteSourceSession.equals(sourceSession)){
                    remoteSourceSession=sourceSession;recvSeq=0;remoteSessionEpoch++;
                }
                byte[] innerBytes=Base64.getDecoder().decode(root.getString("envelopeB64"));
                String inner;
                try{inner=new String(innerBytes,StandardCharsets.UTF_8);}
                finally{Arrays.fill(innerBytes,(byte)0);}
                CloudTunnelCodec.Envelope envelope=CloudTunnelCodec.fromJsonString(inner);
                if(envelope.seq!=outerSeq||envelope.expiresAtUnixMs!=outerExpiry)throw new SecurityException("CLOUD_ENVELOPE_BINDING_MISMATCH");
                byte[] frameBytes=CloudTunnelCodec.open(tunnelKey,envelope,remotePeerId,peerId,recvSeq,System.currentTimeMillis());
                recvSeq=envelope.seq;
                PresenceProtocol.Frame parsed;
                try{
                    ByteArrayInputStream input=new ByteArrayInputStream(frameBytes);
                    parsed=PresenceProtocol.readFrame(input);
                    if(input.available()!=0)throw new SecurityException("CLOUD_AIOP_TRAILING_BYTES");
                }finally{Arrays.fill(frameBytes,(byte)0);}
                CloudInboundRouter.Route route=CloudInboundRouter.route(pendingRequestId,parsed);
                if(route==CloudInboundRouter.Route.CORRELATED_RESPONSE){
                    if(!inbound.offer(Inbound.frame(parsed))){Arrays.fill(parsed.payload,(byte)0);throw new IOException("CLOUD_INBOUND_BACKPRESSURE");}
                }else if(route==CloudInboundRouter.Route.ANDROID_NODE_REQUEST){
                    if(!nodeInbox.offer(parsed)){Arrays.fill(parsed.payload,(byte)0);throw new IOException("CLOUD_NODE_BACKPRESSURE");}
                }else{
                    Arrays.fill(parsed.payload,(byte)0);
                    throw new SecurityException("CLOUD_UNEXPECTED_FRAME");
                }
                }catch(Exception failure){fail(failure);}
            }
        }
        @Override public void onFailure(WebSocket webSocket,Throwable t,Response response){
            if(response!=null)response.close();
            synchronized(callbackGate){
                if(!current())return;
                fail(t instanceof Exception?(Exception)t:new IOException("CLOUD_FAILURE",t));
            }
        }
        @Override public void onClosed(WebSocket webSocket,int code,String reason){
            synchronized(callbackGate){
                if(!current())return;
                connected=false;if(!closed)fail(new IOException("CLOUD_CLOSED_"+code));
            }
        }
        private void fail(Exception failure){
            if(!current())return;
            if(terminal.compareAndSet(null,failure)){
                connected=false;welcome.countDown();inbound.offer(Inbound.failure(failure));
            }
        }
    }
    private static final class Inbound{
        final PresenceProtocol.Frame frame;final Exception failure;
        private Inbound(PresenceProtocol.Frame frame,Exception failure){this.frame=frame;this.failure=failure;}
        static Inbound frame(PresenceProtocol.Frame frame){return new Inbound(frame,null);}
        static Inbound failure(Exception failure){return new Inbound(null,failure);}
    }
}
