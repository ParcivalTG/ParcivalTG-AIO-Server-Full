package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** AIO-native Android capability state. Platform permissions are boundary facts, never inferred authority. */
final class AioAndroidNode {
    enum Capability { SCREEN_OBSERVE, GESTURE_INPUT, FILE_READ, FILE_WRITE, CLIPBOARD, NOTIFICATIONS, RESOURCE_STATUS, RESOURCE_CONTRIBUTE, PACKAGE_STAGE }
    enum Tier { OBSERVE, INTERACT, DATA, UPDATE }
    enum Privacy { PUBLIC, FOUNDER_ONLY, PRIVATE }

    static final class ResourceEnvelope {
        final int maxConcurrent;
        final long maxBytes;
        final boolean chargingOnly;
        final String thermalPolicy;
        ResourceEnvelope(int maxConcurrent,long maxBytes,boolean chargingOnly,String thermalPolicy){
            this.maxConcurrent=maxConcurrent;this.maxBytes=maxBytes;this.chargingOnly=chargingOnly;this.thermalPolicy=thermalPolicy;
        }
    }
    static final class Manifest {
        final String nodeId,platform,version;
        final Set<Capability> capabilities;
        final ResourceEnvelope envelope;
        Manifest(String nodeId,String platform,String version,Set<Capability> capabilities,ResourceEnvelope envelope){
            this.nodeId=nodeId;this.platform=platform;this.version=version;this.capabilities=Set.copyOf(capabilities);this.envelope=envelope;
        }
    }
    static final class Grant {
        final String id,peer;
        final Capability capability;
        final Tier tier;
        final Privacy privacy;
        final long issuedMs,expiresMs;
        Grant(String id,String peer,Capability capability,Tier tier,Privacy privacy,long issuedMs,long expiresMs){
            this.id=id;this.peer=peer;this.capability=capability;this.tier=tier;this.privacy=privacy;this.issuedMs=issuedMs;this.expiresMs=expiresMs;
        }
    }
    static final class Receipt {
        final String id,capability,action,outcome,reason;
        final long atMs;
        Receipt(String id,String capability,String action,String outcome,String reason,long atMs){
            this.id=id;this.capability=capability;this.action=action;this.outcome=outcome;this.reason=reason;this.atMs=atMs;
        }
    }

    private static final int MAX_ACTIVE_GRANTS=128;
    private static final int MAX_RECEIPTS=512;
    private final Map<String,Grant> grants=new HashMap<>();
    private final Set<String> revoked=new HashSet<>();
    private final ArrayList<Receipt> receipts=new ArrayList<>();

    synchronized Grant grant(String peer,Capability cap,Tier tier,Privacy privacy,long ttlMs){
        if(peer==null||peer.isBlank()||cap==null||tier==null||privacy==null)
            throw new IllegalArgumentException("ANDROID_GRANT_INVALID");
        AndroidCapabilityCatalog.validateGrant(cap,ttlMs);
        long now=System.currentTimeMillis();
        prune(now);
        if(grants.size()>=MAX_ACTIVE_GRANTS)throw new IllegalStateException("ANDROID_GRANT_CAPACITY");
        for(Grant prior:grants.values()){
            if(prior.peer.equals(peer)&&prior.capability==cap&&!revoked.contains(prior.id)&&now<prior.expiresMs)
                revoked.add(prior.id);
        }
        String id=digest(peer+"|"+cap+"|"+tier+"|"+privacy+"|"+now+"|"+UUID.randomUUID());
        Grant g=new Grant(id,peer,cap,tier,privacy,now,now+ttlMs);
        grants.put(id,g);receipt(id,cap.name(),"GRANT","ACCEPT","founder-authorized");return g;
    }
    synchronized void revoke(String id){
        if(grants.containsKey(id))revoked.add(id);
        receipt(id,"N/A","REVOKE","ACCEPT","explicit-revocation");
    }
    synchronized Receipt authorize(String id,String peer,Capability cap,String action,Privacy dataClass){
        Grant g=grants.get(id);String reason="ok";
        if(g==null)reason="unknown-grant";
        else if(revoked.contains(id))reason="revoked";
        else if(System.currentTimeMillis()>=g.expiresMs)reason="expired";
        else if(!g.peer.equals(peer))reason="peer-mismatch";
        else if(g.capability!=cap)reason="capability-mismatch";
        else if(dataClass==null||dataClass.ordinal()<g.privacy.ordinal())reason="privacy-downgrade";
        else if(g.tier.ordinal()<requiredTier(cap).ordinal())reason="tier-insufficient";
        Receipt r=receipt(id,cap.name(),action,"ok".equals(reason)?"ALLOW":"DENY",reason);
        if(!"ok".equals(reason))throw new SecurityException(reason);
        return r;
    }
    synchronized Receipt authorizePeer(String peer,Capability cap,String action,Privacy dataClass){
        long now=System.currentTimeMillis();
        Grant match=null;
        for(Grant grant:grants.values()){
            if(!grant.peer.equals(peer)||grant.capability!=cap||revoked.contains(grant.id)||now>=grant.expiresMs)continue;
            if(match!=null)throw new SecurityException("ambiguous-active-grant");
            match=grant;
        }
        if(match==null)throw new SecurityException("no-active-grant");
        return authorize(match.id,peer,cap,action,dataClass);
    }

    synchronized void revokePeerCapability(String peer,Capability cap){
        for(Grant grant:grants.values())
            if(grant.peer.equals(peer)&&grant.capability==cap&&!revoked.contains(grant.id))revoked.add(grant.id);
        receipt("peer:"+peer,cap.name(),"REVOKE","ACCEPT","peer-capability-revocation");
    }

    synchronized int activeGrantCount(String peer){
        long now=System.currentTimeMillis();prune(now);int count=0;
        for(Grant grant:grants.values())
            if(grant.peer.equals(peer)&&!revoked.contains(grant.id)&&now<grant.expiresMs)count++;
        return count;
    }

    synchronized List<Receipt> receipts(){return List.copyOf(receipts);}

    private void prune(long now){
        java.util.Iterator<Map.Entry<String,Grant>> iterator=grants.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String,Grant> row=iterator.next();
            Grant grant=row.getValue();
            if(now>=grant.expiresMs||revoked.contains(grant.id)){
                revoked.remove(grant.id);
                iterator.remove();
            }
        }
    }

    static Tier requiredTier(Capability capability){
        return AndroidCapabilityCatalog.spec(capability).minimumTier;
    }

    static Manifest manifest(String nodeId){
        return new Manifest(nodeId,"android","r5",EnumSet.allOf(Capability.class),
            new ResourceEnvelope(2,64L*1024*1024,true,"pause-on-critical"));
    }
    private Receipt receipt(String id,String cap,String action,String outcome,String reason){
        Receipt r=new Receipt(id,cap,action,outcome,reason,System.currentTimeMillis());
        if(receipts.size()>=MAX_RECEIPTS)receipts.remove(0);
        receipts.add(r);return r;
    }
    private static String digest(String s){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            try{
                StringBuilder out=new StringBuilder(bytes.length*2);
                for(byte value:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",value&0xff));
                return out.toString();
            }finally{java.util.Arrays.fill(bytes,(byte)0);}
        }catch(Exception e){throw new AssertionError(e);}
    }
}
