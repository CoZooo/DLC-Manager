package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.sighs.apricityui.init.Document
import net.minecraft.client.gui.GuiGraphics
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.Collections
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.CompletableFuture

class DlcServerScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    info: DlcServerInfo?,
    private val openedFromIngame: Boolean,
) : DlcManagerPageScreen(DlcUiPage.SERVER_MANAGER), DlcReturnPositionHost {
    constructor(parent: Screen?, entry: DlcServerEntry?, info: DlcServerInfo?) : this(
        parent,
        entry,
        info,
        parent is PauseScreen,
    )

    private val rows: MutableList<DlcServerListRow> = ArrayList()
    private val items: MutableList<DlcServerItem> = ArrayList()
    private val itemsByIdentifier: MutableMap<String, DlcServerItem> = LinkedHashMap()
    private val baseline: MutableMap<String?, Boolean> = HashMap()
    private var serverBaseVersion = ""
    private var serverDlcVersion = ""
    private val presetSnapshot: MutableSet<String> = LinkedHashSet()
    private var presetSnapshotCaptured = false
    private val presetAppliedIdentifiers: MutableSet<String> = LinkedHashSet()
    private val presetStatusById: MutableMap<String, DlcPresetStatus> = LinkedHashMap()
    private var savedScrollOffset = 0.0
    private var savedScrollFilter: DlcType? = null
    private var restoreScrollPending = false

    private var applyButton: DlcUiButton? = null
    private var backButton: DlcUiButton? = null

    private var activeFilter: DlcType? = null
    private var applying = false
    private var remoteMissingCount = 0
    private var requiredMissingCount = 0
    private var hasRequiredEntries = false
    private var promptedRemoteDownload = false
    private var hasServerTaczMod = false

    init {
        setInfo(info)
    }

    private fun setInfo(info: DlcServerInfo?) {
        items.clear()
        itemsByIdentifier.clear()
        baseline.clear()
        remoteMissingCount = info?.remoteMissing ?: 0
        requiredMissingCount = info?.requiredMissing ?: 0
        hasRequiredEntries = info?.hasRequired == true
        hasServerTaczMod = info?.hasTaczMod == true
        if (!hasServerTaczMod && activeFilter == DlcType.TACZ_PACK) {
            activeFilter = null
        }
        serverBaseVersion = info?.baseVersion ?: ""
        serverDlcVersion = info?.dlcVersion ?: ""
        for (item in info?.items ?: emptyList()) {
            items.add(item)
            baseline[item.id] = item.enabled
            val identifier = item.identifier
            if (!identifier.isNullOrBlank()) {
                val key = normalizeIdentifier(identifier)
                if (key.isNotBlank()) {
                    itemsByIdentifier[key] = item
                }
            }
        }
        applyPresetOverrides()
    }

    private fun rebuildRows() {
        rows.clear()
        val grouped: MutableMap<DlcType, MutableList<DlcServerItem>> = EnumMap(DlcType::class.java)
        for (item in items) {
            val type = mapType(item.type) ?: DlcType.UNKNOWN
            if (!isServerTypeVisible(type)) {
                continue
            }
            grouped.computeIfAbsent(type) { ArrayList() }.add(item)
        }
        if (activeFilter == null) {
            for (type in DlcType.values()) {
                if (!isServerTypeVisible(type)) {
                    continue
                }
                val group = grouped[type]
                if (group.isNullOrEmpty()) {
                    continue
                }
                rows.add(DlcServerListRow.groupHeader(type))
                addGroupedRows(group)
            }
        } else {
            val group = grouped[activeFilter]
            if (!group.isNullOrEmpty()) {
                addGroupedRows(group)
            }
        }
    }

    private fun addGroupedRows(group: List<DlcServerItem>) {
        val byPath = groupByRelativeFolder(group)
        val hasSubDir = byPath.keys.any { key -> key != "__root__" }
        var firstPathHeader = true
        for ((path, pathItems) in byPath) {
            if (path == "__root__") {
                if (hasSubDir) {
                    rows.add(DlcServerListRow.pathHeader(resolvePathLabel(path), pathItems, true, firstPathHeader))
                    firstPathHeader = false
                }
            } else {
                rows.add(DlcServerListRow.pathHeader(resolvePathLabel(path), pathItems, false, firstPathHeader))
                firstPathHeader = false
            }
            for (item in pathItems) {
                rows.add(DlcServerListRow.item(item))
            }
        }
    }

    private fun visibleFilterTypes(): List<DlcType> {
        return DlcType.values().filter { it != DlcType.DLC_PACK && isServerTypeVisible(it) }
    }

    private fun isServerTypeVisible(type: DlcType): Boolean {
        return type != DlcType.TACZ_PACK || hasServerTaczMod
    }

    private fun openConfig() {
        if (applying) return
        val serverEntry = entry ?: return
        Minecraft.getInstance().setScreen(DlcServerConfigScreen(this, serverEntry))
    }

    private fun openServerTools() {
        if (applying) return
        Minecraft.getInstance().setScreen(DlcServerManagerScreen(this, entry, buildInfoSnapshot(), DlcServerManagerScreen.Tab.SERVERS, openedFromIngame))
    }

    private fun openServerList() {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot)
            presetAppliedIdentifiers.clear()
            applyPresetOverrides()
        }
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent)
            return
        }
        if (openedFromIngame) {
            Minecraft.getInstance().setScreen(null)
            return
        }
        Minecraft.getInstance().setScreen(DlcServerListScreen(null))
    }

    fun getRootParent(): Screen? = parent

    fun getItemByIdentifier(identifier: String?): DlcServerItem? {
        if (identifier.isNullOrBlank()) {
            return null
        }
        return itemsByIdentifier[normalizeIdentifier(identifier)]
    }

    fun getItemsSnapshot(): List<DlcServerItem> = ArrayList(items)

    private fun buildInfoSnapshot(): DlcServerInfo {
        val info = DlcServerInfo()
        info.items = ArrayList(items)
        info.remoteMissing = remoteMissingCount
        info.requiredMissing = requiredMissingCount
        info.hasRequired = hasRequiredEntries
        info.hasTaczMod = hasServerTaczMod
        info.baseVersion = serverBaseVersion
        info.dlcVersion = serverDlcVersion
        return info
    }

    private fun openPreset() {
        if (applying) return
        capturePresetSnapshot()
        Minecraft.getInstance().setScreen(object : DlcPresetManagerScreen(DlcUiPage.SERVER_PRESETS, this@DlcServerScreen) {
            override fun onClose() {
                refreshAfterPresetChange()
                super.onClose()
            }
        })
    }

    private fun setAllEnabled(enabled: Boolean) {
        for (item in items) {
            if (!matchesVisibleItem(item)) {
                continue
            }
            item.enabled = enabled
        }
        updateApplyState()
    }

    fun handleToggle(item: DlcServerItem?) {
        if (item == null) {
            return
        }
        if (item.enabled) {
            disableWithDependents(item)
            updateApplyState()
            return
        }
        if (item.alarm) {
            val tip = getTooltipText(item)
            rememberListScroll()
            Minecraft.getInstance().setScreen(DlcAlarmConfirmScreen(this, tip, Runnable { continueEnableWithChecks(item) }))
            return
        }
        continueEnableWithChecks(item)
    }

    private fun continueEnableWithChecks(item: DlcServerItem?) {
        if (item == null) return
        val conflicts = checkConflicts(item)
        if (conflicts.conflicts.isNotEmpty()) {
            val message = DlcConfirmUtil.buildConflictPromptMessage(conflicts.conflictNames)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            continueEnableAfterConflictResolution(item)
                        }
                    },
                    Component.translatable("dlc_manager.conflict.word"),
                    message,
                    Component.translatable("dlc_manager.conflict.resolve"),
                    Component.translatable("gui.cancel"),
                ),
            )
            return
        }
        val deps = checkDependencies(item)
        if (deps.missing.isNotEmpty()) {
            val message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            for (dep in deps.missingItems) {
                                dep.enabled = true
                            }
                            item.enabled = true
                            updateApplyState()
                        }
                    },
                    Component.translatable("dlc_manager.dependency.enable"),
                    message,
                    Component.translatable("dlc_manager.dependency.enable"),
                    Component.translatable("gui.cancel"),
                ),
            )
            return
        }
        item.enabled = true
        updateApplyState()
    }

    private fun continueEnableAfterConflictResolution(item: DlcServerItem) {
        var guard = 0
        while (guard++ < 64) {
            val conflicts = checkConflicts(item)
            if (conflicts.conflicts.isEmpty() || conflicts.conflictItems.isEmpty()) {
                break
            }
            disableItemsWithDependents(conflicts.conflictItems)
        }

        val deps = checkDependencies(item)
        if (deps.missing.isNotEmpty()) {
            val message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                DlcConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            for (dep in deps.missingItems) {
                                dep.enabled = true
                            }
                            item.enabled = true
                            updateApplyState()
                        }
                    },
                    Component.translatable("dlc_manager.dependency.enable"),
                    message,
                    Component.translatable("dlc_manager.dependency.enable"),
                    Component.translatable("gui.cancel"),
                ),
            )
            return
        }
        item.enabled = true
        updateApplyState()
    }

    private fun disableWithDependents(item: DlcServerItem?) {
        if (item == null) return
        disableItemsWithDependents(listOf(item))
    }

    fun disableItemsWithDependents(itemsToDisable: Collection<DlcServerItem>?) {
        if (itemsToDisable.isNullOrEmpty()) {
            return
        }
        val toDisable: MutableSet<DlcServerItem> = LinkedHashSet()
        for (item in itemsToDisable) {
            toDisable.addAll(collectDependentsRecursive(item))
        }
        for (target in toDisable) {
            target.enabled = false
        }
    }

    private fun collectDependentsRecursive(root: DlcServerItem?): Set<DlcServerItem> {
        val result: MutableSet<DlcServerItem> = LinkedHashSet()
        if (root == null) {
            return result
        }
        val stack = ArrayDeque<DlcServerItem>()
        stack.add(root)
        while (!stack.isEmpty()) {
            val current = stack.removeFirst()
            if (!result.add(current)) {
                continue
            }
            val currentId = normalizeIdentifier(current.identifier)
            if (currentId.isBlank()) {
                continue
            }
            for (item in items) {
                val deps = item.dependencies
                if (deps.isNullOrEmpty()) {
                    continue
                }
                for (dep in deps) {
                    if (dep.isNullOrBlank()) {
                        continue
                    }
                    if (normalizeIdentifier(dep) == currentId) {
                        stack.add(item)
                        break
                    }
                }
            }
        }
        return result
    }

    fun updateApplyState() {
        val apply = applyButton ?: return
        var dirty = false
        for (item in items) {
            val baselineValue = baseline[item.id] ?: false
            if (baselineValue != item.enabled) {
                dirty = true
                break
            }
        }
        apply.setEnabled(dirty && !applying)
        backButton?.setEnabled(!applying && requiredMissingCount <= 0)
    }

    fun isDirty(item: DlcServerItem?): Boolean {
        if (item == null) return false
        val baselineValue = baseline[item.id] ?: false
        return baselineValue != item.enabled
    }

    fun getPendingSuffix(item: DlcServerItem?): String {
        if (!isDirty(item)) {
            return ""
        }
        return if (mapType(item?.type) == DlcType.MOD) {
            tr("dlc_manager.pending.restart")
        } else {
            tr("dlc_manager.pending.reload")
        }
    }

    fun resolveDisplayName(item: DlcServerItem?): String {
        if (item == null) return ""
        val displayNames = item.displayNames
        if (!displayNames.isNullOrEmpty()) {
            val lang = Minecraft.getInstance().options.languageCode
            if (displayNames.containsKey(lang)) {
                return displayNames[lang] ?: ""
            }
            if (displayNames.containsKey("en_us")) {
                return displayNames["en_us"] ?: ""
            }
            return displayNames.values.firstOrNull() ?: (item.identifier ?: "")
        }
        if (!item.name.isNullOrBlank()) {
            return item.name!!
        }
        return item.identifier ?: ""
    }

    private fun getTooltipText(item: DlcServerItem?): String? {
        val tips = item?.tips
        if (tips.isNullOrEmpty()) {
            return null
        }
        val lang = Minecraft.getInstance().options.languageCode
        if (tips.containsKey(lang)) {
            return tips[lang]
        }
        if (tips.containsKey("en_us")) {
            return tips["en_us"]
        }
        return tips.values.firstOrNull()
    }

    private fun collectDependencyNamesRecursive(item: DlcServerItem?): List<String> {
        val deps = item?.dependencies ?: return emptyList()
        val result: LinkedHashSet<String> = LinkedHashSet()
        val queue = ArrayDeque(deps)
        while (!queue.isEmpty()) {
            val id = queue.poll()
            if (id.isNullOrBlank()) {
                continue
            }
            if (result.add(id)) {
                val dep = resolveDependencyItem(id)
                val depDeps = dep?.dependencies
                if (depDeps != null) {
                    queue.addAll(depDeps)
                }
            }
        }
        return ArrayList(result)
    }

    private fun collectMissingDependencyIdentifiers(item: DlcServerItem?): Set<String> {
        val deps = item?.dependencies
        if (deps.isNullOrEmpty()) {
            return emptySet()
        }
        val missing: MutableSet<String> = LinkedHashSet()
        for (depId in deps) {
            if (depId.isNullOrBlank()) continue
            if (resolveDependencyItem(depId) == null) {
                missing.add(depId)
            }
        }
        return missing
    }

    private fun checkDependencies(item: DlcServerItem?): DependencyCheck {
        val check = DependencyCheck()
        if (item?.dependencies == null) {
            return check
        }
        val visited: MutableSet<String> = LinkedHashSet()
        val queue = ArrayDeque<DlcServerItem>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val identifier = current.identifier
            if (identifier.isNullOrBlank()) {
                continue
            }
            val currentId = normalizeIdentifier(identifier)
            if (currentId.isBlank() || !visited.add(currentId)) {
                continue
            }
            val dependencies = current.dependencies
            if (dependencies.isNullOrEmpty()) {
                continue
            }
            for (depId in dependencies) {
                if (depId.isNullOrBlank()) continue
                val dep = resolveDependencyItem(depId)
                if (dep == null || !dep.enabled) {
                    val depKey = normalizeIdentifier(depId)
                    if (!check.missing.contains(depKey)) {
                        check.missing.add(depKey)
                        check.missingNames.add(resolveDisplayName(dep))
                    }
                    if (dep != null && !check.missingItems.contains(dep)) {
                        check.missingItems.add(dep)
                    }
                }
                if (dep != null) {
                    queue.add(dep)
                }
            }
        }
        return check
    }

    private fun checkConflicts(item: DlcServerItem?): ConflictCheck {
        val check = ConflictCheck()
        if (item == null) {
            return check
        }
        val plannedIds: MutableSet<String> = LinkedHashSet()
        val visited: MutableSet<String> = LinkedHashSet()
        val queue = ArrayDeque<DlcServerItem>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val identifier = current.identifier
            if (identifier.isNullOrBlank()) {
                continue
            }
            val currentId = normalizeIdentifier(identifier)
            if (currentId.isBlank() || !visited.add(currentId)) {
                continue
            }
            plannedIds.add(currentId)
            val dependencies = current.dependencies
            for (depId in dependencies) {
                if (depId.isNullOrBlank()) continue
                val dep = resolveDependencyItem(depId)
                if (dep != null) {
                    queue.add(dep)
                }
            }
        }

        for (plannedId in plannedIds) {
            val planned = itemsByIdentifier[plannedId]
            val conflicts = planned?.conflicts
            if (planned == null || conflicts.isNullOrEmpty()) {
                continue
            }
            for (conflictId in conflicts) {
                if (conflictId.isNullOrBlank()) continue
                val conflictKey = normalizeIdentifier(conflictId)
                if (conflictKey.isBlank() || plannedIds.contains(conflictKey)) {
                    continue
                }
                val conflict = resolveDependencyItem(conflictId)
                if (conflict != null && conflict.enabled && !check.conflicts.contains(conflictKey)) {
                    check.conflicts.add(conflictKey)
                    check.conflictItems.add(conflict)
                    check.conflictNames.add(resolveDisplayName(conflict))
                }
            }
        }

        for (existing in items) {
            val identifier = existing.identifier
            if (identifier.isNullOrBlank() || !existing.enabled) {
                continue
            }
            val existingKey = normalizeIdentifier(identifier)
            if (existingKey.isBlank() || plannedIds.contains(existingKey)) {
                continue
            }
            val conflicts = existing.conflicts
            if (conflicts.isNullOrEmpty()) {
                continue
            }
            var hit = false
            for (conflictId in conflicts) {
                if (conflictId.isNullOrBlank()) continue
                val conflictKey = normalizeIdentifier(conflictId)
                if (conflictKey.isNotBlank() && plannedIds.contains(conflictKey)) {
                    hit = true
                    break
                }
            }
            if (hit && !check.conflicts.contains(existingKey)) {
                check.conflicts.add(existingKey)
                check.conflictItems.add(existing)
                check.conflictNames.add(resolveDisplayName(existing))
            }
        }
        return check
    }

    private fun resolveDependencyItem(depId: String?): DlcServerItem? {
        if (depId.isNullOrBlank()) {
            return null
        }
        val key = normalizeIdentifier(depId)
        if (key.isBlank()) {
            return null
        }
        return itemsByIdentifier[key]
    }

    private fun applyChanges() {
        if (applying) {
            return
        }
        applying = true
        pageError = null
        updateApplyState()
        val changed: MutableList<DlcServerItem> = ArrayList()
        for (item in items) {
            val baselineValue = baseline[item.id] ?: false
            if (baselineValue != item.enabled) {
                val copy = DlcServerItem()
                copy.id = item.id
                copy.enabled = item.enabled
                changed.add(copy)
            }
        }
        CompletableFuture
            .supplyAsync { applyRemoteChanges(changed) }
            .whenComplete { response, _ ->
                Minecraft.getInstance().execute {
                    applying = false
                    if (response == null || !response.ok) pageError = tr("dlc_manager.server.apply_failed")
                    if (response != null && response.ok) {
                        for (item in items) {
                            baseline[item.id] = item.enabled
                        }
                        if (DlcConfig.isPresetFuncOn()) {
                            syncPresetStatusFromItems()
                            updatePresetSnapshot()
                        }
                    }
                    updateApplyState()
                    if (response != null && response.ok && response.requiresRestart) {
                        promptServerRestart()
                    }
                }
            }
    }

    private fun applyRemoteChanges(changes: List<DlcServerItem>): DlcServerConnection.ApplyResponse? {
        return try {
            DlcServerConnection.applyChangesWithResponse(entry, 8000, changes)
        } catch (_: Exception) {
            null
        }
    }

    private fun promptServerRestart() {
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { result ->
                    if (result) {
                        CompletableFuture.runAsync {
                            try {
                                DlcServerConnection.restartServer(entry, 8000)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    Minecraft.getInstance().setScreen(this)
                },
                Component.translatable("dlc_manager.server.restart_title"),
                Component.translatable("dlc_manager.server.restart_prompt"),
            ),
        )
    }

    private fun applyPresetOverrides() {
        if (!DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.clearAll()
            presetAppliedIdentifiers.clear()
            presetStatusById.clear()
            return
        }
        if (DlcPresetRuntimeState.getPresets().isEmpty()) {
            DlcPresetRuntimeState.reload()
        }
        val entries = DlcPresetRuntimeState.getPresets()
        val enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds()
        updatePresetStatusFromItems()
        if (enabledPresets.isEmpty() || entries.isEmpty()) {
            if (presetAppliedIdentifiers.isNotEmpty()) {
                for (identifier in presetAppliedIdentifiers) {
                    val item = getItemByIdentifier(identifier)
                    if (item != null) {
                        item.enabled = false
                    }
                }
                presetAppliedIdentifiers.clear()
            }
            return
        }
        val byId: MutableMap<String, DlcPresetEntry> = HashMap()
        for (presetEntry in entries) {
            val id = presetEntry.getId()
            if (id != null) {
                byId[id] = presetEntry
            }
        }
        val desiredEnable: MutableSet<String> = LinkedHashSet()
        var desiredDisable: MutableSet<String> = LinkedHashSet()
        val enableCache: MutableMap<String, Set<String>> = LinkedHashMap()
        val conflictCache: MutableMap<String, Set<String>> = LinkedHashMap()
        for (id in enabledPresets) {
            val preset = byId[id] ?: continue
            val partial = preset.getStatus() == DlcPresetStatus.PART
            for (enable in resolvePresetEnableIdentifiers(preset.getId(), byId, enableCache, LinkedHashSet())) {
                val key = normalizeIdentifier(enable)
                if (key.isBlank()) continue
                if (partial) {
                    val item = getItemByIdentifier(key)
                    if (item != null && item.enabled) {
                        desiredEnable.add(key)
                    }
                } else {
                    desiredEnable.add(key)
                }
            }
            for (conflict in resolvePresetConflictIdentifiers(preset.getId(), byId, enableCache, conflictCache, LinkedHashSet(), LinkedHashSet())) {
                val key = normalizeIdentifier(conflict)
                if (key.isNotBlank()) {
                    desiredDisable.add(key)
                }
            }
        }
        desiredDisable.addAll(collectInfoConflicts(desiredEnable))
        desiredDisable = expandDisabledByDependents(desiredDisable).toMutableSet()
        desiredEnable.removeAll(desiredDisable)
        val desiredAll: MutableSet<String> = LinkedHashSet(desiredEnable)
        desiredAll.addAll(desiredDisable)

        val toDisable: MutableSet<String> = LinkedHashSet(presetAppliedIdentifiers)
        toDisable.removeAll(desiredAll)
        for (identifier in desiredEnable) {
            getItemByIdentifier(identifier)?.enabled = true
        }
        for (identifier in desiredDisable) {
            getItemByIdentifier(identifier)?.enabled = false
        }
        for (identifier in toDisable) {
            val item = getItemByIdentifier(identifier)
            if (item != null) {
                item.enabled = baseline[item.id] ?: false
            }
        }
        presetAppliedIdentifiers.clear()
        presetAppliedIdentifiers.addAll(desiredAll)
    }

    private fun collectInfoConflicts(enabledIdentifiers: Set<String>?): MutableSet<String> {
        val result: MutableSet<String> = LinkedHashSet()
        if (enabledIdentifiers.isNullOrEmpty()) {
            return result
        }
        for (identifier in enabledIdentifiers) {
            if (identifier.isBlank()) continue
            val item = getItemByIdentifier(identifier)
            val conflicts = item?.conflicts
            if (conflicts.isNullOrEmpty()) continue
            for (conflict in conflicts) {
                val key = normalizeIdentifier(conflict)
                if (key.isBlank()) continue
                if (enabledIdentifiers.contains(key)) continue
                result.add(key)
            }
        }
        return result
    }

    private fun expandDisabledByDependents(disabledIdentifiers: Set<String>?): MutableSet<String> {
        val expanded: MutableSet<String> = LinkedHashSet()
        if (disabledIdentifiers.isNullOrEmpty()) {
            return expanded
        }
        val queue = ArrayDeque<DlcServerItem>()
        for (identifier in disabledIdentifiers) {
            if (identifier.isBlank()) continue
            expanded.add(identifier)
            val root = getItemByIdentifier(identifier)
            if (root != null) {
                queue.add(root)
            }
        }
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val currentIdentifier = current.identifier
            if (currentIdentifier.isNullOrBlank()) continue
            for (dependent in collectDependentsRecursive(current)) {
                val dependentIdentifier = dependent.identifier
                if (dependentIdentifier.isNullOrBlank()) continue
                val key = normalizeIdentifier(dependentIdentifier)
                if (key.isNotBlank() && expanded.add(key)) {
                    queue.add(dependent)
                }
            }
        }
        return expanded
    }

    fun refreshAfterPresetChange() {
        applyPresetOverrides()
        rebuildRows()
        refreshManagerPage()
        
        updateApplyState()
    }

    fun getPresetStatus(presetId: String?): DlcPresetStatus {
        if (presetId.isNullOrBlank()) {
            return DlcPresetStatus.OFF
        }
        updatePresetStatusFromItems()
        return presetStatusById.getOrDefault(presetId, DlcPresetStatus.OFF)
    }

    private fun updatePresetStatusFromItems(): MutableMap<String, DlcPresetStatus> {
        presetStatusById.clear()
        if (!DlcConfig.isPresetFuncOn()) {
            return presetStatusById
        }
        val entries = DlcPresetRuntimeState.getPresets()
        val enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds()
        val byId: MutableMap<String, DlcPresetEntry> = LinkedHashMap()
        for (entry in entries) {
            val id = entry.getId()
            if (!id.isNullOrBlank()) {
                byId[id] = entry
            }
        }
        val enableCache: MutableMap<String, Set<String>> = LinkedHashMap()
        for (entry in entries) {
            val status = if (!enabledPresets.contains(entry.getId())) {
                DlcPresetStatus.OFF
            } else {
                var allEnabled = true
                for (identifier in resolvePresetEnableIdentifiers(entry.getId(), byId, enableCache, LinkedHashSet())) {
                    if (identifier.isBlank()) continue
                    val item = resolveDependencyItem(identifier)
                    if (item == null || !item.enabled) {
                        allEnabled = false
                        break
                    }
                }
                if (allEnabled) DlcPresetStatus.ON else DlcPresetStatus.PART
            }
            val id = entry.getId()
            if (id != null) {
                presetStatusById[id] = status
            }
        }
        return presetStatusById
    }

    private fun resolvePresetEnableIdentifiers(
        presetId: String?,
        byId: Map<String, DlcPresetEntry>,
        cache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
    ): Set<String> {
        if (presetId.isNullOrBlank()) return emptySet()
        cache[presetId]?.let { return it }
        if (!stack.add(presetId)) return emptySet()
        val preset = byId[presetId]
        if (preset == null) {
            stack.remove(presetId)
            return emptySet()
        }
        val result: MutableSet<String> = LinkedHashSet()
        for (token in preset.getEnable()) {
            val value = token?.trim() ?: ""
            if (value.isBlank()) continue
            val refPresetId = resolvePresetReference(value, byId)
            if (refPresetId != null) {
                result.addAll(resolvePresetEnableIdentifiers(refPresetId, byId, cache, stack))
            } else {
                result.add(value)
            }
        }
        stack.remove(presetId)
        val unmodifiable = Collections.unmodifiableSet(LinkedHashSet(result))
        cache[presetId] = unmodifiable
        return unmodifiable
    }

    private fun resolvePresetConflictIdentifiers(
        presetId: String?,
        byId: Map<String, DlcPresetEntry>,
        enableCache: MutableMap<String, Set<String>>,
        conflictCache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
        enableStack: MutableSet<String>,
    ): Set<String> {
        if (presetId.isNullOrBlank()) return emptySet()
        conflictCache[presetId]?.let { return it }
        if (!stack.add(presetId)) return emptySet()
        val preset = byId[presetId]
        if (preset == null) {
            stack.remove(presetId)
            return emptySet()
        }
        val result: MutableSet<String> = LinkedHashSet()
        for (token in preset.getConflicts()) {
            val value = token?.trim() ?: ""
            if (value.isBlank()) continue
            val refPresetId = resolvePresetReference(value, byId)
            if (refPresetId != null) {
                result.addAll(resolvePresetEnableIdentifiers(refPresetId, byId, enableCache, enableStack))
            } else {
                result.add(value)
            }
        }
        stack.remove(presetId)
        val unmodifiable = Collections.unmodifiableSet(LinkedHashSet(result))
        conflictCache[presetId] = unmodifiable
        return unmodifiable
    }

    private fun resolvePresetReference(token: String?, byId: Map<String, DlcPresetEntry>): String? {
        if (token.isNullOrBlank()) return null
        var forcedPreset = false
        var value = token.trim()
        if (value.regionMatches(0, PRESET_REF_PREFIX, 0, PRESET_REF_PREFIX.length, ignoreCase = true)) {
            forcedPreset = true
            value = value.substring(PRESET_REF_PREFIX.length).trim()
            if (value.isBlank()) return null
        }
        val presetId = findPresetId(value, byId) ?: return null
        if (!forcedPreset && getItemByIdentifier(value) != null) {
            return null
        }
        return presetId
    }

    private fun findPresetId(value: String, byId: Map<String, DlcPresetEntry>): String? {
        val exact = byId[value]
        if (!exact?.getId().isNullOrBlank()) {
            return exact!!.getId()
        }
        for (entry in byId.values) {
            val id = entry.getId()
            if (id.isNullOrBlank()) continue
            if (id.equals(value, ignoreCase = true)) return id
            val fileName = entry.getFileName()
            if (!fileName.isNullOrBlank() && fileName.equals(value, ignoreCase = true)) {
                return id
            }
        }
        return null
    }

    private fun syncPresetStatusFromItems() {
        val statuses = updatePresetStatusFromItems()
        DlcPresetRuntimeState.syncStatusFromExternal(statuses)
    }

    private fun capturePresetSnapshot() {
        if (presetSnapshotCaptured) return
        updatePresetSnapshot()
    }

    private fun updatePresetSnapshot() {
        presetSnapshot.clear()
        if (DlcConfig.isPresetFuncOn()) {
            presetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds())
        }
        presetSnapshotCaptured = true
    }

    private fun hasPresetChanges(): Boolean {
        if (!DlcConfig.isPresetFuncOn()) return false
        if (!presetSnapshotCaptured) return false
        return DlcPresetRuntimeState.getEnabledPresetIds() != presetSnapshot
    }

    private fun promptRemoteDownloadIfMissing() {
        if (remoteMissingCount <= 0) {
            REMOTE_PROMPTED.remove(getPromptKey())
            return
        }
        if (promptedRemoteDownload || REMOTE_PROMPTED.contains(getPromptKey())) {
            return
        }
        promptedRemoteDownload = true
        REMOTE_PROMPTED.add(getPromptKey())
        startRemoteDownload()
    }

    private fun getPromptKey(): String = entry?.getKey() ?: ""

    private fun startRemoteDownload() {
        Minecraft.getInstance().setScreen(DlcServerRemoteDownloadScreen(this, entry))
    }

    fun refreshServerInfo() {
        CompletableFuture
            .supplyAsync { fetchInfo(entry) }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    if (info != null) {
                        setInfo(info)
                        rebuildRows()
                        refreshManagerPage()
                        
                    }
                }
            }
    }

    private fun fetchInfo(entry: DlcServerEntry?): DlcServerInfo? {
        return try {
            DlcServerConnection.fetchServerInfo(entry, 5000)
        } catch (_: Exception) {
            null
        }
    }

    override fun rememberReturnPosition() {
        rememberListScroll()
    }

    private fun groupByRelativeFolder(items: List<DlcServerItem>): Map<String, List<DlcServerItem>> {
        val grouped: MutableMap<String, MutableList<DlcServerItem>> = java.util.TreeMap()
        for (item in items) {
            val label = if (item.path.isNullOrBlank()) "__root__" else item.path!!
            grouped.computeIfAbsent(label) { ArrayList() }.add(item)
        }
        return grouped
    }

    private fun resolvePathLabel(raw: String): String {
        if (raw == "__root__") {
            return tr("dlc_manager.group.root")
        }
        return raw
    }

    private class DependencyCheck {
        val missing: MutableList<String> = ArrayList()
        val missingNames: MutableList<String> = ArrayList()
        val missingItems: MutableList<DlcServerItem> = ArrayList()
    }

    private class ConflictCheck {
        val conflicts: MutableList<String> = ArrayList()
        val conflictNames: MutableList<String> = ArrayList()
        val conflictItems: MutableList<DlcServerItem> = ArrayList()
    }

    companion object {
        private const val PRESET_REF_PREFIX = "preset:"
        private val REMOTE_PROMPTED: MutableSet<String> = HashSet()

        private fun normalizeIdentifier(identifier: String?): String {
            if (identifier == null) return ""
            return identifier.trim().lowercase(Locale.ROOT)
        }

        private fun mapType(type: String?): DlcType? {
            if (type == null) return null
            val lower = type.lowercase(Locale.ROOT)
            if (lower.contains("datapack") || lower == "data_pack" || lower == "data") return DlcType.DATA_PACK
            if (lower.contains("resourcepack") || lower == "resource_pack" || lower == "resource") return DlcType.RESOURCE_PACK
            if (lower.contains("tacz")) return DlcType.TACZ_PACK
            if (lower == "mods" || lower == "mod") return DlcType.MOD
            for (dlcType in DlcType.values()) {
                if (dlcType == DlcType.DLC_PACK) {
                    continue
                }
                val id = when (dlcType) {
                    DlcType.MOD -> "mod"
                    DlcType.RESOURCE_PACK -> "resource"
                    DlcType.DATA_PACK -> "data"
                    DlcType.TACZ_PACK -> "tacz"
                    DlcType.DLC_PACK -> "dlcpack"
                    DlcType.UNKNOWN -> "other"
                }
                if (id.equals(type, ignoreCase = true) || dlcType.name.equals(type, ignoreCase = true)) {
                    return dlcType
                }
            }
            return DlcType.UNKNOWN
        }
    }

    private var prepared = false
    private var enableAllButton: DlcUiButton? = null
    private var disableAllButton: DlcUiButton? = null
    private var pageError: String? = null
    private var controlsLocked: Boolean? = null
    private var renderedState: List<Boolean> = emptyList()

    override fun getTitle(): Component = Component.translatable("dlc_manager.server.title")

    override fun init() {
        capturePresetSnapshot()
        super.init()
        if (!prepared) {
            prepared = true
            promptRemoteDownloadIfMissing()
        }
    }

    override fun bindDocument(document: Document) {
        bindManagerLayout(document, tr("dlc_manager.server.title"))
        document.getElementById("manager-header").setAttribute("class", "workspace-header connected")
        val connection = document.getElementById("manager-connection")
        clearUiContent(connection)
        connection.setAttribute("style", "display:flex")
        managerText(document, connection, "span", entry?.getDisplayName().orEmpty(), "connection-name")
        managerText(document, connection, "span", listOf(serverBaseVersion, serverDlcVersion).filter { it.isNotBlank() }.joinToString(" · "), "connection-version")
        val context = document.getElementById("manager-context")
        clearUiContent(context)
        if (entry != null) button(document, context, tr("dlc_manager.server.config")) { openConfig() }
        val tools = document.getElementById("manager-tools")
        clearUiContent(tools)
        if (DlcConfig.isPresetFuncOn()) button(document, tools, tr("dlc_manager.preset.manager")) { openPreset() }
        button(document, tools, tr("dlc_manager.server.tab.servers")) { openServerTools() }
        for (tool in tools.children) tool.setAttribute("class", "button nav-button")
        val batch = document.getElementById("batch-actions")
        clearUiContent(batch)
        if (remoteMissingCount > 0) button(document, batch, tr("dlc_manager.server.remote_missing_button")) { startRemoteDownload() }
        enableAllButton = if (DlcConfig.isEnableAllButtonShown()) button(document, batch, "") { confirmBatchToggle(true) } else null
        disableAllButton = button(document, batch, "") { confirmBatchToggle(false) }
        val mode = document.getElementById("manager-mode")
        clearUiContent(mode)
        mode.setAttribute("style", "display:none")
        val actions = document.getElementById("manager-actions")
        clearUiContent(actions)
        backButton = button(document, actions, tr("gui.back")) { onClose() }
        applyButton = button(document, actions, tr("dlc_manager.apply")) { applyChanges() }
        applyButton!!.getElement().setAttribute("class", "button primary")
        renderFilters(document)
        renderServerRows(document)
        restoreScrollPending = savedScrollFilter == activeFilter
        if (searchQuery.isNotBlank()) onManagerSearch(document, searchQuery)
    }

    private fun refreshManagerPage() {
        linkedDocument?.let { document ->
            if (document.isActive) Document.runWithContext(document) { bindDocument(document) }
        }
    }

    private fun renderFilters(document: Document) {
        val filters = document.getElementById("manager-filters")
        clearUiContent(filters)
        for (type in listOf(null) + visibleFilterTypes()) {
            val filter = button(document, filters, tr(type?.getDisplayKey() ?: "dlc_manager.filter.all")) {
                if (!applying) {
                    activeFilter = type
                    savedScrollOffset = 0.0
                    savedScrollFilter = type
                    document.getElementById("manager-list").setScrollTop(0.0)
                    renderFilters(document)
                    renderServerRows(document)
                }
            }
            filter.getElement().setAttribute("class", "button filter" + if (activeFilter == type) " active" else "")
            filter.getElement().setAttribute("aria-pressed", (activeFilter == type).toString())
            filter.getElement().setAttribute("aria-selected", (activeFilter == type).toString())
        }
    }

    private var searchMatches = emptySet<String>()
    override fun onManagerSearch(document: Document, query: String?) {
        if (applying) return
        searchQuery = query.orEmpty()
        searchMatches = emptySet()
        savedScrollOffset = 0.0
        val list = document.getElementById("manager-list")
        list.setScrollTop(0.0); list.setAttribute("style", "pointer-events:none"); list.setAttribute("aria-busy", "true")
        val apply = java.util.function.Consumer<Set<String>> { matches ->
            searchMatches = matches; list.removeAttribute("style"); list.removeAttribute("aria-busy")
            renderServerRows(document); updateManagerControls(document)
        }
        if (searchQuery.isBlank()) {
            filterAsync("", emptyList(), apply)
            return
        }
        val entries = items.map { DlcUiAsyncSearch.Entry(it.id.orEmpty(), DlcTextFormatter.parse(resolveDisplayName(it)).string, it.identifier, it.path) }
        updateManagerControls(document)
        filterAsync(searchQuery, entries, apply)
    }
    private fun matchesItem(item: DlcServerItem): Boolean = !item.hidden && (searchQuery.isBlank() || item.id.orEmpty() in searchMatches)

    private fun matchesVisibleItem(item: DlcServerItem): Boolean = isServerTypeVisible(mapType(item.type) ?: DlcType.UNKNOWN) &&
        (activeFilter == null || mapType(item.type) == activeFilter) && matchesItem(item)

    private fun renderServerRows(document: Document) {
        tooltip?.hide()
        val list = document.getElementById("manager-list")
        val scroll = list.targetScrollTop
        clearUiContent(list)
        clearManagerGroups()
        rebuildRows()
        var parent = list
        var count = 0
        var enabled = 0
        for (row in rows) {
            if (row.getType() != DlcServerListRow.RowType.ITEM) parent = list
            when (row.getType()) {
                DlcServerListRow.RowType.GROUP_HEADER -> {
                    if (items.any { mapType(it.type) == row.getGroupType() && matchesItem(it) })
                        managerText(document, list, "div", tr(row.getGroupType()!!.getDisplayKey()), "group-heading")
                }
                DlcServerListRow.RowType.PATH_HEADER -> {
                    val groupItems = row.getItems().orEmpty().filter { matchesItem(it) }
                    if (groupItems.isEmpty()) continue
                    val heading = managerText(document, list, "div", "", "path-heading")
                    managerText(document, heading, "span", row.getLabel(), "path-label")
                    if (!row.isRootPath()) button(document, heading, tr(if (groupItems.all { it.enabled }) "dlc_manager.disable_all" else "dlc_manager.enable_all")) {
                        if (!applying) {
                            val enable = groupItems.any { !it.enabled }
                            groupItems.forEach { it.enabled = enable }
                            renderServerRows(document)
                        }
                    }
                    parent = addManagerGroup(document, list, heading, (groupItems.first().type.orEmpty()) + ":" + row.getLabel(), groupItems.size)
                }
                DlcServerListRow.RowType.ITEM -> {
                    val item = row.getItem() ?: continue
                    if (!matchesItem(item)) continue
                    count++
                    if (item.enabled) enabled++
                    val line = parent.appendChild(document.createElement("div"))
                    val name = styleManagerRow(document, line, resolveDisplayName(item), item.enabled, isDirty(item))
                    if (!item.redirectUrl.isNullOrBlank()) {
                        val generation = document.refreshGeneration
                        name.addEventListener("dblclick") {
                            if (!applying && Minecraft.getInstance().screen === this && document.isCurrentGeneration(generation)) {
                                rememberListScroll()
                                Minecraft.getInstance().setScreen(DlcRedirectConfirmScreen(this, item.redirectUrl, ""))
                            }
                        }
                    }
                    tooltip?.bind(line) { serverTooltip(item) }
                    if (isDirty(item)) managerText(document, line, "span", getPendingSuffix(item), "pending-label")
                    managerToggle(document, line, item.enabled) { if (!applying) handleToggle(item) }.setEnabled(!applying)
                }
            }
        }
        if (count == 0) managerText(document, list, "p", tr(if (searchQuery.isBlank()) "dlc_manager.ui.empty" else "dlc_manager.ui.no_results"), "empty-state")
        document.getElementById("manager-summary").setTextContent(tr("dlc_manager.ui.summary", count, enabled))
        renderedState = items.flatMap { listOf(it.enabled, isDirty(it)) }
        controlsLocked = null
        updateManagerControls(document)
        updateAnimations(document, System.nanoTime())
        list.setScrollTop(scroll)
    }

    private fun serverTooltip(item: DlcServerItem): DlcUiTooltip.Content {
        val details = ArrayList<DlcUiTooltip.Detail>()
        val blockers = DlcPresetRuntimeState.getDlcBlockerNames(item.identifier, Minecraft.getInstance().options.languageCode)
        if (blockers.isNotEmpty()) details.add(DlcUiTooltip.Detail("", blockers.joinToString("\n") { tr("dlc_manager.preset.conflict", it) }, true))
        val missing = collectMissingDependencyIdentifiers(item); if (missing.isNotEmpty()) details.add(DlcUiTooltip.Detail(tr("dlc_manager.tooltip.missing_dependencies"), missing.joinToString("\n") { resolveDisplayName(resolveDependencyItem(it)) }, true))
        val deps = collectDependencyNamesRecursive(item); if (deps.isNotEmpty()) details.add(DlcUiTooltip.Detail(tr("dlc_manager.tooltip.dependencies"), deps.joinToString("\n") { resolveDisplayName(resolveDependencyItem(it)) }, false))
        if (item.conflicts.isNotEmpty()) details.add(DlcUiTooltip.Detail(tr("dlc_manager.tooltip.conflicts"), item.conflicts.joinToString("\n") { resolveDisplayName(resolveDependencyItem(it)) }, true))
        return DlcUiTooltip.Content(resolveDisplayName(item), getTooltipText(item).orEmpty(), details, if (!item.redirectUrl.isNullOrBlank()) tr("dlc_manager.tooltip.double_click_redirect") else getPendingSuffix(item))
    }
    private fun updateManagerControls(document: Document) {
        if (controlsLocked != applying) {
            controlsLocked = applying
            val managed = listOfNotNull(applyButton, backButton, enableAllButton, disableAllButton, searchClearButton).map { it.getElement() }
            for (element in document.querySelectorAll("#manager button, #manager input")) {
                if (element in managed) continue
                if (applying) element.setAttribute("disabled", "") else element.removeAttribute("disabled")
            }
        }
        updateApplyState()
        val visible = items.filter { matchesVisibleItem(it) }
        val scope = if (searchQuery.isNotBlank()) tr("dlc_manager.scope.search") else activeFilter?.let { tr(it.getDisplayKey()) }
        val allEnabled = visible.isNotEmpty() && visible.all { it.enabled }
        val allDisabled = visible.isNotEmpty() && visible.none { it.enabled }
        fun label(enable: Boolean, done: Boolean): String {
            val key = if (enable) (if (done) "dlc_manager.all_enabled" else "dlc_manager.enable_all") else (if (done) "dlc_manager.all_disabled" else "dlc_manager.disable_all")
            return if (scope == null) tr(key) else tr(key + "_type", scope)
        }
        enableAllButton?.setDisplayString(label(true, allEnabled))
        disableAllButton?.setDisplayString(label(false, allDisabled))
        enableAllButton?.setEnabled(!applying && visible.isNotEmpty() && !allEnabled)
        disableAllButton?.setEnabled(!applying && visible.isNotEmpty() && !allDisabled)
        searchClearButton?.setEnabled(!applying && searchQuery.isNotBlank())
        val count = items.count { isDirty(it) }
        val status = document.getElementById("pending-status")
        val message = if (applying) tr("dlc_manager.server.applying") else pageError ?: tr(if (count > 0) "dlc_manager.ui.pending" else "dlc_manager.ui.clean", count)
        if (status.textContent != message) status.setTextContent(message)
        val classes = if (count > 0 || applying || pageError != null) "pending" else ""
        if (status.getAttribute("class") != classes) status.setAttribute("class", classes)
    }

    private fun confirmBatchToggle(enable: Boolean) {
        if (applying) return
        rememberListScroll()
        val scope = if (searchQuery.isNotBlank()) Component.translatable("dlc_manager.scope.search") else activeFilter?.let { Component.translatable(it.getDisplayKey()) }
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) setAllEnabled(enable)
        }, getTitle(), DlcConfirmUtil.buildHeaderConfirmMessage(enable, scope), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()))
    }

    override fun tick() {
        super.tick()
        val document = linkedDocument ?: return
        if (!document.isActive) return
        Document.runWithContext(document) {
            rememberListScroll()
            if (renderedState != items.flatMap { listOf(it.enabled, isDirty(it)) } || (controlsLocked == true && !applying)) renderServerRows(document)
            else updateManagerControls(document)
        }
    }

    private fun rememberListScroll() {
        if (restoreScrollPending) return
        linkedDocument?.let { savedScrollOffset = it.getElementById("manager-list").targetScrollTop }
        savedScrollFilter = activeFilter
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        rememberListScroll()
        restoreScrollPending = savedScrollFilter == activeFilter
        super.resize(minecraft, width, height)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val document = linkedDocument ?: return
        if (!restoreScrollPending || document.isInitialCommitSlicing) return
        val list = document.getElementById("manager-list")
        if (list.clientHeight > 0 && list.computedStyle.overflowY == "auto") {
            Document.runWithContext(document) { list.setScrollTop(savedScrollOffset) }
            restoreScrollPending = false
        }
    }

    private fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string

    override fun onClose() {
        if (applying || requiredMissingCount > 0) return
        if (items.any { isDirty(it) }) Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted -> if (accepted) { for (item in items) item.enabled = baseline[item.id] ?: false; openServerList() } else Minecraft.getInstance().setScreen(this) }, Component.translatable("dlc_manager.config.cancel_title"), Component.translatable("dlc_manager.config.cancel_prompt")))
        else openServerList()
    }
    override fun shouldCloseOnEsc() = !applying && requiredMissingCount <= 0
}
