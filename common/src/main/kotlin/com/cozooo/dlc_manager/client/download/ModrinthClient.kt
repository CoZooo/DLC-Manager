package com.cozooo.dlc_manager.client.download

import com.cozooo.dlc_manager.core.DlcPathConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object ModrinthClient {
    private val GSON = Gson()
    private val CLIENT: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(DlcPathConfig.getConnectTimeoutMs().toLong()))
        .build()
    private val CACHE: MutableMap<String, ModrinthVersion> = ConcurrentHashMap()

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun fetchVersion(versionId: String?): ModrinthVersion? {
        if (versionId.isNullOrBlank()) {
            return null
        }
        CACHE[versionId]?.let { return it }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.modrinth.com/v2/version/$versionId"))
            .header("User-Agent", "DLC-Manager")
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs().toLong()))
            .GET()
            .build()
        val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null
        }
        val obj = GSON.fromJson(response.body(), JsonObject::class.java) ?: return null
        val version = ModrinthVersion()
        val files = obj.getAsJsonArray("files")
        if (files != null && files.size() > 0) {
            val fileObj = files[0].asJsonObject
            applyFile(version, fileObj)
            for (element in files) {
                val file = element.asJsonObject
                if (file.has("primary") && file.get("primary").asBoolean) {
                    applyFile(version, file)
                    break
                }
            }
        }
        val deps = obj.getAsJsonArray("dependencies")
        if (deps != null) {
            for (element in deps) {
                if (!element.isJsonObject) {
                    continue
                }
                val depObj = element.asJsonObject
                val depType = getString(depObj, "dependency_type")
                if (!"required".equals(depType, ignoreCase = true)) {
                    continue
                }
                val dep = ModrinthDependency()
                dep.versionId = getString(depObj, "version_id")
                dep.projectId = getString(depObj, "project_id")
                version.requiredDependencies.add(dep)
            }
        }
        CACHE[versionId] = version
        return version
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun fetchVersionByIdOrVersion(projectIdOrSlug: String?, versionIdOrString: String?, mcVersion: String?, loader: String?): ModrinthVersion? {
        if (versionIdOrString.isNullOrBlank()) {
            return null
        }
        val trimmed = versionIdOrString.trim()
        val direct = fetchVersion(trimmed)
        if (direct != null) {
            return direct
        }
        if (projectIdOrSlug.isNullOrBlank()) {
            return null
        }
        val cacheKey = projectIdOrSlug + ":" + (mcVersion ?: "") +
            ":" + (loader ?: "") + ":version:" + trimmed
        CACHE[cacheKey]?.let { return it }
        val matched = fetchVersionByProjectVersion(projectIdOrSlug, trimmed, mcVersion, loader)
        if (matched != null) {
            CACHE[cacheKey] = matched
        }
        return matched
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fetchVersionByProjectVersion(projectIdOrSlug: String, version: String, mcVersion: String?, loader: String?): ModrinthVersion? {
        var endpoint = "https://api.modrinth.com/v2/project/$projectIdOrSlug/version"
        val normalizedLoader = normalizeLoader(loader)
        val params = ArrayList<String>()
        if (!normalizedLoader.isNullOrBlank()) {
            params.add("loaders=" + URLEncoder.encode("[\"$normalizedLoader\"]", StandardCharsets.UTF_8))
        }
        if (!mcVersion.isNullOrBlank()) {
            params.add("game_versions=" + URLEncoder.encode("[\"$mcVersion\"]", StandardCharsets.UTF_8))
        }
        if (params.isNotEmpty()) {
            endpoint += "?" + params.joinToString("&")
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("User-Agent", "DLC-Manager")
            .timeout(Duration.ofMillis(DlcPathConfig.getTaskTimeoutMs().toLong()))
            .GET()
            .build()
        val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null
        }
        val data = GSON.fromJson(response.body(), JsonArray::class.java) ?: return null
        val versionLower = version.trim().lowercase()
        var matched: ModrinthVersion? = null
        var matchedTime: Instant? = null
        for (element in data) {
            if (!element.isJsonObject) {
                continue
            }
            val obj = element.asJsonObject
            val versionNumber = getString(obj, "version_number")
            if (versionNumber == null || !versionNumber.lowercase().contains(versionLower)) {
                continue
            }
            if (!matchesGameVersion(obj, mcVersion, normalizedLoader)) {
                continue
            }
            val versionObj = ModrinthVersion()
            val files = obj.getAsJsonArray("files")
            if (files != null && files.size() > 0) {
                val fileObj = files[0].asJsonObject
                applyFile(versionObj, fileObj)
                for (fileElement in files) {
                    val file = fileElement.asJsonObject
                    if (file.has("primary") && file.get("primary").asBoolean) {
                        applyFile(versionObj, file)
                        break
                    }
                }
            }
            val deps = obj.getAsJsonArray("dependencies")
            if (deps != null) {
                for (depElement in deps) {
                    if (!depElement.isJsonObject) {
                        continue
                    }
                    val depObj = depElement.asJsonObject
                    val depType = getString(depObj, "dependency_type")
                    if (!"required".equals(depType, ignoreCase = true)) {
                        continue
                    }
                    val dep = ModrinthDependency()
                    dep.versionId = getString(depObj, "version_id")
                    dep.projectId = getString(depObj, "project_id")
                    versionObj.requiredDependencies.add(dep)
                }
            }
            val time = parseInstant(getString(obj, "date_published"))
            if (matched == null || isAfter(time, matchedTime)) {
                matched = versionObj
                matchedTime = time
            }
        }
        return matched
    }

    private fun getString(obj: JsonObject?, key: String): String? {
        return if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive) {
            obj.get(key).asString
        } else {
            null
        }
    }

    private fun matchesGameVersion(obj: JsonObject?, mcVersion: String?, loader: String?): Boolean {
        if (obj == null) {
            return false
        }
        if (!mcVersion.isNullOrBlank()) {
            val versions = obj.getAsJsonArray("game_versions")
            if (versions == null || !containsIgnoreCase(versions, mcVersion)) {
                return false
            }
        }
        if (!loader.isNullOrBlank()) {
            val loaders = obj.getAsJsonArray("loaders")
            if (loaders == null || !containsIgnoreCase(loaders, loader)) {
                return false
            }
        }
        return true
    }

    private fun containsIgnoreCase(array: JsonArray?, value: String?): Boolean {
        if (array == null || value == null) {
            return false
        }
        for (element in array) {
            if (element.isJsonPrimitive && value.equals(element.asString, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun normalizeLoader(loader: String?): String? {
        if (loader.isNullOrBlank()) {
            return null
        }
        val trimmed = loader.trim().lowercase()
        return when (trimmed) {
            "forge" -> "forge"
            "neoforge", "neo_forge", "neo-forge" -> "neoforge"
            "fabric" -> "fabric"
            else -> trimmed
        }
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

    private fun applyFile(target: ModrinthVersion?, file: JsonObject?) {
        if (target == null || file == null) {
            return
        }
        target.fileName = getString(file, "filename")
        target.url = getString(file, "url")
        target.size = getLong(file, "size")
        if (file.has("hashes") && file.get("hashes").isJsonObject) {
            val hashes = file.getAsJsonObject("hashes")
            target.sha1 = getString(hashes, "sha1")
            target.sha512 = getString(hashes, "sha512")
        }
    }

    class ModrinthVersion {
        @JvmField var fileName: String? = null
        @JvmField var url: String? = null
        @JvmField var sha1: String? = null
        @JvmField var sha512: String? = null
        @JvmField var size: Long = 0L
        @JvmField val requiredDependencies: MutableList<ModrinthDependency> = ArrayList()
    }

    class ModrinthDependency {
        @JvmField var versionId: String? = null
        @JvmField var projectId: String? = null
    }

    private fun getLong(obj: JsonObject?, key: String): Long {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive) {
            try {
                return obj.get(key).asLong
            } catch (_: Exception) {
                return -1L
            }
        }
        return -1L
    }
}
