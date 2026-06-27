package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
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
public final class DlcFxRemoteRconTab {

    private final DlcFxRemoteConnection conn;
    private final ObservableList<String> log = FXCollections.observableArrayList();
    private final Label infoLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
    private final TextField cmdField = new TextField();

    public DlcFxRemoteRconTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.remote_rcon"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: info + config ────────────────────────────────────
        Button infoBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.rcon.query_info"));
        Button getBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.rcon.get_config"));
        infoBtn.disableProperty().bind(conn.connectedProperty().not());
        getBtn.disableProperty().bind(conn.connectedProperty().not());
        infoBtn.setOnAction(e -> {
            conn.runAsync(f -> f.rconInfo(), resp -> {
                infoLabel.setText(formatInfo(resp));
                appendLog("rcon_info: " + resp.toString());
            }, err -> infoLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        getBtn.setOnAction(e -> {
            conn.runAsync(f -> f.rconGet(), resp -> {
                infoLabel.setText(formatGet(resp));
                appendLog("rcon_get: " + resp.toString());
            }, err -> infoLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        TextField hostField = new TextField();
        hostField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.rcon.host_prompt"));
        hostField.setPrefColumnCount(14);
        TextField portField = new TextField();
        portField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote.port_plain"));
        portField.setPrefColumnCount(6);
        TextField pwField = new TextField();
        pwField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote.password_plain"));
        pwField.setPrefColumnCount(14);
        Button setBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.config.save"));
        setBtn.disableProperty().bind(conn.connectedProperty().not());
        setBtn.setOnAction(e -> {
            String h = hostField.getText().trim();
            int parsed;
            try { parsed = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException ex) { parsed = 25575; }
            final int p = parsed;
            String pw = pwField.getText();
            conn.runAsync(f -> f.rconSet(h, p, pw), resp -> {
                appendLog(DlcFxI18n.tr("dlc_manager.javafx.rcon.set_saved"));
            }, err -> appendLog(DlcFxI18n.tr("dlc_manager.javafx.rcon.set_failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        HBox topBox = new HBox(8, infoBtn, getBtn, infoLabel,
                new Label(DlcFxI18n.tr("dlc_manager.javafx.rcon.settings_label")), hostField, portField, pwField, setBtn);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: command + log ─────────────────────────────────
        cmdField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.rcon.command_prompt"));
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
            appendLog(DlcFxI18n.tr("dlc_manager.javafx.rcon.sending_restart"));
            conn.runAsync(f -> f.serverRestart(), resp -> appendLog("server_restart: " + resp.toString()),
                    err -> appendLog(DlcFxI18n.tr("dlc_manager.javafx.rcon.restart_failed", DlcFxRemoteConnection.safeMessage(err))));
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
        center.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(center);
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
            appendLog(msg.isBlank() ? DlcFxI18n.tr("dlc_manager.javafx.rcon.no_output") : msg);
        }, err -> appendLog(DlcFxI18n.tr("dlc_manager.javafx.tooltip.error") + DlcFxRemoteConnection.safeMessage(err)));
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
        if (resp == null) return DlcFxI18n.tr("dlc_manager.javafx.status.no_data");
        return DlcFxI18n.tr("dlc_manager.javafx.rcon.info_format",
                str(resp, "enabled"), str(resp, "port"), str(resp, "passwordSet"));
    }

    private static String formatGet(JsonObject resp) {
        if (resp == null) return DlcFxI18n.tr("dlc_manager.javafx.status.no_data");
        return DlcFxI18n.tr("dlc_manager.javafx.rcon.get_format",
                str(resp, "host"), str(resp, "port"),
                resp.has("password") && !str(resp, "password").isBlank()
                        ? DlcFxI18n.tr("dlc_manager.javafx.common.yes")
                        : DlcFxI18n.tr("dlc_manager.javafx.common.no"));
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_no"), ButtonData.CANCEL_CLOSE),
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_yes"), ButtonData.OK_DONE));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
    }
}
