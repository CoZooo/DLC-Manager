package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteService;
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker;
import com.cozooo.dlc_manager.core.DlcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DlcRemoteDownloadScreen extends DlcRemotePageScreen {



    private static final String NONE_MARKER = "__NONE__";
    private static final String ALL_MARKER = "__ALL__";
    private static final long SWITCH_DELAY_MS = 20_000L;



    private final Screen parent;
    private final List<DlcRemoteEntry> missing;
    private final String partMarkerKey;
    private List<DlcRemoteEntry> previewQueue = List.of();
    private List<DlcRemoteEntry> downloadTargets = List.of();
    private final int maxConcurrentDownloads;
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

    public DlcRemoteDownloadScreen(Screen parent, List<DlcRemoteEntry> missing) {
        this(parent, missing, DlcRemoteMarker.PART_DLC, Component.translatable("dlc_manager.remote.title"));
    }

    public DlcRemoteDownloadScreen(Screen parent, List<DlcRemoteEntry> missing, String partMarkerKey, Component title) {
        super(DlcRemoteMarker.PART_DLC_PACK.equals(partMarkerKey) ? DlcUiPage.REMOTE_PACK_DOWNLOAD : DlcUiPage.REMOTE_DOWNLOAD, parent, title);
        this.parent = parent;
        this.missing = missing == null ? List.of() : missing;
        this.partMarkerKey = partMarkerKey;
        this.maxConcurrentDownloads = Math.max(1, DlcConfig.getMaxConcurrentDownloads());
    }

    private void initControls() {
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
        skipButton = Button.builder(Component.translatable("dlc_manager.remote.skip"), button -> handleSkip())
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(skipButton);
        detecting = true;
        statusText = Component.translatable("dlc_manager.remote.preparing").getString();
        new Thread(this::buildPreviewQueue, "dlc-remote-preview").start();
    }

    private void buildPreviewQueue() {
        previewQueue = DlcRemoteService.expandWithDependencies(missing);
        detecting = false;
        statusText = Component.translatable("dlc_manager.remote.ready").getString();
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

    private void handleSkip() {
        writeSkipMarker();
        Minecraft.getInstance().setScreen(parent);
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
        boolean fullSelection = isAllSelected();
        List<DlcRemoteEntry> targets = resolveSelectionTargets(showList);
        downloadTargets = targets;
        if (!fullSelection) {
            writeSkipMarker();
        }
        initProgressMap(targets);
        new Thread(() -> downloadAll(targets, fullSelection), "dlc-remote-download").start();
    }

    private void downloadAll(List<DlcRemoteEntry> targets, boolean clearSkipOnSuccess) {
        totalCount = targets.size();
        completedCount = 0;
        DlcRemoteService.BatchResult result;
        try {
            result = DlcRemoteService.downloadAll(targets, new DlcRemoteService.BatchProgressListener() {
                @Override
                public void onEntryStart(DlcRemoteEntry entry, int index, int total) {
                    if (maxConcurrentDownloads <= 1) {
                        activeDownloadEntry = entry;
                        statusText = Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
                    } else {
                        statusText = Component.translatable("dlc_manager.remote.downloading_multi").getString();
                    }
                }

                @Override
                public void onEntryAttemptStart(DlcRemoteEntry entry, int attemptId, int index, int total) {
                    String key = progressKey(entry);
                    attemptByKey.put(key, attemptId);
                    markDownloadStart(key);
                    if (maxConcurrentDownloads <= 1) {
                        activeDownloadEntry = entry;
                    }
                }

                @Override
                public void onEntryProgress(DlcRemoteEntry entry, long downloadedBytes, long totalBytes) {
                    if (totalBytes > 0) {
                        String key = progressKey(entry);
                        Integer attemptId = attemptByKey.get(key);
                        setEntryProgress(key, attemptId == null ? 0 : attemptId, Math.min(1f, downloadedBytes / (float) totalBytes));
                    }
                }

                @Override
                public void onEntrySuccess(DlcRemoteEntry entry, String fileName, int completed, int total) {
                    completedCount = completed;
                    String key = progressKey(entry);
                    Integer attemptId = attemptByKey.get(key);
                    setEntryProgress(key, attemptId == null ? 0 : attemptId, 1f);
                }

                @Override
                public void onEntryFailure(DlcRemoteEntry entry, Throwable error, int completed, int total) {
                    completedCount = completed;
                    errorText = Component.translatable("dlc_manager.remote.error", error == null ? "" : error.getMessage()).getString();
                }

                @Override
                public void onEntryStatus(DlcRemoteEntry entry, String statusKey, int completed, int total) {
                    if ("selecting_source".equals(statusKey)) {
                        statusText = Component.translatable("dlc_manager.remote.selecting_source").getString();
                    } else if (maxConcurrentDownloads <= 1 && entry != null) {
                        statusText = Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
                    } else {
                        statusText = Component.translatable("dlc_manager.remote.downloading_multi").getString();
                    }
                }

                @Override
                public void onEntryComplete(DlcRemoteEntry entry) {
                    String key = progressKey(entry);
                    downloadStartByKey.remove(key);
                    switchRequested.remove(key);
                    if (activeDownloadEntry == entry) {
                        activeDownloadEntry = null;
                    }
                }
            }, () -> cancelRequested, new DlcRemoteService.SourceSwitchChecker() {
                @Override
                public boolean isSwitchRequested(DlcRemoteEntry entry) {
                    return DlcRemoteDownloadScreen.this.isSwitchRequested(progressKey(entry));
                }

                @Override
                public void clearSwitchRequest(DlcRemoteEntry entry) {
                    DlcRemoteDownloadScreen.this.clearSwitchRequest(progressKey(entry));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelRequested = true;
            result = new DlcRemoteService.BatchResult(targets.size(), 0,
                List.of(new DlcRemoteService.EntryError(null, e)));
        } finally {
            activeDownloadEntry = null;
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
        if (!result.errors.isEmpty()) {
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
        if (clearSkipOnSuccess) {
            clearSkipMarker();
        }
        Minecraft.getInstance().execute(() -> {
            updateDownloadButtonLabel(false);
            Minecraft.getInstance().setScreen(parent);
        });
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

    private void markDownloadStart(String key) {
        if (key != null) {
            downloadStartByKey.put(key, System.currentTimeMillis());
        }
    }

    private boolean isSwitchRequested(String key) {
        return key != null && switchRequested.contains(key);
    }

    private void clearSwitchRequest(String key) {
        if (key != null) {
            switchRequested.remove(key);
        }
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
        return targets;
    }

    private void writeSkipMarker() {
        Path dlcRoot = firstDlcRoot();
        if (dlcRoot == null) {
            return;
        }
        DlcRemoteMarker.writePartMarker(dlcRoot, partMarkerKey);
    }

    private void clearSkipMarker() {
        Path dlcRoot = firstDlcRoot();
        if (dlcRoot == null) {
            return;
        }
        DlcRemoteMarker.clearPartMarker(dlcRoot, partMarkerKey);
    }

    private Path firstDlcRoot() {
        List<Path> roots = DlcConfig.getDlcRoots();
        return roots.isEmpty() ? null : roots.get(0);
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


    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash((previewQueue.isEmpty() ? missing : previewQueue), downloading, detecting);
        sourceButtons.clear();
        var showList = previewQueue.isEmpty() ? missing : previewQueue;
        var list = remoteList(this::isAllSelected, () -> !downloading && !detecting, this::toggleSelectAll);
        for (var entry : showList) {
            var line = remoteRow(list, entry.resolveDisplayName(), () -> isAllSelected() || selectedKeys.contains(selectionKey(entry)),
                () -> !downloading && !detecting, () -> toggleSelection(entry, showList));
            remoteTip(line, entry.resolveDisplayName(), entry.getTips());
            String key = progressKey(entry);
            if (downloading && maxConcurrentDownloads > 1 && progressByKey.containsKey(key)) {
                var controls = node(line, "div", "remote-row-progress");
                sourceSwitch(controls, entry);
                remoteProgress(controls, () -> progressByKey.getOrDefault(key, 0f));
            }
        }
        var summary = remoteStatus();
        live(summary, () -> detecting ? tr("dlc_manager.remote.detecting") : downloading ? statusText : "", "remote-download-status");
        live(summary, () -> errorText, "page-error");
        if (detecting) progress(summary, () -> -1);
        if (downloading) {
            var meter = node(summary, "div", "remote-total-progress");
            if (maxConcurrentDownloads <= 1) sourceSwitch(meter, () -> activeDownloadEntry);
            remoteProgress(meter, () -> maxConcurrentDownloads <= 1 ? currentProgress : getTotalProgress());
        }
        mount(footer, skipButton);
        mount(footer, downloadButton).getElement().setAttribute("class", "button primary");
    }

    private void sourceSwitch(com.sighs.apricityui.init.Element parent, DlcRemoteEntry entry) {
        sourceSwitch(parent, () -> entry);
    }

    private void sourceSwitch(com.sighs.apricityui.init.Element parent, java.util.function.Supplier<DlcRemoteEntry> entry) {
        var button = action(parent, tr("dlc_manager.remote.switch_source"), () -> {
            var active = entry.get();
            if (canSwitchSource(active)) switchRequested.add(progressKey(active));
        });
        button.getElement().setAttribute("class", "button remote-source-switch");
        button.getElement().setAttribute("style", "display:none");
        boolean[] shown = {false};
        sourceButtons.add(() -> {
            boolean visible = canSwitchSource(entry.get());
            if (shown[0] != visible) {
                shown[0] = visible;
                button.getElement().setAttribute("style", visible ? "" : "display:none");
            }
        });
    }

    private final List<Runnable> sourceButtons = new ArrayList<>();

    private boolean canSwitchSource(DlcRemoteEntry entry) {
        if (!downloading || entry == null || !hasMultipleSources(entry)) return false;
        String key = progressKey(entry);
        Long start = downloadStartByKey.get(key);
        return start != null && System.currentTimeMillis() - start >= SWITCH_DELAY_MS
            && progressByKey.getOrDefault(key, 0f) < 1f && !switchRequested.contains(key);
    }

    @Override protected void updatePage() {
        if (pageRevision != java.util.Objects.hash((previewQueue.isEmpty() ? missing : previewQueue), downloading, detecting)) refreshPage();
        updateRemoteSelections();
        sourceButtons.forEach(Runnable::run);
    }

    @Override public boolean shouldCloseOnEsc() { return !downloading && !detecting; }
    @Override public void onClose() { if (shouldCloseOnEsc()) handleSkip(); }
}
