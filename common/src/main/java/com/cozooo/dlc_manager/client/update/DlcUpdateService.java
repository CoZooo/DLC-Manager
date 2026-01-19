package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcUpdateService {
    private DlcUpdateService() {
    }

    public static UpdateCheckResult checkForUpdates() {
        Path dlcRoot = getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        List<DlcUpdatePackage> packages = scanUpdatePackages(dlcRoot);
        List<DlcUpdatePackage> applicable = resolveUpdateChain(version, packages, false);
        return new UpdateCheckResult(version, applicable);
    }

    public static UpdateCheckResult checkForUpdates(boolean ignoreRollback) {
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
        String targetNew = safeVersion(dlcNew);
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (!base.equals(safeVersion(info.getBase()))) {
                continue;
            }
            if (targetOld.equals(safeVersion(info.getDlcOld())) && targetNew.equals(safeVersion(info.getDlcNew()))) {
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
        return UpdateApplyResult.success("OK", addedRequired, addedRemote);
    }

    public static UpdateApplyResult applyUpdate(DlcUpdatePackage pkg, DlcUpdateVersion current, boolean forceMissing) {
        if (pkg == null || pkg.getManifest() == null) {
            return UpdateApplyResult.failed("Missing update.json");
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
        meta.setDlcOld(info.getDlcOld());
        meta.setDlcNew(info.getDlcNew());
        meta.setTimestamp(Long.toString(System.currentTimeMillis()));
        Path backupRoot = dlcRoot.resolve("update_tmp").resolve(info.getDlcNew() == null ? "unknown" : info.getDlcNew());
        Path metaPath = backupRoot.resolve("meta.json");

        UpdateContext context = new UpdateContext(dlcRoot, staging, backupRoot, meta);

        try {
            applyDeleteOps(manifest.getFileOps(), context);
            applyAddOps(manifest.getFileOps(), context);
            applyModifyOps(manifest.getFileOps(), context, forceMissing);
            meta.save(metaPath);

            DlcUpdateVersion updated = new DlcUpdateVersion();
            updated.setBase(safeVersion(current.getBase()));
            updated.setDlc(safeVersion(info.getDlcNew()));
            updated.setRollbackFrom(null);
            updated.save(dlcRoot);

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
        for (DlcUpdateMeta meta : chain) {
            if (meta == null) {
                break;
            }
            if (!working.equals(meta.getDlcNew())) {
                continue;
            }
            Path backupRoot = dlcRoot.resolve("update_tmp").resolve(meta.getDlcNew());
            UpdateContext context = new UpdateContext(dlcRoot, null, backupRoot, meta);
            rollbackUpdate(context);
            working = meta.getDlcOld();
            if (working != null && working.equals(targetVersion)) {
                break;
            }
        }
        DlcUpdateVersion updated = new DlcUpdateVersion();
        updated.setBase(current.getBase());
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

    public static Path getUpdateDir() {
        Path dlcRoot = getDlcRoot();
        return dlcRoot == null ? null : dlcRoot.resolve("update");
    }

    public static void ensureUpdateDir() {
        Path updateDir = getUpdateDir();
        if (updateDir == null) {
            return;
        }
        try {
            Files.createDirectories(updateDir);
        } catch (IOException ignored) {
            // Best-effort.
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
        return safeVersion(current.getBase()).equals(safeVersion(info.getBase()))
            && safeVersion(current.getDlc()).equals(safeVersion(info.getDlcOld()));
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

    public static void removeSkipMarkerIfNeeded(List<Path> addedRequiredFiles) {
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
            // Best-effort.
        }
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
                    if (pkg != null) {
                        packages.add(pkg);
                    }
                });
        } catch (IOException ignored) {
            // Best-effort.
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
            String key = safeVersion(info.getBase()) + "|" + safeVersion(info.getDlcOld()) + "|" + safeVersion(info.getDlcNew());
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
                            // Best-effort.
                        }
                    }
                });
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private static boolean sameVersion(DlcUpdateManifest.VersionInfo left, DlcUpdateManifest.VersionInfo right) {
        if (left == null || right == null) {
            return false;
        }
        return safeVersion(left.getBase()).equals(safeVersion(right.getBase()))
            && safeVersion(left.getDlcOld()).equals(safeVersion(right.getDlcOld()))
            && safeVersion(left.getDlcNew()).equals(safeVersion(right.getDlcNew()));
    }

    private static List<DlcUpdatePackage> resolveUpdateChain(DlcUpdateVersion version, List<DlcUpdatePackage> packages,
        boolean ignoreRollback) {
        if (version == null || packages == null || packages.isEmpty()) {
            return List.of();
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom().isBlank()) {
            boolean hasNewer = packages.stream().anyMatch(pkg -> {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                return info != null && version.getRollbackFrom().equals(info.getDlcOld());
            });
            if (!hasNewer) {
                return List.of();
            }
        }
        List<DlcUpdatePackage> remaining = new ArrayList<>(packages);
        List<DlcUpdatePackage> chain = new ArrayList<>();
        String current = version.getDlc();
        while (true) {
            List<DlcUpdatePackage> candidates = new ArrayList<>();
            for (DlcUpdatePackage pkg : remaining) {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                if (info == null) {
                    continue;
                }
                if (!safeVersion(version.getBase()).equals(safeVersion(info.getBase()))) {
                    continue;
                }
                if (safeVersion(current).equals(safeVersion(info.getDlcOld()))) {
                    candidates.add(pkg);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            candidates.sort(Comparator.comparing(pkg -> safeVersion(pkg.getManifest().getVersion().getDlcNew()), DlcUpdateService::compareVersion));
            DlcUpdatePackage chosen = candidates.get(0);
            chain.add(chosen);
            remaining.remove(chosen);
            current = chosen.getManifest().getVersion().getDlcNew();
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
        String start = safeVersion(version.getDlc());
        if (start.equals(target)) {
            return List.of();
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom().isBlank()) {
            boolean hasNewer = packages.stream().anyMatch(pkg -> {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                return info != null && version.getRollbackFrom().equals(info.getDlcOld());
            });
            if (!hasNewer) {
                return List.of();
            }
        }
        List<DlcUpdatePackage> filtered = new ArrayList<>();
        String base = safeVersion(version.getBase());
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (base.equals(safeVersion(info.getBase()))) {
                filtered.add(pkg);
            }
        }
        if (filtered.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, List<DlcUpdatePackage>> byOld = new java.util.HashMap<>();
        for (DlcUpdatePackage pkg : filtered) {
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            String oldV = safeVersion(info.getDlcOld());
            byOld.computeIfAbsent(oldV, k -> new ArrayList<>()).add(pkg);
        }
        java.util.ArrayDeque<PathNode> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        queue.add(new PathNode(start, new ArrayList<>()));
        visited.add(start);
        while (!queue.isEmpty()) {
            PathNode node = queue.poll();
            if (node.version.equals(target)) {
                return node.path;
            }
            List<DlcUpdatePackage> nextPkgs = byOld.get(node.version);
            if (nextPkgs == null || nextPkgs.isEmpty()) {
                continue;
            }
            for (DlcUpdatePackage pkg : nextPkgs) {
                DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                String next = safeVersion(info.getDlcNew());
                if (visited.contains(next)) {
                    continue;
                }
                List<DlcUpdatePackage> nextPath = new ArrayList<>(node.path);
                nextPath.add(pkg);
                visited.add(next);
                queue.add(new PathNode(next, nextPath));
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
        String targetNew = safeVersion(targetDlcNew);
        if (targetOld.isEmpty() || targetNew.isEmpty()) {
            return List.of();
        }
        DlcUpdatePackage targetPkg = null;
        String base = safeVersion(version.getBase());
        for (DlcUpdatePackage pkg : packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue;
            }
            DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
            if (!base.equals(safeVersion(info.getBase()))) {
                continue;
            }
            if (targetOld.equals(safeVersion(info.getDlcOld())) && targetNew.equals(safeVersion(info.getDlcNew()))) {
                targetPkg = pkg;
                break;
            }
        }
        if (targetPkg == null) {
            return List.of();
        }
        String current = safeVersion(version.getDlc());
        if (current.equals(targetOld)) {
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
        private final String version;
        private final List<DlcUpdatePackage> path;

        private PathNode(String version, List<DlcUpdatePackage> path) {
            this.version = version;
            this.path = path;
        }
    }

    private static boolean matchesVersion(DlcUpdateVersion current, DlcUpdateManifest.VersionInfo info) {
        if (current == null || info == null) {
            return false;
        }
        String base = safeVersion(current.getBase());
        String dlc = safeVersion(current.getDlc());
        return base.equals(safeVersion(info.getBase()))
            && dlc.equals(safeVersion(info.getDlcOld()));
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
        removeSkipMarkerIfNeeded(addedRequiredFiles);
        if (!addedRequiredFiles.isEmpty()) {
            context.addedRequired = true;
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
            // Best-effort.
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
        uninstallDlcPath(target, dlcRoot, context);
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
                if (file != null && !file.getFileName().toString().endsWith(".json")) {
                    return file.getFileName().toString();
                }
            }
        } catch (IOException ignored) {
            // Best-effort.
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
                    } else if (target.getFileName() != null && target.getFileName().toString().endsWith(".json")) {
                        uninstallRequiredConfig(target, dlcRoot, null);
                    }
                } else if (isUnderDir(target, remoteRoot)) {
                    if (Files.isDirectory(target)) {
                        uninstallConfigsInDir(target, dlcRoot, false);
                    } else if (target.getFileName() != null && target.getFileName().toString().endsWith(".json")) {
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
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    if (required) {
                        uninstallRequiredConfig(path, dlcRoot, null);
                    } else {
                        uninstallRemoteConfig(path, dlcRoot, null);
                    }
                });
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private static List<DlcUpdateMeta> loadRollbackChain(Path dlcRoot, String currentVersion) {
        if (dlcRoot == null || currentVersion == null) {
            return List.of();
        }
        List<DlcUpdateMeta> chain = new ArrayList<>();
        String working = currentVersion;
        for (int i = 0; i < 32; i++) {
            Path metaPath = dlcRoot.resolve("update_tmp").resolve(working).resolve("meta.json");
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
        Path notice = dlcRoot.resolve("info").resolve("notice.json").normalize();
        return path.normalize().equals(notice);
    }

    private static boolean isVersionPath(Path path, Path dlcRoot) {
        if (path == null || dlcRoot == null) {
            return false;
        }
        return path.normalize().equals(dlcRoot.resolve("info").resolve("version.json").normalize());
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
            // Best-effort delete.
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
            // Retry below.
        }
        try {
            Files.setAttribute(path, "dos:readonly", false);
        } catch (Exception ignored) {
            // Ignore if not supported.
        }
        try {
            java.io.File file = path.toFile();
            file.setWritable(true);
            file.setReadable(true);
            file.setExecutable(true, false);
        } catch (Exception ignored) {
            // Best-effort.
        }
        try {
            Files.deleteIfExists(path);
            return;
        } catch (IOException ignored) {
            // Fall back to deleteOnExit.
        }
        try {
            path.toFile().deleteOnExit();
        } catch (Exception ignored) {
            // Best-effort.
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
                            // Best-effort.
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
                            // Best-effort.
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
            // Best-effort copy.
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
            // Best-effort sync.
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
                        // Best-effort.
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
                        // Best-effort.
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best-effort copy.
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void writeString(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort.
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
            // Best-effort.
        }
        try {
            if (normalized.startsWith(gameDir)) {
                return gameDir.relativize(normalized).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // Best-effort.
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
            try (var reader = Files.newBufferedReader(path)) {
                com.google.gson.JsonObject obj = new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .fromJson(reader, com.google.gson.JsonObject.class);
                if (obj == null) {
                    return null;
                }
                return new JsonObjectHolder(obj);
            } catch (IOException ignored) {
                return null;
            }
        }

        void save(Path path) {
            if (path == null || obj == null) {
                return;
            }
            try (var writer = Files.newBufferedWriter(path)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
            } catch (IOException ignored) {
                // Best-effort.
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
