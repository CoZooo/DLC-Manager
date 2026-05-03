package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcDownloadService {
    private DlcDownloadService() {
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    @FunctionalInterface
    public interface CancelChecker {
        boolean isCanceled();
    }

    public static String suggestFileName(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("empty url");
        }
        DownloadProbe probe = probeDownload(url);
        String fileName = probe.fileNameHint;
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(url);
        }
        return DlcDownloadUtil.sanitizeFileName(fileName);
    }

    public static Path download(String url, Path outputDir, String desiredFileName, ProgressListener listener, CancelChecker cancelChecker)
        throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("empty url");
        }
        if (outputDir == null) {
            throw new IOException("missing output dir");
        }
        Files.createDirectories(outputDir);
        DownloadProbe probe = probeDownload(url);
        String fileName = desiredFileName;
        if (fileName == null || fileName.isBlank()) {
            fileName = probe.fileNameHint;
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(url);
        }
        fileName = DlcDownloadUtil.sanitizeFileName(fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = "download.bin";
        }
        Path target = outputDir.resolve(fileName);
        Path temp = outputDir.resolve(fileName + ".part");

        if (listener != null) {
            listener.onProgress(0L, probe.totalBytes);
        }
        if (probe.supportsRanges
            && probe.totalBytes > 0
            && probe.totalBytes >= DlcPathConfig.getMinSegmentBytes()
            && DlcPathConfig.getMaxSegmentsPerFile() > 1) {
            Files.deleteIfExists(temp);
            try {
                downloadSegmented(url, temp, probe.totalBytes, listener, cancelChecker);
            } catch (SegmentDownloadException ex) {
                Files.deleteIfExists(temp);
                downloadSingleWithRetry(url, temp, probe.totalBytes, 0L, listener, cancelChecker);
            } catch (IOException ex) {
                Files.deleteIfExists(temp);
                throw ex;
            }
        } else {
            long existing = Files.exists(temp) ? Files.size(temp) : 0L;
            downloadSingleWithRetry(url, temp, probe.totalBytes, existing, listener, cancelChecker);
        }

        if (cancelChecker != null && cancelChecker.isCanceled()) {
            Files.deleteIfExists(temp);
            throw new DownloadCanceledException("canceled");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static Path downloadKnown(String url, Path outputDir, String desiredFileName, long expectedSize,
                                     ProgressListener listener, CancelChecker cancelChecker) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("empty url");
        }
        if (outputDir == null) {
            throw new IOException("missing output dir");
        }
        Files.createDirectories(outputDir);
        String fileName = desiredFileName;
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(url);
        }
        fileName = DlcDownloadUtil.sanitizeFileName(fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = "download.bin";
        }
        Path target = outputDir.resolve(fileName);
        Path temp = outputDir.resolve(fileName + ".part");
        long total = expectedSize > 0 ? expectedSize : -1L;
        if (listener != null) {
            listener.onProgress(0L, total);
        }
        RangeProbe rangeProbe = total > 0
            && total >= DlcPathConfig.getMinSegmentBytes()
            && DlcPathConfig.getMaxSegmentsPerFile() > 1
            ? probeRangeSupport(url)
            : RangeProbe.unsupported(url);
        if (rangeProbe.supportsRanges) {
            Files.deleteIfExists(temp);
            try {
                downloadSegmented(rangeProbe.url, temp, total, listener, cancelChecker);
            } catch (SegmentDownloadException ex) {
                Files.deleteIfExists(temp);
                downloadSingleWithRetry(rangeProbe.url, temp, total, 0L, listener, cancelChecker);
            } catch (IOException ex) {
                Files.deleteIfExists(temp);
                throw ex;
            }
        } else {
            long existing = Files.exists(temp) ? Files.size(temp) : 0L;
            downloadSingleWithRetry(url, temp, total, existing, listener, cancelChecker);
        }
        if (cancelChecker != null && cancelChecker.isCanceled()) {
            Files.deleteIfExists(temp);
            throw new DownloadCanceledException("canceled");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static void downloadSingleWithRetry(String url, Path temp, long knownTotal, long existing, ProgressListener listener,
                                                CancelChecker cancelChecker) throws IOException {
        int maxRetry = Math.max(1, DlcPathConfig.getMaxRetryCount());
        IOException lastError = null;
        long offset = Math.max(0L, existing);
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw new DownloadCanceledException("canceled");
            }
            DownloadInfo info = null;
            try {
                info = openDownload(url, offset);
                if (info.responseCode >= 400) {
                    throw new IOException("http " + info.responseCode);
                }
                long total = knownTotal > 0 ? knownTotal : info.totalBytes;
                if (offset > 0 && info.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    offset = 0L;
                    Files.deleteIfExists(temp);
                    closeQuietly(info.connection);
                    info = openDownload(url, 0L);
                    if (info.responseCode >= 400) {
                        throw new IOException("http " + info.responseCode);
                    }
                    total = knownTotal > 0 ? knownTotal : info.totalBytes;
                }
                downloadToFile(info.connection, temp, offset, total, listener, cancelChecker);
                return;
            } catch (DownloadCanceledException ex) {
                throw ex;
            } catch (IOException ex) {
                lastError = ex;
                offset = Files.exists(temp) ? Files.size(temp) : 0L;
            } finally {
                if (info != null) {
                    closeQuietly(info.connection);
                }
            }
        }
        throw lastError == null ? new IOException("download failed") : lastError;
    }

    private static DownloadInfo openDownload(String url, long offset) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
        connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
        connection.setRequestProperty("User-Agent", "DLC-Manager");
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=" + offset + "-");
        }
        connection.connect();
        int code = connection.getResponseCode();
        long contentLength = connection.getContentLengthLong();
        long total = contentLength > 0 ? contentLength + Math.max(0L, offset) : -1L;
        String fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.getURL().toString());
        }
        return new DownloadInfo(connection, fileName, code, total);
    }

    private static void downloadToFile(HttpURLConnection connection, Path target, long offset, long totalBytes,
                                       ProgressListener listener, CancelChecker cancelChecker) throws IOException {
        if (connection == null) {
            throw new IOException("missing connection");
        }
        long total = totalBytes > 0 ? totalBytes : connection.getContentLengthLong();
        long read = offset;
        if (listener != null) {
            listener.onProgress(read, total);
        }
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = Files.newOutputStream(target,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.WRITE,
                 offset > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) >= 0) {
                if (cancelChecker != null && cancelChecker.isCanceled()) {
                    throw new DownloadCanceledException("canceled");
                }
                out.write(buffer, 0, len);
                read += len;
                if (listener != null) {
                    listener.onProgress(read, total);
                }
            }
        } finally {
            connection.disconnect();
        }
        if (total > 0 && read < total) {
            throw new IOException("download incomplete");
        }
    }

    private static DownloadProbe probeDownload(String url) throws IOException {
        long total = -1L;
        boolean supportsRange = false;
        String fileName = null;
        HttpURLConnection head = null;
        try {
            head = (HttpURLConnection) new URL(url).openConnection();
            head.setInstanceFollowRedirects(true);
            head.setRequestMethod("HEAD");
            head.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            head.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            head.setRequestProperty("User-Agent", "DLC-Manager");
            head.connect();
            fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(head);
            if (fileName == null || fileName.isBlank()) {
                fileName = DlcDownloadUtil.extractFileNameFromUrl(head.getURL().toString());
            }
            long len = head.getContentLengthLong();
            if (len > 0) {
                total = len;
            }
            String acceptRanges = head.getHeaderField("Accept-Ranges");
            supportsRange = acceptRanges != null && acceptRanges.toLowerCase(Locale.ROOT).contains("bytes");
        } catch (Exception ignored) {
        } finally {
            closeQuietly(head);
        }
        if (supportsRange && total > 0) {
            return new DownloadProbe(total, true, fileName);
        }
        HttpURLConnection range = null;
        try {
            range = (HttpURLConnection) new URL(url).openConnection();
            range.setInstanceFollowRedirects(true);
            range.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            range.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            range.setRequestProperty("User-Agent", "DLC-Manager");
            range.setRequestProperty("Range", "bytes=0-0");
            range.connect();
            int code = range.getResponseCode();
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                supportsRange = true;
                String contentRange = range.getHeaderField("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    String[] parts = contentRange.split("/");
                    String last = parts[parts.length - 1];
                    try {
                        long parsed = Long.parseLong(last);
                        if (parsed > 0) {
                            total = parsed;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(range);
        }
        return new DownloadProbe(total, supportsRange, fileName);
    }

    private static RangeProbe probeRangeSupport(String url) {
        String resolved = resolveRedirectTarget(url, 0);
        return probeFinalRangeSupport(resolved);
    }

    private static String resolveRedirectTarget(String url, int redirects) {
        if (redirects > 8 || url == null || url.isBlank()) {
            return url;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.connect();
            int code = connection.getResponseCode();
            if (!isRedirect(code)) {
                return url;
            }
            String location = connection.getHeaderField("Location");
            String redirected = resolveRedirectUrl(url, location);
            closeQuietly(connection);
            connection = null;
            return resolveRedirectTarget(redirected, redirects + 1);
        } catch (Exception ignored) {
            return url;
        } finally {
            closeQuietly(connection);
        }
    }

    private static RangeProbe probeFinalRangeSupport(String url) {
        if (url == null || url.isBlank()) {
            return RangeProbe.unsupported(url);
        }
        HttpURLConnection range = null;
        try {
            range = (HttpURLConnection) new URL(url).openConnection();
            range.setInstanceFollowRedirects(false);
            range.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            range.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            range.setRequestProperty("User-Agent", "DLC-Manager");
            range.setRequestProperty("Range", "bytes=0-0");
            range.connect();
            int code = range.getResponseCode();
            return code == HttpURLConnection.HTTP_PARTIAL
                ? RangeProbe.supported(url)
                : RangeProbe.unsupported(url);
        } catch (Exception ignored) {
            return RangeProbe.unsupported(url);
        } finally {
            closeQuietly(range);
        }
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
            || code == HttpURLConnection.HTTP_MOVED_TEMP
            || code == HttpURLConnection.HTTP_SEE_OTHER
            || code == 307
            || code == 308;
    }

    private static String resolveRedirectUrl(String baseUrl, String location) {
        if (location == null || location.isBlank()) {
            return baseUrl;
        }
        try {
            return URI.create(baseUrl).resolve(location).toString();
        } catch (IllegalArgumentException ignored) {
            return location;
        }
    }

    private static void downloadSegmented(String url, Path tempFile, long totalBytes, ProgressListener listener, CancelChecker cancelChecker)
        throws IOException {
        int maxSegments = Math.max(1, DlcPathConfig.getMaxSegmentsPerFile());
        int segments = (int) Math.max(1L, Math.min((long) maxSegments, Math.max(1L, totalBytes / Math.max(1L, DlcPathConfig.getMinSegmentBytes()))));
        long segmentSize = Math.max(1L, totalBytes / segments);
        try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
            raf.setLength(totalBytes);
        }
        ExecutorService executor = Executors.newFixedThreadPool(segments);
        List<Future<?>> futures = new ArrayList<>();
        AtomicLong totalRead = new AtomicLong(0L);
        int maxRetries = Math.max(1, DlcPathConfig.getMaxRetryCount());
        for (int i = 0; i < segments; i++) {
            long start = i * segmentSize;
            long end = (i == segments - 1) ? totalBytes - 1 : (start + segmentSize - 1);
            long segmentTotal = end - start + 1;
            futures.add(executor.submit(() -> {
                long segmentRead = 0L;
                int attempts = 0;
                IOException lastError = null;
                while (segmentRead < segmentTotal) {
                    attempts++;
                    if (attempts > maxRetries) {
                        throw new SegmentDownloadException("segment retry exceeded", lastError);
                    }
                    if (cancelChecker != null && cancelChecker.isCanceled()) {
                        throw new DownloadCanceledException("canceled");
                    }
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setInstanceFollowRedirects(true);
                        connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
                        connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
                        connection.setRequestProperty("User-Agent", "DLC-Manager");
                        long rangeStart = start + segmentRead;
                        connection.setRequestProperty("Range", "bytes=" + rangeStart + "-" + end);
                        connection.connect();
                        int code = connection.getResponseCode();
                        if (code != HttpURLConnection.HTTP_PARTIAL) {
                            throw new IOException("range not supported");
                        }
                        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                             RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
                            raf.seek(rangeStart);
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = in.read(buffer)) >= 0) {
                                if (cancelChecker != null && cancelChecker.isCanceled()) {
                                    throw new DownloadCanceledException("canceled");
                                }
                                raf.write(buffer, 0, len);
                                segmentRead += len;
                                if (listener != null) {
                                    long allRead = totalRead.addAndGet(len);
                                    listener.onProgress(allRead, totalBytes);
                                }
                            }
                        }
                    } catch (IOException ex) {
                        lastError = ex;
                        continue;
                    } finally {
                        closeQuietly(connection);
                    }
                }
                return null;
            }));
        }
        executor.shutdown();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ex) {
                for (Future<?> pending : futures) {
                    pending.cancel(true);
                }
                Throwable cause = ex.getCause();
                if (cause instanceof DownloadCanceledException canceled) {
                    throw canceled;
                }
                if (cause instanceof SegmentDownloadException segment) {
                    throw segment;
                }
                throw new SegmentDownloadException("segment failed", ex);
            }
        }
    }

    private static void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }

    private record DownloadInfo(HttpURLConnection connection, String fileNameHint, int responseCode, long totalBytes) {
    }

    private record DownloadProbe(long totalBytes, boolean supportsRanges, String fileNameHint) {
    }

    private record RangeProbe(boolean supportsRanges, String url) {
        private static RangeProbe supported(String url) {
            return new RangeProbe(true, url);
        }

        private static RangeProbe unsupported(String url) {
            return new RangeProbe(false, url);
        }
    }

    public static class DownloadCanceledException extends IOException {
        public DownloadCanceledException(String message) {
            super(message);
        }
    }

    public static class SegmentDownloadException extends IOException {
        public SegmentDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
