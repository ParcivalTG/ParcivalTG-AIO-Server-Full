package com.aio.founder;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AioFounderStateStore {
    static final int HOT_HISTORY_ROWS=64;
    static final int HOT_EVIDENCE_ROWS=128;
    private static final int MAX_SCAN_RECORDS=4096;

    private final SecretStore legacy;
    private final AioSelectiveJournal historyJournal;
    private final AioSelectiveJournal evidenceJournal;

    AioFounderStateStore(Context context){
        Context app=context.getApplicationContext();
        legacy=new SecretStore(app);
        File root=new File(app.getFilesDir(),"aio-native-state-v1");
        historyJournal=new AioSelectiveJournal(
            new File(root,"history.aioj"),new AioJournalCipher("history"));
        evidenceJournal=new AioSelectiveJournal(
            new File(root,"evidence.aioj"),new AioJournalCipher("evidence"));
    }

    synchronized void initialize()throws Exception{
        migrateLegacy("history",historyJournal);
        migrateLegacy("evidence",evidenceJournal);
    }

    synchronized void appendHistory(JSONObject row)throws Exception{
        requireObject(row,"AIO_HISTORY_ROW_REQUIRED");
        historyJournal.append(row.toString(),System.currentTimeMillis());
    }

    synchronized void appendEvidence(JSONObject row)throws Exception{
        requireObject(row,"AIO_EVIDENCE_ROW_REQUIRED");
        evidenceJournal.append(AioWitnessCellCodec.encode(row),System.currentTimeMillis());
    }

    synchronized JSONObject appendCapabilityAttempt(java.util.UUID requestId,String peerId,long sourceSessionEpoch,
                                                    String capability,String action)throws Exception{
        if(requestId==null)throw new IllegalArgumentException("AIO_CAPABILITY_AUDIT_REQUIRED");
        JSONObject row=new JSONObject();
        row.put("code","ANDROID_CAPABILITY_ATTEMPT");
        row.put("atUnixMs",System.currentTimeMillis());
        row.put("requestId",requestId.toString());
        row.put("peerId",peerId==null?"UNKNOWN":peerId);
        row.put("sourceSessionEpoch",sourceSessionEpoch);
        row.put("capability",capability==null?"UNKNOWN":capability);
        row.put("action",action==null?"UNKNOWN":action);
        appendEvidence(row);
        return row;
    }

    synchronized JSONObject appendCapabilityResult(java.util.UUID requestId,String peerId,long sourceSessionEpoch,
                                                   AndroidCapabilityDispatcher.Result result)throws Exception{
        if(requestId==null||result==null)throw new IllegalArgumentException("AIO_CAPABILITY_AUDIT_REQUIRED");
        JSONObject row=new JSONObject();
        row.put("code",result.accepted?"ANDROID_CAPABILITY_ALLOWED":"ANDROID_CAPABILITY_DENIED");
        row.put("atUnixMs",System.currentTimeMillis());
        row.put("requestId",requestId.toString());
        row.put("peerId",peerId==null?"UNKNOWN":peerId);
        row.put("sourceSessionEpoch",sourceSessionEpoch);
        row.put("capability",result.capability);
        row.put("action",result.action);
        row.put("resultCode",result.code);
        row.put("manifestedDependencies",result.manifestedDependencies);
        row.put("unmanifestedDependencies",result.unmanifestedDependencies());
        row.put("dependencyUniverse",result.totalDependencies);
        row.put("nonManifestationPermille",
            Math.round(result.nonManifestationFraction()*1000.0));
        appendEvidence(row);
        return row;
    }

    synchronized JSONArray historyTail(int logicalRows)throws Exception{
        if(logicalRows<0||logicalRows>HOT_HISTORY_ROWS)throw new IllegalArgumentException("AIO_HISTORY_TAIL_LIMIT");
        if(logicalRows==0)return new JSONArray();

        int scan=Math.min(MAX_SCAN_RECORDS,Math.max(32,logicalRows*8));
        AioSelectiveJournal.TailResult tail=historyJournal.tail(scan);
        LinkedHashMap<String,HistorySnapshot> latest=new LinkedHashMap<>();
        int ordinal=0;
        for(AioSelectiveJournal.Entry entry:tail.entries){
            JSONObject row=new JSONObject(entry.value);
            String key=historyKey(row,ordinal++);
            latest.put(key,new HistorySnapshot(row,entry.timestampUnixMs,ordinal));
        }

        List<HistorySnapshot> rows=new ArrayList<>(latest.values());
        rows.sort(Comparator
            .comparingLong((HistorySnapshot row)->row.logicalTimestamp)
            .thenComparingInt(row->row.ordinal));
        int start=Math.max(0,rows.size()-logicalRows);
        JSONArray out=new JSONArray();
        for(int i=start;i<rows.size();i++)out.put(rows.get(i).row);
        return out;
    }

    synchronized JSONArray evidenceTail(int limit)throws Exception{
        if(limit<0||limit>HOT_EVIDENCE_ROWS)throw new IllegalArgumentException("AIO_EVIDENCE_TAIL_LIMIT");
        AioSelectiveJournal.TailResult tail=evidenceJournal.tail(limit);
        JSONArray out=new JSONArray();
        for(AioSelectiveJournal.Entry entry:tail.entries)out.put(AioWitnessCellCodec.decode(entry.value));
        return out;
    }

    synchronized ChatGptHistoryProjection.Plan chatGptContext(String query,int charBudget)throws Exception{
        AioSelectiveJournal.TailResult tail=historyJournal.tail(MAX_SCAN_RECORDS);
        ArrayList<ChatGptHistoryProjection.Row> rows=new ArrayList<>();
        for(AioSelectiveJournal.Entry entry:tail.entries){
            JSONObject row=new JSONObject(entry.value);
            String role=row.optString("role","");
            String text=row.optString("text","");
            String privacy=row.optString("privacyClass","");
            if(role.isEmpty()||text.isEmpty()||privacy.isEmpty())continue;
            try{
                rows.add(new ChatGptHistoryProjection.Row(
                    role,text,privacy,row.optString("intentId",""),row.optString("requestId",""),
                    row.optLong("timestampUnixMs",entry.timestampUnixMs)));
            }catch(SecurityException minimizedWithoutProjection){
                if(!"CHATGPT_MINIMIZED_HISTORY_REQUIRES_PROJECTION".equals(minimizedWithoutProjection.getMessage()))
                    throw minimizedWithoutProjection;
            }
        }
        return ChatGptHistoryProjection.project(rows,query,charBudget);
    }

    synchronized String summary()throws Exception{
        AioSelectiveJournal.TailResult historyTail=historyJournal.tail(Math.min(16,HOT_HISTORY_ROWS));
        AioSelectiveJournal.TailResult evidenceTail=evidenceJournal.tail(Math.min(16,HOT_EVIDENCE_ROWS));
        return "AIO NATIVE SELECTIVE STATE"+
            "\nHistory records="+historyJournal.countRecords()+
            " | tailBytes="+historyTail.bytesRead+"/"+historyTail.fileBytes+
            " | materialized="+percent(historyTail.materializationFraction())+
            "\nEvidence records="+evidenceJournal.countRecords()+
            " | tailBytes="+evidenceTail.bytesRead+"/"+evidenceTail.fileBytes+
            " | materialized="+percent(evidenceTail.materializationFraction())+
            "\nHistory representation="+lastRepresentation(historyTail)+
            "\nEvidence representation="+lastRepresentation(evidenceTail);
    }

    synchronized void clearAll(){
        historyJournal.clear();evidenceJournal.clear();
        legacy.removeText("history");legacy.removeText("evidence");
    }

    private void migrateLegacy(String name,AioSelectiveJournal journal)throws Exception{
        String stored=legacy.readText(name);
        if(stored==null)return;
        String raw=AioNativeCellStateCodec.decodeArray(stored);
        JSONArray rows=new JSONArray(raw);
        if(journal.countRecords()==0){
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null)continue;
                long timestamp=row.optLong(
                    "timestampUnixMs",
                    row.optLong("atUnixMs",System.currentTimeMillis()+i));
                if("evidence".equals(name))
                    journal.append(AioWitnessCellCodec.encode(row),Math.max(1,timestamp));
                else
                    journal.append(row.toString(),Math.max(1,timestamp));
            }
        }
        if(journal.countRecords()>=rows.length())legacy.removeText(name);
    }

    private static void requireObject(JSONObject row,String code){
        if(row==null)throw new IllegalArgumentException(code);
        String text=row.toString();
        if(text.length()>512*1024)throw new IllegalArgumentException("AIO_STATE_ROW_BUDGET");
    }

    private static String historyKey(JSONObject row,int ordinal){
        String request=row.optString("requestId","");
        if(!request.isEmpty())return "request:"+request;
        String intent=row.optString("intentId","");
        if(!intent.isEmpty())return "intent:"+intent+":"+row.optString("role","");
        return "row:"+row.optLong("timestampUnixMs",0)+":"+ordinal;
    }

    private static String lastRepresentation(AioSelectiveJournal.TailResult result){
        if(result.entries.isEmpty())return "EMPTY";
        AioSelectiveJournal.Entry entry=result.entries.get(result.entries.size()-1);
        if(AioWitnessCellCodec.isCell(entry.value))
            return "WITNESS_CELL | storedChars="+entry.value.length()+" | "+entry.representation;
        return entry.representation;
    }

    private static String percent(double fraction){
        return String.format(java.util.Locale.ROOT,"%.4f%%",fraction*100.0);
    }

    private static final class HistorySnapshot{
        final JSONObject row;final long journalTimestamp,logicalTimestamp;final int ordinal;
        HistorySnapshot(JSONObject row,long journalTimestamp,int ordinal){
            this.row=row;this.journalTimestamp=journalTimestamp;this.ordinal=ordinal;
            this.logicalTimestamp=row.optLong("timestampUnixMs",journalTimestamp);
        }
    }
}
