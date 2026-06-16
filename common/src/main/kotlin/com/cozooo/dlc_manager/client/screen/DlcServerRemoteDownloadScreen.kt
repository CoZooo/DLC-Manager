package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DcConfigIO
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

class DlcServerRemoteDownloadScreen(
    private val parent: Screen?,
    private val serverEntry: DlcServerEntry?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.remote.title")) {
    private val selectedKeys: MutableSet<String> = HashSet(setOf(ALL_MARKER))
    private val progressByKey: MutableMap<String, Float> = ConcurrentHashMap()
    @Volatile
    private var items: List<DlcServerRemoteItem> = emptyList()

    private var downloadButton: Button? = null
    private var backButton: Button? = null

    @Volatile
    private var loading = true
    @Volatile
    private var downloading = false
    @Volatile
    private var status: DlcServerRemoteStatus? = null
    @Volatile
    private var errorText: String? = null
    private var scrollIndex = 0
    private var draggingScrollBar = false
    private var scrollBarDragOffset = 0
    private var lastTotalProgress = 0f

    override fun init() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val download = Button.builder(Component.translatable("dlc_manager.remote.download")) { handleDownload() }
            .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build()
        download.active = false
        val back = Button.builder(Component.translatable("dlc_manager.required.skip")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
            .build()
        downloadButton = download
        backButton = back
        addRenderableWidget(download)
        addRenderableWidget(back)
        loadMissingList()
    }

    private fun loadMissingList() {
        loading = true
        CompletableFuture
            .supplyAsync { fetchMissing() }
            .whenComplete { list, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (list == null) {
                        errorText = Component.translatable("dlc_manager.server.remote_missing_failed").string
                        return@execute
                    }
                    items = list
                    if (items.isEmpty()) {
                        Minecraft.getInstance().setScreen(parent)
                        return@execute
                    }
                    downloadButton?.active = true
                }
            }
    }

    private fun fetchMissing(): List<DlcServerRemoteItem>? {
        return try {
            DlcServerConnection.fetchRemoteMissingList(serverEntry, 8000)
        } catch (_: Exception) {
            null
        }
    }

    private fun handleDownload() {
        if (downloading) {
            return
        }
        downloading = true
        downloadButton?.active = false
        CompletableFuture
            .supplyAsync { startDownload() }
            .whenComplete { started, _ ->
                if (started == null) {
                    return@whenComplete
                }
                startPolling()
            }
    }

    private fun startDownload(): DlcServerRemoteStatus? {
        return try {
            val keys = resolveSelectionKeys()
            DlcServerConnection.startRemoteDownload(serverEntry, 8000, keys)
        } catch (_: Exception) {
            null
        }
    }

    private fun startPolling() {
        Thread({
            while (downloading) {
                val next = fetchStatus()
                if (next != null) {
                    updateStatus(next)
                    if (next.finished) {
                        downloading = false
                        val ok = isStatusOk(next)
                        Minecraft.getInstance().execute {
                            if (ok) {
                                DlcScreenCompat.addOrUpdatePeriodicNotification(
                                    Minecraft.getInstance().toasts,
                                    Component.translatable("dlc_manager.server.remote_missing_success"),
                                    Component.empty(),
                                )
                            } else {
                                DlcScreenCompat.addOrUpdatePeriodicNotification(
                                    Minecraft.getInstance().toasts,
                                    Component.translatable("dlc_manager.server.remote_missing_failed"),
                                    Component.empty(),
                                )
                            }
                            when (parent) {
                                is DlcServerManagerScreen -> parent.refreshServerInfo()
                                is DlcServerMaLiLibScreen -> parent.refreshServerInfo()
                            }
                            Minecraft.getInstance().setScreen(parent)
                        }
                        break
                    }
                }
                try {
                    Thread.sleep(200L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "dlc-server-remote-poll").start()
    }

    private fun isStatusOk(next: DlcServerRemoteStatus?): Boolean {
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

    private fun fetchStatus(): DlcServerRemoteStatus? {
        return try {
            DlcServerConnection.fetchRemoteDownloadStatus(serverEntry, 3000)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateStatus(next: DlcServerRemoteStatus?) {
        status = next
        if (next == null) {
            return
        }
        for (item in next.items) {
            val key = item.key ?: continue
            val progress = if (item.totalBytes > 0) {
                item.downloadedBytes / item.totalBytes.toFloat()
            } else if (item.done) {
                1f
            } else {
                0f
            }
            progressByKey[key] = progress
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.remote.desc"), centerX, y, 0xC8C8C8)
        y += 16
        if (loading) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.remote.detecting"), centerX, y, 0xE6E6E6)
            y += 12
        }
        val error = errorText
        if (!error.isNullOrBlank()) {
            guiGraphics.drawCenteredString(font, DlcTextFormatter.parse(error), centerX, y, 0xE6E6E6)
            y += 12
        }

        val listTop = y
        drawCheckbox(guiGraphics, CHECKBOX_X, listTop + 1, CHECKBOX_SIZE, isAllSelected())
        guiGraphics.drawString(
            font,
            Component.translatable("dlc_manager.remote.select_all"),
            CHECKBOX_X + CHECKBOX_SIZE + 6,
            listTop,
            0xE6E6E6,
            false,
        )
        val listStart = listTop + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listStart) / LINE_HEIGHT)
        val totalLines = items.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        val end = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until end) {
            val item = items[i]
            val rowY = listStart + (i - scrollIndex) * LINE_HEIGHT
            val checked = isAllSelected() || selectedKeys.contains(item.key)
            drawCheckbox(guiGraphics, CHECKBOX_X, rowY + 1, CHECKBOX_SIZE, checked)
            val name = resolveDisplayName(item)
            guiGraphics.drawString(font, DlcTextFormatter.parse(name), CHECKBOX_X + CHECKBOX_SIZE + 6, rowY, 0xE6E6E6, false)
            if (downloading) {
                val progress = progressByKey[item.key]
                if (progress != null) {
                    val percent = (max(0f, min(1f, progress)) * 100f).roundToInt()
                    val progressText = "$percent%"
                    val textWidth = font.width(progressText)
                    val percentBoxWidth = font.width("100%")
                    val barWidth = 36
                    val barHeight = 6
                    val percentRight = width - 18
                    val percentLeft = percentRight - percentBoxWidth
                    val barRight = percentLeft - 6
                    val barLeft = barRight - barWidth
                    val barY = rowY + 2
                    drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress)
                    guiGraphics.drawString(font, progressText, percentRight - textWidth, rowY, 0xC8C8C8, false)
                }
            }
        }
        drawScrollBar(guiGraphics, listStart, listBottom, totalLines, maxVisible, maxScroll)

        val currentStatus = status
        if (downloading && currentStatus != null) {
            y = height - 70
            val totalProgress = resolveTotalProgress(currentStatus)
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.remote.downloading_multi"), centerX, y, 0xE6E6E6)
            y += 12
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, totalProgress)
            val percent = (totalProgress * 100f).roundToInt()
            guiGraphics.drawCenteredString(font, "$percent%", centerX, y + 10, 0xC8C8C8)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun resolveDisplayName(item: DlcServerRemoteItem?): String {
        if (item == null) {
            return ""
        }
        if (item.displayNames.isNotEmpty()) {
            val lang = DcConfigIO.normalizeLangKey(Minecraft.getInstance().options.languageCode)
            var name = if (lang == null) null else item.displayNames[lang]
            if (name.isNullOrBlank()) {
                name = item.displayNames["en_us"]
            }
            if (name.isNullOrBlank()) {
                name = item.displayNames.values.firstOrNull()
            }
            if (!name.isNullOrBlank()) {
                return name
            }
        }
        if (!item.name.isNullOrBlank()) {
            return item.name!!
        }
        return item.key ?: ""
    }

    private fun resolveTotalProgress(status: DlcServerRemoteStatus?): Float {
        if (status == null) {
            return 0f
        }
        if (status.items.isNotEmpty()) {
            var sum = 0f
            var count = 0
            for (item in status.items) {
                val progress = if (item.totalBytes > 0) {
                    item.downloadedBytes / item.totalBytes.toFloat()
                } else if (item.done) {
                    1f
                } else {
                    0f
                }
                sum += max(0f, min(1f, progress))
                count++
            }
            if (count > 0) {
                val computed = sum / count.toFloat()
                val clamped = max(0f, min(1f, computed))
                lastTotalProgress = max(lastTotalProgress, clamped)
                return lastTotalProgress
            }
        }
        if (status.totalBytes > 0) {
            val computed = max(0f, min(1f, status.downloadedBytes / status.totalBytes.toFloat()))
            lastTotalProgress = max(lastTotalProgress, computed)
            return lastTotalProgress
        }
        if (status.totalCount > 0) {
            val computed = max(0f, min(1f, status.completedCount / status.totalCount.toFloat()))
            lastTotalProgress = max(lastTotalProgress, computed)
            return lastTotalProgress
        }
        return 0f
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(deltaY: Double): Boolean {
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = items.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (deltaY > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && beginScrollDrag(mouseX, mouseY)) {
            return true
        }
        if (!downloading) {
            val listTop = getListTop()
            val listBottom = height - 70
            if (mouseY >= listTop && mouseY < listTop + LINE_HEIGHT) {
                val boxX = CHECKBOX_X
                val boxY = listTop + 1
                if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE &&
                    mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE
                ) {
                    toggleSelectAll()
                    return true
                }
            }
            val listStart = listTop + LINE_HEIGHT
            if (mouseY >= listStart && mouseY < listBottom) {
                val row = ((mouseY - listStart) / LINE_HEIGHT).toInt()
                val index = scrollIndex + row
                if (index >= 0 && index < items.size) {
                    val boxX = CHECKBOX_X
                    val boxY = listStart + row * LINE_HEIGHT + 1
                    if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE &&
                        mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE
                    ) {
                        toggleSelection(items[index])
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == 0 && draggingScrollBar) {
            updateScrollFromDrag(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollBar) {
            draggingScrollBar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
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

    private fun beginScrollDrag(mouseX: Double, mouseY: Double): Boolean {
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = items.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return false
        }
        val barX = width - 12
        val barWidth = 4
        val barHeight = max(12, (listBottom - listTop) * maxVisible / totalLines)
        val trackHeight = listBottom - listTop
        val maxTrack = max(1, trackHeight - barHeight)
        val barY = listTop + (scrollIndex / maxScroll.toFloat() * maxTrack).toInt()
        if (mouseX < barX || mouseX > barX + barWidth || mouseY < listTop || mouseY > listBottom) {
            return false
        }
        if (mouseY >= barY && mouseY <= barY + barHeight) {
            draggingScrollBar = true
            scrollBarDragOffset = mouseY.toInt() - barY
            return true
        }
        var targetBarY = mouseY.toInt() - barHeight / 2
        targetBarY = max(listTop, min(listTop + maxTrack, targetBarY))
        val newIndex = ((targetBarY - listTop) / maxTrack.toFloat() * maxScroll).roundToInt()
        scrollIndex = max(0, min(maxScroll, newIndex))
        return true
    }

    private fun updateScrollFromDrag(mouseY: Double) {
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = items.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return
        }
        val barHeight = max(12, (listBottom - listTop) * maxVisible / totalLines)
        val trackHeight = listBottom - listTop
        val maxTrack = max(1, trackHeight - barHeight)
        var targetBarY = mouseY.toInt() - scrollBarDragOffset
        targetBarY = max(listTop, min(listTop + maxTrack, targetBarY))
        val newIndex = ((targetBarY - listTop) / maxTrack.toFloat() * maxScroll).roundToInt()
        scrollIndex = max(0, min(maxScroll, newIndex))
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val bg = 0xFF2B2B2B.toInt()
        val fg = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + width, y + height, bg)
        val filled = (width * max(0f, min(1f, progress))).toInt()
        guiGraphics.fill(x, y, x + filled, y + height, fg)
    }

    private fun drawCheckbox(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int, checked: Boolean) {
        val bg = 0xFF2B2B2B.toInt()
        val outline = 0xFF6B6B6B.toInt()
        val fill = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + size, y + size, outline)
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, bg)
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, fill)
        }
    }

    private fun getListTop(): Int {
        return 20 + 18 + 16
    }

    private fun isAllSelected(): Boolean {
        return selectedKeys.contains(ALL_MARKER)
    }

    private fun toggleSelectAll() {
        if (isAllSelected()) {
            selectedKeys.clear()
            return
        }
        selectedKeys.clear()
        selectedKeys.add(ALL_MARKER)
    }

    private fun toggleSelection(item: DlcServerRemoteItem?) {
        val key = item?.key
        if (key.isNullOrBlank()) {
            return
        }
        if (isAllSelected()) {
            selectedKeys.clear()
            for (entry in items) {
                val entryKey = entry.key
                if (entryKey != null && entryKey != key) {
                    selectedKeys.add(entryKey)
                }
            }
            return
        }
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key)
        } else {
            selectedKeys.add(key)
        }
        if (items.isNotEmpty() && selectedKeys.size == items.size) {
            selectedKeys.clear()
            selectedKeys.add(ALL_MARKER)
        }
    }

    private fun resolveSelectionKeys(): List<String> {
        if (isAllSelected()) {
            return emptyList()
        }
        return ArrayList(selectedKeys)
    }

    companion object {
        private const val LINE_HEIGHT = 10
        private const val CHECKBOX_SIZE = 8
        private const val CHECKBOX_X = 12
        private const val ALL_MARKER = "__ALL__"
    }
}
