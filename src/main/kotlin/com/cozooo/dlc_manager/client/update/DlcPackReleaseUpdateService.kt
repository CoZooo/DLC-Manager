package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.DlcUpdateMessage
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.StringReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

object DlcPackReleaseUpdateService {
    private val SSH_REPO_PATTERN = Pattern.compile("^git@([^:]+):(.+?)(?:\\.git)?/?$", Pattern.CASE_INSENSITIVE)
    private const val DEFAULT_VERSION = "1.0.0"
    private const val UNIVERSAL_PREFIX = "Universal_"
    private val VERSION_PAIR_DELIMITERS = arrayOf("_", "+", "@", "~", " ")
    private const val RELEASES_PER_PAGE = 100
    private const val RELEASES_MAX_PAGES = 10
    private const val CNB_API_HOST = "api.cnb.cool"
    private const val CNB_TOKEN_SHIFT = 7
    private const val CNB_TOKEN_MASK: Byte = 0x2D
    private val CNB_TOKEN_PART1 = byteArrayOf(
        0xF3.toByte(), 0xFA.toByte(), 0xE3.toByte(), 0x9B.toByte(), 0xB9.toByte(), 0x0B, 0xD0.toByte(), 0xAA.toByte(), 0xA0.toByte(),
        0xEE.toByte(), 0x1D, 0x95.toByte(), 0x91.toByte(), 0xDB.toByte(), 0x86.toByte(), 0x88.toByte(), 0x88.toByte(), 0x3A,
        0x88.toByte(), 0xA7.toByte(), 0xC1.toByte(), 0x2B, 0x30, 0x4B, 0x47, 0xF6.toByte(), 0x2E,
    )
    private const val CNB_TOKEN_PART2_SEED = 0x59
    private const val GITCODE_TOKEN_SHIFT = 5
    private const val GITCODE_TOKEN_MASK: Byte = 0x33
    private val GITCODE_TOKEN_PART1 = byteArrayOf(
        0x60.toByte(), 0x80.toByte(), 0x30.toByte(), 0x33.toByte(), 0xE9.toByte(), 0x58.toByte(), 0x6A.toByte(), 0xEB.toByte(),
        0x34.toByte(), 0x7D.toByte(), 0x53.toByte(), 0x17.toByte(), 0xD0.toByte(), 0x69.toByte(), 0xC6.toByte(), 0x88.toByte(),
        0xE6.toByte(), 0xC5.toByte(), 0xA7.toByte(), 0x16.toByte(), 0x34.toByte(), 0xF9.toByte(), 0x9F.toByte(), 0x47.toByte(),
    )
    private const val GITCODE_TOKEN_PART2_SEED = 0x6D
    private var gitcodeTokenCache: String? = null

    enum class Side(private val assetPrefix: String) {
        CLIENT("Client_"),
        SERVER("Server_");

        fun assetPrefix(): String = assetPrefix
    }

    fun interface ProgressListener {
        fun onProgress(stepIndex: Int, stepCount: Int, fileName: String?, downloaded: Long, total: Long, overallProgress: Float)
    }

    fun interface CancelChecker {
        fun isCanceled(): Boolean
    }

    fun interface ServerPackageImporter {
        @Throws(Exception::class)
        fun importPackage(assetUrl: String?, assetName: String?): Boolean
    }

    class CheckResult {
        @JvmField var modpackUrl: String? = null
        @JvmField var repoUrl: String? = null
        @JvmField var currentBase: String? = null
        @JvmField var currentDlc: String? = null
        @JvmField var targetBase: String? = null
        @JvmField var targetDlc: String? = null
        @JvmField var downloadBase: String? = null
        @JvmField var downloadDlc: String? = null
        @JvmField var fullSteps: List<ReleaseStep> = listOf()
        @JvmField var steps: List<ReleaseStep> = listOf()
        @JvmField var error: String? = null

        fun hasUpdate(): Boolean {
            return compareState(currentBase, currentDlc, targetBase, targetDlc) < 0
        }

        fun canDownload(): Boolean {
            if (compareState(currentBase, currentDlc, downloadBase, downloadDlc) >= 0) return false
            for (step in steps) {
                if (step.assetUrl.isNullOrBlank()) return false
            }
            return true
        }

        fun hasPartialDownload(): Boolean {
            return hasUpdate() &&
                compareState(currentBase, currentDlc, downloadBase, downloadDlc) < 0 &&
                compareState(downloadBase, downloadDlc, targetBase, targetDlc) < 0
        }

        fun hasLatestGap(): Boolean {
            return hasUpdate() && compareState(downloadBase, downloadDlc, targetBase, targetDlc) < 0
        }

        fun isLatestFullyDownloadable(): Boolean {
            return hasUpdate() && compareState(downloadBase, downloadDlc, targetBase, targetDlc) == 0
        }

        fun resolveCombinedChangelog(lang: String?): String {
            if (steps.isEmpty()) return ""
            val builder = StringBuilder()
            for (step in steps) {
                val line = step.resolveChangelog(lang)
                if (line.isBlank()) continue
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder
                    .append(formatCompactTransition(step.oldBase, step.oldDlc, step.newBase, step.newDlc))
                    .append("\n")
                    .append(line.trim())
            }
            return builder.toString()
        }
    }

    private class RepoQueryResult(
        val error: String?,
        val validRepo: Boolean,
        fullTransitions: Map<String, List<ReleaseStep>>?,
        downloadableTransitions: Map<String, List<ReleaseStep>>?,
    ) {
        val fullTransitions: Map<String, List<ReleaseStep>> = fullTransitions ?: mapOf()
        val downloadableTransitions: Map<String, List<ReleaseStep>> = downloadableTransitions ?: mapOf()
    }

    class ReleaseStep {
        @JvmField var tag: String? = null
        @JvmField var oldBase: String? = null
        @JvmField var oldDlc: String? = null
        @JvmField var newBase: String? = null
        @JvmField var newDlc: String? = null
        @JvmField var assetName: String? = null
        @JvmField var assetUrl: String? = null
        @JvmField var repoIndex: Int = Int.MAX_VALUE
        @JvmField var repoUrl: String = ""
        @JvmField var assetCandidates: List<AssetCandidate?> = listOf()
        @JvmField var rawChangelog: String = ""
        @JvmField var changelog: Map<String, String> = mapOf()

        fun resolveChangelog(lang: String?): String {
            if (rawChangelog.isNotBlank()) return rawChangelog
            if (changelog.isEmpty()) return ""
            val key = normalizeLangKey(lang)
            if (changelog.containsKey(key)) return changelog[key] ?: ""
            if (changelog.containsKey("en_us")) return changelog["en_us"] ?: ""
            return changelog.values.firstOrNull() ?: ""
        }
    }

