package com.aio.founder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScreenBudgetInstrumentationTest {
    @Test public void noisyFrameAdaptsUnderGptBudgetAndRemainsDecodable()throws Exception{
        final int width=900,height=900;
        Bitmap source=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[width*height];
        int state=0x13579bdf;
        for(int i=0;i<pixels.length;i++){
            state=state*1664525+1013904223;
            pixels[i]=0xff000000|(state&0x00ffffff);
        }
        source.setPixels(pixels,0,width,0,0,width,height);
        Arrays.fill(pixels,0);
        byte[] encoded=null;
        Bitmap decoded=null;
        try{
            encoded=AndroidScreenProjectionService.encodeJpegBounded(
                source,75,AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES);
            assertNotNull(encoded);
            assertTrue(encoded.length<=AndroidRemoteScreenPolicy.GPT_MAX_JPEG_BYTES);
            assertEquals(0xff,encoded[0]&0xff);
            assertEquals(0xd8,encoded[1]&0xff);
            decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length);
            assertNotNull(decoded);
            assertTrue(decoded.getWidth()>0&&decoded.getHeight()>0);
            assertTrue(decoded.getWidth()<=width&&decoded.getHeight()<=height);
        }finally{
            if(decoded!=null)decoded.recycle();
            if(encoded!=null)Arrays.fill(encoded,(byte)0);
            source.recycle();
        }
    }
}
