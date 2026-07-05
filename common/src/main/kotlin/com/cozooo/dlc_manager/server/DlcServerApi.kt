package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPathUtil
import com.cozooo.dlc_manager.core.DlcPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.logging.LogUtils
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import net.minecraft.server.MinecraftServer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object DlcServerApi {
    private val LOGGER = LogUtils.getLogger()
    private val GSON: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val RUNNING = AtomicBoolean(false)

    private var httpServer: HttpServer? = null
    private var clientPool: ExecutorService? = null

    @Volatile
    private var currentServerValue: MinecraftServer? = null

    @Volatile
    private var clientStateChangedListener: Runnable? = null

    @JvmStatic
    @Synchronized
    fun start(server: MinecraftServer?) {
        currentServerValue = server
        if (!DlcConfig.isApiEnabled()) {
            LOGGER.info("[dlc-manager] API disabled; skip DlcServerApi start")
            return
        }
        if (DlcConfig.getApiPort() == 0) {
            LOGGER.info("[dlc-manager] HTTP API disabled because apiPort is 0; Java API remains available")
            return
        }
        if (RUNNING.get()) {
            if (httpServer != null) {
                return
            }
            RUNNING.set(false)
        }
        try {
            val created = HttpServer.create(InetSocketAddress(DlcConfig.getApiPort()), 0)
            created.createContext("/api", ApiHandler())
            clientPool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "dlc-manager-api-client").apply { isDaemon = true }
            }
            created.executor = clientPool
            created.start()
            httpServer = created
            RUNNING.set(true)
            if (DlcConfig.getApiToken().isNullOrBlank()) {
                LOGGER.warn(
                    "[dlc-manager] DlcServerApi started on port {} with empty token; all requests will be rejected until apiToken is configured",
                    DlcConfig.getApiPort(),
                )
            } else {
                LOGGER.info("[dlc-manager] DlcServerApi started on port {}", DlcConfig.getApiPort())
            }
        } catch (error: IOException) {
            RUNNING.set(false)
            httpServer = null
            clientPool?.shutdownNow()
            clientPool = null
            LOGGER.warn("[dlc-manager] failed to start DlcServerApi", error)
        }
    }

    @JvmStatic
    @Synchronized
    fun stop() {
        RUNNING.set(false)
        currentServerValue = null
        httpServer?.stop(0)
        httpServer = null
        clientPool?.shutdownNow()
        clientPool = null
    }

    @JvmStatic
    fun setClientStateChangedListener(listener: Runnable?) {
        clientStateChangedListener = listener
    }

    @JvmStatic
    fun currentServer(): MinecraftServer? = currentServerValue

    @JvmStatic
    fun isRunning(): Boolean = RUNNING.get()

    @JvmStatic
    fun notifyClientStateChanged() {
        val listener = clientStateChangedListener ?: return
        try {
            listener.run()
        } catch (error: Throwable) {
            LOGGER.debug("[dlc-manager] client state changed listener failed", error)
        }
    }

    private class ApiHandler : HttpHandler {
        @Throws(IOException::class)
        override fun handle(exchange: HttpExchange) {
            try {
                addCors(exchange.responseHeaders)
                if ("OPTIONS".equals(exchange.requestMethod, ignoreCase = true)) {
                    sendJson(exchange, 204, null)
                    return
                }
                if (!isAuthorized(exchange)) {
                    sendError(exchange, 401, "unauthorized", "Invalid or missing API token.")
                    return
                }
                val path = normalizePath(exchange.requestURI)
                val method = exchange.requestMethod.uppercase(Locale.ROOT)
                when {
                    method == "GET" && path == "/api/ping" -> sendResult(exchange, DlcApiService.ping())
                    method == "GET" && path == "/api/version" -> sendResult(exchange, DlcApiService.version())
                    method == "GET" && path == "/api/state" -> sendResult(exchange, DlcApiService.state())
                    method == "GET" && path == "/api/dlc/list" -> sendResult(exchange, DlcApiService.listDlc())
                    method == "GET" && path == "/api/dlc/pending" -> sendResult(exchange, DlcApiService.listPendingDlc())
                    method == "POST" && path == "/api/dlc/apply" -> handleDlcApply(exchange)
                    method == "POST" && path == "/api/dlc/enable" -> handleDlcSetEnabled(exchange, true)
                    method == "POST" && path == "/api/dlc/disable" -> handleDlcSetEnabled(exchange, false)
                    method == "GET" && path == "/api/remote/list" -> handleRemoteList(exchange)
                    method == "POST" && path == "/api/remote/download" -> handleRemoteDownload(exchange)
                    method == "POST" && path == "/api/remote/download/start" -> handleRemoteDownloadStart(exchange)
                    method == "GET" && path == "/api/remote/download/status" -> handleRemoteDownloadStatus(exchange)
                    method == "POST" && path == "/api/remote/download/cancel" -> handleRemoteDownloadCancel(exchange)
                    method == "GET" && path == "/api/required/list" -> handleRequiredList(exchange)
                    method == "POST" && path == "/api/required/download/start" -> handleRequiredDownloadStart(exchange)
                    method == "GET" && path == "/api/required/download/status" -> handleRequiredDownloadStatus(exchange)
                    method == "POST" && path == "/api/required/download/cancel" -> handleRequiredDownloadCancel(exchange)
                    method == "POST" && path == "/api/required/uninstall" -> handleRequiredUninstall(exchange)
                    method == "POST" && path == "/api/required/apply" -> handleRequiredApply(exchange)
                    method == "GET" && path == "/api/update/state" -> handleUpdateState(exchange)
                    method == "GET" && path == "/api/update/list" -> handleUpdateList(exchange)
                    method == "POST" && path == "/api/update/apply" -> handleUpdateApply(exchange)
                    method == "POST" && path == "/api/update/rollback" -> handleUpdateRollback(exchange)
                    method == "POST" && path == "/api/update/import" -> handleUpdateImport(exchange)
                    method == "POST" && path == "/api/update/import-url" -> handleUpdateImportUrl(exchange)
                    method == "POST" && path == "/api/notice/content" -> handleNoticeContent(exchange)
                    method == "POST" && path == "/api/file/add" -> handleFileAdd(exchange)
                    method == "POST" && path == "/api/file/del" -> handleFileDelete(exchange)
                    method == "POST" && path == "/api/file/mv" -> handleFileMove(exchange)
                    method == "POST" && path == "/api/file/rename" -> handleFileRename(exchange)
                    method == "POST" && path == "/api/file/upload" -> handleFileUpload(exchange)
                    else -> sendError(exchange, 404, "not_found", "Unknown API route.")
                }
            } catch (error: Exception) {
                LOGGER.warn("[dlc-manager] DlcServerApi request failed", error)
                sendError(exchange, 500, "internal_error", "Internal server error.")
            } finally {
                exchange.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun sendResult(exchange: HttpExchange, result: DlcApiService.ApiResult) {
        sendJson(exchange, result.status(), result.body())
    }

    @Throws(IOException::class)
    private fun handleDlcApply(exchange: HttpExchange) {
        sendResult(exchange, DlcApiService.applyDlc(readJsonBody(exchange)))
    }

    @Throws(IOException::class)
    private fun handleDlcSetEnabled(exchange: HttpExchange, enabled: Boolean) {
        sendResult(exchange, DlcApiService.setDlcEnabled(readJsonBody(exchange), enabled))
    }

    @Throws(IOException::class)
    private fun handleRemoteList(exchange: HttpExchange) {
        val lang = blank(exchange.requestHeaders.getFirst("X-Dlc-Lang"))
        val data = JsonObject()
        data.add("items", GSON.toJsonTree(DlcServerDownloadService.collectRemoteItems(DlcConfig.getDlcRoot(), lang)))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownload(exchange: HttpExchange) {
        val result = DlcServerDownloadService.downloadMissingRemote(DlcConfig.getDlcRoot())
        val data = JsonObject()
        data.addProperty("success", result.success)
        data.addProperty("downloaded", result.downloadedCount)
        data.addProperty("error", result.error ?: "")
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownloadStart(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val keys = readStringArray(request, "keys")
        val lang = blank(getString(request, "lang"))
        val status: DlcServerRemoteStatus = DlcServerDownloadService.startRemoteDownload(DlcConfig.getDlcRoot(), keys, lang)
        val data = JsonObject()
        data.add("status", GSON.toJsonTree(status))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownloadStatus(exchange: HttpExchange) {
        val lang = blank(exchange.requestHeaders.getFirst("X-Dlc-Lang"))
        val status: DlcServerRemoteStatus = DlcServerDownloadService.getRemoteDownloadStatus(lang)
        val data = JsonObject()
        data.add("status", GSON.toJsonTree(status))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownloadCancel(exchange: HttpExchange) {
        val data = JsonObject()
        data.addProperty("canceled", DlcServerDownloadService.cancelRemoteDownload())
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredList(exchange: HttpExchange) {
        val lang = blank(exchange.requestHeaders.getFirst("X-Dlc-Lang"))
        val items: List<DlcServerRequiredItem> = DlcServerDownloadService.collectRequiredItems(DlcConfig.getDlcRoot(), lang)
        val data = JsonObject()
        data.add("items", GSON.toJsonTree(items))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadStart(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val ids = readStringArray(request, "ids")
        val lang = blank(getString(request, "lang"))
        val status: DlcServerRequiredStatus = DlcServerDownloadService.startRequiredDownload(DlcConfig.getDlcRoot(), ids, lang)
        val data = JsonObject()
        data.add("status", GSON.toJsonTree(status))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadStatus(exchange: HttpExchange) {
        val lang = blank(exchange.requestHeaders.getFirst("X-Dlc-Lang"))
        val status: DlcServerRequiredStatus = DlcServerDownloadService.getRequiredDownloadStatus(lang)
        val data = JsonObject()
        data.add("status", GSON.toJsonTree(status))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadCancel(exchange: HttpExchange) {
        val data = JsonObject()
        data.addProperty("canceled", DlcServerDownloadService.cancelRequiredDownload())
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredUninstall(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val ids = readStringArray(request, "ids")
        val data = JsonObject()
        data.addProperty("removed", DlcServerDownloadService.uninstallRequired(DlcConfig.getDlcRoot(), ids))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredApply(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val ids = readStringArray(request, "ids")
        val data = JsonObject()
        data.addProperty("applied", DlcServerDownloadService.applyRequiredByIds(DlcConfig.getDlcRoot(), ids))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleNoticeContent(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val url = blank(getString(request, "url"))
        if (url.isBlank()) {
            sendError(exchange, 400, "url_missing", "URL is required.")
            return
        }
        val data = JsonObject()
        data.addProperty("url", url)
        data.addProperty("content", fetchNoticeContent(url) ?: "")
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleFileAdd(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val pathRaw = blank(getString(request, "path"))
        if (pathRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Target path is required.")
            return
        }
        val target = resolveWritableGamePath(pathRaw)
        if (target == null) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.")
            return
        }
        val content = getString(request, "content")
        val base64 = blank(getString(request, "contentBase64"))
        val sourceRaw = blank(getString(request, "source"))
        val directory = getBoolean(request, "directory")
        if (directory) {
            Files.createDirectories(target)
            val data = JsonObject()
            data.addProperty("path", DlcPathUtil.toGameRelativePath(target))
            data.addProperty("created", true)
            data.addProperty("directory", true)
            sendOk(exchange, data)
            return
        }
        target.parent?.let { Files.createDirectories(it) }
        if (sourceRaw.isNotBlank()) {
            val source = resolveReadableGamePath(sourceRaw)
            if (source == null || !Files.exists(source)) {
                sendError(exchange, 400, "invalid_source", "Source path is invalid.")
                return
            }
            Files.copy(source, target)
        } else if (base64.isNotBlank()) {
            Files.write(target, Base64.getDecoder().decode(base64))
        } else {
            Files.writeString(target, content, StandardCharsets.UTF_8)
        }
        val data = JsonObject()
        data.addProperty("path", DlcPathUtil.toGameRelativePath(target))
        data.addProperty("created", true)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleFileDelete(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val pathRaw = blank(getString(request, "path"))
        if (pathRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Target path is required.")
            return
        }
        val target = resolveReadableGamePath(pathRaw)
        if (target == null) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.")
            return
        }
        val data = JsonObject()
        data.addProperty("path", DlcPathUtil.toGameRelativePath(target))
        data.addProperty("deleted", deleteRecursiveIfExists(target))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleFileMove(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val fromRaw = blank(getString(request, "from"))
        val toRaw = blank(getString(request, "to"))
        if (fromRaw.isBlank() || toRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Both from and to paths are required.")
            return
        }
        val source = resolveReadableGamePath(fromRaw)
        val target = resolveWritableGamePath(toRaw)
        if (source == null || target == null) {
            sendError(exchange, 400, "invalid_path", "Path is outside the allowed game workspace.")
            return
        }
        if (!Files.exists(source)) {
            sendError(exchange, 404, "not_found", "Source path does not exist.")
            return
        }
        target.parent?.let { Files.createDirectories(it) }
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        val data = JsonObject()
        data.addProperty("from", DlcPathUtil.toGameRelativePath(source))
        data.addProperty("to", DlcPathUtil.toGameRelativePath(target))
        data.addProperty("moved", true)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleFileRename(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val pathRaw = blank(getString(request, "path"))
        val nameRaw = blank(getString(request, "name"))
        if (pathRaw.isBlank() || nameRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Path and new name are required.")
            return
        }
        if (nameRaw.contains("/") || nameRaw.contains("\\") || nameRaw == "." || nameRaw == "..") {
            sendError(exchange, 400, "invalid_name", "New name is invalid.")
            return
        }
        val source = resolveReadableGamePath(pathRaw)
        if (source == null) {
            sendError(exchange, 400, "invalid_path", "Path is outside the allowed game workspace.")
            return
        }
        if (!Files.exists(source)) {
            sendError(exchange, 404, "not_found", "Source path does not exist.")
            return
        }
        val parent = source.parent
        if (parent == null) {
            sendError(exchange, 400, "invalid_path", "Source path cannot be renamed.")
            return
        }
        val target = parent.resolve(nameRaw).normalize()
        if (!target.startsWith(DlcPaths.gameDir().normalize())) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.")
            return
        }
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        val data = JsonObject()
        data.addProperty("from", DlcPathUtil.toGameRelativePath(source))
        data.addProperty("to", DlcPathUtil.toGameRelativePath(target))
        data.addProperty("renamed", true)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleFileUpload(exchange: HttpExchange) {
        var pathRaw = blank(getQueryParam(exchange.requestURI, "path"))
        if (pathRaw.isBlank()) {
            pathRaw = blank(exchange.requestHeaders.getFirst("X-Dlc-Path"))
        }
        if (pathRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Upload target path is required.")
            return
        }
        val target = resolveWritableGamePath(pathRaw)
        if (target == null) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.")
            return
        }
        val body = exchange.requestBody.readAllBytes()
        target.parent?.let { Files.createDirectories(it) }
        Files.write(target, body)
        val data = JsonObject()
        data.addProperty("path", DlcPathUtil.toGameRelativePath(target))
        data.addProperty("uploaded", true)
        data.addProperty("size", body.size)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateState(exchange: HttpExchange) {
        val dlcRoot = DlcConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot)
        val base = blank(version?.getBase())
        val dlc = blank(version?.getDlc())
        val rollbackFrom = blank(version?.getRollbackFrom())
        val rolledBack = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, base, dlc)
        val history: List<DlcUpdateMeta?>? = if (rolledBack) {
            DlcUpdateService.listUpdateHistoryFrom(rollbackFrom)
        } else {
            DlcUpdateService.listUpdateHistory()
        }
        val data = JsonObject()
        data.addProperty("rollbackFrom", rollbackFrom)
        data.addProperty("rolledBack", rolledBack)
        data.add("history", GSON.toJsonTree(history ?: emptyList<DlcUpdateMeta>()))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateList(exchange: HttpExchange) {
        val result = DlcUpdateService.checkForUpdates()
        val data = JsonObject()
        val updates = JsonArray()
        for (pkg in result.updates ?: mutableListOf()) {
            val row = toUpdatePackageJson(pkg)
            if (row != null) {
                updates.add(row)
            }
        }
        data.add("updates", updates)
        data.addProperty("hasUpdates", updates.size() > 0)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateApply(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val baseOld = blank(getString(request, "baseOld"))
        val dlcOld = blank(getString(request, "dlcOld"))
        val baseNew = blank(getString(request, "baseNew"))
        val dlcNew = blank(getString(request, "dlcNew"))
        val updates = if (baseOld.isNotBlank() && dlcOld.isNotBlank() && baseNew.isNotBlank() && dlcNew.isNotBlank()) {
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
        } else {
            DlcUpdateService.checkForUpdates().updates
        }
        val apply = DlcUpdateService.applyUpdates(updates, false)
        val data = JsonObject()
        data.addProperty("success", apply?.isSuccess == true)
        data.addProperty("missing", apply?.isMissing == true)
        data.addProperty("message", blank(apply?.message))
        data.add("missingPaths", toStringArray(apply?.missingPaths))
        data.addProperty("addedRequired", apply?.hasRequiredAdd() == true)
        data.addProperty("addedRemote", apply?.hasRemoteAdd() == true)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateRollback(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val target = blank(getString(request, "target"))
        if (target.isBlank()) {
            sendError(exchange, 400, "target_missing", "Rollback target is required.")
            return
        }
        val result = DlcUpdateService.rollbackTo(target)
        val data = JsonObject()
        data.addProperty("success", result.isSuccess)
        data.addProperty("message", blank(result.message))
        data.addProperty("version", blank(result.version))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateImport(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val rawPath = blank(getString(request, "path"))
        val source = resolveGameRelativePath(rawPath)
        if (source == null || !Files.exists(source) || !Files.isRegularFile(source)) {
            sendError(exchange, 400, "invalid_path", "Update package path is invalid.")
            return
        }
        val data = JsonObject()
        data.addProperty("success", DlcUpdateService.importUpdatePackage(source))
        data.addProperty("path", source.toString())
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateImportUrl(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val url = blank(getString(request, "url"))
        val fileName = blank(getString(request, "fileName"))
        if (url.isBlank()) {
            sendError(exchange, 400, "url_missing", "URL is required.")
            return
        }
        val updateDir = DlcUpdateService.updateDir
        if (updateDir == null) {
            sendError(exchange, 400, "missing_dlc_root", "Missing DLC root.")
            return
        }
        var ok = false
        var message = ""
        var downloaded: Path? = null
        try {
            downloaded = DlcDownloadService.download(url, updateDir, fileName, null, null)
            ok = DlcUpdateService.importUpdatePackage(downloaded)
            if (!ok) {
                message = "import_failed"
            }
        } catch (_: DlcDownloadService.DownloadCanceledException) {
            message = "canceled"
        } catch (ex: Exception) {
            message = ex.message ?: "download_failed"
        }
        val data = JsonObject()
        data.addProperty("success", ok)
        data.addProperty("message", message)
        data.addProperty("downloadedPath", downloaded?.toString() ?: "")
        sendOk(exchange, data)
    }

    private fun toUpdatePackageJson(pkg: DlcUpdatePackage?): JsonObject? {
        val manifest = pkg?.getManifest() ?: return null
        val info: DlcUpdateManifest.VersionInfo = manifest.getVersion() ?: return null
        val json = JsonObject()
        json.addProperty("name", blank(pkg.getName()))
        json.addProperty("platform", blank(info.getPlatform()))
        json.addProperty("base", blank(info.getBase()))
        json.addProperty("base_new", blank(info.getBaseNew()))
        json.addProperty("dlc", blank(info.getDlcOld()))
        json.addProperty("dlc_new", blank(info.getDlcNew()))
        json.addProperty("hidden", manifest.isHidden())
        json.add("changelog", GSON.toJsonTree(manifest.getChangelog()))
        return json
    }

    private fun resolveGameRelativePath(rawPath: String?): Path? {
        val root = DlcPaths.gameDir()
        val value = rawPath?.replace('\\', '/') ?: ""
        val resolved = if (value.isBlank()) root else root.resolve(value)
        val normalized = resolved.normalize()
        if (!normalized.startsWith(root.normalize())) {
            return null
        }
        return normalized
    }

    private fun resolveReadableGamePath(rawPath: String?): Path? = resolveGameRelativePath(rawPath)

    private fun resolveWritableGamePath(rawPath: String?): Path? = resolveGameRelativePath(rawPath)

    @Throws(IOException::class)
    private fun deleteRecursiveIfExists(path: Path?): Boolean {
        if (path == null || !Files.exists(path)) {
            return false
        }
        if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    @Throws(IOException::class)
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (exc != null) {
                            throw exc
                        }
                        Files.deleteIfExists(dir)
                        return FileVisitResult.CONTINUE
                    }
                })
            } catch (error: IOException) {
                throw error
            }
            return true
        }
        return Files.deleteIfExists(path)
    }

    private fun fetchNoticeContent(rawUrl: String?): String? {
        val url = blank(rawUrl)
        if (url.isBlank()) {
            return null
        }
        var connection: HttpURLConnection? = null
        try {
            connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "DLC-Manager-NoticeApi/1.0")
            val code = connection.responseCode
            if (code < 200 || code >= 300) {
                return null
            }
            connection.inputStream.use { input ->
                var text = String(input.readAllBytes(), StandardCharsets.UTF_8)
                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1)
                }
                return if (text.isBlank()) null else text
            }
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getBoolean(obj: JsonObject?, key: String): Boolean {
        if (obj == null) {
            return false
        }
        val element = DlcDcConfigIO.getIgnoreCase(obj, key)
        if (element == null || element.isJsonNull) {
            return false
        }
        return try {
            element.asBoolean
        } catch (_: Exception) {
            false
        }
    }

    private fun readStringArray(obj: JsonObject?, key: String): List<String> {
        val array = if (obj == null) null else DlcDcConfigIO.getArrayIgnoreCase(obj, key)
        val values = ArrayList<String>()
        if (array == null) {
            return values
        }
        for (element in array) {
            if (element != null && element.isJsonPrimitive) {
                val value = blank(element.asString)
                if (value.isNotBlank()) {
                    values.add(value)
                }
            }
        }
        return values
    }

    private fun getString(obj: JsonObject?, key: String): String = if (obj == null) "" else blank(DlcDcConfigIO.getStringIgnoreCase(obj, key))

    @Throws(IOException::class)
    private fun readJsonBody(exchange: HttpExchange): JsonObject {
        val body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        if (body.isBlank()) {
            return JsonObject()
        }
        return GSON.fromJson(body, JsonObject::class.java) ?: JsonObject()
    }

    @Throws(IOException::class)
    private fun sendOk(exchange: HttpExchange, data: JsonObject?) {
        val root = JsonObject()
        root.addProperty("ok", true)
        root.add("data", data ?: JsonObject())
        sendJson(exchange, 200, root)
    }

    @Throws(IOException::class)
    private fun sendError(exchange: HttpExchange, status: Int, code: String, message: String) {
        val root = JsonObject()
        root.addProperty("ok", false)
        val error = JsonObject()
        error.addProperty("code", code)
        error.addProperty("message", message)
        root.add("error", error)
        sendJson(exchange, status, root)
    }

    @Throws(IOException::class)
    private fun sendJson(exchange: HttpExchange, status: Int, body: JsonObject?) {
        val bytes = body?.let { GSON.toJson(it).toByteArray(StandardCharsets.UTF_8) } ?: ByteArray(0)
        val headers = exchange.responseHeaders
        if (body != null) {
            headers["Content-Type"] = "application/json; charset=utf-8"
        }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        if (bytes.isNotEmpty()) {
            exchange.responseBody.write(bytes)
        }
    }

    private fun addCors(headers: Headers) {
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
        headers["Access-Control-Allow-Headers"] = "Authorization,Content-Type"
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val expected = DlcConfig.getApiToken()
        if (expected.isNullOrBlank()) {
            return false
        }
        val authorization = exchange.requestHeaders.getFirst("Authorization")
        if (authorization.isNullOrBlank()) {
            return false
        }
        if (!authorization.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)) {
            return false
        }
        val provided = authorization.substring(7).trim()
        return Objects.equals(expected, provided)
    }

    private fun normalizePath(uri: URI?): String {
        if (uri?.path.isNullOrBlank()) {
            return "/"
        }
        var path = uri!!.path
        while (path.endsWith("/") && path.length > 1) {
            path = path.substring(0, path.length - 1)
        }
        return path
    }

    private fun getQueryParam(uri: URI?, key: String?): String {
        if (uri?.query.isNullOrBlank() || key.isNullOrBlank()) {
            return ""
        }
        val parts = uri!!.query.split("&")
        for (part in parts) {
            if (part.isBlank()) {
                continue
            }
            val index = part.indexOf('=')
            val rawKey = if (index >= 0) part.substring(0, index) else part
            if (key != rawKey) {
                continue
            }
            val rawValue = if (index >= 0) part.substring(index + 1) else ""
            return try {
                URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                rawValue
            }
        }
        return ""
    }

    private fun toStringArray(values: List<*>?): JsonArray {
        val array = JsonArray()
        if (values == null) {
            return array
        }
        for (value in values) {
            array.add(value?.toString() ?: "")
        }
        return array
    }

    private fun blank(value: String?): String = value ?: ""

}
