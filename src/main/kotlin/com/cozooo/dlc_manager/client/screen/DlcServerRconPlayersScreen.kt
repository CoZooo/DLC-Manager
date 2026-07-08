package com.cozooo.dlc_manager.client.screen

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

class DlcServerRconPlayersScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    private val port: Int,
    password: String?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.rcon_players")) {
    private val password: String = password ?: ""
    private val players: MutableList<String> = ArrayList()
    private var selected = -1
    private var scroll = 0
    private var statusText = ""

    private var refreshButton: Button? = null
    private var opButton: Button? = null
    private var deopButton: Button? = null
    private var kickButton: Button? = null
    private var banButton: Button? = null
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
        listTop = 60
        listBottom = height - 60

        val buttonY = 32
        val refresh = Button.builder(Component.translatable("dlc_manager.server.rcon_refresh")) { refreshPlayers() }
            .bounds(margin, buttonY, 80, 20)
            .build()
        val op = Button.builder(Component.translatable("dlc_manager.server.rcon_op")) { opSelected() }
            .bounds(margin + 86, buttonY, 60, 20)
            .build()
        val deop = Button.builder(Component.translatable("dlc_manager.server.rcon_deop")) { deopSelected() }
            .bounds(margin + 150, buttonY, 60, 20)
            .build()
        val kick = Button.builder(Component.translatable("dlc_manager.server.rcon_kick")) { kickSelected() }
            .bounds(margin + 214, buttonY, 60, 20)
            .build()
        val ban = Button.builder(Component.translatable("dlc_manager.server.rcon_ban")) { banSelected() }
            .bounds(margin + 278, buttonY, 60, 20)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(width - margin - 80, height - 30, 80, 20)
            .build()
        refreshButton = refresh
        opButton = op
        deopButton = deop
        kickButton = kick
        banButton = ban
        backButton = back

        addRenderableWidget(refresh)
        addRenderableWidget(op)
        addRenderableWidget(deop)
        addRenderableWidget(kick)
        addRenderableWidget(ban)
        addRenderableWidget(back)

        refreshPlayers()
        updateButtonStates()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)
        if (statusText.isNotBlank()) {
            guiGraphics.drawString(font, statusText, listLeft, 48, 0xC8C8C8, false)
        }
        drawList(guiGraphics)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawList(guiGraphics: GuiGraphics) {
        val height = listBottom - listTop
        if (height <= 0) {
            return
        }
        guiGraphics.fill(listLeft, listTop, listRight, listBottom, 0x22000000)
        val maxVisible = max(1, height / ROW_HEIGHT)
        val maxScroll = max(0, players.size - maxVisible)
        if (scroll > maxScroll) {
            scroll = maxScroll
        }
        var rowY = listTop + 2
        for (i in players.indices) {
            if (i < scroll || i >= scroll + maxVisible) {
                continue
            }
            val isSelected = i == selected
            if (isSelected) {
                guiGraphics.fill(listLeft + 2, rowY - 1, listRight - 2, rowY + ROW_HEIGHT - 1, 0x33222222)
                guiGraphics.fill(listLeft + 2, rowY - 1, listRight - 2, rowY, 0xFFFFFFFF.toInt())
                guiGraphics.fill(listLeft + 2, rowY + ROW_HEIGHT - 2, listRight - 2, rowY + ROW_HEIGHT - 1, 0xFFFFFFFF.toInt())
                val leftX = listLeft + 2
                val rightX = listRight - 3
                guiGraphics.fill(leftX, rowY - 1, leftX + 1, rowY + ROW_HEIGHT - 1, 0xFFFFFFFF.toInt())
                guiGraphics.fill(rightX, rowY - 1, rightX + 1, rowY + ROW_HEIGHT - 1, 0xFFFFFFFF.toInt())
            }
            guiGraphics.drawString(font, players[i], listLeft + 6, rowY + 4, 0xE6E6E6, false)
            rowY += ROW_HEIGHT
        }
        drawScrollbar(guiGraphics, maxVisible, maxScroll)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isOnScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            val index = ((mouseY - listTop) / ROW_HEIGHT).toInt() + scroll
            if (index >= 0 && index < players.size) {
                selected = index
                updateButtonStates()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, vertical: Double): Boolean {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            val height = listBottom - listTop
            val maxVisible = max(1, height / ROW_HEIGHT)
            val maxScroll = max(0, players.size - maxVisible)
            if (maxScroll > 0) {
                scroll = max(0, min(maxScroll, scroll + if (vertical > 0) -1 else 1))
                return true
            }
        }
        return false
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
        val totalLines = players.size
        if (totalLines <= maxVisible) {
            return
        }
        val barWidth = 6
        val barX = listRight - barWidth - 2
        val trackTop = listTop + 1
        val trackBottom = listBottom - 1
        val trackHeight = trackBottom - trackTop
        val barHeight = max(20, trackHeight * maxVisible / max(1, totalLines))
        val maxTrack = max(1, trackHeight - barHeight)
        val barY = trackTop + maxTrack * scroll / max(1, maxScroll)
        guiGraphics.fill(barX, trackTop, barX + barWidth, trackBottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    private fun isOnScrollbar(mouseX: Double, mouseY: Double): Boolean {
        val barWidth = 8
        val barX = listRight - barWidth - 2
        return mouseX >= barX && mouseX <= barX + barWidth && mouseY >= listTop && mouseY <= listBottom
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val height = listBottom - listTop
        val maxVisible = max(1, height / ROW_HEIGHT)
        val maxScroll = max(0, players.size - maxVisible)
        if (maxScroll <= 0) {
            scroll = 0
            return
        }
        val trackTop = listTop + 1
        val trackBottom = listBottom - 1
        val trackHeight = trackBottom - trackTop
        val barHeight = max(20, trackHeight * maxVisible / max(1, players.size))
        val maxTrack = max(1, trackHeight - barHeight)
        var target = (mouseY - trackTop - barHeight / 2.0).toInt()
        target = max(0, min(maxTrack, target))
        scroll = max(0, min(maxScroll, target * maxScroll / max(1, maxTrack)))
    }

    private fun refreshPlayers() {
        val host = entry?.host ?: ""
        CompletableFuture
            .supplyAsync { DlcRconClient.execute(host, port, password, "list", 8000) }
            .whenComplete { response, _ ->
                Minecraft.getInstance().execute {
                    if (response == null) {
                        statusText = Component.translatable("dlc_manager.server.rcon_connect_failed").string
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.rcon_connect_failed"),
                            Component.empty(),
                        )
                    } else {
                        statusText = ""
                        updatePlayersFromList(response)
                    }
                    updateButtonStates()
                }
            }
    }

    private fun updatePlayersFromList(response: String?) {
        players.clear()
        selected = -1
        if (response != null) {
            val colon = response.indexOf(':')
            if (colon >= 0 && colon + 1 < response.length) {
                val list = response.substring(colon + 1).trim()
                if (list.isNotEmpty()) {
                    val split = list.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (part in split) {
                        val name = part.trim()
                        if (name.isNotEmpty()) {
                            players.add(name)
                        }
                    }
                }
            }
        }
    }

    private fun kickSelected() {
        val player = selectedPlayer ?: return
        runCommand("kick $player", true)
    }

    private fun banSelected() {
        val player = selectedPlayer ?: return
        runCommand("ban $player", true)
    }

    private fun opSelected() {
        val player = selectedPlayer ?: return
        runCommand("op $player", true)
    }

    private fun deopSelected() {
        val player = selectedPlayer ?: return
        runCommand("deop $player", true)
    }

    private fun runCommand(command: String, refreshAfter: Boolean) {
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
                    } else if (refreshAfter) {
                        refreshPlayers()
                    }
                }
            }
    }

    private val selectedPlayer: String?
        get() = if (selected >= 0 && selected < players.size) players[selected] else null

    private fun updateButtonStates() {
        val hasPlayer = selectedPlayer != null
        kickButton?.active = hasPlayer
        banButton?.active = hasPlayer
        opButton?.active = hasPlayer
        deopButton?.active = hasPlayer
    }

    companion object {
        private const val ROW_HEIGHT = 18
    }
}
