package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.server.DlcRconClient
import net.minecraft.client.Minecraft
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
) : DlcPageScreen(parent, Component.translatable("dlc_manager.server.rcon_console")) {
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

    private fun initControls() {
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

    private fun appendLine(line: String?) { if (line != null) lines.add(line) }

    private var output: com.sighs.apricityui.init.Element? = null
    private var renderedLines = 0
    private var scrollToEnd = false
    override fun buildPage() {
        val draft = commandField?.value.orEmpty()
        initControls(); commandField?.value = draft
        content.setAttribute("class", "console-content")
        val output = node(content, "div", "console-output"); this.output = output; renderedLines = 0
        val command = node(content, "div", "console-command")
        commandField?.let {
            val field = mount(command, it, tr("dlc_manager.server.rcon_command"), "text")
            field.addEventListener("keydown") { event -> if (event is com.sighs.apricityui.event.KeyEvent && event.key == "Enter") { event.preventDefault(); sendCommand() } }
        }
        sendButton?.let { mount(command, it).getElement().setAttribute("class", "button primary") }
    }
    override fun updatePage() {
        val output = output ?: return
        if (scrollToEnd) { DlcUiScroll.scrollToEnd(output); scrollToEnd = false }
        if (renderedLines != lines.size) {
            val follow = renderedLines == 0 || DlcUiScroll.isAtEnd(output, 24.0)
            while (renderedLines < lines.size) text(output, lines[renderedLines++], "console-line")
            scrollToEnd = follow
        }
    }

}
