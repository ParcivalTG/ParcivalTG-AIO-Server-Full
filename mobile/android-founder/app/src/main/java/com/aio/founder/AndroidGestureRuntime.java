package com.aio.founder;

final class AndroidGestureRuntime {
    private static AndroidGestureAccessibilityService active;
    private AndroidGestureRuntime(){}

    static synchronized void attach(AndroidGestureAccessibilityService service){active=service;}
    static synchronized void detach(AndroidGestureAccessibilityService service){if(active==service)active=null;}
    static synchronized boolean available(){return active!=null;}
    static synchronized boolean tap(int xPermille,int yPermille,long durationMs)throws Exception{
        if(active==null)throw new IllegalStateException("GESTURE_SERVICE_NOT_ENABLED");
        return active.tap(xPermille,yPermille,durationMs);
    }
    static synchronized boolean swipe(int x1,int y1,int x2,int y2,long durationMs)throws Exception{
        if(active==null)throw new IllegalStateException("GESTURE_SERVICE_NOT_ENABLED");
        return active.swipe(x1,y1,x2,y2,durationMs);
    }
}
