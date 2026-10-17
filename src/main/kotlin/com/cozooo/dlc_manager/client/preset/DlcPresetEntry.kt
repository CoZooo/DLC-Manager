package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import java.nio.file.Path
import java.util.Collections

class DlcPresetEntry(
    private val id: String?,
    private val fileName: String?,
    private val sourcePath: Path?,
    displayName: Map<String, String>?,
    tip: Map<String, String>?,
    alarmTip: Map<String, String>?,
    enable: List<String>?,
    conflicts: List<String>?,
    private val alarm: Boolean,
    status: DlcPresetStatus?,
) {
    constructor(
        id: String?, fileName: String?, sourcePath: Path?, displayName: Map<String, String>?, tip: Map<String, String>?,
        enable: List<String>?, conflicts: List<String>?, alarm: Boolean, status: DlcPresetStatus?
    ) : this(id, fileName, sourcePath, displayName, tip, emptyMap(), enable, conflicts, alarm, status)

    private val displayName: Map<String, String> = displayName ?: emptyMap()
    private val tip: Map<String, String> = tip ?: emptyMap()
    private val alarmTip: Map<String, String> = alarmTip ?: emptyMap()
    private val enable: MutableList<String> = Collections.unmodifiableList(ArrayList(enable ?: emptyList()))
    private val conflicts: MutableList<String> = Collections.unmodifiableList(ArrayList(conflicts ?: emptyList()))
    private var status: DlcPresetStatus = status ?: DlcPresetStatus.OFF

    fun getId(): String? = id

    fun getFileName(): String? = fileName

    fun getSourcePath(): Path? = sourcePath

    fun getDisplayNames(): Map<String, String> = displayName

    fun getTips(): Map<String, String> = tip
    fun getAlarmTips(): Map<String, String> = alarmTip

    fun getEnable(): MutableList<String> = enable

    fun getConflicts(): MutableList<String> = conflicts

    fun getStatus(): DlcPresetStatus = status

    fun isAlarm(): Boolean = alarm

    fun setStatus(status: DlcPresetStatus?) {
        this.status = status ?: DlcPresetStatus.OFF
    }

    fun resolveDisplayName(lang: String?): String? {
        val key = DlcDcConfigIO.normalizeLangKey(lang)
        if (key != null && displayName.containsKey(key)) {
            val value = displayName[key]
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        if (displayName.containsKey("en_us")) {
            val value = displayName["en_us"]
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return fileName
    }

    fun resolveTip(lang: String?): String? {
        val key = DlcDcConfigIO.normalizeLangKey(lang)
        if (key != null && tip.containsKey(key)) {
            val value = tip[key]
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        if (tip.containsKey("en_us")) {
            val value = tip["en_us"]
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        for (value in tip.values) {
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    fun resolveAlarmTip(lang: String?): String? {
        val key = DlcDcConfigIO.normalizeLangKey(lang)
        if (key != null && alarmTip[key].orEmpty().isNotBlank()) return alarmTip[key]
        if (key != null && tip[key].orEmpty().isNotBlank()) return tip[key]
        if (alarmTip["en_us"].orEmpty().isNotBlank()) return alarmTip["en_us"]
        return resolveTip(lang)
    }
}
