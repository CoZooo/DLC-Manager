package com.cozooo.dlc_manager.javafx;

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
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
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
 *   <li>Game-directory chooser (updates {@link StandalonePlatformProvider})</li>
 *   <li>Scan button invoking {@link DlcScanner}</li>
 *   <li>Grouped DLC list with enable/disable checkboxes</li>
 *   <li>Search/filter bar</li>
 *   <li>Apply button invoking {@link DlcApplier}</li>
 * </ul>
 * All long-running operations run on a background thread; UI updates are
 * marshalled back via {@link Platform#runLater}.
 */
final class LocalDlcManagementTab {

    private final StandalonePlatformProvider provider;

    /**
     * Notified after the user picks a new game directory, so sibling local
     * tabs (Remote download, Required) can clear/refresh their cached state.
     */
    private Runnable onGameDirChanged;

    private final ObservableList<DlcItem> allItems = FXCollections.observableArrayList();
    private final FilteredList<DlcItem> filteredItems = new FilteredList<>(allItems);
    private final Label dirLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.local.no_game_dir"));
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.ready"));
    private final TextField searchField = new TextField();
    private final ComboBox<DlcType> typeFilter = new ComboBox<>();

    LocalDlcManagementTab(StandalonePlatformProvider provider) {
        this.provider = provider;
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);
    }

    void setOnGameDirChanged(Runnable onGameDirChanged) {
        this.onGameDirChanged = onGameDirChanged;
    }

    Tab build(Stage stage) {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.local_dlc"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: directory chooser + scan controls ────────────────
        Button chooseDirBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.choose_game_dir"));
        chooseDirBtn.setOnAction(e -> chooseDirectory(stage));

        Button rescanBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.scan_dlc"));
        rescanBtn.disableProperty().bind(dirLabel.textProperty().isEqualTo(DlcFxI18n.tr("dlc_manager.javafx.local.no_game_dir")));
        rescanBtn.setOnAction(e -> scanAsync());

        Button openDlcDirBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.open_dlc_dir"));
        openDlcDirBtn.disableProperty().bind(dirLabel.textProperty().isEqualTo(DlcFxI18n.tr("dlc_manager.javafx.local.no_game_dir")));
        openDlcDirBtn.setOnAction(e -> openDlcDirectory());
        DlcFxStyle.primary(chooseDirBtn);
        DlcFxStyle.primary(rescanBtn);
        DlcFxStyle.muted(dirLabel);
        DlcFxStyle.muted(countLabel);

        HBox topBox = new HBox(10,
                chooseDirBtn, dirLabel,
                rescanBtn, countLabel,
                openDlcDirBtn);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: filter bar + DLC list ─────────────────────────
        searchField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.local.search_prompt"));
        searchField.setPrefColumnCount(30);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter(n, typeFilter.getValue()));

        typeFilter.getItems().add(null); // "All"
        for (DlcType t : DlcType.values()) {
            typeFilter.getItems().add(t);
        }
        typeFilter.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.local.all_types"));
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcType t) { return t == null ? DlcFxI18n.tr("dlc_manager.javafx.local.all_types") : t.name(); }
            @Override public DlcType fromString(String s) { return null; }
        });
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter(searchField.getText(), n));

        Label searchLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.local.filter"));
        Label typeLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.local.type"));
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
        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.select_all"));
        selectAllBtn.setOnAction(e -> {
            for (DlcItem it : allItems) setItemEnabled(it, true);
            listView.refresh();
        });
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.select_none"));
        selectNoneBtn.setOnAction(e -> {
            for (DlcItem it : allItems) setItemEnabled(it, false);
            listView.refresh();
        });
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.invert"));
        invertBtn.setOnAction(e -> {
            for (DlcItem it : allItems) setItemEnabled(it, !it.isEnabled());
            listView.refresh();
        });

        ComboBox<DlcApplyMode> modeCombo = new ComboBox<>(FXCollections.observableArrayList(DlcApplyMode.values()));
        modeCombo.getSelectionModel().select(DlcApplyMode.RESTART_ONLY);

        Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.apply_changes"));
        applyBtn.disableProperty().bind(dirLabel.textProperty().isEqualTo(DlcFxI18n.tr("dlc_manager.javafx.local.no_game_dir")));
        applyBtn.setOnAction(e -> applyAsync(modeCombo.getValue()));
        DlcFxStyle.primary(applyBtn);

        HBox bottomBox = new HBox(8,
                selectAllBtn, selectNoneBtn, invertBtn,
                new Label(DlcFxI18n.tr("dlc_manager.apply_mode")), modeCombo, applyBtn,
                progressBar, statusLabel);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.statusStrip(bottomBox);
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    // ───────────────────────────────────────────────────────────────
    //  Actions
    // ───────────────────────────────────────────────────────────────

    private void chooseDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(DlcFxI18n.tr("dlc_manager.javafx.local.choose_game_dir_title"));
        File current = new File(provider.gameDir().toString());
        if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File chosen = chooser.showDialog(stage);
        if (chosen == null) {
            return;
        }
        Path gameDir = chosen.toPath();
        provider.setGameDir(gameDir);
        dirLabel.setText(gameDir.toString());
        // Reload config + state for the new game dir
        DlcConfig.load();
        DlcStateConfig.load();
        DlcRuntimeState.clearOverrides();
        DlcPresetRuntimeState.clearAll();
        allItems.clear();
        countLabel.setText("");
        // Notify sibling local tabs that the game directory changed.
        if (onGameDirChanged != null) {
            onGameDirChanged.run();
        }
        // Auto-scan after directory selection
        scanAsync();
    }

    private void openDlcDirectory() {
        List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
        if (roots.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.local.no_dlc_root"));
            return;
        }
        java.nio.file.Path dlcRoot = roots.get(0);
        if (!java.nio.file.Files.exists(dlcRoot)) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.local.dlc_dir_missing", dlcRoot));
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(dlcRoot.toFile());
        } catch (Exception ex) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.open_failed", safeMessage(ex)));
        }
    }

    private void scanAsync() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // indeterminate
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.local.scanning"));
        Thread thread = new Thread(() -> {
            try {
                // Diagnostic: collect DLC roots state
                List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
                StringBuilder diag = new StringBuilder();
                diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.game_dir", provider.gameDir())).append('\n');
                diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.dlc_root_raw", DlcConfig.getDlcRootRaw())).append('\n');
                boolean[] anyRootExists = {false};
                for (java.nio.file.Path root : roots) {
                    boolean exists = java.nio.file.Files.exists(root);
                    if (exists) anyRootExists[0] = true;
                    diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.dlc_root", root, exists)).append('\n');
                    // Diagnose the info directory and config files.
                    java.nio.file.Path infoDir = root.resolve("info");
                    boolean infoExists = java.nio.file.Files.isDirectory(infoDir);
                    diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.info_dir", infoDir, infoExists)).append('\n');
                    if (infoExists) {
                        try (var stream = java.nio.file.Files.list(infoDir)) {
                            var items = stream.toList();
                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.info_file_count", items.size())).append('\n');
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
                                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.dc_summary", fn, fName, fType, fTarget)).append('\n');
                                        } else {
                                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.dc_parse_failed", fn)).append('\n');
                                        }
                                    } catch (Exception ex) {
                                        diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.dc_read_failed", fn, ex.getMessage())).append('\n');
                                    }
                                } else {
                                    otherCount++;
                                    diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.non_dc_file", fn)).append('\n');
                                }
                            }
                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.dc_config_count", dcCount)).append('\n');
                            if (otherCount > 0) {
                                diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.other_file_count", otherCount)).append('\n');
                            }
                        } catch (Exception ex) {
                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.info_read_failed", ex.getMessage())).append('\n');
                        }
                    }
                    // Diagnose actual DLC files.
                    if (exists) {
                        try (var stream = java.nio.file.Files.walk(root)) {
                            var allFiles = stream.toList();
                            long jars = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".jar")).count();
                            long zips = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".zip")).count();
                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.jar_count", jars)).append('\n');
                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.zip_count", zips)).append('\n');
                            // List every file outside the info directory.
                            java.nio.file.Path infoDir0 = root.resolve("info");
                            for (var p : allFiles) {
                                if (java.nio.file.Files.isRegularFile(p) && !p.startsWith(infoDir0)) {
                                    String rel = root.relativize(p).toString().replace('\\', '/');
                                    diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.file", rel)).append('\n');
                                }
                            }
                        } catch (Exception ex) {
                            diag.append(DlcFxI18n.tr("dlc_manager.javafx.local.diag.walk_failed", ex.getMessage())).append('\n');
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
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.common.count_items", total));
                    progressBar.setVisible(false);
                    if (total == 0) {
                        String msg = anyRootExists[0]
                            ? DlcFxI18n.tr("dlc_manager.javafx.local.no_dlc_items")
                            : DlcFxI18n.tr("dlc_manager.javafx.local.no_dlc_dir");
                        statusLabel.setText(msg);
                        statusLabel.setTooltip(new Tooltip(diagText));
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.local.scan_done"));
                        statusLabel.setTooltip(new Tooltip(diagText));
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.local.scan_failed", safeMessage(t)));
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
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.applying"));
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
                        sb.append(DlcFxI18n.tr("dlc_manager.javafx.apply.requires_restart"));
                    }
                    if (result.hotReloaded()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.javafx.apply.hot_reloaded"));
                    }
                    if (result.helperScheduled()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.javafx.apply.helper_scheduled"));
                    }
                    if (sb.length() == 0) {
                        sb.append(DlcFxI18n.tr("dlc_manager.javafx.status.apply_done"));
                    }
                    if (!result.messages().isEmpty()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.javafx.status.message_count_suffix", result.messages().size()));
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
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.apply_failed", safeMessage(t)));
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
                tip.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.id")).append(item.getId());
                if (item.getSourcePath() != null) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.source")).append(item.getSourcePath());
                }
                if (item.getAppliedTargetRaw() != null && !item.getAppliedTargetRaw().isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.target")).append(item.getAppliedTargetRaw());
                }
                if (!item.getDependencies().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.dependencies")).append(String.join(", ", item.getDependencies()));
                }
                if (!item.getConflicts().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.conflicts")).append(String.join(", ", item.getConflicts()));
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
