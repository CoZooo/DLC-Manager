package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.download.CurseForgeClient
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

object DlcPackCFResolver {
    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun resolveManifestTargets(pack: DlcPackPackage?): List<DlcPackInstallPlan.FileEntry> {
        if (pack == null || pack.format != DlcPackPackage.Format.CURSEFORGE || pack.manifestFiles.isEmpty()) {
            return emptyList()
        }
        val entries = ArrayList<DlcPackInstallPlan.FileEntry>()
        for (ref in pack.manifestFiles) {
            val target = resolveManifestTarget(pack, ref)
            if (target != null && entries.none { it.relativePath == target.relativePath }) {
                entries.add(DlcPackInstallPlan.FileEntry(pack.manifestFile ?: pack.root, target.relativePath))
            }
        }
        return entries
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadManifestFiles(
        pack: DlcPackPackage?,
        staging: Path,
        packDir: Path,
        cancelChecker: DlcDownloadService.CancelChecker?,
    ): List<DlcPackInstallPlan.FileEntry> {
        return downloadManifestFiles(pack, staging, packDir, cancelChecker, null)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadManifestFiles(
        pack: DlcPackPackage?,
        staging: Path,
        packDir: Path,
        cancelChecker: DlcDownloadService.CancelChecker?,
        progressListener: DlcPackInstaller.ProgressListener?,
    ): List<DlcPackInstallPlan.FileEntry> {
        if (pack == null || pack.format != DlcPackPackage.Format.CURSEFORGE || pack.manifestFiles.isEmpty()) {
            return emptyList()
        }
        val downloadRoot = staging.resolve("manifest")
        val cacheRoot = packDir.resolve("cf_cache")
        val entries = ArrayList<DlcPackInstallPlan.FileEntry>()
        val totalRefs = pack.manifestFiles.size
        for ((index, ref) in pack.manifestFiles.withIndex()) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw DlcDownloadService.DownloadCanceledException("canceled")
            }
            val target = resolveManifestTarget(pack, ref) ?: continue
            if (hasDownloadedEntry(entries, target.relativePath)) {
                continue
            }
            val file = target.file
            val fileName = target.fileName
            val targetDirectory = target.targetDirectory
            val relative = target.relativePath
            val candidate = DlcDownloadCandidate.curseForge(file)
            val cached = cacheFile(cacheRoot, targetDirectory, ref, fileName)
            if (!isUsableCachedFile(cached, candidate)) {
                Files.deleteIfExists(cached)
                val outputDir = downloadRoot.resolve(targetDirectory).resolve(safeSegment(ref.projectId)).resolve(safeSegment(ref.fileId))
                progressListener?.onProgress("Downloading $fileName", index / totalRefs.toFloat())
                val downloaded = DlcDownloadService.downloadKnown(
                    candidate.url,
                    outputDir,
                    fileName,
                    candidate.expectedSize,
                    DlcDownloadService.ProgressListener { done, total ->
                        val fileProgress = if (total > 0) done / total.toFloat() else 0f
                        progressListener?.onProgress("Downloading $fileName", (index + fileProgress.coerceIn(0f, 1f)) / totalRefs.toFloat())
                    },
                    cancelChecker,
                )
                verifyDownloaded(downloaded, candidate)
                Files.createDirectories(cached.parent)
                Files.move(downloaded, cached, StandardCopyOption.REPLACE_EXISTING)
            }
            entries.add(DlcPackInstallPlan.FileEntry(cached, relative))
            progressListener?.onProgress("Resolved $fileName", (index + 1) / totalRefs.toFloat())
        }
        return entries
    }

    private fun resolveManifestTarget(pack: DlcPackPackage, ref: DlcPackManifest.FileRef): ManifestTarget? {
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
        val targetDirectory = targetDirectory(file, mod)
        val relative = targetDirectory.resolve(fileName).toString().replace('\\', '/')
        if (hasBundledOverride(pack, relative)) {
            return null
        }
        return ManifestTarget(file, targetDirectory, fileName, relative)
    }

    private fun targetDirectory(file: CurseForgeClient.CurseForgeFile?, mod: CurseForgeClient.CurseForgeMod?): Path {
        return Path.of(resolveTargetDirectoryName(file, mod))
    }

    @JvmStatic
    fun resolveTargetDirectoryName(file: CurseForgeClient.CurseForgeFile?, mod: CurseForgeClient.CurseForgeMod?): String {
        var type = normalize(file?.classSlug)
        if (type == null) {
            type = normalize(mod?.classSlug)
        }
        if (type != null) {
            if (type.contains("resource") || type.contains("texture")) {
                return "resourcepacks"
            }
            if (type.contains("data-pack") || type.contains("datapack") || type.contains("world")) {
                return "datapacks"
            }
        }
        if (mod != null) {
            for (slug in mod.categorySlugs) {
                val normalized = normalize(slug) ?: continue
                if (normalized.contains("resource") || normalized.contains("texture")) {
                    return "resourcepacks"
                }
                if (normalized.contains("data-pack") || normalized.contains("datapack") || normalized.contains("world")) {
                    return "datapacks"
                }
            }
        }
        return "mods"
    }

    private fun hasBundledOverride(pack: DlcPackPackage?, relative: String?): Boolean {
        val normalized = DlcPackMeta.normalizeRelativePath(relative) ?: return false
        return pack != null && Files.exists(pack.installRoot.resolve(normalized))
    }

    private fun hasDownloadedEntry(entries: List<DlcPackInstallPlan.FileEntry>, relative: String): Boolean {
        return entries.any { it.relativePath == relative }
    }

    private fun cacheFile(cacheRoot: Path, targetDirectory: Path, ref: DlcPackManifest.FileRef, fileName: String): Path {
        return cacheRoot
            .resolve(targetDirectory)
            .resolve(safeSegment(ref.projectId))
            .resolve(safeSegment(ref.fileId))
            .resolve(fileName)
    }

    private fun safeSegment(value: String?): String {
        val sanitized = DlcDownloadUtil.sanitizeFileName(value)
        return if (sanitized.isNullOrBlank()) "_" else sanitized
    }

    private fun isUsableCachedFile(path: Path, candidate: DlcDownloadCandidate): Boolean {
        if (!Files.isRegularFile(path)) {
            return false
        }
        return try {
            verifyDownloaded(path, candidate)
            true
        } catch (_: IOException) {
            Files.deleteIfExists(path)
            false
        }
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

    private class ManifestTarget(
        val file: CurseForgeClient.CurseForgeFile,
        val targetDirectory: Path,
        val fileName: String,
        val relativePath: String,
    )
}
