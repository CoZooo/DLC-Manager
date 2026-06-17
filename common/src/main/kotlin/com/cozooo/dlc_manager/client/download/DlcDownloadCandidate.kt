package com.cozooo.dlc_manager.client.download

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException

class DlcDownloadCandidate private constructor(
    @JvmField val url: String,
    @JvmField val direct: Boolean,
    @JvmField val fileNameOverride: String?,
    @JvmField val hashType: String?,
    @JvmField val hashValue: String?,
    @JvmField val expectedSize: Long,
) {
    class Resolver private constructor(
        order: List<String>?,
        private val curseProjectId: String?,
        private val curseFileId: String?,
        private val modrinthProjectId: String?,
        private val modrinthVersionId: String?,
        directUrls: List<String?>?,
    ) {
        private val order: List<String> = resolveOrder(order)
        private val directUrls: List<String?> = directUrls ?: emptyList()
        private var index = 0
        private var fallbackDirectTried = false
        private var lastError: IOException? = null

        @Throws(IOException::class, InterruptedException::class)
        fun next(): MutableList<DlcDownloadCandidate> {
            while (index < order.size) {
                val source = order[index++]
                val candidates = ArrayList<DlcDownloadCandidate>()
                try {
                    when (source) {
                        "directurl" -> addDirectCandidates(candidates, directUrls)
                        "curseforge" -> addCurseForgeCandidate(candidates, curseProjectId, curseFileId)
                        "modrinth" -> addModrinthCandidate(candidates, modrinthProjectId, modrinthVersionId)
                    }
                } catch (ex: IOException) {
                    lastError = ex
                    continue
                }
                if (candidates.isNotEmpty()) {
                    return candidates
                }
            }
            if (!fallbackDirectTried && !order.contains("directurl")) {
                fallbackDirectTried = true
                val candidates = ArrayList<DlcDownloadCandidate>()
                addDirectCandidates(candidates, directUrls)
                if (candidates.isNotEmpty()) {
                    return candidates
                }
            }
            return ArrayList()
        }

        fun lastError(): IOException? = lastError

        companion object {
            @JvmStatic
            fun empty(): Resolver = Resolver(emptyList(), null, null, null, null, emptyList())

            @JvmStatic
            fun create(
                order: List<String>?,
                curseProjectId: String?,
                curseFileId: String?,
                modrinthProjectId: String?,
                modrinthVersionId: String?,
                directUrls: List<String?>?,
            ): Resolver = Resolver(order, curseProjectId, curseFileId, modrinthProjectId, modrinthVersionId, directUrls)
        }
    }

    companion object {
        @JvmStatic
        fun direct(url: String): DlcDownloadCandidate = DlcDownloadCandidate(url, true, null, null, null, -1L)

        @JvmStatic
        fun curseForge(file: CurseForgeClient.CurseForgeFile): DlcDownloadCandidate {
            return curseForgeCandidate(file.url, file)
        }

        @JvmStatic
        fun curseForgeCandidates(file: CurseForgeClient.CurseForgeFile): List<DlcDownloadCandidate> {
            val candidates = ArrayList<DlcDownloadCandidate>()
            val primaryUrl = file.url
            val primaryIsWebsiteFallback = isCurseForgeWebsiteDownloadUrl(primaryUrl)
            if (!primaryIsWebsiteFallback && !primaryUrl.isNullOrBlank()) {
                candidates.add(curseForgeCandidate(primaryUrl, file))
            }
            for (url in file.alternateUrls) {
                if (!url.isNullOrBlank() && candidates.none { it.url == url }) {
                    candidates.add(curseForgeCandidate(url, file))
                }
            }
            if (primaryIsWebsiteFallback && !primaryUrl.isNullOrBlank() && candidates.none { it.url == primaryUrl }) {
                candidates.add(curseForgeCandidate(primaryUrl, file))
            }
            return candidates
        }

        private fun curseForgeCandidate(url: String?, file: CurseForgeClient.CurseForgeFile): DlcDownloadCandidate {
            var hashType: String? = null
            var hashValue: String? = null
            if (!file.sha1.isNullOrBlank()) {
                hashType = "sha1"
                hashValue = file.sha1
            } else if (!file.md5.isNullOrBlank()) {
                hashType = "md5"
                hashValue = file.md5
            }
            return DlcDownloadCandidate(url ?: "", false, file.fileName, hashType, hashValue, file.size)
        }

        private fun isCurseForgeWebsiteDownloadUrl(url: String?): Boolean {
            return !url.isNullOrBlank()
                && url.contains("curseforge.com/api/v1/mods/")
                && url.endsWith("/download")
        }

        @JvmStatic
        fun modrinth(version: ModrinthClient.ModrinthVersion): DlcDownloadCandidate {
            var hashType: String? = null
            var hashValue: String? = null
            if (!version.sha1.isNullOrBlank()) {
                hashType = "sha1"
                hashValue = version.sha1
            } else if (!version.sha512.isNullOrBlank()) {
                hashType = "sha512"
                hashValue = version.sha512
            }
            return DlcDownloadCandidate(version.url ?: "", false, version.fileName, hashType, hashValue, version.size)
        }

        @JvmStatic
        @Throws(IOException::class, InterruptedException::class)
        fun resolve(entry: DlcRemoteEntry?): List<DlcDownloadCandidate> = collect(resolver(entry))

        @JvmStatic
        fun resolver(entry: DlcRemoteEntry?): Resolver {
            if (entry == null) {
                return Resolver.empty()
            }
            return Resolver.create(
                entry.getDownloadPriorityList(),
                entry.getCurseforgeProjectId(),
                entry.getCurseforgeFileId(),
                entry.getModrinthProjectId(),
                entry.getModrinthVersionId(),
                entry.getDirectUrls(),
            )
        }

        @JvmStatic
        @Throws(IOException::class, InterruptedException::class)
        fun resolve(entry: DlcRequiredEntry?): List<DlcDownloadCandidate> = collect(resolver(entry))

        @JvmStatic
        fun resolver(entry: DlcRequiredEntry?): Resolver {
            if (entry == null) {
                return Resolver.empty()
            }
            return Resolver.create(
                entry.getDownloadPriorityList(),
                entry.getCurseforgeProjectId(),
                entry.getCurseforgeFileId(),
                entry.getModrinthProjectId(),
                entry.getModrinthVersionId(),
                entry.getDirectUrls(),
            )
        }

        @JvmStatic
        @Throws(IOException::class, InterruptedException::class)
        fun resolve(
            order: List<String>?,
            curseProjectId: String?,
            curseFileId: String?,
            modrinthProjectId: String?,
            modrinthVersionId: String?,
            directUrls: List<String?>?,
        ): List<DlcDownloadCandidate> {
            return collect(Resolver.create(order, curseProjectId, curseFileId, modrinthProjectId, modrinthVersionId, directUrls))
        }

        @Throws(IOException::class, InterruptedException::class)
        private fun collect(resolver: Resolver): List<DlcDownloadCandidate> {
            val candidates = ArrayList<DlcDownloadCandidate>()
            while (true) {
                val batch = resolver.next()
                if (batch.isEmpty()) {
                    break
                }
                candidates.addAll(batch)
            }
            return candidates
        }

        @JvmStatic
        fun readPriority(obj: JsonObject?): String {
            val element = DcConfigIO.getIgnoreCase(obj, "priority")
            return parsePriority(element)
        }

        @JvmStatic
        fun parsePriority(element: JsonElement?): String {
            if (element == null || element.isJsonNull) {
                return ""
            }
            if (element.isJsonPrimitive) {
                return try {
                    element.asString
                } catch (_: Exception) {
                    ""
                }
            }
            if (!element.isJsonArray) {
                return ""
            }
            return parsePriorityArray(element.asJsonArray)
        }

        private fun parsePriorityArray(array: JsonArray): String {
            val values = ArrayList<String>()
            for (item in array) {
                if (item == null || !item.isJsonPrimitive) {
                    continue
                }
                val value = try {
                    item.asString
                } catch (_: Exception) {
                    continue
                }
                if (!value.isNullOrBlank()) {
                    values.add(value.trim())
                }
            }
            return values.joinToString(",")
        }

        private fun resolveOrder(order: List<String>?): List<String> {
            val resolved = ArrayList<String>()
            if (order != null) {
                for (key in order) {
                    if (!key.isNullOrBlank() && !resolved.contains(key)) {
                        resolved.add(key)
                    }
                }
            }
            if (resolved.isEmpty()) {
                resolved.add("curseforge")
                resolved.add("modrinth")
                resolved.add("directurl")
            }
            return resolved
        }

        private fun addDirectCandidates(candidates: MutableList<DlcDownloadCandidate>, directUrls: List<String?>?) {
            if (directUrls == null) {
                return
            }
            for (url in directUrls) {
                if (!url.isNullOrBlank()) {
                    candidates.add(direct(url))
                }
            }
        }

        @Throws(IOException::class, InterruptedException::class)
        private fun addCurseForgeCandidate(candidates: MutableList<DlcDownloadCandidate>, projectId: String?, fileId: String?) {
            if (fileId.isNullOrBlank()) {
                return
            }
            val file = CurseForgeClient.fetchFileByIdOrVersion(
                projectId,
                fileId,
                CrossPlatformRuntime.mcVersion(),
                CrossPlatformRuntime.loaderName(),
            )
            if (file != null && !file.url.isNullOrBlank()) {
                candidates.addAll(curseForgeCandidates(file))
            }
        }

        @Throws(IOException::class, InterruptedException::class)
        private fun addModrinthCandidate(candidates: MutableList<DlcDownloadCandidate>, projectId: String?, versionId: String?) {
            if (versionId.isNullOrBlank()) {
                return
            }
            val version = ModrinthClient.fetchVersionByIdOrVersion(
                projectId,
                versionId,
                CrossPlatformRuntime.mcVersion(),
                CrossPlatformRuntime.loaderName(),
            )
            if (version != null && !version.url.isNullOrBlank()) {
                candidates.add(modrinth(version))
            }
        }
    }
}
