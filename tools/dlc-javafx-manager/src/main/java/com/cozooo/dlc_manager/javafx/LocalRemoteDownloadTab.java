package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.core.DlcConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
 * Controller for the "本地 · Remote 下载" tab.
 * <p>
 * Lists every {@code .dc} entry under {@code DLC/remote/}, marks the ones
 * whose target file is missing, and lets the user resolve + download them via
 * {@link DlcDownloadCandidate#resolve(DlcRemoteEntry)}.
 * <p>
 * Downloads use a plain {@link HttpClient} (the in-game client uses a more
 * elaborate segmented downloader, but for the standalone manager a simple
 * stream copy with hash verification is sufficient and dependency-free).
 */
final class LocalRemoteDownloadTab {

    private final StandalonePlatformProvider provider;

    private final ObservableList<DlcRemoteEntry> entries = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("请先在「本地 · DLC 管理」中选择游戏目录");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = new Button("刷新远程列表");
    private final Button downloadMissingBtn = new Button("下载全部缺失项");
    private final Button downloadSelectedBtn = new Button("下载选中项");
    private final Label countLabel = new Label("");

    LocalRemoteDownloadTab(StandalonePlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
    }

    Tab build() {
        Tab tab = new Tab("本地 · Remote 下载");
        tab.setClosable(false);

        BorderPane root = new BorderPane();

        // ── Top: action bar ───────────────────────────────────────
        refreshBtn.setOnAction(e -> refreshAsync());
        downloadMissingBtn.setOnAction(e -> downloadMissingAsync());
        downloadSelectedBtn.disableProperty().bind(refreshBtn.disabledProperty());

        HBox topBox = new HBox(8, refreshBtn, downloadMissingBtn, downloadSelectedBtn, countLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(8));
        root.setTop(topBox);

        // ── Center: entry list ────────────────────────────────────
        ListView<DlcRemoteEntry> listView = new ListView<>(entries);
        listView.setCellFactory(param -> new RemoteEntryCell());
        listView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox centerBox = new VBox(listView);
        centerBox.setPadding(new Insets(0, 8, 0, 8));
        root.setCenter(centerBox);

        // Download the user-selected entries (any that are still missing),
        // expanded with their nested remote dependencies so a selected pack
        // pulls in its declared deps too.
        downloadSelectedBtn.setOnAction(e -> {
            java.util.List<DlcRemoteEntry> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                statusLabel.setText("请先在列表中选择条目");
                return;
            }
            java.util.List<DlcRemoteEntry> missingSelected = new java.util.ArrayList<>();
            for (DlcRemoteEntry entry : selected) {
                if (entry != null && isMissing(entry)) {
                    missingSelected.add(entry);
                }
            }
            if (missingSelected.isEmpty()) {
                statusLabel.setText("所选条目均已存在");
                return;
            }
            java.util.List<DlcRemoteEntry> toDownload = expandWithDependencies(missingSelected);
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
        statusLabel.setText("游戏目录已更新，点击「刷新远程列表」");
        refreshBtn.setDisable(false);
    }

    // ───────────────────────────────────────────────────────────────
    //  Refresh
    // ───────────────────────────────────────────────────────────────

    private void refreshAsync() {
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("加载远程 DLC 列表…");
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRemoteEntry> loaded = DlcRemoteLoader.load(dlcRoot);
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    int missing = countMissing(loaded);
                    countLabel.setText(String.format("共 %d 项，缺失 %d 项", loaded.size(), missing));
                    statusLabel.setText(missing == 0 ? "无缺失项" : "存在缺失项，可点击「下载全部缺失项」");
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
        }, "dlc-remote-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private int countMissing(List<DlcRemoteEntry> list) {
        int n = 0;
        for (DlcRemoteEntry e : list) {
            if (isMissing(e)) {
                n++;
            }
        }
        return n;
    }

    private boolean isMissing(DlcRemoteEntry entry) {
        Path target = entry.getTargetPath();
        return target == null || !Files.exists(target);
    }

    // ───────────────────────────────────────────────────────────────
    //  Download
    // ───────────────────────────────────────────────────────────────

    private void downloadMissingAsync() {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        for (DlcRemoteEntry e : entries) {
            if (isMissing(e)) {
                missing.add(e);
            }
        }
        if (missing.isEmpty()) {
            statusLabel.setText("无缺失项需要下载");
            return;
        }
        // Recursively pull in each missing entry's nested remote dependencies
        // (declared inline in its [dependencies] block) that are themselves
        // missing — mirrors the in-game remote downloader.
        List<DlcRemoteEntry> toDownload = expandWithDependencies(missing);
        downloadAll(toDownload);
    }

    /**
     * Recursively expand {@code roots} with their nested remote dependencies,
     * keeping only entries that are still missing.  Already-present entries are
     * skipped but still traversed so their own dependencies get a chance to be
     * pulled in.  Dedup is by dlc-identifier (falling back to the download
     * unique-key) so the same pack isn't downloaded twice.
     */
    private List<DlcRemoteEntry> expandWithDependencies(List<DlcRemoteEntry> roots) {
        List<DlcRemoteEntry> result = new ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<DlcRemoteEntry> queue = new java.util.ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            DlcRemoteEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            String key = entry.getDlcIdentifier();
            if (key == null || key.isBlank()) {
                key = entry.getUniqueKey();
            }
            if (key == null || key.isBlank() || !seen.add(key)) {
                continue;
            }
            if (isMissing(entry)) {
                result.add(entry);
            }
            for (DlcRemoteEntry dep : entry.getRemoteDependencies()) {
                if (dep != null) {
                    queue.add(dep);
                }
            }
        }
        return result;
    }

    private void downloadAll(List<DlcRemoteEntry> toDownload) {
        downloadMissingBtn.setDisable(true);
        refreshBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        Thread thread = new Thread(() -> {
            int total = toDownload.size();
            AtomicLong done = new AtomicLong();
            List<String> errors = new ArrayList<>();
            for (DlcRemoteEntry entry : toDownload) {
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
        }, "dlc-remote-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadOne(DlcRemoteEntry entry) throws IOException, InterruptedException {
        Path target = entry.getTargetPath();
        if (target == null) {
            throw new IOException("无法解析目标路径: " + safeName(entry));
        }
        if (Files.exists(target)) {
            return; // already present
        }
        List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(entry);
        if (candidates.isEmpty()) {
            throw new IOException("无可用下载源");
        }
        Files.createDirectories(target.getParent());
        IOException last = null;
        for (DlcDownloadCandidate c : candidates) {
            try {
                downloadCandidate(c, target, entry.getFileNameHint());
                return;
            } catch (IOException ioe) {
                last = ioe;
            }
        }
        throw last != null ? last : new IOException("下载失败: " + safeName(entry));
    }

    private void downloadCandidate(DlcDownloadCandidate candidate, Path target, String preferredName)
            throws IOException, InterruptedException {
        String fileName = firstNonBlank(preferredName,
                candidate.fileNameOverride,
                DlcDownloadUtil.extractFileNameFromUrl(candidate.url),
                target.getFileName().toString());
        fileName = DlcDownloadUtil.sanitizeFileName(fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = target.getFileName().toString();
        }
        Path finalPath = target.getParent().resolve(fileName);
        Path temp = target.getParent().resolve(fileName + ".part");
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
    //  Cell renderer
    // ───────────────────────────────────────────────────────────────

    private final class RemoteEntryCell extends javafx.scene.control.ListCell<DlcRemoteEntry> {
        private final Label nameLabel = new Label();
        private final Label statusTag = new Label();
        private final Label detailLabel = new Label();
        private final HBox row = new HBox(8, nameLabel, statusTag, detailLabel);

        RemoteEntryCell() {
            nameLabel.setStyle("-fx-font-weight: bold;");
            statusTag.setStyle("-fx-text-fill: white; -fx-background-color: gray; -fx-background-radius: 3; -fx-padding: 0 4;");
            detailLabel.setStyle("-fx-text-fill: #666;");
        }

        @Override
        protected void updateItem(DlcRemoteEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                nameLabel.setText(safeName(item));
                boolean missing = isMissing(item);
                if (missing) {
                    statusTag.setText(" 缺失 ");
                    statusTag.setStyle("-fx-text-fill: white; -fx-background-color: #c0392b; -fx-background-radius: 3; -fx-padding: 0 4;");
                } else {
                    statusTag.setText(" 已存在 ");
                    statusTag.setStyle("-fx-text-fill: white; -fx-background-color: #27ae60; -fx-background-radius: 3; -fx-padding: 0 4;");
                }
                Path target = item.getTargetPath();
                detailLabel.setText(target == null ? "(无目标路径)" : target.toString());
                StringBuilder tip = new StringBuilder();
                tip.append("标识: ").append(item.getDlcIdentifier() == null ? "" : item.getDlcIdentifier());
                if (item.getType() != null) {
                    tip.append("\n类型: ").append(item.getType().name());
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
                setTooltip(new Tooltip(tip.toString()));
                setGraphic(row);
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────

    private static String safeName(DlcRemoteEntry entry) {
        String name = entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry.getFileNameHint();
        }
        if (name == null || name.isBlank()) {
            name = entry.getDlcIdentifier();
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
