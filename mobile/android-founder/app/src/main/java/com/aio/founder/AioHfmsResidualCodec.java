package com.aio.founder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Faithful Android port of the preserved HFR1 HFMS residual representation.
 *
 * Operational defaults match the preserved 2 MiB court:
 * object=32768 bytes, residual block=256 bytes, mask=1 byte.
 * The codec is only considered by the Android portfolio when cheap sensors
 * detect residual-class reuse; final serialized cost remains authoritative.
 */
final class AioHfmsResidualCodec {
    static final int DEFAULT_OBJECT_BYTES=32768;
    static final int DEFAULT_RESIDUAL_BLOCK=256;
    static final int DEFAULT_MASK_BYTES=1;
    private static final int MAX_SOURCE_BYTES=2*1024*1024;

    static final class Encoded {
        final byte[] stream;
        final int uniqueClasses,occurrences;
        Encoded(byte[] stream,int uniqueClasses,int occurrences){
            this.stream=stream;this.uniqueClasses=uniqueClasses;this.occurrences=occurrences;
        }
    }

    private static final class ByteKey {
        final byte[] bytes;
        final int hash;
        ByteKey(byte[] bytes){
            this.bytes=Arrays.copyOf(bytes,bytes.length);
            this.hash=Arrays.hashCode(this.bytes);
        }
        void clear(){Arrays.fill(bytes,(byte)0);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            return other instanceof ByteKey&&Arrays.equals(bytes,((ByteKey)other).bytes);
        }
    }

    private static final class Canonical {
        final byte[] normalized,mask;
        final int orientation;
        Canonical(byte[] normalized,int orientation,byte[] mask){
            this.normalized=normalized;this.orientation=orientation;this.mask=mask;
        }
    }

    private static final class Occurrence {
        final int index,orientation,length;
        final byte[] mask;
        Occurrence(int index,int orientation,byte[] mask,int length){
            this.index=index;this.orientation=orientation;this.mask=mask;this.length=length;
        }
    }

    private AioHfmsResidualCodec(){}

    static boolean sensorEligible(byte[] source){
        if(source==null||source.length<DEFAULT_OBJECT_BYTES*4||
            source.length>MAX_SOURCE_BYTES||source.length%DEFAULT_OBJECT_BYTES!=0)return false;
        int objects=source.length/DEFAULT_OBJECT_BYTES;
        byte[] base=Arrays.copyOfRange(source,0,DEFAULT_OBJECT_BYTES);
        java.util.HashSet<ByteKey> seen=new java.util.HashSet<>();
        int samples=0,duplicates=0;
        try{
            int objectLimit=Math.min(objects,5);
            for(int object=1;object<objectLimit;object++){
                int objectOffset=object*DEFAULT_OBJECT_BYTES;
                // Fine collision fields can recur below 2 KiB spacing; 1 KiB keeps the sensor cheap
                // while preserving the existing 25% duplicate promotion gate.
                for(int off=0;off<DEFAULT_OBJECT_BYTES;off+=1024){
                    int length=Math.min(DEFAULT_RESIDUAL_BLOCK,DEFAULT_OBJECT_BYTES-off);
                    byte[] residual=new byte[length];
                    for(int i=0;i<length;i++)residual[i]=(byte)(source[objectOffset+off+i]^base[off+i]);
                    Canonical canonical=canonical(residual,DEFAULT_MASK_BYTES);
                    ByteKey key=new ByteKey(canonical.normalized);
                    if(!seen.add(key))duplicates++;
                    samples++;
                    Arrays.fill(residual,(byte)0);
                    Arrays.fill(canonical.mask,(byte)0);
                }
            }
            return samples>=16&&duplicates*4>=samples;
        }finally{
            Arrays.fill(base,(byte)0);
            for(ByteKey key:seen)key.clear();
        }
    }

    static Encoded encodeDefault(byte[] source)throws Exception{
        return encode(source,DEFAULT_OBJECT_BYTES,DEFAULT_RESIDUAL_BLOCK,DEFAULT_MASK_BYTES);
    }

