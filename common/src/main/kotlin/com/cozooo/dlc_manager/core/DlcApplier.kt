package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object DlcApplier {
    private const val CONFIG_MISSING_MARKER = "__dlc_config_missing__"
    private val CURRENT_APPLY_CONTEXT = ThreadLocal<ApplyContext>()

    @JvmStatic
    fun apply(items: List<DlcItem>, mode: DlcApplyMode?): ApplyResult {
        val messages = ArrayList<String>()
        var requiresRestart = false
        val attemptedHotReload = false
        val context = ApplyContext(messages)
        CURRENT_APPLY_CONTEXT.set(context)

        try {
            val conflictPathResult = applyDlcConflictPaths(items)
            if (conflictPathResult.messages.isNotEmpty()) {
                messages.addAll(conflictPathResult.messages)
            }
            if (conflictPathResult.changed) {
                requiresRestart = true
            }
            for (item in items) {
                val previous = DlcStateConfig.getState(item.getId())
                val wasEnabled = previous != null && previous.enabled
                if (requiresRestartOnToggle(item)) {
                    val baseline = DlcRuntimeState.getBaselineEnabled(item.getId())
                    if (baseline != null) {
                        if (baseline != item.isEnabled()) {
                            requiresRestart = true
                        }
                    } else if (item.isEnabled() != wasEnabled) {
                        requiresRestart = true
                    }
                }

                if (item.isEnabled()) {
                    if (!wasEnabled) {
                        messages.add("Enabled " + item.getName())
                    }
                    val statePaths = applyItem(item, wasEnabled)
                    if (statePaths.isNotEmpty()) {
                        val state = DlcStateConfig.DlcState()
                        state.enabled = true
                        state.appliedTargets = statePaths.map(DlcPathUtil::toGameRelativePath)
                        DlcStateConfig.setState(item.getId(), state)
                    }
                } else {
                    if (wasEnabled) {
                        messages.add("Disabled " + item.getName())
                        val removal = removeItem(item, previous)
                        messages.addAll(removal.messages)
                    }
                    val state = DlcStateConfig.DlcState()
                    state.enabled = false
                    state.appliedTargets = emptyList()
                    DlcStateConfig.setState(item.getId(), state)
                }
            }
            val presetPathResult = applyPresetConflictPaths()
            if (presetPathResult.messages.isNotEmpty()) {
                messages.addAll(presetPathResult.messages)
            }
            if (presetPathResult.changed && DlcPresetRuntimeState.hasAppliedFileConflictChangesFromBaseline()) {
                requiresRestart = true
            }
        } finally {
            CURRENT_APPLY_CONTEXT.remove()
        }

        requiresRestart = requiresRestart || context.helperScheduled
        DlcStateConfig.save()

        return ApplyResult(messages, requiresRestart, attemptedHotReload, context.helperScheduled)
    }

    private fun applyDlcConflictPaths(items: Collection<DlcItem>?): DlcConflictPathResult {
        val messages = ArrayList<String>()
        val desiredPatterns = collectEnabledDlcConflictPathPatterns(items)
        val desiredPaths = resolveExistingConflictTargets(desiredPatterns)
        val currentPaths = LinkedHashSet(collectConflictBackupRelativePaths())
        var changed = false

        val toDisable = LinkedHashSet(desiredPaths)
        toDisable.removeAll(currentPaths)
        for (relative in toDisable) {
            if (disableDlcConflictPath(relative, messages)) {
                changed = true
                currentPaths.add(relative)
            }
        }

        val toRestore = LinkedHashSet(currentPaths)
        for (relative in toRestore) {
            if (matchesAnyConflictPattern(desiredPatterns, relative)) {
                continue
            }
            if (restoreDlcConflictPath(relative, messages)) {
                changed = true
            }
        }
        return DlcConflictPathResult(messages, changed)
    }

    private fun collectEnabledDlcConflictPathPatterns(items: Collection<DlcItem>?): Set<String> {
        val result = LinkedHashSet<String>()
        if (items.isNullOrEmpty()) {
            return result
        }
        for (item in items) {
            if (!item.isEnabled()) {
                continue
            }
            val conflicts = item.getConflicts()
            if (conflicts.isNullOrEmpty()) {
                continue
            }
            for (conflict in conflicts) {
                if (conflict.isNullOrBlank() || DlcRuntimeState.getItemByIdentifier(conflict) != null) {
                    continue
                }
                val relative = DlcPathUtil.normalizeRelativePath(conflict)
                if (relative != null) {
                    result.add(relative)
                }
            }
        }
        return result
    }

    private fun resolveExistingConflictTargets(patterns: Set<String>?): Set<String> {
        val result = LinkedHashSet<String>()
        if (patterns.isNullOrEmpty()) {
            return result
        }
        val gameDir = DlcPaths.gameDir()
        val normalizedGameDir = gameDir.toAbsolutePath().normalize()
        for (pattern in patterns) {
            for (target in DlcPathUtil.findMatches(normalizedGameDir, pattern)) {
                val relative = DlcPathUtil.normalizeRelativePath(
                    normalizedGameDir.relativize(target.toAbsolutePath().normalize()).toString()
                )
                if (relative != null) {
                    result.add(relative)
                }
            }
        }
        return result
    }

    private fun collectConflictBackupRelativePaths(): Set<String> {
        val result = LinkedHashSet<String>()
        val root = resolveDlcConflictBackupRoot()
        if (root == null || !Files.exists(root)) {
            return result
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        try {
            Files.walk(normalizedRoot).use { stream ->
                stream.filter(Files::isRegularFile).forEach { path ->
                    val relative = DlcPathUtil.normalizeRelativePath(
                        normalizedRoot.relativize(path.toAbsolutePath().normalize()).toString()
                    )
                    if (relative != null) {
                        result.add(relative)
                    }
                }
            }
        } catch (ignored: IOException) {
            recordApplyFailure(ignored)
        }
        return result
    }

    private fun matchesAnyConflictPattern(patterns: Set<String>?, relative: String?): Boolean {
        if (patterns.isNullOrEmpty() || relative.isNullOrBlank()) {
            return false
        }
        for (pattern in patterns) {
            if (DlcPathUtil.matches(pattern, relative)) {
                return true
            }
        }
        return false
    }

    private fun disableDlcConflictPath(relative: String?, messages: MutableList<String>): Boolean {
        return disableConflictPath(relative, resolveDlcConflictBackupRoot(), messages, "Failed to disable DLC conflict path: $relative")
    }

    private fun restoreDlcConflictPath(relative: String?, messages: MutableList<String>): Boolean {
        return restoreConflictPath(relative, resolveDlcConflictBackupRoot(), messages, "Failed to restore DLC conflict path: $relative")
    }

    private fun addPathConflictMessage(messages: MutableList<String>, exception: IOException, fallback: String) {
        val message = exception.message
        if (!message.isNullOrBlank()) {
            messages.add(message)
        } else {
            messages.add(fallback)
        }
    }

    private fun resolveConflictTarget(relative: String?): Path? {
        if (relative.isNullOrBlank()) {
            return null
        }
        val gameDir = DlcPaths.gameDir()
        val resolved = DlcPaths.resolveAgainstGameDir(relative) ?: return null
        val normalizedGameDir = gameDir.toAbsolutePath().normalize()
        return if (resolved.startsWith(normalizedGameDir)) resolved else null
    }

    private fun resolveConflictBackup(root: Path?, relative: String?): Path? {
        if (root == null || relative.isNullOrBlank()) {
            return null
        }
        val normalized = DlcPathUtil.normalizeRelativePath(relative)
        return if (normalized == null) null else root.resolve(normalized).normalize()
    }

    private fun resolveDlcConflictBackupRoot(): Path? {
        val dlcRoot = DlcPaths.dlcRoot()
        return DlcTmpPaths.resolve(dlcRoot, "conflicts")
    }

    private fun applyPresetConflictPaths(): PresetConflictPathResult {
        val desiredByPreset = if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.getDesiredDisabledRelativePathsByPreset()
        } else {
            emptyMap()
        }
        val currentByPreset = DlcPresetRuntimeState.getAppliedDisabledRelativePathsByPreset()
        if (currentByPreset == desiredByPreset) {
            return PresetConflictPathResult(emptyList(), false)
        }

        val messages = ArrayList<String>()
        var changed = false
        val current = unionRelativePaths(currentByPreset)
        val desired = unionRelativePaths(desiredByPreset)
        val restored = LinkedHashSet<String>()
        val disabled = LinkedHashSet<String>()

        val toRestore = LinkedHashSet(current)
        toRestore.removeAll(desired)
        for (relative in toRestore) {
            if (restorePresetConflictPath(relative, messages)) {
                restored.add(relative)
                changed = true
            }
        }

        val toDisable = LinkedHashSet(desired)
        toDisable.removeAll(current)
        for (relative in toDisable) {
            if (disablePresetConflictPath(relative, messages)) {
                disabled.add(relative)
                changed = true
            }
        }
        DlcPresetRuntimeState.setAppliedDisabledRelativePathsByPreset(
            updateAppliedRelativePathsByPreset(currentByPreset, desiredByPreset, restored, disabled)
        )
        return PresetConflictPathResult(messages, changed)
    }

    private fun disablePresetConflictPath(relative: String?, messages: MutableList<String>): Boolean {
        return disableConflictPath(relative, resolvePresetConflictBackupRoot(), messages, "Failed to disable preset conflict path: $relative")
    }

    private fun disableConflictPath(relative: String?, backupRoot: Path?, messages: MutableList<String>, fallbackMessage: String): Boolean {
        val target = resolveConflictTarget(relative)
        val backup = resolveConflictBackup(backupRoot, relative)
        if (target == null || backup == null) {
            return false
        }
        if (!Files.exists(target)) {
            if (Files.exists(backup)) {
                return true
            }
            if (DlcFileOpsHelperClient.cancelPendingOperation(target, "move") && Files.exists(backup)) {
                return true
            }
            return false
        }
        return try {
            deleteRecursiveIfExists(backup)
            if (backup.parent != null) {
                Files.createDirectories(backup.parent)
            }
            moveWithHelperFallback(target, backup, true)
            cleanupEmptyParents(target.parent, DlcPaths.gameDir())
            cleanupEmptyParents(backup.parent, backupRoot)
            true
        } catch (exception: IOException) {
            addPathConflictMessage(messages, exception, fallbackMessage)
            recordApplyFailure(exception)
            false
        }
    }

    private fun restorePresetConflictPath(relative: String?, messages: MutableList<String>): Boolean {
        return restoreConflictPath(relative, resolvePresetConflictBackupRoot(), messages, "Failed to restore preset conflict path: $relative")
    }

    private fun restoreConflictPath(relative: String?, backupRoot: Path?, messages: MutableList<String>, fallbackMessage: String): Boolean {
        val target = resolveConflictTarget(relative)
        val backup = resolveConflictBackup(backupRoot, relative)
        if (target == null || backup == null) {
            return false
        }
        if (!Files.exists(backup)) {
            if (Files.exists(target)) {
                return true
            }
            if (DlcFileOpsHelperClient.cancelPendingOperation(backup, "move") && Files.exists(target)) {
                return true
            }
            return false
        }
        return try {
            deleteRecursiveIfExists(target)
            if (target.parent != null) {
                Files.createDirectories(target.parent)
            }
            moveWithHelperFallback(backup, target, true)
            cleanupEmptyParents(backup.parent, backupRoot)
            true
        } catch (exception: IOException) {
            addPathConflictMessage(messages, exception, fallbackMessage)
            recordApplyFailure(exception)
            false
        }
    }

    private fun resolvePresetConflictBackupRoot(): Path? {
        val dlcRoot = DlcPaths.dlcRoot()
        return DlcTmpPaths.resolve(dlcRoot, "disabled")
    }

    private fun unionRelativePaths(byPreset: Map<String, Set<String>>?): Set<String> {
        val result = LinkedHashSet<String>()
        if (byPreset.isNullOrEmpty()) {
            return result
        }
        for (paths in byPreset.values) {
            result.addAll(paths)
        }
        return result
    }

    private fun updateAppliedRelativePathsByPreset(
        currentByPreset: Map<String, Set<String>>,
        desiredByPreset: Map<String, Set<String>>,
        restored: Set<String>,
        disabled: Set<String>,
    ): Map<String, Set<String>> {
        val next: MutableMap<String, MutableSet<String>> = LinkedHashMap()
        val current = unionRelativePaths(currentByPreset)
        val desired = unionRelativePaths(desiredByPreset)
        val all = LinkedHashSet(current)
        all.addAll(desired)
        for (relative in all) {
            val currentHas = current.contains(relative)
            val desiredHas = desired.contains(relative)
            if (currentHas && desiredHas) {
                assignRelativePathOwners(next, desiredByPreset, relative)
            } else if (!currentHas && desiredHas) {
                if (disabled.contains(relative)) {
                    assignRelativePathOwners(next, desiredByPreset, relative)
                }
            } else if (currentHas && !desiredHas) {
                if (!restored.contains(relative)) {
                    assignRelativePathOwners(next, currentByPreset, relative)
                }
            }
        }
        return next
    }

    private fun assignRelativePathOwners(
        target: MutableMap<String, MutableSet<String>>,
        ownersByPreset: Map<String, Set<String>>?,
        relative: String?,
    ) {
        if (relative.isNullOrBlank() || ownersByPreset.isNullOrEmpty()) {
            return
        }
        for ((key, value) in ownersByPreset) {
            if (key.isBlank() || !value.contains(relative)) {
                continue
            }
            target.computeIfAbsent(key) { LinkedHashSet() }.add(relative)
        }
    }

    private fun cleanupEmptyParents(start: Path?, stopExclusive: Path?) {
        if (start == null) {
            return
        }
        val stop = stopExclusive?.toAbsolutePath()?.normalize()
        var current: Path? = start.toAbsolutePath().normalize()
        while (current != null && current != stop) {
            try {
                if (!Files.isDirectory(current) || !isDirectoryEmpty(current)) {
                    break
                }
                Files.deleteIfExists(current)
            } catch (_: IOException) {
                break
            }
            current = current.parent
        }
    }

    @Throws(IOException::class)
    private fun isDirectoryEmpty(path: Path): Boolean {
        Files.list(path).use { stream ->
            return stream.findAny().isEmpty
        }
    }

    private fun requiresRestartOnToggle(item: DlcItem): Boolean {
        return item.getType() == DlcType.MOD ||
            item.getType() == DlcType.TACZ_PACK ||
            DlcConfigTypeSupport.isConfigType(item)
    }

    private fun applyItem(item: DlcItem, wasEnabled: Boolean): List<Path> {
        val statePaths = ArrayList<Path>()
        try {
            if (item.getType() == DlcType.UNKNOWN) {
                val target = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw()) ?: return statePaths
                val sources = item.getSourcePaths()
                if (sources.isEmpty()) {
                    return statePaths
                }
                val dirTarget = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), target, sources[0])
                if (!dirTarget && sources.size > 1) {
                    return statePaths
                }
                if (DlcConfigTypeSupport.isConfigType(item)) {
                    return applyConfigItem(item, sources, target, dirTarget, wasEnabled)
                }
                if (dirTarget) {
                    Files.createDirectories(target)
                    for (source in sources) {
                        val resolved = target.resolve(source.fileName.toString())
                        if (!wasEnabled && Files.exists(resolved)) {
                            backupExisting(resolved)
                        }
                        copyRecursive(source, resolved)
                        statePaths.add(resolved)
                    }
                } else {
                    val parent = target.parent
                    if (parent != null) {
                        Files.createDirectories(parent)
                    }
                    val source = sources[0]
                    if (!wasEnabled && Files.exists(target)) {
                        backupExisting(target)
                    }
                    copyRecursive(source, target)
                    statePaths.add(target)
                }
                return statePaths
            }
            val targetDirs = when (item.getType()) {
                DlcType.MOD -> listOf(DlcPaths.modsDir())
                DlcType.RESOURCE_PACK -> DlcConfig.getResourcepackRoots()
                DlcType.DATA_PACK -> DlcConfig.getDatapackRoots()
                DlcType.TACZ_PACK -> listOf(DlcPaths.taczDir())
                else -> emptyList()
            }
            for (targetDir in targetDirs) {
                Files.createDirectories(targetDir)
                val sourcePath = item.getSourcePath() ?: continue
                val target = targetDir.resolve(sourcePath.fileName.toString())
                if (!wasEnabled && Files.exists(target)) {
                    backupExisting(target)
                }
                copyRecursive(sourcePath, target)
                statePaths.add(target)
            }
        } catch (ignored: IOException) {
            recordApplyFailure(ignored)
        }

        return statePaths
    }

    @Throws(IOException::class)
    private fun applyConfigItem(item: DlcItem, sources: List<Path>, targetRoot: Path, dirTarget: Boolean, wasEnabled: Boolean): List<Path> {
        val statePaths = ArrayList<Path>()
        if (dirTarget) {
            Files.createDirectories(targetRoot)
            for (source in sources) {
                val target = targetRoot.resolve(source.fileName.toString())
                val statePath = backupConfigTarget(item.getId(), target, !wasEnabled)
                copyRecursive(source, target)
                statePaths.add(statePath)
            }
            return statePaths
        }
        val parent = targetRoot.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val source = sources[0]
        val statePath = backupConfigTarget(item.getId(), targetRoot, !wasEnabled)
        copyRecursive(source, targetRoot)
        statePaths.add(statePath)
        return statePaths
    }

    @Throws(IOException::class)
    private fun backupConfigTarget(itemId: String?, target: Path, shouldCreate: Boolean): Path {
        val backupPath = DlcConfigTypeSupport.backupPathForItemTarget(itemId, target)
        val markerPath = DlcConfigTypeSupport.missingMarkerPath(backupPath)
        if (shouldCreate) {
            deleteRecursiveIfExists(backupPath)
            deleteRecursiveIfExists(markerPath)
            Files.createDirectories(DlcConfigTypeSupport.backupRootForItem(itemId))
            if (Files.exists(target)) {
                copyRecursive(target, backupPath)
                return backupPath
            }
            Files.writeString(markerPath, CONFIG_MISSING_MARKER)
            return markerPath
        }
        if (Files.exists(markerPath)) {
            return markerPath
        }
        return backupPath
    }

    private fun removeItem(item: DlcItem, previous: DlcStateConfig.DlcState?): RemovalResult {
        val messages = ArrayList<String>()
        if (previous == null || previous.appliedTargets == null) {
            return RemovalResult(messages)
        }
        if (DlcConfigTypeSupport.isConfigType(item)) {
            restoreConfigItem(item, previous, messages)
            return RemovalResult(messages)
        }
        val failedPaths = ArrayList<String>()
        for (pathString in previous.appliedTargets ?: emptyList()) {
            val resolved = resolveStoredPath(pathString)
            try {
                deleteRecursive(resolved)
            } catch (ignored: IOException) {
                val message = ignored.message
                if (message != null && message.startsWith("Failed to ")) {
                    messages.add(message)
                    continue
                }
                failedPaths.add(DlcPathUtil.toGameRelativePath(resolved))
            }
        }
        if (failedPaths.isNotEmpty()) {
            messages.add("Failed to remove some files for " + item.getName() + ": " + failedPaths.joinToString(", "))
        }
        return RemovalResult(messages)
    }

    private fun restoreConfigItem(item: DlcItem, previous: DlcStateConfig.DlcState, messages: MutableList<String>) {
        val targets = resolveConfigTargets(item)
        val stored = previous.appliedTargets ?: emptyList()
        for (i in targets.indices) {
            val target = targets[i]
            val fallbackBackup = DlcConfigTypeSupport.backupPathForItemTarget(item.getId(), target)
            val fallbackMarker = DlcConfigTypeSupport.missingMarkerPath(fallbackBackup)
            var statePath = if (i < stored.size) resolveStoredPath(stored[i]) else null
            if (statePath == null || statePath.toString().isBlank()) {
                statePath = if (Files.exists(fallbackMarker)) {
                    fallbackMarker
                } else if (Files.exists(fallbackBackup)) {
                    fallbackBackup
                } else {
                    val legacyBackup = DlcConfigTypeSupport.backupPathForTarget(target)
                    val legacyMarker = DlcConfigTypeSupport.missingMarkerPath(legacyBackup)
                    if (Files.exists(legacyMarker)) legacyMarker else legacyBackup
                }
            }
            var backupPath = statePath
            var markerPath = statePath
            if (Files.exists(statePath) && statePath.fileName != null && statePath.fileName.toString().endsWith(".missing")) {
                backupPath = fallbackBackup
                if (!Files.exists(backupPath)) {
                    backupPath = DlcConfigTypeSupport.backupPathForTarget(target)
                }
            } else if (statePath == fallbackBackup) {
                markerPath = fallbackMarker
            } else if (statePath == DlcConfigTypeSupport.backupPathForTarget(target)) {
                markerPath = DlcConfigTypeSupport.missingMarkerPath(statePath)
            } else {
                markerPath = DlcConfigTypeSupport.missingMarkerPath(statePath)
            }
            try {
                if (isMissingMarker(statePath) || isMissingMarker(markerPath)) {
                    deleteRecursiveIfExists(target)
                } else if (Files.exists(backupPath)) {
                    copyRecursive(backupPath, target)
                } else {
                    deleteRecursiveIfExists(target)
                }
                deleteRecursiveIfExists(backupPath)
                deleteRecursiveIfExists(markerPath)
            } catch (ignored: IOException) {
                val message = ignored.message
                if (message != null && message.startsWith("Failed to ")) {
                    messages.add(message)
                } else {
                    messages.add("Failed to restore: " + DlcPathUtil.toGameRelativePath(target))
                }
            }
        }
    }

    private fun isMissingMarker(path: Path?): Boolean {
        if (path == null || !Files.isRegularFile(path)) {
            return false
        }
        val name = path.fileName?.toString() ?: ""
        if (name.endsWith(".missing")) {
            return true
        }
        return try {
            CONFIG_MISSING_MARKER == Files.readString(path).trim()
        } catch (_: IOException) {
            false
        }
    }

    private fun resolveConfigTargets(item: DlcItem?): List<Path> {
        val targets = ArrayList<Path>()
        if (item == null) {
            return targets
        }
        val targetRoot = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw()) ?: return targets
        val sources = item.getSourcePaths()
        if (sources.isEmpty()) {
            return targets
        }
        val dirTarget = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), targetRoot, sources[0])
        if (!dirTarget && sources.size > 1) {
            return targets
        }
        if (dirTarget) {
            for (source in sources) {
                targets.add(targetRoot.resolve(source.fileName.toString()))
            }
        } else {
            targets.add(targetRoot)
        }
        return targets
    }

    @Throws(IOException::class)
    private fun copyRecursive(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(dir)
                        Files.createDirectories(target.resolve(relative))
                        return FileVisitResult.CONTINUE
                    }

                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(file)
                        copyWithHelperFallback(file, target.resolve(relative))
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } else {
            copyWithHelperFallback(source, target)
        }
    }

    private fun backupExisting(target: Path?) {
        if (target == null || !Files.exists(target)) {
            return
        }
        var backupRoot: Path? = null
        if (DlcConfig.getDlcRoots().isNotEmpty()) {
            backupRoot = DlcTmpPaths.resolve(DlcConfig.getDlcRoots()[0], "backup")
        }
        if (backupRoot == null) {
            backupRoot = DlcTmpPaths.resolve(DlcPaths.gameDir(), "backup")
        }
        if (backupRoot == null) {
            backupRoot = DlcPaths.gameDir().resolve("tmp").resolve("backup")
        }
        try {
            Files.createDirectories(backupRoot)
            val name = target.fileName.toString() + "." + System.currentTimeMillis() + ".bak"
            val backupTarget = backupRoot.resolve(name)
            copyRecursive(target, backupTarget)
        } catch (_: IOException) {
        }
    }

    @Throws(IOException::class)
    private fun deleteRecursiveIfExists(path: Path?) {
        if (path == null || !Files.exists(path)) {
            return
        }
        deleteRecursive(path)
    }

    @Throws(IOException::class)
    private fun deleteRecursive(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    deleteWithHelperFallback(file)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    deleteWithHelperFallback(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    @Throws(IOException::class)
    private fun copyWithHelperFallback(source: Path?, target: Path?) {
        if (source == null || target == null) {
            return
        }
        if (target.parent != null) {
            Files.createDirectories(target.parent)
        }
        if (!DlcFileOpsHelperClient.shouldUseHelper()) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        if (DlcFileOpsHelperClient.cancelPendingDelete(target) && Files.exists(target)) {
            return
        }
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: IOException) {
            val result = DlcFileOpsHelperClient.scheduleCopy(source, target, true)
            if (result.ok()) {
                markHelperScheduled()
                return
            }
            throw IOException(result.message(), exception)
        }
    }

    @Throws(IOException::class)
    private fun moveWithHelperFallback(source: Path?, target: Path?, replaceExisting: Boolean) {
        if (source == null || target == null) {
            return
        }
        if (target.parent != null) {
            Files.createDirectories(target.parent)
        }
        if (!DlcFileOpsHelperClient.shouldUseHelper()) {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
            return
        }
        try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
        } catch (exception: IOException) {
            val result = DlcFileOpsHelperClient.scheduleMove(source, target, replaceExisting)
            if (result.ok()) {
                markHelperScheduled()
                return
            }
            throw IOException(result.message(), exception)
        }
    }

    @Throws(IOException::class)
    private fun deleteWithHelperFallback(path: Path?) {
        if (path == null) {
            return
        }
        if (!DlcFileOpsHelperClient.shouldUseHelper()) {
            Files.deleteIfExists(path)
            return
        }
        try {
            Files.deleteIfExists(path)
        } catch (exception: IOException) {
            val result = DlcFileOpsHelperClient.scheduleDelete(path)
            if (result.ok()) {
                markHelperScheduled()
                return
            }
            throw IOException(result.message(), exception)
        }
    }

    private fun recordApplyFailure(exception: IOException?) {
        if (exception == null) {
            return
        }
        val message = exception.message
        if (message.isNullOrBlank()) {
            return
        }
        val context = CURRENT_APPLY_CONTEXT.get()
        if (context == null || context.messages.contains(message)) {
            return
        }
        context.messages.add(message)
    }

    private fun markHelperScheduled() {
        val context = CURRENT_APPLY_CONTEXT.get()
        if (context != null) {
            context.helperScheduled = true
        }
    }

    private fun resolveStoredPath(rawPath: String?): Path {
        if (rawPath.isNullOrBlank()) {
            return Path.of("")
        }
        val path = Path.of(rawPath)
        return if (path.isAbsolute) path.normalize() else DlcPaths.gameDir().resolve(path).normalize()
    }

    private class RemovalResult(val messages: List<String>)

    private class ApplyContext(val messages: MutableList<String>) {
        var helperScheduled: Boolean = false
    }

    private class PresetConflictPathResult(val messages: List<String>, val changed: Boolean)

    private class DlcConflictPathResult(val messages: List<String>, val changed: Boolean)

    class ApplyResult(
        val messages: List<String>,
        val requiresRestart: Boolean,
        val hotReloaded: Boolean,
        val helperScheduled: Boolean,
    ) {
        fun messages(): List<String> = messages
        fun requiresRestart(): Boolean = requiresRestart
        fun hotReloaded(): Boolean = hotReloaded
        fun helperScheduled(): Boolean = helperScheduled
    }
}
