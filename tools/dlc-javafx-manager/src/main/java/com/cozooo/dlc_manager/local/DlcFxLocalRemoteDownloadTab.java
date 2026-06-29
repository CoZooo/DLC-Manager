package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import javafx.scene.shape.Rectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class DlcFxLocalRemoteDownloadTab {

    private static final double LIST_ROW_WIDTH_RESERVED = 34;
    private static final double LIST_ITEM_CONTENT_MIN_WIDTH = 620;
    private static final double LIST_GROUP_CONTENT_MIN_WIDTH = 260;
    private static final double LIST_PROGRESS_TEXT_WIDTH = 40;
    private static final double LIST_PROGRESS_WIDTH = 140;
    private static final double LIST_DOWNLOAD_WIDTH = 82;

    private final DlcFxPlatformProvider provider;
    private final ObservableList<DlcRemoteEntry> allEntries = FXCollections.observableArrayList();
    private final ObservableList<RemoteRow> visibleRows = FXCollections.observableArrayList();
    private final Set<String> selectedEntryIds = new LinkedHashSet<>();
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    private final Set<String> animatingGroups = new LinkedHashSet<>();
    private final Set<String> activeSingleDownloads = new LinkedHashSet<>();
    private final Set<RemoteEntryCell> activeItemCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<RemoteEntryCell> activeGroupCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<String, Float> progressByKey = new java.util.concurrent.ConcurrentHashMap<>();

    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.refresh_download_list"));
    private final Button downloadMissingBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.download_missing"));
    private final Button downloadSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.common.download_selected"));
    private final Label countLabel = new Label("");
    private final Label totalProgressLabel = new Label("");
    private ListView<RemoteRow> listView;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private boolean downloading;

    public DlcFxLocalRemoteDownloadTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
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
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(downloadMissingBtn);
        DlcFxStyle.add(downloadSelectedBtn, "dlc-toggle-off");
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(totalProgressLabel);
        totalProgressLabel.setMinWidth(120);
        totalProgressLabel.setPrefWidth(120);
        totalProgressLabel.setMaxWidth(120);
        statusLabel.setMinWidth(240);
        statusLabel.setPrefWidth(240);
        statusLabel.setMaxWidth(240);

        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topBox = new HBox(8, refreshBtn, countLabel, topSpacer, selectionActions);
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
        HBox rightActions = new HBox(8, progressBar, totalProgressLabel, statusLabel);
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
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.game_dir_changed"));
        refreshBtn.setDisable(false);
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

    private void toggleGroup(String groupKey) {
        if (groupKey == null || groupKey.isBlank() || animatingGroups.contains(groupKey)) {
            return;
        }
        if (collapsedGroups.contains(groupKey)) {
            collapsedGroups.remove(groupKey);
        } else {
            collapsedGroups.add(groupKey);
        }
        rebuildRows();
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
        for (DlcRemoteEntry entry : allEntries) {
            if (entry != null && DlcRemoteService.isMissing(entry) && isEntrySelected(entry)) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private void updateActionButtons() {
        boolean busy = downloading || progressBar.isVisible();
        downloadMissingBtn.setDisable(busy || !gameDirSelected);
        downloadSelectedBtn.setDisable(busy || !gameDirSelected || selectedMissingEntries().isEmpty());
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
        refreshBtn.setDisable(true);
        updateActionButtons();
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(0);
        totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.remote.downloading_total", 0, toDownload.size()));
        initProgressMap(toDownload);
        Thread thread = new Thread(() -> {
            DlcRemoteService.BatchResult result;
            try {
                result = DlcRemoteService.downloadAll(toDownload, new DlcRemoteService.BatchProgressListener() {
                    @Override
                    public void onEntryStart(DlcRemoteEntry entry, int index, int total) {
                        Platform.runLater(() -> markDownloading(entry, 0f));
                    }

                    @Override
                    public void onEntryAttemptStart(DlcRemoteEntry entry, int attemptId, int index, int total) {
                        Platform.runLater(() -> markDownloading(entry, 0f));
                    }

                    @Override
                    public void onEntryProgress(DlcRemoteEntry entry, long downloadedBytes, long totalBytes) {
                        if (totalBytes > 0) {
                            Platform.runLater(() -> {
                                markDownloading(entry, Math.min(1f, downloadedBytes / (float) totalBytes));
                                updateBatchProgress(toDownload.size());
                            });
                        }
                    }

                    @Override
                    public void onEntrySuccess(DlcRemoteEntry entry, String fileName, int completed, int total) {
                        Platform.runLater(() -> {
                            markDownloading(entry, 1f);
                            updateBatchProgress(total);
                            totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.remote.downloading_total", completed, total));
                        });
                    }

                    @Override
                    public void onEntryFailure(DlcRemoteEntry entry, Throwable error, int completed, int total) {
                        Platform.runLater(() -> {
                            markDownloading(entry, 1f);
                            updateBatchProgress(total);
                            totalProgressLabel.setText(DlcFxI18n.tr("dlc_manager.remote.downloading_total", completed, total));
                        });
                    }
                }, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = new DlcRemoteService.BatchResult(toDownload.size(), 0,
                    List.of(new DlcRemoteService.EntryError(null, ex)));
            }
            DlcRemoteService.BatchResult finalResult = result;
            Platform.runLater(() -> {
                downloading = false;
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                totalProgressLabel.setText("");
                progressByKey.clear();
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.completed", finalResult.success, finalResult.total));
                if (!finalResult.errors.isEmpty()) {
                    statusLabel.setText(statusLabel.getText() + DlcFxI18n.tr("dlc_manager.status.failed_count_suffix", finalResult.errors.size()));
                }
                progressBar.setProgress(0);
                refreshBtn.setDisable(false);
                syncVisibleItemCells();
                updateActionButtons();
            });
        }, "dlc-remote-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void initProgressMap(List<DlcRemoteEntry> targets) {
        progressByKey.clear();
        if (targets == null) {
            return;
        }
        for (DlcRemoteEntry entry : targets) {
            if (entry != null) {
                progressByKey.put(selectionId(entry), 0f);
            }
        }
        syncVisibleItemCells();
    }

    private void updateBatchProgress(int total) {
        if (total <= 0) {
            progressBar.setProgress(-1);
            return;
        }
        float sum = 0f;
        for (Float value : progressByKey.values()) {
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
        for (DlcType type : DlcType.values()) {
            List<DlcRemoteEntry> typeEntries = allEntries.stream()
                .filter(entry -> entry != null && !entry.isHidden() && entry.getType() == type)
                .toList();
            if (typeEntries.isEmpty()) {
                continue;
            }
            String groupKey = groupKey(type);
            rows.add(RemoteRow.group(groupKey, type));
            if (collapsedGroups.contains(groupKey)) {
                continue;
            }
            for (DlcRemoteEntry entry : typeEntries) {
                rows.add(RemoteRow.item(entry, groupKey));
            }
        }
        visibleRows.setAll(rows);
        if (listView != null) {
            listView.refresh();
        }
        updateActionButtons();
    }

    private String groupKey(DlcType type) {
        return type == null ? "unknown" : type.name();
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
        private final HBox groupRow = new HBox(8, groupLabel);
        private final StackPane itemPane = new StackPane(itemRow);
        private final Rectangle clip = new Rectangle();

        RemoteEntryCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            statusTag.setStyle("-fx-text-fill: white;");
            DlcFxStyle.add(groupLabel, "dlc-dlc-group-header");
            DlcFxStyle.add(itemRow, "dlc-dlc-row");
            DlcFxStyle.add(itemPane, "dlc-dlc-row");
            DlcFxStyle.add(downloadButton, "dlc-neutral-button");
            DlcFxStyle.muted(rowProgressLabel);
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
            itemGrid.setHgap(10);
            itemGrid.setMaxWidth(Double.MAX_VALUE);
            itemGrid.add(selectBox, 0, 0);
            itemGrid.add(nameLabel, 1, 0);
            itemGrid.add(statusTag, 2, 0);
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
                column(340, 540, Priority.ALWAYS),
                column(104, 124, Priority.NEVER),
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
            groupRow.setAlignment(Pos.CENTER_LEFT);
            groupRow.setMinWidth(LIST_GROUP_CONTENT_MIN_WIDTH);
            groupRow.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_GROUP_CONTENT_MIN_WIDTH));
            downloadButton.setOnAction(e -> {
                RemoteRow row = getItem();
                if (row != null && row.type == RemoteRowType.ITEM && row.entry != null && DlcRemoteService.isMissing(row.entry)) {
                    downloadSingle(row.entry);
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
                setSelected(row.entry, selectBox.isSelected());
                updateActionButtons();
                e.consume();
            });
            itemRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), selectBox) && !isInside(event.getTarget(), downloadButton)) {
                    event.consume();
                }
            });
            groupRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    RemoteRow row = getItem();
                    if (row != null && row.type == RemoteRowType.GROUP && row.groupKey != null) {
                        toggleGroup(row.groupKey);
                    }
                }
                event.consume();
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
                activeGroupCells.add(this);
                getStyleClass().add("dlc-row-group");
                groupLabel.setText(DlcFxI18n.tr(row.groupType.getDisplayKey()));
                setGraphic(groupRow);
                setText(null);
                return;
            }
            resetRowAnimation();
            activeItemCells.add(this);
            getStyleClass().add("dlc-row-item");
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
            if (missing) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.missing"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                statusTag.setStyle("-fx-text-fill: white;");
                downloadButton.setVisible(true);
                downloadButton.setManaged(true);
                downloadButton.setDisable(showProgress);
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
                downloadButton.setVisible(true);
                downloadButton.setManaged(true);
                String progressId = progressKey(row.entry);
                boolean showProgress = progressByKey.containsKey(progressId);
                downloadButton.setDisable(showProgress);
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
        }

        private void resetRowAnimation() {
            itemPane.setMinHeight(Region.USE_PREF_SIZE);
            itemPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setOpacity(1);
            itemPane.setClip(clip);
        }
    }

    private void downloadSingle(DlcRemoteEntry entry) {
        if (!requireGameDir() || entry == null || !DlcRemoteService.isMissing(entry)) {
            return;
        }
        String key = progressKey(entry);
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
                }, () -> false);
                Platform.runLater(() -> {
                    progressByKey.remove(key);
                    activeSingleDownloads.remove(key);
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
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    totalProgressLabel.setText("");
                    syncVisibleItemCells();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressByKey.remove(key);
                    activeSingleDownloads.remove(key);
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

    private enum RemoteRowType {
        GROUP,
        ITEM
    }

    private static final class RemoteRow {
        private final RemoteRowType type;
        private final String groupKey;
        private final DlcType groupType;
        private final DlcRemoteEntry entry;

        private RemoteRow(RemoteRowType type, String groupKey, DlcType groupType, DlcRemoteEntry entry) {
            this.type = type;
            this.groupKey = groupKey;
            this.groupType = groupType;
            this.entry = entry;
        }

        private static RemoteRow group(String groupKey, DlcType type) {
            return new RemoteRow(RemoteRowType.GROUP, groupKey, type, null);
        }

        private static RemoteRow item(DlcRemoteEntry entry, String groupKey) {
            return new RemoteRow(RemoteRowType.ITEM, groupKey, null, entry);
        }
    }
}
