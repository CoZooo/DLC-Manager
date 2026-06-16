package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcFilePicker
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
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
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
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.pack.manager_title")) {
    private var importButton: Button? = null
    private var urlButton: Button? = null
    private var applyButton: Button? = null
    private var backButton: Button? = null
    private val pendingInstalls = LinkedHashMap<String, PackArchiveEntry>()
    private val pendingUninstalls = LinkedHashSet<String>()
    private var archiveEntries: List<PackArchiveEntry> = emptyList()
    private var committedArchiveOrder: List<String> = emptyList()
    private var currentArchiveOrder: List<String> = emptyList()
    private var installedByIdentifier: Map<String, DlcPackMeta> = emptyMap()
    private var selectedIndex = -1
    private var scrollIndex = 0
    private var draggingScrollbar = false
    private var lastClickKey: String? = null
    private var lastClickAt = 0L
    private var status: Component? = null
    @Volatile private var busy = false
    @Volatile private var cancelRequested = false
    @Volatile private var operationProgress = -1f
    @Volatile private var operationStatus: Component? = null
    private var iconTextureSerial = 0

    override fun init() {
        if (!busy) {
            refreshEntries()
        }
        clearWidgets()
        val buttonWidth = 104
        val buttonHeight = 20
        val y = height - 28
        if (busy) {
            val gap = 8
            val startX = width / 2 - buttonWidth - gap / 2
            val cancel = Button.builder(
                if (cancelRequested) Component.translatable("dlc_manager.pack.canceling") else Component.translatable("dlc_manager.pack.cancel")
            ) { onBackOrCancel() }
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build()
            cancel.active = !cancelRequested
            val back = Button.builder(Component.translatable("dlc_manager.pack.back")) {}
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build()
            back.active = false
            backButton = cancel
            addRenderableWidget(cancel)
            addRenderableWidget(back)
            return
        }
        val gap = 8
        val total = buttonWidth * 4 + gap * 3
        val startX = width / 2 - total / 2
        val import = Button.builder(Component.translatable("dlc_manager.pack.import")) { importPackArchive() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        import.active = DlcPathConfig.isPackFuncOn() && !busy
        val url = Button.builder(Component.translatable("dlc_manager.pack.import_url")) {
            Minecraft.getInstance().setScreen(DlcPackUrlScreen(this))
        }.bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight).build()
        url.active = DlcPathConfig.isPackFuncOn() && !busy
        val apply = Button.builder(Component.translatable("dlc_manager.pack.apply")) { applyPendingChanges() }
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build()
        apply.active = DlcPathConfig.isPackFuncOn() && hasPendingChanges() && !busy
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

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        if (busy) {
            renderOperation(guiGraphics)
            DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
            return
        }
        if (!DlcPathConfig.isPackFuncOn()) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack.disabled"), width / 2, height / 2 - 8, 0xFFCC66)
        } else {
            renderPackList(guiGraphics, mouseX, mouseY)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderOperation(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        var y = height / 2 - 44
        val progressText = if (operationProgress >= 0f) {
            "${(operationProgress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
        } else {
            "..."
        }
        val message = operationStatus ?: status ?: Component.translatable("dlc_manager.pack.applying")
        guiGraphics.drawCenteredString(font, formatOperationTitle(message, operationProgress, progressText), centerX, y, 0xE6E6E6)
        y += 24
        drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, operationProgress)
        guiGraphics.drawCenteredString(font, progressText, centerX, y + 14, 0xC8C8C8)
    }

    private fun renderPackList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val left = listLeft
        val right = listRight
        val top = listTop
        guiGraphics.drawString(font, Component.translatable("dlc_manager.pack.archive_count", archiveEntries.size), left, 38, 0xE6E6E6, false)
        status?.let { guiGraphics.drawString(font, it, left + 150, 38, 0xFFCC66, false) }
        if (archiveEntries.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack.none_archived"), width / 2, top + 36, 0x9A9A9A)
            return
        }
        clampScroll()
        val visible = min(visibleCount, archiveEntries.size - scrollIndex)
        for (row in 0 until visible) {
            val index = scrollIndex + row
            renderEntry(guiGraphics, archiveEntries[index], index, left, right - SCROLLBAR_WIDTH - 4, top + row * ROW_HEIGHT, mouseX, mouseY)
        }
        drawScrollBar(guiGraphics)
    }

    private fun renderEntry(guiGraphics: GuiGraphics, entry: PackArchiveEntry, index: Int, left: Int, right: Int, y: Int, mouseX: Int, mouseY: Int) {
        val background = if (index == selectedIndex) 0x66335566 else 0x55202020
        guiGraphics.fill(left, y, right, y + ROW_HEIGHT - 4, background)
        val arrowX = left + 4
        guiGraphics.drawString(font, Component.literal("^"), arrowX, y + 8, if (canMoveUp(index)) 0xFFFFFF else 0x666666, false)
        guiGraphics.drawString(font, Component.literal("v"), arrowX, y + 30, if (canMoveDown(index)) 0xFFFFFF else 0x666666, false)
        val iconX = left + 24
        val iconY = y + 16
        val iconTexture = entry.iconTexture
        if (iconTexture != null) {
            guiGraphics.blit(iconTexture, iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f, entry.iconWidth, entry.iconHeight, entry.iconWidth, entry.iconHeight)
        } else {
            guiGraphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3B3B3B.toInt())
            val initial = entry.displayNameText.ifBlank { "P" }.substring(0, 1).uppercase(Locale.ROOT)
            guiGraphics.drawCenteredString(font, Component.literal(initial), iconX + ICON_SIZE / 2, iconY + 12, 0xFFFFFF)
        }
        val textX = iconX + ICON_SIZE + 8
        val textRight = right - (BUTTON_WIDTH + 5) * 4 - 14
        guiGraphics.drawString(font, trimToWidth(entry.displayName, textRight - textX), textX, y + 6, 0xFFFFFF, false)
        guiGraphics.drawString(font, trimToWidth(entry.author, textRight - textX), textX, y + 20, 0xC8C8C8, false)
        guiGraphics.drawString(font, Component.translatable("dlc_manager.pack.version_line", entry.metadata.version), textX, y + 34, 0xAFC8FF, false)
        guiGraphics.drawString(font, trimToWidth(entry.description, textRight - textX), textX, y + 48, 0xB0B0B0, false)
        val buttonX = right - (BUTTON_WIDTH + 5) * 4
        drawRowButton(guiGraphics, buttonX, y + 18, installButtonLabel(entry), mouseX, mouseY)
        drawRowButton(guiGraphics, buttonX + BUTTON_WIDTH + 5, y + 18, Component.translatable("dlc_manager.pack.delete"), mouseX, mouseY)
        drawRowButton(guiGraphics, buttonX + (BUTTON_WIDTH + 5) * 2, y + 18, Component.translatable("dlc_manager.pack.feedback"), mouseX, mouseY)
        drawRowButton(guiGraphics, buttonX + (BUTTON_WIDTH + 5) * 3, y + 18, Component.translatable("dlc_manager.pack.support_author"), mouseX, mouseY)
    }

    private fun drawRowButton(guiGraphics: GuiGraphics, x: Int, y: Int, label: Component, mouseX: Int, mouseY: Int) {
        val hover = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT
        guiGraphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, if (hover) 0xFF5A5A5A.toInt() else 0xFF404040.toInt())
        guiGraphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2, y + 4, 0xFFFFFF)
    }

    private fun installButtonLabel(entry: PackArchiveEntry): Component {
        if (pendingInstalls[entry.metadata.identifier] === entry) return pendingInstallButtonLabel(entry)
        if (pendingUninstalls.contains(entry.metadata.identifier)) return Component.translatable("dlc_manager.pack.pending_uninstall")
        val installed = installedByIdentifier[entry.metadata.identifier]
        if (installed != null && entry.metadata.version == installed.version) return Component.translatable("dlc_manager.pack.uninstall")
        if (installed != null) {
            val compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version)
            if (compare > 0) return Component.translatable("dlc_manager.pack.upgrade")
            if (compare < 0) return Component.translatable("dlc_manager.pack.downgrade")
            return Component.translatable("dlc_manager.pack.replace")
        }
        return Component.translatable("dlc_manager.pack.install")
    }

    private fun pendingInstallButtonLabel(entry: PackArchiveEntry): Component {
        val installed = installedByIdentifier[entry.metadata.identifier] ?: return Component.translatable("dlc_manager.pack.pending_install")
        val compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version)
        if (compare > 0) return Component.translatable("dlc_manager.pack.pending_upgrade")
        if (compare < 0) return Component.translatable("dlc_manager.pack.pending_downgrade")
        return Component.translatable("dlc_manager.pack.pending_replace")
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (busy || archiveEntries.isEmpty() || mouseY < listTop || mouseY > listBottom) return false
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return false
        scrollIndex = max(0, min(maxScrollValue, scrollIndex + if (deltaY > 0) -1 else 1))
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!busy && button == 0) {
            if (isInsideScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true
                updateScrollFromMouse(mouseY)
                return true
            }
            val row = rowAt(mouseY)
            if (row >= 0) {
                val index = scrollIndex + row
                if (index in archiveEntries.indices) {
                    return handleEntryClick(archiveEntries[index], index, mouseX, mouseY)
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun handleEntryClick(entry: PackArchiveEntry, index: Int, mouseX: Double, mouseY: Double): Boolean {
        selectedIndex = index
        val rowTop = listTop + (index - scrollIndex) * ROW_HEIGHT
        val left = listLeft
        val right = listRight - SCROLLBAR_WIDTH - 4
        val arrowX = left + 4
        if (mouseX >= arrowX && mouseX < arrowX + 12) {
            if (mouseY >= rowTop + 4 && mouseY < rowTop + 24) {
                moveEntry(index, -1)
                return true
            }
            if (mouseY >= rowTop + 26 && mouseY < rowTop + 48) {
                moveEntry(index, 1)
                return true
            }
        }
        val buttonX = right - (BUTTON_WIDTH + 5) * 4
        if (mouseY >= rowTop + 18 && mouseY < rowTop + 18 + BUTTON_HEIGHT && mouseX >= buttonX) {
            val action = ((mouseX - buttonX) / (BUTTON_WIDTH + 5)).toInt()
            if (action in 0..3) {
                when (action) {
                    0 -> toggleInstall(entry)
                    1 -> deleteArchive(entry)
                    2 -> openLink(entry, "issue")
                    3 -> openLink(entry, "sponsor")
                }
                return true
            }
        }
        val now = Util.getMillis()
        if (entry.key == lastClickKey && now - lastClickAt < 350L) {
            openLink(entry, "homepage")
        }
        lastClickKey = entry.key
        lastClickAt = now
        rebuildPackWidgets()
        return true
    }

    private fun toggleInstall(entry: PackArchiveEntry) {
        val identifier = entry.metadata.identifier
        if (pendingInstalls[identifier] === entry) {
            pendingInstalls.remove(identifier)
        } else if (pendingUninstalls.remove(identifier)) {
            pendingInstalls[identifier] = entry
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

    private fun deleteArchive(entry: PackArchiveEntry) {
        val installed = installedByIdentifier[entry.metadata.identifier]
        if (installed != null && entry.metadata.version == installed.version) {
            status = Component.translatable("dlc_manager.pack.delete_installed_blocked")
            rebuildPackWidgets()
            return
        }
        try {
            Files.deleteIfExists(entry.archivePath)
            if (pendingInstalls[entry.metadata.identifier] === entry) pendingInstalls.remove(entry.metadata.identifier)
            status = Component.translatable("dlc_manager.pack.delete_success", entry.displayName)
            refreshEntries()
            rebuildPackWidgets()
        } catch (ex: Exception) {
            status = Component.translatable("dlc_manager.pack.delete_failed", ex.message ?: ex.javaClass.simpleName)
        }
    }

    private fun openLink(entry: PackArchiveEntry, key: String) {
        val url = entry.metadata.links[key]
        if (url.isNullOrBlank()) {
            status = Component.translatable("dlc_manager.pack.link_missing")
            rebuildPackWidgets()
            return
        }
        Util.getPlatform().openUri(url)
    }

    private fun moveEntry(index: Int, direction: Int) {
        val target = index + direction
        if (target !in archiveEntries.indices) return
        val mutable = ArrayList(archiveEntries)
        val entry = mutable.removeAt(index)
        mutable.add(target, entry)
        archiveEntries = mutable
        currentArchiveOrder = collectArchiveKeys(archiveEntries)
        selectedIndex = target
        ensureSelectionVisible()
        rebuildPackWidgets()
    }

    private fun importPackArchive() {
        if (!DlcPathConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled")
            return
        }
        val zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").string) ?: return
        setBusy(true, Component.translatable("dlc_manager.pack.importing"))
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
        val target = uniquePath(packRoot, fileName)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    private fun uniquePath(dir: Path, fileName: String): Path {
        val target = dir.resolve(fileName)
        if (!Files.exists(target)) return target
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        for (i in 1 until 10000) {
            val candidate = dir.resolve("$base-$i$ext")
            if (!Files.exists(candidate)) return candidate
        }
        return dir.resolve("$base-${System.nanoTime()}$ext")
    }

    private fun applyPendingChanges() {
        if (!hasPendingChanges() || busy) return
        val confirmText = buildReplacementConfirmText()
        if (confirmText != null) {
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
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
                ConfirmScreen(
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

    private fun startApplyPendingChanges(allowRuntimeChanges: Boolean) {
        setBusy(true, Component.translatable("dlc_manager.pack.applying"))
        updateOperationProgress(Component.translatable("dlc_manager.pack.applying"), 0f)
        Thread({
            try {
                val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                applyOrderedChanges(installer, allowRuntimeChanges)
                pendingUninstalls.clear()
                pendingInstalls.clear()
                finishBusy(Component.translatable("dlc_manager.pack.apply_success"))
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                finishBusy(Component.translatable("dlc_manager.pack.canceled"))
            } catch (ex: Exception) {
                finishBusy(Component.translatable("dlc_manager.pack.apply_failed", describeFailure(ex)))
            }
        }, "dlc-pack-apply").start()
    }

    private fun applyOrderedChanges(installer: DlcPackInstaller, allowRuntimeChanges: Boolean) {
        val ordered = archiveEntries.sortedBy { orderIndex(it.key) }
        val reinstallIdentifiers = collectAffectedReinstallIdentifiers(ordered)
        val displayMode = determineOperationDisplayMode(reinstallIdentifiers)
        val uninstalled = LinkedHashSet<String>()
        for (entry in ordered) {
            val identifier = entry.metadata.identifier
            if (!uninstalled.contains(identifier) && isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier))) {
                checkCanceled()
                updateOperationProgress(operationStatusFor(displayMode, entry), -1f)
                installer.uninstall(identifier) { message, progress ->
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(0f, 0.35f, progress))
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
                updateOperationProgress(operationStatusFor(displayMode, entry), 0.35f)
                installArchive(installer, entry, allowRuntimeChanges, displayMode)
                installed.add(identifier)
            }
        }
        checkCanceled()
        committedArchiveOrder = currentArchiveOrder.toList()
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder)
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
        for (entry in archiveEntries) {
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
        for (entry in archiveEntries) {
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
    private fun installArchive(installer: DlcPackInstaller, entry: PackArchiveEntry, allowRuntimeChanges: Boolean, displayMode: OperationDisplayMode) {
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
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(0.35f, 0.65f, progress))
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
        return when (mode) {
            OperationDisplayMode.UNINSTALLING -> Component.translatable("dlc_manager.pack.operation_uninstalling")
            OperationDisplayMode.INSTALLING -> Component.translatable("dlc_manager.pack.operation_installing")
            OperationDisplayMode.APPLYING -> Component.translatable("dlc_manager.pack.operation_applying")
        }
    }

    private fun formatOperationTitle(message: Component, progress: Float, progressText: String): Component {
        val text = message.string.trimEnd('.', '。', '…')
        return if (progress >= 0f) {
            Component.literal("$text：$progressText")
        } else {
            Component.literal("$text...")
        }
    }

    private fun describeFailure(ex: Exception): String {
        val message = ex.message
        return if (message.isNullOrBlank()) ex.javaClass.simpleName else message
    }

    private fun setBusy(value: Boolean, message: Component) {
        if (value) {
            cancelRequested = false
            operationProgress = -1f
            operationStatus = message
        }
        busy = value
        status = message
        rebuildPackWidgets()
    }

    private fun finishBusy(message: Component) {
        Minecraft.getInstance().execute {
            busy = false
            cancelRequested = false
            operationProgress = -1f
            operationStatus = null
            status = message
            refreshEntries()
            rebuildPackWidgets()
        }
    }

    private fun onBackOrCancel() {
        if (busy) {
            cancelRequested = true
            status = Component.translatable("dlc_manager.pack.canceling")
            operationStatus = status
            rebuildPackWidgets()
            return
        }
        if (hasPendingChanges()) {
            pendingInstalls.clear()
            pendingUninstalls.clear()
            currentArchiveOrder = committedArchiveOrder.toList()
            sortArchiveEntriesByCurrentOrder()
            status = Component.translatable("dlc_manager.pack.changes_cancelled")
            rebuildPackWidgets()
            return
        }
        releaseIconTextures()
        Minecraft.getInstance().setScreen(parent)
    }

    private fun refreshEntries() {
        val pendingInstallKeys = pendingInstalls.mapValues { it.value.key }
        releaseIconTextures()
        uninstallIncompatibleInstalledPacks()
        installedByIdentifier = loadInstalledPacks()
        val scanned = scanPackArchives()
        val keys = collectArchiveKeys(scanned)
        val saved = DlcPackOrder.load(packOrderPath())
        val preserveCurrentOrder = currentArchiveOrder.isNotEmpty() && hasOrderChanges()
        committedArchiveOrder = DlcPackOrder.normalizeOrder(saved, keys)
        currentArchiveOrder = if (preserveCurrentOrder) {
            DlcPackOrder.normalizeOrder(currentArchiveOrder, keys)
        } else {
            committedArchiveOrder
        }
        archiveEntries = scanned
        sortArchiveEntriesByCurrentOrder()
        pendingInstalls.clear()
        for (entry in archiveEntries) {
            if (entry.key == pendingInstallKeys[entry.metadata.identifier]) {
                pendingInstalls[entry.metadata.identifier] = entry
            }
        }
        pendingUninstalls.removeIf { !installedByIdentifier.containsKey(it) }
        if (selectedIndex >= archiveEntries.size) selectedIndex = if (archiveEntries.isEmpty()) -1 else archiveEntries.size - 1
        clampScroll()
    }

    private fun packOrderPath(): Path {
        return DlcPaths.dlcRoot().resolve("pack").resolve("order.dc")
    }

    private fun collectArchiveKeys(entries: List<PackArchiveEntry>): List<String> {
        return entries.map { it.key }
    }

    private fun orderIndex(key: String): Int {
        val index = currentArchiveOrder.indexOf(key)
        return if (index < 0) Int.MAX_VALUE else index
    }

    private fun committedOrderIndex(key: String): Int {
        return committedArchiveOrder.indexOf(key)
    }

    private fun sortArchiveEntriesByCurrentOrder() {
        archiveEntries = archiveEntries.sortedBy { orderIndex(it.key) }
    }

    private fun scanPackArchives(): List<PackArchiveEntry> {
        val packRoot = DlcPaths.dlcRoot().resolve("pack")
        try {
            Files.createDirectories(packRoot)
        } catch (_: IOException) {
        }
        if (!Files.isDirectory(packRoot)) return emptyList()
        val out = ArrayList<PackArchiveEntry>()
        try {
            Files.list(packRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip") }
                    .sorted { a, b -> a.fileName.toString().lowercase(Locale.ROOT).compareTo(b.fileName.toString().lowercase(Locale.ROOT)) }
                    .forEach { inspectArchive(it)?.let(out::add) }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun inspectArchive(archive: Path): PackArchiveEntry? {
        var staging: Path? = null
        return try {
            staging = DlcPackArchive.extractToStaging(archive, DlcPaths.dlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            val pack = result.pack
            if (!result.valid || pack == null) return null
            val lang = Minecraft.getInstance().options.languageCode
            val displayName = pack.metadata.displayNameComponent(lang)
            val description = pack.metadata.descriptionComponent(lang)
            val author = if (pack.metadata.authors.isEmpty()) {
                Component.translatable("dlc_manager.pack.unknown_author")
            } else {
                DlcTextFormatter.parse(pack.metadata.authors.joinToString(", "))
            }
            val icon = loadIconTexture(pack.iconFile)
            PackArchiveEntry(archive.toAbsolutePath().normalize(), pack.metadata, displayName, description, author, icon?.location, icon?.width ?: 0, icon?.height ?: 0)
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
        for (entry in archiveEntries) {
            entry.iconTexture?.let { Minecraft.getInstance().textureManager.release(it) }
        }
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

    private fun hasPendingChanges(): Boolean = pendingInstalls.isNotEmpty() || pendingUninstalls.isNotEmpty() || hasOrderChanges()

    private fun hasOrderChanges(): Boolean = currentArchiveOrder != committedArchiveOrder

    private fun canMoveUp(index: Int): Boolean = index > 0

    private fun canMoveDown(index: Int): Boolean = index >= 0 && index < archiveEntries.size - 1

    private val listLeft: Int get() = max(14, width / 2 - 235)
    private val listRight: Int get() = min(width - 14, width / 2 + 235)
    private val listTop: Int get() = 54
    private val listBottom: Int get() = max(listTop + ROW_HEIGHT, height - 42)
    private val visibleCount: Int get() = max(1, (listBottom - listTop) / ROW_HEIGHT)
    private val maxScroll: Int get() = max(0, archiveEntries.size - visibleCount)

    private fun rowAt(mouseY: Double): Int {
        if (mouseY < listTop || mouseY >= listBottom) return -1
        val row = ((mouseY - listTop) / ROW_HEIGHT).toInt()
        return if (row < visibleCount) row else -1
    }

    private fun clampScroll() {
        scrollIndex = max(0, min(maxScroll, scrollIndex))
    }

    private fun ensureSelectionVisible() {
        if (selectedIndex < 0) return
        val visible = visibleCount
        if (selectedIndex < scrollIndex) {
            scrollIndex = selectedIndex
        } else if (selectedIndex >= scrollIndex + visible) {
            scrollIndex = max(0, selectedIndex - visible + 1)
        }
        clampScroll()
    }

    private fun isInsideScrollbar(mouseX: Double, mouseY: Double): Boolean {
        if (maxScroll <= 0) return false
        val x = listRight - SCROLLBAR_WIDTH
        return mouseX >= x && mouseX <= x + SCROLLBAR_WIDTH && mouseY >= listTop && mouseY <= listBottom
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return
        val track = max(1, listBottom - listTop)
        val ratio = max(0.0, min(1.0, (mouseY - listTop) / track))
        scrollIndex = max(0, min(maxScrollValue, (ratio * maxScrollValue).roundToInt()))
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return
        val trackHeight = max(1, listBottom - listTop)
        val barHeight = max(18, trackHeight * visibleCount / max(1, archiveEntries.size))
        val barY = listTop + (trackHeight - barHeight) * scrollIndex / max(1, maxScrollValue)
        val x = listRight - SCROLLBAR_WIDTH
        guiGraphics.fill(x, listTop, x + SCROLLBAR_WIDTH, listBottom, 0x66333333)
        guiGraphics.fill(x, barY, x + SCROLLBAR_WIDTH, barY + barHeight, 0xFF66CCFF.toInt())
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val filled = if (progress >= 0f) (width * progress.coerceIn(0f, 1f)).roundToInt() else 0
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B.toInt())
        if (progress < 0f) {
            val pulse = ((Util.getMillis() / 12L) % width).toInt()
            guiGraphics.fill(x + pulse.coerceAtMost(width - 24), y, x + (pulse + 24).coerceAtMost(width), y + height, 0xFF66CCFF.toInt())
        } else {
            guiGraphics.fill(x, y, x + filled, y + height, 0xFF66CCFF.toInt())
        }
    }

    private fun updateOperationProgress(message: Component, progress: Float) {
        operationStatus = message
        operationProgress = progress
    }

    private fun trimToWidth(text: Component, width: Int): FormattedCharSequence {
        if (font.width(text) <= width) return text.visualOrderText
        return font.split(text, max(1, width)).firstOrNull() ?: FormattedCharSequence.EMPTY
    }

    private fun rebuildPackWidgets() {
        init(Minecraft.getInstance(), width, height)
    }

    override fun shouldCloseOnEsc(): Boolean = !busy

    override fun onClose() {
        if (!busy) {
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
        val metadata: DlcPackMetadata,
        val displayName: Component,
        val description: Component,
        val author: Component,
        val iconTexture: ResourceLocation?,
        val iconWidth: Int,
        val iconHeight: Int,
    ) {
        val displayNameText: String = displayName.string.ifBlank { metadata.identifier }
        val key: String = archivePath.toString() + "#" + metadata.identifier + "@" + metadata.version
    }

    private enum class OperationDisplayMode {
        APPLYING,
        INSTALLING,
        UNINSTALLING,
    }

    companion object {
        private const val ROW_HEIGHT = 72
        private const val ICON_SIZE = 32
        private const val BUTTON_WIDTH = 46
        private const val BUTTON_HEIGHT = 16
        private const val SCROLLBAR_WIDTH = 6
    }
}
