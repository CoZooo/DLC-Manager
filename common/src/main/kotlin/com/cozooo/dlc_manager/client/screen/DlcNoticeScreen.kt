package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcNoticeConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.util.Objects
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcNoticeScreen(
    private val parent: Screen?,
    private val config: DlcNoticeConfig,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.notice_title")) {
    private var neverShowBox: Checkbox? = null
    private var readButton: Button? = null

    private var bodyLines: List<FormattedCharSequence> = emptyList()
    private var subheadLines: List<FormattedCharSequence> = emptyList()
    private var subheadLine: FormattedCharSequence? = null
    private var titleText = ""
    private var subheadText = ""
    private var subheadEnabled = false
    private var panelLeft = 0
    private var panelTop = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var scrollOffset = 0
    private var maxScroll = 0
    private var contentTop = 0
    private var contentBottom = 0
    private var visibleHeight = 0
    private var totalContentHeight = 0
    private var lineStep = 0
    private var markdownWidth = 0
    private var scrollbarX = 0
    private var scrollbarTop = 0
    private var scrollbarBottom = 0
    private var scrollbarHeight = 0
    private var draggingScrollbar = false
    private var dragStartY = 0
    private var dragStartScroll = 0
    private var contentLang = ""
    private var resolvedContents = ""
    private var loadingRemoteContent = false
    private var contentFuture: CompletableFuture<String>? = null

    override fun init() {
        layoutText()
        val showNeverBox = config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE
        val checkboxY = panelTop + panelHeight - 34
        val checkboxX = panelLeft + 14
        if (showNeverBox) {
            val checkbox = DlcScreenCompat.createCheckbox(
                font,
                checkboxX,
                checkboxY,
                56,
                20,
                Component.translatable("dlc_manager.notice.never_show"),
                config.isNeverShow(),
            )
            neverShowBox = checkbox
            addRenderableWidget(checkbox)
        } else {
            neverShowBox = null
        }

        val buttonWidth = 80
        val buttonX = panelLeft + panelWidth - buttonWidth - 14
        val read = Button.builder(Component.translatable("dlc_manager.notice.read")) { onClose() }
            .bounds(buttonX, checkboxY, buttonWidth, 18)
            .build()
        readButton = read
        addRenderableWidget(read)
    }

    override fun tick() {
        super.tick()
        val lang = Minecraft.getInstance().options.languageCode
        if (!Objects.equals(lang, contentLang)) {
            beginContentResolve(lang)
            layoutText()
            return
        }
        val future = contentFuture
        if (loadingRemoteContent && future != null && future.isDone) {
            val fallback = config.getContents().resolve(contentLang)
            try {
                val loaded = future.getNow(fallback)
                resolvedContents = if (loaded == null || loaded.isBlank()) fallback else loaded
            } catch (_: Exception) {
                resolvedContents = fallback
            }
            loadingRemoteContent = false
            contentFuture = null
            layoutText()
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        layoutText()

        val panelRight = panelLeft + panelWidth
        val panelBottom = panelTop + panelHeight
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC101215.toInt())
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF2A2F36.toInt())
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF2A2F36.toInt())
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, 0xFF2A2F36.toInt())
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF2A2F36.toInt())

        val titleY = panelTop + 10
        guiGraphics.drawCenteredString(font, DlcTextFormatter.parse(titleText), width / 2, titleY, 0xFFFFFF)
        if (subheadEnabled && subheadLines.isNotEmpty()) {
            var subheadY = titleY + 12
            val subheadStep = font.lineHeight + 2
            for (line in subheadLines) {
                val subheadWidth = font.width(line)
                var subheadX = panelLeft + panelWidth - 14 - subheadWidth
                val minX = panelLeft + 14
                if (subheadX < minX) {
                    subheadX = minX
                }
                guiGraphics.drawString(font, line, subheadX, subheadY, 0xBFC7D5, false)
                subheadY += subheadStep
            }
        }

        val textX = panelLeft + 14
        var textY = contentTop - scrollOffset
        guiGraphics.enableScissor(panelLeft + 2, contentTop, panelLeft + panelWidth - 2, contentBottom)
        try {
            for (line in bodyLines) {
                if (DlcMarkdownRenderer.isHrLine(line)) {
                    val y = textY + lineStep / 2
                    if (y >= contentTop && y <= contentBottom - 1) {
                        val x2 = panelLeft + panelWidth - 14
                        guiGraphics.fill(textX, y, x2, y + 1, 0xFF5A5A5A.toInt())
                    }
                    textY += lineStep
                    continue
                }
                val scale = DlcMarkdownRenderer.getHeadingScale(font, line, markdownWidth)
                val drawHeight = DlcMarkdownRenderer.getLineHeight(font, line, lineStep, markdownWidth)
                if (textY >= contentTop && textY <= contentBottom - drawHeight + 1) {
                    if (scale > 1.0f) {
                        guiGraphics.pose().pushPose()
                        guiGraphics.pose().scale(scale, scale, 1.0f)
                        guiGraphics.drawString(font, line, (textX / scale).toInt(), (textY / scale).toInt(), 0xFFFFFF, false)
                        guiGraphics.pose().popPose()
                    } else {
                        guiGraphics.drawString(font, line, textX, textY, 0xE6E6E6, false)
                    }
                }
                textY += drawHeight
            }
        } finally {
            guiGraphics.disableScissor()
        }

        drawScrollBar(guiGraphics)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun layoutText() {
        val minDim = min(width, height)
        val margin = max(12, min(20, (minDim * 0.03f).toInt()))
        panelWidth = max(260, width - margin * 2)
        panelLeft = (width - panelWidth) / 2
        panelTop = margin
        val contentWidth = panelWidth - 28

        val lang = Minecraft.getInstance().options.languageCode
        if (!Objects.equals(lang, contentLang)) {
            beginContentResolve(lang)
        }
        titleText = config.getTitle().resolve(lang)
        subheadText = config.getSubhead().text.resolve(lang)
        subheadEnabled = config.getSubhead().enabled
        var contents = resolvedContents
        if (contents.isBlank()) {
            contents = if (loadingRemoteContent) {
                Component.translatable("dlc_manager.notice.loading").string
            } else {
                config.getContents().resolve(lang)
            }
        }

        subheadLines = if (subheadEnabled && subheadText.isNotBlank()) {
            wrapMultiline(subheadText, max(40, contentWidth))
        } else {
            emptyList()
        }
        subheadLine = if (subheadLines.isEmpty()) null else subheadLines[0]
        bodyLines = wrapMarkdown(contents, contentWidth)

        lineStep = font.lineHeight + 2
        val bodyHeight = bodyLines.size * lineStep
        val headingExtra = countHeadingExtra()
        panelHeight = max(160, height - margin * 2)

        val titleY = panelTop + 10
        var subheadHeight = 0
        if (subheadEnabled && subheadLines.isNotEmpty()) {
            subheadHeight = subheadLines.size * lineStep
        }
        contentTop = titleY + 18 + subheadHeight + 4
        val checkboxY = panelTop + panelHeight - 26
        contentBottom = checkboxY - 8
        scrollbarTop = contentTop
        scrollbarBottom = contentBottom
        scrollbarX = panelLeft + panelWidth - 10
        visibleHeight = max(10, contentBottom - contentTop)
        totalContentHeight = bodyHeight + headingExtra
        maxScroll = max(0, totalContentHeight - visibleHeight)
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll
        }
    }

    private fun beginContentResolve(lang: String?) {
        contentLang = lang ?: ""
        val fallback = config.getContents().resolve(contentLang)
        val url = config.getContentUrl(contentLang)
        if (url.isBlank()) {
            resolvedContents = fallback
            loadingRemoteContent = false
            contentFuture = null
            return
        }
        resolvedContents = Component.translatable("dlc_manager.notice.loading").string
        loadingRemoteContent = true
        val langKey = contentLang
        val fallbackText = fallback
        contentFuture = CompletableFuture.supplyAsync {
            val remote = config.resolveContents(langKey)
            if (remote.isBlank()) fallbackText else remote
        }
    }

    private fun wrapMultiline(text: String, maxWidth: Int): List<FormattedCharSequence> {
        return DlcTextFormatter.splitMultiline(font, text, maxWidth)
    }

    private fun wrapMarkdown(text: String?, maxWidth: Int): List<FormattedCharSequence> {
        if (text.isNullOrBlank()) {
            return emptyList()
        }
        markdownWidth = maxWidth
        return DlcMarkdownRenderer.wrapMarkdown(font, text, maxWidth)
    }

    private fun countHeadingExtra(): Int {
        var extra = 0
        for (line in bodyLines) {
            val drawHeight = DlcMarkdownRenderer.getLineHeight(font, line, lineStep, markdownWidth)
            if (drawHeight > lineStep) {
                extra += drawHeight - lineStep
            }
        }
        return extra
    }

    override fun onClose() {
        val box = neverShowBox
        if (box != null) {
            config.setNeverShow(box.selected())
            config.save()
        }
        minecraft?.setScreen(parent)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (maxScroll <= 0) {
            return false
        }
        scrollOffset -= (delta * lineStep).toInt()
        if (scrollOffset < 0) {
            scrollOffset = 0
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll
        }
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isOverContent(mouseX, mouseY)) {
            val textX = panelLeft + 14
            var textY = contentTop - scrollOffset
            for (line in bodyLines) {
                val scale = DlcMarkdownRenderer.getHeadingScale(font, line, markdownWidth)
                val lineHeight = DlcMarkdownRenderer.getLineHeight(font, line, lineStep, markdownWidth)
                if (mouseY >= textY && mouseY < textY + lineHeight) {
                    val relX = mouseX.toInt() - textX
                    val maxWidth = (font.width(line) * scale).roundToInt()
                    if (relX >= 0 && relX <= maxWidth) {
                        val styleX = if (scale > 1.0f) (relX / scale).toInt() else relX
                        val style = findStyleAt(line, styleX)
                        if (style != null && handleComponentClicked(style)) {
                            return true
                        }
                    }
                    break
                }
                textY += lineHeight
            }
        }
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            updateScrollFromDrag(mouseY)
            draggingScrollbar = true
            dragStartY = mouseY.toInt()
            dragStartScroll = scrollOffset
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            val trackHeight = max(1, scrollbarBottom - scrollbarTop - scrollbarHeight)
            val delta = mouseY.toInt() - dragStartY
            val next = dragStartScroll + (delta * (maxScroll / trackHeight.toDouble())).roundToInt()
            scrollOffset = max(0, min(maxScroll, next))
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun isOverContent(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= panelLeft + 14 && mouseX <= panelLeft + panelWidth - 14 &&
            mouseY >= contentTop && mouseY <= contentBottom
    }

    private fun findStyleAt(sequence: FormattedCharSequence, x: Int): Style? {
        val width = intArrayOf(0)
        val result = arrayOfNulls<Style>(1)
        sequence.accept { _, style, codePoint ->
            if (result[0] != null) {
                return@accept false
            }
            val text = String(Character.toChars(codePoint))
            val w = font.width(text)
            if (width[0] + w > x) {
                result[0] = style
                return@accept false
            }
            width[0] += w
            true
        }
        return result[0]
    }

    private fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean {
        return maxScroll > 0 &&
            mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + 6 &&
            mouseY >= scrollbarTop && mouseY <= scrollbarBottom
    }

    private fun updateScrollFromDrag(mouseY: Double) {
        if (maxScroll <= 0) {
            scrollOffset = 0
            return
        }
        val trackHeight = max(1, scrollbarBottom - scrollbarTop - scrollbarHeight)
        val knobTop = mouseY.toInt() - scrollbarHeight / 2
        val rel = max(0, min(trackHeight, knobTop - scrollbarTop))
        scrollOffset = (rel * (maxScroll / trackHeight.toDouble())).roundToInt()
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics) {
        if (maxScroll <= 0) {
            return
        }
        val trackHeight = max(1, scrollbarBottom - scrollbarTop)
        scrollbarHeight = max(18, (trackHeight * (visibleHeight / totalContentHeight.toDouble())).toInt())
        if (scrollbarHeight > trackHeight) {
            scrollbarHeight = trackHeight
        }
        val travel = max(1, trackHeight - scrollbarHeight)
        val knobTop = scrollbarTop + (scrollOffset / maxScroll.toDouble() * travel).roundToInt()
        guiGraphics.fill(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, 0x663A3F48)
        guiGraphics.fill(scrollbarX, knobTop, scrollbarX + 4, knobTop + scrollbarHeight, 0xFF8A95A8.toInt())
    }
}
