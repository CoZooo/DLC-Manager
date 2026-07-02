package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredActionFlow;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredListShell;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredOperationModel;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredRefreshFlow;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredSelectionModel;
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

public final class DlcFxServerRequiredTab {
    private final DlcFxServerConnection conn;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label totalProgressLabel = new Label("");
    private final DlcFxRequiredOperationModel operationModel = new DlcFxRequiredOperationModel();
    private final DlcFxRequiredRefreshFlow refreshFlow = new DlcFxRequiredRefreshFlow(new RemoteRequiredRefreshActions());
    private final DlcFxRequiredActionFlow<Row> actionFlow =
            new DlcFxRequiredActionFlow<>(new RemoteRequiredActions());
    private volatile boolean pollingDownloadStatus;
    private DlcFxRequiredListShell<Row> shell;
    private boolean loadedForCurrentConnection;

    public DlcFxServerRequiredTab(DlcFxServerConnection conn) {
        this.conn = conn;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        shell = new DlcFxRequiredListShell<>(
                new Adapter(),
                statusLabel,
                progressBar,
                totalProgressLabel,
                DlcFxI18n.tr("dlc_manager.required.refresh"),
                DlcFxI18n.tr("dlc_manager.required.download_missing"),
                DlcFxI18n.tr("dlc_manager.required.install_selected"),
                refreshFlow::refresh,
                actionFlow::installMissing,
                actionFlow::installSelected,
                this::cancelDownload,
                row -> operationModel.isActive(row.id)
                        ? null
                        : () -> actionFlow.toggle(row)
        );
        shell.bindAvailable(conn.connectedProperty());
        DlcFxStyle.muted(countLabel);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);
        root.setTop(shell.topBar(countLabel));
        VBox center = new VBox(shell.listFrame());
        DlcFxStyle.section(center);
        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);
        root.setCenter(center);
        root.setBottom(shell.bottomBar());
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.required.title"), root);
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
        conn.runAsync(f -> f.requiredList(), response -> {
            rows.setAll(parseRows(response));
            shell.setItems(rows);
            long missing = rows.stream().filter(row -> !row.hasFile).count();
            long needsApply = rows.stream().filter(row -> row.hasFile && row.needsApply).count();
            countLabel.setText(DlcFxI18n.tr("dlc_manager.required.count", rows.size(), missing, needsApply));
            statusLabel.setText(missing == 0 && needsApply == 0
                    ? DlcFxI18n.tr("dlc_manager.status.all_ready")
                    : missing > 0 ? DlcFxI18n.tr("dlc_manager.required.has_missing") : DlcFxI18n.tr("dlc_manager.required.has_needs_apply"));
            setBusy(false);
            loadedForCurrentConnection = true;
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(error)));
            setBusy(false);
        });
    }

    private void install(List<String> ids) {
        setBusy(true);
        shell.setCancelVisible(true);
        beginOperation(ids);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.starting_download"));
        conn.runAsync(f -> f.requiredDownloadStart(ids), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_started", DlcFxServerDownloadTab.summarizeStatus(response)));
            shell.clearSelection();
            applyDownloadStatus(response);
            pollDownloadStatus();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(error)));
            operationModel.reset();
            setBusy(false);
            shell.setCancelVisible(false);
        });
    }

    private void beginOperation(List<String> ids) {
        List<String> targets = ids == null || ids.isEmpty()
                ? rows.stream().filter(row -> !row.hasFile).map(row -> row.id).filter(id -> id != null && !id.isBlank()).toList()
                : ids;
        operationModel.beginBatch(targets);
        shell.refreshRows();
    }

    private void apply(List<String> ids) {
        setBusy(true);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        conn.runAsync(f -> f.requiredApply(ids), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applied_value", DlcFxServerDownloadTab.summarizeStatus(response)));
            shell.clearSelection();
            setBusy(false);
            refresh();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(error)));
            setBusy(false);
        });
    }

    private void uninstall(List<String> ids) {
        setBusy(true);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.uninstalling"));
        conn.runAsync(f -> f.requiredUninstall(ids), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.uninstalled_value", DlcFxServerDownloadTab.summarizeStatus(response)));
            shell.clearSelection();
            setBusy(false);
            refresh();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(error)));
            setBusy(false);
        });
    }

    private void cancelDownload() {
        operationModel.requestCancelAll();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
        conn.runAsync(f -> f.requiredDownloadCancel(), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.canceled_value", DlcFxServerDownloadTab.summarizeStatus(response)));
            pollingDownloadStatus = false;
            operationModel.reset();
            shell.setCancelVisible(false);
            setBusy(false);
            refresh();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(error)));
            setBusy(false);
            shell.setCancelVisible(false);
        });
    }

    private void setBusy(boolean busy) {
        shell.setBusy(busy);
    }

    private final class RemoteRequiredRefreshActions implements DlcFxRequiredRefreshFlow.Actions {
        @Override
        public boolean enabled() {
            return conn.connectedProperty().get();
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.disconnected"));
        }

        @Override
        public void refresh() {
            DlcFxServerRequiredTab.this.refresh();
        }
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
                    JsonObject response = facade.requiredDownloadStatus();
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
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(t)));
                        operationModel.reset();
                        shell.setCancelVisible(false);
                        setBusy(false);
                    });
                    break;
                }
            }
            pollingDownloadStatus = false;
            javafx.application.Platform.runLater(() -> {
                operationModel.reset();
                shell.setCancelVisible(false);
                setBusy(false);
                refresh();
            });
        }, "dlc-remote-required-status");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyDownloadStatus(JsonObject response) {
        JsonObject status = response == null || !response.has("status") ? null : response.getAsJsonObject("status");
        if (status == null) {
            return;
        }
        JsonArray items = status.has("items") && status.get("items").isJsonArray() ? status.getAsJsonArray("items") : null;
        if (items != null) {
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                String id = str(item, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                operationModel.applyProgress(id, lng(item, "downloadedBytes"), lng(item, "totalBytes"), bool(item, "done"));
            }
        }
        long totalBytes = lng(status, "totalBytes");
        long downloadedBytes = lng(status, "downloadedBytes");
        int total = (int) lng(status, "totalCount");
        int completed = (int) lng(status, "completedCount");
        if (totalBytes > 0) {
            double progress = Math.max(0d, Math.min(1d, downloadedBytes / (double) totalBytes));
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
        statusLabel.setText(DlcFxServerDownloadTab.summarizeStatus(response));
        shell.refreshRows();
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
            String id = firstNonBlank(str(obj, "id"), str(obj, "identifier"), str(obj, "fileName"));
            String title = firstNonBlank(str(obj, "name"), str(obj, "identifier"), str(obj, "fileName"), DlcFxI18n.tr("dlc_manager.status.unnamed"));
            boolean hasFile = bool(obj, "hasFile");
            boolean needsApply = bool(obj, "needsApply");
            boolean applied = bool(obj, "applied");
            String fileName = str(obj, "fileName");
            List<String> dependencies = stringArray(obj, "dependencies");
            String detail = id == null ? "" : DlcFxI18n.tr("dlc_manager.tooltip.id") + id;
            if (fileName != null && !fileName.isBlank()) {
                detail = detail + (detail.isBlank() ? "" : "\n") + DlcFxI18n.tr("dlc_manager.tooltip.file") + fileName;
            }
            out.add(new Row(id, title, detail, dependencies, hasFile, needsApply, applied));
        }
        return out;
    }

    private static List<DlcFxRequiredSelectionModel.Item> selectionItems(List<Row> source) {
        List<DlcFxRequiredSelectionModel.Item> out = new ArrayList<>();
        for (Row row : source == null ? List.<Row>of() : source) {
            if (row != null) {
                out.add(new DlcFxRequiredSelectionModel.Item(row.id, row.hasFile, row.needsApply, row.dependencies));
            }
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

    private static List<String> stringArray(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement value : e.getAsJsonArray()) {
            if (value != null && value.isJsonPrimitive()) {
                String raw = value.getAsString();
                if (raw != null && !raw.isBlank()) {
                    out.add(raw);
                }
            }
        }
        return out;
    }

    private record Row(String id, String title, String detail, List<String> dependencies, boolean hasFile, boolean needsApply, boolean installed) {
    }

    private final class RemoteRequiredActions implements DlcFxRequiredActionFlow.Actions<Row> {
        @Override
        public boolean requireAvailable() {
            return conn.isConnected();
        }

        @Override
        public List<Row> missingInstallTargets() {
            return rows.stream().filter(row -> !row.hasFile).toList();
        }

        @Override
        public List<Row> selectedItems() {
            return shell == null ? List.of() : shell.selectedItems();
        }

        @Override
        public List<Row> selectedInstallTargets(List<Row> selectedItems) {
            List<String> missingIds = DlcFxRequiredSelectionModel.installTargetIds(selectionItems(rows), selectionItems(selectedItems));
            return rowsByIds(missingIds);
        }

        @Override
        public boolean isInstalled(Row row) {
            return row != null && row.installed && !row.needsApply;
        }

        @Override
        public boolean isActive(Row row) {
            return row != null && operationModel.isActive(row.id);
        }

        @Override
        public void startInstall(List<Row> items) {
            if (items == null || items.isEmpty()) {
                showSelectedDownloaded();
                return;
            }
            List<String> ids = items.stream()
                    .map(row -> row.id)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            boolean hasMissing = items.stream().anyMatch(row -> !row.hasFile);
            if (hasMissing) {
                install(ids);
                return;
            }
            apply(ids);
        }

        @Override
        public void uninstall(Row row) {
            if (row != null && row.id != null && !row.id.isBlank()) {
                DlcFxServerRequiredTab.this.uninstall(List.of(row.id));
            }
        }

        @Override
        public void showNoInstallable() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.no_installable"));
        }

        @Override
        public void showSelectListItem() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
        }

        @Override
        public void showSelectedDownloaded() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.selected_downloaded"));
        }
    }

    private List<Row> rowsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Row> out = new ArrayList<>();
        for (String id : ids) {
            for (Row row : rows) {
                if (row != null && id != null && id.equals(row.id)) {
                    out.add(row);
                    break;
                }
            }
        }
        return out;
    }

    private final class Adapter implements DlcFxRequiredListShell.Adapter<Row> {
        @Override public String id(Row item) { return item.id; }
        @Override public String title(Row item) { return item.title; }
        @Override public String tooltip(Row item) { return item.detail; }
        @Override public boolean selectable(Row item) { return true; }
        @Override public boolean actionVisible(Row item) { return true; }
        @Override public String actionText(Row item) {
            if (operationModel.isActive(item.id)) {
                return DlcFxI18n.tr("dlc_manager.required.installing");
            }
            return item.installed && !item.needsApply
                    ? DlcFxI18n.tr("dlc_manager.required.uninstall")
                    : DlcFxI18n.tr("dlc_manager.required.install");
        }
        @Override public boolean actionDanger(Row item) { return !operationModel.isActive(item.id) && item.installed && !item.needsApply; }
        @Override public boolean actionDisabled(Row item) { return operationModel.isActive(item.id); }
        @Override public double progress(Row item) {
            float progress = operationModel.progress(item.id);
            return Float.isNaN(progress) ? Double.NaN : progress;
        }
        @Override public String progressText(Row item) { return operationModel.progressText(item.id); }
        @Override public String statusText(Row item) {
            if (operationModel.isActive(item.id)) {
                return DlcFxI18n.tr("dlc_manager.status.installing");
            }
            if (!item.hasFile) {
                return DlcFxI18n.tr("dlc_manager.status.missing_download");
            }
            if (item.needsApply) {
                return DlcFxI18n.tr("dlc_manager.status.needs_install");
            }
            return item.installed ? DlcFxI18n.tr("dlc_manager.status.installed") : DlcFxI18n.tr("dlc_manager.status.downloaded");
        }
        @Override public String statusStyle(Row item) {
            if (operationModel.isActive(item.id)) {
                return DlcFxStyle.CHIP_WARN;
            }
            if (!item.hasFile) {
                return DlcFxStyle.CHIP_ERROR;
            }
            if (item.needsApply) {
                return DlcFxStyle.CHIP_WARN;
            }
            return item.installed ? DlcFxStyle.CHIP_OK : DlcFxStyle.CHIP_NEUTRAL;
        }
    }
}
