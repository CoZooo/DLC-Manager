package com.cozooo.dlc_manager.javafx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * [远程] 服务器连接 tab — host/port/password + 连接状态 + base/dlc 版本显示.
 */
final class RemoteServerConnectionTab {

    private final RemoteConnection conn;
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("29690");
    private final TextField passwordField = new TextField();
    private final Label statusLabel = new Label("未连接");
    private final Label infoLabel = new Label("");

    RemoteServerConnectionTab(RemoteConnection conn) {
        this.conn = conn;
        hostField.setPrefColumnCount(18);
        portField.setPrefColumnCount(6);
        passwordField.setPrefColumnCount(20);
        // Bind labels to connection properties
        statusLabel.textProperty().bind(conn.statusTextProperty());
        infoLabel.textProperty().bind(conn.serverInfoTextProperty());
    }

    Tab build() {
        Tab tab = new Tab("远程 · 服务器连接");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: connection form ──────────────────────────────────
        Button connectBtn = new Button("连接");
        connectBtn.setOnAction(e -> {
            String host = hostField.getText().trim();
            int port;
            try { port = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException ex) { port = 29690; }
            String pw = passwordField.getText();
            statusLabel.setText("连接中…");
            conn.connectAsync(host, port, pw, 10000, err -> {
                if (err != null) {
                    statusLabel.setText("连接失败: " + RemoteConnection.safeMessage(err));
                }
            });
        });

        Button disconnectBtn = new Button("断开");
        disconnectBtn.disableProperty().bind(conn.connectedProperty().not());
        disconnectBtn.setOnAction(e -> conn.disconnect());

        HBox formBox = new HBox(8,
                new Label("主机:"), hostField,
                new Label("端口:"), portField,
                new Label("密码:"), passwordField,
                connectBtn, disconnectBtn);
        formBox.setAlignment(Pos.CENTER_LEFT);
        formBox.setPadding(new Insets(12));
        root.setTop(formBox);

        // ── Center: status ────────────────────────────────────────
        VBox center = new VBox(8,
                new Label("连接状态:"), statusLabel,
                new Label("服务器信息:"), infoLabel);
        center.setPadding(new Insets(12));
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }
}
