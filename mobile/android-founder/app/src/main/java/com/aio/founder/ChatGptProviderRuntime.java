package com.aio.founder;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ChatGptProviderRuntime implements AutoCloseable {
    interface Listener {
        default void onState(ChatGptProviderState.Snapshot snapshot){}
        default void onDelta(String delta){}
        default void onCompleted(ChatGptResponsesContract.Completion completion){}
        default void onFailure(String code){}
    }

    interface ToolExecutor {
        ChatGptToolLoop.Execution execute(String name,String arguments)throws Exception;
    }

    private final ChatGptSessionStore store;
    private final ChatGptProviderClient client;
    private final ChatGptProviderState state=new ChatGptProviderState();
    private final ChatGptOAuthController oauth;
    private final ChatGptReasoningStore reasoning;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object refreshGate=new Object();
    private volatile boolean closed;
    private volatile Listener observer=new Listener(){};

    ChatGptProviderRuntime(Context context){
        Context app=context.getApplicationContext();
        store=new ChatGptSessionStore(app);
        client=new ChatGptProviderClient();
        oauth=new ChatGptOAuthController(app,store,client);
        reasoning=new ChatGptReasoningStore(app);
        worker.execute(this::bootstrap);
    }

    ChatGptProviderState.Snapshot snapshot(){return state.snapshot();}

    void attach(Listener listener){
        observer=safe(listener);
        observer.onState(state.snapshot());
    }
    void detach(){observer=new Listener(){};}
    private void publishState(){observer.onState(state.snapshot());}
    private void publishFailure(String code){observer.onFailure(code);}

    void signIn(Listener listener){
        requireOpen();
        Listener target=safe(listener);
        boolean forceConsent=state.snapshot().phase==ChatGptProviderState.Phase.SIGNED_IN_NO_PLAN;
        state.signingIn();target.onState(state.snapshot());publishState();
        oauth.start(new ChatGptOAuthController.Listener(){
            @Override public void onState(String code){
                if("CHATGPT_AUTH_HOLD".equals(code))state.hold(code);
                target.onState(state.snapshot());publishState();
            }
            @Override public void onAuthorized(ChatGptSessionCodec.Session session){
                worker.execute(()->{
                    try{
                        List<ChatGptResponsesContract.Model> models=session.tokens.planUsageGranted
                            ?client.models(session.tokens.accessToken):List.of();
                        state.authorized(session,models);
                        target.onState(state.snapshot());publishState();
                    }catch(Exception failure){
                        state.hold(safeCode(failure));target.onState(state.snapshot());publishState();
                        target.onFailure(safeCode(failure));publishFailure(safeCode(failure));
                    }
                });
            }
            @Override public void onFailure(String code){
                state.hold(code);target.onState(state.snapshot());publishState();target.onFailure(code);publishFailure(code);
            }
        },forceConsent);
    }

    void signOut(Listener listener){
        requireOpen();
        Listener target=safe(listener);
        oauth.cancel();
        worker.execute(()->{
            String note="CHATGPT_SIGNED_OUT";
            try{
                ChatGptSessionCodec.Session session=store.load();
                if(session!=null){
                    try{
                        if(!client.revoke(session))note="CHATGPT_SIGNOUT_REMOTE_UNCONFIRMED";
                    }catch(Exception revocationFailure){
                        note="CHATGPT_SIGNOUT_REMOTE_UNCONFIRMED";
                    }
                }
            }catch(Exception ignored){
                note="CHATGPT_SIGNOUT_REMOTE_UNCONFIRMED";
            }finally{
                store.clearSession();
                state.signedOut();target.onState(state.snapshot());publishState();
            }
            if(!"CHATGPT_SIGNED_OUT".equals(note)){target.onFailure(note);publishFailure(note);}
        });
    }

    void selectModel(String slug,Listener listener){
        requireOpen();
        Listener target=safe(listener);
        try{
            state.selectModel(slug);target.onState(state.snapshot());publishState();
        }catch(Exception failure){target.onFailure(safeCode(failure));publishFailure(safeCode(failure));}
    }

    void refreshModels(Listener listener){
        requireOpen();
        Listener target=safe(listener);
        worker.execute(()->{
            try{
                ChatGptSessionCodec.Session session=ensureAccess();
                List<ChatGptResponsesContract.Model> models=session.tokens.planUsageGranted
                    ?client.models(session.tokens.accessToken):List.of();
                state.authorized(session,models);target.onState(state.snapshot());publishState();
            }catch(Exception failure){
                state.hold(safeCode(failure));target.onState(state.snapshot());publishState();target.onFailure(safeCode(failure));publishFailure(safeCode(failure));
            }
        });
    }

    void respond(String intentId,String privacyClass,String rawText,String minimizedText,String instructions,
                 List<ChatGptResponsesContract.InputItem> context,Listener listener){
        respond(intentId,privacyClass,rawText,minimizedText,instructions,context,null,listener);
    }

    void respond(String intentId,String privacyClass,String rawText,String minimizedText,String instructions,
                 List<ChatGptResponsesContract.InputItem> context,ToolExecutor toolExecutor,Listener listener){
        requireOpen();
        Listener target=safe(listener);
        worker.execute(()->{
            try{
                String projected=ChatGptProjectionPolicy.projectText(privacyClass,rawText,minimizedText);
                ChatGptSessionCodec.Session session=ensureAccess();
                if(!session.tokens.planUsageGranted)throw new SecurityException("CHATGPT_PLAN_USAGE_NOT_GRANTED");
                ChatGptProviderState.Snapshot snapshot=state.snapshot();
                if(snapshot.phase!=ChatGptProviderState.Phase.READY||snapshot.selectedModel.isEmpty())
                    throw new IllegalStateException("CHATGPT_PROVIDER_NOT_READY");
                if(intentId==null||intentId.isBlank()||intentId.length()>512)
                    throw new IllegalArgumentException("CHATGPT_INTENT_ID_INVALID");

                ArrayList<ChatGptResponsesContract.InputItem> input=new ArrayList<>();
                if(context!=null)input.addAll(context);
                input.addAll(ChatGptInputBudget.newestWithin(
                    reasoning.replayTail(8),8,96*1024));
                input.add(ChatGptResponsesContract.message("user",projected));

                List<ChatGptResponsesContract.FunctionTool> tools=
                    toolExecutor==null?List.of():ChatGptAioToolContract.p0Tools();

                for(int round=0;round<4;round++){
                    ChatGptResponsesContract.Completion completion=client.respond(
                        session.tokens.accessToken,snapshot.selectedModel,instructions,input,tools,target::onDelta);
                    reasoning.append(intentId,completion.reasoningItems);

                    if(completion.functionCalls.isEmpty()){
                        target.onCompleted(completion);
                        return;
                    }
                    if(toolExecutor==null)
                        throw new SecurityException("CHATGPT_TOOL_EXECUTOR_REQUIRED");

                    ChatGptToolLoop.Step step=ChatGptToolLoop.advance(
                        input,completion,(name,arguments)->toolExecutor.execute(name,arguments));
                    if(step.terminal){
                        target.onCompleted(step.completion);
                        return;
                    }
                    input=new ArrayList<>(step.nextInput);
                }
                throw new IllegalStateException("CHATGPT_TOOL_LOOP_LIMIT");
            }catch(Exception failure){
                String code=safeCode(failure);target.onFailure(code);publishFailure(code);
            }
        });
    }

    private void bootstrap(){
        if(closed)return;
        try{
            ChatGptSessionCodec.Session session=ensureAccess();
            List<ChatGptResponsesContract.Model> models=session.tokens.planUsageGranted
                ?client.models(session.tokens.accessToken):List.of();
            state.authorized(session,models);publishState();
        }catch(IllegalStateException noSession){
            if("CHATGPT_SIGNED_OUT".equals(noSession.getMessage()))state.signedOut();
            else state.hold(safeCode(noSession));
            publishState();
        }catch(Exception failure){
            state.hold(safeCode(failure));publishState();publishFailure(safeCode(failure));
        }
    }

    private ChatGptSessionCodec.Session ensureAccess()throws Exception{
        synchronized(refreshGate){
            ChatGptSessionCodec.Session session=store.load();
            if(session==null)throw new IllegalStateException("CHATGPT_SIGNED_OUT");
            long now=System.currentTimeMillis()/1000L;
            if(session.tokens.accessUsable(now))return session;
            if(!session.tokens.refreshAllowed(now))throw new IllegalStateException("CHATGPT_REFRESH_TOO_EARLY");
            ChatGptSessionCodec.Session refreshed=client.refresh(session,now);
            store.save(refreshed);
            return refreshed;
        }
    }

    private static Listener safe(Listener listener){return listener==null?new Listener(){}:listener;}

    private void requireOpen(){if(closed)throw new IllegalStateException("CHATGPT_RUNTIME_CLOSED");}

    private static String safeCode(Exception failure){
        String message=failure.getMessage();
        if(message!=null&&message.matches("[A-Za-z0-9_.:-]{1,128}"))return message;
        return failure instanceof SecurityException?"CHATGPT_AUTHORITY_DENIED":"CHATGPT_PROVIDER_FAILURE";
    }

    @Override public void close(){
        closed=true;oauth.close();worker.shutdownNow();
    }
}
