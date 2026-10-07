package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidRemoteFilePolicyTest {
    @Test public void boundedContentDecodes(){
        byte[] bytes=AndroidRemoteFilePolicy.decodeContent(Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)));
        try{assertEquals("abc",new String(bytes,StandardCharsets.UTF_8));}
        finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
    @Test public void oversizeWriteRejected(){
        String encoded=Base64.getEncoder().encodeToString(new byte[AndroidRemoteFilePolicy.MAX_WRITE_BYTES+1]);
        try{AndroidRemoteFilePolicy.decodeContent(encoded);fail();}
        catch(IllegalArgumentException expected){assertEquals("FILE_WRITE_CONTENT_BUDGET",expected.getMessage());}
    }
    @Test public void mimeIsBounded(){
        assertEquals("application/octet-stream",AndroidRemoteFilePolicy.mime(""));
        assertEquals("text/plain",AndroidRemoteFilePolicy.mime(" text/plain "));
        try{AndroidRemoteFilePolicy.mime("bad mime");fail();}
        catch(IllegalArgumentException expected){assertEquals("FILE_WRITE_MIME_INVALID",expected.getMessage());}
    }
}
