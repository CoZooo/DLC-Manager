package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.Consumer

class DlcServerEditScreen(
    private val parent: Screen?,
    private val editing: DlcServerEntry?,
    private val onSave: Consumer<DlcServerEntry>?,
) : Screen(Component.translatable("dlc_manager.server.edit_title")) {
    private var nameField: EditBox? = null
    private var hostField: EditBox? = null
    private var portField: EditBox? = null
    private var passwordField: EditBox? = null
    private var saveButton: Button? = null
    private var cancelButton: Button? = null

    override fun init() {
        val fieldWidth = minOf(260, width - 40)
        val fieldHeight = 18
        val centerX = width / 2
        val startY = height / 2 - 60
        val gap = 22

        nameField = EditBox(font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, Component.empty())
        hostField = EditBox(font, centerX - fieldWidth / 2, startY + gap, fieldWidth, fieldHeight, Component.empty())
        portField = EditBox(font, centerX - fieldWidth / 2, startY + gap * 2, fieldWidth, fieldHeight, Component.empty())
        passwordField = EditBox(font, centerX - fieldWidth / 2, startY + gap * 3, fieldWidth, fieldHeight, Component.empty())
        hostField!!.setMaxLength(255)

        if (editing != null) {
            if (editing.name != null) {
                nameField!!.value = editing.name
            }
            if (editing.host != null) {
                hostField!!.value = editing.host
            }
            if (editing.port > 0) {
                portField!!.value = editing.port.toString()
            }
            if (editing.password != null) {
                passwordField!!.value = editing.password
            }
        } else {
            portField!!.value = DlcConfig.getServerPort().toString()
        }

        addRenderableWidget(nameField!!)
        addRenderableWidget(hostField!!)
        addRenderableWidget(portField!!)
        addRenderableWidget(passwordField!!)

        val buttonWidth = 90
        val buttonHeight = 20
        val buttonY = height / 2 + 40
        val save = Button.builder(Component.translatable("dlc_manager.server.save")) { handleSave() }
            .bounds(centerX - buttonWidth - 6, buttonY, buttonWidth, buttonHeight)
            .build()
        val cancel = Button.builder(Component.translatable("gui.cancel")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(centerX + 6, buttonY, buttonWidth, buttonHeight)
            .build()
        saveButton = save
        cancelButton = cancel
        addRenderableWidget(save)
        addRenderableWidget(cancel)
        updateSaveButtonState()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        guiGraphics.drawCenteredString(font, title, centerX, height / 2 - 90, 0xFFFFFF)
        drawFieldLabel(guiGraphics, "dlc_manager.server.name", nameField)
        drawFieldLabel(guiGraphics, "dlc_manager.server.host", hostField)
        drawFieldLabel(guiGraphics, "dlc_manager.server.port", portField)
        drawFieldLabel(guiGraphics, "dlc_manager.server.password", passwordField)
        updateSaveButtonState()
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (nameField!!.keyPressed(keyCode, scanCode, modifiers)) return true
        if (hostField!!.keyPressed(keyCode, scanCode, modifiers)) return true
        if (portField!!.keyPressed(keyCode, scanCode, modifiers)) return true
        if (passwordField!!.keyPressed(keyCode, scanCode, modifiers)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (nameField!!.charTyped(codePoint, modifiers)) return true
        if (hostField!!.charTyped(codePoint, modifiers)) return true
        if (portField!!.charTyped(codePoint, modifiers)) return true
        if (passwordField!!.charTyped(codePoint, modifiers)) return true
        return super.charTyped(codePoint, modifiers)
    }

    private fun drawFieldLabel(guiGraphics: GuiGraphics, key: String, field: EditBox?) {
        if (field == null) {
            return
        }
        val labelWidth = font.width(Component.translatable(key))
        var x = field.x - labelWidth - 8
        if (x < 8) {
            x = 8
        }
        val y = field.y + (field.height - font.lineHeight) / 2
        guiGraphics.drawString(font, Component.translatable(key), x, y, 0xC8C8C8, false)
    }

    private fun updateSaveButtonState() {
        val host = hostField
        val button = saveButton
        if (button != null) {
            button.active = host != null && host.value.isNotBlank()
        }
    }

    private fun handleSave() {
        val entry = editing ?: DlcServerEntry()
        entry.name = nameField!!.value.trim()
        entry.host = hostField!!.value.trim()
        entry.password = passwordField!!.value.trim()
        entry.useTcp = true
        var port = DlcConfig.getServerPort()
        try {
            port = portField!!.value.trim().toInt()
        } catch (_: NumberFormatException) {
        }
        entry.port = port
        onSave?.accept(entry)
        Minecraft.getInstance().setScreen(parent)
    }
}
