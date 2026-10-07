package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AioLocalReasoningRouterTest {
    static final class CountingSource implements AioLocalReasoningRouter.ProjectionSource{
        int resources,readiness,capabilities,evidence,history,nativeState;
        public String resources(){resources++;return "RESOURCE";}
        public String readiness(){readiness++;return "READY";}
        public String capabilities(){capabilities++;return "CAPS";}
        public String evidence(){evidence++;return "EVIDENCE";}
        public String history(){history++;return "HISTORY";}
        public String nativeState(){nativeState++;return "NATIVE";}
        int total(){return resources+readiness+capabilities+evidence+history+nativeState;}
    }

    @Test public void resourceQuestionEvaluatesOnlyResourceProjection()throws Exception{
        CountingSource source=new CountingSource();
        AioLocalReasoningRouter.Decision d=AioLocalReasoningRouter.route("Battery status?",source);
        assertTrue(d.handledLocally);assertEquals(AioLocalReasoningRouter.Route.RESOURCE,d.route);
        assertEquals("RESOURCE",d.response);assertEquals(1,source.resources);assertEquals(1,source.total());
    }

    @Test public void readinessQuestionAvoidsUnrelatedMaterialization()throws Exception{
        CountingSource source=new CountingSource();
        AioLocalReasoningRouter.Decision d=AioLocalReasoningRouter.route("Are you ready?",source);
        assertEquals(AioLocalReasoningRouter.Route.READINESS,d.route);
        assertEquals(1,source.readiness);assertEquals(1,source.total());
    }

    @Test public void nativeStateQuestionDoesNotMaterializeHistory()throws Exception{
        CountingSource source=new CountingSource();
        AioLocalReasoningRouter.Decision d=AioLocalReasoningRouter.route("AIO native state",source);
        assertEquals(AioLocalReasoningRouter.Route.NATIVE_STATE,d.route);
        assertEquals(1,source.nativeState);assertEquals(0,source.history);assertEquals(1,source.total());
    }

    @Test public void unknownRequestPreservesRemoteRouteWithoutLocalWork()throws Exception{
        CountingSource source=new CountingSource();
        AioLocalReasoningRouter.Decision d=AioLocalReasoningRouter.route("Develop the Windows objective graph",source);
        assertFalse(d.handledLocally);assertEquals(AioLocalReasoningRouter.Route.REMOTE,d.route);
        assertNull(d.response);assertEquals(0,source.total());
    }

    @Test public void punctuationAndCaseDoNotBreakExactLocalIntent(){
        assertEquals(AioLocalReasoningRouter.Route.CAPABILITIES,
            AioLocalReasoningRouter.classify("android capabilities"));
        assertEquals(AioLocalReasoningRouter.Route.REMOTE,
            AioLocalReasoningRouter.classify("capabilities of the windows application"));
    }
}
