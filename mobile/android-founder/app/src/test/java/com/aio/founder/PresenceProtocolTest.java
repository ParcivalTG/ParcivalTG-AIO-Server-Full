package com.aio.founder;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PresenceProtocolTest {
    @Test public void witnessMatchesKnownVector() throws Exception {
        assertEquals(
            "Rh0HMEz6tiptlSEkjZxOGg0F2NyxSP7rPoZmgvmPTRs",
            PresenceProtocol.witness("client", 1700000000000L, "nonce",
                "secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void dotNetGuidByteLayoutMatchesSystemGuid() {
        UUID id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        byte[] actual = PresenceProtocol.dotNetGuidBytes(id);
        byte[] expected = new byte[] {
            0x33,0x22,0x11,0x00,0x55,0x44,0x77,0x66,
            (byte)0x88,(byte)0x99,(byte)0xaa,(byte)0xbb,
            (byte)0xcc,(byte)0xdd,(byte)0xee,(byte)0xff
        };
        assertArrayEquals(expected, actual);
    }

    @Test public void frameRoundTripsWithDotNetGuidLayout() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] frame = PresenceProtocol.frame(
            PresenceProtocol.HELLO, "x".getBytes(StandardCharsets.UTF_8), id);
        assertEquals(28 + 1, frame.length);
        assertEquals("AIOP", new String(frame, 0, 4, StandardCharsets.US_ASCII));
        PresenceProtocol.Frame parsed =
            PresenceProtocol.readFrame(new ByteArrayInputStream(frame));
        assertEquals(id, parsed.id);
        assertEquals(PresenceProtocol.HELLO, parsed.type);
        assertEquals(0, parsed.flags);
        assertArrayEquals("x".getBytes(StandardCharsets.UTF_8), parsed.payload);
    }

    @Test public void base64UrlWitnessSecretDecodes() {
        byte[] source = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(source);
        assertArrayEquals(source, PresenceProtocol.decodeWitnessSecret(encoded));
    }

    @Test(expected = java.io.IOException.class) public void reservedHeaderByteIsRejected() throws Exception {
        byte[] frame = PresenceProtocol.frame(PresenceProtocol.E2E, new byte[0], UUID.randomUUID());
        frame[7] = 1; PresenceProtocol.readFrame(new ByteArrayInputStream(frame));
    }
    @Test(expected = java.io.IOException.class) public void unsupportedVersionIsRejected() throws Exception {
        byte[] frame = PresenceProtocol.frame(PresenceProtocol.E2E, new byte[0], UUID.randomUUID());
        frame[4] = 2; PresenceProtocol.readFrame(new ByteArrayInputStream(frame));
    }
    @Test(expected = java.io.IOException.class) public void oversizedIncomingPayloadIsRejectedBeforeAllocation() throws Exception {
        byte[] frame = PresenceProtocol.frame(PresenceProtocol.E2E, new byte[0], UUID.randomUUID());
        java.nio.ByteBuffer.wrap(frame,24,4).putInt(PresenceProtocol.MAX_PAYLOAD + 1);
        PresenceProtocol.readFrame(new ByteArrayInputStream(frame));
    }
    @Test(expected = java.io.IOException.class) public void truncatedPayloadIsRejected() throws Exception {
        byte[] frame = PresenceProtocol.frame(PresenceProtocol.E2E, new byte[]{1,2}, UUID.randomUUID());
        PresenceProtocol.readFrame(new ByteArrayInputStream(java.util.Arrays.copyOf(frame,frame.length - 1)));
    }
}
