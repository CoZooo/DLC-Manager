package com.cozooo.dlc_manager.sync;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.cozooo.dlc_manager.sync.SyncModel.*;

public final class SyncClient {
    private final Peer local, remote, control;
    private final String localId = UUID.randomUUID().toString(), remoteId = UUID.randomUUID().toString();
    private volatile boolean canceled, sealed, recoveryPending, preparing, rollbackAvailable;
    private void prepare(Peer peer, boolean force) throws IOException {
        preparing = true;
        try { JsonObject r = request("prepare"); r.addProperty("force", force); checked(peer, r); }
        finally { preparing = false; }
    }
    private void poll(Peer peer, String operation, Progress progress) {
        try {
            JsonObject r = request("status"); r.addProperty("operation", operation);
            JsonObject response = checked(peer, r);
            if (preparing && !canceled && response.has("progress")) {
                JsonObject value = response.getAsJsonObject("progress");
                if (value.has("phase") && "downloading".equals(value.get("phase").getAsString())) progress.update("downloading", value.get("resource").getAsString(), value.get("done").getAsLong(), value.get("total").getAsLong());
            }
        } catch (IOException ignored) {}
    }
    public boolean canRollback() { return rollbackAvailable; }
    public void rollback(Peer local, Peer remote) throws IOException {
        recoverPeer(remote, remoteId); recoverPeer(local, localId); rollbackAvailable = false; recoveryPending = false;
    }
    public boolean needsRecovery() { return recoveryPending; }
    public void recover() throws IOException {
        rollback(local, control);
    }
    private void recoverPeer(Peer peer, String operation) throws IOException {
        IOException failure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try { JsonObject request = request("recover_operation"); request.addProperty("operation", operation); checked(peer, request); return; }
            catch (IOException error) { if (!SyncFailure.is(error, "active")) throw error; failure = error; }
            try { Thread.sleep(100); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new SyncFailure("recovery_interrupted").causedBy(error); }
        }
        throw failure;
    }
    public SyncClient(Peer local, Peer remote, Peer control) { this.local = local; this.remote = remote; this.control = control; }
    public boolean cancel() {
        synchronized (this) { if (sealed) return false; canceled = true; }
        SyncEndpoint.cancel(localId);
        Thread thread = new Thread(() -> {
            try { JsonObject request = request("cancel"); request.addProperty("operation", remoteId); checked(control, request); }
            catch (IOException failure) { try { remote.close(); } catch (IOException ignored) {} }
        }, "dlc-sync-cancel");
        thread.setDaemon(true); thread.start();
        return true;
    }
    public static List<Item> manifest(Peer peer) throws IOException { return manifest(peer, () -> false); }
    public static List<Item> manifest(Peer peer, BooleanSupplier canceled) throws IOException {
        checked(peer, request("manifest_start"));
        while (true) {
            if (canceled.getAsBoolean()) throw new SyncFailure("canceled");
            JsonObject response = checked(peer, request("manifest_poll"));
            if (response.get("ready").getAsBoolean()) return Arrays.asList(SyncEndpoint.JSON.fromJson(response.get("items"), Item[].class));
            try { Thread.sleep(50); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new SyncFailure("comparison_interrupted").causedBy(error); }
        }
    }
    private List<Item> readManifest(Peer peer) throws IOException { return manifest(peer, () -> canceled); }
    public Result execute(List<Row> rows, Mode mode, boolean states, Progress progress) throws IOException {
        java.util.concurrent.ScheduledExecutorService monitor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> { Thread thread = new Thread(task, "dlc-sync-progress"); thread.setDaemon(true); return thread; });
        monitor.scheduleWithFixedDelay(() -> { if (preparing) { poll(local, localId, progress); poll(control, remoteId, progress); } }, 200, 400, java.util.concurrent.TimeUnit.MILLISECONDS);
        List<String> errors = new ArrayList<>(), succeeded = new ArrayList<>();
        int count = 0; boolean restart = false, localStarted = false, remoteStarted = false;
        try {
            check();
            rollbackAvailable = true; localStarted = true; begin(local, localId);
            remoteStarted = true; begin(remote, remoteId);
            Map<String, Item> freshLocal = index(readManifest(local)), freshRemote = index(readManifest(remote));
            for (Row row : rows) {
                Item expectedLocal = row.direction() == Direction.PUSH ? row.source() : row.target();
                Item expectedRemote = row.direction() == Direction.PUSH ? row.target() : row.source();
                if (!Objects.equals(expectedLocal, freshLocal.get(row.key())) || !Objects.equals(expectedRemote, freshRemote.get(row.key()))) throw new SyncFailure("changed", row.key());
            }
            for (Row row : ordered(rows)) {
                check();
                Peer source = row.direction() == Direction.PUSH ? local : remote;
                Peer target = row.direction() == Direction.PUSH ? remote : local;
                Item current = inspect(source, row.key());
                if (current == null || !same(row.source().config(), current.config())) throw new SyncFailure("config_changed", row.key());
                boolean contentNeeded = row.target() == null || !same(row.source().config(), row.target().config())
                    || mode == Mode.FILES && usesResources(row.source()) && !resourcesMatch(row.source().resources(), row.target().resources(), mode);
                boolean applied = false, itemRestart = false, targetResourcesMissing = mode == Mode.CONFIG && row.target() != null && usesResources(row.target()) && row.target().resources().stream().anyMatch(file -> file.size() < 0 || file.hash() == null);
                if (contentNeeded) {
                    try {
                        start(local, row.key()); start(remote, row.key());
                        progress.update("preparing", row.key(), count, rows.size());
                        if (mode == Mode.FILES) prepare(source, false);
                        check();
                        current = inspect(source, row.key());
                        if (current == null) throw new SyncFailure("config_missing");
                        if (category(current) == Category.UPDATE && mode != Mode.FILES) throw new SyncFailure("update_mode");
                        JsonObject validation = request("validate"); validation.add("item", SyncEndpoint.JSON.toJsonTree(current)); checked(target, validation);
                        Item destinationBeforeWrite = row.target() == null ? null : inspect(target, row.key());
                        boolean configChanged = destinationBeforeWrite == null || !same(current.config(), destinationBeforeWrite.config());
                        if (category(current) == Category.MANAGER_CONFIG) {
                            if (current.settings() == null) throw new SyncFailure("settings_missing");
                            JsonObject settings = request("settings"); settings.add("settings", current.settings()); checked(target, settings);
                        } else {
                            if (configChanged) transfer(source, target, current.config(), progress);
                            if (mode == Mode.FILES) {
                                Item destination = inspect(target, row.key());
                                Map<String, File> files = new HashMap<>();
                                if (destination != null) for (File file : destination.resources()) files.put(file.id(), file);
                                for (File file : current.resources()) { check(); if (!same(file, files.get(file.id()))) transfer(source, target, file, progress); }
                            } else if (mode == Mode.FILES) {
                                prepare(target, configChanged); current = inspect(target, row.key());
                            } else {
                                current = inspect(target, row.key());
                                targetResourcesMissing = current != null && usesResources(current) && current.resources().stream().anyMatch(file -> file.size() < 0 || file.hash() == null);
                            }
                        }
                        check(); progress.update("applying", row.key(), count, rows.size());
                        if (!targetResourcesMissing) {
                            JsonObject apply = request("apply");
                            if (category(current) != Category.PRESET && row.target() != null && row.target().enabled() != null) apply.addProperty("enabled", row.target().enabled());
                            itemRestart = checked(target, apply).get("requiresRestart").getAsBoolean();
                        }
                        check(); Item result = inspect(target, row.key());
                        if (result == null || !same(current.config(), result.config()) || mode == Mode.FILES && usesResources(current) && !sameResources(current.resources(), result.resources())) throw new SyncFailure("verification", row.key());
                        applied = true;
                    } catch (IOException | RuntimeException error) {
                        if (canceled) throw new SyncFailure("canceled").causedBy(error);
                        checked(target, request("item_keep")); checked(source, request("item_keep"));
                        errors.add(row.key() + ": " + error.getMessage());
                    }
                    if (!applied) continue;
                    checked(target, request("item_done")); checked(source, request("item_done"));
                }
                if (!targetResourcesMissing && states && sharedState(row.source(), row.target()) && !Objects.equals(row.source().enabled(), row.target().enabled())) {
                    boolean stateApplied = false;
                    try {
                        check(); start(target, row.key());
                        progress.update("applying", row.key(), count, rows.size());
                        JsonObject apply = request("apply"); apply.addProperty("enabled", row.source().enabled());
                        boolean stateRestart = checked(target, apply).get("requiresRestart").getAsBoolean();
                        check(); stateApplied = true; itemRestart |= stateRestart;
                    } catch (IOException | RuntimeException error) {
                        if (canceled) throw new SyncFailure("canceled").causedBy(error);
                        checked(target, request("item_keep"));
                    }
                    if (stateApplied) { checked(target, request("item_done")); applied = true; }
                }
                if (applied) { count++; succeeded.add(row.key()); restart |= itemRestart; }
            }
            synchronized (this) { check(); sealed = true; }
            checked(remote, request("commit"));
            checked(local, request("commit")); remoteStarted = false; localStarted = false;
            return new Result(count, false, restart, errors, List.copyOf(succeeded));
        } catch (IOException | RuntimeException executionFailure) {
            IOException error = executionFailure instanceof IOException io ? io : new SyncFailure("invalid_response").causedBy(executionFailure);
            if (!canceled) {
                if (remoteStarted) try { checked(remote, request("retain")); } catch (IOException failure) { error.addSuppressed(failure); }
                if (localStarted) try { checked(local, request("retain")); } catch (IOException failure) { error.addSuppressed(failure); }
                throw error;
            }
            progress.update("restoring", "", 0, rows.size());
            IOException recovery = null;
            if (remoteStarted) try { checked(remote, request("rollback")); } catch (IOException failure) { try { recoverPeer(control, remoteId); } catch (IOException unresolved) { recovery = unresolved; } }
            if (localStarted) try { checked(local, request("rollback")); } catch (IOException failure) { if (recovery == null) recovery = failure; else recovery.addSuppressed(failure); }
            if (recovery != null) { recoveryPending = true; error.addSuppressed(recovery); throw new SyncFailure("recovery_incomplete", recovery.getMessage()).causedBy(error); }
            if (canceled) { rollbackAvailable = false; return new Result(0, true, false, errors, List.of()); }
            throw error;
        } finally { sealed = true; preparing = false; monitor.shutdownNow(); }
    }
    private void transfer(Peer source, Peer target, File file, Progress progress) throws IOException {
        check();
        JsonObject inventory = request("inventory"); inventory.addProperty("id", file.id());
        JsonObject read = request("read"); read.addProperty("id", file.id()); read.add("existing", checked(target, inventory).get("files"));
        JsonObject opened = checked(source, read);
        long size = opened.get("size").getAsLong();
        long resourceSize = opened.get("resourceSize").getAsLong();
        String contentHash = opened.get("contentHash").getAsString();
        if (resourceSize != file.size() || !Objects.equals(contentHash, file.hash())) throw new SyncFailure("source_changed", file.id());
        JsonObject write = request("write");
        write.add("file", SyncEndpoint.JSON.toJsonTree(new File(file.id(), size, file.hash())));
        write.addProperty("hash", opened.get("hash").getAsString());
        if (opened.has("reused")) write.add("reused", opened.get("reused"));
        write.add("directory", opened.get("directory")); write.addProperty("resourceSize", resourceSize); checked(target, write);
        for (long offset = 0; offset < size;) {
            check();
            JsonObject chunk = request("chunk"); chunk.addProperty("id", file.id()); chunk.addProperty("offset", offset);
            String data = checked(source, chunk).get("data").getAsString();
            int bytes = Base64.getDecoder().decode(data).length;
            if (bytes == 0) throw new SyncFailure("truncated", file.id());
            JsonObject upload = request("write_chunk"); upload.addProperty("data", data); checked(target, upload);
            offset += bytes; progress.update("transferring", file.id(), offset, size);
        }
        JsonObject finish = request("read_end"); finish.addProperty("id", file.id()); checked(source, finish);
        check(); checked(target, request("write_end"));
    }
    private Item inspect(Peer peer, String key) throws IOException {
        JsonObject request = request("inspect"); request.addProperty("key", key);
        Item value = SyncEndpoint.JSON.fromJson(checked(peer, request).get("item"), Item.class);
        if (value == null) throw new SyncFailure("config_missing");
        return value;
    }
    private void check() throws IOException { if (canceled) throw new SyncFailure("canceled"); }
    private static void begin(Peer peer, String id) throws IOException { JsonObject r = request("begin"); r.addProperty("operation", id); checked(peer, r); }
    private static void start(Peer peer, String key) throws IOException { JsonObject r = request("item"); r.addProperty("key", key); checked(peer, r); }
    public static JsonObject request(String action) { JsonObject r = new JsonObject(); r.addProperty("action", action); return r; }
    public static JsonObject checked(Peer peer, JsonObject request) throws IOException {
        try {
            JsonObject result = peer.call(request);
            if (result == null || !result.has("ok") || !result.get("ok").getAsBoolean()) throw SyncFailure.read(result);
            return result;
        } catch (RuntimeException error) { throw new SyncFailure("invalid_response").causedBy(error); }
    }
}
