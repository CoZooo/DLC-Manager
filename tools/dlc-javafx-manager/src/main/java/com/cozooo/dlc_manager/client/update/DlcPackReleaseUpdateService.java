package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcPackReleaseUpdateService {
    private static final Pattern SSH_REPO_PATTERN = Pattern.compile("^git@([^:]+):(.+?)(?:\\.git)?/?$", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_VERSION = "1.0.0";
    private static final String UNIVERSAL_PREFIX = "Universal_";
    private static final String[] VERSION_PAIR_DELIMITERS = new String[] {"_", "+", "@", "~", " "};
    private static final int RELEASES_PER_PAGE = 100;
    private static final int RELEASES_MAX_PAGES = 10;
    private static final int GITCODE_TOKEN_SHIFT = 5;
    private static final byte GITCODE_TOKEN_MASK = (byte) 0x33;
    private static final byte[] GITCODE_TOKEN_PART1 = new byte[] {
        (byte) 0x60, (byte) 0x80, (byte) 0x30, (byte) 0x33, (byte) 0xE9, (byte) 0x58, (byte) 0x6A, (byte) 0xEB,
        (byte) 0x34, (byte) 0x7D, (byte) 0x53, (byte) 0x17, (byte) 0xD0, (byte) 0x69, (byte) 0xC6, (byte) 0x88,
        (byte) 0xE6, (byte) 0xC5, (byte) 0xA7, (byte) 0x16, (byte) 0x34, (byte) 0xF9, (byte) 0x9F, (byte) 0x47
    };
    private static final int GITCODE_TOKEN_PART2_SEED = 0x6D;
    private static String GITCODE_TOKEN_CACHE;

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
        public String downloadBase;
        public String downloadDlc;
        public List<ReleaseStep> fullSteps = List.of();
        public List<ReleaseStep> steps = List.of();
        public String error;

        public boolean hasUpdate() {
            return compareState(currentBase, currentDlc, targetBase, targetDlc) < 0;
        }

        public boolean canDownload() {
            if (compareState(currentBase, currentDlc, downloadBase, downloadDlc) >= 0) {
                return false;
            }
            for (ReleaseStep step : steps) {
                if (step == null || step.assetUrl == null || step.assetUrl.isBlank()) {
                    return false;
                }
            }
            return true;
        }

        public boolean hasPartialDownload() {
            return hasUpdate()
                && compareState(currentBase, currentDlc, downloadBase, downloadDlc) < 0
                && compareState(downloadBase, downloadDlc, targetBase, targetDlc) < 0;
        }

        public boolean hasLatestGap() {
            return hasUpdate() && compareState(downloadBase, downloadDlc, targetBase, targetDlc) < 0;
        }

        public boolean isLatestFullyDownloadable() {
            return hasUpdate() && compareState(downloadBase, downloadDlc, targetBase, targetDlc) == 0;
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
                    .append(formatCompactTransition(step.oldBase, step.oldDlc, step.newBase, step.newDlc))
                    .append("\n")
                    .append(line.trim());
            }
            return builder.toString();
        }
    }

    private static final class RepoQueryResult {
        private final String error;
        private final boolean validRepo;
        private final Map<String, List<ReleaseStep>> fullTransitions;
        private final Map<String, List<ReleaseStep>> downloadableTransitions;

        private RepoQueryResult(String error, boolean validRepo,
                                Map<String, List<ReleaseStep>> fullTransitions,
                                Map<String, List<ReleaseStep>> downloadableTransitions) {
            this.error = error;
            this.validRepo = validRepo;
            this.fullTransitions = fullTransitions == null ? Map.of() : fullTransitions;
            this.downloadableTransitions = downloadableTransitions == null ? Map.of() : downloadableTransitions;
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
        public int repoIndex = Integer.MAX_VALUE;
        public String repoUrl = "";
        public List<AssetCandidate> assetCandidates = List.of();
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

    private static final class AssetCandidate {
        private final int repoIndex;
        private final String repoUrl;
        private final String assetName;
        private final String assetUrl;

        private AssetCandidate(int repoIndex, String repoUrl, String assetName, String assetUrl) {
            this.repoIndex = repoIndex;
            this.repoUrl = normalizeString(repoUrl);
            this.assetName = normalizeString(assetName);
            this.assetUrl = normalizeString(assetUrl);
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

    public static String toCheckFailureMessage(String error) {
        if (isTimeoutFailureMessage(error)) {
            return "dlc_manager.pack_update.check_failed (dlc_manager.pack_update.check_timeout)";
        }
        String safeError = error == null || error.isBlank() ? "failed" : error;
        return "dlc_manager.pack_update.check_failed: " + safeError;
    }

    public static String toDownloadFailureMessage(String error) {
        if (isTimeoutFailureMessage(error)) {
            return "dlc_manager.pack_update.download_failed (dlc_manager.pack_update.download_timeout)";
        }
        String safeError = error == null || error.isBlank() ? "failed" : error;
        return "dlc_manager.pack_update.download_failed: " + safeError;
    }

    public static CheckResult checkForUpdates(String modpackUrl, String githubRepoUrl, String currentBase, String currentDlc, Side side) {
        CheckResult result = new CheckResult();
        result.modpackUrl = normalizeString(modpackUrl);
        result.repoUrl = normalizeString(githubRepoUrl);
        result.currentBase = safeVersion(currentBase);
        result.currentDlc = safeVersion(currentDlc);
        result.targetBase = result.currentBase;
        result.targetDlc = result.currentDlc;
        result.downloadBase = result.currentBase;
        result.downloadDlc = result.currentDlc;
        if (side == null || result.repoUrl.isBlank()) {
            return result;
        }
        List<String> repoUrls = splitRepoUrls(result.repoUrl);
        if (repoUrls.isEmpty()) {
            return result;
        }
        List<CompletableFuture<RepoQueryResult>> futures = new ArrayList<>();
        for (int i = 0; i < repoUrls.size(); i++) {
            String repoUrl = repoUrls.get(i);
            int repoIndex = i;
            futures.add(CompletableFuture.supplyAsync(() -> querySingleRepo(result, repoUrl, repoIndex, side)));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        String lastError = null;
        boolean hasValidRepo = false;
        Map<String, List<ReleaseStep>> fullTransitions = new HashMap<>();
        Map<String, List<ReleaseStep>> downloadableTransitions = new HashMap<>();
        for (CompletableFuture<RepoQueryResult> future : futures) {
            RepoQueryResult single = future.join();
            if (single == null) {
                continue;
            }
            if (single.error != null && !single.error.isBlank()) {
                lastError = single.error;
                continue;
            }
            if (!single.validRepo) {
                continue;
            }
            hasValidRepo = true;
            mergeTransitions(fullTransitions, single.fullTransitions, false);
            mergeTransitions(downloadableTransitions, single.downloadableTransitions, true);
        }
        if (hasValidRepo) {
            List<ReleaseStep> fullChain = buildUpdateChain(result.currentBase, result.currentDlc, fullTransitions);
            List<ReleaseStep> downloadableChain = buildUpdateChain(result.currentBase, result.currentDlc, downloadableTransitions);
            result.fullSteps = fullChain;
            result.steps = downloadableChain;
            if (!fullChain.isEmpty()) {
                ReleaseStep tail = fullChain.get(fullChain.size() - 1);
                result.targetBase = safeVersion(tail.newBase);
                result.targetDlc = safeVersion(tail.newDlc);
            }
            if (!downloadableChain.isEmpty()) {
                ReleaseStep tail = downloadableChain.get(downloadableChain.size() - 1);
                result.downloadBase = safeVersion(tail.newBase);
                result.downloadDlc = safeVersion(tail.newDlc);
            }
            return result;
        }
        if (!hasValidRepo) {
            result.error = lastError == null || lastError.isBlank() ? "invalid_repo_url" : lastError;
        } else if (lastError != null && !lastError.isBlank()) {
            result.error = lastError;
        }
        return result;
    }

    private static RepoQueryResult querySingleRepo(CheckResult base, String repoUrl, int repoIndex, Side side) {
        if (base == null || repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        String normalizedRepoUrl = normalizeString(repoUrl);
        RepoCoordinates repo = parseRepoCoordinates(repoUrl);
        if (repo == null) {
            return new RepoQueryResult("invalid_repo_url", false, Map.of(), Map.of());
        }
        JsonArray releases;
        try {
            releases = fetchReleases(repo, safeVersion(base.currentBase), safeVersion(base.currentDlc));
        } catch (Exception ex) {
            return new RepoQueryResult(
                ex.getMessage() == null ? "release_fetch_failed" : ex.getMessage(),
                true,
                Map.of(),
                Map.of()
            );
        }
        if (releases == null || releases.isEmpty()) {
            return new RepoQueryResult(null, true, Map.of(), Map.of());
        }
        Map<String, List<ReleaseStep>> fullTransitions = new HashMap<>();
        Map<String, List<ReleaseStep>> downloadableTransitions = new HashMap<>();
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
            candidate.repoIndex = repoIndex;
            candidate.repoUrl = normalizedRepoUrl;
            AssetChoice asset = resolvePreferredAsset(release, side, parsed.newBase, parsed.newDlc);
            candidate.assetName = asset.name;
            candidate.assetUrl = asset.url;
            if (candidate.assetUrl != null && !candidate.assetUrl.isBlank()) {
                candidate.assetCandidates = new ArrayList<>(List.of(
                    new AssetCandidate(repoIndex, normalizedRepoUrl, candidate.assetName, candidate.assetUrl)
                ));
            } else {
                candidate.assetCandidates = new ArrayList<>();
            }
            candidate.rawChangelog = resolveReleaseChangelog(release);
            addTransition(fullTransitions, candidate, false);
            addTransition(downloadableTransitions, candidate, true);
        }
        return new RepoQueryResult(null, true, fullTransitions, downloadableTransitions);
    }

    private static void mergeTransitions(Map<String, List<ReleaseStep>> merged, Map<String, List<ReleaseStep>> incoming, boolean requireDownloadable) {
        if (merged == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<ReleaseStep>> entry : incoming.entrySet()) {
            List<ReleaseStep> candidates = entry.getValue();
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            for (ReleaseStep candidate : candidates) {
                addTransition(merged, candidate, requireDownloadable);
            }
        }
    }

    private static void addTransition(Map<String, List<ReleaseStep>> transitions, ReleaseStep candidate, boolean requireDownloadable) {
        if (transitions == null || candidate == null) {
            return;
        }
        if (requireDownloadable && (candidate.assetUrl == null || candidate.assetUrl.isBlank())) {
            return;
        }
        String key = stateKey(candidate.oldBase, candidate.oldDlc);
        List<ReleaseStep> candidates = transitions.computeIfAbsent(key, ignored -> new ArrayList<>());
        for (ReleaseStep existing : candidates) {
            if (sameTransition(existing, candidate)) {
                mergeAssetCandidates(existing, candidate);
                if (shouldReplace(existing, candidate)) {
                    copyTransitionMetadata(existing, candidate);
                }
                return;
            }
        }
        candidates.add(candidate);
    }

    private static List<ReleaseStep> buildUpdateChain(String currentBase, String currentDlc, Map<String, List<ReleaseStep>> transitions) {
        if (transitions == null || transitions.isEmpty()) {
            return List.of();
        }
        String currentKey = stateKey(currentBase, currentDlc);
        Set<String> visited = new HashSet<>();
        visited.add(currentKey);
        return buildBestUpdateChain(currentKey, transitions, visited);
    }

    private static List<ReleaseStep> buildBestUpdateChain(String currentKey, Map<String, List<ReleaseStep>> transitions, Set<String> visited) {
        List<ReleaseStep> candidates = transitions.get(currentKey);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ReleaseStep> best = List.of();
        for (ReleaseStep candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String nextKey = stateKey(candidate.newBase, candidate.newDlc);
            if (nextKey.equals(currentKey) || visited.contains(nextKey)) {
                continue;
            }
            visited.add(nextKey);
            List<ReleaseStep> tail = buildBestUpdateChain(nextKey, transitions, visited);
            visited.remove(nextKey);
            List<ReleaseStep> chain = new ArrayList<>();
            chain.add(candidate);
            chain.addAll(tail);
            if (isBetterChain(chain, best)) {
                best = chain;
            }
        }
        return best;
    }

    private static boolean isBetterChain(List<ReleaseStep> candidate, List<ReleaseStep> current) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (current == null || current.isEmpty()) {
            return true;
        }
        ReleaseStep candidateTail = candidate.get(candidate.size() - 1);
        ReleaseStep currentTail = current.get(current.size() - 1);
        int compare = compareState(candidateTail.newBase, candidateTail.newDlc, currentTail.newBase, currentTail.newDlc);
        if (compare != 0) {
            return compare > 0;
        }
        int lengthCompare = Integer.compare(candidate.size(), current.size());
        if (lengthCompare != 0) {
            return lengthCompare > 0;
        }
        int count = Math.min(candidate.size(), current.size());
        for (int i = 0; i < count; i++) {
            ReleaseStep left = candidate.get(i);
            ReleaseStep right = current.get(i);
            int targetCompare = compareState(left.newBase, left.newDlc, right.newBase, right.newDlc);
            if (targetCompare != 0) {
                return targetCompare > 0;
            }
            int repoCompare = Integer.compare(left.repoIndex, right.repoIndex);
            if (repoCompare != 0) {
                return repoCompare < 0;
            }
        }
        return false;
    }

    private static List<String> splitRepoUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                urls.add(trimmed);
            }
        }
        return urls;
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
                Path path = downloadClientStepWithFallback(step, updateDir, stepIndex, totalSteps, progress, cancelChecker);
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
                boolean imported = downloadServerStepWithFallback(step, entry, timeoutMs, stepIndex, totalSteps, progress, cancelChecker);
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

    private static Path downloadClientStepWithFallback(ReleaseStep step, Path updateDir, int stepIndex, int totalSteps,
                                                       ProgressListener progress, CancelChecker cancelChecker) throws IOException {
        IOException lastError = null;
        for (AssetCandidate candidate : resolveAssetCandidates(step)) {
            try {
                return DlcDownloadService.download(candidate.assetUrl, updateDir, candidate.assetName,
                    (done, total) -> notifyProgress(progress, stepIndex, totalSteps, candidate.assetName, done, total),
                    () -> cancelChecker != null && cancelChecker.isCanceled());
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                throw ex;
            } catch (IOException ex) {
                lastError = ex;
                if (!isRetryableDownloadFailure(ex)) {
                    throw ex;
                }
            }
        }
        throw lastError == null ? new IOException("download_failed") : lastError;
    }

    private static boolean downloadServerStepWithFallback(ReleaseStep step, DlcServerEntry entry, int timeoutMs, int stepIndex,
                                                          int totalSteps, ProgressListener progress, CancelChecker cancelChecker) throws Exception {
        Exception lastError = null;
        for (AssetCandidate candidate : resolveAssetCandidates(step)) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw new DlcDownloadService.DownloadCanceledException("canceled");
            }
            try {
                notifyProgress(progress, stepIndex, totalSteps, candidate.assetName, 0L, 1L);
                return DlcServerConnection.importUpdatePackageFromUrl(entry, timeoutMs, candidate.assetUrl, candidate.assetName);
            } catch (Exception ex) {
                lastError = ex;
                if (!isRetryableDownloadFailure(ex)) {
                    throw ex;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return false;
    }

    private static List<AssetCandidate> resolveAssetCandidates(ReleaseStep step) {
        if (step == null) {
            return List.of();
        }
        if (step.assetCandidates != null && !step.assetCandidates.isEmpty()) {
            return step.assetCandidates;
        }
        if (step.assetUrl == null || step.assetUrl.isBlank()) {
            return List.of();
        }
        return List.of(new AssetCandidate(step.repoIndex, step.repoUrl, step.assetName, step.assetUrl));
    }

    private static boolean isRetryableDownloadFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                || current instanceof ConnectException
                || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTimeoutFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.contains("timed out")
            || normalized.contains("timeout")
            || normalized.contains("connect timed out")
            || normalized.contains("read timed out");
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
        return false;
    }

    private static void copyTransitionMetadata(ReleaseStep target, ReleaseStep source) {
        if (target == null || source == null) {
            return;
        }
        List<AssetCandidate> mergedCandidates = target.assetCandidates;
        target.tag = source.tag;
        target.oldBase = source.oldBase;
        target.oldDlc = source.oldDlc;
        target.newBase = source.newBase;
        target.newDlc = source.newDlc;
        target.assetName = source.assetName;
        target.assetUrl = source.assetUrl;
        target.repoIndex = source.repoIndex;
        target.repoUrl = source.repoUrl;
        target.rawChangelog = source.rawChangelog;
        target.changelog = source.changelog;
        target.assetCandidates = mergedCandidates;
    }

    private static boolean sameTransition(ReleaseStep left, ReleaseStep right) {
        if (left == null || right == null) {
            return false;
        }
        return safeVersion(left.oldBase).equals(safeVersion(right.oldBase))
            && safeVersion(left.oldDlc).equals(safeVersion(right.oldDlc))
            && safeVersion(left.newBase).equals(safeVersion(right.newBase))
            && safeVersion(left.newDlc).equals(safeVersion(right.newDlc));
    }

    private static void mergeAssetCandidates(ReleaseStep target, ReleaseStep candidate) {
        if (target == null || candidate == null) {
            return;
        }
        List<AssetCandidate> merged = new ArrayList<>();
        appendAssetCandidates(merged, target.assetCandidates);
        appendAssetCandidates(merged, candidate.assetCandidates);
        target.assetCandidates = merged;
    }

    private static void appendAssetCandidates(List<AssetCandidate> merged, List<AssetCandidate> candidates) {
        if (merged == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        for (AssetCandidate candidate : candidates) {
            if (candidate == null || candidate.assetUrl == null || candidate.assetUrl.isBlank()) {
                continue;
            }
            boolean exists = false;
            for (AssetCandidate existing : merged) {
                if (existing == null) {
                    continue;
                }
                if (existing.repoIndex == candidate.repoIndex && existing.assetUrl.equals(candidate.assetUrl)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                merged.add(candidate);
            }
        }
    }

    private static AssetChoice resolvePreferredAsset(JsonObject release, Side side, String base, String dlc) {
        List<String> universalNames = buildAssetNameCandidates(UNIVERSAL_PREFIX, base, dlc);
        String sidePrefix = side == null ? "" : side.assetPrefix();
        List<String> sideNames = buildAssetNameCandidates(sidePrefix, base, dlc);
        for (String name : universalNames) {
            String url = findAssetUrl(release, name);
            if (!url.isBlank()) {
                return new AssetChoice(name, url);
            }
        }
        for (String name : sideNames) {
            String url = findAssetUrl(release, name);
            if (!url.isBlank()) {
                return new AssetChoice(name, url);
            }
        }
        String fallbackName = !sideNames.isEmpty()
            ? sideNames.get(0)
            : (universalNames.isEmpty() ? "" : universalNames.get(0));
        return new AssetChoice(fallbackName, "");
    }

    private static List<String> buildAssetNameCandidates(String prefix, String base, String dlc) {
        String namePrefix = prefix == null ? "" : prefix;
        String safeBase = safeVersion(base);
        String safeDlc = safeVersion(dlc);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String delimiter : VERSION_PAIR_DELIMITERS) {
            names.add(namePrefix + safeBase + delimiter + safeDlc + ".zip");
        }
        return new ArrayList<>(names);
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
        if (!release.has("assets") || release.get("assets").isJsonNull()) {
            return "";
        }
        JsonElement assetsElement = release.get("assets");
        if (assetsElement.isJsonArray()) {
            JsonArray assets = assetsElement.getAsJsonArray();
            for (JsonElement element : assets) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject asset = element.getAsJsonObject();
                String name = getString(asset, "name");
                String download = getString(asset, "browser_download_url");
                String altDownload = getString(asset, "url");
                if (!assetMatchesExpected(name, download, altDownload, expectedName)) {
                    continue;
                }
                if (!download.isBlank()) {
                    return download;
                }
                if (!altDownload.isBlank()) {
                    return altDownload;
                }
            }
            return "";
        }
        if (assetsElement.isJsonObject()) {
            JsonObject assetsObj = assetsElement.getAsJsonObject();
            if (!assetsObj.has("links") || !assetsObj.get("links").isJsonArray()) {
                return "";
            }
            JsonArray links = assetsObj.getAsJsonArray("links");
            for (JsonElement element : links) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject link = element.getAsJsonObject();
                String name = getString(link, "name");
                String direct = getString(link, "direct_asset_url");
                String url = getString(link, "url");
                if (!assetMatchesExpected(name, direct, url, expectedName)) {
                    continue;
                }
                if (!direct.isBlank()) {
                    return direct;
                }
                if (!url.isBlank()) {
                    return url;
                }
            }
        }
        return "";
    }

    private static boolean assetMatchesExpected(String name, String primaryUrl, String secondaryUrl, String expectedName) {
        if (expectedName == null || expectedName.isBlank()) {
            return false;
        }
        if (name != null && name.equalsIgnoreCase(expectedName)) {
            return true;
        }
        String fromPrimary = extractFileNameFromUrl(primaryUrl);
        if (!fromPrimary.isBlank() && fromPrimary.equalsIgnoreCase(expectedName)) {
            return true;
        }
        String fromSecondary = extractFileNameFromUrl(secondaryUrl);
        return !fromSecondary.isBlank() && fromSecondary.equalsIgnoreCase(expectedName);
    }

    private static String extractFileNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "";
            }
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < path.length()) {
                return path.substring(slash + 1).trim();
            }
            return path.trim();
        } catch (Exception ignored) {
            String normalized = url.trim();
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < normalized.length()) {
                return normalized.substring(slash + 1).trim();
            }
            return "";
        }
    }

    private static ParsedTag parseTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        ParsedTagParts parts = parseTagParts(tag.trim());
        if (parts == null) {
            return null;
        }
        ParsedTag parsed = new ParsedTag();
        parsed.oldBase = parts.oldPair.base;
        parsed.oldDlc = parts.oldPair.dlc;
        parsed.newBase = parts.newPair.base;
        parsed.newDlc = parts.newPair.dlc;
        return parsed;
    }

    private static ParsedTagParts parseTagParts(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String[] slashParts = tag.split("/", 2);
        if (slashParts.length == 2) {
            ParsedTagParts parsed = parseTagParts(slashParts[0], slashParts[1]);
            if (parsed != null) {
                return parsed;
            }
        }
        int index = -1;
        while ((index = tag.indexOf('-', index + 1)) >= 0) {
            if (index <= 0 || index >= tag.length() - 1) {
                continue;
            }
            ParsedTagParts parsed = parseTagParts(tag.substring(0, index), tag.substring(index + 1));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static ParsedTagParts parseTagParts(String oldRaw, String newRaw) {
        VersionPair oldPair = parseVersionPair(oldRaw);
        VersionPair newPair = parseVersionPair(newRaw);
        if (oldPair == null || newPair == null) {
            return null;
        }
        ParsedTagParts parts = new ParsedTagParts();
        parts.oldPair = oldPair;
        parts.newPair = newPair;
        return parts;
    }

    private static VersionPair parseVersionPair(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String[] parts = splitVersionPair(trimmed);
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

    private static String[] splitVersionPair(String value) {
        if (value == null) {
            return new String[0];
        }
        for (String delimiter : VERSION_PAIR_DELIMITERS) {
            int idx = value.indexOf(delimiter);
            if (idx <= 0 || idx >= value.length() - 1) {
                continue;
            }
            String left = value.substring(0, idx).trim();
            String right = value.substring(idx + delimiter.length()).trim();
            if (!left.isBlank() && !right.isBlank()) {
                return new String[] {left, right};
            }
        }
        String[] whitespaceParts = value.trim().split("\\s+");
        if (whitespaceParts.length == 2) {
            String left = whitespaceParts[0].trim();
            String right = whitespaceParts[1].trim();
            if (!left.isBlank() && !right.isBlank()) {
                return new String[] {left, right};
            }
        }
        return new String[0];
    }

    private static JsonArray fetchReleases(RepoCoordinates repo, String currentBase, String currentDlc) throws IOException {
        List<ApiEndpoint> endpoints = buildReleaseApiEndpoints(repo);
        IOException lastException = null;
        for (ApiEndpoint endpoint : endpoints) {
            try {
                return fetchReleasesFromEndpoint(endpoint, currentBase, currentDlc);
            } catch (IOException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("release_fetch_failed");
    }

    private static JsonArray fetchReleasesFromEndpoint(ApiEndpoint endpoint, String currentBase, String currentDlc) throws IOException {
        JsonArray collected = new JsonArray();
        String safeCurrentBase = safeVersion(currentBase);
        String safeCurrentDlc = safeVersion(currentDlc);
        for (int page = 1; page <= RELEASES_MAX_PAGES; page++) {
            JsonArray pageReleases = fetchReleasePage(endpoint, page);
            if (pageReleases == null || pageReleases.isEmpty()) {
                break;
            }
            boolean shouldStop = appendReleasesUntilCurrentBoundary(collected, pageReleases, safeCurrentBase, safeCurrentDlc);
            if (shouldStop || pageReleases.size() < RELEASES_PER_PAGE) {
                break;
            }
        }
        return collected;
    }

    private static JsonArray fetchReleasePage(ApiEndpoint endpoint, int page) throws IOException {
        String api = appendPageParameter(endpoint.url, page);
        HttpURLConnection connection = (HttpURLConnection) URI.create(api).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "DLC-Manager");
        if (endpoint.accept != null && !endpoint.accept.isBlank()) {
            connection.setRequestProperty("Accept", endpoint.accept);
        }
        if (endpoint.authHeaderName != null && endpoint.authHeaderValue != null
            && !endpoint.authHeaderName.isBlank() && !endpoint.authHeaderValue.isBlank()) {
            connection.setRequestProperty(endpoint.authHeaderName, endpoint.authHeaderValue);
        }
        connection.connect();
        try {
            int code = connection.getResponseCode();
            InputStream payloadStream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String payload = "";
            if (payloadStream != null) {
                try (InputStream in = payloadStream;
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    char[] buffer = new char[2048];
                    StringBuilder sb = new StringBuilder();
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, read);
                    }
                    payload = sb.toString();
                }
            }
            if (code >= 400) {
                throw new IOException("http " + code + " @ " + api);
            }
            JsonArray releases = parseReleaseArray(payload, api);
            if (releases != null) {
                return releases;
            }
            throw new IOException("invalid_release_response @ " + api);
        } finally {
            connection.disconnect();
        }
    }

    private static boolean appendReleasesUntilCurrentBoundary(JsonArray collected, JsonArray pageReleases, String currentBase, String currentDlc) {
        if (collected == null || pageReleases == null || pageReleases.isEmpty()) {
            return false;
        }
        boolean hasRelevantRelease = hasRelevantRelease(collected, currentBase, currentDlc);
        for (JsonElement element : pageReleases) {
            if (element == null || !element.isJsonObject()) {
                collected.add(element);
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            String tag = getString(release, "tag_name");
            if (tag.isBlank()) {
                tag = getString(release, "name");
            }
            ParsedTag parsed = parseTag(tag);
            if (parsed == null) {
                collected.add(release);
                continue;
            }
            int compare = compareState(parsed.oldBase, parsed.oldDlc, currentBase, currentDlc);
            if (compare < 0) {
                if (hasRelevantRelease) {
                    return true;
                }
                continue;
            }
            collected.add(release);
            hasRelevantRelease = true;
        }
        return false;
    }

    private static boolean hasRelevantRelease(JsonArray releases, String currentBase, String currentDlc) {
        if (releases == null || releases.isEmpty()) {
            return false;
        }
        for (JsonElement element : releases) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            String tag = getString(release, "tag_name");
            if (tag.isBlank()) {
                tag = getString(release, "name");
            }
            ParsedTag parsed = parseTag(tag);
            if (parsed != null && compareState(parsed.oldBase, parsed.oldDlc, currentBase, currentDlc) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int compareState(String baseA, String dlcA, String baseB, String dlcB) {
        int compareBase = DlcUpdateService.compareVersions(safeVersion(baseA), safeVersion(baseB));
        if (compareBase != 0) {
            return compareBase;
        }
        return DlcUpdateService.compareVersions(safeVersion(dlcA), safeVersion(dlcB));
    }

    private static String appendPageParameter(String url, int page) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "page=" + Math.max(page, 1);
    }

    private static JsonArray parseReleaseArray(String payload, String api) throws IOException {
        if (payload == null) {
            return new JsonArray();
        }
        String text = stripJsonPrefix(payload);
        if (text.isBlank()) {
            return new JsonArray();
        }
        try {
            JsonReader jsonReader = new JsonReader(new StringReader(text));
            jsonReader.setLenient(true);
            JsonElement element = JsonParser.parseReader(jsonReader);
            if (element == null || element.isJsonNull()) {
                return new JsonArray();
            }
            if (element.isJsonArray()) {
                return element.getAsJsonArray();
            }
            if (element.isJsonObject()) {
                JsonArray wrapped = unwrapReleaseArray(element.getAsJsonObject());
                if (wrapped != null) {
                    return wrapped;
                }
            }
        } catch (Exception ex) {
            throw new IOException("malformed_release_json @ " + api, ex);
        }
        return null;
    }

    private static String stripJsonPrefix(String payload) {
        String text = payload;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        text = text.trim();
        if (text.startsWith(")]}'")) {
            int newline = text.indexOf('\n');
            if (newline >= 0 && newline + 1 < text.length()) {
                text = text.substring(newline + 1).trim();
            }
        }
        return text;
    }

    private static JsonArray unwrapReleaseArray(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        String[] keys = new String[] {"data", "list", "items", "releases", "records"};
        for (String key : keys) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                continue;
            }
            JsonElement candidate = obj.get(key);
            if (candidate.isJsonArray()) {
                return candidate.getAsJsonArray();
            }
            if (candidate.isJsonObject()) {
                JsonArray nested = unwrapReleaseArray(candidate.getAsJsonObject());
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static RepoCoordinates parseRepoCoordinates(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim();
        Matcher sshMatcher = SSH_REPO_PATTERN.matcher(normalized);
        if (sshMatcher.matches()) {
            String host = normalizeHost(sshMatcher.group(1));
            Provider provider = detectProvider(host);
            RepoPath repoPath = parseRepoPathSegments(sshMatcher.group(2), provider);
            if (host.isBlank() || repoPath == null) {
                return null;
            }
            RepoCoordinates coordinates = new RepoCoordinates();
            coordinates.host = host;
            coordinates.owner = repoPath.owner;
            coordinates.repo = repoPath.repo;
            coordinates.namespacePath = repoPath.namespacePath;
            coordinates.provider = provider;
            return coordinates;
        }
        URI uri = parseRepoUri(normalized);
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
            return null;
        }
        String host = normalizeHost(uri.getHost());
        Provider provider = detectProvider(host);
        String rawPath = uri.getPath();
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        RepoPath repoPath = parseRepoPathSegments(rawPath, provider);
        if (repoPath == null) {
            return null;
        }
        RepoCoordinates coordinates = new RepoCoordinates();
        coordinates.host = host;
        coordinates.owner = repoPath.owner;
        coordinates.repo = repoPath.repo;
        coordinates.namespacePath = repoPath.namespacePath;
        coordinates.provider = provider;
        return coordinates;
    }

    private static URI parseRepoUri(String value) {
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return URI.create(value);
            }
            return URI.create("https://" + value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        return host.trim().toLowerCase();
    }

    private static Provider detectProvider(String host) {
        if (host.endsWith("github.com")) {
            return Provider.GITHUB;
        }
        if (host.endsWith("gitcode.com")) {
            return Provider.GITCODE;
        }
        if (host.endsWith("gitee.com")) {
            return Provider.GITEE;
        }
        if (host.endsWith("gitlab.com") || host.contains("gitlab")) {
            return Provider.GITLAB;
        }
        return Provider.GENERIC;
    }

    private static List<ApiEndpoint> buildReleaseApiEndpoints(RepoCoordinates repo) {
        if (repo == null || repo.owner == null || repo.repo == null) {
            return List.of();
        }
        String owner = repo.owner;
        String project = repo.repo;
        String host = repo.host;
        String namespacePath = repo.namespacePath == null || repo.namespacePath.isBlank()
            ? owner + "/" + project
            : repo.namespacePath;
        String projectPath = urlEncode(namespacePath);
        List<ApiEndpoint> endpoints = new ArrayList<>();
        switch (repo.provider) {
            case GITHUB -> endpoints.add(new ApiEndpoint(
                "https://api.github.com/repos/" + owner + "/" + project + "/releases?per_page=" + RELEASES_PER_PAGE,
                "application/vnd.github+json"));
            case GITCODE -> endpoints.add(new ApiEndpoint(
                "https://api.gitcode.com/api/v5/repos/" + owner + "/" + project + "/releases?per_page=" + RELEASES_PER_PAGE,
                "application/json",
                "PRIVATE-TOKEN",
                getGitCodeAccessToken()));
            case GITEE -> endpoints.add(new ApiEndpoint(
                "https://gitee.com/api/v5/repos/" + owner + "/" + project + "/releases?per_page=" + RELEASES_PER_PAGE,
                "application/json"));
            case GITLAB -> endpoints.add(new ApiEndpoint(
                "https://" + host + "/api/v4/projects/" + projectPath + "/releases?per_page=" + RELEASES_PER_PAGE,
                "application/json"));
            case GENERIC -> {
                endpoints.add(new ApiEndpoint(
                    "https://" + host + "/api/v5/repos/" + owner + "/" + project + "/releases?per_page=" + RELEASES_PER_PAGE,
                    "application/json"));
                endpoints.add(new ApiEndpoint(
                    "https://api." + host + "/repos/" + owner + "/" + project + "/releases?per_page=" + RELEASES_PER_PAGE,
                    "application/json"));
                endpoints.add(new ApiEndpoint(
                    "https://" + host + "/api/v4/projects/" + projectPath + "/releases?per_page=" + RELEASES_PER_PAGE,
                    "application/json"));
            }
        }
        return endpoints;
    }

    private static RepoPath parseRepoPathSegments(String rawPath, Provider provider) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String[] segments = rawPath.split("/");
        List<String> parts = new ArrayList<>();
        for (String segment : segments) {
            String trimmed = normalizeRepoPart(segment);
            if (!trimmed.isBlank()) {
                parts.add(trimmed);
            }
        }
        if (parts.size() < 2) {
            return null;
        }
        List<String> repoParts = new ArrayList<>(parts);
        if (provider == Provider.GITLAB || provider == Provider.GENERIC) {
            int routeMarker = repoParts.indexOf("-");
            if (routeMarker >= 2) {
                repoParts = new ArrayList<>(repoParts.subList(0, routeMarker));
            } else {
                while (repoParts.size() > 2) {
                    String tail = repoParts.get(repoParts.size() - 1).toLowerCase();
                    if (isKnownRepoRouteSegment(tail)) {
                        repoParts.remove(repoParts.size() - 1);
                        continue;
                    }
                    break;
                }
            }
        } else {
            repoParts = new ArrayList<>(repoParts.subList(0, 2));
        }
        if (repoParts.size() < 2) {
            return null;
        }
        RepoPath repoPath = new RepoPath();
        repoPath.owner = normalizeRepoPart(repoParts.get(0));
        repoPath.repo = normalizeRepoPart(repoParts.get(repoParts.size() - 1));
        repoPath.namespacePath = String.join("/", repoParts);
        if (repoPath.owner.isBlank() || repoPath.repo.isBlank() || repoPath.namespacePath.isBlank()) {
            return null;
        }
        return repoPath;
    }

    private static boolean isKnownRepoRouteSegment(String tail) {
        return "releases".equals(tail)
            || "tags".equals(tail)
            || "issues".equals(tail)
            || "merge_requests".equals(tail)
            || "wikis".equals(tail)
            || "blob".equals(tail)
            || "tree".equals(tail)
            || "raw".equals(tail)
            || "commit".equals(tail)
            || "commits".equals(tail)
            || "pipelines".equals(tail)
            || "activity".equals(tail);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception ignored) {
            return value;
        }
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

    private static String formatCompactTransition(String oldBase, String oldDlc, String newBase, String newDlc) {
        String sourceBase = safeVersion(oldBase);
        String sourceDlc = safeVersion(oldDlc);
        String targetBase = safeVersion(newBase);
        String targetDlc = safeVersion(newDlc);
        boolean baseChanged = !sourceBase.equals(targetBase);
        boolean dlcChanged = !sourceDlc.equals(targetDlc);
        if (baseChanged && dlcChanged) {
            return sourceBase + "+" + sourceDlc + " -> " + targetBase + "+" + targetDlc;
        }
        if (baseChanged) {
            return sourceBase + " -> " + targetBase;
        }
        if (dlcChanged) {
            return sourceDlc + " -> " + targetDlc;
        }
        return sourceBase + "+" + sourceDlc + " -> " + targetBase + "+" + targetDlc;
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
        String[] keys = new String[] {"body", "description", "release_notes", "notes"};
        for (String key : keys) {
            String value = getString(release, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
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

    private static String getGitCodeAccessToken() {
        if (GITCODE_TOKEN_CACHE != null) {
            return GITCODE_TOKEN_CACHE;
        }
        int length = GITCODE_TOKEN_PART1.length;
        byte[] part2 = generateGitCodeTokenPart2(length);
        byte[] decoded = new byte[length];
        for (int i = 0; i < length; i++) {
            byte mixed = (byte) (GITCODE_TOKEN_PART1[i] ^ part2[i]);
            decoded[i] = (byte) (mixed ^ GITCODE_TOKEN_MASK ^ ((i * 17) & 0xFF));
        }
        byte[] shifted = new byte[length];
        for (int i = 0; i < length; i++) {
            shifted[i] = decoded[(i + GITCODE_TOKEN_SHIFT) % length];
        }
        GITCODE_TOKEN_CACHE = new String(shifted, StandardCharsets.UTF_8);
        return GITCODE_TOKEN_CACHE;
    }

    private static byte[] generateGitCodeTokenPart2(int length) {
        byte[] bytes = new byte[length];
        int seed = GITCODE_TOKEN_PART2_SEED;
        for (int i = 0; i < length; i++) {
            seed = (seed * 73 + 41) & 0xFF;
            bytes[i] = (byte) seed;
        }
        return bytes;
    }

    private static final class RepoCoordinates {
        private String host;
        private String owner;
        private String repo;
        private String namespacePath;
        private Provider provider;
    }

    private enum Provider {
        GITHUB,
        GITCODE,
        GITEE,
        GITLAB,
        GENERIC
    }

    private static final class ApiEndpoint {
        private final String url;
        private final String accept;
        private final String authHeaderName;
        private final String authHeaderValue;

        private ApiEndpoint(String url, String accept) {
            this(url, accept, null, null);
        }

        private ApiEndpoint(String url, String accept, String authHeaderName, String authHeaderValue) {
            this.url = url;
            this.accept = accept;
            this.authHeaderName = authHeaderName;
            this.authHeaderValue = authHeaderValue;
        }
    }

    private static final class RepoPath {
        private String owner;
        private String repo;
        private String namespacePath;
    }

    private static final class ParsedTag {
        private String oldBase;
        private String oldDlc;
        private String newBase;
        private String newDlc;
    }

    private static final class ParsedTagParts {
        private VersionPair oldPair;
        private VersionPair newPair;
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
