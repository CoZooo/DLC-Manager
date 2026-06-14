package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DcConfigIO
import java.nio.file.Path
import java.util.Collections

class DlcPresetEntry(
    private val id: String?,
    private val fileName: String?,
    private val sourcePath: Path?,
    displayName: Map<String, String>?,
    tip: Map<String, String>?,
    enable: List<String>?,
    conflicts: List<String>?,
    private val alarm: Boolean,
    status: DlcPresetStatus?,
) {
    private val displayName: Map<String, String> = displayName ?: emptyMap()
    private val tip: Map<String, String> = tip ?: emptyMap()
    private val enable: MutableList<String> = Collections.unmodifiableList(ArrayList(enable ?: emptyList()))
    private val conflicts: MutableList<String> = Collections.unmodifiableList(ArrayList(conflicts ?: emptyList()))
    private var status: DlcPresetStatus = status ?: DlcPresetStatus.OFF

    fun getId(): String? = id

    fun getFileName(): String? = fileName

    fun getSourcePath(): Path? = sourcePath

    fun getEnable(): MutableList<String> = enable

    fun getConflicts(): MutableList<String> = conflicts

    fun getStatus(): DlcPresetStatus = status

    fun isAlarm(): Boolean = alarm

    fun setStatus(status: DlcPresetStatus?) {
        this.status = status ?: DlcPresetStatus.OFF
    }

    fun resolveDisplayName(lang: String?): String? {
        val key = DcConfigIO.normalizeLangKey(lang)
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
        val key = DcConfigIO.normalizeLangKey(lang)
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
}
