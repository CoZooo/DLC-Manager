package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.ui.common.DlcFxConfirmDialogs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.update.DlcFxUpdateActionFlow;
import com.cozooo.dlc_manager.ui.update.DlcFxUpdateToolbarFlow;
import com.cozooo.dlc_manager.ui.update.DlcFxUpdateShell;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class DlcFxRemoteHotUpdateTab {
    private static final Gson GSON = new Gson();

    private final DlcFxRemoteConnection conn;
    private final DlcFxUpdateActionFlow actionFlow;
    private final DlcFxUpdateToolbarFlow toolbarFlow;
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private DlcFxUpdateShell<DlcUpdateRowPlanner.UpdateRow> shell;
    private boolean loadedForCurrentConnection;

    public DlcFxRemoteHotUpdateTab(DlcFxRemoteConnection conn) {
        this.conn = conn;
        this.actionFlow = new DlcFxUpdateActionFlow(new RemoteUpdateActions());
        this.toolbarFlow = new DlcFxUpdateToolbarFlow(new RemoteUpdateToolbarActions());
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
        progressBar.managedProperty().bind(progressBar.visibleProperty());
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        shell = new DlcFxUpdateShell<>(
            new RemoteUpdateAdapter(),
            statusLabel,
            progressBar,
            DlcFxI18n.tr("dlc_manager.remote_update.query_state"),
            DlcFxI18n.tr("dlc_manager.update.import_package"),
            DlcFxI18n.tr("dlc_manager.remote_update.import_url"),
            DlcFxI18n.tr("dlc_manager.remote_update.apply_all"),
            toolbarFlow::refresh,
            toolbarFlow::importPackage,
            toolbarFlow::importUrl,
            toolbarFlow::extraAction
        );
        shell.bindAvailable(conn.connectedProperty());
        DlcFxStyle.routeStatus(statusLabel);
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.remote_update"), shell.root());
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            refreshState();
        }
    }

    private void refreshState() {
        setBusy(true, DlcFxI18n.tr("dlc_manager.status.querying"));
        conn.runAsync(f -> f.updateState(), response -> {
            String base = displayVersion(str(response, "base"));
            String dlc = displayVersion(str(response, "dlc"));
            shell.setVersionText(DlcFxI18n.tr("dlc_manager.update.current_version", base, dlc));
            String rollbackFrom = str(response, "rollbackFrom");
            shell.setRollbackInfo(rollbackFrom == null || rollbackFrom.isBlank()
                ? ""
                : DlcFxI18n.tr("dlc_manager.update.rollback_info", rollbackFrom, base + "+" + dlc));
            DlcUpdateRowPlanner.PlanResult plan = planRows(response);
            shell.setRows(plan.rows);
            shell.setUpdateHint(plan.hasVisibleUpdates
                ? DlcFxI18n.tr("dlc_manager.update.desc")
                : "");
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.update_count",
                visibleUpdateCount(plan.rows)));
            setBusy(false, null);
            loadedForCurrentConnection = true;
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            setBusy(false, null);
        });
    }

    private void confirmApplyAll() {
        if (!confirm(DlcFxI18n.tr("dlc_manager.remote_update.apply_title"), DlcFxI18n.tr("dlc_manager.remote_update.apply_confirm"))) {
            return;
        }
        apply(null, null, null, null);
    }

    private void apply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.status.applying"));
        conn.runAsync(f -> f.updateApply(baseOld, dlcOld, baseNew, dlcNew), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", response.toString()));
            setBusy(false, null);
            refreshState();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            setBusy(false, null);
        });
    }

    private void confirmApplyByVersion(DlcUpdateRowPlanner.UpdateRow row) {
        if (!confirm(DlcFxI18n.tr("dlc_manager.remote_update.apply_by_version_title"),
            DlcFxI18n.tr("dlc_manager.remote_update.apply_by_version_confirm"))) {
            return;
        }
        DlcUpdateMeta meta = row == null ? null : row.meta;
        apply(
            meta == null ? null : meta.getBaseOld(),
            meta == null ? null : meta.getDlcOld(),
            meta == null ? null : meta.getBaseNew(),
            meta == null ? null : meta.getDlcNew()
        );
    }

    private void confirmRollback(String target) {
        if (target == null || target.isBlank()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.rollback_target_required"));
            return;
        }
        if (!confirm(DlcFxI18n.tr("dlc_manager.remote_update.rollback_title"),
            DlcFxI18n.tr("dlc_manager.remote_update.rollback_confirm", target))) {
            return;
        }
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.rolling_back"));
        conn.runAsync(f -> f.updateRollback(target), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.rollback_result", response.toString()));
            setBusy(false, null);
            refreshState();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            setBusy(false, null);
        });
    }

    private void runImportDialog() {
        TextField pathField = new TextField();
        pathField.setPromptText(DlcFxI18n.tr("dlc_manager.remote_update.server_package_path"));
        pathField.setPrefWidth(460);
        Label error = new Label();
        DlcFxStyle.muted(error);
        VBox content = new VBox(10, pathField, error);
        DlcFxModalOverlay.show(shell.root().getScene(),
            DlcFxI18n.tr("dlc_manager.remote_update.import_title"),
            content,
            () -> {
                String path = pathField.getText() == null ? "" : pathField.getText().trim();
                if (path.isBlank()) {
                    error.setText(DlcFxI18n.tr("dlc_manager.remote_update.server_package_path_required"));
                    return;
                }
                runImportPackage(path);
            },
            DlcFxI18n.tr("dlc_manager.pack.import"),
            () -> {},
            DlcFxI18n.tr("dlc_manager.common.cancel"),
            button -> button.disableProperty().bind(pathField.textProperty().isEmpty()));
    }

    private void runImportPackage(String path) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.importing"));
        conn.runAsync(f -> f.updateImport(path), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.import_result", response.toString()));
            setBusy(false, null);
            refreshState();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            setBusy(false, null);
        });
    }

    private void runShowImportUrlDialog(DlcFxUpdateToolbarFlow.UrlImportPrompt prompt) {
        TextField urlField = new TextField();
        urlField.setPromptText(DlcFxI18n.tr("dlc_manager.common.url"));
        urlField.setPrefWidth(420);
        TextField nameField = new TextField();
        nameField.setPromptText(DlcFxI18n.tr("dlc_manager.common.file_name"));
        Label error = new Label();
        DlcFxStyle.muted(error);
        HBox fields = new HBox(8, urlField, nameField);
        fields.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, fields, error);
        DlcFxModalOverlay.show(shell.root().getScene(),
            DlcFxI18n.tr("dlc_manager.remote_update.import_url_title"),
            content,
            () -> {
                String url = urlField.getText() == null ? "" : urlField.getText().trim();
                String name = nameField.getText() == null ? "" : nameField.getText().trim();
                if (url.isBlank()) {
                    error.setText(DlcFxI18n.tr("dlc_manager.common.url_required"));
                    return;
                }
                prompt.submit(url, name);
            },
            DlcFxI18n.tr("dlc_manager.update.url_download"),
            () -> {},
            DlcFxI18n.tr("dlc_manager.common.cancel"),
            button -> button.disableProperty().bind(urlField.textProperty().isEmpty()));
    }

    private void runImportUrl(String url, String name) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.remote_update.downloading_importing"));
        progressBar.setProgress(-1);
        conn.runAsync(f -> f.updateImportUrl(url, name), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.import_result", response.toString()));
            setBusy(false, null);
            refreshState();
        }, error -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxRemoteConnection.safeMessage(error)));
            setBusy(false, null);
        });
    }

    private final class RemoteUpdateToolbarActions implements DlcFxUpdateToolbarFlow.Actions {
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
            refreshState();
        }

        @Override
        public void importPackage() {
            runImportDialog();
        }

        @Override
        public void promptUrlImport(DlcFxUpdateToolbarFlow.UrlImportPrompt prompt) {
            runShowImportUrlDialog(prompt);
        }

        @Override
        public void emptyUrl() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.url_required"));
        }

        @Override
        public void importUrl(String url, String fileName) {
            runImportUrl(url, fileName);
        }

        @Override
        public void extraAction() {
            confirmApplyAll();
        }
    }

    private void setBusy(boolean busy, String status) {
        if (shell != null) {
            shell.setBusy(busy, status);
            shell.updateActionDisableState(true);
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return "";
        }
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static String displayVersion(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static boolean confirm(String title, String message) {
        return DlcFxConfirmDialogs.confirm(title, message);
    }

    private static DlcUpdateRowPlanner.PlanResult planRows(JsonObject response) {
        String rollbackFrom = str(response, "rollbackFrom");
        boolean rolledBack = rollbackFrom != null && !rollbackFrom.isBlank();
        return DlcUpdateRowPlanner.planServerRows(
            parseHistory(response),
            parseUpdates(response),
            rolledBack,
            str(response, "base"),
            str(response, "dlc")
        );
    }

    private static List<DlcUpdateMeta> parseHistory(JsonObject response) {
        List<DlcUpdateMeta> out = new ArrayList<>();
        JsonElement element = response == null ? null : response.get("history");
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        for (JsonElement value : element.getAsJsonArray()) {
            try {
                DlcUpdateMeta meta = GSON.fromJson(value, DlcUpdateMeta.class);
                if (meta != null) {
                    out.add(meta);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static List<DlcServerUpdateInfo> parseUpdates(JsonObject response) {
        List<DlcServerUpdateInfo> out = new ArrayList<>();
        JsonElement element = response == null ? null : response.get("updates");
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        for (JsonElement value : element.getAsJsonArray()) {
            try {
                DlcServerUpdateInfo info = GSON.fromJson(value, DlcServerUpdateInfo.class);
                if (info != null) {
                    out.add(info);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static long visibleUpdateCount(List<DlcUpdateRowPlanner.UpdateRow> plannedRows) {
        long count = 0;
        for (DlcUpdateRowPlanner.UpdateRow row : plannedRows == null ? List.<DlcUpdateRowPlanner.UpdateRow>of() : plannedRows) {
            if (row != null && row.action == DlcUpdateRowPlanner.Action.UPDATE) {
                count++;
            }
        }
        return count;
    }

    private final class RemoteUpdateAdapter implements DlcFxUpdateShell.Adapter<DlcUpdateRowPlanner.UpdateRow> {
        @Override
        public String transitionText(DlcUpdateRowPlanner.UpdateRow row) {
            return row == null ? "" : DlcFxUpdateShell.describeTransition(row);
        }

        @Override
        public String changelog(DlcUpdateRowPlanner.UpdateRow row) {
            return row == null || row.meta == null ? "" : row.meta.resolveChangelog(currentLang());
        }

        @Override
        public DlcFxUpdateShell.Tag tag(DlcUpdateRowPlanner.UpdateRow row) {
            return row == null ? DlcFxUpdateShell.Tag.HISTORY : DlcFxUpdateShell.tagFor(row);
        }

        @Override
        public DlcFxUpdateShell.Action action(DlcUpdateRowPlanner.UpdateRow row) {
            return row == null ? DlcFxUpdateShell.Action.NONE : DlcFxUpdateShell.actionFor(row);
        }

        @Override
        public void performAction(DlcUpdateRowPlanner.UpdateRow row) {
            actionFlow.perform(row);
        }
    }

    private final class RemoteUpdateActions implements DlcFxUpdateActionFlow.Actions {
        @Override
        public void targetMissing() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.remote_update.rollback_target_required"));
        }

        @Override
        public void managerRollback(String targetVersion) {
            confirmRollback(targetVersion);
        }

        @Override
        public void rollback(String targetVersion) {
            confirmRollback(targetVersion);
        }

        @Override
        public void reapply(DlcUpdateRowPlanner.UpdateRow row) {
            confirmApplyByVersion(row);
        }

        @Override
        public void apply(DlcUpdateRowPlanner.UpdateRow row) {
            confirmApplyByVersion(row);
        }
    }

    private static String currentLang() {
        return DlcDcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode());
    }
}
