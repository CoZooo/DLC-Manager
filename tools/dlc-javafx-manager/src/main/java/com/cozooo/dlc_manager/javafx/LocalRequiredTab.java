package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.core.DlcConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for the "本地 · Required 必装" tab.
 * <p>
 * Lists every {@code .dc} entry under {@code DLC/required/}, classifies each as
 * <i>缺失下载</i> (file absent from the required directory), <i>已下载未安装</i>
 * (present but not yet copied to its applied-target directories), or <i>已安装</i>,
 * and lets the user download missing files and install them — reusing the
 * encrypted CurseForge / Modrinth / direct-URL resolver via
 * {@link DlcDownloadCandidate#resolve(DlcRequiredEntry)}.
 * <p>
 * Parity with the in-game "Required 必装资源" screen; detection reuses
 * {@link DlcRequiredLoader} (ported from {@code DlcRequiredRuntime}).
 */
final class LocalRequiredTab {

    private final StandalonePlatformProvider provider;

    private final ObservableList<DlcRequiredEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("请先在「本地 · DLC 管理」中选择游戏目录");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button("刷新必装列表");
    private final Button downloadMissingBtn = new Button("下载全部缺失");
    private final Button downloadSelectedBtn = new Button("下载选中项");
    private final Button applyBtn = new Button("安装到目标目录");
    private final Label countLabel = new Label("");

    LocalRequiredTab(StandalonePlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
    }

    Tab build() {
        Tab tab = new Tab("本地 · Required 必装");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: action bar ───────────────────────────────────────
        refreshBtn.setOnAction(e -> refreshAsync());
        downloadMissingBtn.setOnAction(e -> downloadMissingAsync());
        downloadSelectedBtn.disableProperty().bind(refreshBtn.disabledProperty());
        applyBtn.disableProperty().bind(refreshBtn.disabledProperty());
        applyBtn.setOnAction(e -> installAsync());

        HBox topBox = new HBox(8, refreshBtn, downloadMissingBtn, downloadSelectedBtn, applyBtn, countLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: entry list ────────────────────────────────────
        ListView<DlcRequiredEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new RequiredEntryCell());
        listView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox centerBox = new VBox(listView);
        centerBox.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(centerBox);

        // Download the user-selected entries (any that are still missing),
        // expanded with their transitive dependencies so a selected pack pulls
        // in its declared deps too.
        downloadSelectedBtn.setOnAction(e -> {
            java.util.List<DlcRequiredEntry> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                statusLabel.setText("请先在列表中选择条目");
                return;
            }
            java.util.List<DlcRequiredEntry> missingSelected = new java.util.ArrayList<>();
            for (DlcRequiredEntry entry : selected) {
                if (entry != null && !DlcRequiredLoader.isDownloaded(entry)) {
                    missingSelected.add(entry);
                }
            }
            if (missingSelected.isEmpty()) {
                statusLabel.setText("所选条目均已下载");
                return;
            }
            java.util.List<DlcRequiredEntry> toDownload = DlcRequiredLoader.collectMissingDownloads(
                    DlcRequiredLoader.collectDependencies(missingSelected, entries));
            downloadAll(toDownload);
        });

        // ── Bottom: progress + status ─────────────────────────────
        HBox bottomBox = new HBox(8, progressBar, statusLabel);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(8));
        root.setBottom(bottomBox);

        tab.setContent(root);
        return tab;
    }

    /** Called by DlcManagerApp when the game directory changes. */
    void onGameDirChanged() {
        entries.clear();
        countLabel.setText("");
        statusLabel.setText("游戏目录已更新，点击「刷新必装列表」");
        refreshBtn.setDisable(false);
    }

    // ───────────────────────────────────────────────────────────────
    //  Refresh
    // ───────────────────────────────────────────────────────────────

    private void refreshAsync() {
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("加载必装资源列表…");
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRequiredEntry> loaded = DlcRequiredLoader.load(dlcRoot);
                int missing = 0;
                int needsApply = 0;
                for (DlcRequiredEntry e : loaded) {
                    if (!DlcRequiredLoader.isDownloaded(e)) {
                        missing++;
                    } else if (DlcRequiredLoader.needsApply(e)) {
                        needsApply++;
                    }
                }
                final int fMissing = missing;
                final int fNeedsApply = needsApply;
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    countLabel.setText(String.format("共 %d 项，缺失 %d 项，待安装 %d 项", loaded.size(), fMissing, fNeedsApply));
                    statusLabel.setText(fMissing == 0 && fNeedsApply == 0
                            ? "全部就绪"
                            : (fMissing > 0 ? "存在缺失项，可点击「下载全部缺失」" : "存在待安装项，可点击「安装到目标目录」"));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText("加载失败: " + safeMessage(t));
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                });
            }
        }, "dlc-required-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    // ───────────────────────────────────────────────────────────────
    //  Download
    // ───────────────────────────────────────────────────────────────

    private void downloadMissingAsync() {
        List<DlcRequiredEntry> missing = DlcRequiredLoader.collectMissingDownloads(entries);
        if (missing.isEmpty()) {
            statusLabel.setText("无缺失项需要下载");
            return;
        }
        // Expand transitive dependencies declared in each entry's [dependencies]
        // block (mirrors DlcRequiredRuntime.collectDependencies), then keep only
        // those still missing — so downloading a required pack pulls in its
        // declared deps that are also absent.
        List<DlcRequiredEntry> expanded = DlcRequiredLoader.collectDependencies(missing, entries);
        List<DlcRequiredEntry> toDownload = DlcRequiredLoader.collectMissingDownloads(expanded);
        downloadAll(toDownload);
    }

    private void downloadAll(List<DlcRequiredEntry> toDownload) {
        downloadMissingBtn.setDisable(true);
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        Thread thread = new Thread(() -> {
            int total = toDownload.size();
            AtomicLong done = new AtomicLong();
            List<String> errors = new ArrayList<>();
            for (DlcRequiredEntry entry : toDownload) {
                try {
                    downloadOne(entry);
                } catch (Throwable t) {
                    errors.add(safeName(entry) + ": " + safeMessage(t));
                }
                long d = done.incrementAndGet();
                Platform.runLater(() -> progressBar.setProgress((double) d / total));
            }
            Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append("完成 ").append(done.get()).append('/').append(total);
                if (!errors.isEmpty()) {
                    sb.append("，失败 ").append(errors.size()).append(" 项");
                }
                statusLabel.setText(sb.toString());
                progressBar.setVisible(false);
                downloadMissingBtn.setDisable(false);
                refreshBtn.setDisable(false);
                // refresh list to reflect new files
                refreshAsync();
            });
        }, "dlc-required-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadOne(DlcRequiredEntry entry) throws IOException, InterruptedException {
        String effectiveName = DlcRequiredLoader.resolveEffectiveFileName(entry);
        Path requiredDir = entry.getRequiredDir();
        if (requiredDir == null) {
            throw new IOException("无法定位 required 目录: " + safeName(entry));
        }
        Path target = effectiveName != null && !effectiveName.isBlank()
                ? requiredDir.resolve(effectiveName)
                : null;
        if (target != null && Files.exists(target)) {
            return; // already present
        }
        List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(entry);
        if (candidates.isEmpty()) {
            throw new IOException("无可用下载源");
        }
        Files.createDirectories(requiredDir);
        IOException last = null;
        for (DlcDownloadCandidate c : candidates) {
            try {
                downloadCandidate(c, requiredDir, effectiveName, target);
                return;
            } catch (IOException ioe) {
                last = ioe;
            }
        }
        throw last != null ? last : new IOException("下载失败: " + safeName(entry));
    }

    private void downloadCandidate(DlcDownloadCandidate candidate, Path requiredDir,
                                   String preferredName, Path presetTarget)
            throws IOException, InterruptedException {
        String fileName = firstNonBlank(preferredName,
                candidate.fileNameOverride,
                DlcDownloadUtil.extractFileNameFromUrl(candidate.url),
                presetTarget == null ? null : presetTarget.getFileName().toString());
        fileName = DlcDownloadUtil.sanitizeFileName(fileName);
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("无法确定下载文件名");
        }
        Path finalPath = requiredDir.resolve(fileName);
        Path temp = requiredDir.resolve(fileName + ".part");
        Files.deleteIfExists(temp);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(candidate.url))
                    .timeout(Duration.ofMinutes(10))
                    .header("User-Agent", "DLC-Manager-JavaFX")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                in.transferTo(out);
            }
            // Hash verification
            if (candidate.hashType != null && !candidate.hashType.isBlank()
                    && candidate.hashValue != null && !candidate.hashValue.isBlank()) {
                String actual = DlcDownloadUtil.computeHashHex(temp, candidate.hashType);
                String expected = DlcDownloadUtil.normalizeHash(candidate.hashValue);
                if (actual == null || !actual.equalsIgnoreCase(expected)) {
                    throw new IOException("校验失败: " + candidate.hashType + " 不匹配");
                }
            }
            try {
                Files.move(temp, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temp, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Install (copy downloaded file → applied targets)
    // ───────────────────────────────────────────────────────────────

    private void installAsync() {
        List<DlcRequiredEntry> targets = DlcRequiredLoader.collectNeedsApply(entries);
        if (targets.isEmpty()) {
            // Fall back to re-installing every downloaded entry on explicit request.
            for (DlcRequiredEntry e : entries) {
                if (e != null && DlcRequiredLoader.isDownloaded(e)) {
                    targets.add(e);
                }
            }
            if (targets.isEmpty()) {
                statusLabel.setText("没有可安装的项（请先下载）");
                return;
            }
        }
        final List<DlcRequiredEntry> finalTargets = targets;
        // Confirm before mutating game directories.
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("将把 %d 项必装资源复制到各自的目标目录（覆盖已存在文件）。是否继续？", finalTargets.size()));
        confirm.setTitle("安装必装资源");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        applyBtn.setDisable(true);
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setText("安装中…");
        Thread thread = new Thread(() -> {
            int total = finalTargets.size();
            AtomicLong done = new AtomicLong();
            List<String> errors = new ArrayList<>();
            for (DlcRequiredEntry entry : finalTargets) {
                try {
                    installOne(entry);
                } catch (Throwable t) {
                    errors.add(safeName(entry) + ": " + safeMessage(t));
                }
                long d = done.incrementAndGet();
                Platform.runLater(() -> progressBar.setProgress((double) d / total));
            }
            Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append("已安装 ").append(done.get()).append('/').append(total);
                if (!errors.isEmpty()) {
                    sb.append("，失败 ").append(errors.size()).append(" 项");
                }
                statusLabel.setText(sb.toString());
                progressBar.setVisible(false);
                applyBtn.setDisable(false);
                refreshBtn.setDisable(false);
                refreshAsync();
            });
        }, "dlc-required-install");
        thread.setDaemon(true);
        thread.start();
    }

    private void installOne(DlcRequiredEntry entry) throws IOException {
        String fileName = DlcRequiredLoader.resolveEffectiveFileName(entry);
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("无法确定文件名");
        }
        Path source = entry.resolveRequiredPath(fileName);
        if (source == null || !Files.exists(source)) {
            throw new IOException("源文件不存在，请先下载");
        }
        List<Path> appliedTargets = entry.resolveAppliedTargets();
        if (appliedTargets.isEmpty()) {
            return; // nothing to install (e.g. config-type with no target)
        }
        for (Path target : appliedTargets) {
            Path targetFile = resolveTargetFile(target, fileName);
            if (targetFile == null) {
                continue;
            }
            Files.createDirectories(targetFile.getParent());
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path resolveTargetFile(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
    }

    // ───────────────────────────────────────────────────────────────
    //  Cell renderer
    // ───────────────────────────────────────────────────────────────

    private final class RequiredEntryCell extends javafx.scene.control.ListCell<DlcRequiredEntry> {
        private final Label nameLabel = new Label();
        private final Label statusTag = new Label();
        private final Label detailLabel = new Label();
        private final HBox row = new HBox(8, nameLabel, statusTag, detailLabel);

        RequiredEntryCell() {
            nameLabel.setStyle("-fx-font-weight: bold;");
            statusTag.setStyle("-fx-text-fill: white; -fx-background-color: gray; -fx-background-radius: 3; -fx-padding: 0 4;");
            detailLabel.setStyle("-fx-text-fill: #666;");
        }

        @Override
        protected void updateItem(DlcRequiredEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                nameLabel.setText(safeName(item));
                boolean downloaded = DlcRequiredLoader.isDownloaded(item);
                boolean needsApply = downloaded && DlcRequiredLoader.needsApply(item);
                if (!downloaded) {
                    statusTag.setText(" 缺失下载 ");
                    statusTag.setStyle("-fx-text-fill: white; -fx-background-color: #c0392b; -fx-background-radius: 3; -fx-padding: 0 4;");
                } else if (needsApply) {
                    statusTag.setText(" 待安装 ");
                    statusTag.setStyle("-fx-text-fill: white; -fx-background-color: #e67e22; -fx-background-radius: 3; -fx-padding: 0 4;");
                } else {
                    statusTag.setText(" 已安装 ");
                    statusTag.setStyle("-fx-text-fill: white; -fx-background-color: #27ae60; -fx-background-radius: 3; -fx-padding: 0 4;");
                }
                String fileName = item.getResolvedFileName();
                detailLabel.setText(fileName == null || fileName.isBlank() ? "(文件名未知)" : fileName);
                StringBuilder tip = new StringBuilder();
                tip.append("标识: ").append(item.getIdentifier() == null ? "" : item.getIdentifier());
                Path requiredDir = item.getRequiredDir();
                if (requiredDir != null) {
                    tip.append("\n目录: ").append(requiredDir);
                }
                if (!item.getDirectUrls().isEmpty()) {
                    tip.append("\n直链: ").append(item.getDirectUrls().get(0));
                }
                if (item.getCurseforgeProjectId() != null && !item.getCurseforgeProjectId().isBlank()) {
                    tip.append("\nCurseForge: ").append(item.getCurseforgeProjectId());
                }
                if (item.getModrinthProjectId() != null && !item.getModrinthProjectId().isBlank()) {
                    tip.append("\nModrinth: ").append(item.getModrinthProjectId());
                }
                String applied = item.getAppliedTargetRaw();
                if (applied != null && !applied.isBlank()) {
                    tip.append("\n目标: ").append(applied);
                }
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────

    private static String safeName(DlcRequiredEntry entry) {
        String name = entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry.getRequiredFileName();
        }
        if (name == null || name.isBlank()) {
            name = entry.getIdentifier();
        }
        return name == null ? "(未命名)" : name;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
