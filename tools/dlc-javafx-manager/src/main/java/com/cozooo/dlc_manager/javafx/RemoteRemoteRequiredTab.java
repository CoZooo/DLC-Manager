package com.cozooo.dlc_manager.javafx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * [远程] 服务器 Remote/Required 下载 —
 * remote_list/download_start/download_status +
 * required_list/download_start/download_status/cancel/uninstall/apply.
 * <p>
 * List responses are rendered as structured rows (name / id / status tag /
 * file name) instead of raw JSON, mirroring the in-game Remote/Required view.
 */
final class RemoteRemoteRequiredTab {

    private final RemoteConnection conn;
    private final ObservableList<Row> remoteItems = FXCollections.observableArrayList();
    private final ObservableList<Row> requiredItems = FXCollections.observableArrayList();
    private final Label remoteStatus = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
    private final Label requiredStatus = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));

    RemoteRemoteRequiredTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.remote_remote_required"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Remote section ────────────────────────────────────────
        Button remoteRefreshBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_required.refresh_remote"));
        remoteRefreshBtn.disableProperty().bind(conn.connectedProperty().not());
        remoteRefreshBtn.setOnAction(e -> {
            remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.loading"));
            conn.runAsync(f -> f.remoteList(), resp -> {
                List<Row> rows = extractRemoteRows(resp);
                remoteItems.setAll(rows);
                remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.common.count_items", rows.size()));
            }, err -> remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        Button remoteDownloadBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.required.download_missing"));
        remoteDownloadBtn.disableProperty().bind(conn.connectedProperty().not());
        remoteDownloadBtn.setOnAction(e -> {
            remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.starting_download"));
            conn.runAsync(f -> f.remoteDownloadStart(null), resp -> {
                remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.download_started", summarizeStatus(resp)));
            }, err -> remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        Button remoteStatusBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_required.download_status"));
        remoteStatusBtn.disableProperty().bind(conn.connectedProperty().not());
        remoteStatusBtn.setOnAction(e -> {
            conn.runAsync(f -> f.remoteDownloadStatus(), resp -> {
                remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.status_value", summarizeStatus(resp)));
            }, err -> remoteStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        ListView<Row> remoteList = new ListView<>(remoteItems);
        remoteList.setCellFactory(param -> new RowCell());
        VBox.setVgrow(remoteList, Priority.ALWAYS);

        Label remoteTitle = new Label(DlcFxI18n.tr("dlc_manager.javafx.remote_required.remote_title"));
        DlcFxStyle.title(remoteTitle);
        DlcFxStyle.primary(remoteDownloadBtn);
        VBox remoteBox = new VBox(8,
                remoteTitle,
                new HBox(8, remoteRefreshBtn, remoteDownloadBtn, remoteStatusBtn, remoteStatus),
                remoteList);

        // ── Required section ──────────────────────────────────────
        Button requiredRefreshBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_required.refresh_required"));
        requiredRefreshBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredRefreshBtn.setOnAction(e -> {
            requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.loading"));
            conn.runAsync(f -> f.requiredList(), resp -> {
                List<Row> rows = extractRequiredRows(resp);
                requiredItems.setAll(rows);
                requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.common.count_items", rows.size()));
            }, err -> requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        Button requiredDownloadBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_required.download_all"));
        requiredDownloadBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredDownloadBtn.setOnAction(e -> {
            requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.starting_download"));
            conn.runAsync(f -> f.requiredDownloadStart(null), resp -> {
                requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.download_started", summarizeStatus(resp)));
            }, err -> requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        Button requiredCancelBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.common.cancel_download"));
        requiredCancelBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredCancelBtn.setOnAction(e -> {
            conn.runAsync(f -> f.requiredDownloadCancel(), resp -> {
                requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.canceled_value", summarizeStatus(resp)));
            }, err -> requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        Button requiredApplyBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_required.apply_required"));
        requiredApplyBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredApplyBtn.setOnAction(e -> {
            requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.applying"));
            conn.runAsync(f -> f.requiredApply(null), resp -> {
                requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.applied_value", summarizeStatus(resp)));
            }, err -> requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        Button requiredUninstallBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.remote_required.uninstall_required"));
        requiredUninstallBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredUninstallBtn.setOnAction(e -> {
            requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.uninstalling"));
            conn.runAsync(f -> f.requiredUninstall(null), resp -> {
                requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.uninstalled_value", summarizeStatus(resp)));
            }, err -> requiredStatus.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        ListView<Row> requiredList = new ListView<>(requiredItems);
        requiredList.setCellFactory(param -> new RowCell());
        VBox.setVgrow(requiredList, Priority.ALWAYS);

        Label requiredTitle = new Label(DlcFxI18n.tr("dlc_manager.javafx.remote_required.required_title"));
        DlcFxStyle.title(requiredTitle);
        DlcFxStyle.primary(requiredDownloadBtn);
        DlcFxStyle.primary(requiredApplyBtn);
        DlcFxStyle.danger(requiredCancelBtn);
        DlcFxStyle.danger(requiredUninstallBtn);
        VBox requiredBox = new VBox(8,
                requiredTitle,
                new HBox(8, requiredRefreshBtn, requiredDownloadBtn, requiredCancelBtn, requiredApplyBtn, requiredUninstallBtn, requiredStatus),
                requiredList);

        // ── Layout: side by side ──────────────────────────────────
        HBox center = new HBox(8, remoteBox, requiredBox);
        HBox.setHgrow(remoteBox, Priority.ALWAYS);
        HBox.setHgrow(requiredBox, Priority.ALWAYS);
        center.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        tab.setContent(root);
        return tab;
    }

    // ───────────────────────────────────────────────────────────────
    //  Row model + cell
    // ───────────────────────────────────────────────────────────────

    /** A single structured row shown in either the Remote or Required list. */
    private static final class Row {
        final String title;
        final String subtitle;
        final String detail;
        final String tag;
        final String tagState;

        Row(String title, String subtitle, String detail, String tag, String tagState) {
            this.title = title;
            this.subtitle = subtitle;
            this.detail = detail;
            this.tag = tag;
            this.tagState = tagState;
        }
    }

    private static final class RowCell extends javafx.scene.control.ListCell<Row> {
        private final Label title = new Label();
        private final Label tag = new Label();
        private final Label subtitle = new Label();
        private final HBox row = new HBox(8, title, tag, subtitle);

        RowCell() {
            DlcFxStyle.title(title);
            DlcFxStyle.chip(tag, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.muted(subtitle);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setTooltip(null);
            } else {
                title.setText(item.title);
                tag.setText(item.tag);
                DlcFxStyle.chip(tag, item.tagState);
                subtitle.setText(item.subtitle);
                if (item.detail != null && !item.detail.isBlank()) {
                    setTooltip(new Tooltip(item.detail));
                } else {
                    setTooltip(null);
                }
                setGraphic(row);
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Response → structured rows
    // ───────────────────────────────────────────────────────────────

    private static List<Row> extractRemoteRows(JsonObject response) {
        List<Row> rows = new ArrayList<>();
        JsonArray arr = itemsArray(response);
        if (arr == null) return rows;
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject obj = e.getAsJsonObject();
            String name = str(obj, "name");
            String key = str(obj, "key");
            String title = firstNonBlank(name, key, DlcFxI18n.tr("dlc_manager.javafx.status.unnamed"));
            boolean done = bool(obj, "done");
            boolean success = bool(obj, "success");
            String error = str(obj, "error");
            String tag;
            String tagState;
            if (done && success) {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.downloaded");
                tagState = DlcFxStyle.CHIP_OK;
            } else if (done) {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.failed_short");
                tagState = DlcFxStyle.CHIP_ERROR;
            } else {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.missing");
                tagState = DlcFxStyle.CHIP_ERROR;
            }
            StringBuilder sub = new StringBuilder();
            if (key != null && !key.isBlank()) {
                sub.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.key")).append(key);
            }
            long total = lng(obj, "totalBytes");
            long downloaded = lng(obj, "downloadedBytes");
            if (total > 0) {
                if (sub.length() > 0) {
                    sub.append("  ");
                }
                sub.append(downloaded).append('/').append(total).append(" B");
            }
            if (error != null && !error.isBlank()) {
                if (sub.length() > 0) {
                    sub.append("  ");
                }
                sub.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.error")).append(error);
            }
            rows.add(new Row(title, sub.toString(), sub.toString(), tag, tagState));
        }
        return rows;
    }

    private static List<Row> extractRequiredRows(JsonObject response) {
        List<Row> rows = new ArrayList<>();
        JsonArray arr = itemsArray(response);
        if (arr == null) return rows;
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject obj = e.getAsJsonObject();
            String name = str(obj, "name");
            String identifier = str(obj, "identifier");
            String id = str(obj, "id");
            String fileName = str(obj, "fileName");
            String title = firstNonBlank(name, identifier, id, DlcFxI18n.tr("dlc_manager.javafx.status.unnamed"));
            boolean hasFile = bool(obj, "hasFile");
            boolean needsApply = bool(obj, "needsApply");
            boolean applied = bool(obj, "applied");
            String tag;
            String tagState;
            if (!hasFile) {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.missing_download");
                tagState = DlcFxStyle.CHIP_ERROR;
            } else if (needsApply) {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.needs_install");
                tagState = DlcFxStyle.CHIP_WARN;
            } else if (applied) {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.installed");
                tagState = DlcFxStyle.CHIP_OK;
            } else {
                tag = DlcFxI18n.tr("dlc_manager.javafx.status.downloaded");
                tagState = DlcFxStyle.CHIP_NEUTRAL;
            }
            StringBuilder sub = new StringBuilder();
            if (identifier != null && !identifier.isBlank()) {
                sub.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.identifier")).append(identifier);
            }
            if (fileName != null && !fileName.isBlank()) {
                if (sub.length() > 0) {
                    sub.append("  ");
                }
                sub.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.file")).append(fileName);
            }
            StringBuilder detail = new StringBuilder();
            if (id != null && !id.isBlank()) {
                detail.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.id")).append(id);
            }
            if (identifier != null && !identifier.isBlank()) {
                if (detail.length() > 0) {
                    detail.append('\n');
                }
                detail.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.identifier")).append(identifier);
            }
            if (fileName != null && !fileName.isBlank()) {
                if (detail.length() > 0) {
                    detail.append('\n');
                }
                detail.append(DlcFxI18n.tr("dlc_manager.javafx.tooltip.file")).append(fileName);
            }
            rows.add(new Row(title, sub.toString(), detail.toString(), tag, tagState));
        }
        return rows;
    }

    private static JsonArray itemsArray(JsonObject response) {
        if (response == null) {
            return null;
        }
        JsonElement element = response.get("items");
        return (element != null && element.isJsonArray()) ? element.getAsJsonArray() : null;
    }

    /** Build a compact, human-readable summary of a status/ack response. */
    private static String summarizeStatus(JsonObject response) {
        if (response == null) {
            return "";
        }
        // download_start / download_status carry a nested "status" object
        // (DlcServerRemoteStatus / DlcServerRequiredStatus); cancel/uninstall/
        // apply carry a single scalar.  Avoids dumping raw JSON at the user.
        JsonElement statusElement = response.get("status");
        if (statusElement != null && statusElement.isJsonObject()) {
            JsonObject status = statusElement.getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            boolean running = bool(status, "running");
            boolean finished = bool(status, "finished");
            boolean success = bool(status, "success");
            if (finished) {
                sb.append(success ? DlcFxI18n.tr("dlc_manager.javafx.status.completed_short") : DlcFxI18n.tr("dlc_manager.javafx.status.completed_with_failures"));
            } else if (running) {
                sb.append(DlcFxI18n.tr("dlc_manager.javafx.status.downloading_short"));
            } else {
                sb.append(DlcFxI18n.tr("dlc_manager.javafx.status.not_running"));
            }
            int total = (int) lng(status, "totalCount");
            int completed = (int) lng(status, "completedCount");
            if (total > 0 || completed > 0) {
                sb.append(' ').append(completed).append('/').append(total);
            }
            String error = str(status, "error");
            if (error != null && !error.isBlank()) {
                sb.append(DlcFxI18n.tr("dlc_manager.javafx.status.error_suffix", error));
            }
            return sb.toString();
        }
        // required_download_cancel → "canceled"
        JsonElement canceledElement = response.get("canceled");
        if (canceledElement != null && canceledElement.isJsonPrimitive()) {
            return DlcFxI18n.tr("dlc_manager.javafx.status.canceled_value", canceledElement.getAsBoolean());
        }
        // required_uninstall → "removed"
        JsonElement removedElement = response.get("removed");
        if (removedElement != null && removedElement.isJsonPrimitive()) {
            return DlcFxI18n.tr("dlc_manager.javafx.status.removed_count", removedElement.getAsInt());
        }
        // required_apply → "applied"
        JsonElement appliedElement = response.get("applied");
        if (appliedElement != null && appliedElement.isJsonPrimitive()) {
            return DlcFxI18n.tr("dlc_manager.javafx.status.applied_count", appliedElement.getAsInt());
        }
        JsonElement items = response.get("items");
        if (items != null && items.isJsonArray()) {
            return DlcFxI18n.tr("dlc_manager.javafx.common.count_items", items.getAsJsonArray().size());
        }
        return DlcFxI18n.tr("dlc_manager.javafx.status.ok");
    }

    // ───────────────────────────────────────────────────────────────
    //  JSON helpers
    // ───────────────────────────────────────────────────────────────

    private static String str(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static long lng(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return e.getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }
}
