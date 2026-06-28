package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.app.DlcFxManagerApp;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteService;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;

public final class DlcFxLocalRemoteDownloadTab {

    private final DlcFxPlatformProvider provider;

    private final ObservableList<DlcRemoteEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.refresh"));
    private final Button downloadMissingBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.download_missing"));
    private final Button downloadSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.common.download_selected"));
    private final Label countLabel = new Label("");
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalRemoteDownloadTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_remote"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        refreshBtn.setText(DlcFxI18n.tr("dlc_manager.remote.refresh_download_list"));
        refreshBtn.setDisable(true);
        refreshBtn.setOnAction(e -> {
            if (gameDirSelected) {
                refreshAsync();
            }
        });
        downloadMissingBtn.setOnAction(e -> downloadMissingAsync());
        downloadMissingBtn.setDisable(true);
        downloadSelectedBtn.setDisable(true);
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(downloadMissingBtn);
        DlcFxStyle.muted(countLabel);

        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);

        HBox topBox = new HBox(8, refreshBtn, countLabel, topSpacer, selectionActions);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        ListView<DlcRemoteEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new RemoteEntryCell());
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox centerBox = new VBox(listView);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        selectAllBtn.setOnAction(e -> listView.getSelectionModel().selectAll());
        selectNoneBtn.setOnAction(e -> listView.getSelectionModel().clearSelection());
        invertBtn.setOnAction(e -> {
            var selectionModel = listView.getSelectionModel();
            java.util.List<Integer> selected = new java.util.ArrayList<>(selectionModel.getSelectedIndices());
            selectionModel.clearSelection();
            for (int i = 0; i < listView.getItems().size(); i++) {
                if (!selected.contains(i)) {
                    selectionModel.select(i);
                }
            }
        });

        downloadSelectedBtn.setOnAction(e -> {
            if (!requireGameDir()) {
                return;
            }
            java.util.List<DlcRemoteEntry> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
                return;
            }
            java.util.List<DlcRemoteEntry> missingSelected = new java.util.ArrayList<>();
            for (DlcRemoteEntry entry : selected) {
                if (entry != null && DlcRemoteService.isMissing(entry)) {
                    missingSelected.add(entry);
                }
            }
            if (missingSelected.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.selected_present"));
                return;
            }
            java.util.List<DlcRemoteEntry> toDownload = DlcRemoteService.expandMissingWithDependencies(missingSelected);
            downloadAll(toDownload);
        });

        HBox actionPanel = new HBox(8, downloadMissingBtn, downloadSelectedBtn, progressBar, statusLabel);
        actionPanel.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        entries.clear();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.game_dir_changed"));
        refreshBtn.setDisable(false);
        updateActionButtons(false);
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
            updateActionButtons(false);
            return;
        }
        loadedForCurrentGameDir = true;
        refreshBtn.setDisable(true);
        updateActionButtons(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote.loading_status"));
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRemoteEntry> loaded = DlcRemoteLoader.load(dlcRoot);
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    int missing = DlcRemoteService.countMissing(loaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.remote.count", loaded.size(), missing));
                    statusLabel.setText(missing == 0 ? DlcFxI18n.tr("dlc_manager.status.no_missing") : DlcFxI18n.tr("dlc_manager.remote.has_missing"));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    updateActionButtons(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    updateActionButtons(false);
                });
            }
        }, "dlc-remote-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadMissingAsync() {
        if (!requireGameDir()) {
            return;
        }
        List<DlcRemoteEntry> missing = DlcRemoteService.collectMissing(entries);
        if (missing.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.no_missing_downloads"));
            return;
        }
        List<DlcRemoteEntry> toDownload = DlcRemoteService.expandMissingWithDependencies(missing);
        downloadAll(toDownload);
    }

    private void downloadAll(List<DlcRemoteEntry> toDownload) {
        if (!requireGameDir()) {
            return;
        }
        downloadMissingBtn.setDisable(true);
        downloadSelectedBtn.setDisable(true);
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        Thread thread = new Thread(() -> {
            DlcRemoteService.BatchResult result;
            try {
                result = DlcRemoteService.downloadAll(toDownload, new DlcRemoteService.BatchProgressListener() {
                    @Override
                    public void onEntrySuccess(DlcRemoteEntry entry, String fileName, int completed, int total) {
                        Platform.runLater(() -> progressBar.setProgress((double) completed / total));
                    }

                    @Override
                    public void onEntryFailure(DlcRemoteEntry entry, Throwable error, int completed, int total) {
                        Platform.runLater(() -> progressBar.setProgress((double) completed / total));
                    }
                }, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = new DlcRemoteService.BatchResult(toDownload.size(), 0,
                        List.of(new DlcRemoteService.EntryError(null, ex)));
            }
            DlcRemoteService.BatchResult finalResult = result;
            Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append(DlcFxI18n.tr("dlc_manager.status.completed", finalResult.success, finalResult.total));
                if (!finalResult.errors.isEmpty()) {
                    sb.append(DlcFxI18n.tr("dlc_manager.status.failed_count_suffix", finalResult.errors.size()));
                }
                statusLabel.setText(sb.toString());
                progressBar.setVisible(false);
                refreshBtn.setDisable(false);
                updateActionButtons(false);
                refreshAsync();
            });
        }, "dlc-remote-download");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean requireGameDir() {
        if (!gameDirSelected || DlcConfig.getDlcRoot() == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            updateActionButtons(false);
            return false;
        }
        return true;
    }

    private void updateActionButtons(boolean busy) {
        boolean disabled = busy || !gameDirSelected;
        downloadMissingBtn.setDisable(disabled);
        downloadSelectedBtn.setDisable(disabled);
    }

    private final class RemoteEntryCell extends javafx.scene.control.ListCell<DlcRemoteEntry> {
        private final Label nameLabel = new Label();
        private final Label statusTag = new Label();
        private final Label detailLabel = new Label();
        private final HBox row = new HBox(8, nameLabel, statusTag, detailLabel);

        RemoteEntryCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.muted(detailLabel);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(DlcRemoteEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                String name = safeName(item);
                if (item.getType() == DlcType.DLC_PACK) {
                    name = DlcFxI18n.tr("dlc_manager.pack.complete_item_prefix", name);
                }
                DlcFxRichTextRenderer.setFormatted(nameLabel, name);
                boolean missing = DlcRemoteService.isMissing(item);
                if (missing) {
                    statusTag.setText(DlcFxI18n.tr("dlc_manager.status.missing"));
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                } else {
                    statusTag.setText(DlcFxI18n.tr("dlc_manager.status.present"));
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_OK);
                }
                Path target = item.getTargetPath();
                detailLabel.setText(target == null ? DlcFxI18n.tr("dlc_manager.status.no_target_path") : target.toString());
                StringBuilder tip = new StringBuilder();
                tip.append(DlcFxI18n.tr("dlc_manager.tooltip.identifier")).append(item.getDlcIdentifier() == null ? "" : item.getDlcIdentifier());
                if (item.getType() != null) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.type")).append(item.getType().name());
                }
                if (!item.getDirectUrls().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.direct_url")).append(item.getDirectUrls().get(0));
                }
                if (item.getCurseforgeProjectId() != null && !item.getCurseforgeProjectId().isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.curseforge")).append(item.getCurseforgeProjectId());
                }
                if (item.getModrinthProjectId() != null && !item.getModrinthProjectId().isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.modrinth")).append(item.getModrinthProjectId());
                }
                setTooltip(DlcFxRichTextRenderer.formattedTooltip(tip.toString()));
                setGraphic(row);
            }
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

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
