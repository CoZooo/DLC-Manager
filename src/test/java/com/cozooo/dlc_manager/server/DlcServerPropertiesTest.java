package com.cozooo.dlc_manager.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcServerPropertiesTest {
    @TempDir
    Path tempDir;

    @Test
    void readRconInfoUsesDefaultsWhenServerPropertiesMissing() {
        DlcServerProperties.RconInfo info = DlcServerProperties.readRconInfo(tempDir);

        assertFalse(info.enabled);
        assertEquals(25575, info.port);
        assertFalse(info.passwordSet);
    }

    @Test
    void readRconInfoParsesBooleanPortAndPassword() throws Exception {
        Files.writeString(tempDir.resolve("server.properties"), """
            enable-rcon=1
            rcon.port=25580
            rcon.password=secret
            """);

        DlcServerProperties.RconInfo info = DlcServerProperties.readRconInfo(tempDir);

        assertTrue(info.enabled);
        assertEquals(25580, info.port);
        assertTrue(info.passwordSet);
    }

    @Test
    void readRconInfoClampsInvalidPortToDefault() throws Exception {
        Files.writeString(tempDir.resolve("server.properties"), """
            enable-rcon=true
            rcon.port=999999
            rcon.password=
            """);

        DlcServerProperties.RconInfo info = DlcServerProperties.readRconInfo(tempDir);

        assertTrue(info.enabled);
        assertEquals(25575, info.port);
        assertFalse(info.passwordSet);
    }
}
