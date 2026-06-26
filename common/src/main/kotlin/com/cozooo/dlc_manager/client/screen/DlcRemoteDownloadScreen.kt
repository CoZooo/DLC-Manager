package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.download.DlcModrinthClient
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcRemoteDownloadScreen(
    private val parent: Screen?,
    missing: List<DlcRemoteEntry>?,
    private val partMarkerKey: String = DlcRemoteMarker.PART_DLC,
    title: Component = Component.translatable("dlc_manager.remote.title"),
) : DlcScreenCompat.MouseScroll(title) {
    private val missing: List<DlcRemoteEntry> = missing ?: emptyList()
    private var previewQueue: List<DlcRemoteEntry> = emptyList()
    private var downloadTargets: List<DlcRemoteEntry> = emptyList()
    private val maxConcurrentDownloads = max(1, DlcConfig.getMaxConcurrentDownloads())
    private val maxRetry = max(1, DlcConfig.getMaxRetryCount())
    private val connectTimeoutMs = max(1, DlcConfig.getConnectTimeoutMs())
    private val taskTimeoutMs = max(1, DlcConfig.getTaskTimeoutMs())
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
    private var scrollIndex = 0
    private var draggingScrollBar = false
    private var scrollBarDragOffset = 0

    override fun init() {
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

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.remote.desc"), centerX, y, 0xC8C8C8)
        y += 16
        if (detecting && !downloading) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.remote.detecting"), centerX, y, 0xE6E6E6)
            y += 12
        }

        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val listTop = y
        drawCheckbox(guiGraphics, CHECKBOX_X, listTop + 1, CHECKBOX_SIZE, isAllSelected())
        guiGraphics.drawString(
            font,
            Component.translatable("dlc_manager.remote.select_all"),
            CHECKBOX_X + CHECKBOX_SIZE + 6,
            listTop,
            0xE6E6E6,
            false,
        )
        val listStart = listTop + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listStart) / LINE_HEIGHT)
        val totalLines = showList.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        val visibleEnd = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until visibleEnd) {
            val entry = showList[i]
            val name = entry.resolveDisplayName()
            val checked = isAllSelected() || selectedKeys.contains(selectionKey(entry))
            val rowY = listStart + (i - scrollIndex) * LINE_HEIGHT
            drawCheckbox(guiGraphics, CHECKBOX_X, rowY + 1, CHECKBOX_SIZE, checked)
            guiGraphics.drawString(font, DlcTextFormatter.parse(name), CHECKBOX_X + CHECKBOX_SIZE + 6, rowY, 0xE6E6E6, false)
            if (downloading && maxConcurrentDownloads > 1) {
                val key = progressKey(entry)
                val progress = progressByKey[key]
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
                    val barY = rowY + 2
                    drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress)
                    if (shouldShowSwitch(key, entry, progress)) {
                        val switchRight = barLeft - SWITCH_BUTTON_PAD
                        val switchLeft = switchRight - SWITCH_BUTTON_SIZE
                        val switchTop = barY - 1
                        drawSwitchButton(guiGraphics, switchLeft, switchTop)
                    }
                    guiGraphics.drawString(font, progressText, percentRight - textWidth, rowY, 0xC8C8C8, false)
                }
            }
        }
        drawScrollBar(guiGraphics, listStart, listBottom, totalLines, maxVisible, maxScroll)

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
                val status = Component.translatable("dlc_manager.remote.downloading_multi").string
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
        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = showList.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (deltaY > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && beginScrollDrag(mouseX, mouseY)) {
            return true
        }
        if (downloading && handleSwitchClick(mouseX, mouseY)) {
            return true
        }
        if (!downloading) {
            val showList = if (previewQueue.isEmpty()) missing else previewQueue
            val listTop = getListTop()
            val listBottom = height - 70
            if (mouseY >= listTop && mouseY < listTop + LINE_HEIGHT) {
                val boxX = CHECKBOX_X
                val boxY = listTop + 1
                if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE &&
                    mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE
                ) {
                    toggleSelectAll()
                    return true
                }
            }
            val listStart = listTop + LINE_HEIGHT
            if (mouseY >= listStart && mouseY < listBottom) {
                val row = ((mouseY - listStart) / LINE_HEIGHT).toInt()
                val index = scrollIndex + row
                if (index >= 0 && index < showList.size) {
                    val boxX = CHECKBOX_X
                    val boxY = listStart + row * LINE_HEIGHT + 1
                    if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE &&
                        mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE
                    ) {
                        toggleSelection(showList[index], showList)
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == 0 && draggingScrollBar) {
            updateScrollFromDrag(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollBar) {
            draggingScrollBar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
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

    private fun beginScrollDrag(mouseX: Double, mouseY: Double): Boolean {
        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = showList.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return false
        }
        val barX = width - 12
        val barWidth = 4
        val barHeight = max(12, (listBottom - listTop) * maxVisible / totalLines)
        val trackHeight = listBottom - listTop
        val maxTrack = max(1, trackHeight - barHeight)
        val barY = listTop + ((scrollIndex / maxScroll.toFloat()) * maxTrack).toInt()
        if (mouseX < barX || mouseX > barX + barWidth || mouseY < listTop || mouseY > listBottom) {
            return false
        }
        if (mouseY >= barY && mouseY <= barY + barHeight) {
            draggingScrollBar = true
            scrollBarDragOffset = mouseY.toInt() - barY
            return true
        }
        var targetBarY = mouseY.toInt() - barHeight / 2
        targetBarY = max(listTop, min(listTop + maxTrack, targetBarY))
        val newIndex = (((targetBarY - listTop) / maxTrack.toFloat()) * maxScroll).roundToInt()
        scrollIndex = max(0, min(maxScroll, newIndex))
        return true
    }

    private fun updateScrollFromDrag(mouseY: Double) {
        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = showList.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return
        }
        val barHeight = max(12, (listBottom - listTop) * maxVisible / totalLines)
        val trackHeight = listBottom - listTop
        val maxTrack = max(1, trackHeight - barHeight)
        var targetBarY = mouseY.toInt() - scrollBarDragOffset
        targetBarY = max(listTop, min(listTop + maxTrack, targetBarY))
        val newIndex = (((targetBarY - listTop) / maxTrack.toFloat()) * maxScroll).roundToInt()
        scrollIndex = max(0, min(maxScroll, newIndex))
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val bg = 0xFF2B2B2B.toInt()
        val fg = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + width, y + height, bg)
        val filled = (width * max(0f, min(1f, progress))).toInt()
        guiGraphics.fill(x, y, x + filled, y + height, fg)
    }

    private fun buildPreviewQueue() {
        val queue: MutableList<DlcRemoteEntry> = ArrayList()
        val work: ArrayDeque<DlcRemoteEntry> = ArrayDeque(missing)
        val seen: MutableSet<String> = HashSet()
        while (!work.isEmpty()) {
            val entry = work.poll() ?: continue
            val key = entry.getUniqueKey()
            if (!seen.add(key)) {
                continue
            }
            queue.add(entry)
            for (dep in entry.getRemoteDependencies()) {
                work.add(dep)
            }
            // Disabled: do not auto-append Modrinth required dependencies into remote queue.
        }
        previewQueue = queue
        detecting = false
        statusText = Component.translatable("dlc_manager.remote.ready").string
        scrollIndex = 0
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
        var failed = runDownloadBatch(targets)
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
        if (failed.isNotEmpty()) {
            failed = runDownloadBatch(failed)
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
        if (failed.isNotEmpty()) {
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

    private fun fetchModrinthRequired(entry: DlcRemoteEntry): List<DlcRemoteEntry> {
        val result: MutableList<DlcRemoteEntry> = ArrayList()
        val versionId = entry.getModrinthVersionId()
        if (versionId.isNullOrBlank()) {
            return result
        }
        try {
            val version = DlcModrinthClient.fetchVersion(versionId) ?: return result
            for (dep in version.requiredDependencies) {
                if (dep.versionId.isNullOrBlank()) {
                    continue
                }
                val depEntry = DlcRemoteEntry(null, entry.getDlcRoot(), entry.getBaseDir(), null)
                depEntry.setModrinthProjectId(dep.projectId ?: "")
                depEntry.setModrinthVersionId(dep.versionId)
                depEntry.setType(entry.getType())
                depEntry.setHidden(false)
                depEntry.setAllowEnableByAll(true)
                try {
                    val depVersion = DlcModrinthClient.fetchVersion(dep.versionId)
                    if (depVersion != null && !depVersion.fileName.isNullOrBlank()) {
                        depEntry.setFileNameHint(depVersion.fileName)
                        if (depEntry.getDlcIdentifier().isNullOrBlank()) {
                            depEntry.setDlcIdentifier(depVersion.fileName)
                        }
                    }
                } catch (_: Exception) {
                }
                if (depEntry.getDlcIdentifier().isNullOrBlank() && !dep.projectId.isNullOrBlank()) {
                    depEntry.setDlcIdentifier(dep.projectId)
                }
                result.add(depEntry)
            }
        } catch (_: Exception) {
            return result
        }
        return result
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadEntry(entry: DlcRemoteEntry?) {
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
            precheckDownloadSources(entry, progressKey)
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
                            var downloadInfo: DownloadInfo? = null
                            var headerName: String? = null
                            val shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(candidate.fileNameOverride)
                            if (shouldProbe) {
                                downloadInfo = openDownload(candidate.url, 0)
                                headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName)
                                closeQuietly(downloadInfo.connection)
                            }
                            var fileName = candidate.fileNameOverride
                            val fileNameExplicit = entry.isFileNameExplicit() && !fileName.isNullOrBlank()
                            if (fileName.isNullOrBlank()) {
                                fileName = entry.getFileNameHint()
                            }
                            if (!fileNameExplicit) {
                                if (candidate.direct && headerName != null) {
                                    fileName = headerName
                                }
                                if (!DlcDownloadUtil.isLikelyFileName(fileName) && headerName != null) {
                                    fileName = headerName
                                }
                                if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                                    fileName = DlcDownloadService.suggestFileName(candidate.url)
                                }
                            }
                            fileName = finalizeFileName(fileName, entry, candidate.url)
                            val target = entry.resolveTargetPath(fileName) ?: throw IOException("invalid target path")
                            Files.createDirectories(target.parent)
                            if (Files.exists(target)) {
                                setEntryProgress(progressKey, attemptId, 1f)
                                return
                            }
                            val currentAttemptId = attemptId
                            val downloaded = if (shouldProbe) {
                                DlcDownloadService.download(
                                    candidate.url,
                                    target.parent,
                                    fileName,
                                    progressListener(progressKey, currentAttemptId, entry),
                                    { cancelRequested || isSwitchRequested(progressKey) },
                                )
                            } else {
                                DlcDownloadService.downloadKnown(
                                    candidate.url,
                                    target.parent,
                                    fileName,
                                    candidate.expectedSize,
                                    progressListener(progressKey, currentAttemptId, entry),
                                    { cancelRequested || isSwitchRequested(progressKey) },
                                )
                            }
                            val resolved = if (downloaded == target) {
                                downloaded
                            } else {
                                Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                            verifyDownloadedFile(resolved, candidate, downloadInfo)
                            setEntryProgress(progressKey, currentAttemptId, 1f)
                            entry.setFileNameHint(fileName)
                            DlcRemoteManager.updateFileNameKey(entry, fileName)
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

    private fun precheckDownloadSources(entry: DlcRemoteEntry, progressKey: String) {
        try {
            val candidates = DlcDownloadCandidate.resolve(entry)
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return
            }
            DlcDownloadService.precheckDownloadSources(candidates, progressListener(progressKey, 0, entry))
        } catch (_: Exception) {
        }
    }

    private fun hasMissingHostedDownloadCandidate(entry: DlcRemoteEntry, candidates: List<DlcDownloadCandidate>): Boolean {
        for (candidate in candidates) {
            if (candidate.direct) {
                continue
            }
            val fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url)
            val target = entry.resolveTargetPath(fileName) ?: continue
            if (!Files.exists(target)) {
                return true
            }
        }
        return false
    }

    private fun progressListener(progressKey: String, attemptId: Int, entry: DlcRemoteEntry?): DlcDownloadService.ProgressListener {
        return object : DlcDownloadService.StatusProgressListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                if (totalBytes > 0) {
                    setEntryProgress(progressKey, attemptId, min(1f, downloadedBytes.toFloat() / totalBytes.toFloat()))
                }
            }

            override fun onStatusKey(statusKey: String) {
                statusText = if (statusKey == "selecting_source") {
                    Component.translatable("dlc_manager.remote.selecting_source").string
                } else if (maxConcurrentDownloads <= 1 && entry != null) {
                    Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).string
                } else {
                    Component.translatable("dlc_manager.remote.downloading_multi").string
                }
            }
        }
    }

    private class DownloadSwitchException(message: String?) : IOException(message)

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

    private fun shouldShowSwitch(key: String?, entry: DlcRemoteEntry?, progress: Float): Boolean {
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
        val showList = if (previewQueue.isEmpty()) missing else previewQueue
        val listTop = getListTop() + LINE_HEIGHT
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / LINE_HEIGHT)
        val totalLines = showList.size
        val visibleEnd = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until visibleEnd) {
            val entry = showList[i]
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

    private fun finalizeFileName(fileName: String?, entry: DlcRemoteEntry, url: String?): String {
        var name = fileName
        if (name.isNullOrBlank()) {
            var fallback = entry.getFileNameHint()
            if (fallback.isNullOrBlank()) {
                fallback = entry.getDlcIdentifier()
            }
            if (fallback.isNullOrBlank()) {
                fallback = "dlc-download"
            }
            name = fallback
        }
        if (!name.contains(".")) {
            val extension = guessExtension(entry)
            if (extension != null) {
                name += extension
            }
        }
        return name
    }

    private fun guessExtension(entry: DlcRemoteEntry?): String? {
        if (entry?.getType() == null) {
            return null
        }
        return when (entry.getType()) {
            DlcType.MOD -> ".jar"
            DlcType.RESOURCE_PACK, DlcType.DATA_PACK, DlcType.TACZ_PACK, DlcType.DLC_PACK, DlcType.UNKNOWN -> ".zip"
            null -> null
        }
    }

    private fun closeQuietly(connection: HttpURLConnection?) {
        if (connection != null) {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
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

    private fun drawCheckbox(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int, checked: Boolean) {
        val bg = 0xFF2B2B2B.toInt()
        val outline = 0xFF6B6B6B.toInt()
        val fill = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + size, y + size, outline)
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, bg)
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, fill)
        }
    }

    private fun getListTop(): Int {
        var y = 20 + 18 + 16
        if (detecting && !downloading) {
            y += 12
        }
        return y
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

    private fun runDownloadBatch(pending: List<DlcRemoteEntry>?): List<DlcRemoteEntry> {
        val failed: MutableList<DlcRemoteEntry> = ArrayList()
        if (pending.isNullOrEmpty()) {
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
                    "dlc_manager.remote.downloading",
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
                    errorText = Component.translatable("dlc_manager.remote.error", e.message).string
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
                        errorText = Component.translatable("dlc_manager.remote.error", result.error.message).string
                    }
                }
            } catch (e: Exception) {
                if (e is DownloadCanceledException) {
                    cancelRequested = true
                } else {
                    errorText = Component.translatable("dlc_manager.remote.error", e.message).string
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

    private fun downloadEntryWithResult(entry: DlcRemoteEntry): DownloadResult {
        return try {
            downloadEntry(entry)
            DownloadResult(entry, true, false, null)
        } catch (_: DownloadCanceledException) {
            DownloadResult(entry, false, true, null)
        } catch (e: Exception) {
            DownloadResult(entry, false, false, e)
        }
    }

    private class DownloadResult(
        val entry: DlcRemoteEntry,
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

    private fun stripJson(name: String?): String? {
        return DlcDcFormatUtil.stripConfigExtension(name)
    }

    companion object {
        private const val LINE_HEIGHT = 10
        private const val CHECKBOX_SIZE = 8
        private const val CHECKBOX_X = 12
        private const val NONE_MARKER = "__NONE__"
        private const val ALL_MARKER = "__ALL__"
        private const val SWITCH_DELAY_MS = 20_000L
        private const val SWITCH_BUTTON_SIZE = 8
        private const val SWITCH_BUTTON_PAD = 4
    }
}
