package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerListScreen extends Screen {
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private List<DlcServerEntry> servers = List.of();
    private int scrollIndex;
    private int selectedIndex = -1;
    private int lastClickIndex = -1;
    private long lastClickTime;
    private Button addButton;
    private Button connectButton;
    private Button deleteButton;
    private Button editButton;
    private Button backButton;
    private volatile boolean connecting;

    public DlcServerListScreen(Screen parent) {
        super(Component.translatable("dlc_manager.server.list_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DlcServerList.load();
        servers = new ArrayList<>(DlcServerList.getEntries());
        String lastKey = DlcServerList.getLastSelectedKey();
        if (lastKey != null) {
            for (int i = 0; i < servers.size(); i++) {
                DlcServerEntry entry = servers.get(i);
                if (entry != null && lastKey.equals(entry.getKey())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        int buttonWidth = 110;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int row1Y = this.height - 52;
        int row2Y = this.height - 28;
        int gap = 6;
        int row1Total = buttonWidth * 3 + gap * 2;
        int row2Total = buttonWidth * 2 + gap;
        int row1X = centerX - row1Total / 2;
        int row2X = centerX - row2Total / 2;
        deleteButton = Button.builder(Component.translatable("selectServer.delete"), btn -> deleteSelected())
            .bounds(row1X, row1Y, buttonWidth, buttonHeight)
            .build();
        editButton = Button.builder(Component.translatable("selectServer.edit"), btn -> editSelected())
            .bounds(row1X + buttonWidth + gap, row1Y, buttonWidth, buttonHeight)
            .build();
        addButton = Button.builder(Component.translatable("selectServer.add"), btn -> openAdd())
            .bounds(row1X + (buttonWidth + gap) * 2, row1Y, buttonWidth, buttonHeight)
            .build();
        connectButton = Button.builder(Component.translatable("dlc_manager.server.connect"), btn -> connectSelected())
            .bounds(row2X, row2Y, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> {
            if (parent != null) {
                Minecraft.getInstance().setScreen(parent);
                return;
            }
            Minecraft.getInstance().setScreen(new DlcMaLiLibScreen(null));
        })
            .bounds(row2X + buttonWidth + gap, row2Y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(deleteButton);
        addRenderableWidget(editButton);
        addRenderableWidget(addButton);
        addRenderableWidget(connectButton);
        addRenderableWidget(backButton);
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, 20, 0xFFFFFF);
        int listTop = 50;
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int totalLines = servers.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int rowY = listTop;
        for (int i = 0; i < servers.size(); i++) {
            DlcServerEntry entry = servers.get(i);
            boolean visible = i >= scrollIndex && i < scrollIndex + maxVisible;
            if (!visible) {
                continue;
            }
            boolean selected = i == selectedIndex;
            if (selected) {
                int left = 28;
                int right = this.width - 28;
                int top = rowY + 2;
                int bottom = rowY + ROW_HEIGHT;
                guiGraphics.fill(left, top, right, bottom, 0x33222222);
                guiGraphics.fill(left, top, right, top + 1, 0xFFFFFFFF);
                guiGraphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
                guiGraphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
                guiGraphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
            }
            String label = entry == null ? "" : entry.getDisplayName();
            String host = entry == null ? "" : entry.getKey();
            guiGraphics.drawString(this.font, label, 36, rowY + 4, 0xE6E6E6, false);
            guiGraphics.drawString(this.font, host, 36, rowY + 13, 0x9A9A9A, false);
            rowY += ROW_HEIGHT;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
        if (connecting) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.connecting"), centerX, listBottom + 4, 0xC8C8C8);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = 50;
        int listBottom = this.height - 70;
        if (mouseY >= listTop && mouseY <= listBottom) {
            int index = (int) ((mouseY - listTop) / ROW_HEIGHT) + scrollIndex;
            if (index >= 0 && index < servers.size()) {
                selectedIndex = index;
                String key = servers.get(index).getKey();
                DlcServerList.setLastSelectedKey(key);
                DlcServerList.save();
                updateButtonStates();
                if (button == 0) {
                    long now = System.currentTimeMillis();
                    if (lastClickIndex == index && now - lastClickTime < 250) {
                        connectSelected();
                        lastClickIndex = -1;
                        lastClickTime = 0L;
                        return true;
                    }
                    lastClickIndex = index;
                    lastClickTime = now;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int listTop = 50;
        int listBottom = this.height - 70;
        if (mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, servers.size() - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        clearWidgets();
        init();
    }

    private void openAdd() {
        Minecraft.getInstance().setScreen(new DlcServerEditScreen(this, null, entry -> {
            servers.add(entry);
            DlcServerList.setEntries(servers);
            DlcServerList.save();
            selectedIndex = servers.size() - 1;
            updateButtonStates();
        }));
    }

    private void editSelected() {
        DlcServerEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new DlcServerEditScreen(this, entry, updated -> {
            if (selectedIndex >= 0 && selectedIndex < servers.size()) {
                servers.set(selectedIndex, updated);
                DlcServerList.setEntries(servers);
                DlcServerList.save();
            }
            updateButtonStates();
        }));
    }

    private void deleteSelected() {
        DlcServerEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        Component title = Component.translatable("selectServer.deleteQuestion");
        Component message = Component.translatable("selectServer.deleteWarning", entry.getDisplayName());
        Minecraft.getInstance().setScreen(new ConfirmScreen(result -> {
            if (result && selectedIndex >= 0 && selectedIndex < servers.size()) {
                servers.remove(selectedIndex);
                if (selectedIndex >= servers.size()) {
                    selectedIndex = servers.size() - 1;
                }
                DlcServerList.setEntries(servers);
                DlcServerList.save();
            }
            updateButtonStates();
            Minecraft.getInstance().setScreen(this);
        }, title, message));
    }

    private void connectSelected() {
        if (connecting) {
            return;
        }
        DlcServerEntry entry = getSelectedEntry();
        if (entry == null) {
            SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.server.select_required"), Component.empty());
            return;
        }
        connecting = true;
        updateButtonStates();
        CompletableFuture
            .supplyAsync(() -> fetchInfo(entry))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                connecting = false;
                updateButtonStates();
                if (info == null) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.connect_failed"), Component.empty());
                    return;
                }
                DlcServerCache.put(entry.getKey(), info);
                Minecraft.getInstance().setScreen(new DlcServerMaLiLibScreen(this, entry, info));
            }));
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void updateButtonStates() {
        boolean hasSelection = getSelectedEntry() != null;
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
        if (editButton != null) {
            editButton.active = hasSelection;
        }
        if (connectButton != null) {
            connectButton.active = hasSelection && !connecting;
        }
    }
    private DlcServerEntry getSelectedEntry() {
        if (selectedIndex >= 0 && selectedIndex < servers.size()) {
            return servers.get(selectedIndex);
        }
        return null;
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 20;
        int barWidth = 6;
        int barHeight = Math.max(20, (bottom - top) * maxVisible / Math.max(1, totalLines));
        int trackHeight = bottom - top;
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
    }
}