    static Encoded encode(byte[] source,int objectBytes,int blockBytes,int maskBytes)throws Exception{
        if(source==null||source.length<2*objectBytes||source.length>MAX_SOURCE_BYTES||
            objectBytes<1||blockBytes<1||blockBytes>objectBytes||maskBytes<1||maskBytes>8||
            source.length%objectBytes!=0)
            throw new IllegalArgumentException("HFMS_HFR1_BOUNDS");

        int objectCount=source.length/objectBytes;
        byte[] base=Arrays.copyOfRange(source,0,objectBytes);
        Map<ByteKey,Integer> dictionaryIndex=new HashMap<>();
        ArrayList<byte[]> dictionary=new ArrayList<>();
        ArrayList<Occurrence> occurrences=new ArrayList<>();
        try{
            for(int object=1;object<objectCount;object++){
                int objectOffset=object*objectBytes;
                for(int off=0;off<objectBytes;off+=blockBytes){
                    int length=Math.min(blockBytes,objectBytes-off);
                    byte[] residual=new byte[length];
                    for(int i=0;i<length;i++)
                        residual[i]=(byte)(source[objectOffset+off+i]^base[off+i]);
                    Canonical canonical=canonical(residual,maskBytes);
                    ByteKey lookup=new ByteKey(canonical.normalized);
                    Integer index=dictionaryIndex.get(lookup);
                    if(index==null){
                        index=dictionary.size();
                        byte[] stored=Arrays.copyOf(canonical.normalized,canonical.normalized.length);
                        dictionary.add(stored);
                        dictionaryIndex.put(new ByteKey(stored),index);
                    }
                    occurrences.add(new Occurrence(index,canonical.orientation,
                        Arrays.copyOf(canonical.mask,canonical.mask.length),length));
                    Arrays.fill(residual,(byte)0);
                    Arrays.fill(canonical.normalized,(byte)0);
                    Arrays.fill(canonical.mask,(byte)0);
                }
            }

            ByteArrayOutputStream out=new ByteArrayOutputStream();
            out.write('H');out.write('F');out.write('R');out.write('1');
            writeIntLE(out,objectBytes);writeIntLE(out,blockBytes);writeIntLE(out,objectCount);
            out.write(maskBytes);out.write(0);out.write(0);out.write(0);
            out.write(base);
            putVarint(out,dictionary.size());putVarint(out,occurrences.size());
            for(byte[] row:dictionary){putVarint(out,row.length);out.write(row);}
            for(Occurrence occurrence:occurrences){
                putVarint(out,occurrence.index);
                out.write(occurrence.orientation);
                putVarint(out,occurrence.mask.length);out.write(occurrence.mask);
                putVarint(out,occurrence.length);
            }
            return new Encoded(out.toByteArray(),dictionary.size(),occurrences.size());
        }finally{
            Arrays.fill(base,(byte)0);
            for(ByteKey key:dictionaryIndex.keySet())key.clear();
            for(byte[] row:dictionary)Arrays.fill(row,(byte)0);
            for(Occurrence occurrence:occurrences)Arrays.fill(occurrence.mask,(byte)0);
        }
    }

    static byte[] decode(byte[] stream)throws Exception{
        if(stream==null||stream.length<20)throw new SecurityException("HFMS_HFR1_HEADER");
        ByteBuffer header=ByteBuffer.wrap(stream,0,20).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic=new byte[4];header.get(magic);
        if(magic[0]!='H'||magic[1]!='F'||magic[2]!='R'||magic[3]!='1')
            throw new SecurityException("HFMS_HFR1_MAGIC");
        int objectBytes=header.getInt(),blockBytes=header.getInt(),objectCount=header.getInt();
        int maskBytes=header.get()&0xff;header.get();header.get();header.get();
        if(objectBytes<1||objectBytes>MAX_SOURCE_BYTES||blockBytes<1||blockBytes>objectBytes||
            objectCount<2||((long)objectBytes*objectCount)>MAX_SOURCE_BYTES||maskBytes<1||maskBytes>8)
            throw new SecurityException("HFMS_HFR1_BOUNDS");

        int[] cursor={20};
        if(cursor[0]+objectBytes>stream.length)throw new SecurityException("HFMS_HFR1_TRUNCATED");
        byte[] base=Arrays.copyOfRange(stream,cursor[0],cursor[0]+objectBytes);cursor[0]+=objectBytes;
        int dictionaryCount=getVarint(stream,cursor),occurrenceCount=getVarint(stream,cursor);
        int per=(objectBytes+blockBytes-1)/blockBytes;
        if(occurrenceCount!=(objectCount-1)*per||dictionaryCount<1||dictionaryCount>occurrenceCount)
            throw new SecurityException("HFMS_HFR1_COUNTS");

        ArrayList<byte[]> dictionary=new ArrayList<>(dictionaryCount);
        ArrayList<Occurrence> occurrences=new ArrayList<>(occurrenceCount);
        try{
            for(int i=0;i<dictionaryCount;i++){
                int length=getVarint(stream,cursor);
                if(length<1||length>blockBytes||cursor[0]+length>stream.length)
                    throw new SecurityException("HFMS_HFR1_DICTIONARY");
                dictionary.add(Arrays.copyOfRange(stream,cursor[0],cursor[0]+length));cursor[0]+=length;
            }
            for(int i=0;i<occurrenceCount;i++){
                int index=getVarint(stream,cursor);
                if(index<0||index>=dictionary.size()||cursor[0]>=stream.length)
                    throw new SecurityException("HFMS_HFR1_OCCURRENCE");
                int orientation=stream[cursor[0]++]&0xff;
                if(orientation>1)throw new SecurityException("HFMS_HFR1_ORIENTATION");
                int keyLength=getVarint(stream,cursor);
                if(keyLength<1||keyLength>8||cursor[0]+keyLength>stream.length)
                    throw new SecurityException("HFMS_HFR1_MASK");
                byte[] mask=Arrays.copyOfRange(stream,cursor[0],cursor[0]+keyLength);cursor[0]+=keyLength;
                int length=getVarint(stream,cursor);
                if(length<1||length>blockBytes||length>dictionary.get(index).length){
                    Arrays.fill(mask,(byte)0);throw new SecurityException("HFMS_HFR1_LENGTH");
                }
                occurrences.add(new Occurrence(index,orientation,mask,length));
            }
            if(cursor[0]!=stream.length)throw new SecurityException("HFMS_HFR1_TRAILING");

            byte[] output=new byte[objectBytes*objectCount];
            System.arraycopy(base,0,output,0,objectBytes);
            int q=0;
            for(int object=1;object<objectCount;object++){
                int objectOffset=object*objectBytes;
                int residualOffset=0;
                for(int chunk=0;chunk<per;chunk++){
                    Occurrence occurrence=occurrences.get(q++);
                    byte[] canonical=dictionary.get(occurrence.index);
                    byte[] residual=new byte[occurrence.length];
                    for(int i=0;i<residual.length;i++)
                        residual[i]=(byte)(canonical[i]^occurrence.mask[i%occurrence.mask.length]);
                    if(occurrence.orientation==1)reverse(residual);
                    for(int i=0;i<residual.length&&residualOffset+i<objectBytes;i++)
                        output[objectOffset+residualOffset+i]=(byte)(base[residualOffset+i]^residual[i]);
                    residualOffset+=residual.length;
                    Arrays.fill(residual,(byte)0);
                }
            }
            return output;
        }finally{
            Arrays.fill(base,(byte)0);
            for(byte[] row:dictionary)Arrays.fill(row,(byte)0);
            for(Occurrence occurrence:occurrences)Arrays.fill(occurrence.mask,(byte)0);
        }
    }

