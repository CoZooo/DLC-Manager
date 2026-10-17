package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.app.DlcFxManagerApp;

import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.client.pack.DlcPackCFResolver;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackInstallPlan;
import com.cozooo.dlc_manager.client.pack.DlcPackManifest;
import com.cozooo.dlc_manager.client.pack.DlcPackMeta;
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.pack.DlcPackOrder;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
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
import com.cozooo.dlc_manager.ui.packs.DlcFxPackPendingActionModel;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackReplacementConfirmModel;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowState;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowView;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackShell;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackToggleFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackVersionFlow;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackVersionSelectionModel;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DlcFxLocalPackTab {

    private final DlcFxPlatformProvider provider;
    private final ObservableList<PackArchiveEntry> entries = FXCollections.observableArrayList();
    private final ObservableList<PackArchiveGroup> groups = FXCollections.observableArrayList();
    private List<PackArchiveGroup> archiveGroups = List.of();
    private final Map<String, DlcPackMeta> installedByIdentifier = new HashMap<>();
    private final Map<String, String> selectedVersionByIdentifier = new LinkedHashMap<>();
    private final DlcFxPackPendingActionModel<PackArchiveEntry> pendingModel =
        new DlcFxPackPendingActionModel<>(entry -> entry.metadata.identifier, entry -> entry.metadata.version);
    private final DlcFxPackVersionSelectionModel<PackArchiveEntry> versionSelectionModel =
        new DlcFxPackVersionSelectionModel<>(selectedVersionByIdentifier, pendingModel, entry -> entry.metadata.identifier, PackArchiveEntry::key);
    private final DlcFxPackApplyFlow applyFlow;
    private final DlcFxPackDeleteFlow<PackArchiveGroup> deleteFlow;
    private final DlcFxPackToggleFlow<PackArchiveGroup, PackArchiveEntry> toggleFlow;
    private final DlcFxPackVersionFlow<PackArchiveGroup, PackArchiveEntry> versionFlow;
    private final DlcFxPackOrderFlow<PackArchiveGroup> orderFlow;
    private final DlcFxPackImportFlow importFlow;
    private final Map<String, PackArchiveEntry> pendingInstalls = pendingModel.pendingInstalls();
    private final Set<String> pendingUninstalls = pendingModel.pendingUninstallIds();
    private final Label countLabel = new Label("");
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private ListView<PackArchiveGroup> listView;
    private final Button refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.refresh"));
    private final Button importBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.import"));
    private final Button importUrlBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.import_url"));
    private final Button applyBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.apply"));
    private final Button cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.pack.cancel_changes"));
    private boolean busy;
    private boolean forceRefreshAfterBusy;
    private int selectedIndex = -1;
    private List<String> currentArchiveOrder = List.of();
    private List<String> committedArchiveOrder = List.of();
    private List<String> entryArchiveOrder = List.of();
    private boolean orderSnapshotInitialized;
    private boolean cancelRequested;
    private boolean cancelApplyRequested;
    private boolean paused;
    private BusyOperation busyOperation = BusyOperation.NONE;
    private boolean pausedAllowRuntimeChanges;
    private Set<String> activeReinstallIdentifiers = Set.of();
    private final Set<String> pausedReinstallIdentifiers = new LinkedHashSet<>();
    private float operationProgress = -1f;
    private Label operationStatusLabel;
    private DlcFxPackShell<PackArchiveGroup> shell;
    private StackPane urlDownloadOverlay;
    private ProgressBar urlDownloadProgressBar;
    private Label urlDownloadStatusLabel;
    private Label urlDownloadDetailLabel;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;

    public DlcFxLocalPackTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        this.applyFlow = new DlcFxPackApplyFlow(new LocalPackApplyActions());
        this.deleteFlow = new DlcFxPackDeleteFlow<>(new LocalPackDeleteActions());
        this.toggleFlow = new DlcFxPackToggleFlow<>(new LocalPackToggleActions());
        this.versionFlow = new DlcFxPackVersionFlow<>(new LocalPackVersionActions());
        this.orderFlow = new DlcFxPackOrderFlow<>(new LocalPackOrderActions());
        this.importFlow = new DlcFxPackImportFlow(new LocalPackImportActions());
        progressBar.setVisible(false);
        progressBar.setPrefWidth(220);
    }

    public Tab build() {
        Tab tab = new Tab(DlcFxI18n.tr("dlc_manager.tab.pack"));
        tab.setClosable(false);

        refreshBtn.setDisable(true);
        importBtn.setDisable(true);
        importUrlBtn.setDisable(true);
        applyBtn.setDisable(true);
        refreshBtn.setOnAction(e -> importFlow.refresh());
        importBtn.setOnAction(e -> importFlow.importArchive());
        importUrlBtn.setOnAction(e -> importFlow.importUrl());
        applyBtn.setOnAction(e -> {
            if (busy && paused && busyOperation == BusyOperation.APPLY) {
                resumePausedApply();
            } else if (busy && busyOperation == BusyOperation.APPLY) {
                requestPauseApplyOperation();
            } else {
                applyPendingChanges();
            }
        });
        cancelBtn.setOnAction(e -> onBackOrCancel());
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);

        operationStatusLabel = new Label("");
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

        tab.setContent(shell.root());
        updateActionButtons();
        return tab;
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        refreshBtn.setDisable(false);
        importBtn.setDisable(false);
        importUrlBtn.setDisable(false);
        applyBtn.setDisable(true);
        cancelBtn.setDisable(false);
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);
        entries.clear();
        groups.clear();
        archiveGroups = List.of();
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.no_data"));
        updateActionButtons();
        refreshAsync(false);
    }

    public void onTabSelected() {
        if (!gameDirSelected || loadedForCurrentGameDir) {
            return;
        }
        refreshAsync(true);
    }

    private void refreshAsync() {
        refreshAsync(true);
    }

    private void refreshAsync(boolean showRefreshDone) {
        if (!gameDirSelected) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            refreshBtn.setDisable(true);
            importBtn.setDisable(true);
            importUrlBtn.setDisable(true);
            applyBtn.setDisable(true);
            return;
        }
        loadedForCurrentGameDir = true;
        if (busy) {
            forceRefreshAfterBusy = true;
            return;
        }
        if (!DlcConfig.isPackFuncOn()) {
            entries.clear();
            groups.clear();
            archiveGroups = List.of();
            countLabel.setText("");
            progressBar.setVisible(false);
            refreshBtn.setDisable(false);
            importBtn.setDisable(true);
            importUrlBtn.setDisable(true);
            applyBtn.setDisable(true);
            cancelBtn.setDisable(false);
            cancelBtn.setVisible(false);
            cancelBtn.setManaged(false);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.disabled"));
            return;
        }
        refreshBtn.setDisable(true);
        importBtn.setDisable(false);
        importUrlBtn.setDisable(false);
        applyBtn.setDisable(false);
        updateActionButtons();
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        if (showRefreshDone) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.scanning_local"));
        }
        Thread thread = new Thread(() -> {
            try {
                Path packRoot = DlcPaths.dlcRoot().resolve("pack");
                Files.createDirectories(packRoot);
                refreshInstalledPacks();
                uninstallIncompatibleInstalledPacks();
                refreshInstalledPacks();
                List<PackArchiveGroup> scanned = scanPackArchives(packRoot);
                List<String> saved = DlcPackOrder.load(packOrderPath());
                boolean preserveCurrentOrder = !currentArchiveOrder.isEmpty() && hasOrderChanges();
                List<String> normalizedSaved = DlcPackOrder.normalizeOrder(saved, collectArchiveKeys(scanned));
                if (!orderSnapshotInitialized) {
                    entryArchiveOrder = List.copyOf(normalizedSaved);
                    orderSnapshotInitialized = true;
                }
                committedArchiveOrder = preserveCurrentOrder
                    ? DlcPackOrder.normalizeOrder(currentArchiveOrder, collectArchiveKeys(scanned))
                    : normalizedSaved;
                if (!preserveCurrentOrder) {
                    entryArchiveOrder = List.copyOf(committedArchiveOrder);
                }
                currentArchiveOrder = List.copyOf(committedArchiveOrder);
                DlcPackOrder.save(packOrderPath(), committedArchiveOrder);
                Platform.runLater(() -> {
                    rebuildWithScanned(scanned);
                    refreshBtn.setDisable(false);
                    progressBar.setVisible(false);
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.pack.count", scanned.size()));
                    if (showRefreshDone) {
                        statusLabel.setText(scanned.isEmpty()
                            ? DlcFxI18n.tr("dlc_manager.pack.none_archived")
                            : DlcFxI18n.tr("dlc_manager.pack.refresh_done"));
                    }
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    refreshBtn.setDisable(false);
                    if (showRefreshDone) {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", safeMessage(t)));
                    }
                });
            }
        }, "dlc-fx-pack-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshInstalledPacks() {
        installedByIdentifier.clear();
        Path packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack");
        if (!Files.isDirectory(packRoot)) {
            return;
        }
        try (var stream = Files.list(packRoot)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                DlcPackMeta meta = DlcPackMeta.load(dir.resolve("meta.dc"));
                if (meta != null) {
                    installedByIdentifier.put(meta.identifier, meta);
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void uninstallIncompatibleInstalledPacks() {
        try {
            List<DlcPackInstaller.UninstallReport> reports =
                new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstallIncompatibleForCurrentVersion();
            if (!reports.isEmpty()) {
                Platform.runLater(() -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.auto_uninstalled_incompatible", reports.size())));
            }
        } catch (Exception ex) {
            Platform.runLater(() -> statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.auto_uninstall_failed", describeFailure(ex))));
        }
    }

    private List<PackArchiveGroup> scanPackArchives(Path packRoot) {
        List<PackArchiveEntry> found = new ArrayList<>();
        try (var stream = DlcPackArchive.findArchives(DlcPaths.dlcRoot()).stream()) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(path -> {
                    PackArchiveEntry entry = inspectArchive(path);
                    if (entry != null) {
                        found.add(entry);
                    }
                });
        } catch (IOException ignored) {
        }
        return groupArchiveEntries(found);
    }

    private PackArchiveEntry inspectArchive(Path archive) {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                return null;
            }
            DlcPackPackage pack = result.pack;
            String lang = provider.languageCode();
            String displayName = pack.metadata.resolveDisplayName(lang);
            String description = pack.metadata.resolveDescription(lang);
            String author = pack.metadata.authors.isEmpty()
                ? DlcFxI18n.tr("dlc_manager.pack.unknown_author")
                : String.join(", ", pack.metadata.authors);
            Image icon = loadPackIcon(pack.iconFile);
            return new PackArchiveEntry(archive.toAbsolutePath().normalize(), archiveOrderPath(archive), pack.metadata, displayName, description, author, pack.format, icon, pack.manifestFiles);
        } catch (Exception ignored) {
            return null;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private Image loadPackIcon(Path iconPath) {
        if (iconPath == null || !Files.isRegularFile(iconPath)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(iconPath)) {
            Image icon = new Image(input, 48, 48, true, true);
            return icon.isError() ? null : icon;
        } catch (IOException ignored) {
            return null;
        }
    }

    private List<PackArchiveGroup> groupArchiveEntries(List<PackArchiveEntry> entries) {
        Map<String, List<PackArchiveEntry>> byIdentifier = new LinkedHashMap<>();
        for (PackArchiveEntry entry : entries) {
            byIdentifier.computeIfAbsent(entry.metadata.identifier, ignored -> new ArrayList<>()).add(entry);
        }
        List<PackArchiveGroup> out = new ArrayList<>();
        for (Map.Entry<String, List<PackArchiveEntry>> item : byIdentifier.entrySet()) {
            List<PackArchiveEntry> versions = new ArrayList<>(item.getValue());
            versions.sort((a, b) -> {
                int compare = DlcPackMetadata.compareVersionStrings(b.metadata.version, a.metadata.version);
                return compare != 0 ? compare : a.archiveOrderPath.compareToIgnoreCase(b.archiveOrderPath);
            });
            DlcPackMeta installed = installedByIdentifier.get(item.getKey());
            PackArchiveEntry orderEntry = versions.get(0);
            if (installed != null) {
                for (PackArchiveEntry entry : versions) {
                    if (entry.metadata.version.equals(installed.version)) {
                        orderEntry = entry;
                        break;
                    }
                }
            }
            String selectedVersion = selectedVersionByIdentifier.get(item.getKey());
            PackArchiveEntry display = orderEntry;
            if (selectedVersion != null) {
                for (PackArchiveEntry entry : versions) {
                    if (entry.key().equals(selectedVersion) || entry.metadata.version.equals(selectedVersion)) {
                        display = entry;
                        break;
                    }
                }
            }
            out.add(new PackArchiveGroup(item.getKey(), versions, orderEntry.key(), display.key()));
        }
        return out;
    }

    private void rebuildWithScanned(List<PackArchiveGroup> scanned) {
        sortGroupsByCurrentOrder(scanned);
        entries.setAll(flattenEntries(scanned));
        normalizeGroupSelections(scanned);
        archiveGroups = new ArrayList<>(scanned);
        restorePendingInstalls();
        pendingUninstalls.removeIf(identifier -> !installedByIdentifier.containsKey(identifier));
        groups.setAll(archiveGroups);
        listView.setItems(groups);
        selectedIndex = groups.isEmpty() ? -1 : Math.min(selectedIndex < 0 ? 0 : selectedIndex, groups.size() - 1);
        updateActionButtons();
    }

    private List<PackArchiveEntry> flattenEntries(List<PackArchiveGroup> scanned) {
        List<PackArchiveEntry> out = new ArrayList<>();
        if (scanned != null) {
            for (PackArchiveGroup group : scanned) {
                out.addAll(group.versions());
            }
        }
        return out;
    }

    private void normalizeGroupSelections(List<PackArchiveGroup> scanned) {
        Set<String> validIdentifiers = new HashSet<>();
        if (scanned != null) {
            for (PackArchiveGroup group : scanned) {
                validIdentifiers.add(group.identifier());
                selectedVersionByIdentifier.put(group.identifier(), group.displayEntry().key());
            }
        }
        selectedVersionByIdentifier.keySet().removeIf(identifier -> !validIdentifiers.contains(identifier));
    }

    private void restorePendingInstalls() {
        Map<String, String> pendingInstallKeys = new HashMap<>();
        for (Map.Entry<String, PackArchiveEntry> entry : pendingInstalls.entrySet()) {
            pendingInstallKeys.put(entry.getKey(), entry.getValue().key());
        }
        pendingInstalls.clear();
        for (PackArchiveGroup group : archiveGroups) {
            PackArchiveEntry entry = group.displayEntry();
            if (entry != null && !isEntryCurrentlyInstalled(entry) && entry.key().equals(pendingInstallKeys.get(entry.metadata.identifier))) {
                pendingInstalls.put(entry.metadata.identifier, entry);
            }
        }
    }

    private List<String> collectArchiveKeys(List<PackArchiveGroup> scanned) {
        List<String> keys = new ArrayList<>();
        for (PackArchiveGroup group : scanned) {
            keys.add(group.orderKey());
        }
        return keys;
    }

    private int orderIndex(String key) {
        int index = currentArchiveOrder.indexOf(key);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private void sortGroupsByCurrentOrder(List<PackArchiveGroup> target) {
        target.sort(Comparator.comparingInt(group -> orderIndex(group.orderKey())));
    }

    private List<PackArchiveGroup> allGroups() {
        return archiveGroups == null || archiveGroups.isEmpty() ? List.copyOf(groups) : List.copyOf(archiveGroups);
    }

    private void applyPendingChanges() {
        applyFlow.start();
    }

    private void startApplyPendingChanges(boolean allowRuntimeChanges) {
        pausedAllowRuntimeChanges = allowRuntimeChanges;
        setBusyStatus(DlcFxI18n.tr("dlc_manager.pack.applying"), DlcFxI18n.tr("dlc_manager.pack.applying"), null, BusyOperation.APPLY);
        Thread thread = new Thread(() -> {
            try {
                DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                applyOrderedChanges(installer, allowRuntimeChanges);
                pendingInstalls.clear();
                pendingUninstalls.clear();
                pausedReinstallIdentifiers.clear();
                activeReinstallIdentifiers = Set.of();
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.apply_success"), true);
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                if (cancelApplyRequested) {
                    finishBusy(DlcFxI18n.tr("dlc_manager.pack.canceled"));
                } else {
                    pauseApplyOperation();
                }
            } catch (Exception ex) {
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.apply_failed", describeFailure(ex)));
            }
        }, "dlc-fx-pack-apply");
        thread.setDaemon(true);
        thread.start();
    }

    private String buildReplacementConfirmText() {
        return DlcFxPackReplacementConfirmModel.message(
            DlcFxPackReplacementConfirmModel.lines(
                displayEntries(),
                entry -> Objects.equals(pendingInstalls.get(entry.metadata.identifier), entry),
                entry -> {
                    DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
                    return installed == null ? null : installed.version;
                },
                entry -> entry.metadata.version,
                entry -> entry.displayName
            ),
            DlcFxI18n::tr
        );
    }

    private List<PackArchiveEntry> displayEntries() {
        List<PackArchiveEntry> out = new ArrayList<>();
        for (PackArchiveGroup group : allGroups()) {
            if (group != null && group.displayEntry() != null) {
                out.add(group.displayEntry());
            }
        }
        return out;
    }

    private void applyOrderedChanges(DlcPackInstaller installer, boolean allowRuntimeChanges) throws IOException {
        List<PackArchiveGroup> ordered = new ArrayList<>(allGroups());
        ordered.sort(Comparator.comparingInt(group -> orderIndex(group.orderKey())));
        List<PackArchiveEntry> orderedEntries = new ArrayList<>();
        for (PackArchiveGroup group : ordered) {
            orderedEntries.add(effectiveApplyEntry(group));
        }
        Set<String> reinstallIdentifiers = collectAffectedReinstallIdentifiers(orderedEntries);
        reinstallIdentifiers.addAll(pausedReinstallIdentifiers);
        activeReinstallIdentifiers = Set.copyOf(reinstallIdentifiers);
        OperationDisplayMode displayMode = determineOperationDisplayMode(reinstallIdentifiers);
        boolean hasUninstallPhase = orderedEntries.stream().anyMatch(entry -> {
            String identifier = entry.metadata.identifier;
            return isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier));
        });
        float installOffset = hasUninstallPhase ? 0.35f : 0f;
        float installSpan = hasUninstallPhase ? 0.65f : 1f;
        LinkedHashSet<String> uninstalled = new LinkedHashSet<>();
        for (PackArchiveEntry entry : orderedEntries) {
            String identifier = entry.metadata.identifier;
            if (!uninstalled.contains(identifier) && isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier))) {
                checkCanceled();
                updateOperationProgress(operationStatusFor(displayMode, entry, null), -1f, null);
                installer.uninstall(identifier, (message, progress) ->
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(0f, 0.35f, progress), operationDetailFor(message))
                );
                uninstalled.add(identifier);
            }
        }
        LinkedHashSet<String> installed = new LinkedHashSet<>();
        for (int i = orderedEntries.size() - 1; i >= 0; i--) {
            PackArchiveEntry entry = orderedEntries.get(i);
            String identifier = entry.metadata.identifier;
            if (!installed.contains(identifier) && !pendingUninstalls.contains(identifier) && (Objects.equals(pendingInstalls.get(identifier), entry) || (reinstallIdentifiers.contains(identifier) && isEntryCurrentlyInstalled(entry)))) {
                checkCanceled();
                updateOperationProgress(operationStatusFor(displayMode, entry, null), installOffset, null);
                installArchive(installer, entry, allowRuntimeChanges, displayMode, installOffset, installSpan);
                installed.add(identifier);
            }
        }
        checkCanceled();
        committedArchiveOrder = finalAppliedOrder();
        currentArchiveOrder = List.copyOf(committedArchiveOrder);
        entryArchiveOrder = List.copyOf(committedArchiveOrder);
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder);
    }

    private PackArchiveEntry effectiveApplyEntry(PackArchiveGroup group) {
        PackArchiveEntry pending = pendingInstalls.get(group.identifier());
        if (pending != null) {
            return pending;
        }
        DlcPackMeta installed = installedByIdentifier.get(group.identifier());
        if (installed != null) {
            for (PackArchiveEntry entry : group.versions()) {
                if (entry.metadata.version.equals(installed.version)) {
                    return entry;
                }
            }
        }
        return group.displayEntry();
    }

    private void installArchive(
        DlcPackInstaller installer,
        PackArchiveEntry entry,
        boolean allowRuntimeChanges,
        OperationDisplayMode displayMode,
        float installOffset,
        float installSpan
    ) throws IOException {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                throw new IOException(result.error == null ? "invalid DLC Pack" : result.error);
            }
            DlcPackInstaller.PrecheckResult precheck = installer.precheck(result.pack);
            if (precheck.hasBlockingIssues() || (precheck.requiresRuntimeConfirmation() && !allowRuntimeChanges)) {
                throw new IOException(describePackBlockingPrecheck(precheck));
            }
            boolean replace = installedByIdentifier.containsKey(result.pack.metadata.identifier);
            DlcPackInstaller.InstallOptions options = allowRuntimeChanges
                ? DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges()
                : DlcPackInstaller.InstallOptions.strict();
            options = options.withProgressListener((message, progress) ->
                updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(installOffset, installSpan, progress), operationDetailFor(message))
            );
            options = options.withCancelChecker(() -> cancelRequested);
            installer.install(result.pack, replace, options);
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private Set<String> collectAffectedReinstallIdentifiers(List<PackArchiveEntry> ordered) throws IOException {
        List<PackArchiveEntry> finalEntries = new ArrayList<>();
        for (PackArchiveEntry entry : ordered) {
            if (pendingUninstalls.contains(entry.metadata.identifier)) {
                continue;
            }
            if (Objects.equals(pendingInstalls.get(entry.metadata.identifier), entry) || isEntryCurrentlyInstalled(entry)) {
                finalEntries.add(entry);
            }
        }
        Set<String> affected = new LinkedHashSet<>();
        for (int i = 0; i < finalEntries.size(); i++) {
            PackArchiveEntry left = finalEntries.get(i);
            for (int j = i + 1; j < finalEntries.size(); j++) {
                PackArchiveEntry right = finalEntries.get(j);
                if (packsHaveFileConflict(left, right) && pairOrderChanged(left, right)) {
                    affected.add(left.metadata.identifier);
                    affected.add(right.metadata.identifier);
                }
            }
        }
        return affected;
    }

    private boolean packsHaveFileConflict(PackArchiveEntry left, PackArchiveEntry right) throws IOException {
        Set<String> leftFiles = inspectArchiveFiles(left);
        if (leftFiles.isEmpty()) {
            return false;
        }
        Set<String> rightFiles = inspectArchiveFiles(right);
        for (String file : leftFiles) {
            if (rightFiles.contains(file)) {
                return true;
            }
        }
        return false;
    }

    private boolean pairOrderChanged(PackArchiveEntry left, PackArchiveEntry right) {
        int leftCommitted = committedOrderIndex(left.key());
        int rightCommitted = committedOrderIndex(right.key());
        if (leftCommitted < 0 || rightCommitted < 0) {
            return true;
        }
        return leftCommitted > rightCommitted;
    }

    private Set<String> inspectArchiveFiles(PackArchiveEntry entry) throws IOException {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                throw new IOException(result.error == null ? "invalid DLC Pack" : result.error);
            }
            Set<String> files = new LinkedHashSet<>();
            DlcPackInstallPlan plan = DlcPackInstallPlan.build(result.pack, DlcPaths.gameDir(), DlcPaths.dlcRoot());
            try {
                plan = DlcPackInstallPlan.merge(plan, DlcPackCFResolver.resolveManifestTargets(result.pack), DlcPaths.gameDir(), DlcPaths.dlcRoot());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", ex);
            }
            for (DlcPackInstallPlan.FileEntry file : plan.files) {
                files.add(file.relativePath);
            }
            return files;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private OperationDisplayMode determineOperationDisplayMode(Set<String> reinstallIdentifiers) {
        boolean hasInstallOps = !pendingInstalls.isEmpty() || !reinstallIdentifiers.isEmpty();
        boolean hasReplacementInstalls = false;
        for (String identifier : pendingInstalls.keySet()) {
            if (installedByIdentifier.containsKey(identifier)) {
                hasReplacementInstalls = true;
                break;
            }
        }
        boolean hasUninstallOps = !pendingUninstalls.isEmpty() || !reinstallIdentifiers.isEmpty() || hasReplacementInstalls;
        if (hasUninstallOps && !hasInstallOps) {
            return OperationDisplayMode.UNINSTALLING;
        }
        if (hasInstallOps && !hasUninstallOps) {
            return OperationDisplayMode.INSTALLING;
        }
        return OperationDisplayMode.APPLYING;
    }

    private String buildRuntimeConfirmText() {
        List<String> messages = new ArrayList<>();
        DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
        for (PackArchiveGroup group : allGroups()) {
            PackArchiveEntry entry = group.displayEntry();
            if (!Objects.equals(pendingInstalls.get(entry.metadata.identifier), entry)) {
                continue;
            }
            Path staging = null;
            try {
                staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
                DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
                if (!result.valid || result.pack == null) {
                    messages.add(result.error == null ? "invalid DLC Pack" : result.error);
                    continue;
                }
                DlcPackInstaller.PrecheckResult precheck = installer.precheck(result.pack);
                if (precheck.hasBlockingIssues()) {
                    messages.add(describePackBlockingPrecheck(precheck));
                    continue;
                }
                if (precheck.requiresRuntimeConfirmation()) {
                    messages.add(DlcFxI18n.tr("dlc_manager.pack.runtime_confirm_entry", entry.displayName, describePackBlockingPrecheck(precheck)));
                }
            } catch (Exception ex) {
                messages.add(describeFailure(ex));
            } finally {
                DlcPackArchive.deleteRecursive(staging);
            }
        }
        return messages.isEmpty() ? null : String.join("\n\n", messages);
    }

    private List<String> finalAppliedOrder() {
        List<String> out = new ArrayList<>();
        for (String key : currentArchiveOrder) {
            String identifier = identifierFromOrderKey(key);
            if (identifier == null) {
                continue;
            }
            PackArchiveGroup group = findGroup(identifier);
            if (group == null) {
                continue;
            }
            PackArchiveEntry pending = pendingInstalls.get(identifier);
            String finalKey = pending != null
                ? pending.key()
                : pendingUninstalls.contains(identifier)
                ? group.versions.get(0).key()
                : group.orderKey();
            if (!out.contains(finalKey)) {
                out.add(finalKey);
            }
        }
        for (PackArchiveGroup group : allGroups()) {
            PackArchiveEntry pending = pendingInstalls.get(group.identifier());
            String finalKey = pending != null
                ? pending.key()
                : pendingUninstalls.contains(group.identifier())
                ? group.versions.get(0).key()
                : group.orderKey();
            if (!out.contains(finalKey)) {
                out.add(finalKey);
            }
        }
        return out;
    }

    private void toggleInstall(PackArchiveGroup group) {
        toggleFlow.toggle(group);
    }

    private void selectPackVersion(PackArchiveGroup group, PackArchiveEntry selected) {
        versionFlow.select(group, selected);
    }

    private void updateArchiveGroup(String identifier, String selectedVersionKey) {
        List<PackArchiveGroup> updated = new ArrayList<>();
        for (PackArchiveGroup item : archiveGroups) {
            updated.add(item.identifier().equals(identifier) ? item.withSelectedVersionKey(selectedVersionKey) : item);
        }
        archiveGroups = updated;
    }

    private void moveGroup(PackArchiveGroup group, int direction) {
        orderFlow.move(group, direction);
    }

    private void confirmDeleteArchive(PackArchiveGroup group) {
        deleteFlow.confirmDelete(group);
    }

    private void startDeleteArchive(PackArchiveGroup group, DlcFxPackDeletePlan plan) {
        PackArchiveEntry entry = group.displayEntry();
        if (plan.uninstallFirst()) {
            setBusyStatus(plan.busyStatus(), plan.busyStatus(), null, BusyOperation.DELETE);
            Thread thread = new Thread(() -> {
                try {
                    DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                    installer.uninstall(entry.metadata.identifier);
                    deleteArchiveFile(group);
                    finishBusy(plan.successMessage(), true);
                } catch (Exception ex) {
                    finishBusy(DlcFxI18n.tr("dlc_manager.pack.delete_failed", describeFailure(ex)));
                }
            }, "dlc-fx-pack-delete");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        try {
            deleteArchiveFile(group);
            statusLabel.setText(plan.successMessage());
            refreshAsync(false);
        } catch (Exception ex) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.delete_failed", describeFailure(ex)));
        }
    }

    private void deleteArchiveFile(PackArchiveGroup group) throws IOException {
        PackArchiveEntry entry = group.displayEntry();
        Files.deleteIfExists(entry.archivePath);
        pendingInstalls.remove(entry.metadata.identifier, entry);
        pendingUninstalls.remove(entry.metadata.identifier);
        String replacementKey = null;
        for (PackArchiveEntry version : group.versions) {
            if (!version.key().equals(entry.key())) {
                replacementKey = version.key();
                break;
            }
        }
        currentArchiveOrder = replaceOrRemoveDeletedOrderKey(currentArchiveOrder, entry, replacementKey);
        committedArchiveOrder = replaceOrRemoveDeletedOrderKey(committedArchiveOrder, entry, replacementKey);
        entryArchiveOrder = replaceOrRemoveDeletedOrderKey(entryArchiveOrder, entry, replacementKey);
        DlcPackOrder.save(packOrderPath(), currentArchiveOrder);
    }

    private List<String> replaceOrRemoveDeletedOrderKey(List<String> order, PackArchiveEntry entry, String replacementKey) {
        List<String> out = new ArrayList<>();
        for (String key : order) {
            if (!orderKeyMatchesIdentifier(key, entry.metadata.identifier)) {
                out.add(key);
                continue;
            }
            if (replacementKey != null && !out.contains(replacementKey)) {
                out.add(key.equals(entry.key()) ? replacementKey : key);
            }
        }
        return out;
    }

    private void confirmOpenLink(PackArchiveEntry entry, String key) {
        String url = entry.metadata.links.get(key);
        if (url == null || url.isBlank()) {
            return;
        }
        DlcFxPackLinkActions.showOpenLinkConfirm(scene(), url, statusLabel::setText);
    }

    private void importPackArchive() {
        if (!DlcConfig.isPackFuncOn()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.disabled"));
            return;
        }
        Path zip = chooseZip(DlcFxI18n.tr("dlc_manager.pack.import"));
        if (zip == null) {
            return;
        }
        setBusyStatus(DlcFxI18n.tr("dlc_manager.pack.importing"), DlcFxI18n.tr("dlc_manager.pack.importing"), null, BusyOperation.IMPORT);
        Thread thread = new Thread(() -> {
            Path copied = null;
            try {
                checkCanceled();
                copied = copyArchiveToPackRoot(zip);
                checkCanceled();
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.import_staged", copied.getFileName().toString()), true);
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                if (copied != null) {
                    try {
                        Files.deleteIfExists(copied);
                    } catch (IOException ignored) {
                    }
                }
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.canceled"));
            } catch (Exception ex) {
                if (copied != null) {
                    try {
                        Files.deleteIfExists(copied);
                    } catch (IOException ignored) {
                    }
                }
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.import_failed", describeFailure(ex)));
            }
        }, "dlc-fx-pack-import");
        thread.setDaemon(true);
        thread.start();
    }

    private void importPackFromUrl() {
        if (!DlcConfig.isPackFuncOn()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.disabled"));
            return;
        }
        TextField urlField = new TextField();
        urlField.setPromptText(DlcFxI18n.tr("dlc_manager.common.url"));
        Scene currentScene = scene();
        double dialogWidth = currentScene == null || currentScene.getWidth() <= 0
            ? 520
            : Math.max(420, Math.min(620, currentScene.getWidth() - 96));
        urlField.setPrefWidth(dialogWidth);
        Label prompt = new Label(DlcFxI18n.tr("dlc_manager.pack.url_prompt"));
        DlcFxStyle.muted(prompt);
        VBox content = new VBox(8, prompt, urlField);
        DlcFxModalOverlay.show(scene(), DlcFxI18n.tr("dlc_manager.pack.import_url_title"), content, () -> {
            String url = urlField.getText() == null ? "" : urlField.getText().trim();
            if (url.isBlank()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.url_empty"));
                return;
            }
            startDownloadPackFromUrl(url);
        }, DlcFxI18n.tr("dlc_manager.update.url_download"), () -> {
        }, DlcFxI18n.tr("dlc_manager.common.cancel"), confirm -> {
            confirm.setDisable(true);
            urlField.textProperty().addListener((obs, oldValue, newValue) ->
                confirm.setDisable(newValue == null || newValue.trim().isEmpty()));
        });
    }

    private void showImportUrlDialog(DlcFxPackImportFlow.UrlImportPrompt prompt) {
        TextField urlField = new TextField();
        urlField.setPromptText(DlcFxI18n.tr("dlc_manager.common.url"));
        Scene currentScene = scene();
        double dialogWidth = currentScene == null || currentScene.getWidth() <= 0
            ? 520
            : Math.max(420, Math.min(620, currentScene.getWidth() - 96));
        urlField.setPrefWidth(dialogWidth);
        Label promptLabel = new Label(DlcFxI18n.tr("dlc_manager.pack.url_prompt"));
        DlcFxStyle.muted(promptLabel);
        VBox content = new VBox(8, promptLabel, urlField);
        DlcFxModalOverlay.show(scene(), DlcFxI18n.tr("dlc_manager.pack.import_url_title"), content, () -> {
            String url = urlField.getText() == null ? "" : urlField.getText().trim();
            if (url.isBlank()) {
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.url_empty"));
                return;
            }
            prompt.submit(url, "");
        }, DlcFxI18n.tr("dlc_manager.update.url_download"), () -> {
        }, DlcFxI18n.tr("dlc_manager.common.cancel"), confirm -> {
            confirm.setDisable(true);
            urlField.textProperty().addListener((obs, oldValue, newValue) ->
                confirm.setDisable(newValue == null || newValue.trim().isEmpty()));
        });
    }

    private void startDownloadPackFromUrl(String url) {
        setBusyStatus(DlcFxI18n.tr("dlc_manager.pack.url_downloading"), DlcFxI18n.tr("dlc_manager.pack.url_downloading"), null, BusyOperation.IMPORT);
        showUrlDownloadProgressDialog();
        Thread thread = new Thread(() -> {
            Path downloaded = null;
            try {
                downloaded = downloadPackUrl(url, DlcPaths.dlcRoot().resolve("pack"));
                closeUrlDownloadProgressDialog();
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.import_staged", downloaded.getFileName().toString()), true);
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded);
                    } catch (IOException ignored) {
                    }
                }
                closeUrlDownloadProgressDialog();
                finishBusy(DlcFxI18n.tr("dlc_manager.pack.canceled"));
            } catch (Exception ex) {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded);
                    } catch (IOException ignored) {
                    }
                }
                closeUrlDownloadProgressDialog();
                finishBusy(DlcFxI18n.tr("dlc_manager.update.url_failed", describeFailure(ex)));
            }
        }, "dlc-fx-pack-url-download");
        thread.setDaemon(true);
        thread.start();
    }

    private final class LocalPackImportActions implements DlcFxPackImportFlow.Actions {
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
            refreshAsync(true);
        }

        @Override
        public void importArchive() {
            importPackArchive();
        }

        @Override
        public void promptUrlImport(DlcFxPackImportFlow.UrlImportPrompt prompt) {
            showImportUrlDialog(prompt);
        }

        @Override
        public void emptyUrl() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.update.url_empty"));
        }

        @Override
        public void importUrl(String url, String fileName) {
            startDownloadPackFromUrl(url);
        }
    }

    private void showUrlDownloadProgressDialog() {
        Platform.runLater(() -> {
            ProgressBar dialogProgress = new ProgressBar(-1);
            dialogProgress.setPrefWidth(360);
            Label status = new Label(DlcFxI18n.tr("dlc_manager.pack.url_downloading"));
            Label detail = new Label("");
            DlcFxStyle.muted(detail);
            VBox content = new VBox(10, status, dialogProgress, detail);
            content.setAlignment(Pos.CENTER_LEFT);
            urlDownloadProgressBar = dialogProgress;
            urlDownloadStatusLabel = status;
            urlDownloadDetailLabel = detail;
            urlDownloadOverlay = DlcFxModalOverlay.showAndGetOverlay(
                scene(),
                DlcFxI18n.tr("dlc_manager.pack.url_download_progress_title"),
                content,
                () -> {
                    cancelRequested = true;
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
                    if (urlDownloadStatusLabel != null) {
                        urlDownloadStatusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
                    }
                },
                DlcFxI18n.tr("dlc_manager.common.cancel_download"),
                null,
                null,
                DlcFxStyle::danger
            );
        });
    }

    private void closeUrlDownloadProgressDialog() {
        Platform.runLater(() -> {
            if (urlDownloadOverlay != null && urlDownloadOverlay.getParent() instanceof StackPane root) {
                root.getChildren().remove(urlDownloadOverlay);
            }
            urlDownloadOverlay = null;
            urlDownloadProgressBar = null;
            urlDownloadStatusLabel = null;
            urlDownloadDetailLabel = null;
        });
    }

    private Path downloadPackUrl(String url, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        String fileName = DlcDownloadService.suggestFileName(url);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(url));
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "pack.zip";
        } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        fileName = uniqueFileName(outputDir, fileName);
        return DlcDownloadService.download(url, outputDir, fileName, (done, total) -> {
            float progress = total > 0 ? done / (float) total : -1f;
            updateOperationProgress(DlcFxI18n.tr("dlc_manager.pack.url_downloading"), progress, total > 0 ? done + "/" + total : null);
            updateUrlDownloadProgress(progress, done, total);
        }, () -> cancelRequested);
    }

    private void updateUrlDownloadProgress(float progress, long done, long total) {
        Platform.runLater(() -> {
            if (urlDownloadProgressBar != null) {
                urlDownloadProgressBar.setProgress(progress);
            }
            if (urlDownloadStatusLabel != null) {
                urlDownloadStatusLabel.setText(progress >= 0
                    ? DlcFxI18n.tr("dlc_manager.pack.url_downloading_percent", Math.round(Math.max(0f, Math.min(1f, progress)) * 100f))
                    : DlcFxI18n.tr("dlc_manager.pack.url_downloading"));
            }
            if (urlDownloadDetailLabel != null) {
                urlDownloadDetailLabel.setText(total > 0 ? done + "/" + total : "");
            }
        });
    }

    private String uniqueFileName(Path dir, String fileName) {
        if (!Files.exists(dir.resolve(fileName))) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            String candidate = base + "-" + i + ext;
            if (!Files.exists(dir.resolve(candidate))) {
                return candidate;
            }
        }
        return base + "-" + System.nanoTime() + ext;
    }

    private Path chooseZip(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(DlcFxI18n.tr("dlc_manager.update.package_filter"), "*.zip"));
        File file = chooser.showOpenDialog(primaryStage());
        return file == null ? null : file.toPath();
    }

    private Path copyArchiveToPackRoot(Path source) throws IOException {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack");
        Files.createDirectories(packRoot);
        String fileName = source.getFileName() == null ? "pack.zip" : source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        Path defaultTarget = packRoot.resolve(fileName);
        if (source.toAbsolutePath().normalize().equals(defaultTarget.toAbsolutePath().normalize())) {
            return defaultTarget;
        }
        Files.copy(source, defaultTarget, StandardCopyOption.REPLACE_EXISTING);
        return defaultTarget;
    }

    private void showPackConfirm(String title, String message, Runnable onConfirm) {
        DlcFxPackDialogs.confirm(scene(), title, message, onConfirm);
    }

    private void showExternalOpenConfirm(String title, String message, Runnable onConfirm) {
        DlcFxPackLinkActions.showExternalOpenConfirm(scene(), title, message, onConfirm, statusLabel::setText);
    }

    private void finishBusy(String message) {
        finishBusy(message, false);
    }

    private void finishBusy(String message, boolean refreshAfter) {
        Platform.runLater(() -> {
            boolean requiredMayHaveChanged = busyOperation == BusyOperation.APPLY || busyOperation == BusyOperation.DELETE;
            busy = false;
            cancelRequested = false;
            cancelApplyRequested = false;
            paused = false;
            busyOperation = BusyOperation.NONE;
            operationProgress = -1f;
            pausedAllowRuntimeChanges = false;
            statusLabel.setText(message);
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            refreshBtn.setDisable(false);
            applyBtn.setDisable(false);
            applyBtn.setText(DlcFxI18n.tr("dlc_manager.pack.apply"));
            cancelBtn.setDisable(false);
            cancelBtn.setText(DlcFxI18n.tr("dlc_manager.pack.cancel_changes"));
            operationStatusLabel.setText("");
            if (refreshAfter || forceRefreshAfterBusy) {
                forceRefreshAfterBusy = false;
                refreshAsync(false);
            } else {
                updateActionButtons();
            }
            if (requiredMayHaveChanged && DlcFxManagerApp.instance() != null) {
                DlcFxManagerApp.instance().ensureLocalRequiredResources();
            }
        });
    }

    private void setBusyStatus(String status, String operationStatus, String detail) {
        setBusyStatus(status, operationStatus, detail, BusyOperation.NONE);
    }

    private void setBusyStatus(String status, String operationStatus, String detail, BusyOperation operation) {
        Platform.runLater(() -> {
            busy = true;
            cancelRequested = false;
            cancelApplyRequested = false;
            paused = false;
            busyOperation = operation;
            operationProgress = -1f;
            statusLabel.setText(status);
            progressBar.setVisible(true);
            progressBar.setProgress(-1);
            operationStatusLabel.setText(operationStatus);
            if (detail != null && !detail.isBlank()) {
                statusLabel.setText(operationStatus == null || operationStatus.isBlank()
                        ? statusLabel.getText() + " " + detail
                        : statusLabel.getText() + " " + detail);
            }
            updateBusyButtons();
        });
    }

    private void updateOperationProgress(String status, float progress, String detail) {
        Platform.runLater(() -> {
            operationStatusLabel.setText(status);
            if (detail != null && !detail.isBlank()) {
                statusLabel.setText(status + " " + detail);
            }
            if (progress >= 0f) {
                progressBar.setProgress(progress);
            }
            operationProgress = progress;
        });
    }

    private float normalizePhaseProgress(float offset, float span, float progress) {
        if (progress < 0f) {
            return -1f;
        }
        return offset + Math.max(0f, Math.min(1f, progress)) * span;
    }

    private String operationDetail(String message) {
        return message == null || message.isBlank() ? "" : message;
    }

    private String operationStatusFor(OperationDisplayMode mode, PackArchiveEntry entry, String fallback) {
        if ("dlc_manager.pack.selecting_source".equals(fallback)) {
            return DlcFxI18n.tr("dlc_manager.pack.selecting_source");
        }
        return switch (mode) {
            case UNINSTALLING -> DlcFxI18n.tr("dlc_manager.pack.operation_uninstalling");
            case INSTALLING -> DlcFxI18n.tr("dlc_manager.pack.operation_installing");
            case APPLYING -> DlcFxI18n.tr("dlc_manager.pack.operation_applying");
        };
    }

    private String operationDetailFor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "dlc_manager.pack.selecting_source".equals(value)) {
            return null;
        }
        if (value.startsWith("Downloading ")) {
            return DlcFxI18n.tr("dlc_manager.pack.downloading_file", value.substring("Downloading ".length()));
        }
        if (value.startsWith("Resolved ")) {
            return DlcFxI18n.tr("dlc_manager.pack.resolved_file", value.substring("Resolved ".length()));
        }
        if (value.startsWith("Installing ")) {
            return DlcFxI18n.tr("dlc_manager.pack.installing", value.substring("Installing ".length()));
        }
        if (value.startsWith("Uninstalling installed DLC items")) {
            return DlcFxI18n.tr("dlc_manager.pack.operation_uninstalling");
        }
        if (value.startsWith("Uninstalling ")) {
            return DlcFxI18n.tr("dlc_manager.pack.uninstalling", value.substring("Uninstalling ".length()));
        }
        if (value.startsWith("Restoring ")) {
            return DlcFxI18n.tr("dlc_manager.pack.restoring_file", value.substring("Restoring ".length()));
        }
        if (value.startsWith("Preparing ")) {
            return DlcFxI18n.tr("dlc_manager.pack.preparing_pack", value.substring("Preparing ".length()));
        }
        if (value.startsWith("Backing up ")) {
            return DlcFxI18n.tr("dlc_manager.pack.backing_up_pack", value.substring("Backing up ".length()));
        }
        if ("Applying runtime changes".equals(value)) {
            return DlcFxI18n.tr("dlc_manager.pack.applying_runtime");
        }
        if ("Writing metadata".equals(value)) {
            return DlcFxI18n.tr("dlc_manager.pack.writing_metadata");
        }
        if (value.startsWith("Installed ")) {
            return DlcFxI18n.tr("dlc_manager.pack.installed_pack", value.substring("Installed ".length()));
        }
        if (value.startsWith("Uninstalled ")) {
            return DlcFxI18n.tr("dlc_manager.pack.uninstalled_pack", value.substring("Uninstalled ".length()));
        }
        if (value.startsWith("dlc_manager.")) {
            return DlcFxI18n.tr(value);
        }
        return value;
    }

    private void checkCanceled() throws DlcDownloadService.DownloadCanceledException {
        if (cancelRequested) {
            throw new DlcDownloadService.DownloadCanceledException("canceled");
        }
    }

    private void pauseApplyOperation() {
        Platform.runLater(() -> {
            busy = true;
            cancelRequested = false;
            cancelApplyRequested = false;
            paused = true;
            busyOperation = BusyOperation.APPLY;
            operationProgress = -1f;
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.paused"));
            pausedReinstallIdentifiers.clear();
            pausedReinstallIdentifiers.addAll(activeReinstallIdentifiers);
            refreshInstalledPacks();
            rebuildPackWidgets();
            updateBusyButtons();
        });
    }

    private void resumePausedApply() {
        if (!paused || busyOperation != BusyOperation.APPLY) {
            return;
        }
        boolean allowRuntimeChanges = pausedAllowRuntimeChanges;
        busy = false;
        paused = false;
        cancelRequested = false;
        cancelApplyRequested = false;
        busyOperation = BusyOperation.NONE;
        startApplyPendingChanges(allowRuntimeChanges);
    }

    private void requestPauseApplyOperation() {
        if (busyOperation != BusyOperation.APPLY || cancelRequested) {
            return;
        }
        cancelApplyRequested = false;
        cancelRequested = true;
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.pausing"));
        operationStatusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.pausing"));
        updateBusyButtons();
    }

    private void requestCancelApplyOperation() {
        if (busyOperation != BusyOperation.APPLY) {
            return;
        }
        cancelApplyRequested = true;
        if (paused) {
            finishBusy(DlcFxI18n.tr("dlc_manager.pack.canceled"));
            return;
        }
        cancelRequested = true;
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
        operationStatusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
        updateBusyButtons();
    }

    private void updateBusyButtons() {
        if (!busy) {
            applyPackActionState(DlcFxPackActionState.compute(gameDirSelected, false, hasPendingChanges(), false));
            return;
        }
        boolean applying = busyOperation == BusyOperation.APPLY;
        applyPackActionState(DlcFxPackActionState.computeBusy(applying, paused, cancelRequested, cancelApplyRequested, busyOperation == BusyOperation.DELETE));
    }

    private void updateActionButtons() {
        if (busy) {
            updateBusyButtons();
            return;
        }
        boolean hasChanges = hasPendingChanges();
        DlcFxPackActionState state = DlcFxPackActionState.compute(gameDirSelected, false, hasChanges, false);
        applyPackActionState(state);
    }

    private void applyPackActionState(DlcFxPackActionState state) {
        if (shell != null) {
            shell.applyActionState(state);
        } else {
            refreshBtn.setDisable(state.refreshDisabled());
            importBtn.setDisable(state.importDisabled());
            importUrlBtn.setDisable(state.importUrlDisabled());
            applyBtn.setDisable(state.applyDisabled());
            cancelBtn.setDisable(state.cancelDisabled());
            cancelBtn.setVisible(state.cancelVisible());
            cancelBtn.setManaged(state.cancelVisible());
        }
        applyBtn.setText(DlcFxI18n.tr(state.applyTextKey()));
        cancelBtn.setText(DlcFxI18n.tr(state.cancelTextKey()));
    }

    private void rebuildPackWidgets() {
        if ((archiveGroups == null || archiveGroups.isEmpty()) && !entries.isEmpty()) {
            archiveGroups = groupArchiveEntries(entries);
            sortGroupsByCurrentOrder(archiveGroups);
        }
        groups.setAll(archiveGroups);
        if (selectedIndex >= groups.size()) {
            selectedIndex = groups.isEmpty() ? -1 : groups.size() - 1;
        }
        updateActionButtons();
    }

    private boolean isEntryCurrentlyInstalled(PackArchiveEntry entry) {
        if (entry == null) {
            return false;
        }
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        return installed != null && entry.metadata.version.equals(installed.version);
    }

    private void commitCurrentOrderWithoutRestart() {
        committedArchiveOrder = List.copyOf(currentArchiveOrder);
        entryArchiveOrder = List.copyOf(committedArchiveOrder);
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.order_applied"));
        updateActionButtons();
    }

    private boolean hasPendingChanges() {
        return hasPackOperationChanges() || hasOrderChanges();
    }

    private boolean hasPackOperationChanges() {
        return !pendingInstalls.isEmpty() || !pendingUninstalls.isEmpty();
    }

    private boolean hasOrderChanges() {
        return !currentArchiveOrder.equals(entryArchiveOrder);
    }

    private List<String> collectArchiveKeys() {
        return collectArchiveKeys(allGroups());
    }

    private Path packOrderPath() {
        return DlcPaths.migrateLegacyConfigFile("order.dc");
    }

    private PackArchiveGroup findGroup(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        for (PackArchiveGroup group : allGroups()) {
            if (identifier.equals(group.identifier())) {
                return group;
            }
        }
        return null;
    }

    private int committedOrderIndex(String key) {
        return entryArchiveOrder.indexOf(key);
    }

    private boolean orderKeyMatchesIdentifier(String key, String identifier) {
        return identifier != null && identifier.equals(identifierFromOrderKey(key));
    }

    private String identifierFromOrderKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int hash = key.lastIndexOf('#');
        int at = key.lastIndexOf('@');
        if (hash < 0 || at <= hash + 1) {
            return null;
        }
        String identifier = key.substring(hash + 1, at);
        return identifier.isBlank() ? null : identifier;
    }

    private String archiveOrderPath(Path archive) {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack").toAbsolutePath().normalize();
        Path normalized = archive.toAbsolutePath().normalize();
        try {
            return packRoot.relativize(normalized).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return archive.getFileName().toString();
        }
    }

    private String describeFailure(Throwable ex) {
        if (ex instanceof IOException io && io.getMessage() != null && !io.getMessage().isBlank()) {
            return io.getMessage();
        }
        return ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
            ? (ex == null ? "error" : ex.getClass().getSimpleName())
            : ex.getMessage();
    }

    private String describePackBlockingPrecheck(DlcPackInstaller.PrecheckResult precheck) {
        List<String> messages = new ArrayList<>();
        if (!precheck.getIncompatibleVersions().isEmpty()) {
            messages.add(DlcFxI18n.tr("dlc_manager.pack.incompatible_versions", joinLimited(precheck.getIncompatibleVersions())));
        }
        if (!precheck.getMissingDependencies().isEmpty()) {
            messages.add(DlcFxI18n.tr("dlc_manager.pack.missing_dependencies", joinLimited(precheck.getMissingDependencies())));
        }
        if (!precheck.getDlcToEnable().isEmpty()) {
            messages.add(DlcFxI18n.tr("dlc_manager.pack.enable_dlc", joinLimited(precheck.getDlcToEnable())));
        }
        if (!precheck.getPresetsToEnable().isEmpty()) {
            messages.add(DlcFxI18n.tr("dlc_manager.pack.enable_presets", joinLimited(precheck.getPresetsToEnable())));
        }
        if (!precheck.getDlcToDisable().isEmpty()) {
            messages.add(DlcFxI18n.tr("dlc_manager.pack.disable_dlc", joinLimited(precheck.getDlcToDisable())));
        }
        if (!precheck.getPresetsToDisable().isEmpty()) {
            messages.add(DlcFxI18n.tr("dlc_manager.pack.disable_presets", joinLimited(precheck.getPresetsToDisable())));
        }
        return messages.isEmpty() ? "unknown" : String.join("\n", messages);
    }

    private String joinLimited(List<String> values) {
        int limit = Math.min(values.size(), 6);
        String joined = String.join(", ", values.subList(0, limit));
        if (values.size() > limit) {
            joined += ", ...";
        }
        return joined;
    }

    private String safeMessage(Throwable ex) {
        return describeFailure(ex);
    }

    private void onBackOrCancel() {
        if (busy) {
            if (paused && busyOperation == BusyOperation.APPLY) {
                requestCancelApplyOperation();
                return;
            }
            if (busyOperation == BusyOperation.DELETE) {
                return;
            }
            if (busyOperation == BusyOperation.APPLY) {
                requestPauseApplyOperation();
            } else {
                cancelRequested = true;
                statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
                operationStatusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.canceling"));
                updateBusyButtons();
            }
            return;
        }
        if (hasPendingChanges()) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            currentArchiveOrder = List.copyOf(entryArchiveOrder);
            committedArchiveOrder = List.copyOf(entryArchiveOrder);
            DlcPackOrder.save(packOrderPath(), committedArchiveOrder);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.changes_cancelled"));
            updateActionButtons();
            refreshAsync(false);
        }
    }

    private Scene scene() {
        return listView.getScene();
    }

    private Stage primaryStage() {
        return scene() == null ? null : (Stage) scene().getWindow();
    }

    private final class LocalPackApplyActions implements DlcFxPackApplyFlow.Actions {
        @Override
        public boolean enabled() {
            return DlcConfig.isPackFuncOn();
        }

        @Override
        public boolean busy() {
            return busy;
        }

        @Override
        public boolean hasPendingChanges() {
            return DlcFxLocalPackTab.this.hasPendingChanges();
        }

        @Override
        public boolean hasOperationChanges() {
            return hasPackOperationChanges();
        }

        @Override
        public String replacementConfirmText() {
            return shouldBuildConfirmText() ? buildReplacementConfirmText() : null;
        }

        @Override
        public String runtimeConfirmText() {
            return shouldBuildConfirmText() ? buildRuntimeConfirmText() : null;
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.disabled"));
        }

        @Override
        public void commitOrderOnly() {
            commitCurrentOrderWithoutRestart();
        }

        @Override
        public void confirmReplacement(String message, Runnable confirm) {
            showPackConfirm(
                DlcFxI18n.tr("dlc_manager.pack.apply_confirm_title"),
                message,
                confirm
            );
        }

        @Override
        public void confirmRuntime(String message, boolean allowRuntimeChanges, Runnable confirm) {
            showPackConfirm(
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

    private final class LocalPackDeleteActions implements DlcFxPackDeleteFlow.Actions<PackArchiveGroup> {
        @Override
        public boolean enabled() {
            return DlcConfig.isPackFuncOn();
        }

        @Override
        public String displayName(PackArchiveGroup group) {
            PackArchiveEntry entry = group == null ? null : group.displayEntry();
            return entry == null ? "" : entry.displayName;
        }

        @Override
        public boolean installed(PackArchiveGroup group) {
            PackArchiveEntry entry = group == null ? null : group.displayEntry();
            return entry != null && isEntryCurrentlyInstalled(entry);
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.disabled"));
        }

        @Override
        public void confirm(DlcFxPackDeletePlan plan, Runnable onConfirm) {
            showPackConfirm(plan.title(), plan.confirmMessage(), onConfirm);
        }

        @Override
        public void delete(PackArchiveGroup group, DlcFxPackDeletePlan plan) {
            startDeleteArchive(group, plan);
        }
    }

    private final class LocalPackToggleActions implements DlcFxPackToggleFlow.Actions<PackArchiveGroup, PackArchiveEntry> {
        @Override
        public boolean enabled() {
            return DlcConfig.isPackFuncOn();
        }

        @Override
        public PackArchiveEntry displayEntry(PackArchiveGroup group) {
            return group == null ? null : group.displayEntry();
        }

        @Override
        public void disabled() {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.pack.disabled"));
        }

        @Override
        public void toggle(PackArchiveGroup group, PackArchiveEntry entry) {
            String identifier = entry.metadata.identifier;
            DlcPackMeta installed = installedByIdentifier.get(identifier);
            pendingModel.toggleWithInstalledVersion(entry, installed == null ? null : installed.version);
        }

        @Override
        public void refresh() {
            rebuildPackWidgets();
        }
    }

    private final class LocalPackVersionActions implements DlcFxPackVersionFlow.Actions<PackArchiveGroup, PackArchiveEntry> {
        @Override
        public String groupIdentifier(PackArchiveGroup group) {
            return group.identifier();
        }

        @Override
        public String entryKey(PackArchiveEntry entry) {
            return entry.key();
        }

        @Override
        public void select(PackArchiveEntry entry) {
            versionSelectionModel.select(entry);
        }

        @Override
        public void updateGroup(String identifier, String selectedVersionKey) {
            updateArchiveGroup(identifier, selectedVersionKey);
        }

        @Override
        public void refresh() {
            rebuildPackWidgets();
        }
    }

    private final class LocalPackOrderActions implements DlcFxPackOrderFlow.Actions<PackArchiveGroup> {
        @Override
        public List<PackArchiveGroup> items() {
            return archiveGroups;
        }

        @Override
        public List<String> collectOrder(List<PackArchiveGroup> reordered) {
            return collectArchiveKeys(reordered);
        }

        @Override
        public void apply(List<PackArchiveGroup> reordered, PackArchiveGroup moved, List<String> order) {
            currentArchiveOrder = List.copyOf(order);
            DlcPackOrder.save(packOrderPath(), currentArchiveOrder);
            archiveGroups = new ArrayList<>(reordered);
            groups.setAll(archiveGroups);
            selectedIndex = Math.max(0, groups.indexOf(moved));
            updateActionButtons();
        }
    }

    private final class PackCellAdapter implements DlcFxPackGroupCell.Adapter<PackArchiveGroup, PackArchiveEntry> {
        @Override
        public PackArchiveEntry displayEntry(PackArchiveGroup group) {
            return group == null ? null : group.displayEntry();
        }

        @Override
        public DlcFxPackRowState rowState(PackArchiveGroup group, PackArchiveEntry entry, int rowIndex, int rowCount) {
            return DlcFxPackRowState.of(
                rowIndex,
                rowCount,
                group.versions().size(),
                entry.metadata.links,
                isEntryCurrentlyInstalled(entry)
            );
        }

        @Override
        public Node versionLine(PackArchiveGroup group) {
            DlcFxPackRowState rowState = rowState(group, group.displayEntry(), 0, 0);
            List<DlcFxPackRowView.VersionOption> options = new ArrayList<>();
            for (PackArchiveEntry option : group.versions()) {
                options.add(new DlcFxPackRowView.VersionOption(
                    option.key(),
                    versionOptionText(option),
                    option.key().equals(group.displayEntry().key())
                ));
            }
            return DlcFxPackRowView.versionLine(
                versionOptionText(group.displayEntry()),
                rowState.canSwitchVersion(),
                options,
                selected -> selectPackVersion(group, entryByKey(group, selected.key()))
            );
        }

        @Override
        public String title(PackArchiveEntry entry) {
            return entry.displayName;
        }

        @Override
        public String author(PackArchiveEntry entry) {
            return DlcFxI18n.tr("dlc_manager.pack.author_prefix", entry.author);
        }

        @Override
        public String description(PackArchiveEntry entry) {
            return entry.description == null || entry.description.isBlank() ? entry.metadata.identifier : entry.description;
        }

        @Override
        public Image icon(PackArchiveEntry entry) {
            return entry.icon;
        }

        @Override
        public String iconFallback(PackArchiveEntry entry) {
            return entry.displayName;
        }

        @Override
        public String actionText(PackArchiveGroup group) {
            PackArchiveEntry entry = group.displayEntry();
            DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
            DlcFxPackPendingActionModel.Action action = pendingModel.actionWithInstalledVersion(entry, installed == null ? null : installed.version);
            return DlcFxI18n.tr(DlcFxPackPendingActionModel.translationKey(action));
        }

        @Override
        public void onPrimaryPress(PackArchiveGroup group, PackArchiveEntry entry, int rowIndex) {
            selectedIndex = rowIndex;
        }

        @Override
        public void openHomepage(PackArchiveEntry entry) {
            confirmOpenLink(entry, "homepage");
        }

        @Override
        public void moveUp(PackArchiveGroup group) {
            moveGroup(group, -1);
        }

        @Override
        public void moveDown(PackArchiveGroup group) {
            moveGroup(group, 1);
        }

        @Override
        public void toggleInstall(PackArchiveGroup group) {
            DlcFxLocalPackTab.this.toggleInstall(group);
        }

        @Override
        public void deleteArchive(PackArchiveGroup group) {
            confirmDeleteArchive(group);
        }

        @Override
        public void openIssue(PackArchiveEntry entry) {
            confirmOpenLink(entry, "issue");
        }

        @Override
        public void openSponsor(PackArchiveEntry entry) {
            confirmOpenLink(entry, "sponsor");
        }

        private PackArchiveEntry entryByKey(PackArchiveGroup group, String key) {
            if (group != null) {
                for (PackArchiveEntry entry : group.versions()) {
                    if (entry != null && entry.key().equals(key)) {
                        return entry;
                    }
                }
            }
            return group == null ? null : group.displayEntry();
        }

        private String versionOptionText(PackArchiveEntry entry) {
            if (isEntryCurrentlyInstalled(entry)) {
                return DlcFxI18n.tr("dlc_manager.pack.version_current_short", entry.metadata.version);
            }
            return entry.metadata.version;
        }

    }

    private record PackArchiveEntry(
        Path archivePath,
        String archiveOrderPath,
        DlcPackMetadata metadata,
        String displayName,
        String description,
        String author,
        DlcPackPackage.Format format,
        Image icon,
        List<DlcPackManifest.FileRef> manifestFiles
    ) {
        private String key() {
            return archiveOrderPath + "#" + metadata.identifier + "@" + metadata.version;
        }

        @Override
        public String toString() {
            return isBlank(displayName) ? metadata.version : displayName + " - " + metadata.version;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    private record PackArchiveGroup(String identifier, List<PackArchiveEntry> versions, String orderKey, String selectedVersionKey) {
        private PackArchiveEntry displayEntry() {
            for (PackArchiveEntry entry : versions) {
                if (entry.key().equals(selectedVersionKey)) {
                    return entry;
                }
            }
            return versions.isEmpty() ? null : versions.get(0);
        }

        private PackArchiveGroup withSelectedVersionKey(String key) {
            return new PackArchiveGroup(identifier, versions, orderKey, key);
        }
    }

    private enum BusyOperation {
        NONE,
        IMPORT,
        APPLY,
        DELETE
    }

    private enum OperationDisplayMode {
        APPLYING,
        INSTALLING,
        UNINSTALLING
    }
}
