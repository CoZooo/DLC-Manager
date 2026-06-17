package com.cozooo.dlc_manager.client.selfupdate

import com.cozooo.dlc_manager.client.download.CurseForgeClient
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateSkipStore
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DcFormatUtil
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
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
import java.util.regex.Pattern

object DlcSelfUpdateService {
    const val MOD_ID: String = "dlc_manager"
    const val PROJECT_ID: String = "1424546"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val VERSION_PATTERN_3: Pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)")
    private val VERSION_PATTERN_2: Pattern = Pattern.compile("(\\d+\\.\\d+)")

    @JvmStatic
    fun checkForUpdate(): SelfUpdateResult {
        return checkForUpdate(null)
    }

    @JvmStatic
    fun checkForUpdate(targetVersion: String?): SelfUpdateResult {
        val loader = CrossPlatformRuntime.loaderName()
        val mcVersion = CrossPlatformRuntime.mcVersion()
        val currentVersion = CrossPlatformRuntime.modVersion(MOD_ID)
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
        return try {
            val file = if (requestedVersion == null) {
                CurseForgeClient.fetchLatestFile(PROJECT_ID, mcVersion, loader)
            } else {
                CurseForgeClient.fetchFileByIdOrVersion(PROJECT_ID, requestedVersion, mcVersion, loader)
            }
            if (requestedVersion != null && file == null) {
                val result = SelfUpdateResult.none(currentVersion)
                result.requestedVersion = requestedVersion
                result.missingRequestedVersion = true
                return result
            }
            if (file == null || file.url.isNullOrBlank()) {
                return SelfUpdateResult.none(currentVersion)
            }
            val latestVersion = extractVersion(file.displayName, file.fileName)
            val hasUpdate = if (requestedVersion == null) {
                isUpdateAvailable(currentVersion, latestVersion, file)
            } else {
                !isSameVersion(currentVersion, if (latestVersion.isNullOrBlank()) requestedVersion else latestVersion)
            }
            val result = SelfUpdateResult()
            result.currentVersion = currentVersion ?: ""
            result.latestVersion = if (latestVersion.isNullOrBlank()) requestedVersion ?: "" else latestVersion
            result.downloadUrl = file.url
            result.fileName = file.fileName
            result.fileId = file.id
            result.displayName = file.displayName
            result.fileDate = file.fileDate
            result.hasUpdate = hasUpdate
            result.requestedVersion = requestedVersion
            result
        } catch (_: Exception) {
            SelfUpdateResult.none(currentVersion)
        }
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
        val dlcRoot = DlcPaths.dlcRoot() ?: return DownloadResult.failed("dlc_root_missing")
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
        val modFilePath = CrossPlatformRuntime.modFilePath(MOD_ID)
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
            DcConfigIO.read(marker, DcConfigIO.Layout.GENERIC)
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
            val dlcRoot = DlcPaths.dlcRoot() ?: return false
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
        val backup = Path.of(history.backupFile)
        if (!Files.exists(backup)) {
            return false
        }
        val current = CrossPlatformRuntime.modFilePath(MOD_ID)
        if (current == null || !Files.exists(current)) {
            return false
        }
        val dlcRoot = DlcPaths.dlcRoot() ?: return false
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
            val root = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC)
            val history = if (root == null) null else GSON.fromJson(root, SelfUpdateHistory::class.java)
            if (history != null && !history.backupFile.isNullOrBlank()) {
                val dlcRoot = DlcPaths.dlcRoot()
                if (!Path.of(history.backupFile).isAbsolute) {
                    history.backupFile = dlcRoot.resolve(history.backupFile).toString()
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
        val dlcRoot = DlcPaths.dlcRoot() ?: throw IOException("dlc_root_missing")
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
            DcConfigIO.write(path, root, DcConfigIO.Layout.GENERIC)
        } catch (_: Exception) {
        }
    }

    private fun getHistoryPath(): Path? {
        val dlcRoot = DlcPaths.dlcRoot() ?: return null
        val dir = DlcTmpPaths.resolve(dlcRoot, "selfupdate") ?: return null
        return DcFormatUtil.resolveConfigPath(dir, "self_update_history")
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
            DcConfigIO.write(marker, obj, DcConfigIO.Layout.GENERIC)
            SaveResult.success()
        } catch (_: IOException) {
            SaveResult.failed()
        }
    }

    private fun resolveStoredPath(value: String?): Path {
        val raw = value?.trim().orEmpty()
        val path = Path.of(raw)
        return if (path.isAbsolute) {
            path
        } else {
            CrossPlatformRuntime.gameDir().resolve(path).normalize()
        }
    }

    private fun toStoredPath(path: Path): String {
        val gameDir = CrossPlatformRuntime.gameDir().toAbsolutePath().normalize()
        val normalized = path.toAbsolutePath().normalize()
        return try {
            gameDir.relativize(normalized).toString().replace('\\', '/')
        } catch (_: IllegalArgumentException) {
            normalized.fileName.toString()
        }
    }

    private fun getMarkerPath(): Path? {
        val configDir = CrossPlatformRuntime.configDir() ?: return null
        val dir = configDir.resolve("dlc_manager")
        return DcFormatUtil.resolveConfigPath(dir, "self_update")
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

    private fun isUpdateAvailable(currentVersion: String?, latestVersion: String?, file: CurseForgeClient.CurseForgeFile): Boolean {
        if (currentVersion.isNullOrBlank()) {
            return true
        }
        val current = currentVersion.trim()
        if (!latestVersion.isNullOrBlank()) {
            if (latestVersion.equals(current, ignoreCase = true)) {
                return false
            }
            try {
                return DlcUpdateService.compareVersions(current, latestVersion) < 0
            } catch (_: Exception) {
            }
        }
        val fileName = file.fileName ?: ""
        val display = file.displayName ?: ""
        if (fileName.contains(current) || display.contains(current)) {
            return false
        }
        return true
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
        var candidate: String? = null
        if (fileName != null) {
            candidate = matchVersion(fileName)
        }
        if (candidate.isNullOrBlank() && displayName != null) {
            candidate = matchVersion(displayName)
        }
        return candidate
    }

    private fun matchVersion(source: String?): String? {
        val last = matchVersionByPattern(source, VERSION_PATTERN_3)
        if (!last.isNullOrBlank()) {
            return last
        }
        return matchVersionByPattern(source, VERSION_PATTERN_2)
    }

    private fun matchVersionByPattern(source: String?, pattern: Pattern): String? {
        if (source.isNullOrBlank()) {
            return null
        }
        val matcher = pattern.matcher(source)
        var last: String? = null
        while (matcher.find()) {
            last = matcher.group(1)
        }
        return last
    }

    class SelfUpdateResult {
        @JvmField var hasUpdate: Boolean = false
        @JvmField var currentVersion: String? = null
        @JvmField var latestVersion: String? = null
        @JvmField var requestedVersion: String? = null
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
