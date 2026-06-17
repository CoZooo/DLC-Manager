package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcAlarmConfirmScreen(
    private val parent: Screen?,
    tip: String?,
    private val onConfirm: Runnable?,
) : Screen(Component.translatable("dlc_manager.alarm.title")) {
    private val tip: String = tip ?: ""
    private var confirmButton: Button? = null
    private var tipLines: List<FormattedCharSequence> = emptyList()
    private var checkboxX = 0
    private var checkboxY = 0
    private var checkboxSize = 12
    private var checkboxTextWidth = 0
    private var acknowledged = false
    private var waitEndMillis = 0L

    override fun init() {
        val maxWidth = min(420, max(180, width - 80))
        tipLines = wrapTip(maxWidth)

        val buttonWidth = 120
        val buttonHeight = 20
        val buttonGap = 10
        val totalWidth = buttonWidth * 2 + buttonGap
        val leftX = (width - totalWidth) / 2
        val buttonY = height - 36

        val confirm = Button.builder(Component.translatable("dlc_manager.alarm.confirm")) { handleConfirm() }
            .bounds(leftX, buttonY, buttonWidth, buttonHeight)
            .build()
        confirmButton = confirm
        confirm.active = false
        addRenderableWidget(confirm)

        addRenderableWidget(
            Button.builder(Component.translatable("dlc_manager.alarm.cancel")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(leftX + buttonWidth + buttonGap, buttonY, buttonWidth, buttonHeight)
                .build()
        )

        val checkboxText = Component.translatable("dlc_manager.alarm.read_ack")
        checkboxTextWidth = font.width(checkboxText)
        val totalCheckboxWidth = checkboxSize + 6 + checkboxTextWidth
        checkboxX = (width - totalCheckboxWidth) / 2
        checkboxY = buttonY - 24

        val configuredWaiting = max(0, min(10, DlcConfig.getAlarmWaitingTime()))
        waitEndMillis = if (configuredWaiting > 0) System.currentTimeMillis() + configuredWaiting * 1000L else 0L
        updateConfirmButtonState()
    }

    private fun handleConfirm() {
        if (!acknowledged || isWaiting()) {
            return
        }
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(parent)
        if (onConfirm != null) {
            minecraft.execute(onConfirm)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isInsideCheckbox(mouseX, mouseY)) {
            if (isWaiting()) {
                return true
            }
            acknowledged = !acknowledged
            updateConfirmButtonState()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun tick() {
        super.tick()
        updateConfirmButtonState()
    }

    private fun isInsideCheckbox(mouseX: Double, mouseY: Double): Boolean {
        val totalWidth = checkboxSize + 6 + checkboxTextWidth
        val hitHeight = max(checkboxSize, font.lineHeight + 2)
        return mouseX >= checkboxX && mouseX < checkboxX + totalWidth &&
            mouseY >= checkboxY && mouseY < checkboxY + hitHeight
    }

    private fun wrapTip(maxWidth: Int): List<FormattedCharSequence> {
        return DlcTextFormatter.splitMultiline(font, tip, maxWidth)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
        val remaining = getRemainingSeconds()
        if (remaining > 0) {
            val remainingText = Component.translatable("dlc_manager.alarm.remaining", remaining)
            val x = width - font.width(remainingText) - 10
            guiGraphics.drawString(font, remainingText, x, 10, 0xE6E6E6, false)
        }

        val centerX = width / 2
        val titleY = 36
        val title = Component.translatable("dlc_manager.alarm.title")
            .withStyle { style -> style.withBold(true).withColor(0xFF5555) }
        val titleScale = 1.25f
        guiGraphics.pose().pushPose()
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f)
        guiGraphics.drawCenteredString(font, title, (centerX / titleScale).roundToInt(), (titleY / titleScale).roundToInt(), 0xFF5555)
        guiGraphics.pose().popPose()

        val lineHeight = font.lineHeight + 2
        val tipTotalHeight = if (tipLines.isEmpty()) 0 else tipLines.size * lineHeight - 2
        val tipAreaTop = 72
        val tipAreaBottom = checkboxY - 12
        val tipAreaCenter = (tipAreaTop + tipAreaBottom) / 2
        var tipY = max(tipAreaTop, tipAreaCenter - tipTotalHeight / 2)
        for (line in tipLines) {
            if (tipY + font.lineHeight > tipAreaBottom) {
                break
            }
            guiGraphics.drawCenteredString(font, line, centerX, tipY, 0xE6E6E6)
            tipY += lineHeight
        }

        val waiting = remaining > 0
        val boxColor = if (waiting) 0xFF777777.toInt() else if (acknowledged) 0xFF66FF66.toInt() else 0xFFB0B0B0.toInt()
        guiGraphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, 0xFF111111.toInt())
        guiGraphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + 1, boxColor)
        guiGraphics.fill(checkboxX, checkboxY + checkboxSize - 1, checkboxX + checkboxSize, checkboxY + checkboxSize, boxColor)
        guiGraphics.fill(checkboxX, checkboxY, checkboxX + 1, checkboxY + checkboxSize, boxColor)
        guiGraphics.fill(checkboxX + checkboxSize - 1, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, boxColor)
        if (acknowledged) {
            val innerPadding = 3
            guiGraphics.fill(
                checkboxX + innerPadding,
                checkboxY + innerPadding,
                checkboxX + checkboxSize - innerPadding,
                checkboxY + checkboxSize - innerPadding,
                0xFF66FF66.toInt(),
            )
        }
        guiGraphics.drawString(
            font,
            Component.translatable("dlc_manager.alarm.read_ack"),
            checkboxX + checkboxSize + 6,
            checkboxY + (checkboxSize - font.lineHeight) / 2,
            if (waiting) 0xA0A0A0 else 0xE6E6E6,
            false,
        )
    }

    private fun getRemainingSeconds(): Int {
        if (waitEndMillis <= 0) {
            return 0
        }
        val remaining = waitEndMillis - System.currentTimeMillis()
        if (remaining <= 0) {
            return 0
        }
        return ceil(remaining / 1000.0).toInt()
    }

    private fun isWaiting(): Boolean = getRemainingSeconds() > 0

    private fun updateConfirmButtonState() {
        val button = confirmButton
        if (button != null) {
            button.active = acknowledged && !isWaiting()
        }
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }
}
