package com.aio.founder;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reverse-addressable append-only AIO journal.
 *
 * Each record is independently sealed. Tail queries walk framing footers backward and
 * materialize only the requested causal suffix instead of reconstructing the whole file.
 */
final class AioSelectiveJournal {
    interface RecordCipher {
        byte[] seal(byte[] plaintext)throws Exception;
        byte[] open(byte[] sealed)throws Exception;
    }

    private static final int MAGIC=0x41494F4A; // AIOJ
    private static final int VERSION=1;
    private static final int OUTER_HEADER_BYTES=9;
    private static final int FOOTER_BYTES=4;
    private static final int INNER_HEADER_BYTES=44;
    private static final int MAX_RECORD_BYTES=768*1024;
    private static final long MAX_FILE_BYTES=16L*1024L*1024L;

    static final class Entry {
        final long timestampUnixMs;
        final String value;
        final String representation;
        final int sealedBytes;
        Entry(long timestampUnixMs,String value,String representation,int sealedBytes){
            this.timestampUnixMs=timestampUnixMs;this.value=value;this.representation=representation;this.sealedBytes=sealedBytes;
        }
    }

    static final class TailResult {
        final List<Entry> entries;
        final long fileBytes,bytesRead;
        TailResult(List<Entry> entries,long fileBytes,long bytesRead){
            this.entries=Collections.unmodifiableList(entries);this.fileBytes=fileBytes;this.bytesRead=bytesRead;
        }
        double materializationFraction(){return fileBytes<=0?0.0:(double)bytesRead/fileBytes;}
    }

    private final File file;
    private final RecordCipher cipher;

    AioSelectiveJournal(File file,RecordCipher cipher){
        if(file==null||cipher==null)throw new IllegalArgumentException("AIO_JOURNAL_DEPENDENCY");
        this.file=file;this.cipher=cipher;
    }

    synchronized void append(String value,long timestampUnixMs)throws Exception{
        if(value==null)throw new IllegalArgumentException("AIO_JOURNAL_VALUE_REQUIRED");
        if(timestampUnixMs<=0)throw new IllegalArgumentException("AIO_JOURNAL_TIME_INVALID");
        String represented=AioNativeStateCodec.encodeForStorage(value);
        byte[] raw=value.getBytes(StandardCharsets.UTF_8);
        byte[] representedBytes=represented.getBytes(StandardCharsets.UTF_8);
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(raw);
        byte[] plain=new byte[INNER_HEADER_BYTES+representedBytes.length];
        ByteBuffer inner=ByteBuffer.wrap(plain);
        inner.putLong(timestampUnixMs).putInt(raw.length).put(hash).put(representedBytes);
        byte[] sealed=null;
        try{
            sealed=cipher.seal(plain);
            if(sealed==null||sealed.length<1||sealed.length>MAX_RECORD_BYTES)
                throw new IllegalArgumentException("AIO_JOURNAL_RECORD_BUDGET");
            ensureParent();
            try(RandomAccessFile raf=new RandomAccessFile(file,"rw")){
                raf.seek(raf.length());
                int total=OUTER_HEADER_BYTES+sealed.length+FOOTER_BYTES;
                raf.writeInt(MAGIC);raf.writeByte(VERSION);raf.writeInt(sealed.length);
                raf.write(sealed);raf.writeInt(total);
                raf.getFD().sync();
            }
            if(file.length()>MAX_FILE_BYTES)compactTail(1024);
        }finally{
            Arrays.fill(raw,(byte)0);Arrays.fill(representedBytes,(byte)0);
            Arrays.fill(hash,(byte)0);Arrays.fill(plain,(byte)0);
            if(sealed!=null)Arrays.fill(sealed,(byte)0);
        }
    }

