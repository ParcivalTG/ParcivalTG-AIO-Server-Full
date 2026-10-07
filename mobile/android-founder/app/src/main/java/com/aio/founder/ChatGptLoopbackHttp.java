package com.aio.founder;

import java.nio.charset.StandardCharsets;

final class ChatGptLoopbackHttp {
    private static final int MAX_REQUEST_LINE=16*1024;
    private ChatGptLoopbackHttp(){}

    static String callbackUrl(String requestLine,int port){
        if(port<1024||port>65535||requestLine==null||requestLine.length()<16||
            requestLine.length()>MAX_REQUEST_LINE)
            throw new IllegalArgumentException("CHATGPT_LOOPBACK_REQUEST_INVALID");
        String[] parts=requestLine.split(" ",-1);
        if(parts.length!=3||!"GET".equals(parts[0])||!"HTTP/1.1".equals(parts[2]))
            throw new IllegalArgumentException("CHATGPT_LOOPBACK_REQUEST_INVALID");
        String target=parts[1];
        if(target.startsWith("http://")||target.startsWith("https://")||
            !(target.equals("/auth/callback")||target.startsWith("/auth/callback?"))||
            target.startsWith("/auth/callback/")||target.indexOf('#')>=0)
            throw new IllegalArgumentException("CHATGPT_LOOPBACK_REQUEST_INVALID");
        return "http://127.0.0.1:"+port+target;
    }

    static String successResponse(){
        String body="<!doctype html><meta charset=\"utf-8\"><title>AIO</title>"+
            "<body><p>Authorization returned to AIO. Return to AIO and close this tab.</p></body>";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 200 OK\r\n"+
            "Content-Type: text/html; charset=utf-8\r\n"+
            "Cache-Control: no-store\r\n"+
            "Pragma: no-cache\r\n"+
            "Content-Length: "+bytes.length+"\r\n"+
            "Connection: close\r\n\r\n"+body;
    }

    static String failureResponse(){
        String body="<!doctype html><meta charset=\"utf-8\"><title>AIO</title>"+
            "<body><p>AIO could not accept this authorization callback. Return to AIO and try again.</p></body>";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 400 Bad Request\r\n"+
            "Content-Type: text/html; charset=utf-8\r\n"+
            "Cache-Control: no-store\r\n"+
            "Content-Length: "+bytes.length+"\r\n"+
            "Connection: close\r\n\r\n"+body;
    }
}
