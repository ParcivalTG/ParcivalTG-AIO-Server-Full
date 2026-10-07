package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatGptReasoningRecordTest {
    @Test public void roundTripsOpaqueCompletedReasoningItem()throws Exception{
        String item="{\"type\":\"reasoning\",\"id\":\"rs_1\",\"encrypted_content\":\"opaque\",\"summary\":[]}";
        String encoded=ChatGptReasoningRecord.encode("intent-1",1700000000000L,item);
        ChatGptReasoningRecord row=ChatGptReasoningRecord.decode(encoded);
        assertEquals("intent-1",row.intentId);
        assertEquals(1700000000000L,row.timestampUnixMs);
        assertTrue(row.itemJson.contains("\"encrypted_content\":\"opaque\""));
        ChatGptResponsesContract.InputItem replay=row.asInput();
        assertNotNull(replay);
    }

    @Test public void rejectsMessageMasqueradingAsReasoning()throws Exception{
        try{ChatGptReasoningRecord.encode("intent-1",1L,"{\"type\":\"message\",\"content\":[]}");fail();}
        catch(IllegalArgumentException expected){assertEquals("CHATGPT_REASONING_ITEM_INVALID",expected.getMessage());}
    }
}
