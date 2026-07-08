package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcServerPackUrlScreen(
    private val parent: DlcServerPackManagerScreen,
    private val entry: DlcServerEntry,
) : Screen(Component.translatable("dlc_manager.pack.import_url_title")) {
    private var urlField: EditBox? = null
    private var downloadButton: Button? = null
    private var pasteButton: Button? = null
    private var backButton: Button? = null
    private var working = false
    private var urlValue = ""
    private var statusText: String? = null

    override fun init() {
        val fieldWidth = minOf(320, width - 40)
        val fieldX = width / 2 - fieldWidth / 2
        val fieldY = height / 2 - 20
        urlField = EditBox(font, fieldX, fieldY, fieldWidth, 18, Component.empty()).also {
            it.setMaxLength(Int.MAX_VALUE)
            it.isFocused = true
            it.value = urlValue
            addRenderableWidget(it)
        }
        val buttonWidth = 90
        val buttonY = fieldY + 30
        val leftX = width / 2 - buttonWidth - 10 - buttonWidth / 2
        val centerX = width / 2 - buttonWidth / 2
        val rightX = width / 2 + buttonWidth / 2 + 10
        val download = Button.builder(Component.translatable("dlc_manager.update.url_download")) { startDownload() }
            .bounds(leftX, buttonY, buttonWidth, 20).build()
        val paste = Button.builder(Component.translatable("dlc_manager.update.url_paste")) { pasteClipboard() }
            .bounds(centerX, buttonY, buttonWidth, 20).build()
        val back = Button.builder(Component.translatable("dlc_manager.pack.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(rightX, buttonY, buttonWidth, 20).build()
        downloadButton = download
        pasteButton = paste
        backButton = back
        addRenderableWidget(download)
        addRenderableWidget(paste)
        addRenderableWidget(back)
        updateButtons()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        val y = height / 2 - 80
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack.url_prompt"), centerX, y + 18, 0xC8C8C8)
        if (!statusText.isNullOrBlank()) {
            guiGraphics.drawCenteredString(font, statusText.orEmpty(), centerX, height / 2 + 60, if (working) 0xE6E6E6 else 0xFF6B6B)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (urlField?.keyPressed(keyCode, scanCode, modifiers) == true) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (urlField?.charTyped(codePoint, modifiers) == true) return true
        return super.charTyped(codePoint, modifiers)
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        urlField?.let { urlValue = it.value }
        super.resize(minecraft, width, height)
        urlField?.value = urlValue
    }

    private fun pasteClipboard() {
        Minecraft.getInstance().keyboardHandler.clipboard?.let { urlField?.value = it.trim() }
    }

    private fun startDownload() {
        if (working) return
        val url = urlField?.value?.trim().orEmpty()
        if (url.isBlank()) {
            statusText = Component.translatable("dlc_manager.update.url_empty").string
            return
        }
        working = true
        statusText = Component.translatable("dlc_manager.pack.url_downloading").string
        updateButtons()
        Thread({
            val ok = try {
                DlcServerConnection.importPackPackageFromUrl(entry, 8000, url, null)
            } catch (error: Exception) {
                statusText = Component.translatable("dlc_manager.update.url_failed", error.message ?: error.javaClass.simpleName).string
                false
            }
            Minecraft.getInstance().execute {
                working = false
                updateButtons()
                if (ok) {
                    Minecraft.getInstance().setScreen(parent)
                    parent.refresh()
                } else if (statusText.isNullOrBlank() || statusText == Component.translatable("dlc_manager.pack.url_downloading").string) {
                    statusText = Component.translatable("dlc_manager.pack.import_failed", "download failed").string
                }
            }
        }, "dlc-server-pack-url").start()
    }

    private fun updateButtons() {
        downloadButton?.active = !working
        pasteButton?.active = !working
        backButton?.active = !working
    }
}
