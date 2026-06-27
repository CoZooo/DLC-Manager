package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DlcRequiredService {
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);

        default void onStatusKey(String statusKey) {
        }
    }

    public interface CancelChecker {
        boolean isCanceled();
    }

    public interface SourceSwitchChecker {
        default boolean isSwitchRequested(DlcRequiredEntry entry) {
            return false;
        }

        default void clearSwitchRequest(DlcRequiredEntry entry) {
        }
    }

    public interface BatchProgressListener {
        default void onEntryStart(DlcRequiredEntry entry, int index, int total) {
        }

        default void onEntryAttemptStart(DlcRequiredEntry entry, int attemptId, int index, int total) {
        }

        default void onEntryProgress(DlcRequiredEntry entry, long downloadedBytes, long totalBytes) {
        }

        default void onEntryStatus(DlcRequiredEntry entry, String statusKey, int completed, int total) {
        }

        default void onEntrySuccess(DlcRequiredEntry entry, String fileName, int completed, int total) {
        }

        default void onEntryFailure(DlcRequiredEntry entry, Throwable error, int completed, int total) {
        }

        default void onEntryComplete(DlcRequiredEntry entry) {
        }
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
        if (entry == null) {
            throw new IOException("missing required entry");
        }
        Path requiredDir = entry.getRequiredDir();
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
                        checkCanceled(cancelChecker);
                        attemptId++;
                        if (listener instanceof AttemptProgressListener attemptListener) {
                            attemptListener.onAttemptStart(attemptId);
                        }
                        String fileName = chooseFileNameOriginal(entry, candidate);
                        DownloadInfo downloadInfo = null;
                        boolean shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName);
                        if (shouldProbe) {
                            downloadInfo = openDownload(candidate.url, 0);
                            if (fileName == null) {
                                fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                            }
                            closeQuietly(downloadInfo.connection);
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadService.suggestFileName(candidate.url);
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadUtil.stripQuery(entry.getBaseName());
                        }
                        Path resolvedTarget = entry.resolveRequiredPath(fileName);
                        if (resolvedTarget == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(resolvedTarget.getParent());
                        if (Files.exists(resolvedTarget)) {
                            entry.setResolvedFileName(fileName);
                            return fileName;
                        }
                        Path downloaded = shouldProbe
                            ? DlcDownloadService.download(candidate.url, resolvedTarget.getParent(), fileName,
                                listener == null ? null : toDownloadProgress(listener),
                                () -> isCanceled(cancelChecker) || isSwitchRequested(switchChecker, entry))
                            : DlcDownloadService.downloadKnown(candidate.url, resolvedTarget.getParent(), fileName, candidate.expectedSize,
                                listener == null ? null : toDownloadProgress(listener),
                                () -> isCanceled(cancelChecker) || isSwitchRequested(switchChecker, entry));
                        Path resolved = downloaded.equals(resolvedTarget)
                            ? downloaded
                            : Files.move(downloaded, resolvedTarget, StandardCopyOption.REPLACE_EXISTING);
                        verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker);
                        entry.setResolvedFileName(fileName);
                        DlcRequiredRuntime.updateFileNameKey(entry, fileName);
                        return fileName;
                    } catch (DlcDownloadService.DownloadCanceledException ex) {
                        if (isSwitchRequested(switchChecker, entry)) {
                            lastError = new DownloadSwitchException("switch");
                            clearSwitchRequest(switchChecker, entry);
                            notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        canceled = true;
                        throw new DownloadCanceledException("canceled");
                    } catch (DownloadCanceledException ex) {
                        canceled = true;
                        throw ex;
                    } catch (IOException ex) {
                        if (isSwitchException(ex)) {
                            lastError = ex;
                            clearSwitchRequest(switchChecker, entry);
                            notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        if (ex.getCause() instanceof DownloadCanceledException canceledCause) {
                            canceled = true;
                            throw canceledCause;
                        }
                        lastError = ex;
                        if (ex instanceof DlcDownloadService.SegmentDownloadException) {
                            notifyProgress(listener, 0L, 1L);
                            break;
                        }
                        if (attempt >= maxRetry) {
                            notifyProgress(listener, 0L, 1L);
                        }
                    }
                }
            }
        }
        if (isCanceled(cancelChecker) || canceled) {
            throw new DownloadCanceledException("canceled");
        }
        if (attemptId > 0) {
            notifyProgress(listener, 0L, 1L);
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
        List<DlcRequiredEntry> targets = entries == null ? List.of() : entries.stream()
            .filter(entry -> entry != null)
            .toList();
        int total = targets.size();
        if (total == 0) {
            return new BatchResult(0, 0, List.of());
        }
        int success = 0;
        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger();
        BatchRunResult first = runDownloadBatch(targets, total, completed, listener, cancelChecker, switchChecker);
        success += first.success;
        if (isCanceled(cancelChecker)) {
            return new BatchResult(total, success, first.errors);
        }
        BatchRunResult second = first.failed.isEmpty()
            ? new BatchRunResult(List.of(), List.of(), 0)
            : runDownloadBatch(first.failed, total, completed, listener, cancelChecker, switchChecker);
        success += second.success;
        List<EntryError> errors = new ArrayList<>(second.errors);
        if (second.failed.isEmpty()) {
            return new BatchResult(total, success, errors);
        }
        errors.clear();
        for (EntryError error : second.errors) {
            errors.add(error);
        }
        return new BatchResult(total, success, errors);
    }

    public static boolean installOne(DlcRequiredEntry entry) throws IOException {
        if (entry == null) {
            return false;
        }
        String fileName = resolveExistingFileName(entry);
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("missing file name");
        }
        entry.setResolvedFileName(fileName);
        Path source = entry.resolveRequiredPath(fileName);
        if (source == null || !Files.exists(source)) {
            throw new IOException("missing required source");
        }
        boolean applied = false;
        List<Path> appliedTargets = entry.resolveAppliedTargets();
        if (appliedTargets.isEmpty()) {
            applied = true;
        }
        for (Path targetDir : appliedTargets) {
            Path target = resolveTarget(targetDir, fileName);
            if (target == null) {
                continue;
            }
            try {
                Files.createDirectories(target.getParent());
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    applied = true;
                }
            } catch (IOException ignored) {
            }
        }
        String id = entry.getIdentifier();
        if (id != null && !id.isBlank()) {
            DlcRequiredRuntime.updateUserRemoved(entry, false);
            DlcRequiredRuntimeState.setOverride(id, true);
        }
        return applied;
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
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                if (isConfigFile(entry, path)) {
                    continue;
                }
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (isTempFileName(name)) {
                    continue;
                }
                return name;
            }
        } catch (IOException ignored) {
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
        if (entry == null) {
            return null;
        }
        String base = entry.getBaseName();
        if (entry.getRequiredDir() == null || base == null || base.isBlank()) {
            return null;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                if (isConfigFile(entry, path)) {
                    continue;
                }
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (!DlcDcFormatUtil.isDcName(name) && !isTempFileName(name)) {
                    return name;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
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

    private static BatchRunResult runDownloadBatch(List<DlcRequiredEntry> pending,
                                                   int total,
                                                   java.util.concurrent.atomic.AtomicInteger completed,
                                                   BatchProgressListener listener,
                                                   CancelChecker cancelChecker,
                                                   SourceSwitchChecker switchChecker) throws InterruptedException {
        List<DlcRequiredEntry> failed = new ArrayList<>();
        List<EntryError> errors = new ArrayList<>();
        if (pending == null || pending.isEmpty() || isCanceled(cancelChecker)) {
            return new BatchRunResult(failed, errors, 0);
        }
        int maxConcurrentDownloads = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
        if (maxConcurrentDownloads <= 1) {
            int success = 0;
            for (DlcRequiredEntry entry : pending) {
                if (isCanceled(cancelChecker)) {
                    break;
                }
                if (listener != null) {
                    listener.onEntryStart(entry, completed.get(), total);
                }
                try {
                    String fileName = downloadOne(entry, new BatchEntryProgress(listener, entry, completed, total), cancelChecker, switchChecker);
                    int done = completed.incrementAndGet();
                    success++;
                    if (listener != null) {
                        listener.onEntrySuccess(entry, fileName, done, total);
                    }
                } catch (DownloadCanceledException e) {
                    break;
                } catch (Exception e) {
                    failed.add(entry);
                    errors.add(new EntryError(entry, e));
                    if (listener != null) {
                        listener.onEntryFailure(entry, e, completed.get(), total);
                    }
                } finally {
                    if (listener != null) {
                        listener.onEntryComplete(entry);
                    }
                }
            }
            return new BatchRunResult(failed, errors, success);
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentDownloads);
        ExecutorCompletionService<DownloadResult> completionService = new ExecutorCompletionService<>(executor);
        for (DlcRequiredEntry entry : pending) {
            completionService.submit(() -> {
                if (listener != null) {
                    listener.onEntryStart(entry, completed.get(), total);
                }
                try {
                    String fileName = downloadOne(entry, new BatchEntryProgress(listener, entry, completed, total), cancelChecker, switchChecker);
                    return DownloadResult.success(entry, fileName);
                } catch (DownloadCanceledException e) {
                    return DownloadResult.canceled(entry);
                } catch (Throwable t) {
                    return DownloadResult.failure(entry, t);
                } finally {
                    if (listener != null) {
                        listener.onEntryComplete(entry);
                    }
                }
            });
        }
        int success = 0;
        try {
            for (int i = 0; i < pending.size(); i++) {
                if (isCanceled(cancelChecker)) {
                    break;
                }
                DownloadResult result;
                try {
                    Future<DownloadResult> future = completionService.take();
                    result = future.get();
                } catch (Exception e) {
                    DlcRequiredEntry fallback = pending.get(Math.min(i, pending.size() - 1));
                    result = DownloadResult.failure(fallback, e);
                }
                if (result.canceled) {
                    break;
                } else if (result.error == null) {
                    int done = completed.incrementAndGet();
                    success++;
                    if (listener != null) {
                        listener.onEntrySuccess(result.entry, result.fileName, done, total);
                    }
                } else {
                    failed.add(result.entry);
                    errors.add(new EntryError(result.entry, result.error));
                    if (listener != null) {
                        listener.onEntryFailure(result.entry, result.error, completed.get(), total);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return new BatchRunResult(failed, errors, success);
    }

    private static DlcDownloadService.ProgressListener toDownloadProgress(ProgressListener listener) {
        return new DlcDownloadService.StatusProgressListener() {
            @Override
            public void onProgress(long downloadedBytes, long totalBytes) {
                listener.onProgress(downloadedBytes, totalBytes);
            }

            @Override
            public void onStatusKey(String statusKey) {
                listener.onStatusKey(statusKey);
            }
        };
    }

    private static void notifyProgress(ProgressListener listener, long downloadedBytes, long totalBytes) {
        if (listener != null) {
            listener.onProgress(downloadedBytes, totalBytes);
        }
    }

    private static void checkCanceled(CancelChecker cancelChecker) throws DownloadCanceledException {
        if (isCanceled(cancelChecker)) {
            throw new DownloadCanceledException("canceled");
        }
    }

    private static boolean isCanceled(CancelChecker cancelChecker) {
        return cancelChecker != null && cancelChecker.isCanceled();
    }

    private static boolean isSwitchRequested(SourceSwitchChecker switchChecker, DlcRequiredEntry entry) {
        return switchChecker != null && switchChecker.isSwitchRequested(entry);
    }

    private static void clearSwitchRequest(SourceSwitchChecker switchChecker, DlcRequiredEntry entry) {
        if (switchChecker != null) {
            switchChecker.clearSwitchRequest(entry);
        }
    }

    private static boolean isSwitchException(IOException ex) {
        return ex instanceof DownloadSwitchException || ex.getCause() instanceof DownloadSwitchException;
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

    private static DownloadInfo openDownload(String url, long offset) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(Math.max(1, DlcConfig.getConnectTimeoutMs()));
        connection.setReadTimeout(Math.max(1, DlcConfig.getTaskTimeoutMs()));
        connection.setRequestProperty("User-Agent", "DLC-Manager");
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=" + offset + "-");
        }
        connection.connect();
        String fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.getURL().toString());
        }
        int responseCode = connection.getResponseCode();
        long contentLength = connection.getContentLengthLong();
        long totalBytes = contentLength > 0 ? contentLength + Math.max(0, offset) : -1L;
        String contentMd5 = connection.getHeaderField("Content-MD5");
        return new DownloadInfo(connection, fileName, responseCode, totalBytes, contentMd5);
    }

    private static void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private static void verifyDownloadedFile(Path file, DlcDownloadCandidate candidate, DownloadInfo info,
                                             CancelChecker cancelChecker) throws IOException {
        checkCanceled(cancelChecker);
        if (file == null || candidate == null) {
            return;
        }
        if (candidate.hashType != null && candidate.hashValue != null && !candidate.hashValue.isBlank()) {
            String actual = DlcDownloadUtil.computeHashHex(file, mapHashAlgorithm(candidate.hashType));
            if (actual == null || !actual.equalsIgnoreCase(DlcDownloadUtil.normalizeHash(candidate.hashValue))) {
                Files.deleteIfExists(file);
                throw new IOException("hash mismatch");
            }
            return;
        }
        if (info != null && info.contentMd5 != null && !info.contentMd5.isBlank()) {
            String expected = DlcDownloadUtil.normalizeHash(info.contentMd5);
            String actualHex = DlcDownloadUtil.computeHashHex(file, "MD5");
            String actualBase64 = DlcDownloadUtil.computeMd5Base64(file);
            if (actualHex == null || actualBase64 == null
                || (!expected.equalsIgnoreCase(actualHex) && !expected.equalsIgnoreCase(actualBase64))) {
                Files.deleteIfExists(file);
                throw new IOException("hash mismatch");
            }
            return;
        }
        long expectedSize = candidate.expectedSize;
        if (expectedSize <= 0 && info != null && info.totalBytes > 0) {
            expectedSize = info.totalBytes;
        }
        if (expectedSize > 0) {
            long size = Files.size(file);
            if (size != expectedSize) {
                Files.deleteIfExists(file);
                throw new IOException("size mismatch");
            }
        }
    }

    private static Path resolveTarget(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
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

    private static String mapHashAlgorithm(String hashType) {
        if (hashType == null) {
            return null;
        }
        return switch (hashType.toLowerCase(java.util.Locale.ROOT)) {
            case "sha1", "sha-1" -> "SHA-1";
            case "sha256", "sha-256" -> "SHA-256";
            case "sha512", "sha-512" -> "SHA-512";
            case "md5" -> "MD5";
            default -> hashType;
        };
    }

    private interface AttemptProgressListener extends ProgressListener {
        void onAttemptStart(int attemptId);
    }

    private static final class BatchEntryProgress implements AttemptProgressListener {
        private final BatchProgressListener listener;
        private final DlcRequiredEntry entry;
        private final java.util.concurrent.atomic.AtomicInteger completed;
        private final int total;
        private volatile int attemptId;

        private BatchEntryProgress(BatchProgressListener listener,
                                   DlcRequiredEntry entry,
                                   java.util.concurrent.atomic.AtomicInteger completed,
                                   int total) {
            this.listener = listener;
            this.entry = entry;
            this.completed = completed;
            this.total = total;
        }

        @Override
        public void onAttemptStart(int attemptId) {
            this.attemptId = attemptId;
            if (listener != null) {
                listener.onEntryAttemptStart(entry, attemptId, completed.get(), total);
            }
        }

        @Override
        public void onProgress(long downloadedBytes, long totalBytes) {
            if (listener != null) {
                listener.onEntryProgress(entry, downloadedBytes, totalBytes);
            }
        }

        @Override
        public void onStatusKey(String statusKey) {
            if (listener != null) {
                listener.onEntryStatus(entry, statusKey, completed.get(), total);
            }
        }
    }

    private static final class BatchRunResult {
        final List<DlcRequiredEntry> failed;
        final List<EntryError> errors;
        final int success;

        private BatchRunResult(List<DlcRequiredEntry> failed, List<EntryError> errors, int success) {
            this.failed = failed == null ? List.of() : failed;
            this.errors = errors == null ? List.of() : errors;
            this.success = success;
        }
    }

    public static final class DownloadSwitchException extends IOException {
        private DownloadSwitchException(String message) {
            super(message);
        }
    }

    public static final class DownloadCanceledException extends IOException {
        private DownloadCanceledException(String message) {
            super(message);
        }
    }

    private static final class DownloadInfo {
        final HttpURLConnection connection;
        final String fileName;
        final int responseCode;
        final long totalBytes;
        final String contentMd5;

        private DownloadInfo(HttpURLConnection connection, String fileName, int responseCode, long totalBytes, String contentMd5) {
            this.connection = connection;
            this.fileName = fileName;
            this.responseCode = responseCode;
            this.totalBytes = totalBytes;
            this.contentMd5 = contentMd5;
        }
    }

    private static final class DownloadResult {
        final DlcRequiredEntry entry;
        final String fileName;
        final Throwable error;
        final boolean canceled;

        private DownloadResult(DlcRequiredEntry entry, String fileName, Throwable error, boolean canceled) {
            this.entry = entry;
            this.fileName = fileName;
            this.error = error;
            this.canceled = canceled;
        }

        static DownloadResult success(DlcRequiredEntry entry, String fileName) {
            return new DownloadResult(entry, fileName, null, false);
        }

        static DownloadResult canceled(DlcRequiredEntry entry) {
            return new DownloadResult(entry, null, null, true);
        }

        static DownloadResult failure(DlcRequiredEntry entry, Throwable error) {
            return new DownloadResult(entry, null, error, false);
        }
    }
}
