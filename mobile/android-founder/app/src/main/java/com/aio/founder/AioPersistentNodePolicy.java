package com.aio.founder;

final class AioPersistentNodePolicy {
    static final String ACTION_START="com.aio.founder.NODE_START";
    static final String ACTION_STOP="com.aio.founder.NODE_STOP";
    static final String PREFS="aio_persistent_node_v1";
    static final String KEY_ENABLED="founder_enabled";

    enum Decision { START, STOP, REJECT }

    private AioPersistentNodePolicy(){}

    static Decision decide(String action,boolean founderEnabled){
        if(ACTION_START.equals(action))return Decision.START;
        if(ACTION_STOP.equals(action))return Decision.STOP;
        if(action==null)return founderEnabled?Decision.START:Decision.STOP;
        return Decision.REJECT;
    }

    static long heartbeatMillis(){
        return 30_000L;
    }
}
