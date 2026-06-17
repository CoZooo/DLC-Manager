package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcPathUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class DlcPackUpdateCoordinator {
    private final Path instanceRoot;
    private final Path dlcRoot;
    private final DlcPackInstaller installer;

    public DlcPackUpdateCoordinator(Path instanceRoot, Path dlcRoot) {
        this.instanceRoot = instanceRoot;
        this.dlcRoot = dlcRoot;
        this.installer = new DlcPackInstaller(instanceRoot, dlcRoot);
    }

    public DlcPackUpdateGuard.CheckResult check(String targetBase, String targetDlc) {
        return DlcPackUpdateGuard.check(dlcRoot, targetBase, targetDlc);
    }

    public List<DlcPackInstaller.UninstallReport> uninstallIncompatibleForCurrentVersion() throws IOException {
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        DlcPackUpdateGuard.CheckResult check = check(
            version == null ? null : version.getBase(),
            version == null ? null : version.getDlc()
        );
        if (check == null || !check.hasIncompatiblePacks()) {
            return List.of();
        }
        List<DlcPackInstaller.UninstallReport> reports = new ArrayList<>();
        IOException failure = null;
        for (DlcPackUpdateGuard.PackStatus status : check.incompatiblePacks) {
            if (status == null || status.meta == null) {
                continue;
            }
            try {
                reports.add(installer.uninstall(status.meta.identifier));
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return reports;
    }

    public UpdateSession prepareForUpdate(String targetBase, String targetDlc, boolean uninstallIncompatible) throws IOException {
        DlcPackUpdateGuard.CheckResult check = check(targetBase, targetDlc);
        return prepareForUpdate(check, uninstallIncompatible);
    }

    public UpdateSession prepareForUpdate(DlcPackUpdateGuard.CheckResult check, boolean uninstallIncompatible) throws IOException {
        if (check == null || !check.hasInstalledPacks()) {
            return new UpdateSession(List.of());
        }
        if (check.hasIncompatiblePacks() && !uninstallIncompatible) {
            throw new IOException("DLC Pack is incompatible with target modpack version");
        }
        List<Snapshot> snapshots = new ArrayList<>();
        try {
            for (DlcPackUpdateGuard.PackStatus status : check.packs) {
                if (status == null || status.meta == null) {
                    throw new IOException("DLC Pack meta is missing: " + (status == null ? "" : status.identifier()));
                }
                snapshots.add(snapshot(status));
            }
            for (Snapshot snapshot : snapshots) {
                installer.uninstall(snapshot.meta.identifier);
                snapshot.uninstalled = true;
            }
            return new UpdateSession(snapshots);
        } catch (Throwable error) {
            restoreSnapshots(snapshots.stream().filter(snapshot -> snapshot.uninstalled).toList(), true);
            snapshots.forEach(snapshot -> DlcZipUtil.deleteRecursive(snapshot.staging));
            if (error instanceof IOException io) {
                throw io;
            }
            throw new IOException("Failed to prepare DLC Packs for update", error);
        }
    }

    private Snapshot snapshot(DlcPackUpdateGuard.PackStatus status) throws IOException {
        String identifier = status.meta.identifier;
        Path stagingRoot = dlcRoot.resolve("tmp").resolve("_staging");
        Path staging = DlcZipUtil.createUniqueDir(stagingRoot, identifier);
        Path packDirSnapshot = staging.resolve("pack_dir");
        Path installedSnapshot = staging.resolve("installed");
        copyRecursive(status.packDir, packDirSnapshot);
        for (String relative : status.meta.installedPaths) {
            Path source = resolveInside(instanceRoot, relative);
            if (!Files.exists(source)) {
                continue;
            }
            Path target = resolveInside(installedSnapshot, relative);
            copyRecursive(source, target);
        }
        DlcPackTransaction txn = DlcPackTransaction.running(identifier, staging.getFileName().toString());
        txn.replacement = true;
        txn.save(staging.resolve("txn.dc"));
        return new Snapshot(status.meta, status.compatible, staging, packDirSnapshot, installedSnapshot);
    }

    private void restoreSnapshots(List<Snapshot> snapshots, boolean includeIncompatible) throws IOException {
        IOException failure = null;
        for (Snapshot snapshot : snapshots) {
            if (snapshot == null || (!includeIncompatible && !snapshot.compatible)) {
                continue;
            }
            try {
                restoreSnapshot(snapshot);
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void restoreSnapshot(Snapshot snapshot) throws IOException {
        Path packDir = installer.packDir(snapshot.meta.identifier);
        DlcZipUtil.deleteRecursive(packDir);
        copyRecursive(snapshot.packDirSnapshot, packDir);
        DlcPackMetadata metadata = readArchivedMetadata(packDir);
        if (metadata != null) {
            moveConfiguredConflicts(metadata.conflicts, packDir);
        }
        for (String relative : snapshot.meta.installedPaths) {
            Path source = resolveInside(snapshot.installedSnapshot, relative);
            if (!Files.exists(source)) {
                continue;
            }
            Path target = resolveInside(instanceRoot, relative);
            if (Files.exists(target)) {
                Path displaced = resolveInside(packDir, relative);
                Files.createDirectories(displaced.getParent());
                Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING);
            }
            copyRecursive(source, target);
        }
    }

    private DlcPackMetadata readArchivedMetadata(Path packDir) {
        Path metadataPath = packDir.resolve("metadata").resolve("pack.dc");
        if (!Files.isRegularFile(metadataPath)) {
            return null;
        }
        DlcPackMetadata.Result result = DlcPackMetadata.read(metadataPath);
        return result.valid ? result.metadata : null;
    }

    private void moveConfiguredConflicts(List<String> conflicts, Path packDir) throws IOException {
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }
        for (String conflict : conflicts) {
            String relative = DlcPackMeta.normalizeRelativePath(conflict);
            if (relative == null) {
                continue;
            }
            if (DlcPackInstallPlan.isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                continue;
            }
            if (isPackRecordPath(relative)) {
                continue;
            }
            List<Path> targets = DlcPathUtil.findMatches(instanceRoot, relative);
            if (targets.isEmpty()) {
                continue;
            }
            for (Path target : targets) {
                String targetRelative = DlcPackMeta.normalizeRelativePath(
                    instanceRoot.toAbsolutePath().normalize()
                        .relativize(target.toAbsolutePath().normalize())
                        .toString()
                );
                if (targetRelative == null) {
                    continue;
                }
                Path displaced = resolveInside(packDir, targetRelative);
                Files.createDirectories(displaced.getParent());
                Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path resolveInside(Path root, String relative) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String normalizedRelative = DlcPackMeta.normalizeRelativePath(relative);
        if (normalizedRelative == null) {
            throw new IOException("Invalid relative path: " + relative);
        }
        Path target = normalizedRoot.resolve(normalizedRelative).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes root: " + relative);
        }
        return target;
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        if (source == null || target == null || !Files.exists(source)) {
            return;
        }
        if (Files.isRegularFile(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private boolean isPackRecordPath(String relative) {
        String normalized = relative.toLowerCase(Locale.ROOT);
        return normalized.equals("meta.dc")
            || normalized.startsWith("metadata/")
            || normalized.startsWith("cf_cache/");
    }

    public final class UpdateSession {
        private final List<Snapshot> snapshots;

        private UpdateSession(List<Snapshot> snapshots) {
            this.snapshots = List.copyOf(snapshots == null ? List.of() : snapshots);
        }

        public boolean hasPacks() {
            return !snapshots.isEmpty();
        }

        public boolean hasIncompatiblePacks() {
            return snapshots.stream().anyMatch(snapshot -> !snapshot.compatible);
        }

        public List<String> identifiers() {
            return snapshots.stream().map(snapshot -> snapshot.meta.identifier).toList();
        }

        public void finishAfterSuccessfulUpdate() throws IOException {
            markCompleted(snapshots.stream().filter(snapshot -> !snapshot.compatible).toList());
            restoreSnapshots(snapshots, false);
            markCompleted(snapshots.stream().filter(snapshot -> snapshot.compatible).toList());
            cleanup();
        }

        public void restoreAfterFailedUpdate() throws IOException {
            restoreSnapshots(snapshots, true);
            markCompleted(snapshots);
            cleanup();
        }

        private void markCompleted(List<Snapshot> completed) {
            for (Snapshot snapshot : completed) {
                DlcPackTransaction txn = DlcPackTransaction.load(snapshot.staging.resolve("txn.dc"));
                if (txn == null) {
                    continue;
                }
                txn.status = DlcPackTransaction.Status.COMMITTED;
                txn.save(snapshot.staging.resolve("txn.dc"));
            }
        }

        public void cleanup() {
            for (Snapshot snapshot : snapshots) {
                DlcZipUtil.deleteRecursive(snapshot.staging);
            }
        }
    }

    private static final class Snapshot {
        private final DlcPackMeta meta;
        private final boolean compatible;
        private final Path staging;
        private final Path packDirSnapshot;
        private final Path installedSnapshot;
        private boolean uninstalled;

        private Snapshot(DlcPackMeta meta, boolean compatible, Path staging, Path packDirSnapshot, Path installedSnapshot) {
            this.meta = meta;
            this.compatible = compatible;
            this.staging = staging;
            this.packDirSnapshot = packDirSnapshot;
            this.installedSnapshot = installedSnapshot;
        }
    }
}
