package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcServerListTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setPlatform() {
        PlatformServices.setProvider(new TestPlatformProvider(tempDir.resolve("game"), tempDir.resolve("config")));
        DlcServerList.setEntries(List.of());
        DlcServerList.setLastSelectedKey(null);
    }

    @AfterEach
    void clearPlatform() {
        DlcServerList.setEntries(List.of());
        DlcServerList.setLastSelectedKey(null);
        PlatformServices.setProvider(null);
    }

    @Test
    void saveAndLoadRoundTripsServerEntriesAndLastSelectedKey() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.name = "Main";
        entry.host = "127.0.0.1";
        entry.port = 25565;
        entry.password = "secret";
        entry.useTcp = false;
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
        assertEquals(false, loaded.useTcp);
        assertEquals("rcon", loaded.rconPassword);
        assertTrue(Files.exists(tempDir.resolve("config").resolve("dlc_manager").resolve("servers.dc")));
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
