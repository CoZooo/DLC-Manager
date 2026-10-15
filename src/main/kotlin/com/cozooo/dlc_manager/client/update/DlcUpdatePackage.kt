package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile

class DlcUpdatePackage private constructor(
    private val archivePath: Path,
    private val name: String,
    private val manifest: DlcUpdateManifest,
    private val contentRoot: String?,
) {
    enum class CompatibilityAction { ASK, IGNORE, UPDATE_MANAGER, RESUME }
    private var compatibilityAction = CompatibilityAction.ASK
    private var backupWorlds: List<String> = emptyList()
    private var restoreWorldsAfterReapply = false
    fun getBackupWorlds(): List<String> = backupWorlds
    fun setBackupWorlds(value: List<String>) { backupWorlds = value.toList() }
    fun isRestoreWorldsAfterReapply(): Boolean = restoreWorldsAfterReapply
    fun setRestoreWorldsAfterReapply(value: Boolean) { restoreWorldsAfterReapply = value }
    private var allowUninstallPacks = false
    fun getCompatibilityAction(): CompatibilityAction = compatibilityAction
    fun setCompatibilityAction(value: CompatibilityAction) { compatibilityAction = value }
    fun isAllowUninstallPacks(): Boolean = allowUninstallPacks
    fun setAllowUninstallPacks(value: Boolean) { allowUninstallPacks = value }

    private var extractionRoot: Path? = null
    private var stagingRoot: Path? = null

    fun extractTo(dlcRoot: Path?): Path? {
        if (stagingRoot != null) {
            return stagingRoot
        }
        if (dlcRoot == null) {
            return null
        }
        val stagingBase = DlcTmpPaths.resolve(dlcRoot, "_staging") ?: return null
        val target = try {
            DlcZipUtil.createUniqueDir(stagingBase, name)
        } catch (_: java.io.IOException) {
            return null
        }
        try {
            DlcZipUtil.extractZip(archivePath, target)
        } catch (_: java.io.IOException) {
            DlcZipUtil.deleteRecursive(target)
            return null
        }
        extractionRoot = target
        stagingRoot = if (!contentRoot.isNullOrBlank()) {
            val rootPath = target.resolve(contentRoot).normalize()
            if (rootPath.startsWith(target)) rootPath else target
        } else {
            target
        }
        return stagingRoot
    }

    fun cleanupStaging() {
        val root = extractionRoot ?: stagingRoot ?: return
        DlcZipUtil.deleteRecursive(root)
        extractionRoot = null
        stagingRoot = null
    }

    fun getArchivePath(): Path {
        return archivePath
    }

    fun getName(): String {
        return name
    }

    fun getManifest(): DlcUpdateManifest {
        return manifest
    }

    fun getStagingRoot(): Path? {
        return stagingRoot
    }

    private class ManifestInfo(
        val manifest: DlcUpdateManifest?,
        contentRoot: String?,
    ) {
        val contentRoot: String = contentRoot ?: ""
    }

    companion object {
        private val GSON: Gson = GsonBuilder().create()

        @JvmStatic
        fun fromZip(zipPath: Path?): DlcUpdatePackage? {
            if (zipPath == null || !Files.exists(zipPath)) {
                return null
            }
            val info = readManifest(zipPath)
            if (info?.manifest == null) {
                return null
            }
            val fileName = zipPath.fileName?.toString() ?: "update"
            val baseName = if (fileName.endsWith(".zip")) fileName.substring(0, fileName.length - 4) else fileName
            return DlcUpdatePackage(zipPath, baseName, info.manifest, info.contentRoot)
        }

        private fun readManifest(zipPath: Path): ManifestInfo? {
            try {
                ZipFile(zipPath.toFile()).use { zipFile ->
                    var entry = zipFile.getEntry("update.dc")
                    if (entry == null) {
                        entry = zipFile.getEntry("./update.dc")
                    }
                    if (entry == null) {
                        entry = zipFile.getEntry("update.json")
                        if (entry == null) {
                            entry = zipFile.getEntry("./update.json")
                        }
                    }
                    if (entry == null) {
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val candidate = entries.nextElement() ?: continue
                            val name = candidate.name.lowercase(Locale.ROOT)
                            if (name.endsWith("update.dc") || name.endsWith("update.json")) {
                                entry = candidate
                                break
                            }
                        }
                    }
                    if (entry == null) {
                        return null
                    }
                    var entryName = entry.name?.replace('\\', '/') ?: ""
                    if (entryName.startsWith("./")) {
                        entryName = entryName.substring(2)
                    }
                    var root = ""
                    val slash = entryName.lastIndexOf('/')
                    if (slash > 0) {
                        root = entryName.substring(0, slash)
                    }
                    var obj: JsonObject? = null
                    zipFile.getInputStream(entry).use { input ->
                        val text = String(input.readAllBytes(), StandardCharsets.UTF_8)
                        if (entryName.lowercase(Locale.ROOT).endsWith(".dc")) {
                            val trimmed = text.trimStart()
                            obj = if (trimmed.startsWith("{")) {
                                GSON.fromJson(trimmed, JsonObject::class.java)
                            } else {
                                DlcUpdateDcParser.parse(text)
                            }
                        }
                        if (obj == null) {
                            obj = GSON.fromJson(text, JsonObject::class.java)
                        }
                    }
                    val parsed = obj ?: return null
                    val manifest = DlcUpdateManifest.fromJsonObject(parsed)
                    return ManifestInfo(manifest, root)
                }
            } catch (_: Exception) {
                return null
            }
        }


    }
}
