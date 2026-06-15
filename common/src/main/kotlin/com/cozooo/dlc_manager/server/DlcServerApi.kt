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
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DcFormatUtil
import com.cozooo.dlc_manager.core.DlcPathConfig
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
import java.util.Comparator
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
        if (!DlcPathConfig.isApiEnabled()) {
            LOGGER.info("[dlc-manager] API disabled; skip DlcServerApi start")
            return
        }
        if (DlcPathConfig.getApiPort() == 0) {
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
            val created = HttpServer.create(InetSocketAddress(DlcPathConfig.getApiPort()), 0)
            created.createContext("/api", ApiHandler())
            clientPool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "dlc-manager-api-client").apply { isDaemon = true }
            }
            created.executor = clientPool
            created.start()
            httpServer = created
            RUNNING.set(true)
            if (DlcPathConfig.getApiToken().isNullOrBlank()) {
                LOGGER.warn(
                    "[dlc-manager] DlcServerApi started on port {} with empty token; all requests will be rejected until apiToken is configured",
                    DlcPathConfig.getApiPort(),
                )
            } else {
                LOGGER.info("[dlc-manager] DlcServerApi started on port {}", DlcPathConfig.getApiPort())
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
                    method == "GET" && path == "/api/dlc/root/list" -> handleDlcRootList(exchange)
                    method == "POST" && path == "/api/dlc/apply" -> handleDlcApply(exchange)
                    method == "POST" && path == "/api/dlc/enable" -> handleDlcSetEnabled(exchange, true)
                    method == "POST" && path == "/api/dlc/disable" -> handleDlcSetEnabled(exchange, false)
                    method == "GET" && path == "/api/remote/list" -> handleRemoteList(exchange)
                    method == "POST" && path == "/api/remote/download" -> handleRemoteDownload(exchange)
                    method == "POST" && path == "/api/remote/download/start" -> handleRemoteDownloadStart(exchange)
                    method == "GET" && path == "/api/remote/download/status" -> handleRemoteDownloadStatus(exchange)
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
                    method == "POST" && path == "/api/dc/detect-type" -> handleDetectType(exchange)
                    method == "POST" && path == "/api/dc/validate" -> handleValidate(exchange)
                    method == "POST" && path == "/api/dc/read" -> handleReadDc(exchange)
                    method == "POST" && path == "/api/dc/write" -> handleWriteDc(exchange)
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
    private fun handleDlcRootList(exchange: HttpExchange) {
        val data = JsonObject()
        val roots = JsonArray()
        val dlcRoots = DlcPathConfig.getDlcRoots()
        for (i in dlcRoots.indices) {
            val root = dlcRoots[i]
            val rootJson = JsonObject()
            rootJson.addProperty("rootIndex", i)
            rootJson.addProperty("rootPath", root.toString())
            val files = JsonArray()
            for (relative in listEditableDcFiles(root)) {
                val relativePath = normalizeRelativePath(relative)
                val fileJson = JsonObject()
                fileJson.addProperty("name", relative.fileName?.toString() ?: relativePath)
                fileJson.addProperty("path", relativePath)
                val type = detectDcType(fileJson.get("name").asString, relativePath, "")
                fileJson.addProperty("type", type)
                fileJson.addProperty("supported", isSupportedType(type))
                files.add(fileJson)
            }
            rootJson.add("files", files)
            roots.add(rootJson)
        }
        data.add("roots", roots)
        data.addProperty("rootCount", dlcRoots.size)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRemoteList(exchange: HttpExchange) {
        val lang = blank(exchange.requestHeaders.getFirst("X-Dlc-Lang"))
        val data = JsonObject()
        data.add("items", GSON.toJsonTree(DlcServerDownloadService.collectMissingRemoteItems(DlcPathConfig.getDlcRoot(), lang)))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRemoteDownload(exchange: HttpExchange) {
        val result = DlcServerDownloadService.downloadMissingRemote(DlcPathConfig.getDlcRoot())
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
        val status: DlcServerRemoteStatus = DlcServerDownloadService.startRemoteDownload(DlcPathConfig.getDlcRoot(), keys, lang)
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
    private fun handleRequiredList(exchange: HttpExchange) {
        val lang = blank(exchange.requestHeaders.getFirst("X-Dlc-Lang"))
        val items: List<DlcServerRequiredItem> = DlcServerDownloadService.collectRequiredItems(DlcPathConfig.getDlcRoot(), lang)
        val data = JsonObject()
        data.add("items", GSON.toJsonTree(items))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredDownloadStart(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val ids = readStringArray(request, "ids")
        val lang = blank(getString(request, "lang"))
        val status: DlcServerRequiredStatus = DlcServerDownloadService.startRequiredDownload(DlcPathConfig.getDlcRoot(), ids, lang)
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
        data.addProperty("removed", DlcServerDownloadService.uninstallRequired(DlcPathConfig.getDlcRoot(), ids))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleRequiredApply(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val ids = readStringArray(request, "ids")
        val data = JsonObject()
        data.addProperty("applied", DlcServerDownloadService.applyRequiredByIds(DlcPathConfig.getDlcRoot(), ids))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleDetectType(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val name = blank(getString(request, "name"))
        val path = blank(getString(request, "path"))
        val content = blank(getString(request, "content"))
        val type = detectDcType(name, path, content)
        val data = JsonObject()
        data.addProperty("type", type)
        data.addProperty("supported", isSupportedType(type))
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleValidate(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val name = blank(getString(request, "name"))
        val path = blank(getString(request, "path"))
        val content = blank(getString(request, "content"))
        val explicitType = blank(getString(request, "type"))
        val type = if (explicitType.isBlank()) detectDcType(name, path, content) else explicitType.trim().lowercase(Locale.ROOT)
        val issues = JsonArray()
        if (content.isBlank()) {
            issues.add(issue("error", "schema", "content", "Content is empty."))
        }
        if (type == "unknown") {
            issues.add(issue("warning", "schema", "type", "Unable to detect config type."))
        }
        val parsed = if (content.isNotBlank() && isSupportedType(type)) parseDc(type, name, content) else null
        if (content.isNotBlank() && isSupportedType(type) && parsed == null) {
            issues.add(issue("error", "syntax", "content", "Failed to parse DC content."))
        }
        if (parsed != null) {
            validateParsed(type, parsed, issues)
        }
        var valid = true
        for (element in issues) {
            if (element != null && element.isJsonObject) {
                val severity = getString(element.asJsonObject, "severity")
                if ("error".equals(severity, ignoreCase = true)) {
                    valid = false
                    break
                }
            }
        }
        val data = JsonObject()
        data.addProperty("type", type)
        data.addProperty("supported", isSupportedType(type))
        data.addProperty("valid", valid)
        data.add("issues", issues)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleReadDc(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val resolved = resolveRequestedDcPath(request, false)
        if (resolved == null) {
            sendError(exchange, 400, "invalid_path", "Requested path is outside the allowed DLC config workspace.")
            return
        }
        if (!Files.exists(resolved.absolutePath) || !Files.isRegularFile(resolved.absolutePath)) {
            sendError(exchange, 404, "not_found", "Requested DC file does not exist.")
            return
        }
        val content = Files.readString(resolved.absolutePath, StandardCharsets.UTF_8)
        val fileName = resolved.absolutePath.fileName?.toString() ?: ""
        val type = detectDcType(fileName, resolved.relativePath(), content)
        val data = JsonObject()
        data.addProperty("name", resolved.absolutePath.fileName?.toString() ?: resolved.relativePath())
        data.addProperty("path", resolved.relativePath())
        data.addProperty("rootIndex", resolved.rootIndex)
        data.addProperty("type", type)
        data.addProperty("supported", isSupportedType(type))
        data.addProperty("content", content)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleWriteDc(exchange: HttpExchange) {
        val request = readJsonBody(exchange)
        val content = blank(getString(request, "content"))
        if (content.isBlank()) {
            sendError(exchange, 400, "content_empty", "Content is empty.")
            return
        }
        val resolved = resolveRequestedDcPath(request, true)
        if (resolved == null) {
            sendError(exchange, 400, "invalid_path", "Requested path is outside the allowed DLC config workspace.")
            return
        }
        val explicitType = blank(getString(request, "type"))
        val fileName = resolved.absolutePath.fileName?.toString() ?: ""
        val detectedType = if (explicitType.isBlank()) {
            detectDcType(fileName, resolved.relativePath(), content)
        } else {
            explicitType.trim().lowercase(Locale.ROOT)
        }
        if (isSupportedType(detectedType) && parseDc(detectedType, fileName, content) == null) {
            sendError(exchange, 400, "parse_failed", "Failed to parse DC content.")
            return
        }
        resolved.absolutePath.parent?.let { Files.createDirectories(it) }
        Files.writeString(resolved.absolutePath, content, StandardCharsets.UTF_8)
        val data = JsonObject()
        data.addProperty("name", resolved.absolutePath.fileName?.toString() ?: resolved.relativePath())
        data.addProperty("path", resolved.relativePath())
        data.addProperty("rootIndex", resolved.rootIndex)
        data.addProperty("type", detectedType)
        data.addProperty("supported", isSupportedType(detectedType))
        data.addProperty("written", true)
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
            data.addProperty("path", toGameRelativePath(target))
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
        data.addProperty("path", toGameRelativePath(target))
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
        data.addProperty("path", toGameRelativePath(target))
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
        data.addProperty("from", toGameRelativePath(source))
        data.addProperty("to", toGameRelativePath(target))
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
        data.addProperty("from", toGameRelativePath(source))
        data.addProperty("to", toGameRelativePath(target))
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
        data.addProperty("path", toGameRelativePath(target))
        data.addProperty("uploaded", true)
        data.addProperty("size", body.size)
        sendOk(exchange, data)
    }

    @Throws(IOException::class)
    private fun handleUpdateState(exchange: HttpExchange) {
        val dlcRoot = DlcPathConfig.getDlcRoot()
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

    private fun validateParsed(type: String, root: JsonObject, issues: JsonArray) {
        when (type) {
            "info" -> {
                requireString(issues, getSection(root, "basic"), "identifier", "[basic].identifier is required.")
                requireString(issues, getSection(root, "basic"), "file_name", "[basic].file_name is required.")
                val itemType = getString(getSection(root, "basic"), "type")
                if ("others".equals(blank(itemType), ignoreCase = true) || "config".equals(blank(itemType), ignoreCase = true)) {
                    requireString(issues, root, "appliedTarget", "appliedTarget is required for info type others/config.")
                }
            }
            "remote" -> {
                requireString(issues, getSection(root, "basic"), "identifier", "[basic].identifier is required.")
                requireString(issues, getSection(root, "basic"), "file_name", "[basic].file_name is required.")
                if (!hasAnyDownloadSource(getSection(root, "download"))) {
                    issues.add(issue("error", "schema", "download", "At least one download source is required."))
                }
                val itemType = getString(getSection(root, "basic"), "type")
                if ("others".equals(blank(itemType), ignoreCase = true)) {
                    requireString(issues, root, "appliedTarget", "appliedTarget is required for remote type others.")
                }
            }
            "required" -> {
                requireString(issues, getSection(root, "basic"), "identifier", "[basic].identifier is required.")
                requireString(issues, getSection(root, "basic"), "file_name", "[basic].file_name is required.")
                if (!hasAnyDownloadSource(getSection(root, "download"))) {
                    issues.add(issue("error", "schema", "download", "At least one download source is required."))
                }
            }
            "redirect" -> requireString(issues, root, "RedirectUrl", "RedirectUrl is required.")
            "version" -> {
                requireString(issues, root, "base", "base is required.")
                requireString(issues, root, "dlc", "dlc is required.")
            }
            "update" -> {
                val version = getSection(root, "version")
                requireString(issues, version, "base", "[version].base is required.")
                requireString(issues, version, "dlc", "[version].dlc is required.")
                val baseNew = blank(getString(version, "base_new"))
                val dlcNew = blank(getString(version, "dlc_new"))
                if (baseNew.isBlank() && dlcNew.isBlank()) {
                    issues.add(issue("error", "schema", "version", "[version].base_new and [version].dlc_new cannot both be empty."))
                }
            }
        }
    }

    private fun hasAnyDownloadSource(download: JsonObject?): Boolean {
        if (download == null) {
            return false
        }
        return hasNonBlankString(download, "modrinth_projectid") ||
            hasNonBlankString(download, "curseforge_projectid") ||
            hasNonBlankString(download, "directUrl")
    }

    private fun parseDc(type: String, name: String?, content: String): JsonObject? {
        var temp: Path? = null
        try {
            val fileName = if (name.isNullOrBlank()) defaultFileName(type) else name
            temp = Files.createTempFile(stripExtension(fileName), DcFormatUtil.DC_EXTENSION)
            Files.writeString(temp, content, StandardCharsets.UTF_8)
            val layout = if (usesStructuredLayout(type)) DcConfigIO.Layout.DLC_STRUCTURED else DcConfigIO.Layout.GENERIC
            return DcConfigIO.read(temp, layout)
        } catch (_: IOException) {
            return null
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp)
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun usesStructuredLayout(type: String): Boolean = type == "info" || type == "remote" || type == "required"

    private fun defaultFileName(type: String): String {
        return when (type) {
            "version" -> "version.dc"
            "notice" -> "notice.dc"
            "redirect" -> "redirect.dc"
            "update" -> "update.dc"
            "required_notice" -> "notice.dc"
            else -> "$type.dc"
        }
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    private fun detectDcType(name: String?, path: String?, content: String?): String {
        val lowerName = blank(name).lowercase(Locale.ROOT)
        val lowerPath = blank(path).replace('\\', '/').lowercase(Locale.ROOT)
        val text = blank(content)
        if (lowerPath.contains("/required/notice.") || lowerName == "notice.dc") {
            if (containsIgnoreCase(text, "[download_desc]") || containsIgnoreCase(text, "[manage_desc]")) {
                return "required_notice"
            }
        }
        if (lowerPath.contains("/info/version.") || lowerName == "version.dc") return "version"
        if (lowerPath.contains("/remote/redirect/") || lowerName == "redirect.dc" || containsIgnoreCase(text, "RedirectUrl")) return "redirect"
        if (containsIgnoreCase(text, "[version]") || containsIgnoreCase(text, "[file]") || containsIgnoreCase(text, "[update]")) return "update"
        if (lowerPath.contains("/preset/")) return "preset"
        if (lowerPath.contains("/remote/")) return "remote"
        if (lowerPath.contains("/required/")) return "required"
        if (lowerPath.contains("/info/")) return "info"
        if (lowerName == "notice.dc" || containsIgnoreCase(text, "isNoticeEnabled") || containsIgnoreCase(text, "[Contents]")) return "notice"
        if (containsIgnoreCase(text, "modrinth_projectid") || containsIgnoreCase(text, "curseforge_projectid") || containsIgnoreCase(text, "directUrl")) {
            return if (containsIgnoreCase(text, "type =") ||
                containsIgnoreCase(text, "AllowEnableByAll") ||
                containsIgnoreCase(text, "conflicts") ||
                containsIgnoreCase(text, "alarm")
            ) {
                "remote"
            } else {
                "required"
            }
        }
        if (containsIgnoreCase(text, "[DisplayName]") && containsIgnoreCase(text, "enable")) return "preset"
        if (containsIgnoreCase(text, "file_name") || containsIgnoreCase(text, "identifier")) return "info"
        if (containsIgnoreCase(text, "base =") && containsIgnoreCase(text, "dlc =")) return "version"
        return "unknown"
    }

    private fun isSupportedType(type: String): Boolean {
        return when (type) {
            "info", "remote", "required", "preset", "notice", "redirect", "update", "version" -> true
            else -> false
        }
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

    private fun listEditableDcFiles(root: Path?): MutableList<Path> {
        val files = ArrayList<Path>()
        if (root == null) {
            return files
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val rootNotice = normalizedRoot.resolve("notice.dc").normalize()
        if (Files.isRegularFile(rootNotice)) {
            files.add(normalizedRoot.relativize(rootNotice))
        }
        for (dirName in listOf("info", "remote", "required", "preset", "update")) {
            val dir = normalizedRoot.resolve(dirName).normalize()
            if (!Files.isDirectory(dir)) {
                continue
            }
            try {
                Files.walk(dir).use { stream ->
                    stream.filter(Files::isRegularFile)
                        .filter { path -> DcConfigIO.isDc(path) || path.fileName?.toString()?.lowercase(Locale.ROOT)?.endsWith(".dc") == true }
                        .map { path -> normalizedRoot.relativize(path.normalize()) }
                        .sorted(Comparator.comparing { path: Path -> normalizeRelativePath(path) })
                        .forEach(files::add)
                }
            } catch (_: IOException) {
            }
        }
        files.sortWith(Comparator.comparing { path: Path -> normalizeRelativePath(path) })
        return files
    }

    private fun resolveRequestedDcPath(request: JsonObject?, allowCreate: Boolean): ResolvedDcPath? {
        val rawPath = if (request == null) "" else blank(getString(request, "path"))
        if (rawPath.isBlank()) {
            return null
        }
        val roots = DlcPathConfig.getDlcRoots()
        if (roots.isEmpty()) {
            return null
        }
        val requestedRootIndex = getOptionalInt(request, "rootIndex")
        val candidate = DlcPaths.parsePathLenient(rawPath) ?: return null
        if (candidate.isAbsolute) {
            val absolute = candidate.normalize()
            for (i in roots.indices) {
                if (requestedRootIndex != null && requestedRootIndex != i) {
                    continue
                }
                val root = roots[i].toAbsolutePath().normalize()
                if (absolute.startsWith(root) && isAllowedDlcConfigPath(root, absolute, allowCreate)) {
                    return ResolvedDcPath(i, root.relativize(absolute), absolute)
                }
            }
            return null
        }
        val start = requestedRootIndex ?: 0
        val end = if (requestedRootIndex == null) roots.size else minOf(roots.size, requestedRootIndex + 1)
        for (i in start until end) {
            val root = roots[i].toAbsolutePath().normalize()
            val absolute = root.resolve(candidate).normalize()
            if (isAllowedDlcConfigPath(root, absolute, allowCreate)) {
                return ResolvedDcPath(i, root.relativize(absolute), absolute)
            }
        }
        return null
    }

    private fun isAllowedDlcConfigPath(root: Path?, target: Path?, allowCreate: Boolean): Boolean {
        if (root == null || target == null) {
            return false
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            return false
        }
        if (normalizedTarget.fileName == null) {
            return false
        }
        val fileName = normalizedTarget.fileName.toString().lowercase(Locale.ROOT)
        if (!fileName.endsWith(".dc")) {
            return false
        }
        val relative = normalizedRoot.relativize(normalizedTarget)
        if (relative.nameCount == 1) {
            return "notice.dc".equals(fileName, ignoreCase = true)
        }
        val first = relative.getName(0).toString().lowercase(Locale.ROOT)
        if (!listOf("info", "remote", "required", "preset", "update").contains(first)) {
            return false
        }
        return allowCreate || Files.exists(normalizedTarget)
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

    private fun toGameRelativePath(path: Path?): String {
        if (path == null) {
            return ""
        }
        val gameDir = DlcPaths.gameDir()
        return try {
            normalizeRelativePath(gameDir.normalize().relativize(path.toAbsolutePath().normalize()))
        } catch (_: Exception) {
            path.toString()
        }
    }

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
        val element = DcConfigIO.getIgnoreCase(obj, key)
        if (element == null || element.isJsonNull) {
            return false
        }
        return try {
            element.asBoolean
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeRelativePath(relative: Path?): String = relative?.toString()?.replace('\\', '/') ?: ""

    private fun getOptionalInt(obj: JsonObject?, key: String): Int? {
        if (obj == null) {
            return null
        }
        val element = DcConfigIO.getIgnoreCase(obj, key)
        if (element == null || element.isJsonNull) {
            return null
        }
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            return element.asInt
        }
        val value = blank(element.asString)
        if (value.isBlank()) {
            return null
        }
        return try {
            value.toInt()
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun readStringArray(obj: JsonObject?, key: String): List<String> {
        val array = if (obj == null) null else DcConfigIO.getArrayIgnoreCase(obj, key)
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

    private fun containsIgnoreCase(haystack: String?, needle: String?): Boolean {
        return haystack != null && needle != null && haystack.lowercase(Locale.ROOT).contains(needle.lowercase(Locale.ROOT))
    }

    private fun issue(severity: String, source: String, field: String, message: String): JsonObject {
        val issue = JsonObject()
        issue.addProperty("severity", severity)
        issue.addProperty("source", source)
        issue.addProperty("field", field)
        issue.addProperty("message", message)
        return issue
    }

    private fun requireString(issues: JsonArray, section: JsonObject?, key: String, message: String) {
        if (!hasNonBlankString(section, key)) {
            issues.add(issue("error", "schema", key, message))
        }
    }

    private fun hasNonBlankString(obj: JsonObject?, key: String): Boolean = blank(getString(obj, key)).isNotBlank()

    private fun getSection(root: JsonObject?, key: String): JsonObject? = if (root == null) null else DcConfigIO.getObjectIgnoreCase(root, key)

    private fun getString(obj: JsonObject?, key: String): String = if (obj == null) "" else blank(DcConfigIO.getStringIgnoreCase(obj, key))

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
        val expected = DlcPathConfig.getApiToken()
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

    private data class ResolvedDcPath(
        val rootIndex: Int,
        val relative: Path,
        val absolutePath: Path,
    ) {
        fun relativePath(): String = normalizeRelativePath(relative)
    }
}
