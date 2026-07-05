package com.cozooo.dlc_manager.client.download;

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

public final class DlcSharedService {
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);

        default void onStatusKey(String statusKey) {
        }
    }

    public interface AttemptProgressListener extends ProgressListener {
        void onAttemptStart(int attemptId);
    }

    public interface CancelChecker {
        boolean isCanceled();
    }

    public interface SourceSwitchChecker<T> {
        default boolean isSwitchRequested(T entry) {
            return false;
        }

        default void clearSwitchRequest(T entry) {
        }
    }

    public interface BatchProgressListener<T> {
        default void onEntryStart(T entry, int index, int total) {
        }

        default void onEntryAttemptStart(T entry, int attemptId, int index, int total) {
        }

        default void onEntryProgress(T entry, long downloadedBytes, long totalBytes) {
        }

        default void onEntryStatus(T entry, String statusKey, int completed, int total) {
        }

        default void onEntrySuccess(T entry, String fileName, int completed, int total) {
        }

        default void onEntryFailure(T entry, Throwable error, int completed, int total) {
        }

        default void onEntryComplete(T entry) {
        }
    }

    public interface EntryDownloader<T> {
        String downloadOne(T entry,
                           ProgressListener listener,
                           CancelChecker cancelChecker,
                           SourceSwitchChecker<T> switchChecker) throws IOException, InterruptedException;
    }

    public static final class EntryError<T> {
        public final T entry;
        public final Throwable error;

        public EntryError(T entry, Throwable error) {
            this.entry = entry;
            this.error = error;
        }
    }

    public static final class BatchResult<T> {
        public final int total;
        public final int success;
        public final List<EntryError<T>> errors;

        public BatchResult(int total, int success, List<EntryError<T>> errors) {
            this.total = total;
            this.success = success;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    public static final class BatchRunResult<T> {
        public final List<T> failed;
        public final List<EntryError<T>> errors;
        public final int success;

        private BatchRunResult(List<T> failed, List<EntryError<T>> errors, int success) {
            this.failed = failed == null ? List.of() : failed;
            this.errors = errors == null ? List.of() : errors;
            this.success = success;
        }
    }

    public static final class DownloadInfo {
        public final HttpURLConnection connection;
        public final String fileName;
        public final int responseCode;
        public final long totalBytes;
        public final String contentMd5;

        private DownloadInfo(HttpURLConnection connection, String fileName, int responseCode, long totalBytes, String contentMd5) {
            this.connection = connection;
            this.fileName = fileName;
            this.responseCode = responseCode;
            this.totalBytes = totalBytes;
            this.contentMd5 = contentMd5;
        }
    }

    public static class DownloadSwitchException extends IOException {
        public DownloadSwitchException(String message) {
            super(message);
        }
    }

    public static class DownloadCanceledException extends IOException {
        public DownloadCanceledException(String message) {
            super(message);
        }
    }

    private DlcSharedService() {
    }

    public static <T> BatchResult<T> downloadAll(List<T> entries,
                                                 BatchProgressListener<T> listener,
                                                 CancelChecker cancelChecker,
                                                 SourceSwitchChecker<T> switchChecker,
                                                 EntryDownloader<T> downloader) throws InterruptedException {
        List<T> targets = entries == null ? List.of() : entries.stream()
            .filter(entry -> entry != null)
            .toList();
        int total = targets.size();
        if (total == 0) {
            return new BatchResult<>(0, 0, List.of());
        }
        int success = 0;
        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger();
        BatchRunResult<T> first = runDownloadBatch(targets, total, completed, listener, cancelChecker, switchChecker, downloader);
        success += first.success;
        if (isCanceled(cancelChecker)) {
            return new BatchResult<>(total, success, first.errors);
        }
        BatchRunResult<T> second = first.failed.isEmpty()
            ? new BatchRunResult<>(List.of(), List.of(), 0)
            : runDownloadBatch(first.failed, total, completed, listener, cancelChecker, switchChecker, downloader);
        success += second.success;
        return new BatchResult<>(total, success, second.failed.isEmpty() ? List.of() : second.errors);
    }

    private static <T> BatchRunResult<T> runDownloadBatch(List<T> pending,
                                                         int total,
                                                         java.util.concurrent.atomic.AtomicInteger completed,
                                                         BatchProgressListener<T> listener,
                                                         CancelChecker cancelChecker,
                                                         SourceSwitchChecker<T> switchChecker,
                                                         EntryDownloader<T> downloader) throws InterruptedException {
        List<T> failed = new ArrayList<>();
        List<EntryError<T>> errors = new ArrayList<>();
        if (pending == null || pending.isEmpty() || isCanceled(cancelChecker)) {
            return new BatchRunResult<>(failed, errors, 0);
        }
        int maxConcurrentDownloads = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
        if (maxConcurrentDownloads <= 1) {
            int success = 0;
            for (T entry : pending) {
                if (isCanceled(cancelChecker)) {
                    break;
                }
                if (listener != null) {
                    listener.onEntryStart(entry, completed.get(), total);
                }
                try {
                    String fileName = downloader.downloadOne(entry, new BatchEntryProgress<>(listener, entry, completed, total), cancelChecker, switchChecker);
                    int done = completed.incrementAndGet();
                    success++;
                    if (listener != null) {
                        listener.onEntrySuccess(entry, fileName, done, total);
                    }
                } catch (DownloadCanceledException e) {
                    break;
                } catch (Exception e) {
                    failed.add(entry);
                    errors.add(new EntryError<>(entry, e));
                    if (listener != null) {
                        listener.onEntryFailure(entry, e, completed.get(), total);
                    }
                } finally {
                    if (listener != null) {
                        listener.onEntryComplete(entry);
                    }
                }
            }
            return new BatchRunResult<>(failed, errors, success);
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentDownloads);
        ExecutorCompletionService<DownloadResult<T>> completionService = new ExecutorCompletionService<>(executor);
        for (T entry : pending) {
            completionService.submit(() -> {
                if (listener != null) {
                    listener.onEntryStart(entry, completed.get(), total);
                }
                try {
                    String fileName = downloader.downloadOne(entry, new BatchEntryProgress<>(listener, entry, completed, total), cancelChecker, switchChecker);
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
                DownloadResult<T> result;
                try {
                    Future<DownloadResult<T>> future = completionService.take();
                    result = future.get();
                } catch (Exception e) {
                    T fallback = pending.get(Math.min(i, pending.size() - 1));
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
                    errors.add(new EntryError<>(result.entry, result.error));
                    if (listener != null) {
                        listener.onEntryFailure(result.entry, result.error, completed.get(), total);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return new BatchRunResult<>(failed, errors, success);
    }

    public static DlcDownloadService.ProgressListener toDownloadProgress(ProgressListener listener) {
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

    public static void notifyProgress(ProgressListener listener, long downloadedBytes, long totalBytes) {
        if (listener != null) {
            listener.onProgress(downloadedBytes, totalBytes);
        }
    }

    public static void checkCanceled(CancelChecker cancelChecker) throws DownloadCanceledException {
        if (isCanceled(cancelChecker)) {
            throw new DownloadCanceledException("canceled");
        }
    }

    public static boolean isCanceled(CancelChecker cancelChecker) {
        return cancelChecker != null && cancelChecker.isCanceled();
    }

    public static <T> boolean isSwitchRequested(SourceSwitchChecker<T> switchChecker, T entry) {
        return switchChecker != null && switchChecker.isSwitchRequested(entry);
    }

    public static <T> void clearSwitchRequest(SourceSwitchChecker<T> switchChecker, T entry) {
        if (switchChecker != null) {
            switchChecker.clearSwitchRequest(entry);
        }
    }

    public static boolean isSwitchException(IOException ex) {
        return ex instanceof DownloadSwitchException || ex.getCause() instanceof DownloadSwitchException;
    }

    public static DownloadInfo openDownload(String url, long offset) throws IOException {
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

    public static void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public static Path downloadCandidate(DlcDownloadCandidate candidate,
                                         Path target,
                                         String fileName,
                                         boolean shouldProbe,
                                         ProgressListener listener,
                                         CancelChecker cancelChecker) throws IOException, InterruptedException {
        Path downloaded = shouldProbe
            ? DlcDownloadService.download(candidate.url, target.getParent(), fileName,
                listener == null ? null : toDownloadProgress(listener),
                cancelChecker == null ? null : cancelChecker::isCanceled)
            : DlcDownloadService.downloadKnown(candidate.url, target.getParent(), fileName, candidate.expectedSize,
                listener == null ? null : toDownloadProgress(listener),
                cancelChecker == null ? null : cancelChecker::isCanceled);
        return downloaded.equals(target)
            ? downloaded
            : Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void verifyDownloadedFile(Path file, DlcDownloadCandidate candidate, DownloadInfo info,
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

    private static final class BatchEntryProgress<T> implements AttemptProgressListener {
        private final BatchProgressListener<T> listener;
        private final T entry;
        private final java.util.concurrent.atomic.AtomicInteger completed;
        private final int total;

        private BatchEntryProgress(BatchProgressListener<T> listener,
                                   T entry,
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

    private static final class DownloadResult<T> {
        final T entry;
        final String fileName;
        final Throwable error;
        final boolean canceled;

        private DownloadResult(T entry, String fileName, Throwable error, boolean canceled) {
            this.entry = entry;
            this.fileName = fileName;
            this.error = error;
            this.canceled = canceled;
        }

        static <T> DownloadResult<T> success(T entry, String fileName) {
            return new DownloadResult<>(entry, fileName, null, false);
        }

        static <T> DownloadResult<T> canceled(T entry) {
            return new DownloadResult<>(entry, null, null, true);
        }

        static <T> DownloadResult<T> failure(T entry, Throwable error) {
            return new DownloadResult<>(entry, null, error, false);
        }
    }
}
