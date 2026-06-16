package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.required.DlcRequiredNotice
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredProgressItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcServerRequiredManagerScreen(
    private val parent: Screen?,
    private val serverEntry: DlcServerEntry?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.required.manage_title")) {
    private class InstallState {
        @Volatile var running = false
        @Volatile var progress = 0f
    }

    private val allItems: MutableList<DlcServerRequiredItem> = ArrayList()
    private val visibleItems: MutableList<DlcServerRequiredItem> = ArrayList()
    private val index: MutableMap<String, DlcServerRequiredItem> = HashMap()
    private var dependents: Map<String, Set<String>> = HashMap()
    private val entryButtons: MutableMap<String, Button> = HashMap()
    private val progressById: MutableMap<String, Float> = ConcurrentHashMap()

    private var applyAllButton: Button? = null
    private var backButton: Button? = null
    private val batchState = InstallState()
    private var noticeLines: List<String> = emptyList()

    @Volatile
    private var loading = false
    @Volatile
    private var downloading = false
    @Volatile
    private var status: DlcServerRequiredStatus? = null
    @Volatile
    private var errorText: String? = null
    @Volatile
    private var lastError: String? = null
    private var scrollIndex = 0
    private var activeIds: Set<String> = HashSet()

    override fun init() {
        noticeLines = splitLines(DlcRequiredNotice.load().resolveManageDesc())
        rebuildAllWidgets()
        loadRequiredList()
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        init()
    }

    private fun rebuildAllWidgets() {
        clearWidgets()
        entryButtons.clear()
        val buttonWidth = 140
        val buttonHeight = 20
        val y = height - 40
        val applyAll = Button.builder(Component.translatable("dlc_manager.required.apply_all")) { applyAll() }
            .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("dlc_manager.back")) { handleBack() }
            .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
            .build()
        applyAllButton = applyAll
        backButton = back
        addRenderableWidget(applyAll)
        addRenderableWidget(back)
        buildEntryButtons()
    }

    private fun buildEntryButtons() {
        val buttonWidth = 72
        val buttonHeight = 16
        val x = width - 20 - buttonWidth
        for (item in visibleItems) {
            val id = item.id
            if (id.isNullOrBlank()) {
                continue
            }
            val label = getEntryButtonLabel(item)
            val button = Button.builder(Component.literal(label)) { onEntryAction(item) }
                .bounds(x, 0, buttonWidth, buttonHeight)
                .build()
            entryButtons[id] = button
            addRenderableWidget(button)
        }
        updateEntryButtons()
    }

    private fun loadRequiredList() {
        loading = true
        CompletableFuture
            .supplyAsync { fetchRequiredList() }
            .whenComplete { list, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (list == null) {
                        errorText = if (!lastError.isNullOrBlank()) {
                            lastError
                        } else {
                            Component.translatable("dlc_manager.required.error", "").string
                        }
                        return@execute
                    }
                    errorText = null
                    setItems(list)
                }
            }
    }

    private fun setItems(list: List<DlcServerRequiredItem>) {
        allItems.clear()
        allItems.addAll(list)
        index.clear()
        visibleItems.clear()
        for (item in allItems) {
            val id = item.id
            if (id.isNullOrBlank()) {
                continue
            }
            index[id] = item
            if (item.inList && !item.hidden) {
                visibleItems.add(item)
            }
        }
        dependents = buildDependents(allItems)
        rebuildAllWidgets()
    }

    private fun fetchRequiredList(): List<DlcServerRequiredItem>? {
        return try {
            lastError = null
            DlcServerConnection.fetchRequiredList(serverEntry, 8000)
        } catch (ex: Exception) {
            lastError = ex.message
            null
        }
    }

    private fun onEntryAction(item: DlcServerRequiredItem?) {
        if (item == null || downloading) {
            return
        }
        if (isApplied(item)) {
            uninstallEntry(item)
            return
        }
        val id = item.id ?: return
        startDownload(listOf(id), false)
    }

    private fun applyAll() {
        if (downloading) {
            return
        }
        val ids: MutableList<String> = ArrayList()
        for (item in visibleItems) {
            val id = item.id
            if (!id.isNullOrBlank()) {
                ids.add(id)
            }
        }
        startDownload(ids, true)
    }

    private fun startDownload(ids: List<String>, batch: Boolean) {
        if (downloading) {
            return
        }
        downloading = true
        activeIds = HashSet(ids)
        batchState.running = batch
        batchState.progress = 0f
        updateEntryButtons()
        updateApplyAllButton(batch, true)
        CompletableFuture
            .supplyAsync { startDownloadRequest(ids) }
            .whenComplete { started, _ ->
                if (started != null) {
                    Minecraft.getInstance().execute {
                        updateStatus(started)
                        if (started.finished) {
                            finishDownload(started)
                        } else {
                            startPolling()
                        }
                    }
                } else {
                    Minecraft.getInstance().execute {
                        downloading = false
                        errorText = if (!lastError.isNullOrBlank()) {
                            lastError
                        } else {
                            Component.translatable("dlc_manager.required.error", "").string
                        }
                        updateEntryButtons()
                        updateApplyAllButton(false, false)
                    }
                }
            }
    }

    private fun startDownloadRequest(ids: List<String>): DlcServerRequiredStatus? {
        return try {
            lastError = null
            DlcServerConnection.startRequiredDownload(serverEntry, 8000, ids)
        } catch (ex: Exception) {
            lastError = ex.message
            null
        }
    }

    private fun uninstallEntry(root: DlcServerRequiredItem) {
        val ids = collectUninstallTargets(root)
        CompletableFuture
            .runAsync {
                try {
                    DlcServerConnection.uninstallRequired(serverEntry, 8000, ArrayList(ids))
                } catch (_: Exception) {
                }
            }
            .whenComplete { _, _ -> Minecraft.getInstance().execute { loadRequiredList() } }
    }

    private fun startPolling() {
        Thread({
            while (downloading) {
                val next = fetchStatus()
                if (next != null) {
                    updateStatus(next)
                    if (next.finished) {
                        downloading = false
                        Minecraft.getInstance().execute { finishDownload(next) }
                        break
                    }
                }
                try {
                    Thread.sleep(200L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "dlc-server-required-poll").start()
    }

    private fun fetchStatus(): DlcServerRequiredStatus? {
        return try {
            DlcServerConnection.fetchRequiredDownloadStatus(serverEntry, 3000)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateStatus(next: DlcServerRequiredStatus?) {
        status = next
        if (next == null) {
            return
        }
        var statusError = next.error
        for (item in next.items) {
            val id = item.id ?: continue
            if (statusError.isNullOrBlank() && !item.error.isNullOrBlank()) {
                statusError = item.error
            }
            val progress = if (item.totalBytes > 0) {
                item.downloadedBytes / item.totalBytes.toFloat()
            } else if (item.done) {
                1f
            } else {
                0f
            }
            progressById[id] = progress
        }
        batchState.progress = if (next.totalBytes > 0) {
            next.downloadedBytes / next.totalBytes.toFloat()
        } else if (next.totalCount > 0) {
            next.completedCount / next.totalCount.toFloat()
        } else {
            0f
        }
        if (next.finished && !statusError.isNullOrBlank()) {
            errorText = statusError
        }
    }

    private fun finishDownload(next: DlcServerRequiredStatus?) {
        downloading = false
        val ok = isStatusOk(next)
        val reason = getStatusError(next)
        if (ok) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                Minecraft.getInstance().toasts,
                Component.translatable("dlc_manager.required.ready"),
                Component.empty(),
            )
        } else {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                Minecraft.getInstance().toasts,
                Component.translatable("dlc_manager.required.error", ""),
                if (reason.isBlank()) Component.empty() else Component.literal(reason),
            )
        }
        updateEntryButtons()
        updateApplyAllButton(false, false)
        activeIds = HashSet()
        loadRequiredList()
    }

    private fun isStatusOk(next: DlcServerRequiredStatus?): Boolean {
        if (next == null) {
            return false
        }
        for (item in next.items) {
            if (item.done && !item.success) {
                return false
            }
        }
        return next.success
    }

    private fun getStatusError(next: DlcServerRequiredStatus?): String {
        if (next == null) {
            return ""
        }
        if (!next.error.isNullOrBlank()) {
            return next.error ?: ""
        }
        for (item in next.items) {
            if (!item.error.isNullOrBlank()) {
                return item.error ?: ""
            }
        }
        return ""
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        for (line in manageDescLines) {
            guiGraphics.drawCenteredString(font, line, centerX, y, 0xC8C8C8)
            y += font.lineHeight + 2
        }
        val error = errorText
        if (!error.isNullOrBlank()) {
            y += 4
            guiGraphics.drawCenteredString(font, error, centerX, y, 0xE6E6E6)
            y += font.lineHeight + 2
        }
        y += 8

        val listTop = y
        val listBottom = height - 70
        val lineHeight = 18
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val totalLines = visibleItems.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }

        var rowY = listTop
        for (i in visibleItems.indices) {
            val item = visibleItems[i]
            val button = item.id?.let { entryButtons[it] }
            val visible = i >= scrollIndex && i < scrollIndex + maxVisible
            if (!visible) {
                if (button != null) {
                    button.visible = false
                }
                continue
            }
            if (button != null) {
                button.visible = true
                button.y = rowY - 2
            }
            val label = buildEntryLabel(item)
            guiGraphics.drawString(font, label, 20, rowY, 0xE6E6E6, false)
            val id = item.id
            if (id != null) {
                val progress = progressById[id]
                if (progress != null) {
                    drawRightProgress(guiGraphics, rowY, button, progress)
                }
            }
            if (isMouseOverRow(mouseX, mouseY, rowY, lineHeight) && !isMouseOverButton(button, mouseX, mouseY)) {
                renderTooltip(guiGraphics, item, mouseX, mouseY)
            }
            rowY += lineHeight
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
        renderInstallOverlays(guiGraphics)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val listTop = getListTop()
        val listBottom = height - 70
        val lineHeight = 18
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val totalLines = visibleItems.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (delta > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    private fun getListTop(): Int {
        var y = 20 + 18
        y += manageDescLines.size * (font.lineHeight + 2)
        if (!errorText.isNullOrBlank()) {
            y += font.lineHeight + 6
        }
        y += 8
        return y
    }

    private val manageDescLines: List<String>
        get() = noticeLines

    private fun splitLines(text: String?): List<String> {
        if (text.isNullOrBlank()) {
            return ArrayList()
        }
        if (text.contains("\n")) {
            val parts = text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val lines: MutableList<String> = ArrayList()
            for (part in parts) {
                if (part.isNotEmpty()) {
                    lines.add(part)
                }
            }
            if (lines.isNotEmpty()) {
                return lines
            }
        }
        return ArrayList(listOf(text))
    }

    private fun handleBack() {
        Minecraft.getInstance().setScreen(parent)
    }

    private fun buildEntryLabel(item: DlcServerRequiredItem?): Component {
        val name = resolveDisplayName(item)
        var label = DlcTextFormatter.parse(name)
        val applied = isApplied(item)
        val pending = item != null && item.hasFile && item.needsApply
        if (applied) {
            label = label.copy().withStyle(ChatFormatting.GREEN)
        } else if (pending) {
            label = DlcTextFormatter.parse(name)
                .copy()
                .append(Component.literal(" "))
                .append(Component.translatable("dlc_manager.required.pending_restart"))
                .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)
        }
        return label
    }

    private fun getEntryButtonLabel(item: DlcServerRequiredItem?): String {
        return if (isApplied(item)) {
            Component.translatable("dlc_manager.required.uninstall").string
        } else {
            Component.translatable("dlc_manager.required.download").string
        }
    }

    private fun updateEntryButtons() {
        for (item in visibleItems) {
            val id = item.id ?: continue
            val button = entryButtons[id] ?: continue
            if (downloading) {
                button.active = false
                if (activeIds.contains(id) || progressById.containsKey(id)) {
                    button.message = Component.literal("")
                } else {
                    button.message = Component.literal(getEntryButtonLabel(item))
                }
            } else {
                button.active = true
                button.message = Component.literal(getEntryButtonLabel(item))
            }
        }
        updateApplyAllAvailability()
    }

    private fun updateApplyAllButton(batch: Boolean, active: Boolean) {
        val applyAll = applyAllButton ?: return
        if (!active) {
            applyAll.active = true
            applyAll.message = Component.translatable("dlc_manager.required.apply_all")
            batchState.running = false
            batchState.progress = 0f
            return
        }
        applyAll.active = false
        if (batch) {
            applyAll.message = Component.literal("")
        }
    }

    private fun updateApplyAllAvailability() {
        val applyAll = applyAllButton ?: return
        if (downloading || batchState.running) {
            return
        }
        var allApplied = true
        for (item in visibleItems) {
            if (!isApplied(item)) {
                allApplied = false
                break
            }
        }
        applyAll.active = !allApplied
        applyAll.message = Component.translatable("dlc_manager.required.apply_all")
    }

    private fun renderInstallOverlays(guiGraphics: GuiGraphics) {
        val applyAll = applyAllButton
        if (batchState.running && applyAll != null) {
            drawProgressButton(guiGraphics, applyAll.x, applyAll.y, applyAll.width, applyAll.height, batchState.progress)
        }
    }

    private fun drawProgressButton(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val bg = 0xFF2B2B2B.toInt()
        val fg = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + width, y + height, bg)
        val clamped = max(0f, min(1f, progress))
        val filled = (width * clamped).toInt()
        guiGraphics.fill(x, y, x + filled, y + height, fg)
        val percent = (clamped * 100f).roundToInt()
        val label = "$percent%"
        val textX = x + (width - font.width(label)) / 2
        val textY = y + (height - font.lineHeight) / 2 + 1
        guiGraphics.drawString(font, label, textX, textY, 0xFFFFFF, false)
    }

    private fun drawRightProgress(guiGraphics: GuiGraphics, rowY: Int, button: Button?, progress: Float) {
        val percent = (max(0f, min(1f, progress)) * 100f).roundToInt()
        val progressText = "$percent%"
        val textWidth = font.width(progressText)
        val percentBoxWidth = font.width("100%")
        val barWidth = 36
        val barHeight = 6
        val percentRight = button?.let { it.x - 6 } ?: (width - 18)
        val percentLeft = percentRight - percentBoxWidth
        val barRight = percentLeft - 6
        val barLeft = barRight - barWidth
        val barY = rowY + 2
        drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress)
        guiGraphics.drawString(font, progressText, percentRight - textWidth, rowY, 0xC8C8C8, false)
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val bg = 0xFF2B2B2B.toInt()
        val fg = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + width, y + height, bg)
        val filled = (width * max(0f, min(1f, progress))).toInt()
        guiGraphics.fill(x, y, x + filled, y + height, fg)
    }

    private fun renderTooltip(guiGraphics: GuiGraphics, item: DlcServerRequiredItem?, mouseX: Int, mouseY: Int) {
        if (item == null || item.tips.isEmpty()) {
            return
        }
        val tip = resolveTip(item)
        if (tip.isBlank()) {
            return
        }
        val maxWidth = min(260, width - 40)
        val lines = DlcTextFormatter.splitMultiline(font, tip, maxWidth)
        guiGraphics.renderTooltip(font, lines, mouseX, mouseY)
    }

    private fun resolveTip(item: DlcServerRequiredItem?): String {
        if (item == null || item.tips.isEmpty()) {
            return ""
        }
        val lang = Minecraft.getInstance().options.languageCode
        var value = item.tips[lang]
        if (!value.isNullOrBlank()) {
            return value
        }
        value = item.tips["en_us"]
        if (!value.isNullOrBlank()) {
            return value
        }
        return item.tips.values.firstOrNull() ?: ""
    }

    private fun isApplied(item: DlcServerRequiredItem?): Boolean {
        return item != null && item.applied
    }

    private fun resolveDisplayName(item: DlcServerRequiredItem?): String {
        if (item == null) {
            return ""
        }
        val lang = Minecraft.getInstance().languageManager.selected
        var value = item.displayNames[lang]
        if (!value.isNullOrBlank()) {
            return value
        }
        value = item.displayNames["en_us"]
        if (!value.isNullOrBlank()) {
            return value
        }
        if (!item.name.isNullOrBlank()) {
            return item.name ?: ""
        }
        return item.id ?: ""
    }

    private fun isMouseOverRow(mouseX: Int, mouseY: Int, y: Int, height: Int): Boolean {
        return mouseX >= 16 && mouseX <= width - 16 && mouseY >= y && mouseY <= y + height
    }

    private fun isMouseOverButton(button: Button?, mouseX: Int, mouseY: Int): Boolean {
        if (button == null || !button.visible) {
            return false
        }
        return button.isMouseOver(mouseX.toDouble(), mouseY.toDouble())
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return
        }
        val barX = width - 12
        val barWidth = 4
        val barHeight = max(12, (bottom - top) * maxVisible / totalLines)
        val trackHeight = bottom - top
        val maxTrack = max(1, trackHeight - barHeight)
        val barY = top + (scrollIndex / maxScroll.toFloat() * maxTrack).toInt()
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B.toInt())
    }

    private fun buildDependents(entries: List<DlcServerRequiredItem>): Map<String, Set<String>> {
        val map: MutableMap<String, MutableSet<String>> = HashMap()
        for (entry in entries) {
            val id = entry.id ?: continue
            for (dep in entry.dependencies) {
                if (dep.isBlank()) {
                    continue
                }
                map.computeIfAbsent(dep) { HashSet() }.add(id)
            }
        }
        return map
    }

    private fun collectUninstallTargets(root: DlcServerRequiredItem?): Set<String> {
        val remove: MutableSet<String> = HashSet()
        val rootId = root?.id
        if (rootId == null) {
            return remove
        }
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (!queue.isEmpty()) {
            val id = queue.poll()
            if (!remove.add(id)) {
                continue
            }
            val entry = index[id] ?: continue
            for (depId in entry.dependencies) {
                if (depId.isBlank()) {
                    continue
                }
                val depEntry = index[depId]
                if (depEntry != null && !isEnabled(depEntry)) {
                    continue
                }
                if (hasOtherDependents(depId, remove)) {
                    continue
                }
                queue.add(depId)
            }
            val ups = dependents[id]
            if (ups != null) {
                for (up in ups) {
                    if (remove.contains(up)) {
                        continue
                    }
                    val upEntry = index[up]
                    if (upEntry != null && !isEnabled(upEntry)) {
                        continue
                    }
                    queue.add(up)
                }
            }
        }
        return remove
    }

    private fun hasOtherDependents(id: String, removing: Set<String>): Boolean {
        val deps = dependents[id]
        if (deps.isNullOrEmpty()) {
            return false
        }
        for (dep in deps) {
            if (removing.contains(dep)) {
                continue
            }
            val entry = index[dep]
            if (entry != null && isEnabled(entry)) {
                return true
            }
        }
        return false
    }

    private fun isEnabled(item: DlcServerRequiredItem?): Boolean {
        return item != null && (item.applied || item.hasFile)
    }
}
