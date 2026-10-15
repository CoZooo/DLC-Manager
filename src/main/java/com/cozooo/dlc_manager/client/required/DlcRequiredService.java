package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.DlcSharedService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.core.DlcResourceHistory;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class DlcRequiredService {
    public interface ProgressListener extends DlcSharedService.ProgressListener {
    }

    public interface CancelChecker extends DlcSharedService.CancelChecker {
    }

    public interface SourceSwitchChecker extends DlcSharedService.SourceSwitchChecker<DlcRequiredEntry> {
    }

    public interface BatchProgressListener extends DlcSharedService.BatchProgressListener<DlcRequiredEntry> {
    }

    public static final class Status {
        public final int total;
        public final int missingDownloads;
        public final int needsApply;

        public Status(int total, int missingDownloads, int needsApply) {
            this.total = total;
            this.missingDownloads = missingDownloads;
            this.needsApply = needsApply;
        }
    }

    public static final class EntryError {
        public final DlcRequiredEntry entry;
        public final Throwable error;

        public EntryError(DlcRequiredEntry entry, Throwable error) {
            this.entry = entry;
            this.error = error;
        }
    }

    public static final class BatchResult {
        public final int total;
        public final int success;
        public final List<EntryError> errors;

        public BatchResult(int total, int success, List<EntryError> errors) {
            this.total = total;
            this.success = success;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    private DlcRequiredService() {
    }

    public static Status inspect(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> normalized = normalizeEntries(entries);
        int missing = collectMissingDownloads(normalized).size();
        int needsApply = collectNeedsApply(normalized).size();
        return new Status(normalized.size(), missing, needsApply);
    }

    public static List<DlcRequiredEntry> collectMissingOrApply(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> normalized = normalizeEntries(entries);
        List<DlcRequiredEntry> missing = collectMissingDownloads(normalized);
        List<DlcRequiredEntry> needs = collectNeedsApply(normalized);
        List<DlcRequiredEntry> result = new ArrayList<>();
        for (DlcRequiredEntry entry : normalized) {
            if (missing.contains(entry) || needs.contains(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static boolean isDownloaded(DlcRequiredEntry entry) {
        String fileName = resolveDownloadFileName(entry);
        if (fileName == null) {
            return false;
        }
        Path required = entry.resolveRequiredPath(fileName);
        if (required == null || !Files.exists(required)) {
            return false;
        }
        DlcRequiredRuntime.ensureFileNameKey(entry, fileName);
        return true;
    }

    public static List<DlcRequiredEntry> collectMissingDownloads(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> missing = new ArrayList<>();
        for (DlcRequiredEntry entry : normalizeEntries(entries)) {
            if (!isDownloaded(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    public static List<DlcRequiredEntry> collectNeedsApply(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> needs = new ArrayList<>();
        for (DlcRequiredEntry entry : normalizeEntries(entries)) {
            if (isDownloaded(entry) && DlcRequiredRuntime.entryNeedsApply(entry)) {
                needs.add(entry);
            }
        }
        return needs;
    }

    public static String downloadOne(DlcRequiredEntry entry, ProgressListener listener, CancelChecker cancelChecker)
        throws IOException, InterruptedException {
        return downloadOne(entry, listener, cancelChecker, null);
    }

    public static String downloadOne(DlcRequiredEntry entry, ProgressListener listener, CancelChecker cancelChecker,
                                     SourceSwitchChecker switchChecker) throws IOException, InterruptedException {
        return downloadOneShared(entry, listener, cancelChecker, switchChecker);
    }

    private static String downloadOneShared(DlcRequiredEntry entry,
                                            DlcSharedService.ProgressListener listener,
                                            DlcSharedService.CancelChecker cancelChecker,
                                            DlcSharedService.SourceSwitchChecker<DlcRequiredEntry> switchChecker)
        throws IOException, InterruptedException {
        if (entry == null) {
            throw new IOException("missing required entry");
        }
        Path requiredDir = entry.getDownloadDir();
        if (requiredDir == null) {
            throw new IOException("missing required directory");
        }
        String effectiveName = resolveDownloadFileName(entry);
        Path target = effectiveName == null ? null : entry.resolveRequiredPath(effectiveName);
        if (target != null && Files.exists(target)) {
            DlcRequiredRuntime.ensureFileNameKey(entry, effectiveName);
            return effectiveName;
        }
        Files.createDirectories(requiredDir);
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        IOException lastError = null;
        int attemptId = 0;
        boolean canceled = false;
        List<DlcDownloadCandidate> candidates;
        while (!(candidates = resolver.next()).isEmpty()) {
            for (DlcDownloadCandidate candidate : candidates) {
                int maxRetry = Math.max(1, DlcConfig.getMaxRetryCount());
                for (int attempt = 1; attempt <= maxRetry; attempt++) {
                    try {
                        DlcSharedService.checkCanceled(cancelChecker);
                        attemptId++;
                        if (listener instanceof DlcSharedService.AttemptProgressListener attemptListener) {
                            attemptListener.onAttemptStart(attemptId);
                        }
                        String fileName = chooseFileNameOriginal(entry, candidate);
                        DlcSharedService.DownloadInfo downloadInfo = null;
                        boolean shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName);
                        if (shouldProbe) {
                            downloadInfo = DlcSharedService.openDownload(candidate.url, 0);
                            if (fileName == null) {
                                fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                            }
                            DlcSharedService.closeQuietly(downloadInfo.connection);
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadService.suggestFileName(candidate.url);
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadUtil.stripQuery(entry.getBaseName());
                        }
                        Path resolvedTarget = entry.resolveDownloadPath(fileName);
                        if (resolvedTarget == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(resolvedTarget.getParent());
                        if (Files.exists(resolvedTarget)) {
                            entry.setResolvedFileName(fileName);
                            return fileName;
                        }
                        Path resolved = DlcSharedService.downloadCandidate(candidate, resolvedTarget, fileName, shouldProbe, listener,
                            () -> DlcSharedService.isCanceled(cancelChecker) || DlcSharedService.isSwitchRequested(switchChecker, entry));
                        DlcSharedService.verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker);
                        entry.setResolvedFileName(fileName);
                        DlcRequiredRuntime.updateFileNameKey(entry, fileName);
                        DlcResourceHistory.downloaded(entry.getDlcRoot(), entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), resolved);
                        return fileName;
                    } catch (DlcDownloadService.DownloadCanceledException ex) {
                        if (DlcSharedService.isSwitchRequested(switchChecker, entry)) {
                            lastError = new DlcSharedService.DownloadSwitchException("switch");
                            DlcSharedService.clearSwitchRequest(switchChecker, entry);
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        canceled = true;
                        throw new DlcSharedService.DownloadCanceledException("canceled");
                    } catch (DlcSharedService.DownloadCanceledException ex) {
                        canceled = true;
                        throw ex;
                    } catch (IOException ex) {
                        if (DlcSharedService.isSwitchException(ex)) {
                            lastError = ex;
                            DlcSharedService.clearSwitchRequest(switchChecker, entry);
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        if (ex.getCause() instanceof DlcSharedService.DownloadCanceledException canceledCause) {
                            canceled = true;
                            throw canceledCause;
                        }
                        lastError = ex;
                        if (ex instanceof DlcDownloadService.SegmentDownloadException) {
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        if (attempt >= maxRetry) {
                            DlcSharedService.notifyProgress(listener, 0L, 1L);
                        }
                    }
                }
            }
        }
        if (DlcSharedService.isCanceled(cancelChecker) || canceled) {
            throw new DlcSharedService.DownloadCanceledException("canceled");
        }
        if (attemptId > 0) {
            DlcSharedService.notifyProgress(listener, 0L, 1L);
        }
        if (lastError == null) {
            lastError = resolver.lastError();
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    public static BatchResult downloadAll(List<DlcRequiredEntry> entries,
                                          BatchProgressListener listener,
                                          CancelChecker cancelChecker) throws InterruptedException {
        return downloadAll(entries, listener, cancelChecker, null);
    }

    public static BatchResult downloadAll(List<DlcRequiredEntry> entries,
                                          BatchProgressListener listener,
                                          CancelChecker cancelChecker,
                                          SourceSwitchChecker switchChecker) throws InterruptedException {
        DlcSharedService.BatchResult<DlcRequiredEntry> result = DlcSharedService.downloadAll(
            entries, listener, cancelChecker, switchChecker, DlcRequiredService::downloadOneShared);
        return toBatchResult(result);
    }

    public static boolean installOne(DlcRequiredEntry entry) throws IOException {
        if (entry == null) return false;
        boolean applied = DlcRequiredInstaller.install(entry, path -> { });
        String id = entry.getIdentifier();
        if (id != null && !id.isBlank()) DlcRequiredRuntimeState.setOverride(id, true);
        return applied;
    }

    public static void reconcileEntries(List<DlcRequiredEntry> entries) throws IOException {
        for (Path root : DlcConfig.getDlcRoots()) reconcileRoot(root, entries);
    }

    public static void reconcileRoot(Path root, List<DlcRequiredEntry> entries) throws IOException {
        java.util.Set<String> keys = new java.util.HashSet<>();
        java.util.Set<String> downloads = new java.util.HashSet<>();
        for (DlcRequiredEntry entry : entries) {
            if (!root.toAbsolutePath().normalize().equals(entry.getDlcRoot().toAbsolutePath().normalize())) continue;
            keys.add(entry.getHistoryKey());
            Path cache = entry.resolveDownloadPath(resolveExistingFileName(entry));
            if (cache != null) downloads.add(DlcResourceHistory.relativePath(cache));
        }
        DlcResourceHistory.reconcileRequired(root, keys, downloads);
    }

    public static String resolveExistingFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = entry.getResolvedFileName();
        if (isConfigFileName(fileName) || isTempFileName(fileName)) {
            fileName = null;
        }
        if (fileName != null && !fileName.isBlank()) {
            Path target = entry.resolveRequiredPath(fileName);
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName;
            }
        }
        String base = entry.getBaseName();
        if (entry.getRequiredDir() == null || base == null || base.isBlank()) {
            return fileName;
        }
        for (Path directory : List.of(entry.getDownloadDir(), entry.getRequiredDir())) {
            if (!Files.isDirectory(directory)) continue;
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory, base + ".*")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    if (!isConfigFile(entry, path) && !isTempFileName(name) && Files.isRegularFile(path)) return name;
                }
            } catch (IOException ignored) {}
        }
        return fileName;
    }

    private static List<DlcRequiredEntry> normalizeEntries(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> normalized = new ArrayList<>();
        if (entries != null) {
            for (DlcRequiredEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                resolveDownloadFileName(entry);
                normalized.add(entry);
            }
        }
        return normalized;
    }

    private static String resolveDownloadFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = normalizeFileName(entry.getRequiredFileName());
        if (fileName == null) {
            fileName = normalizeFileName(resolveFileNameFromCandidates(entry));
        }
        if (fileName == null) {
            fileName = normalizeFileName(resolveExistingRequiredFile(entry));
        }
        if (isTempFileName(fileName)) {
            fileName = null;
        }
        entry.setResolvedFileName(fileName);
        return fileName;
    }

    private static String resolveFileNameFromCandidates(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        try {
            List<DlcDownloadCandidate> candidates;
            while (!(candidates = resolver.next()).isEmpty()) {
                for (DlcDownloadCandidate candidate : candidates) {
                    String name = candidate.fileNameOverride;
                    if (!DlcDownloadUtil.isLikelyFileName(name) && candidate.direct) {
                        name = DlcDownloadUtil.extractFileNameFromUrl(candidate.url);
                    }
                    name = DlcDownloadUtil.sanitizeFileName(name);
                    if (DlcDownloadUtil.isLikelyFileName(name)) {
                        return name;
                    }
                }
            }
        } catch (IOException | InterruptedException ignored) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    private static String resolveExistingRequiredFile(DlcRequiredEntry entry) {
        return resolveExistingFileName(entry);
    }

    private static String chooseFileNameOriginal(DlcRequiredEntry entry, DlcDownloadCandidate candidate) {
        String fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName());
        if (fileName == null && candidate.fileNameOverride != null) {
            fileName = DlcDownloadUtil.sanitizeFileName(candidate.fileNameOverride);
        }
        if (fileName == null) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(candidate.url));
        }
        return fileName;
    }

    private static boolean isConfigFile(DlcRequiredEntry entry, Path path) {
        if (entry == null || path == null) {
            return false;
        }
        Path configPath = entry.getConfigPath();
        if (configPath != null && path.equals(configPath)) {
            return true;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return isConfigFileName(name);
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String trimmed = DlcDownloadUtil.stripQuery(fileName);
        if (trimmed == null) {
            return null;
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty() || com.cozooo.dlc_manager.core.DlcDcFormatUtil.isDcName(trimmed)) {
            return null;
        }
        return DlcDownloadUtil.sanitizeFileName(trimmed);
    }

    private static boolean isTempFileName(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".part");
    }

    private static boolean isConfigFileName(String fileName) {
        return fileName != null && DlcDcFormatUtil.isDcName(fileName);
    }

    private static BatchResult toBatchResult(DlcSharedService.BatchResult<DlcRequiredEntry> result) {
        List<EntryError> errors = new ArrayList<>();
        for (DlcSharedService.EntryError<DlcRequiredEntry> error : result.errors) {
            errors.add(new EntryError(error.entry, error.error));
        }
        return new BatchResult(result.total, result.success, errors);
    }
}
