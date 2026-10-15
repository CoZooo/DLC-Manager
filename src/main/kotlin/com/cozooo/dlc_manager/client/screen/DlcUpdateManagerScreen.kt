package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.core.DlcFilePicker
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner
import com.cozooo.dlc_manager.client.update.DlcUpdateRuntime
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateRollbackResult
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Path
import kotlin.math.max

class DlcUpdateManagerScreen(
    private val parent: Screen?,
) : DlcUpdatePageScreen(DlcUiPage.LOCAL_UPDATES, parent, DlcTextCompat.translatable("dlc_manager.update.manager_title")) {
    private var importButton: Button? = null
    private var urlButton: Button? = null
    private var selfUpdateButton: Button? = null
    private var backButton: Button? = null
    private var history: List<DlcUpdateMeta?> = emptyList()
    private val rollbackButtons: MutableList<Button> = ArrayList()
    private var rollbackTargets: List<String?> = emptyList()
    private var rowActions: List<DlcUpdateRowPlanner.Action?> = emptyList()
    private var rowTypes: List<DlcUpdateRowPlanner.Type?> = emptyList()
    private var rowDisplayOld: List<String?> = emptyList()
    private var rowDisplayNew: List<String?> = emptyList()
    private var currentVersion: String? = null
    private var baseVersion: String? = null
    private var rollbackFrom: String? = null
    private var rolledBackMode = false
    private var updateCheck: UpdateCheckResult? = null
    private var hasVisibleUpdates = false
    private var updateStatus = ""
    private var applyingUpdates = false

    private fun initControls() {
        refreshData()
    }

    fun refreshData() {
        val snapshot = DlcUpdateRuntime.loadClientSnapshot(DlcPaths.dlcRoot())
        currentVersion = snapshot.currentVersion
        baseVersion = snapshot.baseVersion
        rollbackFrom = snapshot.rollbackFrom
        rolledBackMode = snapshot.rolledBackMode
        updateCheck = snapshot.updateCheck
        hasVisibleUpdates = false
        val selfHistory = DlcSelfUpdateService.loadHistory()
        val plan = DlcUpdateRowPlanner.planClientRows(
            snapshot.history,
            selfHistory,
            updateCheck,
            rolledBackMode,
            baseVersion,
            currentVersion,
        )
        val rows: MutableList<DlcUpdateMeta?> = ArrayList(plan.rows.size)
        val targets: MutableList<String?> = ArrayList(plan.rows.size)
        val actions: MutableList<DlcUpdateRowPlanner.Action?> = ArrayList(plan.rows.size)
        val types: MutableList<DlcUpdateRowPlanner.Type?> = ArrayList(plan.rows.size)
        val displayOld: MutableList<String?> = ArrayList(plan.rows.size)
        val displayNew: MutableList<String?> = ArrayList(plan.rows.size)
        for (row in plan.rows) {
            rows.add(row.meta)
            targets.add(row.rollbackTarget)
            actions.add(row.action)
            types.add(row.type)
            displayOld.add(row.displayOldVersion)
            displayNew.add(row.displayNewVersion)
        }
        hasVisibleUpdates = plan.hasVisibleUpdates
        history = rows
        rollbackTargets = targets
        rowActions = actions
        rowTypes = types
        rowDisplayOld = displayOld
        rowDisplayNew = displayNew
        buildWidgets()
    }

    private fun buildWidgets() {
        clearWidgets()
        var buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val spacing = 10
        val maxWidth = width - 40
        val idealWidth = (maxWidth - spacing * 3) / 4
        if (idealWidth > 80 && idealWidth < buttonWidth) {
            buttonWidth = idealWidth
        }
        val totalWidth = buttonWidth * 4 + spacing * 3
        val startX = max(20, (width - totalWidth) / 2)
        val import = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.import")) { importUpdate() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        val urlX = startX + buttonWidth + spacing
        val url = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.import_url")) {
            Minecraft.getInstance().setScreen(DlcUpdateUrlScreen(this))
        }.bounds(urlX, y, buttonWidth, buttonHeight).build()
        val selfX = urlX + buttonWidth + spacing
        val self = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.pack_update.check_button")) { checkPackUpdate() }
            .bounds(selfX, y, buttonWidth, buttonHeight).build()
        self.active = hasPackUpdateRepoConfigured()
        val backX = selfX + buttonWidth + spacing
        val back = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build()
        importButton = import
        urlButton = url
        selfUpdateButton = self
        backButton = back
        addRenderableWidget(import)
        addRenderableWidget(url)
        addRenderableWidget(self)
        addRenderableWidget(back)
        rollbackButtons.clear()
        for (i in history.indices) {
            val action = if (i < rowActions.size) rowActions[i] else DlcUpdateRowPlanner.Action.NONE
            val label = when (action) {
                DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK -> DlcTextCompat.translatable("dlc_manager.update.rollback")
                DlcUpdateRowPlanner.Action.REUPDATE -> DlcTextCompat.translatable("dlc_manager.update.reapply")
                DlcUpdateRowPlanner.Action.UPDATE -> DlcTextCompat.translatable("dlc_manager.update.apply")
                else -> DlcTextCompat.translatable("dlc_manager.update.rollback")
            }
            val buttonW = if (action == DlcUpdateRowPlanner.Action.REUPDATE || action == DlcUpdateRowPlanner.Action.UPDATE) 80 else 50
            val rollback = DlcScreenCompat.buttonBuilder(label) { btn ->
                val idx = rollbackButtons.indexOf(btn)
                if (idx >= 0 && idx < rowActions.size) {
                    when (rowActions[idx]) {
                        DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK -> {
                            val target = rollbackTargets[idx]
                            if (!target.isNullOrBlank()) {
                                confirmManagerRollback(target)
                            }
                        }
                        DlcUpdateRowPlanner.Action.ROLLBACK -> {
                            val target = rollbackTargets[idx]
                            if (!target.isNullOrBlank()) {
                                confirmRollback(target)
                            }
                        }
                        DlcUpdateRowPlanner.Action.REUPDATE -> {
                            val meta = if (idx < history.size) history[idx] else null
                            if (meta != null) {
                                confirmReupdate(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew())
                            }
                        }
                        DlcUpdateRowPlanner.Action.UPDATE -> {
                            val meta = if (idx < history.size) history[idx] else null
                            if (meta != null) {
                                confirmApply(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew())
                            }
                        }
                        else -> Unit
                    }
                }
            }.bounds(-10000, -10000, buttonW, 16).build()
            rollback.visible = false
            addRenderableWidget(rollback)
            rollbackButtons.add(rollback)
        }
    }

    private fun formatUpdateRowLabel(
        sourceBase: String?,
        sourceDlc: String?,
        targetBase: String?,
        targetDlc: String?,
        oldDisplay: String?,
        newDisplay: String?,
    ): String {
        val baseChanged = normalizeVersion(sourceBase) != normalizeVersion(targetBase)
        val dlcChanged = normalizeVersion(sourceDlc) != normalizeVersion(targetDlc)
        if (baseChanged && !dlcChanged) {
            return DlcTextCompat.translatable("dlc_manager.update.base_prefix").string +
                normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase)
        }
        if (!baseChanged && dlcChanged) {
            return DlcTextCompat.translatable("dlc_manager.update.dlc_prefix").string +
                normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc)
        }
        return "$oldDisplay -> $newDisplay"
    }

    private fun startRollback(targetVersion: String?) {
        if (targetVersion.isNullOrBlank()) {
            return
        }
        importButton?.active = false
        backButton?.active = false
        Thread({
            val result: UpdateRollbackResult = DlcUpdateService.rollbackTo(targetVersion)
            Minecraft.getInstance().execute {
                if (!result.isSuccess) {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.rollback_failed")))
                } else {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, true, DlcTextCompat.translatable("dlc_manager.update.rollback_success")))
                }
            }
        }, "dlc-update-rollback").start()
    }

    private fun startManagerRollback(targetVersion: String?) {
        if (targetVersion.isNullOrBlank()) {
            return
        }
        setApplyingUpdates(true)
        Thread({
            val ok = DlcSelfUpdateService.rollbackToPrevious()
            Minecraft.getInstance().execute {
                setApplyingUpdates(false)
                if (!ok) {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.rollback_failed")))
                } else {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, true, DlcTextCompat.translatable("dlc_manager.update.rollback_success")))
                }
            }
        }, "dlc-selfupdate-rollback").start()
    }

    private fun confirmRollback(targetVersion: String) {
        val title = DlcConfirmUtil.buildUpdateManagerTitle()
        val message = DlcConfirmUtil.buildUpdateConfirmRollbackMessage(targetVersion)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    if (accepted) {
                        startRollback(targetVersion)
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            ),
        )
    }

    private fun confirmManagerRollback(targetVersion: String) {
        val title = DlcConfirmUtil.buildUpdateManagerTitle()
        val message = DlcConfirmUtil.buildUpdateConfirmRollbackMessage(targetVersion)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    if (accepted) {
                        startManagerRollback(targetVersion)
                    } else {
                        Minecraft.getInstance().setScreen(this)
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

    private fun startApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
        startApply(baseOld, dlcOld, baseNew, dlcNew, chain, null, false)
    }

    private fun confirmReupdate(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true),
            DlcPlatformServices.languageCode(),
        )
        confirmReupdateNow(baseOld, dlcOld, baseNew, dlcNew, changelog)
    }

    private fun confirmApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val title = DlcConfirmUtil.buildUpdateManagerTitle()
        val message = DlcConfirmUtil.buildUpdateConfirmApplyMessage()
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    if (accepted) {
                        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
                        DlcUpdatePackGuard.confirmBeforeUpdate(this, chain) { packCheck, uninstallIncompatible ->
                            startApply(baseOld, dlcOld, baseNew, dlcNew, chain, packCheck, uninstallIncompatible)
                        }
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            ),
        )
    }

    private fun confirmReupdateNow(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?, changelog: String?) {
        val title = DlcConfirmUtil.buildUpdateManagerTitle()
        val message = DlcConfirmUtil.buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew, changelog)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    if (accepted) {
                        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
                        DlcUpdatePackGuard.confirmBeforeUpdate(this, chain) { packCheck, uninstallIncompatible ->
                            startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, packCheck, uninstallIncompatible)
                        }
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            ),
        )
    }

    private fun handleReupdateResult(result: UpdateApplyResult?, check: UpdateCheckResult?) {
        setApplyingUpdates(false)
        if (result?.isMissing == true) {
            val missingCheck = check ?: DlcUpdateService.checkForUpdates(true)
            Minecraft.getInstance().setScreen(DlcUpdateMissingScreen(this, missingCheck, result.missingPaths))
            return
        }
        if (result?.isSuccess != true) {
            val component = DlcMessageAdapter.toFailureComponent(result)
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, component))
            return
        }
        if (tryShowPostUpdateDownloads(result)) {
            return
        }
        val message = if (result.isHelperScheduled) {
            DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
        } else {
            DlcTextCompat.translatable("dlc_manager.update.success")
        }
        Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, true, message))
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
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.failed")))
            return
        }
        setApplyingUpdates(true)
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val result = DlcUpdateService.applyUpdates(chain, false) { status, _ ->
                    Minecraft.getInstance().execute { updateStatus = DlcMessageAdapter.toProgressText(status) }
                }
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
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, DlcTextCompat.literal(message)))
                }
            }
        }, "dlc-update-reapply").start()
    }

    private fun startApply(
        baseOld: String?,
        dlcOld: String?,
        baseNew: String?,
        dlcNew: String?,
        chain: MutableList<DlcUpdatePackage?>,
        packCheck: DlcPackUpdateGuard.CheckResult?,
        uninstallIncompatible: Boolean,
    ) {
        if (chain.isEmpty()) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.failed")))
            return
        }
        setApplyingUpdates(true)
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val result = DlcUpdateService.applyUpdates(chain, false) { status, _ ->
                    Minecraft.getInstance().execute { updateStatus = DlcMessageAdapter.toProgressText(status) }
                }
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
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, DlcTextCompat.literal(message)))
                }
            }
        }, "dlc-update-apply").start()
    }

    private fun tryShowPostUpdateDownloads(result: UpdateApplyResult?): Boolean {
        if (result == null || !result.isSuccess) {
            return false
        }
        if (result.hasRequiredAdd()) {
            val missing: List<DlcRequiredEntry> = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(this, missing, result))
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
            DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
        } else {
            DlcTextCompat.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(this, true, message)
    }

    private fun setApplyingUpdates(applying: Boolean) {
        applyingUpdates = applying
        importButton?.active = !applying
        urlButton?.active = !applying
        backButton?.active = !applying
        for (button in rollbackButtons) {
            button.active = !applying
        }
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !applyingUpdates
    }

    override fun onClose() {
        if (!applyingUpdates) {
            super.onClose()
        }
    }

    private fun importUpdate() {
        val picked: Path = DlcFilePicker.pickUpdateZip("Import Update") ?: return
        val validation = DlcUpdateService.validateImportPackage(picked)
        if (!validation.isValid) {
            showToast(
                DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                DlcMessageAdapter.toImportValidationComponent(validation),
            )
            return
        }
        if (!DlcUpdateService.importUpdatePackage(picked)) {
            showToast(
                DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                DlcTextCompat.translatable("dlc_manager.update.dlc_root_missing"),
            )
            return
        }
        promptImportedUpdate()
    }

    private fun promptImportedUpdate() {
        val check = DlcUpdateService.checkForUpdates(true)
        if (check.hasUpdates()) {
            Minecraft.getInstance().setScreen(DlcUpdatePromptScreen(this, check))
            return
        }
        showToast(
            DlcTextCompat.translatable("dlc_manager.update.manager_title"),
            DlcTextCompat.translatable("dlc_manager.update.no_updates"),
        )
        refreshData()
    }

    private fun checkPackUpdate() {
        val repoUrl = DlcConfig.getUpdateGitRepoUrl()
        if (repoUrl.isNullOrBlank()) {
            return
        }
        selfUpdateButton?.active = false
        val base = if (baseVersion.isNullOrBlank()) "1.0.0" else baseVersion!!
        val dlc = if (currentVersion.isNullOrBlank()) "1.0.0" else currentVersion!!
        Thread({
            val result = try {
                DlcPackReleaseUpdateService.checkForUpdates(
                    DlcConfig.getModpackUrl(),
                    repoUrl,
                    base,
                    dlc,
                    DlcPackReleaseUpdateService.Side.CLIENT,
                )
            } catch (_: Exception) {
                null
            }
            Minecraft.getInstance().execute {
                selfUpdateButton?.active = true
                if (result != null && result.hasUpdate()) {
                    Minecraft.getInstance().setScreen(DlcModpackUpdateScreen(this, result))
                    return@execute
                }
                if (result != null && !result.error.isNullOrBlank()) {
                    showToast(
                        DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                        DlcMessageAdapter.toCheckFailureComponent(result.error),
                    )
                    return@execute
                }
                showToast(
                    DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                    DlcTextCompat.translatable("dlc_manager.pack_update.no_updates"),
                )
            }
        }, "dlc-pack-update-check-manual").start()
    }

    private fun hasPackUpdateRepoConfigured(): Boolean {
        val repoUrl = DlcConfig.getUpdateGitRepoUrl()
        return !repoUrl.isNullOrBlank()
    }

    private fun showToast(title: Component, desc: Component) {
        val minecraft = Minecraft.getInstance()
        DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(minecraft), title, desc)
    }

    companion object {
        private fun normalizeVersion(value: String?): String {
            return if (value.isNullOrBlank()) "1.0.0" else value.trim()
        }

        private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
            val sourceBase = normalizeVersion(oldBase)
            val sourceDlc = normalizeVersion(oldDlc)
            val targetBase = normalizeVersion(if (newBase.isNullOrBlank()) sourceBase else newBase)
            val targetDlc = normalizeVersion(if (newDlc.isNullOrBlank()) sourceDlc else newDlc)
            val baseChanged = sourceBase != targetBase
            val dlcChanged = sourceDlc != targetDlc
            if (baseChanged && dlcChanged) {
                return arrayOf("$sourceBase+$sourceDlc", "$targetBase+$targetDlc")
            }
            if (baseChanged) {
                return arrayOf(sourceBase, targetBase)
            }
            if (dlcChanged) {
                return arrayOf(sourceDlc, targetDlc)
            }
            return arrayOf("$sourceBase+$sourceDlc", "$targetBase+$targetDlc")
        }
    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 31 * history.hashCode() + rollbackButtons.hashCode() + applyingUpdates.hashCode()

        live(content, { if (applyingUpdates) updateStatus else "" }, "page-description")
        val list = updateSection(baseVersion, currentVersion)
        if (rolledBackMode) text(list, tr("dlc_manager.update.rollback_info", rollbackFrom.orEmpty(), normalizeVersion(baseVersion) + "+" + normalizeVersion(currentVersion)), "page-warning")
        if (hasVisibleUpdates) text(list, tr("dlc_manager.update.desc"), "page-description")
        val updates = mutableListOf<UpdateRow>()
        for ((index, meta) in history.withIndex()) {
            if (meta == null) continue
            var label = formatUpdateRowLabel(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew(), rowDisplayOld[index], rowDisplayNew[index])
            if (rowTypes[index] == DlcUpdateRowPlanner.Type.MANAGER) label = tr("dlc_manager.update.manager_prefix") + label.removePrefix(tr("dlc_manager.update.dlc_prefix"))
            val model = rollbackButtons.getOrNull(index)
            model?.let { it.visible = rowActions[index] != DlcUpdateRowPlanner.Action.NONE; it.active = it.visible && !applyingUpdates }
            updates.add(UpdateRow(label, if (rowActions[index] == DlcUpdateRowPlanner.Action.REUPDATE) tr("dlc_manager.update.rolled_back") else null,
                meta.resolveChangelog(DlcPlatformServices.languageCode()), model))
        }
        renderUpdates(list, updates, true)
        renderUpdateActions(importButton, urlButton, selfUpdateButton)

    }
    override fun updatePage() {

        if (pageRevision != 31 * history.hashCode() + rollbackButtons.hashCode() + applyingUpdates.hashCode()) refreshPage()
    }

}
