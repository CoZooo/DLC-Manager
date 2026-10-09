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
) : DlcPackPageScreen(DlcUiPage.SERVER_PACKS, parent, Component.translatable("dlc_manager.server.pack.manager_title")) {
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

    private fun initControls() {
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

    private fun drawSizedButton(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, label: Component, mouseX: Int, mouseY: Int, textColor: Int = 0xFFFFFF) {
        val hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT
        guiGraphics.fill(x, y, x + width, y + BUTTON_HEIGHT, if (hover) 0xFF5A5A5A.toInt() else 0xFF404040.toInt())
        guiGraphics.drawCenteredString(font, label, x + width / 2, y + 4, textColor)
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
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ restart ->
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
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
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
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) {
                Util.getPlatform().openUri(url)
            }
        }, Component.translatable("dlc_manager.pack.open_link_title"), Component.translatable("dlc_manager.pack.open_link_prompt", url), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
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

    private fun totalContentHeight(): Int {
        val right = listRight - SCROLLBAR_WIDTH - 4
        var total = 0
        for (group in archiveGroups) total += rowHeight(group, right)
        return total
    }

    private fun canMoveUp(index: Int) = index > 0
    private fun canMoveDown(index: Int) = index >= 0 && index < archiveGroups.size - 1

    private fun versionButtonLabel(group: PackGroup): Component = versionOptionLabel(group.displayEntry)

    private fun versionOptionLabel(entry: PackEntry): Component {
        return if (entry.installed) Component.translatable("dlc_manager.pack.version_installed", entry.version) else Component.literal(entry.version)
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




        private const val ORDER_BUTTON_WIDTH = 18

        private const val HOMEPAGE_PROMPT_GAP = 12
        private const val HOMEPAGE_PROMPT_SCALE = 0.85f
        private const val SCROLLBAR_WIDTH = 6

    }
    private val packAdapter = object : PackAdapter<PackGroup> {
        override fun details(group: PackGroup): PackDetails {
            val pack = group.displayEntry
            return PackDetails(pack.displayName, pack.description, pack.authorNames, pack.version, pack.iconTexture?.location?.toString(), group.selectedVersionKey)
        }
        override fun versions(group: PackGroup): List<PackVersion> = group.versions.map { PackVersion(it.key, versionOptionLabel(it).string) }
        override fun actions(group: PackGroup): List<PackAction> {
            val pack = group.displayEntry
            val choices = actionButtons(group).mapTo(ArrayList<PackAction>()) { choice ->
                PackAction(actionLabel(group, choice).string, choice == RowAction.DELETE) {
                    when (choice) {
                        RowAction.INSTALL -> toggleInstall(group)
                        RowAction.DELETE -> confirmDelete(pack)
                        RowAction.FEEDBACK -> confirmOpenLink(pack, "issue")
                        RowAction.SUPPORT -> confirmOpenLink(pack, "sponsor")
                        else -> {}
                    }
                }
            }
            if (!pack.links["homepage"].isNullOrBlank()) choices.add(PackAction(tr("dlc_manager.pack.homepage"), false) { confirmOpenLink(pack, "homepage") })
            return choices
        }
        override fun selectVersion(group: PackGroup, key: String) { selectServerVersion(group, key) }
        override fun move(index: Int, direction: Int) { moveGroup(index, direction) }
    }
    override fun canEditPacks(): Boolean = !loading && !working

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        pageRevision = archiveGroups.hashCode() + pendingInstalls.hashCode() + pendingUninstalls.hashCode() + loading.hashCode() + working.hashCode() + applyButton.hashCode()

        if (!controlsReady) { controlsReady = true; initControls() }
        val list = section(null, null)
        live(content, { status?.string }, "page-description")

        if (archiveGroups.isEmpty() && !loading) text(list, tr("dlc_manager.ui.empty_packs"), "page-description")
        renderPackRows(list, archiveGroups, packAdapter)
        renderPackFooter(importButton, urlButton, backButton, applyButton, hasPendingChanges())
        refreshButton?.let { mount(toolbar, it) }
    }
    override fun updatePage() { updateButtonState(); if (pageRevision != archiveGroups.hashCode() + pendingInstalls.hashCode() + pendingUninstalls.hashCode() + loading.hashCode() + working.hashCode() + applyButton.hashCode()) refreshPage() }
    private fun selectServerVersion(group: PackGroup, key: String) { val selected = group.versions.firstOrNull { it.key == key } ?: return; selectedVersionByIdentifier[group.identifier] = key; archiveGroups = archiveGroups.map { if (it.identifier == group.identifier) it.copy(selectedVersionKey = key) else it }; if (pendingInstalls.containsKey(group.identifier) && isEntryCurrentlyInstalled(selected)) pendingInstalls.remove(group.identifier); rebuildButtons(); refreshPage() }

}
