package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.text.DlcTextFormatter
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class DlcRedirectConfirmScreen(parent: Screen?, url: String?, notice: String?) :
    Screen(Component.translatable("dlc_manager.redirect.title")) {
    private val parent: Screen? = parent
    private val url: String = url ?: ""
    private val notice: String = notice ?: ""
    private var promptLines: List<FormattedCharSequence> = emptyList()
    private var urlLines: List<FormattedCharSequence> = emptyList()
    private var noticeLines: List<FormattedCharSequence> = emptyList()
    private var textTop = 0
    private var contentHeight = 0

    override fun init() {
        buildLines()

        val buttonHeight = 20
        val gap = 8
        val openWidth = maxOf(60, font.width(Component.translatable("dlc_manager.redirect.open")) + 12)
        val copyWidth = maxOf(80, font.width(Component.translatable("dlc_manager.redirect.copy")) + 12)
        val cancelWidth = maxOf(60, font.width(Component.translatable("dlc_manager.confirm_no")) + 12)
        val buttonWidth = maxOf(openWidth, copyWidth, cancelWidth)
        val totalWidth = buttonWidth * 3 + gap * 2
        val startX = (width - totalWidth) / 2
        val y = minOf(height - 28, textTop + contentHeight + 8)

        addRenderableWidget(
            Button.builder(Component.translatable("dlc_manager.redirect.open")) {
                Util.getPlatform().openUri(url)
                Minecraft.getInstance().setScreen(parent)
            }
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("dlc_manager.redirect.copy")) {
                Minecraft.getInstance().keyboardHandler.setClipboard(url)
                Minecraft.getInstance().setScreen(parent)
            }
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("dlc_manager.confirm_no")) {
                Minecraft.getInstance().setScreen(parent)
            }
                .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
                .build()
        )
    }

    private fun buildLines() {
        val maxWidth = minOf(360, width - 40)
        promptLines = font.split(Component.translatable("dlc_manager.redirect.prompt"), maxWidth)
        urlLines = font.split(Component.literal(url), maxWidth)
        noticeLines = DlcTextFormatter.splitMultiline(font, notice, maxWidth)
        val lineHeight = font.lineHeight
        var total = lineHeight + 10 + promptLines.size * (lineHeight + 2) + 8 + urlLines.size * (lineHeight + 2)
        if (noticeLines.isNotEmpty()) {
            total += 8 + noticeLines.size * (lineHeight + 2)
        }
        contentHeight = total
        textTop = maxOf(6, (height - total) / 2 - 14)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        var y = textTop
        val centerX = width / 2
        val header = Component.translatable("dlc_manager.redirect.header").withStyle(ChatFormatting.BOLD)
        val titleScale = 1.1f
        val titleHeight = Math.round(font.lineHeight * titleScale)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f)
        val titleX = (centerX - font.width(header) * titleScale / 2.0f) / titleScale
        val titleY = y / titleScale
        guiGraphics.drawString(font, header, titleX.toInt(), titleY.toInt(), 0xE6E6E6, false)
        guiGraphics.pose().popPose()
        y += titleHeight + 10

        for (line in promptLines) {
            guiGraphics.drawCenteredString(font, line, centerX, y, 0xE6E6E6)
            y += font.lineHeight + 2
        }
        y += 8
        for (line in urlLines) {
            guiGraphics.drawCenteredString(font, line, centerX, y, 0xBFD7FF)
            y += font.lineHeight + 2
        }
        if (noticeLines.isNotEmpty()) {
            y += 8
            for (line in noticeLines) {
                guiGraphics.drawCenteredString(font, line, centerX, y, 0xE6E6E6)
                y += font.lineHeight + 2
            }
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }
}
