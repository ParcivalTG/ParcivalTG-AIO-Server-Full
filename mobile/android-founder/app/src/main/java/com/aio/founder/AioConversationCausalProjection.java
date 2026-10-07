package com.aio.founder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AIO-native bounded context projection.
 * It preserves a small recent causal light cone, then selectively manifests older
 * turns whose semantic anchors or explicit intent/request dependencies intersect
 * the current query. Platform/provider context windows are compatibility projections,
 * never the authoritative conversation store.
 */
final class AioConversationCausalProjection {
    private static final int RECENT_LIGHT_CONE=8;
    private static final int MAX_HISTORY_ROWS=100_000;
    private static final int MAX_QUERY_CHARS=16_384;
    private static final Pattern WORD=Pattern.compile("[a-z0-9_:-]{3,}");
    private static final Set<String> STOP=Set.of(
        "the","and","for","that","with","this","from","into","have","what","when","then","they",
        "you","your","our","are","was","were","will","would","could","should","continue","work",
        "working","latest","request","please","about","there","where","which","while","been");

    static final class Turn {
        final String role,text,intentId,requestId;
        final long timestamp;
        Turn(String role,String text,String intentId,String requestId,long timestamp){
            this.role=bounded(role,96,"AIO_CONTEXT_ROLE_INVALID");
            this.text=bounded(text,256*1024,"AIO_CONTEXT_TEXT_INVALID");
            this.intentId=optional(intentId,512);
            this.requestId=optional(requestId,512);
            if(timestamp<0)throw new IllegalArgumentException("AIO_CONTEXT_TIMESTAMP_INVALID");
            this.timestamp=timestamp;
        }
        int cost(){
            return role.length()+text.length()+intentId.length()+requestId.length()+48;
        }
    }

    static final class Plan {
        final List<Turn> turns;
        final int totalRows,manifestedRows,totalChars,manifestedChars,anchorHits;
        Plan(List<Turn> turns,int totalRows,int totalChars,int manifestedChars,int anchorHits){
            this.turns=List.copyOf(turns);this.totalRows=totalRows;this.manifestedRows=turns.size();
            this.totalChars=totalChars;this.manifestedChars=manifestedChars;this.anchorHits=anchorHits;
        }
        double materializationFraction(){
            return totalRows==0?0.0:(double)manifestedRows/(double)totalRows;
        }
        double characterMaterializationFraction(){
            return totalChars==0?0.0:(double)manifestedChars/(double)totalChars;
        }
        String summary(){
            return "AIO CAUSAL CONTEXT"+
                "\nRows manifested="+manifestedRows+"/"+totalRows+
                " ("+String.format(Locale.ROOT,"%.4f%%",materializationFraction()*100.0)+")"+
                "\nChars manifested="+manifestedChars+"/"+totalChars+
                " ("+String.format(Locale.ROOT,"%.4f%%",characterMaterializationFraction()*100.0)+")"+
                "\nAnchor hits="+anchorHits;
        }
    }

    private static final class Candidate {
        final int index,score,anchorHits;
        Candidate(int index,int score,int anchorHits){this.index=index;this.score=score;this.anchorHits=anchorHits;}
    }

    private AioConversationCausalProjection(){}

