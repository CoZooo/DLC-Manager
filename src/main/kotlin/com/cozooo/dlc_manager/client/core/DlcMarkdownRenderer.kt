package com.cozooo.dlc_manager.client.core

import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

object DlcMarkdownRenderer {
    private const val HEADING_MARKER = "__dlc_heading__:"
    private const val HR_MARKER = "__dlc_hr__"
    private val MARKDOWN_PARSER: Parser = Parser.builder()
        .extensions(listOf(StrikethroughExtension.create()))
        .build()

    @JvmStatic
    fun wrapMarkdown(font: Font?, text: String?, maxWidth: Int): List<FormattedCharSequence> {
        if (font == null || text.isNullOrBlank()) {
            return emptyList()
        }
        val markdown = renderMarkdownComponent(text)
        return font.split(markdown, maxOf(1, maxWidth))
    }

    @JvmStatic
    fun renderMarkdownComponent(text: String?): Component {
        if (text.isNullOrBlank()) {
            return DlcTextCompat.empty()
        }
        return renderMarkdown(text)
    }

    @JvmStatic
    fun isHrLine(line: FormattedCharSequence?): Boolean {
        if (line == null) {
            return false
        }
        var match = false
        line.accept { _, style, _ ->
            //? if >=26.1 {
            /*if (HR_MARKER == style.insertion) {
                match = true
                false
            } else {
                true
            }*///?}
            //? if <26.1 {
            if (style != null && HR_MARKER == style.insertion) {
                match = true
                false
            } else {
                true
            }
            //?}
        }
        return match
    }

    @JvmStatic
    fun getHeadingScale(font: Font?, line: FormattedCharSequence?, markdownWidth: Int): Float {
        if (font == null || line == null) {
            return 1.0f
        }
        val level = extractHeadingLevel(line)
        if (level <= 0) {
            return 1.0f
        }
        val desired = headingScaleForLevel(level)
        val width = font.width(line)
        if (width <= 0 || markdownWidth <= 0) {
            return desired
        }
        val maxScale = markdownWidth.toFloat() / width.toFloat()
        return minOf(desired, maxScale)
    }

    @JvmStatic
    fun getLineHeight(font: Font?, line: FormattedCharSequence?, lineStep: Int, markdownWidth: Int): Int {
        val scale = getHeadingScale(font, line, markdownWidth)
        return lineStep + Math.round(lineStep * (scale - 1f))
    }

    private fun renderMarkdown(text: String): Component {
        val document = MARKDOWN_PARSER.parse(text)
        val out = DlcTextCompat.literal("")
        var first = true
        var node = document.firstChild
        while (node != null) {
            if (!first) {
                out.append(DlcTextCompat.literal("\n"))
            }
            appendBlock(out, node)
            first = false
            node = node.next
        }
        return out
    }

