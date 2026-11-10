package com.cozooo.dlc_manager.client.selfupdate

import com.cozooo.dlc_manager.client.download.DlcCurseForgeClient
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.download.DlcModrinthClient
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateSkipStore
import com.cozooo.dlc_manager.core.DlcVersionUtil
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Locale
import java.util.regex.Pattern

object DlcSelfUpdateService {
    const val MOD_ID: String = "dlc_manager"
    const val PROJECT_ID: String = "1424546"
    private const val MODRINTH_PROJECT_ID: String = "B05EUil9"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val DISPLAY_NAME_VERSION_PATTERN: Pattern = Pattern.compile(
        "^\\s*(\\d+\\.\\d+(?:\\.\\d+)?(?:-[0-9A-Za-z][0-9A-Za-z.]*)?)\\s*\\("
    )
    private val FILE_NAME_VERSION_PATTERN: Pattern = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?")
    private val FILE_NAME_PRERELEASE_PATTERN: Pattern = Pattern.compile(
        "(?i)(?:alpha|beta|rc|pre|nightly|build)[0-9A-Za-z.]*"
    )
    private val MAJOR_VERSION_PATTERN: Pattern = Pattern.compile("(\\d+)")

    @JvmStatic
    fun checkForUpdate(): SelfUpdateResult {
        return checkForUpdate(null)
    }

    @JvmStatic
    fun checkForUpdate(targetVersion: String?): SelfUpdateResult {
        val loader = DlcPlatformServices.loaderName()
        val mcVersion = DlcPlatformServices.mcVersion()
        val currentVersion = DlcPlatformServices.modVersion(MOD_ID)
        var requestedVersion = targetVersion?.trim()
        if (!requestedVersion.isNullOrBlank().not()) {
            requestedVersion = null
        }
        if (loader.isNullOrBlank() || mcVersion.isNullOrBlank()) {
            return SelfUpdateResult.none(currentVersion)
        }
        if (requestedVersion != null && isSameVersion(currentVersion, requestedVersion)) {
            val result = SelfUpdateResult.none(currentVersion)
            result.latestVersion = requestedVersion
            result.requestedVersion = requestedVersion
            return result
        }
        return if (requestedVersion == null) {
            checkAutomaticUpdate(currentVersion, mcVersion, loader)
        } else {
            checkRequestedVersion(currentVersion, requestedVersion, mcVersion, loader)
        }
    }

    private fun checkAutomaticUpdate(currentVersion: String?, mcVersion: String?, loader: String?): SelfUpdateResult {
        val candidates = ArrayList<UpdateCandidate>()
        var fetched = false
        try {
            for (file in DlcCurseForgeClient.fetchFiles(PROJECT_ID, mcVersion, loader)) {
                UpdateCandidate.fromCurseForge(file)?.let { candidates.add(it) }
            }
            fetched = true
        } catch (_: Exception) {
        }
        var best = pickUpdateCandidate(currentVersion, candidates)
        if (best == null) {
            try {
                for (version in DlcModrinthClient.fetchVersions(MODRINTH_PROJECT_ID, mcVersion, loader)) {
                    UpdateCandidate.fromModrinth(version)?.let { candidates.add(it) }
                }
                fetched = true
            } catch (_: Exception) {
            }
            best = pickUpdateCandidate(currentVersion, candidates)
        }
        if (!fetched) {
            return SelfUpdateResult.failed(currentVersion)
        }
        if (best == null) {
            return SelfUpdateResult.none(currentVersion)
        }
        if (best.curseForgeFile != null && best.url.isNullOrBlank()) {
            try {
                DlcCurseForgeClient.resolveDownloadUrl(PROJECT_ID, best.curseForgeFile)
            } catch (_: Exception) {
            }
            best.url = best.curseForgeFile?.url
        }
        if (best.url.isNullOrBlank()) {
            return SelfUpdateResult.failed(currentVersion)
        }
        val result = SelfUpdateResult()
        result.currentVersion = currentVersion ?: ""
        result.latestVersion = best.version ?: ""
        result.downloadUrl = best.url
        result.fileName = best.fileName
        result.fileId = best.fileId
        result.displayName = best.displayName
        result.fileDate = best.fileDate
        result.hasUpdate = true
        return result
    }

