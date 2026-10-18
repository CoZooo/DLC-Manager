package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry
import com.cozooo.dlc_manager.client.core.DlcFilePicker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

class DlcServerFileManagerScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcFilePageScreen(DlcUiPage.SERVER_FILES, parent, DlcTextCompat.translatable("dlc_manager.server.files_title")) {
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

    private fun initControls() {
        val centerX = width / 2
        var fieldWidth = width - 120
        if (fieldWidth < 160) {
            fieldWidth = 160
        }
        val field = EditBox(font, centerX - fieldWidth / 2, 36, fieldWidth, 20, DlcTextCompat.translatable("dlc_manager.server.files_path"))
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
        val refresh = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_refresh")) { refresh() }
            .bounds(startX, topButtonsY, buttonWidth, buttonHeight)
            .build()
        val delete = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_delete")) { confirmDelete() }
            .bounds(startX + buttonWidth + gap, topButtonsY, buttonWidth, buttonHeight)
            .build()
        val save = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_save")) { saveSelected() }
            .bounds(startX + (buttonWidth + gap) * 2, topButtonsY, buttonWidth, buttonHeight)
            .build()
        val upload = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_upload")) { uploadFile() }
            .bounds(startX + (buttonWidth + gap) * 3, topButtonsY, buttonWidth, buttonHeight)
            .build()

        val bottomY = height - 28
        val up = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.files_up")) { goUp() }
            .bounds(centerX - buttonWidth - 6, bottomY, buttonWidth, buttonHeight)
            .build()
        val back = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
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
        val file = DlcFilePicker.pickAnyFile(DlcTextCompat.translatable("dlc_manager.server.files_upload").string)
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
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.files_upload_failed"),
                            DlcTextCompat.empty(),
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
        var target = DlcFilePicker.pickSaveFile(DlcTextCompat.translatable("dlc_manager.server.files_save").string, fileName)
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
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.files_save_success"),
                            DlcTextCompat.empty(),
                        )
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.files_save_failed"),
                            DlcTextCompat.empty(),
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
        saveButton?.active = !loading && selectedEntry != null
        deleteButton?.active = !loading && selectedEntry != null
        refreshButton?.active = !loading
        uploadButton?.active = !loading
        upButton?.active = !loading
    }

    private fun confirmDelete() {
        val fileEntry = selectedEntry ?: return
        val title = DlcTextCompat.translatable("dlc_manager.server.files_delete")
        val message = DlcTextCompat.translatable("dlc_manager.server.files_delete_confirm", fileEntry.name)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen({ accepted ->
                if (accepted) {
                    confirmDeleteAgain(fileEntry)
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun confirmDeleteAgain(fileEntry: DlcServerFileEntry) {
        val title = DlcTextCompat.translatable("dlc_manager.server.files_delete")
        val message = DlcTextCompat.translatable("dlc_manager.server.files_delete_confirm_again", fileEntry.name)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen({ accepted ->
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
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.files_delete_failed"),
                            DlcTextCompat.empty(),
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

    private fun formatSize(size: Long): String = com.cozooo.dlc_manager.core.DlcFileSize.format(size)

    companion object {

    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 31 * entries.hashCode() + loading.hashCode()

        upButton?.let { mount(toolbar, it) }; refreshButton?.let { mount(toolbar, it) }; uploadButton?.let { mount(toolbar, it) }
        pathField?.let { mount(content, it, tr("dlc_manager.server.files_path"), "text") }
        if (entries.isEmpty() && !loading) text(content, tr("dlc_manager.ui.empty_files"), "page-description")
        val list = fileList(null)
        for ((index, file) in entries.withIndex()) {
            fileRow(list, file.name + if (file.dir) "/" else "", if (file.dir) null else formatSize(file.size),
                { (index == selectedIndex).toString() }, { !loading },
                { selectedIndex = if (selectedIndex == index) -1 else index; updateSelectionState() },
                if (file.dir) Runnable { selectedIndex = index; openSelected() } else null)
        }
        deleteButton?.let { mount(footer, it).getElement().setAttribute("class", "button danger") }; saveButton?.let { mount(footer, it) }
        status(if (loading) tr("dlc_manager.server.files_loading") else "")

    }
    override fun updatePage() {
        updateSelectionState(); pathField?.active = !loading
        if (pageRevision != 31 * entries.hashCode() + loading.hashCode()) refreshPage()
        super.updatePage()
    }

    override fun shouldCloseOnEsc() = !loading
}
