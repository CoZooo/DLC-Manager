package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class DlcFxRemoteServerConnectionTab {

    private final DlcFxRemoteConnection conn;
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("29690");
    private final TextField passwordField = new TextField();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label infoLabel = new Label("");

    public DlcFxRemoteServerConnectionTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
        hostField.setPrefColumnCount(18);
        portField.setPrefColumnCount(6);
        passwordField.setPrefColumnCount(20);
        statusLabel.textProperty().bind(conn.statusTextProperty());
        infoLabel.textProperty().bind(conn.serverInfoTextProperty());
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.remote_connection"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        Button connectBtn = new Button(DlcFxI18n.tr("dlc_manager.server.connect"));
        connectBtn.setOnAction(e -> {
            String host = hostField.getText().trim();
            int port;
            try { port = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException ex) { port = 29690; }
            String pw = passwordField.getText();
            conn.connectAsync(host, port, pw, 10000, err -> {
            });
        });

        Button disconnectBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.disconnect"));
        disconnectBtn.disableProperty().bind(conn.connectedProperty().not());
        disconnectBtn.setOnAction(e -> conn.disconnect());
        DlcFxStyle.primary(connectBtn);

        HBox formBox = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.remote.host")), hostField,
                new Label(DlcFxI18n.tr("dlc_manager.remote.port")), portField,
                new Label(DlcFxI18n.tr("dlc_manager.remote.password")), passwordField,
                connectBtn, disconnectBtn);
        formBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(formBox);
        root.setTop(formBox);

        Label statusTitle = new Label(DlcFxI18n.tr("dlc_manager.remote.connection_status"));
        Label infoTitle = new Label(DlcFxI18n.tr("dlc_manager.remote.server_info"));
        DlcFxStyle.title(statusTitle);
        DlcFxStyle.title(infoTitle);
        DlcFxStyle.muted(infoLabel);
        VBox center = new VBox(8, statusTitle, statusLabel, infoTitle, infoLabel);
        center.setPadding(new Insets(18, 16, 16, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }
}
