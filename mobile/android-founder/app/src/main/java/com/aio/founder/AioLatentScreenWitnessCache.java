package com.aio.founder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny latent witness field for GPT visual state.
 * A prior image hash is retained as a coordinate witness, not as another image.
 * Re-observation can therefore prove non-change without rematerializing JPEG bytes.
 */
final class AioLatentScreenWitnessCache {
    private final int maximum;
    private final LinkedHashMap<String,String> witnesses;

    AioLatentScreenWitnessCache(int maximum){
        if(maximum<1||maximum>64)throw new IllegalArgumentException("SCREEN_WITNESS_CACHE_BOUNDS");
        this.maximum=maximum;
        this.witnesses=new LinkedHashMap<String,String>(maximum,0.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest){
                return size()>AioLatentScreenWitnessCache.this.maximum;
            }
        };
    }

    synchronized boolean inject(AndroidCapabilityProtocol.Request request){
        if(request==null||!"screen.capture".equals(request.action))return false;
        if(request.args.containsKey("previousSha256"))return false;
        String prior=witnesses.get(key(request));
        if(prior==null)return false;
        request.args.put("previousSha256",prior);
        return true;
    }

    synchronized void remember(AndroidCapabilityProtocol.Request request,String sha256){
        if(request==null||!"screen.capture".equals(request.action))return;
        if(sha256==null||!sha256.matches("[0-9A-Fa-f]{64}"))
            throw new IllegalArgumentException("SCREEN_WITNESS_HASH_INVALID");
        witnesses.put(key(request),sha256.toLowerCase(java.util.Locale.ROOT));
    }

    synchronized int size(){return witnesses.size();}
    synchronized void clear(){witnesses.clear();}

    static String key(AndroidCapabilityProtocol.Request request){
        if(request==null||!"screen.capture".equals(request.action))
            throw new IllegalArgumentException("SCREEN_WITNESS_REQUEST_INVALID");
        long quality=AndroidCapabilityProtocol.integer(
            request.args,"quality",30,75,AndroidRemoteScreenPolicy.DEFAULT_QUALITY);
        long maxBytes=AndroidCapabilityProtocol.integer(
            request.args,"maxBytes",
            AndroidRemoteScreenPolicy.MIN_JPEG_BYTES,
            AndroidRemoteScreenPolicy.MAX_JPEG_BYTES,
            AndroidRemoteScreenPolicy.DEFAULT_MAX_JPEG_BYTES);
        boolean any=request.args.containsKey("x1")||request.args.containsKey("y1")||
            request.args.containsKey("x2")||request.args.containsKey("y2");
        boolean full=request.args.containsKey("x1")&&request.args.containsKey("y1")&&
            request.args.containsKey("x2")&&request.args.containsKey("y2");
        if(any&&!full)throw new IllegalArgumentException("SCREEN_REGION_KEYS");
        long x1=full?AndroidCapabilityProtocol.integer(request.args,"x1",0,999,0):0;
        long y1=full?AndroidCapabilityProtocol.integer(request.args,"y1",0,999,0):0;
        long x2=full?AndroidCapabilityProtocol.integer(request.args,"x2",1,1000,1000):1000;
        long y2=full?AndroidCapabilityProtocol.integer(request.args,"y2",1,1000,1000):1000;
        return quality+"|"+maxBytes+"|"+x1+"|"+y1+"|"+x2+"|"+y2;
    }
}
