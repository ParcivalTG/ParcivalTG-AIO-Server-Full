package com.aio.founder;

import java.util.ArrayDeque;

final class AndroidNotificationPolicy {
    static final int MAX_TITLE_CHARS=96;
    static final int MAX_TEXT_CHARS=512;
    static final int MAX_PER_MINUTE=5;
    static final long MIN_INTERVAL_MS=5_000L;
    private AndroidNotificationPolicy(){}

    static String title(String value){return bounded(value,MAX_TITLE_CHARS,"NOTIFICATION_TITLE_INVALID");}
    static String text(String value){return bounded(value,MAX_TEXT_CHARS,"NOTIFICATION_TEXT_INVALID");}

    static void admit(ArrayDeque<Long> timestamps,long now){
        while(!timestamps.isEmpty()&&now-timestamps.peekFirst()>=60_000L)timestamps.removeFirst();
        if(!timestamps.isEmpty()&&now-timestamps.peekLast()<MIN_INTERVAL_MS)
            throw new SecurityException("NOTIFICATION_RATE_LIMIT");
        if(timestamps.size()>=MAX_PER_MINUTE)throw new SecurityException("NOTIFICATION_RATE_LIMIT");
        timestamps.addLast(now);
    }

    private static String bounded(String value,int max,String code){
        if(value==null)throw new IllegalArgumentException(code);
        String clean=value.trim();
        if(clean.isEmpty()||clean.length()>max)throw new IllegalArgumentException(code);
        return clean;
    }
}
