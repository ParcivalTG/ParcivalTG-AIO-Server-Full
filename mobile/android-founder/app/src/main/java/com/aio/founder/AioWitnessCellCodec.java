package com.aio.founder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/** Compact exact representation for simple evidence rows. Falls back to JSON whenever it cannot win safely. */
final class AioWitnessCellCodec {
    private static final String PREFIX="@AIOW1:";
    private static final int VERSION=1;
    private static final int MAX_FIELDS=64;
    private static final int MAX_TEXT_BYTES=32*1024;

    private static final LinkedHashMap<String,Integer> KEY_IDS=new LinkedHashMap<>();
    private static final String[] ID_KEYS=new String[64];
    private static final LinkedHashMap<String,Integer> VALUE_IDS=new LinkedHashMap<>();
    private static final String[] ID_VALUES=new String[128];

    static {
        int k=1;
        for(String key:new String[]{
            "code","atUnixMs","timestampUnixMs","requestId","peerId","sourceSessionEpoch",
            "capability","action","resultCode","manifestedDependencies","unmanifestedDependencies",
            "dependencyUniverse","nonManifestationPermille","requesterRoundTripMs","intentId",
            "provider","state","privacyClass","role","status","schema","roundTripMs"
        })addKey(k++,key);

        int v=1;
        for(String value:new String[]{
            "OK","UNKNOWN","FOUNDER_ONLY","LOCAL_ONLY","AIO","ChatGPT","Founder",
            "windows-founder-r5","chatgpt-local-provider",
            "SCREEN_OBSERVE","GESTURE_INPUT","FILE_READ","FILE_WRITE","CLIPBOARD",
            "NOTIFICATIONS","RESOURCE_STATUS","RESOURCE_CONTRIBUTE",
            "screen.capture","gesture.tap","gesture.swipe","file.list","file.read","file.sha256",
            "file.write","file.rename","file.delete","clipboard.read","clipboard.write",
            "notification.post","resource.status","resource.sha256","resource.deflate",
            "CHATGPT_ANDROID_TOOL_ACCEPTED","CHATGPT_ANDROID_TOOL_DENIED",
            "CHATGPT_ANDROID_HINDSIGHT_WITNESS","CHATGPT_ANDROID_REFLECTION_GATE",
            "ANDROID_CAPABILITY_ATTEMPT","ANDROID_CAPABILITY_ALLOWED","ANDROID_CAPABILITY_DENIED",
            "E2E_RECEIPT_VERIFIED","E2E_REJECTION_VERIFIED",
            "WINDOWS_AUTHORITY_REFRESHED_PINNED_E2E","PERSISTENT_NODE_SESSION_RESTORED_PINNED_E2E",
            "no-active-grant","SCREEN_CAPTURE_NOT_ACTIVE","GESTURE_DISPATCH_REJECTED"
        })addValue(v++,value);
    }

    private AioWitnessCellCodec(){}

    static String encode(JSONObject row)throws Exception{
        if(row==null)throw new IllegalArgumentException("WITNESS_ROW_REQUIRED");
        String raw=row.toString();
        byte[] packed=pack(row);
        if(packed==null)return raw;
        try{
            String encoded=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
            return encoded.length()<raw.length()?encoded:raw;
        }finally{Arrays.fill(packed,(byte)0);}
    }

    static JSONObject decode(String stored)throws Exception{
        if(stored==null)throw new IllegalArgumentException("WITNESS_ROW_REQUIRED");
        if(!stored.startsWith(PREFIX))return new JSONObject(stored);
        byte[] packet;
        try{packet=Base64.getUrlDecoder().decode(stored.substring(PREFIX.length()));}
        catch(IllegalArgumentException invalid){throw new SecurityException("WITNESS_CELL_BASE64");}
        try{return unpack(packet);}
        finally{Arrays.fill(packet,(byte)0);}
    }

    static boolean isCell(String stored){return stored!=null&&stored.startsWith(PREFIX);}

