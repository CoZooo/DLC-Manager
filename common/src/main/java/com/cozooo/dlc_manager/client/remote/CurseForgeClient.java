package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CurseForgeClient {
    private static final Gson GSON = new Gson();
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
    private static String API_KEY_CACHE;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(DlcPathConfig.getConnectTimeoutMs()))
        .build();
    private static final Map<String, CurseForgeFile> CACHE = new ConcurrentHashMap<>();

    private CurseForgeClient() {
    }

    public static CurseForgeFile fetchFile(String projectId, String fileId) throws IOException, InterruptedException {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String cacheKey = (projectId == null ? "" : projectId) + ":" + fileId;
        CurseForgeFile cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JsonObject data = fetchFileData(projectId, fileId, apiKey);
        if (data == null) {
            return null;
        }
        CurseForgeFile file = new CurseForgeFile();
        file.fileName = getString(data, "fileName");
        file.url = getString(data, "downloadUrl");
        if (file.url == null || file.url.isBlank()) {
            file.url = fetchDownloadUrl(projectId, fileId, apiKey);
        }
        CACHE.put(cacheKey, file);
        return file;
    }

    private static String getApiKey() {
        if (API_KEY_CACHE != null) {
            return API_KEY_CACHE;
        }
        byte[] part2 = generatePart2(API_KEY_PART1.length);
        if (API_KEY_PART1.length == 0 || part2.length == 0 || API_KEY_PART1.length != part2.length) {
            API_KEY_CACHE = "";
            return API_KEY_CACHE;
        }
        int len = API_KEY_PART1.length;
        byte[] decoded = new byte[len];
        for (int i = 0; i < len; i++) {
            byte mixed = (byte) (API_KEY_PART1[i] ^ part2[i]);
            decoded[i] = (byte) (mixed ^ API_KEY_MASK ^ ((i * 31) & 0xFF));
        }
        byte[] shifted = new byte[len];
        for (int i = 0; i < len; i++) {
            shifted[(i + API_KEY_SHIFT) % len] = decoded[i];
        }
        API_KEY_CACHE = new String(shifted, StandardCharsets.UTF_8);
        return API_KEY_CACHE;
    }

    private static byte[] generatePart2(int length) {
        if (length <= 0) {
            return new byte[0];
        }
        byte[] part2 = new byte[length];
        int seed = API_KEY_PART2_SEED & 0xFF;
        for (int i = 0; i < length; i++) {
            seed = (seed * 1103515245 + 12345) & 0xFF;
            part2[i] = (byte) seed;
        }
        return part2;
    }

    private static JsonObject fetchFileData(String projectId, String fileId, String apiKey) throws IOException, InterruptedException {
        String endpoint;
        if (projectId != null && !projectId.isBlank()) {
            endpoint = "https://api.curseforge.com/v1/mods/" + projectId + "/files/" + fileId;
        } else {
            endpoint = "https://api.curseforge.com/v1/mods/files/" + fileId;
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("User-Agent", "DLC-Manager")
            .header("x-api-key", apiKey)
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs()))
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject("data");
    }

    private static String fetchDownloadUrl(String projectId, String fileId, String apiKey) throws IOException, InterruptedException {
        if (projectId == null || projectId.isBlank() || fileId == null || fileId.isBlank()) {
            return null;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + projectId + "/files/" + fileId + "/download-url";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("User-Agent", "DLC-Manager")
            .header("x-api-key", apiKey)
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs()))
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            return null;
        }
        JsonObject data = root.getAsJsonObject("data");
        return getString(data, "downloadUrl");
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    public static final class CurseForgeFile {
        public String fileName;
        public String url;
    }
}
