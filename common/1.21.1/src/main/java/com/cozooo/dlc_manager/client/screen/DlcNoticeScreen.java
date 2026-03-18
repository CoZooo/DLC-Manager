package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcNoticeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
    private int contentBottom;
    private int visibleHeight;
    private int totalContentHeight;
    private int lineStep;
    private int markdownWidth;
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarBottom;
    private int scrollbarHeight;
    private boolean draggingScrollbar;
    private int dragStartY;
    private int dragStartScroll;
    private String contentLang = "";
    private String resolvedContents = "";
    private boolean loadingRemoteContent;
    private java.util.concurrent.CompletableFuture<String> contentFuture;

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
            neverShowBox = Checkbox.builder(Component.translatable("dlc_manager.notice.never_show"), this.font)
                .pos(checkboxX, checkboxY)
                .selected(config.isNeverShow())
                .build();
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
    public void tick() {
        super.tick();
        String lang = Minecraft.getInstance().options.languageCode;
        if (!java.util.Objects.equals(lang, contentLang)) {
            beginContentResolve(lang);
            layoutText();
            return;
        }
        if (loadingRemoteContent && contentFuture != null && contentFuture.isDone()) {
            String fallback = config.getContents().resolve(contentLang);
            try {
                String loaded = contentFuture.getNow(fallback);
                resolvedContents = (loaded == null || loaded.isBlank()) ? fallback : loaded;
            } catch (Exception ignored) {
                resolvedContents = fallback;
            }
            loadingRemoteContent = false;
            contentFuture = null;
            layoutText();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        layoutText();

        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC101215);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, 0xFF2A2F36);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF2A2F36);

        int titleY = panelTop + 10;
        guiGraphics.drawCenteredString(this.font, DlcTextFormatter.parse(titleText), this.width / 2, titleY, 0xFFFFFF);
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
        guiGraphics.enableScissor(panelLeft + 2, contentTop, panelLeft + panelWidth - 2, contentBottom);
        try {
            for (FormattedCharSequence line : bodyLines) {
                if (DlcMarkdownRenderer.isHrLine(line)) {
                    int y = textY + lineStep / 2;
                    if (y >= contentTop && y <= contentBottom - 1) {
                        int x2 = panelLeft + panelWidth - 14;
                        guiGraphics.fill(textX, y, x2, y + 1, 0xFF5A5A5A);
                    }
                    textY += lineStep;
                    continue;
                }
                float scale = DlcMarkdownRenderer.getHeadingScale(this.font, line, markdownWidth);
                int drawHeight = DlcMarkdownRenderer.getLineHeight(this.font, line, lineStep, markdownWidth);
                if (textY >= contentTop && textY <= contentBottom - drawHeight + 1) {
                    if (scale > 1.0f) {
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().scale(scale, scale, 1.0f);
                        guiGraphics.drawString(this.font, line,
                            (int) (textX / scale),
                            (int) (textY / scale),
                            0xFFFFFF, false);
                        guiGraphics.pose().popPose();
                    } else {
                        guiGraphics.drawString(this.font, line, textX, textY, 0xE6E6E6, false);
                    }
                }
                textY += drawHeight;
            }
        } finally {
            guiGraphics.disableScissor();
        }

        drawScrollBar(guiGraphics);

        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void layoutText() {
        int minDim = Math.min(this.width, this.height);
        int margin = Math.max(12, Math.min(20, (int) (minDim * 0.03f)));
        panelWidth = Math.max(260, this.width - margin * 2);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = margin;
        int contentWidth = panelWidth - 28;
        int footerHeight = 36;

        String lang = Minecraft.getInstance().options.languageCode;
        if (!java.util.Objects.equals(lang, contentLang)) {
            beginContentResolve(lang);
        }
        titleText = config.getTitle().resolve(lang);
        subheadText = config.getSubhead().text.resolve(lang);
        subheadEnabled = config.getSubhead().enabled;
        String contents = resolvedContents;
        if (contents == null || contents.isBlank()) {
            contents = loadingRemoteContent
                ? Component.translatable("dlc_manager.notice.loading").getString()
                : config.getContents().resolve(lang);
        }

        subheadLines = subheadEnabled && !subheadText.isBlank()
            ? wrapMultiline(subheadText, Math.max(40, contentWidth))
            : List.of();
        subheadLine = subheadLines.isEmpty() ? null : subheadLines.get(0);
        bodyLines = wrapMarkdown(contents, contentWidth);

        lineStep = this.font.lineHeight + 2;
        int bodyHeight = bodyLines.size() * lineStep;
        int headingExtra = countHeadingExtra();
        panelHeight = Math.max(160, this.height - margin * 2);

        int titleY = panelTop + 10;
        int subheadHeight = 0;
        if (subheadEnabled && !subheadLines.isEmpty()) {
            subheadHeight = subheadLines.size() * lineStep;
        }
        contentTop = titleY + 18 + subheadHeight + 4;
        int checkboxY = panelTop + panelHeight - 26;
        contentBottom = checkboxY - 8;
        scrollbarTop = contentTop;
        scrollbarBottom = contentBottom;
        scrollbarX = panelLeft + panelWidth - 10;
        visibleHeight = Math.max(10, contentBottom - contentTop);
        totalContentHeight = bodyHeight + headingExtra;
        maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void beginContentResolve(String lang) {
        contentLang = lang == null ? "" : lang;
        String fallback = config.getContents().resolve(contentLang);
        String url = config.getContentUrl(contentLang);
        if (url == null || url.isBlank()) {
            resolvedContents = fallback;
            loadingRemoteContent = false;
            contentFuture = null;
            return;
        }
        resolvedContents = Component.translatable("dlc_manager.notice.loading").getString();
        loadingRemoteContent = true;
        final String langKey = contentLang;
        final String fallbackText = fallback;
        contentFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            String remote = config.resolveContents(langKey);
            return (remote == null || remote.isBlank()) ? fallbackText : remote;
        });
    }

    private List<FormattedCharSequence> wrapMultiline(String text, int maxWidth) {
        return DlcTextFormatter.splitMultiline(this.font, text, maxWidth);
    }

    private List<FormattedCharSequence> wrapMarkdown(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        markdownWidth = maxWidth;
        return DlcMarkdownRenderer.wrapMarkdown(this.font, text, maxWidth);
    }

    private int countHeadingExtra() {
        int extra = 0;
        for (FormattedCharSequence line : bodyLines) {
            int drawHeight = DlcMarkdownRenderer.getLineHeight(this.font, line, lineStep, markdownWidth);
            if (drawHeight > lineStep) {
                extra += drawHeight - lineStep;
            }
        }
        return extra;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        scrollOffset -= (int) (deltaY * lineStep);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverContent(mouseX, mouseY)) {
            int textX = panelLeft + 14;
            int textY = contentTop - scrollOffset;
            for (FormattedCharSequence line : bodyLines) {
                float scale = DlcMarkdownRenderer.getHeadingScale(this.font, line, markdownWidth);
                int lineHeight = DlcMarkdownRenderer.getLineHeight(this.font, line, lineStep, markdownWidth);
                if (mouseY >= textY && mouseY < textY + lineHeight) {
                    int relX = (int) mouseX - textX;
                    int maxWidth = Math.round(this.font.width(line) * scale);
                    if (relX >= 0 && relX <= maxWidth) {
                        int styleX = scale > 1.0f ? (int) (relX / scale) : relX;
                        Style style = findStyleAt(line, styleX);
                        if (style != null && handleComponentClicked(style)) {
                            return true;
                        }
                    }
                    break;
                }
                textY += lineHeight;
            }
        }
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            updateScrollFromDrag(mouseY);
            draggingScrollbar = true;
            dragStartY = (int) mouseY;
            dragStartScroll = scrollOffset;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            int trackHeight = Math.max(1, (scrollbarBottom - scrollbarTop) - scrollbarHeight);
            int delta = (int) mouseY - dragStartY;
            int next = dragStartScroll + (int) Math.round(delta * (maxScroll / (double) trackHeight));
            scrollOffset = Math.max(0, Math.min(maxScroll, next));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverContent(double mouseX, double mouseY) {
        return mouseX >= panelLeft + 14 && mouseX <= panelLeft + panelWidth - 14
            && mouseY >= contentTop && mouseY <= contentBottom;
    }

    private Style findStyleAt(FormattedCharSequence sequence, int x) {
        final int[] width = {0};
        final Style[] result = {null};
        sequence.accept((index, style, codePoint) -> {
            if (result[0] != null) {
                return false;
            }
            String text = new String(Character.toChars(codePoint));
            int w = this.font.width(text);
            if (width[0] + w > x) {
                result[0] = style;
                return false;
            }
            width[0] += w;
            return true;
        });
        return result[0];
    }

    private void drawScrollBar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0 || totalContentHeight <= 0) {
            return;
        }
        int trackHeight = contentBottom - contentTop;
        if (trackHeight <= 0) {
            return;
        }
        scrollbarX = panelLeft + panelWidth - 10;
        scrollbarTop = contentTop;
        scrollbarBottom = contentBottom;
        int barWidth = 4;
        scrollbarHeight = Math.max(12, trackHeight * visibleHeight / Math.max(1, totalContentHeight));
        int maxTrack = Math.max(1, trackHeight - scrollbarHeight);
        int barY = scrollbarTop + (int) ((scrollOffset / (float) maxScroll) * maxTrack);
        guiGraphics.fill(scrollbarX, scrollbarTop, scrollbarX + barWidth, scrollbarBottom, 0xFF2B2B2B);
        guiGraphics.fill(scrollbarX, barY, scrollbarX + barWidth, barY + scrollbarHeight, 0xFF6B6B6B);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (maxScroll <= 0) {
            return false;
        }
        int barWidth = 4;
        int barX = panelLeft + panelWidth - 10;
        return mouseX >= barX - 2 && mouseX <= barX + barWidth + 2
            && mouseY >= scrollbarTop && mouseY <= scrollbarBottom;
    }

    private void updateScrollFromDrag(double mouseY) {
        int trackHeight = contentBottom - contentTop;
        if (trackHeight <= 0 || totalContentHeight <= 0) {
            return;
        }
        int barHeight = Math.max(12, trackHeight * visibleHeight / Math.max(1, totalContentHeight));
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barCenter = (int) mouseY - contentTop - (barHeight / 2);
        int next = (int) Math.round((barCenter / (double) maxTrack) * maxScroll);
        scrollOffset = Math.max(0, Math.min(maxScroll, next));
    }
}
