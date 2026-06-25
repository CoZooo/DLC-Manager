package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class DlcTcpClient implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int CHUNK_SIZE = 64 * 1024;

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    private DlcTcpClient(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    static DlcTcpClient connect(SyncModels.ServerConfig config) throws ExternalSyncException {
        String host = config.host == null || config.host.isBlank() ? "127.0.0.1" : config.host.trim();
        int timeout = config.timeoutMs <= 0 ? SyncModels.DEFAULT_TIMEOUT_MS : config.timeoutMs;
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, config.port), timeout);
            socket.setSoTimeout(timeout);
            DlcTcpClient client = new DlcTcpClient(socket);
            client.hello(config);
            return client;
        } catch (IOException ex) {
            throw new ExternalSyncException("server connection failed: " + ex.getMessage(), ex);
        }
    }

    SyncModels.ServerInfo fetchList() throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "list");
        send(request);
        JsonObject response = read();
        if (!"list".equals(string(response, "type"))) {
            throw new ExternalSyncException("invalid list response");
        }
        SyncModels.ServerInfo info = new SyncModels.ServerInfo();
        info.baseVersion = string(response, "base");
        info.dlcVersion = string(response, "dlc");
        info.remoteMissing = longValue(response, "remoteMissing", 0L);
        info.requiredMissing = longValue(response, "requiredMissing", 0L);
        JsonArray items = array(response, "items");
        if (items != null) {
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                SyncModels.ServerItem item = new SyncModels.ServerItem();
                item.id = string(object, "id");
                item.name = string(object, "name");
                item.identifier = string(object, "identifier");
                item.path = string(object, "path");
                item.type = string(object, "type");
                item.enabled = booleanOrNull(object, "enabled");
                info.items.add(item);
            }
        }
        return info;
    }

    List<SyncModels.FileEntry> listFiles(String path) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "file_list");
        request.addProperty("path", path == null ? "" : path);
        send(request);
        JsonObject response = read();
        if (!bool(response, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(response, "error"), string(response, "message"), "file_list failed"));
        }
        List<SyncModels.FileEntry> entries = new ArrayList<>();
        JsonArray array = array(response, "entries");
        if (array == null) {
            return entries;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            SyncModels.FileEntry entry = new SyncModels.FileEntry();
            entry.name = firstNonBlank(string(object, "name"), "");
            entry.dir = bool(object, "dir");
            entry.size = longValue(object, "size", longValue(object, "length", 0L));
            entry.modified = longValue(object, "modified", 0L);
            entries.add(entry);
        }
        return entries;
    }

    void uploadFile(String remotePath, Path localPath) throws ExternalSyncException {
        if (localPath == null || !Files.isRegularFile(localPath)) {
            throw new ExternalSyncException("missing local file: " + localPath);
        }
        String transferId = UUID.randomUUID().toString();
        long size;
        try {
            size = Files.size(localPath);
        } catch (IOException ex) {
            throw new ExternalSyncException("failed to stat " + localPath, ex);
        }
        JsonObject init = new JsonObject();
        init.addProperty("type", "file_upload_init");
        init.addProperty("id", transferId);
        init.addProperty("path", remotePath == null ? "" : remotePath);
        init.addProperty("size", size);
        send(init);
        JsonObject initResponse = read();
        if (!bool(initResponse, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(initResponse, "error"), string(initResponse, "message"), "file_upload_init failed"));
        }
        byte[] buffer = new byte[CHUNK_SIZE];
        try (InputStream input = Files.newInputStream(localPath)) {
            while (true) {
                int bytesRead = input.read(buffer);
                if (bytesRead < 0) {
                    break;
                }
                JsonObject chunk = new JsonObject();
                chunk.addProperty("type", "file_upload_chunk");
                chunk.addProperty("id", transferId);
                chunk.addProperty("data", Base64.getEncoder().encodeToString(copy(buffer, bytesRead)));
                send(chunk);
            }
        } catch (IOException ex) {
            throw new ExternalSyncException("failed to upload " + localPath, ex);
        }
        JsonObject finish = new JsonObject();
        finish.addProperty("type", "file_upload_finish");
        finish.addProperty("id", transferId);
        send(finish);
        JsonObject finishResponse = read();
        if (!bool(finishResponse, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(finishResponse, "error"), string(finishResponse, "message"), "file_upload_finish failed"));
        }
    }

    void deleteFile(String remotePath) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "file_delete");
        request.addProperty("path", remotePath == null ? "" : remotePath);
        send(request);
        JsonObject response = read();
        if (!bool(response, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(response, "error"), string(response, "message"), "file_delete failed"));
        }
    }

    void setEnabled(String id, boolean enabled) throws ExternalSyncException {
        if (id == null || id.isBlank()) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("type", "set");
        request.addProperty("id", id);
        request.addProperty("enabled", enabled);
        send(request);
        JsonObject response = read();
        if (!bool(response, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(response, "error"), string(response, "message"), "set failed"));
        }
    }

    boolean apply() throws ExternalSyncException {
        return applyWithMessages().requiresRestart();
    }

    ApplyOutcome applyWithMessages() throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "apply");
        send(request);
        JsonObject response = read();
        if (!bool(response, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(response, "error"), string(response, "message"), "apply failed"));
        }
        boolean requiresRestart = bool(response, "requiresRestart");
        List<String> messages = new ArrayList<>();
        JsonArray msgArray = array(response, "messages");
        if (msgArray != null) {
            for (JsonElement e : msgArray) {
                if (e != null && e.isJsonPrimitive()) {
                    String msg = e.getAsString();
                    if (msg != null && !msg.isBlank()) {
                        messages.add(msg);
                    }
                }
            }
        }
        return new ApplyOutcome(requiresRestart, messages);
    }

    // ───────────────────────────────────────────────────────────────
    //  Remote download
    // ───────────────────────────────────────────────────────────────

    JsonObject remoteList() throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "remote_list");
        send(request);
        JsonObject response = read();
        requireOk(response, "remote_list");
        return response;
    }

    JsonObject remoteDownloadStart(List<String> keys) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "remote_download_start");
        if (keys != null) {
            JsonArray arr = new JsonArray();
            for (String k : keys) {
                arr.add(k);
            }
            request.add("keys", arr);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "remote_download_start");
        return response;
    }

    JsonObject remoteDownloadStatus() throws ExternalSyncException {
        return simpleRequest("remote_download_status");
    }

    // ───────────────────────────────────────────────────────────────
    //  Required
    // ───────────────────────────────────────────────────────────────

    JsonObject requiredList() throws ExternalSyncException {
        return simpleRequest("required_list");
    }

    JsonObject requiredDownloadStart(List<String> ids) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "required_download_start");
        if (ids != null) {
            JsonArray arr = new JsonArray();
            for (String id : ids) {
                arr.add(id);
            }
            request.add("ids", arr);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "required_download_start");
        return response;
    }

    JsonObject requiredDownloadStatus() throws ExternalSyncException {
        return simpleRequest("required_download_status");
    }

    JsonObject requiredDownloadCancel() throws ExternalSyncException {
        return simpleRequest("required_download_cancel");
    }

    JsonObject requiredUninstall(List<String> ids) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "required_uninstall");
        if (ids != null) {
            JsonArray arr = new JsonArray();
            for (String id : ids) {
                arr.add(id);
            }
            request.add("ids", arr);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "required_uninstall");
        return response;
    }

    JsonObject requiredApply(List<String> ids) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "required_apply");
        if (ids != null) {
            JsonArray arr = new JsonArray();
            for (String id : ids) {
                arr.add(id);
            }
            request.add("ids", arr);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "required_apply");
        return response;
    }

    // ───────────────────────────────────────────────────────────────
    //  Hot update
    // ───────────────────────────────────────────────────────────────

    JsonObject updateState() throws ExternalSyncException {
        return simpleRequest("update_state");
    }

    JsonObject updateList() throws ExternalSyncException {
        return simpleRequest("update_list");
    }

    JsonObject updateApply(String baseOld, String dlcOld, String baseNew, String dlcNew) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "update_apply");
        if (baseOld != null) request.addProperty("baseOld", baseOld);
        if (dlcOld != null) request.addProperty("dlcOld", dlcOld);
        if (baseNew != null) request.addProperty("baseNew", baseNew);
        if (dlcNew != null) request.addProperty("dlcNew", dlcNew);
        send(request);
        JsonObject response = read();
        requireOk(response, "update_apply");
        return response;
    }

    JsonObject updateRollback(String target) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "update_rollback");
        if (target != null) request.addProperty("target", target);
        send(request);
        JsonObject response = read();
        requireOk(response, "update_rollback");
        return response;
    }

    JsonObject updateImport(String path) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "update_import");
        if (path != null) request.addProperty("path", path);
        send(request);
        JsonObject response = read();
        requireOk(response, "update_import");
        return response;
    }

    JsonObject updateImportUrl(String url, String fileName) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "update_import_url");
        if (url != null) request.addProperty("url", url);
        if (fileName != null) request.addProperty("fileName", fileName);
        send(request);
        JsonObject response = read();
        requireOk(response, "update_import_url");
        return response;
    }

    // ───────────────────────────────────────────────────────────────
    //  DLC Pack
    // ───────────────────────────────────────────────────────────────

    JsonObject packList() throws ExternalSyncException {
        return simpleRequest("pack_list");
    }

    JsonObject packInstall(String archivePath) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "pack_install");
        if (archivePath != null) request.addProperty("archivePath", archivePath);
        send(request);
        JsonObject response = read();
        requireOk(response, "pack_install");
        return response;
    }

    JsonObject packUninstall(String identifier) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "pack_uninstall");
        if (identifier != null) request.addProperty("identifier", identifier);
        send(request);
        JsonObject response = read();
        requireOk(response, "pack_uninstall");
        return response;
    }

    JsonObject packDelete(String archivePath) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "pack_delete");
        if (archivePath != null) request.addProperty("archivePath", archivePath);
        send(request);
        JsonObject response = read();
        requireOk(response, "pack_delete");
        return response;
    }

    JsonObject packImport(String path) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "pack_import");
        if (path != null) request.addProperty("path", path);
        send(request);
        JsonObject response = read();
        requireOk(response, "pack_import");
        return response;
    }

    JsonObject packImportUrl(String url, String fileName) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "pack_import_url");
        if (url != null) request.addProperty("url", url);
        if (fileName != null) request.addProperty("fileName", fileName);
        send(request);
        JsonObject response = read();
        requireOk(response, "pack_import_url");
        return response;
    }

    JsonObject packOrder(List<String> order) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "pack_order");
        if (order != null) {
            JsonArray arr = new JsonArray();
            for (String o : order) {
                arr.add(o);
            }
            request.add("order", arr);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "pack_order");
        return response;
    }

    JsonObject packApplyStart() throws ExternalSyncException {
        return simpleRequest("pack_apply_start");
    }

    JsonObject packApplyStatus() throws ExternalSyncException {
        return simpleRequest("pack_apply_status");
    }

    JsonObject packApplyPause() throws ExternalSyncException {
        return simpleRequest("pack_apply_pause");
    }

    JsonObject packApplyResume() throws ExternalSyncException {
        return simpleRequest("pack_apply_resume");
    }

    JsonObject packApplyCancel() throws ExternalSyncException {
        return simpleRequest("pack_apply_cancel");
    }

    // ───────────────────────────────────────────────────────────────
    //  Push (local → server)
    // ───────────────────────────────────────────────────────────────

    JsonObject pushInit(String id, String mode, List<String> targets, List<String> paths) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "push_init");
        if (id != null) request.addProperty("id", id);
        if (mode != null) request.addProperty("mode", mode);
        if (targets != null) {
            JsonArray arr = new JsonArray();
            for (String t : targets) {
                arr.add(t);
            }
            request.add("targets", arr);
        }
        if (paths != null) {
            JsonArray arr = new JsonArray();
            for (String p : paths) {
                arr.add(p);
            }
            request.add("paths", arr);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "push_init");
        return response;
    }

    void pushChunk(String id, byte[] data) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "push_chunk");
        if (id != null) request.addProperty("id", id);
        if (data != null) {
            request.addProperty("data", Base64.getEncoder().encodeToString(data));
        }
        send(request);
    }

    JsonObject pushFinish() throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "push_finish");
        send(request);
        JsonObject response = read();
        requireOk(response, "push_finish");
        return response;
    }

    // ───────────────────────────────────────────────────────────────
    //  File download (server → local)
    // ───────────────────────────────────────────────────────────────

    Path downloadFile(String remotePath, Path outputDir) throws ExternalSyncException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "file_download_init");
        request.addProperty("path", remotePath == null ? "" : remotePath);
        send(request);
        JsonObject init = read();
        if (!bool(init, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(init, "error"), string(init, "message"), "file_download_init failed"));
        }
        String name = firstNonBlank(string(init, "name"), "download.bin");
        Files.createDirectories(outputDir);
        Path target = outputDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(target)) {
            while (true) {
                JsonObject chunk = read();
                String chunkType = string(chunk, "type");
                if ("file_download_finish".equals(chunkType)) {
                    break;
                }
                if (!"file_download_chunk".equals(chunkType)) {
                    continue;
                }
                String data = string(chunk, "data");
                if (data != null && !data.isBlank()) {
                    out.write(Base64.getDecoder().decode(data));
                }
            }
        }
        return target;
    }

    // ───────────────────────────────────────────────────────────────
    //  Config
    // ───────────────────────────────────────────────────────────────

    JsonObject configGet() throws ExternalSyncException {
        return simpleRequest("config_get");
    }

    JsonObject configSet(JsonObject config) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "config_set");
        if (config != null) {
            request.add("config", config);
        }
        send(request);
        JsonObject response = read();
        requireOk(response, "config_set");
        return response;
    }

    // ───────────────────────────────────────────────────────────────
    //  RCON
    // ───────────────────────────────────────────────────────────────

    JsonObject rconInfo() throws ExternalSyncException {
        return simpleRequest("rcon_info");
    }

    JsonObject rconGet() throws ExternalSyncException {
        return simpleRequest("rcon_get");
    }

    JsonObject rconSet(String host, int port, String password) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "rcon_set");
        if (host != null) request.addProperty("host", host);
        request.addProperty("port", port);
        if (password != null) request.addProperty("password", password);
        send(request);
        JsonObject response = read();
        requireOk(response, "rcon_set");
        return response;
    }

    JsonObject rconExec(String command) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "rcon_exec");
        if (command != null) request.addProperty("command", command);
        send(request);
        JsonObject response = read();
        requireOk(response, "rcon_exec");
        return response;
    }

    JsonObject serverRestart() throws ExternalSyncException {
        return simpleRequest("server_restart");
    }

    // ───────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────

    private JsonObject simpleRequest(String type) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", type);
        send(request);
        JsonObject response = read();
        requireOk(response, type);
        return response;
    }

    private void requireOk(JsonObject response, String op) throws ExternalSyncException {
        if (!bool(response, "ok")) {
            throw new ExternalSyncException(firstNonBlank(string(response, "error"), string(response, "message"), op + " failed"));
        }
    }

    private void hello(SyncModels.ServerConfig config) throws ExternalSyncException {
        JsonObject request = new JsonObject();
        request.addProperty("type", "hello");
        request.addProperty("password", config.password == null ? "" : config.password);
        request.addProperty("lang", Locale.getDefault().toLanguageTag().replace('-', '_').toLowerCase(Locale.ROOT));
        if (config.player != null && !config.player.isBlank()) {
            request.addProperty("player", config.player);
        }
        if (config.uuid != null && !config.uuid.isBlank()) {
            request.addProperty("uuid", config.uuid);
        }
        send(request);
        JsonObject response = read();
        if (!"hello".equals(string(response, "type"))) {
            throw new ExternalSyncException("invalid hello response");
        }
        if (!bool(response, "ok")) {
            throw new ExternalSyncException("auth_failed:" + firstNonBlank(string(response, "reason"), string(response, "message"), "unauthorized"));
        }
    }

    private void send(JsonObject value) throws ExternalSyncException {
        try {
            writer.write(GSON.toJson(value));
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            throw new ExternalSyncException("failed to write protocol message", ex);
        }
    }

    private JsonObject read() throws ExternalSyncException {
        try {
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                throw new ExternalSyncException("empty server response");
            }
            JsonElement element = GSON.fromJson(line, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                throw new ExternalSyncException("invalid json response");
            }
            return element.getAsJsonObject();
        } catch (IOException ex) {
            throw new ExternalSyncException("failed to read protocol message", ex);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private static byte[] copy(byte[] source, int length) {
        byte[] out = new byte[length];
        System.arraycopy(source, 0, out, 0, length);
        return out;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static Boolean booleanOrNull(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        Boolean value = booleanOrNull(object, key);
        return value != null && value;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
