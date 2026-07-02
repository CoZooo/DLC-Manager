package com.cozooo.dlc_manager.ui.preset;

import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.ui.common.DlcFxActionPanel;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStatusChip;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxTooltips;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;
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

import java.util.List;
import java.util.Map;

public final class DlcFxPresetShell<T> {
    public interface Adapter<T> {
        boolean available();
        String unavailableStatusText();
        List<T> load() throws Exception;
        ApplyResult<T> apply(Map<String, Boolean> pending) throws Exception;
        String id(T entry);
        boolean enabled(T entry);
        boolean alarm(T entry);
        String displayName(T entry, String lang);
        String tip(T entry, String lang);
        DlcPresetStatus displayStatus(T entry, DlcFxPresetFlow<T> flow);
        List<String> blockerNames(T entry, List<T> entries, DlcFxPresetFlow<T> flow, String lang);
        void afterApplied(ApplyResult<T> result, javafx.scene.Scene scene);
    }

    public record ApplyResult<T>(List<T> entries, boolean dlcChanged, String statusText) {
    }

    private final Adapter<T> adapter;
    private final ObservableList<T> entries = FXCollections.observableArrayList();
    private final DlcFxPresetFlow<T> flow;
    private final Label statusLabel;
    private final Label countLabel = new Label("");
    private ListView<T> listView;
    private Button refreshBtn;
    private Button applyBtn;
    private Button cancelBtn;
    private Label pendingWarningLabel;
    private boolean loadedForCurrentContext;

