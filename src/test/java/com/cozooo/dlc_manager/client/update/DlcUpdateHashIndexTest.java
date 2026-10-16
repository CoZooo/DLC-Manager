package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dlc_manager.core.DlcFileMatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcUpdateHashIndexTest {
    @TempDir Path game;

    @Test
    void alignsNamesFillsMissingHashesAndReusesExistingHashes() throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path root = game.resolve("DLC");
        Path index = root.resolve("tmp/update/hash_list.dc");
        Files.createDirectories(index.getParent());
        Files.writeString(index, """
            # keep this comment
            vendor = "keep"
            [files]
            "existing.jar" = "cached"
            "missing-hash.jar" = ""
            "removed.jar" = "stale"
            """);
        Files.writeString(mods.resolve("existing.jar"), "changed externally");
        Files.writeString(mods.resolve("missing-hash.jar"), "fill");
        Files.writeString(mods.resolve("new.jar"), "new");
        DlcUpdateHashIndex.prepare(game, root);
        var document = DcDocuments.read(index).toJson();
        var files = document.getAsJsonObject("files");
        assertEquals(3, files.size());
        assertEquals("cached", files.get("existing.jar").getAsString());
        assertEquals(DlcFileMatch.hash(mods.resolve("missing-hash.jar")), files.get("missing-hash.jar").getAsString());
        assertEquals(DlcFileMatch.hash(mods.resolve("new.jar")), files.get("new.jar").getAsString());
        assertEquals("keep", document.get("vendor").getAsString());
        assertTrue(Files.readString(index).contains("# keep this comment"));
    }

    @Test
    void prefersNamesThenLooksUpHashesOnlyInRequestedModsDirectory() throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods/nested"));
        Path root = game.resolve("DLC");
        Path named = Files.writeString(mods.resolve("a.jar"), "named");
        Path renamed = Files.writeString(mods.resolve("renamed.jar"), "payload");
        Files.writeString(game.resolve("mods/other.jar"), "payload");
        Path config = Files.createDirectories(game.resolve("config"));
        Files.writeString(config.resolve("renamed.jar"), "payload");
        String hash = DlcFileMatch.hash(renamed);
        var index = DlcUpdateHashIndex.prepare(game, root);
        assertEquals(List.of(named), index.findDeleteMatches(game, "mods/nested/a.jar", hash));
        assertEquals(List.of(renamed), index.findDeleteMatches(game, "mods/nested/missing.jar", hash.toUpperCase()));
        assertEquals(List.of(renamed), index.findDeleteMatches(game, "mods/*/missing.jar", hash));
        assertTrue(index.findDeleteMatches(game, "mods/nested/missing.jar", null).isEmpty());
        assertTrue(index.findDeleteMatches(game, "config/missing.jar", hash).isEmpty());
        assertTrue(index.findDeleteMatches(game, "mods/../config/missing.jar", hash).isEmpty());
        assertTrue(index.findDeleteMatches(game, "mods/nested/missing.jar", "invalid").isEmpty());
    }

    @Test
    void hashFallbackReturnsAllMatchingNamesAndNeverFollowsSymlinks() throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path first = Files.writeString(mods.resolve("first.jar"), "payload");
        Path second = Files.writeString(mods.resolve("second.jar"), "payload");
        Path outside = Files.createDirectories(game.resolve("outside"));
        Files.writeString(outside.resolve("external.jar"), "payload");
        Files.createSymbolicLink(mods.resolve("linked"), outside);
        var index = DlcUpdateHashIndex.prepare(game, game.resolve("DLC"));
        assertEquals(List.of(first, second), index.findDeleteMatches(game, "mods/missing.jar", DlcFileMatch.hash(first)));
        assertTrue(index.findDeleteMatches(game, "mods/linked/missing.jar", DlcFileMatch.hash(first)).isEmpty());
        assertFalse(DcDocuments.read(game.resolve("DLC/tmp/update/hash_list.dc")).toJson().getAsJsonObject("files").has("linked/external.jar"));
    }

    @Test
    void invalidationRehashesReplacementsButKeepsUnaffectedHashes() throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path file = Files.writeString(mods.resolve("a.jar"), "old");
        Path other = Files.writeString(mods.resolve("b.jar"), "other");
        Path root = game.resolve("DLC");
        String old = DlcFileMatch.hash(file);
        DlcUpdateHashIndex.prepare(game, root);
        Files.writeString(file, "new");
        Files.writeString(other, "external change deliberately ignored");
        DlcUpdateHashIndex.invalidate(game, root, List.of("mods/a.jar"));
        var index = DlcUpdateHashIndex.prepare(game, root);
        assertTrue(index.findDeleteMatches(game, "mods/missing.jar", old).isEmpty());
        assertEquals(List.of(file), index.findDeleteMatches(game, "mods/missing.jar", DlcFileMatch.hash(file)));
        var files = DcDocuments.read(root.resolve("tmp/update/hash_list.dc")).toJson().getAsJsonObject("files");
        assertNotEquals(DlcFileMatch.hash(other), files.get("b.jar").getAsString());
    }

    @Test
    void hashingDoesNotRunOnCallingThreadsScanCache() throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path file = Files.writeString(mods.resolve("a.jar"), "before");
        DlcFileMatch.beginScan();
        try {
            String old = DlcFileMatch.hash(file);
            Files.writeString(file, "after");
            var index = DlcUpdateHashIndex.prepare(game, game.resolve("DLC"));
            assertTrue(index.findDeleteMatches(game, "mods/missing.jar", old).isEmpty());
        } finally {
            DlcFileMatch.endScan();
        }
    }

    @Test
    void corruptIndexFailsWithoutOverwritingIt() throws Exception {
        Path root = game.resolve("DLC");
        Path file = root.resolve("tmp/update/hash_list.dc");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "files = [broken");
        assertThrows(java.io.IOException.class, () -> DlcUpdateHashIndex.prepare(game, root));
        assertEquals("files = [broken", Files.readString(file));
    }
}
