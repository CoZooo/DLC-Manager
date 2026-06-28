package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.core.DlcConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcFxLocalRequiredTab {

    private final DlcFxPlatformProvider provider;

    private final ObservableList<DlcRequiredEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.required.refresh"));
    private final Button downloadMissingBtn = new Button(DlcFxI18n.tr("dlc_manager.required.download_missing"));
    private final Button downloadSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.common.download_selected"));
    private final Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.required.install_selected"));
    private final Label countLabel = new Label("");
    private List<DlcRequiredEntry> forcedEntries = List.of();
    private boolean forceInstallFlow;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalRequiredTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_required"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        refreshBtn.setDisable(true);
        refreshBtn.setOnAction(e -> {
            if (gameDirSelected) {
                refreshAsync();
            }
        });
        downloadMissingBtn.setOnAction(e -> downloadMissingAsync());
        downloadMissingBtn.setDisable(true);
        downloadSelectedBtn.setDisable(true);
        applyBtn.setDisable(true);
        applyBtn.setOnAction(e -> installAsync());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(downloadMissingBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.muted(countLabel);

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

        ListView<DlcRequiredEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new RequiredEntryCell());
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
            java.util.List<DlcRequiredEntry> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
                return;
            }
            java.util.List<DlcRequiredEntry> missingSelected = new java.util.ArrayList<>();
            for (DlcRequiredEntry entry : selected) {
                if (entry != null && !DlcRequiredService.isDownloaded(entry)) {
                    missingSelected.add(entry);
                }
            }
            if (missingSelected.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.selected_downloaded"));
                return;
            }
            java.util.List<DlcRequiredEntry> toDownload = DlcRequiredService.collectMissingDownloads(
                DlcRequiredRuntime.collectDependencies(missingSelected));
            downloadAll(toDownload);
        });

        HBox downloadActions = new HBox(14, applyBtn, downloadMissingBtn, downloadSelectedBtn);
        downloadActions.setAlignment(Pos.CENTER_LEFT);
        HBox rightActions = new HBox(8, progressBar, statusLabel);
        rightActions.setAlignment(Pos.CENTER_RIGHT);

        BorderPane actionPanel = new BorderPane();
        actionPanel.setLeft(downloadActions);
        actionPanel.setRight(rightActions);
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
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.game_dir_changed"));
        refreshBtn.setDisable(false);
        updateActionButtons(false);
        forcedEntries = List.of();
        forceInstallFlow = false;
    }

    public void onTabSelected() {
        if (!gameDirSelected || loadedForCurrentGameDir) {
            return;
        }
        refreshAsync();
    }

    public void forceDownloadAndInstall(List<DlcRequiredEntry> missing) {
        if (missing != null && !missing.isEmpty()) {
            forcedEntries = new ArrayList<>(DlcRequiredRuntime.collectDependencies(missing));
            entries.setAll(forcedEntries);
        }
        forceInstallFlow = true;
        downloadMissingAsync();
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
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.loading"));
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRequiredEntry> loaded = DlcRequiredLoader.load(dlcRoot);
                DlcRequiredService.Status status = DlcRequiredService.inspect(loaded);
                final int fMissing = status.missingDownloads;
                final int fNeedsApply = status.needsApply;
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.required.count", loaded.size(), fMissing, fNeedsApply));
                    statusLabel.setText(fMissing == 0 && fNeedsApply == 0
                            ? DlcFxI18n.tr("dlc_manager.status.all_ready")
                            : (fMissing > 0 ? DlcFxI18n.tr("dlc_manager.required.has_missing") : DlcFxI18n.tr("dlc_manager.required.has_needs_apply")));
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
        }, "dlc-required-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadMissingAsync() {
        if (!requireGameDir()) {
            return;
        }
        List<DlcRequiredEntry> missing = DlcRequiredService.collectMissingDownloads(entries);
        if (missing.isEmpty()) {
            if (forceInstallFlow) {
                forceInstallFlow = false;
                List<DlcRequiredEntry> installTargets = resolveForceInstallTargets();
                if (installTargets.isEmpty()) {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.no_installable"));
                    return;
                }
                startInstallThread(installTargets);
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.no_missing_downloads"));
            return;
        }
        List<DlcRequiredEntry> expanded = DlcRequiredRuntime.collectDependencies(missing);
        List<DlcRequiredEntry> toDownload = DlcRequiredService.collectMissingDownloads(expanded);
        downloadAll(toDownload);
    }

    private void downloadAll(List<DlcRequiredEntry> toDownload) {
        if (!requireGameDir()) {
            return;
        }
        downloadMissingBtn.setDisable(true);
        downloadSelectedBtn.setDisable(true);
        applyBtn.setDisable(true);
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        Thread thread = new Thread(() -> {
            DlcRequiredService.BatchResult result;
            try {
                result = DlcRequiredService.downloadAll(toDownload, new DlcRequiredService.BatchProgressListener() {
                    @Override
                    public void onEntrySuccess(DlcRequiredEntry entry, String fileName, int completed, int total) {
                        Platform.runLater(() -> progressBar.setProgress((double) completed / total));
                    }

                    @Override
                    public void onEntryFailure(DlcRequiredEntry entry, Throwable error, int completed, int total) {
                        Platform.runLater(() -> progressBar.setProgress((double) completed / total));
                    }
                }, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = new DlcRequiredService.BatchResult(toDownload.size(), 0,
                        List.of(new DlcRequiredService.EntryError(null, ex)));
            }
            DlcRequiredService.BatchResult finalResult = result;
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
                if (forceInstallFlow) {
                    forceInstallFlow = false;
                    List<DlcRequiredEntry> installTargets = resolveForceInstallTargets();
                    if (installTargets.isEmpty()) {
                        installTargets = new ArrayList<>(forcedEntries.isEmpty() ? entries : forcedEntries);
                    }
                    startInstallThread(installTargets);
                }
            });
        }, "dlc-required-download");
        thread.setDaemon(true);
        thread.start();
    }

    private List<DlcRequiredEntry> resolveForceInstallTargets() {
        List<DlcRequiredEntry> source = forcedEntries.isEmpty() ? entries : forcedEntries;
        List<DlcRequiredEntry> installTargets = new ArrayList<>();
        for (DlcRequiredEntry entry : source) {
            if (entry != null && DlcRequiredService.isDownloaded(entry) && DlcRequiredRuntime.entryNeedsApply(entry)) {
                installTargets.add(entry);
            }
        }
        if (!installTargets.isEmpty()) {
            return installTargets;
        }
        for (DlcRequiredEntry entry : source) {
            if (entry != null && DlcRequiredService.isDownloaded(entry)) {
                installTargets.add(entry);
            }
        }
        return installTargets;
    }

    private void installAsync() {
        if (!requireGameDir()) {
            return;
        }
        runInstall(false);
    }

    private void runInstall(boolean force) {
        if (!requireGameDir()) {
            return;
        }
        List<DlcRequiredEntry> targets = DlcRequiredService.collectNeedsApply(entries);
        if (targets.isEmpty()) {
            for (DlcRequiredEntry e : entries) {
                if (e != null && DlcRequiredService.isDownloaded(e)) {
                    targets.add(e);
                }
            }
            if (targets.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.no_installable"));
                return;
            }
        }
        final List<DlcRequiredEntry> finalTargets = targets;
        if (force) {
            startInstallThread(finalTargets);
            return;
        }
        DlcFxModalOverlay.show(
                applyBtn.getScene(),
                DlcFxI18n.tr("dlc_manager.required.install_title"),
                DlcFxI18n.tr("dlc_manager.required.install_confirm", finalTargets.size()),
                () -> startInstallThread(finalTargets),
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                () -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.canceled")),
                DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void startInstallThread(List<DlcRequiredEntry> finalTargets) {
        if (!requireGameDir()) {
            return;
        }
        applyBtn.setDisable(true);
        downloadMissingBtn.setDisable(true);
        downloadSelectedBtn.setDisable(true);
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.installing"));
        Thread thread = new Thread(() -> {
            int total = finalTargets.size();
            AtomicLong done = new AtomicLong();
            List<String> errors = new ArrayList<>();
            for (DlcRequiredEntry entry : finalTargets) {
                try {
                    DlcRequiredService.installOne(entry);
                } catch (Throwable t) {
                    errors.add(safeName(entry) + ": " + safeMessage(t));
                }
                long d = done.incrementAndGet();
                Platform.runLater(() -> progressBar.setProgress((double) d / total));
            }
            Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append(DlcFxI18n.tr("dlc_manager.required.installed_count", done.get(), total));
                if (!errors.isEmpty()) {
                    sb.append(DlcFxI18n.tr("dlc_manager.status.failed_count_suffix", errors.size()));
                }
                statusLabel.setText(sb.toString());
                progressBar.setVisible(false);
                refreshBtn.setDisable(false);
                updateActionButtons(false);
                refreshAsync();
            });
        }, "dlc-required-install");
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
        applyBtn.setDisable(disabled);
    }

    private final class RequiredEntryCell extends javafx.scene.control.ListCell<DlcRequiredEntry> {
        private final Label nameLabel = new Label();
        private final Label statusTag = new Label();
        private final Label detailLabel = new Label();
        private final HBox row = new HBox(8, nameLabel, statusTag, detailLabel);

        RequiredEntryCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.muted(detailLabel);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(DlcRequiredEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                DlcFxRichTextRenderer.setFormatted(nameLabel, safeName(item));
                boolean downloaded = DlcRequiredService.isDownloaded(item);
                boolean needsApply = downloaded && DlcRequiredRuntime.entryNeedsApply(item);
                if (!downloaded) {
                    statusTag.setText(DlcFxI18n.tr("dlc_manager.status.missing_download"));
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                } else if (needsApply) {
                    statusTag.setText(DlcFxI18n.tr("dlc_manager.status.needs_install"));
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_WARN);
                } else {
                    statusTag.setText(DlcFxI18n.tr("dlc_manager.status.installed"));
                    DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_OK);
                }
                String fileName = item.getResolvedFileName();
                detailLabel.setText(fileName == null || fileName.isBlank() ? DlcFxI18n.tr("dlc_manager.status.unknown_file_name") : fileName);
                StringBuilder tip = new StringBuilder();
                tip.append(DlcFxI18n.tr("dlc_manager.tooltip.identifier")).append(item.getIdentifier() == null ? "" : item.getIdentifier());
                Path requiredDir = item.getRequiredDir();
                if (requiredDir != null) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.directory")).append(requiredDir);
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
                String applied = item.getAppliedTargetRaw();
                if (applied != null && !applied.isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.target")).append(applied);
                }
                String entryTip = resolveTip(item);
                if (entryTip != null && !entryTip.isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.description")).append(entryTip);
                }
                setTooltip(DlcFxRichTextRenderer.formattedTooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }

    private static String safeName(DlcRequiredEntry entry) {
        String name = entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry.getRequiredFileName();
        }
        if (name == null || name.isBlank()) {
            name = entry.getIdentifier();
        }
        return name == null ? DlcFxI18n.tr("dlc_manager.status.unnamed") : name;
    }

    private static String resolveTip(DlcRequiredEntry entry) {
        if (entry == null || entry.getTips().isEmpty()) {
            return "";
        }
        Map<String, String> tips = entry.getTips();
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = tips.get(lang);
        if (value == null || value.isBlank()) {
            value = tips.get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = tips.values().stream().findFirst().orElse("");
        }
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
