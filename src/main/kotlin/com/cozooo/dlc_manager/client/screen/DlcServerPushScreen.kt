package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class DlcServerPushScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcFilePageScreen(DlcUiPage.SERVER_PUSH, parent, Component.translatable("dlc_manager.server.push_title")) {
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

    private fun initControls() {
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
            DlcConfirmScreen({ accepted ->
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
            DlcConfirmScreen({ accepted ->
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
                DlcPlatformServices.playerName(),
                DlcPlatformServices.playerUuid(),
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
        val count = files.count { it in selectedFiles }
        return if (count == 0) SelectionState.NONE else if (count == files.size) SelectionState.ALL else SelectionState.PARTIAL
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



    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 31 * entries.hashCode() + pushing.hashCode()

        upButton?.let { mount(toolbar, it) }; clearButton?.let { mount(toolbar, it) }
        val list = fileList(currentDirLabel)
        for (file in entries) {
            fileRow(list, file.name + if (file.isDir) "/" else "", null,
                { when (getEntryState(file)) { SelectionState.ALL -> "true"; SelectionState.PARTIAL -> "mixed"; SelectionState.NONE -> "false" } },
                { !pushing }, { toggleEntry(file) }, if (file.isDir) Runnable { openDirectory(file.path) } else null)
        }
        live(content, { statusText }, "page-description"); live(content, { errorText }, "page-error"); modeButton?.let { mount(footer, it) }; pushButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }

    }
    override fun updatePage() {
        updateLabels()
        if (pageRevision != 31 * entries.hashCode() + pushing.hashCode()) refreshPage()
        super.updatePage()
    }

    override fun shouldCloseOnEsc() = !pushing
}
