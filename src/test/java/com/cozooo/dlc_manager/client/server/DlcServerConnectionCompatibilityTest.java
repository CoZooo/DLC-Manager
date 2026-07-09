package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcServerConnectionCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void staticConnectionMethodsKeepNullBehaviorForMissingEntry() throws Exception {
        assertNull(DlcServerConnection.fetchServerInfo(null, 1));
        assertNull(DlcServerConnection.fetchRemoteMissingList(null, 1));
        assertNull(DlcServerConnection.fetchRequiredList(null, 1));
        assertNull(DlcServerConnection.startRemoteDownload(null, 1, List.of("a")));
        assertNull(DlcServerConnection.fetchRemoteDownloadStatus(null, 1));
        assertNull(DlcServerConnection.startRequiredDownload(null, 1, List.of("a")));
        assertNull(DlcServerConnection.fetchRequiredDownloadStatus(null, 1));
        assertNull(DlcServerConnection.fetchUpdates(null, 1));
        assertNull(DlcServerConnection.fetchUpdateState(null, 1));
        assertNull(DlcServerConnection.applyUpdate(null, 1, "1", "1", "2", "2"));
        assertNull(DlcServerConnection.rollbackUpdate(null, 1, "1"));
        assertNull(DlcServerConnection.fetchPackList(null, 1));
        assertNull(DlcServerConnection.savePackOrder(null, 1, List.of("a")));
        assertNull(DlcServerConnection.installPack(null, 1, "a.zip"));
        assertNull(DlcServerConnection.startPackApply(null, 1, List.of("a"), List.of(), List.of()));
        assertNull(DlcServerConnection.fetchPackApplyStatus(null, 1));
        assertNull(DlcServerConnection.pausePackApply(null, 1));
        assertNull(DlcServerConnection.resumePackApply(null, 1));
        assertNull(DlcServerConnection.cancelPackApply(null, 1));
        assertNull(DlcServerConnection.uninstallPack(null, 1, "a"));
        assertNull(DlcServerConnection.deletePack(null, 1, "a.zip"));
        assertNull(DlcServerConnection.listFiles(null, 1, ""));
        assertNull(DlcServerConnection.fetchRconConfig(null, 1));
        assertNull(DlcServerConnection.fetchRconInfo(null, 1));
        assertNull(DlcServerConnection.execRcon(null, 1, "list"));
        assertNull(DlcServerConnection.fetchServerConfig(null, 1));
    }

    @Test
    void staticConnectionMethodsKeepFalseBehaviorForMissingEntry() throws Exception {
        Path source = Files.writeString(tempDir.resolve("a.zip"), "data");
        Path isolatedDlcRoot = tempDir.resolve("game").resolve("DLC");
        DlcConfig.setDlcRoot(isolatedDlcRoot.toString());
        try {
            assertFalse(DlcServerConnection.downloadMissingRemote(null, 1));
            assertFalse(DlcServerConnection.cancelRequiredDownload(null, 1));
            assertFalse(DlcServerConnection.uninstallRequired(null, 1, List.of("a")));
            assertFalse(DlcServerConnection.applyRequired(null, 1, List.of("a")));
            assertFalse(DlcServerConnection.restartServer(null, 1));
            assertFalse(DlcServerConnection.downloadFile(null, 1, "a", Path.of("a")));
            assertFalse(DlcServerConnection.importUpdatePackage(null, 1, source));
            assertFalse(DlcServerConnection.importPackPackage(null, 1, source));
            assertFalse(DlcServerConnection.importPackPackageFromUrl(null, 1, "https://example.invalid/a.zip", null));
            assertFalse(DlcServerConnection.importUpdatePackageFromUrl(null, 1, "https://example.invalid/a.zip", null));
            assertFalse(DlcServerConnection.applyUpdates(null, 1));
            assertFalse(DlcServerConnection.push(null, 1, List.of("all"), "overwrite"));
            assertFalse(DlcServerConnection.deleteFile(null, 1, "a"));
            assertFalse(DlcServerConnection.uploadFile(null, 1, "a", source));
            assertFalse(DlcServerConnection.saveRconConfig(null, 1, new DlcServerRconConfig()));
            assertFalse(DlcServerConnection.saveServerConfig(null, 1, new JsonObject()));
            assertFalse(Files.exists(isolatedDlcRoot));
        } finally {
            DlcConfig.setDlcRoot(DlcConfig.DEFAULT_DLC_ROOT);
        }
    }
}
