package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class DlcPresetManagerScreen(private val parent: Screen?) :
    DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.preset.title")) {
    private var presets: List<DlcPresetEntry?> = emptyList()
    private val entryButtons: MutableMap<String, PresetToggleButton> = HashMap()
    private var scrollIndex = 0
    private var descLines: List<String> = emptyList()
    private var backButton: Button? = null

    override fun init() {
        reloadPresets()
        val buttonWidth = 140
        val buttonHeight = 20
        val y = height - 40
        val back = Button.builder(Component.translatable("dlc_manager.back")) { handleBack() }
            .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
            .build()
        backButton = back
        addRenderableWidget(back)
        buildEntryButtons()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        for (line in descLines) {
            guiGraphics.drawCenteredString(font, line, centerX, y, 0xC8C8C8)
            y += font.lineHeight + 2
        }
        y += 10

        val listTop = y
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val totalLines = presets.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }

        var rowY = listTop
        val lang = Minecraft.getInstance().options.languageCode
        for (i in presets.indices) {
            val entry = presets[i] ?: continue
            val button = entryButtons[entry.getId()]
            val visible = i >= scrollIndex && i < scrollIndex + maxVisible
            if (!visible) {
                if (button != null) {
                    button.visible = false
                }
                continue
            }
            if (button != null) {
                button.visible = true
                button.y = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2
                button.x = CHECKBOX_X
                updateButtonState(button, entry)
            }
            val blocked = DlcPresetRuntimeState.isPresetBlocked(entry.getId())
            val color = if (blocked) 0x888888 else 0xE6E6E6
            val labelY = rowY + (ROW_HEIGHT - font.lineHeight) / 2
            guiGraphics.drawString(
                font,
                DlcTextFormatter.parse(entry.resolveDisplayName(lang)),
                CHECKBOX_X + CHECKBOX_SIZE + LABEL_GAP,
                labelY,
                color,
                false,
            )
            if (isMouseOverRow(mouseX, mouseY, rowY, ROW_HEIGHT) && !isMouseOverButton(button, mouseX, mouseY)) {
                renderTooltip(guiGraphics, entry, mouseX, mouseY, lang)
            }
            rowY += ROW_HEIGHT
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)

        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(vertical: Double): Boolean {
        val listTop = getListTop()
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val totalLines = presets.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (vertical > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        clearWidgets()
        entryButtons.clear()
        init()
    }

    private fun reloadPresets() {
        DlcPresetRuntimeState.reload()
        presets = DlcPresetRuntimeState.getPresets()
        descLines = splitLines(Component.translatable("dlc_manager.preset.desc").string)
    }

    private fun buildEntryButtons() {
        entryButtons.clear()
        for (entry in presets) {
            if (entry == null) {
                continue
            }
            val id = entry.getId() ?: continue
            val button = PresetToggleButton(id) { togglePreset(entry) }
            button.x = CHECKBOX_X
            button.y = 0
            entryButtons[id] = button
            addRenderableWidget(button)
        }
    }

    private fun togglePreset(entry: DlcPresetEntry?) {
        if (entry == null) {
            return
        }
        if (DlcPresetRuntimeState.isPresetBlocked(entry.getId())) {
            return
        }
        val enabled = !DlcPresetRuntimeState.isPresetEnabled(entry.getId())
        if (enabled && entry.isAlarm()) {
            val tip = entry.resolveTip(Minecraft.getInstance().options.languageCode)
            Minecraft.getInstance().setScreen(DlcAlarmConfirmScreen(this, tip) { applyPresetEnabledState(entry, true) })
            return
        }
        applyPresetEnabledState(entry, enabled)
    }

    private fun applyPresetEnabledState(entry: DlcPresetEntry, enabled: Boolean) {
        DlcPresetRuntimeState.setPresetEnabled(entry.getId(), enabled)
        when (parent) {
            is DlcMaLiLibScreen -> parent.refreshAfterStateChange()
            is DlcServerMaLiLibScreen -> parent.refreshAfterPresetChange()
        }
    }

    private fun updateButtonState(button: PresetToggleButton, entry: DlcPresetEntry) {
        val blocked = DlcPresetRuntimeState.isPresetBlocked(entry.getId())
        button.message = Component.empty()
        button.active = !blocked
    }

    private fun renderTooltip(guiGraphics: GuiGraphics, entry: DlcPresetEntry?, mouseX: Int, mouseY: Int, lang: String) {
        if (entry == null) {
            return
        }
        val lines: MutableList<FormattedCharSequence> = ArrayList()
        val maxWidth = min(260, width - 40)
        val tip = entry.resolveTip(lang)
        if (!tip.isNullOrBlank()) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, tip, maxWidth))
        }
        val blockers = DlcPresetRuntimeState.getPresetBlockerNames(entry.getId(), lang)
        if (blockers.isNotEmpty()) {
            if (lines.isNotEmpty()) {
                lines.add(FormattedCharSequence.EMPTY)
            }
            for (blocker in blockers) {
                val line = Component.translatable("dlc_manager.preset.conflict", blocker)
                    .withStyle(ChatFormatting.DARK_RED)
                lines.addAll(font.split(line, maxWidth))
            }
        }
        if (lines.isNotEmpty()) {
            guiGraphics.renderTooltip(font, lines, mouseX, mouseY)
        }
    }

    private fun splitLines(text: String?): List<String> {
        if (text.isNullOrBlank()) {
            return emptyList()
        }
        if (text.contains("\n")) {
            val parts = text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val lines: MutableList<String> = ArrayList()
            for (part in parts) {
                if (part.isNotEmpty()) {
                    lines.add(part)
                }
            }
            if (lines.isNotEmpty()) {
                return lines
            }
        }
        return ArrayList(listOf(text))
    }

    private fun isMouseOverRow(mouseX: Int, mouseY: Int, rowY: Int, lineHeight: Int): Boolean {
        return mouseX >= 20 && mouseX <= width - 20 && mouseY >= rowY && mouseY < rowY + lineHeight
    }

    private fun isMouseOverButton(button: Button?, mouseX: Int, mouseY: Int): Boolean {
        if (button == null || !button.visible) {
            return false
        }
        val bx = button.x
        val by = button.y
        val bw = button.width
        val bh = button.height
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh
    }

    private fun getListTop(): Int {
        var y = 20 + 18
        y += descLines.size * (font.lineHeight + 2)
        y += 10
        return y
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return
        }
        val barX = width - 12
        val barWidth = 4
        val barHeight = max(12, (bottom - top) * maxVisible / totalLines)
        val trackHeight = bottom - top
        val maxTrack = max(1, trackHeight - barHeight)
        val scrollRatio = if (maxScroll == 0) 0f else scrollIndex.toFloat() / maxScroll.toFloat()
        val barY = top + (scrollRatio * maxTrack).roundToInt()
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF22262C.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF8A9099.toInt())
    }

    private fun handleBack() {
        Minecraft.getInstance().setScreen(parent)
    }

    private inner class PresetToggleButton(
        private val presetId: String,
        onPress: OnPress,
    ) : Button(0, 0, CHECKBOX_SIZE, CHECKBOX_SIZE, Component.empty(), onPress, DEFAULT_NARRATION) {
        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val status = resolvePresetStatus(presetId)
            val blocked = DlcPresetRuntimeState.isPresetBlocked(presetId)
            val hovered = isHoveredOrFocused
            val x0 = x
            val y0 = y
            val x1 = x0 + width
            val y1 = y0 + height
            val border = borderColor(blocked, hovered)
            val bg = if (blocked) 0xFF1C1C1C.toInt() else if (hovered) 0xFF2A2A2A.toInt() else 0xFF151515.toInt()
            guiGraphics.fill(x0, y0, x1, y1, bg)
            guiGraphics.fill(x0, y0, x1, y0 + 1, border)
            guiGraphics.fill(x0, y1 - 1, x1, y1, border)
            guiGraphics.fill(x0, y0, x0 + 1, y1, border)
            guiGraphics.fill(x1 - 1, y0, x1, y1, border)
            if (status == DlcPresetStatus.ON) {
                val color = checkColor(true, blocked)
                val size = 8
                val cx = x0 + (width - size) / 2
                val cy = y0 + (height - size) / 2
                guiGraphics.fill(cx, cy + 4, cx + 2, cy + 6, color)
                guiGraphics.fill(cx + 2, cy + 6, cx + 4, cy + 8, color)
                guiGraphics.fill(cx + 4, cy + 4, cx + 6, cy + 6, color)
                guiGraphics.fill(cx + 6, cy + 2, cx + 8, cy + 4, color)
            } else if (status == DlcPresetStatus.PART) {
                val color = checkColor(true, blocked)
                val size = 6
                val cx = x0 + (width - size) / 2
                val cy = y0 + (height - size) / 2
                guiGraphics.fill(cx, cy, cx + size, cy + size, color)
            }
        }
    }

    private fun resolvePresetStatus(presetId: String): DlcPresetStatus {
        if (parent is DlcServerMaLiLibScreen) {
            return parent.getPresetStatus(presetId)
        }
        return DlcPresetRuntimeState.getPresetStatus(presetId)
    }

    companion object {
        private const val ROW_HEIGHT = 22
        private const val CHECKBOX_SIZE = 14
        private const val CHECKBOX_X = 24
        private const val LABEL_GAP = 8

        private fun checkColor(enabled: Boolean, blocked: Boolean): Int {
            if (blocked) {
                return 0xFF5E5E5E.toInt()
            }
            return if (enabled) 0xFF7BFF4A.toInt() else 0xFFE0E0E0.toInt()
        }

        private fun borderColor(blocked: Boolean, hovered: Boolean): Int {
            if (blocked) {
                return 0xFF3A3A3A.toInt()
            }
            return if (hovered) 0xFFFFFFFF.toInt() else 0xFF9A9A9A.toInt()
        }
    }
}
