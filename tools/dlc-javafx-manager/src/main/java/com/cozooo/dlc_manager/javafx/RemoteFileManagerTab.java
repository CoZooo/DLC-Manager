package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * [远程] 文件管理器 — file_list/upload/download/delete +
 * push_init/chunk/finish (本地→服务器推送).
 */
final class RemoteFileManagerTab {

    private final RemoteConnection conn;
    private final ObservableList<String> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("未连接");
    private final Label pathLabel = new Label("/");
    private String currentPath = "";

    private final Stage primaryStage;

    RemoteFileManagerTab(RemoteConnection conn, Stage primaryStage) {
        this.conn = conn;
        this.primaryStage = primaryStage;
    }

    Tab build() {
        Tab tab = new Tab("远程 · 文件管理");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: path + list ──────────────────────────────────────
        TextField pathField = new TextField("/");
        pathField.setPrefColumnCount(30);
        Button listBtn = new Button("列出");
        listBtn.disableProperty().bind(conn.connectedProperty().not());
        listBtn.setOnAction(e -> {
            currentPath = pathField.getText().trim();
            pathLabel.setText(currentPath.isEmpty() ? "/" : currentPath);
            statusLabel.setText("加载中…");
            conn.runAsync(f -> f.listFiles(currentPath), list -> {
                entries.setAll(formatEntries(list));
                statusLabel.setText("共 " + list.size() + " 项");
            }, err -> statusLabel.setText("失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox topBox = new HBox(8, new Label("路径:"), pathField, listBtn, pathLabel, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: file list ─────────────────────────────────────
        ListView<String> listView = new ListView<>(entries);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox center = new VBox(listView);
        center.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(center);

        // ── Bottom: upload/download/delete/push ───────────────────
        Button uploadBtn = new Button("上传文件…");
        Button downloadBtn = new Button("下载文件…");
        Button deleteBtn = new Button("删除");
        Button pushBtn = new Button("推送文件夹…");

        uploadBtn.disableProperty().bind(conn.connectedProperty().not());
        downloadBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());
        pushBtn.disableProperty().bind(conn.connectedProperty().not());

        TextField remoteNameField = new TextField();
        remoteNameField.setPromptText("远程文件名 (可选)");
        remoteNameField.setPrefColumnCount(20);

        uploadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择要上传的文件");
            File f = chooser.showOpenDialog(primaryStage);
            if (f == null) return;
            String remote = remoteNameField.getText().trim();
            if (remote.isBlank()) {
                remote = (currentPath.isEmpty() ? "" : currentPath + "/") + f.getName();
            }
            final String remotePath = remote;
            statusLabel.setText("上传中…");
            conn.runAsync(client -> {
                client.uploadFile(remotePath, f.toPath());
                return true;
            }, ok -> {
                statusLabel.setText("上传完成: " + remotePath);
                // refresh
                conn.runAsync(c -> c.listFiles(currentPath), list -> {
                    entries.setAll(formatEntries(list));
                }, err -> {});
            }, err -> statusLabel.setText("上传失败: " + RemoteConnection.safeMessage(err)));
        });

        downloadBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            String remotePath;
            if (sel != null && !sel.startsWith("[DIR]")) {
                String name = sel.startsWith("[FILE] ") ? sel.substring(7) : sel;
                remotePath = (currentPath.isEmpty() ? "" : currentPath + "/") + name.split("\\s+")[0];
            } else {
                remotePath = remoteNameField.getText().trim();
            }
            if (remotePath.isBlank()) { statusLabel.setText("请选择文件或输入路径"); return; }
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择保存目录");
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            statusLabel.setText("下载中…");
            conn.runAsync(client -> client.downloadFile(remotePath, dir.toPath()), saved -> {
                statusLabel.setText("已下载: " + saved);
            }, err -> statusLabel.setText("下载失败: " + RemoteConnection.safeMessage(err)));
        });

        deleteBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            String remotePath;
            if (sel != null) {
                String name = sel.startsWith("[FILE] ") ? sel.substring(7) : (sel.startsWith("[DIR] ") ? sel.substring(6) : sel);
                remotePath = (currentPath.isEmpty() ? "" : currentPath + "/") + name.split("\\s+")[0];
            } else {
                remotePath = remoteNameField.getText().trim();
            }
            if (remotePath.isBlank()) { statusLabel.setText("请选择文件或输入路径"); return; }
            statusLabel.setText("删除中…");
            conn.runAsync(client -> { client.deleteFile(remotePath); return true; }, ok -> {
                statusLabel.setText("已删除: " + remotePath);
                conn.runAsync(c -> c.listFiles(currentPath), list -> entries.setAll(formatEntries(list)), err -> {});
            }, err -> statusLabel.setText("删除失败: " + RemoteConnection.safeMessage(err)));
        });

        pushBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择要推送的文件夹");
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            statusLabel.setText("推送中…");
            conn.runAsync(client -> {
                pushDirectory(client, dir.toPath());
                return true;
            }, ok -> statusLabel.setText("推送完成"), err -> statusLabel.setText("推送失败: " + RemoteConnection.safeMessage(err)));
        });

        HBox bottomBox = new HBox(8,
                uploadBtn, downloadBtn, deleteBtn, pushBtn,
                new Label("远程名:"), remoteNameField);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(8));
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    private void pushDirectory(RemoteConnection conn2, Path dir) {
        // not used; placeholder for direct push via facade
    }

    private void pushDirectory(com.cozooo.dlc_manager.tools.externalsync.DlcTcpFacade facade, Path dir) throws Exception {
        String id = UUID.randomUUID().toString();
        facade.pushInit(id, "overwrite", java.util.List.of("all"), null);
        // Zip the directory in-memory and send chunks
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(baos)) {
            java.nio.file.Files.walk(dir)
                .filter(p -> !java.nio.file.Files.isDirectory(p))
                .forEach(p -> {
                    java.nio.file.Path relative = dir.relativize(p);
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("root/" + relative.toString().replace('\\', '/'));
                    try {
                        zip.putNextEntry(entry);
                        java.nio.file.Files.copy(p, zip);
                        zip.closeEntry();
                    } catch (java.io.IOException ignored) {}
                });
        }
        byte[] data = baos.toByteArray();
        int chunkSize = 64 * 1024;
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int len = Math.min(chunkSize, data.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(data, offset, chunk, 0, len);
            facade.pushChunk(id, chunk);
        }
        facade.pushFinish();
    }

    private static List<String> formatEntries(List<SyncModels.FileEntry> list) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        for (SyncModels.FileEntry e : list) {
            if (e.dir) {
                rows.add("[DIR] " + e.name);
            } else {
                rows.add("[FILE] " + e.name + "  (" + formatSize(e.size) + ")");
            }
        }
        return rows;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
