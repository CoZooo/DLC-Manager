package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconPlayersScreen extends Screen {
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private final DlcServerEntry entry;
    private final int port;
    private final String password;

    private final List<String> players = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private String statusText = "";

    private Button refreshButton;
    private Button opButton;
    private Button deopButton;
    private Button kickButton;
    private Button banButton;
    private Button backButton;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private boolean draggingScrollbar;

    public DlcServerRconPlayersScreen(Screen parent, DlcServerEntry entry, int port, String password) {
        super(Component.translatable("dlc_manager.server.rcon_players"));
        this.parent = parent;
        this.entry = entry;
        this.port = port;
        this.password = password == null ? "" : password;
    }

    @Override
    protected void init() {
        int margin = 20;
        listLeft = margin;
        listRight = this.width - margin;
        listTop = 60;
        listBottom = this.height - 60;

        int buttonY = 32;
        refreshButton = Button.builder(Component.translatable("dlc_manager.server.rcon_refresh"), btn -> refreshPlayers())
            .bounds(margin, buttonY, 80, 20)
            .build();
        opButton = Button.builder(Component.translatable("dlc_manager.server.rcon_op"), btn -> opSelected())
            .bounds(margin + 86, buttonY, 60, 20)
            .build();
        deopButton = Button.builder(Component.translatable("dlc_manager.server.rcon_deop"), btn -> deopSelected())
            .bounds(margin + 150, buttonY, 60, 20)
            .build();
        kickButton = Button.builder(Component.translatable("dlc_manager.server.rcon_kick"), btn -> kickSelected())
            .bounds(margin + 214, buttonY, 60, 20)
            .build();
        banButton = Button.builder(Component.translatable("dlc_manager.server.rcon_ban"), btn -> banSelected())
            .bounds(margin + 278, buttonY, 60, 20)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width - margin - 80, this.height - 30, 80, 20)
            .build();

        addRenderableWidget(refreshButton);
        addRenderableWidget(opButton);
        addRenderableWidget(deopButton);
        addRenderableWidget(kickButton);
        addRenderableWidget(banButton);
        addRenderableWidget(backButton);

        refreshPlayers();
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 12, 0xFFFFFF);
        if (!statusText.isBlank()) {
            guiGraphics.drawString(this.font, statusText, listLeft, 48, 0xC8C8C8, false);
        }
        drawList(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawList(GuiGraphics guiGraphics) {
        int height = listBottom - listTop;
        if (height <= 0) {
            return;
        }
        guiGraphics.fill(listLeft, listTop, listRight, listBottom, 0x22000000);
        int maxVisible = Math.max(1, height / ROW_HEIGHT);
        int maxScroll = Math.max(0, players.size() - maxVisible);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        int rowY = listTop + 2;
        for (int i = 0; i < players.size(); i++) {
            if (i < scroll || i >= scroll + maxVisible) {
                continue;
            }
            boolean isSelected = i == selected;
            if (isSelected) {
                guiGraphics.fill(listLeft + 2, rowY - 1, listRight - 2, rowY + ROW_HEIGHT - 1, 0x33222222);
                guiGraphics.fill(listLeft + 2, rowY - 1, listRight - 2, rowY, 0xFFFFFFFF);
                guiGraphics.fill(listLeft + 2, rowY + ROW_HEIGHT - 2, listRight - 2, rowY + ROW_HEIGHT - 1, 0xFFFFFFFF);
                int leftX = listLeft + 2;
                int rightX = listRight - 3;
                guiGraphics.fill(leftX, rowY - 1, leftX + 1, rowY + ROW_HEIGHT - 1, 0xFFFFFFFF);
                guiGraphics.fill(rightX, rowY - 1, rightX + 1, rowY + ROW_HEIGHT - 1, 0xFFFFFFFF);
            }
            guiGraphics.drawString(this.font, players.get(i), listLeft + 6, rowY + 4, 0xE6E6E6, false);
            rowY += ROW_HEIGHT;
        }
        drawScrollbar(guiGraphics, maxVisible, maxScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOnScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int index = (int) ((mouseY - listTop) / ROW_HEIGHT) + scroll;
            if (index >= 0 && index < players.size()) {
                selected = index;
                updateButtonStates();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int height = listBottom - listTop;
            int maxVisible = Math.max(1, height / ROW_HEIGHT);
            int maxScroll = Math.max(0, players.size() - maxVisible);
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll + (delta > 0 ? -1 : 1)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawScrollbar(GuiGraphics guiGraphics, int maxVisible, int maxScroll) {
        int totalLines = players.size();
        if (totalLines <= maxVisible) {
            return;
        }
        int barWidth = 6;
        int barX = listRight - barWidth - 2;
        int trackTop = listTop + 1;
        int trackBottom = listBottom - 1;
        int trackHeight = trackBottom - trackTop;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, totalLines));
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = trackTop + maxTrack * scroll / Math.max(1, maxScroll);
        guiGraphics.fill(barX, trackTop, barX + barWidth, trackBottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
    }

    private boolean isOnScrollbar(double mouseX, double mouseY) {
        int barWidth = 8;
        int barX = listRight - barWidth - 2;
        return mouseX >= barX && mouseX <= barX + barWidth && mouseY >= listTop && mouseY <= listBottom;
    }

    private void updateScrollFromMouse(double mouseY) {
        int height = listBottom - listTop;
        int maxVisible = Math.max(1, height / ROW_HEIGHT);
        int maxScroll = Math.max(0, players.size() - maxVisible);
        if (maxScroll <= 0) {
            scroll = 0;
            return;
        }
        int trackTop = listTop + 1;
        int trackBottom = listBottom - 1;
        int trackHeight = trackBottom - trackTop;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, players.size()));
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int target = (int) (mouseY - trackTop - barHeight / 2.0);
        target = Math.max(0, Math.min(maxTrack, target));
        scroll = Math.max(0, Math.min(maxScroll, target * maxScroll / Math.max(1, maxTrack)));
    }

    private void refreshPlayers() {
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, "list", 8000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    statusText = Component.translatable("dlc_manager.server.rcon_connect_failed").getString();
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_connect_failed"), Component.empty());
                } else {
                    statusText = "";
                    updatePlayersFromList(response);
                }
                updateButtonStates();
            }));
    }

    private void updatePlayersFromList(String response) {
        players.clear();
        selected = -1;
        if (response != null) {
            int colon = response.indexOf(':');
            if (colon >= 0 && colon + 1 < response.length()) {
                String list = response.substring(colon + 1).trim();
                if (!list.isEmpty()) {
                    String[] split = list.split(",");
                    for (String part : split) {
                        String name = part.trim();
                        if (!name.isEmpty()) {
                            players.add(name);
                        }
                    }
                }
            }
        }
    }

    private void kickSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("kick " + player, true);
    }

    private void banSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("ban " + player, true);
    }

    private void opSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("op " + player, true);
    }

    private void deopSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("deop " + player, true);
    }

    private void runCommand(String command, boolean refreshAfter) {
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else if (refreshAfter) {
                    refreshPlayers();
                }
            }));
    }

    private String getSelectedPlayer() {
        if (selected >= 0 && selected < players.size()) {
            return players.get(selected);
        }
        return null;
    }

    private void updateButtonStates() {
        boolean hasPlayer = getSelectedPlayer() != null;
        if (kickButton != null) {
            kickButton.active = hasPlayer;
        }
        if (banButton != null) {
            banButton.active = hasPlayer;
        }
        if (opButton != null) {
            opButton.active = hasPlayer;
        }
        if (deopButton != null) {
            deopButton.active = hasPlayer;
        }
    }
}
