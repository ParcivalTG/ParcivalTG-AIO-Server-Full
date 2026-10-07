package com.aio.founder;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String KS = "AndroidKeyStore";
    private static final String ALIAS = "aio_founder_pairing_wrap_v1";
    private final SharedPreferences prefs;

    SecretStore(Context context) {
        prefs = context.getSharedPreferences("aio_founder_secure_projection", Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(KS);
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
            generator.init(new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
    }

    synchronized void saveEncodedWitnessSecret(String encoded) throws Exception {
        byte[] raw = PresenceProtocol.decodeWitnessSecret(encoded);
        if (raw.length < 16 || raw.length > 128) {
            java.util.Arrays.fill(raw, (byte) 0);
            throw new SecurityException("WITNESS_LENGTH_INVALID");
        }
        Cipher cipher;
        byte[] sealed;
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            sealed = cipher.doFinal(raw);
        }
        finally { java.util.Arrays.fill(raw, (byte) 0); }
        if (!prefs.edit()
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP))
                .commit()) throw new java.io.IOException("SECURE_STORE_COMMIT_FAILED");
    }

    synchronized byte[] readWitnessSecret() throws Exception {
        String ivText = prefs.getString("iv", null);
        String sealedText = prefs.getString("sealed", null);
        if (ivText == null || sealedText == null) return null;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)));
        return cipher.doFinal(Base64.decode(sealedText, Base64.NO_WRAP));
    }

    synchronized void clear() {
        prefs.edit().remove("iv").remove("sealed")
                .remove("lease_iv").remove("lease_sealed")
                .remove("cloud_admission_iv").remove("cloud_admission_sealed")
                .remove("cloud_tunnel_iv").remove("cloud_tunnel_sealed")
                .remove("pairing_generation").commit();
    }

    boolean hasSecret() {
        return prefs.contains("iv") && prefs.contains("sealed");
    }

    synchronized String pairingGeneration(){
        return prefs.getString("pairing_generation",null);
    }

    synchronized void savePairingUpdate(String witnessEncoded,String lease,String admission,String tunnel,
                                        boolean clearCloud,String generation)throws Exception{
        if(generation==null)throw new IllegalArgumentException("PAIRING_GENERATION_REQUIRED");
        try{java.util.UUID.fromString(generation);}
        catch(Exception invalid){throw new IllegalArgumentException("PAIRING_GENERATION_INVALID");}

        SharedPreferences.Editor editor=prefs.edit();
        if(witnessEncoded!=null){
            byte[] raw=PresenceProtocol.decodeWitnessSecret(witnessEncoded);
            try{
                if(raw.length<16||raw.length>128)throw new SecurityException("WITNESS_LENGTH_INVALID");
                ProtectedValue protectedValue=protect(raw,null);
                editor.putString("iv",protectedValue.iv).putString("sealed",protectedValue.sealed);
            }finally{java.util.Arrays.fill(raw,(byte)0);}
        }
        if(lease!=null){
            ProtectedValue protectedValue=protectText("lease",lease);
            editor.putString("lease_iv",protectedValue.iv).putString("lease_sealed",protectedValue.sealed);
        }
        if(clearCloud){
            editor.remove("cloud_admission_iv").remove("cloud_admission_sealed")
                .remove("cloud_tunnel_iv").remove("cloud_tunnel_sealed");
        }else{
            if(admission!=null){
                ProtectedValue protectedValue=protectText("cloud_admission",admission);
                editor.putString("cloud_admission_iv",protectedValue.iv)
                    .putString("cloud_admission_sealed",protectedValue.sealed);
            }
            if(tunnel!=null){
                ProtectedValue protectedValue=protectText("cloud_tunnel",tunnel);
                editor.putString("cloud_tunnel_iv",protectedValue.iv)
                    .putString("cloud_tunnel_sealed",protectedValue.sealed);
            }
        }
        editor.putString("pairing_generation",generation);
        if(!editor.commit())throw new java.io.IOException("SECURE_PAIRING_COMMIT_FAILED");
    }

    private ProtectedValue protectText(String name,String value)throws Exception{
        byte[] raw=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try{return protect(raw,name);}
        finally{java.util.Arrays.fill(raw,(byte)0);}
    }

    private ProtectedValue protect(byte[] raw,String aad)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        if(aad!=null)cipher.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] sealed=cipher.doFinal(raw);
        try{
            return new ProtectedValue(
                Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP),
                Base64.encodeToString(sealed,Base64.NO_WRAP));
        }finally{java.util.Arrays.fill(sealed,(byte)0);}
    }

    private static final class ProtectedValue{
        final String iv,sealed;
        ProtectedValue(String iv,String sealed){this.iv=iv;this.sealed=sealed;}
    }

    synchronized void saveText(String name, String value) throws Exception {
        byte[] raw = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] sealed;
        try { sealed = cipher.doFinal(raw); }
        finally { java.util.Arrays.fill(raw, (byte) 0); }
        if (!prefs.edit()
                .putString(name + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(name + "_sealed", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit())
            throw new java.io.IOException("SECURE_STORE_COMMIT_FAILED");
    }

    synchronized String readText(String name) throws Exception {
        String iv = prefs.getString(name + "_iv", null);
        String sealed = prefs.getString(name + "_sealed", null);
        if (iv == null && sealed == null) return null;
        if (iv == null || sealed == null) throw new SecurityException("SECURE_STORE_INCOMPLETE");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        cipher.updateAAD(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] raw = cipher.doFinal(Base64.decode(sealed, Base64.NO_WRAP));
        try { return new String(raw, java.nio.charset.StandardCharsets.UTF_8); }
        finally { java.util.Arrays.fill(raw, (byte) 0); }
    }

    boolean hasText(String name) {
        return prefs.contains(name + "_iv") && prefs.contains(name + "_sealed");
    }

    synchronized void removeText(String name){
        prefs.edit().remove(name+"_iv").remove(name+"_sealed").commit();
    }
}
