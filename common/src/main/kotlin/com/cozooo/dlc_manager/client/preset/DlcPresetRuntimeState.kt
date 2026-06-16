package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcRuntimeState

object DlcPresetRuntimeState {
    private const val PRESET_REF_PREFIX = "preset:"
    private val PRESETS_BY_ID: MutableMap<String, DlcPresetEntry> = LinkedHashMap()
    private val ENABLED_PRESETS: MutableSet<String> = LinkedHashSet()
    private val LAST_APPLIED_IDENTIFIERS: MutableSet<String> = LinkedHashSet()
    private val BASELINE_DISABLED_RELATIVE_PATHS: MutableMap<String, Set<String>> = LinkedHashMap()
    private val APPLIED_DISABLED_RELATIVE_PATHS: MutableMap<String, Set<String>> = LinkedHashMap()
    private var BLOCKED_DLC: MutableMap<String, Set<String>> = LinkedHashMap()
    private var BLOCKED_PRESET: MutableMap<String, Set<String>> = LinkedHashMap()
    private var presetDirty = false
    private var baselineDisabledRelativePathsInitialized = false

    @JvmStatic
    fun reload() {
        val previousEnabled = if (presetDirty) LinkedHashSet(ENABLED_PRESETS) else null
        val entries = DlcPresetLoader.loadAll()
        PRESETS_BY_ID.clear()
        for (entry in entries) {
            val id = entry?.getId()
            if (!id.isNullOrBlank()) {
                PRESETS_BY_ID[id] = entry
            }
        }
        ENABLED_PRESETS.clear()
        if (presetDirty && previousEnabled != null) {
            for (entry in PRESETS_BY_ID.values) {
                val id = entry.getId()
                if (!id.isNullOrBlank() && previousEnabled.contains(id)) {
                    ENABLED_PRESETS.add(id)
                    entry.setStatus(DlcPresetStatus.ON)
                } else {
                    entry.setStatus(DlcPresetStatus.OFF)
                }
            }
        } else {
            val saved = DlcPresetStateStore.loadPresetStatus()
            for (entry in PRESETS_BY_ID.values) {
                val id = entry.getId()
                val status = saved.getOrDefault(id, DlcPresetStatus.OFF)
                entry.setStatus(status)
                if (!id.isNullOrBlank() && status.isEnabled()) {
                    ENABLED_PRESETS.add(id)
                }
            }
            presetDirty = false
        }
        if (!baselineDisabledRelativePathsInitialized) {
            val baseline = normalizeRelativePathMap(getDesiredDisabledRelativePathsByPreset())
            BASELINE_DISABLED_RELATIVE_PATHS.clear()
            BASELINE_DISABLED_RELATIVE_PATHS.putAll(baseline)
            APPLIED_DISABLED_RELATIVE_PATHS.clear()
            APPLIED_DISABLED_RELATIVE_PATHS.putAll(copyRelativePathMap(baseline))
            baselineDisabledRelativePathsInitialized = true
        }
        filterAppliedDisabledRelativePaths()
        updateEntryStatusesFromRuntime()
        rebuildBlocks()
        applyOverrides()
    }

    @JvmStatic
    fun getPresets(): List<DlcPresetEntry> = ArrayList(PRESETS_BY_ID.values)

    @JvmStatic
    fun hasPreset(id: String?): Boolean {
        return !id.isNullOrBlank() && PRESETS_BY_ID.containsKey(id)
    }

    @JvmStatic
    fun getPresetById(id: String?): DlcPresetEntry? {
        if (id.isNullOrBlank()) {
            return null
        }
        return PRESETS_BY_ID[id]
    }

    @JvmStatic
    fun findPresetId(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val trimmed = value.trim()
        val exact = PRESETS_BY_ID[trimmed]
        if (exact != null && !exact.getId().isNullOrBlank()) {
            return exact.getId()
        }
        for (entry in PRESETS_BY_ID.values) {
            val id = entry.getId()
            if (!id.isNullOrBlank() && id.equals(trimmed, ignoreCase = true)) {
                return id
            }
            val fileName = entry.getFileName()
            if (!fileName.isNullOrBlank() && fileName.equals(trimmed, ignoreCase = true)) {
                return id
            }
        }
        return null
    }

