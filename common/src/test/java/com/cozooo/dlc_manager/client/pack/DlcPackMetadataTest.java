package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.platform.PlatformServices;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
    }

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
            dlc = ""

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

        DlcPackMetadata.Result result = DlcPackMetadata.read(pack);

        assertTrue(result.valid);
        assertNotNull(result.metadata);
        assertEquals("example_pack", result.metadata.identifier);
        assertEquals(List.of("Steve", "Alex"), result.metadata.authors);
        assertEquals(List.of(), result.metadata.modpacks);
        assertFalse(result.metadata.modpackAnyWildcard);
        assertEquals(List.of("1.0.x", "1.2.0~1.3.0"), result.metadata.baseVersions);
        assertEquals(List.of(), result.metadata.dlcVersions);
        assertEquals("中文名", result.metadata.resolveDisplayName("zh_cn"));
        assertEquals("English Name", result.metadata.resolveDisplayName("fr_fr"));
        assertEquals("English Description", result.metadata.resolveDescription("zh_cn"));
        assertEquals("https://example.com", result.metadata.links.get("homepage"));
        assertFalse(result.metadata.links.containsKey("unknown"));
        assertEquals(List.of("mods/a.jar", "identifier_dep", "mod1"), result.metadata.dependencies);
        assertEquals(List.of("mods/b.jar"), result.metadata.conflicts);
    }

    @Test
    void readsLanguageSpecificLinksOverGlobalLinks() throws Exception {
        Path pack = tempDir.resolve("pack.dc");
        Files.writeString(pack, """
            [basic]
            identifier = "example_pack"
            version = "1.0.0"

            [links]
            homepage = "https://example.com/global"
            sponsor = "https://example.com/sponsor"

            [links.zh_cn]
            homepage = "https://example.com/zh"
            issue = "https://example.com/zh-issue"
            """);

        PlatformServices.setProvider(provider("zh_cn"));
        DlcPackMetadata.Result zhResult = DlcPackMetadata.read(pack);

        assertTrue(zhResult.valid);
        assertEquals("https://example.com/zh", zhResult.metadata.links.get("homepage"));
        assertEquals("https://example.com/zh-issue", zhResult.metadata.links.get("issue"));
        assertEquals("https://example.com/sponsor", zhResult.metadata.links.get("sponsor"));

        PlatformServices.setProvider(provider("fr_fr"));
        DlcPackMetadata.Result frResult = DlcPackMetadata.read(pack);

        assertTrue(frResult.valid);
        assertEquals("https://example.com/global", frResult.metadata.links.get("homepage"));
        assertFalse(frResult.metadata.links.containsKey("issue"));
        assertEquals("https://example.com/sponsor", frResult.metadata.links.get("sponsor"));
    }

    private PlatformServices.Provider provider(String languageCode) {
        return new PlatformServices.Provider() {
            @Override
            public Path gameDir() {
                return tempDir.resolve("game");
            }

            @Override
            public Path configDir() {
                return tempDir.resolve("config");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }

            @Override
            public boolean isDedicatedServer() {
                return false;
            }

            @Override
            public String languageCode() {
                return languageCode;
            }

            @Override
            public String loaderName() {
                return "fabric";
            }

            @Override
            public String mcVersion() {
                return "1.20.1";
            }

            @Override
            public String modVersion(String modId) {
                return "";
            }

            @Override
            public Path modFilePath(String modId) {
                return null;
            }

            @Override
            public String playerName() {
                return "Player";
            }

            @Override
            public String playerUuid() {
                return "00000000-0000-0000-0000-000000000000";
            }
        };
    }

    @Test
    void missingAuthorDefaultsToUnknownAuthor() throws Exception {
        Path pack = tempDir.resolve("pack.dc");
        Files.writeString(pack, """
            [basic]
            identifier = "example_pack"
            version = "1.0.0"
            """);

        DlcPackMetadata.Result result = DlcPackMetadata.read(pack);

        assertTrue(result.valid);
        assertNotNull(result.metadata);
        assertEquals(List.of("Unknown Author"), result.metadata.authors);
    }

    @Test
    void barePackRuleTokensAreIgnoredLikeBlankRules() throws Exception {
        Path pack = tempDir.resolve("bare.dc");
        Files.writeString(pack, """
            [basic]
            identifier = "example"
            version = "1.0.0"
            gameVersion = any
            modLoader = fabric

            [modpack]
            modpack = any
            base = 1.0.x
            dlc = [any, 1.0.x]
            """);

        DlcPackMetadata.Result result = DlcPackMetadata.read(pack);

        assertTrue(result.valid);
        assertEquals("", result.metadata.gameVersion);
        assertEquals("", result.metadata.modLoader);
        assertEquals(List.of(), result.metadata.modpacks);
        assertEquals(List.of(), result.metadata.baseVersions);
        assertEquals(List.of(), result.metadata.dlcVersions);
        assertTrue(DlcPackMetadata.anyStringMatches(result.metadata.modpacks, "MainPack", result.metadata.modpackAnyWildcard));
        assertTrue(DlcPackMetadata.anyVersionMatches(result.metadata.baseVersions, "1.0.0"));
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
