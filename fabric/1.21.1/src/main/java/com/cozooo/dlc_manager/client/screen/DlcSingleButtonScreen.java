package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class DlcSingleButtonScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Component buttonLabel;
    private List<FormattedCharSequence> lines;

    public DlcSingleButtonScreen(Screen parent, Component title, Component message, Component buttonLabel) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.buttonLabel = buttonLabel;
    }

    @Override
    protected void init() {
        int buttonWidth = 140;
        int buttonHeight = 20;
        int x = (this.width - buttonWidth) / 2;
        int y = this.height - 40;
        addRenderableWidget(Button.builder(buttonLabel, button -> minecraft.setScreen(parent))
            .bounds(x, y, buttonWidth, buttonHeight)
            .build());
        if (this.font != null) {
            lines = this.font.split(message, this.width - 40);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        if (lines != null) {
            int y = 50;
            for (FormattedCharSequence line : lines) {
                guiGraphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFFFFFF);
                y += this.font.lineHeight + 2;
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

}
