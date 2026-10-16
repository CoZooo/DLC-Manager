package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.bootstrap.DlcEarlyInstaller;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.remote.DlcRemoteService;
import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.server.DlcServerDownloadService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcResourceLifecycleTest {
    @TempDir Path game;
    Path root;

    @BeforeEach void setup() {
        root = game.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
        DlcConfig.setResourcepackRoot("resourcepacks");
        DlcConfig.setDatapackRoot("datapacks");
        DlcStateConfig.load();
    }

    @AfterEach void cleanup() {
        PlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    private Path write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return path;
    }

    private void install() throws Exception {
        DlcEarlyInstaller.install(game, game.resolve("config"), "forge", "1.20.1", false, ignored -> {});
    }

    private List<DlcItem> items() {
        return new DlcScanner().scan(true).values().stream().flatMap(List::stream).toList();
    }

    private DlcItem mod(Path source) {
        return new DlcItem("mod:DLC|main.jar", "main", "main", DlcType.MOD, source, null, false, null, null, List.of(), List.of(), true, false);
    }

    @Test void hashesCompareContentAndDirectoryStructureIndependentlyOfRootNames() throws Exception {
        Path a = write(game.resolve("one.jar"), "content");
        Path b = write(game.resolve("renamed.jar"), "content");
        assertEquals(DlcFileMatch.hash(a), DlcFileMatch.hash(b));
        write(b, "changed");
        assertFalse(DlcFileMatch.matches(a, b, "hash"));
        assertTrue(DlcFileMatch.matches(a, b, "name"));
        Path folder = game.resolve("folder");
        Path other = game.resolve("other");
        write(folder.resolve("nested/a.txt"), "same");
        write(other.resolve("nested/a.txt"), "same");
        assertEquals(DlcFileMatch.hash(folder), DlcFileMatch.hash(other));
        write(other.resolve("extra.txt"), "extra");
        assertFalse(DlcFileMatch.matches(folder, other, "hash"));
    }

    @Test void startupValidatesContentRecognizesRenamedFilesAndStoresStateOnlyInDlcRoot() throws Exception {
        write(root.resolve("main.jar"), "source");
        Path target = write(game.resolve("mods/main.jar"), "other");
        DlcStateConfig.load();
        assertFalse(DlcStateConfig.getState("mod:DLC|main.jar").enabled);
        Files.delete(target);
        write(game.resolve("mods/renamed.jar"), "source");
        DlcStateConfig.load();
        var state = DlcStateConfig.getState("mod:DLC|main.jar");
        assertTrue(state.enabled);
        assertEquals(List.of("mods/renamed.jar"), state.appliedTargets);
        assertTrue(Files.exists(root.resolve("tmp/state.dc")));
        assertFalse(Files.exists(game.resolve("config/dlc_manager/state.dc")));
    }

    @Test void infoModeNameKeepsTheFilenameMatchingBehavior() throws Exception {
        write(root.resolve("main.jar"), "source");
        write(game.resolve("mods/main.jar"), "different");
        write(root.resolve("info/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\nmode = \"name\"\ntype = \"mod\"\n");
        assertTrue(items().get(0).isEnabled());
        assertEquals("name", items().get(0).getMatchMode());
    }

    @Test void remoteDownloadsPreferTheNewNestedLocationWithoutMigratingLegacyFiles() throws Exception {
        write(root.resolve("remote/nested/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\ntype = \"mod\"\nmode = \"name\"\n");
        Path legacy = write(root.resolve("nested/main.jar"), "old");
        DlcRemoteEntry entry = DlcRemoteLoader.load(root).get(0);
        assertEquals(legacy, entry.getTargetPath());
        String oldId = items().get(0).getId();
        Path cached = write(root.resolve("downloaded/DLCs/nested/main.jar"), "new");
        assertEquals(cached, entry.getTargetPath());
        List<DlcItem> scanned = items();
        assertEquals(1, scanned.size());
        assertEquals(oldId, scanned.get(0).getId());
        assertEquals(cached, scanned.get(0).getSourcePath());
        assertEquals("name", scanned.get(0).getMatchMode());
        assertEquals("old", Files.readString(legacy));
    }

    @Test void requiredRemovalRestoresEarliestBackupDeletesCacheAndRetiresEveryBackup() throws Exception {
        Path config = write(root.resolve("required/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        Path source = write(root.resolve("downloaded/required/main.jar"), "first");
        Path target = write(game.resolve("mods/main.jar"), "original");
        install();
        assertEquals("first", Files.readString(target));
        write(source, "second");
        install();
        assertEquals("second", Files.readString(target));
        write(source, "third");
        install();
        Files.delete(config);
        install();
        assertEquals("original", Files.readString(target));
        assertFalse(Files.exists(source));
        JsonObject history = DlcDcConfigIO.read(root.resolve("tmp/required/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        assertEquals(3, history.getAsJsonObject("backups").size());
        for (var value : history.getAsJsonObject("backups").asMap().values()) {
            var backup = value.getAsJsonObject();
            assertTrue(backup.get("old").getAsBoolean());
            assertTrue(backup.get("path").getAsString().endsWith(".old"));
            assertTrue(Files.exists(root.resolve(backup.get("path").getAsString())));
        }
        var recorded = history.getAsJsonObject("resources").asMap().values().iterator().next().getAsJsonObject();
        assertEquals("removed", recorded.get("status").getAsString());
        install();
        assertEquals("original", Files.readString(target));
    }

    @Test void requiredLocalSourcesStayInPlaceAndNameModeDoesNotReplaceExistingTargets() throws Exception {
        Path config = write(root.resolve("required/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\nmode = \"name\"\nappliedTarget = \"mods/main.jar\"\n");
        Path local = write(root.resolve("required/main.jar"), "source");
        Path target = write(game.resolve("mods/main.jar"), "existing");
        install();
        assertEquals("existing", Files.readString(target));
        Files.delete(config);
        install();
        assertTrue(Files.exists(local));
        assertEquals("existing", Files.readString(target));
    }

    @Test void dlcDisableRestoresNewestBackupAndRetiresTheWholeBackupChain() throws Exception {
        Path source = write(root.resolve("main.jar"), "first");
        Path target = write(game.resolve("mods/main.jar"), "original");
        DlcItem item = mod(source);
        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        write(source, "second");
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertEquals("second", Files.readString(target));
        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertEquals("first", Files.readString(target));
        JsonObject history = DlcDcConfigIO.read(root.resolve("tmp/dlc/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        assertEquals(2, history.getAsJsonObject("backups").size());
        assertTrue(history.getAsJsonObject("backups").asMap().values().stream().allMatch(value -> value.getAsJsonObject().get("old").getAsBoolean()));
        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertEquals("first", Files.readString(target));
    }

    @Test void requiredAndDlcBackupsWithTheSameIdentifierRemainIndependent() throws Exception {
        write(root.resolve("required/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"config/required.jar\"\n");
        write(root.resolve("required/main.jar"), "required");
        Path required = write(game.resolve("config/required.jar"), "required-original");
        install();
        DlcItem item = mod(write(root.resolve("main.jar"), "dlc"));
        Path target = write(game.resolve("mods/main.jar"), "dlc-original");
        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertEquals("dlc-original", Files.readString(target));
        assertEquals("required", Files.readString(required));
        assertTrue(Files.exists(root.resolve("tmp/dlc/history.dc")));
        assertTrue(Files.exists(root.resolve("tmp/required/history.dc")));
    }
    @Test void remoteHttpDownloadWritesOnlyTheNestedCacheAndPackDiscoveryPrefersIt() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = "remote archive".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        http.createContext("/pack.zip", exchange -> {
            if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(200, -1);
            else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        http.start();
        try {
            write(root.resolve("remote/nested/pack.dc"), "[basic]\nidentifier = \"pack\"\nfile_name = \"pack.zip\"\ntype = \"dlcpack\"\n[download]\npriority = \"directurl\"\ndirectUrl = \"http://127.0.0.1:" + http.getAddress().getPort() + "/pack.zip\"\n");
            DlcRemoteEntry entry = DlcRemoteLoader.load(root).get(0);
            DlcRemoteService.downloadOne(entry, null, () -> false);
            Path cached = root.resolve("downloaded/DLCs/nested/pack.zip");
            assertArrayEquals(body, Files.readAllBytes(cached));
            assertFalse(Files.exists(root.resolve("pack/pack.zip")));
            Path legacy = write(root.resolve("pack/pack.zip"), "legacy");
            assertEquals(List.of(cached), DlcPackArchive.findArchives(root));
            assertTrue(items().isEmpty());
            Files.delete(cached);
            assertEquals(List.of(legacy), DlcPackArchive.findArchives(root));
        } finally { http.stop(0); }
    }

    @Test void remoteMetadataDoesNotConfuseSameNamedFilesInDifferentDirectories() throws Exception {
        write(root.resolve("remote/first/main.dc"), "[basic]\nidentifier = \"one\"\nfile_name = \"main.jar\"\nmode = \"name\"\ntype = \"mod\"\n");
        write(root.resolve("remote/second/main.dc"), "[basic]\nidentifier = \"two\"\nfile_name = \"main.jar\"\nmode = \"hash\"\ntype = \"mod\"\n");
        write(root.resolve("downloaded/DLCs/first/main.jar"), "one");
        write(root.resolve("downloaded/DLCs/second/main.jar"), "two");
        List<DlcItem> scanned = items();
        assertEquals(2, scanned.size());
        assertEquals("name", scanned.stream().filter(item -> item.getIdentifier().equals("one")).findFirst().orElseThrow().getMatchMode());
        assertEquals("hash", scanned.stream().filter(item -> item.getIdentifier().equals("two")).findFirst().orElseThrow().getMatchMode());
    }

    @Test void disablingRenamedDlcRemovesItsContentAndRestoresTheOriginalFilenameBackup() throws Exception {
        DlcItem item = mod(write(root.resolve("main.jar"), "dlc"));
        Path target = write(game.resolve("mods/main.jar"), "original");
        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        Path renamed = target.resolveSibling("renamed.jar");
        Files.move(target, renamed);
        DlcStateConfig.load();
        item = items().get(0);
        assertTrue(item.isEnabled());
        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertFalse(Files.exists(renamed));
        assertEquals("original", Files.readString(target));
    }

    @Test void nameModeStillRemovesAnEditedManagedDlcOnDisable() throws Exception {
        DlcItem item = mod(write(root.resolve("main.jar"), "source"));
        item.setMatchMode("name");
        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        Path target = write(game.resolve("mods/main.jar"), "edited");
        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertFalse(Files.exists(target));
    }

    @Test void removingANewRequiredInstallationRemovesTheInstalledCopyButKeepsTheLocalSource() throws Exception {
        Path config = write(root.resolve("required/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        Path source = write(root.resolve("required/main.jar"), "source");
        install();
        Files.delete(config);
        install();
        assertFalse(Files.exists(game.resolve("mods/main.jar")));
        assertTrue(Files.exists(source));
    }

    @Test void damagedBackupsPreventRecoveryBeforeAnyTargetIsChanged() throws Exception {
        Path source = write(root.resolve("required/main.jar"), "installed");
        Path config = write(root.resolve("required/main.dc"), "config");
        String key = DlcResourceHistory.key(DlcResourceHistory.relativePath(config), "main");
        Path first = write(game.resolve("mods/first.jar"), "original-first");
        Path second = write(game.resolve("mods/second.jar"), "original-second");
        for (Path target : List.of(first, second)) {
            DlcResourceHistory.prepare(root, "required", key, "main", config, source, target, true);
            DlcResourceHistory.replace(source, target);
        }
        DlcResourceHistory.complete(root, "required", key);
        JsonObject history = DlcDcConfigIO.read(root.resolve("tmp/required/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        JsonObject backup = history.getAsJsonObject("backups").asMap().values().stream().map(value -> value.getAsJsonObject()).filter(value -> value.get("target").getAsString().equals(DlcResourceHistory.relativePath(second))).findFirst().orElseThrow();
        write(root.resolve(backup.get("path").getAsString()), "corrupt");
        assertThrows(IOException.class, () -> DlcResourceHistory.restore(root, "required", key, true));
        assertEquals("installed", Files.readString(first));
        assertEquals("installed", Files.readString(second));
        history = DlcDcConfigIO.read(root.resolve("tmp/required/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        assertTrue(history.getAsJsonObject("resources").getAsJsonObject(key).get("active").getAsBoolean());
    }

    @Test void serverRequiredListsUseHashesAndRecoverDeletedConfigurations() throws Exception {
        Path config = write(root.resolve("required/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        write(root.resolve("downloaded/required/main.jar"), "source");
        Path target = write(game.resolve("mods/main.jar"), "original");
        var pending = DlcServerDownloadService.collectRequiredItems(root).get(0);
        assertTrue(pending.hasFile);
        assertTrue(pending.needsApply);
        install();
        assertTrue(DlcServerDownloadService.collectRequiredItems(root).get(0).applied);
        Files.delete(config);
        assertTrue(DlcServerDownloadService.collectRequiredItems(root).isEmpty());
        assertEquals("original", Files.readString(target));
        assertFalse(Files.exists(root.resolve("downloaded/required/main.jar")));
    }

    @Test void startupPartitionsStateFilesByDlcRoot() throws Exception {
        DlcConfig.setDlcRoot("first,second");
        write(game.resolve("first/a.jar"), "one");
        write(game.resolve("second/b.jar"), "two");
        write(game.resolve("mods/a.jar"), "one");
        write(game.resolve("mods/b.jar"), "wrong");
        DlcStateConfig.load();
        JsonObject first = DlcDcConfigIO.read(game.resolve("first/tmp/state.dc"), DlcDcConfigIO.Layout.GENERIC);
        JsonObject second = DlcDcConfigIO.read(game.resolve("second/tmp/state.dc"), DlcDcConfigIO.Layout.GENERIC);
        assertEquals(1, first.getAsJsonObject("DLC").size());
        assertEquals(1, second.getAsJsonObject("DLC").size());
        assertTrue(DlcStateConfig.getState("mod:first|a.jar").enabled);
        assertFalse(DlcStateConfig.getState("mod:second|b.jar").enabled);
    }
    @Test void managerUpdatesUseNewCachesAndRetainIndependentResourceRecovery() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (String name : List.of("req-next.jar", "remote-next.jar")) {
            byte[] body = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            http.createContext("/" + name, exchange -> {
                if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(200, -1);
                else {
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
        }
        http.start();
        try {
            String url = "http://127.0.0.1:" + http.getAddress().getPort();
            Path requiredConfig = write(root.resolve("required/req.dc"), "[basic]\nidentifier = \"req\"\nfile_name = \"req.jar\"\nappliedTarget = \"mods/\"\n[download]\npriority = \"directurl\"\ndirectUrl = \"" + url + "/old-req.jar\"\n");
            Path localRequired = write(root.resolve("required/req.jar"), "required-first");
            Path requiredTarget = write(game.resolve("mods/req.jar"), "required-original");
            write(root.resolve("remote/nested/remote.dc"), "[basic]\nidentifier = \"remote\"\nfile_name = \"remote.jar\"\ntype = \"mod\"\n[download]\npriority = \"directurl\"\ndirectUrl = \"" + url + "/old-remote.jar\"\n");
            Path localRemote = write(root.resolve("nested/remote.jar"), "remote-first");
            Path remoteTarget = write(game.resolve("mods/remote.jar"), "remote-original");
            install();
            DlcItem item = items().get(0);
            item.setEnabled(true);
            DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
            write(root.resolve("info/version.dc"), "base = \"1.0.0\"\ndlc = \"1.0.0\"\n");
            Path zip = game.resolve("Universal_1.0.0_1.0.0-1.0.0_1.0.1.zip");
            String manifest = "[version]\nplatform = \"universal\"\nbase = \"1.0.0\"\ndlc = \"1.0.0\"\ndlc_new = \"1.0.1\"\n[update]\n  [required]\n    req:\n      directUrl = \"" + url + "/req-next.jar\"\n  [remote]\n    remote:\n      directUrl = \"" + url + "/remote-next.jar\"\n";
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("update.dc"));
                out.write(manifest.replace("/req-next.jar", "/missing.jar").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
            var failed = DlcUpdateService.applyUpdate(DlcUpdatePackage.fromZip(zip), DlcUpdateVersion.load(root), false);
            assertFalse(failed.isSuccess());
            assertEquals("remote-first", Files.readString(remoteTarget));
            assertEquals("required-first", Files.readString(requiredTarget));
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("update.dc"));
                out.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
            var result = DlcUpdateService.applyUpdate(DlcUpdatePackage.fromZip(zip), DlcUpdateVersion.load(root), false);
            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals("req-next.jar", Files.readString(root.resolve("downloaded/required/req-next.jar")));
            assertEquals("remote-next.jar", Files.readString(root.resolve("downloaded/DLCs/nested/remote-next.jar")));
            assertTrue(Files.exists(localRequired));
            assertTrue(Files.exists(localRemote));
            assertEquals("req-next.jar", Files.readString(game.resolve("mods/req-next.jar")));
            assertEquals("remote-next.jar", Files.readString(game.resolve("mods/remote-next.jar")));
            Files.delete(requiredConfig);
            install();
            assertEquals("required-original", Files.readString(requiredTarget));
            assertFalse(Files.exists(game.resolve("mods/req-next.jar")));
            DlcStateConfig.load();
            item = items().stream().filter(candidate -> candidate.getIdentifier().equals("remote")).findFirst().orElseThrow();
            item.setEnabled(false);
            DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
            assertEquals("remote-first", Files.readString(remoteTarget));
            assertFalse(Files.exists(game.resolve("mods/remote-next.jar")));
        } finally { http.stop(0); }
    }
    @Test void requiredRemovalCleansAllRecordedDownloadVersions() throws Exception {
        Path config = write(root.resolve("required/main.dc"), "[basic]\nidentifier = \"main\"\nfile_name = \"first.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        Path first = write(root.resolve("downloaded/required/first.jar"), "first");
        Path target = write(game.resolve("mods/main.jar"), "original");
        install();
        write(config, "[basic]\nidentifier = \"main\"\nfile_name = \"second.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        Path second = write(root.resolve("downloaded/required/second.jar"), "second");
        install();
        Files.delete(config);
        install();
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
        assertEquals("original", Files.readString(target));
    }

    @Test void sharedRequiredCacheIsKeptUntilItsLastConfigurationIsRemoved() throws Exception {
        Path one = write(root.resolve("required/one.dc"), "[basic]\nidentifier = \"one\"\nfile_name = \"common.jar\"\nappliedTarget = \"mods/one.jar\"\n");
        Path two = write(root.resolve("required/two.dc"), "[basic]\nidentifier = \"two\"\nfile_name = \"common.jar\"\nappliedTarget = \"mods/two.jar\"\n");
        Path cache = write(root.resolve("downloaded/required/common.jar"), "common");
        install();
        Files.delete(one);
        install();
        assertTrue(Files.exists(cache));
        assertFalse(Files.exists(game.resolve("mods/one.jar")));
        assertEquals("common", Files.readString(game.resolve("mods/two.jar")));
        Files.delete(two);
        install();
        assertFalse(Files.exists(cache));
        assertFalse(Files.exists(game.resolve("mods/two.jar")));
    }
    @Test void replacingARequiredConfigurationKeepsTheCacheReferencedByItsUninstalledReplacement() throws Exception {
        Path old = write(root.resolve("required/old.dc"), "[basic]\nidentifier = \"old\"\nfile_name = \"common.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        Path cache = write(root.resolve("downloaded/required/common.jar"), "common");
        Path target = write(game.resolve("mods/main.jar"), "original");
        install();
        Files.delete(old);
        Path next = write(root.resolve("required/next.dc"), "[basic]\nidentifier = \"next\"\nfile_name = \"common.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        install();
        assertTrue(Files.exists(cache));
        assertEquals("common", Files.readString(target));
        Files.delete(next);
        install();
        assertFalse(Files.exists(cache));
        assertEquals("original", Files.readString(target));
    }

    private JsonObject history(String kind) throws IOException {
        JsonObject data = DlcDcConfigIO.read(root.resolve("tmp/" + kind + "/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        assertNotNull(data);
        for (var value : data.getAsJsonObject("resources").asMap().values()) {
            JsonObject resource = value.getAsJsonObject();
            for (String field : List.of("config", "source")) assertFalse(Path.of(resource.get(field).getAsString()).isAbsolute());
            for (var download : resource.getAsJsonArray("downloads")) assertFalse(Path.of(download.getAsString()).isAbsolute());
            for (var target : resource.getAsJsonObject("targets").asMap().values()) assertFalse(Path.of(target.getAsJsonObject().get("path").getAsString()).isAbsolute());
        }
        for (var value : data.getAsJsonObject("backups").asMap().values()) {
            JsonObject backup = value.getAsJsonObject();
            for (String field : List.of("target", "path")) assertFalse(Path.of(backup.get(field).getAsString()).isAbsolute());
        }
        assertFalse(data.toString().contains(game.toString()));
        return data;
    }

    @Test void movingTheGameDirectoryKeepsHistoryKeysAndRestoresBothResourceKinds() throws Exception {
        Path workspace = game;
        game = workspace.resolve("original");
        setup();
        write(root.resolve("required/required.dc"), "[basic]\nidentifier = \"required\"\nfile_name = \"required.jar\"\nappliedTarget = \"mods/required.jar\"\n");
        Path required = write(root.resolve("downloaded/required/required.jar"), "required-first");
        write(game.resolve("mods/required.jar"), "required-original");
        install();
        write(required, "required-second");
        install();
        String key = com.cozooo.dlc_manager.client.required.DlcRequiredLoader.load(root).get(0).getHistoryKey();
        JsonObject before = history("required");
        assertEquals("DLC/required/required.dc", before.getAsJsonObject("resources").getAsJsonObject(key).get("config").getAsString());
        assertEquals("mods/required.jar", before.getAsJsonObject("resources").getAsJsonObject(key).getAsJsonObject("targets").asMap().values().iterator().next().getAsJsonObject().get("path").getAsString());
        DlcItem item = mod(write(root.resolve("main.jar"), "dlc-first"));
        write(game.resolve("mods/main.jar"), "dlc-original");
        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        write(root.resolve("main.jar"), "dlc-second");
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        history("dlc");
        String dlcId = item.getId();
        Path moved = workspace.resolve("moved");
        Files.move(game, moved);
        game = moved;
        root = game.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config")));
        assertEquals(key, com.cozooo.dlc_manager.client.required.DlcRequiredLoader.load(root).get(0).getHistoryKey());
        install();
        JsonObject after = history("required");
        assertEquals(before.getAsJsonObject("resources").keySet(), after.getAsJsonObject("resources").keySet());
        assertEquals(2, after.getAsJsonObject("backups").size());
        assertEquals(1, after.getAsJsonObject("resources").getAsJsonObject(key).getAsJsonObject("targets").size());
        assertEquals("required-second", Files.readString(game.resolve("mods/required.jar")));
        DlcStateConfig.load();
        assertTrue(DlcStateConfig.getState(dlcId).enabled);
        assertFalse(Files.readString(root.resolve("tmp/state.dc")).contains(workspace.toString()));
        Files.delete(root.resolve("required/required.dc"));
        install();
        assertEquals("required-original", Files.readString(game.resolve("mods/required.jar")));
        assertFalse(Files.exists(root.resolve("downloaded/required/required.jar")));
        item = items().get(0);
        assertEquals(dlcId, item.getId());
        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);
        assertEquals("dlc-first", Files.readString(game.resolve("mods/main.jar")));
        for (String kind : List.of("required", "dlc")) {
            JsonObject data = history(kind);
            for (var value : data.getAsJsonObject("backups").asMap().values()) {
                JsonObject backup = value.getAsJsonObject();
                assertTrue(backup.get("old").getAsBoolean());
                assertTrue(Files.exists(root.resolve(backup.get("path").getAsString())));
            }
        }
    }

    @Test void historyStoresExternalSourcesAndTargetsUsingParentRelativePaths() throws Exception {
        Path workspace = game;
        game = workspace.resolve("instance");
        setup();
        root = workspace.resolve("external/DLC");
        Path config = write(root.resolve("required/main.dc"), "config");
        Path source = write(root.resolve("downloaded/required/main.jar"), "installed");
        Path target = write(workspace.resolve("shared/main.jar"), "original");
        String key = DlcResourceHistory.key(DlcResourceHistory.relativePath(config), "main");
        DlcResourceHistory.prepare(root, "required", key, "main", config, source, target, true);
        DlcResourceHistory.replace(source, target);
        DlcResourceHistory.complete(root, "required", key);
        JsonObject externalHistory = DlcDcConfigIO.read(root.resolve("tmp/required/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        JsonObject record = externalHistory.getAsJsonObject("resources").getAsJsonObject(key);
        assertEquals("../external/DLC/required/main.dc", record.get("config").getAsString());
        assertEquals("../external/DLC/downloaded/required/main.jar", record.get("source").getAsString());
        assertEquals("../shared/main.jar", record.getAsJsonObject("targets").asMap().values().iterator().next().getAsJsonObject().get("path").getAsString());
        Files.delete(config);
        DlcResourceHistory.reconcileRequired(root, java.util.Set.of());
        assertEquals("original", Files.readString(target));
        assertFalse(Files.exists(source));
        JsonObject finalHistory = DlcDcConfigIO.read(root.resolve("tmp/required/history.dc"), DlcDcConfigIO.Layout.GENERIC);
        assertFalse(finalHistory.toString().contains(workspace.toString()));
    }
}
