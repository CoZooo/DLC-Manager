package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.rcon.DlcRconQuickCommandCatalog
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.server.DlcRconClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.max

class DlcServerRconQuickScreen(
    private val parent: Screen?,
    private val serverEntry: DlcServerEntry?,
    private val port: Int,
    password: String?,
) : DlcPageScreen(DlcUiPage.SERVER_RCON_QUICK, parent, Component.translatable("dlc_manager.server.rcon_common")) {
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

    private fun initControls() {
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
            val button = Button.builder(Component.translatable("dlc_manager.server.rcon_exec")) { runCommand(quickEntry.command) }
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

    override fun buildPage() {
        initControls()
        var group = section("", "")
        for (item in quickEntries) {
            if (item.separator) group = section(tr(item.labelKey), "")
            else item.button?.let { mount(row(group, tr(item.labelKey), item.command), it) }
        }
    }
    override fun updatePage() { status(statusText) }

}
