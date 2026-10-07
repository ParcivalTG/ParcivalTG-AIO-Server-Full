package com.aio.founder;

/** Compatibility identifiers only. Ordinary Dialogue uses FounderDialogueSubmit. */
final class FounderIntentCapsule {
    static final String SCHEMA = "aio.founder-intent.v2";
    static final String ACTION = "windows.objective.submit";
    static final String STATUS_ACTION = "founder.intent.status";
    static final String RESULT_ACTION = "founder.result.read";
    private FounderIntentCapsule() {}
}
