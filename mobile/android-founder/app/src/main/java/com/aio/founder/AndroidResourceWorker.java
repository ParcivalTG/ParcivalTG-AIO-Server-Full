package com.aio.founder;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class AndroidResourceWorker {
    static final int MAX_INPUT_BYTES=6*1024;
    static final int MAX_OUTPUT_BYTES=16*1024;

    private AndroidResourceWorker(){}

    static String sha256(String contentB64)throws Exception{
        byte[] input=decode(contentB64);
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(input);
            try{
                StringBuilder out=new StringBuilder(digest.length*2);
                for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&0xff));
                return out.toString();
            }finally{java.util.Arrays.fill(digest,(byte)0);}
        }finally{java.util.Arrays.fill(input,(byte)0);}
    }

    static String deflate(String contentB64,int level)throws Exception{
        if(level<1||level>9)throw new IllegalArgumentException("RESOURCE_DEFLATE_LEVEL_INVALID");
        byte[] input=decode(contentB64);
        Deflater deflater=new Deflater(level,false);
        byte[] output=new byte[MAX_OUTPUT_BYTES];
        try{
            deflater.setInput(input);deflater.finish();
            int length=deflater.deflate(output);
            if(!deflater.finished())throw new IllegalArgumentException("RESOURCE_OUTPUT_BUDGET");
            byte[] exact=java.util.Arrays.copyOf(output,length);
            try{return Base64.getEncoder().encodeToString(exact);}
            finally{java.util.Arrays.fill(exact,(byte)0);}
        }finally{
            deflater.end();
            java.util.Arrays.fill(input,(byte)0);
            java.util.Arrays.fill(output,(byte)0);
        }
    }

    static byte[] inflateForCourt(String compressedB64,int maxBytes)throws Exception{
        byte[] compressed=Base64.getDecoder().decode(compressedB64);
        if(maxBytes<1||maxBytes>MAX_INPUT_BYTES)throw new IllegalArgumentException("RESOURCE_INFLATE_BOUNDS");
        byte[] output=new byte[maxBytes];
        Inflater inflater=new Inflater(false);
        try{
            inflater.setInput(compressed);
            int length=inflater.inflate(output);
            if(!inflater.finished())throw new IllegalArgumentException("RESOURCE_INFLATE_INCOMPLETE");
            return java.util.Arrays.copyOf(output,length);
        }finally{
            inflater.end();
            java.util.Arrays.fill(compressed,(byte)0);
            java.util.Arrays.fill(output,(byte)0);
        }
    }

    private static byte[] decode(String contentB64){
        if(contentB64==null||contentB64.isEmpty()||contentB64.length()>8192)
            throw new IllegalArgumentException("RESOURCE_INPUT_BUDGET");
        byte[] input;
        try{input=Base64.getDecoder().decode(contentB64);}
        catch(IllegalArgumentException failure){throw new IllegalArgumentException("RESOURCE_INPUT_BASE64");}
        if(input.length>MAX_INPUT_BYTES){
            java.util.Arrays.fill(input,(byte)0);
            throw new IllegalArgumentException("RESOURCE_INPUT_BUDGET");
        }
        return input;
    }
}
