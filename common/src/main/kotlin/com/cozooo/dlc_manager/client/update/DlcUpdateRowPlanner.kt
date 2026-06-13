package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo

object DlcUpdateRowPlanner {
    enum class Action {
        NONE,
        ROLLBACK,
        REUPDATE,
        UPDATE,
        MANAGER_ROLLBACK
    }

    enum class Type {
        UPDATE,
        MANAGER
    }

    class UpdateRow(
        @JvmField val meta: DlcUpdateMeta?,
        @JvmField val action: Action?,
        @JvmField val type: Type?,
        @JvmField val oldVersion: String?,
        @JvmField val newVersion: String?,
        @JvmField val rollbackTarget: String?,
        @JvmField val displayOldVersion: String?,
        @JvmField val displayNewVersion: String?,
    )

    class PlanResult(
        @JvmField val rows: List<UpdateRow>,
        @JvmField val hasVisibleUpdates: Boolean,
    )

    @JvmStatic
    fun planClientRows(
        history: List<DlcUpdateMeta>?,
        selfHistory: DlcSelfUpdateService.SelfUpdateHistory?,
        updateCheck: DlcUpdateService.UpdateCheckResult?,
        rolledBackMode: Boolean,
        currentBaseVersion: String?,
        currentVersion: String?,
    ): PlanResult {
        val rows = ArrayList<UpdateRow>()
        var hasVisibleUpdates = false

        val selfOldVersion = selfHistory?.oldVersion
        val selfNewVersion = selfHistory?.newVersion
        if (!selfOldVersion.isNullOrBlank() && !selfNewVersion.isNullOrBlank()) {
            val managerMeta = DlcUpdateMeta()
            managerMeta.setDlcOld(selfOldVersion)
            managerMeta.setDlcNew(selfNewVersion)
            rows.add(
                UpdateRow(
                    managerMeta,
                    Action.MANAGER_ROLLBACK,
                    Type.MANAGER,
                    selfHistory.oldVersion,
                    selfHistory.newVersion,
                    selfHistory.oldVersion,
                    selfHistory.oldVersion,
                    selfHistory.newVersion,
                )
            )
        }

        val currentBase = normalizeVersion(currentBaseVersion)
        val currentDlc = normalizeVersion(currentVersion)
        for (meta in visibleOnly(history)) {
            val oldVersion = meta.getDlcOld()
            val newVersion = meta.getDlcNew()
            val oldBase = meta.getBaseOld()
            val newBase = meta.getBaseNew()
            val targetBase = if (newBase == null || newBase.isBlank()) oldBase else newBase
            val targetDlc = if (newVersion == null || newVersion.isBlank()) oldVersion else newVersion
            val display = compactTransition(oldBase, oldVersion, targetBase, targetDlc)
            val rollbackTarget = composePair(oldBase, oldVersion)
            val action = if (rolledBackMode && compareState(targetBase, targetDlc, currentBase, currentDlc) > 0) {
                Action.REUPDATE
            } else {
                Action.ROLLBACK
            }
            rows.add(UpdateRow(meta, action, Type.UPDATE, oldVersion, newVersion, rollbackTarget, display[0], display[1]))
        }

        if (!rolledBackMode && updateCheck != null && updateCheck.hasUpdates()) {
            for (pkg in updateCheck.updates ?: emptyList()) {
                val manifest = pkg?.getManifest() ?: continue
                val version = manifest.getVersion() ?: continue
                if (manifest.isHidden()) {
                    continue
                }
                val meta = DlcUpdateMeta()
                meta.setDlcOld(version.getDlcOld())
                meta.setDlcNew(version.getDlcNew())
                meta.setBaseOld(version.getBase())
                val targetBase = version.getBaseNew()
                if (targetBase != null && targetBase.isNotBlank()) {
                    meta.setBaseNew(targetBase)
                }
                meta.setChangelog(manifest.getChangelog())
                val metaBaseNew = meta.getBaseNew()
                val metaDlcNew = meta.getDlcNew()
                val updateBase = if (metaBaseNew == null || metaBaseNew.isBlank()) meta.getBaseOld() else metaBaseNew
                val updateDlc = if (metaDlcNew == null || metaDlcNew.isBlank()) meta.getDlcOld() else metaDlcNew
                val display = compactTransition(meta.getBaseOld(), meta.getDlcOld(), updateBase, updateDlc)
                val rollbackTarget = composePair(meta.getBaseOld(), meta.getDlcOld())
                rows.add(UpdateRow(meta, Action.UPDATE, Type.UPDATE, meta.getDlcOld(), meta.getDlcNew(), rollbackTarget, display[0], display[1]))
                hasVisibleUpdates = true
            }
        }

        return PlanResult(rows, hasVisibleUpdates)
    }

