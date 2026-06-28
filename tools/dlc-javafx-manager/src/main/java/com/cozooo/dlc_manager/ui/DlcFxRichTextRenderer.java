package com.cozooo.dlc_manager.ui;

import javafx.scene.input.MouseButton;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.text.TextFlow;
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
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

public final class DlcFxRichTextRenderer {
    private static final Parser MARKDOWN_PARSER = Parser.builder()
        .extensions(List.of(StrikethroughExtension.create()))
        .build();

    private DlcFxRichTextRenderer() {
    }

    public static TextFlow markdown(String text) {
        TextFlow flow = flow();
        if (text == null || text.isBlank()) {
            return flow;
        }
        org.commonmark.node.Node document = MARKDOWN_PARSER.parse(text);
        boolean first = true;
        for (org.commonmark.node.Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (!first) {
                addPlain(flow, "\n");
            }
            appendBlock(flow, node);
            first = false;
        }
        return flow;
    }

    public static TextFlow formatted(String text) {
        TextFlow flow = flow();
        appendFormatted(flow, text, FxTextStyle.normal());
        return flow;
    }

    public static void setFormatted(Label label, String text) {
        if (label == null) {
            return;
        }
        label.setText(null);
        label.setGraphic(formatted(text));
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    public static void setPlainLabel(Label label, String text) {
        if (label == null) {
            return;
        }
        label.setGraphic(null);
        label.setText(text == null ? "" : text);
        label.setContentDisplay(ContentDisplay.LEFT);
    }

    public static Tooltip formattedTooltip(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Tooltip tooltip = new Tooltip();
        tooltip.setText(null);
        tooltip.setGraphic(formatted(text));
        return tooltip;
    }

    public static Tooltip markdownTooltip(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Tooltip tooltip = new Tooltip();
        tooltip.setText(null);
        tooltip.setGraphic(markdown(text));
        return tooltip;
    }

    private static TextFlow flow() {
        TextFlow flow = new TextFlow();
        DlcFxStyle.add(flow, "dlc-rich-text");
        return flow;
    }

    private static void appendBlock(TextFlow flow, org.commonmark.node.Node node) {
        if (node instanceof Paragraph) {
            appendInlineChildren(flow, node, FxTextStyle.normal());
            return;
        }
        if (node instanceof Heading heading) {
            appendInlineChildren(flow, heading, FxTextStyle.normal().bold(true).headingLevel(heading.getLevel()));
            return;
        }
        if (node instanceof BlockQuote) {
            appendStyledText(flow, "> ", FxTextStyle.normal().color("#667085"));
            appendInlineChildren(flow, node, FxTextStyle.normal());
            return;
        }
        if (node instanceof ThematicBreak) {
            appendStyledText(flow, "----------------", FxTextStyle.normal().color("#667085"));
            return;
        }
        if (node instanceof BulletList) {
            appendList(flow, node, false, 1);
            return;
        }
        if (node instanceof OrderedList ordered) {
            appendList(flow, node, true, ordered.getStartNumber());
            return;
        }
        appendInlineChildren(flow, node, FxTextStyle.normal());
    }

    private static void appendList(TextFlow flow, org.commonmark.node.Node node, boolean ordered, int start) {
        int index = start;
        boolean first = true;
        for (org.commonmark.node.Node item = node.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            if (!first) {
                addPlain(flow, "\n");
            }
            appendStyledText(flow, ordered ? index + ". " : "• ", FxTextStyle.normal());
            appendListItem(flow, item);
            index++;
            first = false;
        }
    }

    private static void appendListItem(TextFlow flow, org.commonmark.node.Node item) {
        boolean first = true;
        for (org.commonmark.node.Node child = item.getFirstChild(); child != null; child = child.getNext()) {
            if (!first) {
                addPlain(flow, "\n");
            }
            if (child instanceof Paragraph) {
                appendInlineChildren(flow, child, FxTextStyle.normal());
            } else {
                appendBlock(flow, child);
            }
            first = false;
        }
    }

    private static void appendInlineChildren(TextFlow flow, org.commonmark.node.Node parent, FxTextStyle style) {
        for (org.commonmark.node.Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            appendInline(flow, child, style);
        }
    }

    private static void appendInline(TextFlow flow, org.commonmark.node.Node node, FxTextStyle style) {
        if (node instanceof Text text) {
            appendMarkdownText(flow, text.getLiteral(), style);
            return;
        }
        if (node instanceof Emphasis) {
            appendInlineChildren(flow, node, style.italic(true));
            return;
        }
        if (node instanceof StrongEmphasis) {
            appendInlineChildren(flow, node, style.bold(true));
            return;
        }
        if (node instanceof Strikethrough) {
            appendInlineChildren(flow, node, style.strikethrough(true));
            return;
        }
        if (node instanceof Link link) {
            FxTextStyle linkStyle = style.underline(true).color("#2563eb").link(link.getDestination());
            appendInlineChildren(flow, node, linkStyle);
            return;
        }
        if (node instanceof Code code) {
            appendStyledText(flow, code.getLiteral(), style.code(true).italic(false).color("#596579"));
            return;
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            appendStyledText(flow, "\n", style);
            return;
        }
        appendInlineChildren(flow, node, style);
    }

    private static void appendMarkdownText(TextFlow flow, String text, FxTextStyle style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int pos = 0;
        boolean underline = false;
        int index = text.indexOf("++");
        while (index >= 0) {
            appendFormatted(flow, text.substring(pos, index), underline ? style.underline(true) : style);
            underline = !underline;
            pos = index + 2;
            index = text.indexOf("++", pos);
        }
        appendFormatted(flow, text.substring(pos), underline ? style.underline(true) : style);
    }

    private static void appendFormatted(TextFlow flow, String text, FxTextStyle baseStyle) {
        if (text == null || text.isEmpty()) {
            return;
        }
        StringBuilder current = new StringBuilder();
        FxTextStyle style = baseStyle;
        boolean inheritBase = true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '§' || ch == '&') && i + 1 < text.length()) {
                char marker = Character.toLowerCase(text.charAt(i + 1));
                if (marker == 'x' && isMinecraftLongHexColor(text, i, ch)) {
                    appendStyledText(flow, current.toString(), style);
                    current.setLength(0);
                    style = (inheritBase ? baseStyle : FxTextStyle.normal()).color("#" + minecraftLongHex(text, i));
                    i += 13;
                    continue;
                }
                if (marker == '#' && i + 7 < text.length() && isHexColor(text, i + 2)) {
                    appendStyledText(flow, current.toString(), style);
                    current.setLength(0);
                    style = (inheritBase ? baseStyle : FxTextStyle.normal()).color("#" + text.substring(i + 2, i + 8));
                    i += 7;
                    continue;
                }
                FxFormat format = FxFormat.byCode(marker);
                if (format != null) {
                    appendStyledText(flow, current.toString(), style);
                    current.setLength(0);
                    if (format.reset) {
                        inheritBase = false;
                        style = FxTextStyle.normal();
                    } else if (format.color != null) {
                        style = (inheritBase ? baseStyle : FxTextStyle.normal()).color(format.color);
                    } else if (format.bold) {
                        style = style.bold(true);
                    } else if (format.italic) {
                        style = style.italic(true);
                    } else if (format.underline) {
                        style = style.underline(true);
                    } else if (format.strikethrough) {
                        style = style.strikethrough(true);
                    }
                    i++;
                    continue;
                }
            }
            current.append(ch);
        }
        appendStyledText(flow, current.toString(), style);
    }

    private static void appendStyledText(TextFlow flow, String value, FxTextStyle style) {
        if (value == null || value.isEmpty()) {
            return;
        }
        javafx.scene.text.Text text = new javafx.scene.text.Text(value);
        DlcFxStyle.add(text, "dlc-rich-text-run");
        if (style.bold) {
            DlcFxStyle.add(text, "bold");
        }
        if (style.italic) {
            DlcFxStyle.add(text, "italic");
        }
        if (style.underline) {
            text.setUnderline(true);
        }
        if (style.strikethrough) {
            text.setStrikethrough(true);
        }
        if (style.code) {
            DlcFxStyle.add(text, "code");
        }
        if (style.headingLevel > 0) {
            DlcFxStyle.add(text, "heading-" + Math.min(style.headingLevel, 3));
        }
        if (style.color != null && !style.color.isBlank()) {
            text.setStyle("-fx-fill: " + style.color + ";");
        }
        if (style.link != null && !style.link.isBlank()) {
            DlcFxStyle.add(text, "link");
            text.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    openLink(style.link);
                }
            });
        }
        flow.getChildren().add(text);
    }

    private static void addPlain(TextFlow flow, String value) {
        appendStyledText(flow, value, FxTextStyle.normal());
    }

    private static boolean isHexColor(String text, int start) {
        if (start + 6 > text.length()) {
            return false;
        }
        for (int i = start; i < start + 6; i++) {
            char c = text.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMinecraftLongHexColor(String text, int start, char prefix) {
        if (start + 13 >= text.length()) {
            return false;
        }
        if (Character.toLowerCase(text.charAt(start + 1)) != 'x') {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            int markerIndex = start + 2 + i * 2;
            int hexIndex = markerIndex + 1;
            if (text.charAt(markerIndex) != prefix || !isHexDigit(text.charAt(hexIndex))) {
                return false;
            }
        }
        return true;
    }

    private static String minecraftLongHex(String text, int start) {
        StringBuilder hex = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            hex.append(text.charAt(start + 3 + i * 2));
        }
        return hex.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
    }

    private static void openLink(String link) {
        if (link == null || link.isBlank() || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(link));
        } catch (Throwable ignored) {
        }
    }

    private record FxTextStyle(boolean bold, boolean italic, boolean underline, boolean strikethrough,
                               boolean code, int headingLevel, String color, String link) {
        private static FxTextStyle normal() {
            return new FxTextStyle(false, false, false, false, false, 0, null, null);
        }

        private FxTextStyle bold(boolean value) {
            return new FxTextStyle(value, italic, underline, strikethrough, code, headingLevel, color, link);
        }

        private FxTextStyle italic(boolean value) {
            return new FxTextStyle(bold, value, underline, strikethrough, code, headingLevel, color, link);
        }

        private FxTextStyle underline(boolean value) {
            return new FxTextStyle(bold, italic, value, strikethrough, code, headingLevel, color, link);
        }

        private FxTextStyle strikethrough(boolean value) {
            return new FxTextStyle(bold, italic, underline, value, code, headingLevel, color, link);
        }

        private FxTextStyle code(boolean value) {
            return new FxTextStyle(bold, italic, underline, strikethrough, value, headingLevel, color, link);
        }

        private FxTextStyle headingLevel(int value) {
            return new FxTextStyle(bold, italic, underline, strikethrough, code, value, color, link);
        }

        private FxTextStyle color(String value) {
            return new FxTextStyle(bold, italic, underline, strikethrough, code, headingLevel, value, link);
        }

        private FxTextStyle link(String value) {
            return new FxTextStyle(bold, italic, underline, strikethrough, code, headingLevel, color, value);
        }
    }

    private record FxFormat(String color, boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean reset) {
        private static FxFormat byCode(char code) {
            return switch (Character.toLowerCase(code)) {
                case '0' -> color("#000000");
                case '1' -> color("#0000AA");
                case '2' -> color("#00AA00");
                case '3' -> color("#00AAAA");
                case '4' -> color("#AA0000");
                case '5' -> color("#AA00AA");
                case '6' -> color("#FFAA00");
                case '7' -> color("#AAAAAA");
                case '8' -> color("#555555");
                case '9' -> color("#5555FF");
                case 'a' -> color("#55FF55");
                case 'b' -> color("#55FFFF");
                case 'c' -> color("#FF5555");
                case 'd' -> color("#FF55FF");
                case 'e' -> color("#FFFF55");
                case 'f' -> color("#FFFFFF");
                case 'l' -> new FxFormat(null, true, false, false, false, false);
                case 'o' -> new FxFormat(null, false, true, false, false, false);
                case 'n' -> new FxFormat(null, false, false, true, false, false);
                case 'm' -> new FxFormat(null, false, false, false, true, false);
                case 'r' -> new FxFormat(null, false, false, false, false, true);
                default -> null;
            };
        }

        private static FxFormat color(String color) {
            return new FxFormat(color, false, false, false, false, false);
        }
    }
}
