package com.cozooo.dlc_manager.javafx;

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
 * Controller for the "本地 · DLC 管理" tab.
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
    private final Label dirLabel = new Label("未选择游戏目录");
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("就绪");
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
        Tab tab = new Tab("本地 · DLC 管理");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: directory chooser + scan controls ────────────────
        Button chooseDirBtn = new Button("选择游戏目录…");
        chooseDirBtn.setOnAction(e -> chooseDirectory(stage));

        Button rescanBtn = new Button("扫描 DLC");
        rescanBtn.disableProperty().bind(dirLabel.textProperty().isEqualTo("未选择游戏目录"));
        rescanBtn.setOnAction(e -> scanAsync());

        Button openDlcDirBtn = new Button("打开 DLC 目录");
        openDlcDirBtn.disableProperty().bind(dirLabel.textProperty().isEqualTo("未选择游戏目录"));
        openDlcDirBtn.setOnAction(e -> openDlcDirectory());

        HBox topBox = new HBox(8,
                chooseDirBtn, dirLabel,
                new Label("  |  "), rescanBtn, countLabel,
                new Label("  |  "), openDlcDirBtn);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: filter bar + DLC list ─────────────────────────
        searchField.setPromptText("搜索 DLC 名称…");
        searchField.setPrefColumnCount(30);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter(n, typeFilter.getValue()));

        typeFilter.getItems().add(null); // "All"
        for (DlcType t : DlcType.values()) {
            typeFilter.getItems().add(t);
        }
        typeFilter.setPromptText("全部类型");
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcType t) { return t == null ? "全部类型" : t.name(); }
            @Override public DlcType fromString(String s) { return null; }
        });
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter(searchField.getText(), n));

        HBox filterBox = new HBox(8, new Label("过滤:"), searchField, new Label("类型:"), typeFilter);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        filterBox.setPadding(new Insets(4, 8, 4, 8));

        ListView<DlcItem> listView = new ListView<>(filteredItems);
        listView.setCellFactory(param -> new DlcItemCell());
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(listView, Priority.ALWAYS);

        VBox centerBox = new VBox(4, filterBox, listView);
        centerBox.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(centerBox);

        // ── Bottom: apply controls ────────────────────────────────
        Button selectAllBtn = new Button("全选");
        selectAllBtn.setOnAction(e -> {
            for (DlcItem it : allItems) it.setEnabled(true);
            listView.refresh();
        });
        Button selectNoneBtn = new Button("全不选");
        selectNoneBtn.setOnAction(e -> {
            for (DlcItem it : allItems) it.setEnabled(false);
            listView.refresh();
        });
        Button invertBtn = new Button("反选");
        invertBtn.setOnAction(e -> {
            for (DlcItem it : allItems) it.setEnabled(!it.isEnabled());
            listView.refresh();
        });

        ComboBox<DlcApplyMode> modeCombo = new ComboBox<>(FXCollections.observableArrayList(DlcApplyMode.values()));
        modeCombo.getSelectionModel().select(DlcApplyMode.RESTART_ONLY);

        Button applyBtn = new Button("应用更改");
        applyBtn.setStyle("-fx-font-weight: bold;");
        applyBtn.disableProperty().bind(dirLabel.textProperty().isEqualTo("未选择游戏目录"));
        applyBtn.setOnAction(e -> applyAsync(modeCombo.getValue()));

        HBox bottomBox = new HBox(8,
                selectAllBtn, selectNoneBtn, invertBtn,
                new Label("  |  应用模式:"), modeCombo, applyBtn,
                new Label("  |  "), progressBar, statusLabel);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(8));
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    // ───────────────────────────────────────────────────────────────
    //  Actions
    // ───────────────────────────────────────────────────────────────

    private void chooseDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择 Minecraft 游戏目录 (.minecraft)");
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
            statusLabel.setText("未配置 DLC 根目录");
            return;
        }
        java.nio.file.Path dlcRoot = roots.get(0);
        if (!java.nio.file.Files.exists(dlcRoot)) {
            statusLabel.setText("DLC 目录不存在: " + dlcRoot);
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(dlcRoot.toFile());
        } catch (Exception ex) {
            statusLabel.setText("无法打开: " + safeMessage(ex));
        }
    }

    private void scanAsync() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // indeterminate
        statusLabel.setText("扫描中…");
        Thread thread = new Thread(() -> {
            try {
                // Diagnostic: collect DLC roots state
                List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
                StringBuilder diag = new StringBuilder();
                diag.append("gameDir=").append(provider.gameDir()).append('\n');
                diag.append("dlcRootRaw=").append(DlcConfig.getDlcRootRaw()).append('\n');
                boolean[] anyRootExists = {false};
                for (java.nio.file.Path root : roots) {
                    boolean exists = java.nio.file.Files.exists(root);
                    if (exists) anyRootExists[0] = true;
                    diag.append("dlcRoot=").append(root).append(" exists=").append(exists).append('\n');
                    // 诊断 info 目录及配置文件
                    java.nio.file.Path infoDir = root.resolve("info");
                    boolean infoExists = java.nio.file.Files.isDirectory(infoDir);
                    diag.append("  infoDir=").append(infoDir).append(" exists=").append(infoExists).append('\n');
                    if (infoExists) {
                        try (var stream = java.nio.file.Files.list(infoDir)) {
                            var items = stream.toList();
                            diag.append("  info 文件数: ").append(items.size()).append('\n');
                            int dcCount = 0;
                            int otherCount = 0;
                            for (var item : items) {
                                String fn = item.getFileName().toString();
                                if (fn.toLowerCase().endsWith(".dc")) {
                                    dcCount++;
                                    // 读取 .dc 文件内容摘要
                                    try {
                                        var obj = com.cozooo.dlc_manager.core.DlcDcConfigIO.read(item,
                                            com.cozooo.dlc_manager.core.DlcDcConfigIO.Layout.DLC_STRUCTURED);
                                        if (obj != null) {
                                            String fName = com.cozooo.dlc_manager.core.DlcDcConfigIO.getStringIgnoreCase(obj, "fileName");
                                            String fType = com.cozooo.dlc_manager.core.DlcDcConfigIO.getStringIgnoreCase(obj, "type");
                                            String fTarget = com.cozooo.dlc_manager.core.DlcDcConfigIO.getStringIgnoreCase(obj, "appliedTarget");
                                            diag.append("  [.dc] ").append(fn)
                                                .append(" | fileName=").append(fName)
                                                .append(" | type=").append(fType)
                                                .append(" | appliedTarget=").append(fTarget)
                                                .append('\n');
                                        } else {
                                            diag.append("  [.dc] ").append(fn).append(" | 解析失败(返回null)\n");
                                        }
                                    } catch (Exception ex) {
                                        diag.append("  [.dc] ").append(fn).append(" | 读取异常: ").append(ex.getMessage()).append('\n');
                                    }
                                } else {
                                    otherCount++;
                                    diag.append("  [非.dc] ").append(fn).append('\n');
                                }
                            }
                            diag.append("  .dc 配置文件数: ").append(dcCount).append('\n');
                            if (otherCount > 0) {
                                diag.append("  其他文件数: ").append(otherCount).append(" (这些不会被识别为配置文件)\n");
                            }
                        } catch (Exception ex) {
                            diag.append("  读取 info 目录失败: ").append(ex.getMessage()).append('\n');
                        }
                    }
                    // 诊断实际 DLC 文件
                    if (exists) {
                        try (var stream = java.nio.file.Files.walk(root)) {
                            var allFiles = stream.toList();
                            long jars = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".jar")).count();
                            long zips = allFiles.stream().filter(p -> p.toString().toLowerCase().endsWith(".zip")).count();
                            diag.append("  DLC 目录下 .jar 文件数: ").append(jars).append('\n');
                            diag.append("  DLC 目录下 .zip 文件数: ").append(zips).append('\n');
                            // 列出所有非 info 目录下的文件
                            java.nio.file.Path infoDir0 = root.resolve("info");
                            for (var p : allFiles) {
                                if (java.nio.file.Files.isRegularFile(p) && !p.startsWith(infoDir0)) {
                                    String rel = root.relativize(p).toString().replace('\\', '/');
                                    diag.append("  [文件] ").append(rel).append('\n');
                                }
                            }
                        } catch (Exception ex) {
                            diag.append("  遍历 DLC 目录失败: ").append(ex.getMessage()).append('\n');
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
                final int total = flat.size();
                final String diagText = diag.toString();
                System.out.println("[DLC-Scan-Debug]\n" + diagText + "total=" + total);
                Platform.runLater(() -> {
                    allItems.setAll(flat);
                    countLabel.setText(String.format("共 %d 项", total));
                    progressBar.setVisible(false);
                    if (total == 0) {
                        String msg = anyRootExists[0]
                            ? "未找到 DLC — DLC 目录存在但无可用项"
                            : "未找到 DLC 目录 — 请确认游戏目录下有 DLC 文件夹";
                        statusLabel.setText(msg);
                        statusLabel.setTooltip(new Tooltip(diagText));
                    } else {
                        statusLabel.setText("扫描完成");
                        statusLabel.setTooltip(new Tooltip(diagText));
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("扫描失败: " + safeMessage(t));
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
        statusLabel.setText("应用中…");
        Thread thread = new Thread(() -> {
            try {
                DlcApplier.ApplyResult result = DlcApplier.apply(new ArrayList<>(allItems), finalMode);
                DlcStateConfig.save();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    StringBuilder sb = new StringBuilder();
                    if (result.requiresRestart()) {
                        sb.append("需要重启游戏以生效。");
                    }
                    if (result.hotReloaded()) {
                        sb.append("已热重载。");
                    }
                    if (result.helperScheduled()) {
                        sb.append("部分操作已安排到 FileOps Helper。");
                    }
                    if (sb.length() == 0) {
                        sb.append("应用完成");
                    }
                    if (!result.messages().isEmpty()) {
                        sb.append(" (").append(result.messages().size()).append(" 条消息)");
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
                    statusLabel.setText("应用失败: " + safeMessage(t));
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
            typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
            nameLabel.setStyle("-fx-text-fill: #333;");
            checkBox.setOnAction(e -> {
                DlcItem item = getItem();
                if (item != null) {
                    item.setEnabled(checkBox.isSelected());
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
                typeLabel.setText("[" + item.getType().name() + "]");
                nameLabel.setText(item.getName() != null ? item.getName() : item.getId());
                StringBuilder tip = new StringBuilder();
                tip.append("ID: ").append(item.getId());
                if (item.getSourcePath() != null) {
                    tip.append("\n源: ").append(item.getSourcePath());
                }
                if (item.getAppliedTargetRaw() != null && !item.getAppliedTargetRaw().isBlank()) {
                    tip.append("\n目标: ").append(item.getAppliedTargetRaw());
                }
                if (!item.getDependencies().isEmpty()) {
                    tip.append("\n依赖: ").append(String.join(", ", item.getDependencies()));
                }
                if (!item.getConflicts().isEmpty()) {
                    tip.append("\n冲突: ").append(String.join(", ", item.getConflicts()));
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
}
