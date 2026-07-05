package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class DlcUpdateUrlScreen(
    private val parent: Screen?,
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
    private var applyingUpdates = false

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
        setButtonsActive(false)

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
                        val message = DlcMessageAdapter.toImportValidationComponent(validation)
                        showToast(Component.translatable("dlc_manager.update.manager_title"), message)
                        errorText = message.string
                        downloading = false
                        setButtonsActive(true)
                    }
                    return@Thread
                }
                val imported = DlcUpdateService.importUpdatePackage(downloaded)
                if (!imported) {
                    showToast(
                        Component.translatable("dlc_manager.update.manager_title"),
                        Component.translatable("dlc_manager.update.dlc_root_missing"),
                    )
                    Minecraft.getInstance().execute {
                        errorText = Component.translatable("dlc_manager.update.dlc_root_missing").string
                        downloading = false
                        setButtonsActive(true)
                    }
                    return@Thread
                }
                Minecraft.getInstance().execute {
                    downloading = false
                    promptImportedUpdate()
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
        }, "dlc-update-url-download").start()
    }

    private fun promptImportedUpdate() {
        val check = DlcUpdateService.checkForUpdates(true)
        if (check.hasUpdates()) {
            Minecraft.getInstance().setScreen(DlcUpdatePromptScreen(parent, check))
            return
        }
        showToast(
            Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.update.no_updates"),
        )
        Minecraft.getInstance().setScreen(parent)
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

    private fun confirmReupdate(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true),
            CrossPlatformRuntime.languageCode(),
        )
        confirmReupdateNow(baseOld, dlcOld, baseNew, dlcNew, changelog)
    }

    private fun confirmReupdateNow(
        baseOld: String?,
        dlcOld: String?,
        baseNew: String?,
        dlcNew: String?,
        changelog: String?,
    ) {
        val title = DlcConfirmUtil.buildUpdateManagerTitle()
        val message = DlcConfirmUtil.buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew, changelog)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    if (accepted) {
                        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
                        val returnScreen = parent ?: this
                        DlcUpdatePackGuard.confirmBeforeUpdate(returnScreen, chain) { packCheck, uninstallIncompatible ->
                            startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, packCheck, uninstallIncompatible)
                        }
                    } else {
                        Minecraft.getInstance().setScreen(parent)
                    }
                },
                title,
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            ),
        )
    }

    private fun startReupdate(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
        startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, null, false)
    }

    private fun startReupdate(
        baseOld: String?,
        dlcOld: String?,
        baseNew: String?,
        dlcNew: String?,
        chain: MutableList<DlcUpdatePackage?>,
        packCheck: DlcPackUpdateGuard.CheckResult?,
        uninstallIncompatible: Boolean,
    ) {
        if (chain.isEmpty()) {
            Minecraft.getInstance().setScreen(
                DlcUpdateResultScreen(parent, false, Component.translatable("dlc_manager.update.failed")),
            )
            return
        }
        setApplyingUpdates(true)
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val result = DlcUpdateService.applyUpdates(chain, false)
                if (result?.isSuccess == true) {
                    packSession.finishAfterSuccessfulUpdate()
                } else {
                    packSession.restoreAfterFailedUpdate()
                }
                Minecraft.getInstance().execute { handleReupdateResult(result, null) }
            } catch (ex: Exception) {
                packSession?.let {
                    try {
                        it.restoreAfterFailedUpdate()
                    } catch (restoreError: Exception) {
                        ex.addSuppressed(restoreError)
                    }
                }
                val message = ex.message ?: "Failed to apply update"
                Minecraft.getInstance().execute {
                    setApplyingUpdates(false)
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, Component.literal(message)))
                }
            }
        }, "dlc-update-url-apply").start()
    }

    private fun handleReupdateResult(result: UpdateApplyResult?, check: UpdateCheckResult?) {
        setApplyingUpdates(false)
        if (result?.isMissing == true) {
            val missingCheck = check ?: DlcUpdateService.checkForUpdates(true)
            Minecraft.getInstance().setScreen(DlcUpdateMissingScreen(parent, missingCheck, result.missingPaths))
            return
        }
        if (result?.isSuccess != true) {
            val component = DlcMessageAdapter.toFailureComponent(result)
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, component))
            return
        }
        if (tryShowPostUpdateDownloads(result)) {
            return
        }
        val message = if (result.isHelperScheduled) {
            Component.translatable("dlc_manager.update.deferred_restart")
        } else {
            Component.translatable("dlc_manager.update.success")
        }
        Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, true, message))
    }

    private fun setApplyingUpdates(applying: Boolean) {
        applyingUpdates = applying
        setButtonsActive(!applying)
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !applyingUpdates
    }

    override fun onClose() {
        if (!applyingUpdates) {
            super.onClose()
        }
    }

    private fun tryShowPostUpdateDownloads(result: UpdateApplyResult?): Boolean {
        if (result == null || !result.isSuccess) {
            return false
        }
        if (result.hasRequiredAdd()) {
            val missing = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(parent, missing, result))
                return true
            }
        }
        if (result.hasRemoteAdd()) {
            val missing = DlcRemoteManager.collectMissing()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(createSuccessResultScreen(result), missing))
                return true
            }
            return false
        }
        return false
    }

    private fun createSuccessResultScreen(result: UpdateApplyResult?): Screen {
        val message = if (result != null && result.isHelperScheduled) {
            Component.translatable("dlc_manager.update.deferred_restart")
        } else {
            Component.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(parent, true, message)
    }

    private fun showToast(title: Component, desc: Component) {
        val minecraft = Minecraft.getInstance()
        DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.toasts, title, desc)
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
