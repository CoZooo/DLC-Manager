package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DlcRemoteService {
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);

        default void onStatusKey(String statusKey) {
        }
    }

    public interface CancelChecker {
        boolean isCanceled();
    }

    public interface SourceSwitchChecker {
        default boolean isSwitchRequested(DlcRemoteEntry entry) {
            return false;
        }

        default void clearSwitchRequest(DlcRemoteEntry entry) {
        }
    }

    public interface BatchProgressListener {
        default void onEntryStart(DlcRemoteEntry entry, int index, int total) {
        }

        default void onEntryAttemptStart(DlcRemoteEntry entry, int attemptId, int index, int total) {
        }

        default void onEntryProgress(DlcRemoteEntry entry, long downloadedBytes, long totalBytes) {
        }

        default void onEntryStatus(DlcRemoteEntry entry, String statusKey, int completed, int total) {
        }

        default void onEntrySuccess(DlcRemoteEntry entry, String fileName, int completed, int total) {
        }

        default void onEntryFailure(DlcRemoteEntry entry, Throwable error, int completed, int total) {
        }

        default void onEntryComplete(DlcRemoteEntry entry) {
        }
    }

    public static final class EntryError {
        public final DlcRemoteEntry entry;
        public final Throwable error;

        public EntryError(DlcRemoteEntry entry, Throwable error) {
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

    private DlcRemoteService() {
    }

    public static boolean isMissing(DlcRemoteEntry entry) {
        if (entry == null) {
            return false;
        }
        Path target = entry.getTargetPath();
        return target == null || !Files.exists(target);
    }

    public static int countMissing(List<DlcRemoteEntry> entries) {
        int count = 0;
        if (entries != null) {
            for (DlcRemoteEntry entry : entries) {
                if (isMissing(entry)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static List<DlcRemoteEntry> collectMissing(List<DlcRemoteEntry> entries) {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        if (entries != null) {
            for (DlcRemoteEntry entry : entries) {
                if (isMissing(entry)) {
                    missing.add(entry);
                }
            }
        }
        return missing;
    }

    public static List<DlcRemoteEntry> expandMissingWithDependencies(List<DlcRemoteEntry> roots) {
        List<DlcRemoteEntry> expanded = expandWithDependencies(roots);
        List<DlcRemoteEntry> result = new ArrayList<>();
        for (DlcRemoteEntry entry : expanded) {
            if (isMissing(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<DlcRemoteEntry> expandWithDependencies(List<DlcRemoteEntry> roots) {
        List<DlcRemoteEntry> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayDeque<DlcRemoteEntry> queue = new ArrayDeque<>();
        if (roots != null) {
            queue.addAll(roots);
        }
        while (!queue.isEmpty()) {
            DlcRemoteEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            String key = entry.getUniqueKey();
            if (key == null || key.isBlank() || !seen.add(key)) {
                continue;
            }
            result.add(entry);
            for (DlcRemoteEntry dep : entry.getRemoteDependencies()) {
                if (dep != null) {
                    queue.add(dep);
                }
            }
        }
        return result;
    }

    public static String downloadOne(DlcRemoteEntry entry, ProgressListener listener, CancelChecker cancelChecker)
        throws IOException, InterruptedException {
        return downloadOne(entry, listener, cancelChecker, null);
    }

    public static String downloadOne(DlcRemoteEntry entry, ProgressListener listener, CancelChecker cancelChecker,
                                     SourceSwitchChecker switchChecker) throws IOException, InterruptedException {
        if (entry == null) {
            throw new IOException("missing remote entry");
        }
        checkCanceled(cancelChecker);
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
        precheckDownloadSources(entry, listener);
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
                        DownloadInfo downloadInfo = null;
                        String headerName = null;
                        boolean shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(candidate.fileNameOverride);
                        if (shouldProbe) {
                            downloadInfo = openDownload(candidate.url, 0);
                            headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                            closeQuietly(downloadInfo.connection);
                        }
                        String fileName = candidate.fileNameOverride;
                        boolean fileNameExplicit = entry.isFileNameExplicit() && fileName != null && !fileName.isBlank();
                        if (fileName == null || fileName.isBlank()) {
                            fileName = entry.getFileNameHint();
                        }
                        if (!fileNameExplicit) {
                            if (candidate.direct && headerName != null) {
                                fileName = headerName;
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName) && headerName != null) {
                                fileName = headerName;
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                                fileName = DlcDownloadService.suggestFileName(candidate.url);
                            }
                        }
                        fileName = finalizeFileName(fileName, entry, candidate.url);
                        Path target = entry.resolveTargetPath(fileName);
                        if (target == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(target.getParent());
                        if (Files.exists(target)) {
                            entry.setFileNameHint(fileName);
                            DlcRemoteRuntime.updateFileNameKey(entry, fileName);
                            return fileName;
                        }
                        Path downloaded = shouldProbe
                            ? DlcDownloadService.download(candidate.url, target.getParent(), fileName,
                                listener == null ? null : toDownloadProgress(listener),
                                () -> isCanceled(cancelChecker) || isSwitchRequested(switchChecker, entry))
                            : DlcDownloadService.downloadKnown(candidate.url, target.getParent(), fileName, candidate.expectedSize,
                                listener == null ? null : toDownloadProgress(listener),
                                () -> isCanceled(cancelChecker) || isSwitchRequested(switchChecker, entry));
                        Path resolved = downloaded.equals(target)
                            ? downloaded
                            : Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
                        verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker);
                        entry.setFileNameHint(fileName);
                        DlcRemoteRuntime.updateFileNameKey(entry, fileName);
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

    public static BatchResult downloadAll(List<DlcRemoteEntry> entries,
                                          BatchProgressListener listener,
                                          CancelChecker cancelChecker) throws InterruptedException {
        return downloadAll(entries, listener, cancelChecker, null);
    }

    public static BatchResult downloadAll(List<DlcRemoteEntry> entries,
                                          BatchProgressListener listener,
                                          CancelChecker cancelChecker,
                                          SourceSwitchChecker switchChecker) throws InterruptedException {
        List<DlcRemoteEntry> targets = entries == null ? List.of() : entries.stream()
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
        return new BatchResult(total, success, second.failed.isEmpty() ? List.of() : second.errors);
    }

    private static BatchRunResult runDownloadBatch(List<DlcRemoteEntry> pending,
                                                   int total,
                                                   java.util.concurrent.atomic.AtomicInteger completed,
                                                   BatchProgressListener listener,
                                                   CancelChecker cancelChecker,
                                                   SourceSwitchChecker switchChecker) throws InterruptedException {
        List<DlcRemoteEntry> failed = new ArrayList<>();
        List<EntryError> errors = new ArrayList<>();
        if (pending == null || pending.isEmpty() || isCanceled(cancelChecker)) {
            return new BatchRunResult(failed, errors, 0);
        }
        int maxConcurrentDownloads = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
        if (maxConcurrentDownloads <= 1) {
            int success = 0;
            for (DlcRemoteEntry entry : pending) {
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
        for (DlcRemoteEntry entry : pending) {
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
                    DlcRemoteEntry fallback = pending.get(Math.min(i, pending.size() - 1));
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

    private static void precheckDownloadSources(DlcRemoteEntry entry, ProgressListener listener) {
        try {
            List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(entry);
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return;
            }
            DlcDownloadService.precheckDownloadSources(candidates, listener == null ? null : toDownloadProgress(listener));
        } catch (Exception ignored) {
        }
    }

    private static boolean hasMissingHostedDownloadCandidate(DlcRemoteEntry entry, List<DlcDownloadCandidate> candidates) {
        if (candidates == null) {
            return false;
        }
        for (DlcDownloadCandidate candidate : candidates) {
            if (candidate == null || candidate.direct) {
                continue;
            }
            String fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url);
            Path target = entry.resolveTargetPath(fileName);
            if (target != null && !Files.exists(target)) {
                return true;
            }
        }
        return false;
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

    private static boolean isSwitchRequested(SourceSwitchChecker switchChecker, DlcRemoteEntry entry) {
        return switchChecker != null && switchChecker.isSwitchRequested(entry);
    }

    private static void clearSwitchRequest(SourceSwitchChecker switchChecker, DlcRemoteEntry entry) {
        if (switchChecker != null) {
            switchChecker.clearSwitchRequest(entry);
        }
    }

    private static boolean isSwitchException(IOException ex) {
        return ex instanceof DownloadSwitchException || ex.getCause() instanceof DownloadSwitchException;
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

    private static String finalizeFileName(String fileName, DlcRemoteEntry entry, String url) {
        String name = fileName;
        if (name == null || name.isBlank()) {
            String fallback = entry.getFileNameHint();
            if (fallback == null || fallback.isBlank()) {
                fallback = entry.getDlcIdentifier();
            }
            if (fallback == null || fallback.isBlank()) {
                fallback = "dlc-download";
            }
            name = fallback;
        }
        if (!name.contains(".")) {
            String extension = guessExtension(entry);
            if (extension != null) {
                name += extension;
            }
        }
        return name;
    }

    private static String guessExtension(DlcRemoteEntry entry) {
        if (entry == null || entry.getType() == null) {
            return null;
        }
        return switch (entry.getType()) {
            case MOD -> ".jar";
            case RESOURCE_PACK, DATA_PACK, TACZ_PACK, DLC_PACK, UNKNOWN -> ".zip";
        };
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
        private final DlcRemoteEntry entry;
        private final java.util.concurrent.atomic.AtomicInteger completed;
        private final int total;

        private BatchEntryProgress(BatchProgressListener listener,
                                   DlcRemoteEntry entry,
                                   java.util.concurrent.atomic.AtomicInteger completed,
                                   int total) {
            this.listener = listener;
            this.entry = entry;
            this.completed = completed;
            this.total = total;
        }

        @Override
        public void onAttemptStart(int attemptId) {
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
        final List<DlcRemoteEntry> failed;
        final List<EntryError> errors;
        final int success;

        private BatchRunResult(List<DlcRemoteEntry> failed, List<EntryError> errors, int success) {
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
        final DlcRemoteEntry entry;
        final String fileName;
        final Throwable error;
        final boolean canceled;

        private DownloadResult(DlcRemoteEntry entry, String fileName, Throwable error, boolean canceled) {
            this.entry = entry;
            this.fileName = fileName;
            this.error = error;
            this.canceled = canceled;
        }

        static DownloadResult success(DlcRemoteEntry entry, String fileName) {
            return new DownloadResult(entry, fileName, null, false);
        }

        static DownloadResult canceled(DlcRemoteEntry entry) {
            return new DownloadResult(entry, null, null, true);
        }

        static DownloadResult failure(DlcRemoteEntry entry, Throwable error) {
            return new DownloadResult(entry, null, error, false);
        }
    }
}
