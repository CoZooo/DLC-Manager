package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker;
import com.cozooo.dlc_manager.client.remote.CurseForgeClient;
import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.Json5Util;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final int LINE_HEIGHT = 10;
    private static final int CHECKBOX_SIZE = 8;
    private static final int CHECKBOX_X = 12;
    private static final String NONE_MARKER = "__NONE__";
    private static final String ALL_MARKER = "__ALL__";
    private static final long SWITCH_DELAY_MS = 20_000L;
    private static final int SWITCH_BUTTON_SIZE = 8;
    private static final int SWITCH_BUTTON_PAD = 4;

    private final Screen parent;
    private final List<DlcRemoteEntry> missing;
    private List<DlcRemoteEntry> previewQueue = List.of();
    private List<DlcRemoteEntry> downloadTargets = List.of();
    private final int maxConcurrentDownloads;
    private final int maxRetry;
    private final int connectTimeoutMs;
    private final int taskTimeoutMs;
    private final Set<String> selectedKeys = new HashSet<>(Set.of(ALL_MARKER));
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private final Map<String, Integer> attemptByKey = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger attemptCounter = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<String, Long> downloadStartByKey = new ConcurrentHashMap<>();
    private final Set<String> switchRequested = ConcurrentHashMap.newKeySet();
    private volatile DlcRemoteEntry activeDownloadEntry;

    private Button downloadButton;
    private Button skipButton;

    private volatile boolean downloading;
    private volatile boolean cancelRequested;
    private volatile boolean detecting = true;
    private volatile String statusText;
    private volatile String errorText;
    private volatile int totalCount;
    private volatile int completedCount;
    private volatile float currentProgress;
    private int scrollIndex;
    private boolean draggingScrollBar;
    private int scrollBarDragOffset;

    public DlcRemoteDownloadScreen(Screen parent, List<DlcRemoteEntry> missing) {
        super(Component.translatable("dlc_manager.remote.title"));
        this.parent = parent;
        this.missing = missing == null ? List.of() : missing;
        this.maxConcurrentDownloads = Math.max(1, DlcPathConfig.getMaxConcurrentDownloads());
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
        downloadButton = Button.builder(Component.translatable("dlc_manager.remote.download"), button -> handleDownloadButton())
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        drawCheckbox(guiGraphics, CHECKBOX_X, listTop + 1, CHECKBOX_SIZE, isAllSelected());
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.remote.select_all"),
            CHECKBOX_X + CHECKBOX_SIZE + 6, listTop, 0xE6E6E6, false);
        int listStart = listTop + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int lineHeight = LINE_HEIGHT;
        int maxVisible = Math.max(1, (listBottom - listStart) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcRemoteEntry entry = showList.get(i);
            String name = entry.resolveDisplayName();
            boolean checked = isAllSelected() || selectedKeys.contains(selectionKey(entry));
            int rowY = listStart + (i - scrollIndex) * lineHeight;
            drawCheckbox(guiGraphics, CHECKBOX_X, rowY + 1, CHECKBOX_SIZE, checked);
            guiGraphics.drawString(this.font, DlcTextFormatter.parse(name), CHECKBOX_X + CHECKBOX_SIZE + 6, rowY, 0xE6E6E6, false);
            if (downloading && maxConcurrentDownloads > 1) {
                String key = progressKey(entry);
                Float progress = progressByKey.get(key);
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
                    int barY = rowY + 2;
                    drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress);
                    if (shouldShowSwitch(key, entry, progress)) {
                        int switchRight = barLeft - SWITCH_BUTTON_PAD;
                        int switchLeft = switchRight - SWITCH_BUTTON_SIZE;
                        int switchTop = barY - 1;
                        drawSwitchButton(guiGraphics, switchLeft, switchTop);
                    }
                    guiGraphics.drawString(this.font, progressText, percentRight - textWidth, rowY, 0xC8C8C8, false);
                }
            }
        }
        drawScrollBar(guiGraphics, listStart, listBottom, totalLines, maxVisible, maxScroll);

        if (downloading) {
            y = this.height - 70;
            if (maxConcurrentDownloads <= 1) {
                String status = statusText == null ? "" : statusText;
                guiGraphics.drawCenteredString(this.font, DlcTextFormatter.parse(status), centerX, y, 0xE6E6E6);
                y += 12;
                DlcRemoteEntry activeEntry = activeDownloadEntry;
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
            guiGraphics.drawCenteredString(this.font, DlcTextFormatter.parse(errorText), centerX, y, 0xFF6B6B);
        }

    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int lineHeight = LINE_HEIGHT;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (downloading && handleSwitchClick(mouseX, mouseY)) {
            return true;
        }
        if (!downloading) {
            List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
            int listTop = getListTop();
            int listBottom = this.height - 70;
            if (mouseY >= listTop && mouseY < listTop + LINE_HEIGHT) {
                int boxX = CHECKBOX_X;
                int boxY = listTop + 1;
                if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE
                    && mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE) {
                    toggleSelectAll();
                    return true;
                }
            }
            int listStart = listTop + LINE_HEIGHT;
            if (mouseY >= listStart && mouseY < listBottom) {
                int row = (int) ((mouseY - listStart) / LINE_HEIGHT);
                int index = scrollIndex + row;
                if (index >= 0 && index < showList.size()) {
                    int boxX = CHECKBOX_X;
                    int boxY = listStart + row * LINE_HEIGHT + 1;
                    if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE
                        && mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE) {
                        toggleSelection(showList.get(index), showList);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollBar) {
            updateScrollFromDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollBar) {
            draggingScrollBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private boolean beginScrollDrag(double mouseX, double mouseY) {
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int lineHeight = LINE_HEIGHT;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return false;
        }
        int barX = this.width - 12;
        int barWidth = 4;
        int barHeight = Math.max(12, (listBottom - listTop) * maxVisible / totalLines);
        int trackHeight = listBottom - listTop;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = listTop + (int) ((scrollIndex / (float) maxScroll) * maxTrack);
        if (mouseX < barX || mouseX > barX + barWidth || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        if (mouseY >= barY && mouseY <= barY + barHeight) {
            draggingScrollBar = true;
            scrollBarDragOffset = (int) mouseY - barY;
            return true;
        }
        int targetBarY = (int) mouseY - barHeight / 2;
        targetBarY = Math.max(listTop, Math.min(listTop + maxTrack, targetBarY));
        int newIndex = Math.round(((targetBarY - listTop) / (float) maxTrack) * maxScroll);
        scrollIndex = Math.max(0, Math.min(maxScroll, newIndex));
        return true;
    }

    private void updateScrollFromDrag(double mouseY) {
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int lineHeight = LINE_HEIGHT;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return;
        }
        int barHeight = Math.max(12, (listBottom - listTop) * maxVisible / totalLines);
        int trackHeight = listBottom - listTop;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int targetBarY = (int) mouseY - scrollBarDragOffset;
        targetBarY = Math.max(listTop, Math.min(listTop + maxTrack, targetBarY));
        int newIndex = Math.round(((targetBarY - listTop) / (float) maxTrack) * maxScroll);
        scrollIndex = Math.max(0, Math.min(maxScroll, newIndex));
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
            // Disabled: do not auto-append Modrinth required dependencies into remote queue.
            // for (DlcRemoteEntry dep : fetchModrinthRequired(entry)) {
            //     work.add(dep);
            // }
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
        statusText = Component.translatable("dlc_manager.remote.canceling").getString();
        downloadButton.active = false;
        skipButton.active = false;
    }

    private void updateDownloadButtonLabel(boolean isDownloading) {
        if (downloadButton == null) {
            return;
        }
        downloadButton.setMessage(Component.translatable(isDownloading
            ? "dlc_manager.remote.cancel"
            : "dlc_manager.remote.download"));
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        cancelRequested = false;
        errorText = null;
        updateDownloadButtonLabel(true);
        downloadButton.active = true;
        skipButton.active = false;
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        List<DlcRemoteEntry> targets = resolveSelectionTargets(showList);
        downloadTargets = targets;
        updatePartMarker(showList, targets);
        initProgressMap(targets);
        new Thread(() -> downloadAll(targets), "dlc-remote-download").start();
    }

    private void downloadAll(List<DlcRemoteEntry> targets) {
        totalCount = targets.size();
        completedCount = 0;
        List<DlcRemoteEntry> failed = runDownloadBatch(targets);
        if (cancelRequested) {
            downloading = false;
            statusText = Component.translatable("dlc_manager.remote.canceled").getString();
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                skipButton.active = true;
            });
            return;
        }
        if (!failed.isEmpty()) {
            failed = runDownloadBatch(failed);
        }
        if (cancelRequested) {
            downloading = false;
            statusText = Component.translatable("dlc_manager.remote.canceled").getString();
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                skipButton.active = true;
            });
            return;
        }
        if (!failed.isEmpty()) {
            downloading = false;
            Minecraft.getInstance().execute(() -> {
                updateDownloadButtonLabel(false);
                downloadButton.active = true;
                skipButton.active = true;
            });
            return;
        }
        errorText = null;
        downloading = false;
        Minecraft.getInstance().execute(() -> {
            updateDownloadButtonLabel(false);
            Minecraft.getInstance().setScreen(parent);
        });
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
        checkCanceled();
        currentProgress = 0f;
        String progressKey = progressKey(entry);
        if (maxConcurrentDownloads <= 1) {
            activeDownloadEntry = entry;
        }
        try {
            List<DownloadCandidate> candidates = buildDownloadCandidates(entry);
            IOException lastError = null;
            int attemptId = 0;
            boolean canceled = false;
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                DownloadCandidate candidate = candidates.get(candidateIndex);
                for (int attempt = 1; attempt <= maxRetry; attempt++) {
                    try {
                        checkCanceled();
                        attemptId = attemptCounter.incrementAndGet();
                        attemptByKey.put(progressKey, attemptId);
                        markDownloadStart(progressKey);
                        DownloadInfo downloadInfo = openDownload(candidate.url, 0);
                        String headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
                        closeQuietly(downloadInfo.connection);
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
                            setEntryProgress(progressKey, attemptId, 1f);
                            return;
                        }
                        final int currentAttemptId = attemptId;
                        Path downloaded = DlcDownloadService.download(candidate.url, target.getParent(), fileName,
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
                        setEntryProgress(progressKey, currentAttemptId, 1f);
                        entry.setFileNameHint(fileName);
                        DlcRemoteManager.updateFileNameKey(entry, fileName);
                        return;
                    } catch (DlcDownloadService.DownloadCanceledException ex) {
                        if (isSwitchRequested(progressKey)) {
                            lastError = new DownloadSwitchException("switch");
                            clearSwitchRequest(progressKey);
                            if (candidateIndex < candidates.size() - 1) {
                                if (!canceled) {
                                    setEntryProgress(progressKey, attemptId, 0f);
                                }
                                break;
                            }
                            continue;
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
                            if (candidateIndex < candidates.size() - 1) {
                                if (!canceled) {
                                    setEntryProgress(progressKey, attemptId, 0f);
                                }
                                break;
                            }
                            continue;
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
            if (cancelRequested || canceled) {
                throw new DownloadCanceledException("canceled");
            }
            if (attemptId > 0) {
                setEntryProgress(progressKey, attemptId, 0f);
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

    private static final class DownloadSwitchException extends IOException {
        private DownloadSwitchException(String message) {
            super(message);
        }
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

    private boolean hasMultipleSources(DlcRemoteEntry entry) {
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

    private boolean shouldShowSwitch(String key, DlcRemoteEntry entry, float progress) {
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
            DlcRemoteEntry activeEntry = activeDownloadEntry;
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
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int lineHeight = LINE_HEIGHT;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcRemoteEntry entry = showList.get(i);
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

    private static final class DownloadCanceledException extends IOException {
        private DownloadCanceledException(String message) {
            super(message);
        }
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
        String contentMd5 = connection.getHeaderField("Content-MD5");
        return new DownloadInfo(connection, fileName, responseCode, totalBytes, contentMd5);
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
                CrossPlatformRuntime.mcVersion(),
                CrossPlatformRuntime.loaderName()
            );
        }
        CurseForgeClient.CurseForgeFile curseFile = null;
        String curseFileId = entry.getCurseforgeFileId();
        if (curseFileId != null && !curseFileId.isBlank()) {
            curseFile = CurseForgeClient.fetchFileByIdOrVersion(
                entry.getCurseforgeProjectId(),
                curseFileId,
                CrossPlatformRuntime.mcVersion(),
                CrossPlatformRuntime.loaderName()
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
        String curseHashType = null;
        String curseHashValue = null;
        long curseSize = -1L;
        if (curseFile != null) {
            if (curseFile.sha1 != null && !curseFile.sha1.isBlank()) {
                curseHashType = "sha1";
                curseHashValue = curseFile.sha1;
            } else if (curseFile.md5 != null && !curseFile.md5.isBlank()) {
                curseHashType = "md5";
                curseHashValue = curseFile.md5;
            }
            curseSize = curseFile.size;
        }
        String modrinthHashType = null;
        String modrinthHashValue = null;
        long modrinthSize = -1L;
        if (version != null) {
            if (version.sha1 != null && !version.sha1.isBlank()) {
                modrinthHashType = "sha1";
                modrinthHashValue = version.sha1;
            } else if (version.sha512 != null && !version.sha512.isBlank()) {
                modrinthHashType = "sha512";
                modrinthHashValue = version.sha512;
            }
            modrinthSize = version.size;
        }
        List<String> order = entry.getDownloadPriorityList();
        for (String source : order) {
            if ("directurl".equals(source)) {
                for (String url : directUrls) {
                    candidates.add(new DownloadCandidate(url, true, null, null, null, -1L));
                }
            } else if ("curseforge".equals(source)) {
                if (curseFile != null && curseFile.url != null && !curseFile.url.isBlank()) {
                    candidates.add(new DownloadCandidate(curseFile.url, false, curseFile.fileName, curseHashType, curseHashValue, curseSize));
                }
            } else if ("modrinth".equals(source)) {
                if (version != null && version.url != null && !version.url.isBlank()) {
                    candidates.add(new DownloadCandidate(version.url, false, version.fileName, modrinthHashType, modrinthHashValue, modrinthSize));
                }
            }
        }
        return candidates;
    }

    private static final class DownloadCandidate {
        private final String url;
        private final boolean direct;
        private final String fileNameOverride;
        private final String hashType;
        private final String hashValue;
        private final long expectedSize;

        private DownloadCandidate(String url, boolean direct, String fileNameOverride, String hashType, String hashValue, long expectedSize) {
            this.url = url;
            this.direct = direct;
            this.fileNameOverride = fileNameOverride;
            this.hashType = hashType;
            this.hashValue = hashValue;
            this.expectedSize = expectedSize;
        }
    }

    private void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private void initProgressMap(List<DlcRemoteEntry> pending) {
        progressByKey.clear();
        attemptByKey.clear();
        downloadStartByKey.clear();
        switchRequested.clear();
        activeDownloadEntry = null;
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

    private List<DlcRemoteEntry> resolveSelectionTargets(List<DlcRemoteEntry> showList) {
        if (showList == null || showList.isEmpty()) {
            return List.of();
        }
        if (isNoneSelected()) {
            return List.of();
        }
        if (isAllSelected()) {
            return new ArrayList<>(showList);
        }
        List<DlcRemoteEntry> targets = new ArrayList<>();
        for (DlcRemoteEntry entry : showList) {
            if (selectedKeys.contains(selectionKey(entry))) {
                targets.add(entry);
            }
        }
        if (targets.isEmpty()) {
            selectedKeys.clear();
            selectedKeys.add(ALL_MARKER);
            return new ArrayList<>(showList);
        }
        return targets;
    }

    private void updatePartMarker(List<DlcRemoteEntry> showList, List<DlcRemoteEntry> targets) {
        if (showList == null || targets == null) {
            return;
        }
        boolean partial = !isAllSelected();
        Path dlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (dlcRoot == null) {
            return;
        }
        if (partial) {
            DlcRemoteMarker.writePartMarker(dlcRoot);
        } else {
            DlcRemoteMarker.clearPartMarker(dlcRoot);
        }
    }

    private void toggleSelection(DlcRemoteEntry entry, List<DlcRemoteEntry> showList) {
        String key = selectionKey(entry);
        if (key.isBlank()) {
            return;
        }
        if (isAllSelected() && showList != null && !showList.isEmpty()) {
            selectedKeys.clear();
            for (DlcRemoteEntry item : showList) {
                String itemKey = selectionKey(item);
                if (!itemKey.isBlank() && !itemKey.equals(key)) {
                    selectedKeys.add(itemKey);
                }
            }
            if (selectedKeys.isEmpty()) {
                selectedKeys.add(NONE_MARKER);
            }
            return;
        }
        if (isNoneSelected()) {
            selectedKeys.clear();
        }
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key);
        } else {
            selectedKeys.add(key);
        }
        if (showList != null && !showList.isEmpty() && selectedKeys.size() == showList.size()) {
            selectedKeys.clear();
            selectedKeys.add(ALL_MARKER);
            return;
        }
        if (selectedKeys.isEmpty()) {
            selectedKeys.add(NONE_MARKER);
        }
    }

    private String selectionKey(DlcRemoteEntry entry) {
        if (entry == null) {
            return "";
        }
        Path config = entry.getConfigPath();
        if (config != null) {
            return config.toString();
        }
        String identifier = entry.getDlcIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        String unique = entry.getUniqueKey();
        return unique == null ? "" : unique;
    }

    private void drawCheckbox(GuiGraphics guiGraphics, int x, int y, int size, boolean checked) {
        int bg = 0xFF2B2B2B;
        int outline = 0xFF6B6B6B;
        int fill = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + size, y + size, outline);
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, bg);
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, fill);
        }
    }

    private int getListTop() {
        int y = 20 + 18 + 16;
        if (detecting && !downloading) {
            y += 12;
        }
        return y;
    }

    private void toggleSelectAll() {
        if (isAllSelected()) {
            selectedKeys.clear();
            selectedKeys.add(NONE_MARKER);
            return;
        }
        selectedKeys.clear();
        selectedKeys.add(ALL_MARKER);
    }

    private boolean isAllSelected() {
        return selectedKeys.contains(ALL_MARKER);
    }

    private boolean isNoneSelected() {
        return selectedKeys.contains(NONE_MARKER);
    }

    private List<DlcRemoteEntry> runDownloadBatch(List<DlcRemoteEntry> pending) {
        List<DlcRemoteEntry> failed = new ArrayList<>();
        if (pending == null || pending.isEmpty()) {
            return failed;
        }
        if (cancelRequested) {
            return failed;
        }
        if (maxConcurrentDownloads <= 1) {
            for (DlcRemoteEntry entry : pending) {
                if (cancelRequested) {
                    break;
                }
                statusText = Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
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
                if (result.canceled) {
                    cancelRequested = true;
                } else if (result.success) {
                    completedCount++;
                } else {
                    failed.add(result.entry);
                    if (result.error != null) {
                        errorText = Component.translatable("dlc_manager.remote.error", result.error.getMessage()).getString();
                    }
                }
            } catch (Exception e) {
                if (e instanceof DownloadCanceledException) {
                    cancelRequested = true;
                } else {
                    errorText = Component.translatable("dlc_manager.remote.error", e.getMessage()).getString();
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

    private DownloadResult downloadEntryWithResult(DlcRemoteEntry entry) {
        try {
            downloadEntry(entry);
            return new DownloadResult(entry, true, false, null);
        } catch (DownloadCanceledException e) {
            return new DownloadResult(entry, false, true, null);
        } catch (Exception e) {
            return new DownloadResult(entry, false, false, e);
        }
    }

    private static final class DownloadResult {
        private final DlcRemoteEntry entry;
        private final boolean success;
        private final boolean canceled;
        private final Exception error;

        private DownloadResult(DlcRemoteEntry entry, boolean success, boolean canceled, Exception error) {
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

    private void verifyDownloadedFile(Path tempFile, DownloadCandidate candidate, DownloadInfo info) throws IOException {
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

    private String stripJson(String name) {
        return Json5Util.stripJsonExtension(name);
    }
}
