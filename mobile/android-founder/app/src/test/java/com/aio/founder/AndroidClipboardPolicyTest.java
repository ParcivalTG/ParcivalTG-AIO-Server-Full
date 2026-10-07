package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidClipboardPolicyTest {
    @Test public void backgroundRemoteClipboardFailsClosed(){
        try{AndroidClipboardPolicy.requireForeground(false);fail();}
        catch(SecurityException expected){assertEquals("CLIPBOARD_FOREGROUND_REQUIRED",expected.getMessage());}
    }
    @Test public void foregroundRemoteClipboardIsEligible(){
        AndroidClipboardPolicy.requireForeground(true);
    }
    @Test public void acceptsBoundedText(){
        assertEquals("hello",AndroidClipboardPolicy.validateText("hello"));
    }
    @Test public void rejectsOversizeUtf8(){
        String value="é".repeat(AndroidClipboardPolicy.MAX_UTF8_BYTES);
        try{AndroidClipboardPolicy.validateText(value);fail();}
        catch(IllegalArgumentException expected){assertEquals("CLIPBOARD_TEXT_BUDGET",expected.getMessage());}
    }
    @Test public void rejectsNull(){
        try{AndroidClipboardPolicy.validateText(null);fail();}
        catch(IllegalArgumentException expected){assertEquals("CLIPBOARD_TEXT_REQUIRED",expected.getMessage());}
    }
}
