package com.aio.founder;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class AndroidCapabilityDispatcher {
    static final class Result {
        final boolean accepted;
        final byte[] payload;
        final String capability,action,code;
        final int manifestedDependencies,totalDependencies;

        Result(boolean accepted,byte[] payload){
            this(accepted,payload,"UNKNOWN","UNKNOWN",accepted?"OK":"DENIED",0,AioAndroidCausalPlanner.Dependency.values().length);
        }

        Result(boolean accepted,byte[] payload,String capability,String action,String code,
               int manifestedDependencies,int totalDependencies){
            this.accepted=accepted;this.payload=payload;this.capability=capability;this.action=action;this.code=code;
            this.manifestedDependencies=manifestedDependencies;this.totalDependencies=totalDependencies;
        }

        int unmanifestedDependencies(){return Math.max(0,totalDependencies-manifestedDependencies);}
        double nonManifestationFraction(){
            return totalDependencies<=0?0.0:(double)unmanifestedDependencies()/(double)totalDependencies;
        }
    }

    private static final int REMOTE_READ_MAX=256*1024;
    private static final int LIST_MAX=128;
    private final Context context;
    private final AioAndroidNode node;
    private final AndroidCapabilityBroker files;
    private final AndroidClipboardBroker clipboard;
    private final AndroidNotificationBroker notifications;

    AndroidCapabilityDispatcher(Context context,AioAndroidNode node,AndroidCapabilityBroker files){
        if(context==null||node==null||files==null)throw new IllegalArgumentException("ANDROID_DISPATCHER_DEPENDENCY");
        this.context=context.getApplicationContext();this.node=node;this.files=files;
        this.clipboard=new AndroidClipboardBroker(this.context);
        this.notifications=new AndroidNotificationBroker(this.context);
    }

    Result dispatch(String peerId,UUID requestId,byte[] payload){
        AndroidCapabilityProtocol.Request request=null;
        try{
            request=AndroidCapabilityProtocol.parse(payload);
        }catch(Exception failure){
            return denied(null,null,failure);
        }
        return dispatchProjected(peerId,requestId,request);
    }

    Result dispatchProjected(String peerId,UUID requestId,AndroidCapabilityProtocol.Request request){
        AioAndroidCausalPlanner.Prepared prepared=null;
        try{
            if(peerId==null||!peerId.matches("[A-Za-z0-9_.:-]{1,128}"))throw new SecurityException("ANDROID_PEER_INVALID");
            if(requestId==null)throw new SecurityException("ANDROID_REQUEST_ID_INVALID");
            if(request==null||request.capability==null||request.action==null||request.privacy==null||request.args==null)
                throw new SecurityException("ANDROID_PROJECTED_REQUEST_INVALID");
            if(!AndroidCapabilityCatalog.supports(request.capability,request.action))
                throw new SecurityException("ANDROID_ACTION_UNSUPPORTED");
            node.authorizePeer(peerId,request.capability,request.action,request.privacy);
            prepared=AioAndroidCausalPlanner.prepare(context,files,request.action);
            String result=execute(request,prepared);
            return new Result(true,AndroidCapabilityProtocol.reply(true,"OK",result),
                request.capability.name(),request.action,"OK",
                prepared.manifestedDependencies(),prepared.totalDependencyUniverse);
        }catch(Exception failure){
            return denied(request,prepared,failure);
        }
    }

    private Result denied(AndroidCapabilityProtocol.Request request,
                          AioAndroidCausalPlanner.Prepared prepared,Exception failure){
        String code=safeCode(failure);
        return new Result(false,AndroidCapabilityProtocol.reply(false,code,null),
            request==null?"UNPARSED":request.capability.name(),
            request==null?"UNPARSED":request.action,
            code,
            prepared==null?0:prepared.manifestedDependencies(),
            prepared==null?AioAndroidCausalPlanner.Dependency.values().length:prepared.totalDependencyUniverse);
    }

    private String execute(AndroidCapabilityProtocol.Request request,AioAndroidCausalPlanner.Prepared prepared)throws Exception{
        switch(request.action){
            case "resource.status":
                args(request.args,Set.of(),Set.of());
                return resourceJson(requireResources(prepared));
            case "resource.sha256":
                args(request.args,Set.of("contentB64"),Set.of("contentB64"));
                requireContributionEligible(requireResources(prepared));
                return "{\"sha256\":\""+AndroidResourceWorker.sha256(
                    AndroidCapabilityProtocol.string(request.args,"contentB64",8192))+"\"}";
            case "resource.deflate":
                args(request.args,Set.of("contentB64","level"),Set.of("contentB64"));
                requireContributionEligible(requireResources(prepared));
                int deflateLevel=(int)AndroidCapabilityProtocol.integer(request.args,"level",1,9,6);
                String compressed=AndroidResourceWorker.deflate(
                    AndroidCapabilityProtocol.string(request.args,"contentB64",8192),deflateLevel);
                return "{\"encoding\":\"zlib\",\"level\":"+deflateLevel+
                    ",\"contentB64\":\""+AndroidCapabilityProtocol.escape(compressed)+"\"}";
            case "screen.capture":
                args(request.args,
                    Set.of("quality","maxBytes","x1","y1","x2","y2","previousSha256"),Set.of());
                int quality=AndroidRemoteScreenPolicy.validateQuality(
                    AndroidCapabilityProtocol.integer(request.args,"quality",30,75,AndroidRemoteScreenPolicy.DEFAULT_QUALITY));
                int screenMaxBytes=AndroidRemoteScreenPolicy.validateMaxBytes(
                    AndroidCapabilityProtocol.integer(
                        request.args,"maxBytes",
                        AndroidRemoteScreenPolicy.MIN_JPEG_BYTES,
                        AndroidRemoteScreenPolicy.MAX_JPEG_BYTES,
                        AndroidRemoteScreenPolicy.DEFAULT_MAX_JPEG_BYTES));
                boolean anyRegion=request.args.containsKey("x1")||request.args.containsKey("y1")||
                    request.args.containsKey("x2")||request.args.containsKey("y2");
                boolean fullRegionKeys=request.args.containsKey("x1")&&request.args.containsKey("y1")&&
                    request.args.containsKey("x2")&&request.args.containsKey("y2");
                if(anyRegion&&!fullRegionKeys)throw new IllegalArgumentException("SCREEN_REGION_KEYS");
                AndroidRemoteScreenPolicy.Region region=fullRegionKeys?
                    AndroidRemoteScreenPolicy.region(
                        AndroidCapabilityProtocol.integer(request.args,"x1",0,999,0),
                        AndroidCapabilityProtocol.integer(request.args,"y1",0,999,0),
                        AndroidCapabilityProtocol.integer(request.args,"x2",1,1000,1000),
                        AndroidCapabilityProtocol.integer(request.args,"y2",1,1000,1000)):
                    AndroidRemoteScreenPolicy.region(0,0,1000,1000);
                String previousSha=AndroidCapabilityProtocol.optionalString(
                    request.args,"previousSha256",64,"").toLowerCase(java.util.Locale.ROOT);
                if(!previousSha.isEmpty()&&!previousSha.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("SCREEN_PREVIOUS_HASH_INVALID");
                AndroidScreenProjectionService.Snapshot screen=prepared.screen;
                if(screen==null)throw new SecurityException("SCREEN_CAPTURE_NOT_ACTIVE");
                byte[] jpeg=AndroidScreenProjectionRuntime.captureJpeg(quality,screenMaxBytes,region);
                if(jpeg==null)throw new IllegalStateException("SCREEN_FRAME_NOT_READY");
                try{
                    String sha=sha256(jpeg);
                    int left=AndroidRemoteScreenPolicy.pixelStart(region.x1,screen.width);
                    int top=AndroidRemoteScreenPolicy.pixelStart(region.y1,screen.height);
                    int right=AndroidRemoteScreenPolicy.pixelEndExclusive(region.x2,screen.width);
                    int bottom=AndroidRemoteScreenPolicy.pixelEndExclusive(region.y2,screen.height);
                    String metadata="{\"format\":\"jpeg\",\"sourceWidth\":"+screen.width+
                        ",\"sourceHeight\":"+screen.height+
                        ",\"regionPermille\":{\"x1\":"+region.x1+",\"y1\":"+region.y1+
                        ",\"x2\":"+region.x2+",\"y2\":"+region.y2+"}"+
                        ",\"regionPixels\":{\"left\":"+left+",\"top\":"+top+
                        ",\"width\":"+(right-left)+",\"height\":"+(bottom-top)+"}"+
                        ",\"qualityCeiling\":"+quality+",\"bytes\":"+jpeg.length+
                        ",\"sha256\":\""+sha+"\"";
                    if(!previousSha.isEmpty()&&previousSha.equals(sha))
                        return metadata+",\"unchanged\":true,\"contentOmitted\":true}";
                    return metadata+",\"unchanged\":false,\"contentB64\":\""+
                        AndroidCapabilityProtocol.escape(Base64.getEncoder().encodeToString(jpeg))+"\"}";
                }finally{java.util.Arrays.fill(jpeg,(byte)0);}
            case "gesture.tap":
                args(request.args,Set.of("x","y","durationMs"),Set.of("x","y"));
                int tapX=(int)AndroidCapabilityProtocol.integer(request.args,"x",0,1000,0);
                int tapY=(int)AndroidCapabilityProtocol.integer(request.args,"y",0,1000,0);
                long tapDuration=AndroidCapabilityProtocol.integer(request.args,"durationMs",
                    AndroidGesturePolicy.MIN_DURATION_MS,AndroidGesturePolicy.MAX_DURATION_MS,80);
                if(!AndroidGestureRuntime.tap(tapX,tapY,tapDuration))throw new IllegalStateException("GESTURE_DISPATCH_REJECTED");
                return "{\"dispatched\":true,\"kind\":\"tap\"}";
            case "gesture.swipe":
                args(request.args,Set.of("x1","y1","x2","y2","durationMs"),Set.of("x1","y1","x2","y2"));
                int x1=(int)AndroidCapabilityProtocol.integer(request.args,"x1",0,1000,0);
                int y1=(int)AndroidCapabilityProtocol.integer(request.args,"y1",0,1000,0);
                int x2=(int)AndroidCapabilityProtocol.integer(request.args,"x2",0,1000,0);
                int y2=(int)AndroidCapabilityProtocol.integer(request.args,"y2",0,1000,0);
                long swipeDuration=AndroidCapabilityProtocol.integer(request.args,"durationMs",
                    AndroidGesturePolicy.MIN_DURATION_MS,AndroidGesturePolicy.MAX_DURATION_MS,350);
                if(!AndroidGestureRuntime.swipe(x1,y1,x2,y2,swipeDuration))throw new IllegalStateException("GESTURE_DISPATCH_REJECTED");
                return "{\"dispatched\":true,\"kind\":\"swipe\"}";
            case "clipboard.read":
                args(request.args,Set.of(),Set.of());
                return "{\"text\":\""+AndroidCapabilityProtocol.escape(clipboard.readText())+"\"}";
            case "clipboard.write":
                args(request.args,Set.of("text"),Set.of("text"));
                clipboard.writeText(AndroidCapabilityProtocol.string(request.args,"text",AndroidClipboardPolicy.MAX_UTF8_BYTES));
                return "{\"written\":true}";
            case "notification.post":
                args(request.args,Set.of("title","text"),Set.of("title","text"));
                int notificationId=notifications.post(
                    AndroidCapabilityProtocol.string(request.args,"title",AndroidNotificationPolicy.MAX_TITLE_CHARS),
                    AndroidCapabilityProtocol.string(request.args,"text",AndroidNotificationPolicy.MAX_TEXT_CHARS));
                return "{\"posted\":true,\"notificationId\":"+notificationId+"}";
            case "file.list":
                args(request.args,Set.of("path"),Set.of());
                return listJson(files.list(AndroidCapabilityProtocol.optionalString(request.args,"path",512,"")));
            case "file.read":
                args(request.args,Set.of("path","maxBytes"),Set.of("path"));
                String readPath=AndroidCapabilityProtocol.string(request.args,"path",512);
                int max=(int)AndroidCapabilityProtocol.integer(request.args,"maxBytes",1,REMOTE_READ_MAX,REMOTE_READ_MAX);
                byte[] bytes=files.read(readPath,max);
                try{return "{\"bytes\":"+bytes.length+",\"contentB64\":\""+
                    AndroidCapabilityProtocol.escape(Base64.getEncoder().encodeToString(bytes))+"\"}";}
                finally{java.util.Arrays.fill(bytes,(byte)0);}
            case "file.sha256":
                args(request.args,Set.of("path"),Set.of("path"));
                return "{\"sha256\":\""+AndroidCapabilityProtocol.escape(
                    files.sha256(AndroidCapabilityProtocol.string(request.args,"path",512)))+"\"}";
            case "file.write":
                args(request.args,Set.of("parentPath","name","mimeType","contentB64"),Set.of("name","contentB64"));
                String parentPath=AndroidCapabilityProtocol.optionalString(request.args,"parentPath",512,"");
                String fileName=AndroidCapabilityProtocol.string(request.args,"name",255);
                String mimeType=AndroidRemoteFilePolicy.mime(
                    AndroidCapabilityProtocol.optionalString(request.args,"mimeType",128,"application/octet-stream"));
                byte[] writeBytes=AndroidRemoteFilePolicy.decodeContent(
                    AndroidCapabilityProtocol.string(request.args,"contentB64",8192));
                try{return entryJson(files.write(parentPath,fileName,mimeType,writeBytes));}
                finally{java.util.Arrays.fill(writeBytes,(byte)0);}
            case "file.rename":
                args(request.args,Set.of("path","newName"),Set.of("path","newName"));
                return entryJson(files.rename(AndroidCapabilityProtocol.string(request.args,"path",512),
                    AndroidCapabilityProtocol.string(request.args,"newName",255)));
            case "file.delete":
                args(request.args,Set.of("path"),Set.of("path"));
                files.delete(AndroidCapabilityProtocol.string(request.args,"path",512));
                return "{\"deleted\":true}";
            default:
                throw new SecurityException("ANDROID_ACTION_UNSUPPORTED");
        }
    }

    private static AndroidResourceProjection.Snapshot requireResources(AioAndroidCausalPlanner.Prepared prepared){
        if(prepared==null||prepared.resources==null)throw new IllegalStateException("RESOURCE_FIELD_NOT_MANIFESTED");
        return prepared.resources;
    }

    private static void requireContributionEligible(AndroidResourceProjection.Snapshot resources){
        if(!"ELIGIBLE_LOCAL_ONLY".equals(resources.contributionState))
            throw new SecurityException("RESOURCE_CONTRIBUTION_"+resources.contributionState);
    }

    static void args(StrictProjectionJson.ObjectValue args,Set<String> allowed,Set<String> required){
        if(args==null||!allowed.containsAll(args.keySet())||!args.keySet().containsAll(required))
            throw new IllegalArgumentException("ANDROID_ARGS_KEYS");
    }

    private static String sha256(byte[] bytes)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        try{
            StringBuilder out=new StringBuilder(64);
            for(byte value:digest)
                out.append(String.format(java.util.Locale.ROOT,"%02x",value&0xff));
            return out.toString();
        }finally{java.util.Arrays.fill(digest,(byte)0);}
    }

    private static String resourceJson(AndroidResourceProjection.Snapshot s){
        return "{\"batteryPercent\":"+s.batteryPercent+
            ",\"charging\":"+s.charging+
            ",\"thermalStatus\":"+s.thermalStatus+
            ",\"validatedNetwork\":"+s.validatedNetwork+
            ",\"availableMemoryBytes\":"+s.availableMemoryBytes+
            ",\"totalMemoryBytes\":"+s.totalMemoryBytes+
            ",\"availableStorageBytes\":"+s.availableStorageBytes+
            ",\"totalStorageBytes\":"+s.totalStorageBytes+
            ",\"contributionState\":\""+AndroidCapabilityProtocol.escape(s.contributionState)+
            "\",\"capturedAtMs\":"+s.capturedAtMs+"}";
    }

    private static String listJson(List<AndroidCapabilityBroker.Entry> entries){
        StringBuilder out=new StringBuilder("{\"entries\":[");
        int count=Math.min(entries.size(),LIST_MAX);
        for(int i=0;i<count;i++){
            if(i>0)out.append(',');
            out.append(entryJson(entries.get(i)));
            if(out.length()>220_000)throw new IllegalArgumentException("ANDROID_LIST_RESULT_BUDGET");
        }
        return out.append("],\"truncated\":").append(entries.size()>count).append('}').toString();
    }

    private static String entryJson(AndroidCapabilityBroker.Entry e){
        if(e.name==null||e.name.length()>512)throw new IllegalArgumentException("ANDROID_ENTRY_NAME_BOUNDS");
        String mime=e.mimeType==null?"":e.mimeType;
        if(mime.length()>192)throw new IllegalArgumentException("ANDROID_ENTRY_MIME_BOUNDS");
        return "{\"name\":\""+AndroidCapabilityProtocol.escape(e.name)+
            "\",\"directory\":"+e.directory+
            ",\"bytes\":"+e.bytes+
            ",\"modifiedMs\":"+e.modifiedMs+
            ",\"mimeType\":\""+AndroidCapabilityProtocol.escape(mime)+"\"}";
    }

    private static String safeCode(Exception failure){
        String message=failure.getMessage();
        if(message!=null&&message.matches("[A-Za-z0-9_.:-]{1,96}"))return message;
        return failure instanceof SecurityException?"AUTHORITY_DENIED":"INTERNAL_FAILURE";
    }
}
