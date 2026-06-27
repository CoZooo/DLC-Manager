package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

object DlcRequiredService {
    fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)

        fun onStatusKey(statusKey: String) {}
    }

    fun interface CancelChecker {
        fun isCanceled(): Boolean
    }

    interface SourceSwitchChecker {
        fun isSwitchRequested(entry: DlcRequiredEntry?): Boolean = false
        fun clearSwitchRequest(entry: DlcRequiredEntry?) {}
    }

    interface BatchProgressListener {
        fun onEntryStart(entry: DlcRequiredEntry?, index: Int, total: Int) {}
        fun onEntryAttemptStart(entry: DlcRequiredEntry?, attemptId: Int, index: Int, total: Int) {}
        fun onEntryProgress(entry: DlcRequiredEntry?, downloadedBytes: Long, totalBytes: Long) {}
        fun onEntryStatus(entry: DlcRequiredEntry?, statusKey: String?, completed: Int, total: Int) {}
        fun onEntrySuccess(entry: DlcRequiredEntry?, fileName: String?, completed: Int, total: Int) {}
        fun onEntryFailure(entry: DlcRequiredEntry?, error: Throwable?, completed: Int, total: Int) {}
        fun onEntryComplete(entry: DlcRequiredEntry?) {}
    }

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
        if (entry == null) {
            throw IOException("missing required entry")
        }
        val requiredDir = entry.getRequiredDir() ?: throw IOException("missing required directory")
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
                        checkCanceled(cancelChecker)
                        attemptId += 1
                        if (listener is AttemptProgressListener) {
                            listener.onAttemptStart(attemptId)
                        }
                        var fileName = chooseFileNameOriginal(entry, candidate)
                        var downloadInfo: DownloadInfo? = null
                        val shouldProbe = candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName)
                        if (shouldProbe) {
                            downloadInfo = openDownload(candidate.url, 0)
                            if (fileName == null) {
                                fileName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName)
                            }
                            closeQuietly(downloadInfo.connection)
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadService.suggestFileName(candidate.url)
                        }
                        if (fileName == null) {
                            fileName = DlcDownloadUtil.stripQuery(entry.getBaseName())
                        }
                        val finalFileName = fileName ?: throw IOException("missing file name")
                        val resolvedTarget = entry.resolveRequiredPath(finalFileName) ?: throw IOException("invalid target path")
                        Files.createDirectories(resolvedTarget.parent)
                        if (Files.exists(resolvedTarget)) {
                            entry.setResolvedFileName(finalFileName)
                            return finalFileName
                        }
                        val progress = if (listener == null) null else toDownloadProgress(listener)
                        val cancel = DlcDownloadService.CancelChecker { isCanceled(cancelChecker) || isSwitchRequested(switchChecker, entry) }
                        val downloaded = if (shouldProbe) {
                            DlcDownloadService.download(candidate.url, resolvedTarget.parent, finalFileName, progress, cancel)
                        } else {
                            DlcDownloadService.downloadKnown(candidate.url, resolvedTarget.parent, finalFileName, candidate.expectedSize, progress, cancel)
                        }
                        val resolved = if (downloaded == resolvedTarget) downloaded else Files.move(downloaded, resolvedTarget, StandardCopyOption.REPLACE_EXISTING)
                        verifyDownloadedFile(resolved, candidate, downloadInfo, cancelChecker)
                        entry.setResolvedFileName(finalFileName)
                        DlcRequiredRuntime.updateFileNameKey(entry, finalFileName)
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
    fun downloadAll(entries: List<DlcRequiredEntry>?, listener: BatchProgressListener?, cancelChecker: CancelChecker?): BatchResult {
        return downloadAll(entries, listener, cancelChecker, null)
    }

    @JvmStatic
    @Throws(InterruptedException::class)
    fun downloadAll(entries: List<DlcRequiredEntry>?, listener: BatchProgressListener?, cancelChecker: CancelChecker?, switchChecker: SourceSwitchChecker?): BatchResult {
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
        if (second.failed.isEmpty()) {
            return BatchResult(total, success, emptyList())
        }
        return BatchResult(total, success, second.errors)
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
            try {
                Files.createDirectories(target.parent)
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    applied = true
                }
            } catch (_: IOException) {
            }
        }
        val id = entry.getIdentifier()
        if (!id.isNullOrBlank()) {
            DlcRequiredRuntime.updateUserRemoved(entry, false)
            DlcRequiredRuntimeState.setOverride(id, true)
        }
        return applied
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
        try {
            Files.newDirectoryStream(requiredDir, "$base.*").use { stream ->
                for (path in stream) {
                    if (isConfigFile(entry, path)) {
                        continue
                    }
                    val name = path.fileName?.toString() ?: ""
                    if (isTempFileName(name)) {
                        continue
                    }
                    return name
                }
            }
        } catch (_: IOException) {
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
        if (entry == null) {
            return null
        }
        val base = entry.getBaseName()
        val requiredDir = entry.getRequiredDir()
        if (requiredDir == null || base.isNullOrBlank()) {
            return null
        }
        try {
            Files.newDirectoryStream(requiredDir, "$base.*").use { stream ->
                for (path in stream) {
                    if (isConfigFile(entry, path)) {
                        continue
                    }
                    val name = path.fileName?.toString() ?: ""
                    if (!DlcDcFormatUtil.isDcName(name) && !isTempFileName(name)) {
                        return name
                    }
                }
            }
        } catch (_: IOException) {
        }
        return null
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

    @Throws(InterruptedException::class)
    private fun runDownloadBatch(
        pending: List<DlcRequiredEntry>,
        total: Int,
        completed: java.util.concurrent.atomic.AtomicInteger,
        listener: BatchProgressListener?,
        cancelChecker: CancelChecker?,
        switchChecker: SourceSwitchChecker?,
    ): BatchRunResult {
        val failed = ArrayList<DlcRequiredEntry>()
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

    private fun isSwitchRequested(switchChecker: SourceSwitchChecker?, entry: DlcRequiredEntry?): Boolean {
        return switchChecker?.isSwitchRequested(entry) == true
    }

    private fun clearSwitchRequest(switchChecker: SourceSwitchChecker?, entry: DlcRequiredEntry?) {
        switchChecker?.clearSwitchRequest(entry)
    }

    private fun isSwitchException(ex: IOException): Boolean {
        return ex is DownloadSwitchException || ex.cause is DownloadSwitchException
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
        private val entry: DlcRequiredEntry?,
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
        val failed: List<DlcRequiredEntry>,
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
        val entry: DlcRequiredEntry?,
        val fileName: String?,
        val error: Throwable?,
        val canceled: Boolean,
    )
}
