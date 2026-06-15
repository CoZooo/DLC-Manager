package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.download.CurseForgeClient
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object DlcPackCFResolver {
    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadManifestFiles(
        pack: DlcPackPackage?,
        staging: Path,
        cancelChecker: DlcDownloadService.CancelChecker?,
    ): List<DlcPackInstallPlan.FileEntry> {
        if (pack == null || pack.format != DlcPackPackage.Format.CURSEFORGE || pack.manifestFiles.isEmpty()) {
            return emptyList()
        }
        val downloadRoot = staging.resolve("manifest")
        val entries = ArrayList<DlcPackInstallPlan.FileEntry>()
        for (ref in pack.manifestFiles) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw DlcDownloadService.DownloadCanceledException("canceled")
            }
            val file = CurseForgeClient.fetchFile(ref.projectId, ref.fileId)
                ?: throw IOException("Failed to resolve CurseForge file: ${ref.projectId}/${ref.fileId}")
            val mod = CurseForgeClient.fetchMod(ref.projectId)
            if (file.url.isNullOrBlank()) {
                throw IOException("Failed to resolve CurseForge file: ${ref.projectId}/${ref.fileId}")
            }
            var fileName = DlcDownloadUtil.sanitizeFileName(file.fileName)
            if (fileName.isNullOrBlank()) {
                fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(file.url))
            }
            if (fileName.isNullOrBlank()) {
                throw IOException("CurseForge file name is not available: ${ref.projectId}/${ref.fileId}")
            }
            val relative = targetDirectory(file, mod).resolve(fileName).toString().replace('\\', '/')
            if (hasBundledOverride(pack, relative) || hasDownloadedEntry(entries, relative)) {
                continue
            }
            val outputDir = downloadRoot.resolve(targetDirectory(file, mod))
            val candidate = DlcDownloadCandidate.curseForge(file)
            val downloaded = DlcDownloadService.downloadKnown(
                candidate.url,
                outputDir,
                fileName,
                candidate.expectedSize,
                null,
                cancelChecker,
            )
            verifyDownloaded(downloaded, candidate)
            entries.add(DlcPackInstallPlan.FileEntry(downloaded, relative))
        }
        return entries
    }

    private fun targetDirectory(file: CurseForgeClient.CurseForgeFile?, mod: CurseForgeClient.CurseForgeMod?): Path {
        var type = normalize(file?.classSlug)
        if (type == null) {
            type = normalize(mod?.classSlug)
        }
        if (type != null) {
            if (type.contains("resource") || type.contains("texture")) {
                return Path.of("resourcepacks")
            }
            if (type.contains("data-pack") || type.contains("datapack") || type.contains("world")) {
                return Path.of("datapacks")
            }
        }
        if (mod != null) {
            for (slug in mod.categorySlugs) {
                val normalized = normalize(slug) ?: continue
                if (normalized.contains("resource") || normalized.contains("texture")) {
                    return Path.of("resourcepacks")
                }
                if (normalized.contains("data-pack") || normalized.contains("datapack") || normalized.contains("world")) {
                    return Path.of("datapacks")
                }
            }
        }
        return Path.of("mods")
    }

    private fun hasBundledOverride(pack: DlcPackPackage?, relative: String?): Boolean {
        val normalized = DlcPackMeta.normalizeRelativePath(relative) ?: return false
        return pack != null && Files.exists(pack.installRoot.resolve(normalized))
    }

    private fun hasDownloadedEntry(entries: List<DlcPackInstallPlan.FileEntry>, relative: String): Boolean {
        return entries.any { it.relativePath == relative }
    }

    private fun verifyDownloaded(path: Path, candidate: DlcDownloadCandidate) {
        if (candidate.hashType != null && !candidate.hashValue.isNullOrBlank()) {
            val actual = DlcDownloadUtil.computeHashHex(path, mapHashAlgorithm(candidate.hashType))
            if (actual == null || !actual.equals(DlcDownloadUtil.normalizeHash(candidate.hashValue), ignoreCase = true)) {
                Files.deleteIfExists(path)
                throw IOException("hash mismatch")
            }
            return
        }
        if (candidate.expectedSize > 0 && Files.size(path) != candidate.expectedSize) {
            Files.deleteIfExists(path)
            throw IOException("size mismatch")
        }
    }

    private fun mapHashAlgorithm(hashType: String?): String? {
        if (hashType == null) {
            return null
        }
        return when (hashType.lowercase(Locale.ROOT)) {
            "sha1", "sha-1" -> "SHA-1"
            "sha512", "sha-512" -> "SHA-512"
            "md5" -> "MD5"
            else -> hashType
        }
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    }
}
