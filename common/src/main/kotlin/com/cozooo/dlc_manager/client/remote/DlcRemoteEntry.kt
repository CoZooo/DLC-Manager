package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import java.nio.file.Path

class DlcRemoteEntry(
    private var configPath: Path?,
    private val dlcRoot: Path?,
    private val baseDir: Path?,
    private var fileNameHint: String?,
) {
    private var fileNameExplicit = false
    private var dlcIdentifier: String? = null
    private var directUrl: String? = null
    private var directUrls: List<String> = emptyList()
    private var downloadPriority = "curseforge"
    private var modrinthProjectId: String? = null
    private var modrinthVersionId: String? = null
    private var curseforgeProjectId: String? = null
    private var curseforgeFileId: String? = null
    private var type: DlcType? = null
    private var configType = false
    private var appliedTargetRaw: String? = null
    private var inList = false
    private var hidden = false
    private var allowEnableByAll = false
    private var dependencyNames: List<String> = emptyList()
    private var conflictNames: List<String> = emptyList()
    private var displayNames: Map<String, String> = emptyMap()
    private var tips: Map<String, String> = emptyMap()
    private var redirectUrl: String? = null
    private var alarm = false
    private var remoteDependencies: List<DlcRemoteEntry> = emptyList()

    fun getConfigPath(): Path? = configPath

    fun getDlcRoot(): Path? = dlcRoot

    fun setConfigPath(configPath: Path?) {
        this.configPath = configPath
    }

    fun getBaseDir(): Path? = baseDir

    fun getFileNameHint(): String? = fileNameHint

    fun setFileNameHint(fileNameHint: String?) {
        this.fileNameHint = fileNameHint
    }

    fun isFileNameExplicit(): Boolean = fileNameExplicit

    fun setFileNameExplicit(fileNameExplicit: Boolean) {
        this.fileNameExplicit = fileNameExplicit
    }

    fun getDlcIdentifier(): String? = dlcIdentifier

    fun setDlcIdentifier(dlcIdentifier: String?) {
        this.dlcIdentifier = dlcIdentifier
    }

    fun getDirectUrl(): String? {
        if (directUrls.isNotEmpty()) {
            return directUrls[0]
        }
        return directUrl
    }

    fun setDirectUrl(directUrl: String?) {
        this.directUrl = directUrl
        if (!directUrl.isNullOrBlank()) {
            directUrls = listOf(directUrl)
        }
    }

    fun getDirectUrls(): MutableList<String?> {
        if (directUrls.isNotEmpty()) {
            return ArrayList(directUrls)
        }
        if (!directUrl.isNullOrBlank()) {
            return arrayListOf(directUrl)
        }
        return ArrayList()
    }

    fun setDirectUrls(directUrls: List<String>?) {
        if (directUrls == null) {
            this.directUrls = emptyList()
            return
        }
        val cleaned = ArrayList<String>()
        for (url in directUrls) {
            if (!url.isNullOrBlank()) {
                cleaned.add(url.trim())
            }
        }
        this.directUrls = cleaned
        if (cleaned.isNotEmpty()) {
            directUrl = cleaned[0]
        }
    }

    fun getDownloadPriority(): String = downloadPriority

    fun setDownloadPriority(downloadPriority: String?) {
        this.downloadPriority = normalizePriority(downloadPriority)
    }

    fun isDirectUrlPriority(): Boolean = getDownloadPriorityList().firstOrNull() == "directurl"

    fun isCurseForgePriority(): Boolean = getDownloadPriorityList().firstOrNull() == "curseforge"

    fun getDownloadPriorityList(): List<String> = parsePriority(downloadPriority)

    fun getModrinthProjectId(): String? = modrinthProjectId

    fun setModrinthProjectId(modrinthProjectId: String?) {
        this.modrinthProjectId = modrinthProjectId
    }

    fun getModrinthVersionId(): String? = modrinthVersionId

    fun setModrinthVersionId(modrinthVersionId: String?) {
        this.modrinthVersionId = modrinthVersionId
    }

    fun getCurseforgeProjectId(): String? = curseforgeProjectId

    fun setCurseforgeProjectId(curseforgeProjectId: String?) {
        this.curseforgeProjectId = curseforgeProjectId
    }

    fun getCurseforgeFileId(): String? = curseforgeFileId

    fun setCurseforgeFileId(curseforgeFileId: String?) {
        this.curseforgeFileId = curseforgeFileId
    }

    fun getType(): DlcType? = type

    fun setType(type: DlcType?) {
        this.type = type
    }

    fun isConfigType(): Boolean = configType

    fun setConfigType(configType: Boolean) {
        this.configType = configType
    }

    fun getAppliedTargetRaw(): String? = appliedTargetRaw

    fun setAppliedTargetRaw(appliedTargetRaw: String?) {
        this.appliedTargetRaw = appliedTargetRaw
    }

    fun isInList(): Boolean = inList

    fun setInList(inList: Boolean) {
        this.inList = inList
    }

    fun isHidden(): Boolean = hidden

    fun setHidden(hidden: Boolean) {
        this.hidden = hidden
    }

    fun isAllowEnableByAll(): Boolean = allowEnableByAll

    fun setAllowEnableByAll(allowEnableByAll: Boolean) {
        this.allowEnableByAll = allowEnableByAll
    }

    fun getDependencyNames(): List<String> = dependencyNames

    fun setDependencyNames(dependencyNames: List<String>?) {
        this.dependencyNames = dependencyNames ?: emptyList()
    }

    fun getConflictNames(): List<String> = conflictNames

    fun setConflictNames(conflictNames: List<String>?) {
        this.conflictNames = conflictNames ?: emptyList()
    }

    fun getDisplayNames(): Map<String, String> = displayNames

    fun setDisplayNames(displayNames: Map<String, String>?) {
        this.displayNames = displayNames ?: emptyMap()
    }

    fun getTips(): Map<String, String> = tips

    fun setTips(tips: Map<String, String>?) {
        this.tips = tips ?: emptyMap()
    }

    fun getRedirectUrl(): String? = redirectUrl

    fun setRedirectUrl(redirectUrl: String?) {
        this.redirectUrl = redirectUrl
    }

    fun isAlarm(): Boolean = alarm

    fun setAlarm(alarm: Boolean) {
        this.alarm = alarm
    }

    fun getRemoteDependencies(): List<DlcRemoteEntry> = remoteDependencies

    fun setRemoteDependencies(remoteDependencies: List<DlcRemoteEntry>?) {
        this.remoteDependencies = remoteDependencies ?: emptyList()
    }

    fun resolveDisplayName(): String {
        if (displayNames.isEmpty()) {
            return fileNameHint ?: ""
        }
        val lang = DcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode())
        if (displayNames.containsKey(lang)) {
            return displayNames[lang]!!
        }
        if (displayNames.containsKey("en_us")) {
            return displayNames["en_us"]!!
        }
        return displayNames.values.firstOrNull() ?: (fileNameHint ?: "")
    }

    fun getTargetPath(): Path? {
        if (fileNameHint.isNullOrBlank() || baseDir == null) {
            return null
        }
        val safeName = sanitizeFileName(fileNameHint)
        if (safeName.isNullOrBlank()) {
            return null
        }
        return baseDir.resolve(safeName).normalize()
    }

    fun resolveTargetPath(fileName: String?): Path? {
        if (baseDir == null || fileName.isNullOrBlank()) {
            return null
        }
        val safeName = sanitizeFileName(fileName)
        if (safeName.isNullOrBlank()) {
            return null
        }
        return baseDir.resolve(safeName).normalize()
    }

    fun getUniqueKey(): String {
        if (!modrinthVersionId.isNullOrBlank()) {
            return "modrinth:$modrinthVersionId"
        }
        if (!curseforgeFileId.isNullOrBlank()) {
            val prefix = if (curseforgeProjectId.isNullOrBlank()) {
                "curseforge"
            } else {
                "curseforge:$curseforgeProjectId"
            }
            return "$prefix:$curseforgeFileId"
        }
        if (!directUrl.isNullOrBlank()) {
            return "url:$directUrl"
        }
        if (!fileNameHint.isNullOrBlank()) {
            return "file:$fileNameHint"
        }
        return "path:" + (configPath?.toString() ?: "unknown")
    }

    fun getResolvedDependencyNames(): List<String> {
        val names = LinkedHashSet<String>()
        for (name in dependencyNames) {
            if (!name.isNullOrBlank()) {
                names.add(name.trim())
            }
        }
        for (dep in remoteDependencies) {
            val identifier = dep.getDlcIdentifier()
            if (!identifier.isNullOrBlank()) {
                names.add(identifier.trim())
            }
        }
        return ArrayList(names)
    }

    companion object {
        private fun normalizePriority(raw: String?): String {
            val order = parsePriority(raw)
            return order.joinToString(",")
        }

        private fun parsePriority(raw: String?): List<String> {
            val order = ArrayList<String>()
            if (raw != null) {
                for (part in raw.split(",")) {
                    val token = normalizePriorityToken(part)
                    if (token.isNullOrBlank()) {
                        continue
                    }
                    if (!order.contains(token)) {
                        order.add(token)
                    }
                }
            }
            if (order.isEmpty()) {
                order.add("curseforge")
                order.add("modrinth")
                order.add("directurl")
                return order
            }
            for (fallback in listOf("curseforge", "modrinth", "directurl")) {
                if (!order.contains(fallback)) {
                    order.add(fallback)
                }
            }
            return order
        }

        private fun normalizePriorityToken(raw: String?): String? {
            if (raw == null) {
                return null
            }
            var token = raw.trim().lowercase()
            if (token.isBlank()) {
                return null
            }
            token = token.replace("_", "").replace("-", "")
            return when (token) {
                "curseforge" -> "curseforge"
                "modrinth" -> "modrinth"
                "directurl", "direct" -> "directurl"
                else -> null
            }
        }

        private fun sanitizeFileName(name: String?): String? {
            val cleaned = DlcDownloadUtil.stripQuery(name) ?: return null
            return cleaned.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
    }
}
