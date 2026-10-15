package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcPendingUpdateTest {
    @TempDir Path game;

    @AfterEach void reset() {
        PlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    private Path setup() throws Exception {
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config")));
        Path root = game.resolve("DLC");
        DlcConfig.setDlcRoot(root.toString());
        Files.createDirectories(root.resolve("info"));
        Files.writeString(root.resolve("info/version.dc"), "# original version\nbase = '1'\ndlc = '1'\n");
        Files.createDirectories(game.resolve("mods"));
        return root;
    }

    private DlcUpdatePackage pack(String old, String next, String ops, String payload) throws Exception {
        Path zip = game.resolve(UUID.randomUUID() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("update.dc"));
            out.write(("[version]\nbase='1'\ndlc='" + old + "'\ndlc_new='" + next + "'\n" + ops).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            if (payload != null) {
                out.putNextEntry(new ZipEntry("mods/a.jar"));
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return DlcUpdatePackage.fromZip(zip);
    }

    @Test void unknownParametersRequireChoiceAndIgnoreOnlySkipsIncompleteOperations() throws Exception {
        setup();
        Files.writeString(game.resolve("mods/old.jar"), "old");
        var pkg = pack("1", "2", """
            future_root = true
            [file]
              [del]
                "mods/old.jar"
              [future_operation]
                path = 'mods/keep.jar'
              [add]
                'mods/a.jar':
                  future_destination = 'mods/unsafe.jar'
            """, "new");
        assertNotNull(pkg);
        assertFalse(pkg.getManifest().getUnknownParameters().isEmpty());
        var blocked = DlcUpdateService.applyUpdates(List.of(pkg), false);
        assertTrue(blocked.isCompatibilityRequired());
        assertTrue(Files.exists(game.resolve("mods/old.jar")));
        pkg.setCompatibilityAction(DlcUpdatePackage.CompatibilityAction.IGNORE);
        var result = DlcUpdateService.applyUpdates(List.of(pkg), false);
        assertTrue(result.isSuccess(), result.getMessage());
        assertFalse(Files.exists(game.resolve("mods/old.jar")));
        assertFalse(Files.exists(game.resolve("mods/a.jar")));
        assertFalse(Files.exists(game.resolve("mods/unsafe.jar")));
    }

    @Test void pendingTaskUsesSavedArchivesAndClearsMarkerOnSuccess() throws Exception {
        Path root = setup();
        Files.writeString(game.resolve("mods/a.jar"), "old");
        var pkg = pack("1", "2", "[file]\n  [del]\n    'mods/a.jar'\n", null);
        DlcPendingUpdate.schedule(List.of(pkg), false);
        Files.writeString(pkg.getArchivePath(), "original archive later replaced");
        assertTrue(Files.exists(game.resolve("mods/a.jar")));
        assertEquals("1", DlcUpdateVersion.load(root).getDlc());
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertFalse(Files.exists(game.resolve("mods/a.jar")));
        assertEquals("2", DlcUpdateVersion.load(root).getDlc());
        assertFalse(Files.exists(root.resolve("tmp/update/pending_update.dc")));
        assertTrue(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        assertFalse(DlcPendingUpdate.process(ignored -> {}, false));
    }

    @Test void failedChainRestoresFilesAndExactVersionConfigAndClearsMarker() throws Exception {
        Path root = setup();
        Path mod = Files.writeString(game.resolve("mods/a.jar"), "original");
        String version = Files.readString(root.resolve("info/version.dc"));
        var first = pack("1", "2", "[file]\n  [del]\n    'mods/a.jar'\n", null);
        var second = pack("2", "3", "[file]\n  [modify]\n    'config/missing.cfg':\n      find='old'\n      replace='new'\n", null);
        DlcPendingUpdate.schedule(List.of(first, second), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertEquals("original", Files.readString(mod));
        assertEquals(version, Files.readString(root.resolve("info/version.dc")));
        assertFalse(Files.exists(root.resolve("tmp/update/pending_update.dc")));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
    }

    @Test void remainingUnknownAfterManagerUpdateFailsAndFabricSuccessRequiresRestart() throws Exception {
        Path root = setup();
        var unsupported = pack("1", "2", "[file]\n  [future]\n    path='mods/a.jar'\n", null);
        unsupported.setCompatibilityAction(DlcUpdatePackage.CompatibilityAction.UPDATE_MANAGER);
        DlcPendingUpdate.schedule(List.of(unsupported), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, true));
        assertEquals("1", DlcUpdateVersion.load(root).getDlc());
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        var supported = pack("1", "2", "", null);
        DlcPendingUpdate.schedule(List.of(supported), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, true));
        assertTrue(DlcPendingUpdate.readResult().get("restart_required").getAsBoolean());
    }

    @Test void interruptedTaskRecoversJournalWithoutReapplyingUpdate() throws Exception {
        Path root = setup();
        Path mod = Files.writeString(game.resolve("mods/a.jar"), "old");
        var pkg = pack("1", "2", "", null);
        DlcPendingUpdate.schedule(List.of(pkg), false);
        Path update = root.resolve("tmp/update");
        var request = DcDocuments.read(update.resolve("pending_update.dc")).toJson();
        Path directory = update.resolve("jobs").resolve(request.get("job").getAsString());
        try (var journal = DlcUpdateJournal.open(game, directory)) {
            journal.capture(mod);
            Files.writeString(mod, "interrupted changes");
        }
        var recovery = new com.google.gson.JsonObject();
        recovery.addProperty("job", request.get("job").getAsString());
        recovery.addProperty("committed", false);
        DcDocuments.write(update.resolve("pending_recovery.dc"), DcDocuments.fromJson(recovery));
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertEquals("old", Files.readString(mod));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        assertFalse(Files.exists(update.resolve("pending_update.dc")));
    }
    @Test void updateDlcmDefersEvenWhenRequestedManagerVersionIsAlreadyInstalled() throws Exception {
        Path root = setup();
        PlatformServices.setProvider(new com.cozooo.dlc_manager.platform.DlcPlatformServices.Provider() {
            public Path gameDir() { return game; }
            public Path configDir() { return game.resolve("config"); }
            public boolean isModLoaded(String id) { return false; }
            public boolean isDedicatedServer() { return false; }
            public String languageCode() { return "en_us"; }
            public String loaderName() { return "fabric"; }
            public String mcVersion() { return "1.20.1"; }
            public String modVersion(String id) { return "4.1.0"; }
            public Path modFilePath(String id) { return null; }
            public String playerName() { return ""; }
            public String playerUuid() { return ""; }
        });
        Path mod = Files.writeString(game.resolve("mods/a.jar"), "old");
        Path zip = game.resolve("manager-first.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("update.dc"));
            out.write("""
                UpdateDLCM = '4.1.0'
                [version]
                base = '1'
                dlc = '1'
                dlc_new = '2'
                [file]
                  [del]
                    'mods/a.jar'
                """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        var pkg = DlcUpdatePackage.fromZip(zip);
        var result = DlcUpdateService.applyUpdates(List.of(pkg), false);
        assertTrue(result.isPendingUpdate());
        assertTrue(result.isSuccess());
        assertEquals("old", Files.readString(mod));
        assertEquals("1", DlcUpdateVersion.load(root).getDlc());
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertFalse(Files.exists(mod));
        assertEquals("2", DlcUpdateVersion.load(root).getDlc());
        assertFalse(Files.exists(root.resolve("tmp/update/pending_update.dc")));
    }

    @Test void archiveTamperingFailsBeforeApplyingAnyOperations() throws Exception {
        Path root = setup();
        Path mod = Files.writeString(game.resolve("mods/a.jar"), "original");
        var pkg = pack("1", "2", "[file]\n  [del]\n    'mods/a.jar'\n", null);
        DlcPendingUpdate.schedule(List.of(pkg), false);
        Path update = root.resolve("tmp/update");
        var request = DcDocuments.read(update.resolve("pending_update.dc")).toJson();
        Files.writeString(update.resolve("jobs").resolve(request.get("job").getAsString()).resolve("archive-0.zip"), "tampered");
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertEquals("original", Files.readString(mod));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        assertFalse(Files.exists(update.resolve("pending_update.dc")));
    }

    @Test void changedDlcRootDoesNotLoseInterruptedTask() throws Exception {
        Path root = setup();
        Path mod = Files.writeString(game.resolve("mods/a.jar"), "original");
        DlcPendingUpdate.schedule(List.of(pack("1", "2", "", null)), false);
        Path update = root.resolve("tmp/update");
        var request = DcDocuments.read(update.resolve("pending_update.dc")).toJson();
        Path directory = update.resolve("jobs").resolve(request.get("job").getAsString());
        Path config = game.resolve("config/dlc_manager/config.dc");
        try (var journal = DlcUpdateJournal.open(game, directory)) {
            journal.capture(config);
            journal.capture(mod);
            Files.createDirectories(config.getParent());
            Files.writeString(config, "[paths]\ndlcRoot='new-root'\n");
            Files.writeString(mod, "interrupted");
        }
        var marker = new com.google.gson.JsonObject();
        marker.addProperty("job", request.get("job").getAsString());
        marker.addProperty("committed", false);
        DcDocuments.write(update.resolve("pending_recovery.dc"), DcDocuments.fromJson(marker));
        DlcConfig.setDlcRoot("new-root");
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertEquals("original", Files.readString(mod));
        assertFalse(Files.exists(config));
        assertFalse(Files.exists(update.resolve("pending_update.dc")));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        assertFalse(Files.exists(game.resolve(".dlc-manager/pending_update_location.dc")));
    }

    @Test void pendingOperationsCannotDeleteTheirOwnRecoveryFiles() throws Exception {
        Path root = setup();
        String original = Files.readString(root.resolve("info/version.dc"));
        var pkg = pack("1", "2", "[file]\n  [del]\n    'DLC/tmp/update/pending_update.dc'\n", null);
        DlcPendingUpdate.schedule(List.of(pkg), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        assertEquals(original, Files.readString(root.resolve("info/version.dc")));
        assertFalse(Files.exists(root.resolve("tmp/update/pending_update.dc")));
    }

    @Test void interruptedUpdateUsesOnlyRelativePathsAndRecoversAfterGameDirectoryMoves(@TempDir Path relocatedBase) throws Exception {
        Path root = setup();
        Path mod = Files.writeString(game.resolve("mods/a.jar"), "original");
        DlcPendingUpdate.schedule(List.of(pack("1", "2", "", null)), false);
        var locator = DcDocuments.read(game.resolve(".dlc-manager/pending_update_location.dc")).toJson();
        assertEquals("DLC/tmp/update", locator.get("directory").getAsString());
        Path update = root.resolve("tmp/update");
        var request = DcDocuments.read(update.resolve("pending_update.dc")).toJson();
        Path directory = update.resolve("jobs").resolve(request.get("job").getAsString());
        try (var journal = DlcUpdateJournal.open(game, directory)) {
            journal.capture(mod);
            Files.writeString(mod, "interrupted");
        }
        var saved = DcDocuments.read(directory.resolve("recovery.dc")).toJson();
        for (var entry : saved.getAsJsonArray("entries")) assertFalse(Path.of(entry.getAsJsonObject().get("path").getAsString()).isAbsolute());
        for (var entry : saved.getAsJsonArray("roots")) assertFalse(Path.of(entry.getAsString()).isAbsolute());
        var marker = new com.google.gson.JsonObject();
        marker.addProperty("job", request.get("job").getAsString());
        marker.addProperty("committed", false);
        DcDocuments.write(update.resolve("pending_recovery.dc"), DcDocuments.fromJson(marker));
        Path relocated = relocatedBase.resolve("moved-game");
        Files.move(game, relocated);
        PlatformServices.setProvider(new TestPlatformProvider(relocated, relocated.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertEquals("original", Files.readString(relocated.resolve("mods/a.jar")));
        assertFalse(Files.exists(relocated.resolve("DLC/tmp/update/pending_update.dc")));
        assertFalse(Files.exists(relocated.resolve(".dlc-manager/pending_update_location.dc")));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
    }

    @Test void newRuntimeConfigurationsRejectAbsolutePathFields() throws Exception {
        setup();
        assertThrows(java.io.IOException.class, () -> DlcUpdateJournal.resolveRelativePath(game, game.resolve("mods/a.jar").toString()));
        assertThrows(java.io.IOException.class, () -> DlcUpdateJournal.resolveRelativePath(game, "C:\\game\\mods"));
        Path locator = game.resolve(".dlc-manager/pending_update_location.dc");
        var value = new com.google.gson.JsonObject();
        value.addProperty("directory", game.resolve("DLC/tmp/update").toString());
        DcDocuments.write(locator, DcDocuments.fromJson(value));
        assertThrows(java.io.IOException.class, DlcPendingUpdate::hasPending);
    }

}
