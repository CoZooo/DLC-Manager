package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
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
import java.util.ArrayList;
import java.util.List;

public class DlcRequiredDownloadScreen extends Screen {
    private static final int MAX_RETRY = 5;
    private final Screen parent;
    private final List<DlcRequiredEntry> entries;
    private final List<DlcRequiredEntry> displayList = new ArrayList<>();
    private final List<DlcRequiredEntry> downloadQueue = new ArrayList<>();
    private final List<DlcRequiredEntry> applyQueue = new ArrayList<>();
    private final boolean forceRequired;
    private boolean updatePackageRequired;

    private Button downloadButton;
    private Button skipButton;

    private volatile boolean detecting = true;
    private volatile boolean downloading;
    private volatile String statusText;
    private volatile String errorText;
    private volatile int totalCount;
    private volatile int completedCount;
    private volatile float currentProgress;
    private volatile boolean downloadedAny;

    private int scrollIndex;
    private java.util.List<String> noticeLines = java.util.List.of();

    public DlcRequiredDownloadScreen(Screen parent, List<DlcRequiredEntry> entries) {
        super(Component.translatable("dlc_manager.required.title"));
        this.parent = parent;
        this.entries = entries == null ? List.of() : entries;
        this.forceRequired = DlcRequiredManager.hasForceMarker();
    }