    @JvmStatic
    fun findEnabledPresetId(value: String?): String? {
        val presetId = findPresetId(value) ?: return null
        return if (ENABLED_PRESETS.contains(presetId)) presetId else null
    }

    @JvmStatic
    fun isPresetEnabled(id: String?): Boolean = id != null && ENABLED_PRESETS.contains(id)

    @JvmStatic
    fun getPresetStatus(id: String?): DlcPresetStatus {
        if (id.isNullOrBlank()) {
            return DlcPresetStatus.OFF
        }
        val entry = PRESETS_BY_ID[id] ?: return DlcPresetStatus.OFF
        if (!ENABLED_PRESETS.contains(id)) {
            return DlcPresetStatus.OFF
        }
        return computeStatus(entry)
    }

    @JvmStatic
    fun getEnabledPresetIds(): Set<String> = LinkedHashSet(ENABLED_PRESETS)

    @JvmStatic
    fun restoreEnabledPresets(presetIds: Set<String?>?) {
        ENABLED_PRESETS.clear()
        if (presetIds != null) {
            for (id in presetIds) {
                if (id != null && PRESETS_BY_ID.containsKey(id)) {
                    ENABLED_PRESETS.add(id)
                }
            }
        }
        updateEntryStatusesFromRuntime()
        rebuildBlocks()
        applyOverrides()
        presetDirty = false
    }

    @JvmStatic
    fun setPresetEnabled(id: String?, enabled: Boolean): Boolean {
        if (id.isNullOrBlank()) {
            return false
        }
        if (enabled) {
            if (isPresetBlocked(id)) {
                return false
            }
            if (!ENABLED_PRESETS.add(id)) {
                return true
            }
        } else {
            if (!ENABLED_PRESETS.remove(id)) {
                return true
            }
        }
        updateEntryStatus(id, if (enabled) DlcPresetStatus.ON else DlcPresetStatus.OFF)
        rebuildBlocks()
        applyOverrides()
        presetDirty = true
        return true
    }

    @JvmStatic
    fun isPresetBlocked(presetId: String?): Boolean {
        if (presetId.isNullOrBlank()) {
            return false
        }
        if (ENABLED_PRESETS.contains(presetId)) {
            return false
        }
        val blockers = BLOCKED_PRESET[presetId]
        return blockers != null && blockers.isNotEmpty()
    }

    @JvmStatic
    fun getPresetBlockerNames(presetId: String?, lang: String?): List<String> {
        val blockers = BLOCKED_PRESET[presetId]
        if (blockers == null || blockers.isEmpty()) {
            return emptyList()
        }
        val names = ArrayList<String>()
        for (id in blockers) {
            val entry = PRESETS_BY_ID[id]
            if (entry != null) {
                entry.resolveDisplayName(lang)?.let { names.add(it) }
            }
        }
        return names
    }

    @JvmStatic
    fun isDlcBlocked(identifier: String?): Boolean {
        if (identifier.isNullOrBlank()) {
            return false
        }
        val blockers = BLOCKED_DLC[identifier]
        return blockers != null && blockers.isNotEmpty()
    }

    @JvmStatic
    fun getDlcBlockerNames(identifier: String?, lang: String?): List<String> {
        val blockers = BLOCKED_DLC[identifier]
        if (blockers == null || blockers.isEmpty()) {
            return emptyList()
        }
        val names = ArrayList<String>()
        for (id in blockers) {
            val entry = PRESETS_BY_ID[id]
            if (entry != null) {
                entry.resolveDisplayName(lang)?.let { names.add(it) }
            }
        }
        return names
    }

    @JvmStatic
    fun clearAll() {
        rollbackLastAppliedToBaseline()
        PRESETS_BY_ID.clear()
        ENABLED_PRESETS.clear()
        LAST_APPLIED_IDENTIFIERS.clear()
        BASELINE_DISABLED_RELATIVE_PATHS.clear()
        APPLIED_DISABLED_RELATIVE_PATHS.clear()
        BLOCKED_DLC = LinkedHashMap()
        BLOCKED_PRESET = LinkedHashMap()
        presetDirty = false
        baselineDisabledRelativePathsInitialized = false
    }

