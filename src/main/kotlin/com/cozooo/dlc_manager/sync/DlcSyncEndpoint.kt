package com.cozooo.dlc_manager.sync

import com.cozooo.dlc_manager.core.DlcFileMatch
import com.cozooo.dlc_manager.sync.DlcSyncModel.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DlcSyncEndpoint(private val adapter: Adapter) : Peer, AutoCloseable {
    private val completed = mutableListOf<Journal>()
    private val reads = mutableMapOf<String, Path>()
    private val readSources = mutableMapOf<String, Path>()
    private val readHashes = mutableMapOf<String, String>()
    private var current: Journal? = null
    private var key: String? = null
    private var operation: String? = null
    private var upload: Path? = null
    private var uploading: DlcSyncModel.File? = null
    private var uploadHash: String? = null
    private var uploadDirectory = false
    private var uploadReused = emptyList<File>()
    private var resourceSize = 0L
    private var received = 0L
    @Volatile private var canceled = false
    @Volatile private var disconnected = false
    private var validation: Thread? = null
    @Volatile private var validationDone = false
    @Volatile private var validated: List<Item>? = null
    @Volatile private var validationFailure: Exception? = null
    private fun startValidation() {
        stopValidation(); validationDone = false; validated = null; validationFailure = null
        validation = Thread({
            try {
                if (operation != null) validated = adapter.manifest()
                else guard(adapter.workDirectory()).use { validated = adapter.manifest() }
            } catch (error: Exception) { validationFailure = error }
            finally { validationDone = true }
        }, "dlc-sync-validation").also { it.isDaemon = true; it.start() }
    }
    private fun stopValidation() {
        val thread = validation ?: return
        if (thread.isAlive) {
            thread.interrupt()
            try { thread.join(5000) } catch (error: InterruptedException) { Thread.currentThread().interrupt(); throw DlcSyncFailure("validation_interrupted").causedBy(error) }
            if (thread.isAlive) throw DlcSyncFailure("validation_stop")
        }
        validation = null
    }
    @Volatile private var progress = JsonObject()
    private fun report(phase: String, resource: String, done: Long, total: Long) {
        val value = JsonObject(); value.addProperty("phase", phase); value.addProperty("resource", resource)
        value.addProperty("done", done); value.addProperty("total", total); progress = value
    }
    private var writeLock: java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock? = null

    override fun call(request: JsonObject): JsonObject {
        val result = JsonObject()
        result.addProperty("type", "dlc_sync")
        if (request.has("requestId")) result.add("requestId", request.get("requestId"))
        try {
            when (request.get("action").asString) {
                "status" -> OPERATIONS[request.get("operation").asString]?.let { result.add("progress", it.progress.deepCopy()) }
                "cancel" -> cancel(request.get("operation").asString)
                "recover_operation" -> exclusiveRecovery(request.get("operation").asString)
                "recover" -> exclusiveRecovery(null)
                "inspect" -> { val selected = request.get("key").asString; adapter.validateKey(selected); result.add("item", JSON.toJsonTree(adapter.item(selected))) }
                "manifest_start" -> startValidation()
                "manifest_poll" -> {
                    if (validation == null) throw DlcSyncFailure("comparison_not_started")
                    result.addProperty("ready", validationDone)
                    if (validationDone) { validationFailure?.let { throw it }; result.add("items", JSON.toJsonTree(validated)) }
                }
                "begin" -> begin(request.get("operation").asString)
                "rollback" -> rollback()
                "retain" -> retain()
                "commit" -> { check(); if (current != null) throw DlcSyncFailure("unfinished_item"); retain() }
                else -> {
                    check()
                    when (request.get("action").asString) {
                        "validate" -> {
                            requireItem()
                            val incoming = JSON.fromJson(request.get("item"), Item::class.java)
                            if (incoming == null || key != incoming.key) throw DlcSyncFailure("invalid_item")
                            adapter.validateIncoming(incoming)
                        }
                        "settings" -> { requireItem(); adapter.writeSettings(key!!, request.getAsJsonObject("settings"), current!!) }
                        "item" -> start(request.get("key").asString)
                        "prepare" -> { requireItem(); report("downloading", key!!, 0, -1); adapter.prepare(key!!, request.has("force") && request.get("force").asBoolean, { canceled || disconnected }, ::report); report("preparing", key!!, 0, -1); check(); result.add("item", JSON.toJsonTree(item())) }
                        "inventory" -> result.add("files", JSON.toJsonTree(DlcSyncArchive.inventory(allowed(request.get("id").asString)) { canceled || disconnected }))
                        "read" -> {
                            requireItem()
                            val id = request.get("id").asString
                            val path = allowed(id)
                            if (!Files.isRegularFile(path) && !Files.isDirectory(path)) throw DlcSyncFailure("resource_missing", "$id")
                            if (reads.containsKey(id)) throw DlcSyncFailure("read_open")
                            val hash = DlcFileMatch.hash(path)
                            readSources[id] = path; readHashes[id] = hash
                            val directory = Files.isDirectory(path)
                            val wire = if (directory) Files.createTempFile(adapter.workDirectory(), "outgoing-", ".zip") else path
                            reads[id] = wire
                            if (directory) {
                                val existing = if (request.has("existing")) JSON.fromJson(request.get("existing"), Array<File>::class.java).toList() else emptyList()
                                val reused = DlcSyncArchive.reusable(path, existing) { canceled || disconnected }
                                result.add("reused", JSON.toJsonTree(reused))
                                DlcSyncArchive.pack(path, wire, { canceled || disconnected }, reused)
                            }
                            result.addProperty("resourceSize", DlcSyncArchive.size(path)); result.addProperty("contentHash", hash)
                            result.addProperty("directory", directory)
                            result.addProperty("size", Files.size(wire)); result.addProperty("hash", if (directory) DlcFileMatch.fullHash(wire) else hash)
                        }
                        "chunk" -> {
                            val id = request.get("id").asString
                            val path = reads[id] ?: throw DlcSyncFailure("read_not_open")
                            val offset = request.get("offset").asLong
                            if (offset < 0 || offset > Files.size(path)) throw DlcSyncFailure("invalid_offset")
                            val bytes = ByteArray(minOf(1048576L, Files.size(path) - offset).toInt())
                            RandomAccessFile(path.toFile(), "r").use { it.seek(offset); it.readFully(bytes) }
                            result.addProperty("data", Base64.getEncoder().encodeToString(bytes))
                        }
                        "read_end" -> {
                            val id = request.get("id").asString
                            val source = readSources.remove(id)
                            val path = reads.remove(id)
                            try { if (source == null || DlcFileMatch.fullHash(source) != readHashes.remove(id)) throw DlcSyncFailure("source_changed_transfer", "$id") }
                            finally { if (path != null && path != source) Files.deleteIfExists(path) }
                        }
                        "write" -> {
                            requireItem()
                            if (upload != null) throw DlcSyncFailure("unfinished_upload")
                            uploading = JSON.fromJson(request.get("file"), DlcSyncModel.File::class.java)
                            allowed(uploading!!.id)
                            if (uploading!!.size < 0) throw DlcSyncFailure("invalid_size")
                            uploadHash = request.get("hash").asString
                            uploadDirectory = request.has("directory") && request.get("directory").asBoolean
                            uploadReused = if (uploadDirectory && request.has("reused")) JSON.fromJson(request.get("reused"), Array<File>::class.java).toList() else emptyList()
                            resourceSize = if (request.has("resourceSize")) request.get("resourceSize").asLong else uploading!!.size
                            if (resourceSize < 0 || uploadDirectory && uploading!!.id == key) throw DlcSyncFailure("invalid_directory")
                            upload = Files.createTempFile(adapter.workDirectory(), "incoming-", ".part"); received = 0
                        }
                        "write_chunk" -> {
                            val path = upload ?: throw DlcSyncFailure("no_upload")
                            val bytes = Base64.getDecoder().decode(request.get("data").asString)
                            if (bytes.size > 1048576 || received + bytes.size > uploading!!.size) throw DlcSyncFailure("resource_size")
                            Files.write(path, bytes, StandardOpenOption.APPEND); received += bytes.size
                        }
                        "write_end" -> {
                            val path = upload
                            if (path == null || received != uploading!!.size || DlcFileMatch.fullHash(path) != uploadHash) throw DlcSyncFailure("resource_integrity")
                            val target = allowed(uploading!!.id)
                            if (uploadDirectory) {
                                DlcSyncArchive.install(path, target, resourceSize, uploading!!.hash!!, current!!, { canceled || disconnected }, uploadReused)
                                Files.deleteIfExists(path)
                            } else {
                                current!!.capture(target); Files.createDirectories(target.parent)
                                try { Files.move(path, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                                catch (_: AtomicMoveNotSupportedException) { Files.move(path, target, StandardCopyOption.REPLACE_EXISTING) }
                            }
                            upload = null; adapter.reload()
                        }
                        "apply" -> {
                            requireItem()
                            val enabled = if (request.has("enabled")) request.get("enabled").asBoolean else null
                            val restart = adapter.apply(key!!, enabled, current!!, { canceled || disconnected }); check()
                            result.addProperty("requiresRestart", restart)
                        }
                        "item_done" -> { requireItem(); completed.add(current!!); current!!.close(); current = null; key = null }
                        "item_rollback" -> rollbackItem()
                    "item_keep" -> keepItem()
                        else -> throw DlcSyncFailure("unsupported_action")
                    }
                }
            }
            result.addProperty("ok", true)
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            result.addProperty("ok", false)
            result.addProperty("reason", error.message ?: error.javaClass.simpleName)
            if (error is DlcSyncFailure) error.write(result)
        }
        return result
    }
    private fun begin(id: String) {
        if (operation != null) throw DlcSyncFailure("already_active")
        UUID.fromString(id)
        val directory = adapter.workDirectory().toAbsolutePath().normalize()
        if (OWNERS.putIfAbsent(directory, this) != null) throw DlcSyncFailure("other_active")
        operation = id; OPERATIONS[id] = this
        try { acquire(directory); Files.createDirectories(directory); adapter.quiesce { canceled || disconnected } }
        catch (error: Exception) { release(); throw error }
    }
    private fun acquire(directory: Path) {
        writeLock = LOCKS.computeIfAbsent(directory) { java.util.concurrent.locks.ReentrantReadWriteLock(true) }.writeLock()
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        while (writeLock?.tryLock(100, java.util.concurrent.TimeUnit.MILLISECONDS) != true) {
            if (canceled || disconnected || System.nanoTime() > deadline) { writeLock = null; throw DlcSyncFailure("other_operation") }
        }
    }
    private fun exclusiveRecovery(operation: String?) {
        val directory = adapter.workDirectory().toAbsolutePath().normalize()
        if (OWNERS.putIfAbsent(directory, this) != null) throw DlcSyncFailure("active")
        try { acquire(directory); adapter.quiesce { false }; if (operation == null) adapter.recover() else adapter.recoverOperation(operation) }
        finally { release() }
    }
    private fun start(id: String) {
        if (current != null) throw DlcSyncFailure("previous_unfinished")
        adapter.validateKey(id); key = id
        current = adapter.journal(adapter.workDirectory().resolve(operation!!).resolve(String.format(Locale.ROOT, "%08d-", completed.size) + UUID.randomUUID()).resolve("journal"))
        current!!.capture(adapter.resolve(id))
    }
    private fun item(): Item = adapter.item(key!!)
    private fun allowed(id: String): Path {
        requireItem()
        if (key!!.startsWith("manager:")) throw DlcSyncFailure("settings_fields")
        if (id == key || item().resources.any { it.id == id }) return adapter.resolve(id)
        throw DlcSyncFailure("outside_item", "$id")
    }
    private fun requireItem() { if (current == null) throw DlcSyncFailure("no_item") }
    private fun check() {
        if (operation == null) throw DlcSyncFailure("not_started")
        if (canceled) throw DlcSyncFailure("canceled")
        if (disconnected) throw DlcSyncFailure("connection_lost")
    }
    private fun cleanupTransfers() {
        stopValidation()
        upload?.let { Files.deleteIfExists(it); upload = null }
        for ((id, path) in reads) if (path != readSources[id]) Files.deleteIfExists(path)
        reads.clear(); readHashes.clear(); readSources.clear()
    }
    private fun keepItem() {
        cleanupTransfers()
        current?.let { it.close(); completed.add(it); current = null; key = null }
    }
    private fun retain() {
        keepItem()
        completed.forEach { it.commit() }
        completed.clear(); release()
    }
    private fun rollbackItem() {
        cleanupTransfers()
        current?.let { it.close(); it.rollback(); current = null; key = null }
        adapter.reload()
    }
    private fun rollback() {
        var failure: IOException? = null
        try { rollbackItem() } catch (error: IOException) { failure = error }
        for (journal in completed.asReversed()) try { journal.rollback() }
        catch (error: IOException) { if (failure == null) failure = error else failure.addSuppressed(error) }
        try { adapter.reload() } catch (error: IOException) { if (failure == null) failure = error else failure.addSuppressed(error) }
        failure?.let { throw it }
        completed.clear(); release()
    }
    private fun release() {
        operation?.let { OPERATIONS.remove(it, this) }
        OWNERS.remove(adapter.workDirectory().toAbsolutePath().normalize(), this); operation = null
        if (writeLock != null && LOCKS[adapter.workDirectory().toAbsolutePath().normalize()]?.isWriteLockedByCurrentThread == true) writeLock?.unlock()
        writeLock = null
    }
    @Throws(IOException::class) override fun close() {
        val interrupted = Thread.interrupted()
        try { stopValidation(); if (operation != null) { if (canceled) rollback() else retain() } }
        catch (failure: IOException) { System.getLogger("DLC Sync").log(System.Logger.Level.ERROR, "DLC sync cleanup failed; recovery records retained", failure); throw failure }
        finally { release(); if (interrupted) Thread.currentThread().interrupt() }
    }
    class OperationGuard internal constructor(private val lock: java.util.concurrent.locks.Lock) : AutoCloseable {
        override fun close() = lock.unlock()
    }
    fun disconnectCurrent() { disconnected = true }
    companion object {
        @JvmField val JSON = Gson()
        private val LOCKS = ConcurrentHashMap<Path, java.util.concurrent.locks.ReentrantReadWriteLock>()
        private val OWNERS = ConcurrentHashMap<Path, DlcSyncEndpoint>()
        private val OPERATIONS = ConcurrentHashMap<String, DlcSyncEndpoint>()
        @JvmStatic @Throws(IOException::class) fun guard(directory: Path): OperationGuard {
            val key = directory.toAbsolutePath().normalize()
            val lock = LOCKS.computeIfAbsent(key) { java.util.concurrent.locks.ReentrantReadWriteLock(true) }.readLock()
            if (busy(key) || !lock.tryLock()) throw DlcSyncFailure("busy")
            if (busy(key)) { lock.unlock(); throw DlcSyncFailure("busy") }
            return OperationGuard(lock)
        }
        @JvmStatic fun busy(directory: Path) = OWNERS.containsKey(directory.toAbsolutePath().normalize())
        @JvmStatic fun cancel(id: String) { OPERATIONS[id]?.canceled = true }
    }
}
