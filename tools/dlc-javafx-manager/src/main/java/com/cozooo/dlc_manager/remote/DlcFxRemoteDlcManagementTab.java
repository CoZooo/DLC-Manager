package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.tools.externalsync.ApplyOutcome;
import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * [远程] 服务器 DLC 管理 — list/set/apply 经 TCP.
 */
public final class DlcFxRemoteDlcManagementTab {

    private final DlcFxRemoteConnection conn;
    private final ObservableList<SyncModels.ServerItem> items = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.common.refresh_list"));
    private final Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.local.apply_changes"));
    /** Read-only transcript of the most recent apply's per-item message lines. */
    private final TextArea applyDetail = new TextArea();

    public DlcFxRemoteDlcManagementTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.remote_dlc"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top ───────────────────────────────────────────────────
        refreshBtn.disableProperty().bind(conn.connectedProperty().not());
        refreshBtn.setOnAction(e -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.loading"));
            conn.runAsync(f -> f.fetchList(), info -> {
                items.setAll(info.items);
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote.dlc_count", info.items.size(), info.baseVersion, info.dlcVersion));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.load_failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        applyBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(applyBtn);
        applyBtn.setOnAction(e -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.applying"));
            applyDetail.clear();
            // Send set for each changed item, then apply (fetching messages).
            List<SyncModels.ServerItem> snapshot = new ArrayList<>(items);
            conn.runAsync(f -> {
                for (SyncModels.ServerItem it : snapshot) {
                    if (it.enabled != null) {
                        f.setEnabled(it.id, it.enabled);
                    }
                }
                return f.applyWithMessages();
            }, outcome -> {
                statusLabel.setText(outcome.requiresRestart() ? DlcFxI18n.tr("dlc_manager.javafx.remote.applied_restart") : DlcFxI18n.tr("dlc_manager.javafx.remote.applied"));
                renderApplyOutcome(outcome);
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.apply_failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        HBox topBox = new HBox(8, refreshBtn, applyBtn, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center ────────────────────────────────────────────────
        ListView<SyncModels.ServerItem> listView = new ListView<>(items);
        listView.setCellFactory(param -> new ServerItemCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        applyDetail.setEditable(false);
        applyDetail.setWrapText(true);
        applyDetail.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote.apply_detail_prompt"));
        applyDetail.setPrefRowCount(6);
        TitledPane detailPane = new TitledPane(DlcFxI18n.tr("dlc_manager.javafx.remote.apply_detail"), applyDetail);
        detailPane.setCollapsible(true);
        detailPane.setExpanded(false);

        VBox center = new VBox(listView, detailPane);
        VBox.setVgrow(detailPane, Priority.NEVER);
        center.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    /** Populate the apply-detail box from the server's response. */
    private void renderApplyOutcome(ApplyOutcome outcome) {
        List<String> messages = outcome.messages();
        if (messages.isEmpty()) {
            applyDetail.setText(outcome.requiresRestart()
                    ? DlcFxI18n.tr("dlc_manager.javafx.remote.apply_detail_restart")
                    : DlcFxI18n.tr("dlc_manager.javafx.remote.apply_detail_done"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (outcome.requiresRestart()) {
            sb.append(DlcFxI18n.tr("dlc_manager.javafx.remote.restart_required_tag")).append('\n');
        }
        for (String msg : messages) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(msg);
        }
        applyDetail.setText(sb.toString());
    }

    private static final class ServerItemCell extends javafx.scene.control.ListCell<SyncModels.ServerItem> {
        private final CheckBox checkBox = new CheckBox();
        private final Label typeLabel = new Label();
        private final Label nameLabel = new Label();
        private final HBox row = new HBox(8, checkBox, typeLabel, nameLabel);

        ServerItemCell() {
            DlcFxStyle.chip(typeLabel, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.title(nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            checkBox.setOnAction(e -> {
                SyncModels.ServerItem item = getItem();
                if (item != null) {
                    item.enabled = checkBox.isSelected();
                }
            });
        }

        @Override
        protected void updateItem(SyncModels.ServerItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                checkBox.setSelected(item.enabled != null && item.enabled);
                typeLabel.setText(item.type == null ? "?" : item.type);
                nameLabel.setText(item.name == null || item.name.isBlank() ? item.id : item.name);
                StringBuilder tip = new StringBuilder();
                tip.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.id")).append(item.id);
                if (item.identifier != null) tip.append('\n').append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.identifier")).append(item.identifier);
                if (item.path != null) tip.append('\n').append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.path")).append(item.path);
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }
}
