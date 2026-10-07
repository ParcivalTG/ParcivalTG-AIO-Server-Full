package com.aio.founder;

import java.util.ArrayDeque;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidNotificationPolicyTest {
    @Test public void boundsText(){
        assertEquals("AIO",AndroidNotificationPolicy.title(" AIO "));
        try{AndroidNotificationPolicy.text("x".repeat(AndroidNotificationPolicy.MAX_TEXT_CHARS+1));fail();}
        catch(IllegalArgumentException expected){assertEquals("NOTIFICATION_TEXT_INVALID",expected.getMessage());}
    }
    @Test public void rateLimitRequiresSpacing(){
        ArrayDeque<Long> events=new ArrayDeque<>();
        AndroidNotificationPolicy.admit(events,10_000);
        try{AndroidNotificationPolicy.admit(events,11_000);fail();}
        catch(SecurityException expected){assertEquals("NOTIFICATION_RATE_LIMIT",expected.getMessage());}
        AndroidNotificationPolicy.admit(events,15_000);
    }
    @Test public void minuteBudgetIsBounded(){
        ArrayDeque<Long> events=new ArrayDeque<>();
        for(int i=0;i<AndroidNotificationPolicy.MAX_PER_MINUTE;i++)
            AndroidNotificationPolicy.admit(events,10_000L+i*AndroidNotificationPolicy.MIN_INTERVAL_MS);
        try{AndroidNotificationPolicy.admit(events,10_000L+AndroidNotificationPolicy.MAX_PER_MINUTE*AndroidNotificationPolicy.MIN_INTERVAL_MS);fail();}
        catch(SecurityException expected){assertEquals("NOTIFICATION_RATE_LIMIT",expected.getMessage());}
    }
}
