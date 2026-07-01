package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.remote.DlcFxResourceDownloadActionFlow;
import com.cozooo.dlc_manager.ui.remote.DlcFxRemoteDownloadListModel;
import com.cozooo.dlc_manager.ui.remote.DlcFxRemoteDownloadShell;
import com.cozooo.dlc_manager.ui.remote.DlcFxRemoteDownloadProgressModel;
import com.cozooo.dlc_manager.ui.remote.DlcFxResourceOperationModel;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class DlcFxRemoteDownloadTab {
    private final DlcFxRemoteConnection conn;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label totalProgressLabel = new Label("");
    private final DlcFxResourceOperationModel operationModel = new DlcFxResourceOperationModel();
    private final DlcFxResourceDownloadActionFlow<Row> actionFlow =
            new DlcFxResourceDownloadActionFlow<>(new RemoteDownloadActions());
    private volatile boolean pollingDownloadStatus;
    private DlcFxRemoteDownloadShell<Row> shell;
    private boolean loadedForCurrentConnection;

    public DlcFxRemoteDownloadTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        shell = new DlcFxRemoteDownloadShell<>(
                new Adapter(operationModel),
                statusLabel,
                countLabel,
                progressBar,
                totalProgressLabel,
                this::refresh,
                actionFlow::downloadMissing,
                actionFlow::downloadSelected,
                this::cancelDownload,
                row -> row.missing && !operationModel.isBatchActive(row.id) ? () -> actionFlow.downloadOne(row) : null
        );
        shell.bindAvailable(conn.connectedProperty());
        DlcFxStyle.muted(countLabel);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);
        root.setTop(shell.topBar());
        VBox center = new VBox(shell.listFrame());
        DlcFxStyle.section(center);
        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);
        root.setCenter(center);
        root.setBottom(shell.bottomBar());
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.remote_remote"), root);
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            refresh();
        }
    }

    private void refresh() {
        operationModel.reset();
        setBusy(true);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        conn.runAsync(f -> f.remoteList(), response -> {
            rows.setAll(parseRows(response));
            shell.setItems(rows);
            int missing = (int) rows.stream().filter(row -> row.missing).count();
            countLabel.setText(DlcFxI18n.tr("dlc_manager.remote.count", rows.size(), missing));
            statusLabel.setText(missing == 0 ? DlcFxI18n.tr("dlc_manager.status.no_missing") : DlcFxI18n.tr("dlc_manager.remote.has_missing"));
            setBusy(false);
            loadedForCurrentConnection = true;
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            setBusy(false);
        });
    }

    private void startDownload(List<String> ids) {
        setBusy(true);
        shell.setCancelVisible(true);
        beginOperation(ids);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.starting_download"));
        conn.runAsync(f -> f.remoteDownloadStart(ids), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_started", summarizeStatus(response)));
            shell.clearSelection();
            pollDownloadStatus();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            operationModel.reset();
            shell.setCancelVisible(false);
            setBusy(false);
        });
    }

    private void beginOperation(List<String> ids) {
        List<String> targets = ids == null || ids.isEmpty()
                ? rows.stream().filter(row -> row.missing).map(row -> row.id).filter(id -> id != null && !id.isBlank()).toList()
                : ids;
        operationModel.beginBatch(targets);
        shell.refreshRows();
    }

    private void setBusy(boolean busy) {
        shell.setBusy(busy);
    }

    private void cancelDownload() {
        operationModel.requestBatchCancel();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
        conn.runAsync(f -> f.remoteDownloadCancel(), response -> {
            pollingDownloadStatus = false;
            operationModel.reset();
            shell.setCancelVisible(false);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.canceled_value", summarizeStatus(response)));
            setBusy(false);
            refresh();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            shell.setCancelVisible(false);
            setBusy(false);
        });
    }

    private void pollDownloadStatus() {
        pollingDownloadStatus = true;
        Thread thread = new Thread(() -> {
            while (pollingDownloadStatus) {
                try {
                    var facade = conn.facade();
                    if (facade == null) {
                        break;
                    }
                    JsonObject response = facade.remoteDownloadStatus();
                    javafx.application.Platform.runLater(() -> applyDownloadStatus(response));
                    JsonObject status = response == null || !response.has("status") ? null : response.getAsJsonObject("status");
                    if (status != null && bool(status, "finished")) {
                        break;
                    }
                    Thread.sleep(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(t)));
                        setBusy(false);
                    });
                    break;
                }
            }
            pollingDownloadStatus = false;
            javafx.application.Platform.runLater(() -> {
                operationModel.finishBatch();
                shell.setCancelVisible(false);
                setBusy(false);
                refresh();
            });
        }, "dlc-remote-download-status");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyDownloadStatus(JsonObject response) {
        JsonObject status = response == null || !response.has("status") ? null : response.getAsJsonObject("status");
        if (status == null) {
            return;
        }
        long totalBytes = lng(status, "totalBytes");
        long downloadedBytes = lng(status, "downloadedBytes");
        int total = (int) lng(status, "totalCount");
        int completed = (int) lng(status, "completedCount");
        boolean hasItemProgress = hasItemProgress(status);
        if (hasItemProgress) {
            DlcFxRemoteDownloadProgressModel.applyItemProgress(status, operationModel);
        }
        if (totalBytes > 0) {
            double progress = Math.max(0d, Math.min(1d, downloadedBytes / (double) totalBytes));
            if (!hasItemProgress) {
                updateActiveProgress((float) progress);
            }
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            progressBar.setProgress(progress);
            totalProgressLabel.setText(Math.max(0, Math.min(100, Math.round((float) (progress * 100d)))) + "%");
        } else {
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            progressBar.setProgress(-1);
            totalProgressLabel.setText(total > 0 ? completed + "/" + total : "");
        }
        statusLabel.setText(summarizeStatus(response));
        shell.refreshRows();
    }

    private static boolean hasItemProgress(JsonObject status) {
        JsonArray items = status == null || !status.has("items") || !status.get("items").isJsonArray()
                ? null
                : status.getAsJsonArray("items");
        return items != null && !items.isEmpty();
    }

    private void updateActiveProgress(float progress) {
        for (String key : operationModel.activeBatchKeys()) {
            operationModel.setProgress(key, progress);
        }
    }

    private static List<Row> parseRows(JsonObject response) {
        List<Row> out = new ArrayList<>();
        JsonArray items = itemsArray(response);
        if (items == null) {
            return out;
        }
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String key = str(obj, "key");
            String name = str(obj, "name");
            String type = str(obj, "type");
            String folder = str(obj, "folder");
            boolean done = bool(obj, "done");
            boolean success = bool(obj, "success");
            String error = str(obj, "error");
            boolean missing = !(done && success);
            String tag;
            String tagStyle;
            if (done && success) {
                tag = DlcFxI18n.tr("dlc_manager.status.present");
                tagStyle = DlcFxStyle.CHIP_OK;
            } else if (done) {
                tag = DlcFxI18n.tr("dlc_manager.status.failed_short");
                tagStyle = DlcFxStyle.CHIP_ERROR;
            } else {
                tag = DlcFxI18n.tr("dlc_manager.status.missing");
                tagStyle = DlcFxStyle.CHIP_ERROR;
            }
            String detail = key == null ? "" : DlcFxI18n.tr("dlc_manager.tooltip.key") + key;
            if (error != null && !error.isBlank()) {
                detail = detail + (detail.isBlank() ? "" : "\n") + DlcFxI18n.tr("dlc_manager.tooltip.error") + error;
            }
            out.add(new Row(firstNonBlank(key, name), firstNonBlank(name, key, DlcFxI18n.tr("dlc_manager.status.unnamed")),
                    firstNonBlank(type), firstNonBlank(folder, "__root__"), detail, tag, tagStyle, missing));
        }
        return out;
    }

    private static JsonArray itemsArray(JsonObject response) {
        if (response == null) {
            return null;
        }
        JsonElement element = response.get("items");
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    static String summarizeStatus(JsonObject response) {
        if (response == null) {
            return "";
        }
        JsonElement statusElement = response.get("status");
        if (statusElement != null && statusElement.isJsonObject()) {
            JsonObject status = statusElement.getAsJsonObject();
            boolean running = bool(status, "running");
            boolean finished = bool(status, "finished");
            boolean success = bool(status, "success");
            int total = (int) lng(status, "totalCount");
            int completed = (int) lng(status, "completedCount");
            String text = finished
                    ? (success ? DlcFxI18n.tr("dlc_manager.status.completed_short") : DlcFxI18n.tr("dlc_manager.status.completed_with_failures"))
                    : running ? DlcFxI18n.tr("dlc_manager.status.downloading_short") : DlcFxI18n.tr("dlc_manager.status.not_running");
            return total > 0 || completed > 0 ? text + " " + completed + "/" + total : text;
        }
        JsonElement items = response.get("items");
        if (items != null && items.isJsonArray()) {
            return DlcFxI18n.tr("dlc_manager.common.count_items", items.getAsJsonArray().size());
        }
        return DlcFxI18n.tr("dlc_manager.status.ok");
    }

    private static String str(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static DlcType typeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return DlcType.UNKNOWN;
        }
        try {
            return DlcType.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return DlcType.UNKNOWN;
        }
    }

    private record Row(String id, String title, String type, String folder, String detail, String tag, String tagStyle, boolean missing) {
        private DlcType dlcType() {
            return typeOf(type);
        }

        private String groupFolder() {
            return DlcFxRemoteDownloadListModel.normalizeFolder(folder);
        }
    }

    private final class RemoteDownloadActions implements DlcFxResourceDownloadActionFlow.Actions<Row> {
        @Override
        public boolean requireAvailable() {
            return conn.isConnected();
        }

        @Override
        public List<Row> missingItems() {
            return rows.stream().filter(row -> row.missing).toList();
        }

        @Override
        public List<Row> selectedItems() {
            return shell == null ? List.of() : shell.selectedItems();
        }

        @Override
        public void startDownload(List<Row> items) {
            List<String> ids = items == null ? List.of() : items.stream()
                    .map(row -> row.id)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            DlcFxRemoteDownloadTab.this.startDownload(ids);
        }

        @Override
        public void showNoMissingDownloads() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.no_missing_downloads"));
        }

        @Override
        public void showSelectListItem() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
        }
    }

    private static final class Adapter implements DlcFxRemoteDownloadShell.Adapter<Row> {
        private final DlcFxResourceOperationModel operationModel;

        private Adapter(DlcFxResourceOperationModel operationModel) {
            this.operationModel = operationModel;
        }

        @Override public String id(Row item) { return item.id; }
        @Override public String title(Row item) { return item.title; }
        @Override public DlcType type(Row item) { return item.dlcType(); }
        @Override public String folder(Row item) { return item.groupFolder(); }
        @Override public String tooltip(Row item) { return item.detail; }
        @Override public boolean missing(Row item) { return item.missing; }
        @Override public boolean itemActionVisible(Row item) { return item.missing && !operationModel.isBatchActive(item.id); }
        @Override public double progress(Row item) { return operationModel.progress(item.id); }
        @Override public String progressText(Row item) {
            float progress = operationModel.progress(item.id);
            return Float.isNaN(progress) ? "" : Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%";
        }
    }
}
