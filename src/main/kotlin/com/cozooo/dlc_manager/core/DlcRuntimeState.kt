package com.cozooo.dlc_manager.core

import java.util.ArrayDeque
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet

object DlcRuntimeState {
    private val pendingRestart: MutableSet<String> = HashSet()
    private val pendingReload: MutableSet<String> = HashSet()
    private val baselineEnabled: MutableMap<String, Boolean> = HashMap()
    private val overrideEnabled: MutableMap<String, Boolean> = HashMap()
    private val itemsByIdentifier: MutableMap<String, DlcItem> = HashMap()
    private val dependentsByIdentifier: MutableMap<String, MutableList<DlcItem>> = HashMap()

    @JvmStatic
    fun isPending(id: String?): Boolean {
        return pendingRestart.contains(id) || pendingReload.contains(id)
    }

    @JvmStatic
    fun isPendingRestart(id: String?): Boolean {
        return pendingRestart.contains(id)
    }

    @JvmStatic
    fun isPendingReload(id: String?): Boolean {
        return pendingReload.contains(id)
    }

    @JvmStatic
    fun markPendingRestart(items: Collection<DlcItem>?) {
        if (items.isNullOrEmpty()) {
            return
        }
        for (item in items) {
            item.getId()?.let(pendingRestart::add)
        }
    }

    @JvmStatic
    fun markPendingReload(items: Collection<DlcItem>?) {
        if (items.isNullOrEmpty()) {
            return
        }
        for (item in items) {
            item.getId()?.let(pendingReload::add)
        }
    }

    @JvmStatic
    fun clearPendingReload(ids: Collection<String>?) {
        if (ids.isNullOrEmpty()) {
            return
        }
        pendingReload.removeAll(ids.toSet())
    }

    @JvmStatic
    fun clearPendingRestart(id: String?) {
        if (id != null) {
            pendingRestart.remove(id)
        }
    }

    @JvmStatic
    fun clearPendingReload(id: String?) {
        if (id != null) {
            pendingReload.remove(id)
        }
    }

    @JvmStatic
    fun clearAllPendingReload() {
        pendingReload.clear()
    }

    @JvmStatic
    fun getPendingRestartIds(): Set<String> {
        return Collections.unmodifiableSet(pendingRestart)
    }

    @JvmStatic
    fun getPendingReloadIds(): Set<String> {
        return Collections.unmodifiableSet(pendingReload)
    }

    @JvmStatic
    fun ensureBaseline(id: String?, enabled: Boolean) {
        if (id != null) {
            baselineEnabled.putIfAbsent(id, enabled)
        }
    }

    @JvmStatic
    fun updateBaseline(id: String?, enabled: Boolean) {
        if (id != null) {
            baselineEnabled[id] = enabled
        }
    }

    @JvmStatic
    fun isBaselineDifferent(id: String?, enabled: Boolean): Boolean {
        if (id == null) {
            return false
        }
        val baseline = baselineEnabled[id] ?: return false
        return baseline != enabled
    }

    @JvmStatic
    fun getBaselineEnabled(id: String?): Boolean? {
        return if (id == null) null else baselineEnabled[id]
    }

    @JvmStatic
    fun setOverride(id: String?, enabled: Boolean) {
        if (id != null) {
            overrideEnabled[id] = enabled
        }
    }

    @JvmStatic
    fun getOverride(id: String?): Boolean? {
        return if (id == null) null else overrideEnabled[id]
    }

    @JvmStatic
    fun clearOverride(id: String?) {
        if (id != null) {
            overrideEnabled.remove(id)
        }
    }

    @JvmStatic
    fun clearOverrides() {
        overrideEnabled.clear()
    }

    @JvmStatic
    fun updateItemIndex(items: Collection<DlcItem>?) {
        itemsByIdentifier.clear()
        dependentsByIdentifier.clear()
        if (items.isNullOrEmpty()) {
            return
        }
        for (item in items) {
            val identifier = item.getIdentifier()
            if (!identifier.isNullOrBlank()) {
                itemsByIdentifier[identifier] = item
            }
        }
        for (item in items) {
            if (item.getIdentifier() == null) {
                continue
            }
            val deps = item.getDependencies()
            if (deps.isNullOrEmpty()) {
                continue
            }
            for (depName in deps) {
                if (depName.isNullOrBlank()) {
                    continue
                }
                if (itemsByIdentifier.containsKey(depName)) {
                    dependentsByIdentifier.computeIfAbsent(depName) { ArrayList() }.add(item)
                }
            }
        }
    }

    @JvmStatic
    fun getItemByIdentifier(identifier: String?): DlcItem? {
        return if (identifier == null) null else itemsByIdentifier[identifier]
    }

    @JvmStatic
    fun getIndexedItems(): Collection<DlcItem> = ArrayList(itemsByIdentifier.values)

