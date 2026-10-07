package com.aio.founder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * AIO-native capability projection packet.
 * The host still receives ordinary bytes, but AIO semantics are not serialized as JSON.
 * JSON remains a compatibility membrane, not the canonical wire representation.
 */
final class AioProjectionQuantumCodec {
    static final int MAX_PACKET_BYTES=64*1024;
    private static final int MAGIC0=0xA1;
    private static final int MAGIC1=0x51;
    private static final int VERSION=1;

    private static final LinkedHashMap<String,Integer> ACTION_IDS=new LinkedHashMap<>();
    private static final String[] ID_ACTIONS=new String[16];
    static {
        add(0,"resource.status");
        add(1,"screen.capture");
        add(2,"gesture.tap");
        add(3,"gesture.swipe");
        add(4,"clipboard.read");
        add(5,"clipboard.write");
        add(6,"notification.post");
        add(7,"file.list");
        add(8,"file.read");
        add(9,"file.sha256");
        add(10,"file.write");
        add(11,"file.rename");
        add(12,"file.delete");
        add(13,"resource.sha256");
        add(14,"resource.deflate");
    }

    private AioProjectionQuantumCodec(){}

    static boolean looksLike(byte[] payload){
        return payload!=null&&payload.length>=9&&
            (payload[0]&0xff)==MAGIC0&&(payload[1]&0xff)==MAGIC1;
    }

    static byte[] encode(AndroidCapabilityProtocol.Request request)throws Exception{
        if(request==null)throw new IllegalArgumentException("QUANTUM_REQUEST_REQUIRED");
        Integer actionId=ACTION_IDS.get(request.action);
        if(actionId==null||!AndroidCapabilityCatalog.supports(request.capability,request.action))
            throw new IllegalArgumentException("QUANTUM_ACTION_UNSUPPORTED");

        ByteArrayOutputStream out=new ByteArrayOutputStream(128);
        out.write(MAGIC0);out.write(MAGIC1);out.write(VERSION);out.write(actionId);
        out.write(request.privacy.ordinal());
        encodeArgs(out,request);
        byte[] body=out.toByteArray();
        if(body.length+4>MAX_PACKET_BYTES)throw new IllegalArgumentException("QUANTUM_PACKET_BUDGET");

        CRC32 crc=new CRC32();crc.update(body);
        long value=crc.getValue();
        byte[] packet=Arrays.copyOf(body,body.length+4);
        packet[body.length]=(byte)(value>>>24);
        packet[body.length+1]=(byte)(value>>>16);
        packet[body.length+2]=(byte)(value>>>8);
        packet[body.length+3]=(byte)value;
        Arrays.fill(body,(byte)0);
        return packet;
    }

    static AndroidCapabilityProtocol.Request decode(byte[] packet)throws Exception{
        if(!looksLike(packet)||packet.length>MAX_PACKET_BYTES)
            throw new IllegalArgumentException("QUANTUM_PACKET_INVALID");
        if(packet.length<9)throw new IllegalArgumentException("QUANTUM_PACKET_INVALID");

        int bodyLength=packet.length-4;
        CRC32 crc=new CRC32();crc.update(packet,0,bodyLength);
        long expected=((long)(packet[bodyLength]&0xff)<<24)|
            ((long)(packet[bodyLength+1]&0xff)<<16)|
            ((long)(packet[bodyLength+2]&0xff)<<8)|
            (long)(packet[bodyLength+3]&0xff);
        if(crc.getValue()!=expected)throw new SecurityException("QUANTUM_CRC_INVALID");

        Cursor in=new Cursor(packet,bodyLength);
        if(in.u8()!=MAGIC0||in.u8()!=MAGIC1||in.u8()!=VERSION)
            throw new IllegalArgumentException("QUANTUM_VERSION_INVALID");
        int actionId=in.u8();
        if(actionId<0||actionId>=ID_ACTIONS.length||ID_ACTIONS[actionId]==null)
            throw new IllegalArgumentException("QUANTUM_ACTION_UNSUPPORTED");
        String action=ID_ACTIONS[actionId];

        int privacyOrdinal=in.u8();
        AioAndroidNode.Privacy[] privacyValues=AioAndroidNode.Privacy.values();
        if(privacyOrdinal<0||privacyOrdinal>=privacyValues.length)
            throw new IllegalArgumentException("QUANTUM_PRIVACY_INVALID");
        AioAndroidNode.Privacy privacy=privacyValues[privacyOrdinal];

        AioAndroidNode.Capability capability=capability(action);
        StrictProjectionJson.ObjectValue args=decodeArgs(in,action);
        if(!in.done())throw new IllegalArgumentException("QUANTUM_TRAILING_BYTES");
        if(!AndroidCapabilityCatalog.supports(capability,action))
            throw new SecurityException("QUANTUM_CAPABILITY_MAPPING_INVALID");
        return new AndroidCapabilityProtocol.Request(capability,action,privacy,args);
    }

