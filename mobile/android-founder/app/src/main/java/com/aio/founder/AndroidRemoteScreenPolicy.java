package com.aio.founder;

final class AndroidRemoteScreenPolicy {
    static final int MIN_JPEG_BYTES=32_768;
    static final int MAX_JPEG_BYTES=320_000;
    static final int DEFAULT_MAX_JPEG_BYTES=220_000;
    static final int GPT_MAX_JPEG_BYTES=160_000;
    static final int DEFAULT_QUALITY=55;
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
}
