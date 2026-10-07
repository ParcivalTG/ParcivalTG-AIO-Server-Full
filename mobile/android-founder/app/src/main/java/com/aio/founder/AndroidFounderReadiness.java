package com.aio.founder;

final class AndroidFounderReadiness {
    static final class Snapshot {
        final boolean releaseIdentity;
        final boolean pairingMaterial;
        final boolean routeConfigured;
        final boolean directPermissionReady;
        final boolean persistentEnabled;
        final boolean notificationReady;
        final boolean storageGranted;
        final boolean screenConsentActive;
        final boolean gestureServiceEnabled;
        final boolean foregroundVisible;
        final boolean presenceAuthenticated;
        final boolean peerVerified;

        Snapshot(boolean releaseIdentity,boolean pairingMaterial,boolean routeConfigured,
                 boolean directPermissionReady,boolean persistentEnabled,boolean notificationReady,
                 boolean storageGranted,boolean screenConsentActive,boolean gestureServiceEnabled,
                 boolean foregroundVisible,boolean presenceAuthenticated,boolean peerVerified){
            this.releaseIdentity=releaseIdentity;this.pairingMaterial=pairingMaterial;
            this.routeConfigured=routeConfigured;this.directPermissionReady=directPermissionReady;
            this.persistentEnabled=persistentEnabled;this.notificationReady=notificationReady;
            this.storageGranted=storageGranted;this.screenConsentActive=screenConsentActive;
            this.gestureServiceEnabled=gestureServiceEnabled;this.foregroundVisible=foregroundVisible;
            this.presenceAuthenticated=presenceAuthenticated;this.peerVerified=peerVerified;
        }
    }

    static final class Report {
        final String installIdentity,connection,remoteAuthority,optionalCapabilities,overall;
        Report(String installIdentity,String connection,String remoteAuthority,String optionalCapabilities,String overall){
            this.installIdentity=installIdentity;this.connection=connection;this.remoteAuthority=remoteAuthority;
            this.optionalCapabilities=optionalCapabilities;this.overall=overall;
        }

        String summary(){
            return "ANDROID READINESS"+
                "\nOverall: "+overall+
                "\nInstall identity: "+installIdentity+
                "\nPairing/route: "+connection+
                "\nWindows peer authority: "+remoteAuthority+
                "\nOptional capabilities: "+optionalCapabilities;
        }
    }

    private AndroidFounderReadiness(){}

    static Report evaluate(Snapshot s){
        String install=s.releaseIdentity?"RELEASE_IDENTITY":"DEVELOPMENT_BUILD";
        String connection=!s.pairingMaterial?"PAIRING_REQUIRED":
            !s.routeConfigured?"ROUTE_REQUIRED":
            !s.directPermissionReady?"DIRECT_PERMISSION_OR_R5_REQUIRED":
            s.presenceAuthenticated?"PRESENCE_AUTHENTICATED":"READY_TO_CONNECT";
        String authority=s.peerVerified?"PINNED_E2E_VERIFIED":
            s.presenceAuthenticated?"DIALOGUE_PROOF_REQUIRED":"NOT_CONNECTED";

        int ready=0,total=6;
        if(s.persistentEnabled)ready++;
        if(s.notificationReady)ready++;
        if(s.storageGranted)ready++;
        if(s.screenConsentActive)ready++;
        if(s.gestureServiceEnabled)ready++;
        if(s.foregroundVisible)ready++;
        String optional=ready+"/"+total+" local prerequisites active";

        String overall;
        if(!s.releaseIdentity)overall="DEVELOPMENT_BUILD";
        else if(!s.pairingMaterial||!s.routeConfigured)overall="PAIRING_REQUIRED";
        else if(!s.presenceAuthenticated)overall="READY_FOR_CONNECTION";
        else if(!s.peerVerified)overall="DIALOGUE_VERIFICATION_REQUIRED";
        else overall="CORE_REMOTE_READY";

        return new Report(install,connection,authority,optional,overall);
    }
}
