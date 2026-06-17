package com.cozooo.dlc_manager.client.selfupdate;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.CurseForgeClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateSkipStore;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.cozooo.dlc_manager.platform.PlatformServices;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcSelfUpdateService {
    public static final String MOD_ID = "dlc_manager";
    public static final String PROJECT_ID = "1424546";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern VERSION_PATTERN_3 = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static final Pattern VERSION_PATTERN_2 = Pattern.compile("(\\d+\\.\\d+)");

    private DlcSelfUpdateService() {
    }

    public static SelfUpdateResult checkForUpdate() {
        return checkForUpdate(null);
    }

    public static SelfUpdateResult checkForUpdate(String targetVersion) {
        String loader = CrossPlatformRuntime.loaderName();
        String mcVersion = CrossPlatformRuntime.mcVersion();
        String currentVersion = CrossPlatformRuntime.modVersion(MOD_ID);
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
        try {
            CurseForgeClient.CurseForgeFile file = requestedVersion == null
                ? CurseForgeClient.fetchLatestFile(PROJECT_ID, mcVersion, loader)
                : CurseForgeClient.fetchFileByIdOrVersion(PROJECT_ID, requestedVersion, mcVersion, loader);
            if (requestedVersion != null && file == null) {
                SelfUpdateResult result = SelfUpdateResult.none(currentVersion);
                result.requestedVersion = requestedVersion;
                result.missingRequestedVersion = true;
                return result;
            }
            if (file == null || file.url == null || file.url.isBlank()) {
                return SelfUpdateResult.none(currentVersion);
            }
            String latestVersion = extractVersion(file.displayName, file.fileName);
            boolean hasUpdate = requestedVersion == null
                ? isUpdateAvailable(currentVersion, latestVersion, file)
                : !isSameVersion(currentVersion, latestVersion == null || latestVersion.isBlank() ? requestedVersion : latestVersion);
            SelfUpdateResult result = new SelfUpdateResult();
            result.currentVersion = currentVersion == null ? "" : currentVersion;
            result.latestVersion = latestVersion == null || latestVersion.isBlank() ? (requestedVersion == null ? "" : requestedVersion) : latestVersion;
            result.downloadUrl = file.url;
            result.fileName = file.fileName;
            result.fileId = file.id;
            result.displayName = file.displayName;
            result.fileDate = file.fileDate;
            result.hasUpdate = hasUpdate;
            result.requestedVersion = requestedVersion;
            return result;
        } catch (Exception ignored) {
            return SelfUpdateResult.none(currentVersion);
        }
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
        Path modFilePath = CrossPlatformRuntime.modFilePath(MOD_ID);
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
        Path backup = Path.of(history.backupFile);
        if (!Files.exists(backup)) {
            return false;
        }
        Path current = CrossPlatformRuntime.modFilePath(MOD_ID);
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
                if (dlcRoot != null && !Path.of(history.backupFile).isAbsolute()) {
                    history.backupFile = dlcRoot.resolve(history.backupFile).toString();
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
        Path path = Path.of(raw);
        if (path.isAbsolute()) {
            return path;
        }
        return CrossPlatformRuntime.gameDir().resolve(path).normalize();
    }

    private static String toStoredPath(Path path) {
        Path gameDir = CrossPlatformRuntime.gameDir().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        try {
            return gameDir.relativize(normalized).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return normalized.getFileName().toString();
        }
    }

    private static Path getMarkerPath() {
        Path configDir = CrossPlatformRuntime.configDir();
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

    private static boolean isUpdateAvailable(String currentVersion, String latestVersion, CurseForgeClient.CurseForgeFile file) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return true;
        }
        String current = currentVersion.trim();
        if (latestVersion != null && !latestVersion.isBlank()) {
            if (latestVersion.equalsIgnoreCase(current)) {
                return false;
            }
            try {
                return DlcUpdateService.compareVersions(current, latestVersion) < 0;
            } catch (Exception ignored) {
            }
        }
        String fileName = file.fileName == null ? "" : file.fileName;
        String display = file.displayName == null ? "" : file.displayName;
        if (fileName.contains(current) || display.contains(current)) {
            return false;
        }
        return true;
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
        String candidate = null;
        if (fileName != null) {
            candidate = matchVersion(fileName);
        }
        if ((candidate == null || candidate.isBlank()) && displayName != null) {
            candidate = matchVersion(displayName);
        }
        return candidate;
    }

    private static String matchVersion(String source) {
        String last = matchVersionByPattern(source, VERSION_PATTERN_3);
        if (last != null && !last.isBlank()) {
            return last;
        }
        return matchVersionByPattern(source, VERSION_PATTERN_2);
    }

    private static String matchVersionByPattern(String source, Pattern pattern) {
        if (source == null || source.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(source);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    public static final class SelfUpdateResult {
        public boolean hasUpdate;
        public String currentVersion;
        public String latestVersion;
        public String requestedVersion;
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
