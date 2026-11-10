package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.core.*;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.sync.DlcSyncFailure;
import com.cozooo.dlc_manager.sync.DlcSyncModel;
import com.cozooo.dlc_manager.sync.DlcSyncModel.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class DlcSyncContent {
    private final List<Path> roots;
    private final Path manager = DlcPaths.managerConfigDir();
    DlcSyncContent(List<Path> roots) { this.roots = List.copyOf(roots); }
    Path managerPath(String key) throws IOException {
        if (!key.equals("manager:config.dc") && !key.equals("manager:version.dc")) throw new DlcSyncFailure("not_settings");
        Path path = manager.resolve(key.substring("manager:".length()));
        for (Path part = path; part != null && part.startsWith(manager); part = part.getParent()) if (Files.isSymbolicLink(part)) throw new DlcSyncFailure("symbolic_settings");
        return path;
    }
    private boolean configKey(String key) { return key.startsWith("manager:") || key.substring(key.indexOf(':') + 1).startsWith("preset/"); }
    List<Item> manifest() throws IOException {
        List<Item> result = new ArrayList<>();
        result.add(item("manager:config.dc", managerPath("manager:config.dc")));
        Path canonicalVersion = DlcPaths.resolveVersionConfigPath(roots.isEmpty() ? null : roots.get(0));
        if (Files.isRegularFile(canonicalVersion)) {
            JsonObject value = DlcDcConfigIO.read(canonicalVersion, DlcDcConfigIO.Layout.GENERIC);
            String base = DlcDcConfigIO.getStringIgnoreCase(value, "version");
            if (base == null || base.isBlank()) base = DlcDcConfigIO.getStringIgnoreCase(value, "base");
            String dlc = DlcDcConfigIO.getStringIgnoreCase(value, "dlc");
            if (base != null && !base.isBlank()) {
                String key = "manager:version.dc";
                String normalizedDlc = dlc == null ? "" : dlc;
                result.add(new Item(key, "version", file(key, canonicalVersion), List.of(), null, Map.of(), Map.of(), new Update(base, normalizedDlc, base, normalizedDlc, DlcPlatformServices.isDedicatedServer() ? "server" : "client")));
            }
        }
        for (int index = 0; index < roots.size(); index++) {
            Path root = roots.get(index), presets = root.resolve("preset"), updates = root.resolve("update");
            if (Files.isDirectory(presets)) try (var paths = Files.walk(presets)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> DlcDcFormatUtil.isDcName(p.getFileName().toString())).sorted().toList()) {
                    result.add(item(index + ":" + root.relativize(path).toString().replace('\\', '/'), path));
                }
            }
            if (Files.isDirectory(updates)) try (var paths = Files.list(updates)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().toList()) {
                    if (DlcUpdatePackage.fromZip(path) != null) try { result.add(item(index + ":update/" + path.getFileName(), path)); } catch (IOException ignored) {}
                }
            }
        }
        return result;
    }
    private File file(String key, Path path) throws IOException {
        Path anchor = path.startsWith(manager) ? manager : roots.stream().filter(path::startsWith).findFirst().orElseThrow(() -> new DlcSyncFailure("outside_roots"));
        for (Path part = path; part != null && part.startsWith(anchor); part = part.getParent()) if (Files.isSymbolicLink(part)) throw new DlcSyncFailure("symbolic_file");
        return new File(key, Files.size(path), DlcFileMatch.hash(path));
    }
    Item item(String key, Path path) throws IOException {
        if (key.equals("manager:version.dc")) {
            JsonObject value = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
            String base = DlcDcConfigIO.getStringIgnoreCase(value, "version");
            if (base == null || base.isBlank()) base = DlcDcConfigIO.getStringIgnoreCase(value, "base");
            if (base == null || base.isBlank()) throw new DlcSyncFailure("version_missing", key);
            String dlc = DlcDcConfigIO.getStringIgnoreCase(value, "dlc");
            String normalizedDlc = dlc == null ? "" : dlc;
            return new Item(key, "version", file(key, path), List.of(), null, Map.of(), Map.of(), new Update(base, normalizedDlc, base, normalizedDlc, DlcPlatformServices.isDedicatedServer() ? "server" : "client"));
        }
        if (key.startsWith("manager:")) return DlcSyncSettings.item(key, path);
        if (!Files.isRegularFile(path)) throw new DlcSyncFailure("file_missing", key);
        if (configKey(key)) {
            com.cozooo.dclanguage.DcDocuments.read(path);
            JsonObject object = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
            String identifier = DlcDcConfigIO.getStringIgnoreCase(DlcDcConfigIO.getObjectIgnoreCase(object, "basic"), "identifier");
            if (identifier == null || identifier.isBlank()) identifier = DlcDcConfigIO.getStringIgnoreCase(object, "identifier");
            if (identifier == null || identifier.isBlank()) identifier = DlcDcFormatUtil.stripConfigExtension(path.getFileName().toString());
            identifier = identifier.trim();
            Map<String, String> names = localized(object, "DisplayName");
            Boolean enabled = com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.loadPresetStatus().getOrDefault(identifier, com.cozooo.dlc_manager.client.preset.DlcPresetStatus.OFF).isEnabled();
            return new Item(key, identifier, file(key, path), List.of(), enabled, names, localized(object, "tip"));
        }
        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(path);
        if (pkg == null || pkg.getManifest().getVersion() == null) throw new DlcSyncFailure("invalid_update", key);
        var version = pkg.getManifest().getVersion();
        String base = version.getBase(), next = version.getBaseNew(), old = version.getDlcOld(), updated = version.getDlcNew();
        if (base == null || base.isBlank()) throw new DlcSyncFailure("update_versions_missing", key);
        if (next == null || next.isBlank()) next = base;
        if (old == null || old.isBlank()) old = "";
        if (updated == null || updated.isBlank()) updated = old;
        return new Item(key, pkg.getName(), file(key, path), List.of(), null, Map.of(), pkg.getManifest().getChangelog(), new Update(base, old, next, updated, version.getPlatform()));
    }
    private Map<String, String> localized(JsonObject object, String category) {
        JsonObject values = DlcDcConfigIO.getObjectIgnoreCase(object, category);
        Map<String, String> result = new LinkedHashMap<>();
        if (values != null) for (var value : values.entrySet()) {
            String language = DlcDcConfigIO.normalizeLangKey(value.getKey());
            if (language != null && value.getValue().isJsonPrimitive() && value.getValue().getAsJsonPrimitive().isString()) result.put(language, value.getValue().getAsString());
        }
        return result;
    }
}
