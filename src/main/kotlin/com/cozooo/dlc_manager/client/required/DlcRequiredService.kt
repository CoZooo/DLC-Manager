package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.download.DlcSharedService
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcFileMatch
import com.cozooo.dlc_manager.core.DlcResourceHistory
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

object DlcRequiredService {
    fun interface ProgressListener : DlcSharedService.ProgressListener

    fun interface CancelChecker : DlcSharedService.CancelChecker

    interface SourceSwitchChecker : DlcSharedService.SourceSwitchChecker<DlcRequiredEntry>

    interface BatchProgressListener : DlcSharedService.BatchProgressListener<DlcRequiredEntry>

    class Status(
        @JvmField val total: Int,
        @JvmField val missingDownloads: Int,
        @JvmField val needsApply: Int,
    )

    class EntryError(
        @JvmField val entry: DlcRequiredEntry?,
        @JvmField val error: Throwable?,
    )

    class BatchResult(
        @JvmField val total: Int,
        @JvmField val success: Int,
        errors: List<EntryError>?,
    ) {
        @JvmField val errors: List<EntryError> = errors?.toList() ?: emptyList()
    }

    @JvmStatic
    fun inspect(entries: List<DlcRequiredEntry>?): Status {
        val normalized = normalizeEntries(entries)
        return Status(normalized.size, collectMissingDownloads(normalized).size, collectNeedsApply(normalized).size)
    }

    @JvmStatic
    fun collectMissingOrApply(entries: List<DlcRequiredEntry>?): List<DlcRequiredEntry> {
        val normalized = normalizeEntries(entries)
        val missing = collectMissingDownloads(normalized)
        val needs = collectNeedsApply(normalized)
        val result = ArrayList<DlcRequiredEntry>()
        for (entry in normalized) {
            if (missing.contains(entry) || needs.contains(entry)) {
                result.add(entry)
            }
        }
        return result
    }

    @JvmStatic
    fun isDownloaded(entry: DlcRequiredEntry?): Boolean {
        val fileName = resolveDownloadFileName(entry) ?: return false
        val required = entry?.resolveRequiredPath(fileName)
        if (required == null || !Files.exists(required)) {
            return false
        }
        DlcRequiredRuntime.ensureFileNameKey(entry, fileName)
        return true
    }

    @JvmStatic
    fun collectMissingDownloads(entries: List<DlcRequiredEntry>?): List<DlcRequiredEntry> {
        val missing = ArrayList<DlcRequiredEntry>()
        normalizeEntries(entries).forEach { entry ->
            if (!isDownloaded(entry)) {
                missing.add(entry)
            }
        }
        return missing
    }

