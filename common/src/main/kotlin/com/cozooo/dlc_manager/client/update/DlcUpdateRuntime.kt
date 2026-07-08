package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.nio.file.Path

object DlcUpdateRuntime {
    private var promptedThisSession = false

    class ClientUpdateSnapshot private constructor(
        baseVersion: String?,
        currentVersion: String?,
        @JvmField val rollbackFrom: String?,
        @JvmField val rolledBackMode: Boolean,
        @JvmField val updateCheck: DlcUpdateService.UpdateCheckResult?,
        history: List<DlcUpdateMeta>?,
    ) {
        @JvmField val baseVersion: String = baseVersion ?: ""
        @JvmField val currentVersion: String = currentVersion ?: ""
        @JvmField val history: List<DlcUpdateMeta> = history ?: emptyList()

        companion object {
            fun create(
                baseVersion: String?,
                currentVersion: String?,
                rollbackFrom: String?,
                rolledBackMode: Boolean,
                updateCheck: DlcUpdateService.UpdateCheckResult?,
                history: List<DlcUpdateMeta>?,
            ): ClientUpdateSnapshot {
                return ClientUpdateSnapshot(baseVersion, currentVersion, rollbackFrom, rolledBackMode, updateCheck, history)
            }
        }
    }

    @JvmStatic
    fun pollTitlePromptCandidate(): DlcUpdateService.UpdateCheckResult? {
        if (promptedThisSession) {
            return null
        }
        val result = DlcUpdateService.checkForUpdates()
        if (!result.hasUpdates()) {
            return null
        }
        if (DlcUpdateService.isPromptSkippedForUpdate(result)) {
            return null
        }
        promptedThisSession = true
        return result
    }

    @JvmStatic
    fun isPrompted(): Boolean {
        return promptedThisSession
    }

    @JvmStatic
    fun markPrompted() {
        promptedThisSession = true
    }

    @JvmStatic
    fun buildLocalizedChangelog(result: DlcUpdateService.UpdateCheckResult?): String {
        if (result == null) {
            return ""
        }
        return DlcUpdateService.buildChangelogText(result.updates, DlcPlatformServices.languageCode())
    }

    @JvmStatic
    fun isUpdateManagerEnabled(): Boolean {
        return DlcConfig.isUpdateManagerOn()
    }

    @JvmStatic
    fun loadClientSnapshot(dlcRoot: Path?): ClientUpdateSnapshot {
        val version = DlcUpdateVersion.load(dlcRoot)
        val currentVersion = version?.getDlc() ?: ""
        val baseVersion = version?.getBase() ?: ""
        val rollbackFrom = version?.getRollbackFrom()
        val rolledBackMode = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, baseVersion, currentVersion)
        var baseHistory = DlcUpdateRowPlanner.visibleOnly(DlcUpdateService.listUpdateHistory())
        if (rolledBackMode) {
            val rolledHistory = DlcUpdateRowPlanner.visibleOnly(DlcUpdateService.listUpdateHistoryFrom(rollbackFrom))
            if (rolledHistory.isNotEmpty()) {
                baseHistory = rolledHistory
            }
        }
        val updateCheck = DlcUpdateService.checkForUpdates()
        return ClientUpdateSnapshot.create(
            baseVersion,
            currentVersion,
            rollbackFrom,
            rolledBackMode,
            updateCheck,
            baseHistory,
        )
    }
}
