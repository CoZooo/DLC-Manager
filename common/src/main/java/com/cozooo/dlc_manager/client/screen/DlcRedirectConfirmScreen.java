package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class DlcRedirectConfirmScreen extends Screen {
    private final Screen parent;
    private final String url;
    private final String notice;
    private List<FormattedCharSequence> promptLines = List.of();
    private List<FormattedCharSequence> urlLines = List.of();
    private List<FormattedCharSequence> noticeLines = List.of();
    private int textTop;
    private int contentHeight;

    public DlcRedirectConfirmScreen(Screen parent, String url, String notice) {
        super(Component.translatable("dlc_manager.redirect.title"));
        this.parent = parent;
        this.url = url == null ? "" : url;
        this.notice = notice == null ? "" : notice;
    }

    @Override
    protected void init() {
        buildLines();

        int buttonHeight = 20;
        int gap = 8;
        int openWidth = Math.max(60, font.width(Component.translatable("dlc_manager.redirect.open")) + 12);
        int copyWidth = Math.max(80, font.width(Component.translatable("dlc_manager.redirect.copy")) + 12);
        int cancelWidth = Math.max(60, font.width(Component.translatable("dlc_manager.confirm_no")) + 12);
        int buttonWidth = Math.max(openWidth, Math.max(copyWidth, cancelWidth));
        int totalWidth = buttonWidth * 3 + gap * 2;
        int startX = (this.width - totalWidth) / 2;
        int y = Math.min(this.height - 28, textTop + contentHeight + 8);

        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.redirect.open"), btn -> {
                Util.getPlatform().openUri(url);
                Minecraft.getInstance().setScreen(parent);
            })
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.redirect.copy"), btn -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(url);
                Minecraft.getInstance().setScreen(parent);
            })
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.confirm_no"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build());
    }

    private void buildLines() {
        int maxWidth = Math.min(360, this.width - 40);
        promptLines = font.split(Component.translatable("dlc_manager.redirect.prompt"), maxWidth);
        urlLines = font.split(Component.literal(url), maxWidth);
        noticeLines = DlcTextFormatter.splitMultiline(this.font, notice, maxWidth);
        int lh = font.lineHeight;
        int total = lh + 10 + promptLines.size() * (lh + 2) + 8 + urlLines.size() * (lh + 2);
        if (!noticeLines.isEmpty()) {
            total += 8 + noticeLines.size() * (lh + 2);
        }
        this.contentHeight = total;
        this.textTop = Math.max(6, (this.height - total) / 2 - 14);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int y = textTop;
        int centerX = this.width / 2;
        Component header = Component.translatable("dlc_manager.redirect.header").withStyle(net.minecraft.ChatFormatting.BOLD);
        float titleScale = 1.1f;
        int titleHeight = Math.round(font.lineHeight * titleScale);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        float titleX = (centerX - (font.width(header) * titleScale) / 2.0f) / titleScale;
        float titleY = y / titleScale;
        guiGraphics.drawString(this.font, header, (int) titleX, (int) titleY, 0xE6E6E6, false);
        guiGraphics.pose().popPose();
        y += titleHeight + 10;

        for (FormattedCharSequence line : promptLines) {
            guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xE6E6E6);
            y += font.lineHeight + 2;
        }
        y += 8;
        for (FormattedCharSequence line : urlLines) {
            guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xBFD7FF);
            y += font.lineHeight + 2;
        }
        if (!noticeLines.isEmpty()) {
            y += 8;
            for (FormattedCharSequence line : noticeLines) {
                guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xE6E6E6);
                y += font.lineHeight + 2;
            }
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }
}
