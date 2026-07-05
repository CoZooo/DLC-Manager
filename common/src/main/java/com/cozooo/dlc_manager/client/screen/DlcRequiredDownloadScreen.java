package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        this.maxConcurrentDownloads = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
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
        List<DlcRequiredEntry> applyCandidates = new ArrayList<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry != null) {
                applyCandidates.add(entry);
            }
        }
        List<DlcRequiredEntry> download = DlcRequiredService.collectMissingDownloads(applyCandidates);
        List<DlcRequiredEntry> needsApply = DlcRequiredService.collectNeedsApply(applyCandidates);
        List<DlcRequiredEntry> missingOrApply = new ArrayList<>();
        for (DlcRequiredEntry entry : applyCandidates) {
            if (download.contains(entry) || needsApply.contains(entry)) {
                missingOrApply.add(entry);
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
        DlcRequiredService.BatchResult result;
        try {
            result = DlcRequiredService.downloadAll(pending, new DlcRequiredService.BatchProgressListener() {
                @Override
                public void onEntryStart(DlcRequiredEntry entry, int index, int total) {
                    String key = progressKey(entry);
                    if (maxConcurrentDownloads <= 1) {
                        activeDownloadEntry = entry;
                        statusText = Component.translatable("dlc_manager.required.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
                    } else {
                        statusText = Component.translatable("dlc_manager.required.downloading_multi").getString();
                    }
                }

                @Override
                public void onEntryAttemptStart(DlcRequiredEntry entry, int attemptId, int index, int total) {
                    String key = progressKey(entry);
                    attemptByKey.put(key, attemptId);
                    markDownloadStart(key);
                    if (maxConcurrentDownloads <= 1) {
                        activeDownloadEntry = entry;
                    }
                }

                @Override
                public void onEntryProgress(DlcRequiredEntry entry, long downloadedBytes, long totalBytes) {
                    if (totalBytes > 0) {
                        String key = progressKey(entry);
                        Integer attemptId = attemptByKey.get(key);
                        setEntryProgress(key, attemptId == null ? 0 : attemptId, Math.min(1f, downloadedBytes / (float) totalBytes));
                    }
                }

                @Override
                public void onEntryStatus(DlcRequiredEntry entry, String statusKey, int completed, int total) {
                    if ("selecting_source".equals(statusKey)) {
                        statusText = Component.translatable("dlc_manager.required.selecting_source").getString();
                    } else if (maxConcurrentDownloads <= 1 && entry != null) {
                        statusText = Component.translatable("dlc_manager.required.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
                    } else {
                        statusText = Component.translatable("dlc_manager.required.downloading_multi").getString();
                    }
                }

                @Override
                public void onEntrySuccess(DlcRequiredEntry entry, String fileName, int completed, int total) {
                    completedCount = completed;
                    downloadedAny = true;
                    String key = progressKey(entry);
                    Integer attemptId = attemptByKey.get(key);
                    setEntryProgress(key, attemptId == null ? 0 : attemptId, 1f);
                }

                @Override
                public void onEntryFailure(DlcRequiredEntry entry, Throwable error, int completed, int total) {
                    completedCount = completed;
                    errorText = Component.translatable("dlc_manager.required.error", error == null ? "" : error.getMessage()).getString();
                }

                @Override
                public void onEntryComplete(DlcRequiredEntry entry) {
                    String key = progressKey(entry);
                    downloadStartByKey.remove(key);
                    switchRequested.remove(key);
                    if (activeDownloadEntry == entry) {
                        activeDownloadEntry = null;
                    }
                }
            }, () -> cancelRequested, new DlcRequiredService.SourceSwitchChecker() {
                @Override
                public boolean isSwitchRequested(DlcRequiredEntry entry) {
                    return DlcRequiredDownloadScreen.this.isSwitchRequested(progressKey(entry));
                }

                @Override
                public void clearSwitchRequest(DlcRequiredEntry entry) {
                    DlcRequiredDownloadScreen.this.clearSwitchRequest(progressKey(entry));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelRequested = true;
            result = new DlcRequiredService.BatchResult(pending.size(), 0,
                List.of(new DlcRequiredService.EntryError(null, e)));
        } finally {
            activeDownloadEntry = null;
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
        if (!result.errors.isEmpty()) {
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
            if (entry == null) {
                continue;
            }
            if (DlcRequiredService.installOne(entry)) {
                didApply = true;
            }
        }
        if (updatePackageRequired) {
            UpdateCheckResult updateCheck = DlcUpdateService.checkForUpdates(true);
            if (updateCheck != null && updateCheck.getUpdates() != null && !updateCheck.getUpdates().isEmpty()) {
                UpdateApplyResult updateResult = DlcUpdateService.applyUpdates(updateCheck.getUpdates(), false);
                if (updateResult != null && updateResult.isMissing()) {
                    Minecraft.getInstance().execute(() -> minecraft.setScreen(new DlcUpdateMissingScreen(parent, updateCheck, updateResult.getMissingPaths())));
                    return;
                }
                if (updateResult == null || !updateResult.isSuccess()) {
                    Minecraft.getInstance().execute(() -> minecraft.setScreen(new DlcUpdateResultScreen(parent, false, DlcMessageAdapter.toFailureComponent(updateResult))));
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
        return DlcDcFormatUtil.isDcName(fileName);
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
                        if (!DlcDcFormatUtil.isDcName(name)) {
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

}