    private static Canonical canonical(byte[] chunk,int maskBytes){
        int m=Math.min(maskBytes,chunk.length);
        byte[] mask0=Arrays.copyOf(chunk,m);
        byte[] normalized0=xorRepeat(chunk,mask0);

        byte[] reversed=Arrays.copyOf(chunk,chunk.length);reverse(reversed);
        byte[] mask1=Arrays.copyOf(reversed,m);
        byte[] normalized1=xorRepeat(reversed,mask1);
        Arrays.fill(reversed,(byte)0);

        if(compareUnsigned(normalized1,normalized0)<0){
            Arrays.fill(normalized0,(byte)0);Arrays.fill(mask0,(byte)0);
            return new Canonical(normalized1,1,mask1);
        }
        Arrays.fill(normalized1,(byte)0);Arrays.fill(mask1,(byte)0);
        return new Canonical(normalized0,0,mask0);
    }

    private static byte[] xorRepeat(byte[] input,byte[] mask){
        byte[] out=new byte[input.length];
        for(int i=0;i<input.length;i++)out[i]=(byte)(input[i]^mask[i%mask.length]);
        return out;
    }

    private static int compareUnsigned(byte[] a,byte[] b){
        int length=Math.min(a.length,b.length);
        for(int i=0;i<length;i++){
            int av=a[i]&0xff,bv=b[i]&0xff;
            if(av!=bv)return Integer.compare(av,bv);
        }
        return Integer.compare(a.length,b.length);
    }

    private static void reverse(byte[] bytes){
        for(int a=0,b=bytes.length-1;a<b;a++,b--){byte t=bytes[a];bytes[a]=bytes[b];bytes[b]=t;}
    }

    private static void putVarint(ByteArrayOutputStream out,int value){
        int n=value;
        while(true){
            int b=n&0x7f;n>>>=7;
            if(n!=0)out.write(b|0x80);
            else{out.write(b);return;}
        }
    }

    private static int getVarint(byte[] data,int[] cursor)throws Exception{
        int n=0,shift=0;
        for(int count=0;count<5;count++){
            if(cursor[0]>=data.length)throw new SecurityException("HFMS_HFR1_VARINT_TRUNCATED");
            int b=data[cursor[0]++]&0xff;
            n|=(b&0x7f)<<shift;
            if((b&0x80)==0)return n;
            shift+=7;
        }
        throw new SecurityException("HFMS_HFR1_VARINT_OVERFLOW");
    }

    private static void writeIntLE(ByteArrayOutputStream out,int value){
        out.write(value&0xff);out.write((value>>>8)&0xff);out.write((value>>>16)&0xff);out.write((value>>>24)&0xff);
    }
}
