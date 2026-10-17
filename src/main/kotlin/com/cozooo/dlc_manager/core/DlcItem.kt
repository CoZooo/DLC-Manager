package com.cozooo.dlc_manager.core

import java.nio.file.Path

class DlcItem {
    private val idValue: String?
    private val identifierValue: String?
    private val nameValue: String?
    private val typeValue: DlcType?
    private val sourcePathValue: Path?
    private val sourcePathsValue: List<Path>
    private val appliedTargetRawValue: String?
    private var enabledValue: Boolean
    private val appliedTargetsValue: MutableList<Path> = ArrayList()
    private val tooltipsValue: Map<String, String>
    private val displayNamesValue: Map<String, String>
    private val dependenciesValue: List<String>
    private val conflictsValue: List<String>
    private val allowEnableByAllValue: Boolean
    private val hiddenValue: Boolean
    private val configTypeValue: Boolean
    private val alarmValue: Boolean
    private var alarmTipsValue: Map<String, String> = emptyMap()
    private var matchModeValue: String = "hash"
    private var dlcRootValue: Path? = null
    private var groupDirectoryValue: Path? = null
    private var redirectUrlValue: String? = null
    private var dirtyValue: Boolean = false

    constructor(
        id: String?,
        identifier: String?,
        name: String?,
        type: DlcType?,
        sourcePath: Path?,
        appliedTargetRaw: String?,
        enabled: Boolean,
        tooltips: Map<String, String>?,
        displayNames: Map<String, String>?,
        dependencies: List<String>?,
        conflicts: List<String>?,
        allowEnableByAll: Boolean,
        hidden: Boolean,
    ) : this(
        id,
        identifier,
        name,
        type,
        sourcePath,
        if (sourcePath == null) emptyList() else listOf(sourcePath),
        appliedTargetRaw,
        enabled,
        tooltips,
        displayNames,
        dependencies,
        conflicts,
        allowEnableByAll,
        hidden,
        false,
        false,
    )

    constructor(
        id: String?,
        identifier: String?,
        name: String?,
        type: DlcType?,
        sourcePath: Path?,
        appliedTargetRaw: String?,
        enabled: Boolean,
        tooltips: Map<String, String>?,
        displayNames: Map<String, String>?,
        dependencies: List<String>?,
        conflicts: List<String>?,
        allowEnableByAll: Boolean,
        hidden: Boolean,
        configType: Boolean,
    ) : this(
        id,
        identifier,
        name,
        type,
        sourcePath,
        if (sourcePath == null) emptyList() else listOf(sourcePath),
        appliedTargetRaw,
        enabled,
        tooltips,
        displayNames,
        dependencies,
        conflicts,
        allowEnableByAll,
        hidden,
        configType,
        false,
    )

    constructor(
        id: String?,
        identifier: String?,
        name: String?,
        type: DlcType?,
        sourcePath: Path?,
        appliedTargetRaw: String?,
        enabled: Boolean,
        tooltips: Map<String, String>?,
        displayNames: Map<String, String>?,
        dependencies: List<String>?,
        conflicts: List<String>?,
        allowEnableByAll: Boolean,
        hidden: Boolean,
        configType: Boolean,
        alarm: Boolean,
    ) : this(
        id,
        identifier,
        name,
        type,
        sourcePath,
        if (sourcePath == null) emptyList() else listOf(sourcePath),
        appliedTargetRaw,
        enabled,
        tooltips,
        displayNames,
        dependencies,
        conflicts,
        allowEnableByAll,
        hidden,
        configType,
        alarm,
    )

    constructor(
        id: String?,
        identifier: String?,
        name: String?,
        type: DlcType?,
        sourcePath: Path?,
        sourcePaths: List<Path>?,
        appliedTargetRaw: String?,
        enabled: Boolean,
        tooltips: Map<String, String>?,
        displayNames: Map<String, String>?,
        dependencies: List<String>?,
        conflicts: List<String>?,
        allowEnableByAll: Boolean,
        hidden: Boolean,
    ) : this(
        id,
        identifier,
        name,
        type,
        sourcePath,
        sourcePaths,
        appliedTargetRaw,
        enabled,
        tooltips,
        displayNames,
        dependencies,
        conflicts,
        allowEnableByAll,
        hidden,
        false,
        false,
    )

