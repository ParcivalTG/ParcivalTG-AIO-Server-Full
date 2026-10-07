package com.aio.founder;

final class AioPostActionObservationPolicy {
    private AioPostActionObservationPolicy(){}

    static long delayMillis(String action,long durationMs){
        if("gesture.tap".equals(action)){
            long delay=durationMs+180L;
            return Math.max(220L,Math.min(500L,delay));
        }
        if("gesture.swipe".equals(action)){
            long delay=durationMs+220L;
            return Math.max(350L,Math.min(900L,delay));
        }
        throw new IllegalArgumentException("POST_ACTION_OBSERVATION_ACTION_INVALID");
    }
}
