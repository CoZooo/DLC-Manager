package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcFilePicker
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo
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
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcServerPackManagerScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.pack.manager_title")) {
    private var importButton: Button? = null
    private var urlButton: Button? = null
    private var applyButton: Button? = null
    private var backButton: Button? = null
    private var refreshButton: Button? = null
    private val pendingInstalls = LinkedHashMap<String, PackEntry>()
    private val pendingUninstalls = LinkedHashSet<String>()
    private var archiveGroups: List<PackGroup> = emptyList()
    private var entryOrder: List<String> = emptyList()
    private var currentOrder: List<String> = emptyList()
    private val selectedVersionByIdentifier = LinkedHashMap<String, String>()
    private var scrollOffset = 0
    private var draggingScrollbar = false
    private var scrollbarDragOffset = 0.0
    private var versionDropdownIdentifier: String? = null
    private var status: Component? = null
    private var lastClickKey: String? = null
    private var lastClickAt = 0L
    @Volatile private var loading = false
    @Volatile private var working = false
    private var iconTextureSerial = 0

    override fun init() {
        if (!working) {
            refresh()
        }
        rebuildButtons()
    }

    fun refresh(finalStatus: Component? = null) {
        loading = true
        status = Component.translatable("dlc_manager.server.files_loading")
        Thread({
            val result = try {
                DlcServerConnection.fetchPackList(entry, TIMEOUT_MS)
            } catch (_: Exception) {
                null
            }
            Minecraft.getInstance().execute {
                loading = false
                if (result == null) {
                    releaseIconTextures()
                    archiveGroups = emptyList()
                    entryOrder = emptyList()
                    currentOrder = emptyList()
                    status = Component.translatable("dlc_manager.server.pack.load_failed")
                } else {
                    rebuildEntries(result.packs, result.order)
                    status = finalStatus ?: Component.translatable("dlc_manager.pack.archive_count", archiveGroups.sumOf { it.versions.size })
                }
                rebuildButtons()
            }
        }, "dlc-server-pack-refresh").start()
    }

    private fun rebuildButtons() {
        clearWidgets()
        val buttonWidth = 104
        val buttonHeight = 20
        val gap = 8
        val total = buttonWidth * 4 + gap * 3
        val startX = width / 2 - total / 2
        val y = height - 28
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import")) { importPack() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        urlButton = Button.builder(Component.translatable("dlc_manager.pack.import_url")) {
            Minecraft.getInstance().setScreen(DlcServerPackUrlScreen(this, entry))
        }.bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight).build()
        applyButton = Button.builder(Component.translatable("dlc_manager.pack.apply")) { applyPendingChanges() }
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build()
        backButton = Button.builder(if (hasPendingChanges()) Component.translatable("dlc_manager.pack.cancel_changes") else Component.translatable("dlc_manager.pack.back")) {
            onBackOrCancel()
        }.bounds(startX + (buttonWidth + gap) * 3, y, buttonWidth, buttonHeight).build()
        refreshButton = Button.builder(Component.translatable("dlc_manager.server.files_refresh")) { refresh() }
            .bounds(width - 24 - 70, 8, 70, 20)
            .build()
        addRenderableWidget(importButton!!)
        addRenderableWidget(urlButton!!)
        addRenderableWidget(applyButton!!)
        addRenderableWidget(backButton!!)
        addRenderableWidget(refreshButton!!)
        updateButtonState()
    }

    private fun updateButtonState() {
        val active = !loading && !working
        importButton?.active = active
        urlButton?.active = active
        applyButton?.active = active && hasPendingChanges()
        backButton?.active = !working
        refreshButton?.active = active && !hasPendingChanges()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)
        renderPackList(guiGraphics, mouseX, mouseY)
        val statusText = status
        if (statusText != null) {
            guiGraphics.drawCenteredString(font, statusText, width / 2, height - 52, if (working) 0xFFE6E6 else 0xC8C8C8)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
        renderVersionDropdown(guiGraphics, mouseX, mouseY)
    }

    private fun renderPackList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val left = listLeft
        val right = listRight
        val top = listTop
        val bottom = listBottom
        guiGraphics.fill(left, top - 4, right, bottom + 4, 0x66000000)
        if (archiveGroups.isEmpty() && !loading) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack.none_archived"), width / 2, top + 24, 0xC8C8C8)
            return
        }
        val contentRight = right - SCROLLBAR_WIDTH - 4
        guiGraphics.enableScissor(left, top, right, bottom)
        var y = top - scrollOffset
        for ((index, group) in archiveGroups.withIndex()) {
            val rowHeight = rowHeight(group, contentRight)
            if (y + rowHeight >= top && y <= bottom) {
                renderEntry(guiGraphics, group, index, left + 4, contentRight, y, rowHeight, mouseX, mouseY)
            }
            y += rowHeight
        }
        guiGraphics.disableScissor()
        drawScrollBar(guiGraphics)
    }

    private fun renderEntry(guiGraphics: GuiGraphics, group: PackGroup, index: Int, left: Int, right: Int, y: Int, rowHeight: Int, mouseX: Int, mouseY: Int) {
        val pack = group.displayEntry
        guiGraphics.fill(left, y, right, y + rowHeight - 4, 0x55202020)
        drawOrderButtons(guiGraphics, index, left + 4, y, rowHeight, mouseX, mouseY)
        val iconX = left + ORDER_BUTTON_WIDTH + 12
        val iconY = y + ROW_PADDING_TOP
        val iconTexture = pack.iconTexture
        if (iconTexture != null) {
            guiGraphics.blit(iconTexture.location, iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f, iconTexture.width, iconTexture.height, iconTexture.width, iconTexture.height)
        } else {
            guiGraphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3B3B3B.toInt())
            val initial = pack.displayNameText.ifBlank { "P" }.substring(0, 1).uppercase(Locale.ROOT)
            guiGraphics.drawCenteredString(font, Component.literal(initial), iconX + ICON_SIZE / 2, iconY + 12, 0xFFFFFF)
        }
        val textX = iconX + ICON_SIZE + 8
        val textRight = right - BUTTON_WIDTH - 18
        val homepagePrompt = homepagePrompt(pack)
        val homepageWidth = homepagePrompt?.let { min((font.width(it) * HOMEPAGE_PROMPT_SCALE).roundToInt(), max(1, (textRight - textX) / 2)) } ?: 0
        val nameWidth = max(1, textRight - textX - if (homepagePrompt != null) homepageWidth + HOMEPAGE_PROMPT_GAP else 0)
        var textY = y + ROW_PADDING_TOP
        textY = drawMultiline(guiGraphics, pack.displayName, textX, textY, nameWidth, 0xFFFFFF) + 4
        if (homepagePrompt != null) {
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(HOMEPAGE_PROMPT_SCALE, HOMEPAGE_PROMPT_SCALE, 1.0f)
            val promptX = textRight - homepageWidth
            val promptY = y + ROW_PADDING_TOP + 1
            guiGraphics.drawString(font, homepagePrompt, (promptX / HOMEPAGE_PROMPT_SCALE).roundToInt(), (promptY / HOMEPAGE_PROMPT_SCALE).roundToInt(), 0x8FA8C8, false)
            guiGraphics.pose().popPose()
        }
        textY = drawAuthorLines(guiGraphics, pack, textX, textY, textRight - textX, 0xC8C8C8) + 4
        drawVersionLine(guiGraphics, group, textX, textY, textRight - textX, mouseX, mouseY)
        drawMultiline(guiGraphics, pack.description, textX, textY + VERSION_LINE_HEIGHT, textRight - textX, 0xB0B0B0)
        drawActionButtons(guiGraphics, group, right - BUTTON_WIDTH - 6, y, rowHeight, mouseX, mouseY)
    }

    private fun drawOrderButtons(guiGraphics: GuiGraphics, index: Int, x: Int, rowY: Int, rowHeight: Int, mouseX: Int, mouseY: Int) {
        val startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS)
        if (canMoveUp(index)) {
            drawSizedButton(guiGraphics, x, startY, ORDER_BUTTON_WIDTH, Component.literal("▲"), mouseX, mouseY)
        }
        if (canMoveDown(index)) {
            drawSizedButton(guiGraphics, x, startY + BUTTON_HEIGHT + BUTTON_GAP, ORDER_BUTTON_WIDTH, Component.literal("▼"), mouseX, mouseY)
        }
    }

    private fun drawActionButtons(guiGraphics: GuiGraphics, group: PackGroup, x: Int, rowY: Int, rowHeight: Int, mouseX: Int, mouseY: Int) {
        val actions = actionButtons(group)
        val startY = centeredButtonColumnY(rowY, rowHeight, actions.size)
        for ((index, action) in actions.withIndex()) {
            drawSizedButton(guiGraphics, x, startY + index * (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, actionLabel(group, action), mouseX, mouseY)
        }
    }

    private fun drawVersionLine(guiGraphics: GuiGraphics, group: PackGroup, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) {
        val prefix = Component.translatable("dlc_manager.pack.version_prefix")
        val valueX = x + font.width(prefix) + VERSION_VALUE_GAP
        guiGraphics.drawString(font, prefix, x, y + 4, 0xAFC8FF, false)
        if (group.versions.size <= 1) {
            guiGraphics.drawString(font, Component.literal(group.displayEntry.version), valueX, y + 4, 0xAFC8FF, false)
            return
        }
        drawSizedButton(guiGraphics, valueX - VERSION_BUTTON_HORIZONTAL_PADDING, y, versionButtonWidth(group), versionButtonLabel(group), mouseX, mouseY, VERSION_BUTTON_TEXT_COLOR)
    }

    private fun drawSizedButton(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, label: Component, mouseX: Int, mouseY: Int, textColor: Int = 0xFFFFFF) {
        val hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT
        guiGraphics.fill(x, y, x + width, y + BUTTON_HEIGHT, if (hover) 0xFF5A5A5A.toInt() else 0xFF404040.toInt())
        guiGraphics.drawCenteredString(font, label, x + width / 2, y + 4, textColor)
    }

    private fun drawMultiline(guiGraphics: GuiGraphics, text: Component, x: Int, y: Int, width: Int, color: Int): Int {
        val lines = font.split(text, max(1, width))
        for ((index, line) in lines.withIndex()) {
            guiGraphics.drawString(font, line, x, y + index * 10, color, false)
        }
        return y + lines.size * 10
    }

    private fun drawAuthorLines(guiGraphics: GuiGraphics, entry: PackEntry, x: Int, y: Int, width: Int, color: Int): Int {
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

    private fun renderVersionDropdown(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val group = dropdownGroup() ?: return
        val bounds = versionDropdownBounds(group) ?: return
        guiGraphics.fill(bounds.x - 1, bounds.y - 1, bounds.x + bounds.width + 1, bounds.y + bounds.height + 1, 0xFF202020.toInt())
        guiGraphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xFF303030.toInt())
        for ((index, pack) in group.versions.withIndex()) {
            val itemY = bounds.y + 1 + index * VERSION_DROPDOWN_ITEM_HEIGHT
            val hover = mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= itemY && mouseY < itemY + VERSION_DROPDOWN_ITEM_HEIGHT
            val selected = pack.key == group.selectedVersionKey
            if (hover || selected) {
                guiGraphics.fill(bounds.x + 1, itemY, bounds.x + bounds.width - 1, itemY + VERSION_DROPDOWN_ITEM_HEIGHT, if (hover) 0xFF5A5A5A.toInt() else 0xFF454545.toInt())
            }
            guiGraphics.drawString(font, versionOptionLabel(pack), bounds.x + VERSION_BUTTON_HORIZONTAL_PADDING, itemY + 5, VERSION_BUTTON_TEXT_COLOR, false)
        }
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (loading || working || archiveGroups.isEmpty() || mouseY < listTop || mouseY > listBottom) return false
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return false
        versionDropdownIdentifier = null
        scrollOffset = (scrollOffset + if (deltaY > 0) -SCROLL_STEP else SCROLL_STEP).coerceIn(0, maxScrollValue)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (loading || working) return super.mouseClicked(mouseX, mouseY, button)
        if (handleVersionDropdownClick(mouseX, mouseY)) return true
        if (versionDropdownIdentifier != null) {
            versionDropdownIdentifier = null
            return true
        }
        if (isOnScrollbar(mouseX, mouseY) && maxScroll > 0) {
            draggingScrollbar = true
            scrollbarDragOffset = mouseY - scrollThumbY()
            return true
        }
        val index = entryAt(mouseY)
        if (index >= 0) {
            val group = archiveGroups[index]
            val rowY = rowTop(index, listRight - SCROLLBAR_WIDTH - 4) ?: return true
            val rowHeight = rowHeight(group, listRight - SCROLLBAR_WIDTH - 4)
            val orderAction = orderButtonAt(index, listLeft + 8, rowY, rowHeight, mouseX, mouseY)
            if (orderAction == RowAction.MOVE_UP) return moveGroup(index, -1)
            if (orderAction == RowAction.MOVE_DOWN) return moveGroup(index, 1)
            val versionX = versionButtonX(group)
            val versionY = versionButtonY(index)
            if (versionButtonAt(group, versionX, versionY, mouseX, mouseY)) {
                versionDropdownIdentifier = if (versionDropdownIdentifier == group.identifier) null else group.identifier
                return true
            }
            val action = actionButtonAt(group, listRight - SCROLLBAR_WIDTH - 4 - BUTTON_WIDTH - 6, rowY, rowHeight, mouseX, mouseY)
            when (action) {
                RowAction.INSTALL -> {
                    toggleInstall(group)
                    return true
                }
                RowAction.DELETE -> {
                    confirmDelete(group.displayEntry)
                    return true
                }
                RowAction.FEEDBACK -> {
                    confirmOpenLink(group.displayEntry, "issue")
                    return true
                }
                RowAction.SUPPORT -> {
                    confirmOpenLink(group.displayEntry, "sponsor")
                    return true
                }
                else -> {}
            }
            handleEntryDoubleClick(group)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (!draggingScrollbar) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val trackHeight = listBottom - listTop
        val thumbHeight = scrollThumbHeight()
        val maxScrollValue = maxScroll
        val y = mouseY - scrollbarDragOffset
        scrollOffset = (((y - listTop) * maxScrollValue) / max(1, trackHeight - thumbHeight)).roundToInt().coerceIn(0, maxScrollValue)
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun importPack() {
        val zip: Path = DlcFilePicker.pickAnyFile(Component.translatable("dlc_manager.pack.import").string) ?: return
        startWork(Component.translatable("dlc_manager.pack.importing"), Component.translatable("dlc_manager.pack.import_staged", zip.fileName.toString())) {
            try {
                val ok = DlcServerConnection.importPackPackage(entry, TIMEOUT_MS, zip)
                if (!ok) Component.translatable("dlc_manager.pack.import_failed", "upload failed") else null
            } catch (error: Exception) {
                Component.translatable("dlc_manager.pack.import_failed", error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun applyPendingChanges() {
        if (!hasPendingChanges()) return
        if (pendingInstalls.isEmpty() && pendingUninstalls.isEmpty()) {
            startWork(Component.translatable("dlc_manager.pack.applying"), Component.translatable("dlc_manager.pack.order_applied")) {
                val orderResponse = try {
                    DlcServerConnection.savePackOrder(entry, TIMEOUT_MS, currentOrder)
                } catch (error: Exception) {
                    return@startWork Component.translatable("dlc_manager.pack.apply_failed", error.message ?: error.javaClass.simpleName)
                }
                if (orderResponse == null || !orderResponse.ok) {
                    return@startWork Component.translatable("dlc_manager.pack.apply_failed", orderResponse?.message ?: "save order failed")
                }
                entryOrder = currentOrder.toList()
                null
            }
            return
        }
        working = true
        updateButtonState()
        Minecraft.getInstance().setScreen(
            DlcServerPackApplyScreen(
                this,
                entry,
                currentOrder.toList(),
                pendingUninstalls.toList(),
                pendingInstalls.values.map { it.archivePath },
            ),
        )
    }

    fun finishServerPackApply(success: Boolean, message: String?) {
        working = false
        if (success) {
            pendingInstalls.clear()
            pendingUninstalls.clear()
            refresh(Component.translatable("dlc_manager.pack.apply_success"))
        } else {
            status = Component.translatable("dlc_manager.pack.apply_failed", message ?: "failed")
            rebuildButtons()
        }
    }

    fun showServerRestartPrompt() {
        Minecraft.getInstance().setScreen(ConfirmScreen({ restart ->
            if (restart) {
                Thread({
                    try {
                        DlcServerConnection.restartServer(entry, TIMEOUT_MS)
                    } catch (_: Exception) {
                    }
                }, "dlc-server-pack-restart").start()
            }
            Minecraft.getInstance().setScreen(this)
        }, Component.translatable("dlc_manager.server.restart_title"), Component.translatable("dlc_manager.server.restart_prompt"), Component.translatable("dlc_manager.restart_now"), Component.translatable("dlc_manager.restart_later")))
    }

    private fun startWork(workingText: Component, successText: Component = Component.translatable("dlc_manager.pack.apply_success"), clearPendingOnSuccess: Boolean = false, action: () -> Component?) {
        if (working) return
        working = true
        status = workingText
        updateButtonState()
        Thread({
            val error = action()
            Minecraft.getInstance().execute {
                working = false
                if (error == null) {
                    if (clearPendingOnSuccess) {
                        pendingInstalls.clear()
                        pendingUninstalls.clear()
                    }
                    refresh(successText)
                } else {
                    status = error
                    rebuildButtons()
                }
            }
        }, "dlc-server-pack-work").start()
    }

    private fun toggleInstall(group: PackGroup) {
        val pack = group.displayEntry
        if (pendingInstalls[pack.identifier] === pack) {
            pendingInstalls.remove(pack.identifier)
            rebuildButtons()
            return
        }
        if (pendingUninstalls.remove(pack.identifier)) {
            rebuildButtons()
            return
        }
        if (isEntryCurrentlyInstalled(pack)) {
            pendingUninstalls.add(pack.identifier)
        } else {
            pendingInstalls[pack.identifier] = pack
            pendingUninstalls.remove(pack.identifier)
        }
        rebuildButtons()
    }

    private fun confirmDelete(pack: PackEntry) {
        val prompt = if (isIdentifierInstalled(pack.identifier)) {
            Component.translatable("dlc_manager.pack.delete_confirm_uninstall", pack.displayNameText)
        } else {
            Component.translatable("dlc_manager.pack.delete_confirm", pack.displayNameText)
        }
        Minecraft.getInstance().setScreen(ConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) {
                val successText = if (isIdentifierInstalled(pack.identifier)) {
                    Component.translatable("dlc_manager.pack.delete_uninstalled_success", pack.displayNameText)
                } else {
                    Component.translatable("dlc_manager.pack.delete_success", pack.displayNameText)
                }
                startWork(Component.translatable("dlc_manager.pack.delete"), successText) {
                    val response = try {
                        DlcServerConnection.deletePack(entry, TIMEOUT_MS, pack.archivePath)
                    } catch (error: Exception) {
                        return@startWork Component.translatable("dlc_manager.pack.delete_failed", error.message ?: error.javaClass.simpleName)
                    }
                    if (response == null || !response.ok) Component.translatable("dlc_manager.pack.delete_failed", response?.message ?: pack.displayNameText) else null
                }
            }
        }, Component.translatable("dlc_manager.pack.delete_confirm_title"), prompt, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
    }

    private fun confirmOpenLink(pack: PackEntry, key: String) {
        val url = pack.links[key]
        if (url.isNullOrBlank()) return
        Minecraft.getInstance().setScreen(ConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) {
                Util.getPlatform().openUri(url)
            }
        }, Component.translatable("dlc_manager.pack.open_link_title"), Component.translatable("dlc_manager.pack.open_link_prompt", url), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
    }

    private fun handleEntryDoubleClick(group: PackGroup) {
        val pack = group.displayEntry
        if (pack.links["homepage"].isNullOrBlank()) return
        val now = Util.getMillis()
        if (lastClickKey == pack.key && now - lastClickAt <= 400L) {
            confirmOpenLink(pack, "homepage")
            lastClickKey = null
            lastClickAt = 0L
        } else {
            lastClickKey = pack.key
            lastClickAt = now
        }
    }

    private fun moveGroup(index: Int, delta: Int): Boolean {
        val target = index + delta
        if (target !in archiveGroups.indices) return true
        val groups = archiveGroups.toMutableList()
        val moved = groups.removeAt(index)
        groups.add(target, moved)
        archiveGroups = groups
        currentOrder = groups.map { it.orderKey }
        saveOrderLive(currentOrder)
        clampScroll()
        rebuildButtons()
        return true
    }

    private fun saveOrderLive(order: List<String>) {
        Thread({
            try {
                DlcServerConnection.savePackOrder(entry, TIMEOUT_MS, order)
            } catch (_: Exception) {
            }
        }, "dlc-server-pack-order").start()
    }

    private fun onBackOrCancel() {
        if (hasPendingChanges()) {
            pendingInstalls.clear()
            pendingUninstalls.clear()
            currentOrder = entryOrder.toList()
            saveOrderLive(entryOrder)
            sortGroupsByCurrentOrder()
            status = Component.translatable("dlc_manager.pack.changes_cancelled")
            rebuildButtons()
            return
        }
        returnToParent()
    }

    private fun returnToParent() {
        releaseIconTextures()
        Minecraft.getInstance().setScreen(parent)
    }

    private fun rebuildEntries(packs: List<DlcServerPackInfo>, order: List<String>) {
        val pendingInstallKeys = pendingInstalls.mapValues { it.value.key }
        releaseIconTextures()
        val entries = packs.map { toEntry(it) }.filter { it.identifier.isNotBlank() && it.version.isNotBlank() && it.archivePath.isNotBlank() }
        val groups = groupEntries(entries)
        entryOrder = normalizeGroupOrder(order, groups)
        currentOrder = entryOrder.toList()
        archiveGroups = groups
        normalizeSelections()
        sortGroupsByCurrentOrder()
        pendingInstalls.clear()
        for (group in archiveGroups) {
            val pack = group.displayEntry
            if (!isEntryCurrentlyInstalled(pack) && pendingInstallKeys[pack.identifier] == pack.key) {
                pendingInstalls[pack.identifier] = pack
            }
        }
        pendingInstalls.keys.removeIf { identifier -> archiveGroups.none { it.identifier == identifier } }
        pendingUninstalls.removeIf { identifier -> archiveGroups.none { it.identifier == identifier } }
        clampScroll()
    }

    private fun toEntry(info: DlcServerPackInfo): PackEntry {
        val identifier = info.identifier.orEmpty()
        val version = info.version.orEmpty()
        val archivePath = info.archivePath.orEmpty()
        val orderKey = info.orderKey?.takeIf { it.isNotBlank() } ?: "$archivePath#$identifier@$version"
        val displayName = DlcTextFormatter.parse(info.displayName?.takeIf { it.isNotBlank() } ?: identifier)
        val description = DlcMarkdownRenderer.renderMarkdownComponent(info.description)
        val authors = info.authors?.filter { it.isNotBlank() }?.ifEmpty { null } ?: listOf(Component.translatable("dlc_manager.pack.unknown_author").string)
        return PackEntry(
            archivePath,
            orderKey,
            identifier,
            version,
            displayName,
            description,
            authors,
            info.links ?: emptyMap(),
            info.installed,
            info.installedVersion.orEmpty(),
            loadIconTexture(info.iconBase64),
        )
    }

    private fun groupEntries(entries: List<PackEntry>): List<PackGroup> {
        val byIdentifier = LinkedHashMap<String, MutableList<PackEntry>>()
        for (entry in entries) {
            byIdentifier.computeIfAbsent(entry.identifier) { ArrayList() }.add(entry)
        }
        val groups = ArrayList<PackGroup>()
        for ((identifier, versions) in byIdentifier) {
            versions.sortWith { a, b ->
                val compare = DlcPackMetadata.compareVersionStrings(b.version, a.version)
                if (compare != 0) compare else a.archivePath.compareTo(b.archivePath, ignoreCase = true)
            }
            val installed = versions.firstOrNull { it.installed }
            val orderEntry = installed ?: versions.first()
            val selected = selectedVersionByIdentifier[identifier]
            val display = versions.firstOrNull { it.key == selected || it.version == selected } ?: orderEntry
            groups.add(PackGroup(identifier, versions, orderEntry.key, display.key))
        }
        return groups
    }

    private fun normalizeSelections() {
        val valid = archiveGroups.mapTo(HashSet()) { it.identifier }
        selectedVersionByIdentifier.keys.removeIf { !valid.contains(it) }
        archiveGroups = archiveGroups.map { group ->
            val selected = selectedVersionByIdentifier[group.identifier]
            val display = group.versions.firstOrNull { it.key == selected || it.version == selected } ?: group.displayEntry
            selectedVersionByIdentifier[group.identifier] = display.key
            group.copy(selectedVersionKey = display.key)
        }
    }

    private fun normalizeGroupOrder(savedOrder: Collection<String>?, groups: List<PackGroup>): List<String> {
        val out = ArrayList<String>()
        savedOrder?.forEach { key ->
            val identifier = identifierFromOrderKey(key)
            val group = groups.firstOrNull { it.identifier == identifier } ?: return@forEach
            if (!out.contains(group.orderKey)) out.add(group.orderKey)
        }
        for (group in groups) {
            if (!out.contains(group.orderKey)) out.add(group.orderKey)
        }
        return out
    }

    private fun sortGroupsByCurrentOrder() {
        archiveGroups = archiveGroups.sortedBy { currentOrder.indexOf(it.orderKey).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    }

    private fun identifierFromOrderKey(key: String?): String? {
        if (key.isNullOrBlank()) return null
        val hash = key.lastIndexOf('#')
        val at = key.lastIndexOf('@')
        if (hash < 0 || at <= hash + 1) return null
        return key.substring(hash + 1, at)
    }

    private fun loadIconTexture(base64: String?): IconTexture? {
        if (base64.isNullOrBlank()) return null
        var image: NativeImage? = null
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            ByteArrayInputStream(bytes).use { input ->
                image = NativeImage.read(input)
            }
            val loaded = image ?: return null
            val imageWidth = loaded.width
            val imageHeight = loaded.height
            val texture = DynamicTexture(loaded)
            image = null
            iconTextureSerial += 1
            val location = Minecraft.getInstance().textureManager.register("dlc_server_pack_icon/$iconTextureSerial", texture)
            IconTexture(location, imageWidth, imageHeight)
        } catch (_: Exception) {
            image?.close()
            null
        }
    }

    private fun releaseIconTextures() {
        for (group in archiveGroups) {
            for (pack in group.versions) {
                pack.iconTexture?.let { Minecraft.getInstance().textureManager.release(it.location) }
            }
        }
    }

    private fun hasPendingChanges(): Boolean {
        return pendingInstalls.isNotEmpty() || pendingUninstalls.isNotEmpty() || currentOrder != entryOrder
    }

    private fun isEntryCurrentlyInstalled(entry: PackEntry): Boolean = entry.installed && entry.installedVersion == entry.version
    private fun isIdentifierInstalled(identifier: String): Boolean = archiveGroups.any { it.identifier == identifier && it.versions.any(PackEntry::installed) }

    private fun installButtonLabel(group: PackGroup): Component {
        val pack = group.displayEntry
        val installed = group.versions.firstOrNull { it.installed }
        val compare = installed?.let { DlcPackMetadata.compareVersionStrings(pack.version, it.installedVersion.ifBlank { it.version }) } ?: 0
        if (pendingInstalls[pack.identifier] === pack) {
            return when {
                installed == null -> Component.translatable("dlc_manager.pack.pending_install")
                compare > 0 -> Component.translatable("dlc_manager.pack.pending_upgrade")
                compare < 0 -> Component.translatable("dlc_manager.pack.pending_downgrade")
                else -> Component.translatable("dlc_manager.pack.pending_install")
            }
        }
        if (pendingUninstalls.contains(pack.identifier)) return Component.translatable("dlc_manager.pack.pending_uninstall")
        if (installed != null && compare == 0) return Component.translatable("dlc_manager.pack.uninstall")
        if (installed != null) return if (compare > 0) Component.translatable("dlc_manager.pack.upgrade") else Component.translatable("dlc_manager.pack.downgrade")
        return Component.translatable("dlc_manager.pack.install")
    }

    private fun actionButtons(group: PackGroup): List<RowAction> {
        val pack = group.displayEntry
        val out = ArrayList<RowAction>()
        out.add(RowAction.INSTALL)
        out.add(RowAction.DELETE)
        if (!pack.links["issue"].isNullOrBlank()) out.add(RowAction.FEEDBACK)
        if (!pack.links["sponsor"].isNullOrBlank()) out.add(RowAction.SUPPORT)
        return out
    }

    private fun actionLabel(group: PackGroup, action: RowAction): Component {
        return when (action) {
            RowAction.INSTALL -> installButtonLabel(group)
            RowAction.DELETE -> Component.translatable("dlc_manager.pack.delete")
            RowAction.FEEDBACK -> Component.translatable("dlc_manager.pack.feedback")
            RowAction.SUPPORT -> Component.translatable("dlc_manager.pack.support_author")
            RowAction.MOVE_UP -> Component.literal("▲")
            RowAction.MOVE_DOWN -> Component.literal("▼")
        }
    }

    private fun authorLines(entry: PackEntry, width: Int): List<List<Component>> {
        val prefix = Component.translatable("dlc_manager.pack.author_prefix")
        val separator = Component.literal(", ")
        val lines = ArrayList<ArrayList<Component>>()
        var current = ArrayList<Component>()
        var currentWidth = 0
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
            if (index > 0) addPart(separator, true)
            addPart(DlcTextFormatter.parse(author), true)
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.ifEmpty { listOf(listOf(prefix)) }
    }

    private fun rowHeight(group: PackGroup, right: Int): Int {
        val pack = group.displayEntry
        val textX = listLeft + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8
        val textRight = right - BUTTON_WIDTH - 18
        val textWidth = max(1, textRight - textX)
        val homepagePrompt = homepagePrompt(pack)
        val homepageWidth = homepagePrompt?.let { min((font.width(it) * HOMEPAGE_PROMPT_SCALE).roundToInt(), max(1, textWidth / 2)) } ?: 0
        val nameWidth = max(1, textWidth - if (homepagePrompt != null) homepageWidth + HOMEPAGE_PROMPT_GAP else 0)
        val nameLines = max(1, font.split(pack.displayName, nameWidth).size)
        val authorLines = authorLines(pack, textWidth).size
        val descriptionLines = max(1, font.split(pack.description, textWidth).size)
        val contentHeight = ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4 + VERSION_LINE_HEIGHT + 4 + descriptionLines * 10
        val actionCount = actionButtons(group).size
        val buttonHeight = max(BUTTON_HEIGHT, actionCount * BUTTON_HEIGHT + (actionCount - 1) * BUTTON_GAP)
        return max(ROW_HEIGHT, max(contentHeight, max(buttonHeight, ROW_PADDING_TOP + ICON_SIZE)) + ROW_PADDING_BOTTOM)
    }

    private fun homepagePrompt(entry: PackEntry): Component? = if (entry.links["homepage"].isNullOrBlank()) null else Component.translatable("dlc_manager.pack.double_click_homepage")

    private fun centeredButtonColumnY(rowY: Int, rowHeight: Int, count: Int): Int {
        if (count <= 0) return rowY + (rowHeight - 4) / 2
        val totalHeight = count * BUTTON_HEIGHT + (count - 1) * BUTTON_GAP
        return rowY + max(0, (rowHeight - 4 - totalHeight) / 2)
    }

    private fun rowTop(index: Int, right: Int): Int? {
        if (index !in archiveGroups.indices) return null
        val y = listTop - scrollOffset + rowContentTop(index, right)
        if (y >= listBottom || y + rowHeight(archiveGroups[index], right) <= listTop) return null
        return y
    }

    private fun rowContentTop(index: Int, right: Int): Int {
        var y = 0
        for (i in 0 until index) y += rowHeight(archiveGroups[i], right)
        return y
    }

    private fun totalContentHeight(): Int {
        val right = listRight - SCROLLBAR_WIDTH - 4
        var total = 0
        for (group in archiveGroups) total += rowHeight(group, right)
        return total
    }

    private fun entryAt(mouseY: Double): Int {
        if (mouseY < listTop || mouseY >= listBottom) return -1
        var y = listTop - scrollOffset
        val right = listRight - SCROLLBAR_WIDTH - 4
        for (index in archiveGroups.indices) {
            val rowHeight = rowHeight(archiveGroups[index], right)
            if (mouseY >= y && mouseY < y + rowHeight) return index
            y += rowHeight
        }
        return -1
    }

    private fun orderButtonAt(index: Int, x: Int, rowY: Int, rowHeight: Int, mouseX: Double, mouseY: Double): RowAction? {
        val startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS)
        if (canMoveUp(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= startY && mouseY < startY + BUTTON_HEIGHT) return RowAction.MOVE_UP
        val downY = startY + BUTTON_HEIGHT + BUTTON_GAP
        if (canMoveDown(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= downY && mouseY < downY + BUTTON_HEIGHT) return RowAction.MOVE_DOWN
        return null
    }

    private fun actionButtonAt(group: PackGroup, x: Int, rowY: Int, rowHeight: Int, mouseX: Double, mouseY: Double): RowAction? {
        val buttons = actionButtons(group)
        val startY = centeredButtonColumnY(rowY, rowHeight, buttons.size)
        for ((buttonIndex, action) in buttons.withIndex()) {
            val y = startY + buttonIndex * (BUTTON_HEIGHT + BUTTON_GAP)
            if (mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT) return action
        }
        return null
    }

    private fun canMoveUp(index: Int) = index > 0
    private fun canMoveDown(index: Int) = index >= 0 && index < archiveGroups.size - 1

    private fun versionButtonAt(group: PackGroup, x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        if (group.versions.size <= 1) return false
        val width = versionButtonWidth(group)
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT
    }

    private fun versionButtonX(group: PackGroup): Int {
        val x = listLeft + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8
        return x + font.width(Component.translatable("dlc_manager.pack.version_prefix")) + VERSION_VALUE_GAP - VERSION_BUTTON_HORIZONTAL_PADDING
    }

    private fun versionButtonY(index: Int): Int {
        val rowY = rowTop(index, listRight - SCROLLBAR_WIDTH - 4) ?: return -1000
        val group = archiveGroups[index]
        val textX = listLeft + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8
        val textRight = listRight - SCROLLBAR_WIDTH - 4 - BUTTON_WIDTH - 18
        val textWidth = max(1, textRight - textX)
        val homepagePrompt = homepagePrompt(group.displayEntry)
        val homepageWidth = homepagePrompt?.let { min((font.width(it) * HOMEPAGE_PROMPT_SCALE).roundToInt(), max(1, textWidth / 2)) } ?: 0
        val nameWidth = max(1, textWidth - if (homepagePrompt != null) homepageWidth + HOMEPAGE_PROMPT_GAP else 0)
        val nameLines = max(1, font.split(group.displayEntry.displayName, nameWidth).size)
        val authorLines = authorLines(group.displayEntry, textWidth).size
        return rowY + ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4
    }

    private fun versionButtonWidth(group: PackGroup): Int {
        return max(32, font.width(versionButtonLabel(group)) + VERSION_BUTTON_HORIZONTAL_PADDING * 2)
    }

    private fun versionButtonLabel(group: PackGroup): Component = versionOptionLabel(group.displayEntry)

    private fun versionOptionLabel(entry: PackEntry): Component {
        return if (entry.installed) Component.translatable("dlc_manager.pack.version_installed", entry.version) else Component.literal(entry.version)
    }

    private fun dropdownGroup(): PackGroup? {
        val identifier = versionDropdownIdentifier ?: return null
        return archiveGroups.firstOrNull { it.identifier == identifier && it.versions.size > 1 }
    }

    private fun versionDropdownBounds(group: PackGroup): Bounds? {
        val index = archiveGroups.indexOfFirst { it.identifier == group.identifier }
        if (index < 0) return null
        val x = versionButtonX(group)
        val buttonY = versionButtonY(index)
        val width = max(versionButtonWidth(group), group.versions.maxOf { font.width(versionOptionLabel(it)) + VERSION_BUTTON_HORIZONTAL_PADDING * 2 })
        val height = group.versions.size * VERSION_DROPDOWN_ITEM_HEIGHT + 2
        val downY = buttonY + BUTTON_HEIGHT + 2
        val upY = buttonY - height - 2
        val y = when {
            downY + height <= listBottom -> downY
            upY >= listTop -> upY
            else -> return null
        }
        return Bounds(x, y, width, height)
    }

    private fun handleVersionDropdownClick(mouseX: Double, mouseY: Double): Boolean {
        val group = dropdownGroup() ?: return false
        val bounds = versionDropdownBounds(group) ?: return false
        if (mouseX < bounds.x || mouseX >= bounds.x + bounds.width || mouseY < bounds.y || mouseY >= bounds.y + bounds.height) return false
        val index = ((mouseY.toInt() - bounds.y - 1) / VERSION_DROPDOWN_ITEM_HEIGHT).coerceIn(0, group.versions.size - 1)
        val selected = group.versions[index]
        selectedVersionByIdentifier[group.identifier] = selected.key
        archiveGroups = archiveGroups.map { if (it.identifier == group.identifier) it.copy(selectedVersionKey = selected.key) else it }
        if (pendingInstalls[group.identifier] != null && isEntryCurrentlyInstalled(selected)) {
            pendingInstalls.remove(group.identifier)
        }
        versionDropdownIdentifier = null
        rebuildButtons()
        return true
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return
        val x = listRight - SCROLLBAR_WIDTH
        val thumbHeight = scrollThumbHeight()
        val thumbY = scrollThumbY()
        guiGraphics.fill(x, listTop, x + 4, listBottom, 0x66000000)
        guiGraphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, 0xFF555555.toInt())
    }

    private fun scrollThumbHeight(): Int {
        val contentHeight = totalContentHeight()
        val viewport = listBottom - listTop
        return max(18, viewport * viewport / max(1, contentHeight))
    }

    private fun scrollThumbY(): Int {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return listTop
        val trackHeight = listBottom - listTop
        val thumbHeight = scrollThumbHeight()
        return listTop + (trackHeight - thumbHeight) * scrollOffset / maxScrollValue
    }

    private fun isOnScrollbar(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= listRight - SCROLLBAR_WIDTH - 4 && mouseX <= listRight + 4 && mouseY >= listTop && mouseY <= listBottom
    }

    private fun clampScroll() {
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
    }

    private val maxScroll: Int
        get() = max(0, totalContentHeight() - (listBottom - listTop))
    private val listLeft: Int get() = 24
    private val listRight: Int get() = width - 24
    private val listTop: Int get() = 34
    private val listBottom: Int get() = height - 64

    override fun shouldCloseOnEsc(): Boolean = !working

    override fun onClose() {
        if (!working) onBackOrCancel()
    }

    private data class IconTexture(val location: ResourceLocation, val width: Int, val height: Int)

    private data class PackEntry(
        val archivePath: String,
        val key: String,
        val identifier: String,
        val version: String,
        val displayName: Component,
        val description: Component,
        val authorNames: List<String>,
        val links: Map<String, String>,
        val installed: Boolean,
        val installedVersion: String,
        val iconTexture: IconTexture?,
    ) {
        val displayNameText: String = displayName.string.ifBlank { identifier }
    }

    private data class PackGroup(
        val identifier: String,
        val versions: List<PackEntry>,
        val orderKey: String,
        val selectedVersionKey: String,
    ) {
        val displayEntry: PackEntry
            get() = versions.firstOrNull { it.key == selectedVersionKey } ?: versions.first()
    }

    private data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private enum class RowAction {
        INSTALL,
        DELETE,
        FEEDBACK,
        SUPPORT,
        MOVE_UP,
        MOVE_DOWN,
    }

    companion object {
        private const val TIMEOUT_MS = 8000
        private const val ROW_HEIGHT = 80
        private const val ROW_PADDING_TOP = 6
        private const val ROW_PADDING_BOTTOM = 8
        private const val ICON_SIZE = 32
        private const val BUTTON_WIDTH = 58
        private const val BUTTON_HEIGHT = 16
        private const val BUTTON_GAP = 4
        private const val VERSION_LINE_HEIGHT = 18
        private const val VERSION_VALUE_GAP = 6
        private const val VERSION_BUTTON_HORIZONTAL_PADDING = 6
        private const val VERSION_BUTTON_TEXT_COLOR = 0xAFC8FF
        private const val VERSION_DROPDOWN_ITEM_HEIGHT = 18
        private const val ORDER_BUTTON_WIDTH = 18
        private const val ORDER_BUTTON_SLOTS = 2
        private const val HOMEPAGE_PROMPT_GAP = 12
        private const val HOMEPAGE_PROMPT_SCALE = 0.85f
        private const val SCROLLBAR_WIDTH = 6
        private const val SCROLL_STEP = 24
    }
}
