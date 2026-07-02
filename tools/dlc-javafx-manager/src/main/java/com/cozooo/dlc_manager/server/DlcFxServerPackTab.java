package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerPackApplyStatus;
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackActionState;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackApplyFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackDeletePlan;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackDeleteFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackDialogs;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackGroupCell;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackImportFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackLinkActions;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackOrderFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackOrderModel;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackPendingActionModel;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackReplacementConfirmModel;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowState;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowView;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackShell;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackToggleFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackVersionFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackVersionSelectionModel;
import com.cozooo.dlc_manager.ui.remote.DlcFxServerPackListModel;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DlcFxServerPackTab {
    private static final Gson GSON = new Gson();

    private final DlcFxServerConnection conn;
    private final ObservableList<DlcFxServerPackListModel.Group> groups = FXCollections.observableArrayList();
    private final Map<String, String> selectedVersionByIdentifier = new LinkedHashMap<>();
    private final DlcFxPackPendingActionModel<DlcFxServerPackListModel.Entry> pendingModel =
        new DlcFxPackPendingActionModel<>(DlcFxServerPackListModel.Entry::identifier, DlcFxServerPackListModel.Entry::version);
    private final DlcFxPackVersionSelectionModel<DlcFxServerPackListModel.Entry> versionSelectionModel =
        new DlcFxPackVersionSelectionModel<>(selectedVersionByIdentifier, pendingModel, DlcFxServerPackListModel.Entry::identifier, DlcFxServerPackListModel.Entry::orderKey);
    private final DlcFxPackApplyFlow applyFlow;
    private final DlcFxPackDeleteFlow<DlcFxServerPackListModel.Group> deleteFlow;
    private final DlcFxPackToggleFlow<DlcFxServerPackListModel.Group, DlcFxServerPackListModel.Entry> toggleFlow;
    private final DlcFxPackVersionFlow<DlcFxServerPackListModel.Group, DlcFxServerPackListModel.Entry> versionFlow;
    private final DlcFxPackOrderFlow<DlcFxServerPackListModel.Group> orderFlow;
    private final DlcFxPackImportFlow importFlow;
    private final Label countLabel = new Label("");
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label operationStatusLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.refresh"));
    private final Button importBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.import"));
    private final Button importUrlBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.import_url"));
    private final Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.apply"));
    private final Button cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.cancel_changes"));
    private DlcFxPackShell<DlcFxServerPackListModel.Group> shell;
    private ListView<DlcFxServerPackListModel.Group> listView;
    private final DlcFxPackOrderModel<DlcFxServerPackListModel.Group> orderModel =
        new DlcFxPackOrderModel<>(DlcFxServerPackListModel.Group::orderKey);
    private boolean busy;
    private boolean cancelRequested;
    private boolean paused;
    private boolean loadedForCurrentConnection;

    public DlcFxServerPackTab(DlcFxServerConnection conn) {
        this.conn = conn;
        this.applyFlow = new DlcFxPackApplyFlow(new RemotePackApplyActions());
        this.deleteFlow = new DlcFxPackDeleteFlow<>(new RemotePackDeleteActions());
        this.toggleFlow = new DlcFxPackToggleFlow<>(new RemotePackToggleActions());
        this.versionFlow = new DlcFxPackVersionFlow<>(new RemotePackVersionActions());
        this.orderFlow = new DlcFxPackOrderFlow<>(new RemotePackOrderActions());
        this.importFlow = new DlcFxPackImportFlow(new RemotePackImportActions());
        progressBar.setVisible(false);
        progressBar.setPrefWidth(220);
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        refreshBtn.setOnAction(e -> importFlow.refresh());
        importBtn.setOnAction(e -> importFlow.importArchive());
        importUrlBtn.setOnAction(e -> importFlow.importUrl());
        applyBtn.setOnAction(e -> {
            if (busy && paused) {
                resumeApply();
            } else if (busy) {
                pauseApply();
            } else {
                applyPendingChanges();
            }
        });
        cancelBtn.setOnAction(e -> onCancel());
        shell = new DlcFxPackShell<>(
            groups,
            countLabel,
            statusLabel,
            operationStatusLabel,
            progressBar,
            refreshBtn,
            importBtn,
            importUrlBtn,
            applyBtn,
            cancelBtn
        );
        listView = shell.listView();
        listView.setCellFactory(param -> new DlcFxPackGroupCell<>(new PackCellAdapter()));
        conn.connectedProperty().addListener((obs, oldValue, connected) -> updateActionButtons());
        updateActionButtons();
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.pack"), shell.root());
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            refreshList();
        }
    }

    private void refreshList() {
        if (!conn.connectedProperty().get()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.disconnected"));
            updateActionButtons();
            return;
        }
        setBusy(true, DlcFxI18n.tr("dlc_manager.status.loading"));
        conn.runAsync(f -> f.packList(), response -> {
            rebuildEntries(parsePacks(response), parseOrder(response));
            countLabel.setText(DlcFxI18n.tr("dlc_manager.pack.count", groups.size()));
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.refresh_done"));
            setBusy(false, null);
            loadedForCurrentConnection = true;
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            setBusy(false, null);
        });
    }

    private void rebuildEntries(List<DlcServerPackInfo> packs, List<String> order) {
        List<DlcFxServerPackListModel.Group> next = DlcFxServerPackListModel.groups(packs, order);
        List<DlcFxServerPackListModel.Group> normalized = new ArrayList<>();
        for (DlcFxServerPackListModel.Group group : next) {
            normalized.add(resolveSelectedVersion(group));
        }
        orderModel.reset(DlcFxServerPackListModel.normalizedOrder(order, normalized));
        groups.setAll(sortByCurrentOrder(normalized));
        pendingModel.pendingInstalls().keySet().removeIf(identifier -> findGroup(identifier) == null);
        pendingModel.pendingUninstallIds().removeIf(identifier -> findGroup(identifier) == null);
        updateActionButtons();
    }

    private DlcFxServerPackListModel.Group resolveSelectedVersion(DlcFxServerPackListModel.Group group) {
        String selected = selectedVersionByIdentifier.get(group.identifier());
        if (selected == null) {
            return group;
        }
        for (DlcFxServerPackListModel.Entry entry : group.versions()) {
            if (entry.orderKey().equals(selected) || entry.version().equals(selected)) {
                return group.withSelectedVersionKey(entry.orderKey());
            }
        }
        return group;
    }

    private List<DlcFxServerPackListModel.Group> sortByCurrentOrder(List<DlcFxServerPackListModel.Group> source) {
        return source.stream()
            .sorted((left, right) -> Integer.compare(orderIndex(left.orderKey()), orderIndex(right.orderKey())))
            .toList();
    }

    private int orderIndex(String key) {
        int index = orderModel.currentOrder().indexOf(key);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private void moveGroup(DlcFxServerPackListModel.Group group, int delta) {
        orderFlow.move(group, delta);
    }

    private void selectVersion(DlcFxServerPackListModel.Group group, DlcFxServerPackListModel.Entry entry) {
        versionFlow.select(group, entry);
    }

    private void toggleInstall(DlcFxServerPackListModel.Group group) {
        toggleFlow.toggle(group);
    }

    private void confirmDeleteArchive(DlcFxServerPackListModel.Group group) {
        deleteFlow.confirmDelete(group);
    }

    private void startDeleteArchive(DlcFxServerPackListModel.Entry entry, DlcFxPackDeletePlan plan) {
        setBusy(true, plan.busyStatus());
        conn.runAsync(f -> f.packDelete(entry.archivePath()), response -> {
            statusLabel.setText(plan.successMessage());
            setBusy(false, null);
            refreshList();
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.delete_failed", DlcFxServerConnection.safeMessage(err)));
            setBusy(false, null);
        });
    }

    private void applyPendingChanges() {
        applyFlow.start();
    }

    private void commitCurrentOrderOnly() {
        setBusy(true, DlcFxI18n.tr("dlc_manager.pack.applying"));
        conn.runAsync(f -> f.packOrder(orderModel.currentOrder()), response -> {
            orderModel.commit();
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.apply_success"));
            setBusy(false, null);
            refreshRows();
            updateActionButtons();
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.apply_failed", DlcFxServerConnection.safeMessage(err)));
            setBusy(false, null);
        });
    }

    private void startApplyPendingChanges(boolean allowRuntimeChanges) {
        List<String> uninstall = new ArrayList<>(pendingModel.pendingUninstallIds());
        List<String> install = pendingModel.pendingInstalls().values().stream().map(DlcFxServerPackListModel.Entry::archivePath).toList();
        setBusy(true, DlcFxI18n.tr("dlc_manager.pack.applying"));
        conn.runAsync(f -> f.packApplyStart(orderModel.currentOrder(), uninstall, install), response -> {
            applyStatus(parseStatus(response));
            pollApplyStatus();
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.apply_failed", DlcFxServerConnection.safeMessage(err)));
            setBusy(false, null);
        });
    }

    private String replacementConfirmText() {
        return DlcFxPackReplacementConfirmModel.message(
            DlcFxPackReplacementConfirmModel.lines(
                displayEntries(),
                entry -> pendingModel.pendingInstalls().get(entry.identifier()) == entry,
                entry -> {
                    DlcFxServerPackListModel.Group group = findGroup(entry.identifier());
                    DlcFxServerPackListModel.Entry installed = installedEntry(group);
                    return installed == null ? null : installed.version();
                },
                DlcFxServerPackListModel.Entry::version,
                this::displayName
            ),
            DlcFxI18n::tr
        );
    }

    private String runtimeConfirmText() {
        return "";
    }

    private void pollApplyStatus() {
        Thread thread = new Thread(() -> {
            while (busy) {
                try {
                    var facade = conn.facade();
                    if (facade == null) {
                        break;
                    }
                    DlcServerPackApplyStatus status = parseStatus(facade.packApplyStatus());
                    Platform.runLater(() -> applyStatus(status));
                    if (status == null || status.finished || status.canceled) {
                        break;
                    }
                    Thread.sleep(300L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    Platform.runLater(() -> {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.apply_failed", DlcFxServerConnection.safeMessage(t)));
                        setBusy(false, null);
                    });
                    break;
                }
            }
        }, "dlc-fx-remote-pack-apply-status");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyStatus(DlcServerPackApplyStatus status) {
        if (status == null) {
            return;
        }
        progressBar.setVisible(true);
        progressBar.setProgress(status.progress < 0 ? -1 : status.progress);
        if (status.finished || status.canceled) {
            pendingModel.clear();
            setBusy(false, null);
            statusLabel.setText(status.canceled
                ? DlcFxI18n.tr("dlc_manager.pack.canceled")
                : status.success ? DlcFxI18n.tr("dlc_manager.pack.apply_success") : DlcFxI18n.tr("dlc_manager.pack.apply_failed", status.error == null ? "" : status.error));
            refreshList();
        }
    }

    private void pauseApply() {
        paused = true;
        conn.runAsync(f -> f.packApplyPause(), this::applyStatusResponse, err ->
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
        updateBusyButtons();
    }

    private void resumeApply() {
        paused = false;
        conn.runAsync(f -> f.packApplyResume(), this::applyStatusResponse, err ->
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
        updateBusyButtons();
    }

    private void applyStatusResponse(JsonObject response) {
        applyStatus(parseStatus(response));
    }

    private void onCancel() {
        if (busy) {
            cancelRequested = true;
            conn.runAsync(f -> f.packApplyCancel(), this::applyStatusResponse, err ->
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
            updateBusyButtons();
            return;
        }
        if (hasPendingChanges()) {
            pendingModel.clear();
            orderModel.cancel();
            groups.setAll(sortByCurrentOrder(groups));
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.changes_cancelled"));
            refreshRows();
            updateActionButtons();
        }
    }

    private void showImportDialog() {
        TextField pathField = new TextField();
        pathField.setPromptText(DlcFxI18n.tr("dlc_manager.common.server_path"));
        pathField.setPrefWidth(460);
        Label error = new Label();
        DlcFxStyle.muted(error);
        VBox content = new VBox(10, pathField, error);
        DlcFxModalOverlay.show(scene(), DlcFxI18n.tr("dlc_manager.pack.import_title"), content, () -> {
            String path = pathField.getText() == null ? "" : pathField.getText().trim();
            if (path.isBlank()) {
                error.setText(DlcFxI18n.tr("dlc_manager.common.server_path_required"));
                return;
            }
            importPack(path);
        }, DlcFxI18n.tr("dlc_manager.pack.import"), () -> {}, DlcFxI18n.tr("dlc_manager.common.cancel"),
            button -> button.disableProperty().bind(pathField.textProperty().isEmpty()));
    }

    private void importPack(String path) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.importing"));
        conn.runAsync(f -> f.packImport(path), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", response.toString()));
            setBusy(false, null);
            refreshList();
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            setBusy(false, null);
        });
    }

    private void showImportUrlDialog(DlcFxPackImportFlow.UrlImportPrompt prompt) {
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
        DlcFxModalOverlay.show(scene(), DlcFxI18n.tr("dlc_manager.pack.import_url_title"), content, () -> {
            String url = urlField.getText() == null ? "" : urlField.getText().trim();
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (url.isBlank()) {
                error.setText(DlcFxI18n.tr("dlc_manager.common.url_required"));
                return;
            }
            prompt.submit(url, name);
        }, DlcFxI18n.tr("dlc_manager.update.url_download"), () -> {}, DlcFxI18n.tr("dlc_manager.common.cancel"),
            button -> button.disableProperty().bind(urlField.textProperty().isEmpty()));
    }

    private void importUrl(String url, String name) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.downloading_importing"));
        progressBar.setProgress(-1);
        conn.runAsync(f -> f.packImportUrl(url, name), response -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.result_value", response.toString()));
            setBusy(false, null);
            refreshList();
        }, err -> {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            setBusy(false, null);
        });
    }

    private final class RemotePackImportActions implements DlcFxPackImportFlow.Actions {
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
            refreshList();
        }

        @Override
        public void importArchive() {
            showImportDialog();
        }

        @Override
        public void promptUrlImport(DlcFxPackImportFlow.UrlImportPrompt prompt) {
            showImportUrlDialog(prompt);
        }

        @Override
        public void emptyUrl() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.url_required"));
        }

        @Override
        public void importUrl(String url, String fileName) {
            importUrl(url, fileName);
        }
    }

    private void setBusy(boolean value, String status) {
        busy = value;
        if (status != null) {
            statusLabel.setText(status);
        }
        progressBar.setVisible(value);
        if (value) {
            progressBar.setProgress(-1);
            updateBusyButtons();
        } else {
            cancelRequested = false;
            paused = false;
            progressBar.setProgress(0);
            updateActionButtons();
        }
    }

    private void updateBusyButtons() {
        if (!busy) {
            updateActionButtons();
            return;
        }
        applyPackActionState(DlcFxPackActionState.computeBusy(true, paused, cancelRequested, false, false));
    }

    private void updateActionButtons() {
        if (busy) {
            updateBusyButtons();
            return;
        }
        applyPackActionState(DlcFxPackActionState.compute(conn.connectedProperty().get(), false, hasPendingChanges(), false));
    }

    private void applyPackActionState(DlcFxPackActionState state) {
        if (shell != null) {
            shell.applyActionState(state);
        }
        applyBtn.setText(DlcFxI18n.tr(state.applyTextKey()));
        cancelBtn.setText(DlcFxI18n.tr(state.cancelTextKey()));
    }

    private boolean hasPendingChanges() {
        return hasPackOperationChanges() || orderModel.hasOrderChanges();
    }

    private boolean hasPackOperationChanges() {
        return pendingModel.hasOperationChanges();
    }

    private List<DlcFxServerPackListModel.Entry> displayEntries() {
        List<DlcFxServerPackListModel.Entry> out = new ArrayList<>();
        for (DlcFxServerPackListModel.Group group : groups) {
            if (group != null && group.displayEntry() != null) {
                out.add(group.displayEntry());
            }
        }
        return out;
    }

    private DlcFxServerPackListModel.Group findGroup(String identifier) {
        for (DlcFxServerPackListModel.Group group : groups) {
            if (group.identifier().equals(identifier)) {
                return group;
            }
        }
        return null;
    }

    private DlcFxServerPackListModel.Entry installedEntry(DlcFxServerPackListModel.Group group) {
        if (group == null) {
            return null;
        }
        return group.versions().stream().filter(DlcFxServerPackListModel.Entry::installed).findFirst().orElse(null);
    }

    private String actionText(DlcFxServerPackListModel.Group group) {
        DlcFxServerPackListModel.Entry entry = group.displayEntry();
        DlcFxServerPackListModel.Entry installed = installedEntry(group);
        DlcFxPackPendingActionModel.Action action = pendingModel.action(entry, installed);
        return DlcFxI18n.tr(DlcFxPackPendingActionModel.translationKey(action));
    }

    private void refreshRows() {
        if (listView != null) {
            listView.refresh();
        }
    }

    private Scene scene() {
        return shell == null ? null : shell.root().getScene();
    }

    private List<DlcServerPackInfo> parsePacks(JsonObject response) {
        List<DlcServerPackInfo> out = new ArrayList<>();
        JsonArray array = response == null || !response.has("packs") ? null : response.getAsJsonArray("packs");
        if (array == null) {
            return out;
        }
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                DlcServerPackInfo info = GSON.fromJson(element, DlcServerPackInfo.class);
                if (info != null) {
                    out.add(info);
                }
            }
        }
        return out;
    }

    private List<String> parseOrder(JsonObject response) {
        List<String> out = new ArrayList<>();
        JsonArray array = response == null || !response.has("order") ? null : response.getAsJsonArray("order");
        if (array == null) {
            return out;
        }
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                out.add(element.getAsString());
            }
        }
        return out;
    }

    private DlcServerPackApplyStatus parseStatus(JsonObject response) {
        if (response == null) {
            return null;
        }
        JsonElement status = response.get("status");
        if (status != null && status.isJsonObject()) {
            return GSON.fromJson(status, DlcServerPackApplyStatus.class);
        }
        return GSON.fromJson(response, DlcServerPackApplyStatus.class);
    }

    private Image decodeIcon(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String displayName(DlcFxServerPackListModel.Entry entry) {
        DlcServerPackInfo info = entry == null ? null : entry.info();
        String value = info == null ? null : info.displayName;
        if (value == null || value.isBlank()) {
            value = entry == null ? null : entry.identifier();
        }
        return value == null || value.isBlank() ? DlcFxI18n.tr("dlc_manager.status.unnamed") : value;
    }

    private String authorText(DlcFxServerPackListModel.Entry entry) {
        DlcServerPackInfo info = entry == null ? null : entry.info();
        List<String> authors = info == null || info.authors == null ? List.of() : info.authors.stream().filter(v -> v != null && !v.isBlank()).toList();
        String value = authors.isEmpty() ? DlcFxI18n.tr("dlc_manager.pack.unknown_author") : String.join(", ", authors);
        return DlcFxI18n.tr("dlc_manager.pack.author_prefix", value);
    }

    private boolean hasLink(DlcFxServerPackListModel.Entry entry, String key) {
        String value = link(entry, key);
        return value != null && !value.isBlank();
    }

    private String link(DlcFxServerPackListModel.Entry entry, String key) {
        DlcServerPackInfo info = entry == null ? null : entry.info();
        return info == null || info.links == null ? null : info.links.get(key);
    }

    private void confirmOpenLink(DlcFxServerPackListModel.Entry entry, String key) {
        String url = link(entry, key);
        if (url == null || url.isBlank()) {
            return;
        }
        DlcFxPackLinkActions.showOpenLinkConfirm(scene(), url, statusLabel::setText);
    }

    private final class RemotePackApplyActions implements DlcFxPackApplyFlow.Actions {
        @Override
        public boolean enabled() {
            return conn.connectedProperty().get();
        }

        @Override
        public boolean busy() {
            return busy;
        }

        @Override
        public boolean hasPendingChanges() {
            return DlcFxServerPackTab.this.hasPendingChanges();
        }

        @Override
        public boolean hasOperationChanges() {
            return hasPackOperationChanges();
        }

        @Override
        public String replacementConfirmText() {
            return shouldBuildConfirmText() ? replacementConfirmText() : null;
        }

        @Override
        public String runtimeConfirmText() {
            return shouldBuildConfirmText() ? runtimeConfirmText() : null;
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.disconnected"));
        }

        @Override
        public void commitOrderOnly() {
            commitCurrentOrderOnly();
        }

        @Override
        public void confirmReplacement(String message, Runnable confirm) {
            DlcFxPackDialogs.confirm(
                scene(),
                DlcFxI18n.tr("dlc_manager.pack.apply_confirm_title"),
                message,
                confirm
            );
        }

        @Override
        public void confirmRuntime(String message, boolean allowRuntimeChanges, Runnable confirm) {
            DlcFxPackDialogs.confirm(
                scene(),
                DlcFxI18n.tr("dlc_manager.pack.runtime_confirm_title"),
                message,
                confirm
            );
        }

        @Override
        public void startApply(boolean allowRuntimeChanges) {
            startApplyPendingChanges(allowRuntimeChanges);
        }

        private boolean shouldBuildConfirmText() {
            return enabled() && !busy() && hasPendingChanges() && hasOperationChanges();
        }
    }

    private final class RemotePackDeleteActions implements DlcFxPackDeleteFlow.Actions<DlcFxServerPackListModel.Group> {
        @Override
        public boolean enabled() {
            return conn.connectedProperty().get();
        }

        @Override
        public String displayName(DlcFxServerPackListModel.Group group) {
            DlcFxServerPackListModel.Entry entry = group == null ? null : group.displayEntry();
            return DlcFxServerPackTab.this.displayName(entry);
        }

        @Override
        public boolean installed(DlcFxServerPackListModel.Group group) {
            DlcFxServerPackListModel.Entry entry = group == null ? null : group.displayEntry();
            return entry != null && entry.installed();
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.disconnected"));
        }

        @Override
        public void confirm(DlcFxPackDeletePlan plan, Runnable onConfirm) {
            DlcFxPackDialogs.confirm(scene(), plan.title(), plan.confirmMessage(), onConfirm);
        }

        @Override
        public void delete(DlcFxServerPackListModel.Group group, DlcFxPackDeletePlan plan) {
            DlcFxServerPackListModel.Entry entry = group == null ? null : group.displayEntry();
            if (entry != null) {
                startDeleteArchive(entry, plan);
            }
        }
    }

    private final class RemotePackToggleActions implements DlcFxPackToggleFlow.Actions<DlcFxServerPackListModel.Group, DlcFxServerPackListModel.Entry> {
        @Override
        public boolean enabled() {
            return conn.connectedProperty().get();
        }

        @Override
        public DlcFxServerPackListModel.Entry displayEntry(DlcFxServerPackListModel.Group group) {
            return group == null ? null : group.displayEntry();
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.disconnected"));
        }

        @Override
        public void toggle(DlcFxServerPackListModel.Group group, DlcFxServerPackListModel.Entry entry) {
            pendingModel.toggle(entry, installedEntry(group));
        }

        @Override
        public void refresh() {
            refreshRows();
            updateActionButtons();
        }
    }

    private final class RemotePackVersionActions implements DlcFxPackVersionFlow.Actions<DlcFxServerPackListModel.Group, DlcFxServerPackListModel.Entry> {
        @Override
        public String groupIdentifier(DlcFxServerPackListModel.Group group) {
            return group.identifier();
        }

        @Override
        public String entryKey(DlcFxServerPackListModel.Entry entry) {
            return entry.orderKey();
        }

        @Override
        public void select(DlcFxServerPackListModel.Entry entry) {
            versionSelectionModel.select(entry);
        }

        @Override
        public void updateGroup(String identifier, String selectedVersionKey) {
            List<DlcFxServerPackListModel.Group> next = new ArrayList<>();
            for (DlcFxServerPackListModel.Group candidate : groups) {
                next.add(candidate.identifier().equals(identifier) ? candidate.withSelectedVersionKey(selectedVersionKey) : candidate);
            }
            groups.setAll(next);
        }

        @Override
        public void refresh() {
            updateActionButtons();
        }
    }

    private final class RemotePackOrderActions implements DlcFxPackOrderFlow.Actions<DlcFxServerPackListModel.Group> {
        @Override
        public List<DlcFxServerPackListModel.Group> items() {
            return List.copyOf(groups);
        }

        @Override
        public List<String> collectOrder(List<DlcFxServerPackListModel.Group> reordered) {
            return orderModel.collectOrder(reordered);
        }

        @Override
        public void apply(List<DlcFxServerPackListModel.Group> reordered,
                          DlcFxServerPackListModel.Group moved,
                          List<String> order) {
            orderModel.setCurrentOrder(order);
            groups.setAll(reordered);
            updateActionButtons();
        }
    }

    private final class PackCellAdapter implements DlcFxPackGroupCell.Adapter<DlcFxServerPackListModel.Group, DlcFxServerPackListModel.Entry> {
        @Override
        public DlcFxServerPackListModel.Entry displayEntry(DlcFxServerPackListModel.Group group) {
            return group == null ? null : group.displayEntry();
        }

        @Override
        public DlcFxPackRowState rowState(DlcFxServerPackListModel.Group group,
                                          DlcFxServerPackListModel.Entry entry,
                                          int rowIndex,
                                          int rowCount) {
            DlcServerPackInfo info = entry == null ? null : entry.info();
            return DlcFxPackRowState.of(
                rowIndex,
                rowCount,
                group.versions().size(),
                info == null ? null : info.links,
                entry != null && entry.installed()
            );
        }

        @Override
        public Node versionLine(DlcFxServerPackListModel.Group group) {
            DlcFxPackRowState rowState = rowState(group, group.displayEntry(), 0, 0);
            List<DlcFxPackRowView.VersionOption> options = new ArrayList<>();
            for (DlcFxServerPackListModel.Entry option : group.versions()) {
                options.add(new DlcFxPackRowView.VersionOption(
                    option.orderKey(),
                    versionOptionText(option),
                    option.orderKey().equals(group.displayEntry().orderKey())
                ));
            }
            return DlcFxPackRowView.versionLine(
                versionOptionText(group.displayEntry()),
                rowState.canSwitchVersion(),
                options,
                selected -> selectVersion(group, entryByKey(group, selected.key()))
            );
        }

        @Override
        public String title(DlcFxServerPackListModel.Entry entry) {
            return displayName(entry);
        }

        @Override
        public String author(DlcFxServerPackListModel.Entry entry) {
            return authorText(entry);
        }

        @Override
        public String description(DlcFxServerPackListModel.Entry entry) {
            return entry.info().description == null || entry.info().description.isBlank() ? entry.identifier() : entry.info().description;
        }

        @Override
        public Image icon(DlcFxServerPackListModel.Entry entry) {
            return decodeIcon(entry.info().iconBase64);
        }

        @Override
        public String iconFallback(DlcFxServerPackListModel.Entry entry) {
            return entry.identifier();
        }

        @Override
        public String actionText(DlcFxServerPackListModel.Group group) {
            return DlcFxServerPackTab.this.actionText(group);
        }

        @Override
        public void openHomepage(DlcFxServerPackListModel.Entry entry) {
            confirmOpenLink(entry, "homepage");
        }

        @Override
        public void moveUp(DlcFxServerPackListModel.Group group) {
            moveGroup(group, -1);
        }

        @Override
        public void moveDown(DlcFxServerPackListModel.Group group) {
            moveGroup(group, 1);
        }

        @Override
        public void toggleInstall(DlcFxServerPackListModel.Group group) {
            DlcFxServerPackTab.this.toggleInstall(group);
        }

        @Override
        public void deleteArchive(DlcFxServerPackListModel.Group group) {
            confirmDeleteArchive(group);
        }

        @Override
        public void openIssue(DlcFxServerPackListModel.Entry entry) {
            confirmOpenLink(entry, "issue");
        }

        @Override
        public void openSponsor(DlcFxServerPackListModel.Entry entry) {
            confirmOpenLink(entry, "sponsor");
        }

        private DlcFxServerPackListModel.Entry entryByKey(DlcFxServerPackListModel.Group group, String key) {
            if (group != null) {
                for (DlcFxServerPackListModel.Entry entry : group.versions()) {
                    if (entry != null && entry.orderKey().equals(key)) {
                        return entry;
                    }
                }
            }
            return group == null ? null : group.displayEntry();
        }

        private String versionOptionText(DlcFxServerPackListModel.Entry entry) {
            return entry.installed() ? DlcFxI18n.tr("dlc_manager.pack.version_current_short", entry.version()) : entry.version();
        }
    }
}
