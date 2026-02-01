package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcNoticeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;

import java.util.ArrayList;
import java.util.List;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

public class DlcNoticeScreen extends Screen {
    private static final String HEADING_MARKER = "__dlc_heading__:";
    private static final String HR_MARKER = "__dlc_hr__";
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
    private static final Parser MARKDOWN_PARSER = Parser.builder()
        .extensions(List.of(StrikethroughExtension.create()))
        .build();

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
            if (isHrLine(line)) {
                int y = textY + lineStep / 2;
                int x2 = panelLeft + panelWidth - 14;
                guiGraphics.fill(textX, y, x2, y + 1, 0xFF5A5A5A);
                textY += lineStep;
                continue;
            }
            float scale = getHeadingScale(line);
            int drawHeight = getLineHeight(line, scale);
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
        titleText = config.getTitle().resolve(lang);
        subheadText = config.getSubhead().text.resolve(lang);
        subheadEnabled = config.getSubhead().enabled;
        String contents = config.getContents().resolve(lang);

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

    private List<FormattedCharSequence> wrapMarkdown(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        markdownWidth = maxWidth;
        Component markdown = renderMarkdown(text);
        return this.font.split(markdown, maxWidth);
    }

    private Component renderMarkdown(String text) {
        Node document = MARKDOWN_PARSER.parse(text);
        MutableComponent out = Component.literal("");
        boolean first = true;
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (!first) {
                out.append(Component.literal("\n"));
            }
            appendBlock(out, node);
            first = false;
        }
        return out;
    }

    private void appendBlock(MutableComponent out, Node node) {
        if (node instanceof Paragraph) {
            out.append(renderInlineChildren(node, Style.EMPTY));
            return;
        }
        if (node instanceof Heading heading) {
            Style headingStyle = Style.EMPTY.withBold(true).withInsertion(HEADING_MARKER + heading.getLevel());
            out.append(renderInlineChildren(heading, headingStyle));
            return;
        }
        if (node instanceof BlockQuote) {
            out.append(Component.literal("> ").withStyle(ChatFormatting.GRAY));
            out.append(renderInlineChildren(node, Style.EMPTY));
            return;
        }
        if (node instanceof ThematicBreak) {
            out.append(Component.literal(" ").withStyle(Style.EMPTY.withInsertion(HR_MARKER).withColor(ChatFormatting.DARK_GRAY)));
            return;
        }
        if (node instanceof BulletList) {
            boolean first = true;
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem)) {
                    continue;
                }
                if (!first) {
                    out.append(Component.literal("\n"));
                }
                out.append(Component.literal("• "));
                appendListItem(out, item);
                first = false;
            }
            return;
        }
        if (node instanceof OrderedList ordered) {
            int index = ordered.getStartNumber();
            boolean first = true;
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem)) {
                    continue;
                }
                if (!first) {
                    out.append(Component.literal("\n"));
                }
                out.append(Component.literal(index + ". "));
                appendListItem(out, item);
                index++;
                first = false;
            }
            return;
        }
        out.append(renderInlineChildren(node, Style.EMPTY));
    }

    private void appendListItem(MutableComponent out, Node item) {
        boolean first = true;
        for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
            if (!first) {
                out.append(Component.literal("\n"));
            }
            if (child instanceof Paragraph) {
                out.append(renderInlineChildren(child, Style.EMPTY));
            } else {
                appendBlock(out, child);
            }
            first = false;
        }
    }

    private MutableComponent renderInlineChildren(Node parent, Style style) {
        MutableComponent result = Component.literal("");
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            result.append(renderInline(child, style));
        }
        return result;
    }

    private Component renderInline(Node node, Style style) {
        if (node instanceof Text text) {
            return renderTextWithFormatting(text.getLiteral(), style);
        }
        if (node instanceof Emphasis) {
            return renderInlineChildren(node, style.withItalic(true));
        }
        if (node instanceof StrongEmphasis) {
            return renderInlineChildren(node, style.withBold(true));
        }
        if (node instanceof Strikethrough) {
            return renderInlineChildren(node, style.withStrikethrough(true));
        }
        if (node instanceof Link link) {
            Style linkStyle = style.withUnderlined(true)
                .withColor(0x4EA1FF)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link.getDestination()));
            return renderInlineChildren(link, linkStyle);
        }
        if (node instanceof Code code) {
            Style codeStyle = style.withColor(ChatFormatting.GRAY).withItalic(false);
            return Component.literal(code.getLiteral()).withStyle(codeStyle);
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            return Component.literal("\n").withStyle(style);
        }
        return renderInlineChildren(node, style);
    }

    private Component renderTextWithFormatting(String text, Style style) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String marker = "++";
        int index = text.indexOf(marker);
        if (index < 0) {
            return renderLegacyCodes(text, style, false);
        }
        MutableComponent result = Component.literal("");
        int pos = 0;
        boolean underline = false;
        while (index >= 0) {
            String chunk = text.substring(pos, index);
            if (!chunk.isEmpty()) {
                result.append(renderLegacyCodes(chunk, style, underline));
            }
            underline = !underline;
            pos = index + marker.length();
            index = text.indexOf(marker, pos);
        }
        String tail = text.substring(pos);
        if (!tail.isEmpty()) {
            result.append(renderLegacyCodes(tail, style, underline));
        }
        return result;
    }

    private Component renderLegacyCodes(String text, Style baseStyle, boolean underlineFlag) {
        MutableComponent result = Component.literal("");
        StringBuilder current = new StringBuilder();
        ChatFormatting currentColor = null;
        boolean extraBold = false;
        boolean extraItalic = false;
        boolean extraUnderline = false;
        boolean extraStrikethrough = false;
        boolean extraObfuscated = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '§' || ch == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting != null) {
                    if (current.length() > 0) {
                        result.append(Component.literal(current.toString())
                            .withStyle(buildStyle(baseStyle, underlineFlag, currentColor,
                                extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated)));
                        current.setLength(0);
                    }
                    if (formatting == ChatFormatting.RESET) {
                        currentColor = null;
                        extraBold = false;
                        extraItalic = false;
                        extraUnderline = false;
                        extraStrikethrough = false;
                        extraObfuscated = false;
                    } else if (formatting.isColor()) {
                        currentColor = formatting;
                        extraBold = false;
                        extraItalic = false;
                        extraUnderline = false;
                        extraStrikethrough = false;
                        extraObfuscated = false;
                    } else if (formatting == ChatFormatting.BOLD) {
                        extraBold = true;
                    } else if (formatting == ChatFormatting.ITALIC) {
                        extraItalic = true;
                    } else if (formatting == ChatFormatting.UNDERLINE) {
                        extraUnderline = true;
                    } else if (formatting == ChatFormatting.STRIKETHROUGH) {
                        extraStrikethrough = true;
                    } else if (formatting == ChatFormatting.OBFUSCATED) {
                        extraObfuscated = true;
                    }
                    i++;
                    continue;
                }
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            result.append(Component.literal(current.toString())
                .withStyle(buildStyle(baseStyle, underlineFlag, currentColor,
                    extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated)));
        }
        return result;
    }

    private Style buildStyle(Style baseStyle,
                             boolean underlineFlag,
                             ChatFormatting currentColor,
                             boolean extraBold,
                             boolean extraItalic,
                             boolean extraUnderline,
                             boolean extraStrikethrough,
                             boolean extraObfuscated) {
        Style style = baseStyle;
        if (underlineFlag) {
            style = style.withUnderlined(true);
        }
        if (currentColor != null) {
            style = style.withColor(currentColor);
        }
        if (extraBold) {
            style = style.withBold(true);
        }
        if (extraItalic) {
            style = style.withItalic(true);
        }
        if (extraUnderline) {
            style = style.withUnderlined(true);
        }
        if (extraStrikethrough) {
            style = style.withStrikethrough(true);
        }
        if (extraObfuscated) {
            style = style.withObfuscated(true);
        }
        return style;
    }

    private boolean isHrLine(FormattedCharSequence line) {
        final boolean[] match = {false};
        line.accept((index, style, codePoint) -> {
            if (style != null && HR_MARKER.equals(style.getInsertion())) {
                match[0] = true;
                return false;
            }
            return true;
        });
        return match[0];
    }

    private int extractHeadingLevel(FormattedCharSequence line) {
        final int[] level = {0};
        line.accept((index, style, codePoint) -> {
            if (style != null) {
                String marker = style.getInsertion();
                if (marker != null && marker.startsWith(HEADING_MARKER)) {
                    String raw = marker.substring(HEADING_MARKER.length());
                    try {
                        level[0] = Integer.parseInt(raw);
                    } catch (NumberFormatException ignored) {
                        level[0] = 1;
                    }
                    return false;
                }
            }
            return true;
        });
        return level[0];
    }

    private float headingScaleForLevel(int level) {
        return switch (level) {
            case 1 -> 1.25f;
            case 2 -> 1.15f;
            case 3 -> 1.08f;
            default -> 1.0f;
        };
    }

    private float getHeadingScale(FormattedCharSequence line) {
        int level = extractHeadingLevel(line);
        if (level <= 0) {
            return 1.0f;
        }
        float desired = headingScaleForLevel(level);
        int width = this.font.width(line);
        if (width <= 0 || markdownWidth <= 0) {
            return desired;
        }
        float maxScale = (float) markdownWidth / (float) width;
        return Math.min(desired, maxScale);
    }

    private int getLineHeight(FormattedCharSequence line, float scale) {
        return lineStep + Math.round(lineStep * (scale - 1f));
    }

    private int countHeadingExtra() {
        int extra = 0;
        for (FormattedCharSequence line : bodyLines) {
            float scale = getHeadingScale(line);
            if (scale > 1.0f) {
                extra += Math.round(lineStep * (scale - 1f));
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
                float scale = getHeadingScale(line);
                int lineHeight = getLineHeight(line, scale);
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
