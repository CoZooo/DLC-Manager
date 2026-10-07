package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcUpdatePromptScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val result: UpdateCheckResult?,
    private val allowSkipThisUpdate: Boolean = false,
) : DlcPageScreen(parent, Component.translatable("dlc_manager.update.title")) {
    private val forceUpdate = hasHiddenUpdates(result)
    private var applyButton: Button? = null
    private var skipThisUpdateButton: Button? = null
    private var cancelButton: Button? = null
    private var scrollIndex = 0
    private var applying = false
    private var statusText: String? = null
    private var progress = -1f

    private fun initControls() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        if (forceUpdate) {
            val apply = Button.builder(Component.translatable("dlc_manager.update.apply")) { confirmApply() }
                .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build()
            applyButton = apply
            skipThisUpdateButton = null
            cancelButton = null
            addRenderableWidget(apply)
        } else if (allowSkipThisUpdate) {
            val spacing = 6
            val skipButtonWidth = 108
            val totalWidth = buttonWidth + skipButtonWidth + buttonWidth + spacing * 2
            val startX = width / 2 - totalWidth / 2
            val apply = Button.builder(Component.translatable("dlc_manager.update.apply")) { confirmApply() }
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build()
            val skip = Button.builder(Component.translatable("dlc_manager.self_update.skip_this_update")) { skipThisUpdate() }
                .bounds(startX + buttonWidth + spacing, y, skipButtonWidth, buttonHeight)
                .build()
            val cancel = Button.builder(Component.translatable("dlc_manager.update.cancel")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(startX + buttonWidth + spacing + skipButtonWidth + spacing, y, buttonWidth, buttonHeight)
                .build()
            applyButton = apply
            skipThisUpdateButton = skip
            cancelButton = cancel
            addRenderableWidget(apply)
            addRenderableWidget(skip)
            addRenderableWidget(cancel)
        } else {
            val apply = Button.builder(Component.translatable("dlc_manager.update.apply")) { confirmApply() }
                .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
                .build()
            val cancel = Button.builder(Component.translatable("dlc_manager.update.cancel")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
                .build()
            applyButton = apply
            cancelButton = cancel
            skipThisUpdateButton = null
            addRenderableWidget(apply)
            addRenderableWidget(cancel)
        }
    }

    private fun skipThisUpdate() {
        if (result != null && result.hasUpdates()) {
            DlcUpdateService.skipPromptForUpdate(result)
        }
        Minecraft.getInstance().setScreen(parent)
    }

    private fun confirmApply() {
        val title = Component.translatable("dlc_manager.update.title")
        val message = DlcConfirmUtil.buildUpdateConfirmApplyMessage()
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen({ accepted ->
                if (accepted) {
                    DlcUpdatePackGuard.confirmBeforeUpdate(this, result?.updates) { packCheck, uninstallIncompatible ->
                        startApply(false, packCheck, uninstallIncompatible)
                    }
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun formatUpdateLine(pkg: DlcUpdatePackage?): String {
        if (pkg?.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return ""
        }
        val version = pkg.getManifest().getVersion() ?: return ""
        val oldBase = normalizeVersion(version.getBase())
        val oldDlc = normalizeVersion(version.getDlcOld())
        var newBase = normalizeVersion(version.getBaseNew())
        var newDlc = normalizeVersion(version.getDlcNew())
        if (newBase.isBlank()) {
            newBase = oldBase
        }
        if (newDlc.isBlank()) {
            newDlc = oldDlc
        }
        val compact = compactTransition(oldBase, oldDlc, newBase, newDlc)
        val from = compact[0]
        val to = compact[1]
        if (from == to) {
            return from
        }
        return "$from -> $to"
    }

    private fun formatVersionPair(base: String?, dlc: String?): String {
        return normalizeVersion(base) + "+" + normalizeVersion(dlc)
    }

    private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
        val sourceBase = normalizeVersion(oldBase)
        val sourceDlc = normalizeVersion(oldDlc)
        val targetBase = normalizeVersion(newBase)
        val targetDlc = normalizeVersion(newDlc)
        val baseChanged = sourceBase != targetBase
        val dlcChanged = sourceDlc != targetDlc
        if (baseChanged && dlcChanged) {
            return arrayOf(formatVersionPair(sourceBase, sourceDlc), formatVersionPair(targetBase, targetDlc))
        }
        if (baseChanged) {
            return arrayOf(sourceBase, targetBase)
        }
        if (dlcChanged) {
            return arrayOf(sourceDlc, targetDlc)
        }
        return arrayOf(formatVersionPair(sourceBase, sourceDlc), formatVersionPair(targetBase, targetDlc))
    }

    private fun normalizeVersion(value: String?): String {
        return if (value == null || value.isBlank()) "1.0.0" else value.trim()
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !forceUpdate && !applying
    }

    override fun onClose() {
        if (!applying) {
            super.onClose()
        }
    }

    private fun startApply(
        forceMissing: Boolean,
        packCheck: DlcPackUpdateGuard.CheckResult?,
        uninstallIncompatible: Boolean,
    ) {
        if (applying) {
            return
        }
        applying = true
        statusText = Component.translatable("dlc_manager.update.applying").string
        progress = -1f
        applyButton?.active = false
        cancelButton?.active = false
        skipThisUpdateButton?.active = false
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val applyResult = DlcUpdateService.applyUpdates(result?.updates, forceMissing) { status, value ->
                    Minecraft.getInstance().execute {
                        statusText = status
                        progress = value
                    }
                }
                if (applyResult != null && applyResult.isSuccess) {
                    packSession.finishAfterSuccessfulUpdate()
                } else {
                    packSession.restoreAfterFailedUpdate()
                }
                Minecraft.getInstance().execute { handleApplyResult(applyResult) }
            } catch (ex: Exception) {
                val session = packSession
                if (session != null) {
                    try {
                        session.restoreAfterFailedUpdate()
                    } catch (restoreError: Exception) {
                        ex.addSuppressed(restoreError)
                    }
                }
                val message = ex.message ?: "Failed to apply update"
                Minecraft.getInstance().execute {
                    applying = false
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, Component.literal(message)))
                }
            }
        }, "dlc-update-apply").start()
    }

    private fun handleApplyResult(result: UpdateApplyResult?) {
        if (result != null && result.isMissing) {
            val updateResult = this.result ?: return
            Minecraft.getInstance().setScreen(DlcUpdateMissingScreen(parent, updateResult, result.missingPaths))
            return
        }
        if (result == null || !result.isSuccess) {
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

    private fun tryShowPostUpdateDownloads(result: UpdateApplyResult?): Boolean {
        if (result == null || !result.isSuccess) {
            return false
        }
        if (result.hasRequiredAdd()) {
            val missing: List<DlcRequiredEntry> = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(parent, missing, result))
                return true
            }
        }
        if (result.hasRemoteAdd()) {
            val missing: List<DlcRemoteEntry> = DlcRemoteManager.collectMissing()
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

    companion object {
        private fun hasHiddenUpdates(result: UpdateCheckResult?): Boolean {
            if (result?.updates == null) {
                return false
            }
            for (pkg in result.updates) {
                if (pkg?.getManifest() != null && pkg.getManifest().isHidden()) {
                    return true
                }
            }
            return false
        }
    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 0

        val list = section(null, tr("dlc_manager.update.desc"))
        for (pkg in result?.updates.orEmpty()) { if (pkg == null) continue; val part = node(list, "section", "page-section"); text(part, formatUpdateLine(pkg), "section-title"); pkg.getManifest()?.let { markdown(part, it.resolveChangelog(DlcPlatformServices.languageCode())) } }
        live(content, { statusText }, "page-description"); progress(content) { if (applying) progress.toDouble() else 0.0 }
        cancelButton?.let { mount(footer, it) }; skipThisUpdateButton?.let { mount(footer, it) }; applyButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }

    }
    override fun updatePage() {
        applyButton?.active = !applying; cancelButton?.active = !applying; skipThisUpdateButton?.active = !applying
        if (pageRevision != 0) refreshPage()
    }

}
