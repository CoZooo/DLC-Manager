package com.cozooo.dlc_manager.javafx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
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
 * [远程] RCON 控制台 — rcon_info/get/set/exec + 快捷命令 + server_restart.
 */
final class RemoteRconTab {

    private final RemoteConnection conn;
    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final Label infoLabel = new Label("未连接");
    private final TextField cmdField = new TextField();

    RemoteRconTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab("远程 · RCON");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: info + config ────────────────────────────────────
        Button infoBtn = new Button("查询 RCON 信息");
        Button getBtn = new Button("获取配置");
        infoBtn.disableProperty().bind(conn.connectedProperty().not());
        getBtn.disableProperty().bind(conn.connectedProperty().not());
        infoBtn.setOnAction(e -> {
            conn.runAsync(f -> f.rconInfo(), resp -> {
                infoLabel.setText(formatInfo(resp));
                appendLog("rcon_info: " + resp.toString());
            }, err -> infoLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });
        getBtn.setOnAction(e -> {
            conn.runAsync(f -> f.rconGet(), resp -> {
                infoLabel.setText(formatGet(resp));
                appendLog("rcon_get: " + resp.toString());
            }, err -> infoLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        TextField hostField = new TextField();
        hostField.setPromptText("RCON host");
        hostField.setPrefColumnCount(14);
        TextField portField = new TextField();
        portField.setPromptText("端口");
        portField.setPrefColumnCount(6);
        TextField pwField = new TextField();
        pwField.setPromptText("密码");
        pwField.setPrefColumnCount(14);
        Button setBtn = new Button("保存配置");
        setBtn.disableProperty().bind(conn.connectedProperty().not());
        setBtn.setOnAction(e -> {
            String h = hostField.getText().trim();
            int parsed;
            try { parsed = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException ex) { parsed = 25575; }
            final int p = parsed;
            String pw = pwField.getText();
            conn.runAsync(f -> f.rconSet(h, p, pw), resp -> {
                appendLog("rcon_set: 已保存");
            }, err -> appendLog("rcon_set 失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox topBox = new HBox(8, infoBtn, getBtn, infoLabel,
                new Label("  设置:"), hostField, portField, pwField, setBtn);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: command + log ─────────────────────────────────
        cmdField.setPromptText("输入命令…");
        cmdField.setPrefColumnCount(40);
        cmdField.setOnAction(e -> execCommand());
        Button execBtn = new Button("执行");
        execBtn.disableProperty().bind(conn.connectedProperty().not());
        execBtn.setOnAction(e -> execCommand());

        Button restartBtn = new Button("重启服务器");
        restartBtn.setStyle("-fx-text-fill: #c0392b;");
        restartBtn.disableProperty().bind(conn.connectedProperty().not());
        restartBtn.setOnAction(e -> {
            appendLog("发送 server_restart…");
            conn.runAsync(f -> f.serverRestart(), resp -> appendLog("server_restart: " + resp.toString()),
                    err -> appendLog("server_restart 失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox cmdBox = new HBox(8, cmdField, execBtn, restartBtn);
        cmdBox.setAlignment(Pos.CENTER_LEFT);
        cmdBox.setPadding(new Insets(4, 8, 4, 8));

        // Quick commands
        HBox quickBox = new HBox(4);
        for (String q : new String[]{"list", "say Hello", "weather clear", "time set day", "gamerule keepInventory true"}) {
            Button qb = new Button(q);
            qb.setOnAction(e -> { cmdField.setText(q); execCommand(); });
            quickBox.getChildren().add(qb);
        }
        quickBox.setAlignment(Pos.CENTER_LEFT);
        quickBox.setPadding(new Insets(0, 8, 4, 8));

        ListView<String> logView = new ListView<>(log);
        VBox.setVgrow(logView, Priority.ALWAYS);
        VBox center = new VBox(4, cmdBox, quickBox, logView);
        center.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    private void execCommand() {
        String cmd = cmdField.getText().trim();
        if (cmd.isBlank()) return;
        appendLog("> " + cmd);
        conn.runAsync(f -> f.rconExec(cmd), resp -> {
            String msg = str(resp, "message");
            appendLog(msg.isBlank() ? "(无输出)" : msg);
        }, err -> appendLog("错误: " + RemoteConnection.safeMessage(err)));
        cmdField.clear();
    }

    private void appendLog(String line) {
        Platform.runLater(() -> {
            log.add(line);
            // Keep last 200 lines
            while (log.size() > 200) {
                log.remove(0);
            }
        });
    }

    private static String formatInfo(JsonObject resp) {
        if (resp == null) return "无数据";
        return String.format("enabled=%s port=%s passwordSet=%s",
                str(resp, "enabled"), str(resp, "port"), str(resp, "passwordSet"));
    }

    private static String formatGet(JsonObject resp) {
        if (resp == null) return "无数据";
        return String.format("host=%s port=%s passwordSet=%s",
                str(resp, "host"), str(resp, "port"),
                resp.has("password") && !str(resp, "password").isBlank() ? "yes" : "no");
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : "";
    }
}
