package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcDownloadService {
    private static final String MEDIAFILEZ_HOST = "mediafilez.forgecdn.net";
    private static final String EDGE_FORGECDN_HOST = "edge.forgecdn.net";
    private static final String MIRROR_HOST = "mod.mcimirror.top";
    private static final long MIRROR_COMPARE_WINDOW_MS = 3000L;
    private static final int MIRROR_MAX_COMPARISONS = 3;
    private static final int MIRROR_DISABLE_FAILURES = 2;
    private static final long SPEED_WINDOW_MS = 1000L;
    private static final long BYTES_PER_MIB = 1024L * 1024L;
    private static final long MIN_MIRROR_SPEED_CHECK_BYTES = BYTES_PER_MIB;
    private static final Object MIRROR_LOCK = new Object();
    private static int mirrorComparisons;
    private static int officialWins;
    private static int officialFastWins;
    private static int mirrorWins;
    private static int mirrorFailures;
    private static Boolean selectedMirror;
    private static boolean mirrorDisabled;

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
        MirrorPlan mirrorPlan = mirrorPlan(url, fileName, total);
        String downloadUrl = preselectedDownloadUrl(url, mirrorPlan);
        if (listener != null) {
            listener.onProgress(0L, total);
        }
        RangeProbe rangeProbe = total > 0
            && total >= DlcPathConfig.getMinSegmentBytes()
            && DlcPathConfig.getMaxSegmentsPerFile() > 1
            ? probeRangeSupport(downloadUrl)
            : RangeProbe.unsupported(downloadUrl);
        if (mirrorPlan != null && downloadUrl.equals(url) && rangeProbe.supportsRanges && !hasSelectedOfficial()) {
            Files.deleteIfExists(temp);
            try {
                downloadSegmentedMirrorAware(rangeProbe.url, mirrorPlan, temp, total, listener, cancelChecker);
            } catch (SegmentDownloadException ex) {
                Files.deleteIfExists(temp);
                downloadSingleMirrorAware(rangeProbe.url, mirrorPlan, temp, total, listener, cancelChecker);
            } catch (IOException ex) {
                Files.deleteIfExists(temp);
                throw ex;
            }
        } else if (rangeProbe.supportsRanges) {
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
        } else if (mirrorPlan != null && downloadUrl.equals(url) && !hasSelectedOfficial() && !Files.exists(temp)) {
            downloadSingleMirrorAware(downloadUrl, mirrorPlan, temp, total, listener, cancelChecker);
        } else {
            long existing = Files.exists(temp) ? Files.size(temp) : 0L;
            downloadSingleWithRetry(downloadUrl, temp, total, existing, listener, cancelChecker);
        }
        if (cancelChecker != null && cancelChecker.isCanceled()) {
            Files.deleteIfExists(temp);
            throw new DownloadCanceledException("canceled");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static MirrorPlan mirrorPlan(String url, String fileName, long totalBytes) {
        if (!DlcDownloadUtil.isLikelyFileName(fileName)
            || totalBytes <= MIN_MIRROR_SPEED_CHECK_BYTES
            || !isCurseForgeCdnUrl(url)) {
            return null;
        }
        String mirrorUrl = mirrorDownloadUrl(url);
        if (mirrorUrl == null || mirrorUrl.isBlank()) {
            return null;
        }
        SpeedRule speedRule = mirrorSpeedRule(totalBytes);
        return new MirrorPlan(mirrorUrl, totalBytes, speedRule.timeoutSpeed, speedRule.bucketSpeed, speedRule.requiredSpeed);
    }

    private static String preselectedDownloadUrl(String url, MirrorPlan mirrorPlan) {
        if (mirrorPlan == null) {
            return url;
        }
        synchronized (MIRROR_LOCK) {
            if (mirrorDisabled) {
                return url;
            }
            if (selectedMirror != null) {
                return selectedMirror ? mirrorPlan.mirrorUrl : url;
            }
        }
        return url;
    }

    private static boolean hasSelectedOfficial() {
        synchronized (MIRROR_LOCK) {
            return Boolean.FALSE.equals(selectedMirror) || mirrorDisabled || mirrorComparisons >= MIRROR_MAX_COMPARISONS;
        }
    }

    private static MirrorProbe startMirrorProbe(double officialSpeed, MirrorPlan mirrorPlan, CancelChecker cancelChecker) {
        if (officialSpeed >= mirrorPlan.requiredSpeed) {
            recordOfficialFastIfStable(officialSpeed, mirrorPlan);
            return null;
        }
        synchronized (MIRROR_LOCK) {
            if (mirrorDisabled) {
                return null;
            }
            if (selectedMirror != null) {
                return selectedMirror ? MirrorProbe.selected(mirrorPlan.mirrorUrl) : null;
            }
            if (mirrorComparisons >= MIRROR_MAX_COMPARISONS) {
                return null;
            }
            mirrorComparisons++;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return new MirrorProbe(
            mirrorPlan.mirrorUrl,
            executor.submit(() -> probeDownloadSpeed(mirrorPlan.mirrorUrl, MIRROR_COMPARE_WINDOW_MS, cancelChecker)),
            executor,
            false
        );
    }

    private static boolean finishMirrorProbe(double officialSpeed, MirrorProbe probe) {
        if (probe.preselected) {
            return true;
        }
        double mirrorSpeed;
        try {
            mirrorSpeed = probe.future == null ? 0.0 : probe.future.get();
        } catch (Exception ignored) {
            mirrorSpeed = 0.0;
        } finally {
            if (probe.executor != null) {
                probe.executor.shutdownNow();
            }
        }
        synchronized (MIRROR_LOCK) {
            if (mirrorSpeed <= 0.0) {
                mirrorFailures++;
                if (mirrorFailures >= MIRROR_DISABLE_FAILURES) {
                    mirrorDisabled = true;
                }
            } else if (mirrorSpeed > officialSpeed) {
                mirrorWins++;
            } else {
                officialWins++;
            }
            if (mirrorWins >= 2) {
                selectedMirror = true;
            } else if (officialWins >= 2) {
                selectedMirror = false;
            }
            if (selectedMirror != null) {
                return selectedMirror;
            }
        }
        return mirrorSpeed > 0.0 && mirrorSpeed > officialSpeed;
    }

    private static void recordOfficialFastIfStable(double officialSpeed, MirrorPlan mirrorPlan) {
        if (mirrorPlan.totalBytes <= 10L * BYTES_PER_MIB
            || mirrorPlan.bucketSpeed <= mirrorPlan.timeoutSpeed
            || officialSpeed < mirrorPlan.bucketSpeed) {
            return;
        }
        synchronized (MIRROR_LOCK) {
            if (selectedMirror != null || mirrorDisabled) {
                return;
            }
            officialFastWins++;
            if (officialFastWins >= 2) {
                selectedMirror = false;
            }
        }
    }

    private static SpeedRule mirrorSpeedRule(long totalBytes) {
        double taskTimeoutSeconds = Math.max(1.0, DlcPathConfig.getTaskTimeoutMs() / 1000.0);
        double timeoutSpeed = totalBytes / taskTimeoutSeconds;
        double bucketSpeed;
        if (totalBytes < 10L * BYTES_PER_MIB) {
            bucketSpeed = totalBytes / 10.0;
        } else if (totalBytes <= 20L * BYTES_PER_MIB) {
            bucketSpeed = 1.0 * BYTES_PER_MIB;
        } else {
            bucketSpeed = 2.0 * BYTES_PER_MIB;
        }
        return new SpeedRule(timeoutSpeed, bucketSpeed, Math.max(timeoutSpeed, bucketSpeed));
    }

    private static double probeDownloadSpeed(String url, long windowMs, CancelChecker cancelChecker) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            connection.setReadTimeout((int) windowMs);
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.setRequestProperty("Range", "bytes=0-");
            connection.connect();
            if (connection.getResponseCode() >= 400) {
                return 0.0;
            }
            long start = System.nanoTime();
            long deadline = start + TimeUnit.MILLISECONDS.toNanos(windowMs);
            long bytes = 0L;
            byte[] buffer = new byte[8192];
            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
                while (System.nanoTime() < deadline) {
                    if (cancelChecker != null && cancelChecker.isCanceled()) {
                        throw new DownloadCanceledException("canceled");
                    }
                    int read = in.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    bytes += read;
                }
            }
            long elapsed = Math.max(1L, System.nanoTime() - start);
            return bytes * 1_000_000_000.0 / elapsed;
        } catch (DownloadCanceledException ex) {
            throw ex;
        } catch (Exception ignored) {
            return 0.0;
        } finally {
            closeQuietly(connection);
        }
    }

    private static boolean isCurseForgeCdnUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            return MEDIAFILEZ_HOST.equalsIgnoreCase(host) || EDGE_FORGECDN_HOST.equalsIgnoreCase(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String mirrorDownloadUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!MEDIAFILEZ_HOST.equalsIgnoreCase(uri.getHost()) && !EDGE_FORGECDN_HOST.equalsIgnoreCase(uri.getHost())) {
                return null;
            }
            return new URI(
                uri.getScheme() == null ? "https" : uri.getScheme(),
                uri.getUserInfo(),
                MIRROR_HOST,
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
            ).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void downloadSingleMirrorAware(String url, MirrorPlan mirrorPlan, Path temp, long knownTotal,
                                                  ProgressListener listener, CancelChecker cancelChecker) throws IOException {
        DownloadInfo info = null;
        try {
            info = openDownload(url, 0L);
            if (info.responseCode >= 400) {
                throw new IOException("http " + info.responseCode);
            }
            downloadToFileMirrorAware(info.connection, temp, knownTotal, listener, cancelChecker, mirrorPlan);
        } catch (SwitchToMirrorException ex) {
            if (info != null) {
                closeQuietly(info.connection);
            }
            Files.deleteIfExists(temp);
            downloadSingleWithRetry(ex.mirrorUrl, temp, knownTotal, 0L, listener, cancelChecker);
        } catch (DownloadCanceledException ex) {
            throw ex;
        } catch (IOException ex) {
            long existing = Files.exists(temp) ? Files.size(temp) : 0L;
            downloadSingleWithRetry(url, temp, knownTotal, existing, listener, cancelChecker);
        } finally {
            if (info != null) {
                closeQuietly(info.connection);
            }
        }
    }

    private static void downloadToFileMirrorAware(HttpURLConnection connection, Path target, long totalBytes,
                                                  ProgressListener listener, CancelChecker cancelChecker, MirrorPlan mirrorPlan)
        throws IOException {
        if (connection == null) {
            throw new IOException("missing connection");
        }
        long total = totalBytes > 0 ? totalBytes : connection.getContentLengthLong();
        long read = 0L;
        if (listener != null) {
            listener.onProgress(read, total);
        }
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = Files.newOutputStream(target,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.WRITE,
                 StandardOpenOption.TRUNCATE_EXISTING)) {
            read = copyToFileWithMirrorCheck(in, out, total, read, listener, cancelChecker, mirrorPlan);
        } finally {
            connection.disconnect();
        }
        if (total > 0 && read < total) {
            throw new IOException("download incomplete");
        }
    }

    private static long copyToFileWithMirrorCheck(BufferedInputStream input, OutputStream output, long total, long initialRead,
                                                  ProgressListener listener, CancelChecker cancelChecker, MirrorPlan mirrorPlan)
        throws IOException {
        byte[] buffer = new byte[8192];
        long read = initialRead;
        long start = System.nanoTime();
        long windowStart = start;
        long windowBytes = 0L;
        double maxSpeed = 0.0;
        boolean checked = false;
        MirrorProbe mirrorProbe = null;
        long compareDeadline = start + TimeUnit.MILLISECONDS.toNanos(MIRROR_COMPARE_WINDOW_MS);
        try {
            while (true) {
                int len = input.read(buffer);
                if (len < 0) {
                    return read;
                }
                if (cancelChecker != null && cancelChecker.isCanceled()) {
                    throw new DownloadCanceledException("canceled");
                }
                output.write(buffer, 0, len);
                read += len;
                windowBytes += len;
                if (listener != null) {
                    listener.onProgress(read, total);
                }
                long now = System.nanoTime();
                if (now - windowStart >= TimeUnit.MILLISECONDS.toNanos(SPEED_WINDOW_MS)) {
                    maxSpeed = Math.max(maxSpeed, windowBytes * 1_000_000_000.0 / Math.max(1L, now - windowStart));
                    windowStart = now;
                    windowBytes = 0L;
                }
                if (!checked && now >= compareDeadline) {
                    checked = true;
                    if (windowBytes > 0) {
                        maxSpeed = Math.max(maxSpeed, windowBytes * 1_000_000_000.0 / Math.max(1L, now - windowStart));
                    }
                    mirrorProbe = startMirrorProbe(maxSpeed, mirrorPlan, cancelChecker);
                    if (mirrorProbe != null && mirrorProbe.preselected) {
                        throw new SwitchToMirrorException(mirrorPlan.mirrorUrl);
                    }
                }
                if (mirrorProbe != null && mirrorProbe.future != null && mirrorProbe.future.isDone()) {
                    if (windowBytes > 0) {
                        maxSpeed = Math.max(maxSpeed, windowBytes * 1_000_000_000.0 / Math.max(1L, now - windowStart));
                    }
                    if (finishMirrorProbe(maxSpeed, mirrorProbe)) {
                        throw new SwitchToMirrorException(mirrorProbe.mirrorUrl);
                    }
                    mirrorProbe = null;
                }
            }
        } finally {
            if (mirrorProbe != null && mirrorProbe.executor != null) {
                mirrorProbe.executor.shutdownNow();
            }
        }
    }

    private static void downloadSegmentedMirrorAware(String url, MirrorPlan mirrorPlan, Path tempFile, long totalBytes,
                                                     ProgressListener listener, CancelChecker cancelChecker) throws IOException {
        InitialRangeResult initial = downloadInitialRangeForMirrorCheck(url, mirrorPlan, tempFile, totalBytes, listener, cancelChecker);
        if (initial.completed) {
            return;
        }
        if (initial.switchToMirror) {
            Files.deleteIfExists(tempFile);
            downloadSegmented(mirrorPlan.mirrorUrl, tempFile, totalBytes, listener, cancelChecker);
            return;
        }
        downloadSegmented(url, tempFile, totalBytes, listener, cancelChecker, initial.downloadedBytes);
    }

    private static InitialRangeResult downloadInitialRangeForMirrorCheck(String url, MirrorPlan mirrorPlan, Path tempFile,
                                                                         long totalBytes, ProgressListener listener,
                                                                         CancelChecker cancelChecker) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
            raf.setLength(totalBytes);
        }
        HttpURLConnection connection = null;
        long read = 0L;
        double maxSpeed = 0.0;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs());
            connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs());
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.setRequestProperty("Range", "bytes=0-");
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("range not supported");
            }
            long start = System.nanoTime();
            long windowStart = start;
            long windowBytes = 0L;
            boolean checked = false;
            MirrorProbe mirrorProbe = null;
            long compareDeadline = start + TimeUnit.MILLISECONDS.toNanos(MIRROR_COMPARE_WINDOW_MS);
            byte[] buffer = new byte[8192];
            try {
                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
                    raf.seek(0L);
                    while (true) {
                        long nowBeforeRead = System.nanoTime();
                        if (checked && mirrorProbe == null && nowBeforeRead >= compareDeadline) {
                            return new InitialRangeResult(read, false, false);
                        }
                        if (cancelChecker != null && cancelChecker.isCanceled()) {
                            throw new DownloadCanceledException("canceled");
                        }
                        int len = in.read(buffer);
                        if (len < 0) {
                            return new InitialRangeResult(read, true, false);
                        }
                        raf.write(buffer, 0, len);
                        read += len;
                        windowBytes += len;
                        if (listener != null) {
                            listener.onProgress(read, totalBytes);
                        }
                        if (read >= totalBytes) {
                            return new InitialRangeResult(read, true, false);
                        }
                        long now = System.nanoTime();
                        if (now - windowStart >= TimeUnit.MILLISECONDS.toNanos(SPEED_WINDOW_MS)) {
                            maxSpeed = Math.max(maxSpeed, windowBytes * 1_000_000_000.0 / Math.max(1L, now - windowStart));
                            windowStart = now;
                            windowBytes = 0L;
                        }
                        if (!checked && now >= compareDeadline) {
                            checked = true;
                            if (windowBytes > 0) {
                                maxSpeed = Math.max(maxSpeed, windowBytes * 1_000_000_000.0 / Math.max(1L, now - windowStart));
                            }
                            mirrorProbe = startMirrorProbe(maxSpeed, mirrorPlan, cancelChecker);
                            if (mirrorProbe != null && mirrorProbe.preselected) {
                                return new InitialRangeResult(read, false, true);
                            }
                            if (mirrorProbe == null) {
                                return new InitialRangeResult(read, false, false);
                            }
                        }
                        if (mirrorProbe != null && mirrorProbe.future != null && mirrorProbe.future.isDone()) {
                            if (windowBytes > 0) {
                                maxSpeed = Math.max(maxSpeed, windowBytes * 1_000_000_000.0 / Math.max(1L, now - windowStart));
                            }
                            return new InitialRangeResult(read, false, finishMirrorProbe(maxSpeed, mirrorProbe));
                        }
                    }
                }
            } finally {
                if (mirrorProbe != null && mirrorProbe.executor != null) {
                    mirrorProbe.executor.shutdownNow();
                }
            }
        } finally {
            closeQuietly(connection);
        }
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
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
            head = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
            range = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
            range = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
        downloadSegmented(url, tempFile, totalBytes, listener, cancelChecker, 0L);
    }

    private static void downloadSegmented(String url, Path tempFile, long totalBytes, ProgressListener listener, CancelChecker cancelChecker,
                                          long initialDownloadedBytes) throws IOException {
        int maxSegments = Math.max(1, DlcPathConfig.getMaxSegmentsPerFile());
        int segments = (int) Math.max(1L, Math.min((long) maxSegments, Math.max(1L, totalBytes / Math.max(1L, DlcPathConfig.getMinSegmentBytes()))));
        long segmentSize = Math.max(1L, totalBytes / segments);
        try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
            raf.setLength(totalBytes);
        }
        ExecutorService executor = Executors.newFixedThreadPool(segments);
        List<Future<?>> futures = new ArrayList<>();
        long initialRead = Math.max(0L, Math.min(initialDownloadedBytes, totalBytes));
        AtomicLong totalRead = new AtomicLong(initialRead);
        int maxRetries = Math.max(1, DlcPathConfig.getMaxRetryCount());
        for (int i = 0; i < segments; i++) {
            long start = i * segmentSize;
            long end = (i == segments - 1) ? totalBytes - 1 : (start + segmentSize - 1);
            long segmentTotal = end - start + 1;
            futures.add(executor.submit(() -> {
                long segmentRead = Math.max(0L, Math.min(segmentTotal, initialRead - start));
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
                        connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
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

    private record MirrorPlan(String mirrorUrl, long totalBytes, double timeoutSpeed, double bucketSpeed, double requiredSpeed) {
    }

    private record MirrorProbe(String mirrorUrl, Future<Double> future, ExecutorService executor, boolean preselected) {
        private static MirrorProbe selected(String mirrorUrl) {
            return new MirrorProbe(mirrorUrl, null, null, true);
        }
    }

    private record SpeedRule(double timeoutSpeed, double bucketSpeed, double requiredSpeed) {
    }

    private record InitialRangeResult(long downloadedBytes, boolean completed, boolean switchToMirror) {
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

    private static class SwitchToMirrorException extends IOException {
        private final String mirrorUrl;

        private SwitchToMirrorException(String mirrorUrl) {
            super("switch to mirror");
            this.mirrorUrl = mirrorUrl;
        }
    }
}
