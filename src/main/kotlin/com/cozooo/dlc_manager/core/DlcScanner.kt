package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class DlcScanner {
    fun scan(): MutableMap<DlcType?, MutableList<DlcItem?>?> {
        return scan(false)
    }

    fun scan(detectInstalledState: Boolean): MutableMap<DlcType?, MutableList<DlcItem?>?> {
        DlcFileMatch.beginScan()
        try {
            val grouped: MutableMap<DlcType?, MutableList<DlcItem?>?> = EnumMap<DlcType, MutableList<DlcItem?>?>(DlcType::class.java)
            for (type in DlcType.values()) {
                grouped[type] = ArrayList()
            }

            val roots = DlcConfig.getDlcRoots()
            val taczRoots = DlcConfig.getTaczDlcRoots()
            val tooltipLoader = DlcTooltipLoader()
            val mainDlcRoot = if (DlcConfig.getDlcRoots().isEmpty()) null else DlcConfig.getDlcRoots()[0]
            val mainInfoDir = mainDlcRoot?.resolve("info")
            val tooltips = if (mainDlcRoot == null) emptyMap() else tooltipLoader.loadTooltips(mainDlcRoot)
            val seen = HashSet<Path>()
            for (root in roots) {
                if (!Files.exists(root) || seen.contains(root)) {
                    continue
                }
                seen.add(root)
                scanRoot(grouped, root, taczRoots, tooltips, tooltipLoader, mainInfoDir, detectInstalledState)
            }

            if (DlcPlatformServices.isModLoaded("tacz")) {
                scanTaczRoots(grouped, tooltipLoader, detectInstalledState)
            }

            return grouped
        } finally { DlcFileMatch.endScan() }
    }

    private fun scanRoot(
        grouped: MutableMap<DlcType?, MutableList<DlcItem?>?>,
        root: Path,
        taczRoots: List<Path>,
        tooltips: Map<String, DlcTooltipLoader.TooltipData>,
        tooltipLoader: DlcTooltipLoader,
        infoDir: Path?,
        detectInstalledState: Boolean,
    ) {
        val localInfoDir = root.resolve("info")
        val localRequiredDir = root.resolve("required")
        val localRemoteDir = root.resolve("remote")
        val localPresetDir = root.resolve("preset")
        val localUpdateDir = root.resolve("update")
        val localTmpDir = DlcTmpPaths.root(root)
        val localLegacyTmpDir = DlcTmpPaths.legacyRoot(root)
        val groupedOwners = buildGroupedOwners(tooltips)
        val remoteMetadata = buildRemoteMetadata(root)
        try {
            Files.walkFileTree(
                root,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (dir.normalize().startsWith(root.resolve("downloaded/required").normalize())) return FileVisitResult.SKIP_SUBTREE
                        if (root == dir) {
                            return FileVisitResult.CONTINUE
                        }
                        if (isUnderInfoDir(dir, infoDir) || isUnderInfoDir(dir, localInfoDir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (isUnderRequiredDir(dir, localRequiredDir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (isUnderRemoteDir(dir, localRemoteDir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (isUnderPresetDir(dir, localPresetDir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (isUnderUpdateDir(dir, localUpdateDir) || isUnderUpdateDir(dir, localTmpDir) || isUnderUpdateDir(dir, localLegacyTmpDir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (isUnderTaczRoot(dir, taczRoots)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }

                        val type = classifyDirectory(dir)
                        if (type != null) {
                            addItem(grouped, root, dir, type, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState)
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (isUnderInfoDir(file, infoDir) || isUnderInfoDir(file, localInfoDir)) {
                            return FileVisitResult.CONTINUE
                        }
                        if (isUnderRequiredDir(file, localRequiredDir)) {
                            return FileVisitResult.CONTINUE
                        }
                        if (isUnderRemoteDir(file, localRemoteDir)) {
                            return FileVisitResult.CONTINUE
                        }
                        if (isUnderPresetDir(file, localPresetDir)) {
                            return FileVisitResult.CONTINUE
                        }
                        if (isUnderUpdateDir(file, localUpdateDir) || isUnderUpdateDir(file, localTmpDir) || isUnderUpdateDir(file, localLegacyTmpDir)) {
                            return FileVisitResult.CONTINUE
                        }
                        if (isUnderTaczRoot(file, taczRoots)) {
                            return FileVisitResult.CONTINUE
                        }
                        val name = file.fileName.toString()
                        if (DlcDcFormatUtil.FOLDER_METADATA_FILE_NAME.equals(name, true)) return FileVisitResult.CONTINUE
                        val relative = root.relativize(file).toString().replace('\\', '/')
                        val owner = groupedOwners[relative]
                        if (owner != null && owner != name) {
                            return FileVisitResult.CONTINUE
                        }
                        if (name.endsWith(".jar")) {
                            addItem(grouped, root, file, DlcType.MOD, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState)
                            return FileVisitResult.CONTINUE
                        }
                        if (name.endsWith(".zip")) {
                            val type = classifyZip(file)
                            addItem(grouped, root, file, type ?: DlcType.UNKNOWN, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState)
                            return FileVisitResult.CONTINUE
                        }
                        if (tooltips.containsKey(name) || remoteMetadata.contains(name)) {
                            addItem(grouped, root, file, DlcType.UNKNOWN, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState)
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (_: IOException) {
        }
    }

    private fun isUnderTaczRoot(path: Path, taczRoots: List<Path>): Boolean {
        val normalized = path.normalize()
        for (taczRoot in taczRoots) {
            val root = taczRoot.normalize()
            if (normalized.startsWith(root)) {
                return true
            }
        }
        return false
    }

    private fun isUnderInfoDir(path: Path, infoDir: Path?): Boolean {
        if (infoDir == null) {
            return false
        }
        return path.normalize().startsWith(infoDir.normalize())
    }

    private fun isUnderRequiredDir(path: Path, requiredDir: Path?): Boolean {
        if (requiredDir == null) {
            return false
        }
        return path.normalize().startsWith(requiredDir.normalize())
    }

    private fun isUnderUpdateDir(path: Path, updateDir: Path?): Boolean {
        if (updateDir == null) {
            return false
        }
        return path.normalize().startsWith(updateDir.normalize())
    }

    private fun isUnderRemoteDir(path: Path, remoteDir: Path?): Boolean {
        if (remoteDir == null) {
            return false
        }
        return path.normalize().startsWith(remoteDir.normalize())
    }

    private fun isUnderPresetDir(path: Path, presetDir: Path?): Boolean {
        if (presetDir == null) {
            return false
        }
        return path.normalize().startsWith(presetDir.normalize())
    }

    private fun addItem(
        grouped: MutableMap<DlcType?, MutableList<DlcItem?>?>,
        root: Path,
        source: Path,
        detectedType: DlcType,
        tooltips: Map<String, DlcTooltipLoader.TooltipData>,
        remoteMetadata: RemoteMetadataLookup,
        tooltipLoader: DlcTooltipLoader,
        infoDir: Path?,
        detectInstalledState: Boolean,
    ) {
        if (source.toAbsolutePath().normalize() in remoteMetadata.shadowed) return
        val relative = root.relativize(source).toString().replace('\\', '/').removePrefix("downloaded/DLCs/")
        val rootId = DlcPathUtil.toGameRelativePath(root).replace('\\', '/')
        val name = source.fileName.toString()
        var tooltipData = remoteMetadata.get(relative) ?: remoteMetadata.get(name)
        if (tooltipData == null) {
            tooltipData = tooltips[name]
        }
        var fileNames = tooltipData?.getFileNames() ?: emptyList()
        if (fileNames.isNotEmpty() && fileNames.none { it == name }) {
            val merged = ArrayList(fileNames)
            merged.add(0, name)
            fileNames = merged
        }
        var effectiveType = detectedType
        var typeValid = false
        if (tooltipData != null) {
            typeValid = tooltipData.isTypeValid()
            val tooltipType = tooltipData.getType()
            if (tooltipData.isTypeValid() && tooltipType != null) {
                effectiveType = tooltipType
            }
        }
        if (detectedType == DlcType.TACZ_PACK && effectiveType != DlcType.TACZ_PACK) {
            effectiveType = detectedType
            typeValid = false
        }
        val id = effectiveType.name.lowercase() + ":" + rootId + "|" + relative
        var identifier = tooltipData?.getIdentifier()
        if (identifier.isNullOrBlank()) {
            identifier = name
        }
        val appliedTargetRaw = tooltipData?.getAppliedTargetRaw()
        val configType = tooltipData != null && tooltipData.isConfigType()
        if (effectiveType == DlcType.UNKNOWN && appliedTargetRaw.isNullOrBlank()) {
            return
        }
        val enabled = isEnabledInState(id)
        val tips = tooltipData?.getTips()
        val hidden = tooltipData != null && tooltipData.isHidden()
        var displayNames = tooltipData?.getDisplayName()
        if (displayNames.isNullOrEmpty()) {
            displayNames = mapOf("en_us" to name)
        }
        val dependencies = tooltipData?.getDependencies() ?: emptyList()
        val conflicts = tooltipData?.getConflicts() ?: emptyList()
        val alarm = tooltipData != null && tooltipData.isAlarm()
        val allowEnableByAll = tooltipData == null || tooltipData.AllowEnableByAll()
        val sources = ArrayList<Path>()
        if (fileNames.isNotEmpty() && effectiveType == DlcType.UNKNOWN) {
            for (entry in fileNames) {
                if (entry.isNullOrBlank()) {
                    continue
                }
                val resolved = root.resolve(entry.replace('\\', '/'))
                sources.add(resolved)
            }
        }
        val item = if (sources.isEmpty()) {
            DlcItem(id, identifier, name, effectiveType, source, appliedTargetRaw, enabled, tips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, configType, alarm)
        } else {
            DlcItem(id, identifier, name, effectiveType, source, sources, appliedTargetRaw, enabled, tips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, configType, alarm)
        }
        if (tooltipData != null) {
            item.setRedirectUrl(tooltipData.getRedirectUrl())
            item.setAlarmTips(tooltipData.getAlarmTips())
        }
        item.setDlcRoot(if (root in DlcConfig.getDlcRoots()) root else DlcConfig.getDlcRoot())
        item.setGroupDirectory(remoteMetadata.groupDirectories[relative])
        item.setMatchMode(tooltipData?.getMatchMode() ?: "hash")
        if (detectInstalledState) {
            try {
                val installed = DlcItemFiles.installed(item)
                item.setEnabled(installed.isNotEmpty())
                item.getAppliedTargets().addAll(installed)
            } catch (_: IOException) { item.setEnabled(false) }
        }
        grouped[effectiveType]?.add(item)
    }

    private fun isEnabledInState(id: String): Boolean {
        val state = DlcStateConfig.getState(id)
        return state != null && state.enabled
    }

    private fun buildGroupedOwners(tooltips: Map<String, DlcTooltipLoader.TooltipData>?): Map<String, String> {
        val owners = HashMap<String, String>()
        if (tooltips.isNullOrEmpty()) {
            return owners
        }
        for ((primary, data) in tooltips) {
            if (primary.isBlank()) {
                continue
            }
            if (data.getType() != DlcType.UNKNOWN) {
                continue
            }
            val fileNames = data.getFileNames()
            if (fileNames.isNullOrEmpty()) {
                continue
            }
            owners[primary] = primary
            for (name in fileNames) {
                if (name.isNullOrBlank()) {
                    continue
                }
                owners[name.replace('\\', '/')] = primary
            }
        }
        return owners
    }

    private fun classifyDirectory(dir: Path): DlcType? {
        val packMeta = dir.resolve("pack.mcmeta")
        val hasPack = Files.exists(packMeta)
        val hasAssets = Files.exists(dir.resolve("assets"))
        val hasData = Files.exists(dir.resolve("data"))

        if (hasPack && (hasAssets || hasData)) {
            if (hasAssets && !hasData) {
                return DlcType.RESOURCE_PACK
            }
            if (hasData && !hasAssets) {
                return DlcType.DATA_PACK
            }
            return DlcType.RESOURCE_PACK
        }

        return null
    }

    private fun classifyZip(zipPath: Path): DlcType? {
        try {
            ZipFile(zipPath.toFile()).use { zipFile ->
                val hasPack = zipFile.getEntry("pack.mcmeta") != null
                val hasAssets = hasZipPrefix(zipFile, "assets/")
                val hasData = hasZipPrefix(zipFile, "data/")

                if (hasPack && (hasAssets || hasData)) {
                    if (hasAssets && !hasData) {
                        return DlcType.RESOURCE_PACK
                    }
                    if (hasData && !hasAssets) {
                        return DlcType.DATA_PACK
                    }
                    return DlcType.RESOURCE_PACK
                }
            }
        } catch (_: IOException) {
            return null
        }
        return null
    }

    private fun hasZipPrefix(zipFile: ZipFile, prefix: String): Boolean {
        return zipFile.stream().map(ZipEntry::getName).anyMatch { name -> name.startsWith(prefix) }
    }

    private fun scanTaczRoots(
        grouped: MutableMap<DlcType?, MutableList<DlcItem?>?>,
        tooltipLoader: DlcTooltipLoader,
        detectInstalledState: Boolean,
    ) {
        val mainDlcRoot = if (DlcConfig.getDlcRoots().isEmpty()) null else DlcConfig.getDlcRoots()[0]
        val mainInfoDir = mainDlcRoot?.resolve("info")
        val tooltips = if (mainDlcRoot == null) emptyMap() else tooltipLoader.loadTooltips(mainDlcRoot)

        for (root in DlcConfig.getTaczDlcRoots()) {
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                continue
            }
            try {
                Files.list(root).use { stream ->
                    stream.forEach { path ->
                        val name = path.fileName.toString()
                        if (Files.isDirectory(path)) {
                            addItem(grouped, root, path, DlcType.TACZ_PACK, emptyMap(), RemoteMetadataLookup.empty(), tooltipLoader, mainInfoDir, detectInstalledState)
                        } else if (name.endsWith(".zip")) {
                            addItem(grouped, root, path, DlcType.TACZ_PACK, emptyMap(), RemoteMetadataLookup.empty(), tooltipLoader, mainInfoDir, detectInstalledState)
                        }
                    }
                }
            } catch (_: IOException) {
            }
        }
    }

    private fun buildRemoteMetadata(root: Path?): RemoteMetadataLookup {
        if (root == null || !Files.isDirectory(root)) {
            return RemoteMetadataLookup.empty()
        }
        val entries = DlcRemoteLoader.load(root)
        if (entries.isEmpty()) {
            return RemoteMetadataLookup.empty()
        }
        val byFileName = HashMap<String, DlcTooltipLoader.TooltipData>()
        val lookup = RemoteMetadataLookup(byFileName)
        val ambiguousFileNames = HashSet<String>()
        for (entry: DlcRemoteEntry? in entries) {
            if (entry == null) {
                continue
            }
            val fileName = entry.getFileNameHint()
            if (fileName.isNullOrBlank()) {
                continue
            }
            val newPath = entry.resolveTargetPath(fileName)
            val legacy = if (entry.getType() == DlcType.DLC_PACK) root.resolve("pack").resolve(fileName) else entry.getBaseDir()?.resolve(fileName)
            if (newPath != null && Files.exists(newPath) && legacy != null) lookup.shadowed.add(legacy.toAbsolutePath().normalize())
            if (entry.getType() == DlcType.DLC_PACK) {
                if (newPath != null) lookup.shadowed.add(newPath.toAbsolutePath().normalize())
                if (legacy != null) lookup.shadowed.add(legacy.toAbsolutePath().normalize())
                continue
            }
            var identifier = entry.getDlcIdentifier()
            if (identifier.isNullOrBlank()) {
                identifier = fileName
            }
            val type = entry.getType() ?: DlcType.MOD
            val data = DlcTooltipLoader.TooltipData(
                identifier,
                entry.getTips(),
                entry.getDisplayNames(),
                type,
                true,
                true,
                entry.isConfigType(),
                entry.isHidden(),
                entry.isAllowEnableByAll(),
                entry.getResolvedDependencyNames(),
                entry.getConflictNames(),
                entry.getAppliedTargetRaw(),
                entry.getRedirectUrl(),
                emptyList(),
                entry.isAlarm(),
            )
            data.setAlarmTips(entry.getAlarmTips())
            data.setMatchMode(entry.getMatchMode())
            if (legacy != null) {
                val relative = root.toAbsolutePath().normalize().relativize(legacy.toAbsolutePath().normalize()).toString().replace('\\', '/')
                lookup.byPath[relative] = data
                entry.getConfigPath()?.parent?.let { lookup.groupDirectories[relative] = it }
            }
            if (ambiguousFileNames.contains(fileName)) {
                continue
            }
            val existing = byFileName[fileName]
            if (existing == null) {
                byFileName[fileName] = data
            } else {
                byFileName.remove(fileName)
                ambiguousFileNames.add(fileName)
            }
        }
        return lookup
    }

    private class RemoteMetadataLookup(
        private val byFileName: Map<String, DlcTooltipLoader.TooltipData>,
    ) {
        companion object {
            @JvmStatic
            fun empty(): RemoteMetadataLookup {
                return RemoteMetadataLookup(emptyMap())
            }
        }

        val byPath = HashMap<String, DlcTooltipLoader.TooltipData>()
        val groupDirectories = HashMap<String, Path>()
        val shadowed = HashSet<Path>()
        fun get(fileName: String?): DlcTooltipLoader.TooltipData? {
            if (fileName.isNullOrBlank()) {
                return null
            }
            return byPath[fileName] ?: byFileName[fileName]
        }

        fun contains(fileName: String?): Boolean {
            return get(fileName) != null
        }
    }

}
