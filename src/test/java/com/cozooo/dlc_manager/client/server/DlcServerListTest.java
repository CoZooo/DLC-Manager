package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcServerListTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setPlatform() {
        PlatformServices.setProvider(new TestPlatformProvider(tempDir.resolve("game"), tempDir.resolve("config")));
        System.setProperty("dlc_manager.user.dir", tempDir.resolve("user").toString());
        DlcConfig.loadClient();
        DlcServerList.setEntries(List.of());
        DlcServerList.setLastSelectedKey(null);
    }

    @AfterEach
    void clearPlatform() {
        DlcServerList.setEntries(List.of());
        DlcServerList.setLastSelectedKey(null);
        DlcConfig.setModpack("");
        System.clearProperty("dlc_manager.user.dir");
        PlatformServices.setProvider(null);
    }

    @Test
    void saveAndLoadRoundTripsServerEntriesAndLastSelectedKey() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.name = "Main";
        entry.host = "127.0.0.1";
        entry.port = 25565;
        entry.password = "secret";
        entry.rconPort = 25575;
        entry.rconPassword = "rcon";

        DlcServerList.setEntries(List.of(entry));
        DlcServerList.setLastSelectedKey(entry.getKey());
        DlcServerList.save();

        DlcServerList.setEntries(List.of());
        DlcServerList.setLastSelectedKey(null);
        DlcServerList.load();

        assertEquals("127.0.0.1:25565", DlcServerList.getLastSelectedKey());
        assertEquals(1, DlcServerList.getEntries().size());
        DlcServerEntry loaded = DlcServerList.getEntries().get(0);
        assertEquals("Main", loaded.name);
        assertEquals("127.0.0.1", loaded.host);
        assertEquals(25565, loaded.port);
        assertEquals("secret", loaded.password);
        assertEquals("rcon", loaded.rconPassword);
        assertTrue(Files.exists(DlcServerList.storePath()));
        assertFalse(Files.exists(tempDir.resolve("game").resolve("DLC").resolve("tmp").resolve("servers.dc")));
    }

    @Test
    void migratesLegacyServerListIntoUserStore() throws Exception {
        Path legacy = tempDir.resolve("config").resolve("dlc_manager").resolve("servers.dc");
        Files.createDirectories(legacy.getParent());
        JsonObject server = new JsonObject();
        server.addProperty("name", "Old");
        server.addProperty("host", "legacy");
        server.addProperty("port", 25565);
        JsonArray servers = new JsonArray();
        servers.add(server);
        JsonObject data = new JsonObject();
        data.add("servers", servers);
        data.addProperty("lastSelected", "legacy:25565");
        assertTrue(DlcDcConfigIO.write(legacy, data, DlcDcConfigIO.Layout.GENERIC));

        DlcServerList.load();

        assertEquals(1, DlcServerList.getEntries().size());
        assertEquals("legacy", DlcServerList.getEntries().get(0).host);
        assertEquals("legacy:25565", DlcServerList.getLastSelectedKey());
        assertTrue(Files.exists(DlcServerList.storePath()));
        assertFalse(Files.exists(legacy));
        assertFalse(Files.exists(tempDir.resolve("game").resolve("DLC").resolve("tmp").resolve("servers.dc")));
    }

    @Test
    void keepsSeparateListsPerModpackIdentity() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.host = "pack-a";
        entry.port = 25565;
        DlcConfig.setModpack("pack-a");
        DlcServerList.setEntries(List.of(entry));
        DlcServerList.save();

        DlcConfig.setModpack("pack-b");
        DlcServerList.load();
        assertTrue(DlcServerList.getEntries().isEmpty());

        DlcConfig.setModpack("pack-a");
        DlcServerList.load();
        assertEquals(1, DlcServerList.getEntries().size());
        assertEquals("pack-a", DlcServerList.getEntries().get(0).host);
    }

    @Test
    void serverListIdentityUsesModpackWithoutVersion() {
        DlcConfig.setModpack("pack-a");
        DlcServerEntry entry = new DlcServerEntry();
        entry.host = "pack-a";
        entry.port = 25565;
        DlcServerList.setEntries(List.of(entry));
        DlcServerList.save();

        JsonObject root = DlcDcConfigIO.read(DlcServerList.storePath(), DlcDcConfigIO.Layout.GENERIC);
        JsonArray packs = DlcDcConfigIO.getArrayIgnoreCase(root, "packs");
        assertEquals(1, packs.size());
        String id = DlcDcConfigIO.getStringIgnoreCase(packs.get(0).getAsJsonObject(), "id");
        assertEquals("pack:pack-a", id);
        assertFalse(id.contains("@"));
    }

    @Test
    void addRemoveAndUnmodifiableEntries() {
        DlcServerEntry first = new DlcServerEntry();
        first.host = "a";
        first.port = 1;
        DlcServerEntry second = new DlcServerEntry();
        second.host = "b";
        second.port = 2;

        DlcServerList.addEntry(null);
        DlcServerList.addEntry(first);
        DlcServerList.addEntry(second);
        DlcServerList.removeEntry("a:1");

        assertEquals(List.of(second), DlcServerList.getEntries());
        assertThrows(UnsupportedOperationException.class, () -> DlcServerList.getEntries().add(first));
    }
}
