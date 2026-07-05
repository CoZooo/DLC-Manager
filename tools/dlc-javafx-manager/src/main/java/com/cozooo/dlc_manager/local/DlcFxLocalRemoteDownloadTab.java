package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcSharedService;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.remote.DlcRemoteService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.remote.DlcFxResourceDownloadActionFlow;
import com.cozooo.dlc_manager.ui.remote.DlcFxRemoteDownloadShell;
import com.cozooo.dlc_manager.ui.remote.DlcFxResourceOperationModel;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcFxLocalRemoteDownloadTab {

    private final ObservableList<DlcRemoteEntry> allEntries = FXCollections.observableArrayList();
    private final DlcFxResourceOperationModel operationModel = new DlcFxResourceOperationModel();
    private final DlcFxResourceDownloadActionFlow<DlcRemoteEntry> actionFlow =
            new DlcFxResourceDownloadActionFlow<>(new LocalDownloadActions());
    private final AtomicLong batchProgressGeneration = new AtomicLong(0L);

    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label countLabel = new Label("");
    private final Label totalProgressLabel = new Label("");
    private DlcFxRemoteDownloadShell<DlcRemoteEntry> shell;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private boolean downloading;

    public DlcFxLocalRemoteDownloadTab(DlcFxPlatformProvider provider) {
        progressBar.setPrefWidth(220);
        progressBar.setMinWidth(220);
        progressBar.setMaxWidth(220);
        progressBar.setVisible(false);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.remote"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        shell = new DlcFxRemoteDownloadShell<>(
                new LocalRemoteDownloadAdapter(),
                statusLabel,
                countLabel,
                progressBar,
                totalProgressLabel,
                () -> {
                    if (gameDirSelected) {
                        refreshAsync();
                    }
                },
                actionFlow::downloadMissing,
                actionFlow::downloadSelected,
                () -> {
                    operationModel.requestBatchCancel();
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
                    updateActionButtons();
                },
                entry -> {
                    String key = progressKey(entry);
                    if (operationModel.isSingleActive(key)) {
                        return () -> cancelSingleDownload(key);
                    }
                    if (DlcRemoteService.isMissing(entry) && !operationModel.isBatchActive(key)) {
                        return () -> actionFlow.downloadOne(entry);
                    }
                    return null;
                }
        );
        shell.setAvailable(gameDirSelected);
        totalProgressLabel.setMinWidth(120);
        totalProgressLabel.setPrefWidth(120);
        totalProgressLabel.setMaxWidth(120);
        statusLabel.setMinWidth(240);
        statusLabel.setPrefWidth(240);
        statusLabel.setMaxWidth(240);

        root.setTop(shell.topBar());
        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);

        VBox centerBox = new VBox(shell.listFrame());
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        root.setBottom(shell.bottomBar());

        tab.setContent(root);
        updateActionButtons();
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        allEntries.clear();
        operationModel.reset();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.server.game_dir_changed"));
        if (shell != null) {
            shell.setAvailable(true);
            shell.setItems(allEntries);
            shell.clearSelection();
        }
        refreshAsync();
    }

    public void onTabSelected() {
        if (!gameDirSelected || loadedForCurrentGameDir) {
            return;
        }
        refreshAsync();
    }

    private void refreshAsync() {
        if (!gameDirSelected) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            updateActionButtons();
            return;
        }
        loadedForCurrentGameDir = true;
        if (shell != null) {
            shell.setBusy(true);
        }
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.server.loading_status"));
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRemoteEntry> loaded = DlcRemoteLoader.load(dlcRoot);
                Platform.runLater(() -> {
                    allEntries.setAll(loaded);
                    rebuildRows();
                    int missing = DlcRemoteService.countMissing(loaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.server.count", loaded.size(), missing));
                    statusLabel.setText(missing == 0 ? DlcFxI18n.tr("dlc_manager.status.no_missing") : DlcFxI18n.tr("dlc_manager.server.has_missing"));
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    updateActionButtons();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    updateActionButtons();
                });
            }
        }, "dlc-remote-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private String selectionId(DlcRemoteEntry entry) {
        if (entry == null) {
            return "";
        }
        String key = entry.getUniqueKey();
        if (key != null && !key.isBlank()) {
            return key;
        }
        String name = entry.getFileNameHint();
        return name == null ? String.valueOf(System.identityHashCode(entry)) : name;
    }

    private void updateActionButtons() {
        boolean busy = downloading || progressBar.isVisible();
        if (shell != null) {
            shell.setAvailable(gameDirSelected);
            shell.setBusy(busy);
            shell.setCancelVisible(downloading);
            shell.refreshRows();
        }
    }

    private void downloadAll(List<DlcRemoteEntry> toDownload) {
        if (!requireGameDir()) {
            return;
        }
        downloading = true;
        long batchToken = batchProgressGeneration.incrementAndGet();
        updateActionButtons();
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(0);
        totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.server.downloading_total", 0, toDownload.size()));
        initBatchProgressMap(toDownload);
        Thread thread = new Thread(() -> {
            DlcRemoteService.BatchResult result;
            try {
                result = DlcRemoteService.downloadAll(toDownload, new DlcRemoteService.BatchProgressListener() {
                    @Override
                    public void onEntryStart(DlcRemoteEntry entry, int index, int total) {
                        Platform.runLater(() -> {
                            if (isCurrentBatch(batchToken)) {
                                markDownloading(entry, 0f);
                            }
                        });
                    }

                    @Override
                    public void onEntryAttemptStart(DlcRemoteEntry entry, int attemptId, int index, int total) {
                        Platform.runLater(() -> {
                            if (isCurrentBatch(batchToken)) {
                                markDownloading(entry, 0f);
                            }
                        });
                    }

                    @Override
                    public void onEntryProgress(DlcRemoteEntry entry, long downloadedBytes, long totalBytes) {
                        if (totalBytes > 0) {
                            Platform.runLater(() -> {
                                if (isCurrentBatch(batchToken)) {
                                    markDownloading(entry, Math.min(1f, downloadedBytes / (float) totalBytes));
                                    updateBatchProgress(toDownload.size());
                                }
                            });
                        }
                    }

                    @Override
                    public void onEntrySuccess(DlcRemoteEntry entry, String fileName, int completed, int total) {
                        Platform.runLater(() -> {
                            if (isCurrentBatch(batchToken)) {
                                markDownloading(entry, 1f);
                                updateBatchProgress(total);
                                totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.server.downloading_total", completed, total));
                            }
                        });
                    }

                    @Override
                    public void onEntryFailure(DlcRemoteEntry entry, Throwable error, int completed, int total) {
                        Platform.runLater(() -> {
                            if (isCurrentBatch(batchToken)) {
                                markDownloading(entry, 1f);
                                updateBatchProgress(total);
                                totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.server.downloading_total", completed, total));
                            }
                        });
                    }
                }, operationModel::isBatchCancelRequested);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = new DlcRemoteService.BatchResult(toDownload.size(), 0,
                    List.of(new DlcRemoteService.EntryError(null, ex)));
            }
            DlcRemoteService.BatchResult finalResult = result;
            batchProgressGeneration.incrementAndGet();
            Platform.runLater(() -> {
                boolean canceled = operationModel.isBatchCancelRequested();
                downloading = false;
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                totalProgressLabel.setText("");
                clearBatchProgress();
                statusLabel.setText(canceled
                    ? DlcFxI18n.tr("dlc_manager.status.download_canceled")
                    : DlcFxI18n.tr("dlc_manager.status.completed", finalResult.success, finalResult.total));
                if (!finalResult.errors.isEmpty()) {
                    statusLabel.setText(statusLabel.getText() + DlcFxI18n.tr("dlc_manager.status.failed_count_suffix", finalResult.errors.size()));
                }
                progressBar.setProgress(0);
                updateActionButtons();
                if (!canceled) {
                    refreshMissingStatusAfterBatch();
                }
            });
        }, "dlc-remote-download");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isCurrentBatch(long batchToken) {
        return batchProgressGeneration.get() == batchToken;
    }

    private void refreshMissingStatusAfterBatch() {
        Thread thread = new Thread(() -> {
            try {
                List<DlcRemoteEntry> reloaded = DlcRemoteLoader.load(DlcConfig.getDlcRoot());
                Platform.runLater(() -> {
                    allEntries.setAll(reloaded);
                    rebuildRows();
                    int missing = DlcRemoteService.countMissing(reloaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.server.count", reloaded.size(), missing));
                    statusLabel.setText(missing == 0 ? DlcFxI18n.tr("dlc_manager.status.no_missing") : DlcFxI18n.tr("dlc_manager.server.has_missing"));
                });
            } catch (Throwable t) {
                Platform.runLater(() -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t))));
            }
        }, "dlc-remote-refresh-after-batch");
        thread.setDaemon(true);
        thread.start();
    }

    private void initBatchProgressMap(List<DlcRemoteEntry> targets) {
        clearBatchProgress();
        if (targets == null) {
            return;
        }
        operationModel.beginBatch(targets.stream().filter(java.util.Objects::nonNull).map(this::selectionId).toList());
        syncVisibleItemCells();
    }

    private void clearBatchProgress() {
        operationModel.finishBatch();
    }

    private void updateBatchProgress(int total) {
        if (total <= 0) {
            progressBar.setProgress(-1);
            return;
        }
        progressBar.setProgress(Math.max(0d, Math.min(1d, operationModel.batchProgress())));
    }

    private void markDownloading(DlcRemoteEntry entry, float progress) {
        if (entry == null) {
            return;
        }
        operationModel.setProgress(selectionId(entry), progress);
        syncVisibleItemCells();
    }

    private void syncVisibleItemCells() {
        if (shell != null) {
            shell.refreshRows();
        }
    }

    private boolean requireGameDir() {
        if (!gameDirSelected || DlcConfig.getDlcRoot() == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            updateActionButtons();
            return false;
        }
        return true;
    }

    private void rebuildRows() {
        if (shell != null) {
            shell.setItems(allEntries);
        }
        updateActionButtons();
    }

    private boolean isVisibleRemoteEntry(DlcRemoteEntry entry) {
        return entry != null && (!entry.isHidden() || entry.getType() == DlcType.DLC_PACK);
    }

    private String resolveGroupLabel(DlcRemoteEntry entry) {
        try {
            Path config = entry.getConfigPath();
            Path remoteRoot = entry.getDlcRoot() == null ? null : entry.getDlcRoot().resolve("remote");
            String relative = null;
            if (config != null && remoteRoot != null && config.startsWith(remoteRoot)) {
                relative = remoteRoot.relativize(config).toString().replace('\\', '/');
            }
            if (relative == null) {
                Path baseDir = entry.getBaseDir();
                if (baseDir != null && remoteRoot != null && baseDir.startsWith(remoteRoot)) {
                    relative = remoteRoot.relativize(baseDir).toString().replace('\\', '/');
                    return relative.isBlank() ? "__root__" : relative;
                }
            }
            if (relative == null || relative.isBlank()) {
                return "__root__";
            }
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash <= 0) {
                return "__root__";
            }
            return relative.substring(0, lastSlash);
        } catch (Exception ignored) {
            return "__root__";
        }
    }

    private static String safeName(DlcRemoteEntry entry) {
        String name = entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry.getFileNameHint();
        }
        if (name == null || name.isBlank()) {
            name = entry.getDlcIdentifier();
        }
        return name == null ? DlcFxI18n.tr("dlc_manager.status.unnamed") : name;
    }

    private String buildTooltip(DlcRemoteEntry entry) {
        if (entry == null) {
            return "";
        }
        return resolveRemoteTip(entry);
    }

    private static String resolveRemoteTip(DlcRemoteEntry entry) {
        if (entry == null || entry.getTips().isEmpty()) {
            return "";
        }
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = entry.getTips().get(lang);
        if (value == null || value.isBlank()) {
            value = entry.getTips().get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = entry.getTips().values().stream().findFirst().orElse("");
        }
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private static String progressKey(DlcRemoteEntry entry) {
        return entry == null ? "" : entry.getUniqueKey();
    }

    private void downloadSingle(DlcRemoteEntry entry) {
        if (!requireGameDir() || entry == null || !DlcRemoteService.isMissing(entry)) {
            return;
        }
        String key = progressKey(entry);
        operationModel.beginSingle(key);
        syncVisibleItemCells();
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        totalProgressLabel.setText("");
        Thread thread = new Thread(() -> {
            try {
                DlcRemoteService.downloadOne(entry, (downloadedBytes, totalBytes) -> {
                    if (totalBytes > 0) {
                        float progress = Math.min(1f, downloadedBytes / (float) totalBytes);
                        Platform.runLater(() -> {
                            operationModel.setProgress(key, progress);
                            syncVisibleItemCells();
                        });
                    }
                }, () -> operationModel.isSingleCancelRequested(key));
                Platform.runLater(() -> {
                    operationModel.finishSingle(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.server.has_missing"));
                    syncVisibleItemCells();
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    operationModel.finishSingle(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    syncVisibleItemCells();
                });
            } catch (DlcSharedService.DownloadCanceledException ex) {
                Platform.runLater(() -> {
                    operationModel.finishSingle(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_canceled"));
                    syncVisibleItemCells();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    operationModel.finishSingle(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(ex)));
                    syncVisibleItemCells();
                });
            }
        }, "dlc-remote-single-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void cancelSingleDownload(String key) {
        operationModel.requestSingleCancel(key);
        syncVisibleItemCells();
    }

    private final class LocalDownloadActions implements DlcFxResourceDownloadActionFlow.Actions<DlcRemoteEntry> {
        @Override
        public boolean requireAvailable() {
            return requireGameDir();
        }

        @Override
        public List<DlcRemoteEntry> missingItems() {
            return DlcRemoteService.collectMissing(allEntries);
        }

        @Override
        public List<DlcRemoteEntry> selectedItems() {
            return shell == null ? List.of() : shell.selectedItems();
        }

        @Override
        public void startDownload(List<DlcRemoteEntry> items) {
            if (items == null || items.size() != 1) {
                downloadAll(items);
                return;
            }
            DlcRemoteEntry entry = items.getFirst();
            if (entry != null && DlcRemoteService.isMissing(entry)) {
                downloadSingle(entry);
            }
        }

        @Override
        public void showNoMissingDownloads() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.no_missing_downloads"));
        }

        @Override
        public void showSelectListItem() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
        }
    }

    private final class LocalRemoteDownloadAdapter implements DlcFxRemoteDownloadShell.Adapter<DlcRemoteEntry> {
        @Override
        public String id(DlcRemoteEntry item) {
            return selectionId(item);
        }

        @Override
        public String title(DlcRemoteEntry item) {
            return safeName(item);
        }

        @Override
        public DlcType type(DlcRemoteEntry item) {
            return item == null ? null : item.getType();
        }

        @Override
        public String folder(DlcRemoteEntry item) {
            return resolveGroupLabel(item);
        }

        @Override
        public boolean missing(DlcRemoteEntry item) {
            return DlcRemoteService.isMissing(item);
        }

        @Override
        public boolean hidden(DlcRemoteEntry item) {
            return !isVisibleRemoteEntry(item);
        }

        @Override
        public String tooltip(DlcRemoteEntry item) {
            return buildTooltip(item);
        }

        @Override
        public boolean itemActionVisible(DlcRemoteEntry item) {
            if (!DlcRemoteService.isMissing(item)) {
                return false;
            }
            return !operationModel.isBatchActive(selectionId(item));
        }

        @Override
        public boolean itemActionDisabled(DlcRemoteEntry item) {
            return false;
        }

        @Override
        public boolean itemActionDanger(DlcRemoteEntry item) {
            return operationModel.isSingleActive(selectionId(item));
        }

        @Override
        public String itemActionText(DlcRemoteEntry item) {
            return operationModel.isSingleActive(selectionId(item))
                    ? DlcFxI18n.tr("dlc_manager.common.cancel")
                    : DlcFxI18n.tr("dlc_manager.server.download");
        }

        @Override
        public double progress(DlcRemoteEntry item) {
            return operationModel.progress(selectionId(item));
        }

        @Override
        public String progressText(DlcRemoteEntry item) {
            float progress = operationModel.progress(selectionId(item));
            return Float.isNaN(progress) ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%";
        }
    }

}
