package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.client.download.DlcLightHash;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lightweight fingerprint: SHA-1 over size + head/tail 64 KB; small files are hashed completely. */
public final class DlcFxLightHash {
    static final int SEGMENT = DlcLightHash.SEGMENT;

    private DlcFxLightHash() {
    }

    public static String compute(Path file) throws IOException {
        return DlcLightHash.compute(file);
    }

    /** Stores local and last-verified server fingerprints keyed by DLC-relative path, size and mtime. */
    public static final class Cache {
        private static final String LOCAL = "local:";
        private static final String SERVER = "server:";
        private final Path path;
        private final JsonObject root;

        private Cache(Path path, JsonObject root) {
            this.path = path;
            this.root = root == null ? new JsonObject() : root;
        }

        public static Cache load(Path path) {
            return new Cache(path, path == null ? null : DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC));
        }

        public static Cache memory() {
            return new Cache(null, null);
        }

        public synchronized String local(String rel, Path file) throws IOException {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            String cached = lookup(LOCAL + rel, size, modified);
            if (cached != null) {
                return cached;
            }
            String hash = compute(file);
            put(LOCAL + rel, size, modified, hash);
            return hash;
        }

        public synchronized String server(String rel, long size, long modified) {
            return lookup(SERVER + rel, size, modified);
        }

        public synchronized void putServer(String rel, long size, long modified, String hash) {
            put(SERVER + rel, size, modified, hash);
        }

        public synchronized boolean save() {
            if (path == null) {
                return true;
            }
            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
            } catch (IOException ignored) {
                return false;
            }
            return DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC);
        }

        private String lookup(String key, long size, long modified) {
            JsonElement element = root.get(key);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject record = element.getAsJsonObject();
            if (parseLong(record, "size") != size || parseLong(record, "modified") != modified) {
                return null;
            }
            JsonElement hash = record.get("hash");
            return hash != null && hash.isJsonPrimitive() && !hash.getAsString().isBlank() ? hash.getAsString() : null;
        }

        private void put(String key, long size, long modified, String hash) {
            if (hash == null || hash.isBlank()) {
                root.remove(key);
                return;
            }
            JsonObject record = new JsonObject();
            record.addProperty("size", Long.toString(size));
            record.addProperty("modified", Long.toString(modified));
            record.addProperty("hash", hash);
            root.add(key, record);
        }

        private static long parseLong(JsonObject record, String key) {
            JsonElement element = record.get(key);
            if (element == null || !element.isJsonPrimitive()) {
                return Long.MIN_VALUE;
            }
            try {
                return Long.parseLong(element.getAsString().trim());
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        }
    }
}
