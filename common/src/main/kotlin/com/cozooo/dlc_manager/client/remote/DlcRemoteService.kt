package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.download.DlcSharedService
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import java.io.IOException
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.LinkedHashSet

object DlcRemoteService {
    fun interface ProgressListener : DlcSharedService.ProgressListener

    fun interface CancelChecker : DlcSharedService.CancelChecker

    interface SourceSwitchChecker : DlcSharedService.SourceSwitchChecker<DlcRemoteEntry>

    interface BatchProgressListener : DlcSharedService.BatchProgressListener<DlcRemoteEntry>

    class EntryError(
        @JvmField val entry: DlcRemoteEntry?,
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
    fun isMissing(entry: DlcRemoteEntry?): Boolean {
        if (entry == null) {
            return false
        }
        val target = entry.getTargetPath()
        return target == null || !Files.exists(target)
    }

    @JvmStatic
    fun countMissing(entries: List<DlcRemoteEntry>?): Int {
        return entries?.count { isMissing(it) } ?: 0
    }

    @JvmStatic
    fun collectMissing(entries: List<DlcRemoteEntry>?): List<DlcRemoteEntry> {
        return entries?.filter { isMissing(it) } ?: emptyList()
    }

    @JvmStatic
    fun expandMissingWithDependencies(roots: List<DlcRemoteEntry>?): List<DlcRemoteEntry> {
        return expandWithDependencies(roots).filter { isMissing(it) }
    }

    @JvmStatic
    fun expandWithDependencies(roots: List<DlcRemoteEntry>?): List<DlcRemoteEntry> {
        val result = ArrayList<DlcRemoteEntry>()
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<DlcRemoteEntry>()
        roots?.let { queue.addAll(it) }
        while (!queue.isEmpty()) {
            val entry = queue.poll() ?: continue
            val key = entry.getUniqueKey()
            if (key.isNullOrBlank() || !seen.add(key)) {
                continue
            }
            result.add(entry)
            for (dep in entry.getRemoteDependencies()) {
                queue.add(dep)
            }
        }
        return result
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadOne(entry: DlcRemoteEntry?, listener: ProgressListener?, cancelChecker: CancelChecker?): String {
        return downloadOne(entry, listener, cancelChecker, null)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadOne(entry: DlcRemoteEntry?, listener: ProgressListener?, cancelChecker: CancelChecker?, switchChecker: SourceSwitchChecker?): String {
        return downloadOneShared(entry, listener, cancelChecker, switchChecker)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadOneShared(
        entry: DlcRemoteEntry?,
        listener: DlcSharedService.ProgressListener?,
        cancelChecker: DlcSharedService.CancelChecker?,
        switchChecker: DlcSharedService.SourceSwitchChecker<DlcRemoteEntry>?,
    ): String {
        if (entry == null) {
            throw IOException("missing remote entry")
        }
        DlcSharedService.checkCanceled(cancelChecker)
        val resolver = DlcDownloadCandidate.resolver(entry)
        precheckDownloadSources(entry, listener)
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
                        var downloadInfo: DlcSharedService.DownloadInfo? = null
                        var headerName: String? = null
                        val shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(candidate.fileNameOverride)
                        if (shouldProbe) {
                            downloadInfo = DlcSharedService.openDownload(candidate.url, 0)
                            headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName)
                            DlcSharedService.closeQuietly(downloadInfo.connection)
                        }
                        var fileName = candidate.fileNameOverride
                        val fileNameExplicit = entry.isFileNameExplicit() && !fileName.isNullOrBlank()
                        if (fileName.isNullOrBlank()) {
                            fileName = entry.getFileNameHint()
                        }
                        if (!fileNameExplicit) {
                            if (candidate.direct && headerName != null) {
                                fileName = headerName
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName) && headerName != null) {
                                fileName = headerName
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                                fileName = DlcDownloadService.suggestFileName(candidate.url)
                            }
                        }
                        val finalFileName = finalizeFileName(fileName, entry, candidate.url)
                        val target = entry.resolveTargetPath(finalFileName) ?: throw IOException("invalid target path")
                        Files.createDirectories(target.parent)
                        if (Files.exists(target)) {
                            entry.setFileNameHint(finalFileName)
                            DlcRemoteRuntime.updateFileNameKey(entry, finalFileName)
                            return finalFileName
                        }
                        val resolved = DlcSharedService.downloadCandidate(candidate, target, finalFileName, shouldProbe, listener) {
                            DlcSharedService.isCanceled(cancelChecker) || DlcSharedService.isSwitchRequested(switchChecker, entry)
                        }
                        DlcSharedService.verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker)
                        entry.setFileNameHint(finalFileName)
                        DlcRemoteRuntime.updateFileNameKey(entry, finalFileName)
                        return finalFileName
                    } catch (ex: DlcDownloadService.DownloadCanceledException) {
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
                        if (ex is DlcDownloadService.SegmentDownloadException) {
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
    fun downloadAll(entries: List<DlcRemoteEntry>?, listener: BatchProgressListener?, cancelChecker: CancelChecker?): BatchResult {
        return downloadAll(entries, listener, cancelChecker, null)
    }

    @JvmStatic
    @Throws(InterruptedException::class)
    fun downloadAll(entries: List<DlcRemoteEntry>?, listener: BatchProgressListener?, cancelChecker: CancelChecker?, switchChecker: SourceSwitchChecker?): BatchResult {
        val result = DlcSharedService.downloadAll(entries, listener, cancelChecker, switchChecker, DlcSharedService.EntryDownloader(::downloadOneShared))
        return toBatchResult(result)
    }

    private fun precheckDownloadSources(entry: DlcRemoteEntry?, listener: DlcSharedService.ProgressListener?) {
        try {
            val candidates = DlcDownloadCandidate.resolve(entry)
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return
            }
            DlcDownloadService.precheckDownloadSources(candidates, if (listener == null) null else DlcSharedService.toDownloadProgress(listener))
        } catch (_: Exception) {
        }
    }

    private fun hasMissingHostedDownloadCandidate(entry: DlcRemoteEntry?, candidates: List<DlcDownloadCandidate>?): Boolean {
        if (entry == null || candidates == null) {
            return false
        }
        for (candidate in candidates) {
            if (candidate.direct) {
                continue
            }
            val fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url)
            val target = entry.resolveTargetPath(fileName) ?: continue
            if (!Files.exists(target)) {
                return true
            }
        }
        return false
    }

    private fun finalizeFileName(fileName: String?, entry: DlcRemoteEntry, url: String?): String {
        var name = fileName
        if (name.isNullOrBlank()) {
            var fallback = entry.getFileNameHint()
            if (fallback.isNullOrBlank()) {
                fallback = entry.getDlcIdentifier()
            }
            if (fallback.isNullOrBlank()) {
                fallback = "dlc-download"
            }
            name = fallback
        }
        if (!name.contains(".")) {
            val extension = guessExtension(entry)
            if (extension != null) {
                name += extension
            }
        }
        return name
    }

    private fun guessExtension(entry: DlcRemoteEntry?): String? {
        if (entry?.getType() == null) {
            return null
        }
        return when (entry.getType()) {
            DlcType.MOD -> ".jar"
            DlcType.RESOURCE_PACK, DlcType.DATA_PACK, DlcType.TACZ_PACK, DlcType.DLC_PACK, DlcType.UNKNOWN -> ".zip"
            null -> null
        }
    }

    private fun toBatchResult(result: DlcSharedService.BatchResult<DlcRemoteEntry>): BatchResult {
        return BatchResult(result.total, result.success, result.errors.map { EntryError(it.entry, it.error) })
    }
}
