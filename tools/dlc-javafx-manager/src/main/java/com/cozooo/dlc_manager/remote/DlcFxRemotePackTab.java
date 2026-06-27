package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;

/**
 * [远程] DLC Pack — pack_list/install/uninstall/delete/import/import_url/order +
 * pack_apply_start/status/pause/resume/cancel.
 */
public final class DlcFxRemotePackTab {

    private final DlcFxRemoteConnection conn;
    private final ObservableList<String> packs = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));

    public DlcFxRemotePackTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.remote_pack"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: list refresh + pack apply controls ───────────────
        Button listBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.refresh"));
        Button applyStartBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.apply_start"));
        Button applyStatusBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.apply_status"));
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
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
            conn.runAsync(f -> f.packList(), resp -> {
                packs.setAll(extractStrings(resp, "packs"));
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.count", packs.size()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        applyStartBtn.setOnAction(e -> {
            if (!confirm(DlcFxI18n.tr("dlc_manager.pack.apply_start_title"), DlcFxI18n.tr("dlc_manager.pack.apply_start_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.starting_apply"));
            conn.runAsync(f -> f.packApplyStart(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.started_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        applyStatusBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyStatus(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.status_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        applyPauseBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyPause(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.paused")),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        applyResumeBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyResume(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.resumed")),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        applyCancelBtn.setOnAction(e -> {
            if (!confirm(DlcFxI18n.tr("dlc_manager.pack.cancel_title"), DlcFxI18n.tr("dlc_manager.pack.cancel_confirm"))) {
                return;
            }
            conn.runAsync(f -> f.packApplyCancel(), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceled")),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        HBox listRow = new HBox(8, listBtn, statusLabel);
        listRow.setAlignment(Pos.CENTER_LEFT);

        HBox applyRow = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.apply")), applyStartBtn, applyStatusBtn, applyPauseBtn, applyResumeBtn, applyCancelBtn);
        applyRow.setAlignment(Pos.CENTER_LEFT);

        VBox topBox = new VBox(8, listRow, applyRow);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: pack list + one-action inspector ──────────────
        ListView<String> packList = new ListView<>(packs);
        VBox.setVgrow(packList, Priority.ALWAYS);
        VBox listBox = new VBox(8, new Label(DlcFxI18n.tr("dlc_manager.pack.list_header")), packList);
        DlcFxStyle.title((Label) listBox.getChildren().get(0));
        VBox.setVgrow(packList, Priority.ALWAYS);
        listBox.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(listBox);

        ToggleGroup modeGroup = new ToggleGroup();
        ToggleButton installMode = modeButton(DlcFxI18n.tr("dlc_manager.pack.mode_install"), modeGroup, true);
        ToggleButton deleteMode = modeButton(DlcFxI18n.tr("dlc_manager.pack.mode_delete"), modeGroup, false);
        ToggleButton uninstallMode = modeButton(DlcFxI18n.tr("dlc_manager.pack.mode_uninstall"), modeGroup, false);
        ToggleButton importMode = modeButton(DlcFxI18n.tr("dlc_manager.pack.mode_import"), modeGroup, false);
        ToggleButton importUrlMode = modeButton(DlcFxI18n.tr("dlc_manager.pack.mode_import_url"), modeGroup, false);
        HBox modeBar = new HBox(8, installMode, deleteMode, uninstallMode, importMode, importUrlMode);
        modeBar.setAlignment(Pos.CENTER_LEFT);

        TextField idField = new TextField();
        idField.setPromptText(DlcFxI18n.tr("dlc_manager.pack.identifier_prompt"));
        TextField installArchiveField = new TextField();
        installArchiveField.setPromptText(DlcFxI18n.tr("dlc_manager.pack.archive_path_prompt"));
        TextField deleteArchiveField = new TextField();
        deleteArchiveField.setPromptText(DlcFxI18n.tr("dlc_manager.pack.archive_path_prompt"));
        TextField importPathField = new TextField();
        importPathField.setPromptText(DlcFxI18n.tr("dlc_manager.common.server_path"));
        TextField importUrlField = new TextField();
        importUrlField.setPromptText(DlcFxI18n.tr("dlc_manager.common.url"));
        TextField importNameField = new TextField();
        importNameField.setPromptText(DlcFxI18n.tr("dlc_manager.common.file_name"));

        Button installBtn = actionButton(DlcFxI18n.tr("dlc_manager.pack.install"), true);
        Button deleteBtn = actionButton(DlcFxI18n.tr("dlc_manager.pack.delete"), false);
        Button uninstallBtn = actionButton(DlcFxI18n.tr("dlc_manager.pack.uninstall"), false);
        Button importBtn = actionButton(DlcFxI18n.tr("dlc_manager.pack.import"), true);
        Button importUrlBtn = actionButton(DlcFxI18n.tr("dlc_manager.pack.import_url"), true);

        installBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());
        uninstallBtn.disableProperty().bind(conn.connectedProperty().not());
        importBtn.disableProperty().bind(conn.connectedProperty().not());
        importUrlBtn.disableProperty().bind(conn.connectedProperty().not());

        installBtn.setOnAction(e -> submitArchiveAction(
                installArchiveField.getText(),
                DlcFxI18n.tr("dlc_manager.pack.install_title"),
                DlcFxI18n.tr("dlc_manager.pack.install_confirm", installArchiveField.getText().trim()),
                DlcFxI18n.tr("dlc_manager.status.installing"),
                p -> conn.runAsync(f -> f.packInstall(p), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", resp.toString())),
                        err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))))));
        deleteBtn.setOnAction(e -> submitArchiveAction(
                deleteArchiveField.getText(),
                DlcFxI18n.tr("dlc_manager.pack.delete_archive_title"),
                DlcFxI18n.tr("dlc_manager.pack.delete_archive_confirm", deleteArchiveField.getText().trim()),
                DlcFxI18n.tr("dlc_manager.status.deleting"),
                p -> conn.runAsync(f -> f.packDelete(p), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", resp.toString())),
                        err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))))));
        uninstallBtn.setOnAction(e -> {
            String id = idField.getText().trim();
            if (id.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.identifier_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.pack.uninstall_title"), DlcFxI18n.tr("dlc_manager.pack.uninstall_confirm", id))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.uninstalling"));
            conn.runAsync(f -> f.packUninstall(id), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        importBtn.setOnAction(e -> {
            String p = importPathField.getText().trim();
            if (p.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.server_path_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.pack.import_title"), DlcFxI18n.tr("dlc_manager.pack.import_confirm", p))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.importing"));
            conn.runAsync(f -> f.packImport(p), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });
        importUrlBtn.setOnAction(e -> {
            String url = importUrlField.getText().trim();
            String name = importNameField.getText().trim();
            if (url.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.url_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.pack.import_url_title"), DlcFxI18n.tr("dlc_manager.pack.import_url_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.downloading_importing"));
            conn.runAsync(f -> f.packImportUrl(url, name), resp -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", resp.toString())),
                    err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        VBox installForm = actionForm(
                DlcFxI18n.tr("dlc_manager.pack.mode_install"),
                DlcFxI18n.tr("dlc_manager.pack.mode_install_desc"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.pack.archive_path_prompt")), installArchiveField, installBtn));
        VBox deleteForm = actionForm(
                DlcFxI18n.tr("dlc_manager.pack.mode_delete"),
                DlcFxI18n.tr("dlc_manager.pack.mode_delete_desc"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.pack.archive_path_prompt")), deleteArchiveField, deleteBtn));
        VBox uninstallForm = actionForm(
                DlcFxI18n.tr("dlc_manager.pack.mode_uninstall"),
                DlcFxI18n.tr("dlc_manager.pack.mode_uninstall_desc"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.pack.identifier_prompt")), idField, uninstallBtn));
        VBox importForm = actionForm(
                DlcFxI18n.tr("dlc_manager.pack.mode_import"),
                DlcFxI18n.tr("dlc_manager.pack.mode_import_desc"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.common.server_path")), importPathField, importBtn));
        VBox importUrlForm = actionForm(
                DlcFxI18n.tr("dlc_manager.pack.mode_import_url"),
                DlcFxI18n.tr("dlc_manager.pack.mode_import_url_desc"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.common.url")), importUrlField, new Label(DlcFxI18n.tr("dlc_manager.common.file_name")), importNameField, importUrlBtn));

        StackPane inspector = new StackPane(installForm, deleteForm, uninstallForm, importForm, importUrlForm);
        inspector.setMinHeight(220);
        inspector.getChildren().forEach(node -> {
            node.setVisible(false);
            node.setManaged(false);
        });
        setActiveForm(installForm, installForm, deleteForm, uninstallForm, importForm, importUrlForm);
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == deleteMode) setActiveForm(deleteForm, installForm, deleteForm, uninstallForm, importForm, importUrlForm);
            else if (newToggle == uninstallMode) setActiveForm(uninstallForm, installForm, deleteForm, uninstallForm, importForm, importUrlForm);
            else if (newToggle == importMode) setActiveForm(importForm, installForm, deleteForm, uninstallForm, importForm, importUrlForm);
            else if (newToggle == importUrlMode) setActiveForm(importUrlForm, installForm, deleteForm, uninstallForm, importForm, importUrlForm);
            else setActiveForm(installForm, installForm, deleteForm, uninstallForm, importForm, importUrlForm);
        });

        Label actionTitle = new Label(DlcFxI18n.tr("dlc_manager.pack.action_panel"));
        DlcFxStyle.title(actionTitle);
        VBox rightBox = new VBox(10, actionTitle, modeBar, inspector);
        rightBox.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(rightBox);
        VBox.setVgrow(inspector, Priority.ALWAYS);

        SplitPane center = new SplitPane(listBox, rightBox);
        center.setDividerPositions(0.46);
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    private ToggleButton modeButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        return button;
    }

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(text);
        if (primary) {
            DlcFxStyle.primary(button);
        } else {
            DlcFxStyle.danger(button);
        }
        return button;
    }

    private void setActiveForm(VBox active, VBox... forms) {
        for (VBox form : forms) {
            if (form == null) {
                continue;
            }
            boolean show = form == active;
            form.setVisible(show);
            form.setManaged(show);
        }
    }

    private void submitArchiveAction(String raw, String title, String confirmMessage, String busyText, java.util.function.Consumer<String> submitter) {
        String p = raw == null ? "" : raw.trim();
        if (p.isBlank()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.archive_required"));
            return;
        }
        if (!confirm(title, confirmMessage)) {
            return;
        }
        statusLabel.setText(busyText);
        submitter.accept(p);
    }

    private VBox actionForm(String title, String desc, javafx.scene.Node content) {
        Label titleLabel = new Label(title);
        Label descLabel = new Label(desc);
        DlcFxStyle.title(titleLabel);
        DlcFxStyle.muted(descLabel);
        descLabel.setWrapText(true);
        VBox box = new VBox(10, titleLabel, descLabel, content);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("dlc-action-form");
        return box;
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
