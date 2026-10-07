package com.aio.founder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class PresenceProtocol {
    private PresenceProtocol() {}

    public static final int MAX_PAYLOAD = 1_600_000;
    public static final byte HELLO = 0x01;
    public static final byte HELLO_REPLY = 0x02;
    public static final byte E2E = 0x10;
    public static final byte E2E_REPLY = 0x11;
    public static final byte STATUS = 0x20;
    public static final byte STATUS_REPLY = 0x21;
    public static final byte PING = 0x30;
    public static final byte PING_REPLY = 0x31;
    public static final byte ANDROID_CAPABILITY_REQUEST = 0x40;
    public static final byte ANDROID_CAPABILITY_REPLY = 0x41;
    public static final byte ERROR = 0x7F;

    public static String newNonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    public static byte[] decodeWitnessSecret(String encoded) {
        String value = encoded == null ? "" : encoded.trim().replace('-', '+').replace('_', '/');
        while ((value.length() & 3) != 0) value += "=";
        return Base64.getDecoder().decode(value);
    }

    public static String witness(String clientId, long timestamp, String nonce, byte[] secret) throws Exception {
        String canonical = "AIO_PRESENCE_WITNESS_V0\n" + clientId + "\n" + timestamp + "\n" + nonce;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] frame(byte type, byte[] payload, UUID id) throws IOException {
        return frame(type,(byte)0,payload,id);
    }

    public static byte[] frame(byte type, byte flags, byte[] payload, UUID id) throws IOException {
        if (payload == null) payload = new byte[0];
        if ((flags & ~1) != 0) throw new IOException("Unsupported AIOP flags");
        if (payload.length > MAX_PAYLOAD) throw new IOException("AIOP payload too large");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(28 + payload.length);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("AIOP");
        out.writeByte(1);
        out.writeByte(type & 0xff);
        out.writeByte(flags & 0xff);
        out.writeByte(0); // reserved
        out.write(dotNetGuidBytes(id));
        out.writeInt(payload.length); // DataOutputStream = big-endian
        out.write(payload);
        out.flush();
        return bytes.toByteArray();
    }

    public static Frame readFrame(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);
        byte[] header = new byte[28];
        in.readFully(header);
        if (!Arrays.equals(Arrays.copyOfRange(header, 0, 4), new byte[]{'A','I','O','P'}))
            throw new IOException("Invalid AIOP magic");
        int version = header[4] & 0xff;
        if (version != 1) throw new IOException("Unsupported AIOP version " + version);
        if (header[7] != 0) throw new IOException("Unsupported AIOP reserved field");
        byte type = header[5];
        byte flags = header[6];
        UUID id = uuidFromDotNetBytes(Arrays.copyOfRange(header, 8, 24));
        int length = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (length < 0 || length > MAX_PAYLOAD) throw new IOException("Invalid AIOP payload length");
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new Frame(type, flags, id, payload);
    }

    static byte[] dotNetGuidBytes(UUID id) {
        ByteBuffer canonical = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        canonical.putLong(id.getMostSignificantBits());
        canonical.putLong(id.getLeastSignificantBits());
        byte[] c = canonical.array();
        return new byte[] {
            c[3], c[2], c[1], c[0],
            c[5], c[4],
            c[7], c[6],
            c[8], c[9], c[10], c[11], c[12], c[13], c[14], c[15]
        };
    }

    static UUID uuidFromDotNetBytes(byte[] b) throws IOException {
        if (b.length != 16) throw new IOException("GUID length");
        byte[] c = new byte[] {
            b[3], b[2], b[1], b[0],
            b[5], b[4],
            b[7], b[6],
            b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]
        };
        ByteBuffer bb = ByteBuffer.wrap(c).order(ByteOrder.BIG_ENDIAN);
        return new UUID(bb.getLong(), bb.getLong());
    }

    public static final class Frame {
        public final byte type;
        public final byte flags;
        public final UUID id;
        public final byte[] payload;
        Frame(byte type, byte flags, UUID id, byte[] payload) {
            this.type = type; this.flags = flags; this.id = id; this.payload = payload;
        }
    }
}
