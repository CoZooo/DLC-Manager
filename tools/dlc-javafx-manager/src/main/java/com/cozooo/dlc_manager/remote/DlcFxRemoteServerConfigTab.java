package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

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

public final class DlcFxRemoteServerConfigTab {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final DlcFxRemoteConnection conn;
    private final TextArea editor = new TextArea();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Button reloadBtn = new Button(DlcFxI18n.tr("dlc_manager.config.load"));
    private final Button saveBtn = new Button(DlcFxI18n.tr("dlc_manager.config.save"));

    public DlcFxRemoteServerConfigTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
        editor.setPromptText(DlcFxI18n.tr("dlc_manager.config.editor_prompt"));
        DlcFxStyle.add(editor, "code-editor");
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.remote_config"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        reloadBtn.disableProperty().bind(conn.connectedProperty().not());
        reloadBtn.setOnAction(e -> loadAsync());

        saveBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(saveBtn);
        saveBtn.setOnAction(e -> saveAsync());

        HBox topBox = new HBox(8, reloadBtn, saveBtn, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        VBox.setVgrow(editor, Priority.ALWAYS);
        VBox center = new VBox(editor);
        center.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    private void loadAsync() {
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.config.loading"));
        reloadBtn.setDisable(true);
        conn.runAsync(f -> f.configGet(), resp -> {
            JsonElement config = resp.get("config");
            if (config == null || !config.isJsonObject()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.config.load_missing_config"));
                reloadBtn.setDisable(false);
                return;
            }
            editor.setText(PRETTY.toJson(config));
            editor.setScrollTop(0);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.config.loaded"));
            reloadBtn.setDisable(false);
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.config.load_failed", DlcFxRemoteConnection.safeMessage(err)));
            reloadBtn.setDisable(false);
        });
    }

    private void saveAsync() {
        JsonObject config;
        try {
            // Parse locally before sending so a typo is caught up front and
            // we never ship a malformed payload to the server.
            JsonElement parsed = JsonParser.parseString(editor.getText());
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalStateException(DlcFxI18n.tr("dlc_manager.config.must_be_object"));
            }
            config = parsed.getAsJsonObject();
        } catch (Throwable t) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.config.invalid_json", DlcFxRemoteConnection.safeMessage(t)));
            return;
        }
        if (!confirm(DlcFxI18n.tr("dlc_manager.config.save_title"), DlcFxI18n.tr("dlc_manager.config.save_confirm"))) {
            return;
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.config.saving"));
        saveBtn.setDisable(true);
        conn.runAsync(f -> f.configSet(config), resp -> {
            boolean ok = resp.has("ok") && resp.get("ok").isJsonPrimitive() && resp.get("ok").getAsBoolean();
            statusLabel.setText(ok ? DlcFxI18n.tr("dlc_manager.config.saved") : DlcFxI18n.tr("dlc_manager.config.save_rejected"));
            saveBtn.setDisable(false);
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.save_failed", DlcFxRemoteConnection.safeMessage(err)));
            saveBtn.setDisable(false);
        });
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
