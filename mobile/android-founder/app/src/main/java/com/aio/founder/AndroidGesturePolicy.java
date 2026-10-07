package com.aio.founder;

final class AndroidGesturePolicy {
    static final int MAX_COORD=1000;
    static final long MIN_DURATION_MS=40;
    static final long MAX_DURATION_MS=1500;

    static final class Point {
        final int x,y;
        Point(int x,int y){this.x=x;this.y=y;}
    }

    private AndroidGesturePolicy(){}

    static Point pixels(int xPermille,int yPermille,int width,int height){
        if(xPermille<0||xPermille>MAX_COORD||yPermille<0||yPermille>MAX_COORD)
            throw new IllegalArgumentException("GESTURE_COORD_INVALID");
        if(width<1||height<1||width>16384||height>16384)
            throw new IllegalArgumentException("GESTURE_SURFACE_INVALID");
        int x=Math.min(width-1,(int)Math.round((width-1)*(xPermille/(double)MAX_COORD)));
        int y=Math.min(height-1,(int)Math.round((height-1)*(yPermille/(double)MAX_COORD)));
        return new Point(x,y);
    }

    static long duration(long value){
        if(value<MIN_DURATION_MS||value>MAX_DURATION_MS)
            throw new IllegalArgumentException("GESTURE_DURATION_INVALID");
        return value;
    }
}
