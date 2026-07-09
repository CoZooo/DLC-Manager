package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus

class DlcServerPresetItem {
    @JvmField var id: String? = null
    @JvmField var fileName: String? = null
    @JvmField var displayNames: Map<String, String> = emptyMap()
    @JvmField var tips: Map<String, String> = emptyMap()
    @JvmField var enable: List<String> = emptyList()
    @JvmField var conflicts: List<String> = emptyList()
    @JvmField var alarm: Boolean = false
    @JvmField var status: DlcPresetStatus = DlcPresetStatus.OFF

    fun toPresetEntry(): DlcPresetEntry {
        return DlcPresetEntry(id, fileName, null, displayNames, tips, enable, conflicts, alarm, status)
    }

    companion object {
        @JvmStatic
        fun fromPreset(entry: DlcPresetEntry?): DlcServerPresetItem {
            val item = DlcServerPresetItem()
            if (entry == null) {
                return item
            }
            item.id = entry.getId()
            item.fileName = entry.getFileName()
            item.displayNames = entry.getDisplayNames()
            item.tips = entry.getTips()
            item.enable = entry.getEnable()
            item.conflicts = entry.getConflicts()
            item.alarm = entry.isAlarm()
            item.status = entry.getStatus()
            return item
        }
    }
}
