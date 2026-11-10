package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.sync.DlcSyncFailure;
import com.cozooo.dlc_manager.sync.DlcSyncModel.*;
import com.cozooo.dlc_manager.sync.DlcSyncModel;
import com.cozooo.dlc_manager.core.*;
import com.cozooo.dlc_manager.client.remote.*;
import com.cozooo.dlc_manager.client.required.*;
import com.cozooo.dlc_manager.client.update.DlcUpdateJournal;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class DlcSyncAdapter implements Adapter {
    private final Quiesce quiesce;
    private final List<Path> configuredRoots = DlcConfig.getDlcRoots().stream().map(p -> p.toAbsolutePath().normalize()).toList();
    private final DlcSyncContent content = new DlcSyncContent(configuredRoots);
    private final Path game = DlcPaths.gameDir().toAbsolutePath().normalize();
    private final Map<String, Path> aliases = new HashMap<>();
    private final Map<String, DlcItem> previous = new HashMap<>();
    public DlcSyncAdapter() { this(canceled -> {}); }
    public DlcSyncAdapter(Quiesce quiesce) { this.quiesce = quiesce; }
    @Override public void quiesce(BooleanSupplier canceled) throws Exception { quiesce.await(canceled); }
    @Override public Path workDirectory() { return configuredRoots.get(0).resolve("tmp/sync"); }
    private List<Path> roots() { return configuredRoots; }
    private String id(Path root, Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) throw new DlcSyncFailure("outside_root");
        return roots().indexOf(root) + ":" + root.relativize(normalized).toString().replace('\\', '/');
    }
    @Override public Path resolve(String id) throws IOException {
        if (id != null && id.startsWith("manager:")) return content.managerPath(id);
        int colon = id.indexOf(':');
        if (colon <= 0) throw new DlcSyncFailure("invalid_resource");
        int index;
        try { index = Integer.parseInt(id.substring(0, colon)); }
        catch (NumberFormatException error) { throw new DlcSyncFailure("invalid_root").causedBy(error); }
        if (index < 0 || index >= roots().size()) throw new DlcSyncFailure("root_missing");
        String relative = id.substring(colon + 1);
        if (relative.isBlank() || relative.contains("\\") || relative.contains(":") || Arrays.stream(relative.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) throw new DlcSyncFailure("invalid_path");
        Path root = roots().get(index), path = aliases.getOrDefault(id, root.resolve(relative).normalize());
        if (!path.startsWith(root) || path.equals(root)) throw new DlcSyncFailure("outside_root");
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent()) if (Files.isSymbolicLink(part)) throw new DlcSyncFailure("symbolic_path");
        return path;
    }
    private File file(Path root, Path path, boolean config) throws IOException { return file(root, path, path, config); }
    private File file(Path root, Path path, Path canonical, boolean config) throws IOException {
        if (path == null) throw new DlcSyncFailure("filename_missing");
        String id = id(root, canonical);
        if (!config) aliases.put(id, path.toAbsolutePath().normalize());
        resolve(id);
        return new File(id, Files.exists(path) ? com.cozooo.dlc_manager.sync.DlcSyncArchive.size(path) : -1, Files.exists(path) ? DlcFileMatch.hash(path) : null);
    }
    private List<DlcItem> items() { return new DlcScanner().scan(false).values().stream().flatMap(List::stream).toList(); }
    private Item remoteItem(Path root, DlcRemoteEntry entry, List<DlcItem> scanned) throws IOException {
        Path data = entry.getTargetPath();
        Boolean enabled = entry.getType() == DlcType.DLC_PACK ? null : false;
        for (DlcItem item : scanned) if (data != null && item.getSourcePath().toAbsolutePath().normalize().equals(data.toAbsolutePath().normalize())) enabled = item.isEnabled();
        List<File> resources = new ArrayList<>();
        if (data != null) resources.add(file(root, data, entry.resolveTargetPath(data.getFileName().toString()), false));
        return new Item(id(root, entry.getConfigPath()), entry.getDlcIdentifier(), file(root, entry.getConfigPath(), true), resources, enabled, entry.getDisplayNames(), entry.getTips());
    }
    private Item requiredItem(Path root, DlcRequiredEntry entry) throws IOException {
        String name = DlcRequiredService.resolveExistingFileName(entry);
        if (name == null || name.isBlank()) name = entry.getRequiredFileName();
        List<File> resources = new ArrayList<>();
        if (name != null && !name.isBlank()) resources.add(file(root, entry.resolveRequiredPath(name), entry.resolveDownloadPath(name), false));
        return new Item(id(root, entry.getConfigPath()), entry.getIdentifier(), file(root, entry.getConfigPath(), true), resources, null, localized(entry.getConfigPath(), "DisplayName"), localized(entry.getConfigPath(), "tip"));
    }
    private Map<String, String> localized(Path config, String category) throws IOException {
        if (config == null) return Map.of();
        var object = DlcDcConfigIO.getObjectIgnoreCase(DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.GENERIC), category);
        Map<String, String> result = new LinkedHashMap<>();
        if (object != null) for (var entry : object.entrySet()) {
            String language = DlcDcConfigIO.normalizeLangKey(entry.getKey());
            if (language != null && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString())
                result.put(language, entry.getValue().getAsString());
        }
        return result;
    }
    private boolean synchronizable(Path path) {
        String name = path.getFileName().toString();
        return DlcDcFormatUtil.isDcName(name) && !name.equalsIgnoreCase("notice.dc") && !name.equalsIgnoreCase("_metadata.dc");
    }
    private void validateConfigurations(Path root, String category, int depth) throws IOException {
        Path directory = root.resolve(category);
        if (!Files.exists(directory)) return;
        resolve(id(root, directory));
        try (var paths = Files.walk(directory, depth)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(this::synchronizable).filter(p -> !p.startsWith(root.resolve("remote/redirect"))).toList()) {
                if (Thread.currentThread().isInterrupted()) throw new DlcSyncFailure("comparison_canceled");
                resolve(id(root, path)); com.cozooo.dclanguage.DcDocuments.read(path);
            }
        }
    }
    @Override public List<Item> manifest() throws IOException {
        List<Item> result = new ArrayList<>();
        List<DlcItem> scanned = items();
        for (Path root : roots()) {
            validateConfigurations(root, "remote", Integer.MAX_VALUE); validateConfigurations(root, "required", 1);
            for (DlcRemoteEntry entry : DlcRemoteLoader.load(root)) { if (!synchronizable(entry.getConfigPath())) continue; if (Thread.currentThread().isInterrupted()) throw new DlcSyncFailure("comparison_canceled"); result.add(remoteItem(root, entry, scanned)); }
            for (DlcRequiredEntry entry : DlcRequiredLoader.load(root)) { if (!synchronizable(entry.getConfigPath())) continue; if (Thread.currentThread().isInterrupted()) throw new DlcSyncFailure("comparison_canceled"); result.add(requiredItem(root, entry)); }
        }
        result.addAll(content.manifest());
        return result;
    }
    private boolean auxiliary(String key) { return key.startsWith("manager:") || key.substring(key.indexOf(':') + 1).startsWith("preset/") || key.substring(key.indexOf(':') + 1).startsWith("update/"); }
    @Override public Item item(String key) throws IOException {
        validateKey(key);
        if (auxiliary(key)) return content.item(key, resolve(key));
        Path root = roots().get(Integer.parseInt(key.substring(0, key.indexOf(':'))));
        if (key.substring(key.indexOf(':') + 1).startsWith("remote/")) {
            DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(resolve(key), root);
            if (entry == null) throw new DlcSyncFailure("invalid_remote");
            return remoteItem(root, entry, items());
        }
        return requiredItem(root, required(key));
    }
    @Override public void validateKey(String key) throws IOException {
        Path path = resolve(key);
        String relative = key.substring(key.indexOf(':') + 1);
        if (key.startsWith("manager:")) return;
        if (relative.startsWith("preset/") && DlcDcFormatUtil.isDcName(relative)) return;
        if (relative.startsWith("update/") && relative.indexOf('/', 7) < 0 && relative.toLowerCase(Locale.ROOT).endsWith(".zip")) return;
        if (!(relative.startsWith("remote/") || relative.startsWith("required/")) || relative.startsWith("remote/redirect/")
            || !DlcDcFormatUtil.isDcName(relative) || path.getFileName().toString().equalsIgnoreCase("notice.dc") || path.getFileName().toString().equalsIgnoreCase("_metadata.dc")) throw new DlcSyncFailure("not_config");
        if (!previous.containsKey(key) && relative.startsWith("remote/") && Files.isRegularFile(path)) {
            DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(path, roots().get(Integer.parseInt(key.substring(0, key.indexOf(':')))));
            if (entry != null && entry.getTargetPath() != null) for (DlcItem item : items()) {
                if (item.getSourcePath().toAbsolutePath().normalize().equals(entry.getTargetPath().toAbsolutePath().normalize())) previous.put(key, item);
            }
        }
    }
    @Override public void validateIncoming(Item incoming) throws IOException {
        if (DlcSyncModel.category(incoming) == Category.UPDATE && !DlcSyncModel.eligibleUpdates(List.of(incoming), content.manifest()).contains(incoming.key()))
            throw new DlcSyncFailure("update_version");
    }
    @Override public void writeSettings(String key, com.google.gson.JsonObject settings, Journal journal) throws IOException {
        if (!key.equals("manager:config.dc")) throw new DlcSyncFailure("not_settings");
        DlcSyncSettings.write(content.managerPath(key), settings, journal);
    }
    @Override public Journal journal(Path directory) throws IOException {
        var presetPaths = com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.getAppliedDisabledRelativePathsByPreset();
        DlcUpdateJournal journal = DlcUpdateJournal.openSync(game, directory);
        try {
            mark(directory, "pending");
            journal.capture(com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.getStatePath());
            for (Path root : roots()) {
                journal.capture(root.resolve("tmp/state.dc"));
                journal.capture(root.resolve("tmp/dlc/history.dc"));
                journal.capture(root.resolve("tmp/required/history.dc"));
            }
        } catch (IOException | RuntimeException error) { journal.close(); throw error; }
        return new Journal() {
            public void capture(Path path) throws IOException { journal.capture(path); }
            public void rollback() throws IOException { journal.rollback(); reload(); com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.setAppliedDisabledRelativePathsByPreset(presetPaths); mark(directory, "restored"); }
            public void commit() throws IOException { mark(directory, "committed"); }
            public void close() { journal.close(); }
        };
    }
    @Override public void prepare(String key, boolean force, BooleanSupplier canceled, Progress progress) throws Exception {
        validateKey(key);
        if (auxiliary(key)) return;
        Path config = resolve(key);
        Path root = roots().get(Integer.parseInt(key.substring(0, key.indexOf(':'))));
        DlcUpdateJournal.beforeWrite(config);
        if (key.substring(key.indexOf(':') + 1).startsWith("remote/")) {
            DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(config, root);
            if (entry == null) throw new DlcSyncFailure("invalid_remote");
            if (force && entry.getTargetPath() != null && Files.exists(entry.getTargetPath())) {
                DlcUpdateJournal.beforeWrite(entry.getTargetPath()); DlcResourceHistory.delete(entry.getTargetPath());
            }
            if (entry.getTargetPath() == null || !Files.exists(entry.getTargetPath())) DlcRemoteService.downloadOne(entry, (done, total) -> progress.update("downloading", key, done, total), canceled::getAsBoolean);
        } else {
            DlcRequiredEntry entry = required(key);
            String existing = DlcRequiredService.resolveExistingFileName(entry);
            if (force && existing != null) { Path path = entry.resolveRequiredPath(existing); DlcUpdateJournal.beforeWrite(path); DlcResourceHistory.delete(path); }
            String available = DlcRequiredService.resolveExistingFileName(entry);
            if (available == null || entry.resolveRequiredPath(available) == null || !Files.isRegularFile(entry.resolveRequiredPath(available))) DlcRequiredService.downloadOne(entry, (done, total) -> progress.update("downloading", key, done, total), canceled::getAsBoolean);
        }
    }
    private DlcRequiredEntry required(String key) throws IOException {
        Path config = resolve(key);
        for (Path root : roots()) for (DlcRequiredEntry entry : DlcRequiredLoader.load(root)) if (entry.getConfigPath().toAbsolutePath().normalize().equals(config)) return entry;
        throw new DlcSyncFailure("invalid_required");
    }
    @Override public boolean apply(String key, Boolean enabled, Journal journal, BooleanSupplier canceled) throws Exception {
        validateKey(key);
        if (canceled.getAsBoolean()) throw new DlcSyncFailure("canceled");
        if (auxiliary(key)) {
            Item value = content.item(key, resolve(key));
            if (DlcSyncModel.category(value) == Category.UPDATE) { validateIncoming(value); return false; }
            if (DlcSyncModel.category(value) == Category.PRESET) return enabled == null || DlcSyncPresets.apply(value.identifier(), enabled, canceled);
            return true;
        }
        if (key.substring(key.indexOf(':') + 1).startsWith("required/")) {
            DlcRequiredEntry entry = required(key);
            DlcRequiredInstaller.install(entry, path -> { if (canceled.getAsBoolean()) throw new DlcSyncFailure("canceled"); journal.capture(path); });
            return !entry.resolveInstallationTargets(DlcRequiredService.resolveExistingFileName(entry)).isEmpty();
        }
        Path root = roots().get(Integer.parseInt(key.substring(0, key.indexOf(':'))));
        DlcRemoteEntry remote = DlcRemoteLoader.loadSingle(resolve(key), root);
        if (remote == null || remote.getTargetPath() == null || !Files.exists(remote.getTargetPath())) throw new DlcSyncFailure("remote_missing");
        if (remote.getType() == DlcType.DLC_PACK) return false;
        for (DlcItem item : items()) if (item.getSourcePath().toAbsolutePath().normalize().equals(remote.getTargetPath().toAbsolutePath().normalize())) {
            DlcItem old = previous.get(key);
            boolean restart = item.isEnabled() || Boolean.TRUE.equals(enabled) || old != null && old.isEnabled();
            if (old != null && !old.getId().equals(item.getId()) && old.isEnabled()) { old.setEnabled(false); DlcApplier.applySynced(old); }
            if (enabled != null) item.setEnabled(enabled);
            DlcApplier.applySynced(item);
            return restart;
        }
        throw new DlcSyncFailure("apply");
    }
    private void mark(Path directory, String status) throws IOException {
        com.google.gson.JsonObject data = new com.google.gson.JsonObject(); data.addProperty("status", status);
        Path temp = directory.resolve("sync.pending.dc");
        Files.createDirectories(directory);
        if (!DlcDcConfigIO.write(temp, data, DlcDcConfigIO.Layout.GENERIC)) throw new DlcSyncFailure("recovery_save");
        try { Files.move(temp, directory.resolve("sync.dc"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(temp, directory.resolve("sync.dc"), StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public void recoverOperation(String operation) throws IOException {
        UUID.fromString(operation);
        restore(workDirectory().resolve(operation), true);
    }
    @Override public void recover() throws IOException { restore(workDirectory(), false); }
    private void restore(Path directory, boolean committed) throws IOException {
        if (!Files.isDirectory(directory)) return;
        boolean restored = false;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("sync.dc")).sorted(Comparator.reverseOrder()).toList()) {
                com.google.gson.JsonObject data = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
                if (data == null) throw new DlcSyncFailure("invalid_recovery", path);
                String status = DlcDcConfigIO.getStringIgnoreCase(data, "status");
                if (!"pending".equals(status) && !(committed && "committed".equals(status))) continue;
                try (DlcUpdateJournal journal = DlcUpdateJournal.open(game, path.getParent())) { journal.rollback(); }
                mark(path.getParent(), "restored");
                restored = true;
            }
        }
        reload();
        if (restored) com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.setAppliedDisabledRelativePathsByPreset(com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.getDesiredDisabledRelativePathsByPreset());
    }
    @Override public void reload() { DlcStateConfig.reloadFromDisk(); DlcRuntimeState.updateItemIndex(items()); DlcSyncPresets.reload(); }
}
