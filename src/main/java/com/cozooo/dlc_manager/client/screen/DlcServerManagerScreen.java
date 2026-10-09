package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.core.DlcType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DlcServerManagerScreen extends DlcPageScreen {






    public enum Tab {
        SERVERS
    }

    private static class DisplayEntry {
        private final boolean header;
        private final Component label;
        private final DlcServerItem item;

        private DisplayEntry(boolean header, Component label, DlcServerItem item) {
            this.header = header;
            this.label = label;
            this.item = item;
        }

        private static DisplayEntry header(Component label) {
            return new DisplayEntry(true, label, null);
        }

        private static DisplayEntry item(DlcServerItem item) {
            return new DisplayEntry(false, null, item);
        }
    }

    private final Screen parent;
    private final DlcServerEntry entry;
    private final boolean openedFromIngame;
    private final Map<String, Boolean> baseline = new HashMap<>();
    private List<DlcServerItem> items = new ArrayList<>();
    private final List<DisplayEntry> displayEntries = new ArrayList<>();
    private int scrollIndex;
    private Button applyButton;
    private Button backButton;
    private Button presetButton;
    private Button packButton;
    private Button enableAllButton;
    private Button disableAllButton;
    private volatile boolean applying;
    private DlcType activeFilter;
    private Button filterAllButton;
    private final Map<DlcType, Button> filterButtons = new EnumMap<>(DlcType.class);

    private List<DlcServerEntry> servers = new ArrayList<>();
    private int serverScrollIndex;
    private int serverSelectedIndex = -1;
    private Button pushButton;
    private Button rconButton;
    private Button filesButton;
    private Button updateManagerButton;
    private Button packManagerButton;
    private Button returnToGameButton;
    private volatile boolean connecting;

    private int lastMouseX;
    private int lastMouseY;

    private Tab activeTab = Tab.SERVERS;

    private boolean draggingDlcScroll;
    private int dlcScrollDragOffset;
    private int remoteMissingCount;
    private int requiredMissingCount;
    private boolean promptedRemoteDownload;
    private boolean hasServerTaczMod;
    private String serverBaseVersion = "";
    private String serverDlcVersion = "";

    public DlcServerManagerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info) {
        this(parent, entry, info, null, false);
    }

    public DlcServerManagerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info, Tab startTab) {
        this(parent, entry, info, startTab, false);
    }

    public DlcServerManagerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info, Tab startTab, boolean openedFromIngame) {
        super(DlcUiPage.SERVER_TOOLS, parent, Component.translatable("dlc_manager.server.title"));
        this.parent = parent;
        this.entry = entry;
        this.openedFromIngame = openedFromIngame;
        if (startTab != null) {
            this.activeTab = startTab;
        }
        setInfo(info);
    }

    private void setInfo(DlcServerInfo info) {
        items.clear();
        baseline.clear();
        if (info != null && info.items != null) {
            items.addAll(info.items);
        }
        for (DlcServerItem item : items) {
            baseline.put(item.id, item.enabled);
        }
        String base = info == null ? "" : info.baseVersion;
        String dlc = info == null ? "" : info.dlcVersion;
        serverBaseVersion = base == null ? "" : base;
        serverDlcVersion = dlc == null ? "" : dlc;
        remoteMissingCount = info == null ? 0 : info.remoteMissing;
        requiredMissingCount = info == null ? 0 : info.requiredMissing;
        hasServerTaczMod = info != null && info.hasTaczMod;
        if (!hasServerTaczMod && activeFilter == DlcType.TACZ_PACK) {
            activeFilter = null;
        }
        rebuildDisplayEntries();
        applyPresetOverrides();
    }

    private void initControls() {
        rebuildUi();
    }

    private void rebuildUi() {
        clearWidgets();
        initServerTab();
    }

    private void openDlcPage() {
        Screen rootParent = parent;
        if (parent instanceof DlcServerScreen mali) {
            Screen root = mali.getRootParent();
            if (root != null) {
                rootParent = root;
            }
        }
        Minecraft.getInstance().setScreen(new DlcServerScreen(rootParent, entry, buildInfoSnapshot(), openedFromIngame));
    }

    private DlcServerInfo buildInfoSnapshot() {
        DlcServerInfo info = new DlcServerInfo();
        info.items = new ArrayList<>(items);
        info.remoteMissing = remoteMissingCount;
        info.requiredMissing = requiredMissingCount;
        info.hasTaczMod = hasServerTaczMod;
        info.baseVersion = serverBaseVersion;
        info.dlcVersion = serverDlcVersion;
        return info;
    }

    private void openPackManager() {
        Minecraft.getInstance().setScreen(new DlcServerPackManagerScreen(this, entry));
    }

    private void initServerTab() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int buttonGap = 8;
        int buttonCount = 5;
        int totalHeight = buttonCount * buttonHeight + (buttonCount - 1) * buttonGap;
        int startY = Math.max(panelTop() + 12, (panelTop() + panelBottom() - totalHeight) / 2 - 6);
        pushButton = Button.builder(Component.translatable("dlc_manager.server.push"), btn -> openPush())
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build();
        rconButton = Button.builder(Component.translatable("dlc_manager.server.rcon"), btn -> openRcon())
            .bounds(centerX - buttonWidth / 2, startY + buttonHeight + buttonGap, buttonWidth, buttonHeight)
            .build();
        filesButton = Button.builder(Component.translatable("dlc_manager.server.files"), btn -> openFiles())
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + buttonGap) * 2, buttonWidth, buttonHeight)
            .build();
        updateManagerButton = Button.builder(Component.translatable("dlc_manager.update.manager"),
                btn -> Minecraft.getInstance().setScreen(new DlcServerUpdateManagerScreen(this, entry)))
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + buttonGap) * 3, buttonWidth, buttonHeight)
            .build();
        packManagerButton = Button.builder(Component.translatable("dlc_manager.pack.manager"),
                btn -> openPackManager())
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + buttonGap) * 4, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(pushButton);
        addRenderableWidget(rconButton);
        addRenderableWidget(filesButton);
        addRenderableWidget(updateManagerButton);
        addRenderableWidget(packManagerButton);
        if (openedFromIngame) {
            returnToGameButton = Button.builder(Component.translatable("menu.returnToGame"), btn -> Minecraft.getInstance().setScreen(null))
                .bounds(centerX - buttonWidth / 2, this.height - 22, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(returnToGameButton);
        }
    }

    private void rebuildDisplayEntries() {
        displayEntries.clear();
        Map<DlcType, List<DlcServerItem>> grouped = new EnumMap<>(DlcType.class);
        for (DlcType type : DlcType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (DlcServerItem item : items) {
            DlcType type = mapType(item.type);
            if (!isServerTypeVisible(type)) {
                continue;
            }
            grouped.get(type).add(item);
        }
        if (activeFilter == null) {
            for (DlcType type : DlcType.values()) {
                if (!isServerTypeVisible(type)) {
                    continue;
                }
                List<DlcServerItem> group = grouped.get(type);
                if (group == null || group.isEmpty()) {
                    continue;
                }
                displayEntries.add(DisplayEntry.header(Component.translatable(type.getDisplayKey())));
                for (DlcServerItem item : group) {
                    displayEntries.add(DisplayEntry.item(item));
                }
            }
        } else {
            List<DlcServerItem> group = grouped.get(activeFilter);
            if (group != null) {
                for (DlcServerItem item : group) {
                    displayEntries.add(DisplayEntry.item(item));
                }
            }
        }
    }

    private DlcType mapType(String raw) {
        if (raw == null) {
            return DlcType.UNKNOWN;
        }
        String value = raw.trim().toLowerCase();
        if (value.equals("mod") || value.equals("mods")) {
            return DlcType.MOD;
        }
        if (value.equals("resourcepack") || value.equals("resourcepacks") || value.equals("resource_pack")) {
            return DlcType.RESOURCE_PACK;
        }
        if (value.equals("datapack") || value.equals("datapacks") || value.equals("data_pack")) {
            return DlcType.DATA_PACK;
        }
        if (value.equals("tacz") || value.equals("tacz_pack") || value.equals("tacz_packs") || value.equals("taczpack")) {
            return DlcType.TACZ_PACK;
        }
        return DlcType.UNKNOWN;
    }

    private int panelTop() {
        return 28;
    }

    private int panelBottom() {
        return this.height - 24;
    }

    private void updateApplyState() {
        if (applyButton == null) {
            return;
        }
        boolean dirty = false;
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            Boolean baselineValue = baseline.get(item.id);
            if (baselineValue == null) {
                baselineValue = false;
            }
            if (baselineValue != item.enabled) {
                dirty = true;
                break;
            }
        }
        applyButton.active = dirty && !applying;
        if (backButton != null) {
            backButton.active = !dirty && !applying && requiredMissingCount <= 0;
        }
    }

    private boolean isServerTypeVisible(DlcType type) {
        return type != DlcType.TACZ_PACK || hasServerTaczMod;
    }

    private void applyPresetOverrides() {
        DlcPresetRuntimeState.reload();
        List<DlcPresetEntry> entries = DlcPresetRuntimeState.getPresets();
        Set<String> enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds();
        if (enabledPresets.isEmpty() || entries.isEmpty()) {
            return;
        }
        Map<String, DlcPresetEntry> byId = new HashMap<>();
        for (DlcPresetEntry entry : entries) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        Set<String> enableIds = new HashSet<>();
        Set<String> conflictIds = new LinkedHashSet<>();
        for (String id : enabledPresets) {
            DlcPresetEntry entry = byId.get(id);
            if (entry == null) {
                continue;
            }
            for (String enable : entry.getEnable()) {
                String normalized = normalizeServerIdentifier(enable);
                if (!normalized.isBlank()) {
                    enableIds.add(normalized);
                }
            }
            for (String conflict : entry.getConflicts()) {
                String normalized = normalizeServerIdentifier(conflict);
                if (!normalized.isBlank()) {
                    conflictIds.add(normalized);
                }
            }
        }
        Set<String> expandedConflictIds = expandConflictIdentifiers(conflictIds);
        for (DlcServerItem item : items) {
            if (item == null || item.identifier == null) {
                continue;
            }
            String key = normalizeServerIdentifier(item.identifier);
            if (enableIds.contains(key)) {
                item.enabled = true;
            }
            if (expandedConflictIds.contains(key)) {
                item.enabled = false;
            }
        }
        rebuildDisplayEntries();
        updateApplyState();
    }

    private Set<String> expandConflictIdentifiers(Set<String> conflictIds) {
        Set<String> expanded = new LinkedHashSet<>();
        if (conflictIds == null || conflictIds.isEmpty()) {
            return expanded;
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String id : conflictIds) {
            String normalized = normalizeServerIdentifier(id);
            if (normalized.isBlank()) {
                continue;
            }
            if (expanded.add(normalized)) {
                queue.add(normalized);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String dependent : collectDependentIdentifiers(current)) {
                if (!dependent.isBlank() && expanded.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        return expanded;
    }

    private Set<String> collectDependentIdentifiers(String targetIdentifier) {
        Set<String> dependents = new LinkedHashSet<>();
        if (targetIdentifier == null || targetIdentifier.isBlank()) {
            return dependents;
        }
        for (DlcServerItem item : items) {
            if (item == null || item.identifier == null || item.dependencies == null || item.dependencies.isEmpty()) {
                continue;
            }
            for (String dep : item.dependencies) {
                if (normalizeServerIdentifier(dep).equals(targetIdentifier)) {
                    String identifier = normalizeServerIdentifier(item.identifier);
                    if (!identifier.isBlank()) {
                        dependents.add(identifier);
                    }
                    break;
                }
            }
        }
        return dependents;
    }

    private static String normalizeServerIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.trim().toLowerCase(java.util.Locale.ROOT);
    }

    void refreshServerInfo() {
        CompletableFuture
            .supplyAsync(() -> fetchInfo(entry))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                if (info != null) {
                    setInfo(info);
                    rebuildUi();
                }
            }));
    }

    private void loadServers() {
        DlcServerList.load();
        servers = new ArrayList<>(DlcServerList.getEntries());
        String lastKey = DlcServerList.getLastSelectedKey();
        serverSelectedIndex = -1;
        if (lastKey != null) {
            for (int i = 0; i < servers.size(); i++) {
                DlcServerEntry item = servers.get(i);
                if (item != null && lastKey.equals(item.getKey())) {
                    serverSelectedIndex = i;
                    break;
                }
            }
        }
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openPush() {
        Minecraft.getInstance().setScreen(new DlcServerPushScreen(this, entry));
    }

    private void openRcon() {
        Minecraft.getInstance().setScreen(new DlcServerRconScreen(this, entry));
    }

    private void openFiles() {
        Minecraft.getInstance().setScreen(new DlcServerFileManagerScreen(this, entry));
    }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = 0;

        var list = section(entry == null ? "" : entry.name, null);
        var models = java.util.List.of(pushButton, rconButton, filesButton, updateManagerButton, packManagerButton);
        String[] keys = {"push", "rcon", "files", "updates", "packs"};
        for (int index = 0; index < models.size(); index++) {
            var model = models.get(index);
            var line = row(list, model.getMessage().getString(), tr("dlc_manager.ui.tool_" + keys[index]));
            mount(node(line, "div", "row-controls"), model);
        }
        if (returnToGameButton != null) mount(footer, returnToGameButton);

    }
    @Override protected void updatePage() {

        if (pageRevision != 0) refreshPage();
    }

    @Override public void onClose() { if (shouldCloseOnEsc()) openDlcPage(); }
}