    synchronized TailResult tail(int limit)throws Exception{
        if(limit<0||limit>4096)throw new IllegalArgumentException("AIO_JOURNAL_LIMIT");
        long fileBytes=file.isFile()?file.length():0;
        if(limit==0||fileBytes==0)return new TailResult(List.of(),fileBytes,0);
        List<Entry> reversed=new ArrayList<>();
        long bytesRead=0;
        try(RandomAccessFile raf=new RandomAccessFile(file,"r")){
            long cursor=fileBytes;
            while(cursor>0&&reversed.size()<limit){
                if(cursor<FOOTER_BYTES)throw new SecurityException("AIO_JOURNAL_TRUNCATED");
                raf.seek(cursor-FOOTER_BYTES);
                int total=raf.readInt();bytesRead+=FOOTER_BYTES;
                if(total<OUTER_HEADER_BYTES+1+FOOTER_BYTES||total>MAX_RECORD_BYTES+OUTER_HEADER_BYTES+FOOTER_BYTES)
                    throw new SecurityException("AIO_JOURNAL_FRAME_BOUNDS");
                long start=cursor-total;
                if(start<0)throw new SecurityException("AIO_JOURNAL_FRAME_BOUNDS");
                raf.seek(start);
                int magic=raf.readInt();int version=raf.readUnsignedByte();int sealedLength=raf.readInt();
                bytesRead+=OUTER_HEADER_BYTES;
                if(magic!=MAGIC||version!=VERSION||sealedLength!=total-OUTER_HEADER_BYTES-FOOTER_BYTES)
                    throw new SecurityException("AIO_JOURNAL_FRAME_HEADER");
                byte[] sealed=new byte[sealedLength];
                raf.readFully(sealed);bytesRead+=sealedLength;
                byte[] plain=null;
                try{
                    plain=cipher.open(sealed);
                    reversed.add(decodeEntry(plain,sealedLength));
                }finally{
                    Arrays.fill(sealed,(byte)0);
                    if(plain!=null)Arrays.fill(plain,(byte)0);
                }
                cursor=start;
            }
        }
        Collections.reverse(reversed);
        return new TailResult(reversed,fileBytes,bytesRead);
    }

    synchronized int countRecords()throws Exception{
        if(!file.isFile())return 0;
        int count=0;
        try(RandomAccessFile raf=new RandomAccessFile(file,"r")){
            long cursor=raf.length();
            while(cursor>0){
                if(cursor<FOOTER_BYTES)throw new SecurityException("AIO_JOURNAL_TRUNCATED");
                raf.seek(cursor-FOOTER_BYTES);
                int total=raf.readInt();
                if(total<OUTER_HEADER_BYTES+1+FOOTER_BYTES||total>MAX_RECORD_BYTES+OUTER_HEADER_BYTES+FOOTER_BYTES)
                    throw new SecurityException("AIO_JOURNAL_FRAME_BOUNDS");
                cursor-=total;
                if(cursor<0)throw new SecurityException("AIO_JOURNAL_FRAME_BOUNDS");
                count++;
            }
        }
        return count;
    }

    synchronized void clear(){
        if(file.exists()&&!file.delete())throw new IllegalStateException("AIO_JOURNAL_CLEAR_FAILED");
    }

    private Entry decodeEntry(byte[] plain,int sealedLength)throws Exception{
        if(plain==null||plain.length<INNER_HEADER_BYTES)throw new SecurityException("AIO_JOURNAL_INNER_HEADER");
        ByteBuffer inner=ByteBuffer.wrap(plain);
        long timestamp=inner.getLong();int rawLength=inner.getInt();
        if(timestamp<=0||rawLength<0||rawLength>AioNativeStateCodec.MAX_RAW_BYTES)
            throw new SecurityException("AIO_JOURNAL_INNER_BOUNDS");
        byte[] expectedHash=new byte[32];inner.get(expectedHash);
        byte[] representedBytes=new byte[inner.remaining()];inner.get(representedBytes);
        try{
            String represented=new String(representedBytes,StandardCharsets.UTF_8);
            String value=AioNativeStateCodec.decodeFromStorage(represented);
            byte[] raw=value.getBytes(StandardCharsets.UTF_8);
            try{
                if(raw.length!=rawLength)throw new SecurityException("AIO_JOURNAL_RAW_LENGTH");
                byte[] actual=MessageDigest.getInstance("SHA-256").digest(raw);
                try{if(!MessageDigest.isEqual(expectedHash,actual))throw new SecurityException("AIO_JOURNAL_HASH");}
                finally{Arrays.fill(actual,(byte)0);}
            }finally{Arrays.fill(raw,(byte)0);}
            return new Entry(timestamp,value,AioNativeStateCodec.describeStored(represented),sealedLength);
        }finally{
            Arrays.fill(expectedHash,(byte)0);Arrays.fill(representedBytes,(byte)0);
        }
    }

    private void compactTail(int keep)throws Exception{
        TailResult suffix=tail(keep);
        File temp=new File(file.getParentFile(),file.getName()+".compact");
        if(temp.exists()&&!temp.delete())throw new IllegalStateException("AIO_JOURNAL_COMPACT_TEMP");
        AioSelectiveJournal replacement=new AioSelectiveJournal(temp,cipher);
        for(Entry entry:suffix.entries)replacement.append(entry.value,entry.timestampUnixMs);
        if(file.exists()&&!file.delete()){temp.delete();throw new IllegalStateException("AIO_JOURNAL_COMPACT_DELETE");}
        if(!temp.renameTo(file))throw new IllegalStateException("AIO_JOURNAL_COMPACT_RENAME");
    }

    private void ensureParent(){
        File parent=file.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IllegalStateException("AIO_JOURNAL_DIRECTORY");
    }
}
