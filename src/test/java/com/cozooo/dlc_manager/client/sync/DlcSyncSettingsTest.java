package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.sync.*;
import com.cozooo.dlc_manager.sync.DlcSyncModel.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class DlcSyncSettingsTest {
    @TempDir Path temp;
    private static final String KEY = "manager:config.dc";
    private static final String CLIENT = "# client comment\n[downloads]\nmaxConcurrentDownloads=3\n[ui]\nshowDependencyReminder=true\ntitleAnchor='hidden'\n[api]\napiToken='client-secret'\n[server]\nserverPassword='client-password'\n";
    private static final String SERVER = "# server comment\n[Downloads]\nmaxConcurrentDownloads = 7 # keep download comment\nunknown = 'keep me'\n[ui]\nshowDependencyReminder=false\ntitleAnchor='left'\n[api]\napiToken='server-secret'\n[server]\nserverPassword='server-password'\n[custom]\nuntouched = 'unchanged'\n";

    @Test void comparesOnlySharedValuesAndIgnoresFormattingDefaultsAndPrivateFields() throws Exception {
        Fixture client = new Fixture(temp.resolve("client"), CLIENT), server = new Fixture(temp.resolve("server"), SERVER);
        Item source = client.item(KEY), target = server.item(KEY);
        assertFalse(source.settings().has("api")); assertFalse(source.settings().has("server"));
        assertFalse(source.settings().getAsJsonObject("ui").has("showDependencyReminder"));
        assertFalse(source.settings().getAsJsonObject("ui").has("titleAnchor"));
        assertTrue(source.displayNames().isEmpty(), "Manager labels belong to the UI language resources");
        assertFalse(DlcSyncEndpoint.JSON.toJson(source).contains("client-secret"));
        assertFalse(DlcSyncEndpoint.JSON.toJson(source).contains("client-password"));
        assertEquals(1, DlcSyncModel.plan(List.of(source), List.of(target), Direction.PUSH, true, Mode.CONFIG).size());
        Files.writeString(server.config, SERVER.replace("maxConcurrentDownloads = 7", "maxConcurrentDownloads = 3"));
        assertEquals(source.settings(), server.item(KEY).settings());
        assertEquals(source.config(), server.item(KEY).config());
        assertTrue(DlcSyncModel.plan(client.manifest(), server.manifest(), Direction.PUSH, true, Mode.CONFIG).isEmpty());
        Fixture absent = new Fixture(temp.resolve("absent"), null);
        Fixture defaults = new Fixture(temp.resolve("defaults"), "[paths]\ndlcRoot='DLC'\n[ui]\nshowDependencyReminder=true\n");
        assertEquals(absent.item(KEY).config(), defaults.item(KEY).config());
        assertFalse(Files.exists(absent.config));
    }
    @Test void bothModesSynchronizeFieldsWithoutTransferringTheWholeConfiguration() throws Exception {
        for (Mode mode : Mode.values()) {
            Fixture client = new Fixture(temp.resolve("client-" + mode), CLIENT), server = new Fixture(temp.resolve("server-" + mode), SERVER);
            List<String> actions = new ArrayList<>();
            try (DlcSyncEndpoint left = new DlcSyncEndpoint(client); DlcSyncEndpoint right = new DlcSyncEndpoint(server)) {
                Peer tracked = request -> { actions.add(request.get("action").getAsString()); return right.call(request); };
                var rows = DlcSyncModel.plan(DlcSyncClient.manifest(left), DlcSyncClient.manifest(right), Direction.PUSH, true, mode);
                Result result = new DlcSyncClient(left, tracked, right).execute(rows, mode, true, (phase, name, done, total) -> {});
                assertEquals(1, result.completed()); assertTrue(result.errors().isEmpty()); assertTrue(result.requiresRestart());
                assertTrue(actions.contains("settings")); assertFalse(actions.contains("write")); assertFalse(actions.contains("read"));
            }
            JsonObject updated = DlcDcConfigIO.read(server.config, DlcDcConfigIO.Layout.GENERIC);
            assertEquals(3, DlcDcConfigIO.getObjectIgnoreCase(updated, "downloads").get("maxConcurrentDownloads").getAsInt());
            assertEquals("server-secret", updated.getAsJsonObject("api").get("apiToken").getAsString());
            assertEquals("server-password", updated.getAsJsonObject("server").get("serverPassword").getAsString());
            assertFalse(updated.getAsJsonObject("ui").get("showDependencyReminder").getAsBoolean());
            assertEquals("left", updated.getAsJsonObject("ui").get("titleAnchor").getAsString());
            String text = Files.readString(server.config);
            assertTrue(text.contains("# server comment")); assertTrue(text.contains("# keep download comment"));
            assertTrue(text.contains("unknown = 'keep me'")); assertTrue(text.contains("untouched = 'unchanged'"));
            assertEquals(CLIENT, Files.readString(client.config));
        }
    }
    @Test void existingRepositoryAliasIsReadAndUpdatedAlongWithTheCanonicalField() throws Exception {
        Fixture source = new Fixture(temp.resolve("source"), "[modpack]\nupdateGitRepo='new/repository'\n");
        Fixture target = new Fixture(temp.resolve("target"), "[modpack]\nupdateGithubRepo='old/repository'\n");
        assertEquals("old/repository", target.item(KEY).settings().getAsJsonObject("modpack").get("updateGitRepo").getAsString());
        try (DlcSyncEndpoint left = new DlcSyncEndpoint(source); DlcSyncEndpoint right = new DlcSyncEndpoint(target)) {
            var rows = DlcSyncModel.plan(DlcSyncClient.manifest(left), DlcSyncClient.manifest(right), Direction.PUSH, false, Mode.CONFIG);
            assertTrue(new DlcSyncClient(left, right, right).execute(rows, Mode.CONFIG, false, (phase, name, done, total) -> {}).errors().isEmpty());
        }
        assertEquals("new/repository", DlcDcConfigIO.read(target.config, DlcDcConfigIO.Layout.GENERIC).getAsJsonObject("modpack").get("updateGithubRepo").getAsString());
    }
    @Test void fieldUpdatesAreBackedUpAndRestoredAndRawManagerFileAccessIsRejected() throws Exception {
        Fixture target = new Fixture(temp.resolve("target"), SERVER), source = new Fixture(temp.resolve("source"), CLIENT);
        try (DlcSyncEndpoint endpoint = new DlcSyncEndpoint(target)) {
            JsonObject begin = DlcSyncClient.request("begin"); begin.addProperty("operation", UUID.randomUUID().toString()); DlcSyncClient.checked(endpoint, begin);
            JsonObject selected = DlcSyncClient.request("item"); selected.addProperty("key", KEY); DlcSyncClient.checked(endpoint, selected);
            JsonObject read = DlcSyncClient.request("read"); read.addProperty("id", KEY);
            assertThrows(IOException.class, () -> DlcSyncClient.checked(endpoint, read));
            JsonObject fields = DlcSyncClient.request("settings"); fields.add("settings", source.item(KEY).settings()); DlcSyncClient.checked(endpoint, fields);
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("apply")); DlcSyncClient.checked(endpoint, DlcSyncClient.request("item_done"));
            assertNotEquals(SERVER, Files.readString(target.config));
            DlcSyncClient.checked(endpoint, DlcSyncClient.request("rollback"));
            assertEquals(SERVER, Files.readString(target.config));
        }
    }
    private static final class Fixture implements Adapter {
        final Path root, config;
        Fixture(Path root, String text) throws IOException { this.root = root; config = root.resolve("config.dc"); Files.createDirectories(root); if (text != null) Files.writeString(config, text); }
        public Path workDirectory() { return root.resolve("work"); }
        public List<Item> manifest() throws IOException { return List.of(DlcSyncSettings.item(KEY, config)); }
        public Path resolve(String key) throws IOException { validateKey(key); return config; }
        public void validateKey(String key) throws IOException { if (!KEY.equals(key)) throw new IOException("Not a manager configuration"); }
        public void writeSettings(String key, JsonObject settings, Journal journal) throws IOException { validateKey(key); DlcSyncSettings.write(config, settings, journal); }
        public Journal journal(Path directory) {
            return new Journal() {
                final Map<Path, byte[]> originals = new LinkedHashMap<>();
                public void capture(Path path) throws IOException { if (!originals.containsKey(path)) originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null); }
                public void rollback() throws IOException { for (var entry : originals.entrySet()) { if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue()); } }
                public void commit() {}
                public void close() {}
            };
        }
        public void prepare(String key, boolean force, BooleanSupplier canceled, Progress progress) {}
        public boolean apply(String key, Boolean enabled, Journal journal, BooleanSupplier canceled) { return true; }
        public void reload() {}
        public void recover() {}
        public void recoverOperation(String operation) {}
    }
}
