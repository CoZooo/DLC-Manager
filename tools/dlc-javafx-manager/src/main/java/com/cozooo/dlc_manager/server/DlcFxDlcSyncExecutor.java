package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Direction;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Row;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Transfer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Moves planned DLC configs/files directly over the existing DLC TCP file commands, without network downloads. */
public final class DlcFxDlcSyncExecutor {
    private static final long CANCEL_WAIT_MS = 30_000L;

    private DlcFxDlcSyncExecutor() {
    }

    @FunctionalInterface
    public interface Progress {
        void update(int done, int total, String rel);
    }

    public record Result(int transferred, int planned, int applied, boolean canceled, List<String> errors) {
    }

    public static Result execute(DlcServerSession session,
                                 DlcFxDlcSyncSnapshot snapshot,
                                 Direction direction,
                                 List<Row> rows,
                                 DlcFxLightHash.Cache cache,
                                 BooleanSupplier canceled,
                                 Progress progress) throws IOException, InterruptedException {
        List<Transfer> transfers = new ArrayList<>();
        for (Row row : rows) {
            transfers.addAll(row.transfers());
        }
        List<String> errors = new ArrayList<>();
        if (transfers.isEmpty()) {
            return new Result(0, 0, 0, false, errors);
        }
        if (isCanceled(canceled)) {
            return new Result(0, transfers.size(), 0, true, errors);
        }
        String currentRoot = DlcFxDlcSyncSnapshot.resolveServerDlcRoot(session.configGet());
        if (!snapshot.serverDlcRoot.equals(currentRoot)) {
            throw new IOException("server DLC root changed; preview again");
        }
        if (!stopServerDownloads(session, canceled)) {
            return new Result(0, transfers.size(), 0, true, errors);
        }
        Path incoming = DlcFxDlcSyncSnapshot.workDir(snapshot.localDlcRoot)
                .resolve("incoming")
                .resolve(UUID.randomUUID().toString());
        Set<String> failedKeys = new LinkedHashSet<>();
        int transferred = 0;
        int done = 0;
        boolean wasCanceled = false;
        for (Row row : rows) {
            for (Transfer transfer : row.transfers()) {
                if (isCanceled(canceled)) {
                    wasCanceled = true;
                    break;
                }
                if (failedKeys.contains(row.key())) {
                    done++;
                    continue;
                }
                if (progress != null) {
                    progress.update(done, transfers.size(), transfer.rel());
                }
                try {
                    if (direction == Direction.PUSH) {
                        push(session, snapshot, transfer, cache, canceled);
                    } else {
                        pull(session, snapshot, transfer, incoming, cache, canceled);
                    }
                    transferred++;
                } catch (IOException error) {
                    if (isCanceled(canceled)) {
                        wasCanceled = true;
                        break;
                    }
                    failedKeys.add(row.key());
                    errors.add(transfer.rel() + ": " + DlcFxServerConnection.safeMessage(error));
                }
                done++;
                if (isCanceled(canceled)) {
                    wasCanceled = true;
                    break;
                }
            }
            if (wasCanceled) {
                break;
            }
        }
        DlcFxDlcSyncSnapshot.deleteRecursive(incoming);
        cache.save();
        if (progress != null) {
            progress.update(done, transfers.size(), "");
        }
        if (wasCanceled || isCanceled(canceled)) {
            return new Result(transferred, transfers.size(), 0, true, errors);
        }
        int applied = applyRequired(session, snapshot, direction, rows, failedKeys, errors, canceled);
        return new Result(transferred, transfers.size(), applied, isCanceled(canceled), errors);
    }

