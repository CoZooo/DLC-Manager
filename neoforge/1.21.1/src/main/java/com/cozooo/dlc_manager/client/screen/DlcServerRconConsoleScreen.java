package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconConsoleScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final int port;
    private final String password;

    private final List<String> lines = new ArrayList<>();
    private int scroll;

    private EditBox commandField;
    private Button sendButton;
    private Button backButton;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private boolean draggingScrollbar;

    public DlcServerRconConsoleScreen(Screen parent, DlcServerEntry entry, int port, String password) {
        super(Component.translatable("dlc_manager.server.rcon_console"));
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
        listTop = 50;
        int buttonY = this.height - 30;
        listBottom = buttonY - 10;

        int backWidth = 80;
        int sendWidth = 60;
        int gap = 6;
        int backX = margin;
        int sendX = this.width - margin - sendWidth;
        int fieldX = backX + backWidth + gap;
        int fieldWidth = sendX - gap - fieldX;
        if (fieldWidth < 80) {
            fieldWidth = Math.max(80, this.width - margin * 2 - sendWidth - gap);
            fieldX = this.width / 2 - fieldWidth / 2;
            backX = fieldX - backWidth - gap;
        }

        commandField = new EditBox(this.font, fieldX, buttonY, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_command"));
        commandField.setMaxLength(256);
        sendButton = Button.builder(Component.translatable("dlc_manager.server.rcon_send"), btn -> sendCommand())
            .bounds(sendX, buttonY, sendWidth, 20)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, buttonY, backWidth, 20)
            .build();

        addRenderableWidget(commandField);
        addRenderableWidget(sendButton);
        addRenderableWidget(backButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 16, 0xFFFFFF);
        drawConsole(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawConsole(GuiGraphics guiGraphics) {
        guiGraphics.fill(listLeft, listTop, listRight, listBottom, 0x22000000);
        int lineHeight = this.font.lineHeight + 1;
        int maxVisible = Math.max(1, (listBottom - listTop - 4) / lineHeight);
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        int startY = listTop + 2;
        int index = scroll;
        for (int i = 0; i < maxVisible && index + i < lines.size(); i++) {
            String line = lines.get(index + i);
            guiGraphics.drawString(this.font, line, listLeft + 4, startY + i * lineHeight, 0xC8C8C8, false);
        }
        drawScrollbar(guiGraphics, maxVisible, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int lineHeight = this.font.lineHeight + 1;
            int maxVisible = Math.max(1, (listBottom - listTop - 4) / lineHeight);
            int maxScroll = Math.max(0, lines.size() - maxVisible);
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll + (vertical > 0 ? -1 : 1)));
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

    private void drawScrollbar(GuiGraphics guiGraphics, int maxVisible, int maxScroll) {
        int totalLines = lines.size();
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
        int lineHeight = this.font.lineHeight + 1;
        int maxVisible = Math.max(1, (listBottom - listTop - 4) / lineHeight);
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        if (maxScroll <= 0) {
            scroll = 0;
            return;
        }
        int trackTop = listTop + 1;
        int trackBottom = listBottom - 1;
        int trackHeight = trackBottom - trackTop;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, lines.size()));
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int target = (int) (mouseY - trackTop - barHeight / 2.0);
        target = Math.max(0, Math.min(maxTrack, target));
        scroll = Math.max(0, Math.min(maxScroll, target * maxScroll / Math.max(1, maxTrack)));
    }

    private void sendCommand() {
        if (commandField == null) {
            return;
        }
        String command = commandField.getValue().trim();
        if (command.isEmpty()) {
            return;
        }
        commandField.setValue("");
        appendLine("> " + command);
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else {
                    appendResponse(response);
                }
            }));
    }

    private void appendResponse(String response) {
        if (response == null || response.isBlank()) {
            appendLine(Component.translatable("dlc_manager.server.rcon_response", "").getString());
            return;
        }
        String[] parts = response.split("\\r?\\n");
        for (String part : parts) {
            appendLine(part);
        }
    }

    private void appendLine(String line) {
        if (line == null) {
            return;
        }
        appendWrapped(line);
        int lineHeight = this.font.lineHeight + 1;
        int maxVisible = Math.max(1, (listBottom - listTop - 4) / lineHeight);
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        scroll = maxScroll;
    }

    private void appendWrapped(String line) {
        int maxWidth = Math.max(40, (listRight - listLeft - 12));
        if (this.font.width(line) <= maxWidth) {
            lines.add(line);
            return;
        }
        String remaining = line;
        while (!remaining.isEmpty()) {
            int cut = remaining.length();
            while (cut > 0 && this.font.width(remaining.substring(0, cut)) > maxWidth) {
                cut--;
            }
            if (cut <= 0) {
                break;
            }
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        if (remaining.length() > 0) {
            lines.add(remaining);
        }
    }
}