    @JvmStatic
    fun syncStatusFromRuntime() {
        if (PRESETS_BY_ID.isEmpty()) {
            return
        }
        val statusMap = LinkedHashMap<String, DlcPresetStatus>()
        for (entry in PRESETS_BY_ID.values) {
            val id = entry.getId()
            val status = if (!id.isNullOrBlank() && ENABLED_PRESETS.contains(id)) computeStatus(entry) else DlcPresetStatus.OFF
            if (status != entry.getStatus()) {
                entry.setStatus(status)
            }
            if (!id.isNullOrBlank()) {
                statusMap[id] = status
            }
        }
        DlcPresetStateStore.savePresetStatus(statusMap)
        presetDirty = false
    }

    @JvmStatic
    fun syncStatusFromExternal(statusesInput: Map<String, DlcPresetStatus>?) {
        if (PRESETS_BY_ID.isEmpty()) {
            return
        }
        val statuses = statusesInput ?: emptyMap()
        val normalized = LinkedHashMap<String, DlcPresetStatus>()
        ENABLED_PRESETS.clear()
        for (entry in PRESETS_BY_ID.values) {
            val id = entry.getId()
            val status = statuses.getOrDefault(id, DlcPresetStatus.OFF)
            entry.setStatus(status)
            if (!id.isNullOrBlank()) {
                normalized[id] = status
            }
            if (!id.isNullOrBlank() && status.isEnabled()) {
                ENABLED_PRESETS.add(id)
            }
        }
        DlcPresetStateStore.savePresetStatus(normalized)
        presetDirty = false
        rebuildBlocks()
    }

    @JvmStatic
    fun applyOverridesToRuntime() {
        if (PRESETS_BY_ID.isEmpty()) {
            return
        }
        rebuildBlocks()
        applyOverrides()
    }

    @JvmStatic
    fun getDesiredDisabledRelativePathsByPreset(): Map<String, Set<String>> {
        val enableCache = LinkedHashMap<String, Set<String>>()
        val conflictCache = LinkedHashMap<String, Set<String>>()
        val result = LinkedHashMap<String, Set<String>>()
        for (presetId in ENABLED_PRESETS) {
            val paths = LinkedHashSet<String>()
            for (token in resolveConflictTokensForPreset(presetId, enableCache, conflictCache, LinkedHashSet(), LinkedHashSet())) {
                val relativePath = normalizeRelativeConflictPath(token)
                if (!relativePath.isNullOrBlank()) {
                    paths.add(relativePath)
                }
            }
            if (paths.isNotEmpty()) {
                result[presetId] = java.util.Collections.unmodifiableSet(LinkedHashSet(paths))
            }
        }
        return result
    }

    @JvmStatic
    fun hasPendingFileConflictChanges(): Boolean {
        return normalizeRelativePathMap(APPLIED_DISABLED_RELATIVE_PATHS) !=
            normalizeRelativePathMap(getDesiredDisabledRelativePathsByPreset())
    }

    @JvmStatic
    fun hasAppliedFileConflictChangesFromBaseline(): Boolean {
        return normalizeRelativePathMap(BASELINE_DISABLED_RELATIVE_PATHS) !=
            normalizeRelativePathMap(APPLIED_DISABLED_RELATIVE_PATHS)
    }

    @JvmStatic
    fun getAppliedDisabledRelativePathsByPreset(): Map<String, Set<String>> = copyRelativePathMap(APPLIED_DISABLED_RELATIVE_PATHS)

    @JvmStatic
    fun setAppliedDisabledRelativePathsByPreset(applied: Map<String, Set<String>>?) {
        APPLIED_DISABLED_RELATIVE_PATHS.clear()
        APPLIED_DISABLED_RELATIVE_PATHS.putAll(normalizeRelativePathMap(applied))
        filterAppliedDisabledRelativePaths()
    }

