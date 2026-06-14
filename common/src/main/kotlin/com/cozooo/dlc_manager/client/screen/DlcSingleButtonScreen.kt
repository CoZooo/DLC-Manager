package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class DlcSingleButtonScreen(
    private val parent: Screen?,
    title: Component,
    private val message: Component,
    private val buttonLabel: Component,
) : Screen(title) {
    private var lines: List<FormattedCharSequence>? = null

    override fun init() {
        val buttonWidth = 140
        val buttonHeight = 20
        val x = (width - buttonWidth) / 2
        val y = height - 40
        addRenderableWidget(
            Button.builder(buttonLabel) { minecraft?.setScreen(parent) }
                .bounds(x, y, buttonWidth, buttonHeight)
                .build()
        )
        if (font != null) {
            lines = font.split(message, width - 40)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
        val currentLines = lines
        if (currentLines != null) {
            var y = 50
            for (line in currentLines) {
                guiGraphics.drawCenteredString(font, line, width / 2, y, 0xFFFFFF)
                y += font.lineHeight + 2
            }
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }
}
