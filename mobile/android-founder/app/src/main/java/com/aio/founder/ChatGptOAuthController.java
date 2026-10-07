package com.aio.founder;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

final class ChatGptOAuthController implements AutoCloseable {
    interface Listener {
        void onState(String state);
        void onAuthorized(ChatGptSessionCodec.Session session);
        void onFailure(String code);
    }

    private static final int AUTH_TIMEOUT_MS=180_000;
    private static final int REQUEST_LINE_MAX=16*1024;

    private final Context context;
    private final ChatGptSessionStore store;
    private final ChatGptProviderClient provider;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicLong generation=new AtomicLong();
    private volatile ServerSocket activeServer;

    ChatGptOAuthController(Context context,ChatGptSessionStore store,ChatGptProviderClient provider){
        if(context==null||store==null||provider==null)throw new IllegalArgumentException("CHATGPT_OAUTH_DEPENDENCY");
        this.context=context.getApplicationContext();this.store=store;this.provider=provider;
    }

    synchronized void start(Listener listener){start(listener,false);}

    synchronized void start(Listener listener,boolean forceConsent){
        if(listener==null)throw new IllegalArgumentException("CHATGPT_OAUTH_LISTENER_REQUIRED");
        cancel();
        long run=generation.incrementAndGet();
        worker.execute(()->run(run,listener,forceConsent));
    }

    synchronized void cancel(){
        generation.incrementAndGet();
        ServerSocket server=activeServer;activeServer=null;
        if(server!=null)try{server.close();}catch(Exception ignored){}
    }

    private void run(long run,Listener listener,boolean forceConsent){
        ServerSocket server=null;
        try{
            state(run,listener,"CHATGPT_AUTH_PREPARING");
            server=new ServerSocket();
            server.setReuseAddress(false);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),1);
            server.setSoTimeout(AUTH_TIMEOUT_MS);
            synchronized(this){
                if(run!=generation.get()){server.close();return;}
                activeServer=server;
            }

            ChatGptSessionStore.Registration registration=store.registration();
            ChatGptSessionCodec.Session current=null;
            try{current=store.load();}catch(Exception invalid){store.clearSession();}
            ChatGptAuthContract.Attempt attempt;
            if(registration==null){
                attempt=ChatGptAuthContract.newRegistrationAttempt(store.hostId(),server.getLocalPort());
            }else{
                String idHint=current==null?null:current.tokens.idToken;
                attempt=ChatGptAuthContract.newReturningAttempt(
                    registration.hostId,server.getLocalPort(),registration.clientId,
                    idHint,registration.email,forceConsent);
            }

            state(run,listener,"CHATGPT_AUTH_BROWSER");
            Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(attempt.authorizationUrl()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_HISTORY);
            context.startActivity(browser);
            state(run,listener,"CHATGPT_AUTH_WAITING_CALLBACK");

            try(Socket socket=server.accept()){
                if(run!=generation.get())return;
                socket.setSoTimeout(15_000);
                String requestLine=readRequestLine(socket.getInputStream());
                String callbackUrl=ChatGptLoopbackHttp.callbackUrl(requestLine,server.getLocalPort());
                ChatGptAuthContract.Callback callback=attempt.validateCallback(callbackUrl);
                state(run,listener,"CHATGPT_AUTH_EXCHANGING");
                String expectedSubject=registration==null?null:registration.subject;
                ChatGptSessionCodec.Session session=provider.exchange(
                    attempt,callback,expectedSubject,System.currentTimeMillis()/1000L);
                store.save(session);
                writeResponse(socket,ChatGptLoopbackHttp.successResponse());
                if(run==generation.get()){
                    state(run,listener,"CHATGPT_AUTHORIZED");
                    listener.onAuthorized(session);
                }
            }
        }catch(java.net.SocketTimeoutException timeout){
            fail(run,listener,"CHATGPT_AUTH_TIMEOUT");
        }catch(Exception failure){
            fail(run,listener,safeCode(failure));
        }finally{
            synchronized(this){
                if(activeServer==server)activeServer=null;
            }
            if(server!=null)try{server.close();}catch(Exception ignored){}
        }
    }

    private static String readRequestLine(InputStream in)throws Exception{
        byte[] bytes=new byte[REQUEST_LINE_MAX];
        int count=0,prior=-1;
        while(count<bytes.length){
            int value=in.read();
            if(value<0)break;
            if(prior=='\r'&&value=='\n'){
                count--;
                break;
            }
            bytes[count++]=(byte)value;prior=value;
        }
        if(count<=0||count>=REQUEST_LINE_MAX)throw new IllegalArgumentException("CHATGPT_LOOPBACK_REQUEST_INVALID");
        return new String(bytes,0,count,StandardCharsets.US_ASCII);
    }

    private static void writeResponse(Socket socket,String response){
        try{
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            BufferedOutputStream out=new BufferedOutputStream(socket.getOutputStream());
            out.write(bytes);out.flush();
        }catch(Exception ignored){}
    }

    private void state(long run,Listener listener,String state){
        if(run==generation.get())listener.onState(state);
    }

    private void fail(long run,Listener listener,String code){
        if(run!=generation.get())return;
        listener.onState("CHATGPT_AUTH_HOLD");
        listener.onFailure(code);
    }

    private static String safeCode(Exception failure){
        String message=failure.getMessage();
        if(message!=null&&message.matches("[A-Za-z0-9_.:-]{1,96}"))return message;
        return failure instanceof SecurityException?"CHATGPT_AUTHORITY_DENIED":"CHATGPT_AUTH_FAILED";
    }

    @Override public synchronized void close(){
        cancel();worker.shutdownNow();
    }
}
