package com.aio.founder;

final class AndroidScreenProjectionRuntime {
    private static AndroidScreenProjectionService active;

    private AndroidScreenProjectionRuntime(){}

    static synchronized void attach(AndroidScreenProjectionService service){
        if(service==null)throw new IllegalArgumentException("SCREEN_SERVICE_REQUIRED");
        active=service;
    }

    static synchronized void detach(AndroidScreenProjectionService service){
        if(active==service)active=null;
    }

    static synchronized AndroidScreenProjectionService.Snapshot snapshot(){
        return active==null?null:active.snapshotInternal();
    }

    static synchronized byte[] captureJpeg(int quality,int maxBytes)throws Exception{
        if(active==null)throw new IllegalStateException("SCREEN_CAPTURE_NOT_ACTIVE");
        return active.captureJpegInternal(quality,maxBytes);
    }
}
