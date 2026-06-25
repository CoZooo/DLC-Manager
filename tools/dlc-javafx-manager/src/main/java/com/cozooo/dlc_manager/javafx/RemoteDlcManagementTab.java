package com.cozooo.dlc_manager.javafx;

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
final class RemoteDlcManagementTab {

    private final RemoteConnection conn;
    private final ObservableList<SyncModels.ServerItem> items = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("未连接");
    private final Button refreshBtn = new Button("刷新列表");
    private final Button applyBtn = new Button("应用更改");
    /** Read-only transcript of the most recent apply's per-item message lines. */
    private final TextArea applyDetail = new TextArea();

    RemoteDlcManagementTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab("远程 · DLC 管理");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top ───────────────────────────────────────────────────
        refreshBtn.disableProperty().bind(conn.connectedProperty().not());
        refreshBtn.setOnAction(e -> {
            statusLabel.setText("加载中…");
            conn.runAsync(f -> f.fetchList(), info -> {
                items.setAll(info.items);
                statusLabel.setText(String.format("共 %d 项 (base=%s dlc=%s)", info.items.size(), info.baseVersion, info.dlcVersion));
            }, err -> statusLabel.setText("加载失败: " + RemoteConnection.safeMessage(err)));
        });

        applyBtn.disableProperty().bind(conn.connectedProperty().not());
        applyBtn.setStyle("-fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            statusLabel.setText("应用中…");
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
                statusLabel.setText(outcome.requiresRestart() ? "已应用 — 需要重启服务器" : "已应用");
                renderApplyOutcome(outcome);
            }, err -> statusLabel.setText("应用失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox topBox = new HBox(8, refreshBtn, applyBtn, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center ────────────────────────────────────────────────
        ListView<SyncModels.ServerItem> listView = new ListView<>(items);
        listView.setCellFactory(param -> new ServerItemCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        applyDetail.setEditable(false);
        applyDetail.setWrapText(true);
        applyDetail.setPromptText("应用后此处显示服务器返回的逐项详情（启用/禁用/冲突等）。");
        applyDetail.setPrefRowCount(6);
        TitledPane detailPane = new TitledPane("应用详情", applyDetail);
        detailPane.setCollapsible(true);
        detailPane.setExpanded(false);

        VBox center = new VBox(listView, detailPane);
        VBox.setVgrow(detailPane, Priority.NEVER);
        center.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    /** Populate the apply-detail box from the server's response. */
    private void renderApplyOutcome(ApplyOutcome outcome) {
        List<String> messages = outcome.messages();
        if (messages.isEmpty()) {
            applyDetail.setText(outcome.requiresRestart()
                    ? "服务器已应用更改，需要重启。"
                    : "服务器已应用更改。");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (outcome.requiresRestart()) {
            sb.append("[需要重启服务器]\n");
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
            typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
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
                typeLabel.setText("[" + (item.type == null ? "?" : item.type) + "]");
                nameLabel.setText(item.name == null || item.name.isBlank() ? item.id : item.name);
                StringBuilder tip = new StringBuilder();
                tip.append("ID: ").append(item.id);
                if (item.identifier != null) tip.append("\n标识: ").append(item.identifier);
                if (item.path != null) tip.append("\n路径: ").append(item.path);
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }
}
