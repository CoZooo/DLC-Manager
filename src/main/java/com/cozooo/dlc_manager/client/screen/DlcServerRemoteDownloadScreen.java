package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DlcServerRemoteDownloadScreen extends DlcRemotePageScreen {



    private static final String ALL_MARKER = "__ALL__";

    private final Screen parent;
    private final DlcServerEntry serverEntry;
    private final Set<String> selectedKeys = new HashSet<>(Set.of(ALL_MARKER));
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private volatile List<DlcServerRemoteItem> items = List.of();

    private Button downloadButton;
    private Button backButton;

    private volatile boolean loading = true;
    private volatile boolean downloading;
    private volatile DlcServerRemoteStatus status;
    private volatile String errorText;
    private float lastTotalProgress;

    public DlcServerRemoteDownloadScreen(Screen parent, DlcServerEntry serverEntry) {
        super(DlcUiPage.SERVER_REMOTE_DOWNLOAD, parent, DlcTextCompat.translatable("dlc_manager.remote.title"));
        this.parent = parent;
        this.serverEntry = serverEntry;
    }

    private void initControls() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        downloadButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.remote.download"), button -> handleDownload())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        downloadButton.active = false;
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.remote.skip"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(backButton);
        loadMissingList();
    }

    private void loadMissingList() {
        loading = true;
        errorText = null;
        downloadButton.active = false;
        CompletableFuture
            .supplyAsync(this::fetchMissing)
            .whenComplete((list, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (list == null) {
                    errorText = DlcTextCompat.translatable("dlc_manager.server.remote_missing_failed").getString();
                    return;
                }
                items = list;
                if (items.isEmpty()) {
                    Minecraft.getInstance().setScreen(parent);
                    return;
                }
                downloadButton.active = true;
            }));
    }

    private List<DlcServerRemoteItem> fetchMissing() {
        try {
            return DlcServerConnection.fetchRemoteMissingList(serverEntry, 8000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void handleDownload() {
        if (downloading || loading) {
            return;
        }
        downloading = true;
        errorText = null;
        downloadButton.active = false;
        CompletableFuture
            .supplyAsync(() -> startDownload())
            .whenComplete((started, throwable) -> {
                if (started == null) {
                    Minecraft.getInstance().execute(this::recoverDownload);
                    return;
                }
                startPolling();
            });
    }

    private DlcServerRemoteStatus startDownload() {
        try {
            List<String> keys = resolveSelectionKeys();
            return DlcServerConnection.startRemoteDownload(serverEntry, 8000, keys);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void startPolling() {
        new Thread(() -> {
            int failedPolls = 0;
            while (downloading) {
                DlcServerRemoteStatus next = fetchStatus();
                if (next != null) {
                    failedPolls = 0;
                    updateStatus(next);
                    if (next.finished) {
                        downloading = false;
                        boolean ok = isStatusOk(next);
                        Minecraft.getInstance().execute(() -> {
                            if (ok) {
                                DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                                    DlcTextCompat.translatable("dlc_manager.server.remote_missing_success"), DlcTextCompat.empty());
                            } else {
                                DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                                    DlcTextCompat.translatable("dlc_manager.server.remote_missing_failed"), DlcTextCompat.empty());
                            }
                            if (parent instanceof DlcServerManagerScreen manager) {
                                manager.refreshServerInfo();
                            } else if (parent instanceof DlcServerScreen mali) {
                                mali.refreshServerInfo();
                            }
                            Minecraft.getInstance().setScreen(parent);
                        });
                        break;
                    }
                }
                if (next == null && ++failedPolls >= 3) {
                    Minecraft.getInstance().execute(this::recoverDownload);
                    break;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }, "dlc-server-remote-poll").start();
    }

    private boolean isStatusOk(DlcServerRemoteStatus next) {
        if (next == null) {
            return false;
        }
        if (next.items != null) {
            for (DlcServerRemoteItem item : next.items) {
                if (item != null && item.done && !item.success) {
                    return false;
                }
            }
        }
        return next.success;
    }

    private DlcServerRemoteStatus fetchStatus() {
        try {
            return DlcServerConnection.fetchRemoteDownloadStatus(serverEntry, 3000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateStatus(DlcServerRemoteStatus next) {
        status = next;
        if (next == null || next.items == null) {
            return;
        }
        for (DlcServerRemoteItem item : next.items) {
            if (item == null || item.key == null) {
                continue;
            }
            float progress = 0f;
            if (item.totalBytes > 0) {
                progress = item.downloadedBytes / (float) item.totalBytes;
            } else if (item.done) {
                progress = 1f;
            }
            progressByKey.put(item.key, progress);
        }
    }

    private String resolveDisplayName(DlcServerRemoteItem item) {
        if (item == null) {
            return "";
        }
        if (item.displayNames != null && !item.displayNames.isEmpty()) {
            String lang = DlcDcConfigIO.normalizeLangKey(Minecraft.getInstance().options.languageCode);
            String name = lang == null ? null : item.displayNames.get(lang);
            if (name == null || name.isBlank()) {
                name = item.displayNames.get("en_us");
            }
            if (name == null || name.isBlank()) {
                name = item.displayNames.values().stream().findFirst().orElse(null);
            }
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (item.name != null && !item.name.isBlank()) {
            return item.name;
        }
        return item.key == null ? "" : item.key;
    }

    private float resolveTotalProgress(DlcServerRemoteStatus status) {
        if (status == null) {
            return 0f;
        }
        if (status.items != null && !status.items.isEmpty()) {
            float sum = 0f;
            int count = 0;
            for (DlcServerRemoteItem item : status.items) {
                if (item == null) {
                    continue;
                }
                float progress;
                if (item.totalBytes > 0) {
                    progress = item.downloadedBytes / (float) item.totalBytes;
                } else if (item.done) {
                    progress = 1f;
                } else {
                    progress = 0f;
                }
                sum += Math.max(0f, Math.min(1f, progress));
                count++;
            }
            if (count > 0) {
                float computed = sum / (float) count;
                float clamped = Math.max(0f, Math.min(1f, computed));
                lastTotalProgress = Math.max(lastTotalProgress, clamped);
                return lastTotalProgress;
            }
        }
        if (status.totalBytes > 0) {
            float computed = Math.max(0f, Math.min(1f, status.downloadedBytes / (float) status.totalBytes));
            lastTotalProgress = Math.max(lastTotalProgress, computed);
            return lastTotalProgress;
        }
        if (status.totalCount > 0) {
            float computed = Math.max(0f, Math.min(1f, status.completedCount / (float) status.totalCount));
            lastTotalProgress = Math.max(lastTotalProgress, computed);
            return lastTotalProgress;
        }
        return 0f;
    }

    private boolean isAllSelected() {
        return selectedKeys.contains(ALL_MARKER);
    }

    private void toggleSelectAll() {
        if (isAllSelected()) {
            selectedKeys.clear();
            return;
        }
        selectedKeys.clear();
        selectedKeys.add(ALL_MARKER);
    }

    private void toggleSelection(DlcServerRemoteItem item) {
        if (item == null || item.key == null || item.key.isBlank()) {
            return;
        }
        if (isAllSelected()) {
            selectedKeys.clear();
            for (DlcServerRemoteItem entry : items) {
                if (entry != null && entry.key != null && !entry.key.equals(item.key)) {
                    selectedKeys.add(entry.key);
                }
            }
            return;
        }
        if (selectedKeys.contains(item.key)) {
            selectedKeys.remove(item.key);
        } else {
            selectedKeys.add(item.key);
        }
        if (!items.isEmpty() && selectedKeys.size() == items.size()) {
            selectedKeys.clear();
            selectedKeys.add(ALL_MARKER);
        }
    }

    private List<String> resolveSelectionKeys() {
        if (isAllSelected()) {
            return List.of();
        }
        return new ArrayList<>(selectedKeys);
    }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash(items, downloading, loading);
        var list = remoteList(this::isAllSelected, () -> !downloading && !loading, this::toggleSelectAll);
        for (var item : items) {
            var line = remoteRow(list, resolveDisplayName(item), () -> isAllSelected() || selectedKeys.contains(item.key),
                () -> !downloading && !loading, () -> toggleSelection(item));
            remoteTip(line, resolveDisplayName(item), item.tips);
            if (downloading && progressByKey.containsKey(item.key)) remoteProgress(line, () -> progressByKey.getOrDefault(item.key, 0f));
        }
        var summary = remoteStatus();
        if (!loading && errorText != null && items.isEmpty()) action(summary, tr("dlc_manager.server.files_refresh"), this::loadMissingList);
        live(summary, () -> loading ? tr("dlc_manager.remote.detecting") : downloading ? tr("dlc_manager.remote.downloading_multi") : "", "remote-download-status");
        live(summary, () -> errorText, "page-error");
        if (loading) progress(summary, () -> -1);
        if (downloading) remoteProgress(node(summary, "div", "remote-total-progress"), () -> resolveTotalProgress(status));
        mount(footer, backButton);
        mount(footer, downloadButton).getElement().setAttribute("class", "button primary");
    }
    @Override protected void updatePage() {
        backButton.active = !downloading && !loading;
        if (pageRevision != java.util.Objects.hash(items, downloading, loading)) refreshPage();
        updateRemoteSelections();
    }

    private void recoverDownload() {
        downloading = false;
        errorText = tr("dlc_manager.server.remote_missing_failed");
        downloadButton.active = !items.isEmpty();
    }

    @Override public boolean shouldCloseOnEsc() { return !downloading && !loading; }
}