    private static void push(DlcServerSession session,
                             DlcFxDlcSyncSnapshot snapshot,
                             Transfer transfer,
                             DlcFxLightHash.Cache cache,
                             BooleanSupplier canceled) throws IOException {
        Path local = snapshot.localPath(transfer.rel());
        if (!Files.isRegularFile(local)) {
            throw new IOException("missing local file");
        }
        String hash = DlcFxLightHash.compute(local);
        if (transfer.hash() != null && !transfer.hash().equals(hash)) {
            throw new IOException("local file changed after preview");
        }
        String serverPath = snapshot.serverPath(transfer.rel());
        session.uploadFile(serverPath, local, canceled);
        checkCanceled(canceled);
        DlcServerFileEntry uploaded = null;
        for (DlcServerFileEntry entry : session.listFiles(DlcFxDlcSyncSnapshot.parentOf(serverPath), true)) {
            if (entry != null && DlcFxDlcSyncSnapshot.nameOf(serverPath).equals(entry.name)) {
                uploaded = entry;
                break;
            }
        }
        long size = Files.size(local);
        if (uploaded == null || uploaded.dir || uploaded.size != size) {
            throw new IOException("size check failed after upload");
        }
        String serverHash = uploaded.hash;
        if (serverHash == null || serverHash.isBlank()) {
            serverHash = fetchServerHash(session, serverPath, snapshot, canceled);
        }
        if (!hash.equals(serverHash)) {
            throw new IOException("hash check failed after upload");
        }
        cache.putServer(snapshot.serverCacheKey(transfer.rel()), uploaded.size, uploaded.modified, serverHash);
    }

