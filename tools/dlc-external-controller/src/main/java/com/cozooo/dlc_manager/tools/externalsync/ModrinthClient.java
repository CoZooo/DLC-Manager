package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ModrinthClient {
    private final HttpJson http;

    ModrinthClient(int timeoutMs) {
        this.http = new HttpJson(timeoutMs);
    }

    SyncModels.DownloadCandidate resolve(String projectId, String versionIdOrNumber, String minecraftVersion, String loader) throws ExternalSyncException {
        if (versionIdOrNumber == null || versionIdOrNumber.isBlank()) {
            return null;
        }
        Version version = looksLikeVersionId(versionIdOrNumber)
            ? fetchVersion(versionIdOrNumber.trim())
            : fetchVersionByNumber(projectId, versionIdOrNumber.trim(), minecraftVersion, loader);
        if (version == null || version.url == null || version.url.isBlank()) {
            return null;
        }
        SyncModels.DownloadCandidate candidate = new SyncModels.DownloadCandidate();
        candidate.source = "modrinth";
        candidate.url = version.url;
        candidate.fileName = version.fileName;
        candidate.sha1 = version.sha1;
        candidate.sha512 = version.sha512;
        candidate.size = version.size;
        return candidate;
    }

    private Version fetchVersion(String versionId) throws ExternalSyncException {
        JsonObject object = http.getObject("https://api.modrinth.com/v2/version/" + encode(versionId), null);
        return readVersion(object);
    }

    private Version fetchVersionByNumber(String projectId, String versionNumber, String minecraftVersion, String loader) throws ExternalSyncException {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        String endpoint = "https://api.modrinth.com/v2/project/" + encode(projectId) + "/version";
        String separator = "?";
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            endpoint += separator + "game_versions=[%22" + encode(minecraftVersion) + "%22]";
            separator = "&";
        }
        if (loader != null && !loader.isBlank()) {
            endpoint += separator + "loaders=[%22" + encode(loader.toLowerCase()) + "%22]";
        }
        JsonArray versions = JsonParser.parseString(http.getString(endpoint, null)).getAsJsonArray();
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String number = string(object, "version_number");
            String name = string(object, "name");
            if (versionNumber.equalsIgnoreCase(number) || containsIgnoreCase(name, versionNumber)) {
                return readVersion(object);
            }
        }
        return null;
    }

    private static Version readVersion(JsonObject object) {
        JsonArray files = array(object, "files");
        if (files == null || files.isEmpty()) {
            return null;
        }
        JsonObject selected = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            if (selected == null || bool(file, "primary")) {
                selected = file;
            }
            if (bool(file, "primary")) {
                break;
            }
        }
        if (selected == null) {
            return null;
        }
        Version version = new Version();
        version.fileName = string(selected, "filename");
        version.url = string(selected, "url");
        version.size = longValue(selected, "size", -1L);
        JsonObject hashes = object(selected, "hashes");
        if (hashes != null) {
            version.sha1 = string(hashes, "sha1");
            version.sha512 = string(hashes, "sha512");
        }
        return version;
    }

    private static boolean looksLikeVersionId(String value) {
        return value != null && value.length() >= 8 && value.matches("[A-Za-z0-9_-]+") && !value.matches(".*[.+].*");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase().contains(needle.toLowerCase());
    }

    private static final class Version {
        String fileName;
        String url;
        String sha1;
        String sha512;
        long size = -1L;
    }
}
