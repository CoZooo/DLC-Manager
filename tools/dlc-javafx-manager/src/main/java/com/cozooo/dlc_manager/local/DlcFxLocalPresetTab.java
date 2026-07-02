package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.common.DlcFxActionPanel;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStatusChip;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxTooltips;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.app.DlcFxManagerApp;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DlcFxLocalPresetTab {

    private final ObservableList<DlcPresetEntry> entries = FXCollections.observableArrayList();
    private final Map<String, Boolean> pendingPresetEnabled = new LinkedHashMap<>();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final Label countLabel = new Label("");
    private ListView<DlcPresetEntry> listView;
    private Button refreshBtn;
    private Button applyBtn;
    private Button cancelBtn;
    private Label pendingWarningLabel;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private Runnable capturePresetSnapshot = () -> {};
    private Supplier<Map<String, Boolean>> snapshotDlcState = Map::of;
    private Function<Map<String, Boolean>, Boolean> syncPresetDlcChanges =
        ignored -> syncIndexedDlcOverridesAndHasPendingChanges();

    public DlcFxLocalPresetTab(DlcFxPlatformProvider provider) {
    }

    public void setPresetSnapshotActions(Runnable capturePresetSnapshot) {
        this.capturePresetSnapshot = capturePresetSnapshot == null ? () -> {} : capturePresetSnapshot;
    }

    public void setPresetStateActions(Runnable capturePresetSnapshot,
                                      Supplier<Map<String, Boolean>> snapshotDlcState,
                                      Function<Map<String, Boolean>, Boolean> syncPresetDlcChanges) {
        this.capturePresetSnapshot = capturePresetSnapshot == null ? () -> {} : capturePresetSnapshot;
        this.snapshotDlcState = snapshotDlcState == null ? Map::of : snapshotDlcState;
        this.syncPresetDlcChanges = syncPresetDlcChanges == null
            ? ignored -> syncIndexedDlcOverridesAndHasPendingChanges()
            : syncPresetDlcChanges;
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));

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
        cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.local.cancel_changes"));
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelPendingChanges());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(statusLabel);

        Button disableAllBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.clear_all"));
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox selectionActions = new HBox(8, disableAllBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);

        HBox topBox = new HBox(8, refreshBtn, countLabel, topSpacer, selectionActions);
        topBox.setAlignment(Pos.CENTER_LEFT);
        page.setTop(DlcFxToolbar.vertical(topBox));

        listView = new ListView<>(entries);
        DlcFxStyle.add(listView, "dlc-preset-list");
        listView.setCellFactory(param -> new PresetCell());
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox center = new VBox(DlcFxListViewSupport.wrap(listView));
        page.setCenter(page.section(center));

        disableAllBtn.setOnAction(e -> setAllPresetEnabled(false));

        HBox applyActions = new HBox(8, applyBtn, cancelBtn);
        applyActions.setAlignment(Pos.CENTER_LEFT);
        pendingWarningLabel = new Label(DlcFxI18n.tr("dlc_manager.pending.unapplied_changes"));
        DlcFxStyle.add(pendingWarningLabel, "dlc-pending-warning");
        pendingWarningLabel.setVisible(false);
        pendingWarningLabel.setManaged(false);
        HBox actionRow = new HBox(18, applyActions, pendingWarningLabel, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        VBox actionPanel = DlcFxActionPanel.panel(actionRow);
        DlcFxStyle.routeStatus(statusLabel);
        page.setBottom(actionPanel);

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.preset"), page.root());
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        entries.clear();
        pendingPresetEnabled.clear();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.game_dir_changed"));
        if (refreshBtn != null) {
            refreshBtn.setDisable(false);
        }
        if (applyBtn != null) {
            applyBtn.setDisable(true);
        }
        if (cancelBtn != null) {
            cancelBtn.setDisable(true);
        }
        updateActionState();
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
                    pendingPresetEnabled.clear();
                    entries.setAll(finalLoaded);
                    int enabled = countEffectiveEnabled(finalLoaded);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", finalLoaded.size(), enabled));
                    statusLabel.setText(finalLoaded.isEmpty() ? DlcFxI18n.tr("dlc_manager.preset.none") : DlcFxI18n.tr("dlc_manager.status.loaded"));
                    updateActionState();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t))));
            }
        }, "dlc-preset-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync() {
        if (pendingPresetEnabled.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
            updateActionState();
            return;
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        Map<String, Boolean> pending = new LinkedHashMap<>(pendingPresetEnabled);
        capturePresetSnapshot.run();
        Map<String, Boolean> beforeDlcState = snapshotDlcState.get();
        Thread thread = new Thread(() -> {
            try {
                for (Map.Entry<String, Boolean> entry : pending.entrySet()) {
                    DlcPresetRuntimeState.setPresetEnabled(entry.getKey(), entry.getValue());
                }
                DlcPresetRuntimeState.applyOverridesToRuntime();
                DlcPresetRuntimeState.syncStatusFromRuntime();
                boolean dlcChanged = syncPresetDlcChanges.apply(beforeDlcState);
                Platform.runLater(() -> {
                    pendingPresetEnabled.clear();
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_done"));
                    statusLabel.setTooltip(null);
                    entries.setAll(DlcPresetRuntimeState.getPresets());
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", entries.size(), countEffectiveEnabled(entries)));
                    updateActionState();
                    if (dlcChanged) {
                        showGoToDlcManagementPrompt(applyBtn.getScene());
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                    updateActionState();
                });
            }
        }, "dlc-preset-apply");
        thread.setDaemon(true);
        thread.start();
    }

    static boolean syncIndexedDlcOverridesAndHasPendingChanges() {
        boolean pending = false;
        for (DlcItem item : DlcRuntimeState.getIndexedItems()) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                pending = true;
            }
        }
        return pending;
    }

    private void setAllPresetEnabled(boolean enabled) {
        for (DlcPresetEntry entry : DlcPresetRuntimeState.getPresets()) {
            if (entry == null) {
                continue;
            }
            setPendingPresetEnabled(entry, enabled);
        }
        refreshPresetRows();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
    }

    public boolean hasPendingChanges() {
        return !pendingPresetEnabled.isEmpty();
    }

    public void applyPendingChanges() {
        if (hasPendingChanges()) {
            applyAsync();
        }
    }

    public void cancelPendingChanges() {
        if (!hasPendingChanges()) {
            return;
        }
        pendingPresetEnabled.clear();
        refreshPresetRows();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.canceled_value", DlcFxI18n.tr("dlc_manager.local.cancel_changes")));
    }

    private void updateActionState() {
        if (applyBtn != null) {
            applyBtn.setDisable(!gameDirSelected || !hasPendingChanges());
        }
        if (cancelBtn != null) {
            cancelBtn.setDisable(!gameDirSelected || !hasPendingChanges());
        }
        if (pendingWarningLabel != null) {
            pendingWarningLabel.setVisible(hasPendingChanges());
            pendingWarningLabel.setManaged(hasPendingChanges());
        }
    }

    private void refreshPresetRows() {
        if (listView != null) {
            listView.refresh();
        } else {
            entries.setAll(DlcPresetRuntimeState.getPresets());
        }
        countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", entries.size(), countEffectiveEnabled(entries)));
        updateActionState();
    }

    private boolean effectiveEnabled(DlcPresetEntry entry) {
        if (entry == null) {
            return false;
        }
        Boolean pending = pendingPresetEnabled.get(entry.getId());
        return pending != null ? pending : entry.getStatus().isEnabled();
    }

    private boolean isPending(DlcPresetEntry entry) {
        if (entry == null) {
            return false;
        }
        Boolean pending = pendingPresetEnabled.get(entry.getId());
        return pending != null && pending != entry.getStatus().isEnabled();
    }

    private void setPendingPresetEnabled(DlcPresetEntry entry, boolean enabled) {
        if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
            return;
        }
        if (enabled == entry.getStatus().isEnabled()) {
            pendingPresetEnabled.remove(entry.getId());
        } else {
            pendingPresetEnabled.put(entry.getId(), enabled);
        }
    }

    private List<String> pendingBlockerNames(DlcPresetEntry target) {
        if (target == null) {
            return List.of();
        }
        Set<String> targetEnable = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(target.getId());
        if (targetEnable.isEmpty()) {
            return List.of();
        }
        Set<String> targetConflicts = DlcPresetRuntimeState.resolveEffectiveConflictIdentifiers(target.getId());
        List<String> blockers = new ArrayList<>();
        String lang = currentLang();
        for (DlcPresetEntry entry : entries) {
            if (entry == null || entry == target || !effectiveEnabled(entry)) {
                continue;
            }
            Set<String> enabled = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(entry.getId());
            Set<String> conflicts = DlcPresetRuntimeState.resolveEffectiveConflictIdentifiers(entry.getId());
            if (!intersects(targetEnable, conflicts) && !intersects(targetConflicts, enabled)) {
                continue;
            }
            blockers.add(entry.resolveDisplayName(lang));
        }
        return blockers;
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int countEffectiveEnabled(List<DlcPresetEntry> list) {
        int count = 0;
        for (DlcPresetEntry e : list) {
            if (effectiveEnabled(e)) {
                count++;
            }
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
        setPendingPresetEnabled(item, true);
        refreshPresetRows();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
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
                () -> {},
                DlcFxI18n.tr("dlc_manager.preset.goto_later")
        );
    }

    private final class PresetCell extends javafx.scene.control.ListCell<DlcPresetEntry> {
        private final Label statusLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label pendingLabel = new Label();
        private final Button toggleButton = new Button();
        private final Region spacer = new Region();
        private final HBox row = new HBox(10, statusLabel, nameLabel, pendingLabel, spacer, toggleButton);
        private javafx.scene.control.Tooltip rowTooltip;

        PresetCell() {
            DlcFxStatusChip.apply(statusLabel, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.add(pendingLabel, "dlc-pending-badge");
            DlcFxStyle.add(toggleButton, "dlc-toggle-off");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            row.prefWidthProperty().bind(widthProperty().subtract(20));
            HBox.setHgrow(spacer, Priority.ALWAYS);
            pendingLabel.setMinWidth(64);
            pendingLabel.setPrefWidth(64);
            pendingLabel.setMaxWidth(64);
            pendingLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
            toggleButton.setMinWidth(96);
            toggleButton.setPrefWidth(96);
            toggleButton.setMaxWidth(96);
            addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInsideButton(event.getTarget())) {
                    if (listView != null) {
                        listView.getSelectionModel().clearSelection();
                    }
                    event.consume();
                }
            });
            toggleButton.setOnAction(e -> {
                DlcPresetEntry item = getItem();
                if (item != null) {
                    boolean enabled = !effectiveEnabled(item);
                    if (enabled) {
                        List<String> blockers = pendingBlockerNames(item);
                        if (blockers.isEmpty() && DlcPresetRuntimeState.isPresetBlocked(item.getId())) {
                            blockers = DlcPresetRuntimeState.getPresetBlockerNames(item.getId(), currentLang());
                        }
                        if (!blockers.isEmpty()) {
                            String msg = DlcFxI18n.tr("dlc_manager.preset.blocked_by", String.join(", ", blockers));
                            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.blocked"));
                            DlcFxStatusChip.apply(statusLabel, DlcFxStyle.CHIP_ERROR);
                            rowTooltip = DlcFxRichTextRenderer.formattedTooltip(msg);
                            return;
                        }
                    }
                    if (enabled && item.isAlarm()) {
                        confirmAlarmPreset(item, toggleButton.getScene());
                        return;
                    }
                    setPendingPresetEnabled(item, enabled);
                    refreshPresetRows();
                    DlcFxLocalPresetTab.this.statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
                }
            });
            DlcFxTooltips.followMouse(this, () -> rowTooltip);
        }

        @Override
        protected void updateItem(DlcPresetEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                rowTooltip = null;
                setGraphic(null);
                setText(null);
            } else {
                updateStatusDisplay(item);
                String lang = currentLang();
                DlcFxRichTextRenderer.setFormatted(nameLabel, item.resolveDisplayName(lang));
                String tipText = item.resolveTip(lang);
                StringBuilder tip = new StringBuilder();
                if (tipText != null && !tipText.isBlank()) {
                    tip.append(tipText);
                }
                List<String> blockers = DlcPresetRuntimeState.getPresetBlockerNames(item.getId(), lang);
                if (!blockers.isEmpty()) {
                    if (tip.length() > 0) {
                        tip.append("\n\n");
                    }
                    for (String blocker : blockers) {
                        if (tip.length() > 0 && tip.charAt(tip.length() - 1) != '\n') {
                            tip.append('\n');
                        }
                        tip.append("§4").append(DlcFxI18n.tr("dlc_manager.preset.conflict", blocker)).append("§r");
                    }
                }
                rowTooltip = DlcFxRichTextRenderer.formattedTooltip(tip.toString());
                setGraphic(row);
            }
        }

        private void updateStatusDisplay(DlcPresetEntry item) {
            DlcPresetStatus status = displayStatus(item);
            switch (status) {
                case ON -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.preset.status_on"));
                    DlcFxStatusChip.apply(statusLabel, DlcFxStyle.CHIP_OK);
                }
                case PART -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.preset.status_part"));
                    DlcFxStatusChip.apply(statusLabel, DlcFxStyle.CHIP_WARN);
                }
                case OFF -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.preset.status_off"));
                    DlcFxStatusChip.apply(statusLabel, DlcFxStyle.CHIP_NEUTRAL);
                }
            }
            boolean pending = isPending(item);
            pendingLabel.setVisible(pending);
            pendingLabel.setManaged(pending);
            toggleButton.setText(effectiveEnabled(item)
                ? DlcFxI18n.tr("dlc_manager.preset.disable")
                : DlcFxI18n.tr("dlc_manager.preset.enable"));
            toggleButton.getStyleClass().removeAll("dlc-toggle-on", "dlc-toggle-off", "dlc-toggle-pending");
            toggleButton.getStyleClass().add(pending
                ? "dlc-toggle-pending"
                : effectiveEnabled(item) ? "dlc-toggle-on" : "dlc-toggle-off");
        }

        private DlcPresetStatus displayStatus(DlcPresetEntry item) {
            if (item == null) {
                return DlcPresetStatus.OFF;
            }
            Boolean pending = pendingPresetEnabled.get(item.getId());
            if (pending == null) {
                if (!item.getStatus().isEnabled()) {
                    return DlcPresetStatus.OFF;
                }
                return statusFromCurrentDlcState(item);
            }
            if (!pending) {
                return DlcPresetStatus.OFF;
            }
            return statusFromPendingEnable(item);
        }

        private DlcPresetStatus statusFromCurrentDlcState(DlcPresetEntry item) {
            if (item == null) {
                return DlcPresetStatus.OFF;
            }
            Set<String> identifiers = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(item.getId());
            if (identifiers.isEmpty()) {
                return DlcPresetStatus.ON;
            }
            boolean anyEnabled = false;
            boolean allEnabled = true;
            for (String identifier : identifiers) {
                DlcItem dlc = DlcRuntimeState.getItemByIdentifier(identifier);
                boolean enabled = dlc != null && effectiveDlcEnabled(dlc);
                anyEnabled |= enabled;
                allEnabled &= enabled;
            }
            if (allEnabled) {
                return DlcPresetStatus.ON;
            }
            return anyEnabled ? DlcPresetStatus.PART : DlcPresetStatus.PART;
        }

        private DlcPresetStatus statusFromPendingEnable(DlcPresetEntry item) {
            if (item == null) {
                return DlcPresetStatus.OFF;
            }
            Set<String> identifiers = DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(item.getId());
            if (identifiers.isEmpty()) {
                return DlcPresetStatus.ON;
            }
            for (String identifier : identifiers) {
                if (DlcRuntimeState.getItemByIdentifier(identifier) == null) {
                    return DlcPresetStatus.PART;
                }
            }
            return DlcPresetStatus.ON;
        }

        private boolean effectiveDlcEnabled(DlcItem item) {
            if (item == null) {
                return false;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            return override != null ? override : item.isEnabled();
        }

        private boolean isInsideButton(Object target) {
            Node node = target instanceof Node ? (Node) target : null;
            while (node != null) {
                if (node instanceof ButtonBase) {
                    return true;
                }
                node = node.getParent();
            }
            return false;
        }
    }
}
