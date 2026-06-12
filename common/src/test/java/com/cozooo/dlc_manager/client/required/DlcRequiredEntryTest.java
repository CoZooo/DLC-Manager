package com.cozooo.dlc_manager.client.required;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcRequiredEntryTest {
    @Test
    void derivesBaseNameAndIdentifierFromConfigFile() {
        DlcRequiredEntry entry = new DlcRequiredEntry(Path.of("required", "example.dc"), Path.of("mods"));

        assertEquals("example", entry.getBaseName());
        assertEquals("example", entry.getIdentifier());

        entry.setIdentifier("custom");
        entry.setIdentifier(" ");
        assertEquals("custom", entry.getIdentifier());
    }

    @Test
    void normalizesDirectUrlsAndRequiredFileName() {
        DlcRequiredEntry entry = new DlcRequiredEntry(Path.of("required", "example.dc"), Path.of("mods"));

        entry.setDirectUrls(List.of(" https://example.com/a.jar ", "", "https://example.com/b.jar"));
        entry.setRequiredFileName("required.jar");

        assertEquals("https://example.com/a.jar", entry.getDirectUrl());
        assertEquals(List.of("https://example.com/a.jar", "https://example.com/b.jar"), entry.getDirectUrls());
        assertEquals("required.jar", entry.getRequiredFileName());
        assertEquals("required.jar", entry.getResolvedFileName());
    }

    @Test
    void normalizesPriorityAliasesAndAddsFallbackSources() {
        DlcRequiredEntry entry = new DlcRequiredEntry(Path.of("required", "example.dc"), Path.of("mods"));

        entry.setDownloadPriority("mod-rinth, direct_url, curseforge, modrinth");

        assertEquals("modrinth,directurl,curseforge", entry.getDownloadPriority());
        assertEquals(List.of("modrinth", "directurl", "curseforge"), entry.getDownloadPriorityList());
        assertEquals(false, entry.isDirectUrlPriority());
        assertEquals(false, entry.isCurseForgePriority());
    }

    @Test
    void resolvesRequiredPathOnlyWhenFileNameIsPresent() {
        DlcRequiredEntry entry = new DlcRequiredEntry(Path.of("required", "example.dc"), Path.of("mods"));

        assertEquals(Path.of("mods", "file.jar"), entry.resolveRequiredPath("file.jar"));
        assertNull(entry.resolveRequiredPath(""));
        assertNull(new DlcRequiredEntry(Path.of("required", "example.dc"), null).resolveRequiredPath("file.jar"));
    }

    @Test
    void addDependencySkipsNullValues() {
        DlcRequiredEntry entry = new DlcRequiredEntry(Path.of("required", "example.dc"), Path.of("mods"));
        DlcRequiredEntry child = new DlcRequiredEntry(Path.of("required", "child.dc"), Path.of("mods"));
        DlcRequiredDependency dependency = new DlcRequiredDependency("id", child);

        entry.addDependency(null);
        entry.addDependency(dependency);

        assertEquals(List.of(dependency), entry.getDependencies());
        assertEquals("id", dependency.getIdentifier());
        assertEquals(child, dependency.getEntry());
    }
}
