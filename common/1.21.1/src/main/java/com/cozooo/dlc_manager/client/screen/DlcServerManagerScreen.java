package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.screen.DlcPresetManagerScreen;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DlcServerManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int FILTER_TAB_HEIGHT = 18;
    private static final int FILTER_TAB_GAP = 6;
    private static final int TAB_WIDTH = 88;
    private static final int TAB_HEIGHT = 20;

    public enum Tab {
        DLC,
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
    private Button enableAllButton;
    private Button disableAllButton;
    private volatile boolean applying;
    private String headerLine;
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
    private Button returnToGameButton;
    private volatile boolean connecting;

    private int lastMouseX;
    private int lastMouseY;

    private Tab activeTab = Tab.SERVERS;
    private Button tabDlc;
    private Button tabServers;
    private Button configButton;

    private boolean draggingDlcScroll;
    private int dlcScrollDragOffset;
    private int remoteMissingCount;
    private int requiredMissingCount;
    private boolean promptedRemoteDownload;
    private String serverBaseVersion = "";
    private String serverDlcVersion = "";

    public DlcServerManagerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info) {
        this(parent, entry, info, null, false);
    }

    public DlcServerManagerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info, Tab startTab) {
        this(parent, entry, info, startTab, false);
    }

    public DlcServerManagerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info, Tab startTab, boolean openedFromIngame) {
        super(Component.translatable("dlc_manager.server.title"));
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
        boolean baseBlank = base == null || base.isBlank();
        boolean dlcBlank = dlc == null || dlc.isBlank();
        if (!baseBlank || !dlcBlank) {
            headerLine = dlcBlank ? base : (baseBlank ? dlc : base + "+" + dlc);
        } else {
            headerLine = null;
        }
        rebuildDisplayEntries();
        applyPresetOverrides();
    }

    @Override
    protected void init() {
        rebuildUi();
    }

    private void promptRemoteDownloadIfMissing() {
        if (promptedRemoteDownload || remoteMissingCount <= 0) {
            return;
        }
        promptedRemoteDownload = true;
        Component title = Component.translatable("dlc_manager.server.remote_missing_title", remoteMissingCount);
        Component desc = Component.translatable("dlc_manager.server.remote_missing_desc");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                startRemoteDownload();
            }
        }, title, desc, Component.translatable("dlc_manager.server.remote_missing_confirm"), Component.translatable("gui.cancel")));
    }

    private void rebuildUi() {
        clearWidgets();
        initTabs();
        if (activeTab == Tab.DLC) {
            initDlcTab();
        } else {
            initServerTab();
        }
    }

    private void initTabs() {
        int right = this.width - 2;
        int y = 0;
        int h = 14;
        int gap = 6;
        int configWidth = Math.max(60, this.font.width(Component.translatable("dlc_manager.server.config")) + 12);
        int configX = right - configWidth;
        configButton = Button.builder(Component.translatable("dlc_manager.server.config"), btn -> openConfig())
            .bounds(configX, y, configWidth, h)
            .build();
        int serverWidth = Math.max(60, this.font.width(Component.translatable("dlc_manager.server.tab.servers")) + 12);
        int serverX = configX - gap - serverWidth;
        tabServers = Button.builder(Component.translatable("dlc_manager.server.tab.servers"), btn -> openTab(Tab.SERVERS))
            .bounds(serverX, y, serverWidth, h)
            .build();
        int dlcWidth = Math.max(48, this.font.width(Component.translatable("dlc_manager.server.tab.dlc")) + 12);
        int dlcX = serverX - gap - dlcWidth;
        tabDlc = Button.builder(Component.translatable("dlc_manager.server.tab.dlc"), btn -> openDlcPage())
            .bounds(dlcX, y, dlcWidth, h)
            .build();
        addRenderableWidget(tabDlc);
        addRenderableWidget(tabServers);
        addRenderableWidget(configButton);
        updateTabStates();
    }

    private void updateTabStates() {
        if (tabDlc != null) {
            tabDlc.active = true;
        }
        if (tabServers != null) {
            tabServers.active = activeTab != Tab.SERVERS;
        }
    }

    private void openTab(Tab tab) {
        if (tab == Tab.DLC) {
            openDlcPage();
            return;
        }
        if (activeTab == tab) {
            return;
        }
        activeTab = tab;
        if (activeTab == Tab.SERVERS) {
            loadServers();
        }
        rebuildUi();
    }

    private void openDlcPage() {
        Screen rootParent = parent;
        if (parent instanceof DlcServerMaLiLibScreen mali) {
            Screen root = mali.getRootParent();
            if (root != null) {
                rootParent = root;
            }
        }
        Minecraft.getInstance().setScreen(new DlcServerMaLiLibScreen(rootParent, entry, buildInfoSnapshot(), openedFromIngame));
    }

    private DlcServerInfo buildInfoSnapshot() {
        DlcServerInfo info = new DlcServerInfo();
        info.items = new ArrayList<>(items);
        info.remoteMissing = remoteMissingCount;
        info.requiredMissing = requiredMissingCount;
        info.baseVersion = serverBaseVersion;
        info.dlcVersion = serverDlcVersion;
        return info;
    }

    private void openConfig() {
        Minecraft.getInstance().setScreen(new DlcServerConfigMaLiLibScreen(this, entry));
    }

    private void openPreset() {
        Minecraft.getInstance().setScreen(new DlcPresetManagerScreen(this) {
            @Override
            public void onClose() {
                applyPresetOverrides();
                super.onClose();
            }
        });
    }

    private void initDlcTab() {
        int gap = 8;
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int panelWidth = panelRight - panelLeft;
        int buttonWidth = Math.min(90, (panelWidth - 16 - gap * 4) / 5);
        if (buttonWidth < 70) {
            buttonWidth = 70;
        }
        int buttonHeight = 20;
        int y = this.height - 22;
        int rightX = panelRight - 8;
        int leftX = panelLeft + 8;
        enableAllButton = Button.builder(Component.translatable("dlc_manager.enable_all"), btn -> setAllEnabled(true))
            .bounds(leftX, y, buttonWidth, buttonHeight)
            .build();
        disableAllButton = Button.builder(Component.translatable("dlc_manager.disable_all"), btn -> setAllEnabled(false))
            .bounds(leftX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        applyButton = Button.builder(Component.translatable("dlc_manager.apply"), btn -> applyChanges())
            .bounds(rightX - buttonWidth * 2 - gap, y, buttonWidth, buttonHeight)
            .build();
        presetButton = Button.builder(Component.translatable("dlc_manager.preset.manager"), btn -> openPreset())
            .bounds(applyButton.getX() - gap - buttonWidth, y, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(rightX - buttonWidth, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(enableAllButton);
        addRenderableWidget(disableAllButton);
        addRenderableWidget(presetButton);
        addRenderableWidget(applyButton);
        addRenderableWidget(backButton);
        initDlcFilters();
        updateApplyState();
    }

    private void initServerTab() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int startY = this.height / 2 - buttonHeight - 8;
        pushButton = Button.builder(Component.translatable("dlc_manager.server.push"), btn -> openPush())
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build();
        rconButton = Button.builder(Component.translatable("dlc_manager.server.rcon"), btn -> openRcon())
            .bounds(centerX - buttonWidth / 2, startY + buttonHeight + 8, buttonWidth, buttonHeight)
            .build();
        filesButton = Button.builder(Component.translatable("dlc_manager.server.files"), btn -> openFiles())
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + 8) * 2, buttonWidth, buttonHeight)
            .build();
        updateManagerButton = Button.builder(Component.translatable("dlc_manager.update.manager"),
                btn -> Minecraft.getInstance().setScreen(new DlcServerUpdateManagerScreen(this, entry)))
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + 8) * 3, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(pushButton);
        addRenderableWidget(rconButton);
        addRenderableWidget(filesButton);
        addRenderableWidget(updateManagerButton);
        if (openedFromIngame) {
            returnToGameButton = Button.builder(Component.translatable("menu.returnToGame"), btn -> Minecraft.getInstance().setScreen(null))
                .bounds(centerX - buttonWidth / 2, this.height - 22, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(returnToGameButton);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        drawPanel(guiGraphics);
        int panelLeft = panelLeft();
        guiGraphics.drawString(this.font, getTitle(), 8, 6, 0xFFFFFF, false);
        if (headerLine != null) {
            guiGraphics.drawString(this.font, headerLine, 8, 16, 0xC8C8C8, false);
        }
        if (activeTab == Tab.DLC) {
            renderDlcTab(guiGraphics);
        } else {
            renderServerTab(guiGraphics);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int panelTop = panelTop();
        int panelBottom = panelBottom();
        int panelColor = 0xCC0F1114;
        int panelBorder = 0xFF2A2F36;
        int accent = 0xFF4C84FF;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, panelColor);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, panelBorder);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, panelBorder);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, panelBorder);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, panelBorder);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 120, panelTop + 2, accent);
    }

    private void renderDlcTab(GuiGraphics guiGraphics) {
        int listTop = listTop();
        int listBottom = listBottom();
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int totalLines = displayEntries.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int rowY = listTop;
        DisplayEntry hovered = null;
        for (int i = 0; i < displayEntries.size(); i++) {
            DisplayEntry entry = displayEntries.get(i);
            if (i < scrollIndex || i >= scrollIndex + maxVisible) {
                continue;
            }
            int labelY = rowY + 6;
            if (entry.header) {
                guiGraphics.drawString(this.font, entry.label, 36, labelY, 0x8CB3FF, false);
            } else if (entry.item != null) {
                DlcServerItem item = entry.item;
                guiGraphics.drawString(this.font, resolveDisplayName(item), 48, labelY, 0xE6E6E6, false);
                drawToggle(guiGraphics, rowY, item.enabled);
            }
            if (!entry.header && entry.item != null && isRowHovered(rowY)) {
                hovered = entry;
            }
            rowY += ROW_HEIGHT;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll, scrollIndex);
        if (applying) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.applying"), this.width / 2, listBottom + 4, 0xC8C8C8);
        }
        if (hovered != null) {
            renderItemTooltip(guiGraphics, hovered.item);
        }
    }

    private void renderServerTab(GuiGraphics guiGraphics) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = listTop();
        int listBottom = listBottom();
        if (activeTab == Tab.DLC) {
            int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
            int maxScroll = Math.max(0, displayEntries.size() - maxVisible);
            if (startDlcScrollDrag(mouseX, mouseY, listTop, listBottom, displayEntries.size(), maxVisible, maxScroll)) {
                return true;
            }
            if (mouseY >= listTop && mouseY <= listBottom) {
                int index = (int) ((mouseY - listTop) / ROW_HEIGHT) + scrollIndex;
                if (index >= 0 && index < displayEntries.size()) {
                    DisplayEntry entry = displayEntries.get(index);
                    if (!entry.header && entry.item != null) {
                        if (isToggleClick(mouseX, mouseY, rowYForIndex(index, listTop))) {
                            entry.item.enabled = !entry.item.enabled;
                            updateApplyState();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeTab == Tab.DLC && draggingDlcScroll) {
            int listTop = listTop();
            int listBottom = listBottom();
            int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
            int maxScroll = Math.max(0, displayEntries.size() - maxVisible);
            updateDlcScrollFromDrag(mouseY, listTop, listBottom, displayEntries.size(), maxVisible, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingDlcScroll) {
            draggingDlcScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int listTop = listTop();
        int listBottom = listBottom();
        if (mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        if (activeTab == Tab.DLC) {
            int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
            int maxScroll = Math.max(0, displayEntries.size() - maxVisible);
            if (maxScroll > 0) {
                int step = deltaY > 0 ? -1 : 1;
                scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void rebuildDisplayEntries() {
        displayEntries.clear();
        Map<DlcType, List<DlcServerItem>> grouped = new EnumMap<>(DlcType.class);
        for (DlcType type : DlcType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (DlcServerItem item : items) {
            grouped.get(mapType(item.type)).add(item);
        }
        if (activeFilter == null) {
            for (DlcType type : DlcType.values()) {
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

    private void initDlcFilters() {
        filterButtons.clear();
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int available = panelRight - panelLeft - 20;
        int count = DlcType.values().length + 1;
        int gap = Math.max(4, Math.min(FILTER_TAB_GAP, 8));
        int tabWidth = (available - gap * (count - 1)) / count;
        if (tabWidth < 48) {
            tabWidth = 48;
        }
        int totalWidth = tabWidth * count + gap * (count - 1);
        if (totalWidth > available) {
            tabWidth = Math.max(32, (available - gap * (count - 1)) / count);
            totalWidth = tabWidth * count + gap * (count - 1);
        }
        int startX = panelLeft + (available - totalWidth) / 2 + 10;
        if (startX < panelLeft + 6) {
            startX = panelLeft + 6;
        }
        int y = filterTabsY();
        filterAllButton = Button.builder(Component.translatable("dlc_manager.filter_all"), btn -> setFilter(null))
            .bounds(startX, y, tabWidth, FILTER_TAB_HEIGHT)
            .build();
        addRenderableWidget(filterAllButton);
        int index = 1;
        for (DlcType type : DlcType.values()) {
            Button button = Button.builder(Component.translatable(type.getDisplayKey()), btn -> setFilter(type))
                .bounds(startX + index * (tabWidth + gap), y, tabWidth, FILTER_TAB_HEIGHT)
                .build();
            filterButtons.put(type, button);
            addRenderableWidget(button);
            index++;
        }
        updateFilterButtons();
    }

    private void setFilter(DlcType type) {
        if (activeFilter == type) {
            return;
        }
        activeFilter = type;
        scrollIndex = 0;
        rebuildDisplayEntries();
        updateFilterButtons();
    }

    private void updateFilterButtons() {
        if (filterAllButton != null) {
            filterAllButton.active = activeFilter != null;
        }
        for (Map.Entry<DlcType, Button> entry : filterButtons.entrySet()) {
            entry.getValue().active = activeFilter != entry.getKey();
        }
    }

    private int panelLeft() {
        return 16;
    }

    private int panelRight() {
        return this.width - 16;
    }

    private int panelTop() {
        return 28;
    }

    private int panelBottom() {
        return this.height - 24;
    }

    private int filterTabsY() {
        return panelTop() + 6;
    }

    private int listTop() {
        if (activeTab == Tab.DLC) {
            return filterTabsY() + FILTER_TAB_HEIGHT + 8;
        }
        return panelTop() + 6;
    }

    private int listBottom() {
        return this.height - 54;
    }

    private void drawToggle(GuiGraphics guiGraphics, int rowY, boolean enabled) {
        int toggleWidth = 54;
        int toggleHeight = 16;
        int toggleX = panelRight() - 12 - toggleWidth;
        int toggleY = rowY + (ROW_HEIGHT - toggleHeight) / 2;
        int bg = enabled ? 0xFF2E6BD9 : 0xFF3A3A3A;
        int border = 0xFF5A5A5A;
        guiGraphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, bg);
        guiGraphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + 1, border);
        guiGraphics.fill(toggleX, toggleY + toggleHeight - 1, toggleX + toggleWidth, toggleY + toggleHeight, border);
        guiGraphics.fill(toggleX, toggleY, toggleX + 1, toggleY + toggleHeight, border);
        guiGraphics.fill(toggleX + toggleWidth - 1, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, border);
        String label = enabled ? Component.translatable("dlc_manager.server.toggle_on").getString()
            : Component.translatable("dlc_manager.server.toggle_off").getString();
        int textX = toggleX + (toggleWidth - this.font.width(label)) / 2;
        int textY = toggleY + 4;
        guiGraphics.drawString(this.font, label, textX, textY, 0xFFFFFF, false);
    }

    private boolean isToggleClick(double mouseX, double mouseY, int rowY) {
        int toggleWidth = 54;
        int toggleHeight = 16;
        int toggleX = panelRight() - 12 - toggleWidth;
        int toggleY = rowY + (ROW_HEIGHT - toggleHeight) / 2;
        return mouseX >= toggleX && mouseX <= toggleX + toggleWidth
            && mouseY >= toggleY && mouseY <= toggleY + toggleHeight;
    }

    private int rowYForIndex(int index, int listTop) {
        return listTop + (index - scrollIndex) * ROW_HEIGHT;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        rebuildUi();
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

    private void setAllEnabled(boolean enabled) {
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            if (!matchesActiveFilter(item)) {
                continue;
            }
            item.enabled = enabled;
        }
        updateApplyState();
    }

    private boolean matchesActiveFilter(DlcServerItem item) {
        if (activeFilter == null) {
            return true;
        }
        return mapType(item.type) == activeFilter;
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
        Set<String> conflictIds = new HashSet<>();
        for (String id : enabledPresets) {
            DlcPresetEntry entry = byId.get(id);
            if (entry == null) {
                continue;
            }
            for (String enable : entry.getEnable()) {
                if (enable != null && !enable.isBlank()) {
                    enableIds.add(enable.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            for (String conflict : entry.getConflicts()) {
                if (conflict != null && !conflict.isBlank()) {
                    conflictIds.add(conflict.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        for (DlcServerItem item : items) {
            if (item == null || item.identifier == null) {
                continue;
            }
            String key = item.identifier.trim().toLowerCase(java.util.Locale.ROOT);
            if (enableIds.contains(key)) {
                item.enabled = true;
            }
            if (conflictIds.contains(key)) {
                item.enabled = false;
            }
        }
        rebuildDisplayEntries();
        updateApplyState();
    }

    private void applyChanges() {
        if (applying) {
            return;
        }
        applying = true;
        updateApplyState();
        List<DlcServerItem> changed = new ArrayList<>();
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            Boolean baselineValue = baseline.get(item.id);
            if (baselineValue == null) {
                baselineValue = false;
            }
            if (baselineValue != item.enabled) {
                DlcServerItem copy = new DlcServerItem();
                copy.id = item.id;
                copy.enabled = item.enabled;
                changed.add(copy);
            }
        }
        CompletableFuture
            .supplyAsync(() -> applyRemoteChanges(changed))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                applying = false;
                if (response != null && response.ok) {
                    for (DlcServerItem item : items) {
                        baseline.put(item.id, item.enabled);
                    }
                } else {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.apply_failed"), Component.empty());
                }
                updateApplyState();
                if (response != null && response.ok && response.requiresRestart) {
                    promptServerRestart();
                }
            }));
    }

    private DlcServerConnection.ApplyResponse applyRemoteChanges(List<DlcServerItem> changes) {
        try {
            return DlcServerConnection.applyChangesWithResponse(entry, 8000, changes);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void promptServerRestart() {
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(result -> {
            if (result) {
                CompletableFuture.runAsync(() -> {
                    try {
                        DlcServerConnection.restartServer(entry, 8000);
                    } catch (Exception ignored) {
                    }
                });
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.translatable("dlc_manager.server.restart_title"),
            Component.translatable("dlc_manager.server.restart_prompt")));
    }

    private void startRemoteDownload() {
        Minecraft.getInstance().setScreen(new DlcServerRemoteDownloadScreen(this, entry));
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

    private void openAdd() {
        Minecraft.getInstance().setScreen(new DlcServerEditScreen(this, null, entry -> {
            servers.add(entry);
            DlcServerList.setEntries(servers);
            DlcServerList.save();
        }));
    }

    private void connectSelected() {
        if (connecting) {
            return;
        }
        connecting = true;
        DlcServerEntry target = entry;
        CompletableFuture
            .supplyAsync(() -> fetchInfo(target))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                connecting = false;
                if (info == null) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.connect_failed"), Component.empty());
                    return;
                }
                DlcServerCache.put(target.getKey(), info);
                Minecraft.getInstance().setScreen(new DlcServerManagerScreen(this, target, info, null, openedFromIngame));
            }));
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DlcServerEntry getSelectedEntry() {
        if (serverSelectedIndex >= 0 && serverSelectedIndex < servers.size()) {
            return servers.get(serverSelectedIndex);
        }
        return null;
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
    private String resolveDisplayName(DlcServerItem item) {
        if (item == null) {
            return "";
        }
        if (item.displayNames == null || item.displayNames.isEmpty()) {
            return item.name == null ? "" : item.name;
        }
        String lang = CrossPlatformRuntime.languageCode();
        if (item.displayNames.containsKey(lang)) {
            return item.displayNames.get(lang);
        }
        if (item.displayNames.containsKey("en_us")) {
            return item.displayNames.get("en_us");
        }
        return item.displayNames.values().stream().findFirst().orElse(item.name == null ? "" : item.name);
    }

    private void renderItemTooltip(GuiGraphics guiGraphics, DlcServerItem item) {
        if (item == null || item.tips == null || item.tips.isEmpty()) {
            return;
        }
        String lang = CrossPlatformRuntime.languageCode();
        String tip = item.tips.get(lang);
        if (tip == null || tip.isBlank()) {
            tip = item.tips.getOrDefault("en_us", "");
        }
        if (tip == null || tip.isBlank()) {
            return;
        }
        guiGraphics.renderTooltip(this.font, this.font.split(Component.literal(tip), 240), 0, 0);
    }

    private boolean isRowHovered(int rowY) {
        return this.lastMouseY >= rowY && this.lastMouseY <= rowY + ROW_HEIGHT;
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll, int index) {
        if (totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 20;
        int barWidth = 6;
        int barHeight = Math.max(20, (bottom - top) * maxVisible / Math.max(1, totalLines));
        int trackHeight = bottom - top;
        int barY = top + (trackHeight - barHeight) * index / Math.max(1, maxScroll);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
    }

    private boolean startDlcScrollDrag(double mouseX, double mouseY, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible) {
            return false;
        }
        int barX = this.width - 20;
        int barWidth = 6;
        int trackHeight = bottom - top;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, totalLines));
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
            draggingDlcScroll = true;
            dlcScrollDragOffset = (int) (mouseY - barY);
            return true;
        }
        return false;
    }

    private void updateDlcScrollFromDrag(double mouseY, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible || maxScroll <= 0) {
            scrollIndex = 0;
            return;
        }
        int trackHeight = bottom - top;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, totalLines));
        int minY = top;
        int maxY = bottom - barHeight;
        int barY = (int) Math.max(minY, Math.min(maxY, mouseY - dlcScrollDragOffset));
        int range = Math.max(1, maxY - minY);
        double ratio = (barY - minY) / (double) range;
        scrollIndex = Math.max(0, Math.min(maxScroll, (int) Math.round(ratio * maxScroll)));
    }
}
