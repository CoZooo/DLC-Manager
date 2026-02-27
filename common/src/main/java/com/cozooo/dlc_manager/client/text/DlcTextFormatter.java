package com.cozooo.dlc_manager.client.text;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class DlcTextFormatter {
    private DlcTextFormatter() {
    }

    public static Component parse(String text) {
        return parse(text, Style.EMPTY);
    }

    public static Component parse(String text, Style baseStyle) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        MutableComponent result = Component.literal("");
        StringBuilder current = new StringBuilder();
        TextColor currentColor = null;
        boolean extraBold = false;
        boolean extraItalic = false;
        boolean extraUnderline = false;
        boolean extraStrikethrough = false;
        boolean extraObfuscated = false;
        boolean inheritBaseStyle = true;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '§' || ch == '&') && i + 1 < text.length()) {
                char marker = text.charAt(i + 1);
                if (marker == '#' && i + 7 < text.length() && isHexColor(text, i + 2)) {
                    appendCurrent(result, current, baseStyle, inheritBaseStyle, currentColor,
                        extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated);
                    int rgb = Integer.parseInt(text.substring(i + 2, i + 8), 16);
                    currentColor = TextColor.fromRgb(rgb);
                    extraBold = false;
                    extraItalic = false;
                    extraUnderline = false;
                    extraStrikethrough = false;
                    extraObfuscated = false;
                    i += 7;
                    continue;
                }
                ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(marker));
                if (formatting != null) {
                    appendCurrent(result, current, baseStyle, inheritBaseStyle, currentColor,
                        extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated);
                    if (formatting == ChatFormatting.RESET) {
                        inheritBaseStyle = false;
                        currentColor = null;
                        extraBold = false;
                        extraItalic = false;
                        extraUnderline = false;
                        extraStrikethrough = false;
                        extraObfuscated = false;
                    } else if (formatting.isColor()) {
                        Integer rgb = formatting.getColor();
                        currentColor = rgb == null ? null : TextColor.fromRgb(rgb);
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
        appendCurrent(result, current, baseStyle, inheritBaseStyle, currentColor,
            extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated);
        return result;
    }

    public static List<FormattedCharSequence> splitMultiline(Font font, String text, int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (font == null || text == null || text.isBlank()) {
            return lines;
        }
        for (String line : text.split("\\R", -1)) {
            if (line.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(font.split(parse(line), Math.max(1, maxWidth)));
            }
        }
        return lines;
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

    private static void appendCurrent(MutableComponent out,
                                      StringBuilder current,
                                      Style baseStyle,
                                      boolean inheritBaseStyle,
                                      TextColor currentColor,
                                      boolean extraBold,
                                      boolean extraItalic,
                                      boolean extraUnderline,
                                      boolean extraStrikethrough,
                                      boolean extraObfuscated) {
        if (current.length() == 0) {
            return;
        }
        Style style = inheritBaseStyle ? baseStyle : Style.EMPTY;
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
        out.append(Component.literal(current.toString()).withStyle(style));
        current.setLength(0);
    }
}
