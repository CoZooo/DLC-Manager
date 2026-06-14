package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.server.DlcRconClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerRconConsoleScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    private val port: Int,
    password: String?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.rcon_console")) {
    private val password: String = password ?: ""
    private val lines: MutableList<String> = ArrayList()
    private var scroll = 0
    private var commandField: EditBox? = null
    private var sendButton: Button? = null
    private var backButton: Button? = null
    private var listLeft = 0
    private var listTop = 0
    private var listRight = 0
    private var listBottom = 0
    private var draggingScrollbar = false

    override fun init() {
        val margin = 20
        listLeft = margin
        listRight = width - margin
        listTop = 50
        val buttonY = height - 30
        listBottom = buttonY - 10

        val backWidth = 80
        val sendWidth = 60
        val gap = 6
        var backX = margin
        val sendX = width - margin - sendWidth
        var fieldX = backX + backWidth + gap
        var fieldWidth = sendX - gap - fieldX
        if (fieldWidth < 80) {
            fieldWidth = maxOf(80, width - margin * 2 - sendWidth - gap)
            fieldX = width / 2 - fieldWidth / 2
            backX = fieldX - backWidth - gap
        }

        val command = EditBox(font, fieldX, buttonY, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_command"))
        command.setMaxLength(256)
        val send = Button.builder(Component.translatable("dlc_manager.server.rcon_send")) { sendCommand() }
            .bounds(sendX, buttonY, sendWidth, 20)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(backX, buttonY, backWidth, 20)
            .build()

        commandField = command
        sendButton = send
        backButton = back
        addRenderableWidget(command)
        addRenderableWidget(send)
        addRenderableWidget(back)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)
        drawConsole(guiGraphics)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawConsole(guiGraphics: GuiGraphics) {
        guiGraphics.fill(listLeft, listTop, listRight, listBottom, 0x22000000)
        val lineHeight = font.lineHeight + 1
        val maxVisible = maxOf(1, (listBottom - listTop - 4) / lineHeight)
        val maxScroll = maxOf(0, lines.size - maxVisible)
        if (scroll > maxScroll) {
            scroll = maxScroll
        }
        val startY = listTop + 2
        val index = scroll
        for (i in 0 until maxVisible) {
            if (index + i >= lines.size) {
                break
            }
            val line = lines[index + i]
            guiGraphics.drawString(font, line, listLeft + 4, startY + i * lineHeight, 0xC8C8C8, false)
        }
        drawScrollbar(guiGraphics, maxVisible, maxScroll)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            val lineHeight = font.lineHeight + 1
            val maxVisible = maxOf(1, (listBottom - listTop - 4) / lineHeight)
            val maxScroll = maxOf(0, lines.size - maxVisible)
            if (maxScroll > 0) {
                scroll = maxOf(0, minOf(maxScroll, scroll + if (deltaY > 0) -1 else 1))
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

    private fun drawScrollbar(guiGraphics: GuiGraphics, maxVisible: Int, maxScroll: Int) {
        val totalLines = lines.size
        if (totalLines <= maxVisible) {
            return
        }
        val barWidth = 6
        val barX = listRight - barWidth - 2
        val trackTop = listTop + 1
        val trackBottom = listBottom - 1
        val trackHeight = trackBottom - trackTop
        val barHeight = maxOf(20, trackHeight * maxVisible / maxOf(1, totalLines))
        val maxTrack = maxOf(1, trackHeight - barHeight)
        val barY = trackTop + maxTrack * scroll / maxOf(1, maxScroll)
        guiGraphics.fill(barX, trackTop, barX + barWidth, trackBottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    private fun isOnScrollbar(mouseX: Double, mouseY: Double): Boolean {
        val barWidth = 8
        val barX = listRight - barWidth - 2
        return mouseX >= barX && mouseX <= barX + barWidth && mouseY >= listTop && mouseY <= listBottom
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val lineHeight = font.lineHeight + 1
        val maxVisible = maxOf(1, (listBottom - listTop - 4) / lineHeight)
        val maxScroll = maxOf(0, lines.size - maxVisible)
        if (maxScroll <= 0) {
            scroll = 0
            return
        }
        val trackTop = listTop + 1
        val trackBottom = listBottom - 1
        val trackHeight = trackBottom - trackTop
        val barHeight = maxOf(20, trackHeight * maxVisible / maxOf(1, lines.size))
        val maxTrack = maxOf(1, trackHeight - barHeight)
        var target = (mouseY - trackTop - barHeight / 2.0).toInt()
        target = maxOf(0, minOf(maxTrack, target))
        scroll = maxOf(0, minOf(maxScroll, target * maxScroll / maxOf(1, maxTrack)))
    }

    private fun sendCommand() {
        val field = commandField ?: return
        val command = field.value.trim()
        if (command.isEmpty()) {
            return
        }
        field.value = ""
        appendLine("> $command")
        val host = entry?.host ?: ""
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
                        appendResponse(response)
                    }
                }
            }
    }

    private fun appendResponse(response: String?) {
        if (response.isNullOrBlank()) {
            appendLine(Component.translatable("dlc_manager.server.rcon_response", "").string)
            return
        }
        val parts = response.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (part in parts) {
            appendLine(part)
        }
    }

    private fun appendLine(line: String?) {
        if (line == null) {
            return
        }
        appendWrapped(line)
        val lineHeight = font.lineHeight + 1
        val maxVisible = maxOf(1, (listBottom - listTop - 4) / lineHeight)
        val maxScroll = maxOf(0, lines.size - maxVisible)
        scroll = maxScroll
    }

    private fun appendWrapped(line: String) {
        val maxWidth = maxOf(40, listRight - listLeft - 12)
        if (font.width(line) <= maxWidth) {
            lines.add(line)
            return
        }
        var remaining = line
        while (remaining.isNotEmpty()) {
            var cut = remaining.length
            while (cut > 0 && font.width(remaining.substring(0, cut)) > maxWidth) {
                cut--
            }
            if (cut <= 0) {
                break
            }
            lines.add(remaining.substring(0, cut))
            remaining = remaining.substring(cut).trim { it <= ' ' }
        }
        if (remaining.isNotEmpty()) {
            lines.add(remaining)
        }
    }
}
