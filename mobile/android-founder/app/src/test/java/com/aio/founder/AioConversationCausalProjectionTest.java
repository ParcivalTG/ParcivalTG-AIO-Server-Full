package com.aio.founder;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AioConversationCausalProjectionTest {
    @Test public void selectivelyManifestsRelevantOldClusterAndRecentLightCone(){
        ArrayList<AioConversationCausalProjection.Turn> rows=new ArrayList<>();
        for(int i=0;i<20000;i++){
            String text="background unrelated routine checkpoint "+i+" storage scheduler theme";
            rows.add(new AioConversationCausalProjection.Turn(
                "AIO",text,"noise-"+i,"",i));
        }
        rows.set(3210,new AioConversationCausalProjection.Turn(
            "Founder","We need the R5 tunnel pairing bridge to connect Android to Windows.",
            "intent-r5-bootstrap","request-r5",3210));
        rows.set(3211,new AioConversationCausalProjection.Turn(
            "AIO","The Windows cloud tunnel uses the R5 rendezvous and Presence 47103.",
            "intent-r5-bootstrap","request-r5",3211));
        rows.set(3212,new AioConversationCausalProjection.Turn(
            "Evidence","R5 pairing bridge cloud-to-Presence physical court passed.",
            "intent-r5-bootstrap","request-r5",3212));

        AioConversationCausalProjection.Plan plan=AioConversationCausalProjection.project(
            rows,"Continue the R5 Android pairing tunnel bridge work",12_000);

        assertEquals(20000,plan.totalRows);
        assertTrue(plan.manifestedRows<80);
        assertTrue(plan.materializationFraction()<0.004);
        assertTrue(plan.turns.stream().anyMatch(x->x.timestamp==3210));
        assertTrue(plan.turns.stream().anyMatch(x->x.timestamp==3211));
        assertTrue(plan.turns.stream().anyMatch(x->x.timestamp==3212));
        for(int i=1;i<plan.turns.size();i++)
            assertTrue(plan.turns.get(i-1).timestamp<=plan.turns.get(i).timestamp);
        assertTrue(plan.manifestedChars<=12_000);
    }

    @Test public void dependencyClosureKeepsSameIntentTogetherWhenBudgetAllows(){
        List<AioConversationCausalProjection.Turn> rows=List.of(
            new AioConversationCausalProjection.Turn("Founder","alpha target","intent-x","req-x",1),
            new AioConversationCausalProjection.Turn("AIO","important consequence","intent-x","req-x",2),
            new AioConversationCausalProjection.Turn("AIO","unrelated","other","",3),
            new AioConversationCausalProjection.Turn("Founder","latest request","latest","",4)
        );
        AioConversationCausalProjection.Plan plan=AioConversationCausalProjection.project(
            rows,"alpha target",4096);
        assertTrue(plan.turns.stream().anyMatch(x->x.timestamp==1));
        assertTrue(plan.turns.stream().anyMatch(x->x.timestamp==2));
    }

    @Test public void tinyBudgetFailsInsteadOfSilentlyTruncatingRecentTurn(){
        List<AioConversationCausalProjection.Turn> rows=List.of(
            new AioConversationCausalProjection.Turn("Founder","This final turn must stay exact.","i","r",1));
        try{AioConversationCausalProjection.project(rows,"final turn",8);fail();}
        catch(IllegalArgumentException expected){assertEquals("AIO_CONTEXT_BUDGET_TOO_SMALL",expected.getMessage());}
    }
}
