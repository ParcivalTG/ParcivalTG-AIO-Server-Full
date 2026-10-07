package com.aio.founder;

import java.util.Base64;

final class AndroidRemoteFilePolicy {
    static final int MAX_WRITE_BYTES=6*1024;
    private AndroidRemoteFilePolicy(){}

    static byte[] decodeContent(String contentB64){
        if(contentB64==null||contentB64.isEmpty()||contentB64.length()>8192)
            throw new IllegalArgumentException("FILE_WRITE_CONTENT_BUDGET");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(contentB64);}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("FILE_WRITE_CONTENT_BASE64");}
        if(bytes.length>MAX_WRITE_BYTES){
            java.util.Arrays.fill(bytes,(byte)0);
            throw new IllegalArgumentException("FILE_WRITE_CONTENT_BUDGET");
        }
        return bytes;
    }

    static String mime(String value){
        if(value==null||value.isBlank())return "application/octet-stream";
        String clean=value.trim();
        if(clean.length()>128||!clean.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+"))
            throw new IllegalArgumentException("FILE_WRITE_MIME_INVALID");
        return clean;
    }
}
