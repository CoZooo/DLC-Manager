package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcFilePicker
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.pack.DlcPackArchive
import com.cozooo.dlc_manager.client.pack.DlcPackCFResolver
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller
import com.cozooo.dlc_manager.client.pack.DlcPackInstallPlan
import com.cozooo.dlc_manager.client.pack.DlcPackMeta
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata
import com.cozooo.dlc_manager.client.pack.DlcPackOrder
import com.cozooo.dlc_manager.client.pack.DlcPackPackage
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcPackManagerScreen(
    private val parent: Screen?,
) : DlcPackPageScreen(parent, Component.translatable("dlc_manager.pack.manager_title")) {
    private var importButton: Button? = null
    private var urlButton: Button? = null
    private var applyButton: Button? = null
    private var backButton: Button? = null
    private var completeButton: Button? = null
    private val pendingInstalls = LinkedHashMap<String, PackArchiveEntry>()
    private val pendingUninstalls = LinkedHashSet<String>()
    private var archiveGroups: List<PackArchiveGroup> = emptyList()
    private var entryArchiveOrder: List<String> = emptyList()
    private var committedArchiveOrder: List<String> = emptyList()
    private var currentArchiveOrder: List<String> = emptyList()
    private var orderSnapshotInitialized = false
    private var installedByIdentifier: Map<String, DlcPackMeta> = emptyMap()
    private val selectedVersionByIdentifier = LinkedHashMap<String, String>()
    private var selectedIndex = -1
    private var versionDropdownIdentifier: String? = null
    private var scrollOffset = 0
    private var draggingScrollbar = false
    private var scrollbarDragOffset = 0.0
    private var lastClickKey: String? = null
    private var lastClickAt = 0L
    private var status: Component? = null
    @Volatile private var busy = false
    @Volatile private var cancelRequested = false
    @Volatile private var cancelApplyRequested = false
    @Volatile private var paused = false
    @Volatile private var busyOperation = BusyOperation.NONE
    @Volatile private var operationProgress = -1f
    @Volatile private var operationStatus: Component? = null
    @Volatile private var operationDetail: Component? = null
    private var pausedAllowRuntimeChanges = false
    private val pausedReinstallIdentifiers = LinkedHashSet<String>()
    @Volatile private var activeReinstallIdentifiers: Set<String> = emptySet()
    private var iconTextureSerial = 0

    private fun initControls() {

        clearWidgets()
        val buttonWidth = 104
        val buttonHeight = 20
        val y = height - 28
        if (busy) {
            val gap = 8
            val startX = width / 2 - buttonWidth - gap / 2
            val canResume = paused && busyOperation == BusyOperation.APPLY
            val applying = busyOperation == BusyOperation.APPLY
            val cancel = Button.builder(
                when {
                    canResume -> Component.translatable("dlc_manager.pack.continue_install")
                    applying && cancelRequested && !cancelApplyRequested -> Component.translatable("dlc_manager.pack.pausing")
                    applying -> Component.translatable("dlc_manager.pack.pause_install")
                    cancelRequested -> Component.translatable("dlc_manager.pack.canceling")
                    else -> Component.translatable("dlc_manager.pack.cancel")
                }
            ) {
                when {
                    canResume -> resumePausedApply()
                    applying -> requestPauseApplyOperation()
                    else -> onBackOrCancel()
                }
            }
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build()
            cancel.active = canResume || (!cancelRequested && busyOperation != BusyOperation.DELETE)
            val back = Button.builder(if (applying) Component.translatable("dlc_manager.pack.cancel_install") else Component.translatable("dlc_manager.pack.back")) {
                if (applying) requestCancelApplyOperation() else returnToParent()
            }
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build()
            back.active = applying || canResume
            backButton = cancel
            addRenderableWidget(cancel)
            addRenderableWidget(back)
            return
        }
        addCompleteButton()
        val gap = 8
        val total = buttonWidth * 4 + gap * 3
        val startX = width / 2 - total / 2
        val import = Button.builder(Component.translatable("dlc_manager.pack.import")) { importPackArchive() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        import.active = DlcConfig.isPackFuncOn() && !busy
        val url = Button.builder(Component.translatable("dlc_manager.pack.import_url")) {
            Minecraft.getInstance().setScreen(DlcPackUrlScreen(this))
        }.bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight).build()
        url.active = DlcConfig.isPackFuncOn() && !busy
        val apply = Button.builder(Component.translatable("dlc_manager.pack.apply")) { applyPendingChanges() }
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build()
        apply.active = DlcConfig.isPackFuncOn() && hasPendingChanges() && !busy
        val backLabel = when {
            busy -> Component.translatable("dlc_manager.pack.cancel")
            hasPendingChanges() -> Component.translatable("dlc_manager.pack.cancel_changes")
            else -> Component.translatable("dlc_manager.pack.back")
        }
        val back = Button.builder(backLabel) { onBackOrCancel() }
            .bounds(startX + (buttonWidth + gap) * 3, y, buttonWidth, buttonHeight)
            .build()
        back.active = true
        importButton = import
        urlButton = url
        applyButton = apply
        backButton = back
        addRenderableWidget(import)
        addRenderableWidget(url)
        addRenderableWidget(apply)
        addRenderableWidget(back)
    }

    private fun addCompleteButton() {
        if (!DlcConfig.isPackFuncOn() || !DlcRemoteManager.hasMissingDlcPacks()) {
            completeButton = null
            return
        }
        val label = Component.translatable("dlc_manager.pack.complete")
        val buttonWidth = max(76, font.width(label) + 8)
        val button = Button.builder(label) { openCompletePacks() }
            .bounds(12, 8, buttonWidth, 16)
            .build()
        completeButton = button
        addRenderableWidget(button)
    }

    private fun openCompletePacks() {
        val missing = DlcRemoteManager.collectMissingDlcPacks()
        if (missing.isEmpty()) {
            status = Component.translatable("dlc_manager.pack.complete_none")
            rebuildPackWidgets()
            return
        }
        Minecraft.getInstance().setScreen(
            DlcRemoteDownloadScreen(
                this,
                missing,
                DlcRemoteMarker.PART_DLC_PACK,
                Component.translatable("dlc_manager.pack.complete_title"),
            )
        )
    }

    private fun drawSizedButton(guiGraphics: GuiGraphics, x: Int, y: Int, buttonWidth: Int, label: Component, mouseX: Int, mouseY: Int) {
        drawSizedButton(guiGraphics, x, y, buttonWidth, label, mouseX, mouseY, 0xFFFFFF)
    }

    private fun drawSizedButton(guiGraphics: GuiGraphics, x: Int, y: Int, buttonWidth: Int, label: Component, mouseX: Int, mouseY: Int, textColor: Int) {
        val hover = mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + BUTTON_HEIGHT
        guiGraphics.fill(x, y, x + buttonWidth, y + BUTTON_HEIGHT, if (hover) 0xFF5A5A5A.toInt() else 0xFF404040.toInt())
        guiGraphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 4, textColor)
    }

    private fun installButtonLabel(group: PackArchiveGroup): Component {
        val entry = group.displayEntry
        val installed = installedByIdentifier[entry.metadata.identifier]
        val compare = installed?.let { DlcPackMetadata.compareVersionStrings(entry.metadata.version, it.version) } ?: 0
        if (pendingInstalls[entry.metadata.identifier] === entry) {
            return when {
                installed == null -> Component.translatable("dlc_manager.pack.pending_install")
                compare > 0 -> Component.translatable("dlc_manager.pack.pending_upgrade")
                compare < 0 -> Component.translatable("dlc_manager.pack.pending_downgrade")
                else -> Component.translatable("dlc_manager.pack.pending_install")
            }
        }
        if (pendingUninstalls.contains(entry.metadata.identifier)) return Component.translatable("dlc_manager.pack.pending_uninstall")
        if (installed != null && compare == 0) return Component.translatable("dlc_manager.pack.uninstall")
        if (installed != null) {
            return if (compare > 0) Component.translatable("dlc_manager.pack.upgrade") else Component.translatable("dlc_manager.pack.downgrade")
        }
        return Component.translatable("dlc_manager.pack.install")
    }

    private fun toggleInstall(group: PackArchiveGroup) {
        val entry = group.displayEntry
        val identifier = entry.metadata.identifier
        if (pendingUninstalls.remove(identifier)) {
            pendingInstalls.remove(identifier)
        } else if (isEntryCurrentlyInstalled(entry)) {
            pendingUninstalls.add(identifier)
            pendingInstalls.remove(identifier)
        } else if (pendingInstalls[identifier] === entry) {
            pendingInstalls.remove(identifier)
        } else {
            val installed = installedByIdentifier[identifier]
            if (installed != null && entry.metadata.version == installed.version) {
                pendingUninstalls.add(identifier)
                pendingInstalls.remove(identifier)
            } else {
                pendingInstalls[identifier] = entry
            }
        }
        rebuildPackWidgets()
    }

    private fun confirmDeleteArchive(group: PackArchiveGroup) {
        val entry = group.displayEntry
        val uninstallFirst = isEntryCurrentlyInstalled(entry)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (accepted) {
                        startDeleteArchive(group, uninstallFirst)
                    }
                },
                Component.translatable("dlc_manager.pack.delete_confirm_title"),
                Component.translatable(
                    if (uninstallFirst) "dlc_manager.pack.delete_confirm_uninstall" else "dlc_manager.pack.delete_confirm",
                    entry.displayName,
                ),
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun startDeleteArchive(group: PackArchiveGroup, uninstallFirst: Boolean) {
        val entry = group.displayEntry
        if (uninstallFirst) {
            setBusy(true, Component.translatable("dlc_manager.pack.operation_uninstalling"), BusyOperation.DELETE)
            Thread({
                try {
                    val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                    installer.uninstall(entry.metadata.identifier) { _, progress ->
                        updateOperationProgress(Component.translatable("dlc_manager.pack.operation_uninstalling"), progress)
                    }
                    deleteArchiveFile(group)
                    finishBusy(Component.translatable("dlc_manager.pack.delete_uninstalled_success", entry.displayName), true)
                } catch (ex: Exception) {
                    finishBusy(Component.translatable("dlc_manager.pack.delete_failed", describeFailure(ex)))
                }
            }, "dlc-pack-delete").start()
            return
        }
        try {
            deleteArchiveFile(group)
            status = Component.translatable("dlc_manager.pack.delete_success", entry.displayName)
            refreshEntries()
            rebuildPackWidgets()
        } catch (ex: Exception) {
            status = Component.translatable("dlc_manager.pack.delete_failed", ex.message ?: ex.javaClass.simpleName)
        }
    }

    @Throws(IOException::class)
    private fun deleteArchiveFile(group: PackArchiveGroup) {
        val entry = group.displayEntry
        Files.deleteIfExists(entry.archivePath)
        if (pendingInstalls[entry.metadata.identifier] === entry) pendingInstalls.remove(entry.metadata.identifier)
        pendingUninstalls.remove(entry.metadata.identifier)
        val replacementKey = group.versions.firstOrNull { it.key != entry.key }?.key
        currentArchiveOrder = replaceOrRemoveDeletedOrderKey(currentArchiveOrder, entry, replacementKey)
        committedArchiveOrder = replaceOrRemoveDeletedOrderKey(committedArchiveOrder, entry, replacementKey)
        entryArchiveOrder = replaceOrRemoveDeletedOrderKey(entryArchiveOrder, entry, replacementKey)
        saveLiveOrder(currentArchiveOrder)
    }

    private fun replaceOrRemoveDeletedOrderKey(order: List<String>, entry: PackArchiveEntry, replacementKey: String?): List<String> {
        val out = ArrayList<String>(order.size)
        for (key in order) {
            if (!orderKeyMatchesIdentifier(key, entry.metadata.identifier)) {
                out.add(key)
                continue
            }
            if (replacementKey != null && !out.contains(replacementKey)) {
                out.add(if (key == entry.key) replacementKey else key)
            }
        }
        return out
    }

    private fun confirmOpenLink(entry: PackArchiveEntry, key: String) {
        val url = entry.metadata.links[key]
        if (url.isNullOrBlank()) {
            return
        }
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (accepted) {
                        Util.getPlatform().openUri(url)
                    }
                },
                Component.translatable("dlc_manager.pack.open_link_title"),
                Component.translatable("dlc_manager.pack.open_link_prompt", url),
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun actionButtons(group: PackArchiveGroup): List<RowAction> {
        val entry = group.displayEntry
        val out = ArrayList<RowAction>(4)
        out.add(RowAction.INSTALL)
        out.add(RowAction.DELETE)
        if (!entry.metadata.links["issue"].isNullOrBlank()) out.add(RowAction.FEEDBACK)
        if (!entry.metadata.links["sponsor"].isNullOrBlank()) out.add(RowAction.SUPPORT)
        return out
    }

    private fun actionLabel(group: PackArchiveGroup, action: RowAction): Component {
        val entry = group.displayEntry
        return when (action) {
            RowAction.INSTALL -> installButtonLabel(group)
            RowAction.DELETE -> Component.translatable("dlc_manager.pack.delete")
            RowAction.FEEDBACK -> Component.translatable("dlc_manager.pack.feedback")
            RowAction.SUPPORT -> Component.translatable("dlc_manager.pack.support_author")
            RowAction.MOVE_UP -> Component.literal("▲")
            RowAction.MOVE_DOWN -> Component.literal("▼")
        }
    }

    private fun versionOptionLabel(entry: PackArchiveEntry): Component {
        val version = Component.literal(entry.metadata.version)
        return if (isInstalledVersion(entry)) {
            Component.translatable("dlc_manager.pack.version_current_short", version)
        } else {
            version
        }
    }

    private fun moveEntry(index: Int, direction: Int) {
        val target = index + direction
        if (target !in archiveGroups.indices) return
        val mutable = ArrayList(archiveGroups)
        val entry = mutable.removeAt(index)
        mutable.add(target, entry)
        archiveGroups = mutable
        currentArchiveOrder = collectArchiveKeys(archiveGroups)
        saveLiveOrder(currentArchiveOrder)
        selectedIndex = target

        rebuildPackWidgets()
    }

    private fun importPackArchive() {
        if (!DlcConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled")
            return
        }
        val zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").string) ?: return
        setBusy(true, Component.translatable("dlc_manager.pack.importing"), BusyOperation.IMPORT)
        updateOperationProgress(Component.translatable("dlc_manager.pack.importing"), -1f)
        Thread({
            var copied: Path? = null
            try {
                checkCanceled()
                copied = copyArchiveToPackRoot(zip)
                checkCanceled()
                finishBusy(Component.translatable("dlc_manager.pack.import_staged", copied.fileName.toString()))
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                copied?.let {
                    try {
                        Files.deleteIfExists(it)
                    } catch (_: IOException) {
                    }
                }
                finishBusy(Component.translatable("dlc_manager.pack.canceled"))
            } catch (ex: Exception) {
                finishBusy(Component.translatable("dlc_manager.pack.import_failed", ex.message ?: ex.javaClass.simpleName))
            }
        }, "dlc-pack-import-copy").start()
    }

    fun importDownloadedPack(zip: Path?) {
        if (zip != null) {
            status = Component.translatable("dlc_manager.pack.import_staged", zip.fileName.toString())
            refreshEntries()
            rebuildPackWidgets()
        }
    }

    @Throws(IOException::class)
    private fun copyArchiveToPackRoot(source: Path): Path {
        val packRoot = DlcPaths.dlcRoot().resolve("pack")
        Files.createDirectories(packRoot)
        var fileName = source.fileName?.toString() ?: "pack.zip"
        if (!fileName.lowercase(Locale.ROOT).endsWith(".zip")) fileName += ".zip"
        val target = packRoot.resolve(fileName)
        if (source.toAbsolutePath().normalize() == target.toAbsolutePath().normalize()) return target
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    private fun applyPendingChanges() {
        if (!hasPendingChanges() || busy) return
        if (!hasPackOperationChanges()) {
            commitCurrentOrderWithoutRestart()
            return
        }
        val confirmText = buildReplacementConfirmText()
        if (confirmText != null) {
            Minecraft.getInstance().setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            continueApplyAfterReplacementConfirm()
                        }
                    },
                    Component.translatable("dlc_manager.pack.apply_confirm_title"),
                    Component.literal(confirmText),
                    DlcConfirmUtil.confirmYes(),
                    DlcConfirmUtil.confirmNo(),
                )
            )
            return
        }
        continueApplyAfterReplacementConfirm()
    }

    private fun continueApplyAfterReplacementConfirm() {
        val runtimeConfirmText = buildRuntimeConfirmText()
        if (runtimeConfirmText != null) {
            Minecraft.getInstance().setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            startApplyPendingChanges(true)
                        }
                    },
                    Component.translatable("dlc_manager.pack.runtime_confirm_title"),
                    Component.literal(runtimeConfirmText),
                    DlcConfirmUtil.confirmYes(),
                    DlcConfirmUtil.confirmNo(),
                )
            )
            return
        }
        startApplyPendingChanges(false)
    }

    private fun commitCurrentOrderWithoutRestart() {
        committedArchiveOrder = currentArchiveOrder.toList()
        entryArchiveOrder = committedArchiveOrder.toList()
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder)
        status = Component.translatable("dlc_manager.pack.order_applied")
        rebuildPackWidgets()
    }

    private fun startApplyPendingChanges(allowRuntimeChanges: Boolean) {
        pausedAllowRuntimeChanges = allowRuntimeChanges
        setBusy(true, Component.translatable("dlc_manager.pack.applying"), BusyOperation.APPLY)
        updateOperationProgress(Component.translatable("dlc_manager.pack.applying"), 0f)
        Thread({
            try {
                val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                applyOrderedChanges(installer, allowRuntimeChanges)
                pendingUninstalls.clear()
                pendingInstalls.clear()
                pausedReinstallIdentifiers.clear()
                activeReinstallIdentifiers = emptySet()
                finishBusy(Component.translatable("dlc_manager.pack.apply_success"), true)
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                if (cancelApplyRequested) {
                    finishBusy(Component.translatable("dlc_manager.pack.canceled"))
                } else {
                    pauseApplyOperation()
                }
            } catch (ex: Exception) {
                finishBusy(Component.translatable("dlc_manager.pack.apply_failed", describeFailure(ex)))
            }
        }, "dlc-pack-apply").start()
    }

    private fun applyOrderedChanges(installer: DlcPackInstaller, allowRuntimeChanges: Boolean) {
        val orderedGroups = archiveGroups.sortedBy { orderIndex(it.orderKey) }
        val ordered = orderedGroups.map { effectiveApplyEntry(it) }
        val reinstallIdentifiers = LinkedHashSet(collectAffectedReinstallIdentifiers(ordered))
        reinstallIdentifiers.addAll(pausedReinstallIdentifiers)
        activeReinstallIdentifiers = reinstallIdentifiers.toSet()
        val displayMode = determineOperationDisplayMode(reinstallIdentifiers)
        val hasUninstallPhase = ordered.any { entry ->
            val identifier = entry.metadata.identifier
            isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier))
        }
        val installOffset = if (hasUninstallPhase) 0.35f else 0f
        val installSpan = if (hasUninstallPhase) 0.65f else 1f
        val uninstalled = LinkedHashSet<String>()
        for (entry in ordered) {
            val identifier = entry.metadata.identifier
            if (!uninstalled.contains(identifier) && isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier))) {
                checkCanceled()
                updateOperationProgress(operationStatusFor(displayMode, entry), -1f)
                installer.uninstall(identifier) { message, progress ->
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(0f, 0.35f, progress), operationDetailFor(message))
                }
                uninstalled.add(identifier)
            }
        }
        val installed = LinkedHashSet<String>()
        for (i in ordered.size - 1 downTo 0) {
            val entry = ordered[i]
            val identifier = entry.metadata.identifier
            if (!installed.contains(identifier) && !pendingUninstalls.contains(identifier) && (pendingInstalls[identifier] === entry || (reinstallIdentifiers.contains(identifier) && isEntryCurrentlyInstalled(entry)))) {
                checkCanceled()
                updateOperationProgress(operationStatusFor(displayMode, entry), installOffset)
                installArchive(installer, entry, allowRuntimeChanges, displayMode, installOffset, installSpan)
                installed.add(identifier)
            }
        }
        checkCanceled()
        committedArchiveOrder = finalAppliedOrder()
        currentArchiveOrder = committedArchiveOrder.toList()
        entryArchiveOrder = committedArchiveOrder.toList()
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder)
    }

    private fun effectiveApplyEntry(group: PackArchiveGroup): PackArchiveEntry {
        pendingInstalls[group.identifier]?.let { return it }
        val installedVersion = installedByIdentifier[group.identifier]?.version
        if (installedVersion != null) {
            group.versions.firstOrNull { it.metadata.version == installedVersion }?.let { return it }
        }
        return group.displayEntry
    }

    private fun finalAppliedOrder(): List<String> {
        val groupsById = archiveGroups.associateBy { it.identifier }
        val out = ArrayList<String>()
        for (key in currentArchiveOrder) {
            val identifier = identifierFromOrderKey(key) ?: continue
            val group = groupsById[identifier] ?: continue
            val finalKey = when {
                pendingInstalls[identifier] != null -> pendingInstalls[identifier]!!.key
                pendingUninstalls.contains(identifier) -> group.versions.first().key
                else -> group.orderKey
            }
            if (!out.contains(finalKey)) {
                out.add(finalKey)
            }
        }
        for (group in archiveGroups) {
            val finalKey = pendingInstalls[group.identifier]?.key ?: if (pendingUninstalls.contains(group.identifier)) group.versions.first().key else group.orderKey
            if (!out.contains(finalKey)) {
                out.add(finalKey)
            }
        }
        return out
    }

    private fun determineOperationDisplayMode(reinstallIdentifiers: Set<String>): OperationDisplayMode {
        val hasInstallOps = pendingInstalls.isNotEmpty() || reinstallIdentifiers.isNotEmpty()
        val hasReplacementInstalls = pendingInstalls.keys.any { installedByIdentifier.containsKey(it) }
        val hasUninstallOps = pendingUninstalls.isNotEmpty() || reinstallIdentifiers.isNotEmpty() || hasReplacementInstalls
        return when {
            hasUninstallOps && !hasInstallOps -> OperationDisplayMode.UNINSTALLING
            hasInstallOps && !hasUninstallOps -> OperationDisplayMode.INSTALLING
            else -> OperationDisplayMode.APPLYING
        }
    }

    private fun collectAffectedReinstallIdentifiers(ordered: List<PackArchiveEntry>): Set<String> {
        val finalEntries = ArrayList<PackArchiveEntry>()
        for (entry in ordered) {
            val identifier = entry.metadata.identifier
            if (pendingUninstalls.contains(identifier)) {
                continue
            }
            if (pendingInstalls[identifier] === entry || isEntryCurrentlyInstalled(entry)) {
                finalEntries.add(entry)
            }
        }
        val affected = LinkedHashSet<String>()
        for (i in finalEntries.indices) {
            val left = finalEntries[i]
            for (j in i + 1 until finalEntries.size) {
                val right = finalEntries[j]
                if (!packsHaveFileConflict(left, right)) {
                    continue
                }
                if (pairOrderChanged(left, right)) {
                    affected.add(left.metadata.identifier)
                    affected.add(right.metadata.identifier)
                }
            }
        }
        return affected
    }

    private fun packsHaveFileConflict(left: PackArchiveEntry, right: PackArchiveEntry): Boolean {
        val leftFiles = inspectArchiveFiles(left)
        if (leftFiles.isEmpty()) {
            return false
        }
        val rightFiles = inspectArchiveFiles(right)
        return leftFiles.any { rightFiles.contains(it) }
    }

    private fun pairOrderChanged(left: PackArchiveEntry, right: PackArchiveEntry): Boolean {
        val leftCommitted = committedOrderIndex(left.key)
        val rightCommitted = committedOrderIndex(right.key)
        if (leftCommitted < 0 || rightCommitted < 0) {
            return true
        }
        return leftCommitted > rightCommitted
    }

    private fun isEntryCurrentlyInstalled(entry: PackArchiveEntry?): Boolean {
        if (entry == null) {
            return false
        }
        val installed = installedByIdentifier[entry.metadata.identifier] ?: return false
        return entry.metadata.version == installed.version
    }

    private fun isInstalledVersion(entry: PackArchiveEntry): Boolean {
        return installedByIdentifier[entry.metadata.identifier]?.version == entry.metadata.version
    }

    private fun inspectArchiveFiles(entry: PackArchiveEntry): Set<String> {
        var staging: Path? = null
        return try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            val pack = result.pack
            if (!result.valid || pack == null) {
                throw IOException(result.error ?: "invalid DLC Pack")
            }
            val plan = DlcPackInstallPlan.build(pack, DlcPaths.gameDir(), DlcPaths.dlcRoot())
            DlcPackInstallPlan.merge(plan, DlcPackCFResolver.resolveManifestTargets(pack), DlcPaths.gameDir(), DlcPaths.dlcRoot())
                .files
                .mapTo(LinkedHashSet()) { it.relativePath }
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
    }

    private fun buildReplacementConfirmText(): String? {
        val lines = ArrayList<String>()
        for (group in archiveGroups) {
            val entry = group.displayEntry
            if (pendingInstalls[entry.metadata.identifier] !== entry) {
                continue
            }
            val installed = installedByIdentifier[entry.metadata.identifier] ?: continue
            val compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version)
            if (compare > 0) {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_upgrade", entry.displayNameText, installed.version, entry.metadata.version).string)
            } else if (compare < 0) {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_downgrade", entry.displayNameText, installed.version, entry.metadata.version).string)
            } else {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_replace", entry.displayNameText, installed.version).string)
            }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun buildRuntimeConfirmText(): String? {
        val messages = ArrayList<String>()
        val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
        for (group in archiveGroups) {
            val entry = group.displayEntry
            if (pendingInstalls[entry.metadata.identifier] !== entry) {
                continue
            }
            var staging: Path? = null
            try {
                staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir
                val result = DlcPackPackage.inspect(staging)
                val pack = result.pack
                if (!result.valid || pack == null) {
                    messages.add(result.error ?: "invalid DLC Pack")
                    continue
                }
                val precheck = installer.precheck(pack)
                if (precheck.hasBlockingIssues()) {
                    messages.add(DlcConfirmUtil.describePackBlockingPrecheck(precheck))
                    continue
                }
                if (precheck.requiresRuntimeConfirmation()) {
                    messages.add(
                        Component.translatable(
                            "dlc_manager.pack.runtime_confirm_entry",
                            entry.displayNameText,
                            DlcConfirmUtil.describePackBlockingPrecheck(precheck),
                        ).string
                    )
                }
            } catch (ex: Exception) {
                messages.add(describeFailure(ex))
            } finally {
                DlcPackArchive.deleteRecursive(staging)
            }
        }
        return messages.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    @Throws(IOException::class)
    private fun installArchive(
        installer: DlcPackInstaller,
        entry: PackArchiveEntry,
        allowRuntimeChanges: Boolean,
        displayMode: OperationDisplayMode,
        installOffset: Float,
        installSpan: Float,
    ) {
        var staging: Path? = null
        try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            val pack = result.pack
            if (!result.valid || pack == null) throw IOException(result.error ?: "invalid DLC Pack")
            val precheck = installer.precheck(pack)
            if (precheck.hasBlockingIssues() || (precheck.requiresRuntimeConfirmation() && !allowRuntimeChanges)) {
                throw IOException(DlcConfirmUtil.describePackBlockingPrecheck(precheck))
            }
            val replace = installedByIdentifier.containsKey(pack.metadata.identifier)
            val options = (if (allowRuntimeChanges) {
                DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges()
            } else {
                DlcPackInstaller.InstallOptions.strict()
            })
                .withCancelChecker { cancelRequested }
                .withProgressListener { message, progress ->
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(installOffset, installSpan, progress), operationDetailFor(message))
                }
            installer.install(pack, replace, options)
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
    }

    private fun checkCanceled() {
        if (cancelRequested) {
            throw DlcDownloadService.DownloadCanceledException("canceled")
        }
    }

    private fun normalizePhaseProgress(offset: Float, span: Float, progress: Float): Float {
        return if (progress < 0f) -1f else offset + progress.coerceIn(0f, 1f) * span
    }

    private fun operationStatusFor(mode: OperationDisplayMode, entry: PackArchiveEntry, fallback: String? = null): Component {
        if (fallback == "dlc_manager.pack.selecting_source") {
            return Component.translatable("dlc_manager.pack.selecting_source")
        }
        return when (mode) {
            OperationDisplayMode.UNINSTALLING -> Component.translatable("dlc_manager.pack.operation_uninstalling")
            OperationDisplayMode.INSTALLING -> Component.translatable("dlc_manager.pack.operation_installing")
            OperationDisplayMode.APPLYING -> Component.translatable("dlc_manager.pack.operation_applying")
        }
    }

    private fun describeFailure(ex: Exception): String {
        val message = ex.message
        return if (message.isNullOrBlank()) ex.javaClass.simpleName else message
    }

    private fun setBusy(value: Boolean, message: Component, operation: BusyOperation = BusyOperation.NONE) {
        if (value) {
            cancelRequested = false
            cancelApplyRequested = false
            paused = false
            busyOperation = operation
            operationProgress = -1f
            operationStatus = message
            operationDetail = null
        } else {
            paused = false
            busyOperation = BusyOperation.NONE
            operationDetail = null
        }
        busy = value
        status = message
        rebuildPackWidgets()
    }

    private fun finishBusy(message: Component, promptRestart: Boolean = false) {
        Minecraft.getInstance().execute {
            busy = false
            cancelRequested = false
            cancelApplyRequested = false
            paused = false
            busyOperation = BusyOperation.NONE
            operationProgress = -1f
            operationStatus = null
            operationDetail = null
            pausedAllowRuntimeChanges = false
            status = message
            refreshEntries()
            rebuildPackWidgets()
            if (promptRestart) {
                showRestartPrompt()
            }
        }
    }

    private fun pauseApplyOperation() {
        Minecraft.getInstance().execute {
            busy = true
            cancelRequested = false
            cancelApplyRequested = false
            paused = true
            busyOperation = BusyOperation.APPLY
            operationProgress = -1f
            operationStatus = Component.translatable("dlc_manager.pack.paused")
            operationDetail = null
            status = operationStatus
            pausedReinstallIdentifiers.clear()
            pausedReinstallIdentifiers.addAll(activeReinstallIdentifiers)
            refreshEntries()
            rebuildPackWidgets()
        }
    }

    private fun resumePausedApply() {
        if (!paused || busyOperation != BusyOperation.APPLY) {
            return
        }
        val allowRuntimeChanges = pausedAllowRuntimeChanges
        busy = false
        paused = false
        cancelRequested = false
        cancelApplyRequested = false
        busyOperation = BusyOperation.NONE
        startApplyPendingChanges(allowRuntimeChanges)
    }

    private fun requestPauseApplyOperation() {
        if (busyOperation != BusyOperation.APPLY || cancelRequested) {
            return
        }
        cancelApplyRequested = false
        cancelRequested = true
        status = Component.translatable("dlc_manager.pack.pausing")
        operationStatus = status
        rebuildPackWidgets()
    }

    private fun requestCancelApplyOperation() {
        if (busyOperation != BusyOperation.APPLY) {
            return
        }
        cancelApplyRequested = true
        if (paused) {
            finishBusy(Component.translatable("dlc_manager.pack.canceled"))
            return
        }
        cancelRequested = true
        status = Component.translatable("dlc_manager.pack.canceling")
        operationStatus = status
        rebuildPackWidgets()
    }

    private fun showRestartPrompt() {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(
            DlcConfirmScreen(
                { accepted -> if (accepted) minecraft.stop() else minecraft.setScreen(this) },
                Component.translatable("dlc_manager.title"),
                Component.translatable("dlc_manager.pack.restart_prompt"),
                Component.translatable("dlc_manager.restart_now"),
                Component.translatable("dlc_manager.restart_later"),
            )
        )
    }

    private fun onBackOrCancel() {
        if (busy) {
            if (paused && busyOperation == BusyOperation.APPLY) {
                requestCancelApplyOperation()
                return
            }
            if (busyOperation == BusyOperation.DELETE) {
                return
            }
            cancelRequested = true
            status = if (busyOperation == BusyOperation.APPLY) {
                Component.translatable("dlc_manager.pack.pausing")
            } else {
                Component.translatable("dlc_manager.pack.canceling")
            }
            operationStatus = status
            operationDetail = null
            rebuildPackWidgets()
            return
        }
        if (hasPendingChanges()) {
            pendingInstalls.clear()
            pendingUninstalls.clear()
            currentArchiveOrder = entryArchiveOrder.toList()
            committedArchiveOrder = entryArchiveOrder.toList()
            saveLiveOrder(entryArchiveOrder)
            sortArchiveGroupsByCurrentOrder()
            returnToParent()
            return
        }
        returnToParent()
    }

    private fun returnToParent() {
        busy = false
        paused = false
        cancelRequested = false
        cancelApplyRequested = false
        busyOperation = BusyOperation.NONE
        activeReinstallIdentifiers = emptySet()
        releaseIconTextures()
        Minecraft.getInstance().setScreen(parent)
    }

    fun selectPackVersion(identifier: String, versionKey: String) {
        val group = findGroup(identifier) ?: return
        val selected = group.versions.firstOrNull { it.key == versionKey } ?: return
        versionDropdownIdentifier = null
        selectedVersionByIdentifier[identifier] = selected.key
        pendingInstalls.remove(identifier)
        pendingUninstalls.remove(identifier)
        archiveGroups = archiveGroups.map {
            if (it.identifier == identifier) it.copy(selectedVersionKey = selected.key) else it
        }
        rebuildPackWidgets()
    }

    private fun refreshEntries() {
        val pendingInstallKeys = pendingInstalls.mapValues { it.value.key }
        releaseIconTextures()
        uninstallIncompatibleInstalledPacks()
        installedByIdentifier = loadInstalledPacks()
        val scanned = scanPackArchives()
        val saved = DlcPackOrder.load(packOrderPath())
        val preserveCurrentOrder = currentArchiveOrder.isNotEmpty() && hasOrderChanges()
        val normalizedSaved = normalizeGroupOrder(saved, scanned)
        if (!orderSnapshotInitialized) {
            entryArchiveOrder = normalizedSaved.toList()
            orderSnapshotInitialized = true
        }
        committedArchiveOrder = if (preserveCurrentOrder) {
            normalizeGroupOrder(currentArchiveOrder, scanned)
        } else {
            normalizedSaved
        }
        if (!preserveCurrentOrder) {
            entryArchiveOrder = committedArchiveOrder.toList()
        }
        saveLiveOrder(committedArchiveOrder)
        currentArchiveOrder = if (preserveCurrentOrder) {
            committedArchiveOrder
        } else {
            committedArchiveOrder
        }
        archiveGroups = scanned
        normalizeGroupSelections()
        sortArchiveGroupsByCurrentOrder()
        pendingInstalls.clear()
        for (group in archiveGroups) {
            val entry = group.displayEntry
            if (!isEntryCurrentlyInstalled(entry) && entry.key == pendingInstallKeys[entry.metadata.identifier]) {
                pendingInstalls[entry.metadata.identifier] = entry
            }
        }
        pendingUninstalls.removeIf { !installedByIdentifier.containsKey(it) }
        if (selectedIndex >= archiveGroups.size) selectedIndex = if (archiveGroups.isEmpty()) -1 else archiveGroups.size - 1

    }

    private fun packOrderPath(): Path {
        return DlcPlatformServices.configDir().resolve("dlc_manager").resolve("order.dc")
    }

    private fun saveLiveOrder(order: List<String>) {
        DlcPackOrder.save(packOrderPath(), order)
    }

    private fun archiveOrderPath(archive: Path): String {
        val packRoot = DlcPaths.dlcRoot().resolve("pack").toAbsolutePath().normalize()
        val normalized = archive.toAbsolutePath().normalize()
        return try {
            packRoot.relativize(normalized).toString().replace('\\', '/')
        } catch (_: IllegalArgumentException) {
            archive.fileName.toString()
        }
    }

    private fun collectArchiveKeys(groups: List<PackArchiveGroup>): List<String> {
        return groups.map { it.orderKey }
    }

    private fun orderIndex(key: String): Int {
        val index = currentArchiveOrder.indexOf(key)
        return if (index < 0) Int.MAX_VALUE else index
    }

    private fun committedOrderIndex(key: String): Int {
        return entryArchiveOrder.indexOf(key)
    }

    private fun sortArchiveGroupsByCurrentOrder() {
        archiveGroups = archiveGroups.sortedBy { orderIndex(it.orderKey) }
    }

    private fun scanPackArchives(): List<PackArchiveGroup> {
        val packRoot = DlcPaths.dlcRoot().resolve("pack")
        try {
            Files.createDirectories(packRoot)
        } catch (_: IOException) {
        }
        val out = ArrayList<PackArchiveEntry>()
        try {
            DlcPackArchive.findArchives(DlcPaths.dlcRoot()).stream().use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip") }
                    .sorted { a, b -> a.fileName.toString().lowercase(Locale.ROOT).compareTo(b.fileName.toString().lowercase(Locale.ROOT)) }
                    .forEach { inspectArchive(it)?.let(out::add) }
            }
        } catch (_: Exception) {
        }
        return groupArchiveEntries(out)
    }

    private fun groupArchiveEntries(entries: List<PackArchiveEntry>): List<PackArchiveGroup> {
        val byIdentifier = LinkedHashMap<String, MutableList<PackArchiveEntry>>()
        for (entry in entries) {
            byIdentifier.computeIfAbsent(entry.metadata.identifier) { ArrayList() }.add(entry)
        }
        val groups = ArrayList<PackArchiveGroup>()
        for ((identifier, versions) in byIdentifier) {
            versions.sortWith { a, b ->
                val compare = DlcPackMetadata.compareVersionStrings(b.metadata.version, a.metadata.version)
                if (compare != 0) compare else a.archiveOrderPath.compareTo(b.archiveOrderPath, ignoreCase = true)
            }
            val installed = installedByIdentifier[identifier]
            val orderEntry = if (installed != null) {
                versions.firstOrNull { it.metadata.version == installed.version } ?: versions.first()
            } else {
                versions.first()
            }
            val selectedVersion = selectedVersionByIdentifier[identifier]
            val displayEntry = versions.firstOrNull { it.key == selectedVersion || it.metadata.version == selectedVersion } ?: orderEntry
            groups.add(PackArchiveGroup(identifier, versions, orderEntry.key, displayEntry.key))
        }
        return groups
    }

    private fun normalizeGroupSelections() {
        val validIdentifiers = archiveGroups.mapTo(HashSet()) { it.identifier }
        selectedVersionByIdentifier.keys.removeIf { !validIdentifiers.contains(it) }
        archiveGroups = archiveGroups.map { group ->
            val selected = selectedVersionByIdentifier[group.identifier]
            val display = group.versions.firstOrNull { it.key == selected || it.metadata.version == selected } ?: group.displayEntry
            selectedVersionByIdentifier[group.identifier] = display.key
            group.copy(selectedVersionKey = display.key)
        }
    }

    private fun normalizeGroupOrder(savedOrder: Collection<String>?, groups: List<PackArchiveGroup>): List<String> {
        val out = ArrayList<String>()
        if (savedOrder != null) {
            for (key in savedOrder) {
                val identifier = identifierFromOrderKey(key) ?: continue
                val group = groups.firstOrNull { it.identifier == identifier } ?: continue
                if (!out.contains(group.orderKey)) {
                    out.add(group.orderKey)
                }
            }
        }
        for (group in groups) {
            if (!out.contains(group.orderKey)) {
                out.add(group.orderKey)
            }
        }
        return out
    }

    private fun orderKeyMatchesIdentifier(key: String, identifier: String): Boolean {
        return identifierFromOrderKey(key) == identifier
    }

    private fun identifierFromOrderKey(key: String?): String? {
        if (key.isNullOrBlank()) {
            return null
        }
        val hash = key.lastIndexOf('#')
        val at = key.lastIndexOf('@')
        if (hash < 0 || at <= hash + 1) {
            return null
        }
        return key.substring(hash + 1, at).takeIf { it.isNotBlank() }
    }

    private fun findGroup(identifier: String): PackArchiveGroup? {
        return archiveGroups.firstOrNull { it.identifier == identifier }
    }

    private fun inspectArchive(archive: Path): PackArchiveEntry? {
        var staging: Path? = null
        return try {
            staging = DlcPackArchive.extractToStaging(archive, DlcPaths.dlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            val pack = result.pack
            if (!result.valid || pack == null) return null
            val lang = Minecraft.getInstance().options.languageCode
            val displayName = DlcTextFormatter.parse(pack.metadata.resolveDisplayName(lang))
            val description = DlcMarkdownRenderer.renderMarkdownComponent(pack.metadata.resolveDescription(lang))
            val authors = pack.metadata.authors.ifEmpty { listOf(Component.translatable("dlc_manager.pack.unknown_author").string) }
            val author = DlcTextFormatter.parse(authors.joinToString(", "))
            val icon = loadIconTexture(pack.iconFile)
            PackArchiveEntry(archive.toAbsolutePath().normalize(), archiveOrderPath(archive), pack.metadata, displayName, description, author, authors, icon?.location, icon?.width ?: 0, icon?.height ?: 0)
        } catch (_: Exception) {
            null
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
    }

    private fun loadIconTexture(iconFile: Path?): IconTexture? {
        if (iconFile == null || !Files.isRegularFile(iconFile)) return null
        var image: NativeImage? = null
        return try {
            Files.newInputStream(iconFile).use { input ->
                image = NativeImage.read(input)
            }
            val loaded = image ?: return null
            val imageWidth = loaded.width
            val imageHeight = loaded.height
            val texture = DynamicTexture(loaded)
            image = null
            iconTextureSerial += 1
            val location = Minecraft.getInstance().textureManager.register("dlc_pack_icon/$iconTextureSerial", texture)
            IconTexture(location, imageWidth, imageHeight)
        } catch (_: Exception) {
            image?.close()
            null
        }
    }

    private fun releaseIconTextures() {
        for (group in archiveGroups) {
            for (entry in group.versions) {
                entry.iconTexture?.let { Minecraft.getInstance().textureManager.release(it) }
            }
        }
        archiveGroups = emptyList()
    }

    private fun loadInstalledPacks(): Map<String, DlcPackMeta> {
        val packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack")
        val out = HashMap<String, DlcPackMeta>()
        if (!Files.isDirectory(packRoot)) return out
        try {
            Files.list(packRoot).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach {
                    val meta = DlcPackMeta.load(it.resolve("meta.dc"))
                    if (meta != null) out[meta.identifier] = meta
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun uninstallIncompatibleInstalledPacks() {
        try {
            val reports = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstallIncompatibleForCurrentVersion()
            if (reports.isNotEmpty()) {
                status = Component.translatable("dlc_manager.pack.auto_uninstalled_incompatible", reports.size)
            }
        } catch (ex: Exception) {
            status = Component.translatable("dlc_manager.pack.auto_uninstall_failed", ex.message ?: ex.javaClass.simpleName)
        }
    }

    private fun hasPendingChanges(): Boolean = hasPackOperationChanges() || hasOrderChanges()

    private fun hasPackOperationChanges(): Boolean = pendingInstalls.isNotEmpty() || pendingUninstalls.isNotEmpty()

    private fun hasOrderChanges(): Boolean = currentArchiveOrder != entryArchiveOrder

    private fun canMoveUp(index: Int): Boolean = index > 0

    private fun canMoveDown(index: Int): Boolean = index >= 0 && index < archiveGroups.size - 1

    private val listLeft: Int get() = max(14, width / 2 - 235)
    private val listRight: Int get() = min(width - 14, width / 2 + 235)
    private val listTop: Int get() = 54
    private val listBottom: Int get() = max(listTop + 96, height - 42)
    private val maxScroll: Int get() = max(0, totalContentHeight() - (listBottom - listTop))

    private fun scrollbarDragOffset(mouseY: Double): Double {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return 0.0
        val track = max(1, listBottom - listTop)
        val barHeight = scrollBarHeight(track)
        val travel = max(1, track - barHeight)
        val barY = listTop + (travel * (scrollOffset / maxScrollValue.toDouble())).roundToInt()
        return if (mouseY >= barY && mouseY <= barY + barHeight) {
            mouseY - barY
        } else {
            barHeight / 2.0
        }
    }

    private fun scrollBarHeight(trackHeight: Int): Int {
        val totalHeight = max(trackHeight, totalContentHeight())
        return max(18, trackHeight * trackHeight / max(1, totalHeight)).coerceAtMost(trackHeight)
    }

    private fun updateOperationProgress(message: Component, progress: Float) {
        updateOperationProgress(message, progress, null)
    }

    private fun updateOperationProgress(message: Component, progress: Float, detail: Component?) {
        operationStatus = message
        operationProgress = progress
        operationDetail = detail
    }

    private fun operationDetailFor(raw: String?): Component? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "dlc_manager.pack.selecting_source") return null
        return when {
            value.startsWith("Downloading ") -> Component.translatable("dlc_manager.pack.downloading_file", value.removePrefix("Downloading "))
            value.startsWith("Resolved ") -> Component.translatable("dlc_manager.pack.resolved_file", value.removePrefix("Resolved "))
            value.startsWith("Installing ") -> Component.translatable("dlc_manager.pack.installing", value.removePrefix("Installing "))
            value.startsWith("Uninstalling installed DLC items") -> Component.translatable("dlc_manager.pack.operation_uninstalling")
            value.startsWith("Uninstalling ") -> Component.translatable("dlc_manager.pack.uninstalling", value.removePrefix("Uninstalling "))
            value.startsWith("Restoring ") -> Component.translatable("dlc_manager.pack.restoring_file", value.removePrefix("Restoring "))
            value.startsWith("Preparing ") -> Component.translatable("dlc_manager.pack.preparing_pack", value.removePrefix("Preparing "))
            value.startsWith("Backing up ") -> Component.translatable("dlc_manager.pack.backing_up_pack", value.removePrefix("Backing up "))
            value == "Applying runtime changes" -> Component.translatable("dlc_manager.pack.applying_runtime")
            value == "Writing metadata" -> Component.translatable("dlc_manager.pack.writing_metadata")
            value.startsWith("Installed ") -> Component.translatable("dlc_manager.pack.installed_pack", value.removePrefix("Installed "))
            value.startsWith("Uninstalled ") -> Component.translatable("dlc_manager.pack.uninstalled_pack", value.removePrefix("Uninstalled "))
            value.startsWith("dlc_manager.") -> Component.translatable(value)
            else -> DlcTextFormatter.parse(value)
        }
    }

    private fun rowHeight(group: PackArchiveGroup, right: Int): Int {
        val entry = group.displayEntry
        val textX = listLeft + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8
        val textRight = right - BUTTON_WIDTH - 18
        val textWidth = max(1, textRight - textX)
        val homepagePrompt = homepagePrompt(entry)
        val homepageWidth = homepagePrompt?.let { min((font.width(it) * HOMEPAGE_PROMPT_SCALE).roundToInt(), max(1, textWidth / 2)) } ?: 0
        val nameWidth = max(1, textWidth - if (homepagePrompt != null) homepageWidth + HOMEPAGE_PROMPT_GAP else 0)
        val nameLines = max(1, font.split(entry.displayName, nameWidth).size)
        val authorLines = authorLines(entry, textWidth).size
        val descriptionLines = max(1, font.split(entry.description, textWidth).size)
        val contentHeight = ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4 + VERSION_LINE_HEIGHT + 4 + descriptionLines * 10
        val actionCount = actionButtons(group).size
        val buttonHeight = max(BUTTON_HEIGHT, actionCount * BUTTON_HEIGHT + (actionCount - 1) * BUTTON_GAP)
        val iconHeight = ROW_PADDING_TOP + ICON_SIZE
        return max(ROW_HEIGHT, max(contentHeight, max(buttonHeight, iconHeight)) + ROW_PADDING_BOTTOM)
    }

    private fun homepagePrompt(entry: PackArchiveEntry): Component? {
        if (entry.metadata.links["homepage"].isNullOrBlank()) {
            return null
        }
        return Component.translatable("dlc_manager.pack.double_click_homepage")
    }

    private fun authorLines(entry: PackArchiveEntry, width: Int): List<List<Component>> {
        val prefix = Component.translatable("dlc_manager.pack.author_prefix")
        val separator = Component.literal(", ")
        val lines = ArrayList<ArrayList<Component>>()
        var current = ArrayList<Component>()
        var currentWidth = 0
        fun addParts(parts: List<Component>, allowNewLine: Boolean) {
            val partWidth = parts.sumOf { font.width(it) }
            if (allowNewLine && current.isNotEmpty() && currentWidth + partWidth > width) {
                lines.add(current)
                current = ArrayList()
                currentWidth = 0
            }
            current.addAll(parts)
            currentWidth += partWidth
        }
        fun addPart(part: Component, allowNewLine: Boolean) {
            val partWidth = font.width(part)
            if (allowNewLine && current.isNotEmpty() && currentWidth + partWidth > width) {
                lines.add(current)
                current = ArrayList()
                currentWidth = 0
            }
            current.add(part)
            currentWidth += partWidth
        }
        addPart(prefix, false)
        for ((index, author) in entry.authorNames.withIndex()) {
            val authorPart = DlcTextFormatter.parse(author)
            if (index > 0) {
                addParts(listOf(separator, authorPart), true)
            } else {
                addPart(authorPart, true)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current)
        }
        return lines.ifEmpty { listOf(listOf(prefix)) }
    }

    private fun totalContentHeight(): Int {
        val right = listRight - SCROLLBAR_WIDTH - 4
        var total = 0
        for (group in archiveGroups) {
            total += rowHeight(group, right)
        }
        return total
    }

    private fun rebuildPackWidgets() { initControls(); refreshPage() }

    override fun shouldCloseOnEsc(): Boolean = !busy || paused

    override fun onClose() {
        if (paused) {
            returnToParent()
        } else if (!busy) {
            onBackOrCancel()
        }
    }

    private class IconTexture(
        val location: ResourceLocation,
        val width: Int,
        val height: Int,
    )

    private class PackArchiveEntry(
        val archivePath: Path,
        val archiveOrderPath: String,
        val metadata: DlcPackMetadata,
        val displayName: Component,
        val description: Component,
        val author: Component,
        val authorNames: List<String>,
        val iconTexture: ResourceLocation?,
        val iconWidth: Int,
        val iconHeight: Int,
    ) {
        val displayNameText: String = displayName.string.ifBlank { metadata.identifier }
        val key: String = archiveOrderPath + "#" + metadata.identifier + "@" + metadata.version
    }

    private data class PackArchiveGroup(
        val identifier: String,
        val versions: List<PackArchiveEntry>,
        val orderKey: String,
        val selectedVersionKey: String,
    ) {
        val displayEntry: PackArchiveEntry
            get() = versions.firstOrNull { it.key == selectedVersionKey } ?: versions.first()
    }

    private enum class OperationDisplayMode {
        APPLYING,
        INSTALLING,
        UNINSTALLING,
    }

    private enum class BusyOperation {
        NONE,
        APPLY,
        IMPORT,
        DELETE,
    }

    private enum class RowAction {
        MOVE_UP,
        MOVE_DOWN,
        INSTALL,
        DELETE,
        FEEDBACK,
        SUPPORT,
    }

    companion object {
        private const val ROW_HEIGHT = 80
        private const val ROW_PADDING_TOP = 6
        private const val ROW_PADDING_BOTTOM = 8
        private const val ICON_SIZE = 32
        private const val BUTTON_WIDTH = 58
        private const val BUTTON_HEIGHT = 16
        private const val BUTTON_GAP = 4
        private const val VERSION_LINE_HEIGHT = 18




        private const val ORDER_BUTTON_WIDTH = 18

        private const val HOMEPAGE_PROMPT_GAP = 12
        private const val HOMEPAGE_PROMPT_SCALE = 0.85f
        private const val SCROLLBAR_WIDTH = 6

    }

    private data class VersionDropdownBounds(val x: Int, val y: Int, val width: Int, val height: Int)
    private val packAdapter = object : PackAdapter<PackArchiveGroup> {
        override fun details(group: PackArchiveGroup): PackDetails {
            val pack = group.displayEntry
            return PackDetails(pack.displayName, pack.description, pack.authorNames, pack.metadata.version, pack.iconTexture?.toString(), group.selectedVersionKey)
        }
        override fun versions(group: PackArchiveGroup): List<PackVersion> = group.versions.map { PackVersion(it.key, versionOptionLabel(it).string) }
        override fun actions(group: PackArchiveGroup): List<PackAction> {
            val pack = group.displayEntry
            val choices = actionButtons(group).mapTo(ArrayList<PackAction>()) { choice ->
                PackAction(actionLabel(group, choice).string, choice == RowAction.DELETE) {
                    when (choice) {
                        RowAction.INSTALL -> toggleInstall(group)
                        RowAction.DELETE -> confirmDeleteArchive(group)
                        RowAction.FEEDBACK -> confirmOpenLink(pack, "issue")
                        RowAction.SUPPORT -> confirmOpenLink(pack, "sponsor")
                        else -> {}
                    }
                }
            }
            if (!pack.metadata.links["homepage"].isNullOrBlank()) choices.add(PackAction(tr("dlc_manager.pack.homepage"), false) { confirmOpenLink(pack, "homepage") })
            return choices
        }
        override fun selectVersion(group: PackArchiveGroup, key: String) { selectPackVersion(group.identifier, key) }
        override fun move(index: Int, direction: Int) { moveEntry(index, direction) }
    }
    override fun canEditPacks(): Boolean = !busy && DlcConfig.isPackFuncOn()

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        pageRevision = archiveGroups.hashCode() + pendingInstalls.hashCode() + pendingUninstalls.hashCode() + busy.hashCode() + paused.hashCode() + cancelRequested.hashCode()

        if (!controlsReady) { controlsReady = true; refreshEntries(); initControls() }
        val list = section(null, null)
        live(content, { status?.string }, "page-description")

        if (busy) {
            live(list, { operationStatus?.string }, "section-title"); live(list, { operationDetail?.string }, "page-description"); progress(list) { operationProgress.toDouble() }
            if (busyOperation == BusyOperation.APPLY) {
                action(footer, tr(if (paused) "dlc_manager.pack.continue_install" else "dlc_manager.pack.pause_install")) { if (paused) resumePausedApply() else requestPauseApplyOperation() }.setEnabled(paused || !cancelRequested)
                action(footer, tr("dlc_manager.pack.cancel_install")) { requestCancelApplyOperation() }
            } else action(footer, tr("dlc_manager.pack.cancel")) { onBackOrCancel() }.setEnabled(!cancelRequested && busyOperation != BusyOperation.DELETE)
            return
        }
        if (!DlcConfig.isPackFuncOn()) text(list, tr("dlc_manager.pack.disabled"), "page-warning")

        if (archiveGroups.isEmpty()) text(list, tr("dlc_manager.ui.empty_packs"), "page-description")
        renderPackRows(list, archiveGroups, packAdapter)
        renderPackFooter(importButton, urlButton, backButton, applyButton, hasPendingChanges())
        completeButton?.let { mount(toolbar, it) }
    }
    override fun updatePage() { if (pageRevision != archiveGroups.hashCode() + pendingInstalls.hashCode() + pendingUninstalls.hashCode() + busy.hashCode() + paused.hashCode() + cancelRequested.hashCode()) refreshPage() }

}