    class AssetCandidate(repoIndex: Int, repoUrl: String?, assetName: String?, assetUrl: String?) {
        val repoIndex: Int = repoIndex
        val repoUrl: String = normalizeString(repoUrl)
        val assetName: String = normalizeString(assetName)
        val assetUrl: String = normalizeString(assetUrl)
    }

    class DownloadResult {
        @JvmField var success: Boolean = false
        @JvmField var canceled: Boolean = false
        @JvmField var message: String? = null
        @JvmField var files: List<Path> = listOf()

        companion object {
            @JvmStatic
            fun success(files: List<Path>?): DownloadResult {
                val result = DownloadResult()
                result.success = true
                result.files = files ?: listOf()
                return result
            }

            @JvmStatic
            fun canceled(): DownloadResult {
                val result = DownloadResult()
                result.canceled = true
                result.message = "canceled"
                return result
            }

            @JvmStatic
            fun failed(message: String?): DownloadResult {
                val result = DownloadResult()
                result.message = if (message.isNullOrBlank()) "failed" else message
                return result
            }
        }
    }

    @JvmStatic
    fun toCheckFailureMessage(error: String?): String {
        return toCheckFailureUpdateMessage(error).asPlainString()
    }

    @JvmStatic
    fun toCheckFailureUpdateMessage(error: String?): DlcUpdateMessage {
        if (isTimeoutFailureMessage(error)) {
            return DlcUpdateMessage.translatable(
                "dlc_manager.pack_update.check_failed",
                DlcUpdateMessage.translatable("dlc_manager.pack_update.check_timeout"),
            )
        }
        val safeError = if (error.isNullOrBlank()) "failed" else error
        return DlcUpdateMessage.translatable("dlc_manager.pack_update.check_failed", safeError)
    }

    @JvmStatic
    fun toDownloadFailureMessage(error: String?): String {
        return toDownloadFailureUpdateMessage(error).asPlainString()
    }

    @JvmStatic
    fun toDownloadFailureUpdateMessage(error: String?): DlcUpdateMessage {
        if (isTimeoutFailureMessage(error)) {
            return DlcUpdateMessage.translatable(
                "dlc_manager.pack_update.download_failed",
                DlcUpdateMessage.translatable("dlc_manager.pack_update.download_timeout"),
            )
        }
        val safeError = if (error.isNullOrBlank()) "failed" else error
        return DlcUpdateMessage.translatable("dlc_manager.pack_update.download_failed", safeError)
    }

    @JvmStatic
    fun checkForUpdates(modpackUrl: String?, githubRepoUrl: String?, currentBase: String?, currentDlc: String?, side: Side?): CheckResult {
        val result = CheckResult()
        result.modpackUrl = normalizeString(modpackUrl)
        result.repoUrl = normalizeString(githubRepoUrl)
        result.currentBase = safeVersion(currentBase)
        result.currentDlc = safeVersion(currentDlc)
        result.targetBase = result.currentBase
        result.targetDlc = result.currentDlc
        result.downloadBase = result.currentBase
        result.downloadDlc = result.currentDlc
        if (side == null || result.repoUrl!!.isBlank()) return result
        val repoUrls = splitRepoUrls(result.repoUrl)
        if (repoUrls.isEmpty()) return result
        val futures = ArrayList<CompletableFuture<RepoQueryResult?>>()
        for (i in repoUrls.indices) {
            val repoUrl = repoUrls[i]
            val repoIndex = i
            futures.add(CompletableFuture.supplyAsync { querySingleRepo(result, repoUrl, repoIndex, side) })
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        var lastError: String? = null
        var hasValidRepo = false
        val fullTransitions = HashMap<String, MutableList<ReleaseStep>>()
        val downloadableTransitions = HashMap<String, MutableList<ReleaseStep>>()
        for (future in futures) {
            val single = future.join() ?: continue
            if (!single.error.isNullOrBlank()) {
                lastError = single.error
                continue
            }
            if (!single.validRepo) continue
            hasValidRepo = true
            mergeTransitions(fullTransitions, single.fullTransitions, false)
            mergeTransitions(downloadableTransitions, single.downloadableTransitions, true)
        }
        if (hasValidRepo) {
            val fullChain = buildUpdateChain(result.currentBase, result.currentDlc, fullTransitions)
            val downloadableChain = buildUpdateChain(result.currentBase, result.currentDlc, downloadableTransitions)
            result.fullSteps = fullChain
            result.steps = downloadableChain
            if (fullChain.isNotEmpty()) {
                val tail = fullChain[fullChain.size - 1]
                result.targetBase = safeVersion(tail.newBase)
                result.targetDlc = safeVersion(tail.newDlc)
            }
            if (downloadableChain.isNotEmpty()) {
                val tail = downloadableChain[downloadableChain.size - 1]
                result.downloadBase = safeVersion(tail.newBase)
                result.downloadDlc = safeVersion(tail.newDlc)
            }
            return result
        }
        result.error = if (lastError.isNullOrBlank()) "invalid_repo_url" else lastError
        return result
    }

    private fun querySingleRepo(base: CheckResult?, repoUrl: String?, repoIndex: Int, side: Side): RepoQueryResult? {
        if (base == null || repoUrl.isNullOrBlank()) return null
        val normalizedRepoUrl = normalizeString(repoUrl)
        val repo = parseRepoCoordinates(repoUrl)
            ?: return RepoQueryResult("invalid_repo_url", false, mapOf(), mapOf())
        val releases = try {
            fetchReleases(repo, safeVersion(base.currentBase), safeVersion(base.currentDlc))
        } catch (ex: Exception) {
            return RepoQueryResult(ex.message ?: "release_fetch_failed", true, mapOf(), mapOf())
        }
        if (releases.isEmpty) return RepoQueryResult(null, true, mapOf(), mapOf())
        val fullTransitions = HashMap<String, MutableList<ReleaseStep>>()
        val downloadableTransitions = HashMap<String, MutableList<ReleaseStep>>()
        for (element in releases) {
            if (element == null || !element.isJsonObject) continue
            val release = element.asJsonObject
            if (release.has("draft") && release["draft"].isJsonPrimitive && release["draft"].asBoolean) continue
            var tag = getString(release, "tag_name")
            if (tag.isBlank()) tag = getString(release, "name")
            val parsed = parseTag(tag) ?: continue
            val candidate = ReleaseStep()
            candidate.tag = tag
            candidate.oldBase = parsed.oldBase
            candidate.oldDlc = parsed.oldDlc
            candidate.newBase = parsed.newBase
            candidate.newDlc = parsed.newDlc
            candidate.repoIndex = repoIndex
            candidate.repoUrl = normalizedRepoUrl
            val asset = resolvePreferredAsset(release, side, parsed.newBase, parsed.newDlc)
            candidate.assetName = asset.name
            candidate.assetUrl = asset.url
            candidate.assetCandidates = if (!candidate.assetUrl.isNullOrBlank()) {
                arrayListOf(AssetCandidate(repoIndex, normalizedRepoUrl, candidate.assetName, candidate.assetUrl))
            } else {
                arrayListOf()
            }
            candidate.rawChangelog = resolveReleaseChangelog(release)
            addTransition(fullTransitions, candidate, false)
            addTransition(downloadableTransitions, candidate, true)
        }
        return RepoQueryResult(null, true, fullTransitions, downloadableTransitions)
    }

    private fun mergeTransitions(merged: MutableMap<String, MutableList<ReleaseStep>>, incoming: Map<String, List<ReleaseStep>>, requireDownloadable: Boolean) {
        if (incoming.isEmpty()) return
        for ((_, candidates) in incoming) {
            if (candidates.isEmpty()) continue
            for (candidate in candidates) addTransition(merged, candidate, requireDownloadable)
        }
    }

    private fun addTransition(transitions: MutableMap<String, MutableList<ReleaseStep>>, candidate: ReleaseStep?, requireDownloadable: Boolean) {
        if (candidate == null) return
        if (requireDownloadable && candidate.assetUrl.isNullOrBlank()) return
        val key = stateKey(candidate.oldBase, candidate.oldDlc)
        val candidates = transitions.computeIfAbsent(key) { ArrayList() }
        for (existing in candidates) {
            if (sameTransition(existing, candidate)) {
                mergeAssetCandidates(existing, candidate)
                if (shouldReplace(existing, candidate)) copyTransitionMetadata(existing, candidate)
                return
            }
        }
        candidates.add(candidate)
    }

    private fun buildUpdateChain(currentBase: String?, currentDlc: String?, transitions: Map<String, List<ReleaseStep>>?): List<ReleaseStep> {
        if (transitions.isNullOrEmpty()) return listOf()
        val currentKey = stateKey(currentBase, currentDlc)
        val visited = HashSet<String>()
        visited.add(currentKey)
        return buildBestUpdateChain(currentKey, transitions, visited)
    }

    private fun buildBestUpdateChain(currentKey: String, transitions: Map<String, List<ReleaseStep>>, visited: MutableSet<String>): List<ReleaseStep> {
        val candidates = transitions[currentKey]
        if (candidates.isNullOrEmpty()) return listOf()
        var best: List<ReleaseStep> = listOf()
        for (candidate in candidates) {
            val nextKey = stateKey(candidate.newBase, candidate.newDlc)
            if (nextKey == currentKey || visited.contains(nextKey)) continue
            visited.add(nextKey)
            val tail = buildBestUpdateChain(nextKey, transitions, visited)
            visited.remove(nextKey)
            val chain = ArrayList<ReleaseStep>()
            chain.add(candidate)
            chain.addAll(tail)
            if (isBetterChain(chain, best)) best = chain
        }
        return best
    }

    private fun isBetterChain(candidate: List<ReleaseStep>?, current: List<ReleaseStep>?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        if (current.isNullOrEmpty()) return true
        val candidateTail = candidate[candidate.size - 1]
        val currentTail = current[current.size - 1]
        val compare = compareState(candidateTail.newBase, candidateTail.newDlc, currentTail.newBase, currentTail.newDlc)
        if (compare != 0) return compare > 0
        val lengthCompare = candidate.size.compareTo(current.size)
        if (lengthCompare != 0) return lengthCompare > 0
        val count = min(candidate.size, current.size)
        for (i in 0 until count) {
            val left = candidate[i]
            val right = current[i]
            val targetCompare = compareState(left.newBase, left.newDlc, right.newBase, right.newDlc)
            if (targetCompare != 0) return targetCompare > 0
            val repoCompare = left.repoIndex.compareTo(right.repoIndex)
            if (repoCompare != 0) return repoCompare < 0
        }
        return false
    }

    private fun splitRepoUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return listOf()
        val urls = ArrayList<String>()
        for (part in raw.split(',')) {
            val trimmed = part.trim()
            if (trimmed.isNotBlank()) urls.add(trimmed)
        }
        return urls
    }

