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

    private static final String TAG_RED =
            "-fx-text-fill: white; -fx-background-color: #c0392b; -fx-background-radius: 3; -fx-padding: 0 4;";
    private static final String TAG_ORANGE =
            "-fx-text-fill: white; -fx-background-color: #e67e22; -fx-background-radius: 3; -fx-padding: 0 4;";
    private static final String TAG_GREEN =
            "-fx-text-fill: white; -fx-background-color: #27ae60; -fx-background-radius: 3; -fx-padding: 0 4;";
    private static final String TAG_GRAY =
            "-fx-text-fill: white; -fx-background-color: gray; -fx-background-radius: 3; -fx-padding: 0 4;";

    private final RemoteConnection conn;
    private final ObservableList<Row> remoteItems = FXCollections.observableArrayList();
    private final ObservableList<Row> requiredItems = FXCollections.observableArrayList();
    private final Label remoteStatus = new Label("未连接");
    private final Label requiredStatus = new Label("未连接");

    RemoteRemoteRequiredTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab("远程 · Remote/Required");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Remote section ────────────────────────────────────────
        Button remoteRefreshBtn = new Button("刷新 Remote");
        remoteRefreshBtn.disableProperty().bind(conn.connectedProperty().not());
        remoteRefreshBtn.setOnAction(e -> {
            remoteStatus.setText("加载中…");
            conn.runAsync(f -> f.remoteList(), resp -> {
                List<Row> rows = extractRemoteRows(resp);
                remoteItems.setAll(rows);
                remoteStatus.setText("共 " + rows.size() + " 项");
            }, err -> remoteStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        Button remoteDownloadBtn = new Button("下载全部缺失");
        remoteDownloadBtn.disableProperty().bind(conn.connectedProperty().not());
        remoteDownloadBtn.setOnAction(e -> {
            remoteStatus.setText("启动下载…");
            conn.runAsync(f -> f.remoteDownloadStart(null), resp -> {
                remoteStatus.setText("下载已启动: " + summarizeStatus(resp));
            }, err -> remoteStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        Button remoteStatusBtn = new Button("查询下载状态");
        remoteStatusBtn.disableProperty().bind(conn.connectedProperty().not());
        remoteStatusBtn.setOnAction(e -> {
            conn.runAsync(f -> f.remoteDownloadStatus(), resp -> {
                remoteStatus.setText("状态: " + summarizeStatus(resp));
            }, err -> remoteStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        ListView<Row> remoteList = new ListView<>(remoteItems);
        remoteList.setCellFactory(param -> new RowCell());
        VBox.setVgrow(remoteList, Priority.ALWAYS);

        VBox remoteBox = new VBox(4,
                new Label("Remote 远程下载 (缺失项)"),
                new HBox(8, remoteRefreshBtn, remoteDownloadBtn, remoteStatusBtn, remoteStatus),
                remoteList);
        remoteBox.setPadding(new Insets(4));

        // ── Required section ──────────────────────────────────────
        Button requiredRefreshBtn = new Button("刷新 Required");
        requiredRefreshBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredRefreshBtn.setOnAction(e -> {
            requiredStatus.setText("加载中…");
            conn.runAsync(f -> f.requiredList(), resp -> {
                List<Row> rows = extractRequiredRows(resp);
                requiredItems.setAll(rows);
                requiredStatus.setText("共 " + rows.size() + " 项");
            }, err -> requiredStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        Button requiredDownloadBtn = new Button("下载全部");
        requiredDownloadBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredDownloadBtn.setOnAction(e -> {
            requiredStatus.setText("启动下载…");
            conn.runAsync(f -> f.requiredDownloadStart(null), resp -> {
                requiredStatus.setText("下载已启动: " + summarizeStatus(resp));
            }, err -> requiredStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        Button requiredCancelBtn = new Button("取消下载");
        requiredCancelBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredCancelBtn.setOnAction(e -> {
            conn.runAsync(f -> f.requiredDownloadCancel(), resp -> {
                requiredStatus.setText("已取消: " + summarizeStatus(resp));
            }, err -> requiredStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        Button requiredApplyBtn = new Button("应用 Required");
        requiredApplyBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredApplyBtn.setOnAction(e -> {
            requiredStatus.setText("应用中…");
            conn.runAsync(f -> f.requiredApply(null), resp -> {
                requiredStatus.setText("已应用: " + summarizeStatus(resp));
            }, err -> requiredStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        Button requiredUninstallBtn = new Button("卸载 Required");
        requiredUninstallBtn.disableProperty().bind(conn.connectedProperty().not());
        requiredUninstallBtn.setOnAction(e -> {
            requiredStatus.setText("卸载中…");
            conn.runAsync(f -> f.requiredUninstall(null), resp -> {
                requiredStatus.setText("已卸载: " + summarizeStatus(resp));
            }, err -> requiredStatus.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        ListView<Row> requiredList = new ListView<>(requiredItems);
        requiredList.setCellFactory(param -> new RowCell());
        VBox.setVgrow(requiredList, Priority.ALWAYS);

        VBox requiredBox = new VBox(4,
                new Label("Required 必装资源"),
                new HBox(8, requiredRefreshBtn, requiredDownloadBtn, requiredCancelBtn, requiredApplyBtn, requiredUninstallBtn, requiredStatus),
                requiredList);
        requiredBox.setPadding(new Insets(4));

        // ── Layout: side by side ──────────────────────────────────
        HBox center = new HBox(8, remoteBox, requiredBox);
        HBox.setHgrow(remoteBox, Priority.ALWAYS);
        HBox.setHgrow(requiredBox, Priority.ALWAYS);
        center.setPadding(new Insets(4, 8, 4, 8));
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
        final String tagStyle;

        Row(String title, String subtitle, String detail, String tag, String tagStyle) {
            this.title = title;
            this.subtitle = subtitle;
            this.detail = detail;
            this.tag = tag;
            this.tagStyle = tagStyle;
        }
    }

    private static final class RowCell extends javafx.scene.control.ListCell<Row> {
        private final Label title = new Label();
        private final Label tag = new Label();
        private final Label subtitle = new Label();
        private final HBox row = new HBox(8, title, tag, subtitle);

        RowCell() {
            title.setStyle("-fx-font-weight: bold;");
            subtitle.setStyle("-fx-text-fill: #666;");
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
                tag.setStyle(item.tagStyle);
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
            String title = firstNonBlank(name, key, "(未命名)");
            boolean done = bool(obj, "done");
            boolean success = bool(obj, "success");
            String error = str(obj, "error");
            String tag;
            String tagStyle;
            if (done && success) {
                tag = " 已下载 ";
                tagStyle = TAG_GREEN;
            } else if (done) {
                tag = " 失败 ";
                tagStyle = TAG_RED;
            } else {
                tag = " 缺失 ";
                tagStyle = TAG_RED;
            }
            StringBuilder sub = new StringBuilder();
            if (key != null && !key.isBlank()) {
                sub.append("key: ").append(key);
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
                sub.append("错误: ").append(error);
            }
            rows.add(new Row(title, sub.toString(), sub.toString(), tag, tagStyle));
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
            String title = firstNonBlank(name, identifier, id, "(未命名)");
            boolean hasFile = bool(obj, "hasFile");
            boolean needsApply = bool(obj, "needsApply");
            boolean applied = bool(obj, "applied");
            String tag;
            String tagStyle;
            if (!hasFile) {
                tag = " 缺失下载 ";
                tagStyle = TAG_RED;
            } else if (needsApply) {
                tag = " 待安装 ";
                tagStyle = TAG_ORANGE;
            } else if (applied) {
                tag = " 已安装 ";
                tagStyle = TAG_GREEN;
            } else {
                tag = " 已下载 ";
                tagStyle = TAG_GRAY;
            }
            StringBuilder sub = new StringBuilder();
            if (identifier != null && !identifier.isBlank()) {
                sub.append("标识: ").append(identifier);
            }
            if (fileName != null && !fileName.isBlank()) {
                if (sub.length() > 0) {
                    sub.append("  ");
                }
                sub.append("文件: ").append(fileName);
            }
            StringBuilder detail = new StringBuilder();
            if (id != null && !id.isBlank()) {
                detail.append("id: ").append(id);
            }
            if (identifier != null && !identifier.isBlank()) {
                if (detail.length() > 0) {
                    detail.append('\n');
                }
                detail.append("标识: ").append(identifier);
            }
            if (fileName != null && !fileName.isBlank()) {
                if (detail.length() > 0) {
                    detail.append('\n');
                }
                detail.append("文件: ").append(fileName);
            }
            rows.add(new Row(title, sub.toString(), detail.toString(), tag, tagStyle));
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
                sb.append(success ? "已完成" : "已完成(存在失败)");
            } else if (running) {
                sb.append("下载中");
            } else {
                sb.append("未运行");
            }
            int total = (int) lng(status, "totalCount");
            int completed = (int) lng(status, "completedCount");
            if (total > 0 || completed > 0) {
                sb.append(' ').append(completed).append('/').append(total);
            }
            String error = str(status, "error");
            if (error != null && !error.isBlank()) {
                sb.append("，错误: ").append(error);
            }
            return sb.toString();
        }
        // required_download_cancel → "canceled"
        JsonElement canceledElement = response.get("canceled");
        if (canceledElement != null && canceledElement.isJsonPrimitive()) {
            return "canceled=" + canceledElement.getAsBoolean();
        }
        // required_uninstall → "removed"
        JsonElement removedElement = response.get("removed");
        if (removedElement != null && removedElement.isJsonPrimitive()) {
            return "已移除 " + removedElement.getAsInt() + " 项";
        }
        // required_apply → "applied"
        JsonElement appliedElement = response.get("applied");
        if (appliedElement != null && appliedElement.isJsonPrimitive()) {
            return "已应用 " + appliedElement.getAsInt() + " 项";
        }
        JsonElement items = response.get("items");
        if (items != null && items.isJsonArray()) {
            return "共 " + items.getAsJsonArray().size() + " 项";
        }
        return "ok";
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
