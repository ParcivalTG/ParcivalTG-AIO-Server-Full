package com.aio.founder;

final class AndroidResourcePolicy {
    private static final long MIN_AVAILABLE_MEMORY=256L*1024L*1024L;
    private AndroidResourcePolicy(){}

    static String contributionState(boolean charging,int thermalStatus,long availableMemoryBytes){
        if(!charging)return "CHARGING_REQUIRED";
        if(thermalStatus>=4)return "THERMAL_HOLD";
        if(availableMemoryBytes<MIN_AVAILABLE_MEMORY)return "MEMORY_HOLD";
        return "ELIGIBLE_LOCAL_ONLY";
    }
}
