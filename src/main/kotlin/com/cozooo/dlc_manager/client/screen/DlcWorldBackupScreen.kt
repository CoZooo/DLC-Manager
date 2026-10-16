package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateWorldBackup
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcTextCompat
import com.sighs.apricityui.init.Element
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

class DlcWorldBackupScreen private constructor(
    private val parent: Screen,
    private val packages: List<DlcUpdatePackage>?,
    private val backupRoot: Path?,
    private val proceed: Runnable?,
) : DlcPageScreen(DlcUiPage.PAGE, parent, DlcTextCompat.translatable("dlc_manager.update.world_title")) {
    constructor(parent: Screen, packages: List<DlcUpdatePackage>, proceed: Runnable) : this(parent, packages, null, proceed)
    constructor(parent: Screen, backupRoot: Path) : this(parent, null, backupRoot, null)
    private val selected = linkedSetOf<String>()
    private val icons = DlcUiSaveIcons()
    private var worlds: List<DlcUpdateWorldBackup.World> = emptyList()
    private var snapshots: List<DlcUpdateWorldBackup.Snapshot> = emptyList()
    private var returnTargets: Map<String, String> = emptyMap()
    private var started = false
    private var busy = true
    private var workingKey = if (packages != null) "dlc_manager.update.world_reading_saves" else "dlc_manager.update.world_reading_backups"
    private var restoreAfter = false
    private var canRestoreAfter = false
    private var answered = false
    private var error = ""
    private var notice = ""

    override fun showHeaderBackButton(): Boolean = packages == null

    override fun buildPage() {
        if (!started) { started = true; load() }
        if (busy) { text(content, tr(workingKey), "page-description"); return }
        if (error.isNotBlank()) text(content, error, "page-warning")
        if (notice.isNotBlank()) text(content, notice, "page-description")
        if (packages != null) {
            text(content, tr("dlc_manager.update.world_choose"), "page-description")
            if (worlds.isEmpty()) text(content, tr("dlc_manager.update.world_empty"), "empty-state")
            for (world in worlds) {
                val row = toggle(content, "", selected.contains(world.name)) { value ->
                    if (value) selected.add(world.name) else selected.remove(world.name)
                    refreshPage()
                }.getElement()
                row.setAttribute("class", "button choice save-choice" + if (selected.contains(world.name)) " selected" else "")
                row.setAttribute("aria-label", world.name + " · " + date(world.modified))
                saveIcon(row, DlcPaths.gameDir().resolve("saves"), Path.of(world.name))
                val copy = node(row, "div", "save-choice-copy")
                text(copy, world.name, "row-title")
                text(copy, date(world.modified), "row-description")
            }
            if (canRestoreAfter) toggle(content, tr("dlc_manager.update.world_reapply") + " · " + tr(if (restoreAfter) "dlc_manager.confirm_yes" else "dlc_manager.confirm_no"), restoreAfter) { value -> restoreAfter = value; refreshPage() }
            val cancel = action(footer, tr("dlc_manager.update.world_cancel"), ::onClose)
            cancel.getElement().setAttribute("class", "button danger")
            if (error.isBlank()) primary(tr("dlc_manager.update.world_continue")) {
                if (!answered) {
                    answered = true
                    for (pkg in packages) {
                        pkg.setBackupWorlds(selected.toList())
                        pkg.setRestoreWorldsAfterReapply(restoreAfter)
                    }
                    Minecraft.getInstance().setScreen(parent)
                    proceed!!.run()
                }
            }
        } else {
            if (snapshots.isEmpty()) text(content, tr("dlc_manager.update.world_no_backups"), "empty-state")
            for (snapshot in snapshots) {
                text(content, date(snapshot.timestamp) + " · " + tr("dlc_manager.update.world_kind_" + snapshot.kind), "section-title")
                for (name in snapshot.worlds) {
                    val row = node(content, "div", "update-row")
                    saveIcon(row, backupRoot!!.resolve("worlds/data"), Path.of(snapshot.id, name))
                    text(row, name, "update-row-copy")
                    val controls = node(row, "div", "row-controls")
                    action(controls, tr("dlc_manager.update.world_delete")) { confirmDelete(snapshot.id, name) }
                        .getElement().setAttribute("class", "button danger")
                    action(controls, tr("dlc_manager.update.world_restore")) { confirmRestore(snapshot.id, name) }
                }
            }
            if (snapshots.isNotEmpty() || error.isNotBlank()) action(toolbar, tr("dlc_manager.update.world_delete_all")) { confirmDelete(null, null) }
                .getElement().setAttribute("class", "button danger")
        }
    }

    private fun saveIcon(row: Element, root: Path, relative: Path) {
        val icon = node(row, "texture", "save-icon")
        icon.setAttribute("aria-hidden", "true")
        icons.bind(icon, root, relative)
    }

    override fun removed() {
        icons.clear()
        super.removed()
    }

    private fun load() {
        busy = true
        workingKey = if (packages != null) "dlc_manager.update.world_reading_saves" else "dlc_manager.update.world_reading_backups"
        Thread({
            try {
                if (packages != null) {
                    val loaded = DlcUpdateWorldBackup.listWorlds(DlcPaths.gameDir())
                    val available = packages.any { pkg ->
                        val target = DlcUpdatePackGuard.resolveTargetVersion(listOf(pkg))
                        val root = DlcUpdateService.resolveMetaRootByPair(DlcPaths.dlcRoot(), target.base, target.dlc)!!
                        DlcUpdateWorldBackup.pointer(root, "after") != null
                    }
                    Minecraft.getInstance().execute {
                        worlds = loaded
                        canRestoreAfter = available
                        busy = false
                        refreshPage()
                    }
                } else {
                    val loaded = DlcUpdateWorldBackup.list(backupRoot!!)
                    val targets = DlcUpdateWorldBackup.returnTargets(backupRoot)
                    Minecraft.getInstance().execute { snapshots = loaded; returnTargets = targets; busy = false; refreshPage() }
                }
            } catch (exception: Exception) {
                Minecraft.getInstance().execute { error = tr("dlc_manager.update.world_error", exception.message.toString()); busy = false; refreshPage() }
            }
        }, "dlc-world-backup-list").start()
    }

    private fun confirmRestore(id: String, name: String) {
        if (id == returnTargets[name]) { confirmReturn(name); return }
        confirmOperation(DlcTextCompat.translatable("dlc_manager.update.world_confirm_restore", name),
            "dlc_manager.update.world_restoring",
            { DlcUpdateWorldBackup.restore(DlcPaths.gameDir(), backupRoot!!, id, listOf(name)) },
            "dlc_manager.update.world_restored", name)
    }

    private fun confirmReturn(name: String) {
        confirmOperation(DlcTextCompat.translatable("dlc_manager.update.world_confirm_return", name),
            "dlc_manager.update.world_returning",
            { DlcUpdateWorldBackup.returnBeforeRestore(DlcPaths.gameDir(), backupRoot!!, name) },
            "dlc_manager.update.world_returned", name)
    }

    private fun confirmDelete(id: String?, name: String?) {
        val label = if (name == null) "" else name + " · " + (snapshots.firstOrNull { it.id == id }?.let { date(it.timestamp) } ?: "")
        confirmOperation(DlcTextCompat.translatable(if (id == null) "dlc_manager.update.world_confirm_delete_all" else "dlc_manager.update.world_confirm_delete", label),
            if (id == null) "dlc_manager.update.world_deleting_all" else "dlc_manager.update.world_deleting",
            { if (id == null) DlcUpdateWorldBackup.deleteAll(backupRoot!!) else DlcUpdateWorldBackup.delete(backupRoot!!, id, listOf(name!!)) },
            if (id == null) "dlc_manager.update.world_deleted_all" else "dlc_manager.update.world_deleted", label)
    }

    private fun confirmOperation(message: net.minecraft.network.chat.Component, workingKey: String, operation: () -> Unit, successKey: String, name: String) {
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) {
                busy = true
                this.workingKey = workingKey
                refreshPage()
                Thread({
                    val failure = try { operation(); null } catch (exception: Exception) { exception.message ?: exception.toString() }
                    Minecraft.getInstance().execute {
                        error = if (failure == null) "" else tr("dlc_manager.update.world_error", failure)
                        notice = if (failure == null) tr(successKey, name) else ""
                        started = true
                        load()
                        refreshPage()
                    }
                }, "dlc-world-backup-operation").start()
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
    }

    private fun date(time: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

    override fun onClose() { if (!busy) Minecraft.getInstance().setScreen(parent) }
}
