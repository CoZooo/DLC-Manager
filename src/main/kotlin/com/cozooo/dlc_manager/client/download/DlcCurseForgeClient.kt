package com.cozooo.dlc_manager.client.download

import com.cozooo.dlc_manager.core.DlcConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object DlcCurseForgeClient {
    private val GSON = Gson()
    private const val API_KEY_SHIFT = 7
    private const val API_KEY_MASK: Byte = 0x5A
    private val API_KEY_PART1 = byteArrayOf(
        0x49.toByte(), 0x04.toByte(), 0xBC.toByte(), 0x56.toByte(), 0x16.toByte(), 0x94.toByte(), 0x84.toByte(), 0xB9.toByte(),
        0x31.toByte(), 0xB9.toByte(), 0x9B.toByte(), 0x63.toByte(), 0x5F.toByte(), 0x5A.toByte(), 0x12.toByte(), 0xFF.toByte(),
        0xFA.toByte(), 0x7F.toByte(), 0xE3.toByte(), 0xD6.toByte(), 0xF6.toByte(), 0x08.toByte(), 0xC4.toByte(), 0x30.toByte(),
        0x42.toByte(), 0xA8.toByte(), 0x83.toByte(), 0xE3.toByte(), 0x9A.toByte(), 0x6C.toByte(), 0x42.toByte(), 0x53.toByte(),
        0x0A.toByte(), 0xFE.toByte(), 0xDE.toByte(), 0x1F.toByte(), 0xC5.toByte(), 0x9C.toByte(), 0x7A.toByte(), 0x8A.toByte(),
        0xC6.toByte(), 0x66.toByte(), 0x28.toByte(), 0x30.toByte(), 0x3B.toByte(), 0xA3.toByte(), 0xD4.toByte(), 0x7B.toByte(),
        0x43.toByte(), 0x7C.toByte(), 0x19.toByte(), 0x57.toByte(), 0x03.toByte(), 0xC2.toByte(), 0x26.toByte(), 0x3F.toByte(),
        0x54.toByte(), 0xFF.toByte(), 0x5C.toByte(), 0xF2.toByte(),
    )
    private const val API_KEY_PART2_SEED = 0xA5
    private var API_KEY_CACHE: String? = null
    private val CLIENT: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(DlcConfig.getConnectTimeoutMs().toLong()))
        .build()
    private val CACHE: MutableMap<String, CurseForgeFile> = ConcurrentHashMap()
    private val FILES_CACHE: MutableMap<String, List<CurseForgeFile>> = ConcurrentHashMap()
    private const val FILE_PAGE_SIZE = 50
    private const val FILE_MAX_PAGES = 5
    private val MOD_CACHE: MutableMap<String, CurseForgeMod> = ConcurrentHashMap()
    private val PROJECT_ID_CACHE: MutableMap<String, String> = ConcurrentHashMap()
    private const val MINECRAFT_GAME_ID = 432
    private val CDN_HOSTS = arrayOf("https://mediafilez.forgecdn.net")
    private const val API_MAX_ATTEMPTS = 2

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun fetchFile(projectId: String?, fileId: String?): CurseForgeFile? {
        if (fileId.isNullOrBlank()) {
            return null
        }
        val apiKey = getApiKey()
        val cacheKey = (projectId ?: "") + ":" + fileId
        CACHE[cacheKey]?.let { return it }
        val data = fetchFileDataWithRetry(projectId, fileId, apiKey) ?: return null
        val file = CurseForgeFile()
        file.id = fileId
        applyFileData(file, data)
        var fallbackUrl: String? = null
        var useCdnFallback = false
        if (data.has("downloadUrl")) {
            val downloadUrl = getString(data, "downloadUrl")
            if (downloadUrl == null) {
                useCdnFallback = true
                fallbackUrl = fallbackDownloadUrl(projectId, fileId)
                file.url = fallbackUrl
            } else {
                file.url = downloadUrl
            }
        } else if (file.url.isNullOrBlank()) {
            val downloadUrl = fetchDownloadUrlWithRetry(projectId, fileId, apiKey)
            useCdnFallback = downloadUrl.useCdnFallback
            fallbackUrl = if (downloadUrl.url == null) fallbackDownloadUrl(projectId, fileId) else null
            file.url = downloadUrl.url ?: fallbackUrl
        }
        resolveMissingFileName(file, fallbackUrl)
        resolveAlternateDownloadUrls(file, fallbackUrl, useCdnFallback)
        CACHE[cacheKey] = file
        return file
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun fetchMod(modId: String?): CurseForgeMod? {
        val resolvedModId = resolveProjectId(modId)
        if (resolvedModId.isNullOrBlank()) {
            return null
        }
        MOD_CACHE[resolvedModId]?.let { return it }
        val apiKey = getApiKey()
        val endpoint = "https://api.curseforge.com/v1/mods/$resolvedModId"
        val root = fetchJsonObjectWithRetry(endpoint, apiKey) ?: return null
        val mod = CurseForgeMod()
        applyModData(mod, root.getAsJsonObject("data"))
        MOD_CACHE[resolvedModId] = mod
        return mod
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun fetchFileByIdOrVersion(projectIdOrSlug: String?, fileIdOrVersion: String?, mcVersion: String?, loader: String?): CurseForgeFile? {
        if (fileIdOrVersion.isNullOrBlank()) {
            return null
        }
        val trimmed = fileIdOrVersion.trim()
        if (isNumeric(trimmed)) {
            return fetchFile(resolveProjectId(projectIdOrSlug), trimmed)
        }
        val resolvedProjectId = resolveProjectId(projectIdOrSlug)
        if (resolvedProjectId.isNullOrBlank()) {
            return null
        }
        val cacheKey = resolvedProjectId +
            ":" + (mcVersion ?: "") +
            ":" + (loader ?: "") +
            ":version:" + trimmed
        CACHE[cacheKey]?.let { return it }
        val matched = fetchFileByVersion(resolvedProjectId, trimmed, mcVersion, loader)
        if (matched != null) {
            CACHE[cacheKey] = matched
        }
        return matched
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun fetchFiles(projectId: String?, mcVersion: String?, loader: String?): List<CurseForgeFile> {
        val resolvedProjectId = resolveProjectId(projectId)
        if (resolvedProjectId.isNullOrBlank()) {
            return emptyList()
        }
        val cacheKey = "files:" + resolvedProjectId + ":" + (mcVersion ?: "") + ":" + (loader ?: "")
        FILES_CACHE[cacheKey]?.let { return it }
        val apiKey = getApiKey()
        var endpoint = "https://api.curseforge.com/v1/mods/$resolvedProjectId/files?pageSize=50"
        if (!mcVersion.isNullOrBlank()) {
            endpoint += "&gameVersion=" + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8)
        }
        val files = ArrayList<CurseForgeFile>()
        val targetLoader = loader?.trim() ?: ""
        var complete = false
        for (page in 0 until FILE_MAX_PAGES) {
            val root = fetchJsonArrayWithRetry(endpoint + "&index=" + (page * FILE_PAGE_SIZE), apiKey)
                ?: break
            val data = root.getAsJsonArray("data")
            if (data == null || data.size() == 0) {
                complete = true
                break
            }
            for (element in data) {
                if (!element.isJsonObject) {
                    continue
                }
                val fileJson = element.asJsonObject
                if (!matchesGameVersion(fileJson, mcVersion, targetLoader)) {
                    continue
                }
                val file = CurseForgeFile()
                file.id = getString(fileJson, "id")
                applyFileData(file, fileJson)
                file.downloadUrlExplicitlyNull = isExplicitNullDownloadUrl(fileJson)
                files.add(file)
            }
            if (data.size() < FILE_PAGE_SIZE) {
                complete = true
                break
            }
        }
        val snapshot = java.util.List.copyOf(files)
        if (complete) {
            FILES_CACHE[cacheKey] = snapshot
        }
        return snapshot
    }

    @JvmStatic
    fun resolveDownloadUrl(projectId: String?, file: CurseForgeFile?): Boolean {
        if (file == null) {
            return false
        }
        try {
            val resolvedProjectId = resolveProjectId(projectId)
            if (resolvedProjectId.isNullOrBlank()) {
                return false
            }
            applyDownloadUrl(resolvedProjectId, file)
        } catch (_: IOException) {
            return false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return !file.url.isNullOrBlank()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun applyDownloadUrl(resolvedProjectId: String, file: CurseForgeFile?) {
        if (file == null) {
            return
        }
        if (file.url.isNullOrBlank()) {
            val downloadUrl = fetchDownloadUrlWithRetry(resolvedProjectId, file.id, getApiKey())
            val fallbackUrl = if (downloadUrl.url == null) fallbackDownloadUrl(resolvedProjectId, file.id) else null
            file.url = downloadUrl.url ?: fallbackUrl
            resolveMissingFileName(file, fallbackUrl)
            resolveAlternateDownloadUrls(file, fallbackUrl, file.downloadUrlExplicitlyNull || downloadUrl.useCdnFallback)
        } else {
            resolveMissingFileName(file, null)
            resolveAlternateDownloadUrls(file, null, false)
        }
    }

    private fun copyFile(source: CurseForgeFile?): CurseForgeFile? {
        if (source == null) {
            return null
        }
        val copy = CurseForgeFile()
        copy.id = source.id
        copy.fileName = source.fileName
        copy.displayName = source.displayName
        copy.fileDate = source.fileDate
        copy.url = source.url
        copy.classSlug = source.classSlug
        copy.sha1 = source.sha1
        copy.md5 = source.md5
        copy.alternateUrls.addAll(source.alternateUrls)
        copy.downloadUrlExplicitlyNull = source.downloadUrlExplicitlyNull
        copy.size = source.size
        copy.releaseType = source.releaseType
        return copy
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchFileByVersion(projectId: String?, version: String?, mcVersion: String?, loader: String?): CurseForgeFile? {
        if (projectId.isNullOrBlank() || version.isNullOrBlank()) {
            return null
        }
        val apiKey = getApiKey()
        var endpoint = "https://api.curseforge.com/v1/mods/$projectId/files?pageSize=50"
        if (!mcVersion.isNullOrBlank()) {
            endpoint += "&gameVersion=" + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8)
        }
        val root = fetchJsonArrayWithRetry(endpoint, apiKey) ?: return null
        var matched: CurseForgeFile? = null
        var matchedTime: Instant? = null
        val targetLoader = loader?.trim() ?: ""
        val versionLower = version.trim().lowercase()
        for (element in root.getAsJsonArray("data")) {
            if (!element.isJsonObject) {
                continue
            }
            val data = element.asJsonObject
            if (!matchesGameVersion(data, mcVersion, targetLoader)) {
                continue
            }
            val fileName = getString(data, "fileName")
            val displayName = getString(data, "displayName")
            if (!containsVersion(fileName, displayName, versionLower)) {
                continue
            }
            val file = CurseForgeFile()
            file.id = getString(data, "id")
            applyFileData(file, data)
            file.downloadUrlExplicitlyNull = isExplicitNullDownloadUrl(data)
            file.fileName = fileName
            file.displayName = displayName
            val time = parseInstant(file.fileDate)
            if (matched == null || isAfter(time, matchedTime)) {
                matched = file
                matchedTime = time
            }
        }
        val matchedFile = matched ?: return null
        if (matchedFile.url.isNullOrBlank()) {
            val downloadUrl = fetchDownloadUrlWithRetry(projectId, matchedFile.id, apiKey)
            val fallbackUrl = if (downloadUrl.url == null) fallbackDownloadUrl(projectId, matchedFile.id) else null
            matchedFile.url = downloadUrl.url ?: fallbackUrl
            resolveMissingFileName(matchedFile, fallbackUrl)
            resolveAlternateDownloadUrls(matchedFile, fallbackUrl, matchedFile.downloadUrlExplicitlyNull || downloadUrl.useCdnFallback)
        } else {
            resolveMissingFileName(matchedFile, null)
            resolveAlternateDownloadUrls(matchedFile, null, false)
        }
        return matchedFile
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun resolveProjectId(projectIdOrSlug: String?): String? {
        if (projectIdOrSlug.isNullOrBlank()) {
            return null
        }
        val trimmed = projectIdOrSlug.trim()
        if (isNumeric(trimmed)) {
            return trimmed
        }
        val lower = trimmed.lowercase()
        PROJECT_ID_CACHE[lower]?.let { return if (it.isBlank()) null else it }
        val apiKey = getApiKey()
        val endpoint = "https://api.curseforge.com/v1/mods/search?gameId=$MINECRAFT_GAME_ID" +
            "&slug=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
        val root = fetchJsonArrayWithRetry(endpoint, apiKey)
        if (root == null) {
            PROJECT_ID_CACHE[lower] = ""
            return null
        }
        for (element in root.getAsJsonArray("data")) {
            if (!element.isJsonObject) {
                continue
            }
            val data = element.asJsonObject
            val id = getString(data, "id")
            if (!id.isNullOrBlank()) {
                PROJECT_ID_CACHE[lower] = id
                return id
            }
        }
        PROJECT_ID_CACHE[lower] = ""
        return null
    }

    private fun containsVersion(fileName: String?, displayName: String?, versionLower: String?): Boolean {
        if (versionLower.isNullOrBlank()) {
            return false
        }
        if (fileName != null && fileName.lowercase().contains(versionLower)) {
            return true
        }
        return displayName != null && displayName.lowercase().contains(versionLower)
    }

    private fun isNumeric(value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return false
        }
        for (c in value) {
            if (c < '0' || c > '9') {
                return false
            }
        }
        return true
    }

    private fun getApiKey(): String? {
        API_KEY_CACHE?.let { return it }
        val part2 = generatePart2(API_KEY_PART1.size)
        if (API_KEY_PART1.isEmpty() || part2.isEmpty() || API_KEY_PART1.size != part2.size) {
            API_KEY_CACHE = ""
            return API_KEY_CACHE
        }
        val len = API_KEY_PART1.size
        val decoded = ByteArray(len)
        for (i in 0 until len) {
            val mixed = (API_KEY_PART1[i].toInt() xor part2[i].toInt()).toByte()
            decoded[i] = (mixed.toInt() xor API_KEY_MASK.toInt() xor ((i * 31) and 0xFF)).toByte()
        }
        val shifted = ByteArray(len)
        for (i in 0 until len) {
            shifted[(i + API_KEY_SHIFT) % len] = decoded[i]
        }
        API_KEY_CACHE = String(shifted, StandardCharsets.UTF_8)
        return API_KEY_CACHE
    }

    private fun generatePart2(length: Int): ByteArray {
        if (length <= 0) {
            return ByteArray(0)
        }
        val part2 = ByteArray(length)
        var seed = API_KEY_PART2_SEED and 0xFF
        for (i in 0 until length) {
            seed = (seed * 1103515245 + 12345) and 0xFF
            part2[i] = seed.toByte()
        }
        return part2
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchFileDataWithRetry(projectId: String?, fileId: String, apiKey: String?): JsonObject? {
        val endpoint = if (!projectId.isNullOrBlank()) {
            "https://api.curseforge.com/v1/mods/$projectId/files/$fileId"
        } else {
            "https://api.curseforge.com/v1/mods/files/$fileId"
        }
        val root = sendJsonWithRetry(endpoint, apiKey) ?: return null
        if (!root.has("data") || !root.get("data").isJsonObject) {
            return null
        }
        return root.getAsJsonObject("data")
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchDownloadUrlWithRetry(projectId: String?, fileId: String?, apiKey: String?): DownloadUrlResult {
        return fetchDownloadUrl(projectId, fileId, apiKey)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchDownloadUrl(projectId: String?, fileId: String?, apiKey: String?): DownloadUrlResult {
        if (projectId.isNullOrBlank() || fileId.isNullOrBlank()) {
            return DownloadUrlResult(null, false)
        }
        val endpoint = "https://api.curseforge.com/v1/mods/$projectId/files/$fileId/download-url"
        var useCdnFallback = false
        var lastError: IOException? = null
        try {
            val official = sendJsonWithFixedRetryResult(endpoint, apiKey)
            if (official.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                useCdnFallback = true
            }
            extractDownloadUrl(official.root)?.let { return DownloadUrlResult(it, false) }
        } catch (ex: IOException) {
            lastError = ex
        }
        val mirrorEndpoint = mirrorApiEndpoint(endpoint)
        if (mirrorEndpoint != endpoint) {
            try {
                val mirror = sendJsonWithFixedRetryResult(mirrorEndpoint, null)
                if (mirror.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    useCdnFallback = true
                }
                extractDownloadUrl(mirror.root)?.let { return DownloadUrlResult(it, false) }
            } catch (ex: IOException) {
                if (useCdnFallback) {
                    return DownloadUrlResult(null, true)
                }
                if (lastError != null) {
                    throw ex
                }
            }
        } else if (lastError != null) {
            throw lastError
        }
        return DownloadUrlResult(null, useCdnFallback)
    }

    private fun extractDownloadUrl(root: JsonObject?): String? {
        if (root == null || !root.has("data") || !root.get("data").isJsonObject) {
            return null
        }
        val data = root.getAsJsonObject("data")
        return if (data.has("downloadUrl")) getString(data, "downloadUrl") else null
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchJsonArrayWithRetry(endpoint: String, apiKey: String?): JsonObject? {
        val root = sendJsonWithRetry(endpoint, apiKey) ?: return null
        if (!root.has("data") || !root.get("data").isJsonArray) {
            return null
        }
        return root
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchJsonObjectWithRetry(endpoint: String, apiKey: String?): JsonObject? {
        val root = sendJsonWithRetry(endpoint, apiKey) ?: return null
        if (!root.has("data") || !root.get("data").isJsonObject) {
            return null
        }
        return root
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun sendJsonWithRetry(endpoint: String, apiKey: String?): JsonObject? {
        var lastError: IOException? = null
        if (!apiKey.isNullOrBlank()) {
            try {
                val official = sendJsonWithFixedRetry(endpoint, apiKey)
                if (official != null) {
                    return official
                }
            } catch (ex: IOException) {
                lastError = ex
            }
        }
        val mirrorEndpoint = mirrorApiEndpoint(endpoint)
        if (mirrorEndpoint != endpoint) {
            try {
                return sendJsonWithFixedRetry(mirrorEndpoint, null)
            } catch (ex: IOException) {
                lastError = ex
            }
        }
        if (lastError != null) {
            throw lastError
        }
        return null
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun sendJsonWithFixedRetry(endpoint: String, apiKey: String?): JsonObject? {
        return sendJsonWithFixedRetryResult(endpoint, apiKey).root
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun sendJsonWithFixedRetryResult(endpoint: String, apiKey: String?): ApiJsonResult {
        var lastError: IOException? = null
        for (attempt in 1..API_MAX_ATTEMPTS) {
            try {
                val builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("User-Agent", "DLC-Manager")
                    .timeout(Duration.ofMillis(DlcConfig.getTaskTimeoutMs().toLong()))
                    .GET()
                if (!apiKey.isNullOrBlank()) {
                    builder.header("x-api-key", apiKey)
                }
                val request = builder.build()
                val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (isRetryableStatus(response.statusCode()) && attempt < API_MAX_ATTEMPTS) {
                        continue
                    }
                    return ApiJsonResult(null, response.statusCode())
                }
                val root = GSON.fromJson(response.body(), JsonObject::class.java)
                return ApiJsonResult(root, response.statusCode())
            } catch (ex: IOException) {
                lastError = ex
            }
        }
        if (lastError != null) {
            throw lastError
        }
        return ApiJsonResult(null, -1)
    }

    private fun mirrorApiEndpoint(endpoint: String): String {
        return endpoint.replace("https://api.curseforge.com", "https://mod.mcimirror.top/curseforge")
    }

    private fun isRetryableStatus(statusCode: Int): Boolean {
        return statusCode == 429 || statusCode >= 500
    }

    private fun fallbackDownloadUrl(projectId: String?, fileId: String?): String? {
        if (projectId.isNullOrBlank() || fileId.isNullOrBlank()) {
            return null
        }
        return "https://www.curseforge.com/api/v1/mods/$projectId/files/$fileId/download"
    }

    private fun resolveMissingFileName(file: CurseForgeFile?, fallbackUrl: String?) {
        if (file == null || !file.fileName.isNullOrBlank()) {
            return
        }
        var fileName = DlcDownloadUtil.extractFileNameFromUrl(file.url)
        if (fileName.isNullOrBlank() && !fallbackUrl.isNullOrBlank()) {
            fileName = extractFileNameFromFallbackRedirect(fallbackUrl)
        }
        if (!fileName.isNullOrBlank()) {
            file.fileName = fileName
        }
    }

    private fun resolveAlternateDownloadUrls(file: CurseForgeFile?, fallbackUrl: String?, useCdnFallback: Boolean) {
        if (file == null) {
            return
        }
        file.alternateUrls.clear()
        val fileName = DlcDownloadUtil.sanitizeFileName(file.fileName)
        if (useCdnFallback && !fallbackUrl.isNullOrBlank() && !file.id.isNullOrBlank() && !fileName.isNullOrBlank()) {
            for (host in CDN_HOSTS) {
                addAlternateUrl(file, buildForgeCdnUrl(host, file.id, fileName))
            }
        }
        addAlternateUrl(file, fallbackUrl)
    }

    private fun addAlternateUrl(file: CurseForgeFile, url: String?) {
        if (url.isNullOrBlank() || url == file.url || file.alternateUrls.contains(url)) {
            return
        }
        file.alternateUrls.add(url)
    }

    private fun buildForgeCdnUrl(host: String, fileId: String?, fileName: String?): String? {
        if (fileId.isNullOrBlank() || fileName.isNullOrBlank()) {
            return null
        }
        val id = try {
            fileId.trim().toLong()
        } catch (_: NumberFormatException) {
            return null
        }
        if (id < 0) {
            return null
        }
        val part1 = id / 1000
        val part2 = id % 1000
        val encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
        return "$host/files/$part1/$part2/$encodedName"
    }

    private fun extractFileNameFromFallbackRedirect(fallbackUrl: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URI.create(fallbackUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = DlcConfig.getConnectTimeoutMs()
            connection.readTimeout = DlcConfig.getTaskTimeoutMs()
            connection.setRequestProperty("User-Agent", "DLC-Manager")
            connection.connect()
            if (!isRedirect(connection.responseCode)) {
                return null
            }
            val location = connection.getHeaderField("Location")
            val redirected = resolveRedirectUrl(fallbackUrl, location)
            DlcDownloadUtil.extractFileNameFromUrl(redirected)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isRedirect(statusCode: Int): Boolean {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM
            || statusCode == HttpURLConnection.HTTP_MOVED_TEMP
            || statusCode == HttpURLConnection.HTTP_SEE_OTHER
            || statusCode == 307
            || statusCode == 308
    }

    private fun resolveRedirectUrl(baseUrl: String, location: String?): String? {
        if (location.isNullOrBlank()) {
            return null
        }
        return try {
            URI.create(baseUrl).resolve(location).toString()
        } catch (_: IllegalArgumentException) {
            location
        }
    }

    private fun getString(obj: JsonObject?, key: String): String? {
        return if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive) {
            obj.get(key).asString
        } else {
            null
        }
    }

    private fun isExplicitNullDownloadUrl(data: JsonObject?): Boolean {
        return data != null && data.has("downloadUrl") && data.get("downloadUrl").isJsonNull
    }

    private fun matchesGameVersion(data: JsonObject?, mcVersion: String?, loader: String?): Boolean {
        if (data == null || !data.has("gameVersions") || !data.get("gameVersions").isJsonArray) {
            return false
        }
        val versions = HashSet<String>()
        for (entry in data.getAsJsonArray("gameVersions")) {
            if (entry.isJsonPrimitive) {
                versions.add(entry.asString)
            }
        }
        if (!mcVersion.isNullOrBlank() && !versions.contains(mcVersion)) {
            return false
        }
        if (!loader.isNullOrBlank()) {
            for (value in versions) {
                if (value.equals(loader, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
        return true
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun isAfter(candidate: Instant?, current: Instant?): Boolean {
        if (candidate == null) {
            return false
        }
        if (current == null) {
            return true
        }
        return candidate.isAfter(current)
    }

    private fun applyFileData(file: CurseForgeFile?, data: JsonObject?) {
        if (file == null || data == null) {
            return
        }
        file.fileName = getString(data, "fileName")
        file.displayName = getString(data, "displayName")
        file.fileDate = getString(data, "fileDate")
        file.url = getString(data, "downloadUrl")
        file.classSlug = readClassSlug(data)
        if (data.has("fileLength") && data.get("fileLength").isJsonPrimitive) {
            try {
                file.size = data.get("fileLength").asLong
            } catch (_: Exception) {
                file.size = -1L
            }
        }
        if (data.has("releaseType") && data.get("releaseType").isJsonPrimitive) {
            try {
                file.releaseType = data.get("releaseType").asInt
            } catch (_: Exception) {
                file.releaseType = 0
            }
        }
        if (data.has("hashes") && data.get("hashes").isJsonArray) {
            for (entry in data.getAsJsonArray("hashes")) {
                if (!entry.isJsonObject) {
                    continue
                }
                val hash = entry.asJsonObject
                var algo = -1
                if (hash.has("algo") && hash.get("algo").isJsonPrimitive) {
                    try {
                        algo = hash.get("algo").asInt
                    } catch (_: Exception) {
                        algo = -1
                    }
                }
                val value = getString(hash, "value")
                if (value.isNullOrBlank()) {
                    continue
                }
                if (algo == 1 && file.sha1.isNullOrBlank()) {
                    file.sha1 = value
                } else if (algo == 2 && file.md5.isNullOrBlank()) {
                    file.md5 = value
                }
            }
        }
    }

    class CurseForgeFile {
        @JvmField var id: String? = null
        @JvmField var fileName: String? = null
        @JvmField var displayName: String? = null
        @JvmField var fileDate: String? = null
        @JvmField var url: String? = null
        @JvmField var classSlug: String? = null
        @JvmField var sha1: String? = null
        @JvmField var md5: String? = null
        @JvmField val alternateUrls: MutableList<String> = ArrayList()
        @JvmField var downloadUrlExplicitlyNull: Boolean = false
        @JvmField var size: Long = 0L
        @JvmField var releaseType: Int = 0
    }

    class CurseForgeMod {
        @JvmField var id: String? = null
        @JvmField var classSlug: String? = null
        @JvmField var primaryCategorySlug: String? = null
        @JvmField var primaryCategoryName: String? = null
        @JvmField val categorySlugs: MutableList<String> = ArrayList()
    }

    private data class DownloadUrlResult(
        val url: String?,
        val useCdnFallback: Boolean,
    )

    private data class ApiJsonResult(
        val root: JsonObject?,
        val statusCode: Int,
    )

    private fun readClassSlug(data: JsonObject?): String? {
        if (data == null) {
            return null
        }
        val direct = getString(data, "classSlug")
        if (!direct.isNullOrBlank()) {
            return direct
        }
        if (data.has("class") && data.get("class").isJsonObject) {
            val clazz = data.getAsJsonObject("class")
            val slug = getString(clazz, "slug")
            if (!slug.isNullOrBlank()) {
                return slug
            }
            val name = getString(clazz, "name")
            if (!name.isNullOrBlank()) {
                return name
            }
        }
        if (data.has("categories") && data.get("categories").isJsonArray) {
            for (element in data.getAsJsonArray("categories")) {
                if (!element.isJsonObject) {
                    continue
                }
                val category = element.asJsonObject
                val slug = getString(category, "slug")
                if (!slug.isNullOrBlank()) {
                    return slug
                }
                val name = getString(category, "name")
                if (!name.isNullOrBlank()) {
                    return name
                }
            }
        }
        return null
    }

    private fun applyModData(mod: CurseForgeMod?, data: JsonObject?) {
        if (mod == null || data == null) {
            return
        }
        mod.id = getString(data, "id")
        if (data.has("classId") && data.get("classId").isJsonPrimitive) {
            try {
                mod.classSlug = getString(data, "classSlug")
            } catch (_: Exception) {
                mod.classSlug = null
            }
        }
        if (data.has("categories") && data.get("categories").isJsonArray) {
            for (element in data.getAsJsonArray("categories")) {
                if (!element.isJsonObject) {
                    continue
                }
                val category = element.asJsonObject
                val slug = getString(category, "slug")
                val name = getString(category, "name")
                if (mod.primaryCategorySlug.isNullOrBlank() && !slug.isNullOrBlank()) {
                    mod.primaryCategorySlug = slug
                }
                if (mod.primaryCategoryName.isNullOrBlank() && !name.isNullOrBlank()) {
                    mod.primaryCategoryName = name
                }
                if (!slug.isNullOrBlank()) {
                    mod.categorySlugs.add(slug)
                } else if (!name.isNullOrBlank()) {
                    mod.categorySlugs.add(name)
                }
            }
        }
        if (mod.classSlug.isNullOrBlank()) {
            mod.classSlug = mod.primaryCategorySlug
        }
    }
}
