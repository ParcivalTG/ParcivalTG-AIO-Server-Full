package com.aio.founder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AndroidUpdateStager {
    private static final String PREFS="aio_update_stage_v1";
    private static final String DIR="updates";
    private static final String FILE_NAME="pending.apk";

    static final class StageResult {
        final String packageName,sha256,signerSha256;
        final long versionCode,bytes,stagedAtMs;
        StageResult(String packageName,long versionCode,long bytes,String sha256,String signerSha256,long stagedAtMs){
            this.packageName=packageName;this.versionCode=versionCode;this.bytes=bytes;
            this.sha256=sha256;this.signerSha256=signerSha256;this.stagedAtMs=stagedAtMs;
        }
        String summary(){
            return packageName+" v"+versionCode+" | "+bytes+" bytes | sha256="+sha256;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    AndroidUpdateStager(Context context){
        this.context=context.getApplicationContext();
        this.prefs=this.context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    StageResult stage(Uri source)throws Exception{
        if(source==null)throw new IllegalArgumentException("UPDATE_SOURCE_REQUIRED");
        File directory=new File(context.getFilesDir(),DIR);
        if(!directory.exists()&&!directory.mkdirs())throw new java.io.IOException("UPDATE_STAGE_DIRECTORY_FAILED");
        File temp=new File(directory,"pending.tmp");
        File target=new File(directory,FILE_NAME);
        if(temp.exists()&&!temp.delete())throw new java.io.IOException("UPDATE_STAGE_TEMP_CLEAN_FAILED");

        long total=0;
        try(InputStream in=context.getContentResolver().openInputStream(source);
            FileOutputStream out=new FileOutputStream(temp,false)){
            if(in==null)throw new java.io.IOException("UPDATE_SOURCE_OPEN_FAILED");
            byte[] buffer=new byte[64*1024];
            try{
                for(int read;(read=in.read(buffer))>=0;){
                    if(read==0)continue;
                    total+=read;
                    if(total>AndroidUpdatePolicy.MAX_APK_BYTES)throw new IllegalArgumentException("UPDATE_APK_SIZE_INVALID");
                    out.write(buffer,0,read);
                }
                out.flush();out.getFD().sync();
            }finally{Arrays.fill(buffer,(byte)0);}
        }catch(Exception failure){
            temp.delete();throw failure;
        }
        if(total<1){temp.delete();throw new IllegalArgumentException("UPDATE_APK_SIZE_INVALID");}

        StageResult verified;
        try{verified=inspectFile(temp);}
        catch(Exception failure){temp.delete();throw failure;}

        if(target.exists()&&!target.delete()){temp.delete();throw new java.io.IOException("UPDATE_STAGE_REPLACE_FAILED");}
        if(!temp.renameTo(target)){temp.delete();throw new java.io.IOException("UPDATE_STAGE_COMMIT_FAILED");}
        save(verified);
        return verified;
    }

    StageResult verifyPending()throws Exception{
        File file=pendingFile();
        if(!file.isFile())throw new IllegalStateException("UPDATE_NOT_STAGED");
        StageResult verified=inspectFile(file);
        save(verified);return verified;
    }

    StageResult peek(){
        File file=pendingFile();
        if(!file.isFile()){prefs.edit().clear().apply();return null;}
        String packageName=prefs.getString("package",null);
        String sha=prefs.getString("sha256",null);
        String signer=prefs.getString("signer",null);
        long version=prefs.getLong("version",-1),bytes=prefs.getLong("bytes",-1),at=prefs.getLong("stagedAt",-1);
        if(packageName==null||sha==null||signer==null||version<0||bytes<1)return null;
        return new StageResult(packageName,version,bytes,sha,signer,at);
    }

    File pendingFile(){return new File(new File(context.getFilesDir(),DIR),FILE_NAME);}

    void clear(){
        File file=pendingFile();if(file.exists())file.delete();
        prefs.edit().clear().apply();
    }

    private StageResult inspectFile(File file)throws Exception{
        PackageManager pm=context.getPackageManager();
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||candidate.packageName==null||candidate.signingInfo==null)
            throw new SecurityException("UPDATE_APK_INVALID");
        PackageInfo current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(current.signingInfo==null)throw new SecurityException("UPDATE_CURRENT_SIGNER_UNAVAILABLE");

        Set<String> currentSigners=digests(current.signingInfo.getApkContentsSigners());
        Set<String> candidateSigners=digests(candidate.signingInfo.getApkContentsSigners());
        boolean signerMatch=!currentSigners.isEmpty()&&currentSigners.equals(candidateSigners);
        long currentVersion=current.getLongVersionCode(),candidateVersion=candidate.getLongVersionCode();
        String signer=candidateSigners.isEmpty()?"":candidateSigners.iterator().next();
        AndroidUpdatePolicy.validate(
            context.getPackageName(),
            candidate.packageName,
            currentVersion,
            candidateVersion,
            file.length(),
            signerMatch,
            BuildConfig.EXPECTED_SIGNER_SHA256,
            signer);

        String sha=fileSha256(file);
        return new StageResult(candidate.packageName,candidateVersion,file.length(),sha,signer,System.currentTimeMillis());
    }

    private void save(StageResult result){
        prefs.edit().putString("package",result.packageName).putLong("version",result.versionCode)
            .putLong("bytes",result.bytes).putString("sha256",result.sha256)
            .putString("signer",result.signerSha256).putLong("stagedAt",result.stagedAtMs).apply();
    }

    private static Set<String> digests(Signature[] signatures)throws Exception{
        Set<String> out=new HashSet<>();
        if(signatures==null)return out;
        for(Signature signature:signatures){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            out.add(hex(digest.digest(signature.toByteArray())));
        }
        return out;
    }

    private static String fileSha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new java.io.FileInputStream(file)){
            byte[] buffer=new byte[64*1024];
            try{for(int read;(read=in.read(buffer))>=0;)if(read>0)digest.update(buffer,0,read);}
            finally{Arrays.fill(buffer,(byte)0);}
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes){
        StringBuilder out=new StringBuilder(bytes.length*2);
        try{for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));return out.toString();}
        finally{Arrays.fill(bytes,(byte)0);}
    }
}
