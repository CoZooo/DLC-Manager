package com.cozooo.dlc_manager.client.notice;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
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

import java.util.List;

public final class DlcNoticeMarkdownRenderer {
    private static final String HEADING_MARKER = "__dlc_heading__:";
    private static final String HR_MARKER = "__dlc_hr__";
    private static final Parser MARKDOWN_PARSER = Parser.builder()
        .extensions(List.of(StrikethroughExtension.create()))
        .build();

    private DlcNoticeMarkdownRenderer() {
    }

    public static List<FormattedCharSequence> wrapMarkdown(Font font, String text, int maxWidth) {
        if (font == null || text == null || text.isBlank()) {
            return List.of();
        }
        Component markdown = renderMarkdown(text);
        return font.split(markdown, Math.max(1, maxWidth));
    }

    public static boolean isHrLine(FormattedCharSequence line) {
        if (line == null) {
            return false;
        }
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

    public static float getHeadingScale(Font font, FormattedCharSequence line, int markdownWidth) {
        if (font == null || line == null) {
            return 1.0f;
        }
        int level = extractHeadingLevel(line);
        if (level <= 0) {
            return 1.0f;
        }
        float desired = headingScaleForLevel(level);
        int width = font.width(line);
        if (width <= 0 || markdownWidth <= 0) {
            return desired;
        }
        float maxScale = (float) markdownWidth / (float) width;
        return Math.min(desired, maxScale);
    }

    public static int getLineHeight(Font font, FormattedCharSequence line, int lineStep, int markdownWidth) {
        float scale = getHeadingScale(font, line, markdownWidth);
        return lineStep + Math.round(lineStep * (scale - 1f));
    }

    private static Component renderMarkdown(String text) {
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

    private static void appendBlock(MutableComponent out, Node node) {
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

    private static void appendListItem(MutableComponent out, Node item) {
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

    private static MutableComponent renderInlineChildren(Node parent, Style style) {
        MutableComponent result = Component.literal("");
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            result.append(renderInline(child, style));
        }
        return result;
    }

    private static Component renderInline(Node node, Style style) {
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

    private static Component renderTextWithFormatting(String text, Style style) {
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

    private static Component renderLegacyCodes(String text, Style baseStyle, boolean underlineFlag) {
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

    private static Style buildStyle(Style baseStyle,
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

    private static int extractHeadingLevel(FormattedCharSequence line) {
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

    private static float headingScaleForLevel(int level) {
        return switch (level) {
            case 1 -> 1.25f;
            case 2 -> 1.15f;
            case 3 -> 1.08f;
            default -> 1.0f;
        };
    }
}
