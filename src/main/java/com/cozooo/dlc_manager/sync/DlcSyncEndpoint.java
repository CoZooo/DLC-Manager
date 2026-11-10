package com.cozooo.dlc_manager.sync;

import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.google.gson.*;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.cozooo.dlc_manager.sync.DlcSyncModel.*;

public final class DlcSyncEndpoint implements Peer, AutoCloseable {
    public static final Gson JSON = new Gson();
    private static final Map<Path, DlcSyncEndpoint> OWNERS = new ConcurrentHashMap<>();
    private static final Map<Path, java.util.concurrent.locks.ReentrantReadWriteLock> LOCKS = new ConcurrentHashMap<>();
    private java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock writeLock;
    private static final Map<String, DlcSyncEndpoint> OPERATIONS = new ConcurrentHashMap<>();
    private final Adapter adapter;
    private final List<Journal> completed = new ArrayList<>();
    private final Map<String, Path> reads = new HashMap<>();
    private final Map<String, Path> readSources = new HashMap<>();
    private final Map<String, String> readHashes = new HashMap<>();
    private Journal current;
    private String key, operation;
    private Path upload;
    private File uploading;
    private String uploadHash;
    private boolean uploadDirectory;
    private List<File> uploadReused = List.of();
    private long resourceSize;
    private long received;
    private volatile boolean canceled, disconnected;
    private Thread validation;
    private volatile boolean validationDone;
    private volatile List<Item> validated;
    private volatile Exception validationFailure;
    private void startValidation() throws IOException {
        stopValidation(); validationDone = false; validated = null; validationFailure = null;
        validation = new Thread(() -> {
            try {
                if (operation != null) validated = adapter.manifest();
                else try (OperationGuard ignored = guard(adapter.workDirectory())) { validated = adapter.manifest(); }
            } catch (Exception error) { validationFailure = error; }
            finally { validationDone = true; }
        }, "dlc-sync-validation");
        validation.setDaemon(true); validation.start();
    }
    private void stopValidation() throws IOException {
        if (validation == null) return;
        if (validation.isAlive()) {
            validation.interrupt();
            try { validation.join(5000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new DlcSyncFailure("validation_interrupted").causedBy(error); }
            if (validation.isAlive()) throw new DlcSyncFailure("validation_stop");
        }
        validation = null;
    }
    private volatile JsonObject progress = new JsonObject();
    private void report(String phase, String resource, long done, long total) {
        JsonObject value = new JsonObject(); value.addProperty("phase", phase); value.addProperty("resource", resource);
        value.addProperty("done", done); value.addProperty("total", total); progress = value;
    }

    public DlcSyncEndpoint(Adapter adapter) { this.adapter = adapter; }
    public static final class OperationGuard implements AutoCloseable {
        private final java.util.concurrent.locks.Lock lock;
        private OperationGuard(java.util.concurrent.locks.Lock lock) { this.lock = lock; }
        @Override public void close() { lock.unlock(); }
    }
    public static OperationGuard guard(Path directory) throws IOException {
        Path key = directory.toAbsolutePath().normalize();
        java.util.concurrent.locks.Lock lock = LOCKS.computeIfAbsent(key, ignored -> new java.util.concurrent.locks.ReentrantReadWriteLock(true)).readLock();
        if (busy(key) || !lock.tryLock()) throw new DlcSyncFailure("busy");
        if (busy(key)) { lock.unlock(); throw new DlcSyncFailure("busy"); }
        return new OperationGuard(lock);
    }
    public static boolean busy(Path directory) { return OWNERS.containsKey(directory.toAbsolutePath().normalize()); }
    public void disconnectCurrent() { disconnected = true; }
    public static void cancel(String id) {
        DlcSyncEndpoint endpoint = OPERATIONS.get(id);
        if (endpoint != null) endpoint.canceled = true;
    }
    public JsonObject call(JsonObject request) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("type", "dlc_sync");
        if (request.has("requestId")) result.add("requestId", request.get("requestId"));
        try {
            String action = request.get("action").getAsString();
            if (action.equals("status")) {
                DlcSyncEndpoint endpoint = OPERATIONS.get(request.get("operation").getAsString());
                if (endpoint != null) result.add("progress", endpoint.progress.deepCopy());
            }
            else if (action.equals("cancel")) { cancel(request.get("operation").getAsString()); }
            else if (action.equals("recover_operation")) { exclusiveRecovery(request.get("operation").getAsString()); }
            else if (action.equals("recover")) { exclusiveRecovery(null); }
            else if (action.equals("inspect")) { String selected = request.get("key").getAsString(); adapter.validateKey(selected); result.add("item", JSON.toJsonTree(adapter.item(selected))); }
            else if (action.equals("manifest_start")) startValidation();
            else if (action.equals("manifest_poll")) {
                if (validation == null) throw new DlcSyncFailure("comparison_not_started");
                result.addProperty("ready", validationDone);
                if (validationDone) {
                    if (validationFailure != null) throw validationFailure;
                    result.add("items", JSON.toJsonTree(validated));
                }
            }
            else if (action.equals("begin")) begin(request.get("operation").getAsString());
            else if (action.equals("rollback")) rollback();
            else if (action.equals("retain")) retain();
            else if (action.equals("commit")) { check(); if (current != null) throw new DlcSyncFailure("unfinished_item"); retain(); }
            else {
                check();
                switch (action) {
                    case "validate" -> {
                        requireItem(); Item incoming = JSON.fromJson(request.get("item"), Item.class);
                        if (incoming == null || !key.equals(incoming.key())) throw new DlcSyncFailure("invalid_item");
                        adapter.validateIncoming(incoming);
                    }
                    case "settings" -> { requireItem(); adapter.writeSettings(key, request.getAsJsonObject("settings"), current); }
                    case "item" -> start(request.get("key").getAsString());
                    case "prepare" -> { requireItem(); report("downloading", key, 0, -1); adapter.prepare(key, request.has("force") && request.get("force").getAsBoolean(), () -> canceled || disconnected, this::report); report("preparing", key, 0, -1); check(); result.add("item", JSON.toJsonTree(item())); }
                    case "inventory" -> { result.add("files", JSON.toJsonTree(DlcSyncArchive.inventory(allowed(request.get("id").getAsString()), () -> canceled || disconnected))); }
                    case "read" -> {
                        requireItem();
                        String id = request.get("id").getAsString();
                        Path path = allowed(id);
                        if (!Files.isRegularFile(path) && !Files.isDirectory(path)) throw new DlcSyncFailure("resource_missing", id);
                        if (reads.containsKey(id)) throw new DlcSyncFailure("read_open");
                        String hash = DlcFileMatch.hash(path);
                        readSources.put(id, path); readHashes.put(id, hash);
                        Path wire = path;
                        boolean directory = Files.isDirectory(path);
                        if (directory) {
                            wire = Files.createTempFile(adapter.workDirectory(), "outgoing-", ".zip");
                            List<File> existing = request.has("existing") ? Arrays.asList(JSON.fromJson(request.get("existing"), File[].class)) : List.of();
                            List<File> reused = DlcSyncArchive.reusable(path, existing, () -> canceled || disconnected);
                            result.add("reused", JSON.toJsonTree(reused));
                            reads.put(id, wire); DlcSyncArchive.pack(path, wire, () -> canceled || disconnected, reused);
                        } else reads.put(id, wire);
                        result.addProperty("resourceSize", DlcSyncArchive.size(path)); result.addProperty("contentHash", hash);
                        result.addProperty("directory", directory);
                        result.addProperty("size", Files.size(wire)); result.addProperty("hash", directory ? DlcFileMatch.fullHash(wire) : hash);
                    }
                    case "chunk" -> {
                        String id = request.get("id").getAsString();
                        Path path = reads.get(id);
                        if (path == null) throw new DlcSyncFailure("read_not_open");
                        long offset = request.get("offset").getAsLong();
                        if (offset < 0 || offset > Files.size(path)) throw new DlcSyncFailure("invalid_offset");
                        byte[] bytes = new byte[(int)Math.min(1048576L, Files.size(path) - offset)];
                        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) { file.seek(offset); file.readFully(bytes); }
                        result.addProperty("data", Base64.getEncoder().encodeToString(bytes));
                    }
                    case "read_end" -> {
                        String id = request.get("id").getAsString();
                        Path source = readSources.remove(id), path = reads.remove(id);
                        try { if (source == null || !DlcFileMatch.fullHash(source).equals(readHashes.remove(id))) throw new DlcSyncFailure("source_changed_transfer", id); }
                        finally { if (path != null && !path.equals(source)) Files.deleteIfExists(path); }
                    }
                    case "write" -> {
                        requireItem();
                        if (upload != null) throw new DlcSyncFailure("unfinished_upload");
                        uploading = JSON.fromJson(request.get("file"), File.class);
                        allowed(uploading.id());
                        if (uploading.size() < 0) throw new DlcSyncFailure("invalid_size");
                        uploadHash = request.get("hash").getAsString();
                        uploadDirectory = request.has("directory") && request.get("directory").getAsBoolean();
                        uploadReused = uploadDirectory && request.has("reused") ? Arrays.asList(JSON.fromJson(request.get("reused"), File[].class)) : List.of();
                        resourceSize = request.has("resourceSize") ? request.get("resourceSize").getAsLong() : uploading.size();
                        if (resourceSize < 0 || uploadDirectory && uploading.id().equals(key)) throw new DlcSyncFailure("invalid_directory");
                        upload = Files.createTempFile(adapter.workDirectory(), "incoming-", ".part"); received = 0;
                    }
                    case "write_chunk" -> {
                        if (upload == null) throw new DlcSyncFailure("no_upload");
                        byte[] bytes = Base64.getDecoder().decode(request.get("data").getAsString());
                        if (bytes.length > 1048576 || received + bytes.length > uploading.size()) throw new DlcSyncFailure("resource_size");
                        Files.write(upload, bytes, StandardOpenOption.APPEND); received += bytes.length;
                    }
                    case "write_end" -> {
                        if (upload == null || received != uploading.size() || !DlcFileMatch.fullHash(upload).equals(uploadHash)) throw new DlcSyncFailure("resource_integrity");
                        Path target = allowed(uploading.id());
                        if (uploadDirectory) {
                            DlcSyncArchive.install(upload, target, resourceSize, uploading.hash(), current, () -> canceled || disconnected, uploadReused);
                            Files.deleteIfExists(upload); upload = null; adapter.reload(); break;
                        }
                        current.capture(target);
                        Files.createDirectories(target.getParent());
                        try { Files.move(upload, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                        catch (AtomicMoveNotSupportedException error) { Files.move(upload, target, StandardCopyOption.REPLACE_EXISTING); }
                        upload = null; adapter.reload();
                    }
                    case "apply" -> {
                        requireItem();
                        Boolean enabled = request.has("enabled") ? request.get("enabled").getAsBoolean() : null;
                        boolean restart = adapter.apply(key, enabled, current, () -> canceled || disconnected); check();
                        result.addProperty("requiresRestart", restart);
                    }
                    case "item_done" -> { requireItem(); completed.add(current); current.close(); current = null; key = null; }
                    case "item_rollback" -> rollbackItem();
                    case "item_keep" -> keepItem();
                    default -> throw new DlcSyncFailure("unsupported_action");
                }
            }
            result.addProperty("ok", true);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            result.addProperty("ok", false);
            result.addProperty("reason", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            if (error instanceof DlcSyncFailure failure) failure.write(result);
        }
        return result;
    }
    private void begin(String id) throws Exception {
        if (operation != null) throw new DlcSyncFailure("already_active");
        UUID.fromString(id);
        Path directory = adapter.workDirectory().toAbsolutePath().normalize();
        if (OWNERS.putIfAbsent(directory, this) != null) throw new DlcSyncFailure("other_active");
        operation = id; OPERATIONS.put(id, this);
        try { acquire(directory); Files.createDirectories(directory); adapter.quiesce(() -> canceled || disconnected); }
        catch (Exception error) { release(); throw error; }
    }
    private void acquire(Path directory) throws Exception {
        writeLock = LOCKS.computeIfAbsent(directory, ignored -> new java.util.concurrent.locks.ReentrantReadWriteLock(true)).writeLock();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (!writeLock.tryLock(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            if (canceled || disconnected || System.nanoTime() > deadline) { writeLock = null; throw new DlcSyncFailure("other_operation"); }
        }
    }
    private void exclusiveRecovery(String operation) throws Exception {
        Path directory = adapter.workDirectory().toAbsolutePath().normalize();
        if (OWNERS.putIfAbsent(directory, this) != null) throw new DlcSyncFailure("active");
        try { acquire(directory); adapter.quiesce(() -> false); if (operation == null) adapter.recover(); else adapter.recoverOperation(operation); }
        finally { release(); }
    }
    private void start(String id) throws IOException {
        if (current != null) throw new DlcSyncFailure("previous_unfinished");
        adapter.validateKey(id);
        key = id;
        current = adapter.journal(adapter.workDirectory().resolve(operation).resolve(String.format(java.util.Locale.ROOT, "%08d-", completed.size()) + UUID.randomUUID()).resolve("journal"));
        current.capture(adapter.resolve(id));
    }
    private Item item() throws IOException { return adapter.item(key); }
    private Path allowed(String id) throws IOException {
        requireItem();
        if (key.startsWith("manager:")) throw new DlcSyncFailure("settings_fields");
        if (id.equals(key)) return adapter.resolve(id);
        for (File file : item().resources()) if (file.id().equals(id)) return adapter.resolve(id);
        throw new DlcSyncFailure("outside_item", id);
    }
    private void requireItem() throws IOException { if (current == null) throw new DlcSyncFailure("no_item"); }
    private void check() throws IOException {
        if (operation == null) throw new DlcSyncFailure("not_started");
        if (canceled) throw new DlcSyncFailure("canceled");
        if (disconnected) throw new DlcSyncFailure("connection_lost");
    }
    private void cleanupTransfers() throws IOException {
        stopValidation();
        if (upload != null) { Files.deleteIfExists(upload); upload = null; }
        for (var read : reads.entrySet()) if (!read.getValue().equals(readSources.get(read.getKey()))) Files.deleteIfExists(read.getValue());
        reads.clear(); readHashes.clear(); readSources.clear();
    }
    private void keepItem() throws IOException {
        cleanupTransfers();
        if (current != null) { current.close(); completed.add(current); current = null; key = null; }
    }
    private void retain() throws IOException {
        keepItem();
        for (Journal journal : completed) journal.commit();
        completed.clear(); release();
    }
    private void rollbackItem() throws IOException {
        cleanupTransfers();
        if (current != null) {
            current.close(); current.rollback(); current = null; key = null;
        }
        adapter.reload();
    }
    private void rollback() throws IOException {
        IOException failure = null;
        try { rollbackItem(); } catch (IOException error) { failure = error; }
        for (int index = completed.size() - 1; index >= 0; index--) {
            try { completed.get(index).rollback(); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        try { adapter.reload(); } catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure != null) throw failure;
        completed.clear(); release();
    }
    private void release() {
        if (operation != null) OPERATIONS.remove(operation, this);
        OWNERS.remove(adapter.workDirectory().toAbsolutePath().normalize(), this); operation = null;
        if (writeLock != null && LOCKS.get(adapter.workDirectory().toAbsolutePath().normalize()).isWriteLockedByCurrentThread()) writeLock.unlock(); writeLock = null;
    }
    @Override public void close() throws IOException {
        boolean interrupted = Thread.interrupted();
        try { stopValidation(); if (operation != null) { if (canceled) rollback(); else retain(); } }
        catch (IOException failure) { System.getLogger("DLC Sync").log(System.Logger.Level.ERROR, "DLC sync cleanup failed; recovery records retained", failure); throw failure; }
        finally { release(); if (interrupted) Thread.currentThread().interrupt(); }
    }
}
