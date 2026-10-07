package com.aio.founder;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.RandomAccessFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AioNativeJournalInstrumentationTest {
    @Test public void androidKeystoreJournalSelectivelyReconstructsExactTail()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        File file=new File(context.getFilesDir(),"instrumentation-aio-native.aioj");
        if(file.exists())assertTrue(file.delete());
        AioSelectiveJournal journal=new AioSelectiveJournal(file,new AioJournalCipher("instrumentation"));
        try{
            for(int i=0;i<512;i++)
                journal.append("{\"index\":"+i+",\"state\":\"READY\",\"payload\":\""+("x".repeat(96))+"\"}",
                    1_800_000_000_000L+i);

            AioSelectiveJournal.TailResult tail=journal.tail(4);
            assertEquals(4,tail.entries.size());
            assertEquals(512,journal.countRecords());
            assertTrue(tail.entries.get(3).value.contains("\"index\":511"));
            assertTrue("selective bytes="+tail.bytesRead+" / "+tail.fileBytes,
                tail.materializationFraction()<0.02);
        }finally{journal.clear();}
    }

    @Test public void androidKeystoreJournalRejectsCiphertextTamper()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        File file=new File(context.getFilesDir(),"instrumentation-aio-native-tamper.aioj");
        if(file.exists())assertTrue(file.delete());
        AioSelectiveJournal journal=new AioSelectiveJournal(file,new AioJournalCipher("instrumentation-tamper"));
        try{
            journal.append("trade-secret-state",1_800_000_000_000L);
            try(RandomAccessFile raf=new RandomAccessFile(file,"rw")){
                long position=Math.max(12,raf.length()/2);
                raf.seek(position);
                int value=raf.read();
                assertTrue(value>=0);
                raf.seek(position);
                raf.write(value^0x01);
            }
            try{journal.tail(1);fail("tampered record must fail authentication");}
            catch(Exception expected){assertNotNull(expected);}
        }finally{journal.clear();}
    }
}
