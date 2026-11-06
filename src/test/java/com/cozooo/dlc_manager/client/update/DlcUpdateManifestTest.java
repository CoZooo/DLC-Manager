package com.cozooo.dlc_manager.client.update;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateManifestTest {
    @Test
    void parsesVersionChangelogAndUpdateDLCMConfig() {
        JsonObject object = com.google.gson.JsonParser.parseString("""
            {
              "version": {
                "platform": "client",
                "base": "1.0.0",
                "base_new": "1.1.0",
                "dlc_old": "1.0.0",
                "dlc_new": "1.0.1"
              },
              "isHidden": true,
              "UpdateDLCM": "3.9.4",
              "changelog": {
                "ZH_CN": "中文日志",
                "en_us": "English log"
              }
            }
            """).getAsJsonObject();

        DlcUpdateManifest manifest = DlcUpdateManifest.fromJsonObject(object);

        assertEquals("client", manifest.getVersion().getPlatform());
        assertEquals("1.0.0", manifest.getVersion().getBase());
        assertEquals("1.1.0", manifest.getVersion().getBaseNew());
        assertEquals("1.0.0", manifest.getVersion().getDlcOld());
        assertEquals("1.0.1", manifest.getVersion().getDlcNew());
        assertTrue(manifest.isHidden());
        assertTrue(manifest.isUpdateDLCM());
        assertEquals("3.9.4", manifest.getUpdateDLCMVersion());
        assertEquals("中文日志", manifest.resolveChangelog("zh_cn"));
        assertEquals("English log", manifest.resolveChangelog("ja_jp"));
    }

    @Test
    void parsesCanonicalVersionKeysAndKeepsLegacyAliasesReadable() {
        JsonObject object = com.google.gson.JsonParser.parseString("""
            {
              "version": {
                "platform": "universal",
                "version": "2.0.0",
                "version_new": "2.1.0"
              }
            }
            """).getAsJsonObject();

        DlcUpdateManifest.VersionInfo info = DlcUpdateManifest.fromJsonObject(object).getVersion();

        assertEquals("2.0.0", info.getVersion());
        assertEquals("2.1.0", info.getVersionNew());
        assertEquals("2.0.0", info.getBase());
        assertEquals("2.1.0", info.getBaseNew());
    }

    @Test
    void parsesFileOperations() {
        JsonObject object = com.google.gson.JsonParser.parseString("""
            {
              "file": {
                "del": ["mods/old.jar"],
                "add": {
                  "mods/new.jar": {
                    "appliedTarget": "mods/new.jar",
                    "override": "all"
                  },
                  "config/dir/": {
                    "appliedTarget": "config/dir",
                    "override": false
                  }
                },
                "move": {
                  "mods/a.jar": "mods/b.jar"
                },
                "modify": {
                  "config/test.json": {
                    "find": "{\\"a\\":\\"b\\", \\"target\\":\\"old\\"}",
                    "replace": "\\"target\\":\\"new\\"",
                    "which": -1
                  }
                }
              }
            }
            """).getAsJsonObject();

        DlcUpdateManifest.FileOps ops = DlcUpdateManifest.fromJsonObject(object).getFileOps();

        assertEquals(List.of("mods/old.jar"), ops.getDel());
        assertEquals("mods/new.jar", ops.getAdd().get("mods/new.jar").getAppliedTarget());
        assertEquals(DlcUpdateManifest.OverrideMode.ALL, ops.getAdd().get("mods/new.jar").getOverrideMode());
        assertTrue(ops.getAdd().get("config/dir/").isSourceDir());
        assertEquals(DlcUpdateManifest.OverrideMode.FALSE, ops.getAdd().get("config/dir/").getOverrideMode());
        assertEquals("mods/b.jar", ops.getMove().get("mods/a.jar"));
        assertEquals("config/test.json", ops.getModify().get(0).getTargetPath());
        assertEquals("\"target\":\"old\"", ops.getModify().get(0).getNormalizedFind());
        assertTrue(ops.getModify().get(0).getWhich().isLast());
    }

    @Test
    void parsesUpdateOpsIncludingPresetAndFlattensExtraFields() {
        JsonObject object = com.google.gson.JsonParser.parseString("""
            {
              "update": {
                "remote": {
                  "remote.dc": {
                    "curseforge": "123",
                    "download": {
                      "priority": ["modrinth", "curseforge"]
                    },
                    "directUrl": ["https://example.com/file.jar"]
                  }
                },
                "preset": {
                  "preset.dc": {
                    "enable": ["mods/a.jar"],
                    "conflicts": ["mods/b.jar"]
                  }
                }
              }
            }
            """).getAsJsonObject();

        DlcUpdateManifest.UpdateOps ops = DlcUpdateManifest.fromJsonObject(object).getUpdateOps();

        DlcUpdateManifest.UpdateEntry remote = ops.getRemote().get("remote.dc");
        assertEquals("123", remote.getCurseforge());
        assertEquals("[\"modrinth\",\"curseforge\"]", remote.getFields().get("download.priority").toString());
        assertEquals("[\"https://example.com/file.jar\"]", remote.getFields().get("download.directUrl").toString());

        DlcUpdateManifest.UpdateEntry preset = ops.getPreset().get("preset.dc");
        assertEquals("[\"mods/a.jar\"]", preset.getFields().get("enable").toString());
        assertEquals("[\"mods/b.jar\"]", preset.getFields().get("conflicts").toString());
    }

    @Test
    void updateDLCMFalseOrBlankDoesNotEnableManagerUpdate() {
        JsonObject object = new JsonObject();
        object.addProperty("UpdateDLCM", false);

        DlcUpdateManifest manifest = DlcUpdateManifest.fromJsonObject(object);

        assertFalse(manifest.isUpdateDLCM());
        assertEquals(null, manifest.getUpdateDLCMVersion());
    }
    @Test
    void invalidDcFileDoesNotFallBackToJsonParsing(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path file = directory.resolve("update.dc");
        java.nio.file.Files.writeString(file, "[update]\nname = \"unterminated\n");
        org.junit.jupiter.api.Assertions.assertNull(DlcUpdateManifest.load(file));
    }
    @Test
    void deleteRulesAllowOptionalHashesAlongsideLegacyPaths() throws Exception {
        var document = DlcUpdateDcParser.parse("""
            [file]
              [del]
                "mods/a.jar":
                  hash = "abc"
                "config/legacy.txt"
                "mods/plain.jar"
            """);
        var ops = DlcUpdateManifest.fromJsonObject(document).getFileOps();
        assertEquals(java.util.List.of("mods/a.jar", "config/legacy.txt", "mods/plain.jar"), ops.getDel());
        assertEquals(java.util.Map.of("mods/a.jar", "abc"), ops.getDeleteHashes());
    }

}