    private fun rebuildBlocks() {
        val dlcBlockers = LinkedHashMap<String, MutableSet<String>>()
        val presetBlockers = LinkedHashMap<String, MutableSet<String>>()
        val activeEnabledOwners = LinkedHashMap<String, MutableSet<String>>()
        val enableCache = LinkedHashMap<String, Set<String>>()
        val conflictCache = LinkedHashMap<String, Set<String>>()
        for (presetId in ENABLED_PRESETS) {
            val preset = PRESETS_BY_ID[presetId] ?: continue
            val partial = preset.getStatus() == DlcPresetStatus.PART
            for (enable in resolveEnableIdentifiersForPreset(preset.getId(), enableCache, LinkedHashSet())) {
                if (enable.isBlank()) {
                    continue
                }
                if (partial) {
                    val item = DlcRuntimeState.getItemByIdentifier(enable)
                    if (item == null || !item.isEnabled()) {
                        continue
                    }
                }
                activeEnabledOwners.computeIfAbsent(enable) { LinkedHashSet() }.add(presetId)
            }
            for (conflict in resolveConflictIdentifiersForPreset(preset.getId(), enableCache, conflictCache, LinkedHashSet(), LinkedHashSet())) {
                if (conflict.isBlank()) {
                    continue
                }
                dlcBlockers.computeIfAbsent(conflict) { LinkedHashSet() }.add(presetId)
            }
        }
        if (activeEnabledOwners.isNotEmpty()) {
            for ((identifier, owners) in activeEnabledOwners) {
                val item = DlcRuntimeState.getItemByIdentifier(identifier)
                if (item == null || item.getConflicts().isEmpty()) {
                    continue
                }
                for (conflict in item.getConflicts()) {
                    if (conflict.isNullOrBlank()) {
                        continue
                    }
                    if (activeEnabledOwners.containsKey(conflict)) {
                        continue
                    }
                    dlcBlockers.computeIfAbsent(conflict) { LinkedHashSet() }.addAll(owners)
                }
            }
        }
        if (dlcBlockers.isNotEmpty()) {
            for (preset in PRESETS_BY_ID.values) {
                if (ENABLED_PRESETS.contains(preset.getId())) {
                    continue
                }
                for (enable in resolveEnableIdentifiersForPreset(preset.getId(), enableCache, LinkedHashSet())) {
                    if (enable.isBlank()) {
                        continue
                    }
                    val blockers = dlcBlockers[enable]
                    if (blockers != null && blockers.isNotEmpty()) {
                        val presetId = preset.getId()
                        if (!presetId.isNullOrBlank()) {
                            presetBlockers.computeIfAbsent(presetId) { LinkedHashSet() }.addAll(blockers)
                        }
                    }
                }
            }
        }
        BLOCKED_DLC = LinkedHashMap(dlcBlockers)
        BLOCKED_PRESET = LinkedHashMap(presetBlockers)
    }

    private fun computeStatus(entry: DlcPresetEntry?): DlcPresetStatus {
        if (entry == null) {
            return DlcPresetStatus.OFF
        }
        if (!ENABLED_PRESETS.contains(entry.getId())) {
            return DlcPresetStatus.OFF
        }
        var allEnabled = true
        for (identifier in resolveEnableIdentifiersForPreset(entry.getId(), LinkedHashMap(), LinkedHashSet())) {
            if (identifier.isBlank()) {
                continue
            }
            val item = DlcRuntimeState.getItemByIdentifier(identifier)
            if (item == null || !item.isEnabled()) {
                allEnabled = false
                break
            }
        }
        return if (allEnabled) DlcPresetStatus.ON else DlcPresetStatus.PART
    }

    private fun updateEntryStatusesFromRuntime() {
        for (entry in PRESETS_BY_ID.values) {
            val status = if (ENABLED_PRESETS.contains(entry.getId())) computeStatus(entry) else DlcPresetStatus.OFF
            if (status != entry.getStatus()) {
                entry.setStatus(status)
            }
        }
    }

