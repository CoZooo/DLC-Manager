package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredService
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class DlcRequiredDownloadScreen @JvmOverloads constructor(
    private val parent: Screen?,
    entries: List<DlcRequiredEntry>?,
    private val postUpdateResult: UpdateApplyResult? = null,
) : DlcPageScreen(DlcUiPage.REQUIRED_DOWNLOAD, parent, DlcTextCompat.translatable("dlc_manager.required.title")) {
    private val entries: List<DlcRequiredEntry> = entries ?: emptyList()
    private val displayList: MutableList<DlcRequiredEntry> = ArrayList()
    private val downloadQueue: MutableList<DlcRequiredEntry> = ArrayList()
    private val applyQueue: MutableList<DlcRequiredEntry> = ArrayList()
    private var updatePackageRequired = false
    private val maxConcurrentDownloads = max(1, DlcConfig.getMaxConcurrentDownloads())
    private val progressByKey: MutableMap<String, Float> = ConcurrentHashMap()
    private val attemptByKey: MutableMap<String, Int> = ConcurrentHashMap()
    private val attemptCounter = AtomicInteger()
    private val downloadStartByKey: MutableMap<String, Long> = ConcurrentHashMap()
    private val switchRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var activeDownloadEntry: DlcRequiredEntry? = null

    private var downloadButton: Button? = null

    @Volatile
    private var detecting = true
    @Volatile
    private var downloading = false
    @Volatile
    private var cancelRequested = false
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
    @Volatile
    private var downloadedAny = false

    private var scrollIndex = 0

    private fun initControls() {
        if (entries.isEmpty()) {
            if (postUpdateResult != null) {
                if (!tryShowPostUpdateDownloads(postUpdateResult)) {
                    Minecraft.getInstance().setScreen(createPostUpdateResultScreen(postUpdateResult))
                }
            } else if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                Minecraft.getInstance().setScreen(parent)
            }
            return
        }
        val buttonWidth = 140
        val buttonHeight = 20
        val y = height - 40
        val downloadX = width / 2 - buttonWidth / 2
        val download = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.required.download")) { handleDownloadButton() }
            .bounds(downloadX, y, buttonWidth, buttonHeight)
            .build()
        download.active = false
        download.visible = false
        downloadButton = download
        addRenderableWidget(download)
        detecting = true
        statusText = DlcTextCompat.translatable("dlc_manager.required.preparing").string
        Thread({ detectEntries() }, "dlc-required-detect").start()
    }

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    private fun detectEntries() {
        val applyCandidates: MutableList<DlcRequiredEntry> = ArrayList()
        for (entry in entries) {
            applyCandidates.add(entry)
        }
        val download = DlcRequiredService.collectMissingDownloads(applyCandidates)
        val needsApply = DlcRequiredService.collectNeedsApply(applyCandidates)
        val missingOrApply: MutableList<DlcRequiredEntry> = ArrayList()
        for (entry in applyCandidates) {
            if (download.contains(entry) || needsApply.contains(entry)) {
                missingOrApply.add(entry)
            }
        }
        displayList.clear()
        displayList.addAll(missingOrApply)
        downloadQueue.clear()
        downloadQueue.addAll(download)
        applyQueue.clear()
        applyQueue.addAll(applyCandidates)
        updatePackageRequired = hasUpdatePackageTargets(applyQueue)
        detecting = false
        statusText = DlcTextCompat.translatable("dlc_manager.required.ready").string
        scrollIndex = 0
        Minecraft.getInstance().execute {
            if (downloadQueue.isEmpty() && applyQueue.isEmpty()) {
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    Minecraft.getInstance().setScreen(parent)
                }
                return@execute
            }
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

    private fun requestCancel() {
        if (!downloading || cancelRequested) {
            return
        }
        cancelRequested = true
        statusText = DlcTextCompat.translatable("dlc_manager.required.canceling").string
        downloadButton?.active = false
    }

    private fun updateDownloadButtonLabel(isDownloading: Boolean) {
        downloadButton?.message = DlcTextCompat.translatable(
            if (isDownloading) "dlc_manager.required.cancel" else "dlc_manager.required.download",
        )
    }

    private fun startDownload() {
        if (downloading) {
            return
        }
        downloading = true
        cancelRequested = false
        errorText = null
        downloadedAny = false
        updateDownloadButtonLabel(true)
        downloadButton?.active = true
        initProgressMap(downloadQueue)
        if (downloadQueue.isEmpty()) {
            Thread({
                try {
                    Minecraft.getInstance().execute {
                        updateDownloadButtonLabel(false)
                        downloadButton?.active = false
                    }
                    applyAll()
                } catch (e: Exception) {
                    errorText = DlcTextCompat.translatable("dlc_manager.required.error", e.message).string
                    Minecraft.getInstance().execute {
                        updateDownloadButtonLabel(false)
                        downloadButton?.active = true
                    }
                } finally {
                    downloading = false
                }
            }, "dlc-required-apply").start()
            return
        }
        Thread({ downloadAll() }, "dlc-required-download").start()
    }

    private fun downloadAll() {
        val pending: MutableList<DlcRequiredEntry> = ArrayList(downloadQueue)
        totalCount = pending.size
        completedCount = 0
        val result = try {
            DlcRequiredService.downloadAll(
                pending,
                object : DlcRequiredService.BatchProgressListener {
                    override fun onEntryStart(entry: DlcRequiredEntry?, index: Int, total: Int) {
                        if (maxConcurrentDownloads <= 1 && entry != null) {
                            activeDownloadEntry = entry
                            statusText = DlcTextCompat.translatable(
                                "dlc_manager.required.downloading",
                                entry.resolveDisplayName(),
                                completedCount + 1,
                                totalCount,
                            ).string
                        } else {
                            statusText = DlcTextCompat.translatable("dlc_manager.required.downloading_multi").string
                        }
                    }

                    override fun onEntryAttemptStart(entry: DlcRequiredEntry?, attemptId: Int, index: Int, total: Int) {
                        val key = progressKey(entry)
                        attemptByKey[key] = attemptId
                        markDownloadStart(key)
                        if (maxConcurrentDownloads <= 1 && entry != null) {
                            activeDownloadEntry = entry
                        }
                    }

                    override fun onEntryProgress(entry: DlcRequiredEntry?, downloadedBytes: Long, totalBytes: Long) {
                        if (totalBytes > 0) {
                            val key = progressKey(entry)
                            val attemptId = attemptByKey[key] ?: 0
                            setEntryProgress(key, attemptId, min(1f, downloadedBytes.toFloat() / totalBytes.toFloat()))
                        }
                    }

                    override fun onEntryStatus(entry: DlcRequiredEntry?, statusKey: String?, completed: Int, total: Int) {
                        statusText = if (statusKey == "selecting_source") {
                            DlcTextCompat.translatable("dlc_manager.required.selecting_source").string
                        } else if (maxConcurrentDownloads <= 1 && entry != null) {
                            DlcTextCompat.translatable("dlc_manager.required.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).string
                        } else {
                            DlcTextCompat.translatable("dlc_manager.required.downloading_multi").string
                        }
                    }

                    override fun onEntrySuccess(entry: DlcRequiredEntry?, fileName: String?, completed: Int, total: Int) {
                        completedCount = completed
                        downloadedAny = true
                        val key = progressKey(entry)
                        val attemptId = attemptByKey[key] ?: 0
                        setEntryProgress(key, attemptId, 1f)
                    }

                    override fun onEntryFailure(entry: DlcRequiredEntry?, error: Throwable?, completed: Int, total: Int) {
                        completedCount = completed
                        errorText = DlcTextCompat.translatable("dlc_manager.required.error", error?.message ?: "").string
                    }

                    override fun onEntryComplete(entry: DlcRequiredEntry?) {
                        val key = progressKey(entry)
                        downloadStartByKey.remove(key)
                        switchRequested.remove(key)
                        if (activeDownloadEntry === entry) {
                            activeDownloadEntry = null
                        }
                    }
                },
                { cancelRequested },
                object : DlcRequiredService.SourceSwitchChecker {
                    override fun isSwitchRequested(entry: DlcRequiredEntry?): Boolean {
                        return this@DlcRequiredDownloadScreen.isSwitchRequested(progressKey(entry))
                    }

                    override fun clearSwitchRequest(entry: DlcRequiredEntry?) {
                        this@DlcRequiredDownloadScreen.clearSwitchRequest(progressKey(entry))
                    }
                },
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            cancelRequested = true
            DlcRequiredService.BatchResult(
                pending.size,
                0,
                listOf(DlcRequiredService.EntryError(null, e)),
            )
        } finally {
            activeDownloadEntry = null
        }
        if (cancelRequested) {
            downloading = false
            statusText = DlcTextCompat.translatable("dlc_manager.required.canceled").string
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
            }
            return
        }
        if (result.errors.isNotEmpty()) {
            downloading = false
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
            }
            return
        }
        for (entry in downloadQueue) {
            val key = progressKey(entry)
            val active = attemptByKey[key]
            val attemptId = active ?: attemptCounter.incrementAndGet()
            attemptByKey[key] = attemptId
            setEntryProgress(key, attemptId, 1f)
        }
        errorText = null
        try {
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = false
            }
            applyAll()
        } catch (e: Exception) {
            errorText = DlcTextCompat.translatable("dlc_manager.required.error", e.message).string
            downloading = false
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
            }
            return
        }
        downloading = false
    }

    private fun applyAll() {
        val minecraft = Minecraft.getInstance()
        var didApply = downloadedAny
        var updateApplied = false
        if (!ensureAllRequiredReady()) {
            throw IOException("download incomplete")
        }
        val baselines: MutableMap<String, Boolean> = HashMap()
        for (entry in applyQueue) {
            val id = entry.getIdentifier()
            if (id.isBlank()) {
                continue
            }
            baselines[id] = isApplied(entry)
            DlcRequiredRuntimeState.getBaseline(id, baselines[id] == true)
        }
        for (entry in applyQueue) {
            if (DlcRequiredService.installOne(entry)) {
                didApply = true
            }
        }
        if (DlcRequiredService.collectNeedsApply(applyQueue).isNotEmpty()) {
            throw IOException("required installation incomplete")
        }
        if (updatePackageRequired) {
            val updateCheck: UpdateCheckResult = DlcUpdateService.checkForUpdates(true)
            if (updateCheck.updates.isNotEmpty()) {
                val updateResult = DlcUpdateService.applyUpdates(updateCheck.updates, false)
                if (updateResult?.isMissing == true) {
                    Minecraft.getInstance().execute {
                        minecraft.setScreen(DlcUpdateMissingScreen(parent, updateCheck, updateResult.missingPaths))
                    }
                    return
                }
                if (updateResult?.isSuccess != true) {
                    Minecraft.getInstance().execute {
                        minecraft.setScreen(DlcUpdateResultScreen(parent, false, DlcMessageAdapter.toFailureComponent(updateResult)))
                    }
                    return
                }
                updateApplied = true
                if (tryShowPostUpdateDownloads(updateResult)) {
                    return
                }
            }
        }
        if (postUpdateResult != null) {
            if (tryShowPostUpdateDownloads(postUpdateResult)) {
                return
            }
            Minecraft.getInstance().execute { minecraft.setScreen(createPostUpdateResultScreen(postUpdateResult)) }
            return
        }
        if (!didApply && !updateApplied) {
            Minecraft.getInstance().execute {
                if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                    minecraft.setScreen(parent)
                }
            }
            return
        }
        Minecraft.getInstance().execute {
            minecraft.setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        if (accepted) {
                            minecraft.stop()
                        } else if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                            minecraft.setScreen(parent)
                        }
                    },
                    DlcTextCompat.translatable("dlc_manager.title"),
                    DlcTextCompat.translatable("dlc_manager.required.restart_prompt"),
                    DlcTextCompat.translatable("dlc_manager.restart_now"),
                    DlcTextCompat.translatable("dlc_manager.restart_later"),
                ),
            )
        }
    }

    private fun ensureAllRequiredReady(): Boolean {
        for (entry in applyQueue) {
            val fileName = resolveExistingFileName(entry)
            if (fileName.isNullOrBlank()) {
                return false
            }
            entry.setResolvedFileName(fileName)
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required)) {
                return false
            }
        }
        return true
    }

    private fun tryShowPostUpdateDownloads(result: UpdateApplyResult?): Boolean {
        if (result == null || !result.isSuccess) {
            return false
        }
        if (result.hasRequiredAdd()) {
            val missing = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(parent, missing, result))
                }
                return true
            }
        }
        if (result.hasRemoteAdd()) {
            val missing: List<DlcRemoteEntry> = DlcRemoteManager.collectMissing()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(createPostUpdateResultScreen(result), missing))
                }
                return true
            }
        }
        return false
    }

    private fun createPostUpdateResultScreen(result: UpdateApplyResult?): Screen {
        val message = if (result != null && result.isHelperScheduled) {
            DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
        } else {
            DlcTextCompat.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(parent, true, message)
    }

    private fun hasUpdatePackageTargets(entries: List<DlcRequiredEntry>?): Boolean {
        if (entries.isNullOrEmpty()) {
            return false
        }
        val dlcRoot = DlcPaths.dlcRoot()
        val updateRoot = dlcRoot.resolve("update").normalize()
        for (entry in entries) {
            for (target in entry.resolveAppliedTargets()) {
                val normalized = target.normalize()
                if (normalized == updateRoot || normalized.startsWith(updateRoot)) {
                    return true
                }
            }
        }
        return false
    }

    private fun resolveTargetPath(targetDir: Path?, fileName: String?): Path? {
        if (targetDir == null) {
            return null
        }
        val name = targetDir.fileName?.toString() ?: ""
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir
        }
        return targetDir.resolve(fileName)
    }

    private fun isApplied(entry: DlcRequiredEntry?): Boolean {
        if (entry == null) {
            return false
        }
        val targets = entry.resolveAppliedTargets()
        if (targets.isEmpty()) {
            return true
        }
        val fileName = resolveAppliedFileName(entry, targets)
        for (target in targets) {
            val name = target.fileName?.toString() ?: ""
            val targetFile = if (name.endsWith(".jar") || name.endsWith(".zip")) {
                target
            } else {
                resolveTargetPath(target, fileName)
            }
            if (targetFile == null || !Files.exists(targetFile)) {
                return false
            }
        }
        return true
    }

    private fun isConfigFileName(fileName: String?): Boolean {
        if (fileName == null) {
            return false
        }
        return DlcDcFormatUtil.isDcName(fileName)
    }

    private fun isTempFileName(fileName: String?): Boolean {
        if (fileName == null) {
            return false
        }
        return fileName.lowercase().endsWith(".part")
    }

    private fun isConfigFile(entry: DlcRequiredEntry?, path: Path?): Boolean {
        if (entry == null || path == null) {
            return false
        }
        val configPath = entry.getConfigPath()
        if (configPath != null && path == configPath) {
            return true
        }
        val name = path.fileName?.toString() ?: ""
        return isConfigFileName(name)
    }

    private fun resolveAppliedFileName(entry: DlcRequiredEntry?, targets: List<Path>?): String? {
        if (entry == null) {
            return null
        }
        val fileName = entry.getResolvedFileName()
        if (!fileName.isNullOrBlank()) {
            return fileName
        }
        if (targets != null) {
            for (target in targets) {
                val name = target.fileName?.toString() ?: ""
                if (name.endsWith(".jar") || name.endsWith(".zip")) {
                    return name
                }
            }
        }
        val base = entry.getBaseName()
        if (base.isBlank() || targets == null) {
            return null
        }
        for (target in targets) {
            if (Files.isDirectory(target)) {
                try {
                    Files.newDirectoryStream(target, "$base.*").use { stream ->
                        for (path in stream) {
                            val name = path.fileName?.toString() ?: ""
                            if (!DlcDcFormatUtil.isDcName(name)) {
                                return name
                            }
                        }
                    }
                } catch (_: IOException) {
                }
            }
        }
        return null
    }

    private fun resolveExistingFileName(entry: DlcRequiredEntry): String? {
        return DlcRequiredService.resolveExistingFileName(entry)
    }

    private fun markDownloadStart(key: String?) {
        if (key == null) {
            return
        }
        downloadStartByKey[key] = System.currentTimeMillis()
    }

    private fun isSwitchRequested(key: String?): Boolean {
        return key != null && switchRequested.contains(key)
    }

    private fun clearSwitchRequest(key: String?) {
        if (key != null) {
            switchRequested.remove(key)
        }
    }

    private fun hasMultipleSources(entry: DlcRequiredEntry?): Boolean {
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

    private fun initProgressMap(pending: List<DlcRequiredEntry>?) {
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

    private fun progressKey(entry: DlcRequiredEntry?): String {
        if (entry == null) {
            return ""
        }
        val id = entry.getIdentifier()
        val suffix = "#" + System.identityHashCode(entry)
        if (id.isNotBlank()) {
            return id + suffix
        }
        return suffix
    }

    companion object {




    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 31 * displayList.hashCode() + downloading.hashCode()
        val list = section(null, tr("dlc_manager.required.desc"))
        for (entry in displayList) { val line = row(list, entry.resolveDisplayName(), null); val controls = node(line, "div", "row-controls")
            progress(controls) { (progressByKey[progressKey(entry)] ?: 0f).toDouble() }
            action(controls, tr("dlc_manager.remote.switch_source")) { switchRequested.add(progressKey(entry)) }.setEnabled(hasMultipleSources(entry))
        }
        live(content, { statusText }, "page-description"); live(content, { errorText }, "page-error"); progress(content) { if (detecting) -1.0 else if (downloading) getTotalProgress().toDouble() else 0.0 }; downloadButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }

    }
    override fun updatePage() {

        if (pageRevision != 31 * displayList.hashCode() + downloading.hashCode()) refreshPage()
    }

    override fun showHeaderBackButton() = false
    override fun onClose() {}
}
