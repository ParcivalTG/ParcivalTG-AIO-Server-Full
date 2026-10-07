package com.aio.founder;

import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Existing gateway cryptographic projection. This class has no JSON, Android, or network dependency. */
final class E2eCodec {
    static final String REQUEST_SCHEMA = "aio.private-gateway.e2e.v1";
    static final String RESPONSE_SCHEMA = "aio.private-gateway.e2e-response.v1";
    static final int MAX_CIPHERTEXT = 1_000_000;
    private E2eCodec() {}

    static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    static byte[] decode(String value, int maximum) {
        if (value == null || value.length() > ((maximum + 2) / 3) * 4)
            throw new IllegalArgumentException("E2E_BASE64_BUDGET");
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (decoded.length > maximum || !encode(decoded).equals(value))
            throw new IllegalArgumentException("E2E_BASE64_NOT_CANONICAL");
        return decoded;
    }

    static PublicKey pinnedKey(String canonicalSpki) throws Exception {
        PublicKey key = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(decode(canonicalSpki, 256)));
        if (!(key instanceof ECPublicKey)) throw new SecurityException("PIN_NOT_EC");
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class);
        ECParameterSpec actual = ((ECPublicKey) key).getParams();
        if (!expected.getCurve().equals(actual.getCurve()) || !expected.getGenerator().equals(actual.getGenerator())
                || !expected.getOrder().equals(actual.getOrder()) || expected.getCofactor() != actual.getCofactor())
            throw new SecurityException("PIN_NOT_P256");
        return key;
    }

    static Request encrypt(String pinnedSpki, String requestId, long created, byte[] plaintext) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        return encryptWithKeyPair(pinnedSpki, requestId, created, created + 30_000,
                plaintext, generator.generateKeyPair(), nonce);
    }

    // Package visibility allows independent deterministic interoperability courts using test-only keys.
    static Request encryptWithKeyPair(String pinnedSpki, String requestId, long created, long expires,
                                      byte[] plaintext, KeyPair client, byte[] nonce) throws Exception {
        if (requestId == null || requestId.trim().isEmpty() || requestId.length() > 128 || requestId.contains("\n"))
            throw new IllegalArgumentException("E2E_REQUEST_ID_INVALID");
        if (plaintext == null || plaintext.length == 0 || plaintext.length > MAX_CIPHERTEXT || nonce.length != 12)
            throw new IllegalArgumentException("E2E_PAYLOAD_BUDGET");
        if (created < 0 || expires <= created || expires - created > 60_000)
            throw new IllegalArgumentException("E2E_TTL_INVALID");
        PublicKey server = pinnedKey(pinnedSpki);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(client.getPrivate());
        agreement.doPhase(server, true);
        byte[] raw = agreement.generateSecret();
        byte[] shared;
        try { shared = MessageDigest.getInstance("SHA-256").digest(raw); }
        finally { Arrays.fill(raw, (byte) 0); }
        String clientSpki = encode(client.getPublic().getEncoded());
        byte[] key = hkdf(shared, requestId, "aio.private-gateway.v1.c2s");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(bytes(requestId + "\n" + clientSpki + "\n" + created + "\n" + expires));
            byte[] sealed = cipher.doFinal(plaintext);
            return new Request(requestId, clientSpki, encode(nonce),
                    encode(Arrays.copyOf(sealed, sealed.length - 16)),
                    encode(Arrays.copyOfRange(sealed, sealed.length - 16, sealed.length)),
                    created, expires, new Context(requestId, pinnedSpki, shared));
        } catch (Exception failure) {
            Arrays.fill(shared, (byte) 0);
            throw failure;
        } finally { Arrays.fill(key, (byte) 0); }
    }

    static byte[] hkdf(byte[] shared, String requestId, String direction) throws Exception {
        Mac extract = Mac.getInstance("HmacSHA256");
        extract.init(new SecretKeySpec(bytes(requestId), "HmacSHA256"));
        byte[] prk = extract.doFinal(shared);
        try {
            Mac expand = Mac.getInstance("HmacSHA256");
            expand.init(new SecretKeySpec(prk, "HmacSHA256"));
            expand.update(bytes(direction));
            return expand.doFinal(new byte[]{1});
        } finally { Arrays.fill(prk, (byte) 0); }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    static final class Request {
        final String requestId, clientPublicKeyB64, nonceB64, ciphertextB64, tagB64;
        final long createdAtUnixMs, expiresAtUnixMs;
        final Context context;
        Request(String id, String client, String nonce, String ciphertext, String tag, long created, long expires, Context context) {
            requestId = id; clientPublicKeyB64 = client; nonceB64 = nonce; ciphertextB64 = ciphertext; tagB64 = tag;
            createdAtUnixMs = created; expiresAtUnixMs = expires; this.context = context;
        }
    }

    static final class Response {
        final String schema, requestId, serverPublicKeyB64, nonceB64, ciphertextB64, tagB64;
        final long ciphertextBytes;
        Response(String schema, String id, String server, String nonce, String ciphertext, String tag, long length) {
            this.schema = schema; requestId = id; serverPublicKeyB64 = server; nonceB64 = nonce;
            ciphertextB64 = ciphertext; tagB64 = tag; ciphertextBytes = length;
        }
    }

    static final class Context implements AutoCloseable {
        private final String requestId, pinnedSpki;
        private final byte[] shared;
        private boolean consumed;
        Context(String requestId, String pin, byte[] shared) { this.requestId = requestId; pinnedSpki = pin; this.shared = shared; }
        synchronized byte[] decrypt(Response response) throws Exception {
            if (consumed) throw new SecurityException("E2E_RESPONSE_ALREADY_CONSUMED");
            consumed = true;
            byte[] key = null;
            try {
                if (!RESPONSE_SCHEMA.equals(response.schema) || !requestId.equals(response.requestId))
                    throw new SecurityException("E2E_RESPONSE_CORRELATION");
                if (!pinnedSpki.equals(response.serverPublicKeyB64)) throw new SecurityException("E2E_SERVER_PIN_MISMATCH");
                byte[] nonce = decode(response.nonceB64, 12);
                byte[] tag = decode(response.tagB64, 16);
                byte[] ciphertext = decode(response.ciphertextB64, MAX_CIPHERTEXT);
                if (nonce.length != 12 || tag.length != 16 || response.ciphertextBytes != ciphertext.length)
                    throw new SecurityException("E2E_RESPONSE_LENGTH");
                byte[] sealed = Arrays.copyOf(ciphertext, ciphertext.length + tag.length);
                System.arraycopy(tag, 0, sealed, ciphertext.length, tag.length);
                key = hkdf(shared, requestId, "aio.private-gateway.v1.s2c");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                cipher.updateAAD(bytes(requestId + "\nresponse\n" + response.serverPublicKeyB64));
                return cipher.doFinal(sealed);
            } finally {
                if (key != null) Arrays.fill(key, (byte) 0);
                Arrays.fill(shared, (byte) 0);
            }
        }
        @Override public synchronized void close() { consumed = true; Arrays.fill(shared, (byte) 0); }
    }
}
