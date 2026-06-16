package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DcConfigIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackMetadataTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsBlankTrailingSpaceAndPathLikeIdentifiers() {
        assertFalse(DlcPackMetadata.isValidIdentifier(null));
        assertFalse(DlcPackMetadata.isValidIdentifier(""));
        assertFalse(DlcPackMetadata.isValidIdentifier("pack "));
        assertFalse(DlcPackMetadata.isValidIdentifier("pack/name"));
        assertFalse(DlcPackMetadata.isValidIdentifier("pack:name"));
        assertTrue(DlcPackMetadata.isValidIdentifier("Pack-01_中文"));
    }

    @Test
    void readsAuthorArraysLocalizedTextLinksAndLists() throws Exception {
        Path pack = tempDir.resolve("pack.dc");
        Files.writeString(pack, """
            [basic]
            identifier = "example_pack"
            version = "1.2.3"
            author = ["Steve", "Alex"]

            [modpack]
            base = ["1.0.x", "1.2.0~1.3.0"]
            dlc = "any"

            [DisplayName]
            zh_cn = "中文名"
            en_us = "English Name"

            [description]
            en_us = "English Description"

            [links]
            homepage = "https://example.com"
            unknown = "ignored"

            [dependencies]
            "mods/a.jar"
            "identifier_dep"

            [dependencies.mod1]
              [basic]
              identifier = "mod1"

            [conflicts]
            "mods/b.jar"
            """);

        DlcPackMetadata.Result result = DlcPackMetadata.read(DcConfigIO.read(pack, DcConfigIO.Layout.GENERIC));

        assertTrue(result.valid);
        assertNotNull(result.metadata);
        assertEquals("example_pack", result.metadata.identifier);
        assertEquals(List.of("Steve", "Alex"), result.metadata.authors);
        assertEquals(List.of("any"), result.metadata.modpacks);
        assertEquals(List.of("1.0.x", "1.2.0~1.3.0"), result.metadata.baseVersions);
        assertEquals(List.of("any"), result.metadata.dlcVersions);
        assertEquals("中文名", result.metadata.resolveDisplayName("zh_cn"));
        assertEquals("English Name", result.metadata.resolveDisplayName("fr_fr"));
        assertEquals("English Description", result.metadata.resolveDescription("zh_cn"));
        assertEquals("https://example.com", result.metadata.links.get("homepage"));
        assertFalse(result.metadata.links.containsKey("unknown"));
        assertEquals(List.of("mods/a.jar", "identifier_dep", "mod1"), result.metadata.dependencies);
        assertEquals(List.of("mods/b.jar"), result.metadata.conflicts);
    }

    @Test
    void missingAuthorDefaultsToUnknownAuthor() throws Exception {
        Path pack = tempDir.resolve("pack.dc");
        Files.writeString(pack, """
            [basic]
            identifier = "example_pack"
            version = "1.0.0"
            """);

        DlcPackMetadata.Result result = DlcPackMetadata.read(DcConfigIO.read(pack, DcConfigIO.Layout.GENERIC));

        assertTrue(result.valid);
        assertNotNull(result.metadata);
        assertEquals(List.of("Unknown Author"), result.metadata.authors);
    }

    @Test
    void matchesExactWildcardRangesAndAnyVersions() {
        assertTrue(DlcPackMetadata.versionMatches("1.0.0", "1.0.0"));
        assertFalse(DlcPackMetadata.versionMatches("1.0.0", "1.0.1"));
        assertTrue(DlcPackMetadata.versionMatches("1.0.x", "1.0.9"));
        assertFalse(DlcPackMetadata.versionMatches("1.0.x", "1.1.0"));
        assertTrue(DlcPackMetadata.versionMatches("1.0.0~1.0.2", "1.0.1"));
        assertFalse(DlcPackMetadata.versionMatches("1.0.0~1.0.2", "1.0.3"));
        assertTrue(DlcPackMetadata.versionMatches("~1.0.2", "1.0.1"));
        assertTrue(DlcPackMetadata.versionMatches("1.0.2~", "1.0.3"));
        assertTrue(DlcPackMetadata.anyVersionMatches(List.of("1.0.0", "1.1.x"), "1.1.5"));
    }
}
