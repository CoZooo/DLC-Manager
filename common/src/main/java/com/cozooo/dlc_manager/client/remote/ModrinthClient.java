package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
            applyFile(version, fileObj);
            for (JsonElement element : files) {
                JsonObject file = element.getAsJsonObject();
                if (file.has("primary") && file.get("primary").getAsBoolean()) {
                    applyFile(version, file);
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

    public static ModrinthVersion fetchVersionByIdOrVersion(String projectIdOrSlug, String versionIdOrString, String mcVersion, String loader)
        throws IOException, InterruptedException {
        if (versionIdOrString == null || versionIdOrString.isBlank()) {
            return null;
        }
        String trimmed = versionIdOrString.trim();
        ModrinthVersion direct = fetchVersion(trimmed);
        if (direct != null) {
            return direct;
        }
        if (projectIdOrSlug == null || projectIdOrSlug.isBlank()) {
            return null;
        }
        String cacheKey = projectIdOrSlug + ":" + (mcVersion == null ? "" : mcVersion)
            + ":" + (loader == null ? "" : loader) + ":version:" + trimmed;
        ModrinthVersion cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ModrinthVersion matched = fetchVersionByProjectVersion(projectIdOrSlug, trimmed, mcVersion, loader);
        if (matched != null) {
            CACHE.put(cacheKey, matched);
        }
        return matched;
    }

    private static ModrinthVersion fetchVersionByProjectVersion(String projectIdOrSlug, String version, String mcVersion, String loader)
        throws IOException, InterruptedException {
        String endpoint = "https://api.modrinth.com/v2/project/" + projectIdOrSlug + "/version";
        String normalizedLoader = normalizeLoader(loader);
        List<String> params = new ArrayList<>();
        if (normalizedLoader != null && !normalizedLoader.isBlank()) {
            params.add("loaders=" + URLEncoder.encode("[\"" + normalizedLoader + "\"]", StandardCharsets.UTF_8));
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            params.add("game_versions=" + URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8));
        }
        if (!params.isEmpty()) {
            endpoint = endpoint + "?" + String.join("&", params);
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("User-Agent", "DLC-Manager")
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs()))
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonArray data = GSON.fromJson(response.body(), JsonArray.class);
        if (data == null) {
            return null;
        }
        String versionLower = version.trim().toLowerCase();
        ModrinthVersion matched = null;
        Instant matchedTime = null;
        for (JsonElement element : data) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String versionNumber = getString(obj, "version_number");
            if (versionNumber == null || !versionNumber.toLowerCase().contains(versionLower)) {
                continue;
            }
            if (!matchesGameVersion(obj, mcVersion, normalizedLoader)) {
                continue;
            }
            ModrinthVersion versionObj = new ModrinthVersion();
            JsonArray files = obj.getAsJsonArray("files");
            if (files != null && files.size() > 0) {
                JsonObject fileObj = files.get(0).getAsJsonObject();
                applyFile(versionObj, fileObj);
                for (JsonElement fileElement : files) {
                    JsonObject file = fileElement.getAsJsonObject();
                    if (file.has("primary") && file.get("primary").getAsBoolean()) {
                        applyFile(versionObj, file);
                        break;
                    }
                }
            }
            JsonArray deps = obj.getAsJsonArray("dependencies");
            if (deps != null) {
                for (JsonElement depElement : deps) {
                    if (!depElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject depObj = depElement.getAsJsonObject();
                    String depType = getString(depObj, "dependency_type");
                    if (!"required".equalsIgnoreCase(depType)) {
                        continue;
                    }
                    ModrinthDependency dep = new ModrinthDependency();
                    dep.versionId = getString(depObj, "version_id");
                    dep.projectId = getString(depObj, "project_id");
                    versionObj.requiredDependencies.add(dep);
                }
            }
            Instant time = parseInstant(getString(obj, "date_published"));
            if (matched == null || isAfter(time, matchedTime)) {
                matched = versionObj;
                matchedTime = time;
            }
        }
        return matched;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static boolean matchesGameVersion(JsonObject obj, String mcVersion, String loader) {
        if (obj == null) {
            return false;
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            JsonArray versions = obj.getAsJsonArray("game_versions");
            if (versions == null || !containsIgnoreCase(versions, mcVersion)) {
                return false;
            }
        }
        if (loader != null && !loader.isBlank()) {
            JsonArray loaders = obj.getAsJsonArray("loaders");
            if (loaders == null || !containsIgnoreCase(loaders, loader)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIgnoreCase(JsonArray array, String value) {
        if (array == null || value == null) {
            return false;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                if (value.equalsIgnoreCase(element.getAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeLoader(String loader) {
        if (loader == null || loader.isBlank()) {
            return null;
        }
        String trimmed = loader.trim().toLowerCase();
        return switch (trimmed) {
            case "forge" -> "forge";
            case "neoforge", "neo_forge", "neo-forge" -> "neoforge";
            case "fabric" -> "fabric";
            default -> trimmed;
        };
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isAfter(Instant candidate, Instant current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return candidate.isAfter(current);
    }

    private static void applyFile(ModrinthVersion target, JsonObject file) {
        if (target == null || file == null) {
            return;
        }
        target.fileName = getString(file, "filename");
        target.url = getString(file, "url");
        target.size = getLong(file, "size");
        if (file.has("hashes") && file.get("hashes").isJsonObject()) {
            JsonObject hashes = file.getAsJsonObject("hashes");
            target.sha1 = getString(hashes, "sha1");
            target.sha512 = getString(hashes, "sha512");
        }
    }

    public static final class ModrinthVersion {
        public String fileName;
        public String url;
        public String sha1;
        public String sha512;
        public long size;
        public final List<ModrinthDependency> requiredDependencies = new ArrayList<>();
    }

    public static final class ModrinthDependency {
        public String versionId;
        public String projectId;
    }

    private static long getLong(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsLong();
            } catch (Exception ignored) {
                return -1L;
            }
        }
        return -1L;
    }
}
