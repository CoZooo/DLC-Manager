package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.*;
import com.cozooo.dlc_manager.client.sync.DlcSyncAdapter;
import com.cozooo.dlc_manager.sync.*;
import com.cozooo.dlc_manager.sync.SyncModel.*;
import com.cozooo.dlc_manager.ui.common.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.*;

public final class DlcFxServerSyncTab {
    private final DlcFxServerConnection connection;
    private final Supplier<String> gameDir;
    private final BooleanSupplier selectedGame;
    private final Runnable chooseDirectory, readLocal;
    private final Label directory = new Label(), status = new Label();
    private final ComboBox<String> mode = new ComboBox<>(), direction = new ComboBox<>();
    private final CheckBox settings = new CheckBox(tr("manager_config"));
    private final CheckBox states = new CheckBox(tr("states")), selectAll = new CheckBox(tr("select_all"));
    private final HBox selectionHeader = DlcFxActionPanel.row(selectAll);
    private final Button compare = new Button(tr("compare")), execute = new Button(tr("execute"));
    private final Button rollback = new Button(tr("rollback"));
    private volatile SyncClient rollbackClient;
    private DlcServerEntry rollbackEntry;
    private volatile String failureReason;
    private final ProgressBar progress = new ProgressBar(-1);
    private final ListView<Row> list = new ListView<>();
    private final Set<String> selected = new HashSet<>();
    private List<Item> local = List.of(), remote = List.of();
    private List<Row> rows = List.of();
    private volatile SyncClient active;
    private volatile AutoCloseable connecting;
    private volatile boolean cancelRequested;
    private volatile DlcServerSession previewSession;
    private boolean busy, syncing, loaded;
    private Category category = Category.ALL;
    private final VBox types = new VBox(5);

