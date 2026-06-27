package com.cozooo.dlc_manager.javafx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * [远程] 服务器配置 — config_get → 编辑 → config_set.
 * <p>
 * Fetches the live {@code DlcConfig} JSON from the server (via the encrypted
 * TCP protocol), lets the operator edit it as pretty-printed JSON with
 * up-front validation, and writes it back.  Mirrors the in-game server-config
 * screen's get/apply round-trip without depending on the running client.
 */
final class RemoteServerConfigTab {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final RemoteConnection conn;
    private final TextArea editor = new TextArea();
    private final Label statusLabel = new Label("未连接");
    private final Button reloadBtn = new Button("载入配置");
    private final Button saveBtn = new Button("保存配置");

    RemoteServerConfigTab(RemoteConnection conn) {
        this.conn = conn;
        editor.setPromptText("点击「载入配置」从服务器拉取当前 DLC 配置…");
        editor.setStyle("-fx-font-family: monospace;");
    }

    Tab build() {
        Tab tab = new Tab("远程 · 服务器配置");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        reloadBtn.disableProperty().bind(conn.connectedProperty().not());
        reloadBtn.setOnAction(e -> loadAsync());

        saveBtn.disableProperty().bind(conn.connectedProperty().not());
        saveBtn.setStyle("-fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveAsync());

        HBox topBox = new HBox(8, reloadBtn, saveBtn, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        VBox.setVgrow(editor, Priority.ALWAYS);
        VBox center = new VBox(editor);
        center.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    // ───────────────────────────────────────────────────────────────
    //  Load
    // ───────────────────────────────────────────────────────────────

    private void loadAsync() {
        statusLabel.setText("载入中…");
        reloadBtn.setDisable(true);
        conn.runAsync(f -> f.configGet(), resp -> {
            JsonElement config = resp.get("config");
            if (config == null || !config.isJsonObject()) {
                statusLabel.setText("载入失败: 服务器未返回 config 对象");
                reloadBtn.setDisable(false);
                return;
            }
            editor.setText(PRETTY.toJson(config));
            editor.setScrollTop(0);
            statusLabel.setText("已载入，编辑后点击「保存配置」");
            reloadBtn.setDisable(false);
        }, err -> {
            statusLabel.setText("载入失败: " + RemoteConnection.safeMessage(err));
            reloadBtn.setDisable(false);
        });
    }

    // ───────────────────────────────────────────────────────────────
    //  Save (with JSON validation)
    // ───────────────────────────────────────────────────────────────

    private void saveAsync() {
        JsonObject config;
        try {
            // Parse locally before sending so a typo is caught up front and
            // we never ship a malformed payload to the server.
            JsonElement parsed = JsonParser.parseString(editor.getText());
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalStateException("配置必须是 JSON 对象");
            }
            config = parsed.getAsJsonObject();
        } catch (Throwable t) {
            statusLabel.setText("JSON 无效: " + RemoteConnection.safeMessage(t));
            return;
        }
        if (!confirm("保存服务器配置", "将覆盖远程服务器当前配置，是否继续？")) {
            return;
        }
        statusLabel.setText("保存中…");
        saveBtn.setDisable(true);
        conn.runAsync(f -> f.configSet(config), resp -> {
            boolean ok = resp.has("ok") && resp.get("ok").isJsonPrimitive() && resp.get("ok").getAsBoolean();
            statusLabel.setText(ok ? "已保存到服务器" : "保存失败: 服务器拒绝");
            saveBtn.setDisable(false);
        }, err -> {
            statusLabel.setText("保存失败: " + RemoteConnection.safeMessage(err));
            saveBtn.setDisable(false);
        });
    }

    private static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                new ButtonType("取消", ButtonData.CANCEL_CLOSE),
                new ButtonType("确定", ButtonData.OK_DONE));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
    }
}
