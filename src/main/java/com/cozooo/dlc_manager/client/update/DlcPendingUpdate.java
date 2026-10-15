package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.pack.DlcPackRecovery;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class DlcPendingUpdate {
    private DlcPendingUpdate() { }

    public static synchronized void schedule(List<DlcUpdatePackage> packages, boolean forceMissing) throws IOException {
        Path root = DlcPaths.dlcRoot();
        if (root == null) throw new IOException("Missing DLC root");
        Path update = root.resolve("tmp/update");
        Path previous = locateUpdate(root);
        if (Files.exists(previous.resolve("pending_update.dc")) || Files.exists(previous.resolve("pending_recovery.dc"))) {
            throw new IOException("A pending update already exists");
        }
        if (Files.exists(update.resolve("pending_update.dc")) || Files.exists(update.resolve("pending_recovery.dc"))) {
            throw new IOException("A pending update already exists");
        }
        String id = UUID.randomUUID().toString();
        Path directory = update.resolve("jobs").resolve(id);
        Files.createDirectories(directory);
        JsonArray archives = new JsonArray();
        boolean uninstall = false;
        for (int index = 0; index < packages.size(); index++) {
            DlcUpdatePackage pkg = packages.get(index);
            String name = "archive-" + index + ".zip";
            Path copy = directory.resolve(name);
            Files.copy(pkg.getArchivePath(), copy, StandardCopyOption.COPY_ATTRIBUTES);
            JsonObject archive = new JsonObject();
            archive.addProperty("file", name);
            archive.addProperty("hash", DlcFileMatch.hash(copy));
            archive.addProperty("ignore_unknown", pkg.getCompatibilityAction() == DlcUpdatePackage.CompatibilityAction.IGNORE);
            archives.add(archive);
            uninstall |= pkg.isAllowUninstallPacks();
        }
        JsonObject request = new JsonObject();
        request.addProperty("format", 1);
        request.addProperty("job", id);
        request.addProperty("force_missing", forceMissing);
        request.addProperty("uninstall_incompatible_packs", uninstall);
        request.add("archives", archives);
        write(update.resolve("pending_update.dc"), request);
        try { locate(update); }
        catch (IOException exception) {
            Files.deleteIfExists(update.resolve("pending_update.dc"));
            throw exception;
        }
    }

    public static synchronized boolean process(Consumer<String> log, boolean restartRequired) throws IOException {
        Path root = DlcPaths.dlcRoot();
        if (root == null) return false;
        Path update = locateUpdate(root);
        root = update.getParent().getParent();
        Path pending = update.resolve("pending_update.dc");
        Path recovery = update.resolve("pending_recovery.dc");
        if (!Files.exists(pending) && !Files.exists(recovery)) {
            Files.deleteIfExists(locatorPath());
            return false;
        }
        Path directory = null;
        DlcUpdateJournal journal = null;
        DlcPackUpdateCoordinator.UpdateSession packSession = null;
        boolean recovered = true;
        try {
            locate(update);
            if (Files.exists(recovery)) {
                JsonObject previous = DcDocuments.read(recovery).toJson();
                directory = job(update, previous.get("job").getAsString());
                if (!previous.has("committed") || !previous.get("committed").getAsBoolean()) {
                    journal = DlcUpdateJournal.open(DlcPaths.gameDir(), directory);
                    DlcPackRecovery.recover(DlcPaths.gameDir(), root);
                    journal.rollback();
                    throw new IOException("The previous update was interrupted and has been rolled back");
                }
                saveResult(true, "OK", restartRequired);
                return true;
            }
            JsonObject request = DcDocuments.read(pending).toJson();
            if (!request.has("format") || request.get("format").getAsInt() != 1) throw new IOException("Unsupported pending update format");
            directory = job(update, request.get("job").getAsString());
            List<DlcUpdatePackage> packages = new ArrayList<>();
            for (var value : request.getAsJsonArray("archives")) {
                JsonObject archive = value.getAsJsonObject();
                Path path = directory.resolve(archive.get("file").getAsString()).normalize();
                if (!path.startsWith(directory) || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) throw new IOException("Invalid pending update archive");
                if (!DlcFileMatch.hash(path).equals(archive.get("hash").getAsString())) throw new IOException("Pending update archive changed: " + path.getFileName());
                DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(path);
                if (pkg == null) throw new IOException("Invalid pending update package");
                boolean ignore = archive.has("ignore_unknown") && archive.get("ignore_unknown").getAsBoolean();
                if (!ignore && !pkg.getManifest().getUnknownParameters().isEmpty()) {
                    throw new IOException("Unknown update parameters: " + String.join(", ", pkg.getManifest().getUnknownParameters()));
                }
                pkg.setCompatibilityAction(ignore ? DlcUpdatePackage.CompatibilityAction.IGNORE : DlcUpdatePackage.CompatibilityAction.RESUME);
                packages.add(pkg);
            }
            if (packages.isEmpty()) throw new IOException("Pending update contains no packages");
            JsonObject marker = new JsonObject();
            marker.addProperty("job", request.get("job").getAsString());
            marker.addProperty("committed", false);
            write(recovery, marker);
            journal = DlcUpdateJournal.open(DlcPaths.gameDir(), directory);
            journal.capture(DlcPaths.gameDir().resolve("config/dlc_manager"));
            for (Path dlcRoot : DlcConfig.getDlcRoots()) {
                for (String folder : List.of("info", "remote", "required", "preset", "tmp/pack", "tmp/history", "tmp/state.dc")) journal.capture(dlcRoot.resolve(folder));
            }
            DlcStateConfig.reloadFromDisk();
            for (var state : DlcStateConfig.getAllStates().values()) {
                if (state != null && state.appliedTargets != null) {
                    for (String target : state.appliedTargets) journal.capture(DlcPaths.gameDir().resolve(target));
                }
            }
            DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), root);
            var version = packages.get(packages.size() - 1).getManifest().getVersion();
            packSession = coordinator.prepareForUpdate(
                version.getBaseNew() == null ? version.getBase() : version.getBaseNew(),
                version.getDlcNew() == null ? version.getDlcOld() : version.getDlcNew(),
                request.has("uninstall_incompatible_packs") && request.get("uninstall_incompatible_packs").getAsBoolean());
            DlcUpdateService.UpdateApplyResult result = DlcUpdateService.resumeUpdates(packages,
                request.has("force_missing") && request.get("force_missing").getAsBoolean(), (status, progress) -> log.accept(status));
            if (!result.isSuccess() || result.isHelperScheduled()) throw new IOException(result.getMessage());
            packSession.finishAfterSuccessfulUpdate();
            DlcConfig.reloadFromDisk(com.cozooo.dlc_manager.platform.DlcPlatformServices.isDedicatedServer());
            marker.addProperty("committed", true);
            write(recovery, marker);
            saveResult(true, "OK", restartRequired);
            log.accept(restartRequired ? "Pending hot update completed; restart required to reload mods" : "Pending hot update completed");
            return true;
        } catch (Exception failure) {
            recovered = false;
            try {
                if (packSession != null) packSession.restoreAfterFailedUpdate();
                if (journal != null) {
                    journal.rollback();
                    DlcUpdateHashIndex.invalidate(DlcPaths.gameDir(), root, List.of("mods"));
                    DlcConfig.reloadFromDisk(com.cozooo.dlc_manager.platform.DlcPlatformServices.isDedicatedServer());
                    DlcStateConfig.reloadFromDisk();
                    com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.clearAll();
                    com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.reload();
                }
                recovered = true;
            } catch (Exception restore) {
                failure.addSuppressed(restore);
            }
            String message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
            saveResult(false, message, false, recovered);
            log.accept("Pending hot update failed: " + message);
            if (!recovered) throw new IOException("Pending update rollback could not complete", failure);
            return true;
        } finally {
            if (journal != null) journal.close();
            Files.deleteIfExists(pending);
            if (recovered) {
                Files.deleteIfExists(recovery);
                Files.deleteIfExists(locatorPath());
                if (directory != null) DlcUpdateService.deleteRecursive(directory);
            }
        }
    }

    public static boolean hasPending() throws IOException {
        Path root = DlcPaths.dlcRoot();
        if (root == null) return false;
        Path directory = locateUpdate(root);
        return Files.exists(directory.resolve("pending_update.dc")) || Files.exists(directory.resolve("pending_recovery.dc"));
    }

    public static JsonObject readResult() throws IOException {
        Path path = resultPath();
        return Files.exists(path) ? DcDocuments.read(path).toJson() : null;
    }

    public static void acknowledgeResult() throws IOException { Files.deleteIfExists(resultPath()); }

    private static Path resultPath() { return DlcPaths.gameDir().resolve(".dlc-manager/pending_update_result.dc"); }

    private static Path locatorPath() { return DlcPaths.gameDir().resolve(".dlc-manager/pending_update_location.dc"); }

    private static void locate(Path update) throws IOException {
        JsonObject locator = new JsonObject();
        locator.addProperty("directory", update.toAbsolutePath().normalize().toString());
        write(locatorPath(), locator);
    }

    private static Path locateUpdate(Path root) throws IOException {
        if (!Files.exists(locatorPath())) return root.resolve("tmp/update").toAbsolutePath().normalize();
        Path path = Path.of(DcDocuments.read(locatorPath()).toJson().get("directory").getAsString()).toAbsolutePath().normalize();
        if (!path.endsWith(Path.of("tmp/update"))) throw new IOException("Invalid pending update location");
        return path;
    }

    private static Path job(Path update, String id) throws IOException {
        if (!id.matches("[0-9a-fA-F-]{36}")) throw new IOException("Invalid pending update job");
        Path directory = update.resolve("jobs").resolve(id).toAbsolutePath().normalize();
        for (Path part = directory; part != null && part.startsWith(update.toAbsolutePath().normalize()); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Invalid pending update job link");
        }
        return directory;
    }

    private static void saveResult(boolean success, String message, boolean restart) throws IOException {
        saveResult(success, message, restart, true);
    }

    private static void saveResult(boolean success, String message, boolean restart, boolean rollbackComplete) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("success", success);
        result.addProperty("rollback_complete", rollbackComplete);
        result.addProperty("message", message);
        result.addProperty("restart_required", restart);
        write(resultPath(), result);
    }

    private static void write(Path path, JsonObject value) throws IOException {
        DcDocuments.write(path, DcDocuments.fromJson(value));
    }
}
