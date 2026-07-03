package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.prefs.Preferences;

public final class DlcFxServerConnectionTab {

    private static final Preferences PREFS = Preferences.userNodeForPackage(DlcFxServerConnectionTab.class);
    private static final String PREF_NAME = "remote.server.name";
    private static final String PREF_HOST = "remote.server.host";
    private static final String PREF_PORT = "remote.server.port";
    private static final String PREF_PASSWORD = "remote.server.password";

    private final DlcFxServerConnection conn;
    private final TextField nameField = new TextField();
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("29690");
    private final TextField passwordField = new TextField();
    private final Label infoLabel = new Label("");
    private boolean loadingConnectionState;

    public DlcFxServerConnectionTab(DlcFxServerConnection conn) {
        this.conn = conn;
        nameField.setPrefColumnCount(14);
        hostField.setPrefColumnCount(18);
        portField.setPrefColumnCount(6);
        passwordField.setPrefColumnCount(20);
        infoLabel.textProperty().bind(conn.serverInfoTextProperty());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        hostField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        portField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        passwordField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage("");

        loadConnectionState();

        Button connectBtn = new Button(DlcFxI18n.tr("dlc_manager.server.connect"));
        connectBtn.setOnAction(e -> connectCurrent());

        Button disconnectBtn = new Button(DlcFxI18n.tr("dlc_manager.server.disconnect"));
        disconnectBtn.disableProperty().bind(conn.connectedProperty().not());
        disconnectBtn.setOnAction(e -> conn.disconnect());
        DlcFxStyle.primary(connectBtn);

        HBox serverRow = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.server.name")), nameField,
                new Label(DlcFxI18n.tr("dlc_manager.server.host")), hostField,
                new Label(DlcFxI18n.tr("dlc_manager.server.port")), portField);
        serverRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox.setHgrow(hostField, Priority.ALWAYS);
        nameField.setMaxWidth(260);
        hostField.setMaxWidth(320);

        HBox passwordRow = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.server.password")), passwordField,
                DlcFxToolbar.spacer(),
                connectBtn, disconnectBtn);
        passwordRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(passwordField, Priority.ALWAYS);
        passwordField.setMaxWidth(360);

        page.setTop(DlcFxToolbar.vertical(serverRow, passwordRow));

        Label infoTitle = new Label(DlcFxI18n.tr("dlc_manager.server.server_info"));
        DlcFxStyle.title(infoTitle);
        DlcFxStyle.muted(infoLabel);
        VBox center = new VBox(8, infoTitle, infoLabel);
        page.setCenter(page.section(center));
        DlcFxStyle.routeStatus(infoLabel);

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.connection"), page.root());
    }

    private void connectCurrent() {
        String host = hostField.getText().trim();
        int port = parsePort(portField.getText(), 29690);
        String password = passwordField.getText();
        saveConnectionState();
        conn.connectAsync(host, port, password, 10000, err -> {
        });
    }

    private static int parsePort(String text, int fallback) {
        try {
            int port = Integer.parseInt(text == null ? "" : text.trim());
            return port <= 0 ? fallback : port;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void loadConnectionState() {
        loadingConnectionState = true;
        try {
            nameField.setText(PREFS.get(PREF_NAME, nameField.getText()));
            hostField.setText(PREFS.get(PREF_HOST, hostField.getText()));
            portField.setText(PREFS.get(PREF_PORT, portField.getText()));
            passwordField.setText(PREFS.get(PREF_PASSWORD, passwordField.getText()));
        } finally {
            loadingConnectionState = false;
        }
    }

    private void saveConnectionState() {
        if (loadingConnectionState) {
            return;
        }
        try {
            PREFS.put(PREF_NAME, nameField.getText() == null ? "" : nameField.getText());
            PREFS.put(PREF_HOST, hostField.getText() == null ? "" : hostField.getText());
            PREFS.put(PREF_PORT, portField.getText() == null ? "" : portField.getText());
            PREFS.put(PREF_PASSWORD, passwordField.getText() == null ? "" : passwordField.getText());
            PREFS.flush();
        } catch (Throwable ignored) {
        }
    }
}
