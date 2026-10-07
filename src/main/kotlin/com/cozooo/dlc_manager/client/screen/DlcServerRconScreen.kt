package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerList
import com.cozooo.dlc_manager.client.server.DlcServerRconInfo
import com.cozooo.dlc_manager.server.DlcRconClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerRconScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcPageScreen(parent, Component.translatable("dlc_manager.server.rcon_title")) {
    private var portField: EditBox? = null
    private var passwordField: EditBox? = null
    private var connectButton: Button? = null
    private var backButton: Button? = null
    private var playersButton: Button? = null
    private var consoleButton: Button? = null
    private var quickButton: Button? = null

    private var rconEnabled = false
    private var passwordSet = false
    private var connected = false
    private var loadingInfo = false
    private var connectOnly = true
    private var autoConnectAttempted = false
    private var connectingRcon = false
    private var statusText: String? = null

    private var rconPort = 25575
    private var rconPassword = ""

    private fun initControls() {
        clearWidgets()
        loadSavedRcon()
        if (connectOnly) {
            initConnectPanel()
        } else {
            initHubPanel()
        }
    }

    private fun initConnectPanel() {
        val centerX = width / 2
        val topY = 60
        val fieldWidth = kotlin.math.min(220, width - 80)
        val fieldX = centerX - fieldWidth / 2

        val port = EditBox(font, fieldX, topY + 18, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_port"))
        val password = EditBox(font, fieldX, topY + 44, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_password"))
        port.setMaxLength(16)
        password.setMaxLength(256)
        port.value = rconPort.toString()
        password.value = rconPassword
        portField = port
        passwordField = password
        addRenderableWidget(port)
        addRenderableWidget(password)

        val buttonY = topY + 76
        val buttonWidth = 90
        val gap = 8
        val totalWidth = buttonWidth * 2 + gap
        val startX = centerX - totalWidth / 2
        val connect = Button.builder(Component.translatable("dlc_manager.server.rcon_connect")) { connect() }
            .bounds(startX, buttonY, buttonWidth, 20)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(startX + buttonWidth + gap, buttonY, buttonWidth, 20)
            .build()
        connectButton = connect
        backButton = back
        addRenderableWidget(connect)
        addRenderableWidget(back)

        loadRconInfo()
        updateButtonStates()
        if (!autoConnectAttempted && rconPassword.isNotBlank()) {
            autoConnectAttempted = true
            connect()
        }
    }

    private fun initHubPanel() {
        val centerX = width / 2
        val startY = 80
        val buttonWidth = 140
        val buttonHeight = 20
        val gap = 8
        val players = Button.builder(Component.translatable("dlc_manager.server.rcon_players")) { openPlayers() }
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build()
        val console = Button.builder(Component.translatable("dlc_manager.server.rcon_console")) { openConsole() }
            .bounds(centerX - buttonWidth / 2, startY + buttonHeight + gap, buttonWidth, buttonHeight)
            .build()
        val quick = Button.builder(Component.translatable("dlc_manager.server.rcon_common")) { openQuickCommands() }
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + gap) * 2, buttonWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + gap) * 3 + 6, buttonWidth, buttonHeight)
            .build()
        playersButton = players
        consoleButton = console
        quickButton = quick
        backButton = back
        addRenderableWidget(players)
        addRenderableWidget(console)
        addRenderableWidget(quick)
        addRenderableWidget(back)

        updateButtonStates()
    }

    private fun connect() {
        val host = entry?.host ?: ""
        val port = parsePort()
        val password = passwordField?.value ?: ""
        if (host.isBlank()) {
            return
        }
        connectingRcon = true
        rconPort = port
        rconPassword = password
        updateButtonStates()
        CompletableFuture
            .supplyAsync { DlcRconClient.execute(host, port, password, "list", 8000) }
            .whenComplete { response, _ ->
                Minecraft.getInstance().execute {
                    connectingRcon = false
                    if (response == null) {
                        connected = false
                        statusText = Component.translatable("dlc_manager.server.rcon_connect_failed").string
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.rcon_connect_failed"),
                            Component.empty(),
                        )
                    } else {
                        connected = true
                        statusText = Component.translatable("dlc_manager.server.rcon_connected").string
                        saveRconConfig()
                        connectOnly = false
                        refreshPage()
                    }
                    updateButtonStates()
                }
            }
    }

    private fun openPlayers() {
        Minecraft.getInstance().setScreen(DlcServerRconPlayersScreen(this, entry, rconPort, rconPassword))
    }

    private fun openConsole() {
        Minecraft.getInstance().setScreen(DlcServerRconConsoleScreen(this, entry, rconPort, rconPassword))
    }

    private fun openQuickCommands() {
        Minecraft.getInstance().setScreen(DlcServerRconQuickScreen(this, entry, rconPort, rconPassword))
    }

    private fun parsePort(): Int {
        val field = portField ?: return rconPort
        val text = field.value.trim()
        var port = 25575
        try {
            port = text.toInt()
        } catch (_: NumberFormatException) {
        }
        if (port < 1 || port > 65535) {
            port = 25575
        }
        return port
    }

    private fun loadRconInfo() {
        if (loadingInfo) {
            return
        }
        loadingInfo = true
        CompletableFuture
            .supplyAsync { fetchRconInfo() }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    loadingInfo = false
                    if (info != null) {
                        rconEnabled = info.enabled
                        passwordSet = info.passwordSet
                        portField?.value = info.port.toString()
                    }
                    statusText = if (!rconEnabled) {
                        Component.translatable("dlc_manager.server.rcon_disabled").string
                    } else if (!passwordSet) {
                        Component.translatable("dlc_manager.server.rcon_password_missing").string
                    } else if (!connected) {
                        Component.translatable("dlc_manager.server.rcon_disconnected").string
                    } else {
                        statusText
                    }
                    updateButtonStates()
                }
            }
    }

    private fun fetchRconInfo(): DlcServerRconInfo? {
        return try {
            DlcServerConnection.fetchRconInfo(entry, 8000)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateButtonStates() {
        if (connectOnly) {
            connectButton?.active = !connectingRcon
            return
        }
        val enable = connected
        playersButton?.active = enable
        consoleButton?.active = enable
        quickButton?.active = enable
    }

    private fun loadSavedRcon() {
        val current = entry ?: return
        if (current.rconPort > 0) {
            rconPort = current.rconPort
        }
        if (current.rconPassword != null) {
            rconPassword = current.rconPassword ?: ""
        }
    }

    private fun saveRconConfig() {
        val current = entry ?: return
        current.rconPort = rconPort
        current.rconPassword = rconPassword
        DlcServerList.load()
        val list = ArrayList(DlcServerList.getEntries())
        var updated = false
        for (item in list) {
            if (item != null && current.getKey() == item.getKey()) {
                item.rconPort = rconPort
                item.rconPassword = rconPassword
                updated = true
                break
            }
        }
        if (!updated) {
            list.add(current)
        }
        DlcServerList.setEntries(list)
        DlcServerList.save()
    }

    override fun buildPage() {
        val portDraft = portField?.value
        val passwordDraft = passwordField?.value
        initControls()
        if (portDraft != null) portField?.value = portDraft
        if (passwordDraft != null) passwordField?.value = passwordDraft
        val body = node(content, "div", "page-dialog")
        text(body, tr("dlc_manager.server.rcon_host") + "  " + entry?.host.orEmpty(), "page-description")
        live(body, { statusText }, "page-message")
        if (connectOnly) {
            portField?.let { mount(body, it, tr("dlc_manager.server.rcon_port"), "text") }
            passwordField?.let { mount(body, it, tr("dlc_manager.server.rcon_password"), "password") }
            connectButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
        } else {
            playersButton?.let { mount(row(body, tr("dlc_manager.server.rcon_players"), ""), it) }
            consoleButton?.let { mount(row(body, tr("dlc_manager.server.rcon_console"), ""), it) }
            quickButton?.let { mount(row(body, tr("dlc_manager.server.rcon_common"), ""), it) }
        }
    }
    override fun updatePage() { updateButtonStates() }

}
