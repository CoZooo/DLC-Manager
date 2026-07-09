package com.cozooo.dlc_manager.client

import com.cozooo.dlc_manager.core.DlcStateConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcScanner
import java.nio.file.Path

object DlcStartupStateSync {
    private var syncedThisLaunch = false

    @JvmStatic
    @Synchronized
    fun syncOnStartup() {
        if (syncedThisLaunch) {
            return
        }
        syncedThisLaunch = true
        var changed = false
        val grouped = DlcScanner().scan(true)
        for (items in grouped.values) {
            if (items == null || items.isEmpty()) {
                continue
            }
            for (item in items) {
                if (item == null) {
                    continue
                }
                val current = DlcStateConfig.getState(item.getId())
                val enabled = item.isEnabled()
                if (enabled) {
                    val appliedTargets = item.getAppliedTargets()
                        .mapNotNull(::toStoredPath)
                        .filter { it.isNotBlank() }
                    if (current != null && current.enabled && sameTargets(current.appliedTargets, appliedTargets)) {
                        continue
                    }
                    val next = DlcStateConfig.DlcState()
                    next.enabled = true
                    next.appliedTargets = appliedTargets
                    DlcStateConfig.setState(item.getId(), next)
                    changed = true
                    continue
                }
                if (current != null && current.enabled) {
                    val next = DlcStateConfig.DlcState()
                    next.enabled = false
                    next.appliedTargets = emptyList()
                    DlcStateConfig.setState(item.getId(), next)
                    changed = true
                }
            }
        }
        if (changed) {
            DlcStateConfig.save()
        }
    }

    private fun sameTargets(left: List<String>?, right: List<String>?): Boolean {
        val a = left ?: emptyList()
        val b = right ?: emptyList()
        if (a.size != b.size) {
            return false
        }
        for (i in a.indices) {
            if (normalize(a[i]) != normalize(b[i])) {
                return false
            }
        }
        return true
    }

    private fun normalize(raw: String?): String {
        if (raw == null || raw.isBlank()) {
            return ""
        }
        val path = DlcPaths.parsePathLenient(raw) ?: return raw
        if (path.isAbsolute) {
            return path.normalize().toString()
        }
        return DlcPaths.gameDir().resolve(path).normalize().toString()
    }

    private fun toStoredPath(path: Path?): String? {
        if (path == null) {
            return null
        }
        val gameDir = DlcPaths.gameDir().toAbsolutePath().normalize()
        val normalized = path.toAbsolutePath().normalize()
        if (normalized.startsWith(gameDir)) {
            return gameDir.relativize(normalized).toString()
        }
        return normalized.toString()
    }
}
