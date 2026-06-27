package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

object DlcRemoteService {
    fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)

        fun onStatusKey(statusKey: String) {}
    }

    fun interface CancelChecker {
        fun isCanceled(): Boolean
    }

    interface SourceSwitchChecker {
        fun isSwitchRequested(entry: DlcRemoteEntry?): Boolean = false
        fun clearSwitchRequest(entry: DlcRemoteEntry?) {}
    }

    interface BatchProgressListener {
        fun onEntryStart(entry: DlcRemoteEntry?, index: Int, total: Int) {}
        fun onEntryAttemptStart(entry: DlcRemoteEntry?, attemptId: Int, index: Int, total: Int) {}
        fun onEntryProgress(entry: DlcRemoteEntry?, downloadedBytes: Long, totalBytes: Long) {}
        fun onEntryStatus(entry: DlcRemoteEntry?, statusKey: String?, completed: Int, total: Int) {}
        fun onEntrySuccess(entry: DlcRemoteEntry?, fileName: String?, completed: Int, total: Int) {}
        fun onEntryFailure(entry: DlcRemoteEntry?, error: Throwable?, completed: Int, total: Int) {}
        fun onEntryComplete(entry: DlcRemoteEntry?) {}
    }

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
        if (entry == null) {
            throw IOException("missing remote entry")
        }
        checkCanceled(cancelChecker)
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
                        checkCanceled(cancelChecker)
                        attemptId += 1
                        if (listener is AttemptProgressListener) {
                            listener.onAttemptStart(attemptId)
                        }
                        var downloadInfo: DownloadInfo? = null
                        var headerName: String? = null
                        val shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(candidate.fileNameOverride)
                        if (shouldProbe) {
                            downloadInfo = openDownload(candidate.url, 0)
                            headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName)
                            closeQuietly(downloadInfo.connection)
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
                        val progress = if (listener == null) null else toDownloadProgress(listener)
                        val cancel = DlcDownloadService.CancelChecker { isCanceled(cancelChecker) || isSwitchRequested(switchChecker, entry) }
                        val downloaded = if (shouldProbe) {
                            DlcDownloadService.download(candidate.url, target.parent, finalFileName, progress, cancel)
                        } else {
                            DlcDownloadService.downloadKnown(candidate.url, target.parent, finalFileName, candidate.expectedSize, progress, cancel)
                        }
                        val resolved = if (downloaded == target) downloaded else Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING)
                        verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker)
                        entry.setFileNameHint(finalFileName)
                        DlcRemoteRuntime.updateFileNameKey(entry, finalFileName)
                        return finalFileName
                    } catch (ex: DlcDownloadService.DownloadCanceledException) {
                        if (isSwitchRequested(switchChecker, entry)) {
                            lastError = DownloadSwitchException("switch")
                            clearSwitchRequest(switchChecker, entry)
                            listener?.onProgress(0L, 1L)
                            break
                        }
                        canceled = true
                        throw DownloadCanceledException("canceled")
                    } catch (ex: DownloadCanceledException) {
                        canceled = true
                        throw ex
                    } catch (ex: IOException) {
                        if (isSwitchException(ex)) {
                            lastError = ex
                            clearSwitchRequest(switchChecker, entry)
                            listener?.onProgress(0L, 1L)
                            break
                        }
                        if (ex.cause is DownloadCanceledException) {
                            canceled = true
                            throw ex.cause as DownloadCanceledException
                        }
                        lastError = ex
                        if (ex is DlcDownloadService.SegmentDownloadException) {
                            listener?.onProgress(0L, 1L)
                            break
                        }
                        if (attempt >= maxRetry) {
                            listener?.onProgress(0L, 1L)
                        }
                    }
                }
            }
        }
        if (isCanceled(cancelChecker) || canceled) {
            throw DownloadCanceledException("canceled")
        }
        if (attemptId > 0) {
            listener?.onProgress(0L, 1L)
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
        val targets = entries?.filterNotNull() ?: emptyList()
        val total = targets.size
        if (total == 0) {
            return BatchResult(0, 0, emptyList())
        }
        var success = 0
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val first = runDownloadBatch(targets, total, completed, listener, cancelChecker, switchChecker)
        success += first.success
        if (isCanceled(cancelChecker)) {
            return BatchResult(total, success, first.errors)
        }
        val second = if (first.failed.isEmpty()) BatchRunResult(emptyList(), emptyList(), 0) else runDownloadBatch(first.failed, total, completed, listener, cancelChecker, switchChecker)
        success += second.success
        return BatchResult(total, success, if (second.failed.isEmpty()) emptyList() else second.errors)
    }

    @Throws(InterruptedException::class)
    private fun runDownloadBatch(
        pending: List<DlcRemoteEntry>,
        total: Int,
        completed: java.util.concurrent.atomic.AtomicInteger,
        listener: BatchProgressListener?,
        cancelChecker: CancelChecker?,
        switchChecker: SourceSwitchChecker?,
    ): BatchRunResult {
        val failed = ArrayList<DlcRemoteEntry>()
        val errors = ArrayList<EntryError>()
        if (pending.isEmpty() || isCanceled(cancelChecker)) {
            return BatchRunResult(failed, errors, 0)
        }
        val maxConcurrentDownloads = maxOf(1, DlcConfig.getMaxConcurrentDownloads())
        if (maxConcurrentDownloads <= 1) {
            var success = 0
            for (entry in pending) {
                if (isCanceled(cancelChecker)) {
                    break
                }
                listener?.onEntryStart(entry, completed.get(), total)
                try {
                    val fileName = downloadOne(entry, BatchEntryProgress(listener, entry, completed, total), cancelChecker, switchChecker)
                    val done = completed.incrementAndGet()
                    success++
                    listener?.onEntrySuccess(entry, fileName, done, total)
                } catch (_: DownloadCanceledException) {
                    break
                } catch (e: Exception) {
                    failed.add(entry)
                    errors.add(EntryError(entry, e))
                    listener?.onEntryFailure(entry, e, completed.get(), total)
                } finally {
                    listener?.onEntryComplete(entry)
                }
            }
            return BatchRunResult(failed, errors, success)
        }
        val executor = Executors.newFixedThreadPool(maxConcurrentDownloads)
        val completion = ExecutorCompletionService<DownloadResult>(executor)
        for (entry in pending) {
            completion.submit {
                listener?.onEntryStart(entry, completed.get(), total)
                try {
                    val fileName = downloadOne(entry, BatchEntryProgress(listener, entry, completed, total), cancelChecker, switchChecker)
                    DownloadResult(entry, fileName, null, false)
                } catch (_: DownloadCanceledException) {
                    DownloadResult(entry, null, null, true)
                } catch (t: Throwable) {
                    DownloadResult(entry, null, t, false)
                } finally {
                    listener?.onEntryComplete(entry)
                }
            }
        }
        var success = 0
        try {
            for (i in pending.indices) {
                if (isCanceled(cancelChecker)) {
                    break
                }
                val result = try {
                    completion.take().get()
                } catch (e: Exception) {
                    DownloadResult(pending[minOf(i, pending.size - 1)], null, e, false)
                }
                if (result.canceled) {
                    break
                } else if (result.error == null) {
                    val done = completed.incrementAndGet()
                    success++
                    listener?.onEntrySuccess(result.entry, result.fileName, done, total)
                } else {
                    result.entry?.let { failed.add(it) }
                    errors.add(EntryError(result.entry, result.error))
                    listener?.onEntryFailure(result.entry, result.error, completed.get(), total)
                }
            }
        } finally {
            executor.shutdownNow()
        }
        return BatchRunResult(failed, errors, success)
    }

    private fun precheckDownloadSources(entry: DlcRemoteEntry?, listener: ProgressListener?) {
        try {
            val candidates = DlcDownloadCandidate.resolve(entry)
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) {
                return
            }
            DlcDownloadService.precheckDownloadSources(candidates, if (listener == null) null else toDownloadProgress(listener))
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

    private fun toDownloadProgress(listener: ProgressListener): DlcDownloadService.ProgressListener {
        return object : DlcDownloadService.StatusProgressListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                listener.onProgress(downloadedBytes, totalBytes)
            }

            override fun onStatusKey(statusKey: String) {
                listener.onStatusKey(statusKey)
            }
        }
    }

    @Throws(DownloadCanceledException::class)
    private fun checkCanceled(cancelChecker: CancelChecker?) {
        if (isCanceled(cancelChecker)) {
            throw DownloadCanceledException("canceled")
        }
    }

    private fun isCanceled(cancelChecker: CancelChecker?): Boolean {
        return cancelChecker?.isCanceled() == true
    }

    private fun isSwitchRequested(switchChecker: SourceSwitchChecker?, entry: DlcRemoteEntry?): Boolean {
        return switchChecker?.isSwitchRequested(entry) == true
    }

    private fun clearSwitchRequest(switchChecker: SourceSwitchChecker?, entry: DlcRemoteEntry?) {
        switchChecker?.clearSwitchRequest(entry)
    }

    private fun isSwitchException(ex: IOException): Boolean {
        return ex is DownloadSwitchException || ex.cause is DownloadSwitchException
    }

    @Throws(IOException::class)
    private fun openDownload(url: String, offset: Long): DownloadInfo {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = maxOf(1, DlcConfig.getConnectTimeoutMs())
        connection.readTimeout = maxOf(1, DlcConfig.getTaskTimeoutMs())
        connection.setRequestProperty("User-Agent", "DLC-Manager")
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }
        connection.connect()
        var fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection)
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.url.toString())
        }
        val responseCode = connection.responseCode
        val contentLength = connection.contentLengthLong
        val totalBytes = if (contentLength > 0) contentLength + maxOf(0L, offset) else -1L
        val contentMd5 = connection.getHeaderField("Content-MD5")
        return DownloadInfo(connection, fileName, responseCode, totalBytes, contentMd5)
    }

    private fun closeQuietly(connection: HttpURLConnection?) {
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
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

    @Throws(IOException::class)
    private fun verifyDownloadedFile(file: Path?, candidate: DlcDownloadCandidate?, info: DownloadInfo?, cancelChecker: CancelChecker?) {
        checkCanceled(cancelChecker)
        if (file == null || candidate == null) {
            return
        }
        if (candidate.hashType != null && !candidate.hashValue.isNullOrBlank()) {
            val actual = DlcDownloadUtil.computeHashHex(file, mapHashAlgorithm(candidate.hashType))
            if (actual == null || !actual.equals(DlcDownloadUtil.normalizeHash(candidate.hashValue), ignoreCase = true)) {
                Files.deleteIfExists(file)
                throw IOException("hash mismatch")
            }
            return
        }
        if (info != null && !info.contentMd5.isNullOrBlank()) {
            val expected = DlcDownloadUtil.normalizeHash(info.contentMd5)
            val actualHex = DlcDownloadUtil.computeHashHex(file, "MD5")
            val actualBase64 = DlcDownloadUtil.computeMd5Base64(file)
            if (actualHex == null || actualBase64 == null ||
                (!expected.equals(actualHex, ignoreCase = true) && !expected.equals(actualBase64, ignoreCase = true))
            ) {
                Files.deleteIfExists(file)
                throw IOException("hash mismatch")
            }
            return
        }
        var expectedSize = candidate.expectedSize
        if (expectedSize <= 0 && info != null && info.totalBytes > 0) {
            expectedSize = info.totalBytes
        }
        if (expectedSize > 0 && Files.size(file) != expectedSize) {
            Files.deleteIfExists(file)
            throw IOException("size mismatch")
        }
    }

    private fun mapHashAlgorithm(hashType: String?): String? {
        return when (hashType?.lowercase(Locale.ROOT)) {
            null -> null
            "sha1", "sha-1" -> "SHA-1"
            "sha256", "sha-256" -> "SHA-256"
            "sha512", "sha-512" -> "SHA-512"
            "md5" -> "MD5"
            else -> hashType
        }
    }

    private interface AttemptProgressListener : ProgressListener {
        fun onAttemptStart(attemptId: Int)
    }

    private class BatchEntryProgress(
        private val listener: BatchProgressListener?,
        private val entry: DlcRemoteEntry?,
        private val completed: java.util.concurrent.atomic.AtomicInteger,
        private val total: Int,
    ) : AttemptProgressListener {
        override fun onAttemptStart(attemptId: Int) {
            listener?.onEntryAttemptStart(entry, attemptId, completed.get(), total)
        }

        override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
            listener?.onEntryProgress(entry, downloadedBytes, totalBytes)
        }

        override fun onStatusKey(statusKey: String) {
            listener?.onEntryStatus(entry, statusKey, completed.get(), total)
        }
    }

    private class BatchRunResult(
        val failed: List<DlcRemoteEntry>,
        val errors: List<EntryError>,
        val success: Int,
    )

    class DownloadSwitchException(message: String?) : IOException(message)

    class DownloadCanceledException(message: String?) : IOException(message)

    private class DownloadInfo(
        val connection: HttpURLConnection,
        val fileName: String?,
        val responseCode: Int,
        val totalBytes: Long,
        val contentMd5: String?,
    )

    private class DownloadResult(
        val entry: DlcRemoteEntry?,
        val fileName: String?,
        val error: Throwable?,
        val canceled: Boolean,
    )
}
