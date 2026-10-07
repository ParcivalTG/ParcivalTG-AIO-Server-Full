package com.aio.founder;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class AioJournalCipher implements AioSelectiveJournal.RecordCipher {
    private static final String KS="AndroidKeyStore";
    private static final String ALIAS="aio_founder_native_journal_v1";
    private final byte[] aad;

    AioJournalCipher(String domain){
        if(domain==null||!domain.matches("[A-Za-z0-9_.:-]{1,64}"))
            throw new IllegalArgumentException("AIO_JOURNAL_DOMAIN");
        this.aad=("AIO_NATIVE_JOURNAL_V1\n"+domain)
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance(KS);store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);
            generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
    }

    @Override public byte[] seal(byte[] plaintext)throws Exception{
        if(plaintext==null)throw new IllegalArgumentException("AIO_JOURNAL_PLAINTEXT");
        byte[] iv=null;
        byte[] encrypted=null;
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            // AndroidKeyStore requires it to generate encryption IVs for keys whose
            // randomized-encryption requirement remains enabled. Preserve that
            // fail-closed default instead of weakening the key policy.
            cipher.init(Cipher.ENCRYPT_MODE,key());
            cipher.updateAAD(aad);
            encrypted=cipher.doFinal(plaintext);
            iv=cipher.getIV();
            if(iv==null||iv.length!=12)throw new SecurityException("AIO_JOURNAL_IV_INVALID");
            byte[] out=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,out,0,iv.length);
            System.arraycopy(encrypted,0,out,iv.length,encrypted.length);
            return out;
        }finally{
            if(iv!=null)java.util.Arrays.fill(iv,(byte)0);
            if(encrypted!=null)java.util.Arrays.fill(encrypted,(byte)0);
        }
    }

    @Override public byte[] open(byte[] sealed)throws Exception{
        if(sealed==null||sealed.length<12+16)throw new SecurityException("AIO_JOURNAL_CIPHERTEXT");
        byte[] iv=java.util.Arrays.copyOfRange(sealed,0,12);
        byte[] encrypted=java.util.Arrays.copyOfRange(sealed,12,sealed.length);
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(encrypted);
        }finally{
            java.util.Arrays.fill(iv,(byte)0);
            java.util.Arrays.fill(encrypted,(byte)0);
        }
    }
}
