package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class DlcServerPackUrlScreen(
    private val parent: DlcServerPackManagerScreen,
    private val entry: DlcServerEntry,
) : DlcUrlPageScreen(DlcUiPage.SERVER_PACK_URL, parent, DlcTextCompat.translatable("dlc_manager.pack.import_url_title")) {
    private var urlField: EditBox? = null
    private var downloadButton: Button? = null
    private var pasteButton: Button? = null
    private var backButton: Button? = null
    private var working = false
    private var urlValue = ""
    private var statusText: String? = null

    private fun initControls() {
        val fieldWidth = minOf(320, width - 40)
        val fieldX = width / 2 - fieldWidth / 2
        val fieldY = height / 2 - 20
        urlField = EditBox(font, fieldX, fieldY, fieldWidth, 18, DlcTextCompat.empty()).also {
            it.setMaxLength(Int.MAX_VALUE)
            DlcScreenCompat.setFocused(it, true)
            it.value = urlValue
            addRenderableWidget(it)
        }
        val buttonWidth = 90
        val buttonY = fieldY + 30
        val leftX = width / 2 - buttonWidth - 10 - buttonWidth / 2
        val centerX = width / 2 - buttonWidth / 2
        val rightX = width / 2 + buttonWidth / 2 + 10
        val download = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.url_download")) { startDownload() }
            .bounds(leftX, buttonY, buttonWidth, 20).build()
        val paste = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.url_paste")) { pasteClipboard() }
            .bounds(centerX, buttonY, buttonWidth, 20).build()
        val back = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.pack.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(rightX, buttonY, buttonWidth, 20).build()
        downloadButton = download
        pasteButton = paste
        backButton = back
        addRenderableWidget(download)
        addRenderableWidget(paste)
        addRenderableWidget(back)
        updateButtons()
    }

    private fun pasteClipboard() {
        val clipboard = Minecraft.getInstance().keyboardHandler.clipboard
        urlField?.value = clipboard.trim()
    }

    private fun startDownload() {
        if (working) return
        val url = urlField?.value?.trim().orEmpty()
        if (url.isBlank()) {
            statusText = DlcTextCompat.translatable("dlc_manager.update.url_empty").string
            return
        }
        working = true
        statusText = DlcTextCompat.translatable("dlc_manager.pack.url_downloading").string
        updateButtons()
        Thread({
            val ok = try {
                DlcServerConnection.importPackPackageFromUrl(entry, 8000, url, null)
            } catch (error: Exception) {
                statusText = DlcTextCompat.translatable("dlc_manager.update.url_failed", error.message ?: error.javaClass.simpleName).string
                false
            }
            Minecraft.getInstance().execute {
                working = false
                updateButtons()
                if (ok) {
                    Minecraft.getInstance().setScreen(parent)
                    parent.refresh()
                } else if (statusText.isNullOrBlank() || statusText == DlcTextCompat.translatable("dlc_manager.pack.url_downloading").string) {
                    statusText = DlcTextCompat.translatable("dlc_manager.pack.import_failed", "download failed").string
                }
            }
        }, "dlc-server-pack-url").start()
    }

    private fun updateButtons() {
        downloadButton?.active = !working
        pasteButton?.active = !working
        backButton?.active = !working
    }

    override fun buildPage() {
        urlField?.let { urlValue = it.value }
        initControls()
        renderUrlForm("dlc_manager.pack.url_prompt", urlField, pasteButton, downloadButton,
            { statusText }, null, null, null, { working })
    }
    override fun updatePage() { updateButtons(); urlField?.active = !working }

    override fun shouldCloseOnEsc() = !working
}
