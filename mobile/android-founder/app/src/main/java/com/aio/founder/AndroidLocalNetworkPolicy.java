package com.aio.founder;

import java.net.Inet6Address;
import java.net.InetAddress;

final class AndroidLocalNetworkPolicy {
    static final String PERMISSION_ANDROID_16="android.permission.NEARBY_WIFI_DEVICES";
    static final String PERMISSION_ANDROID_17="android.permission.ACCESS_LOCAL_NETWORK";

    private AndroidLocalNetworkPolicy(){}

    static boolean likelyLocalHost(String host){
        if(host==null)return false;
        String h=host.trim().toLowerCase(java.util.Locale.ROOT);
        if(h.isEmpty())return false;
        if("localhost".equals(h)||h.endsWith(".local"))return true;
        if(h.indexOf(':')>=0)return localIpv6Literal(h);
        int[] octets=ipv4(h);
        if(octets==null)return false;
        int a=octets[0],b=octets[1];
        return a==10
            || (a==172&&b>=16&&b<=31)
            || (a==192&&b==168)
            || (a==169&&b==254)
            || a==127;
    }

    static boolean needsRuntimePermission(int sdk,String host){
        return sdk>=36&&likelyLocalHost(host);
    }

    static String permissionName(int sdk){
        if(sdk>=37)return PERMISSION_ANDROID_17;
        if(sdk>=36)return PERMISSION_ANDROID_16;
        return "";
    }

    private static int[] ipv4(String value){
        String[] parts=value.split("\\.",-1);
        if(parts.length!=4)return null;
        int[] out=new int[4];
        for(int i=0;i<4;i++){
            if(parts[i].isEmpty()||parts[i].length()>3)return null;
            int v=0;
            for(int j=0;j<parts[i].length();j++){
                char c=parts[i].charAt(j);
                if(c<'0'||c>'9')return null;
                v=v*10+(c-'0');
            }
            if(v>255)return null;
            out[i]=v;
        }
        return out;
    }

    private static boolean localIpv6Literal(String value){
        try{
            InetAddress parsed=InetAddress.getByName(value);
            if(!(parsed instanceof Inet6Address))return false;
            byte[] b=parsed.getAddress();
            boolean loopback=true;
            for(int i=0;i<15;i++)loopback&=b[i]==0;
            loopback&=b[15]==1;
            if(loopback)return true;
            int first=b[0]&0xff,second=b[1]&0xff;
            boolean ula=(first&0xfe)==0xfc;
            boolean linkLocal=first==0xfe&&(second&0xc0)==0x80;
            return ula||linkLocal;
        }catch(Exception ignored){return false;}
    }
}
