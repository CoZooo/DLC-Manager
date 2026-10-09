package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class DlcServerUpdateUrlScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcUrlPageScreen(DlcUiPage.SERVER_UPDATE_URL, parent, Component.translatable("dlc_manager.update.import_url_title")) {
    private var urlField: EditBox? = null
    private var downloadButton: Button? = null
    private var pasteButton: Button? = null
    private var backButton: Button? = null
    @Volatile private var downloading = false
    @Volatile private var progress = 0f
    @Volatile private var errorText: String? = null
    @Volatile private var statusText: String? = null
    @Volatile private var totalBytes = 0L
    @Volatile private var downloadedBytes = 0L
    private var urlValue = ""

    private fun initControls() {
        val fieldWidth = kotlin.math.min(320, width - 40)
        val fieldHeight = 18
        val fieldX = width / 2 - fieldWidth / 2
        val fieldY = height / 2 - 20
        val field = EditBox(font, fieldX, fieldY, fieldWidth, fieldHeight, Component.empty()).apply {
            setMaxLength(Int.MAX_VALUE)
            isFocused = true
            value = urlValue
        }
        urlField = field
        addRenderableWidget(field)

        val buttonWidth = 90
        val buttonHeight = 20
        val buttonY = fieldY + 30
        val leftX = width / 2 - buttonWidth - 10 - buttonWidth / 2
        val centerX = width / 2 - buttonWidth / 2
        val rightX = width / 2 + buttonWidth / 2 + 10

        val download = Button.builder(Component.translatable("dlc_manager.update.url_download")) { startDownload() }
            .bounds(leftX, buttonY, buttonWidth, buttonHeight)
            .build()
        val paste = Button.builder(Component.translatable("dlc_manager.update.url_paste")) { pasteClipboard() }
            .bounds(centerX, buttonY, buttonWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(rightX, buttonY, buttonWidth, buttonHeight)
            .build()
        downloadButton = download
        pasteButton = paste
        backButton = back
        addRenderableWidget(download)
        addRenderableWidget(paste)
        addRenderableWidget(back)
    }

    private fun pasteClipboard() {
        val clipboard = Minecraft.getInstance().keyboardHandler.clipboard
        if (clipboard != null) {
            urlField?.value = clipboard.trim()
        }
    }

    private fun startDownload() {
        if (downloading) {
            return
        }
        val url = urlField?.value?.trim() ?: ""
        if (url.isBlank()) {
            errorText = Component.translatable("dlc_manager.update.url_empty").string
            return
        }
        downloading = true
        progress = 0f
        downloadedBytes = 0L
        totalBytes = 0L
        errorText = null
        statusText = Component.translatable("dlc_manager.update.url_downloading").string
        downloadButton?.active = false
        pasteButton?.active = false
        backButton?.active = false

        Thread({
            var downloaded: Path? = null
            try {
                downloaded = downloadToUpdateDir(url)
                if (downloaded == null) {
                    throw IOException("download failed")
                }
                val validation = DlcUpdateService.validateImportPackage(downloaded)
                if (!validation.isValid) {
                    Files.deleteIfExists(downloaded)
                    Minecraft.getInstance().execute {
                        errorText = DlcMessageAdapter.toImportValidationComponent(validation).string
                        downloading = false
                        setButtonsActive(true)
                    }
                    return@Thread
                }
                val imported = DlcServerConnection.importUpdatePackage(entry, 8000, downloaded)
                Minecraft.getInstance().execute {
                    downloading = false
                    setButtonsActive(true)
                    if (!imported) {
                        errorText = Component.translatable("dlc_manager.update.failed").string
                        return@execute
                    }
                    if (parent is DlcServerUpdateManagerScreen) {
                        parent.refreshData()
                    }
                    Minecraft.getInstance().setScreen(parent)
                }
            } catch (error: Exception) {
                val toDelete = downloaded
                if (toDelete != null) {
                    try {
                        Files.deleteIfExists(toDelete)
                    } catch (_: IOException) {
                    }
                }
                val msg = error.message ?: "download failed"
                Minecraft.getInstance().execute {
                    errorText = Component.translatable("dlc_manager.update.url_failed", msg).string
                    downloading = false
                    setButtonsActive(true)
                }
            }
        }, "dlc-server-update-url-download").start()
    }

    @Throws(IOException::class)
    private fun downloadToUpdateDir(url: String): Path? {
        val updateDir = DlcUpdateService.updateDir ?: throw IOException("missing dlc root")
        Files.createDirectories(updateDir)
        var fileName = DlcDownloadService.suggestFileName(url)
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(url))
        }
        if (fileName.isNullOrBlank()) {
            fileName = "update.zip"
        } else if (!fileName.lowercase(Locale.ROOT).endsWith(".zip")) {
            fileName += ".zip"
        }
        totalBytes = 0L
        downloadedBytes = 0L
        return DlcDownloadService.download(
            url,
            updateDir,
            fileName,
            DlcDownloadService.ProgressListener { done, total ->
                statusText = Component.translatable("dlc_manager.update.url_downloading").string
                downloadedBytes = done
                totalBytes = total
                progress = if (total > 0) done / total.toFloat() else 0f
            },
            null as DlcDownloadService.CancelChecker?,
        )
    }

    private fun setButtonsActive(active: Boolean) {
        downloadButton?.active = active
        pasteButton?.active = active
        backButton?.active = active
    }

    override fun buildPage() {
        urlField?.let { urlValue = it.value }
        initControls()
        renderUrlForm("dlc_manager.update.url_prompt", urlField, pasteButton, downloadButton,
            { statusText }, { errorText }, { progress.toDouble() }, { if (downloading) "${(progress * 100).toInt()}%" else "" }, { downloading })
    }
    override fun updatePage() { urlField?.active = !downloading }

    override fun shouldCloseOnEsc() = !downloading
}
