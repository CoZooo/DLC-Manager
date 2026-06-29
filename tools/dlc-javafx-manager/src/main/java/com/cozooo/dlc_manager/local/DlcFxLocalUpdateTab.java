package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.app.DlcFxManagerApp;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteRuntime;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CancelChecker;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CheckResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.ProgressListener;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.Side;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.ApplyProgressListener;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.ImportValidationResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateRollbackResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxListViewSupport;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DlcFxLocalUpdateTab {

    private final DlcFxPlatformProvider provider;

    private final Label versionLabel = new Label(DlcFxI18n.tr("dlc_manager.update.version_empty"));

    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.update.check_local"));
    private final Button checkRemoteBtn = new Button(DlcFxI18n.tr("dlc_manager.update.sync_remote"));
    private final Button downloadRemoteBtn = new Button(DlcFxI18n.tr("dlc_manager.update.download_remote"));
    private final Button cancelDownloadBtn = new Button(DlcFxI18n.tr("dlc_manager.common.cancel_download"));
    private final Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.update.apply"));
    private final Button rollbackBtn = new Button(DlcFxI18n.tr("dlc_manager.update.rollback_selected"));
    private final Button importBtn = new Button(DlcFxI18n.tr("dlc_manager.update.import_package"));

    private final ObservableList<DlcUpdateMeta> history = FXCollections.observableArrayList();
    private final ListView<DlcUpdateMeta> historyView = new ListView<>(history);

    private final ScrollPane changelogPane = new ScrollPane();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);

    private CheckResult lastRemoteCheck;
    private volatile List<DlcUpdatePackage> lastApplyChain = List.of();
    private volatile boolean downloadCanceled;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalUpdateTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
        changelogPane.setFitToWidth(true);
        DlcFxStyle.add(changelogPane, "dlc-rich-text-pane");
        setChangelogText("");
        cancelDownloadBtn.setVisible(false);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_update"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        refreshBtn.setDisable(true);
        checkRemoteBtn.setDisable(true);
        applyBtn.setDisable(true);
        rollbackBtn.setDisable(true);
        importBtn.setDisable(true);
        refreshBtn.setOnAction(e -> {
            if (gameDirSelected) {
                checkLocalAsync();
            }
        });
        checkRemoteBtn.setOnAction(e -> checkRemoteAsync());
        downloadRemoteBtn.setOnAction(e -> downloadRemoteAsync());
        downloadRemoteBtn.setDisable(true);
        cancelDownloadBtn.setOnAction(e -> downloadCanceled = true);
        applyBtn.setOnAction(e -> applySelectedAsync());
        rollbackBtn.setOnAction(e -> rollbackSelectedAsync());
        importBtn.setOnAction(e -> importPackage());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(checkRemoteBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.danger(cancelDownloadBtn);

        VBox topBox = new VBox(4, versionLabel);
        DlcFxStyle.toolbar(topBox);
        root.setTop(topBox);

        historyView.setCellFactory(param -> new UpdateRowCell());
        historyView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
        historyView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> showChangelogFor(sel));

        Label historyHeader = new Label(DlcFxI18n.tr("dlc_manager.update.version_list_header"));
        DlcFxStyle.title(historyHeader);
        VBox leftBox = new VBox(4, historyHeader, DlcFxListViewSupport.wrap(historyView));
        leftBox.setPadding(new Insets(0, 4, 0, 0));

        Label changelogHeader = new Label(DlcFxI18n.tr("dlc_manager.update.changelog_header"));
        DlcFxStyle.title(changelogHeader);
        VBox rightBox = new VBox(4, changelogHeader, changelogPane);
        VBox.setVgrow(changelogPane, Priority.ALWAYS);
        rightBox.setPadding(new Insets(0, 0, 0, 4));

        HBox centerBox = new HBox(8, leftBox, rightBox);
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        VBox actionPanel = new VBox(0,
                new HBox(8, refreshBtn, checkRemoteBtn, downloadRemoteBtn, cancelDownloadBtn, applyBtn, rollbackBtn, importBtn),
                new HBox(8, progressBar, statusLabel));
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        history.clear();
        setChangelogText("");
        versionLabel.setText(DlcFxI18n.tr("dlc_manager.update.version_empty"));
        lastRemoteCheck = null;
        lastApplyChain = List.of();
        downloadRemoteBtn.setDisable(true);
        setBusy(false, null);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.game_dir_changed"));
    }

    public void onTabSelected() {
        if (!gameDirSelected || loadedForCurrentGameDir) {
            return;
        }
        checkLocalAsync();
    }

    private void checkLocalAsync() {
        if (!requireGameDir()) {
            return;
        }
        loadedForCurrentGameDir = true;
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.scanning_local"));
        progressBar.setProgress(-1);
        Thread thread = new Thread(() -> {
            try {
                UpdateCheckResult result = DlcUpdateService.checkForUpdates(false);
                List<DlcUpdateMeta> rows = new ArrayList<>();
                if (result.hasUpdates()) {
                    for (DlcUpdatePackage pkg : result.getUpdates()) {
                        DlcUpdateMeta meta = metaForPackage(pkg);
                        if (meta != null) {
                            rows.add(meta);
                        }
                    }
                }
                rows.addAll(DlcUpdateService.listUpdateHistory());
                Platform.runLater(() -> {
                    history.setAll(rows);
                    renderVersionHeader(result);
                    if (result.hasUpdates()) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.available_count", result.getUpdates().size()));
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.local_latest"));
                    }
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.check_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-check-local");
        thread.setDaemon(true);
        thread.start();
    }

    private void applySelectedAsync() {
        DlcUpdateMeta selected = historyView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.select_apply_version"));
            return;
        }
        DlcFxModalOverlay.show(
                versionLabel.getScene(),
                DlcFxI18n.tr("dlc_manager.update.apply_title"),
                DlcFxI18n.tr("dlc_manager.update.apply_confirm", safe(selected.getDlcNew())),
                () -> startApplyThread(selected),
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                null,
                null
        );
    }

    private void rollbackSelectedAsync() {
        DlcUpdateMeta selected = historyView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.select_rollback_version"));
            return;
        }
        String target = safe(selected.getDlcNew());
        if (target.isBlank()) {
            target = safe(selected.getDlcOld());
        }
        if (target.isBlank()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.rollback_target_missing"));
            return;
        }
        final String finalTarget = target;
        DlcFxModalOverlay.show(
                versionLabel.getScene(),
                DlcFxI18n.tr("dlc_manager.update.rollback_title"),
                DlcFxI18n.tr("dlc_manager.update.rollback_confirm", finalTarget),
                () -> startRollbackThread(finalTarget),
                DlcFxI18n.tr("dlc_manager.confirm_yes"),
                null,
                null
        );
    }

    private void importPackage() {
        if (!requireGameDir()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(DlcFxI18n.tr("dlc_manager.update.choose_package"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(DlcFxI18n.tr("dlc_manager.update.package_filter"), "*.zip"));
        java.io.File file = chooser.showOpenDialog(primaryStage());
        if (file == null) {
            return;
        }
        Path zipPath = file.toPath();
        ImportValidationResult validation = DlcUpdateService.validateImportPackage(zipPath);
        if (!validation.isValid()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.import_validation_failed", DlcUpdateService.toImportValidationMessage(validation)));
            return;
        }
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.importing"));
        progressBar.setProgress(-1);
        Thread thread = new Thread(() -> {
            try {
                boolean ok = DlcUpdateService.importUpdatePackage(zipPath);
                Platform.runLater(() -> {
                    statusLabel.setText(ok
                            ? DlcFxI18n.tr("dlc_manager.update.import_done")
                            : DlcFxI18n.tr("dlc_manager.update.import_invalid"));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (ok) {
                        checkLocalAsync();
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.import_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-import");
        thread.setDaemon(true);
        thread.start();
    }

    private void startApplyThread(DlcUpdateMeta selected) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.applying"));
        progressBar.setProgress(-1);
        final String dlcOld = safe(selected.getDlcOld());
        final String dlcNew = safe(selected.getDlcNew());
        final ApplyProgressListener listener = (status, progress) ->
                Platform.runLater(() -> {
                    if (status != null && !status.isBlank()) {
                        statusLabel.setText(status);
                    }
                    progressBar.setProgress(progress < 0 ? -1 : progress);
                });
        Thread thread = new Thread(() -> {
            try {
                List<DlcUpdatePackage> chain = DlcUpdateService.resolveUpdateChainTo(dlcOld, dlcNew, false);
                if (chain.isEmpty()) {
                    chain = DlcUpdateService.resolveUpdateChainTo(dlcNew, false);
                }
                if (chain.isEmpty()) {
                    Platform.runLater(() -> {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.chain_missing"));
                        progressBar.setVisible(false);
                        setBusy(false, null);
                    });
                    return;
                }
                lastApplyChain = new ArrayList<>(chain);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false, listener);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    handleApplyResult(result);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-apply");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleApplyResult(UpdateApplyResult result) {
        if (result == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.result_unknown"));
            return;
        }
        if (result.isMissing()) {
            showMissingUpdateTargets(result);
            return;
        }
        statusLabel.setText(describeApply(result));
        if (result.isSuccess()) {
            checkLocalAsync();
        }
    }

    private void showMissingUpdateTargets(UpdateApplyResult result) {
        List<String> missing = result.getMissingPaths();
        if (missing == null || missing.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.result_failed", DlcUpdateService.toFailureMessage(result)));
            return;
        }
        String title = DlcFxI18n.tr("dlc_manager.update.missing_title");
        StringBuilder message = new StringBuilder(DlcFxI18n.tr("dlc_manager.update.missing_desc"));
        message.append("\n\n");
        for (String path : missing) {
            if (path != null && !path.isBlank()) {
                message.append("- ").append(path).append('\n');
            }
        }
        if (message.charAt(message.length() - 1) == '\n') {
            message.setLength(message.length() - 1);
        }
        DlcFxModalOverlay.show(
                versionLabel.getScene(),
                title,
                DlcFxRichTextRenderer.formatted(message.toString()),
                this::startForceApply,
                DlcFxI18n.tr("dlc_manager.update.force"),
                () -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.missing_cancelled"));
                },
                DlcFxI18n.tr("dlc_manager.update.cancel")
        );
    }

    private void startForceApply() {
        if (!requireGameDir()) {
            return;
        }
        List<DlcUpdatePackage> chain = lastApplyChain;
        if (chain == null || chain.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.chain_missing"));
            return;
        }
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.applying"));
        progressBar.setProgress(-1);
        final ApplyProgressListener listener = (status, progress) ->
                Platform.runLater(() -> {
                    if (status != null && !status.isBlank()) {
                        statusLabel.setText(status);
                    }
                    progressBar.setProgress(progress < 0 ? -1 : progress);
                });
        Thread thread = new Thread(() -> {
            try {
                UpdateApplyResult forced = DlcUpdateService.applyUpdates(chain, true, listener);
                Platform.runLater(() -> {
                    statusLabel.setText(describeApply(forced));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (forced.isSuccess()) {
                        checkLocalAsync();
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-force-apply");
        thread.setDaemon(true);
        thread.start();
    }

    private void startRollbackThread(String target) {
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.rolling_back"));
        progressBar.setProgress(-1);
        final String finalTarget = target;
        Thread thread = new Thread(() -> {
            try {
                UpdateRollbackResult result = DlcUpdateService.rollbackTo(finalTarget);
                Platform.runLater(() -> {
                    statusLabel.setText(result.isSuccess()
                            ? DlcFxI18n.tr("dlc_manager.update.rollback_done", safe(result.getVersion()))
                            : DlcFxI18n.tr("dlc_manager.update.rollback_failed", safe(result.getMessage())));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (result.isSuccess()) {
                        checkLocalAsync();
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.rollback_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-rollback");
        thread.setDaemon(true);
        thread.start();
    }

    private void checkRemoteAsync() {
        if (!requireGameDir()) {
            return;
        }
        String repoUrl = DlcConfig.getUpdateGitRepoUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_repo_missing"));
            return;
        }
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.checking_remote"));
        progressBar.setProgress(-1);
        final DlcUpdateVersion current = DlcUpdateVersion.load(DlcConfig.getDlcRoot());
        final String base = safe(current.getBase());
        final String dlc = safe(current.getDlc());
        Thread thread = new Thread(() -> {
            try {
                CheckResult result = DlcPackReleaseUpdateService.checkForUpdates(
                        DlcConfig.getModpackUrl(), repoUrl, base, dlc, Side.CLIENT);
                lastRemoteCheck = result;
                Platform.runLater(() -> {
                    if (result.error != null && !result.error.isBlank()) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_check_failed",
                                DlcPackReleaseUpdateService.toCheckFailureMessage(result.error)));
                        downloadRemoteBtn.setDisable(true);
                    } else if (result.hasUpdate()) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_available",
                                formatVersion(result.currentBase, result.currentDlc),
                                formatVersion(result.targetBase, result.targetDlc)));
                        setChangelogText(result.resolveCombinedChangelog(currentLang()));
                        downloadRemoteBtn.setDisable(!result.canDownload());
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_latest"));
                        downloadRemoteBtn.setDisable(true);
                    }
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_check_failed", safeMessage(t)));
                    downloadRemoteBtn.setDisable(true);
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-check-remote");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadRemoteAsync() {
        CheckResult result = lastRemoteCheck;
        if (result == null || !result.hasUpdate()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.check_remote_first"));
            return;
        }
        if (!result.canDownload()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_no_assets"));
            return;
        }
        downloadCanceled = false;
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.downloading"));
        progressBar.setProgress(0);
        cancelDownloadBtn.setVisible(true);
        final CancelChecker cancelChecker = () -> downloadCanceled;
        final ProgressListener progress = (stepIndex, stepCount, fileName, downloaded, total, overall) ->
                Platform.runLater(() -> {
                    progressBar.setProgress(overall < 0 ? -1 : overall);
                    if (stepCount > 0) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.download_progress",
                                stepIndex + 1, stepCount, safe(fileName)));
                    }
                });
        Thread thread = new Thread(() -> {
            try {
                DownloadResult dl = DlcPackReleaseUpdateService.downloadClientPackages(
                        result, progress, cancelChecker);
                Platform.runLater(() -> {
                    cancelDownloadBtn.setVisible(false);
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (dl.canceled) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_canceled"));
                    } else if (dl.success) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.download_done", dl.files.size()));
                        checkLocalAsync();
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_failed",
                                DlcPackReleaseUpdateService.toDownloadFailureMessage(dl.message)));
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    cancelDownloadBtn.setVisible(false);
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.download_failed", safeMessage(t)));
                });
            }
        }, "dlc-update-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderVersionHeader(UpdateCheckResult result) {
        DlcUpdateVersion v = result.getCurrent();
        String rollbackFrom = v.getRollbackFrom();
        String base = v.getBase();
        String dlc = v.getDlc();
        String current = DlcFxI18n.tr("dlc_manager.update.current_version",
                base == null || base.isBlank() ? "-" : base,
                dlc == null || dlc.isBlank() ? "-" : dlc);
        if (rollbackFrom != null && !rollbackFrom.isBlank()) {
            current = current + DlcFxI18n.tr("dlc_manager.update.current_version_suffix", rollbackFrom);
        }
        versionLabel.setText(current);
    }

    private void showChangelogFor(DlcUpdateMeta meta) {
        if (meta == null) {
            setChangelogText("");
            return;
        }
        String text = resolveChangelogText(meta);
        setChangelogText(text);
    }

    private void setChangelogText(String text) {
        String value = text == null ? "" : text;
        if (value.isBlank()) {
            Label prompt = new Label(DlcFxI18n.tr("dlc_manager.update.changelog_prompt"));
            DlcFxStyle.muted(prompt);
            changelogPane.setContent(prompt);
            return;
        }
        changelogPane.setContent(DlcFxRichTextRenderer.markdown(value));
    }

    private String resolveChangelogText(DlcUpdateMeta meta) {
        if (meta == null) {
            return "";
        }
        Map<String, String> log = meta.getChangelog();
        if (log == null || log.isEmpty()) {
            return "";
        }
        String lang = currentLang();
        String localized = log.get(com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(lang));
        if (localized == null || localized.isBlank()) {
            localized = log.get("en_us");
        }
        if (localized == null || localized.isBlank()) {
            localized = log.values().stream().findFirst().orElse("");
        }
        return localized;
    }

    private static DlcUpdateMeta metaForPackage(DlcUpdatePackage pkg) {
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return null;
        }
        DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.setBaseOld(info.getBase());
        meta.setBaseNew(info.getBaseNew());
        meta.setDlcOld(info.getDlcOld());
        meta.setDlcNew(info.getDlcNew());
        if (pkg.getManifest().getChangelog() != null) {
            meta.getChangelog().putAll(pkg.getManifest().getChangelog());
        }
        return meta;
    }

    private String describeApply(UpdateApplyResult result) {
        if (result == null) {
            return DlcFxI18n.tr("dlc_manager.update.result_unknown");
        }
        if (result.isSuccess()) {
            StringBuilder sb = new StringBuilder(DlcFxI18n.tr("dlc_manager.update.result_success"));
            if (result.hasRequiredAdd()) {
                sb.append(DlcFxI18n.tr("dlc_manager.update.result_required_added"));
                List<DlcRequiredEntry> missing = DlcRequiredRuntime.collectMissingDownloadsForPrompt();
                if (!missing.isEmpty()) {
                    sb.append(DlcFxI18n.tr("dlc_manager.update.result_required_missing", missing.size()));
                }
            }
            if (result.hasRemoteAdd()) {
                sb.append(DlcFxI18n.tr("dlc_manager.update.result_remote_added"));
                List<DlcRemoteEntry> missing = DlcRemoteRuntime.collectMissing();
                if (!missing.isEmpty()) {
                    sb.append(DlcFxI18n.tr("dlc_manager.update.result_remote_missing", missing.size()));
                }
            }
            if (result.isHelperScheduled()) {
                sb.append(DlcFxI18n.tr("dlc_manager.apply.helper_scheduled"));
            }
            if (result.isDlcManagerUpdated()) {
                sb.append(DlcFxI18n.tr("dlc_manager.update.manager_updated"));
            }
            return sb.toString();
        }
        return DlcFxI18n.tr("dlc_manager.update.result_failed", DlcUpdateService.toFailureMessage(result));
    }

    private static String formatVersion(String base, String dlc) {
        String b = base == null || base.isBlank() ? "-" : base;
        String d = dlc == null || dlc.isBlank() ? "-" : dlc;
        return b + "+" + d;
    }

    private static String currentLang() {
        String lang = CrossPlatformRuntime.languageCode();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private void setBusy(boolean busy, String status) {
        boolean disabled = busy || !gameDirSelected;
        refreshBtn.setDisable(disabled);
        checkRemoteBtn.setDisable(disabled);
        applyBtn.setDisable(disabled);
        rollbackBtn.setDisable(disabled);
        importBtn.setDisable(disabled);
        if (busy) {
            progressBar.setVisible(true);
            if (status != null) {
                statusLabel.setText(status);
            }
        }
    }

    private boolean requireGameDir() {
        if (!gameDirSelected || DlcConfig.getDlcRoot() == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            setBusy(false, null);
            return false;
        }
        return true;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private javafx.stage.Stage primaryStage() {
        return (javafx.stage.Stage) versionLabel.getScene().getWindow();
    }

    private final class UpdateRowCell extends javafx.scene.control.ListCell<DlcUpdateMeta> {
        private final Label transitionLabel = new Label();
        private final Label tagLabel = new Label();

        UpdateRowCell() {
            DlcFxStyle.title(transitionLabel);
            DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_NEUTRAL);
        }

        @Override
        protected void updateItem(DlcUpdateMeta item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                transitionLabel.setText(describeTransition(item));
                boolean isHistory = item.getTimestamp() != null && !item.getTimestamp().isBlank();
                if (isHistory) {
                    tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_history"));
                    DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_NEUTRAL);
                } else {
                    tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_available"));
                    DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_OK);
                }
                HBox row = new HBox(8, transitionLabel, tagLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        }

        private String describeTransition(DlcUpdateMeta item) {
            String baseOld = safe(item.getBaseOld());
            String baseNew = safe(item.getBaseNew());
            String dlcOld = safe(item.getDlcOld());
            String dlcNew = safe(item.getDlcNew());
            boolean baseChanged = !baseOld.isBlank() && !baseNew.isBlank() && !baseOld.equals(baseNew);
            boolean dlcChanged = !dlcOld.isBlank() && !dlcNew.isBlank() && !dlcOld.equals(dlcNew);
            if (baseChanged && !dlcChanged) {
                return DlcFxI18n.tr("dlc_manager.update.base_transition", baseOld, baseNew);
            }
            if (dlcChanged && !baseChanged) {
                return DlcFxI18n.tr("dlc_manager.update.dlc_transition", dlcOld, dlcNew);
            }
            String from = DlcFxI18n.tr("dlc_manager.update.current_version", baseOld.isBlank() ? "-" : baseOld, dlcOld.isBlank() ? "-" : dlcOld);
            String to = DlcFxI18n.tr("dlc_manager.update.current_version", baseNew.isBlank() ? "-" : baseNew, dlcNew.isBlank() ? "-" : dlcNew);
            return from + " -> " + to;
        }
    }
}
