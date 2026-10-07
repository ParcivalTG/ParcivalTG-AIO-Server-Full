package com.aio.founder;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class CloudInboundRouterTest {
    @Test public void correlatedResponseStaysInRequesterPath(){
        UUID id=UUID.randomUUID();
        PresenceProtocol.Frame frame=new PresenceProtocol.Frame(PresenceProtocol.E2E_REPLY,(byte)0,id,new byte[0]);
        assertEquals(CloudInboundRouter.Route.CORRELATED_RESPONSE,CloudInboundRouter.route(id,frame));
    }
    @Test public void typedAndroidRequestUsesNodeInbox(){
        PresenceProtocol.Frame frame=new PresenceProtocol.Frame(
            PresenceProtocol.ANDROID_CAPABILITY_REQUEST,(byte)0,UUID.randomUUID(),new byte[0]);
        assertEquals(CloudInboundRouter.Route.ANDROID_NODE_REQUEST,CloudInboundRouter.route(null,frame));
    }
    @Test public void unmatchedNormalReplyIsRejected(){
        PresenceProtocol.Frame frame=new PresenceProtocol.Frame(
            PresenceProtocol.STATUS_REPLY,(byte)0,UUID.randomUUID(),new byte[0]);
        assertEquals(CloudInboundRouter.Route.REJECT,CloudInboundRouter.route(UUID.randomUUID(),frame));
    }
    @Test public void capabilityReplyCannotMasqueradeAsRequest(){
        PresenceProtocol.Frame frame=new PresenceProtocol.Frame(
            PresenceProtocol.ANDROID_CAPABILITY_REPLY,(byte)0,UUID.randomUUID(),new byte[0]);
        assertEquals(CloudInboundRouter.Route.REJECT,CloudInboundRouter.route(null,frame));
    }
}
