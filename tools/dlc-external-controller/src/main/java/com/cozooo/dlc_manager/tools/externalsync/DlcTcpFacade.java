package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Public facade over the package-private {@link DlcTcpClient}.
 * <p>
 * The JavaFX UI lives in a different package and cannot reference
 * {@code DlcTcpClient} directly, so this wrapper re-exposes every operation
 * as a public method.  Instances are not thread-safe; callers should confine
 * a connection to a single background thread or use external synchronisation.
 */
public final class DlcTcpFacade implements AutoCloseable {

    private final DlcTcpClient client;

    private DlcTcpFacade(DlcTcpClient client) {
        this.client = client;
    }

    /** Connect to a server.  Throws {@link ExternalSyncException} on failure. */
    public static DlcTcpFacade connect(String host, int port, String password, int timeoutMs)
            throws ExternalSyncException {
        SyncModels.ServerConfig config = new SyncModels.ServerConfig();
        config.host = host;
        config.port = port <= 0 ? SyncModels.DEFAULT_SERVER_PORT : port;
        config.password = password == null ? "" : password;
        config.timeoutMs = timeoutMs <= 0 ? SyncModels.DEFAULT_TIMEOUT_MS : timeoutMs;
        return new DlcTcpFacade(DlcTcpClient.connect(config));
    }

    public SyncModels.ServerInfo fetchList() throws ExternalSyncException {
        return client.fetchList();
    }

    public List<SyncModels.FileEntry> listFiles(String path) throws ExternalSyncException {
        return client.listFiles(path);
    }

    public void uploadFile(String remotePath, Path localPath) throws ExternalSyncException {
        client.uploadFile(remotePath, localPath);
    }

    public void deleteFile(String remotePath) throws ExternalSyncException {
        client.deleteFile(remotePath);
    }

    public void setEnabled(String id, boolean enabled) throws ExternalSyncException {
        client.setEnabled(id, enabled);
    }

    public boolean apply() throws ExternalSyncException {
        return client.apply();
    }

    /**
     * Same as {@link #apply()} but also returns the human-readable message
     * lines the server reports for the apply (enabled/disabled items, conflicts,
     * …).  {@code messages} is empty when the server omits the field.
     */
    public ApplyOutcome applyWithMessages() throws ExternalSyncException {
        return client.applyWithMessages();
    }

    // ── Remote download ───────────────────────────────────────────
    public JsonObject remoteList() throws ExternalSyncException { return client.remoteList(); }
    public JsonObject remoteDownloadStart(List<String> keys) throws ExternalSyncException { return client.remoteDownloadStart(keys); }
    public JsonObject remoteDownloadStatus() throws ExternalSyncException { return client.remoteDownloadStatus(); }
    public JsonObject remoteDownloadCancel() throws ExternalSyncException { return client.remoteDownloadCancel(); }

    // ── Required ──────────────────────────────────────────────────
    public JsonObject requiredList() throws ExternalSyncException { return client.requiredList(); }
    public JsonObject requiredDownloadStart(List<String> ids) throws ExternalSyncException { return client.requiredDownloadStart(ids); }
    public JsonObject requiredDownloadStatus() throws ExternalSyncException { return client.requiredDownloadStatus(); }
    public JsonObject requiredDownloadCancel() throws ExternalSyncException { return client.requiredDownloadCancel(); }
    public JsonObject requiredUninstall(List<String> ids) throws ExternalSyncException { return client.requiredUninstall(ids); }
    public JsonObject requiredApply(List<String> ids) throws ExternalSyncException { return client.requiredApply(ids); }

    // ── Hot update ────────────────────────────────────────────────
    public JsonObject updateState() throws ExternalSyncException { return client.updateState(); }
    public JsonObject updateList() throws ExternalSyncException { return client.updateList(); }
    public JsonObject updateApply(String baseOld, String dlcOld, String baseNew, String dlcNew) throws ExternalSyncException { return client.updateApply(baseOld, dlcOld, baseNew, dlcNew); }
    public JsonObject updateRollback(String target) throws ExternalSyncException { return client.updateRollback(target); }
    public JsonObject updateImport(String path) throws ExternalSyncException { return client.updateImport(path); }
    public JsonObject updateImportUrl(String url, String fileName) throws ExternalSyncException { return client.updateImportUrl(url, fileName); }

    // ── DLC Pack ──────────────────────────────────────────────────
    public JsonObject packList() throws ExternalSyncException { return client.packList(); }
    public JsonObject packInstall(String archivePath) throws ExternalSyncException { return client.packInstall(archivePath); }
    public JsonObject packUninstall(String identifier) throws ExternalSyncException { return client.packUninstall(identifier); }
    public JsonObject packDelete(String archivePath) throws ExternalSyncException { return client.packDelete(archivePath); }
    public JsonObject packImport(String path) throws ExternalSyncException { return client.packImport(path); }
    public JsonObject packImportUrl(String url, String fileName) throws ExternalSyncException { return client.packImportUrl(url, fileName); }
    public JsonObject packOrder(List<String> order) throws ExternalSyncException { return client.packOrder(order); }
    public JsonObject packApplyStart() throws ExternalSyncException { return client.packApplyStart(null, null, null); }
    public JsonObject packApplyStart(List<String> order, List<String> uninstallIdentifiers, List<String> installArchives) throws ExternalSyncException {
        return client.packApplyStart(order, uninstallIdentifiers, installArchives);
    }
    public JsonObject packApplyStatus() throws ExternalSyncException { return client.packApplyStatus(); }
    public JsonObject packApplyPause() throws ExternalSyncException { return client.packApplyPause(); }
    public JsonObject packApplyResume() throws ExternalSyncException { return client.packApplyResume(); }
    public JsonObject packApplyCancel() throws ExternalSyncException { return client.packApplyCancel(); }

    // ── Push (local → server) ────────────────────────────────────
    public JsonObject pushInit(String id, String mode, List<String> targets, List<String> paths) throws ExternalSyncException { return client.pushInit(id, mode, targets, paths); }
    public void pushChunk(String id, byte[] data) throws ExternalSyncException { client.pushChunk(id, data); }
    public JsonObject pushFinish() throws ExternalSyncException { return client.pushFinish(); }

    // ── File download (server → local) ───────────────────────────
    public Path downloadFile(String remotePath, Path outputDir) throws ExternalSyncException, IOException {
        return client.downloadFile(remotePath, outputDir);
    }

    // ── Config ────────────────────────────────────────────────────
    public JsonObject configGet() throws ExternalSyncException { return client.configGet(); }
    public JsonObject configSet(JsonObject config) throws ExternalSyncException { return client.configSet(config); }

    // ── RCON ──────────────────────────────────────────────────────
    public JsonObject rconInfo() throws ExternalSyncException { return client.rconInfo(); }
    public JsonObject rconGet() throws ExternalSyncException { return client.rconGet(); }
    public JsonObject rconSet(String host, int port, String password) throws ExternalSyncException { return client.rconSet(host, port, password); }
    public JsonObject rconExec(String command) throws ExternalSyncException { return client.rconExec(command); }
    public JsonObject serverRestart() throws ExternalSyncException { return client.serverRestart(); }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
