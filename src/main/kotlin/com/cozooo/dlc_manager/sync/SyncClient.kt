package com.cozooo.dlc_manager.sync

import com.cozooo.dlc_manager.sync.SyncModel.*
import com.google.gson.JsonObject
import java.io.IOException
import java.util.*

class SyncClient(private val local: Peer, private val remote: Peer, private val control: Peer) {
    private val localId = UUID.randomUUID().toString()
    private val remoteId = UUID.randomUUID().toString()
    @Volatile private var canceled = false
    @Volatile private var sealed = false
    @Volatile private var recoveryPending = false
    @Volatile private var rollbackAvailable = false
    @Volatile private var preparing = false
    private fun prepare(peer: Peer, force: Boolean) {
        preparing = true
        try { val r = request("prepare"); r.addProperty("force", force); checked(peer, r) }
        finally { preparing = false }
    }
    private fun poll(peer: Peer, operation: String, progress: Progress) {
        try {
            val r = request("status"); r.addProperty("operation", operation)
            val response = checked(peer, r)
            if (preparing && !canceled && response.has("progress")) {
                val value = response.getAsJsonObject("progress")
                if (value.has("phase") && value.get("phase").asString == "downloading") progress.update("downloading", value.get("resource").asString, value.get("done").asLong, value.get("total").asLong)
            }
        } catch (_: IOException) {}
    }
    fun canRollback() = rollbackAvailable
    @Throws(IOException::class) fun rollback(local: Peer, remote: Peer) {
        recoverPeer(remote, remoteId); recoverPeer(local, localId); rollbackAvailable = false; recoveryPending = false
    }
    fun needsRecovery() = recoveryPending
    @Throws(IOException::class) fun recover() = rollback(local, control)
    private fun recoverPeer(peer: Peer, operation: String) {
        var failure: IOException? = null
        repeat(20) {
            try { val request = request("recover_operation"); request.addProperty("operation", operation); checked(peer, request); return }
            catch (error: IOException) { if (!SyncFailure.`is`(error, "active")) throw error; failure = error }
            try { Thread.sleep(100) } catch (error: InterruptedException) { Thread.currentThread().interrupt(); throw SyncFailure("recovery_interrupted").causedBy(error) }
        }
        throw failure!!
    }
    fun cancel(): Boolean {
        synchronized(this) { if (sealed) return false; canceled = true }
        SyncEndpoint.cancel(localId)
        Thread({
            try { val r = request("cancel"); r.addProperty("operation", remoteId); checked(control, r) }
            catch (_: IOException) { try { remote.close() } catch (_: IOException) {} }
        }, "dlc-sync-cancel").also { it.isDaemon = true; it.start() }
        return true
    }
    private fun readManifest(peer: Peer) = manifest(peer) { canceled }
    @Throws(IOException::class)
    fun execute(rows: List<Row>, mode: Mode, states: Boolean, progress: Progress): Result {
        val monitor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "dlc-sync-progress").also { it.isDaemon = true } }
        monitor.scheduleWithFixedDelay({ if (preparing) { poll(local, localId, progress); poll(control, remoteId, progress) } }, 200, 400, java.util.concurrent.TimeUnit.MILLISECONDS)
        val errors = mutableListOf<String>()
        val succeeded = mutableListOf<String>()
        var count = 0
        var restart = false
        var localStarted = false
        var remoteStarted = false
        try {
            check()
            rollbackAvailable = true; localStarted = true; begin(local, localId)
            remoteStarted = true; begin(remote, remoteId)
            val freshLocal = SyncModel.index(readManifest(local))
            val freshRemote = SyncModel.index(readManifest(remote))
            for (row in rows) {
                val expectedLocal = if (row.direction == Direction.PUSH) row.source else row.target
                val expectedRemote = if (row.direction == Direction.PUSH) row.target else row.source
                if (expectedLocal != freshLocal[row.key] || expectedRemote != freshRemote[row.key]) throw SyncFailure("changed", "${row.key}")
            }
            for (row in SyncModel.ordered(rows)) {
                check()
                val source = if (row.direction == Direction.PUSH) local else remote
                val target = if (row.direction == Direction.PUSH) remote else local
                var current = inspect(source, row.key)
                if (!SyncModel.same(row.source.config, current.config)) throw SyncFailure("config_changed", "${row.key}")
                val contentNeeded = row.target == null || !SyncModel.same(row.source.config, row.target.config) || mode == Mode.FILES && SyncModel.usesResources(row.source) && !SyncModel.resourcesMatch(row.source.resources, row.target.resources, mode)
                var applied = false
                var itemRestart = false
                var targetResourcesMissing = mode == Mode.CONFIG && row.target != null && SyncModel.usesResources(row.target) && row.target.resources.any { it.size < 0 || it.hash == null }
                if (contentNeeded) {
                    try {
                        start(local, row.key); start(remote, row.key)
                        progress.update("preparing", row.key, count.toLong(), rows.size.toLong())
                        if (mode == Mode.FILES) prepare(source, false)
                        check(); current = inspect(source, row.key)
                        if (SyncModel.category(current) == Category.UPDATE && mode != Mode.FILES) throw SyncFailure("update_mode")
                        val validation = request("validate"); validation.add("item", SyncEndpoint.JSON.toJsonTree(current)); checked(target, validation)
                        val destinationBeforeWrite = if (row.target == null) null else inspect(target, row.key)
                        val configChanged = destinationBeforeWrite == null || !SyncModel.same(current.config, destinationBeforeWrite.config)
                        if (SyncModel.category(current) == Category.MANAGER_CONFIG) {
                            val values = current.settings ?: throw SyncFailure("settings_missing")
                            val settings = request("settings"); settings.add("settings", values); checked(target, settings)
                        } else {
                            if (configChanged) transfer(source, target, current.config, progress)
                            if (mode == Mode.FILES) {
                                val destination = inspect(target, row.key)
                                val files = destination.resources.associateBy { it.id }
                                for (file in current.resources) { check(); if (!SyncModel.same(file, files[file.id])) transfer(source, target, file, progress) }
                            } else if (mode == Mode.FILES) {
                                prepare(target, configChanged); current = inspect(target, row.key)
                            } else {
                                current = inspect(target, row.key)
                                targetResourcesMissing = SyncModel.usesResources(current) && current.resources.any { it.size < 0 || it.hash == null }
                            }
                        }
                        check(); progress.update("applying", row.key, count.toLong(), rows.size.toLong())
                        if (!targetResourcesMissing) {
                            val apply = request("apply")
                            if (SyncModel.category(current) != Category.PRESET) row.target?.enabled?.let { apply.addProperty("enabled", it) }
                            itemRestart = checked(target, apply).get("requiresRestart").asBoolean
                        }
                        check(); val result = inspect(target, row.key)
                        if (!SyncModel.same(current.config, result.config) || mode == Mode.FILES && SyncModel.usesResources(current) && !SyncModel.sameResources(current.resources, result.resources)) throw SyncFailure("verification", "${row.key}")
                        applied = true
                    } catch (error: Exception) {
                        if (canceled) throw SyncFailure("canceled").causedBy(error)
                        checked(target, request("item_keep")); checked(source, request("item_keep"))
                        errors.add("${row.key}: ${error.message}")
                    }
                    if (!applied) continue
                    checked(target, request("item_done")); checked(source, request("item_done"))
                }
                if (!targetResourcesMissing && states && SyncModel.sharedState(row.source, row.target) && row.source.enabled != row.target?.enabled) {
                    var stateApplied = false
                    try {
                        check(); start(target, row.key)
                        progress.update("applying", row.key, count.toLong(), rows.size.toLong())
                        val apply = request("apply"); apply.addProperty("enabled", row.source.enabled)
                        val stateRestart = checked(target, apply).get("requiresRestart").asBoolean
                        check(); stateApplied = true; itemRestart = itemRestart || stateRestart
                    } catch (error: Exception) {
                        if (canceled) throw SyncFailure("canceled").causedBy(error)
                        checked(target, request("item_keep"))
                    }
                    if (stateApplied) { checked(target, request("item_done")); applied = true }
                }
                if (applied) { count++; succeeded.add(row.key); restart = restart || itemRestart }
            }
            synchronized(this) { check(); sealed = true }
            checked(remote, request("commit"))
            checked(local, request("commit")); remoteStarted = false; localStarted = false
            return Result(count, false, restart, errors, succeeded.toList())
        } catch (failure: Exception) {
            val error = if (failure is IOException) failure else SyncFailure("invalid_response").causedBy(failure)
            if (!canceled) {
                if (remoteStarted) try { checked(remote, request("retain")) } catch (failure: IOException) { error.addSuppressed(failure) }
                if (localStarted) try { checked(local, request("retain")) } catch (failure: IOException) { error.addSuppressed(failure) }
                throw error
            }
            progress.update("restoring", "", 0, rows.size.toLong())
            var recovery: IOException? = null
            if (remoteStarted) try { checked(remote, request("rollback")) } catch (failure: IOException) { try { recoverPeer(control, remoteId) } catch (unresolved: IOException) { recovery = unresolved } }
            if (localStarted) try { checked(local, request("rollback")) } catch (failure: IOException) { if (recovery == null) recovery = failure else recovery.addSuppressed(failure) }
            recovery?.let { recoveryPending = true; error.addSuppressed(it); throw SyncFailure("recovery_incomplete", "${it.message}").causedBy(error) }
            if (canceled) { rollbackAvailable = false; return Result(0, true, false, errors, emptyList()) }
            throw error
        } finally { sealed = true; preparing = false; monitor.shutdownNow() }
    }
    private fun transfer(source: Peer, target: Peer, file: File, progress: Progress) {
        check()
        val inventory = request("inventory"); inventory.addProperty("id", file.id)
        val read = request("read"); read.addProperty("id", file.id); read.add("existing", checked(target, inventory).get("files"))
        val opened = checked(source, read)
        val size = opened.get("size").asLong
        val resourceSize = opened.get("resourceSize").asLong
        val contentHash = opened.get("contentHash").asString
        if (resourceSize != file.size || contentHash != file.hash) throw SyncFailure("source_changed", "${file.id}")
        val write = request("write")
        write.add("file", SyncEndpoint.JSON.toJsonTree(File(file.id, size, file.hash)))
        write.addProperty("hash", opened.get("hash").asString)
        if (opened.has("reused")) write.add("reused", opened.get("reused"))
        write.add("directory", opened.get("directory")); write.addProperty("resourceSize", resourceSize); checked(target, write)
        var offset = 0L
        while (offset < size) {
            check()
            val chunk = request("chunk"); chunk.addProperty("id", file.id); chunk.addProperty("offset", offset)
            val data = checked(source, chunk).get("data").asString
            val bytes = Base64.getDecoder().decode(data).size
            if (bytes == 0) throw SyncFailure("truncated", "${file.id}")
            val upload = request("write_chunk"); upload.addProperty("data", data); checked(target, upload)
            offset += bytes; progress.update("transferring", file.id, offset, size)
        }
        val finish = request("read_end"); finish.addProperty("id", file.id); checked(source, finish)
        check(); checked(target, request("write_end"))
    }
    private fun inspect(peer: Peer, key: String): Item {
        val request = request("inspect"); request.addProperty("key", key)
        val value = SyncEndpoint.JSON.fromJson(checked(peer, request).get("item"), Item::class.java)
        return value ?: throw SyncFailure("config_missing")
    }
    private fun check() { if (canceled) throw SyncFailure("canceled") }
    companion object {
        @JvmStatic @Throws(IOException::class) fun manifest(peer: Peer): List<Item> = manifest(peer) { false }
        @JvmStatic @Throws(IOException::class) fun manifest(peer: Peer, canceled: java.util.function.BooleanSupplier): List<Item> {
            checked(peer, request("manifest_start"))
            while (true) {
                if (canceled.asBoolean) throw SyncFailure("canceled")
                val response = checked(peer, request("manifest_poll"))
                if (response.get("ready").asBoolean) return SyncEndpoint.JSON.fromJson(response.get("items"), Array<Item>::class.java).toList()
                try { Thread.sleep(50) } catch (error: InterruptedException) { Thread.currentThread().interrupt(); throw SyncFailure("comparison_interrupted").causedBy(error) }
            }
        }
        private fun begin(peer: Peer, id: String) { val r = request("begin"); r.addProperty("operation", id); checked(peer, r) }
        private fun start(peer: Peer, key: String) { val r = request("item"); r.addProperty("key", key); checked(peer, r) }
        @JvmStatic fun request(action: String): JsonObject = JsonObject().also { it.addProperty("action", action) }
        @JvmStatic @Throws(IOException::class) fun checked(peer: Peer, request: JsonObject): JsonObject {
            try {
                val result = peer.call(request)
                if (!result.has("ok") || !result.get("ok").asBoolean) throw SyncFailure.read(result)
                return result
            } catch (error: RuntimeException) { throw SyncFailure("invalid_response").causedBy(error) }
        }
    }
}
