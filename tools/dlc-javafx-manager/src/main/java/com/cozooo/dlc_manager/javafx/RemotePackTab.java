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
    private final Label statusLabel = new Label("未连接");

    RemotePackTab(RemoteConnection conn) {
        this.conn = conn;
    }

    Tab build() {
        Tab tab = new Tab("远程 · DLC Pack");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: list + apply controls ────────────────────────────
        Button listBtn = new Button("刷新 Pack 列表");
        Button applyStartBtn = new Button("开始应用");
        Button applyStatusBtn = new Button("应用状态");
        Button applyPauseBtn = new Button("暂停");
        Button applyResumeBtn = new Button("继续");
        Button applyCancelBtn = new Button("取消");

        listBtn.disableProperty().bind(conn.connectedProperty().not());
        applyStartBtn.disableProperty().bind(conn.connectedProperty().not());
        applyStatusBtn.disableProperty().bind(conn.connectedProperty().not());
        applyPauseBtn.disableProperty().bind(conn.connectedProperty().not());
        applyResumeBtn.disableProperty().bind(conn.connectedProperty().not());
        applyCancelBtn.disableProperty().bind(conn.connectedProperty().not());

        listBtn.setOnAction(e -> {
            statusLabel.setText("加载中…");
            conn.runAsync(f -> f.packList(), resp -> {
                packs.setAll(extractStrings(resp, "packs"));
                statusLabel.setText("共 " + packs.size() + " 个 Pack");
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        applyStartBtn.setOnAction(e -> {
            if (!confirm("开始应用 Pack", "将开始在服务器应用 DLC Pack，是否继续？")) {
                return;
            }
            statusLabel.setText("启动应用…");
            conn.runAsync(f -> f.packApplyStart(), resp -> statusLabel.setText("已启动: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });
        applyStatusBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyStatus(), resp -> statusLabel.setText("状态: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });
        applyPauseBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyPause(), resp -> statusLabel.setText("已暂停"),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });
        applyResumeBtn.setOnAction(e -> {
            conn.runAsync(f -> f.packApplyResume(), resp -> statusLabel.setText("已继续"),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });
        applyCancelBtn.setOnAction(e -> {
            if (!confirm("取消 Pack 应用", "将取消当前服务器 Pack 应用流程，是否继续？")) {
                return;
            }
            conn.runAsync(f -> f.packApplyCancel(), resp -> statusLabel.setText("已取消"),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox topBox = new HBox(8, listBtn,
                new Label("应用:"), applyStartBtn, applyStatusBtn, applyPauseBtn, applyResumeBtn, applyCancelBtn,
                statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: pack list ─────────────────────────────────────
        ListView<String> packList = new ListView<>(packs);
        VBox.setVgrow(packList, Priority.ALWAYS);
        VBox center = new VBox(packList);
        center.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(center);

        // ── Bottom: install/uninstall/delete/import/order ─────────
        TextField archiveField = new TextField();
        archiveField.setPromptText("archivePath");
        archiveField.setPrefColumnCount(18);
        Button installBtn = new Button("安装");
        Button deleteBtn = new Button("删除");
        installBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());
        installBtn.setOnAction(e -> {
            String p = archiveField.getText().trim();
            if (p.isBlank()) { statusLabel.setText("请输入 archivePath"); return; }
            if (!confirm("安装 Pack", "将安装 Pack: " + p + "\n是否继续？")) {
                return;
            }
            statusLabel.setText("安装中…");
            conn.runAsync(f -> f.packInstall(p), resp -> statusLabel.setText("结果: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });
        deleteBtn.setOnAction(e -> {
            String p = archiveField.getText().trim();
            if (p.isBlank()) { statusLabel.setText("请输入 archivePath"); return; }
            if (!confirm("删除 Pack 归档", "将删除 Pack 归档: " + p + "\n是否继续？")) {
                return;
            }
            statusLabel.setText("删除中…");
            conn.runAsync(f -> f.packDelete(p), resp -> statusLabel.setText("结果: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        TextField idField = new TextField();
        idField.setPromptText("identifier");
        idField.setPrefColumnCount(14);
        Button uninstallBtn = new Button("卸载");
        uninstallBtn.disableProperty().bind(conn.connectedProperty().not());
        uninstallBtn.setOnAction(e -> {
            String id = idField.getText().trim();
            if (id.isBlank()) { statusLabel.setText("请输入 identifier"); return; }
            if (!confirm("卸载 Pack", "将卸载 Pack: " + id + "\n是否继续？")) {
                return;
            }
            statusLabel.setText("卸载中…");
            conn.runAsync(f -> f.packUninstall(id), resp -> statusLabel.setText("结果: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        TextField importPathField = new TextField();
        importPathField.setPromptText("服务器路径");
        importPathField.setPrefColumnCount(14);
        Button importBtn = new Button("导入");
        importBtn.disableProperty().bind(conn.connectedProperty().not());
        importBtn.setOnAction(e -> {
            String p = importPathField.getText().trim();
            if (p.isBlank()) { statusLabel.setText("请输入服务器路径"); return; }
            if (!confirm("导入 Pack", "将从服务器路径导入 Pack: " + p + "\n是否继续？")) {
                return;
            }
            statusLabel.setText("导入中…");
            conn.runAsync(f -> f.packImport(p), resp -> statusLabel.setText("结果: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        TextField urlField = new TextField();
        urlField.setPromptText("URL");
        urlField.setPrefColumnCount(18);
        TextField nameField = new TextField();
        nameField.setPromptText("文件名");
        nameField.setPrefColumnCount(10);
        Button importUrlBtn = new Button("URL导入");
        importUrlBtn.disableProperty().bind(conn.connectedProperty().not());
        importUrlBtn.setOnAction(e -> {
            String url = urlField.getText().trim();
            String name = nameField.getText().trim();
            if (url.isBlank()) { statusLabel.setText("请输入 URL"); return; }
            if (!confirm("URL 导入 Pack", "服务器将下载并导入该 URL，是否继续？")) {
                return;
            }
            statusLabel.setText("下载并导入中…");
            conn.runAsync(f -> f.packImportUrl(url, name), resp -> statusLabel.setText("结果: " + resp.toString()),
                    err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox bottomBox = new HBox(8,
                new Label("安装/删除:"), archiveField, installBtn, deleteBtn,
                new Label("卸载:"), idField, uninstallBtn,
                new Label("导入:"), importPathField, importBtn,
                new Label("URL:"), urlField, nameField, importUrlBtn);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(8));
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
                new ButtonType("取消", ButtonData.CANCEL_CLOSE),
                new ButtonType("确定", ButtonData.OK_DONE));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
    }
}