    constructor(
        id: String?,
        identifier: String?,
        name: String?,
        type: DlcType?,
        sourcePath: Path?,
        sourcePaths: List<Path>?,
        appliedTargetRaw: String?,
        enabled: Boolean,
        tooltips: Map<String, String>?,
        displayNames: Map<String, String>?,
        dependencies: List<String>?,
        conflicts: List<String>?,
        allowEnableByAll: Boolean,
        hidden: Boolean,
        configType: Boolean,
    ) : this(
        id,
        identifier,
        name,
        type,
        sourcePath,
        sourcePaths,
        appliedTargetRaw,
        enabled,
        tooltips,
        displayNames,
        dependencies,
        conflicts,
        allowEnableByAll,
        hidden,
        configType,
        false,
    )

    constructor(
        id: String?,
        identifier: String?,
        name: String?,
        type: DlcType?,
        sourcePath: Path?,
        sourcePaths: List<Path>?,
        appliedTargetRaw: String?,
        enabled: Boolean,
        tooltips: Map<String, String>?,
        displayNames: Map<String, String>?,
        dependencies: List<String>?,
        conflicts: List<String>?,
        allowEnableByAll: Boolean,
        hidden: Boolean,
        configType: Boolean,
        alarm: Boolean,
    ) {
        idValue = id
        identifierValue = identifier
        nameValue = name
        typeValue = type
        sourcePathValue = sourcePath
        sourcePathsValue = if (sourcePaths.isNullOrEmpty()) {
            if (sourcePath == null) emptyList() else listOf(sourcePath)
        } else {
            sourcePaths.toList()
        }
        appliedTargetRawValue = appliedTargetRaw
        enabledValue = enabled
        tooltipsValue = tooltips ?: emptyMap()
        displayNamesValue = displayNames ?: emptyMap()
        dependenciesValue = dependencies ?: emptyList()
        conflictsValue = conflicts ?: emptyList()
        allowEnableByAllValue = allowEnableByAll
        hiddenValue = hidden
        configTypeValue = configType
        alarmValue = alarm
    }

    fun getMatchMode(): String = matchModeValue
    fun setMatchMode(value: String?) { matchModeValue = DlcFileMatch.mode(value) }
    fun getDlcRoot(): Path = dlcRootValue ?: DlcPaths.dlcRoot()
    fun setDlcRoot(value: Path?) { dlcRootValue = value }
    fun getGroupDirectory(): Path? = groupDirectoryValue
    fun setGroupDirectory(value: Path?) { groupDirectoryValue = value }

    fun getId(): String? = idValue
    fun getIdentifier(): String? = identifierValue
    fun getName(): String? = nameValue
    fun getType(): DlcType? = typeValue
    fun getSourcePath(): Path? = sourcePathValue
    fun getSourcePaths(): List<Path> = sourcePathsValue
    fun getAppliedTargetRaw(): String? = appliedTargetRawValue
    fun isEnabled(): Boolean = enabledValue
    fun setEnabled(enabled: Boolean) {
        enabledValue = enabled
    }
    fun getAppliedTargets(): MutableList<Path> = appliedTargetsValue
    fun getTooltips(): Map<String, String> = tooltipsValue
    fun getDisplayNames(): Map<String, String> = displayNamesValue
    fun getDependencies(): List<String> = dependenciesValue
    fun getConflicts(): List<String> = conflictsValue
    fun AllowEnableByAll(): Boolean = allowEnableByAllValue
    fun isHidden(): Boolean = hiddenValue
    fun isConfigType(): Boolean = configTypeValue
    fun isAlarm(): Boolean = alarmValue
    fun getAlarmTips(): Map<String, String> = alarmTipsValue
    fun setAlarmTips(values: Map<String, String>?) { alarmTipsValue = values ?: emptyMap() }
    fun getRedirectUrl(): String? = redirectUrlValue
    fun setRedirectUrl(redirectUrl: String?) {
        redirectUrlValue = redirectUrl
    }
    fun isDirty(): Boolean = dirtyValue
    fun setDirty(dirty: Boolean) {
        dirtyValue = dirty
    }
}
