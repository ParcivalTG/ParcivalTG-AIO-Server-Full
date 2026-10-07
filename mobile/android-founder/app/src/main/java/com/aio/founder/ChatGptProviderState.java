package com.aio.founder;

import java.util.List;

final class ChatGptProviderState {
    enum Phase { SIGNED_OUT, SIGNING_IN, READY, SIGNED_IN_NO_PLAN, HOLD }

    static final class Snapshot {
        final Phase phase;
        final String accountLabel,selectedModel,lastCode;
        final List<ChatGptResponsesContract.Model> models;
        final boolean planUsageGranted;
        Snapshot(Phase phase,String accountLabel,String selectedModel,String lastCode,
                 List<ChatGptResponsesContract.Model> models,boolean planUsageGranted){
            this.phase=phase;this.accountLabel=accountLabel;this.selectedModel=selectedModel;
            this.lastCode=lastCode;this.models=List.copyOf(models);this.planUsageGranted=planUsageGranted;
        }
    }

    private Phase phase=Phase.SIGNED_OUT;
    private String accountLabel="",selectedModel="",lastCode="SIGNED_OUT";
    private List<ChatGptResponsesContract.Model> models=List.of();
    private boolean planUsageGranted;

    synchronized void signingIn(){
        phase=Phase.SIGNING_IN;lastCode="CHATGPT_AUTH_IN_PROGRESS";
    }

    synchronized void signedOut(){
        phase=Phase.SIGNED_OUT;accountLabel="";selectedModel="";
        lastCode="SIGNED_OUT";models=List.of();planUsageGranted=false;
    }

    synchronized void authorized(ChatGptSessionCodec.Session session,
                                 List<ChatGptResponsesContract.Model> catalog){
        if(session==null||catalog==null||catalog.isEmpty())
            throw new IllegalArgumentException("CHATGPT_PROVIDER_STATE_INVALID");
        String prior=selectedModel;
        models=List.copyOf(catalog);
        boolean keep=!prior.isEmpty()&&models.stream().anyMatch(x->prior.equals(x.slug));
        selectedModel=keep?prior:models.get(0).slug;
        accountLabel=!session.email.isEmpty()?session.email:
            (!session.name.isEmpty()?session.name:session.subject);
        planUsageGranted=session.tokens.planUsageGranted;
        phase=planUsageGranted?Phase.READY:Phase.SIGNED_IN_NO_PLAN;
        lastCode=planUsageGranted?"CHATGPT_READY":"CHATGPT_PLAN_USAGE_NOT_GRANTED";
    }

    synchronized void selectModel(String slug){
        if(slug==null||models.stream().noneMatch(x->slug.equals(x.slug)))
            throw new IllegalArgumentException("CHATGPT_MODEL_NOT_AVAILABLE");
        selectedModel=slug;lastCode="CHATGPT_MODEL_SELECTED";
    }

    synchronized void hold(String code){
        phase=Phase.HOLD;lastCode=safe(code);
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(phase,accountLabel,selectedModel,lastCode,models,planUsageGranted);
    }

    private static String safe(String code){
        if(code!=null&&code.matches("[A-Za-z0-9_.:-]{1,96}"))return code;
        return "CHATGPT_PROVIDER_HOLD";
    }
}
