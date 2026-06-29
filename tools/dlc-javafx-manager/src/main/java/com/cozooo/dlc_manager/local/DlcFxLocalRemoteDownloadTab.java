package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcSharedService;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.remote.DlcRemoteService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxStyle;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcFxLocalRemoteDownloadTab {

    private static final double LIST_ROW_WIDTH_RESERVED = 34;
    private static final double LIST_ITEM_CONTENT_MIN_WIDTH = 620;
    private static final double LIST_GROUP_CONTENT_MIN_WIDTH = 260;
    private static final double LIST_PROGRESS_TEXT_WIDTH = 40;
    private static final double LIST_PROGRESS_WIDTH = 140;
    private static final double LIST_DOWNLOAD_WIDTH = 82;
    private static final double LIST_STATUS_WIDTH = 54;

    private final ObservableList<DlcRemoteEntry> allEntries = FXCollections.observableArrayList();
    private final ObservableList<RemoteRow> visibleRows = FXCollections.observableArrayList();
    private final Set<String> selectedEntryIds = new LinkedHashSet<>();
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    private final Set<String> animatingGroups = new LinkedHashSet<>();
    private final Set<String> activeSingleDownloads = new LinkedHashSet<>();
    private final Set<String> activeBatchDownloads = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<String, AtomicBoolean> singleCancelRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean batchCancelRequested = new AtomicBoolean(false);
    private final AtomicLong batchProgressGeneration = new AtomicLong(0L);
    private final Set<RemoteEntryCell> activeItemCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<RemoteEntryCell> activeGroupCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<String, Float> progressByKey = new java.util.concurrent.ConcurrentHashMap<>();

    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.refresh_download_list"));
    private final Button downloadMissingBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.download_missing"));
    private final Button downloadSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.common.download_selected"));
    private final Button cancelBatchBtn = new Button(DlcFxI18n.tr("dlc_manager.common.cancel"));
    private final Label countLabel = new Label("");
    private final Label totalProgressLabel = new Label("");
    private final ComboBox<DlcTypeOption> typeFilter = new ComboBox<>();
    private ListView<RemoteRow> listView;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private boolean downloading;
    private DlcType currentTypeFilter;

    public DlcFxLocalRemoteDownloadTab(DlcFxPlatformProvider provider) {
        progressBar.setPrefWidth(220);
        progressBar.setMinWidth(220);
        progressBar.setMaxWidth(220);
        progressBar.setVisible(false);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_remote"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        refreshBtn.setDisable(true);
        refreshBtn.setOnAction(e -> {
            if (gameDirSelected) {
                refreshAsync();
            }
        });
        downloadMissingBtn.setDisable(true);
        downloadMissingBtn.setOnAction(e -> downloadMissingAsync());
        downloadSelectedBtn.setDisable(true);
        downloadSelectedBtn.setOnAction(e -> downloadSelectedAsync());
        cancelBatchBtn.setVisible(false);
        cancelBatchBtn.setManaged(false);
        cancelBatchBtn.setOnAction(e -> {
            batchCancelRequested.set(true);
            cancelBatchBtn.setDisable(true);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
        });
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(downloadMissingBtn);
        DlcFxStyle.add(downloadSelectedBtn, "dlc-toggle-off");
        DlcFxStyle.danger(cancelBatchBtn);
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(totalProgressLabel);
        totalProgressLabel.setMinWidth(120);
        totalProgressLabel.setPrefWidth(120);
        totalProgressLabel.setMaxWidth(120);
        statusLabel.setMinWidth(240);
        statusLabel.setPrefWidth(240);
        statusLabel.setMaxWidth(240);

        typeFilter.setPromptText(DlcFxI18n.tr("dlc_manager.local.all_types"));
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcTypeOption option) { return typeText(option); }
            @Override public DlcTypeOption fromString(String s) { return DlcTypeOption.all(); }
        });
        typeFilter.setButtonCell(new DlcTypeButtonCell(typeFilter));
        typeFilter.setCellFactory(param -> new DlcTypePopupCell());
        typeFilter.valueProperty().addListener((obs, o, n) -> {
            currentTypeFilter = typeOf(n);
            selectedEntryIds.clear();
            rebuildRows();
        });
        refreshTypeFilterItems();
        Label typeLabel = new Label(DlcFxI18n.tr("dlc_manager.local.type"));
        DlcFxStyle.muted(typeLabel);

        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topBox = new HBox(8, refreshBtn, typeLabel, typeFilter, countLabel, topSpacer, selectionActions);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        listView = new ListView<>(visibleRows);
        DlcFxStyle.add(listView, "dlc-dlc-list");
        listView.setCellFactory(param -> new RemoteEntryCell());
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        HBox listBox = DlcFxListViewSupport.wrap(listView);
        VBox.setVgrow(listBox, Priority.ALWAYS);

        VBox centerBox = new VBox(10, listBox);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        selectAllBtn.setOnAction(e -> toggleAll(true));
        selectNoneBtn.setOnAction(e -> toggleAll(false));
        invertBtn.setOnAction(e -> invertSelection());

        HBox downloadActions = new HBox(8, downloadMissingBtn, downloadSelectedBtn);
        downloadActions.setAlignment(Pos.CENTER_LEFT);
        HBox rightActions = new HBox(8, cancelBatchBtn, progressBar, totalProgressLabel, statusLabel);
        rightActions.setAlignment(Pos.CENTER_RIGHT);

        BorderPane actionPanel = new BorderPane();
        actionPanel.setLeft(downloadActions);
        actionPanel.setRight(rightActions);
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        updateActionButtons();
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        allEntries.clear();
        visibleRows.clear();
        selectedEntryIds.clear();
        collapsedGroups.clear();
        animatingGroups.clear();
        progressByKey.clear();
        activeBatchDownloads.clear();
        activeSingleDownloads.clear();
        singleCancelRequests.clear();
        batchCancelRequested.set(false);
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.game_dir_changed"));
        refreshBtn.setDisable(false);
        refreshTypeFilterItems();
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
            refreshBtn.setDisable(true);
            updateActionButtons();
            return;
        }
        loadedForCurrentGameDir = true;
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.loading_status"));
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRemoteEntry> loaded = DlcRemoteLoader.load(dlcRoot);
                Platform.runLater(() -> {
                    allEntries.setAll(loaded);
                    refreshTypeFilterItems();
                    rebuildRows();
                    int missing = DlcRemoteService.countMissing(loaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.remote.count", loaded.size(), missing));
                    statusLabel.setText(missing == 0 ? DlcFxI18n.tr("dlc_manager.status.no_missing") : DlcFxI18n.tr("dlc_manager.remote.has_missing"));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    updateActionButtons();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    updateActionButtons();
                });
            }
        }, "dlc-remote-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void toggleAll(boolean selected) {
        for (RemoteRow row : visibleRows) {
            if (row.type == RemoteRowType.ITEM && row.entry != null && DlcRemoteService.isMissing(row.entry)) {
                setSelected(row.entry, selected);
            }
        }
        syncVisibleItemCells();
        updateActionButtons();
    }

    private void invertSelection() {
        for (RemoteRow row : visibleRows) {
            if (row.type == RemoteRowType.ITEM && row.entry != null && DlcRemoteService.isMissing(row.entry)) {
                String id = selectionId(row.entry);
                if (selectedEntryIds.contains(id)) {
                    selectedEntryIds.remove(id);
                } else {
                    selectedEntryIds.add(id);
                }
            }
        }
        syncVisibleItemCells();
        updateActionButtons();
    }

    private void setSelected(DlcRemoteEntry entry, boolean selected) {
        String id = selectionId(entry);
        if (selected) {
            selectedEntryIds.add(id);
        } else {
            selectedEntryIds.remove(id);
        }
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

    private List<DlcRemoteEntry> selectedMissingEntries() {
        List<DlcRemoteEntry> selected = new ArrayList<>();
        for (RemoteRow row : visibleRows) {
            if (row.type == RemoteRowType.ITEM
                && row.entry != null
                && DlcRemoteService.isMissing(row.entry)
                && isEntrySelected(row.entry)) {
                selected.add(row.entry);
            }
        }
        return selected;
    }

    private void updateActionButtons() {
        boolean busy = downloading || progressBar.isVisible();
        downloadMissingBtn.setDisable(busy || !gameDirSelected);
        downloadSelectedBtn.setDisable(busy || !gameDirSelected || selectedMissingEntries().isEmpty());
        cancelBatchBtn.setVisible(downloading);
        cancelBatchBtn.setManaged(downloading);
        cancelBatchBtn.setDisable(!downloading || batchCancelRequested.get());
    }

    private void syncVisibleItemCells() {
        for (RemoteEntryCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.syncItemState();
            }
        }
    }

    private void downloadMissingAsync() {
        if (!requireGameDir()) {
            return;
        }
        List<DlcRemoteEntry> missing = DlcRemoteService.collectMissing(allEntries);
        if (missing.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.no_missing_downloads"));
            return;
        }
        downloadAll(missing);
    }

    private void downloadSelectedAsync() {
        if (!requireGameDir()) {
            return;
        }
        List<DlcRemoteEntry> selected = selectedMissingEntries();
        if (selected.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
            return;
        }
        downloadAll(selected);
    }

    private void downloadAll(List<DlcRemoteEntry> toDownload) {
        if (!requireGameDir()) {
            return;
        }
        downloading = true;
        batchCancelRequested.set(false);
        long batchToken = batchProgressGeneration.incrementAndGet();
        refreshBtn.setDisable(true);
        updateActionButtons();
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(0);
        totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.remote.downloading_total", 0, toDownload.size()));
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
                                totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.remote.downloading_total", completed, total));
                            }
                        });
                    }

                    @Override
                    public void onEntryFailure(DlcRemoteEntry entry, Throwable error, int completed, int total) {
                        Platform.runLater(() -> {
                            if (isCurrentBatch(batchToken)) {
                                markDownloading(entry, 1f);
                                updateBatchProgress(total);
                                totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.remote.downloading_total", completed, total));
                            }
                        });
                    }
                }, () -> batchCancelRequested.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = new DlcRemoteService.BatchResult(toDownload.size(), 0,
                    List.of(new DlcRemoteService.EntryError(null, ex)));
            }
            DlcRemoteService.BatchResult finalResult = result;
            batchProgressGeneration.incrementAndGet();
            Platform.runLater(() -> {
                boolean canceled = batchCancelRequested.get();
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
                batchCancelRequested.set(false);
                refreshBtn.setDisable(false);
                syncVisibleItemCells();
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
                    refreshTypeFilterItems();
                    rebuildRows();
                    int missing = DlcRemoteService.countMissing(reloaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.remote.count", reloaded.size(), missing));
                    statusLabel.setText(missing == 0 ? DlcFxI18n.tr("dlc_manager.status.no_missing") : DlcFxI18n.tr("dlc_manager.remote.has_missing"));
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
        for (DlcRemoteEntry entry : targets) {
            if (entry != null) {
                String key = selectionId(entry);
                activeBatchDownloads.add(key);
                progressByKey.put(key, 0f);
            }
        }
        syncVisibleItemCells();
    }

    private void clearBatchProgress() {
        for (String key : new ArrayList<>(activeBatchDownloads)) {
            if (!activeSingleDownloads.contains(key)) {
                progressByKey.remove(key);
            }
        }
        activeBatchDownloads.clear();
    }

    private void updateBatchProgress(int total) {
        if (total <= 0) {
            progressBar.setProgress(-1);
            return;
        }
        float sum = 0f;
        for (String key : activeBatchDownloads) {
            Float value = progressByKey.get(key);
            if (value != null) {
                sum += Math.max(0f, Math.min(1f, value));
            }
        }
        progressBar.setProgress(Math.max(0d, Math.min(1d, sum / total)));
    }

    private void markDownloading(DlcRemoteEntry entry, float progress) {
        if (entry == null) {
            return;
        }
        progressByKey.put(selectionId(entry), progress);
        syncVisibleItemCells();
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
        List<RemoteRow> rows = new ArrayList<>();
        List<DlcRemoteEntry> filtered = allEntries.stream()
            .filter(this::isVisibleRemoteEntry)
            .filter(entry -> currentTypeFilter == null || entry.getType() == currentTypeFilter)
            .toList();
        Map<String, List<DlcRemoteEntry>> byFolder = groupByRelativeFolder(filtered);
        boolean hasSubDir = byFolder.keySet().stream().anyMatch(key -> !"__root__".equals(key));
        for (Map.Entry<String, List<DlcRemoteEntry>> entry : byFolder.entrySet()) {
            String groupKey = folderKey(entry.getKey());
            if ("__root__".equals(entry.getKey()) && !hasSubDir) {
                for (DlcRemoteEntry remoteEntry : entry.getValue()) {
                    rows.add(RemoteRow.item(remoteEntry, groupKey));
                }
                continue;
            }
            rows.add(RemoteRow.group(groupKey, resolvePathLabel(entry.getKey()), entry.getKey(), collapsedGroups.contains(groupKey)));
            if (collapsedGroups.contains(groupKey)) {
                continue;
            }
            for (DlcRemoteEntry remoteEntry : entry.getValue()) {
                rows.add(RemoteRow.item(remoteEntry, groupKey));
            }
        }
        visibleRows.setAll(rows);
        if (listView != null) {
            listView.refresh();
        }
        updateActionButtons();
    }

    private boolean setFolderCollapsed(RemoteRow row, boolean collapsed) {
        if (row == null || row.groupKey == null || row.groupKey.isBlank()) {
            return false;
        }
        if (animatingGroups.contains(row.groupKey)) {
            return false;
        }
        int headerIndex = visibleRows.indexOf(row);
        if (headerIndex < 0 || row.collapsed == collapsed) {
            return false;
        }
        if (collapsed) {
            animateFolderCollapse(row, headerIndex);
        } else {
            animateFolderExpand(row, headerIndex);
        }
        if (listView != null) {
            listView.getSelectionModel().clearSelection();
        }
        return true;
    }

    private void animateFolderCollapse(RemoteRow row, int headerIndex) {
        animatingGroups.add(row.groupKey);
        collapsedGroups.add(row.groupKey);
        row.collapsed = true;
        List<RemoteRow> targets = folderItemRowsAfter(headerIndex);
        if (targets.isEmpty()) {
            finishFolderAnimation(row.groupKey);
            return;
        }
        for (RemoteRow target : targets) {
            target.expanding = false;
            target.collapsing = true;
        }
        startVisibleFolderAnimations(row.groupKey);
        PauseTransition removeAfterAnimation = new PauseTransition(Duration.millis(260));
        removeAfterAnimation.setOnFinished(event -> {
            visibleRows.removeAll(targets);
            for (RemoteRow target : targets) {
                if (target.collapsing && !target.expanding) {
                    target.collapsing = false;
                }
            }
            finishFolderAnimation(row.groupKey);
        });
        removeAfterAnimation.play();
    }

    private void animateFolderExpand(RemoteRow row, int headerIndex) {
        animatingGroups.add(row.groupKey);
        collapsedGroups.remove(row.groupKey);
        row.collapsed = false;
        List<RemoteRow> rows = itemRowsForFolder(row.folderRaw);
        if (rows.isEmpty()) {
            finishFolderAnimation(row.groupKey);
            return;
        }
        for (RemoteRow item : rows) {
            item.expanding = true;
            item.collapsing = false;
        }
        visibleRows.addAll(headerIndex + 1, rows);
        Platform.runLater(() -> startVisibleFolderAnimations(row.groupKey));
        PauseTransition finishExpansion = new PauseTransition(Duration.millis(260));
        finishExpansion.setOnFinished(event -> {
            for (RemoteRow item : rows) {
                if (item.expanding && !item.collapsing) {
                    item.expanding = false;
                }
            }
            finishFolderAnimation(row.groupKey);
        });
        finishExpansion.play();
    }

    private void finishFolderAnimation(String folderKey) {
        if (folderKey != null) {
            animatingGroups.remove(folderKey);
        }
        syncVisibleGroupHeaders(folderKey);
        syncVisibleItemCells();
    }

    private void startVisibleFolderAnimations(String folderKey) {
        for (RemoteEntryCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.startAnimationForFolder(folderKey);
            }
        }
    }

    private void syncVisibleGroupHeaders(String folderKey) {
        for (RemoteEntryCell cell : new ArrayList<>(activeGroupCells)) {
            if (cell != null) {
                cell.syncGroupState(folderKey);
            }
        }
    }

    private boolean isFolderAnimating(String folderKey) {
        return folderKey != null && animatingGroups.contains(folderKey);
    }

    private List<RemoteRow> folderItemRowsAfter(int headerIndex) {
        List<RemoteRow> out = new ArrayList<>();
        for (int i = headerIndex + 1; i < visibleRows.size(); i++) {
            RemoteRow row = visibleRows.get(i);
            if (row.type != RemoteRowType.ITEM) {
                break;
            }
            out.add(row);
        }
        return out;
    }

    private List<RemoteRow> itemRowsForFolder(String rawFolder) {
        String normalized = rawFolder == null ? "__root__" : rawFolder;
        List<DlcRemoteEntry> filtered = allEntries.stream()
            .filter(this::isVisibleRemoteEntry)
            .filter(entry -> currentTypeFilter == null || entry.getType() == currentTypeFilter)
            .toList();
        List<DlcRemoteEntry> folderEntries = groupByRelativeFolder(filtered).get(normalized);
        if (folderEntries == null || folderEntries.isEmpty()) {
            return List.of();
        }
        List<RemoteRow> out = new ArrayList<>();
        String groupKey = folderKey(normalized);
        for (DlcRemoteEntry entry : folderEntries) {
            out.add(RemoteRow.item(entry, groupKey));
        }
        return out;
    }

    private boolean isVisibleRemoteEntry(DlcRemoteEntry entry) {
        return entry != null && (!entry.isHidden() || entry.getType() == DlcType.DLC_PACK);
    }

    private Map<String, List<DlcRemoteEntry>> groupByRelativeFolder(List<DlcRemoteEntry> entries) {
        Map<String, List<DlcRemoteEntry>> grouped = new java.util.TreeMap<>();
        for (DlcRemoteEntry entry : entries) {
            grouped.computeIfAbsent(resolveGroupLabel(entry), key -> new ArrayList<>()).add(entry);
        }
        return grouped;
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

    private String resolvePathLabel(String raw) {
        if ("__root__".equals(raw)) {
            return DlcFxI18n.tr("dlc_manager.group.root");
        }
        return raw;
    }

    private String folderKey(String raw) {
        return raw == null ? "__root__" : raw;
    }

    private void refreshTypeFilterItems() {
        DlcType selected = currentTypeFilter;
        typeFilter.getItems().clear();
        typeFilter.getItems().add(DlcTypeOption.all());
        for (DlcType type : visibleRemoteTypes()) {
            typeFilter.getItems().add(DlcTypeOption.of(type));
        }
        boolean keepSelected = selected != null && visibleRemoteTypes().contains(selected);
        typeFilter.setValue(keepSelected ? DlcTypeOption.of(selected) : DlcTypeOption.all());
    }

    private Set<DlcType> visibleRemoteTypes() {
        Set<DlcType> types = new LinkedHashSet<>();
        for (DlcType type : DlcType.values()) {
            if (hasVisibleEntryOfType(type) || allEntries.isEmpty()) {
                types.add(type);
            }
        }
        return types;
    }

    private boolean hasVisibleEntryOfType(DlcType type) {
        for (DlcRemoteEntry entry : allEntries) {
            if (isVisibleRemoteEntry(entry) && entry.getType() == type) {
                return true;
            }
        }
        return false;
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

    private static String buildTooltip(DlcRemoteEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder tip = new StringBuilder();
        String name = safeName(entry);
        if (name != null && !name.isBlank()) {
            tip.append(name);
        }
        Path target = entry.getTargetPath();
        if (target != null) {
            if (tip.length() > 0) {
                tip.append('\n');
            }
            tip.append(target);
        }
        String url = entry.getDirectUrl();
        if (url != null && !url.isBlank()) {
            if (tip.length() > 0) {
                tip.append('\n');
            }
            tip.append(url);
        }
        return tip.toString();
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private static String progressKey(DlcRemoteEntry entry) {
        return entry == null ? "" : entry.getUniqueKey();
    }

    private static ColumnConstraints column(double min, double pref, Priority hgrow) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(min);
        constraints.setPrefWidth(pref);
        constraints.setHgrow(hgrow);
        return constraints;
    }

    private final class RemoteEntryCell extends ListCell<RemoteRow> {
        private final CheckBox selectBox = new CheckBox();
        private final Label nameLabel = new Label();
        private final Label statusTag = new Label();
        private final Label rowProgressLabel = new Label();
        private final Label groupLabel = new Label();
        private final Button downloadButton = new Button(DlcFxI18n.tr("dlc_manager.remote.download"));
        private final ProgressBar rowProgress = new ProgressBar(0);
        private final Region trailingSpacer = new Region();
        private final Region progressSpacer = new Region();
        private final HBox progressPane = new HBox(6, rowProgressLabel, rowProgress);
        private final GridPane itemGrid = new GridPane();
        private final HBox itemRow = new HBox(0, itemGrid);
        private final Button collapseButton = new Button();
        private final SVGPath collapseIcon = new SVGPath();
        private final StackPane collapseIconBox = new StackPane(collapseIcon);
        private final BorderPane groupRow = new BorderPane();
        private final StackPane itemPane = new StackPane(itemRow);
        private final Rectangle clip = new Rectangle();
        private Timeline rowTimeline;
        private RotateTransition collapseTransition;
        private RemoteRow animatedRow;
        private Boolean animatedExpansion;

        RemoteEntryCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            statusTag.setStyle("-fx-text-fill: white;");
            DlcFxStyle.add(groupLabel, "dlc-dlc-group-header");
            DlcFxStyle.add(itemRow, "dlc-dlc-row");
            DlcFxStyle.add(groupRow, "dlc-dlc-path-row");
            DlcFxStyle.add(collapseButton, "dlc-folder-collapse-button");
            DlcFxStyle.add(collapseIconBox, "dlc-folder-collapse-icon-box");
            DlcFxStyle.add(collapseIcon, "dlc-folder-collapse-icon");
            DlcFxStyle.add(downloadButton, "dlc-neutral-button");
            DlcFxStyle.muted(rowProgressLabel);
            collapseIcon.setContent("M 3 10 L 8 5 L 13 10");
            collapseIcon.setFill(Color.TRANSPARENT);
            collapseIcon.setStroke(Color.web("#475467"));
            collapseIcon.setStrokeWidth(1.8);
            collapseIcon.setStrokeLineCap(StrokeLineCap.ROUND);
            collapseIcon.setStrokeLineJoin(StrokeLineJoin.ROUND);
            downloadButton.setMinWidth(LIST_DOWNLOAD_WIDTH);
            downloadButton.setPrefWidth(LIST_DOWNLOAD_WIDTH);
            downloadButton.setMaxWidth(LIST_DOWNLOAD_WIDTH);
            rowProgressLabel.setMinWidth(LIST_PROGRESS_TEXT_WIDTH);
            rowProgressLabel.setPrefWidth(LIST_PROGRESS_TEXT_WIDTH);
            rowProgressLabel.setMaxWidth(LIST_PROGRESS_TEXT_WIDTH);
            rowProgressLabel.setAlignment(Pos.CENTER_RIGHT);
            trailingSpacer.setMinWidth(0);
            trailingSpacer.setPrefWidth(0);
            trailingSpacer.setMaxWidth(Double.MAX_VALUE);
            rowProgress.setPrefWidth(LIST_PROGRESS_WIDTH);
            rowProgress.setMinWidth(LIST_PROGRESS_WIDTH - LIST_PROGRESS_TEXT_WIDTH - 6);
            rowProgress.setMaxWidth(LIST_PROGRESS_WIDTH - LIST_PROGRESS_TEXT_WIDTH - 6);
            rowProgress.setVisible(false);
            rowProgress.setManaged(true);
            rowProgress.setProgress(0);
            progressPane.setAlignment(Pos.CENTER_RIGHT);
            itemGrid.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setHgap(8);
            itemGrid.setMaxWidth(Double.MAX_VALUE);
            itemGrid.add(selectBox, 0, 0);
            itemGrid.add(statusTag, 1, 0);
            itemGrid.add(nameLabel, 2, 0);
            itemGrid.add(trailingSpacer, 3, 0);
            itemGrid.add(progressPane, 4, 0);
            itemGrid.add(downloadButton, 5, 0);
            GridPane.setHgrow(nameLabel, Priority.ALWAYS);
            GridPane.setHgrow(trailingSpacer, Priority.ALWAYS);
            GridPane.setHgrow(progressPane, Priority.NEVER);
            HBox.setHgrow(itemGrid, Priority.ALWAYS);
            HBox.setHgrow(progressPane, Priority.NEVER);
            itemGrid.getColumnConstraints().addAll(
                column(28, 28, Priority.NEVER),
                column(LIST_STATUS_WIDTH, LIST_STATUS_WIDTH, Priority.NEVER),
                column(340, 540, Priority.ALWAYS),
                column(0, 0, Priority.ALWAYS),
                column(LIST_PROGRESS_WIDTH, LIST_PROGRESS_WIDTH, Priority.NEVER),
                column(LIST_DOWNLOAD_WIDTH, LIST_DOWNLOAD_WIDTH, Priority.NEVER)
            );
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemPane.setAlignment(Pos.CENTER_LEFT);
            itemPane.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemPane.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_ITEM_CONTENT_MIN_WIDTH));
            clip.widthProperty().bind(itemPane.widthProperty());
            clip.heightProperty().bind(itemPane.heightProperty());
            itemPane.setClip(clip);
            groupRow.setCenter(groupLabel);
            groupRow.setRight(collapseButton);
            BorderPane.setAlignment(groupLabel, Pos.CENTER_LEFT);
            BorderPane.setAlignment(collapseButton, Pos.CENTER_RIGHT);
            groupLabel.setMaxWidth(Double.MAX_VALUE);
            groupRow.setMinWidth(LIST_GROUP_CONTENT_MIN_WIDTH);
            groupRow.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_GROUP_CONTENT_MIN_WIDTH));
            collapseButton.setMinWidth(28);
            collapseButton.setPrefWidth(28);
            collapseButton.setMaxWidth(28);
            collapseButton.setText(null);
            collapseButton.setGraphic(collapseIconBox);
            collapseButton.setOnAction(event -> {
                RemoteRow current = getItem();
                if (current != null && current.type == RemoteRowType.GROUP && !isFolderAnimating(current.groupKey)) {
                    boolean targetCollapsed = !current.collapsed;
                    if (setFolderCollapsed(current, targetCollapsed)) {
                        collapseButton.setDisable(true);
                        animateCollapseButton(targetCollapsed);
                    }
                }
                event.consume();
            });
            downloadButton.setOnAction(e -> {
                RemoteRow row = getItem();
                if (row != null && row.type == RemoteRowType.ITEM && row.entry != null) {
                    if (isFolderAnimating(row.groupKey)) {
                        e.consume();
                        return;
                    }
                    String key = progressKey(row.entry);
                    if (activeSingleDownloads.contains(key)) {
                        cancelSingleDownload(key);
                    } else if (DlcRemoteService.isMissing(row.entry) && !activeBatchDownloads.contains(key)) {
                        downloadSingle(row.entry);
                    }
                }
                e.consume();
            });
            selectBox.setOnMouseClicked(e -> {
                RemoteRow row = getItem();
                if (row == null || row.type != RemoteRowType.ITEM || row.entry == null) {
                    e.consume();
                    return;
                }
                if (!DlcRemoteService.isMissing(row.entry)) {
                    e.consume();
                    return;
                }
                if (isFolderAnimating(row.groupKey)) {
                    selectBox.setSelected(isEntrySelected(row.entry));
                    e.consume();
                    return;
                }
                setSelected(row.entry, selectBox.isSelected());
                syncVisibleItemCells();
                updateActionButtons();
                e.consume();
            });
            itemRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), selectBox) && !isInside(event.getTarget(), downloadButton)) {
                    event.consume();
                }
            });
            groupRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), collapseButton)) {
                    event.consume();
                }
                if (event.getButton() == MouseButton.PRIMARY) {
                    if (listView != null) {
                        listView.getSelectionModel().clearSelection();
                    }
                }
            });
        }

        @Override
        protected void updateItem(RemoteRow row, boolean empty) {
            super.updateItem(row, empty);
            activeItemCells.remove(this);
            activeGroupCells.remove(this);
            getStyleClass().removeAll("dlc-row-group", "dlc-row-path", "dlc-row-item");
            if (empty || row == null) {
                resetRowAnimation();
                setGraphic(null);
                setText(null);
                return;
            }
            if (row.type == RemoteRowType.GROUP) {
                resetRowAnimation();
                setMouseTransparent(false);
                activeGroupCells.add(this);
                getStyleClass().add("dlc-row-group");
                groupLabel.setText(row.groupLabel);
                syncGroupState(row.groupKey);
                setGraphic(groupRow);
                setText(null);
                return;
            }
            resetRowAnimation();
            activeItemCells.add(this);
            getStyleClass().add("dlc-row-item");
            setMouseTransparent(false);
            DlcRemoteEntry entry = row.entry;
            boolean missing = DlcRemoteService.isMissing(entry);
            selectBox.setVisible(true);
            selectBox.setManaged(true);
            selectBox.setDisable(!missing || downloading);
            selectBox.setOpacity(1);
            selectBox.setSelected(missing && isEntrySelected(entry));
            DlcFxRichTextRenderer.setFormatted(nameLabel, safeName(entry));
            statusTag.setVisible(true);
            statusTag.setManaged(true);
            statusTag.setStyle("-fx-text-fill: white;");
            String progressId = progressKey(entry);
            boolean showProgress = progressByKey.containsKey(progressId);
            boolean singleDownloading = activeSingleDownloads.contains(progressId);
            boolean batchDownloading = activeBatchDownloads.contains(progressId);
            if (missing) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.missing"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                statusTag.setStyle("-fx-text-fill: white;");
                configureDownloadButton(singleDownloading, batchDownloading);
                rowProgressLabel.setVisible(showProgress);
                rowProgressLabel.setManaged(showProgress);
                rowProgress.setVisible(showProgress);
                rowProgress.setManaged(showProgress);
                Float progress = progressByKey.get(progressId);
                rowProgress.setProgress(progress == null ? 0 : progress);
                rowProgressLabel.setText(progress == null ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%");
            } else {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.present"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_OK);
                statusTag.setStyle("-fx-text-fill: white;");
                downloadButton.setVisible(false);
                downloadButton.setManaged(false);
                rowProgressLabel.setVisible(false);
                rowProgressLabel.setManaged(false);
                rowProgress.setVisible(false);
                rowProgress.setManaged(false);
                rowProgress.setProgress(0);
                rowProgressLabel.setText("");
            }
            getStyleClass().remove("dlc-row-selected");
            if (selectBox.isSelected()) {
                getStyleClass().add("dlc-row-selected");
            }
            setTooltip(DlcFxRichTextRenderer.formattedTooltip(buildTooltip(entry)));
            setGraphic(itemPane);
            startRowAnimationIfNeeded(row);
        }

        private void syncItemState() {
            RemoteRow row = getItem();
            if (row == null || row.type != RemoteRowType.ITEM || row.entry == null) {
                return;
            }
            boolean missing = DlcRemoteService.isMissing(row.entry);
            boolean selected = missing && isEntrySelected(row.entry);
            getStyleClass().remove("dlc-row-selected");
            if (selected) {
                getStyleClass().add("dlc-row-selected");
            }
            selectBox.setSelected(selected);
            if (missing) {
                selectBox.setVisible(true);
                selectBox.setManaged(true);
                String progressId = progressKey(row.entry);
                boolean showProgress = progressByKey.containsKey(progressId);
                boolean singleDownloading = activeSingleDownloads.contains(progressId);
                boolean batchDownloading = activeBatchDownloads.contains(progressId);
                configureDownloadButton(singleDownloading, batchDownloading);
                Float progress = progressByKey.get(progressId);
                rowProgressLabel.setVisible(showProgress);
                rowProgressLabel.setManaged(showProgress);
                rowProgress.setVisible(showProgress);
                rowProgress.setManaged(showProgress);
                rowProgress.setProgress(progress == null ? 0 : progress);
                rowProgressLabel.setText(progress == null ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%");
            } else {
                selectBox.setVisible(false);
                selectBox.setManaged(false);
                downloadButton.setVisible(false);
                downloadButton.setManaged(false);
                rowProgressLabel.setVisible(false);
                rowProgressLabel.setManaged(false);
                rowProgress.setVisible(false);
                rowProgress.setManaged(false);
                rowProgress.setProgress(0);
            }
            statusTag.setStyle("-fx-text-fill: white;");
            boolean folderAnimating = isFolderAnimating(row.groupKey);
            itemRow.setMouseTransparent(folderAnimating);
            selectBox.setDisable(folderAnimating || !missing || downloading);
            downloadButton.setDisable(folderAnimating);
        }

        private void syncGroupState(String folderKey) {
            RemoteRow row = getItem();
            if (row == null || row.type != RemoteRowType.GROUP) {
                return;
            }
            if (folderKey == null || folderKey.equals(row.groupKey)) {
                collapseIcon.setRotate(row.collapsed ? 180 : 0);
                collapseButton.setDisable(isFolderAnimating(row.groupKey));
            }
        }

        private void startAnimationForFolder(String folderKey) {
            RemoteRow row = getItem();
            if (row == null || row.type != RemoteRowType.ITEM || !folderKey.equals(row.groupKey)) {
                return;
            }
            syncItemState();
            startRowAnimationIfNeeded(row);
        }

        private void animateCollapseButton(boolean collapsing) {
            if (collapseTransition != null) {
                collapseTransition.stop();
            }
            collapseIcon.setRotate(collapsing ? 0 : 180);
            collapseTransition = new RotateTransition(Duration.millis(180), collapseIcon);
            collapseTransition.setInterpolator(Interpolator.EASE_BOTH);
            collapseTransition.setToAngle(collapsing ? 180 : 0);
            collapseTransition.play();
        }

        private void startRowAnimationIfNeeded(RemoteRow row) {
            if (row == null) {
                resetRowAnimation();
                return;
            }
            if (row.collapsing && !isCurrentAnimation(row, false)) {
                animateRow(row, false, Math.max(getCurrentAnimatedHeight(), 42), 0, 1, 0, null);
                return;
            }
            if (row.expanding && !isCurrentAnimation(row, true)) {
                animateRow(row, true, 0, 42, 0, 1, () -> {
                    if (getItem() == row && row.expanding && !row.collapsing) {
                        row.expanding = false;
                        resetRowAnimation();
                    }
                });
                return;
            }
            if (!row.expanding && !row.collapsing) {
                resetRowAnimation();
            }
        }

        private boolean isCurrentAnimation(RemoteRow row, boolean expandingAnimation) {
            return rowTimeline != null && animatedRow == row && animatedExpansion != null && animatedExpansion == expandingAnimation;
        }

        private void animateRow(RemoteRow row, boolean expandingAnimation, double fromHeight, double toHeight,
                                double fromOpacity, double toOpacity, Runnable afterFinished) {
            if (rowTimeline != null) {
                rowTimeline.stop();
            }
            animatedRow = row;
            animatedExpansion = expandingAnimation;
            itemPane.setClip(clip);
            itemPane.setMinHeight(fromHeight);
            itemPane.setPrefHeight(fromHeight);
            itemPane.setMaxHeight(fromHeight);
            itemPane.setOpacity(fromOpacity);
            rowTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(itemPane.minHeightProperty(), fromHeight),
                    new KeyValue(itemPane.prefHeightProperty(), fromHeight),
                    new KeyValue(itemPane.maxHeightProperty(), fromHeight),
                    new KeyValue(itemPane.opacityProperty(), fromOpacity)
                ),
                new KeyFrame(Duration.millis(240),
                    new KeyValue(itemPane.minHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                    new KeyValue(itemPane.prefHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                    new KeyValue(itemPane.maxHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                    new KeyValue(itemPane.opacityProperty(), toOpacity, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0))
                )
            );
            rowTimeline.setOnFinished(event -> {
                if (getItem() != row || row.expanding != expandingAnimation || row.collapsing == expandingAnimation) {
                    return;
                }
                rowTimeline = null;
                animatedRow = null;
                animatedExpansion = null;
                if (afterFinished != null) {
                    afterFinished.run();
                }
            });
            rowTimeline.play();
        }

        private double getCurrentAnimatedHeight() {
            double height = itemPane.getHeight();
            if (Double.isNaN(height) || height <= 0) {
                height = itemPane.getPrefHeight();
            }
            if (Double.isNaN(height) || height <= 0 || height == Region.USE_COMPUTED_SIZE || height == Region.USE_PREF_SIZE) {
                return 42;
            }
            return height;
        }

        private void configureDownloadButton(boolean singleDownloading, boolean batchDownloading) {
            downloadButton.getStyleClass().remove("danger");
            if (batchDownloading) {
                downloadButton.setVisible(false);
                downloadButton.setManaged(false);
                downloadButton.setDisable(false);
                downloadButton.setText(DlcFxI18n.tr("dlc_manager.remote.download"));
                DlcFxStyle.add(downloadButton, "dlc-neutral-button");
                return;
            }
            downloadButton.setVisible(true);
            downloadButton.setManaged(true);
            downloadButton.setDisable(false);
            if (singleDownloading) {
                DlcFxStyle.danger(downloadButton);
                downloadButton.setText(DlcFxI18n.tr("dlc_manager.common.cancel"));
            } else {
                DlcFxStyle.add(downloadButton, "dlc-neutral-button");
                downloadButton.setText(DlcFxI18n.tr("dlc_manager.remote.download"));
            }
        }

        private void resetRowAnimation() {
            if (rowTimeline != null) {
                rowTimeline.stop();
                rowTimeline = null;
            }
            animatedRow = null;
            animatedExpansion = null;
            setMinHeight(Region.USE_PREF_SIZE);
            setPrefHeight(Region.USE_COMPUTED_SIZE);
            setMaxHeight(Region.USE_COMPUTED_SIZE);
            setOpacity(1);
            itemPane.setMinHeight(Region.USE_PREF_SIZE);
            itemPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setOpacity(1);
            itemPane.setClip(null);
        }
    }

    private void downloadSingle(DlcRemoteEntry entry) {
        if (!requireGameDir() || entry == null || !DlcRemoteService.isMissing(entry)) {
            return;
        }
        String key = progressKey(entry);
        AtomicBoolean cancelRequested = new AtomicBoolean(false);
        singleCancelRequests.put(key, cancelRequested);
        activeSingleDownloads.add(key);
        progressByKey.put(key, 0f);
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
                            progressByKey.put(key, progress);
                            syncVisibleItemCells();
                        });
                    }
                }, () -> cancelRequested.get());
                Platform.runLater(() -> {
                    progressByKey.remove(key);
                    activeSingleDownloads.remove(key);
                    singleCancelRequests.remove(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.has_missing"));
                    syncVisibleItemCells();
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    progressByKey.remove(key);
                    activeSingleDownloads.remove(key);
                    singleCancelRequests.remove(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    syncVisibleItemCells();
                });
            } catch (DlcSharedService.DownloadCanceledException ex) {
                Platform.runLater(() -> {
                    progressByKey.remove(key);
                    activeSingleDownloads.remove(key);
                    singleCancelRequests.remove(key);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_canceled"));
                    syncVisibleItemCells();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressByKey.remove(key);
                    activeSingleDownloads.remove(key);
                    singleCancelRequests.remove(key);
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
        AtomicBoolean cancelRequested = singleCancelRequests.get(key);
        if (cancelRequested != null) {
            cancelRequested.set(true);
            syncVisibleItemCells();
        }
    }

    private boolean isEntrySelected(DlcRemoteEntry entry) {
        return entry != null && selectedEntryIds.contains(selectionId(entry));
    }

    private boolean isInside(Object target, Node parent) {
        if (!(target instanceof Node node) || parent == null) {
            return false;
        }
        while (node != null) {
            if (node == parent) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private static DlcType typeOf(DlcTypeOption option) {
        return option == null || option.all ? null : option.type;
    }

    private static String typeText(DlcTypeOption option) {
        DlcType type = typeOf(option);
        return type == null ? DlcFxI18n.tr("dlc_manager.local.all_types") : DlcFxI18n.tr(type.getDisplayKey());
    }

    private static final class DlcTypeButtonCell extends ListCell<DlcTypeOption> {
        private final ComboBox<DlcTypeOption> owner;

        private DlcTypeButtonCell(ComboBox<DlcTypeOption> owner) {
            this.owner = owner;
        }

        @Override
        protected void updateItem(DlcTypeOption item, boolean empty) {
            super.updateItem(item, empty);
            setText(typeText(owner == null ? item : owner.getValue()));
        }
    }

    private static final class DlcTypePopupCell extends ListCell<DlcTypeOption> {
        @Override
        protected void updateItem(DlcTypeOption item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "" : typeText(item));
        }
    }

    private static final class DlcTypeOption {
        private static final DlcTypeOption ALL = new DlcTypeOption(true, null);
        private final boolean all;
        private final DlcType type;

        private DlcTypeOption(boolean all, DlcType type) {
            this.all = all;
            this.type = type;
        }

        private static DlcTypeOption all() {
            return ALL;
        }

        private static DlcTypeOption of(DlcType type) {
            return type == null ? all() : new DlcTypeOption(false, type);
        }

        @Override
        public String toString() {
            return typeText(this);
        }
    }

    private enum RemoteRowType {
        GROUP,
        ITEM
    }

    private static final class RemoteRow {
        private final RemoteRowType type;
        private final String groupKey;
        private final String groupLabel;
        private final String folderRaw;
        private final DlcRemoteEntry entry;
        private boolean collapsed;
        private boolean expanding;
        private boolean collapsing;

        private RemoteRow(RemoteRowType type, String groupKey, String groupLabel, String folderRaw,
                          DlcRemoteEntry entry, boolean collapsed) {
            this.type = type;
            this.groupKey = groupKey;
            this.groupLabel = groupLabel;
            this.folderRaw = folderRaw;
            this.entry = entry;
            this.collapsed = collapsed;
        }

        private static RemoteRow group(String groupKey, String label, String folderRaw, boolean collapsed) {
            return new RemoteRow(RemoteRowType.GROUP, groupKey, label, folderRaw, null, collapsed);
        }

        private static RemoteRow item(DlcRemoteEntry entry, String groupKey) {
            return new RemoteRow(RemoteRowType.ITEM, groupKey, null, null, entry, false);
        }
    }
}
