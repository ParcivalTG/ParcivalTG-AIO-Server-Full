package com.aio.founder;

import android.content.Context;

final class AioAndroidRuntime {
    private static volatile AioAndroidRuntime instance;

    private final AioAndroidNode node;
    private final AndroidCapabilityBroker files;
    private final AndroidCapabilityDispatcher dispatcher;
    private final AioFounderStateStore stateStore;
    private final ChatGptProviderRuntime chatGpt;

    private AioAndroidRuntime(Context context){
        Context app=context.getApplicationContext();
        node=new AioAndroidNode();
        files=new AndroidCapabilityBroker(app);
        dispatcher=new AndroidCapabilityDispatcher(app,node,files);
        stateStore=new AioFounderStateStore(app);
        chatGpt=new ChatGptProviderRuntime(app);
    }

    static AioAndroidRuntime get(Context context){
        AioAndroidRuntime current=instance;
        if(current!=null)return current;
        synchronized(AioAndroidRuntime.class){
            current=instance;
            if(current==null){
                current=new AioAndroidRuntime(context);
                instance=current;
            }
            return current;
        }
    }

    AioAndroidNode node(){return node;}
    AndroidCapabilityBroker files(){return files;}
    AndroidCapabilityDispatcher dispatcher(){return dispatcher;}
    AioFounderStateStore stateStore(){return stateStore;}
    ChatGptProviderRuntime chatGpt(){return chatGpt;}
}
