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
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
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
    private var completeButton: Button? = null
    private val pendingInstalls = LinkedHashMap<String, PackArchiveEntry>()
    private val pendingUninstalls = LinkedHashSet<String>()
    private var archiveEntries: List<PackArchiveEntry> = emptyList()
    private var committedArchiveOrder: List<String> = emptyList()
    private var currentArchiveOrder: List<String> = emptyList()
    private var installedByIdentifier: Map<String, DlcPackMeta> = emptyMap()
    private var selectedIndex = -1
    private var scrollOffset = 0
    private var draggingScrollbar = false
    private var scrollbarDragOffset = 0.0
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
        addCompleteButton()
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

    private fun addCompleteButton() {
        if (!DlcPathConfig.isPackFuncOn() || !DlcRemoteManager.hasMissingDlcPacks()) {
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
        var rowY = top - scrollOffset
        var index = 0
        val contentRight = right - SCROLLBAR_WIDTH - 4
        guiGraphics.enableScissor(left, top, contentRight, listBottom)
        while (index < archiveEntries.size && rowY < listBottom) {
            val entry = archiveEntries[index]
            val rowHeight = rowHeight(entry, contentRight)
            if (rowY + rowHeight > top) {
                renderEntry(guiGraphics, entry, index, left, contentRight, rowY, rowHeight, mouseX, mouseY)
            }
            rowY += rowHeight
            index++
        }
        guiGraphics.disableScissor()
        drawScrollBar(guiGraphics)
    }

    private fun renderEntry(guiGraphics: GuiGraphics, entry: PackArchiveEntry, index: Int, left: Int, right: Int, y: Int, rowHeight: Int, mouseX: Int, mouseY: Int) {
        val background = if (index == selectedIndex) 0x66335566 else 0x55202020
        guiGraphics.fill(left, y, right, y + rowHeight - 4, background)
        drawOrderButtons(guiGraphics, index, left + 4, y, rowHeight, mouseX, mouseY)
        val iconX = left + ORDER_BUTTON_WIDTH + 12
        val iconY = y + ROW_PADDING_TOP
        val iconTexture = entry.iconTexture
        if (iconTexture != null) {
            guiGraphics.blit(iconTexture, iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f, entry.iconWidth, entry.iconHeight, entry.iconWidth, entry.iconHeight)
        } else {
            guiGraphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3B3B3B.toInt())
            val initial = entry.displayNameText.ifBlank { "P" }.substring(0, 1).uppercase(Locale.ROOT)
            guiGraphics.drawCenteredString(font, Component.literal(initial), iconX + ICON_SIZE / 2, iconY + 12, 0xFFFFFF)
        }
        val textX = iconX + ICON_SIZE + 8
        val textRight = right - BUTTON_WIDTH - 18
        val homepagePrompt = homepagePrompt(entry)
        val homepageWidth = homepagePrompt?.let { min((font.width(it) * HOMEPAGE_PROMPT_SCALE).roundToInt(), max(1, (textRight - textX) / 2)) } ?: 0
        val nameWidth = max(1, textRight - textX - if (homepagePrompt != null) homepageWidth + HOMEPAGE_PROMPT_GAP else 0)
        var textY = y + ROW_PADDING_TOP
        textY = drawMultiline(guiGraphics, entry.displayName, textX, textY, nameWidth, 0xFFFFFF) + 4
        if (homepagePrompt != null) {
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(HOMEPAGE_PROMPT_SCALE, HOMEPAGE_PROMPT_SCALE, 1.0f)
            val promptX = textRight - homepageWidth
            val promptY = y + ROW_PADDING_TOP + 1
            guiGraphics.drawString(font, homepagePrompt, (promptX / HOMEPAGE_PROMPT_SCALE).roundToInt(), (promptY / HOMEPAGE_PROMPT_SCALE).roundToInt(), 0x8FA8C8, false)
            guiGraphics.pose().popPose()
        }
        textY = drawAuthorLines(guiGraphics, entry, textX, textY, textRight - textX, 0xC8C8C8) + 4
        guiGraphics.drawString(font, Component.translatable("dlc_manager.pack.version_line", entry.metadata.version), textX, textY, 0xAFC8FF, false)
        drawMultiline(guiGraphics, entry.description, textX, textY + 14, textRight - textX, 0xB0B0B0)
        drawActionButtons(guiGraphics, entry, right - BUTTON_WIDTH - 6, y, rowHeight, mouseX, mouseY)
    }

    private fun drawRowButton(guiGraphics: GuiGraphics, x: Int, y: Int, label: Component, mouseX: Int, mouseY: Int) {
        drawSizedButton(guiGraphics, x, y, BUTTON_WIDTH, label, mouseX, mouseY)
    }

    private fun drawSizedButton(guiGraphics: GuiGraphics, x: Int, y: Int, buttonWidth: Int, label: Component, mouseX: Int, mouseY: Int) {
        val hover = mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + BUTTON_HEIGHT
        guiGraphics.fill(x, y, x + buttonWidth, y + BUTTON_HEIGHT, if (hover) 0xFF5A5A5A.toInt() else 0xFF404040.toInt())
        guiGraphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 4, 0xFFFFFF)
    }

    private fun drawOrderButtons(guiGraphics: GuiGraphics, index: Int, x: Int, rowY: Int, rowHeight: Int, mouseX: Int, mouseY: Int) {
        val startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS)
        if (canMoveUp(index)) {
            drawSizedButton(guiGraphics, x, startY, ORDER_BUTTON_WIDTH, Component.literal("^"), mouseX, mouseY)
        }
        if (canMoveDown(index)) {
            drawSizedButton(guiGraphics, x, startY + BUTTON_HEIGHT + BUTTON_GAP, ORDER_BUTTON_WIDTH, Component.literal("v"), mouseX, mouseY)
        }
    }

    private fun drawActionButtons(guiGraphics: GuiGraphics, entry: PackArchiveEntry, x: Int, rowY: Int, rowHeight: Int, mouseX: Int, mouseY: Int) {
        val actions = actionButtons(entry)
        val startY = centeredButtonColumnY(rowY, rowHeight, actions.size)
        for ((index, action) in actions.withIndex()) {
            drawRowButton(guiGraphics, x, startY + index * (BUTTON_HEIGHT + BUTTON_GAP), actionLabel(entry, action), mouseX, mouseY)
        }
    }

    private fun centeredButtonColumnY(rowY: Int, rowHeight: Int, count: Int): Int {
        if (count <= 0) return rowY + (rowHeight - 4) / 2
        val totalHeight = count * BUTTON_HEIGHT + (count - 1) * BUTTON_GAP
        return rowY + max(0, (rowHeight - 4 - totalHeight) / 2)
    }

    private fun drawMultiline(guiGraphics: GuiGraphics, text: Component, x: Int, y: Int, width: Int, color: Int): Int {
        val lines = font.split(text, max(1, width))
        for ((index, line) in lines.withIndex()) {
            guiGraphics.drawString(font, line, x, y + index * 10, color, false)
        }
        return y + lines.size * 10
    }

    private fun drawAuthorLines(guiGraphics: GuiGraphics, entry: PackArchiveEntry, x: Int, y: Int, width: Int, color: Int): Int {
        val lines = authorLines(entry, width)
        for ((lineIndex, line) in lines.withIndex()) {
            var cursor = x
            for (part in line) {
                guiGraphics.drawString(font, part, cursor, y + lineIndex * 10, color, false)
                cursor += font.width(part)
            }
        }
        return y + lines.size * 10
    }

    private fun installButtonLabel(entry: PackArchiveEntry): Component {
        if (pendingInstalls[entry.metadata.identifier] === entry) return Component.translatable("dlc_manager.pack.pending_install")
        if (pendingUninstalls.contains(entry.metadata.identifier)) return Component.translatable("dlc_manager.pack.pending_uninstall")
        val installed = installedByIdentifier[entry.metadata.identifier]
        if (installed != null && entry.metadata.version == installed.version) return Component.translatable("dlc_manager.pack.uninstall")
        return Component.translatable("dlc_manager.pack.install")
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (busy || archiveEntries.isEmpty() || mouseY < listTop || mouseY > listBottom) return false
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return false
        scrollOffset = max(0, min(maxScrollValue, scrollOffset + if (deltaY > 0) -SCROLL_STEP else SCROLL_STEP))
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!busy && button == 0) {
            if (isInsideScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true
                scrollbarDragOffset = scrollbarDragOffset(mouseY)
                updateScrollFromMouse(mouseY)
                return true
            }
            val index = entryAt(mouseY)
            if (index in archiveEntries.indices) {
                return handleEntryClick(archiveEntries[index], index, mouseX, mouseY)
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
        scrollbarDragOffset = 0.0
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun handleEntryClick(entry: PackArchiveEntry, index: Int, mouseX: Double, mouseY: Double): Boolean {
        selectedIndex = index
        val left = listLeft
        val right = listRight - SCROLLBAR_WIDTH - 4
        val rowTop = rowTop(index, right) ?: return true
        val rowHeight = rowHeight(entry, right)
        val arrowX = left + 4
        val orderAction = orderButtonAt(index, arrowX, rowTop, rowHeight, mouseX, mouseY)
        if (orderAction != null) {
            moveEntry(index, if (orderAction == RowAction.MOVE_UP) -1 else 1)
            return true
        }
        val rowAction = actionButtonAt(entry, right - BUTTON_WIDTH - 6, rowTop, rowHeight, mouseX, mouseY)
        if (rowAction != null) {
            when (rowAction) {
                RowAction.INSTALL -> toggleInstall(entry)
                RowAction.DELETE -> confirmDeleteArchive(entry)
                RowAction.FEEDBACK -> confirmOpenLink(entry, "issue")
                RowAction.SUPPORT -> confirmOpenLink(entry, "sponsor")
                else -> {}
            }
            return true
        }
        val now = Util.getMillis()
        if (entry.key == lastClickKey && now - lastClickAt < 350L) {
            confirmOpenLink(entry, "homepage")
        }
        lastClickKey = entry.key
        lastClickAt = now
        rebuildPackWidgets()
        return true
    }

    private fun toggleInstall(entry: PackArchiveEntry) {
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

    private fun confirmDeleteArchive(entry: PackArchiveEntry) {
        val uninstallFirst = isEntryCurrentlyInstalled(entry)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (accepted) {
                        startDeleteArchive(entry, uninstallFirst)
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

    private fun startDeleteArchive(entry: PackArchiveEntry, uninstallFirst: Boolean) {
        if (uninstallFirst) {
            setBusy(true, Component.translatable("dlc_manager.pack.operation_uninstalling"))
            Thread({
                try {
                    val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                    installer.uninstall(entry.metadata.identifier) { _, progress ->
                        updateOperationProgress(Component.translatable("dlc_manager.pack.operation_uninstalling"), progress)
                    }
                    deleteArchiveFile(entry)
                    finishBusy(Component.translatable("dlc_manager.pack.delete_uninstalled_success", entry.displayName), true)
                } catch (ex: Exception) {
                    finishBusy(Component.translatable("dlc_manager.pack.delete_failed", describeFailure(ex)))
                }
            }, "dlc-pack-delete").start()
            return
        }
        try {
            deleteArchiveFile(entry)
            status = Component.translatable("dlc_manager.pack.delete_success", entry.displayName)
            refreshEntries()
            rebuildPackWidgets()
        } catch (ex: Exception) {
            status = Component.translatable("dlc_manager.pack.delete_failed", ex.message ?: ex.javaClass.simpleName)
        }
    }

    @Throws(IOException::class)
    private fun deleteArchiveFile(entry: PackArchiveEntry) {
        Files.deleteIfExists(entry.archivePath)
        if (pendingInstalls[entry.metadata.identifier] === entry) pendingInstalls.remove(entry.metadata.identifier)
        pendingUninstalls.remove(entry.metadata.identifier)
        currentArchiveOrder = currentArchiveOrder.filter { it != entry.key }
        committedArchiveOrder = committedArchiveOrder.filter { it != entry.key }
    }

    private fun confirmOpenLink(entry: PackArchiveEntry, key: String) {
        val url = entry.metadata.links[key]
        if (url.isNullOrBlank()) {
            return
        }
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
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

    private fun openLink(entry: PackArchiveEntry, key: String) {
        val url = entry.metadata.links[key]
        if (url.isNullOrBlank()) {
            return
        }
        Util.getPlatform().openUri(url)
    }

    private fun orderButtons(index: Int): List<RowAction> {
        val out = ArrayList<RowAction>(2)
        if (canMoveUp(index)) out.add(RowAction.MOVE_UP)
        if (canMoveDown(index)) out.add(RowAction.MOVE_DOWN)
        return out
    }

    private fun actionButtons(entry: PackArchiveEntry): List<RowAction> {
        val out = ArrayList<RowAction>(4)
        out.add(RowAction.INSTALL)
        out.add(RowAction.DELETE)
        if (!entry.metadata.links["issue"].isNullOrBlank()) out.add(RowAction.FEEDBACK)
        if (!entry.metadata.links["sponsor"].isNullOrBlank()) out.add(RowAction.SUPPORT)
        return out
    }

    private fun actionLabel(entry: PackArchiveEntry, action: RowAction): Component {
        return when (action) {
            RowAction.INSTALL -> installButtonLabel(entry)
            RowAction.DELETE -> Component.translatable("dlc_manager.pack.delete")
            RowAction.FEEDBACK -> Component.translatable("dlc_manager.pack.feedback")
            RowAction.SUPPORT -> Component.translatable("dlc_manager.pack.support_author")
            RowAction.MOVE_UP -> Component.literal("^")
            RowAction.MOVE_DOWN -> Component.literal("v")
        }
    }

    private fun orderButtonAt(index: Int, x: Int, rowY: Int, rowHeight: Int, mouseX: Double, mouseY: Double): RowAction? {
        val startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS)
        if (canMoveUp(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= startY && mouseY < startY + BUTTON_HEIGHT) {
            return RowAction.MOVE_UP
        }
        val downY = startY + BUTTON_HEIGHT + BUTTON_GAP
        if (canMoveDown(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= downY && mouseY < downY + BUTTON_HEIGHT) {
            return RowAction.MOVE_DOWN
        }
        return null
    }

    private fun actionButtonAt(entry: PackArchiveEntry, x: Int, rowY: Int, rowHeight: Int, mouseX: Double, mouseY: Double): RowAction? {
        val buttons = actionButtons(entry)
        val startY = centeredButtonColumnY(rowY, rowHeight, buttons.size)
        for ((buttonIndex, action) in buttons.withIndex()) {
            val y = startY + buttonIndex * (BUTTON_HEIGHT + BUTTON_GAP)
            if (mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT) {
                return action
            }
        }
        return null
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
                finishBusy(Component.translatable("dlc_manager.pack.apply_success"), true)
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
                updateOperationProgress(operationStatusFor(displayMode, entry), installOffset)
                installArchive(installer, entry, allowRuntimeChanges, displayMode, installOffset, installSpan)
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
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(installOffset, installSpan, progress))
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

    private fun finishBusy(message: Component, promptRestart: Boolean = false) {
        Minecraft.getInstance().execute {
            busy = false
            cancelRequested = false
            operationProgress = -1f
            operationStatus = null
            status = message
            refreshEntries()
            rebuildPackWidgets()
            if (promptRestart) {
                showRestartPrompt()
            }
        }
    }

    private fun showRestartPrompt() {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(
            ConfirmScreen(
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
            releaseIconTextures()
            Minecraft.getInstance().setScreen(parent)
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
            if (!isEntryCurrentlyInstalled(entry) && entry.key == pendingInstallKeys[entry.metadata.identifier]) {
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
            val authors = pack.metadata.authors.ifEmpty { listOf(Component.translatable("dlc_manager.pack.unknown_author").string) }
            val author = DlcTextFormatter.parse(authors.joinToString(", "))
            val icon = loadIconTexture(pack.iconFile)
            PackArchiveEntry(archive.toAbsolutePath().normalize(), pack.metadata, displayName, description, author, authors, icon?.location, icon?.width ?: 0, icon?.height ?: 0)
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
    private val listBottom: Int get() = max(listTop + 96, height - 42)
    private val maxScroll: Int get() = max(0, totalContentHeight() - (listBottom - listTop))

    private fun clampScroll() {
        scrollOffset = max(0, min(maxScroll, scrollOffset))
    }

    private fun ensureSelectionVisible() {
        if (selectedIndex < 0) return
        val right = listRight - SCROLLBAR_WIDTH - 4
        val rowTop = rowTop(selectedIndex, right) ?: return
        if (rowTop < listTop) {
            scrollOffset = rowContentTop(selectedIndex, right)
        } else {
            val rowHeight = rowHeight(archiveEntries[selectedIndex], right)
            if (rowTop + rowHeight > listBottom) {
                scrollOffset = rowContentTop(selectedIndex, right) + rowHeight - (listBottom - listTop)
            }
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
        val barHeight = scrollBarHeight(track)
        val travel = max(1, track - barHeight)
        val ratio = max(0.0, min(1.0, (mouseY - listTop - scrollbarDragOffset) / travel))
        scrollOffset = max(0, min(maxScrollValue, (ratio * maxScrollValue).roundToInt()))
    }

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

    private fun drawScrollBar(guiGraphics: GuiGraphics) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return
        val trackHeight = max(1, listBottom - listTop)
        val barHeight = scrollBarHeight(trackHeight)
        val travel = max(1, trackHeight - barHeight)
        val barY = listTop + (travel * (scrollOffset / maxScrollValue.toDouble())).roundToInt()
        val x = listRight - SCROLLBAR_WIDTH
        guiGraphics.fill(x, listTop, x + SCROLLBAR_WIDTH, listBottom, 0x663A3F48)
        guiGraphics.fill(x, barY, x + SCROLLBAR_WIDTH, barY + barHeight, 0xFF8A95A8.toInt())
    }

    private fun scrollBarHeight(trackHeight: Int): Int {
        val totalHeight = max(trackHeight, totalContentHeight())
        return max(18, trackHeight * trackHeight / max(1, totalHeight)).coerceAtMost(trackHeight)
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

    private fun rowHeight(entry: PackArchiveEntry, right: Int): Int {
        val textX = listLeft + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8
        val textRight = right - BUTTON_WIDTH - 18
        val textWidth = max(1, textRight - textX)
        val homepagePrompt = homepagePrompt(entry)
        val homepageWidth = homepagePrompt?.let { min((font.width(it) * HOMEPAGE_PROMPT_SCALE).roundToInt(), max(1, textWidth / 2)) } ?: 0
        val nameWidth = max(1, textWidth - if (homepagePrompt != null) homepageWidth + HOMEPAGE_PROMPT_GAP else 0)
        val nameLines = max(1, font.split(entry.displayName, nameWidth).size)
        val authorLines = authorLines(entry, textWidth).size
        val descriptionLines = max(1, font.split(entry.description, textWidth).size)
        val contentHeight = ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4 + 10 + 4 + descriptionLines * 10
        val buttonHeight = max(BUTTON_HEIGHT, actionButtons(entry).size * BUTTON_HEIGHT + (actionButtons(entry).size - 1) * BUTTON_GAP)
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

    private fun rowTop(index: Int, right: Int): Int? {
        if (index !in archiveEntries.indices) return null
        val y = listTop - scrollOffset + rowContentTop(index, right)
        if (y >= listBottom || y + rowHeight(archiveEntries[index], right) <= listTop) return null
        return y
    }

    private fun entryAt(mouseY: Double): Int {
        if (mouseY < listTop || mouseY >= listBottom) return -1
        var y = listTop - scrollOffset
        var index = 0
        while (index < archiveEntries.size && y < listBottom) {
            val rowHeight = rowHeight(archiveEntries[index], listRight - SCROLLBAR_WIDTH - 4)
            val rowBottom = y + rowHeight
            if (mouseY >= y && mouseY < rowBottom) return index
            y = rowBottom
            index++
        }
        return -1
    }

    private fun rowContentTop(index: Int, right: Int): Int {
        var y = 0
        for (i in 0 until index) {
            y += rowHeight(archiveEntries[i], right)
        }
        return y
    }

    private fun totalContentHeight(): Int {
        val right = listRight - SCROLLBAR_WIDTH - 4
        var total = 0
        for (entry in archiveEntries) {
            total += rowHeight(entry, right)
        }
        return total
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
        val authorNames: List<String>,
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
        private const val ORDER_BUTTON_WIDTH = 18
        private const val ORDER_BUTTON_SLOTS = 2
        private const val HOMEPAGE_PROMPT_GAP = 12
        private const val HOMEPAGE_PROMPT_SCALE = 0.85f
        private const val SCROLLBAR_WIDTH = 6
        private const val SCROLL_STEP = 24
    }
}