    private fun appendBlock(out: MutableComponent, node: Node) {
        when (node) {
            is Paragraph -> {
                out.append(renderInlineChildren(node, Style.EMPTY))
                return
            }
            is Heading -> {
                val headingStyle = Style.EMPTY.withBold(true).withInsertion(HEADING_MARKER + node.level)
                out.append(renderInlineChildren(node, headingStyle))
                return
            }
            is BlockQuote -> {
                out.append(DlcTextCompat.literal("> ").withStyle(ChatFormatting.GRAY))
                out.append(renderInlineChildren(node, Style.EMPTY))
                return
            }
            is ThematicBreak -> {
                out.append(
                    DlcTextCompat.literal(" ")
                        .withStyle(Style.EMPTY.withInsertion(HR_MARKER).withColor(ChatFormatting.DARK_GRAY))
                )
                return
            }
            is BulletList -> {
                var first = true
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        if (!first) {
                            out.append(DlcTextCompat.literal("\n"))
                        }
                        out.append(DlcTextCompat.literal("• "))
                        appendListItem(out, item)
                        first = false
                    }
                    item = item.next
                }
                return
            }
            is OrderedList -> {
                var index = node.startNumber
                var first = true
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        if (!first) {
                            out.append(DlcTextCompat.literal("\n"))
                        }
                        out.append(DlcTextCompat.literal("$index. "))
                        appendListItem(out, item)
                        index++
                        first = false
                    }
                    item = item.next
                }
                return
            }
        }
        out.append(renderInlineChildren(node, Style.EMPTY))
    }

    private fun appendListItem(out: MutableComponent, item: Node) {
        var first = true
        var child = item.firstChild
        while (child != null) {
            if (!first) {
                out.append(DlcTextCompat.literal("\n"))
            }
            if (child is Paragraph) {
                out.append(renderInlineChildren(child, Style.EMPTY))
            } else {
                appendBlock(out, child)
            }
            first = false
            child = child.next
        }
    }

    private fun renderInlineChildren(parent: Node, style: Style): MutableComponent {
        val result = DlcTextCompat.literal("")
        var child = parent.firstChild
        while (child != null) {
            result.append(renderInline(child, style))
            child = child.next
        }
        return result
    }

    private fun renderInline(node: Node, style: Style): Component {
        return when (node) {
            is Text -> renderTextWithFormatting(node.literal, style)
            is Emphasis -> renderInlineChildren(node, style.withItalic(true))
            is StrongEmphasis -> renderInlineChildren(node, style.withBold(true))
            is Strikethrough -> renderInlineChildren(node, style.withStrikethrough(true))
            is Link -> {
                var linkStyle = style.withUnderlined(true)
                    .withColor(0x4EA1FF)
                //? if >=26.1 {
                /**///?} else {
                linkStyle = linkStyle.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, node.destination))
                //?}
                renderInlineChildren(node, linkStyle)
            }
            is Code -> {
                val codeStyle = style.withColor(ChatFormatting.GRAY).withItalic(false)
                DlcTextCompat.literal(node.literal).withStyle(codeStyle)
            }
            is SoftLineBreak, is HardLineBreak -> DlcTextCompat.literal("\n").withStyle(style)
            else -> renderInlineChildren(node, style)
        }
    }

    private fun renderTextWithFormatting(text: String?, style: Style): Component {
        if (text.isNullOrEmpty()) {
            return DlcTextCompat.empty()
        }
        val marker = "++"
        var index = text.indexOf(marker)
        if (index < 0) {
            return DlcTextFormatter.parse(text, style)
        }
        val result = DlcTextCompat.literal("")
        var pos = 0
        var underline = false
        while (index >= 0) {
            val chunk = text.substring(pos, index)
            if (chunk.isNotEmpty()) {
                result.append(DlcTextFormatter.parse(chunk, if (underline) style.withUnderlined(true) else style))
            }
            underline = !underline
            pos = index + marker.length
            index = text.indexOf(marker, pos)
        }
        val tail = text.substring(pos)
        if (tail.isNotEmpty()) {
            result.append(DlcTextFormatter.parse(tail, if (underline) style.withUnderlined(true) else style))
        }
        return result
    }

    private fun extractHeadingLevel(line: FormattedCharSequence): Int {
        var level = 0
        line.accept { _, style, _ ->
            //? if >=26.1 {
            /*val marker = style.insertion
            if (marker != null && marker.startsWith(HEADING_MARKER)) {
                val raw = marker.substring(HEADING_MARKER.length)
                level = try {
                    raw.toInt()
                } catch (_: NumberFormatException) {
                    1
                }
                return@accept false
            }*///?}
            //? if <26.1 {
            if (style != null) {
                val marker = style.insertion
                if (marker != null && marker.startsWith(HEADING_MARKER)) {
                    val raw = marker.substring(HEADING_MARKER.length)
                    level = try {
                        raw.toInt()
                    } catch (_: NumberFormatException) {
                        1
                    }
                    return@accept false
                }
            }
            //?}
            true
        }
        return level
    }

    private fun headingScaleForLevel(level: Int): Float {
        return when (level) {
            1 -> 1.35f
            2 -> 1.2f
            3 -> 1.1f
            else -> 1.0f
        }
    }
}
