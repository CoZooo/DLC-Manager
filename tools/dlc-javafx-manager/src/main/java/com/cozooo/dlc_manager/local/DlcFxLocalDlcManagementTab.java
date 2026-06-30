package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxStyle;
import com.cozooo.dlc_manager.ui.DlcFxTooltips;

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.core.DlcType;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class DlcFxLocalDlcManagementTab {

    private static final double LIST_ROW_WIDTH_RESERVED = 34;
    private static final double LIST_ITEM_CONTENT_MIN_WIDTH = 652;
    private static final double LIST_PATH_CONTENT_MIN_WIDTH = 360;

    private final DlcFxPlatformProvider provider;

    private final ObservableList<DlcItem> allItems = FXCollections.observableArrayList();
    private final ObservableList<DlcRow> visibleRows = FXCollections.observableArrayList();
    private final Set<String> selectedItemIds = new LinkedHashSet<>();
    private final Set<String> collapsedFolders = new LinkedHashSet<>();
    private final Set<String> animatingFolders = new LinkedHashSet<>();
    private final Set<DlcItemCell> activeItemCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<DlcItemCell> activePathCells = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.ready"));
    private final TextField searchField = new TextField();
    private final ComboBox<DlcTypeOption> typeFilter = new ComboBox<>();
    private ListView<DlcRow> listView;
    private Button rescanBtn;
    private Button openDlcDirBtn;
    private Button cancelBtn;
    private Button applyBtn;
    private Button enableSelectedBtn;
    private Button disableSelectedBtn;
    private Label pendingWarningLabel;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private Runnable capturePresetSnapshot = () -> {};
    private Runnable restorePresetSnapshot = () -> {};
    private Runnable resetPresetSnapshot = () -> {};
    private Runnable applyPresetOwnedDlcOverrides = () -> {};

    public DlcFxLocalDlcManagementTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);
    }

    public void setGameDirSelected(boolean selected) {
        this.gameDirSelected = selected;
    }

    public void setPresetSnapshotActions(Runnable capturePresetSnapshot,
                                         Runnable restorePresetSnapshot,
                                         Runnable resetPresetSnapshot,
                                         Runnable applyPresetOwnedDlcOverrides) {
        this.capturePresetSnapshot = capturePresetSnapshot == null ? () -> {} : capturePresetSnapshot;
        this.restorePresetSnapshot = restorePresetSnapshot == null ? () -> {} : restorePresetSnapshot;
        this.resetPresetSnapshot = resetPresetSnapshot == null ? () -> {} : resetPresetSnapshot;
        this.applyPresetOwnedDlcOverrides = applyPresetOwnedDlcOverrides == null ? () -> {} : applyPresetOwnedDlcOverrides;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_dlc"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        rescanBtn = new Button(DlcFxI18n.tr("dlc_manager.local.scan_dlc"));
        rescanBtn.setDisable(!gameDirSelected);
        rescanBtn.setOnAction(e -> {
            if (gameDirSelected) {
                scanAsync();
            }
        });

        openDlcDirBtn = new Button(DlcFxI18n.tr("dlc_manager.local.open_dlc_dir"));
        openDlcDirBtn.setDisable(!gameDirSelected);
        openDlcDirBtn.setOnAction(e -> openDlcDirectory());
        DlcFxStyle.primary(rescanBtn);
        DlcFxStyle.muted(countLabel);

        searchField.setPromptText(DlcFxI18n.tr("dlc_manager.local.search_prompt"));
        searchField.setPrefColumnCount(30);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter(n, selectedTypeFilter()));

        typeFilter.getItems().add(DlcTypeOption.all());
        for (DlcType t : DlcType.values()) {
            if (!isVisibleDlcType(t)) {
                continue;
            }
            typeFilter.getItems().add(DlcTypeOption.of(t));
        }
        typeFilter.setPromptText(DlcFxI18n.tr("dlc_manager.local.all_types"));
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcTypeOption option) { return typeText(option); }
            @Override public DlcTypeOption fromString(String s) { return DlcTypeOption.all(); }
        });
        typeFilter.setButtonCell(new DlcTypeButtonCell(typeFilter));
        typeFilter.setCellFactory(param -> new DlcTypePopupCell());
        typeFilter.setValue(DlcTypeOption.all());
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter(searchField.getText(), typeOf(n)));

        Label searchLabel = new Label(DlcFxI18n.tr("dlc_manager.local.filter"));
        Label typeLabel = new Label(DlcFxI18n.tr("dlc_manager.local.type"));
        DlcFxStyle.muted(searchLabel);
        DlcFxStyle.muted(typeLabel);
        HBox filterBox = new HBox(8, searchLabel, searchField, typeLabel, typeFilter);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.filterBar(filterBox);

        listView = new ListView<>(visibleRows);
        DlcFxStyle.add(listView, "dlc-dlc-list");
        listView.setCellFactory(param -> new DlcItemCell());
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        HBox listBox = DlcFxListViewSupport.wrap(listView);
        VBox.setVgrow(listBox, Priority.ALWAYS);

        VBox centerBox = new VBox(10, filterBox, listBox);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        selectAllBtn.setOnAction(e -> {
            selectedItemIds.clear();
            for (DlcRow row : visibleRows) {
                if (row.type == DlcRowType.ITEM && row.item != null) {
                    selectedItemIds.add(selectionId(row.item));
                }
            }
            listView.refresh();
            updateActionState();
        });
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        selectNoneBtn.setOnAction(e -> {
            selectedItemIds.clear();
            listView.refresh();
            updateActionState();
        });
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        invertBtn.setOnAction(e -> invertVisibleSelection());
        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);

        cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.local.cancel_changes"));
        cancelBtn.setOnAction(e -> resetChanges());
        applyBtn = new Button(DlcFxI18n.tr("dlc_manager.local.apply_changes"));
        applyBtn.setOnAction(e -> applyAsync());
        DlcFxStyle.primary(applyBtn);

        enableSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.local.enable_selected"));
        enableSelectedBtn.setOnAction(e -> handleBatchEnable(selectedItems(), true));
        DlcFxStyle.primary(enableSelectedBtn);
        disableSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.local.disable_selected"));
        disableSelectedBtn.setOnAction(e -> disableItemsWithDependents(selectedItems()));

        Region topSpacer2 = new Region();
        HBox.setHgrow(topSpacer2, Priority.ALWAYS);
        HBox topBox = new HBox(10, rescanBtn, openDlcDirBtn, countLabel, topSpacer2, selectionActions);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        HBox applyActions = new HBox(8, applyBtn, cancelBtn, enableSelectedBtn, disableSelectedBtn);
        applyActions.setAlignment(Pos.CENTER_LEFT);
        pendingWarningLabel = new Label(DlcFxI18n.tr("dlc_manager.pending.unapplied_changes"));
        DlcFxStyle.add(pendingWarningLabel, "dlc-pending-warning");
        pendingWarningLabel.setVisible(false);
        pendingWarningLabel.setManaged(false);
        HBox statusRow = new HBox(18, progressBar, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        VBox actionPanel = new VBox(new HBox(18, applyActions, pendingWarningLabel, statusRow));
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        updateActionState();
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        if (rescanBtn != null) {
            rescanBtn.setDisable(false);
        }
        if (openDlcDirBtn != null) {
            openDlcDirBtn.setDisable(false);
        }
        if (cancelBtn != null) {
            cancelBtn.setDisable(true);
        }
        if (applyBtn != null) {
            applyBtn.setDisable(true);
        }
        allItems.clear();
        visibleRows.clear();
        selectedItemIds.clear();
        collapsedFolders.clear();
        animatingFolders.clear();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.game_dir_changed"));
        refreshTypeFilterItems();
        updateActionState();
    }

    public void onTabSelected() {
        if (!gameDirSelected) {
            return;
        }
        if (loadedForCurrentGameDir) {
            syncPresetOverridesIntoItems();
            refreshRowsAfterStateChange();
            return;
        }
        scanAsync();
    }

    public void scanSelectedGameDir() {
        scanAsync();
    }

    private void openDlcDirectory() {
        List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
        if (roots.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.no_dlc_root"));
            return;
        }
        java.nio.file.Path dlcRoot = roots.get(0);
        if (!java.nio.file.Files.exists(dlcRoot)) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.dlc_dir_missing", dlcRoot));
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(dlcRoot.toFile());
        } catch (Exception ex) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.open_failed", safeMessage(ex)));
        }
    }

    private void scanAsync() {
        if (!gameDirSelected) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            return;
        }
        loadedForCurrentGameDir = true;
        refreshTypeFilterItems();
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scanning"));
        Thread thread = new Thread(() -> {
            try {
                List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
                StringBuilder diag = new StringBuilder();
                diag.append(DlcFxI18n.tr("dlc_manager.local.diag.game_dir", provider.gameDir())).append('\n');
                diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dlc_root_raw", DlcConfig.getDlcRootRaw())).append('\n');
                boolean[] anyRootExists = {false};
                for (java.nio.file.Path root : roots) {
                    boolean exists = java.nio.file.Files.exists(root);
                    if (exists) anyRootExists[0] = true;
                    diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dlc_root", root, exists)).append('\n');
                    java.nio.file.Path infoDir = root.resolve("info");
                    boolean infoExists = java.nio.file.Files.isDirectory(infoDir);
                    diag.append(DlcFxI18n.tr("dlc_manager.local.diag.info_dir", infoDir, infoExists)).append('\n');
                    if (infoExists) {
                        try (var stream = java.nio.file.Files.list(infoDir)) {
                            var items = stream.toList();
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.info_file_count", items.size())).append('\n');
                            int dcCount = 0;
                            int otherCount = 0;
                            for (var item : items) {
                                String fn = item.getFileName().toString();
                                if (fn.toLowerCase().endsWith(".dc")) {
                                    dcCount++;
                                    try {
                                        var obj = com.cozooo.dlc_manager.core.DlcDcConfigIO.read(item,
                                            com.cozooo.dlc_manager.core.DlcDcConfigIO.Layout.DLC_STRUCTURED);
                                        if (obj != null) {
                                            String fName = com.cozooo.dlc_manager.core.DlcDcConfigIO.getStringIgnoreCase(obj, "fileName");
                                            String fType = com.cozooo.dlc_manager.core.DlcDcConfigIO.getStringIgnoreCase(obj, "type");
                                            String fTarget = com.cozooo.dlc_manager.core.DlcDcConfigIO.getStringIgnoreCase(obj, "appliedTarget");
                                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dc_summary", fn, fName, fType, fTarget)).append('\n');
                                        } else {
                                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dc_parse_failed", fn)).append('\n');
                                        }
                                    } catch (Exception ex) {
                                        diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dc_read_failed", fn, ex.getMessage())).append('\n');
                                    }
                                } else {
                                    otherCount++;
                                    diag.append(DlcFxI18n.tr("dlc_manager.local.diag.non_dc_file", fn)).append('\n');
                                }
                            }
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dc_config_count", dcCount)).append('\n');
                            if (otherCount > 0) {
                                diag.append(DlcFxI18n.tr("dlc_manager.local.diag.other_file_count", otherCount)).append('\n');
                            }
                        } catch (Exception ex) {
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.info_read_failed", ex.getMessage())).append('\n');
                        }
                    }
                    if (exists) {
                        try (var stream = java.nio.file.Files.walk(root)) {
                            var allFiles = stream.toList();
                            long jars = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".jar")).count();
                            long zips = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".zip")).count();
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.jar_count", jars)).append('\n');
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.zip_count", zips)).append('\n');
                            java.nio.file.Path infoDir0 = root.resolve("info");
                            for (var p : allFiles) {
                                if (java.nio.file.Files.isRegularFile(p) && !p.startsWith(infoDir0)) {
                                    String rel = root.relativize(p).toString().replace('\\', '/');
                                    diag.append(DlcFxI18n.tr("dlc_manager.local.diag.file", rel)).append('\n');
                                }
                            }
                        } catch (Exception ex) {
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.walk_failed", ex.getMessage())).append('\n');
                        }
                    }
                }

                DlcScanner scanner = new DlcScanner();
                Map<DlcType, List<DlcItem>> scanned = scanner.scan(true);
                List<DlcItem> flat = new ArrayList<>();
                for (DlcType type : DlcType.values()) {
                    if (!isVisibleDlcType(type)) {
                        continue;
                    }
                    List<DlcItem> group = scanned.get(type);
                    if (group != null) {
                        flat.addAll(group);
                    }
                }
                if (DlcConfig.isPresetFuncOn()) {
                    DlcPresetRuntimeState.reload();
                } else {
                    DlcPresetRuntimeState.clearAll();
                }
                for (DlcItem item : flat) {
                    if (item == null) {
                        continue;
                    }
                    boolean enabled = item.isEnabled();
                    DlcRuntimeState.ensureBaseline(item.getId(), enabled);
                    Boolean override = DlcRuntimeState.getOverride(item.getId());
                    if (override != null) {
                        item.setEnabled(override);
                    }
                }
                DlcRuntimeState.updateItemIndex(flat);
                DlcPresetRuntimeState.applyOverridesToRuntime();
                for (DlcItem item : flat) {
                    if (item == null) {
                        continue;
                    }
                    Boolean override = DlcRuntimeState.getOverride(item.getId());
                    if (override != null) {
                        item.setEnabled(override);
                    }
                }
                final int total = flat.size();
                final String diagText = diag.toString();
                System.out.println("[DLC-Scan-Debug]\n" + diagText + "total=" + total);
                Platform.runLater(() -> {
                    allItems.setAll(flat);
                    selectedItemIds.removeIf(id -> flat.stream().noneMatch(item -> id.equals(selectionId(item))));
                    rebuildRows();
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", total));
                    progressBar.setVisible(false);
                    if (total == 0) {
                        String msg = anyRootExists[0]
                            ? DlcFxI18n.tr("dlc_manager.local.no_dlc_items")
                            : DlcFxI18n.tr("dlc_manager.local.no_dlc_dir");
                        statusLabel.setText(msg);
                        statusLabel.setTooltip(DlcFxTooltips.plain(diagText));
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scan_done"));
                        statusLabel.setTooltip(DlcFxTooltips.plain(diagText));
                    }
                    updateActionState();
                });
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scan_failed", safeMessage(t)));
                    updateActionState();
                });
            }
        }, "dlc-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        updateActionState();
        Thread thread = new Thread(() -> {
            try {
                DlcApplier.ApplyResult result = DlcApplier.apply(new ArrayList<>(allItems), DlcApplyMode.RESTART_ONLY);
                DlcStateConfig.save();
                if (DlcConfig.isPresetFuncOn()) {
                    DlcPresetRuntimeState.syncStatusFromRuntime();
                    resetPresetSnapshot.run();
                }
                for (DlcItem item : allItems) {
                    if (item == null) {
                        continue;
                    }
                    DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled());
                    DlcRuntimeState.clearOverride(item.getId());
                }
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    rebuildRows();
                    List<String> failureMessages = applyFailureMessages(result);
                    StringBuilder sb = new StringBuilder(DlcFxI18n.tr("dlc_manager.status.apply_done"));
                    if (!failureMessages.isEmpty()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.status.message_count_suffix", failureMessages.size()));
                    }
                    statusLabel.setText(sb.toString());
                    if (!failureMessages.isEmpty()) {
                        StringBuilder tip = new StringBuilder();
                        for (String msg : failureMessages) {
                            if (tip.length() > 0) tip.append('\n');
                            tip.append(msg);
                        }
                        statusLabel.setTooltip(DlcFxRichTextRenderer.formattedTooltip(tip.toString()));
                    } else {
                        statusLabel.setTooltip(null);
                    }
                    updateActionState();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                    updateActionState();
                });
            }
        }, "dlc-apply");
        thread.setDaemon(true);
        thread.start();
    }

    public void applyPendingChanges() {
        if (hasPendingChanges()) {
            applyAsync();
        }
    }

    private List<String> applyFailureMessages(DlcApplier.ApplyResult result) {
        if (result == null || result.messages() == null || result.messages().isEmpty()) {
            return List.of();
        }
        List<String> failures = new ArrayList<>();
        for (String message : result.messages()) {
            if (message != null && message.startsWith("Failed to ")) {
                failures.add(message);
            }
        }
        return failures;
    }

    private void resetChanges() {
        restorePresetSnapshot.run();
        for (DlcItem item : allItems) {
            if (item == null) {
                continue;
            }
            Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
            boolean enabled = baseline == null ? item.isEnabled() : baseline;
            item.setEnabled(enabled);
        }
        DlcRuntimeState.clearOverrides();
        syncPresetStatusesFromCurrentItems();
        rebuildRows();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.cancel_done"));
        updateActionState();
    }

    public void cancelPendingChanges() {
        resetChanges();
    }

    public List<DlcItem> currentItemsSnapshot() {
        return List.copyOf(allItems);
    }

    private DlcType currentTypeFilter;
    private String currentSearchText = "";

    private void refreshTypeFilterItems() {
        DlcType selected = selectedTypeFilter();
        typeFilter.getItems().clear();
        typeFilter.getItems().add(DlcTypeOption.all());
        for (DlcType type : DlcType.values()) {
            if (isVisibleDlcType(type)) {
                typeFilter.getItems().add(DlcTypeOption.of(type));
            }
        }
        typeFilter.setValue(isVisibleDlcType(selected) ? DlcTypeOption.of(selected) : DlcTypeOption.all());
    }

    private void handleBatchEnable(List<DlcItem> targets, boolean enabled) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        List<DlcItem> active = new ArrayList<>();
        for (DlcItem item : targets) {
            if (item != null) {
                active.add(item);
            }
        }
        if (active.isEmpty()) {
            return;
        }
        if (!enabled) {
            disableItemsWithDependents(active);
            return;
        }
        DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(active);
        if (!conflicts.getLines().isEmpty()) {
            showBatchConflictPrompt(conflicts.getLines(), active);
            return;
        }
        DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(active);
        if (!deps.getLines().isEmpty()) {
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), active);
            return;
        }
        if (active.stream().anyMatch(DlcItem::isAlarm)) {
            showAlarmPrompt(active);
            return;
        }
        applyBatchEnable(active, true);
    }

    private void applyBatchEnable(List<DlcItem> items, boolean enabled) {
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            item.setEnabled(enabled);
            DlcRuntimeState.setOverride(item.getId(), enabled);
        }
        selectedItemIds.clear();
        refreshRowsAfterStateChange();
    }

    private void showBatchConflictPrompt(List<DlcRuntimeState.ConflictLine> lines, List<DlcItem> targets) {
        DlcFxModalOverlay.show(
                applyBtn.getScene(),
                DlcFxI18n.tr("dlc_manager.batch.conflicts_title"),
                DlcFxRichTextRenderer.formatted(buildConflictMessage(lines)),
                () -> {
                    List<DlcItem> filtered = filterConflictTargets(targets, lines);
                    if (!filtered.isEmpty()) {
                        continueBatchEnable(filtered);
                    }
                },
                DlcFxI18n.tr("dlc_manager.batch.conflicts_skip_enable"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.common.cancel")
        );
    }

    private void showBatchDependencyPrompt(List<DlcRuntimeState.DependencyLine> lines, List<DlcItem> depsToEnable, List<DlcItem> targets) {
        StringBuilder message = new StringBuilder();
        for (DlcRuntimeState.DependencyLine line : lines) {
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append(line.getItemName()).append(" -> ").append(line.getDependencyName());
        }
        DlcFxModalOverlay.show(
                applyBtn.getScene(),
                DlcFxI18n.tr("dlc_manager.batch.dependencies_title"),
                DlcFxRichTextRenderer.formatted(message.toString()),
                () -> {
                    List<DlcItem> planned = new ArrayList<>();
                    if (depsToEnable != null) {
                        planned.addAll(depsToEnable);
                    }
                    if (targets != null) {
                        planned.addAll(targets);
                    }
                    DlcRuntimeState.ConflictBatchResult plannedConflicts = DlcRuntimeState.collectConflicts(planned);
                    if (!plannedConflicts.getLines().isEmpty()) {
                        showBatchConflictPrompt(plannedConflicts.getLines(), planned);
                        return;
                    }
                    continueBatchEnable(planned);
                },
                DlcFxI18n.tr("dlc_manager.batch.enable_deps", DlcFxI18n.tr("dlc_manager.scope.group")),
                () -> {},
                DlcFxI18n.tr("dlc_manager.common.cancel")
        );
    }

    private void showAlarmPrompt(List<DlcItem> items) {
        DlcFxModalOverlay.show(
                applyBtn.getScene(),
                DlcFxI18n.tr("dlc_manager.alarm.title"),
                DlcFxRichTextRenderer.formatted(buildAlarmTip(items)),
                () -> applyBatchEnable(items, true),
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void continueBatchEnable(List<DlcItem> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(targets);
        if (!deps.getLines().isEmpty()) {
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets);
            return;
        }
        List<DlcItem> alarmItems = targets.stream().filter(DlcItem::isAlarm).toList();
        if (!alarmItems.isEmpty()) {
            showAlarmPrompt(alarmItems);
            return;
        }
        applyBatchEnable(targets, true);
    }

    private List<DlcItem> filterConflictTargets(List<DlcItem> targets, List<DlcRuntimeState.ConflictLine> lines) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        if (lines == null || lines.isEmpty()) {
            return targets;
        }
        java.util.Set<String> targetIds = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item != null && item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                targetIds.add(item.getIdentifier());
            }
        }
        java.util.Set<String> skipped = new java.util.HashSet<>();
        for (DlcRuntimeState.ConflictLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getLeftName() != null && targetIds.contains(line.getLeftName())) {
                skipped.add(line.getLeftName());
            }
            if (line.getRightName() != null && targetIds.contains(line.getRightName())) {
                skipped.add(line.getRightName());
            }
        }
        if (skipped.isEmpty()) {
            return targets;
        }
        List<DlcItem> filtered = new ArrayList<>();
        for (DlcItem item : targets) {
            if (item == null) {
                continue;
            }
            String id = item.getIdentifier();
            if (id == null || !skipped.contains(id)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private void disableItemsWithDependents(List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<DlcItem> toDisable = new java.util.LinkedHashSet<>();
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            toDisable.addAll(DlcRuntimeState.collectDependentsRecursive(item));
        }
        toDisable.addAll(DlcRuntimeState.collectHiddenDependenciesToDisable(toDisable));
        applyBatchEnable(new ArrayList<>(toDisable), false);
    }

    private String buildAlarmTip(List<DlcItem> items) {
        StringBuilder sb = new StringBuilder();
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(item.getName() == null ? item.getIdentifier() : item.getName());
        }
        return sb.toString();
    }

    private String buildConflictMessage(List<DlcRuntimeState.ConflictLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (DlcRuntimeState.ConflictLine line : lines) {
            if (line == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.getLeftName()).append(" <-> ").append(line.getRightName());
        }
        return sb.toString();
    }

    private boolean isVisibleDlcType(DlcType type) {
        return type != DlcType.DLC_PACK
            && (type != DlcType.TACZ_PACK || provider.hasTaczSupportJar());
    }

    void applyFilter(String searchText, DlcType typeFilter) {
        currentSearchText = searchText == null ? "" : searchText.toLowerCase();
        currentTypeFilter = typeFilter;
        rebuildRows();
    }

    private void invertVisibleSelection() {
        if (listView == null) {
            return;
        }
        for (DlcRow row : visibleRows) {
            if (row.type != DlcRowType.ITEM || row.item == null) {
                continue;
            }
            String id = selectionId(row.item);
            if (selectedItemIds.contains(id)) {
                selectedItemIds.remove(id);
            } else {
                selectedItemIds.add(id);
            }
        }
        listView.refresh();
        updateActionState();
    }

    private void rebuildRows() {
        List<DlcRow> rows = new ArrayList<>();
        for (DlcType type : DlcType.values()) {
            if (!isVisibleDlcType(type) || (currentTypeFilter != null && currentTypeFilter != type)) {
                continue;
            }
            List<DlcItem> typeItems = allItems.stream()
                .filter(item -> item != null && item.getType() == type && !item.isHidden() && matchesSearch(item))
                .toList();
            if (typeItems.isEmpty()) {
                continue;
            }
            if (currentTypeFilter == null) {
                rows.add(DlcRow.groupHeader(type));
            }
            Map<String, List<DlcItem>> byPath = groupByRelativeFolder(typeItems);
            boolean hasSubDir = byPath.keySet().stream().anyMatch(key -> !"__root__".equals(key));
            for (Map.Entry<String, List<DlcItem>> entry : byPath.entrySet()) {
                String folderKey = folderKey(type, entry.getKey());
                boolean collapsed = collapsedFolders.contains(folderKey);
                if ("__root__".equals(entry.getKey())) {
                    if (hasSubDir) {
                        rows.add(DlcRow.pathHeader(resolvePathLabel(entry.getKey()), folderKey, type, entry.getKey(), collapsed));
                    }
                } else {
                    rows.add(DlcRow.pathHeader(resolvePathLabel(entry.getKey()), folderKey, type, entry.getKey(), collapsed));
                }
                if (collapsed) {
                    continue;
                }
                for (DlcItem item : entry.getValue()) {
                    item.setDirty(isDirty(item));
                    rows.add(DlcRow.item(item, folderKey));
                }
            }
        }
        visibleRows.setAll(rows);
        if (listView != null) {
            listView.refresh();
        }
        updateActionState();
    }

    private Map<String, List<DlcItem>> groupByRelativeFolder(List<DlcItem> items) {
        Map<String, List<DlcItem>> grouped = new java.util.TreeMap<>();
        for (DlcItem item : items) {
            grouped.computeIfAbsent(resolveGroupLabel(item), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private String resolveGroupLabel(DlcItem item) {
        try {
            Path source = item.getSourcePath();
            String relative = null;
            for (Path dlcRoot : DlcConfig.getDlcRoots()) {
                if (source != null && source.startsWith(dlcRoot)) {
                    relative = dlcRoot.relativize(source).toString().replace('\\', '/');
                    break;
                }
            }
            if (relative == null && source != null) {
                relative = DlcPaths.gameDir().relativize(source).toString().replace('\\', '/');
            }
            if (relative == null) {
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

    private String folderKey(DlcType type, String raw) {
        return (type == null ? "unknown" : type.name()) + ":" + (raw == null ? "__root__" : raw);
    }

    private boolean setFolderCollapsed(DlcRow row, boolean collapsed) {
        if (row == null || row.folderKey == null || row.folderKey.isBlank()) {
            return false;
        }
        if (animatingFolders.contains(row.folderKey)) {
            return false;
        }
        int headerIndex = visibleRows.indexOf(row);
        if (headerIndex < 0) {
            return false;
        }
        if (row.collapsed == collapsed) {
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

    private void animateFolderCollapse(DlcRow row, int headerIndex) {
        animatingFolders.add(row.folderKey);
        collapsedFolders.add(row.folderKey);
        row.collapsed = true;
        List<DlcRow> targets = folderItemRowsAfter(headerIndex);
        if (targets.isEmpty()) {
            finishFolderAnimation(row.folderKey);
            return;
        }
        for (DlcRow target : targets) {
            target.expanding = false;
            target.collapsing = true;
        }
        startVisibleFolderAnimations(row.folderKey);
        PauseTransition removeAfterAnimation = new PauseTransition(Duration.millis(260));
        removeAfterAnimation.setOnFinished(event -> {
            visibleRows.removeAll(targets);
            for (DlcRow target : targets) {
                if (target.collapsing && !target.expanding) {
                    target.collapsing = false;
                }
            }
            finishFolderAnimation(row.folderKey);
        });
        removeAfterAnimation.play();
    }

    private void animateFolderExpand(DlcRow row, int headerIndex) {
        animatingFolders.add(row.folderKey);
        collapsedFolders.remove(row.folderKey);
        row.collapsed = false;
        List<DlcRow> rows = itemRowsForFolder(row.folderType, row.folderRaw);
        if (rows.isEmpty()) {
            finishFolderAnimation(row.folderKey);
            return;
        }
        for (DlcRow item : rows) {
            item.expanding = true;
            item.collapsing = false;
        }
        visibleRows.addAll(headerIndex + 1, rows);
        Platform.runLater(() -> startVisibleFolderAnimations(row.folderKey));
        PauseTransition finishExpansion = new PauseTransition(Duration.millis(260));
        finishExpansion.setOnFinished(event -> {
            for (DlcRow item : rows) {
                if (item.expanding && !item.collapsing) {
                    item.expanding = false;
                }
            }
            finishFolderAnimation(row.folderKey);
        });
        finishExpansion.play();
    }

    private void finishFolderAnimation(String folderKey) {
        if (folderKey != null) {
            animatingFolders.remove(folderKey);
        }
        syncVisibleFolderHeaders(folderKey);
        syncVisibleItemCells();
    }

    private void startVisibleFolderAnimations(String folderKey) {
        for (DlcItemCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.startAnimationForFolder(folderKey);
            }
        }
    }

    private void syncVisibleFolderHeaders(String folderKey) {
        for (DlcItemCell cell : new ArrayList<>(activePathCells)) {
            if (cell != null) {
                cell.syncPathState(folderKey);
            }
        }
    }

    private boolean isFolderAnimating(String folderKey) {
        return folderKey != null && animatingFolders.contains(folderKey);
    }

    private List<DlcRow> folderItemRowsAfter(int headerIndex) {
        List<DlcRow> out = new ArrayList<>();
        for (int i = headerIndex + 1; i < visibleRows.size(); i++) {
            DlcRow row = visibleRows.get(i);
            if (row.type != DlcRowType.ITEM) {
                break;
            }
            out.add(row);
        }
        return out;
    }

    private List<DlcRow> itemRowsForFolder(DlcType type, String rawFolder) {
        if (type == null) {
            return List.of();
        }
        List<DlcItem> typeItems = allItems.stream()
            .filter(item -> item != null && item.getType() == type && !item.isHidden() && matchesSearch(item))
            .toList();
        List<DlcItem> folderItems = groupByRelativeFolder(typeItems).get(rawFolder == null ? "__root__" : rawFolder);
        if (folderItems == null || folderItems.isEmpty()) {
            return List.of();
        }
        List<DlcRow> out = new ArrayList<>();
        for (DlcItem item : folderItems) {
            item.setDirty(isDirty(item));
            out.add(DlcRow.item(item, folderKey(type, rawFolder)));
        }
        return out;
    }

    private boolean matchesSearch(DlcItem item) {
        if (currentSearchText.isBlank()) {
            return true;
        }
        String name = displayName(item).toLowerCase();
        String rawName = item.getName() == null ? "" : item.getName().toLowerCase();
        String id = item.getId() == null ? "" : item.getId().toLowerCase();
        String identifier = item.getIdentifier() == null ? "" : item.getIdentifier().toLowerCase();
        return name.contains(currentSearchText)
            || rawName.contains(currentSearchText)
            || id.contains(currentSearchText)
            || identifier.contains(currentSearchText);
    }

    private void refreshRowsAfterStateChange() {
        capturePresetSnapshot.run();
        for (DlcItem item : allItems) {
            if (item != null) {
                item.setDirty(isDirty(item));
            }
        }
        syncVisibleItemCells();
        updateActionState();
    }

    private void syncPresetOverridesIntoItems() {
        if (!DlcConfig.isPresetFuncOn()) {
            return;
        }
        capturePresetSnapshot.run();
        DlcPresetRuntimeState.applyOverridesToRuntime();
        applyPresetOwnedDlcOverrides.run();
        for (DlcItem item : allItems) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
        }
    }

    private void syncPresetStatusesFromCurrentItems() {
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.syncStatusFromRuntime();
        }
    }

    private void syncVisibleItemCells() {
        for (DlcItemCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.syncItemState();
            }
        }
    }

    public boolean hasPendingChanges() {
        for (DlcItem item : allItems) {
            if (isDirty(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDirty(DlcItem item) {
        return item != null && DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled());
    }

    private List<DlcItem> selectedItems() {
        if (selectedItemIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<DlcItem> selected = new LinkedHashSet<>();
        for (DlcItem item : allItems) {
            if (item != null && selectedItemIds.contains(selectionId(item))) {
                selected.add(item);
            }
        }
        return new ArrayList<>(selected);
    }

    private boolean isItemSelected(DlcItem item) {
        return item != null && selectedItemIds.contains(selectionId(item));
    }

    private void setSelected(DlcItem item, boolean selected) {
        if (item == null) {
            return;
        }
        String id = selectionId(item);
        if (selected) {
            selectedItemIds.add(id);
        } else {
            selectedItemIds.remove(id);
        }
    }

    private String selectionId(DlcItem item) {
        if (item == null) {
            return "";
        }
        String id = item.getId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        String identifier = item.getIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        Path source = item.getSourcePath();
        return source == null ? String.valueOf(System.identityHashCode(item)) : source.toAbsolutePath().normalize().toString();
    }

    private List<DlcItem> selectedItemsToEnable() {
        return selectedItems().stream()
            .filter(item -> item != null
                && !item.isEnabled()
                && item.AllowEnableByAll()
                && hasOnlyResolvableDependencies(item)
                && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier()))
            .toList();
    }

    private boolean hasOnlyResolvableDependencies(DlcItem item) {
        java.util.Set<String> missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item);
        if (missing.isEmpty()) {
            return true;
        }
        for (String dependency : missing) {
            if (DlcRuntimeState.getItemByIdentifier(dependency) == null) {
                return false;
            }
        }
        return true;
    }

    private List<DlcItem> selectedItemsToDisable() {
        return selectedItems().stream()
            .filter(item -> item != null && item.isEnabled())
            .toList();
    }

    private void updateActionState() {
        boolean pending = gameDirSelected && hasPendingChanges();
        if (applyBtn != null) {
            applyBtn.setDisable(!pending || progressBar.isVisible());
        }
        if (cancelBtn != null) {
            cancelBtn.setDisable(!pending || progressBar.isVisible());
        }
        if (pendingWarningLabel != null) {
            pendingWarningLabel.setVisible(pending);
            pendingWarningLabel.setManaged(pending);
        }
        List<DlcItem> selected = selectedItems();
        boolean hasSelection = !selected.isEmpty();
        if (enableSelectedBtn != null) {
            enableSelectedBtn.setVisible(hasSelection);
            enableSelectedBtn.setManaged(hasSelection);
            enableSelectedBtn.setDisable(!hasSelection || progressBar.isVisible());
        }
        if (disableSelectedBtn != null) {
            disableSelectedBtn.setVisible(hasSelection);
            disableSelectedBtn.setManaged(hasSelection);
            disableSelectedBtn.setDisable(!hasSelection || progressBar.isVisible());
        }
    }

    private String displayName(DlcItem item) {
        if (item == null) {
            return "";
        }
        Map<String, String> names = item.getDisplayNames();
        if (names != null && !names.isEmpty()) {
            String lang = com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode();
            if (lang != null && names.containsKey(lang)) {
                return names.get(lang);
            }
            if (names.containsKey("en_us")) {
                return names.get("en_us");
            }
            String first = names.values().stream().findFirst().orElse(null);
            if (first != null && !first.isBlank()) {
                return first;
            }
        }
        return item.getName() != null ? item.getName() : item.getId();
    }

    private String pendingSuffix(DlcItem item) {
        if (!isDirty(item)) {
            return "";
        }
        return DlcFxI18n.tr("dlc_manager.pending.apply");
    }

    private String presetDisabledSuffix(DlcItem item) {
        if (item == null || !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())) {
            return "";
        }
        return DlcFxI18n.tr("dlc_manager.preset.disabled_by_preset");
    }

    private String toggleLabel(DlcItem item) {
        return item != null && item.isEnabled()
            ? DlcFxI18n.tr("dlc_manager.toggle.on")
            : DlcFxI18n.tr("dlc_manager.toggle.off");
    }

    private void confirmOpenItemRedirect(DlcItem item) {
        if (item == null || item.getRedirectUrl() == null || item.getRedirectUrl().isBlank()) {
            return;
        }
        DlcFxModalOverlay.show(
            listView == null ? null : listView.getScene(),
            DlcFxI18n.tr("dlc_manager.redirect.title"),
            DlcFxRichTextRenderer.formatted(DlcFxI18n.tr("dlc_manager.redirect.prompt") + "\n" + item.getRedirectUrl()),
            () -> openLink(item.getRedirectUrl()),
            DlcFxI18n.tr("dlc_manager.redirect.open"),
            () -> {},
            DlcFxI18n.tr("dlc_manager.common.cancel")
        );
    }

    private void openLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.open_failed", safeMessage(ex)));
        }
    }

    private final class DlcItemCell extends ListCell<DlcRow> {
        private final CheckBox selectBox = new CheckBox();
        private final Region accent = new Region();
        private final Label typeLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label pendingLabel = new Label();
        private final Label presetDisabledLabel = new Label();
        private final Button toggleButton = new Button();
        private final GridPane itemGrid = new GridPane();
        private final HBox nameBox = new HBox(8, selectBox, accent, nameLabel, pendingLabel, presetDisabledLabel);
        private final HBox itemRow = new HBox(0, itemGrid);
        private final StackPane itemAnimationPane = new StackPane(itemRow);
        private final Label groupLabel = new Label();
        private final Label pathLabel = new Label();
        private final Button collapseButton = new Button();
        private final SVGPath collapseIcon = new SVGPath();
        private final StackPane collapseIconBox = new StackPane(collapseIcon);
        private javafx.scene.control.Tooltip rowTooltip;
        private final BorderPane pathRow = new BorderPane();
        private final Rectangle rowClip = new Rectangle();
        private Timeline rowTimeline;
        private RotateTransition collapseTransition;
        private DlcRow animatedRow;
        private Boolean animatedExpansion;

        DlcItemCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.muted(pathLabel);
            DlcFxStyle.add(pendingLabel, "dlc-pending-badge");
            DlcFxStyle.add(presetDisabledLabel, "dlc-preset-disabled-badge");
            DlcFxStyle.add(accent, "dlc-row-accent");
            DlcFxStyle.add(itemRow, "dlc-dlc-row");
            DlcFxStyle.add(typeLabel, "dlc-dlc-type-label");
            DlcFxStyle.add(groupLabel, "dlc-dlc-group-header");
            DlcFxStyle.add(pathLabel, "dlc-dlc-path-header");
            DlcFxStyle.add(pathRow, "dlc-dlc-path-row");
            DlcFxStyle.add(collapseButton, "dlc-folder-collapse-button");
            DlcFxStyle.add(collapseIconBox, "dlc-folder-collapse-icon-box");
            DlcFxStyle.add(collapseIcon, "dlc-folder-collapse-icon");
            collapseIcon.setContent("M 3 10 L 8 5 L 13 10");
            collapseIcon.setFill(Color.TRANSPARENT);
            collapseIcon.setStroke(Color.web("#475467"));
            collapseIcon.setStrokeWidth(1.8);
            collapseIcon.setStrokeLineCap(StrokeLineCap.ROUND);
            collapseIcon.setStrokeLineJoin(StrokeLineJoin.ROUND);
            rowClip.widthProperty().bind(itemAnimationPane.widthProperty());
            rowClip.heightProperty().bind(itemAnimationPane.heightProperty());
            pendingLabel.setMinWidth(64);
            pendingLabel.setPrefWidth(64);
            pendingLabel.setMaxWidth(64);
            pendingLabel.setOpacity(0);
            presetDisabledLabel.setMinWidth(88);
            presetDisabledLabel.setPrefWidth(88);
            presetDisabledLabel.setMaxWidth(88);
            presetDisabledLabel.setOpacity(0);
            toggleButton.setMinWidth(96);
            toggleButton.setPrefWidth(96);
            toggleButton.setMaxWidth(96);
            nameBox.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setHgap(12);
            itemGrid.add(nameBox, 0, 0);
            itemGrid.add(typeLabel, 1, 0);
            itemGrid.add(toggleButton, 2, 0);
            GridPane.setHgrow(nameBox, Priority.ALWAYS);
            itemGrid.getColumnConstraints().addAll(
                column(420, 520, Priority.ALWAYS),
                column(112, 150, Priority.NEVER),
                column(96, 96, Priority.NEVER)
            );
            itemGrid.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemGrid.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_ITEM_CONTENT_MIN_WIDTH));
            HBox.setHgrow(itemGrid, Priority.ALWAYS);
            itemRow.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemAnimationPane.setAlignment(Pos.CENTER_LEFT);
            itemAnimationPane.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemAnimationPane.prefWidthProperty().bind(itemGrid.prefWidthProperty());
            itemRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (isInside(event.getTarget(), selectBox) || isInside(event.getTarget(), toggleButton)) {
                    return;
                }
                DlcRow row = super.getItem();
                if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() >= 2
                    && row != null
                    && row.type == DlcRowType.ITEM
                    && row.item != null
                    && !isFolderAnimating(row.itemFolderKey)) {
                    confirmOpenItemRedirect(row.item);
                }
                event.consume();
            });
            itemRow.setOnMouseClicked(event -> event.consume());
            HBox.setHgrow(pathLabel, Priority.ALWAYS);
            pathRow.setCenter(pathLabel);
            pathRow.setRight(collapseButton);
            BorderPane.setAlignment(pathLabel, Pos.CENTER_LEFT);
            BorderPane.setAlignment(collapseButton, Pos.CENTER_RIGHT);
            pathLabel.setMaxWidth(Double.MAX_VALUE);
            pathRow.setMinWidth(LIST_PATH_CONTENT_MIN_WIDTH);
            pathRow.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_PATH_CONTENT_MIN_WIDTH));
            pathRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), collapseButton)) {
                    event.consume();
                }
                if (event.getButton() == MouseButton.PRIMARY) {
                    if (listView != null) {
                        listView.getSelectionModel().clearSelection();
                    }
                }
            });
            collapseButton.setMinWidth(28);
            collapseButton.setPrefWidth(28);
            collapseButton.setMaxWidth(28);
            collapseButton.setText(null);
            collapseButton.setGraphic(collapseIconBox);
            collapseButton.setOnAction(event -> {
                DlcRow current = getItem();
                if (current != null && (current.folderKey == null || !animatingFolders.contains(current.folderKey))) {
                    boolean targetCollapsed = !current.collapsed;
                    if (setFolderCollapsed(current, targetCollapsed)) {
                        collapseButton.setDisable(true);
                        animateCollapseButton(targetCollapsed);
                    }
                }
                event.consume();
            });
            selectBox.setOnMouseClicked(event -> {
                DlcRow row = super.getItem();
                if (row == null || row.type != DlcRowType.ITEM || row.item == null) {
                    event.consume();
                    return;
                }
                if (isFolderAnimating(row.itemFolderKey)) {
                    selectBox.setSelected(isItemSelected(row.item));
                    event.consume();
                    return;
                }
                setSelected(row.item, selectBox.isSelected());
                syncVisibleItemCells();
                updateActionState();
                event.consume();
            });
            DlcFxTooltips.followMouse(this, () -> rowTooltip);
            toggleButton.setOnAction(e -> {
                DlcItem item = currentItem();
                DlcRow row = super.getItem();
                if (item != null && (row == null || !isFolderAnimating(row.itemFolderKey))) {
                    if (item.isEnabled()) {
                        disableItemsWithDependents(List.of(item));
                    } else {
                        handleBatchEnable(List.of(item), true);
                    }
                }
            });
        }

        @Override
        protected void updateItem(DlcRow row, boolean empty) {
            super.updateItem(row, empty);
            activeItemCells.remove(this);
            activePathCells.remove(this);
            getStyleClass().removeAll("dlc-row-group", "dlc-row-path", "dlc-row-item");
            getStyleClass().removeAll("dlc-dlc-selected");
            rowTooltip = null;
            if (empty || row == null) {
                resetCellAnimation();
                setMouseTransparent(false);
                setGraphic(null);
                setText(null);
                return;
            }
            if (row.type == DlcRowType.GROUP_HEADER) {
                resetCellAnimation();
                setMouseTransparent(false);
                getStyleClass().add("dlc-row-group");
                groupLabel.setText(DlcFxI18n.tr(row.groupType.getDisplayKey()));
                setGraphic(groupLabel);
                setText(null);
                return;
            }
            if (row.type == DlcRowType.PATH_HEADER) {
                resetCellAnimation();
                setMouseTransparent(false);
                activePathCells.add(this);
                getStyleClass().add("dlc-row-path");
                pathLabel.setText(row.label);
                syncPathState(row.folderKey);
                setGraphic(pathRow);
                setText(null);
                return;
            }
            DlcItem item = row.item;
            activeItemCells.add(this);
            getStyleClass().add("dlc-row-item");
            setMouseTransparent(false);
            typeLabel.getStyleClass().removeAll("type-mod", "type-resource", "type-data", "type-tacz", "type-unknown");
            typeLabel.getStyleClass().add(typeStyleClass(item.getType()));
            typeLabel.setText(DlcFxI18n.tr(item.getType().getDisplayKey()));
            DlcFxRichTextRenderer.setFormatted(nameLabel, displayName(item));
            rowTooltip = DlcFxRichTextRenderer.formattedTooltip(buildTooltip(item));
            setText(null);
            setGraphic(itemAnimationPane);
            syncItemState();
            startRowAnimationIfNeeded(row);
        }

        private void syncPathState(String folderKey) {
            DlcRow row = super.getItem();
            if (row == null || row.type != DlcRowType.PATH_HEADER) {
                return;
            }
            if (folderKey == null || folderKey.equals(row.folderKey)) {
                collapseIcon.setRotate(row.collapsed ? 180 : 0);
                collapseButton.setDisable(isFolderAnimating(row.folderKey));
            }
        }

        private void syncItemState() {
            DlcItem item = currentItem();
            if (item == null) {
                return;
            }
            boolean dirty = isDirty(item);
            boolean selected = isItemSelected(item);
            getStyleClass().removeAll("dlc-dlc-selected");
            if (selected) {
                getStyleClass().add("dlc-dlc-selected");
            }
            selectBox.setSelected(selected);
            accent.getStyleClass().removeAll("enabled", "pending", "off");
            accent.getStyleClass().add(dirty ? "pending" : item.isEnabled() ? "enabled" : "off");
            pendingLabel.setText(pendingSuffix(item));
            pendingLabel.setManaged(true);
            pendingLabel.setVisible(true);
            pendingLabel.setOpacity(dirty ? 1 : 0);
            boolean blocked = DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier());
            presetDisabledLabel.setText(presetDisabledSuffix(item));
            presetDisabledLabel.setManaged(blocked);
            presetDisabledLabel.setVisible(blocked);
            presetDisabledLabel.setOpacity(blocked ? 1 : 0);
            toggleButton.setText(toggleLabel(item));
            toggleButton.getStyleClass().removeAll("dlc-toggle-on", "dlc-toggle-off", "dlc-toggle-pending");
            toggleButton.getStyleClass().add(dirty ? "dlc-toggle-pending" : item.isEnabled() ? "dlc-toggle-on" : "dlc-toggle-off");
            boolean missing = DlcRuntimeState.hasMissingDependencies(item);
            DlcRow row = super.getItem();
            boolean folderAnimating = row != null && isFolderAnimating(row.itemFolderKey);
            itemRow.setMouseTransparent(folderAnimating);
            selectBox.setDisable(folderAnimating);
            toggleButton.setDisable(folderAnimating || blocked || (!item.isEnabled() && missing));
        }

        private DlcItem currentItem() {
            DlcRow row = super.getItem();
            return row == null ? null : row.item;
        }

        private void startAnimationForFolder(String folderKey) {
            DlcRow row = super.getItem();
            if (row == null || row.type != DlcRowType.ITEM || !folderKey.equals(row.itemFolderKey)) {
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

        private void startRowAnimationIfNeeded(DlcRow row) {
            if (row == null) {
                resetCellAnimation();
                return;
            }
            if (row.collapsing && !isCurrentAnimation(row, false)) {
                animateRow(row, false, Math.max(getCurrentAnimatedHeight(), 42), 0, 1, 0, null);
                return;
            }
            if (row.expanding && !isCurrentAnimation(row, true)) {
                animateRow(row, true, 0, 42, 0, 1, () -> {
                    if (super.getItem() == row && row.expanding && !row.collapsing) {
                        row.expanding = false;
                        resetCellAnimation();
                    }
                });
                return;
            }
            if (!row.expanding && !row.collapsing) {
                resetCellAnimation();
            }
        }

        private boolean isCurrentAnimation(DlcRow row, boolean expandingAnimation) {
            return rowTimeline != null && animatedRow == row && animatedExpansion != null && animatedExpansion == expandingAnimation;
        }

        private void animateRow(DlcRow row, boolean expandingAnimation, double fromHeight, double toHeight,
                                double fromOpacity, double toOpacity, Runnable afterFinished) {
            if (rowTimeline != null) {
                rowTimeline.stop();
            }
            animatedRow = row;
            animatedExpansion = expandingAnimation;
            itemAnimationPane.setClip(rowClip);
            itemAnimationPane.setMinHeight(fromHeight);
            itemAnimationPane.setPrefHeight(fromHeight);
            itemAnimationPane.setMaxHeight(fromHeight);
            itemAnimationPane.setOpacity(fromOpacity);
            rowTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(itemAnimationPane.minHeightProperty(), fromHeight),
                    new KeyValue(itemAnimationPane.prefHeightProperty(), fromHeight),
                    new KeyValue(itemAnimationPane.maxHeightProperty(), fromHeight),
                    new KeyValue(itemAnimationPane.opacityProperty(), fromOpacity)
                ),
                new KeyFrame(Duration.millis(240),
                    new KeyValue(itemAnimationPane.minHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                    new KeyValue(itemAnimationPane.prefHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                    new KeyValue(itemAnimationPane.maxHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                    new KeyValue(itemAnimationPane.opacityProperty(), toOpacity, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0))
                )
            );
            rowTimeline.setOnFinished(event -> {
                if (super.getItem() != row || row.expanding != expandingAnimation || row.collapsing == expandingAnimation) {
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
            double height = itemAnimationPane.getHeight();
            if (Double.isNaN(height) || height <= 0) {
                height = itemAnimationPane.getPrefHeight();
            }
            if (Double.isNaN(height) || height <= 0 || height == Region.USE_COMPUTED_SIZE || height == Region.USE_PREF_SIZE) {
                return 42;
            }
            return height;
        }

        private void resetCellAnimation() {
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
            itemAnimationPane.setMinHeight(Region.USE_PREF_SIZE);
            itemAnimationPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            itemAnimationPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
            itemAnimationPane.setOpacity(1);
            itemAnimationPane.setClip(null);
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
    }

    private String typeStyleClass(DlcType type) {
        if (type == null) {
            return "type-unknown";
        }
        return switch (type) {
            case MOD -> "type-mod";
            case RESOURCE_PACK -> "type-resource";
            case DATA_PACK -> "type-data";
            case TACZ_PACK -> "type-tacz";
            default -> "type-unknown";
        };
    }

    private DlcType selectedTypeFilter() {
        return typeOf(typeFilter.getValue());
    }

    private static DlcType typeOf(DlcTypeOption option) {
        return option == null || option.all ? null : option.type;
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

    private static String typeText(DlcTypeOption option) {
        DlcType type = typeOf(option);
        return type == null ? DlcFxI18n.tr("dlc_manager.local.all_types") : DlcFxI18n.tr(type.getDisplayKey());
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

    private javafx.scene.layout.ColumnConstraints column(double min, double pref, Priority grow) {
        javafx.scene.layout.ColumnConstraints constraints = new javafx.scene.layout.ColumnConstraints();
        constraints.setMinWidth(min);
        constraints.setPrefWidth(pref);
        constraints.setHgrow(grow);
        return constraints;
    }

    private String buildTooltip(DlcItem item) {
        if (item == null) {
            return "";
        }
        StringBuilder tip = new StringBuilder();
        appendBlock(tip, resolveTooltipText(item));
        String redirectUrl = item.getRedirectUrl();
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            appendBlock(tip, "§b" + DlcFxI18n.tr("dlc_manager.tooltip.double_click_redirect") + "§r");
        }
        List<String> blockers = DlcPresetRuntimeState.getDlcBlockerNames(item.getIdentifier(),
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        if (!blockers.isEmpty()) {
            for (String blocker : blockers) {
                appendBlock(tip, "§4" + DlcFxI18n.tr("dlc_manager.preset.conflict", blocker) + "§r");
            }
        }
        java.util.Set<String> missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item);
        if (!missing.isEmpty()) {
            appendBlock(tip, "§c" + DlcFxI18n.tr("dlc_manager.tooltip.missing_dependencies") + ":§r");
            for (String dep : missing) {
                appendLine(tip, resolveDisplayName(dep));
            }
        }
        List<String> deps = collectDependencyNamesRecursive(item);
        if (!deps.isEmpty()) {
            appendBlock(tip, "§6" + DlcFxI18n.tr("dlc_manager.tooltip.dependencies") + ":§r");
            for (String dep : deps) {
                appendLine(tip, resolveDisplayName(dep));
            }
        }
        if (!item.getConflicts().isEmpty()) {
            appendBlock(tip, "§4" + DlcFxI18n.tr("dlc_manager.tooltip.conflicts") + ":§r");
            for (String conflict : item.getConflicts()) {
                appendLine(tip, resolveDisplayName(conflict));
            }
        }
        return tip.toString();
    }

    private static String resolveTooltipText(DlcItem item) {
        if (item == null || item.getTooltips().isEmpty()) {
            return "";
        }
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = item.getTooltips().get(lang);
        if (value == null || value.isBlank()) {
            value = item.getTooltips().get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = item.getTooltips().values().stream().findFirst().orElse("");
        }
        return value == null ? "" : value;
    }

    private static String resolveDisplayName(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
        if (item == null || item.getDisplayNames().isEmpty()) {
            return identifier;
        }
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = item.getDisplayNames().get(lang);
        if (value == null || value.isBlank()) {
            value = item.getDisplayNames().get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = item.getDisplayNames().values().stream().findFirst().orElse(identifier);
        }
        return value == null || value.isBlank() ? identifier : value;
    }

    private static List<String> collectDependencyNamesRecursive(DlcItem item) {
        if (item == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        addDependencyNames(item.getDependencies(), result, queue);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (name == null || name.isBlank()) {
                continue;
            }
            DlcItem depItem = DlcRuntimeState.getItemByIdentifier(name);
            if (depItem != null) {
                addDependencyNames(depItem.getDependencies(), result, queue);
            }
        }
        return new ArrayList<>(result);
    }

    private static void addDependencyNames(List<String> names, Set<String> result, java.util.ArrayDeque<String> queue) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isBlank() && result.add(name)) {
                queue.add(name);
            }
        }
    }

    private static void appendBlock(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(value);
    }

    private static void appendLine(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private enum DlcRowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM
    }

    private static final class DlcRow {
        private final DlcRowType type;
        private final String label;
        private final DlcType groupType;
        private final DlcItem item;
        private final String itemFolderKey;
        private final String folderKey;
        private final DlcType folderType;
        private final String folderRaw;
        private boolean collapsed;
        private boolean expanding;
        private boolean collapsing;

        private DlcRow(DlcRowType type, String label, DlcType groupType, DlcItem item, String itemFolderKey,
                       String folderKey, DlcType folderType, String folderRaw, boolean collapsed) {
            this.type = type;
            this.label = label;
            this.groupType = groupType;
            this.item = item;
            this.itemFolderKey = itemFolderKey;
            this.folderKey = folderKey;
            this.folderType = folderType;
            this.folderRaw = folderRaw;
            this.collapsed = collapsed;
        }

        private static DlcRow groupHeader(DlcType type) {
            return new DlcRow(DlcRowType.GROUP_HEADER, null, type, null, null, null, null, null, false);
        }

        private static DlcRow pathHeader(String label, String folderKey, DlcType folderType, String folderRaw, boolean collapsed) {
            return new DlcRow(DlcRowType.PATH_HEADER, label, null, null, null, folderKey, folderType, folderRaw, collapsed);
        }

        private static DlcRow item(DlcItem item, String itemFolderKey) {
            return new DlcRow(DlcRowType.ITEM, null, null, item, itemFolderKey, null, null, null, false);
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

}