    private fun checkRequestedVersion(currentVersion: String?, requestedVersion: String, mcVersion: String?, loader: String?): SelfUpdateResult {
        var lookupFailed = false
        var file: DlcCurseForgeClient.CurseForgeFile? = null
        try {
            file = DlcCurseForgeClient.fetchFileByIdOrVersion(PROJECT_ID, requestedVersion, mcVersion, loader)
        } catch (_: Exception) {
            lookupFailed = true
        }
        if (file != null) {
            return requestedCurseForgeResult(currentVersion, requestedVersion, file)
        }
        var version: DlcModrinthClient.ModrinthVersion? = null
        try {
            version = DlcModrinthClient.fetchVersionByIdOrVersion(MODRINTH_PROJECT_ID, requestedVersion, mcVersion, loader)
        } catch (_: Exception) {
        }
        if (version != null) {
            return requestedModrinthResult(currentVersion, requestedVersion, version)
        }
        val result = SelfUpdateResult.none(currentVersion)
        result.requestedVersion = requestedVersion
        if (lookupFailed) {
            result.checkFailed = true
        } else {
            result.missingRequestedVersion = true
        }
        return result
    }

    private fun requestedCurseForgeResult(currentVersion: String?, requestedVersion: String, file: DlcCurseForgeClient.CurseForgeFile): SelfUpdateResult {
        if (file.url.isNullOrBlank()) {
            try {
                DlcCurseForgeClient.resolveDownloadUrl(PROJECT_ID, file)
            } catch (_: Exception) {
            }
        }
        if (file.url.isNullOrBlank()) {
            val failed = SelfUpdateResult.failed(currentVersion)
            failed.requestedVersion = requestedVersion
            return failed
        }
        val latestVersion = extractVersion(file.displayName, file.fileName)
        val effectiveVersion = if (latestVersion.isNullOrBlank()) requestedVersion else latestVersion
        val result = SelfUpdateResult()
        result.currentVersion = currentVersion ?: ""
        result.latestVersion = effectiveVersion
        result.downloadUrl = file.url
        result.fileName = file.fileName
        result.fileId = file.id
        result.displayName = file.displayName
        result.fileDate = file.fileDate
        result.hasUpdate = !isSameVersion(currentVersion, effectiveVersion)
        result.requestedVersion = requestedVersion
        return result
    }

    private fun requestedModrinthResult(currentVersion: String?, requestedVersion: String, version: DlcModrinthClient.ModrinthVersion): SelfUpdateResult {
        if (version.url.isNullOrBlank()) {
            val failed = SelfUpdateResult.failed(currentVersion)
            failed.requestedVersion = requestedVersion
            return failed
        }
        val latestVersion = version.versionNumber
        val effectiveVersion = if (latestVersion.isNullOrBlank()) requestedVersion else latestVersion
        val result = SelfUpdateResult()
        result.currentVersion = currentVersion ?: ""
        result.latestVersion = effectiveVersion
        result.downloadUrl = version.url
        result.fileName = version.fileName
        result.fileId = version.versionId
        result.displayName = effectiveVersion
        result.fileDate = version.datePublished?.toString()
        result.hasUpdate = !isSameVersion(currentVersion, effectiveVersion)
        result.requestedVersion = requestedVersion
        return result
    }

