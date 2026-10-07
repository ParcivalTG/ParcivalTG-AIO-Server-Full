package com.aio.founder;

final class AioAppVisibility {
    private static int visibleActivities;
    private AioAppVisibility(){}

    static synchronized void enteredForeground(){visibleActivities++;}
    static synchronized void leftForeground(){if(visibleActivities>0)visibleActivities--;}
    static synchronized boolean isForegroundVisible(){return visibleActivities>0;}
}
