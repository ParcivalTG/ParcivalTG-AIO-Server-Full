package com.aio.founder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class AioSelectiveJournalTest {
    static final class XorCipher implements AioSelectiveJournal.RecordCipher {
        @Override public byte[] seal(byte[] plain){
            byte[] out=Arrays.copyOf(plain,plain.length);
            for(int i=0;i<out.length;i++)out[i]^=(byte)0xA5;
            return out;
        }
        @Override public byte[] open(byte[] sealed){return seal(sealed);}
    }

    private File temp()throws Exception{
        File file=Files.createTempFile("aio-journal",".bin").toFile();
        assertTrue(file.delete());return file;
    }

    @Test public void tailReconstructsOnlyRequestedSuffix()throws Exception{
        File file=temp();AioSelectiveJournal journal=new AioSelectiveJournal(file,new XorCipher());
        try{
            for(int i=0;i<1000;i++)journal.append("{\"i\":"+i+",\"state\":\"READY\"}",1800000000000L+i);
            AioSelectiveJournal.TailResult tail=journal.tail(8);
            assertEquals(8,tail.entries.size());
            assertEquals("{\"i\":999,\"state\":\"READY\"}",tail.entries.get(7).value);
            assertTrue(tail.bytesRead<tail.fileBytes/50);
            assertTrue(tail.materializationFraction()<0.02);
            assertEquals(1000,journal.countRecords());
        }finally{journal.clear();}
    }

    @Test public void exactRoundTripAcrossRepresentationPortfolio()throws Exception{
        File file=temp();AioSelectiveJournal journal=new AioSelectiveJournal(file,new XorCipher());
        try{
            String structural="{\"role\":\"Founder\",\"provider\":\"AIO\",\"state\":\"READY\"}".repeat(30);
            String generator="ABC123|".repeat(500);
            journal.append(structural,1);journal.append(generator,2);
            AioSelectiveJournal.TailResult tail=journal.tail(2);
            assertEquals(structural,tail.entries.get(0).value);
            assertEquals(generator,tail.entries.get(1).value);
        }finally{journal.clear();}
    }

    @Test public void corruptionFailsClosed()throws Exception{
        File file=temp();AioSelectiveJournal journal=new AioSelectiveJournal(file,new XorCipher());
        try{
            journal.append("critical-state",1);
            try(java.io.RandomAccessFile raf=new java.io.RandomAccessFile(file,"rw")){
                long position=Math.max(10,raf.length()/2);
                raf.seek(position);
                int value=raf.read();
                raf.seek(position);
                raf.write(value^1);
            }
            try{journal.tail(1);fail();}
            catch(Exception expected){assertTrue(expected instanceof SecurityException||expected instanceof java.util.zip.DataFormatException);}
        }finally{journal.clear();}
    }

    @Test public void zeroTailMaterializesNothing()throws Exception{
        File file=temp();AioSelectiveJournal journal=new AioSelectiveJournal(file,new XorCipher());
        try{
            journal.append("x",1);
            AioSelectiveJournal.TailResult result=journal.tail(0);
            assertTrue(result.entries.isEmpty());assertEquals(0,result.bytesRead);
        }finally{journal.clear();}
    }
}
