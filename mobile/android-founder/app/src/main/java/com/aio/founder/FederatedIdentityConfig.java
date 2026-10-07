package com.aio.founder;

/**
 * Federated identity compatibility boundary.
 *
 * Google/Microsoft/ChatGPT implementations are activated only when a real provider registration
 * is configured. No provider password or session cookie is stored by AIO.
 */
final class FederatedIdentityConfig {
    static final String GOOGLE_WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID;
    static final String MICROSOFT_CLIENT_ID = BuildConfig.MICROSOFT_CLIENT_ID;
    static final String CHATGPT_CLIENT_ID = BuildConfig.CHATGPT_CLIENT_ID;

    static boolean googleConfigured() {
        return GOOGLE_WEB_CLIENT_ID != null && !GOOGLE_WEB_CLIENT_ID.isBlank();
    }
    static boolean microsoftConfigured() {
        return MICROSOFT_CLIENT_ID != null && !MICROSOFT_CLIENT_ID.isBlank();
    }
    static boolean chatgptConfigured() {
        return CHATGPT_CLIENT_ID != null && !CHATGPT_CLIENT_ID.isBlank();
    }

    private FederatedIdentityConfig() {}
}
