package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcPackReleaseUpdateService {
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("github\\.com[:/]+([^/]+)/([^/#?]+)", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_VERSION = "1.0.0";
    private static final String UNIVERSAL_PREFIX = "Universal_";

    private DlcPackReleaseUpdateService() {
    }

    public enum Side {
        CLIENT("Client_"),
        SERVER("Server_");

        private final String assetPrefix;

        Side(String assetPrefix) {
            this.assetPrefix = assetPrefix;
        }

        public String assetPrefix() {
            return assetPrefix;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int stepIndex, int stepCount, String fileName, long downloaded, long total, float overallProgress);
    }

    @FunctionalInterface
    public interface CancelChecker {
        boolean isCanceled();
    }

    public static final class CheckResult {
        public String modpackUrl;
        public String repoUrl;
        public String currentBase;
        public String currentDlc;
        public String targetBase;
        public String targetDlc;
        public List<ReleaseStep> steps = List.of();
        public String error;

        public boolean hasUpdate() {
            return steps != null && !steps.isEmpty();
        }

        public boolean canDownload() {
            if (!hasUpdate()) {
                return false;
            }
            for (ReleaseStep step : steps) {
                if (step == null || step.assetUrl == null || step.assetUrl.isBlank()) {
                    return false;
                }
            }
            return true;
        }

        public String resolveCombinedChangelog(String lang) {
            if (steps == null || steps.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (ReleaseStep step : steps) {
                if (step == null) {
                    continue;
                }
                String line = step.resolveChangelog(lang);
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder
                    .append(safeVersion(step.oldBase))
                    .append("+")
                    .append(safeVersion(step.oldDlc))
                    .append(" -> ")
                    .append(safeVersion(step.newBase))
                    .append("+")
                    .append(safeVersion(step.newDlc))
                    .append("\n")
                    .append(line.trim());
            }
            return builder.toString();
        }
    }

    public static final class ReleaseStep {
        public String tag;
        public String oldBase;
        public String oldDlc;
        public String newBase;
        public String newDlc;
        public String assetName;
        public String assetUrl;
        public String rawChangelog = "";
        public Map<String, String> changelog = Map.of();

        public String resolveChangelog(String lang) {
            if (rawChangelog != null && !rawChangelog.isBlank()) {
                return rawChangelog;
            }
            if (changelog == null || changelog.isEmpty()) {
                return "";
            }
            String key = normalizeLangKey(lang);
            if (changelog.containsKey(key)) {
                return changelog.get(key);
            }
            if (changelog.containsKey("en_us")) {
                return changelog.get("en_us");
            }
            return changelog.values().stream().findFirst().orElse("");
        }
    }

    public static final class DownloadResult {
        public boolean success;
        public boolean canceled;
        public String message;
        public List<Path> files = List.of();

        public static DownloadResult success(List<Path> files) {
            DownloadResult result = new DownloadResult();
            result.success = true;
            result.files = files == null ? List.of() : files;
            return result;
        }

        public static DownloadResult canceled() {
            DownloadResult result = new DownloadResult();
            result.canceled = true;
            result.message = "canceled";
            return result;
        }

        public static DownloadResult failed(String message) {
            DownloadResult result = new DownloadResult();
            result.message = message == null ? "failed" : message;
            return result;
        }
    }

    public static CheckResult checkForUpdates(String modpackUrl, String githubRepoUrl, String currentBase, String currentDlc, Side side) {
        CheckResult result = new CheckResult();
        result.modpackUrl = normalizeString(modpackUrl);
        result.repoUrl = normalizeString(githubRepoUrl);
        result.currentBase = safeVersion(currentBase);
        result.currentDlc = safeVersion(currentDlc);
        result.targetBase = result.currentBase;
        result.targetDlc = result.currentDlc;
        if (side == null || result.repoUrl.isBlank()) {
            return result;
        }
        RepoCoordinates repo = parseRepoCoordinates(result.repoUrl);
        if (repo == null) {
            result.error = "invalid_repo_url";
            return result;
        }
        JsonArray releases;
        try {
            releases = fetchReleases(repo);
        } catch (Exception ex) {
            result.error = ex.getMessage() == null ? "release_fetch_failed" : ex.getMessage();
            return result;
        }
        if (releases == null || releases.isEmpty()) {
            return result;
        }
        Map<String, ReleaseStep> nextByState = new HashMap<>();
        for (JsonElement element : releases) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            if (release.has("draft") && release.get("draft").isJsonPrimitive() && release.get("draft").getAsBoolean()) {
                continue;
            }
            String tag = getString(release, "tag_name");
            if (tag.isBlank()) {
                tag = getString(release, "name");
            }
            ParsedTag parsed = parseTag(tag);
            if (parsed == null) {
                continue;
            }
            ReleaseStep candidate = new ReleaseStep();
            candidate.tag = tag;
            candidate.oldBase = parsed.oldBase;
            candidate.oldDlc = parsed.oldDlc;
            candidate.newBase = parsed.newBase;
            candidate.newDlc = parsed.newDlc;
            AssetChoice asset = resolvePreferredAsset(release, side, parsed.newBase, parsed.newDlc);
            candidate.assetName = asset.name;
            candidate.assetUrl = asset.url;
            candidate.rawChangelog = resolveReleaseChangelog(release);
            String key = stateKey(candidate.oldBase, candidate.oldDlc);
            ReleaseStep current = nextByState.get(key);
            if (shouldReplace(current, candidate)) {
                nextByState.put(key, candidate);
            }
        }

        String currentKey = stateKey(result.currentBase, result.currentDlc);
        List<ReleaseStep> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(currentKey);
        while (true) {
            ReleaseStep next = nextByState.get(currentKey);
            if (next == null) {
                break;
            }
            String nextKey = stateKey(next.newBase, next.newDlc);
            if (nextKey.equals(currentKey) || visited.contains(nextKey)) {
                break;
            }
            chain.add(next);
            visited.add(nextKey);
            currentKey = nextKey;
        }
        result.steps = chain;
        if (!chain.isEmpty()) {
            ReleaseStep tail = chain.get(chain.size() - 1);
            result.targetBase = safeVersion(tail.newBase);
            result.targetDlc = safeVersion(tail.newDlc);
        }
        return result;
    }

    public static void skipPromptForUpdate(CheckResult result, Side side) {
        PromptSpan span = resolvePromptSpan(result, side);
        if (span == null) {
            return;
        }
        try {
            JsonObject root = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE);
            if (root == null) {
                root = new JsonObject();
            }
            JsonObject section = new JsonObject();
            section.addProperty("repoUrl", span.repoUrl);
            section.addProperty("fromBase", span.fromBase);
            section.addProperty("fromDlc", span.fromDlc);
            section.addProperty("toBase", span.toBase);
            section.addProperty("toDlc", span.toDlc);
            section.addProperty("skippedAt", Instant.now().toString());
            root.add(span.sideKey, section);
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE, root);
        } catch (Exception ignored) {
        }
    }

    public static boolean isPromptSkippedForUpdate(CheckResult result, Side side) {
        PromptSpan span = resolvePromptSpan(result, side);
        if (span == null) {
            return false;
        }
        JsonObject root = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE);
        if (root == null || !root.has(span.sideKey) || !root.get(span.sideKey).isJsonObject()) {
            return false;
        }
        JsonObject section = root.getAsJsonObject(span.sideKey);
        String repoUrl = normalizeString(getString(section, "repoUrl"));
        String fromBase = safeVersion(getString(section, "fromBase"));
        String fromDlc = safeVersion(getString(section, "fromDlc"));
        String toBase = safeVersion(getString(section, "toBase"));
        String toDlc = safeVersion(getString(section, "toDlc"));
        return span.repoUrl.equals(repoUrl)
            && span.fromBase.equals(fromBase)
            && span.fromDlc.equals(fromDlc)
            && span.toBase.equals(toBase)
            && span.toDlc.equals(toDlc);
    }

    public static DownloadResult downloadClientPackages(CheckResult result, ProgressListener progress, CancelChecker cancelChecker) {
        if (result == null || !result.hasUpdate()) {
            return DownloadResult.failed("no_update");
        }
        if (!result.canDownload()) {
            return DownloadResult.failed("missing_release_asset");
        }
        Path updateDir = DlcUpdateService.getUpdateDir();
        if (updateDir == null) {
            return DownloadResult.failed("missing_dlc_root");
        }
        List<Path> downloaded = new ArrayList<>();
        int totalSteps = result.steps.size();
        for (int i = 0; i < totalSteps; i++) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                return DownloadResult.canceled();
            }
            ReleaseStep step = result.steps.get(i);
            final int stepIndex = i;
            try {
                Path path = DlcDownloadService.download(step.assetUrl, updateDir, step.assetName,
                    (done, total) -> notifyProgress(progress, stepIndex, totalSteps, step.assetName, done, total),
                    () -> cancelChecker != null && cancelChecker.isCanceled());
                downloaded.add(path);
                if (!DlcUpdateService.importUpdatePackage(path)) {
                    return DownloadResult.failed("import_failed");
                }
                notifyProgress(progress, stepIndex, totalSteps, step.assetName, 1L, 1L);
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                return DownloadResult.canceled();
            } catch (Exception ex) {
                return DownloadResult.failed(ex.getMessage() == null ? "download_failed" : ex.getMessage());
            }
        }
        clearSkipPromptMarker(Side.CLIENT);
        return DownloadResult.success(downloaded);
    }

    public static boolean hasDownloadedClientPackages(CheckResult result) {
        if (result == null || !result.hasUpdate() || result.steps == null || result.steps.isEmpty()) {
            return false;
        }
        Path updateDir = DlcUpdateService.getUpdateDir();
        if (updateDir == null) {
            return false;
        }
        for (ReleaseStep step : result.steps) {
            if (step == null) {
                return false;
            }
            String assetName = normalizeString(step.assetName);
            if (assetName.isBlank()) {
                return false;
            }
            Path target = updateDir.resolve(assetName);
            if (!Files.isRegularFile(target)) {
                return false;
            }
        }
        return true;
    }

    public static DownloadResult downloadServerPackages(CheckResult result, DlcServerEntry entry, int timeoutMs, ProgressListener progress, CancelChecker cancelChecker) {
        if (result == null || !result.hasUpdate()) {
            return DownloadResult.failed("no_update");
        }
        if (!result.canDownload()) {
            return DownloadResult.failed("missing_release_asset");
        }
        if (entry == null) {
            return DownloadResult.failed("missing_server_entry");
        }
        int totalSteps = result.steps.size();
        for (int i = 0; i < totalSteps; i++) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                return DownloadResult.canceled();
            }
            ReleaseStep step = result.steps.get(i);
            final int stepIndex = i;
            try {
                notifyProgress(progress, stepIndex, totalSteps, step.assetName, 0L, 1L);
                boolean imported = DlcServerConnection.importUpdatePackageFromUrl(entry, timeoutMs, step.assetUrl, step.assetName);
                if (!imported) {
                    return DownloadResult.failed("server_import_failed");
                }
                notifyProgress(progress, stepIndex, totalSteps, step.assetName, 1L, 1L);
            } catch (Exception ex) {
                return DownloadResult.failed(ex.getMessage() == null ? "download_failed" : ex.getMessage());
            }
        }
        clearSkipPromptMarker(Side.SERVER);
        return DownloadResult.success(List.of());
    }

    private static void notifyProgress(ProgressListener progress, int stepIndex, int stepCount, String fileName, long downloaded, long total) {
        if (progress == null || stepCount <= 0) {
            return;
        }
        float stepProgress;
        if (total > 0) {
            stepProgress = downloaded / (float) total;
        } else {
            stepProgress = 0f;
        }
        if (stepProgress < 0f) {
            stepProgress = 0f;
        }
        if (stepProgress > 1f) {
            stepProgress = 1f;
        }
        float overall = (stepIndex + stepProgress) / (float) stepCount;
        if (overall < 0f) {
            overall = 0f;
        }
        if (overall > 1f) {
            overall = 1f;
        }
        progress.onProgress(stepIndex, stepCount, fileName, downloaded, total, overall);
    }

    private static boolean shouldReplace(ReleaseStep current, ReleaseStep candidate) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        int compareBase = DlcUpdateService.compareVersions(safeVersion(candidate.newBase), safeVersion(current.newBase));
        if (compareBase != 0) {
            return compareBase > 0;
        }
        int compareDlc = DlcUpdateService.compareVersions(safeVersion(candidate.newDlc), safeVersion(current.newDlc));
        if (compareDlc != 0) {
            return compareDlc > 0;
        }
        boolean currentHasAsset = current.assetUrl != null && !current.assetUrl.isBlank();
        boolean candidateHasAsset = candidate.assetUrl != null && !candidate.assetUrl.isBlank();
        if (candidateHasAsset && !currentHasAsset) {
            return true;
        }
        if (!candidateHasAsset || !currentHasAsset) {
            return false;
        }
        return assetPriority(candidate.assetName) > assetPriority(current.assetName);
    }

    private static AssetChoice resolvePreferredAsset(JsonObject release, Side side, String base, String dlc) {
        String suffix = safeVersion(base) + "+" + safeVersion(dlc) + ".zip";
        String universalName = UNIVERSAL_PREFIX + suffix;
        String sideName = (side == null ? "" : side.assetPrefix()) + suffix;
        String fallbackName = sideName.isBlank() ? universalName : sideName;
        String universalUrl = findAssetUrl(release, universalName);
        if (!universalUrl.isBlank()) {
            return new AssetChoice(universalName, universalUrl);
        }
        String sideUrl = findAssetUrl(release, sideName);
        if (!sideUrl.isBlank()) {
            return new AssetChoice(sideName, sideUrl);
        }
        return new AssetChoice(fallbackName, "");
    }

    private static int assetPriority(String assetName) {
        if (assetName == null || assetName.isBlank()) {
            return 0;
        }
        if (assetName.regionMatches(true, 0, UNIVERSAL_PREFIX, 0, UNIVERSAL_PREFIX.length())) {
            return 2;
        }
        return 1;
    }

    private static String findAssetUrl(JsonObject release, String expectedName) {
        if (release == null || expectedName == null || expectedName.isBlank()) {
            return "";
        }
        if (!release.has("assets") || !release.get("assets").isJsonArray()) {
            return "";
        }
        JsonArray assets = release.getAsJsonArray("assets");
        for (JsonElement element : assets) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            String name = getString(asset, "name");
            if (!name.equalsIgnoreCase(expectedName)) {
                continue;
            }
            String download = getString(asset, "browser_download_url");
            if (!download.isBlank()) {
                return download;
            }
        }
        return "";
    }

    private static ParsedTag parseTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String[] parts = tag.trim().split("/", 2);
        if (parts.length != 2) {
            return null;
        }
        VersionPair oldPair = parseVersionPair(parts[0]);
        VersionPair newPair = parseVersionPair(parts[1]);
        if (oldPair == null || newPair == null) {
            return null;
        }
        ParsedTag parsed = new ParsedTag();
        parsed.oldBase = oldPair.base;
        parsed.oldDlc = oldPair.dlc;
        parsed.newBase = newPair.base;
        parsed.newDlc = newPair.dlc;
        return parsed;
    }

    private static VersionPair parseVersionPair(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String[] parts = trimmed.split("\\+", 2);
        if (parts.length != 2) {
            return null;
        }
        String base = safeVersion(parts[0]);
        String dlc = safeVersion(parts[1]);
        if (base.isBlank() || dlc.isBlank()) {
            return null;
        }
        VersionPair pair = new VersionPair();
        pair.base = base;
        pair.dlc = dlc;
        return pair;
    }

    private static JsonArray fetchReleases(RepoCoordinates repo) throws IOException {
        String api = "https://api.github.com/repos/" + repo.owner + "/" + repo.repo + "/releases?per_page=100";
        HttpURLConnection connection = (HttpURLConnection) new URL(api).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "DLC-Manager");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.connect();
        try {
            int code = connection.getResponseCode();
            if (code >= 400) {
                throw new IOException("http " + code);
            }
            try (InputStream in = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonArray()) {
                    return new JsonArray();
                }
                return element.getAsJsonArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static RepoCoordinates parseRepoCoordinates(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim();
        Matcher matcher = GITHUB_REPO_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        String owner = normalizeRepoPart(matcher.group(1));
        String repo = normalizeRepoPart(matcher.group(2));
        if (owner.isBlank() || repo.isBlank()) {
            return null;
        }
        RepoCoordinates coordinates = new RepoCoordinates();
        coordinates.owner = owner;
        coordinates.repo = repo;
        return coordinates;
    }

    private static String normalizeRepoPart(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static PromptSpan resolvePromptSpan(CheckResult result, Side side) {
        if (result == null || !result.hasUpdate()) {
            return null;
        }
        PromptSpan span = new PromptSpan();
        span.sideKey = normalizeSideKey(side);
        span.repoUrl = normalizeString(result.repoUrl);
        span.fromBase = safeVersion(result.currentBase);
        span.fromDlc = safeVersion(result.currentDlc);
        span.toBase = safeVersion(result.targetBase);
        span.toDlc = safeVersion(result.targetDlc);
        return span;
    }

    private static String normalizeSideKey(Side side) {
        return side == Side.SERVER ? "server" : "client";
    }

    private static void clearSkipPromptMarker(Side side) {
        try {
            JsonObject root = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE);
            if (root == null) {
                return;
            }
            root.remove(normalizeSideKey(side));
            if (root.entrySet().isEmpty()) {
                DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE);
                return;
            }
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE, root);
        } catch (Exception ignored) {
        }
    }

    private static String safeVersion(String value) {
        if (value == null) {
            return DEFAULT_VERSION;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? DEFAULT_VERSION : normalized;
    }

    private static String stateKey(String base, String dlc) {
        return safeVersion(base) + "|" + safeVersion(dlc);
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String normalizeLangKey(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en_us";
        }
        return lang.trim().toLowerCase().replace('-', '_');
    }

    private static String resolveReleaseChangelog(JsonObject release) {
        if (release == null) {
            return "";
        }
        String body = getString(release, "body");
        if (body.isBlank()) {
            return "";
        }
        return body;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || key.isBlank()) {
            return "";
        }
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class RepoCoordinates {
        private String owner;
        private String repo;
    }

    private static final class ParsedTag {
        private String oldBase;
        private String oldDlc;
        private String newBase;
        private String newDlc;
    }

    private static final class VersionPair {
        private String base;
        private String dlc;
    }

    private static final class AssetChoice {
        private final String name;
        private final String url;

        private AssetChoice(String name, String url) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }
    }

    private static final class PromptSpan {
        private String sideKey;
        private String repoUrl;
        private String fromBase;
        private String fromDlc;
        private String toBase;
        private String toDlc;
    }
}
