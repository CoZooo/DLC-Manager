package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice;
import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DlcRequiredDownloadScreen extends DlcScreenCompat.MouseScroll {
    private static final long SWITCH_DELAY_MS = 20_000L;
    private static final int SWITCH_BUTTON_SIZE = 8;
    private static final int SWITCH_BUTTON_PAD = 4;

    private final Screen parent;
    private final UpdateApplyResult postUpdateResult;
    private final List<DlcRequiredEntry> entries;
    private final List<DlcRequiredEntry> displayList = new ArrayList<>();
    private final List<DlcRequiredEntry> downloadQueue = new ArrayList<>();
    private final List<DlcRequiredEntry> applyQueue = new ArrayList<>();
    private final boolean forceRequired;
    private final boolean forceRestartRequired;
    private boolean updatePackageRequired;
    private final int maxConcurrentDownloads;
    private final int maxRetry;
    private final int connectTimeoutMs;
    private final int taskTimeoutMs;
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private final Map<String, Integer> attemptByKey = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger attemptCounter = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<String, Long> downloadStartByKey = new ConcurrentHashMap<>();
    private final Set<String> switchRequested = ConcurrentHashMap.newKeySet();
    private volatile DlcRequiredEntry activeDownloadEntry;

    private Button downloadButton;
    private Button skipButton;

    private volatile boolean detecting = true;
    private volatile boolean downloading;
    private volatile boolean cancelRequested;
    private volatile String statusText;
    private volatile String errorText;
    private volatile int totalCount;
    private volatile int completedCount;
    private volatile float currentProgress;
    private volatile boolean downloadedAny;

    private int scrollIndex;
    private java.util.List<String> noticeLines = java.util.List.of();

    public DlcRequiredDownloadScreen(Screen parent, List<DlcRequiredEntry> entries) {
        this(parent, entries, null);
    }

    public DlcRequiredDownloadScreen(Screen parent, List<DlcRequiredEntry> entries, UpdateApplyResult postUpdateResult) {
        super(Component.translatable("dlc_manager.required.title"));
        this.parent = parent;
        this.postUpdateResult = postUpdateResult;
        this.entries = entries == null ? List.of() : entries;
        this.forceRequired = DlcRequiredManager.hasForceMarker();
        this.forceRestartRequired = DlcRequiredManager.hasForceRestartMarker();
        this.maxConcurrentDownloads = Math.max(1, DlcPathConfig.getMaxConcurrentDownloads());
        this.maxRetry = Math.max(1, DlcPathConfig.getMaxRetryCount());
        this.connectTimeoutMs = Math.max(1, DlcPathConfig.getConnectTimeoutMs());
        this.taskTimeoutMs = Math.max(1, DlcPathConfig.getTaskTimeoutMs());
    }

    @Override
    protected void init() {
        if (entries.isEmpty()) {
            if (postUpdateResult != null) {
                if (!tryShowPostUpdateDownloads(postUpdateResult)) {
                    Minecraft.getInstance().setScreen(createPostUpdateResultScreen(postUpdateResult));
                }
            } else {
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    Minecraft.getInstance().setScreen(parent);
                }
            }
            return;
        }
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        int downloadX = forceRequired
            ? this.width / 2 - buttonWidth / 2
            : this.width / 2 - buttonWidth - 6;
        downloadButton = Button.builder(Component.translatable("dlc_manager.required.download"), button -> handleDownloadButton())
            .bounds(downloadX, y, buttonWidth, buttonHeight)
            .build();
        downloadButton.active = false;
        downloadButton.visible = false;
        skipButton = Button.builder(Component.translatable("dlc_manager.required.skip"), button -> {
            DlcRequiredManager.markPrompted();
            DlcRequiredManager.markSkipped();
            if (postUpdateResult != null) {
                if (!tryShowPostUpdateDownloads(postUpdateResult)) {
                    Minecraft.getInstance().setScreen(createPostUpdateResultScreen(postUpdateResult));
                }
            } else {
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    Minecraft.getInstance().setScreen(parent);
                }
            }
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
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
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
            guiGraphics.drawString(this.font, DlcTextFormatter.parse("- " + name), 24, y, 0xE6E6E6, false);
            if (!downloading || maxConcurrentDownloads > 1) {
                String key = progressKey(entry);
                Float progress = downloading ? progressByKey.get(key) : null;
                if (progress == null) {
                    progress = getCompletedProgress(entry);
                }
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
                    if (shouldShowSwitch(key, entry, progress)) {
                        int switchRight = barLeft - SWITCH_BUTTON_PAD;
                        int switchLeft = switchRight - SWITCH_BUTTON_SIZE;
                        int switchTop = barY - 1;
                        drawSwitchButton(guiGraphics, switchLeft, switchTop);
                    }
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
                guiGraphics.drawCenteredString(this.font, DlcTextFormatter.parse(status), centerX, y, 0xE6E6E6);
                y += 12;
                DlcRequiredEntry activeEntry = activeDownloadEntry;
                String activeKey = activeEntry == null ? null : progressKey(activeEntry);
                if (activeKey != null && shouldShowSwitch(activeKey, activeEntry, currentProgress)) {
                    int barLeft = centerX - 120;
                    int switchLeft = barLeft - SWITCH_BUTTON_PAD - SWITCH_BUTTON_SIZE;
                    int switchTop = y - 1;
                    drawSwitchButton(guiGraphics, switchLeft, switchTop);
                }
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, currentProgress);
                int percent = Math.round(Math.max(0f, Math.min(1f, currentProgress)) * 100f);
                guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
            } else {
                String status = Component.translatable("dlc_manager.required.downloading_multi").getString();
                guiGraphics.drawCenteredString(this.font, status, centerX, y, 0xE6E6E6);
                y += 12;
                float totalProgress = getTotalProgress();
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, totalProgress);
                int percent = Math.round(Math.max(0f, Math.min(1f, totalProgress)) * 100f);
                guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
            }
        } else if (errorText != null && !errorText.isBlank()) {
            y = this.height - 70;
            guiGraphics.drawCenteredString(this.font, DlcTextFormatter.parse(errorText), centerX, y, 0xFF6B6B);
        }

        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(deltaY);
    }

    private boolean handleMouseScrolled(double deltaY) {
        int listTop = getListTop();
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = displayList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (downloading && handleSwitchClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
            if (isTempFileName(fileName)) {
                fileName = null;
            }
            entry.setResolvedFileName(fileName);
            boolean hasFile = fileName != null && entry.resolveRequiredPath(fileName) != null
                && Files.exists(entry.resolveRequiredPath(fileName)) && !isTempFileName(fileName);
            if (!hasFile && fileName == null) {
                String base = entry.getBaseName();
                if (base != null && !base.isBlank() && entry.getRequiredDir() != null) {
                    try (var stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
                        for (Path file : stream) {
                            if (isConfigFile(entry, file)) {
                                continue;
                            }
                            fileName = file.getFileName().toString();
                            if (isTempFileName(fileName)) {
                                continue;
                            }
                            entry.setResolvedFileName(fileName);
                            hasFile = true;
                            break;
                        }
                    } catch (IOException ignored) {
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
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    Minecraft.getInstance().setScreen(parent);
                }
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

    private void handleDownloadButton() {
        if (downloading) {
            requestCancel();
            return;
        }
        startDownload();
    }

    private void requestCancel() {
        if (!downloading || cancelRequested) {
            return;
        }
        cancelRequested = true;
        statusText = Component.translatable("dlc_manager.required.canceling").getString();
        downloadButton.active = false;
        if (!forceRequired) {
            skipButton.active = false;
        }
    }

    private void updateDownloadButtonLabel(boolean isDownloading) {
        if (downloadButton == null) {
            return;
        }
        downloadButton.setMessage(Component.translatable(isDownloading
            ? "dlc_manager.required.cancel"
            : "dlc_manager.required.download"));
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        cancelRequested = false;
        errorText = null;
        downloadedAny = false;
        updateDownloadButtonLabel(true);
        downloadButton.active = true;
        if (!forceRequired) {
            skipButton.active = false;
        }
        initProgressMap(downloadQueue);
        if (downloadQueue.isEmpty()) {
            new Thread(() -> {
                try {
                    Minecraft.getInstance().execute(() -> {
                        updateDownloadButtonLabel(false);
                        downloadButton.active = false;
                    });
                    applyAll();
                } catch (Exception e) {
                    errorText = Component.translatable("dlc_manager.required.error", e.getMessage()).getString();
                    Minecraft.getInstance().execute(() -> {
                        updateDownloadButtonLabel(false);
                        downloadButton.active = true;
                        if (!forceRequired) {
                            skipButton.active = true;
                        }
                    });
                } finally {
                    downloading = false;
                }
            }, "dlc-required-apply").start();
            return;
        }
        new Thread(this::downloadAll, "dlc-required-download").start();
    }

    private void downloadAll() {
        List<DlcRequiredEntry> pending = new ArrayList<>(downloadQueue);
        totalCount = pending.size();
        completedCount = 0;
        List<DlcRequiredEntry> failed = runDownloadBatch(pending);
        if (cancelRequested) {
            downloading = false;
            statusText = Component.translatable("dlc_manager.required.canceled").getString();
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                if (!forceRequired) {
                    skipButton.active = true;
                }
            });
            return;
        }
        if (!failed.isEmpty()) {
            failed = runDownloadBatch(failed);
        }
        if (cancelRequested) {
            downloading = false;
            statusText = Component.translatable("dlc_manager.required.canceled").getString();
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                if (!forceRequired) {
                    skipButton.active = true;
                }
            });
            return;
        }
        if (!failed.isEmpty()) {
            downloading = false;
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                if (!forceRequired) {
                    skipButton.active = true;
                }
            });
            return;
        }
        for (DlcRequiredEntry entry : downloadQueue) {
            String key = progressKey(entry);
            Integer active = attemptByKey.get(key);
            int attemptId = active == null ? attemptCounter.incrementAndGet() : active;
            attemptByKey.put(key, attemptId);
            setEntryProgress(key, attemptId, 1f);
        }
        errorText = null;
        try {
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = false;
            });
            applyAll();
        } catch (Exception e) {
            errorText = Component.translatable("dlc_manager.required.error", e.getMessage()).getString();
            downloading = false;
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                if (!forceRequired) {
                    skipButton.active = true;
                }
            });
            return;
        }
        downloading = false;
    }

    private List<DlcRequiredEntry> runDownloadBatch(List<DlcRequiredEntry> pending) {
        List<DlcRequiredEntry> failed = new ArrayList<>();
        if (pending.isEmpty()) {
            return failed;
        }
        if (cancelRequested) {
            return failed;
        }
        if (maxConcurrentDownloads <= 1) {
            for (DlcRequiredEntry entry : pending) {
                if (cancelRequested) {
                    break;
                }
                statusText = Component.translatable("dlc_manager.required.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
                try {
                    downloadEntry(entry);
                    completedCount++;
                    if (cancelRequested) {
                        break;
                    }
                } catch (Exception e) {
                    if (e instanceof DownloadCanceledException) {
                        cancelRequested = true;
                        break;
                    }
                    failed.add(entry);
                    errorText = Component.translatable("dlc_manager.required.error", e.getMessage()).getString();
                }
            }
            return failed;
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentDownloads);
        ExecutorCompletionService<DownloadResult> completion = new ExecutorCompletionService<>(executor);
        for (DlcRequiredEntry entry : pending) {
            completion.submit(() -> downloadEntryWithResult(entry));
        }
        for (int i = 0; i < pending.size(); i++) {
            try {
                Future<DownloadResult> future = completion.take();
                DownloadResult result = future.get();
                if (result.canceled) {
                    cancelRequested = true;
                } else if (result.success) {
                    completedCount++;
                } else {
                    failed.add(result.entry);
                    if (result.error != null) {
                        errorText = Component.translatable("dlc_manager.required.error", result.error.getMessage()).getString();
                    }
                }
            } catch (Exception e) {
                if (e instanceof DownloadCanceledException) {
                    cancelRequested = true;
                } else {
                    errorText = Component.translatable("dlc_manager.required.error", e.getMessage()).getString();
                    failed.add(pending.get(i));
                }
            }
            if (cancelRequested) {
                break;
            }
        }
        executor.shutdownNow();
        return failed;
    }

    private DownloadResult downloadEntryWithResult(DlcRequiredEntry entry) {
        try {
            downloadEntry(entry);
            return new DownloadResult(entry, true, false, null);
        } catch (DownloadCanceledException e) {
            return new DownloadResult(entry, false, true, null);
        } catch (Exception e) {
            return new DownloadResult(entry, false, false, e);
        }
    }

    private void downloadEntry(DlcRequiredEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return;
        }
        checkCanceled();
        currentProgress = 0f;
        String progressKey = progressKey(entry);
        if (maxConcurrentDownloads <= 1) {
            activeDownloadEntry = entry;
        }
        try {
            DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver(entry);
            IOException lastError = null;
            int attemptId = 0;
            boolean canceled = false;
            List<DlcDownloadCandidate> candidates;
            while (!(candidates = resolver.next()).isEmpty()) {
                for (DlcDownloadCandidate candidate : candidates) {
                for (int attempt = 1; attempt <= maxRetry; attempt++) {
                    try {
                        checkCanceled();
                        attemptId = attemptCounter.incrementAndGet();
                        attemptByKey.put(progressKey, attemptId);
                        markDownloadStart(progressKey);
                        String fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName());
                        if (fileName == null && candidate.fileNameOverride != null) {
                            fileName = DlcDownloadUtil.sanitizeFileName(candidate.fileNameOverride);
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(candidate.url));
                        }
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
                        Path target = entry.resolveRequiredPath(fileName);
                        if (target == null) {
                            throw new IOException("invalid target path");
                        }
                        Files.createDirectories(target.getParent());
                        if (Files.exists(target)) {
                            entry.setResolvedFileName(fileName);
                            setEntryProgress(progressKey, attemptId, 1f);
                            return;
                        }
                        final int currentAttemptId = attemptId;
                        Path downloaded = shouldProbe
                            ? DlcDownloadService.download(candidate.url, target.getParent(), fileName,
                                (done, total) -> {
                                    if (total > 0) {
                                        setEntryProgress(progressKey, currentAttemptId, Math.min(1f, done / (float) total));
                                    }
                                },
                                () -> cancelRequested || isSwitchRequested(progressKey))
                            : DlcDownloadService.downloadKnown(candidate.url, target.getParent(), fileName, candidate.expectedSize,
                                (done, total) -> {
                                    if (total > 0) {
                                        setEntryProgress(progressKey, currentAttemptId, Math.min(1f, done / (float) total));
                                    }
                                },
                                () -> cancelRequested || isSwitchRequested(progressKey));
                        Path resolved = downloaded.equals(target)
                            ? downloaded
                            : Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
                        verifyDownloadedFile(resolved, candidate, downloadInfo);
                        downloadedAny = true;
                        setEntryProgress(progressKey, currentAttemptId, 1f);
                        entry.setResolvedFileName(fileName);
                        DlcRequiredManager.updateFileNameKey(entry, fileName);
                        return;
                    } catch (DlcDownloadService.DownloadCanceledException ex) {
                        if (isSwitchRequested(progressKey)) {
                            lastError = new DownloadSwitchException("switch");
                            clearSwitchRequest(progressKey);
                            if (!canceled) {
                                setEntryProgress(progressKey, attemptId, 0f);
                            }
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
                            clearSwitchRequest(progressKey);
                            if (!canceled) {
                                setEntryProgress(progressKey, attemptId, 0f);
                            }
                            break;
                        }
                        if (ex.getCause() instanceof DownloadCanceledException) {
                            canceled = true;
                            throw (DownloadCanceledException) ex.getCause();
                        }
                        lastError = ex;
                        if (ex instanceof DlcDownloadService.SegmentDownloadException) {
                            if (!canceled) {
                                setEntryProgress(progressKey, attemptId, 0f);
                            }
                            break;
                        }
                        if (attempt >= maxRetry) {
                            if (!canceled) {
                                setEntryProgress(progressKey, attemptId, 0f);
                            }
                        }
                    }
                }
            }
            }
            if (cancelRequested || canceled) {
                throw new DownloadCanceledException("canceled");
            }
            if (attemptId > 0) {
                setEntryProgress(progressKey, attemptId, 0f);
            }
            if (lastError == null) {
                lastError = resolver.lastError();
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new IOException("missing download url");
        } finally {
            downloadStartByKey.remove(progressKey);
            switchRequested.remove(progressKey);
            if (activeDownloadEntry == entry) {
                activeDownloadEntry = null;
            }
        }
    }

    private void applyAll() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        boolean didApply = downloadedAny;
        boolean updateApplied = false;
        if (!ensureAllRequiredReady()) {
            throw new IOException("download incomplete");
        }
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
            DlcRequiredManager.updateUserRemoved(entry, false);
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
                if (tryShowPostUpdateDownloads(updateResult)) {
                    return;
                }
            }
        }
        if (postUpdateResult != null) {
            if (tryShowPostUpdateDownloads(postUpdateResult)) {
                return;
            }
            Minecraft.getInstance().execute(() -> minecraft.setScreen(createPostUpdateResultScreen(postUpdateResult)));
            return;
        }
        if (!didApply && !updateApplied) {
            Minecraft.getInstance().execute(() -> {
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    minecraft.setScreen(parent);
                }
            });
            return;
        }
        if (forceRestartRequired) {
            Minecraft.getInstance().execute(() -> minecraft.setScreen(new Screen(Component.translatable("dlc_manager.title")) {
                @Override
                protected void init() {
                    int buttonWidth = 140;
                    int buttonHeight = 20;
                    int y = this.height - 40;
                    addRenderableWidget(Button.builder(Component.translatable("dlc_manager.restart_now"), button -> Minecraft.getInstance().stop())
                        .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                        .build());
                }

                @Override
                public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                    DlcScreenCompat.renderBackground(this, guiGraphics, mouseX, mouseY, partialTick);
                    int centerX = this.width / 2;
                    int y = 40;
                    int titleX = (this.width - this.font.width(getTitle())) / 2;
                    guiGraphics.drawString(this.font, getTitle(), titleX, y, 0xFFFFFF, false);
                    y += 20;
                    guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.required.restart_prompt"), centerX, y, 0xC8C8C8);
                    DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
                }

                @Override
                public boolean shouldCloseOnEsc() {
                    return false;
                }
            }));
            return;
        }
        Minecraft.getInstance().execute(() -> minecraft.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                minecraft.stop();
            } else {
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    minecraft.setScreen(parent);
                }
            }
        }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.required.restart_prompt"),
            Component.translatable("dlc_manager.restart_now"),
            Component.translatable("dlc_manager.restart_later"))));
    }

    private boolean ensureAllRequiredReady() {
        for (DlcRequiredEntry entry : applyQueue) {
            if (entry == null) {
                continue;
            }
            String fileName = resolveExistingFileName(entry);
            if (fileName == null || fileName.isBlank()) {
                return false;
            }
            entry.setResolvedFileName(fileName);
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required)) {
                return false;
            }
        }
        return true;
    }

    private boolean tryShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, missing, result)));
                return true;
            }
        }
        if (result.hasRemoteAdd()) {
            List<DlcRemoteEntry> missing = DlcRemoteManager.collectMissing();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(createPostUpdateResultScreen(result), missing)));
                return true;
            }
        }
        return false;
    }

    private Screen createPostUpdateResultScreen(UpdateApplyResult result) {
        Component message = result != null && result.isHelperScheduled()
            ? Component.translatable("dlc_manager.update.deferred_restart")
            : Component.translatable("dlc_manager.update.success");
        return new DlcUpdateResultScreen(parent, true, message);
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
        return DcFormatUtil.isDcName(fileName);
    }

    private boolean isTempFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".part");
    }

    private Float getCompletedProgress(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = resolveExistingFileName(entry);
        if (fileName == null || isTempFileName(fileName)) {
            return null;
        }
        Path required = entry.resolveRequiredPath(fileName);
        if (required != null && Files.exists(required)) {
            return 1f;
        }
        return null;
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
                        if (!DcFormatUtil.isDcName(name)) {
                            return name;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private String resolveExistingFileName(DlcRequiredEntry entry) {
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

    private String resolveFileName(DlcRequiredEntry entry) {
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

    private void checkCanceled() throws DownloadCanceledException {
        if (cancelRequested) {
            throw new DownloadCanceledException("canceled");
        }
    }

    private void markDownloadStart(String key) {
        if (key == null) {
            return;
        }
        downloadStartByKey.put(key, System.currentTimeMillis());
    }

    private boolean isSwitchRequested(String key) {
        return key != null && switchRequested.contains(key);
    }

    private void clearSwitchRequest(String key) {
        if (key != null) {
            switchRequested.remove(key);
        }
    }

    private boolean isSwitchException(IOException ex) {
        if (ex instanceof DownloadSwitchException) {
            return true;
        }
        return ex.getCause() instanceof DownloadSwitchException;
    }

    private boolean hasMultipleSources(DlcRequiredEntry entry) {
        if (entry == null) {
            return false;
        }
        int count = 0;
        if ((entry.getCurseforgeProjectId() != null && !entry.getCurseforgeProjectId().isBlank())
            || (entry.getCurseforgeFileId() != null && !entry.getCurseforgeFileId().isBlank())) {
            count += 1;
        }
        if ((entry.getModrinthProjectId() != null && !entry.getModrinthProjectId().isBlank())
            || (entry.getModrinthVersionId() != null && !entry.getModrinthVersionId().isBlank())) {
            count += 1;
        }
        int directCount = entry.getDirectUrls().size();
        if (directCount > 0) {
            count += directCount;
        }
        return count > 1;
    }

    private boolean shouldShowSwitch(String key, DlcRequiredEntry entry, float progress) {
        if (!downloading || key == null || entry == null) {
            return false;
        }
        if (progress >= 1f) {
            return false;
        }
        Long start = downloadStartByKey.get(key);
        if (start == null) {
            return false;
        }
        if (System.currentTimeMillis() - start < SWITCH_DELAY_MS) {
            return false;
        }
        return hasMultipleSources(entry);
    }

    private void drawSwitchButton(GuiGraphics guiGraphics, int x, int y) {
        int right = x + SWITCH_BUTTON_SIZE;
        int bottom = y + SWITCH_BUTTON_SIZE;
        guiGraphics.fill(x, y, right, bottom, 0xFF4B4B4B);
        guiGraphics.drawString(this.font, ">", x + 2, y + 1, 0xE6E6E6, false);
    }

    private boolean handleSwitchClick(double mouseX, double mouseY) {
        if (!downloading) {
            return false;
        }
        if (maxConcurrentDownloads <= 1) {
            DlcRequiredEntry activeEntry = activeDownloadEntry;
            String key = activeEntry == null ? null : progressKey(activeEntry);
            if (key != null && shouldShowSwitch(key, activeEntry, currentProgress)) {
                int centerX = this.width / 2;
                int barLeft = centerX - 120;
                int switchLeft = barLeft - SWITCH_BUTTON_PAD - SWITCH_BUTTON_SIZE;
                int switchTop = (this.height - 70) + 12 - 1;
                if (mouseX >= switchLeft && mouseX <= switchLeft + SWITCH_BUTTON_SIZE
                    && mouseY >= switchTop && mouseY <= switchTop + SWITCH_BUTTON_SIZE) {
                    switchRequested.add(key);
                    return true;
                }
            }
            return false;
        }
        int listTop = getListTop();
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = displayList.size();
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcRequiredEntry entry = displayList.get(i);
            String key = progressKey(entry);
            Float progress = progressByKey.get(key);
            if (progress == null || !shouldShowSwitch(key, entry, progress)) {
                continue;
            }
            int row = i - scrollIndex;
            int rowY = listTop + row * lineHeight;
            int barWidth = 36;
            int barHeight = 6;
            int percentBoxWidth = this.font.width("100%");
            int percentRight = this.width - 18;
            int percentLeft = percentRight - percentBoxWidth;
            int barRight = percentLeft - 6;
            int barLeft = barRight - barWidth;
            int barY = rowY + 2;
            int switchRight = barLeft - SWITCH_BUTTON_PAD;
            int switchLeft = switchRight - SWITCH_BUTTON_SIZE;
            int switchTop = barY - 1;
            if (mouseX >= switchLeft && mouseX <= switchLeft + SWITCH_BUTTON_SIZE
                && mouseY >= switchTop && mouseY <= switchTop + SWITCH_BUTTON_SIZE) {
                switchRequested.add(key);
                return true;
            }
        }
        return false;
    }

    private static final class DownloadSwitchException extends IOException {
        private DownloadSwitchException(String message) {
            super(message);
        }
    }

    private static final class DownloadCanceledException extends IOException {
        private DownloadCanceledException(String message) {
            super(message);
        }
    }

    private DownloadInfo openDownload(String url, long offset) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
        String contentMd5 = connection.getHeaderField("Content-MD5");
        return new DownloadInfo(connection, fileName, responseCode, totalBytes, contentMd5);
    }

    private void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private void initProgressMap(List<DlcRequiredEntry> pending) {
        progressByKey.clear();
        attemptByKey.clear();
        downloadStartByKey.clear();
        switchRequested.clear();
        activeDownloadEntry = null;
        if (pending == null) {
            return;
        }
        for (DlcRequiredEntry entry : pending) {
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

    private String progressKey(DlcRequiredEntry entry) {
        if (entry == null) {
            return "";
        }
        String id = entry.getIdentifier();
        String suffix = "#" + System.identityHashCode(entry);
        if (id != null && !id.isBlank()) {
            return id + suffix;
        }
        return suffix;
    }

    private static final class DownloadResult {
        private final DlcRequiredEntry entry;
        private final boolean success;
        private final boolean canceled;
        private final Exception error;

        private DownloadResult(DlcRequiredEntry entry, boolean success, boolean canceled, Exception error) {
            this.entry = entry;
            this.success = success;
            this.canceled = canceled;
            this.error = error;
        }
    }

    private static final class DownloadInfo {
        private final HttpURLConnection connection;
        private final String fileName;
        private final int responseCode;
        private final long totalBytes;
        private final String contentMd5;

        private DownloadInfo(HttpURLConnection connection, String fileName, int responseCode, long totalBytes, String contentMd5) {
            this.connection = connection;
            this.fileName = fileName;
            this.responseCode = responseCode;
            this.totalBytes = totalBytes;
            this.contentMd5 = contentMd5;
        }
    }

    private void verifyDownloadedFile(Path tempFile, DlcDownloadCandidate candidate, DownloadInfo info) throws IOException {
        checkCanceled();
        String hashType = candidate == null ? null : candidate.hashType;
        String hashValue = candidate == null ? null : candidate.hashValue;
        if (hashType != null && hashValue != null && !hashValue.isBlank()) {
            String algorithm = mapHashAlgorithm(hashType);
            String actual = DlcDownloadUtil.computeHashHex(tempFile, algorithm);
            if (actual == null || !actual.equalsIgnoreCase(DlcDownloadUtil.normalizeHash(hashValue))) {
                Files.deleteIfExists(tempFile);
                throw new IOException("hash mismatch");
            }
            return;
        }
        if (info != null && info.contentMd5 != null && !info.contentMd5.isBlank()) {
            String expected = DlcDownloadUtil.normalizeHash(info.contentMd5);
            String actualHex = DlcDownloadUtil.computeHashHex(tempFile, "MD5");
            String actualBase64 = DlcDownloadUtil.computeMd5Base64(tempFile);
            if (actualHex == null || actualBase64 == null
                || (!expected.equalsIgnoreCase(actualHex) && !expected.equalsIgnoreCase(actualBase64))) {
                Files.deleteIfExists(tempFile);
                throw new IOException("hash mismatch");
            }
            return;
        }
        long expectedSize = candidate == null ? -1L : candidate.expectedSize;
        if (expectedSize <= 0 && info != null && info.totalBytes > 0) {
            expectedSize = info.totalBytes;
        }
        if (expectedSize > 0) {
            long size = Files.size(tempFile);
            if (size != expectedSize) {
                Files.deleteIfExists(tempFile);
                throw new IOException("size mismatch");
            }
        }
    }

    private String mapHashAlgorithm(String hashType) {
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
}
