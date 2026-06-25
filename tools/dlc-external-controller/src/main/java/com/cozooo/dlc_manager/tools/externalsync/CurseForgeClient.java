package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CurseForgeClient {
    private static final int API_KEY_SHIFT = 7;
    private static final byte API_KEY_MASK = (byte) 0x5A;
    private static final byte[] API_KEY_PART1 = new byte[] {
        (byte)0x49, (byte)0x04, (byte)0xBC, (byte)0x56, (byte)0x16, (byte)0x94, (byte)0x84, (byte)0xB9,
        (byte)0x31, (byte)0xB9, (byte)0x9B, (byte)0x63, (byte)0x5F, (byte)0x5A, (byte)0x12, (byte)0xFF,
        (byte)0xFA, (byte)0x7F, (byte)0xE3, (byte)0xD6, (byte)0xF6, (byte)0x08, (byte)0xC4, (byte)0x30,
        (byte)0x42, (byte)0xA8, (byte)0x83, (byte)0xE3, (byte)0x9A, (byte)0x6C, (byte)0x42, (byte)0x53,
        (byte)0x0A, (byte)0xFE, (byte)0xDE, (byte)0x1F, (byte)0xC5, (byte)0x9C, (byte)0x7A, (byte)0x8A,
        (byte)0xC6, (byte)0x66, (byte)0x28, (byte)0x30, (byte)0x3B, (byte)0xA3, (byte)0xD4, (byte)0x7B,
        (byte)0x43, (byte)0x7C, (byte)0x19, (byte)0x57, (byte)0x03, (byte)0xC2, (byte)0x26, (byte)0x3F,
        (byte)0x54, (byte)0xFF, (byte)0x5C, (byte)0xF2
    };
    private static final int API_KEY_PART2_SEED = 0xA5;
    private static final String CDN_HOST = "https://mediafilez.forgecdn.net";
    private static String apiKeyCache;

    private final HttpJson http;

    CurseForgeClient(int timeoutMs) {
        this.http = new HttpJson(timeoutMs);
    }

    List<SyncModels.DownloadCandidate> resolve(String projectId, String fileId, String minecraftVersion, String loader) throws ExternalSyncException {
        if (fileId == null || fileId.isBlank()) {
            return List.of();
        }
        CurseForgeFile file = isNumeric(fileId) ? fetchFile(projectId, fileId.trim()) : fetchFileByVersion(projectId, fileId.trim(), minecraftVersion, loader);
        if (file == null) {
            return List.of();
        }
        List<SyncModels.DownloadCandidate> candidates = new ArrayList<>();
        addCandidate(candidates, file.url, file);
        for (String alternateUrl : file.alternateUrls) {
            addCandidate(candidates, alternateUrl, file);
        }
        return candidates;
    }

    private CurseForgeFile fetchFile(String projectId, String fileId) throws ExternalSyncException {
        String endpoint = projectId == null || projectId.isBlank()
            ? "https://api.curseforge.com/v1/mods/files/" + encode(fileId)
            : "https://api.curseforge.com/v1/mods/" + encode(projectId) + "/files/" + encode(fileId);
        JsonObject root = http.getObject(endpoint, Map.of("x-api-key", apiKey()));
        JsonObject data = object(root, "data");
        if (data == null) {
            return null;
        }
        CurseForgeFile file = readFile(data);
        file.id = firstNonBlank(file.id, fileId);
        if ((file.url == null || file.url.isBlank()) && projectId != null && !projectId.isBlank()) {
            file.url = fetchDownloadUrl(projectId, fileId);
        }
        addFallbacks(projectId, file);
        return file;
    }

    private CurseForgeFile fetchFileByVersion(String projectId, String version, String minecraftVersion, String loader) throws ExternalSyncException {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + encode(projectId) + "/files?pageSize=50";
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            endpoint += "&gameVersion=" + encode(minecraftVersion);
        }
        JsonObject root = http.getObject(endpoint, Map.of("x-api-key", apiKey()));
        JsonArray data = array(root, "data");
        if (data == null) {
            return null;
        }
        String needle = version.toLowerCase();
        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!matchesGameVersion(object, minecraftVersion, loader)) {
                continue;
            }
            String fileName = string(object, "fileName");
            String displayName = string(object, "displayName");
            if (!containsLower(fileName, needle) && !containsLower(displayName, needle)) {
                continue;
            }
            CurseForgeFile file = readFile(object);
            if ((file.url == null || file.url.isBlank()) && file.id != null && !file.id.isBlank()) {
                file.url = fetchDownloadUrl(projectId, file.id);
            }
            addFallbacks(projectId, file);
            return file;
        }
        return null;
    }

    private String fetchDownloadUrl(String projectId, String fileId) {
        try {
            String endpoint = "https://api.curseforge.com/v1/mods/" + encode(projectId) + "/files/" + encode(fileId) + "/download-url";
            return http.getString(endpoint, Map.of("x-api-key", apiKey())).replace("\"", "").trim();
        } catch (ExternalSyncException ignored) {
            return null;
        }
    }

    private static CurseForgeFile readFile(JsonObject data) {
        CurseForgeFile file = new CurseForgeFile();
        file.id = string(data, "id");
        file.fileName = string(data, "fileName");
        file.url = string(data, "downloadUrl");
        file.size = longValue(data, "fileLength", -1L);
        JsonArray hashes = array(data, "hashes");
        if (hashes != null) {
            for (JsonElement element : hashes) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject hash = element.getAsJsonObject();
                int algo = (int) longValue(hash, "algo", -1L);
                String value = string(hash, "value");
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (algo == 1 && (file.sha1 == null || file.sha1.isBlank())) {
                    file.sha1 = value;
                } else if (algo == 2 && (file.md5 == null || file.md5.isBlank())) {
                    file.md5 = value;
                }
            }
        }
        return file;
    }

    private static void addFallbacks(String projectId, CurseForgeFile file) {
        if (file == null || projectId == null || projectId.isBlank() || file.id == null || file.id.isBlank()) {
            return;
        }
        String website = "https://www.curseforge.com/api/v1/mods/" + projectId + "/files/" + file.id + "/download";
        if (!website.equals(file.url)) {
            file.alternateUrls.add(website);
        }
        if (file.fileName != null && !file.fileName.isBlank()) {
            String cdn = forgeCdnUrl(file.id, file.fileName);
            if (cdn != null && !cdn.equals(file.url)) {
                file.alternateUrls.add(cdn);
            }
        }
    }

    private static void addCandidate(List<SyncModels.DownloadCandidate> candidates, String url, CurseForgeFile file) {
        if (url == null || url.isBlank() || candidates.stream().anyMatch(candidate -> url.equals(candidate.url))) {
            return;
        }
        SyncModels.DownloadCandidate candidate = new SyncModels.DownloadCandidate();
        candidate.source = "curseforge";
        candidate.url = url;
        candidate.fileName = file.fileName;
        candidate.sha1 = file.sha1;
        candidate.md5 = file.md5;
        candidate.size = file.size;
        candidates.add(candidate);
    }

    private static boolean matchesGameVersion(JsonObject data, String minecraftVersion, String loader) {
        JsonArray versions = array(data, "gameVersions");
        if (versions == null) {
            return true;
        }
        if (minecraftVersion != null && !minecraftVersion.isBlank() && !arrayContains(versions, minecraftVersion)) {
            return false;
        }
        return loader == null || loader.isBlank() || arrayContains(versions, loader);
    }

    private static boolean arrayContains(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && value.equalsIgnoreCase(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String apiKey() {
        if (apiKeyCache != null) {
            return apiKeyCache;
        }
        byte[] part2 = generatePart2(API_KEY_PART1.length);
        int length = API_KEY_PART1.length;
        byte[] decoded = new byte[length];
        for (int index = 0; index < length; index++) {
            byte mixed = (byte) (API_KEY_PART1[index] ^ part2[index]);
            decoded[index] = (byte) (mixed ^ API_KEY_MASK ^ ((index * 31) & 0xFF));
        }
        byte[] shifted = new byte[length];
        for (int index = 0; index < length; index++) {
            shifted[(index + API_KEY_SHIFT) % length] = decoded[index];
        }
        apiKeyCache = new String(shifted, StandardCharsets.UTF_8);
        return apiKeyCache;
    }

    private static byte[] generatePart2(int length) {
        byte[] part2 = new byte[length];
        int seed = API_KEY_PART2_SEED & 0xFF;
        for (int index = 0; index < length; index++) {
            seed = (seed * 1103515245 + 12345) & 0xFF;
            part2[index] = (byte) seed;
        }
        return part2;
    }

    private static String forgeCdnUrl(String fileId, String fileName) {
        try {
            long id = Long.parseLong(fileId.trim());
            return CDN_HOST + "/files/" + (id / 1000L) + "/" + (id % 1000L) + "/" + encode(fileName).replace("+", "%20");
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private static boolean isNumeric(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }

    private static boolean containsLower(String value, String lowerNeedle) {
        return value != null && lowerNeedle != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class CurseForgeFile {
        String id;
        String fileName;
        String url;
        String sha1;
        String md5;
        long size = -1L;
        List<String> alternateUrls = new ArrayList<>();
    }
}
