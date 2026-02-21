package com.cozooo.dlc_manager.client.update;
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
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.Json5Util;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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
        return resolveUpdateChainTo(version, packages, targetDlcNew, ignoreRollback);
    }

    public static List<DlcUpdatePackage> resolveUpdateChainTo(String targetDlcOld, String targetDlcNew, boolean ignoreRollback) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return List.of();
        }
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        return resolveUpdateChainTo(version, packages, targetDlcOld, targetDlcNew, ignoreRollback);
    }

    public static UpdateApplyResult applyUpdates(List<DlcUpdatePackage> packages, boolean forceMissing) {
        if (packages == null || packages.isEmpty()) {
            return UpdateApplyResult.empty();
        }
        Path dlcRoot = getDlcRoot();
        DlcUpdateVersion current = DlcUpdateVersion.load(dlcRoot);
        boolean addedRequired = false;
        boolean addedRemote = false;
        for (DlcUpdatePackage pkg : packages) {
            UpdateApplyResult result = applyUpdate(pkg, current, forceMissing);
            if (!result.success) {
                return result;
            }
            addedRequired = addedRequired || result.addedRequired;
            addedRemote = addedRemote || result.addedRemote;
            current = DlcUpdateVersion.load(dlcRoot);
        }
        clearUpdatePromptSkipMarker();
        return UpdateApplyResult.success("OK", addedRequired, addedRemote);
    }

    public static UpdateApplyResult applyUpdate(DlcUpdatePackage pkg, DlcUpdateVersion current, boolean forceMissing) {
        if (pkg == null || pkg.getManifest() == null) {
            return UpdateApplyResult.failed("Missing update.json5");
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
        Path backupRoot = DlcTmpPaths.resolve(dlcRoot, targetDlcVersion(info));
        Json5Util.migrateLegacy(backupRoot.resolve("meta.json"), UPDATE_GSON);
        Path metaPath = Json5Util.resolveConfigPath(backupRoot, "meta");

        UpdateContext context = new UpdateContext(dlcRoot, staging, backupRoot, meta);

        try {
            applyDeleteOps(manifest.getFileOps(), context);
            applyMoveOps(manifest.getFileOps(), context);
            applyAddOps(manifest.getFileOps(), context);
            applyModifyOps(manifest.getFileOps(), context, forceMissing);
            applyUpdateOps(manifest.getUpdateOps(), context);
            meta.save(metaPath);

            DlcUpdateVersion updated = new DlcUpdateVersion();
            String nextBase = safeVersion(current.getBase());
            if (baseNew != null) {
                nextBase = baseNew;
            }
            updated.setBase(nextBase);
            updated.setDlc(targetDlcVersion(info));
            updated.setRollbackFrom(null);
            updated.setChangelogShown(sourceDlcVersion(info));
            updated.save(dlcRoot);

            if (context.addedRemote) {
                DlcRemoteMarker.clearPartMarker(dlcRoot);
            }
            refreshPresetRuntimeState();

            pkg.cleanupStaging();
            return UpdateApplyResult.success("OK", context.addedRequired, context.addedRemote);
        } catch (Exception ex) {
            rollbackUpdate(context);
            pkg.cleanupStaging();
            return UpdateApplyResult.failed("Update failed");
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
        if (targetVersion.equals(current.getDlc())) {
            return UpdateRollbackResult.success(current.getDlc());
        }

        List<DlcUpdateMeta> chain = loadRollbackChain(dlcRoot, current.getDlc());
        if (chain.isEmpty()) {
            return UpdateRollbackResult.failed("No rollback data");
        }
        boolean targetFound = false;
        for (DlcUpdateMeta meta : chain) {
            if (meta != null && targetVersion.equals(meta.getDlcOld())) {
                targetFound = true;
                break;
            }
        }
        if (!targetFound) {
            return UpdateRollbackResult.failed("Target version not found");
        }
        String beforeRollback = current.getDlc();
        String working = current.getDlc();
        String workingBase = current.getBase();
        for (DlcUpdateMeta meta : chain) {
            if (meta == null) {
                break;
            }
            if (!working.equals(meta.getDlcNew())) {
                continue;
            }
            Path backupRoot = DlcTmpPaths.resolve(dlcRoot, meta.getDlcNew());
            UpdateContext context = new UpdateContext(dlcRoot, null, backupRoot, meta);
            rollbackUpdate(context);
            working = meta.getDlcOld();
            if (meta.getBaseOld() != null && !meta.getBaseOld().isBlank()) {
                workingBase = meta.getBaseOld();
            }
            if (working != null && working.equals(targetVersion)) {
                break;
            }
        }
        DlcUpdateVersion updated = new DlcUpdateVersion();
        updated.setBase(workingBase);
        updated.setDlc(working);
        updated.setRollbackFrom(beforeRollback);
        updated.save(dlcRoot);
        if (!targetVersion.equals(working)) {
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
        return loadRollbackChain(dlcRoot, version.getDlc());
    }

    public static List<DlcUpdateMeta> listUpdateHistoryFrom(String startVersion) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null || startVersion == null || startVersion.isBlank()) {
            return List.of();
        }
        return loadRollbackChain(dlcRoot, startVersion);
    }

    public static DlcUpdateMeta loadMetaForVersion(String dlcVersion) {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null || dlcVersion == null || dlcVersion.isBlank()) {
            return null;
        }
        Path metaRoot = DlcTmpPaths.resolve(dlcRoot, dlcVersion);
        Json5Util.migrateLegacy(metaRoot.resolve("meta.json"), UPDATE_GSON);
        Path metaPath = Json5Util.resolveConfigPath(metaRoot, "meta");
        return DlcUpdateMeta.load(metaPath);
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
                builder.append(sourceDlcVersion(info)).append(" -> ").append(targetDlcVersion(info)).append("\n");
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
                Files.copy(zipPath, target, StandardCopyOption.REPLACE_EXISTING);
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
        try {
            Files.deleteIfExists(skip);
        } catch (IOException ignored) {
        }
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
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
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
                return info != null && version.getRollbackFrom().equals(sourceDlcVersion(info));
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
        String targetDlcNew, boolean ignoreRollback) {
        if (version == null || packages == null || packages.isEmpty()) {
            return List.of();
        }
        String target = safeVersion(targetDlcNew);
        if (target.isEmpty()) {
            return List.of();
        }
        String startBase = safeVersion(version.getBase());
        String startDlc = safeVersion(version.getDlc());
        if (startDlc.equals(target)) {
            return List.of();
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom().isBlank()) {
            boolean hasNewer = packages.stream().anyMatch(pkg -> {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                return info != null && version.getRollbackFrom().equals(sourceDlcVersion(info));
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
            if (node.dlc.equals(target)) {
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
        String targetDlcOld, String targetDlcNew, boolean ignoreRollback) {
        if (version == null || packages == null || packages.isEmpty()) {
            return List.of();
        }
        String targetOld = safeVersion(targetDlcOld);
        String targetNew = optionalVersion(targetDlcNew);
        if (targetNew == null) {
            targetNew = targetOld;
        }
        if (targetOld.isEmpty()) {
            return List.of();
        }
        DlcUpdatePackage targetPkg = null;
        String base = safeVersion(version.getBase());
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
                targetPkg = pkg;
                break;
            }
        }
        if (targetPkg == null) {
            return List.of();
        }
        String currentBase = safeVersion(version.getBase());
        String currentDlc = safeVersion(version.getDlc());
        if (currentDlc.equals(targetOld) && currentBase.equals(sourceBaseVersion(targetPkg.getManifest().getVersion()))) {
            return List.of(targetPkg);
        }
        List<DlcUpdatePackage> prefix = resolveUpdateChainTo(version, packages, targetOld, ignoreRollback);
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
                Files.createDirectories(to.getParent());
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
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
            Files.createDirectories(actualTarget.getParent());
            Files.copy(source, actualTarget, StandardCopyOption.REPLACE_EXISTING);
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
    }

    private static void applyRemoteUpdateOps(Map<String, DlcUpdateManifest.UpdateEntry> updates, UpdateContext context)
        throws IOException, InterruptedException {
        Map<String, DlcRemoteEntry> entries = loadRemoteEntries(context.dlcRoot);
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
            if (context != null) {
                context.addedRemote = true;
            }
            boolean wasEnabled = isRemoteEnabled(entry);
            Path oldTarget = entry.getTargetPath();
            if (wasEnabled && oldTarget != null) {
                uninstallDlcPath(oldTarget, context.dlcRoot, context);
            }
            if (oldTarget != null && Files.exists(oldTarget)) {
                backupAndDelete(oldTarget, context);
            }
            DlcUpdateManifest.UpdateEntry updateEntry = update.getValue();
            Path configPath = entry.getConfigPath();
            if (configPath != null && Files.exists(configPath)) {
                backupPath(toGameRelativePath(configPath), configPath, context);
                updateRemoteConfigFields(configPath, updateEntry);
                entry = DlcRemoteLoader.loadSingle(configPath, context.dlcRoot);
                if (entry == null) {
                    continue;
                }
            }
            DownloadedFile downloaded = downloadRemoteEntry(entry);
            if (downloaded == null) {
                throw new IOException("remote update download failed");
            }
            if (context != null && downloaded.path != null) {
                String created = toGameRelativePath(downloaded.path);
                if (created != null && !created.isBlank() && !context.meta.getCreatedPaths().contains(created)) {
                    context.meta.getCreatedPaths().add(created);
                }
            }
            if (!entry.isFileNameExplicit()) {
                updateRemoteFileName(configPath, downloaded.fileName);
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
            String oldFileName = resolveRequiredFileName(entry, context.dlcRoot);
            List<AppliedTarget> appliedTargets = collectExistingRequiredTargets(entry, oldFileName);
            boolean wasApplied = !appliedTargets.isEmpty();
            if (wasApplied) {
                for (AppliedTarget target : appliedTargets) {
                    Path resolved = target.directory
                        ? (oldFileName == null ? null : target.path.resolve(oldFileName))
                        : target.path;
                    if (resolved != null) {
                        backupAndDelete(resolved, context);
                    }
                }
            }
            if (oldFileName != null) {
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
                entry = DlcRequiredLoader.loadSingle(configPath, context.dlcRoot);
                if (entry == null) {
                    continue;
                }
            }
            DownloadedFile downloaded = downloadRequiredEntry(entry);
            if (downloaded == null) {
                throw new IOException("required update download failed");
            }
            if (context != null && downloaded.path != null) {
                String created = toGameRelativePath(downloaded.path);
                if (created != null && !created.isBlank() && !context.meta.getCreatedPaths().contains(created)) {
                    context.meta.getCreatedPaths().add(created);
                }
            }
            if (entry.getRequiredFileName() == null || entry.getRequiredFileName().isBlank()) {
                updateRequiredFileName(configPath, downloaded.fileName);
            }
            if (wasApplied) {
                applyRequiredTargets(downloaded.path, downloaded.fileName, appliedTargets, context);
            }
        }
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
                if ("notice.json".equalsIgnoreCase(fileName) || "notice.json5".equalsIgnoreCase(fileName)) {
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
        writeJson(configPath, obj);
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

    private static JsonObject readJson(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            return UPDATE_GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void writeJson(Path path, JsonObject obj) {
        if (path == null || obj == null) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            UPDATE_GSON.toJson(obj, writer);
        } catch (IOException ignored) {
        }
    }

    private static DownloadedFile downloadRemoteEntry(DlcRemoteEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        List<DownloadCandidate> candidates = buildRemoteCandidates(entry);
        if (candidates.isEmpty()) {
            throw new IOException("missing download url");
        }
        return downloadWithRetry(entry, candidates);
    }

    private static DownloadedFile downloadRequiredEntry(DlcRequiredEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        List<DownloadCandidate> candidates = buildRequiredCandidates(entry);
        if (candidates.isEmpty()) {
            throw new IOException("missing download url");
        }
        return downloadWithRetry(entry, candidates);
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

    private static DownloadedFile downloadWithRetry(Object entry, List<DownloadCandidate> candidates)
        throws IOException {
        int maxRetry = Math.max(1, DlcPathConfig.getMaxRetryCount());
        IOException lastError = null;
        for (DownloadCandidate candidate : candidates) {
            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                try {
                    return downloadCandidate(entry, candidate);
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

    private static DownloadedFile downloadCandidate(Object entry, DownloadCandidate candidate) throws IOException {
        String fileName = null;
        boolean fileNameExplicit = false;
        if (entry instanceof DlcRemoteEntry remote) {
            fileName = remote.getFileNameHint();
            fileNameExplicit = remote.isFileNameExplicit();
        } else if (entry instanceof DlcRequiredEntry required) {
            fileName = required.getRequiredFileName();
            fileNameExplicit = fileName != null && !fileName.isBlank();
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
        Files.deleteIfExists(tempFile);
        downloadToFile(candidate.url, tempFile);
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        return new DownloadedFile(target, fileName);
    }

    private static void downloadToFile(String url, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("invalid target path");
        }
        Path downloaded = DlcDownloadService.download(url, parent, target.getFileName().toString(), null, null);
        if (!downloaded.equals(target)) {
            Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
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
                    Files.copy(source, resolved, StandardCopyOption.REPLACE_EXISTING);
                    targets.add(resolved);
                } else {
                    Files.createDirectories(target.getParent());
                    backupOrCreate(target, context);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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
                Files.createDirectories(target.getParent());
                backupOrCreate(target, context);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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

    private static List<DlcUpdateMeta> loadRollbackChain(Path dlcRoot, String currentVersion) {
        if (dlcRoot == null || currentVersion == null) {
            return List.of();
        }
        List<DlcUpdateMeta> chain = new ArrayList<>();
        String working = currentVersion;
        for (int i = 0; i < 32; i++) {
            Path metaRoot = DlcTmpPaths.resolve(dlcRoot, working);
            Json5Util.migrateLegacy(metaRoot.resolve("meta.json"), UPDATE_GSON);
            Path metaPath = Json5Util.resolveConfigPath(metaRoot, "meta");
            DlcUpdateMeta meta = DlcUpdateMeta.load(metaPath);
            if (meta == null || meta.getDlcOld() == null || meta.getDlcOld().isBlank()) {
                break;
            }
            chain.add(meta);
            if (meta.getDlcOld().equals(working)) {
                break;
            }
            working = meta.getDlcOld();
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
        Path json5 = infoDir.resolve("version" + Json5Util.JSON5_EXTENSION).normalize();
        Path json = infoDir.resolve("version.json").normalize();
        Path normalized = path.normalize();
        return normalized.equals(json5) || normalized.equals(json);
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
        try {
            Files.deleteIfExists(path);
            return;
        } catch (IOException ignored) {
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
        try {
            Files.deleteIfExists(path);
            return;
        } catch (IOException ignored) {
        }
        try {
            path.toFile().deleteOnExit();
        } catch (Exception ignored) {
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
                            Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
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
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
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
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
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

    private static final class UpdatePromptSpan {
        private String fromBase;
        private String fromDlc;
        private String toBase;
        private String toDlc;
    }

    private static int compareVersion(String a, String b) {
        String[] left = safeVersion(a).split("\\.");
        String[] right = safeVersion(b).split("\\.");
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            int l = parseVersionPart(i < left.length ? left[i] : "0");
            int r = parseVersionPart(i < right.length ? right[i] : "0");
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return safeVersion(a).compareToIgnoreCase(safeVersion(b));
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

        UpdateContext(Path dlcRoot, Path stagingRoot, Path backupRoot, DlcUpdateMeta meta) {
            this.dlcRoot = dlcRoot;
            this.stagingRoot = stagingRoot;
            this.backupRoot = backupRoot;
            this.meta = meta;
        }
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
            com.google.gson.JsonObject obj = Json5Util.readObject(path);
            if (obj == null) {
                return null;
            }
            return new JsonObjectHolder(obj);
        }

        void save(Path path) {
            if (path == null || obj == null) {
                return;
            }
            try (var writer = Files.newBufferedWriter(path)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
            } catch (IOException ignored) {
            }
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

        private UpdateApplyResult(boolean success, boolean missing, String message, List<String> missingPaths,
            boolean addedRequired, boolean addedRemote) {
            this.success = success;
            this.missing = missing;
            this.message = message;
            this.missingPaths = missingPaths == null ? List.of() : missingPaths;
            this.addedRequired = addedRequired;
            this.addedRemote = addedRemote;
        }

        static UpdateApplyResult success(String message, boolean addedRequired, boolean addedRemote) {
            return new UpdateApplyResult(true, false, message, List.of(), addedRequired, addedRemote);
        }

        static UpdateApplyResult failed(String message) {
            return new UpdateApplyResult(false, false, message, List.of(), false, false);
        }

        static UpdateApplyResult missing(List<String> missingPaths) {
            return new UpdateApplyResult(false, true, "missing", missingPaths, false, false);
        }

        static UpdateApplyResult empty() {
            return new UpdateApplyResult(true, false, "empty", List.of(), false, false);
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
