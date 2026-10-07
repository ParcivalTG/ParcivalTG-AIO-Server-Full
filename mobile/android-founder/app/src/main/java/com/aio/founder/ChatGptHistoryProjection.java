package com.aio.founder;

import java.util.ArrayList;
import java.util.List;

final class ChatGptHistoryProjection {
    static final class Row {
        final String role,text,privacyClass,intentId,requestId;
        final long timestamp;
        Row(String role,String text,String privacyClass,String intentId,String requestId,long timestamp){
            this.role=required(role,96,"CHATGPT_HISTORY_ROLE_INVALID");
            this.text=required(text,256*1024,"CHATGPT_HISTORY_TEXT_INVALID");
            this.privacyClass=required(privacyClass,64,"CHATGPT_HISTORY_PRIVACY_INVALID");
            this.intentId=optional(intentId,512);
            this.requestId=optional(requestId,512);
            if(timestamp<0)throw new IllegalArgumentException("CHATGPT_HISTORY_TIME_INVALID");
            if("EXTERNAL_MINIMIZED".equals(privacyClass))
                throw new SecurityException("CHATGPT_MINIMIZED_HISTORY_REQUIRES_PROJECTION");
            this.timestamp=timestamp;
        }
    }

    static final class Plan {
        final List<ChatGptResponsesContract.InputItem> input;
        final List<String> debugText;
        final AioConversationCausalProjection.Plan causal;
        Plan(List<ChatGptResponsesContract.InputItem> input,List<String> debugText,
             AioConversationCausalProjection.Plan causal){
            this.input=List.copyOf(input);this.debugText=List.copyOf(debugText);this.causal=causal;
        }
    }

    private ChatGptHistoryProjection(){}

    static Plan project(List<Row> rows,String query,int charBudget)throws Exception{
        if(rows==null)throw new IllegalArgumentException("CHATGPT_HISTORY_REQUIRED");
        ArrayList<AioConversationCausalProjection.Turn> eligible=new ArrayList<>();
        for(Row row:rows){
            if(row==null)continue;
            if(!"EXTERNAL_ALLOWED".equals(row.privacyClass))continue;
            eligible.add(new AioConversationCausalProjection.Turn(
                row.role,row.text,row.intentId,row.requestId,row.timestamp));
        }
        if(eligible.isEmpty())return new Plan(List.of(),List.of(),
            AioConversationCausalProjection.project(List.of(),"empty",Math.max(64,charBudget)));

        AioConversationCausalProjection.Plan causal=AioConversationCausalProjection.project(
            eligible,query,charBudget);
        ArrayList<ChatGptResponsesContract.InputItem> input=new ArrayList<>();
        ArrayList<String> debug=new ArrayList<>();
        for(AioConversationCausalProjection.Turn turn:causal.turns){
            String role=providerRole(turn.role);
            if(role==null)continue;
            input.add(ChatGptResponsesContract.message(role,turn.text));
            debug.add(turn.role+": "+turn.text);
        }
        return new Plan(input,debug,causal);
    }

    private static String providerRole(String role){
        if("Founder".equalsIgnoreCase(role)||"User".equalsIgnoreCase(role))return "user";
        if("ChatGPT".equalsIgnoreCase(role)||"GPT".equalsIgnoreCase(role)||
            "Assistant".equalsIgnoreCase(role)||"AIO".equalsIgnoreCase(role))return "assistant";
        return null;
    }

    private static String required(String value,int max,String code){
        if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException(code);
        return value;
    }

    private static String optional(String value,int max){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException("CHATGPT_HISTORY_ID_INVALID");
        return value;
    }
}
