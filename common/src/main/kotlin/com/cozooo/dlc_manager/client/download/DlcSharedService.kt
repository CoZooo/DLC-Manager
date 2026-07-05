package com.cozooo.dlc_manager.client.download

import com.cozooo.dlc_manager.core.DlcConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

object DlcSharedService {
    fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)

        fun onStatusKey(statusKey: String) {}
    }

    interface AttemptProgressListener : ProgressListener {
        fun onAttemptStart(attemptId: Int)
    }

    fun interface CancelChecker {
        fun isCanceled(): Boolean
    }

    interface SourceSwitchChecker<T> {
        fun isSwitchRequested(entry: T?): Boolean = false
        fun clearSwitchRequest(entry: T?) {}
    }

    interface BatchProgressListener<T> {
        fun onEntryStart(entry: T?, index: Int, total: Int) {}
        fun onEntryAttemptStart(entry: T?, attemptId: Int, index: Int, total: Int) {}
        fun onEntryProgress(entry: T?, downloadedBytes: Long, totalBytes: Long) {}
        fun onEntryStatus(entry: T?, statusKey: String?, completed: Int, total: Int) {}
        fun onEntrySuccess(entry: T?, fileName: String?, completed: Int, total: Int) {}
        fun onEntryFailure(entry: T?, error: Throwable?, completed: Int, total: Int) {}
        fun onEntryComplete(entry: T?) {}
    }

    fun interface EntryDownloader<T> {
        @Throws(IOException::class, InterruptedException::class)
        fun downloadOne(
            entry: T,
            listener: ProgressListener?,
            cancelChecker: CancelChecker?,
            switchChecker: SourceSwitchChecker<T>?,
        ): String
    }

    class EntryError<T>(
        @JvmField val entry: T?,
        @JvmField val error: Throwable?,
    )

    class BatchResult<T>(
        @JvmField val total: Int,
        @JvmField val success: Int,
        errors: List<EntryError<T>>?,
    ) {
        @JvmField val errors: List<EntryError<T>> = errors?.toList() ?: emptyList()
    }

    class DownloadInfo(
        @JvmField val connection: HttpURLConnection,
        @JvmField val fileName: String?,
        @JvmField val responseCode: Int,
        @JvmField val totalBytes: Long,
        @JvmField val contentMd5: String?,
    )

    class DownloadSwitchException(message: String?) : IOException(message)

    class DownloadCanceledException(message: String?) : IOException(message)

    @JvmStatic
    @Throws(InterruptedException::class)
    fun <T> downloadAll(
        entries: List<T>?,
        listener: BatchProgressListener<T>?,
        cancelChecker: CancelChecker?,
        switchChecker: SourceSwitchChecker<T>?,
        downloader: EntryDownloader<T>,
    ): BatchResult<T> {
        val targets = entries?.filterNotNull() ?: emptyList()
        val total = targets.size
        if (total == 0) {
            return BatchResult(0, 0, emptyList())
        }
        var success = 0
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val first = runDownloadBatch(targets, total, completed, listener, cancelChecker, switchChecker, downloader)
        success += first.success
        if (isCanceled(cancelChecker)) {
            return BatchResult(total, success, first.errors)
        }
        val second = if (first.failed.isEmpty()) {
            BatchRunResult<T>(emptyList(), emptyList(), 0)
        } else {
            runDownloadBatch(first.failed, total, completed, listener, cancelChecker, switchChecker, downloader)
        }
        success += second.success
        return BatchResult(total, success, if (second.failed.isEmpty()) emptyList() else second.errors)
    }

    @Throws(InterruptedException::class)
    private fun <T> runDownloadBatch(
        pending: List<T>,
        total: Int,
        completed: java.util.concurrent.atomic.AtomicInteger,
        listener: BatchProgressListener<T>?,
        cancelChecker: CancelChecker?,
        switchChecker: SourceSwitchChecker<T>?,
        downloader: EntryDownloader<T>,
    ): BatchRunResult<T> {
        val failed = ArrayList<T>()
        val errors = ArrayList<EntryError<T>>()
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
                    val fileName = downloader.downloadOne(entry, BatchEntryProgress(listener, entry, completed, total), cancelChecker, switchChecker)
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
        val completion = ExecutorCompletionService<DownloadResult<T>>(executor)
        val futures = ArrayList<Future<DownloadResult<T>>>()
        for (entry in pending) {
            futures.add(completion.submit {
                listener?.onEntryStart(entry, completed.get(), total)
                try {
                    val fileName = downloader.downloadOne(entry, BatchEntryProgress(listener, entry, completed, total), cancelChecker, switchChecker)
                    DownloadResult(entry, fileName, null, false)
                } catch (_: DownloadCanceledException) {
                    DownloadResult(entry, null, null, true)
                } catch (t: Throwable) {
                    DownloadResult(entry, null, t, false)
                } finally {
                    listener?.onEntryComplete(entry)
                }
            })
        }
        var success = 0
        try {
            while (success + failed.size < pending.size) {
                if (isCanceled(cancelChecker)) {
                    cancelPending(futures)
                    break
                }
                val future = try {
                    completion.poll(100, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    cancelPending(futures)
                    throw InterruptedException("interrupted")
                } ?: continue
                val result = try {
                    future.get()
                } catch (e: Exception) {
                    DownloadResult(null, null, e, false)
                }
                if (result.canceled) {
                    cancelPending(futures)
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
            cancelPending(futures)
            executor.shutdownNow()
        }
        return BatchRunResult(failed, errors, success)
    }

    @JvmStatic
    fun toDownloadProgress(listener: ProgressListener): DlcDownloadService.ProgressListener {
        return object : DlcDownloadService.StatusProgressListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                listener.onProgress(downloadedBytes, totalBytes)
            }

            override fun onStatusKey(statusKey: String) {
                listener.onStatusKey(statusKey)
            }
        }
    }

    @JvmStatic
    fun notifyProgress(listener: ProgressListener?, downloadedBytes: Long, totalBytes: Long) {
        listener?.onProgress(downloadedBytes, totalBytes)
    }

    @JvmStatic
    @Throws(DownloadCanceledException::class)
    fun checkCanceled(cancelChecker: CancelChecker?) {
        if (isCanceled(cancelChecker)) {
            throw DownloadCanceledException("canceled")
        }
    }

    @JvmStatic
    fun isCanceled(cancelChecker: CancelChecker?): Boolean {
        return cancelChecker?.isCanceled() == true
    }

    @JvmStatic
    fun <T> isSwitchRequested(switchChecker: SourceSwitchChecker<T>?, entry: T?): Boolean {
        return switchChecker?.isSwitchRequested(entry) == true
    }

    @JvmStatic
    fun <T> clearSwitchRequest(switchChecker: SourceSwitchChecker<T>?, entry: T?) {
        switchChecker?.clearSwitchRequest(entry)
    }

    @JvmStatic
    fun isSwitchException(ex: IOException): Boolean {
        return ex is DownloadSwitchException || ex.cause is DownloadSwitchException
    }

    @JvmStatic
    @Throws(IOException::class)
    fun openDownload(url: String, offset: Long): DownloadInfo {
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

    @JvmStatic
    fun closeQuietly(connection: HttpURLConnection?) {
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun downloadCandidate(
        candidate: DlcDownloadCandidate,
        target: Path,
        fileName: String,
        shouldProbe: Boolean,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ): Path {
        val progress = if (listener == null) null else toDownloadProgress(listener)
        val cancel = if (cancelChecker == null) null else DlcDownloadService.CancelChecker { cancelChecker.isCanceled() }
        val downloaded = if (shouldProbe) {
            DlcDownloadService.download(candidate.url, target.parent, fileName, progress, cancel)
        } else {
            DlcDownloadService.downloadKnown(candidate.url, target.parent, fileName, candidate.expectedSize, progress, cancel)
        }
        return if (downloaded == target) downloaded else Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun verifyDownloadedFile(file: Path?, candidate: DlcDownloadCandidate?, info: DownloadInfo?, cancelChecker: CancelChecker?) {
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

    private class BatchEntryProgress<T>(
        private val listener: BatchProgressListener<T>?,
        private val entry: T?,
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

    private class BatchRunResult<T>(
        val failed: List<T>,
        val errors: List<EntryError<T>>,
        val success: Int,
    )

    private class DownloadResult<T>(
        val entry: T?,
        val fileName: String?,
        val error: Throwable?,
        val canceled: Boolean,
    )

    private fun <T> cancelPending(futures: List<Future<DownloadResult<T>>>) {
        for (future in futures) {
            future.cancel(true)
        }
    }
}
