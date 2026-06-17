package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.ApplyMode
import com.cozooo.dlc_manager.core.DlcApplier
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Arrays
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DlcServerService {
    private val LOGGER = LogUtils.getLogger()
    private val GSON: Gson = GsonBuilder().create()
    private const val FILE_CHUNK_SIZE = 64 * 1024
    private val RUNNING = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var clientPool: ExecutorService? = null
    @Volatile
    private var currentServer: MinecraftServer? = null

    @JvmStatic
    fun start() {
        if (!DlcPathConfig.isServerSupportOnServer()) {
            LOGGER.info("[dlc-manager] server support disabled; skip server manager TCP start")
            return
        }
        if (RUNNING.get()) {
            if (serverSocket != null && !serverSocket!!.isClosed) {
                return
            }
            RUNNING.set(false)
        }
        if (RUNNING.get()) {
            return
        }
        RUNNING.set(true)
        clientPool = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "dlc-manager-server-client").apply { isDaemon = true }
        }
        acceptThread = Thread({ acceptLoop() }, "dlc-manager-server").apply {
            isDaemon = true
            start()
        }
        LOGGER.info("[dlc-manager] server manager TCP starting on port {}", DlcPathConfig.getServerPort())
    }

    @JvmStatic
    fun stop() {
        RUNNING.set(false)
        acceptThread?.interrupt()
        acceptThread = null
        serverSocket?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        serverSocket = null
        clientPool?.shutdownNow()
        clientPool = null
    }

    @JvmStatic
    fun updateServerStatus(server: MinecraftServer?) {
        if (!DlcPathConfig.isServerSupportOnServer()) {
            return
        }
        currentServer = server
        if (server == null) {
            return
        }
        val label = getServerVersionLabel()
        if (label.isBlank()) {
            return
        }
        val motd = server.motd
        val combined = if (motd.isNullOrBlank()) {
            label
        } else if (motd.contains(label)) {
            motd
        } else {
            "$motd\n$label"
        }
        DlcServerCompat.setStatusDescription(server, Component.literal(combined))
    }

    @JvmStatic
    fun getServerVersionLabel(): String {
        if (!DlcPathConfig.isServerSupportOnServer()) {
            return ""
        }
        val dlcRoot = DlcPathConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot) ?: return ""
        val base = version.getBase()
        val dlc = version.getDlc()
        if (base.isNullOrBlank() || dlc.isNullOrBlank()) {
            return ""
        }
        return "$base+$dlc"
    }

    private fun acceptLoop() {
        try {
            ServerSocket(DlcPathConfig.getServerPort()).use { socket ->
                serverSocket = socket
                while (RUNNING.get()) {
                    val client = socket.accept()
                    val pool = clientPool
                    if (pool != null) {
                        pool.submit { handleClient(client) }
                    } else {
                        client.close()
                    }
                }
            }
        } catch (error: IOException) {
            if (RUNNING.get()) {
                LOGGER.warn("[dlc-manager] server manager TCP failed", error)
            }
        } finally {
            RUNNING.set(false)
            serverSocket = null
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { client ->
                BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
                    BufferedWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)).use { writer ->
                        var authed = false
                        var clientLang: String? = null
                        val overrides = HashMap<String, Boolean>()
                        var pushState: PushState? = null
                        var uploadState: UploadState? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) {
                                continue
                            }
                            val request = GSON.fromJson(line, JsonObject::class.java)
                            if (request == null || !request.has("type")) {
                                writeError(writer, "invalid request")
                                continue
                            }
                            val type = request.get("type").asString
                            if (request.has("lang") && request.get("lang").isJsonPrimitive) {
                                clientLang = request.get("lang").asString
                            }
                            if (type == "hello") {
                                authed = handleHello(request, writer)
                                if (!authed) {
                                    break
                                }
                                continue
                            }
                            if (!authed) {
                                writeError(writer, "unauthorized")
                                continue
                            }
                            when (type) {
                                "list" -> writeList(writer)
                                "remote_list" -> handleRemoteList(writer, clientLang)
                                "remote_download_start" -> handleRemoteDownloadStart(request, writer, clientLang)
                                "remote_download_status" -> handleRemoteDownloadStatus(writer, clientLang)
                                "remote_download" -> handleRemoteDownload(writer)
                                "required_list" -> handleRequiredList(writer, clientLang)
                                "required_download_start" -> handleRequiredDownloadStart(request, writer, clientLang)
                                "required_download_status" -> handleRequiredDownloadStatus(writer, clientLang)
                                "required_download_cancel" -> handleRequiredDownloadCancel(writer)
                                "required_uninstall" -> handleRequiredUninstall(request, writer)
                                "required_apply" -> handleRequiredApply(request, writer)
                                "update_state" -> writeUpdateState(writer)
                                "update_list" -> writeUpdateList(writer)
                                "update_apply" -> handleUpdateApply(request, writer)
                                "update_rollback" -> handleUpdateRollback(request, writer)
                                "update_import" -> handleUpdateImport(request, writer)
                                "update_import_url" -> handleUpdateImportUrl(request, writer)
                                "set" -> handleSet(request, overrides, writer)
                                "apply" -> {
                                    handleApply(overrides, writer)
                                    overrides.clear()
                                }
                                "push_init" -> pushState = handlePushInit(request, writer)
                                "push_chunk" -> handlePushChunk(request, pushState, writer)
                                "push_finish" -> {
                                    handlePushFinish(pushState, writer)
                                    pushState = null
                                }
                                "file_list" -> handleFileList(request, writer)
                                "file_delete" -> handleFileDelete(request, writer)
                                "file_upload_init" -> uploadState = handleFileUploadInit(request, writer)
                                "file_upload_chunk" -> handleFileUploadChunk(request, uploadState, writer)
                                "file_upload_finish" -> {
                                    handleFileUploadFinish(uploadState, writer)
                                    uploadState = null
                                }
                                "file_download_init" -> handleFileDownload(request, writer)
                                "config_get" -> handleConfigGet(writer)
                                "config_set" -> handleConfigSet(request, writer)
                                "rcon_info" -> handleRconInfo(writer)
                                "rcon_get" -> handleRconGet(writer)
                                "rcon_set" -> handleRconSet(request, writer)
                                "rcon_exec" -> handleRconExec(request, writer)
                                "server_restart" -> handleServerRestart(writer)
                                else -> writeError(writer, "unknown request")
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
        }
    }

    @Throws(IOException::class)
    private fun handleHello(request: JsonObject, writer: BufferedWriter): Boolean {
        val expected = DlcPathConfig.getServerPassword()
        val provided = if (request.has("password")) request.get("password").asString else ""
        val player = if (request.has("player")) request.get("player").asString else ""
        val uuid = if (request.has("uuid")) request.get("uuid").asString else ""
        val passwordOk = if (expected.isNullOrBlank()) provided.isBlank() else expected == provided
        val ok = passwordOk
        val response = JsonObject()
        response.addProperty("type", "hello")
        response.addProperty("ok", ok)
        if (!ok) {
            val reason = if (!passwordOk) "password" else "op"
            response.addProperty("reason", reason)
            LOGGER.info("[dlc-manager] server manager auth failed: reason={} player={} uuid={}", reason, player, uuid)
        } else {
            LOGGER.info("[dlc-manager] server manager connected: player={} uuid={}", player, uuid)
        }
        writeLine(writer, response)
        return ok
    }

    private fun isPlayerOp(name: String?, uuid: String?): Boolean {
        val server = currentServer ?: return false
        var player: ServerPlayer? = null
        if (!uuid.isNullOrBlank()) {
            try {
                player = server.playerList.getPlayer(UUID.fromString(uuid))
            } catch (_: IllegalArgumentException) {
            }
        }
        if (player == null && !name.isNullOrBlank()) {
            player = server.playerList.getPlayerByName(name)
        }
        if (player == null) {
            return false
        }
        return server.playerList.isOp(player.gameProfile) || player.hasPermissions(4)
    }

    @Throws(IOException::class)
    private fun writeList(writer: BufferedWriter) {
        val items = ArrayList<DlcServerItem>()
        val grouped = scanDlcItems()
        for (group in grouped.values) {
            for (item in group) {
                val serverItem = DlcServerItem()
                serverItem.id = item.getId()
                serverItem.identifier = item.getIdentifier()
                serverItem.name = item.getName()
                serverItem.displayNames = item.getDisplayNames()
                serverItem.tips = item.getTooltips()
                serverItem.type = item.getType()?.name
                serverItem.enabled = item.isEnabled()
                serverItem.dependencies = item.getDependencies()
                serverItem.conflicts = item.getConflicts()
                serverItem.allowEnableByAll = item.AllowEnableByAll()
                serverItem.alarm = item.isAlarm()
                serverItem.path = resolveGroupLabel(item)
                items.add(serverItem)
            }
        }
        val dlcRoot = DlcPathConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot)
        val remoteMissing = DlcServerDownloadService.collectMissingRemote(dlcRoot).size
        val requiredMissing = DlcServerDownloadService.collectMissingRequired(dlcRoot).size
        val response = JsonObject()
        response.addProperty("type", "list")
        response.addProperty("base", version.getBase())
        response.addProperty("dlc", version.getDlc())
        response.addProperty("remoteMissing", remoteMissing)
        response.addProperty("requiredMissing", requiredMissing)
        response.addProperty("hasRequired", DlcServerDownloadService.hasRequiredEntries(dlcRoot))
        response.addProperty("modpackUrl", DlcPathConfig.getServerModpackUrl())
        response.addProperty("updateGithubRepo", DlcPathConfig.getServerUpdateGithubRepoUrl())
        response.add("items", GSON.toJsonTree(items))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownload(writer: BufferedWriter) {
        val result = DlcServerDownloadService.downloadMissingRemote(DlcPathConfig.getDlcRoot())
        val response = JsonObject()
        response.addProperty("type", "remote_download")
        response.addProperty("ok", result.success)
        response.addProperty("downloaded", result.downloadedCount)
        if (!result.success && result.error != null) {
            response.addProperty("error", result.error)
        }
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRemoteList(writer: BufferedWriter, lang: String?) {
        val response = JsonObject()
        response.addProperty("type", "remote_list")
        response.addProperty("ok", true)
        response.add("items", GSON.toJsonTree(DlcServerDownloadService.collectMissingRemoteItems(DlcPathConfig.getDlcRoot(), lang)))
        writeLine(writer, response)
    }

    private fun resolveGroupLabel(item: DlcItem): String {
        return try {
            val source = item.getSourcePath()
            var relative: String? = null
            for (dlcRoot in DlcPathConfig.getDlcRoots()) {
                if (source != null && source.startsWith(dlcRoot)) {
                    relative = dlcRoot.relativize(source).toString().replace('\\', '/')
                    break
                }
            }
            if (relative == null && source != null) {
                relative = DlcPaths.gameDir().relativize(source).toString().replace('\\', '/')
            }
            val lastSlash = relative?.lastIndexOf('/') ?: -1
            if (lastSlash <= 0) "__root__" else relative!!.substring(0, lastSlash)
        } catch (_: Exception) {
            "__root__"
        }
    }

    @Throws(IOException::class)
    private fun handleRemoteDownloadStart(request: JsonObject, writer: BufferedWriter, lang: String?) {
        val keys = readStringArray(request, "keys")
        val status: DlcServerRemoteStatus = DlcServerDownloadService.startRemoteDownload(DlcPathConfig.getDlcRoot(), keys, lang)
        val response = JsonObject()
        response.addProperty("type", "remote_download_start")
        response.addProperty("ok", true)
        response.add("status", GSON.toJsonTree(status))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownloadStatus(writer: BufferedWriter, lang: String?) {
        val status: DlcServerRemoteStatus = DlcServerDownloadService.getRemoteDownloadStatus(lang)
        val response = JsonObject()
        response.addProperty("type", "remote_download_status")
        response.addProperty("ok", true)
        response.add("status", GSON.toJsonTree(status))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredList(writer: BufferedWriter, lang: String?) {
        val items: List<DlcServerRequiredItem> = DlcServerDownloadService.collectRequiredItems(DlcPathConfig.getDlcRoot(), lang)
        val response = JsonObject()
        response.addProperty("type", "required_list")
        response.addProperty("ok", true)
        response.add("items", GSON.toJsonTree(items))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadStart(request: JsonObject, writer: BufferedWriter, lang: String?) {
        val ids = readStringArray(request, "ids")
        val status: DlcServerRequiredStatus = DlcServerDownloadService.startRequiredDownload(DlcPathConfig.getDlcRoot(), ids, lang)
        val response = JsonObject()
        response.addProperty("type", "required_download_start")
        response.addProperty("ok", true)
        response.add("status", GSON.toJsonTree(status))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadStatus(writer: BufferedWriter, lang: String?) {
        val status: DlcServerRequiredStatus = DlcServerDownloadService.getRequiredDownloadStatus(lang)
        val response = JsonObject()
        response.addProperty("type", "required_download_status")
        response.addProperty("ok", true)
        response.add("status", GSON.toJsonTree(status))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadCancel(writer: BufferedWriter) {
        val canceled = DlcServerDownloadService.cancelRequiredDownload()
        val response = JsonObject()
        response.addProperty("type", "required_download_cancel")
        response.addProperty("ok", true)
        response.addProperty("canceled", canceled)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredUninstall(request: JsonObject, writer: BufferedWriter) {
        val ids = readStringArray(request, "ids")
        val removed = DlcServerDownloadService.uninstallRequired(DlcPathConfig.getDlcRoot(), ids)
        val response = JsonObject()
        response.addProperty("type", "required_uninstall")
        response.addProperty("ok", true)
        response.addProperty("removed", removed)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredApply(request: JsonObject, writer: BufferedWriter) {
        val ids = readStringArray(request, "ids")
        val applied = DlcServerDownloadService.applyRequiredByIds(DlcPathConfig.getDlcRoot(), ids)
        val response = JsonObject()
        response.addProperty("type", "required_apply")
        response.addProperty("ok", true)
        response.addProperty("applied", applied)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleSet(request: JsonObject, overrides: MutableMap<String, Boolean>, writer: BufferedWriter) {
        val id = if (request.has("id")) request.get("id").asString else null
        val enabled = request.has("enabled") && request.get("enabled").asBoolean
        if (!id.isNullOrBlank()) {
            overrides[id] = enabled
        }
        val response = JsonObject()
        response.addProperty("type", "set")
        response.addProperty("ok", true)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleApply(overrides: Map<String, Boolean>, writer: BufferedWriter) {
        val items = ArrayList<DlcItem>()
        for (group in scanDlcItems().values) {
            items.addAll(group)
        }
        for (item in items) {
            val desired = overrides[item.getId()]
            if (desired != null) {
                item.setEnabled(desired)
            }
        }
        val result = DlcApplier.apply(items, ApplyMode.RESTART_ONLY)
        val response = JsonObject()
        response.addProperty("type", "apply")
        response.addProperty("ok", true)
        response.addProperty("requiresRestart", result.requiresRestart())
        writeLine(writer, response)
    }

    private fun scanDlcItems(): Map<DlcType, List<DlcItem>> {
        val raw = DlcScanner().scan()
        val grouped = LinkedHashMap<DlcType, List<DlcItem>>()
        for (type in DlcType.values()) {
            grouped[type] = raw[type]?.filterNotNull() ?: emptyList()
        }
        return grouped
    }

    private fun readStringArray(request: JsonObject, key: String): List<String> {
        val values = ArrayList<String>()
        if (request.has(key) && request.get(key).isJsonArray) {
            request.getAsJsonArray(key).forEach { element ->
                if (element.isJsonPrimitive) {
                    values.add(element.asString)
                }
            }
        }
        return values
    }

    @Throws(IOException::class)
    private fun writeUpdateState(writer: BufferedWriter) {
        val dlcRoot = DlcPathConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot)
        val base = version.getBase()
        val dlc = version.getDlc()
        val rollbackFrom = version.getRollbackFrom()
        val rolledBack = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, base, dlc)
        val history: List<DlcUpdateMeta?> = if (rolledBack) {
            DlcUpdateService.listUpdateHistoryFrom(rollbackFrom)
        } else {
            DlcUpdateService.listUpdateHistory()
        }
        val updates = ArrayList<DlcServerUpdateInfo>()
        if (!rolledBack) {
            val result = DlcUpdateService.checkForUpdates()
            for (pkg in result.updates) {
                val manifest = pkg?.getManifest()
                val info = manifest?.getVersion() ?: continue
                val row = DlcServerUpdateInfo()
                row.name = pkg.getName()
                row.baseOld = info.getBase()
                row.baseNew = info.getBaseNew()
                row.dlcOld = info.getDlcOld()
                row.dlcNew = info.getDlcNew()
                row.hidden = manifest.isHidden()
                row.changelog = LinkedHashMap(manifest.getChangelog())
                updates.add(row)
            }
        }
        val response = JsonObject()
        response.addProperty("type", "update_state")
        response.addProperty("ok", true)
        response.addProperty("base", base ?: "")
        response.addProperty("dlc", dlc ?: "")
        response.addProperty("modpackUrl", DlcPathConfig.getServerModpackUrl())
        response.addProperty("updateGithubRepo", DlcPathConfig.getServerUpdateGithubRepoUrl())
        if (!rollbackFrom.isNullOrBlank()) {
            response.addProperty("rollbackFrom", rollbackFrom)
        }
        response.add("history", GSON.toJsonTree(history))
        response.add("updates", GSON.toJsonTree(updates))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun writeUpdateList(writer: BufferedWriter) {
        val result = DlcUpdateService.checkForUpdates()
        val updates = ArrayList<DlcServerUpdateInfo>()
        for (pkg in result.updates) {
            val manifest = pkg?.getManifest() ?: continue
            val version = manifest.getVersion() ?: continue
            val info = DlcServerUpdateInfo()
            info.name = pkg.getName()
            info.baseOld = version.getBase()
            info.baseNew = version.getBaseNew()
            info.dlcOld = version.getDlcOld()
            info.dlcNew = version.getDlcNew()
            info.changelog = LinkedHashMap(manifest.getChangelog())
            updates.add(info)
        }
        val response = JsonObject()
        response.addProperty("type", "update_list")
        response.addProperty("ok", true)
        response.add("updates", GSON.toJsonTree(updates))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleUpdateApply(request: JsonObject, writer: BufferedWriter) {
        val baseOld = if (request.has("baseOld")) request.get("baseOld").asString else null
        val dlcOld = if (request.has("dlcOld")) request.get("dlcOld").asString else null
        val baseNew = if (request.has("baseNew")) request.get("baseNew").asString else null
        val dlcNew = if (request.has("dlcNew")) request.get("dlcNew").asString else null
        val updates: MutableList<DlcUpdatePackage?> = if (!baseOld.isNullOrBlank()
            && !dlcOld.isNullOrBlank()
            && !baseNew.isNullOrBlank()
            && !dlcNew.isNullOrBlank()
        ) {
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
        } else {
            DlcUpdateService.checkForUpdates().updates ?: mutableListOf()
        }
        val apply = DlcUpdateService.applyUpdates(updates, false)
        val response = JsonObject()
        response.addProperty("type", "update_apply")
        response.addProperty("ok", apply?.isSuccess == true)
        response.addProperty("missing", apply?.isMissing == true)
        response.addProperty("message", apply?.message ?: "")
        if (apply?.isMissing == true) {
            response.add("missing", GSON.toJsonTree(apply.missingPaths))
        }
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleUpdateRollback(request: JsonObject, writer: BufferedWriter) {
        val target = if (request.has("target")) request.get("target").asString else null
        val result = DlcUpdateService.rollbackTo(target)
        val response = JsonObject()
        response.addProperty("type", "update_rollback")
        response.addProperty("ok", result.isSuccess)
        response.addProperty("message", result.message ?: "")
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleUpdateImport(request: JsonObject, writer: BufferedWriter) {
        val root = CrossPlatformRuntime.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        val ok = target != null && DlcUpdateService.importUpdatePackage(target)
        val response = JsonObject()
        response.addProperty("type", "update_import")
        response.addProperty("ok", ok)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleUpdateImportUrl(request: JsonObject, writer: BufferedWriter) {
        val url = if (request.has("url")) request.get("url").asString else ""
        val fileName = if (request.has("fileName")) request.get("fileName").asString else ""
        var ok = false
        var message = ""
        if (url.isBlank()) {
            message = "missing_url"
        } else {
            val updateDir = DlcUpdateService.updateDir
            if (updateDir == null) {
                message = "missing_dlc_root"
            } else {
                try {
                    val downloaded = DlcDownloadService.download(
                        url,
                        updateDir,
                        fileName,
                        null as DlcDownloadService.ProgressListener?,
                        null as DlcDownloadService.CancelChecker?,
                    )
                    ok = DlcUpdateService.importUpdatePackage(downloaded)
                    if (!ok) {
                        message = "import_failed"
                    }
                } catch (_: DlcDownloadService.DownloadCanceledException) {
                    message = "canceled"
                } catch (error: Exception) {
                    message = error.message ?: "download_failed"
                }
            }
        }
        val response = JsonObject()
        response.addProperty("type", "update_import_url")
        response.addProperty("ok", ok)
        response.addProperty("message", message)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePushInit(request: JsonObject, writer: BufferedWriter): PushState? {
        val id = if (request.has("id")) request.get("id").asString else null
        val mode = if (request.has("mode")) request.get("mode").asString else "overwrite"
        val targets = readStringArray(request, "targets")
        val paths = readStringArray(request, "paths")
        val dlcRoot = DlcPathConfig.getDlcRoot()
        val tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops")
        if (tempDir == null) {
            writeError(writer, "missing temp dir")
            return null
        }
        try {
            Files.createDirectories(tempDir)
        } catch (_: IOException) {
        }
        val tempFile = tempDir.resolve("push_$id.bin")
        val state = PushState(id, mode, targets, paths, tempFile)
        val response = JsonObject()
        response.addProperty("type", "push_init")
        response.addProperty("ok", true)
        writeLine(writer, response)
        return state
    }

    @Throws(IOException::class)
    private fun handlePushChunk(request: JsonObject, state: PushState?, writer: BufferedWriter) {
        if (state == null) {
            return
        }
        val id = if (request.has("id")) request.get("id").asString else null
        if (id == null || id != state.id) {
            return
        }
        val data = if (request.has("data")) request.get("data").asString else null
        if (data.isNullOrBlank()) {
            return
        }
        val bytes = Base64.getDecoder().decode(data)
        Files.write(state.tempFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    @Throws(IOException::class)
    private fun handlePushFinish(state: PushState?, writer: BufferedWriter) {
        val ok = state != null && applyPushZip(state)
        val response = JsonObject()
        response.addProperty("type", "push_finish")
        response.addProperty("ok", ok)
        writeLine(writer, response)
    }

    private fun applyPushZip(state: PushState): Boolean {
        if (!Files.exists(state.tempFile)) {
            return false
        }
        val dlcRoot = DlcPathConfig.getDlcRoot()
        val configDir = CrossPlatformRuntime.configDir().resolve("dlc_manager")
        val gameDir = CrossPlatformRuntime.gameDir()
        val includeAll = state.targets.any { "all".equals(it, ignoreCase = true) }
        val includeDlc = includeAll || state.targets.any { "dlc".equals(it, ignoreCase = true) }
        val includeConfig = includeAll || state.targets.any { "config".equals(it, ignoreCase = true) }
        val includeFiles = state.paths.isNotEmpty()
        val mode = state.mode ?: "overwrite"
        try {
            DlcServerOps.begin(dlcRoot, gameDir, "push").use { op ->
                Files.createDirectories(configDir)
                Files.newInputStream(state.tempFile).use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (name.isNullOrBlank()) {
                                zip.closeEntry()
                                entry = zip.nextEntry
                                continue
                            }
                            var targetRoot: Path? = null
                            var relativeName: String? = null
                            if (includeDlc && name.startsWith("dlc/")) {
                                targetRoot = dlcRoot
                                relativeName = name.substring("dlc/".length)
                            } else if (includeConfig && name.startsWith("config/")) {
                                targetRoot = configDir
                                relativeName = name.substring("config/".length)
                            } else if (includeFiles && name.startsWith("root/")) {
                                targetRoot = gameDir
                                relativeName = name.substring("root/".length)
                            }
                            if (targetRoot == null || relativeName.isNullOrBlank()) {
                                zip.closeEntry()
                                entry = zip.nextEntry
                                continue
                            }
                            val target = safeResolve(targetRoot, relativeName)
                            if (target == null) {
                                zip.closeEntry()
                                entry = zip.nextEntry
                                continue
                            }
                            if (entry.isDirectory) {
                                try {
                                    Files.createDirectories(target)
                                } catch (_: IOException) {
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                                continue
                            }
                            val exists = Files.exists(target)
                            if (exists && "diff".equals(mode, ignoreCase = true)) {
                                drainEntry(zip)
                                op.log("SKIP ${op.displayPath(target)}")
                                zip.closeEntry()
                                entry = zip.nextEntry
                                continue
                            }
                            if (exists) {
                                op.backupIfExists(target)
                            }
                            try {
                                Files.createDirectories(target.parent)
                                writeEntry(zip, target)
                                op.log("WRITE ${op.displayPath(target)}")
                            } catch (_: IOException) {
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            }
        } catch (_: IOException) {
            return false
        } finally {
            try {
                Files.deleteIfExists(state.tempFile)
            } catch (_: IOException) {
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun handleFileList(request: JsonObject, writer: BufferedWriter) {
        val root = CrossPlatformRuntime.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        if (target == null) {
            writeError(writer, "invalid path")
            return
        }
        val entries = JsonArray()
        if (Files.exists(target) && Files.isDirectory(target)) {
            try {
                Files.list(target).use { stream ->
                    stream.forEach { path ->
                        val entry = JsonObject()
                        entry.addProperty("name", path.fileName.toString())
                        entry.addProperty("dir", Files.isDirectory(path))
                        try {
                            entry.addProperty("size", if (Files.isDirectory(path)) 0L else Files.size(path))
                        } catch (_: IOException) {
                            entry.addProperty("size", 0L)
                        }
                        try {
                            entry.addProperty("modified", Files.getLastModifiedTime(path).toMillis())
                        } catch (_: IOException) {
                            entry.addProperty("modified", 0L)
                        }
                        entries.add(entry)
                    }
                }
            } catch (_: IOException) {
            }
        }
        val response = JsonObject()
        response.addProperty("type", "file_list")
        response.addProperty("ok", true)
        response.addProperty("path", raw)
        response.add("entries", entries)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleFileDelete(request: JsonObject, writer: BufferedWriter) {
        val root = CrossPlatformRuntime.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        if (target == null || !Files.exists(target)) {
            writeError(writer, "invalid path")
            return
        }
        val dlcRoot = DlcPathConfig.getDlcRoot()
        DlcServerOps.begin(dlcRoot, root, "delete").use { op ->
            op.backupIfExists(target)
            op.deleteRecursive(target)
            val response = JsonObject()
            response.addProperty("type", "file_delete")
            response.addProperty("ok", true)
            writeLine(writer, response)
        }
    }

    @Throws(IOException::class)
    private fun handleFileUploadInit(request: JsonObject, writer: BufferedWriter): UploadState? {
        val id = if (request.has("id")) request.get("id").asString else null
        val raw = if (request.has("path")) request.get("path").asString else ""
        val root = CrossPlatformRuntime.gameDir()
        val target = safeResolve(root, raw)
        if (target == null) {
            writeError(writer, "invalid path")
            return null
        }
        val dlcRoot = DlcPathConfig.getDlcRoot()
        val tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops")
        if (tempDir == null) {
            writeError(writer, "missing temp dir")
            return null
        }
        try {
            Files.createDirectories(tempDir)
        } catch (_: IOException) {
        }
        val tempFile = tempDir.resolve("upload_$id.bin")
        val state = UploadState(id, tempFile, target)
        val response = JsonObject()
        response.addProperty("type", "file_upload_init")
        response.addProperty("ok", true)
        writeLine(writer, response)
        return state
    }

    @Throws(IOException::class)
    private fun handleFileUploadChunk(request: JsonObject, state: UploadState?, writer: BufferedWriter) {
        if (state == null) {
            return
        }
        val id = if (request.has("id")) request.get("id").asString else null
        if (id == null || id != state.id) {
            return
        }
        val data = if (request.has("data")) request.get("data").asString else null
        if (data.isNullOrBlank()) {
            return
        }
        val bytes = Base64.getDecoder().decode(data)
        Files.write(state.tempFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    @Throws(IOException::class)
    private fun handleFileUploadFinish(state: UploadState?, writer: BufferedWriter) {
        var ok = false
        if (state != null) {
            val root = CrossPlatformRuntime.gameDir()
            val dlcRoot = DlcPathConfig.getDlcRoot()
            try {
                DlcServerOps.begin(dlcRoot, root, "upload").use { op ->
                    val target = state.targetPath
                    if (Files.exists(target)) {
                        op.backupIfExists(target)
                    }
                    Files.createDirectories(target.parent)
                    Files.move(state.tempFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    op.log("UPLOAD ${op.displayPath(target)}")
                    ok = true
                }
            } catch (_: IOException) {
                ok = false
            } finally {
                try {
                    Files.deleteIfExists(state.tempFile)
                } catch (_: IOException) {
                }
            }
        }
        val response = JsonObject()
        response.addProperty("type", "file_upload_finish")
        response.addProperty("ok", ok)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleFileDownload(request: JsonObject, writer: BufferedWriter) {
        val root = CrossPlatformRuntime.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        if (target == null || !Files.exists(target)) {
            writeError(writer, "invalid path")
            return
        }
        val isDir = Files.isDirectory(target)
        val dlcRoot = DlcPathConfig.getDlcRoot()
        var sendFile = target
        var tempZip: Path? = null
        if (isDir) {
            val tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops")
            if (tempDir == null) {
                writeError(writer, "zip failed")
                return
            }
            try {
                Files.createDirectories(tempDir)
            } catch (_: IOException) {
            }
            tempZip = tempDir.resolve("download_${System.currentTimeMillis()}.zip")
            try {
                zipDirectory(target, tempZip)
                sendFile = tempZip
            } catch (_: IOException) {
                writeError(writer, "zip failed")
                return
            }
        }
        val size = try {
            Files.size(sendFile)
        } catch (_: IOException) {
            writeError(writer, "invalid path")
            return
        }
        DlcServerOps.begin(dlcRoot, root, "download").use { op ->
            op.log("DOWNLOAD ${op.displayPath(target)}")
        }
        val init = JsonObject()
        init.addProperty("type", "file_download_init")
        init.addProperty("ok", true)
        init.addProperty("name", target.fileName.toString())
        init.addProperty("size", size)
        init.addProperty("dir", isDir)
        writeLine(writer, init)
        try {
            Files.newInputStream(sendFile).use { input ->
                val buffer = ByteArray(FILE_CHUNK_SIZE)
                var len = input.read(buffer)
                while (len >= 0) {
                    val chunk = JsonObject()
                    chunk.addProperty("type", "file_download_chunk")
                    val data = Base64.getEncoder()
                        .encodeToString(if (len == buffer.size) buffer else Arrays.copyOf(buffer, len))
                    chunk.addProperty("data", data)
                    writeLine(writer, chunk)
                    len = input.read(buffer)
                }
            }
        } catch (_: IOException) {
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip)
                } catch (_: IOException) {
                }
            }
        }
        val finish = JsonObject()
        finish.addProperty("type", "file_download_finish")
        finish.addProperty("ok", true)
        writeLine(writer, finish)
    }

    @Throws(IOException::class)
    private fun zipDirectory(sourceDir: Path, zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { zip ->
            Files.walk(sourceDir).use { stream ->
                stream.filter { path -> !Files.isDirectory(path) }.forEach { path ->
                    val relative = sourceDir.relativize(path)
                    val entry = ZipEntry(relative.toString().replace("\\", "/"))
                    try {
                        Files.newInputStream(path).use { input ->
                            zip.putNextEntry(entry)
                            val buffer = ByteArray(FILE_CHUNK_SIZE)
                            var len = input.read(buffer)
                            while (len >= 0) {
                                zip.write(buffer, 0, len)
                                len = input.read(buffer)
                            }
                            zip.closeEntry()
                        }
                    } catch (_: IOException) {
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun handleRconGet(writer: BufferedWriter) {
        val response = JsonObject()
        response.addProperty("type", "rcon_get")
        response.addProperty("ok", true)
        response.addProperty("host", DlcPathConfig.getServerRconHost())
        response.addProperty("port", DlcPathConfig.getServerRconPort())
        response.addProperty("password", DlcPathConfig.getServerRconPassword())
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRconInfo(writer: BufferedWriter) {
        val info = DlcServerProperties.readRconInfo()
        val response = JsonObject()
        response.addProperty("type", "rcon_info")
        response.addProperty("ok", true)
        response.addProperty("enabled", info.enabled)
        response.addProperty("port", info.port)
        response.addProperty("passwordSet", info.passwordSet)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRconSet(request: JsonObject, writer: BufferedWriter) {
        val host = if (request.has("host")) request.get("host").asString else ""
        val port = if (request.has("port")) request.get("port").asInt else DlcPathConfig.getServerRconPort()
        val password = if (request.has("password")) request.get("password").asString else ""
        DlcPathConfig.setServerRconHost(host)
        DlcPathConfig.setServerRconPort(port)
        DlcPathConfig.setServerRconPassword(password)
        DlcPathConfig.saveServer()
        val response = JsonObject()
        response.addProperty("type", "rcon_set")
        response.addProperty("ok", true)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleConfigGet(writer: BufferedWriter) {
        val response = JsonObject()
        response.addProperty("type", "config_get")
        response.addProperty("ok", true)
        response.add("config", DlcPathConfig.buildServerConfigJson())
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleConfigSet(request: JsonObject, writer: BufferedWriter) {
        var ok = false
        if (request.has("config") && request.get("config").isJsonObject) {
            val config = request.getAsJsonObject("config")
            DlcPathConfig.applyServerConfigJson(config)
            DlcPathConfig.saveServer()
            ok = true
        }
        val response = JsonObject()
        response.addProperty("type", "config_set")
        response.addProperty("ok", ok)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRconExec(request: JsonObject, writer: BufferedWriter) {
        val command = if (request.has("command")) request.get("command").asString else ""
        var message: String?
        var ok = false
        val host = DlcPathConfig.getServerRconHost()
        val password = DlcPathConfig.getServerRconPassword()
        val port = DlcPathConfig.getServerRconPort()
        if (!host.isNullOrBlank() && !password.isNullOrBlank()) {
            message = DlcRconClient.execute(host, port, password, command, 5000)
            ok = message != null
        } else {
            ok = executeServerCommand(command)
            message = if (ok) "ok" else "failed"
        }
        val response = JsonObject()
        response.addProperty("type", "rcon_exec")
        response.addProperty("ok", ok)
        response.addProperty("message", message ?: "")
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleServerRestart(writer: BufferedWriter) {
        val server = currentServer
        var ok = false
        if (server != null) {
            LOGGER.info("[dlc-manager] Applying required resources/DLC, restarting server...")
            server.halt(false)
            ok = true
        }
        val response = JsonObject()
        response.addProperty("type", "server_restart")
        response.addProperty("ok", ok)
        writeLine(writer, response)
    }

    private fun executeServerCommand(command: String): Boolean {
        return DlcServerCompat.executeCommand(currentServer, command)
    }

    private fun safeResolve(root: Path?, raw: String?): Path? {
        if (root == null) {
            return null
        }
        val value = (raw ?: "").replace('\\', '/')
        var resolved = if (value.isBlank()) root else root.resolve(value)
        resolved = resolved.normalize()
        if (!resolved.startsWith(root.normalize())) {
            return null
        }
        return resolved
    }

    @Throws(IOException::class)
    private fun writeEntry(zip: InputStream, target: Path) {
        Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
            val buffer = ByteArray(8192)
            var len = zip.read(buffer)
            while (len > 0) {
                output.write(buffer, 0, len)
                len = zip.read(buffer)
            }
        }
    }

    @Throws(IOException::class)
    private fun drainEntry(zip: InputStream) {
        val buffer = ByteArray(8192)
        while (zip.read(buffer) > 0) {
        }
    }

    @Throws(IOException::class)
    private fun writeError(writer: BufferedWriter, message: String) {
        val response = JsonObject()
        response.addProperty("type", "error")
        response.addProperty("ok", false)
        response.addProperty("message", message)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun writeLine(writer: BufferedWriter, obj: JsonObject) {
        writer.write(GSON.toJson(obj))
        writer.newLine()
        writer.flush()
    }

    private class PushState(
        val id: String?,
        val mode: String?,
        targets: List<String>?,
        paths: List<String>?,
        val tempFile: Path,
    ) {
        val targets: List<String> = targets ?: emptyList()
        val paths: List<String> = paths ?: emptyList()
    }

    private class UploadState(
        val id: String?,
        val tempFile: Path,
        val targetPath: Path,
    )
}
