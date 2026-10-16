package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/**
 * Shared in-game visual language for DLC Manager screens.
 *
 * The shapes intentionally stay pixel-aligned so the UI keeps the Minecraft
 * feel while borrowing the darker stone, emerald, and gold treatment from
 * Minecraft Dungeons.
 */
object DlcDungeonsUi {
    private const val BACKDROP = 0xE3080D12.toInt()
    private const val BACKDROP_BAND = 0xFF101B22.toInt()
    private const val PANEL = 0xF018252B.toInt()
    private const val PANEL_INNER = 0xFF132027.toInt()
    private const val PANEL_LINE = 0xFF3B4C55.toInt()
    private const val STONE_LIGHT = 0xFF53636B.toInt()
    private const val STONE_DARK = 0xFF0B1217.toInt()
    private const val GOLD = 0xFFE2B65F.toInt()
    private const val GOLD_SHADOW = 0xFF72521E.toInt()
    private const val EMERALD = 0xFF6FD697.toInt()
    private const val EMERALD_DARK = 0xFF286B4B.toInt()
    private const val CYAN = 0xFF77D4DD.toInt()
    private const val PENDING = 0xFFFFC55D.toInt()
    private const val DANGER = 0xFFE88982.toInt()
    private const val TEXT = 0xFFF4EFDF.toInt()
    private const val MUTED = 0xFFABB9B9.toInt()

    fun renderBackdrop(graphics: GuiGraphics, width: Int, height: Int) {
        graphics.fill(0, 0, width, height, BACKDROP)
        graphics.fill(0, 0, width, 8, BACKDROP_BAND)
        graphics.fill(0, height - 8, width, height, BACKDROP_BAND)

        var x = -8
        var segment = 0
        while (x < width) {
            val segmentWidth = if (segment % 3 == 0) 28 else 20
            graphics.fill(x, 4, x + segmentWidth, 6, STONE_DARK)
            graphics.fill(x + 1, 6, x + segmentWidth - 1, 8, 0xFF14242C.toInt())
            x += segmentWidth + 2
            segment++
        }
    }

    fun drawWorkspace(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        accent: Int = EMERALD,
    ) {
        if (right - left < 12 || bottom - top < 12) {
            return
        }
        drawRoundedFill(graphics, left, top, right, bottom, PANEL, 4)
        drawRoundedOutline(graphics, left, top, right, bottom, PANEL_LINE, 4)
        graphics.fill(left + 5, top + 5, right - 5, top + 7, STONE_DARK)
        graphics.fill(left + 6, top + 7, right - 6, top + 9, PANEL_INNER)
        graphics.fill(left + 10, top + 9, minOf(right - 10, left + 132), top + 11, accent)
        drawStoneCourses(graphics, left + 8, bottom - 8, right - 8, 3)
        drawGoldCorner(graphics, left + 2, top + 2, false, false)
        drawGoldCorner(graphics, right - 10, top + 2, true, false)
        drawGoldCorner(graphics, left + 2, bottom - 10, false, true)
        drawGoldCorner(graphics, right - 10, bottom - 10, true, true)
    }

    fun drawSectionCard(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        accent: Int = CYAN,
    ) {
        if (right - left < 8 || bottom - top < 8) {
            return
        }
        drawRoundedFill(graphics, left, top, right, bottom, PANEL_INNER, 3)
        drawRoundedOutline(graphics, left, top, right, bottom, 0xFF32434C.toInt(), 3)
        graphics.fill(left + 5, top + 4, minOf(right - 5, left + 44), top + 6, accent)
        drawGoldCorner(graphics, right - 8, top + 2, true, false)
    }

    fun drawListRow(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        hovered: Boolean,
        enabled: Boolean = false,
        pending: Boolean = false,
    ) {
        val fill = when {
            pending -> 0xFF3A3022.toInt()
            hovered -> 0xFF263B42.toInt()
            enabled -> 0xFF20352E.toInt()
            else -> 0xFF17262D.toInt()
        }
        val outline = when {
            pending -> PENDING
            hovered -> CYAN
            enabled -> EMERALD_DARK
            else -> 0xFF2B3B44.toInt()
        }
        drawRoundedFill(graphics, left, top, right, bottom, fill, 3)
        drawRoundedOutline(graphics, left, top, right, bottom, outline, 3)
        if (pending || enabled) {
            val barColor = if (pending) PENDING else EMERALD
            graphics.fill(left + 3, top + 3, left + 5, bottom - 3, barColor)
        }
    }

