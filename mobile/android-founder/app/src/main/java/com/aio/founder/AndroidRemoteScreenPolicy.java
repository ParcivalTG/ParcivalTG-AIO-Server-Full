package com.aio.founder;

final class AndroidRemoteScreenPolicy {
    static final int MAX_JPEG_BYTES=320_000;
    static final int DEFAULT_QUALITY=55;
    private AndroidRemoteScreenPolicy(){}

    static int validateQuality(long quality){
        if(quality<30||quality>75)throw new IllegalArgumentException("SCREEN_REMOTE_QUALITY_INVALID");
        return (int)quality;
    }
}
