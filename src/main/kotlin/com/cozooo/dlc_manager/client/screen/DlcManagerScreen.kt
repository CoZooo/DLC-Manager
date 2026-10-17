package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig
import com.cozooo.dlc_manager.client.notice.DlcNoticeManager
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.core.DlcApplyMode
import com.cozooo.dlc_manager.core.DlcApplier
import com.cozooo.dlc_manager.core.DlcStateConfig
import com.cozooo.dlc_manager.core.DlcConfigTypeSupport
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcFolderMetadata
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcRuntimeState
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20.1 {
//import com.mojang.blaze3d.vertex.PoseStack
//?}
//? if >=26.1 {
//import net.minecraft.client.gui.GuiGraphicsExtractor
//?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.PackRepository
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element

class DlcManagerScreen(private val parent: Screen?) : DlcManagerPageScreen(DlcUiPage.MANAGER), DlcReturnPositionHost {
    private val rows: MutableList<DlcListRow> = ArrayList()
    private val items: MutableList<DlcItem> = ArrayList()
    private val allItems: MutableList<DlcItem> = ArrayList()
    private var applyMode = DlcApplyMode.RESTART_ONLY
    private var hasModItems = false
    private var hasModChanges = false
    private var userSelectedDlcApplyMode = false
    private var noticeChecked = false
    private var activeFilter: DlcType? = null
    private val hasTaczMod = DlcPlatformServices.isModLoaded("tacz")
    private val presetSnapshot: MutableSet<String> = LinkedHashSet()
    private var presetSnapshotCaptured = false
    private var modeButton: DlcUiButton? = null
    private var backButton: DlcUiButton? = null
    private var enableAllButton: DlcUiButton? = null
    private var disableAllButton: DlcUiButton? = null
    private var pendingScrollRestore = false
    private var savedScrollOffset = 0.0
    private var savedScrollFilter: DlcType? = null


    private fun canShowServerButton(): Boolean = DlcConfig.isServerSupportOn()

    private fun rebuildRows() {
        rows.clear()
        items.clear()
        allItems.clear()
        hasModItems = false
        hasModChanges = false
        userSelectedDlcApplyMode = false
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.reload()
        } else {
            DlcPresetRuntimeState.clearAll()
        }
        if (activeFilter == null) {
            appendRedirectRows()
        }
        val grouped = DlcScanner().scan()
        val folders = DlcFolderMetadata(DlcConfig.getDlcRoots(), DlcPaths.gameDir())
        val enabledById: MutableMap<String?, Boolean> = HashMap()
        for (type in DlcType.values()) {
            if (type == DlcType.TACZ_PACK && !hasTaczMod) {
                continue
            }
            val typeItems = grouped[type]?.filterNotNull()
            if (typeItems.isNullOrEmpty()) {
                continue
            }
            if (type == DlcType.MOD) {
                hasModItems = true
            }
            for (item in typeItems) {
                val enabled = item.isEnabled()
                enabledById[item.getId()] = enabled
                DlcRuntimeState.ensureBaseline(item.getId(), enabled)
                DlcRuntimeState.getOverride(item.getId())?.let { item.setEnabled(it) }
                allItems.add(item)
            }
            if (activeFilter != null && type != activeFilter) {
                continue
            }
            val visibleItems = typeItems.filter { !it.isHidden() }
            if (visibleItems.isEmpty()) {
                continue
            }
            if (activeFilter == null) {
                rows.add(DlcListRow.groupHeader(type))
            }
            val byPath = folders.group(visibleItems, DlcPlatformServices.languageCode())
            val hasSubDir = byPath.any { !it.isRoot() }
            var firstPathHeader = true
            for (group in byPath) {
                if (!group.isRoot() || hasSubDir) {
                    val label = if (group.isRoot()) translate("dlc_manager.group.root") else group.label
                    rows.add(DlcListRow.pathHeader(label, group.items, group.isRoot(), firstPathHeader, group.key))
                    firstPathHeader = false
                }
                for (item in group.items) {
                    val enabled = enabledById[item.getId()] ?: item.isEnabled()
                    DlcRuntimeState.ensureBaseline(item.getId(), enabled)
                    DlcRuntimeState.getOverride(item.getId())?.let { item.setEnabled(it) }
                    item.setDirty(isItemModified(item))
                    rows.add(DlcListRow.item(item))
                    items.add(item)
                }
            }
        }
        DlcRuntimeState.updateItemIndex(allItems)
        DlcPresetRuntimeState.applyOverridesToRuntime()
        hasModChanges = computeModReloadChanges()
        updateHeaderButtonsState()
    }

    private fun appendRedirectRows() {
        val redirectEntries: MutableList<DlcRedirectEntry> = ArrayList()
        for (dlcRoot in DlcConfig.getDlcRoots()) {
            redirectEntries.addAll(DlcRedirectLoader.load(dlcRoot))
        }
        if (redirectEntries.isEmpty()) {
            return
        }
        val lang = Minecraft.getInstance().options.languageCode
        val visible = redirectEntries.filter { !it.isHidden() && it.isAvailableFor(lang) }
        if (visible.isEmpty()) {
            return
        }
        rows.add(DlcListRow.redirectHeader(translate("dlc_manager.redirect.header")))
        for (entry in visible) {
            rows.add(DlcListRow.redirectItem(entry))
        }
    }

    private fun getDlcApplyModeLabel(): String {
        val modeName = translate(applyMode.getLabelKey())
        return translate("dlc_manager.apply_mode_label", modeName)
    }

    private fun syncVisibleItemOverrides() {
        for (item in items) {
            val id = item.getId()
            if (id.isNullOrBlank()) {
                continue
            }
            DlcRuntimeState.setOverride(id, item.isEnabled())
        }
    }

    private fun applyChanges() {
        val applyNeeded = computeApplyChanges()
        var modReloadNeeded = computeModReloadChanges()
        var otherReloadNeeded = computeNonModReloadChanges()
        val requiredPending = DlcRequiredRuntimeState.hasPendingChanges()
        val presetChanged = DlcConfig.isPresetFuncOn() && hasPresetChanges()
        if (!applyNeeded && !modReloadNeeded && !otherReloadNeeded) {
            if (presetChanged) {
                DlcPresetRuntimeState.syncStatusFromRuntime()
            }
            if (requiredPending) {
                showRequiredRestartPrompt()
            } else {
                Minecraft.getInstance().setScreen(parent)
            }
            return
        }
        var applyResult: DlcApplier.ApplyResult? = null
        if (applyNeeded) {
            applyResult = DlcApplier.apply(allItems, applyMode)
            if (applyResult.requiresRestart()) {
                modReloadNeeded = true
            }
            if (applyResult.helperScheduled()) {
                modReloadNeeded = true
                otherReloadNeeded = false
            }
            updateResourcePackSelection()
            if (shouldShowApplyFailureScreen(applyResult)) {
                showApplyFailureScreen(applyResult)
                return
            }
        }
        if (DlcConfig.isPresetFuncOn() && (presetChanged || applyNeeded)) {
            DlcPresetRuntimeState.syncStatusFromRuntime()
        }
        if (!modReloadNeeded && !otherReloadNeeded) {
            if (requiredPending) {
                showRequiredRestartPrompt()
            } else {
                Minecraft.getInstance().setScreen(parent)
            }
            return
        }
        val helperScheduled = applyResult?.helperScheduled() == true
        val message = when {
            helperScheduled -> "dlc_manager.apply.deferred_restart"
            modReloadNeeded -> "dlc_manager.message.restart"
            otherReloadNeeded && applyMode == DlcApplyMode.TRY_HOT_RELOAD -> "dlc_manager.message.reload_prompt"
            else -> "dlc_manager.message.reopen_world"
        }
        val minecraft = Minecraft.getInstance()
        if (applyMode == DlcApplyMode.RESTART_ONLY) {
            minecraft.setScreen(
                DlcConfirmScreen(
                    { accepted -> if (accepted) minecraft.stop() else minecraft.setScreen(parent) },
                    DlcTextCompat.translatable("dlc_manager.title"),
                    DlcTextCompat.translatable(message),
                    DlcTextCompat.translatable("dlc_manager.restart_now"),
                    DlcTextCompat.translatable("dlc_manager.restart_later"),
                )
            )
        } else if (applyMode == DlcApplyMode.TRY_HOT_RELOAD) {
            minecraft.setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        if (accepted) {
                            val future = tryReloadResources()
                            future.thenRun {
                                minecraft.execute {
                                    updateBaselineAfterReload()
                                    minecraft.setScreen(parent)
                                }
                            }
                        } else {
                            minecraft.setScreen(parent)
                        }
                    },
                    DlcTextCompat.translatable("dlc_manager.title"),
                    DlcTextCompat.translatable(message),
                    DlcTextCompat.translatable("dlc_manager.reload_now"),
                    DlcTextCompat.translatable("dlc_manager.reload_later"),
                )
            )
        } else {
            minecraft.setScreen(
                DlcConfirmScreen(
                    { minecraft.setScreen(parent) },
                    DlcTextCompat.translatable("dlc_manager.title"),
                    DlcTextCompat.translatable(message),
                )
            )
        }
    }

    private fun shouldShowApplyFailureScreen(result: DlcApplier.ApplyResult?): Boolean = hasFailureMessage(result)

    private fun hasFailureMessage(result: DlcApplier.ApplyResult?): Boolean {
        val messages = result?.messages() ?: return false
        return messages.any { it.startsWith("Failed to ") }
    }

    private fun showApplyFailureScreen(result: DlcApplier.ApplyResult?) {
        Minecraft.getInstance().setScreen(
            DlcSingleButtonScreen(
                parent,
                DlcTextCompat.translatable("dlc_manager.server.apply_failed"),
                DlcTextCompat.literal(joinFailureMessages(result)),
                DlcTextCompat.translatable("dlc_manager.back"),
            )
        )
    }

    private fun joinFailureMessages(result: DlcApplier.ApplyResult?): String {
        val messages = result?.messages() ?: return ""
        return messages.filter { it.startsWith("Failed to ") }.joinToString("\n")
    }

    private fun updateModeAvailability() {
        val modChanges = computeModReloadChanges()
        val otherChanges = computeNonModReloadChanges()
        hasModChanges = modChanges
        if (modChanges) {
            applyMode = DlcApplyMode.RESTART_ONLY
            userSelectedDlcApplyMode = false
        } else if (otherChanges) {
            if (!userSelectedDlcApplyMode) {
                applyMode = DlcApplyMode.TRY_HOT_RELOAD
            }
        } else if (!userSelectedDlcApplyMode) {
            applyMode = DlcApplyMode.RESTART_ONLY
        }
        modeButton?.setEnabled(!modChanges)
        modeButton?.setDisplayString(getDlcApplyModeLabel())
        updateBackButtonLabel()
    }

    private fun getBackButtonLabel(): String {
        val hasChanges = computeApplyChanges() || hasPresetChanges()
        return if (hasChanges) translate("dlc_manager.back_discard") else translate("dlc_manager.back")
    }

    private fun updateBackButtonLabel() {
        backButton?.setDisplayString(getBackButtonLabel())
        backButton?.setEnabled(true)
    }

    private fun computeModReloadChanges(): Boolean {
        for (item in allItems) {
            if (item.getType() != DlcType.MOD && !DlcConfigTypeSupport.isConfigType(item)) {
                continue
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true
            }
        }
        return false
    }

    private fun computeNonModReloadChanges(): Boolean {
        for (item in allItems) {
            if (item.getType() == DlcType.MOD || DlcConfigTypeSupport.isConfigType(item)) {
                continue
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true
            }
        }
        return false
    }

    private fun isItemModified(item: DlcItem): Boolean = hasPendingApplyChange(item)

    private fun computeApplyChanges(): Boolean {
        if (DlcPresetRuntimeState.hasPendingFileConflictChanges()) {
            return true
        }
        for (item in allItems) {
            if (hasPendingApplyChange(item)) {
                return true
            }
        }
        return false
    }

    private fun hasPendingApplyChange(item: DlcItem?): Boolean {
        if (item == null || item.getId().isNullOrBlank()) {
            return false
        }
        val state = DlcStateConfig.getState(item.getId())
        val appliedEnabled = state != null && state.enabled
        return appliedEnabled != item.isEnabled()
    }

    private fun tryReloadResources(): CompletableFuture<Void> {
        val minecraft = Minecraft.getInstance()
        return try {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                DlcScreenCompat.toastManager(minecraft),
                DlcTextCompat.translatable("dlc_manager.toast.reload_title"),
                DlcTextCompat.translatable("dlc_manager.toast.reload_start"),
            )
            val future = minecraft.reloadResourcePacks()
            future.thenRun {
                minecraft.execute {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(
                        DlcScreenCompat.toastManager(minecraft),
                        DlcTextCompat.translatable("dlc_manager.toast.reload_title"),
                        DlcTextCompat.translatable("dlc_manager.toast.reload_done"),
                    )
                }
            }
            future
        } catch (_: Throwable) {
            CompletableFuture.completedFuture(null)
        }
    }

    private fun updateBaselineAfterReload() {
        for (item in items) {
            if (item.getType() == DlcType.MOD) {
                continue
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled())
        }
    }

    private fun updateResourcePackSelection() {
        val minecraft = Minecraft.getInstance()
        val repo = minecraft.resourcePackRepository
        try {
            repo.reload()
            val selected: MutableSet<String> = LinkedHashSet(repo.selectedIds)
            for (item in items) {
                if (item.getType() != DlcType.RESOURCE_PACK) {
                    continue
                }
                val sourcePath = item.getSourcePath() ?: continue
                val fileName = sourcePath.fileName.toString()
                var packId = resolvePackId(repo, fileName)
                if (item.isEnabled() && packId == null) {
                    val target = java.nio.file.Path.of(minecraft.resourcePackDirectory.toString(), fileName)
                    copyRecursive(sourcePath, target)
                    repo.reload()
                    packId = resolvePackId(repo, fileName)
                }
                if (packId == null) {
                    continue
                }
                if (item.isEnabled()) {
                    selected.add(packId)
                } else {
                    selected.remove(packId)
                }
            }
            repo.setSelected(selected)
            minecraft.options.resourcePacks = ArrayList(selected)
            minecraft.options.save()
        } catch (_: Throwable) {
        }
    }

    private fun resolvePackId(repo: PackRepository, fileName: String): String? {
        val fileId = "file/$fileName"
        if (repo.isAvailable(fileId)) {
            return fileId
        }
        if (repo.isAvailable(fileName)) {
            return fileName
        }
        for (id in repo.availableIds) {
            if (id.endsWith("/$fileName") || id.endsWith(fileName)) {
                return id
            }
        }
        return null
    }

    private fun copyRecursive(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(dir)
                        Files.createDirectories(target.resolve(relative))
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(file)
                        Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } else {
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun updateHeaderButtonsState() {
        val disableButton = disableAllButton ?: return
        val actionItems = getActionItems()
        val any = actionItems.isNotEmpty()
        var anyEligible = false
        var allEligibleEnabled = true
        var allDisabled = true
        for (item in actionItems) {
            if (item.isEnabled()) {
                allDisabled = false
            }
            if (item.AllowEnableByAll() && !DlcRuntimeState.hasMissingDependencies(item)) {
                anyEligible = true
                if (!item.isEnabled()) {
                    allEligibleEnabled = false
                }
            }
            if (!allDisabled && !allEligibleEnabled && anyEligible) {
                break
            }
        }
        val labels = getHeaderLabels()
        val enableButton = enableAllButton
        if (!any) {
            enableButton?.setEnabled(false)
            enableButton?.setDisplayString(labels.enableLabel)
            disableButton.setEnabled(false)
            disableButton.setDisplayString(labels.disableLabel)
            return
        }
        if (enableButton != null) {
            if (!anyEligible) {
                enableButton.setEnabled(false)
                enableButton.setDisplayString(labels.enableLabel)
            } else if (allEligibleEnabled) {
                enableButton.setEnabled(false)
                enableButton.setDisplayString(labels.enableDone)
            } else {
                enableButton.setEnabled(true)
                enableButton.setDisplayString(labels.enableLabel)
            }
        }
        if (allDisabled) {
            disableButton.setEnabled(false)
            disableButton.setDisplayString(labels.disableDone)
        } else {
            disableButton.setEnabled(true)
            disableButton.setDisplayString(labels.disableLabel)
        }
    }

    private fun getActionItems(): List<DlcItem> = allItems.filter {
        (activeFilter == null || it.getType() == activeFilter) &&
            (searchQuery.isBlank() || (!it.isHidden() && matchesItem(it)))
    }

    private fun handleBatchEnable(targets: List<DlcItem>, scopeLabel: Component) {
        val conflicts = DlcRuntimeState.collectConflicts(targets)
        if (conflicts.getLines().isNotEmpty()) {
            val filteredTargets = filterConflictTargets(targets, conflicts)
            showBatchConflictSkipPrompt(conflicts.getLines()) {
                if (filteredTargets.isEmpty()) {
                    updateModeAvailability()
                    return@showBatchConflictSkipPrompt
                }
                continueBatchEnable(filteredTargets, scopeLabel)
            }
            return
        }
        continueBatchEnable(targets, scopeLabel)
    }

    private fun continueBatchEnable(targets: List<DlcItem>?, scopeLabel: Component) {
        if (targets.isNullOrEmpty()) {
            updateModeAvailability()
            return
        }
        val deps = DlcRuntimeState.collectMissingDependencies(targets)
        if (deps.getLines().isNotEmpty()) {
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel)
            return
        }
        val alarmItems = targets.filter { it.isAlarm() }
        if (alarmItems.isNotEmpty()) {
            rememberListScroll()
            val tip = DlcConfirmUtil.buildBatchAlarmTip(alarmItems)
            Minecraft.getInstance().setScreen(
                DlcAlarmConfirmScreen(this, tip) {
                    enableItems(targets, true)
                    refreshAfterStateChange()
                }
            )
            return
        }
        enableItems(targets, true)
        updateModeAvailability()
    }

    private fun filterConflictTargets(targets: List<DlcItem>?, conflicts: DlcRuntimeState.ConflictBatchResult?): List<DlcItem> {
        if (targets.isNullOrEmpty()) {
            return emptyList()
        }
        val targetIds = HashSet<String>()
        for (item in targets) {
            val identifier = item.getIdentifier()
            if (!identifier.isNullOrBlank()) {
                targetIds.add(identifier)
            }
        }
        if (targetIds.isEmpty()) {
            return emptyList()
        }
        if (conflicts == null || conflicts.getLines().isEmpty()) {
            return targets
        }
        val skipped = HashSet<String>()
        for (line in conflicts.getLines()) {
            val left = line.getLeftName()
            val right = line.getRightName()
            if (targetIds.contains(left)) {
                skipped.add(left)
            }
            if (targetIds.contains(right)) {
                skipped.add(right)
            }
        }
        if (skipped.isEmpty()) {
            return targets
        }
        return targets.filter { item ->
            val identifier = item.getIdentifier()
            identifier == null || !skipped.contains(identifier)
        }
    }

    private fun showBatchConflictSkipPrompt(lines: List<DlcRuntimeState.ConflictLine>, onSkipConfirm: Runnable?) {
        val message = DlcConfirmUtil.buildConflictMessage(lines)
        val title = DlcTextCompat.translatable("dlc_manager.batch.conflicts_title").withStyle(ChatFormatting.DARK_RED)
        rememberListScroll()
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (!accepted) {
                        return@DlcConfirmScreen
                    }
                    onSkipConfirm?.run()
                },
                title,
                message,
                DlcTextCompat.translatable("dlc_manager.batch.conflicts_skip_enable"),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun enableItems(items: Collection<DlcItem>?, enabled: Boolean) {
        if (items.isNullOrEmpty()) {
            return
        }
        for (item in items) {
            item.setEnabled(enabled)
            DlcRuntimeState.setOverride(item.getId(), enabled)
        }
    }

    private fun disableItemsWithDependents(items: Collection<DlcItem>?) {
        if (items.isNullOrEmpty()) {
            return
        }
        val toDisable = LinkedHashSet<DlcItem>()
        for (item in items) {
            toDisable.addAll(DlcRuntimeState.collectDependentsRecursive(item))
        }
        enableItems(toDisable, false)
        val hiddenDeps = DlcRuntimeState.collectHiddenDependenciesToDisable(toDisable)
        enableItems(hiddenDeps, false)
    }

    private fun showBatchDependencyPrompt(
        lines: List<DlcRuntimeState.DependencyLine>,
        depsToEnable: List<DlcItem>?,
        targets: List<DlcItem>?,
        scopeLabel: Component,
    ) {
        val message = DlcConfirmUtil.buildDependencyMessage(lines)
        val title = DlcTextCompat.translatable("dlc_manager.batch.dependencies_title")
        val enableLabel = DlcTextCompat.translatable("dlc_manager.batch.enable_deps", scopeLabel)
        rememberListScroll()
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (!accepted) {
                        return@DlcConfirmScreen
                    }
                    val planned = ArrayList<DlcItem>()
                    if (depsToEnable != null) {
                        planned.addAll(depsToEnable)
                    }
                    if (targets != null) {
                        planned.addAll(targets)
                    }
                    val conflicts = DlcRuntimeState.collectConflicts(planned)
                    if (conflicts.getLines().isNotEmpty()) {
                        val filteredTargets = filterConflictTargets(planned, conflicts)
                        if (filteredTargets.isEmpty()) {
                            refreshAfterStateChange()
                            return@DlcConfirmScreen
                        }
                        showBatchConflictSkipPrompt(conflicts.getLines()) {
                            enableItems(filteredTargets, true)
                            refreshAfterStateChange()
                        }
                        return@DlcConfirmScreen
                    }
                    enableItems(planned, true)
                    refreshAfterStateChange()
                },
                title,
                message,
                enableLabel,
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun getHeaderLabels(): HeaderLabels {
        if (activeFilter == null && searchQuery.isBlank()) {
            return HeaderLabels(
                translate("dlc_manager.enable_all"),
                translate("dlc_manager.disable_all"),
                translate("dlc_manager.all_enabled"),
                translate("dlc_manager.all_disabled"),
            )
        }
        val typeName = if (searchQuery.isBlank()) translate(activeFilter!!.getDisplayKey()) else translate("dlc_manager.scope.search")
        return HeaderLabels(
            translate("dlc_manager.enable_all_type", typeName),
            translate("dlc_manager.disable_all_type", typeName),
            translate("dlc_manager.all_enabled_type", typeName),
            translate("dlc_manager.all_disabled_type", typeName),
        )
    }

    private fun returnToMainMenu() {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot)
        }
        DlcRuntimeState.clearOverrides()
        Minecraft.getInstance().setScreen(TitleScreen())
    }

    private fun capturePresetSnapshot() {
        if (presetSnapshotCaptured) {
            return
        }
        if (!DlcConfig.isPresetFuncOn()) {
            presetSnapshot.clear()
            presetSnapshotCaptured = true
            return
        }
        presetSnapshot.clear()
        presetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds())
        presetSnapshotCaptured = true
    }

    private fun hasPresetChanges(): Boolean {
        if (!DlcConfig.isPresetFuncOn() || !presetSnapshotCaptured) {
            return false
        }
        return DlcPresetRuntimeState.getEnabledPresetIds() != presetSnapshot
    }

    private data class HeaderLabels(
        val enableLabel: String,
        val disableLabel: String,
        val enableDone: String,
        val disableDone: String,
    )

    companion object {
        private fun translate(key: String, vararg args: Any): String = plain(DlcTextCompat.translatable(key, *args).string)
        private fun plain(value: String?): String = value?.let { DlcTextFormatter.parse(it).string } ?: ""

        @JvmStatic
        fun refreshCurrentOpenScreen() {
            val minecraft = Minecraft.getInstance()
            val screen = minecraft.screen
            if (screen is DlcManagerScreen) {
                screen.refreshAfterStateChange()
            }
        }

        @JvmStatic
        fun getPendingSuffix(item: DlcItem): String {
            if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return ""
            }
            return if (item.getType() == DlcType.MOD || DlcConfigTypeSupport.isConfigType(item)) {
                translate("dlc_manager.pending.restart")
            } else {
                translate("dlc_manager.pending.reload")
            }
        }
    }

    private fun handleBatchToggle(enable: Boolean) {
        val activeFilterLabel = if (searchQuery.isNotBlank()) DlcTextCompat.translatable("dlc_manager.scope.search") else activeFilter?.let { DlcTextCompat.translatable(it.getDisplayKey()) }
        val message = DlcConfirmUtil.buildHeaderConfirmMessage(enable, activeFilterLabel)
        rememberListScroll()
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this@DlcManagerScreen)
                    if (!accepted) {
                        return@DlcConfirmScreen
                    }
                    val actionItems = getActionItems()
                    if (enable) {
                        val targets = actionItems.filter {
                            it.AllowEnableByAll() && !DlcRuntimeState.hasMissingDependencies(it) && !it.isEnabled()
                        }
                        if (targets.isNotEmpty()) {
                            val scopeLabel = if (searchQuery.isNotBlank()) {
                                DlcTextCompat.translatable("dlc_manager.scope.search")
                            } else if (activeFilter == null) {
                                DlcTextCompat.translatable("dlc_manager.scope.all")
                            } else {
                                DlcTextCompat.translatable("dlc_manager.scope.group")
                            }
                            handleBatchEnable(targets, scopeLabel)
                        }
                    } else {
                        disableItemsWithDependents(actionItems)
                        updateModeAvailability()
                    }
                },
                DlcTextCompat.translatable("dlc_manager.title"),
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    override fun init() {
        if (DlcRemoteManager.tryShowFromManager(this)) return
        if (!noticeChecked) {
            noticeChecked = true
            if (DlcNoticeManager.tryShowFromManager(this)) return
        }
        rebuildRows()
        capturePresetSnapshot()
        super.init()
    }

    override fun bindDocument(document: Document) {
        bindManagerLayout(document, translate("dlc_manager.title"))
        val tools = document.getElementById("manager-tools")
        clearUiContent(tools)
        if (DlcConfig.isUpdateManagerOn() && !DlcConfig.isUButtomShown())
            button(document, tools, translate("dlc_manager.update.manager")) { open { DlcUpdateClient.openUpdateManager(this) } }
        if (DlcConfig.isNoticeDisplayModeManagerButton() && hasNoticeContent())
            button(document, tools, translate("dlc_manager.notice")) { open { DlcNoticeManager.forceShow(this) } }
        if (DlcConfig.isPresetFuncOn())
            button(document, tools, translate("dlc_manager.preset.manager")) { open { Minecraft.getInstance().setScreen(DlcPresetManagerScreen(this)) } }
        if (DlcConfig.isPackFuncOn() && !DlcConfig.isPButtomShown())
            button(document, tools, translate("dlc_manager.pack.manager")) { open { Minecraft.getInstance().setScreen(DlcPackManagerScreen(this)) } }
        if (canShowServerButton())
            button(document, tools, translate("dlc_manager.server.manage")) { open { Minecraft.getInstance().setScreen(DlcServerListScreen(this)) } }
        val context = document.getElementById("manager-context")
        clearUiContent(context)
        if (DlcConfig.isConfigButtonShown())
            button(document, tools, translate("dlc_manager.config")) { open { Minecraft.getInstance().setScreen(DlcConfigScreen(this)) } }

        for (tool in tools.children) tool.setAttribute("class", "button nav-button")
        val batch = document.getElementById("batch-actions")
        clearUiContent(batch)
        if (DlcRemoteManager.hasMissing())
            button(document, batch, translate("dlc_manager.remote.complete")) { open {
                val missing = DlcRemoteManager.collectMissing()
                if (missing.isNotEmpty()) Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(this, missing))
            } }
        enableAllButton = if (DlcConfig.isEnableAllButtonShown()) button(document, batch, "") { handleBatchToggle(true) } else null
        disableAllButton = button(document, batch, "") { handleBatchToggle(false) }
        val footer = document.getElementById("manager-actions")
        clearUiContent(footer)
        clearUiContent(document.getElementById("manager-mode"))
        modeButton = button(document, document.getElementById("manager-mode"), getDlcApplyModeLabel()) { toggleDlcApplyMode() }
        modeButton!!.getElement().setAttribute("class", "button mode-button")
        backButton = button(document, footer, getBackButtonLabel()) { returnToMainMenu() }
        button(document, footer, translate("dlc_manager.apply")) { applyChanges() }.getElement().setAttribute("class", "button primary")
        renderFilters(document)
        renderRows(document)
        updateModeAvailability()
        updateHeaderButtonsState()
        restoreListScroll()
        if (searchQuery.isNotBlank()) onManagerSearch(document, searchQuery)
    }

    private fun hasNoticeContent(): Boolean {
        val notice = DlcNoticeConfig.load()
        return notice.isNoticeEnabled() && notice.hasContent(DlcPlatformServices.languageCode())
    }

    private var searchMatches = emptySet<String>()
    override fun onManagerSearch(document: Document, query: String?) {
        searchQuery = query.orEmpty()
        searchMatches = emptySet()
        savedScrollOffset = 0.0
        val list = document.getElementById("manager-list")
        list.setScrollTop(0.0); list.getStyle().setProperty("pointer-events", "none"); list.setAttribute("aria-busy", "true")
        searchClearButton?.setEnabled(searchQuery.isNotBlank())
        val apply = java.util.function.Consumer<Set<String>> { matches ->
            searchMatches = matches; list.getStyle().removeProperty("pointer-events"); list.removeAttribute("aria-busy")
            renderRows(document); updateHeaderButtonsState()
        }
        if (searchQuery.isBlank()) {
            filterAsync("", emptyList(), apply)
            return
        }
        val entries = allItems.mapTo(ArrayList<DlcUiAsyncSearch.Entry<String>>()) {
            DlcUiAsyncSearch.Entry("item:" + it.getId(), plain(DlcDisplayNameUtil.resolveDisplayName(it)), it.getIdentifier(), it.getSourcePath()?.toString())
        }
        for (row in rows) row.getRedirectEntry()?.let {
            entries.add(DlcUiAsyncSearch.Entry(redirectSearchKey(it), plain(it.resolveDisplayName(DlcPlatformServices.languageCode())), it.getRedirectUrl()))
        }
        updateHeaderButtonsState()
        filterAsync(searchQuery, entries, apply)
    }
    private fun redirectSearchKey(redirect: DlcRedirectEntry): String = "redirect:" + plain(redirect.resolveDisplayName(DlcPlatformServices.languageCode())) + "\n" + redirect.getRedirectUrl()
    private fun matchesItem(item: DlcItem): Boolean = searchQuery.isBlank() || "item:" + item.getId() in searchMatches
    private fun matchesRedirect(redirect: DlcRedirectEntry?): Boolean = redirect != null && (searchQuery.isBlank() || redirectSearchKey(redirect) in searchMatches)

    private fun matchesRow(row: DlcListRow): Boolean = when (row.getType()) {
        DlcListRow.RowType.ITEM -> row.getItem()?.let { matchesItem(it) } ?: false
        DlcListRow.RowType.PATH_HEADER -> row.getItems().orEmpty().any { matchesItem(it) }
        DlcListRow.RowType.GROUP_HEADER -> items.any { it.getType() == row.getGroupType() && matchesItem(it) }
        DlcListRow.RowType.REDIRECT_ITEM -> matchesRedirect(row.getRedirectEntry())
        DlcListRow.RowType.REDIRECT_HEADER -> rows.any { it.getType() == DlcListRow.RowType.REDIRECT_ITEM && matchesRedirect(it.getRedirectEntry()) }
    }

    private fun updatePendingStatus(document: Document) {
        val count = allItems.count { DlcRuntimeState.isBaselineDifferent(it.getId(), it.isEnabled()) }
        val other = hasPresetChanges() || DlcRequiredRuntimeState.hasPendingChanges() || DlcPresetRuntimeState.hasPendingFileConflictChanges()
        val status = document.getElementById("pending-status")
        status.setTextContent(translate(if (count > 0) "dlc_manager.ui.pending" else if (other) "dlc_manager.ui.pending_other" else "dlc_manager.ui.clean", count))
        status.setAttribute("class", if (count > 0 || other) "pending" else "")
    }

    private fun open(action: Runnable) {
        rememberListScroll()
        action.run()
    }

    private fun renderFilters(document: Document) {
        val filters = document.getElementById("manager-filters")
        clearUiContent(filters)
        addFilter(document, filters, null)
        for (type in DlcType.values()) {
            if (type == DlcType.DLC_PACK || (type == DlcType.TACZ_PACK && !hasTaczMod)) continue
            addFilter(document, filters, type)
        }
    }

    private fun addFilter(document: Document, filters: Element, type: DlcType?) {
        val label = translate(type?.getDisplayKey() ?: "dlc_manager.filter.all")
        val filter = button(document, filters, label) { setFilter(type) }
        filter.getElement().setAttribute("class", if (type == activeFilter) "button filter active" else "button filter")
        filter.getElement().setAttribute("aria-pressed", (type == activeFilter).toString())
        filter.getElement().setAttribute("aria-selected", (type == activeFilter).toString())
    }

    private fun setFilter(type: DlcType?) {
        if (activeFilter == type) return
        linkedDocument?.let { captureUiChange(it.getElementById("manager-list")) }
        syncVisibleItemOverrides()
        activeFilter = type
        savedScrollOffset = 0.0
        savedScrollFilter = type
        rebuildRows()
        linkedDocument?.let {
            renderFilters(it)
            renderRows(it)
            restoreListScroll()
        }
        updateModeAvailability()
        updateHeaderButtonsState()
    }

    private fun toggleDlcApplyMode() {
        if (hasModChanges) return
        applyMode = if (applyMode == DlcApplyMode.RESTART_ONLY) DlcApplyMode.TRY_HOT_RELOAD else DlcApplyMode.RESTART_ONLY
        userSelectedDlcApplyMode = true
        updateModeAvailability()
    }

    private fun renderRows(document: Document) {
        tooltip?.hide()
        val list = document.getElementById("manager-list")
        val scroll = list.targetScrollTop
        clearUiContent(list)
        clearManagerGroups()
        var rowParent = list
        var renderedRows = 0
        var renderedDlcCount = 0
        var enabledDlcCount = 0
        for (row in rows) {
            if (!matchesRow(row)) continue
            renderedRows++
            if (row.getType() != DlcListRow.RowType.ITEM) rowParent = list
            val entry = rowParent.appendChild(document.createElement("div"))
            when (row.getType()) {
                DlcListRow.RowType.GROUP_HEADER, DlcListRow.RowType.REDIRECT_HEADER -> {
                    entry.setAttribute("class", "group-heading")
                    entry.setTextContent(row.getGroupType()?.let { translate(it.getDisplayKey()) } ?: plain(row.getLabel()))
                }
                DlcListRow.RowType.PATH_HEADER -> {
                    entry.setAttribute("class", "path-heading")
                    managerText(document, entry, "span", row.getLabel(), "path-label")
                    val groupItems = row.getItems().orEmpty().filter { matchesItem(it) }
                    if (!row.isRootPath() && DlcItemActions.canToggleGroup(groupItems))
                        button(document, entry, DlcItemActions.groupToggleLabel(groupItems)) {
                            rememberListScroll()
                            DlcItemActions.toggleGroup(groupItems)
                            refreshAfterStateChange()
                        }
                    val key = (groupItems.first().getType()?.name ?: "") + ":" + row.getPathKey()
                    rowParent = addManagerGroup(document, list, entry, key, groupItems.size)
                }
                DlcListRow.RowType.REDIRECT_ITEM -> {
                    val redirect = row.getRedirectEntry() ?: continue
                    entry.setAttribute("class", "dlc-row redirect-row")
                    managerText(document, entry, "span", redirect.resolveDisplayName(DlcPlatformServices.languageCode()), "item-name")
                    button(document, entry, translate("dlc_manager.redirect.open")) { open {
                        Minecraft.getInstance().setScreen(DlcRedirectConfirmScreen(this, redirect.getRedirectUrl(), redirect.resolveNotice(DlcPlatformServices.languageCode())))
                    } }
                }
                DlcListRow.RowType.ITEM -> {
                    val item = row.getItem() ?: continue
                    renderedDlcCount++
                    if (item.isEnabled()) enabledDlcCount++
                    val dirty = DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())
                    val name = styleManagerRow(document, entry, DlcDisplayNameUtil.resolveDisplayName(item), item.isEnabled(), dirty)
                    tooltip?.bind(entry) { itemTooltip(item) }
                    if (!item.getRedirectUrl().isNullOrBlank()) {
                        val generation = document.refreshGeneration
                        name.addEventListener("dblclick") {
                            if (Minecraft.getInstance().screen === this && document.isCurrentGeneration(generation))
                                open { Minecraft.getInstance().setScreen(DlcRedirectConfirmScreen(this, item.getRedirectUrl()!!, "")) }
                        }
                    }
                    if (dirty) managerText(document, entry, "span", getPendingSuffix(item), "pending-label")
                    val toggle = managerToggle(document, entry, item.isEnabled()) {
                        rememberListScroll()
                        DlcItemActions.toggle(item)
                        refreshAfterStateChange()
                    }
                    toggle.setEnabled(!DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier()) && (item.isEnabled() || !DlcRuntimeState.hasMissingDependencies(item)))
                }
            }
        }
        if (renderedRows == 0) managerText(document, list, "p", translate(if (searchQuery.isBlank()) "dlc_manager.ui.empty" else "dlc_manager.ui.no_results"), "empty-state")
        document.getElementById("manager-summary").setTextContent(translate("dlc_manager.ui.summary", renderedDlcCount, enabledDlcCount))
        updatePendingStatus(document)
        updateAnimations(document, System.nanoTime())
        list.setScrollTop(scroll)
    }

    private fun itemTooltip(item: DlcItem): DlcUiTooltip.Content {
        val details = ArrayList<DlcUiTooltip.Detail>()
        val tips = item.getTooltips()
        val description = if (tips.isEmpty()) "" else tips[DlcPlatformServices.languageCode()] ?: tips["en_us"] ?: tips.values.first()
        val missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item)
        if (missing.isNotEmpty()) details.add(DlcUiTooltip.Detail(translate("dlc_manager.tooltip.dependencies_missing"),
            missing.joinToString("&r, ") { DlcDisplayNameUtil.resolveDisplayName(it) }, true))
        val dependencies = DlcItemActions.dependencyNames(item)
        if (dependencies.isNotEmpty()) details.add(DlcUiTooltip.Detail(translate("dlc_manager.tooltip.dependencies"),
            dependencies.joinToString("&r, ") { DlcDisplayNameUtil.resolveDisplayName(it) }, false))
        if (item.getConflicts().isNotEmpty()) details.add(DlcUiTooltip.Detail(translate("dlc_manager.tooltip.conflicts"),
            item.getConflicts().joinToString("&r, ") { DlcDisplayNameUtil.resolveDisplayName(it) }, true))
        val hint = if (!item.getRedirectUrl().isNullOrBlank()) translate("dlc_manager.tooltip.double_click_redirect") else ""
        return DlcUiTooltip.Content(DlcDisplayNameUtil.resolveDisplayName(item), description, details, hint)
    }

    fun refreshAfterStateChange() {
        for (item in allItems) {
            DlcRuntimeState.getOverride(item.getId())?.let { item.setEnabled(it) }
            item.setDirty(isItemModified(item))
        }
        linkedDocument?.let { if (Minecraft.getInstance().screen === this) renderRows(it) }
        updateHeaderButtonsState()
        updateModeAvailability()
    }

    override fun rememberReturnPosition() { rememberListScroll() }

    override fun getTitle(): Component = DlcTextCompat.translatable("dlc_manager.title")

    override fun tick() {
        super.tick()
        rememberListScroll()
        updateHeaderButtonsState()
        updateModeAvailability()
    }

    //? if <26.1 {
    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        rememberListScroll()
        super.resize(minecraft, width, height)
    }
    //?}

    //? if >=26.1 {
    /*override fun resize(width: Int, height: Int) {
        rememberListScroll()
        super.resize(width, height)
    }
    *///?}

    fun rememberListScroll() {
        val document = linkedDocument ?: return
        if (pendingScrollRestore) return
        savedScrollOffset = document.getElementById("manager-list").targetScrollTop
        savedScrollFilter = activeFilter
    }

    private fun restoreListScroll() {
        pendingScrollRestore = savedScrollFilter == activeFilter
    }

    override fun isUiContentReady(): Boolean = !pendingScrollRestore

    //? if >=1.20.1 <26.1 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val document = linkedDocument ?: return
        if (!pendingScrollRestore || document.needsInitialFullCommit() || document.isInitialCommitSlicing) return
        val list = document.getElementById("manager-list")
        if (list.clientHeight > 0 && list.computedStyle.overflowY in setOf("auto", "scroll")) {
            Document.runWithContext(document) { list.setScrollTop(savedScrollOffset) }
        }
        pendingScrollRestore = false
    }
    //?}

    //? if <1.20.1 {
    /*override fun render(graphics: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val document = linkedDocument ?: return
        if (!pendingScrollRestore || document.needsInitialFullCommit() || document.isInitialCommitSlicing) return
        val list = document.getElementById("manager-list")
        if (list.clientHeight > 0 && list.computedStyle.overflowY in setOf("auto", "scroll")) {
            Document.runWithContext(document) { list.setScrollTop(savedScrollOffset) }
        }
        pendingScrollRestore = false
    }
    *///?}

    //? if >=26.1 {
    /*override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val document = linkedDocument ?: return
        if (!pendingScrollRestore || document.needsInitialFullCommit() || document.isInitialCommitSlicing) return
        val list = document.getElementById("manager-list")
        if (list.clientHeight > 0 && list.computedStyle.overflowY in setOf("auto", "scroll")) {
            Document.runWithContext(document) { list.setScrollTop(savedScrollOffset) }
        }
        pendingScrollRestore = false
    }
    *///?}

    override fun onClose() {
        tooltip?.hide()
        if (hasPresetChanges()) DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot)
        DlcRuntimeState.clearOverrides()
        super.onClose()
        Minecraft.getInstance().setScreen(parent)
    }
    private fun showRequiredRestartPrompt() {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(
            DlcConfirmScreen(
                { accepted -> if (accepted) minecraft.stop() else minecraft.setScreen(parent) },
                DlcTextCompat.translatable("dlc_manager.title"),
                DlcTextCompat.translatable("dlc_manager.required.restart_prompt"),
                DlcTextCompat.translatable("dlc_manager.restart_now"),
                DlcTextCompat.translatable("dlc_manager.restart_later"),
            )
        )
    }
}
