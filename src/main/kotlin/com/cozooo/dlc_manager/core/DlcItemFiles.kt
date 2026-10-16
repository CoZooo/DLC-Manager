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
        val result = ArrayList<Target>()
        if (item.getType() == DlcType.UNKNOWN) {
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
                DlcType.TACZ_PACK -> listOf(DlcPaths.taczDir())
                else -> emptyList()
            }
            for (directory in directories) result.add(Target(source, directory.resolve(source.fileName), true))
        }
        return result
    }

    @JvmStatic @Throws(IOException::class)
    fun installed(item: DlcItem): List<Path> {
        val result = ArrayList<Path>()
        for (pair in expected(item)) {
            val target = if (pair.searchByHash()) DlcFileMatch.find(pair.source(), pair.target().parent, item.getMatchMode())
                else if (DlcFileMatch.matches(pair.source(), pair.target(), item.getMatchMode())) pair.target() else null
            if (target == null) return emptyList()
            if (item.getType() == DlcType.RESOURCE_PACK && !com.cozooo.dlc_manager.platform.DlcPlatformServices.isResourcePackSelected(target.fileName.toString())) return emptyList()
            result.add(target)
        }
        return result
    }
}