    @JvmStatic
    fun downloadUpdate(result: SelfUpdateResult?): DownloadResult {
        if (result == null || !result.hasUpdate || result.downloadUrl.isNullOrBlank()) {
            return DownloadResult.failed("no_update")
        }
        var fileName = result.fileName
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(result.downloadUrl)
        }
        if (fileName.isNullOrBlank()) {
            fileName = "dlc_manager-update.jar"
        }
        val dlcRoot = DlcPaths.dlcRoot()
        try {
            if (!Files.exists(dlcRoot)) {
                Files.createDirectories(dlcRoot)
            }
        } catch (_: IOException) {
        }
        val updateDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate") ?: return DownloadResult.failed("dlc_root_missing")
        val tempFile = updateDir.resolve("$fileName.part")
        val targetFile = updateDir.resolve(fileName)
        try {
            Files.createDirectories(updateDir)
            if (Files.exists(tempFile)) {
                Files.deleteIfExists(tempFile)
            }
            downloadToFile(result.downloadUrl!!, tempFile)
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            try {
                Files.deleteIfExists(tempFile)
            } catch (_: IOException) {
            }
            return DownloadResult.failed("download_failed")
        }
        val modFilePath = DlcPlatformServices.modFilePath(MOD_ID)
        if (modFilePath == null || !Files.exists(modFilePath) || Files.isDirectory(modFilePath)) {
            try {
                Files.deleteIfExists(targetFile)
            } catch (_: IOException) {
            }
            return DownloadResult.failed("mod_file_missing")
        }
        val modsDir = modFilePath.parent
        if (modsDir == null) {
            try {
                Files.deleteIfExists(targetFile)
            } catch (_: IOException) {
            }
            return DownloadResult.failed("mod_file_missing")
        }
        val finalFile = modsDir.resolve(targetFile.fileName.toString())
        try {
            val backupFile = replaceModFileNow(targetFile, finalFile, modFilePath)
            if (backupFile != null) {
                saveHistory(
                    backupFile,
                    result.currentVersion,
                    result.latestVersion,
                    modFilePath.fileName.toString(),
                    finalFile.fileName.toString()
                )
            }
        } catch (_: IOException) {
            try {
                Files.deleteIfExists(targetFile)
            } catch (_: IOException) {
            }
            return DownloadResult.failed("download_failed")
        }
        clearSkipPromptMarker()
        clearPendingMarker()
        return DownloadResult.success(finalFile)
    }

    @JvmStatic
    fun skipPromptForUpdate(result: SelfUpdateResult?) {
        if (result == null || !result.hasUpdate) {
            return
        }
        try {
            val obj = JsonObject()
            obj.addProperty("fileId", result.fileId ?: "")
            obj.addProperty("latestVersion", result.latestVersion ?: "")
            obj.addProperty("fileName", result.fileName ?: "")
            obj.addProperty("skippedAt", Instant.now().toString())
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE, obj)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun isPromptSkippedForUpdate(result: SelfUpdateResult?): Boolean {
        if (result == null || !result.hasUpdate) {
            return false
        }
        val obj = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE) ?: return false
        val skippedFileId = readString(obj, "fileId")
        val skippedLatest = readString(obj, "latestVersion")
        val skippedFileName = readString(obj, "fileName")

        if (skippedFileId.isNotBlank() && !result.fileId.isNullOrBlank() && skippedFileId == result.fileId) {
            return true
        }
        if (skippedLatest.isNotBlank() && !result.latestVersion.isNullOrBlank() && skippedLatest == result.latestVersion) {
            return true
        }
        return skippedFileName.isNotBlank() && !result.fileName.isNullOrBlank() && skippedFileName == result.fileName
    }

    @JvmStatic
    fun applyPendingUpdate(): Boolean {
        val marker = getMarkerPath()
        if (marker == null || !Files.exists(marker)) {
            return false
        }
        val obj = try {
            DlcDcConfigIO.read(marker, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: Exception) {
            return false
        }
        if (obj == null || !obj.has("pendingFile") || !obj.has("targetFile")) {
            return false
        }
        val pending = resolveStoredPath(obj.get("pendingFile").asString)
        val target = resolveStoredPath(obj.get("targetFile").asString)
        if (!Files.exists(pending)) {
            return false
        }
        return try {
            val targetDir = target.parent ?: return false
            val dlcRoot = DlcPaths.dlcRoot()
            try {
                if (!Files.exists(dlcRoot)) {
                    Files.createDirectories(dlcRoot)
                }
            } catch (_: IOException) {
            }
            val backupDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate") ?: return false
            Files.createDirectories(backupDir)
            if (Files.exists(target)) {
                val backupName = target.fileName.toString() + "." + System.currentTimeMillis() + ".bak"
                Files.move(target, backupDir.resolve(backupName), StandardCopyOption.REPLACE_EXISTING)
            }
            if (pending.parent != targetDir) {
                val newTarget = targetDir.resolve(pending.fileName.toString())
                Files.copy(pending, newTarget, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(pending)
            }
            Files.deleteIfExists(marker)
            true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun rollbackToPrevious(): Boolean {
        val history = loadHistory()
        if (history == null || history.backupFile.isNullOrBlank()) {
            return false
        }
        val backup = DlcPaths.parsePathLenient(history.backupFile) ?: return false
        if (!Files.exists(backup)) {
            return false
        }
        val current = DlcPlatformServices.modFilePath(MOD_ID)
        if (current == null || !Files.exists(current)) {
            return false
        }
        val dlcRoot = DlcPaths.dlcRoot()
        return try {
            if (!Files.exists(dlcRoot)) {
                Files.createDirectories(dlcRoot)
            }
            val backupDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate") ?: return false
            Files.createDirectories(backupDir)
            val modsDir = current.parent ?: return false
            if (!history.newFileName.isNullOrBlank()) {
                val newFile = modsDir.resolve(history.newFileName)
                if (Files.exists(newFile)) {
                    Files.deleteIfExists(newFile)
                }
            }
            if (Files.exists(current)) {
                val backupName = current.fileName.toString() + "." + System.currentTimeMillis() + ".bak"
                Files.move(current, backupDir.resolve(backupName), StandardCopyOption.REPLACE_EXISTING)
            }
            val restoreName = if (history.oldFileName.isNullOrBlank()) {
                var backupName = backup.fileName.toString()
                if (backupName.endsWith(".bak")) {
                    backupName = backupName.substring(0, backupName.length - 4)
                    val lastDot = backupName.lastIndexOf('.')
                    if (lastDot > 0) {
                        val tail = backupName.substring(lastDot + 1)
                        if (tail.chars().allMatch(Character::isDigit)) {
                            backupName = backupName.substring(0, lastDot)
                        }
                    }
                }
                backupName
            } else {
                history.oldFileName
            }
            val target = modsDir.resolve(restoreName)
            Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING)
            clearHistory()
            true
        } catch (_: IOException) {
            false
        }
    }

    @JvmStatic
    fun loadHistory(): SelfUpdateHistory? {
        val path = getHistoryPath()
        if (path == null || !Files.exists(path)) {
            return null
        }
        return try {
            val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC)
            val history = if (root == null) null else GSON.fromJson(root, SelfUpdateHistory::class.java)
            if (history != null && !history.backupFile.isNullOrBlank()) {
                val dlcRoot = DlcPaths.dlcRoot()
                val historyPath = DlcPaths.parsePathLenient(history.backupFile)
                if (historyPath != null && !historyPath.isAbsolute) {
                    history.backupFile = dlcRoot.resolve(historyPath).toString()
                }
            }
            history
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun clearHistory() {
        val path = getHistoryPath() ?: return
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
        }
    }

    @Throws(IOException::class)
    private fun replaceModFileNow(downloadedFile: Path, finalFile: Path, currentModFile: Path): Path? {
        val dlcRoot = DlcPaths.dlcRoot()
        if (!Files.exists(dlcRoot)) {
            Files.createDirectories(dlcRoot)
        }
        val backupDir = DlcTmpPaths.resolve(dlcRoot, "selfupdate") ?: throw IOException("dlc_root_missing")
        Files.createDirectories(backupDir)
        var backupPath: Path? = null
        if (Files.exists(currentModFile)) {
            val backupName = currentModFile.fileName.toString() + "." + System.currentTimeMillis() + ".bak"
            backupPath = backupDir.resolve(backupName)
            Files.move(currentModFile, backupPath, StandardCopyOption.REPLACE_EXISTING)
        }
        if (downloadedFile != finalFile) {
            Files.copy(downloadedFile, finalFile, StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(downloadedFile)
        }
        return backupPath
    }

    private fun saveHistory(backupFile: Path, oldVersion: String?, newVersion: String?, oldFileName: String?, newFileName: String?) {
        val path = getHistoryPath() ?: return
        try {
            Files.createDirectories(path.parent)
            val history = SelfUpdateHistory()
            history.oldVersion = oldVersion ?: ""
            history.newVersion = newVersion ?: ""
            history.oldFileName = oldFileName ?: ""
            history.newFileName = newFileName ?: ""
            val dlcRoot = DlcPaths.dlcRoot()
            history.backupFile = if (backupFile.startsWith(dlcRoot)) {
                dlcRoot.relativize(backupFile).toString()
            } else {
                backupFile.fileName.toString()
            }
            history.appliedAt = Instant.now().toString()
            val root = GSON.toJsonTree(history).asJsonObject
            DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: Exception) {
        }
    }

    private fun getHistoryPath(): Path? {
        val dlcRoot = DlcPaths.dlcRoot()
        val dir = DlcTmpPaths.resolve(dlcRoot, "selfupdate") ?: return null
        return DlcDcFormatUtil.resolveConfigPath(dir, "self_update_history")
    }

    private fun clearPendingMarker() {
        try {
            Files.deleteIfExists(getMarkerPath())
        } catch (_: IOException) {
        }
    }

    private fun clearSkipPromptMarker() {
        DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE)
    }

    private fun savePendingUpdate(pendingFile: Path, targetFile: Path, result: SelfUpdateResult): SaveResult {
        val marker = getMarkerPath() ?: return SaveResult.failed()
        return try {
            Files.createDirectories(marker.parent)
            val obj = JsonObject()
            obj.addProperty("pendingFile", toStoredPath(pendingFile))
            obj.addProperty("targetFile", toStoredPath(targetFile))
            obj.addProperty("currentVersion", result.currentVersion ?: "")
            obj.addProperty("latestVersion", result.latestVersion ?: "")
            obj.addProperty("updatedAt", Instant.now().toString())
            DlcDcConfigIO.write(marker, obj, DlcDcConfigIO.Layout.GENERIC)
            SaveResult.success()
        } catch (_: IOException) {
            SaveResult.failed()
        }
    }

    private fun resolveStoredPath(value: String?): Path {
        val raw = value?.trim().orEmpty()
        val path = DlcPaths.parsePathLenient(raw)
            ?: return DlcPlatformServices.gameDir().toAbsolutePath().normalize()
        return if (path.isAbsolute) {
            path
        } else {
            DlcPlatformServices.gameDir().resolve(path).normalize()
        }
    }

    private fun toStoredPath(path: Path): String {
        val gameDir = DlcPlatformServices.gameDir().toAbsolutePath().normalize()
        val normalized = path.toAbsolutePath().normalize()
        return try {
            gameDir.relativize(normalized).toString().replace('\\', '/')
        } catch (_: IllegalArgumentException) {
            normalized.fileName.toString()
        }
    }

    private fun getMarkerPath(): Path? {
        val configDir = DlcPlatformServices.configDir()
        val dir = configDir.resolve("dlc_manager")
        return DlcDcFormatUtil.resolveConfigPath(dir, "self_update")
    }

    private fun readString(obj: JsonObject?, key: String?): String {
        if (obj == null || key.isNullOrBlank() || !obj.has(key) || obj.get(key).isJsonNull) {
            return ""
        }
        return try {
            obj.get(key).asString ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    @Throws(IOException::class)
    private fun downloadToFile(url: String, target: Path) {
        var connection: HttpURLConnection? = null
        try {
            connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 180000
            connection.setRequestProperty("User-Agent", "DLC-Manager")
            connection.connect()
            if (connection.responseCode >= 400) {
                throw IOException("http " + connection.responseCode)
            }
            BufferedInputStream(connection.inputStream).use { input ->
                Files.newOutputStream(target).use { output: OutputStream ->
                    val buffer = ByteArray(8192)
                    var len = input.read(buffer)
                    while (len >= 0) {
                        output.write(buffer, 0, len)
                        len = input.read(buffer)
                    }
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    @JvmStatic
    fun isUpdateAvailable(currentVersion: String?, latestVersion: String?, file: DlcCurseForgeClient.CurseForgeFile?): Boolean {
        if (currentVersion.isNullOrBlank()) {
            return true
        }
        val current = currentVersion.trim()
        if (!latestVersion.isNullOrBlank()) {
            var channel: DlcVersionUtil.Channel? = releaseTypeChannel(file?.releaseType ?: 0)
            if (channel == null) {
                channel = versionChannel(latestVersion)
            }
            return shouldOfferUpdate(current, latestVersion, channel)
        }
        val fileName = file?.fileName ?: ""
        val display = file?.displayName ?: ""
        if (fileName.contains(current) || display.contains(current)) {
            return false
        }
        return true
    }

    @JvmStatic
    fun isMajorVersionChanged(currentVersion: String?, latestVersion: String?): Boolean {
        val currentMajor = extractMajorVersion(currentVersion) ?: return false
        val latestMajor = extractMajorVersion(latestVersion) ?: return false
        return currentMajor != latestMajor
    }

    private fun extractMajorVersion(version: String?): Int? {
        if (version.isNullOrBlank()) {
            return null
        }
        val matcher = MAJOR_VERSION_PATTERN.matcher(version.trim())
        if (!matcher.find()) {
            return null
        }
        return try {
            matcher.group(1).toInt()
        } catch (_: NumberFormatException) {
            null
        }
    }

    @JvmStatic
    fun pickUpdateCandidate(currentVersion: String?, candidates: List<UpdateCandidate>?): UpdateCandidate? {
        if (candidates == null) {
            return null
        }
        var best: UpdateCandidate? = null
        for (candidate in candidates) {
            if (!shouldOfferUpdate(currentVersion, candidate.version, candidate.channel)) {
                continue
            }
            val currentBest = best
            if (currentBest == null || isBetterCandidate(candidate, currentBest)) {
                best = candidate
            }
        }
        return best
    }

    private fun isBetterCandidate(candidate: UpdateCandidate, currentBest: UpdateCandidate): Boolean {
        val candidateVersion = candidate.version ?: ""
        val bestVersion = currentBest.version ?: ""
        val comparison = try {
            DlcUpdateService.compareVersions(candidateVersion, bestVersion)
        } catch (_: Exception) {
            candidateVersion.compareTo(bestVersion, ignoreCase = true)
        }
        if (comparison != 0) {
            return comparison > 0
        }
        return isAfterInstant(candidate.fileDate, currentBest.fileDate)
    }

    @JvmStatic
    fun shouldOfferUpdate(currentVersion: String?, candidateVersion: String?, candidateChannel: DlcVersionUtil.Channel?): Boolean {
        if (candidateVersion.isNullOrBlank()) {
            return false
        }
        if (currentVersion.isNullOrBlank()) {
            return true
        }
        val current = currentVersion.trim()
        val candidate = candidateVersion.trim()
        if (candidate.equals(current, ignoreCase = true)) {
            return false
        }
        if (!isSameMajorSeries(current, candidate)) {
            return false
        }
        if (!isChannelAllowed(versionChannel(current), candidateChannel)) {
            return false
        }
        return try {
            DlcUpdateService.compareVersions(current, candidate) < 0
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun isSameMajorSeries(left: String?, right: String?): Boolean {
        val leftMajor = extractMajorVersion(left) ?: return false
        val rightMajor = extractMajorVersion(right) ?: return false
        return leftMajor == rightMajor
    }

    @JvmStatic
    fun versionChannel(version: String?): DlcVersionUtil.Channel {
        return DlcVersionUtil.channelOf(version)
    }

    @JvmStatic
    fun releaseTypeChannel(releaseType: Int): DlcVersionUtil.Channel? {
        return when (releaseType) {
            3 -> DlcVersionUtil.Channel.ALPHA
            2 -> DlcVersionUtil.Channel.BETA
            1 -> DlcVersionUtil.Channel.STABLE
            else -> null
        }
    }

    @JvmStatic
    fun releaseChannelOf(versionType: String?): DlcVersionUtil.Channel? {
        return DlcVersionUtil.channelByName(versionType)
    }

    @JvmStatic
    fun isChannelAllowed(currentChannel: DlcVersionUtil.Channel?, candidateChannel: DlcVersionUtil.Channel?): Boolean {
        val current = currentChannel ?: DlcVersionUtil.Channel.STABLE
        val candidate = candidateChannel ?: DlcVersionUtil.Channel.STABLE
        return current.accepts(candidate)
    }

    private fun isAfterInstant(candidate: String?, current: String?): Boolean {
        val candidateTime = parseInstantOrNull(candidate) ?: return false
        val currentTime = parseInstantOrNull(current) ?: return true
        return candidateTime.isAfter(currentTime)
    }

    private fun parseInstantOrNull(value: String?): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            Instant.parse(value.trim())
        } catch (_: Exception) {
            null
        }
    }

    class UpdateCandidate(
        @JvmField val version: String?,
        @JvmField val channel: DlcVersionUtil.Channel?,
        @JvmField val fileName: String?,
        @JvmField val displayName: String?,
        @JvmField val fileId: String?,
        @JvmField val fileDate: String?,
        @JvmField var url: String?
    ) {
        @JvmField var curseForgeFile: DlcCurseForgeClient.CurseForgeFile? = null

        companion object {
            @JvmStatic
            fun fromCurseForge(file: DlcCurseForgeClient.CurseForgeFile?): UpdateCandidate? {
                if (file == null) {
                    return null
                }
                val version = extractPublishedVersion(file.displayName, file.fileName)
                var channel: DlcVersionUtil.Channel? = releaseTypeChannel(file.releaseType)
                if (channel == null) {
                    channel = versionChannel(version)
                }
                val candidate = UpdateCandidate(version, channel, file.fileName, file.displayName, file.id, file.fileDate, file.url)
                candidate.curseForgeFile = file
                return candidate
            }

            @JvmStatic
            fun fromModrinth(version: DlcModrinthClient.ModrinthVersion?): UpdateCandidate? {
                if (version == null) {
                    return null
                }
                var versionText = version.versionNumber
                if (versionText.isNullOrBlank()) {
                    versionText = extractPublishedVersion(null, version.fileName)
                }
                var channel: DlcVersionUtil.Channel? = releaseChannelOf(version.versionType)
                if (channel == null) {
                    channel = versionChannel(versionText)
                }
                return UpdateCandidate(versionText, channel, version.fileName, versionText, version.versionId, version.datePublished?.toString(), version.url)
            }
        }
    }

    private fun isSameVersion(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) {
            return false
        }
        return try {
            DlcUpdateService.compareVersions(left, right) == 0
        } catch (_: Exception) {
            left.trim().equals(right.trim(), ignoreCase = true)
        }
    }

    private fun extractVersion(displayName: String?, fileName: String?): String? {
        return extractPublishedVersion(displayName, fileName)
    }

    @JvmStatic
    fun extractPublishedVersion(displayName: String?, fileName: String?): String? {
        var candidate: String? = null
        if (fileName != null) {
            candidate = matchPublishedFileName(fileName)
        }
        if (candidate.isNullOrBlank() && displayName != null) {
            candidate = matchDisplayNameVersion(displayName)
        }
        return candidate
    }

    private fun matchDisplayNameVersion(displayName: String?): String? {
        return matchFirstVersionByPattern(displayName, DISPLAY_NAME_VERSION_PATTERN)
    }

    private fun matchPublishedFileName(fileName: String?): String? {
        if (fileName.isNullOrBlank()) {
            return null
        }
        var name = fileName.trim()
        if (name.lowercase(Locale.ROOT).endsWith(".jar")) {
            name = name.substring(0, name.length - 4)
        }
        val tokens = name.split("-")
        for (i in tokens.indices) {
            val token = tokens[i]
            if (!FILE_NAME_VERSION_PATTERN.matcher(token).matches()) {
                continue
            }
            if (i + 1 < tokens.size && FILE_NAME_PRERELEASE_PATTERN.matcher(tokens[i + 1]).matches()) {
                return "$token-${tokens[i + 1]}"
            }
            return token
        }
        return null
    }

    private fun matchFirstVersionByPattern(source: String?, pattern: Pattern): String? {
        if (source.isNullOrBlank()) {
            return null
        }
        val matcher = pattern.matcher(source)
        return if (matcher.find()) matcher.group(1) else null
    }

    class SelfUpdateResult {
        @JvmField var hasUpdate: Boolean = false
        @JvmField var currentVersion: String? = null
        @JvmField var latestVersion: String? = null
        @JvmField var requestedVersion: String? = null
        @JvmField var checkFailed = false
        @JvmField var missingRequestedVersion: Boolean = false
        @JvmField var fileId: String? = null
        @JvmField var fileName: String? = null
        @JvmField var displayName: String? = null
        @JvmField var fileDate: String? = null
        @JvmField var downloadUrl: String? = null

        companion object {
            @JvmStatic
            fun none(currentVersion: String?): SelfUpdateResult {
                val result = SelfUpdateResult()
                result.hasUpdate = false
                result.currentVersion = currentVersion ?: ""
                return result
            }

            @JvmStatic
            fun failed(currentVersion: String?): SelfUpdateResult {
                val result = none(currentVersion)
                result.checkFailed = true
                return result
            }
        }
    }

    class DownloadResult private constructor(
        @JvmField val success: Boolean,
        @JvmField val reason: String,
        @JvmField val file: Path?,
    ) {
        companion object {
            @JvmStatic
            fun success(file: Path?): DownloadResult {
                return DownloadResult(true, "", file)
            }

            @JvmStatic
            fun failed(reason: String): DownloadResult {
                return DownloadResult(false, reason, null)
            }
        }
    }

    class SelfUpdateHistory {
        @JvmField var oldVersion: String? = null
        @JvmField var newVersion: String? = null
        @JvmField var backupFile: String? = null
        @JvmField var oldFileName: String? = null
        @JvmField var newFileName: String? = null
        @JvmField var appliedAt: String? = null
    }

    private class SaveResult private constructor(
        val success: Boolean,
    ) {
        companion object {
            fun success(): SaveResult {
                return SaveResult(true)
            }

            fun failed(): SaveResult {
                return SaveResult(false)
            }
        }
    }
}
