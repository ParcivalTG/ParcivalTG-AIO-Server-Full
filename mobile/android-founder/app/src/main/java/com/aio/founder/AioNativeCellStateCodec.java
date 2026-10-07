package com.aio.founder;

import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Cell-addressable AIO-native state codec.
 *
 * Competes against AioNativeStateCodec. Cell mode is selected only when its full
 * stored-character cost is lower. Otherwise the already-qualified whole-state
 * portfolio remains authoritative.
 */
final class AioNativeCellStateCodec {
    private static final String PREFIX="@AIOCELL1:";
    private static final int MAX_CELLS=4096;
    private static final int MAX_CELL_BYTES=256*1024;
    private static final int DEFAULT_ESCAPE_RADIUS=4;

    static final class Decision {
        final String stored;
        final boolean cellArchive;
        final int cells;
        final long logicalBytes,representationBytes;
        final String fallbackDescription;
        Decision(String stored,boolean cellArchive,int cells,long logicalBytes,long representationBytes,String fallbackDescription){
            this.stored=stored;this.cellArchive=cellArchive;this.cells=cells;
            this.logicalBytes=logicalBytes;this.representationBytes=representationBytes;
            this.fallbackDescription=fallbackDescription;
        }
    }

    private AioNativeCellStateCodec(){}

    static String encodeArray(JSONArray array)throws Exception{
        Decision decision=decide(array);
        AioNativeRuntimeTelemetry.recordState(
            decision.logicalBytes,
            decision.stored.getBytes(StandardCharsets.UTF_8).length,
            decision.cellArchive);
        return decision.stored;
    }

    static Decision decide(JSONArray array)throws Exception{
        if(array==null)throw new IllegalArgumentException("AIO_ARRAY_REQUIRED");
        if(array.length()>MAX_CELLS)throw new IllegalArgumentException("AIO_CELL_COUNT_BUDGET");

        String raw=array.toString();
        AioNativeStateCodec.Decision whole=AioNativeStateCodec.decide(raw);

        ArrayList<byte[]> logical=new ArrayList<>(array.length());
        long logicalBytes=0;
        try{
            for(int i=0;i<array.length();i++){
                byte[] bytes=String.valueOf(array.get(i)).getBytes(StandardCharsets.UTF_8);
                if(bytes.length>MAX_CELL_BYTES)throw new IllegalArgumentException("AIO_CELL_BUDGET");
                logical.add(bytes);logicalBytes+=bytes.length;
            }
            AioNativeRepresentationFabric.Archive archive=
                AioNativeRepresentationFabric.encode(logical,DEFAULT_ESCAPE_RADIUS);
            try{
                String cellStored=pack(archive,raw);
                if(cellStored.length()<whole.stored.length())
                    return new Decision(cellStored,true,array.length(),logicalBytes,
                        archive.metrics().representationBytes,AioNativeStateCodec.describeStored(whole.stored));
                return new Decision(whole.stored,false,array.length(),logicalBytes,
                    whole.payloadBytes,AioNativeStateCodec.describeStored(whole.stored));
            }finally{
                archive.clearPayloads();
            }
        }finally{
            for(byte[] row:logical)Arrays.fill(row,(byte)0);
        }
    }

    static String decodeArray(String stored)throws Exception{
        if(stored==null)return null;
        if(!stored.startsWith(PREFIX))return AioNativeStateCodec.decodeFromStorage(stored);
        DecodedArchive decoded=unpack(stored);
        try{
            ArrayList<byte[]> cells=new ArrayList<>(decoded.archive.size());
            try{
                StringBuilder out=new StringBuilder("[");
                for(int i=0;i<decoded.archive.size();i++){
                    byte[] row=decoded.manifestVerified(i);cells.add(row);
                    if(i>0)out.append(',');
                    out.append(new String(row,StandardCharsets.UTF_8));
                }
                out.append(']');
                String raw=out.toString();
                byte[] rawBytes=raw.getBytes(StandardCharsets.UTF_8);
                try{
                    if(!sha256(rawBytes).equals(decoded.expectedHash))
                        throw new SecurityException("AIO_CELL_ARCHIVE_HASH");
                }finally{Arrays.fill(rawBytes,(byte)0);}
                return raw;
            }finally{
                for(byte[] row:cells)Arrays.fill(row,(byte)0);
            }
        }finally{decoded.clear();}
    }

