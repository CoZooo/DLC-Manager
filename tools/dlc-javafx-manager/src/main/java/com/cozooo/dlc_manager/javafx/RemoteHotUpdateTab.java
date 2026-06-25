package com.cozooo.dlc_manager.javafx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * [远程] 热更新 — update_state/list/apply/rollback/import/import_url.
 */
final class RemoteHotUpdateTab {

    private final RemoteConnection conn;
    private final ObservableList<String> updates = FXCollections.observableArrayList();
    private final ObservableList<String> history = FXCollections.observableArrayList();
    private final Label stateLabel = new Label("未连接");
    private final Label statusLabel = new Label("");

    RemoteHotUpdateTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab("远程 · 热更新");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: state + actions ──────────────────────────────────
        Button stateBtn = new Button("查询状态");
        Button listBtn = new Button("查询更新列表");
        Button applyBtn = new Button("应用全部更新");

        stateBtn.disableProperty().bind(conn.connectedProperty().not());
        listBtn.disableProperty().bind(conn.connectedProperty().not());
        applyBtn.disableProperty().bind(conn.connectedProperty().not());

        stateBtn.setOnAction(e -> {
            stateLabel.setText("查询中…");
            conn.runAsync(f -> f.updateState(), resp -> {
                stateLabel.setText(formatState(resp));
                history.setAll(extractStrings(resp, "history"));
                updates.setAll(extractStrings(resp, "updates"));
            }, err -> stateLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        listBtn.setOnAction(e -> {
            statusLabel.setText("查询中…");
            conn.runAsync(f -> f.updateList(), resp -> {
                updates.setAll(extractStrings(resp, "updates"));
                statusLabel.setText("共 " + updates.size() + " 个更新");
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        applyBtn.setOnAction(e -> {
            statusLabel.setText("应用中…");
            conn.runAsync(f -> f.updateApply(null, null, null, null), resp -> {
                statusLabel.setText("结果: " + resp.toString());
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox topBox = new HBox(8, stateBtn, listBtn, applyBtn, stateLabel, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));

        // ── Versioned apply (optional base/dlc old→new) ──────────
        TextField baseOldField = new TextField();
        baseOldField.setPromptText("baseOld");
        baseOldField.setPrefColumnCount(10);
        TextField dlcOldField = new TextField();
        dlcOldField.setPromptText("dlcOld");
        dlcOldField.setPrefColumnCount(10);
        TextField baseNewField = new TextField();
        baseNewField.setPromptText("baseNew");
        baseNewField.setPrefColumnCount(10);
        TextField dlcNewField = new TextField();
        dlcNewField.setPromptText("dlcNew");
        dlcNewField.setPrefColumnCount(10);
        Button applyVersionBtn = new Button("按版本应用");
        applyVersionBtn.disableProperty().bind(conn.connectedProperty().not());
        applyVersionBtn.setOnAction(e -> {
            String baseOld = blankToNull(baseOldField.getText());
            String dlcOld = blankToNull(dlcOldField.getText());
            String baseNew = blankToNull(baseNewField.getText());
            String dlcNew = blankToNull(dlcNewField.getText());
            if (baseOld == null && dlcOld == null && baseNew == null && dlcNew == null) {
                statusLabel.setText("请至少填写一个版本参数");
                return;
            }
            statusLabel.setText("应用中…");
            conn.runAsync(f -> f.updateApply(baseOld, dlcOld, baseNew, dlcNew), resp -> {
                statusLabel.setText("结果: " + resp.toString());
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox versionBox = new HBox(8,
                new Label("按版本应用:"), baseOldField, dlcOldField,
                new Label("→"), baseNewField, dlcNewField, applyVersionBtn);
        versionBox.setAlignment(Pos.CENTER_LEFT);
        versionBox.setPadding(new Insets(0, 8, 8, 8));

        VBox top = new VBox(topBox, versionBox);
        root.setTop(top);

        // ── Center: updates + history ─────────────────────────────
        ListView<String> updatesList = new ListView<>(updates);
        ListView<String> historyList = new ListView<>(history);
        VBox.setVgrow(updatesList, Priority.ALWAYS);
        VBox.setVgrow(historyList, Priority.ALWAYS);

        VBox updatesBox = new VBox(4, new Label("可用更新"), updatesList);
        VBox historyBox = new VBox(4, new Label("历史版本"), historyList);

        HBox center = new HBox(8, updatesBox, historyBox);
        HBox.setHgrow(updatesBox, Priority.ALWAYS);
        HBox.setHgrow(historyBox, Priority.ALWAYS);
        center.setPadding(new Insets(4, 8, 4, 8));
        root.setCenter(center);

        // ── Bottom: rollback / import ─────────────────────────────
        TextField rollbackField = new TextField();
        rollbackField.setPromptText("回滚目标版本");
        rollbackField.setPrefColumnCount(20);
        Button rollbackBtn = new Button("回滚");
        rollbackBtn.disableProperty().bind(conn.connectedProperty().not());
        rollbackBtn.setOnAction(e -> {
            String target = rollbackField.getText().trim();
            if (target.isBlank()) { statusLabel.setText("请输入回滚目标"); return; }
            statusLabel.setText("回滚中…");
            conn.runAsync(f -> f.updateRollback(target), resp -> {
                statusLabel.setText("回滚结果: " + resp.toString());
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        TextField importPathField = new TextField();
        importPathField.setPromptText("服务器本地包路径");
        importPathField.setPrefColumnCount(20);
        Button importBtn = new Button("导入包");
        importBtn.disableProperty().bind(conn.connectedProperty().not());
        importBtn.setOnAction(e -> {
            String path = importPathField.getText().trim();
            statusLabel.setText("导入中…");
            conn.runAsync(f -> f.updateImport(path), resp -> {
                statusLabel.setText("导入结果: " + resp.toString());
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        TextField importUrlField = new TextField();
        importUrlField.setPromptText("URL");
        importUrlField.setPrefColumnCount(24);
        TextField importNameField = new TextField();
        importNameField.setPromptText("文件名");
        importNameField.setPrefColumnCount(12);
        Button importUrlBtn = new Button("从URL导入");
        importUrlBtn.disableProperty().bind(conn.connectedProperty().not());
        importUrlBtn.setOnAction(e -> {
            String url = importUrlField.getText().trim();
            String name = importNameField.getText().trim();
            statusLabel.setText("下载并导入中…");
            conn.runAsync(f -> f.updateImportUrl(url, name), resp -> {
                statusLabel.setText("导入结果: " + resp.toString());
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox bottomBox = new HBox(8,
                new Label("回滚:"), rollbackField, rollbackBtn,
                new Label("  导入:"), importPathField, importBtn,
                new Label("  URL导入:"), importUrlField, importNameField, importUrlBtn);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(8));
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    private static String formatState(JsonObject resp) {
        if (resp == null) return "无数据";
        StringBuilder sb = new StringBuilder();
        sb.append("base=").append(str(resp, "base"));
        sb.append("  dlc=").append(str(resp, "dlc"));
        if (resp.has("rollbackFrom")) {
            sb.append("  rollbackFrom=").append(str(resp, "rollbackFrom"));
        }
        return sb.toString();
    }

    private static java.util.List<String> extractStrings(JsonObject resp, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (resp == null) return list;
        JsonElement element = resp.get(key);
        if (element == null || !element.isJsonArray()) return list;
        for (JsonElement e : element.getAsJsonArray()) {
            list.add(e.toString());
        }
        return list;
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
