package com.aio.founder;

import java.util.UUID;

/** Exact ordinary Dialogue submit shape. Caller objective fields are impossible here. */
final class FounderDialogueSubmit {
    static final String SCHEMA = "aio.founder-dialogue.submit.v1";
    final String intentId, text, privacyClass, provider, lease, presenceWitness;

    FounderDialogueSubmit(String intentId, String text, String privacyClass,
                          String provider, String lease, String presenceWitness) {
        if (intentId == null) throw new IllegalArgumentException("INTENT_ID_INVALID");
        try {
            if (!UUID.fromString(intentId).toString().equalsIgnoreCase(intentId))
                throw new IllegalArgumentException("INTENT_ID_INVALID");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("INTENT_ID_INVALID");
        }
        if (text == null || text.trim().isEmpty() || text.length() > 4096)
            throw new IllegalArgumentException("TEXT_INVALID");
        if (!"LOCAL_ONLY".equals(privacyClass) && !"TRADE_SECRET_LOCAL_ONLY".equals(privacyClass))
            throw new SecurityException("PRIVACY_DENIED");
        if (!"AIO".equals(provider)) throw new SecurityException("PROVIDER_DENIED");
        if (lease == null || lease.isBlank() || presenceWitness == null || presenceWitness.isBlank())
            throw new SecurityException("AUTHORITY_PROOF_REQUIRED");
        this.intentId=intentId; this.text=text; this.privacyClass=privacyClass;
        this.provider=provider; this.lease=lease; this.presenceWitness=presenceWitness;
    }
}
