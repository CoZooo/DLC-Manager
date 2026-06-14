package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.FormattedCharSequence

object DlcTooltipContext {
    private var lines: List<FormattedCharSequence>? = null
    private var mouseX = 0
    private var mouseY = 0

    @JvmStatic
    fun clear() {
        lines = null
    }

    @JvmStatic
    fun set(tooltipLines: List<FormattedCharSequence>?, x: Int, y: Int) {
        if (tooltipLines.isNullOrEmpty()) {
            return
        }
        lines = tooltipLines
        mouseX = x
        mouseY = y
    }

    @JvmStatic
    fun render(guiGraphics: GuiGraphics, font: Font) {
        val currentLines = lines
        if (currentLines.isNullOrEmpty()) {
            return
        }
        guiGraphics.renderTooltip(font, currentLines, mouseX, mouseY)
    }
}
