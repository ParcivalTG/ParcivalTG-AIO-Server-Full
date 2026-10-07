package com.aio.founder;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ChatGptHistoryProjectionTest {
    @Test public void localHistoryNeverCrossesProviderBoundary()throws Exception{
        List<ChatGptHistoryProjection.Row> rows=List.of(
            new ChatGptHistoryProjection.Row("Founder","R5 secret local detail","LOCAL_ONLY","i1","r1",1),
            new ChatGptHistoryProjection.Row("AIO","R5 trade secret detail","TRADE_SECRET_LOCAL_ONLY","i2","r2",2),
            new ChatGptHistoryProjection.Row("Founder","R5 public-to-provider checkpoint","EXTERNAL_ALLOWED","i3","r3",3),
            new ChatGptHistoryProjection.Row("ChatGPT","Prior GPT response about R5","EXTERNAL_ALLOWED","i3","r3",4)
        );
        ChatGptHistoryProjection.Plan plan=ChatGptHistoryProjection.project(rows,"continue R5",4096);
        assertEquals(2,plan.input.size());
        String joined=String.join("\n",plan.debugText);
        assertFalse(joined.contains("secret local"));
        assertFalse(joined.contains("trade secret"));
        assertTrue(joined.contains("public-to-provider"));
        assertTrue(joined.contains("Prior GPT"));
    }

    @Test public void mapsFounderToUserAndProviderToAssistant()throws Exception{
        List<ChatGptHistoryProjection.Row> rows=List.of(
            new ChatGptHistoryProjection.Row("Founder","question alpha","EXTERNAL_ALLOWED","i","r",1),
            new ChatGptHistoryProjection.Row("ChatGPT","answer alpha","EXTERNAL_ALLOWED","i","r",2)
        );
        ChatGptHistoryProjection.Plan plan=ChatGptHistoryProjection.project(rows,"alpha",4096);
        assertEquals(2,plan.input.size());
        assertTrue(plan.input.get(0).json().contains("\"role\":\"user\""));
        assertTrue(plan.input.get(1).json().contains("\"role\":\"assistant\""));
    }

    @Test public void minimizedRowsRequireStoredExternalProjection(){
        try{
            new ChatGptHistoryProjection.Row(
                "Founder","raw secret","EXTERNAL_MINIMIZED","i","r",1);
            fail();
        }catch(SecurityException expected){
            assertEquals("CHATGPT_MINIMIZED_HISTORY_REQUIRES_PROJECTION",expected.getMessage());
        }
    }
}
