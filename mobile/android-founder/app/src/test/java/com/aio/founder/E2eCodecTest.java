package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Properties;
import javax.crypto.AEADBadTagException;

public class E2eCodecTest {
    private Properties golden() throws Exception {
        Properties result = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/dotnet-e2e-golden.properties")) {
            assertNotNull("Independent .NET fixture must be present", input); result.load(input);
        }
        return result;
    }
    private KeyPair publicTestClient() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        // Scalar 1 and generator are publicly known test fixtures, never pairing material.
        return new KeyPair(factory.generatePublic(new ECPublicKeySpec(curve.getGenerator(), curve)),
                factory.generatePrivate(new ECPrivateKeySpec(BigInteger.ONE, curve)));
    }
    private E2eCodec.Request request(Properties fixture) throws Exception {
        return E2eCodec.encryptWithKeyPair(fixture.getProperty("serverPublicKeyB64"), fixture.getProperty("requestId"),
                Long.parseLong(fixture.getProperty("createdAtUnixMs")), Long.parseLong(fixture.getProperty("expiresAtUnixMs")),
                fixture.getProperty("commandPlain").getBytes(StandardCharsets.UTF_8), publicTestClient(),
                E2eCodec.decode(fixture.getProperty("requestNonceB64"), 12));
    }
    private E2eCodec.Response response(Properties fixture) {
        return new E2eCodec.Response(E2eCodec.RESPONSE_SCHEMA, fixture.getProperty("requestId"), fixture.getProperty("serverPublicKeyB64"),
                fixture.getProperty("responseNonceB64"), fixture.getProperty("responseCiphertextB64"),
                fixture.getProperty("responseTagB64"), Long.parseLong(fixture.getProperty("responseCiphertextBytes")));
    }
    @Test public void requestExactlyMatchesIndependentDotNetVector() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        try {
            assertEquals(fixture.getProperty("clientPublicKeyB64"), request.clientPublicKeyB64);
            assertEquals(fixture.getProperty("requestNonceB64"), request.nonceB64);
            assertEquals(fixture.getProperty("requestCiphertextB64"), request.ciphertextB64);
            assertEquals(fixture.getProperty("requestTagB64"), request.tagB64);
        } finally { request.context.close(); }
    }
    @Test public void decryptsIndependentDotNetResponse() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        assertEquals(fixture.getProperty("responsePlain"), new String(request.context.decrypt(response(fixture)), StandardCharsets.UTF_8));
    }
    @Test(expected = AEADBadTagException.class) public void alteredResponseTagFailsClosed() throws Exception {
        Properties fixture = golden(); byte[] tag = E2eCodec.decode(fixture.getProperty("responseTagB64"), 16);
        tag[0] ^= 1; fixture.setProperty("responseTagB64", E2eCodec.encode(tag));
        request(fixture).context.decrypt(response(fixture));
    }
    @Test(expected = AEADBadTagException.class) public void alteredResponseCiphertextFailsClosed() throws Exception {
        Properties fixture = golden(); byte[] ciphertext = E2eCodec.decode(fixture.getProperty("responseCiphertextB64"), E2eCodec.MAX_CIPHERTEXT);
        ciphertext[0] ^= 1; fixture.setProperty("responseCiphertextB64", E2eCodec.encode(ciphertext));
        request(fixture).context.decrypt(response(fixture));
    }
    @Test(expected = SecurityException.class) public void responseCannotReplaceTrustedServerPin() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        fixture.setProperty("serverPublicKeyB64", fixture.getProperty("clientPublicKeyB64"));
        request.context.decrypt(response(fixture));
    }
    @Test(expected = SecurityException.class) public void responseMustMatchRequestId() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        fixture.setProperty("requestId", "other-request"); request.context.decrypt(response(fixture));
    }
    @Test(expected = SecurityException.class) public void authenticatedResponseIsSingleUse() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        E2eCodec.Response response = response(fixture); request.context.decrypt(response); request.context.decrypt(response);
    }
    @Test(expected = SecurityException.class) public void cancellationDestroysResponseAuthority() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        request.context.close(); request.context.decrypt(response(fixture));
    }
    @Test(expected = IllegalArgumentException.class) public void noncanonicalPinFailsClosed() throws Exception {
        E2eCodec.pinnedKey(golden().getProperty("serverPublicKeyB64") + "=");
    }
    @Test(expected = SecurityException.class) public void wrongCurveCannotBecomeServerPin() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp384r1"));
        E2eCodec.pinnedKey(E2eCodec.encode(generator.generateKeyPair().getPublic().getEncoded()));
    }
    @Test(expected = IllegalArgumentException.class) public void requestExpiryIsBounded() throws Exception {
        Properties fixture = golden();
        E2eCodec.encryptWithKeyPair(fixture.getProperty("serverPublicKeyB64"),fixture.getProperty("requestId"),100,60101,
                new byte[]{1},publicTestClient(),new byte[12]);
    }
    @Test(expected = SecurityException.class) public void responseDeclaredLengthCannotLie() throws Exception {
        Properties fixture = golden(); E2eCodec.Request request = request(fixture);
        fixture.setProperty("responseCiphertextBytes", "1"); request.context.decrypt(response(fixture));
    }
}
