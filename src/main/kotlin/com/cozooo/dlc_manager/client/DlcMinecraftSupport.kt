package com.cozooo.dlc_manager.client

import net.minecraft.client.Minecraft
import net.minecraft.server.packs.repository.PackRepository

object DlcMinecraftSupport {
    @JvmStatic
    fun isResourcePackSelected(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) {
            return false
        }
        return try {
            val minecraft = Minecraft.getInstance()
            val repo = minecraft.resourcePackRepository
            val packId = resolvePackId(repo, fileName) ?: return false
            repo.selectedIds.contains(packId)
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolvePackId(repo: PackRepository?, fileName: String): String? {
        if (repo == null) {
            return null
        }
        val stripped = stripArchiveExtension(fileName)
        val candidates = arrayOf(
            "file/$fileName",
            "file/$stripped",
            "vanilla/$fileName",
            "vanilla/$stripped",
        )
        for (candidate in candidates) {
            if (repo.getPack(candidate) != null) {
                return candidate
            }
        }
        return repo.availableIds.stream()
            .filter { id -> id.endsWith("/$fileName") || id.endsWith("/$stripped") }
            .findFirst()
            .orElse(null)
    }

    private fun stripArchiveExtension(fileName: String): String {
        val lower = fileName.lowercase()
        return if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
            fileName.substring(0, fileName.length - 4)
        } else {
            fileName
        }
    }
}
