package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteService
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class DlcRemoteDownloadScreen(
    private val parent: Screen?,
    missing: List<DlcRemoteEntry>?,
    private val partMarkerKey: String = DlcRemoteMarker.PART_DLC,
    title: Component = Component.translatable("dlc_manager.remote.title"),
) : DlcRemotePageScreen(parent, title) {
    private val missing: List<DlcRemoteEntry> = missing ?: emptyList()
    private var previewQueue: List<DlcRemoteEntry> = emptyList()
    private var downloadTargets: List<DlcRemoteEntry> = emptyList()
    private val maxConcurrentDownloads = max(1, DlcConfig.getMaxConcurrentDownloads())
    private val selectedKeys: MutableSet<String> = HashSet(setOf(ALL_MARKER))
    private val progressByKey: MutableMap<String, Float> = ConcurrentHashMap()
    private val attemptByKey: MutableMap<String, Int> = ConcurrentHashMap()
    private val attemptCounter = AtomicInteger()
    private val downloadStartByKey: MutableMap<String, Long> = ConcurrentHashMap()
    private val switchRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile
    private var activeDownloadEntry: DlcRemoteEntry? = null

    private var downloadButton: Button? = null
    private var skipButton: Button? = null

    @Volatile
    private var downloading = false
    @Volatile
    private var cancelRequested = false
    @Volatile
    private var detecting = true
    @Volatile
    private var statusText: String? = null
    @Volatile
    private var errorText: String? = null
    @Volatile
    private var totalCount = 0
    @Volatile
    private var completedCount = 0
    @Volatile
    private var currentProgress = 0f

    private fun initControls() {
        if (missing.isEmpty()) {
            Minecraft.getInstance().setScreen(parent)
            return
        }
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val download = Button.builder(Component.translatable("dlc_manager.remote.download")) { handleDownloadButton() }
            .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build()
        download.active = false
        download.visible = false
        val skip = Button.builder(Component.translatable("dlc_manager.remote.skip")) { handleSkip() }
            .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
            .build()
        downloadButton = download
        skipButton = skip
        addRenderableWidget(download)
        addRenderableWidget(skip)
        detecting = true
        statusText = Component.translatable("dlc_manager.remote.preparing").string
        Thread({ buildPreviewQueue() }, "dlc-remote-preview").start()
    }

    private fun buildPreviewQueue() {
        previewQueue = DlcRemoteService.expandWithDependencies(missing)
        detecting = false
        statusText = Component.translatable("dlc_manager.remote.ready").string
        Minecraft.getInstance().execute {
            downloadButton?.visible = true
            downloadButton?.active = true
        }
    }

    private fun handleDownloadButton() {
        if (downloading) {
            requestCancel()
            return
        }
        startDownload()
    }

    private fun handleSkip() {
        writeSkipMarker()
        Minecraft.getInstance().setScreen(parent)
    }

    private fun requestCancel() {
        if (!downloading || cancelRequested) {
            return
        }
        cancelRequested = true
        statusText = Component.translatable("dlc_manager.remote.canceling").string
        downloadButton?.active = false
        skipButton?.active = false
    }

    private fun updateDownloadButtonLabel(isDownloading: Boolean) {
        downloadButton?.message = Component.translatable(
            if (isDownloading) "dlc_manager.remote.cancel" else "dlc_manager.remote.download",
        )
    }

    private fun startDownload() {
        if (downloading) {
            return
        }
        downloading = true
        cancelRequested = false
        errorText = null
        updateDownloadButtonLabel(true)
        downloadButton?.active = true
        skipButton?.active = false
        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val fullSelection = isAllSelected()
        val targets = resolveSelectionTargets(showList)
        downloadTargets = targets
        if (!fullSelection) {
            writeSkipMarker()
        }
        initProgressMap(targets)
        Thread({ downloadAll(targets, fullSelection) }, "dlc-remote-download").start()
    }

    private fun downloadAll(targets: List<DlcRemoteEntry>, clearSkipOnSuccess: Boolean) {
        totalCount = targets.size
        completedCount = 0
        val result = try {
            DlcRemoteService.downloadAll(
                targets,
                object : DlcRemoteService.BatchProgressListener {
                    override fun onEntryStart(entry: DlcRemoteEntry?, index: Int, total: Int) {
                        if (maxConcurrentDownloads <= 1 && entry != null) {
                            activeDownloadEntry = entry
                            statusText = Component.translatable(
                                "dlc_manager.remote.downloading",
                                entry.resolveDisplayName(),
                                completedCount + 1,
                                totalCount,
                            ).string
                        } else {
                            statusText = Component.translatable("dlc_manager.remote.downloading_multi").string
                        }
                    }

                    override fun onEntryAttemptStart(entry: DlcRemoteEntry?, attemptId: Int, index: Int, total: Int) {
                        val key = progressKey(entry)
                        attemptByKey[key] = attemptId
                        markDownloadStart(key)
                        if (maxConcurrentDownloads <= 1 && entry != null) {
                            activeDownloadEntry = entry
                        }
                    }

                    override fun onEntryProgress(entry: DlcRemoteEntry?, downloadedBytes: Long, totalBytes: Long) {
                        if (totalBytes > 0) {
                            val key = progressKey(entry)
                            val attemptId = attemptByKey[key] ?: 0
                            setEntryProgress(key, attemptId, min(1f, downloadedBytes.toFloat() / totalBytes.toFloat()))
                        }
                    }

                    override fun onEntrySuccess(entry: DlcRemoteEntry?, fileName: String?, completed: Int, total: Int) {
                        completedCount = completed
                        val key = progressKey(entry)
                        val attemptId = attemptByKey[key] ?: 0
                        setEntryProgress(key, attemptId, 1f)
                    }

                    override fun onEntryFailure(entry: DlcRemoteEntry?, error: Throwable?, completed: Int, total: Int) {
                        completedCount = completed
                        errorText = Component.translatable("dlc_manager.remote.error", error?.message ?: "").string
                    }

                    override fun onEntryStatus(entry: DlcRemoteEntry?, statusKey: String?, completed: Int, total: Int) {
                        statusText = if (statusKey == "selecting_source") {
                            Component.translatable("dlc_manager.remote.selecting_source").string
                        } else if (maxConcurrentDownloads <= 1 && entry != null) {
                            Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).string
                        } else {
                            Component.translatable("dlc_manager.remote.downloading_multi").string
                        }
                    }

                    override fun onEntryComplete(entry: DlcRemoteEntry?) {
                        val key = progressKey(entry)
                        downloadStartByKey.remove(key)
                        switchRequested.remove(key)
                        if (activeDownloadEntry === entry) {
                            activeDownloadEntry = null
                        }
                    }
                },
                { cancelRequested },
                object : DlcRemoteService.SourceSwitchChecker {
                    override fun isSwitchRequested(entry: DlcRemoteEntry?): Boolean {
                        return this@DlcRemoteDownloadScreen.isSwitchRequested(progressKey(entry))
                    }

                    override fun clearSwitchRequest(entry: DlcRemoteEntry?) {
                        this@DlcRemoteDownloadScreen.clearSwitchRequest(progressKey(entry))
                    }
                },
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            cancelRequested = true
            DlcRemoteService.BatchResult(
                targets.size,
                0,
                listOf(DlcRemoteService.EntryError(null, e)),
            )
        } finally {
            activeDownloadEntry = null
        }
        if (cancelRequested) {
            downloading = false
            statusText = Component.translatable("dlc_manager.remote.canceled").string
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
                skipButton?.active = true
            }
            return
        }
        if (result.errors.isNotEmpty()) {
            downloading = false
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
                skipButton?.active = true
            }
            return
        }
        errorText = null
        downloading = false
        if (clearSkipOnSuccess) {
            clearSkipMarker()
        }
        Minecraft.getInstance().execute {
            updateDownloadButtonLabel(false)
            Minecraft.getInstance().setScreen(parent)
        }
    }

    private fun initProgressMap(pending: List<DlcRemoteEntry>?) {
        progressByKey.clear()
        attemptByKey.clear()
        downloadStartByKey.clear()
        switchRequested.clear()
        activeDownloadEntry = null
        if (pending == null) {
            return
        }
        for (entry in pending) {
            progressByKey[progressKey(entry)] = 0f
        }
    }

    private fun setEntryProgress(key: String?, attemptId: Int, progress: Float) {
        if (key == null) {
            return
        }
        val active = attemptByKey[key]
        if (active != null && active != attemptId) {
            return
        }
        progressByKey[key] = progress
        if (maxConcurrentDownloads <= 1) {
            currentProgress = progress
        }
    }

    private fun getTotalProgress(): Float {
        if (totalCount <= 0) {
            return 0f
        }
        var sum = 0f
        for (value in progressByKey.values) {
            sum += value
        }
        return sum / max(1, totalCount)
    }

    private fun markDownloadStart(key: String?) {
        if (key != null) {
            downloadStartByKey[key] = System.currentTimeMillis()
        }
    }

    private fun isSwitchRequested(key: String?): Boolean {
        return key != null && switchRequested.contains(key)
    }

    private fun clearSwitchRequest(key: String?) {
        if (key != null) {
            switchRequested.remove(key)
        }
    }

    private fun hasMultipleSources(entry: DlcRemoteEntry?): Boolean {
        if (entry == null) {
            return false
        }
        var count = 0
        if (!entry.getCurseforgeProjectId().isNullOrBlank() || !entry.getCurseforgeFileId().isNullOrBlank()) {
            count += 1
        }
        if (!entry.getModrinthProjectId().isNullOrBlank() || !entry.getModrinthVersionId().isNullOrBlank()) {
            count += 1
        }
        val directCount = entry.getDirectUrls().size
        if (directCount > 0) {
            count += directCount
        }
        return count > 1
    }

    private fun progressKey(entry: DlcRemoteEntry?): String {
        if (entry == null) {
            return ""
        }
        val key = entry.getUniqueKey()
        val suffix = "#" + System.identityHashCode(entry)
        if (key.isBlank()) {
            return suffix
        }
        return key + suffix
    }

    private fun resolveSelectionTargets(showList: List<DlcRemoteEntry>?): List<DlcRemoteEntry> {
        if (showList.isNullOrEmpty()) {
            return emptyList()
        }
        if (isNoneSelected()) {
            return emptyList()
        }
        if (isAllSelected()) {
            return ArrayList(showList)
        }
        val targets: MutableList<DlcRemoteEntry> = ArrayList()
        for (entry in showList) {
            if (selectedKeys.contains(selectionKey(entry))) {
                targets.add(entry)
            }
        }
        return targets
    }

    private fun writeSkipMarker() {
        val dlcRoot = firstDlcRoot() ?: return
        DlcRemoteMarker.writePartMarker(dlcRoot, partMarkerKey)
    }

    private fun clearSkipMarker() {
        val dlcRoot = firstDlcRoot() ?: return
        DlcRemoteMarker.clearPartMarker(dlcRoot, partMarkerKey)
    }

    private fun firstDlcRoot(): Path? {
        val roots = DlcConfig.getDlcRoots()
        return if (roots.isEmpty()) null else roots[0]
    }

    private fun toggleSelection(entry: DlcRemoteEntry?, showList: List<DlcRemoteEntry>?) {
        val key = selectionKey(entry)
        if (key.isBlank()) {
            return
        }
        if (isAllSelected() && !showList.isNullOrEmpty()) {
            selectedKeys.clear()
            for (item in showList) {
                val itemKey = selectionKey(item)
                if (itemKey.isNotBlank() && itemKey != key) {
                    selectedKeys.add(itemKey)
                }
            }
            if (selectedKeys.isEmpty()) {
                selectedKeys.add(NONE_MARKER)
            }
            return
        }
        if (isNoneSelected()) {
            selectedKeys.clear()
        }
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key)
        } else {
            selectedKeys.add(key)
        }
        if (!showList.isNullOrEmpty() && selectedKeys.size == showList.size) {
            selectedKeys.clear()
            selectedKeys.add(ALL_MARKER)
            return
        }
        if (selectedKeys.isEmpty()) {
            selectedKeys.add(NONE_MARKER)
        }
    }

    private fun selectionKey(entry: DlcRemoteEntry?): String {
        if (entry == null) {
            return ""
        }
        val config = entry.getConfigPath()
        if (config != null) {
            return config.toString()
        }
        val identifier = entry.getDlcIdentifier()
        if (!identifier.isNullOrBlank()) {
            return identifier
        }
        return entry.getUniqueKey()
    }

    private fun toggleSelectAll() {
        if (isAllSelected()) {
            selectedKeys.clear()
            selectedKeys.add(NONE_MARKER)
            return
        }
        selectedKeys.clear()
        selectedKeys.add(ALL_MARKER)
    }

    private fun isAllSelected(): Boolean {
        return selectedKeys.contains(ALL_MARKER)
    }

    private fun isNoneSelected(): Boolean {
        return selectedKeys.contains(NONE_MARKER)
    }

    companion object {



        private const val NONE_MARKER = "__NONE__"
        private const val ALL_MARKER = "__ALL__"



    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = java.util.Objects.hash(if (previewQueue.isEmpty()) missing else previewQueue, downloading, detecting)
        sourceButtons.clear()
        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val list = remoteList({ isAllSelected() }, { !downloading && !detecting }, { toggleSelectAll() })
        for (entry in showList) {
            val line = remoteRow(list, entry.resolveDisplayName(), { isAllSelected() || selectedKeys.contains(selectionKey(entry)) },
                { !downloading && !detecting }, { toggleSelection(entry, showList) })
            remoteTip(line, entry.resolveDisplayName(), entry.getTips())
            val key = progressKey(entry)
            if (downloading && maxConcurrentDownloads > 1 && progressByKey.containsKey(key)) {
                val controls = node(line, "div", "remote-row-progress")
                sourceSwitch(controls) { entry }
                remoteProgress(controls) { (progressByKey[key] ?: 0f).toDouble() }
            }
        }
        val summary = remoteStatus()
        live(summary, { if (detecting) tr("dlc_manager.remote.detecting") else if (downloading) statusText else "" }, "remote-download-status")
        live(summary, { errorText }, "page-error")
        if (detecting) progress(summary) { -1.0 }
        if (downloading) {
            val meter = node(summary, "div", "remote-total-progress")
            if (maxConcurrentDownloads <= 1) sourceSwitch(meter) { activeDownloadEntry }
            remoteProgress(meter) { if (maxConcurrentDownloads <= 1) currentProgress.toDouble() else getTotalProgress().toDouble() }
        }
        skipButton?.let { mount(footer, it) }
        downloadButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }

    private val sourceButtons = mutableListOf<Runnable>()

    private fun sourceSwitch(parent: com.sighs.apricityui.init.Element, entry: () -> DlcRemoteEntry?) {
        val button = action(parent, tr("dlc_manager.remote.switch_source")) {
            entry()?.takeIf { canSwitchSource(it) }?.let { switchRequested.add(progressKey(it)) }
        }
        button.getElement().setAttribute("class", "button remote-source-switch")
        button.getElement().setAttribute("style", "display:none")
        var shown = false
        sourceButtons.add(Runnable {
            val visible = canSwitchSource(entry())
            if (shown != visible) {
                shown = visible
                button.getElement().setAttribute("style", if (visible) "" else "display:none")
            }
        })
    }

    private fun canSwitchSource(entry: DlcRemoteEntry?): Boolean {
        if (!downloading || entry == null || !hasMultipleSources(entry)) return false
        val key = progressKey(entry)
        val start = downloadStartByKey[key] ?: return false
        return System.currentTimeMillis() - start >= 20_000L && (progressByKey[key] ?: 0f) < 1f && !switchRequested.contains(key)
    }

    override fun updatePage() {
        if (pageRevision != java.util.Objects.hash(if (previewQueue.isEmpty()) missing else previewQueue, downloading, detecting)) refreshPage()
        updateRemoteSelections()
        sourceButtons.forEach { it.run() }
    }

    override fun shouldCloseOnEsc() = !downloading && !detecting
    override fun onClose() { if (shouldCloseOnEsc()) handleSkip() }
}
