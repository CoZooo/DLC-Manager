package com.cozooo.dlc_manager.javafx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
 * [远程] DLC Pack — pack_list/install/uninstall/delete/import/import_url/order +
 * pack_apply_start/status/pause/resume/cancel.
 */
final class RemotePackTab {

    private final RemoteConnection conn;
    private final ObservableList<String> packs = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));

    RemotePackTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.remote_pack"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: list + apply controls ────────────────────────────
        Button listBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.pack.refresh"));
        Button applyStartBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.pack.apply_start"));
        Button applyStatusBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.pack.apply_status"));
        Button applyPauseBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.pause_install"));
        Button applyResumeBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.continue_install"));
        Button applyCancelBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.cancel_install"));

        listBtn.disableProperty().bind(conn.connectedProperty().not());
        applyStartBtn.disableProperty().bind(conn.connectedProperty().not());
        applyStatusBtn.disableProperty().bind(conn.connectedProperty().not());
        applyPauseBtn.disableProperty().bind(conn.connectedProperty().not());
        applyResumeBtn.disableProperty().bind(conn.connectedProperty().not());
        applyCancelBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(listBtn);
        DlcFxStyle.primary(applyStartBtn);
        DlcFxStyle.danger(applyCancelBtn);

        listBtn.setOnAction(e -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.loading"));
            conn.runAsync(f -> f.packList(), resp -> {
                packs.setAll(extractStrings(resp, "packs"));
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.pack.count", packs.size()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        applyStartBtn.setOnAction(e -> {
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.apply_start_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.apply_start_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.pack.starting_apply"));
            conn.runAsync(f -> f.packApplyStart(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.started_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });
        applyStatusBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyStatus(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.status_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });
        applyPauseBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyPause(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.paused")),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });
        applyResumeBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyResume(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.pack.resumed")),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });
        applyCancelBtn.setOnAction(e -> {
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.cancel_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.cancel_confirm"))) {
                return;
            }
            conn.runAsync(f -> f.packApplyCancel(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceled")),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        HBox topBox = new HBox(8, listBtn,
                new Label(DlcFxI18n.tr("dlc_manager.apply")), applyStartBtn, applyStatusBtn, applyPauseBtn, applyResumeBtn, applyCancelBtn,
                statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: pack list ─────────────────────────────────────
        ListView<String> packList = new ListView<>(packs);
        VBox.setVgrow(packList, Priority.ALWAYS);
        VBox center = new VBox(packList);
        center.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        // ── Bottom: install/uninstall/delete/import/order ─────────
        TextField archiveField = new TextField();
        archiveField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.pack.archive_path_prompt"));
        archiveField.setPrefColumnCount(18);
        Button installBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.install"));
        Button deleteBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.delete"));
        installBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(installBtn);
        DlcFxStyle.danger(deleteBtn);
        installBtn.setOnAction(e -> {
            String p = archiveField.getText().trim();
            if (p.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.pack.archive_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.install_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.install_confirm", p))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.installing"));
            conn.runAsync(f -> f.packInstall(p), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });
        deleteBtn.setOnAction(e -> {
            String p = archiveField.getText().trim();
            if (p.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.pack.archive_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.delete_archive_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.delete_archive_confirm", p))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.deleting"));
            conn.runAsync(f -> f.packDelete(p), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        TextField idField = new TextField();
        idField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.pack.identifier_prompt"));
        idField.setPrefColumnCount(14);
        Button uninstallBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.uninstall"));
        uninstallBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.danger(uninstallBtn);
        uninstallBtn.setOnAction(e -> {
            String id = idField.getText().trim();
            if (id.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.pack.identifier_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.uninstall_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.uninstall_confirm", id))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.uninstalling"));
            conn.runAsync(f -> f.packUninstall(id), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        TextField importPathField = new TextField();
        importPathField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.common.server_path"));
        importPathField.setPrefColumnCount(14);
        Button importBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.import"));
        importBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(importBtn);
        importBtn.setOnAction(e -> {
            String p = importPathField.getText().trim();
            if (p.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.common.server_path_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.import_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.import_confirm", p))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.update.importing"));
            conn.runAsync(f -> f.packImport(p), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        TextField urlField = new TextField();
        urlField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.common.url"));
        urlField.setPrefColumnCount(18);
        TextField nameField = new TextField();
        nameField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.common.file_name"));
        nameField.setPrefColumnCount(10);
        Button importUrlBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.import_url"));
        importUrlBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(importUrlBtn);
        importUrlBtn.setOnAction(e -> {
            String url = urlField.getText().trim();
            String name = nameField.getText().trim();
            if (url.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.common.url_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.pack.import_url_title"), DlcFxI18n.tr("dlc_manager.javafx.pack.import_url_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.downloading_importing"));
            conn.runAsync(f -> f.packImportUrl(url, name), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        HBox bottomBox = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.javafx.pack.install_delete_label")), archiveField, installBtn, deleteBtn,
                new Label(DlcFxI18n.tr("dlc_manager.javafx.pack.uninstall_label")), idField, uninstallBtn,
                new Label(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_label")), importPathField, importBtn,
                new Label(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_url_label")), urlField, nameField, importUrlBtn);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.statusStrip(bottomBox);
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
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

    private static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_no"), ButtonData.CANCEL_CLOSE),
                new ButtonType(DlcFxI18n.tr("dlc_manager.confirm_yes"), ButtonData.OK_DONE));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
    }
}
