package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.rcon.DlcRconQuickCommandCatalog
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.server.DlcRconClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

class DlcServerRconQuickScreen(
    private val parent: Screen?,
    private val serverEntry: DlcServerEntry?,
    private val port: Int,
    password: String?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.rcon_common")) {
    private val password: String = password ?: ""
    private val quickEntries: MutableList<QuickEntry> = ArrayList()
    private var statusText: String? = null
    private var backButton: Button? = null
    private var draggingScrollbar = false
    private var scroll = 0
    private var panelTop = 0
    private var panelBottom = 0
    private var panelLeft = 0
    private var panelRight = 0
    private var scrollbarX = 0
    private var buttonWidth = 0
    private var buttonHeight = 0
    private var buttonGap = 0

    override fun init() {
        quickEntries.clear()
        for (item in DlcRconQuickCommandCatalog.commandItems()) {
            if (item.separator) {
                addSeparator(item.labelKey)
            } else {
                addCommand(item.labelKey, item.command)
            }
        }

        val margin = 20
        panelLeft = margin
        panelRight = width - margin - 16
        panelTop = 40
        panelBottom = height - 50
        scrollbarX = width - margin + 2
        buttonWidth = max(120, (width - margin * 2 - 6) / 2)
        buttonHeight = 20
        buttonGap = 6
        val x1 = width / 2 - buttonWidth - 3
        val x2 = width / 2 + 3
        var y = panelTop

        var col = 0
        for (quickEntry in quickEntries) {
            if (quickEntry.separator) {
                if (col != 0) {
                    y += buttonHeight + buttonGap
                    col = 0
                }
                quickEntry.baseY = y
                y += buttonHeight + buttonGap
                continue
            }
            val x = if (col == 0) x1 else x2
            val button = Button.builder(Component.translatable(quickEntry.labelKey)) { runCommand(quickEntry.command) }
                .bounds(x, y, buttonWidth, buttonHeight)
                .build()
            quickEntry.button = button
            addRenderableWidget(button)
            quickEntry.baseY = y
            col++
            if (col >= 2) {
                y += buttonHeight + buttonGap
                col = 0
            }
        }

        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(width / 2 - 40, height - 30, 80, 20)
            .build()
        backButton = back
        addRenderableWidget(back)

        updateButtonPositions()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)
        val status = statusText
        if (!status.isNullOrBlank()) {
            guiGraphics.drawCenteredString(font, status, width / 2, height - 52, 0xC8C8C8)
        }
        drawSeparators(guiGraphics)
        drawScrollbar(guiGraphics)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, vertical: Double): Boolean {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom) {
            val maxScroll = getMaxScroll()
            if (maxScroll > 0) {
                scroll = max(0, min(maxScroll, scroll + (if (vertical > 0) -1 else 1) * (buttonHeight + buttonGap)))
                updateButtonPositions()
                return true
            }
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isOnScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun runCommand(command: String) {
        val host = serverEntry?.host ?: ""
        CompletableFuture
            .supplyAsync { DlcRconClient.execute(host, port, password, command, 15000) }
            .whenComplete { response, _ ->
                Minecraft.getInstance().execute {
                    if (response == null) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.rcon_exec_failed"),
                            Component.empty(),
                        )
                    } else {
                        statusText = Component.translatable("dlc_manager.server.rcon_response", response).string
                    }
                }
            }
    }

    private fun updateButtonPositions() {
        for (quickEntry in quickEntries) {
            val y = quickEntry.baseY - scroll
            if (quickEntry.separator) {
                quickEntry.visible = y + buttonHeight >= panelTop && y <= panelBottom - 2
                continue
            }
            val button = quickEntry.button ?: continue
            button.y = y
            val visible = y + buttonHeight >= panelTop && y <= panelBottom - 2
            button.visible = visible
            button.active = visible
        }
    }

    private fun getMaxScroll(): Int {
        if (quickEntries.isEmpty()) {
            return 0
        }
        var totalHeight = 0
        var col = 0
        for (quickEntry in quickEntries) {
            if (quickEntry.separator) {
                if (col != 0) {
                    totalHeight += buttonHeight + buttonGap
                    col = 0
                }
                totalHeight += buttonHeight + buttonGap
                continue
            }
            col++
            if (col >= 2) {
                totalHeight += buttonHeight + buttonGap
                col = 0
            }
        }
        if (col != 0) {
            totalHeight += buttonHeight + buttonGap
        }
        val visibleHeight = panelBottom - panelTop
        return max(0, totalHeight - visibleHeight)
    }

    private fun drawScrollbar(guiGraphics: GuiGraphics) {
        val maxScroll = getMaxScroll()
        if (maxScroll <= 0) {
            return
        }
        val barWidth = 6
        val barX = scrollbarX
        val trackTop = panelTop
        val trackBottom = panelBottom
        val trackHeight = trackBottom - trackTop
        val visibleHeight = panelBottom - panelTop
        val totalHeight = visibleHeight + maxScroll
        val barHeight = max(20, trackHeight * visibleHeight / max(1, totalHeight))
        val maxTrack = max(1, trackHeight - barHeight)
        val barY = trackTop + maxTrack * scroll / max(1, maxScroll)
        guiGraphics.fill(barX, trackTop, barX + barWidth, trackBottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    private fun isOnScrollbar(mouseX: Double, mouseY: Double): Boolean {
        val barWidth = 8
        val barX = scrollbarX - 1
        return mouseX >= barX && mouseX <= barX + barWidth && mouseY >= panelTop && mouseY <= panelBottom
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val maxScroll = getMaxScroll()
        if (maxScroll <= 0) {
            scroll = 0
            return
        }
        val trackTop = panelTop
        val trackBottom = panelBottom
        val trackHeight = trackBottom - trackTop
        val visibleHeight = panelBottom - panelTop
        val totalHeight = visibleHeight + maxScroll
        val barHeight = max(20, trackHeight * visibleHeight / max(1, totalHeight))
        val maxTrack = max(1, trackHeight - barHeight)
        var target = (mouseY - trackTop - barHeight / 2.0).toInt()
        target = max(0, min(maxTrack, target))
        scroll = max(0, min(maxScroll, target * maxScroll / max(1, maxTrack)))
        updateButtonPositions()
    }

    private fun drawSeparators(guiGraphics: GuiGraphics) {
        val left = panelLeft
        val right = scrollbarX - 6
        for (quickEntry in quickEntries) {
            if (!quickEntry.separator || !quickEntry.visible) {
                continue
            }
            val y = quickEntry.baseY - scroll
            val text = Component.translatable(quickEntry.labelKey).string
            val textWidth = font.width(text)
            val textX = max(left + 6, (left + right - textWidth) / 2)
            val lineY = y + buttonHeight / 2
            val leftLineEnd = max(left, textX - 6)
            val rightLineStart = min(right, textX + textWidth + 6)
            if (leftLineEnd > left) {
                guiGraphics.fill(left, lineY, leftLineEnd, lineY + 1, 0xFF3B3B3B.toInt())
            }
            if (right > rightLineStart) {
                guiGraphics.fill(rightLineStart, lineY, right, lineY + 1, 0xFF3B3B3B.toInt())
            }
            guiGraphics.drawString(font, text, textX, y + 4, 0xC8C8C8, false)
        }
    }

    private fun addCommand(labelKey: String, command: String) {
        quickEntries.add(QuickEntry.command(labelKey, command))
    }

    private fun addSeparator(labelKey: String) {
        quickEntries.add(QuickEntry.separator(labelKey))
    }

    private class QuickEntry private constructor(
        val separator: Boolean,
        val labelKey: String,
        val command: String,
    ) {
        var button: Button? = null
        var baseY = 0
        var visible = false

        companion object {
            fun command(labelKey: String, command: String): QuickEntry {
                return QuickEntry(false, labelKey, command)
            }

            fun separator(labelKey: String): QuickEntry {
                return QuickEntry(true, labelKey, "")
            }
        }
    }
}
