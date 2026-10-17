package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.server.DlcServerCache
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerList
import net.minecraft.client.Minecraft
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
) : DlcPageScreen(DlcUiPage.SERVER_PASSWORD, parent, DlcTextCompat.translatable("dlc_manager.server.password_title")) {
    private var portField: EditBox? = null
    private var passwordField: EditBox? = null
    private var connectButton: Button? = null
    private var backButton: Button? = null
    private var connecting = false
    private var autoConnectAttempted = false
    private var usePortMultiplexer = entry.usePortMultiplexer

    private fun initControls() {
        val fieldWidth = minOf(260, width - 40)
        val fieldHeight = 18
        val centerX = width / 2
        val startY = height / 2 - 32

        portField = EditBox(font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, DlcTextCompat.empty()).also {
            it.value = entry.port.toString()
            DlcScreenCompat.setNumericFilter(it)
            addRenderableWidget(it)
        }

        passwordField = EditBox(font, centerX - fieldWidth / 2, startY + 24, fieldWidth, fieldHeight, DlcTextCompat.empty()).also {
            entry.password?.let { password -> it.value = password }
            addRenderableWidget(it)
        }

        val buttonWidth = 90
        val buttonHeight = 20
        val buttonY = startY + 56
        connectButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.connect")) {
            startConnect()
        }
            .bounds(centerX - buttonWidth - 6, buttonY, buttonWidth, buttonHeight)
            .build()
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back")) {
            Minecraft.getInstance().setScreen(parent)
        }
            .bounds(centerX + 6, buttonY, buttonWidth, buttonHeight)
            .build()
        connectButton?.let { addRenderableWidget(it) }
        backButton?.let { addRenderableWidget(it) }
        updateButtonState()
        if (autoConnect && !autoConnectAttempted && !entry.password.isNullOrBlank()) {
            autoConnectAttempted = true
            startConnect()
        }
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
        entry.usePortMultiplexer = usePortMultiplexer
        updateButtonState()
        CompletableFuture
            .supplyAsync { fetchInfo(entry) }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    connecting = false
                    updateButtonState()
                    if (info == null) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.connect_failed"),
                            DlcTextCompat.empty(),
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
                        Minecraft.getInstance().setScreen(DlcServerScreen(parent, entry, info))
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
                existing.usePortMultiplexer = entry.usePortMultiplexer
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
                    DlcScreenCompat.toastManager(Minecraft.getInstance()),
                    DlcTextCompat.translatable("dlc_manager.server.port_invalid"),
                    DlcTextCompat.empty(),
                )
                -1
            } else {
                port
            }
        } catch (_: NumberFormatException) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                DlcScreenCompat.toastManager(Minecraft.getInstance()),
                DlcTextCompat.translatable("dlc_manager.server.port_invalid"),
                DlcTextCompat.empty(),
            )
            -1
        }
    }

    override fun buildPage() {
        val portDraft = portField?.value
        val passwordDraft = passwordField?.value
        initControls()
        if (portDraft != null) portField?.value = portDraft
        if (passwordDraft != null) passwordField?.value = passwordDraft
        val form = node(content, "div", "page-dialog")
        text(form, tr("dlc_manager.server.address", entry.host.orEmpty()), "page-description")
        portField?.let { mount(form, it, tr("dlc_manager.server.port"), "text") }
        passwordField?.let { mount(form, it, tr("dlc_manager.server.password"), "password") }
        toggle(form, tr("dlc_manager.server.use_port_multiplexer"), usePortMultiplexer) { usePortMultiplexer = it; refreshPage() }
        connectButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
    override fun updatePage() { updateButtonState(); status(if (connecting) tr("dlc_manager.server.connecting") else "") }
    override fun onClose() { if (!connecting) super.onClose() }

    override fun shouldCloseOnEsc() = !connecting
}
