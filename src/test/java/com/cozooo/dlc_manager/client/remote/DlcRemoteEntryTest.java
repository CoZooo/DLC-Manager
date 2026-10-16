package com.cozooo.dlc_manager.client.remote;

import org.junit.jupiter.api.Test;

import com.cozooo.dlc_manager.core.DlcType;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcRemoteEntryTest {
    @Test
    void normalizesDirectUrlsAndKeepsFirstAsPrimaryUrl() {
        DlcRemoteEntry entry = new DlcRemoteEntry(null, null, Path.of("mods"), "file.jar");

        entry.setDirectUrls(List.of(" https://example.com/a.jar ", "", "https://example.com/b.jar"));

        assertEquals("https://example.com/a.jar", entry.getDirectUrl());
        assertEquals(List.of("https://example.com/a.jar", "https://example.com/b.jar"), entry.getDirectUrls());
    }

    @Test
    void normalizesPriorityAliasesAndAddsFallbackSources() {
        DlcRemoteEntry entry = new DlcRemoteEntry(null, null, Path.of("mods"), "file.jar");

        entry.setDownloadPriority("direct-url, MODRINTH, direct, unknown");

        assertEquals("directurl,modrinth,curseforge", entry.getDownloadPriority());
        assertEquals(List.of("directurl", "modrinth", "curseforge"), entry.getDownloadPriorityList());
        assertEquals(true, entry.isDirectUrlPriority());
        assertEquals(false, entry.isCurseForgePriority());
    }

    @Test
    void resolvesTargetPathWithSanitizedFileNameAndStripsQuery() {
        DlcRemoteEntry entry = new DlcRemoteEntry(null, Path.of("DLC"), Path.of("DLC", "mods"), "bad:name.jar?download=1");

        assertEquals(Path.of("DLC/downloaded/DLCs/mods/bad_name.jar").toAbsolutePath().normalize(), entry.getTargetPath());
        assertEquals(Path.of("DLC/downloaded/DLCs/mods/a_b.jar").toAbsolutePath().normalize(), entry.resolveTargetPath("a:b.jar?x=1"));
        assertNull(entry.resolveTargetPath(" "));
    }

    @Test
    void dlcPackDownloadsUseTheNewCacheDirectory() {
        DlcRemoteEntry entry = new DlcRemoteEntry(null, Path.of("DLC"), Path.of("DLC"), "pack:one.zip?download=1");
        entry.setType(DlcType.DLC_PACK);

        assertEquals(Path.of("DLC/downloaded/DLCs/pack_one.zip").toAbsolutePath().normalize(), entry.getTargetPath());
        assertEquals(Path.of("DLC/downloaded/DLCs/pack_two.zip").toAbsolutePath().normalize(), entry.resolveTargetPath("pack:two.zip"));
    }

    @Test
    void uniqueKeyPrefersVersionIdentifiersOverUrlAndPath() {
        DlcRemoteEntry entry = new DlcRemoteEntry(Path.of("remote", "entry.dc"), null, Path.of("mods"), "file.jar");
        entry.setDirectUrl("https://example.com/file.jar");
        assertEquals("url:https://example.com/file.jar", entry.getUniqueKey());

        entry.setCurseforgeProjectId("project");
        entry.setCurseforgeFileId("file");
        assertEquals("curseforge:project:file", entry.getUniqueKey());

        entry.setModrinthVersionId("version");
        assertEquals("modrinth:version", entry.getUniqueKey());
    }

    @Test
    void resolvedDependencyNamesMergeExplicitNamesAndRemoteDependencyIdentifiers() {
        DlcRemoteEntry entry = new DlcRemoteEntry(null, null, Path.of("mods"), "file.jar");
        DlcRemoteEntry dependency = new DlcRemoteEntry(null, null, Path.of("mods"), "dep.jar");
        dependency.setDlcIdentifier(" dep ");

        entry.setDependencyNames(List.of("dep", " explicit ", "", "explicit"));
        entry.setRemoteDependencies(List.of(dependency));

        assertEquals(List.of("dep", "explicit"), entry.getResolvedDependencyNames());
    }
    @Test
    void missingFileNamesCannotResolveToTheLegacyRootDirectory() {
        DlcRemoteEntry entry = new DlcRemoteEntry(null, Path.of("."), Path.of("."), null);
        assertNull(entry.resolveExistingTargetPath(null));
        assertNull(entry.resolveExistingTargetPath(" "));
    }
}
