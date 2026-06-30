package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteRuntime;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CheckResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.ProgressListener;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.ReleaseStep;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.Side;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import com.cozooo.dlc_manager.client.update.DlcUpdateRuntime;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.ApplyProgressListener;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.ImportValidationResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateRollbackResult;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.DlcFxStyle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DlcFxLocalUpdateTab {
    private static final String DEFAULT_VERSION = "1.0.0";

    private final DlcFxPlatformProvider provider;
    private final Label versionLabel = new Label(DlcFxI18n.tr("dlc_manager.update.version_empty"));
    private final Label rollbackInfoLabel = new Label();
    private final Label updateHintLabel = new Label();
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.update.check_local"));
    private final Button importBtn = new Button(DlcFxI18n.tr("dlc_manager.update.import_package"));
    private final Button importUrlBtn = new Button(DlcFxI18n.tr("dlc_manager.update.import_url"));
    private final Button checkPackUpdateBtn = new Button(DlcFxI18n.tr("dlc_manager.pack_update.check_button"));
    private final ObservableList<DlcUpdateRowPlanner.UpdateRow> rows = FXCollections.observableArrayList();
    private final ListView<DlcUpdateRowPlanner.UpdateRow> historyView = new ListView<>(rows);
    private final ScrollPane changelogPane = new ScrollPane();
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private boolean busy;

    private volatile List<DlcUpdatePackage> lastApplyChain = List.of();
    private volatile boolean downloadCanceled;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private boolean rolledBackMode;
    private String baseVersion = "";
    private String currentVersion = "";
    private String rollbackFrom = "";

    public DlcFxLocalUpdateTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
        progressBar.managedProperty().bind(progressBar.visibleProperty());
        changelogPane.setFitToWidth(true);
        DlcFxStyle.add(changelogPane, "dlc-rich-text-pane");
        setChangelogText("");
        setOptionalHeaderText(rollbackInfoLabel, "");
        setOptionalHeaderText(updateHintLabel, "");
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.local_update"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        refreshBtn.setOnAction(e -> checkLocalAsync());
        importBtn.setOnAction(e -> importPackage());
        importUrlBtn.setOnAction(e -> showImportUrlDialog());
        checkPackUpdateBtn.setOnAction(e -> checkPackUpdateAsync());
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(checkPackUpdateBtn);

        DlcFxStyle.title(versionLabel);
        DlcFxStyle.muted(rollbackInfoLabel);
        DlcFxStyle.muted(updateHintLabel);
        VBox topBox = new VBox(2, versionLabel, rollbackInfoLabel, updateHintLabel);
        DlcFxStyle.toolbar(topBox);
        DlcFxStyle.add(topBox, "dlc-update-header");
        root.setTop(topBox);

        historyView.setCellFactory(param -> new UpdateRowCell());
        historyView.setSelectionModel(null);
        DlcFxStyle.add(historyView, "dlc-update-list");

        Label historyHeader = new Label(DlcFxI18n.tr("dlc_manager.update.version_list_header"));
        DlcFxStyle.title(historyHeader);
        VBox leftBox = new VBox(6, historyHeader, DlcFxListViewSupport.wrap(historyView));
        leftBox.setPadding(new Insets(0, 4, 0, 0));
        leftBox.setMinWidth(0);
        VBox.setVgrow(leftBox.getChildren().get(1), Priority.ALWAYS);

        Label changelogHeader = new Label(DlcFxI18n.tr("dlc_manager.update.changelog_header"));
        DlcFxStyle.title(changelogHeader);
        VBox rightBox = new VBox(6, changelogHeader, changelogPane);
        rightBox.setMinWidth(0);
        changelogPane.setMinWidth(0);
        VBox.setVgrow(changelogPane, Priority.ALWAYS);
        rightBox.setPadding(new Insets(0, 0, 0, 4));

        HBox centerBox = new HBox(10, leftBox, rightBox);
        leftBox.prefWidthProperty().bind(centerBox.widthProperty().subtract(42).divide(2));
        rightBox.prefWidthProperty().bind(centerBox.widthProperty().subtract(42).divide(2));
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        HBox actionPanel = new HBox(8, refreshBtn, importBtn, importUrlBtn, checkPackUpdateBtn, progressBar, statusLabel);
        actionPanel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);

        tab.setContent(root);
        updateActionDisableState();
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        rows.clear();
        setChangelogText("");
        versionLabel.setText(DlcFxI18n.tr("dlc_manager.update.version_empty"));
        setOptionalHeaderText(rollbackInfoLabel, "");
        setOptionalHeaderText(updateHintLabel, "");
        lastApplyChain = List.of();
        setBusy(false, null);
        updateActionDisableState();
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
                DlcUpdateRuntime.ClientUpdateSnapshot snapshot = DlcUpdateRuntime.loadClientSnapshot(DlcPaths.dlcRoot());
                DlcSelfUpdateService.SelfUpdateHistory selfHistory = DlcSelfUpdateService.loadHistory();
                DlcUpdateRowPlanner.PlanResult plan = DlcUpdateRowPlanner.planClientRows(
                    snapshot.history,
                    selfHistory,
                    snapshot.updateCheck,
                    snapshot.rolledBackMode,
                    snapshot.baseVersion,
                    snapshot.currentVersion
                );
                Platform.runLater(() -> {
                    baseVersion = snapshot.baseVersion;
                    currentVersion = snapshot.currentVersion;
                    rollbackFrom = snapshot.rollbackFrom == null ? "" : snapshot.rollbackFrom;
                    rolledBackMode = snapshot.rolledBackMode;
                    rows.setAll(plan.rows);
                    renderVersionHeader(plan.hasVisibleUpdates);
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    statusLabel.setText(plan.hasVisibleUpdates
                        ? DlcFxI18n.tr("dlc_manager.update.available_count", visibleUpdateCount(plan.rows))
                        : DlcFxI18n.tr("dlc_manager.update.local_latest"));
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
        importPackageAsync(file.toPath());
    }

    private void importPackageAsync(Path zipPath) {
        ImportValidationResult validation = DlcUpdateService.validateImportPackage(zipPath);
        if (!validation.isValid()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.import_validation_failed",
                DlcUpdateService.toImportValidationMessage(validation)));
            return;
        }
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.importing"));
        progressBar.setProgress(-1);
        Thread thread = new Thread(() -> {
            try {
                boolean ok = DlcUpdateService.importUpdatePackage(zipPath);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (!ok) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.import_invalid"));
                        return;
                    }
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.import_done"));
                    checkLocalAsync();
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

    private void showImportUrlDialog() {
        if (!requireGameDir()) {
            return;
        }
        TextField urlField = new TextField();
        urlField.setPromptText(DlcFxI18n.tr("dlc_manager.update.url_prompt"));
        urlField.setPrefWidth(460);
        Label error = new Label();
        DlcFxStyle.muted(error);
        VBox content = new VBox(10, urlField, error);
        Button[] downloadButton = new Button[1];
        DlcFxModalOverlay.show(scene(), DlcFxI18n.tr("dlc_manager.update.import_url_title"), content, () -> {
            String url = urlField.getText() == null ? "" : urlField.getText().trim();
            if (url.isBlank()) {
                error.setText(DlcFxI18n.tr("dlc_manager.update.url_empty"));
                return;
            }
            showUrlDownloadProgress(url);
        }, DlcFxI18n.tr("dlc_manager.update.url_download"), () -> {
        }, DlcFxI18n.tr("dlc_manager.update.cancel"), button -> {
            downloadButton[0] = button;
            button.disableProperty().bind(urlField.textProperty().isEmpty());
        });
    }

    private void showUrlDownloadProgress(String url) {
        Label detail = new Label(DlcFxI18n.tr("dlc_manager.update.url_downloading"));
        DlcFxStyle.muted(detail);
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        Label percent = new Label("0%");
        HBox progress = new HBox(8, percent, bar);
        progress.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(12, detail, progress);
        StackPane overlay = DlcFxModalOverlay.showAndGetOverlay(
            scene(),
            DlcFxI18n.tr("dlc_manager.update.import_url_title"),
            content,
            null,
            DlcFxI18n.tr("dlc_manager.common.close"),
            () -> downloadCanceled = true,
            DlcFxI18n.tr("dlc_manager.common.cancel_download"),
            button -> {
                button.setDisable(true);
                button.setVisible(false);
                button.setManaged(false);
            }
        );
        downloadCanceled = false;
        Thread thread = new Thread(() -> {
            Path downloaded = null;
            try {
                downloaded = downloadToUpdateDir(url, (done, total) -> Platform.runLater(() -> {
                    double progressValue = total > 0 ? done / (double) total : 0d;
                    bar.setProgress(progressValue);
                    percent.setText(Math.round(Math.max(0d, Math.min(1d, progressValue)) * 100d) + "%");
                    detail.setText(DlcFxI18n.tr("dlc_manager.update.url_downloading"));
                }));
                Path finalDownloaded = downloaded;
                Platform.runLater(() -> {
                    removeOverlay(overlay);
                    importPackageAsync(finalDownloaded);
                });
            } catch (Throwable t) {
                Path toDelete = downloaded;
                if (toDelete != null) {
                    try {
                        Files.deleteIfExists(toDelete);
                    } catch (IOException ignored) {
                    }
                }
                Platform.runLater(() -> {
                    removeOverlay(overlay);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.url_failed", safeMessage(t)));
                });
            }
        }, "dlc-update-url-download");
        thread.setDaemon(true);
        thread.start();
    }

    private Path downloadToUpdateDir(String url, DlcDownloadService.ProgressListener listener) throws IOException {
        Path updateDir = DlcUpdateService.getUpdateDir();
        if (updateDir == null) {
            throw new IOException("missing dlc root");
        }
        Files.createDirectories(updateDir);
        String fileName = DlcDownloadService.suggestFileName(url);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(url));
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "update.zip";
        } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        return DlcDownloadService.download(url, updateDir, fileName, listener, () -> downloadCanceled);
    }

    private void checkPackUpdateAsync() {
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
        String base = normalizeVersion(baseVersion);
        String dlc = normalizeVersion(currentVersion);
        Thread thread = new Thread(() -> {
            CheckResult result;
            try {
                result = DlcPackReleaseUpdateService.checkForUpdates(DlcConfig.getModpackUrl(), repoUrl, base, dlc, Side.CLIENT);
            } catch (Throwable t) {
                result = new CheckResult();
                result.error = safeMessage(t);
            }
            CheckResult finalResult = result;
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                setBusy(false, null);
                if (finalResult.error != null && !finalResult.error.isBlank()) {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_check_failed",
                        DlcPackReleaseUpdateService.toCheckFailureMessage(finalResult.error)));
                    return;
                }
                if (!finalResult.hasUpdate()) {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack_update.no_updates"));
                    return;
                }
                showPackUpdateDialog(finalResult);
            });
        }, "dlc-pack-update-check-manual");
        thread.setDaemon(true);
        thread.start();
    }

    private void showPackUpdateDialog(CheckResult result) {
        Label base = new Label(versionTransition(DlcFxI18n.tr("dlc_manager.pack_update.base"), result.currentBase, result.targetBase));
        Label dlc = new Label(versionTransition(DlcFxI18n.tr("dlc_manager.pack_update.dlc"), result.currentDlc, result.targetDlc));
        DlcFxStyle.title(base);
        DlcFxStyle.title(dlc);

        ScrollPane changelog = new ScrollPane();
        changelog.setFitToWidth(true);
        DlcFxStyle.add(changelog, "dlc-rich-text-pane");
        String changelogText = buildPackUpdateChangelog(result);
        changelog.setContent(changelogText.isBlank()
            ? new Label(DlcFxI18n.tr("dlc_manager.update.changelog_prompt"))
            : DlcFxRichTextRenderer.markdown(changelogText));
        changelog.setPrefViewportHeight(260);
        changelog.setPrefWidth(560);

        Label warning = new Label(packUpdateWarning(result));
        warning.setWrapText(true);
        DlcFxStyle.add(warning, "dlc-update-warning");
        warning.setVisible(!warning.getText().isBlank());
        warning.setManaged(warning.isVisible());

        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        Label progressText = new Label();
        DlcFxStyle.muted(progressText);
        HBox progress = new HBox(8, progressText, bar);
        progress.setAlignment(Pos.CENTER_LEFT);
        progress.setVisible(false);
        progress.setManaged(false);

        VBox content = new VBox(12, base, dlc, changelog, warning, progress);
        content.setPrefWidth(580);

        DlcFxModalOverlay.showAndGetOverlay(
            scene(),
            DlcFxI18n.tr("dlc_manager.pack_update.title"),
            content,
            () -> showPackUpdateDownloadProgress(result),
            result.hasLatestGap()
                ? DlcFxI18n.tr("dlc_manager.pack_update.download_reachable")
                : DlcFxI18n.tr("dlc_manager.pack_update.download"),
            () -> downloadCanceled = true,
            DlcFxI18n.tr("dlc_manager.update.cancel"),
            button -> {
                button.setDisable(!result.canDownload());
            }
        );
    }

    private void showPackUpdateDownloadProgress(CheckResult result) {
        if (result == null || !result.canDownload()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.remote_no_assets"));
            return;
        }
        downloadCanceled = false;
        Label progressText = new Label(DlcFxI18n.tr("dlc_manager.pack_update.downloading"));
        DlcFxStyle.muted(progressText);
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        VBox content = new VBox(12, progressText, bar);
        content.setPrefWidth(420);
        StackPane overlay = DlcFxModalOverlay.showAndGetOverlay(
            scene(),
            DlcFxI18n.tr("dlc_manager.pack_update.title"),
            content,
            null,
            DlcFxI18n.tr("dlc_manager.common.close"),
            () -> downloadCanceled = true,
            DlcFxI18n.tr("dlc_manager.common.cancel_download"),
            button -> {
                button.setDisable(true);
                button.setVisible(false);
                button.setManaged(false);
            }
        );
        ProgressListener progress = (stepIndex, stepCount, fileName, downloaded, total, overall) -> Platform.runLater(() -> {
            double value = overall < 0 ? 0 : overall;
            bar.setProgress(value);
            int pct = Math.round((float) Math.max(0d, Math.min(1d, value)) * 100f);
            progressText.setText(pct + "%  " + DlcFxI18n.tr("dlc_manager.pack_update.downloading_file",
                stepIndex + 1, stepCount, safe(fileName)));
        });
        Thread thread = new Thread(() -> {
            try {
                DownloadResult dl = DlcPackReleaseUpdateService.downloadClientPackages(result, progress, () -> downloadCanceled);
                Platform.runLater(() -> {
                    removeOverlay(overlay);
                    if (dl.canceled) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack_update.canceled"));
                        return;
                    }
                    if (!dl.success) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack_update.download_failed",
                            DlcPackReleaseUpdateService.toDownloadFailureMessage(dl.message)));
                        return;
                    }
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack_update.download_success"));
                    checkLocalAsync();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    removeOverlay(overlay);
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack_update.download_failed", safeMessage(t)));
                });
            }
        }, "dlc-pack-release-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void confirmRowAction(DlcUpdateRowPlanner.UpdateRow row) {
        if (row == null || row.action == DlcUpdateRowPlanner.Action.NONE) {
            return;
        }
        switch (row.action) {
            case MANAGER_ROLLBACK -> confirmManagerRollback(row.rollbackTarget);
            case ROLLBACK -> confirmRollback(row.rollbackTarget);
            case REUPDATE -> confirmReupdate(row);
            case UPDATE -> confirmApply(row);
            default -> {
            }
        }
    }

    private void confirmRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.rollback_target_missing"));
            return;
        }
        DlcFxModalOverlay.show(
            scene(),
            DlcFxI18n.tr("dlc_manager.update.manager_title"),
            DlcFxI18n.tr("dlc_manager.update.confirm_rollback", targetVersion),
            () -> startRollbackThread(targetVersion),
            DlcFxI18n.tr("dlc_manager.confirm_yes"),
            () -> {
            },
            DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void confirmManagerRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.rollback_target_missing"));
            return;
        }
        DlcFxModalOverlay.show(
            scene(),
            DlcFxI18n.tr("dlc_manager.update.manager_title"),
            DlcFxI18n.tr("dlc_manager.update.confirm_rollback", targetVersion),
            this::startManagerRollbackThread,
            DlcFxI18n.tr("dlc_manager.confirm_yes"),
            () -> {
            },
            DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void confirmApply(DlcUpdateRowPlanner.UpdateRow row) {
        DlcFxModalOverlay.show(
            scene(),
            DlcFxI18n.tr("dlc_manager.update.manager_title"),
            DlcFxI18n.tr("dlc_manager.update.confirm_apply"),
            () -> resolvePackGuardAndApply(row, false),
            DlcFxI18n.tr("dlc_manager.confirm_yes"),
            () -> {
            },
            DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void confirmReupdate(DlcUpdateRowPlanner.UpdateRow row) {
        String changelog = DlcUpdateService.buildChangelogText(resolveChain(row), currentLang());
        String message = DlcFxI18n.tr("dlc_manager.update.confirm_reapply",
            safe(row.displayOldVersion), safe(row.displayNewVersion));
        if (changelog != null && !changelog.isBlank()) {
            message += "\n\n" + DlcFxI18n.tr("dlc_manager.update.changelog_header") + "\n" + changelog;
        }
        DlcFxModalOverlay.show(
            scene(),
            DlcFxI18n.tr("dlc_manager.update.manager_title"),
            DlcFxRichTextRenderer.markdown(message),
            () -> resolvePackGuardAndApply(row, true),
            DlcFxI18n.tr("dlc_manager.confirm_yes"),
            () -> {
            },
            DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private void resolvePackGuardAndApply(DlcUpdateRowPlanner.UpdateRow row, boolean reapply) {
        List<DlcUpdatePackage> chain = resolveChain(row);
        if (chain.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.chain_missing"));
            return;
        }
        DlcPackUpdateGuard.CheckResult check = DlcPackUpdateGuard.check(DlcPaths.dlcRoot(),
            resolveTargetVersion(chain).base, resolveTargetVersion(chain).dlc);
        if (check == null || !check.hasInstalledPacks() || !check.hasIncompatiblePacks()) {
            startApplyThread(chain, reapply, null, false);
            return;
        }
        DlcFxModalOverlay.show(
            scene(),
            DlcFxI18n.tr("dlc_manager.pack.manager_title"),
            DlcFxI18n.tr("dlc_manager.pack.update_incompatible_confirm", describeIncompatiblePacks(check)),
            () -> startApplyThread(chain, reapply, check, true),
            DlcFxI18n.tr("dlc_manager.confirm_yes"),
            () -> {
            },
            DlcFxI18n.tr("dlc_manager.confirm_no")
        );
    }

    private List<DlcUpdatePackage> resolveChain(DlcUpdateRowPlanner.UpdateRow row) {
        if (row == null || row.meta == null) {
            return List.of();
        }
        return DlcUpdateService.resolveUpdateChainTo(
            row.meta.getBaseOld(),
            row.meta.getDlcOld(),
            row.meta.getBaseNew(),
            row.meta.getDlcNew(),
            true
        );
    }

    private void startApplyThread(List<DlcUpdatePackage> chain, boolean reapply,
                                  DlcPackUpdateGuard.CheckResult packCheck, boolean uninstallIncompatible) {
        if (chain == null || chain.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.chain_missing"));
            return;
        }
        lastApplyChain = new ArrayList<>(chain);
        setBusy(true, reapply ? DlcFxI18n.tr("dlc_manager.update.reapplying") : DlcFxI18n.tr("dlc_manager.update.applying"));
        progressBar.setProgress(-1);
        ApplyProgressListener listener = (status, progress) -> Platform.runLater(() -> {
            if (status != null && !status.isBlank()) {
                statusLabel.setText(status);
            }
            progressBar.setProgress(progress < 0 ? -1 : progress);
        });
        Thread thread = new Thread(() -> {
            DlcPackUpdateCoordinator.UpdateSession packSession = null;
            try {
                DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false, listener);
                if (result != null && result.isSuccess()) {
                    packSession.finishAfterSuccessfulUpdate();
                } else {
                    packSession.restoreAfterFailedUpdate();
                }
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    handleApplyResult(result);
                });
            } catch (Throwable t) {
                DlcPackUpdateCoordinator.UpdateSession session = packSession;
                if (session != null) {
                    try {
                        session.restoreAfterFailedUpdate();
                    } catch (Exception restoreError) {
                        t.addSuppressed(restoreError);
                    }
                }
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, reapply ? "dlc-update-reapply" : "dlc-update-apply");
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
        StringBuilder message = new StringBuilder(DlcFxI18n.tr("dlc_manager.update.missing_desc")).append("\n\n");
        for (String path : missing) {
            if (path != null && !path.isBlank()) {
                message.append("- ").append(path).append('\n');
            }
        }
        DlcFxModalOverlay.show(
            scene(),
            DlcFxI18n.tr("dlc_manager.update.missing_title"),
            DlcFxRichTextRenderer.formatted(message.toString().trim()),
            this::startForceApply,
            DlcFxI18n.tr("dlc_manager.update.force"),
            () -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.missing_cancelled")),
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
        ApplyProgressListener listener = (status, progress) -> Platform.runLater(() -> {
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
        Thread thread = new Thread(() -> {
            try {
                UpdateRollbackResult result = DlcUpdateService.rollbackTo(target);
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

    private void startManagerRollbackThread() {
        setBusy(true, DlcFxI18n.tr("dlc_manager.update.rolling_back"));
        progressBar.setProgress(-1);
        Thread thread = new Thread(() -> {
            boolean ok = DlcSelfUpdateService.rollbackToPrevious();
            Platform.runLater(() -> {
                statusLabel.setText(ok
                    ? DlcFxI18n.tr("dlc_manager.update.rollback_success")
                    : DlcFxI18n.tr("dlc_manager.update.rollback_failed", DlcFxI18n.tr("dlc_manager.update.failed")));
                progressBar.setVisible(false);
                setBusy(false, null);
                if (ok) {
                    checkLocalAsync();
                }
            });
        }, "dlc-selfupdate-rollback");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderVersionHeader(boolean hasVisibleUpdates) {
        String base = displayVersion(baseVersion);
        String dlc = displayVersion(currentVersion);
        versionLabel.setText(DlcFxI18n.tr("dlc_manager.update.current_version", base, dlc));
        setOptionalHeaderText(rollbackInfoLabel, rolledBackMode
            ? DlcFxI18n.tr("dlc_manager.update.rollback_info", safe(rollbackFrom), normalizeVersion(baseVersion) + "+" + normalizeVersion(currentVersion))
            : "");
        setOptionalHeaderText(updateHintLabel, hasVisibleUpdates ? DlcFxI18n.tr("dlc_manager.update.desc") : "");
    }

    private static void setOptionalHeaderText(Label label, String text) {
        String value = text == null ? "" : text;
        label.setText(value);
        boolean visible = !value.isBlank();
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private void setChangelogText(String text) {
        String value = text == null ? "" : text;
        if (value.isBlank()) {
            Label prompt = new Label(DlcFxI18n.tr("dlc_manager.update.changelog_prompt"));
            DlcFxStyle.muted(prompt);
            bindChangelogContentWidth(prompt);
            changelogPane.setContent(prompt);
            return;
        }
        TextFlow flow = DlcFxRichTextRenderer.markdown(value);
        bindChangelogContentWidth(flow);
        changelogPane.setContent(flow);
    }

    private void bindChangelogContentWidth(Region content) {
        content.minWidthProperty().bind(changelogPane.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        content.prefWidthProperty().bind(changelogPane.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        content.maxWidthProperty().bind(changelogPane.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
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

    private String buildPackUpdateChangelog(CheckResult result) {
        if (result == null) {
            return "";
        }
        List<ReleaseStep> steps = result.steps == null ? List.of() : result.steps;
        StringBuilder builder = new StringBuilder();
        appendReleaseSteps(builder, steps, currentLang());
        List<ReleaseStep> unreachable = resolveUnreachableSteps(result, steps);
        if (!unreachable.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(DlcFxI18n.tr("dlc_manager.pack_update.unreachable_changelog")).append("\n\n");
            appendReleaseSteps(builder, unreachable, currentLang());
        }
        return builder.toString();
    }

    private void appendReleaseSteps(StringBuilder builder, List<ReleaseStep> steps, String lang) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (ReleaseStep step : steps) {
            if (step == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(formatReleaseTransition(step));
            String body = step.resolveChangelog(lang);
            if (body != null && !body.isBlank()) {
                builder.append("\n").append(body.trim());
            }
        }
    }

    private List<ReleaseStep> resolveUnreachableSteps(CheckResult result, List<ReleaseStep> downloadableSteps) {
        List<ReleaseStep> fullSteps = result.fullSteps == null ? List.of() : result.fullSteps;
        if (fullSteps.isEmpty() || !result.hasLatestGap()) {
            return List.of();
        }
        int index = 0;
        while (index < fullSteps.size()
            && downloadableSteps != null
            && index < downloadableSteps.size()
            && sameTransition(fullSteps.get(index), downloadableSteps.get(index))) {
            index++;
        }
        if (index >= fullSteps.size()) {
            return List.of();
        }
        return fullSteps.subList(index, fullSteps.size());
    }

    private static boolean sameTransition(ReleaseStep left, ReleaseStep right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeVersion(left.oldBase).equals(normalizeVersion(right.oldBase))
            && normalizeVersion(left.oldDlc).equals(normalizeVersion(right.oldDlc))
            && normalizeVersion(left.newBase).equals(normalizeVersion(right.newBase))
            && normalizeVersion(left.newDlc).equals(normalizeVersion(right.newDlc));
    }

    private String packUpdateWarning(CheckResult result) {
        if (result == null) {
            return "";
        }
        if (result.hasLatestGap()) {
            return result.canDownload()
                ? DlcFxI18n.tr("dlc_manager.pack_update.latest_unreachable",
                    normalizeVersion(result.downloadBase), normalizeVersion(result.downloadDlc))
                    + "\n" + DlcFxI18n.tr("dlc_manager.pack_update.partial_download_hint")
                : DlcFxI18n.tr("dlc_manager.pack_update.latest_unreachable_no_download");
        }
        if (!result.canDownload()) {
            return DlcFxI18n.tr("dlc_manager.pack_update.asset_missing");
        }
        return "";
    }

    private String versionTransition(String label, String current, String target) {
        String from = normalizeVersion(current);
        String to = normalizeVersion(target);
        return label + " " + (from.equals(to) ? from : from + " -> " + to);
    }

    private static String formatReleaseTransition(ReleaseStep step) {
        return normalizeVersion(step.oldBase) + "+" + normalizeVersion(step.oldDlc)
            + " -> " + normalizeVersion(step.newBase) + "+" + normalizeVersion(step.newDlc);
    }

    private static TargetVersion resolveTargetVersion(List<DlcUpdatePackage> packages) {
        String targetBase = null;
        String targetDlc = null;
        if (packages != null) {
            for (DlcUpdatePackage pkg : packages) {
                if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                    continue;
                }
                var version = pkg.getManifest().getVersion();
                String base = normalizeNullable(version.getBase());
                String dlcOld = normalizeNullable(version.getDlcOld());
                String baseNew = normalizeNullable(version.getBaseNew());
                String dlcNew = normalizeNullable(version.getDlcNew());
                targetBase = baseNew == null ? base : baseNew;
                targetDlc = dlcNew == null ? dlcOld : dlcNew;
            }
        }
        return new TargetVersion(normalizeVersion(targetBase), normalizeVersion(targetDlc));
    }

    private String describeIncompatiblePacks(DlcPackUpdateGuard.CheckResult check) {
        if (check == null || check.incompatiblePacks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (DlcPackUpdateGuard.PackStatus status : check.incompatiblePacks) {
            if (status == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(status.identifier());
            if (status.reasons != null && !status.reasons.isEmpty()) {
                builder.append(" (").append(String.join("; ", status.reasons)).append(')');
            }
        }
        return builder.toString();
    }

    private int visibleUpdateCount(List<DlcUpdateRowPlanner.UpdateRow> plannedRows) {
        int count = 0;
        for (DlcUpdateRowPlanner.UpdateRow row : plannedRows == null ? List.<DlcUpdateRowPlanner.UpdateRow>of() : plannedRows) {
            if (row != null && row.action == DlcUpdateRowPlanner.Action.UPDATE) {
                count++;
            }
        }
        return count;
    }

    private void setBusy(boolean value, String status) {
        busy = value;
        updateActionDisableState();
        historyView.refresh();
        if (value) {
            progressBar.setVisible(true);
            if (status != null) {
                statusLabel.setText(status);
            }
        }
    }

    private void updateActionDisableState() {
        boolean disabled = busy || !gameDirSelected;
        refreshBtn.setDisable(disabled);
        importBtn.setDisable(disabled);
        importUrlBtn.setDisable(disabled);
        checkPackUpdateBtn.setDisable(disabled || !hasPackUpdateRepoConfigured());
    }

    private boolean hasPackUpdateRepoConfigured() {
        String repoUrl = DlcConfig.getUpdateGitRepoUrl();
        return repoUrl != null && !repoUrl.isBlank();
    }

    private boolean requireGameDir() {
        if (!gameDirSelected || DlcConfig.getDlcRoot() == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            setBusy(false, null);
            return false;
        }
        return true;
    }

    private static String currentLang() {
        String lang = CrossPlatformRuntime.languageCode();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private Scene scene() {
        return versionLabel.getScene();
    }

    private javafx.stage.Stage primaryStage() {
        return (javafx.stage.Stage) versionLabel.getScene().getWindow();
    }

    private static void removeOverlay(StackPane overlay) {
        if (overlay != null && overlay.getParent() instanceof StackPane root) {
            root.getChildren().remove(overlay);
        }
    }

    private static String displayVersion(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? DEFAULT_VERSION : value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private record TargetVersion(String base, String dlc) {
    }

    private final class UpdateRowCell extends javafx.scene.control.ListCell<DlcUpdateRowPlanner.UpdateRow> {
        private final Label transitionLabel = new Label();
        private final Label tagLabel = new Label();
        private final Button actionButton = new Button();
        private final HBox row = new HBox(10);

        UpdateRowCell() {
            DlcFxStyle.title(transitionLabel);
            transitionLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(transitionLabel, Priority.ALWAYS);
            actionButton.setOnAction(e -> {
                DlcUpdateRowPlanner.UpdateRow item = getItem();
                if (item != null) {
                    confirmRowAction(item);
                }
            });
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().setAll(tagLabel, transitionLabel, actionButton);
            DlcFxStyle.add(row, "dlc-update-row");
            setOnMouseEntered(e -> {
                DlcUpdateRowPlanner.UpdateRow item = getItem();
                setChangelogText(item == null || item.meta == null ? "" : item.meta.resolveChangelog(currentLang()));
            });
        }

        @Override
        protected void updateItem(DlcUpdateRowPlanner.UpdateRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            transitionLabel.setText(describeTransition(item));
            configureTag(item);
            configureAction(item);
            setGraphic(row);
            setText(null);
        }

        private void configureTag(DlcUpdateRowPlanner.UpdateRow item) {
            if (item.type == DlcUpdateRowPlanner.Type.MANAGER) {
                tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_manager"));
                DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_NEUTRAL);
            } else if (item.action == DlcUpdateRowPlanner.Action.UPDATE) {
                tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_available"));
                DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_OK);
            } else if (item.action == DlcUpdateRowPlanner.Action.REUPDATE) {
                tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_rolled_back"));
                DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_WARN);
            } else {
                tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_history"));
                DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_NEUTRAL);
            }
        }

        private void configureAction(DlcUpdateRowPlanner.UpdateRow item) {
            actionButton.getStyleClass().remove(DlcFxStyle.PRIMARY);
            actionButton.getStyleClass().remove(DlcFxStyle.DANGER);
            switch (item.action) {
                case UPDATE -> {
                    actionButton.setText(DlcFxI18n.tr("dlc_manager.update.apply"));
                    DlcFxStyle.primary(actionButton);
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);
                }
                case REUPDATE -> {
                    actionButton.setText(DlcFxI18n.tr("dlc_manager.update.reapply"));
                    DlcFxStyle.primary(actionButton);
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);
                }
                case ROLLBACK, MANAGER_ROLLBACK -> {
                    actionButton.setText(DlcFxI18n.tr("dlc_manager.update.rollback"));
                    DlcFxStyle.danger(actionButton);
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);
                }
                default -> {
                    actionButton.setVisible(false);
                    actionButton.setManaged(false);
                }
            }
                    actionButton.setDisable(busy);
        }

        private String describeTransition(DlcUpdateRowPlanner.UpdateRow item) {
            String label = safe(item.displayOldVersion) + " -> " + safe(item.displayNewVersion);
            if (item.type == DlcUpdateRowPlanner.Type.MANAGER) {
                return DlcFxI18n.tr("dlc_manager.update.manager_prefix") + label;
            }
            DlcUpdateMeta meta = item.meta;
            String sourceBase = meta == null ? null : meta.getBaseOld();
            String sourceDlc = meta == null ? null : meta.getDlcOld();
            String targetBase = meta == null ? null : meta.getBaseNew();
            String targetDlc = meta == null ? null : meta.getDlcNew();
            if (targetBase == null || targetBase.isBlank()) {
                targetBase = sourceBase;
            }
            if (targetDlc == null || targetDlc.isBlank()) {
                targetDlc = sourceDlc;
            }
            boolean baseChanged = !normalizeVersion(sourceBase).equals(normalizeVersion(targetBase));
            boolean dlcChanged = !normalizeVersion(sourceDlc).equals(normalizeVersion(targetDlc));
            if (baseChanged && !dlcChanged) {
                label = DlcFxI18n.tr("dlc_manager.update.base_prefix") + normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase);
            } else if (!baseChanged && dlcChanged) {
                label = DlcFxI18n.tr("dlc_manager.update.dlc_prefix") + normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc);
            }
            return label;
        }
    }
}
