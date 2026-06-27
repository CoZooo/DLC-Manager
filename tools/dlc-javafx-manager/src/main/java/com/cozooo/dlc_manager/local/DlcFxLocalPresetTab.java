package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.preset.DlcPresetStateStore;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
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
public final class DlcFxLocalPresetTab {

    private final DlcFxPlatformProvider provider;
    private final ObservableList<DlcPresetEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final Label countLabel = new Label("");

    public DlcFxLocalPresetTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_preset"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: refresh + summary ────────────────────────────────
        Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.refresh"));
        refreshBtn.setOnAction(e -> refreshAsync());

        Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.apply"));
        applyBtn.setOnAction(e -> applyAsync());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(statusLabel);

        Button clearBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.clear_all"));
        clearBtn.setOnAction(e -> {
            for (DlcPresetEntry entry : entries) {
                DlcPresetRuntimeState.setPresetEnabled(entry.getId(), false);
            }
            DlcPresetRuntimeState.applyOverridesToRuntime();
            saveAndRefresh();
        });

        HBox topBox = new HBox(8, refreshBtn, countLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: preset list ───────────────────────────────────
        ListView<DlcPresetEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new PresetCell());
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox center = new VBox(listView);
        center.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        Label applyTitle = new Label(DlcFxI18n.tr("dlc_manager.preset.apply"));
        Label clearTitle = new Label(DlcFxI18n.tr("dlc_manager.preset.clear_all"));
        DlcFxStyle.actionTitle(applyTitle);
        DlcFxStyle.actionTitle(clearTitle);
        DlcFxStyle.danger(clearBtn);

        VBox applyBox = new VBox(8, applyTitle, applyBtn);
        VBox clearBox = new VBox(8, clearTitle, clearBtn);
        HBox actionPanel = new HBox(18, applyBox, clearBox, statusLabel);
        HBox.setHgrow(applyBox, Priority.ALWAYS);
        HBox.setHgrow(clearBox, Priority.ALWAYS);
        actionPanel.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        return tab;
    }

    private void refreshAsync() {
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
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
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", finalLoaded.size(), enabled));
                    statusLabel.setText(finalLoaded.isEmpty() ? DlcFxI18n.tr("dlc_manager.preset.none") : DlcFxI18n.tr("dlc_manager.status.loaded"));
                });
            } catch (Throwable t) {
                Platform.runLater(() -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t))));
            }
        }, "dlc-preset-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync() {
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        Thread thread = new Thread(() -> {
            try {
                DlcPresetRuntimeState.applyOverridesToRuntime();
                DlcApplier.ApplyResult result = DlcApplier.apply(new ArrayList<>(), DlcApplyMode.RESTART_ONLY);
                DlcPresetRuntimeState.syncStatusFromRuntime();
                Platform.runLater(() -> {
                    StringBuilder sb = new StringBuilder();
                    if (result.requiresRestart()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.apply.requires_restart"));
                    }
                    if (result.hotReloaded()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.apply.hot_reloaded"));
                    }
                    if (sb.length() == 0) {
                        sb.append(DlcFxI18n.tr("dlc_manager.status.apply_done"));
                    }
                    if (!result.messages().isEmpty()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.status.message_count_suffix", result.messages().size()));
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
                Platform.runLater(() -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t))));
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
            DlcPresetRuntimeState.applyOverridesToRuntime();
            DlcPresetStateStore.savePresetStatus(statuses);
            entries.setAll(DlcPresetRuntimeState.getPresets());
            int enabled = countEnabled(entries);
            countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", entries.size(), enabled));
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.saved"));
        } catch (Throwable t) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.save_failed", safeMessage(t)));
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

    private static String currentLang() {
        String lang = CrossPlatformRuntime.languageCode();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private boolean confirmAlarmPreset(DlcPresetEntry item) {
        String tip = item == null ? "" : item.resolveTip(currentLang());
        String message = tip == null || tip.isBlank()
                ? DlcFxI18n.tr("dlc_manager.preset.alarm_confirm")
                : tip;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_no"), ButtonData.CANCEL_CLOSE),
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_yes"), ButtonData.OK_DONE));
        alert.setTitle(DlcFxI18n.tr("dlc_manager.preset.alarm_title"));
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
    }

    private final class PresetCell extends javafx.scene.control.ListCell<DlcPresetEntry> {
        private final CheckBox checkBox = new CheckBox();
        private final Label statusLabel = new Label();
        private final Label nameLabel = new Label();
        private final HBox row = new HBox(8, checkBox, statusLabel, nameLabel);

        PresetCell() {
            DlcFxStyle.chip(statusLabel, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.title(nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            checkBox.setOnAction(e -> {
                DlcPresetEntry item = getItem();
                if (item != null) {
                    boolean enabled = checkBox.isSelected();
                    if (enabled && item.isAlarm() && !confirmAlarmPreset(item)) {
                        checkBox.setSelected(item.getStatus().isEnabled());
                        return;
                    }
                    boolean ok = DlcPresetRuntimeState.setPresetEnabled(item.getId(), enabled);
                    if (!ok && enabled) {
                        checkBox.setSelected(false);
                        List<String> blockers = DlcPresetRuntimeState.getPresetBlockerNames(item.getId(), currentLang());
                        String msg = blockers.isEmpty() ? DlcFxI18n.tr("dlc_manager.preset.blocked_by_conflict") : DlcFxI18n.tr("dlc_manager.preset.blocked_by", String.join(", ", blockers));
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.blocked"));
                        setTooltip(new Tooltip(msg));
                    } else {
                        updateStatusDisplay(item);
                        DlcPresetRuntimeState.applyOverridesToRuntime();
                        saveAndRefresh();
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
                String lang = currentLang();
                nameLabel.setText(item.resolveDisplayName(lang));
                StringBuilder tip = new StringBuilder();
                tip.append(DlcFxI18n.tr("dlc_manager.tooltip.id")).append(item.getId());
                tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.file")).append(item.getFileName());
                if (!item.getEnable().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.enable")).append(String.join(", ", item.getEnable()));
                }
                if (!item.getConflicts().isEmpty()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.conflicts")).append(String.join(", ", item.getConflicts()));
                }
                if (item.isAlarm()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.preset.alarm_tag"));
                }
                String tipText = item.resolveTip(lang);
                if (tipText != null && !tipText.isBlank()) {
                    tip.append('\n').append(DlcFxI18n.tr("dlc_manager.tooltip.description")).append(tipText);
                }
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }

        private void updateStatusDisplay(DlcPresetEntry item) {
            DlcPresetStatus status = item.getStatus();
            switch (status) {
                case ON -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.preset.status_on"));
                    DlcFxStyle.chip(statusLabel, DlcFxStyle.CHIP_OK);
                }
                case PART -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.preset.status_part"));
                    DlcFxStyle.chip(statusLabel, DlcFxStyle.CHIP_WARN);
                }
                case OFF -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.preset.status_off"));
                    DlcFxStyle.chip(statusLabel, DlcFxStyle.CHIP_NEUTRAL);
                }
            }
        }
    }
}
