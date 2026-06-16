package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.required.DlcRequiredDependency
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DcFormatUtil
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcRequiredManagerScreen(
    private val parent: Screen?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.required.manage_title")) {
    private class InstallState {
        @Volatile var running = false
        @Volatile var done = false
        @Volatile var failed = false
        @Volatile var progress = 0f
        @Volatile var error: String? = null
    }

    private val allEntries: MutableList<DlcRequiredEntry> = ArrayList()
    private val visibleEntries: MutableList<DlcRequiredEntry> = ArrayList()
    private val index: MutableMap<String, DlcRequiredEntry> = HashMap()
    private var dependents: Map<String, Set<String>> = HashMap()
    private val installStates: MutableMap<String, InstallState> = ConcurrentHashMap()
    @Volatile
    private var batchInstalling = false
    private val batchState = InstallState()
    private var noticeLines: List<String> = emptyList()

    private var applyAllButton: Button? = null
    private var backButton: Button? = null
    private val entryButtons: MutableMap<String, Button> = HashMap()
    private var scrollIndex = 0

    override fun init() {
        rebuildEntries()
        noticeLines = splitLines(DlcRequiredNotice.load().resolveManageDesc())
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
        updateApplyAllAvailability()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18
        for (line in getManageDescLines()) {
            guiGraphics.drawCenteredString(font, line, centerX, y, 0xC8C8C8)
            y += font.lineHeight + 2
        }
        y += 10

        val listTop = y
        val listBottom = height - 70
        val lineHeight = 18
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val totalLines = visibleEntries.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }

        var rowY = listTop
        for (i in visibleEntries.indices) {
            val entry = visibleEntries[i]
            val button = entryButtons[entry.getIdentifier()]
            val visible = i >= scrollIndex && i < scrollIndex + maxVisible
            if (!visible) {
                if (button != null) {
                    button.visible = false
                }
                continue
            }
            if (button != null) {
                button.visible = true
                button.y = rowY - 2
            }
            val label = buildEntryLabel(entry)
            guiGraphics.drawString(font, label, 20, rowY, 0xE6E6E6, false)
            if (isMouseOverRow(mouseX, mouseY, rowY, lineHeight) && !isMouseOverButton(button, mouseX, mouseY)) {
                renderTooltip(guiGraphics, entry, mouseX, mouseY)
            }
            rowY += lineHeight
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)

        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
        renderInstallOverlays(guiGraphics)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val listTop = getListTop()
        val listBottom = height - 70
        val lineHeight = 18
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val totalLines = visibleEntries.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (delta > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    private fun getListTop(): Int {
        var y = 20 + 18
        y += getManageDescLines().size * (font.lineHeight + 2)
        y += 10
        return y
    }

    private fun getManageDescLines(): List<String> {
        return noticeLines
    }

    private fun splitLines(text: String?): List<String> {
        if (text.isNullOrBlank()) {
            return ArrayList()
        }
        if (text.contains("\n")) {
            val parts = text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val lines: MutableList<String> = ArrayList()
            for (part in parts) {
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

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        clearWidgets()
        entryButtons.clear()
        init()
    }

    private fun rebuildEntries() {
        allEntries.clear()
        allEntries.addAll(DlcRequiredManager.loadAllEntries())
        index.clear()
        for (entry in allEntries) {
            val id = entry.getIdentifier()
            if (!id.isNullOrBlank()) {
                index.putIfAbsent(id, entry)
            }
        }
        dependents = DlcRequiredManager.buildDependents(allEntries)
        visibleEntries.clear()
        for (entry in allEntries) {
            if (entry.isHidden() || !entry.isInList()) {
                continue
            }
            visibleEntries.add(entry)
        }
        autoCleanupHidden()
    }

    private fun autoCleanupHidden() {
        for (entry in allEntries) {
            if (!entry.isHidden() || !entry.isInList()) {
                continue
            }
            val id = entry.getIdentifier()
            if (dependents.containsKey(id)) {
                continue
            }
            if (isApplied(entry)) {
                uninstallEntry(entry)
                DlcRequiredRuntimeState.setOverride(id, false)
            }
        }
    }

    private fun buildEntryButtons() {
        val buttonWidth = 72
        val buttonHeight = 16
        val x = width - 20 - buttonWidth
        for (entry in visibleEntries) {
            val id = entry.getIdentifier()
            if (id.isNullOrBlank()) {
                continue
            }
            val label = getEntryButtonLabel(entry)
            val button = Button.builder(Component.literal(label)) { onEntryAction(entry) }
                .bounds(x, 0, buttonWidth, buttonHeight)
                .build()
            entryButtons[id] = button
            addRenderableWidget(button)
        }
    }

    private fun onEntryAction(entry: DlcRequiredEntry?) {
        if (entry == null) {
            return
        }
        val state = installStates[entry.getIdentifier()]
        if (state != null && state.running) {
            return
        }
        if (getDesiredState(entry)) {
            uninstallEntry(entry)
            DlcRequiredRuntimeState.setOverride(entry.getIdentifier(), false)
            updateEntryButtons()
            return
        }
        startInstall(entry, false)
    }

    private fun applyAll() {
        if (batchInstalling) {
            return
        }
        val targets = DlcRequiredManager.collectDependencies(visibleEntries)
        if (targets.isEmpty()) {
            showRequiredErrorToast("no_installable_entries")
            return
        }
        batchInstalling = true
        applyAllButton?.active = false
        applyAllButton?.message = Component.literal("")
        batchState.running = true
        batchState.done = false
        batchState.failed = false
        batchState.progress = 0f
        batchState.error = null
        INSTALL_EXECUTOR.submit {
            var didApply = false
            try {
                didApply = installEntries(targets, batchState)
            } catch (e: Exception) {
                batchState.failed = true
                batchState.error = e.message
            }
            batchInstalling = false
            batchState.running = false
            batchState.done = true
            val shouldPrompt = didApply || DlcRequiredRuntimeState.hasPendingChanges()
            val minecraft = Minecraft.getInstance()
            minecraft.execute {
                updateApplyAllAvailability()
                updateEntryButtons()
                if (batchState.failed) {
                    showRequiredErrorToast(batchState.error)
                    return@execute
                }
                if (shouldPrompt) {
                    minecraft.setScreen(
                        ConfirmScreen(
                            { accepted ->
                                if (accepted) {
                                    minecraft.stop()
                                } else {
                                    minecraft.setScreen(null)
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
        }
    }

    private fun handleBack() {
        Minecraft.getInstance().setScreen(parent)
    }

    private fun getDesiredState(entry: DlcRequiredEntry): Boolean {
        val applied = isApplied(entry)
        DlcRequiredRuntimeState.getBaseline(entry.getIdentifier(), applied)
        return DlcRequiredRuntimeState.getDesired(entry.getIdentifier(), applied)
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
            val targetFile = resolveTarget(target, fileName)
            if (targetFile == null || !Files.exists(targetFile)) {
                return false
            }
        }
        return true
    }

    private fun resolveExistingFileName(entry: DlcRequiredEntry): String? {
        var fileName = entry.getRequiredFileName()
        if (!fileName.isNullOrBlank()) {
            return fileName
        }
        fileName = entry.getResolvedFileName()
        if (!fileName.isNullOrBlank()) {
            val target = entry.resolveRequiredPath(fileName)
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName
            }
        }
        val base = entry.getBaseName()
        if (entry.getRequiredDir() == null || base.isBlank()) {
            return fileName
        }
        try {
            Files.newDirectoryStream(entry.getRequiredDir(), "$base.*").use { stream ->
                for (path in stream) {
                    if (isConfigFile(entry, path)) {
                        continue
                    }
                    val name = path.fileName?.toString() ?: ""
                    if (DcFormatUtil.isDcName(name)) {
                        continue
                    }
                    return name
                }
            }
        } catch (_: IOException) {
        }
        return fileName
    }

    private fun uninstallEntry(root: DlcRequiredEntry?) {
        val removeIds = collectUninstallTargets(root)
        for (id in removeIds) {
            val entry = index[id]
            if (entry != null) {
                val desired = getDesiredState(entry)
                if (!desired) {
                    continue
                }
                DlcRequiredRuntimeState.getBaseline(id, isApplied(entry))
                removeEntryFiles(entry)
                DlcRequiredManager.updateUserRemoved(entry, true)
                DlcRequiredRuntimeState.setOverride(id, false)
            }
        }
    }

    private fun collectUninstallTargets(root: DlcRequiredEntry?): Set<String> {
        val remove: MutableSet<String> = HashSet()
        if (root == null) {
            return remove
        }
        val dependentsMap = DlcRequiredManager.buildDependents(allEntries)
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(root.getIdentifier())
        while (!queue.isEmpty()) {
            val id = queue.poll()
            if (!remove.add(id)) {
                continue
            }
            val entry = index[id]
            if (entry == null) {
                continue
            }
            for (dep in entry.getDependencies()) {
                val depId = dep?.getIdentifier()
                if (depId.isNullOrBlank()) {
                    continue
                }
                val depEntry = index[depId]
                if (depEntry != null && !getDesiredState(depEntry)) {
                    continue
                }
                if (hasOtherDependents(depId, remove, dependentsMap)) {
                    continue
                }
                queue.add(depId)
            }
            val ups = dependentsMap[id]
            if (ups != null) {
                for (up in ups) {
                    if (!remove.contains(up)) {
                        val upEntry = index[up]
                        if (upEntry != null && !getDesiredState(upEntry)) {
                            continue
                        }
                        queue.add(up)
                    }
                }
            }
        }
        return remove
    }

    private fun hasOtherDependents(id: String, removing: Set<String>, dependentsMap: Map<String, Set<String>>): Boolean {
        val deps = dependentsMap[id]
        if (deps.isNullOrEmpty()) {
            return false
        }
        for (dep in deps) {
            val entry = index[dep]
            if (!removing.contains(dep) && entry != null && getDesiredState(entry)) {
                return true
            }
        }
        return false
    }

    private fun removeEntryFiles(entry: DlcRequiredEntry?) {
        if (entry == null) {
            return
        }
        val targets = entry.resolveAppliedTargets()
        if (targets.isEmpty()) {
            return
        }
        val fileName = resolveAppliedFileName(entry, targets)
        for (target in entry.resolveAppliedTargets()) {
            val name = target.fileName?.toString() ?: ""
            val targetFile = if (name.endsWith(".jar") || name.endsWith(".zip")) {
                target
            } else {
                resolveTarget(target, fileName)
            }
            if (targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile)
                } catch (_: IOException) {
                }
            }
        }
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
        return DcFormatUtil.isDcName(name)
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

    private fun resolveTarget(targetDir: Path?, fileName: String?): Path? {
        if (targetDir == null || fileName.isNullOrBlank()) {
            return null
        }
        val name = targetDir.fileName?.toString() ?: ""
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir
        }
        return targetDir.resolve(fileName)
    }

    private fun buildEntryLabel(entry: DlcRequiredEntry): Component {
        val name = entry.resolveDisplayName()
        val applied = getDesiredState(entry)
        val baseline = DlcRequiredRuntimeState.getBaseline(entry.getIdentifier(), applied)
        val pending = DlcRequiredRuntimeState.isPending(entry.getIdentifier(), baseline)
        var label = DlcTextFormatter.parse(name)
        if (applied && pending) {
            label = label.copy().withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)
        } else if (applied) {
            label = label.copy().withStyle(ChatFormatting.GREEN)
        } else if (pending) {
            label = label.copy().withStyle(ChatFormatting.ITALIC)
        }
        if (pending) {
            label = label.copy().append(Component.translatable("dlc_manager.pending.restart"))
        }
        return label
    }

    private fun getEntryButtonLabel(entry: DlcRequiredEntry): String {
        return if (getDesiredState(entry)) {
            Component.translatable("dlc_manager.required.uninstall").string
        } else {
            Component.translatable("dlc_manager.required.download").string
        }
    }

    private fun updateEntryButtons() {
        for (entry in visibleEntries) {
            val id = entry.getIdentifier()
            val button = entryButtons[id]
            if (button != null) {
                val state = installStates[id]
                if (state != null && state.running) {
                    button.active = false
                    button.message = Component.literal("")
                } else {
                    button.active = true
                    button.message = Component.literal(getEntryButtonLabel(entry))
                }
            }
        }
        updateApplyAllAvailability()
    }

    private fun updateApplyAllAvailability() {
        val button = applyAllButton ?: return
        if (batchInstalling || batchState.running) {
            button.active = false
            return
        }
        var allApplied = true
        for (entry in visibleEntries) {
            if (!isApplied(entry)) {
                allApplied = false
                break
            }
        }
        button.active = !allApplied
        button.message = Component.translatable("dlc_manager.required.apply_all")
    }

    private fun isMouseOverRow(mouseX: Int, mouseY: Int, y: Int, height: Int): Boolean {
        return mouseX >= 16 && mouseX <= width - 16 && mouseY >= y && mouseY <= y + height
    }

    private fun isMouseOverButton(button: Button?, mouseX: Int, mouseY: Int): Boolean {
        if (button == null || !button.visible) {
            return false
        }
        return button.isMouseOver(mouseX.toDouble(), mouseY.toDouble())
    }

    private fun renderInstallOverlays(guiGraphics: GuiGraphics) {
        val applyAll = applyAllButton
        if (batchState.running && applyAll != null && applyAll.visible) {
            drawProgressButton(guiGraphics, applyAll.x, applyAll.y, applyAll.width, applyAll.height, batchState.progress)
        }
        for (entry in visibleEntries) {
            val id = entry.getIdentifier()
            if (id.isNullOrBlank()) {
                continue
            }
            val state = installStates[id]
            if (state == null || !state.running) {
                continue
            }
            val button = entryButtons[id]
            if (button == null || !button.visible) {
                continue
            }
            drawProgressButton(guiGraphics, button.x, button.y, button.width, button.height, state.progress)
        }
    }

    private fun drawProgressButton(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val bg = 0xFF2B2B2B.toInt()
        val fg = 0xFF4CAF50.toInt()
        guiGraphics.fill(x, y, x + width, y + height, bg)
        val clamped = max(0f, min(1f, progress))
        val filled = (width * clamped).toInt()
        guiGraphics.fill(x, y, x + filled, y + height, fg)
        val percent = (clamped * 100f).roundToInt()
        val label = "$percent%"
        val textX = x + (width - font.width(label)) / 2
        val textY = y + (height - font.lineHeight) / 2 + 1
        guiGraphics.drawString(font, label, textX, textY, 0xFFFFFF, false)
    }

    private fun showRequiredErrorToast(reason: String?) {
        val safeReason = if (reason.isNullOrBlank()) "unknown" else reason
        val minecraft = Minecraft.getInstance()
        DlcScreenCompat.addOrUpdatePeriodicNotification(
            minecraft.toasts,
            Component.translatable("dlc_manager.required.manage_title"),
            Component.translatable("dlc_manager.required.error", safeReason),
        )
    }

    private fun startInstall(entry: DlcRequiredEntry?, promptRestart: Boolean) {
        if (entry == null) {
            return
        }
        val id = entry.getIdentifier()
        val state = installStates.computeIfAbsent(id) { InstallState() }
        if (state.running) {
            return
        }
        state.running = true
        state.done = false
        state.failed = false
        state.progress = 0f
        state.error = null
        updateEntryButtons()
        val targets = DlcRequiredManager.collectDependencies(listOf(entry))
        INSTALL_EXECUTOR.submit {
            var didApply = false
            try {
                didApply = installEntries(targets, state)
            } catch (e: Exception) {
                state.failed = true
                state.error = e.message
            }
            state.running = false
            state.done = true
            val shouldPrompt = promptRestart && (didApply || DlcRequiredRuntimeState.hasPendingChanges())
            val minecraft = Minecraft.getInstance()
            minecraft.execute {
                updateEntryButtons()
                if (state.failed) {
                    showRequiredErrorToast(state.error)
                    return@execute
                }
                if (shouldPrompt) {
                    minecraft.setScreen(
                        ConfirmScreen(
                            { accepted ->
                                if (accepted) {
                                    minecraft.stop()
                                } else {
                                    minecraft.setScreen(this)
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
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun installEntries(entries: List<DlcRequiredEntry>, state: InstallState): Boolean {
        val unique: MutableList<DlcRequiredEntry> = ArrayList()
        val seen: MutableSet<String> = HashSet()
        for (entry in entries) {
            if (!entry.isInList()) {
                continue
            }
            val id = entry.getIdentifier()
            if (id.isNullOrBlank() || !seen.add(id)) {
                continue
            }
            unique.add(entry)
        }
        val total = max(1, unique.size)
        var didApply = false
        var index = 0
        for (entry in unique) {
            val id = entry.getIdentifier()
            val applied = isApplied(entry)
            DlcRequiredRuntimeState.getBaseline(id, applied)
            updateProgress(state, index, 0f, total)
            var fileName = resolveExistingFileName(entry)
            if (fileName.isNullOrBlank()) {
                fileName = resolveFileName(entry)
            }
            if (fileName.isNullOrBlank()) {
                downloadEntry(entry, state, index, total)
                fileName = entry.getResolvedFileName()
            } else {
                entry.setResolvedFileName(fileName)
                val required = entry.resolveRequiredPath(fileName)
                if (required == null || !Files.exists(required)) {
                    downloadEntry(entry, state, index, total)
                }
            }
            if (applyEntry(entry)) {
                didApply = true
            }
            DlcRequiredManager.updateUserRemoved(entry, false)
            DlcRequiredRuntimeState.setOverride(id, true)
            index++
            updateProgress(state, index, 0f, total)
        }
        updateProgress(state, total, 0f, total)
        return didApply
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadEntry(entry: DlcRequiredEntry?, state: InstallState, stepIndex: Int, totalSteps: Int) {
        if (entry == null) {
            return
        }
        val resolver = DlcDownloadCandidate.resolver(entry)
        var lastError: IOException? = null
        while (true) {
            val candidates = resolver.next()
            if (candidates.isEmpty()) {
                break
            }
            for (candidate in candidates) {
                try {
                    var fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName())
                    if (fileName == null && candidate.fileNameOverride != null) {
                        fileName = DlcDownloadUtil.sanitizeFileName(candidate.fileNameOverride)
                    }
                    if (fileName == null && candidate.direct) {
                        try {
                            fileName = DlcDownloadService.suggestFileName(candidate.url)
                        } catch (_: IOException) {
                        }
                    }
                    if (fileName == null) {
                        fileName = DlcDownloadUtil.stripQuery(entry.getBaseName())
                    }
                    val target = entry.resolveRequiredPath(fileName)
                        ?: throw IOException("invalid target path")
                    Files.createDirectories(target.parent)
                    if (Files.exists(target)) {
                        entry.setResolvedFileName(fileName)
                        DlcRequiredManager.ensureFileNameKey(entry, fileName)
                        return
                    }
                    val downloaded = if (candidate.direct) {
                        DlcDownloadService.download(
                            candidate.url,
                            target.parent,
                            fileName,
                            { done, total ->
                                if (total > 0) {
                                    val fraction = min(1f, done.toFloat() / total.toFloat())
                                    updateProgress(state, stepIndex, fraction, totalSteps)
                                }
                            },
                            null,
                        )
                    } else {
                        DlcDownloadService.downloadKnown(
                            candidate.url,
                            target.parent,
                            fileName,
                            candidate.expectedSize,
                            { done, total ->
                                if (total > 0) {
                                    val fraction = min(1f, done.toFloat() / total.toFloat())
                                    updateProgress(state, stepIndex, fraction, totalSteps)
                                }
                            },
                            null,
                        )
                    }
                    val actualName = downloaded.fileName?.toString() ?: fileName
                    entry.setResolvedFileName(actualName)
                    DlcRequiredManager.updateFileNameKey(entry, actualName)
                    updateProgress(state, stepIndex + 1, 0f, totalSteps)
                    return
                } catch (ex: IOException) {
                    lastError = ex
                }
            }
        }
        if (lastError == null) {
            lastError = resolver.lastError()
        }
        if (lastError != null) {
            throw lastError
        }
        throw IOException("missing download url")
    }

    private fun applyEntry(entry: DlcRequiredEntry?): Boolean {
        if (entry == null) {
            return false
        }
        val fileName = entry.getResolvedFileName()
        if (fileName.isNullOrBlank()) {
            return false
        }
        val source = entry.resolveRequiredPath(fileName)
        if (source == null || !Files.exists(source)) {
            return false
        }
        var applied = false
        if (entry.resolveAppliedTargets().isEmpty()) {
            return true
        }
        for (targetDir in entry.resolveAppliedTargets()) {
            val target = resolveTarget(targetDir, fileName)
            if (target == null) {
                continue
            }
            try {
                Files.createDirectories(target.parent)
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    applied = true
                }
            } catch (_: IOException) {
            }
        }
        return applied
    }

    private fun updateProgress(state: InstallState?, stepIndex: Int, fraction: Float, totalSteps: Int) {
        if (state == null) {
            return
        }
        val progress = (stepIndex + max(0f, min(1f, fraction))) / totalSteps.toFloat()
        state.progress = max(0f, min(1f, progress))
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

    private fun renderTooltip(guiGraphics: GuiGraphics, entry: DlcRequiredEntry?, mouseX: Int, mouseY: Int) {
        if (entry == null) {
            return
        }
        val tip = resolveTip(entry)
        if (tip.isNullOrBlank()) {
            return
        }
        val maxWidth = min(260, width - 40)
        val lines: List<FormattedCharSequence> = DlcTextFormatter.splitMultiline(font, tip, maxWidth)
        guiGraphics.renderTooltip(font, lines, mouseX, mouseY)
    }

    private fun resolveTip(entry: DlcRequiredEntry?): String {
        if (entry == null || entry.getTips().isEmpty()) {
            return ""
        }
        val lang = Minecraft.getInstance().options.languageCode
        if (entry.getTips().containsKey(lang)) {
            return entry.getTips()[lang] ?: ""
        }
        if (entry.getTips().containsKey("en_us")) {
            return entry.getTips()["en_us"] ?: ""
        }
        return entry.getTips().values.stream().findFirst().orElse("")
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

    companion object {
        private val INSTALL_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(3) { runnable ->
            Thread(runnable, "dlc-required-install").apply {
                isDaemon = true
            }
        }
    }
}
