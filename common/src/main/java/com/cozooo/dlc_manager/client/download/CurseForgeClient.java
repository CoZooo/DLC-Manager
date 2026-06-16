package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.HttpURLConnection;
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
    private static final String[] CDN_HOSTS = new String[] {
        "https://mediafilez.forgecdn.net"
    };
    private static final int API_MAX_ATTEMPTS = 2;

    private CurseForgeClient() {
    }

    public static CurseForgeFile fetchFile(String projectId, String fileId) throws IOException, InterruptedException {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        String apiKey = getApiKey();
        String cacheKey = (projectId == null ? "" : projectId) + ":" + fileId;
        CurseForgeFile cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JsonObject data = fetchFileDataWithRetry(projectId, fileId, apiKey);
        if (data == null) {
            return null;
        }
        CurseForgeFile file = new CurseForgeFile();
        file.id = fileId;
        applyFileData(file, data);
        String fallbackUrl = null;
        boolean useCdnFallback = false;
        if (data.has("downloadUrl")) {
            String downloadUrl = getString(data, "downloadUrl");
            if (downloadUrl == null) {
                useCdnFallback = true;
                fallbackUrl = fallbackDownloadUrl(projectId, fileId);
                file.url = fallbackUrl;
            } else {
                file.url = downloadUrl;
            }
        } else if (file.url == null || file.url.isBlank()) {
            DownloadUrlResult downloadUrl = fetchDownloadUrlWithRetry(projectId, fileId, apiKey);
            useCdnFallback = downloadUrl.useCdnFallback;
            fallbackUrl = downloadUrl.url == null ? fallbackDownloadUrl(projectId, fileId) : null;
            file.url = downloadUrl.url == null ? fallbackUrl : downloadUrl.url;
        }
        resolveMissingFileName(file, fallbackUrl);
        resolveAlternateDownloadUrls(file, fallbackUrl, useCdnFallback);
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
        String endpoint = "https://api.curseforge.com/v1/mods/" + resolvedModId;
        JsonObject root = fetchJsonObjectWithRetry(endpoint, apiKey);
        if (root == null) {
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
        String cacheKey = resolvedProjectId + ":" + (mcVersion == null ? "" : mcVersion) + ":" + (loader == null ? "" : loader);
        CurseForgeFile cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + resolvedProjectId + "/files?pageSize=50";
        if (mcVersion != null && !mcVersion.isBlank()) {
            endpoint = endpoint + "&gameVersion=" + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8);
        }
        JsonObject root = fetchJsonArrayWithRetry(endpoint, apiKey);
        if (root == null) {
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
            file.downloadUrlExplicitlyNull = isExplicitNullDownloadUrl(data);
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
            DownloadUrlResult downloadUrl = fetchDownloadUrlWithRetry(resolvedProjectId, latest.id, apiKey);
            String fallbackUrl = downloadUrl.url == null ? fallbackDownloadUrl(resolvedProjectId, latest.id) : null;
            latest.url = downloadUrl.url == null ? fallbackUrl : downloadUrl.url;
            resolveMissingFileName(latest, fallbackUrl);
            resolveAlternateDownloadUrls(latest, fallbackUrl, latest.downloadUrlExplicitlyNull || downloadUrl.useCdnFallback);
        } else {
            resolveMissingFileName(latest, null);
            resolveAlternateDownloadUrls(latest, null, false);
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
        String endpoint = "https://api.curseforge.com/v1/mods/" + projectId + "/files?pageSize=50";
        if (mcVersion != null && !mcVersion.isBlank()) {
            endpoint = endpoint + "&gameVersion=" + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8);
        }
        JsonObject root = fetchJsonArrayWithRetry(endpoint, apiKey);
        if (root == null) {
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
            file.downloadUrlExplicitlyNull = isExplicitNullDownloadUrl(data);
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
            DownloadUrlResult downloadUrl = fetchDownloadUrlWithRetry(projectId, matched.id, apiKey);
            String fallbackUrl = downloadUrl.url == null ? fallbackDownloadUrl(projectId, matched.id) : null;
            matched.url = downloadUrl.url == null ? fallbackUrl : downloadUrl.url;
            resolveMissingFileName(matched, fallbackUrl);
            resolveAlternateDownloadUrls(matched, fallbackUrl, matched.downloadUrlExplicitlyNull || downloadUrl.useCdnFallback);
        } else {
            resolveMissingFileName(matched, null);
            resolveAlternateDownloadUrls(matched, null, false);
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
        String endpoint = "https://api.curseforge.com/v1/mods/search?gameId=" + MINECRAFT_GAME_ID
            + "&slug=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        JsonObject root = fetchJsonArrayWithRetry(endpoint, apiKey);
        if (root == null) {
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

    private static JsonObject fetchFileDataWithRetry(String projectId, String fileId, String apiKey) throws IOException, InterruptedException {
        String endpoint;
        if (projectId != null && !projectId.isBlank()) {
            endpoint = "https://api.curseforge.com/v1/mods/" + projectId + "/files/" + fileId;
        } else {
            endpoint = "https://api.curseforge.com/v1/mods/files/" + fileId;
        }
        JsonObject root = sendJsonWithRetry(endpoint, apiKey);
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject("data");
    }

    private static DownloadUrlResult fetchDownloadUrlWithRetry(String projectId, String fileId, String apiKey) throws IOException, InterruptedException {
        return fetchDownloadUrl(projectId, fileId, apiKey);
    }

    private static DownloadUrlResult fetchDownloadUrl(String projectId, String fileId, String apiKey) throws IOException, InterruptedException {
        if (projectId == null || projectId.isBlank() || fileId == null || fileId.isBlank()) {
            return new DownloadUrlResult(null, false);
        }
        String endpoint = "https://api.curseforge.com/v1/mods/" + projectId + "/files/" + fileId + "/download-url";
        boolean useCdnFallback = false;
        IOException lastError = null;
        try {
            ApiJsonResult official = sendJsonWithFixedRetryResult(endpoint, apiKey);
            if (official.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                useCdnFallback = true;
            }
            String officialUrl = extractDownloadUrl(official.root);
            if (officialUrl != null) {
                return new DownloadUrlResult(officialUrl, false);
            }
        } catch (IOException ex) {
            lastError = ex;
        }
        String mirrorEndpoint = mirrorApiEndpoint(endpoint);
        if (!mirrorEndpoint.equals(endpoint)) {
            try {
                ApiJsonResult mirror = sendJsonWithFixedRetryResult(mirrorEndpoint, null);
                if (mirror.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    useCdnFallback = true;
                }
                String mirrorUrl = extractDownloadUrl(mirror.root);
                if (mirrorUrl != null) {
                    return new DownloadUrlResult(mirrorUrl, false);
                }
            } catch (IOException ex) {
                if (useCdnFallback) {
                    return new DownloadUrlResult(null, true);
                }
                if (lastError != null) {
                    throw ex;
                }
            }
        } else if (lastError != null) {
            throw lastError;
        }
        return new DownloadUrlResult(null, useCdnFallback);
    }

    private static String extractDownloadUrl(JsonObject root) {
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            return null;
        }
        JsonObject data = root.getAsJsonObject("data");
        return data.has("downloadUrl") ? getString(data, "downloadUrl") : null;
    }

    private static JsonObject fetchJsonArrayWithRetry(String endpoint, String apiKey) throws IOException, InterruptedException {
        JsonObject root = sendJsonWithRetry(endpoint, apiKey);
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
            return null;
        }
        return root;
    }

    private static JsonObject fetchJsonObjectWithRetry(String endpoint, String apiKey) throws IOException, InterruptedException {
        JsonObject root = sendJsonWithRetry(endpoint, apiKey);
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            return null;
        }
        return root;
    }

    private static JsonObject sendJsonWithRetry(String endpoint, String apiKey) throws IOException, InterruptedException {
        IOException lastError = null;
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                JsonObject official = sendJsonWithFixedRetry(endpoint, apiKey);
                if (official != null) {
                    return official;
                }
            } catch (IOException ex) {
                lastError = ex;
            }
        }
        String mirrorEndpoint = mirrorApiEndpoint(endpoint);
        if (!mirrorEndpoint.equals(endpoint)) {
            try {
                return sendJsonWithFixedRetry(mirrorEndpoint, null);
            } catch (IOException ex) {
                lastError = ex;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    private static JsonObject sendJsonWithFixedRetry(String endpoint, String apiKey)
        throws IOException, InterruptedException {
        return sendJsonWithFixedRetryResult(endpoint, apiKey).root;
    }

    private static ApiJsonResult sendJsonWithFixedRetryResult(String endpoint, String apiKey)
        throws IOException, InterruptedException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= API_MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("User-Agent", "DLC-Manager")
                    .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs()))
                    .GET();
                if (apiKey != null && !apiKey.isBlank()) {
                    builder.header("x-api-key", apiKey);
                }
                HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (isRetryableStatus(response.statusCode()) && attempt < API_MAX_ATTEMPTS) {
                        continue;
                    }
                    return new ApiJsonResult(null, response.statusCode());
                }
                return new ApiJsonResult(GSON.fromJson(response.body(), JsonObject.class), response.statusCode());
            } catch (IOException ex) {
                lastError = ex;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return new ApiJsonResult(null, -1);
    }

    private static String mirrorApiEndpoint(String endpoint) {
        return endpoint.replace("https://api.curseforge.com", "https://mod.mcimirror.top/curseforge");
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private static String fallbackDownloadUrl(String projectId, String fileId) {
        if (projectId == null || projectId.isBlank() || fileId == null || fileId.isBlank()) {
            return null;
        }
        return "https://www.curseforge.com/api/v1/mods/" + projectId + "/files/" + fileId + "/download";
    }

    private static void resolveMissingFileName(CurseForgeFile file, String fallbackUrl) {
        if (file == null || file.fileName != null && !file.fileName.isBlank()) {
            return;
        }
        String fileName = DlcDownloadUtil.extractFileNameFromUrl(file.url);
        if ((fileName == null || fileName.isBlank()) && fallbackUrl != null && !fallbackUrl.isBlank()) {
            fileName = extractFileNameFromFallbackRedirect(fallbackUrl);
        }
        if (fileName != null && !fileName.isBlank()) {
            file.fileName = fileName;
        }
    }

    private static void resolveAlternateDownloadUrls(CurseForgeFile file, String fallbackUrl, boolean useCdnFallback) {
        if (file == null) {
            return;
        }
        file.alternateUrls.clear();
        String fileName = DlcDownloadUtil.sanitizeFileName(file.fileName);
        if (useCdnFallback && fallbackUrl != null && !fallbackUrl.isBlank() && file.id != null && !file.id.isBlank() && fileName != null && !fileName.isBlank()) {
            for (String host : CDN_HOSTS) {
                addAlternateUrl(file, buildForgeCdnUrl(host, file.id, fileName));
            }
        }
        addAlternateUrl(file, fallbackUrl);
    }

    private static void addAlternateUrl(CurseForgeFile file, String url) {
        if (url == null || url.isBlank() || url.equals(file.url) || file.alternateUrls.contains(url)) {
            return;
        }
        file.alternateUrls.add(url);
    }

    private static String buildForgeCdnUrl(String host, String fileId, String fileName) {
        if (fileId == null || fileId.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(fileId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (id < 0) {
            return null;
        }
        long part1 = id / 1000L;
        long part2 = id % 1000L;
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return host + "/files/" + part1 + "/" + part2 + "/" + encodedName;
    }

    private static String extractFileNameFromFallbackRedirect(String fallbackUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(fallbackUrl).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.connect();
            if (!isRedirect(connection.getResponseCode())) {
                return null;
            }
            String location = connection.getHeaderField("Location");
            String redirected = resolveRedirectUrl(fallbackUrl, location);
            return DlcDownloadUtil.extractFileNameFromUrl(redirected);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM
            || statusCode == HttpURLConnection.HTTP_MOVED_TEMP
            || statusCode == HttpURLConnection.HTTP_SEE_OTHER
            || statusCode == 307
            || statusCode == 308;
    }

    private static String resolveRedirectUrl(String baseUrl, String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            return URI.create(baseUrl).resolve(location).toString();
        } catch (IllegalArgumentException ignored) {
            return location;
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static boolean isExplicitNullDownloadUrl(JsonObject data) {
        return data != null && data.has("downloadUrl") && data.get("downloadUrl").isJsonNull();
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
        public final List<String> alternateUrls = new ArrayList<>();
        public boolean downloadUrlExplicitlyNull;
        public long size;
    }

    public static final class CurseForgeMod {
        public String id;
        public String classSlug;
        public String primaryCategorySlug;
        public String primaryCategoryName;
        public final java.util.List<String> categorySlugs = new java.util.ArrayList<>();
    }

    private record DownloadUrlResult(String url, boolean useCdnFallback) {
    }

    private record ApiJsonResult(JsonObject root, int statusCode) {
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
