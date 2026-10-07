package com.aio.founder;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AndroidSigningIdentity {
    private AndroidSigningIdentity(){}

    static boolean matchesExpectedRelease(Context context){
        if(BuildConfig.DEBUG)return false;
        String expected=normalize(BuildConfig.EXPECTED_SIGNER_SHA256);
        if(expected.length()!=64)return false;
        try{return currentSignerSha256(context).contains(expected);}
        catch(Exception failure){return false;}
    }

    static List<String> currentSignerSha256(Context context)throws Exception{
        PackageInfo info=context.getPackageManager().getPackageInfo(
            context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(info.signingInfo==null)throw new SecurityException("SIGNER_INFO_UNAVAILABLE");
        Signature[] signatures=info.signingInfo.getApkContentsSigners();
        if(signatures==null||signatures.length==0)throw new SecurityException("SIGNER_INFO_UNAVAILABLE");
        ArrayList<String> out=new ArrayList<>();
        for(Signature signature:signatures){
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            try{out.add(hex(digest));}
            finally{java.util.Arrays.fill(digest,(byte)0);}
        }
        Collections.sort(out);
        return Collections.unmodifiableList(out);
    }

    static String normalize(String value){
        if(value==null)return "";
        return value.replace(":","").replace(" ","").trim().toLowerCase(java.util.Locale.ROOT);
    }

    static boolean matches(String expected,List<String> actual){
        String normalized=normalize(expected);
        if(normalized.length()!=64||actual==null)return false;
        for(String value:actual)if(normalized.equals(normalize(value)))return true;
        return false;
    }

    private static String hex(byte[] bytes){
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte value:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",value&0xff));
        return out.toString();
    }
}
