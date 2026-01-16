package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcNoticeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class DlcNoticeScreen extends Screen {
    private final Screen parent;
    private final DlcNoticeConfig config;
    private Checkbox neverShowBox;
    private Button readButton;

    private List<FormattedCharSequence> bodyLines = List.of();
    private List<FormattedCharSequence> subheadLines = List.of();
    private FormattedCharSequence subheadLine;
    private String titleText = "";
    private String subheadText = "";
    private boolean subheadEnabled = false;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int scrollOffset;
    private int maxScroll;
    private int contentTop;

    public DlcNoticeScreen(Screen parent, DlcNoticeConfig config) {
        super(Component.translatable("dlc_manager.notice_title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        layoutText();
        boolean showNeverBox = config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE;
        int checkboxY = panelTop + panelHeight - 34;
        int checkboxX = panelLeft + 14;
        if (showNeverBox) {
            neverShowBox = new Checkbox(checkboxX, checkboxY, 56, 20,
                Component.translatable("dlc_manager.notice.never_show"), config.isNeverShow());
            addRenderableWidget(neverShowBox);
        } else {
            neverShowBox = null;
        }

        int buttonWidth = 80;
        int buttonX = panelLeft + panelWidth - buttonWidth - 14;
        readButton = Button.builder(Component.translatable("dlc_manager.notice.read"), btn -> onClose())
            .bounds(buttonX, checkboxY, buttonWidth, 18).build();
        addRenderableWidget(readButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        layoutText();

        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC101215);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, 0xFF2A2F36);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF2A2F36);

        int titleY = panelTop + 10;
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, titleY, 0xFFFFFF);
        if (subheadEnabled && !subheadLines.isEmpty()) {
            int subheadY = titleY + 12;
            int lineStep = this.font.lineHeight + 2;
            for (FormattedCharSequence line : subheadLines) {
                int subheadWidth = this.font.width(line);
                int subheadX = panelLeft + panelWidth - 14 - subheadWidth;
                int minX = panelLeft + 14;
                if (subheadX < minX) {
                    subheadX = minX;
                }
                guiGraphics.drawString(this.font, line, subheadX, subheadY, 0xBFC7D5, false);
                subheadY += lineStep;
            }
        }

        int textX = panelLeft + 14;
        int textY = contentTop - scrollOffset;

        for (FormattedCharSequence line : bodyLines) {
            if (textY >= panelTop + 18 && textY <= panelTop + panelHeight - 40) {
                guiGraphics.drawString(this.font, line, textX, textY, 0xE6E6E6, false);
            }
            textY += 10;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void layoutText() {
        int margin = 24;
        panelWidth = Math.max(260, this.width - margin * 2);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = margin;
        int contentWidth = panelWidth - 28;
        int footerHeight = 36;

        String lang = Minecraft.getInstance().options.languageCode;
        titleText = config.getTitle().resolve(lang);
        subheadText = config.getSubhead().text.resolve(lang);
        subheadEnabled = config.getSubhead().enabled;
        String contents = config.getContents().resolve(lang);

        subheadLines = subheadEnabled && !subheadText.isBlank()
            ? wrapMultiline(subheadText, Math.max(40, contentWidth))
            : List.of();
        subheadLine = subheadLines.isEmpty() ? null : subheadLines.get(0);
        bodyLines = wrapMultiline(contents, contentWidth);

        int bodyHeight = bodyLines.size() * 10;
        panelHeight = Math.max(160, this.height - margin * 2);

        int titleY = panelTop + 10;
        int subheadHeight = 0;
        if (subheadEnabled && !subheadLines.isEmpty()) {
            subheadHeight = subheadLines.size() * (this.font.lineHeight + 2);
        }
        contentTop = titleY + 18 + subheadHeight;
        int checkboxY = panelTop + panelHeight - 26;
        int visibleHeight = Math.max(10, checkboxY - 8 - contentTop);
        int totalContentHeight = bodyHeight;
        maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private List<FormattedCharSequence> wrapMultiline(String text, int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String line : text.split("\\R", -1)) {
            if (line.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(this.font.split(Component.literal(line), maxWidth));
            }
        }
        return lines;
    }

    @Override
    public void onClose() {
        if (neverShowBox != null) {
            config.setNeverShow(neverShowBox.selected());
            config.save();
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int step = 10;
        scrollOffset -= (int) (delta * step);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        return true;
    }
}
