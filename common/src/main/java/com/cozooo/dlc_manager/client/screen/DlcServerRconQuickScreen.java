package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.rcon.DlcRconQuickCommandCatalog;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconQuickScreen extends DlcScreenCompat.MouseScroll {
    private final Screen parent;
    private final DlcServerEntry serverEntry;
    private final int port;
    private final String password;

    private final List<QuickEntry> quickEntries = new ArrayList<>();
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

    public DlcServerRconQuickScreen(Screen parent, DlcServerEntry serverEntry, int port, String password) {
        super(Component.translatable("dlc_manager.server.rcon_common"));
        this.parent = parent;
        this.serverEntry = serverEntry;
        this.port = port;
        this.password = password == null ? "" : password;
    }

    @Override
    protected void init() {
        quickEntries.clear();
        for (DlcRconQuickCommandCatalog.QuickCommandItem item : DlcRconQuickCommandCatalog.commandItems()) {
            if (item.separator()) {
                addSeparator(item.labelKey());
            } else {
                addCommand(item.labelKey(), item.command());
            }
        }

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
        for (QuickEntry quickEntry : quickEntries) {
            if (quickEntry.separator) {
                if (col != 0) {
                    y += buttonHeight + buttonGap;
                    col = 0;
                }
                quickEntry.baseY = y;
                y += buttonHeight + buttonGap;
                continue;
            }
            int x = (col == 0) ? x1 : x2;
            quickEntry.button = Button.builder(Component.translatable(quickEntry.labelKey), btn -> runCommand(quickEntry.command))
                .bounds(x, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(quickEntry.button);
            quickEntry.baseY = y;
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
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 16, 0xFFFFFF);
        if (statusText != null && !statusText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, statusText, this.width / 2, this.height - 52, 0xC8C8C8);
        }
        drawSeparators(guiGraphics);
        drawScrollbar(guiGraphics);
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(mouseX, mouseY, deltaY);
    }

    private boolean handleMouseScrolled(double mouseX, double mouseY, double vertical) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom) {
            int maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll + (vertical > 0 ? -1 : 1) * (buttonHeight + buttonGap)));
                updateButtonPositions();
                return true;
            }
        }
        return false;
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
        String host = serverEntry == null ? "" : serverEntry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else {
                    statusText = Component.translatable("dlc_manager.server.rcon_response", response).getString();
                }
            }));
    }

    private void updateButtonPositions() {
        for (QuickEntry quickEntry : quickEntries) {
            int y = quickEntry.baseY - scroll;
            if (quickEntry.separator) {
                quickEntry.visible = y + buttonHeight >= panelTop && y <= panelBottom - 2;
                continue;
            }
            quickEntry.button.setY(y);
            boolean visible = y + buttonHeight >= panelTop && y <= panelBottom - 2;
            quickEntry.button.visible = visible;
            quickEntry.button.active = visible;
        }
    }

    private int getMaxScroll() {
        if (quickEntries.isEmpty()) {
            return 0;
        }
        int totalHeight = 0;
        int col = 0;
        for (QuickEntry quickEntry : quickEntries) {
            if (quickEntry.separator) {
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
        for (QuickEntry quickEntry : quickEntries) {
            if (!quickEntry.separator || !quickEntry.visible) {
                continue;
            }
            int y = quickEntry.baseY - scroll;
            String text = Component.translatable(quickEntry.labelKey).getString();
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
        quickEntries.add(QuickEntry.command(labelKey, command));
    }

    private void addSeparator(String labelKey) {
        quickEntries.add(QuickEntry.separator(labelKey));
    }

    private static class QuickEntry {
        private final boolean separator;
        private final String labelKey;
        private final String command;
        private Button button;
        private int baseY;
        private boolean visible;

        private QuickEntry(boolean separator, String labelKey, String command) {
            this.separator = separator;
            this.labelKey = labelKey;
            this.command = command;
        }

        private static QuickEntry command(String labelKey, String command) {
            return new QuickEntry(false, labelKey, command);
        }

        private static QuickEntry separator(String labelKey) {
            return new QuickEntry(true, labelKey, "");
        }
    }
}