    @JvmStatic
    fun collectNeedsApply(entries: List<DlcRequiredEntry>?): List<DlcRequiredEntry> {
        val needs = ArrayList<DlcRequiredEntry>()
        normalizeEntries(entries).forEach { entry ->
            if (isDownloaded(entry) && DlcRequiredRuntime.entryNeedsApply(entry)) {
                needs.add(entry)
            }
        }
        return needs
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadOne(entry: DlcRequiredEntry?, listener: ProgressListener?, cancelChecker: CancelChecker?): String {
        return downloadOne(entry, listener, cancelChecker, null)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadOne(entry: DlcRequiredEntry?, listener: ProgressListener?, cancelChecker: CancelChecker?, switchChecker: SourceSwitchChecker?): String {
        return downloadOneShared(entry, listener, cancelChecker, switchChecker)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadOneShared(
        entry: DlcRequiredEntry?,
        listener: DlcSharedService.ProgressListener?,
        cancelChecker: DlcSharedService.CancelChecker?,
        switchChecker: DlcSharedService.SourceSwitchChecker<DlcRequiredEntry>?,
    ): String {
        if (entry == null) {
            throw IOException("missing required entry")
        }
        val requiredDir = entry.getDownloadDir() ?: throw IOException("missing required directory")
        val effectiveName = resolveDownloadFileName(entry)
        val target = if (effectiveName == null) null else entry.resolveRequiredPath(effectiveName)
        if (target != null && Files.exists(target)) {
            DlcRequiredRuntime.ensureFileNameKey(entry, effectiveName)
            return effectiveName ?: throw IOException("missing file name")
        }
        Files.createDirectories(requiredDir)
        val resolver = DlcDownloadCandidate.resolver(entry)
        var lastError: IOException? = null
        var attemptId = 0
        var canceled = false
        while (true) {
            val candidates = resolver.next()
            if (candidates.isEmpty()) {
                break
            }
            for (candidate in candidates) {
                val maxRetry = maxOf(1, DlcConfig.getMaxRetryCount())
                for (attempt in 1..maxRetry) {
                    try {
                        DlcSharedService.checkCanceled(cancelChecker)
                        attemptId += 1
                        if (listener is DlcSharedService.AttemptProgressListener) {
                            listener.onAttemptStart(attemptId)
                        }
                        var fileName = chooseFileNameOriginal(entry, candidate)
                        var downloadInfo: DlcSharedService.DownloadInfo? = null
                        val shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName)
                        if (shouldProbe) {
                            downloadInfo = DlcSharedService.openDownload(candidate.url, 0)
                            if (fileName == null) {
                                fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName)
                            }
                            DlcSharedService.closeQuietly(downloadInfo.connection)
                        }
                        if (fileName == null) {
                            fileName = com.cozooo.dlc_manager.client.download.DlcDownloadService.suggestFileName(candidate.url)
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadUtil.stripQuery(entry.getBaseName())
                        }
                        val finalFileName = fileName ?: throw IOException("missing file name")
                        val resolvedTarget = entry.resolveDownloadPath(finalFileName) ?: throw IOException("invalid target path")
                        Files.createDirectories(resolvedTarget.parent)
                        if (Files.exists(resolvedTarget)) {
                            entry.setResolvedFileName(finalFileName)
                            return finalFileName
                        }
                        val resolved = DlcSharedService.downloadCandidate(candidate, resolvedTarget, finalFileName, shouldProbe, listener) {
                            DlcSharedService.isCanceled(cancelChecker) || DlcSharedService.isSwitchRequested(switchChecker, entry)
                        }
                        DlcSharedService.verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker)
                        entry.setResolvedFileName(finalFileName)
                        DlcRequiredRuntime.updateFileNameKey(entry, finalFileName)
                        DlcResourceHistory.downloaded(entry.getDlcRoot()!!, entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), resolved)
                        return finalFileName
                    } catch (ex: com.cozooo.dlc_manager.client.download.DlcDownloadService.DownloadCanceledException) {
                        if (DlcSharedService.isSwitchRequested(switchChecker, entry)) {
                            lastError = DlcSharedService.DownloadSwitchException("switch")
                            DlcSharedService.clearSwitchRequest(switchChecker, entry)
                            DlcSharedService.notifyProgress(listener, 0L, 1L)
                            break
                        }
                        canceled = true
                        throw DlcSharedService.DownloadCanceledException("canceled")
                    } catch (ex: DlcSharedService.DownloadCanceledException) {
                        canceled = true
                        throw ex
                    } catch (ex: IOException) {
                        if (DlcSharedService.isSwitchException(ex)) {
                            lastError = ex
                            DlcSharedService.clearSwitchRequest(switchChecker, entry)
                            DlcSharedService.notifyProgress(listener, 0L, 1L)
                            break
                        }
                        if (ex.cause is DlcSharedService.DownloadCanceledException) {
                            canceled = true
                            throw ex.cause as DlcSharedService.DownloadCanceledException
                        }
                        lastError = ex
                        if (ex is com.cozooo.dlc_manager.client.download.DlcDownloadService.SegmentDownloadException) {
                            DlcSharedService.notifyProgress(listener, 0L, 1L)
                            break
                        }
                        if (attempt >= maxRetry) {
                            DlcSharedService.notifyProgress(listener, 0L, 1L)
                        }
                    }
                }
            }
        }
        if (DlcSharedService.isCanceled(cancelChecker) || canceled) {
            throw DlcSharedService.DownloadCanceledException("canceled")
        }
        if (attemptId > 0) {
            DlcSharedService.notifyProgress(listener, 0L, 1L)
        }
        if (lastError == null) {
            lastError = resolver.lastError()
        }
        throw lastError ?: IOException("missing download url")
    }

    @JvmStatic
    @Throws(InterruptedException::class)
    fun downloadAll(entries: List<DlcRequiredEntry>?, listener: BatchProgressListener?, cancelChecker: CancelChecker?): BatchResult {
        return downloadAll(entries, listener, cancelChecker, null)
    }

    @JvmStatic
    @Throws(InterruptedException::class)
    fun downloadAll(entries: List<DlcRequiredEntry>?, listener: BatchProgressListener?, cancelChecker: CancelChecker?, switchChecker: SourceSwitchChecker?): BatchResult {
        val result = DlcSharedService.downloadAll(entries, listener, cancelChecker, switchChecker, DlcSharedService.EntryDownloader(::downloadOneShared))
        return toBatchResult(result)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun installOne(entry: DlcRequiredEntry?): Boolean {
        if (entry == null) {
            return false
        }
        val fileName = resolveExistingFileName(entry)
        if (fileName.isNullOrBlank()) {
            throw IOException("missing file name")
        }
        entry.setResolvedFileName(fileName)
        val source = entry.resolveRequiredPath(fileName)
        if (source == null || !Files.exists(source)) {
            throw IOException("missing required source")
        }
        var applied = false
        val appliedTargets = entry.resolveAppliedTargets()
        if (appliedTargets.isEmpty()) {
            applied = true
        }
        for (targetDir in appliedTargets) {
            val target = resolveTarget(targetDir, fileName) ?: continue
            Files.createDirectories(target.parent)
            val replace = !DlcFileMatch.matches(source, target, entry.getMatchMode())
            DlcResourceHistory.prepare(entry.getDlcRoot()!!, "required", entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), source, target, replace, entry.getMatchMode())
            if (replace) {
                DlcResourceHistory.replace(source, target)
                applied = true
            }
        }
        if (appliedTargets.isEmpty()) DlcResourceHistory.downloaded(entry.getDlcRoot()!!, entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), source)
        DlcResourceHistory.complete(entry.getDlcRoot()!!, "required", entry.getHistoryKey())
        val id = entry.getIdentifier()
        if (!id.isNullOrBlank()) {
            DlcRequiredRuntimeState.setOverride(id, true)
        }
        return applied
    }

    @JvmStatic @Throws(IOException::class)
    fun reconcileEntries(entries: List<DlcRequiredEntry>) {
        for (root in DlcConfig.getDlcRoots()) reconcileRoot(root, entries)
    }

    @JvmStatic @Throws(IOException::class)
    fun reconcileRoot(root: Path, entries: List<DlcRequiredEntry>) {
        val keys = HashSet<String>()
        val downloads = HashSet<String>()
        for (entry in entries) {
            if (entry.getDlcRoot()?.toAbsolutePath()?.normalize() != root.toAbsolutePath().normalize()) continue
            keys.add(entry.getHistoryKey())
            entry.resolveDownloadPath(resolveExistingFileName(entry))?.let { downloads.add(DlcResourceHistory.relativePath(it)) }
        }
        DlcResourceHistory.reconcileRequired(root, keys, downloads)
    }

    @JvmStatic
    fun resolveExistingFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        var fileName = entry.getResolvedFileName()
        if (isConfigFileName(fileName) || isTempFileName(fileName)) {
            fileName = null
        }
        if (!fileName.isNullOrBlank()) {
            val target = entry.resolveRequiredPath(fileName)
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName
            }
        }
        val base = entry.getBaseName()
        val requiredDir = entry.getRequiredDir()
        if (requiredDir == null || base.isNullOrBlank()) {
            return fileName
        }
        for (directory in listOfNotNull(entry.getDownloadDir(), requiredDir)) {
            if (!Files.isDirectory(directory)) continue
            try {
                Files.newDirectoryStream(directory, "$base.*").use { stream ->
                    for (path in stream) {
                        val name = path.fileName?.toString() ?: continue
                        if (!isConfigFile(entry, path) && !isTempFileName(name) && Files.isRegularFile(path)) return name
                    }
                }
            } catch (_: IOException) {}
        }
        return fileName
    }

    private fun normalizeEntries(entries: List<DlcRequiredEntry?>?): List<DlcRequiredEntry> {
        val normalized = ArrayList<DlcRequiredEntry>()
        entries?.forEach { entry ->
            if (entry == null) {
                return@forEach
            }
            resolveDownloadFileName(entry)
            normalized.add(entry)
        }
        return normalized
    }

    private fun resolveDownloadFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        var fileName = normalizeFileName(entry.getRequiredFileName())
        if (fileName == null) {
            fileName = normalizeFileName(resolveFileNameFromCandidates(entry))
        }
        if (fileName == null) {
            fileName = normalizeFileName(resolveExistingRequiredFile(entry))
        }
        if (isTempFileName(fileName)) {
            fileName = null
        }
        entry.setResolvedFileName(fileName)
        return fileName
    }

    private fun resolveFileNameFromCandidates(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        val resolver = DlcDownloadCandidate.resolver(entry)
        try {
            while (true) {
                val candidates = resolver.next()
                if (candidates.isEmpty()) {
                    break
                }
                for (candidate in candidates) {
                    var name = candidate.fileNameOverride
                    if (!DlcDownloadUtil.isLikelyFileName(name) && candidate.direct) {
                        name = DlcDownloadUtil.extractFileNameFromUrl(candidate.url)
                    }
                    name = DlcDownloadUtil.sanitizeFileName(name)
                    if (DlcDownloadUtil.isLikelyFileName(name)) {
                        return name
                    }
                }
            }
        } catch (_: IOException) {
            if (Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return null
    }

    private fun resolveExistingRequiredFile(entry: DlcRequiredEntry?): String? {
        return resolveExistingFileName(entry)
    }

    private fun chooseFileNameOriginal(entry: DlcRequiredEntry, candidate: DlcDownloadCandidate): String? {
        var fileName = DlcDownloadUtil.sanitizeFileName(entry.getResolvedFileName())
        if (fileName == null && candidate.fileNameOverride != null) {
            fileName = DlcDownloadUtil.sanitizeFileName(candidate.fileNameOverride)
        }
        if (fileName == null) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(candidate.url))
        }
        return fileName
    }

    private fun isConfigFile(entry: DlcRequiredEntry?, path: Path?): Boolean {
        if (entry == null || path == null) {
            return false
        }
        val configPath = entry.getConfigPath()
        if (configPath != null && path == configPath) {
            return true
        }
        val name = path.fileName?.toString() ?: ""
        return isConfigFileName(name)
    }

    private fun resolveTarget(targetDir: Path?, fileName: String?): Path? {
        if (targetDir == null || fileName.isNullOrBlank()) {
            return null
        }
        val lower = targetDir.fileName?.toString()?.lowercase(Locale.ROOT) ?: ""
        return if (lower.endsWith(".jar") || lower.endsWith(".zip")) targetDir else targetDir.resolve(fileName)
    }

    private fun normalizeFileName(fileName: String?): String? {
        var trimmed = DlcDownloadUtil.stripQuery(fileName) ?: return null
        trimmed = trimmed.trim()
        if (trimmed.isEmpty() || DlcDcFormatUtil.isDcName(trimmed)) {
            return null
        }
        return DlcDownloadUtil.sanitizeFileName(trimmed)
    }

    private fun isTempFileName(fileName: String?): Boolean {
        return fileName != null && fileName.lowercase(Locale.ROOT).endsWith(".part")
    }

    private fun isConfigFileName(fileName: String?): Boolean {
        return fileName != null && DlcDcFormatUtil.isDcName(fileName)
    }

    private fun toBatchResult(result: DlcSharedService.BatchResult<DlcRequiredEntry>): BatchResult {
        return BatchResult(result.total, result.success, result.errors.map { EntryError(it.entry, it.error) })
    }
}