    @JvmStatic
    fun planServerRows(
        history: List<DlcUpdateMeta>?,
        updates: List<DlcServerUpdateInfo?>?,
        rolledBackMode: Boolean,
        currentBaseVersion: String?,
        currentVersion: String?,
    ): PlanResult {
        val rows = ArrayList<UpdateRow>()
        var hasVisibleUpdates = false

        val currentBase = normalizeVersion(currentBaseVersion)
        val currentDlc = normalizeVersion(currentVersion)
        for (meta in visibleOnly(history)) {
            val oldVersion = meta.getDlcOld()
            val newVersion = meta.getDlcNew()
            val oldBase = meta.getBaseOld()
            val newBase = meta.getBaseNew()
            val targetBase = if (newBase == null || newBase.isBlank()) oldBase else newBase
            val targetDlc = if (newVersion == null || newVersion.isBlank()) oldVersion else newVersion
            val display = compactTransition(oldBase, oldVersion, targetBase, targetDlc)
            val rollbackTarget = composePair(oldBase, oldVersion)
            val action = if (rolledBackMode && compareState(targetBase, targetDlc, currentBase, currentDlc) > 0) {
                Action.REUPDATE
            } else {
                Action.ROLLBACK
            }
            rows.add(UpdateRow(meta, action, Type.UPDATE, oldVersion, newVersion, rollbackTarget, display[0], display[1]))
        }

        if (!rolledBackMode && updates != null) {
            for (info in updates) {
                if (info == null || info.hidden) {
                    continue
                }
                val meta = DlcUpdateMeta()
                meta.setDlcOld(info.dlcOld)
                meta.setDlcNew(info.dlcNew)
                meta.setBaseOld(info.baseOld)
                val infoBaseNew = info.baseNew
                val infoDlcNew = info.dlcNew
                if (infoBaseNew != null && infoBaseNew.isNotBlank()) {
                    meta.setBaseNew(infoBaseNew)
                }
                meta.setChangelog(info.changelog)
                val updateBase = if (infoBaseNew == null || infoBaseNew.isBlank()) info.baseOld else infoBaseNew
                val updateDlc = if (infoDlcNew == null || infoDlcNew.isBlank()) info.dlcOld else infoDlcNew
                val display = compactTransition(info.baseOld, info.dlcOld, updateBase, updateDlc)
                val rollbackTarget = composePair(info.baseOld, info.dlcOld)
                rows.add(UpdateRow(meta, Action.UPDATE, Type.UPDATE, info.dlcOld, info.dlcNew, rollbackTarget, display[0], display[1]))
                hasVisibleUpdates = true
            }
        }

        return PlanResult(rows, hasVisibleUpdates)
    }

    @JvmStatic
    fun visibleOnly(input: List<DlcUpdateMeta?>?): List<DlcUpdateMeta> {
        if (input == null || input.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<DlcUpdateMeta>()
        for (meta in input) {
            if (meta != null && !meta.isHidden()) {
                out.add(meta)
            }
        }
        return out
    }

    private fun compareState(leftBase: String?, leftDlc: String?, rightBase: String?, rightDlc: String?): Int {
        val baseCompare = DlcUpdateService.compareVersions(normalizeVersion(leftBase), normalizeVersion(rightBase))
        if (baseCompare != 0) {
            return baseCompare
        }
        return DlcUpdateService.compareVersions(normalizeVersion(leftDlc), normalizeVersion(rightDlc))
    }

    private fun composePair(base: String?, dlc: String?): String {
        return normalizeVersion(base) + "+" + normalizeVersion(dlc)
    }

    private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
        val sourceBase = normalizeVersion(oldBase)
        val sourceDlc = normalizeVersion(oldDlc)
        val targetBase = normalizeVersion(newBase)
        val targetDlc = normalizeVersion(newDlc)
        val baseChanged = sourceBase != targetBase
        val dlcChanged = sourceDlc != targetDlc
        if (baseChanged && dlcChanged) {
            return arrayOf(composePair(sourceBase, sourceDlc), composePair(targetBase, targetDlc))
        }
        if (baseChanged) {
            return arrayOf(sourceBase, targetBase)
        }
        if (dlcChanged) {
            return arrayOf(sourceDlc, targetDlc)
        }
        return arrayOf(composePair(sourceBase, sourceDlc), composePair(targetBase, targetDlc))
    }

    private fun normalizeVersion(value: String?): String {
        return if (value == null || value.isBlank()) "1.0.0" else value.trim()
    }
}