    public DlcFxPresetShell(Adapter<T> adapter) {
        this.adapter = adapter;
        this.flow = new DlcFxPresetFlow<>(adapter::id, adapter::enabled);
        this.statusLabel = new Label(adapter.unavailableStatusText());
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(adapter.unavailableStatusText());

        refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.refresh"));
        refreshBtn.setOnAction(e -> refreshAsync());
        applyBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.apply"));
        applyBtn.setOnAction(e -> applyAsync());
        cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.local.cancel_changes"));
        cancelBtn.setOnAction(e -> cancelPendingChanges());
        Button disableAllBtn = new Button(DlcFxI18n.tr("dlc_manager.preset.clear_all"));
        disableAllBtn.setOnAction(e -> {
            flow.setAll(entries, false);
            refreshPresetRows();
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
        });
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(statusLabel);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topBox = new HBox(8, refreshBtn, countLabel, topSpacer, disableAllBtn);
        topBox.setAlignment(Pos.CENTER_LEFT);
        page.setTop(DlcFxToolbar.vertical(topBox));

        listView = new ListView<>(entries);
        DlcFxStyle.add(listView, "dlc-preset-list");
        listView.setCellFactory(param -> new PresetCell());
        HBox listFrame = DlcFxListViewSupport.wrap(listView);
        DlcFxTabPage.grow(listView);
        DlcFxTabPage.grow(listFrame);
        VBox center = new VBox(listFrame);
        DlcFxTabPage.grow(center);
        page.setCenter(page.section(center));

        HBox applyActions = new HBox(8, applyBtn, cancelBtn);
        applyActions.setAlignment(Pos.CENTER_LEFT);
        pendingWarningLabel = new Label(DlcFxI18n.tr("dlc_manager.pending.unapplied_changes"));
        DlcFxStyle.add(pendingWarningLabel, "dlc-pending-warning");
        HBox actionRow = new HBox(18, applyActions, pendingWarningLabel, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        VBox actionPanel = DlcFxActionPanel.panel(actionRow);
        DlcFxStyle.routeStatus(statusLabel);
        page.setBottom(actionPanel);

        updateActionState();
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.preset"), page.root());
    }

    public void onContextChanged(String statusText) {
        loadedForCurrentContext = false;
        entries.clear();
        flow.clear();
        countLabel.setText("");
        statusLabel.setText(statusText == null || statusText.isBlank() ? adapter.unavailableStatusText() : statusText);
        updateActionState();
    }

    public void onTabSelected() {
        if (!adapter.available() || loadedForCurrentContext) {
            updateActionState();
            return;
        }
        refreshAsync();
    }

    public boolean hasPendingChanges() {
        return flow.hasPendingChanges();
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
        flow.clear();
        refreshPresetRows();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.canceled_value", DlcFxI18n.tr("dlc_manager.local.cancel_changes")));
    }

    public void refreshAsync() {
        if (!adapter.available()) {
            statusLabel.setText(adapter.unavailableStatusText());
            updateActionState();
            return;
        }
        loadedForCurrentContext = true;
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        Thread thread = new Thread(() -> {
            try {
                List<T> loaded = adapter.load();
                Platform.runLater(() -> {
                    flow.clear();
                    entries.setAll(loaded == null ? List.of() : loaded);
                    updateCount();
                    statusLabel.setText(entries.isEmpty()
                            ? DlcFxI18n.tr("dlc_manager.preset.none")
                            : DlcFxI18n.tr("dlc_manager.status.loaded"));
                    updateActionState();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    updateActionState();
                });
            }
        }, "dlc-preset-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync() {
        if (!flow.hasPendingChanges()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
            updateActionState();
            return;
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        Map<String, Boolean> pending = flow.pendingChanges();
        Thread thread = new Thread(() -> {
            try {
                ApplyResult<T> result = adapter.apply(pending);
                Platform.runLater(() -> {
                    flow.clear();
                    if (result != null && result.entries() != null) {
                        entries.setAll(result.entries());
                    }
                    updateCount();
                    statusLabel.setText(result == null || result.statusText() == null || result.statusText().isBlank()
                            ? DlcFxI18n.tr("dlc_manager.status.apply_done")
                            : result.statusText());
                    statusLabel.setTooltip(null);
                    updateActionState();
                    adapter.afterApplied(result, applyBtn == null ? null : applyBtn.getScene());
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

    private void updateActionState() {
        boolean available = adapter.available();
        if (refreshBtn != null) {
            refreshBtn.setDisable(!available);
        }
        if (applyBtn != null) {
            applyBtn.setDisable(!available || !flow.hasPendingChanges());
        }
        if (cancelBtn != null) {
            cancelBtn.setDisable(!available || !flow.hasPendingChanges());
        }
        if (pendingWarningLabel != null) {
            pendingWarningLabel.setVisible(flow.hasPendingChanges());
            pendingWarningLabel.setManaged(flow.hasPendingChanges());
        }
    }

    private void refreshPresetRows() {
        if (listView != null) {
            listView.refresh();
        }
        updateCount();
        updateActionState();
    }

    private void updateCount() {
        int enabled = 0;
        for (T entry : entries) {
            if (flow.effectiveEnabled(entry)) {
                enabled++;
            }
        }
        countLabel.setText(DlcFxI18n.tr("dlc_manager.preset.count", entries.size(), enabled));
    }

    private void confirmAlarmPreset(T item, javafx.scene.Scene scene) {
        String tip = item == null ? "" : adapter.tip(item, currentLang());
        Node message = tip == null || tip.isBlank()
                ? DlcFxRichTextRenderer.formatted(DlcFxI18n.tr("dlc_manager.preset.alarm_confirm"))
                : DlcFxRichTextRenderer.formatted(tip);
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.preset.alarm_title"),
                message,
                () -> {
                    flow.setPending(item, true);
                    refreshPresetRows();
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
                },
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private static String currentLang() {
        String lang = CrossPlatformRuntime.languageCode();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        return msg == null ? t == null ? "Unknown" : t.getClass().getSimpleName() : msg;
    }

    private final class PresetCell extends javafx.scene.control.ListCell<T> {
        private final Label statusChip = new Label();
        private final Label nameLabel = new Label();
        private final Label pendingLabel = new Label();
        private final Button toggleButton = new Button();
        private final Region spacer = new Region();
        private final HBox row = new HBox(10, statusChip, nameLabel, pendingLabel, spacer, toggleButton);
        private javafx.scene.control.Tooltip rowTooltip;

        PresetCell() {
            DlcFxStatusChip.apply(statusChip, DlcFxStyle.CHIP_NEUTRAL);
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
            toggleButton.setOnAction(e -> toggleEntry());
            DlcFxTooltips.followMouse(this, () -> rowTooltip);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                rowTooltip = null;
                setGraphic(null);
                setText(null);
                return;
            }
            updateStatusDisplay(item);
            String lang = currentLang();
            DlcFxRichTextRenderer.setFormatted(nameLabel, adapter.displayName(item, lang));
            StringBuilder tip = new StringBuilder();
            String tipText = adapter.tip(item, lang);
            if (tipText != null && !tipText.isBlank()) {
                tip.append(tipText);
            }
            List<String> blockers = adapter.blockerNames(item, List.copyOf(entries), flow, lang);
            for (String blocker : blockers) {
                if (tip.length() > 0) {
                    tip.append("\n\n");
                }
                tip.append("§4").append(DlcFxI18n.tr("dlc_manager.preset.conflict", blocker)).append("§r");
            }
            rowTooltip = DlcFxRichTextRenderer.formattedTooltip(tip.toString());
            setGraphic(row);
        }

        private void toggleEntry() {
            T item = getItem();
            if (item == null) {
                return;
            }
            boolean enabled = !flow.effectiveEnabled(item);
            if (enabled) {
                List<String> blockers = adapter.blockerNames(item, List.copyOf(entries), flow, currentLang());
                if (!blockers.isEmpty()) {
                    String msg = DlcFxI18n.tr("dlc_manager.preset.blocked_by", String.join(", ", blockers));
                    statusChip.setText(DlcFxI18n.tr("dlc_manager.status.blocked"));
                    DlcFxStatusChip.apply(statusChip, DlcFxStyle.CHIP_ERROR);
                    rowTooltip = DlcFxRichTextRenderer.formattedTooltip(msg);
                    return;
                }
            }
            if (enabled && adapter.alarm(item)) {
                confirmAlarmPreset(item, toggleButton.getScene());
                return;
            }
            flow.setPending(item, enabled);
            refreshPresetRows();
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
        }

        private void updateStatusDisplay(T item) {
            DlcPresetStatus status = adapter.displayStatus(item, flow);
            switch (status) {
                case ON -> {
                    statusChip.setText(DlcFxI18n.tr("dlc_manager.preset.status_on"));
                    DlcFxStatusChip.apply(statusChip, DlcFxStyle.CHIP_OK);
                }
                case PART -> {
                    statusChip.setText(DlcFxI18n.tr("dlc_manager.preset.status_part"));
                    DlcFxStatusChip.apply(statusChip, DlcFxStyle.CHIP_WARN);
                }
                case OFF -> {
                    statusChip.setText(DlcFxI18n.tr("dlc_manager.preset.status_off"));
                    DlcFxStatusChip.apply(statusChip, DlcFxStyle.CHIP_NEUTRAL);
                }
            }
            boolean pending = flow.isPending(item);
            pendingLabel.setVisible(pending);
            pendingLabel.setManaged(pending);
            toggleButton.setText(flow.effectiveEnabled(item)
                    ? DlcFxI18n.tr("dlc_manager.preset.disable")
                    : DlcFxI18n.tr("dlc_manager.preset.enable"));
            toggleButton.getStyleClass().removeAll("dlc-toggle-on", "dlc-toggle-off", "dlc-toggle-pending");
            toggleButton.getStyleClass().add(pending
                    ? "dlc-toggle-pending"
                    : flow.effectiveEnabled(item) ? "dlc-toggle-on" : "dlc-toggle-off");
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
