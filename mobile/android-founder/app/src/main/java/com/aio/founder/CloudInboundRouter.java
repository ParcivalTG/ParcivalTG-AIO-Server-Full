package com.aio.founder;

import java.util.UUID;

final class CloudInboundRouter {
    enum Route { CORRELATED_RESPONSE, ANDROID_NODE_REQUEST, REJECT }

    private CloudInboundRouter(){}

    static Route route(UUID pendingRequestId,PresenceProtocol.Frame frame){
        if(frame==null)return Route.REJECT;
        if(pendingRequestId!=null&&pendingRequestId.equals(frame.id))return Route.CORRELATED_RESPONSE;
        if(frame.type==PresenceProtocol.ANDROID_CAPABILITY_REQUEST)return Route.ANDROID_NODE_REQUEST;
        return Route.REJECT;
    }
}
