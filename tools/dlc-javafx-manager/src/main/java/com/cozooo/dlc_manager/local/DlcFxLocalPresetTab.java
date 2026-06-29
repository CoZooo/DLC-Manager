package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.preset.DlcPresetStateStore;
import com.cozooo.dlc_manager.app.DlcFxManagerApp;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DlcFxLocalPresetTab {

    private final DlcFxPlatformProvider provider;
    private final ObservableList<DlcPresetEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final Label countLabel = new Label("");
    private Button refreshBtn;
    private Button applyBtn;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalPresetTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_preset"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.refresh"));
        refreshBtn.setDisable(true);
        refreshBtn.setOnAction(e -> {
            if (gameDirSelected) {
                refreshAsync();
            }
        });

        applyBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.apply"));
        applyBtn.setDisable(true);
        applyBtn.setOnAction(e -> applyAsync());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(statusLabel);

        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox selectionActions = new HBox(8, selectNoneBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);

        HBox topBox = new HBox(8, refreshBtn, countLabel, topSpacer, selectionActions);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        ListView<DlcPresetEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new PresetCell());
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox center = new VBox(DlcFxListViewSupport.wrap(listView));
        center.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        selectNoneBtn.setOnAction(e -> setAllPresetEnabled(false));

        HBox actionPanel = new HBox(18, applyBtn, statusLabel);
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
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.game_dir_changed"));
        if (refreshBtn != null) {
            refreshBtn.setDisable(false);
        }
        if (applyBtn != null) {
            applyBtn.setDisable(false);
        }
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
            if (refreshBtn != null) {
                refreshBtn.setDisable(true);
            }
            return;
        }
        loadedForCurrentGameDir = true;
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
                DlcPresetRuntimeState.syncStatusFromRuntime();
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_done"));
                    statusLabel.setTooltip(null);
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

    private void resetPresetChanges() {
        DlcPresetRuntimeState.reload();
        entries.setAll(DlcPresetRuntimeState.getPresets());
        countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", entries.size(), countEnabled(entries)));
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.canceled_value", DlcFxI18n.tr("dlc_manager.preset.clear_all")));
    }

    private void setAllPresetEnabled(boolean enabled) {
        boolean changed = false;
        for (DlcPresetEntry entry : DlcPresetRuntimeState.getPresets()) {
            if (entry == null) {
                continue;
            }
            changed |= DlcPresetRuntimeState.setPresetEnabled(entry.getId(), enabled);
        }
        if (changed || !entries.isEmpty()) {
            DlcPresetRuntimeState.applyOverridesToRuntime();
            saveAndRefresh();
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

    private void confirmAlarmPreset(DlcPresetEntry item, javafx.scene.Scene scene) {
        String tip = item == null ? "" : item.resolveTip(currentLang());
        javafx.scene.Node message = tip == null || tip.isBlank()
                ? DlcFxRichTextRenderer.formatted(DlcFxI18n.tr("dlc_manager.preset.alarm_confirm"))
                : DlcFxRichTextRenderer.formatted(tip);
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.preset.alarm_title"),
                message,
                () -> onPresetAlarmConfirmed(item, scene),
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void onPresetAlarmConfirmed(DlcPresetEntry item, javafx.scene.Scene scene) {
        if (item == null) {
            return;
        }
        boolean ok = DlcPresetRuntimeState.setPresetEnabled(item.getId(), true);
        if (!ok) {
            return;
        }
        DlcPresetRuntimeState.applyOverridesToRuntime();
        saveAndRefresh();
        showGoToDlcManagementPrompt(scene);
    }

    private void showGoToDlcManagementPrompt(javafx.scene.Scene scene) {
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.preset.title"),
                DlcFxI18n.tr("dlc_manager.preset.apply_hint"),
                () -> {
                    DlcFxManagerApp app = DlcFxManagerApp.instance();
                    if (app != null) {
                        app.showLocalDlcManagementTab();
                    }
                },
                DlcFxI18n.tr("dlc_manager.preset.goto_dlc"),
                null,
                null
        );
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
                    if (enabled && item.isAlarm()) {
                        checkBox.setSelected(item.getStatus().isEnabled());
                        confirmAlarmPreset(item, checkBox.getScene());
                        return;
                    }
                    if (enabled && !DlcPresetRuntimeState.setPresetEnabled(item.getId(), true)) {
                        checkBox.setSelected(item.getStatus().isEnabled());
                        List<String> blockers = DlcPresetRuntimeState.getPresetBlockerNames(item.getId(), currentLang());
                        String msg = blockers.isEmpty() ? DlcFxI18n.tr("dlc_manager.preset.blocked_by_conflict") : DlcFxI18n.tr("dlc_manager.preset.blocked_by", String.join(", ", blockers));
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.blocked"));
                        setTooltip(DlcFxRichTextRenderer.formattedTooltip(msg));
                        return;
                    }
                    if (!enabled) {
                        DlcPresetRuntimeState.setPresetEnabled(item.getId(), false);
                        updateStatusDisplay(item);
                        DlcPresetRuntimeState.applyOverridesToRuntime();
                        saveAndRefresh();
                        return;
                    }
                    updateStatusDisplay(item);
                    DlcPresetRuntimeState.applyOverridesToRuntime();
                    saveAndRefresh();
                    showGoToDlcManagementPrompt(checkBox.getScene());
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
                DlcFxRichTextRenderer.setFormatted(nameLabel, item.resolveDisplayName(lang));
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
                setTooltip(DlcFxRichTextRenderer.formattedTooltip(tip.toString()));
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
