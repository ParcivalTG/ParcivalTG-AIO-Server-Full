package com.aio.founder;

final class AioPersistentNodeState {
    enum Phase { STOPPED, ACTIVE_NO_LINK, LINK_ATTACHED_UNVERIFIED, LINK_VERIFIED, HOLD }

    static final class Snapshot {
        final Phase phase;
        final long revision;
        final String reason;
        Snapshot(Phase phase,long revision,String reason){
            this.phase=phase;this.revision=revision;this.reason=reason;
        }
    }

    private Phase phase=Phase.STOPPED;
    private long revision;
    private String reason="INITIAL";

    synchronized Snapshot snapshot(){return new Snapshot(phase,revision,reason);}

    synchronized Snapshot founderStart(){
        if(phase==Phase.STOPPED){phase=Phase.ACTIVE_NO_LINK;reason="FOUNDER_START";revision++;}
        return snapshot();
    }

    synchronized Snapshot founderStop(){
        phase=Phase.STOPPED;reason="FOUNDER_STOP";revision++;
        return snapshot();
    }

    synchronized Snapshot stickyRestart(boolean founderEnabled){
        phase=founderEnabled?Phase.ACTIVE_NO_LINK:Phase.STOPPED;
        reason=founderEnabled?"PROCESS_RESTART_REQUIRES_REATTACH":"PROCESS_RESTART_DISABLED";
        revision++;return snapshot();
    }

    synchronized Snapshot linkAttached(){
        requireActive("LINK_ATTACH_WHILE_STOPPED");
        phase=Phase.LINK_ATTACHED_UNVERIFIED;reason="R5_LINK_ATTACHED_E2E_UNVERIFIED";revision++;
        return snapshot();
    }

    synchronized Snapshot peerVerified(){
        if(phase!=Phase.LINK_ATTACHED_UNVERIFIED&&phase!=Phase.LINK_VERIFIED)
            throw new IllegalStateException("PEER_VERIFY_BEFORE_LINK");
        phase=Phase.LINK_VERIFIED;reason="PINNED_E2E_PEER_VERIFIED";revision++;
        return snapshot();
    }

    synchronized Snapshot linkLost(){
        requireActive("LINK_LOSS_WHILE_STOPPED");
        phase=Phase.ACTIVE_NO_LINK;reason="R5_LINK_LOST";revision++;
        return snapshot();
    }

    synchronized Snapshot hold(String code){
        requireActive("HOLD_WHILE_STOPPED");
        if(code==null||!code.matches("[A-Z0-9_]{3,96}"))throw new IllegalArgumentException("NODE_HOLD_CODE_INVALID");
        phase=Phase.HOLD;reason=code;revision++;
        return snapshot();
    }

    synchronized boolean remoteCapabilityEligible(){
        return phase==Phase.LINK_VERIFIED;
    }

    private void requireActive(String code){
        if(phase==Phase.STOPPED)throw new IllegalStateException(code);
    }
}
