package com.cozooo.dlc_manager.client.server;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        public List<String> messages = List.of();
    }

    public static final class UpdateResponse {
        public boolean ok;
        public boolean missing;
        public String message;
        public List<String> missingPaths = List.of();
    }

    public static final class PackListResponse {
        public List<DlcServerPackInfo> packs = List.of();
        public List<String> order = List.of();
    }

    public static DlcServerInfo fetchServerInfo(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchServerInfo(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerInfo fetchServerInfo(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return session.fetchList();
            }
        });
    }

    public static boolean downloadMissingRemote(DlcServerEntry entry, int timeoutMs) throws IOException {
        return downloadMissingRemote(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean downloadMissingRemote(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.remoteDownload();
                return isOk(response);
            }
        });
    }

    public static List<DlcServerRemoteItem> fetchRemoteMissingList(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.remoteList();
                if (!hasType(response, "remote_list")) {
                    return null;
                }
                List<DlcServerRemoteItem> items = new ArrayList<>();
                if (response.has("items") && response.get("items").isJsonArray()) {
                    response.getAsJsonArray("items").forEach(element -> {
                        if (element.isJsonObject()) {
                            DlcServerRemoteItem item = GSON.fromJson(element, DlcServerRemoteItem.class);
                            if (item != null && !isRemotePresent(item)) {
                                items.add(item);
                            }
                        }
                    });
                }
                return items;
            }
        });
    }

    public static List<DlcServerRemoteItem> fetchRemoteMissingList(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchRemoteMissingList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    private static boolean isRemotePresent(DlcServerRemoteItem item) {
        return item != null && item.done && item.success;
    }

    public static DlcServerRemoteStatus startRemoteDownload(DlcServerEntry entry, int timeoutMs, List<String> keys, String playerName, String playerUuid)
        throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.remoteDownloadStart(keys);
                if (!hasType(response, "remote_download_start")) {
                    return null;
                }
                if (response.has("status") && response.get("status").isJsonObject()) {
                    return GSON.fromJson(response.get("status"), DlcServerRemoteStatus.class);
                }
                return null;
            }
        });
    }

    public static DlcServerRemoteStatus startRemoteDownload(DlcServerEntry entry, int timeoutMs, List<String> keys) throws IOException {
        return startRemoteDownload(entry, timeoutMs, keys, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerRemoteStatus fetchRemoteDownloadStatus(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.remoteDownloadStatus();
                if (!hasType(response, "remote_download_status")) {
                    return null;
                }
                if (response.has("status") && response.get("status").isJsonObject()) {
                    return GSON.fromJson(response.get("status"), DlcServerRemoteStatus.class);
                }
                return null;
            }
        });
    }

    public static DlcServerRemoteStatus fetchRemoteDownloadStatus(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchRemoteDownloadStatus(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static List<DlcServerRequiredItem> fetchRequiredList(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.requiredList();
                if (!hasType(response, "required_list")) {
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
        });
    }

    public static List<DlcServerRequiredItem> fetchRequiredList(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchRequiredList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerRequiredStatus startRequiredDownload(DlcServerEntry entry, int timeoutMs, List<String> ids,
        String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.requiredDownloadStart(ids);
                if (!hasType(response, "required_download_start")) {
                    return null;
                }
                if (response.has("status") && response.get("status").isJsonObject()) {
                    return GSON.fromJson(response.get("status"), DlcServerRequiredStatus.class);
                }
                return null;
            }
        });
    }

    public static DlcServerRequiredStatus startRequiredDownload(DlcServerEntry entry, int timeoutMs, List<String> ids) throws IOException {
        return startRequiredDownload(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerRequiredStatus fetchRequiredDownloadStatus(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.requiredDownloadStatus();
                if (!hasType(response, "required_download_status")) {
                    return null;
                }
                if (response.has("status") && response.get("status").isJsonObject()) {
                    return GSON.fromJson(response.get("status"), DlcServerRequiredStatus.class);
                }
                return null;
            }
        });
    }

    public static DlcServerRequiredStatus fetchRequiredDownloadStatus(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchRequiredDownloadStatus(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean cancelRequiredDownload(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid)
        throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.requiredDownloadCancel();
                if (!hasType(response, "required_download_cancel")) {
                    return false;
                }
                return response.has("canceled") && response.get("canceled").getAsBoolean();
            }
        });
    }

    public static boolean cancelRequiredDownload(DlcServerEntry entry, int timeoutMs) throws IOException {
        return cancelRequiredDownload(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean uninstallRequired(DlcServerEntry entry, int timeoutMs, List<String> ids, String playerName, String playerUuid)
        throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.requiredUninstall(ids);
                if (!hasType(response, "required_uninstall")) {
                    return false;
                }
                return isOk(response);
            }
        });
    }

    public static boolean uninstallRequired(DlcServerEntry entry, int timeoutMs, List<String> ids) throws IOException {
        return uninstallRequired(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean applyRequired(DlcServerEntry entry, int timeoutMs, List<String> ids, String playerName, String playerUuid)
        throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.requiredApply(ids);
                if (!hasType(response, "required_apply")) {
                    return false;
                }
                return isOk(response);
            }
        });
    }

    public static boolean applyRequired(DlcServerEntry entry, int timeoutMs, List<String> ids) throws IOException {
        return applyRequired(entry, timeoutMs, ids, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean applyChanges(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items) throws IOException {
        ApplyResponse response = applyChangesWithResponse(entry, timeoutMs, items, currentPlayerName(), currentPlayerUuid());
        return response != null && response.ok;
    }

    public static boolean applyChanges(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items, String playerName, String playerUuid) throws IOException {
        ApplyResponse response = applyChangesWithResponse(entry, timeoutMs, items, playerName, playerUuid);
        return response != null && response.ok;
    }

    public static ApplyResponse applyChangesWithResponse(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items, String playerName, String playerUuid)
        throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                if (items != null) {
                    for (DlcServerItem item : items) {
                        if (item == null) {
                            continue;
                        }
                        session.setEnabled(item.id, item.enabled);
                    }
                }
                return session.applyWithMessages();
            }
        });
    }

    public static ApplyResponse applyChangesWithResponse(DlcServerEntry entry, int timeoutMs, List<DlcServerItem> items) throws IOException {
        return applyChangesWithResponse(entry, timeoutMs, items, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean restartServer(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                session.serverRestart();
                return true;
            }
        });
    }

    public static boolean restartServer(DlcServerEntry entry, int timeoutMs) throws IOException {
        return restartServer(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean downloadFile(DlcServerEntry entry, int timeoutMs, String path, Path target, String playerName, String playerUuid)
        throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return session.downloadFileTo(path, target);
            }
        });
    }

    public static boolean downloadFile(DlcServerEntry entry, int timeoutMs, String path, Path target) throws IOException {
        return downloadFile(entry, timeoutMs, path, target, currentPlayerName(), currentPlayerUuid());
    }

    public static List<DlcServerUpdateInfo> fetchUpdates(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchUpdates(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static List<DlcServerUpdateInfo> fetchUpdates(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.updateList();
                if (!hasType(response, "update_list")) {
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
        });
    }

    public static DlcServerUpdateState fetchUpdateState(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.updateState();
                if (!hasType(response, "update_state")) {
                    return null;
                }
                DlcServerUpdateState state = new DlcServerUpdateState();
                state.baseVersion = getString(response, "base");
                state.dlcVersion = getString(response, "dlc");
                state.rollbackFrom = getString(response, "rollbackFrom");
                state.modpackUrl = getString(response, "modpackUrl");
                state.updateGitRepo = getString(response, "updateGitRepo");
                if (state.updateGitRepo == null) {
                    state.updateGitRepo = getString(response, "updateGithubRepo");
                }
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
        });
    }

    public static DlcServerUpdateState fetchUpdateState(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchUpdateState(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static UpdateResponse applyUpdate(DlcServerEntry entry, int timeoutMs,
        String baseOld, String dlcOld, String baseNew, String dlcNew,
        String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.updateApply(baseOld, dlcOld, baseNew, dlcNew);
                return readUpdateResponse(response);
            }
        });
    }

    public static UpdateResponse applyUpdate(DlcServerEntry entry, int timeoutMs, String baseOld, String dlcOld, String baseNew, String dlcNew) throws IOException {
        return applyUpdate(entry, timeoutMs, baseOld, dlcOld, baseNew, dlcNew, currentPlayerName(), currentPlayerUuid());
    }

    public static UpdateResponse rollbackUpdate(DlcServerEntry entry, int timeoutMs, String targetVersion,
        String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject payload = new JsonObject();
                payload.addProperty("target", targetVersion == null ? "" : targetVersion);
                return readUpdateResponse(session.rawRequest("update_rollback", payload));
            }
        });
    }

    public static UpdateResponse rollbackUpdate(DlcServerEntry entry, int timeoutMs, String targetVersion) throws IOException {
        return rollbackUpdate(entry, timeoutMs, targetVersion, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean importUpdatePackage(DlcServerEntry entry, int timeoutMs, Path source,
        String playerName, String playerUuid) throws IOException {
        if (source == null || !Files.exists(source)) {
            return false;
        }
        return falseOnIo(() -> {
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
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject payload = new JsonObject();
                payload.addProperty("path", targetPath);
                return isOk(session.rawRequest("update_import", payload));
            }
        });
    }

    public static boolean importUpdatePackage(DlcServerEntry entry, int timeoutMs, Path source) throws IOException {
        return importUpdatePackage(entry, timeoutMs, source, currentPlayerName(), currentPlayerUuid());
    }

    public static List<DlcServerPackInfo> fetchPacks(DlcServerEntry entry, int timeoutMs) throws IOException {
        PackListResponse response = fetchPackList(entry, timeoutMs);
        return response == null ? null : response.packs;
    }

    public static List<DlcServerPackInfo> fetchPacks(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        PackListResponse response = fetchPackList(entry, timeoutMs, playerName, playerUuid);
        return response == null ? null : response.packs;
    }

    public static PackListResponse fetchPackList(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchPackList(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static PackListResponse fetchPackList(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.packList();
                if (!hasType(response, "pack_list")) {
                    return null;
                }
                List<DlcServerPackInfo> packs = new ArrayList<>();
                if (response.has("packs") && response.get("packs").isJsonArray()) {
                    response.getAsJsonArray("packs").forEach(element -> {
                        if (element.isJsonObject()) {
                            DlcServerPackInfo info = GSON.fromJson(element, DlcServerPackInfo.class);
                            if (info != null) {
                                packs.add(info);
                            }
                        }
                    });
                }
                List<String> order = new ArrayList<>();
                if (response.has("order") && response.get("order").isJsonArray()) {
                    response.getAsJsonArray("order").forEach(element -> {
                        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                            order.add(element.getAsString());
                        }
                    });
                }
                PackListResponse result = new PackListResponse();
                result.packs = packs;
                result.order = order;
                return result;
            }
        });
    }

    public static UpdateResponse savePackOrder(DlcServerEntry entry, int timeoutMs, List<String> order) throws IOException {
        return savePackOrder(entry, timeoutMs, order, currentPlayerName(), currentPlayerUuid());
    }

    public static UpdateResponse savePackOrder(DlcServerEntry entry, int timeoutMs, List<String> order, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return readUpdateResponse(session.packOrder(order));
            }
        });
    }

    public static UpdateResponse installPack(DlcServerEntry entry, int timeoutMs, String archivePath) throws IOException {
        return packAction(entry, timeoutMs, "pack_install", archivePath, null, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerPackApplyStatus startPackApply(DlcServerEntry entry, int timeoutMs, List<String> order,
                                                          List<String> uninstallIdentifiers, List<String> installArchives) throws IOException {
        return startPackApply(entry, timeoutMs, order, uninstallIdentifiers, installArchives, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerPackApplyStatus startPackApply(DlcServerEntry entry, int timeoutMs, List<String> order,
                                                          List<String> uninstallIdentifiers, List<String> installArchives,
                                                          String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return readPackApplyStatus(session.packApplyStart(order, uninstallIdentifiers, installArchives));
            }
        });
    }

    public static DlcServerPackApplyStatus fetchPackApplyStatus(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchPackApplyStatus(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerPackApplyStatus fetchPackApplyStatus(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return readPackApplyStatus(session.packApplyStatus());
            }
        });
    }

    public static DlcServerPackApplyStatus pausePackApply(DlcServerEntry entry, int timeoutMs) throws IOException {
        return controlPackApply(entry, timeoutMs, "pack_apply_pause", currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerPackApplyStatus pausePackApply(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return controlPackApply(entry, timeoutMs, "pack_apply_pause", playerName, playerUuid);
    }

    public static DlcServerPackApplyStatus resumePackApply(DlcServerEntry entry, int timeoutMs) throws IOException {
        return controlPackApply(entry, timeoutMs, "pack_apply_resume", currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerPackApplyStatus resumePackApply(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return controlPackApply(entry, timeoutMs, "pack_apply_resume", playerName, playerUuid);
    }

    public static DlcServerPackApplyStatus cancelPackApply(DlcServerEntry entry, int timeoutMs) throws IOException {
        return controlPackApply(entry, timeoutMs, "pack_apply_cancel", currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerPackApplyStatus cancelPackApply(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return controlPackApply(entry, timeoutMs, "pack_apply_cancel", playerName, playerUuid);
    }

    private static DlcServerPackApplyStatus controlPackApply(DlcServerEntry entry, int timeoutMs, String type, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return readPackApplyStatus(session.rawRequest(type, null));
            }
        });
    }

    public static UpdateResponse uninstallPack(DlcServerEntry entry, int timeoutMs, String identifier) throws IOException {
        return packAction(entry, timeoutMs, "pack_uninstall", null, identifier, currentPlayerName(), currentPlayerUuid());
    }

    public static UpdateResponse deletePack(DlcServerEntry entry, int timeoutMs, String archivePath) throws IOException {
        return packAction(entry, timeoutMs, "pack_delete", archivePath, null, currentPlayerName(), currentPlayerUuid());
    }

    private static UpdateResponse packAction(DlcServerEntry entry, int timeoutMs, String type, String archivePath, String identifier,
        String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject req = new JsonObject();
                if (archivePath != null && !archivePath.isBlank()) {
                    req.addProperty("archivePath", archivePath);
                }
                if (identifier != null && !identifier.isBlank()) {
                    req.addProperty("identifier", identifier);
                }
                return readUpdateResponse(session.rawRequest(type, req));
            }
        });
    }

    private static DlcServerPackApplyStatus readPackApplyStatus(JsonObject response) {
        if (response.has("status") && response.get("status").isJsonObject()) {
            return GSON.fromJson(response.get("status"), DlcServerPackApplyStatus.class);
        }
        if (response.has("type") && "pack_apply_status".equals(response.get("type").getAsString())) {
            return GSON.fromJson(response, DlcServerPackApplyStatus.class);
        }
        return null;
    }

    public static boolean importPackPackage(DlcServerEntry entry, int timeoutMs, Path source, String playerName, String playerUuid) throws IOException {
        if (source == null || !Files.exists(source)) {
            return false;
        }
        return falseOnIo(() -> {
            JsonObject config = fetchServerConfig(entry, timeoutMs, playerName, playerUuid);
            String dlcRoot = getDlcRootFromConfig(config);
            if (dlcRoot == null || dlcRoot.isBlank()) {
                dlcRoot = "DLC";
            }
            String normalizedRoot = dlcRoot.replace("\\", "/");
            if (!normalizedRoot.endsWith("/")) {
                normalizedRoot = normalizedRoot + "/";
            }
            String name = source.getFileName() == null ? "pack.zip" : source.getFileName().toString();
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                name = name + ".zip";
            }
            String targetPath = normalizedRoot + "pack/" + name;
            if (!uploadFile(entry, timeoutMs, targetPath, source, playerName, playerUuid)) {
                return false;
            }
            return importPackPackagePath(entry, timeoutMs, targetPath, playerName, playerUuid);
        });
    }

    public static boolean importPackPackage(DlcServerEntry entry, int timeoutMs, Path source) throws IOException {
        return importPackPackage(entry, timeoutMs, source, currentPlayerName(), currentPlayerUuid());
    }

    private static boolean importPackPackagePath(DlcServerEntry entry, int timeoutMs, String targetPath, String playerName, String playerUuid) throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject req = new JsonObject();
                req.addProperty("path", targetPath);
                return isOk(session.rawRequest("pack_import", req));
            }
        });
    }

    public static boolean importPackPackageFromUrl(DlcServerEntry entry, int timeoutMs, String url, String fileName) throws IOException {
        return importPackPackageFromUrl(entry, timeoutMs, url, fileName, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean importPackPackageFromUrl(DlcServerEntry entry, int timeoutMs, String url, String fileName,
        String playerName, String playerUuid) throws IOException {
        if (url == null || url.isBlank()) {
            return false;
        }
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject req = new JsonObject();
                req.addProperty("url", url);
                if (fileName != null && !fileName.isBlank()) {
                    req.addProperty("fileName", fileName);
                }
                return isOk(session.rawRequest("pack_import_url", req));
            }
        });
    }

    public static boolean importUpdatePackageFromUrl(DlcServerEntry entry, int timeoutMs, String url, String fileName,
        String playerName, String playerUuid) throws IOException {
        if (url == null || url.isBlank()) {
            return false;
        }
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject req = new JsonObject();
                req.addProperty("url", url);
                if (fileName != null && !fileName.isBlank()) {
                    req.addProperty("fileName", fileName);
                }
                return isOk(session.rawRequest("update_import_url", req));
            }
        });
    }

    public static boolean importUpdatePackageFromUrl(DlcServerEntry entry, int timeoutMs, String url, String fileName) throws IOException {
        return importUpdatePackageFromUrl(entry, timeoutMs, url, fileName, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean applyUpdates(DlcServerEntry entry, int timeoutMs) throws IOException {
        return applyUpdates(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean applyUpdates(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return isOk(session.rawRequest("update_apply", null));
            }
        });
    }

    public static boolean push(DlcServerEntry entry, int timeoutMs, List<String> targets, String mode, String playerName, String playerUuid) throws IOException {
        return push(entry, timeoutMs, targets, null, mode, playerName, playerUuid);
    }

    public static boolean push(DlcServerEntry entry, int timeoutMs, List<String> targets, String mode) throws IOException {
        return push(entry, timeoutMs, targets, mode, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean push(DlcServerEntry entry, int timeoutMs, List<String> targets, List<String> files, String mode, String playerName, String playerUuid) throws IOException {
        if (entry == null) {
            return false;
        }
        Path zipPath = buildPushZip(targets, files);
        if (zipPath == null) {
            return false;
        }
        String transferId = UUID.randomUUID().toString();
        try {
            return falseOnIo(() -> {
                try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                    return session.pushZip(transferId, mode, targets, files, zipPath);
                }
            });
        } finally {
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException ignored) {
            }
        }
    }

    public static boolean push(DlcServerEntry entry, int timeoutMs, List<String> targets, List<String> files, String mode) throws IOException {
        return push(entry, timeoutMs, targets, files, mode, currentPlayerName(), currentPlayerUuid());
    }

    public static List<DlcServerFileEntry> listFiles(DlcServerEntry entry, int timeoutMs, String path, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return session.listFiles(path);
            }
        });
    }

    public static List<DlcServerFileEntry> listFiles(DlcServerEntry entry, int timeoutMs, String path) throws IOException {
        return listFiles(entry, timeoutMs, path, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean deleteFile(DlcServerEntry entry, int timeoutMs, String path, String playerName, String playerUuid) throws IOException {
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                session.deleteFile(path);
                return true;
            }
        });
    }

    public static boolean deleteFile(DlcServerEntry entry, int timeoutMs, String path) throws IOException {
        return deleteFile(entry, timeoutMs, path, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean uploadFile(DlcServerEntry entry, int timeoutMs, String targetPath, Path source, String playerName, String playerUuid) throws IOException {
        if (source == null || !Files.exists(source)) {
            return false;
        }
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                session.uploadFile(targetPath, source);
                return true;
            }
        });
    }

    public static boolean uploadFile(DlcServerEntry entry, int timeoutMs, String targetPath, Path source) throws IOException {
        return uploadFile(entry, timeoutMs, targetPath, source, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerRconConfig fetchRconConfig(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject resp = session.rconGet();
                if (!isOk(resp)) {
                    return null;
                }
                DlcServerRconConfig config = new DlcServerRconConfig();
                config.host = getString(resp, "host");
                config.port = resp.has("port") ? resp.get("port").getAsInt() : 0;
                config.password = getString(resp, "password");
                return config;
            }
        });
    }

    public static DlcServerRconConfig fetchRconConfig(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchRconConfig(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static DlcServerRconInfo fetchRconInfo(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject resp = session.rconInfo();
                if (!isOk(resp)) {
                    return null;
                }
                DlcServerRconInfo info = new DlcServerRconInfo();
                info.enabled = resp.has("enabled") && resp.get("enabled").getAsBoolean();
                info.port = resp.has("port") ? resp.get("port").getAsInt() : 25575;
                info.passwordSet = resp.has("passwordSet") && resp.get("passwordSet").getAsBoolean();
                return info;
            }
        });
    }

    public static DlcServerRconInfo fetchRconInfo(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchRconInfo(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean saveRconConfig(DlcServerEntry entry, int timeoutMs, DlcServerRconConfig config, String playerName, String playerUuid) throws IOException {
        if (config == null) {
            return false;
        }
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                return isOk(session.rconSet(config.host == null ? "" : config.host, config.port, config.password == null ? "" : config.password));
            }
        });
    }

    public static boolean saveRconConfig(DlcServerEntry entry, int timeoutMs, DlcServerRconConfig config) throws IOException {
        return saveRconConfig(entry, timeoutMs, config, currentPlayerName(), currentPlayerUuid());
    }

    public static String execRcon(DlcServerEntry entry, int timeoutMs, String command, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject resp = session.rconExec(command == null ? "" : command);
                if (resp == null) {
                    return null;
                }
                if (resp.has("message")) {
                    return resp.get("message").getAsString();
                }
                return resp.has("ok") && resp.get("ok").getAsBoolean() ? "ok" : null;
            }
        });
    }

    public static String execRcon(DlcServerEntry entry, int timeoutMs, String command) throws IOException {
        return execRcon(entry, timeoutMs, command, currentPlayerName(), currentPlayerUuid());
    }

    public static JsonObject fetchServerConfig(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        return nullOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                JsonObject response = session.configGet();
                return response.has("config") && response.get("config").isJsonObject()
                        ? response.getAsJsonObject("config")
                        : null;
            }
        });
    }

    public static JsonObject fetchServerConfig(DlcServerEntry entry, int timeoutMs) throws IOException {
        return fetchServerConfig(entry, timeoutMs, currentPlayerName(), currentPlayerUuid());
    }

    public static boolean saveServerConfig(DlcServerEntry entry, int timeoutMs, JsonObject config, String playerName, String playerUuid) throws IOException {
        if (config == null) {
            return false;
        }
        return falseOnIo(() -> {
            try (DlcServerSession session = openSession(entry, timeoutMs, playerName, playerUuid)) {
                session.configSet(config);
                return true;
            }
        });
    }

    public static boolean saveServerConfig(DlcServerEntry entry, int timeoutMs, JsonObject config) throws IOException {
        return saveServerConfig(entry, timeoutMs, config, currentPlayerName(), currentPlayerUuid());
    }

    private static DlcServerSession openSession(DlcServerEntry entry, int timeoutMs, String playerName, String playerUuid) throws IOException {
        if (entry == null) {
            throw new IOException("missing server entry");
        }
        return DlcServerSession.connect(
                entry.host,
                entry.port,
                entry.password,
                timeoutMs,
                DlcPlatformServices.languageCode(),
                playerName,
                playerUuid,
                entry.usePortMultiplexer);
    }

    @FunctionalInterface
    private interface IoCall<T> {
        T run() throws IOException;
    }

    @FunctionalInterface
    private interface IoBooleanCall {
        boolean run() throws IOException;
    }

    private static <T> T nullOnIo(IoCall<T> call) throws IOException {
        try {
            return call.run();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean falseOnIo(IoBooleanCall call) throws IOException {
        try {
            return call.run();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean hasType(JsonObject response, String type) {
        return response != null
                && response.has("type")
                && type.equals(response.get("type").getAsString());
    }

    private static boolean isOk(JsonObject response) {
        return response != null && response.has("ok") && response.get("ok").getAsBoolean();
    }

    private static UpdateResponse readUpdateResponse(JsonObject response) {
        if (response == null) {
            return null;
        }
        UpdateResponse result = new UpdateResponse();
        result.ok = isOk(response);
        result.missing = response.has("missing") && response.get("missing").isJsonPrimitive() && response.get("missing").getAsBoolean();
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

    private static String currentPlayerName() {
        return DlcPlatformServices.playerName();
    }

    private static String currentPlayerUuid() {
        return DlcPlatformServices.playerUuid();
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
        JsonObject paths = DlcDcConfigIO.getObjectIgnoreCase(config, "paths");
        if (paths == null) {
            return "";
        }
        return DlcDcConfigIO.getStringIgnoreCase(paths, "dlcRoot");
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
        Path dlcRoot = DlcConfig.getDlcRoot();
        Path configDir = DlcPlatformServices.configDir().resolve("dlc_manager");
        Path gameDir = DlcPaths.gameDir();
        Path tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops");
        Files.createDirectories(tempDir);
        Path zipPath = tempDir.resolve("push_" + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            if (includeDlc && Files.exists(dlcRoot)) {
                zipDirectory(dlcRoot, out, "dlc/", DlcTmpPaths.TMP_DIR);
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
                        if (skipDirName.equalsIgnoreCase(first)
                            || DlcTmpPaths.TMP_DIR.equalsIgnoreCase(first)
                            || DlcTmpPaths.LEGACY_TMP_DIR.equalsIgnoreCase(first)) {
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
