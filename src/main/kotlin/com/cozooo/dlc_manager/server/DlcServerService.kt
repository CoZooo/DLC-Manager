package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.pack.DlcPackArchive
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller
import com.cozooo.dlc_manager.client.pack.DlcPackMeta
import com.cozooo.dlc_manager.client.pack.DlcPackOrder
import com.cozooo.dlc_manager.client.pack.DlcPackPackage
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.server.DlcServerPackApplyStatus
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo
import com.cozooo.dlc_manager.client.server.DlcServerPresetItem
import com.cozooo.dlc_manager.client.server.DlcServerRedirectItem
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.DlcApplyMode
import com.cozooo.dlc_manager.core.DlcApplier
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcRuntimeState
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.DlcPlatformServices
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
import java.util.Comparator
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DlcServerService {
    private val LOGGER = LogUtils.getLogger()
    private val GSON: Gson = GsonBuilder().create()
    private const val FILE_CHUNK_SIZE = 64 * 1024
    private val RUNNING = AtomicBoolean(false)
    private val LOGGED_CONNECTION_KEYS = ConcurrentHashMap.newKeySet<String>()
    private val PACK_APPLY_LOCK = Any()
    @Volatile
    private var packApplyJob: PackApplyJob? = null
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var clientPool: ExecutorService? = null
    @Volatile
    private var currentServer: MinecraftServer? = null

    @JvmStatic
    fun start() {
        if (!DlcConfig.isServerSupportOnServer()) {
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
        LOGGER.info("[dlc-manager] server manager TCP starting on port {}", DlcConfig.getServerPort())
    }

    @JvmStatic
    fun stop() {
        RUNNING.set(false)
        LOGGED_CONNECTION_KEYS.clear()
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
        if (!DlcConfig.isServerSupportOnServer()) {
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
        DlcServerCompat.setStatusDescription(server, DlcTextCompat.literal(combined))
    }

    @JvmStatic
    fun getServerVersionLabel(): String {
        if (!DlcConfig.isServerSupportOnServer()) {
            return ""
        }
        val dlcRoot = DlcConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot)
        val base = version.getBase()
        val dlc = version.getDlc()
        if (base.isNullOrBlank() || dlc.isNullOrBlank()) {
            return ""
        }
        return "$base+$dlc"
    }

    private fun acceptLoop() {
        try {
            ServerSocket(DlcConfig.getServerPort()).use { socket ->
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
                                authed = handleHello(request, writer, client.inetAddress?.hostAddress ?: "")
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
                                "list" -> writeList(writer, clientLang)
                                "remote_list" -> handleRemoteList(writer, clientLang)
                                "remote_download_start" -> handleRemoteDownloadStart(request, writer, clientLang)
                                "remote_download_status" -> handleRemoteDownloadStatus(writer, clientLang)
                                "remote_download_cancel" -> handleRemoteDownloadCancel(writer)
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
                                "pack_list" -> handlePackList(writer, clientLang)
                                "pack_install" -> handlePackInstall(request, writer)
                                "pack_uninstall" -> handlePackUninstall(request, writer)
                                "pack_delete" -> handlePackDelete(request, writer)
                                "pack_import" -> handlePackImport(request, writer)
                                "pack_import_url" -> handlePackImportUrl(request, writer)
                                "pack_order" -> handlePackOrder(request, writer)
                                "pack_apply_start" -> handlePackApplyStart(request, writer)
                                "pack_apply_status" -> handlePackApplyStatus(writer)
                                "pack_apply_pause" -> handlePackApplyPause(writer)
                                "pack_apply_resume" -> handlePackApplyResume(writer)
                                "pack_apply_cancel" -> handlePackApplyCancel(writer)
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
    private fun handleHello(request: JsonObject, writer: BufferedWriter, remoteAddress: String): Boolean {
        val expected = DlcConfig.getServerPassword()
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
            logConnectedOnce(player, uuid, remoteAddress)
        }
        writeLine(writer, response)
        return ok
    }

    private fun logConnectedOnce(player: String, uuid: String, remoteAddress: String) {
        val key = connectionLogKey(player, uuid, remoteAddress)
        if (LOGGED_CONNECTION_KEYS.add(key)) {
            LOGGER.info("[dlc-manager] server manager connected: player={} uuid={}", player, uuid)
        }
    }

    private fun connectionLogKey(player: String, uuid: String, remoteAddress: String): String {
        val normalizedUuid = uuid.trim()
        if (normalizedUuid.isNotEmpty()) {
            return "uuid:$normalizedUuid"
        }
        val normalizedPlayer = player.trim()
        if (normalizedPlayer.isNotEmpty()) {
            return "player:$normalizedPlayer"
        }
        val normalizedRemote = remoteAddress.trim()
        return if (normalizedRemote.isNotEmpty()) "remote:$normalizedRemote" else "unknown"
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
        //? if >=26.1 {
        //return false
        //?} else {
        return server.playerList.isOp(player.gameProfile) || player.hasPermissions(4)
        //?}
    }

    @Throws(IOException::class)
    private fun writeList(writer: BufferedWriter, lang: String?) {
        val items = ArrayList<DlcServerItem>()
        val effectiveLang = if (lang.isNullOrBlank()) DlcPlatformServices.languageCode() else lang
        for (item in scanDlcItemsForServer(true)) {
            items.add(toServerItem(item, effectiveLang))
        }
        val dlcRoot = DlcConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot)
        val remoteMissing = DlcServerDownloadService.collectMissingRemote(dlcRoot).size
        val requiredMissing = DlcServerDownloadService.collectMissingRequired(dlcRoot).size
        val response = JsonObject()
        response.addProperty("type", "list")
        response.addProperty("base", version.getBase())
        response.addProperty("dlc", version.getDlc())
        response.addProperty("mcVersion", DlcPlatformServices.mcVersion())
        response.addProperty("loaderName", DlcPlatformServices.loaderName())
        response.addProperty("remoteMissing", remoteMissing)
        response.addProperty("requiredMissing", requiredMissing)
        response.addProperty("hasRequired", DlcServerDownloadService.hasRequiredEntries(dlcRoot))
        response.addProperty("hasTaczMod", DlcPlatformServices.isModLoaded("tacz"))
        response.addProperty("modpackUrl", DlcConfig.getServerModpackUrl())
        response.addProperty("updateGitRepo", DlcConfig.getServerUpdateGitRepoUrl())
        response.add("redirects", GSON.toJsonTree(loadVisibleRedirectItems(dlcRoot, effectiveLang)))
        response.add("presets", GSON.toJsonTree(toServerPresetItems()))
        response.add("items", GSON.toJsonTree(items))
        writeLine(writer, response)
    }

    private fun toServerPresetItems(): List<DlcServerPresetItem> {
        val out = ArrayList<DlcServerPresetItem>()
        for (preset in DlcPresetRuntimeState.getPresets()) {
            out.add(DlcServerPresetItem.fromPreset(preset))
        }
        return out
    }

    private fun scanDlcItemsForServer(detectInstalledState: Boolean): List<DlcItem> {
        val grouped = DlcScanner().scan(detectInstalledState)
        val items = ArrayList<DlcItem>()
        for (type in DlcType.values()) {
            val group = grouped[type] ?: continue
            for (item in group) {
                if (item != null) {
                    items.add(item)
                }
            }
        }
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.reload()
        } else {
            DlcPresetRuntimeState.clearAll()
        }
        for (item in items) {
            DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled())
            val override = DlcRuntimeState.getOverride(item.getId())
            if (override != null) {
                item.setEnabled(override)
            }
        }
        DlcRuntimeState.updateItemIndex(items)
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.applyOverridesToRuntime()
        }
        for (item in items) {
            val override = DlcRuntimeState.getOverride(item.getId())
            if (override != null) {
                item.setEnabled(override)
            }
        }
        return items
    }

    private fun toServerItem(item: DlcItem, lang: String?): DlcServerItem {
        val serverItem = DlcServerItem()
        serverItem.id = item.getId()
        serverItem.identifier = item.getIdentifier()
        serverItem.name = item.getName()
        serverItem.displayNames = item.getDisplayNames()
        serverItem.tips = item.getTooltips()
        serverItem.type = item.getType()?.name
        serverItem.enabled = item.isEnabled()
        serverItem.baselineEnabled = DlcRuntimeState.getBaselineEnabled(item.getId()) ?: item.isEnabled()
        serverItem.dependencies = item.getDependencies()
        serverItem.conflicts = item.getConflicts()
        serverItem.allowEnableByAll = item.AllowEnableByAll()
        serverItem.alarm = item.isAlarm()
        serverItem.hidden = item.isHidden()
        serverItem.configType = item.isConfigType()
        serverItem.appliedTargetRaw = item.getAppliedTargetRaw()
        serverItem.redirectUrl = item.getRedirectUrl()
        serverItem.presetBlocked = DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
        serverItem.presetBlockers = DlcPresetRuntimeState.getDlcBlockerNames(item.getIdentifier(), lang)
        serverItem.path = resolveGroupLabel(item)
        return serverItem
    }

    private fun loadVisibleRedirectItems(dlcRoot: Path?, lang: String?): List<DlcServerRedirectItem> {
        if (dlcRoot == null) {
            return emptyList()
        }
        val out = ArrayList<DlcServerRedirectItem>()
        for (entry in DlcRedirectLoader.load(dlcRoot)) {
            if (entry.isHidden() || !entry.isAvailableFor(lang)) {
                continue
            }
            out.add(toServerRedirectItem(entry))
        }
        return out
    }

    private fun toServerRedirectItem(entry: DlcRedirectEntry): DlcServerRedirectItem {
        val item = DlcServerRedirectItem()
        item.redirectUrl = entry.getRedirectUrl()
        item.fallbackName = entry.getFallbackName()
        item.displayNames = entry.getDisplayNames()
        item.notices = entry.getNotices()
        item.tips = entry.getTips()
        return item
    }

    @Throws(IOException::class)
    private fun handleRemoteDownload(writer: BufferedWriter) {
        val result = DlcServerDownloadService.downloadMissingRemote(DlcConfig.getDlcRoot())
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
        response.add("items", GSON.toJsonTree(DlcServerDownloadService.collectRemoteItems(DlcConfig.getDlcRoot(), lang)))
        writeLine(writer, response)
    }

    private fun resolveGroupLabel(item: DlcItem): String {
        return try {
            val source = item.getSourcePath()
            var relative: String? = null
            for (dlcRoot in DlcConfig.getDlcRoots()) {
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
        val status: DlcServerRemoteStatus = DlcServerDownloadService.startRemoteDownload(DlcConfig.getDlcRoot(), keys, lang)
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
    private fun handleRemoteDownloadCancel(writer: BufferedWriter) {
        val canceled = DlcServerDownloadService.cancelRemoteDownload()
        val response = JsonObject()
        response.addProperty("type", "remote_download_cancel")
        response.addProperty("ok", true)
        response.addProperty("canceled", canceled)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredList(writer: BufferedWriter, lang: String?) {
        val items: List<DlcServerRequiredItem> = DlcServerDownloadService.collectRequiredItems(DlcConfig.getDlcRoot(), lang)
        val response = JsonObject()
        response.addProperty("type", "required_list")
        response.addProperty("ok", true)
        response.add("items", GSON.toJsonTree(items))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadStart(request: JsonObject, writer: BufferedWriter, lang: String?) {
        val ids = readStringArray(request, "ids")
        val status: DlcServerRequiredStatus = DlcServerDownloadService.startRequiredDownload(DlcConfig.getDlcRoot(), ids, lang)
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
        val removed = DlcServerDownloadService.uninstallRequired(DlcConfig.getDlcRoot(), ids)
        val response = JsonObject()
        response.addProperty("type", "required_uninstall")
        response.addProperty("ok", true)
        response.addProperty("removed", removed)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleRequiredApply(request: JsonObject, writer: BufferedWriter) {
        val ids = readStringArray(request, "ids")
        val applied = DlcServerDownloadService.applyRequiredByIds(DlcConfig.getDlcRoot(), ids)
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
        val items = scanDlcItemsForServer(true)
        for (item in items) {
            val desired = overrides[item.getId()]
            if (desired != null) {
                item.setEnabled(desired)
            }
        }
        val result = DlcApplier.apply(items, DlcApplyMode.RESTART_ONLY)
        for (item in items) {
            if (item.getId() == null) {
                continue
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled())
            DlcRuntimeState.clearOverride(item.getId())
        }
        val response = JsonObject()
        response.addProperty("type", "apply")
        response.addProperty("ok", true)
        response.addProperty("requiresRestart", result.requiresRestart())
        val messages = JsonArray()
        for (msg in result.messages()) {
            if (!msg.isNullOrBlank()) {
                messages.add(msg)
            }
        }
        response.add("messages", messages)
        writeLine(writer, response)
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
        val dlcRoot = DlcConfig.getDlcRoot()
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
        response.addProperty("modpackUrl", DlcConfig.getServerModpackUrl())
        response.addProperty("updateGitRepo", DlcConfig.getServerUpdateGitRepoUrl())
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
            DlcUpdateService.checkForUpdates().updates
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
        val root = DlcPlatformServices.gameDir()
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
    private fun handlePackList(writer: BufferedWriter, lang: String?) {
        val response = JsonObject()
        response.addProperty("type", "pack_list")
        response.addProperty("ok", true)
        response.add("packs", GSON.toJsonTree(scanServerPackArchives(lang)))
        response.add("order", GSON.toJsonTree(DlcPackOrder.load(packOrderPath())))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackOrder(request: JsonObject, writer: BufferedWriter) {
        val order = ArrayList<String>()
        if (request.has("order") && request.get("order").isJsonArray) {
            for (element in request.getAsJsonArray("order")) {
                if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    order.add(element.asString)
                }
            }
        }
        val ok = DlcPackOrder.save(packOrderPath(), order)
        val response = JsonObject()
        response.addProperty("type", "pack_order")
        response.addProperty("ok", ok)
        response.addProperty("message", if (ok) "saved" else "save_failed")
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackInstall(request: JsonObject, writer: BufferedWriter) {
        val archivePath = if (request.has("archivePath")) request.get("archivePath").asString else ""
        val result = installServerPack(archivePath)
        val response = JsonObject()
        response.addProperty("type", "pack_install")
        response.addProperty("ok", result.ok)
        response.addProperty("message", result.message)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackUninstall(request: JsonObject, writer: BufferedWriter) {
        val identifier = if (request.has("identifier")) request.get("identifier").asString else ""
        val result = uninstallServerPack(identifier)
        val response = JsonObject()
        response.addProperty("type", "pack_uninstall")
        response.addProperty("ok", result.ok)
        response.addProperty("message", result.message)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackDelete(request: JsonObject, writer: BufferedWriter) {
        val archivePath = if (request.has("archivePath")) request.get("archivePath").asString else ""
        val result = deleteServerPack(archivePath)
        val response = JsonObject()
        response.addProperty("type", "pack_delete")
        response.addProperty("ok", result.ok)
        response.addProperty("message", result.message)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackImport(request: JsonObject, writer: BufferedWriter) {
        val root = DlcPlatformServices.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        val ok = target != null && Files.isRegularFile(target) && target.startsWith(packArchiveRoot())
        val response = JsonObject()
        response.addProperty("type", "pack_import")
        response.addProperty("ok", ok)
        response.addProperty("message", if (ok) "" else "invalid pack archive")
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackImportUrl(request: JsonObject, writer: BufferedWriter) {
        val url = if (request.has("url")) request.get("url").asString else ""
        val fileName = if (request.has("fileName")) request.get("fileName").asString else ""
        var ok = false
        var message = ""
        if (url.isBlank()) {
            message = "missing_url"
        } else {
            try {
                val packRoot = packArchiveRoot()
                Files.createDirectories(packRoot)
                val downloaded = DlcDownloadService.download(
                    url,
                    packRoot,
                    fileName,
                    null as DlcDownloadService.ProgressListener?,
                    null as DlcDownloadService.CancelChecker?,
                )
                ok = Files.isRegularFile(downloaded)
                if (!ok) {
                    message = "download_failed"
                }
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                message = "canceled"
            } catch (error: Exception) {
                message = error.message ?: "download_failed"
            }
        }
        val response = JsonObject()
        response.addProperty("type", "pack_import_url")
        response.addProperty("ok", ok)
        response.addProperty("message", message)
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackApplyStart(request: JsonObject, writer: BufferedWriter) {
        val order = readStringArray(request, "order")
        val uninstallIdentifiers = readStringArray(request, "uninstallIdentifiers")
        val installArchives = readStringArray(request, "installArchives")
        val status = startPackApplyJob(order, uninstallIdentifiers, installArchives)
        val response = JsonObject()
        response.addProperty("type", "pack_apply_start")
        response.addProperty("ok", true)
        response.add("status", GSON.toJsonTree(status))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackApplyStatus(writer: BufferedWriter) {
        val response = JsonObject()
        response.addProperty("type", "pack_apply_status")
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackApplyPause(writer: BufferedWriter) {
        packApplyJob?.pause()
        val response = JsonObject()
        response.addProperty("type", "pack_apply_pause")
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackApplyResume(writer: BufferedWriter) {
        packApplyJob?.resume()
        val response = JsonObject()
        response.addProperty("type", "pack_apply_resume")
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()))
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handlePackApplyCancel(writer: BufferedWriter) {
        packApplyJob?.cancel()
        val response = JsonObject()
        response.addProperty("type", "pack_apply_cancel")
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()))
        writeLine(writer, response)
    }

    private fun startPackApplyJob(order: List<String>, uninstallIdentifiers: List<String>, installArchives: List<String>): DlcServerPackApplyStatus {
        synchronized(PACK_APPLY_LOCK) {
            val existing = packApplyJob
            if (existing != null && existing.isRunning()) {
                return existing.toStatus()
            }
            val job = PackApplyJob(order, uninstallIdentifiers, installArchives)
            packApplyJob = job
            Thread({ job.run() }, "dlc-server-pack-apply").apply {
                isDaemon = true
                start()
            }
            return job.toStatus()
        }
    }

    private fun currentPackApplyStatus(): DlcServerPackApplyStatus {
        return packApplyJob?.toStatus() ?: DlcServerPackApplyStatus().apply {
            finished = true
            success = true
            totalCount = 0
            completedCount = 0
            progress = 1f
        }
    }

    private fun scanServerPackArchives(lang: String?): List<DlcServerPackInfo> {
        val packRoot = packArchiveRoot()
        try {
            Files.createDirectories(packRoot)
        } catch (_: IOException) {
        }
        val installed = loadInstalledPacks()
        val out = ArrayList<DlcServerPackInfo>()
        try {
            DlcPackArchive.findArchives(DlcConfig.getDlcRoot()).stream().use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { it.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip") }
                    .sorted(Comparator.comparing { it.fileName.toString().lowercase(Locale.ROOT) })
                    .forEach { path ->
                        inspectServerPackArchive(path, packRoot, installed, lang)?.let(out::add)
                    }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun inspectServerPackArchive(archive: Path, packRoot: Path, installed: Map<String, DlcPackMeta>, lang: String?): DlcServerPackInfo? {
        var staging: Path? = null
        return try {
            staging = DlcPackArchive.extractToStaging(archive, DlcConfig.getDlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            if (!result.valid || result.pack == null) {
                return null
            }
            val pack = result.pack
            val relative = packRoot.relativize(archive.toAbsolutePath().normalize()).toString().replace('\\', '/')
            val info = DlcServerPackInfo()
            info.archivePath = relative
            info.orderKey = relative + "#" + pack.metadata.identifier + "@" + pack.metadata.version
            info.fileName = archive.fileName.toString()
            info.identifier = pack.metadata.identifier
            info.version = pack.metadata.version
            info.displayName = pack.metadata.resolveDisplayName(lang)
            info.description = pack.metadata.resolveDescription(lang)
            info.authors = ArrayList(pack.metadata.authors)
            info.links = LinkedHashMap(pack.metadata.links)
            info.iconBase64 = readPackIconBase64(pack.iconFile)
            info.format = pack.format.name
            val meta = installed[info.identifier]
            info.installed = meta != null && info.version == meta.version
            info.installedVersion = meta?.version ?: ""
            info
        } catch (_: Exception) {
            null
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
    }

    private fun installServerPack(
        archivePath: String?,
        progressListener: DlcPackInstaller.ProgressListener? = null,
        cancelChecker: DlcDownloadService.CancelChecker? = null,
    ): UpdatePackActionResult {
        val archive = resolvePackArchive(archivePath)
        if (archive == null || !Files.isRegularFile(archive)) {
            return UpdatePackActionResult.fail("invalid pack archive")
        }
        var staging: Path? = null
        return try {
            staging = DlcPackArchive.extractToStaging(archive, DlcConfig.getDlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            if (!result.valid || result.pack == null) {
                return UpdatePackActionResult.fail(result.error ?: "invalid DLC Pack")
            }
            val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcConfig.getDlcRoot())
            val precheck = installer.precheck(result.pack)
            if (precheck.hasBlockingIssues()) {
                return UpdatePackActionResult.fail(describePackPrecheck(precheck))
            }
            val replace = loadInstalledPacks().containsKey(result.pack.metadata.identifier)
            val options = if (precheck.requiresRuntimeConfirmation()) {
                DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges()
            } else {
                DlcPackInstaller.InstallOptions.strict()
            }.withProgressListener(progressListener).withCancelChecker(cancelChecker)
            installer.install(result.pack, replace, options)
            UpdatePackActionResult.ok("installed")
        } catch (error: Exception) {
            UpdatePackActionResult.fail(error.message ?: error.javaClass.simpleName)
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
    }

    private fun uninstallServerPack(identifier: String?, progressListener: DlcPackInstaller.ProgressListener? = null): UpdatePackActionResult {
        if (identifier.isNullOrBlank()) {
            return UpdatePackActionResult.fail("missing identifier")
        }
        return try {
            DlcPackInstaller(DlcPaths.gameDir(), DlcConfig.getDlcRoot()).uninstall(identifier, progressListener)
            UpdatePackActionResult.ok("uninstalled")
        } catch (error: Exception) {
            UpdatePackActionResult.fail(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun deleteServerPack(archivePath: String?): UpdatePackActionResult {
        val archive = resolvePackArchive(archivePath)
        if (archive == null || !Files.isRegularFile(archive)) {
            return UpdatePackActionResult.fail("invalid pack archive")
        }
        var identifier: String? = null
        var staging: Path? = null
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcConfig.getDlcRoot()).stagingDir
            val result = DlcPackPackage.inspect(staging)
            if (result.valid && result.pack != null) {
                identifier = result.pack.metadata.identifier
            }
        } catch (_: Exception) {
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
        return try {
            if (!identifier.isNullOrBlank()) {
                val installed = DlcPackMeta.load(DlcConfig.getDlcRoot().resolve("tmp").resolve("pack").resolve(identifier).resolve("meta.dc"))
                if (installed != null) {
                    DlcPackInstaller(DlcPaths.gameDir(), DlcConfig.getDlcRoot()).uninstall(identifier)
                }
            }
            Files.deleteIfExists(archive)
            UpdatePackActionResult.ok("deleted")
        } catch (error: Exception) {
            UpdatePackActionResult.fail(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun loadInstalledPacks(): Map<String, DlcPackMeta> {
        val root = DlcConfig.getDlcRoot().resolve("tmp").resolve("pack")
        val out = HashMap<String, DlcPackMeta>()
        if (!Files.isDirectory(root)) {
            return out
        }
        try {
            Files.list(root).use { stream ->
                stream.filter(Files::isDirectory).forEach { dir ->
                    val meta = DlcPackMeta.load(dir.resolve("meta.dc"))
                    if (meta != null && !meta.identifier.isNullOrBlank()) {
                        out[meta.identifier] = meta
                    }
                }
            }
        } catch (_: IOException) {
        }
        return out
    }

    private fun packArchiveRoot(): Path {
        return DlcConfig.getDlcRoot().resolve("pack").toAbsolutePath().normalize()
    }

    private fun packOrderPath(): Path {
        return DlcPaths.gameDir().resolve("config").resolve("dlc_manager").resolve("order.dc")
    }

    private fun readPackIconBase64(iconFile: Path?): String? {
        if (iconFile == null || !Files.isRegularFile(iconFile)) {
            return null
        }
        return try {
            val size = Files.size(iconFile)
            if (size <= 0L || size > 1024L * 1024L) {
                null
            } else {
                Base64.getEncoder().encodeToString(Files.readAllBytes(iconFile))
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun resolvePackArchive(archivePath: String?): Path? {
        if (archivePath.isNullOrBlank()) return null
        return try {
            val local = packArchiveRoot()
            val downloaded = DlcConfig.getDlcRoot().resolve("downloaded/DLCs").toAbsolutePath().normalize()
            val target = local.resolve(archivePath).toAbsolutePath().normalize()
            if (target.startsWith(local) || target.startsWith(downloaded)) target else null
        } catch (_: java.nio.file.InvalidPathException) { null }
    }

    private fun describePackPrecheck(precheck: DlcPackInstaller.PrecheckResult): String {
        val lines = ArrayList<String>()
        if (precheck.incompatibleVersions.isNotEmpty()) {
            lines.add("incompatible: " + precheck.incompatibleVersions.joinToString(", "))
        }
        if (precheck.missingDependencies.isNotEmpty()) {
            lines.add("missing dependencies: " + precheck.missingDependencies.joinToString(", "))
        }
        if (precheck.dlcToEnable.isNotEmpty()) {
            lines.add("enable DLC: " + precheck.dlcToEnable.joinToString(", "))
        }
        if (precheck.presetsToEnable.isNotEmpty()) {
            lines.add("enable presets: " + precheck.presetsToEnable.joinToString(", "))
        }
        if (precheck.dlcToDisable.isNotEmpty()) {
            lines.add("disable DLC: " + precheck.dlcToDisable.joinToString(", "))
        }
        if (precheck.presetsToDisable.isNotEmpty()) {
            lines.add("disable presets: " + precheck.presetsToDisable.joinToString(", "))
        }
        return if (lines.isEmpty()) "precheck failed" else lines.joinToString("; ")
    }

    @Throws(IOException::class)
    private fun handlePushInit(request: JsonObject, writer: BufferedWriter): PushState? {
        val id = if (request.has("id")) request.get("id").asString else null
        val mode = if (request.has("mode")) request.get("mode").asString else "overwrite"
        val targets = readStringArray(request, "targets")
        val paths = readStringArray(request, "paths")
        val dlcRoot = DlcConfig.getDlcRoot()
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
        val dlcRoot = DlcConfig.getDlcRoot()
        val configDir = DlcPlatformServices.configDir().resolve("dlc_manager")
        val gameDir = DlcPlatformServices.gameDir()
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
        val root = DlcPlatformServices.gameDir()
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
        val root = DlcPlatformServices.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        if (target == null || !Files.exists(target)) {
            writeError(writer, "invalid path")
            return
        }
        val dlcRoot = DlcConfig.getDlcRoot()
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
        val root = DlcPlatformServices.gameDir()
        val target = safeResolve(root, raw)
        if (target == null) {
            writeError(writer, "invalid path")
            return null
        }
        val dlcRoot = DlcConfig.getDlcRoot()
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
            val root = DlcPlatformServices.gameDir()
            val dlcRoot = DlcConfig.getDlcRoot()
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
        val root = DlcPlatformServices.gameDir()
        val raw = if (request.has("path")) request.get("path").asString else ""
        val target = safeResolve(root, raw)
        if (target == null || !Files.exists(target)) {
            writeError(writer, "invalid path")
            return
        }
        val isDir = Files.isDirectory(target)
        val dlcRoot = DlcConfig.getDlcRoot()
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
        response.addProperty("host", DlcConfig.getServerRconHost())
        response.addProperty("port", DlcConfig.getServerRconPort())
        response.addProperty("password", DlcConfig.getServerRconPassword())
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
        val port = if (request.has("port")) request.get("port").asInt else DlcConfig.getServerRconPort()
        val password = if (request.has("password")) request.get("password").asString else ""
        DlcConfig.setServerRconHost(host)
        DlcConfig.setServerRconPort(port)
        DlcConfig.setServerRconPassword(password)
        DlcConfig.saveServer()
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
        response.add("config", DlcConfig.buildServerConfigJson())
        writeLine(writer, response)
    }

    @Throws(IOException::class)
    private fun handleConfigSet(request: JsonObject, writer: BufferedWriter) {
        var ok = false
        if (request.has("config") && request.get("config").isJsonObject) {
            val config = request.getAsJsonObject("config")
            DlcConfig.applySharedConfigJson(config)
            DlcConfig.applyServerConfigJson(config)
            DlcConfig.saveShared()
            DlcConfig.saveServer()
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
        val host = DlcConfig.getServerRconHost()
        val password = DlcConfig.getServerRconPassword()
        val port = DlcConfig.getServerRconPort()
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

    private class PackApplyJob(
        private val order: List<String>,
        private val uninstallIdentifiers: List<String>,
        private val installArchives: List<String>,
    ) {
        private val cancelRequested = AtomicBoolean(false)
        private val pauseRequested = AtomicBoolean(false)
        private val pauseLock = ReentrantLock()
        private val pauseChanged = pauseLock.newCondition()
        @Volatile private var running = true
        @Volatile private var finished = false
        @Volatile private var success = false
        @Volatile private var canceled = false
        @Volatile private var paused = false
        @Volatile private var totalCount = (uninstallIdentifiers.size + installArchives.size).coerceAtLeast(1)
        @Volatile private var completedCount = 0
        @Volatile private var progress = 0f
        @Volatile private var message: String? = "dlc_manager.pack.operation_applying"
        @Volatile private var detail: String? = null
        @Volatile private var error: String? = null

        fun isRunning(): Boolean = running

        fun cancel() {
            cancelRequested.set(true)
            message = "dlc_manager.pack.canceling"
            detail = null
            pauseLock.lock()
            try {
                pauseChanged.signalAll()
            } finally {
                pauseLock.unlock()
            }
        }

        fun pause() {
            if (!running || finished) return
            pauseRequested.set(true)
            paused = true
            message = "dlc_manager.pack.paused"
            detail = null
        }

        fun resume() {
            pauseRequested.set(false)
            paused = false
            pauseLock.lock()
            try {
                pauseChanged.signalAll()
            } finally {
                pauseLock.unlock()
            }
        }

        fun run() {
            try {
                checkCanceled()
                message = "dlc_manager.pack.operation_applying"
                detail = null
                progress = 0f
                if (!DlcPackOrder.save(packOrderPath(), order)) {
                    throw IOException("save order failed")
                }
                val hasUninstallPhase = uninstallIdentifiers.isNotEmpty()
                val hasInstallPhase = installArchives.isNotEmpty()
                val uninstallTotalSpan = if (hasUninstallPhase && hasInstallPhase) 0.35f else if (hasUninstallPhase) 1f else 0f
                val installTotalSpan = if (hasUninstallPhase && hasInstallPhase) 0.65f else if (hasInstallPhase) 1f else 0f
                val uninstallSpan = if (uninstallIdentifiers.isNotEmpty()) uninstallTotalSpan / uninstallIdentifiers.size.toFloat() else 0f
                val installSpan = if (installArchives.isNotEmpty()) installTotalSpan / installArchives.size.toFloat() else 0f
                var phaseOffset = 0f
                for (identifier in uninstallIdentifiers) {
                    checkCanceled()
                    val offset = phaseOffset
                    runStep("dlc_manager.pack.operation_uninstalling", offset, uninstallSpan) { stepProgress ->
                        val result = uninstallServerPack(identifier) { status, value ->
                            waitIfPaused()
                            detail = status
                            stepProgress(normalizeProgressStatus(status, "dlc_manager.pack.operation_uninstalling"), value)
                        }
                        if (!result.ok) {
                            throw IOException(result.message)
                        }
                    }
                    phaseOffset += uninstallSpan
                }
                for (archive in installArchives) {
                    checkCanceled()
                    val offset = phaseOffset
                    runStep("dlc_manager.pack.operation_installing", offset, installSpan) { stepProgress ->
                        val result = installServerPack(
                            archive,
                            { status, value ->
                                waitIfPaused()
                                detail = status
                                stepProgress(normalizeProgressStatus(status, "dlc_manager.pack.operation_installing"), value)
                            },
                            {
                                waitIfPaused()
                                cancelRequested.get()
                            },
                        )
                        if (!result.ok) {
                            throw IOException(result.message)
                        }
                    }
                    phaseOffset += installSpan
                }
                success = true
                progress = 1f
                message = "dlc_manager.pack.apply_success"
                detail = null
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                canceled = true
                error = "canceled"
                message = "dlc_manager.pack.canceled"
                detail = null
            } catch (error: Exception) {
                this.error = error.message ?: error.javaClass.simpleName
                message = this.error
            } finally {
                running = false
                finished = true
            }
        }

        private fun runStep(label: String, offset: Float, span: Float, action: ((String, Float) -> Unit) -> Unit) {
            waitIfPaused()
            message = label
            detail = null
            updateProgress(offset, span, -1f)
            action { status, value ->
                waitIfPaused()
                message = status
                updateProgress(offset, span, value)
            }
            completedCount++
            updateProgress(offset, span, 1f)
        }

        private fun updateProgress(offset: Float, span: Float, stepProgress: Float) {
            if (stepProgress < 0f) {
                progress = -1f
                return
            }
            progress = (offset + stepProgress.coerceIn(0f, 1f) * span).coerceIn(0f, 1f)
        }

        private fun checkCanceled() {
            waitIfPaused()
            if (cancelRequested.get()) {
                throw DlcDownloadService.DownloadCanceledException("canceled")
            }
        }

        private fun waitIfPaused() {
            while (pauseRequested.get() && !cancelRequested.get()) {
                paused = true
                message = "dlc_manager.pack.paused"
                detail = null
                pauseLock.lock()
                try {
                    try {
                        pauseChanged.await(200L, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                    }
                } finally {
                    pauseLock.unlock()
                }
            }
            paused = false
        }

        private fun normalizeProgressStatus(status: String?, fallback: String): String {
            val raw = status?.trim()
            if (raw.isNullOrEmpty()) return fallback
            if (raw.startsWith("dlc_manager.")) return raw
            return when {
                raw.startsWith("Uninstalling") || raw.startsWith("Restoring") || raw.startsWith("Uninstalled") ->
                    "dlc_manager.pack.operation_uninstalling"
                raw.startsWith("Downloading") || raw.startsWith("Resolved") || raw.startsWith("Preparing") ||
                    raw.startsWith("Backing up") || raw.startsWith("Installing") || raw.startsWith("Writing metadata") ||
                    raw.startsWith("Installed") ->
                    "dlc_manager.pack.operation_installing"
                raw.startsWith("Applying") || raw.startsWith("Saving") || raw.startsWith("Applied") ->
                    "dlc_manager.pack.operation_applying"
                raw.startsWith("Canceling") || raw.startsWith("Canceled") ->
                    "dlc_manager.pack.canceling"
                raw.startsWith("Paused") ->
                    "dlc_manager.pack.paused"
                else -> fallback
            }
        }

        fun toStatus(): DlcServerPackApplyStatus {
            val status = DlcServerPackApplyStatus()
            status.running = running
            status.finished = finished
            status.success = success
            status.canceled = canceled
            status.paused = paused
            status.totalCount = totalCount
            status.completedCount = completedCount
            status.progress = progress
            status.message = message
            status.detail = detail
            status.error = error
            return status
        }
    }

    private class UpdatePackActionResult private constructor(
        val ok: Boolean,
        val message: String,
    ) {
        companion object {
            fun ok(message: String?): UpdatePackActionResult = UpdatePackActionResult(true, message ?: "")
            fun fail(message: String?): UpdatePackActionResult = UpdatePackActionResult(false, message ?: "")
        }
    }
}
