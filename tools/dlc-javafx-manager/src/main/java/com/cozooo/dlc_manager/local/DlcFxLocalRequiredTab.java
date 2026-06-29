package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxStyle;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.HPos;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
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

public final class DlcFxLocalRequiredTab {

    private static final double LIST_ROW_WIDTH_RESERVED = 34;
    private static final double LIST_ITEM_CONTENT_MIN_WIDTH = 620;
    private static final double LIST_GROUP_CONTENT_MIN_WIDTH = 260;
    private static final double LIST_STATUS_WIDTH = 110;
    private static final double LIST_PROGRESS_TEXT_WIDTH = 40;
    private static final double LIST_PROGRESS_WIDTH = 140;
    private static final double LIST_ACTION_WIDTH = 82;

    private final DlcFxPlatformProvider provider;
    private final ObservableList<DlcRequiredEntry> visibleRows = FXCollections.observableArrayList();
    private final ObservableList<DlcRequiredEntry> entries = FXCollections.observableArrayList();
    private final Set<String> selectedEntryIds = new LinkedHashSet<>();
    private final Set<String> activeInstallEntries = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<String> installingEntries = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<String, AtomicBoolean> cancelRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Float> progressByKey = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<RequiredEntryCell> activeItemCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final AtomicLong generation = new AtomicLong(0L);

    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label totalProgressLabel = new Label("");
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.required.refresh"));
    private final Label countLabel = new Label("");
    private final Button installMissingBtn = new Button(DlcFxI18n.tr("dlc_manager.required.download_missing"));
    private final Button installSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.required.install_selected"));
    private ListView<DlcRequiredEntry> listView;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalRequiredTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setMinWidth(220);
        progressBar.setMaxWidth(220);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        totalProgressLabel.setVisible(false);
        totalProgressLabel.setManaged(false);
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
        installMissingBtn.setDisable(true);
        installMissingBtn.setOnAction(e -> installMissingAll(false));
        installSelectedBtn.setDisable(true);
        installSelectedBtn.setOnAction(e -> installSelectedItems());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(installMissingBtn);
        DlcFxStyle.add(installSelectedBtn, "dlc-neutral-button");
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(totalProgressLabel);
        totalProgressLabel.setMinWidth(42);
        totalProgressLabel.setPrefWidth(42);
        totalProgressLabel.setMaxWidth(42);

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
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.getSelectionModel().clearSelection();
        listView.setCellFactory(param -> new RequiredEntryCell());
        HBox listBox = DlcFxListViewSupport.wrap(listView);
        VBox.setVgrow(listBox, Priority.ALWAYS);

        VBox centerBox = new VBox(10, listBox);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        selectAllBtn.setOnAction(e -> toggleAll(true));
        selectNoneBtn.setOnAction(e -> toggleAll(false));
        invertBtn.setOnAction(e -> invertSelection());

