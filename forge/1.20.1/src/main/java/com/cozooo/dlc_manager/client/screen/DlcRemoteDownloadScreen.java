package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.remote.CurseForgeClient;
import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DlcRemoteDownloadScreen extends Screen {
    private final Screen parent;
    private final List<DlcRemoteEntry> missing;
    private List<DlcRemoteEntry> previewQueue = List.of();
    private final int maxConcurrentDownloads;
    private final int maxSegmentsPerFile;
    private final long minSegmentBytes;
    private final int maxRetry;
    private final int connectTimeoutMs;
    private final int taskTimeoutMs;
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private final Map<String, Integer> attemptByKey = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger attemptCounter = new java.util.concurrent.atomic.AtomicInteger();

    private Button downloadButton;
    private Button skipButton;

    private volatile boolean downloading;
    private volatile boolean detecting = true;
    private volatile String statusText;
    private volatile String errorText;
    private volatile int totalCount;
    private volatile int completedCount;
    private volatile float currentProgress;
    private int scrollIndex;

    public DlcRemoteDownloadScreen(Screen parent, List<DlcRemoteEntry> missing) {
        super(Component.translatable("dlc_manager.remote.title"));
        this.parent = parent;
        this.missing = missing == null ? List.of() : missing;
        this.maxConcurrentDownloads = Math.max(1, DlcPathConfig.getMaxConcurrentDownloads());
        this.maxSegmentsPerFile = Math.max(1, DlcPathConfig.getMaxSegmentsPerFile());
        this.minSegmentBytes = Math.max(1L, DlcPathConfig.getMinSegmentBytes());
        this.maxRetry = Math.max(1, DlcPathConfig.getMaxRetryCount());
        this.connectTimeoutMs = Math.max(1, DlcPathConfig.getConnectTimeoutMs());
        this.taskTimeoutMs = Math.max(1, DlcPathConfig.getTaskTimeoutMs());
    }

    @Override
    protected void init() {
        if (missing.isEmpty()) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        downloadButton = Button.builder(Component.translatable("dlc_manager.remote.download"), button -> startDownload())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        downloadButton.active = false;
        downloadButton.visible = false;
        skipButton = Button.builder(Component.translatable("dlc_manager.remote.skip"), button -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight).build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(skipButton);
        detecting = true;
        statusText = Component.translatable("dlc_manager.remote.preparing").getString();
        new Thread(this::buildPreviewQueue, "dlc-remote-preview").start();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.remote.desc"), centerX, y, 0xC8C8C8);
        y += 16;
        if (detecting && !downloading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.remote.detecting"), centerX, y, 0xE6E6E6);
            y += 12;
        }

        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = y;
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcRemoteEntry entry = showList.get(i);
            String name = entry.resolveDisplayName();
            guiGraphics.drawString(this.font, "- " + name, 24, y, 0xE6E6E6, false);
            if (downloading && maxConcurrentDownloads > 1) {
                Float progress = progressByKey.get(progressKey(entry));
                if (progress != null) {
                    int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
                    String progressText = percent + "%";
                    int textWidth = this.font.width(progressText);
                    int percentBoxWidth = this.font.width("100%");
                    int barWidth = 36;
                    int barHeight = 6;
                    int percentRight = this.width - 18;
                    int percentLeft = percentRight - percentBoxWidth;
                    int barRight = percentLeft - 6;
                    int barLeft = barRight - barWidth;
                    int barY = y + 2;
                    drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress);
                    guiGraphics.drawString(this.font, progressText, percentRight - textWidth, y, 0xC8C8C8, false);
                }
            }
            y += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);

        if (downloading) {
            y = this.height - 70;
            if (maxConcurrentDownloads <= 1) {
                String status = statusText == null ? "" : statusText;
                guiGraphics.drawCenteredString(this.font, status, centerX, y, 0xE6E6E6);
                y += 12;
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, currentProgress);
                int percent = Math.round(Math.max(0f, Math.min(1f, currentProgress)) * 100f);
                guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
            } else {
                String status = Component.translatable("dlc_manager.remote.downloading_multi").getString();
                guiGraphics.drawCenteredString(this.font, status, centerX, y, 0xE6E6E6);
                y += 12;
                float totalProgress = getTotalProgress();
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, totalProgress);
                int percent = Math.round(Math.max(0f, Math.min(1f, totalProgress)) * 100f);
                guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
            }
        } else if (errorText != null && !errorText.isBlank()) {
            y = this.height - 70;
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xFF6B6B);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = 20 + 18 + 16 + (detecting && !downloading ? 12 : 0);
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = delta > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 12;
        int barWidth = 4;
        int barHeight = Math.max(12, (bottom - top) * maxVisible / totalLines);
        int trackHeight = bottom - top;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = top + (int) ((scrollIndex / (float) maxScroll) * maxTrack);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int bg = 0xFF2B2B2B;
        int fg = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + filled, y + height, fg);
    }

    private void buildPreviewQueue() {
        List<DlcRemoteEntry> queue = new ArrayList<>();
        ArrayDeque<DlcRemoteEntry> work = new ArrayDeque<>(missing);
        Set<String> seen = new HashSet<>();
        while (!work.isEmpty()) {
            DlcRemoteEntry entry = work.poll();
            if (entry == null) {
                continue;
            }
            String key = entry.getUniqueKey();
            if (!seen.add(key)) {
                continue;
            }
            queue.add(entry);
            for (DlcRemoteEntry dep : entry.getRemoteDependencies()) {
                work.add(dep);
            }
            for (DlcRemoteEntry dep : fetchModrinthRequired(entry)) {
                work.add(dep);
            }
        }
        previewQueue = queue;
        detecting = false;
        statusText = Component.translatable("dlc_manager.remote.ready").getString();
        scrollIndex = 0;
        Minecraft.getInstance().execute(() -> {
            if (downloadButton != null) {
                downloadButton.visible = true;
                downloadButton.active = true;
            }
        });
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        errorText = null;
        downloadButton.active = false;
        skipButton.active = false;
        initProgressMap(previewQueue.isEmpty() ? missing : previewQueue);
        new Thread(this::downloadAll, "dlc-remote-download").start();
    }

    private void downloadAll() {
        List<DlcRemoteEntry> targets = previewQueue.isEmpty() ? missing : previewQueue;
        totalCount = targets.size();
        completedCount = 0;
        List<DlcRemoteEntry> failed = runDownloadBatch(targets);
        if (!failed.isEmpty()) {
            failed = runDownloadBatch(failed);
        }
        if (!failed.isEmpty()) {
            downloading = false;
            Minecraft.getInstance().execute(() -> {
                downloadButton.active = true;
                skipButton.active = true;
            });
            return;
        }
        errorText = null;
        downloading = false;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(parent));
    }

    private List<DlcRemoteEntry> fetchModrinthRequired(DlcRemoteEntry entry) {
        List<DlcRemoteEntry> result = new ArrayList<>();
        String versionId = entry.getModrinthVersionId();
        if (versionId == null || versionId.isBlank()) {
            return result;
        }
        try {
            ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
            if (version == null) {
                return result;
            }
            for (ModrinthClient.ModrinthDependency dep : version.requiredDependencies) {
                if (dep == null || dep.versionId == null || dep.versionId.isBlank()) {
                    continue;
                }
                DlcRemoteEntry depEntry = new DlcRemoteEntry(null, entry.getDlcRoot(), entry.getBaseDir(), null);
                depEntry.setModrinthProjectId(dep.projectId == null ? "" : dep.projectId);
                depEntry.setModrinthVersionId(dep.versionId);
                depEntry.setType(entry.getType());
                depEntry.setInList(true);
                depEntry.setHidden(false);
                depEntry.setAllowEnableByAll(true);
                try {
                    ModrinthClient.ModrinthVersion depVersion = ModrinthClient.fetchVersion(dep.versionId);
                    if (depVersion != null && depVersion.fileName != null && !depVersion.fileName.isBlank()) {
                        depEntry.setFileNameHint(depVersion.fileName);
                        if (depEntry.getDlcIdentifier() == null || depEntry.getDlcIdentifier().isBlank()) {
                            depEntry.setDlcIdentifier(depVersion.fileName);
                        }
                    }
                } catch (Exception ignored) {
                    // Best-effort.
                }
                if (depEntry.getDlcIdentifier() == null || depEntry.getDlcIdentifier().isBlank()) {
                    if (dep.projectId != null && !dep.projectId.isBlank()) {
                        depEntry.setDlcIdentifier(dep.projectId);
                    }
                }
                result.add(depEntry);
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    private void downloadEntry(DlcRemoteEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return;
        }
        currentProgress = 0f;
        String progressKey = progressKey(entry);
        List<DownloadCandidate> candidates = buildDownloadCandidates(entry);
        IOException lastError = null;
        Path lastTempFile = null;
        int attemptId = 0;
        for (DownloadCandidate candidate : candidates) {
            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                try {
                    attemptId = attemptCounter.incrementAndGet();
                    attemptByKey.put(progressKey, attemptId);
                    DownloadInfo downloadInfo = openDownload(candidate.url, 0);
                    String headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                    String fileName = candidate.fileNameOverride;
                    boolean fileNameExplicit = entry.isFileNameExplicit()
                        && fileName != null && !fileName.isBlank();
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
                    }
                    fileName = finalizeFileName(fileName, entry, candidate.url);
                    Path target = entry.resolveTargetPath(fileName);
                    if (target == null) {
                        closeQuietly(downloadInfo.connection);
                        throw new IOException("invalid target path");
                    }
                    Files.createDirectories(target.getParent());
                    if (Files.exists(target)) {
                        closeQuietly(downloadInfo.connection);
                        setEntryProgress(progressKey, attemptId, 1f);
                        return;
                    }
                    Path tempFile = target.getParent().resolve(fileName + ".part");
                    lastTempFile = tempFile;
                    long existing = Files.exists(tempFile) ? Files.size(tempFile) : 0L;
                    closeQuietly(downloadInfo.connection);
                    if (existing > 0) {
                        downloadInfo = openDownload(candidate.url, existing);
                        if (downloadInfo.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                            closeQuietly(downloadInfo.connection);
                            existing = 0L;
                            Files.deleteIfExists(tempFile);
                            downloadInfo = openDownload(candidate.url, 0);
                        }
                    } else {
                        downloadInfo = openDownload(candidate.url, 0);
                    }
                    if (downloadInfo.responseCode >= 400) {
                        closeQuietly(downloadInfo.connection);
                        throw new IOException("http " + downloadInfo.responseCode);
                    }
                    if (existing == 0 && maxSegmentsPerFile > 1) {
                        DownloadProbe probe = probeDownload(candidate.url, downloadInfo);
                        if (probe.supportsRanges && probe.totalBytes > 0 && probe.totalBytes >= minSegmentBytes) {
                            downloadSegmented(candidate.url, tempFile, probe.totalBytes, progressKey, attemptId);
                        } else {
                            downloadToFile(downloadInfo.connection, tempFile, existing, downloadInfo.totalBytes, progressKey, attemptId);
                        }
                    } else {
                        downloadToFile(downloadInfo.connection, tempFile, existing, downloadInfo.totalBytes, progressKey, attemptId);
                    }
                    if (downloadInfo.totalBytes <= 0 && Files.size(tempFile) <= 0) {
                        throw new IOException("download empty");
                    }
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

                    setEntryProgress(progressKey, attemptId, 1f);
                    entry.setFileNameHint(fileName);
                    DlcRemoteManager.updateFileNameKey(entry, fileName);
                    DlcRemoteManager.writeInfoFile(entry.getDlcRoot(), entry, fileName);
                    return;
                } catch (IOException ex) {
                    lastError = ex;
                    if (ex instanceof SegmentDownloadException) {
                        setEntryProgress(progressKey, attemptId, 0f);
                        break;
                    }
                    if (attempt >= maxRetry) {
                        setEntryProgress(progressKey, attemptId, 0f);
                    }
                }
            }
        }
        if (attemptId > 0) {
            setEntryProgress(progressKey, attemptId, 0f);
        }
        if (lastTempFile != null) {
            try {
                Files.deleteIfExists(lastTempFile);
            } catch (IOException ignored) {
                // Best-effort.
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    private void downloadToFile(HttpURLConnection connection, Path target, long offset, long totalBytes, String progressKey, int attemptId) throws IOException {
        if (connection == null) {
            throw new IOException("missing download url");
        }
        long total = totalBytes > 0 ? totalBytes : connection.getContentLengthLong();
        if (total > 0 && offset > 0) {
            setEntryProgress(progressKey, attemptId, Math.min(1f, (float) offset / (float) total));
        }
        long read = offset;
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = Files.newOutputStream(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                offset > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) >= 0) {
                out.write(buffer, 0, len);
                read += len;
                if (total > 0) {
                    setEntryProgress(progressKey, attemptId, Math.min(1f, (float) read / (float) total));
                }
            }
        } finally {
            connection.disconnect();
        }
        if (total > 0 && read < total) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
            throw new IOException("download incomplete");
        }
        setEntryProgress(progressKey, attemptId, 1f);
    }

    private DownloadProbe probeDownload(String url, DownloadInfo existingInfo) throws IOException {
        long total = existingInfo != null ? existingInfo.totalBytes : -1L;
        boolean supports = false;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(taskTimeoutMs);
            connection.setRequestProperty("User-Agent", "DLC-Manager");
            connection.connect();
            String accept = connection.getHeaderField("Accept-Ranges");
            supports = accept != null && accept.toLowerCase(java.util.Locale.ROOT).contains("bytes");
            long length = connection.getContentLengthLong();
            if (length > 0) {
                total = length;
            }
            if (supports && total > 0) {
                return new DownloadProbe(total, true);
            }
        } catch (Exception ignored) {
            // Best-effort.
        } finally {
            closeQuietly(connection);
        }
        HttpURLConnection rangeConn = null;
        try {
            rangeConn = (HttpURLConnection) new URL(url).openConnection();
            rangeConn.setInstanceFollowRedirects(true);
            rangeConn.setConnectTimeout(connectTimeoutMs);
            rangeConn.setReadTimeout(taskTimeoutMs);
            rangeConn.setRequestProperty("User-Agent", "DLC-Manager");
            rangeConn.setRequestProperty("Range", "bytes=0-0");
            rangeConn.connect();
            int code = rangeConn.getResponseCode();
            String contentRange = rangeConn.getHeaderField("Content-Range");
            long parsed = parseTotalBytes(contentRange);
            if (code == HttpURLConnection.HTTP_PARTIAL && parsed > 0) {
                return new DownloadProbe(parsed, true);
            }
            if (total <= 0) {
                long length = rangeConn.getContentLengthLong();
                if (length > 0) {
                    total = length;
                }
            }
        } finally {
            closeQuietly(rangeConn);
        }
        return new DownloadProbe(total, false);
    }

    private long parseTotalBytes(String contentRange) {
        if (contentRange == null || contentRange.isBlank()) {
            return -1L;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= contentRange.length()) {
            return -1L;
        }
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static class SegmentDownloadException extends IOException {
        private SegmentDownloadException(String message) {
            super(message);
        }

        private SegmentDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void downloadSegmented(String url, Path tempFile, long totalBytes, String progressKey, int attemptId) throws IOException {
        int segments = Math.max(1, Math.min(maxSegmentsPerFile, (int) Math.min(Integer.MAX_VALUE, totalBytes)));
        long segmentSize = Math.max(1L, totalBytes / segments);
        java.util.concurrent.atomic.AtomicLong readBytes = new java.util.concurrent.atomic.AtomicLong(0L);
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempFile.toFile(), "rw")) {
            raf.setLength(totalBytes);
        }
        ExecutorService executor = Executors.newFixedThreadPool(segments);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            long start = i * segmentSize;
            long end = (i == segments - 1) ? totalBytes - 1 : (start + segmentSize - 1);
            long segmentTotal = end - start + 1;
            futures.add(executor.submit(() -> {
                long segmentRead = 0L;
                int attempts = 0;
                IOException lastError = null;
                while (segmentRead < segmentTotal) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new SegmentDownloadException("interrupted");
                    }
                    attempts++;
                    if (attempts > maxRetry) {
                        throw new SegmentDownloadException("segment retry exceeded", lastError);
                    }
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setInstanceFollowRedirects(true);
                        connection.setConnectTimeout(connectTimeoutMs);
                        connection.setReadTimeout(taskTimeoutMs);
                        connection.setRequestProperty("User-Agent", "DLC-Manager");
                        long rangeStart = start + segmentRead;
                        connection.setRequestProperty("Range", "bytes=" + rangeStart + "-" + end);
                        connection.connect();
                        int code = connection.getResponseCode();
                        if (code != HttpURLConnection.HTTP_PARTIAL) {
                            throw new IOException("range not supported");
                        }
                        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                             java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempFile.toFile(), "rw")) {
                            raf.seek(rangeStart);
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = in.read(buffer)) >= 0) {
                                if (Thread.currentThread().isInterrupted()) {
                                    throw new IOException("interrupted");
                                }
                                raf.write(buffer, 0, len);
                                segmentRead += len;
                                long totalRead = readBytes.addAndGet(len);
                                setEntryProgress(progressKey, attemptId, Math.min(1f, (float) totalRead / (float) totalBytes));
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
            } catch (Exception e) {
                for (Future<?> pending : futures) {
                    pending.cancel(true);
                }
                Throwable cause = e.getCause();
                if (cause instanceof SegmentDownloadException) {
                    throw (SegmentDownloadException) cause;
                }
                throw new SegmentDownloadException("segment failed", e);
            }
        }
        setEntryProgress(progressKey, attemptId, 1f);
    }

    private DownloadInfo openDownload(String url, long offset) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(taskTimeoutMs);
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
        return new DownloadInfo(connection, fileName, responseCode, totalBytes);
    }

    private String finalizeFileName(String fileName, DlcRemoteEntry entry, String url) {
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
                name = name + extension;
            }
        }
        return name;
    }

    private String guessExtension(DlcRemoteEntry entry) {
        if (entry == null || entry.getType() == null) {
            return null;
        }
        return switch (entry.getType()) {
            case MOD -> ".jar";
            case RESOURCE_PACK, DATA_PACK, TACZ_PACK, UNKNOWN -> ".zip";
        };
    }

    private List<DownloadCandidate> buildDownloadCandidates(DlcRemoteEntry entry) throws IOException, InterruptedException {
        List<DownloadCandidate> candidates = new ArrayList<>();
        if (entry == null) {
            return candidates;
        }
        ModrinthClient.ModrinthVersion version = null;
        String versionId = entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            version = ModrinthClient.fetchVersionByIdOrVersion(
                entry.getModrinthProjectId(),
                versionId,
                PlatformServices.get().mcVersion(),
                PlatformServices.get().loaderName()
            );
        }
        CurseForgeClient.CurseForgeFile curseFile = null;
        String curseFileId = entry.getCurseforgeFileId();
        if (curseFileId != null && !curseFileId.isBlank()) {
            curseFile = CurseForgeClient.fetchFileByIdOrVersion(
                entry.getCurseforgeProjectId(),
                curseFileId,
                PlatformServices.get().mcVersion(),
                PlatformServices.get().loaderName()
            );
        }
        List<String> directUrls = new ArrayList<>();
        for (String url : entry.getDirectUrls()) {
            if (url != null && !url.isBlank()) {
                directUrls.add(url);
            }
        }
        if (directUrls.isEmpty() && entry.getDirectUrl() != null && !entry.getDirectUrl().isBlank()) {
            directUrls.add(entry.getDirectUrl());
        }
        if (entry.isDirectUrlPriority()) {
            for (String url : directUrls) {
                candidates.add(new DownloadCandidate(url, true, null));
            }
            if (curseFile != null && curseFile.url != null && !curseFile.url.isBlank()) {
                candidates.add(new DownloadCandidate(curseFile.url, false, curseFile.fileName));
            }
            if (version != null && version.url != null && !version.url.isBlank()) {
                candidates.add(new DownloadCandidate(version.url, false, version.fileName));
            }
        } else if (entry.isCurseForgePriority()) {
            if (curseFile != null && curseFile.url != null && !curseFile.url.isBlank()) {
                candidates.add(new DownloadCandidate(curseFile.url, false, curseFile.fileName));
            }
            if (version != null && version.url != null && !version.url.isBlank()) {
                candidates.add(new DownloadCandidate(version.url, false, version.fileName));
            }
            for (String url : directUrls) {
                candidates.add(new DownloadCandidate(url, true, null));
            }
        } else {
            if (curseFile != null && curseFile.url != null && !curseFile.url.isBlank()) {
                candidates.add(new DownloadCandidate(curseFile.url, false, curseFile.fileName));
            }
            if (version != null && version.url != null && !version.url.isBlank()) {
                candidates.add(new DownloadCandidate(version.url, false, version.fileName));
            }
            for (String url : directUrls) {
                candidates.add(new DownloadCandidate(url, true, null));
            }
        }
        return candidates;
    }

    private static final class DownloadCandidate {
        private final String url;
        private final boolean direct;
        private final String fileNameOverride;

        private DownloadCandidate(String url, boolean direct, String fileNameOverride) {
            this.url = url;
            this.direct = direct;
            this.fileNameOverride = fileNameOverride;
        }
    }

    private void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // Best-effort.
            }
        }
    }

    private void initProgressMap(List<DlcRemoteEntry> pending) {
        progressByKey.clear();
        attemptByKey.clear();
        if (pending == null) {
            return;
        }
        for (DlcRemoteEntry entry : pending) {
            progressByKey.put(progressKey(entry), 0f);
        }
    }

    private void setEntryProgress(String key, int attemptId, float progress) {
        if (key == null) {
            return;
        }
        Integer active = attemptByKey.get(key);
        if (active != null && active != attemptId) {
            return;
        }
        progressByKey.put(key, progress);
        if (maxConcurrentDownloads <= 1) {
            currentProgress = progress;
        }
    }

    private float getTotalProgress() {
        if (totalCount <= 0) {
            return 0f;
        }
        float sum = 0f;
        for (Float value : progressByKey.values()) {
            if (value != null) {
                sum += value;
            }
        }
        return sum / Math.max(1, totalCount);
    }

    private String progressKey(DlcRemoteEntry entry) {
        if (entry == null) {
            return "";
        }
        String key = entry.getUniqueKey();
        String suffix = "#" + System.identityHashCode(entry);
        if (key == null || key.isBlank()) {
            return suffix;
        }
        return key + suffix;
    }

    private List<DlcRemoteEntry> runDownloadBatch(List<DlcRemoteEntry> pending) {
        List<DlcRemoteEntry> failed = new ArrayList<>();
        if (pending == null || pending.isEmpty()) {
            return failed;
        }
        if (maxConcurrentDownloads <= 1) {
            for (DlcRemoteEntry entry : pending) {
                statusText = Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
                try {
                    downloadEntry(entry);
                    completedCount++;
                } catch (Exception e) {
                    failed.add(entry);
                    errorText = Component.translatable("dlc_manager.remote.error", e.getMessage()).getString();
                }
            }
            return failed;
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentDownloads);
        ExecutorCompletionService<DownloadResult> completion = new ExecutorCompletionService<>(executor);
        for (DlcRemoteEntry entry : pending) {
            completion.submit(() -> downloadEntryWithResult(entry));
        }
        for (int i = 0; i < pending.size(); i++) {
            try {
                Future<DownloadResult> future = completion.take();
                DownloadResult result = future.get();
                if (result.success) {
                    completedCount++;
                } else {
                    failed.add(result.entry);
                    if (result.error != null) {
                        errorText = Component.translatable("dlc_manager.remote.error", result.error.getMessage()).getString();
                    }
                }
            } catch (Exception e) {
                errorText = Component.translatable("dlc_manager.remote.error", e.getMessage()).getString();
                failed.add(pending.get(i));
            }
        }
        executor.shutdownNow();
        return failed;
    }

    private DownloadResult downloadEntryWithResult(DlcRemoteEntry entry) {
        try {
            downloadEntry(entry);
            return new DownloadResult(entry, true, null);
        } catch (Exception e) {
            return new DownloadResult(entry, false, e);
        }
    }

    private static final class DownloadProbe {
        private final long totalBytes;
        private final boolean supportsRanges;

        private DownloadProbe(long totalBytes, boolean supportsRanges) {
            this.totalBytes = totalBytes;
            this.supportsRanges = supportsRanges;
        }
    }

    private static final class DownloadResult {
        private final DlcRemoteEntry entry;
        private final boolean success;
        private final Exception error;

        private DownloadResult(DlcRemoteEntry entry, boolean success, Exception error) {
            this.entry = entry;
            this.success = success;
            this.error = error;
        }
    }

    private static final class DownloadInfo {
        private final HttpURLConnection connection;
        private final String fileName;
        private final int responseCode;
        private final long totalBytes;

        private DownloadInfo(HttpURLConnection connection, String fileName, int responseCode, long totalBytes) {
            this.connection = connection;
            this.fileName = fileName;
            this.responseCode = responseCode;
            this.totalBytes = totalBytes;
        }
    }

    private String stripJson(String name) {
        if (name != null && name.endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }
}