    private fun updateEntryStatus(id: String?, status: DlcPresetStatus) {
        if (id == null) {
            return
        }
        val entry = PRESETS_BY_ID[id] ?: return
        if (status != entry.getStatus()) {
            entry.setStatus(status)
        }
    }

    private fun applyOverrides() {
        val desiredEnable = LinkedHashSet<String>()
        var desiredDisableIdentifiers = LinkedHashSet<String>()
        val enableCache = LinkedHashMap<String, Set<String>>()
        val conflictCache = LinkedHashMap<String, Set<String>>()
        for (presetId in ENABLED_PRESETS) {
            val preset = PRESETS_BY_ID[presetId] ?: continue
            val partial = preset.getStatus() == DlcPresetStatus.PART
            for (enable in resolveEnableIdentifiersForPreset(preset.getId(), enableCache, LinkedHashSet())) {
                if (enable.isBlank()) {
                    continue
                }
                if (partial) {
                    val item = DlcRuntimeState.getItemByIdentifier(enable)
                    if (item != null && item.isEnabled()) {
                        desiredEnable.add(enable)
                    }
                } else {
                    desiredEnable.add(enable)
                }
            }
            for (conflict in resolveConflictIdentifiersForPreset(preset.getId(), enableCache, conflictCache, LinkedHashSet(), LinkedHashSet())) {
                if (conflict.isBlank()) {
                    continue
                }
                desiredDisableIdentifiers.add(conflict)
            }
        }
        desiredDisableIdentifiers.addAll(collectInfoConflicts(desiredEnable))
        desiredDisableIdentifiers = LinkedHashSet(expandDisabledByDependents(desiredDisableIdentifiers))
        desiredEnable.removeAll(desiredDisableIdentifiers)

        val toDisable = LinkedHashSet(LAST_APPLIED_IDENTIFIERS)
        val desiredAll = LinkedHashSet(desiredEnable)
        desiredAll.addAll(desiredDisableIdentifiers)
        toDisable.removeAll(desiredAll)

        for (identifier in desiredEnable) {
            val item = DlcRuntimeState.getItemByIdentifier(identifier)
            if (item != null) {
                DlcRuntimeState.setOverride(item.getId(), true)
            }
        }
        for (identifier in desiredDisableIdentifiers) {
            val item = DlcRuntimeState.getItemByIdentifier(identifier)
            if (item != null) {
                DlcRuntimeState.setOverride(item.getId(), false)
            }
        }
        for (identifier in toDisable) {
            val item = DlcRuntimeState.getItemByIdentifier(identifier)
            if (item != null) {
                val baseline = DlcRuntimeState.getBaselineEnabled(item.getId())
                if (baseline != null) {
                    DlcRuntimeState.setOverride(item.getId(), baseline)
                } else {
                    DlcRuntimeState.clearOverride(item.getId())
                }
            }
        }
        LAST_APPLIED_IDENTIFIERS.clear()
        LAST_APPLIED_IDENTIFIERS.addAll(desiredAll)
    }

    @JvmStatic
    fun resolveEffectiveEnableIdentifiers(presetId: String?): Set<String> {
        return resolveEnableIdentifiersForPreset(presetId, LinkedHashMap(), LinkedHashSet())
    }

    @JvmStatic
    fun resolveEffectiveConflictIdentifiers(presetId: String?): Set<String> {
        val enableCache = LinkedHashMap<String, Set<String>>()
        return resolveConflictIdentifiersForPreset(presetId, enableCache, LinkedHashMap(), LinkedHashSet(), LinkedHashSet())
    }

    private fun resolveEnableIdentifiersForPreset(
        presetId: String?,
        cache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
    ): Set<String> {
        if (presetId.isNullOrBlank()) {
            return emptySet()
        }
        val cached = cache[presetId]
        if (cached != null) {
            return cached
        }
        if (!stack.add(presetId)) {
            return emptySet()
        }
        val preset = PRESETS_BY_ID[presetId]
        if (preset == null) {
            stack.remove(presetId)
            return emptySet()
        }
        val result = LinkedHashSet<String>()
        for (token in preset.getEnable()) {
            val value = normalizeToken(token)
            if (value.isBlank()) {
                continue
            }
            val refPresetId = resolvePresetReference(value)
            if (refPresetId != null) {
                result.addAll(resolveEnableIdentifiersForPreset(refPresetId, cache, stack))
            } else {
                result.add(value)
            }
        }
        stack.remove(presetId)
        val unmodifiable = java.util.Collections.unmodifiableSet(LinkedHashSet(result))
        cache[presetId] = unmodifiable
        return unmodifiable
    }

