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

public class DlcServerRconQuickScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final int port;
    private final String password;

    private final List<Entry> entries = new ArrayList<>();
    private String statusText;

    private Button backButton;
    private boolean draggingScrollbar;
    private int scroll;
    private int panelTop;
    private int panelBottom;
    private int panelLeft;
    private int panelRight;
    private int scrollbarX;
    private int buttonWidth;
    private int buttonHeight;
    private int buttonGap;

    public DlcServerRconQuickScreen(Screen parent, DlcServerEntry entry, int port, String password) {
        super(Component.translatable("dlc_manager.server.rcon_common"));
        this.parent = parent;
        this.entry = entry;
        this.port = port;
        this.password = password == null ? "" : password;
    }

    @Override
    protected void init() {
        entries.clear();
        addSeparator("dlc_manager.server.rcon_group_time");
        addCommand("dlc_manager.server.rcon_cmd_time_day", "time set day");
        addCommand("dlc_manager.server.rcon_cmd_time_night", "time set night");
        addSeparator("dlc_manager.server.rcon_group_weather");
        addCommand("dlc_manager.server.rcon_cmd_weather_cycle_on", "gamerule doWeatherCycle true");
        addCommand("dlc_manager.server.rcon_cmd_weather_cycle_off", "gamerule doWeatherCycle false");
        addCommand("dlc_manager.server.rcon_cmd_weather_clear", "weather clear");
        addCommand("dlc_manager.server.rcon_cmd_weather_rain", "weather rain");
        addCommand("dlc_manager.server.rcon_cmd_weather_thunder", "weather thunder");
        addSeparator("dlc_manager.server.rcon_group_world");
        addCommand("dlc_manager.server.rcon_cmd_keep_inventory_on", "gamerule keepInventory true");
        addCommand("dlc_manager.server.rcon_cmd_keep_inventory_off", "gamerule keepInventory false");
        addCommand("dlc_manager.server.rcon_cmd_daylight_on", "gamerule doDaylightCycle true");
        addCommand("dlc_manager.server.rcon_cmd_daylight_off", "gamerule doDaylightCycle false");
        addCommand("dlc_manager.server.rcon_cmd_mobspawning_on", "gamerule doMobSpawning true");
        addCommand("dlc_manager.server.rcon_cmd_mobspawning_off", "gamerule doMobSpawning false");
        addCommand("dlc_manager.server.rcon_cmd_mobgriefing_on", "gamerule mobGriefing true");
        addCommand("dlc_manager.server.rcon_cmd_mobgriefing_off", "gamerule mobGriefing false");
        addCommand("dlc_manager.server.rcon_cmd_firetick_on", "gamerule doFireTick true");
        addCommand("dlc_manager.server.rcon_cmd_firetick_off", "gamerule doFireTick false");

        int margin = 20;
        panelLeft = margin;
        panelRight = this.width - margin - 16;
        panelTop = 40;
        panelBottom = this.height - 50;
        scrollbarX = this.width - margin + 2;
        buttonWidth = Math.max(120, (this.width - margin * 2 - 6) / 2);
        buttonHeight = 20;
        buttonGap = 6;
        int x1 = this.width / 2 - buttonWidth - 3;
        int x2 = this.width / 2 + 3;
        int y = panelTop;

        int col = 0;
        for (Entry entry : entries) {
            if (entry.separator) {
                if (col != 0) {
                    y += buttonHeight + buttonGap;
                    col = 0;
                }
                entry.baseY = y;
                y += buttonHeight + buttonGap;
                continue;
            }
            int x = (col == 0) ? x1 : x2;
            entry.button = Button.builder(Component.translatable(entry.labelKey), btn -> runCommand(entry.command))
                .bounds(x, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(entry.button);
            entry.baseY = y;
            col++;
            if (col >= 2) {
                y += buttonHeight + buttonGap;
                col = 0;
            }
        }

        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 - 40, this.height - 30, 80, 20)
            .build();
        addRenderableWidget(backButton);

        updateButtonPositions();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 16, 0xFFFFFF);
        if (statusText != null && !statusText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, statusText, this.width / 2, this.height - 52, 0xC8C8C8);
        }
        drawSeparators(guiGraphics);
        drawScrollbar(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom) {
            int maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll + (vertical > 0 ? -1 : 1) * (buttonHeight + buttonGap)));
                updateButtonPositions();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOnScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void runCommand(String command) {
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else {
                    statusText = Component.translatable("dlc_manager.server.rcon_response", response).getString();
                }
            }));
    }

    private void updateButtonPositions() {
        for (Entry entry : entries) {
            int y = entry.baseY - scroll;
            if (entry.separator) {
                entry.visible = y + buttonHeight >= panelTop && y <= panelBottom - 2;
                continue;
            }
            entry.button.setY(y);
            boolean visible = y + buttonHeight >= panelTop && y <= panelBottom - 2;
            entry.button.visible = visible;
            entry.button.active = visible;
        }
    }

    private int getMaxScroll() {
        if (entries.isEmpty()) {
            return 0;
        }
        int totalHeight = 0;
        int col = 0;
        for (Entry entry : entries) {
            if (entry.separator) {
                if (col != 0) {
                    totalHeight += buttonHeight + buttonGap;
                    col = 0;
                }
                totalHeight += buttonHeight + buttonGap;
                continue;
            }
            col++;
            if (col >= 2) {
                totalHeight += buttonHeight + buttonGap;
                col = 0;
            }
        }
        if (col != 0) {
            totalHeight += buttonHeight + buttonGap;
        }
        int visibleHeight = panelBottom - panelTop;
        return Math.max(0, totalHeight - visibleHeight);
    }

    private void drawScrollbar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int barWidth = 6;
        int barX = scrollbarX;
        int trackTop = panelTop;
        int trackBottom = panelBottom;
        int trackHeight = trackBottom - trackTop;
        int visibleHeight = panelBottom - panelTop;
        int totalHeight = visibleHeight + maxScroll;
        int barHeight = Math.max(20, trackHeight * visibleHeight / Math.max(1, totalHeight));
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = trackTop + maxTrack * scroll / Math.max(1, maxScroll);
        guiGraphics.fill(barX, trackTop, barX + barWidth, trackBottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
    }

    private boolean isOnScrollbar(double mouseX, double mouseY) {
        int barWidth = 8;
        int barX = scrollbarX - 1;
        return mouseX >= barX && mouseX <= barX + barWidth && mouseY >= panelTop && mouseY <= panelBottom;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            scroll = 0;
            return;
        }
        int trackTop = panelTop;
        int trackBottom = panelBottom;
        int trackHeight = trackBottom - trackTop;
        int visibleHeight = panelBottom - panelTop;
        int totalHeight = visibleHeight + maxScroll;
        int barHeight = Math.max(20, trackHeight * visibleHeight / Math.max(1, totalHeight));
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int target = (int) (mouseY - trackTop - barHeight / 2.0);
        target = Math.max(0, Math.min(maxTrack, target));
        scroll = Math.max(0, Math.min(maxScroll, target * maxScroll / Math.max(1, maxTrack)));
        updateButtonPositions();
    }

    private void drawSeparators(GuiGraphics guiGraphics) {
        int left = panelLeft;
        int right = scrollbarX - 6;
        for (Entry entry : entries) {
            if (!entry.separator || !entry.visible) {
                continue;
            }
            int y = entry.baseY - scroll;
            String text = Component.translatable(entry.labelKey).getString();
            int textWidth = this.font.width(text);
            int textX = Math.max(left + 6, (left + right - textWidth) / 2);
            int lineY = y + buttonHeight / 2;
            int leftLineEnd = Math.max(left, textX - 6);
            int rightLineStart = Math.min(right, textX + textWidth + 6);
            if (leftLineEnd > left) {
                guiGraphics.fill(left, lineY, leftLineEnd, lineY + 1, 0xFF3B3B3B);
            }
            if (right > rightLineStart) {
                guiGraphics.fill(rightLineStart, lineY, right, lineY + 1, 0xFF3B3B3B);
            }
            guiGraphics.drawString(this.font, text, textX, y + 4, 0xC8C8C8, false);
        }
    }

    private void addCommand(String labelKey, String command) {
        entries.add(Entry.command(labelKey, command));
    }

    private void addSeparator(String labelKey) {
        entries.add(Entry.separator(labelKey));
    }

    private static class Entry {
        private final boolean separator;
        private final String labelKey;
        private final String command;
        private Button button;
        private int baseY;
        private boolean visible;

        private Entry(boolean separator, String labelKey, String command) {
            this.separator = separator;
            this.labelKey = labelKey;
            this.command = command;
        }

        private static Entry command(String labelKey, String command) {
            return new Entry(false, labelKey, command);
        }

        private static Entry separator(String labelKey) {
            return new Entry(true, labelKey, "");
        }
    }
}
