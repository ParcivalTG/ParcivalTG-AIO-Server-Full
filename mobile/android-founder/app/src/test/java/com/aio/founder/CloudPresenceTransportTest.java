package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class CloudPresenceTransportTest {
    @Test public void admissionSignatureMatchesCrossLanguageVector() throws Exception {
        byte[] key=new byte[32];
        for(int i=0;i<key.length;i++)key[i]=(byte)i;
        String actual=CloudPresenceTransport.admissionSignature(
            key,"android-founder",1700000000000L,"abcdefghijklmnop");
        assertEquals("4e08f537fc2ed037c152020c3dfc38f5bd322a4a83a3f4662c63639109198f89",actual);
    }

    @Test(expected=IllegalArgumentException.class)
    public void cleartextRendezvousRejected(){
        byte[] key=new byte[32];
        new CloudPresenceTransport("ws://example.invalid/v1/peer","android-founder",key,key,"windows-founder");
    }

    @Test(expected=IllegalArgumentException.class)
    public void wrongLengthKeyRejected(){
        new CloudPresenceTransport("wss://example.invalid/v1/peer","android-founder",new byte[31],new byte[32],"windows-founder");
    }
}
