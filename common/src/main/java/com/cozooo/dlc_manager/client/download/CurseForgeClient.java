package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.google.gson.Gson;
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
import java.util.Map;
import java.util.Set;
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
    private static final Map<String, CurseForgeMod> MOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> PROJECT_ID_CACHE = new ConcurrentHashMap<>();
    private static final int MINECRAFT_GAME_ID = 432;

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
        file.id = fileId;
        applyFileData(file, data);
        if (file.url == null || file.url.isBlank()) {
            file.url = fetchDownloadUrl(projectId, fileId, apiKey);
        }
        CACHE.put(cacheKey, file);
        return file;
    }

    public static CurseForgeMod fetchMod(String modId) throws IOException, InterruptedException {
        String resolvedModId = resolveProjectId(modId);
        if (resolvedModId == null || resolvedModId.isBlank()) {
            return null;
        }
        CurseForgeMod cached = MOD_CACHE.get(resolvedModId);
        if (cached != null) {
            return cached;
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + resolvedModId;
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
        CurseForgeMod mod = new CurseForgeMod();
        applyModData(mod, root.getAsJsonObject("data"));
        MOD_CACHE.put(resolvedModId, mod);
        return mod;
    }

    public static CurseForgeFile fetchFileByIdOrVersion(String projectIdOrSlug, String fileIdOrVersion, String mcVersion, String loader)
        throws IOException, InterruptedException {
        if (fileIdOrVersion == null || fileIdOrVersion.isBlank()) {
            return null;
        }
        String trimmed = fileIdOrVersion.trim();
        if (isNumeric(trimmed)) {
            return fetchFile(resolveProjectId(projectIdOrSlug), trimmed);
        }
        String resolvedProjectId = resolveProjectId(projectIdOrSlug);
        if (resolvedProjectId == null || resolvedProjectId.isBlank()) {
            return null;
        }
        String cacheKey = resolvedProjectId
            + ":" + (mcVersion == null ? "" : mcVersion)
            + ":" + (loader == null ? "" : loader)
            + ":version:" + trimmed;
        CurseForgeFile cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        CurseForgeFile matched = fetchFileByVersion(resolvedProjectId, trimmed, mcVersion, loader);
        if (matched != null) {
            CACHE.put(cacheKey, matched);
        }
        return matched;
    }

    public static CurseForgeFile fetchLatestFile(String projectId, String mcVersion, String loader) throws IOException, InterruptedException {
        String resolvedProjectId = resolveProjectId(projectId);
        if (resolvedProjectId == null || resolvedProjectId.isBlank()) {
            return null;
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String cacheKey = resolvedProjectId + ":" + (mcVersion == null ? "" : mcVersion) + ":" + (loader == null ? "" : loader);
        CurseForgeFile cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + resolvedProjectId + "/files?pageSize=50";
        if (mcVersion != null && !mcVersion.isBlank()) {
            endpoint = endpoint + "&gameVersion=" + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8);
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
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
            return null;
        }
        CurseForgeFile latest = null;
        Instant latestTime = null;
        String targetLoader = loader == null ? "" : loader.trim();
        for (var element : root.getAsJsonArray("data")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject data = element.getAsJsonObject();
            if (!matchesGameVersion(data, mcVersion, targetLoader)) {
                continue;
            }
            CurseForgeFile file = new CurseForgeFile();
            file.id = getString(data, "id");
            applyFileData(file, data);
            Instant time = parseInstant(file.fileDate);
            if (latest == null || isAfter(time, latestTime)) {
                latest = file;
                latestTime = time;
            }
        }
        if (latest == null) {
            return null;
        }
        if (latest.url == null || latest.url.isBlank()) {
            latest.url = fetchDownloadUrl(resolvedProjectId, latest.id, apiKey);
        }
        CACHE.put(cacheKey, latest);
        return latest;
    }

    private static CurseForgeFile fetchFileByVersion(String projectId, String version, String mcVersion, String loader)
        throws IOException, InterruptedException {
        if (projectId == null || projectId.isBlank() || version == null || version.isBlank()) {
            return null;
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + projectId + "/files?pageSize=50";
        if (mcVersion != null && !mcVersion.isBlank()) {
            endpoint = endpoint + "&gameVersion=" + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8);
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
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
            return null;
        }
        CurseForgeFile matched = null;
        Instant matchedTime = null;
        String targetLoader = loader == null ? "" : loader.trim();
        String versionLower = version.trim().toLowerCase();
        for (var element : root.getAsJsonArray("data")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject data = element.getAsJsonObject();
            if (!matchesGameVersion(data, mcVersion, targetLoader)) {
                continue;
            }
            String fileName = getString(data, "fileName");
            String displayName = getString(data, "displayName");
            if (!containsVersion(fileName, displayName, versionLower)) {
                continue;
            }
            CurseForgeFile file = new CurseForgeFile();
            file.id = getString(data, "id");
            applyFileData(file, data);
            file.fileName = fileName;
            file.displayName = displayName;
            Instant time = parseInstant(file.fileDate);
            if (matched == null || isAfter(time, matchedTime)) {
                matched = file;
                matchedTime = time;
            }
        }
        if (matched == null) {
            return null;
        }
        if (matched.url == null || matched.url.isBlank()) {
            matched.url = fetchDownloadUrl(projectId, matched.id, apiKey);
        }
        return matched;
    }

    private static String resolveProjectId(String projectIdOrSlug) throws IOException, InterruptedException {
        if (projectIdOrSlug == null || projectIdOrSlug.isBlank()) {
            return null;
        }
        String trimmed = projectIdOrSlug.trim();
        if (isNumeric(trimmed)) {
            return trimmed;
        }
        String cached = PROJECT_ID_CACHE.get(trimmed.toLowerCase());
        if (cached != null) {
            return cached.isBlank() ? null : cached;
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/search?gameId=" + MINECRAFT_GAME_ID
            + "&slug=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("User-Agent", "DLC-Manager")
            .header("x-api-key", apiKey)
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs()))
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            PROJECT_ID_CACHE.put(trimmed.toLowerCase(), "");
            return null;
        }
        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
            PROJECT_ID_CACHE.put(trimmed.toLowerCase(), "");
            return null;
        }
        for (var element : root.getAsJsonArray("data")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject data = element.getAsJsonObject();
            String id = getString(data, "id");
            if (id != null && !id.isBlank()) {
                PROJECT_ID_CACHE.put(trimmed.toLowerCase(), id);
                return id;
            }
        }
        PROJECT_ID_CACHE.put(trimmed.toLowerCase(), "");
        return null;
    }

    private static boolean containsVersion(String fileName, String displayName, String versionLower) {
        if (versionLower == null || versionLower.isBlank()) {
            return false;
        }
        if (fileName != null && fileName.toLowerCase().contains(versionLower)) {
            return true;
        }
        return displayName != null && displayName.toLowerCase().contains(versionLower);
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
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

    private static boolean matchesGameVersion(JsonObject data, String mcVersion, String loader) {
        if (data == null || !data.has("gameVersions") || !data.get("gameVersions").isJsonArray()) {
            return false;
        }
        Set<String> versions = new java.util.HashSet<>();
        for (var entry : data.getAsJsonArray("gameVersions")) {
            if (entry.isJsonPrimitive()) {
                versions.add(entry.getAsString());
            }
        }
        if (mcVersion != null && !mcVersion.isBlank() && !versions.contains(mcVersion)) {
            return false;
        }
        if (loader != null && !loader.isBlank()) {
            for (String value : versions) {
                if (value.equalsIgnoreCase(loader)) {
                    return true;
                }
            }
            return false;
        }
        return true;
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

    private static void applyFileData(CurseForgeFile file, JsonObject data) {
        if (file == null || data == null) {
            return;
        }
        file.fileName = getString(data, "fileName");
        file.displayName = getString(data, "displayName");
        file.fileDate = getString(data, "fileDate");
        file.url = getString(data, "downloadUrl");
        file.classSlug = readClassSlug(data);
        if (data.has("fileLength") && data.get("fileLength").isJsonPrimitive()) {
            try {
                file.size = data.get("fileLength").getAsLong();
            } catch (Exception ignored) {
                file.size = -1L;
            }
        }
        if (data.has("hashes") && data.get("hashes").isJsonArray()) {
            for (var entry : data.getAsJsonArray("hashes")) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject hash = entry.getAsJsonObject();
                int algo = -1;
                if (hash.has("algo") && hash.get("algo").isJsonPrimitive()) {
                    try {
                        algo = hash.get("algo").getAsInt();
                    } catch (Exception ignored) {
                        algo = -1;
                    }
                }
                String value = getString(hash, "value");
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
    }

    public static final class CurseForgeFile {
        public String id;
        public String fileName;
        public String displayName;
        public String fileDate;
        public String url;
        public String classSlug;
        public String sha1;
        public String md5;
        public long size;
    }

    public static final class CurseForgeMod {
        public String id;
        public String classSlug;
        public String primaryCategorySlug;
        public String primaryCategoryName;
        public final java.util.List<String> categorySlugs = new java.util.ArrayList<>();
    }

    private static String readClassSlug(JsonObject data) {
        if (data == null) {
            return null;
        }
        String direct = getString(data, "classSlug");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        if (data.has("class") && data.get("class").isJsonObject()) {
            JsonObject clazz = data.getAsJsonObject("class");
            String slug = getString(clazz, "slug");
            if (slug != null && !slug.isBlank()) {
                return slug;
            }
            String name = getString(clazz, "name");
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (data.has("categories") && data.get("categories").isJsonArray()) {
            for (var element : data.getAsJsonArray("categories")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject category = element.getAsJsonObject();
                String slug = getString(category, "slug");
                if (slug != null && !slug.isBlank()) {
                    return slug;
                }
                String name = getString(category, "name");
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }
        return null;
    }

    private static void applyModData(CurseForgeMod mod, JsonObject data) {
        if (mod == null || data == null) {
            return;
        }
        mod.id = getString(data, "id");
        if (data.has("classId") && data.get("classId").isJsonPrimitive()) {
            try {
                mod.classSlug = getString(data, "classSlug");
            } catch (Exception ignored) {
                mod.classSlug = null;
            }
        }
        if (data.has("categories") && data.get("categories").isJsonArray()) {
            for (var element : data.getAsJsonArray("categories")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject category = element.getAsJsonObject();
                String slug = getString(category, "slug");
                String name = getString(category, "name");
                if (mod.primaryCategorySlug == null || mod.primaryCategorySlug.isBlank()) {
                    if (slug != null && !slug.isBlank()) {
                        mod.primaryCategorySlug = slug;
                    }
                }
                if (mod.primaryCategoryName == null || mod.primaryCategoryName.isBlank()) {
                    if (name != null && !name.isBlank()) {
                        mod.primaryCategoryName = name;
                    }
                }
                if (slug != null && !slug.isBlank()) {
                    mod.categorySlugs.add(slug);
                } else if (name != null && !name.isBlank()) {
                    mod.categorySlugs.add(name);
                }
            }
        }
        if (mod.classSlug == null || mod.classSlug.isBlank()) {
            mod.classSlug = mod.primaryCategorySlug;
        }
    }
}