    public DlcFxServerSyncTab(DlcFxServerConnection connection, Supplier<String> gameDir, BooleanSupplier selectedGame, Runnable chooseDirectory, Runnable readLocal) {
        this.connection = connection;
        this.gameDir = gameDir;
        this.selectedGame = selectedGame;
        this.chooseDirectory = chooseDirectory;
        this.readLocal = readLocal;
    }
    private static String tr(String key, Object... args) { return DlcFxI18n.tr("dlc_manager.sync." + key, args); }
    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage("");
        mode.getItems().setAll(tr("mode_config"), tr("mode_files")); mode.getSelectionModel().select(0);
        states.setSelected(true);
        direction.getItems().setAll(tr("push"), tr("pull")); direction.getSelectionModel().select(0);
        mode.setOnAction(e -> replan()); direction.setOnAction(e -> replan()); states.setOnAction(e -> replan());
        Button choose = new Button(DlcFxI18n.tr("dlc_manager.local.choose_game_dir")); choose.setOnAction(e -> { if (!busy) chooseDirectory.run(); });
        Button read = new Button(DlcFxI18n.tr("dlc_manager.server.read_local_files")); read.setOnAction(e -> { if (!busy) readLocal.run(); });
        page.setTop(new VBox(10, DlcFxToolbar.create(directory, DlcFxToolbar.spacer(), choose, read), DlcFxToolbar.create(direction, DlcFxToolbar.spacer(), compare)));
        types.getStyleClass().add("dlc-config-tabs"); types.setPrefWidth(172);
        for (Category type : Category.values()) {
            if (type == Category.MANAGER_CONFIG) continue;
            Button button = new Button(tr("type_" + type.name().toLowerCase(Locale.ROOT)));
            button.setMaxWidth(Double.MAX_VALUE); button.setUserData(type); button.getStyleClass().add("dlc-config-tab-button");
            button.setOnAction(event -> { if (!busy) { category = type; list.getItems().setAll(visibleRows()); refreshState(); } });
            types.getChildren().add(button);
        }
        page.root().setLeft(types);
        list.setPlaceholder(new Label(tr("differences")));
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) { setGraphic(null); return; }
                CheckBox check = new CheckBox(SyncModel.displayName(row.source(), com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode()));
                check.setSelected(selected.contains(row.key())); check.setDisable(busy);
                Runnable change = () -> { if (!selected.add(row.key())) selected.remove(row.key()); refreshState(); };
                check.setOnAction(e -> change.run());
                HBox.setHgrow(check, Priority.ALWAYS); check.setMaxWidth(Double.MAX_VALUE);
                Label difference = new Label(tr(SyncModel.status(row, mode.getSelectionModel().getSelectedIndex() == 0 ? Mode.CONFIG : Mode.FILES)));
                DlcFxStyle.muted(difference);
                HBox line = new HBox(12, check, difference); line.setAlignment(Pos.CENTER_LEFT);
                selectOnRow(line, check, change);
                String language = com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode();
                DlcFxTooltips.install(line, DlcFxTooltips.formatted(SyncModel.displayName(row.source(), language) + "\n" + SyncModel.tooltip(row.source(), language) + "\n" + difference.getText()));
                setGraphic(line);
            }
        });
        status.setWrapText(true);
        progress.setMaxWidth(Double.MAX_VALUE);
        Runnable selectEverything = () -> {
            List<Row> shown = visibleRows(); boolean all = shown.stream().allMatch(row -> selected.contains(row.key()));
            for (Row row : shown) { if (all) selected.remove(row.key()); else selected.add(row.key()); }
            refreshState();
        };
        selectAll.setOnAction(e -> selectEverything.run()); selectAll.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(selectAll, Priority.ALWAYS);
        selectOnRow(selectionHeader, selectAll, selectEverything);
        VBox body = new VBox(12, selectionHeader, list, status, progress); VBox.setVgrow(list, Priority.ALWAYS); page.setCenter(page.section(body));
        page.setBottom(DlcFxActionPanel.panel(DlcFxActionPanel.row(mode, states, settings, DlcFxActionPanel.spacer(), rollback, execute)));
        settings.setOnAction(e -> refreshState());
        compare.setOnAction(e -> compare()); execute.setOnAction(e -> { if (syncing) cancel(); else confirm(); });
        rollback.setOnAction(e -> rollback()); DlcFxStyle.danger(rollback);
        DlcFxStyle.primary(execute); refreshState();
        return DlcFxTabFactory.create(tr("title"), page.root());
    }
    private void selectOnRow(HBox line, CheckBox checkbox, Runnable change) {
        line.setOnMousePressed(event -> {
            if (busy || event.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (event.getTarget() instanceof javafx.scene.Node node) {
                for (javafx.scene.Node parent = node; parent != null; parent = parent.getParent()) if (parent == checkbox) return;
            }
            change.run(); event.consume();
        });
    }
    private Peer peer(DlcServerSession session) {
        return new Peer() {
            public com.google.gson.JsonObject call(com.google.gson.JsonObject request) throws java.io.IOException {
                try { return session.syncRequest(request); } catch (java.io.IOException error) { session.close(); throw error; }
            }
            public void close() throws java.io.IOException { session.close(); }
        };
    }
    private Peer control(DlcServerEntry entry) { return request -> { try (DlcServerSession session = DlcServerSession.connect(entry, 10000)) { return peer(session).call(request); } }; }
    private void compare() {
        if (busy) return;
        loaded = false; local = List.of(); remote = List.of(); rows = List.of(); selected.clear(); list.getItems().clear();
        DlcServerEntry entry = connection.currentEntry();
        run(() -> {
            List<Item> left, right;
            try (SyncEndpoint endpoint = new SyncEndpoint(new DlcSyncAdapter()); DlcServerSession session = connect(entry)) {
                previewSession = session;
                left = SyncClient.manifest(endpoint, () -> cancelRequested);
                right = SyncClient.manifest(peer(session), () -> cancelRequested);
            } finally { previewSession = null; }
            Platform.runLater(() -> { local = left; remote = right; loaded = true; status.setText(""); replan(); });
        });
    }
    private void replan() {
        rows = SyncModel.plan(local, remote, direction.getSelectionModel().getSelectedIndex() == 0 ? Direction.PUSH : Direction.PULL, states.isSelected(), mode.getSelectionModel().getSelectedIndex() == 0 ? Mode.CONFIG : Mode.FILES);
        selected.clear(); settings.setSelected(false);
        list.getItems().setAll(visibleRows()); refreshState();
    }
    private void confirm() {
        if (busy) return;
        List<Row> chosen = selectedRows();
        if (chosen.isEmpty()) return;
        DlcFxModalOverlay.show(execute.getScene(), tr("title"), tr("confirm", chosen.size()), () -> execute(chosen), DlcFxI18n.tr("dlc_manager.confirm_yes"), () -> {}, DlcFxI18n.tr("dlc_manager.confirm_no"));
    }
    private void execute(List<Row> chosen) {
        DlcServerEntry entry = connection.currentEntry();
        Mode selectedMode = mode.getSelectionModel().getSelectedIndex() == 0 ? Mode.CONFIG : Mode.FILES;
        boolean syncStates = states.isSelected();
        run(() -> {
            rollbackClient = null;
            try (SyncEndpoint endpoint = new SyncEndpoint(new DlcSyncAdapter()); DlcServerSession session = connect(entry)) {
                SyncClient client = new SyncClient(endpoint, peer(session), control(entry)); active = client; rollbackClient = client; rollbackEntry = entry;
                if (cancelRequested) client.cancel();
                Result result = client.execute(chosen, selectedMode, syncStates, (phase, resource, done, total) -> Platform.runLater(() -> { progress.setProgress(total > 0 && !phase.equals("restoring") ? done / (double) total : -1); String amount = phase.equals("transferring") || phase.equals("downloading") ? com.cozooo.dlc_manager.core.DlcFileSize.formatProgress(done, total) : done + (total >= 0 ? "/" + total : ""); status.setText(tr(phase) + " " + resource.substring(resource.lastIndexOf('/') + 1) + " " + amount); }));
                String message = tr(result.canceled() ? "canceled" : "finished", result.completed(), result.errors().size()) + (result.requiresRestart() ? " " + tr("restart") : "") + "\n" + String.join("\n", result.succeeded().stream().map(key -> tr("succeeded_item", key)).toList()) + "\n" + String.join("\n", result.errors());
                String updates = chosen.stream().anyMatch(row -> SyncModel.category(row.source()) == Category.UPDATE && result.succeeded().contains(row.key())) ? "\n" + tr("updates_ready") : "";
                if (!result.errors().isEmpty() && !result.canceled()) failureReason = String.join("\n", result.errors());
                if (result.canceled()) { Platform.runLater(() -> status.setText(message)); return; }
                List<Item> left = SyncClient.manifest(endpoint, () -> cancelRequested), right = SyncClient.manifest(peer(session), () -> cancelRequested);
                Platform.runLater(() -> { status.setText(message + updates); local = left; remote = right; replan(); });
            } finally { active = null; }
        }, true);
    }

    private void rollback() {
        SyncClient client = rollbackClient;
        if (busy || client == null || !client.canRollback()) return;
        DlcServerEntry entry = rollbackEntry;
        run(() -> {
            Platform.runLater(() -> status.setText(tr("restoring")));
            try (SyncEndpoint endpoint = new SyncEndpoint(new DlcSyncAdapter()); DlcServerSession session = connect(entry)) {
                client.rollback(endpoint, peer(session));
                List<Item> left = SyncClient.manifest(endpoint), right = SyncClient.manifest(peer(session));
                Platform.runLater(() -> { local = left; remote = right; status.setText(tr("restored")); replan(); });
            }
        });
    }
    private void showFailure(String reason) {
        Label message = new Label(tr("failure_choice", reason)); message.setWrapText(true); message.setPrefWidth(520);
        StackPane overlay = DlcFxModalOverlay.showAndGetOverlay(execute.getScene(), tr("failed"), message, () -> {}, tr("keep"), this::rollback, tr("rollback"), null);
        if (overlay != null) for (javafx.scene.Node node : overlay.lookupAll(".button")) {
            if (node instanceof Button button && button.getText().equals(tr("rollback"))) DlcFxStyle.danger(button);
        }
    }
    private DlcServerSession connect(DlcServerEntry entry) throws java.io.IOException {
        return DlcServerSession.connect(entry, 120000, socket -> {
            connecting = socket;
            if (cancelRequested) try { socket.close(); } catch (Exception ignored) {}
        });
    }
    private void cancel() {
        if (!busy || cancelRequested) return;
        cancelRequested = true;
        if (active != null) { if (!active.cancel()) { cancelRequested = false; return; } status.setText(tr("restoring")); progress.setProgress(-1); }
        else try { if (connecting != null) connecting.close(); } catch (Exception ignored) {}
        refreshState();
    }
    private Row settingsRow() { return rows.stream().filter(row -> SyncModel.category(row.source()) == Category.MANAGER_CONFIG).findFirst().orElse(null); }
    private List<Row> visibleRows() { return rows.stream().filter(row -> SyncModel.category(row.source()) != Category.MANAGER_CONFIG && (category == Category.ALL || SyncModel.category(row.source()) == category)).toList(); }
    private List<Row> selectedRows() {
        List<Row> chosen = new ArrayList<>(visibleRows().stream().filter(row -> selected.contains(row.key())).toList());
        if (settings.isSelected() && settingsRow() != null) chosen.add(settingsRow());
        return chosen;
    }
    private void refreshState() {
        directory.setText(gameDir.get());
        list.setVisible(!busy || syncing); list.setManaged(!busy || syncing);
        list.setPlaceholder(new Label(tr(loaded ? "empty" : "differences")));
        List<Row> shown = visibleRows(); long selectedCount = shown.stream().filter(row -> selected.contains(row.key())).count();
        for (javafx.scene.Node node : types.getChildren()) {
            node.setDisable(busy); node.getStyleClass().remove("selected");
            if (node.getUserData() == category) node.getStyleClass().add("selected");
        }
        boolean showSelection = (!busy || syncing) && !shown.isEmpty();
        selectionHeader.setVisible(showSelection); selectionHeader.setManaged(showSelection);
        selectAll.setDisable(busy || shown.isEmpty()); selectAll.setSelected(!shown.isEmpty() && selectedCount == shown.size());
        selectAll.setIndeterminate(selectedCount > 0 && selectedCount != shown.size());
        progress.setVisible(busy); progress.setManaged(busy);
        boolean ready = selectedGame.getAsBoolean() && connection.isConnected();
        compare.setDisable(busy || !ready);
        boolean canRollback = rollbackClient != null && rollbackClient.canRollback();
        rollback.setVisible(canRollback); rollback.setManaged(canRollback); rollback.setDisable(busy);
        execute.setText(tr(syncing ? "cancel" : "execute"));
        execute.getStyleClass().removeAll(DlcFxStyle.PRIMARY, DlcFxStyle.DANGER);
        if (syncing) DlcFxStyle.danger(execute); else DlcFxStyle.primary(execute);
        execute.setDisable(busy ? !syncing || cancelRequested : !ready || selectedRows().isEmpty());
        boolean stateDifferences = SyncModel.hasStateDifferences(local, remote); states.setVisible(stateDifferences); states.setManaged(stateDifferences);
        settings.setVisible(settingsRow() != null); settings.setManaged(settingsRow() != null); settings.setDisable(busy);
        mode.setDisable(busy); direction.setDisable(busy); states.setDisable(busy); list.refresh();
    }
    public boolean isBusy() { return busy; }
    public void onStorageContextChanged() { if (busy) cancel(); else { rollbackClient = null; rollbackEntry = null; loaded = false; local = List.of(); remote = List.of(); replan(); } }
    public void onTabSelected() { refreshState(); }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void run(Work work) { run(work, false); }
    private void run(Work work, boolean synchronizing) {
        if (busy) return;
        failureReason = null; cancelRequested = false; syncing = synchronizing; busy = true; progress.setProgress(-1);
        status.setText(tr("working")); refreshState();
        Thread thread = new Thread(() -> {
            try { work.run(); } catch (Exception error) { String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); if (synchronizing && !cancelRequested) failureReason = failureReason == null ? reason : failureReason + "\n" + reason; Platform.runLater(() -> status.setText(cancelRequested && (error instanceof java.net.SocketException || SyncFailure.is(error, "canceled")) ? tr("canceled") : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())); }
            finally { Platform.runLater(() -> { busy = false; syncing = false; connecting = null; refreshState(); if (failureReason != null) showFailure(failureReason); }); }
        }, "dlc-sync");
        thread.setDaemon(true); thread.start();
    }
}
