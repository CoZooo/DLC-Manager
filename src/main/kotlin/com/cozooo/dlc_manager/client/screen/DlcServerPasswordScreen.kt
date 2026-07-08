package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerCache
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerList
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerPasswordScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val entry: DlcServerEntry,
    private val autoConnect: Boolean,
    private val openServerManagerOnSuccess: Boolean = false,
) : Screen(Component.translatable("dlc_manager.server.password_title")) {
    private var portField: EditBox? = null
    private var passwordField: EditBox? = null
    private var connectButton: Button? = null
    private var backButton: Button? = null
    private var connecting = false

    override fun init() {
        val fieldWidth = minOf(260, width - 40)
        val fieldHeight = 18
        val centerX = width / 2
        val startY = height / 2 - 32

        portField = EditBox(font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, Component.empty()).also {
            it.value = entry.port.toString()
            it.setFilter { value -> value.chars().allMatch { codePoint -> Character.isDigit(codePoint) } }
            addRenderableWidget(it)
        }

        passwordField = EditBox(font, centerX - fieldWidth / 2, startY + 24, fieldWidth, fieldHeight, Component.empty()).also {
            if (entry.password != null) {
                it.value = entry.password
            }
            addRenderableWidget(it)
        }

        val buttonWidth = 90
        val buttonHeight = 20
        val buttonY = startY + 56
        connectButton = Button.builder(Component.translatable("dlc_manager.server.connect")) {
            startConnect()
        }
            .bounds(centerX - buttonWidth - 6, buttonY, buttonWidth, buttonHeight)
            .build()
        backButton = Button.builder(Component.translatable("gui.back")) {
            Minecraft.getInstance().setScreen(parent)
        }
            .bounds(centerX + 6, buttonY, buttonWidth, buttonHeight)
            .build()
        connectButton?.let { addRenderableWidget(it) }
        backButton?.let { addRenderableWidget(it) }
        updateButtonState()
        if (autoConnect && !entry.password.isNullOrBlank()) {
            startConnect()
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        guiGraphics.drawCenteredString(font, title, centerX, height / 2 - 60, 0xFFFFFF)
        val address = entry.host ?: ""
        guiGraphics.drawCenteredString(
            font,
            Component.translatable("dlc_manager.server.address", address),
            centerX,
            height / 2 - 48,
            0xC8C8C8,
        )
        portField?.let { drawFieldLabel(guiGraphics, "dlc_manager.server.port", it) }
        passwordField?.let { drawFieldLabel(guiGraphics, "dlc_manager.server.password", it) }
        updateButtonState()
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawFieldLabel(guiGraphics: GuiGraphics, key: String, field: EditBox) {
        val labelWidth = font.width(Component.translatable(key))
        var x = field.x - labelWidth - 8
        if (x < 8) {
            x = 8
        }
        val y = field.y + (field.height - font.lineHeight) / 2
        guiGraphics.drawString(font, Component.translatable(key), x, y, 0xC8C8C8, false)
    }

    private fun updateButtonState() {
        connectButton?.active = !connecting
        backButton?.active = !connecting
    }

    private fun startConnect() {
        if (connecting) {
            return
        }
        val port = parsePort()
        if (port <= 0) {
            return
        }
        connecting = true
        entry.port = port
        entry.password = passwordField?.value?.trim() ?: ""
        updateButtonState()
        CompletableFuture
            .supplyAsync { fetchInfo(entry) }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    connecting = false
                    updateButtonState()
                    if (info == null) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.connect_failed"),
                            Component.empty(),
                        )
                        return@execute
                    }
                    saveEntry(entry)
                    DlcServerCache.put(entry.getKey(), info)
                    if (openServerManagerOnSuccess) {
                        Minecraft.getInstance().setScreen(
                            DlcServerManagerScreen(parent, entry, info, DlcServerManagerScreen.Tab.SERVERS, true)
                        )
                    } else {
                        Minecraft.getInstance().setScreen(DlcServerMaLiLibScreen(parent, entry, info))
                    }
                }
            }
    }

    private fun fetchInfo(entry: DlcServerEntry): DlcServerInfo? {
        return try {
            DlcServerConnection.fetchServerInfo(entry, 5000)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveEntry(entry: DlcServerEntry) {
        DlcServerList.load()
        val list = ArrayList(DlcServerList.getEntries())
        var found = false
        for (i in list.indices) {
            val existing = list[i]
            if (existing != null && entry.getKey() == existing.getKey()) {
                existing.password = entry.password
                existing.port = entry.port
                if (!entry.name.isNullOrBlank()) {
                    existing.name = entry.name
                }
                found = true
                break
            }
        }
        if (!found) {
            list.add(entry)
        }
        DlcServerList.setEntries(list)
        DlcServerList.setLastSelectedKey(entry.getKey())
        DlcServerList.save()
    }

    private fun parsePort(): Int {
        val field = portField ?: return entry.port
        val value = field.value.trim()
        if (value.isEmpty()) {
            return entry.port
        }
        return try {
            val port = value.toInt()
            if (port < 1 || port > 65535) {
                DlcScreenCompat.addOrUpdatePeriodicNotification(
                    Minecraft.getInstance().toasts,
                    Component.translatable("dlc_manager.server.port_invalid"),
                    Component.empty(),
                )
                -1
            } else {
                port
            }
        } catch (_: NumberFormatException) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                Minecraft.getInstance().toasts,
                Component.translatable("dlc_manager.server.port_invalid"),
                Component.empty(),
            )
            -1
        }
    }
}