    @JvmStatic
    fun getDependents(identifier: String?): List<DlcItem> {
        if (identifier == null) {
            return emptyList()
        }
        return dependentsByIdentifier[identifier] ?: emptyList()
    }

    @JvmStatic
    fun collectDependentsRecursive(item: DlcItem?): Set<DlcItem> {
        val result = LinkedHashSet<DlcItem>()
        if (item == null) {
            return result
        }
        val queue = ArrayDeque<DlcItem>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            if (current == null || current.getIdentifier() == null) {
                continue
            }
            if (!result.add(current)) {
                continue
            }
            for (dependent in getDependents(current.getIdentifier())) {
                if (!result.contains(dependent)) {
                    queue.add(dependent)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun collectDependenciesRecursive(item: DlcItem?): Set<DlcItem> {
        val result = LinkedHashSet<DlcItem>()
        if (item == null) {
            return result
        }
        val queue = ArrayDeque<DlcItem>()
        val visited = HashSet<String>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val currentIdentifier = current?.getIdentifier()
            if (currentIdentifier == null) {
                continue
            }
            if (!visited.add(currentIdentifier)) {
                continue
            }
            val deps = current.getDependencies()
            if (deps.isNullOrEmpty()) {
                continue
            }
            for (depName in deps) {
                if (depName.isNullOrBlank()) {
                    continue
                }
                val depItem = getItemByIdentifier(depName) ?: continue
                if (result.add(depItem)) {
                    queue.add(depItem)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun collectHiddenDependenciesToDisable(disabledItems: Collection<DlcItem>?): Set<DlcItem> {
        val disabledSet = LinkedHashSet<DlcItem>()
        if (disabledItems != null) {
            disabledSet.addAll(disabledItems)
        }
        val candidates = LinkedHashSet<DlcItem>()
        for (item in disabledSet) {
            for (dep in collectDependenciesRecursive(item)) {
                if (dep.isHidden()) {
                    candidates.add(dep)
                }
            }
        }
        val toDisable = LinkedHashSet<DlcItem>()
        for (candidate in candidates) {
            var required = false
            for (dependent in getDependents(candidate.getIdentifier())) {
                if (disabledSet.contains(dependent)) {
                    continue
                }
                if (dependent.isEnabled()) {
                    required = true
                    break
                }
            }
            if (!required) {
                toDisable.add(candidate)
            }
        }
        return toDisable
    }

    @JvmStatic
    fun collectMissingDependencies(targets: Collection<DlcItem>?): DependencyBatchResult {
        val lines = ArrayList<DependencyLine>()
        val toEnable = ArrayList<DlcItem>()
        if (targets.isNullOrEmpty()) {
            return DependencyBatchResult(lines, toEnable)
        }
        val targetIdentifiers = HashSet<String>()
        val queue = ArrayDeque<DlcItem>()
        for (item in targets) {
            val identifier = item.getIdentifier()
            if (identifier != null) {
                targetIdentifiers.add(identifier)
                queue.add(item)
            }
        }
        val seenLines = HashSet<String>()
        val processed = HashSet<String>()
        while (!queue.isEmpty()) {
            val item = queue.poll()
            val identifier = item.getIdentifier()
            if (identifier == null) {
                continue
            }
            if (!processed.add(identifier)) {
                continue
            }
            val deps = item.getDependencies()
            if (deps.isNullOrEmpty()) {
                continue
            }
            for (depName in deps) {
                if (depName.isNullOrBlank()) {
                    continue
                }
                val depItem = getItemByIdentifier(depName)
                val satisfied = depItem != null && (depItem.isEnabled() || targetIdentifiers.contains(depItem.getIdentifier())) ||
                    depItem == null && isRelationPathPresent(depName)
                if (!satisfied) {
                    val key = "$identifier|$depName"
                    if (seenLines.add(key)) {
                        lines.add(DependencyLine(identifier, depName))
                    }
                    if (depItem != null && !toEnable.contains(depItem)) {
                        toEnable.add(depItem)
                        depItem.getIdentifier()?.let(targetIdentifiers::add)
                    }
                }
                if (depItem != null) {
                    queue.add(depItem)
                }
            }
        }
        return DependencyBatchResult(lines, toEnable)
    }

    @JvmStatic
    fun collectConflicts(targets: Collection<DlcItem>?): ConflictBatchResult {
        val lines = ArrayList<ConflictLine>()
        if (targets.isNullOrEmpty()) {
            return ConflictBatchResult(lines)
        }
        val plannedById = LinkedHashMap<String, DlcItem>()
        val queue = ArrayDeque<DlcItem>()
        for (item in targets) {
            val identifier = item.getIdentifier()
            if (identifier.isNullOrBlank()) {
                continue
            }
            if (plannedById.putIfAbsent(identifier, item) == null) {
                queue.add(item)
            }
        }
        while (!queue.isEmpty()) {
            val item = queue.poll()
            val deps = item?.getDependencies()
            if (deps.isNullOrEmpty()) {
                continue
            }
            for (depName in deps) {
                if (depName.isNullOrBlank()) {
                    continue
                }
                val depItem = getItemByIdentifier(depName)
                val depIdentifier = depItem?.getIdentifier()
                if (depIdentifier.isNullOrBlank()) {
                    continue
                }
                if (plannedById.putIfAbsent(depIdentifier, depItem) == null) {
                    queue.add(depItem)
                }
            }
        }
        val targetIdentifiers = plannedById.keys
        val seen = HashSet<String>()
        for (item in plannedById.values) {
            val identifier = item.getIdentifier()
            val conflicts = item.getConflicts()
            if (conflicts.isNullOrEmpty()) {
                continue
            }
            for (conflictName in conflicts) {
                if (conflictName.isNullOrBlank()) {
                    continue
                }
                val conflictItem = getItemByIdentifier(conflictName)
                if (conflictItem != null) {
                    val conflictEnabled = conflictItem.isEnabled() || targetIdentifiers.contains(conflictItem.getIdentifier())
                    if (conflictEnabled) {
                        addConflictLine(lines, seen, identifier, conflictItem.getIdentifier())
                    }
                }
            }
        }
        for (existing in itemsByIdentifier.values) {
            val identifier = existing.getIdentifier()
            if (identifier.isNullOrBlank()) {
                continue
            }
            if (targetIdentifiers.contains(identifier) || !existing.isEnabled()) {
                continue
            }
            val conflicts = existing.getConflicts()
            if (conflicts.isNullOrEmpty()) {
                continue
            }
            for (conflictName in conflicts) {
                if (conflictName.isNullOrBlank()) {
                    continue
                }
                if (!targetIdentifiers.contains(conflictName)) {
                    continue
                }
                addConflictLine(lines, seen, identifier, conflictName)
            }
        }
        return ConflictBatchResult(lines)
    }

    private fun isRelationPathPresent(value: String?): Boolean {
        return DlcPathUtil.findMatches(DlcPaths.gameDir(), value).isNotEmpty()
    }

    private fun addConflictLine(lines: MutableList<ConflictLine>, seen: MutableSet<String>, left: String?, right: String?) {
        if (left.isNullOrBlank() || right.isNullOrBlank()) {
            return
        }
        var a = left
        var b = right
        val key = if (a.compareTo(b, ignoreCase = true) <= 0) "$a|$b" else "$b|$a"
        if (!seen.add(key)) {
            return
        }
        if (a.compareTo(b, ignoreCase = true) > 0) {
            val tmp = a
            a = b
            b = tmp
        }
        lines.add(ConflictLine(a, b))
    }

    @JvmStatic
    fun collectMissingDependencyIdentifiers(item: DlcItem?): Set<String> {
        val missing = LinkedHashSet<String>()
        val identifier = item?.getIdentifier()
        if (identifier == null) {
            return missing
        }
        val queue = ArrayDeque<DlcItem>()
        val visited = HashSet<String>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val currentIdentifier = current?.getIdentifier()
            if (currentIdentifier == null) {
                continue
            }
            if (!visited.add(currentIdentifier)) {
                continue
            }
            val deps = current.getDependencies()
            if (deps.isNullOrEmpty()) {
                continue
            }
            for (depName in deps) {
                if (depName.isNullOrBlank()) {
                    continue
                }
                val depItem = getItemByIdentifier(depName)
                if (depItem == null) {
                    if (isRelationPathPresent(depName)) {
                        continue
                    }
                    missing.add(depName)
                } else {
                    queue.add(depItem)
                }
            }
        }
        return missing
    }

    @JvmStatic
    fun hasMissingDependencies(item: DlcItem?): Boolean {
        return collectMissingDependencyIdentifiers(item).isNotEmpty()
    }

    class DependencyBatchResult(
        private val lines: List<DependencyLine>,
        private val itemsToEnable: List<DlcItem>,
    ) {
        fun getLines(): List<DependencyLine> = lines

        fun getItemsToEnable(): List<DlcItem> = itemsToEnable
    }

    class ConflictBatchResult(
        private val lines: List<ConflictLine>,
    ) {
        fun getLines(): List<ConflictLine> = lines
    }

    class DependencyLine(
        private val itemName: String,
        private val dependencyName: String,
    ) {
        fun getItemName(): String = itemName

        fun getDependencyName(): String = dependencyName
    }

    class ConflictLine(
        private val leftName: String,
        private val rightName: String,
    ) {
        fun getLeftName(): String = leftName

        fun getRightName(): String = rightName
    }
}