    @Override
    protected void init() {
        if (entries.isEmpty()) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        int downloadX = forceRequired
            ? this.width / 2 - buttonWidth / 2
            : this.width / 2 - buttonWidth - 6;
        downloadButton = Button.builder(Component.translatable("dlc_manager.required.download"), button -> startDownload())
            .bounds(downloadX, y, buttonWidth, buttonHeight)
            .build();
        downloadButton.active = false;
        downloadButton.visible = false;
        skipButton = Button.builder(Component.translatable("dlc_manager.required.skip"), button -> {
            DlcRequiredManager.markPrompted();
            DlcRequiredManager.markSkipped();
            Minecraft.getInstance().setScreen(parent);
        }).bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight).build();
        skipButton.visible = !forceRequired;
        skipButton.active = !forceRequired;
        addRenderableWidget(downloadButton);
        addRenderableWidget(skipButton);
        noticeLines = splitLines(DlcRequiredNotice.load().resolveDownloadDesc());
        detecting = true;
        statusText = Component.translatable("dlc_manager.required.preparing").getString();
        new Thread(this::detectEntries, "dlc-required-detect").start();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        for (String line : getDescLines()) {
            guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xC8C8C8);
            y += this.font.lineHeight + 2;
        }
        if (forceRequired) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.required.force_notice"), centerX, y, 0xFFAA55);
            y += this.font.lineHeight + 2;
        }
        y += 6;
        if (detecting && !downloading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.required.detecting"), centerX, y, 0xE6E6E6);
            y += 12;
        }

        int listTop = y;
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = displayList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcRequiredEntry entry = displayList.get(i);
            String name = entry.resolveDisplayName();
            guiGraphics.drawString(this.font, "- " + name, 24, y, 0xE6E6E6, false);
            y += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);

        if (downloading) {
            y = this.height - 70;
            String status = statusText == null ? "" : statusText;
            guiGraphics.drawCenteredString(this.font, status, centerX, y, 0xE6E6E6);
            y += 12;
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, currentProgress);
            int percent = Math.round(Math.max(0f, Math.min(1f, currentProgress)) * 100f);
            guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
        } else if (errorText != null && !errorText.isBlank()) {
            y = this.height - 70;
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xFF6B6B);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = getListTop();
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = displayList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = delta > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !forceRequired;
    }

    private void detectEntries() {
        List<DlcRequiredEntry> missingOrApply = new ArrayList<>();
        List<DlcRequiredEntry> download = new ArrayList<>();
        List<DlcRequiredEntry> applyCandidates = new ArrayList<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            applyCandidates.add(entry);
            String fileName = entry.getRequiredFileName();
            if (isConfigFileName(fileName)) {
                fileName = null;
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveFileName(entry);
            }
            if (isConfigFileName(fileName)) {
                fileName = null;
            }
            entry.setResolvedFileName(fileName);
            boolean hasFile = fileName != null && entry.resolveRequiredPath(fileName) != null
                && Files.exists(entry.resolveRequiredPath(fileName));
            if (!hasFile && fileName == null) {
                // fall back to base name match
                String base = entry.getBaseName();
                if (base != null && !base.isBlank() && entry.getRequiredDir() != null) {
                    try (var stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
                        for (Path file : stream) {
                            if (isConfigFile(entry, file)) {
                                continue;
                            }
                            fileName = file.getFileName().toString();
                            entry.setResolvedFileName(fileName);
                            hasFile = true;
                            break;
                        }
                    } catch (IOException ignored) {
                        // Best-effort.
                    }
                }
            }
            if (!hasFile) {
                download.add(entry);
                missingOrApply.add(entry);
            } else {
                if (DlcRequiredManager.entryNeedsApply(entry)) {
                    missingOrApply.add(entry);
                }
                if (fileName != null && !fileName.isBlank()) {
                    DlcRequiredManager.ensureFileNameKey(entry, fileName);
                }
            }
        }
        displayList.clear();
        displayList.addAll(missingOrApply);
        downloadQueue.clear();
        downloadQueue.addAll(download);
        applyQueue.clear();
        applyQueue.addAll(applyCandidates);
        updatePackageRequired = hasUpdatePackageTargets(applyQueue);
        detecting = false;
        statusText = Component.translatable("dlc_manager.required.ready").getString();
        scrollIndex = 0;
        Minecraft.getInstance().execute(() -> {
            if (downloadQueue.isEmpty() && applyQueue.isEmpty()) {
                Minecraft.getInstance().setScreen(parent);
                return;
            }
            if (downloadButton != null) {
                downloadButton.visible = true;
                downloadButton.active = true;
            }
        });
    }

    private int getListTop() {
        int y = 20 + 18;
        y += getDescLines().size() * (this.font.lineHeight + 2);
        y += 6;
        if (detecting && !downloading) {
            y += 12;
        }
        return y;
    }

    private List<String> getDescLines() {
        List<String> lines = new ArrayList<>();
        lines.addAll(splitLines(Component.translatable("dlc_manager.required.desc").getString()));
        if (!noticeLines.isEmpty()) {
            lines.addAll(noticeLines);
        }
        return lines;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        if (text.contains("\n")) {
            String[] parts = text.split("\\n");
            List<String> lines = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    lines.add(part);
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        return new ArrayList<>(List.of(text));
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        errorText = null;
        downloadedAny = false;
        downloadButton.active = false;
        if (!forceRequired) {
            skipButton.active = false;
        }
        if (downloadQueue.isEmpty()) {
            new Thread(() -> {
                try {
                    applyAll();
                } finally {
                    downloading = false;
                }
            }, "dlc-required-apply").start();
            return;
        }
        new Thread(this::downloadAll, "dlc-required-download").start();
    }

    private void downloadAll() {
        totalCount = downloadQueue.size();
        completedCount = 0;
        for (DlcRequiredEntry entry : downloadQueue) {
            statusText = Component.translatable("dlc_manager.required.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
            try {
                downloadEntry(entry);
            } catch (Exception e) {
                errorText = Component.translatable("dlc_manager.required.error", e.getMessage()).getString();
                downloading = false;
                Minecraft.getInstance().execute(() -> {
                    downloadButton.active = true;
                    if (!forceRequired) {
                        skipButton.active = true;
                    }
                });
                return;
            }
            completedCount++;
        }
        try {
            applyAll();
        } catch (Exception e) {
            errorText = Component.translatable("dlc_manager.required.error", e.getMessage()).getString();
            downloading = false;
            Minecraft.getInstance().execute(() -> {
                downloadButton.active = true;
                if (!forceRequired) {
                    skipButton.active = true;
                }
            });
            return;
        }
        downloading = false;
    }

    private void downloadEntry(DlcRequiredEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return;
        }
        currentProgress = 0f;
        List<DownloadCandidate> candidates = buildDownloadCandidates(entry);
        IOException lastError = null;
        for (DownloadCandidate candidate : candidates) {
            for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
                try {
                    String fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName());
                    if (fileName == null && candidate.fileNameOverride != null) {
                        fileName = DlcDownloadUtil.sanitizeFileName(candidate.fileNameOverride);
                    }
                    if (fileName == null) {
                        fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(candidate.url));
                    }
                    DownloadInfo downloadInfo = openDownload(candidate.url, 0);
                    if (fileName == null) {
                        fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                    }
                    if (fileName == null) {
                        fileName = DlcDownloadUtil.stripQuery(entry.getBaseName());
                    }
                    Path target = entry.resolveRequiredPath(fileName);
                    if (target == null) {
                        closeQuietly(downloadInfo.connection);
                        throw new IOException("invalid target path");
                    }
                    Files.createDirectories(target.getParent());
                    if (Files.exists(target)) {
                        closeQuietly(downloadInfo.connection);
                        entry.setResolvedFileName(fileName);
                        return;
                    }
                    Path tempFile = target.getParent().resolve(fileName + ".part");
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
                    downloadToFile(downloadInfo.connection, tempFile, existing, downloadInfo.totalBytes);
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                    downloadedAny = true;
                    entry.setResolvedFileName(fileName);
                    DlcRequiredManager.updateFileNameKey(entry, fileName);
                    return;
                } catch (IOException ex) {
                    lastError = ex;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("missing download url");
    }

    private void applyAll() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean didApply = downloadedAny;
        boolean updateApplied = false;
        java.util.Map<String, Boolean> baselines = new java.util.HashMap<>();
        for (DlcRequiredEntry entry : applyQueue) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            baselines.put(id, isApplied(entry));
            DlcRequiredRuntimeState.getBaseline(id, baselines.get(id));
        }
        for (DlcRequiredEntry entry : applyQueue) {
            String fileName = entry.getResolvedFileName();
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            Path source = entry.resolveRequiredPath(fileName);
            if (source == null || !Files.exists(source)) {
                continue;
            }
            if (entry.resolveAppliedTargets().isEmpty()) {
                didApply = true;
            }
            for (Path targetDir : entry.resolveAppliedTargets()) {
                Path target = resolveTargetPath(targetDir, fileName);
                if (target == null) {
                    continue;
                }
                try {
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        didApply = true;
                    }
                } catch (IOException ignored) {
                    // Best-effort.
                }
            }
        }
        for (DlcRequiredEntry entry : applyQueue) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            DlcRequiredRuntimeState.setOverride(id, true);
        }
        if (updatePackageRequired) {
            UpdateCheckResult updateCheck = DlcUpdateService.checkForUpdates(true);
            if (updateCheck != null && updateCheck.getUpdates() != null && !updateCheck.getUpdates().isEmpty()) {
                UpdateApplyResult updateResult = DlcUpdateService.applyUpdates(updateCheck.getUpdates(), false);
                if (updateResult.isMissing()) {
                    Minecraft.getInstance().execute(() -> minecraft.setScreen(new DlcUpdateMissingScreen(parent, updateCheck, updateResult.getMissingPaths())));
                    return;
                }
                if (!updateResult.isSuccess()) {
                    Minecraft.getInstance().execute(() -> minecraft.setScreen(new DlcUpdateResultScreen(parent, false, Component.translatable("dlc_manager.update.failed"))));
                    return;
                }
                updateApplied = true;
                if (maybeShowPostUpdateDownloads(updateResult)) {
                    return;
                }
            }
        }
        if (!didApply && !updateApplied) {
            Minecraft.getInstance().execute(() -> minecraft.setScreen(parent));
            return;
        }
        Minecraft.getInstance().execute(() -> minecraft.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                minecraft.stop();
            } else {
                minecraft.setScreen(parent);
            }
        }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.required.restart_prompt"),
            Component.translatable("dlc_manager.restart_now"),
            Component.translatable("dlc_manager.restart_later"))));
    }

    private boolean maybeShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloads();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, missing)));
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean hasUpdatePackageTargets(List<DlcRequiredEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        Path dlcRoot = DlcPaths.dlcRoot();
        if (dlcRoot == null) {
            return false;
        }
        Path updateRoot = dlcRoot.resolve("update").normalize();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            for (Path target : entry.resolveAppliedTargets()) {
                if (target == null) {
                    continue;
                }
                Path normalized = target.normalize();
                if (normalized.equals(updateRoot) || normalized.startsWith(updateRoot)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Path resolveTargetPath(Path targetDir, String fileName) {
        if (targetDir == null) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
    }

    private boolean isApplied(DlcRequiredEntry entry) {
        if (entry == null) {
            return false;
        }
        java.util.List<Path> targets = entry.resolveAppliedTargets();
        if (targets.isEmpty()) {
            return true;
        }
        String fileName = resolveAppliedFileName(entry, targets);
        for (Path target : targets) {
            Path targetFile;
            String name = target.getFileName() == null ? "" : target.getFileName().toString();
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
                targetFile = target;
            } else {
                targetFile = resolveTargetPath(target, fileName);
            }
            if (targetFile == null || !Files.exists(targetFile)) {
                return false;
            }
        }
        return true;
    }

    private boolean isConfigFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }

    private boolean isConfigFile(DlcRequiredEntry entry, Path path) {
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

    private String resolveAppliedFileName(DlcRequiredEntry entry, java.util.List<Path> targets) {
        if (entry == null) {
            return null;
        }
        String fileName = entry.getResolvedFileName();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        if (targets != null) {
            for (Path target : targets) {
                if (target == null) {
                    continue;
                }
                String name = target.getFileName() == null ? "" : target.getFileName().toString();
                if (name.endsWith(".jar") || name.endsWith(".zip")) {
                    return name;
                }
            }
        }
        String base = entry.getBaseName();
        if (base == null || base.isBlank() || targets == null) {
            return null;
        }
        for (Path target : targets) {
            if (target == null) {
                continue;
            }
            if (Files.isDirectory(target)) {
                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(target, base + ".*")) {
                    for (Path path : stream) {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        if (!name.endsWith(".json")) {
                            return name;
                        }
                    }
                } catch (IOException ignored) {
                    // Best-effort.
                }
            }
        }
        return null;
    }

    private String resolveExistingFileName(DlcRequiredEntry entry) {
        String fileName = entry.getResolvedFileName();
        if (isConfigFileName(fileName)) {
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
                return path.getFileName().toString();
            }
        } catch (IOException ignored) {
            // Best-effort.
        }
        return fileName;
    }

    private String resolveDownloadUrl(DlcRequiredEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return null;
        }
        String versionId = entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
            if (version != null) {
                if (entry.getResolvedFileName() == null || entry.getResolvedFileName().isBlank()) {
                    entry.setResolvedFileName(version.fileName);
                }
                return version.url;
            }
        }
        String directUrl = entry.getDirectUrl();
        if (directUrl != null && !directUrl.isBlank()) {
            return directUrl;
        }
        return null;
    }

    private String resolveFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String versionId = entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            try {
                ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
                if (version != null && version.fileName != null && !version.fileName.isBlank()) {
                    return version.fileName;
                }
            } catch (Exception ignored) {
                // Best-effort.
            }
        }
        String directUrl = entry.getDirectUrl();
        if (directUrl != null && !directUrl.isBlank()) {
            String name = DlcDownloadUtil.extractFileNameFromUrl(directUrl);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return null;
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

    private void downloadToFile(HttpURLConnection connection, Path target, long offset, long totalBytes) throws IOException {
        if (connection == null) {
            throw new IOException("missing download url");
        }
        long total = totalBytes > 0 ? totalBytes : connection.getContentLengthLong();
        if (total > 0 && offset > 0) {
            currentProgress = Math.min(1f, (float) offset / (float) total);
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
                    currentProgress = Math.min(1f, (float) read / (float) total);
                }
            }
        } finally {
            connection.disconnect();
        }
        if (total > 0 && read < total) {
            throw new IOException("download incomplete");
        }
        currentProgress = total > 0 ? Math.min(1f, (float) read / (float) total) : 1f;
    }

    private List<DownloadCandidate> buildDownloadCandidates(DlcRequiredEntry entry) throws IOException, InterruptedException {
        List<DownloadCandidate> candidates = new ArrayList<>();
        if (entry == null) {
            return candidates;
        }
        ModrinthClient.ModrinthVersion version = null;
        String versionId = entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            version = ModrinthClient.fetchVersion(versionId);
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
                candidates.add(new DownloadCandidate(url, null));
            }
            if (version != null && version.url != null && !version.url.isBlank()) {
                candidates.add(new DownloadCandidate(version.url, version.fileName));
            }
        } else {
            if (version != null && version.url != null && !version.url.isBlank()) {
                candidates.add(new DownloadCandidate(version.url, version.fileName));
            }
            for (String url : directUrls) {
                candidates.add(new DownloadCandidate(url, null));
            }
        }
        return candidates;
    }

    private static final class DownloadCandidate {
        private final String url;
        private final String fileNameOverride;

        private DownloadCandidate(String url, String fileNameOverride) {
            this.url = url;
            this.fileNameOverride = fileNameOverride;
        }
    }

    private DownloadInfo openDownload(String url, long offset) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(180000);
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

    private void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // Best-effort.
            }
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
}
