package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.common.DlcFxActionPanel;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;

import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DlcFxServerFileManagerTab {

    private final DlcFxServerConnection conn;
    private final ObservableList<FileRow> entries = FXCollections.observableArrayList();
    private final Set<String> selectedEntryNames = new LinkedHashSet<>();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label pathLabel = new Label("/");
    private final Label countLabel = new Label(DlcFxI18n.tr("dlc_manager.common.count_items", 0));
    private ListView<FileRow> listView;
    private String currentPath = "";
    private int selectionAnchor = -1;
    private boolean loadedForCurrentConnection;
    private DlcFxServerSyncTab syncTab;

    private final Stage primaryStage;

    public DlcFxServerFileManagerTab(DlcFxServerConnection conn, Stage primaryStage) {
        this.conn = conn;
        this.primaryStage = primaryStage;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> clearConnectionState(newValue));
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.disconnected"));

        Button upBtn = new Button(DlcFxI18n.tr("dlc_manager.files.up"));
        Button listBtn = new Button(DlcFxI18n.tr("dlc_manager.files.refresh"));
        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        upBtn.disableProperty().bind(conn.connectedProperty().not());
        listBtn.disableProperty().bind(conn.connectedProperty().not());
        selectAllBtn.disableProperty().bind(conn.connectedProperty().not());
        selectNoneBtn.disableProperty().bind(conn.connectedProperty().not());
        invertBtn.disableProperty().bind(conn.connectedProperty().not());
        upBtn.setOnAction(e -> listPath(parentPath(currentPath)));
        listBtn.setOnAction(e -> listPath(currentPath));
        selectAllBtn.setOnAction(e -> selectAllEntries());
        selectNoneBtn.setOnAction(e -> clearEntrySelection());
        invertBtn.setOnAction(e -> invertEntrySelection());
        DlcFxStyle.primary(listBtn);

        HBox pathSummary = new HBox(14,
                new Label(DlcFxI18n.tr("dlc_manager.files.current_path")),
                pathLabel,
                countLabel,
                statusLabel);
        pathSummary.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pathSummary, Priority.ALWAYS);

        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_RIGHT);

        HBox pathBar = new HBox(10,
                upBtn,
                listBtn,
                pathSummary,
                selectionActions);
        pathBar.setAlignment(Pos.CENTER_LEFT);
        pathBar.getStyleClass().add("dlc-file-path-bar");

        VBox topBox = new VBox(pathBar);
        topBox.getStyleClass().add("dlc-file-top");
        page.setTop(topBox);

        listView = new ListView<>(entries);
        listView.getStyleClass().add("dlc-file-list");
        listView.setCellFactory(view -> new FileCell());
        HBox listFrame = DlcFxListViewSupport.wrap(listView);
        VBox.setVgrow(listFrame, Priority.ALWAYS);

        Button uploadBtn = new Button(DlcFxI18n.tr("dlc_manager.files.upload_file"));
        Button downloadBtn = new Button(DlcFxI18n.tr("dlc_manager.files.download_file"));
        Button deleteBtn = new Button(DlcFxI18n.tr("dlc_manager.server.files_delete"));
        DlcFxStyle.danger(deleteBtn);

        uploadBtn.disableProperty().bind(conn.connectedProperty().not());
        downloadBtn.disableProperty().bind(conn.connectedProperty().not());
        deleteBtn.disableProperty().bind(conn.connectedProperty().not());

        uploadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.files.choose_upload"));
            List<File> files = chooser.showOpenMultipleDialog(primaryStage);
            if (files == null || files.isEmpty()) return;
            List<String> remotePaths = files.stream()
                    .map(file -> childPath(currentPath, file.getName()))
                    .toList();
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.uploading"));
            conn.runAsync(client -> {
                for (int i = 0; i < files.size(); i++) {
                    client.uploadFile(remotePaths.get(i), files.get(i).toPath());
                }
                return true;
            }, ok -> {
                reloadCurrentPathAfterOperation(DlcFxI18n.tr("dlc_manager.files.upload_done", summarizeRemotePaths(remotePaths)));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.upload_failed", DlcFxServerConnection.safeMessage(err))));
        });

        downloadBtn.setOnAction(e -> {
            List<FileRow> selectedRows = selectedRows();
            if (selectedContainsDirectory(selectedRows)) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.download_folder_unsupported"));
                return;
            }
            List<String> remotePaths = selectedFilePaths(selectedRows);
            if (remotePaths.isEmpty()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.select_or_enter_path")); return; }
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(DlcFxI18n.tr("dlc_manager.files.choose_save_dir"));
            File dir = chooser.showDialog(primaryStage);
            if (dir == null) return;
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.downloading_short"));
            conn.runAsync(client -> {
                java.util.List<java.nio.file.Path> savedPaths = new java.util.ArrayList<>();
                for (String remotePath : remotePaths) {
                    savedPaths.add(client.downloadFile(remotePath, dir.toPath()));
                }
                return savedPaths;
            }, saved -> {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.download_done", summarizePaths(saved)));
            }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_failed", DlcFxServerConnection.safeMessage(err))));
        });

        deleteBtn.setOnAction(e -> {
            List<String> remotePaths = selectedPaths(selectedRows());
            if (remotePaths.isEmpty()) { statusLabel.setText(DlcFxI18n.tr("dlc_manager.files.select_or_enter_path")); return; }
            showDeleteConfirm(remotePaths);
        });

        VBox center = new VBox(listFrame);
        VBox.setVgrow(listFrame, Priority.ALWAYS);
        page.setCenter(page.section(center));
        page.setBottom(DlcFxActionPanel.panel(DlcFxActionPanel.row(uploadBtn, downloadBtn, deleteBtn)));
        DlcFxStyle.routeStatus(statusLabel);

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.files"), page.root());
    }

    public Tab buildSyncTab(java.util.function.Supplier<String> gameDirTextSupplier,
                                java.util.function.BooleanSupplier gameDirSelectedSupplier,
                                ObservableValue<String> serverPortTextProperty,
                                Runnable chooseGameDirAction,
                                Runnable readLocalFilesAction) {
        syncTab = new DlcFxServerSyncTab(conn, gameDirTextSupplier, gameDirSelectedSupplier, serverPortTextProperty, chooseGameDirAction, readLocalFilesAction);
        return syncTab.build();
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            loadedForCurrentConnection = true;
            listPath(currentPath);
        }
    }

    public void onSyncTabSelected() {
        if (syncTab != null) {
            syncTab.onTabSelected();
        }
    }

    private void clearConnectionState(boolean connected) {
        loadedForCurrentConnection = false;
        clearEntrySelectionState();
        entries.clear();
        currentPath = "";
        pathLabel.setText("/");
        countLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", 0));
        statusLabel.setText(connected
                ? DlcFxI18n.tr("dlc_manager.status.no_data")
                : DlcFxI18n.tr("dlc_manager.status.disconnected"));
    }

    public void onStorageContextChanged() {
        if (syncTab != null) {
            syncTab.onStorageContextChanged();
        }
    }

    private void listPath(String path) {
        if (!conn.connectedProperty().get()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.disconnected"));
            return;
        }
        String normalized = normalizePath(path);
        currentPath = normalized;
        pathLabel.setText(displayPath(normalized));
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        conn.runAsync(f -> f.listFiles(normalized), list -> {
            clearEntrySelectionState();
            entries.setAll(formatEntries(list));
            countLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", entries.size()));
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loaded"));
        }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
    }

    private void reloadCurrentPathAfterOperation(String completionMessage) {
        conn.runAsync(f -> f.listFiles(currentPath), list -> {
            clearEntrySelectionState();
            entries.setAll(formatEntries(list));
            countLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", entries.size()));
            statusLabel.setText(completionMessage);
        }, err -> statusLabel.setText(completionMessage));
    }

    private List<FileRow> selectedRows() {
        if (selectedEntryNames.isEmpty()) {
            return List.of();
        }
        java.util.List<FileRow> rows = new java.util.ArrayList<>();
        for (FileRow row : entries) {
            if (row != null && selectedEntryNames.contains(row.name())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> selectedFilePaths(List<FileRow> selected) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (FileRow row : selected) {
            if (row != null && !row.directory()) {
                paths.add(childPath(currentPath, row.name()));
            }
        }
        return paths;
    }

    private boolean selectedContainsDirectory(List<FileRow> selected) {
        for (FileRow row : selected) {
            if (row != null && row.directory()) {
                return true;
            }
        }
        return false;
    }

    private List<String> selectedPaths(List<FileRow> selected) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (FileRow row : selected) {
            if (row != null) {
                paths.add(childPath(currentPath, row.name()));
            }
        }
        return paths;
    }

    private void selectAllEntries() {
        selectedEntryNames.clear();
        for (FileRow row : entries) {
            if (row != null) {
                selectedEntryNames.add(row.name());
            }
        }
        selectionAnchor = entries.isEmpty() ? -1 : 0;
        syncVisibleItemCells();
    }

    private void clearEntrySelection() {
        clearEntrySelectionState();
        syncVisibleItemCells();
    }

    private void invertEntrySelection() {
        Set<String> next = new LinkedHashSet<>();
        for (FileRow row : entries) {
            if (row != null && !selectedEntryNames.contains(row.name())) {
                next.add(row.name());
            }
        }
        selectedEntryNames.clear();
        selectedEntryNames.addAll(next);
        selectionAnchor = entries.isEmpty() ? -1 : 0;
        syncVisibleItemCells();
    }

    private void selectEntryAt(int index, boolean shortcutDown, boolean shiftDown) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        FileRow row = entries.get(index);
        if (row == null) {
            return;
        }
        if (shiftDown && selectionAnchor >= 0 && selectionAnchor < entries.size()) {
            if (!shortcutDown) {
                selectedEntryNames.clear();
            }
            int start = Math.min(selectionAnchor, index);
            int end = Math.max(selectionAnchor, index);
            for (int i = start; i <= end; i++) {
                FileRow rangeRow = entries.get(i);
                if (rangeRow != null) {
                    selectedEntryNames.add(rangeRow.name());
                }
            }
        } else if (shortcutDown) {
            if (selectedEntryNames.contains(row.name())) {
                selectedEntryNames.remove(row.name());
            } else {
                selectedEntryNames.add(row.name());
            }
            selectionAnchor = index;
        } else {
            selectedEntryNames.clear();
            selectedEntryNames.add(row.name());
            selectionAnchor = index;
        }
        syncVisibleItemCells();
    }

    private void clearEntrySelectionState() {
        selectedEntryNames.clear();
        selectionAnchor = -1;
    }

    private boolean isEntrySelected(FileRow row) {
        return row != null && selectedEntryNames.contains(row.name());
    }

    private void syncVisibleItemCells() {
        if (listView == null) {
            return;
        }
        for (javafx.scene.Node node : listView.lookupAll(".list-cell")) {
            if (node instanceof FileCell cell) {
                cell.syncSelection();
            }
        }
    }

    private static List<FileRow> formatEntries(List<DlcServerFileEntry> list) {
        java.util.List<FileRow> rows = new java.util.ArrayList<>();
        for (DlcServerFileEntry e : list) {
            rows.add(new FileRow(e.name, e.dir, e.size));
        }
        rows.sort(Comparator
                .comparing(FileRow::directory).reversed()
                .thenComparing(row -> row.name().toLowerCase(java.util.Locale.ROOT)));
        return rows;
    }

    private static String normalizePath(String path) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value.equals("/") ? "" : value;
    }

    private static String displayPath(String path) {
        String normalized = normalizePath(path);
        return normalized.isBlank() ? "/" : "/" + normalized;
    }

    private static String childPath(String parent, String child) {
        String normalizedParent = normalizePath(parent);
        String normalizedChild = normalizePath(child);
        if (normalizedParent.isBlank()) {
            return normalizedChild;
        }
        if (normalizedChild.isBlank()) {
            return normalizedParent;
        }
        return normalizedParent + "/" + normalizedChild;
    }

    private static String parentPath(String path) {
        String normalized = normalizePath(path);
        int slash = normalized.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        return normalized.substring(0, slash);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void showDeleteConfirm(List<String> remotePaths) {
        DlcFxModalOverlay.show(
                listView == null ? null : listView.getScene(),
                DlcFxI18n.tr("dlc_manager.files.delete_title"),
                DlcFxRichTextRenderer.formatted(DlcFxI18n.tr("dlc_manager.files.delete_confirm", summarizeRemotePaths(remotePaths))),
                () -> deleteRemotePaths(remotePaths),
                DlcFxI18n.tr("dlc_manager.common.confirm"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.common.cancel"),
                DlcFxStyle::danger
        );
    }

    private void deleteRemotePaths(List<String> remotePaths) {
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.deleting"));
        conn.runAsync(client -> {
            for (String remotePath : remotePaths) {
                client.deleteFile(remotePath);
            }
            return true;
        }, ok -> {
            reloadCurrentPathAfterOperation(DlcFxI18n.tr("dlc_manager.files.deleted", summarizeRemotePaths(remotePaths)));
        }, err -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.delete_failed", DlcFxServerConnection.safeMessage(err))));
    }

    private static String summarizeRemotePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        if (paths.size() == 1) {
            return paths.get(0);
        }
        return String.join("\n", paths);
    }

    private static String summarizePaths(List<java.nio.file.Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        if (paths.size() == 1) {
            return String.valueOf(paths.get(0));
        }
        return DlcFxI18n.tr("dlc_manager.common.count_items", paths.size());
    }

    private record FileRow(String name, boolean directory, long size) {
    }

    private final class FileCell extends ListCell<FileRow> {
        private final CheckBox selectBox = new CheckBox();
        private final StackPane icon = new StackPane();
        private final Region folderTab = new Region();
        private final Region iconBody = new Region();
        private final Region fileCorner = new Region();
        private final Label name = new Label();
        private final Label kind = new Label();
        private final Label size = new Label();
        private final StackPane kindColumn = new StackPane(kind);
        private final StackPane sizeColumn = new StackPane(size);
        private final HBox row = new HBox(12, selectBox, icon, name, kindColumn, sizeColumn);

        private FileCell() {
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("dlc-file-row");
            selectBox.setMouseTransparent(true);
            selectBox.setFocusTraversable(false);
            icon.getStyleClass().add("dlc-file-icon");
            name.getStyleClass().add("dlc-file-name");
            kind.getStyleClass().add("dlc-file-kind");
            size.getStyleClass().add("dlc-file-size");
            kindColumn.getStyleClass().add("dlc-file-kind-column");
            sizeColumn.getStyleClass().add("dlc-file-size-column");
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            row.prefWidthProperty().bind(widthProperty().subtract(18));
            row.maxWidthProperty().bind(widthProperty().subtract(18));
            addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    event.consume();
                }
            });
            addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                FileRow item = getItem();
                if (item == null || isEmpty()) {
                    return;
                }
                if (event.getClickCount() == 2 && item.directory()) {
                    listPath(childPath(currentPath, item.name()));
                } else if (event.getClickCount() == 1) {
                    selectEntryAt(getIndex(), event.isShortcutDown(), event.isShiftDown());
                }
                event.consume();
            });
        }

        @Override
        protected void updateItem(FileRow item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("directory", "file");
            icon.getStyleClass().removeAll("directory", "file");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                syncSelection();
                return;
            }
            getStyleClass().add(item.directory() ? "directory" : "file");
            icon.getStyleClass().add(item.directory() ? "directory" : "file");
            syncIcon(item.directory());
            syncSelection();
            name.setText(item.name());
            kind.setText(DlcFxI18n.tr(item.directory()
                    ? "dlc_manager.files.type_directory"
                    : "dlc_manager.files.type_file"));
            size.setText(item.directory() ? "" : formatSize(item.size()));
            setText(null);
            setGraphic(row);
        }

        private void syncIcon(boolean directory) {
            if (icon.getChildren().isEmpty()) {
                folderTab.getStyleClass().add("dlc-file-folder-tab");
                iconBody.getStyleClass().add("dlc-file-icon-body");
                fileCorner.getStyleClass().add("dlc-file-corner");
                icon.getChildren().addAll(folderTab, iconBody, fileCorner);
            }
            folderTab.setVisible(directory);
            folderTab.setManaged(directory);
            fileCorner.setVisible(!directory);
            fileCorner.setManaged(!directory);
        }

        private void syncSelection() {
            boolean selected = !isEmpty() && isEntrySelected(getItem());
            selectBox.setSelected(selected);
            getStyleClass().remove("dlc-file-selected");
            if (selected) {
                getStyleClass().add("dlc-file-selected");
            }
        }
    }
}
