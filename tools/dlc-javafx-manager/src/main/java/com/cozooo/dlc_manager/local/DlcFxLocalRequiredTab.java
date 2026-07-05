package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredActionFlow;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredRefreshFlow;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredOperationModel;
import com.cozooo.dlc_manager.ui.required.DlcFxRequiredListShell;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.layout.Priority;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class DlcFxLocalRequiredTab {

    private final DlcFxPlatformProvider provider;
    private final ObservableList<DlcRequiredEntry> visibleRows = FXCollections.observableArrayList();
    private final ObservableList<DlcRequiredEntry> entries = FXCollections.observableArrayList();
    private final DlcFxRequiredOperationModel operationModel = new DlcFxRequiredOperationModel();
    private final DlcFxRequiredRefreshFlow refreshFlow = new DlcFxRequiredRefreshFlow(new LocalRequiredRefreshActions());
    private final DlcFxRequiredActionFlow<DlcRequiredEntry> actionFlow =
            new DlcFxRequiredActionFlow<>(new LocalRequiredActions());
    private final AtomicLong generation = new AtomicLong(0L);

    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label totalProgressLabel = new Label("");
    private final Label countLabel = new Label("");
    private DlcFxRequiredListShell<DlcRequiredEntry> shell;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalRequiredTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setPrefWidth(220);
        progressBar.setMinWidth(220);
        progressBar.setMaxWidth(220);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        totalProgressLabel.setVisible(false);
        totalProgressLabel.setManaged(false);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.required"));
        tab.setClosable(false);

        BorderPane root = new BorderPane();
        DlcFxStyle.view(root);

        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(totalProgressLabel);
        totalProgressLabel.setMinWidth(42);
        totalProgressLabel.setPrefWidth(42);
        totalProgressLabel.setMaxWidth(42);

        shell = new DlcFxRequiredListShell<>(
                new RequiredAdapter(),
                statusLabel,
                progressBar,
                totalProgressLabel,
                DlcFxI18n.tr("dlc_manager.required.refresh"),
                DlcFxI18n.tr("dlc_manager.required.download_missing"),
                DlcFxI18n.tr("dlc_manager.required.install_selected"),
                refreshFlow::refresh,
                actionFlow::installMissing,
                actionFlow::installSelected,
                null,
                entry -> () -> actionFlow.toggle(entry)
        );
        shell.setAvailable(gameDirSelected);
        root.setTop(shell.topBar(countLabel));

        VBox centerBox = new VBox(shell.listFrame());
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);

        root.setBottom(shell.bottomBar());

        tab.setContent(root);
        updateActionButtons();
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        entries.clear();
        visibleRows.clear();
        operationModel.reset();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.game_dir_changed"));
        if (shell != null) {
            shell.setAvailable(true);
        }
        refreshAsync();
    }

    public void onTabSelected() {
        if (!gameDirSelected || loadedForCurrentGameDir) {
            return;
        }
        refreshAsync();
    }

    public void forceDownloadAndInstall(List<DlcRequiredEntry> missing) {
        if (missing == null || missing.isEmpty()) {
            return;
        }
        entries.setAll(DlcRequiredRuntime.collectDependencies(missing));
        refreshView();
        installMissingAll(true);
    }

    private void refreshAsync() {
        if (!gameDirSelected) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            if (shell != null) {
                shell.setAvailable(false);
            }
            updateActionButtons();
            return;
        }
        loadedForCurrentGameDir = true;
        if (shell != null) {
            shell.setBusy(true);
        }
        updateActionButtons();
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.loading"));
        Thread thread = new Thread(() -> {
            try {
                Path dlcRoot = DlcConfig.getDlcRoot();
                List<DlcRequiredEntry> loaded = DlcRequiredLoader.load(dlcRoot);
                DlcRequiredService.Status status = DlcRequiredService.inspect(loaded);
                Platform.runLater(() -> {
                    entries.setAll(loaded);
                    refreshView();
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.required.count", loaded.size(), status.missingDownloads, status.needsApply));
                    statusLabel.setText(status.missingDownloads == 0 && status.needsApply == 0
                            ? DlcFxI18n.tr("dlc_manager.status.all_ready")
                            : (status.missingDownloads > 0 ? DlcFxI18n.tr("dlc_manager.required.has_missing") : DlcFxI18n.tr("dlc_manager.required.has_needs_apply")));
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    updateActionButtons();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    updateActionButtons();
                });
            }
        }, "dlc-required-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void installMissingAll(boolean forceInstall) {
        List<DlcRequiredEntry> targets = DlcRequiredService.collectMissingDownloads(entries);
        if (targets.isEmpty()) {
            if (forceInstall) {
                targets = resolveInstallTargets(entries);
            }
            if (targets.isEmpty()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.no_installable"));
                return;
            }
        }
        installEntries(targets, true);
    }

    private List<DlcRequiredEntry> resolveInstallTargets(List<DlcRequiredEntry> source) {
        List<DlcRequiredEntry> targets = new ArrayList<>();
        for (DlcRequiredEntry entry : source) {
            if (entry == null) {
                continue;
            }
            if (DlcRequiredService.isDownloaded(entry) && DlcRequiredRuntime.entryNeedsApply(entry)) {
                targets.add(entry);
            }
        }
        if (!targets.isEmpty()) {
            return targets;
        }
        for (DlcRequiredEntry entry : source) {
            if (entry != null && DlcRequiredService.isDownloaded(entry)) {
                targets.add(entry);
            }
        }
        return targets;
    }

    private void installEntries(List<DlcRequiredEntry> targets, boolean refreshAfter) {
        if (!requireGameDir()) {
            return;
        }
        long token = generation.incrementAndGet();
        boolean showTotalProgress = targets.size() > 1;
        progressBar.setVisible(showTotalProgress);
        progressBar.setManaged(showTotalProgress);
        progressBar.setProgress(showTotalProgress ? 0 : -1);
        totalProgressLabel.setVisible(showTotalProgress);
        totalProgressLabel.setManaged(showTotalProgress);
        totalProgressLabel.setText(showTotalProgress ? "0%" : "");
        if (shell != null) {
            shell.setBusy(true);
        }
        updateActionButtons();
        for (DlcRequiredEntry entry : targets) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            operationModel.begin(id);
        }
        syncVisibleItemCells();
        Thread thread = new Thread(() -> {
            int total = targets.size();
            int done = 0;
            List<String> errors = new ArrayList<>();
            try {
                for (DlcRequiredEntry entry : targets) {
                    if (entry == null) {
                        continue;
                    }
                    String id = entry.getIdentifier();
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    if (operationModel.isCancelRequested(id)) {
                        break;
                    }
                    if (isCurrentGeneration(token)) {
                        operationModel.markDownloading(id, 0f);
                        syncVisibleItemCells();
                        updateTotalProgress(targets, token);
                    }
                    if (!DlcRequiredService.isDownloaded(entry)) {
                        DlcRequiredService.downloadOne(entry, (downloadedBytes, totalBytes) -> {
                            if (totalBytes > 0 && isCurrentGeneration(token)) {
                                operationModel.markDownloading(id, Math.min(1f, downloadedBytes / (float) totalBytes));
                                syncVisibleItemCells();
                                updateTotalProgress(targets, token);
                            }
                        }, () -> operationModel.isCancelRequested(id));
                    }
                    if (operationModel.isCancelRequested(id)) {
                        break;
                    }
                    if (isCurrentGeneration(token)) {
                        operationModel.markInstalling(id);
                        syncVisibleItemCells();
                        updateTotalProgress(targets, token);
                    }
                    if (DlcRequiredService.installOne(entry)) {
                        DlcRequiredRuntime.updateUserRemoved(entry, false);
                    }
                    done++;
                    if (isCurrentGeneration(token)) {
                        operationModel.markDownloading(id, 1f);
                        syncVisibleItemCells();
                        updateTotalProgress(targets, token);
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                errors.add(ex.getMessage());
            } catch (Throwable t) {
                errors.add(safeMessage(t));
            } finally {
                final int completedCount = done;
                final int totalCount = total;
                Platform.runLater(() -> {
                    generation.incrementAndGet();
                    operationModel.reset();
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    progressBar.setProgress(0);
                    totalProgressLabel.setVisible(false);
                    totalProgressLabel.setManaged(false);
                    totalProgressLabel.setText("");
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    updateActionButtons();
                    syncVisibleItemCells();
                    refreshView();
                    if (!errors.isEmpty()) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.failed", errors.get(0)));
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.installed_count", completedCount, totalCount));
                    }
                    if (refreshAfter) {
                        refreshAsync();
                    }
                });
            }
        }, "dlc-required-install");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateTotalProgress(List<DlcRequiredEntry> targets, long token) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateTotalProgress(targets, token));
            return;
        }
        if (!isCurrentGeneration(token) || targets == null || targets.size() <= 1) {
            return;
        }
        int total = targets.size();
        float sum = 0f;
        for (DlcRequiredEntry entry : targets) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            float progress = id == null ? Float.NaN : operationModel.progress(id);
            if (!Float.isNaN(progress)) {
                sum += Math.max(0f, Math.min(1f, progress));
            }
        }
        double value = Math.max(0d, Math.min(1d, sum / total));
        progressBar.setProgress(value);
        totalProgressLabel.setText(Math.max(0, Math.min(100, Math.round((float) (value * 100d)))) + "%");
    }

    private void uninstallEntry(DlcRequiredEntry entry) {
        if (entry == null) {
            return;
        }
        List<Path> targets = entry.resolveAppliedTargets();
        String fileName = resolveExistingFileName(entry);
        for (Path target : targets) {
            if (target == null) {
                continue;
            }
            Path targetFile = resolveTarget(target, fileName);
            if (targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile);
                } catch (Throwable ignored) {
                }
            }
        }
        DlcRequiredRuntime.updateUserRemoved(entry, true);
    }

    private final class LocalRequiredActions implements DlcFxRequiredActionFlow.Actions<DlcRequiredEntry> {
        @Override
        public boolean requireAvailable() {
            return requireGameDir();
        }

        @Override
        public List<DlcRequiredEntry> missingInstallTargets() {
            return DlcRequiredService.collectMissingDownloads(entries);
        }

        @Override
        public List<DlcRequiredEntry> selectedItems() {
            return shell == null ? List.of() : shell.selectedItems();
        }

        @Override
        public List<DlcRequiredEntry> selectedInstallTargets(List<DlcRequiredEntry> selectedItems) {
            return DlcRequiredService.collectMissingDownloads(DlcRequiredRuntime.collectDependencies(selectedItems));
        }

        @Override
        public boolean isInstalled(DlcRequiredEntry entry) {
            return DlcRequiredService.isDownloaded(entry) && !DlcRequiredRuntime.entryNeedsApply(entry);
        }

        @Override
        public boolean isActive(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            return id != null && operationModel.isActive(id);
        }

        @Override
        public void startInstall(List<DlcRequiredEntry> items) {
            installEntries(items, true);
        }

        @Override
        public void uninstall(DlcRequiredEntry entry) {
            uninstallEntry(entry);
            syncVisibleItemCells();
        }

        @Override
        public void showNoInstallable() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.no_installable"));
        }

        @Override
        public void showSelectListItem() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_list_item"));
        }

        @Override
        public void showSelectedDownloaded() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.required.selected_downloaded"));
        }
    }

    private boolean requireGameDir() {
        if (!gameDirSelected || DlcConfig.getDlcRoot() == null) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            updateActionButtons();
            return false;
        }
        return true;
    }

    private void updateActionButtons() {
        if (shell != null) {
            shell.setAvailable(gameDirSelected);
        }
    }

    private final class LocalRequiredRefreshActions implements DlcFxRequiredRefreshFlow.Actions {
        @Override
        public boolean enabled() {
            return gameDirSelected;
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
        }

        @Override
        public void refresh() {
            refreshAsync();
        }
    }

    private void refreshView() {
        List<DlcRequiredEntry> rows = new ArrayList<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry != null && !entry.isHidden()) {
                rows.add(entry);
            }
        }
        visibleRows.setAll(rows);
        if (shell != null) {
            shell.setItems(rows);
        }
        updateActionButtons();
    }

    private void syncVisibleItemCells() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::syncVisibleItemCells);
            return;
        }
        if (shell != null) {
            shell.refreshRows();
        }
    }

    private String selectionId(DlcRequiredEntry entry) {
        if (entry == null) {
            return "";
        }
        String id = entry.getIdentifier();
        if (id != null && !id.isBlank()) {
            return id;
        }
        String name = entry.getRequiredFileName();
        return name == null ? "" : name;
    }

    private boolean isCurrentGeneration(long token) {
        return generation.get() == token;
    }

    private String safeName(DlcRequiredEntry entry) {
        String name = entry == null ? null : entry.resolveDisplayName();
        if (name == null || name.isBlank()) {
            name = entry == null ? null : entry.getRequiredFileName();
        }
        if (name == null || name.isBlank()) {
            name = entry == null ? null : entry.getIdentifier();
        }
        return name == null ? DlcFxI18n.tr("dlc_manager.status.unnamed") : name;
    }

    private String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        return msg == null || msg.isBlank() ? "unknown" : msg;
    }

    private String resolveExistingFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = entry.getResolvedFileName();
        if (fileName != null && !fileName.isBlank()) {
            Path target = entry.resolveRequiredPath(fileName);
            if (target != null && Files.exists(target)) {
                return fileName;
            }
        }
        String base = entry.getBaseName();
        if (base == null || base.isBlank() || entry.getRequiredDir() == null) {
            return fileName;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".part")) {
                    continue;
                }
                if (!name.endsWith(".dc")) {
                    return name;
                }
            }
        } catch (IOException ignored) {
        }
        return fileName;
    }

    private Path resolveTarget(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
    }

    private static String resolveTip(DlcRequiredEntry entry) {
        if (entry == null || entry.getTips().isEmpty()) {
            return "";
        }
        Map<String, String> tips = entry.getTips();
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = tips.get(lang);
        if (value == null || value.isBlank()) {
            value = tips.get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = tips.values().stream().findFirst().orElse("");
        }
        return value == null ? "" : value;
    }

    private final class RequiredAdapter implements DlcFxRequiredListShell.Adapter<DlcRequiredEntry> {
        @Override
        public String id(DlcRequiredEntry entry) {
            return selectionId(entry);
        }

        @Override
        public String title(DlcRequiredEntry entry) {
            return safeName(entry);
        }

        @Override
        public String tooltip(DlcRequiredEntry entry) {
            return resolveTip(entry);
        }

        @Override
        public String statusText(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            boolean active = id != null && operationModel.isActive(id);
            float progress = id == null ? Float.NaN : operationModel.progress(id);
            if (id != null && (operationModel.isInstalling(id) || (active && !Float.isNaN(progress) && progress >= 1f))) {
                return DlcFxI18n.tr("dlc_manager.status.installing");
            }
            boolean downloaded = DlcRequiredService.isDownloaded(entry);
            if (!downloaded) {
                return DlcFxI18n.tr("dlc_manager.status.missing_download");
            }
            if (DlcRequiredRuntime.entryNeedsApply(entry)) {
                return DlcFxI18n.tr("dlc_manager.status.needs_install");
            }
            return DlcFxI18n.tr("dlc_manager.status.installed");
        }

        @Override
        public String statusStyle(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            boolean active = id != null && operationModel.isActive(id);
            float progress = id == null ? Float.NaN : operationModel.progress(id);
            if (id != null && (operationModel.isInstalling(id) || (active && !Float.isNaN(progress) && progress >= 1f))) {
                return DlcFxStyle.CHIP_WARN;
            }
            boolean downloaded = DlcRequiredService.isDownloaded(entry);
            if (!downloaded) {
                return DlcFxStyle.CHIP_ERROR;
            }
            if (DlcRequiredRuntime.entryNeedsApply(entry)) {
                return DlcFxStyle.CHIP_WARN;
            }
            return DlcFxStyle.CHIP_OK;
        }

        @Override
        public boolean actionVisible(DlcRequiredEntry entry) {
            return true;
        }

        @Override
        public String actionText(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            boolean active = id != null && operationModel.isActive(id);
            float progress = id == null ? Float.NaN : operationModel.progress(id);
            if (id != null && (operationModel.isInstalling(id) || (active && !Float.isNaN(progress) && progress >= 1f))) {
                return DlcFxI18n.tr("dlc_manager.required.installing");
            }
            boolean downloaded = DlcRequiredService.isDownloaded(entry);
            return downloaded && !DlcRequiredRuntime.entryNeedsApply(entry)
                    ? DlcFxI18n.tr("dlc_manager.required.uninstall")
                    : DlcFxI18n.tr("dlc_manager.required.install");
        }

        @Override
        public boolean actionDanger(DlcRequiredEntry entry) {
            return DlcRequiredService.isDownloaded(entry) && !DlcRequiredRuntime.entryNeedsApply(entry);
        }

        @Override
        public boolean actionDisabled(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            return id != null && operationModel.isActive(id);
        }

        @Override
        public double progress(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            float progress = id == null ? Float.NaN : operationModel.progress(id);
            return Float.isNaN(progress) ? Double.NaN : progress;
        }

        @Override
        public String progressText(DlcRequiredEntry entry) {
            String id = entry == null ? null : entry.getIdentifier();
            return id == null ? "" : operationModel.progressText(id);
        }
    }
}
