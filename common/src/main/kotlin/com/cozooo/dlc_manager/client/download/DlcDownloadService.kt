package com.cozooo.dlc_manager.client.download

import com.cozooo.dlc_manager.core.DlcPathConfig
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

object DlcDownloadService {
    private const val MEDIAFILEZ_HOST = "mediafilez.forgecdn.net"
    private const val EDGE_FORGECDN_HOST = "edge.forgecdn.net"
    private const val MODRINTH_CDN_HOST = "cdn.modrinth.com"
    private const val MIRROR_HOST = "mod.mcimirror.top"
    private const val MIRROR_COMPARE_WINDOW_MS = 3000L
    private const val MIRROR_DISABLE_FAILURES = 2
    private const val SPEED_WINDOW_MS = 1000L
    private const val BYTES_PER_MIB = 1024L * 1024L
    private const val MIN_MIRROR_SPEED_CHECK_BYTES = BYTES_PER_MIB
    private val mirrorLock = Any()
    private var mirrorComparisons = 0
    private var officialWins = 0
    private var officialFastWins = 0
    private var mirrorFastWins = 0
    private var mirrorWins = 0
    private var mirrorFailures = 0
    private var selectedMirror: Boolean? = null
    private var mirrorDisabled = false

    fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)
    }

    interface StatusProgressListener : ProgressListener {
        fun onStatusKey(statusKey: String)
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
        val mode = DownloadMirrorMode.fromConfig(DlcPathConfig.getDownloadMirrorMode())
        val mirrorPlan = mirrorPlan(url, fileName, total)
        notifyStatus(listener, "selecting_source", mode, mirrorPlan)
        val downloadUrl = primaryDownloadUrl(url, mirrorPlan, mode)
        notifyStatus(listener, "downloading", mode, mirrorPlan)
        val switchPlan = sourceSwitchPlan(url, mirrorPlan, mode, downloadUrl)
        listener?.onProgress(0L, total)
        val rangeProbe = if (total > 0
            && total >= DlcPathConfig.getMinSegmentBytes()
            && DlcPathConfig.getMaxSegmentsPerFile() > 1
        ) {
            probeRangeSupport(downloadUrl)
        } else {
            RangeProbe.unsupported(downloadUrl)
        }
        if (switchPlan != null && rangeProbe.supportsRanges) {
            Files.deleteIfExists(temp)
            try {
                downloadSegmentedSwitchAware(rangeProbe.url, switchPlan, temp, total, listener, cancelChecker)
            } catch (_: SegmentDownloadException) {
                Files.deleteIfExists(temp)
                downloadSingleSwitchAware(rangeProbe.url, switchPlan, temp, total, listener, cancelChecker)
            } catch (ex: IOException) {
                Files.deleteIfExists(temp)
                throw ex
            }
        } else if (rangeProbe.supportsRanges) {
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
        } else if (switchPlan != null && !Files.exists(temp)) {
            downloadSingleSwitchAware(downloadUrl, switchPlan, temp, total, listener, cancelChecker)
        } else {
            val existing = if (Files.exists(temp)) Files.size(temp) else 0L
            downloadSingleWithRetry(downloadUrl, temp, total, existing, listener, cancelChecker)
        }
        if (cancelChecker?.isCanceled() == true) {
            Files.deleteIfExists(temp)
            throw DownloadCanceledException("canceled")
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    private fun mirrorPlan(url: String, fileName: String?, totalBytes: Long): MirrorPlan? {
        if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
            return null
        }
        val mirrorUrl = mirrorDownloadUrl(url) ?: return null
        val speedRule = mirrorSpeedRule(totalBytes)
        return MirrorPlan(mirrorUrl, totalBytes, speedRule.timeoutSpeed, speedRule.bucketSpeed, speedRule.requiredSpeed, totalBytes > MIN_MIRROR_SPEED_CHECK_BYTES)
    }

    private fun primaryDownloadUrl(url: String, mirrorPlan: MirrorPlan?, mode: DownloadMirrorMode): String {
        return when (mode) {
            DownloadMirrorMode.OFFICIAL_ONLY,
            DownloadMirrorMode.OFFICIAL_FIRST -> url
            DownloadMirrorMode.MIRROR_ONLY -> mirrorPlan?.mirrorUrl ?: if (isMirrorUrl(url)) url else throw IOException("mirror download source unavailable")
            DownloadMirrorMode.MIRROR_FIRST -> mirrorPlan?.mirrorUrl ?: if (isMirrorUrl(url)) url else url
            DownloadMirrorMode.SMART_OFFICIAL -> {
                if (mirrorPlan == null) {
                    return url
                }
                synchronized(mirrorLock) {
                    if (mirrorDisabled) {
                        return url
                    }
                    selectedMirror?.let { return if (it) mirrorPlan.mirrorUrl else url }
                }
                url
            }
            DownloadMirrorMode.SMART_MIRROR -> {
                if (mirrorPlan == null) {
                    return url
                }
                synchronized(mirrorLock) {
                    if (mirrorDisabled) {
                        return url
                    }
                    selectedMirror?.let { return if (it) mirrorPlan.mirrorUrl else url }
                }
                mirrorPlan.mirrorUrl
            }
            DownloadMirrorMode.SMART_PRECHECK -> {
                if (mirrorPlan == null) {
                    return url
                }
                var selected = selectedSourceFromCache()
                if (selected == null && mirrorPlan.canSpeedCompare) {
                    selected = precheckDownloadSource(url, mirrorPlan.mirrorUrl)
                }
                if (selected == true) mirrorPlan.mirrorUrl else url
            }
        }
    }

    private fun sourceSwitchPlan(officialUrl: String, mirrorPlan: MirrorPlan?, mode: DownloadMirrorMode, primaryUrl: String): SourceSwitchPlan? {
        if (mirrorPlan == null) {
            return null
        }
        return when (mode) {
            DownloadMirrorMode.SMART_OFFICIAL -> if (primaryUrl == officialUrl && mirrorPlan.canSpeedCompare) {
                SourceSwitchPlan(mirrorPlan.mirrorUrl, primaryIsMirror = false, alternateIsMirror = true, mode = SourceSwitchMode.SPEED_COMPARE, mirrorPlan = mirrorPlan)
            } else null
            DownloadMirrorMode.SMART_MIRROR -> if (primaryUrl == mirrorPlan.mirrorUrl && mirrorPlan.canSpeedCompare) {
                SourceSwitchPlan(officialUrl, primaryIsMirror = true, alternateIsMirror = false, mode = SourceSwitchMode.SPEED_COMPARE, mirrorPlan = mirrorPlan)
            } else null
            DownloadMirrorMode.SMART_PRECHECK -> null
            DownloadMirrorMode.OFFICIAL_FIRST -> if (primaryUrl == officialUrl) {
                SourceSwitchPlan(mirrorPlan.mirrorUrl, primaryIsMirror = false, alternateIsMirror = true, mode = SourceSwitchMode.EARLY_TIMEOUT, mirrorPlan = mirrorPlan)
            } else null
            DownloadMirrorMode.MIRROR_FIRST -> if (primaryUrl == mirrorPlan.mirrorUrl) {
                SourceSwitchPlan(officialUrl, primaryIsMirror = true, alternateIsMirror = false, mode = SourceSwitchMode.EARLY_TIMEOUT, mirrorPlan = mirrorPlan)
            } else null
            DownloadMirrorMode.OFFICIAL_ONLY,
            DownloadMirrorMode.MIRROR_ONLY -> null
        }
    }

    private fun notifyStatus(listener: ProgressListener?, statusKey: String, mode: DownloadMirrorMode, mirrorPlan: MirrorPlan?) {
        if (listener is StatusProgressListener && mode == DownloadMirrorMode.SMART_PRECHECK && mirrorPlan != null && mirrorPlan.canSpeedCompare) {
            listener.onStatusKey(statusKey)
        }
    }

    private fun startSourceProbe(primarySpeed: Double, switchPlan: SourceSwitchPlan, cancelChecker: CancelChecker?): SourceProbe? {
        if (switchPlan.mode == SourceSwitchMode.EARLY_TIMEOUT) {
            return if (primarySpeed >= switchPlan.mirrorPlan.timeoutSpeed) null else SourceProbe.selected(switchPlan)
        }
        if (primarySpeed >= switchPlan.mirrorPlan.requiredSpeed) {
            recordPrimaryFastIfStable(primarySpeed, switchPlan)
            return null
        }
        synchronized(mirrorLock) {
            if (mirrorDisabled && switchPlan.alternateIsMirror) {
                return null
            }
            selectedMirror?.let { return if (it == switchPlan.primaryIsMirror) null else SourceProbe.selected(switchPlan) }
            if (mirrorComparisons >= maxMirrorProbeCount()) {
                return null
            }
            mirrorComparisons++
        }
        val executor = Executors.newSingleThreadExecutor()
        val primarySpeedRef = AtomicReference(primarySpeed)
        return SourceProbe(
            switchPlan.alternateUrl,
            switchPlan.primaryIsMirror,
            switchPlan.alternateIsMirror,
            executor.submit<Double> { probeDownloadSpeed(switchPlan.alternateUrl, MIRROR_COMPARE_WINDOW_MS, cancelChecker, primarySpeedRef) },
            executor,
            primarySpeedRef,
        )
    }

    private fun finishSourceProbe(primarySpeed: Double, probe: SourceProbe): Boolean {
        if (probe.preselected) {
            return true
        }
        val primaryBaseline = probe.primarySpeedRef?.get() ?: primarySpeed
        val alternateSpeed = try {
            probe.future?.get() ?: 0.0
        } catch (_: Exception) {
            0.0
        } finally {
            probe.executor?.shutdownNow()
        }
        synchronized(mirrorLock) {
            recordSourceProbeResult(alternateSpeed, primaryBaseline, probe.alternateIsMirror, probe.primaryIsMirror)
            selectedMirror?.let { return it == probe.alternateIsMirror }
        }
        return alternateSpeed > 0.0 && alternateSpeed > primaryBaseline
    }

    private fun selectedSourceFromCache(): Boolean? {
        synchronized(mirrorLock) {
            if (mirrorDisabled) {
                return false
            }
            return selectedMirror
        }
    }

    private fun precheckDownloadSource(officialUrl: String, mirrorUrl: String): Boolean? {
        val maxCount = maxMirrorProbeCount()
        repeat(maxCount) {
            synchronized(mirrorLock) {
                if (mirrorDisabled) {
                    return false
                }
                selectedMirror?.let { return it }
                mirrorComparisons++
            }
            val result = probeDownloadSpeedPair(officialUrl, mirrorUrl, MIRROR_COMPARE_WINDOW_MS, null)
            synchronized(mirrorLock) {
                recordSourceProbeResult(result.mirrorSpeed, result.officialSpeed, alternateIsMirror = true, primaryIsMirror = false)
                selectedMirror?.let { return it }
            }
        }
        synchronized(mirrorLock) {
            return selectedMirror
        }
    }

    private fun recordSourceProbeResult(alternateSpeed: Double, primarySpeed: Double, alternateIsMirror: Boolean, primaryIsMirror: Boolean) {
        if (alternateSpeed <= 0.0) {
            if (alternateIsMirror) {
                mirrorFailures++
                if (mirrorFailures >= MIRROR_DISABLE_FAILURES) {
                    mirrorDisabled = true
                }
            }
        } else if (alternateSpeed > primarySpeed) {
            if (alternateIsMirror) {
                mirrorWins++
            } else {
                officialWins++
            }
        } else if (primaryIsMirror) {
            mirrorWins++
        } else {
            officialWins++
        }
        val requiredWins = requiredMirrorProbeWins()
        if (mirrorWins >= requiredWins) {
            selectedMirror = true
        } else if (officialWins >= requiredWins) {
            selectedMirror = false
        }
    }

    private fun maxMirrorProbeCount(): Int = maxOf(1, DlcPathConfig.getDownloadMirrorProbeCount())

    private fun requiredMirrorProbeWins(): Int {
        return if (DlcPathConfig.getDownloadMirrorProbeStrategy() == DlcPathConfig.DOWNLOAD_MIRROR_PROBE_SINGLE) {
            1
        } else {
            maxMirrorProbeCount() / 2 + 1
        }
    }

    private fun recordPrimaryFastIfStable(primarySpeed: Double, switchPlan: SourceSwitchPlan) {
        val mirrorPlan = switchPlan.mirrorPlan
        if (mirrorPlan.totalBytes <= 10L * BYTES_PER_MIB || mirrorPlan.bucketSpeed <= mirrorPlan.timeoutSpeed || primarySpeed < mirrorPlan.bucketSpeed) {
            return
        }
        synchronized(mirrorLock) {
            if (selectedMirror != null || mirrorDisabled) {
                return
            }
            if (switchPlan.primaryIsMirror) {
                mirrorFastWins++
                if (mirrorFastWins >= 2) {
                    selectedMirror = true
                }
            } else {
                officialFastWins++
                if (officialFastWins >= 2) {
                    selectedMirror = false
                }
            }
        }
    }

    private fun mirrorSpeedRule(totalBytes: Long): SpeedRule {
        val taskTimeoutSeconds = maxOf(1.0, DlcPathConfig.getTaskTimeoutMs() / 1000.0)
        val timeoutSpeed = totalBytes / taskTimeoutSeconds
        val bucketSpeed = when {
            totalBytes < 10L * BYTES_PER_MIB -> totalBytes / 10.0
            totalBytes <= 20L * BYTES_PER_MIB -> 1.0 * BYTES_PER_MIB
            else -> 2.0 * BYTES_PER_MIB
        }
        return SpeedRule(timeoutSpeed, bucketSpeed, maxOf(timeoutSpeed, bucketSpeed))
    }

    private fun probeDownloadSpeed(
        url: String,
        windowMs: Long,
        cancelChecker: CancelChecker?,
        primarySpeedRef: AtomicReference<Double>,
    ): Double {
        var connection: HttpURLConnection? = null
        return try {
            connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
            connection.readTimeout = windowMs.toInt()
            connection.setRequestProperty("User-Agent", "DLC-Manager")
            connection.setRequestProperty("Range", "bytes=0-")
            connection.connect()
            if (connection.responseCode >= 400) {
                return 0.0
            }
            val start = System.nanoTime()
            val deadline = start + TimeUnit.MILLISECONDS.toNanos(windowMs)
            var bytes = 0L
            var windowStart = start
            var windowBytes = 0L
            var maxSpeed = 0.0
            val buffer = ByteArray(8192)
            BufferedInputStream(connection.inputStream).use { input ->
                while (System.nanoTime() < deadline) {
                    if (cancelChecker?.isCanceled() == true) {
                        throw DownloadCanceledException("canceled")
                    }
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    bytes += read.toLong()
                    windowBytes += read.toLong()
                    val now = System.nanoTime()
                    if (now - windowStart >= TimeUnit.MILLISECONDS.toNanos(SPEED_WINDOW_MS)) {
                        maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                        if (maxSpeed > primarySpeedRef.get()) {
                            return maxSpeed
                        }
                        windowStart = now
                        windowBytes = 0L
                    }
                }
            }
            val elapsed = maxOf(1L, System.nanoTime() - start)
            maxOf(maxSpeed, bytes * 1_000_000_000.0 / elapsed)
        } catch (ex: DownloadCanceledException) {
            throw ex
        } catch (_: Exception) {
            0.0
        } finally {
            closeQuietly(connection)
        }
    }

    private fun probeDownloadSpeedPair(officialUrl: String, mirrorUrl: String, windowMs: Long, cancelChecker: CancelChecker?): ProbePairResult {
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val officialRef = AtomicReference(Double.MAX_VALUE)
            val mirrorRef = AtomicReference(Double.MAX_VALUE)
            val officialFuture = executor.submit<Double> { probeDownloadSpeed(officialUrl, windowMs, cancelChecker, mirrorRef) }
            val mirrorFuture = executor.submit<Double> { probeDownloadSpeed(mirrorUrl, windowMs, cancelChecker, officialRef) }
            ProbePairResult(getProbeSpeed(officialFuture), getProbeSpeed(mirrorFuture))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun getProbeSpeed(future: Future<Double>): Double {
        return try {
            future.get()
        } catch (_: Exception) {
            0.0
        }
    }

    private fun isCurseForgeCdnUrl(url: String?): Boolean {
        return try {
            val host = URI.create(url).host
            host.equals(MEDIAFILEZ_HOST, ignoreCase = true) || host.equals(EDGE_FORGECDN_HOST, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun isMirrorUrl(url: String?): Boolean {
        return try {
            URI.create(url).host.equals(MIRROR_HOST, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun mirrorDownloadUrl(url: String): String? {
        return try {
            val uri = URI.create(url)
            val curseForge = uri.host.equals(MEDIAFILEZ_HOST, ignoreCase = true) || uri.host.equals(EDGE_FORGECDN_HOST, ignoreCase = true)
            val modrinth = uri.host.equals(MODRINTH_CDN_HOST, ignoreCase = true) && isModrinthDownloadPath(uri)
            if (!curseForge && !modrinth) {
                null
            } else {
                URI(uri.scheme ?: "https", uri.userInfo, MIRROR_HOST, uri.port, uri.path, uri.query, uri.fragment).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isModrinthDownloadPath(uri: URI?): Boolean {
        val path = uri?.path
        return path != null && Regex("/data/[^/]+/versions/[^/]+/[^/]+").matches(path)
    }

    @Throws(IOException::class)
    private fun downloadSingleSwitchAware(
        url: String,
        switchPlan: SourceSwitchPlan,
        temp: Path,
        knownTotal: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ) {
        var info: DownloadInfo? = null
        try {
            info = openDownload(url, 0L)
            if (info.responseCode >= 400) {
                throw IOException("http ${info.responseCode}")
            }
            downloadToFileSwitchAware(info.connection, temp, knownTotal, listener, cancelChecker, switchPlan)
        } catch (ex: SwitchDownloadSourceException) {
            closeQuietly(info?.connection)
            Files.deleteIfExists(temp)
            downloadSingleWithRetry(ex.targetUrl, temp, knownTotal, 0L, listener, cancelChecker)
        } catch (ex: DownloadCanceledException) {
            throw ex
        } catch (ex: IOException) {
            if (shouldFallbackAfterTimeout(ex, switchPlan)) {
                closeQuietly(info?.connection)
                Files.deleteIfExists(temp)
                downloadSingleWithRetry(switchPlan.alternateUrl, temp, knownTotal, 0L, listener, cancelChecker)
                return
            }
            val existing = if (Files.exists(temp)) Files.size(temp) else 0L
            downloadSingleWithRetry(url, temp, knownTotal, existing, listener, cancelChecker)
        } finally {
            closeQuietly(info?.connection)
        }
    }

    @Throws(IOException::class)
    private fun downloadToFileSwitchAware(
        connection: HttpURLConnection?,
        target: Path,
        totalBytes: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
        switchPlan: SourceSwitchPlan,
    ) {
        if (connection == null) {
            throw IOException("missing connection")
        }
        val total = if (totalBytes > 0) totalBytes else connection.contentLengthLong
        var read = 0L
        listener?.onProgress(read, total)
        try {
            BufferedInputStream(connection.inputStream).use { input ->
                Files.newOutputStream(
                    target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use { output ->
                    copyToFileWithSourceSwitch(input, output, total, read, listener, cancelChecker, switchPlan).also {
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

    private fun shouldFallbackAfterTimeout(ex: IOException, switchPlan: SourceSwitchPlan): Boolean {
        return switchPlan.mode == SourceSwitchMode.EARLY_TIMEOUT && ex is SocketTimeoutException
    }

    @Throws(IOException::class)
    private fun copyToFileWithSourceSwitch(
        input: BufferedInputStream,
        output: OutputStream,
        total: Long,
        initialRead: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
        switchPlan: SourceSwitchPlan,
    ): Long {
        val buffer = ByteArray(8192)
        var read = initialRead
        val start = System.nanoTime()
        var windowStart = start
        var windowBytes = 0L
        var maxSpeed = 0.0
        var checked = false
        var sourceProbe: SourceProbe? = null
        val compareDeadline = start + TimeUnit.MILLISECONDS.toNanos(MIRROR_COMPARE_WINDOW_MS)
        try {
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
                windowBytes += len.toLong()
                listener?.onProgress(read, total)
                val now = System.nanoTime()
                if (now - windowStart >= TimeUnit.MILLISECONDS.toNanos(SPEED_WINDOW_MS)) {
                    maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                    windowStart = now
                    windowBytes = 0L
                }
                if (!checked && now >= compareDeadline) {
                    checked = true
                    if (windowBytes > 0) {
                        maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                    }
                    sourceProbe = startSourceProbe(maxSpeed, switchPlan, cancelChecker)
                    if (sourceProbe?.preselected == true) {
                        throw SwitchDownloadSourceException(switchPlan.alternateUrl)
                    }
                }
                val probe = sourceProbe
                if (probe != null && probe.future?.isDone == true) {
                    if (windowBytes > 0) {
                        maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                    }
                    if (finishSourceProbe(maxSpeed, probe)) {
                        throw SwitchDownloadSourceException(probe.alternateUrl)
                    }
                    sourceProbe = null
                }
            }
        } finally {
            sourceProbe?.executor?.shutdownNow()
        }
    }

    @Throws(IOException::class)
    private fun downloadSegmentedSwitchAware(
        url: String,
        switchPlan: SourceSwitchPlan,
        tempFile: Path,
        totalBytes: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ) {
        val initial = try {
            downloadInitialRangeForSourceSwitch(url, switchPlan, tempFile, totalBytes, listener, cancelChecker)
        } catch (ex: IOException) {
            if (shouldFallbackAfterTimeout(ex, switchPlan)) {
                Files.deleteIfExists(tempFile)
                downloadSegmented(switchPlan.alternateUrl, tempFile, totalBytes, listener, cancelChecker)
                return
            }
            throw ex
        }
        if (initial.completed) {
            return
        }
        if (initial.switchSource) {
            Files.deleteIfExists(tempFile)
            downloadSegmented(switchPlan.alternateUrl, tempFile, totalBytes, listener, cancelChecker)
            return
        }
        downloadSegmented(url, tempFile, totalBytes, listener, cancelChecker, initial.downloadedBytes)
    }

    @Throws(IOException::class)
    private fun downloadInitialRangeForSourceSwitch(
        url: String,
        switchPlan: SourceSwitchPlan,
        tempFile: Path,
        totalBytes: Long,
        listener: ProgressListener?,
        cancelChecker: CancelChecker?,
    ): InitialRangeResult {
        RandomAccessFile(tempFile.toFile(), "rw").use { raf ->
            raf.setLength(totalBytes)
        }
        var connection: HttpURLConnection? = null
        var read = 0L
        var maxSpeed = 0.0
        try {
            connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = DlcPathConfig.getConnectTimeoutMs()
            connection.readTimeout = DlcPathConfig.getTaskTimeoutMs()
            connection.setRequestProperty("User-Agent", "DLC-Manager")
            connection.setRequestProperty("Range", "bytes=0-")
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("range not supported")
            }
            val start = System.nanoTime()
            var windowStart = start
            var windowBytes = 0L
            var checked = false
            var sourceProbe: SourceProbe? = null
            val compareDeadline = start + TimeUnit.MILLISECONDS.toNanos(MIRROR_COMPARE_WINDOW_MS)
            val buffer = ByteArray(8192)
            try {
                BufferedInputStream(connection.inputStream).use { input ->
                    RandomAccessFile(tempFile.toFile(), "rw").use { raf ->
                        raf.seek(0L)
                        while (true) {
                            val nowBeforeRead = System.nanoTime()
                            if (checked && sourceProbe == null && nowBeforeRead >= compareDeadline) {
                                return InitialRangeResult(read, false, false)
                            }
                            if (cancelChecker?.isCanceled() == true) {
                                throw DownloadCanceledException("canceled")
                            }
                            val len = input.read(buffer)
                            if (len < 0) {
                                return InitialRangeResult(read, true, false)
                            }
                            raf.write(buffer, 0, len)
                            read += len.toLong()
                            windowBytes += len.toLong()
                            listener?.onProgress(read, totalBytes)
                            if (read >= totalBytes) {
                                return InitialRangeResult(read, true, false)
                            }
                            val now = System.nanoTime()
                            if (now - windowStart >= TimeUnit.MILLISECONDS.toNanos(SPEED_WINDOW_MS)) {
                                maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                                windowStart = now
                                windowBytes = 0L
                            }
                            if (!checked && now >= compareDeadline) {
                                checked = true
                                if (windowBytes > 0) {
                                    maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                                }
                                sourceProbe = startSourceProbe(maxSpeed, switchPlan, cancelChecker)
                                if (sourceProbe?.preselected == true) {
                                    return InitialRangeResult(read, false, true)
                                }
                                if (sourceProbe == null) {
                                    return InitialRangeResult(read, false, false)
                                }
                            }
                            val probe = sourceProbe
                            if (probe != null && probe.future?.isDone == true) {
                                if (windowBytes > 0) {
                                    maxSpeed = maxOf(maxSpeed, windowBytes * 1_000_000_000.0 / maxOf(1L, now - windowStart))
                                }
                                return InitialRangeResult(read, false, finishSourceProbe(maxSpeed, probe))
                            }
                        }
                    }
                }
            } finally {
                sourceProbe?.executor?.shutdownNow()
            }
        } finally {
            closeQuietly(connection)
        }
        return InitialRangeResult(read, false, false)
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
        initialDownloadedBytes: Long = 0L,
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
        val initialRead = initialDownloadedBytes.coerceIn(0L, totalBytes)
        val totalRead = AtomicLong(initialRead)
        val maxRetries = maxOf(1, DlcPathConfig.getMaxRetryCount())
        for (i in 0 until segments) {
            val start = i * segmentSize
            val end = if (i == segments - 1) totalBytes - 1 else start + segmentSize - 1
            val segmentTotal = end - start + 1
            futures.add(executor.submit<Unit> {
                var segmentRead = maxOf(0L, minOf(segmentTotal, initialRead - start))
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

    private data class MirrorPlan(
        val mirrorUrl: String,
        val totalBytes: Long,
        val timeoutSpeed: Double,
        val bucketSpeed: Double,
        val requiredSpeed: Double,
        val canSpeedCompare: Boolean,
    )

    private data class SourceSwitchPlan(
        val alternateUrl: String,
        val primaryIsMirror: Boolean,
        val alternateIsMirror: Boolean,
        val mode: SourceSwitchMode,
        val mirrorPlan: MirrorPlan,
    )

    private enum class SourceSwitchMode {
        SPEED_COMPARE,
        EARLY_TIMEOUT,
    }

    private data class SourceProbe(
        val alternateUrl: String,
        val primaryIsMirror: Boolean,
        val alternateIsMirror: Boolean,
        val future: Future<Double>?,
        val executor: ExecutorService?,
        val primarySpeedRef: AtomicReference<Double>? = null,
        val preselected: Boolean = false,
    ) {
        companion object {
            fun selected(switchPlan: SourceSwitchPlan): SourceProbe = SourceProbe(
                switchPlan.alternateUrl,
                switchPlan.primaryIsMirror,
                switchPlan.alternateIsMirror,
                null,
                null,
                null,
                true,
            )
        }
    }

    private enum class DownloadMirrorMode {
        SMART_OFFICIAL,
        SMART_MIRROR,
        SMART_PRECHECK,
        OFFICIAL_ONLY,
        MIRROR_ONLY,
        OFFICIAL_FIRST,
        MIRROR_FIRST;

        companion object {
            fun fromConfig(value: String?): DownloadMirrorMode {
                return when (DlcPathConfig.normalizeDownloadMirrorMode(value)) {
                    DlcPathConfig.DOWNLOAD_MIRROR_SMART_MIRROR -> SMART_MIRROR
                    DlcPathConfig.DOWNLOAD_MIRROR_SMART_PRECHECK -> SMART_PRECHECK
                    DlcPathConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY -> OFFICIAL_ONLY
                    DlcPathConfig.DOWNLOAD_MIRROR_MIRROR_ONLY -> MIRROR_ONLY
                    DlcPathConfig.DOWNLOAD_MIRROR_OFFICIAL_FIRST -> OFFICIAL_FIRST
                    DlcPathConfig.DOWNLOAD_MIRROR_MIRROR_FIRST -> MIRROR_FIRST
                    else -> SMART_OFFICIAL
                }
            }
        }
    }

    private data class SpeedRule(
        val timeoutSpeed: Double,
        val bucketSpeed: Double,
        val requiredSpeed: Double,
    )

    private data class ProbePairResult(
        val officialSpeed: Double,
        val mirrorSpeed: Double,
    )

    private data class InitialRangeResult(
        val downloadedBytes: Long,
        val completed: Boolean,
        val switchSource: Boolean,
    )

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

    private class SwitchDownloadSourceException(val targetUrl: String) : IOException("switch download source")
}
