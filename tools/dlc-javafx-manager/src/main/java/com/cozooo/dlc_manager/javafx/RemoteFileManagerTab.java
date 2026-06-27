package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
import javafx.application.Platform;
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
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.disconnected"));
    private final Label pathLabel = new Label("/");
    private String currentPath = "";

    private final Stage primaryStage;

    RemoteFileManagerTab(RemoteConnection conn, Stage primaryStage) {
        this.conn = conn;
        this.primaryStage = primaryStage;
    }

    Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.javafx.tab.remote_files"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        // ── Top: path + list ──────────────────────────────────────
        TextField pathField = new TextField("/");
        pathField.setPrefColumnCount(30);
        Button listBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.files.list"));
        listBtn.disableProperty().bind(conn.connectedProperty().not());
        listBtn.setOnAction(e -> {
            currentPath = pathField.getText().trim();
            pathLabel.setText(currentPath.isEmpty() ? "/" : currentPath);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.loading"));
            conn.runAsync(f -> f.listFiles(currentPath), list -> {
                entries.setAll(formatEntries(list));
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.common.count_items", list.size()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.failed", RemoteConnection.safeMessage(err))));
        });

        HBox topBox = new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.server.files_path")), pathField, listBtn, pathLabel, statusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        // ── Center: file list ─────────────────────────────────────
        ListView<String> listView = new ListView<>(entries);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox center = new VBox(listView);
        center.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(center);
        root.setCenter(center);

        // ── Bottom: upload/download/delete/push ───────────────────
        Button uploadBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.files.upload_file"));
        Button downloadBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.files.download_file"));
        Button deleteBtn = new Button(DlcFxI18n.tr("dlc_manager.server.files_delete"));
        Button pushBtn = new Button(DlcFxI18n.tr("dlc_manager.javafx.files.push_folder"));
        DlcFxStyle.primary(uploadBtn);
        DlcFxStyle.primary(pushBtn);
        DlcFxStyle.danger(deleteBtn);

        uploadBtn.disableProperty().bind(conn.connectedProperty().not());
        downloadBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());
        pushBtn.disableProperty().bind(conn.connectedProperty().not());

        TextField remoteNameField = new TextField();
        remoteNameField.setPromptText(DlcFxI18n.tr("dlc_manager.javafx.files.remote_name_prompt"));
        remoteNameField.setPrefColumnCount(20);

        uploadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.javafx.files.choose_upload"));
            File f = chooser.showOpenDialog(primaryStage);
            if (f == null) return;
            String remote = remoteNameField.getText().trim();
            if (remote.isBlank()) {
                remote = (currentPath.isEmpty() ? "" : currentPath + "/") + f.getName();
            }
            final String remotePath = remote;
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.uploading"));
            conn.runAsync(client -> {
                client.uploadFile(remotePath, f.toPath());
                return true;
            }, ok -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.upload_done", remotePath));
                // refresh
                conn.runAsync(c -> c.listFiles(currentPath), list -> {
                    entries.setAll(formatEntries(list));
                }, err -> {});
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.upload_failed", RemoteConnection.safeMessage(err))));
        });

        downloadBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            String remotePath;
            if (sel != null && !isDirectoryRow(sel)) {
                String name = entryName(sel);
                remotePath = (currentPath.isEmpty() ? "" : currentPath + "/") + name;
            } else {
                remotePath = remoteNameField.getText().trim();
            }
            if (remotePath.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.select_or_enter_path")); return; }
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.javafx.files.choose_save_dir"));
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.downloading_short"));
            conn.runAsync(client -> client.downloadFile(remotePath, dir.toPath()), saved -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.download_done", saved));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.download_failed", RemoteConnection.safeMessage(err))));
        });

        deleteBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            String remotePath;
            if (sel != null) {
                String name = entryName(sel);
                remotePath = (currentPath.isEmpty() ? "" : currentPath + "/") + name;
            } else {
                remotePath = remoteNameField.getText().trim();
            }
            if (remotePath.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.select_or_enter_path")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.files.delete_title"), DlcFxI18n.tr("dlc_manager.javafx.files.delete_confirm", remotePath))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.status.deleting"));
            conn.runAsync(client -> { client.deleteFile(remotePath); return true; }, ok -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.deleted", remotePath));
                conn.runAsync(c -> c.listFiles(currentPath), list -> entries.setAll(formatEntries(list)), err -> {});
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.delete_failed", RemoteConnection.safeMessage(err))));
        });

        pushBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.javafx.files.choose_push_folder"));
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            if (!confirm(DlcFxI18n.tr("dlc_manager.javafx.files.push_title"), DlcFxI18n.tr("dlc_manager.javafx.files.push_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.pushing"));
            conn.runAsync(client -> {
                pushDirectory(client, dir.toPath());
                return true;
            }, ok -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.push_done")), err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.javafx.files.push_failed", RemoteConnection.safeMessage(err))));
        });

        HBox bottomBox = new HBox(8,
                uploadBtn, downloadBtn, deleteBtn, pushBtn,
                new Label(DlcFxI18n.tr("dlc_manager.javafx.files.remote_name")), remoteNameField);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.statusStrip(bottomBox);
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
                rows.add(dirPrefix() + e.name);
            } else {
                rows.add(filePrefix() + e.name + "  (" + formatSize(e.size) + ")");
            }
        }
        return rows;
    }

    private static boolean isDirectoryRow(String row) {
        return row != null && row.startsWith(dirPrefix());
    }

    private static String entryName(String row) {
        if (row == null) {
            return "";
        }
        String value = row;
        if (value.startsWith(filePrefix())) {
            value = value.substring(filePrefix().length());
        } else if (value.startsWith(dirPrefix())) {
            value = value.substring(dirPrefix().length());
        }
        int sizeSuffix = value.indexOf("  (");
        if (sizeSuffix >= 0) {
            value = value.substring(0, sizeSuffix);
        }
        return value.trim();
    }

    private static String dirPrefix() {
        return DlcFxI18n.tr("dlc_manager.javafx.files.dir_prefix");
    }

    private static String filePrefix() {
        return DlcFxI18n.tr("dlc_manager.javafx.files.file_prefix");
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
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
