package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import javafx.scene.layout.VBox;

/**
 * [远程] 热更新 — update_state/list/apply/rollback/import/import_url.
 */
public final class DlcFxRemoteHotUpdateTab {

    private final DlcFxRemoteConnection conn;
    private final ObservableList<String> updates = FXCollections.observableArrayList();
    private final ObservableList<String> history = FXCollections.observableArrayList();
    private final Label stateLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
    private final Label statusLabel = new Label("");

    public DlcFxRemoteHotUpdateTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.remote_update"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: state + actions ──────────────────────────────────
        Button stateBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_update.query_state"));
        Button listBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_update.query_list"));
        Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_all"));

        stateBtn.disableProperty().bind(conn.connectedProperty().not());
        listBtn.disableProperty().bind(conn.connectedProperty().not());
        applyBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(stateBtn);
        DlcFxStyle.primary(applyBtn);

        stateBtn.setOnAction(e -> {
            stateLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.querying"));
            conn.runAsync(f -> f.updateState(), resp -> {
                stateLabel.setText(formatState(resp));
                history.setAll(extractStrings(resp, "history"));
                updates.setAll(extractStrings(resp, "updates"));
            }, err -> stateLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        listBtn.setOnAction(e -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.querying"));
            conn.runAsync(f -> f.updateList(), resp -> {
                updates.setAll(extractStrings(resp, "updates"));
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.update_count", updates.size()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        applyBtn.setOnAction(e -> {
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_title"), DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.applying"));
            conn.runAsync(f -> f.updateApply(null, null, null, null), resp -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        HBox topBox = new HBox(8, stateBtn, listBtn, stateLabel, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);

        // ── Versioned apply (optional base/dlc old→new) ──────────
        TextField baseOldField = new TextField();
        baseOldField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.base_old"));
        baseOldField.setPrefColumnCount(10);
        TextField dlcOldField = new TextField();
        dlcOldField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.dlc_old"));
        dlcOldField.setPrefColumnCount(10);
        TextField baseNewField = new TextField();
        baseNewField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.base_new"));
        baseNewField.setPrefColumnCount(10);
        TextField dlcNewField = new TextField();
        dlcNewField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.dlc_new"));
        dlcNewField.setPrefColumnCount(10);
        Button applyVersionBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_by_version"));
        applyVersionBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(applyVersionBtn);
        applyVersionBtn.setOnAction(e -> {
            String baseOld = blankToNull(baseOldField.getText());
            String dlcOld = blankToNull(dlcOldField.getText());
            String baseNew = blankToNull(baseNewField.getText());
            String dlcNew = blankToNull(dlcNewField.getText());
            if (baseOld == null && dlcOld == null && baseNew == null && dlcNew == null) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.version_required"));
                return;
            }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_by_version_title"), DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_by_version_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.applying"));
            conn.runAsync(f -> f.updateApply(baseOld, dlcOld, baseNew, dlcNew), resp -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.result_value", resp.toString()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        root.setTop(topBox);

        // ── Center: updates + history ─────────────────────────────
        ListView<String> updatesList = new ListView<>(updates);
        ListView<String> historyList = new ListView<>(history);
        VBox.setVgrow(updatesList, Priority.ALWAYS);
        VBox.setVgrow(historyList, Priority.ALWAYS);

        Label updatesTitle = new Label(DlcFxI18n.tr("dlc_manager.javafx.update.available_updates"));
        Label historyTitle = new Label(DlcFxI18n.tr("dlc_manager.javafx.update.history_versions"));
        DlcFxStyle.title(updatesTitle);
        DlcFxStyle.title(historyTitle);
        VBox updatesBox = new VBox(4, updatesTitle, updatesList);
        VBox historyBox = new VBox(4, historyTitle, historyList);

        HBox center = new HBox(8, updatesBox, historyBox);
        HBox.setHgrow(updatesBox, Priority.ALWAYS);
        HBox.setHgrow(historyBox, Priority.ALWAYS);
        center.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        // ── Bottom: one active operation at a time ────────────────
        TextField rollbackField = new TextField();
        rollbackField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback_target"));
        rollbackField.setPrefColumnCount(20);
        Button rollbackBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback"));
        rollbackBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.danger(rollbackBtn);
        rollbackBtn.setOnAction(e -> {
            String target = rollbackField.getText().trim();
            if (target.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback_target_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback_title"), DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback_confirm", target))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.update.rolling_back"));
            conn.runAsync(f -> f.updateRollback(target), resp -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback_result", resp.toString()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        TextField importPathField = new TextField();
        importPathField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.server_package_path"));
        importPathField.setPrefColumnCount(20);
        Button importBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.update.import_package"));
        importBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(importBtn);
        importBtn.setOnAction(e -> {
            String path = importPathField.getText().trim();
            if (path.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.server_package_path_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_title"), DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_confirm", path))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.update.importing"));
            conn.runAsync(f -> f.updateImport(path), resp -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_result", resp.toString()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        TextField importUrlField = new TextField();
        importUrlField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.common.url"));
        importUrlField.setPrefColumnCount(24);
        TextField importNameField = new TextField();
        importNameField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.common.file_name"));
        importNameField.setPrefColumnCount(12);
        Button importUrlBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_url"));
        importUrlBtn.disableProperty().bind(conn.connectedProperty().not());
        DlcFxStyle.primary(importUrlBtn);
        importUrlBtn.setOnAction(e -> {
            String url = importUrlField.getText().trim();
            String name = importNameField.getText().trim();
            if (url.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.common.url_required")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_url_title"), DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_url_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.downloading_importing"));
            conn.runAsync(f -> f.updateImportUrl(url, name), resp -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_result", resp.toString()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", DlcFxRemoteConnection.safeMessage(err))));
        });

        ToggleGroup actionGroup = new ToggleGroup();
        ToggleButton applyAllMode = modeButton(DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_all"), actionGroup, true);
        ToggleButton applyVersionMode = modeButton(DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_by_version"), actionGroup, false);
        ToggleButton rollbackMode = modeButton(DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback"), actionGroup, false);
        ToggleButton importMode = modeButton(DlcFxI18n.tr("dlc_manager.javafx.update.import_package"), actionGroup, false);
        ToggleButton importUrlMode = modeButton(DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_url"), actionGroup, false);
        HBox modeBar = new HBox(6, applyAllMode, applyVersionMode, rollbackMode, importMode, importUrlMode);
        modeBar.setAlignment(Pos.CENTER_LEFT);

        VBox applyAllForm = actionForm(
                DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_all"),
                applyBtn);
        VBox applyVersionForm = actionForm(
                DlcFxI18n.tr("dlc_manager.javafx.remote_update.apply_by_version_label"),
                new HBox(8, baseOldField, dlcOldField,
                        new Label(DlcFxI18n.tr("dlc_manager.javafx.common.arrow")),
                        baseNewField, dlcNewField, applyVersionBtn));
        VBox rollbackForm = actionForm(
                DlcFxI18n.tr("dlc_manager.javafx.remote_update.rollback_label"),
                new HBox(8, rollbackField, rollbackBtn));
        VBox importForm = actionForm(
                DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_label"),
                new HBox(8, importPathField, importBtn));
        VBox importUrlForm = actionForm(
                DlcFxI18n.tr("dlc_manager.javafx.remote_update.import_url_label"),
                new HBox(8, importUrlField, importNameField, importUrlBtn));

        VBox inspector = new VBox(applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
        setActiveForm(applyAllForm, applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
        actionGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == applyVersionMode) {
                setActiveForm(applyVersionForm, applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
            } else if (newToggle == rollbackMode) {
                setActiveForm(rollbackForm, applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
            } else if (newToggle == importMode) {
                setActiveForm(importForm, applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
            } else if (newToggle == importUrlMode) {
                setActiveForm(importUrlForm, applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
            } else {
                setActiveForm(applyAllForm, applyAllForm, applyVersionForm, rollbackForm, importForm, importUrlForm);
            }
        });

        Label actionTitle = new Label(DlcFxI18n.tr("dlc_manager.javafx.pack.action_panel"));
        DlcFxStyle.actionTitle(actionTitle);
        VBox actionPanel = new VBox(10, actionTitle, modeBar, inspector);
        DlcFxStyle.actionPanel(actionPanel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        return tab;
    }

    private static ToggleButton modeButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        return button;
    }

    private static VBox actionForm(String title, Node content) {
        Label titleLabel = new Label(title);
        DlcFxStyle.title(titleLabel);
        VBox box = new VBox(8, titleLabel, content);
        box.getStyleClass().add("dlc-action-form");
        box.setPadding(new Insets(12, 12, 12, 12));
        return box;
    }

    private static void setActiveForm(VBox active, VBox... forms) {
        for (VBox form : forms) {
            boolean show = form == active;
            form.setVisible(show);
            form.setManaged(show);
        }
    }

    private static String formatState(JsonObject resp) {
        if (resp == null) return DlcFxI18n.tr("dlc_manager.javafx.status.no_data");
        StringBuilder sb = new StringBuilder();
        sb.append("base=").append(str(resp, "base"));
        sb.append("  dlc=").append(str(resp, "dlc"));
        if (resp.has("rollbackFrom")) {
            sb.append("  rollbackFrom=").append(str(resp, "rollbackFrom"));
        }
        return sb.toString();
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

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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
