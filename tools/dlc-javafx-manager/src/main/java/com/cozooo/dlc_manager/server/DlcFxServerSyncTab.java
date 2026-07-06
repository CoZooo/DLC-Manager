package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DlcFxServerSyncTab {

    private final DlcFxServerConnection conn;
    private final Supplier<String> gameDirTextSupplier;
    private final BooleanSupplier gameDirSelectedSupplier;
    private final ObservableValue<String> serverPortTextProperty;
    private final Runnable chooseGameDirAction;
    private final Runnable readLocalFilesAction;
    private final Label gameDirLabel = new Label();
    private final Label portLabel = new Label();
    private final Label connectionLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final Label dlcStatusLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_dlc_status_empty"));
    private final Label previewSummaryLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_preview_empty"));
    private final Label modeNoticeLabel = new Label("");
    private final Button readLocalButton = new Button(DlcFxI18n.tr("dlc_manager.server.read_local_files"));
    private final Button chooseDirButton = new Button(DlcFxI18n.tr("dlc_manager.local.choose_game_dir"));
    private final Button refreshDlcButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_refresh_dlc"));
    private final Button previewButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_preview"));
    private final Button applyButton = new Button(DlcFxI18n.tr("dlc_manager.server.sync_apply"));
    private final CheckBox excludeDlcModsCheck = new CheckBox(DlcFxI18n.tr("dlc_manager.server.sync_exclude_dlc_mods"));
    private final ObservableList<DlcFxServerSyncPlanner.Row> previewRows = FXCollections.observableArrayList();
    private final ListView<DlcFxServerSyncPlanner.Row> previewList = new ListView<>(previewRows);

    private DlcFxServerSyncConfig config;
    private DlcFxServerSyncPlanner.Preview lastPreview;
    private volatile boolean applyRunning;

    public DlcFxServerSyncTab(DlcFxServerConnection conn,
                              Supplier<String> gameDirTextSupplier,
                              BooleanSupplier gameDirSelectedSupplier,
                              ObservableValue<String> serverPortTextProperty,
                              Runnable chooseGameDirAction,
                              Runnable readLocalFilesAction) {
        this.conn = conn;
        this.gameDirTextSupplier = gameDirTextSupplier == null ? () -> "" : gameDirTextSupplier;
        this.gameDirSelectedSupplier = gameDirSelectedSupplier == null ? () -> false : gameDirSelectedSupplier;
        this.serverPortTextProperty = serverPortTextProperty;
        this.chooseGameDirAction = chooseGameDirAction == null ? () -> {} : chooseGameDirAction;
        this.readLocalFilesAction = readLocalFilesAction == null ? () -> {} : readLocalFilesAction;
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage("");
        config = DlcFxServerSyncConfig.load();

        chooseDirButton.setOnAction(e -> chooseGameDirAction.run());
        DlcFxStyle.primary(chooseDirButton);
        readLocalButton.setOnAction(e -> {
            readLocalFilesAction.run();
            refreshState();
        });
        refreshDlcButton.setOnAction(e -> refreshDlcStatus());
        previewButton.setOnAction(e -> previewSync());
        applyButton.setOnAction(e -> applySync());
        DlcFxStyle.primary(applyButton);

        excludeDlcModsCheck.setSelected(config.excludeDlcManagedMods());
        excludeDlcModsCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            config.setExcludeDlcManagedMods(newValue);
            saveConfig();
        });

        previewList.setCellFactory(list -> new PreviewRowCell());
        VBox.setVgrow(previewList, Priority.ALWAYS);
        DlcFxStyle.muted(connectionLabel);
        DlcFxStyle.muted(dlcStatusLabel);
        DlcFxStyle.muted(previewSummaryLabel);
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

        HBox optionRow = new HBox(8, excludeDlcModsCheck);
        optionRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(8, previewButton, applyButton, previewSummaryLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(10,
                optionRow,
                modeNoticeLabel,
                dlcStatusLabel,
                actionRow,
                previewList);
        center.setAlignment(Pos.TOP_LEFT);
        page.setCenter(page.section(center));

        if (conn != null) {
            conn.connectedProperty().addListener((obs, oldValue, newValue) -> refreshState());
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
        gameDirLabel.setText(selected ? safeText(gameDirTextSupplier.get()) : DlcFxI18n.tr("dlc_manager.local.no_game_dir"));
        portLabel.setText(safeText(serverPortTextProperty == null ? "" : serverPortTextProperty.getValue()));
        connectionLabel.setText(conn == null ? DlcFxI18n.tr("dlc_manager.status.disconnected") : conn.statusTextProperty().get());
        readLocalButton.setDisable(!selected);
        refreshDlcButton.setDisable(!connected);
        previewButton.setDisable(!connected || !selected || !dlcTcp);
        applyButton.setDisable(!connected || !selected || !dlcTcp || lastPreview == null || lastPreview.uploadCount() <= 0 || applyRunning);
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
        dlcStatusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        conn.runAsync(session -> session.fetchList(), info -> {
            conn.rememberServerInfo(info);
            updateDlcStatusLabel(info);
            refreshState();
        }, err -> dlcStatusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err))));
    }

    private void previewSync() {
        if (config != null && config.transportMode() != DlcFxServerSyncConfig.TransportMode.DLC_TCP) {
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_webrtc_disabled"));
            return;
        }
        if (conn == null || !conn.isConnected()) {
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.not_connected"));
            return;
        }
        Path gameDir = Path.of(safeText(gameDirTextSupplier.get()));
        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        previewRows.clear();
        conn.runAsync(session -> {
            DlcServerInfo info = session.fetchList();
            List<DlcServerFileEntry> remoteMods = session.listFiles("mods");
            return DlcFxServerSyncPlanner.preview(gameDir, info, remoteMods, config == null || config.excludeDlcManagedMods());
        }, preview -> {
            lastPreview = preview;
            rebuildPreviewRows(preview);
            previewSummaryLabel.setText(summary(preview));
            refreshState();
        }, err -> {
            lastPreview = null;
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            refreshState();
        });
    }

    private void applySync() {
        if (lastPreview == null || conn == null || !conn.isConnected()) {
            refreshState();
            return;
        }
        if (config != null && config.transportMode() != DlcFxServerSyncConfig.TransportMode.DLC_TCP) {
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_webrtc_disabled"));
            return;
        }
        applyRunning = true;
        refreshState();
        previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_applying"));
        conn.runAsync(session -> {
            DlcFxServerSyncPlanner.ApplyResult upload = DlcFxServerSyncPlanner.uploadChanged(session, lastPreview, () -> false);
            session.remoteDownloadStart(List.of());
            session.requiredDownloadStart(List.of());
            return upload;
        }, result -> {
            applyRunning = false;
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.server.sync_apply_done", result.uploaded(), result.planned(), result.errors().size()));
            lastPreview = null;
            refreshState();
        }, err -> {
            applyRunning = false;
            previewSummaryLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", DlcFxServerConnection.safeMessage(err)));
            refreshState();
        });
    }

    private void rebuildPreviewRows(DlcFxServerSyncPlanner.Preview preview) {
        List<DlcFxServerSyncPlanner.Row> rows = new ArrayList<>();
        rows.addAll(preview.missingRemote);
        rows.addAll(preview.changed);
        rows.addAll(preview.extraRemote);
        rows.addAll(preview.excluded);
        rows.addAll(preview.ok);
        previewRows.setAll(rows);
    }

    private String summary(DlcFxServerSyncPlanner.Preview preview) {
        if (preview == null) {
            return DlcFxI18n.tr("dlc_manager.server.sync_preview_empty");
        }
        return DlcFxI18n.tr(
                "dlc_manager.server.sync_preview_summary",
                preview.missingRemote.size(),
                preview.changed.size(),
                preview.extraRemote.size(),
                preview.excluded.size(),
                preview.ok.size());
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
        if (excludeDlcModsCheck != null) {
            excludeDlcModsCheck.setSelected(config.excludeDlcManagedMods());
        }
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static final class PreviewRowCell extends ListCell<DlcFxServerSyncPlanner.Row> {
        @Override
        protected void updateItem(DlcFxServerSyncPlanner.Row item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            String key = switch (item.kind()) {
                case MISSING_REMOTE -> "dlc_manager.server.sync_row_missing_remote";
                case CHANGED -> "dlc_manager.server.sync_row_changed";
                case EXTRA_REMOTE -> "dlc_manager.server.sync_row_extra_remote";
                case EXCLUDED -> "dlc_manager.server.sync_row_excluded";
                case OK -> "dlc_manager.server.sync_row_ok";
            };
            setText(DlcFxI18n.tr(key, item.fileName(), item.localSize(), item.remoteSize()));
        }
    }
}
