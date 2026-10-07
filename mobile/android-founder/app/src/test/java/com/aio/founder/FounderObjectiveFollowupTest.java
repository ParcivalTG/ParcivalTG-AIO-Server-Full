package com.aio.founder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class FounderObjectiveFollowupTest {
    private static String statusReceipt(String state,int progress,boolean terminal)throws Exception{
        JSONObject result=new JSONObject();
        result.put("schema",FounderObjectiveFollowup.STATUS_SCHEMA);
        result.put("objectiveId","OBJ-BOOTSTRAP-1");
        result.put("state",state);
        result.put("version",7);
        result.put("progress",progress);
        result.put("currentWorkstream","Android remote development");
        result.put("currentTask","Run cloud court");
        result.put("currentBlocker","");
        result.put("lastCheckpoint","Gateway accepted objective");
        result.put("nextAction",terminal?"None":"Continue verification");
        result.put("terminal",terminal);

        JSONObject receipt=new JSONObject();
        receipt.put("schema",FounderObjectiveFollowup.GATEWAY_RECEIPT_SCHEMA);
        receipt.put("requestId","REQ-STATUS-1");
        receipt.put("action",FounderIntentCapsule.STATUS_ACTION);
        receipt.put("success",true);
        receipt.put("result",result);
        return receipt.toString();
    }

    @Test public void actionIdentifiersRemainStable(){
        assertEquals("windows.objective.submit",FounderIntentCapsule.ACTION);
        assertEquals("founder.intent.status",FounderIntentCapsule.STATUS_ACTION);
        assertEquals("founder.result.read",FounderIntentCapsule.RESULT_ACTION);
    }

    @Test public void runningStatusIsCorrelatedAndBounded()throws Exception{
        FounderObjectiveFollowup.Status status=FounderObjectiveFollowup.parseStatusReceipt(
            statusReceipt("RUNNING",42,false),"REQ-STATUS-1","OBJ-BOOTSTRAP-1");
        assertEquals("OBJ-BOOTSTRAP-1",status.objectiveId);
        assertEquals("RUNNING",status.state);
        assertEquals(7,status.version);
        assertEquals(42,status.progress);
        assertFalse(status.terminal);
        assertEquals("Run cloud court",status.currentTask);
        assertEquals("Continue verification",status.nextAction);
    }

    @Test public void terminalStateMustAgreeWithTerminalFlag()throws Exception{
        try{
            FounderObjectiveFollowup.parseStatusReceipt(
                statusReceipt("PASS",100,false),"REQ-STATUS-1","OBJ-BOOTSTRAP-1");
            fail();
        }catch(SecurityException expected){
            assertEquals("FOUNDER_STATUS_TERMINAL_MISMATCH",expected.getMessage());
        }
    }

    @Test public void statusRejectsWrongRequestOrObjective()throws Exception{
        try{
            FounderObjectiveFollowup.parseStatusReceipt(
                statusReceipt("RUNNING",50,false),"OTHER","OBJ-BOOTSTRAP-1");
            fail();
        }catch(SecurityException expected){
            assertEquals("FOUNDER_STATUS_CORRELATION_INVALID",expected.getMessage());
        }
        try{
            FounderObjectiveFollowup.parseStatusReceipt(
                statusReceipt("RUNNING",50,false),"REQ-STATUS-1","OBJ-OTHER");
            fail();
        }catch(SecurityException expected){
            assertEquals("FOUNDER_STATUS_OBJECTIVE_MISMATCH",expected.getMessage());
        }
    }

    @Test public void statusRejectsUnknownStateAndProgressOverflow()throws Exception{
        try{
            FounderObjectiveFollowup.parseStatusReceipt(
                statusReceipt("MAGIC",50,false),"REQ-STATUS-1","OBJ-BOOTSTRAP-1");
            fail();
        }catch(SecurityException expected){
            assertEquals("FOUNDER_STATUS_STATE_INVALID",expected.getMessage());
        }
        try{
            FounderObjectiveFollowup.parseStatusReceipt(
                statusReceipt("RUNNING",101,false),"REQ-STATUS-1","OBJ-BOOTSTRAP-1");
            fail();
        }catch(SecurityException expected){
            assertEquals("FOUNDER_STATUS_PROGRESS_INVALID",expected.getMessage());
        }
    }

    @Test public void resultReceiptRequiresTerminalStateAndBoundsEvidence()throws Exception{
        JSONObject result=new JSONObject();
        result.put("schema",FounderObjectiveFollowup.RESULT_SCHEMA);
        result.put("objectiveId","OBJ-BOOTSTRAP-1");
        result.put("state","PASS");
        result.put("summary","Build and tests completed");
        result.put("evidenceRefs",new JSONArray().put("git:abc123").put("court:android"));
        result.put("artifactRefs",new JSONArray().put("github:ParcivalTG/AIO@abc123"));

        JSONObject receipt=new JSONObject();
        receipt.put("schema",FounderObjectiveFollowup.GATEWAY_RECEIPT_SCHEMA);
        receipt.put("requestId","REQ-RESULT-1");
        receipt.put("action",FounderIntentCapsule.RESULT_ACTION);
        receipt.put("success",true);
        receipt.put("result",result);

        FounderObjectiveFollowup.Result parsed=FounderObjectiveFollowup.parseResultReceipt(
            receipt.toString(),"REQ-RESULT-1","OBJ-BOOTSTRAP-1");
        assertEquals("PASS",parsed.state);
        assertEquals("Build and tests completed",parsed.summary);
        assertEquals(2,parsed.evidenceRefs.length);
        assertEquals(1,parsed.artifactRefs.length);

        result.put("state","RUNNING");
        receipt.put("result",result);
        try{
            FounderObjectiveFollowup.parseResultReceipt(
                receipt.toString(),"REQ-RESULT-1","OBJ-BOOTSTRAP-1");
            fail();
        }catch(SecurityException expected){
            assertEquals("FOUNDER_RESULT_NOT_TERMINAL",expected.getMessage());
        }
    }
}
