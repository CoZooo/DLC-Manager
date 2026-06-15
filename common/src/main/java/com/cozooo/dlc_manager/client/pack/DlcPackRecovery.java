package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class DlcPackRecovery {
    private DlcPackRecovery() {
    }

    public static RecoveryReport recover(Path instanceRoot, Path dlcRoot) {
        RecoveryReport report = new RecoveryReport();
        if (instanceRoot == null || dlcRoot == null) {
            return report;
        }
        Path stagingRoot = dlcRoot.resolve("tmp").resolve("_staging").toAbsolutePath().normalize();
        if (!Files.isDirectory(stagingRoot)) {
            return report;
        }
        try (Stream<Path> stream = Files.list(stagingRoot)) {
            for (Path staging : stream.filter(Files::isDirectory).toList()) {
                recoverStaging(instanceRoot, dlcRoot, staging, report);
            }
        } catch (IOException ignored) {
        }
        return report;
    }

    private static void recoverStaging(Path instanceRoot, Path dlcRoot, Path staging, RecoveryReport report) {
        Path txnPath = staging.resolve("txn.dc");
        DlcPackTransaction txn = DlcPackTransaction.load(txnPath);
        if (txn == null) {
            return;
        }
        report.transactions++;
        if (txn.status == DlcPackTransaction.Status.RUNNING) {
            Path packDir = packDir(dlcRoot, txn.identifier);
            rollback(instanceRoot, packDir, txn, report);
            txn.status = DlcPackTransaction.Status.ROLLED_BACK;
            txn.save(txnPath);
            DlcZipUtil.deleteRecursive(packDir);
            report.rolledBack++;
        } else {
            report.cleaned++;
        }
        DlcZipUtil.deleteRecursive(staging);
    }

    private static void rollback(Path instanceRoot, Path packDir, DlcPackTransaction txn, RecoveryReport report) {
        List<String> written = new ArrayList<>(txn.writtenPaths);
        java.util.Collections.reverse(written);
        for (String relative : written) {
            try {
                Path target = resolveInside(instanceRoot, relative);
                if (Files.deleteIfExists(target)) {
                    report.deletedWritten++;
                }
                pruneEmptyParents(target.getParent(), instanceRoot);
            } catch (Exception ignored) {
            }
        }
        Set<String> archivedPaths = archivedRelativePaths(packDir);
        archivedPaths.addAll(txn.movedPaths);
        List<String> toRestore = new ArrayList<>(archivedPaths);
        toRestore.sort(Comparator.comparingInt(String::length));
        for (String relative : toRestore) {
            try {
                Path archived = resolveInside(packDir, relative);
                if (!Files.exists(archived)) {
                    continue;
                }
                Path target = resolveInside(instanceRoot, relative);
                Files.createDirectories(target.getParent());
                Files.move(archived, target, StandardCopyOption.REPLACE_EXISTING);
                report.restored++;
            } catch (Exception ignored) {
            }
        }
        restoreRuntimeChanges(txn);
    }

    private static Set<String> archivedRelativePaths(Path packDir) {
        Set<String> paths = new LinkedHashSet<>();
        if (packDir == null || !Files.isDirectory(packDir)) {
            return paths;
        }
        try (Stream<Path> stream = Files.walk(packDir)) {
            for (Path archived : stream.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).toList()) {
                String relative = DlcPackMeta.normalizeRelativePath(packDir.relativize(archived).toString());
                if (relative != null && !isPackRecordPath(relative)) {
                    paths.add(relative);
                }
            }
        } catch (IOException ignored) {
        }
        return paths;
    }

    private static void restoreRuntimeChanges(DlcPackTransaction txn) {
        for (String id : txn.enabledDlc) {
            try {
                DlcItem item = DlcRuntimeState.getItemByIdentifier(id);
                if (item != null) {
                    item.setEnabled(false);
                }
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.disabledDlc) {
            try {
                DlcItem item = DlcRuntimeState.getItemByIdentifier(id);
                if (item != null) {
                    item.setEnabled(true);
                }
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.enabledPresets) {
            try {
                DlcPresetRuntimeState.setPresetEnabled(id, false);
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.disabledPresets) {
            try {
                DlcPresetRuntimeState.setPresetEnabled(id, true);
            } catch (Exception ignored) {
            }
        }
    }

    private static Path packDir(Path dlcRoot, String identifier) {
        return dlcRoot.resolve("tmp").resolve("pack").resolve(identifier).toAbsolutePath().normalize();
    }

    private static Path resolveInside(Path root, String relative) throws IOException {
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

    private static boolean isPackRecordPath(String relative) {
        String normalized = relative.toLowerCase(Locale.ROOT);
        return normalized.equals("meta.dc")
            || normalized.startsWith("metadata/")
            || normalized.startsWith("dlcpack_metadata/");
    }

    private static void pruneEmptyParents(Path start, Path stop) {
        Path dir = start;
        Path normalizedStop = stop.toAbsolutePath().normalize();
        while (dir != null) {
            Path current = dir.toAbsolutePath().normalize();
            if (current.equals(normalizedStop) || !current.startsWith(normalizedStop)) {
                break;
            }
            try (Stream<Path> stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
                Files.deleteIfExists(current);
            } catch (IOException ex) {
                break;
            }
            dir = current.getParent();
        }
    }

    public static final class RecoveryReport {
        public int transactions;
        public int rolledBack;
        public int cleaned;
        public int deletedWritten;
        public int restored;
    }
}
