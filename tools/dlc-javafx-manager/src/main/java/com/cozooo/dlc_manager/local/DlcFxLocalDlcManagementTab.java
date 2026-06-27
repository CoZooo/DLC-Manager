package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.core.DlcType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Controller for the local DLC management tab.
 * <p>
 * Provides:
 * <ul>
 *   <li>Game-directory chooser (updates {@link DlcFxPlatformProvider})</li>
 *   <li>Scan button invoking {@link DlcScanner}</li>
 *   <li>Grouped DLC list with enable/disable checkboxes</li>
 *   <li>Search/filter bar</li>
 *   <li>Apply button invoking {@link DlcApplier}</li>
 * </ul>
 * All long-running operations run on a background thread; UI updates are
 * marshalled back via {@link Platform#runLater}.
 */
public final class DlcFxLocalDlcManagementTab {

    private final DlcFxPlatformProvider provider;

    private final ObservableList<DlcItem> allItems = FXCollections.observableArrayList();
    private final FilteredList<DlcItem> filteredItems = new FilteredList<>(allItems);
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.ready"));
    private final TextField searchField = new TextField();
    private final ComboBox<DlcType> typeFilter = new ComboBox<>();
    private Button rescanBtn;
    private Button openDlcDirBtn;
    private Button applyBtn;
    private boolean gameDirSelected;

    public DlcFxLocalDlcManagementTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);
    }

    public void setGameDirSelected(boolean selected) {
        this.gameDirSelected = selected;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_dlc"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: scan controls ───────────────────────────────────
        rescanBtn = new Button(DlcFxI18n.tr("dlc_manager.local.scan_dlc"));
        rescanBtn.setDisable(!gameDirSelected);
        rescanBtn.setOnAction(e -> scanAsync());

        openDlcDirBtn = new Button(DlcFxI18n.tr("dlc_manager.local.open_dlc_dir"));
        openDlcDirBtn.setDisable(!gameDirSelected);
        openDlcDirBtn.setOnAction(e -> openDlcDirectory());
        DlcFxStyle.primary(rescanBtn);
        DlcFxStyle.muted(countLabel);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topBox = new HBox(10,
                rescanBtn, openDlcDirBtn, topSpacer, countLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: filter bar + DLC list ─────────────────────────
        searchField.setPromptText(DlcFxI18n.tr("dlc_manager.local.search_prompt"));
        searchField.setPrefColumnCount(30);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter(n, typeFilter.getValue()));

        typeFilter.getItems().add(null); // "All"
        for (DlcType t : DlcType.values()) {
            typeFilter.getItems().add(t);
        }
        typeFilter.setPromptText(DlcFxI18n.tr("dlc_manager.local.all_types"));
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcType t) { return t == null ? DlcFxI18n.tr("dlc_manager.local.all_types") : DlcFxI18n.tr(t.getDisplayKey()); }
            @Override public DlcType fromString(String s) { return null; }
        });
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter(searchField.getText(), n));

        Label searchLabel = new Label(DlcFxI18n.tr("dlc_manager.local.filter"));
        Label typeLabel = new Label(DlcFxI18n.tr("dlc_manager.local.type"));
        DlcFxStyle.muted(searchLabel);
        DlcFxStyle.muted(typeLabel);
        HBox filterBox = new HBox(8, searchLabel, searchField, typeLabel, typeFilter);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.filterBar(filterBox);

        ListView<DlcItem> listView = new ListView<>(filteredItems);
        listView.setCellFactory(param -> new DlcItemCell());
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(listView, Priority.ALWAYS);

        VBox centerBox = new VBox(10, filterBox, listView);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        // ── Bottom: apply controls ────────────────────────────────
        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        selectAllBtn.setOnAction(e -> {
            for (DlcItem it : allItems) setItemEnabled(it, true);
            listView.refresh();
        });
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        selectNoneBtn.setOnAction(e -> {
            for (DlcItem it : allItems) setItemEnabled(it, false);
            listView.refresh();
        });
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        invertBtn.setOnAction(e -> {
            for (DlcItem it : allItems) setItemEnabled(it, !it.isEnabled());
            listView.refresh();
        });

        ComboBox<DlcApplyMode> modeCombo = new ComboBox<>(FXCollections.observableArrayList(DlcApplyMode.values()));
        modeCombo.getSelectionModel().select(DlcApplyMode.RESTART_ONLY);

        applyBtn = new Button(DlcFxI18n.tr("dlc_manager.local.apply_changes"));
        applyBtn.setDisable(!gameDirSelected);
        applyBtn.setOnAction(e -> applyAsync(modeCombo.getValue()));
        DlcFxStyle.primary(applyBtn);

        Label selectionTitle = new Label(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Label applyTitle = new Label(DlcFxI18n.tr("dlc_manager.local.apply_changes"));
        DlcFxStyle.actionTitle(selectionTitle);
        DlcFxStyle.actionTitle(applyTitle);

        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);
        HBox applyActions = new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.apply_mode")), modeCombo, applyBtn);
        applyActions.setAlignment(Pos.CENTER_LEFT);
        VBox selectionBox = new VBox(8, selectionTitle, selectionActions);
        VBox applyBox = new VBox(8, applyTitle, applyActions);
        HBox actionPanel = new HBox(18, selectionBox, applyBox, progressBar, statusLabel);
        HBox.setHgrow(selectionBox, Priority.ALWAYS);
        HBox.setHgrow(applyBox, Priority.ALWAYS);
        actionPanel.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        if (rescanBtn != null) {
            rescanBtn.setDisable(false);
        }
        if (openDlcDirBtn != null) {
            openDlcDirBtn.setDisable(false);
        }
        if (applyBtn != null) {
            applyBtn.setDisable(false);
        }
        allItems.clear();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.game_dir_changed"));
        scanAsync();
    }

    // ───────────────────────────────────────────────────────────────
    //  Actions
    // ───────────────────────────────────────────────────────────────

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
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // indeterminate
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scanning"));
        Thread thread = new Thread(() -> {
            try {
                // Diagnostic: collect DLC roots state
                List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
                StringBuilder diag = new StringBuilder();
                diag.append(DlcFxI18n.tr("dlc_manager.local.diag.game_dir", provider.gameDir())).append('\n');
                diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dlc_root_raw", DlcConfig.getDlcRootRaw())).append('\n');
                boolean[] anyRootExists = {false};
                for (java.nio.file.Path root : roots) {
                    boolean exists = java.nio.file.Files.exists(root);
                    if (exists) anyRootExists[0] = true;
                    diag.append(DlcFxI18n.tr("dlc_manager.local.diag.dlc_root", root, exists)).append('\n');
                    // Diagnose the info directory and config files.
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
                                    // Read a compact .dc file summary.
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
                    // Diagnose actual DLC files.
                    if (exists) {
                        try (var stream = java.nio.file.Files.walk(root)) {
                            var allFiles = stream.toList();
                            long jars = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".jar")).count();
                            long zips = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".zip")).count();
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.jar_count", jars)).append('\n');
                            diag.append(DlcFxI18n.tr("dlc_manager.local.diag.zip_count", zips)).append('\n');
                            // List every file outside the info directory.
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
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", total));
                    progressBar.setVisible(false);
                    if (total == 0) {
                        String msg = anyRootExists[0]
                            ? DlcFxI18n.tr("dlc_manager.local.no_dlc_items")
                            : DlcFxI18n.tr("dlc_manager.local.no_dlc_dir");
                        statusLabel.setText(msg);
                        statusLabel.setTooltip(new Tooltip(diagText));
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scan_done"));
                        statusLabel.setTooltip(new Tooltip(diagText));
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scan_failed", safeMessage(t)));
                });
            }
        }, "dlc-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync(DlcApplyMode mode) {
        if (mode == null) {
            mode = DlcApplyMode.RESTART_ONLY;
        }
        final DlcApplyMode finalMode = mode;
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        Thread thread = new Thread(() -> {
            try {
                DlcApplier.ApplyResult result = DlcApplier.apply(new ArrayList<>(allItems), finalMode);
                DlcStateConfig.save();
                if (DlcConfig.isPresetFuncOn()) {
                    DlcPresetRuntimeState.syncStatusFromRuntime();
                }
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    StringBuilder sb = new StringBuilder();
                    if (result.requiresRestart()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.apply.requires_restart"));
                    }
                    if (result.hotReloaded()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.apply.hot_reloaded"));
                    }
                    if (result.helperScheduled()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.apply.helper_scheduled"));
                    }
                    if (sb.length() == 0) {
                        sb.append(DlcFxI18n.tr("dlc_manager.status.apply_done"));
                    }
                    if (!result.messages().isEmpty()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.status.message_count_suffix", result.messages().size()));
                    }
                    statusLabel.setText(sb.toString());
                    // Surface the per-item apply messages (conflicts/enables/etc.)
                    // that the in-game screen shows; the count above is just a summary.
                    if (!result.messages().isEmpty()) {
                        StringBuilder tip = new StringBuilder();
                        for (String msg : result.messages()) {
                            if (tip.length() > 0) tip.append('\n');
                            tip.append(msg);
                        }
                        statusLabel.setTooltip(new Tooltip(tip.toString()));
                    } else {
                        statusLabel.setTooltip(null);
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                });
            }
        }, "dlc-apply");
        thread.setDaemon(true);
        thread.start();
    }

    // ───────────────────────────────────────────────────────────────
    //  Filtering
    // ───────────────────────────────────────────────────────────────

    private DlcType currentTypeFilter;
    private String currentSearchText = "";

    void applyFilter(String searchText, DlcType typeFilter) {
        currentSearchText = searchText == null ? "" : searchText.toLowerCase();
        currentTypeFilter = typeFilter;
        Predicate<DlcItem> pred = item -> {
            if (typeFilter != null && item.getType() != typeFilter) {
                return false;
            }
            if (currentSearchText.isBlank()) {
                return true;
            }
            String name = item.getName() == null ? "" : item.getName().toLowerCase();
            String id = item.getId() == null ? "" : item.getId().toLowerCase();
            return name.contains(currentSearchText) || id.contains(currentSearchText);
        };
        filteredItems.setPredicate(pred);
    }

    // ───────────────────────────────────────────────────────────────
    //  Cell renderer
    // ───────────────────────────────────────────────────────────────

    private static final class DlcItemCell extends ListCell<DlcItem> {
        private final CheckBox checkBox = new CheckBox();
        private final Label typeLabel = new Label();
        private final Label nameLabel = new Label();
        private final HBox row = new HBox(8, checkBox, typeLabel, nameLabel);

        DlcItemCell() {
            DlcFxStyle.chip(typeLabel, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.title(nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            checkBox.setOnAction(e -> {
                DlcItem item = getItem();
                if (item != null) {
                    setItemEnabled(item, checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(DlcItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                checkBox.setSelected(item.isEnabled());
                typeLabel.setText(item.getType().name());
                nameLabel.setText(item.getName() != null ? item.getName() : item.getId());
                StringBuilder tip = new StringBuilder();
                tip.append(DlcFxI18n.tr("dlc_manager.tooltip.id")).append(item.getId());
                if (item.getSourcePath() != null) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.source")).append(item.getSourcePath());
                }
                if (item.getAppliedTargetRaw() != null && !item.getAppliedTargetRaw().isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.target")).append(item.getAppliedTargetRaw());
                }
                if (!item.getDependencies().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.dependencies")).append(String.join(", ", item.getDependencies()));
                }
                if (!item.getConflicts().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.conflicts")).append(String.join(", ", item.getConflicts()));
                }
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private static void setItemEnabled(DlcItem item, boolean enabled) {
        if (item == null) {
            return;
        }
        item.setEnabled(enabled);
        DlcRuntimeState.setOverride(item.getId(), enabled);
    }
}
