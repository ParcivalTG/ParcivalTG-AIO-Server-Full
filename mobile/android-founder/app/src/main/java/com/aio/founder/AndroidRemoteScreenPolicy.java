package com.aio.founder;

final class AndroidRemoteScreenPolicy {
    static final int MIN_JPEG_BYTES=32_768;
    static final int MAX_JPEG_BYTES=320_000;
    static final int DEFAULT_MAX_JPEG_BYTES=220_000;
    static final int GPT_MAX_JPEG_BYTES=160_000;
    static final int DEFAULT_QUALITY=55;
    static final int COORD_MAX=1000;

    static final class Region {
        final int x1,y1,x2,y2;
        Region(int x1,int y1,int x2,int y2){
            this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;
        }
        boolean full(){return x1==0&&y1==0&&x2==COORD_MAX&&y2==COORD_MAX;}
    }

    private AndroidRemoteScreenPolicy(){}

    static int validateQuality(long quality){
        if(quality<30||quality>75)throw new IllegalArgumentException("SCREEN_REMOTE_QUALITY_INVALID");
        return (int)quality;
    }

    static int validateMaxBytes(long maxBytes){
        if(maxBytes<MIN_JPEG_BYTES||maxBytes>MAX_JPEG_BYTES)
            throw new IllegalArgumentException("SCREEN_REMOTE_BUDGET_INVALID");
        return (int)maxBytes;
    }

    static Region region(long x1,long y1,long x2,long y2){
        if(x1<0||y1<0||x2>COORD_MAX||y2>COORD_MAX||x2<=x1||y2<=y1)
            throw new IllegalArgumentException("SCREEN_REGION_INVALID");
        return new Region((int)x1,(int)y1,(int)x2,(int)y2);
    }

    static int pixelStart(int permille,int size){
        if(size<1)throw new IllegalArgumentException("SCREEN_REGION_SURFACE_INVALID");
        return Math.min(size-1,(int)Math.floor(size*(permille/(double)COORD_MAX)));
    }

    static int pixelEndExclusive(int permille,int size){
        if(size<1)throw new IllegalArgumentException("SCREEN_REGION_SURFACE_INVALID");
        return Math.max(1,Math.min(size,(int)Math.ceil(size*(permille/(double)COORD_MAX))));
    }
}
