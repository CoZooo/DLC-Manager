package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class DlcServerRemoteDownloadScreen(
    private val parent: Screen?,
    private val serverEntry: DlcServerEntry?,
) : DlcRemotePageScreen(parent, Component.translatable("dlc_manager.remote.title")) {
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
    private var lastTotalProgress = 0f

    private fun initControls() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val download = Button.builder(Component.translatable("dlc_manager.remote.download")) { handleDownload() }
            .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build()
        download.active = false
        val back = Button.builder(Component.translatable("dlc_manager.remote.skip")) { Minecraft.getInstance().setScreen(parent) }
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
        errorText = null
        downloadButton?.active = false
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
        if (downloading || loading) {
            return
        }
        downloading = true
        errorText = null
        downloadButton?.active = false
        CompletableFuture
            .supplyAsync { startDownload() }
            .whenComplete { started, _ ->
                if (started == null) {
                    Minecraft.getInstance().execute { recoverDownload() }
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
            var failedPolls = 0
            while (downloading) {
                val next = fetchStatus()
                if (next != null) {
                    failedPolls = 0
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
                                is DlcServerScreen -> parent.refreshServerInfo()
                            }
                            Minecraft.getInstance().setScreen(parent)
                        }
                        break
                    }
                }
                if (next == null && ++failedPolls >= 3) {
                    Minecraft.getInstance().execute { recoverDownload() }
                    break
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

    private fun resolveDisplayName(item: DlcServerRemoteItem?): String {
        if (item == null) {
            return ""
        }
        if (item.displayNames.isNotEmpty()) {
            val lang = DlcDcConfigIO.normalizeLangKey(Minecraft.getInstance().options.languageCode)
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



        private const val ALL_MARKER = "__ALL__"
    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 31 * items.hashCode() + downloading.hashCode() + loading.hashCode()
        val list = remoteList({ isAllSelected() }, { !downloading && !loading }, { toggleSelectAll() })
        for (item in items) {
            val line = remoteRow(list, resolveDisplayName(item), { isAllSelected() || selectedKeys.contains(item.key) },
                { !downloading && !loading }, { toggleSelection(item) })
            remoteTip(line, resolveDisplayName(item), item.tips)
            if (downloading && progressByKey.containsKey(item.key)) remoteProgress(line) { (progressByKey[item.key] ?: 0f).toDouble() }
        }
        val summary = remoteStatus()
        if (!loading && errorText != null && items.isEmpty()) action(summary, tr("dlc_manager.server.files_refresh")) { loadMissingList() }
        live(summary, { if (loading) tr("dlc_manager.remote.detecting") else if (downloading) tr("dlc_manager.remote.downloading_multi") else "" }, "remote-download-status")
        live(summary, { errorText }, "page-error")
        if (loading) progress(summary) { -1.0 }
        if (downloading) remoteProgress(node(summary, "div", "remote-total-progress")) { resolveTotalProgress(status).toDouble() }
        backButton?.let { mount(footer, it) }
        downloadButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
    override fun updatePage() {
        backButton?.active = !downloading && !loading
        if (pageRevision != 31 * items.hashCode() + downloading.hashCode() + loading.hashCode()) refreshPage()
        updateRemoteSelections()
    }

    private fun recoverDownload() {
        downloading = false
        errorText = tr("dlc_manager.server.remote_missing_failed")
        downloadButton?.active = items.isNotEmpty()
    }

    override fun shouldCloseOnEsc() = !downloading && !loading
}