    static JSONArray tail(String stored,int count)throws Exception{
        if(count<0||count>MAX_CELLS)throw new IllegalArgumentException("AIO_TAIL_COUNT");
        if(stored==null||count==0)return new JSONArray();
        if(!stored.startsWith(PREFIX)){
            JSONArray full=new JSONArray(AioNativeStateCodec.decodeFromStorage(stored));
            JSONArray out=new JSONArray();
            for(int i=Math.max(0,full.length()-count);i<full.length();i++)out.put(full.get(i));
            AioNativeRuntimeTelemetry.recordSelective(out.length(),full.length(),full.length());
            return out;
        }

        DecodedArchive decoded=unpack(stored);
        try{
            JSONArray out=new JSONArray();
            int start=Math.max(0,decoded.archive.size()-count);
            int physical=0;
            for(int i=start;i<decoded.archive.size();i++){
                AioNativeRepresentationFabric.Manifestation manifestedCell=decoded.archive.manifest(i);
                physical+=manifestedCell.manifestedCells;
                byte[] manifested=manifestedCell.bytes;
                byte[] digest=sha256Bytes(manifested);
                try{
                    if(!MessageDigest.isEqual(digest,decoded.cellDigests.get(i)))
                        throw new SecurityException("AIO_CELL_WITNESS_HASH");
                    out.put(new org.json.JSONTokener(new String(manifested,StandardCharsets.UTF_8)).nextValue());
                }finally{
                    Arrays.fill(digest,(byte)0);Arrays.fill(manifested,(byte)0);
                }
            }
            AioNativeRuntimeTelemetry.recordSelective(out.length(),physical,decoded.archive.size());
            return out;
        }finally{decoded.clear();}
    }

    static String describeStored(String stored){
        if(stored==null)return "EMPTY";
        if(!stored.startsWith(PREFIX))return AioNativeStateCodec.describeStored(stored);
        try{
            DecodedArchive decoded=unpack(stored);
            try{
                AioNativeRepresentationFabric.Metrics m=decoded.archive.metrics();
                return "CELL_PORTFOLIO | cells="+m.cells+
                    " | logicalBytes="+m.logicalBytes+
                    " | representedBytes="+m.representationBytes+
                    " | witnesses="+m.witnessCells+
                    " | mirrors="+m.mirrorCells+
                    " | runs="+m.runCells+
                    " | maxDepth="+m.maxDepth;
            }finally{decoded.clear();}
        }catch(Exception failure){return "CORRUPT_AIO_CELL_STATE";}
    }

    private static String pack(AioNativeRepresentationFabric.Archive archive,String raw)throws Exception{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        for(int i=0;i<archive.size();i++){
            AioNativeRepresentationFabric.Cell cell=archive.cell(i);
            AioNativeRepresentationFabric.Manifestation manifestation=archive.manifest(i);
            byte[] digest=sha256Bytes(manifestation.bytes);
            try{
                body.write(cell.kind.ordinal());
                writeInt(body,cell.reference);
                writeInt(body,cell.logicalBytes);
                writeInt(body,cell.depth);
                writeInt(body,cell.payload.length);
                body.write(digest);
                body.write(cell.payload);
            }finally{
                Arrays.fill(manifestation.bytes,(byte)0);
                Arrays.fill(digest,(byte)0);
            }
        }
        byte[] payload=body.toByteArray();
        byte[] rawBytes=raw.getBytes(StandardCharsets.UTF_8);
        try{
            return PREFIX+archive.escapeRadius()+":"+archive.size()+":"+sha256(rawBytes)+":"+
                Base64.getEncoder().encodeToString(payload);
        }finally{
            Arrays.fill(payload,(byte)0);Arrays.fill(rawBytes,(byte)0);
        }
    }

