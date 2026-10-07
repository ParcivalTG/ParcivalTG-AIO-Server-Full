package com.aio.founder;

final class AndroidUpdatePolicy {
    static final long MAX_APK_BYTES=128L*1024L*1024L;
    private AndroidUpdatePolicy(){}

    static void validate(String expectedPackage,String actualPackage,long currentVersion,long candidateVersion,
                         long bytes,boolean signerMatch,String expectedSignerSha256,String candidateSignerSha256){
        if(expectedPackage==null||!expectedPackage.equals(actualPackage))
            throw new SecurityException("UPDATE_PACKAGE_MISMATCH");
        if(bytes<1||bytes>MAX_APK_BYTES)throw new IllegalArgumentException("UPDATE_APK_SIZE_INVALID");
        if(candidateVersion<=currentVersion)throw new SecurityException("UPDATE_VERSION_NOT_NEWER");
        if(!signerMatch)throw new SecurityException("UPDATE_SIGNER_MISMATCH");

        String expected=normalizeSigner(expectedSignerSha256);
        String candidate=normalizeSigner(candidateSignerSha256);
        if(!expected.isEmpty()&&!expected.equals(candidate))
            throw new SecurityException("UPDATE_PINNED_SIGNER_MISMATCH");
    }

    static String normalizeSigner(String value){
        if(value==null)return "";
        String clean=value.replaceAll("[^0-9A-Fa-f]","").toLowerCase(java.util.Locale.ROOT);
        if(clean.isEmpty())return "";
        if(!clean.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("UPDATE_SIGNER_FINGERPRINT_INVALID");
        return clean;
    }
}
