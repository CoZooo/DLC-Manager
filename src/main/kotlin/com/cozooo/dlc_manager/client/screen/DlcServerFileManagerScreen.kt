package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry
import com.cozooo.dlc_manager.client.core.DlcFilePicker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcServerFileManagerScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.files_title")) {
    private var pathField: EditBox? = null
    private var entries: List<DlcServerFileEntry> = ArrayList()
    private var scrollIndex = 0
    private var selectedIndex = -1
    private var lastClickIndex = -1
    private var lastClickTime = 0L
    private var refreshButton: Button? = null
    private var upButton: Button? = null
    private var uploadButton: Button? = null
    private var saveButton: Button? = null
    private var deleteButton: Button? = null
    private var backButton: Button? = null
    private var loading = false
    private var draggingScroll = false
    private var scrollDragOffset = 0

    override fun init() {
        val centerX = width / 2
        var fieldWidth = width - 120
        if (fieldWidth < 160) {
            fieldWidth = 160
        }
        val field = EditBox(font, centerX - fieldWidth / 2, 36, fieldWidth, 20, Component.translatable("dlc_manager.server.files_path"))
        field.setMaxLength(512)
        pathField = field
        addRenderableWidget(field)

        val panelLeft = 24
        val panelRight = width - 24
        val buttonWidth = 90
        val buttonHeight = 20
        val topButtonsY = 62
        var gap = (panelRight - panelLeft - buttonWidth * 4) / 5
        if (gap < 6) {
            gap = 6
        }
        val startX = panelLeft + gap
        val refresh = Button.builder(Component.translatable("dlc_manager.server.files_refresh")) { refresh() }
            .bounds(startX, topButtonsY, buttonWidth, buttonHeight)
            .build()
        val delete = Button.builder(Component.translatable("dlc_manager.server.files_delete")) { confirmDelete() }
            .bounds(startX + buttonWidth + gap, topButtonsY, buttonWidth, buttonHeight)
            .build()
        val save = Button.builder(Component.translatable("dlc_manager.server.files_save")) { saveSelected() }
            .bounds(startX + (buttonWidth + gap) * 2, topButtonsY, buttonWidth, buttonHeight)
            .build()
        val upload = Button.builder(Component.translatable("dlc_manager.server.files_upload")) { uploadFile() }
            .bounds(startX + (buttonWidth + gap) * 3, topButtonsY, buttonWidth, buttonHeight)
            .build()

        val bottomY = height - 28
        val up = Button.builder(Component.translatable("dlc_manager.server.files_up")) { goUp() }
            .bounds(centerX - buttonWidth - 6, bottomY, buttonWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(centerX + 6, bottomY, buttonWidth, buttonHeight)
            .build()

        refreshButton = refresh
        upButton = up
        uploadButton = upload
        saveButton = save
        deleteButton = delete
        backButton = back

        addRenderableWidget(refresh)
        addRenderableWidget(up)
        addRenderableWidget(upload)
        addRenderableWidget(save)
        addRenderableWidget(delete)
        addRenderableWidget(back)

        updateSelectionState()
        refresh()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        guiGraphics.drawCenteredString(font, title, centerX, 16, 0xFFFFFF)
        val field = pathField
        if (field != null) {
            guiGraphics.drawString(font, Component.translatable("dlc_manager.server.files_path"), field.x, field.y - 10, 0xC8C8C8, false)
        }
        renderList(guiGraphics)
        if (loading) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.server.files_loading"), centerX, height - 62, 0xC8C8C8)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderList(guiGraphics: GuiGraphics) {
        val listTop = 90
        val listBottom = height - 40
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val totalLines = entries.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var rowY = listTop
        for (i in entries.indices) {
            if (i < scrollIndex || i >= scrollIndex + maxVisible) {
                continue
            }
            val fileEntry = entries[i]
            val selected = i == selectedIndex
            if (selected) {
                val left = 24
                val right = width - 24
                val top = rowY + 1
                val bottom = rowY + ROW_HEIGHT - 3
                guiGraphics.fill(left, top, right, bottom, 0x33222222)
                guiGraphics.fill(left, top, right, top + 1, 0xFFFFFFFF.toInt())
                guiGraphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF.toInt())
                guiGraphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF.toInt())
                guiGraphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF.toInt())
            }
            val rawName = fileEntry.name ?: ""
            val label = if (fileEntry.dir) "$rawName/" else rawName
            val color = if (fileEntry.dir) 0x6BA7FF else 0xE6E6E6
            guiGraphics.drawString(font, label, 32, rowY + 4, color, false)
            if (!fileEntry.dir) {
                val size = formatSize(fileEntry.size)
                guiGraphics.drawString(font, size, width - 90, rowY + 4, 0x9A9A9A, false)
            }
            rowY += ROW_HEIGHT
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val listTop = 90
        val listBottom = height - 40
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val maxScroll = max(0, entries.size - maxVisible)
        if (startScrollDrag(mouseX, mouseY, listTop, listBottom, entries.size, maxVisible, maxScroll)) {
            return true
        }
        if (mouseY >= listTop && mouseY <= listBottom) {
            val index = ((mouseY - listTop) / ROW_HEIGHT).toInt() + scrollIndex
            if (index >= 0 && index < entries.size) {
                val now = System.currentTimeMillis()
                if (index == lastClickIndex && now - lastClickTime <= 250) {
                    selectedIndex = index
                    openSelected()
                    lastClickIndex = -1
                    lastClickTime = 0L
                    return true
                }
                selectedIndex = index
                lastClickIndex = index
                lastClickTime = now
                updateSelectionState()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScroll) {
            val listTop = 90
            val listBottom = height - 40
            val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
            val maxScroll = max(0, entries.size - maxVisible)
            updateScrollFromDrag(mouseY, listTop, listBottom, entries.size, maxVisible, maxScroll)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingScroll) {
            draggingScroll = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, verticalAmount: Double): Boolean {
        val listTop = 90
        val listBottom = height - 40
        if (mouseY < listTop || mouseY > listBottom) {
            return false
        }
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val maxScroll = max(0, entries.size - maxVisible)
        if (maxScroll > 0) {
            val step = if (verticalAmount > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    private fun refresh() {
        if (loading) {
            return
        }
        loading = true
        val path = pathField?.value?.trim() ?: ""
        CompletableFuture
            .supplyAsync { loadEntries(path) }
            .whenComplete { list, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (list != null) {
                        entries = list
                        selectedIndex = -1
                        updateSelectionState()
                    }
                }
            }
    }

    private fun loadEntries(path: String): List<DlcServerFileEntry>? {
        return try {
            DlcServerConnection.listFiles(entry, 10000, path)
        } catch (_: Exception) {
            null
        }
    }

    private fun goUp() {
        val field = pathField ?: return
        val path = field.value.trim()
        if (path.isEmpty()) {
            return
        }
        var normalized = path.replace("\\", "/")
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length - 1)
        }
        val idx = normalized.lastIndexOf('/')
        if (idx <= 0) {
            field.value = ""
        } else {
            field.value = normalized.substring(0, idx)
        }
        refresh()
    }

    private fun openSelected() {
        val fileEntry = selectedEntry
        if (fileEntry == null || !fileEntry.dir) {
            return
        }
        val entryName = fileEntry.name ?: return
        val field = pathField ?: return
        val path = field.value.trim()
        val next = if (path.isEmpty()) entryName else "$path/$entryName"
        field.value = next
        refresh()
    }

    private fun uploadFile() {
        val file = DlcFilePicker.pickAnyFile(Component.translatable("dlc_manager.server.files_upload").string)
        if (file == null) {
            return
        }
        val path = pathField?.value?.trim() ?: ""
        val target = if (path.isEmpty()) file.fileName.toString() else path + "/" + file.fileName.toString()
        loading = true
        CompletableFuture
            .supplyAsync { uploadPath(target, file) }
            .whenComplete { ok, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (ok == true) {
                        refresh()
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.files_upload_failed"),
                            Component.empty(),
                        )
                    }
                }
            }
    }

    private fun upload(target: String, file: Path): Boolean {
        return try {
            DlcServerConnection.uploadFile(entry, 20000, target, file)
        } catch (_: Exception) {
            false
        }
    }

    private fun uploadPath(target: String, source: Path?): Boolean {
        return try {
            if (source == null || !Files.exists(source)) {
                return false
            }
            if (!Files.isDirectory(source)) {
                return upload(target, source)
            }
            val base = source
            val baseTarget = target
            Files.walk(base).use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .allMatch { file ->
                        val relative = base.relativize(file)
                        val rel = relative.toString().replace("\\", "/")
                        val childTarget = if (baseTarget.isEmpty()) rel else "$baseTarget/$rel"
                        try {
                            upload(childTarget, file)
                        } catch (_: Exception) {
                            false
                        }
                    }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun saveSelected() {
        val fileEntry = selectedEntry ?: return
        val entryName = fileEntry.name ?: return
        val basePath = pathField?.value?.trim() ?: ""
        val remotePath = if (basePath.isEmpty()) entryName else "$basePath/$entryName"
        val fileName = if (fileEntry.dir) "$entryName.zip" else entryName
        var target = DlcFilePicker.pickSaveFile(Component.translatable("dlc_manager.server.files_save").string, fileName)
        if (target == null) {
            return
        }
        target = if (Files.isDirectory(target)) {
            target.resolve(fileName)
        } else if (fileEntry.dir && !target.toString().endsWith(".zip")) {
            target.resolveSibling(target.fileName.toString() + ".zip")
        } else {
            target
        }
        val finalTarget = target
        loading = true
        CompletableFuture
            .supplyAsync { download(remotePath, finalTarget) }
            .whenComplete { ok, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (ok == true) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.files_save_success"),
                            Component.empty(),
                        )
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.files_save_failed"),
                            Component.empty(),
                        )
                    }
                }
            }
    }

    private fun download(remotePath: String, target: Path): Boolean {
        return try {
            DlcServerConnection.downloadFile(entry, 20000, remotePath, target)
        } catch (_: Exception) {
            false
        }
    }

    private fun updateSelectionState() {
        saveButton?.active = selectedEntry != null
    }

    private fun confirmDelete() {
        val fileEntry = selectedEntry ?: return
        val title = Component.translatable("dlc_manager.server.files_delete")
        val message = Component.translatable("dlc_manager.server.files_delete_confirm", fileEntry.name)
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    confirmDeleteAgain(fileEntry)
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun confirmDeleteAgain(fileEntry: DlcServerFileEntry) {
        val title = Component.translatable("dlc_manager.server.files_delete")
        val message = Component.translatable("dlc_manager.server.files_delete_confirm_again", fileEntry.name)
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    deleteSelected(fileEntry)
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun deleteSelected(fileEntry: DlcServerFileEntry) {
        val entryName = fileEntry.name ?: return
        val path = pathField?.value?.trim() ?: ""
        val target = if (path.isEmpty()) entryName else "$path/$entryName"
        loading = true
        CompletableFuture
            .supplyAsync { delete(target) }
            .whenComplete { ok, _ ->
                Minecraft.getInstance().execute {
                    loading = false
                    if (ok == true) {
                        refresh()
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.files_delete_failed"),
                            Component.empty(),
                        )
                    }
                }
            }
    }

    private fun delete(path: String): Boolean {
        return try {
            DlcServerConnection.deleteFile(entry, 15000, path)
        } catch (_: Exception) {
            false
        }
    }

    private val selectedEntry: DlcServerFileEntry?
        get() = if (selectedIndex >= 0 && selectedIndex < entries.size) entries[selectedIndex] else null

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (totalLines <= maxVisible) {
            return
        }
        val barX = width - 20
        val barWidth = 6
        val barHeight = max(20, (bottom - top) * maxVisible / max(1, totalLines))
        val trackHeight = bottom - top
        val barY = top + (trackHeight - barHeight) * scrollIndex / max(1, maxScroll)
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    private fun startScrollDrag(
        mouseX: Double,
        mouseY: Double,
        top: Int,
        bottom: Int,
        totalLines: Int,
        maxVisible: Int,
        maxScroll: Int,
    ): Boolean {
        if (totalLines <= maxVisible) {
            return false
        }
        val barX = width - 20
        val barWidth = 6
        val trackHeight = bottom - top
        val barHeight = max(20, trackHeight * maxVisible / max(1, totalLines))
        val barY = top + (trackHeight - barHeight) * scrollIndex / max(1, maxScroll)
        if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
            draggingScroll = true
            scrollDragOffset = (mouseY - barY).toInt()
            return true
        }
        return false
    }

    private fun updateScrollFromDrag(mouseY: Double, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (totalLines <= maxVisible || maxScroll <= 0) {
            scrollIndex = 0
            return
        }
        val trackHeight = bottom - top
        val barHeight = max(20, trackHeight * maxVisible / max(1, totalLines))
        val minY = top
        val maxY = bottom - barHeight
        val barY = max(minY.toDouble(), min(maxY.toDouble(), mouseY - scrollDragOffset)).toInt()
        val range = max(1, maxY - minY)
        val ratio = (barY - minY) / range.toDouble()
        scrollIndex = max(0, min(maxScroll, (ratio * maxScroll).roundToInt()))
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) {
            return ""
        }
        if (size > 1024 * 1024) {
            return String.format("%.1f MB", size / 1024.0 / 1024.0)
        }
        if (size > 1024) {
            return String.format("%.1f KB", size / 1024.0)
        }
        return "$size B"
    }

    companion object {
        private const val ROW_HEIGHT = 18
    }
}
