package com.aio.founder;

final class ChatGptProjectionPolicy {
    private ChatGptProjectionPolicy(){}

    static boolean mayProject(String privacyClass){
        return "EXTERNAL_MINIMIZED".equals(privacyClass)||"EXTERNAL_ALLOWED".equals(privacyClass);
    }

    static boolean requiresMinimization(String privacyClass){
        return "EXTERNAL_MINIMIZED".equals(privacyClass);
    }

    static void requireProjection(String privacyClass){
        if(!mayProject(privacyClass))throw new SecurityException("CHATGPT_PRIVACY_PROJECTION_DENIED");
    }

    static String projectText(String privacyClass,String raw,String minimized){
        requireProjection(privacyClass);
        if(raw==null||raw.isBlank()||raw.length()>64*1024)
            throw new IllegalArgumentException("CHATGPT_PROJECTION_TEXT_INVALID");
        if(requiresMinimization(privacyClass)){
            if(minimized==null||minimized.isBlank()||minimized.length()>32*1024)
                throw new SecurityException("CHATGPT_MINIMIZED_PROJECTION_REQUIRED");
            return minimized;
        }
        return raw;
    }
}
