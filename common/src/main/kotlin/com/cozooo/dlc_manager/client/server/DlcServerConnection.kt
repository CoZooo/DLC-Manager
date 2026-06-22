package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DlcServerConnection {
    private val GSON: Gson = GsonBuilder().create()
    private const val CHUNK_SIZE = 64 * 1024

    class ApplyResponse {
        @JvmField var ok: Boolean = false
        @JvmField var requiresRestart: Boolean = false
    }

    class UpdateResponse {
        @JvmField var ok: Boolean = false
        @JvmField var missing: Boolean = false
        @JvmField var message: String? = null
        @JvmField var missingPaths: List<String> = listOf()
    }

    class PackListResponse {
        @JvmField var packs: List<DlcServerPackInfo> = listOf()
        @JvmField var order: List<String> = listOf()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchServerInfo(entry: DlcServerEntry?, timeoutMs: Int): DlcServerInfo? {
        return fetchServerInfo(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchServerInfo(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerInfo? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val listReq = JsonObject()
            listReq.addProperty("type", "list")
            writeLine(writer, listReq)
            val response = readObject(reader) ?: return null
            if (!response.has("type") || response["type"].asString != "list") return null
            val info = DlcServerInfo()
            info.baseVersion = getString(response, "base")
            info.dlcVersion = getString(response, "dlc")
            info.remoteMissing = getInt(response, "remoteMissing")
            info.requiredMissing = getInt(response, "requiredMissing")
            info.hasRequired = response.has("hasRequired") && response["hasRequired"].asBoolean
            info.hasTaczMod = response.has("hasTaczMod") && response["hasTaczMod"].asBoolean
            info.modpackUrl = getString(response, "modpackUrl")
            info.updateGitRepo = getString(response, "updateGitRepo") ?: getString(response, "updateGithubRepo")
            if (!info.hasRequired && info.requiredMissing > 0) {
                info.hasRequired = true
            }
            val items = ArrayList<DlcServerItem>()
            if (response.has("items") && response["items"].isJsonArray) {
                response.getAsJsonArray("items").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcServerItem::class.java)?.let(items::add)
                    }
                }
            }
            info.items = items
            return info
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadMissingRemote(entry: DlcServerEntry?, timeoutMs: Int): Boolean {
        return downloadMissingRemote(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadMissingRemote(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val request = JsonObject()
            request.addProperty("type", "remote_download")
            writeLine(writer, request)
            val response = readObject(reader) ?: return false
            return response.has("ok") && response["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRemoteMissingList(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): List<DlcServerRemoteItem>? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val request = JsonObject()
            request.addProperty("type", "remote_list")
            addLang(request)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "remote_list") return null
            val items = ArrayList<DlcServerRemoteItem>()
            if (response.has("items") && response["items"].isJsonArray) {
                response.getAsJsonArray("items").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcServerRemoteItem::class.java)?.let(items::add)
                    }
                }
            }
            return items
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRemoteMissingList(entry: DlcServerEntry?, timeoutMs: Int): List<DlcServerRemoteItem>? {
        return fetchRemoteMissingList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startRemoteDownload(entry: DlcServerEntry?, timeoutMs: Int, keys: List<String>?, playerName: String?, playerUuid: String?): DlcServerRemoteStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val request = JsonObject()
            request.addProperty("type", "remote_download_start")
            addLang(request)
            addStringArray(request, "keys", keys)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "remote_download_start") return null
            return if (response.has("status") && response["status"].isJsonObject) {
                GSON.fromJson(response["status"], DlcServerRemoteStatus::class.java)
            } else {
                null
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startRemoteDownload(entry: DlcServerEntry?, timeoutMs: Int, keys: List<String>?): DlcServerRemoteStatus? {
        return startRemoteDownload(entry, timeoutMs, keys, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRemoteDownloadStatus(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerRemoteStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val request = JsonObject()
            request.addProperty("type", "remote_download_status")
            addLang(request)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "remote_download_status") return null
            return if (response.has("status") && response["status"].isJsonObject) {
                GSON.fromJson(response["status"], DlcServerRemoteStatus::class.java)
            } else {
                null
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRemoteDownloadStatus(entry: DlcServerEntry?, timeoutMs: Int): DlcServerRemoteStatus? {
        return fetchRemoteDownloadStatus(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRequiredList(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): List<DlcServerRequiredItem>? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val request = JsonObject()
            request.addProperty("type", "required_list")
            addLang(request)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "required_list") return null
            val items = ArrayList<DlcServerRequiredItem>()
            if (response.has("items") && response["items"].isJsonArray) {
                response.getAsJsonArray("items").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcServerRequiredItem::class.java)?.let(items::add)
                    }
                }
            }
            return items
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRequiredList(entry: DlcServerEntry?, timeoutMs: Int): List<DlcServerRequiredItem>? {
        return fetchRequiredList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startRequiredDownload(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?, playerName: String?, playerUuid: String?): DlcServerRequiredStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val request = JsonObject()
            request.addProperty("type", "required_download_start")
            addLang(request)
            addStringArray(request, "ids", ids)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "required_download_start") return null
            return if (response.has("status") && response["status"].isJsonObject) {
                GSON.fromJson(response["status"], DlcServerRequiredStatus::class.java)
            } else {
                null
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startRequiredDownload(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?): DlcServerRequiredStatus? {
        return startRequiredDownload(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRequiredDownloadStatus(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerRequiredStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val request = JsonObject()
            request.addProperty("type", "required_download_status")
            addLang(request)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "required_download_status") return null
            return if (response.has("status") && response["status"].isJsonObject) {
                GSON.fromJson(response["status"], DlcServerRequiredStatus::class.java)
            } else {
                null
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRequiredDownloadStatus(entry: DlcServerEntry?, timeoutMs: Int): DlcServerRequiredStatus? {
        return fetchRequiredDownloadStatus(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun cancelRequiredDownload(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val request = JsonObject()
            request.addProperty("type", "required_download_cancel")
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "required_download_cancel") return false
            return response.has("canceled") && response["canceled"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun cancelRequiredDownload(entry: DlcServerEntry?, timeoutMs: Int): Boolean {
        return cancelRequiredDownload(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uninstallRequired(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?, playerName: String?, playerUuid: String?): Boolean {
        return requestIdsBoolean(entry, timeoutMs, "required_uninstall", ids, playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uninstallRequired(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?): Boolean {
        return uninstallRequired(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyRequired(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?, playerName: String?, playerUuid: String?): Boolean {
        return requestIdsBoolean(entry, timeoutMs, "required_apply", ids, playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyRequired(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?): Boolean {
        return applyRequired(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyChanges(entry: DlcServerEntry?, timeoutMs: Int, items: List<DlcServerItem>?): Boolean {
        val response = applyChangesWithResponse(entry, timeoutMs, items, currentPlayerName(), currentPlayerUuid())
        return response != null && response.ok
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyChanges(entry: DlcServerEntry?, timeoutMs: Int, items: List<DlcServerItem>?, playerName: String?, playerUuid: String?): Boolean {
        val response = applyChangesWithResponse(entry, timeoutMs, items, playerName, playerUuid)
        return response != null && response.ok
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyChangesWithResponse(entry: DlcServerEntry?, timeoutMs: Int, items: List<DlcServerItem>?, playerName: String?, playerUuid: String?): ApplyResponse? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            items?.forEach { item ->
                val setReq = JsonObject()
                setReq.addProperty("type", "set")
                setReq.addProperty("id", item.id)
                setReq.addProperty("enabled", item.enabled)
                writeLine(writer, setReq)
                readObject(reader) ?: return null
            }
            val applyReq = JsonObject()
            applyReq.addProperty("type", "apply")
            writeLine(writer, applyReq)
            val applyResp = readObject(reader) ?: return null
            val response = ApplyResponse()
            response.ok = applyResp.has("ok") && applyResp["ok"].asBoolean
            response.requiresRestart = applyResp.has("requiresRestart") && applyResp["requiresRestart"].asBoolean
            return response
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyChangesWithResponse(entry: DlcServerEntry?, timeoutMs: Int, items: List<DlcServerItem>?): ApplyResponse? {
        return applyChangesWithResponse(entry, timeoutMs, items, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun restartServer(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): Boolean {
        return simpleOkRequest(entry, timeoutMs, "server_restart", playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun restartServer(entry: DlcServerEntry?, timeoutMs: Int): Boolean {
        return restartServer(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadFile(entry: DlcServerEntry?, timeoutMs: Int, path: String?, target: Path, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val request = JsonObject()
            request.addProperty("type", "file_download_init")
            request.addProperty("path", path ?: "")
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "file_download_init") return false
            if (!response.has("ok") || !response["ok"].asBoolean) return false
            target.parent?.let(Files::createDirectories)
            val temp = target.resolveSibling(target.fileName.toString() + ".part")
            var ok = false
            try {
                Files.newOutputStream(temp).use { out ->
                    while (true) {
                        val chunk = readObject(reader)
                        if (chunk == null || !chunk.has("type")) break
                        when (chunk["type"].asString) {
                            "file_download_chunk" -> {
                                val data = if (chunk.has("data")) chunk["data"].asString else ""
                                if (data.isNotBlank()) {
                                    out.write(Base64.getDecoder().decode(data))
                                }
                            }
                            "file_download_finish" -> {
                                ok = chunk.has("ok") && chunk["ok"].asBoolean
                                break
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                ok = false
            }
            if (ok) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                return true
            }
            try {
                Files.deleteIfExists(temp)
            } catch (_: IOException) {
            }
            return false
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadFile(entry: DlcServerEntry?, timeoutMs: Int, path: String?, target: Path): Boolean {
        return downloadFile(entry, timeoutMs, path, target, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchUpdates(entry: DlcServerEntry?, timeoutMs: Int): List<DlcServerUpdateInfo>? {
        return fetchUpdates(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchUpdates(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): List<DlcServerUpdateInfo>? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val listReq = JsonObject()
            listReq.addProperty("type", "update_list")
            writeLine(writer, listReq)
            val response = readObject(reader) ?: return null
            if (!response.has("type") || response["type"].asString != "update_list") return null
            val updates = ArrayList<DlcServerUpdateInfo>()
            if (response.has("updates") && response["updates"].isJsonArray) {
                response.getAsJsonArray("updates").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcServerUpdateInfo::class.java)?.let(updates::add)
                    }
                }
            }
            return updates
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchUpdateState(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerUpdateState? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "update_state")
            writeLine(writer, req)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "update_state") return null
            val state = DlcServerUpdateState()
            state.baseVersion = getString(response, "base")
            state.dlcVersion = getString(response, "dlc")
            state.rollbackFrom = getString(response, "rollbackFrom")
            state.modpackUrl = getString(response, "modpackUrl")
            state.updateGitRepo = getString(response, "updateGitRepo") ?: getString(response, "updateGithubRepo")
            val history = ArrayList<DlcUpdateMeta?>()
            if (response.has("history") && response["history"].isJsonArray) {
                response.getAsJsonArray("history").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcUpdateMeta::class.java)?.let(history::add)
                    }
                }
            }
            state.history = history
            val updates = ArrayList<DlcServerUpdateInfo?>()
            if (response.has("updates") && response["updates"].isJsonArray) {
                response.getAsJsonArray("updates").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcServerUpdateInfo::class.java)?.let(updates::add)
                    }
                }
            }
            state.updates = updates
            return state
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchUpdateState(entry: DlcServerEntry?, timeoutMs: Int): DlcServerUpdateState? {
        return fetchUpdateState(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyUpdate(
        entry: DlcServerEntry?,
        timeoutMs: Int,
        baseOld: String?,
        dlcOld: String?,
        baseNew: String?,
        dlcNew: String?,
        playerName: String?,
        playerUuid: String?,
    ): UpdateResponse? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val applyReq = JsonObject()
            applyReq.addProperty("type", "update_apply")
            if (!baseOld.isNullOrBlank()) applyReq.addProperty("baseOld", baseOld)
            if (!dlcOld.isNullOrBlank()) applyReq.addProperty("dlcOld", dlcOld)
            if (!baseNew.isNullOrBlank()) applyReq.addProperty("baseNew", baseNew)
            if (!dlcNew.isNullOrBlank()) applyReq.addProperty("dlcNew", dlcNew)
            writeLine(writer, applyReq)
            val response = readObject(reader) ?: return null
            val result = UpdateResponse()
            result.ok = response.has("ok") && response["ok"].asBoolean
            result.missing = response.has("missing") && response["missing"].asBoolean
            result.message = getString(response, "message")
            if (response.has("missing") && response["missing"].isJsonArray) {
                val missing = ArrayList<String>()
                response.getAsJsonArray("missing").forEach { element ->
                    if (element.isJsonPrimitive) {
                        missing.add(element.asString)
                    }
                }
                result.missingPaths = missing
            }
            return result
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyUpdate(entry: DlcServerEntry?, timeoutMs: Int, baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?): UpdateResponse? {
        return applyUpdate(entry, timeoutMs, baseOld, dlcOld, baseNew, dlcNew, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun rollbackUpdate(entry: DlcServerEntry?, timeoutMs: Int, targetVersion: String?, playerName: String?, playerUuid: String?): UpdateResponse? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "update_rollback")
            req.addProperty("target", targetVersion ?: "")
            writeLine(writer, req)
            val resp = readObject(reader) ?: return null
            val result = UpdateResponse()
            result.ok = resp.has("ok") && resp["ok"].asBoolean
            result.message = getString(resp, "message")
            return result
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun rollbackUpdate(entry: DlcServerEntry?, timeoutMs: Int, targetVersion: String?): UpdateResponse? {
        return rollbackUpdate(entry, timeoutMs, targetVersion, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importUpdatePackage(entry: DlcServerEntry?, timeoutMs: Int, source: Path?, playerName: String?, playerUuid: String?): Boolean {
        if (source == null || !Files.exists(source)) return false
        val config = fetchServerConfig(entry, timeoutMs, playerName, playerUuid)
        var dlcRoot = getDlcRootFromConfig(config)
        if (dlcRoot.isNullOrBlank()) {
            dlcRoot = "DLC"
        }
        var normalizedRoot = dlcRoot.replace("\\", "/")
        if (!normalizedRoot.endsWith("/")) {
            normalizedRoot += "/"
        }
        val targetPath = normalizedRoot + "update/" + source.fileName
        if (!uploadFile(entry, timeoutMs, targetPath, source, playerName, playerUuid)) return false
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "update_import")
            req.addProperty("path", targetPath)
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importUpdatePackage(entry: DlcServerEntry?, timeoutMs: Int, source: Path?): Boolean {
        return importUpdatePackage(entry, timeoutMs, source, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchPacks(entry: DlcServerEntry?, timeoutMs: Int): List<DlcServerPackInfo>? {
        return fetchPackList(entry, timeoutMs)?.packs
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchPacks(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): List<DlcServerPackInfo>? {
        return fetchPackList(entry, timeoutMs, playerName, playerUuid)?.packs
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchPackList(entry: DlcServerEntry?, timeoutMs: Int): PackListResponse? {
        return fetchPackList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchPackList(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): PackListResponse? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "pack_list")
            writeLine(writer, req)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != "pack_list") return null
            val packs = ArrayList<DlcServerPackInfo>()
            if (response.has("packs") && response["packs"].isJsonArray) {
                response.getAsJsonArray("packs").forEach { element ->
                    if (element.isJsonObject) {
                        GSON.fromJson(element, DlcServerPackInfo::class.java)?.let(packs::add)
                    }
                }
            }
            val order = ArrayList<String>()
            if (response.has("order") && response["order"].isJsonArray) {
                response.getAsJsonArray("order").forEach { element ->
                    if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        order.add(element.asString)
                    }
                }
            }
            val result = PackListResponse()
            result.packs = packs
            result.order = order
            return result
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun savePackOrder(entry: DlcServerEntry?, timeoutMs: Int, order: List<String>?): UpdateResponse? {
        return savePackOrder(entry, timeoutMs, order, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun savePackOrder(entry: DlcServerEntry?, timeoutMs: Int, order: List<String>?, playerName: String?, playerUuid: String?): UpdateResponse? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "pack_order")
            val array = JsonArray()
            order?.forEach { array.add(it) }
            req.add("order", array)
            writeLine(writer, req)
            val response = readObject(reader) ?: return null
            val result = UpdateResponse()
            result.ok = response.has("ok") && response["ok"].asBoolean
            result.message = getString(response, "message")
            return result
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun installPack(entry: DlcServerEntry?, timeoutMs: Int, archivePath: String?): UpdateResponse? {
        return packAction(entry, timeoutMs, "pack_install", archivePath, null, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startPackApply(entry: DlcServerEntry?, timeoutMs: Int, order: List<String>?, uninstallIdentifiers: List<String>?, installArchives: List<String>?): DlcServerPackApplyStatus? {
        return startPackApply(entry, timeoutMs, order, uninstallIdentifiers, installArchives, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startPackApply(
        entry: DlcServerEntry?,
        timeoutMs: Int,
        order: List<String>?,
        uninstallIdentifiers: List<String>?,
        installArchives: List<String>?,
        playerName: String?,
        playerUuid: String?,
    ): DlcServerPackApplyStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "pack_apply_start")
            req.add("order", GSON.toJsonTree(order ?: emptyList<String>()))
            req.add("uninstallIdentifiers", GSON.toJsonTree(uninstallIdentifiers ?: emptyList<String>()))
            req.add("installArchives", GSON.toJsonTree(installArchives ?: emptyList<String>()))
            writeLine(writer, req)
            val response = readObject(reader) ?: return null
            return readPackApplyStatus(response)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchPackApplyStatus(entry: DlcServerEntry?, timeoutMs: Int): DlcServerPackApplyStatus? {
        return fetchPackApplyStatus(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchPackApplyStatus(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerPackApplyStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "pack_apply_status")
            writeLine(writer, req)
            val response = readObject(reader) ?: return null
            return readPackApplyStatus(response)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun pausePackApply(entry: DlcServerEntry?, timeoutMs: Int): DlcServerPackApplyStatus? {
        return controlPackApply(entry, timeoutMs, "pack_apply_pause", currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun pausePackApply(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerPackApplyStatus? {
        return controlPackApply(entry, timeoutMs, "pack_apply_pause", playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun resumePackApply(entry: DlcServerEntry?, timeoutMs: Int): DlcServerPackApplyStatus? {
        return controlPackApply(entry, timeoutMs, "pack_apply_resume", currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun resumePackApply(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerPackApplyStatus? {
        return controlPackApply(entry, timeoutMs, "pack_apply_resume", playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun cancelPackApply(entry: DlcServerEntry?, timeoutMs: Int): DlcServerPackApplyStatus? {
        return controlPackApply(entry, timeoutMs, "pack_apply_cancel", currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun cancelPackApply(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerPackApplyStatus? {
        return controlPackApply(entry, timeoutMs, "pack_apply_cancel", playerName, playerUuid)
    }

    @Throws(IOException::class)
    private fun controlPackApply(entry: DlcServerEntry?, timeoutMs: Int, type: String, playerName: String?, playerUuid: String?): DlcServerPackApplyStatus? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", type)
            writeLine(writer, req)
            val response = readObject(reader) ?: return null
            return readPackApplyStatus(response)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uninstallPack(entry: DlcServerEntry?, timeoutMs: Int, identifier: String?): UpdateResponse? {
        return packAction(entry, timeoutMs, "pack_uninstall", null, identifier, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun deletePack(entry: DlcServerEntry?, timeoutMs: Int, archivePath: String?): UpdateResponse? {
        return packAction(entry, timeoutMs, "pack_delete", archivePath, null, currentPlayerName(), currentPlayerUuid())
    }

    @Throws(IOException::class)
    private fun packAction(
        entry: DlcServerEntry?,
        timeoutMs: Int,
        type: String,
        archivePath: String?,
        identifier: String?,
        playerName: String?,
        playerUuid: String?,
    ): UpdateResponse? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", type)
            if (!archivePath.isNullOrBlank()) req.addProperty("archivePath", archivePath)
            if (!identifier.isNullOrBlank()) req.addProperty("identifier", identifier)
            writeLine(writer, req)
            val response = readObject(reader) ?: return null
            val result = UpdateResponse()
            result.ok = response.has("ok") && response["ok"].asBoolean
            result.message = getString(response, "message")
            return result
        }
    }

    private fun readPackApplyStatus(response: JsonObject): DlcServerPackApplyStatus? {
        if (response.has("status") && response["status"].isJsonObject) {
            return GSON.fromJson(response["status"], DlcServerPackApplyStatus::class.java)
        }
        if (response.has("type") && response["type"].asString == "pack_apply_status") {
            return GSON.fromJson(response, DlcServerPackApplyStatus::class.java)
        }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importPackPackage(entry: DlcServerEntry?, timeoutMs: Int, source: Path?, playerName: String?, playerUuid: String?): Boolean {
        if (source == null || !Files.exists(source)) return false
        val config = fetchServerConfig(entry, timeoutMs, playerName, playerUuid)
        var dlcRoot = getDlcRootFromConfig(config)
        if (dlcRoot.isNullOrBlank()) {
            dlcRoot = "DLC"
        }
        var normalizedRoot = dlcRoot.replace("\\", "/")
        if (!normalizedRoot.endsWith("/")) {
            normalizedRoot += "/"
        }
        var name = source.fileName?.toString() ?: "pack.zip"
        if (!name.lowercase(java.util.Locale.ROOT).endsWith(".zip")) {
            name += ".zip"
        }
        val targetPath = normalizedRoot + "pack/" + name
        if (!uploadFile(entry, timeoutMs, targetPath, source, playerName, playerUuid)) return false
        return importPackPackagePath(entry, timeoutMs, targetPath, playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importPackPackage(entry: DlcServerEntry?, timeoutMs: Int, source: Path?): Boolean {
        return importPackPackage(entry, timeoutMs, source, currentPlayerName(), currentPlayerUuid())
    }

    @Throws(IOException::class)
    private fun importPackPackagePath(entry: DlcServerEntry?, timeoutMs: Int, targetPath: String, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "pack_import")
            req.addProperty("path", targetPath)
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importPackPackageFromUrl(entry: DlcServerEntry?, timeoutMs: Int, url: String?, fileName: String?): Boolean {
        return importPackPackageFromUrl(entry, timeoutMs, url, fileName, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importPackPackageFromUrl(entry: DlcServerEntry?, timeoutMs: Int, url: String?, fileName: String?, playerName: String?, playerUuid: String?): Boolean {
        if (url.isNullOrBlank()) return false
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "pack_import_url")
            req.addProperty("url", url)
            if (!fileName.isNullOrBlank()) {
                req.addProperty("fileName", fileName)
            }
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importUpdatePackageFromUrl(entry: DlcServerEntry?, timeoutMs: Int, url: String?, fileName: String?, playerName: String?, playerUuid: String?): Boolean {
        if (url.isNullOrBlank()) return false
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "update_import_url")
            req.addProperty("url", url)
            if (!fileName.isNullOrBlank()) {
                req.addProperty("fileName", fileName)
            }
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importUpdatePackageFromUrl(entry: DlcServerEntry?, timeoutMs: Int, url: String?, fileName: String?): Boolean {
        return importUpdatePackageFromUrl(entry, timeoutMs, url, fileName, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyUpdates(entry: DlcServerEntry?, timeoutMs: Int): Boolean {
        return applyUpdates(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyUpdates(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): Boolean {
        return simpleOkRequest(entry, timeoutMs, "update_apply", playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun push(entry: DlcServerEntry?, timeoutMs: Int, targets: List<String>?, mode: String?, playerName: String?, playerUuid: String?): Boolean {
        return push(entry, timeoutMs, targets, null, mode, playerName, playerUuid)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun push(entry: DlcServerEntry?, timeoutMs: Int, targets: List<String>?, mode: String?): Boolean {
        return push(entry, timeoutMs, targets, mode, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun push(entry: DlcServerEntry?, timeoutMs: Int, targets: List<String>?, files: List<String>?, mode: String?, playerName: String?, playerUuid: String?): Boolean {
        val zipPath = buildPushZip(targets, files) ?: return false
        val transferId = UUID.randomUUID().toString()
        try {
            openSocket(entry, timeoutMs).use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
                if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
                val init = JsonObject()
                init.addProperty("type", "push_init")
                init.addProperty("id", transferId)
                init.addProperty("mode", mode)
                init.add("targets", GSON.toJsonTree(targets))
                if (!files.isNullOrEmpty()) {
                    init.add("paths", GSON.toJsonTree(files))
                }
                init.addProperty("size", Files.size(zipPath))
                writeLine(writer, init)
                val initResp = readObject(reader)
                if (initResp == null || !initResp.has("ok") || !initResp["ok"].asBoolean) return false
                Files.newInputStream(zipPath).use { input ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    while (true) {
                        val len = input.read(buffer)
                        if (len <= 0) break
                        val data = Base64.getEncoder().encodeToString(copyOf(buffer, len))
                        val chunk = JsonObject()
                        chunk.addProperty("type", "push_chunk")
                        chunk.addProperty("id", transferId)
                        chunk.addProperty("data", data)
                        writeLine(writer, chunk)
                    }
                }
                val finish = JsonObject()
                finish.addProperty("type", "push_finish")
                finish.addProperty("id", transferId)
                writeLine(writer, finish)
                val finishResp = readObject(reader)
                return finishResp != null && finishResp.has("ok") && finishResp["ok"].asBoolean
            }
        } finally {
            try {
                Files.deleteIfExists(zipPath)
            } catch (_: IOException) {
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun push(entry: DlcServerEntry?, timeoutMs: Int, targets: List<String>?, files: List<String>?, mode: String?): Boolean {
        return push(entry, timeoutMs, targets, files, mode, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun listFiles(entry: DlcServerEntry?, timeoutMs: Int, path: String?, playerName: String?, playerUuid: String?): List<DlcServerFileEntry>? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "file_list")
            req.addProperty("path", path ?: "")
            writeLine(writer, req)
            val resp = readObject(reader)
            if (resp == null || !resp.has("ok") || !resp["ok"].asBoolean) return null
            val entries = ArrayList<DlcServerFileEntry>()
            if (resp.has("entries") && resp["entries"].isJsonArray) {
                resp.getAsJsonArray("entries").forEach { element ->
                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val entryObj = DlcServerFileEntry()
                        entryObj.name = if (obj.has("name") && !obj["name"].isJsonNull) obj["name"].asString else ""
                        entryObj.dir = obj.has("dir") && !obj["dir"].isJsonNull && obj["dir"].asBoolean
                        entryObj.size = if (obj.has("size") && !obj["size"].isJsonNull) obj["size"].asLong else 0L
                        entryObj.modified = if (obj.has("modified") && !obj["modified"].isJsonNull) obj["modified"].asLong else 0L
                        entries.add(entryObj)
                    }
                }
            }
            return entries
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun listFiles(entry: DlcServerEntry?, timeoutMs: Int, path: String?): List<DlcServerFileEntry>? {
        return listFiles(entry, timeoutMs, path, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun deleteFile(entry: DlcServerEntry?, timeoutMs: Int, path: String?, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "file_delete")
            req.addProperty("path", path ?: "")
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun deleteFile(entry: DlcServerEntry?, timeoutMs: Int, path: String?): Boolean {
        return deleteFile(entry, timeoutMs, path, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uploadFile(entry: DlcServerEntry?, timeoutMs: Int, targetPath: String?, source: Path?, playerName: String?, playerUuid: String?): Boolean {
        if (source == null || !Files.exists(source)) return false
        val transferId = UUID.randomUUID().toString()
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val init = JsonObject()
            init.addProperty("type", "file_upload_init")
            init.addProperty("id", transferId)
            init.addProperty("path", targetPath ?: "")
            init.addProperty("size", Files.size(source))
            writeLine(writer, init)
            val initResp = readObject(reader)
            if (initResp == null || !initResp.has("ok") || !initResp["ok"].asBoolean) return false
            Files.newInputStream(source).use { input ->
                val buffer = ByteArray(CHUNK_SIZE)
                while (true) {
                    val len = input.read(buffer)
                    if (len <= 0) break
                    val data = Base64.getEncoder().encodeToString(copyOf(buffer, len))
                    val chunk = JsonObject()
                    chunk.addProperty("type", "file_upload_chunk")
                    chunk.addProperty("id", transferId)
                    chunk.addProperty("data", data)
                    writeLine(writer, chunk)
                }
            }
            val finish = JsonObject()
            finish.addProperty("type", "file_upload_finish")
            finish.addProperty("id", transferId)
            writeLine(writer, finish)
            val finishResp = readObject(reader)
            return finishResp != null && finishResp.has("ok") && finishResp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uploadFile(entry: DlcServerEntry?, timeoutMs: Int, targetPath: String?, source: Path?): Boolean {
        return uploadFile(entry, timeoutMs, targetPath, source, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRconConfig(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerRconConfig? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "rcon_get")
            writeLine(writer, req)
            val resp = readObject(reader)
            if (resp == null || !resp.has("ok") || !resp["ok"].asBoolean) return null
            val config = DlcServerRconConfig()
            config.host = getString(resp, "host")
            config.port = if (resp.has("port")) resp["port"].asInt else 0
            config.password = getString(resp, "password")
            return config
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRconConfig(entry: DlcServerEntry?, timeoutMs: Int): DlcServerRconConfig? {
        return fetchRconConfig(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRconInfo(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerRconInfo? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "rcon_info")
            writeLine(writer, req)
            val resp = readObject(reader)
            if (resp == null || !resp.has("ok") || !resp["ok"].asBoolean) return null
            val info = DlcServerRconInfo()
            info.enabled = resp.has("enabled") && resp["enabled"].asBoolean
            info.port = if (resp.has("port")) resp["port"].asInt else 25575
            info.passwordSet = resp.has("passwordSet") && resp["passwordSet"].asBoolean
            return info
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRconInfo(entry: DlcServerEntry?, timeoutMs: Int): DlcServerRconInfo? {
        return fetchRconInfo(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveRconConfig(entry: DlcServerEntry?, timeoutMs: Int, config: DlcServerRconConfig?, playerName: String?, playerUuid: String?): Boolean {
        if (config == null) return false
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "rcon_set")
            req.addProperty("host", config.host ?: "")
            req.addProperty("port", config.port)
            req.addProperty("password", config.password ?: "")
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveRconConfig(entry: DlcServerEntry?, timeoutMs: Int, config: DlcServerRconConfig?): Boolean {
        return saveRconConfig(entry, timeoutMs, config, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun execRcon(entry: DlcServerEntry?, timeoutMs: Int, command: String?, playerName: String?, playerUuid: String?): String? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "rcon_exec")
            req.addProperty("command", command ?: "")
            writeLine(writer, req)
            val resp = readObject(reader) ?: return null
            if (resp.has("message")) {
                return resp["message"].asString
            }
            return if (resp.has("ok") && resp["ok"].asBoolean) "ok" else null
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun execRcon(entry: DlcServerEntry?, timeoutMs: Int, command: String?): String? {
        return execRcon(entry, timeoutMs, command, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchServerConfig(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): JsonObject? {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return null
            val req = JsonObject()
            req.addProperty("type", "config_get")
            writeLine(writer, req)
            val resp = readObject(reader)
            if (resp == null || !resp.has("ok") || !resp["ok"].asBoolean) return null
            return if (resp.has("config") && resp["config"].isJsonObject) {
                resp.getAsJsonObject("config")
            } else {
                null
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchServerConfig(entry: DlcServerEntry?, timeoutMs: Int): JsonObject? {
        return fetchServerConfig(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveServerConfig(entry: DlcServerEntry?, timeoutMs: Int, config: JsonObject?, playerName: String?, playerUuid: String?): Boolean {
        if (config == null) return false
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val req = JsonObject()
            req.addProperty("type", "config_set")
            req.add("config", config)
            writeLine(writer, req)
            val resp = readObject(reader)
            return resp != null && resp.has("ok") && resp["ok"].asBoolean
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveServerConfig(entry: DlcServerEntry?, timeoutMs: Int, config: JsonObject?): Boolean {
        return saveServerConfig(entry, timeoutMs, config, currentPlayerName(), currentPlayerUuid())
    }

    @Throws(IOException::class)
    private fun requestIdsBoolean(entry: DlcServerEntry?, timeoutMs: Int, type: String, ids: List<String>?, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val request = JsonObject()
            request.addProperty("type", type)
            addStringArray(request, "ids", ids)
            writeLine(writer, request)
            val response = readObject(reader)
            if (response == null || !response.has("type") || response["type"].asString != type) return false
            return response.has("ok") && response["ok"].asBoolean
        }
    }

    @Throws(IOException::class)
    private fun simpleOkRequest(entry: DlcServerEntry?, timeoutMs: Int, type: String, playerName: String?, playerUuid: String?): Boolean {
        openSocket(entry, timeoutMs).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) return false
            val request = JsonObject()
            request.addProperty("type", type)
            writeLine(writer, request)
            val response = readObject(reader) ?: return false
            return response.has("ok") && response["ok"].asBoolean
        }
    }

    private fun addStringArray(request: JsonObject, key: String, values: List<String>?) {
        if (values.isNullOrEmpty()) return
        val arr = JsonArray()
        values.forEach { value ->
            if (value.isNotBlank()) {
                arr.add(value)
            }
        }
        request.add(key, arr)
    }

    @Throws(IOException::class)
    private fun openSocket(entry: DlcServerEntry?, timeoutMs: Int): Socket {
        val actualEntry = entry ?: throw NullPointerException("entry")
        val host = actualEntry.host ?: ""
        val socket = Socket()
        socket.connect(InetSocketAddress(host, actualEntry.port), timeoutMs)
        socket.soTimeout = timeoutMs
        return socket
    }

    @Throws(IOException::class)
    private fun sendHello(entry: DlcServerEntry?, reader: BufferedReader, writer: BufferedWriter, playerName: String?, playerUuid: String?): Boolean {
        val hello = JsonObject()
        hello.addProperty("type", "hello")
        val actualEntry = entry ?: throw NullPointerException("entry")
        val password = actualEntry.password ?: ""
        hello.addProperty("password", password)
        addLang(hello)
        if (!playerName.isNullOrBlank()) {
            hello.addProperty("player", playerName)
        }
        if (!playerUuid.isNullOrBlank()) {
            hello.addProperty("uuid", playerUuid)
        }
        writeLine(writer, hello)
        val response = readObject(reader) ?: throw IOException("no response")
        if (!response.has("type") || response["type"].asString != "hello") {
            throw IOException("invalid response")
        }
        val ok = response.has("ok") && response["ok"].asBoolean
        if (!ok) {
            val reason = if (response.has("reason")) response["reason"].asString else "unauthorized"
            throw IOException("auth_failed:$reason")
        }
        return true
    }

    private fun addLang(obj: JsonObject?) {
        if (obj == null) return
        val lang = CrossPlatformRuntime.languageCode()
        if (!lang.isNullOrBlank()) {
            obj.addProperty("lang", lang)
        }
    }

    private fun currentPlayerName(): String? {
        return CrossPlatformRuntime.playerName()
    }

    private fun currentPlayerUuid(): String? {
        return CrossPlatformRuntime.playerUuid()
    }

    @Throws(IOException::class)
    private fun readObject(reader: BufferedReader): JsonObject? {
        val line = reader.readLine()
        if (line.isNullOrBlank()) return null
        return GSON.fromJson(line, JsonObject::class.java)
    }

    @Throws(IOException::class)
    private fun writeLine(writer: BufferedWriter, obj: JsonObject) {
        writer.write(GSON.toJson(obj))
        writer.newLine()
        writer.flush()
    }

    private fun getString(obj: JsonObject?, key: String?): String? {
        if (obj == null || key == null) return null
        if (!obj.has(key)) return null
        if (obj[key].isJsonNull) return null
        return obj[key].asString
    }

    private fun getInt(obj: JsonObject?, key: String?): Int {
        if (obj == null || key == null || !obj.has(key)) return 0
        return try {
            obj[key].asInt
        } catch (_: Exception) {
            0
        }
    }

    private fun getDlcRootFromConfig(config: JsonObject?): String? {
        if (config == null) return ""
        val paths = DlcDcConfigIO.getObjectIgnoreCase(config, "paths") ?: return ""
        return DlcDcConfigIO.getStringIgnoreCase(paths, "dlcRoot")
    }

    private fun copyOf(input: ByteArray, len: Int): ByteArray {
        val out = ByteArray(len)
        System.arraycopy(input, 0, out, 0, len)
        return out
    }

    @Throws(IOException::class)
    private fun buildPushZip(targets: List<String>?, files: List<String>?): Path? {
        val hasTargets = !targets.isNullOrEmpty()
        val hasFiles = !files.isNullOrEmpty()
        if (!hasTargets && !hasFiles) return null
        val includeAll = hasTargets && targets!!.any { it.equals("all", ignoreCase = true) }
        val includeDlc = includeAll || (hasTargets && targets!!.any { it.equals("dlc", ignoreCase = true) })
        val includeConfig = includeAll || (hasTargets && targets!!.any { it.equals("config", ignoreCase = true) })
        val dlcRoot = DlcConfig.getDlcRoot()
        val configDir = CrossPlatformRuntime.configDir().resolve("dlc_manager")
        val gameDir = DlcPaths.gameDir()
        val tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops") ?: return null
        Files.createDirectories(tempDir)
        val zipPath = tempDir.resolve("push_" + System.currentTimeMillis() + ".zip")
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            if (includeDlc && Files.exists(dlcRoot)) {
                zipDirectory(dlcRoot, out, "dlc/", DlcTmpPaths.TMP_DIR)
            }
            if (includeConfig && Files.exists(configDir)) {
                zipDirectory(configDir, out, "config/", null)
            }
            if (hasFiles && Files.exists(gameDir)) {
                zipSelectedFiles(gameDir, files, out, "root/")
            }
        }
        return zipPath
    }

    @Throws(IOException::class)
    private fun zipDirectory(root: Path, out: ZipOutputStream, prefix: String, skipDirName: String?) {
        val stream = Files.walk(root)
        try {
            stream.forEach { path ->
                try {
                    if (Files.isDirectory(path)) return@forEach
                    val relative = root.relativize(path)
                    if (relative.toString().isBlank()) return@forEach
                    if (skipDirName != null && relative.nameCount > 0) {
                        val first = relative.getName(0).toString()
                        if (skipDirName.equals(first, ignoreCase = true) ||
                            DlcTmpPaths.TMP_DIR.equals(first, ignoreCase = true) ||
                            DlcTmpPaths.LEGACY_TMP_DIR.equals(first, ignoreCase = true)
                        ) {
                            return@forEach
                        }
                    }
                    val entryName = prefix + relative.toString().replace('\\', '/')
                    val entry = ZipEntry(entryName)
                    out.putNextEntry(entry)
                    Files.newInputStream(path).use { input ->
                        val buffer = ByteArray(CHUNK_SIZE)
                        while (true) {
                            val len = input.read(buffer)
                            if (len <= 0) break
                            out.write(buffer, 0, len)
                        }
                    }
                    out.closeEntry()
                } catch (_: IOException) {
                }
            }
        } finally {
            stream.close()
        }
    }

    @Throws(IOException::class)
    private fun zipSelectedFiles(root: Path, files: List<String>?, out: ZipOutputStream, prefix: String) {
        if (files.isNullOrEmpty()) return
        for (raw in files) {
            if (raw.isBlank()) continue
            val target = root.resolve(raw).normalize()
            if (!target.startsWith(root)) continue
            if (!Files.exists(target)) continue
            if (Files.isDirectory(target)) {
                zipDirectory(target, out, prefix + root.relativize(target).toString().replace('\\', '/') + "/", null)
                continue
            }
            zipFile(root, target, out, prefix)
        }
    }

    @Throws(IOException::class)
    private fun zipFile(root: Path, file: Path, out: ZipOutputStream, prefix: String) {
        val relative = root.relativize(file)
        if (relative.toString().isBlank()) return
        val entryName = prefix + relative.toString().replace('\\', '/')
        val entry = ZipEntry(entryName)
        out.putNextEntry(entry)
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            while (true) {
                val len = input.read(buffer)
                if (len <= 0) break
                out.write(buffer, 0, len)
            }
        }
        out.closeEntry()
    }
}