    static Plan project(List<Turn> history,String query,int charBudget){
        if(history==null||history.size()>MAX_HISTORY_ROWS)
            throw new IllegalArgumentException("AIO_CONTEXT_HISTORY_BOUNDS");
        if(query==null||query.isBlank()||query.length()>MAX_QUERY_CHARS)
            throw new IllegalArgumentException("AIO_CONTEXT_QUERY_INVALID");
        if(charBudget<64)throw new IllegalArgumentException("AIO_CONTEXT_BUDGET_TOO_SMALL");
        if(history.isEmpty())return new Plan(List.of(),0,0,0,0);

        int totalChars=0;
        for(Turn turn:history){
            if(turn==null)throw new IllegalArgumentException("AIO_CONTEXT_TURN_REQUIRED");
            totalChars=Math.addExact(totalChars,turn.cost());
        }

        LinkedHashSet<Integer> selected=new LinkedHashSet<>();
        int used=0;
        int recentStart=Math.max(0,history.size()-RECENT_LIGHT_CONE);
        for(int i=recentStart;i<history.size();i++){
            Turn turn=history.get(i);
            if(used+turn.cost()>charBudget)
                throw new IllegalArgumentException("AIO_CONTEXT_BUDGET_TOO_SMALL");
            selected.add(i);used+=turn.cost();
        }

        Set<String> anchors=tokens(query);
        Set<String> queryIds=explicitIds(query);
        ArrayList<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<recentStart;i++){
            Turn turn=history.get(i);
            Set<String> rowTokens=tokens(turn.text+" "+turn.role);
            int hits=0;
            for(String anchor:anchors)if(rowTokens.contains(anchor))hits++;
            boolean idHit=(!turn.intentId.isEmpty()&&queryIds.contains(turn.intentId.toLowerCase(Locale.ROOT)))||
                (!turn.requestId.isEmpty()&&queryIds.contains(turn.requestId.toLowerCase(Locale.ROOT)));
            if(hits==0&&!idHit)continue;
            int recency=Math.min(99,(int)((100L*i)/Math.max(1,recentStart)));
            int score=hits*1000+(idHit?10_000:0)+recency;
            candidates.add(new Candidate(i,score,hits));
        }
        candidates.sort(Comparator
            .comparingInt((Candidate x)->x.score).reversed()
            .thenComparingInt(x->x.index).reversed());

        int anchorHits=0;
        for(Candidate candidate:candidates){
            if(selected.contains(candidate.index))continue;
            Turn turn=history.get(candidate.index);
            if(used+turn.cost()>charBudget)continue;
            selected.add(candidate.index);used+=turn.cost();anchorHits+=candidate.anchorHits;
            used=closeDependencies(history,selected,turn.intentId,turn.requestId,charBudget,used);
        }

        ArrayList<Turn> manifested=new ArrayList<>(selected.size());
        selected.stream().sorted().forEach(index->manifested.add(history.get(index)));
        return new Plan(manifested,history.size(),totalChars,used,anchorHits);
    }

    private static int closeDependencies(List<Turn> history,LinkedHashSet<Integer> selected,
                                         String intentId,String requestId,int budget,int used){
        if(intentId.isEmpty()&&requestId.isEmpty())return used;
        for(int i=0;i<history.size();i++){
            if(selected.contains(i))continue;
            Turn row=history.get(i);
            boolean sameIntent=!intentId.isEmpty()&&intentId.equals(row.intentId);
            boolean sameRequest=!requestId.isEmpty()&&requestId.equals(row.requestId);
            if(!sameIntent&&!sameRequest)continue;
            if(used+row.cost()>budget)continue;
            selected.add(i);used+=row.cost();
        }
        return used;
    }

    private static Set<String> tokens(String text){
        HashSet<String> out=new HashSet<>();
        Matcher matcher=WORD.matcher(text.toLowerCase(Locale.ROOT));
        while(matcher.find()){
            String word=matcher.group();
            if(!STOP.contains(word))out.add(word);
        }
        return out;
    }

    private static Set<String> explicitIds(String text){
        HashSet<String> out=new HashSet<>();
        Matcher matcher=WORD.matcher(text.toLowerCase(Locale.ROOT));
        while(matcher.find()){
            String word=matcher.group();
            if(word.contains("-")||word.contains(":")||word.startsWith("req")||word.startsWith("intent"))
                out.add(word);
        }
        return out;
    }

    private static String bounded(String value,int max,String code){
        if(value==null)throw new IllegalArgumentException(code);
        String clean=value.trim();
        if(clean.isEmpty()||clean.length()>max)throw new IllegalArgumentException(code);
        return clean;
    }

    private static String optional(String value,int max){
        if(value==null)return "";
        String clean=value.trim();
        if(clean.length()>max)throw new IllegalArgumentException("AIO_CONTEXT_ID_INVALID");
        return clean;
    }
}
