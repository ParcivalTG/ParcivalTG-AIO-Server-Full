package com.aio.founder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class FounderObjectiveFollowup {
    static final String GATEWAY_RECEIPT_SCHEMA="aio.private-gateway.receipt.v1";
    static final String STATUS_SCHEMA="aio.founder.intent-status.v1";
    static final String RESULT_SCHEMA="aio.founder.result.v1";
    private static final int MAX_RECEIPT_CHARS=262_144;
    private static final int MAX_TEXT_CHARS=16_384;
    private static final int MAX_REFS=64;
    private static final int MAX_REF_CHARS=1_024;
    private static final Set<String> STATES=new HashSet<>(Arrays.asList(
        "RECEIVED","ACCEPTED","RUNNING","PASS","PARTIAL","HOLD","FAILED","CANCELLED"));
    private static final Set<String> TERMINAL=new HashSet<>(Arrays.asList(
        "PASS","PARTIAL","HOLD","FAILED","CANCELLED"));

    static final class Status {
        final String objectiveId,state,currentWorkstream,currentTask,currentBlocker,lastCheckpoint,nextAction;
        final int version,progress;
        final boolean terminal;
        Status(String objectiveId,String state,int version,int progress,
               String currentWorkstream,String currentTask,String currentBlocker,
               String lastCheckpoint,String nextAction,boolean terminal){
            this.objectiveId=objectiveId;this.state=state;this.version=version;this.progress=progress;
            this.currentWorkstream=currentWorkstream;this.currentTask=currentTask;
            this.currentBlocker=currentBlocker;this.lastCheckpoint=lastCheckpoint;
            this.nextAction=nextAction;this.terminal=terminal;
        }
    }

    static final class Result {
        final String objectiveId,state,summary;
        final String[] evidenceRefs,artifactRefs;
        Result(String objectiveId,String state,String summary,String[] evidenceRefs,String[] artifactRefs){
            this.objectiveId=objectiveId;this.state=state;this.summary=summary;
            this.evidenceRefs=evidenceRefs;this.artifactRefs=artifactRefs;
        }
    }

    private FounderObjectiveFollowup(){}

    static Status parseStatusReceipt(String receiptText,String expectedRequestId,String expectedObjectiveId)throws Exception{
        JSONObject result=parseReceipt(receiptText,expectedRequestId,FounderIntentCapsule.STATUS_ACTION);
        if(!STATUS_SCHEMA.equals(result.optString("schema")))throw new SecurityException("FOUNDER_STATUS_SCHEMA_INVALID");
        String objectiveId=requireObjective(result,expectedObjectiveId,"FOUNDER_STATUS_OBJECTIVE_MISMATCH");
        String state=result.optString("state","");
        if(!STATES.contains(state))throw new SecurityException("FOUNDER_STATUS_STATE_INVALID");
        int version=result.optInt("version",-1);
        if(version<1)throw new SecurityException("FOUNDER_STATUS_VERSION_INVALID");
        int progress=result.optInt("progress",-1);
        if(progress<0||progress>100)throw new SecurityException("FOUNDER_STATUS_PROGRESS_INVALID");
        boolean terminal=result.optBoolean("terminal",false);
        if(terminal!=TERMINAL.contains(state))throw new SecurityException("FOUNDER_STATUS_TERMINAL_MISMATCH");
        return new Status(objectiveId,state,version,progress,
            bounded(result.optString("currentWorkstream",""),"FOUNDER_STATUS_TEXT_INVALID"),
            bounded(result.optString("currentTask",""),"FOUNDER_STATUS_TEXT_INVALID"),
            bounded(result.optString("currentBlocker",""),"FOUNDER_STATUS_TEXT_INVALID"),
            bounded(result.optString("lastCheckpoint",""),"FOUNDER_STATUS_TEXT_INVALID"),
            bounded(result.optString("nextAction",""),"FOUNDER_STATUS_TEXT_INVALID"),
            terminal);
    }

    static Result parseResultReceipt(String receiptText,String expectedRequestId,String expectedObjectiveId)throws Exception{
        JSONObject result=parseReceipt(receiptText,expectedRequestId,FounderIntentCapsule.RESULT_ACTION);
        if(!RESULT_SCHEMA.equals(result.optString("schema")))throw new SecurityException("FOUNDER_RESULT_SCHEMA_INVALID");
        String objectiveId=requireObjective(result,expectedObjectiveId,"FOUNDER_RESULT_OBJECTIVE_MISMATCH");
        String state=result.optString("state","");
        if(!TERMINAL.contains(state))throw new SecurityException("FOUNDER_RESULT_NOT_TERMINAL");
        String summary=bounded(result.optString("summary",""),"FOUNDER_RESULT_SUMMARY_INVALID");
        String[] evidence=parseRefs(result.optJSONArray("evidenceRefs"),"FOUNDER_RESULT_EVIDENCE_INVALID");
        String[] artifacts=parseRefs(result.optJSONArray("artifactRefs"),"FOUNDER_RESULT_ARTIFACT_INVALID");
        return new Result(objectiveId,state,summary,evidence,artifacts);
    }

    private static JSONObject parseReceipt(String text,String expectedRequestId,String expectedAction)throws Exception{
        if(text==null||text.isEmpty()||text.length()>MAX_RECEIPT_CHARS)
            throw new SecurityException("FOUNDER_FOLLOWUP_RECEIPT_BOUNDS");
        JSONObject receipt=new JSONObject(text);
        if(!GATEWAY_RECEIPT_SCHEMA.equals(receipt.optString("schema")))
            throw new SecurityException("FOUNDER_FOLLOWUP_RECEIPT_SCHEMA");
        if(expectedRequestId==null||!expectedRequestId.equals(receipt.optString("requestId")))
            throw new SecurityException("FOUNDER_STATUS_CORRELATION_INVALID");
        if(!expectedAction.equals(receipt.optString("action")))
            throw new SecurityException("FOUNDER_FOLLOWUP_ACTION_MISMATCH");
        if(!receipt.optBoolean("success",false))
            throw new SecurityException("FOUNDER_FOLLOWUP_REJECTED");
        JSONObject result=receipt.optJSONObject("result");
        if(result==null)throw new SecurityException("FOUNDER_FOLLOWUP_RESULT_MISSING");
        return result;
    }

    private static String requireObjective(JSONObject value,String expected,String code){
        String objectiveId=value.optString("objectiveId","");
        if(expected==null||expected.isEmpty()||!expected.equals(objectiveId))throw new SecurityException(code);
        return objectiveId;
    }

    private static String bounded(String value,String code){
        if(value==null||value.length()>MAX_TEXT_CHARS)throw new SecurityException(code);
        return value;
    }

    private static String[] parseRefs(JSONArray array,String code){
        if(array==null)return new String[0];
        if(array.length()>MAX_REFS)throw new SecurityException(code);
        String[] out=new String[array.length()];
        for(int i=0;i<array.length();i++){
            String value=array.optString(i,null);
            if(value==null||value.isEmpty()||value.length()>MAX_REF_CHARS)throw new SecurityException(code);
            out[i]=value;
        }
        return out;
    }
}
