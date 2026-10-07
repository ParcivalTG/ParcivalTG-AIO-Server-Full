package com.aio.founder;

final class AioReflectionGate {
    static final class Decision {
        final boolean recommended;
        final String code;
        Decision(boolean recommended,String code){
            this.recommended=recommended;this.code=code;
        }
    }

    private AioReflectionGate(){}

    static Decision evaluate(String action,boolean accepted,String resultCode,
                             String beforeScreenSha256,String afterScreenSha256,
                             int sameActionCount){
        if(action==null||action.isBlank())throw new IllegalArgumentException("REFLECTION_ACTION_INVALID");
        if(!accepted)
            return new Decision(true,"ACTION_DENIED:"+safe(resultCode));
        if(("gesture.tap".equals(action)||"gesture.swipe".equals(action))&&
           validSha(beforeScreenSha256)&&beforeScreenSha256.equalsIgnoreCase(afterScreenSha256))
            return new Decision(true,"NO_VISUAL_PROGRESS");
        if(sameActionCount>=3)
            return new Decision(true,"REPEATED_ACTION_NO_NEW_HYPOTHESIS");
        return new Decision(false,"PROGRESS_NOMINAL");
    }

    private static boolean validSha(String value){
        return value!=null&&value.matches("[0-9A-Fa-f]{64}");
    }

    private static String safe(String value){
        if(value!=null&&value.matches("[A-Za-z0-9_.:-]{1,96}"))return value;
        return "DENIED";
    }
}
