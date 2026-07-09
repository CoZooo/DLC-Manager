package com.cozooo.dlc_manager.client.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcServerEntryTest {
    @Test
    void displayNameUsesNameWhenPresent() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.name = "Main Server";
        entry.host = "127.0.0.1";
        entry.port = 25565;

        assertEquals("Main Server", entry.getDisplayName());
        assertEquals("127.0.0.1:25565", entry.getKey());
    }

    @Test
    void displayNameFallsBackToHostAndPort() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.host = "example.com";
        entry.port = 25566;

        assertEquals("example.com:25566", entry.getDisplayName());
        assertEquals("example.com:25566", entry.getKey());
    }

    @Test
    void nullHostFallsBackToEmptyHostPart() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.port = 25565;

        assertEquals(":25565", entry.getDisplayName());
        assertEquals(":25565", entry.getKey());
    }
}
