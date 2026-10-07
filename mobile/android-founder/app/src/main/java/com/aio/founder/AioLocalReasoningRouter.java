package com.aio.founder;

import java.util.Locale;

final class AioLocalReasoningRouter {
    interface ProjectionSource {
        String resources()throws Exception;
        String readiness()throws Exception;
        String capabilities()throws Exception;
        String evidence()throws Exception;
        String history()throws Exception;
        String nativeState()throws Exception;
    }

    enum Route { RESOURCE, READINESS, CAPABILITIES, EVIDENCE, HISTORY, NATIVE_STATE, REMOTE }

    static final class Decision {
        final Route route;
        final boolean handledLocally;
        final String response;
        Decision(Route route,boolean handledLocally,String response){
            this.route=route;this.handledLocally=handledLocally;this.response=response;
        }
    }

    private AioLocalReasoningRouter(){}

    static Decision route(String text,ProjectionSource source)throws Exception{
        if(source==null)throw new IllegalArgumentException("AIO_LOCAL_SOURCE_REQUIRED");
        String normalized=normalize(text);
        Route route=classify(normalized);
        switch(route){
            case RESOURCE:return local(route,source.resources());
            case READINESS:return local(route,source.readiness());
            case CAPABILITIES:return local(route,source.capabilities());
            case EVIDENCE:return local(route,source.evidence());
            case HISTORY:return local(route,source.history());
            case NATIVE_STATE:return local(route,source.nativeState());
            default:return new Decision(Route.REMOTE,false,null);
        }
    }

    static Route classify(String normalized){
        if(normalized==null||normalized.isEmpty())return Route.REMOTE;
        switch(normalized){
            case "android status":
            case "phone status":
            case "resource status":
            case "battery status":
            case "android resource status":
            case "phone resource status":
                return Route.RESOURCE;
            case "readiness":
            case "android readiness":
            case "phone readiness":
            case "are you ready":
            case "is android ready":
                return Route.READINESS;
            case "capabilities":
            case "android capabilities":
            case "phone capabilities":
            case "what can android do":
            case "what can the phone do":
                return Route.CAPABILITIES;
            case "recent evidence":
            case "local evidence":
            case "android evidence":
                return Route.EVIDENCE;
            case "recent history":
            case "local history":
            case "android history":
                return Route.HISTORY;
            case "native state":
            case "representation status":
            case "android native state":
            case "aio native state":
                return Route.NATIVE_STATE;
            default:return Route.REMOTE;
        }
    }

    static String capabilitySummary(){
        StringBuilder out=new StringBuilder("ANDROID CAPABILITIES");
        for(AioAndroidNode.Capability capability:AioAndroidNode.Capability.values()){
            AndroidCapabilityCatalog.Spec spec=AndroidCapabilityCatalog.spec(capability);
            out.append("\n").append(capability.name())
                .append(" | tier=").append(spec.minimumTier)
                .append(" | ").append(spec.remoteEnabled?"REMOTE_TYPED":"LOCAL_ONLY")
                .append(" | prerequisite=").append(spec.prerequisite);
            if(!spec.remoteActions.isEmpty())out.append(" | actions=").append(spec.remoteActions);
        }
        return out.toString();
    }

    private static Decision local(Route route,String response){
        return new Decision(route,true,response==null?"NOT_MEASURED":response);
    }

    private static String normalize(String value){
        if(value==null)return "";
        String clean=value.trim().toLowerCase(Locale.ROOT);
        while(clean.endsWith("?")||clean.endsWith(".")||clean.endsWith("!"))
            clean=clean.substring(0,clean.length()-1).trim();
        clean=clean.replaceAll("\\s+"," ");
        return clean;
    }
}
