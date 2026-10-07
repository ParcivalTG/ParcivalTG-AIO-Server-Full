package com.aio.founder;

/**
 * AIO-native internal Presence representation.
 * Android framework objects and wire JSON remain outside this state boundary.
 */
final class AioPresenceField {
    enum Phase { DORMANT, PAIRED, CONNECTING, AUTHENTICATED, READY, HELD, FAILED }

    private Phase phase = Phase.DORMANT;
    private long lastLatencyMs = -1;
    private String principal = "";
    private String coreSchema = "";
    private String evidence = "AIO_NATIVE_FIELD_CREATED";

    synchronized void transition(Phase next, String reason) {
        phase = next;
        evidence = reason == null ? "" : reason;
    }

    synchronized void absorbHello(String observedPrincipal, String observedCoreSchema) {
        principal = bounded(observedPrincipal,256,"PRESENCE_PRINCIPAL_INVALID");
        coreSchema = bounded(observedCoreSchema,128,"PRESENCE_SCHEMA_INVALID");
        phase = Phase.AUTHENTICATED;
        evidence = "PRESENCE_HELLO_OBSERVED_E2E_PIN_STILL_REQUIRED";
    }

    synchronized void absorbStatus(boolean ready, String observedCoreSchema, long latencyMs) {
        if (phase != Phase.AUTHENTICATED && phase != Phase.READY && phase != Phase.HELD)
            throw new IllegalStateException("PRESENCE_STATUS_BEFORE_HELLO");
        if (latencyMs < 0 || latencyMs > 120_000) throw new IllegalArgumentException("PRESENCE_LATENCY_INVALID");
        lastLatencyMs = latencyMs;
        coreSchema = bounded(observedCoreSchema,128,"PRESENCE_SCHEMA_INVALID");
        phase = ready ? Phase.READY : Phase.HELD;
        evidence = "PRESENCE_STATUS_OBSERVED";
    }

    synchronized AndroidShadow projectAndroidShadow() {
        String summary = "phase=" + phase +
                (lastLatencyMs >= 0 ? " | " + lastLatencyMs + " ms" : "") +
                (!principal.isEmpty() ? "\nprincipal=" + principal : "") +
                (!coreSchema.isEmpty() ? "\ncore=" + coreSchema : "");
        return new AndroidShadow(phase.name(), summary, evidence, phase == Phase.READY);
    }

    private static String bounded(String value,int maximum,String code) {
        if (value == null) throw new IllegalArgumentException(code);
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > maximum) throw new IllegalArgumentException(code);
        return clean;
    }

    static final class AndroidShadow {
        final String status;
        final String summary;
        final String evidence;
        final boolean ready;
        AndroidShadow(String status, String summary, String evidence, boolean ready) {
            this.status = status; this.summary = summary; this.evidence = evidence; this.ready = ready;
        }
    }
}
