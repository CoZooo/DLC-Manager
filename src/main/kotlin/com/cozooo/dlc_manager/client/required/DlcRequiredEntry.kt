package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.nio.file.Path
import java.util.Collections
import java.util.Locale

class DlcRequiredEntry(
    private val configPath: Path?,
    private val requiredDir: Path?,
) {
    private val baseName: String
    private var identifier: String
    private val displayNames: MutableMap<String, String> = HashMap()
    private val tips: MutableMap<String, String> = HashMap()
    private var requiredFileName: String? = null
    private var directUrl: String? = null
    private var directUrls: MutableList<String> = ArrayList()
    private var downloadPriority: String = "curseforge"
    private var modrinthProjectId: String? = null
    private var modrinthVersionId: String? = null
    private var curseforgeProjectId: String? = null
    private var curseforgeFileId: String? = null
    private var hidden = false
    private var appliedTargetRaw: String? = null
    private var redirectUrl: String? = null
    private var resolvedFileName: String? = null
    private val dependencies: MutableList<DlcRequiredDependency> = ArrayList()

    init {
        var name = configPath?.fileName?.toString() ?: ""
        name = stripConfigExtension(name)
        baseName = name
        identifier = name
    }

    fun getConfigPath(): Path? = configPath

    fun getRequiredDir(): Path? = requiredDir

    fun getBaseName(): String = baseName

    fun getIdentifier(): String = identifier

    fun setIdentifier(identifier: String?) {
        if (!identifier.isNullOrBlank()) {
            this.identifier = identifier
        }
    }

    fun getDisplayNames(): Map<String, String> = Collections.unmodifiableMap(displayNames)

    fun putDisplayName(lang: String?, value: String?) {
        if (lang.isNullOrBlank() || value == null) {
            return
        }
        displayNames[lang] = value
    }

    fun getTips(): Map<String, String> = Collections.unmodifiableMap(tips)

    fun putTip(lang: String?, value: String?) {
        if (lang.isNullOrBlank() || value == null) {
            return
        }
        tips[lang] = value
    }

    fun getDirectUrl(): String? {
        if (directUrls.isNotEmpty()) {
            return directUrls[0]
        }
        return directUrl
    }

    fun getRequiredFileName(): String? = requiredFileName

    fun setRequiredFileName(requiredFileName: String?) {
        if (!requiredFileName.isNullOrBlank()) {
            this.requiredFileName = requiredFileName
            if (resolvedFileName.isNullOrBlank()) {
                resolvedFileName = requiredFileName
            }
        }
    }

    fun setDirectUrl(directUrl: String?) {
        this.directUrl = directUrl
        if (!directUrl.isNullOrBlank()) {
            directUrls = ArrayList()
            directUrls.add(directUrl)
        }
    }

    fun getDirectUrls(): List<String> {
        if (directUrls.isNotEmpty()) {
            return ArrayList(directUrls)
        }
        if (!directUrl.isNullOrBlank()) {
            return listOf(directUrl!!)
        }
        return emptyList()
    }

    fun setDirectUrls(directUrls: List<String>?) {
        this.directUrls = ArrayList()
        if (directUrls == null) {
            return
        }
        for (url in directUrls) {
            if (!url.isNullOrBlank()) {
                this.directUrls.add(url.trim())
            }
        }
        if (this.directUrls.isNotEmpty()) {
            directUrl = this.directUrls[0]
        }
    }

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

    fun getDownloadPriority(): String = downloadPriority

    fun setDownloadPriority(downloadPriority: String?) {
        this.downloadPriority = normalizePriority(downloadPriority)
    }

    fun isDirectUrlPriority(): Boolean {
        return getDownloadPriorityList().firstOrNull() == "directurl"
    }

    fun isCurseForgePriority(): Boolean {
        return getDownloadPriorityList().firstOrNull() == "curseforge"
    }

    fun getDownloadPriorityList(): List<String> {
        return parsePriority(downloadPriority)
    }

    fun isHidden(): Boolean = hidden

    fun setHidden(hidden: Boolean) {
        this.hidden = hidden
    }

    fun getDependencies(): MutableList<DlcRequiredDependency> = dependencies

    fun addDependency(dependency: DlcRequiredDependency?) {
        if (dependency != null) {
            dependencies.add(dependency)
        }
    }

    fun setAppliedTargetRaw(appliedTargetRaw: String?) {
        this.appliedTargetRaw = appliedTargetRaw
    }

    fun getAppliedTargetRaw(): String? = appliedTargetRaw

    fun setRedirectUrl(redirectUrl: String?) {
        this.redirectUrl = redirectUrl
    }

    fun getRedirectUrl(): String? = redirectUrl

    fun setResolvedFileName(resolvedFileName: String?) {
        this.resolvedFileName = resolvedFileName
    }

    fun getResolvedFileName(): String? = resolvedFileName

    fun resolveRequiredPath(fileName: String?): Path? {
        if (requiredDir == null || fileName.isNullOrBlank()) {
            return null
        }
        return requiredDir.resolve(fileName)
    }

    fun resolveAppliedTargets(): List<Path> {
        val targets = ArrayList<Path>()
        if (appliedTargetRaw.isNullOrBlank()) {
            return targets
        }
        val parts = appliedTargetRaw!!.split(",")
        for (part in parts) {
            val value = part.trim()
            if (value.isEmpty()) {
                continue
            }
            var path = Path.of(value)
            if (!path.isAbsolute) {
                path = DlcPaths.gameDir().resolve(path)
            }
            targets.add(path.normalize())
        }
        return targets
    }

    fun resolveDisplayName(): String {
        var fallback = baseName
        if (fallback.isBlank()) {
            fallback = identifier
        }
        if (fallback.isBlank()) {
            fallback = "required"
        }
        if (displayNames.isEmpty()) {
            return fallback
        }
        val lang = DlcDcConfigIO.normalizeLangKey(DlcPlatformServices.languageCode())
        if (displayNames.containsKey(lang)) {
            return displayNames[lang] ?: fallback
        }
        if (displayNames.containsKey("en_us")) {
            return displayNames["en_us"] ?: fallback
        }
        return displayNames.values.firstOrNull() ?: fallback
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
            var token = raw.trim().lowercase(Locale.ROOT)
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

        private fun stripConfigExtension(name: String?): String {
            if (name == null) {
                return ""
            }
            return if (name.lowercase(Locale.ROOT).endsWith(".dc")) {
                name.substring(0, name.length - 3)
            } else {
                name
            }
        }
    }
}
