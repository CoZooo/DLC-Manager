package com.cozooo.dlc_manager.client.text

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence

object DlcTextFormatter {
    @JvmStatic
    fun parse(text: String?): Component {
        return parse(text, Style.EMPTY)
    }

    @JvmStatic
    fun parse(text: String?, baseStyle: Style): Component {
        if (text.isNullOrEmpty()) {
            return Component.empty()
        }
        val result = Component.literal("")
        val current = StringBuilder()
        var currentColor: TextColor? = null
        var extraBold = false
        var extraItalic = false
        var extraUnderline = false
        var extraStrikethrough = false
        var extraObfuscated = false
        var inheritBaseStyle = true

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if ((ch == '§' || ch == '&') && i + 1 < text.length) {
                val marker = text[i + 1]
                if (marker == '#' && i + 7 < text.length && isHexColor(text, i + 2)) {
                    appendCurrent(result, current, baseStyle, inheritBaseStyle, currentColor,
                        extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated)
                    val rgb = text.substring(i + 2, i + 8).toInt(16)
                    currentColor = TextColor.fromRgb(rgb)
                    extraBold = false
                    extraItalic = false
                    extraUnderline = false
                    extraStrikethrough = false
                    extraObfuscated = false
                    i += 8
                    continue
                }
                val formatting = ChatFormatting.getByCode(marker.lowercaseChar())
                if (formatting != null) {
                    appendCurrent(result, current, baseStyle, inheritBaseStyle, currentColor,
                        extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated)
                    if (formatting == ChatFormatting.RESET) {
                        inheritBaseStyle = false
                        currentColor = null
                        extraBold = false
                        extraItalic = false
                        extraUnderline = false
                        extraStrikethrough = false
                        extraObfuscated = false
                    } else if (formatting.isColor) {
                        val rgb = formatting.color
                        currentColor = if (rgb == null) null else TextColor.fromRgb(rgb)
                        extraBold = false
                        extraItalic = false
                        extraUnderline = false
                        extraStrikethrough = false
                        extraObfuscated = false
                    } else if (formatting == ChatFormatting.BOLD) {
                        extraBold = true
                    } else if (formatting == ChatFormatting.ITALIC) {
                        extraItalic = true
                    } else if (formatting == ChatFormatting.UNDERLINE) {
                        extraUnderline = true
                    } else if (formatting == ChatFormatting.STRIKETHROUGH) {
                        extraStrikethrough = true
                    } else if (formatting == ChatFormatting.OBFUSCATED) {
                        extraObfuscated = true
                    }
                    i += 2
                    continue
                }
            }
            current.append(ch)
            i++
        }
        appendCurrent(result, current, baseStyle, inheritBaseStyle, currentColor,
            extraBold, extraItalic, extraUnderline, extraStrikethrough, extraObfuscated)
        return result
    }

    @JvmStatic
    fun splitMultiline(font: Font?, text: String?, maxWidth: Int): List<FormattedCharSequence> {
        val lines = ArrayList<FormattedCharSequence>()
        if (font == null || text.isNullOrBlank()) {
            return lines
        }
        for (line in text.split(Regex("\\R"), 0)) {
            if (line.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            } else {
                lines.addAll(font.split(parse(line), maxOf(1, maxWidth)))
            }
        }
        return lines
    }

    private fun isHexColor(text: String, start: Int): Boolean {
        if (start + 6 > text.length) {
            return false
        }
        for (i in start until start + 6) {
            val c = text[i]
            val hex = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            if (!hex) {
                return false
            }
        }
        return true
    }

    private fun appendCurrent(
        out: MutableComponent,
        current: StringBuilder,
        baseStyle: Style,
        inheritBaseStyle: Boolean,
        currentColor: TextColor?,
        extraBold: Boolean,
        extraItalic: Boolean,
        extraUnderline: Boolean,
        extraStrikethrough: Boolean,
        extraObfuscated: Boolean,
    ) {
        if (current.isEmpty()) {
            return
        }
        var style = if (inheritBaseStyle) baseStyle else Style.EMPTY
        if (currentColor != null) {
            style = style.withColor(currentColor)
        }
        if (extraBold) {
            style = style.withBold(true)
        }
        if (extraItalic) {
            style = style.withItalic(true)
        }
        if (extraUnderline) {
            style = style.withUnderlined(true)
        }
        if (extraStrikethrough) {
            style = style.withStrikethrough(true)
        }
        if (extraObfuscated) {
            style = style.withObfuscated(true)
        }
        out.append(Component.literal(current.toString()).withStyle(style))
        current.setLength(0)
    }
}
