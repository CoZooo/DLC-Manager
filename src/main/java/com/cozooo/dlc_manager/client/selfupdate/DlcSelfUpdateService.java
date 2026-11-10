package com.cozooo.dlc_manager.client.selfupdate;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.DlcCurseForgeClient;
import com.cozooo.dlc_manager.client.download.DlcModrinthClient;
import com.cozooo.dlc_manager.core.DlcVersionUtil;
import com.cozooo.dlc_manager.client.update.DlcUpdateSkipStore;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcSelfUpdateService {
    public static final String MOD_ID = "dlc_manager";
    public static final String PROJECT_ID = "1424546";
    private static final String MODRINTH_PROJECT_ID = "B05EUil9";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern DISPLAY_NAME_VERSION_PATTERN = Pattern.compile(
        "^\\s*(\\d+\\.\\d+(?:\\.\\d+)?(?:-[0-9A-Za-z][0-9A-Za-z.]*)?)\\s*\\("
    );
    private static final Pattern FILE_NAME_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?");
    private static final Pattern FILE_NAME_PRERELEASE_PATTERN = Pattern.compile(
        "(?i)(?:alpha|beta|rc|pre|nightly|build)[0-9A-Za-z.]*"
    );
    private static final Pattern MAJOR_VERSION_PATTERN = Pattern.compile("(\\d+)");

    private DlcSelfUpdateService() {
    }

    public static SelfUpdateResult checkForUpdate() {
        return checkForUpdate(null);
    }

    public static SelfUpdateResult checkForUpdate(String targetVersion) {
        String loader = DlcPlatformServices.loaderName();
        String mcVersion = DlcPlatformServices.mcVersion();
        String currentVersion = DlcPlatformServices.modVersion(MOD_ID);
        String requestedVersion = targetVersion == null ? null : targetVersion.trim();
        if (requestedVersion != null && requestedVersion.isBlank()) {
            requestedVersion = null;
        }
        if (loader == null || loader.isBlank() || mcVersion == null || mcVersion.isBlank()) {
            return SelfUpdateResult.none(currentVersion);
        }
        if (requestedVersion != null && isSameVersion(currentVersion, requestedVersion)) {
            SelfUpdateResult result = SelfUpdateResult.none(currentVersion);
            result.latestVersion = requestedVersion;
            result.requestedVersion = requestedVersion;
            return result;
        }
        return requestedVersion == null
            ? checkAutomaticUpdate(currentVersion, mcVersion, loader)
            : checkRequestedVersion(currentVersion, requestedVersion, mcVersion, loader);
    }

    private static SelfUpdateResult checkAutomaticUpdate(String currentVersion, String mcVersion, String loader) {
        List<UpdateCandidate> candidates = new ArrayList<>();
        boolean fetched = false;
        try {
            for (DlcCurseForgeClient.CurseForgeFile file : DlcCurseForgeClient.fetchFiles(PROJECT_ID, mcVersion, loader)) {
                UpdateCandidate candidate = UpdateCandidate.fromCurseForge(file);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            fetched = true;
        } catch (Exception ignored) {
        }
        UpdateCandidate best = pickUpdateCandidate(currentVersion, candidates);
        if (best == null) {
            try {
                for (DlcModrinthClient.ModrinthVersion version : DlcModrinthClient.fetchVersions(MODRINTH_PROJECT_ID, mcVersion, loader)) {
                    UpdateCandidate candidate = UpdateCandidate.fromModrinth(version);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
                fetched = true;
            } catch (Exception ignored) {
            }
            best = pickUpdateCandidate(currentVersion, candidates);
        }
        if (!fetched) {
            return SelfUpdateResult.failed(currentVersion);
        }
        if (best == null) {
            return SelfUpdateResult.none(currentVersion);
        }
        if (best.curseForgeFile != null && (best.url == null || best.url.isBlank())) {
            try {
                DlcCurseForgeClient.resolveDownloadUrl(PROJECT_ID, best.curseForgeFile);
            } catch (Exception ignored) {
            }
            best.url = best.curseForgeFile.url;
        }
        if (best.url == null || best.url.isBlank()) {
            return SelfUpdateResult.failed(currentVersion);
        }
        SelfUpdateResult result = new SelfUpdateResult();
        result.currentVersion = currentVersion == null ? "" : currentVersion;
        result.latestVersion = best.version == null ? "" : best.version;
        result.downloadUrl = best.url;
        result.fileName = best.fileName;
        result.fileId = best.fileId;
        result.displayName = best.displayName;
        result.fileDate = best.fileDate;
        result.hasUpdate = true;
        return result;
    }

    private static SelfUpdateResult checkRequestedVersion(String currentVersion, String requestedVersion, String mcVersion, String loader) {
        boolean lookupFailed = false;
        DlcCurseForgeClient.CurseForgeFile file = null;
        try {
            file = DlcCurseForgeClient.fetchFileByIdOrVersion(PROJECT_ID, requestedVersion, mcVersion, loader);
        } catch (Exception ignored) {
            lookupFailed = true;
        }
        if (file != null) {
            return requestedCurseForgeResult(currentVersion, requestedVersion, file);
        }
        DlcModrinthClient.ModrinthVersion version = null;
        try {
            version = DlcModrinthClient.fetchVersionByIdOrVersion(MODRINTH_PROJECT_ID, requestedVersion, mcVersion, loader);
        } catch (Exception ignored) {
        }
        if (version != null) {
            return requestedModrinthResult(currentVersion, requestedVersion, version);
        }
        SelfUpdateResult result = SelfUpdateResult.none(currentVersion);
        result.requestedVersion = requestedVersion;
        if (lookupFailed) {
            result.checkFailed = true;
        } else {
            result.missingRequestedVersion = true;
        }
        return result;
    }

    private static SelfUpdateResult requestedCurseForgeResult(String currentVersion, String requestedVersion, DlcCurseForgeClient.CurseForgeFile file) {
        if (file.url == null || file.url.isBlank()) {
            try {
                DlcCurseForgeClient.resolveDownloadUrl(PROJECT_ID, file);
            } catch (Exception ignored) {
            }
        }
        if (file.url == null || file.url.isBlank()) {
            SelfUpdateResult failed = SelfUpdateResult.failed(currentVersion);
            failed.requestedVersion = requestedVersion;
            return failed;
        }
        String latestVersion = extractVersion(file.displayName, file.fileName);
        String effectiveVersion = latestVersion == null || latestVersion.isBlank() ? requestedVersion : latestVersion;
        SelfUpdateResult result = new SelfUpdateResult();
        result.currentVersion = currentVersion == null ? "" : currentVersion;
        result.latestVersion = effectiveVersion;
        result.downloadUrl = file.url;
        result.fileName = file.fileName;
        result.fileId = file.id;
        result.displayName = file.displayName;
        result.fileDate = file.fileDate;
        result.hasUpdate = !isSameVersion(currentVersion, effectiveVersion);
        result.requestedVersion = requestedVersion;
        return result;
    }

    private static SelfUpdateResult requestedModrinthResult(String currentVersion, String requestedVersion, DlcModrinthClient.ModrinthVersion version) {
        if (version.url == null || version.url.isBlank()) {
            SelfUpdateResult failed = SelfUpdateResult.failed(currentVersion);
            failed.requestedVersion = requestedVersion;
            return failed;
        }
        String latestVersion = version.versionNumber;
        String effectiveVersion = latestVersion == null || latestVersion.isBlank() ? requestedVersion : latestVersion;
        SelfUpdateResult result = new SelfUpdateResult();
        result.currentVersion = currentVersion == null ? "" : currentVersion;
        result.latestVersion = effectiveVersion;
        result.downloadUrl = version.url;
        result.fileName = version.fileName;
        result.fileId = version.versionId;
        result.displayName = effectiveVersion;
        result.fileDate = version.datePublished == null ? null : version.datePublished.toString();
        result.hasUpdate = !isSameVersion(currentVersion, effectiveVersion);
        result.requestedVersion = requestedVersion;
        return result;
    }

    public static DownloadResult downloadUpdate(SelfUpdateResult result) {
        if (result == null || !result.hasUpdate || result.downloadUrl == null || result.downloadUrl.isBlank()) {
            return DownloadResult.failed("no_update");
        }
        String fileName = result.fileName;
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(result.downloadUrl);
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "dlc_manager-update.jar";
        }
        Path dlcRoot = DlcPaths.dlcRoot();
        if (dlcRoot == null) {
            return DownloadResult.failed("dlc_root_missing");
        }
        try {
            if (!Files.exists(dlcRoot)) {
                Files.createDirectories(dlcRoot);
            }
        } catch (IOException ignored) {
        }
        Path updateDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate");
        Path tempFile = updateDir.resolve(fileName + ".part");
        Path targetFile = updateDir.resolve(fileName);
        try {
            Files.createDirectories(updateDir);
            if (Files.exists(tempFile)) {
                Files.deleteIfExists(tempFile);
            }
            downloadToFile(result.downloadUrl, tempFile);
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            return DownloadResult.failed("download_failed");
        }
        Path modFilePath = DlcPlatformServices.modFilePath(MOD_ID);
        if (modFilePath == null || !Files.exists(modFilePath) || Files.isDirectory(modFilePath)) {
            try {
                Files.deleteIfExists(targetFile);
            } catch (IOException ignored) {
            }
            return DownloadResult.failed("mod_file_missing");
        }
        Path modsDir = modFilePath.getParent();
        if (modsDir == null) {
            try {
                Files.deleteIfExists(targetFile);
            } catch (IOException ignored) {
            }
            return DownloadResult.failed("mod_file_missing");
        }
        Path finalFile = modsDir.resolve(targetFile.getFileName().toString());
        try {
            Path backupFile = replaceModFileNow(targetFile, finalFile, modFilePath);
            if (backupFile != null) {
                saveHistory(backupFile, result.currentVersion, result.latestVersion,
                    modFilePath.getFileName().toString(), finalFile.getFileName().toString());
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(targetFile);
            } catch (IOException ignored) {
            }
            return DownloadResult.failed("download_failed");
        }
        clearSkipPromptMarker();
        clearPendingMarker();
        return DownloadResult.success(finalFile);
    }

    public static void skipPromptForUpdate(SelfUpdateResult result) {
        if (result == null || !result.hasUpdate) {
            return;
        }
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("fileId", result.fileId == null ? "" : result.fileId);
            obj.addProperty("latestVersion", result.latestVersion == null ? "" : result.latestVersion);
            obj.addProperty("fileName", result.fileName == null ? "" : result.fileName);
            obj.addProperty("skippedAt", Instant.now().toString());
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE, obj);
        } catch (Exception ignored) {
        }
    }

    public static boolean isPromptSkippedForUpdate(SelfUpdateResult result) {
        if (result == null || !result.hasUpdate) {
            return false;
        }
        JsonObject obj = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE);
        if (obj == null) {
            return false;
        }
        String skippedFileId = readString(obj, "fileId");
        String skippedLatest = readString(obj, "latestVersion");
        String skippedFileName = readString(obj, "fileName");

        if (!skippedFileId.isBlank() && result.fileId != null && !result.fileId.isBlank() && skippedFileId.equals(result.fileId)) {
            return true;
        }
        if (!skippedLatest.isBlank() && result.latestVersion != null && !result.latestVersion.isBlank() && skippedLatest.equals(result.latestVersion)) {
            return true;
        }
        return !skippedFileName.isBlank() && result.fileName != null && !result.fileName.isBlank() && skippedFileName.equals(result.fileName);
    }

    public static boolean applyPendingUpdate() {
        Path marker = getMarkerPath();
        if (marker == null || !Files.exists(marker)) {
            return false;
        }
        JsonObject obj;
        try {
            obj = DlcDcConfigIO.read(marker, DlcDcConfigIO.Layout.GENERIC);
        } catch (Exception ex) {
            return false;
        }
        if (obj == null || !obj.has("pendingFile") || !obj.has("targetFile")) {
            return false;
        }
        Path pending = resolveStoredPath(obj.get("pendingFile").getAsString());
        Path target = resolveStoredPath(obj.get("targetFile").getAsString());
        if (!Files.exists(pending) || target == null) {
            return false;
        }
        try {
            Path targetDir = target.getParent();
            if (targetDir == null) {
                return false;
            }
            Path dlcRoot = DlcPaths.dlcRoot();
            if (dlcRoot == null) {
                return false;
            }
            try {
                if (!Files.exists(dlcRoot)) {
                    Files.createDirectories(dlcRoot);
                }
            } catch (IOException ignored) {
            }
            Path backupDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate");
            Files.createDirectories(backupDir);
            if (Files.exists(target)) {
                String backupName = target.getFileName().toString() + "." + System.currentTimeMillis() + ".bak";
                Files.move(target, backupDir.resolve(backupName), StandardCopyOption.REPLACE_EXISTING);
            }
            if (!pending.getParent().equals(targetDir)) {
                Path newTarget = targetDir.resolve(pending.getFileName().toString());
                Files.copy(pending, newTarget, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(pending);
            }
            Files.deleteIfExists(marker);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean rollbackToPrevious() {
        SelfUpdateHistory history = loadHistory();
        if (history == null || history.backupFile == null || history.backupFile.isBlank()) {
            return false;
        }
        Path backup = DlcPaths.parsePathLenient(history.backupFile);
        if (backup == null) {
            return false;
        }
        if (!Files.exists(backup)) {
            return false;
        }
        Path current = DlcPlatformServices.modFilePath(MOD_ID);
        if (current == null || !Files.exists(current)) {
            return false;
        }
        Path dlcRoot = DlcPaths.dlcRoot();
        if (dlcRoot == null) {
            return false;
        }
        try {
            if (!Files.exists(dlcRoot)) {
                Files.createDirectories(dlcRoot);
            }
            Path backupDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate");
            Files.createDirectories(backupDir);
            Path modsDir = current.getParent();
            if (modsDir == null) {
                return false;
            }
            if (history.newFileName != null && !history.newFileName.isBlank()) {
                Path newFile = modsDir.resolve(history.newFileName);
                if (Files.exists(newFile)) {
                    Files.deleteIfExists(newFile);
                }
            }
            if (Files.exists(current)) {
                String backupName = current.getFileName().toString() + "." + System.currentTimeMillis() + ".bak";
                Files.move(current, backupDir.resolve(backupName), StandardCopyOption.REPLACE_EXISTING);
            }
            String restoreName;
            if (history.oldFileName == null || history.oldFileName.isBlank()) {
                String backupName = backup.getFileName().toString();
                if (backupName.endsWith(".bak")) {
                    backupName = backupName.substring(0, backupName.length() - 4);
                    int lastDot = backupName.lastIndexOf('.');
                    if (lastDot > 0) {
                        String tail = backupName.substring(lastDot + 1);
                        if (tail.chars().allMatch(Character::isDigit)) {
                            backupName = backupName.substring(0, lastDot);
                        }
                    }
                }
                restoreName = backupName;
            } else {
                restoreName = history.oldFileName;
            }
            Path target = modsDir.resolve(restoreName);
            Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
            clearHistory();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static SelfUpdateHistory loadHistory() {
        Path path = getHistoryPath();
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            JsonObject root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
            SelfUpdateHistory history = root == null ? null : GSON.fromJson(root, SelfUpdateHistory.class);
            if (history != null && history.backupFile != null && !history.backupFile.isBlank()) {
                Path dlcRoot = DlcPaths.dlcRoot();
                Path historyPath = DlcPaths.parsePathLenient(history.backupFile);
                if (dlcRoot != null && historyPath != null && !historyPath.isAbsolute()) {
                    history.backupFile = dlcRoot.resolve(historyPath).toString();
                }
            }
            return history;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void clearHistory() {
        Path path = getHistoryPath();
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static Path replaceModFileNow(Path downloadedFile, Path finalFile, Path currentModFile) throws IOException {
        Path dlcRoot = DlcPaths.dlcRoot();
        if (dlcRoot == null) {
            throw new IOException("dlc_root_missing");
        }
        if (!Files.exists(dlcRoot)) {
            Files.createDirectories(dlcRoot);
        }
        Path backupDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate");
        Files.createDirectories(backupDir);
        Path backupPath = null;
        if (Files.exists(currentModFile)) {
            String backupName = currentModFile.getFileName().toString() + "." + System.currentTimeMillis() + ".bak";
            backupPath = backupDir.resolve(backupName);
            Files.move(currentModFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!downloadedFile.equals(finalFile)) {
            Files.copy(downloadedFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(downloadedFile);
        }
        return backupPath;
    }

    private static void saveHistory(Path backupFile, String oldVersion, String newVersion, String oldFileName, String newFileName) {
        Path path = getHistoryPath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            SelfUpdateHistory history = new SelfUpdateHistory();
            history.oldVersion = oldVersion == null ? "" : oldVersion;
            history.newVersion = newVersion == null ? "" : newVersion;
            history.oldFileName = oldFileName == null ? "" : oldFileName;
            history.newFileName = newFileName == null ? "" : newFileName;
            Path dlcRoot = DlcPaths.dlcRoot();
            if (dlcRoot != null && backupFile.startsWith(dlcRoot)) {
                history.backupFile = dlcRoot.relativize(backupFile).toString();
            } else {
                history.backupFile = backupFile.getFileName().toString();
            }
            history.appliedAt = Instant.now().toString();
            JsonObject root = GSON.toJsonTree(history).getAsJsonObject();
            DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC);
        } catch (Exception ignored) {
        }
    }

    private static Path getHistoryPath() {
        Path dlcRoot = DlcPaths.dlcRoot();
        if (dlcRoot == null) {
            return null;
        }
        Path dir = DlcTmpPaths.resolve(dlcRoot, "selfupdate");
        return DlcDcFormatUtil.resolveConfigPath(dir, "self_update_history");
    }

    private static void clearPendingMarker() {
        try {
            Files.deleteIfExists(getMarkerPath());
        } catch (IOException ignored) {
        }
    }

    private static void clearSkipPromptMarker() {
        DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE);
    }

    private static SaveResult savePendingUpdate(Path pendingFile, Path targetFile, SelfUpdateResult result) {
        Path marker = getMarkerPath();
        if (marker == null) {
            return SaveResult.failed();
        }
        try {
            Files.createDirectories(marker.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("pendingFile", toStoredPath(pendingFile));
            obj.addProperty("targetFile", toStoredPath(targetFile));
            obj.addProperty("currentVersion", result.currentVersion == null ? "" : result.currentVersion);
            obj.addProperty("latestVersion", result.latestVersion == null ? "" : result.latestVersion);
            obj.addProperty("updatedAt", Instant.now().toString());
            DlcDcConfigIO.write(marker, obj, DlcDcConfigIO.Layout.GENERIC);
            return SaveResult.success();
        } catch (IOException ignored) {
            return SaveResult.failed();
        }
    }

    private static Path resolveStoredPath(String value) {
        String raw = value == null ? "" : value.trim();
        Path path = DlcPaths.parsePathLenient(raw);
        if (path == null) {
            return DlcPlatformServices.gameDir().toAbsolutePath().normalize();
        }
        if (path.isAbsolute()) {
            return path;
        }
        return DlcPlatformServices.gameDir().resolve(path).normalize();
    }

    private static String toStoredPath(Path path) {
        Path gameDir = DlcPlatformServices.gameDir().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        try {
            return gameDir.relativize(normalized).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return normalized.getFileName().toString();
        }
    }

    private static Path getMarkerPath() {
        Path configDir = DlcPlatformServices.configDir();
        if (configDir == null) {
            return null;
        }
        Path dir = configDir.resolve("dlc_manager");
        return DlcDcFormatUtil.resolveConfigPath(dir, "self_update");
    }

    private static String readString(JsonObject obj, String key) {
        if (obj == null || key == null || key.isBlank() || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            String value = obj.get(key).getAsString();
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void downloadToFile(String url, Path target) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(180000);
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.connect();
            if (connection.getResponseCode() >= 400) {
                throw new IOException("http " + connection.getResponseCode());
            }
            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static boolean isUpdateAvailable(String currentVersion, String latestVersion, DlcCurseForgeClient.CurseForgeFile file) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return true;
        }
        String current = currentVersion.trim();
        if (latestVersion != null && !latestVersion.isBlank()) {
            DlcVersionUtil.Channel channel = releaseTypeChannel(file == null ? 0 : file.releaseType);
            if (channel == null) {
                channel = versionChannel(latestVersion);
            }
            return shouldOfferUpdate(current, latestVersion, channel);
        }
        String fileName = file == null || file.fileName == null ? "" : file.fileName;
        String display = file == null || file.displayName == null ? "" : file.displayName;
        if (fileName.contains(current) || display.contains(current)) {
            return false;
        }
        return true;
    }

    static boolean isMajorVersionChanged(String currentVersion, String latestVersion) {
        Integer currentMajor = extractMajorVersion(currentVersion);
        Integer latestMajor = extractMajorVersion(latestVersion);
        return currentMajor != null && latestMajor != null && !currentMajor.equals(latestMajor);
    }

    private static Integer extractMajorVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Matcher matcher = MAJOR_VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static UpdateCandidate pickUpdateCandidate(String currentVersion, List<UpdateCandidate> candidates) {
        if (candidates == null) {
            return null;
        }
        UpdateCandidate best = null;
        for (UpdateCandidate candidate : candidates) {
            if (candidate == null || !shouldOfferUpdate(currentVersion, candidate.version, candidate.channel)) {
                continue;
            }
            if (best == null || isBetterCandidate(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isBetterCandidate(UpdateCandidate candidate, UpdateCandidate currentBest) {
        int comparison;
        try {
            comparison = DlcUpdateService.compareVersions(candidate.version, currentBest.version);
        } catch (Exception ignored) {
            comparison = candidate.version.compareToIgnoreCase(currentBest.version);
        }
        if (comparison != 0) {
            return comparison > 0;
        }
        return isAfterInstant(candidate.fileDate, currentBest.fileDate);
    }

    static boolean shouldOfferUpdate(String currentVersion, String candidateVersion, DlcVersionUtil.Channel candidateChannel) {
        if (candidateVersion == null || candidateVersion.isBlank()) {
            return false;
        }
        if (currentVersion == null || currentVersion.isBlank()) {
            return true;
        }
        String current = currentVersion.trim();
        String candidate = candidateVersion.trim();
        if (candidate.equalsIgnoreCase(current)) {
            return false;
        }
        if (!isSameMajorSeries(current, candidate)) {
            return false;
        }
        if (!isChannelAllowed(versionChannel(current), candidateChannel)) {
            return false;
        }
        try {
            return DlcUpdateService.compareVersions(current, candidate) < 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isSameMajorSeries(String left, String right) {
        Integer leftMajor = extractMajorVersion(left);
        Integer rightMajor = extractMajorVersion(right);
        return leftMajor != null && rightMajor != null && leftMajor.equals(rightMajor);
    }

    static DlcVersionUtil.Channel versionChannel(String version) {
        return DlcVersionUtil.channelOf(version);
    }

    static DlcVersionUtil.Channel releaseTypeChannel(int releaseType) {
        return switch (releaseType) {
            case 1 -> DlcVersionUtil.Channel.STABLE;
            case 2 -> DlcVersionUtil.Channel.BETA;
            case 3 -> DlcVersionUtil.Channel.ALPHA;
            default -> null;
        };
    }

    static DlcVersionUtil.Channel releaseChannelOf(String versionType) {
        return DlcVersionUtil.channelByName(versionType);
    }

    static boolean isChannelAllowed(DlcVersionUtil.Channel currentChannel, DlcVersionUtil.Channel candidateChannel) {
        DlcVersionUtil.Channel current = currentChannel == null ? DlcVersionUtil.Channel.STABLE : currentChannel;
        DlcVersionUtil.Channel candidate = candidateChannel == null ? DlcVersionUtil.Channel.STABLE : candidateChannel;
        return current.accepts(candidate);
    }

    private static boolean isAfterInstant(String candidate, String current) {
        Instant candidateTime = parseInstantOrNull(candidate);
        if (candidateTime == null) {
            return false;
        }
        Instant currentTime = parseInstantOrNull(current);
        return currentTime == null || candidateTime.isAfter(currentTime);
    }

    private static Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    static final class UpdateCandidate {
        final String version;
        final DlcVersionUtil.Channel channel;
        final String fileName;
        final String displayName;
        final String fileId;
        final String fileDate;
        String url;
        DlcCurseForgeClient.CurseForgeFile curseForgeFile;

        UpdateCandidate(String version, DlcVersionUtil.Channel channel, String fileName, String displayName, String fileId, String fileDate, String url) {
            this.version = version;
            this.channel = channel;
            this.fileName = fileName;
            this.displayName = displayName;
            this.fileId = fileId;
            this.fileDate = fileDate;
            this.url = url;
        }

        static UpdateCandidate fromCurseForge(DlcCurseForgeClient.CurseForgeFile file) {
            if (file == null) {
                return null;
            }
            String version = extractPublishedVersion(file.displayName, file.fileName);
            DlcVersionUtil.Channel channel = releaseTypeChannel(file.releaseType);
            if (channel == null) {
                channel = versionChannel(version);
            }
            UpdateCandidate candidate = new UpdateCandidate(version, channel, file.fileName, file.displayName, file.id, file.fileDate, file.url);
            candidate.curseForgeFile = file;
            return candidate;
        }

        static UpdateCandidate fromModrinth(DlcModrinthClient.ModrinthVersion version) {
            if (version == null) {
                return null;
            }
            String versionText = version.versionNumber;
            if (versionText == null || versionText.isBlank()) {
                versionText = extractPublishedVersion(null, version.fileName);
            }
            DlcVersionUtil.Channel channel = releaseChannelOf(version.versionType);
            if (channel == null) {
                channel = versionChannel(versionText);
            }
            return new UpdateCandidate(versionText, channel, version.fileName, versionText, version.versionId, version.datePublished == null ? null : version.datePublished.toString(), version.url);
        }
    }

    private static boolean isSameVersion(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return false;
        }
        try {
            return DlcUpdateService.compareVersions(left, right) == 0;
        } catch (Exception ignored) {
            return left.trim().equalsIgnoreCase(right.trim());
        }
    }

    private static String extractVersion(String displayName, String fileName) {
        return extractPublishedVersion(displayName, fileName);
    }

    static String extractPublishedVersion(String displayName, String fileName) {
        String candidate = null;
        if (fileName != null) {
            candidate = matchPublishedFileName(fileName);
        }
        if ((candidate == null || candidate.isBlank()) && displayName != null) {
            candidate = matchDisplayNameVersion(displayName);
        }
        return candidate;
    }

    private static String matchDisplayNameVersion(String displayName) {
        return matchFirstVersionByPattern(displayName, DISPLAY_NAME_VERSION_PATTERN);
    }

    private static String matchPublishedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        String[] tokens = name.split("-");
        for (int i = 0; i < tokens.length; i++) {
            if (!FILE_NAME_VERSION_PATTERN.matcher(tokens[i]).matches()) {
                continue;
            }
            if (i + 1 < tokens.length && FILE_NAME_PRERELEASE_PATTERN.matcher(tokens[i + 1]).matches()) {
                return tokens[i] + "-" + tokens[i + 1];
            }
            return tokens[i];
        }
        return null;
    }

    private static String matchFirstVersionByPattern(String source, Pattern pattern) {
        if (source == null || source.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static final class SelfUpdateResult {
        public boolean hasUpdate;
        public String currentVersion;
        public String latestVersion;
        public String requestedVersion;
        public boolean checkFailed;
        public boolean missingRequestedVersion;
        public String fileId;
        public String fileName;
        public String displayName;
        public String fileDate;
        public String downloadUrl;

        public static SelfUpdateResult none(String currentVersion) {
            SelfUpdateResult result = new SelfUpdateResult();
            result.hasUpdate = false;
            result.currentVersion = currentVersion == null ? "" : currentVersion;
            return result;
        }

        public static SelfUpdateResult failed(String currentVersion) {
            SelfUpdateResult result = none(currentVersion);
            result.checkFailed = true;
            return result;
        }
    }

    public static final class DownloadResult {
        public final boolean success;
        public final String reason;
        public final Path file;

        private DownloadResult(boolean success, String reason, Path file) {
            this.success = success;
            this.reason = reason;
            this.file = file;
        }

        public static DownloadResult success(Path file) {
            return new DownloadResult(true, "", file);
        }

        public static DownloadResult failed(String reason) {
            return new DownloadResult(false, reason, null);
        }
    }

    public static final class SelfUpdateHistory {
        public String oldVersion;
        public String newVersion;
        public String backupFile;
        public String oldFileName;
        public String newFileName;
        public String appliedAt;
    }

    private static final class SaveResult {
        private final boolean success;

        private SaveResult(boolean success) {
            this.success = success;
        }

        private static SaveResult success() {
            return new SaveResult(true);
        }

        private static SaveResult failed() {
            return new SaveResult(false);
        }
    }
}
