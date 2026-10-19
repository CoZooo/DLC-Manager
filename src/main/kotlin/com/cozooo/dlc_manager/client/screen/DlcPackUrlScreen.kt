package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.min

class DlcPackUrlScreen(
    private val parent: DlcPackManagerScreen,
) : DlcUrlPageScreen(DlcUiPage.LOCAL_PACK_URL, parent, DlcTextCompat.translatable("dlc_manager.pack.import_url_title")) {
    private var urlField: EditBox? = null
    private var downloadButton: Button? = null
    private var pasteButton: Button? = null
    private var backButton: Button? = null
    @Volatile private var downloading = false
    @Volatile private var cancelRequested = false
    @Volatile private var progress = 0f
    @Volatile private var errorText: String? = null
    @Volatile private var statusText: String? = null
    @Volatile private var totalBytes = 0L
    @Volatile private var downloadedBytes = 0L
    private var urlValue = ""

    private fun initControls() {
        val fieldWidth = min(320, width - 40)
        val fieldHeight = 18
        val fieldX = width / 2 - fieldWidth / 2
        val fieldY = height / 2 - 20
        val field = EditBox(font, fieldX, fieldY, fieldWidth, fieldHeight, DlcTextCompat.empty()).apply {
            setMaxLength(Int.MAX_VALUE)
            DlcScreenCompat.setFocused(this, true)
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

        val downloadX = if (downloading) centerX else leftX
        val download = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.url_download")) {
            if (downloading) cancelDownload() else startDownload()
        }
            .bounds(downloadX, buttonY, buttonWidth, buttonHeight)
            .build()
        val paste = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.url_paste")) { pasteClipboard() }
            .bounds(centerX, buttonY, buttonWidth, buttonHeight)
            .build()
        val back = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.pack.back")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(rightX, buttonY, buttonWidth, buttonHeight).build()
        downloadButton = download
        pasteButton = paste
        backButton = back
        addRenderableWidget(download)
        addRenderableWidget(paste)
        addRenderableWidget(back)
        if (downloading) {
            setButtonsForDownload()
        }
    }

    private fun pasteClipboard() {
        val clipboard = Minecraft.getInstance().keyboardHandler.clipboard
        //? if >=26.1 {
        //urlField?.value = clipboard.trim()
        //?}
        //? if <26.1 {
        if (clipboard != null) {
            urlField?.value = clipboard.trim()
        }
        //?}
    }

    private fun startDownload() {
        if (downloading) {
            return
        }
        val url = urlField?.value?.trim() ?: ""
        if (url.isBlank()) {
            errorText = DlcTextCompat.translatable("dlc_manager.update.url_empty").string
            return
        }
        downloading = true
        cancelRequested = false
        progress = 0f
        downloadedBytes = 0L
        totalBytes = 0L
        errorText = null
        statusText = DlcTextCompat.translatable("dlc_manager.pack.url_downloading").string
        setButtonsForDownload()

        Thread({
            var downloaded: Path? = null
            try {
                downloaded = downloadToDir(url, DlcPaths.dlcRoot().resolve("pack"))
                val finalDownloaded = downloaded
                Minecraft.getInstance().execute {
                    downloading = false
                    Minecraft.getInstance().setScreen(parent)
                    parent.importDownloadedPack(finalDownloaded)
                }
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded)
                    } catch (_: IOException) {
                    }
                }
                Minecraft.getInstance().execute {
                    errorText = DlcTextCompat.translatable("dlc_manager.pack.url_canceled").string
                    downloading = false
                    cancelRequested = false
                    setButtonsActive(true)
                }
            } catch (ex: Exception) {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded)
                    } catch (_: IOException) {
                    }
                }
                val msg = ex.message ?: "download failed"
                Minecraft.getInstance().execute {
                    errorText = DlcTextCompat.translatable("dlc_manager.update.url_failed", msg).string
                    downloading = false
                    cancelRequested = false
                    setButtonsActive(true)
                }
            }
        }, "dlc-pack-url-download").start()
    }

    private fun cancelDownload() {
        if (!downloading) {
            return
        }
        cancelRequested = true
        statusText = DlcTextCompat.translatable("dlc_manager.pack.canceling").string
        downloadButton?.active = false
        downloadButton?.message = DlcTextCompat.translatable("dlc_manager.pack.canceling")
    }

    @Throws(IOException::class)
    private fun downloadToDir(url: String, outputDir: Path): Path {
        Files.createDirectories(outputDir)
        var fileName = DlcDownloadService.suggestFileName(url)
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(url))
        }
        if (fileName.isNullOrBlank()) {
            fileName = "pack.zip"
        } else if (!com.cozooo.dlc_manager.client.pack.DlcPackArchive.isArchiveFileName(fileName)) {
            fileName += ".zip"
        }
        fileName = uniqueFileName(outputDir, fileName)
        totalBytes = 0L
        downloadedBytes = 0L
        return DlcDownloadService.download(
            url,
            outputDir,
            fileName,
            DlcDownloadService.ProgressListener { done, total ->
                statusText = DlcTextCompat.translatable("dlc_manager.pack.url_downloading").string
                downloadedBytes = done
                totalBytes = total
                progress = if (total > 0) done / total.toFloat() else 0f
            },
            DlcDownloadService.CancelChecker { cancelRequested },
        )
    }

    private fun uniqueFileName(dir: Path, fileName: String): String {
        if (!Files.exists(dir.resolve(fileName))) {
            return fileName
        }
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        for (i in 1 until 10000) {
            val candidate = "$base-$i$ext"
            if (!Files.exists(dir.resolve(candidate))) {
                return candidate
            }
        }
        return "$base-${System.nanoTime()}$ext"
    }

    private fun setButtonsActive(active: Boolean) {
        downloadButton?.active = active
        downloadButton?.message = DlcTextCompat.translatable("dlc_manager.update.url_download")
        downloadButton?.visible = true
        urlField?.visible = true
        pasteButton?.active = active
        pasteButton?.visible = true
        backButton?.active = active
        backButton?.visible = true
    }

    private fun setButtonsForDownload() {
        downloadButton?.active = !cancelRequested
        downloadButton?.message = if (cancelRequested) DlcTextCompat.translatable("dlc_manager.pack.canceling") else DlcTextCompat.translatable("dlc_manager.pack.cancel")
        downloadButton?.visible = true
        urlField?.visible = false
        pasteButton?.active = false
        pasteButton?.visible = false
        backButton?.active = false
        backButton?.visible = true
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !downloading
    }

    override fun onClose() {
        if (!downloading) {
            Minecraft.getInstance().setScreen(parent)
        }
    }

    override fun buildPage() {
        urlField?.let { urlValue = it.value }
        initControls()
        renderUrlForm("dlc_manager.pack.url_prompt", urlField, pasteButton, downloadButton,
            { statusText }, { errorText }, { progress.toDouble() }, { if (downloading) "${(progress * 100).toInt()}%" else "" }, { downloading })
    }
    override fun updatePage() { urlField?.active = !downloading }

}