    private static byte[] pack(JSONObject row)throws Exception{
        if(row.length()<1||row.length()>MAX_FIELDS)return null;
        ByteArrayOutputStream out=new ByteArrayOutputStream(96);
        out.write(VERSION);out.write(row.length());
        java.util.Iterator<String> keys=row.keys();
        while(keys.hasNext()){
            String key=keys.next();
            Object value=row.get(key);
            if(value instanceof JSONObject||value instanceof org.json.JSONArray)return null;
            writeSymbol(out,key,KEY_IDS);
            if(value==JSONObject.NULL||value==null){
                out.write(0);
            }else if(value instanceof Boolean){
                out.write(1);out.write((Boolean)value?1:0);
            }else if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long){
                out.write(2);writeVarLong(out,zigZag(((Number)value).longValue()));
            }else if(value instanceof String){
                String text=(String)value;
                if(text.matches("[0-9a-fA-F-]{36}")&&isUuid(text)){
                    out.write(3);writeUuid(out,UUID.fromString(text));
                }else{
                    byte[] utf8=text.getBytes(StandardCharsets.UTF_8);
                    try{
                        if(utf8.length>MAX_TEXT_BYTES)return null;
                    }finally{Arrays.fill(utf8,(byte)0);}
                    out.write(4);writeSymbol(out,text,VALUE_IDS);
                }
            }else{
                return null;
            }
        }
        byte[] body=out.toByteArray();
        CRC32 crc=new CRC32();crc.update(body);
        long value=crc.getValue();
        byte[] packet=Arrays.copyOf(body,body.length+4);
        packet[body.length]=(byte)(value>>>24);packet[body.length+1]=(byte)(value>>>16);
        packet[body.length+2]=(byte)(value>>>8);packet[body.length+3]=(byte)value;
        Arrays.fill(body,(byte)0);
        return packet;
    }

    private static JSONObject unpack(byte[] packet)throws Exception{
        if(packet.length<6)throw new SecurityException("WITNESS_CELL_TRUNCATED");
        int limit=packet.length-4;
        CRC32 crc=new CRC32();crc.update(packet,0,limit);
        long expected=((long)(packet[limit]&0xff)<<24)|((long)(packet[limit+1]&0xff)<<16)|
            ((long)(packet[limit+2]&0xff)<<8)|(packet[limit+3]&0xffL);
        if(crc.getValue()!=expected)throw new SecurityException("WITNESS_CELL_CRC");

        Cursor in=new Cursor(packet,limit);
        if(in.u8()!=VERSION)throw new SecurityException("WITNESS_CELL_VERSION");
        int fields=in.u8();
        if(fields<1||fields>MAX_FIELDS)throw new SecurityException("WITNESS_CELL_FIELDS");
        JSONObject row=new JSONObject();
        for(int i=0;i<fields;i++){
            String key=readSymbol(in,ID_KEYS,MAX_TEXT_BYTES);
            int type=in.u8();
            switch(type){
                case 0:row.put(key,JSONObject.NULL);break;
                case 1:row.put(key,in.u8()!=0);break;
                case 2:row.put(key,unZigZag(readVarLong(in)));break;
                case 3:row.put(key,in.uuid().toString());break;
                case 4:row.put(key,readSymbol(in,ID_VALUES,MAX_TEXT_BYTES));break;
                default:throw new SecurityException("WITNESS_CELL_TYPE");
            }
        }
        if(!in.done())throw new SecurityException("WITNESS_CELL_TRAILING");
        return row;
    }

    private static void writeSymbol(ByteArrayOutputStream out,String value,Map<String,Integer> dictionary){
        Integer id=dictionary.get(value);
        if(id!=null){out.write(id);return;}
        out.write(0);
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        try{
            if(bytes.length>0xffff)throw new IllegalArgumentException("WITNESS_CELL_TEXT");
            out.write(bytes.length>>>8);out.write(bytes.length);out.write(bytes,0,bytes.length);
        }finally{Arrays.fill(bytes,(byte)0);}
    }

    private static String readSymbol(Cursor in,String[] dictionary,int max)throws Exception{
        int id=in.u8();
        if(id!=0){
            if(id>=dictionary.length||dictionary[id]==null)throw new SecurityException("WITNESS_CELL_SYMBOL");
            return dictionary[id];
        }
        int length=in.u16();
        if(length>max||length>in.remaining())throw new SecurityException("WITNESS_CELL_TEXT");
        byte[] bytes=in.bytes(length);
        try{
            String value=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            if(value.length()>max)throw new SecurityException("WITNESS_CELL_TEXT");
            return value;
        }finally{Arrays.fill(bytes,(byte)0);}
    }

    private static void writeUuid(ByteArrayOutputStream out,UUID value){
        writeLong64(out,value.getMostSignificantBits());
        writeLong64(out,value.getLeastSignificantBits());
    }

    private static void writeLong64(ByteArrayOutputStream out,long value){
        for(int shift=56;shift>=0;shift-=8)out.write((int)(value>>>shift));
    }

    private static void writeVarLong(ByteArrayOutputStream out,long value){
        while((value&~0x7fL)!=0){out.write((int)(value&0x7f)|0x80);value>>>=7;}
        out.write((int)value);
    }

    private static long readVarLong(Cursor in){
        long value=0;int shift=0;
        while(shift<70){
            int b=in.u8();value|=(long)(b&0x7f)<<shift;
            if((b&0x80)==0)return value;
            shift+=7;
        }
        throw new SecurityException("WITNESS_CELL_VARINT");
    }

    private static long zigZag(long value){return (value<<1)^(value>>63);}
    private static long unZigZag(long value){return (value>>>1)^-(value&1);}

    private static boolean isUuid(String text){
        try{UUID.fromString(text);return true;}catch(Exception ignored){return false;}
    }

    private static void addKey(int id,String value){KEY_IDS.put(value,id);ID_KEYS[id]=value;}
    private static void addValue(int id,String value){VALUE_IDS.put(value,id);ID_VALUES[id]=value;}

    private static final class Cursor{
        final byte[] data;final int limit;int offset;
        Cursor(byte[] data,int limit){this.data=data;this.limit=limit;}
        int u8(){need(1);return data[offset++]&0xff;}
        int u16(){return (u8()<<8)|u8();}
        int remaining(){return limit-offset;}
        boolean done(){return offset==limit;}
        void need(int n){if(n<0||offset+n>limit)throw new SecurityException("WITNESS_CELL_TRUNCATED");}
        byte[] bytes(int n){need(n);byte[] out=Arrays.copyOfRange(data,offset,offset+n);offset+=n;return out;}
        UUID uuid(){
            need(16);
            long msb=0,lsb=0;
            for(int i=0;i<8;i++)msb=(msb<<8)|(u8()&0xffL);
            for(int i=0;i<8;i++)lsb=(lsb<<8)|(u8()&0xffL);
            return new UUID(msb,lsb);
        }
    }
}