    private static void pull(DlcServerSession session,
                             DlcFxDlcSyncSnapshot snapshot,
                             Transfer transfer,
                             Path incoming,
                              DlcFxLightHash.Cache cache,
                              BooleanSupplier canceled) throws IOException {
        Path target = snapshot.localPath(transfer.rel());
        Files.createDirectories(incoming);
        Path temp = incoming.resolve(UUID.randomUUID() + ".bin");
        try {
            if (!session.downloadFileTo(snapshot.serverPath(transfer.rel()), temp, canceled)) {
                throw new IOException("download failed");
            }
            if (Files.size(temp) != transfer.size()) {
                throw new IOException("size check failed after download");
            }
            String hash = DlcFxLightHash.compute(temp);
            if (transfer.hash() != null && !transfer.hash().equals(hash)) {
                throw new IOException("hash check failed after download");
            }
            DlcServerFileEntry current = null;
            for (DlcServerFileEntry entry : session.listFiles(
                    DlcFxDlcSyncSnapshot.parentOf(snapshot.serverPath(transfer.rel())), true)) {
                if (DlcFxDlcSyncSnapshot.nameOf(transfer.rel()).equals(entry.name)) {
                    current = entry;
                    break;
                }
            }
            if (current == null || current.dir || current.size != transfer.size()) {
                throw new IOException("server file changed during download");
            }
            String expected = current.hash;
            if (expected == null || expected.isBlank()) {
                expected = fetchServerHash(session, snapshot.serverPath(transfer.rel()), snapshot, canceled);
            }
            if (!hash.equals(expected)) {
                throw new IOException("server file hash changed during download");
            }
            checkCanceled(canceled);
            Files.createDirectories(target.getParent());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            cache.putServer(snapshot.serverCacheKey(transfer.rel()), current.size, current.modified, expected);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Server network downloads write with REPLACE_EXISTING, so they are stopped before direct transfer. */
    private static boolean stopServerDownloads(DlcServerSession session, BooleanSupplier canceled)
            throws IOException, InterruptedException {
        if (isCanceled(canceled)) {
            return false;
        }
        session.remoteDownloadCancel();
        session.requiredDownloadCancel();
        long deadline = System.currentTimeMillis() + CANCEL_WAIT_MS;
        while (isRunning(session.remoteDownloadStatus()) || isRunning(session.requiredDownloadStatus())) {
            if (isCanceled(canceled)) {
                return false;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("server download did not stop");
            }
            Thread.sleep(250L);
        }
        return true;
    }

    private static String fetchServerHash(DlcServerSession session,
                                          String serverPath,
                                          DlcFxDlcSyncSnapshot snapshot,
                                          BooleanSupplier canceled) throws IOException {
        Path verifyDir = DlcFxDlcSyncSnapshot.workDir(snapshot.localDlcRoot)
                .resolve("verify-upload")
                .resolve(UUID.randomUUID().toString());
        Files.createDirectories(verifyDir);
        Path temp = verifyDir.resolve("received.bin");
        try {
            if (!session.downloadFileTo(serverPath, temp, canceled)) {
                throw new IOException("server verification download failed");
            }
            return DlcFxLightHash.compute(temp);
        } finally {
            DlcFxDlcSyncSnapshot.deleteRecursive(verifyDir);
        }
    }

    static boolean isRunning(JsonObject response) {
        JsonElement status = response == null ? null : response.get("status");
        if (status == null || !status.isJsonObject()) {
            return false;
        }
        JsonElement running = status.getAsJsonObject().get("running");
        return running != null && running.isJsonPrimitive() && running.getAsBoolean();
    }

    /** Re-applies synced required entries through each side's shared required-file installer. */
    private static int applyRequired(DlcServerSession session,
                                     DlcFxDlcSyncSnapshot snapshot,
                                     Direction direction,
                                     List<Row> rows,
                                     Set<String> failedKeys,
                                     List<String> errors,
                                     BooleanSupplier canceled) throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Row row : rows) {
            if (row.kind() == DlcFxDlcSyncSnapshot.Kind.REQUIRED
                    && row.dataRel() != null
                    && !row.transfers().isEmpty()
                    && !failedKeys.contains(row.key())) {
                ids.add(row.item().identifier());
                keys.add(row.key());
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        if (direction == Direction.PUSH) {
            try {
                checkCanceled(canceled);
                JsonObject response = session.requiredApply(ids, true);
                JsonElement applied = response.get("applied");
                int count = applied != null && applied.isJsonPrimitive() ? applied.getAsInt() : 0;
                JsonElement stored = response.get("stored");
                int storedCount = stored != null && stored.isJsonPrimitive() ? stored.getAsInt() : 0;
                JsonElement failures = response.get("errors");
                if (failures != null && failures.isJsonArray()) {
                    for (JsonElement failure : failures.getAsJsonArray()) {
                        errors.add("required_apply: " + failure.getAsString());
                    }
                }
                if (!response.has("replaceExisting") || !response.get("replaceExisting").getAsBoolean()) {
                    errors.add("required_apply: server does not support verified replacement; update DLC Manager");
                } else if (count + storedCount != new LinkedHashSet<>(ids).size()) {
                    errors.add("required_apply: some required targets were not applied");
                }
                return count;
            } catch (IOException | RuntimeException error) {
                errors.add("required_apply: " + DlcFxServerConnection.safeMessage(error));
                return 0;
            }
        }
        int applied = 0;
        for (String key : keys) {
            try {
                if (isCanceled(canceled)) {
                    return applied;
                }
                DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(snapshot.localPath(key), snapshot.localDlcRoot);
                if (entry == null) {
                    throw new IOException("required config not readable");
                }
                if (DlcRequiredService.installSynced(entry, snapshot.localGameDir, canceled)) {
                    applied++;
                } else if (!entry.resolveAppliedTargets(snapshot.localGameDir).isEmpty()) {
                    throw new IOException("required targets were not applied");
                }
            } catch (IOException | RuntimeException error) {
                errors.add(key + ": " + DlcFxServerConnection.safeMessage(error));
            }
        }
        return applied;
    }

    private static boolean isCanceled(BooleanSupplier canceled) {
        return Thread.currentThread().isInterrupted() || (canceled != null && canceled.getAsBoolean());
    }

    private static void checkCanceled(BooleanSupplier canceled) throws IOException {
        if (isCanceled(canceled)) {
            throw new java.io.InterruptedIOException("canceled");
        }
    }
}
