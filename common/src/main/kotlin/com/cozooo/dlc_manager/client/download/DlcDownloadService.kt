package com.cozooo.dlc_manager.client.download

import com.cozooo.dlc_manager.core.DlcPathConfig
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

object DlcDownloadService {
    fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)
    }

    fun interface CancelChecker {
        fun isCanceled(): Boolean
    }

    @JvmStatic
    @Throws(IOException::class)
    fun suggestFileName(url: String?): String? {
        if (url.isNullOrBlank()) {
            throw IOException("empty url")
        }
        val probe = probeDownload(url)
        var fileName = probe.fileNameHint
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(url)
        }
        return DlcDownloadUtil.sanitizeFileName(fileName)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun download(
        url: String?,
        outputDir: Path?,
        desiredFileName: String?,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ): Path {
        if (url.isNullOrBlank()) {
            throw IOException("empty url")
        }
        if (outputDir == null) {
            throw IOException("missing output dir")
        }
        Files.createDirectories(outputDir)
        val probe = probeDownload(url)
        var fileName = desiredFileName
        if (fileName.isNullOrBlank()) {
            fileName = probe.fileNameHint
        }
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(url)
        }
        fileName = DlcDownloadUtil.sanitizeFileName(fileName)
        if (fileName.isNullOrBlank()) {
            fileName = "download.bin"
        }
        val target = outputDir.resolve(fileName)
        val temp = outputDir.resolve("$fileName.part")

        listener?.onProgress(0L, probe.totalBytes)
        if (probe.supportsRanges
            && probe.totalBytes > 0
            && probe.totalBytes >= DlcPathConfig.getMinSegmentBytes()
            && DlcPathConfig.getMaxSegmentsPerFile() > 1
        ) {
            Files.deleteIfExists(temp)
            try {
                downloadSegmented(url, temp, probe.totalBytes, listener, cancelChecker)
            } catch (_: SegmentDownloadException) {
                Files.deleteIfExists(temp)
                downloadSingleWithRetry(url, temp, probe.totalBytes, 0L, listener, cancelChecker)
            } catch (ex: IOException) {
                Files.deleteIfExists(temp)
                throw ex
            }
        } else {
            val existing = if (Files.exists(temp)) Files.size(temp) else 0L
            downloadSingleWithRetry(url, temp, probe.totalBytes, existing, listener, cancelChecker)
        }

        if (cancelChecker?.isCanceled() == true) {
            Files.deleteIfExists(temp)
            throw DownloadCanceledException("canceled")
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadKnown(
        url: String?,
        outputDir: Path?,
        desiredFileName: String?,
        expectedSize: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ): Path {
        if (url.isNullOrBlank()) {
            throw IOException("empty url")
        }
        if (outputDir == null) {
            throw IOException("missing output dir")
        }
        Files.createDirectories(outputDir)
        var fileName = desiredFileName
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(url)
        }
        fileName = DlcDownloadUtil.sanitizeFileName(fileName)
        if (fileName.isNullOrBlank()) {
            fileName = "download.bin"
        }
        val target = outputDir.resolve(fileName)
        val temp = outputDir.resolve("$fileName.part")
        val total = if (expectedSize > 0) expectedSize else -1L
        listener?.onProgress(0L, total)
        val rangeProbe = if (total > 0
            && total >= DlcPathConfig.getMinSegmentBytes()
            && DlcPathConfig.getMaxSegmentsPerFile() > 1
        ) {
            probeRangeSupport(url)
        } else {
            RangeProbe.unsupported(url)
        }
        if (rangeProbe.supportsRanges) {
            Files.deleteIfExists(temp)
            try {
                downloadSegmented(rangeProbe.url, temp, total, listener, cancelChecker)
            } catch (_: SegmentDownloadException) {
                Files.deleteIfExists(temp)
                downloadSingleWithRetry(rangeProbe.url, temp, total, 0L, listener, cancelChecker)
            } catch (ex: IOException) {
                Files.deleteIfExists(temp)
                throw ex
            }
        } else {
            val existing = if (Files.exists(temp)) Files.size(temp) else 0L
            downloadSingleWithRetry(url, temp, total, existing, listener, cancelChecker)
        }
        if (cancelChecker?.isCanceled() == true) {
            Files.deleteIfExists(temp)
            throw DownloadCanceledException("canceled")
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    @Throws(IOException::class)
    private fun downloadSingleWithRetry(
        url: String,
        temp: Path,
        knownTotal: Long,
        existing: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ) {
        val maxRetry = maxOf(1, DlcPathConfig.getMaxRetryCount())
        var lastError: IOException? = null
        var offset = maxOf(0L, existing)
        for (attempt in 1..maxRetry) {
            if (cancelChecker?.isCanceled() == true) {
                throw DownloadCanceledException("canceled")
            }
            var info: DownloadInfo? = null
            try {
                info = openDownload(url, offset)
                if (info.responseCode >= 400) {
                    throw IOException("http ${info.responseCode}")
                }
                var total = if (knownTotal > 0) knownTotal else info.totalBytes
                if (offset > 0 && info.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    offset = 0L
                    Files.deleteIfExists(temp)
                    closeQuietly(info.connection)
                    info = openDownload(url, 0L)
                    if (info.responseCode >= 400) {
                        throw IOException("http ${info.responseCode}")
                    }
                    total = if (knownTotal > 0) knownTotal else info.totalBytes
                }
                downloadToFile(info.connection, temp, offset, total, listener, cancelChecker)
                return
            } catch (ex: DownloadCanceledException) {
                throw ex
            } catch (ex: IOException) {
                lastError = ex
                offset = if (Files.exists(temp)) Files.size(temp) else 0L
            } finally {
                if (info != null) {
                    closeQuietly(info.connection)
                }
            }
        }
        throw lastError ?: IOException("download failed")
    }

    @Throws(IOException::class)
    private fun openDownload(url: String, offset: Long): DownloadInfo {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
        connection.readTimeout = DlcPathConfig.getTaskTimeoutMs()
        connection.setRequestProperty("User-Agent", "DLC-Manager")
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }
        connection.connect()
        val code = connection.responseCode
        val contentLength = connection.contentLengthLong
        val total = if (contentLength > 0) contentLength + maxOf(0L, offset) else -1L
        var fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection)
        if (fileName.isNullOrBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.url.toString())
        }
        return DownloadInfo(connection, fileName, code, total)
    }

    @Throws(IOException::class)
    private fun downloadToFile(
        connection: HttpURLConnection?,
        target: Path,
        offset: Long,
        totalBytes: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ) {
        if (connection == null) {
            throw IOException("missing connection")
        }
        val total = if (totalBytes > 0) totalBytes else connection.contentLengthLong
        var read = offset
        listener?.onProgress(read, total)
        try {
            BufferedInputStream(connection.inputStream).use { input ->
                val options = if (offset > 0) {
                    arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
                } else {
                    arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                }
                Files.newOutputStream(target, *options).use { output ->
                    copyToFile(input, output, total, read, listener, cancelChecker).also {
                        read = it
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (total > 0 && read < total) {
            throw IOException("download incomplete")
        }
    }

    @Throws(IOException::class)
    private fun copyToFile(
        input: BufferedInputStream,
        output: OutputStream,
        total: Long,
        initialRead: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ): Long {
        val buffer = ByteArray(8192)
        var read = initialRead
        while (true) {
            val len = input.read(buffer)
            if (len < 0) {
                return read
            }
            if (cancelChecker?.isCanceled() == true) {
                throw DownloadCanceledException("canceled")
            }
            output.write(buffer, 0, len)
            read += len.toLong()
            listener?.onProgress(read, total)
        }
    }

    @Throws(IOException::class)
    private fun probeDownload(url: String): DownloadProbe {
        var total = -1L
        var supportsRange = false
        var fileName: String? = null
        var head: HttpURLConnection? = null
        try {
            head = URI.create(url).toURL().openConnection() as HttpURLConnection
            head.instanceFollowRedirects = true
            head.requestMethod = "HEAD"
            head.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
            head.readTimeout = DlcPathConfig.getTaskTimeoutMs()
            head.setRequestProperty("User-Agent", "DLC-Manager")
            head.connect()
            fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(head)
            if (fileName.isNullOrBlank()) {
                fileName = DlcDownloadUtil.extractFileNameFromUrl(head.url.toString())
            }
            val len = head.contentLengthLong
            if (len > 0) {
                total = len
            }
            val acceptRanges = head.getHeaderField("Accept-Ranges")
            supportsRange = acceptRanges != null && acceptRanges.lowercase(Locale.ROOT).contains("bytes")
        } catch (_: Exception) {
        } finally {
            closeQuietly(head)
        }
        if (supportsRange && total > 0) {
            return DownloadProbe(total, true, fileName)
        }
        var range: HttpURLConnection? = null
        try {
            range = URI.create(url).toURL().openConnection() as HttpURLConnection
            range.instanceFollowRedirects = true
            range.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
            range.readTimeout = DlcPathConfig.getTaskTimeoutMs()
            range.setRequestProperty("User-Agent", "DLC-Manager")
            range.setRequestProperty("Range", "bytes=0-0")
            range.connect()
            val code = range.responseCode
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                supportsRange = true
                val contentRange = range.getHeaderField("Content-Range")
                if (contentRange != null && contentRange.contains("/")) {
                    val parts = contentRange.split("/")
                    val last = parts[parts.size - 1]
                    try {
                        val parsed = last.toLong()
                        if (parsed > 0) {
                            total = parsed
                        }
                    } catch (_: NumberFormatException) {
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            closeQuietly(range)
        }
        return DownloadProbe(total, supportsRange, fileName)
    }

    private fun probeRangeSupport(url: String): RangeProbe {
        val resolved = resolveRedirectTarget(url, 0)
        return probeFinalRangeSupport(resolved)
    }

    private fun resolveRedirectTarget(url: String, redirects: Int): String {
        if (redirects > 8 || url.isBlank()) {
            return url
        }
        var connection: HttpURLConnection? = null
        try {
            connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
            connection.readTimeout = DlcPathConfig.getTaskTimeoutMs()
            connection.setRequestProperty("User-Agent", "DLC-Manager")
            connection.connect()
            val code = connection.responseCode
            if (!isRedirect(code)) {
                return url
            }
            val location = connection.getHeaderField("Location")
            val redirected = resolveRedirectUrl(url, location)
            closeQuietly(connection)
            connection = null
            return resolveRedirectTarget(redirected, redirects + 1)
        } catch (_: Exception) {
            return url
        } finally {
            closeQuietly(connection)
        }
    }

    private fun probeFinalRangeSupport(url: String): RangeProbe {
        if (url.isBlank()) {
            return RangeProbe.unsupported(url)
        }
        var range: HttpURLConnection? = null
        try {
            range = URI.create(url).toURL().openConnection() as HttpURLConnection
            range.instanceFollowRedirects = false
            range.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
            range.readTimeout = DlcPathConfig.getTaskTimeoutMs()
            range.setRequestProperty("User-Agent", "DLC-Manager")
            range.setRequestProperty("Range", "bytes=0-0")
            range.connect()
            val code = range.responseCode
            return if (code == HttpURLConnection.HTTP_PARTIAL) {
                RangeProbe.supported(url)
            } else {
                RangeProbe.unsupported(url)
            }
        } catch (_: Exception) {
            return RangeProbe.unsupported(url)
        } finally {
            closeQuietly(range)
        }
    }

    private fun isRedirect(code: Int): Boolean {
        return code == HttpURLConnection.HTTP_MOVED_PERM
            || code == HttpURLConnection.HTTP_MOVED_TEMP
            || code == HttpURLConnection.HTTP_SEE_OTHER
            || code == 307
            || code == 308
    }

    private fun resolveRedirectUrl(baseUrl: String, location: String?): String {
        if (location.isNullOrBlank()) {
            return baseUrl
        }
        return try {
            URI.create(baseUrl).resolve(location).toString()
        } catch (_: IllegalArgumentException) {
            location
        }
    }

    @Throws(IOException::class)
    private fun downloadSegmented(
        url: String,
        tempFile: Path,
        totalBytes: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ) {
        val maxSegments = maxOf(1, DlcPathConfig.getMaxSegmentsPerFile())
        val segments = maxOf(
            1L,
            minOf(maxSegments.toLong(), maxOf(1L, totalBytes / maxOf(1L, DlcPathConfig.getMinSegmentBytes()))),
        ).toInt()
        val segmentSize = maxOf(1L, totalBytes / segments)
        RandomAccessFile(tempFile.toFile(), "rw").use { raf ->
            raf.setLength(totalBytes)
        }
        val executor = Executors.newFixedThreadPool(segments)
        val futures = ArrayList<Future<*>>()
        val totalRead = AtomicLong(0L)
        val maxRetries = maxOf(1, DlcPathConfig.getMaxRetryCount())
        for (i in 0 until segments) {
            val start = i * segmentSize
            val end = if (i == segments - 1) totalBytes - 1 else start + segmentSize - 1
            val segmentTotal = end - start + 1
            futures.add(executor.submit<Unit> {
                var segmentRead = 0L
                var attempts = 0
                var lastError: IOException? = null
                while (segmentRead < segmentTotal) {
                    attempts++
                    if (attempts > maxRetries) {
                        throw SegmentDownloadException("segment retry exceeded", lastError)
                    }
                    if (cancelChecker?.isCanceled() == true) {
                        throw DownloadCanceledException("canceled")
                    }
                    var connection: HttpURLConnection? = null
                    try {
                        connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                        connection.instanceFollowRedirects = true
                        connection.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
                        connection.readTimeout = DlcPathConfig.getTaskTimeoutMs()
                        connection.setRequestProperty("User-Agent", "DLC-Manager")
                        val rangeStart = start + segmentRead
                        connection.setRequestProperty("Range", "bytes=$rangeStart-$end")
                        connection.connect()
                        val code = connection.responseCode
                        if (code != HttpURLConnection.HTTP_PARTIAL) {
                            throw IOException("range not supported")
                        }
                        BufferedInputStream(connection.inputStream).use { input ->
                            RandomAccessFile(tempFile.toFile(), "rw").use { raf ->
                                raf.seek(rangeStart)
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val len = input.read(buffer)
                                    if (len < 0) {
                                        break
                                    }
                                    if (cancelChecker?.isCanceled() == true) {
                                        throw DownloadCanceledException("canceled")
                                    }
                                    raf.write(buffer, 0, len)
                                    segmentRead += len.toLong()
                                    listener?.onProgress(totalRead.addAndGet(len.toLong()), totalBytes)
                                }
                            }
                        }
                    } catch (ex: IOException) {
                        lastError = ex
                        continue
                    } finally {
                        closeQuietly(connection)
                    }
                }
            })
        }
        executor.shutdown()
        for (future in futures) {
            try {
                future.get()
            } catch (ex: Exception) {
                for (pending in futures) {
                    pending.cancel(true)
                }
                val cause = ex.cause
                if (cause is DownloadCanceledException) {
                    throw cause
                }
                if (cause is SegmentDownloadException) {
                    throw cause
                }
                throw SegmentDownloadException("segment failed", ex)
            }
        }
    }

    private fun closeQuietly(connection: HttpURLConnection?) {
        connection?.disconnect()
    }

    private data class DownloadInfo(
        val connection: HttpURLConnection,
        val fileNameHint: String?,
        val responseCode: Int,
        val totalBytes: Long,
    )

    private data class DownloadProbe(
        val totalBytes: Long,
        val supportsRanges: Boolean,
        val fileNameHint: String?,
    )

    private data class RangeProbe(
        val supportsRanges: Boolean,
        val url: String,
    ) {
        companion object {
            fun supported(url: String): RangeProbe = RangeProbe(true, url)

            fun unsupported(url: String): RangeProbe = RangeProbe(false, url)
        }
    }

    class DownloadCanceledException(message: String?) : IOException(message)

    class SegmentDownloadException(message: String?, cause: Throwable?) : IOException(message, cause)
}
