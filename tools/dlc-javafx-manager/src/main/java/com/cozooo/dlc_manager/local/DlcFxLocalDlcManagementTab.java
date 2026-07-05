package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementFlow;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementShell;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcTogglePlanner;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.core.DlcType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcFxLocalDlcManagementTab {

    private final DlcFxPlatformProvider provider;

    private final ObservableList<DlcItem> allItems = FXCollections.observableArrayList();
    private final ObservableList<DlcRedirectEntry> redirectEntries = FXCollections.observableArrayList();
    private final Label countLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.ready"));
    private Button rescanBtn;
    private Button openDlcDirBtn;
    private DlcFxDlcManagementShell<DlcItem> shell;
    private DlcFxDlcManagementFlow<DlcItem> flow;
    private boolean gameDirSelected;
    private boolean loadedForCurrentGameDir;
    private Runnable capturePresetSnapshot = () -> {};
    private Runnable restorePresetSnapshot = () -> {};
    private Runnable resetPresetSnapshot = () -> {};
    private Runnable applyPresetOwnedDlcOverrides = () -> {};

    public DlcFxLocalDlcManagementTab(DlcFxPlatformProvider provider) {
        this.provider = provider;
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);
    }

    public void setGameDirSelected(boolean selected) {
        this.gameDirSelected = selected;
    }

    public void setPresetSnapshotActions(Runnable capturePresetSnapshot,
                                         Runnable restorePresetSnapshot,
                                         Runnable resetPresetSnapshot,
                                         Runnable applyPresetOwnedDlcOverrides) {
        this.capturePresetSnapshot = capturePresetSnapshot == null ? () -> {} : capturePresetSnapshot;
        this.restorePresetSnapshot = restorePresetSnapshot == null ? () -> {} : restorePresetSnapshot;
        this.resetPresetSnapshot = resetPresetSnapshot == null ? () -> {} : resetPresetSnapshot;
        this.applyPresetOwnedDlcOverrides = applyPresetOwnedDlcOverrides == null ? () -> {} : applyPresetOwnedDlcOverrides;
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.ready"));

        flow = new DlcFxDlcManagementFlow<>(new LocalDlcFlowAdapter(), new LocalDlcPrompts());
        shell = new DlcFxDlcManagementShell<>(
            new LocalDlcAdapter(),
            statusLabel,
            progressBar,
            this::applyAsync,
            this::resetChanges,
            items -> flow.enable(items),
            items -> flow.disable(new ArrayList<>(items))
        );
        shell.setAvailable(gameDirSelected);
        shell.setRedirectOpenHandler(this::confirmOpenRedirect);

        rescanBtn = new Button(DlcFxI18n.tr("dlc_manager.local.scan_dlc"));
        rescanBtn.setDisable(!gameDirSelected);
        rescanBtn.setOnAction(e -> {
            if (gameDirSelected) {
                scanAsync();
            }
        });

        openDlcDirBtn = new Button(DlcFxI18n.tr("dlc_manager.local.open_dlc_dir"));
        openDlcDirBtn.setDisable(!gameDirSelected);
        openDlcDirBtn.setOnAction(e -> openDlcDirectory());
        DlcFxStyle.primary(rescanBtn);
        DlcFxStyle.muted(countLabel);

        HBox listBox = shell.listFrame();
        VBox.setVgrow(listBox, Priority.ALWAYS);

        VBox centerBox = new VBox(10, shell.filterBar(), listBox);
        page.setCenter(page.section(centerBox));

        Region topSpacer2 = new Region();
        HBox.setHgrow(topSpacer2, Priority.ALWAYS);
        HBox topBox = new HBox(10, rescanBtn, openDlcDirBtn, countLabel, topSpacer2, shell.selectionActions());
        topBox.setAlignment(Pos.CENTER_LEFT);
        page.setTop(DlcFxToolbar.vertical(topBox));

        DlcFxStyle.routeStatus(statusLabel);
        page.setBottom(shell.bottomPanel());

        updateActionState();
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.dlc"), page.root());
    }

    public void onGameDirChanged() {
        gameDirSelected = true;
        loadedForCurrentGameDir = false;
        if (rescanBtn != null) {
            rescanBtn.setDisable(false);
        }
        if (openDlcDirBtn != null) {
            openDlcDirBtn.setDisable(false);
        }
        allItems.clear();
        redirectEntries.clear();
        if (shell != null) {
            shell.setAvailable(true);
            shell.setItems(allItems);
            shell.setRedirectEntries(redirectEntries);
        }
        countLabel.setText("");
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.game_dir_changed"));
        updateActionState();
        if (shell != null) {
            scanAsync();
        }
    }

    public void onTabSelected() {
        if (!gameDirSelected) {
            return;
        }
        if (loadedForCurrentGameDir) {
            syncPresetOverridesIntoItems();
            refreshRowsAfterStateChange();
            return;
        }
        scanAsync();
    }

    public void scanSelectedGameDir() {
        scanAsync();
    }

    private void openDlcDirectory() {
        List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
        if (roots.isEmpty()) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.no_dlc_root"));
            return;
        }
        java.nio.file.Path dlcRoot = roots.get(0);
        if (!java.nio.file.Files.exists(dlcRoot)) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.dlc_dir_missing", dlcRoot));
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(dlcRoot.toFile());
        } catch (Exception ex) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.open_failed", safeMessage(ex)));
        }
    }

    private void scanAsync() {
        if (!gameDirSelected) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            return;
        }
        loadedForCurrentGameDir = true;
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        if (shell != null) {
            shell.setBusy(true);
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scanning"));
        Thread thread = new Thread(() -> {
            try {
                List<java.nio.file.Path> roots = DlcConfig.getDlcRoots();
                boolean[] anyRootExists = {false};
                for (java.nio.file.Path root : roots) {
                    boolean exists = java.nio.file.Files.exists(root);
                    if (exists) anyRootExists[0] = true;
                }

                DlcScanner scanner = new DlcScanner();
                Map<DlcType, List<DlcItem>> scanned = scanner.scan(true);
                List<DlcItem> flat = new ArrayList<>();
                List<DlcRedirectEntry> redirects = loadVisibleRedirectEntries(roots);
                for (DlcType type : DlcType.values()) {
                    if (!isVisibleDlcType(type)) {
                        continue;
                    }
                    List<DlcItem> group = scanned.get(type);
                    if (group != null) {
                        flat.addAll(group);
                    }
                }
                if (DlcConfig.isPresetFuncOn()) {
                    DlcPresetRuntimeState.reload();
                } else {
                    DlcPresetRuntimeState.clearAll();
                }
                for (DlcItem item : flat) {
                    if (item == null) {
                        continue;
                    }
                    boolean enabled = item.isEnabled();
                    DlcRuntimeState.ensureBaseline(item.getId(), enabled);
                    Boolean override = DlcRuntimeState.getOverride(item.getId());
                    if (override != null) {
                        item.setEnabled(override);
                    }
                }
                DlcRuntimeState.updateItemIndex(flat);
                DlcPresetRuntimeState.applyOverridesToRuntime();
                for (DlcItem item : flat) {
                    if (item == null) {
                        continue;
                    }
                    Boolean override = DlcRuntimeState.getOverride(item.getId());
                    if (override != null) {
                        item.setEnabled(override);
                    }
                }
                final int total = flat.size();
                Platform.runLater(() -> {
                    allItems.setAll(flat);
                    redirectEntries.setAll(redirects);
                    if (shell != null) {
                        shell.setItems(allItems);
                        shell.setRedirectEntries(redirectEntries);
                    }
                    countLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", total));
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    if (total == 0) {
                        String msg = anyRootExists[0]
                            ? DlcFxI18n.tr("dlc_manager.local.no_dlc_items")
                            : DlcFxI18n.tr("dlc_manager.local.no_dlc_dir");
                        statusLabel.setText(msg);
                    } else {
                        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scan_done"));
                    }
                    updateActionState();
                });
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.scan_failed", safeMessage(t)));
                    updateActionState();
                });
            }
        }, "dlc-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAsync() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        if (shell != null) {
            shell.setBusy(true);
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        updateActionState();
        Thread thread = new Thread(() -> {
            try {
                DlcApplier.ApplyResult result = DlcApplier.apply(new ArrayList<>(allItems), DlcApplyMode.RESTART_ONLY);
                DlcStateConfig.save();
                if (DlcConfig.isPresetFuncOn()) {
                    DlcPresetRuntimeState.syncStatusFromRuntime();
                    resetPresetSnapshot.run();
                }
                for (DlcItem item : allItems) {
                    if (item == null) {
                        continue;
                    }
                    DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled());
                    DlcRuntimeState.clearOverride(item.getId());
                }
                Platform.runLater(() -> {
                    if (shell != null) {
                        shell.commitCurrentState();
                    }
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    if (shell != null) {
                        shell.refreshRows();
                    }
                    List<String> failureMessages = applyFailureMessages(result);
                    StringBuilder sb = new StringBuilder(DlcFxI18n.tr("dlc_manager.status.apply_done"));
                    if (!failureMessages.isEmpty()) {
                        sb.append(DlcFxI18n.tr("dlc_manager.status.message_count_suffix", failureMessages.size()));
                    }
                    statusLabel.setText(sb.toString());
                    if (!failureMessages.isEmpty()) {
                        StringBuilder tip = new StringBuilder();
                        for (String msg : failureMessages) {
                            if (tip.length() > 0) tip.append('\n');
                            tip.append(msg);
                        }
                        statusLabel.setTooltip(DlcFxRichTextRenderer.formattedTooltip(tip.toString()));
                    } else {
                        statusLabel.setTooltip(null);
                    }
                    updateActionState();
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    if (shell != null) {
                        shell.setBusy(false);
                    }
                    statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", safeMessage(t)));
                    updateActionState();
                });
            }
        }, "dlc-apply");
        thread.setDaemon(true);
        thread.start();
    }

    public void applyPendingChanges() {
        if (hasPendingChanges()) {
            applyAsync();
        }
    }

    private List<String> applyFailureMessages(DlcApplier.ApplyResult result) {
        if (result == null || result.messages() == null || result.messages().isEmpty()) {
            return List.of();
        }
        List<String> failures = new ArrayList<>();
        for (String message : result.messages()) {
            if (message != null && message.startsWith("Failed to ")) {
                failures.add(message);
            }
        }
        return failures;
    }

    private void resetChanges() {
        restorePresetSnapshot.run();
        for (DlcItem item : allItems) {
            if (item == null) {
                continue;
            }
            Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
            boolean enabled = baseline == null ? item.isEnabled() : baseline;
            item.setEnabled(enabled);
        }
        DlcRuntimeState.clearOverrides();
        syncPresetStatusesFromCurrentItems();
        if (shell != null) {
            shell.resetChanges();
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.cancel_done"));
        updateActionState();
    }

    public void cancelPendingChanges() {
        resetChanges();
    }

    public List<DlcItem> currentItemsSnapshot() {
        return List.copyOf(allItems);
    }

    private String buildAlarmTip(List<DlcItem> items) {
        StringBuilder sb = new StringBuilder();
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(item.getName() == null ? item.getIdentifier() : item.getName());
        }
        return sb.toString();
    }

    private String buildConflictMessage(List<DlcFxDlcManagementFlow.ConflictLine<DlcItem>> lines) {
        StringBuilder sb = new StringBuilder();
        for (DlcFxDlcManagementFlow.ConflictLine<DlcItem> line : lines) {
            if (line == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.leftDisplayName()).append(" <-> ").append(line.rightDisplayName());
        }
        return sb.toString();
    }

    private String buildDependencyMessage(List<DlcFxDlcManagementFlow.DependencyLine<DlcItem>> lines) {
        StringBuilder message = new StringBuilder();
        for (DlcFxDlcManagementFlow.DependencyLine<DlcItem> line : lines) {
            if (line == null) {
                continue;
            }
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append(line.itemDisplayName()).append(" -> ").append(line.dependencyDisplayName());
        }
        return message.toString();
    }

    private List<DlcFxDlcTogglePlanner.Item> plannerItems(List<DlcItem> source) {
        List<DlcFxDlcTogglePlanner.Item> out = new ArrayList<>();
        for (DlcItem item : source == null ? List.<DlcItem>of() : source) {
            if (item == null) {
                continue;
            }
            out.add(new DlcFxDlcTogglePlanner.Item(
                    item.getId(),
                    item.getIdentifier(),
                    item.isEnabled(),
                    item.getDependencies(),
                    item.getConflicts(),
                    item.AllowEnableByAll(),
                    item.isAlarm(),
                    item.isHidden()));
        }
        return out;
    }

    private List<String> itemIds(Iterable<DlcItem> items) {
        List<String> ids = new ArrayList<>();
        if (items != null) {
            for (DlcItem item : items) {
                if (item != null && item.getId() != null && !item.getId().isBlank()) {
                    ids.add(item.getId());
                }
            }
        }
        return ids;
    }

    private List<DlcItem> itemsByIds(List<String> ids) {
        Map<String, DlcItem> byId = new java.util.LinkedHashMap<>();
        for (DlcItem item : allItems) {
            if (item != null && item.getId() != null) {
                byId.put(item.getId(), item);
            }
        }
        List<DlcItem> out = new ArrayList<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            DlcItem item = byId.get(id);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private DlcItem itemByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        for (DlcItem item : allItems) {
            if (item != null && identifier.equals(item.getIdentifier())) {
                return item;
            }
        }
        return null;
    }

    private boolean isVisibleDlcType(DlcType type) {
        return type != DlcType.DLC_PACK
            && (type != DlcType.TACZ_PACK || provider.hasTaczSupportJar());
    }

    private List<DlcRedirectEntry> loadVisibleRedirectEntries(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        String lang = com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode();
        List<DlcRedirectEntry> out = new ArrayList<>();
        for (Path root : roots) {
            for (DlcRedirectEntry entry : DlcRedirectLoader.load(root)) {
                if (entry != null && !entry.isHidden() && entry.isAvailableFor(lang)) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    private String resolveGroupLabel(DlcItem item) {
        try {
            Path source = item.getSourcePath();
            String relative = null;
            for (Path dlcRoot : DlcConfig.getDlcRoots()) {
                if (source != null && source.startsWith(dlcRoot)) {
                    relative = dlcRoot.relativize(source).toString().replace('\\', '/');
                    break;
                }
            }
            if (relative == null && source != null) {
                relative = DlcPaths.gameDir().relativize(source).toString().replace('\\', '/');
            }
            if (relative == null) {
                return "__root__";
            }
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash <= 0) {
                return "__root__";
            }
            return relative.substring(0, lastSlash);
        } catch (Exception ignored) {
            return "__root__";
        }
    }

    private String resolvePathLabel(String raw) {
        if ("__root__".equals(raw)) {
            return DlcFxI18n.tr("dlc_manager.group.root");
        }
        return raw;
    }

    private void refreshRowsAfterStateChange() {
        capturePresetSnapshot.run();
        for (DlcItem item : allItems) {
            if (item != null) {
                item.setDirty(isDirty(item));
            }
        }
        if (shell != null) {
            shell.refreshRows();
        }
        updateActionState();
    }

    private void syncPresetOverridesIntoItems() {
        if (!DlcConfig.isPresetFuncOn()) {
            return;
        }
        capturePresetSnapshot.run();
        DlcPresetRuntimeState.applyOverridesToRuntime();
        applyPresetOwnedDlcOverrides.run();
        for (DlcItem item : allItems) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
        }
    }

    private void syncPresetStatusesFromCurrentItems() {
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.syncStatusFromRuntime();
        }
    }

    public boolean hasPendingChanges() {
        return shell != null && shell.hasPendingChanges();
    }

    private boolean isDirty(DlcItem item) {
        return shell != null && shell.model().isDirty(item);
    }

    private boolean baselineEnabled(DlcItem item) {
        if (item == null) {
            return false;
        }
        Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
        return baseline == null ? item.isEnabled() : baseline;
    }

    private String selectionId(DlcItem item) {
        if (item == null) {
            return "";
        }
        String id = item.getId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        String identifier = item.getIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        Path source = item.getSourcePath();
        return source == null ? String.valueOf(System.identityHashCode(item)) : source.toAbsolutePath().normalize().toString();
    }

    private boolean hasOnlyResolvableDependencies(DlcItem item) {
        java.util.Set<String> missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item);
        if (missing.isEmpty()) {
            return true;
        }
        for (String dependency : missing) {
            if (DlcRuntimeState.getItemByIdentifier(dependency) == null) {
                return false;
            }
        }
        return true;
    }

    private void updateActionState() {
        if (shell != null) {
            shell.setAvailable(gameDirSelected);
            shell.refreshRows();
            return;
        }
    }

    private String displayName(DlcItem item) {
        if (item == null) {
            return "";
        }
        Map<String, String> names = item.getDisplayNames();
        if (names != null && !names.isEmpty()) {
            String lang = com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode();
            if (lang != null && names.containsKey(lang)) {
                return names.get(lang);
            }
            if (names.containsKey("en_us")) {
                return names.get("en_us");
            }
            String first = names.values().stream().findFirst().orElse(null);
            if (first != null && !first.isBlank()) {
                return first;
            }
        }
        return item.getName() != null ? item.getName() : item.getId();
    }

    private String presetDisabledSuffix(DlcItem item) {
        if (item == null || !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())) {
            return "";
        }
        return DlcFxI18n.tr("dlc_manager.preset.disabled_by_preset");
    }

    private void confirmOpenItemRedirect(DlcItem item) {
        if (item == null || item.getRedirectUrl() == null || item.getRedirectUrl().isBlank()) {
            return;
        }
        confirmOpenRedirect(item.getRedirectUrl(), "");
    }

    private void confirmOpenRedirect(DlcRedirectEntry entry) {
        if (entry == null || entry.getRedirectUrl() == null || entry.getRedirectUrl().isBlank()) {
            return;
        }
        String lang = com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode();
        confirmOpenRedirect(entry.getRedirectUrl(), entry.resolveNotice(lang));
    }

    private void confirmOpenRedirect(String url, String notice) {
        if (url == null || url.isBlank()) {
            return;
        }
        String message = DlcFxI18n.tr("dlc_manager.redirect.prompt") + "\n" + url;
        if (notice != null && !notice.isBlank()) {
            message += "\n\n" + notice;
        }
        DlcFxModalOverlay.show(
            overlayScene(),
            DlcFxI18n.tr("dlc_manager.redirect.title"),
            DlcFxRichTextRenderer.formatted(message),
            () -> openLink(url),
            DlcFxI18n.tr("dlc_manager.redirect.open"),
            () -> {},
            DlcFxI18n.tr("dlc_manager.common.cancel"),
            () -> copyLink(url),
            DlcFxI18n.tr("dlc_manager.redirect.copy")
        );
    }

    private javafx.scene.Scene overlayScene() {
        return shell == null || shell.listFrame() == null ? null : shell.listFrame().getScene();
    }

    private void copyLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.redirect.copied"));
    }

    private void openLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.open_failed", safeMessage(ex)));
        }
    }

    private final class LocalDlcAdapter implements DlcFxDlcManagementShell.Adapter<DlcItem> {
        @Override
        public String id(DlcItem item) {
            return selectionId(item);
        }

        @Override
        public String displayName(DlcItem item) {
            return DlcFxLocalDlcManagementTab.this.displayName(item);
        }

        @Override
        public String typeText(DlcItem item) {
            return item == null || item.getType() == null ? "?" : DlcFxI18n.tr(item.getType().getDisplayKey());
        }

        @Override
        public boolean baselineEnabled(DlcItem item) {
            return DlcFxLocalDlcManagementTab.this.baselineEnabled(item);
        }

        @Override
        public boolean effectiveEnabled(DlcItem item) {
            return item != null && item.isEnabled();
        }

        @Override
        public void setEffectiveEnabled(DlcItem item, boolean enabled) {
            if (item != null) {
                item.setEnabled(enabled);
                DlcRuntimeState.setOverride(item.getId(), enabled);
            }
        }

        @Override
        public DlcType type(DlcItem item) {
            return item == null ? null : item.getType();
        }

        @Override
        public String folder(DlcItem item) {
            return resolveGroupLabel(item);
        }

        @Override
        public String folderLabel(String raw) {
            return resolvePathLabel(raw);
        }

        @Override
        public boolean visibleType(DlcType type) {
            return isVisibleDlcType(type);
        }

        @Override
        public boolean canEnable(DlcItem item) {
            return item != null
                && item.AllowEnableByAll()
                && hasOnlyResolvableDependencies(item)
                && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier());
        }

        @Override
        public boolean canDisable(DlcItem item) {
            return item != null;
        }

        @Override
        public boolean missingDependencies(DlcItem item) {
            return DlcRuntimeState.hasMissingDependencies(item);
        }

        @Override
        public boolean blocked(DlcItem item) {
            return item != null && DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier());
        }

        @Override
        public String blockedText(DlcItem item) {
            return presetDisabledSuffix(item);
        }

        @Override
        public String tooltip(DlcItem item) {
            return buildTooltip(item);
        }

        @Override
        public void openRedirect(DlcItem item) {
            confirmOpenItemRedirect(item);
        }
    }

    private final class LocalDlcFlowAdapter implements DlcFxDlcManagementFlow.Adapter<DlcItem> {
        @Override
        public List<DlcItem> allItems() {
            return List.copyOf(allItems);
        }

        @Override
        public String id(DlcItem item) {
            return selectionId(item);
        }

        @Override
        public String identifier(DlcItem item) {
            return item == null || item.getIdentifier() == null ? "" : item.getIdentifier();
        }

        @Override
        public String displayName(DlcItem item) {
            return DlcFxLocalDlcManagementTab.this.displayName(item);
        }

        @Override
        public boolean alarm(DlcItem item) {
            return item != null && item.isAlarm();
        }

        @Override
        public DlcFxDlcManagementFlow.ConflictBatch<DlcItem> collectConflicts(List<DlcItem> targets) {
            DlcRuntimeState.ConflictBatchResult result = DlcRuntimeState.collectConflicts(targets);
            List<DlcFxDlcManagementFlow.ConflictLine<DlcItem>> lines = new ArrayList<>();
            for (DlcRuntimeState.ConflictLine line : result.getLines()) {
                if (line == null) {
                    continue;
                }
                DlcItem left = itemByIdentifier(line.getLeftName());
                DlcItem right = itemByIdentifier(line.getRightName());
                lines.add(new DlcFxDlcManagementFlow.ConflictLine<>(
                        left,
                        right,
                        line.getLeftName(),
                        line.getRightName(),
                        resolveDisplayName(line.getLeftName()),
                        resolveDisplayName(line.getRightName())
                ));
            }
            return new DlcFxDlcManagementFlow.ConflictBatch<>(lines);
        }

        @Override
        public DlcFxDlcManagementFlow.DependencyBatch<DlcItem> collectMissingDependencies(List<DlcItem> targets) {
            DlcRuntimeState.DependencyBatchResult result = DlcRuntimeState.collectMissingDependencies(targets);
            List<DlcFxDlcManagementFlow.DependencyLine<DlcItem>> lines = new ArrayList<>();
            for (DlcRuntimeState.DependencyLine line : result.getLines()) {
                if (line == null) {
                    continue;
                }
                DlcItem item = itemByIdentifier(line.getItemName());
                DlcItem dependency = itemByIdentifier(line.getDependencyName());
                lines.add(new DlcFxDlcManagementFlow.DependencyLine<>(
                        item,
                        dependency,
                        resolveDisplayName(line.getItemName()),
                        resolveDisplayName(line.getDependencyName())
                ));
            }
            return new DlcFxDlcManagementFlow.DependencyBatch<>(lines, result.getItemsToEnable());
        }

        @Override
        public List<DlcItem> planDisable(List<DlcItem> targets) {
            return itemsByIds(DlcFxDlcTogglePlanner.planDisable(plannerItems(allItems), itemIds(targets)));
        }

        @Override
        public void applyEnabled(List<DlcItem> items, boolean enabled) {
            for (DlcItem item : items == null ? List.<DlcItem>of() : items) {
                if (item != null) {
                    item.setEnabled(enabled);
                    DlcRuntimeState.setOverride(item.getId(), enabled);
                }
            }
        }

        @Override
        public String conflictFilterKey(DlcItem item) {
            return identifier(item);
        }

        @Override
        public void clearSelection() {
            if (shell != null) {
                shell.model().clearSelection();
            }
        }

        @Override
        public void refreshRows() {
            refreshRowsAfterStateChange();
        }
    }

    private final class LocalDlcPrompts implements DlcFxDlcManagementFlow.Prompts<DlcItem> {
        @Override
        public void showConflict(List<DlcFxDlcManagementFlow.ConflictLine<DlcItem>> lines, Runnable skipConflictingTargets) {
            DlcFxModalOverlay.show(
                    overlayScene(),
                    DlcFxI18n.tr("dlc_manager.batch.conflicts_title"),
                    DlcFxRichTextRenderer.formatted(buildConflictMessage(lines)),
                    skipConflictingTargets,
                    DlcFxI18n.tr("dlc_manager.batch.conflicts_skip_enable"),
                    () -> {},
                    DlcFxI18n.tr("dlc_manager.common.cancel")
            );
        }

        @Override
        public void showDependencies(List<DlcFxDlcManagementFlow.DependencyLine<DlcItem>> lines, Runnable enableDependencies) {
            DlcFxModalOverlay.show(
                    overlayScene(),
                    DlcFxI18n.tr("dlc_manager.batch.dependencies_title"),
                    DlcFxRichTextRenderer.formatted(buildDependencyMessage(lines)),
                    enableDependencies,
                    DlcFxI18n.tr("dlc_manager.batch.enable_deps", DlcFxI18n.tr("dlc_manager.scope.group")),
                    () -> {},
                    DlcFxI18n.tr("dlc_manager.common.cancel")
            );
        }

        @Override
        public void showAlarm(List<DlcItem> items, Runnable confirm) {
            DlcFxModalOverlay.show(
                    overlayScene(),
                    DlcFxI18n.tr("dlc_manager.alarm.title"),
                    DlcFxRichTextRenderer.formatted(buildAlarmTip(items)),
                    confirm,
                    DlcFxI18n.tr("dlc_manager.confirm_yes"),
                    () -> {},
                    DlcFxI18n.tr("dlc_manager.confirm_no")
            );
        }
    }

    private String buildTooltip(DlcItem item) {
        if (item == null) {
            return "";
        }
        StringBuilder tip = new StringBuilder();
        appendBlock(tip, resolveTooltipText(item));
        String redirectUrl = item.getRedirectUrl();
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            appendBlock(tip, "§b" + DlcFxI18n.tr("dlc_manager.tooltip.double_click_redirect") + "§r");
        }
        List<String> blockers = DlcPresetRuntimeState.getDlcBlockerNames(item.getIdentifier(),
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        if (!blockers.isEmpty()) {
            for (String blocker : blockers) {
                appendBlock(tip, "§4" + DlcFxI18n.tr("dlc_manager.preset.conflict", blocker) + "§r");
            }
        }
        java.util.Set<String> missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item);
        if (!missing.isEmpty()) {
            appendBlock(tip, "§c" + DlcFxI18n.tr("dlc_manager.tooltip.missing_dependencies") + ":§r");
            for (String dep : missing) {
                appendLine(tip, resolveDisplayName(dep));
            }
        }
        List<String> deps = collectDependencyNamesRecursive(item);
        if (!deps.isEmpty()) {
            appendBlock(tip, "§6" + DlcFxI18n.tr("dlc_manager.tooltip.dependencies") + ":§r");
            for (String dep : deps) {
                appendLine(tip, resolveDisplayName(dep));
            }
        }
        if (!item.getConflicts().isEmpty()) {
            appendBlock(tip, "§4" + DlcFxI18n.tr("dlc_manager.tooltip.conflicts") + ":§r");
            for (String conflict : item.getConflicts()) {
                appendLine(tip, resolveDisplayName(conflict));
            }
        }
        return tip.toString();
    }

    private static String resolveTooltipText(DlcItem item) {
        if (item == null || item.getTooltips().isEmpty()) {
            return "";
        }
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = item.getTooltips().get(lang);
        if (value == null || value.isBlank()) {
            value = item.getTooltips().get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = item.getTooltips().values().stream().findFirst().orElse("");
        }
        return value == null ? "" : value;
    }

    private static String resolveDisplayName(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
        if (item == null || item.getDisplayNames().isEmpty()) {
            return identifier;
        }
        String lang = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(
                com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode());
        String value = item.getDisplayNames().get(lang);
        if (value == null || value.isBlank()) {
            value = item.getDisplayNames().get("en_us");
        }
        if (value == null || value.isBlank()) {
            value = item.getDisplayNames().values().stream().findFirst().orElse(identifier);
        }
        return value == null || value.isBlank() ? identifier : value;
    }

    private static List<String> collectDependencyNamesRecursive(DlcItem item) {
        if (item == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        addDependencyNames(item.getDependencies(), result, queue);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (name == null || name.isBlank()) {
                continue;
            }
            DlcItem depItem = DlcRuntimeState.getItemByIdentifier(name);
            if (depItem != null) {
                addDependencyNames(depItem.getDependencies(), result, queue);
            }
        }
        return new ArrayList<>(result);
    }

    private static void addDependencyNames(List<String> names, Set<String> result, java.util.ArrayDeque<String> queue) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isBlank() && result.add(name)) {
                queue.add(name);
            }
        }
    }

    private static void appendBlock(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(value);
    }

    private static void appendLine(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

}
