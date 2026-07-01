package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.common.DlcFxActionPanel;
import com.cozooo.dlc_manager.ui.common.DlcFxConfirmDialogs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;

import com.cozooo.dlc_manager.tools.externalsync.SyncModels;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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

public final class DlcFxServerFileManagerTab {

    private final DlcFxServerConnection conn;
    private final ObservableList<String> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label pathLabel = new Label("/");
    private String currentPath = "";
    private boolean loadedForCurrentConnection;
    private DlcFxServerSyncTab syncTab;

    private final Stage primaryStage;

    public DlcFxServerFileManagerTab(DlcFxServerConnection conn, Stage primaryStage) {
        this.conn = conn;
        this.primaryStage = primaryStage;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.disconnected"));

        TextField pathField = new TextField("/");
        pathField.setPrefColumnCount(30);
        Button listBtn = new Button(DlcFxI18n.tr("dlc_manager.files.list"));
        listBtn.disableProperty().bind(conn.connectedProperty().not());
        listBtn.setOnAction(e -> {
            currentPath = pathField.getText().trim();
            pathLabel.setText(currentPath.isEmpty() ? "/" : currentPath);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
            conn.runAsync(f -> f.listFiles(currentPath), list -> {
                entries.setAll(formatEntries(list));
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", list.size()));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
        });

        HBox topBox = DlcFxToolbar.create(new Label(DlcFxI18n.tr("dlc_manager.server.files_path")), pathField, listBtn, pathLabel, statusLabel);
        page.setTop(topBox);

        ListView<String> listView = new ListView<>(entries);
        HBox listFrame = DlcFxListViewSupport.wrap(listView);
        VBox.setVgrow(listFrame, Priority.ALWAYS);
        VBox center = new VBox(listFrame);
        page.setCenter(page.section(center));

        Button uploadBtn = new Button(DlcFxI18n.tr("dlc_manager.files.upload_file"));
        Button downloadBtn = new Button(DlcFxI18n.tr("dlc_manager.files.download_file"));
        Button deleteBtn = new Button(DlcFxI18n.tr("dlc_manager.server.files_delete"));
        Button pushBtn = new Button(DlcFxI18n.tr("dlc_manager.files.push_folder"));
        DlcFxStyle.primary(uploadBtn);
        DlcFxStyle.primary(pushBtn);
        DlcFxStyle.danger(deleteBtn);

        uploadBtn.disableProperty().bind(conn.connectedProperty().not());
        downloadBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());
        pushBtn.disableProperty().bind(conn.connectedProperty().not());

        TextField uploadNameField = new TextField();
        uploadNameField.setPromptText(DlcFxI18n.tr("dlc_manager.files.remote_name_prompt"));
        uploadNameField.setPrefColumnCount(24);
        TextField downloadPathField = new TextField();
        downloadPathField.setPromptText(DlcFxI18n.tr("dlc_manager.files.remote_name_prompt"));
        downloadPathField.setPrefColumnCount(24);
        TextField deletePathField = new TextField();
        deletePathField.setPromptText(DlcFxI18n.tr("dlc_manager.files.remote_name_prompt"));
        deletePathField.setPrefColumnCount(24);

        uploadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.files.choose_upload"));
            File f = chooser.showOpenDialog(primaryStage);
            if (f == null) return;
            String remote = uploadNameField.getText().trim();
            if (remote.isBlank()) {
                remote = (currentPath.isEmpty() ? "" : currentPath + "/") + f.getName();
            }
            final String remotePath = remote;
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.uploading"));
            conn.runAsync(client -> {
                client.uploadFile(remotePath, f.toPath());
                return true;
            }, ok -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.upload_done", remotePath));
                conn.runAsync(c -> c.listFiles(currentPath), list -> {
                    entries.setAll(formatEntries(list));
                }, err -> {});
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.upload_failed", DlcFxServerConnection.safeMessage(err))));
        });

        downloadBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            String remotePath;
            if (sel != null && !isDirectoryRow(sel)) {
                String name = entryName(sel);
                remotePath = (currentPath.isEmpty() ? "" : currentPath + "/") + name;
            } else {
                remotePath = downloadPathField.getText().trim();
            }
            if (remotePath.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.select_or_enter_path")); return; }
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.files.choose_save_dir"));
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.downloading_short"));
            conn.runAsync(client -> client.downloadFile(remotePath, dir.toPath()), saved -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.download_done", saved));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_failed", DlcFxServerConnection.safeMessage(err))));
        });

        deleteBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            String remotePath;
            if (sel != null) {
                String name = entryName(sel);
                remotePath = (currentPath.isEmpty() ? "" : currentPath + "/") + name;
            } else {
                remotePath = deletePathField.getText().trim();
            }
            if (remotePath.isBlank()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.select_or_enter_path")); return; }
            if (!confirm(DlcFxI18n.tr("dlc_manager.files.delete_title"), DlcFxI18n.tr("dlc_manager.files.delete_confirm", remotePath))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.deleting"));
            conn.runAsync(client -> { client.deleteFile(remotePath); return true; }, ok -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.deleted", remotePath));
                conn.runAsync(c -> c.listFiles(currentPath), list -> entries.setAll(formatEntries(list)), err -> {});
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.delete_failed", DlcFxServerConnection.safeMessage(err))));
        });

        pushBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.files.choose_push_folder"));
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            if (!confirm(DlcFxI18n.tr("dlc_manager.files.push_title"), DlcFxI18n.tr("dlc_manager.files.push_confirm"))) {
                return;
            }
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.pushing"));
            conn.runAsync(client -> {
                pushDirectory(client, dir.toPath());
                return true;
            }, ok -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.push_done")), err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.push_failed", DlcFxServerConnection.safeMessage(err))));
        });

        ToggleGroup actionGroup = new ToggleGroup();
        ToggleButton uploadMode = modeButton(DlcFxI18n.tr("dlc_manager.files.upload_file"), actionGroup, true);
        ToggleButton downloadMode = modeButton(DlcFxI18n.tr("dlc_manager.files.download_file"), actionGroup, false);
        ToggleButton deleteMode = modeButton(DlcFxI18n.tr("dlc_manager.server.files_delete"), actionGroup, false);
        ToggleButton pushMode = modeButton(DlcFxI18n.tr("dlc_manager.files.push_folder"), actionGroup, false);
        HBox modeBar = new HBox(6, uploadMode, downloadMode, deleteMode, pushMode);
        modeBar.setAlignment(Pos.CENTER_LEFT);

        VBox uploadForm = actionForm(
                DlcFxI18n.tr("dlc_manager.files.upload_file"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.files.remote_name")), uploadNameField, uploadBtn));
        VBox downloadForm = actionForm(
                DlcFxI18n.tr("dlc_manager.files.download_file"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.files.remote_name")), downloadPathField, downloadBtn));
        VBox deleteForm = actionForm(
                DlcFxI18n.tr("dlc_manager.server.files_delete"),
                new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.files.remote_name")), deletePathField, deleteBtn));
        VBox pushForm = actionForm(
                DlcFxI18n.tr("dlc_manager.files.push_folder"),
                pushBtn);

        VBox inspector = new VBox(uploadForm, downloadForm, deleteForm, pushForm);
        setActiveForm(uploadForm, uploadForm, downloadForm, deleteForm, pushForm);
        actionGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == downloadMode) {
                setActiveForm(downloadForm, uploadForm, downloadForm, deleteForm, pushForm);
            } else if (newToggle == deleteMode) {
                setActiveForm(deleteForm, uploadForm, downloadForm, deleteForm, pushForm);
            } else if (newToggle == pushMode) {
                setActiveForm(pushForm, uploadForm, downloadForm, deleteForm, pushForm);
            } else {
                setActiveForm(uploadForm, uploadForm, downloadForm, deleteForm, pushForm);
            }
        });

        VBox actionPanel = DlcFxActionPanel.panel(modeBar, inspector);
        page.setBottom(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.remote_files"), page.root());
    }

    public Tab buildSyncTab(java.util.function.Supplier<String> gameDirTextSupplier,
                            java.util.function.BooleanSupplier gameDirSelectedSupplier,
                            Runnable chooseGameDirAction,
                            Runnable readLocalFilesAction) {
        syncTab = new DlcFxServerSyncTab(gameDirTextSupplier, gameDirSelectedSupplier, chooseGameDirAction, readLocalFilesAction);
        return syncTab.build();
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            loadedForCurrentConnection = true;
        }
    }

    public void onStorageContextChanged() {
        if (syncTab != null) {
            syncTab.onStorageContextChanged();
        }
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

    private void pushDirectory(DlcFxServerConnection conn2, Path dir) {
    }

    private void pushDirectory(com.cozooo.dlc_manager.tools.externalsync.DlcTcpFacade facade, Path dir) throws Exception {
        String id = UUID.randomUUID().toString();
        facade.pushInit(id, "overwrite", java.util.List.of("all"), null);
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
        return DlcFxI18n.tr("dlc_manager.files.dir_prefix");
    }

    private static String filePrefix() {
        return DlcFxI18n.tr("dlc_manager.files.file_prefix");
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static boolean confirm(String title, String message) {
        return DlcFxConfirmDialogs.confirm(title, message);
    }
}
