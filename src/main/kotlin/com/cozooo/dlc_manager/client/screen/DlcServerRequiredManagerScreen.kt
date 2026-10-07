package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class DlcServerRequiredManagerScreen(
    private val parent: Screen?,
    private val serverEntry: DlcServerEntry?,
) : DlcRequiredPageScreen(parent, Component.translatable("dlc_manager.required.manage_title")) {
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

    private fun initControls() {
        rebuildAllWidgets()
        loadRequiredList()
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
            if (!item.hidden) {
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

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 31 * visibleItems.hashCode() + entryButtons.values.hashCode() + loading.hashCode()

        val resources = visibleItems.map { item -> ResourceRow(resolveDisplayName(item),
            { buildEntryLabel(item).string }, { resolveTip(item) }, entryButtons[item.id],
            { (progressById[item.id] ?: if (isApplied(item)) 1f else 0f).toDouble() }) }
        renderResources(resources, { errorText }, !loading, applyAllButton,
            { if (loading) -1.0 else batchState.progress.toDouble() })

    }
    override fun updatePage() {
        updateEntryButtons(); updateApplyAllAvailability()
        if (pageRevision != 31 * visibleItems.hashCode() + entryButtons.values.hashCode() + loading.hashCode()) refreshPage()
    }

    override fun shouldCloseOnEsc() = !downloading && !loading
}
