package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcUpdateResultScreen extends Screen {
    private final Screen parent;
    private final boolean success;
    private final Component message;

    public DlcUpdateResultScreen(Screen parent, boolean success, Component message) {
        super(Component.translatable("dlc_manager.update.title"));
        this.parent = parent;
        this.success = success;
        this.message = message;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        if (success) {
            Button restartNow = Button.builder(Component.translatable("dlc_manager.restart_now"), button -> Minecraft.getInstance().stop())
                .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
                .build();
            Button restartLater = Button.builder(Component.translatable("dlc_manager.restart_later"), button -> {
                if (parent instanceof DlcUpdateManagerScreen manager) {
                    manager.refreshData();
                }
                Minecraft.getInstance().setScreen(parent);
            }).bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(restartNow);
            addRenderableWidget(restartLater);
        } else {
            Button back = Button.builder(Component.translatable("gui.back"), button -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(back);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        int y = 40;
        int titleX = (this.width - this.font.width(getTitle())) / 2;
        guiGraphics.drawString(this.font, getTitle(), titleX, y, 0xFFFFFF, false);
        y += 20;
        int color = success ? 0x9BE17A : 0xFF6B6B;
        guiGraphics.drawCenteredString(this.font, message, centerX, y, color);
        if (success) {
            y += 14;
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.restart"), centerX, y, 0xC8C8C8);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
