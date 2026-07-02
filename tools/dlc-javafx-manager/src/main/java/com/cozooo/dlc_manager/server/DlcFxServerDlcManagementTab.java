package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerRedirectItem;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementFlow;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementListModel;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementShell;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcTogglePlanner;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxPageActions;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxTooltipText;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;
import com.cozooo.dlc_manager.ui.preset.DlcFxPresetResolver;

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcFxServerDlcManagementTab {

    private final DlcFxServerConnection conn;
    private final Label statusLabel = new Label(DlcFxI18n.tr("dlc_manager.status.disconnected"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button refreshBtn = DlcFxPageActions.primary(DlcFxI18n.tr("dlc_manager.local.scan_dlc"), null);
    private final RemoteDlcAdapter adapter = new RemoteDlcAdapter();
    private DlcFxDlcManagementShell<DlcServerItem> shell;
    private DlcFxDlcManagementFlow<DlcServerItem> flow;
    private boolean loadedForCurrentConnection;
    private final Set<String> presetAppliedIdentifiers = new LinkedHashSet<>();

    public DlcFxServerDlcManagementTab(DlcFxServerConnection conn) {
        this.conn = conn;
        conn.connectedProperty().addListener((obs, oldValue, newValue) -> loadedForCurrentConnection = false);
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage(DlcFxI18n.tr("dlc_manager.status.disconnected"));

        flow = new DlcFxDlcManagementFlow<>(new RemoteDlcFlowAdapter(), new RemoteDlcPrompts());
        shell = new DlcFxDlcManagementShell<>(
                adapter,
                statusLabel,
                progressBar,
                this::applyChanges,
                this::cancelChanges,
                items -> flow.enable(items),
                items -> flow.disable(new ArrayList<>(items))
        );
        shell.setAvailable(conn.connectedProperty().get());
        shell.setRedirectOpenHandler(this::confirmOpenRedirect);
        conn.connectedProperty().addListener((obs, oldValue, connected) -> shell.setAvailable(connected));

        refreshBtn.disableProperty().bind(conn.connectedProperty().not());
        refreshBtn.setOnAction(e -> refreshList());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBox = new HBox(10, refreshBtn, statusLabel, spacer, shell.selectionActions());
        topBox.setAlignment(Pos.CENTER_LEFT);
        page.setTop(DlcFxToolbar.vertical(topBox));

        VBox.setVgrow(shell.listFrame(), Priority.ALWAYS);

        VBox center = new VBox(10, shell.filterBar(), shell.listFrame());
        page.setCenter(page.section(center));
        page.setBottom(shell.bottomPanel());
        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.dlc"), page.root());
    }

    public void onTabSelected() {
        if (!loadedForCurrentConnection && conn.connectedProperty().get()) {
            refreshList();
        }
    }

    public boolean hasPendingChanges() {
        return shell != null && shell.hasPendingChanges();
    }

    public void applyPendingChanges() {
        applyChanges();
    }

    public void cancelPendingChanges() {
        cancelChanges();
    }

    public List<DlcServerItem> currentItemsSnapshot() {
        return List.copyOf(ensureItemsLoadedFromConnectionSnapshot());
    }

    public DlcServerItem itemByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String key = DlcFxPresetResolver.normalizeIdentifier(identifier);
        for (DlcServerItem item : ensureItemsLoadedFromConnectionSnapshot()) {
            if (item == null || item.identifier == null) {
                continue;
            }
            if (key.equals(DlcFxPresetResolver.normalizeIdentifier(item.identifier))) {
                return item;
            }
        }
        return null;
    }

    public boolean applyPresetOverrides(List<DlcPresetEntry> presets, Set<String> enabledPresetIds) {
        if (shell == null || !conn.connectedProperty().get()) {
            return false;
        }
        List<DlcServerItem> current = ensureItemsLoadedFromConnectionSnapshot();
        if (current.isEmpty()) {
            return false;
        }
        Map<String, DlcPresetEntry> presetsById = DlcFxPresetResolver.byId(presets);
        Set<String> desiredEnable = new LinkedHashSet<>();
        Set<String> desiredDisable = new LinkedHashSet<>();
        for (String presetId : enabledPresetIds == null ? Set.<String>of() : enabledPresetIds) {
            if (presetId == null || presetId.isBlank() || !presetsById.containsKey(presetId)) {
                continue;
            }
            desiredEnable.addAll(DlcFxPresetResolver.resolveEnableIdentifiers(
                    presetId,
                    presetsById,
                    identifier -> itemByIdentifier(identifier) != null
            ));
            desiredDisable.addAll(DlcFxPresetResolver.resolveConflictIdentifiers(
                    presetId,
                    presetsById,
                    identifier -> itemByIdentifier(identifier) != null
            ));
        }
        desiredDisable.addAll(collectInfoConflictIdentifiers(desiredEnable));
        desiredDisable = expandDisabledByDependents(desiredDisable);
        desiredEnable.removeAll(desiredDisable);

        Set<String> desiredAll = new LinkedHashSet<>(desiredEnable);
        desiredAll.addAll(desiredDisable);
        Set<String> toRestore = new LinkedHashSet<>(presetAppliedIdentifiers);
        toRestore.removeAll(desiredAll);

        for (String identifier : desiredEnable) {
            DlcServerItem item = itemByIdentifier(identifier);
            if (item != null) {
                item.enabled = true;
            }
        }
        for (String identifier : desiredDisable) {
            DlcServerItem item = itemByIdentifier(identifier);
            if (item != null) {
                item.enabled = false;
            }
        }
        for (String identifier : toRestore) {
            DlcServerItem item = itemByIdentifier(identifier);
            if (item != null) {
                item.enabled = adapter.baselineEnabled(item);
            }
        }
        presetAppliedIdentifiers.clear();
        presetAppliedIdentifiers.addAll(desiredAll);
        shell.refreshRows();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.pending.apply"));
        return shell.hasPendingChanges();
    }

    private void refreshList() {
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.loading"));
        shell.setBusy(true);
        conn.runAsync(f -> f.fetchList(), info -> {
            shell.setBusy(false);
            loadedForCurrentConnection = true;
            conn.rememberServerInfo(info);
            adapter.resetBaseline(info.items);
            shell.setItems(info.items);
            shell.setRedirectEntries(toRedirectEntries(info.redirects));
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.common.count_items", info.items.size()));
        }, err -> {
            shell.setBusy(false);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.load_failed", DlcFxServerConnection.safeMessage(err)));
        });
    }

    private List<DlcServerItem> ensureItemsLoadedFromConnectionSnapshot() {
        if (shell == null) {
            return List.of();
        }
        if (!shell.items().isEmpty()) {
            return List.copyOf(shell.items());
        }
        DlcServerInfo info = conn.lastServerInfo();
        if (info != null && info.items != null && !info.items.isEmpty()) {
            adapter.resetBaseline(info.items);
            shell.setItems(info.items);
            shell.setRedirectEntries(toRedirectEntries(info.redirects));
            loadedForCurrentConnection = true;
        }
        return List.copyOf(shell.items());
    }

    private Set<String> collectInfoConflictIdentifiers(Set<String> enabledIdentifiers) {
        Set<String> result = new LinkedHashSet<>();
        for (String identifier : enabledIdentifiers == null ? Set.<String>of() : enabledIdentifiers) {
            DlcServerItem item = itemByIdentifier(identifier);
            if (item == null || item.conflicts == null || item.conflicts.isEmpty()) {
                continue;
            }
            for (String conflict : item.conflicts) {
                String key = DlcFxPresetResolver.normalizeIdentifier(conflict);
                if (!key.isBlank() && !containsIdentifier(enabledIdentifiers, key)) {
                    result.add(conflict);
                }
            }
        }
        return result;
    }

    private Set<String> expandDisabledByDependents(Set<String> disabledIdentifiers) {
        Set<String> expanded = new LinkedHashSet<>();
        List<DlcServerItem> queue = new ArrayList<>();
        for (String identifier : disabledIdentifiers == null ? Set.<String>of() : disabledIdentifiers) {
            DlcServerItem item = itemByIdentifier(identifier);
            if (item != null && item.identifier != null && expanded.add(item.identifier)) {
                queue.add(item);
            }
        }
        for (int index = 0; index < queue.size(); index++) {
            DlcServerItem current = queue.get(index);
            String currentId = DlcFxPresetResolver.normalizeIdentifier(current.identifier);
            if (currentId.isBlank()) {
                continue;
            }
            for (DlcServerItem candidate : ensureItemsLoadedFromConnectionSnapshot()) {
                if (candidate == null || candidate.dependencies == null || candidate.identifier == null) {
                    continue;
                }
                boolean dependsOnCurrent = false;
                for (String dependency : candidate.dependencies) {
                    if (currentId.equals(DlcFxPresetResolver.normalizeIdentifier(dependency))) {
                        dependsOnCurrent = true;
                        break;
                    }
                }
                if (dependsOnCurrent && expanded.add(candidate.identifier)) {
                    queue.add(candidate);
                }
            }
        }
        return expanded;
    }

    private boolean containsIdentifier(Set<String> identifiers, String normalized) {
        if (identifiers == null || normalized == null || normalized.isBlank()) {
            return false;
        }
        for (String identifier : identifiers) {
            if (normalized.equals(DlcFxPresetResolver.normalizeIdentifier(identifier))) {
                return true;
            }
        }
        return false;
    }

    private void applyChanges() {
        List<DlcServerItem> changed = new ArrayList<>(shell.changedItems());
        if (changed.isEmpty()) {
            return;
        }
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.applying"));
        shell.setBusy(true);
        conn.runAsync(f -> {
            for (DlcServerItem item : changed) {
                f.setEnabled(item.id, item.enabled);
            }
            return f.applyWithMessages();
        }, outcome -> {
            shell.setBusy(false);
            adapter.commitBaseline(changed);
            shell.commitCurrentState();
            statusLabel.setText(outcome.requiresRestart
                    ? DlcFxI18n.tr("dlc_manager.server.applied_restart")
                    : DlcFxI18n.tr("dlc_manager.server.applied"));
        }, err -> {
            shell.setBusy(false);
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.apply_failed", DlcFxServerConnection.safeMessage(err)));
        });
    }

    private void cancelChanges() {
        shell.resetChanges();
        statusLabel.setText(DlcFxI18n.tr("dlc_manager.local.cancel_done"));
    }

    private String buildConflictMessage(List<DlcFxDlcManagementFlow.ConflictLine<DlcServerItem>> conflicts) {
        List<String> lines = new ArrayList<>();
        for (DlcFxDlcManagementFlow.ConflictLine<DlcServerItem> conflict : conflicts) {
            if (conflict != null) {
                lines.add(conflict.leftDisplayName() + " <-> " + conflict.rightDisplayName());
            }
        }
        return String.join("\n", lines);
    }

    private String buildDependencyMessage(List<DlcFxDlcManagementFlow.DependencyLine<DlcServerItem>> dependencies) {
        List<String> lines = new ArrayList<>();
        for (DlcFxDlcManagementFlow.DependencyLine<DlcServerItem> dependency : dependencies) {
            if (dependency != null) {
                lines.add(dependency.itemDisplayName() + " -> " + dependency.dependencyDisplayName());
            }
        }
        return String.join("\n", lines);
    }

    private javafx.scene.Scene overlayScene() {
        return shell == null || shell.listFrame() == null ? null : shell.listFrame().getScene();
    }

    private List<DlcFxDlcTogglePlanner.Item> plannerItems(List<DlcServerItem> source) {
        List<DlcFxDlcTogglePlanner.Item> out = new ArrayList<>();
        for (DlcServerItem item : source == null ? List.<DlcServerItem>of() : source) {
            if (item == null) {
                continue;
            }
            out.add(new DlcFxDlcTogglePlanner.Item(
                    item.id,
                    item.identifier,
                    item.enabled,
                    item.dependencies,
                    item.conflicts,
                    item.allowEnableByAll,
                    item.alarm,
                    item.hidden));
        }
        return out;
    }

    private List<String> itemIds(Iterable<DlcServerItem> items) {
        List<String> ids = new ArrayList<>();
        if (items != null) {
            for (DlcServerItem item : items) {
                if (item != null && item.id != null && !item.id.isBlank()) {
                    ids.add(item.id);
                }
            }
        }
        return ids;
    }

    private List<DlcServerItem> itemsByIds(List<String> ids) {
        Map<String, DlcServerItem> byId = new LinkedHashMap<>();
        for (DlcServerItem item : shell.items()) {
            if (item != null && item.id != null) {
                byId.put(item.id, item);
            }
        }
        List<DlcServerItem> out = new ArrayList<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            DlcServerItem item = byId.get(id);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private String displayName(String id) {
        DlcServerItem item = itemByIdOrIdentifier(id);
        if (item != null) {
            return serverDisplayName(item);
        }
        return id == null ? "" : id;
    }

    private DlcServerItem itemById(String id) {
        if (id == null || shell == null) {
            return null;
        }
        for (DlcServerItem item : shell.items()) {
            if (item != null && id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    private static String identifierOrId(DlcServerItem item, String fallbackId) {
        if (item != null && item.identifier != null && !item.identifier.isBlank()) {
            return item.identifier;
        }
        return fallbackId == null ? "" : fallbackId;
    }

    private DlcServerItem itemByIdOrIdentifier(String value) {
        if (value == null || shell == null) {
            return null;
        }
        String normalized = DlcFxPresetResolver.normalizeIdentifier(value);
        for (DlcServerItem item : shell.items()) {
            if (item == null) {
                continue;
            }
            if (value.equals(item.id)) {
                return item;
            }
            if (!normalized.isBlank() && normalized.equals(DlcFxPresetResolver.normalizeIdentifier(item.identifier))) {
                return item;
            }
        }
        return null;
    }

    private static String serverDisplayName(DlcServerItem item) {
        if (item == null) {
            return "";
        }
        String localized = DlcFxTooltipText.resolveLocalized(item.displayNames);
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        if (item.name != null && !item.name.isBlank()) {
            return item.name;
        }
        if (item.identifier != null && !item.identifier.isBlank()) {
            return item.identifier;
        }
        return item.id == null ? "" : item.id;
    }

    private static List<DlcRedirectEntry> toRedirectEntries(List<DlcServerRedirectItem> source) {
        List<DlcRedirectEntry> out = new ArrayList<>();
        for (DlcServerRedirectItem item : source == null ? List.<DlcServerRedirectItem>of() : source) {
            if (item == null || item.redirectUrl == null || item.redirectUrl.isBlank()) {
                continue;
            }
            DlcRedirectEntry entry = new DlcRedirectEntry();
            entry.setRedirectUrl(item.redirectUrl);
            entry.setFallbackName(item.fallbackName);
            if (item.displayNames != null) {
                entry.getDisplayNames().putAll(item.displayNames);
            }
            if (item.notices != null) {
                entry.getNotices().putAll(item.notices);
            }
            if (item.tips != null) {
                entry.getTips().putAll(item.tips);
            }
            out.add(entry);
        }
        return out;
    }

    private void confirmOpenRedirect(DlcRedirectEntry entry) {
        if (entry == null || entry.getRedirectUrl() == null || entry.getRedirectUrl().isBlank()) {
            return;
        }
        confirmOpenRedirect(entry.getRedirectUrl(), entry.resolveNotice(com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode()));
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
            statusLabel.setText(DlcFxI18n.tr("dlc_manager.status.open_failed", DlcFxServerConnection.safeMessage(ex)));
        }
    }

    private String buildAlarmTip(List<DlcServerItem> items) {
        List<String> lines = new ArrayList<>();
        for (DlcServerItem item : items == null ? List.<DlcServerItem>of() : items) {
            if (item != null) {
                lines.add(adapter.displayName(item));
            }
        }
        return String.join("\n\n", lines);
    }

    private final class RemoteDlcAdapter implements DlcFxDlcManagementShell.Adapter<DlcServerItem> {
        private final Map<String, Boolean> baseline = new LinkedHashMap<>();

        void resetBaseline(List<DlcServerItem> items) {
            baseline.clear();
            if (items == null) {
                return;
            }
            for (DlcServerItem item : items) {
                if (item != null) {
                    baseline.put(id(item), item.baselineEnabled == null ? item.enabled : item.baselineEnabled);
                }
            }
        }

        void commitBaseline(List<DlcServerItem> items) {
            if (items == null) {
                return;
            }
            for (DlcServerItem item : items) {
                if (item != null) {
                    baseline.put(id(item), item.enabled);
                    item.baselineEnabled = item.enabled;
                }
            }
        }

        @Override
        public String id(DlcServerItem item) {
            return item == null || item.id == null ? "" : item.id;
        }

        @Override
        public String displayName(DlcServerItem item) {
            return serverDisplayName(item);
        }

        @Override
        public String typeText(DlcServerItem item) {
            DlcType type = type(item);
            if (type != null) {
                return DlcFxI18n.tr(type.getDisplayKey());
            }
            return item == null || item.type == null || item.type.isBlank() ? "?" : item.type;
        }

        @Override
        public DlcType type(DlcServerItem item) {
            if (item == null || item.type == null || item.type.isBlank()) {
                return null;
            }
            try {
                return DlcType.valueOf(item.type.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        @Override
        public boolean visibleType(DlcType type) {
            DlcServerInfo info = conn.lastServerInfo();
            return type != DlcType.DLC_PACK
                    && (type != DlcType.TACZ_PACK || (info != null && info.hasTaczMod));
        }

        @Override
        public String folder(DlcServerItem item) {
            if (item == null || item.path == null || item.path.isBlank()) {
                return DlcFxDlcManagementListModel.ROOT_GROUP;
            }
            return item.path.replace('\\', '/');
        }

        @Override
        public boolean baselineEnabled(DlcServerItem item) {
            Boolean value = item == null ? null : baseline.get(id(item));
            return value != null && value;
        }

        @Override
        public boolean effectiveEnabled(DlcServerItem item) {
            return item != null && item.enabled;
        }

        @Override
        public void setEffectiveEnabled(DlcServerItem item, boolean enabled) {
            if (item != null) {
                item.enabled = enabled;
            }
        }

        @Override
        public String tooltip(DlcServerItem item) {
            if (item == null) {
                return "";
            }
            StringBuilder tip = new StringBuilder();
            DlcFxTooltipText.appendBlock(tip, DlcFxTooltipText.resolveLocalized(item.tips));
            if (item.redirectUrl != null && !item.redirectUrl.isBlank()) {
                DlcFxTooltipText.appendBlock(tip, "§b" + DlcFxI18n.tr("dlc_manager.tooltip.double_click_redirect") + "§r");
            }
            for (String blocker : item.presetBlockers == null ? List.<String>of() : item.presetBlockers) {
                DlcFxTooltipText.appendBlock(tip, "§4" + DlcFxI18n.tr("dlc_manager.preset.conflict", blocker) + "§r");
            }
            List<String> missing = missingDependencyIdentifiers(item);
            if (!missing.isEmpty()) {
                DlcFxTooltipText.appendBlock(tip, "§c" + DlcFxI18n.tr("dlc_manager.tooltip.missing_dependencies") + ":§r");
                for (String dependency : missing) {
                    DlcFxTooltipText.appendLine(tip, dependencyDisplayName(dependency));
                }
            }
            List<String> dependencies = dependencyIdentifiersRecursive(item);
            if (!dependencies.isEmpty()) {
                DlcFxTooltipText.appendBlock(tip, "§6" + DlcFxI18n.tr("dlc_manager.tooltip.dependencies") + ":§r");
                for (String dependency : dependencies) {
                    DlcFxTooltipText.appendLine(tip, dependencyDisplayName(dependency));
                }
            }
            if (item.conflicts != null && !item.conflicts.isEmpty()) {
                DlcFxTooltipText.appendBlock(tip, "§4" + DlcFxI18n.tr("dlc_manager.tooltip.conflicts") + ":§r");
                for (String conflict : item.conflicts) {
                    DlcFxTooltipText.appendLine(tip, dependencyDisplayName(conflict));
                }
            }
            return tip.toString();
        }

        @Override
        public boolean missingDependencies(DlcServerItem item) {
            return !missingDependencyIdentifiers(item).isEmpty();
        }

        @Override
        public boolean canEnable(DlcServerItem item) {
            return item != null && item.allowEnableByAll && !missingDependencies(item) && !blocked(item);
        }

        @Override
        public boolean blocked(DlcServerItem item) {
            return item != null && item.presetBlocked;
        }

        @Override
        public String blockedText(DlcServerItem item) {
            return item != null && item.presetBlocked ? DlcFxI18n.tr("dlc_manager.preset.disabled_by_preset") : "";
        }

        @Override
        public void openRedirect(DlcServerItem item) {
            if (item != null && item.redirectUrl != null && !item.redirectUrl.isBlank()) {
                confirmOpenRedirect(item.redirectUrl, "");
            }
        }

        private List<String> missingDependencyIdentifiers(DlcServerItem item) {
            if (item == null || item.dependencies == null || item.dependencies.isEmpty()) {
                return List.of();
            }
            List<String> missing = new ArrayList<>();
            for (String dependency : dependencyIdentifiersRecursive(item)) {
                if (itemByIdentifier(dependency) == null) {
                    missing.add(dependency);
                }
            }
            return missing;
        }

        private List<String> dependencyIdentifiersRecursive(DlcServerItem item) {
            if (item == null || item.dependencies == null || item.dependencies.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> result = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>(item.dependencies);
            while (!queue.isEmpty()) {
                String identifier = queue.poll();
                if (identifier == null || identifier.isBlank() || !result.add(identifier)) {
                    continue;
                }
                DlcServerItem dependency = itemByIdentifier(identifier);
                if (dependency != null && dependency.dependencies != null && !dependency.dependencies.isEmpty()) {
                    queue.addAll(dependency.dependencies);
                }
            }
            return new ArrayList<>(result);
        }

        private String dependencyDisplayName(String identifier) {
            DlcServerItem item = itemByIdOrIdentifier(identifier);
            if (item == null) {
                return identifier == null ? "" : identifier;
            }
            return displayName(item);
        }
    }

    private final class RemoteDlcFlowAdapter implements DlcFxDlcManagementFlow.Adapter<DlcServerItem> {
        @Override
        public List<DlcServerItem> allItems() {
            return shell == null ? List.of() : List.copyOf(shell.items());
        }

        @Override
        public String id(DlcServerItem item) {
            return adapter.id(item);
        }

        @Override
        public String identifier(DlcServerItem item) {
            return item == null || item.identifier == null ? "" : item.identifier;
        }

        @Override
        public String displayName(DlcServerItem item) {
            return adapter.displayName(item);
        }

        @Override
        public boolean alarm(DlcServerItem item) {
            return item != null && item.alarm;
        }

        @Override
        public DlcFxDlcManagementFlow.ConflictBatch<DlcServerItem> collectConflicts(List<DlcServerItem> targets) {
            List<DlcFxDlcTogglePlanner.Conflict> conflicts = DlcFxDlcTogglePlanner.planEnable(
                    plannerItems(allItems()),
                    itemIds(targets)
            ).conflicts();
            List<DlcFxDlcManagementFlow.ConflictLine<DlcServerItem>> lines = new ArrayList<>();
            for (DlcFxDlcTogglePlanner.Conflict conflict : conflicts) {
                if (conflict == null) {
                    continue;
                }
                DlcServerItem left = itemById(conflict.leftId());
                DlcServerItem right = itemById(conflict.rightId());
                lines.add(new DlcFxDlcManagementFlow.ConflictLine<>(
                        left,
                        right,
                        identifierOrId(left, conflict.leftId()),
                        identifierOrId(right, conflict.rightId()),
                        DlcFxServerDlcManagementTab.this.displayName(conflict.leftId()),
                        DlcFxServerDlcManagementTab.this.displayName(conflict.rightId())
                ));
            }
            return new DlcFxDlcManagementFlow.ConflictBatch<>(lines);
        }

        @Override
        public DlcFxDlcManagementFlow.DependencyBatch<DlcServerItem> collectMissingDependencies(List<DlcServerItem> targets) {
            DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(
                    plannerItems(allItems()),
                    itemIds(targets)
            );
            List<DlcFxDlcManagementFlow.DependencyLine<DlcServerItem>> lines = new ArrayList<>();
            Set<String> dependencyIds = new LinkedHashSet<>();
            for (DlcFxDlcTogglePlanner.Dependency dependency : plan.dependencies()) {
                if (dependency == null) {
                    continue;
                }
                dependencyIds.add(dependency.dependencyId());
                lines.add(new DlcFxDlcManagementFlow.DependencyLine<>(
                        itemById(dependency.itemId()),
                        itemById(dependency.dependencyId()),
                        DlcFxServerDlcManagementTab.this.displayName(dependency.itemId()),
                        DlcFxServerDlcManagementTab.this.displayName(dependency.dependencyId())
                ));
            }
            return new DlcFxDlcManagementFlow.DependencyBatch<>(lines, itemsByIds(new ArrayList<>(dependencyIds)));
        }

        @Override
        public List<DlcServerItem> planDisable(List<DlcServerItem> targets) {
            return itemsByIds(DlcFxDlcTogglePlanner.planDisable(plannerItems(allItems()), itemIds(targets)));
        }

        @Override
        public void applyEnabled(List<DlcServerItem> items, boolean enabled) {
            for (DlcServerItem item : items == null ? List.<DlcServerItem>of() : items) {
                adapter.setEffectiveEnabled(item, enabled);
            }
        }

        @Override
        public String conflictFilterKey(DlcServerItem item) {
            return identifierOrId(item, id(item));
        }

        @Override
        public void clearSelection() {
            if (shell != null) {
                shell.model().clearSelection();
            }
        }

        @Override
        public void refreshRows() {
            if (shell != null) {
                shell.refreshRows();
            }
        }
    }

    private final class RemoteDlcPrompts implements DlcFxDlcManagementFlow.Prompts<DlcServerItem> {
        @Override
        public void showConflict(List<DlcFxDlcManagementFlow.ConflictLine<DlcServerItem>> lines, Runnable skipConflictingTargets) {
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
        public void showDependencies(List<DlcFxDlcManagementFlow.DependencyLine<DlcServerItem>> lines, Runnable enableDependencies) {
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
        public void showAlarm(List<DlcServerItem> items, Runnable confirm) {
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
}
