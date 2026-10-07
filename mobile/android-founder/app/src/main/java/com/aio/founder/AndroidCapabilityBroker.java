package com.aio.founder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Governed Android file-capability broker using only platform SAF APIs.
 * The assistant never receives a raw filesystem path or arbitrary shell.
 */
final class AndroidCapabilityBroker {
    static final String CAP_LIST = "android.file.list";
    static final String CAP_READ = "android.file.read";
    static final String CAP_WRITE = "android.file.write";
    static final String CAP_RENAME = "android.file.rename";
    static final String CAP_DELETE = "android.file.delete";
    static final String CAP_SHA256 = "android.file.sha256";

    private static final int MAX_BYTES = 1_048_576;

    private final Context context;
    private final ContentResolver resolver;
    private Uri treeUri;

    AndroidCapabilityBroker(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        String persisted = this.context.getSharedPreferences(
                "aio_android_capabilities", Context.MODE_PRIVATE).getString("tree_uri", null);
        if (persisted != null) treeUri = Uri.parse(persisted);
    }

    boolean hasStorageGrant() { return treeUri != null; }
    Uri getTreeUri() { return treeUri; }

    void acceptStorageGrant(Intent data) {
        Uri uri = data == null ? null : data.getData();
        if (uri == null) throw new IllegalArgumentException("STORAGE_GRANT_MISSING");
        int sourceFlags=data.getFlags();
        boolean read=(sourceFlags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0;
        boolean write=(sourceFlags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;
        if(!read&&!write)throw new SecurityException("STORAGE_GRANT_PERMISSION_MISSING");
        if(read&&write)
            resolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        else if(read)
            resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        else
            resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        treeUri = uri;
        context.getSharedPreferences("aio_android_capabilities", Context.MODE_PRIVATE)
                .edit().putString("tree_uri", uri.toString()).apply();
    }

    void clearGrant() {
        if (treeUri != null) {
            try {
                resolver.releasePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
        }
        treeUri = null;
        context.getSharedPreferences("aio_android_capabilities", Context.MODE_PRIVATE)
                .edit().remove("tree_uri").apply();
    }

    List<Entry> list(String relativePath) throws IOException {
        Uri dir = resolve(relativePath, true);
        String docId = DocumentsContract.getDocumentId(dir);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        return queryEntries(children);
    }

    byte[] read(String relativePath, int maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes > MAX_BYTES) throw new IOException("READ_BUDGET_INVALID");
        Uri file = resolve(relativePath, false);
        try (InputStream in = resolver.openInputStream(file)) {
            if (in == null) throw new FileNotFoundException("READ_STREAM_UNAVAILABLE");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > maxBytes) throw new IOException("READ_BUDGET_EXCEEDED");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    Entry write(String parentPath, String name, String mimeType, byte[] content) throws IOException {
        validateName(name);
        if (content == null || content.length > MAX_BYTES) throw new IOException("WRITE_BUDGET_EXCEEDED");
        Uri parent = resolve(parentPath, true);
        Child existing = findChild(parent, name);
        Uri target = existing == null ? create(parent, name, mimeType) : existing.uri;
        if (existing != null && existing.directory) throw new IOException("TARGET_IS_DIRECTORY");

        try (OutputStream out = resolver.openOutputStream(target, "wt")) {
            if (out == null) throw new IOException("WRITE_STREAM_UNAVAILABLE");
            out.write(content);
            out.flush();
        }
        return stat(target);
    }

    Entry rename(String relativePath, String newName) throws IOException {
        validateName(newName);
        Uri source = resolve(relativePath, false);
        try {
            Uri renamed = DocumentsContract.renameDocument(resolver, source, newName);
            if (renamed == null) throw new IOException("RENAME_FAILED");
            return stat(renamed);
        } catch (FileNotFoundException ex) {
            throw new IOException("RENAME_FAILED", ex);
        }
    }

    void delete(String relativePath) throws IOException {
        Uri target = resolve(relativePath, false);
        try {
            if (!DocumentsContract.deleteDocument(resolver, target)) throw new IOException("DELETE_FAILED");
        } catch (FileNotFoundException ex) {
            throw new IOException("DELETE_FAILED", ex);
        }
    }

    String sha256(String relativePath) throws Exception {
        Uri file = resolve(relativePath, false);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = resolver.openInputStream(file)) {
            if (in == null) throw new FileNotFoundException("HASH_STREAM_UNAVAILABLE");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest.digest()) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private Uri create(Uri parent, String name, String mimeType) throws IOException {
        try {
            Uri uri = DocumentsContract.createDocument(
                    resolver, parent,
                    (mimeType == null || mimeType.isBlank()) ? "application/octet-stream" : mimeType,
                    name);
            if (uri == null) throw new IOException("CREATE_FAILED");
            return uri;
        } catch (FileNotFoundException ex) {
            throw new IOException("CREATE_FAILED", ex);
        }
    }

    private Uri rootDocument() throws IOException {
        if (treeUri == null) throw new IOException("STORAGE_GRANT_REQUIRED");
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        } catch (Exception ex) {
            throw new IOException("STORAGE_GRANT_INVALID", ex);
        }
    }

    private Uri resolve(String path, boolean requireDirectory) throws IOException {
        String normalized = normalize(path);
        Uri current = rootDocument();
        if (!normalized.isEmpty()) {
            for (String part : normalized.split("/")) {
                Child child = findChild(current, part);
                if (child == null) throw new FileNotFoundException("PATH_NOT_FOUND:" + part);
                current = child.uri;
            }
        }
        Entry e = stat(current);
        if (requireDirectory && !e.directory) throw new IOException("DIRECTORY_REQUIRED");
        if (!requireDirectory && e.directory) throw new IOException("FILE_REQUIRED");
        return current;
    }

    private Child findChild(Uri parent, String name) throws IOException {
        String docId = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        Child match = null;
        for (Entry e : queryEntries(children)) {
            if (!Objects.equals(e.name, name)) continue;
            if (match != null) throw new IOException("PATH_AMBIGUOUS:" + name);
            match = new Child(e.uri, e.directory);
        }
        return match;
    }

    private List<Entry> queryEntries(Uri uri) throws IOException {
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        List<Entry> rows = new ArrayList<>();
        try (Cursor c = resolver.query(uri, projection, null, null, null)) {
            if (c == null) throw new IOException("QUERY_FAILED");
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? 0L : c.getLong(3);
                long modified = c.isNull(4) ? 0L : c.getLong(4);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                rows.add(new Entry(name,
                        DocumentsContract.Document.MIME_TYPE_DIR.equals(mime),
                        size, modified, mime, child));
            }
        } catch (SecurityException ex) {
            throw new IOException("QUERY_PERMISSION_DENIED", ex);
        }
        return rows;
    }

    private Entry stat(Uri uri) throws IOException {
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor c = resolver.query(uri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) throw new FileNotFoundException("STAT_FAILED");
            String name = c.getString(0);
            String mime = c.getString(1);
            long size = c.isNull(2) ? 0L : c.getLong(2);
            long modified = c.isNull(3) ? 0L : c.getLong(3);
            return new Entry(name,
                    DocumentsContract.Document.MIME_TYPE_DIR.equals(mime),
                    size, modified, mime, uri);
        } catch (SecurityException ex) {
            throw new IOException("STAT_PERMISSION_DENIED", ex);
        }
    }

    static String normalize(String path) throws IOException {
        String p = path == null ? "" : path.replace('\\', '/').trim();
        if (p.isEmpty()) return "";
        if (p.startsWith("/") || p.endsWith("/"))
            throw new IOException("PATH_OUTSIDE_GRANTED_TREE");
        String[] parts = p.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IOException("PATH_OUTSIDE_GRANTED_TREE");
        }
        return String.join("/", parts);
    }

    private static void validateName(String name) throws IOException {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) throw new IOException("INVALID_NAME");
    }

    private static final class Child {
        final Uri uri; final boolean directory;
        Child(Uri uri, boolean directory) { this.uri = uri; this.directory = directory; }
    }

    static final class Entry {
        final String name;
        final boolean directory;
        final long bytes;
        final long modifiedMs;
        final String mimeType;
        final Uri uri;
        Entry(String name, boolean directory, long bytes, long modifiedMs, String mimeType, Uri uri) {
            this.name = name; this.directory = directory; this.bytes = bytes;
            this.modifiedMs = modifiedMs; this.mimeType = mimeType; this.uri = uri;
        }
    }
}
