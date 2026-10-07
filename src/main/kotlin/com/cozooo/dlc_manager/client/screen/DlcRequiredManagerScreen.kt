package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredService
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class DlcRequiredManagerScreen(
    private val parent: Screen?,
) : DlcRequiredPageScreen(parent, Component.translatable("dlc_manager.required.manage_title")) {
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

    private var applyAllButton: Button? = null
    private var backButton: Button? = null
    private val entryButtons: MutableMap<String, Button> = HashMap()
    private var scrollIndex = 0

    private fun initControls() {
        rebuildEntries()
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
            if (entry.isHidden()) {
                continue
            }
            visibleEntries.add(entry)
        }
        autoCleanupHidden()
    }

    private fun autoCleanupHidden() {
        for (entry in allEntries) {
            if (!entry.isHidden()) {
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
                        DlcConfirmScreen(
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
                        DlcConfirmScreen(
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
            if (!DlcRequiredService.isDownloaded(entry)) {
                downloadEntry(entry, state, index, total)
            }
            if (DlcRequiredService.installOne(entry)) {
                didApply = true
            }
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
        DlcRequiredService.downloadOne(
            entry,
            { done, total ->
                if (total > 0) {
                    val fraction = min(1f, done.toFloat() / total.toFloat())
                    updateProgress(state, stepIndex, fraction, totalSteps)
                }
            },
            null,
        )
        updateProgress(state, stepIndex + 1, 0f, totalSteps)
    }

    private fun updateProgress(state: InstallState?, stepIndex: Int, fraction: Float, totalSteps: Int) {
        if (state == null) {
            return
        }
        val progress = (stepIndex + max(0f, min(1f, fraction))) / totalSteps.toFloat()
        state.progress = max(0f, min(1f, progress))
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

    companion object {
        private val INSTALL_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(3) { runnable ->
            Thread(runnable, "dlc-required-install").apply {
                isDaemon = true
            }
        }
    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 0

        val resources = visibleEntries.map { entry -> ResourceRow(entry.resolveDisplayName().orEmpty(),
            { buildEntryLabel(entry).string }, { resolveTip(entry) }, entryButtons[entry.getIdentifier()],
            { installStates[entry.getIdentifier()]?.takeIf { it.running }?.progress?.toDouble() ?: if (isApplied(entry)) 1.0 else 0.0 }) }
        renderResources(resources, null, true, applyAllButton,
            { if (batchState.running) batchState.progress.toDouble() else 0.0 })

    }
    override fun updatePage() {
        updateEntryButtons(); updateApplyAllAvailability()
        if (pageRevision != 0) refreshPage()
    }

    override fun shouldCloseOnEsc() = !batchInstalling && installStates.values.none { it.running }
}
