package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionEpochTest {
    @Test public void newestEpochIsTheOnlyAcceptedEpoch(){
        ConnectionEpoch epoch=new ConnectionEpoch();
        long first=epoch.invalidate();
        assertTrue(epoch.accepts(first));
        long second=epoch.invalidate();
        assertFalse(epoch.accepts(first));
        assertTrue(epoch.accepts(second));
    }

    @Test public void repeatedInvalidationNeverRevalidatesOldEpoch(){
        ConnectionEpoch epoch=new ConnectionEpoch();
        long old=epoch.invalidate();
        for(int i=0;i<100;i++)epoch.invalidate();
        assertFalse(epoch.accepts(old));
    }
}
