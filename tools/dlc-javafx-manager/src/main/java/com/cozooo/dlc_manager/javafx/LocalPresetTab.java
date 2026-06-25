package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.preset.DlcPresetStateStore;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.platform.PlatformServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [本地] Preset 预设 — 列出所有预设、切换启用状态、应用更改.
 * <p>
 * 预设是 .dc 配置文件，定义一组要启用的 DLC 标识符和冲突项。
 * 启用预设后，对应的 DLC 会被启用，冲突的 DLC 会被禁用。
 */
final class LocalPresetTab {

    private final StandalonePlatformProvider provider;
    private final ObservableList<DlcPresetEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("请先在「本地 · DLC 管理」中选择游戏目录");
    private final Label countLabel = new Label("");

    LocalPresetTab(StandalonePlatformProvider provider) {
        this.provider = provider;
    }

    Tab build() {
        Tab tab = new Tab("本地 · Preset 预设");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: refresh + apply ──────────────────────────────────
        Button refreshBtn = new Button("刷新预设");
        refreshBtn.setStyle("-fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> refreshAsync());

        Button applyBtn = new Button("应用预设");
        applyBtn.setStyle("-fx-font-weight: bold;");
        applyBtn.setOnAction(e -> applyAsync());

        Button clearBtn = new Button("全部关闭");
        clearBtn.setOnAction(e -> {
            for (DlcPresetEntry entry : entries) {
                DlcPresetRuntimeState.setPresetEnabled(entry.getId(), false);
            }
            saveAndRefresh();
        });

        HBox topBox = new HBox(8, refreshBtn, applyBtn, clearBtn, countLabel, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: preset list ───────────────────────────────────
        ListView<DlcPresetEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new PresetCell());
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox center = new VBox(listView);
        center.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    private void refreshAsync() {
        statusLabel.setText("加载中…");
        Thread thread = new Thread(() -> {
            try {
                List<DlcPresetEntry> loaded = DlcPresetRuntimeState.getPresets();
                if (loaded.isEmpty()) {
                    DlcPresetRuntimeState.reload();
                    loaded = DlcPresetRuntimeState.getPresets();
                }
                final List<DlcPresetEntry> finalLoaded = loaded;
                Platform.runLater(() -> {
                    entries.setAll(finalLoaded);
                    int enabled = countEnabled(finalLoaded);
                    countLabel.setText(String.format("共 %d 个预设，已启用 %d", finalLoaded.size(), enabled));
                    statusLabel.setText(finalLoaded.isEmpty() ? "未找到预设 (dlc/preset/*.dc)" : "已加载");
                });
            } catch (Throwable t) {
                Platform.runLater(() -> statusLabel.setText("加载失败: " + safeMessage(t)));
            }
        }, "dlc-preset-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync() {
        statusLabel.setText("应用中…");
        Thread thread = new Thread(() -> {
            try {
                DlcPresetRuntimeState.applyOverridesToRuntime();
                DlcApplier.ApplyResult result = DlcApplier.apply(new ArrayList<>(), DlcApplyMode.RESTART_ONLY);
                DlcPresetRuntimeState.syncStatusFromRuntime();
                Platform.runLater(() -> {
                    StringBuilder sb = new StringBuilder();
                    if (result.requiresRestart()) {
                        sb.append("需要重启游戏以生效。");
                    }
                    if (result.hotReloaded()) {
                        sb.append("已热重载。");
                    }
                    if (sb.length() == 0) {
                        sb.append("应用完成");
                    }
                    if (!result.messages().isEmpty()) {
                        sb.append(" (").append(result.messages().size()).append(" 条消息)");
                    }
                    statusLabel.setText(sb.toString());
                    // Surface the per-item apply messages like the DLC tab does.
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
                    // Refresh to update PART/ON/OFF statuses
                    entries.setAll(DlcPresetRuntimeState.getPresets());
                });
            } catch (Throwable t) {
                Platform.runLater(() -> statusLabel.setText("应用失败: " + safeMessage(t)));
            }
        }, "dlc-preset-apply");
        thread.setDaemon(true);
        thread.start();
    }

    private void saveAndRefresh() {
        try {
            Map<String, DlcPresetStatus> statuses = new LinkedHashMap<>();
            for (DlcPresetEntry entry : DlcPresetRuntimeState.getPresets()) {
                statuses.put(entry.getId(), entry.getStatus());
            }
            DlcPresetStateStore.savePresetStatus(statuses);
            entries.setAll(DlcPresetRuntimeState.getPresets());
            int enabled = countEnabled(entries);
            countLabel.setText(String.format("共 %d 个预设，已启用 %d", entries.size(), enabled));
            statusLabel.setText("已保存");
        } catch (Throwable t) {
            statusLabel.setText("保存失败: " + safeMessage(t));
        }
    }

    private static int countEnabled(List<DlcPresetEntry> list) {
        int count = 0;
        for (DlcPresetEntry e : list) {
            if (e.getStatus().isEnabled()) count++;
        }
        return count;
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private static final class PresetCell extends javafx.scene.control.ListCell<DlcPresetEntry> {
        private final CheckBox checkBox = new CheckBox();
        private final Label statusLabel = new Label();
        private final Label nameLabel = new Label();
        private final HBox row = new HBox(8, checkBox, statusLabel, nameLabel);

        PresetCell() {
            statusLabel.setStyle("-fx-font-weight: bold;");
            checkBox.setOnAction(e -> {
                DlcPresetEntry item = getItem();
                if (item != null) {
                    boolean enabled = checkBox.isSelected();
                    boolean wasBlocked = DlcPresetRuntimeState.isPresetBlocked(item.getId());
                    boolean ok = DlcPresetRuntimeState.setPresetEnabled(item.getId(), enabled);
                    if (!ok && enabled) {
                        checkBox.setSelected(false);
                        List<String> blockers = DlcPresetRuntimeState.getPresetBlockerNames(item.getId(), "zh_cn");
                        String msg = blockers.isEmpty() ? "被冲突预设阻止" : "被阻止: " + String.join(", ", blockers);
                        statusLabel.setText("[阻止]");
                        setTooltip(new Tooltip(msg));
                    } else {
                        updateStatusDisplay(item);
                    }
                }
            });
        }

        @Override
        protected void updateItem(DlcPresetEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                checkBox.setSelected(item.getStatus().isEnabled());
                updateStatusDisplay(item);
                nameLabel.setText(item.resolveDisplayName("zh_cn"));
                StringBuilder tip = new StringBuilder();
                tip.append("ID: ").append(item.getId());
                tip.append("\n文件: ").append(item.getFileName());
                if (!item.getEnable().isEmpty()) {
                    tip.append("\n启用: ").append(String.join(", ", item.getEnable()));
                }
                if (!item.getConflicts().isEmpty()) {
                    tip.append("\n冲突: ").append(String.join(", ", item.getConflicts()));
                }
                if (item.isAlarm()) {
                    tip.append("\n[警告预设]");
                }
                String tipText = item.resolveTip("zh_cn");
                if (tipText != null && !tipText.isBlank()) {
                    tip.append("\n说明: ").append(tipText);
                }
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }

        private void updateStatusDisplay(DlcPresetEntry item) {
            DlcPresetStatus status = item.getStatus();
            switch (status) {
                case ON -> {
                    statusLabel.setText("[ON]");
                    statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                }
                case PART -> {
                    statusLabel.setText("[PART]");
                    statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                }
                case OFF -> {
                    statusLabel.setText("[OFF]");
                    statusLabel.setStyle("-fx-text-fill: #999; -fx-font-weight: bold;");
                }
            }
        }
    }
}
