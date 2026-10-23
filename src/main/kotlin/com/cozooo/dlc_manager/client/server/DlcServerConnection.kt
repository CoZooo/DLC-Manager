package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcTmpPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DlcServerConnection {
    private val GSON: Gson = GsonBuilder().create()
    private const val CHUNK_SIZE = 64 * 1024

    class ApplyResponse {
        @JvmField var ok: Boolean = false
        @JvmField var requiresRestart: Boolean = false
        @JvmField var messages: List<String> = listOf()
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.fetchList()
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                isOk(session.remoteDownload())
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRemoteMissingList(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): List<DlcServerRemoteItem>? {
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.remoteList()
                if (!hasType(response, "remote_list")) return@nullOnIo null
                val items = ArrayList<DlcServerRemoteItem>()
                if (response.has("items") && response["items"].isJsonArray) {
                    response.getAsJsonArray("items").forEach { element ->
                        if (element.isJsonObject) {
                            GSON.fromJson(element, DlcServerRemoteItem::class.java)
                                ?.takeUnless(::isRemotePresent)
                                ?.let(items::add)
                        }
                    }
                }
                items
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRemoteMissingList(entry: DlcServerEntry?, timeoutMs: Int): List<DlcServerRemoteItem>? {
        return fetchRemoteMissingList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    private fun isRemotePresent(item: DlcServerRemoteItem?): Boolean {
        return item != null && item.done && item.success
    }

    @JvmStatic
    @Throws(IOException::class)
    fun startRemoteDownload(entry: DlcServerEntry?, timeoutMs: Int, keys: List<String>?, playerName: String?, playerUuid: String?): DlcServerRemoteStatus? {
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.remoteDownloadStart(keys)
                if (!hasType(response, "remote_download_start")) return@nullOnIo null
                if (response.has("status") && response["status"].isJsonObject) {
                    GSON.fromJson(response["status"], DlcServerRemoteStatus::class.java)
                } else {
                    null
                }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.remoteDownloadStatus()
                if (!hasType(response, "remote_download_status")) return@nullOnIo null
                if (response.has("status") && response["status"].isJsonObject) {
                    GSON.fromJson(response["status"], DlcServerRemoteStatus::class.java)
                } else {
                    null
                }
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
    fun cancelRemoteDownload(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): Boolean {
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.remoteDownloadCancel()
                hasType(response, "remote_download_cancel") && response.has("canceled") && response["canceled"].asBoolean
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun cancelRemoteDownload(entry: DlcServerEntry?, timeoutMs: Int): Boolean {
        return cancelRemoteDownload(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchRequiredList(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): List<DlcServerRequiredItem>? {
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.requiredList()
                if (!hasType(response, "required_list")) return@nullOnIo null
                val items = ArrayList<DlcServerRequiredItem>()
                if (response.has("items") && response["items"].isJsonArray) {
                    response.getAsJsonArray("items").forEach { element ->
                        if (element.isJsonObject) {
                            GSON.fromJson(element, DlcServerRequiredItem::class.java)?.let(items::add)
                        }
                    }
                }
                items
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.requiredDownloadStart(ids)
                if (!hasType(response, "required_download_start")) return@nullOnIo null
                if (response.has("status") && response["status"].isJsonObject) {
                    GSON.fromJson(response["status"], DlcServerRequiredStatus::class.java)
                } else {
                    null
                }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.requiredDownloadStatus()
                if (!hasType(response, "required_download_status")) return@nullOnIo null
                if (response.has("status") && response["status"].isJsonObject) {
                    GSON.fromJson(response["status"], DlcServerRequiredStatus::class.java)
                } else {
                    null
                }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.requiredDownloadCancel()
                if (!hasType(response, "required_download_cancel")) return@falseOnIo false
                response.has("canceled") && response["canceled"].asBoolean
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.requiredUninstall(ids)
                hasType(response, "required_uninstall") && isOk(response)
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uninstallRequired(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?): Boolean {
        return uninstallRequired(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun applyRequired(entry: DlcServerEntry?, timeoutMs: Int, ids: List<String>?, playerName: String?, playerUuid: String?): Boolean {
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.requiredApply(ids)
                hasType(response, "required_apply") && isOk(response)
            }
        }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                items?.forEach { item ->
                    session.setEnabled(item.id, item.enabled)
                }
                session.applyWithMessages()
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.serverRestart()
                true
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun restartServer(entry: DlcServerEntry?, timeoutMs: Int): Boolean {
        return restartServer(entry, timeoutMs, currentPlayerName(), currentPlayerUuid())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadFile(entry: DlcServerEntry?, timeoutMs: Int, path: String?, target: Path, playerName: String?, playerUuid: String?): Boolean {
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.downloadFileTo(path, target)
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.updateList()
                if (!hasType(response, "update_list")) return@nullOnIo null
                val updates = ArrayList<DlcServerUpdateInfo>()
                if (response.has("updates") && response["updates"].isJsonArray) {
                    response.getAsJsonArray("updates").forEach { element ->
                        if (element.isJsonObject) {
                            GSON.fromJson(element, DlcServerUpdateInfo::class.java)?.let(updates::add)
                        }
                    }
                }
                updates
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetchUpdateState(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerUpdateState? {
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.updateState()
                if (!hasType(response, "update_state")) return@nullOnIo null
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
                state
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                readUpdateResponse(session.updateApply(baseOld, dlcOld, baseNew, dlcNew))
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val payload = JsonObject()
                payload.addProperty("target", targetVersion ?: "")
                readUpdateResponse(session.rawRequest("update_rollback", payload))
            }
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
        return falseOnIo {
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
            if (!uploadFile(entry, timeoutMs, targetPath, source, playerName, playerUuid)) return@falseOnIo false
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val payload = JsonObject()
                payload.addProperty("path", targetPath)
                isOk(session.rawRequest("update_import", payload))
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val response = session.packList()
                if (!hasType(response, "pack_list")) return@nullOnIo null
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
                result
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                readUpdateResponse(session.packOrder(order))
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                readPackApplyStatus(session.packApplyStart(order, uninstallIdentifiers, installArchives))
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                readPackApplyStatus(session.packApplyStatus())
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                readPackApplyStatus(session.rawRequest(type, null))
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val req = JsonObject()
                if (!archivePath.isNullOrBlank()) req.addProperty("archivePath", archivePath)
                if (!identifier.isNullOrBlank()) req.addProperty("identifier", identifier)
                readUpdateResponse(session.rawRequest(type, req))
            }
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
        return falseOnIo {
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
            if (!uploadFile(entry, timeoutMs, targetPath, source, playerName, playerUuid)) return@falseOnIo false
            importPackPackagePath(entry, timeoutMs, targetPath, playerName, playerUuid)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importPackPackage(entry: DlcServerEntry?, timeoutMs: Int, source: Path?): Boolean {
        return importPackPackage(entry, timeoutMs, source, currentPlayerName(), currentPlayerUuid())
    }

    @Throws(IOException::class)
    private fun importPackPackagePath(entry: DlcServerEntry?, timeoutMs: Int, targetPath: String, playerName: String?, playerUuid: String?): Boolean {
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val req = JsonObject()
                req.addProperty("path", targetPath)
                isOk(session.rawRequest("pack_import", req))
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val req = JsonObject()
                req.addProperty("url", url)
                if (!fileName.isNullOrBlank()) {
                    req.addProperty("fileName", fileName)
                }
                isOk(session.rawRequest("pack_import_url", req))
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importUpdatePackageFromUrl(entry: DlcServerEntry?, timeoutMs: Int, url: String?, fileName: String?, playerName: String?, playerUuid: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val req = JsonObject()
                req.addProperty("url", url)
                if (!fileName.isNullOrBlank()) {
                    req.addProperty("fileName", fileName)
                }
                isOk(session.rawRequest("update_import_url", req))
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                isOk(session.rawRequest("update_apply", null))
            }
        }
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
        if (entry == null) return false
        val zipPath = buildPushZip(targets, files) ?: return false
        val transferId = UUID.randomUUID().toString()
        try {
            return falseOnIo {
                openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                    session.pushZip(transferId, mode, targets, files, zipPath)
                }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.listFiles(path)
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.deleteFile(path)
                true
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.uploadFile(targetPath, source)
                true
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val resp = session.rconGet()
                if (!isOk(resp)) return@nullOnIo null
                val config = DlcServerRconConfig()
                config.host = getString(resp, "host")
                config.port = if (resp.has("port")) resp["port"].asInt else 0
                config.password = getString(resp, "password")
                config
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val resp = session.rconInfo()
                if (!isOk(resp)) return@nullOnIo null
                val info = DlcServerRconInfo()
                info.enabled = resp.has("enabled") && resp["enabled"].asBoolean
                info.port = if (resp.has("port")) resp["port"].asInt else 25575
                info.passwordSet = resp.has("passwordSet") && resp["passwordSet"].asBoolean
                info
            }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                isOk(session.rconSet(config.host ?: "", config.port, config.password ?: ""))
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val resp = session.rconExec(command ?: "")
                if (resp.has("message")) {
                    resp["message"].asString
                } else if (resp.has("ok") && resp["ok"].asBoolean) {
                    "ok"
                } else {
                    null
                }
            }
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
        return nullOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                val resp = session.configGet()
                if (resp.has("config") && resp["config"].isJsonObject) {
                    resp.getAsJsonObject("config")
                } else {
                    null
                }
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
        return falseOnIo {
            openSession(entry, timeoutMs, playerName, playerUuid).use { session ->
                session.configSet(config)
                true
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveServerConfig(entry: DlcServerEntry?, timeoutMs: Int, config: JsonObject?): Boolean {
        return saveServerConfig(entry, timeoutMs, config, currentPlayerName(), currentPlayerUuid())
    }

    @Throws(IOException::class)
    private fun openSession(entry: DlcServerEntry?, timeoutMs: Int, playerName: String?, playerUuid: String?): DlcServerSession {
        val actualEntry = entry ?: throw IOException("missing server entry")
        return DlcServerSession.connect(
            actualEntry.host,
            actualEntry.port,
            actualEntry.password,
            timeoutMs,
            DlcPlatformServices.languageCode(),
            playerName,
            playerUuid,
        )
    }

    private inline fun <T> nullOnIo(block: () -> T): T? {
        return try {
            block()
        } catch (_: IOException) {
            null
        }
    }

    private inline fun falseOnIo(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (_: IOException) {
            false
        }
    }

    private fun hasType(response: JsonObject?, type: String): Boolean {
        return response != null && response.has("type") && response["type"].asString == type
    }

    private fun isOk(response: JsonObject?): Boolean {
        return response != null && response.has("ok") && response["ok"].asBoolean
    }

    private fun readUpdateResponse(response: JsonObject?): UpdateResponse? {
        if (response == null) return null
        val result = UpdateResponse()
        result.ok = isOk(response)
        result.missing = response.has("missing") && response["missing"].isJsonPrimitive && response["missing"].asBoolean
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

    private fun currentPlayerName(): String? {
        return DlcPlatformServices.playerName()
    }

    private fun currentPlayerUuid(): String? {
        return DlcPlatformServices.playerUuid()
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
        val includeAll = hasTargets && targets.any { it.equals("all", ignoreCase = true) }
        val includeDlc = includeAll || (hasTargets && targets.any { it.equals("dlc", ignoreCase = true) })
        val includeConfig = includeAll || (hasTargets && targets.any { it.equals("config", ignoreCase = true) })
        val dlcRoot = DlcConfig.getDlcRoot()
        val configDir = DlcPlatformServices.configDir().resolve("dlc_manager")
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