    private fun resolveConflictTokensForPreset(
        presetId: String?,
        enableCache: MutableMap<String, Set<String>>,
        conflictCache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
        enableStack: MutableSet<String>,
    ): Set<String> {
        if (presetId.isNullOrBlank()) {
            return emptySet()
        }
        val cached = conflictCache[presetId]
        if (cached != null) {
            return cached
        }
        if (!stack.add(presetId)) {
            return emptySet()
        }
        val preset = PRESETS_BY_ID[presetId]
        if (preset == null) {
            stack.remove(presetId)
            return emptySet()
        }
        val result = LinkedHashSet<String>()
        for (token in preset.getConflicts()) {
            val value = normalizeToken(token)
            if (value.isBlank()) {
                continue
            }
            val refPresetId = resolvePresetReference(value)
            if (refPresetId != null) {
                result.addAll(resolveEnableIdentifiersForPreset(refPresetId, enableCache, enableStack))
            } else {
                result.add(value)
            }
        }
        stack.remove(presetId)
        val unmodifiable = java.util.Collections.unmodifiableSet(LinkedHashSet(result))
        conflictCache[presetId] = unmodifiable
        return unmodifiable
    }

    private fun resolveConflictIdentifiersForPreset(
        presetId: String?,
        enableCache: MutableMap<String, Set<String>>,
        conflictCache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
        enableStack: MutableSet<String>,
    ): Set<String> {
        val tokens = resolveConflictTokensForPreset(presetId, enableCache, conflictCache, stack, enableStack)
        if (tokens.isEmpty()) {
            return tokens
        }
        val identifiers = LinkedHashSet<String>()
        for (token in tokens) {
            if (isIdentifierConflictToken(token)) {
                identifiers.add(token)
            }
        }
        return java.util.Collections.unmodifiableSet(identifiers)
    }

    private fun resolvePresetReference(token: String?): String? {
        if (token.isNullOrBlank()) {
            return null
        }
        var forcedPreset = false
        var value = token.trim()
        if (value.regionMatches(0, PRESET_REF_PREFIX, 0, PRESET_REF_PREFIX.length, ignoreCase = true)) {
            forcedPreset = true
            value = value.substring(PRESET_REF_PREFIX.length).trim()
            if (value.isBlank()) {
                return null
            }
        }
        val resolved = resolvePresetId(value) ?: return null
        if (!forcedPreset && DlcRuntimeState.getItemByIdentifier(value) != null) {
            return null
        }
        return resolved
    }

    private fun resolvePresetId(value: String): String? {
        val exact = PRESETS_BY_ID[value]
        if (exact != null && !exact.getId().isNullOrBlank()) {
            return exact.getId()
        }
        for (entry in PRESETS_BY_ID.values) {
            if (entry.getId().isNullOrBlank()) {
                continue
            }
            if (entry.getId().equals(value, ignoreCase = true)) {
                return entry.getId()
            }
        }
        return null
    }

    private fun normalizeToken(token: String?): String {
        if (token == null) {
            return ""
        }
        return token.trim()
    }

    private fun isIdentifierConflictToken(token: String?): Boolean {
        val value = normalizeToken(token)
        if (value.isBlank()) {
            return false
        }
        return DlcRuntimeState.getItemByIdentifier(value) != null
    }

