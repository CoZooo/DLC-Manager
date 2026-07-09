package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class DlcUpdateChangelogScreen(
    private val parent: Screen,
    changelog: String?,
    private val onDone: Runnable?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.update.changelog_title")) {
    private val changelog: String = changelog ?: ""
    private var okButton: Button? = null
    private var lines: List<FormattedCharSequence> = emptyList()
    private var scrollIndex = 0

    override fun init() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val button = Button.builder(Component.translatable("dlc_manager.update.changelog_button")) { finish() }
            .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
            .build()
        okButton = button
        addRenderableWidget(button)
        rebuildLines()
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        rebuildLines()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        var y = 20
        val titleX = (width - font.width(title)) / 2
        guiGraphics.drawString(font, title, titleX, y, 0xFFFFFF, false)
        y += 18
        drawLines(guiGraphics, y)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawLines(guiGraphics: GuiGraphics, startY: Int) {
        val listTop = startY
        val listBottom = height - 60
        val lineHeight = font.lineHeight + 2
        val maxWidth = maxOf(40, width - 48)
        val maxVisible = maxOf(1, (listBottom - listTop) / lineHeight)
        val totalLines = lines.size
        val maxScroll = maxOf(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var y = listTop
        val end = minOf(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until end) {
            val line = lines[i]
            if (DlcMarkdownRenderer.isHrLine(line)) {
                val hrY = y + lineHeight / 2
                guiGraphics.fill(24, hrY, width - 24, hrY + 1, 0xFF5A5A5A.toInt())
                y += lineHeight
                continue
            }
            val scale = DlcMarkdownRenderer.getHeadingScale(font, line, maxWidth)
            val drawHeight = DlcMarkdownRenderer.getLineHeight(font, line, lineHeight, maxWidth)
            if (scale > 1.0f) {
                guiGraphics.pose().pushPose()
                guiGraphics.pose().scale(scale, scale, 1.0f)
                guiGraphics.drawString(font, line, (24 / scale).toInt(), (y / scale).toInt(), 0xE6E6E6, false)
                guiGraphics.pose().popPose()
            } else {
                guiGraphics.drawString(font, line, 24, y, 0xE6E6E6, false)
            }
            y += drawHeight
        }
    }

    private fun rebuildLines() {
        val maxWidth = maxOf(40, width - 48)
        lines = DlcMarkdownRenderer.wrapMarkdown(font, changelog, maxWidth)
        scrollIndex = 0
    }

    private fun finish() {
        if (onDone != null) {
            onDone.run()
            if (Minecraft.getInstance().screen === this) {
                Minecraft.getInstance().setScreen(parent)
            }
        } else {
            Minecraft.getInstance().setScreen(parent)
        }
    }

    override fun onClose() {
        finish()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val buttonWidget = okButton
        if (button == 0 && buttonWidget != null && buttonWidget.isMouseOver(mouseX, mouseY)) {
            finish()
            return true
        }
        if (buttonWidget != null && buttonWidget.mouseClicked(mouseX, mouseY, button)) {
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        val step = if (deltaY > 0) -1 else 1
        scrollIndex = maxOf(0, scrollIndex + step)
        return true
    }
}
