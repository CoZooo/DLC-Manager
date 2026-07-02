package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.common.DlcFxConfirmDialogs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class DlcFxServerRconTab {

    private final DlcFxServerConnection conn;
    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final Label infoLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final TextField cmdField = new TextField();
    private boolean loadedForCurrentConnection;

    public DlcFxServerRconTab(DlcFxServerConnection conn) {
        this.conn = conn;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.disconnected"));

        Button infoBtn = new Button(DlcFxI18n.tr("dlc_manager.rcon.query_info"));
        Button getBtn = new Button(DlcFxI18n.tr("dlc_manager.rcon.get_config"));
        infoBtn.disableProperty().bind(conn.connectedProperty().not());
        getBtn.disableProperty().bind(conn.connectedProperty().not());
        infoBtn.setOnAction(e -> {
            conn.runAsync(f -> f.rconInfo(), resp -> {
                infoLabel.setText(formatInfo(resp));
                appendLog("rcon_info: " + resp.toString());
            }, err -> infoLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
        });
        getBtn.setOnAction(e -> {
            conn.runAsync(f -> f.rconGet(), resp -> {
                infoLabel.setText(formatGet(resp));
                appendLog("rcon_get: " + resp.toString());
            }, err -> infoLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
        });

        TextField hostField = new TextField();
        hostField.setPromptText(DlcFxI18n.tr("dlc_manager.rcon.host_prompt"));
        hostField.setPrefColumnCount(14);
        TextField portField = new TextField();
        portField.setPromptText(DlcFxI18n.tr("dlc_manager.server.port_plain"));
        portField.setPrefColumnCount(6);
        TextField pwField = new TextField();
        pwField.setPromptText(DlcFxI18n.tr("dlc_manager.server.password_plain"));
        pwField.setPrefColumnCount(14);
        Button setBtn = new Button(DlcFxI18n.tr("dlc_manager.config.save"));
        setBtn.disableProperty().bind(conn.connectedProperty().not());
        setBtn.setOnAction(e -> {
            String h = hostField.getText().trim();
            int parsed;
            try { parsed = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException ex) { parsed = 25575; }
            final int p = parsed;
            String pw = pwField.getText();
            conn.runAsync(f -> f.rconSet(h, p, pw), resp -> {
                appendLog(DlcFxI18n.tr("dlc_manager.rcon.set_saved"));
            }, err -> appendLog(DlcFxI18n.tr("dlc_manager.rcon.set_failed", DlcFxServerConnection.safeMessage(err))));
        });

        HBox topBox = DlcFxToolbar.create(infoBtn, getBtn, infoLabel,
                new Label(DlcFxI18n.tr("dlc_manager.rcon.settings_label")), hostField, portField, pwField, setBtn);
        page.setTop(topBox);

        cmdField.setPromptText(DlcFxI18n.tr("dlc_manager.rcon.command_prompt"));
        cmdField.setPrefColumnCount(40);
        cmdField.setOnAction(e -> execCommand());
        Button execBtn = new Button(DlcFxI18n.tr("dlc_manager.server.rcon_send"));
        execBtn.disableProperty().bind(conn.connectedProperty().not());
        execBtn.setOnAction(e -> execCommand());

        Button restartBtn = new Button(DlcFxI18n.tr("dlc_manager.server.restart_title"));
        DlcFxStyle.danger(restartBtn);
        restartBtn.disableProperty().bind(conn.connectedProperty().not());
        restartBtn.setOnAction(e -> {
            if (!confirm(DlcFxI18n.tr("dlc_manager.server.restart_title"), DlcFxI18n.tr("dlc_manager.server.restart_prompt"))) {
                return;
            }
            appendLog(DlcFxI18n.tr("dlc_manager.rcon.sending_restart"));
            conn.runAsync(f -> f.serverRestart(), resp -> appendLog("server_restart: " + resp.toString()),
                    err -> appendLog(DlcFxI18n.tr("dlc_manager.rcon.restart_failed", DlcFxServerConnection.safeMessage(err))));
        });

        HBox cmdBox = new HBox(8, cmdField, execBtn, restartBtn);
        cmdBox.setAlignment(Pos.CENTER_LEFT);
        cmdBox.setPadding(new Insets(4, 8, 4, 8));

        HBox quickBox = new HBox(4);
        for (String q : new String[]{"list", "say Hello", "weather clear", "time set day", "gamerule keepInventory true"}) {
            Button qb = new Button(q);
            qb.setOnAction(e -> { cmdField.setText(q); execCommand(); });
            quickBox.getChildren().add(qb);
        }
        quickBox.setAlignment(Pos.CENTER_LEFT);
        quickBox.setPadding(new Insets(0, 8, 4, 8));

        ListView<String> logView = new ListView<>(log);
        HBox logFrame = DlcFxListViewSupport.wrap(logView);
        VBox.setVgrow(logFrame, Priority.ALWAYS);
        VBox center = new VBox(4, cmdBox, quickBox, logFrame);
        page.setCenter(page.section(center));
        DlcFxStyle.routeStatus(infoLabel);

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.rcon"), page.root());
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            loadedForCurrentConnection = true;
        }
    }

    private void execCommand() {
        String cmd = cmdField.getText().trim();
        if (cmd.isBlank()) return;
        appendLog("> " + cmd);
        conn.runAsync(f -> f.rconExec(cmd), resp -> {
            String msg = str(resp, "message");
            appendLog(msg.isBlank() ? DlcFxI18n.tr("dlc_manager.rcon.no_output") : msg);
        }, err -> appendLog(DlcFxI18n.tr("dlc_manager.tooltip.error") + DlcFxServerConnection.safeMessage(err)));
        cmdField.clear();
    }

    private void appendLog(String line) {
        Platform.runLater(() -> {
            log.add(line);
            while (log.size() > 200) {
                log.remove(0);
            }
        });
    }

    private static String formatInfo(JsonObject resp) {
        if (resp == null) return DlcFxI18n.tr("dlc_manager.status.no_data");
        return DlcFxI18n.tr("dlc_manager.rcon.info_format",
                str(resp, "enabled"), str(resp, "port"), str(resp, "passwordSet"));
    }

    private static String formatGet(JsonObject resp) {
        if (resp == null) return DlcFxI18n.tr("dlc_manager.status.no_data");
        return DlcFxI18n.tr("dlc_manager.rcon.get_format",
                str(resp, "host"), str(resp, "port"),
                resp.has("password") && !str(resp, "password").isBlank()
                        ? DlcFxI18n.tr("dlc_manager.common.yes")
                        : DlcFxI18n.tr("dlc_manager.common.no"));
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static boolean confirm(String title, String message) {
        return DlcFxConfirmDialogs.confirm(title, message);
    }
}
