package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
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
) : Screen(Component.translatable("dlc_manager.update.import_url_title")) {
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

    override fun init() {
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

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = height / 2 - 80
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.url_prompt"), centerX, y, 0xC8C8C8)

        if (downloading) {
            val barY = height / 2 + 60
            val status = statusText ?: ""
            guiGraphics.drawCenteredString(font, status, centerX, barY - 14, 0xE6E6E6)
            drawProgressBar(guiGraphics, centerX - 120, barY, 240, 8, progress)
            val percent = if (totalBytes > 0) kotlin.math.round(kotlin.math.max(0f, kotlin.math.min(1f, progress)) * 100f).toInt() else 0
            guiGraphics.drawCenteredString(font, "$percent%", centerX, barY + 10, 0xC8C8C8)
        } else if (!errorText.isNullOrBlank()) {
            guiGraphics.drawCenteredString(font, errorText ?: "", centerX, height / 2 + 60, 0xFF6B6B)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val field = urlField
        if (field != null && field.keyPressed(keyCode, scanCode, modifiers)) {
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val field = urlField
        if (field != null && field.charTyped(codePoint, modifiers)) {
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        val field = urlField
        if (field != null) {
            urlValue = field.value
        }
        super.resize(minecraft, width, height)
        urlField?.value = urlValue
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
                        errorText = DlcUpdateService.toImportValidationComponent(validation).string
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

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val filled = (width * kotlin.math.max(0f, kotlin.math.min(1f, progress))).toInt()
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B.toInt())
        guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50.toInt())
    }
}
