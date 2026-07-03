package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.app.DlcFxManagerApp;
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerPresetItem;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.preset.DlcFxPresetFlow;
import com.cozooo.dlc_manager.ui.preset.DlcFxPresetResolver;
import com.cozooo.dlc_manager.ui.preset.DlcFxPresetShell;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class DlcFxServerPresetTab {
    private final DlcFxServerConnection conn;
    private final Supplier<DlcFxServerDlcManagementTab> dlcManagementTab;
    private final ServerPresetAdapter adapter = new ServerPresetAdapter();
    private final DlcFxPresetShell<DlcPresetEntry> shell;
    private final List<DlcPresetEntry> serverPresets = new ArrayList<>();
    private final Set<String> serverEnabledPresetIds = new LinkedHashSet<>();

    public DlcFxServerPresetTab(DlcFxServerConnection conn,
                                Supplier<DlcFxServerDlcManagementTab> dlcManagementTab) {
        this.conn = conn;
        this.dlcManagementTab = dlcManagementTab == null ? () -> null : dlcManagementTab;
        this.shell = new DlcFxPresetShell<>(adapter);
        if (conn != null) {
            conn.connectedProperty().addListener((obs, oldValue, newValue) -> {
                serverPresets.clear();
                serverEnabledPresetIds.clear();
                shell.onContextChanged(newValue
                        ? DlcFxI18n.tr("dlc_manager.server.connect_success")
                        : DlcFxI18n.tr("dlc_manager.status.disconnected"));
            });
        }
    }

    public Tab build() {
        return shell.build();
    }

    public void onTabSelected() {
        shell.onTabSelected();
    }

    private DlcPresetStatus displayStatus(DlcPresetEntry entry, DlcFxPresetFlow<DlcPresetEntry> flow) {
        if (entry == null || !flow.effectiveEnabled(entry)) {
            return DlcPresetStatus.OFF;
        }
        Map<String, DlcPresetEntry> byId = DlcFxPresetResolver.byId(List.copyOf(serverPresets));
        Set<String> identifiers = DlcFxPresetResolver.resolveEnableIdentifiers(
                entry.getId(),
                byId,
                identifier -> serverItem(identifier) != null
        );
        if (identifiers.isEmpty()) {
            return DlcPresetStatus.ON;
        }
        for (String identifier : identifiers) {
            DlcServerItem item = serverItem(identifier);
            if (item == null) {
                return DlcPresetStatus.PART;
            }
            if (!flow.isPending(entry) && !item.enabled) {
                return DlcPresetStatus.PART;
            }
        }
        return DlcPresetStatus.ON;
    }

    private List<String> pendingBlockerNames(DlcPresetEntry target,
                                             List<DlcPresetEntry> entries,
                                             DlcFxPresetFlow<DlcPresetEntry> flow,
                                             String lang) {
        if (target == null) {
            return List.of();
        }
        Map<String, DlcPresetEntry> byId = DlcFxPresetResolver.byId(entries);
        Set<String> targetEnable = DlcFxPresetResolver.resolveEnableIdentifiers(
                target.getId(),
                byId,
                identifier -> serverItem(identifier) != null
        );
        if (targetEnable.isEmpty()) {
            return List.of();
        }
        Set<String> targetConflicts = DlcFxPresetResolver.resolveConflictIdentifiers(
                target.getId(),
                byId,
                identifier -> serverItem(identifier) != null
        );
        List<String> blockers = new ArrayList<>();
        for (DlcPresetEntry entry : entries == null ? List.<DlcPresetEntry>of() : entries) {
            if (entry == null || entry == target || !flow.effectiveEnabled(entry)) {
                continue;
            }
            Set<String> enabled = DlcFxPresetResolver.resolveEnableIdentifiers(
                    entry.getId(),
                    byId,
                    identifier -> serverItem(identifier) != null
            );
            Set<String> conflicts = DlcFxPresetResolver.resolveConflictIdentifiers(
                    entry.getId(),
                    byId,
                    identifier -> serverItem(identifier) != null
            );
            if (!intersects(targetEnable, conflicts) && !intersects(targetConflicts, enabled)) {
                continue;
            }
            blockers.add(entry.resolveDisplayName(lang));
        }
        return blockers;
    }

    private DlcServerItem serverItem(String identifier) {
        DlcFxServerDlcManagementTab tab = dlcManagementTab.get();
        if (tab != null) {
            return tab.itemByIdentifier(identifier);
        }
        return itemByIdentifier(serverInfoItems(), identifier);
    }

    private List<DlcServerItem> serverInfoItems() {
        DlcServerInfo info = conn.lastServerInfo();
        return info == null || info.items == null ? List.of() : info.items;
    }

    private static List<DlcPresetEntry> toPresetEntries(List<DlcServerPresetItem> presets) {
        List<DlcPresetEntry> out = new ArrayList<>();
        for (DlcServerPresetItem item : presets == null ? List.<DlcServerPresetItem>of() : presets) {
            if (item != null) {
                out.add(item.toPresetEntry());
            }
        }
        return out;
    }

    private static DlcServerItem itemByIdentifier(List<DlcServerItem> items, String identifier) {
        String key = DlcFxPresetResolver.normalizeIdentifier(identifier);
        if (key.isBlank()) {
            return null;
        }
        for (DlcServerItem item : items == null ? List.<DlcServerItem>of() : items) {
            if (item == null || item.identifier == null) {
                continue;
            }
            if (key.equals(DlcFxPresetResolver.normalizeIdentifier(item.identifier))) {
                return item;
            }
        }
        return null;
    }

    private Map<String, DlcPresetStatus> computeStatuses(List<DlcPresetEntry> entries) {
        Map<String, DlcPresetEntry> byId = DlcFxPresetResolver.byId(entries);
        java.util.LinkedHashMap<String, DlcPresetStatus> statuses = new java.util.LinkedHashMap<>();
        for (DlcPresetEntry entry : entries == null ? List.<DlcPresetEntry>of() : entries) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            if (!serverEnabledPresetIds.contains(entry.getId())) {
                statuses.put(entry.getId(), DlcPresetStatus.OFF);
                continue;
            }
            Set<String> identifiers = DlcFxPresetResolver.resolveEnableIdentifiers(
                    entry.getId(),
                    byId,
                    identifier -> itemByIdentifier(serverInfoItems(), identifier) != null
            );
            boolean allEnabled = true;
            for (String identifier : identifiers) {
                DlcServerItem item = itemByIdentifier(serverInfoItems(), identifier);
                if (item == null || !item.enabled) {
                    allEnabled = false;
                    break;
                }
            }
            statuses.put(entry.getId(), allEnabled ? DlcPresetStatus.ON : DlcPresetStatus.PART);
        }
        return statuses;
    }

    private void rememberLoadedPresets(List<DlcPresetEntry> loaded) {
        serverPresets.clear();
        serverEnabledPresetIds.clear();
        for (DlcPresetEntry entry : loaded == null ? List.<DlcPresetEntry>of() : loaded) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            serverPresets.add(entry);
            if (entry.getStatus().isEnabled()) {
                serverEnabledPresetIds.add(entry.getId());
            }
        }
        applyStatuses(serverPresets, computeStatuses(serverPresets));
    }

    private static void applyStatuses(List<DlcPresetEntry> entries, Map<String, DlcPresetStatus> statuses) {
        for (DlcPresetEntry entry : entries == null ? List.<DlcPresetEntry>of() : entries) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            entry.setStatus(statuses == null ? DlcPresetStatus.OFF : statuses.getOrDefault(entry.getId(), DlcPresetStatus.OFF));
        }
    }

    private void showGoToDlcManagementPrompt(javafx.scene.Scene scene) {
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.preset.title"),
                DlcFxI18n.tr("dlc_manager.preset.apply_hint"),
                () -> {
                    DlcFxManagerApp app = DlcFxManagerApp.instance();
                    if (app != null) {
                        app.showServerDlcManagementTab();
                    }
                },
                DlcFxI18n.tr("dlc_manager.preset.goto_dlc"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.preset.goto_later")
        );
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String currentLang() {
        String lang = CrossPlatformRuntime.languageCode();
        return lang == null || lang.isBlank() ? "en_us" : lang;
    }

    private final class ServerPresetAdapter implements DlcFxPresetShell.Adapter<DlcPresetEntry> {
        @Override
        public boolean available() {
            return conn != null && conn.connectedProperty().get();
        }

        @Override
        public String unavailableStatusText() {
            return DlcFxI18n.tr("dlc_manager.status.disconnected");
        }

        @Override
        public List<DlcPresetEntry> load() throws Exception {
            if (conn == null || conn.facade() == null) {
                throw new IllegalStateException(DlcFxI18n.tr("dlc_manager.server.not_connected"));
            }
            DlcServerInfo info = conn.facade().fetchList();
            conn.rememberServerInfo(info);
            List<DlcPresetEntry> loaded = toPresetEntries(info == null ? null : info.presets);
            rememberLoadedPresets(loaded);
            return new ArrayList<>(serverPresets);
        }

        @Override
        public DlcFxPresetShell.ApplyResult<DlcPresetEntry> apply(Map<String, Boolean> pending) {
            for (Map.Entry<String, Boolean> entry : pending.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                if (Boolean.TRUE.equals(entry.getValue())) {
                    serverEnabledPresetIds.add(entry.getKey());
                } else {
                    serverEnabledPresetIds.remove(entry.getKey());
                }
            }
            applyStatuses(serverPresets, computeStatuses(serverPresets));
            return new DlcFxPresetShell.ApplyResult<>(
                    new ArrayList<>(serverPresets),
                    !pending.isEmpty(),
                    DlcFxI18n.tr("dlc_manager.status.apply_done")
            );
        }

        @Override
        public String id(DlcPresetEntry entry) {
            return entry == null ? "" : entry.getId();
        }

        @Override
        public boolean enabled(DlcPresetEntry entry) {
            return entry != null && entry.getStatus().isEnabled();
        }

        @Override
        public boolean alarm(DlcPresetEntry entry) {
            return entry != null && entry.isAlarm();
        }

        @Override
        public String displayName(DlcPresetEntry entry, String lang) {
            return entry == null ? "" : entry.resolveDisplayName(lang);
        }

        @Override
        public String tip(DlcPresetEntry entry, String lang) {
            return entry == null ? "" : entry.resolveTip(lang);
        }

        @Override
        public DlcPresetStatus displayStatus(DlcPresetEntry entry, DlcFxPresetFlow<DlcPresetEntry> flow) {
            return DlcFxServerPresetTab.this.displayStatus(entry, flow);
        }

        @Override
        public List<String> blockerNames(DlcPresetEntry entry,
                                         List<DlcPresetEntry> entries,
                                         DlcFxPresetFlow<DlcPresetEntry> flow,
                                         String lang) {
            return pendingBlockerNames(entry, entries, flow, lang == null ? currentLang() : lang);
        }

        @Override
        public void afterApplied(DlcFxPresetShell.ApplyResult<DlcPresetEntry> result, javafx.scene.Scene scene) {
            DlcFxServerDlcManagementTab tab = dlcManagementTab.get();
            if (tab == null || result == null || !result.dlcChanged()) {
                return;
            }
            boolean changed = tab.applyPresetOverrides(result.entries(), new LinkedHashSet<>(serverEnabledPresetIds));
            if (changed) {
                showGoToDlcManagementPrompt(scene);
            }
        }
    }
}