    private static void encodeArgs(ByteArrayOutputStream out,AndroidCapabilityProtocol.Request request)throws Exception{
        StrictProjectionJson.ObjectValue args=request.args;
        switch(request.action){
            case "resource.status":
            case "clipboard.read":
                AndroidCapabilityDispatcher.args(args,java.util.Set.of(),java.util.Set.of());
                return;
            case "screen.capture":{
                AndroidCapabilityDispatcher.args(args,
                    java.util.Set.of("quality","maxBytes","x1","y1","x2","y2","previousSha256"),
                    java.util.Set.of());
                int flags=0;
                if(args.containsKey("quality"))flags|=1;
                if(args.containsKey("maxBytes"))flags|=2;
                boolean anyRegion=args.containsKey("x1")||args.containsKey("y1")||
                    args.containsKey("x2")||args.containsKey("y2");
                boolean fullRegion=args.containsKey("x1")&&args.containsKey("y1")&&
                    args.containsKey("x2")&&args.containsKey("y2");
                if(anyRegion&&!fullRegion)throw new IllegalArgumentException("SCREEN_REGION_KEYS");
                if(fullRegion)flags|=4;
                if(args.containsKey("previousSha256"))flags|=8;
                out.write(flags);
                if((flags&1)!=0)u16(out,(int)AndroidCapabilityProtocol.integer(args,"quality",30,75,55));
                if((flags&2)!=0)u32(out,AndroidCapabilityProtocol.integer(args,"maxBytes",
                    AndroidRemoteScreenPolicy.MIN_JPEG_BYTES,AndroidRemoteScreenPolicy.MAX_JPEG_BYTES,
                    AndroidRemoteScreenPolicy.DEFAULT_MAX_JPEG_BYTES));
                if((flags&4)!=0){
                    u16(out,(int)AndroidCapabilityProtocol.integer(args,"x1",0,999,0));
                    u16(out,(int)AndroidCapabilityProtocol.integer(args,"y1",0,999,0));
                    u16(out,(int)AndroidCapabilityProtocol.integer(args,"x2",1,1000,1000));
                    u16(out,(int)AndroidCapabilityProtocol.integer(args,"y2",1,1000,1000));
                }
                if((flags&8)!=0){
                    String sha=AndroidCapabilityProtocol.string(args,"previousSha256",64)
                        .toLowerCase(java.util.Locale.ROOT);
                    if(!sha.matches("[0-9a-f]{64}"))
                        throw new IllegalArgumentException("SCREEN_PREVIOUS_HASH_INVALID");
                    out.write(hex(sha));
                }
                return;
            }
            case "gesture.tap":{
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("x","y","durationMs"),java.util.Set.of("x","y"));
                int flags=args.containsKey("durationMs")?1:0;out.write(flags);
                u16(out,(int)AndroidCapabilityProtocol.integer(args,"x",0,1000,0));
                u16(out,(int)AndroidCapabilityProtocol.integer(args,"y",0,1000,0));
                if(flags!=0)u16(out,(int)AndroidCapabilityProtocol.integer(args,"durationMs",
                    AndroidGesturePolicy.MIN_DURATION_MS,AndroidGesturePolicy.MAX_DURATION_MS,80));
                return;
            }
            case "gesture.swipe":{
                AndroidCapabilityDispatcher.args(args,
                    java.util.Set.of("x1","y1","x2","y2","durationMs"),
                    java.util.Set.of("x1","y1","x2","y2"));
                int flags=args.containsKey("durationMs")?1:0;out.write(flags);
                for(String key:new String[]{"x1","y1","x2","y2"})
                    u16(out,(int)AndroidCapabilityProtocol.integer(args,key,0,1000,0));
                if(flags!=0)u16(out,(int)AndroidCapabilityProtocol.integer(args,"durationMs",
                    AndroidGesturePolicy.MIN_DURATION_MS,AndroidGesturePolicy.MAX_DURATION_MS,350));
                return;
            }
            case "clipboard.write":
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("text"),java.util.Set.of("text"));
                text(out,AndroidCapabilityProtocol.string(args,"text",AndroidClipboardPolicy.MAX_UTF8_BYTES));
                return;
            case "notification.post":
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("title","text"),java.util.Set.of("title","text"));
                text(out,AndroidCapabilityProtocol.string(args,"title",AndroidNotificationPolicy.MAX_TITLE_CHARS));
                text(out,AndroidCapabilityProtocol.string(args,"text",AndroidNotificationPolicy.MAX_TEXT_CHARS));
                return;
            case "file.list":{
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("path"),java.util.Set.of());
                int flags=args.containsKey("path")?1:0;out.write(flags);
                if(flags!=0)text(out,AndroidCapabilityProtocol.optionalString(args,"path",512,""));
                return;
            }
            case "file.read":{
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("path","maxBytes"),java.util.Set.of("path"));
                int flags=args.containsKey("maxBytes")?1:0;out.write(flags);
                text(out,AndroidCapabilityProtocol.string(args,"path",512));
                if(flags!=0)u32(out,AndroidCapabilityProtocol.integer(args,"maxBytes",1,256*1024,256*1024));
                return;
            }
            case "file.sha256":
            case "file.delete":
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("path"),java.util.Set.of("path"));
                text(out,AndroidCapabilityProtocol.string(args,"path",512));
                return;
            case "file.write":{
                AndroidCapabilityDispatcher.args(args,
                    java.util.Set.of("parentPath","name","mimeType","contentB64"),
                    java.util.Set.of("name","contentB64"));
                int flags=(args.containsKey("parentPath")?1:0)|(args.containsKey("mimeType")?2:0);
                out.write(flags);
                if((flags&1)!=0)text(out,AndroidCapabilityProtocol.optionalString(args,"parentPath",512,""));
                text(out,AndroidCapabilityProtocol.string(args,"name",255));
                if((flags&2)!=0)text(out,AndroidCapabilityProtocol.optionalString(args,"mimeType",128,"application/octet-stream"));
                text(out,AndroidCapabilityProtocol.string(args,"contentB64",8192));
                return;
            }
            case "file.rename":
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("path","newName"),java.util.Set.of("path","newName"));
                text(out,AndroidCapabilityProtocol.string(args,"path",512));
                text(out,AndroidCapabilityProtocol.string(args,"newName",255));
                return;
            case "resource.sha256":
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("contentB64"),java.util.Set.of("contentB64"));
                text(out,AndroidCapabilityProtocol.string(args,"contentB64",8192));
                return;
            case "resource.deflate":{
                AndroidCapabilityDispatcher.args(args,java.util.Set.of("contentB64","level"),java.util.Set.of("contentB64"));
                int flags=args.containsKey("level")?1:0;out.write(flags);
                text(out,AndroidCapabilityProtocol.string(args,"contentB64",8192));
                if(flags!=0)out.write((int)AndroidCapabilityProtocol.integer(args,"level",1,9,6));
                return;
            }
            default:throw new IllegalArgumentException("QUANTUM_ACTION_UNSUPPORTED");
        }
    }

    private static StrictProjectionJson.ObjectValue decodeArgs(Cursor in,String action)throws Exception{
        StrictProjectionJson.ObjectValue args=new StrictProjectionJson.ObjectValue();
        switch(action){
            case "resource.status":
            case "clipboard.read": return args;
            case "screen.capture":{
                int flags=in.u8();
                if((flags&~15)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                if((flags&1)!=0)args.put("quality",(long)in.u16());
                if((flags&2)!=0)args.put("maxBytes",in.u32());
                if((flags&4)!=0){
                    args.put("x1",(long)in.u16());args.put("y1",(long)in.u16());
                    args.put("x2",(long)in.u16());args.put("y2",(long)in.u16());
                }
                if((flags&8)!=0)args.put("previousSha256",toHex(in.bytes(32)));
                return args;
            }
            case "gesture.tap":{
                int flags=in.u8();if((flags&~1)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                args.put("x",(long)in.u16());args.put("y",(long)in.u16());
                if((flags&1)!=0)args.put("durationMs",(long)in.u16());
                return args;
            }
            case "gesture.swipe":{
                int flags=in.u8();if((flags&~1)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                args.put("x1",(long)in.u16());args.put("y1",(long)in.u16());
                args.put("x2",(long)in.u16());args.put("y2",(long)in.u16());
                if((flags&1)!=0)args.put("durationMs",(long)in.u16());
                return args;
            }
            case "clipboard.write":args.put("text",in.text(AndroidClipboardPolicy.MAX_UTF8_BYTES));return args;
            case "notification.post":
                args.put("title",in.text(AndroidNotificationPolicy.MAX_TITLE_CHARS));
                args.put("text",in.text(AndroidNotificationPolicy.MAX_TEXT_CHARS));return args;
            case "file.list":{
                int flags=in.u8();if((flags&~1)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                if((flags&1)!=0)args.put("path",in.text(512));return args;
            }
            case "file.read":{
                int flags=in.u8();if((flags&~1)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                args.put("path",in.text(512));if((flags&1)!=0)args.put("maxBytes",in.u32());return args;
            }
            case "file.sha256":
            case "file.delete":args.put("path",in.text(512));return args;
            case "file.write":{
                int flags=in.u8();if((flags&~3)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                if((flags&1)!=0)args.put("parentPath",in.text(512));
                args.put("name",in.text(255));
                if((flags&2)!=0)args.put("mimeType",in.text(128));
                args.put("contentB64",in.text(8192));return args;
            }
            case "file.rename":
                args.put("path",in.text(512));args.put("newName",in.text(255));return args;
            case "resource.sha256":args.put("contentB64",in.text(8192));return args;
            case "resource.deflate":{
                int flags=in.u8();if((flags&~1)!=0)throw new IllegalArgumentException("QUANTUM_FLAGS_INVALID");
                args.put("contentB64",in.text(8192));
                if((flags&1)!=0)args.put("level",(long)in.u8());
                return args;
            }
            default:throw new IllegalArgumentException("QUANTUM_ACTION_UNSUPPORTED");
        }
    }

    private static AioAndroidNode.Capability capability(String action){
        for(AioAndroidNode.Capability capability:AioAndroidNode.Capability.values())
            if(AndroidCapabilityCatalog.supports(capability,action))return capability;
        throw new IllegalArgumentException("QUANTUM_ACTION_UNSUPPORTED");
    }

    private static void add(int id,String action){
        ACTION_IDS.put(action,id);ID_ACTIONS[id]=action;
    }

    private static void u16(ByteArrayOutputStream out,int value){
        if(value<0||value>0xffff)throw new IllegalArgumentException("QUANTUM_U16");
        out.write(value>>>8);out.write(value);
    }

    private static void u32(ByteArrayOutputStream out,long value){
        if(value<0||value>0xffffffffL)throw new IllegalArgumentException("QUANTUM_U32");
        out.write((int)(value>>>24));out.write((int)(value>>>16));out.write((int)(value>>>8));out.write((int)value);
    }

    private static void text(ByteArrayOutputStream out,String value){
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        try{
            if(bytes.length>0xffff)throw new IllegalArgumentException("QUANTUM_TEXT_BUDGET");
            u16(out,bytes.length);out.write(bytes,0,bytes.length);
        }finally{Arrays.fill(bytes,(byte)0);}
    }

    private static byte[] hex(String text){
        byte[] out=new byte[text.length()/2];
        for(int i=0;i<out.length;i++)
            out[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);
        return out;
    }

    private static String toHex(byte[] bytes){
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte value:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",value&0xff));
        Arrays.fill(bytes,(byte)0);
        return out.toString();
    }

    private static final class Cursor{
        final byte[] data;final int limit;int offset;
        Cursor(byte[] data,int limit){this.data=data;this.limit=limit;}
        int u8(){need(1);return data[offset++]&0xff;}
        int u16(){need(2);return (u8()<<8)|u8();}
        long u32(){need(4);return ((long)u8()<<24)|((long)u8()<<16)|((long)u8()<<8)|u8();}
        byte[] bytes(int n){need(n);byte[] out=Arrays.copyOfRange(data,offset,offset+n);offset+=n;return out;}
        String text(int max)throws Exception{
            int n=u16();if(n>max*4||n>remaining())throw new IllegalArgumentException("QUANTUM_TEXT_BUDGET");
            byte[] bytes=bytes(n);
            try{
                String value=StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
                if(value.length()>max)throw new IllegalArgumentException("QUANTUM_TEXT_BUDGET");
                return value;
            }finally{Arrays.fill(bytes,(byte)0);}
        }
        int remaining(){return limit-offset;}
        boolean done(){return offset==limit;}
        void need(int n){if(n<0||offset+n>limit)throw new IllegalArgumentException("QUANTUM_TRUNCATED");}
    }
}
