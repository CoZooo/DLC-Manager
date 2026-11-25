package com.cozooo.dlc_manager.core

import java.io.IOException
import java.nio.file.Path

object DlcItemFiles {
    class Target(private val source: Path, private val target: Path, private val searchByHash: Boolean) {
        fun source(): Path = source
        fun target(): Path = target
        fun searchByHash(): Boolean = searchByHash
    }

    @JvmStatic fun expected(item: DlcItem): List<Target> {
        return expected(item, true)
    }

    @JvmStatic fun expected(item: DlcItem, allowCollectionDownloads: Boolean): List<Target> {
        return expected(item, allowCollectionDownloads, HashSet())
    }

    private fun expected(item: DlcItem, allowCollectionDownloads: Boolean, visited: MutableSet<String>): List<Target> {
        val result = ArrayList<Target>()
        val id = item.getId()
        if (id != null && !visited.add(id)) return result
        try {
            if (item.getType() == DlcType.COLLECTION) {
            val sources = item.getSourcePaths().ifEmpty { listOfNotNull(item.getSourcePath()) }
            for (source in sources) {
                for (entry in DlcCollectionSource.entries(source, item.getDlcRoot(), DlcPaths.gameDir(), allowCollectionDownloads, item.getIdentifier(), item.getCollectionRemoteEntries())) {
                    result.add(Target(entry.source, DlcPathUtil.resolveInside(DlcPaths.gameDir(), entry.relativePath), false))
                }
            }
            for (included in item.getCollectionIncludedItems()) {
                result.addAll(expected(included, allowCollectionDownloads, visited))
            }
            } else if (item.getType() == DlcType.UNKNOWN) {
            val target = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw()) ?: return result
            val sources = item.getSourcePaths()
            if (sources.isEmpty()) return result
            val directory = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), target, sources[0])
            if (!directory && sources.size > 1) return result
            for (source in sources) result.add(Target(source, if (directory) target.resolve(source.fileName) else target, false))
            } else {
            val source = item.getSourcePath() ?: return result
            val directories = when (item.getType()) {
                DlcType.MOD -> listOf(DlcPaths.modsDir())
                DlcType.RESOURCE_PACK -> DlcConfig.getResourcepackRoots()
                DlcType.DATA_PACK -> DlcConfig.getDatapackRoots()
                DlcType.TACZ_PACK -> DlcConfig.getTaczRoots()
                else -> emptyList()
            }
            for (directory in directories) result.add(Target(source, directory.resolve(source.fileName), true))
            }
            return result
        } finally {
            if (id != null) visited.remove(id)
        }
    }

    @JvmStatic @Throws(IOException::class)
    fun installed(item: DlcItem): List<Path> {
        val result = ArrayList<Path>()
        for (pair in expected(item, false)) {
            val target = if (pair.searchByHash()) DlcFileMatch.find(pair.source(), pair.target().parent, item.getMatchMode())
                else if (DlcFileMatch.matches(pair.source(), pair.target(), item.getMatchMode())) pair.target() else null
            if (target == null) return emptyList()
            if (item.getType() == DlcType.RESOURCE_PACK && !com.cozooo.dlc_manager.platform.DlcPlatformServices.isResourcePackSelected(target.fileName.toString())) return emptyList()
            result.add(target)
        }
        return result
    }
}