    fun drawSectionHeader(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        accent: Int = GOLD,
    ) {
        graphics.fill(left, top + 8, right, top + 10, 0xFF1D2E35.toInt())
        graphics.fill(left + 4, top + 8, minOf(right - 4, left + 56), top + 10, accent)
        drawGem(graphics, left + 2, top + 4, accent)
    }

    fun drawTitle(
        graphics: GuiGraphics,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        subtitle: String? = null,
    ) {
        drawEmblem(graphics, x, y - 1)
        graphics.drawString(font, text, x + 18, y + 2, TEXT, false)
        if (!subtitle.isNullOrBlank()) {
            graphics.drawString(font, subtitle, x + 18, y + 12, MUTED, false)
        }
    }

    fun drawStatus(
        graphics: GuiGraphics,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        color: Int = EMERALD,
    ) {
        drawGem(graphics, x, y + 1, color)
        graphics.drawString(font, text, x + 8, y, color, false)
    }

    fun drawEmblem(graphics: GuiGraphics, x: Int, y: Int) {
        drawRoundedFill(graphics, x, y, x + 14, y + 14, 0xFF5B4322.toInt(), 3)
        drawRoundedOutline(graphics, x, y, x + 14, y + 14, GOLD, 3)
        graphics.fill(x + 3, y + 3, x + 11, y + 5, 0xFFB88D45.toInt())
        graphics.fill(x + 4, y + 6, x + 10, y + 11, 0xFF775325.toInt())
        drawGem(graphics, x + 5, y + 7, EMERALD)
    }

    fun drawSyncGlyph(graphics: GuiGraphics, x: Int, y: Int, color: Int = CYAN) {
        graphics.fill(x + 2, y, x + 7, y + 2, color)
        graphics.fill(x + 5, y + 2, x + 9, y + 4, color)
        graphics.fill(x + 7, y + 4, x + 10, y + 8, color)
        graphics.fill(x + 3, y + 8, x + 8, y + 10, color)
        graphics.fill(x, y + 4, x + 3, y + 8, color)
    }

    fun drawDangerGlyph(graphics: GuiGraphics, x: Int, y: Int) {
        graphics.fill(x + 4, y, x + 6, y + 7, DANGER)
        graphics.fill(x + 4, y + 9, x + 6, y + 11, DANGER)
    }

    private fun drawRoundedFill(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int,
        radius: Int,
    ) {
        graphics.fill(left + radius, top, right - radius, bottom, color)
        graphics.fill(left, top + radius, right, bottom - radius, color)
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, color)
    }

    private fun drawRoundedOutline(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int,
        radius: Int,
    ) {
        graphics.fill(left + radius, top, right - radius, top + 1, color)
        graphics.fill(left + radius, bottom - 1, right - radius, bottom, color)
        graphics.fill(left, top + radius, left + 1, bottom - radius, color)
        graphics.fill(right - 1, top + radius, right, bottom - radius, color)
        graphics.fill(left + 1, top + 1, left + radius, top + 2, color)
        graphics.fill(right - radius, top + 1, right - 1, top + 2, color)
        graphics.fill(left + 1, bottom - 2, left + radius, bottom - 1, color)
        graphics.fill(right - radius, bottom - 2, right - 1, bottom - 1, color)
    }

    private fun drawGoldCorner(graphics: GuiGraphics, x: Int, y: Int, right: Boolean, bottom: Boolean) {
        val horizontalDirection = if (right) -1 else 1
        val verticalDirection = if (bottom) -1 else 1
        graphics.fill(x, y, x + 6, y + 2, GOLD_SHADOW)
        graphics.fill(x, y, x + 2, y + 6, GOLD_SHADOW)
        graphics.fill(x + horizontalDirection * 2 + if (right) 2 else 0, y, x + horizontalDirection * 4 + if (right) 6 else 0, y + 1, GOLD)
        graphics.fill(x, y + verticalDirection * 2 + if (bottom) 2 else 0, x + 1, y + verticalDirection * 4 + if (bottom) 6 else 0, GOLD)
    }

    private fun drawGem(graphics: GuiGraphics, x: Int, y: Int, color: Int) {
        graphics.fill(x + 1, y, x + 3, y + 1, color)
        graphics.fill(x, y + 1, x + 4, y + 3, color)
        graphics.fill(x + 1, y + 3, x + 3, y + 4, color)
    }

    private fun drawStoneCourses(graphics: GuiGraphics, left: Int, y: Int, right: Int, height: Int) {
        var x = left
        var index = 0
        while (x < right) {
            val blockWidth = if (index % 2 == 0) 18 else 26
            graphics.fill(x, y, minOf(right, x + blockWidth), y + height, if (index % 3 == 0) STONE_DARK else 0xFF17272E.toInt())
            x += blockWidth + 1
            index++
        }
    }
}
