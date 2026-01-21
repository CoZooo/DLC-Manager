package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModrinthClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(DlcPathConfig.getConnectTimeoutMs()))
        .build();
    private static final Map<String, ModrinthVersion> CACHE = new ConcurrentHashMap<>();

    private ModrinthClient() {
    }

    public static ModrinthVersion fetchVersion(String versionId) throws IOException, InterruptedException {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        ModrinthVersion cached = CACHE.get(versionId);
        if (cached != null) {
            return cached;
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.modrinth.com/v2/version/" + versionId))
            .header("User-Agent", "DLC-Manager")
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs()))
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonObject obj = GSON.fromJson(response.body(), JsonObject.class);
        if (obj == null) {
            return null;
        }
        ModrinthVersion version = new ModrinthVersion();
        JsonArray files = obj.getAsJsonArray("files");
        if (files != null && files.size() > 0) {
            JsonObject fileObj = files.get(0).getAsJsonObject();
            version.fileName = getString(fileObj, "filename");
            version.url = getString(fileObj, "url");
            for (JsonElement element : files) {
                JsonObject file = element.getAsJsonObject();
                if (file.has("primary") && file.get("primary").getAsBoolean()) {
                    version.fileName = getString(file, "filename");
                    version.url = getString(file, "url");
                    break;
                }
            }
        }
        JsonArray deps = obj.getAsJsonArray("dependencies");
        if (deps != null) {
            for (JsonElement element : deps) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject depObj = element.getAsJsonObject();
                String depType = getString(depObj, "dependency_type");
                if (!"required".equalsIgnoreCase(depType)) {
                    continue;
                }
                ModrinthDependency dep = new ModrinthDependency();
                dep.versionId = getString(depObj, "version_id");
                dep.projectId = getString(depObj, "project_id");
                version.requiredDependencies.add(dep);
            }
        }
        CACHE.put(versionId, version);
        return version;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    public static final class ModrinthVersion {
        public String fileName;
        public String url;
        public final List<ModrinthDependency> requiredDependencies = new ArrayList<>();
    }

    public static final class ModrinthDependency {
        public String versionId;
        public String projectId;
    }
}
