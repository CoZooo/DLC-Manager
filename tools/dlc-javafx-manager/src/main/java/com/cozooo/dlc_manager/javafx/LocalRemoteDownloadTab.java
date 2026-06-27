package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteService;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.core.DlcConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;

/**
 * Controller for the "本地 · Remote 下载" tab.
 * <p>
 * Lists every {@code .dc} entry under {@code DLC/remote/}, marks the ones
 * whose target file is missing, and lets the user resolve + download them
 * through the same download runtime used by the in-game client.
 */
final class LocalRemoteDownloadTab {

    private final StandalonePlatformProvider provider;

    private final ObservableList<DlcRemoteEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("请先在「本地 · DLC 管理」中选择游戏目录");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button("刷新远程列表");
    private final Button downloadMissingBtn = new Button("下载全部缺失项");
    private final Button downloadSelectedBtn = new Button("下载选中项");
    private final Label countLabel = new Label("");

    LocalRemoteDownloadTab(StandalonePlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
    }

    Tab build() {
        Tab tab = new Tab("本地 · Remote 下载");
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: action bar ───────────────────────────────────────
        refreshBtn.setOnAction(e -> refreshAsync());
        downloadMissingBtn.setOnAction(e -> downloadMissingAsync());
        downloadSelectedBtn.disableProperty().bind(refreshBtn.disabledProperty());
        DlcFxStyle.primary(downloadMissingBtn);
        DlcFxStyle.muted(countLabel);

        HBox topBox = new HBox(8, refreshBtn, downloadMissingBtn, downloadSelectedBtn, countLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: entry list ────────────────────────────────────
        ListView<DlcRemoteEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new RemoteEntryCell());
        listView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox centerBox = new VBox(listView);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        // Download the user-selected entries (any that are still missing),
        // expanded with their nested remote dependencies so a selected pack
        // pulls in its declared deps too.
        downloadSelectedBtn.setOnAction(e -> {
            java.util.List<DlcRemoteEntry> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                statusLabel.setText("请先在列表中选择条目");
                return;
            }
            java.util.List<DlcRemoteEntry> missingSelected = new java.util.ArrayList<>();
            for (DlcRemoteEntry entry : selected) {
                if (entry != null && DlcRemoteService.isMissing(entry)) {
                    missingSelected.add(entry);
                }
            }
            if (missingSelected.isEmpty()) {
                statusLabel.setText("所选条目均已存在");
                return;
            }
            java.util.List<DlcRemoteEntry> toDownload = DlcRemoteService.expandMissingWithDependencies(missingSelected);
            downloadAll(toDownload);
        });

        // ── Bottom: progress + status ─────────────────────────────
        HBox bottomBox = new HBox(8, progressBar, statusLabel);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.statusStrip(bottomBox);
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    /** Called by DlcManagerApp when the game directory changes. */
    void onGameDirChanged() {
        entries.clear();
        countLabel.setText("");
        statusLabel.setText("游戏目录已更新，点击「刷新远程列表」");
        refreshBtn.setDisable(false);
    }

    // ───────────────────────────────────────────────────────────────
    //  Refresh
    // ───────────────────────────────────────────────────────────────

    private void refreshAsync() {
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("加载远程 DLC 列表…");
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRemoteEntry> loaded = DlcRemoteLoader.load(dlcRoot);
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    int missing = DlcRemoteService.countMissing(loaded);
                    countLabel.setText(String.format("共 %d 项，缺失 %d 项", loaded.size(), missing));
                    statusLabel.setText(missing == 0 ? "无缺失项" : "存在缺失项，可点击「下载全部缺失项」");
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("加载失败: " + safeMessage(t));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                });
            }
        }, "dlc-remote-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    // ───────────────────────────────────────────────────────────────
    //  Download
    // ───────────────────────────────────────────────────────────────

    private void downloadMissingAsync() {
        List<DlcRemoteEntry> missing = DlcRemoteService.collectMissing(entries);
        if (missing.isEmpty()) {
            statusLabel.setText("无缺失项需要下载");
            return;
        }
        // Recursively pull in each missing entry's nested remote dependencies
        // (declared inline in its [dependencies] block) that are themselves
        // missing — mirrors the in-game remote downloader.
        List<DlcRemoteEntry> toDownload = DlcRemoteService.expandMissingWithDependencies(missing);
        downloadAll(toDownload);
    }

    private void downloadAll(List<DlcRemoteEntry> toDownload) {
        downloadMissingBtn.setDisable(true);
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
                sb.append("完成 ").append(finalResult.success).append('/').append(finalResult.total);
                if (!finalResult.errors.isEmpty()) {
                    sb.append("，失败 ").append(finalResult.errors.size()).append(" 项");
                }
                statusLabel.setText(sb.toString());
                progressBar.setVisible(false);
                downloadMissingBtn.setDisable(false);
                refreshBtn.setDisable(false);
                // refresh list to reflect new files
                refreshAsync();
            });
        }, "dlc-remote-download");
        thread.setDaemon(true);
        thread.start();
    }

    // ───────────────────────────────────────────────────────────────
    //  Cell renderer
    // ───────────────────────────────────────────────────────────────

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
                nameLabel.setText(safeName(item));
                boolean missing = DlcRemoteService.isMissing(item);
                if (missing) {
                    statusTag.setText("缺失");
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                } else {
                    statusTag.setText("已存在");
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_OK);
                }
                Path target = item.getTargetPath();
                detailLabel.setText(target == null ? "(无目标路径)" : target.toString());
                StringBuilder tip = new StringBuilder();
                tip.append("标识: ").append(item.getDlcIdentifier() == null ? "" : item.getDlcIdentifier());
                if (item.getType() != null) {
                    tip.append("\n类型: ").append(item.getType().name());
                }
                if (!item.getDirectUrls().isEmpty()) {
                    tip.append("\n直链: ").append(item.getDirectUrls().get(0));
                }
                if (item.getCurseforgeProjectId() != null && !item.getCurseforgeProjectId().isBlank()) {
                    tip.append("\nCurseForge: ").append(item.getCurseforgeProjectId());
                }
                if (item.getModrinthProjectId() != null && !item.getModrinthProjectId().isBlank()) {
                    tip.append("\nModrinth: ").append(item.getModrinthProjectId());
                }
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────

    private static String safeName(DlcRemoteEntry entry) {
        String name = entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry.getFileNameHint();
        }
        if (name == null || name.isBlank()) {
            name = entry.getDlcIdentifier();
        }
        return name == null ? "(未命名)" : name;
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
