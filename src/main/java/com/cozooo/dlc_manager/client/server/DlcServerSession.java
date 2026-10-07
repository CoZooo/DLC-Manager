package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DlcServerSession implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int CHUNK_SIZE = 64 * 1024;

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    private DlcServerSession(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public static DlcServerSession connect(DlcServerEntry entry, int timeoutMs) throws IOException {
        return connect(entry, timeoutMs, connection -> {});
    }

    public static DlcServerSession connect(DlcServerEntry entry, int timeoutMs,
                                           Consumer<AutoCloseable> connectionOpened) throws IOException {
        if (entry == null) {
            throw new IOException("missing server entry");
        }
        return connect(entry.host, entry.port, entry.password, timeoutMs,
                DlcPlatformServices.languageCode(), DlcPlatformServices.playerName(), DlcPlatformServices.playerUuid(),
                connectionOpened);
    }

    public static DlcServerSession connect(String host, int port, String password, int timeoutMs,
                                           String lang, String player, String uuid) throws IOException {
        return connect(host, port, password, timeoutMs, lang, player, uuid, connection -> {});
    }

    private static DlcServerSession connect(String host, int port, String password, int timeoutMs,
                                             String lang, String player, String uuid,
                                             Consumer<AutoCloseable> connectionOpened) throws IOException {
        Socket socket = new Socket();
        try {
            if (connectionOpened != null) {
                connectionOpened.accept(socket);
            }
            String actualHost = host == null ? "" : host;
            socket.connect(new InetSocketAddress(actualHost, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DlcServerSession session = new DlcServerSession(socket);
            session.hello(password, lang, player, uuid);
            return session;
        } catch (IOException | RuntimeException error) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw error;
        }
    }

    public synchronized DlcServerInfo fetchList() throws IOException {
        JsonObject response = request("list", null);
        if (!"list".equals(string(response, "type"))) {
            throw new IOException("invalid list response");
        }
        DlcServerInfo info = new DlcServerInfo();
        info.baseVersion = string(response, "base");
        info.dlcVersion = string(response, "dlc");
        info.mcVersion = string(response, "mcVersion");
        info.loaderName = string(response, "loaderName");
        info.remoteMissing = intValue(response, "remoteMissing");
        info.requiredMissing = intValue(response, "requiredMissing");
        info.hasRequired = bool(response, "hasRequired");
        info.hasTaczMod = bool(response, "hasTaczMod");
        info.modpackUrl = string(response, "modpackUrl");
        info.updateGitRepo = firstNonBlank(string(response, "updateGitRepo"), string(response, "updateGithubRepo"));
        if (!info.hasRequired && info.requiredMissing > 0) {
            info.hasRequired = true;
        }
        List<DlcServerRedirectItem> redirects = new ArrayList<>();
        JsonArray redirectArray = array(response, "redirects");
        if (redirectArray != null) {
            for (JsonElement element : redirectArray) {
                if (element != null && element.isJsonObject()) {
                    DlcServerRedirectItem item = GSON.fromJson(element, DlcServerRedirectItem.class);
                    if (item != null) {
                        redirects.add(item);
                    }
                }
            }
        }
        info.redirects = redirects;
        List<DlcServerPresetItem> presets = new ArrayList<>();
        JsonArray presetArray = array(response, "presets");
        if (presetArray != null) {
            for (JsonElement element : presetArray) {
                if (element != null && element.isJsonObject()) {
                    DlcServerPresetItem item = GSON.fromJson(element, DlcServerPresetItem.class);
                    if (item != null) {
                        presets.add(item);
                    }
                }
            }
        }
        info.presets = presets;
        List<DlcServerItem> items = new ArrayList<>();
        JsonArray array = array(response, "items");
        if (array != null) {
            for (JsonElement element : array) {
                if (element != null && element.isJsonObject()) {
                    DlcServerItem item = GSON.fromJson(element, DlcServerItem.class);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }
        info.items = items;
        return info;
    }

    public List<DlcServerFileEntry> listFiles(String path) throws IOException {
        return listFiles(path, false);
    }

    public synchronized List<DlcServerFileEntry> listFiles(String path, boolean includeHash) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("path", path == null ? "" : path);
        if (includeHash) {
            payload.addProperty("includeHash", true);
        }
        JsonObject response = rawRequest("file_list", payload);
        requireOk(response, "file_list");
        List<DlcServerFileEntry> entries = new ArrayList<>();
        JsonArray array = array(response, "entries");
        if (array == null) {
            return entries;
        }
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            DlcServerFileEntry entry = GSON.fromJson(element, DlcServerFileEntry.class);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public synchronized void uploadFile(String remotePath, Path localPath) throws IOException {
        uploadFile(remotePath, localPath, () -> false);
    }

    public synchronized void uploadFile(String remotePath, Path localPath, BooleanSupplier canceled) throws IOException {
        checkCanceled(canceled);
        if (localPath == null || !Files.isRegularFile(localPath)) {
            throw new IOException("missing local file: " + localPath);
        }
        String transferId = UUID.randomUUID().toString();
        JsonObject init = new JsonObject();
        init.addProperty("id", transferId);
        init.addProperty("path", remotePath == null ? "" : remotePath);
        init.addProperty("size", Files.size(localPath));
        requireOk(rawRequest("file_upload_init", init), "file_upload_init");
        byte[] buffer = new byte[CHUNK_SIZE];
        try (InputStream input = Files.newInputStream(localPath)) {
            while (true) {
                checkCanceled(canceled);
                int bytesRead = input.read(buffer);
                if (bytesRead < 0) {
                    break;
                }
                JsonObject chunk = new JsonObject();
                chunk.addProperty("id", transferId);
                chunk.addProperty("data", Base64.getEncoder().encodeToString(copy(buffer, bytesRead)));
                send(withType("file_upload_chunk", chunk));
            }
        }
        JsonObject finish = new JsonObject();
        finish.addProperty("id", transferId);
        checkCanceled(canceled);
        requireOk(rawRequest("file_upload_finish", finish), "file_upload_finish");
    }

    public synchronized void deleteFile(String remotePath) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("path", remotePath == null ? "" : remotePath);
        requireOk(rawRequest("file_delete", payload), "file_delete");
    }

    public synchronized void setEnabled(String id, boolean enabled) throws IOException {
        if (id == null || id.isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("id", id);
        payload.addProperty("enabled", enabled);
        requireOk(rawRequest("set", payload), "set");
    }

    public DlcServerConnection.ApplyResponse apply() throws IOException {
        return applyWithMessages();
    }

    public synchronized DlcServerConnection.ApplyResponse applyWithMessages() throws IOException {
        JsonObject response = rawRequest("apply", null);
        requireOk(response, "apply");
        DlcServerConnection.ApplyResponse result = new DlcServerConnection.ApplyResponse();
        result.ok = true;
        result.requiresRestart = bool(response, "requiresRestart");
        result.messages = stringList(response, "messages");
        return result;
    }

    public JsonObject remoteList() throws IOException { return checked("remote_list"); }
    public JsonObject remoteDownload() throws IOException { return rawRequest("remote_download", null); }
    public JsonObject remoteDownloadStart(List<String> keys) throws IOException { return checkedWithStrings("remote_download_start", "keys", keys); }
    public JsonObject remoteDownloadStatus() throws IOException { return checked("remote_download_status"); }
    public JsonObject remoteDownloadCancel() throws IOException { return checked("remote_download_cancel"); }
    public JsonObject requiredList() throws IOException { return checked("required_list"); }
    public JsonObject requiredDownloadStart(List<String> ids) throws IOException { return checkedWithStrings("required_download_start", "ids", ids); }
    public JsonObject requiredDownloadStatus() throws IOException { return checked("required_download_status"); }
    public JsonObject requiredDownloadCancel() throws IOException { return checked("required_download_cancel"); }
    public JsonObject requiredUninstall(List<String> ids) throws IOException { return checkedWithStrings("required_uninstall", "ids", ids); }
    public JsonObject requiredApply(List<String> ids) throws IOException {
        return requiredApply(ids, false);
    }

    public synchronized JsonObject requiredApply(List<String> ids, boolean replaceExisting) throws IOException {
        JsonObject payload = new JsonObject();
        payload.add("ids", GSON.toJsonTree(ids == null ? List.of() : ids));
        payload.addProperty("replaceExisting", replaceExisting);
        return checked("required_apply", payload);
    }
    public JsonObject updateState() throws IOException { return checked("update_state"); }
    public JsonObject updateList() throws IOException { return checked("update_list"); }
    public JsonObject updateApply(String baseOld, String dlcOld, String baseNew, String dlcNew) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "baseOld", baseOld);
        addIfNotBlank(payload, "dlcOld", dlcOld);
        addIfNotBlank(payload, "baseNew", baseNew);
        addIfNotBlank(payload, "dlcNew", dlcNew);
        JsonObject response = rawRequest("update_apply", payload);
        if (!bool(response, "ok") && !bool(response, "missing")) {
            requireOk(response, "update_apply");
        }
        return response;
    }
    public JsonObject updateRollback(String target) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "target", target);
        return checked("update_rollback", payload);
    }
    public JsonObject updateImport(String path) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "path", path);
        return checked("update_import", payload);
    }
    public JsonObject updateImportUrl(String url, String fileName) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "url", url);
        addIfNotBlank(payload, "fileName", fileName);
        return checked("update_import_url", payload);
    }
    public JsonObject packList() throws IOException { return checked("pack_list"); }
    public JsonObject packInstall(String archivePath) throws IOException { return checkedWithString("pack_install", "archivePath", archivePath); }
    public JsonObject packUninstall(String identifier) throws IOException { return checkedWithString("pack_uninstall", "identifier", identifier); }
    public JsonObject packDelete(String archivePath) throws IOException { return checkedWithString("pack_delete", "archivePath", archivePath); }
    public JsonObject packImport(String path) throws IOException { return checkedWithString("pack_import", "path", path); }
    public JsonObject packImportUrl(String url, String fileName) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "url", url);
        addIfNotBlank(payload, "fileName", fileName);
        return checked("pack_import_url", payload);
    }
    public JsonObject packOrder(List<String> order) throws IOException { return checkedWithStrings("pack_order", "order", order); }
    public JsonObject packApplyStart() throws IOException { return packApplyStart(null, null, null); }
    public JsonObject packApplyStart(List<String> order, List<String> uninstallIdentifiers, List<String> installArchives) throws IOException {
        JsonObject payload = new JsonObject();
        payload.add("order", GSON.toJsonTree(order == null ? List.of() : order));
        payload.add("uninstallIdentifiers", GSON.toJsonTree(uninstallIdentifiers == null ? List.of() : uninstallIdentifiers));
        payload.add("installArchives", GSON.toJsonTree(installArchives == null ? List.of() : installArchives));
        return checked("pack_apply_start", payload);
    }
    public JsonObject packApplyStatus() throws IOException { return checked("pack_apply_status"); }
    public JsonObject packApplyPause() throws IOException { return checked("pack_apply_pause"); }
    public JsonObject packApplyResume() throws IOException { return checked("pack_apply_resume"); }
    public JsonObject packApplyCancel() throws IOException { return checked("pack_apply_cancel"); }
    public JsonObject configGet() throws IOException { return checked("config_get"); }
    public JsonObject configSet(JsonObject config) throws IOException {
        JsonObject payload = new JsonObject();
        if (config != null) {
            payload.add("config", config);
        }
        return checked("config_set", payload);
    }
    public JsonObject rconInfo() throws IOException { return checked("rcon_info"); }
    public JsonObject rconGet() throws IOException { return checked("rcon_get"); }
    public JsonObject rconSet(String host, int port, String password) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "host", host);
        payload.addProperty("port", port);
        if (password != null) {
            payload.addProperty("password", password);
        }
        return checked("rcon_set", payload);
    }
    public JsonObject rconExec(String command) throws IOException { return checkedWithString("rcon_exec", "command", command); }
    public JsonObject serverRestart() throws IOException { return checked("server_restart"); }

    public synchronized JsonObject pushInit(String id, String mode, List<String> targets, List<String> paths) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "id", id);
        addIfNotBlank(payload, "mode", mode);
        if (targets != null) {
            payload.add("targets", GSON.toJsonTree(targets));
        }
        if (paths != null) {
            payload.add("paths", GSON.toJsonTree(paths));
        }
        return checked("push_init", payload);
    }

    public synchronized void pushChunk(String id, byte[] data) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, "id", id);
        if (data != null) {
            payload.addProperty("data", Base64.getEncoder().encodeToString(data));
        }
        send(withType("push_chunk", payload));
    }

    public synchronized JsonObject pushFinish() throws IOException {
        return checked("push_finish");
    }

    public synchronized Path downloadFile(String remotePath, Path outputDir) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("path", remotePath == null ? "" : remotePath);
        JsonObject init = rawRequest("file_download_init", payload);
        requireOk(init, "file_download_init");
        String name = firstNonBlank(string(init, "name"), "download.bin");
        Files.createDirectories(outputDir);
        Path target = outputDir.resolve(name);
        Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
        boolean ok = false;
        try (OutputStream out = Files.newOutputStream(temp)) {
            while (true) {
                JsonObject chunk = readObject();
                String type = string(chunk, "type");
                if ("file_download_chunk".equals(type)) {
                    String data = string(chunk, "data");
                    if (data != null && !data.isBlank()) {
                        out.write(Base64.getDecoder().decode(data));
                    }
                } else if ("file_download_finish".equals(type)) {
                    ok = bool(chunk, "ok");
                    break;
                }
            }
        } catch (IOException error) {
            Files.deleteIfExists(temp);
            throw error;
        }
        if (!ok) {
            Files.deleteIfExists(temp);
            throw new IOException("file_download_finish failed");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public synchronized boolean downloadFileTo(String remotePath, Path target) throws IOException {
        return downloadFileTo(remotePath, target, () -> false);
    }

    public synchronized boolean downloadFileTo(String remotePath, Path target, BooleanSupplier canceled) throws IOException {
        checkCanceled(canceled);
        if (target == null) {
            return false;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("path", remotePath == null ? "" : remotePath);
        JsonObject init = rawRequest("file_download_init", payload);
        if (!"file_download_init".equals(string(init, "type")) || !bool(init, "ok")) {
            return false;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
        boolean ok = false;
        try (OutputStream out = Files.newOutputStream(temp)) {
            while (true) {
                checkCanceled(canceled);
                JsonObject chunk = readObject();
                checkCanceled(canceled);
                String type = string(chunk, "type");
                if ("file_download_chunk".equals(type)) {
                    String data = string(chunk, "data");
                    if (data != null && !data.isBlank()) {
                        out.write(Base64.getDecoder().decode(data));
                    }
                } else if ("file_download_finish".equals(type)) {
                    ok = bool(chunk, "ok");
                    break;
                }
            }
        } catch (IOException error) {
            Files.deleteIfExists(temp);
            throw error;
        }
        if (!ok) {
            Files.deleteIfExists(temp);
            return false;
        }
        try {
            checkCanceled(canceled);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void checkCanceled(BooleanSupplier canceled) throws IOException {
        if (Thread.currentThread().isInterrupted() || (canceled != null && canceled.getAsBoolean())) {
            close();
            throw new InterruptedIOException("canceled");
        }
    }

    public synchronized boolean pushZip(String id, String mode, List<String> targets, List<String> paths, Path zipPath) throws IOException {
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            return false;
        }
        JsonObject init = new JsonObject();
        addIfNotBlank(init, "id", id);
        addIfNotBlank(init, "mode", mode);
        init.add("targets", GSON.toJsonTree(targets));
        if (paths != null && !paths.isEmpty()) {
            init.add("paths", GSON.toJsonTree(paths));
        }
        init.addProperty("size", Files.size(zipPath));
        JsonObject initResponse = rawRequest("push_init", init);
        if (!bool(initResponse, "ok")) {
            return false;
        }
        try (InputStream input = Files.newInputStream(zipPath)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int len;
            while ((len = input.read(buffer)) > 0) {
                pushChunk(id, copy(buffer, len));
            }
        }
        JsonObject finish = new JsonObject();
        addIfNotBlank(finish, "id", id);
        JsonObject finishResponse = rawRequest("push_finish", finish);
        return bool(finishResponse, "ok");
    }

    public synchronized JsonObject rawRequest(String type, JsonObject payload) throws IOException {
        send(withType(type, payload));
        return readObject();
    }

    private JsonObject checked(String type) throws IOException {
        return checked(type, null);
    }

    private JsonObject checked(String type, JsonObject payload) throws IOException {
        JsonObject response = rawRequest(type, payload);
        requireOk(response, type);
        return response;
    }

    private JsonObject checkedWithString(String type, String key, String value) throws IOException {
        JsonObject payload = new JsonObject();
        addIfNotBlank(payload, key, value);
        return checked(type, payload);
    }

    private JsonObject checkedWithStrings(String type, String key, List<String> values) throws IOException {
        JsonObject payload = new JsonObject();
        if (values != null) {
            payload.add(key, GSON.toJsonTree(values));
        }
        return checked(type, payload);
    }

    private synchronized JsonObject request(String type, JsonObject payload) throws IOException {
        send(withType(type, payload));
        return readObject();
    }

    private void hello(String password, String lang, String player, String uuid) throws IOException {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("password", password == null ? "" : password);
        addIfNotBlank(hello, "lang", lang);
        addIfNotBlank(hello, "player", player);
        addIfNotBlank(hello, "uuid", uuid);
        send(hello);
        JsonObject response = readObject();
        if (!"hello".equals(string(response, "type"))) {
            throw new IOException("invalid response");
        }
        if (!bool(response, "ok")) {
            throw new IOException("auth_failed:" + firstNonBlank(string(response, "reason"), string(response, "message"), "unauthorized"));
        }
    }

    private void send(JsonObject value) throws IOException {
        writer.write(GSON.toJson(value));
        writer.newLine();
        writer.flush();
    }

    private JsonObject readObject() throws IOException {
        String line = reader.readLine();
        if (line == null || line.isBlank()) {
            throw new IOException("empty server response");
        }
        JsonElement element = GSON.fromJson(line, JsonElement.class);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("invalid json response");
        }
        return element.getAsJsonObject();
    }

    public static void requireOk(JsonObject response, String operation) throws IOException {
        if (!bool(response, "ok")) {
            throw new IOException(firstNonBlank(string(response, "error"), string(response, "message"), operation + " failed"));
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private static JsonObject withType(String type, JsonObject payload) {
        JsonObject out = payload == null ? new JsonObject() : payload;
        out.addProperty("type", type);
        return out;
    }

    private static void addIfNotBlank(JsonObject object, String key, String value) {
        if (object != null && key != null && value != null && !value.isBlank()) {
            object.addProperty(key, value);
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static List<String> stringList(JsonObject object, String key) {
        JsonArray array = array(object, key);
        if (array == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static int intValue(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static byte[] copy(byte[] source, int length) {
        byte[] out = new byte[length];
        System.arraycopy(source, 0, out, 0, length);
        return out;
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
