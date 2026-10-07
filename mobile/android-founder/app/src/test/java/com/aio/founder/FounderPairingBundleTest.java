package com.aio.founder;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class FounderPairingBundleTest {
    private Properties golden()throws Exception{
        Properties result=new Properties();
        try(InputStream in=getClass().getResourceAsStream("/dotnet-e2e-golden.properties")){
            assertNotNull(in);result.load(in);
        }
        return result;
    }

    private static String quote(String value){
        return value.replace("\\","\\\\").replace("\"","\\\"");
    }

    private String lease(long now){
        String expires=Instant.ofEpochMilli(now+30*60_000L).atOffset(ZoneOffset.UTC).toString();
        return "{\"schema\":\"aio.private-gateway.lease.v1\","+
            "\"leaseId\":\"test-lease\",\"principalId\":\"founder\",\"signature\":\"TEST_ONLY_NOT_AUTHORITY\","+
            "\"maxUses\":10,\"capabilities\":[\""+FounderIntentCapsule.ACTION+"\"],\"expiresAt\":\""+expires+"\"}";
    }

    private String bundle(long issued,long expires,String direct,String admission,String tunnel,boolean cloud,boolean extra)throws Exception{
        String witness=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        String pin=golden().getProperty("serverPublicKeyB64");
        String cloudJson=cloud?
            "{\"wssUrl\":\"wss://r5.example.test/v1/peer\",\"androidPeerId\":\"android-founder\","+
            "\"windowsPeerId\":\"windows-founder\",\"peerAdmissionKeyB64\":\""+admission+
            "\",\"tunnelKeyB64\":\""+tunnel+"\"}":"null";
        return "{\"schema\":\"aio.founder.android.pairing.v1\",\"issuedAtUnixMs\":"+issued+
            ",\"expiresAtUnixMs\":"+expires+",\"clientId\":\"android-founder\",\"directEndpoint\":"+
            (direct==null?"null":"\""+quote(direct)+"\"")+
            ",\"witnessSecretB64\":\""+witness+"\",\"pinnedGatewaySpkiB64Url\":\""+quote(pin)+
            "\",\"signedLeaseJson\":\""+quote(lease(issued))+"\",\"cloud\":"+cloudJson+
            (extra?",\"extra\":true":"")+"}";
    }

    @Test public void completeCloudBundleParsesAtomically()throws Exception{
        long now=1_800_000_000_000L;
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        FounderPairingBundle parsed=FounderPairingBundle.parse(
            bundle(now,now+10*60_000L,null,key,key,true,false),now);
        assertEquals("android-founder",parsed.clientId);
        assertEquals("",parsed.directEndpoint);
        assertNotNull(parsed.cloud);
        assertEquals("windows-founder",parsed.cloud.windowsPeerId);
        assertEquals("wss://r5.example.test/v1/peer",parsed.cloud.wssUrl);
    }

    @Test public void directOnlyBundleParses()throws Exception{
        long now=1_800_000_000_000L;
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        FounderPairingBundle parsed=FounderPairingBundle.parse(
            bundle(now,now+10*60_000L,"192.168.1.10:47103",key,key,false,false),now);
        assertEquals("192.168.1.10:47103",parsed.directEndpoint);
        assertNull(parsed.cloud);
    }

    @Test public void expiredBundleFailsBeforeImport()throws Exception{
        long now=1_800_000_000_000L;
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        try{
            FounderPairingBundle.parse(bundle(now-20*60_000L,now-10*60_000L,null,key,key,true,false),now);
            fail();
        }catch(SecurityException expected){
            assertEquals("PAIRING_BUNDLE_EXPIRED_OR_TIME_INVALID",expected.getMessage());
        }
    }

    @Test public void unknownTopLevelFieldFailsClosed()throws Exception{
        long now=1_800_000_000_000L;
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        try{
            FounderPairingBundle.parse(bundle(now,now+10*60_000L,null,key,key,true,true),now);
            fail();
        }catch(IllegalArgumentException expected){
            assertEquals("PAIRING_BUNDLE_KEYS",expected.getMessage());
        }
    }

    @Test public void cloudKeyLengthIsExactly32Bytes()throws Exception{
        long now=1_800_000_000_000L;
        String bad=Base64.getEncoder().encodeToString(new byte[31]);
        String good=Base64.getEncoder().encodeToString(new byte[32]);
        try{
            FounderPairingBundle.parse(bundle(now,now+10*60_000L,null,bad,good,true,false),now);
            fail();
        }catch(SecurityException expected){
            assertEquals("PAIRING_CLOUD_ADMISSION_INVALID",expected.getMessage());
        }
    }

    @Test public void bundleMustContainDirectOrCloudRoute()throws Exception{
        long now=1_800_000_000_000L;
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        try{
            FounderPairingBundle.parse(bundle(now,now+10*60_000L,null,key,key,false,false),now);
            fail();
        }catch(SecurityException expected){
            assertEquals("PRESENCE_ROUTE_REQUIRED",expected.getMessage());
        }
    }
}
