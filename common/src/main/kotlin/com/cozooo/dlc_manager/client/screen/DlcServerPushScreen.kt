package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

class DlcServerPushScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.push_title")) {
    private val rootDir: Path = DlcPaths.gameDir().toAbsolutePath().normalize()
    private var currentDir: Path = rootDir

    private var modeButton: Button? = null
    private var pushButton: Button? = null
    private var backButton: Button? = null
    private var upButton: Button? = null
    private var clearButton: Button? = null

    private var overwriteMode = true
    private var pushing = false
    private var statusText: String? = null
    private var errorText: String? = null
    private val selectedFiles: MutableSet<String> = LinkedHashSet()
    private val dirFilesCache: MutableMap<Path, List<String>> = HashMap()
    private var entries: List<FileEntry> = emptyList()

    private var scrollIndex = 0
    private var draggingScrollBar = false

    override fun init() {
        val buttonHeight = 20
        val bottomY = height - 28
        val modeWidth = 96
        val pushWidth = 96
        val backWidth = 96
        val gap = 6

        val modeLabel = Component.translatable("dlc_manager.server.push_mode")
        val modeLabelWidth = font.width(modeLabel)
        val modeX = 20 + modeLabelWidth + 6
        val mode = Button.builder(Component.empty()) { toggleMode() }
            .bounds(modeX, bottomY, modeWidth, buttonHeight)
            .build()

        val rightMargin = 10
        val backX = width - rightMargin - backWidth
        val pushX = backX - gap - pushWidth
        val push = Button.builder(Component.translatable("dlc_manager.server.push_start")) { confirmPush() }
            .bounds(pushX, bottomY, pushWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(backX, bottomY, backWidth, buttonHeight)
            .build()

        val topButtonY = 56
        val up = Button.builder(Component.translatable("dlc_manager.server.push_up")) { goUp() }
            .bounds(20, topButtonY, 70, buttonHeight)
            .build()
        val clear = Button.builder(Component.translatable("dlc_manager.server.push_clear_selection", 0)) { clearSelection() }
            .bounds(width - 20 - 140, topButtonY, 140, buttonHeight)
            .build()

        modeButton = mode
        pushButton = push
        backButton = back
        upButton = up
        clearButton = clear

        addRenderableWidget(mode)
        addRenderableWidget(push)
        addRenderableWidget(back)
        addRenderableWidget(up)
        addRenderableWidget(clear)
        refreshEntries()
        updateLabels()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        guiGraphics.drawCenteredString(font, title, centerX, 20, 0xFFFFFF)
        guiGraphics.drawString(
            font,
            Component.translatable("dlc_manager.server.push_current_dir", currentDirLabel),
            20,
            36,
            0xC8C8C8,
            false,
        )
        if (selectedFiles.isNotEmpty()) {
            guiGraphics.drawString(
                font,
                Component.translatable("dlc_manager.server.push_selected_count", selectedFiles.size),
                20,
                48,
                0xC8C8C8,
                false,
            )
        }
        val error = errorText
        if (!error.isNullOrBlank()) {
            guiGraphics.drawString(font, error, 20, 48, 0xE6E6E6, false)
        }

        val listTop = 80
        val listBottom = height - 60
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = entries.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        val end = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until end) {
            val fileEntry = entries[i]
            val rowY = listTop + (i - scrollIndex) * LINE_HEIGHT
            val state = getEntryState(fileEntry)
            drawCheckbox(guiGraphics, CHECKBOX_X, rowY + 1, CHECKBOX_SIZE, state)
            val color = if (fileEntry.isDir) 0x66A8FF else 0xE6E6E6
            val label = if (fileEntry.isDir) fileEntry.name + "/" else fileEntry.name
            guiGraphics.drawString(font, label, CHECKBOX_X + CHECKBOX_SIZE + 6, rowY, color, false)
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible)

        val modeLabelBottom = Component.translatable("dlc_manager.server.push_mode")
        guiGraphics.drawString(font, modeLabelBottom, 20, height - 23, 0xC8C8C8, false)
        if (pushing) {
            val text = statusText ?: ""
            guiGraphics.drawCenteredString(font, text, centerX, height - 48, 0xC8C8C8)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val listTop = 80
            val listBottom = height - 60
            val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
            val index = ((mouseY - listTop) / LINE_HEIGHT).toInt() + scrollIndex
            if (mouseY >= listTop && mouseY <= listBottom && index >= 0 && index < entries.size) {
                val fileEntry = entries[index]
                if (mouseX >= CHECKBOX_X && mouseX <= CHECKBOX_X + CHECKBOX_SIZE + 4) {
                    toggleEntry(fileEntry)
                    return true
                }
                if (fileEntry.isDir) {
                    openDirectory(fileEntry.path)
                    return true
                }
            }
            val barX = width - 10
            if (mouseX >= barX && mouseX <= barX + 6 && mouseY >= listTop && mouseY <= listBottom) {
                draggingScrollBar = true
                updateScrollFromDrag(mouseY)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            draggingScrollBar = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollBar) {
            updateScrollFromDrag(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(deltaY: Double): Boolean {
        if (deltaY != 0.0) {
            scrollIndex = max(0, scrollIndex - sign(deltaY).toInt())
            return true
        }
        return false
    }

    private fun updateScrollFromDrag(mouseY: Double) {
        val listTop = 80
        val listBottom = height - 60
        val total = entries.size
        val visible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val maxScroll = max(0, total - visible)
        if (maxScroll <= 0) {
            scrollIndex = 0
            return
        }
        val barHeight = max(10, (listBottom - listTop) * visible / total)
        val maxBarTravel = listBottom - listTop - barHeight
        var relY = mouseY.toInt() - listTop - barHeight / 2
        relY = max(0, min(maxBarTravel, relY))
        scrollIndex = (relY * (maxScroll / maxBarTravel.toDouble())).roundToInt()
    }

    private fun toggleMode() {
        if (pushing) {
            return
        }
        overwriteMode = !overwriteMode
        updateLabels()
    }

    private fun updateLabels() {
        modeButton?.let {
            val modeLabel = if (overwriteMode) {
                Component.translatable("dlc_manager.server.push_mode_overwrite")
            } else {
                Component.translatable("dlc_manager.server.push_mode_diff")
            }
            it.message = modeLabel
            it.active = !pushing
        }
        pushButton?.active = !pushing
        backButton?.active = !pushing
        clearButton?.let {
            it.message = Component.translatable("dlc_manager.server.push_clear_selection", selectedFiles.size)
            it.active = !pushing && selectedFiles.isNotEmpty()
        }
        upButton?.active = !pushing && currentDir != rootDir
    }

    private fun confirmPush() {
        if (selectedFiles.isEmpty()) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                Minecraft.getInstance().toasts,
                Component.translatable("dlc_manager.server.push_no_selection"),
                Component.empty(),
            )
            return
        }
        val title = Component.translatable("dlc_manager.server.push_title")
        val message = Component.translatable("dlc_manager.server.push_confirm", buildTargetLabel(), modeLabel)
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    confirmPushAgain()
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun confirmPushAgain() {
        val title = Component.translatable("dlc_manager.server.push_title")
        val message = Component.translatable("dlc_manager.server.push_confirm_again")
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    startPush()
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun startPush() {
        if (pushing) {
            return
        }
        pushing = true
        statusText = Component.translatable("dlc_manager.server.pushing").string
        updateLabels()
        CompletableFuture
            .supplyAsync { doPush() }
            .whenComplete { ok, _ ->
                Minecraft.getInstance().execute {
                    pushing = false
                    if (ok == true) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.push_success"),
                            Component.empty(),
                        )
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.push_failed"),
                            Component.empty(),
                        )
                    }
                    updateLabels()
                }
            }
    }

    private fun doPush(): Boolean {
        return try {
            val files = ArrayList(selectedFiles)
            if (files.isEmpty()) {
                return false
            }
            DlcServerConnection.push(
                entry,
                20000,
                emptyList(),
                files,
                if (overwriteMode) "overwrite" else "diff",
                CrossPlatformRuntime.playerName(),
                CrossPlatformRuntime.playerUuid(),
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun buildTargetLabel(): String {
        return Component.translatable("dlc_manager.server.push_selected_count", selectedFiles.size).string
    }

    private val modeLabel: String
        get() = if (overwriteMode) {
            Component.translatable("dlc_manager.server.push_mode_overwrite").string
        } else {
            Component.translatable("dlc_manager.server.push_mode_diff").string
        }

    private fun refreshEntries() {
        entries = listEntries(currentDir)
        scrollIndex = 0
        updateLabels()
    }

    private fun listEntries(dir: Path?): List<FileEntry> {
        val result: MutableList<FileEntry> = ArrayList()
        if (dir == null || !Files.isDirectory(dir)) {
            return result
        }
        try {
            Files.newDirectoryStream(dir).use { stream: DirectoryStream<Path> ->
                for (path in stream) {
                    val isDir = Files.isDirectory(path)
                    val name = path.fileName?.toString() ?: path.toString()
                    result.add(FileEntry(path, name, isDir))
                }
            }
        } catch (_: Exception) {
        }
        result.sortWith(compareBy<FileEntry> { !it.isDir }.thenBy { it.name.lowercase() })
        return result
    }

    private fun openDirectory(dir: Path?) {
        if (dir == null) {
            return
        }
        val normalized = dir.toAbsolutePath().normalize()
        if (!normalized.startsWith(rootDir) || !Files.isDirectory(normalized)) {
            return
        }
        currentDir = normalized
        refreshEntries()
    }

    private fun goUp() {
        if (currentDir == rootDir) {
            return
        }
        val parentDir = currentDir.parent
        if (parentDir == null || !parentDir.startsWith(rootDir)) {
            return
        }
        currentDir = parentDir
        refreshEntries()
    }

    private fun getEntryState(entry: FileEntry?): SelectionState {
        if (entry == null) {
            return SelectionState.NONE
        }
        if (!entry.isDir) {
            val rel = toRelative(entry.path)
            return if (rel != null && selectedFiles.contains(rel)) SelectionState.ALL else SelectionState.NONE
        }
        val files = getDirFiles(entry.path)
        if (files.isEmpty()) {
            return SelectionState.NONE
        }
        var anySelected = false
        for (file in files) {
            if (selectedFiles.contains(file)) {
                anySelected = true
            } else if (anySelected) {
                return SelectionState.PARTIAL
            }
        }
        if (anySelected) {
            return SelectionState.ALL
        }
        return SelectionState.NONE
    }

    private fun toggleEntry(entry: FileEntry?) {
        if (entry == null) {
            return
        }
        if (!entry.isDir) {
            val rel = toRelative(entry.path)
            if (rel.isNullOrBlank()) {
                return
            }
            if (!selectedFiles.add(rel)) {
                selectedFiles.remove(rel)
            }
            updateLabels()
            return
        }
        val files = getDirFiles(entry.path)
        if (files.isEmpty()) {
            return
        }
        var allSelected = true
        for (file in files) {
            if (!selectedFiles.contains(file)) {
                allSelected = false
                break
            }
        }
        if (allSelected) {
            selectedFiles.removeAll(files.toSet())
        } else {
            selectedFiles.addAll(files)
        }
        updateLabels()
    }

    private fun getDirFiles(dir: Path?): List<String> {
        if (dir == null) {
            return emptyList()
        }
        val cached = dirFilesCache[dir]
        if (cached != null) {
            return cached
        }
        val files: MutableList<String> = ArrayList()
        try {
            Files.walk(dir).use { stream ->
                stream.filter(Files::isRegularFile).forEach { path ->
                    val rel = toRelative(path)
                    if (!rel.isNullOrBlank()) {
                        files.add(rel)
                    }
                }
            }
        } catch (_: Exception) {
        }
        dirFilesCache[dir] = files
        return files
    }

    private fun toRelative(path: Path?): String? {
        if (path == null) {
            return null
        }
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(rootDir)) {
            return null
        }
        val relative = rootDir.relativize(normalized)
        val rel = relative.toString()
        if (rel.isBlank()) {
            return null
        }
        return rel.replace('\\', '/')
    }

    private fun clearSelection() {
        selectedFiles.clear()
        updateLabels()
    }

    private val currentDirLabel: String
        get() {
            if (currentDir == rootDir) {
                return "/"
            }
            val rel = rootDir.relativize(currentDir).toString().replace('\\', '/')
            return "/$rel"
        }

    private fun drawCheckbox(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int, state: SelectionState) {
        val checked = state == SelectionState.ALL
        val color = if (checked) 0xFF66FF66.toInt() else 0xFF777777.toInt()
        guiGraphics.fill(x, y, x + size, y + size, color)
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF000000.toInt())
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF66FF66.toInt())
        } else if (state == SelectionState.PARTIAL) {
            val barY = y + size / 2 - 1
            guiGraphics.fill(x + 2, barY, x + size - 2, barY + 2, 0xFFBBBBBB.toInt())
        }
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, total: Int, visible: Int) {
        if (total <= visible) {
            return
        }
        val barHeight = max(10, (bottom - top) * visible / total)
        val barX = width - 10
        val maxScroll = total - visible
        val barY = top + (bottom - top - barHeight) * scrollIndex / max(1, maxScroll)
        guiGraphics.fill(barX, top, barX + 6, bottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(barX, barY, barX + 6, barY + barHeight, 0xFFCCCCCC.toInt())
    }

    private class FileEntry(
        val path: Path,
        val name: String,
        val isDir: Boolean,
    )

    private enum class SelectionState {
        NONE,
        PARTIAL,
        ALL,
    }

    companion object {
        private const val LINE_HEIGHT = 10
        private const val CHECKBOX_SIZE = 8
        private const val CHECKBOX_X = 12
    }
}
