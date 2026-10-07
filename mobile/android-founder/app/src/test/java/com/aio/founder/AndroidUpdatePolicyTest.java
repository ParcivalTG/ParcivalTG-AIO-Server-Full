package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidUpdatePolicyTest {
    private static final String SIGNER="a".repeat(64);

    @Test public void validSelfUpdateAccepted(){
        AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,7,10_000_000,true,SIGNER,SIGNER);
    }
    @Test public void foreignPackageRejected(){
        try{AndroidUpdatePolicy.validate("com.aio.founder","example.other",6,7,1000,true,SIGNER,SIGNER);fail();}
        catch(SecurityException expected){assertEquals("UPDATE_PACKAGE_MISMATCH",expected.getMessage());}
    }
    @Test public void downgradeRejected(){
        try{AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,6,1000,true,SIGNER,SIGNER);fail();}
        catch(SecurityException expected){assertEquals("UPDATE_VERSION_NOT_NEWER",expected.getMessage());}
    }
    @Test public void signerMismatchRejected(){
        try{AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,7,1000,false,SIGNER,SIGNER);fail();}
        catch(SecurityException expected){assertEquals("UPDATE_SIGNER_MISMATCH",expected.getMessage());}
    }
    @Test public void pinnedSignerMismatchRejected(){
        try{AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,7,1000,true,SIGNER,"b".repeat(64));fail();}
        catch(SecurityException expected){assertEquals("UPDATE_PINNED_SIGNER_MISMATCH",expected.getMessage());}
    }
    @Test public void emptyExpectedSignerAllowsDebugCourtContinuity(){
        AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,7,1000,true,"",SIGNER);
    }
    @Test public void malformedPinnedSignerRejected(){
        try{AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,7,1000,true,"not-a-fingerprint",SIGNER);fail();}
        catch(IllegalArgumentException expected){assertEquals("UPDATE_SIGNER_FINGERPRINT_INVALID",expected.getMessage());}
    }
    @Test public void oversizeRejected(){
        try{AndroidUpdatePolicy.validate("com.aio.founder","com.aio.founder",6,7,AndroidUpdatePolicy.MAX_APK_BYTES+1,true,SIGNER,SIGNER);fail();}
        catch(IllegalArgumentException expected){assertEquals("UPDATE_APK_SIZE_INVALID",expected.getMessage());}
    }
}
