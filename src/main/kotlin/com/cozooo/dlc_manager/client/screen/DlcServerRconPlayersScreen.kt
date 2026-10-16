package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.server.DlcRconClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerRconPlayersScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    private val port: Int,
    password: String?,
) : DlcPageScreen(DlcUiPage.SERVER_RCON_PLAYERS, parent, DlcTextCompat.translatable("dlc_manager.server.rcon_players")) {
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

    private fun initControls() {
        val margin = 20
        listLeft = margin
        listRight = width - margin
        listTop = 60
        listBottom = height - 60

        val buttonY = 32
        val refresh = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_refresh")) { refreshPlayers() }
            .bounds(margin, buttonY, 80, 20)
            .build()
        val op = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_op")) { opSelected() }
            .bounds(margin + 86, buttonY, 60, 20)
            .build()
        val deop = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_deop")) { deopSelected() }
            .bounds(margin + 150, buttonY, 60, 20)
            .build()
        val kick = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_kick")) { kickSelected() }
            .bounds(margin + 214, buttonY, 60, 20)
            .build()
        val ban = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_ban")) { banSelected() }
            .bounds(margin + 278, buttonY, 60, 20)
            .build()
        val back = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
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

    private fun refreshPlayers() {
        val host = entry?.host ?: ""
        CompletableFuture
            .supplyAsync { DlcRconClient.execute(host, port, password, "list", 8000) }
            .whenComplete { response, _ ->
                Minecraft.getInstance().execute {
                    if (response == null) {
                        statusText = DlcTextCompat.translatable("dlc_manager.server.rcon_connect_failed").string
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.rcon_connect_failed"),
                            DlcTextCompat.empty(),
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
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.rcon_exec_failed"),
                            DlcTextCompat.empty(),
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

    }

    private var renderedPlayers: List<String> = emptyList()
    private var renderedSelection = -2
    override fun buildPage() {
        initControls()
        refreshButton?.let { mount(toolbar, it) }
        opButton?.let { mount(footer, it) }; deopButton?.let { mount(footer, it) }
        kickButton?.let { mount(footer, it).getElement().setAttribute("class", "button danger") }
        banButton?.let { mount(footer, it).getElement().setAttribute("class", "button danger") }
        renderPlayers()
    }
    private fun renderPlayers() {
        content.setInnerHTML(""); renderedPlayers = players.toList(); renderedSelection = selected
        for ((index, player) in players.withIndex()) {
            val row = row(content, player, "")
            row.setAttribute("class", "page-row server-row" + if (index == selected) " selected" else "")
            row.setAttribute("role", "option"); row.setAttribute("aria-selected", (index == selected).toString())
            row.addEventListener("click") { selected = index; updateButtonStates() }
        }
        if (players.isEmpty()) text(content, tr("dlc_manager.ui.no_players"), "empty-state")
    }
    override fun updatePage() {
        if (renderedPlayers != players || renderedSelection != selected) renderPlayers()
        updateButtonStates(); status(statusText)
    }

}
