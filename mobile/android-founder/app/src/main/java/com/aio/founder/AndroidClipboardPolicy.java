package com.aio.founder;

import java.nio.charset.StandardCharsets;

final class AndroidClipboardPolicy {
    static final int MAX_UTF8_BYTES=16*1024;
    private AndroidClipboardPolicy(){}

    static void requireForeground(boolean visible){
        if(!visible)throw new SecurityException("CLIPBOARD_FOREGROUND_REQUIRED");
    }

    static String validateText(String text){
        if(text==null)throw new IllegalArgumentException("CLIPBOARD_TEXT_REQUIRED");
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        try{
            if(bytes.length>MAX_UTF8_BYTES)throw new IllegalArgumentException("CLIPBOARD_TEXT_BUDGET");
            return text;
        }finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
}