    @JvmStatic
    fun skipPromptForUpdate(result: CheckResult?, side: Side?) {
        val span = resolvePromptSpan(result, side) ?: return
        try {
            var root = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE)
            if (root == null) root = JsonObject()
            val section = JsonObject()
            section.addProperty("repoUrl", span.repoUrl)
            section.addProperty("fromBase", span.fromBase)
            section.addProperty("fromDlc", span.fromDlc)
            section.addProperty("toBase", span.toBase)
            section.addProperty("toDlc", span.toDlc)
            section.addProperty("skippedAt", Instant.now().toString())
            root.add(span.sideKey, section)
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE, root)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun isPromptSkippedForUpdate(result: CheckResult?, side: Side?): Boolean {
        val span = resolvePromptSpan(result, side) ?: return false
        val root = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE)
        if (root == null || !root.has(span.sideKey) || !root[span.sideKey].isJsonObject) return false
        val section = root.getAsJsonObject(span.sideKey)
        val repoUrl = normalizeString(getString(section, "repoUrl"))
        val fromBase = safeVersion(getString(section, "fromBase"))
        val fromDlc = safeVersion(getString(section, "fromDlc"))
        val toBase = safeVersion(getString(section, "toBase"))
        val toDlc = safeVersion(getString(section, "toDlc"))
        return span.repoUrl == repoUrl && span.fromBase == fromBase && span.fromDlc == fromDlc && span.toBase == toBase && span.toDlc == toDlc
    }

    @JvmStatic
    fun downloadClientPackages(result: CheckResult?, progress: ProgressListener?, cancelChecker: CancelChecker?): DownloadResult {
        return downloadLocalPackages(result, Side.CLIENT, progress, cancelChecker)
    }

    @JvmStatic
    fun downloadLocalPackages(result: CheckResult?, side: Side?, progress: ProgressListener?, cancelChecker: CancelChecker?): DownloadResult {
        if (result == null || !result.hasUpdate()) return DownloadResult.failed("no_update")
        if (!result.canDownload()) return DownloadResult.failed("missing_release_asset")
        val updateDir = DlcUpdateService.updateDir ?: return DownloadResult.failed("missing_dlc_root")
        val downloaded = ArrayList<Path>()
        val totalSteps = result.steps.size
        for (i in 0 until totalSteps) {
            if (cancelChecker?.isCanceled() == true) return DownloadResult.canceled()
            val step = result.steps[i]
            val stepIndex = i
            try {
                val path = downloadClientStepWithFallback(step, updateDir, stepIndex, totalSteps, progress, cancelChecker)
                downloaded.add(path)
                if (!DlcUpdateService.importUpdatePackage(path)) return DownloadResult.failed("import_failed")
                notifyProgress(progress, stepIndex, totalSteps, step.assetName, 1L, 1L)
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                return DownloadResult.canceled()
            } catch (ex: Exception) {
                return DownloadResult.failed(ex.message ?: "download_failed")
            }
        }
        if (side != null) clearSkipPromptMarker(side)
        return DownloadResult.success(downloaded)
    }

    @JvmStatic
    fun hasDownloadedClientPackages(result: CheckResult?): Boolean {
        if (result == null || !result.hasUpdate() || result.steps.isEmpty()) return false
        val updateDir = DlcUpdateService.updateDir ?: return false
        for (step in result.steps) {
            val assetName = normalizeString(step.assetName)
            if (assetName.isBlank()) return false
            val target = updateDir.resolve(assetName)
            if (!Files.isRegularFile(target)) return false
        }
        return true
    }

    @JvmStatic
    fun downloadServerPackages(result: CheckResult?, progress: ProgressListener?, cancelChecker: CancelChecker?, importer: ServerPackageImporter?): DownloadResult {
        if (result == null || !result.hasUpdate()) return DownloadResult.failed("no_update")
        if (!result.canDownload()) return DownloadResult.failed("missing_release_asset")
        if (importer == null) return DownloadResult.failed("missing_server_importer")
        val totalSteps = result.steps.size
        for (i in 0 until totalSteps) {
            if (cancelChecker?.isCanceled() == true) return DownloadResult.canceled()
            val step = result.steps[i]
            val stepIndex = i
            try {
                val imported = downloadServerStepWithFallback(step, importer, stepIndex, totalSteps, progress, cancelChecker)
                if (!imported) return DownloadResult.failed("server_import_failed")
                notifyProgress(progress, stepIndex, totalSteps, step.assetName, 1L, 1L)
            } catch (ex: Exception) {
                return DownloadResult.failed(ex.message ?: "download_failed")
            }
        }
        clearSkipPromptMarker(Side.SERVER)
        return DownloadResult.success(listOf())
    }

    @Throws(IOException::class)
    private fun downloadClientStepWithFallback(step: ReleaseStep?, updateDir: Path, stepIndex: Int, totalSteps: Int, progress: ProgressListener?, cancelChecker: CancelChecker?): Path {
        var lastError: IOException? = null
        for (candidate in resolveAssetCandidates(step)) {
            try {
                return DlcDownloadService.download(
                    candidate.assetUrl,
                    updateDir,
                    candidate.assetName,
                    { done, total -> notifyProgress(progress, stepIndex, totalSteps, candidate.assetName, done, total) },
                    { cancelChecker?.isCanceled() == true },
                )
            } catch (ex: DlcDownloadService.DownloadCanceledException) {
                throw ex
            } catch (ex: IOException) {
                lastError = ex
                if (!isRetryableDownloadFailure(ex)) throw ex
            }
        }
        throw lastError ?: IOException("download_failed")
    }

    @Throws(Exception::class)
    private fun downloadServerStepWithFallback(step: ReleaseStep?, importer: ServerPackageImporter, stepIndex: Int, totalSteps: Int, progress: ProgressListener?, cancelChecker: CancelChecker?): Boolean {
        var lastError: Exception? = null
        for (candidate in resolveAssetCandidates(step)) {
            if (cancelChecker?.isCanceled() == true) throw DlcDownloadService.DownloadCanceledException("canceled")
            try {
                notifyProgress(progress, stepIndex, totalSteps, candidate.assetName, 0L, 1L)
                return importer.importPackage(candidate.assetUrl, candidate.assetName)
            } catch (ex: Exception) {
                lastError = ex
                if (!isRetryableDownloadFailure(ex)) throw ex
            }
        }
        if (lastError != null) throw lastError
        return false
    }

    private fun resolveAssetCandidates(step: ReleaseStep?): List<AssetCandidate> {
        if (step == null) return listOf()
        if (step.assetCandidates.isNotEmpty()) return step.assetCandidates.filterNotNull()
        if (step.assetUrl.isNullOrBlank()) return listOf()
        return listOf(AssetCandidate(step.repoIndex, step.repoUrl, step.assetName, step.assetUrl))
    }

    private fun isRetryableDownloadFailure(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is SocketTimeoutException || current is ConnectException || current is InterruptedIOException) return true
            current = current.cause
        }
        return false
    }

    private fun isTimeoutFailureMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val normalized = message.trim().lowercase(Locale.ROOT)
        return normalized.contains("timed out") || normalized.contains("timeout") || normalized.contains("connect timed out") || normalized.contains("read timed out")
    }

    private fun notifyProgress(progress: ProgressListener?, stepIndex: Int, stepCount: Int, fileName: String?, downloaded: Long, total: Long) {
        if (progress == null || stepCount <= 0) return
        var stepProgress = if (total > 0) downloaded / total.toFloat() else 0f
        if (stepProgress < 0f) stepProgress = 0f
        if (stepProgress > 1f) stepProgress = 1f
        var overall = (stepIndex + stepProgress) / stepCount.toFloat()
        if (overall < 0f) overall = 0f
        if (overall > 1f) overall = 1f
        progress.onProgress(stepIndex, stepCount, fileName, downloaded, total, overall)
    }

    private fun shouldReplace(current: ReleaseStep?, candidate: ReleaseStep?): Boolean {
        if (candidate == null) return false
        if (current == null) return true
        val compareBase = DlcUpdateService.compareVersions(safeVersion(candidate.newBase), safeVersion(current.newBase))
        if (compareBase != 0) return compareBase > 0
        val compareDlc = DlcUpdateService.compareVersions(safeVersion(candidate.newDlc), safeVersion(current.newDlc))
        if (compareDlc != 0) return compareDlc > 0
        val currentHasAsset = !current.assetUrl.isNullOrBlank()
        val candidateHasAsset = !candidate.assetUrl.isNullOrBlank()
        return candidateHasAsset && !currentHasAsset
    }

    private fun copyTransitionMetadata(target: ReleaseStep?, source: ReleaseStep?) {
        if (target == null || source == null) return
        val mergedCandidates = target.assetCandidates
        target.tag = source.tag
        target.oldBase = source.oldBase
        target.oldDlc = source.oldDlc
        target.newBase = source.newBase
        target.newDlc = source.newDlc
        target.assetName = source.assetName
        target.assetUrl = source.assetUrl
        target.repoIndex = source.repoIndex
        target.repoUrl = source.repoUrl
        target.rawChangelog = source.rawChangelog
        target.changelog = source.changelog
        target.assetCandidates = mergedCandidates
    }

    private fun sameTransition(left: ReleaseStep?, right: ReleaseStep?): Boolean {
        if (left == null || right == null) return false
        return safeVersion(left.oldBase) == safeVersion(right.oldBase) &&
            safeVersion(left.oldDlc) == safeVersion(right.oldDlc) &&
            safeVersion(left.newBase) == safeVersion(right.newBase) &&
            safeVersion(left.newDlc) == safeVersion(right.newDlc)
    }

    private fun mergeAssetCandidates(target: ReleaseStep?, candidate: ReleaseStep?) {
        if (target == null || candidate == null) return
        val merged = ArrayList<AssetCandidate?>()
        appendAssetCandidates(merged, target.assetCandidates)
        appendAssetCandidates(merged, candidate.assetCandidates)
        target.assetCandidates = merged
    }

    private fun appendAssetCandidates(merged: MutableList<AssetCandidate?>?, candidates: List<AssetCandidate?>?) {
        if (merged == null || candidates.isNullOrEmpty()) return
        for (candidate in candidates) {
            if (candidate == null || candidate.assetUrl.isBlank()) continue
            var exists = false
            for (existing in merged) {
                if (existing == null) continue
                if (existing.repoIndex == candidate.repoIndex && existing.assetUrl == candidate.assetUrl) {
                    exists = true
                    break
                }
            }
            if (!exists) merged.add(candidate)
        }
    }

    private fun resolvePreferredAsset(release: JsonObject?, side: Side?, base: String?, dlc: String?): AssetChoice {
        val universalNames = buildAssetNameCandidates(UNIVERSAL_PREFIX, base, dlc)
        val sidePrefix = side?.assetPrefix() ?: ""
        val sideNames = buildAssetNameCandidates(sidePrefix, base, dlc)
        for (name in universalNames) {
            val url = findAssetUrl(release, name)
            if (url.isNotBlank()) return AssetChoice(name, url)
        }
        for (name in sideNames) {
            val url = findAssetUrl(release, name)
            if (url.isNotBlank()) return AssetChoice(name, url)
        }
        val fallbackName = if (sideNames.isNotEmpty()) sideNames[0] else if (universalNames.isEmpty()) "" else universalNames[0]
        return AssetChoice(fallbackName, "")
    }

    private fun buildAssetNameCandidates(prefix: String?, base: String?, dlc: String?): List<String> {
        val namePrefix = prefix ?: ""
        val safeBase = safeVersion(base)
        val safeDlc = safeVersion(dlc)
        val names = LinkedHashSet<String>()
        for (delimiter in VERSION_PAIR_DELIMITERS) names.add("$namePrefix$safeBase$delimiter$safeDlc.zip")
        return ArrayList(names)
    }

    private fun findAssetUrl(release: JsonObject?, expectedName: String?): String {
        if (release == null || expectedName.isNullOrBlank()) return ""
        if (!release.has("assets") || release["assets"].isJsonNull) return ""
        val assetsElement = release["assets"]
        if (assetsElement.isJsonArray) {
            for (element in assetsElement.asJsonArray) {
                if (element == null || !element.isJsonObject) continue
                val asset = element.asJsonObject
                val name = getString(asset, "name")
                val download = getString(asset, "browser_download_url")
                val altDownload = getString(asset, "url")
                if (!assetMatchesExpected(name, download, altDownload, expectedName)) continue
                if (download.isNotBlank()) return download
                if (altDownload.isNotBlank()) return altDownload
            }
            return ""
        }
        if (assetsElement.isJsonObject) {
            val assetsObj = assetsElement.asJsonObject
            if (!assetsObj.has("links") || !assetsObj["links"].isJsonArray) return ""
            for (element in assetsObj.getAsJsonArray("links")) {
                if (element == null || !element.isJsonObject) continue
                val link = element.asJsonObject
                val name = getString(link, "name")
                val direct = getString(link, "direct_asset_url")
                val url = getString(link, "url")
                if (!assetMatchesExpected(name, direct, url, expectedName)) continue
                if (direct.isNotBlank()) return direct
                if (url.isNotBlank()) return url
            }
        }
        return ""
    }

    private fun assetMatchesExpected(name: String?, primaryUrl: String?, secondaryUrl: String?, expectedName: String?): Boolean {
        if (expectedName.isNullOrBlank()) return false
        if (name != null && name.equals(expectedName, ignoreCase = true)) return true
        val fromPrimary = extractFileNameFromUrl(primaryUrl)
        if (fromPrimary.isNotBlank() && fromPrimary.equals(expectedName, ignoreCase = true)) return true
        val fromSecondary = extractFileNameFromUrl(secondaryUrl)
        return fromSecondary.isNotBlank() && fromSecondary.equals(expectedName, ignoreCase = true)
    }

    private fun extractFileNameFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val uri = URI.create(url.trim())
            val path = uri.path
            if (path.isNullOrBlank()) return ""
            val slash = path.lastIndexOf('/')
            if (slash >= 0 && slash + 1 < path.length) path.substring(slash + 1).trim() else path.trim()
        } catch (_: Exception) {
            val normalized = url.trim()
            val slash = normalized.lastIndexOf('/')
            if (slash >= 0 && slash + 1 < normalized.length) normalized.substring(slash + 1).trim() else ""
        }
    }

    private fun parseTag(tag: String?): ParsedTag? {
        if (tag.isNullOrBlank()) return null
        val parts = parseTagParts(tag.trim()) ?: return null
        val parsed = ParsedTag()
        parsed.oldBase = parts.oldPair?.base
        parsed.oldDlc = parts.oldPair?.dlc
        parsed.newBase = parts.newPair?.base
        parsed.newDlc = parts.newPair?.dlc
        return parsed
    }

    private fun parseTagParts(tag: String?): ParsedTagParts? {
        if (tag.isNullOrBlank()) return null
        val slashParts = tag.split("/", limit = 2)
        if (slashParts.size == 2) {
            val parsed = parseTagParts(slashParts[0], slashParts[1])
            if (parsed != null) return parsed
        }
        var index = -1
        while (true) {
            index = tag.indexOf('-', index + 1)
            if (index < 0) break
            if (index <= 0 || index >= tag.length - 1) continue
            val parsed = parseTagParts(tag.substring(0, index), tag.substring(index + 1))
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseTagParts(oldRaw: String?, newRaw: String?): ParsedTagParts? {
        val oldPair = parseVersionPair(oldRaw) ?: return null
        val newPair = parseVersionPair(newRaw) ?: return null
        val parts = ParsedTagParts()
        parts.oldPair = oldPair
        parts.newPair = newPair
        return parts
    }

    private fun parseVersionPair(value: String?): VersionPair? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val parts = splitVersionPair(trimmed)
        if (parts.size != 2) return null
        val base = safeVersion(parts[0])
        val dlc = safeVersion(parts[1])
        if (base.isBlank() || dlc.isBlank()) return null
        val pair = VersionPair()
        pair.base = base
        pair.dlc = dlc
        return pair
    }

    private fun splitVersionPair(value: String?): Array<String> {
        if (value == null) return emptyArray()
        for (delimiter in VERSION_PAIR_DELIMITERS) {
            val idx = value.indexOf(delimiter)
            if (idx <= 0 || idx >= value.length - 1) continue
            val left = value.substring(0, idx).trim()
            val right = value.substring(idx + delimiter.length).trim()
            if (left.isNotBlank() && right.isNotBlank()) return arrayOf(left, right)
        }
        val whitespaceParts = value.trim().split(Regex("\\s+"))
        if (whitespaceParts.size == 2) {
            val left = whitespaceParts[0].trim()
            val right = whitespaceParts[1].trim()
            if (left.isNotBlank() && right.isNotBlank()) return arrayOf(left, right)
        }
        return emptyArray()
    }

    @Throws(IOException::class)
    private fun fetchReleases(repo: RepoCoordinates, currentBase: String?, currentDlc: String?): JsonArray {
        val endpoints = buildReleaseApiEndpoints(repo)
        var lastException: IOException? = null
        for (endpoint in endpoints) {
            try {
                return fetchReleasesFromEndpoint(endpoint, currentBase, currentDlc)
            } catch (ex: IOException) {
                lastException = ex
            }
        }
        if (lastException != null) throw lastException
        throw IOException("release_fetch_failed")
    }

    @Throws(IOException::class)
    private fun fetchReleasesFromEndpoint(endpoint: ApiEndpoint, currentBase: String?, currentDlc: String?): JsonArray {
        val collected = JsonArray()
        val safeCurrentBase = safeVersion(currentBase)
        val safeCurrentDlc = safeVersion(currentDlc)
        for (page in 1..RELEASES_MAX_PAGES) {
            val pageReleases = fetchReleasePage(endpoint, page)
            if (pageReleases.isEmpty) break
            val shouldStop = appendReleasesUntilCurrentBoundary(collected, pageReleases, safeCurrentBase, safeCurrentDlc)
            if (shouldStop || pageReleases.size() < RELEASES_PER_PAGE) break
        }
        return collected
    }

    @Throws(IOException::class)
    private fun fetchReleasePage(endpoint: ApiEndpoint, page: Int): JsonArray {
        val api = appendPageParameter(endpoint.url, page)
        val connection = URI.create(api).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "DLC-Manager")
        if (!endpoint.accept.isNullOrBlank()) connection.setRequestProperty("Accept", endpoint.accept)
        if (!endpoint.authHeaderName.isNullOrBlank() && !endpoint.authHeaderValue.isNullOrBlank()) {
            connection.setRequestProperty(endpoint.authHeaderName, endpoint.authHeaderValue)
        }
        connection.connect()
        try {
            val code = connection.responseCode
            val payloadStream = if (code >= 400) connection.errorStream else connection.inputStream
            var payload = ""
            if (payloadStream != null) {
                payloadStream.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        val buffer = CharArray(2048)
                        val sb = StringBuilder()
                        while (true) {
                            val read = reader.read(buffer)
                            if (read == -1) break
                            sb.append(buffer, 0, read)
                        }
                        payload = sb.toString()
                    }
                }
            }
            if (code >= 400) throw IOException("http $code @ $api")
            val releases = parseReleaseArray(payload, api)
            if (releases != null) return releases
            throw IOException("invalid_release_response @ $api")
        } finally {
            connection.disconnect()
        }
    }

    private fun appendReleasesUntilCurrentBoundary(collected: JsonArray?, pageReleases: JsonArray?, currentBase: String?, currentDlc: String?): Boolean {
        if (collected == null || pageReleases == null || pageReleases.isEmpty) return false
        var hasRelevantRelease = hasRelevantRelease(collected, currentBase, currentDlc)
        for (element in pageReleases) {
            if (element == null || !element.isJsonObject) {
                collected.add(element)
                continue
            }
            val release = element.asJsonObject
            var tag = getString(release, "tag_name")
            if (tag.isBlank()) tag = getString(release, "name")
            val parsed = parseTag(tag)
            if (parsed == null) {
                collected.add(release)
                continue
            }
            val compare = compareState(parsed.oldBase, parsed.oldDlc, currentBase, currentDlc)
            if (compare < 0) {
                if (hasRelevantRelease) return true
                continue
            }
            collected.add(release)
            hasRelevantRelease = true
        }
        return false
    }

    private fun hasRelevantRelease(releases: JsonArray?, currentBase: String?, currentDlc: String?): Boolean {
        if (releases == null || releases.isEmpty) return false
        for (element in releases) {
            if (element == null || !element.isJsonObject) continue
            val release = element.asJsonObject
            var tag = getString(release, "tag_name")
            if (tag.isBlank()) tag = getString(release, "name")
            val parsed = parseTag(tag)
            if (parsed != null && compareState(parsed.oldBase, parsed.oldDlc, currentBase, currentDlc) >= 0) return true
        }
        return false
    }

    private fun compareState(baseA: String?, dlcA: String?, baseB: String?, dlcB: String?): Int {
        val compareBase = DlcUpdateService.compareVersions(safeVersion(baseA), safeVersion(baseB))
        if (compareBase != 0) return compareBase
        return DlcUpdateService.compareVersions(safeVersion(dlcA), safeVersion(dlcB))
    }

    private fun appendPageParameter(url: String?, page: Int): String {
        if (url.isNullOrBlank()) return ""
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + "page=" + max(page, 1)
    }

    @Throws(IOException::class)
    @Suppress("DEPRECATION")
    private fun parseReleaseArray(payload: String?, api: String): JsonArray? {
        if (payload == null) return JsonArray()
        val text = stripJsonPrefix(payload)
        if (text.isBlank()) return JsonArray()
        try {
            val jsonReader = JsonReader(StringReader(text))
            jsonReader.isLenient = true
            val element = JsonParser.parseReader(jsonReader)
            if (element == null || element.isJsonNull) return JsonArray()
            if (element.isJsonArray) return element.asJsonArray
            if (element.isJsonObject) {
                val wrapped = unwrapReleaseArray(element.asJsonObject)
                if (wrapped != null) return wrapped
            }
        } catch (ex: Exception) {
            throw IOException("malformed_release_json @ $api", ex)
        }
        return null
    }

    private fun stripJsonPrefix(payload: String): String {
        var text = payload
        if (text.isNotEmpty() && text[0] == '\uFEFF') text = text.substring(1)
        text = text.trim()
        if (text.startsWith(")]}'")) {
            val newline = text.indexOf('\n')
            if (newline >= 0 && newline + 1 < text.length) text = text.substring(newline + 1).trim()
        }
        return text
    }

    private fun unwrapReleaseArray(obj: JsonObject?): JsonArray? {
        if (obj == null) return null
        val keys = arrayOf("data", "list", "items", "releases", "records")
        for (key in keys) {
            if (!obj.has(key) || obj[key].isJsonNull) continue
            val candidate = obj[key]
            if (candidate.isJsonArray) return candidate.asJsonArray
            if (candidate.isJsonObject) {
                val nested = unwrapReleaseArray(candidate.asJsonObject)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun parseRepoCoordinates(url: String?): RepoCoordinates? {
        if (url.isNullOrBlank()) return null
        val normalized = url.trim()
        val sshMatcher = SSH_REPO_PATTERN.matcher(normalized)
        if (sshMatcher.matches()) {
            val host = normalizeHost(sshMatcher.group(1))
            val provider = detectProvider(host)
            val repoPath = parseRepoPathSegments(sshMatcher.group(2), provider)
            if (host.isBlank() || repoPath == null) return null
            val coordinates = RepoCoordinates()
            coordinates.host = host
            coordinates.owner = repoPath.owner
            coordinates.repo = repoPath.repo
            coordinates.namespacePath = repoPath.namespacePath
            coordinates.provider = provider
            return coordinates
        }
        val uri = parseRepoUri(normalized)
        if (uri?.host.isNullOrBlank()) return null
        val host = normalizeHost(uri.host)
        val provider = detectProvider(host)
        val rawPath = uri.path
        if (rawPath.isNullOrBlank()) return null
        val repoPath = parseRepoPathSegments(rawPath, provider) ?: return null
        val coordinates = RepoCoordinates()
        coordinates.host = host
        coordinates.owner = repoPath.owner
        coordinates.repo = repoPath.repo
        coordinates.namespacePath = repoPath.namespacePath
        coordinates.provider = provider
        return coordinates
    }

    private fun parseRepoUri(value: String): URI? {
        return try {
            if (value.startsWith("http://") || value.startsWith("https://")) URI.create(value) else URI.create("https://$value")
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeHost(host: String?): String {
        return host?.trim()?.lowercase(Locale.ROOT) ?: ""
    }

    private fun detectProvider(host: String): Provider {
        if (host.endsWith("github.com")) return Provider.GITHUB
        if (host.endsWith("gitcode.com")) return Provider.GITCODE
        if (host.endsWith("gitee.com")) return Provider.GITEE
        if (host.endsWith("cnb.cool")) return Provider.CNB
        if (host.endsWith("gitlab.com") || host.contains("gitlab")) return Provider.GITLAB
        return Provider.GENERIC
    }

    private fun buildReleaseApiEndpoints(repo: RepoCoordinates?): List<ApiEndpoint> {
        if (repo?.owner == null || repo.repo == null) return listOf()
        val owner = repo.owner!!
        val project = repo.repo!!
        val host = repo.host ?: ""
        val namespacePath = if (repo.namespacePath.isNullOrBlank()) "$owner/$project" else repo.namespacePath!!
        val projectPath = urlEncode(namespacePath)
        val endpoints = ArrayList<ApiEndpoint>()
        when (repo.provider) {
            Provider.GITHUB -> endpoints.add(ApiEndpoint("https://api.github.com/repos/$owner/$project/releases?per_page=$RELEASES_PER_PAGE", "application/vnd.github+json"))
            Provider.GITCODE -> endpoints.add(ApiEndpoint("https://api.gitcode.com/api/v5/repos/$owner/$project/releases?per_page=$RELEASES_PER_PAGE", "application/json", "PRIVATE-TOKEN", getGitCodeAccessToken()))
            Provider.GITEE -> endpoints.add(ApiEndpoint("https://gitee.com/api/v5/repos/$owner/$project/releases?per_page=$RELEASES_PER_PAGE", "application/json"))
            Provider.CNB -> {
                endpoints.add(ApiEndpoint("https://cnb.cool/$namespacePath/-/releases?per_page=$RELEASES_PER_PAGE", "application/json"))
                endpoints.add(ApiEndpoint(
                    "https://$CNB_API_HOST/$projectPath/-/releases?page_size=$RELEASES_PER_PAGE",
                    "application/vnd.cnb.api+json",
                    "Authorization",
                    cnbAuthorizationHeader(),
                ))
            }
            Provider.GITLAB -> endpoints.add(ApiEndpoint("https://$host/api/v4/projects/$projectPath/releases?per_page=$RELEASES_PER_PAGE", "application/json"))
            Provider.GENERIC, null -> {
                endpoints.add(ApiEndpoint("https://$host/api/v5/repos/$owner/$project/releases?per_page=$RELEASES_PER_PAGE", "application/json"))
                endpoints.add(ApiEndpoint("https://api.$host/repos/$owner/$project/releases?per_page=$RELEASES_PER_PAGE", "application/json"))
                endpoints.add(ApiEndpoint("https://$host/api/v4/projects/$projectPath/releases?per_page=$RELEASES_PER_PAGE", "application/json"))
            }
        }
        return endpoints
    }

    private fun parseRepoPathSegments(rawPath: String?, provider: Provider): RepoPath? {
        if (rawPath.isNullOrBlank()) return null
        val parts = ArrayList<String>()
        for (segment in rawPath.split('/')) {
            val trimmed = normalizeRepoPart(segment)
            if (trimmed.isNotBlank()) parts.add(trimmed)
        }
        if (parts.size < 2) return null
        var repoParts = ArrayList(parts)
        if (provider == Provider.GITLAB || provider == Provider.CNB || provider == Provider.GENERIC) {
            val routeMarker = repoParts.indexOf("-")
            if (routeMarker >= 2) {
                repoParts = ArrayList(repoParts.subList(0, routeMarker))
            } else {
                while (repoParts.size > 2) {
                    val tail = repoParts[repoParts.size - 1].lowercase(Locale.ROOT)
                    if (isKnownRepoRouteSegment(tail)) {
                        repoParts.removeAt(repoParts.size - 1)
                        continue
                    }
                    break
                }
            }
        } else {
            repoParts = ArrayList(repoParts.subList(0, 2))
        }
        if (repoParts.size < 2) return null
        val repoPath = RepoPath()
        repoPath.owner = normalizeRepoPart(repoParts[0])
        repoPath.repo = normalizeRepoPart(repoParts[repoParts.size - 1])
        repoPath.namespacePath = repoParts.joinToString("/")
        if (repoPath.owner.isNullOrBlank() || repoPath.repo.isNullOrBlank() || repoPath.namespacePath.isNullOrBlank()) return null
        return repoPath
    }

    private fun isKnownRepoRouteSegment(tail: String): Boolean {
        return tail == "releases" || tail == "tags" || tail == "issues" || tail == "merge_requests" || tail == "wikis" ||
            tail == "blob" || tail == "tree" || tail == "raw" || tail == "commit" || tail == "commits" || tail == "pipelines" || tail == "activity"
    }

    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }

    private fun normalizeRepoPart(value: String?): String {
        var normalized = value?.trim() ?: ""
        if (normalized.endsWith(".git")) normalized = normalized.substring(0, normalized.length - 4)
        return normalized
    }

    private fun resolvePromptSpan(result: CheckResult?, side: Side?): PromptSpan? {
        if (result == null || !result.hasUpdate()) return null
        val span = PromptSpan()
        span.sideKey = normalizeSideKey(side)
        span.repoUrl = normalizeString(result.repoUrl)
        span.fromBase = safeVersion(result.currentBase)
        span.fromDlc = safeVersion(result.currentDlc)
        span.toBase = safeVersion(result.targetBase)
        span.toDlc = safeVersion(result.targetDlc)
        return span
    }

    private fun normalizeSideKey(side: Side?): String {
        return if (side == Side.SERVER) "server" else "client"
    }

    private fun clearSkipPromptMarker(side: Side) {
        try {
            val root = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE) ?: return
            root.remove(normalizeSideKey(side))
            if (root.entrySet().isEmpty()) {
                DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE)
                return
            }
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE, root)
        } catch (_: Exception) {
        }
    }

    private fun safeVersion(value: String?): String {
        if (value == null) return DEFAULT_VERSION
        val normalized = value.trim()
        return if (normalized.isBlank()) DEFAULT_VERSION else normalized
    }

    private fun formatCompactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): String {
        val sourceBase = safeVersion(oldBase)
        val sourceDlc = safeVersion(oldDlc)
        val targetBase = safeVersion(newBase)
        val targetDlc = safeVersion(newDlc)
        val baseChanged = sourceBase != targetBase
        val dlcChanged = sourceDlc != targetDlc
        if (baseChanged && dlcChanged) return "$sourceBase+$sourceDlc -> $targetBase+$targetDlc"
        if (baseChanged) return "$sourceBase -> $targetBase"
        if (dlcChanged) return "$sourceDlc -> $targetDlc"
        return "$sourceBase+$sourceDlc -> $targetBase+$targetDlc"
    }

    private fun stateKey(base: String?, dlc: String?): String {
        return safeVersion(base) + "|" + safeVersion(dlc)
    }

    private fun normalizeString(value: String?): String {
        return value?.trim() ?: ""
    }

    private fun normalizeLangKey(lang: String?): String {
        if (lang.isNullOrBlank()) return "en_us"
        return lang.trim().lowercase(Locale.ROOT).replace('-', '_')
    }

    private fun resolveReleaseChangelog(release: JsonObject?): String {
        if (release == null) return ""
        val keys = arrayOf("body", "description", "release_notes", "notes")
        for (key in keys) {
            val value = getString(release, key)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun getString(obj: JsonObject?, key: String?): String {
        if (obj == null || key.isNullOrBlank()) return ""
        if (!obj.has(key) || obj[key].isJsonNull) return ""
        return try {
            obj[key].asString.trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun getGitCodeAccessToken(): String {
        val cached = gitcodeTokenCache
        if (cached != null) return cached
        val length = GITCODE_TOKEN_PART1.size
        val part2 = generateGitCodeTokenPart2(length)
        val decoded = ByteArray(length)
        for (i in 0 until length) {
            val mixed = (GITCODE_TOKEN_PART1[i].toInt() xor part2[i].toInt()).toByte()
            decoded[i] = (mixed.toInt() xor GITCODE_TOKEN_MASK.toInt() xor ((i * 17) and 0xFF)).toByte()
        }
        val shifted = ByteArray(length)
        for (i in 0 until length) shifted[i] = decoded[(i + GITCODE_TOKEN_SHIFT) % length]
        val token = String(shifted, StandardCharsets.UTF_8)
        gitcodeTokenCache = token
        return token
    }

    private fun cnbAuthorizationHeader(): String? {
        return "Bearer ${getEmbeddedCnbAccessToken()}"
    }

    private fun getEmbeddedCnbAccessToken(): String {
        val length = CNB_TOKEN_PART1.size
        val part2 = generateCnbTokenPart2(length)
        val decoded = ByteArray(length)
        for (i in 0 until length) {
            val mixed = (CNB_TOKEN_PART1[i].toInt() xor part2[i].toInt()).toByte()
            decoded[i] = (mixed.toInt() xor CNB_TOKEN_MASK.toInt() xor ((i * 17) and 0xFF)).toByte()
        }
        val shifted = ByteArray(length)
        for (i in 0 until length) shifted[i] = decoded[(i + CNB_TOKEN_SHIFT) % length]
        return String(shifted, StandardCharsets.UTF_8)
    }

    private fun generateCnbTokenPart2(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var seed = CNB_TOKEN_PART2_SEED
        for (i in 0 until length) {
            seed = (seed * 73 + 41) and 0xFF
            bytes[i] = seed.toByte()
        }
        return bytes
    }

    private fun generateGitCodeTokenPart2(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var seed = GITCODE_TOKEN_PART2_SEED
        for (i in 0 until length) {
            seed = (seed * 73 + 41) and 0xFF
            bytes[i] = seed.toByte()
        }
        return bytes
    }

    private class RepoCoordinates {
        var host: String? = null
        var owner: String? = null
        var repo: String? = null
        var namespacePath: String? = null
        var provider: Provider? = null
    }

    private enum class Provider {
        GITHUB,
        GITCODE,
        GITEE,
        GITLAB,
        CNB,
        GENERIC,
    }

    private class ApiEndpoint @JvmOverloads constructor(
        val url: String,
        val accept: String?,
        val authHeaderName: String? = null,
        val authHeaderValue: String? = null,
    )

    private class RepoPath {
        var owner: String? = null
        var repo: String? = null
        var namespacePath: String? = null
    }

    private class ParsedTag {
        var oldBase: String? = null
        var oldDlc: String? = null
        var newBase: String? = null
        var newDlc: String? = null
    }

    private class ParsedTagParts {
        var oldPair: VersionPair? = null
        var newPair: VersionPair? = null
    }

    private class VersionPair {
        var base: String? = null
        var dlc: String? = null
    }

    private class AssetChoice(name: String?, url: String?) {
        val name: String = name ?: ""
        val url: String = url ?: ""
    }

    private class PromptSpan {
        var sideKey: String = ""
        var repoUrl: String = ""
        var fromBase: String = ""
        var fromDlc: String = ""
        var toBase: String = ""
        var toDlc: String = ""
    }
}
