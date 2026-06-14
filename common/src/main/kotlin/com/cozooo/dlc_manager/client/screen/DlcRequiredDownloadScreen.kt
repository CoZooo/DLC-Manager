package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState
import com.cozooo.dlc_manager.client.text.DlcTextFormatter
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DcFormatUtil
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcRequiredDownloadScreen @JvmOverloads constructor(
    private val parent: Screen?,
    entries: List<DlcRequiredEntry>?,
    private val postUpdateResult: UpdateApplyResult? = null,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.required.title")) {
    private val entries: List<DlcRequiredEntry> = entries ?: emptyList()
    private val displayList: MutableList<DlcRequiredEntry> = ArrayList()
    private val downloadQueue: MutableList<DlcRequiredEntry> = ArrayList()
    private val applyQueue: MutableList<DlcRequiredEntry> = ArrayList()
    private val forceRequired = DlcRequiredManager.hasForceMarker()
    private val forceRestartRequired = DlcRequiredManager.hasForceRestartMarker()
    private var updatePackageRequired = false
    private val maxConcurrentDownloads = max(1, DlcPathConfig.getMaxConcurrentDownloads())
    private val maxRetry = max(1, DlcPathConfig.getMaxRetryCount())
    private val connectTimeoutMs = max(1, DlcPathConfig.getConnectTimeoutMs())
    private val taskTimeoutMs = max(1, DlcPathConfig.getTaskTimeoutMs())
    private val progressByKey: MutableMap<String, Float> = ConcurrentHashMap()
    private val attemptByKey: MutableMap<String, Int> = ConcurrentHashMap()
    private val attemptCounter = AtomicInteger()
    private val downloadStartByKey: MutableMap<String, Long> = ConcurrentHashMap()
    private val switchRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var activeDownloadEntry: DlcRequiredEntry? = null

    private var downloadButton: Button? = null
    private var skipButton: Button? = null

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
    private var noticeLines: List<String> = emptyList()

    override fun init() {
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
        val downloadX = if (forceRequired) width / 2 - buttonWidth / 2 else width / 2 - buttonWidth - 6
        val download = Button.builder(Component.translatable("dlc_manager.required.download")) { handleDownloadButton() }
            .bounds(downloadX, y, buttonWidth, buttonHeight)
            .build()
        download.active = false
        download.visible = false
        val skip = Button.builder(Component.translatable("dlc_manager.required.skip")) {
            DlcRequiredManager.markPrompted()
            DlcRequiredManager.markSkipped()
            if (postUpdateResult != null) {
                if (!tryShowPostUpdateDownloads(postUpdateResult)) {
                    Minecraft.getInstance().setScreen(createPostUpdateResultScreen(postUpdateResult))
                }
            } else if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                Minecraft.getInstance().setScreen(parent)
            }
        }.bounds(width / 2 + 6, y, buttonWidth, buttonHeight).build()
        skip.visible = !forceRequired
        skip.active = !forceRequired
        downloadButton = download
        skipButton = skip
        addRenderableWidget(download)
        addRenderableWidget(skip)
        noticeLines = splitLines(DlcRequiredNotice.load().resolveDownloadDesc())
        detecting = true
        statusText = Component.translatable("dlc_manager.required.preparing").string
        Thread({ detectEntries() }, "dlc-required-detect").start()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        for (line in getDescLines()) {
            guiGraphics.drawCenteredString(font, line, centerX, y, 0xC8C8C8)
            y += font.lineHeight + 2
        }
        if (forceRequired) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.required.force_notice"), centerX, y, 0xFFAA55)
            y += font.lineHeight + 2
        }
        y += 6
        if (detecting && !downloading) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.required.detecting"), centerX, y, 0xE6E6E6)
            y += 12
        }

        val listTop = y
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = displayList.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        val visibleEnd = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until visibleEnd) {
            val entry = displayList[i]
            val name = entry.resolveDisplayName()
            guiGraphics.drawString(font, DlcTextFormatter.parse("- $name"), 24, y, 0xE6E6E6, false)
            if (!downloading || maxConcurrentDownloads > 1) {
                val key = progressKey(entry)
                var progress = if (downloading) progressByKey[key] else null
                if (progress == null) {
                    progress = getCompletedProgress(entry)
                }
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
                    val barY = y + 2
                    drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress)
                    if (shouldShowSwitch(key, entry, progress)) {
                        val switchRight = barLeft - SWITCH_BUTTON_PAD
                        val switchLeft = switchRight - SWITCH_BUTTON_SIZE
                        val switchTop = barY - 1
                        drawSwitchButton(guiGraphics, switchLeft, switchTop)
                    }
                    guiGraphics.drawString(font, progressText, percentRight - textWidth, y, 0xC8C8C8, false)
                }
            }
            y += LINE_HEIGHT
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)

        if (downloading) {
            y = height - 70
            if (maxConcurrentDownloads <= 1) {
                val status = statusText ?: ""
                guiGraphics.drawCenteredString(font, DlcTextFormatter.parse(status), centerX, y, 0xE6E6E6)
                y += 12
                val activeEntry = activeDownloadEntry
                val activeKey = if (activeEntry == null) null else progressKey(activeEntry)
                if (activeKey != null && shouldShowSwitch(activeKey, activeEntry, currentProgress)) {
                    val barLeft = centerX - 120
                    val switchLeft = barLeft - SWITCH_BUTTON_PAD - SWITCH_BUTTON_SIZE
                    val switchTop = y - 1
                    drawSwitchButton(guiGraphics, switchLeft, switchTop)
                }
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, currentProgress)
                val percent = (max(0f, min(1f, currentProgress)) * 100f).roundToInt()
                guiGraphics.drawCenteredString(font, "$percent%", centerX, y + 10, 0xC8C8C8)
            } else {
                val status = Component.translatable("dlc_manager.required.downloading_multi").string
                guiGraphics.drawCenteredString(font, status, centerX, y, 0xE6E6E6)
                y += 12
                val totalProgress = getTotalProgress()
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, totalProgress)
                val percent = (max(0f, min(1f, totalProgress)) * 100f).roundToInt()
                guiGraphics.drawCenteredString(font, "$percent%", centerX, y + 10, 0xC8C8C8)
            }
        } else if (!errorText.isNullOrBlank()) {
            y = height - 70
            guiGraphics.drawCenteredString(font, DlcTextFormatter.parse(errorText), centerX, y, 0xFF6B6B)
        }

        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(deltaY: Double): Boolean {
        val listTop = getListTop()
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = displayList.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (deltaY > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (downloading && handleSwitchClick(mouseX, mouseY)) {
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !forceRequired
    }

    private fun detectEntries() {
        val missingOrApply: MutableList<DlcRequiredEntry> = ArrayList()
        val download: MutableList<DlcRequiredEntry> = ArrayList()
        val applyCandidates: MutableList<DlcRequiredEntry> = ArrayList()
        for (entry in entries) {
            if (!entry.isInList()) {
                continue
            }
            applyCandidates.add(entry)
            var fileName = entry.getRequiredFileName()
            if (isConfigFileName(fileName)) {
                fileName = null
            }
            if (fileName.isNullOrBlank()) {
                fileName = resolveFileName(entry)
            }
            if (isConfigFileName(fileName)) {
                fileName = null
            }
            if (isTempFileName(fileName)) {
                fileName = null
            }
            entry.setResolvedFileName(fileName)
            var hasFile = fileName != null &&
                entry.resolveRequiredPath(fileName) != null &&
                Files.exists(entry.resolveRequiredPath(fileName)) &&
                !isTempFileName(fileName)
            if (!hasFile && fileName == null) {
                val base = entry.getBaseName()
                val requiredDir = entry.getRequiredDir()
                if (base.isNotBlank() && requiredDir != null) {
                    try {
                        Files.newDirectoryStream(requiredDir, "$base.*").use { stream ->
                            for (file in stream) {
                                if (isConfigFile(entry, file)) {
                                    continue
                                }
                                fileName = file.fileName.toString()
                                if (isTempFileName(fileName)) {
                                    continue
                                }
                                entry.setResolvedFileName(fileName)
                                hasFile = true
                                break
                            }
                        }
                    } catch (_: IOException) {
                    }
                }
            }
            if (!hasFile) {
                download.add(entry)
                missingOrApply.add(entry)
            } else {
                if (DlcRequiredManager.entryNeedsApply(entry)) {
                    missingOrApply.add(entry)
                }
                if (!fileName.isNullOrBlank()) {
                    DlcRequiredManager.ensureFileNameKey(entry, fileName)
                }
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
        statusText = Component.translatable("dlc_manager.required.ready").string
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

    private fun getListTop(): Int {
        var y = 20 + 18
        y += getDescLines().size * (font.lineHeight + 2)
        y += 6
        if (detecting && !downloading) {
            y += 12
        }
        return y
    }

    private fun getDescLines(): List<String> {
        val lines: MutableList<String> = ArrayList()
        lines.addAll(splitLines(Component.translatable("dlc_manager.required.desc").string))
        if (noticeLines.isNotEmpty()) {
            lines.addAll(noticeLines)
        }
        return lines
    }

    private fun splitLines(text: String?): List<String> {
        if (text.isNullOrBlank()) {
            return ArrayList()
        }
        if (text.contains("\n")) {
            val lines: MutableList<String> = ArrayList()
            for (part in text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }) {
                if (part.isNotEmpty()) {
                    lines.add(part)
                }
            }
            if (lines.isNotEmpty()) {
                return lines
            }
        }
        return ArrayList(listOf(text))
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
        statusText = Component.translatable("dlc_manager.required.canceling").string
        downloadButton?.active = false
        if (!forceRequired) {
            skipButton?.active = false
        }
    }

    private fun updateDownloadButtonLabel(isDownloading: Boolean) {
        downloadButton?.message = Component.translatable(
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
        if (!forceRequired) {
            skipButton?.active = false
        }
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
                    errorText = Component.translatable("dlc_manager.required.error", e.message).string
                    Minecraft.getInstance().execute {
                        updateDownloadButtonLabel(false)
                        downloadButton?.active = true
                        if (!forceRequired) {
                            skipButton?.active = true
                        }
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
        var failed = runDownloadBatch(pending)
        if (cancelRequested) {
            downloading = false
            statusText = Component.translatable("dlc_manager.required.canceled").string
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
                if (!forceRequired) {
                    skipButton?.active = true
                }
            }
            return
        }
        if (failed.isNotEmpty()) {
            failed = runDownloadBatch(failed)
        }
        if (cancelRequested) {
            downloading = false
            statusText = Component.translatable("dlc_manager.required.canceled").string
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
                if (!forceRequired) {
                    skipButton?.active = true
                }
            }
            return
        }
        if (failed.isNotEmpty()) {
            downloading = false
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
                if (!forceRequired) {
                    skipButton?.active = true
                }
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
            errorText = Component.translatable("dlc_manager.required.error", e.message).string
            downloading = false
            Minecraft.getInstance().execute {
                updateDownloadButtonLabel(false)
                downloadButton?.active = true
                if (!forceRequired) {
                    skipButton?.active = true
                }
            }
            return
        }
        downloading = false
    }

    private fun runDownloadBatch(pending: List<DlcRequiredEntry>): List<DlcRequiredEntry> {
        val failed: MutableList<DlcRequiredEntry> = ArrayList()
        if (pending.isEmpty()) {
            return failed
        }
        if (cancelRequested) {
            return failed
        }
        if (maxConcurrentDownloads <= 1) {
            for (entry in pending) {
                if (cancelRequested) {
                    break
                }
                statusText = Component.translatable(
                    "dlc_manager.required.downloading",
                    entry.resolveDisplayName(),
                    completedCount + 1,
                    totalCount,
                ).string
                try {
                    downloadEntry(entry)
                    completedCount++
                    if (cancelRequested) {
                        break
                    }
                } catch (e: Exception) {
                    if (e is DownloadCanceledException) {
                        cancelRequested = true
                        break
                    }
                    failed.add(entry)
                    errorText = Component.translatable("dlc_manager.required.error", e.message).string
                }
            }
            return failed
        }
        val executor = Executors.newFixedThreadPool(maxConcurrentDownloads)
        val completion = ExecutorCompletionService<DownloadResult>(executor)
        for (entry in pending) {
            completion.submit { downloadEntryWithResult(entry) }
        }
        for (i in pending.indices) {
            try {
                val future: Future<DownloadResult> = completion.take()
                val result = future.get()
                if (result.canceled) {
                    cancelRequested = true
                } else if (result.success) {
                    completedCount++
                } else {
                    failed.add(result.entry)
                    if (result.error != null) {
                        errorText = Component.translatable("dlc_manager.required.error", result.error.message).string
                    }
                }
            } catch (e: Exception) {
                if (e is DownloadCanceledException) {
                    cancelRequested = true
                } else {
                    errorText = Component.translatable("dlc_manager.required.error", e.message).string
                    failed.add(pending[i])
                }
            }
            if (cancelRequested) {
                break
            }
        }
        executor.shutdownNow()
        return failed
    }

    private fun downloadEntryWithResult(entry: DlcRequiredEntry): DownloadResult {
        return try {
            downloadEntry(entry)
            DownloadResult(entry, true, false, null)
        } catch (_: DownloadCanceledException) {
            DownloadResult(entry, false, true, null)
        } catch (e: Exception) {
            DownloadResult(entry, false, false, e)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadEntry(entry: DlcRequiredEntry?) {
        if (entry == null) {
            return
        }
        checkCanceled()
        currentProgress = 0f
        val progressKey = progressKey(entry)
        if (maxConcurrentDownloads <= 1) {
            activeDownloadEntry = entry
        }
        try {
            val resolver = DlcDownloadCandidate.resolver(entry)
            var lastError: IOException? = null
            var attemptId = 0
            var canceled = false
            while (true) {
                val candidates = resolver.next()
                if (candidates.isEmpty()) {
                    break
                }
                for (candidate in candidates) {
                    for (attempt in 1..maxRetry) {
                        try {
                            checkCanceled()
                            attemptId = attemptCounter.incrementAndGet()
                            attemptByKey[progressKey] = attemptId
                            markDownloadStart(progressKey)
                            var fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName())
                            if (fileName == null && candidate.fileNameOverride != null) {
                                fileName = DlcDownloadUtil.sanitizeFileName(candidate.fileNameOverride)
                            }
                            if (fileName == null) {
                                fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(candidate.url))
                            }
                            var downloadInfo: DownloadInfo? = null
                            val shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName)
                            if (shouldProbe) {
                                downloadInfo = openDownload(candidate.url, 0)
                                if (fileName == null) {
                                    fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName)
                                }
                                closeQuietly(downloadInfo.connection)
                            }
                            if (fileName == null) {
                                fileName = DlcDownloadService.suggestFileName(candidate.url)
                            }
                            if (fileName == null) {
                                fileName = DlcDownloadUtil.stripQuery(entry.getBaseName())
                            }
                            val target = entry.resolveRequiredPath(fileName) ?: throw IOException("invalid target path")
                            Files.createDirectories(target.parent)
                            if (Files.exists(target)) {
                                entry.setResolvedFileName(fileName)
                                setEntryProgress(progressKey, attemptId, 1f)
                                return
                            }
                            val currentAttemptId = attemptId
                            val downloaded = if (shouldProbe) {
                                DlcDownloadService.download(
                                    candidate.url,
                                    target.parent,
                                    fileName,
                                    { done, total ->
                                        if (total > 0) {
                                            setEntryProgress(progressKey, currentAttemptId, min(1f, done.toFloat() / total.toFloat()))
                                        }
                                    },
                                    { cancelRequested || isSwitchRequested(progressKey) },
                                )
                            } else {
                                DlcDownloadService.downloadKnown(
                                    candidate.url,
                                    target.parent,
                                    fileName,
                                    candidate.expectedSize,
                                    { done, total ->
                                        if (total > 0) {
                                            setEntryProgress(progressKey, currentAttemptId, min(1f, done.toFloat() / total.toFloat()))
                                        }
                                    },
                                    { cancelRequested || isSwitchRequested(progressKey) },
                                )
                            }
                            val resolved = if (downloaded == target) {
                                downloaded
                            } else {
                                Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                            verifyDownloadedFile(resolved, candidate, downloadInfo)
                            downloadedAny = true
                            setEntryProgress(progressKey, currentAttemptId, 1f)
                            entry.setResolvedFileName(fileName)
                            DlcRequiredManager.updateFileNameKey(entry, fileName)
                            return
                        } catch (ex: DlcDownloadService.DownloadCanceledException) {
                            if (isSwitchRequested(progressKey)) {
                                lastError = DownloadSwitchException("switch")
                                clearSwitchRequest(progressKey)
                                if (!canceled) {
                                    setEntryProgress(progressKey, attemptId, 0f)
                                }
                                break
                            }
                            canceled = true
                            throw DownloadCanceledException("canceled")
                        } catch (ex: DownloadCanceledException) {
                            canceled = true
                            throw ex
                        } catch (ex: IOException) {
                            if (isSwitchException(ex)) {
                                lastError = ex
                                clearSwitchRequest(progressKey)
                                if (!canceled) {
                                    setEntryProgress(progressKey, attemptId, 0f)
                                }
                                break
                            }
                            if (ex.cause is DownloadCanceledException) {
                                canceled = true
                                throw ex.cause as DownloadCanceledException
                            }
                            lastError = ex
                            if (ex is DlcDownloadService.SegmentDownloadException) {
                                if (!canceled) {
                                    setEntryProgress(progressKey, attemptId, 0f)
                                }
                                break
                            }
                            if (attempt >= maxRetry && !canceled) {
                                setEntryProgress(progressKey, attemptId, 0f)
                            }
                        }
                    }
                }
            }
            if (cancelRequested || canceled) {
                throw DownloadCanceledException("canceled")
            }
            if (attemptId > 0) {
                setEntryProgress(progressKey, attemptId, 0f)
            }
            if (lastError == null) {
                lastError = resolver.lastError()
            }
            if (lastError != null) {
                throw lastError
            }
            throw IOException("missing download url")
        } finally {
            downloadStartByKey.remove(progressKey)
            switchRequested.remove(progressKey)
            if (activeDownloadEntry === entry) {
                activeDownloadEntry = null
            }
        }
    }

    @Throws(IOException::class)
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
            val fileName = entry.getResolvedFileName()
            if (fileName.isNullOrBlank()) {
                continue
            }
            val source = entry.resolveRequiredPath(fileName)
            if (source == null || !Files.exists(source)) {
                continue
            }
            if (entry.resolveAppliedTargets().isEmpty()) {
                didApply = true
            }
            for (targetDir in entry.resolveAppliedTargets()) {
                val target = resolveTargetPath(targetDir, fileName)
                if (target == null) {
                    continue
                }
                try {
                    Files.createDirectories(target.parent)
                    if (!Files.exists(target)) {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                        didApply = true
                    }
                } catch (_: IOException) {
                }
            }
        }
        for (entry in applyQueue) {
            val id = entry.getIdentifier()
            if (id.isBlank()) {
                continue
            }
            DlcRequiredManager.updateUserRemoved(entry, false)
            DlcRequiredRuntimeState.setOverride(id, true)
        }
        if (updatePackageRequired) {
            val updateCheck: UpdateCheckResult = DlcUpdateService.checkForUpdates(true)
            if (updateCheck.updates.isNotEmpty()) {
                val updateResult = DlcUpdateService.applyUpdates(updateCheck.updates, false)
                if (updateResult.isMissing) {
                    Minecraft.getInstance().execute {
                        minecraft.setScreen(DlcUpdateMissingScreen(parent, updateCheck, updateResult.missingPaths))
                    }
                    return
                }
                if (!updateResult.isSuccess) {
                    Minecraft.getInstance().execute {
                        minecraft.setScreen(DlcUpdateResultScreen(parent, false, Component.translatable("dlc_manager.update.failed")))
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
        if (forceRestartRequired) {
            Minecraft.getInstance().execute { minecraft.setScreen(RestartRequiredScreen()) }
            return
        }
        Minecraft.getInstance().execute {
            minecraft.setScreen(
                ConfirmScreen(
                    { accepted ->
                        if (accepted) {
                            minecraft.stop()
                        } else if (!DlcClientRuntime.tryContinueFirstRun(parent)) {
                            minecraft.setScreen(parent)
                        }
                    },
                    Component.translatable("dlc_manager.title"),
                    Component.translatable("dlc_manager.required.restart_prompt"),
                    Component.translatable("dlc_manager.restart_now"),
                    Component.translatable("dlc_manager.restart_later"),
                ),
            )
        }
    }

    private inner class RestartRequiredScreen : Screen(Component.translatable("dlc_manager.title")) {
        override fun init() {
            val buttonWidth = 140
            val buttonHeight = 20
            val y = height - 40
            addRenderableWidget(
                Button.builder(Component.translatable("dlc_manager.restart_now")) { Minecraft.getInstance().stop() }
                    .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                    .build(),
            )
        }

        override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            DlcScreenCompat.renderBackground(this, guiGraphics, mouseX, mouseY, partialTick)
            val centerX = width / 2
            var y = 40
            val titleX = (width - font.width(title)) / 2
            guiGraphics.drawString(font, title, titleX, y, 0xFFFFFF, false)
            y += 20
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.required.restart_prompt"), centerX, y, 0xC8C8C8)
            DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
        }

        override fun shouldCloseOnEsc(): Boolean {
            return false
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
            Component.translatable("dlc_manager.update.deferred_restart")
        } else {
            Component.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(parent, true, message)
    }

    private fun hasUpdatePackageTargets(entries: List<DlcRequiredEntry>?): Boolean {
        if (entries.isNullOrEmpty()) {
            return false
        }
        val dlcRoot = DlcPaths.dlcRoot() ?: return false
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
        return DcFormatUtil.isDcName(fileName)
    }

    private fun isTempFileName(fileName: String?): Boolean {
        if (fileName == null) {
            return false
        }
        return fileName.lowercase().endsWith(".part")
    }

    private fun getCompletedProgress(entry: DlcRequiredEntry?): Float? {
        if (entry == null) {
            return null
        }
        val fileName = resolveExistingFileName(entry)
        if (fileName == null || isTempFileName(fileName)) {
            return null
        }
        val required = entry.resolveRequiredPath(fileName)
        if (required != null && Files.exists(required)) {
            return 1f
        }
        return null
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
                            if (!DcFormatUtil.isDcName(name)) {
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
        var fileName = entry.getResolvedFileName()
        if (isConfigFileName(fileName) || isTempFileName(fileName)) {
            fileName = null
        }
        if (!fileName.isNullOrBlank()) {
            val target = entry.resolveRequiredPath(fileName)
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName
            }
        }
        val base = entry.getBaseName()
        val requiredDir = entry.getRequiredDir()
        if (requiredDir == null || base.isBlank()) {
            return fileName
        }
        try {
            Files.newDirectoryStream(requiredDir, "$base.*").use { stream ->
                for (path in stream) {
                    if (isConfigFile(entry, path)) {
                        continue
                    }
                    val name = path.fileName?.toString() ?: ""
                    if (isTempFileName(name)) {
                        continue
                    }
                    return name
                }
            }
        } catch (_: IOException) {
        }
        return fileName
    }

    private fun resolveFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        val resolver = DlcDownloadCandidate.resolver(entry)
        try {
            while (true) {
                val candidates = resolver.next()
                if (candidates.isEmpty()) {
                    break
                }
                for (candidate in candidates) {
                    var name = candidate.fileNameOverride
                    if (!DlcDownloadUtil.isLikelyFileName(name) && candidate.direct) {
                        name = DlcDownloadUtil.extractFileNameFromUrl(candidate.url)
                    }
                    name = DlcDownloadUtil.sanitizeFileName(name)
                    if (DlcDownloadUtil.isLikelyFileName(name)) {
                        return name
                    }
                }
            }
        } catch (_: IOException) {
            if (Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return null
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
        val barY = top + ((scrollIndex / maxScroll.toFloat()) * maxTrack).toInt()
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B.toInt())
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val bg = 0xFF2B2B2B.toInt()
        val fg = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + width, y + height, bg)
        val filled = (width * max(0f, min(1f, progress))).toInt()
        guiGraphics.fill(x, y, x + filled, y + height, fg)
    }

    @Throws(DownloadCanceledException::class)
    private fun checkCanceled() {
        if (cancelRequested) {
            throw DownloadCanceledException("canceled")
        }
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

    private fun isSwitchException(ex: IOException): Boolean {
        if (ex is DownloadSwitchException) {
            return true
        }
        return ex.cause is DownloadSwitchException
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

    private fun shouldShowSwitch(key: String?, entry: DlcRequiredEntry?, progress: Float): Boolean {
        if (!downloading || key == null || entry == null) {
            return false
        }
        if (progress >= 1f) {
            return false
        }
        val start = downloadStartByKey[key] ?: return false
        if (System.currentTimeMillis() - start < SWITCH_DELAY_MS) {
            return false
        }
        return hasMultipleSources(entry)
    }

    private fun drawSwitchButton(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val right = x + SWITCH_BUTTON_SIZE
        val bottom = y + SWITCH_BUTTON_SIZE
        guiGraphics.fill(x, y, right, bottom, 0xFF4B4B4B.toInt())
        guiGraphics.drawString(font, ">", x + 2, y + 1, 0xE6E6E6, false)
    }

    private fun handleSwitchClick(mouseX: Double, mouseY: Double): Boolean {
        if (!downloading) {
            return false
        }
        if (maxConcurrentDownloads <= 1) {
            val activeEntry = activeDownloadEntry
            val key = if (activeEntry == null) null else progressKey(activeEntry)
            if (key != null && shouldShowSwitch(key, activeEntry, currentProgress)) {
                val centerX = width / 2
                val barLeft = centerX - 120
                val switchLeft = barLeft - SWITCH_BUTTON_PAD - SWITCH_BUTTON_SIZE
                val switchTop = (height - 70) + 12 - 1
                if (mouseX >= switchLeft && mouseX <= switchLeft + SWITCH_BUTTON_SIZE &&
                    mouseY >= switchTop && mouseY <= switchTop + SWITCH_BUTTON_SIZE
                ) {
                    switchRequested.add(key)
                    return true
                }
            }
            return false
        }
        val listTop = getListTop()
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = displayList.size
        val visibleEnd = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until visibleEnd) {
            val entry = displayList[i]
            val key = progressKey(entry)
            val progress = progressByKey[key]
            if (progress == null || !shouldShowSwitch(key, entry, progress)) {
                continue
            }
            val row = i - scrollIndex
            val rowY = listTop + row * LINE_HEIGHT
            val barWidth = 36
            val percentBoxWidth = font.width("100%")
            val percentRight = width - 18
            val percentLeft = percentRight - percentBoxWidth
            val barRight = percentLeft - 6
            val barLeft = barRight - barWidth
            val barY = rowY + 2
            val switchRight = barLeft - SWITCH_BUTTON_PAD
            val switchLeft = switchRight - SWITCH_BUTTON_SIZE
            val switchTop = barY - 1
            if (mouseX >= switchLeft && mouseX <= switchLeft + SWITCH_BUTTON_SIZE &&
                mouseY >= switchTop && mouseY <= switchTop + SWITCH_BUTTON_SIZE
            ) {
                switchRequested.add(key)
                return true
            }
        }
        return false
    }

    private class DownloadSwitchException(message: String?) : IOException(message)

    private class DownloadCanceledException(message: String?) : IOException(message)

    @Throws(IOException::class)
    private fun openDownload(url: String, offset: Long): DownloadInfo {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = taskTimeoutMs
        connection.setRequestProperty("User-Agent", "DLC-Manager")
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }
        connection.connect()
        var fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection)
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.url.toString())
        }
        val responseCode = connection.responseCode
        val contentLength = connection.contentLengthLong
        val totalBytes = if (contentLength > 0) contentLength + max(0L, offset) else -1L
        val contentMd5 = connection.getHeaderField("Content-MD5")
        return DownloadInfo(connection, fileName, responseCode, totalBytes, contentMd5)
    }

    private fun closeQuietly(connection: HttpURLConnection?) {
        if (connection != null) {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
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

    private class DownloadResult(
        val entry: DlcRequiredEntry,
        val success: Boolean,
        val canceled: Boolean,
        val error: Exception?,
    )

    private class DownloadInfo(
        val connection: HttpURLConnection,
        val fileName: String?,
        val responseCode: Int,
        val totalBytes: Long,
        val contentMd5: String?,
    )

    @Throws(IOException::class)
    private fun verifyDownloadedFile(tempFile: Path, candidate: DlcDownloadCandidate?, info: DownloadInfo?) {
        checkCanceled()
        val hashType = candidate?.hashType
        val hashValue = candidate?.hashValue
        if (hashType != null && hashValue != null && hashValue.isNotBlank()) {
            val algorithm = mapHashAlgorithm(hashType)
            val actual = DlcDownloadUtil.computeHashHex(tempFile, algorithm)
            if (actual == null || !actual.equals(DlcDownloadUtil.normalizeHash(hashValue), ignoreCase = true)) {
                Files.deleteIfExists(tempFile)
                throw IOException("hash mismatch")
            }
            return
        }
        if (info != null && !info.contentMd5.isNullOrBlank()) {
            val expected = DlcDownloadUtil.normalizeHash(info.contentMd5)
            val actualHex = DlcDownloadUtil.computeHashHex(tempFile, "MD5")
            val actualBase64 = DlcDownloadUtil.computeMd5Base64(tempFile)
            if (actualHex == null || actualBase64 == null ||
                (!expected.equals(actualHex, ignoreCase = true) && !expected.equals(actualBase64, ignoreCase = true))
            ) {
                Files.deleteIfExists(tempFile)
                throw IOException("hash mismatch")
            }
            return
        }
        var expectedSize = candidate?.expectedSize ?: -1L
        if (expectedSize <= 0 && info != null && info.totalBytes > 0) {
            expectedSize = info.totalBytes
        }
        if (expectedSize > 0) {
            val size = Files.size(tempFile)
            if (size != expectedSize) {
                Files.deleteIfExists(tempFile)
                throw IOException("size mismatch")
            }
        }
    }

    private fun mapHashAlgorithm(hashType: String?): String? {
        if (hashType == null) {
            return null
        }
        return when (hashType.lowercase()) {
            "sha1", "sha-1" -> "SHA-1"
            "sha256", "sha-256" -> "SHA-256"
            "sha512", "sha-512" -> "SHA-512"
            "md5" -> "MD5"
            else -> hashType
        }
    }

    companion object {
        private const val SWITCH_DELAY_MS = 20_000L
        private const val SWITCH_BUTTON_SIZE = 8
        private const val SWITCH_BUTTON_PAD = 4
        private const val LINE_HEIGHT = 10
    }
}