    private static DecodedArchive unpack(String stored)throws Exception{
        String[] parts=stored.split(":",5);
        if(parts.length!=5||!("@AIOCELL1".equals(parts[0])))throw new SecurityException("AIO_CELL_HEADER");
        int radius=parseInt(parts[1],"AIO_CELL_RADIUS");
        int count=parseInt(parts[2],"AIO_CELL_COUNT");
        if(radius<0||radius>64||count<0||count>MAX_CELLS)throw new SecurityException("AIO_CELL_BOUNDS");
        String hash=parts[3];
        if(!hash.matches("[0-9a-f]{64}"))throw new SecurityException("AIO_CELL_HASH");

        byte[] payload;
        try{payload=Base64.getDecoder().decode(parts[4]);}
        catch(IllegalArgumentException invalid){throw new SecurityException("AIO_CELL_BASE64");}

        ArrayList<AioNativeRepresentationFabric.Cell> cells=new ArrayList<>(count);
        ArrayList<byte[]> cellDigests=new ArrayList<>(count);
        ByteBuffer buffer=ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        long logicalBytes=0,representationBytes=0;
        int raw=0,runs=0,witness=0,mirror=0,maxDepth=0;
        try{
            for(int i=0;i<count;i++){
                if(buffer.remaining()<49)throw new SecurityException("AIO_CELL_TRUNCATED");
                int kindOrdinal=buffer.get()&0xff;
                if(kindOrdinal>=AioNativeRepresentationFabric.Kind.values().length)
                    throw new SecurityException("AIO_CELL_KIND");
                AioNativeRepresentationFabric.Kind kind=AioNativeRepresentationFabric.Kind.values()[kindOrdinal];
                int reference=buffer.getInt(),logical=buffer.getInt(),depth=buffer.getInt(),length=buffer.getInt();
                if(logical<0||logical>MAX_CELL_BYTES||depth<0||depth>radius||length<0||length>MAX_CELL_BYTES||length>buffer.remaining())
                    throw new SecurityException("AIO_CELL_RECORD_BOUNDS");
                if((kind==AioNativeRepresentationFabric.Kind.WITNESS||
                    kind==AioNativeRepresentationFabric.Kind.MIRROR_WITNESS)&&(reference<0||reference>=i))
                    throw new SecurityException("AIO_CELL_REFERENCE");
                if((kind==AioNativeRepresentationFabric.Kind.RAW||kind==AioNativeRepresentationFabric.Kind.RUN_FIELD)&&reference!=-1)
                    throw new SecurityException("AIO_CELL_LOCAL_REFERENCE");
                byte[] digest=new byte[32];buffer.get(digest);
                byte[] cellPayload=new byte[length];buffer.get(cellPayload);
                cells.add(new AioNativeRepresentationFabric.Cell(kind,cellPayload,reference,logical,depth));
                cellDigests.add(digest);
                logicalBytes+=logical;representationBytes+=49L+length;maxDepth=Math.max(maxDepth,depth);
                switch(kind){case RAW:raw++;break;case RUN_FIELD:runs++;break;case WITNESS:witness++;break;case MIRROR_WITNESS:mirror++;break;}
            }
            if(buffer.hasRemaining())throw new SecurityException("AIO_CELL_TRAILING");
            AioNativeRepresentationFabric.Metrics metrics=new AioNativeRepresentationFabric.Metrics(
                count,raw,runs,witness,mirror,maxDepth,logicalBytes,representationBytes);
            return new DecodedArchive(new AioNativeRepresentationFabric.Archive(cells,radius,metrics),hash,payload,cellDigests);
        }catch(Exception failure){
            for(AioNativeRepresentationFabric.Cell cell:cells)Arrays.fill(cell.payload,(byte)0);
            for(byte[] digest:cellDigests)Arrays.fill(digest,(byte)0);
            Arrays.fill(payload,(byte)0);
            throw failure;
        }
    }

    private static int parseInt(String value,String code){
        try{return Integer.parseInt(value);}catch(Exception invalid){throw new SecurityException(code);}
    }

    private static void writeInt(ByteArrayOutputStream out,int value){
        out.write((value>>>24)&0xff);out.write((value>>>16)&0xff);out.write((value>>>8)&0xff);out.write(value&0xff);
    }

    private static byte[] sha256Bytes(byte[] bytes)throws Exception{
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private static String sha256(byte[] bytes)throws Exception{
        byte[] digest=sha256Bytes(bytes);
        try{
            StringBuilder out=new StringBuilder(64);
            for(byte value:digest)out.append(String.format(Locale.ROOT,"%02x",value&0xff));
            return out.toString();
        }finally{Arrays.fill(digest,(byte)0);}
    }

    private static final class DecodedArchive{
        final AioNativeRepresentationFabric.Archive archive;
        final String expectedHash;
        final byte[] encodedPayload;
        final List<byte[]> cellDigests;
        DecodedArchive(AioNativeRepresentationFabric.Archive archive,String expectedHash,byte[] encodedPayload,List<byte[]> cellDigests){
            this.archive=archive;this.expectedHash=expectedHash;this.encodedPayload=encodedPayload;
            this.cellDigests=cellDigests;
        }
        byte[] manifestVerified(int index)throws Exception{
            AioNativeRepresentationFabric.Manifestation manifestation=archive.manifest(index);
            byte[] digest=sha256Bytes(manifestation.bytes);
            try{
                if(!MessageDigest.isEqual(digest,cellDigests.get(index))){
                    Arrays.fill(manifestation.bytes,(byte)0);
                    throw new SecurityException("AIO_CELL_WITNESS_HASH");
                }
                return manifestation.bytes;
            }finally{Arrays.fill(digest,(byte)0);}
        }
        void clear(){
            for(int i=0;i<archive.size();i++)Arrays.fill(archive.cell(i).payload,(byte)0);
            for(byte[] digest:cellDigests)Arrays.fill(digest,(byte)0);
            Arrays.fill(encodedPayload,(byte)0);
        }
    }
}