    private fun normalizeRelativeConflictPath(token: String?): String? {
        val value = normalizeToken(token)
        if (value.isBlank()) {
            return null
        }
        if (DlcRuntimeState.getItemByIdentifier(value) != null) {
            return null
        }
        val rawPath = DlcPaths.parsePathLenient(value)
        if (rawPath == null || rawPath.isAbsolute) {
            return null
        }
        val gameDir = DlcPaths.gameDir() ?: return null
        val resolved = DlcPaths.resolveAgainstGameDir(value) ?: return null
        val normalizedGameDir = gameDir.toAbsolutePath().normalize()
        if (!resolved.startsWith(normalizedGameDir)) {
            return null
        }
        return normalizedGameDir.relativize(resolved).toString().replace('\\', '/')
    }

    private fun collectInfoConflicts(enabledIdentifiers: Set<String>?): Set<String> {
        val result = LinkedHashSet<String>()
        if (enabledIdentifiers == null || enabledIdentifiers.isEmpty()) {
            return result
        }
        for (identifier in enabledIdentifiers) {
            if (identifier.isBlank()) {
                continue
            }
            val item = DlcRuntimeState.getItemByIdentifier(identifier) ?: continue
            val conflicts = item.getConflicts()
            if (conflicts.isEmpty()) {
                continue
            }
            for (conflict in conflicts) {
                if (conflict.isNullOrBlank()) {
                    continue
                }
                if (enabledIdentifiers.contains(conflict)) {
                    continue
                }
                result.add(conflict)
            }
        }
        return result
    }

    private fun expandDisabledByDependents(disabledIdentifiers: Set<String>?): Set<String> {
        val expanded = LinkedHashSet<String>()
        if (disabledIdentifiers == null || disabledIdentifiers.isEmpty()) {
            return expanded
        }
        val queue = java.util.ArrayDeque<DlcItem>()
        for (identifier in disabledIdentifiers) {
            if (identifier.isBlank()) {
                continue
            }
            expanded.add(identifier)
            val root = DlcRuntimeState.getItemByIdentifier(identifier)
            if (root != null) {
                queue.add(root)
            }
        }
        while (!queue.isEmpty()) {
            val current = queue.poll()
            if (current == null || current.getIdentifier().isNullOrBlank()) {
                continue
            }
            for (dependent in DlcRuntimeState.getDependents(current.getIdentifier())) {
                if (dependent.getIdentifier().isNullOrBlank()) {
                    continue
                }
                val dependentIdentifier = dependent.getIdentifier()
                if (!dependentIdentifier.isNullOrBlank() && expanded.add(dependentIdentifier)) {
                    queue.add(dependent)
                }
            }
        }
        return expanded
    }

    private fun rollbackLastAppliedToBaseline() {
        if (LAST_APPLIED_IDENTIFIERS.isEmpty()) {
            return
        }
        for (identifier in LAST_APPLIED_IDENTIFIERS) {
            val item = DlcRuntimeState.getItemByIdentifier(identifier) ?: continue
            val baseline = DlcRuntimeState.getBaselineEnabled(item.getId())
            if (baseline != null) {
                DlcRuntimeState.setOverride(item.getId(), baseline)
            } else {
                DlcRuntimeState.clearOverride(item.getId())
            }
        }
    }

    private fun filterAppliedDisabledRelativePaths() {
        APPLIED_DISABLED_RELATIVE_PATHS.entries.removeIf { entry ->
            !PRESETS_BY_ID.containsKey(entry.key)
        }
    }

    private fun copyRelativePathMap(source: Map<String, Set<String>>?): Map<String, Set<String>> {
        return normalizeRelativePathMap(source)
    }

    private fun normalizeRelativePathMap(source: Map<String, Set<String>>?): Map<String, Set<String>> {
        val normalized = LinkedHashMap<String, Set<String>>()
        if (source == null || source.isEmpty()) {
            return normalized
        }
        for ((presetId, sourceValues) in source) {
            if (presetId.isNullOrBlank()) {
                continue
            }
            val values = LinkedHashSet<String>()
            for (value in sourceValues) {
                if (value.isNullOrBlank()) {
                    continue
                }
                values.add(value.replace('\\', '/'))
            }
            if (values.isNotEmpty()) {
                normalized[presetId] = java.util.Collections.unmodifiableSet(LinkedHashSet(values))
            }
        }
        return normalized
    }
}
