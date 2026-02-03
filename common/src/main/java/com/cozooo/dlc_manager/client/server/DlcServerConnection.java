package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DlcServerConnection {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int CHUNK_SIZE = 64 * 1024;

    public static final class ApplyResponse {
        public boolean ok;
        public boolean requiresRestart;
    }

    public static final class UpdateResponse {
        public boolean ok;
        public boolean missing;
        public String message;
        public List<String> missingPaths = List.of();
    }

    public static DlcServerInfo fetchServerInfo(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchServerInfo(entry, timeoutMs, null, null);
    }

    public static DlcServerInfo fetchServerInfo(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject listReq = new JsonObject();
            listReq.addProperty("type", "list");
            writeLine(writer, listReq);
            JsonObject response = readObject(reader);
            if (response == null) {
                return null;
            }
            if (!response.has("type") || !"list".equals(response.get("type").getAsString())) {
                return null;
            }
            DlcServerInfo info = new DlcServerInfo();
            info.baseVersion = getString(response, "base");
            info.dlcVersion = getString(response, "dlc");
            info.remoteMissing = getInt(response, "remoteMissing");
            info.requiredMissing = getInt(response, "requiredMissing");
            info.hasRequired = response.has("hasRequired") && response.get("hasRequired").getAsBoolean();
            if (!info.hasRequired && info.requiredMissing > 0) {
                info.hasRequired = true;
            }
            List<DlcServerItem> items = new ArrayList<>();
            if (response.has("items") && response.get("items").isJsonArray()) {
                response.getAsJsonArray("items").forEach(element -> {
                    if (!element.isJsonObject()) {
                        return;
                    }
                    DlcServerItem item = GSON.fromJson(element, DlcServerItem.class);
                    if (item != null) {
                        items.add(item);
                    }
                });
            }
            info.items = items;
            return info;
        }
    }

    public static boolean downloadMissingRemote(DlcServerEntry entry, int timeoutMs) throws IOException {
        return downloadMissingRemote(entry, timeoutMs, null, null);
    }

    public static boolean downloadMissingRemote(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "remote_download");
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null) {
                return false;
            }
            return response.has("ok") && response.get("ok").getAsBoolean();
        }
    }

    public static List<DlcServerRemoteItem> fetchRemoteMissingList(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "remote_list");
            addLang(request);
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"remote_list".equals(response.get("type").getAsString())) {
                return null;
            }
            List<DlcServerRemoteItem> items = new ArrayList<>();
            if (response.has("items") && response.get("items").isJsonArray()) {
                response.getAsJsonArray("items").forEach(element -> {
                    if (element.isJsonObject()) {
                        DlcServerRemoteItem item = GSON.fromJson(element, DlcServerRemoteItem.class);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                });
            }
            return items;
        }
    }

    public static DlcServerRemoteStatus startRemoteDownload(DlcServerEntry entry, int timeoutMs, List<String> keys, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "remote_download_start");
            addLang(request);
            if (keys != null && !keys.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String key : keys) {
                    if (key != null && !key.isBlank()) {
                        arr.add(key);
                    }
                }
                request.add("keys", arr);
            }
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"remote_download_start".equals(response.get("type").getAsString())) {
                return null;
            }
            if (response.has("status") && response.get("status").isJsonObject()) {
                return GSON.fromJson(response.get("status"), DlcServerRemoteStatus.class);
            }
            return null;
        }
    }

    public static DlcServerRemoteStatus fetchRemoteDownloadStatus(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "remote_download_status");
            addLang(request);
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"remote_download_status".equals(response.get("type").getAsString())) {
                return null;
            }
            if (response.has("status") && response.get("status").isJsonObject()) {
                return GSON.fromJson(response.get("status"), DlcServerRemoteStatus.class);
            }
            return null;
        }
    }

    public static List<DlcServerRequiredItem> fetchRequiredList(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "required_list");
            addLang(request);
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"required_list".equals(response.get("type").getAsString())) {
                return null;
            }
            List<DlcServerRequiredItem> items = new ArrayList<>();
            if (response.has("items") && response.get("items").isJsonArray()) {
                response.getAsJsonArray("items").forEach(element -> {
                    if (element.isJsonObject()) {
                        DlcServerRequiredItem item = GSON.fromJson(element, DlcServerRequiredItem.class);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                });
            }
            return items;
        }
    }

    public static DlcServerRequiredStatus startRequiredDownload(DlcServerEntry entry, int timeoutMs, List<String> ids,
        String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "required_download_start");
            addLang(request);
            if (ids != null && !ids.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        arr.add(id);
                    }
                }
                request.add("ids", arr);
            }
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"required_download_start".equals(response.get("type").getAsString())) {
                return null;
            }
            if (response.has("status") && response.get("status").isJsonObject()) {
                return GSON.fromJson(response.get("status"), DlcServerRequiredStatus.class);
            }
            return null;
        }
    }

    public static DlcServerRequiredStatus fetchRequiredDownloadStatus(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "required_download_status");
            addLang(request);
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"required_download_status".equals(response.get("type").getAsString())) {
                return null;
            }
            if (response.has("status") && response.get("status").isJsonObject()) {
                return GSON.fromJson(response.get("status"), DlcServerRequiredStatus.class);
            }
            return null;
        }
    }

    public static boolean cancelRequiredDownload(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "required_download_cancel");
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"required_download_cancel".equals(response.get("type").getAsString())) {
                return false;
            }
            return response.has("canceled") && response.get("canceled").getAsBoolean();
        }
    }

    public static boolean uninstallRequired(DlcServerEntry entry, int timeoutMs, List<String> ids, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "required_uninstall");
            if (ids != null && !ids.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        arr.add(id);
                    }
                }
                request.add("ids", arr);
            }
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"required_uninstall".equals(response.get("type").getAsString())) {
                return false;
            }
            return response.has("ok") && response.get("ok").getAsBoolean();
        }
    }

    public static boolean applyRequired(DlcServerEntry entry, int timeoutMs, List<String> ids, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "required_apply");
            if (ids != null && !ids.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        arr.add(id);
                    }
                }
                request.add("ids", arr);
            }
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"required_apply".equals(response.get("type").getAsString())) {
                return false;
            }
            return response.has("ok") && response.get("ok").getAsBoolean();
        }
    }

    public static boolean applyChanges(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items) throws IOException {
        ApplyResponse response = applyChangesWithResponse(entry, timeoutMs, items, null, null);
        return response != null && response.ok;
    }

    public static boolean applyChanges(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items, String playerName, String playerUuid) throws IOException {
        ApplyResponse response = applyChangesWithResponse(entry, timeoutMs, items, playerName, playerUuid);
        return response != null && response.ok;
    }

    public static ApplyResponse applyChangesWithResponse(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            if (items != null) {
                for (DlcServerItem item : items) {
                    if (item == null) {
                        continue;
                    }
                    JsonObject setReq = new JsonObject();
                    setReq.addProperty("type", "set");
                    setReq.addProperty("id", item.id);
                    setReq.addProperty("enabled", item.enabled);
                    writeLine(writer, setReq);
                    JsonObject setResp = readObject(reader);
                    if (setResp == null) {
                        return null;
                    }
                }
            }
            JsonObject applyReq = new JsonObject();
            applyReq.addProperty("type", "apply");
            writeLine(writer, applyReq);
            JsonObject applyResp = readObject(reader);
            if (applyResp == null) {
                return null;
            }
            ApplyResponse response = new ApplyResponse();
            response.ok = applyResp.has("ok") && applyResp.get("ok").getAsBoolean();
            response.requiresRestart = applyResp.has("requiresRestart") && applyResp.get("requiresRestart").getAsBoolean();
            return response;
        }
    }

    public static boolean restartServer(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "server_restart");
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            return response != null && response.has("ok") && response.get("ok").getAsBoolean();
        }
    }

    public static boolean downloadFile(DlcServerEntry entry, int timeoutMs, String path, Path target, String playerName, String playerUuid)
        throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject request = new JsonObject();
            request.addProperty("type", "file_download_init");
            request.addProperty("path", path == null ? "" : path);
            writeLine(writer, request);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"file_download_init".equals(response.get("type").getAsString())) {
                return false;
            }
            if (!response.has("ok") || !response.get("ok").getAsBoolean()) {
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
                    JsonObject chunk = readObject(reader);
                    if (chunk == null || !chunk.has("type")) {
                        break;
                    }
                    String type = chunk.get("type").getAsString();
                    if ("file_download_chunk".equals(type)) {
                        String data = chunk.has("data") ? chunk.get("data").getAsString() : "";
                        if (!data.isBlank()) {
                            byte[] decoded = Base64.getDecoder().decode(data);
                            out.write(decoded);
                        }
                    } else if ("file_download_finish".equals(type)) {
                        ok = chunk.has("ok") && chunk.get("ok").getAsBoolean();
                        break;
                    }
                }
            } catch (IOException ignored) {
                ok = false;
            }
            if (ok) {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Best-effort.
            }
            return false;
        }
    }

    public static List<DlcServerUpdateInfo> fetchUpdates(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchUpdates(entry, timeoutMs, null, null);
    }

    public static List<DlcServerUpdateInfo> fetchUpdates(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject listReq = new JsonObject();
            listReq.addProperty("type", "update_list");
            writeLine(writer, listReq);
            JsonObject response = readObject(reader);
            if (response == null) {
                return null;
            }
            if (!response.has("type") || !"update_list".equals(response.get("type").getAsString())) {
                return null;
            }
            List<DlcServerUpdateInfo> updates = new ArrayList<>();
            if (response.has("updates") && response.get("updates").isJsonArray()) {
                response.getAsJsonArray("updates").forEach(element -> {
                    if (!element.isJsonObject()) {
                        return;
                    }
                    DlcServerUpdateInfo info = GSON.fromJson(element, DlcServerUpdateInfo.class);
                    if (info != null) {
                        updates.add(info);
                    }
                });
            }
            return updates;
        }
    }

    public static DlcServerUpdateState fetchUpdateState(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "update_state");
            writeLine(writer, req);
            JsonObject response = readObject(reader);
            if (response == null || !response.has("type") || !"update_state".equals(response.get("type").getAsString())) {
                return null;
            }
            DlcServerUpdateState state = new DlcServerUpdateState();
            state.baseVersion = getString(response, "base");
            state.dlcVersion = getString(response, "dlc");
            state.rollbackFrom = getString(response, "rollbackFrom");
            List<DlcUpdateMeta> history = new ArrayList<>();
            if (response.has("history") && response.get("history").isJsonArray()) {
                response.getAsJsonArray("history").forEach(element -> {
                    if (element.isJsonObject()) {
                        DlcUpdateMeta meta = GSON.fromJson(element, DlcUpdateMeta.class);
                        if (meta != null) {
                            history.add(meta);
                        }
                    }
                });
            }
            state.history = history;
            List<DlcServerUpdateInfo> updates = new ArrayList<>();
            if (response.has("updates") && response.get("updates").isJsonArray()) {
                response.getAsJsonArray("updates").forEach(element -> {
                    if (element.isJsonObject()) {
                        DlcServerUpdateInfo info = GSON.fromJson(element, DlcServerUpdateInfo.class);
                        if (info != null) {
                            updates.add(info);
                        }
                    }
                });
            }
            state.updates = updates;
            return state;
        }
    }

    public static UpdateResponse applyUpdate(DlcServerEntry entry, int timeoutMs, String dlcOld, String dlcNew,
        String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject applyReq = new JsonObject();
            applyReq.addProperty("type", "update_apply");
            if (dlcOld != null && !dlcOld.isBlank()) {
                applyReq.addProperty("dlcOld", dlcOld);
            }
            if (dlcNew != null && !dlcNew.isBlank()) {
                applyReq.addProperty("dlcNew", dlcNew);
            }
            writeLine(writer, applyReq);
            JsonObject response = readObject(reader);
            if (response == null) {
                return null;
            }
            UpdateResponse result = new UpdateResponse();
            result.ok = response.has("ok") && response.get("ok").getAsBoolean();
            result.missing = response.has("missing") && response.get("missing").getAsBoolean();
            result.message = getString(response, "message");
            if (response.has("missing") && response.get("missing").isJsonArray()) {
                List<String> missing = new ArrayList<>();
                response.getAsJsonArray("missing").forEach(element -> {
                    if (element.isJsonPrimitive()) {
                        missing.add(element.getAsString());
                    }
                });
                result.missingPaths = missing;
            }
            return result;
        }
    }

    public static UpdateResponse rollbackUpdate(DlcServerEntry entry, int timeoutMs, String targetVersion,
        String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "update_rollback");
            req.addProperty("target", targetVersion == null ? "" : targetVersion);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            if (resp == null) {
                return null;
            }
            UpdateResponse result = new UpdateResponse();
            result.ok = resp.has("ok") && resp.get("ok").getAsBoolean();
            result.message = getString(resp, "message");
            return result;
        }
    }

    public static boolean importUpdatePackage(DlcServerEntry entry, int timeoutMs, Path source,
        String playerName, String playerUuid) throws IOException {
        if (source == null || !Files.exists(source)) {
            return false;
        }
        JsonObject config = fetchServerConfig(entry, timeoutMs, playerName, playerUuid);
        String dlcRoot = getDlcRootFromConfig(config);
        if (dlcRoot == null || dlcRoot.isBlank()) {
            dlcRoot = "DLC";
        }
        String normalizedRoot = dlcRoot.replace("\\", "/");
        if (!normalizedRoot.endsWith("/")) {
            normalizedRoot = normalizedRoot + "/";
        }
        String targetPath = normalizedRoot + "update/" + source.getFileName();
        if (!uploadFile(entry, timeoutMs, targetPath, source, playerName, playerUuid)) {
            return false;
        }
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "update_import");
            req.addProperty("path", targetPath);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            return resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
        }
    }

    public static boolean applyUpdates(DlcServerEntry entry, int timeoutMs) throws IOException {
        return applyUpdates(entry, timeoutMs, null, null);
    }

    public static boolean applyUpdates(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject applyReq = new JsonObject();
            applyReq.addProperty("type", "update_apply");
            writeLine(writer, applyReq);
            JsonObject response = readObject(reader);
            if (response == null) {
                return false;
            }
            return response.has("ok") && response.get("ok").getAsBoolean();
        }
    }

    public static boolean push(DlcServerEntry entry, int timeoutMs, List<String> targets, String mode, String playerName, String playerUuid) throws IOException {
        return push(entry, timeoutMs, targets, null, mode, playerName, playerUuid);
    }

    public static boolean push(DlcServerEntry entry, int timeoutMs, List<String> targets, List<String> files, String mode, String playerName, String playerUuid) throws IOException {
        Path zipPath = buildPushZip(targets, files);
        if (zipPath == null) {
            return false;
        }
        String transferId = UUID.randomUUID().toString();
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject init = new JsonObject();
            init.addProperty("type", "push_init");
            init.addProperty("id", transferId);
            init.addProperty("mode", mode);
            init.add("targets", GSON.toJsonTree(targets));
            if (files != null && !files.isEmpty()) {
                init.add("paths", GSON.toJsonTree(files));
            }
            init.addProperty("size", Files.size(zipPath));
            writeLine(writer, init);
            JsonObject initResp = readObject(reader);
            if (initResp == null || !initResp.has("ok") || !initResp.get("ok").getAsBoolean()) {
                return false;
            }
            try (InputStream in = Files.newInputStream(zipPath)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    String data = Base64.getEncoder().encodeToString(copyOf(buffer, len));
                    JsonObject chunk = new JsonObject();
                    chunk.addProperty("type", "push_chunk");
                    chunk.addProperty("id", transferId);
                    chunk.addProperty("data", data);
                    writeLine(writer, chunk);
                }
            }
            JsonObject finish = new JsonObject();
            finish.addProperty("type", "push_finish");
            finish.addProperty("id", transferId);
            writeLine(writer, finish);
            JsonObject finishResp = readObject(reader);
            return finishResp != null && finishResp.has("ok") && finishResp.get("ok").getAsBoolean();
        } finally {
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    public static List<DlcServerFileEntry> listFiles(DlcServerEntry entry, int timeoutMs, String path, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "file_list");
            req.addProperty("path", path == null ? "" : path);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            if (resp == null || !resp.has("ok") || !resp.get("ok").getAsBoolean()) {
                return null;
            }
            List<DlcServerFileEntry> entries = new ArrayList<>();
            if (resp.has("entries") && resp.get("entries").isJsonArray()) {
                resp.getAsJsonArray("entries").forEach(element -> {
                    if (!element.isJsonObject()) {
                        return;
                    }
                    DlcServerFileEntry entryObj = GSON.fromJson(element, DlcServerFileEntry.class);
                    if (entryObj != null) {
                        entries.add(entryObj);
                    }
                });
            }
            return entries;
        }
    }

    public static boolean deleteFile(DlcServerEntry entry, int timeoutMs, String path, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "file_delete");
            req.addProperty("path", path == null ? "" : path);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            return resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
        }
    }

    public static boolean uploadFile(DlcServerEntry entry, int timeoutMs, String targetPath, Path source, String playerName, String playerUuid) throws IOException {
        if (source == null || !Files.exists(source)) {
            return false;
        }
        String transferId = UUID.randomUUID().toString();
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject init = new JsonObject();
            init.addProperty("type", "file_upload_init");
            init.addProperty("id", transferId);
            init.addProperty("path", targetPath == null ? "" : targetPath);
            init.addProperty("size", Files.size(source));
            writeLine(writer, init);
            JsonObject initResp = readObject(reader);
            if (initResp == null || !initResp.has("ok") || !initResp.get("ok").getAsBoolean()) {
                return false;
            }
            try (InputStream in = Files.newInputStream(source)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    String data = Base64.getEncoder().encodeToString(copyOf(buffer, len));
                    JsonObject chunk = new JsonObject();
                    chunk.addProperty("type", "file_upload_chunk");
                    chunk.addProperty("id", transferId);
                    chunk.addProperty("data", data);
                    writeLine(writer, chunk);
                }
            }
            JsonObject finish = new JsonObject();
            finish.addProperty("type", "file_upload_finish");
            finish.addProperty("id", transferId);
            writeLine(writer, finish);
            JsonObject finishResp = readObject(reader);
            return finishResp != null && finishResp.has("ok") && finishResp.get("ok").getAsBoolean();
        }
    }

    public static DlcServerRconConfig fetchRconConfig(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "rcon_get");
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            if (resp == null || !resp.has("ok") || !resp.get("ok").getAsBoolean()) {
                return null;
            }
            DlcServerRconConfig config = new DlcServerRconConfig();
            config.host = getString(resp, "host");
            config.port = resp.has("port") ? resp.get("port").getAsInt() : 0;
            config.password = getString(resp, "password");
            return config;
        }
    }

    public static DlcServerRconInfo fetchRconInfo(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "rcon_info");
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            if (resp == null || !resp.has("ok") || !resp.get("ok").getAsBoolean()) {
                return null;
            }
            DlcServerRconInfo info = new DlcServerRconInfo();
            info.enabled = resp.has("enabled") && resp.get("enabled").getAsBoolean();
            info.port = resp.has("port") ? resp.get("port").getAsInt() : 25575;
            info.passwordSet = resp.has("passwordSet") && resp.get("passwordSet").getAsBoolean();
            return info;
        }
    }

    public static boolean saveRconConfig(DlcServerEntry entry, int timeoutMs, DlcServerRconConfig config, String playerName, String playerUuid) throws IOException {
        if (config == null) {
            return false;
        }
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "rcon_set");
            req.addProperty("host", config.host == null ? "" : config.host);
            req.addProperty("port", config.port);
            req.addProperty("password", config.password == null ? "" : config.password);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            return resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
        }
    }

    public static String execRcon(DlcServerEntry entry, int timeoutMs, String command, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "rcon_exec");
            req.addProperty("command", command == null ? "" : command);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            if (resp == null) {
                return null;
            }
            if (resp.has("message")) {
                return resp.get("message").getAsString();
            }
            return resp.has("ok") && resp.get("ok").getAsBoolean() ? "ok" : null;
        }
    }

    public static JsonObject fetchServerConfig(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return null;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "config_get");
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            if (resp == null || !resp.has("ok") || !resp.get("ok").getAsBoolean()) {
                return null;
            }
            if (resp.has("config") && resp.get("config").isJsonObject()) {
                return resp.getAsJsonObject("config");
            }
            return null;
        }
    }

    public static boolean saveServerConfig(DlcServerEntry entry, int timeoutMs, JsonObject config, String playerName, String playerUuid) throws IOException {
        if (config == null) {
            return false;
        }
        try (Socket socket = openSocket(entry, timeoutMs)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!sendHello(entry, reader, writer, playerName, playerUuid)) {
                return false;
            }
            JsonObject req = new JsonObject();
            req.addProperty("type", "config_set");
            req.add("config", config);
            writeLine(writer, req);
            JsonObject resp = readObject(reader);
            return resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
        }
    }

    private static Socket openSocket(DlcServerEntry entry, int timeoutMs) throws IOException {
        String host = entry.host == null ? "" : entry.host;
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, entry.port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    private static boolean sendHello(DlcServerEntry entry, BufferedReader reader, BufferedWriter writer, String playerName, String playerUuid) throws IOException {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        String password = entry.password == null ? "" : entry.password;
        hello.addProperty("password", password);
        addLang(hello);
        if (playerName != null && !playerName.isBlank()) {
            hello.addProperty("player", playerName);
        }
        if (playerUuid != null && !playerUuid.isBlank()) {
            hello.addProperty("uuid", playerUuid);
        }
        writeLine(writer, hello);
        JsonObject response = readObject(reader);
        if (response == null) {
            throw new IOException("no response");
        }
        if (!response.has("type") || !"hello".equals(response.get("type").getAsString())) {
            throw new IOException("invalid response");
        }
        boolean ok = response.has("ok") && response.get("ok").getAsBoolean();
        if (!ok) {
            String reason = response.has("reason") ? response.get("reason").getAsString() : "unauthorized";
            throw new IOException("auth_failed:" + reason);
        }
        return true;
    }

    private static void addLang(JsonObject obj) {
        if (obj == null) {
            return;
        }
        String lang = PlatformServices.get().languageCode();
        if (lang != null && !lang.isBlank()) {
            obj.addProperty("lang", lang);
        }
    }

    private static JsonObject readObject(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.isBlank()) {
            return null;
        }
        return GSON.fromJson(line, JsonObject.class);
    }

    private static void writeLine(BufferedWriter writer, JsonObject obj) throws IOException {
        writer.write(GSON.toJson(obj));
        writer.newLine();
        writer.flush();
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        if (!obj.has(key)) {
            return null;
        }
        if (obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static int getInt(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return 0;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String getDlcRootFromConfig(JsonObject config) {
        if (config == null) {
            return "";
        }
        JsonObject paths = JsonCaseUtil.getObjectIgnoreCase(config, "paths");
        if (paths == null) {
            return "";
        }
        return JsonCaseUtil.getStringIgnoreCase(paths, "dlcRoot");
    }

    private static byte[] copyOf(byte[] input, int len) {
        byte[] out = new byte[len];
        System.arraycopy(input, 0, out, 0, len);
        return out;
    }

    private static Path buildPushZip(List<String> targets, List<String> files) throws IOException {
        boolean hasTargets = targets != null && !targets.isEmpty();
        boolean hasFiles = files != null && !files.isEmpty();
        if (!hasTargets && !hasFiles) {
            return null;
        }
        boolean includeAll = hasTargets && targets.stream().anyMatch(value -> "all".equalsIgnoreCase(value));
        boolean includeDlc = includeAll || (hasTargets && targets.stream().anyMatch(value -> "dlc".equalsIgnoreCase(value)));
        boolean includeConfig = includeAll || (hasTargets && targets.stream().anyMatch(value -> "config".equalsIgnoreCase(value)));
        Path dlcRoot = DlcPathConfig.getDlcRoot();
        Path configDir = PlatformServices.get().configDir().resolve("dlc_manager");
        Path gameDir = DlcPaths.gameDir();
        Path tempDir = dlcRoot.resolve("update_tmp").resolve("server_ops");
        Files.createDirectories(tempDir);
        Path zipPath = tempDir.resolve("push_" + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            if (includeDlc && Files.exists(dlcRoot)) {
                zipDirectory(dlcRoot, out, "dlc/", "update_tmp");
            }
            if (includeConfig && Files.exists(configDir)) {
                zipDirectory(configDir, out, "config/", null);
            }
            if (hasFiles && Files.exists(gameDir)) {
                zipSelectedFiles(gameDir, files, out, "root/");
            }
        }
        return zipPath;
    }

    private static void zipDirectory(Path root, ZipOutputStream out, String prefix, String skipDirName) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        return;
                    }
                    Path relative = root.relativize(path);
                    if (relative.toString().isBlank()) {
                        return;
                    }
                    if (skipDirName != null && relative.getNameCount() > 0) {
                        String first = relative.getName(0).toString();
                        if (skipDirName.equalsIgnoreCase(first)) {
                            return;
                        }
                    }
                    String entryName = prefix + relative.toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    out.putNextEntry(entry);
                    try (InputStream in = Files.newInputStream(path)) {
                        byte[] buffer = new byte[CHUNK_SIZE];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                    out.closeEntry();
                } catch (IOException ignored) {
                    // Best-effort.
                }
            });
        }
    }

    private static void zipSelectedFiles(Path root, List<String> files, ZipOutputStream out, String prefix) throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (String raw : files) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Path target = root.resolve(raw).normalize();
            if (!target.startsWith(root)) {
                continue;
            }
            if (!Files.exists(target)) {
                continue;
            }
            if (Files.isDirectory(target)) {
                zipDirectory(target, out, prefix + root.relativize(target).toString().replace('\\', '/') + "/", null);
                continue;
            }
            zipFile(root, target, out, prefix);
        }
    }

    private static void zipFile(Path root, Path file, ZipOutputStream out, String prefix) throws IOException {
        Path relative = root.relativize(file);
        if (relative.toString().isBlank()) {
            return;
        }
        String entryName = prefix + relative.toString().replace('\\', '/');
        ZipEntry entry = new ZipEntry(entryName);
        out.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        out.closeEntry();
    }
}