        HBox leftActions = new HBox(8, installMissingBtn, installSelectedBtn);
        leftActions.setAlignment(Pos.CENTER_LEFT);
        HBox rightActions = new HBox(8, progressBar, totalProgressLabel, statusLabel);
        rightActions.setAlignment(Pos.CENTER_RIGHT);
        BorderPane actionPanel = new BorderPane();
        actionPanel.setLeft(leftActions);
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
        entries.clear();
        visibleRows.clear();
        selectedEntryIds.clear();
        activeInstallEntries.clear();
        installingEntries.clear();
        cancelRequests.clear();
        progressByKey.clear();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.game_dir_changed"));
        refreshBtn.setDisable(false);
        refreshAsync();
    }

    public void onTabSelected() {
        if (!gameDirSelected || loadedForCurrentGameDir) {
            return;
        }
        refreshAsync();
    }

    public void forceDownloadAndInstall(List<DlcRequiredEntry> missing) {
        if (missing == null || missing.isEmpty()) {
            return;
        }
        entries.setAll(DlcRequiredRuntime.collectDependencies(missing));
        refreshView();
        installMissingAll(true);
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
        installMissingBtn.setDisable(true);
        installSelectedBtn.setDisable(true);
        updateActionButtons();
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.loading"));
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRequiredEntry> loaded = DlcRequiredLoader.load(dlcRoot);
                DlcRequiredService.Status status = DlcRequiredService.inspect(loaded);
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    refreshView();
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.required.count", loaded.size(), status.missingDownloads, status.needsApply));
                    statusLabel.setText(status.missingDownloads == 0 && status.needsApply == 0
                            ? DlcFxI18n.tr("dlc_manager.status.all_ready")
                            : (status.missingDownloads > 0 ? DlcFxI18n.tr("dlc_manager.required.has_missing") : DlcFxI18n.tr("dlc_manager.required.has_needs_apply")));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    installMissingBtn.setDisable(false);
                    installSelectedBtn.setDisable(false);
                    updateActionButtons();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    installMissingBtn.setDisable(false);
                    installSelectedBtn.setDisable(false);
                    updateActionButtons();
                });
            }
        }, "dlc-required-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void installMissingAll(boolean forceInstall) {
        List<DlcRequiredEntry> targets = DlcRequiredService.collectMissingDownloads(entries);
        if (targets.isEmpty()) {
            if (forceInstall) {
                targets = resolveInstallTargets(entries);
            }
            if (targets.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.no_installable"));
                return;
            }
        }
        installEntries(targets, true);
    }

    private void installSelectedItems() {
        if (!requireGameDir() || listView == null) {
            return;
        }
        List<DlcRequiredEntry> selected = new ArrayList<>();
        for (DlcRequiredEntry entry : visibleRows) {
            if (entry != null && isEntrySelected(entry)) {
                selected.add(entry);
            }
        }
        if (selected.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
            return;
        }
        List<DlcRequiredEntry> targets = DlcRequiredService.collectMissingDownloads(
                DlcRequiredRuntime.collectDependencies(selected));
        if (targets.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.selected_downloaded"));
            return;
        }
        installEntries(targets, true);
    }

    private List<DlcRequiredEntry> resolveInstallTargets(List<DlcRequiredEntry> source) {
        List<DlcRequiredEntry> targets = new ArrayList<>();
        for (DlcRequiredEntry entry : source) {
            if (entry == null) {
                continue;
            }
            if (DlcRequiredService.isDownloaded(entry) && DlcRequiredRuntime.entryNeedsApply(entry)) {
                targets.add(entry);
            }
        }
        if (!targets.isEmpty()) {
            return targets;
        }
        for (DlcRequiredEntry entry : source) {
            if (entry != null && DlcRequiredService.isDownloaded(entry)) {
                targets.add(entry);
            }
        }
        return targets;
    }

    private void installEntries(List<DlcRequiredEntry> targets, boolean refreshAfter) {
        if (!requireGameDir()) {
            return;
        }
        long token = generation.incrementAndGet();
        boolean showTotalProgress = targets.size() > 1;
        progressBar.setVisible(showTotalProgress);
        progressBar.setManaged(showTotalProgress);
        progressBar.setProgress(showTotalProgress ? 0 : -1);
        totalProgressLabel.setVisible(showTotalProgress);
        totalProgressLabel.setManaged(showTotalProgress);
        totalProgressLabel.setText(showTotalProgress ? "0%" : "");
        refreshBtn.setDisable(true);
        updateActionButtons();
        for (DlcRequiredEntry entry : targets) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            AtomicBoolean cancel = new AtomicBoolean(false);
            cancelRequests.put(id, cancel);
            activeInstallEntries.add(id);
            progressByKey.put(id, 0f);
        }
        syncVisibleItemCells();
        Thread thread = new Thread(() -> {
            int total = targets.size();
            int done = 0;
            List<String> errors = new ArrayList<>();
            try {
                for (DlcRequiredEntry entry : targets) {
                    if (entry == null) {
                        continue;
                    }
                    String id = entry.getIdentifier();
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    AtomicBoolean cancel = cancelRequests.get(id);
                    if (cancel != null && cancel.get()) {
                        break;
                    }
                    if (isCurrentGeneration(token)) {
                        progressByKey.put(id, 0f);
                        activeInstallEntries.add(id);
                        installingEntries.remove(id);
                        syncVisibleItemCells();
                        updateTotalProgress(targets, token);
                    }
                    if (!DlcRequiredService.isDownloaded(entry)) {
                        DlcRequiredService.downloadOne(entry, (downloadedBytes, totalBytes) -> {
                            if (totalBytes > 0 && isCurrentGeneration(token)) {
                                progressByKey.put(id, Math.min(1f, downloadedBytes / (float) totalBytes));
                                syncVisibleItemCells();
                                updateTotalProgress(targets, token);
                            }
                        }, () -> cancel != null && cancel.get());
                    }
                    if (cancel != null && cancel.get()) {
                        break;
                    }
                    if (isCurrentGeneration(token)) {
                        installingEntries.add(id);
                        progressByKey.put(id, 1f);
                        syncVisibleItemCells();
                        updateTotalProgress(targets, token);
                    }
                    if (DlcRequiredService.installOne(entry)) {
                        DlcRequiredRuntime.updateUserRemoved(entry, false);
                    }
                    done++;
                    if (isCurrentGeneration(token)) {
                        progressByKey.put(id, 1f);
                        installingEntries.remove(id);
                        syncVisibleItemCells();
                        updateTotalProgress(targets, token);
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                errors.add(ex.getMessage());
            } catch (Throwable t) {
                errors.add(safeMessage(t));
            } finally {
                final int completedCount = done;
                final int totalCount = total;
                Platform.runLater(() -> {
                    generation.incrementAndGet();
                    activeInstallEntries.clear();
                    installingEntries.clear();
                    cancelRequests.clear();
                    progressByKey.clear();
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    progressBar.setProgress(0);
                    totalProgressLabel.setVisible(false);
                    totalProgressLabel.setManaged(false);
                    totalProgressLabel.setText("");
                    refreshBtn.setDisable(false);
                    updateActionButtons();
                    syncVisibleItemCells();
                    refreshView();
                    if (!errors.isEmpty()) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", errors.get(0)));
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.installed_count", completedCount, totalCount));
                    }
                    if (refreshAfter) {
                        refreshAsync();
                    }
                });
            }
        }, "dlc-required-install");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateTotalProgress(List<DlcRequiredEntry> targets, long token) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateTotalProgress(targets, token));
            return;
        }
        if (!isCurrentGeneration(token) || targets == null || targets.size() <= 1) {
            return;
        }
        int total = targets.size();
        float sum = 0f;
        for (DlcRequiredEntry entry : targets) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            Float progress = id == null ? null : progressByKey.get(id);
            if (progress != null) {
                sum += Math.max(0f, Math.min(1f, progress));
            }
        }
        double value = Math.max(0d, Math.min(1d, sum / total));
        progressBar.setProgress(value);
        totalProgressLabel.setText(Math.max(0, Math.min(100, Math.round((float) (value * 100d)))) + "%");
    }

    private void toggleEntry(DlcRequiredEntry entry) {
        if (entry == null) {
            return;
        }
        String id = entry.getIdentifier();
        if (id == null || id.isBlank()) {
            return;
        }
        if (activeInstallEntries.contains(id)) {
            return;
        }
        if (DlcRequiredService.isDownloaded(entry) && !DlcRequiredRuntime.entryNeedsApply(entry)) {
            uninstallEntry(entry);
            syncVisibleItemCells();
            return;
        }
        installEntries(List.of(entry), true);
    }

    private void uninstallEntry(DlcRequiredEntry entry) {
        if (entry == null) {
            return;
        }
        List<Path> targets = entry.resolveAppliedTargets();
        String fileName = resolveExistingFileName(entry);
        for (Path target : targets) {
            if (target == null) {
                continue;
            }
            Path targetFile = resolveTarget(target, fileName);
            if (targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile);
                } catch (Throwable ignored) {
                }
            }
        }
        DlcRequiredRuntime.updateUserRemoved(entry, true);
    }

    private boolean requireGameDir() {
        if (!gameDirSelected || DlcConfig.getDlcRoot() == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            updateActionButtons();
            return false;
        }
        return true;
    }

    private void updateActionButtons() {
        refreshBtn.setDisable(!gameDirSelected || progressBar.isVisible());
        installSelectedBtn.setDisable(!gameDirSelected || progressBar.isVisible() || selectedRows().isEmpty());
    }

    private void toggleAll(boolean selected) {
        if (selected) {
            for (DlcRequiredEntry entry : visibleRows) {
                if (entry != null) {
                    selectedEntryIds.add(selectionId(entry));
                }
            }
        } else {
            selectedEntryIds.clear();
        }
        syncVisibleItemCells();
    }

    private void invertSelection() {
        Set<String> snapshot = new java.util.LinkedHashSet<>(selectedEntryIds);
        selectedEntryIds.clear();
        for (DlcRequiredEntry entry : visibleRows) {
            if (entry != null && !snapshot.contains(selectionId(entry))) {
                selectedEntryIds.add(selectionId(entry));
            }
        }
        syncVisibleItemCells();
    }

    private List<DlcRequiredEntry> selectedRows() {
        List<DlcRequiredEntry> rows = new ArrayList<>();
        for (DlcRequiredEntry entry : visibleRows) {
            if (entry != null && isEntrySelected(entry)) {
                rows.add(entry);
            }
        }
        return rows;
    }

    private void refreshView() {
        List<DlcRequiredEntry> rows = new ArrayList<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry != null && !entry.isHidden()) {
                rows.add(entry);
            }
        }
        visibleRows.setAll(rows);
        if (listView != null) {
            listView.refresh();
        }
        updateActionButtons();
    }

    private void syncVisibleItemCells() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::syncVisibleItemCells);
            return;
        }
        for (RequiredEntryCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.syncItemState();
            }
        }
    }

    private boolean isEntrySelected(DlcRequiredEntry entry) {
        return entry != null && selectedEntryIds.contains(selectionId(entry));
    }

    private void setSelected(DlcRequiredEntry entry, boolean selected) {
        if (entry == null) {
            return;
        }
        String id = selectionId(entry);
        if (selected) {
            selectedEntryIds.add(id);
        } else {
            selectedEntryIds.remove(id);
        }
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

    private String selectionId(DlcRequiredEntry entry) {
        if (entry == null) {
            return "";
        }
        String id = entry.getIdentifier();
        if (id != null && !id.isBlank()) {
            return id;
        }
        String name = entry.getRequiredFileName();
        return name == null ? "" : name;
    }

    private boolean isCurrentGeneration(long token) {
        return generation.get() == token;
    }

    private String safeName(DlcRequiredEntry entry) {
        String name = entry == null ? null : entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry == null ? null : entry.getRequiredFileName();
        }
        if (name == null || name.isBlank()) {
            name = entry == null ? null : entry.getIdentifier();
        }
        return name == null ? DlcFxI18n.tr("dlc_manager.status.unnamed") : name;
    }

    private String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        return msg == null || msg.isBlank() ? "unknown" : msg;
    }

    private String resolveExistingFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = entry.getResolvedFileName();
        if (fileName != null && !fileName.isBlank()) {
            Path target = entry.resolveRequiredPath(fileName);
            if (target != null && Files.exists(target)) {
                return fileName;
            }
        }
        String base = entry.getBaseName();
        if (base == null || base.isBlank() || entry.getRequiredDir() == null) {
            return fileName;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".part")) {
                    continue;
                }
                if (!name.endsWith(".dc")) {
                    return name;
                }
            }
        } catch (IOException ignored) {
        }
        return fileName;
    }

    private Path resolveTarget(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
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

    private static ColumnConstraints column(double min, double pref, Priority priority) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setMinWidth(min);
        cc.setPrefWidth(pref);
        cc.setHgrow(priority);
        return cc;
    }

    private final class RequiredEntryCell extends ListCell<DlcRequiredEntry> {
        private final CheckBox selectBox = new CheckBox();
        private final Label nameLabel = new Label();
        private final Label statusTag = new Label();
        private final Label progressText = new Label();
        private final ProgressBar rowProgress = new ProgressBar(0);
        private final Button actionButton = new Button();
        private final Region actionSpacer = new Region();
        private final Region trailingSpacer = new Region();
        private final HBox progressBox = new HBox(6, progressText, rowProgress);
        private final GridPane itemGrid = new GridPane();
        private final HBox itemRow = new HBox(0, itemGrid);
        private final StackPane itemPane = new StackPane(itemRow);
        private final Rectangle clip = new Rectangle();

        RequiredEntryCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.muted(progressText);
            DlcFxStyle.add(this, "dlc-row-item");
            DlcFxStyle.add(selectBox, "dlc-row-select");
            DlcFxStyle.add(actionButton, "dlc-neutral-button");
            actionButton.setAlignment(Pos.CENTER);
            actionButton.setMinWidth(LIST_ACTION_WIDTH);
            actionButton.setPrefWidth(LIST_ACTION_WIDTH);
            actionButton.setMaxWidth(LIST_ACTION_WIDTH);
            rowProgress.setPrefWidth(LIST_PROGRESS_WIDTH);
            rowProgress.setMinWidth(LIST_PROGRESS_WIDTH - LIST_PROGRESS_TEXT_WIDTH - 6);
            rowProgress.setMaxWidth(LIST_PROGRESS_WIDTH - LIST_PROGRESS_TEXT_WIDTH - 6);
            rowProgress.setVisible(false);
            rowProgress.setManaged(true);
            progressBox.setAlignment(Pos.CENTER_RIGHT);
            actionSpacer.setMinWidth(0);
            actionSpacer.setPrefWidth(0);
            actionSpacer.setMaxWidth(Double.MAX_VALUE);
            trailingSpacer.setMinWidth(0);
            trailingSpacer.setPrefWidth(0);
            trailingSpacer.setMaxWidth(Double.MAX_VALUE);
            itemGrid.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setHgap(10);
            itemGrid.setMaxWidth(Double.MAX_VALUE);
            itemGrid.add(selectBox, 0, 0);
            itemGrid.add(nameLabel, 1, 0);
            itemGrid.add(statusTag, 2, 0);
            itemGrid.add(trailingSpacer, 3, 0);
            itemGrid.add(progressBox, 4, 0);
            itemGrid.add(actionSpacer, 5, 0);
            itemGrid.add(actionButton, 6, 0);
            GridPane.setHgrow(nameLabel, Priority.ALWAYS);
            GridPane.setHgrow(trailingSpacer, Priority.ALWAYS);
            GridPane.setHgrow(actionSpacer, Priority.ALWAYS);
            GridPane.setHalignment(actionButton, HPos.RIGHT);
            GridPane.setHgrow(progressBox, Priority.NEVER);
            itemGrid.getColumnConstraints().addAll(
                    column(28, 28, Priority.NEVER),
                    column(340, 540, Priority.ALWAYS),
                    column(110, 110, Priority.NEVER),
                    column(0, 0, Priority.ALWAYS),
                    column(LIST_PROGRESS_WIDTH, LIST_PROGRESS_WIDTH, Priority.NEVER),
                    column(0, 0, Priority.ALWAYS),
                    column(LIST_ACTION_WIDTH, LIST_ACTION_WIDTH, Priority.NEVER)
            );
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemRow.setMaxWidth(Double.MAX_VALUE);
            itemPane.setAlignment(Pos.CENTER_LEFT);
            itemPane.setMaxWidth(Double.MAX_VALUE);
            itemPane.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemPane.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_ITEM_CONTENT_MIN_WIDTH));
            HBox.setHgrow(itemGrid, Priority.ALWAYS);
            clip.widthProperty().bind(itemPane.widthProperty());
            clip.heightProperty().bind(itemPane.heightProperty());
            itemPane.setClip(clip);
            selectBox.setOnMouseClicked(e -> {
                DlcRequiredEntry entry = getItem();
                if (entry == null) {
                    e.consume();
                    return;
                }
                setSelected(entry, selectBox.isSelected());
                syncVisibleItemCells();
                updateActionButtons();
                e.consume();
            });
            itemRow.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (!isInside(e.getTarget(), selectBox) && !isInside(e.getTarget(), actionButton)) {
                    e.consume();
                }
            });
            itemRow.setOnMouseEntered(e -> {
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
            });
            itemRow.setOnMouseExited(e -> {
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
            });
            actionButton.setOnAction(e -> {
                DlcRequiredEntry entry = getItem();
                if (entry != null) {
                    toggleEntry(entry);
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(DlcRequiredEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            activeItemCells.remove(this);
            if (empty || entry == null) {
                getStyleClass().remove("dlc-row-item");
                resetRow();
                setGraphic(null);
                setText(null);
                return;
            }
            resetRow();
            activeItemCells.add(this);
            if (!getStyleClass().contains("dlc-row-item")) {
                getStyleClass().add("dlc-row-item");
            }
            selectBox.setVisible(true);
            selectBox.setManaged(true);
            selectBox.setDisable(activeInstallEntries.contains(entry.getIdentifier()));
            selectBox.setSelected(isEntrySelected(entry));
            DlcFxRichTextRenderer.setFormatted(nameLabel, safeName(entry));
            boolean downloaded = DlcRequiredService.isDownloaded(entry);
            boolean needsApply = downloaded && DlcRequiredRuntime.entryNeedsApply(entry);
            String progressId = entry.getIdentifier();
            boolean active = activeInstallEntries.contains(progressId);
            boolean installing = installingEntries.contains(progressId);
            Float progress = progressByKey.get(progressId);
            boolean showProgress = active || progressByKey.containsKey(progressId);
            if (installing || (active && progress != null && progress >= 1f)) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.installing"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_WARN);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setVisible(true);
                actionButton.setManaged(true);
                actionButton.setDisable(true);
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.installing"));
                DlcFxStyle.add(actionButton, "dlc-neutral-button");
            } else if (!downloaded) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.missing_download"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setVisible(true);
                actionButton.setManaged(true);
                actionButton.setDisable(active);
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.install"));
                DlcFxStyle.add(actionButton, "dlc-neutral-button");
            } else if (needsApply) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.needs_install"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_WARN);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setVisible(true);
                actionButton.setManaged(true);
                actionButton.setDisable(active);
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.install"));
                setNeutralActionButton();
            } else {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.installed"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_OK);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setVisible(true);
                actionButton.setManaged(true);
                actionButton.setDisable(active);
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.uninstall"));
                setDangerActionButton();
            }
            rowProgress.setVisible(showProgress);
            rowProgress.setManaged(showProgress);
            rowProgress.setProgress(progress == null ? 0 : progress);
            progressText.setVisible(showProgress);
            progressText.setManaged(showProgress);
            progressText.setText(progress == null ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%");
            getStyleClass().remove("dlc-row-selected");
            if (selectBox.isSelected()) {
                getStyleClass().add("dlc-row-selected");
            }
            setTooltip(DlcFxRichTextRenderer.formattedTooltip(buildTooltip(entry)));
            setGraphic(itemPane);
        }

        private String buildTooltip(DlcRequiredEntry entry) {
            if (entry == null) {
                return "";
            }
            StringBuilder tip = new StringBuilder();
            tip.append(DlcFxI18n.tr("dlc_manager.tooltip.identifier")).append(entry.getIdentifier() == null ? "" : entry.getIdentifier());
            Path requiredDir = entry.getRequiredDir();
            if (requiredDir != null) {
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.directory")).append(requiredDir);
            }
            if (!entry.getDirectUrls().isEmpty()) {
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.direct_url")).append(entry.getDirectUrls().get(0));
            }
            String applied = entry.getAppliedTargetRaw();
            if (applied != null && !applied.isBlank()) {
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.target")).append(applied);
            }
            String entryTip = resolveTip(entry);
            if (entryTip != null && !entryTip.isBlank()) {
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.description")).append(entryTip);
            }
            return tip.toString();
        }

        private void syncItemState() {
            DlcRequiredEntry entry = getItem();
            if (entry == null) {
                return;
            }
            String progressId = entry.getIdentifier();
            boolean downloaded = DlcRequiredService.isDownloaded(entry);
            boolean needsApply = downloaded && DlcRequiredRuntime.entryNeedsApply(entry);
            boolean installing = installingEntries.contains(progressId);
            boolean active = activeInstallEntries.contains(progressId);
            Float progress = progressByKey.get(progressId);
            boolean showProgress = active || progressByKey.containsKey(progressId);
            selectBox.setSelected(isEntrySelected(entry));
            if (installing || (active && progress != null && progress >= 1f)) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.installing"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_WARN);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.installing"));
                setNeutralActionButton();
            } else if (!downloaded) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.missing_download"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_ERROR);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.install"));
                setNeutralActionButton();
            } else if (needsApply) {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.needs_install"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_WARN);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.install"));
                setNeutralActionButton();
            } else {
                statusTag.setText(DlcFxI18n.tr("dlc_manager.status.installed"));
                DlcFxStyle.chip(statusTag, DlcFxStyle.CHIP_OK);
                statusTag.setStyle("-fx-text-fill: white;");
                actionButton.setText(DlcFxI18n.tr("dlc_manager.required.uninstall"));
                setDangerActionButton();
            }
            actionButton.setDisable(active);
            rowProgress.setVisible(showProgress);
            rowProgress.setManaged(showProgress);
            rowProgress.setProgress(progress == null ? 0 : progress);
            progressText.setVisible(showProgress);
            progressText.setManaged(showProgress);
            progressText.setText(progress == null ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%");
            getStyleClass().remove("dlc-row-selected");
            if (selectBox.isSelected()) {
                getStyleClass().add("dlc-row-selected");
            }
        }

        private void resetRow() {
            itemPane.setMinHeight(Region.USE_PREF_SIZE);
            itemPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setOpacity(1);
            itemPane.setClip(clip);
        }

        private void setNeutralActionButton() {
            actionButton.getStyleClass().removeAll("danger", "dlc-neutral-button");
            DlcFxStyle.add(actionButton, "dlc-neutral-button");
        }

        private void setDangerActionButton() {
            actionButton.getStyleClass().removeAll("danger", "dlc-neutral-button");
            DlcFxStyle.danger(actionButton);
        }

    }
}
