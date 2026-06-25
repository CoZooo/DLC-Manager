package com.cozooo.dlc_manager.javafx;

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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for the "本地 · 热更新" tab.
 * <p>
 * This is the standalone counterpart of the in-game
 * {@code DlcUpdateManagerScreen} + {@code DlcModpackUpdateScreen} and is the
 * direct fix for the two reported bugs:
 * <ul>
 *   <li><b>"无法检测dlc内的更新内容"</b> — Layer A: scans the {@code update/}
 *       directory for {@code .zip} packages via
 *       {@link DlcUpdateService#checkForUpdates()}, resolves the applicable
 *       update chain, shows the version transition + changelog, and applies /
 *       rolls back / imports packages — all on the local game directory.</li>
 *   <li><b>"没有同步远程仓库校验本地mod"</b> — Layer B: queries the configured
 *       GitHub/Gitea/GitCode/GitLab releases API via
 *       {@link DlcPackReleaseUpdateService#checkForUpdates} and downloads the
 *       matching {@code Client_*.zip} assets into {@code update/}, then hands
 *       off to Layer A to verify and apply.</li>
 * </ul>
 * Detection reuses the engine ported 1:1 from {@code common} (only the four
 * {@code Component} display helpers were converted to {@code String}); there is
 * no {@code net.minecraft} dependency.
 */
final class LocalUpdateTab {

    private final StandalonePlatformProvider provider;

    // ── Version header ──────────────────────────────────────────────
    private final Label versionLabel = new Label("底包：-　当前 DLC：-");

    // ── Action buttons ──────────────────────────────────────────────
    private final Button refreshBtn = new Button("检查本地热更新");
    private final Button checkRemoteBtn = new Button("同步远程仓库");
    private final Button downloadRemoteBtn = new Button("下载远程热更新包");
    private final Button cancelDownloadBtn = new Button("取消下载");
    private final Button applyBtn = new Button("应用更新");
    private final Button rollbackBtn = new Button("回溯选中版本");
    private final Button importBtn = new Button("导入更新包");

    // ── Center: history / available-update list ─────────────────────
    private final ObservableList<DlcUpdateMeta> history = FXCollections.observableArrayList();
    private final ListView<DlcUpdateMeta> historyView = new ListView<>(history);

    // ── Changelog panel ─────────────────────────────────────────────
    private final TextArea changelogArea = new TextArea();
    private final Label statusLabel = new Label("请先在「本地 · DLC 管理」中选择游戏目录");
    private final ProgressBar progressBar = new ProgressBar(0);

    // Live update state (touched only on the FX thread unless noted).
    private CheckResult lastRemoteCheck;
    private volatile boolean downloadCanceled;

    LocalUpdateTab(StandalonePlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
        changelogArea.setEditable(false);
        changelogArea.setWrapText(true);
        changelogArea.setPromptText("更新日志将显示在此处");
        cancelDownloadBtn.setVisible(false);
    }

    Tab build() {
        Tab tab = new Tab("本地 · 热更新");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: version header + action bar ──────────────────────
        versionLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 4 0;");

        refreshBtn.setOnAction(e -> checkLocalAsync());
        checkRemoteBtn.setOnAction(e -> checkRemoteAsync());
        downloadRemoteBtn.setOnAction(e -> downloadRemoteAsync());
        downloadRemoteBtn.setDisable(true);
        cancelDownloadBtn.setOnAction(e -> downloadCanceled = true);
        applyBtn.setOnAction(e -> applySelectedAsync());
        rollbackBtn.setOnAction(e -> rollbackSelectedAsync());
        importBtn.setOnAction(e -> importPackage());

        HBox actionBar = new HBox(8,
                refreshBtn, checkRemoteBtn, downloadRemoteBtn, cancelDownloadBtn,
                applyBtn, rollbackBtn, importBtn);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        VBox topBox = new VBox(4, versionLabel, actionBar);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: history list (left) + changelog (right) ──────
        historyView.setCellFactory(param -> new UpdateRowCell());
        historyView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
        historyView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> showChangelogFor(sel));

        Label historyHeader = new Label("可更新 / 历史版本");
        historyHeader.setStyle("-fx-font-weight: bold;");
        VBox leftBox = new VBox(4, historyHeader, historyView);
        VBox.setVgrow(historyView, Priority.ALWAYS);
        leftBox.setPadding(new Insets(0, 4, 0, 8));

        Label changelogHeader = new Label("更新日志");
        changelogHeader.setStyle("-fx-font-weight: bold;");
        VBox rightBox = new VBox(4, changelogHeader, changelogArea);
        VBox.setVgrow(changelogArea, Priority.ALWAYS);
        rightBox.setPadding(new Insets(0, 8, 0, 4));

        HBox centerBox = new HBox(8, leftBox, rightBox);
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        centerBox.setPadding(new Insets(0, 0, 0, 0));
        root.setCenter(centerBox);

        // ── Bottom: progress + status ────────────────────────────
        HBox bottomBox = new HBox(8, progressBar, statusLabel);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(8));
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    /** Called by DlcManagerApp when the game directory changes. */
    void onGameDirChanged() {
        history.clear();
        changelogArea.clear();
        versionLabel.setText("底包：-　当前 DLC：-");
        lastRemoteCheck = null;
        downloadRemoteBtn.setDisable(true);
        statusLabel.setText("游戏目录已更新，点击「检查本地热更新」");
    }

    // ───────────────────────────────────────────────────────────────
    //  Layer A — local update detection
    // ───────────────────────────────────────────────────────────────

    private void checkLocalAsync() {
        if (!requireGameDir()) {
            return;
        }
        setBusy(true, "扫描本地热更新包…");
        progressBar.setProgress(-1);
        Thread thread = new Thread(() -> {
            try {
                // Layer A: scan update/*.zip, compare manifest vs installed
                // version, resolve the applicable update chain.
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
                // Always show history so the user can roll back.
                rows.addAll(DlcUpdateService.listUpdateHistory());
                Platform.runLater(() -> {
                    history.setAll(rows);
                    renderVersionHeader(result);
                    if (result.hasUpdates()) {
                        statusLabel.setText(String.format(
                                "发现 %d 个可用热更新，可选择后点击「应用更新」",
                                result.getUpdates().size()));
                    } else {
                        statusLabel.setText("已是最新版本（无可用本地热更新）");
                    }
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("检查失败: " + safeMessage(t));
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
            statusLabel.setText("请先在列表中选择一个要应用的版本");
            return;
        }
        if (!confirm("应用热更新",
                String.format("将应用更新到 DLC %s，期间会增删改文件，是否继续？",
                        safe(selected.getDlcNew())))) {
            return;
        }
        setBusy(true, "应用更新中…");
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
                        statusLabel.setText("无法解析到该版本的更新链（包可能缺失）");
                        progressBar.setVisible(false);
                        setBusy(false, null);
                    });
                    return;
                }
                UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false, listener);
                Platform.runLater(() -> {
                    statusLabel.setText(describeApply(result));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (result.isSuccess()) {
                        // Refresh version header + history after a successful apply.
                        checkLocalAsync();
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("应用失败: " + safeMessage(t));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-apply");
        thread.setDaemon(true);
        thread.start();
    }

    private void rollbackSelectedAsync() {
        DlcUpdateMeta selected = historyView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("请先在列表中选择一个要回溯的版本");
            return;
        }
        String target = safe(selected.getDlcNew());
        if (target.isBlank()) {
            target = safe(selected.getDlcOld());
        }
        if (target.isBlank()) {
            statusLabel.setText("无法确定回溯目标版本");
            return;
        }
        if (!confirm("回溯版本",
                String.format("将回溯到 DLC %s，部分文件操作会在退出后生效，是否继续？", target))) {
            return;
        }
        setBusy(true, "回溯中…");
        progressBar.setProgress(-1);
        final String finalTarget = target;
        Thread thread = new Thread(() -> {
            try {
                UpdateRollbackResult result = DlcUpdateService.rollbackTo(finalTarget);
                Platform.runLater(() -> {
                    statusLabel.setText(result.isSuccess()
                            ? "已回溯到 " + safe(result.getVersion())
                            : "回溯失败: " + safe(result.getMessage()));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (result.isSuccess()) {
                        checkLocalAsync();
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("回溯失败: " + safeMessage(t));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-rollback");
        thread.setDaemon(true);
        thread.start();
    }

    private void importPackage() {
        if (!requireGameDir()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择热更新包 (.zip)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("热更新包", "*.zip"));
        java.io.File file = chooser.showOpenDialog(primaryStage());
        if (file == null) {
            return;
        }
        Path zipPath = file.toPath();
        // Validate before importing so we can show a precise reason.
        ImportValidationResult validation = DlcUpdateService.validateImportPackage(zipPath);
        if (!validation.isValid()) {
            statusLabel.setText("导入校验失败: " + DlcUpdateService.toImportValidationMessage(validation));
            return;
        }
        setBusy(true, "导入更新包…");
        progressBar.setProgress(-1);
        Thread thread = new Thread(() -> {
            try {
                boolean ok = DlcUpdateService.importUpdatePackage(zipPath);
                Platform.runLater(() -> {
                    statusLabel.setText(ok
                            ? "导入成功，请点击「检查本地热更新」"
                            : "导入失败：包格式无效或已存在");
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    if (ok) {
                        checkLocalAsync();
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("导入失败: " + safeMessage(t));
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            }
        }, "dlc-update-import");
        thread.setDaemon(true);
        thread.start();
    }

    // ───────────────────────────────────────────────────────────────
    //  Layer B — remote repo sync (download release packages)
    // ───────────────────────────────────────────────────────────────

    private void checkRemoteAsync() {
        if (!requireGameDir()) {
            return;
        }
        String repoUrl = DlcConfig.getUpdateGitRepoUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            statusLabel.setText("未配置远程仓库地址（updateGitRepoClient），无法同步");
            return;
        }
        setBusy(true, "查询远程仓库版本…");
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
                        statusLabel.setText("远程查询失败: "
                                + DlcPackReleaseUpdateService.toCheckFailureMessage(result.error));
                        downloadRemoteBtn.setDisable(true);
                    } else if (result.hasUpdate()) {
                        statusLabel.setText(String.format(
                                "远程有更新：%s -> %s（可下载）",
                                formatVersion(result.currentBase, result.currentDlc),
                                formatVersion(result.targetBase, result.targetDlc)));
                        changelogArea.setText(result.resolveCombinedChangelog(currentLang()));
                        downloadRemoteBtn.setDisable(!result.canDownload());
                    } else {
                        statusLabel.setText("远程仓库已是最新版本");
                        downloadRemoteBtn.setDisable(true);
                    }
                    progressBar.setVisible(false);
                    setBusy(false, null);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("远程查询失败: " + safeMessage(t));
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
            statusLabel.setText("请先点击「同步远程仓库」检测更新");
            return;
        }
        if (!result.canDownload()) {
            statusLabel.setText("远程缺少可下载的热更新包资源");
            return;
        }
        downloadCanceled = false;
        setBusy(true, "正在下载热更新包…");
        progressBar.setProgress(0);
        cancelDownloadBtn.setVisible(true);
        final CancelChecker cancelChecker = () -> downloadCanceled;
        final ProgressListener progress = (stepIndex, stepCount, fileName, downloaded, total, overall) ->
                Platform.runLater(() -> {
                    progressBar.setProgress(overall < 0 ? -1 : overall);
                    if (stepCount > 0) {
                        statusLabel.setText(String.format("正在下载（%d/%d）：%s",
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
                        statusLabel.setText("下载已取消");
                    } else if (dl.success) {
                        statusLabel.setText(String.format(
                                "下载完成，已导入 %d 个包，请点击「检查本地热更新」",
                                dl.files.size()));
                        // Auto re-check so the freshly imported packages appear.
                        checkLocalAsync();
                    } else {
                        statusLabel.setText("下载失败: "
                                + DlcPackReleaseUpdateService.toDownloadFailureMessage(dl.message));
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    cancelDownloadBtn.setVisible(false);
                    progressBar.setVisible(false);
                    setBusy(false, null);
                    statusLabel.setText("下载失败: " + safeMessage(t));
                });
            }
        }, "dlc-update-download");
        thread.setDaemon(true);
        thread.start();
    }

    // ───────────────────────────────────────────────────────────────
    //  Rendering helpers
    // ───────────────────────────────────────────────────────────────

    private void renderVersionHeader(UpdateCheckResult result) {
        DlcUpdateVersion v = result.getCurrent();
        String rollbackFrom = v.getRollbackFrom();
        String base = formatVersionHeader(v.getBase(), null);
        String dlc = formatVersionHeader(v.getDlc(), null);
        if (rollbackFrom != null && !rollbackFrom.isBlank()) {
            versionLabel.setText(String.format("底包：%s　当前 DLC：%s　（已回溯：%s）", base, dlc, rollbackFrom));
        } else {
            versionLabel.setText(String.format("底包：%s　当前 DLC：%s", base, dlc));
        }
    }

    private void showChangelogFor(DlcUpdateMeta meta) {
        if (meta == null) {
            changelogArea.clear();
            return;
        }
        String text = resolveChangelogText(meta);
        changelogArea.setText(text == null ? "" : text);
    }

    private String resolveChangelogText(DlcUpdateMeta meta) {
        if (meta == null) {
            return "";
        }
        // Both update-package rows and on-disk history rows carry their
        // localized changelog map (populated by metaForPackage / DlcUpdateMeta.load).
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
        // VersionInfo models the transition as base(old) -> baseNew, dlcOld -> dlcNew.
        meta.setBaseOld(info.getBase());
        meta.setBaseNew(info.getBaseNew());
        meta.setDlcOld(info.getDlcOld());
        meta.setDlcNew(info.getDlcNew());
        if (pkg.getManifest().getChangelog() != null) {
            meta.getChangelog().putAll(pkg.getManifest().getChangelog());
        }
        return meta;
    }

    private static String describeApply(UpdateApplyResult result) {
        if (result == null) {
            return "更新结果未知";
        }
        if (result.isSuccess()) {
            StringBuilder sb = new StringBuilder("更新完成。");
            if (result.hasRequiredAdd()) {
                sb.append(" 已新增必装资源。");
            }
            if (result.hasRemoteAdd()) {
                sb.append(" 已新增远程资源。");
            }
            if (result.isHelperScheduled()) {
                sb.append(" 部分文件操作已安排在退出后处理。");
            }
            if (result.isDlcManagerUpdated()) {
                sb.append(" DLC 管理器自身已更新。");
            }
            return sb.toString();
        }
        if (result.isMissing()) {
            return "更新失败：缺失文件 — " + result.getMissingPaths();
        }
        return "更新失败：" + DlcUpdateService.toFailureMessage(result);
    }

    private static String formatVersion(String base, String dlc) {
        String b = base == null || base.isBlank() ? "1.0.0" : base;
        String d = dlc == null || dlc.isBlank() ? "1.0.0" : dlc;
        return b + "+" + d;
    }

    private static String formatVersionHeader(String base, String dlc) {
        if (dlc == null || dlc.isBlank()) {
            return base == null || base.isBlank() ? "1.0.0" : base;
        }
        return base + "+" + dlc;
    }

    private static String currentLang() {
        return Locale.getDefault().toLanguageTag().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private void setBusy(boolean busy, String status) {
        refreshBtn.setDisable(busy);
        checkRemoteBtn.setDisable(busy);
        applyBtn.setDisable(busy);
        rollbackBtn.setDisable(busy);
        importBtn.setDisable(busy);
        // downloadRemoteBtn / cancelDownloadBtn are managed by the download flow.
        if (busy) {
            progressBar.setVisible(true);
            if (status != null) {
                statusLabel.setText(status);
            }
        }
    }

    private boolean requireGameDir() {
        if (DlcConfig.getDlcRoot() == null) {
            statusLabel.setText("请先在「本地 · DLC 管理」中选择游戏目录");
            return false;
        }
        return true;
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                new ButtonType("取消", ButtonData.CANCEL_CLOSE),
                new ButtonType("确定", ButtonData.OK_DONE));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL).getButtonData() == ButtonData.OK_DONE;
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

    // ───────────────────────────────────────────────────────────────
    //  Cell renderer — mirrors DlcUpdateRowPlanner row labels
    // ───────────────────────────────────────────────────────────────

    private final class UpdateRowCell extends javafx.scene.control.ListCell<DlcUpdateMeta> {
        private final Label transitionLabel = new Label();
        private final Label tagLabel = new Label();

        UpdateRowCell() {
            transitionLabel.setStyle("-fx-font-weight: bold;");
            tagLabel.setStyle("-fx-text-fill: white; -fx-background-color: gray; -fx-background-radius: 3; -fx-padding: 0 4;");
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
                    tagLabel.setText(" 历史 ");
                    tagLabel.setStyle("-fx-text-fill: white; -fx-background-color: #7f8c8d; -fx-background-radius: 3; -fx-padding: 0 4;");
                } else {
                    tagLabel.setText(" 可更新 ");
                    tagLabel.setStyle("-fx-text-fill: white; -fx-background-color: #27ae60; -fx-background-radius: 3; -fx-padding: 0 4;");
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
                return "底包：" + baseOld + " -> " + baseNew;
            }
            if (dlcChanged && !baseChanged) {
                return "DLC：" + dlcOld + " -> " + dlcNew;
            }
            String from = formatVersionHeader(baseOld.isBlank() ? null : baseOld, dlcOld);
            String to = formatVersionHeader(baseNew.isBlank() ? null : baseNew, dlcNew);
            return from + " -> " + to;
        }
    }
}
