package com.aio.founder;

final class AndroidScreenProjectionPolicy {
    static final int MAX_WIDTH=1600;
    static final int MAX_HEIGHT=1600;
    static final long MAX_PIXELS=2_073_600L;

    static final class Size {
        final int width,height;
        Size(int width,int height){this.width=width;this.height=height;}
    }

    private AndroidScreenProjectionPolicy(){}

    static Size target(int sourceWidth,int sourceHeight){
        if(sourceWidth<1||sourceHeight<1||sourceWidth>16384||sourceHeight>16384)
            throw new IllegalArgumentException("SCREEN_SIZE_INVALID");
        double scale=Math.min(1.0,Math.min(
            (double)MAX_WIDTH/sourceWidth,
            (double)MAX_HEIGHT/sourceHeight));
        long pixels=(long)Math.ceil(sourceWidth*scale)*(long)Math.ceil(sourceHeight*scale);
        if(pixels>MAX_PIXELS)scale*=Math.sqrt((double)MAX_PIXELS/pixels);
        int width=Math.max(1,(int)Math.floor(sourceWidth*scale));
        int height=Math.max(1,(int)Math.floor(sourceHeight*scale));
        if((long)width*height>MAX_PIXELS)throw new IllegalStateException("SCREEN_TARGET_BOUNDS");
        return new Size(width,height);
    }

    static int validateDensity(int densityDpi){
        if(densityDpi<72||densityDpi>1000)throw new IllegalArgumentException("SCREEN_DENSITY_INVALID");
        return densityDpi;
    }
}
