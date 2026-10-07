package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioReflectionGateTest {
    private static final String A="a".repeat(64);
    private static final String B="b".repeat(64);

    @Test public void deniedActionDemandsReflection(){
        AioReflectionGate.Decision d=AioReflectionGate.evaluate(
            "file.read",false,"no-active-grant",A,B,1);
        assertTrue(d.recommended);
        assertEquals("ACTION_DENIED:no-active-grant",d.code);
    }

    @Test public void unchangedGestureDemandsReflection(){
        AioReflectionGate.Decision d=AioReflectionGate.evaluate(
            "gesture.tap",true,"OK",A,A,1);
        assertTrue(d.recommended);
        assertEquals("NO_VISUAL_PROGRESS",d.code);
    }

    @Test public void thirdIdenticalActionDemandsNewHypothesis(){
        AioReflectionGate.Decision d=AioReflectionGate.evaluate(
            "notification.post",true,"OK",A,B,3);
        assertTrue(d.recommended);
        assertEquals("REPEATED_ACTION_NO_NEW_HYPOTHESIS",d.code);
    }

    @Test public void evidenceOfProgressAvoidsExtraReflection(){
        AioReflectionGate.Decision d=AioReflectionGate.evaluate(
            "gesture.swipe",true,"OK",A,B,1);
        assertFalse(d.recommended);
        assertEquals("PROGRESS_NOMINAL",d.code);
    }
}
