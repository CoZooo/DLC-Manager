package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Direction;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Row;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Status;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DlcFxServerSyncTab {
    private static final int SYNC_TIMEOUT_MS = 60_000;

    private final DlcFxServerConnection conn;
    private final Supplier<String> gameDirTextSupplier;
    private final BooleanSupplier gameDirSelectedSupplier;
    private final ObservableValue<String> serverPortTextProperty;
    private final Runnable chooseGameDirAction;
    private final BiConsumer<Runnable, Runnable> readLocalFilesAction;
    private final Label gameDirLabel = new Label();
    private final Label portLabel = new Label();
    private final Label connectionLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label dlcStatusLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_dlc_status_empty"));
    private final Label previewSummaryLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_preview_empty"));
    private final Label configSummaryLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_config_empty"));
    private final Label modeNoticeLabel = new Label("");
    private final Button readLocalButton = new Button(DlcFxI18n.tr("dlc_manager.server.read_local_files"));
    private final Button chooseDirButton = new Button(DlcFxI18n.tr("dlc_manager.local.choose_game_dir"));
    private final Button refreshDlcButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_refresh_dlc"));
    private final Button previewButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_preview"));
    private final Button applyButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_apply"));
    private final Button cancelButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_cancel"));
    private final Button readServerConfigButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_config_read_server"));
    private final Button syncConfigButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_config_apply"));
    private final ComboBox<DirectionOption> directionBox = new ComboBox<>();
    private final CheckBox deepVerifyCheck = new CheckBox(DlcFxI18n.tr("dlc_manager.server.sync_deep_verify"));
    private final ObservableList<Row> previewRows = FXCollections.observableArrayList();
    private final ListView<Row> previewList = new ListView<>(previewRows);
    private final Set<String> selectedKeys = new HashSet<>();
    private final ObservableList<DlcFxServerConfigSyncPlanner.Row> configRows = FXCollections.observableArrayList();
    private final ListView<DlcFxServerConfigSyncPlanner.Row> configList = new ListView<>(configRows);

    private DlcFxServerSyncConfig config;
    private DlcFxDlcSyncSnapshot lastSnapshot;
    private DlcFxDlcSyncPlanner.Plan lastPlan;
    private JsonObject localConfigSnapshot;
    private JsonObject serverConfigSnapshot;
    private DlcFxServerConfigSyncPlanner.Preview lastConfigPreview;
    private DlcFxDlcSyncOperation activeOperation;
    private volatile boolean previewRunning;
    private volatile boolean applyRunning;
    private volatile boolean configSyncRunning;
    private boolean configReadRunning;
    private long contextVersion;
    private boolean localReadRunning;
    private boolean localFilesLoaded;

    public DlcFxServerSyncTab(DlcFxServerConnection conn,
                              Supplier<String> gameDirTextSupplier,
                              BooleanSupplier gameDirSelectedSupplier,
                              ObservableValue<String> serverPortTextProperty,
                              Runnable chooseGameDirAction,
                              BiConsumer<Runnable, Runnable> readLocalFilesAction) {
        this.conn = conn;
        this.gameDirTextSupplier = gameDirTextSupplier == null ? () -> "" : gameDirTextSupplier;
        this.gameDirSelectedSupplier = gameDirSelectedSupplier == null ? () -> false : gameDirSelectedSupplier;
        this.serverPortTextProperty = serverPortTextProperty;
        this.chooseGameDirAction = chooseGameDirAction == null ? () -> {} : chooseGameDirAction;
        this.readLocalFilesAction = readLocalFilesAction == null ? (onSuccess, onFailure) -> onSuccess.run() : readLocalFilesAction;
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage("");
        config = DlcFxServerSyncConfig.load();

        chooseDirButton.setOnAction(e -> chooseGameDirAction.run());
        DlcFxStyle.primary(chooseDirButton);
        readLocalButton.setOnAction(e -> {
            if (localReadRunning || isBusy()) {
                return;
            }
            invalidateSyncState();
            localReadRunning = true;
            long requestVersion = contextVersion;
            refreshState();
            readLocalFilesAction.accept(() -> {
                if (requestVersion != contextVersion) {
                    return;
                }
                localReadRunning = false;
                localFilesLoaded = true;
                readLocalConfig();
            }, () -> {
                if (requestVersion != contextVersion) {
                    return;
                }
                localReadRunning = false;
                refreshState();
            });
        });
        refreshDlcButton.setOnAction(e -> refreshDlcStatus());
        previewButton.setOnAction(e -> previewSync());
        applyButton.setOnAction(e -> confirmApply());
        cancelButton.setOnAction(e -> {
            if (activeOperation != null) {
                activeOperation.cancel();
            }
        });
        readServerConfigButton.setOnAction(e -> readServerConfig());
        syncConfigButton.setOnAction(e -> confirmSyncConfig());
        DlcFxStyle.primary(applyButton);
        DlcFxStyle.danger(cancelButton);
        DlcFxStyle.primary(syncConfigButton);

        directionBox.getItems().setAll(
                new DirectionOption(Direction.PUSH, DlcFxI18n.tr("dlc_manager.server.sync_direction_push")),
                new DirectionOption(Direction.PULL, DlcFxI18n.tr("dlc_manager.server.sync_direction_pull")));
        directionBox.getSelectionModel().select(directionOption(config.syncDirection()));
        directionBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            Direction direction = newValue == null ? Direction.PUSH : newValue.direction();
            config.setSyncDirection(direction);
            saveConfig();
            replan();
        });
        deepVerifyCheck.setSelected(config.deepVerify());
        deepVerifyCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            config.setDeepVerify(newValue);
            saveConfig();
        });

        previewList.setCellFactory(list -> new PreviewRowCell());
        configList.setCellFactory(list -> new ConfigRowCell());
        VBox.setVgrow(previewList, Priority.ALWAYS);
        configList.setPrefHeight(190);
        DlcFxStyle.muted(connectionLabel);
        DlcFxStyle.muted(dlcStatusLabel);
        DlcFxStyle.muted(previewSummaryLabel);
        DlcFxStyle.muted(configSummaryLabel);
        DlcFxStyle.muted(modeNoticeLabel);

        Node top = DlcFxToolbar.vertical(
                DlcFxToolbar.create(
                        new Label(DlcFxI18n.tr("dlc_manager.server.local_source")),
                        gameDirLabel,
                        DlcFxToolbar.spacer(),
                        chooseDirButton,
                        readLocalButton
                ),
                DlcFxToolbar.create(
                        new Label(DlcFxI18n.tr("dlc_manager.server.sync_connection")),
                        connectionLabel,
                        new Label(DlcFxI18n.tr("dlc_manager.server.sync_port")),
                        portLabel,
                        DlcFxToolbar.spacer(),
                        refreshDlcButton
                )
        );
        page.setTop(top);

        HBox optionRow = new HBox(8, new Label(DlcFxI18n.tr("dlc_manager.server.sync_direction")), directionBox, deepVerifyCheck);
        optionRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(8, previewButton, applyButton, cancelButton, previewSummaryLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        HBox configActionRow = new HBox(8, readServerConfigButton, syncConfigButton, configSummaryLabel);
        configActionRow.setAlignment(Pos.CENTER_LEFT);

        Label configTitle = new Label(DlcFxI18n.tr("dlc_manager.server.sync_config_title"));
        DlcFxStyle.muted(configTitle);

        VBox center = new VBox(10,
                optionRow,
                modeNoticeLabel,
                dlcStatusLabel,
                actionRow,
                previewList,
                configTitle,
                configActionRow,
                configList);
        center.setAlignment(Pos.TOP_LEFT);
        page.setCenter(page.section(center));

        if (conn != null) {
            conn.connectedProperty().addListener((obs, oldValue, newValue) -> {
                invalidateSyncState();
                refreshState();
            });
            conn.statusTextProperty().addListener((obs, oldValue, newValue) -> refreshState());
        }
        if (serverPortTextProperty != null) {
            serverPortTextProperty.addListener((obs, oldValue, newValue) -> refreshState());
        }
        refreshState();

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.sync"), page.root());
    }

    public void onStorageContextChanged() {
        reloadConfig();
        invalidateSyncState();
        refreshState();
    }

    public void onTabSelected() {
        reloadConfig();
        refreshState();
        if (conn != null && conn.isConnected()) {
            refreshDlcStatus();
        }
    }

    private void refreshState() {
        boolean selected = gameDirSelectedSupplier.getAsBoolean();
        boolean connected = conn != null && conn.isConnected();
        boolean dlcTcp = config == null || config.transportMode() == DlcFxServerSyncConfig.TransportMode.DLC_TCP;
        boolean busy = isBusy();
        gameDirLabel.setText(selected ? safeText(gameDirTextSupplier.get()) : DlcFxI18n.tr("dlc_manager.local.no_game_dir"));
        portLabel.setText(safeText(serverPortTextProperty == null ? "" : serverPortTextProperty.getValue()));
        connectionLabel.setText(conn == null ? DlcFxI18n.tr("dlc_manager.status.disconnected") : conn.statusTextProperty().get());
        readLocalButton.setDisable(!selected || localReadRunning || busy);
        chooseDirButton.setDisable(busy);
        refreshDlcButton.setDisable(!connected || busy);
        directionBox.setDisable(busy);
        deepVerifyCheck.setDisable(busy);
        previewButton.setDisable(!connected || !selected || !dlcTcp || localReadRunning || !localFilesLoaded || busy);
        applyButton.setDisable(!connected || !selected || !dlcTcp || !localFilesLoaded || lastPlan == null || selectedTransferCount() <= 0 || busy);
        cancelButton.setDisable(activeOperation == null);
        readServerConfigButton.setDisable(!connected || busy);
        syncConfigButton.setDisable(!connected || busy || localReadRunning || localConfigSnapshot == null || serverConfigSnapshot == null || lastConfigPreview == null || lastConfigPreview.changedCount() <= 0);
        modeNoticeLabel.setText(dlcTcp
                ? DlcFxI18n.tr("dlc_manager.server.sync_dlc_tcp_hint")
                : DlcFxI18n.tr("dlc_manager.server.sync_webrtc_disabled"));
        updateDlcStatusLabel(conn == null ? null : conn.lastServerInfo());
    }

    private void refreshDlcStatus() {
        if (conn == null || !conn.isConnected()) {
            refreshState();
            return;
        }
        long requestVersion = contextVersion;
        dlcStatusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        conn.runAsync(session -> session.fetchList(), info -> {
            if (!isCurrentContext(requestVersion)) {
                return;
            }
            conn.rememberServerInfo(info);
            updateDlcStatusLabel(info);
            refreshState();
        }, err -> {
            if (isCurrentContext(requestVersion)) {
                dlcStatusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            }
        });
    }

    private void previewSync() {
        if (isBusy()) {
            return;
        }
        if (config != null && config.transportMode() != DlcFxServerSyncConfig.TransportMode.DLC_TCP) {
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_webrtc_disabled"));
            return;
        }
        DlcServerEntry entry = conn == null || !conn.isConnected() ? null : conn.currentEntry();
        if (entry == null) {
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.not_connected"));
            return;
        }
        java.nio.file.Path localDlcRoot = DlcConfig.getDlcRoot();
        java.nio.file.Path localGameDir = DlcPaths.gameDir();
        boolean deepVerify = deepVerifyCheck.isSelected();
        String serverId = entry.host + ":" + entry.port;
        long requestVersion = contextVersion;
        previewRunning = true;
        lastSnapshot = null;
        lastPlan = null;
        previewRows.clear();
        selectedKeys.clear();
        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        refreshState();
        runDedicated(operation -> {
            try (DlcServerSession session = operation.connect(entry, SYNC_TIMEOUT_MS)) {
                String serverDlcRoot = DlcFxDlcSyncSnapshot.resolveServerDlcRoot(session.configGet());
                DlcFxLightHash.Cache cache = DlcFxLightHash.Cache.load(DlcFxDlcSyncSnapshot.cachePath(localDlcRoot));
                return DlcFxDlcSyncSnapshot.collect(session, localDlcRoot, localGameDir, serverDlcRoot,
                        serverId, cache, deepVerify, operation::isCanceled);
            }
        }, snapshot -> {
            if (!isCurrentContext(requestVersion)) {
                return;
            }
            previewRunning = false;
            lastSnapshot = snapshot;
            replan();
        }, err -> {
            if (!isCurrentContext(requestVersion)) {
                return;
            }
            previewRunning = false;
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            refreshState();
        });
    }

    private void replan() {
        if (lastSnapshot == null || previewRunning || applyRunning) {
            refreshState();
            return;
        }
        lastPlan = DlcFxDlcSyncPlanner.plan(lastSnapshot, selectedDirection());
        selectedKeys.clear();
        for (Row row : lastPlan.rows()) {
            if (row.status() == Status.TRANSFER) {
                selectedKeys.add(row.key());
            }
        }
        previewRows.setAll(lastPlan.rows());
        previewSummaryLabel.setText(summary(lastPlan));
        refreshState();
    }

    private void confirmApply() {
        if (isBusy()) {
            return;
        }
        List<Row> rows = selectedRows();
        if (rows.isEmpty()) {
            refreshState();
            return;
        }
        long bytes = 0L;
        int files = 0;
        for (Row row : rows) {
            bytes += row.transferBytes();
            files += row.transfers().size();
        }
        Direction direction = selectedDirection();
        DlcFxDlcSyncSnapshot snapshot = lastSnapshot;
        long requestVersion = contextVersion;
        String key = direction == Direction.PUSH
                ? "dlc_manager.server.sync_confirm_push"
                : "dlc_manager.server.sync_confirm_pull";
        DlcFxModalOverlay.show(
                applyButton.getScene(),
                DlcFxI18n.tr("dlc_manager.server.sync_confirm_title"),
                DlcFxI18n.tr(key, rows.size(), files, formatSize(bytes)),
                () -> {
                    if (isCurrentContext(requestVersion) && lastSnapshot == snapshot
                            && selectedDirection() == direction && !isBusy()) {
                        applySync(rows, snapshot, direction);
                    }
                },
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.confirm_no"));
    }

    private void applySync(List<Row> rows, DlcFxDlcSyncSnapshot snapshot, Direction direction) {
        DlcServerEntry entry = conn == null || !conn.isConnected() ? null : conn.currentEntry();
        if (entry == null || snapshot == null || rows.isEmpty()) {
            refreshState();
            return;
        }
        applyRunning = true;
        long requestVersion = contextVersion;
        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_applying"));
        refreshState();
        List<String> syncedKeys = rows.stream().map(Row::key).toList();
        runDedicated(operation -> {
            try (DlcServerSession session = operation.connect(entry, SYNC_TIMEOUT_MS)) {
                java.nio.file.Path cachePath = DlcFxDlcSyncSnapshot.cachePath(snapshot.localDlcRoot);
                DlcFxLightHash.Cache cache = DlcFxLightHash.Cache.load(cachePath);
                DlcFxDlcSyncExecutor.Result executed = DlcFxDlcSyncExecutor.execute(session, snapshot, direction, rows, cache, operation::isCanceled,
                        (done, total, rel) -> Platform.runLater(() -> {
                            if (isCurrentContext(requestVersion) && applyRunning) {
                                previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_progress", done, total, rel));
                            }
                        }));
                if (executed.canceled() || operation.isCanceled()) {
                    return new SyncOutcome(executed, List.of());
                }
                String currentRoot = DlcFxDlcSyncSnapshot.resolveServerDlcRoot(session.configGet());
                if (!snapshot.serverDlcRoot.equals(currentRoot)) {
                    throw new java.io.IOException("server DLC root changed during synchronization; preview again");
                }
                Platform.runLater(() -> {
                    if (isCurrentContext(requestVersion) && applyRunning) {
                        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_verifying"));
                    }
                });
                DlcFxDlcSyncSnapshot after = DlcFxDlcSyncSnapshot.collect(session, snapshot.localDlcRoot,
                        snapshot.localGameDir, snapshot.serverDlcRoot, snapshot.serverId,
                        DlcFxLightHash.Cache.load(cachePath), true, operation::isCanceled);
                return new SyncOutcome(executed, DlcFxDlcSyncPlanner.verify(after, direction, syncedKeys));
            }
        }, outcome -> {
            DlcFxDlcSyncExecutor.Result result = outcome.result();
            if (!isCurrentContext(requestVersion)) {
                return;
            }
            applyRunning = false;
            lastSnapshot = null;
            lastPlan = null;
            previewRows.clear();
            selectedKeys.clear();
            String key = result.canceled() ? "dlc_manager.server.sync_apply_canceled" : "dlc_manager.server.sync_apply_done";
            String text = DlcFxI18n.tr(key, result.transferred(), result.planned(), result.applied(), result.errors().size());
            List<DlcFxDlcSyncPlanner.Verification> verified = outcome.verification();
            int failed = DlcFxDlcSyncPlanner.countVerified(verified, DlcFxDlcSyncPlanner.VerifyStatus.MISMATCH)
                    + DlcFxDlcSyncPlanner.countVerified(verified, DlcFxDlcSyncPlanner.VerifyStatus.MISSING);
            if (!verified.isEmpty()) {
                text += " | " + DlcFxI18n.tr("dlc_manager.server.sync_verify_summary",
                        DlcFxDlcSyncPlanner.countVerified(verified, DlcFxDlcSyncPlanner.VerifyStatus.OK),
                        failed,
                        DlcFxDlcSyncPlanner.countVerified(verified, DlcFxDlcSyncPlanner.VerifyStatus.UNVERIFIED));
            }
            previewSummaryLabel.setText(text);
            List<String> problems = new ArrayList<>(result.errors());
            for (DlcFxDlcSyncPlanner.Verification item : verified) {
                if (item.status() != DlcFxDlcSyncPlanner.VerifyStatus.OK) {
                    problems.add(DlcFxI18n.tr("dlc_manager.server.sync_verify_row",
                            DlcFxI18n.tr("dlc_manager.server.sync_verify_" + item.status().name().toLowerCase(java.util.Locale.ROOT)),
                            item.identifier(),
                            item.key(),
                            DlcFxI18n.tr("dlc_manager.server.sync_verify_reason_" + item.reason())));
                }
            }
            if (!problems.isEmpty()) {
                DlcFxModalOverlay.show(
                        applyButton.getScene(),
                        DlcFxI18n.tr(failed > 0 || !result.errors().isEmpty()
                                ? "dlc_manager.server.sync_errors_title"
                                : "dlc_manager.server.sync_verify_title"),
                        String.join("\n", problems),
                        () -> {},
                        DlcFxI18n.tr("dlc_manager.confirm_yes"));
            }
            refreshState();
            if (direction == Direction.PULL && result.transferred() > 0) {
                localReadRunning = true;
                refreshState();
                readLocalFilesAction.accept(() -> {
                    if (isCurrentContext(requestVersion)) {
                        localReadRunning = false;
                        localFilesLoaded = true;
                        readLocalConfig();
                    }
                }, () -> {
                    if (isCurrentContext(requestVersion)) {
                        localReadRunning = false;
                        refreshState();
                    }
                });
            } else if (direction == Direction.PUSH) {
                refreshDlcStatus();
            }
        }, err -> {
            if (!isCurrentContext(requestVersion)) {
                return;
            }
            applyRunning = false;
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            refreshState();
        });
    }

    private List<Row> selectedRows() {
        List<Row> rows = new ArrayList<>();
        if (lastPlan == null) {
            return rows;
        }
        for (Row row : lastPlan.rows()) {
            if (row.status() == Status.TRANSFER && selectedKeys.contains(row.key())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private int selectedTransferCount() {
        int count = 0;
        for (Row row : selectedRows()) {
            count += row.transfers().size();
        }
        return count;
    }

    private Direction selectedDirection() {
        DirectionOption option = directionBox.getValue();
        return option == null ? Direction.PUSH : option.direction();
    }

    private DirectionOption directionOption(Direction direction) {
        for (DirectionOption option : directionBox.getItems()) {
            if (option.direction() == direction) {
                return option;
            }
        }
        return directionBox.getItems().isEmpty() ? null : directionBox.getItems().get(0);
    }

    private <T> void runDedicated(DlcFxDlcSyncTask<T> task,
                                  java.util.function.Consumer<T> onSuccess,
                                  java.util.function.Consumer<Throwable> onError) {
        runDedicated(task, onSuccess, onError, null);
    }

    private <T> void runDedicated(DlcFxDlcSyncTask<T> task,
                                  java.util.function.Consumer<T> onSuccess,
                                  java.util.function.Consumer<Throwable> onError,
                                  Runnable onCanceled) {
        DlcFxDlcSyncOperation operation = new DlcFxDlcSyncOperation();
        long requestVersion = contextVersion;
        activeOperation = operation;
        Thread thread = new Thread(() -> {
            T result = null;
            Throwable failure = null;
            try {
                result = task.run(operation);
            } catch (Throwable error) {
                failure = error;
            } finally {
                operation.close();
            }
            T completed = result;
            Throwable failed = failure;
            Platform.runLater(() -> {
                if (activeOperation != operation) {
                    return;
                }
                activeOperation = null;
                previewRunning = false;
                applyRunning = false;
                if (operation.isCanceled()) {
                    if (onCanceled != null) {
                        onCanceled.run();
                    } else if (isCurrentContext(requestVersion)) {
                        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_apply_canceled", 0, 0, 0, 0));
                    }
                } else if (failed != null) {
                    onError.accept(failed);
                } else {
                    onSuccess.accept(completed);
                }
                refreshState();
            });
        }, "dlc-sync");
        thread.setDaemon(true);
        operation.setWorker(thread);
        thread.start();
        refreshState();
    }

    private record SyncOutcome(DlcFxDlcSyncExecutor.Result result, List<DlcFxDlcSyncPlanner.Verification> verification) {
    }

    @FunctionalInterface
    private interface DlcFxDlcSyncTask<T> {
        T run(DlcFxDlcSyncOperation operation) throws Exception;
    }
    private void readLocalConfig() {
        if (!gameDirSelectedSupplier.getAsBoolean()) {
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            refreshState();
            return;
        }
        try {
            localConfigSnapshot = DlcFxServerConfigSyncPlanner.readLocalConfig();
            refreshConfigPreview();
        } catch (RuntimeException error) {
            localConfigSnapshot = null;
            lastConfigPreview = null;
            configRows.clear();
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(error)));
        }
        refreshState();
    }

    private void readServerConfig() {
        if (isBusy()) {
            return;
        }
        if (conn == null || !conn.isConnected()) {
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.not_connected"));
            refreshState();
            return;
        }
        DlcServerEntry entry = conn.currentEntry();
        if (entry == null) {
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.not_connected"));
            refreshState();
            return;
        }
        configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.config.loading"));
        configReadRunning = true;
        refreshState();
        long requestVersion = contextVersion;
        runDedicated(operation -> {
            try (DlcServerSession session = operation.connect(entry, SYNC_TIMEOUT_MS)) {
                operation.checkCanceled();
                return DlcFxServerConfigSyncPlanner.extractServerConfig(session.configGet());
            }
        }, config -> {
            configReadRunning = false;
            if (!isCurrentContext(requestVersion)) {
                refreshState();
                return;
            }
            if (config == null) {
                serverConfigSnapshot = null;
                lastConfigPreview = null;
                configRows.clear();
                configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.config.load_missing_config"));
            } else {
                serverConfigSnapshot = config;
                refreshConfigPreview();
            }
            refreshState();
        }, err -> {
            configReadRunning = false;
            if (!isCurrentContext(requestVersion)) {
                refreshState();
                return;
            }
            serverConfigSnapshot = null;
            lastConfigPreview = null;
            configRows.clear();
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.config.load_failed", DlcFxServerConnection.safeMessage(err)));
            refreshState();
        }, () -> {
            configReadRunning = false;
            if (isCurrentContext(requestVersion)) {
                configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_canceled"));
            }
            refreshState();
        });
    }

    private void refreshConfigPreview() {
        if (localConfigSnapshot == null && serverConfigSnapshot == null) {
            lastConfigPreview = null;
            configRows.clear();
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_empty"));
            return;
        }
        if (localConfigSnapshot == null) {
            lastConfigPreview = null;
            configRows.clear();
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_local_loaded_missing"));
            return;
        }
        if (serverConfigSnapshot == null) {
            lastConfigPreview = null;
            configRows.clear();
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_server_loaded_missing"));
            return;
        }
        lastConfigPreview = DlcFxServerConfigSyncPlanner.preview(localConfigSnapshot, serverConfigSnapshot);
        configRows.setAll(lastConfigPreview.rows());
        configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_summary", lastConfigPreview.changedCount(), lastConfigPreview.sameCount(), lastConfigPreview.maskedCount()));
    }

    private void confirmSyncConfig() {
        if (isBusy() || lastConfigPreview == null || lastConfigPreview.changedCount() <= 0) {
            refreshState();
            return;
        }
        long requestVersion = contextVersion;
        DlcFxModalOverlay.show(
                syncConfigButton.getScene(),
                DlcFxI18n.tr("dlc_manager.server.sync_config_confirm_title"),
                DlcFxI18n.tr("dlc_manager.server.sync_config_confirm", lastConfigPreview.changedCount()),
                () -> {
                    if (isCurrentContext(requestVersion)) {
                        syncConfig();
                    }
                },
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.confirm_no"));
    }

    private void syncConfig() {
        if (isBusy()) {
            return;
        }
        if (conn == null || !conn.isConnected() || localConfigSnapshot == null || serverConfigSnapshot == null) {
            refreshState();
            return;
        }
        DlcServerEntry entry = conn.currentEntry();
        if (entry == null) {
            refreshState();
            return;
        }
        configSyncRunning = true;
        long requestVersion = contextVersion;
        lastSnapshot = null;
        lastPlan = null;
        previewRows.clear();
        selectedKeys.clear();
        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_preview_empty"));
        serverConfigSnapshot = null;
        lastConfigPreview = null;
        configRows.clear();
        refreshState();
        configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.config.saving"));
        JsonObject local = localConfigSnapshot.deepCopy();
        runDedicated(operation -> {
            try (DlcServerSession session = operation.connect(entry, SYNC_TIMEOUT_MS)) {
                operation.checkCanceled();
                JsonObject current = DlcFxServerConfigSyncPlanner.extractServerConfig(session.configGet());
                if (current == null) {
                    throw new java.io.IOException("server config not readable");
                }
                JsonObject next = DlcFxServerConfigSyncPlanner.buildServerConfigUpdate(local, current);
                operation.checkCanceled();
                session.configSet(next);
                operation.checkCanceled();
                JsonObject refreshed = DlcFxServerConfigSyncPlanner.extractServerConfig(session.configGet());
                if (refreshed == null) {
                    throw new java.io.IOException("server config not readable after synchronization");
                }
                return refreshed;
            }
        }, refreshed -> {
            configSyncRunning = false;
            if (!isCurrentContext(requestVersion)) {
                refreshState();
                return;
            }
            serverConfigSnapshot = refreshed;
            contextVersion++;
            lastSnapshot = null;
            lastPlan = null;
            previewRows.clear();
            selectedKeys.clear();
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_preview_empty"));
            refreshConfigPreview();
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_done", lastConfigPreview == null ? 0 : lastConfigPreview.changedCount()));
            refreshState();
        }, err -> {
            configSyncRunning = false;
            lastSnapshot = null;
            lastPlan = null;
            previewRows.clear();
            selectedKeys.clear();
            if (!isCurrentContext(requestVersion)) {
                refreshState();
                return;
            }
            configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            refreshState();
        }, () -> {
            configSyncRunning = false;
            if (isCurrentContext(requestVersion)) {
                configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_canceled"));
            }
            refreshState();
        });
    }

    private String summary(DlcFxDlcSyncPlanner.Plan plan) {
        if (plan == null) {
            return DlcFxI18n.tr("dlc_manager.server.sync_preview_empty");
        }
        return DlcFxI18n.tr(
                "dlc_manager.server.sync_preview_summary",
                plan.count(Status.TRANSFER),
                plan.count(Status.SOURCE_DATA_MISSING),
                plan.count(Status.TARGET_ONLY),
                plan.count(Status.SYNCED),
                plan.unverifiedCount());
    }

    private void updateDlcStatusLabel(DlcServerInfo info) {
        if (info == null) {
            dlcStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_dlc_status_empty"));
            return;
        }
        int itemCount = info.items == null ? 0 : info.items.size();
        dlcStatusLabel.setText(DlcFxI18n.tr(
                "dlc_manager.server.sync_dlc_status",
                safeText(info.baseVersion),
                safeText(info.dlcVersion),
                itemCount,
                info.remoteMissing,
                info.requiredMissing));
    }

    private void saveConfig() {
        if (config != null) {
            config.save();
        }
    }

    private void reloadConfig() {
        config = DlcFxServerSyncConfig.load();
        if (!isBusy()) {
            DirectionOption option = directionOption(config.syncDirection());
            if (option != null && option != directionBox.getValue()) {
                directionBox.getSelectionModel().select(option);
            }
            deepVerifyCheck.setSelected(config.deepVerify());
        }
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String changeText(DlcFxDlcSyncPlanner.Change change) {
        return DlcFxI18n.tr("dlc_manager.server.sync_change_" + change.name().toLowerCase(java.util.Locale.ROOT));
    }

    private final class PreviewRowCell extends ListCell<Row> {
        private final CheckBox check = new CheckBox();
        private Row row;

        PreviewRowCell() {
            check.selectedProperty().addListener((obs, oldValue, newValue) -> {
                if (row == null || row.status() != Status.TRANSFER) {
                    return;
                }
                boolean changed = newValue ? selectedKeys.add(row.key()) : selectedKeys.remove(row.key());
                if (changed) {
                    refreshState();
                }
            });
        }

        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            row = null;
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String statusKey = "dlc_manager.server.sync_status_" + item.status().name().toLowerCase(java.util.Locale.ROOT);
            String kindKey = "dlc_manager.server.sync_kind_" + item.kind().name().toLowerCase(java.util.Locale.ROOT);
            String text = DlcFxI18n.tr(
                    "dlc_manager.server.sync_row",
                    DlcFxI18n.tr(statusKey),
                    DlcFxI18n.tr(kindKey),
                    item.item().identifier(),
                    item.key(),
                    changeText(item.configChange()),
                    changeText(item.dataChange()),
                    formatSize(item.transferBytes()));
            setText(text);
            if (item.status() == Status.TRANSFER) {
                check.setSelected(selectedKeys.contains(item.key()));
                check.setDisable(applyRunning);
                row = item;
                setGraphic(check);
            } else {
                setGraphic(null);
            }
        }
    }

    private record DirectionOption(Direction direction, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private void invalidateSyncState() {
        contextVersion++;
        localReadRunning = false;
        configReadRunning = false;
        configSyncRunning = false;
        localFilesLoaded = false;
        if (activeOperation != null) {
            activeOperation.cancel();
        }
        lastSnapshot = null;
        lastPlan = null;
        previewRows.clear();
        selectedKeys.clear();
        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_preview_empty"));
        localConfigSnapshot = null;
        serverConfigSnapshot = null;
        lastConfigPreview = null;
        configRows.clear();
        configSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_config_empty"));
    }

    private boolean isCurrentContext(long requestVersion) {
        return requestVersion == contextVersion;
    }

    private boolean isBusy() {
        return activeOperation != null || previewRunning || applyRunning || configReadRunning || configSyncRunning;
    }
    private static final class ConfigRowCell extends ListCell<DlcFxServerConfigSyncPlanner.Row> {
        @Override
        protected void updateItem(DlcFxServerConfigSyncPlanner.Row item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            String key = item.changed()
                    ? "dlc_manager.server.sync_config_row_changed"
                    : "dlc_manager.server.sync_config_row_same";
            setText(DlcFxI18n.tr(key, item.tabLabel(), item.label(), item.localValue(), item.serverValue()));
        }
    }
}
