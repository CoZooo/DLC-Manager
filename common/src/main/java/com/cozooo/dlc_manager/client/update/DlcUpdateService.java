package com.cozooo.dlc_manager.client.update;
import com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.remote.CurseForgeClient;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker;
import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStateStore;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.SelfUpdateResult;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.core.Json5Util;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DlcUpdateService {
    private static final Gson UPDATE_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PLATFORM_CLIENT = "client";
    private static final String PLATFORM_SERVER = "server";
    private static final String PLATFORM_UNIVERSAL = "universal";
    private static final ThreadLocal<UpdateContext> CURRENT_UPDATE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<ApplyProgressListener> CURRENT_PROGRESS_LISTENER = new ThreadLocal<>();

    private DlcUpdateService() {
    }

    public static UpdateCheckResult checkForUpdates() {
        if (!DlcPathConfig.isUpdateManagerOn()) {
            Path dlcRoot = getDlcRoot();
            return new UpdateCheckResult(DlcUpdateVersion.load(dlcRoot), List.of());
        }
        Path dlcRoot = getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        List<DlcUpdatePackage> applicable = resolveUpdateChain(version, packages, false);
        return new UpdateCheckResult(version, applicable);
    }

    public static UpdateCheckResult checkForUpdates(boolean ignoreRollback) {
        if (!DlcPathConfig.isUpdateManagerOn()) {
            Path dlcRoot = getDlcRoot();
            return new UpdateCheckResult(DlcUpdateVersion.load(dlcRoot), List.of());
        }
        Path dlcRoot = getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        List<DlcUpdatePackage> applicable = resolveUpdateChain(version, packages, ignoreRollback);
        return new UpdateCheckResult(version, applicable);
    }

    public static DlcUpdatePackage findUpdatePackage(String dlcOld, String dlcNew) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return null;
        }
        DlcUpdateVersion current = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        if (packages.isEmpty()) {
            return null;
        }
        String base = safeVersion(current.getBase());
        String targetOld = safeVersion(dlcOld);
        String targetNew = optionalVersion(dlcNew);
        if (targetNew == null) {
            targetNew = targetOld;
        }
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (!hasVersionTransition(info)) {
                continue;
            }
            if (!base.equals(sourceBaseVersion(info))) {
                continue;
            }
            if (targetOld.equals(sourceDlcVersion(info)) && targetNew.equals(targetDlcVersion(info))) {
                return pkg;
            }
        }
        return null;
    }

    public static List<DlcUpdatePackage> resolveUpdateChainTo(String targetDlcNew, boolean ignoreRollback) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return List.of();
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        String currentBase = version == null ? null : version.getBase();
        return resolveUpdateChainTo(version, packages, currentBase, targetDlcNew, ignoreRollback);
    }

    public static List<DlcUpdatePackage> resolveUpdateChainTo(String targetDlcOld, String targetDlcNew, boolean ignoreRollback) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return List.of();
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        String currentBase = version == null ? null : version.getBase();
        return resolveUpdateChainTo(version, packages, currentBase, targetDlcOld, currentBase, targetDlcNew, ignoreRollback);
    }

    public static List<DlcUpdatePackage> resolveUpdateChainTo(String targetBaseOld,
                                                              String targetDlcOld,
                                                              String targetBaseNew,
                                                              String targetDlcNew,
                                                              boolean ignoreRollback) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return List.of();
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        return resolveUpdateChainTo(version, packages, targetBaseOld, targetDlcOld, targetBaseNew, targetDlcNew, ignoreRollback);
    }

    public static UpdateApplyResult applyUpdates(List<DlcUpdatePackage> packages, boolean forceMissing) {
        return applyUpdates(packages, forceMissing, null);
    }

    public static UpdateApplyResult applyUpdates(List<DlcUpdatePackage> packages, boolean forceMissing, ApplyProgressListener progressListener) {
        CURRENT_PROGRESS_LISTENER.set(progressListener);
        try {
            return applyUpdatesInternal(packages, forceMissing);
        } finally {
            CURRENT_PROGRESS_LISTENER.remove();
        }
    }

    private static UpdateApplyResult applyUpdatesInternal(List<DlcUpdatePackage> packages, boolean forceMissing) {
        if (packages == null || packages.isEmpty()) {
            return UpdateApplyResult.empty();
        }
        notifyApplyProgress("Applying update...", -1f);
        Path dlcRoot = getDlcRoot();
        DlcUpdateVersion current = DlcUpdateVersion.load(dlcRoot);
        boolean addedRequired = false;
        boolean addedRemote = false;
        boolean helperScheduled = false;
        boolean dlcManagerUpdated = false;
        boolean requiresDlcManagerUpdate = packages.stream().anyMatch(pkg ->
            pkg != null && pkg.getManifest() != null && pkg.getManifest().isUpdateDLCM());
        LinkedHashSet<String> requestedDlcManagerVersions = collectRequestedDlcManagerVersions(packages);
        if (requestedDlcManagerVersions.size() > 1) {
            return UpdateApplyResult.failed("Conflicting UpdateDLCM versions in update chain");
        }
        String requiredDlcManagerVersion = requestedDlcManagerVersions.stream().findFirst().orElse(null);
        if (requiresDlcManagerUpdate) {
            String progressMessage = requiredDlcManagerVersion == null
                ? "Downloading DLC Manager update..."
                : "Downloading DLC Manager update " + requiredDlcManagerVersion + "...";
            notifyApplyProgress(progressMessage, -1f);
            UpdateApplyResult managerResult = applyRequiredDlcManagerUpdate(requiredDlcManagerVersion);
            if (!managerResult.success) {
                return managerResult;
            }
            helperScheduled = helperScheduled || managerResult.helperScheduled;
            dlcManagerUpdated = managerResult.dlcManagerUpdated;
        }
        for (DlcUpdatePackage pkg : packages) {
            UpdateApplyResult result = applyUpdate(pkg, current, forceMissing);
            if (!result.success) {
                return result;
            }
            addedRequired = addedRequired || result.addedRequired;
            addedRemote = addedRemote || result.addedRemote;
            helperScheduled = helperScheduled || result.helperScheduled;
            current = DlcUpdateVersion.load(dlcRoot);
        }
        clearUpdatePromptSkipMarker();
        return UpdateApplyResult.success("OK", addedRequired, addedRemote, helperScheduled, dlcManagerUpdated);
    }

    private static UpdateApplyResult applyRequiredDlcManagerUpdate(String targetVersion) {
        SelfUpdateResult check = DlcSelfUpdateService.checkForUpdate(targetVersion);
        if (check == null) {
            return UpdateApplyResult.failed("Failed to check DLC Manager update");
        }
        if (check.missingRequestedVersion) {
            return UpdateApplyResult.failed("Failed to find DLC Manager version: " + targetVersion);
        }
        if (!check.hasUpdate) {
            return UpdateApplyResult.success("OK", false, false, false, false);
        }
        DownloadResult downloadResult = DlcSelfUpdateService.downloadUpdate(check);
        if (downloadResult == null || !downloadResult.success) {
            String message = downloadResult == null ? "" : downloadResult.reason;
            if (message == null || message.isBlank()) {
                message = "Failed to update DLC Manager";
            } else {
                message = "Failed to update DLC Manager: " + message;
            }
            return UpdateApplyResult.failed(message);
        }
        return UpdateApplyResult.success("OK", false, false, false, true);
    }

    private static LinkedHashSet<String> collectRequestedDlcManagerVersions(List<DlcUpdatePackage> packages) {
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        if (packages == null) {
            return versions;
        }
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || !pkg.getManifest().isUpdateDLCM()) {
                continue;
            }
            String version = pkg.getManifest().getUpdateDLCMVersion();
            if (version != null && !version.isBlank()) {
                versions.add(version.trim());
            }
        }
        return versions;
    }

    public static UpdateApplyResult applyUpdate(DlcUpdatePackage pkg, DlcUpdateVersion current, boolean forceMissing) {
        if (pkg == null || pkg.getManifest() == null) {
            return UpdateApplyResult.failed("Missing update manifest");
        }
        DlcUpdateManifest manifest = pkg.getManifest();
        DlcUpdateManifest.VersionInfo info = manifest.getVersion();
        if (info == null) {
            return UpdateApplyResult.failed("Missing version info");
        }
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return UpdateApplyResult.failed("Missing DLC root");
        }
        if (!matchesVersion(current, info)) {
            return UpdateApplyResult.failed("Version mismatch");
        }

        List<String> missingModify = findMissingModifyTargets(manifest, DlcPaths.gameDir());
        if (!missingModify.isEmpty() && !forceMissing) {
            return UpdateApplyResult.missing(missingModify);
        }

        Path staging = pkg.extractTo(dlcRoot);
        if (staging == null) {
            return UpdateApplyResult.failed("Failed to extract update package");
        }

        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.setDlcOld(sourceDlcVersion(info));
        meta.setDlcNew(targetDlcVersion(info));
        meta.setBaseOld(safeVersion(current.getBase()));
        String baseNew = optionalVersion(info.getBaseNew());
        if (baseNew != null) {
            meta.setBaseNew(baseNew);
        }
        meta.setTimestamp(Long.toString(System.currentTimeMillis()));
        meta.setHidden(manifest.isHidden());
        meta.setChangelog(manifest.getChangelog());
        String nextBase = baseNew != null ? safeVersion(baseNew) : safeVersion(current.getBase());
        String nextDlc = targetDlcVersion(info);
        Path backupRoot = resolveMetaRootByPair(dlcRoot, nextBase, nextDlc);
        Path metaPath = resolveMetaPath(backupRoot);

        UpdateContext context = new UpdateContext(dlcRoot, staging, backupRoot, meta);

        CURRENT_UPDATE_CONTEXT.set(context);
        try {
            applyDeleteOps(manifest.getFileOps(), context);
            applyMoveOps(manifest.getFileOps(), context);
            applyAddOps(manifest.getFileOps(), context);
            applyModifyOps(manifest.getFileOps(), context, forceMissing);
            applyUpdateOps(manifest.getUpdateOps(), context);
            if (!context.failureMessages.isEmpty()) {
                throw new IllegalStateException(String.join("\n", context.failureMessages));
            }
            meta.save(metaPath);

            DlcUpdateVersion updated = new DlcUpdateVersion();
            updated.setBase(nextBase);
            updated.setDlc(nextDlc);
            updated.setRollbackFrom(null);
            updated.setChangelogShown(sourceBaseVersion(info) + "+" + sourceDlcVersion(info));
            updated.save(dlcRoot);

            if (context.addedRemote) {
                DlcRemoteMarker.clearPartMarker(dlcRoot);
            }
            refreshPresetRuntimeState();

            pkg.cleanupStaging();
            return UpdateApplyResult.success("OK", context.addedRequired, context.addedRemote, context.helperScheduled, false);
        } catch (Exception ex) {
            rollbackUpdate(context);
            pkg.cleanupStaging();
            String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage().trim();
            if (message.isBlank() && !context.failureMessages.isEmpty()) {
                message = String.join("\n", context.failureMessages);
            }
            if (message.isBlank()) {
                message = "Update failed";
            }
            return UpdateApplyResult.failed(message);
        } finally {
            CURRENT_UPDATE_CONTEXT.remove();
        }
    }

    public static UpdateRollbackResult rollbackTo(String targetVersion) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return UpdateRollbackResult.failed("Missing DLC root");
        }
        DlcUpdateVersion current = DlcUpdateVersion.load(dlcRoot);
        if (targetVersion == null || targetVersion.isBlank()) {
            return UpdateRollbackResult.failed("Missing target version");
        }
        String currentBase = safeVersion(current.getBase());
        String currentDlc = safeVersion(current.getDlc());
        VersionState targetState = parseVersionState(targetVersion, currentBase);
        if (targetState == null) {
            return UpdateRollbackResult.failed("Missing target version");
        }
        if (stateMatchesTarget(currentBase, currentDlc, targetState)) {
            return UpdateRollbackResult.success(current.getDlc());
        }

        List<DlcUpdateMeta> chain = loadRollbackChain(dlcRoot, currentBase, currentDlc);
        if (chain.isEmpty()) {
            return UpdateRollbackResult.failed("No rollback data");
        }
        boolean targetFound = false;
        for (DlcUpdateMeta meta : chain) {
            if (meta != null && stateMatchesTarget(sourceBaseVersion(meta), sourceDlcVersion(meta), targetState)) {
                targetFound = true;
                break;
            }
        }
        if (!targetFound) {
            return UpdateRollbackResult.failed("Target version not found");
        }
        String beforeRollback = currentBase + "+" + currentDlc;
        String working = currentDlc;
        String workingBase = currentBase;
        for (DlcUpdateMeta meta : chain) {
            if (meta == null) {
                break;
            }
            String metaTargetDlc = targetDlcVersion(meta);
            String metaTargetBase = targetBaseVersion(meta);
            if (!working.equals(metaTargetDlc) || !safeVersion(workingBase).equals(metaTargetBase)) {
                continue;
            }
            Path backupRoot = resolveMetaRootByPair(dlcRoot, metaTargetBase, metaTargetDlc);
            UpdateContext context = new UpdateContext(dlcRoot, null, backupRoot, meta);
            rollbackUpdate(context);
            working = sourceDlcVersion(meta);
            workingBase = sourceBaseVersion(meta);
            if (stateMatchesTarget(workingBase, working, targetState)) {
                break;
            }
        }
        DlcUpdateVersion updated = new DlcUpdateVersion();
        updated.setBase(workingBase);
        updated.setDlc(working);
        updated.setRollbackFrom(beforeRollback);
        updated.save(dlcRoot);
        if (!stateMatchesTarget(workingBase, working, targetState)) {
            return UpdateRollbackResult.failed("Rollback incomplete");
        }
        return UpdateRollbackResult.success(working);
    }

    public static List<DlcUpdateMeta> listUpdateHistory() {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return List.of();
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        return loadRollbackChain(dlcRoot, version.getBase(), version.getDlc());
    }

    public static List<DlcUpdateMeta> listUpdateHistoryFrom(String startVersion) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null || startVersion == null || startVersion.isBlank()) {
            return List.of();
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        VersionState start = parseVersionState(startVersion, version.getBase());
        if (start == null) {
            return List.of();
        }
        List<DlcUpdateMeta> chain = loadRollbackChain(dlcRoot, start.base, start.dlc);
        if (!chain.isEmpty()) {
            return chain;
        }
        return loadRollbackChain(dlcRoot, null, start.dlc);
    }

    public static DlcUpdateMeta loadMetaForVersion(String dlcVersion) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null || dlcVersion == null || dlcVersion.isBlank()) {
            return null;
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        String base = version == null ? null : version.getBase();
        DlcUpdateMeta meta = loadMetaForVersion(dlcRoot, base, dlcVersion);
        if (meta != null) {
            return meta;
        }
        return loadMetaForVersion(dlcRoot, null, dlcVersion);
    }

    public static DlcUpdateMeta loadMetaForVersion(String baseVersion, String dlcVersion) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return null;
        }
        return loadMetaForVersion(dlcRoot, baseVersion, dlcVersion);
    }

    public static String buildChangelogText(List<DlcUpdatePackage> packages, String lang) {
        if (packages == null || packages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            String text = pkg.getManifest().resolveChangelog(lang);
            if (text == null || text.isBlank()) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            if (info != null) {
                builder.append(formatCompactTransition(
                    sourceBaseVersion(info),
                    sourceDlcVersion(info),
                    targetBaseVersion(info),
                    targetDlcVersion(info)
                )).append("\n");
            }
            builder.append(text.trim());
        }
        return builder.toString();
    }

    public static Path getUpdateDir() {
        Path dlcRoot = getDlcRoot();
        return dlcRoot == null ? null : dlcRoot.resolve("update");
    }

    public static void ensureUpdateDir() {
        if (!DlcPathConfig.isUpdateManagerOn()) {
            return;
        }
        Path updateDir = getUpdateDir();
        if (updateDir == null) {
            return;
        }
        try {
            Files.createDirectories(updateDir);
        } catch (IOException ignored) {
        }
    }

    public static boolean isImportPackageValid(Path zipPath) {
        if (zipPath == null) {
            return false;
        }
        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zipPath);
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return false;
        }
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return false;
        }
        DlcUpdateVersion current = DlcUpdateVersion.load(dlcRoot);
        DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
        return hasVersionTransition(info)
            && isPlatformCompatible(info)
            && safeVersion(current.getBase()).equals(sourceBaseVersion(info))
            && safeVersion(current.getDlc()).equals(sourceDlcVersion(info));
    }

    public static boolean importUpdatePackage(Path zipPath) {
        if (zipPath == null || !Files.exists(zipPath)) {
            return false;
        }
        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zipPath);
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return false;
        }
        Path updateDir = getUpdateDir();
        if (updateDir == null) {
            return false;
        }
        try {
            Files.createDirectories(updateDir);
            Path target = updateDir.resolve(zipPath.getFileName().toString());
            if (!target.normalize().equals(zipPath.normalize())) {
                if (!copyWithHelperFallback(zipPath, target, true)) {
                    return false;
                }
            }
            removeDuplicatePackages(updateDir, target, pkg.getManifest().getVersion());
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void removeSkipMarker(List<Path> addedRequiredFiles) {
        if (addedRequiredFiles == null || addedRequiredFiles.isEmpty()) {
            return;
        }
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return;
        }
        Path skip = dlcRoot.resolve("required").resolve("SKIP");
        deleteWithHelperFallback(skip);
    }

    public static void skipPromptForUpdate(UpdateCheckResult result) {
        UpdatePromptSpan span = resolveUpdatePromptSpan(result);
        if (span == null) {
            return;
        }
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("fromBase", span.fromBase);
            obj.addProperty("fromDlc", span.fromDlc);
            obj.addProperty("toBase", span.toBase);
            obj.addProperty("toDlc", span.toDlc);
            obj.addProperty("skippedAt", Instant.now().toString());
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_HOT_UPDATE, obj);
        } catch (Exception ignored) {
        }
    }

    public static boolean isPromptSkippedForUpdate(UpdateCheckResult result) {
        UpdatePromptSpan span = resolveUpdatePromptSpan(result);
        if (span == null) {
            return false;
        }
        JsonObject obj = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_HOT_UPDATE);
        if (obj == null) {
            return false;
        }
        String fromBase = safeVersion(readJsonString(obj, "fromBase"));
        String fromDlc = safeVersion(readJsonString(obj, "fromDlc"));
        String toBase = safeVersion(readJsonString(obj, "toBase"));
        String toDlc = safeVersion(readJsonString(obj, "toDlc"));
        return span.fromBase.equals(fromBase)
            && span.fromDlc.equals(fromDlc)
            && span.toBase.equals(toBase)
            && span.toDlc.equals(toDlc);
    }

    private static UpdatePromptSpan resolveUpdatePromptSpan(UpdateCheckResult result) {
        if (result == null || !result.hasUpdates() || result.getCurrent() == null) {
            return null;
        }
        List<DlcUpdatePackage> updates = result.getUpdates();
        if (updates == null || updates.isEmpty()) {
            return null;
        }
        DlcUpdateManifest.VersionInfo tail = null;
        for (int i = updates.size() - 1; i >= 0; i--) {
            DlcUpdatePackage pkg = updates.get(i);
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            tail = pkg.getManifest().getVersion();
            break;
        }
        if (tail == null) {
            return null;
        }
        UpdatePromptSpan span = new UpdatePromptSpan();
        span.fromBase = safeVersion(result.getCurrent().getBase());
        span.fromDlc = safeVersion(result.getCurrent().getDlc());
        span.toBase = targetBaseVersion(tail);
        span.toDlc = targetDlcVersion(tail);
        return span;
    }

    private static void clearUpdatePromptSkipMarker() {
        DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_HOT_UPDATE);
    }

    private static List<DlcUpdatePackage> scanUpdatePackages(Path dlcRoot) {
        if (dlcRoot == null) {
            return List.of();
        }
        Path updateDir = dlcRoot.resolve("update");
        if (!Files.isDirectory(updateDir)) {
            return List.of();
        }
        List<DlcUpdatePackage> packages = new ArrayList<>();
        try (var stream = Files.list(updateDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName() != null && path.getFileName().toString().toLowerCase().endsWith(".zip"))
                .forEach(path -> {
                    DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(path);
                    if (pkg != null && isPackageCompatible(pkg)) {
                        packages.add(pkg);
                    }
                });
        } catch (IOException ignored) {
        }
        return dedupeUpdatePackages(packages);
    }

    private static List<DlcUpdatePackage> dedupeUpdatePackages(List<DlcUpdatePackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return List.of();
        }
        Map<String, DlcUpdatePackage> best = new java.util.LinkedHashMap<>();
        Map<String, Long> times = new java.util.HashMap<>();
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            String key = versionPlatform(info) + "|" + sourceBaseVersion(info) + "|" + targetBaseVersion(info) + "|" + sourceDlcVersion(info) + "|" + targetDlcVersion(info);
            long modified = getLastModifiedMillis(pkg.getArchivePath());
            Long existing = times.get(key);
            if (existing == null || modified >= existing) {
                best.put(key, pkg);
                times.put(key, modified);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static long getLastModifiedMillis(Path path) {
        if (path == null) {
            return 0L;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static void removeDuplicatePackages(Path updateDir, Path keepPath, DlcUpdateManifest.VersionInfo versionInfo) {
        if (updateDir == null || keepPath == null || versionInfo == null) {
            return;
        }
        try (var stream = Files.walk(updateDir)) {
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().toLowerCase().endsWith(".zip"))
                .forEach(path -> {
                    if (path.equals(keepPath)) {
                        return;
                    }
                    DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(path);
                    if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                        return;
                    }
                    if (sameVersion(versionInfo, pkg.getManifest().getVersion())) {
                        deleteWithHelperFallback(path);
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private static boolean sameVersion(DlcUpdateManifest.VersionInfo left, DlcUpdateManifest.VersionInfo right) {
        if (left == null || right == null) {
            return false;
        }
        return sourceBaseVersion(left).equals(sourceBaseVersion(right))
            && targetBaseVersion(left).equals(targetBaseVersion(right))
            && sourceDlcVersion(left).equals(sourceDlcVersion(right))
            && targetDlcVersion(left).equals(targetDlcVersion(right))
            && versionPlatform(left).equals(versionPlatform(right));
    }

    private static List<DlcUpdatePackage> resolveUpdateChain(DlcUpdateVersion version, List<DlcUpdatePackage> packages,
        boolean ignoreRollback) {
        if (version == null || packages == null || packages.isEmpty()) {
            return List.of();
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom().isBlank()) {
            boolean hasNewer = packages.stream().anyMatch(pkg -> {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                return info != null && rollbackMarkerMatchesSource(
                    version.getRollbackFrom(),
                    sourceBaseVersion(info),
                    sourceDlcVersion(info)
                );
            });
            if (!hasNewer) {
                return List.of();
            }
        }
        List<DlcUpdatePackage> remaining = new ArrayList<>(packages);
        List<DlcUpdatePackage> chain = new ArrayList<>();
        String currentBase = safeVersion(version.getBase());
        String currentDlc = safeVersion(version.getDlc());
        while (true) {
            List<DlcUpdatePackage> candidates = new ArrayList<>();
            for (DlcUpdatePackage pkg : remaining) {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                if (info == null) {
                    continue;
                }
                if (!hasVersionTransition(info)) {
                    continue;
                }
                if (currentBase.equals(sourceBaseVersion(info)) && currentDlc.equals(sourceDlcVersion(info))) {
                    candidates.add(pkg);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            candidates.sort(Comparator
                .comparing((DlcUpdatePackage pkg) -> targetBaseVersion(pkg.getManifest().getVersion()), DlcUpdateService::compareVersion)
                .thenComparing(pkg -> targetDlcVersion(pkg.getManifest().getVersion()), DlcUpdateService::compareVersion));
            DlcUpdatePackage chosen = candidates.get(0);
            chain.add(chosen);
            remaining.remove(chosen);
            DlcUpdateManifest.VersionInfo info = chosen.getManifest().getVersion();
            currentBase = targetBaseVersion(info);
            currentDlc = targetDlcVersion(info);
        }
        return chain;
    }

    private static List<DlcUpdatePackage> resolveUpdateChainTo(DlcUpdateVersion version, List<DlcUpdatePackage> packages,
        String targetBase, String targetDlc, boolean ignoreRollback) {
        if (version == null || packages == null || packages.isEmpty()) {
            return List.of();
        }
        String targetBaseVersion = safeVersion(targetBase);
        String targetDlcVersion = safeVersion(targetDlc);
        if (targetBaseVersion.isEmpty() || targetDlcVersion.isEmpty()) {
            return List.of();
        }
        String startBase = safeVersion(version.getBase());
        String startDlc = safeVersion(version.getDlc());
        if (startBase.equals(targetBaseVersion) && startDlc.equals(targetDlcVersion)) {
            return List.of();
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom().isBlank()) {
            boolean hasNewer = packages.stream().anyMatch(pkg -> {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                return info != null && rollbackMarkerMatchesSource(
                    version.getRollbackFrom(),
                    sourceBaseVersion(info),
                    sourceDlcVersion(info)
                );
            });
            if (!hasNewer) {
                return List.of();
            }
        }
        List<DlcUpdatePackage> filtered = new ArrayList<>();
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (!hasVersionTransition(info)) {
                continue;
            }
            filtered.add(pkg);
        }
        if (filtered.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, List<DlcUpdatePackage>> byOld = new java.util.HashMap<>();
        for (DlcUpdatePackage pkg : filtered) {
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            String oldState = stateKey(sourceBaseVersion(info), sourceDlcVersion(info));
            byOld.computeIfAbsent(oldState, k -> new ArrayList<>()).add(pkg);
        }
        java.util.ArrayDeque<PathNode> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        queue.add(new PathNode(startBase, startDlc, new ArrayList<>()));
        visited.add(stateKey(startBase, startDlc));
        while (!queue.isEmpty()) {
            PathNode node = queue.poll();
            if (node.base.equals(targetBaseVersion) && node.dlc.equals(targetDlcVersion)) {
                return node.path;
            }
            List<DlcUpdatePackage> nextPkgs = byOld.get(stateKey(node.base, node.dlc));
            if (nextPkgs == null || nextPkgs.isEmpty()) {
                continue;
            }
            for (DlcUpdatePackage pkg : nextPkgs) {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                String nextBase = targetBaseVersion(info);
                String nextDlc = targetDlcVersion(info);
                String nextState = stateKey(nextBase, nextDlc);
                if (visited.contains(nextState)) {
                    continue;
                }
                List<DlcUpdatePackage> nextPath = new ArrayList<>(node.path);
                nextPath.add(pkg);
                visited.add(nextState);
                queue.add(new PathNode(nextBase, nextDlc, nextPath));
            }
        }
        return List.of();
    }

    private static List<DlcUpdatePackage> resolveUpdateChainTo(DlcUpdateVersion version, List<DlcUpdatePackage> packages,
        String targetBaseOld, String targetDlcOld, String targetBaseNew, String targetDlcNew, boolean ignoreRollback) {
        if (version == null || packages == null || packages.isEmpty()) {
            return List.of();
        }
        String oldBase = safeVersion(targetBaseOld);
        String targetOld = safeVersion(targetDlcOld);
        String newBase = optionalVersion(targetBaseNew);
        if (newBase == null) {
            newBase = oldBase;
        } else {
            newBase = safeVersion(newBase);
        }
        String targetNew = optionalVersion(targetDlcNew);
        if (targetNew == null) {
            targetNew = targetOld;
        } else {
            targetNew = safeVersion(targetNew);
        }
        if (oldBase.isEmpty() || targetOld.isEmpty()) {
            return List.of();
        }
        DlcUpdatePackage targetPkg = null;
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (!hasVersionTransition(info)) {
                continue;
            }
            if (!oldBase.equals(sourceBaseVersion(info))) {
                continue;
            }
            if (targetOld.equals(sourceDlcVersion(info))
                && newBase.equals(targetBaseVersion(info))
                && targetNew.equals(targetDlcVersion(info))) {
                targetPkg = pkg;
                break;
            }
        }
        if (targetPkg == null) {
            return List.of();
        }
        String currentBase = safeVersion(version.getBase());
        String currentDlc = safeVersion(version.getDlc());
        if (currentDlc.equals(targetOld)
            && currentBase.equals(oldBase)) {
            return List.of(targetPkg);
        }
        List<DlcUpdatePackage> prefix = resolveUpdateChainTo(version, packages, oldBase, targetOld, ignoreRollback);
        if (prefix.isEmpty()) {
            return List.of();
        }
        List<DlcUpdatePackage> chain = new ArrayList<>(prefix);
        chain.add(targetPkg);
        return chain;
    }

    private static final class PathNode {
        private final String base;
        private final String dlc;
        private final List<DlcUpdatePackage> path;

        private PathNode(String base, String dlc, List<DlcUpdatePackage> path) {
            this.base = base;
            this.dlc = dlc;
            this.path = path;
        }
    }

    private static boolean matchesVersion(DlcUpdateVersion current, DlcUpdateManifest.VersionInfo info) {
        if (current == null || info == null) {
            return false;
        }
        if (!hasVersionTransition(info)) {
            return false;
        }
        if (!isPlatformCompatible(info)) {
            return false;
        }
        String base = safeVersion(current.getBase());
        String dlc = safeVersion(current.getDlc());
        return base.equals(sourceBaseVersion(info))
            && dlc.equals(sourceDlcVersion(info));
    }

    private static List<String> findMissingModifyTargets(DlcUpdateManifest manifest, Path gameDir) {
        if (manifest == null || manifest.getFileOps() == null) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (DlcUpdateManifest.ModifyEntry entry : manifest.getFileOps().getModify()) {
            if (entry == null) {
                continue;
            }
            Path target = resolveGamePath(gameDir, entry.getTargetPath());
            if (target == null || !Files.exists(target)) {
                missing.add(entry.getTargetPath());
            }
        }
        return missing;
    }

    private static void applyDeleteOps(DlcUpdateManifest.FileOps ops, UpdateContext context) {
        if (ops == null) {
            return;
        }
        for (String raw : ops.getDel()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Path target = resolveGamePath(DlcPaths.gameDir(), raw);
            if (target == null) {
                continue;
            }
            if (Files.exists(target)) {
                backupPath(raw, target, context);
                handleSpecialDelete(target, raw, context);
                deleteRecursive(target);
            }
        }
    }

    private static void applyAddOps(DlcUpdateManifest.FileOps ops, UpdateContext context) {
        if (ops == null) {
            return;
        }
        List<Path> addedRequiredFiles = new ArrayList<>();
        boolean hasRemoteAdd = false;
        boolean hasRequiredAdd = false;
        for (String key : ops.getAdd().keySet()) {
            if (key == null) {
                continue;
            }
            String normalized = key.replace('\\', '/');
            if (normalized.startsWith("remote/")) {
                hasRemoteAdd = true;
            }
            if (normalized.startsWith("required/")) {
                hasRequiredAdd = true;
            }
        }
        for (Map.Entry<String, DlcUpdateManifest.AddEntry> entry : ops.getAdd().entrySet()) {
            String sourceKey = entry.getKey();
            DlcUpdateManifest.AddEntry add = entry.getValue();
            if (add == null || sourceKey == null || sourceKey.isBlank()) {
                continue;
            }
            Path source = resolveStagingPath(context.stagingRoot, sourceKey);
            if (source == null || !Files.exists(source)) {
                continue;
            }
            Path target = resolveGamePath(DlcPaths.gameDir(), add.getAppliedTarget());
            if (target == null) {
                continue;
            }
            boolean sourceIsDir = add.isSourceDir() || Files.isDirectory(source);
            boolean targetIsDir = add.getAppliedTarget() != null && add.getAppliedTarget().endsWith("/");
            if (sourceIsDir) {
                applyAddDirectory(source, target, add.getOverrideMode(), sourceKey, targetIsDir, context);
                if (isRemotePath(target, context.dlcRoot)) {
                    context.addedRemote = true;
                }
            } else {
                applyAddFile(source, target, add.getOverrideMode(), sourceKey, targetIsDir, context);
            }
            if (isRequiredPath(target, context.dlcRoot)) {
                addedRequiredFiles.add(target);
            }
        }
        applyImplicitRemoteAndRequired(context, addedRequiredFiles, hasRemoteAdd, hasRequiredAdd);
        removeSkipMarker(addedRequiredFiles);
        if (!addedRequiredFiles.isEmpty()) {
            context.addedRequired = true;
        }
    }

    private static void applyMoveOps(DlcUpdateManifest.FileOps ops, UpdateContext context) {
        if (ops == null || context == null) {
            return;
        }
        if (ops.getMove().isEmpty()) {
            return;
        }
        Path gameDir = DlcPaths.gameDir();
        for (Map.Entry<String, String> entry : ops.getMove().entrySet()) {
            String rawFrom = entry.getKey();
            String rawTo = entry.getValue();
            if (rawFrom == null || rawFrom.isBlank() || rawTo == null || rawTo.isBlank()) {
                continue;
            }
            Path from = resolveGamePath(gameDir, rawFrom);
            if (from == null || !Files.exists(from)) {
                continue;
            }
            boolean toIsDirHint = rawTo.endsWith("/");
            Path to = resolveGamePath(gameDir, rawTo);
            if (to == null) {
                continue;
            }
            if (toIsDirHint || Files.isDirectory(to)) {
                to = to.resolve(from.getFileName().toString());
            }
            boolean destExisted = Files.exists(to);
            backupPath(toGameRelativePath(from), from, context);
            if (destExisted) {
                backupPath(toGameRelativePath(to), to, context);
                deleteRecursive(to);
            } else {
                context.meta.getCreatedPaths().add(toGameRelativePath(to));
            }
            try {
                if (to.getParent() != null) {
                    Files.createDirectories(to.getParent());
                }
                if (moveWithHelperFallback(from, to, true)) {
                    continue;
                }
            } catch (IOException ignored) {
                try {
                    copyRecursive(from, to);
                    deleteRecursive(from);
                } catch (Exception ignored2) {
                }
            }
        }
    }

    private static void applyAddDirectory(Path source, Path target, DlcUpdateManifest.OverrideMode mode, String rawSourceKey,
        boolean targetIsDir, UpdateContext context) {
        if (!targetIsDir && !Files.isDirectory(target)) {
            targetIsDir = true;
        }
        if (!targetIsDir) {
            return;
        }
        if (Files.exists(target)) {
            switch (mode) {
                case NONE -> {
                    return;
                }
                case ALL -> {
                    backupPath(toGameRelativePath(target), target, context);
                    syncDirectory(source, target);
                    return;
                }
                case FALSE -> {
                    copyDirectory(source, target, false);
                    return;
                }
                case TRUE -> {
                    backupPath(toGameRelativePath(target), target, context);
                    copyDirectory(source, target, true);
                    return;
                }
            }
        } else {
            context.meta.getCreatedPaths().add(toGameRelativePath(target));
            copyRecursive(source, target);
        }
    }

    private static void applyAddFile(Path source, Path target, DlcUpdateManifest.OverrideMode mode, String rawSourceKey, boolean targetIsDir,
        UpdateContext context) {
        Path actualTarget = target;
        if (targetIsDir || Files.isDirectory(target)) {
            actualTarget = target.resolve(source.getFileName().toString());
        }
        if (Files.exists(actualTarget)) {
            if (mode == DlcUpdateManifest.OverrideMode.FALSE || mode == DlcUpdateManifest.OverrideMode.NONE) {
                return;
            }
            backupPath(toGameRelativePath(actualTarget), actualTarget, context);
        } else {
            context.meta.getCreatedPaths().add(toGameRelativePath(actualTarget));
        }
        try {
            if (actualTarget.getParent() != null) {
                Files.createDirectories(actualTarget.getParent());
            }
            if (!copyWithHelperFallback(source, actualTarget, true)) {
                return;
            }
            if (isNoticePath(actualTarget, context.dlcRoot)) {
                resetNoticeNeverShow(actualTarget);
            }
            if (isRemotePath(actualTarget, context.dlcRoot)) {
                context.addedRemote = true;
            }
        } catch (IOException ignored) {
        }
    }

    private static void applyModifyOps(DlcUpdateManifest.FileOps ops, UpdateContext context, boolean forceMissing) {
        if (ops == null) {
            return;
        }
        for (DlcUpdateManifest.ModifyEntry entry : ops.getModify()) {
            if (entry == null) {
                continue;
            }
            Path target = resolveGamePath(DlcPaths.gameDir(), entry.getTargetPath());
            if (target == null || !Files.exists(target)) {
                if (forceMissing) {
                    continue;
                }
                throw new IllegalStateException("Missing modify target");
            }
            backupPath(entry.getTargetPath(), target, context);
            String content = readString(target);
            if (content == null) {
                continue;
            }
            String find = entry.getNormalizedFind();
            if (find == null || find.isEmpty()) {
                continue;
            }
            String replaced = replaceOccurrences(content, find, entry.getReplace(), entry.getWhich());
            if (!content.equals(replaced)) {
                writeString(target, replaced);
            }
            if (isNoticePath(target, context.dlcRoot)) {
                resetNoticeNeverShow(target);
            }
        }
    }

    private static void applyUpdateOps(DlcUpdateManifest.UpdateOps ops, UpdateContext context) throws IOException, InterruptedException {
        if (ops == null || context == null || context.dlcRoot == null) {
            return;
        }
        if (!ops.getRemote().isEmpty()) {
            applyRemoteUpdateOps(ops.getRemote(), context);
        }
        if (!ops.getRequired().isEmpty()) {
            applyRequiredUpdateOps(ops.getRequired(), context);
        }
        if (!ops.getInfo().isEmpty()) {
            applyInfoUpdateOps(ops.getInfo(), context);
        }
    }

    private static void applyInfoUpdateOps(Map<String, DlcUpdateManifest.UpdateEntry> updates, UpdateContext context) {
        Map<String, Path> infoByIdentifier = loadInfoConfigPathByIdentifier(context.dlcRoot);
        if (infoByIdentifier.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DlcUpdateManifest.UpdateEntry> update : updates.entrySet()) {
            String identifier = update.getKey();
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            Path infoPath = infoByIdentifier.get(identifier);
            if (infoPath == null || !Files.exists(infoPath)) {
                continue;
            }
            DlcUpdateManifest.UpdateEntry updateEntry = update.getValue();
            if (updateEntry == null) {
                continue;
            }
            backupPath(toGameRelativePath(infoPath), infoPath, context);
            updateInfoConfigFields(infoPath, updateEntry);
        }
    }

    private static void applyRemoteUpdateOps(Map<String, DlcUpdateManifest.UpdateEntry> updates, UpdateContext context)
        throws IOException, InterruptedException {
        Map<String, DlcRemoteEntry> entries = loadRemoteEntries(context.dlcRoot);
        Map<String, Path> infoByIdentifier = loadInfoConfigPathByIdentifier(context.dlcRoot);
        if (entries.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DlcUpdateManifest.UpdateEntry> update : updates.entrySet()) {
            String identifier = update.getKey();
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcRemoteEntry entry = entries.get(identifier);
            if (entry == null) {
                continue;
            }
            Path infoPath = infoByIdentifier.get(identifier);
            boolean shouldRedownload = hasRemoteDownloadSourceChanges(entry, update.getValue());
            if (context != null && shouldRedownload) {
                context.addedRemote = true;
            }
            boolean wasEnabled = isRemoteEnabled(entry);
            Path oldTarget = entry.getTargetPath();
            if (shouldRedownload && wasEnabled && oldTarget != null) {
                uninstallDlcPath(oldTarget, context.dlcRoot, context);
            }
            if (shouldRedownload && oldTarget != null && Files.exists(oldTarget)) {
                backupAndDelete(oldTarget, context);
            }
            DlcUpdateManifest.UpdateEntry updateEntry = update.getValue();
            Path configPath = entry.getConfigPath();
            if (configPath != null && Files.exists(configPath)) {
                backupPath(toGameRelativePath(configPath), configPath, context);
                updateRemoteConfigFields(configPath, updateEntry);
                syncInfoConfigFields(infoPath, updateEntry, context);
                entry = DlcRemoteLoader.loadSingle(configPath, context.dlcRoot);
                if (entry == null) {
                    continue;
                }
            }
            if (!shouldRedownload) {
                continue;
            }
            DownloadedFile downloaded = downloadRemoteEntry(entry, true);
            if (downloaded == null) {
                throw new IOException("remote update download failed");
            }
            if (context != null && downloaded.path != null) {
                String created = toGameRelativePath(downloaded.path);
                if (created != null && !created.isBlank() && !context.meta.getCreatedPaths().contains(created)) {
                    context.meta.getCreatedPaths().add(created);
                }
            }
            if (downloaded.fileName != null && !downloaded.fileName.isBlank()) {
                updateRemoteFileName(configPath, downloaded.fileName);
                rewriteInfoFileWithNewFileName(infoPath, downloaded.fileName, context);
            }
            if (wasEnabled) {
                List<Path> applied = applyRemoteTargets(entry, downloaded.path, context);
                updateConfigState(entry, downloaded.path, applied, context);
            }
        }
    }

    private static void applyRequiredUpdateOps(Map<String, DlcUpdateManifest.UpdateEntry> updates, UpdateContext context)
        throws IOException, InterruptedException {
        Map<String, DlcRequiredEntry> entries = loadRequiredEntries(context.dlcRoot);
        Map<String, Path> infoByIdentifier = loadInfoConfigPathByIdentifier(context.dlcRoot);
        if (entries.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DlcUpdateManifest.UpdateEntry> update : updates.entrySet()) {
            String identifier = update.getKey();
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcRequiredEntry entry = entries.get(identifier);
            if (entry == null) {
                continue;
            }
            Path infoPath = infoByIdentifier.get(identifier);
            boolean shouldRedownload = hasRequiredDownloadSourceChanges(entry, update.getValue());
            String oldFileName = resolveRequiredFileName(entry, context.dlcRoot);
            List<AppliedTarget> appliedTargets = collectExistingRequiredTargets(entry, oldFileName);
            boolean wasApplied = !appliedTargets.isEmpty();
            if (shouldRedownload && wasApplied) {
                for (AppliedTarget target : appliedTargets) {
                    Path resolved = target.directory
                        ? (oldFileName == null ? null : target.path.resolve(oldFileName))
                        : target.path;
                    if (resolved != null) {
                        backupAndDelete(resolved, context);
                    }
                }
            }
            if (shouldRedownload && oldFileName != null) {
                Path oldDownload = entry.resolveRequiredPath(oldFileName);
                if (oldDownload != null && Files.exists(oldDownload)) {
                    backupAndDelete(oldDownload, context);
                }
            }
            DlcUpdateManifest.UpdateEntry updateEntry = update.getValue();
            Path configPath = entry.getConfigPath();
            if (configPath != null && Files.exists(configPath)) {
                backupPath(toGameRelativePath(configPath), configPath, context);
                updateRequiredConfigFields(configPath, updateEntry);
                syncInfoConfigFields(infoPath, updateEntry, context);
                entry = DlcRequiredLoader.loadSingle(configPath, context.dlcRoot);
                if (entry == null) {
                    continue;
                }
            }
            if (!shouldRedownload) {
                continue;
            }
            DownloadedFile downloaded = downloadRequiredEntry(entry, true);
            if (downloaded == null) {
                throw new IOException("required update download failed");
            }
            if (context != null && downloaded.path != null) {
                String created = toGameRelativePath(downloaded.path);
                if (created != null && !created.isBlank() && !context.meta.getCreatedPaths().contains(created)) {
                    context.meta.getCreatedPaths().add(created);
                }
            }
            if (downloaded.fileName != null && !downloaded.fileName.isBlank()) {
                updateRequiredFileName(configPath, downloaded.fileName);
                rewriteInfoFileWithNewFileName(infoPath, downloaded.fileName, context);
            }
            if (wasApplied) {
                applyRequiredTargets(downloaded.path, downloaded.fileName, appliedTargets, context);
            }
        }
    }

    private static boolean hasRemoteDownloadSourceChanges(DlcRemoteEntry entry, DlcUpdateManifest.UpdateEntry update) {
        if (entry == null || update == null) {
            return false;
        }
        if (update.getCurseforge() != null
            && !java.util.Objects.equals(normalizeNullable(update.getCurseforge()), normalizeNullable(entry.getCurseforgeFileId()))) {
            return true;
        }
        if (update.getModrinth() != null
            && !java.util.Objects.equals(normalizeNullable(update.getModrinth()), normalizeNullable(entry.getModrinthVersionId()))) {
            return true;
        }
        if (update.getFields().containsKey("download.directUrl")) {
            return !normalizeStringList(readStringArray(update.getFields().get("download.directUrl")))
                .equals(normalizeStringList(entry.getDirectUrls()));
        }
        return false;
    }

    private static boolean hasRequiredDownloadSourceChanges(DlcRequiredEntry entry, DlcUpdateManifest.UpdateEntry update) {
        if (entry == null || update == null) {
            return false;
        }
        if (update.getCurseforge() != null
            && !java.util.Objects.equals(normalizeNullable(update.getCurseforge()), normalizeNullable(entry.getCurseforgeFileId()))) {
            return true;
        }
        if (update.getModrinth() != null
            && !java.util.Objects.equals(normalizeNullable(update.getModrinth()), normalizeNullable(entry.getModrinthVersionId()))) {
            return true;
        }
        if (update.getFields().containsKey("download.directUrl")) {
            return !normalizeStringList(readStringArray(update.getFields().get("download.directUrl")))
                .equals(normalizeStringList(entry.getDirectUrls()));
        }
        return false;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static List<String> readStringArray(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
            return values;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child != null && !child.isJsonNull() && child.isJsonPrimitive()) {
                    values.add(child.getAsString());
                }
            }
        }
        return values;
    }

    private static Map<String, DlcRemoteEntry> loadRemoteEntries(Path dlcRoot) {
        Map<String, DlcRemoteEntry> map = new java.util.HashMap<>();
        for (DlcRemoteEntry entry : DlcRemoteLoader.load(dlcRoot)) {
            if (entry == null) {
                continue;
            }
            String id = entry.getDlcIdentifier();
            if (id != null && !id.isBlank()) {
                map.put(id, entry);
            }
        }
        return map;
    }

    private static Map<String, DlcRequiredEntry> loadRequiredEntries(Path dlcRoot) {
        Map<String, DlcRequiredEntry> map = new java.util.HashMap<>();
        Path requiredDir = dlcRoot == null ? null : dlcRoot.resolve("required");
        if (requiredDir == null || !Files.isDirectory(requiredDir)) {
            return map;
        }
        try (var stream = Files.newDirectoryStream(requiredDir)) {
            for (Path path : stream) {
                if (path.getFileName() == null) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (!Json5Util.isJsonLikeName(fileName) || Json5Util.shouldSkipLegacy(path)) {
                    continue;
                }
                if (isNoticeConfig(fileName)) {
                    continue;
                }
                DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(path, dlcRoot);
                if (entry == null) {
                    continue;
                }
                String id = entry.getIdentifier();
                if (id != null && !id.isBlank()) {
                    map.put(id, entry);
                }
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    private static boolean isRemoteEnabled(DlcRemoteEntry entry) {
        if (entry == null) {
            return false;
        }
        String fileName = entry.getFileNameHint();
        if (fileName == null || fileName.isBlank()) {
            Path target = entry.getTargetPath();
            if (target == null) {
                return false;
            }
            fileName = target.getFileName().toString();
        }
        DlcType type = entry.getType() == null ? DlcType.UNKNOWN : entry.getType();
        if (type == DlcType.UNKNOWN) {
            Path target = DlcPaths.resolveAppliedTarget(entry.getAppliedTargetRaw());
            if (target == null) {
                return false;
            }
            boolean dirTarget = DlcPaths.isAppliedTargetDirectory(entry.getAppliedTargetRaw(), target, entry.getTargetPath());
            if (dirTarget) {
                return Files.exists(target.resolve(fileName));
            }
            return Files.exists(target);
        }
        List<Path> targets = switch (type) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots();
            case DATA_PACK -> DlcPathConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> List.of();
        };
        if (targets.isEmpty()) {
            return false;
        }
        for (Path dir : targets) {
            if (!Files.exists(dir.resolve(fileName))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRequiredApplied(DlcRequiredEntry entry, Path dlcRoot) {
        if (entry == null || dlcRoot == null) {
            return false;
        }
        String fileName = resolveRequiredFileName(entry, dlcRoot);
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        for (Path target : entry.resolveAppliedTargets()) {
            Path resolved = resolveTarget(target, fileName);
            if (resolved != null && Files.exists(resolved)) {
                return true;
            }
        }
        return false;
    }

    private static List<AppliedTarget> collectExistingRequiredTargets(DlcRequiredEntry entry, String fileName) {
        if (entry == null) {
            return List.of();
        }
        List<AppliedTarget> targets = new ArrayList<>();
        for (Path target : entry.resolveAppliedTargets()) {
            if (target == null) {
                continue;
            }
            boolean dirTarget = Files.isDirectory(target)
                || target.toString().endsWith("/")
                || target.toString().endsWith("\\");
            if (dirTarget) {
                if (fileName != null && !fileName.isBlank()) {
                    Path resolved = target.resolve(fileName).normalize();
                    if (Files.exists(resolved)) {
                        targets.add(new AppliedTarget(target, true));
                    }
                }
            } else if (Files.exists(target)) {
                targets.add(new AppliedTarget(target, false));
            }
        }
        return targets;
    }

    private static void updateRemoteConfigFields(Path configPath, DlcUpdateManifest.UpdateEntry update) {
        if (configPath == null || update == null) {
            return;
        }
        JsonObject obj = readJson(configPath);
        if (obj == null) {
            return;
        }
        if (update.getCurseforge() != null && !update.getCurseforge().isBlank()) {
            obj.addProperty("curseforge_fileid", update.getCurseforge());
        }
        if (update.getModrinth() != null && !update.getModrinth().isBlank()) {
            obj.addProperty("modrinth_versionid", update.getModrinth());
        }
        applyEntryFieldUpdates(obj, update, FieldUpdateMode.NORMAL);
        writeJson(configPath, obj);
    }

    private static void updateRequiredConfigFields(Path configPath, DlcUpdateManifest.UpdateEntry update) {
        if (configPath == null || update == null) {
            return;
        }
        JsonObject obj = readJson(configPath);
        if (obj == null) {
            return;
        }
        if (update.getCurseforge() != null && !update.getCurseforge().isBlank()) {
            obj.addProperty("curseforge_fileid", update.getCurseforge());
        }
        if (update.getModrinth() != null && !update.getModrinth().isBlank()) {
            obj.addProperty("modrinth_versionid", update.getModrinth());
        }
        applyEntryFieldUpdates(obj, update, FieldUpdateMode.NORMAL);
        writeJson(configPath, obj);
    }

    private static void syncInfoConfigFields(Path infoPath, DlcUpdateManifest.UpdateEntry update, UpdateContext context) {
        if (infoPath == null || update == null || !Files.exists(infoPath)) {
            return;
        }
        JsonObject info = readJson(infoPath);
        if (info == null) {
            return;
        }
        if (applyEntryFieldUpdates(info, update, FieldUpdateMode.INFO_ALLOWED)) {
            backupPath(toGameRelativePath(infoPath), infoPath, context);
            writeJson(infoPath, info);
        }
    }

    private static void updateInfoConfigFields(Path infoPath, DlcUpdateManifest.UpdateEntry update) {
        if (infoPath == null || update == null || !Files.exists(infoPath)) {
            return;
        }
        JsonObject info = readJson(infoPath);
        if (info == null) {
            return;
        }
        if (applyEntryFieldUpdates(info, update, FieldUpdateMode.NORMAL)) {
            writeJson(infoPath, info);
        }
    }

    private static boolean applyEntryFieldUpdates(JsonObject obj, DlcUpdateManifest.UpdateEntry update, FieldUpdateMode mode) {
        if (obj == null || update == null || update.getFields().isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, JsonElement> field : update.getFields().entrySet()) {
            String path = field.getKey();
            JsonElement value = field.getValue();
            if (path == null || path.isBlank()) {
                continue;
            }
            if (applyStructuredFieldPath(obj, path, value, mode == null ? FieldUpdateMode.NORMAL : mode)) {
                changed = true;
            }
        }
        return changed;
    }

    private static final Set<String> INFO_ALLOWED_TABLES = Set.of(
        "basic", "displayname", "tip", "dependencies", "conflicts"
    );
    private static final Set<String> INFO_ALLOWED_BASIC_KEYS = Set.of(
        "identifier",
        "file_name",
        "type",
        "appliedTarget",
        "RedirectUrl",
        "isInList",
        "isHidden",
        "AllowEnableByAll",
        "dependencies",
        "conflicts",
        "alarm"
    );

    private static boolean applyStructuredFieldPath(JsonObject root, String dottedPath, JsonElement value, FieldUpdateMode mode) {
        if (root == null || dottedPath == null || dottedPath.isBlank()) {
            return false;
        }
        String[] parts = dottedPath.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        String table = parts[0].trim().toLowerCase(Locale.ROOT);
        if (table.isBlank()) {
            return false;
        }
        if (mode == FieldUpdateMode.INFO_ALLOWED && !INFO_ALLOWED_TABLES.contains(table)) {
            return false;
        }
        String[] keyPath = java.util.Arrays.copyOfRange(parts, 1, parts.length);
        if ("basic".equals(table) || "download".equals(table)) {
            if (mode == FieldUpdateMode.INFO_ALLOWED && "download".equals(table)) {
                return false;
            }
            String key = String.join(".", keyPath).trim();
            if (key.isBlank()) {
                return false;
            }
            if ("basic".equals(table)) {
                key = normalizeBasicFieldKey(key);
            }
            if (mode == FieldUpdateMode.INFO_ALLOWED && !INFO_ALLOWED_BASIC_KEYS.contains(key)) {
                return false;
            }
            upsertCaseInsensitive(root, key, safeCopy(value));
            return true;
        }
        String containerKey = switch (table) {
            case "displayname" -> "DisplayName";
            case "tip" -> "tip";
            case "dependencies" -> "dependencies";
            case "conflicts" -> "conflicts";
            default -> null;
        };
        if (containerKey == null) {
            return false;
        }
        JsonElement containerElement = JsonCaseUtil.getIgnoreCase(root, containerKey);
        JsonObject container;
        if (containerElement != null && containerElement.isJsonObject()) {
            container = containerElement.getAsJsonObject();
        } else {
            container = new JsonObject();
            upsertCaseInsensitive(root, containerKey, container);
        }
        if (keyPath.length == 0) {
            return false;
        }
        setNestedPath(container, keyPath, safeCopy(value));
        return true;
    }

    private static String normalizeBasicFieldKey(String key) {
        if (key == null) {
            return "";
        }
        if ("conflict".equalsIgnoreCase(key)) {
            return "conflicts";
        }
        if ("dependency".equalsIgnoreCase(key)) {
            return "dependencies";
        }
        if ("dlc_identifier".equalsIgnoreCase(key)) {
            return "identifier";
        }
        if ("dlc_name".equalsIgnoreCase(key)) {
            return "file_name";
        }
        if ("redirecturl".equalsIgnoreCase(key)) {
            return "RedirectUrl";
        }
        return key;
    }

    private static String findExistingKeyIgnoreCase(JsonObject obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        if (obj.has(key)) {
            return key;
        }
        for (String candidate : obj.keySet()) {
            if (candidate != null && candidate.equalsIgnoreCase(key)) {
                return candidate;
            }
        }
        return null;
    }

    private static void upsertCaseInsensitive(JsonObject obj, String key, JsonElement value) {
        if (obj == null || key == null || key.isBlank()) {
            return;
        }
        String existing = findExistingKeyIgnoreCase(obj, key);
        if (existing != null) {
            obj.add(existing, value);
        } else {
            obj.add(key, value);
        }
    }

    private static void setNestedPath(JsonObject root, String[] path, JsonElement value) {
        if (root == null || path == null || path.length == 0) {
            return;
        }
        JsonObject current = root;
        for (int i = 0; i < path.length; i++) {
            String raw = path[i];
            String key = raw == null ? "" : raw.trim();
            if (key.isBlank()) {
                return;
            }
            if (i == path.length - 1) {
                upsertCaseInsensitive(current, key, value);
                return;
            }
            JsonElement child = JsonCaseUtil.getIgnoreCase(current, key);
            JsonObject next;
            if (child != null && child.isJsonObject()) {
                next = child.getAsJsonObject();
            } else {
                next = new JsonObject();
                upsertCaseInsensitive(current, key, next);
            }
            current = next;
        }
    }

    private static JsonElement safeCopy(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        return value.deepCopy();
    }

    private enum FieldUpdateMode {
        NORMAL,
        INFO_ALLOWED
    }

    private static Map<String, Path> loadInfoConfigPathByIdentifier(Path dlcRoot) {
        Map<String, Path> map = new java.util.HashMap<>();
        if (dlcRoot == null) {
            return map;
        }
        Path infoDir = dlcRoot.resolve("info");
        if (!Files.isDirectory(infoDir)) {
            return map;
        }
        try (var stream = Files.walk(infoDir)) {
            stream.filter(Files::isRegularFile)
                .forEach(path -> {
                    if (path.getFileName() == null) {
                        return;
                    }
                    String fileName = path.getFileName().toString();
                    if (!Json5Util.isJsonLikeName(fileName) || Json5Util.shouldSkipLegacy(path)) {
                        return;
                    }
                    String baseName = Json5Util.stripJsonExtension(fileName);
                    if ("version".equalsIgnoreCase(baseName) || "notice".equalsIgnoreCase(baseName)) {
                        return;
                    }
                    JsonObject obj = readJson(path);
                    if (obj == null) {
                        return;
                    }
                    String identifier = JsonCaseUtil.getStringIgnoreCase(obj, "identifier");
                    if (identifier == null || identifier.isBlank()) {
                        identifier = JsonCaseUtil.getStringIgnoreCase(obj, "dlc_identifier");
                    }
                    if (identifier == null || identifier.isBlank()) {
                        return;
                    }
                    map.putIfAbsent(identifier, path);
                });
        } catch (IOException ignored) {
        }
        return map;
    }

    private static void updateRemoteFileName(Path configPath, String fileName) {
        if (configPath == null || fileName == null || fileName.isBlank()) {
            return;
        }
        JsonObject obj = readJson(configPath);
        if (obj == null) {
            return;
        }
        obj.addProperty("file_name", fileName);
        writeJson(configPath, obj);
        Path dlcRoot = getDlcRoot();
        if (dlcRoot != null) {
            DlcRemoteLoader.loadSingle(configPath, dlcRoot);
        }
    }

    private static void updateRequiredFileName(Path configPath, String fileName) {
        if (configPath == null || fileName == null || fileName.isBlank()) {
            return;
        }
        JsonObject obj = readJson(configPath);
        if (obj == null) {
            return;
        }
        obj.addProperty("file_name", fileName);
        writeJson(configPath, obj);
        Path dlcRoot = getDlcRoot();
        if (dlcRoot != null) {
            DlcRequiredLoader.loadSingle(configPath, dlcRoot);
        }
    }

    private static void rewriteInfoFileWithNewFileName(Path infoPath, String fileName, UpdateContext context) {
        if (infoPath == null || fileName == null || fileName.isBlank() || !Files.exists(infoPath)) {
            return;
        }
        JsonObject info = readJson(infoPath);
        if (info == null) {
            return;
        }
        upsertCaseInsensitive(info, "file_name", safeCopy(new com.google.gson.JsonPrimitive(fileName)));
        Path infoDir = infoPath.getParent();
        if (infoDir == null) {
            backupPath(toGameRelativePath(infoPath), infoPath, context);
            writeJson(infoPath, info);
            return;
        }
        Path targetPath = Json5Util.resolveConfigPath(infoDir, fileName);
        if (targetPath == null) {
            backupPath(toGameRelativePath(infoPath), infoPath, context);
            writeJson(infoPath, info);
            return;
        }
        backupPath(toGameRelativePath(infoPath), infoPath, context);
        if (!targetPath.normalize().equals(infoPath.normalize()) && Files.exists(targetPath)) {
            backupPath(toGameRelativePath(targetPath), targetPath, context);
            if (!deleteWithHelperFallback(targetPath)) {
                throw new IllegalStateException(latestFailureMessage("Failed to replace info file: " + targetPath));
            }
        }
        if (!targetPath.normalize().equals(infoPath.normalize())) {
            if (!deleteWithHelperFallback(infoPath)) {
                throw new IllegalStateException(latestFailureMessage("Failed to remove old info file: " + infoPath));
            }
        }
        writeJson(targetPath, info);
    }

    private static JsonObject readJson(Path path) {
        return DcConfigIO.read(path, layoutForPath(path));
    }

    private static void writeJson(Path path, JsonObject obj) {
        if (path == null || obj == null) {
            return;
        }
        if (DcConfigIO.write(path, obj, layoutForPath(path))) {
            return;
        }
        Path target = DcConfigIO.toDcPath(path);
        String serialized = serializeJsonForPath(target, obj);
        DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleWriteText(target, serialized);
        if (result.ok()) {
            markHelperScheduled();
            if (!target.equals(path)) {
                deleteWithHelperFallback(path);
            }
            return;
        }
        recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message(), target, "write");
    }

    private static DcConfigIO.Layout layoutForPath(Path path) {
        if (path == null) {
            return DcConfigIO.Layout.GENERIC;
        }
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("/remote/")
            || normalized.contains("/required/")) {
            return DcConfigIO.Layout.DLC_STRUCTURED;
        }
        return DcConfigIO.Layout.GENERIC;
    }

    private static DownloadedFile downloadRemoteEntry(DlcRemoteEntry entry) throws IOException, InterruptedException {
        return downloadRemoteEntry(entry, false);
    }

    private static DownloadedFile downloadRemoteEntry(DlcRemoteEntry entry, boolean ignoreExistingFileName) throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        List<DownloadCandidate> candidates = buildRemoteCandidates(entry);
        if (candidates.isEmpty()) {
            throw new IOException("missing download url");
        }
        return downloadWithRetry(entry, candidates, ignoreExistingFileName);
    }

    private static DownloadedFile downloadRequiredEntry(DlcRequiredEntry entry) throws IOException, InterruptedException {
        return downloadRequiredEntry(entry, false);
    }

    private static DownloadedFile downloadRequiredEntry(DlcRequiredEntry entry, boolean ignoreExistingFileName) throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        List<DownloadCandidate> candidates = buildRequiredCandidates(entry);
        if (candidates.isEmpty()) {
            throw new IOException("missing download url");
        }
        return downloadWithRetry(entry, candidates, ignoreExistingFileName);
    }

    private static List<DownloadCandidate> buildRemoteCandidates(DlcRemoteEntry entry) throws IOException, InterruptedException {
        List<DownloadCandidate> candidates = new ArrayList<>();
        List<String> order = entry.getDownloadPriorityList();
        addCandidatesForPriority(candidates, order, entry.getCurseforgeProjectId(), entry.getCurseforgeFileId(),
            entry.getModrinthProjectId(), entry.getModrinthVersionId(), entry.getDirectUrls());
        return candidates;
    }

    private static List<DownloadCandidate> buildRequiredCandidates(DlcRequiredEntry entry) throws IOException, InterruptedException {
        List<DownloadCandidate> candidates = new ArrayList<>();
        List<String> order = entry.getDownloadPriorityList();
        addCandidatesForPriority(candidates, order, entry.getCurseforgeProjectId(), entry.getCurseforgeFileId(),
            entry.getModrinthProjectId(), entry.getModrinthVersionId(), entry.getDirectUrls());
        return candidates;
    }

    private static void addCandidatesForPriority(List<DownloadCandidate> candidates, List<String> order,
        String curseProjectId, String curseFileId, String modrinthProjectId, String modrinthVersionId, List<String> directUrls)
        throws IOException, InterruptedException {
        List<String> resolvedOrder = new ArrayList<>();
        if (order != null) {
            for (String key : order) {
                if (key != null && !key.isBlank() && !resolvedOrder.contains(key)) {
                    resolvedOrder.add(key);
                }
            }
        }
        if (resolvedOrder.isEmpty()) {
            resolvedOrder.add("curseforge");
            resolvedOrder.add("modrinth");
            resolvedOrder.add("directurl");
        }
        for (String key : resolvedOrder) {
            switch (key) {
                case "curseforge" -> {
                    if (curseFileId != null && !curseFileId.isBlank()) {
                        CurseForgeClient.CurseForgeFile file = CurseForgeClient.fetchFileByIdOrVersion(
                            curseProjectId, curseFileId, CrossPlatformRuntime.mcVersion(), CrossPlatformRuntime.loaderName());
                        if (file != null && file.url != null && !file.url.isBlank()) {
                            candidates.add(DownloadCandidate.fromCurseForge(file));
                        }
                    }
                }
                case "modrinth" -> {
                    if (modrinthVersionId != null && !modrinthVersionId.isBlank()) {
                        ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersionByIdOrVersion(
                            modrinthProjectId, modrinthVersionId, CrossPlatformRuntime.mcVersion(), CrossPlatformRuntime.loaderName());
                        if (version != null && version.url != null && !version.url.isBlank()) {
                            candidates.add(DownloadCandidate.fromModrinth(version));
                        }
                    }
                }
                case "directurl" -> {
                    if (directUrls != null) {
                        for (String url : directUrls) {
                            if (url != null && !url.isBlank()) {
                                candidates.add(DownloadCandidate.fromDirect(url));
                            }
                        }
                    }
                }
            }
        }
        if (candidates.isEmpty() && directUrls != null) {
            for (String url : directUrls) {
                if (url != null && !url.isBlank()) {
                    candidates.add(DownloadCandidate.fromDirect(url));
                }
            }
        }
    }

    private static DownloadedFile downloadWithRetry(Object entry, List<DownloadCandidate> candidates, boolean ignoreExistingFileName)
        throws IOException {
        int maxRetry = Math.max(1, DlcPathConfig.getMaxRetryCount());
        IOException lastError = null;
        for (DownloadCandidate candidate : candidates) {
            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                try {
                    return downloadCandidate(entry, candidate, ignoreExistingFileName);
                } catch (IOException ex) {
                    lastError = ex;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    private static DownloadedFile downloadCandidate(Object entry, DownloadCandidate candidate, boolean ignoreExistingFileName) throws IOException {
        String fileName = null;
        boolean fileNameExplicit = false;
        if (entry instanceof DlcRemoteEntry remote) {
            fileName = remote.getFileNameHint();
            fileNameExplicit = remote.isFileNameExplicit();
        } else if (entry instanceof DlcRequiredEntry required) {
            fileName = required.getRequiredFileName();
            fileNameExplicit = fileName != null && !fileName.isBlank();
        }
        if (ignoreExistingFileName) {
            fileNameExplicit = false;
            fileName = null;
        }
        if (!fileNameExplicit) {
            if (candidate.fileName != null && !candidate.fileName.isBlank()) {
                fileName = candidate.fileName;
            }
            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                String urlName = DlcDownloadUtil.extractFileNameFromUrl(candidate.url);
                if (DlcDownloadUtil.isLikelyFileName(urlName)) {
                    fileName = urlName;
                }
            }
            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                String headerName = fetchHeaderFileName(candidate.url);
                if (DlcDownloadUtil.isLikelyFileName(headerName)) {
                    fileName = headerName;
                }
            }
        }
        if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
            throw new IOException("invalid file name");
        }
        fileName = sanitizeFileName(fileName);
        notifyApplyProgress("Downloading " + fileName + "...", 0f);
        Path target;
        if (entry instanceof DlcRemoteEntry remote) {
            target = remote.resolveTargetPath(fileName);
        } else if (entry instanceof DlcRequiredEntry required) {
            target = required.resolveRequiredPath(fileName);
        } else {
            target = null;
        }
        if (target == null) {
            throw new IOException("invalid target path");
        }
        Files.createDirectories(target.getParent());
        Path tempFile = target.resolveSibling(fileName + ".part");
        deleteWithHelperFallback(tempFile);
        downloadToFile(candidate.url, tempFile);
        if (!moveWithHelperFallback(tempFile, target, true)) {
            throw new IOException(latestFailureMessage("failed to move downloaded file"));
        }
        return new DownloadedFile(target, fileName);
    }

    private static void downloadToFile(String url, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("invalid target path");
        }
        String displayName = target.getFileName() == null ? "download" : target.getFileName().toString();
        Path downloaded = DlcDownloadService.download(url, parent, displayName, (done, total) -> {
            float progress = total > 0 ? Math.max(0f, Math.min(1f, done / (float) total)) : -1f;
            notifyApplyProgress("Downloading " + displayName + "...", progress);
        }, null);
        if (!downloaded.equals(target)) {
            if (!moveWithHelperFallback(downloaded, target, true)) {
                throw new IOException(latestFailureMessage("failed to finalize downloaded file"));
            }
        }
    }

    private static void notifyApplyProgress(String status, float progress) {
        ApplyProgressListener listener = CURRENT_PROGRESS_LISTENER.get();
        if (listener != null) {
            listener.onProgress(status, progress);
        }
    }

    private static String fetchHeaderFileName(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.connect();
            return DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<Path> applyRemoteTargets(DlcRemoteEntry entry, Path source, UpdateContext context) {
        List<Path> targets = new ArrayList<>();
        if (entry == null || source == null) {
            return targets;
        }
        DlcType type = entry.getType() == null ? DlcType.UNKNOWN : entry.getType();
        String fileName = source.getFileName().toString();
        try {
            if (type == DlcType.UNKNOWN) {
                Path target = DlcPaths.resolveAppliedTarget(entry.getAppliedTargetRaw());
                if (target == null) {
                    return targets;
                }
                boolean dirTarget = DlcPaths.isAppliedTargetDirectory(entry.getAppliedTargetRaw(), target, source);
                if (dirTarget) {
                Files.createDirectories(target);
                Path resolved = target.resolve(fileName);
                backupOrCreate(resolved, context);
                if (!copyWithHelperFallback(source, resolved, true)) {
                    return targets;
                }
                targets.add(resolved);
            } else {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                backupOrCreate(target, context);
                if (!copyWithHelperFallback(source, target, true)) {
                    return targets;
                }
                targets.add(target);
            }
                return targets;
            }
            List<Path> targetDirs = switch (type) {
                case MOD -> List.of(DlcPaths.modsDir());
                case RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots();
                case DATA_PACK -> DlcPathConfig.getDatapackRoots();
                case TACZ_PACK -> List.of(DlcPaths.taczDir());
                default -> List.of();
            };
            for (Path targetDir : targetDirs) {
                Files.createDirectories(targetDir);
                Path target = targetDir.resolve(fileName);
                backupOrCreate(target, context);
                if (!copyWithHelperFallback(source, target, true)) {
                    return targets;
                }
                targets.add(target);
            }
        } catch (IOException ignored) {
        }
        return targets;
    }

    private static void applyRequiredTargets(Path source, String fileName, List<AppliedTarget> targets, UpdateContext context) {
        if (source == null || fileName == null || fileName.isBlank() || targets == null || targets.isEmpty()) {
            return;
        }
        for (AppliedTarget applied : targets) {
            if (applied == null || applied.path == null) {
                continue;
            }
            Path target = applied.directory ? applied.path.resolve(fileName) : applied.path;
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                backupOrCreate(target, context);
                if (!copyWithHelperFallback(source, target, true)) {
                    continue;
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void backupOrCreate(Path target, UpdateContext context) {
        if (target == null || context == null) {
            return;
        }
        if (Files.exists(target)) {
            backupPath(toGameRelativePath(target), target, context);
        } else {
            context.meta.getCreatedPaths().add(toGameRelativePath(target));
        }
    }

    private static void updateConfigState(DlcRemoteEntry entry, Path source, List<Path> applied, UpdateContext context) {
        if (entry == null || source == null || applied == null) {
            return;
        }
        DlcType type = entry.getType() == null ? DlcType.UNKNOWN : entry.getType();
        String key = buildConfigKey(entry.getDlcRoot(), source, type);
        if (key == null || key.isBlank()) {
            return;
        }
        DlcConfig.DlcState state = DlcConfig.getState(key);
        if (state == null) {
            state = new DlcConfig.DlcState();
        }
        state.enabled = true;
        state.appliedTargets = applied.stream()
            .map(DlcUpdateService::toGameRelativePath)
            .toList();
        DlcConfig.setState(key, state);
        DlcConfig.save();
    }

    private static String buildConfigKey(Path dlcRoot, Path source, DlcType type) {
        if (dlcRoot == null || source == null || type == null) {
            return null;
        }
        String rootId = toGameRelativePath(dlcRoot).replace('\\', '/');
        String relative = dlcRoot.relativize(source).toString().replace('\\', '/');
        return type.name().toLowerCase(java.util.Locale.ROOT) + ":" + rootId + "|" + relative;
    }

    private static Path resolveTarget(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        if (Files.isDirectory(targetDir) || targetDir.toString().endsWith("/") || targetDir.toString().endsWith("\\")) {
            return targetDir.resolve(fileName).normalize();
        }
        return targetDir.normalize();
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = DlcDownloadUtil.stripQuery(name);
        if (cleaned == null) {
            return null;
        }
        return cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record DownloadCandidate(String url, String fileName) {
        static DownloadCandidate fromCurseForge(CurseForgeClient.CurseForgeFile file) {
            return new DownloadCandidate(file.url, file.fileName);
        }

        static DownloadCandidate fromModrinth(ModrinthClient.ModrinthVersion version) {
            return new DownloadCandidate(version.url, version.fileName);
        }

        static DownloadCandidate fromDirect(String url) {
            return new DownloadCandidate(url, null);
        }
    }

    private record DownloadedFile(Path path, String fileName) {}

    private record AppliedTarget(Path path, boolean directory) {}

    private static void applyImplicitRemoteAndRequired(UpdateContext context, List<Path> addedRequiredFiles, boolean hasRemoteAdd, boolean hasRequiredAdd) {
        if (context.stagingRoot == null || context.dlcRoot == null) {
            return;
        }
        Path remoteDir = context.stagingRoot.resolve("remote");
        if (!hasRemoteAdd && Files.isDirectory(remoteDir)) {
            Path target = context.dlcRoot.resolve("remote");
            if (Files.exists(target)) {
                backupPath(toGameRelativePath(target), target, context);
            } else {
                context.meta.getCreatedPaths().add(toGameRelativePath(target));
            }
            copyDirectory(remoteDir, target, true);
            context.addedRemote = true;
        }
        Path requiredDir = context.stagingRoot.resolve("required");
        if (!hasRequiredAdd && Files.isDirectory(requiredDir)) {
            Path target = context.dlcRoot.resolve("required");
            if (Files.exists(target)) {
                backupPath(toGameRelativePath(target), target, context);
            } else {
                context.meta.getCreatedPaths().add(toGameRelativePath(target));
            }
            copyDirectory(requiredDir, target, true);
            addedRequiredFiles.add(target);
            context.addedRequired = true;
        }
    }

    private static void handleSpecialDelete(Path target, String rawPath, UpdateContext context) {
        if (context.dlcRoot == null || target == null) {
            return;
        }
        Path normalized = target.normalize();
        Path dlcRoot = context.dlcRoot.normalize();
        if (!normalized.startsWith(dlcRoot)) {
            return;
        }
        if (isNoticePath(normalized, dlcRoot)) {
            return;
        }
        if (isVersionPath(normalized, dlcRoot)) {
            return;
        }
        if (isUnderDir(normalized, dlcRoot.resolve("required"))) {
            uninstallRequiredConfig(normalized, dlcRoot, context);
            return;
        }
        if (isUnderDir(normalized, dlcRoot.resolve("remote"))) {
            uninstallRemoteConfig(normalized, dlcRoot, context);
            return;
        }
        if (isUnderDir(normalized, dlcRoot.resolve("preset"))) {
            if (Files.isDirectory(normalized)) {
                uninstallPresetConfigsInDir(normalized, dlcRoot);
            } else if (normalized.getFileName() != null && Json5Util.isJsonLikeName(normalized.getFileName().toString())) {
                uninstallPresetConfig(normalized, dlcRoot);
            }
            return;
        }
        if (isUnderDir(normalized, dlcRoot.resolve("info"))) {
            return;
        }
        uninstallDlcPath(normalized, dlcRoot, context);
    }

    private static void uninstallRequiredConfig(Path configPath, Path dlcRoot, UpdateContext context) {
        if (configPath == null || dlcRoot == null || !Files.exists(configPath)) {
            return;
        }
        DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(configPath, dlcRoot);
        if (entry == null) {
            return;
        }
        String requiredFileName = resolveRequiredFileName(entry, dlcRoot);
        deleteRequiredDownload(entry, dlcRoot, requiredFileName, context);
        for (Path target : entry.resolveAppliedTargets()) {
            if (target == null) {
                continue;
            }
            if (Files.isDirectory(target)) {
                if (requiredFileName == null || requiredFileName.isBlank()) {
                    continue;
                }
                Path file = target.resolve(requiredFileName);
                if (Files.exists(file)) {
                    backupAndDelete(file, context);
                }
                continue;
            }
            backupAndDelete(target, context);
        }
    }

    private static void uninstallRemoteConfig(Path configPath, Path dlcRoot, UpdateContext context) {
        if (configPath == null || dlcRoot == null || !Files.exists(configPath)) {
            return;
        }
        DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(configPath, dlcRoot);
        if (entry == null) {
            return;
        }
        Path target = entry.getTargetPath();
        if (target == null) {
            return;
        }
        if (context != null) {
            context.addedRemote = true;
        }
        uninstallDlcPath(target, dlcRoot, context);
    }

    private static void uninstallPresetConfig(Path configPath, Path dlcRoot) {
        if (configPath == null || dlcRoot == null || !Files.exists(configPath)) {
            return;
        }
        if (configPath.getFileName() == null || !Json5Util.isJsonLikeName(configPath.getFileName().toString())) {
            return;
        }
        String presetId = resolvePresetId(configPath, dlcRoot);
        if (presetId == null || presetId.isBlank()) {
            return;
        }
        if (!CrossPlatformRuntime.isDedicatedServer()) {
            DlcPresetRuntimeState.setPresetEnabled(presetId, false);
        }
        Map<String, DlcPresetStatus> statuses = new LinkedHashMap<>(DlcPresetStateStore.loadPresetStatus());
        if (statuses.remove(presetId) != null) {
            DlcPresetStateStore.savePresetStatus(statuses);
        }
    }

    private static void uninstallPresetConfigsInDir(Path dir, Path dlcRoot) {
        if (dir == null || dlcRoot == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.filter(path -> path.getFileName() != null && Json5Util.isJsonLikeName(path.getFileName().toString()))
                .forEach(path -> uninstallPresetConfig(path, dlcRoot));
        } catch (IOException ignored) {
        }
    }

    private static String resolvePresetId(Path configPath, Path dlcRoot) {
        if (configPath == null || dlcRoot == null) {
            return null;
        }
        Path presetRoot = dlcRoot.resolve("preset").normalize();
        Path normalized = configPath.normalize();
        String fallback = Json5Util.stripJsonExtension(configPath.getFileName().toString());
        if (!normalized.startsWith(presetRoot)) {
            return fallback;
        }
        try {
            String relative = presetRoot.relativize(normalized).toString().replace('\\', '/');
            return Json5Util.stripJsonExtension(relative);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void uninstallDlcPath(Path target, Path dlcRoot, UpdateContext context) {
        if (target == null || dlcRoot == null) {
            return;
        }
        String rootId = toGameRelativePath(dlcRoot).replace('\\', '/');
        String relative = dlcRoot.relativize(target).toString().replace('\\', '/');
        String suffix = ":" + rootId + "|" + relative;
        for (Map.Entry<String, DlcConfig.DlcState> entry : DlcConfig.getAllStates().entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (!key.contains(suffix)) {
                continue;
            }
            DlcConfig.DlcState state = entry.getValue();
            if (state != null && state.appliedTargets != null) {
                for (String raw : state.appliedTargets) {
                    Path applied = resolveStoredPath(raw);
                    backupAndDelete(applied, context);
                }
                state.enabled = false;
                state.appliedTargets = List.of();
                DlcConfig.setState(key, state);
            }
        }
        DlcConfig.save();
    }

    private static void deleteRequiredDownload(DlcRequiredEntry entry, Path dlcRoot, String requiredFileName, UpdateContext context) {
        if (entry == null || dlcRoot == null) {
            return;
        }
        Path requiredDir = dlcRoot.resolve("required");
        if (!Files.isDirectory(requiredDir)) {
            return;
        }
        if (requiredFileName == null || requiredFileName.isBlank()) {
            return;
        }
        Path downloaded = requiredDir.resolve(requiredFileName);
        if (Files.exists(downloaded)) {
            backupAndDelete(downloaded, context);
        }
    }

    private static String resolveRequiredFileName(DlcRequiredEntry entry, Path dlcRoot) {
        if (entry == null || dlcRoot == null) {
            return null;
        }
        String fileName = entry.getRequiredFileName();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        Path requiredDir = dlcRoot.resolve("required");
        if (!Files.isDirectory(requiredDir)) {
            return null;
        }
        String base = entry.getBaseName();
        if (base == null || base.isBlank()) {
            return null;
        }
        try (var stream = Files.newDirectoryStream(requiredDir, base + ".*")) {
            for (Path file : stream) {
                if (file != null && !Json5Util.isJsonLikeName(file.getFileName().toString())) {
                    return file.getFileName().toString();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static void backupAndDelete(Path target, UpdateContext context) {
        if (target == null) {
            return;
        }
        if (Files.exists(target) && context != null) {
            backupPath(toGameRelativePath(target), target, context);
        }
        deleteRecursive(target);
    }

    private static void rollbackUpdate(UpdateContext context) {
        if (context == null || context.backupRoot == null || context.meta == null) {
            return;
        }
        for (String created : context.meta.getCreatedPaths()) {
            Path target = resolveGamePath(DlcPaths.gameDir(), created);
            if (context.dlcRoot != null && target != null && Files.exists(target)) {
                Path dlcRoot = context.dlcRoot;
                Path requiredRoot = dlcRoot.resolve("required");
                Path remoteRoot = dlcRoot.resolve("remote");
                if (isUnderDir(target, requiredRoot)) {
                    if (Files.isDirectory(target)) {
                        uninstallConfigsInDir(target, dlcRoot, true);
                    } else if (target.getFileName() != null && Json5Util.isJsonLikeName(target.getFileName().toString())) {
                        uninstallRequiredConfig(target, dlcRoot, null);
                    }
                } else if (isUnderDir(target, remoteRoot)) {
                    if (Files.isDirectory(target)) {
                        uninstallConfigsInDir(target, dlcRoot, false);
                    } else if (target.getFileName() != null && Json5Util.isJsonLikeName(target.getFileName().toString())) {
                        uninstallRemoteConfig(target, dlcRoot, null);
                    }
                }
            }
            deleteRecursive(target);
        }
        for (String restore : context.meta.getRestorePaths()) {
            Path backup = context.backupRoot.resolve(restore);
            Path target = resolveGamePath(DlcPaths.gameDir(), restore);
            if (Files.exists(backup)) {
                deleteRecursive(target);
                copyRecursive(backup, target);
            }
        }
    }

    private static void uninstallConfigsInDir(Path dir, Path dlcRoot, boolean required) {
        if (dir == null || dlcRoot == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.filter(path -> path.getFileName() != null && Json5Util.isJsonLikeName(path.getFileName().toString()))
                .forEach(path -> {
                    if (required) {
                        uninstallRequiredConfig(path, dlcRoot, null);
                    } else {
                        uninstallRemoteConfig(path, dlcRoot, null);
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private static List<DlcUpdateMeta> loadRollbackChain(Path dlcRoot, String currentBaseVersion, String currentDlcVersion) {
        if (dlcRoot == null || currentDlcVersion == null) {
            return List.of();
        }
        List<DlcUpdateMeta> chain = new ArrayList<>();
        String workingBase = safeVersion(currentBaseVersion);
        String workingDlc = safeVersion(currentDlcVersion);
        for (int i = 0; i < 32; i++) {
            DlcUpdateMeta meta = loadMetaForVersion(dlcRoot, workingBase, workingDlc);
            if (meta == null) {
                break;
            }
            String sourceDlc = sourceDlcVersion(meta);
            String targetDlc = targetDlcVersion(meta);
            String sourceBase = sourceBaseVersion(meta);
            String targetBase = targetBaseVersion(meta);
            if (sourceDlc.isBlank() || targetDlc.isBlank()) {
                break;
            }
            if (!workingDlc.equals(targetDlc) || !workingBase.equals(targetBase)) {
                break;
            }
            chain.add(meta);
            if (sourceDlc.equals(workingDlc) && sourceBase.equals(workingBase)) {
                break;
            }
            workingDlc = sourceDlc;
            workingBase = sourceBase;
        }
        return chain;
    }

    private static void backupPath(String rawRelative, Path target, UpdateContext context) {
        if (context == null || target == null || rawRelative == null || rawRelative.isBlank()) {
            return;
        }
        String normalized = normalizePath(rawRelative, target);
        if (!context.meta.getRestorePaths().contains(normalized)) {
            context.meta.getRestorePaths().add(normalized);
        }
        Path backupTarget = context.backupRoot.resolve(normalized);
        copyRecursive(target, backupTarget);
    }

    private static String normalizePath(String raw, Path target) {
        if (raw == null || raw.isBlank()) {
            return target == null ? "" : target.toString();
        }
        String normalized = raw.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static Path resolveGamePath(Path gameDir, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path path = Path.of(rawPath);
        if (!path.isAbsolute()) {
            path = gameDir.resolve(path);
        }
        return path.normalize();
    }

    private static Path resolveStagingPath(Path stagingRoot, String rawPath) {
        if (stagingRoot == null || rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path path = stagingRoot.resolve(rawPath).normalize();
        if (!path.startsWith(stagingRoot)) {
            return null;
        }
        return path;
    }

    private static boolean isUnderDir(Path path, Path root) {
        if (path == null || root == null) {
            return false;
        }
        return path.normalize().startsWith(root.normalize());
    }

    private static boolean isNoticePath(Path path, Path dlcRoot) {
        if (path == null || dlcRoot == null) {
            return false;
        }
        Path infoDir = dlcRoot.resolve("info");
        Path notice = Json5Util.resolveConfigPath(infoDir, "notice");
        notice = Json5Util.migrateLegacy(notice, UPDATE_GSON);
        if (notice == null) {
            return false;
        }
        return path.normalize().equals(notice.normalize());
    }

    private static boolean isVersionPath(Path path, Path dlcRoot) {
        if (path == null || dlcRoot == null) {
            return false;
        }
        Path infoDir = dlcRoot.resolve("info");
        Path dc = infoDir.resolve("version" + Json5Util.DC_EXTENSION).normalize();
        Path json5 = infoDir.resolve("version" + Json5Util.JSON5_EXTENSION).normalize();
        Path json = infoDir.resolve("version.json").normalize();
        Path normalized = path.normalize();
        return normalized.equals(dc) || normalized.equals(json5) || normalized.equals(json);
    }

    private static boolean isNoticeConfig(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return "notice".equalsIgnoreCase(Json5Util.stripJsonExtension(fileName));
    }

    private static boolean isRequiredPath(Path target, Path dlcRoot) {
        if (target == null || dlcRoot == null) {
            return false;
        }
        return isUnderDir(target, dlcRoot.resolve("required"));
    }

    private static boolean isRemotePath(Path target, Path dlcRoot) {
        if (target == null || dlcRoot == null) {
            return false;
        }
        return isUnderDir(target, dlcRoot.resolve("remote"));
    }

    private static void refreshPresetRuntimeState() {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return;
        }
        try {
            DlcPresetRuntimeState.reload();
        } catch (Throwable ignored) {
        }
    }

    private static void resetNoticeNeverShow(Path noticePath) {
        JsonObjectHolder holder = JsonObjectHolder.load(noticePath);
        if (holder == null || holder.obj == null) {
            return;
        }
        holder.obj.addProperty("neverShow", false);
        holder.save(noticePath);
    }

    public static void deleteRecursive(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    forceDelete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    forceDelete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    forceDelete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void forceDelete(Path path) {
        if (path == null) {
            return;
        }
        if (deleteWithHelperFallback(path)) {
            return;
        }
        try {
            Files.setAttribute(path, "dos:readonly", false);
        } catch (Exception ignored) {
        }
        try {
            java.io.File file = path.toFile();
            file.setWritable(true);
            file.setReadable(true);
            file.setExecutable(true, false);
        } catch (Exception ignored) {
        }
        if (deleteWithHelperFallback(path)) {
            return;
        }
        try {
            path.toFile().deleteOnExit();
        } catch (Exception ignored) {
        }
    }

    private static boolean deleteWithHelperFallback(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(path) || !Files.exists(path);
        } catch (IOException ignored) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleDelete(path);
            if (result.ok()) {
                markHelperScheduled();
                return true;
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message(), path, "delete");
            return false;
        }
    }

    private static boolean copyWithHelperFallback(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return false;
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            if (replaceExisting) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target);
            }
            return true;
        } catch (IOException ignored) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleCopy(source, target, replaceExisting);
            if (result.ok()) {
                markHelperScheduled();
                return true;
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message(), target, "copy");
            return false;
        }
    }

    private static boolean moveWithHelperFallback(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return false;
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
            return true;
        } catch (IOException ignored) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleMove(source, target, replaceExisting);
            if (result.ok()) {
                markHelperScheduled();
                return true;
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message(), target, "move");
            return false;
        }
    }

    private static void copyRecursive(Path source, Path target) {
        if (source == null || target == null) {
            return;
        }
        try {
            if (Files.isDirectory(source)) {
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path relative = source.relativize(dir);
                        try {
                            Files.createDirectories(target.resolve(relative));
                        } catch (IOException ignored) {
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relative = source.relativize(file);
                        try {
                            Files.createDirectories(target.resolve(relative).getParent());
                            copyWithHelperFallback(file, target.resolve(relative), true);
                        } catch (IOException ignored) {
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                copyWithHelperFallback(source, target, true);
            }
        } catch (IOException ignored) {
        }
    }

    private static void syncDirectory(Path source, Path target) {
        if (source == null || target == null) {
            return;
        }
        if (!Files.exists(target)) {
            copyRecursive(source, target);
            return;
        }
        deleteExtras(source, target);
        copyRecursive(source, target);
    }

    private static void deleteExtras(Path source, Path target) {
        if (source == null || target == null || !Files.exists(target)) {
            return;
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = target.relativize(file);
                    if (!Files.exists(source.resolve(relative))) {
                        forceDelete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (dir.equals(target)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relative = target.relativize(dir);
                    if (!Files.exists(source.resolve(relative))) {
                        forceDelete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void copyDirectory(Path source, Path target, boolean override) {
        if (source == null || target == null) {
            return;
        }
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(dir);
                    try {
                        Files.createDirectories(target.resolve(relative));
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(file);
                    Path dest = target.resolve(relative);
                    if (!override && Files.exists(dest)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        Files.createDirectories(dest.getParent());
                        copyWithHelperFallback(file, dest, true);
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String readJsonString(JsonObject obj, String key) {
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

    private static void writeString(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleWriteText(path, content == null ? "" : content);
            if (result.ok()) {
                markHelperScheduled();
                return;
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message(), path, "write");
        }
    }

    private static String replaceOccurrences(String content, String find, String replace, DlcUpdateManifest.WhichSpec which) {
        if (content == null || find == null || find.isEmpty()) {
            return content;
        }
        if (which == null || which.isAll()) {
            return content.replace(find, replace);
        }
        List<Integer> positions = new ArrayList<>();
        int index = 0;
        while ((index = content.indexOf(find, index)) != -1) {
            positions.add(index);
            index += find.length();
        }
        if (positions.isEmpty()) {
            return content;
        }
        if (which.isLast()) {
            int pos = positions.get(positions.size() - 1);
            return content.substring(0, pos) + replace + content.substring(pos + find.length());
        }
        Set<Integer> targetIndexes = new LinkedHashSet<>();
        for (Integer idx : which.getIndexes()) {
            if (idx == null) {
                continue;
            }
            if (idx <= 0) {
                continue;
            }
            int target = idx - 1;
            if (target >= 0 && target < positions.size()) {
                targetIndexes.add(target);
            }
        }
        if (targetIndexes.isEmpty()) {
            return content;
        }
        StringBuilder builder = new StringBuilder();
        int last = 0;
        for (int i = 0; i < positions.size(); i++) {
            int pos = positions.get(i);
            if (!targetIndexes.contains(i)) {
                continue;
            }
            builder.append(content, last, pos);
            builder.append(replace);
            last = pos + find.length();
        }
        builder.append(content.substring(last));
        return builder.toString();
    }

    private static String safeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    public static boolean isRollbackMarkerActive(String rollbackFrom, String currentBase, String currentDlc) {
        VersionState marker = parseVersionState(rollbackFrom, currentBase);
        if (marker == null) {
            return false;
        }
        if (marker.explicitBase) {
            return !stateEquals(marker.base, marker.dlc, currentBase, currentDlc);
        }
        return !safeVersion(marker.dlc).equals(safeVersion(currentDlc));
    }

    private static String sourceBaseVersion(DlcUpdateMeta meta) {
        return safeVersion(meta == null ? null : meta.getBaseOld());
    }

    private static String targetBaseVersion(DlcUpdateMeta meta) {
        if (meta == null) {
            return safeVersion(null);
        }
        String target = optionalVersion(meta.getBaseNew());
        return target == null ? sourceBaseVersion(meta) : safeVersion(target);
    }

    private static String sourceDlcVersion(DlcUpdateMeta meta) {
        return safeVersion(meta == null ? null : meta.getDlcOld());
    }

    private static String targetDlcVersion(DlcUpdateMeta meta) {
        if (meta == null) {
            return safeVersion(null);
        }
        String target = optionalVersion(meta.getDlcNew());
        return target == null ? sourceDlcVersion(meta) : safeVersion(target);
    }

    private static String metaPairKey(String baseVersion, String dlcVersion) {
        return safeVersion(baseVersion) + "+" + safeVersion(dlcVersion);
    }

    private static Path resolveMetaRootByPair(Path dlcRoot, String baseVersion, String dlcVersion) {
        return DlcTmpPaths.resolve(dlcRoot, metaPairKey(baseVersion, dlcVersion));
    }

    private static Path resolveMetaPath(Path metaRoot) {
        if (metaRoot == null) {
            return null;
        }
        Json5Util.migrateLegacy(metaRoot.resolve("meta.json"), UPDATE_GSON);
        Path metaPath = Json5Util.resolveConfigPath(metaRoot, "meta");
        return Json5Util.migrateLegacy(metaPath, UPDATE_GSON);
    }

    private static DlcUpdateMeta loadMetaFromRoot(Path metaRoot) {
        Path metaPath = resolveMetaPath(metaRoot);
        if (metaPath == null) {
            return null;
        }
        return DlcUpdateMeta.load(metaPath);
    }

    private static DlcUpdateMeta loadMetaForVersion(Path dlcRoot, String baseVersion, String dlcVersion) {
        if (dlcRoot == null || dlcVersion == null || dlcVersion.isBlank()) {
            return null;
        }
        String expectedBase = safeVersion(baseVersion);
        String expectedDlc = safeVersion(dlcVersion);
        Path pairRoot = resolveMetaRootByPair(dlcRoot, expectedBase, expectedDlc);
        DlcUpdateMeta meta = loadMetaFromRoot(pairRoot);
        if (meta != null && expectedBase.equals(targetBaseVersion(meta)) && expectedDlc.equals(targetDlcVersion(meta))) {
            return meta;
        }

        Path legacyRoot = DlcTmpPaths.resolve(dlcRoot, expectedDlc);
        if (!legacyRoot.equals(pairRoot)) {
            DlcUpdateMeta legacy = loadMetaFromRoot(legacyRoot);
            if (legacy != null) {
                if (expectedDlc.equals(targetDlcVersion(legacy)) && (baseVersion == null || baseVersion.isBlank() || expectedBase.equals(targetBaseVersion(legacy)))) {
                    return legacy;
                }
                if (meta == null) {
                    meta = legacy;
                }
            }
        }

        Path tmpRoot = DlcTmpPaths.root(dlcRoot);
        if (tmpRoot == null || !Files.isDirectory(tmpRoot)) {
            return meta;
        }
        DlcUpdateMeta dlcMatch = meta;
        long dlcMatchTs = parseTimestamp(meta);
        try (var stream = Files.list(tmpRoot)) {
            for (Path child : (Iterable<Path>) stream::iterator) {
                if (child == null || !Files.isDirectory(child)) {
                    continue;
                }
                DlcUpdateMeta candidate = loadMetaFromRoot(child);
                if (candidate == null || !expectedDlc.equals(targetDlcVersion(candidate))) {
                    continue;
                }
                long candidateTs = parseTimestamp(candidate);
                if (expectedBase.equals(targetBaseVersion(candidate))) {
                    if (dlcMatch == null || candidateTs >= dlcMatchTs) {
                        dlcMatch = candidate;
                        dlcMatchTs = candidateTs;
                    }
                    continue;
                }
                if (dlcMatch == null || candidateTs > dlcMatchTs) {
                    dlcMatch = candidate;
                    dlcMatchTs = candidateTs;
                }
            }
        } catch (IOException ignored) {
        }
        return dlcMatch;
    }

    private static long parseTimestamp(DlcUpdateMeta meta) {
        if (meta == null || meta.getTimestamp() == null || meta.getTimestamp().isBlank()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(meta.getTimestamp().trim());
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static String sourceBaseVersion(DlcUpdateManifest.VersionInfo info) {
        return info == null ? safeVersion(null) : safeVersion(info.getBase());
    }

    private static String versionPlatform(DlcUpdateManifest.VersionInfo info) {
        if (info == null) {
            return PLATFORM_UNIVERSAL;
        }
        String raw = info.getPlatform();
        if (raw == null || raw.isBlank()) {
            return PLATFORM_UNIVERSAL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (PLATFORM_CLIENT.equals(normalized) || PLATFORM_SERVER.equals(normalized) || PLATFORM_UNIVERSAL.equals(normalized)) {
            return normalized;
        }
        return PLATFORM_UNIVERSAL;
    }

    private static boolean isPlatformCompatible(DlcUpdateManifest.VersionInfo info) {
        return isPlatformCompatible(versionPlatform(info));
    }

    private static boolean isPackageCompatible(DlcUpdatePackage pkg) {
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return false;
        }
        return isPlatformCompatible(pkg.getManifest().getVersion());
    }

    private static boolean isPlatformCompatible(String platform) {
        String normalized = platform == null || platform.isBlank() ? PLATFORM_UNIVERSAL : platform.trim().toLowerCase(Locale.ROOT);
        if (PLATFORM_UNIVERSAL.equals(normalized)) {
            return true;
        }
        boolean dedicated = CrossPlatformRuntime.isDedicatedServer();
        if (dedicated) {
            return PLATFORM_SERVER.equals(normalized);
        }
        return PLATFORM_CLIENT.equals(normalized);
    }

    private static String targetBaseVersion(DlcUpdateManifest.VersionInfo info) {
        if (info == null) {
            return safeVersion(null);
        }
        String target = optionalVersion(info.getBaseNew());
        return target == null ? sourceBaseVersion(info) : target;
    }

    private static String sourceDlcVersion(DlcUpdateManifest.VersionInfo info) {
        return info == null ? safeVersion(null) : safeVersion(info.getDlcOld());
    }

    private static String targetDlcVersion(DlcUpdateManifest.VersionInfo info) {
        if (info == null) {
            return safeVersion(null);
        }
        String target = optionalVersion(info.getDlcNew());
        return target == null ? sourceDlcVersion(info) : target;
    }

    private static boolean hasVersionTransition(DlcUpdateManifest.VersionInfo info) {
        if (info == null) {
            return false;
        }
        return !sourceBaseVersion(info).equals(targetBaseVersion(info))
            || !sourceDlcVersion(info).equals(targetDlcVersion(info));
    }

    private static String stateKey(String base, String dlc) {
        return safeVersion(base) + "|" + safeVersion(dlc);
    }

    private static String optionalVersion(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
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

    private static VersionState parseVersionState(String raw, String defaultBase) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        int plus = normalized.indexOf('+');
        if (plus >= 0) {
            String base = normalized.substring(0, plus).trim();
            String dlc = normalized.substring(plus + 1).trim();
            return new VersionState(safeVersion(base), safeVersion(dlc), true);
        }
        return new VersionState(safeVersion(defaultBase), safeVersion(normalized), false);
    }

    private static boolean rollbackMarkerMatchesSource(String rollbackFrom, String sourceBase, String sourceDlc) {
        VersionState marker = parseVersionState(rollbackFrom, sourceBase);
        if (marker == null) {
            return false;
        }
        if (marker.explicitBase) {
            return stateEquals(marker.base, marker.dlc, sourceBase, sourceDlc);
        }
        return safeVersion(marker.dlc).equals(safeVersion(sourceDlc));
    }

    private static boolean stateMatchesTarget(String base, String dlc, VersionState target) {
        if (target == null) {
            return false;
        }
        if (!safeVersion(dlc).equals(target.dlc)) {
            return false;
        }
        if (!target.explicitBase) {
            return true;
        }
        return safeVersion(base).equals(target.base);
    }

    private static boolean stateEquals(String leftBase, String leftDlc, String rightBase, String rightDlc) {
        return safeVersion(leftBase).equals(safeVersion(rightBase))
            && safeVersion(leftDlc).equals(safeVersion(rightDlc));
    }

    private static final class VersionState {
        private final String base;
        private final String dlc;
        private final boolean explicitBase;

        private VersionState(String base, String dlc, boolean explicitBase) {
            this.base = safeVersion(base);
            this.dlc = safeVersion(dlc);
            this.explicitBase = explicitBase;
        }
    }

    private static final class UpdatePromptSpan {
        private String fromBase;
        private String fromDlc;
        private String toBase;
        private String toDlc;
    }

    private static int compareVersion(String a, String b) {
        ParsedVersion left = ParsedVersion.parse(a);
        ParsedVersion right = ParsedVersion.parse(b);
        int max = Math.max(left.numericParts.length, right.numericParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < left.numericParts.length ? left.numericParts[i] : 0;
            int r = i < right.numericParts.length ? right.numericParts[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        if (left.knownQualifier && right.knownQualifier) {
            if (left.qualifierRank != right.qualifierRank) {
                return Integer.compare(left.qualifierRank, right.qualifierRank);
            }
            if (left.qualifierNumber != right.qualifierNumber) {
                return Integer.compare(left.qualifierNumber, right.qualifierNumber);
            }
            int qualifierTextCompare = left.qualifierText.compareToIgnoreCase(right.qualifierText);
            if (qualifierTextCompare != 0) {
                return qualifierTextCompare;
            }
            return left.normalized.compareToIgnoreCase(right.normalized);
        }
        if (left.knownQualifier != right.knownQualifier) {
            return left.knownQualifier ? 1 : -1;
        }
        return left.normalized.compareToIgnoreCase(right.normalized);
    }

    public static int compareVersions(String a, String b) {
        return compareVersion(a, b);
    }

    private static int parseVersionPart(String part) {
        if (part == null) {
            return 0;
        }
        String normalized = part.trim();
        int idx = normalized.indexOf('-');
        if (idx >= 0) {
            normalized = normalized.substring(0, idx);
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class ParsedVersion {
        private static final int QUALIFIER_ALPHA = 0;
        private static final int QUALIFIER_BETA = 1;
        private static final int QUALIFIER_RC = 2;
        private static final int QUALIFIER_RELEASE = 3;
        private static final int QUALIFIER_HOTFIX = 4;
        private static final int QUALIFIER_BUILD = 5;

        private final String normalized;
        private final int[] numericParts;
        private final boolean knownQualifier;
        private final int qualifierRank;
        private final int qualifierNumber;
        private final String qualifierText;

        private ParsedVersion(String normalized, int[] numericParts, boolean knownQualifier, int qualifierRank, int qualifierNumber, String qualifierText) {
            this.normalized = normalized;
            this.numericParts = numericParts;
            this.knownQualifier = knownQualifier;
            this.qualifierRank = qualifierRank;
            this.qualifierNumber = qualifierNumber;
            this.qualifierText = qualifierText == null ? "" : qualifierText;
        }

        private static ParsedVersion parse(String value) {
            String normalized = safeVersion(value);
            int split = findSuffixStart(normalized);
            String core = split < 0 ? normalized : normalized.substring(0, split);
            String suffix = split < 0 ? "" : normalized.substring(split);
            int[] numericParts = parseNumericParts(core);
            Qualifier qualifier = parseQualifier(suffix);
            return new ParsedVersion(normalized, numericParts, qualifier.known, qualifier.rank, qualifier.number, qualifier.text);
        }

        private static int findSuffixStart(String value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if ((ch >= '0' && ch <= '9') || ch == '.') {
                    continue;
                }
                return i;
            }
            return -1;
        }

        private static int[] parseNumericParts(String core) {
            String[] parts = safeVersion(core).split("\\.");
            int[] parsed = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                parsed[i] = parseVersionPart(parts[i]);
            }
            return parsed;
        }

        private static Qualifier parseQualifier(String suffix) {
            if (suffix == null || suffix.isBlank()) {
                return new Qualifier(true, QUALIFIER_RELEASE, 0, "release");
            }
            String normalized = suffix.trim();
            while (!normalized.isEmpty()) {
                char ch = normalized.charAt(0);
                if (ch == '-' || ch == '_' || ch == '.') {
                    normalized = normalized.substring(1);
                    continue;
                }
                break;
            }
            if (normalized.isBlank()) {
                return new Qualifier(true, QUALIFIER_RELEASE, 0, "release");
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            for (String name : new String[] {"alpha", "beta", "rc", "hotfix", "fix", "build"}) {
                if (!lower.startsWith(name)) {
                    continue;
                }
                String rest = normalized.substring(name.length());
                int number = 0;
                if (!rest.isBlank()) {
                    rest = rest.trim();
                    while (!rest.isEmpty()) {
                        char ch = rest.charAt(0);
                        if (ch == '-' || ch == '_' || ch == '.') {
                            rest = rest.substring(1);
                            continue;
                        }
                        break;
                    }
                    if (!rest.isBlank()) {
                        StringBuilder digits = new StringBuilder();
                        int index = 0;
                        while (index < rest.length() && Character.isDigit(rest.charAt(index))) {
                            digits.append(rest.charAt(index));
                            index++;
                        }
                        if (digits.length() > 0) {
                            try {
                                number = Integer.parseInt(digits.toString());
                            } catch (NumberFormatException ignored) {
                                number = 0;
                            }
                        }
                    }
                }
                int rank = switch (name) {
                    case "alpha" -> QUALIFIER_ALPHA;
                    case "beta" -> QUALIFIER_BETA;
                    case "rc" -> QUALIFIER_RC;
                    case "hotfix", "fix" -> QUALIFIER_HOTFIX;
                    case "build" -> QUALIFIER_BUILD;
                    default -> QUALIFIER_RELEASE;
                };
                return new Qualifier(true, rank, number, "fix".equals(name) ? "hotfix" : name);
            }
            return new Qualifier(false, QUALIFIER_RELEASE, 0, normalized);
        }
    }

    private static final class Qualifier {
        private final boolean known;
        private final int rank;
        private final int number;
        private final String text;

        private Qualifier(boolean known, int rank, int number, String text) {
            this.known = known;
            this.rank = rank;
            this.number = number;
            this.text = text == null ? "" : text;
        }
    }

    private static Path getDlcRoot() {
        return DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
    }

    private static String toGameRelativePath(Path path) {
        if (path == null) {
            return "";
        }
        Path gameDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        try {
            Path gameReal = gameDir.toRealPath();
            Path pathReal = normalized.toRealPath();
            if (pathReal.startsWith(gameReal)) {
                return gameReal.relativize(pathReal).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        try {
            if (normalized.startsWith(gameDir)) {
                return gameDir.relativize(normalized).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return normalized.toString().replace('\\', '/');
    }

    private static Path resolveStoredPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of("");
        }
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return DlcPaths.gameDir().resolve(path).normalize();
    }

    private static class UpdateContext {
        private final Path dlcRoot;
        private final Path stagingRoot;
        private final Path backupRoot;
        private final DlcUpdateMeta meta;
        private boolean addedRequired;
        private boolean addedRemote;
        private boolean helperScheduled;
        private final List<String> failureMessages = new ArrayList<>();

        UpdateContext(Path dlcRoot, Path stagingRoot, Path backupRoot, DlcUpdateMeta meta) {
            this.dlcRoot = dlcRoot;
            this.stagingRoot = stagingRoot;
            this.backupRoot = backupRoot;
            this.meta = meta;
        }
    }

    private static void recordFileOpFailure(UpdateContext context, String operation, Path path) {
        if (context == null || path == null) {
            return;
        }
        String relative = toGameRelativePath(path);
        String label = relative == null || relative.isBlank() ? path.toString() : relative;
        recordFileOpFailure(context, "Failed to " + operation + ": " + label, path, operation);
    }

    private static void recordFileOpFailure(UpdateContext context, String message, Path path, String operation) {
        if (context == null) {
            return;
        }
        String finalMessage = message;
        if (finalMessage == null || finalMessage.isBlank()) {
            String relative = path == null ? "" : toGameRelativePath(path);
            String label = relative == null || relative.isBlank()
                ? (path == null ? "" : path.toString())
                : relative;
            finalMessage = "Failed to " + operation + ": " + label;
        }
        if (!context.failureMessages.contains(finalMessage)) {
            context.failureMessages.add(finalMessage);
        }
    }

    private static void markHelperScheduled() {
        UpdateContext context = CURRENT_UPDATE_CONTEXT.get();
        if (context != null) {
            context.helperScheduled = true;
        }
    }

    private static String latestFailureMessage(String fallback) {
        UpdateContext context = CURRENT_UPDATE_CONTEXT.get();
        if (context == null || context.failureMessages.isEmpty()) {
            return fallback;
        }
        return context.failureMessages.get(context.failureMessages.size() - 1);
    }

    private static String serializeJsonForPath(Path path, JsonObject obj) {
        if (obj == null) {
            return "";
        }
        JsonObject source = obj;
        if (layoutForPath(path) == DcConfigIO.Layout.DLC_STRUCTURED && isInfoPathForWrite(path)) {
            source = stripDownloadKeysForInfo(obj);
        }
        if (layoutForPath(path) == DcConfigIO.Layout.DLC_STRUCTURED && !isNoticeConfigPathForWrite(path)) {
            return DcFormatUtil.write(source);
        }
        return DcFormatUtil.writeGeneric(source);
    }

    private static boolean isInfoPathForWrite(Path path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/info/");
    }

    private static boolean isNoticeConfigPathForWrite(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String base = Json5Util.stripJsonExtension(path.getFileName().toString());
        return "notice".equalsIgnoreCase(base);
    }

    private static JsonObject stripDownloadKeysForInfo(JsonObject input) {
        JsonObject out = new JsonObject();
        if (input == null) {
            return out;
        }
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if ("modrinth_projectid".equalsIgnoreCase(key)
                || "modrinth_versionid".equalsIgnoreCase(key)
                || "curseforge_projectid".equalsIgnoreCase(key)
                || "curseforge_fileid".equalsIgnoreCase(key)
                || "directUrl".equalsIgnoreCase(key)
                || "priority".equalsIgnoreCase(key)) {
                continue;
            }
            out.add(key, entry.getValue());
        }
        return out;
    }

    private static class JsonObjectHolder {
        private final com.google.gson.JsonObject obj;

        private JsonObjectHolder(com.google.gson.JsonObject obj) {
            this.obj = obj;
        }

        static JsonObjectHolder load(Path path) {
            if (path == null || !Files.exists(path)) {
                return null;
            }
            com.google.gson.JsonObject obj = DcConfigIO.read(path, layoutForPath(path));
            if (obj == null) {
                return null;
            }
            return new JsonObjectHolder(obj);
        }

        void save(Path path) {
            if (path == null || obj == null) {
                return;
            }
            DcConfigIO.write(path, obj, layoutForPath(path));
        }
    }

    public static class UpdateCheckResult {
        private final DlcUpdateVersion current;
        private final List<DlcUpdatePackage> updates;

        UpdateCheckResult(DlcUpdateVersion current, List<DlcUpdatePackage> updates) {
            this.current = current;
            this.updates = updates == null ? List.of() : updates;
        }

        public DlcUpdateVersion getCurrent() {
            return current;
        }

        public List<DlcUpdatePackage> getUpdates() {
            return updates;
        }

        public boolean hasUpdates() {
            return updates != null && !updates.isEmpty();
        }
    }

    public static class UpdateApplyResult {
        private final boolean success;
        private final boolean missing;
        private final String message;
        private final List<String> missingPaths;
        private final boolean addedRequired;
        private final boolean addedRemote;
        private final boolean helperScheduled;
        private final boolean dlcManagerUpdated;

        private UpdateApplyResult(boolean success, boolean missing, String message, List<String> missingPaths,
            boolean addedRequired, boolean addedRemote, boolean helperScheduled, boolean dlcManagerUpdated) {
            this.success = success;
            this.missing = missing;
            this.message = message;
            this.missingPaths = missingPaths == null ? List.of() : missingPaths;
            this.addedRequired = addedRequired;
            this.addedRemote = addedRemote;
            this.helperScheduled = helperScheduled;
            this.dlcManagerUpdated = dlcManagerUpdated;
        }

        static UpdateApplyResult success(String message, boolean addedRequired, boolean addedRemote, boolean helperScheduled, boolean dlcManagerUpdated) {
            return new UpdateApplyResult(true, false, message, List.of(), addedRequired, addedRemote, helperScheduled, dlcManagerUpdated);
        }

        static UpdateApplyResult failed(String message) {
            return new UpdateApplyResult(false, false, message, List.of(), false, false, false, false);
        }

        static UpdateApplyResult missing(List<String> missingPaths) {
            return new UpdateApplyResult(false, true, "missing", missingPaths, false, false, false, false);
        }

        static UpdateApplyResult empty() {
            return new UpdateApplyResult(true, false, "empty", List.of(), false, false, false, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isMissing() {
            return missing;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getMissingPaths() {
            return missingPaths;
        }

        public boolean hasRequiredAdd() {
            return addedRequired;
        }

        public boolean hasRemoteAdd() {
            return addedRemote;
        }

        public boolean isHelperScheduled() {
            return helperScheduled;
        }

        public boolean isDlcManagerUpdated() {
            return dlcManagerUpdated;
        }
    }

    @FunctionalInterface
    public interface ApplyProgressListener {
        void onProgress(String status, float progress);
    }

    public static class UpdateRollbackResult {
        private final boolean success;
        private final String version;
        private final String message;

        private UpdateRollbackResult(boolean success, String version, String message) {
            this.success = success;
            this.version = version;
            this.message = message;
        }

        static UpdateRollbackResult success(String version) {
            return new UpdateRollbackResult(true, version, "ok");
        }

        static UpdateRollbackResult failed(String message) {
            return new UpdateRollbackResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getVersion() {
            return version;
        }

        public String getMessage() {
            return message;
        }
    }
}
