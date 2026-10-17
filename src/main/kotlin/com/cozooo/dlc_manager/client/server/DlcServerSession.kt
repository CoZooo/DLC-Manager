package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
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

class DlcServerSession private constructor(private val socket: Socket) : AutoCloseable {
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))

    @Throws(IOException::class)
    fun fetchList(): DlcServerInfo {
        val response = request("list", null)
        if (string(response, "type") != "list") {
            throw IOException("invalid list response")
        }
        val info = DlcServerInfo()
        info.baseVersion = string(response, "base")
        info.dlcVersion = string(response, "dlc")
        info.mcVersion = string(response, "mcVersion")
        info.loaderName = string(response, "loaderName")
        info.remoteMissing = intValue(response, "remoteMissing")
        info.requiredMissing = intValue(response, "requiredMissing")
        info.hasRequired = bool(response, "hasRequired")
        info.hasTaczMod = bool(response, "hasTaczMod")
        info.modpackUrl = string(response, "modpackUrl")
        info.updateGitRepo = firstNonBlank(string(response, "updateGitRepo"), string(response, "updateGithubRepo"))
        if (!info.hasRequired && info.requiredMissing > 0) {
            info.hasRequired = true
        }
        val items = ArrayList<DlcServerItem>()
        array(response, "items")?.forEach { element ->
            if (element != null && element.isJsonObject) {
                GSON.fromJson(element, DlcServerItem::class.java)?.let(items::add)
            }
        }
        info.items = items
        val presets = ArrayList<DlcServerPresetItem>()
        array(response, "presets")?.forEach { element ->
            if (element != null && element.isJsonObject) {
                GSON.fromJson(element, DlcServerPresetItem::class.java)?.let(presets::add)
            }
        }
        info.presets = presets
        val redirects = ArrayList<DlcServerRedirectItem>()
        array(response, "redirects")?.forEach { element ->
            if (element != null && element.isJsonObject) {
                GSON.fromJson(element, DlcServerRedirectItem::class.java)?.let(redirects::add)
            }
        }
        info.redirects = redirects
        return info
    }

    @Throws(IOException::class)
    fun listFiles(path: String?): List<DlcServerFileEntry> {
        val payload = JsonObject()
        payload.addProperty("path", path ?: "")
        val response = rawRequest("file_list", payload)
        requireOk(response, "file_list")
        val entries = ArrayList<DlcServerFileEntry>()
        array(response, "entries")?.forEach { element ->
            if (element != null && element.isJsonObject) {
                GSON.fromJson(element, DlcServerFileEntry::class.java)?.let(entries::add)
            }
        }
        return entries
    }

    @Throws(IOException::class)
    fun uploadFile(remotePath: String?, localPath: Path?) {
        if (localPath == null || !Files.isRegularFile(localPath)) {
            throw IOException("missing local file: $localPath")
        }
        val id = UUID.randomUUID().toString()
        val init = JsonObject()
        init.addProperty("id", id)
        init.addProperty("path", remotePath ?: "")
        init.addProperty("size", Files.size(localPath))
        requireOk(rawRequest("file_upload_init", init), "file_upload_init")
        val buffer = ByteArray(CHUNK_SIZE)
        Files.newInputStream(localPath).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val chunk = JsonObject()
                chunk.addProperty("id", id)
                chunk.addProperty("data", Base64.getEncoder().encodeToString(buffer.copyOf(read)))
                send(withType("file_upload_chunk", chunk))
            }
        }
        val finish = JsonObject()
        finish.addProperty("id", id)
        requireOk(rawRequest("file_upload_finish", finish), "file_upload_finish")
    }

    @Throws(IOException::class)
    fun deleteFile(remotePath: String?) {
        val payload = JsonObject()
        payload.addProperty("path", remotePath ?: "")
        requireOk(rawRequest("file_delete", payload), "file_delete")
    }

    @Throws(IOException::class)
    fun setEnabled(id: String?, enabled: Boolean) {
        if (id.isNullOrBlank()) return
        val payload = JsonObject()
        payload.addProperty("id", id)
        payload.addProperty("enabled", enabled)
        requireOk(rawRequest("set", payload), "set")
    }

    @Throws(IOException::class)
    fun apply(): DlcServerConnection.ApplyResponse = applyWithMessages()

    @Throws(IOException::class)
    fun applyWithMessages(): DlcServerConnection.ApplyResponse {
        val response = rawRequest("apply", null)
        requireOk(response, "apply")
        val result = DlcServerConnection.ApplyResponse()
        result.ok = true
        result.requiresRestart = bool(response, "requiresRestart")
        result.messages = stringList(response, "messages")
        return result
    }

    @Throws(IOException::class) fun remoteList(): JsonObject = checked("remote_list")
    @Throws(IOException::class) fun remoteDownload(): JsonObject = rawRequest("remote_download", null)
    @Throws(IOException::class) fun remoteDownloadStart(keys: List<String>?): JsonObject = checkedWithStrings("remote_download_start", "keys", keys)
    @Throws(IOException::class) fun remoteDownloadStatus(): JsonObject = checked("remote_download_status")
    @Throws(IOException::class) fun remoteDownloadCancel(): JsonObject = checked("remote_download_cancel")
    @Throws(IOException::class) fun requiredList(): JsonObject = checked("required_list")
    @Throws(IOException::class) fun requiredDownloadStart(ids: List<String>?): JsonObject = checkedWithStrings("required_download_start", "ids", ids)
    @Throws(IOException::class) fun requiredDownloadStatus(): JsonObject = checked("required_download_status")
    @Throws(IOException::class) fun requiredDownloadCancel(): JsonObject = checked("required_download_cancel")
    @Throws(IOException::class) fun requiredUninstall(ids: List<String>?): JsonObject = checkedWithStrings("required_uninstall", "ids", ids)
    @Throws(IOException::class) fun requiredApply(ids: List<String>?): JsonObject = checkedWithStrings("required_apply", "ids", ids)
    @Throws(IOException::class) fun updateState(): JsonObject = checked("update_state")
    @Throws(IOException::class) fun updateList(): JsonObject = checked("update_list")

    @Throws(IOException::class)
    fun updateApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?): JsonObject {
        val payload = JsonObject()
        addIfNotBlank(payload, "baseOld", baseOld)
        addIfNotBlank(payload, "dlcOld", dlcOld)
        addIfNotBlank(payload, "baseNew", baseNew)
        addIfNotBlank(payload, "dlcNew", dlcNew)
        val response = rawRequest("update_apply", payload)
        if (!bool(response, "ok") && !bool(response, "missing")) {
            requireOk(response, "update_apply")
        }
        return response
    }

    @Throws(IOException::class) fun updateRollback(target: String?): JsonObject = checkedWithString("update_rollback", "target", target)
    @Throws(IOException::class) fun updateImport(path: String?): JsonObject = checkedWithString("update_import", "path", path)
    @Throws(IOException::class)
    fun updateImportUrl(url: String?, fileName: String?): JsonObject {
        val payload = JsonObject()
        addIfNotBlank(payload, "url", url)
        addIfNotBlank(payload, "fileName", fileName)
        return checked("update_import_url", payload)
    }

    @Throws(IOException::class) fun packList(): JsonObject = checked("pack_list")
    @Throws(IOException::class) fun packInstall(archivePath: String?): JsonObject = checkedWithString("pack_install", "archivePath", archivePath)
    @Throws(IOException::class) fun packUninstall(identifier: String?): JsonObject = checkedWithString("pack_uninstall", "identifier", identifier)
    @Throws(IOException::class) fun packDelete(archivePath: String?): JsonObject = checkedWithString("pack_delete", "archivePath", archivePath)
    @Throws(IOException::class) fun packImport(path: String?): JsonObject = checkedWithString("pack_import", "path", path)
    @Throws(IOException::class)
    fun packImportUrl(url: String?, fileName: String?): JsonObject {
        val payload = JsonObject()
        addIfNotBlank(payload, "url", url)
        addIfNotBlank(payload, "fileName", fileName)
        return checked("pack_import_url", payload)
    }
    @Throws(IOException::class) fun packOrder(order: List<String>?): JsonObject = checkedWithStrings("pack_order", "order", order)
    @Throws(IOException::class) fun packApplyStart(): JsonObject = packApplyStart(null, null, null)
    @Throws(IOException::class)
    fun packApplyStart(order: List<String>?, uninstallIdentifiers: List<String>?, installArchives: List<String>?): JsonObject {
        val payload = JsonObject()
        payload.add("order", GSON.toJsonTree(order ?: listOf<String>()))
        payload.add("uninstallIdentifiers", GSON.toJsonTree(uninstallIdentifiers ?: listOf<String>()))
        payload.add("installArchives", GSON.toJsonTree(installArchives ?: listOf<String>()))
        return checked("pack_apply_start", payload)
    }
    @Throws(IOException::class) fun packApplyStatus(): JsonObject = checked("pack_apply_status")
    @Throws(IOException::class) fun packApplyPause(): JsonObject = checked("pack_apply_pause")
    @Throws(IOException::class) fun packApplyResume(): JsonObject = checked("pack_apply_resume")
    @Throws(IOException::class) fun packApplyCancel(): JsonObject = checked("pack_apply_cancel")
    @Throws(IOException::class) fun configGet(): JsonObject = checked("config_get")
    @Throws(IOException::class)
    fun configSet(config: JsonObject?): JsonObject {
        val payload = JsonObject()
        if (config != null) payload.add("config", config)
        return checked("config_set", payload)
    }
    @Throws(IOException::class) fun rconInfo(): JsonObject = checked("rcon_info")
    @Throws(IOException::class) fun rconGet(): JsonObject = checked("rcon_get")
    @Throws(IOException::class)
    fun rconSet(host: String?, port: Int, password: String?): JsonObject {
        val payload = JsonObject()
        addIfNotBlank(payload, "host", host)
        payload.addProperty("port", port)
        if (password != null) payload.addProperty("password", password)
        return checked("rcon_set", payload)
    }
    @Throws(IOException::class) fun rconExec(command: String?): JsonObject = checkedWithString("rcon_exec", "command", command)
    @Throws(IOException::class) fun serverRestart(): JsonObject = checked("server_restart")

    @Throws(IOException::class)
    fun pushInit(id: String?, mode: String?, targets: List<String>?, paths: List<String>?): JsonObject {
        val payload = JsonObject()
        addIfNotBlank(payload, "id", id)
        addIfNotBlank(payload, "mode", mode)
        if (targets != null) payload.add("targets", GSON.toJsonTree(targets))
        if (paths != null) payload.add("paths", GSON.toJsonTree(paths))
        return checked("push_init", payload)
    }

    @Throws(IOException::class)
    fun pushChunk(id: String?, data: ByteArray?) {
        val payload = JsonObject()
        addIfNotBlank(payload, "id", id)
        if (data != null) payload.addProperty("data", Base64.getEncoder().encodeToString(data))
        send(withType("push_chunk", payload))
    }

    @Throws(IOException::class) fun pushFinish(): JsonObject = checked("push_finish")

    @Throws(IOException::class)
    fun downloadFile(remotePath: String?, outputDir: Path): Path {
        val payload = JsonObject()
        payload.addProperty("path", remotePath ?: "")
        val init = rawRequest("file_download_init", payload)
        requireOk(init, "file_download_init")
        val name = firstNonBlank(string(init, "name"), "download.bin")
        Files.createDirectories(outputDir)
        val target = outputDir.resolve(name)
        val temp = target.resolveSibling(target.fileName.toString() + ".part")
        var ok = false
        try {
            Files.newOutputStream(temp).use { out ->
                while (true) {
                    val chunk = readObject()
                    when (string(chunk, "type")) {
                        "file_download_chunk" -> {
                            val data = string(chunk, "data")
                            if (!data.isNullOrBlank()) out.write(Base64.getDecoder().decode(data))
                        }
                        "file_download_finish" -> {
                            ok = bool(chunk, "ok")
                            break
                        }
                    }
                }
            }
        } catch (error: IOException) {
            Files.deleteIfExists(temp)
            throw error
        }
        if (!ok) {
            Files.deleteIfExists(temp)
            throw IOException("file_download_finish failed")
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    @Throws(IOException::class)
    fun downloadFileTo(remotePath: String?, target: Path?): Boolean {
        if (target == null) return false
        val payload = JsonObject()
        payload.addProperty("path", remotePath ?: "")
        val init = rawRequest("file_download_init", payload)
        if (string(init, "type") != "file_download_init" || !bool(init, "ok")) return false
        target.parent?.let(Files::createDirectories)
        val temp = target.resolveSibling(target.fileName.toString() + ".part")
        var ok = false
        try {
            Files.newOutputStream(temp).use { out ->
                while (true) {
                    val chunk = readObject()
                    when (string(chunk, "type")) {
                        "file_download_chunk" -> {
                            val data = string(chunk, "data")
                            if (!data.isNullOrBlank()) out.write(Base64.getDecoder().decode(data))
                        }
                        "file_download_finish" -> {
                            ok = bool(chunk, "ok")
                            break
                        }
                    }
                }
            }
        } catch (error: IOException) {
            Files.deleteIfExists(temp)
            throw error
        }
        if (!ok) {
            Files.deleteIfExists(temp)
            return false
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    @Throws(IOException::class)
    fun pushZip(id: String?, mode: String?, targets: List<String>?, paths: List<String>?, zipPath: Path?): Boolean {
        if (zipPath == null || !Files.isRegularFile(zipPath)) return false
        val init = JsonObject()
        addIfNotBlank(init, "id", id)
        addIfNotBlank(init, "mode", mode)
        init.add("targets", GSON.toJsonTree(targets))
        if (!paths.isNullOrEmpty()) init.add("paths", GSON.toJsonTree(paths))
        init.addProperty("size", Files.size(zipPath))
        val initResponse = rawRequest("push_init", init)
        if (!bool(initResponse, "ok")) return false
        Files.newInputStream(zipPath).use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            while (true) {
                val len = input.read(buffer)
                if (len <= 0) break
                pushChunk(id, buffer.copyOf(len))
            }
        }
        val finish = JsonObject()
        addIfNotBlank(finish, "id", id)
        val finishResponse = rawRequest("push_finish", finish)
        return bool(finishResponse, "ok")
    }

    @Throws(IOException::class)
    fun rawRequest(type: String, payload: JsonObject?): JsonObject {
        send(withType(type, payload))
        return readObject()
    }

    @Throws(IOException::class)
    private fun checked(type: String, payload: JsonObject? = null): JsonObject {
        val response = rawRequest(type, payload)
        requireOk(response, type)
        return response
    }

    @Throws(IOException::class)
    private fun checkedWithString(type: String, key: String, value: String?): JsonObject {
        val payload = JsonObject()
        addIfNotBlank(payload, key, value)
        return checked(type, payload)
    }

    @Throws(IOException::class)
    private fun checkedWithStrings(type: String, key: String, values: List<String>?): JsonObject {
        val payload = JsonObject()
        if (values != null) payload.add(key, GSON.toJsonTree(values))
        return checked(type, payload)
    }

    @Throws(IOException::class)
    private fun request(type: String, payload: JsonObject?): JsonObject {
        send(withType(type, payload))
        return readObject()
    }

    @Throws(IOException::class)
    private fun hello(password: String?, lang: String?, player: String?, uuid: String?) {
        val hello = JsonObject()
        hello.addProperty("type", "hello")
        hello.addProperty("password", password ?: "")
        addIfNotBlank(hello, "lang", lang)
        addIfNotBlank(hello, "player", player)
        addIfNotBlank(hello, "uuid", uuid)
        send(hello)
        val response = readObject()
        if (string(response, "type") != "hello") throw IOException("invalid response")
        if (!bool(response, "ok")) {
            throw IOException("auth_failed:" + firstNonBlank(string(response, "reason"), string(response, "message"), "unauthorized"))
        }
    }

    @Throws(IOException::class)
    private fun send(value: JsonObject) {
        writer.write(GSON.toJson(value))
        writer.newLine()
        writer.flush()
    }

    @Throws(IOException::class)
    private fun readObject(): JsonObject {
        val line = reader.readLine()
        if (line.isNullOrBlank()) throw IOException("empty server response")
        val element = GSON.fromJson(line, JsonElement::class.java)
        if (element == null || !element.isJsonObject) throw IOException("invalid json response")
        return element.asJsonObject
    }

    @Throws(IOException::class)
    override fun close() {
        socket.close()
    }

    companion object {
        private val GSON = Gson()
        private const val CHUNK_SIZE = 64 * 1024
        private val PORT_MULTIPLEXER_PREFACE = byteArrayOf('D'.code.toByte(), 'L'.code.toByte(), 'C'.code.toByte(), 'M'.code.toByte(), 1)

        @JvmStatic
        @Throws(IOException::class)
        fun connect(entry: DlcServerEntry?, timeoutMs: Int): DlcServerSession {
            if (entry == null) throw IOException("missing server entry")
            return connect(
                entry.host,
                entry.port,
                entry.password,
                timeoutMs,
                DlcPlatformServices.languageCode(),
                DlcPlatformServices.playerName(),
                DlcPlatformServices.playerUuid(),
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun connect(host: String?, port: Int, password: String?, timeoutMs: Int, lang: String?, player: String?, uuid: String?): DlcServerSession {
            val socket = Socket()
            val actualHost = host ?: ""
            socket.connect(InetSocketAddress(actualHost, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val session = DlcServerSession(socket)
            try {
                socket.getOutputStream().write(PORT_MULTIPLEXER_PREFACE)
                socket.getOutputStream().flush()
                session.hello(password, lang, player, uuid)
                return session
            } catch (error: Throwable) {
                try {
                    session.close()
                } catch (_: IOException) {
                }
                throw error
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun requireOk(response: JsonObject?, operation: String) {
            if (!bool(response, "ok")) {
                throw IOException(firstNonBlank(string(response, "error"), string(response, "message"), "$operation failed"))
            }
        }

        private fun withType(type: String, payload: JsonObject?): JsonObject {
            val out = payload ?: JsonObject()
            out.addProperty("type", type)
            return out
        }

        private fun addIfNotBlank(obj: JsonObject, key: String, value: String?) {
            if (!value.isNullOrBlank()) obj.addProperty(key, value)
        }

        private fun array(obj: JsonObject?, key: String): JsonArray? {
            val element = obj?.get(key)
            return if (element != null && element.isJsonArray) element.asJsonArray else null
        }

        private fun stringList(obj: JsonObject?, key: String): List<String> {
            val array = array(obj, key) ?: return listOf()
            val out = ArrayList<String>()
            array.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    val value = element.asString
                    if (!value.isNullOrBlank()) out.add(value)
                }
            }
            return out
        }

        private fun string(obj: JsonObject?, key: String): String? {
            val element = obj?.get(key)
            return if (element != null && element.isJsonPrimitive) element.asString else null
        }

        private fun bool(obj: JsonObject?, key: String): Boolean {
            val element = obj?.get(key)
            return element != null && element.isJsonPrimitive && element.asBoolean
        }

        private fun intValue(obj: JsonObject?, key: String): Int {
            val element = obj?.get(key) ?: return 0
            return try {
                if (element.isJsonPrimitive) element.asInt else 0
            } catch (_: RuntimeException) {
                0
            }
        }

        private fun firstNonBlank(vararg values: String?): String {
            for (value in values) {
                if (!value.isNullOrBlank()) return value
            }
            return ""
        }
    }
}
