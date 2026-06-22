package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader
import com.cozooo.dlc_manager.client.required.DlcRequiredDependency
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredProgressItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object DlcServerDownloadService {
    private val PRETTY_GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    class DownloadResult {
        @JvmField var success: Boolean = false
        @JvmField var downloadedCount: Int = 0
        @JvmField var appliedCount: Int = 0
        @JvmField var error: String? = null

        fun didWork(): Boolean = downloadedCount > 0 || appliedCount > 0
    }

    private fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long)
        fun onStatusKey(statusKey: String) {}
    }

    private val REMOTE_LOCK = Any()
    private var remoteSession: RemoteDownloadSession? = null
    private val REQUIRED_LOCK = Any()
    private var requiredSession: RequiredDownloadSession? = null

    @Volatile
    private var requiredCancelRequested = false

    private fun consoleInfo(message: String?) {
        println(message)
    }

    private fun consoleWarn(message: String?) {
        println(message)
    }

    private class RemoteItemState(
        val entry: DlcRemoteEntry?,
        val key: String,
        val name: String,
    ) {
        val downloaded = AtomicLong(0L)
        @Volatile var totalBytes: Long = -1L
        @Volatile var done: Boolean = false
        @Volatile var success: Boolean = false
        @Volatile var error: String? = null
    }

    private class RequiredItemState(
        val entry: DlcRequiredEntry?,
        val id: String,
        val name: String,
    ) {
        val downloaded = AtomicLong(0L)
        @Volatile var totalBytes: Long = -1L
        @Volatile var done: Boolean = false
        @Volatile var success: Boolean = false
        @Volatile var error: String? = null
        @Volatile var lastLogAt: Long = 0L
        @Volatile var lastLogPercent: Int = -1
    }

    private class RemoteDownloadSession(private val items: List<RemoteItemState>) {
        val running = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)
        private val success = AtomicBoolean(true)
        private val completed = AtomicInteger(0)
        private val totalBytes = AtomicLong(0L)
        private val downloadedBytes = AtomicLong(0L)
        private val itemByKey: MutableMap<String, RemoteItemState> = ConcurrentHashMap()
        @Volatile private var statusKey: String? = null
        @Volatile private var error: String? = null

        init {
            for (item in items) {
                itemByKey[item.key] = item
            }
        }

        fun start(dlcRoot: Path?) {
            if (!running.compareAndSet(false, true)) {
                return
            }
            val maxConcurrent = maxOf(1, DlcConfig.getMaxConcurrentDownloads())
            val executor = Executors.newFixedThreadPool(maxConcurrent)
            for (item in items) {
                executor.submit {
                    if (!running.get()) return@submit
                    downloadOne(dlcRoot, item)
                }
            }
            executor.shutdown()
            Thread({
                while (!executor.isTerminated) {
                    try {
                        Thread.sleep(100L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                running.set(false)
                finished.set(true)
            }, "dlc-server-remote-finish").start()
        }

        private fun downloadOne(dlcRoot: Path?, item: RemoteItemState?) {
            if (item?.entry == null) {
                markDone(item, false, "invalid entry")
                return
            }
            try {
                val fileName = downloadRemoteEntryWithProgress(item.entry, item, object : ProgressListener {
                    override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                        this@RemoteDownloadSession.statusKey = null
                        updateProgress(item, downloadedBytes, totalBytes)
                    }

                    override fun onStatusKey(statusKey: String) {
                        this@RemoteDownloadSession.statusKey = statusKey
                    }
                })
                if (fileName != null) {
                    updateRemoteFileNameKey(item.entry, fileName)
                }
                markDone(item, true, null)
            } catch (ex: Exception) {
                markDone(item, false, ex.message)
                success.set(false)
                error = ex.message
            }
        }

        private fun updateProgress(item: RemoteItemState?, current: Long, total: Long) {
            if (item == null) return
            val prev = item.downloaded.getAndSet(current)
            val delta = current - prev
            if (delta != 0L) {
                downloadedBytes.addAndGet(delta)
            }
            if (total > 0 && item.totalBytes != total) {
                val oldTotal = item.totalBytes
                item.totalBytes = total
                if (oldTotal > 0) {
                    totalBytes.addAndGet(total - oldTotal)
                } else {
                    totalBytes.addAndGet(total)
                }
            }
        }

        private fun markDone(item: RemoteItemState?, ok: Boolean, err: String?) {
            if (item == null) return
            item.done = true
            item.success = ok
            item.error = err
            completed.incrementAndGet()
        }

        fun toStatus(lang: String?): DlcServerRemoteStatus {
            val status = DlcServerRemoteStatus()
            status.running = running.get()
            status.finished = finished.get()
            status.success = success.get()
            status.totalCount = items.size
            status.completedCount = completed.get()
            status.totalBytes = totalBytes.get()
            status.downloadedBytes = downloadedBytes.get()
            status.statusKey = statusKey
            status.error = error
            for (item in items) {
                val view = DlcServerRemoteItem()
                view.key = item.key
                view.name = resolveDisplayName(item.entry, lang)
                view.displayNames = resolveDisplayNames(item.entry, view.name)
                view.totalBytes = item.totalBytes
                view.downloadedBytes = item.downloaded.get()
                view.done = item.done
                view.success = item.success
                view.error = item.error
                status.items.add(view)
            }
            return status
        }
    }

    private class RequiredDownloadSession(private val items: List<RequiredItemState>) {
        val running = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)
        private val success = AtomicBoolean(true)
        private val completed = AtomicInteger(0)
        private val totalBytes = AtomicLong(0L)
        private val downloadedBytes = AtomicLong(0L)
        private val itemById: MutableMap<String, RequiredItemState> = ConcurrentHashMap()
        val cancelRequested = AtomicBoolean(false)
        @Volatile private var error: String? = null

        init {
            for (item in items) {
                itemById[item.id] = item
            }
        }

        fun start(dlcRoot: Path?) {
            if (!running.compareAndSet(false, true)) {
                return
            }
            cancelRequested.set(false)
            val maxConcurrent = maxOf(1, DlcConfig.getMaxConcurrentDownloads())
            val executor = Executors.newFixedThreadPool(maxConcurrent)
            for (item in items) {
                executor.submit {
                    if (!running.get() || cancelRequested.get()) return@submit
                    downloadOne(dlcRoot, item)
                }
            }
            executor.shutdown()
            Thread({
                while (!executor.isTerminated) {
                    try {
                        Thread.sleep(100L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                running.set(false)
                finished.set(true)
            }, "dlc-server-required-finish").start()
        }

        private fun downloadOne(dlcRoot: Path?, item: RequiredItemState?) {
            if (item?.entry == null) {
                markDone(item, false, "invalid entry")
                return
            }
            try {
                consoleInfo("[dlc-manager] server required download start: ${item.name} (${safeId(item.entry)})")
                if (cancelRequested.get()) {
                    markDone(item, false, "canceled")
                    success.set(false)
                    error = "canceled"
                    return
                }
                val existingName = resolveExistingFileName(item.entry)
                if (!existingName.isNullOrBlank() && !isTempFileName(existingName)) {
                    item.entry.setResolvedFileName(existingName)
                    if (entryNeedsApply(item.entry)) {
                        applyRequiredEntry(item.entry)
                        if (entryNeedsApply(item.entry)) {
                            val err = "apply_failed:${safeId(item.entry)}"
                            markDone(item, false, err)
                            success.set(false)
                            error = err
                            consoleWarn("[dlc-manager] server required apply failed: ${safeId(item.entry)}")
                            return
                        }
                    }
                    updateProgress(item, 1L, 1L)
                    markDone(item, true, null)
                    return
                }
                val fileName = downloadRequiredEntryWithProgress(item.entry) { downloaded, total ->
                    updateProgress(item, downloaded, total)
                }
                if (!fileName.isNullOrBlank()) {
                    item.entry.setResolvedFileName(fileName)
                    updateRequiredFileNameKey(item.entry, fileName)
                    updateRequiredUserRemoved(item.entry, false)
                } else {
                    val err = "download_failed:${safeId(item.entry)}"
                    markDone(item, false, err)
                    success.set(false)
                    error = err
                    consoleWarn("[dlc-manager] server required download failed: ${safeId(item.entry)}")
                    return
                }
                if (entryNeedsApply(item.entry)) {
                    applyRequiredEntry(item.entry)
                    if (entryNeedsApply(item.entry)) {
                        val err = "apply_failed:${safeId(item.entry)}"
                        markDone(item, false, err)
                        success.set(false)
                        error = err
                        consoleWarn("[dlc-manager] server required apply failed: ${safeId(item.entry)}")
                        return
                    }
                }
                markDone(item, true, null)
            } catch (_: DownloadCanceledException) {
                markDone(item, false, "canceled")
                success.set(false)
                error = "canceled"
            } catch (ex: Exception) {
                val err = if (ex.message.isNullOrBlank()) "error:${safeId(item.entry)}" else ex.message!!
                markDone(item, false, err)
                success.set(false)
                error = err
                consoleWarn("[dlc-manager] server required download failed: ${safeId(item.entry)} ${ex.message}")
            }
        }

        private fun updateProgress(item: RequiredItemState?, current: Long, total: Long) {
            if (item == null) return
            val prev = item.downloaded.getAndSet(current)
            val delta = current - prev
            if (delta != 0L) {
                downloadedBytes.addAndGet(delta)
            }
            if (total > 0 && item.totalBytes != total) {
                val oldTotal = item.totalBytes
                item.totalBytes = total
                if (oldTotal > 0) {
                    totalBytes.addAndGet(total - oldTotal)
                } else {
                    totalBytes.addAndGet(total)
                }
            }
            if (total > 0) {
                val percent = minOf(100L, current * 100L / maxOf(1L, total)).toInt()
                val now = System.currentTimeMillis()
                if (percent >= 0 && (percent - item.lastLogPercent >= 10 || now - item.lastLogAt >= 5000)) {
                    item.lastLogAt = now
                    item.lastLogPercent = percent
                    var fileName = item.entry?.getResolvedFileName()
                    if (fileName.isNullOrBlank()) {
                        fileName = item.entry?.getRequiredFileName()
                    }
                    if (fileName.isNullOrBlank()) {
                        fileName = safeId(item.entry)
                    }
                    consoleInfo("[dlc-manager] server required downloading: ${item.name} ($fileName) $percent%")
                }
            }
        }

        private fun markDone(item: RequiredItemState?, ok: Boolean, err: String?) {
            if (item == null) return
            item.done = true
            item.success = ok
            item.error = err
            completed.incrementAndGet()
            if (ok) {
                consoleInfo("[dlc-manager] server required download done: ${item.name} (${safeId(item.entry)})")
            } else if (!err.isNullOrBlank()) {
                consoleWarn("[dlc-manager] server required download failed: ${item.name} (${safeId(item.entry)}) $err")
            } else {
                consoleWarn("[dlc-manager] server required download failed: ${item.name} (${safeId(item.entry)})")
            }
        }

        fun toStatus(lang: String?): DlcServerRequiredStatus {
            val status = DlcServerRequiredStatus()
            status.running = running.get()
            status.finished = finished.get()
            status.success = success.get()
            status.totalCount = items.size
            status.completedCount = completed.get()
            status.totalBytes = totalBytes.get()
            status.downloadedBytes = downloadedBytes.get()
            status.error = error
            for (item in items) {
                val view = DlcServerRequiredProgressItem()
                view.id = item.id
                view.name = resolveRequiredDisplayName(item.entry, lang)
                view.totalBytes = item.totalBytes
                view.downloadedBytes = item.downloaded.get()
                view.done = item.done
                view.success = item.success
                view.error = item.error
                status.items.add(view)
            }
            return status
        }

        fun requestCancel() {
            cancelRequested.set(true)
            success.set(false)
            error = "canceled"
        }
    }

    @JvmStatic
    fun collectMissingRemoteItems(dlcRoot: Path?): List<DlcServerRemoteItem> = collectMissingRemoteItems(dlcRoot, null)

    @JvmStatic
    fun collectMissingRemoteItems(dlcRoot: Path?, lang: String?): List<DlcServerRemoteItem> {
        val list = ArrayList<DlcServerRemoteItem>()
        for (entry in collectMissingRemote(dlcRoot)) {
            val item = DlcServerRemoteItem()
            item.key = selectionKey(entry)
            item.name = resolveDisplayName(entry, lang)
            item.displayNames = resolveDisplayNames(entry, item.name)
            list.add(item)
        }
        return list
    }

    @JvmStatic
    fun collectRequiredItems(dlcRoot: Path?): List<DlcServerRequiredItem> = collectRequiredItems(dlcRoot, null)

    @JvmStatic
    fun collectRequiredItems(dlcRoot: Path?, lang: String?): List<DlcServerRequiredItem> {
        val items = ArrayList<DlcServerRequiredItem>()
        val entries = loadAllRequiredEntries(dlcRoot)
        for (entry in entries) {
            if (!entry.isInList() || entry.isHidden()) continue
            if (entry.isUserRemoved()) continue
            val fileName = resolveExistingFileName(entry)
            entry.setResolvedFileName(fileName)
            val hasFile = fileName != null &&
                entry.resolveRequiredPath(fileName) != null &&
                Files.exists(entry.resolveRequiredPath(fileName)) &&
                !isTempFileName(fileName)
            val needsApply = entryNeedsApply(entry)
            val item = DlcServerRequiredItem()
            item.id = safeId(entry)
            item.identifier = entry.getIdentifier()
            item.name = resolveRequiredDisplayName(entry, lang)
            item.displayNames.putAll(entry.getDisplayNames())
            item.tips.putAll(entry.getTips())
            for (dep in entry.getDependencies()) {
                val identifier = dep?.getIdentifier()
                if (!identifier.isNullOrBlank()) {
                    item.dependencies.add(identifier)
                }
            }
            item.inList = entry.isInList()
            item.hidden = entry.isHidden()
            item.userRemoved = entry.isUserRemoved()
            item.hasFile = hasFile
            item.needsApply = needsApply
            item.applied = hasFile && !needsApply
            item.fileName = fileName
            items.add(item)
        }
        return items
    }

    @JvmStatic
    fun startRemoteDownload(dlcRoot: Path?, keys: List<String>?): DlcServerRemoteStatus = startRemoteDownload(dlcRoot, keys, null)

    @JvmStatic
    fun startRemoteDownload(dlcRoot: Path?, keys: List<String>?, lang: String?): DlcServerRemoteStatus {
        synchronized(REMOTE_LOCK) {
            val current = remoteSession
            if (current != null && current.running.get()) {
                return current.toStatus(lang)
            }
            val missing = if (!keys.isNullOrEmpty()) collectMissingRemoteByKeys(dlcRoot, keys) else collectMissingRemote(dlcRoot)
            val items = ArrayList<RemoteItemState>()
            for (entry in missing) {
                val key = selectionKey(entry)
                val name = resolveDisplayName(entry, lang)
                items.add(RemoteItemState(entry, key, name))
            }
            val session = RemoteDownloadSession(items)
            remoteSession = session
            session.start(dlcRoot)
            return session.toStatus(lang)
        }
    }

    private fun collectMissingRemoteByKeys(dlcRoot: Path?, keys: List<String>?): List<DlcRemoteEntry> {
        val missing = ArrayList<DlcRemoteEntry>()
        if (dlcRoot == null || keys.isNullOrEmpty()) {
            return missing
        }
        val entries = DlcRemoteLoader.load(dlcRoot)
        val seen = HashSet<String>()
        for (entry in entries) {
            val key = selectionKey(entry)
            if (keys.contains(key)) {
                addMissingRemote(entry, missing, seen, false)
            }
        }
        return missing
    }

    @JvmStatic
    fun startRequiredDownload(dlcRoot: Path?, ids: List<String>?): DlcServerRequiredStatus = startRequiredDownload(dlcRoot, ids, null)

    @JvmStatic
    fun startRequiredDownload(dlcRoot: Path?, ids: List<String>?, lang: String?): DlcServerRequiredStatus {
        synchronized(REQUIRED_LOCK) {
            val current = requiredSession
            if (current != null && current.running.get()) {
                return current.toStatus(lang)
            }
            requiredCancelRequested = false
            val entries = loadAllRequiredEntries(dlcRoot)
            val index = buildRequiredIndex(entries)
            val targets = if (!ids.isNullOrEmpty()) collectRequiredDependencies(ids, index) else entries
            val items = ArrayList<RequiredItemState>()
            for (entry in targets) {
                if (!entry.isInList() || entry.isHidden()) continue
                if (entry.isUserRemoved()) continue
                val id = safeId(entry)
                val name = resolveRequiredDisplayName(entry, lang)
                items.add(RequiredItemState(entry, id, name))
            }
            val session = RequiredDownloadSession(items)
            requiredSession = session
            session.start(dlcRoot)
            return session.toStatus(lang)
        }
    }

    @JvmStatic
    fun cancelRequiredDownload(): Boolean {
        synchronized(REQUIRED_LOCK) {
            val session = requiredSession
            if (session == null || !session.running.get()) {
                return false
            }
            requiredCancelRequested = true
            session.requestCancel()
            return true
        }
    }

    @JvmStatic
    fun getRequiredDownloadStatus(): DlcServerRequiredStatus = getRequiredDownloadStatus(null)

    @JvmStatic
    fun getRequiredDownloadStatus(lang: String?): DlcServerRequiredStatus {
        synchronized(REQUIRED_LOCK) {
            val session = requiredSession
            if (session == null) {
                return DlcServerRequiredStatus().apply {
                    running = false
                    finished = true
                    success = true
                    totalCount = 0
                    completedCount = 0
                }
            }
            return session.toStatus(lang)
        }
    }

    @JvmStatic
    fun getRemoteDownloadStatus(): DlcServerRemoteStatus = getRemoteDownloadStatus(null)

    @JvmStatic
    fun getRemoteDownloadStatus(lang: String?): DlcServerRemoteStatus {
        synchronized(REMOTE_LOCK) {
            val session = remoteSession
            if (session == null) {
                return DlcServerRemoteStatus().apply {
                    running = false
                    finished = true
                    success = true
                    totalCount = 0
                    completedCount = 0
                }
            }
            return session.toStatus(lang)
        }
    }

    @JvmStatic
    fun collectMissingRemote(dlcRoot: Path?): List<DlcRemoteEntry> {
        val missing = ArrayList<DlcRemoteEntry>()
        if (dlcRoot == null) {
            return missing
        }
        val entries = DlcRemoteLoader.load(dlcRoot)
        val seen = HashSet<String>()
        for (entry in entries) {
            addMissingRemote(entry, missing, seen, false)
        }
        return missing
    }

    private fun addMissingRemote(entry: DlcRemoteEntry?, missing: MutableList<DlcRemoteEntry>, seen: MutableSet<String>, forceInclude: Boolean) {
        if (entry == null) return
        val key = selectionKey(entry)
        if (!seen.add(key)) return
        if (isRemoteMissing(entry, forceInclude)) {
            missing.add(entry)
        }
        val deps = entry.getRemoteDependencies()
        if (deps.isNotEmpty()) {
            for (dep in deps) {
                addMissingRemote(dep, missing, seen, true)
            }
        }
    }

    private fun isRemoteMissing(entry: DlcRemoteEntry?, forceInclude: Boolean): Boolean {
        if (entry == null) return false
        if (!forceInclude && !entry.isInList() && !entry.isHidden()) return false
        val target = entry.getTargetPath()
        return target == null || !Files.exists(target)
    }

    private fun selectionKey(entry: DlcRemoteEntry?): String {
        if (entry == null) return "unknown"
        var key = entry.getDlcIdentifier()
        if (key.isNullOrBlank()) {
            val configPath = entry.getConfigPath()
            if (configPath?.fileName != null) {
                key = configPath.fileName.toString()
            }
        }
        if (key.isNullOrBlank()) {
            key = entry.getFileNameHint()
        }
        return if (key.isNullOrBlank()) "unknown" else key
    }

    private fun resolveDisplayName(entry: DlcRemoteEntry?): String = resolveDisplayName(entry, null)

    private fun resolveDisplayName(entry: DlcRemoteEntry?, langOverride: String?): String {
        if (entry == null) return ""
        val fallback = selectionKey(entry)
        val displayNames = resolveDisplayNames(entry, fallback)
        if (displayNames.isEmpty()) {
            return fallback
        }
        val lang = normalizeLang(langOverride)
        var name = displayNames[lang]
        if (name.isNullOrBlank()) {
            name = displayNames["en_us"]
        }
        if (name.isNullOrBlank()) {
            name = displayNames.values.firstOrNull() ?: fallback
        }
        return name ?: ""
    }

    private fun resolveRequiredDisplayName(entry: DlcRequiredEntry?, langOverride: String?): String {
        if (entry == null) return ""
        var fallback = entry.getBaseName()
        if (fallback.isBlank()) {
            fallback = entry.getIdentifier()
        }
        if (fallback.isBlank()) {
            fallback = "required"
        }
        val displayNames = entry.getDisplayNames()
        if (displayNames.isEmpty()) {
            return fallback
        }
        val lang = normalizeLang(langOverride)
        var name = displayNames[lang]
        if (name.isNullOrBlank()) {
            name = displayNames["en_us"]
        }
        if (name.isNullOrBlank()) {
            name = displayNames.values.firstOrNull() ?: fallback
        }
        return name ?: ""
    }

    private fun normalizeLang(langOverride: String?): String? {
        var lang = langOverride
        if (lang.isNullOrBlank()) {
            lang = CrossPlatformRuntime.languageCode()
        }
        return DlcDcConfigIO.normalizeLangKey(lang)
    }

    @JvmStatic
    fun collectMissingRequired(dlcRoot: Path?): List<DlcRequiredEntry> {
        val missing = ArrayList<DlcRequiredEntry>()
        if (dlcRoot == null) {
            return missing
        }
        val entries = loadRequiredEntries(dlcRoot)
        for (entry in entries) {
            if (!entry.isInList() || entry.isUserRemoved()) continue
            val fileName = resolveExistingFileName(entry)
            entry.setResolvedFileName(fileName)
            val hasFile = fileName != null &&
                entry.resolveRequiredPath(fileName) != null &&
                Files.exists(entry.resolveRequiredPath(fileName)) &&
                !isTempFileName(fileName)
            if (!hasFile || entryNeedsApply(entry)) {
                missing.add(entry)
            }
        }
        return missing
    }

    @JvmStatic
    fun hasRequiredEntries(dlcRoot: Path?): Boolean {
        if (dlcRoot == null) {
            return false
        }
        return loadRequiredEntries(dlcRoot).isNotEmpty()
    }

    @JvmStatic
    fun downloadMissingRemote(dlcRoot: Path?): DownloadResult {
        val result = DownloadResult()
        val missing = collectMissingRemote(dlcRoot)
        if (missing.isEmpty()) {
            result.success = true
            return result
        }
        for (entry in missing) {
            try {
                val fileName = downloadRemoteEntry(entry)
                if (fileName != null) {
                    result.downloadedCount++
                    updateRemoteFileNameKey(entry, fileName)
                }
            } catch (ex: Exception) {
                result.success = false
                result.error = ex.message
                consoleWarn("[dlc-manager] server remote download failed: ${ex.message}")
                return result
            }
        }
        result.success = true
        return result
    }

    @JvmStatic
    fun downloadAndApplyRequired(dlcRoot: Path?): DownloadResult? {
        val result = DownloadResult()
        if (dlcRoot == null) {
            result.success = true
            return result
        }
        try {
            Files.createDirectories(dlcRoot)
        } catch (_: IOException) {
        }
        val entries = DlcRequiredLoader.load(dlcRoot)
        if (entries.isEmpty()) {
            result.success = true
            return result
        }
        for (entry in entries) {
            if (!entry.isInList() || entry.isUserRemoved()) continue
            var fileName = resolveExistingFileName(entry)
            if (fileName.isNullOrBlank() || isTempFileName(fileName)) {
                try {
                    val id = safeId(entry)
                    val name = entry.resolveDisplayName()
                    consoleInfo("[dlc-manager] server required download start: $name ($id)")
                    val lastLogAt = longArrayOf(0L)
                    val lastPercent = intArrayOf(-1)
                    fileName = downloadRequiredEntryWithProgress(entry) { downloaded, total ->
                        if (total <= 0) return@downloadRequiredEntryWithProgress
                        val percent = minOf(100L, downloaded * 100L / maxOf(1L, total)).toInt()
                        val now = System.currentTimeMillis()
                        if (percent >= 0 && (percent - lastPercent[0] >= 10 || now - lastLogAt[0] >= 5000)) {
                            lastPercent[0] = percent
                            lastLogAt[0] = now
                            var label = entry.getRequiredFileName()
                            if (label.isNullOrBlank()) {
                                label = id
                            }
                            consoleInfo("[dlc-manager] server required downloading: $name ($label) $percent%")
                        }
                    }
                    if (!fileName.isNullOrBlank()) {
                        entry.setResolvedFileName(fileName)
                        result.downloadedCount++
                        updateRequiredFileNameKey(entry, fileName)
                        consoleInfo("[dlc-manager] server required download done: $name ($id)")
                    }
                } catch (ex: Exception) {
                    result.success = false
                    result.error = ex.message
                    consoleWarn("[dlc-manager] server required download failed: ${ex.message}")
                    return result
                }
            } else {
                entry.setResolvedFileName(fileName)
            }
        }
        result.appliedCount = applyRequired(entries)
        result.success = true
        return result
    }

    @JvmStatic
    fun uninstallRequired(dlcRoot: Path?, ids: List<String>?): Int {
        if (dlcRoot == null || ids.isNullOrEmpty()) {
            return 0
        }
        val entries = loadAllRequiredEntries(dlcRoot)
        val index = buildRequiredIndex(entries)
        var removed = 0
        for (id in ids) {
            if (id.isNullOrBlank()) continue
            val entry = index[id] ?: continue
            removeEntryFiles(entry)
            updateRequiredUserRemoved(entry, true)
            removed++
        }
        return removed
    }

    @JvmStatic
    fun applyRequiredByIds(dlcRoot: Path?, ids: List<String>?): Int {
        if (dlcRoot == null || ids.isNullOrEmpty()) {
            return 0
        }
        val entries = loadAllRequiredEntries(dlcRoot)
        val index = buildRequiredIndex(entries)
        var applied = 0
        for (id in ids) {
            if (id.isNullOrBlank()) continue
            val entry = index[id] ?: continue
            val fileName = resolveExistingFileName(entry)
            if (fileName != null) {
                entry.setResolvedFileName(fileName)
            }
            if (applyRequiredEntry(entry)) {
                updateRequiredUserRemoved(entry, false)
                applied++
            }
        }
        return applied
    }

    private fun safeId(entry: DlcRequiredEntry?): String {
        if (entry == null) return "unknown"
        var id = entry.getIdentifier()
        if (id.isBlank()) {
            id = entry.resolveDisplayName()
        }
        if (id.isBlank()) {
            id = "required"
        }
        return id
    }

    private fun applyRequired(entries: List<DlcRequiredEntry>?): Int {
        var applied = 0
        if (entries.isNullOrEmpty()) {
            return applied
        }
        for (entry in entries) {
            val fileName = entry.getResolvedFileName()
            if (fileName.isNullOrBlank()) continue
            val source = entry.resolveRequiredPath(fileName)
            if (source == null || !Files.exists(source)) continue
            for (targetDir in entry.resolveAppliedTargets()) {
                val target = resolveTargetPath(targetDir, fileName) ?: continue
                try {
                    target.parent?.let { Files.createDirectories(it) }
                    if (!Files.exists(target)) {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                        applied++
                    }
                } catch (_: IOException) {
                }
            }
        }
        return applied
    }

    private fun applyRequiredEntry(entry: DlcRequiredEntry?): Boolean {
        if (entry == null) return false
        val fileName = entry.getResolvedFileName()
        if (fileName.isNullOrBlank()) return false
        val source = entry.resolveRequiredPath(fileName)
        if (source == null || !Files.exists(source)) return false
        var applied = false
        for (targetDir in entry.resolveAppliedTargets()) {
            val target = resolveTargetPath(targetDir, fileName) ?: continue
            try {
                target.parent?.let { Files.createDirectories(it) }
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    applied = true
                }
            } catch (_: IOException) {
            }
        }
        return applied
    }

    private fun entryNeedsApply(entry: DlcRequiredEntry?): Boolean {
        if (entry == null) return false
        var fileName = normalizeFileName(entry.getResolvedFileName())
        if (fileName.isNullOrBlank()) {
            fileName = normalizeFileName(guessFileName(entry))
            entry.setResolvedFileName(fileName)
        }
        if (isTempFileName(fileName)) {
            entry.setResolvedFileName(null)
            return true
        }
        if (fileName == null) {
            return true
        }
        for (target in entry.resolveAppliedTargets()) {
            val targetFile = resolveTargetPath(target, fileName)
            if (targetFile != null && !Files.exists(targetFile)) {
                return true
            }
        }
        return false
    }

    private fun loadAllRequiredEntries(dlcRoot: Path?): MutableList<DlcRequiredEntry> {
        val entries = ArrayList(loadRequiredEntries(dlcRoot))
        val index = buildRequiredIndex(entries)
        val queue = ArrayDeque(entries)
        while (!queue.isEmpty()) {
            val entry = queue.poll() ?: continue
            for (dep in entry.getDependencies()) {
                val id = dep?.getIdentifier()
                if (id.isNullOrBlank()) continue
                if (index.containsKey(id)) continue
                val depEntry = dep.getEntry()
                if (depEntry != null && depEntry.isInList()) {
                    depEntry.setIdentifier(id)
                    index[id] = depEntry
                    entries.add(depEntry)
                    queue.add(depEntry)
                }
            }
        }
        return entries
    }

    private fun buildRequiredIndex(entries: List<DlcRequiredEntry>?): MutableMap<String, DlcRequiredEntry> {
        val map = HashMap<String, DlcRequiredEntry>()
        if (entries == null) return map
        for (entry in entries) {
            val id = entry.getIdentifier()
            if (id.isBlank()) {
                val safe = safeId(entry)
                if (safe.isNotBlank()) {
                    map.putIfAbsent(safe, entry)
                }
                continue
            }
            map.putIfAbsent(id, entry)
            val safe = safeId(entry)
            if (safe.isNotBlank()) {
                map.putIfAbsent(safe, entry)
            }
        }
        return map
    }

    private fun collectRequiredDependencies(ids: List<String>?, index: Map<String, DlcRequiredEntry>): List<DlcRequiredEntry> {
        val result = ArrayList<DlcRequiredEntry>()
        if (ids.isNullOrEmpty()) {
            return result
        }
        val queue = ArrayDeque(ids)
        val seen = HashSet<String>()
        while (!queue.isEmpty()) {
            val id = queue.poll()
            if (id.isNullOrBlank()) continue
            if (!seen.add(id)) continue
            val entry = index[id] ?: continue
            result.add(entry)
            for (dep in entry.getDependencies()) {
                val depId = dep?.getIdentifier()
                if (depId.isNullOrBlank()) continue
                queue.add(depId)
            }
        }
        return result
    }

    private fun removeEntryFiles(entry: DlcRequiredEntry?) {
        if (entry == null) return
        val targets = entry.resolveAppliedTargets()
        if (targets.isEmpty()) return
        val fileName = resolveAppliedFileName(entry, targets)
        for (target in targets) {
            val name = target.fileName?.toString() ?: ""
            val targetFile = if (name.endsWith(".jar") || name.endsWith(".zip")) {
                target
            } else {
                resolveTargetPath(target, fileName)
            }
            if (targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile)
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun resolveAppliedFileName(entry: DlcRequiredEntry?, targets: List<Path>?): String? {
        if (entry == null) return null
        val fileName = entry.getResolvedFileName()
        if (!fileName.isNullOrBlank()) {
            return fileName
        }
        if (targets != null) {
            for (target in targets) {
                val name = target.fileName?.toString() ?: ""
                if (name.endsWith(".jar") || name.endsWith(".zip")) {
                    return name
                }
            }
        }
        val base = entry.getBaseName()
        if (base.isBlank() || targets == null) {
            return null
        }
        for (target in targets) {
            if (Files.isDirectory(target)) {
                try {
                    Files.newDirectoryStream(target, "$base.*").use { stream ->
                        for (path in stream) {
                            val name = path.fileName?.toString() ?: ""
                            if (!DlcDcFormatUtil.isDcName(name)) {
                                return name
                            }
                        }
                    }
                } catch (_: IOException) {
                }
            }
        }
        return null
    }

    private fun loadRequiredEntries(dlcRoot: Path?): List<DlcRequiredEntry> {
        val entries = DlcRequiredLoader.load(dlcRoot)
        if (entries.isNotEmpty()) {
            return entries
        }
        val fallback = ArrayList<DlcRequiredEntry>()
        if (dlcRoot == null) {
            return fallback
        }
        val requiredDir = dlcRoot.resolve("required")
        if (!Files.isDirectory(requiredDir)) {
            return fallback
        }
        try {
            Files.newDirectoryStream(requiredDir).use { stream ->
                for (path in stream) {
                    val fileName = path.fileName?.toString() ?: continue
                    if (!DlcDcFormatUtil.isDcName(fileName)) continue
                    if (isNoticeConfig(fileName)) continue
                    val entry = DlcRequiredLoader.loadSingle(path, dlcRoot)
                    if (entry != null) {
                        fallback.add(entry)
                    }
                }
            }
        } catch (_: IOException) {
        }
        return fallback
    }

    private fun normalizeFileName(name: String?): String? {
        val trimmed = name?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun guessFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) return null
        val directUrl = entry.getDirectUrl()
        if (!directUrl.isNullOrBlank()) {
            val name = DlcDownloadUtil.extractFileNameFromUrl(directUrl)
            if (DlcDownloadUtil.isLikelyFileName(name)) {
                return name
            }
        }
        return null
    }

    private fun resolveTargetPath(targetDir: Path?, fileName: String?): Path? {
        if (targetDir == null || fileName.isNullOrBlank()) {
            return null
        }
        val name = targetDir.fileName?.toString() ?: ""
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir.normalize()
        }
        return targetDir.resolve(fileName).normalize()
    }

    private fun resolveExistingFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) return null
        var fileName = entry.getResolvedFileName()
        if (isConfigFileName(fileName) || isTempFileName(fileName)) {
            fileName = null
        }
        if (!fileName.isNullOrBlank()) {
            val target = entry.resolveRequiredPath(fileName)
            if (target != null && Files.exists(target) && !isConfigFile(entry, target)) {
                return fileName
            }
            fileName = null
        }
        val base = entry.getBaseName()
        if (entry.getRequiredDir() == null || base.isBlank()) {
            return fileName
        }
        try {
            Files.newDirectoryStream(entry.getRequiredDir(), "$base.*").use { stream ->
                for (path in stream) {
                    if (isConfigFile(entry, path)) continue
                    val name = path.fileName?.toString() ?: ""
                    if (isTempFileName(name)) continue
                    return name
                }
            }
        } catch (_: IOException) {
        }
        return null
    }

    private fun isConfigFileName(fileName: String?): Boolean = fileName != null && DlcDcFormatUtil.isDcName(fileName)

    private fun isTempFileName(fileName: String?): Boolean = fileName != null && fileName.endsWith(".part")

    private fun isConfigFile(entry: DlcRequiredEntry?, file: Path?): Boolean {
        if (entry == null || file == null) return false
        val config = entry.getConfigPath() ?: return false
        return try {
            Files.isSameFile(config, file)
        } catch (_: IOException) {
            false
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadRequiredEntry(entry: DlcRequiredEntry?): String? = downloadRequiredEntryWithProgress(entry, null)

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadRequiredEntryWithProgress(entry: DlcRequiredEntry?, listener: ProgressListener?): String? {
        if (entry == null) return null
        checkRequiredCanceled()
        val resolver = DlcDownloadCandidate.resolver(entry)
        precheckDownloadSources(entry, listener)
        var lastError: IOException? = null
        while (true) {
            val candidates = resolver.next()
            if (candidates.isEmpty()) break
            for (candidate in candidates) {
                for (attempt in 1..DlcConfig.getMaxRetryCount()) {
                    try {
                        checkRequiredCanceled()
                        var fileName = candidate.fileNameOverride
                        if (fileName.isNullOrBlank()) {
                            fileName = entry.getRequiredFileName()
                        }
                        if (candidate.direct && !DlcDownloadUtil.isLikelyFileName(fileName)) {
                            try {
                                val suggested = DlcDownloadService.suggestFileName(candidate.url)
                                if (!suggested.isNullOrBlank()) {
                                    fileName = suggested
                                }
                            } catch (_: IOException) {
                            }
                        }
                        fileName = finalizeFileName(fileName, entry, candidate.url)
                        val target = entry.resolveRequiredPath(fileName) ?: throw IOException("invalid target path")
                        target.parent?.let { Files.createDirectories(it) }
                        if (Files.exists(target)) {
                            return fileName
                        }
                        val downloaded = if (candidate.direct) {
                            DlcDownloadService.download(candidate.url, target.parent, fileName, { downloadedBytes, totalBytes ->
                                listener?.onProgress(downloadedBytes, totalBytes)
                            }) {
                                requiredCancelRequested || isRequiredCanceled()
                            }
                        } else {
                            DlcDownloadService.downloadKnown(candidate.url, target.parent, fileName, candidate.expectedSize, { downloadedBytes, totalBytes ->
                                listener?.onProgress(downloadedBytes, totalBytes)
                            }) {
                                requiredCancelRequested || isRequiredCanceled()
                            }
                        }
                        checkRequiredCanceled()
                        verifyDownloadedFile(downloaded, candidate)
                        return downloaded.fileName?.toString() ?: fileName
                    } catch (_: DlcDownloadService.DownloadCanceledException) {
                        throw DownloadCanceledException("canceled")
                    } catch (ex: DownloadCanceledException) {
                        throw ex
                    } catch (ex: IOException) {
                        lastError = ex
                        if (attempt >= DlcConfig.getMaxRetryCount()) {
                            break
                        }
                    }
                }
            }
        }
        if (lastError == null) {
            lastError = resolver.lastError()
        }
        if (lastError != null) {
            throw lastError
        }
        throw IOException("missing download url")
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadRemoteEntry(entry: DlcRemoteEntry?): String? = downloadRemoteEntryWithProgress(entry, null, null)

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadRemoteEntryWithProgress(
        entry: DlcRemoteEntry?,
        state: RemoteItemState?,
        listener: ProgressListener?,
    ): String? {
        if (entry == null) return null
        val resolver = DlcDownloadCandidate.resolver(entry)
        precheckDownloadSources(entry, listener)
        var lastError: IOException? = null
        while (true) {
            val candidates = resolver.next()
            if (candidates.isEmpty()) break
            for (candidate in candidates) {
                for (attempt in 1..DlcConfig.getMaxRetryCount()) {
                    try {
                        var fileName = candidate.fileNameOverride
                        val fileNameExplicit = entry.isFileNameExplicit() && !fileName.isNullOrBlank()
                        if (fileName.isNullOrBlank()) {
                            fileName = entry.getFileNameHint()
                        }
                        var suggestedName: String? = null
                        if (candidate.direct || !DlcDownloadUtil.isLikelyFileName(fileName)) {
                            try {
                                suggestedName = DlcDownloadService.suggestFileName(candidate.url)
                            } catch (_: IOException) {
                            }
                        }
                        if (!fileNameExplicit) {
                            if (candidate.direct && !suggestedName.isNullOrBlank()) {
                                fileName = suggestedName
                            }
                            if (!DlcDownloadUtil.isLikelyFileName(fileName) && !suggestedName.isNullOrBlank()) {
                                fileName = suggestedName
                            }
                        }
                        fileName = finalizeFileName(fileName, entry, candidate.url)
                        val target = entry.resolveTargetPath(fileName) ?: throw IOException("invalid target path")
                        target.parent?.let { Files.createDirectories(it) }
                        if (Files.exists(target)) {
                            return fileName
                        }
                        val downloadListener = progressListener(listener)
                        val downloaded = if (candidate.direct) {
                            DlcDownloadService.download(candidate.url, target.parent, fileName, downloadListener, null)
                        } else {
                            DlcDownloadService.downloadKnown(candidate.url, target.parent, fileName, candidate.expectedSize, downloadListener, null)
                        }
                        verifyDownloadedFile(downloaded, candidate)
                        val actualName = downloaded.fileName?.toString() ?: fileName
                        entry.setFileNameHint(actualName)
                        return actualName
                    } catch (ex: IOException) {
                        lastError = ex
                        if (attempt >= DlcConfig.getMaxRetryCount()) {
                            break
                        }
                    }
                }
            }
        }
        if (lastError == null) {
            lastError = resolver.lastError()
        }
        if (lastError != null) {
            throw lastError
        }
        throw IOException("missing download url")
    }

    private fun precheckDownloadSources(entry: DlcRemoteEntry, listener: ProgressListener?) {
        try {
            val candidates = DlcDownloadCandidate.resolve(entry)
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) return
            DlcDownloadService.precheckDownloadSources(candidates, progressListener(listener))
        } catch (_: Exception) {
        }
    }

    private fun precheckDownloadSources(entry: DlcRequiredEntry, listener: ProgressListener?) {
        try {
            val candidates = DlcDownloadCandidate.resolve(entry)
            if (!hasMissingHostedDownloadCandidate(entry, candidates)) return
            DlcDownloadService.precheckDownloadSources(candidates, progressListener(listener))
        } catch (_: Exception) {
        }
    }

    private fun hasMissingHostedDownloadCandidate(entry: DlcRemoteEntry, candidates: List<DlcDownloadCandidate>): Boolean {
        for (candidate in candidates) {
            if (candidate.direct) continue
            val fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url)
            val target = entry.resolveTargetPath(fileName) ?: continue
            if (!Files.exists(target)) return true
        }
        return false
    }

    private fun hasMissingHostedDownloadCandidate(entry: DlcRequiredEntry, candidates: List<DlcDownloadCandidate>): Boolean {
        for (candidate in candidates) {
            if (candidate.direct) continue
            val fileName = finalizeFileName(candidate.fileNameOverride, entry, candidate.url)
            val target = entry.resolveRequiredPath(fileName) ?: continue
            if (!Files.exists(target)) return true
        }
        return false
    }

    private fun progressListener(listener: ProgressListener?): DlcDownloadService.ProgressListener? {
        if (listener == null) return null
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
    private fun checkRequiredCanceled() {
        if (requiredCancelRequested || isRequiredCanceled()) {
            throw DownloadCanceledException("canceled")
        }
    }

    private fun isRequiredCanceled(): Boolean = requiredSession?.cancelRequested?.get() == true

    @Throws(IOException::class)
    private fun verifyDownloadedFile(tempFile: Path?, candidate: DlcDownloadCandidate?) {
        val hashType = candidate?.hashType
        val hashValue = candidate?.hashValue
        if (hashType != null && hashValue != null && hashValue.isNotBlank()) {
            val algorithm = mapHashAlgorithm(hashType)
            val actual = DlcDownloadUtil.computeHashHex(tempFile, algorithm)
            if (actual == null || !actual.equals(DlcDownloadUtil.normalizeHash(hashValue), ignoreCase = true)) {
                if (tempFile != null) {
                    Files.deleteIfExists(tempFile)
                }
                throw IOException("hash mismatch")
            }
            return
        }
        val expectedSize = candidate?.expectedSize ?: -1L
        if (expectedSize > 0) {
            val size = if (tempFile == null) -1L else Files.size(tempFile)
            if (size != expectedSize) {
                if (tempFile != null) {
                    Files.deleteIfExists(tempFile)
                }
                throw IOException("size mismatch")
            }
        }
    }

    private fun mapHashAlgorithm(hashType: String?): String? {
        if (hashType == null) return null
        return when (hashType.lowercase(Locale.ROOT)) {
            "sha1", "sha-1" -> "SHA-1"
            "sha256", "sha-256" -> "SHA-256"
            "sha512", "sha-512" -> "SHA-512"
            "md5" -> "MD5"
            else -> hashType
        }
    }

    private class DownloadCanceledException(message: String?) : IOException(message)

    private fun finalizeFileName(fileName: String?, entry: DlcRemoteEntry?, url: String?): String {
        var cleaned = DlcDownloadUtil.sanitizeFileName(fileName)
        if (!cleaned.isNullOrBlank()) return cleaned
        val urlName = DlcDownloadUtil.extractFileNameFromUrl(url)
        cleaned = DlcDownloadUtil.sanitizeFileName(urlName)
        if (!cleaned.isNullOrBlank()) return cleaned
        val fallback = entry?.getFileNameHint()
        cleaned = DlcDownloadUtil.sanitizeFileName(fallback)
        if (!cleaned.isNullOrBlank()) return cleaned
        return if (entry != null) {
            when (entry.getType()) {
                DlcType.MOD -> "download.jar"
                DlcType.RESOURCE_PACK, DlcType.DATA_PACK, DlcType.TACZ_PACK, DlcType.DLC_PACK, DlcType.UNKNOWN, null -> "download.zip"
            }
        } else {
            "download.zip"
        }
    }

    private fun finalizeFileName(fileName: String?, entry: DlcRequiredEntry?, url: String?): String {
        var cleaned = DlcDownloadUtil.sanitizeFileName(fileName)
        if (!cleaned.isNullOrBlank()) return cleaned
        val urlName = DlcDownloadUtil.extractFileNameFromUrl(url)
        cleaned = DlcDownloadUtil.sanitizeFileName(urlName)
        if (!cleaned.isNullOrBlank()) return cleaned
        if (entry != null) {
            val fallback = entry.getBaseName()
            if (fallback.isNotBlank()) {
                return "$fallback.jar"
            }
        }
        return "download.jar"
    }

    private fun updateRemoteFileNameKey(entry: DlcRemoteEntry?, fileName: String?) {
        if (entry == null) return
        val normalized = DlcDownloadUtil.sanitizeFileName(fileName) ?: return
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) return
        val obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
        val reordered = reorderRemoteJson(obj, normalized)
        DlcDcConfigIO.write(config, reordered, DlcDcConfigIO.Layout.DLC_STRUCTURED)
        entry.setFileNameHint(normalized)
    }

    private fun reorderRemoteJson(obj: JsonObject, fileNameOverride: String?): JsonObject {
        val ordered = JsonObject()
        ordered.addProperty("curseforge_projectid", getString(obj, "curseforge_projectid"))
        ordered.addProperty("curseforge_fileid", getString(obj, "curseforge_fileid"))
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"))
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"))
        val directUrl = obj.get("directUrl")
        if (directUrl == null || directUrl.isJsonNull) {
            ordered.addProperty("directUrl", "")
        } else {
            ordered.add("directUrl", directUrl)
        }
        ordered.addProperty("priority", DlcDownloadCandidate.readPriority(obj))
        ordered.addProperty("identifier", getIdentifier(obj))
        ordered.addProperty("type", getString(obj, "type"))
        val fileName = if (fileNameOverride.isNullOrBlank()) getString(obj, "file_name") else fileNameOverride
        ordered.addProperty("file_name", fileName ?: "")
        ordered.addProperty("isInList", getBoolean(obj, "isInList", true))
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false))
        ordered.addProperty("AllowEnableByAll", getBoolean(obj, "AllowEnableByAll", true))
        val deps = DlcDcConfigIO.getIgnoreCase(obj, "dependencies")
        if (deps == null || deps.isJsonNull) {
            ordered.addProperty("dependencies", "")
        } else {
            ordered.add("dependencies", normalizeRelationElement(deps))
        }
        val conflicts = DlcDcConfigIO.getIgnoreCase(obj, "conflicts")
        if (conflicts == null || conflicts.isJsonNull) {
            ordered.addProperty("conflicts", "")
        } else {
            ordered.add("conflicts", normalizeRelationElement(conflicts))
        }
        val display = JsonObject()
        val displayObj = DlcDcConfigIO.getIgnoreCase(obj, "DisplayName")
        if (displayObj != null) {
            if (displayObj.isJsonObject) {
                for ((key, value) in displayObj.asJsonObject.entrySet()) {
                    if (value.isJsonPrimitive) {
                        display.addProperty(key, value.asString)
                    }
                }
            } else if (displayObj.isJsonPrimitive) {
                display.addProperty("en_us", displayObj.asString)
            }
        }
        ordered.add("DisplayName", display)
        ordered.addProperty("alarm", getBoolean(obj, "alarm", false))
        val tipObj = JsonObject()
        val tip = DlcDcConfigIO.getIgnoreCase(obj, "tip")
        if (tip != null) {
            if (tip.isJsonObject) {
                for ((key, value) in tip.asJsonObject.entrySet()) {
                    if (value.isJsonPrimitive) {
                        tipObj.addProperty(key, value.asString)
                    }
                }
            } else if (tip.isJsonPrimitive) {
                tipObj.addProperty("en_us", tip.asString)
            }
        }
        ordered.add("tip", tipObj)
        return ordered
    }

    private fun updateRequiredFileNameKey(entry: DlcRequiredEntry?, fileName: String?) {
        if (entry == null) return
        val normalized = DlcDownloadUtil.sanitizeFileName(fileName) ?: return
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) return
        val obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
        val ordered = reorderRequiredJson(obj, normalized)
        DlcDcConfigIO.write(config, ordered, DlcDcConfigIO.Layout.DLC_STRUCTURED)
    }

    private fun updateRequiredUserRemoved(entry: DlcRequiredEntry?, userRemoved: Boolean) {
        if (entry == null) return
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) return
        val obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
        if (userRemoved) {
            obj.addProperty("UserRemoved", true)
        } else {
            obj.remove("UserRemoved")
            obj.remove("userRemoved")
        }
        val ordered = reorderRequiredJson(obj, entry.getRequiredFileName())
        DlcDcConfigIO.write(config, ordered, DlcDcConfigIO.Layout.DLC_STRUCTURED)
        entry.setUserRemoved(userRemoved)
    }

    private fun reorderRequiredJson(obj: JsonObject, fileNameOverride: String?): JsonObject {
        val ordered = JsonObject()
        ordered.addProperty("curseforge_projectid", getString(obj, "curseforge_projectid"))
        ordered.addProperty("curseforge_fileid", getString(obj, "curseforge_fileid"))
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"))
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"))
        val directUrl = obj.get("directUrl")
        if (directUrl == null || directUrl.isJsonNull) {
            ordered.addProperty("directUrl", "")
        } else {
            ordered.add("directUrl", directUrl)
        }
        ordered.addProperty("priority", DlcDownloadCandidate.readPriority(obj))
        ordered.addProperty("identifier", getString(obj, "identifier"))
        val fileName = if (fileNameOverride.isNullOrBlank()) getString(obj, "file_name") else fileNameOverride
        ordered.addProperty("file_name", fileName ?: "")
        ordered.addProperty("appliedTarget", getString(obj, "appliedTarget"))
        ordered.addProperty("isInList", getBoolean(obj, "isInList", true))
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false))
        val userRemoved = getBoolean(obj, "UserRemoved", false) || getBoolean(obj, "userRemoved", false)
        if (userRemoved) {
            ordered.addProperty("UserRemoved", true)
        }
        ordered.addProperty("dependencies", getString(obj, "dependencies"))
        val display = JsonObject()
        val displayObj = obj.get("DisplayName")
        if (displayObj != null && displayObj.isJsonObject) {
            for ((key, value) in displayObj.asJsonObject.entrySet()) {
                display.addProperty(key, value.asString)
            }
        }
        ordered.add("DisplayName", display)
        val tipObj = JsonObject()
        val tip = obj.get("tip")
        if (tip != null && tip.isJsonObject) {
            for ((key, value) in tip.asJsonObject.entrySet()) {
                tipObj.addProperty(key, value.asString)
            }
        }
        ordered.add("tip", tipObj)
        return ordered
    }

    private fun getString(obj: JsonObject?, key: String?): String {
        if (obj == null || key == null) return ""
        val element = obj.get(key)
        if (element == null || element.isJsonNull) return ""
        return element.asString
    }

    private fun getIdentifier(obj: JsonObject): String {
        var identifier = getString(obj, "identifier")
        if (identifier.isBlank()) {
            identifier = getString(obj, "dlc_identifier")
        }
        return identifier
    }

    private fun normalizeRelationElement(element: JsonElement?): JsonElement {
        if (element == null || element.isJsonNull) {
            return JsonPrimitive("")
        }
        if (element.isJsonPrimitive) {
            return element.deepCopy()
        }
        if (element.isJsonArray) {
            val array = JsonArray()
            for (child in element.asJsonArray) {
                array.add(normalizeRelationElement(child))
            }
            return array
        }
        val obj = element.asJsonObject
        val normalized = JsonObject()
        for ((key, value) in obj.entrySet()) {
            var targetKey = key ?: continue
            if ("dlc_identifier".equals(key, ignoreCase = true)) {
                targetKey = "identifier"
            } else if ("dlc_name".equals(key, ignoreCase = true)) {
                targetKey = "file_name"
            }
            if (normalized.has(targetKey)) {
                continue
            }
            normalized.add(targetKey, normalizeRelationElement(value))
        }
        return normalized
    }

    private fun getBoolean(obj: JsonObject?, key: String?, fallback: Boolean): Boolean {
        if (obj == null || key == null) return fallback
        val element = obj.get(key)
        if (element == null || element.isJsonNull) return fallback
        return element.asBoolean
    }

    private fun readConfigObject(path: Path?): JsonObject? {
        if (path == null || !Files.exists(path)) {
            return null
        }
        return try {
            Files.newBufferedReader(path).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                PRETTY_GSON.fromJson(jsonReader, JsonObject::class.java)
            }
        } catch (_: IOException) {
            null
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun readLocalizedMap(obj: JsonObject?): MutableMap<String, String> {
        val values = HashMap<String, String>()
        if (obj == null) return values
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val normalizedKey = DlcDcConfigIO.normalizeLangKey(key)
                if (normalizedKey != null) {
                    values[normalizedKey] = value.asString
                }
            }
        }
        return values
    }

    private fun resolveDisplayNames(entry: DlcRemoteEntry?, fallbackName: String?): MutableMap<String, String> {
        val values = HashMap<String, String>()
        if (entry != null) {
            values.putAll(entry.getDisplayNames())
        }
        if (values.isNotEmpty()) {
            return values
        }
        val obj = readConfigObject(entry?.getConfigPath())
        if (obj != null) {
            val displayObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName")
            values.putAll(readLocalizedMap(displayObj))
            if (values.isEmpty()) {
                val display = DlcDcConfigIO.getStringIgnoreCase(obj, "DisplayName")
                if (!display.isNullOrBlank()) {
                    values["en_us"] = display
                }
            }
        }
        if (values.isEmpty() && !fallbackName.isNullOrBlank()) {
            values["en_us"] = fallbackName
        }
        return values
    }

    private fun resolveTips(entry: DlcRemoteEntry?): MutableMap<String, String> {
        val values = HashMap<String, String>()
        if (entry != null) {
            values.putAll(entry.getTips())
        }
        if (values.isNotEmpty()) {
            return values
        }
        val obj = readConfigObject(entry?.getConfigPath())
        if (obj != null) {
            val tipObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "tip")
            values.putAll(readLocalizedMap(tipObj))
            if (values.isEmpty()) {
                val tip = DlcDcConfigIO.getStringIgnoreCase(obj, "tip")
                if (!tip.isNullOrBlank()) {
                    values["en_us"] = tip
                }
            }
        }
        return values
    }

    private fun isNoticeConfig(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) {
            return false
        }
        return "notice".equals(DlcDcFormatUtil.stripConfigExtension(fileName), ignoreCase = true)
    }
}
