package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioVoiceRoomTest {
    @Test public void localPrivacyNeverNeedsExternalProvider(){
        AioVoiceRoom r=new AioVoiceRoom();
        assertEquals(AioVoiceRoom.ExternalStatus.BLOCKED_PRIVACY,
            r.externalStatus(AioVoiceRoom.Privacy.LOCAL_ONLY));
        assertEquals(AioVoiceRoom.ExternalStatus.BLOCKED_PRIVACY,
            r.externalStatus(AioVoiceRoom.Privacy.TRADE_SECRET_LOCAL_ONLY));
    }
    @Test(expected=SecurityException.class) public void externalTurnFailsClosedWithoutGrant(){
        AioVoiceRoom r=new AioVoiceRoom();
        r.commit("Founder","secret",AioVoiceRoom.Privacy.EXTERNAL_ALLOWED);
    }
    @Test public void founderBargeInTakesListeningState(){
        AioVoiceRoom r=new AioVoiceRoom();
        r.beginResponse();r.beginSpeech();assertEquals(AioVoiceRoom.State.SPEAKING,r.state());
        r.bargeIn();assertEquals(AioVoiceRoom.State.LISTENING,r.state());
    }
    @Test public void councilBudgetIsFinite(){
        AioVoiceRoom r=new AioVoiceRoom();
        for(int i=0;i<8;i++)r.teach("lesson-"+i);
        try{r.teach("overflow");fail("budget should be exhausted");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("BUDGET"));}
    }
}
