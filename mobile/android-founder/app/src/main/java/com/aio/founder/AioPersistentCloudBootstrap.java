package com.aio.founder;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

final class AioPersistentCloudBootstrap {
    private static final String PREFS="aio_founder_connection_v1";
    private AioPersistentCloudBootstrap(){}

    static CloudPresenceTransport connect(Context context)throws Exception{
        SharedPreferences configuration=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String wss=configuration.getString("cloud_wss","").trim();
        String peer=configuration.getString("cloud_peer","").trim();
        String target=configuration.getString("cloud_target","").trim();
        String client=configuration.getString("client","").trim();
        String pin=configuration.getString("pin","").trim();
        if(!wss.startsWith("wss://")||!peer.matches("[A-Za-z0-9_.:-]{1,128}")||
            !target.matches("[A-Za-z0-9_.:-]{1,128}")||!client.matches("[A-Za-z0-9_.-]{1,128}")||
            pin.isEmpty())
            throw new IllegalStateException("PERSISTENT_CLOUD_NOT_CONFIGURED");

        SecretStore secrets=new SecretStore(context);
        String configGeneration=configuration.getString("pairing_generation","");
        String secretGeneration=secrets.pairingGeneration();
        if(configGeneration.isEmpty()||secretGeneration==null||!configGeneration.equals(secretGeneration))
            throw new SecurityException("PERSISTENT_PAIRING_STATE_INCONSISTENT");

        String admissionText=secrets.readText("cloud_admission");
        String tunnelText=secrets.readText("cloud_tunnel");
        byte[] witness=secrets.readWitnessSecret();
        if(admissionText==null||tunnelText==null||witness==null)
            throw new SecurityException("PERSISTENT_AUTHORITY_MATERIAL_MISSING");

        byte[] admission=null,tunnel=null,witnessPayload=null;
        CloudPresenceTransport cloud=null;
        try{
            admission=Base64.getDecoder().decode(admissionText);
            tunnel=Base64.getDecoder().decode(tunnelText);
            if(admission.length!=32||tunnel.length!=32)throw new SecurityException("CLOUD_KEY_LENGTH");

            long now=System.currentTimeMillis();
            String nonce=PresenceProtocol.newNonce();
            witnessPayload=AioProjectionMembrane.projectWitnessCell(
                client,now,nonce,PresenceProtocol.witness(client,now,nonce,witness));

            cloud=new CloudPresenceTransport(wss,peer,admission,tunnel,target);
            CloudPresenceTransport selected=cloud;
            FounderPresenceSessionController.Ready ready=new FounderPresenceSessionController().connect(
                null,()->selected,witnessPayload);

            AioPresenceField field=new AioPresenceField();
            AioProjectionMembrane.absorbHello(field,ready.hello);
            AioProjectionMembrane.absorbStatus(field,ready.status,ready.pingRoundTripMs);
            if(!field.projectAndroidShadow().ready)throw new IllegalStateException("PC_CORE_NOT_READY");

            PresenceAuthorityClient.Verified verified=
                PresenceAuthorityClient.refreshAndVerify(cloud,pin,System.currentTimeMillis());
            secrets.saveText("lease",verified.leaseJson);
            return cloud;
        }catch(Exception failure){
            if(cloud!=null)try{cloud.close();}catch(Exception ignored){}
            throw failure;
        }finally{
            if(admission!=null)Arrays.fill(admission,(byte)0);
            if(tunnel!=null)Arrays.fill(tunnel,(byte)0);
            if(witness!=null)Arrays.fill(witness,(byte)0);
            if(witnessPayload!=null)Arrays.fill(witnessPayload,(byte)0);
        }
    }
}
