package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.sync.*;
import com.cozooo.dlc_manager.sync.DlcSyncModel.*;
import com.cozooo.dlc_manager.core.*;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcSyncAdapterTest {
    @TempDir Path game;
    Path root;
    @BeforeEach void setup() throws Exception {
        root = game.resolve("DLC"); Files.createDirectories(root.resolve("required")); Files.createDirectories(root.resolve("downloaded/required"));
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config"))); DlcConfig.setDlcRoot("DLC"); DlcStateConfig.reloadFromDisk();
        Files.writeString(root.resolve("required/main.dc"), "identifier='main'\nfile_name='main.txt'\nappliedTarget='config/main.txt'\n");
        Files.writeString(root.resolve("downloaded/required/main.txt"), "old resource");
        Files.createDirectories(game.resolve("config")); Files.writeString(game.resolve("config/main.txt"), "original user file");
        Files.writeString(game.resolve("config/unrelated.txt"), "keep");
    }
    @AfterEach void reset() { com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.clearAll(); DlcRuntimeState.clearOverrides(); PlatformServices.setProvider(null); DlcConfig.setDlcRoot("DLC"); }
    private void start(DlcSyncEndpoint endpoint) throws Exception {
        JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(endpoint, begin);
        JsonObject row = DlcSyncClient.request("item"); row.addProperty("key", "0:required/main.dc"); DlcSyncClient.checked(endpoint, row);
    }
    private void upload(DlcSyncEndpoint endpoint, String value) throws Exception { upload(endpoint, "0:downloaded/required/main.txt", value); }
    private void upload(DlcSyncEndpoint endpoint, String id, String value) throws Exception {
        Path bytes = Files.writeString(game.resolve("incoming.txt"), value);
        JsonObject write = DlcSyncClient.request("write"); write.add("file", DlcSyncEndpoint.JSON.toJsonTree(new File(id, Files.size(bytes), DlcFileMatch.fullHash(bytes))));
        write.addProperty("hash", DlcFileMatch.fullHash(bytes)); DlcSyncClient.checked(endpoint, write);
        JsonObject chunk = DlcSyncClient.request("write_chunk"); chunk.addProperty("data", Base64.getEncoder().encodeToString(Files.readAllBytes(bytes))); DlcSyncClient.checked(endpoint, chunk);
        DlcSyncClient.checked(endpoint, DlcSyncClient.request("write_end"));
    }
    @Test void manifestIncludesLocalizedNamesForRemoteAndRequiredDlc() throws Exception {
        Files.writeString(root.resolve("required/main.dc"), "identifier='main'\nfile_name='main.txt'\nappliedTarget='config/main.txt'\n[DisplayName]\nzh_CN='必要资源'\nen_US='Required resource'\n[tip]\nzh_CN='必要资源说明'\n");
        Files.createDirectories(root.resolve("remote"));
        Files.writeString(root.resolve("remote/main.dc"), "identifier='remote-main'\ntype='mod'\nfile_name='main.jar'\n[DisplayName]\nzh_CN='远程内容'\nen_US='Remote content'\n[tip]\nen_US='Remote description'\n");
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            Map<String, Item> items = DlcSyncModel.index(DlcSyncClient.manifest(endpoint));
            assertEquals("必要资源", DlcSyncModel.displayName(items.get("0:required/main.dc"), "zh_cn"));
            assertEquals("Remote content", DlcSyncModel.displayName(items.get("0:remote/main.dc"), "en_us"));
            assertEquals("远程内容", items.get("0:remote/main.dc").displayNames().get("zh_cn"));
            assertEquals("必要资源说明", DlcSyncModel.tooltip(items.get("0:required/main.dc"), "zh_cn"));
            assertEquals("Remote description", DlcSyncModel.tooltip(items.get("0:remote/main.dc"), "en_us"));
        }
    }

    @Test void manifestAcceptsCanonicalBaseOnlyVersionFile() throws Exception {
        Path version = game.resolve("config/dlc_manager/version.dc");
        Files.createDirectories(version.getParent());
        Files.writeString(version, "version = \"2.0.0\"\n");

        DlcSyncAdapter adapter = new DlcSyncAdapter();
        Item item = adapter.manifest().stream()
            .filter(value -> value.key().equals("manager:version.dc"))
            .findFirst()
            .orElseThrow();

        assertEquals("2.0.0", item.update().baseOld());
        assertEquals("", item.update().dlcOld());
    }

    @Test void manifestMigratesLegacyVersionFileToManagerConfig() throws Exception {
        Path legacy = root.resolve("info/version.dc");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "base = \"2.0.0\"\n");

        Item item = new DlcSyncAdapter().manifest().stream()
            .filter(value -> value.key().equals("manager:version.dc"))
            .findFirst()
            .orElseThrow();

        assertEquals("2.0.0", item.update().baseOld());
        assertTrue(Files.exists(game.resolve("config/dlc_manager/version.dc")));
        assertFalse(Files.exists(legacy));
    }
    @Test void usesCurrentResourceDirectoryAndRestoresAppliedFilesAfterCompletedItem() throws Exception {
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        assertEquals("0:downloaded/required/main.txt", adapter.manifest().get(0).resources().get(0).id());
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
            start(endpoint); upload(endpoint, "new resource"); DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply"));
            assertEquals("new resource", Files.readString(game.resolve("config/main.txt")));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            assertEquals("original user file", Files.readString(game.resolve("config/main.txt")));
            assertEquals("old resource", Files.readString(root.resolve("downloaded/required/main.txt")));
            assertEquals("keep", Files.readString(game.resolve("config/unrelated.txt")));
        }
        try (var paths = Files.walk(root.resolve("tmp/sync"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("recovery.dc")).toList()) {
                JsonObject log = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
                for (var value : log.getAsJsonArray("entries")) assertFalse(Path.of(value.getAsJsonObject().get("path").getAsString()).isAbsolute());
            }
        }
    }
    @Test void disconnectKeepsChangesAndSuccessfulCommitIsNotReplayedByRecovery() throws Exception {
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) { start(endpoint); upload(endpoint, "uncommitted"); }
        new DlcSyncAdapter().recover();
        assertEquals("uncommitted", Files.readString(root.resolve("downloaded/required/main.txt")));
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            start(endpoint); upload(endpoint, "committed"); DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply"));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("commit"));
        }
        new DlcSyncAdapter().recover();
        assertEquals("committed", Files.readString(root.resolve("downloaded/required/main.txt")));
        assertEquals("committed", Files.readString(game.resolve("config/main.txt")));
    }
    @Test void retainedAppliedFilesCanBeRestoredAfterClosingAndReopeningTheEndpoint() throws Exception {
        String operation = UUID.randomUUID().toString();
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", operation); DlcSyncClient.checked(endpoint, begin);
            JsonObject row = DlcSyncClient.request("item"); row.addProperty("key", "0:required/main.dc"); DlcSyncClient.checked(endpoint, row);
            upload(endpoint, "retained"); DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply"));
            endpoint.disconnectCurrent();
            assertThrows(java.io.IOException.class, () -> DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply")));
        }
        new DlcSyncAdapter().recover();
        assertEquals("retained", Files.readString(root.resolve("downloaded/required/main.txt")));
        assertEquals("retained", Files.readString(game.resolve("config/main.txt")));
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            JsonObject restore = DlcSyncClient.request("recover_operation"); restore.addProperty("operation", operation); DlcSyncClient.checked(endpoint, restore);
            DlcSyncClient.checked(endpoint, restore);
        }
        assertEquals("old resource", Files.readString(root.resolve("downloaded/required/main.txt")));
        assertEquals("original user file", Files.readString(game.resolve("config/main.txt")));
        assertEquals("keep", Files.readString(game.resolve("config/unrelated.txt")));
    }
    @Test void startingAnotherSyncDoesNotAutomaticallyRestoreAnInterruptedOperation() throws Exception {
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        String interrupted = UUID.randomUUID().toString();
        Path resource = root.resolve("downloaded/required/main.txt");
        try (Journal journal = adapter.journal(adapter.workDirectory().resolve(interrupted).resolve("00000000-item/journal"))) {
            journal.capture(resource); Files.writeString(resource, "partial result");
        }
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(endpoint, begin);
            assertEquals("partial result", Files.readString(resource));
        }
        new DlcSyncAdapter().recoverOperation(interrupted);
        assertEquals("old resource", Files.readString(resource));
    }
    @Test void remoteReapplicationAndFilenameChangeUseApplicationFlowAndRollbackConflicts() throws Exception {
        Path config = root.resolve("remote/main.dc"); Files.createDirectories(config.getParent());
        String original = "identifier='remote-main'\ntype='mod'\nfile_name='old.jar'\n";
        Files.writeString(config, original);
        var remote = com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.loadSingle(config, root);
        Path old = remote.resolveTargetPath("old.jar"); Files.createDirectories(old.getParent()); Files.writeString(old, "old mod");
        DlcItem initial = new DlcScanner().scan(false).values().stream().flatMap(List::stream).filter(item -> old.equals(item.getSourcePath())).findFirst().orElseThrow();
        initial.setEnabled(true); DlcApplier.applySynced(initial);
        Files.writeString(game.resolve("mods/conflict.jar"), "unrelated conflicting mod");
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(endpoint, begin);
            JsonObject row = DlcSyncClient.request("item"); row.addProperty("key", "0:remote/main.dc"); DlcSyncClient.checked(endpoint, row);
            upload(endpoint, "0:remote/main.dc", "identifier='remote-main'\ntype='mod'\nfile_name='new.jar'\nconflicts=['mods/conflict.jar']\n");
            Path next = com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.loadSingle(config, root).resolveTargetPath("new.jar");
            String id = "0:" + root.relativize(next).toString().replace('\\', '/');
            upload(endpoint, id, "new mod payload");
            JsonObject apply = DlcSyncClient.request("apply"); apply.addProperty("enabled", true); DlcSyncClient.checked(endpoint, apply);
            assertFalse(Files.exists(game.resolve("mods/old.jar")));
            assertEquals("new mod payload", Files.readString(game.resolve("mods/new.jar")));
            assertFalse(Files.exists(game.resolve("mods/conflict.jar")));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            assertEquals(original, Files.readString(config));
            assertEquals("old mod", Files.readString(game.resolve("mods/old.jar")));
            assertEquals("unrelated conflicting mod", Files.readString(game.resolve("mods/conflict.jar")));
            assertFalse(Files.exists(game.resolve("mods/new.jar")));
            assertFalse(Files.exists(next));
            assertTrue(DlcStateConfig.getState(initial.getId()).enabled);
            assertEquals(old, DlcRuntimeState.getItemByIdentifier("remote-main").getSourcePath());
            assertTrue(DlcRuntimeState.getItemByIdentifier("remote-main").isEnabled());
        }
    }
    @Test void explicitRecoveryCanUndoAnAcknowledgmentLostAfterCommit() throws Exception {
        String operation = UUID.randomUUID().toString();
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", operation); DlcSyncClient.checked(endpoint, begin);
            JsonObject row = DlcSyncClient.request("item"); row.addProperty("key", "0:required/main.dc"); DlcSyncClient.checked(endpoint, row);
            upload(endpoint, "new resource"); DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply"));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("commit"));
        }
        new DlcSyncAdapter().recoverOperation(operation);
        assertEquals("old resource", Files.readString(root.resolve("downloaded/required/main.txt")));
        assertEquals("original user file", Files.readString(game.resolve("config/main.txt")));
    }
    @Test void serverDownloadPreparationAndAppliedResourcesAreRevertedTogether() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        byte[] content = "downloaded resource".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/main.txt", exchange -> {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(content.length));
            if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(200, -1);
            else { exchange.sendResponseHeaders(200, content.length); exchange.getResponseBody().write(content); }
            exchange.close();
        });
        server.start();
        String mode = DlcConfig.getDownloadMirrorMode(); int retries = DlcConfig.getMaxRetryCount();
        try {
            DlcConfig.setDownloadMirrorMode("direct"); DlcConfig.setMaxRetryCount(1);
            String config = "# keep comment\nidentifier='main'\nfile_name='main.txt'\nappliedTarget='config/main.txt'\nunknown='keep'\ndownloadPriority='directurl'\ndirectUrl='http://127.0.0.1:" + server.getAddress().getPort() + "/main.txt'\n";
            Files.writeString(root.resolve("required/main.dc"), config);
            try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
                start(endpoint);
                JsonObject prepare = DlcSyncClient.request("prepare"); prepare.addProperty("force", true); DlcSyncClient.checked(endpoint, prepare);
                assertEquals("downloaded resource", Files.readString(root.resolve("downloaded/required/main.txt")));
                DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply"));
                assertEquals("downloaded resource", Files.readString(game.resolve("config/main.txt")));
                DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            }
            assertEquals(config, Files.readString(root.resolve("required/main.dc")));
            assertEquals("old resource", Files.readString(root.resolve("downloaded/required/main.txt")));
            assertEquals("original user file", Files.readString(game.resolve("config/main.txt")));
            assertFalse(Files.exists(root.resolve("downloaded/required/main.txt.part")));
        } finally { server.stop(0); DlcConfig.setDownloadMirrorMode(mode); DlcConfig.setMaxRetryCount(retries); }
    }
    @Test void directoryTransferVerifiesBeforeReplacingAndRestoresWholeTree() throws Exception {
        Path config = root.resolve("remote/folder.dc"); Files.createDirectories(config.getParent());
        Files.writeString(config, "identifier='folder'\ntype='resourcepack'\nfile_name='folder'\n");
        var remote = com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.loadSingle(config, root);
        Path target = remote.resolveTargetPath("folder"); Files.createDirectories(target);
        Files.writeString(target.resolve("old.txt"), "original");
        Path source = game.resolve("source-folder"), archive = game.resolve("folder.zip");
        Files.createDirectories(source.resolve("nested/empty")); Files.writeString(source.resolve("nested/new.txt"), "new resource");
        DlcSyncArchive.pack(source, archive, () -> false);
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        String key = "0:remote/folder.dc", id = "0:" + root.relativize(target).toString().replace('\\', '/');
        assertEquals(DlcFileMatch.fullHash(target), adapter.item(key).resources().get(0).hash());
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(endpoint, begin);
            JsonObject row = DlcSyncClient.request("item"); row.addProperty("key", key); DlcSyncClient.checked(endpoint, row);
            JsonObject write = DlcSyncClient.request("write"); write.add("file", DlcSyncEndpoint.JSON.toJsonTree(new File(id, Files.size(archive), DlcFileMatch.fullHash(source))));
            write.addProperty("hash", DlcFileMatch.fullHash(archive)); write.addProperty("directory", true); write.addProperty("resourceSize", DlcSyncArchive.size(source)); DlcSyncClient.checked(endpoint, write);
            JsonObject chunk = DlcSyncClient.request("write_chunk"); chunk.addProperty("data", Base64.getEncoder().encodeToString(Files.readAllBytes(archive))); DlcSyncClient.checked(endpoint, chunk);
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("write_end"));
            assertEquals(DlcFileMatch.fullHash(source), DlcFileMatch.fullHash(target)); assertFalse(Files.exists(target.resolve("old.txt")));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            assertEquals("original", Files.readString(target.resolve("old.txt"))); assertFalse(Files.exists(target.resolve("nested")));
        }
        Path malicious = game.resolve("escape.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(malicious))) {
            output.putNextEntry(new java.util.zip.ZipEntry("../escape.txt")); output.write(1); output.closeEntry();
        }
        Journal journal = adapter.journal(root.resolve("tmp/test-directory-recovery"));
        try { assertThrows(java.io.IOException.class, () -> DlcSyncArchive.install(malicious, target, 1, "invalid", journal, () -> false)); }
        finally { journal.close(); }
        assertEquals("original", Files.readString(target.resolve("old.txt"))); assertFalse(Files.exists(target.getParent().resolve("escape.txt")));
    }
    @Test void invalidConfigurationsAreReportedAndLegacyCacheUsesCanonicalResourceId() throws Exception {
        Files.move(root.resolve("downloaded/required/main.txt"), root.resolve("required/main.txt"));
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        File resource = adapter.manifest().get(0).resources().get(0);
        assertEquals("0:downloaded/required/main.txt", resource.id());
        assertEquals(root.resolve("required/main.txt"), adapter.resolve(resource.id()));
        Files.writeString(root.resolve("required/broken.dc"), "identifier=[\n");
        assertThrows(java.io.IOException.class, adapter::manifest);
    }
    @Test void requiredZipInstallationAndHistoryAreRestoredOnRollback() throws Exception {
        Path zip = root.resolve("downloaded/required/main.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new java.util.zip.ZipEntry("main.txt")); output.write("new extracted resource".getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("extra.txt")); output.write("extra".getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
        }
        Files.writeString(root.resolve("required/main.dc"), "identifier='main'\nfile_name='main.zip'\nunzip=true\nappliedTarget='config/'\n");
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            start(endpoint); DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply"));
            assertEquals("new extracted resource", Files.readString(game.resolve("config/main.txt"))); assertTrue(Files.exists(game.resolve("config/extra.txt")));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
        }
        assertEquals("original user file", Files.readString(game.resolve("config/main.txt"))); assertFalse(Files.exists(game.resolve("config/extra.txt")));
        assertFalse(Files.exists(root.resolve("tmp/required/history.dc")));
    }
    private Path updateZip(String name, String platform, String old, String next, String baseNew) throws Exception {
        Path zip = root.resolve("update").resolve(name); Files.createDirectories(zip.getParent());
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new java.util.zip.ZipEntry("update.dc"));
            output.write(("[version]\nplatform='" + platform + "'\nbase='1.0.0'\ndlc='" + old + "'\n" + (next == null ? "" : "dlc_new='" + next + "'\n") + (baseNew == null ? "" : "base_new='" + baseNew + "'\n") + "[file]\nadd=['mods/new.jar']\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry(); output.putNextEntry(new java.util.zip.ZipEntry("mods/new.jar")); output.write("new mod".getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
        }
        return zip;
    }
    private void installedVersion() throws Exception {
        Path version = game.resolve("config/dlc_manager/version.dc");
        Files.createDirectories(version.getParent());
        Files.writeString(version, "base='1.0.0'\ndlc='1.0.0'\n");
    }
    private void beginItem(DlcSyncEndpoint endpoint, String key) throws Exception {
        JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(endpoint, begin);
        JsonObject row = DlcSyncClient.request("item"); row.addProperty("key", key); DlcSyncClient.checked(endpoint, row);
    }
    private void validate(DlcSyncEndpoint endpoint, Item item) throws Exception {
        JsonObject request = DlcSyncClient.request("validate"); request.add("item", DlcSyncEndpoint.JSON.toJsonTree(item)); DlcSyncClient.checked(endpoint, request);
    }
    private void uploadBytes(DlcSyncEndpoint endpoint, String key, byte[] bytes) throws Exception {
        Path incoming = Files.write(game.resolve("incoming.zip"), bytes);
        JsonObject write = DlcSyncClient.request("write"); write.add("file", DlcSyncEndpoint.JSON.toJsonTree(new File(key, bytes.length, DlcFileMatch.fullHash(incoming))));
        write.addProperty("hash", DlcFileMatch.fullHash(incoming)); DlcSyncClient.checked(endpoint, write);
        JsonObject chunk = DlcSyncClient.request("write_chunk"); chunk.addProperty("data", Base64.getEncoder().encodeToString(bytes)); DlcSyncClient.checked(endpoint, chunk);
        DlcSyncClient.checked(endpoint, DlcSyncClient.request("write_end"));
    }
    @Test void discoversPresetsManagerConfigurationsAndUpdateMetadataWithoutExtractingOrCreatingVersion() throws Exception {
        Path manager = game.resolve("config/dlc_manager"); Files.createDirectories(manager);
        Files.writeString(manager.resolve("config.dc"), "# keep\n[ui]\nshowDependencyReminder=true\nunknown='keep'\n");
        Files.writeString(manager.resolve("config-server.dc"), "password='example'\n");
        Files.createDirectories(root.resolve("preset/nested"));
        Files.writeString(root.resolve("preset/nested/main.dc"), "identifier='preset-main'\n[DisplayName]\nzh_CN='测试预设'\n");
        Path zip = updateZip("next.zip", "universal", "1.0.0", "1.1.0", null);
        updateZip("base.zip", "universal", "1.0.0", null, "2.0.0");
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        Map<String, Item> before = DlcSyncModel.index(adapter.manifest());
        assertEquals("测试预设", DlcSyncModel.displayName(before.get("0:preset/nested/main.dc"), "zh_cn"));
        assertTrue(before.get("manager:config.dc").displayNames().isEmpty());
        assertFalse(before.containsKey("manager:config-server.dc"));
        assertEquals("1.0.0", before.get("0:update/base.zip").update().dlcNew());
        assertEquals("2.0.0", before.get("0:update/base.zip").update().baseNew());
        assertEquals(Files.size(zip), before.get("0:update/next.zip").config().size());
        assertFalse(Files.exists(game.resolve("config/dlc_manager/version.dc")));
        assertFalse(Files.exists(root.resolve("tmp/_staging")));
        installedVersion();
        assertTrue(DlcSyncModel.versionState(DlcSyncModel.index(adapter.manifest()).get("manager:version.dc")));
        assertThrows(java.io.IOException.class, () -> adapter.validateKey("0:info/version.dc"));
        assertThrows(java.io.IOException.class, () -> adapter.resolve("manager:../other.dc"));
        assertThrows(java.io.IOException.class, () -> adapter.resolve("manager:state.dc"));
        assertThrows(java.io.IOException.class, () -> adapter.validateKey("0:update/nested/next.zip"));
    }
    @Test void synchronizingHotUpdateOnlyStoresArchiveAndCanRollBackOverwrite() throws Exception {
        installedVersion();
        String key = "0:update/next.zip", version = Files.readString(game.resolve("config/dlc_manager/version.dc"));
        Path zip = updateZip("next.zip", "universal", "1.0.0", "1.1.0", null);
        byte[] original = Files.readAllBytes(zip);
        DlcSyncAdapter adapter = new DlcSyncAdapter(); Item expected = adapter.item(key);
        byte[] replacement = Files.readAllBytes(updateZip("payload.zip", "universal", "1.0.0", "1.2.0", null));
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
            beginItem(endpoint, key); validate(endpoint, expected); uploadBytes(endpoint, key, replacement);
            assertFalse(DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply")).get("requiresRestart").getAsBoolean());
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done"));
            assertArrayEquals(replacement, Files.readAllBytes(zip));
            assertEquals(version, Files.readString(game.resolve("config/dlc_manager/version.dc")));
            assertFalse(Files.exists(game.resolve("mods/new.jar")));
            assertFalse(Files.exists(root.resolve("tmp/_staging")));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
        }
        assertArrayEquals(original, Files.readAllBytes(zip));
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            beginItem(endpoint, key); validate(endpoint, expected); uploadBytes(endpoint, key, replacement);
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("commit"));
        }
        new DlcSyncAdapter().recover();
        assertArrayEquals(replacement, Files.readAllBytes(zip)); assertEquals(version, Files.readString(game.resolve("config/dlc_manager/version.dc"))); assertFalse(Files.exists(game.resolve("mods/new.jar")));
    }
    @Test void rejectsUpdateWithoutItsPrerequisiteAndAcceptsExistingTargetPrerequisite() throws Exception {
        installedVersion();
        Path incoming = updateZip("later.zip", "universal", "1.1.0", "1.2.0", null);
        DlcSyncAdapter adapter = new DlcSyncAdapter(); Item item = adapter.item("0:update/later.zip");
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
            beginItem(endpoint, item.key());
            assertThrows(java.io.IOException.class, () -> validate(endpoint, item));
            updateZip("first.zip", "universal", "1.0.0", "1.1.0", null);
            validate(endpoint, item);
            assertEquals(Files.size(incoming), item.config().size());
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
        }
    }
    @Test void preservesManagerAndPresetTextAndRollsBackTheirOverwrites() throws Exception {
        for (String key : List.of("0:preset/main.dc")) {
            DlcSyncAdapter adapter = new DlcSyncAdapter(); Path path = adapter.resolve(key); Files.createDirectories(path.getParent());
            String original = "# original\nidentifier='before'\nunknown='retain'\n", replacement = "# incoming\nidentifier='after'\nunknown='preserve'\n";
            Files.writeString(path, original);
            try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
                beginItem(endpoint, key); upload(endpoint, key, replacement);
                assertTrue(DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply")).get("requiresRestart").getAsBoolean());
                DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done"));
                assertEquals(replacement, Files.readString(path));
                DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            }
            assertEquals(original, Files.readString(path));
        }
    }
    private Path presetWithMod() throws Exception {
        Files.createDirectories(root.resolve("remote")); Files.writeString(root.resolve("remote/mod.dc"), "identifier='preset-mod'\ntype='mod'\nfile_name='preset.jar'\n");
        var remote = com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.loadSingle(root.resolve("remote/mod.dc"), root);
        Path payload = remote.resolveTargetPath("preset.jar"); Files.createDirectories(payload.getParent()); Files.writeString(payload, "preset mod");
        Files.createDirectories(root.resolve("preset"));
        Files.writeString(root.resolve("preset/main.dc"), "identifier='preset-main'\nenable=['preset-mod']\nconflicts=['config/unrelated.txt']\n");
        com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.clearAll(); DlcRuntimeState.clearOverrides();
        return payload;
    }
    @Test void presetManifestIncludesEnabledStateForStateOnlyComparison() throws Exception {
        presetWithMod();
        var adapter = new DlcSyncAdapter();
        Item disabled = adapter.item("0:preset/main.dc");
        com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.savePresetStatus(Map.of("preset-main", com.cozooo.dlc_manager.client.preset.DlcPresetStatus.ON));
        Item enabled = adapter.item("0:preset/main.dc");
        assertEquals(false, disabled.enabled()); assertEquals(true, enabled.enabled());
        assertTrue(DlcSyncModel.plan(List.of(enabled), List.of(disabled), Direction.PUSH, false, Mode.CONFIG).isEmpty());
        Row row = DlcSyncModel.plan(List.of(enabled), List.of(disabled), Direction.PUSH, true, Mode.CONFIG).get(0);
        assertEquals("state_changed", DlcSyncModel.status(row, Mode.CONFIG));
    }
    @Test void presetMetadataUsesTheSameIdentifierPrecedenceAsThePresetLoader() throws Exception {
        presetWithMod();
        Files.writeString(root.resolve("preset/main.dc"), "identifier='legacy'\n[basic]\nidentifier='  preset-main  '\n");
        com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.savePresetStatus(Map.of("preset-main", com.cozooo.dlc_manager.client.preset.DlcPresetStatus.ON));
        Item item = new DlcSyncAdapter().item("0:preset/main.dc");
        assertEquals("preset-main", item.identifier()); assertEquals(true, item.enabled());
    }
    @Test void presetSyncRunsEnableAndConflictOperationsAndRollbackRestoresEverything() throws Exception {
        presetWithMod();
        Path state = com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.getStatePath();
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            beginItem(endpoint, "0:preset/main.dc");
            JsonObject apply = DlcSyncClient.request("apply"); apply.addProperty("enabled", true); DlcSyncClient.checked(endpoint, apply);
            assertEquals("preset mod", Files.readString(game.resolve("mods/preset.jar")));
            assertFalse(Files.exists(game.resolve("config/unrelated.txt")));
            assertTrue(com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.loadPresetStatus().get("preset-main").isEnabled());
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
        }
        assertFalse(Files.exists(game.resolve("mods/preset.jar")));
        assertEquals("keep", Files.readString(game.resolve("config/unrelated.txt")));
        assertFalse(Files.exists(state));
        assertFalse(com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.isPresetEnabled("preset-main"));
    }
    @Test void presetRecoveryAfterLostCommitAcknowledgmentRestoresFilesAndRuntime() throws Exception {
        presetWithMod(); String operation = UUID.randomUUID().toString();
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", operation); DlcSyncClient.checked(endpoint, begin);
            JsonObject item = DlcSyncClient.request("item"); item.addProperty("key", "0:preset/main.dc"); DlcSyncClient.checked(endpoint, item);
            JsonObject apply = DlcSyncClient.request("apply"); apply.addProperty("enabled", true); DlcSyncClient.checked(endpoint, apply);
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("commit"));
        }
        new DlcSyncAdapter().recoverOperation(operation);
        assertFalse(Files.exists(game.resolve("mods/preset.jar")));
        assertEquals("keep", Files.readString(game.resolve("config/unrelated.txt")));
        assertFalse(com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.isPresetEnabled("preset-main"));
        assertFalse(com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.hasPendingFileConflictChanges());
    }
    @Test void enabledPresetMayBePartialOnTheDestinationWithoutFailingSynchronization() throws Exception {
        presetWithMod(); Files.writeString(root.resolve("preset/main.dc"), "identifier='preset-main'\nenable=['preset-mod','missing-dlc']\n");
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(new DlcSyncAdapter())) {
            beginItem(endpoint, "0:preset/main.dc");
            JsonObject apply = DlcSyncClient.request("apply"); apply.addProperty("enabled", true);
            DlcSyncClient.checked(endpoint, apply);
            assertEquals(com.cozooo.dlc_manager.client.preset.DlcPresetStatus.PART, com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.loadPresetStatus().get("preset-main"));
            assertEquals("preset mod", Files.readString(game.resolve("mods/preset.jar")));
            Item partial = new DlcSyncAdapter().item("0:preset/main.dc");
            assertEquals(true, partial.enabled());
            com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.savePresetStatus(Map.of("preset-main", com.cozooo.dlc_manager.client.preset.DlcPresetStatus.ON));
            Item full = new DlcSyncAdapter().item("0:preset/main.dc");
            assertTrue(DlcSyncModel.plan(List.of(full), List.of(partial), Direction.PUSH, true, Mode.CONFIG).isEmpty());
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
        }
        assertFalse(com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.isPresetEnabled("preset-main"));
        assertTrue(com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.loadPresetStatus().isEmpty());
        assertFalse(Files.exists(game.resolve("mods/preset.jar")));
    }
    @Test void managerSynchronizationMergesOnlySharedFieldsAndRestoresTheOriginalFile() throws Exception {
        Path path = game.resolve("config/dlc_manager/config.dc"); Files.createDirectories(path.getParent());
        String original = "# local comment\n[downloads]\nmaxConcurrentDownloads=7\n[api]\napiToken='secret'\n[server]\nserverPassword='private'\n[ui]\nshowDependencyReminder=true\n";
        Files.writeString(path, original);
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        assertThrows(java.io.IOException.class, () -> adapter.resolve("manager:config-server.dc"));
        assertFalse(DlcSyncEndpoint.JSON.toJson(adapter.item("manager:config.dc")).contains("secret"));
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(adapter)) {
            beginItem(endpoint, "manager:config.dc");
            JsonObject values = DlcConfig.buildSyncConfigJson(null); values.getAsJsonObject("downloads").addProperty("maxConcurrentDownloads", 3);
            JsonObject settings = DlcSyncClient.request("settings"); settings.add("settings", values); DlcSyncClient.checked(endpoint, settings);
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done"));
            JsonObject after = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
            assertEquals(3, after.getAsJsonObject("downloads").get("maxConcurrentDownloads").getAsInt());
            assertEquals("secret", after.getAsJsonObject("api").get("apiToken").getAsString());
            assertEquals("private", after.getAsJsonObject("server").get("serverPassword").getAsString());
            assertTrue(after.getAsJsonObject("ui").get("showDependencyReminder").getAsBoolean());
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            assertEquals(original, Files.readString(path));
        }
    }
    @Test void rejectsUnmanagedPathsAndSymbolicLinks() throws Exception {
        DlcSyncAdapter adapter = new DlcSyncAdapter();
        assertThrows(java.io.IOException.class, () -> adapter.validateKey("0:mods/other.jar"));
        assertThrows(java.io.IOException.class, () -> adapter.resolve("0:../mods/other.jar"));
        assertThrows(java.io.IOException.class, () -> adapter.validateKey("0:remote/../tmp/state.dc"));
        Files.createSymbolicLink(root.resolve("required/link.dc"), game.resolve("config/unrelated.txt"));
        assertThrows(java.io.IOException.class, () -> adapter.validateKey("0:required/link.dc"));
    }
}
