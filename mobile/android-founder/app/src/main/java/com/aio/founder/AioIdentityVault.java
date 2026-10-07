package com.aio.founder;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AIO-native identity vault.
 *
 * Provider identities (Google/Microsoft/ChatGPT) are projections at the edge.
 * Internally AIO stores only bounded identity descriptors and sealed provider tokens.
 */
final class AioIdentityVault {
    enum Provider { AIO_LOCAL, GOOGLE, MICROSOFT, CHATGPT }

    static final class Identity {
        final String id;
        final Provider provider;
        final String displayName;
        final String email;
        final boolean active;

        Identity(String id, Provider provider, String displayName, String email, boolean active) {
            this.id = id;
            this.provider = provider;
            this.displayName = displayName;
            this.email = email;
            this.active = active;
        }
    }

    private final SharedPreferences prefs;

    AioIdentityVault(Context context) {
        prefs = context.getSharedPreferences("aio_identity_vault_v0", Context.MODE_PRIVATE);
        ensureLocalIdentity();
    }

    private void ensureLocalIdentity() {
        if (!prefs.contains("identities")) {
            try {
                JSONArray rows = new JSONArray();
                JSONObject local = new JSONObject();
                local.put("id", "aio-founder-local");
                local.put("provider", Provider.AIO_LOCAL.name());
                local.put("displayName", "AIO Founder");
                local.put("email", "");
                local.put("active", true);
                rows.put(local);
                prefs.edit().putString("identities", rows.toString()).apply();
            } catch (Exception ignored) {}
        }
    }

    synchronized List<Identity> list() {
        List<Identity> result = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(prefs.getString("identities", "[]"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                result.add(new Identity(
                        row.optString("id"),
                        Provider.valueOf(row.optString("provider", Provider.AIO_LOCAL.name())),
                        row.optString("displayName"),
                        row.optString("email"),
                        row.optBoolean("active", false)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    synchronized void upsert(String id, Provider provider, String displayName, String email, boolean makeActive) throws Exception {
        JSONArray rows = new JSONArray(prefs.getString("identities", "[]"));
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (makeActive) row.put("active", false);
            if (id.equals(row.optString("id"))) {
                row.put("provider", provider.name());
                row.put("displayName", displayName == null ? "" : displayName);
                row.put("email", email == null ? "" : email);
                row.put("active", makeActive);
                replaced = true;
            }
            next.put(row);
        }
        if (!replaced) {
            JSONObject row = new JSONObject();
            row.put("id", id);
            row.put("provider", provider.name());
            row.put("displayName", displayName == null ? "" : displayName);
            row.put("email", email == null ? "" : email);
            row.put("active", makeActive);
            next.put(row);
        }
        prefs.edit().putString("identities", next.toString()).apply();
    }

    synchronized void activate(String id) throws Exception {
        JSONArray rows = new JSONArray(prefs.getString("identities", "[]"));
        JSONArray next = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            row.put("active", id.equals(row.optString("id")));
            next.put(row);
        }
        prefs.edit().putString("identities", next.toString()).apply();
    }

    synchronized Identity active() {
        for (Identity i : list()) if (i.active) return i;
        return list().isEmpty() ? null : list().get(0);
    }

    synchronized void removeProviderIdentity(String id) throws Exception {
        JSONArray rows = new JSONArray(prefs.getString("identities", "[]"));
        JSONArray next = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (id.equals(row.optString("id")) && !Provider.AIO_LOCAL.name().equals(row.optString("provider")))
                continue;
            next.put(row);
        }
        prefs.edit().putString("identities", next.toString()).apply();
    }
}
