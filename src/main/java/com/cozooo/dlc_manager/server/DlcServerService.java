package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerPackApplyStatus;
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo;
import com.cozooo.dlc_manager.client.server.DlcServerPresetItem;
import com.cozooo.dlc_manager.client.server.DlcServerRedirectItem;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackMeta;
import com.cozooo.dlc_manager.client.pack.DlcPackOrder;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DlcServerService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final int FILE_CHUNK_SIZE = 64 * 1024;
    private static final Set<String> LOGGED_CONNECTION_KEYS = ConcurrentHashMap.newKeySet();
    private static final Object PACK_APPLY_LOCK = new Object();
    private static volatile PackApplyJob packApplyJob;
    private static volatile MinecraftServer currentServer;
    private static final java.util.concurrent.ScheduledExecutorService SYNC_HEARTBEATS = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dlc-sync-heartbeat"); thread.setDaemon(true); return thread;
    });

    public static void updateServerStatus(MinecraftServer server) {
        if (!DlcConfig.isServerSupportOnServer()) {
            return;
        }
        currentServer = server;
        if (server == null) {
            return;
        }
        String label = getServerVersionLabel();
        if (label == null || label.isBlank()) {
            return;
        }
        String motd = server.getMotd();
        String combined;
        if (motd == null || motd.isBlank()) {
            combined = label;
        } else if (motd.contains(label)) {
            combined = motd;
        } else {
            combined = motd + "\n" + label;
        }
        DlcServerCompat.setStatusDescription(server, DlcTextCompat.literal(combined));
    }

    public static String getServerVersionLabel() {
        if (!DlcConfig.isServerSupportOnServer()) {
            return "";
        }
        Path dlcRoot = DlcConfig.getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        if (version == null) {
            return "";
        }
        String base = version.getBase();
        String dlc = version.getDlc();
        if (base == null || base.isBlank() || dlc == null || dlc.isBlank()) {
            return "";
        }
        return base + "+" + dlc;
    }

    public static void handleClient(Socket socket) {
        try (com.cozooo.dlc_manager.sync.SyncEndpoint sync = new com.cozooo.dlc_manager.sync.SyncEndpoint(new com.cozooo.dlc_manager.client.sync.DlcSyncAdapter(canceled -> {
                 DlcServerDownloadService.cancelRemoteDownload(); DlcServerDownloadService.cancelRequiredDownload();
                 long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                 while (DlcServerDownloadService.getRemoteDownloadStatus().running || DlcServerDownloadService.getRequiredDownloadStatus().running) {
                     if (canceled.getAsBoolean() || System.nanoTime() > deadline) throw new com.cozooo.dlc_manager.sync.SyncFailure("server_downloads_stop");
                     Thread.sleep(50);
                 }
             }));
             Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            boolean authed = false;
            String clientLang = null;
            Map<String, Boolean> overrides = new HashMap<>();
            PushState pushState = null;
            UploadState uploadState = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonObject request = GSON.fromJson(line, JsonObject.class);
                if (request == null || !request.has("type")) {
                    writeError(writer, "invalid request");
                    continue;
                }
                String type = request.get("type").getAsString();
                if (request.has("lang") && request.get("lang").isJsonPrimitive()) {
                    clientLang = request.get("lang").getAsString();
                }
                if ("hello".equals(type)) {
                    String remoteAddress = client.getInetAddress() != null ? client.getInetAddress().getHostAddress() : "";
                    authed = handleHello(request, writer, remoteAddress);
                    if (!authed) {
                        break;
                    }
                    continue;
                }
                if (!authed) {
                    writeError(writer, "unauthorized");
                    continue;
                }
                if ("dlc_sync".equals(type)) {
                    handleSync(sync, request, writer);
                    continue;
                }
                com.cozooo.dlc_manager.sync.SyncEndpoint.OperationGuard guard;
                try { guard = com.cozooo.dlc_manager.sync.SyncEndpoint.guard(DlcConfig.getDlcRoot().resolve("tmp/sync")); }
                catch (IOException busy) { writeError(writer, busy.getMessage()); continue; }
                try (guard) {
                    if ("list".equals(type)) {
                        writeList(writer, clientLang);
                    } else if ("remote_list".equals(type)) {
                        handleRemoteList(writer, clientLang);
                    } else if ("remote_download_start".equals(type)) {
                        handleRemoteDownloadStart(request, writer, clientLang);
                    } else if ("remote_download_status".equals(type)) {
                        handleRemoteDownloadStatus(writer, clientLang);
                    } else if ("remote_download_cancel".equals(type)) {
                        handleRemoteDownloadCancel(writer);
                    } else if ("remote_download".equals(type)) {
                        handleRemoteDownload(writer);
                    } else if ("required_list".equals(type)) {
                        handleRequiredList(writer, clientLang);
                    } else if ("required_download_start".equals(type)) {
                        handleRequiredDownloadStart(request, writer, clientLang);
                    } else if ("required_download_status".equals(type)) {
                        handleRequiredDownloadStatus(writer, clientLang);
                    } else if ("required_download_cancel".equals(type)) {
                        handleRequiredDownloadCancel(writer);
                    } else if ("required_uninstall".equals(type)) {
                        handleRequiredUninstall(request, writer);
                    } else if ("required_apply".equals(type)) {
                        handleRequiredApply(request, writer);
                    } else if ("update_state".equals(type)) {
                        writeUpdateState(writer);
                    } else if ("update_list".equals(type)) {
                        writeUpdateList(writer);
                    } else if ("update_apply".equals(type)) {
                        handleUpdateApply(request, writer);
                    } else if ("update_rollback".equals(type)) {
                        handleUpdateRollback(request, writer);
                    } else if ("update_import".equals(type)) {
                        handleUpdateImport(request, writer);
                    } else if ("update_import_url".equals(type)) {
                        handleUpdateImportUrl(request, writer);
                    } else if ("pack_list".equals(type)) {
                        handlePackList(writer, clientLang);
                    } else if ("pack_install".equals(type)) {
                        handlePackInstall(request, writer);
                    } else if ("pack_uninstall".equals(type)) {
                        handlePackUninstall(request, writer);
                    } else if ("pack_delete".equals(type)) {
                        handlePackDelete(request, writer);
                    } else if ("pack_import".equals(type)) {
                        handlePackImport(request, writer);
                    } else if ("pack_import_url".equals(type)) {
                        handlePackImportUrl(request, writer);
                    } else if ("pack_order".equals(type)) {
                        handlePackOrder(request, writer);
                    } else if ("pack_apply_start".equals(type)) {
                        handlePackApplyStart(request, writer);
                    } else if ("pack_apply_status".equals(type)) {
                        handlePackApplyStatus(writer);
                    } else if ("pack_apply_pause".equals(type)) {
                        handlePackApplyPause(writer);
                    } else if ("pack_apply_resume".equals(type)) {
                        handlePackApplyResume(writer);
                    } else if ("pack_apply_cancel".equals(type)) {
                        handlePackApplyCancel(writer);
                    } else if ("set".equals(type)) {
                        handleSet(request, overrides, writer);
                    } else if ("apply".equals(type)) {
                        handleApply(overrides, writer);
                        overrides.clear();
                    } else if ("push_init".equals(type)) {
                        pushState = handlePushInit(request, writer);
                    } else if ("push_chunk".equals(type)) {
                        handlePushChunk(request, pushState, writer);
                    } else if ("push_finish".equals(type)) {
                        handlePushFinish(pushState, writer);
                        pushState = null;
                    } else if ("file_list".equals(type)) {
                        handleFileList(request, writer);
                    } else if ("file_delete".equals(type)) {
                        handleFileDelete(request, writer);
                    } else if ("file_upload_init".equals(type)) {
                        uploadState = handleFileUploadInit(request, writer);
                    } else if ("file_upload_chunk".equals(type)) {
                        handleFileUploadChunk(request, uploadState, writer);
                    } else if ("file_upload_finish".equals(type)) {
                        handleFileUploadFinish(uploadState, writer);
                        uploadState = null;
                    } else if ("file_download_init".equals(type)) {
                        handleFileDownload(request, writer);
                    } else if ("config_get".equals(type)) {
                        handleConfigGet(writer);
                    } else if ("config_set".equals(type)) {
                        handleConfigSet(request, writer);
                    } else if ("rcon_info".equals(type)) {
                        handleRconInfo(writer);
                    } else if ("rcon_get".equals(type)) {
                        handleRconGet(writer);
                    } else if ("rcon_set".equals(type)) {
                        handleRconSet(request, writer);
                    } else if ("rcon_exec".equals(type)) {
                        handleRconExec(request, writer);
                    } else if ("server_restart".equals(type)) {
                        handleServerRestart(writer);
                    } else {
                        writeError(writer, "unknown request");
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void handleSync(com.cozooo.dlc_manager.sync.SyncEndpoint sync, JsonObject request, BufferedWriter writer) throws IOException {
        Thread actor = Thread.currentThread();
        Object gate = new Object();
        boolean[] finished = {false};
        java.util.concurrent.ScheduledFuture<?> heartbeat = null;
        String action = request.has("action") ? request.get("action").getAsString() : "";
        if (Set.of("prepare", "read", "read_end", "write_end", "apply", "rollback", "recover", "recover_operation").contains(action)) {
            heartbeat = SYNC_HEARTBEATS.scheduleWithFixedDelay(() -> {
                JsonObject progress = new JsonObject(); progress.addProperty("type", "dlc_sync_progress");
                if (request.has("requestId")) progress.add("requestId", request.get("requestId"));
                try { synchronized (writer) { writeLine(writer, progress); } }
                catch (IOException disconnected) { synchronized (gate) { if (!finished[0]) { sync.disconnectCurrent(); actor.interrupt(); } } }
            }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
        }
        JsonObject response;
        try { response = sync.call(request); }
        finally { synchronized (gate) { finished[0] = true; } if (heartbeat != null) heartbeat.cancel(false); }
        synchronized (writer) { writeLine(writer, response); }
    }

    private static boolean handleHello(JsonObject request, BufferedWriter writer, String remoteAddress) throws IOException {
        String expected = DlcConfig.getServerPassword();
        String provided = request.has("password") ? request.get("password").getAsString() : "";
        String player = request.has("player") ? request.get("player").getAsString() : "";
        String uuid = request.has("uuid") ? request.get("uuid").getAsString() : "";
        boolean passwordOk = expected == null || expected.isBlank() ? provided.isBlank() : expected.equals(provided);
        boolean hasPlayer = (player != null && !player.isBlank()) || (uuid != null && !uuid.isBlank());
        boolean ok = passwordOk;
        JsonObject response = new JsonObject();
        response.addProperty("type", "hello");
        response.addProperty("ok", ok);
        if (!ok) {
            String reason = !passwordOk ? "password" : "op";
            response.addProperty("reason", reason);
            LOGGER.info("[dlc-manager] server manager auth failed: reason={} player={} uuid={}", reason, player, uuid);
        } else {
            logConnectedOnce(player, uuid, remoteAddress);
        }
        writeLine(writer, response);
        return ok;
    }

    private static void logConnectedOnce(String player, String uuid, String remoteAddress) {
        String key = connectionLogKey(player, uuid, remoteAddress);
        if (LOGGED_CONNECTION_KEYS.add(key)) {
            LOGGER.info("[dlc-manager] server manager connected: player={} uuid={}", player, uuid);
        }
    }

    private static String connectionLogKey(String player, String uuid, String remoteAddress) {
        String normalizedUuid = uuid == null ? "" : uuid.trim();
        if (!normalizedUuid.isEmpty()) {
            return "uuid:" + normalizedUuid;
        }
        String normalizedPlayer = player == null ? "" : player.trim();
        if (!normalizedPlayer.isEmpty()) {
            return "player:" + normalizedPlayer;
        }
        String normalizedRemote = remoteAddress == null ? "" : remoteAddress.trim();
        return normalizedRemote.isEmpty() ? "unknown" : "remote:" + normalizedRemote;
    }

    private static boolean isPlayerOp(String name, String uuid) {
        MinecraftServer server = currentServer;
        if (server == null) {
            return false;
        }
        ServerPlayer player = null;
        if (uuid != null && !uuid.isBlank()) {
            try {
                player = server.getPlayerList().getPlayer(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (player == null && name != null && !name.isBlank()) {
            player = server.getPlayerList().getPlayerByName(name);
        }
        if (player == null) {
            return false;
        }
        return invokeOperatorCheck(server, player);
    }


    private static boolean invokeOperatorCheck(Object server, Object player) {
        try {
            Object list = server.getClass().getMethod("getPlayerList").invoke(server);
            Object profile = player.getClass().getMethod("getGameProfile").invoke(player);
            for (var method : list.getClass().getMethods()) {
                if (method.getName().equals("isOp") && method.getParameterCount() == 1) {
                    Object result = method.invoke(list, profile);
                    if (result instanceof Boolean && (Boolean) result) return true;
                }
            }
            for (var method : player.getClass().getMethods()) {
                if (method.getName().equals("hasPermissions") && method.getParameterCount() == 1) {
                    Object result = method.invoke(player, 4);
                    return result instanceof Boolean && (Boolean) result;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private static void writeList(BufferedWriter writer, String lang) throws IOException {
        List<DlcServerItem> items = new ArrayList<>();
        String effectiveLang = lang == null || lang.isBlank() ? DlcPlatformServices.languageCode() : lang;
        for (DlcItem item : scanDlcItemsForServer(true)) {
            DlcServerItem serverItem = toServerItem(item, effectiveLang);
            if (serverItem != null) {
                items.add(serverItem);
            }
        }
        Path dlcRoot = DlcConfig.getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        int remoteMissing = DlcServerDownloadService.collectMissingRemote(dlcRoot).size();
        int requiredMissing = DlcServerDownloadService.collectMissingRequired(dlcRoot).size();
        JsonObject response = new JsonObject();
        response.addProperty("type", "list");
        response.addProperty("base", version.getBase());
        response.addProperty("version", version.getVersion());
        response.addProperty("dlc", version.getDlc());
        response.addProperty("mcVersion", DlcPlatformServices.mcVersion());
        response.addProperty("loaderName", DlcPlatformServices.loaderName());
        response.addProperty("remoteMissing", remoteMissing);
        response.addProperty("requiredMissing", requiredMissing);
        response.addProperty("hasRequired", DlcServerDownloadService.hasRequiredEntries(dlcRoot));
        response.addProperty("hasTaczMod", DlcPlatformServices.isModLoaded("tacz"));
        response.addProperty("modpackUrl", DlcConfig.getServerModpackUrl());
        response.addProperty("updateGitRepo", DlcConfig.getServerUpdateGitRepoUrl());
        response.add("redirects", GSON.toJsonTree(loadVisibleRedirectItems(dlcRoot, effectiveLang)));
        response.add("presets", GSON.toJsonTree(toServerPresetItems()));
        response.add("items", GSON.toJsonTree(items));
        writeLine(writer, response);
    }

    private static List<DlcServerPresetItem> toServerPresetItems() {
        List<DlcServerPresetItem> out = new ArrayList<>();
        for (var preset : DlcPresetRuntimeState.getPresets()) {
            if (preset != null) {
                out.add(DlcServerPresetItem.fromPreset(preset));
            }
        }
        return out;
    }

    private static List<DlcItem> scanDlcItemsForServer(boolean detectInstalledState) {
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan(detectInstalledState);
        List<DlcItem> items = new ArrayList<>();
        for (List<DlcItem> group : grouped.values()) {
            if (group == null) {
                continue;
            }
            for (DlcItem item : group) {
                if (item == null) {
                    continue;
                }
                items.add(item);
            }
        }
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.reload();
        } else {
            DlcPresetRuntimeState.clearAll();
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled());
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
        }
        DlcRuntimeState.updateItemIndex(items);
        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.applyOverridesToRuntime();
        }
        for (DlcItem item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
        }
        return items;
    }

    private static DlcServerItem toServerItem(DlcItem item, String lang) {
        if (item == null) {
            return null;
        }
        DlcServerItem serverItem = new DlcServerItem();
        serverItem.id = item.getId();
        serverItem.identifier = item.getIdentifier();
        serverItem.name = item.getName();
        serverItem.displayNames = item.getDisplayNames();
        serverItem.tips = item.getTooltips();
        serverItem.alarmTips = item.getAlarmTips();
        serverItem.type = item.getType() == null ? null : item.getType().name();
        serverItem.enabled = item.isEnabled();
        Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
        serverItem.baselineEnabled = baseline == null ? item.isEnabled() : baseline;
        serverItem.dependencies = item.getDependencies();
        serverItem.conflicts = item.getConflicts();
        serverItem.allowEnableByAll = item.AllowEnableByAll();
        serverItem.alarm = item.isAlarm();
        serverItem.hidden = item.isHidden();
        serverItem.configType = item.isConfigType();
        serverItem.appliedTargetRaw = item.getAppliedTargetRaw();
        serverItem.redirectUrl = item.getRedirectUrl();
        serverItem.presetBlocked = DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier());
        serverItem.presetBlockers = DlcPresetRuntimeState.getDlcBlockerNames(item.getIdentifier(), lang);
        serverItem.path = resolveGroupLabel(item);
        return serverItem;
    }

    private static List<DlcServerRedirectItem> loadVisibleRedirectItems(Path dlcRoot, String lang) {
        if (dlcRoot == null) {
            return List.of();
        }
        List<DlcServerRedirectItem> out = new ArrayList<>();
        for (DlcRedirectEntry entry : DlcRedirectLoader.load(dlcRoot)) {
            if (entry == null || entry.isHidden() || !entry.isAvailableFor(lang)) {
                continue;
            }
            DlcServerRedirectItem item = new DlcServerRedirectItem();
            item.redirectUrl = entry.getRedirectUrl();
            item.fallbackName = entry.getFallbackName();
            item.displayNames = entry.getDisplayNames();
            item.notices = entry.getNotices();
            item.tips = entry.getTips();
            out.add(item);
        }
        return out;
    }

    private static void handleRemoteDownload(BufferedWriter writer) throws IOException {
        DlcServerDownloadService.DownloadResult result = DlcServerDownloadService.downloadMissingRemote(DlcConfig.getDlcRoot());
        JsonObject response = new JsonObject();
        response.addProperty("type", "remote_download");
        response.addProperty("ok", result.success);
        response.addProperty("downloaded", result.downloadedCount);
        if (!result.success && result.error != null) {
            response.addProperty("error", result.error);
        }
        writeLine(writer, response);
    }

    private static void handleRemoteList(BufferedWriter writer, String lang) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "remote_list");
        response.addProperty("ok", true);
        response.add("items", GSON.toJsonTree(DlcServerDownloadService.collectRemoteItems(DlcConfig.getDlcRoot(), lang)));
        writeLine(writer, response);
    }

    private static String resolveGroupLabel(DlcItem item) {
        try {
            java.nio.file.Path source = item.getSourcePath();
            String relative = null;
            for (java.nio.file.Path dlcRoot : DlcConfig.getDlcRoots()) {
                if (source.startsWith(dlcRoot)) {
                    relative = dlcRoot.relativize(source).toString().replace('\\', '/');
                    break;
                }
            }
            if (relative == null) {
                relative = DlcPaths.gameDir().relativize(source).toString().replace('\\', '/');
            }
            String normalized = relative == null ? "" : relative.replace('\\', '/');
            String visible;
            if ("remote".equals(normalized) || "downloaded/DLCs".equals(normalized)) {
                visible = "";
            } else if (normalized.startsWith("remote/")) {
                visible = normalized.substring("remote/".length());
            } else if (normalized.startsWith("downloaded/DLCs/")) {
                visible = normalized.substring("downloaded/DLCs/".length());
            } else {
                visible = normalized;
            }
            int lastSlash = visible.lastIndexOf('/');
            if (lastSlash <= 0) {
                return "__root__";
            }
            return visible.substring(0, lastSlash);
        } catch (Exception ignored) {
            return "__root__";
        }
    }

    private static List<String> readStringList(JsonObject request, String key) {
        List<String> values = new ArrayList<>();
        if (request.has(key) && request.get(key).isJsonArray()) {
            request.getAsJsonArray(key).forEach(element -> {
                if (element.isJsonPrimitive()) {
                    values.add(element.getAsString());
                }
            });
        }
        return values;
    }

    private static void handleRemoteDownloadStart(JsonObject request, BufferedWriter writer, String lang) throws IOException {
        List<String> keys = new ArrayList<>();
        if (request.has("keys") && request.get("keys").isJsonArray()) {
            request.getAsJsonArray("keys").forEach(element -> {
                if (element.isJsonPrimitive()) {
                    keys.add(element.getAsString());
                }
            });
        }
        DlcServerRemoteStatus status = DlcServerDownloadService.startRemoteDownload(DlcConfig.getDlcRoot(), keys, lang);
        JsonObject response = new JsonObject();
        response.addProperty("type", "remote_download_start");
        response.addProperty("ok", true);
        response.add("status", GSON.toJsonTree(status));
        writeLine(writer, response);
    }

    private static void handleRemoteDownloadStatus(BufferedWriter writer, String lang) throws IOException {
        DlcServerRemoteStatus status = DlcServerDownloadService.getRemoteDownloadStatus(lang);
        JsonObject response = new JsonObject();
        response.addProperty("type", "remote_download_status");
        response.addProperty("ok", true);
        response.add("status", GSON.toJsonTree(status));
        writeLine(writer, response);
    }

    private static void handleRemoteDownloadCancel(BufferedWriter writer) throws IOException {
        boolean canceled = DlcServerDownloadService.cancelRemoteDownload();
        JsonObject response = new JsonObject();
        response.addProperty("type", "remote_download_cancel");
        response.addProperty("ok", true);
        response.addProperty("canceled", canceled);
        writeLine(writer, response);
    }

    private static void handleRequiredList(BufferedWriter writer, String lang) throws IOException {
        List<DlcServerRequiredItem> items = DlcServerDownloadService.collectRequiredItems(DlcConfig.getDlcRoot(), lang);
        JsonObject response = new JsonObject();
        response.addProperty("type", "required_list");
        response.addProperty("ok", true);
        response.add("items", GSON.toJsonTree(items));
        writeLine(writer, response);
    }

    private static void handleRequiredDownloadStart(JsonObject request, BufferedWriter writer, String lang) throws IOException {
        List<String> ids = new ArrayList<>();
        if (request.has("ids") && request.get("ids").isJsonArray()) {
            request.getAsJsonArray("ids").forEach(element -> {
                if (element.isJsonPrimitive()) {
                    ids.add(element.getAsString());
                }
            });
        }
        DlcServerRequiredStatus status = DlcServerDownloadService.startRequiredDownload(DlcConfig.getDlcRoot(), ids, lang);
        JsonObject response = new JsonObject();
        response.addProperty("type", "required_download_start");
        response.addProperty("ok", true);
        response.add("status", GSON.toJsonTree(status));
        writeLine(writer, response);
    }

    private static void handleRequiredDownloadStatus(BufferedWriter writer, String lang) throws IOException {
        DlcServerRequiredStatus status = DlcServerDownloadService.getRequiredDownloadStatus(lang);
        JsonObject response = new JsonObject();
        response.addProperty("type", "required_download_status");
        response.addProperty("ok", true);
        response.add("status", GSON.toJsonTree(status));
        writeLine(writer, response);
    }

    private static void handleRequiredDownloadCancel(BufferedWriter writer) throws IOException {
        boolean canceled = DlcServerDownloadService.cancelRequiredDownload();
        JsonObject response = new JsonObject();
        response.addProperty("type", "required_download_cancel");
        response.addProperty("ok", true);
        response.addProperty("canceled", canceled);
        writeLine(writer, response);
    }

    private static void handleRequiredUninstall(JsonObject request, BufferedWriter writer) throws IOException {
        List<String> ids = new ArrayList<>();
        if (request.has("ids") && request.get("ids").isJsonArray()) {
            request.getAsJsonArray("ids").forEach(element -> {
                if (element.isJsonPrimitive()) {
                    ids.add(element.getAsString());
                }
            });
        }
        int removed = DlcServerDownloadService.uninstallRequired(DlcConfig.getDlcRoot(), ids);
        JsonObject response = new JsonObject();
        response.addProperty("type", "required_uninstall");
        response.addProperty("ok", true);
        response.addProperty("removed", removed);
        writeLine(writer, response);
    }

    private static void handleRequiredApply(JsonObject request, BufferedWriter writer) throws IOException {
        List<String> ids = new ArrayList<>();
        if (request.has("ids") && request.get("ids").isJsonArray()) {
            request.getAsJsonArray("ids").forEach(element -> {
                if (element.isJsonPrimitive()) {
                    ids.add(element.getAsString());
                }
            });
        }
        int applied = DlcServerDownloadService.applyRequiredByIds(DlcConfig.getDlcRoot(), ids);
        JsonObject response = new JsonObject();
        response.addProperty("type", "required_apply");
        response.addProperty("ok", true);
        response.addProperty("applied", applied);
        writeLine(writer, response);
    }

    private static void handleSet(JsonObject request, Map<String, Boolean> overrides, BufferedWriter writer) throws IOException {
        String id = request.has("id") ? request.get("id").getAsString() : null;
        boolean enabled = request.has("enabled") && request.get("enabled").getAsBoolean();
        if (id != null && !id.isBlank()) {
            overrides.put(id, enabled);
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "set");
        response.addProperty("ok", true);
        writeLine(writer, response);
    }

    private static void handleApply(Map<String, Boolean> overrides, BufferedWriter writer) throws IOException {
        List<DlcItem> items = scanDlcItemsForServer(true);
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            Boolean desired = overrides.get(item.getId());
            if (desired != null) {
                item.setEnabled(desired);
            }
        }
        DlcApplier.ApplyResult result = DlcApplier.apply(items, DlcApplyMode.RESTART_ONLY);
        for (DlcItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled());
            DlcRuntimeState.clearOverride(item.getId());
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "apply");
        response.addProperty("ok", true);
        response.addProperty("requiresRestart", result.requiresRestart());
        JsonArray messages = new JsonArray();
        for (String msg : result.messages()) {
            if (msg != null && !msg.isBlank()) {
                messages.add(msg);
            }
        }
        response.add("messages", messages);
        writeLine(writer, response);
    }

    private static void writeUpdateState(BufferedWriter writer) throws IOException {
        Path dlcRoot = DlcConfig.getDlcRoot();
        DlcUpdateVersion version = dlcRoot == null ? null : DlcUpdateVersion.load(dlcRoot);
        String base = version == null ? "" : version.getBase();
        String dlc = version == null ? "" : version.getDlc();
        String rollbackFrom = version == null ? "" : version.getRollbackFrom();
        boolean rolledBack = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, base, dlc);
        List<DlcUpdateMeta> history = rolledBack
            ? DlcUpdateService.listUpdateHistoryFrom(rollbackFrom)
            : DlcUpdateService.listUpdateHistory();
        List<DlcServerUpdateInfo> updates = new ArrayList<>();
        if (!rolledBack) {
            DlcUpdateService.UpdateCheckResult result = DlcUpdateService.checkForUpdates();
            if (result != null && result.getUpdates() != null) {
                for (DlcUpdatePackage pkg : result.getUpdates()) {
                    if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                        continue;
                    }
                    DlcUpdateManifest.VersionInfo info = pkg.getManifest().getVersion();
                    DlcServerUpdateInfo row = new DlcServerUpdateInfo();
                    row.name = pkg.getName();
                    row.baseOld = info.getBase();
                    row.baseNew = info.getBaseNew();
                    row.dlcOld = info.getDlcOld();
                    row.dlcNew = info.getDlcNew();
                    row.hidden = pkg.getManifest().isHidden();
                    row.changelog = pkg.getManifest().getChangelog();
                    updates.add(row);
                }
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "update_state");
        response.addProperty("ok", true);
        response.addProperty("base", base == null ? "" : base);
        response.addProperty("dlc", dlc == null ? "" : dlc);
        response.addProperty("modpackUrl", DlcConfig.getServerModpackUrl());
        response.addProperty("updateGitRepo", DlcConfig.getServerUpdateGitRepoUrl());
        if (rollbackFrom != null && !rollbackFrom.isBlank()) {
            response.addProperty("rollbackFrom", rollbackFrom);
        }
        response.add("history", GSON.toJsonTree(history));
        response.add("updates", GSON.toJsonTree(updates));
        writeLine(writer, response);
    }

    private static void writeUpdateList(BufferedWriter writer) throws IOException {
        DlcUpdateService.UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        List<DlcServerUpdateInfo> updates = new ArrayList<>();
        if (result != null && result.getUpdates() != null) {
            for (DlcUpdatePackage pkg : result.getUpdates()) {
                if (pkg == null || pkg.getManifest() == null) {
                    continue;
                }
                DlcUpdateManifest.VersionInfo version = pkg.getManifest().getVersion();
                if (version == null) {
                    continue;
                }
                DlcServerUpdateInfo info = new DlcServerUpdateInfo();
                info.name = pkg.getName();
                info.baseOld = version.getBase();
                info.baseNew = version.getBaseNew();
                info.dlcOld = version.getDlcOld();
                info.dlcNew = version.getDlcNew();
                info.changelog = pkg.getManifest().getChangelog();
                updates.add(info);
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "update_list");
        response.addProperty("ok", true);
        response.add("updates", GSON.toJsonTree(updates));
        writeLine(writer, response);
    }

    private static void handleUpdateApply(JsonObject request, BufferedWriter writer) throws IOException {
        String baseOld = request.has("baseOld") ? request.get("baseOld").getAsString() : null;
        String dlcOld = request.has("dlcOld") ? request.get("dlcOld").getAsString() : null;
        String baseNew = request.has("baseNew") ? request.get("baseNew").getAsString() : null;
        String dlcNew = request.has("dlcNew") ? request.get("dlcNew").getAsString() : null;
        List<DlcUpdatePackage> updates;
        if (baseOld != null && !baseOld.isBlank()
            && dlcOld != null && !dlcOld.isBlank()
            && baseNew != null && !baseNew.isBlank()
            && dlcNew != null && !dlcNew.isBlank()) {
            updates = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
        } else {
            DlcUpdateService.UpdateCheckResult result = DlcUpdateService.checkForUpdates();
            updates = result == null ? List.of() : result.getUpdates();
        }
        DlcUpdateService.UpdateApplyResult apply = DlcUpdateService.applyUpdates(updates, false);
        JsonObject response = new JsonObject();
        response.addProperty("type", "update_apply");
        response.addProperty("ok", apply != null && apply.isSuccess());
        response.addProperty("missing", apply != null && apply.isMissing());
        response.addProperty("message", apply == null ? "" : apply.getMessage());
        if (apply != null && apply.isMissing()) {
            response.add("missing", GSON.toJsonTree(apply.getMissingPaths()));
        }
        writeLine(writer, response);
    }

    private static void handleUpdateRollback(JsonObject request, BufferedWriter writer) throws IOException {
        String target = request.has("target") ? request.get("target").getAsString() : null;
        DlcUpdateService.UpdateRollbackResult result = DlcUpdateService.rollbackTo(target);
        JsonObject response = new JsonObject();
        response.addProperty("type", "update_rollback");
        response.addProperty("ok", result != null && result.isSuccess());
        response.addProperty("message", result == null ? "" : result.getMessage());
        writeLine(writer, response);
    }

    private static void handleUpdateImport(JsonObject request, BufferedWriter writer) throws IOException {
        Path root = DlcPlatformServices.gameDir();
        String raw = request.has("path") ? request.get("path").getAsString() : "";
        Path target = safeResolve(root, raw);
        boolean ok = target != null && DlcUpdateService.importUpdatePackage(target);
        JsonObject response = new JsonObject();
        response.addProperty("type", "update_import");
        response.addProperty("ok", ok);
        writeLine(writer, response);
    }

    private static void handleUpdateImportUrl(JsonObject request, BufferedWriter writer) throws IOException {
        String url = request.has("url") ? request.get("url").getAsString() : "";
        String fileName = request.has("fileName") ? request.get("fileName").getAsString() : "";
        boolean ok = false;
        String message = "";
        if (url == null || url.isBlank()) {
            message = "missing_url";
        } else {
            Path updateDir = DlcUpdateService.getUpdateDir();
            if (updateDir == null) {
                message = "missing_dlc_root";
            } else {
                try {
                    Path downloaded = DlcDownloadService.download(url, updateDir, fileName, null, null);
                    ok = DlcUpdateService.importUpdatePackage(downloaded);
                    if (!ok) {
                        message = "import_failed";
                    }
                } catch (DlcDownloadService.DownloadCanceledException ex) {
                    message = "canceled";
                } catch (Exception ex) {
                    message = ex.getMessage() == null ? "download_failed" : ex.getMessage();
                }
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "update_import_url");
        response.addProperty("ok", ok);
        response.addProperty("message", message);
        writeLine(writer, response);
    }

    private static void handlePackList(BufferedWriter writer, String lang) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_list");
        response.addProperty("ok", true);
        response.add("packs", GSON.toJsonTree(scanServerPackArchives(lang)));
        response.add("order", GSON.toJsonTree(DlcPackOrder.load(packOrderPath())));
        writeLine(writer, response);
    }

    private static void handlePackOrder(JsonObject request, BufferedWriter writer) throws IOException {
        List<String> order = new ArrayList<>();
        if (request.has("order") && request.get("order").isJsonArray()) {
            request.getAsJsonArray("order").forEach(element -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    order.add(element.getAsString());
                }
            });
        }
        boolean ok = DlcPackOrder.save(packOrderPath(), order);
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_order");
        response.addProperty("ok", ok);
        response.addProperty("message", ok ? "saved" : "save_failed");
        writeLine(writer, response);
    }

    private static void handlePackInstall(JsonObject request, BufferedWriter writer) throws IOException {
        String archivePath = request.has("archivePath") ? request.get("archivePath").getAsString() : "";
        UpdatePackActionResult result = installServerPack(archivePath);
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_install");
        response.addProperty("ok", result.ok);
        response.addProperty("message", result.message);
        writeLine(writer, response);
    }

    private static void handlePackUninstall(JsonObject request, BufferedWriter writer) throws IOException {
        String identifier = request.has("identifier") ? request.get("identifier").getAsString() : "";
        UpdatePackActionResult result = uninstallServerPack(identifier);
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_uninstall");
        response.addProperty("ok", result.ok);
        response.addProperty("message", result.message);
        writeLine(writer, response);
    }

    private static void handlePackDelete(JsonObject request, BufferedWriter writer) throws IOException {
        String archivePath = request.has("archivePath") ? request.get("archivePath").getAsString() : "";
        UpdatePackActionResult result = deleteServerPack(archivePath);
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_delete");
        response.addProperty("ok", result.ok);
        response.addProperty("message", result.message);
        writeLine(writer, response);
    }

    private static void handlePackImport(JsonObject request, BufferedWriter writer) throws IOException {
        Path root = DlcPlatformServices.gameDir();
        String raw = request.has("path") ? request.get("path").getAsString() : "";
        Path target = safeResolve(root, raw);
        boolean ok = target != null && Files.isRegularFile(target) && target.startsWith(packArchiveRoot().toAbsolutePath().normalize());
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_import");
        response.addProperty("ok", ok);
        response.addProperty("message", ok ? "" : "invalid pack archive");
        writeLine(writer, response);
    }

    private static void handlePackImportUrl(JsonObject request, BufferedWriter writer) throws IOException {
        String url = request.has("url") ? request.get("url").getAsString() : "";
        String fileName = request.has("fileName") ? request.get("fileName").getAsString() : "";
        boolean ok = false;
        String message = "";
        if (url == null || url.isBlank()) {
            message = "missing_url";
        } else {
            try {
                Path packRoot = packArchiveRoot();
                Files.createDirectories(packRoot);
                Path downloaded = DlcDownloadService.download(url, packRoot, fileName, null, null);
                ok = downloaded != null && Files.isRegularFile(downloaded);
                if (!ok) {
                    message = "download_failed";
                }
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                message = "canceled";
            } catch (Exception ex) {
                message = ex.getMessage() == null ? "download_failed" : ex.getMessage();
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_import_url");
        response.addProperty("ok", ok);
        response.addProperty("message", message);
        writeLine(writer, response);
    }

    private static void handlePackApplyStart(JsonObject request, BufferedWriter writer) throws IOException {
        List<String> order = readStringList(request, "order");
        List<String> uninstallIdentifiers = readStringList(request, "uninstallIdentifiers");
        List<String> installArchives = readStringList(request, "installArchives");
        DlcServerPackApplyStatus status = startPackApplyJob(order, uninstallIdentifiers, installArchives);
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_apply_start");
        response.addProperty("ok", true);
        response.add("status", GSON.toJsonTree(status));
        writeLine(writer, response);
    }

    private static void handlePackApplyStatus(BufferedWriter writer) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_apply_status");
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()));
        writeLine(writer, response);
    }

    private static void handlePackApplyPause(BufferedWriter writer) throws IOException {
        PackApplyJob job = packApplyJob;
        if (job != null) {
            job.pause();
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_apply_pause");
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()));
        writeLine(writer, response);
    }

    private static void handlePackApplyResume(BufferedWriter writer) throws IOException {
        PackApplyJob job = packApplyJob;
        if (job != null) {
            job.resume();
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_apply_resume");
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()));
        writeLine(writer, response);
    }

    private static void handlePackApplyCancel(BufferedWriter writer) throws IOException {
        PackApplyJob job = packApplyJob;
        if (job != null) {
            job.cancel();
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "pack_apply_cancel");
        response.add("status", GSON.toJsonTree(currentPackApplyStatus()));
        writeLine(writer, response);
    }

    private static DlcServerPackApplyStatus startPackApplyJob(List<String> order, List<String> uninstallIdentifiers, List<String> installArchives) {
        synchronized (PACK_APPLY_LOCK) {
            PackApplyJob existing = packApplyJob;
            if (existing != null && existing.isRunning()) {
                return existing.toStatus();
            }
            PackApplyJob job = new PackApplyJob(order, uninstallIdentifiers, installArchives);
            packApplyJob = job;
            Thread thread = new Thread(job::run, "dlc-server-pack-apply");
            thread.setDaemon(true);
            thread.start();
            return job.toStatus();
        }
    }

    private static DlcServerPackApplyStatus currentPackApplyStatus() {
        PackApplyJob job = packApplyJob;
        if (job != null) {
            return job.toStatus();
        }
        DlcServerPackApplyStatus status = new DlcServerPackApplyStatus();
        status.finished = true;
        status.success = true;
        status.totalCount = 0;
        status.completedCount = 0;
        status.progress = 1.0F;
        return status;
    }

    private static List<DlcServerPackInfo> scanServerPackArchives(String lang) {
        Path packRoot = packArchiveRoot();
        try {
            Files.createDirectories(packRoot);
        } catch (IOException ignored) {
        }
        Map<String, DlcPackMeta> installed = loadInstalledPacks();
        List<DlcServerPackInfo> out = new ArrayList<>();
        try (var stream = DlcPackArchive.findArchives(DlcConfig.getDlcRoot()).stream()) {
            stream.filter(Files::isRegularFile)
                .filter(path -> DlcPackArchive.isArchiveFileName(path.getFileName().toString()))
                .sorted(java.util.Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                .forEach(path -> {
                    DlcServerPackInfo info = inspectServerPackArchive(path, packRoot, installed, lang);
                    if (info != null) {
                        out.add(info);
                    }
                });
        } catch (Exception ignored) {
        }
        return out;
    }

    private static DlcServerPackInfo inspectServerPackArchive(Path archive, Path packRoot, Map<String, DlcPackMeta> installed, String lang) {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcConfig.getDlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                return null;
            }
            DlcServerPackInfo info = new DlcServerPackInfo();
            String relative = packRoot.toAbsolutePath().normalize().relativize(archive.toAbsolutePath().normalize()).toString().replace('\\', '/');
            info.archivePath = relative;
            info.orderKey = relative + "#" + result.pack.metadata.identifier + "@" + result.pack.metadata.version;
            info.fileName = archive.getFileName().toString();
            info.identifier = result.pack.metadata.identifier;
            info.version = result.pack.metadata.version;
            info.displayName = result.pack.metadata.resolveDisplayName(lang);
            info.description = result.pack.metadata.resolveDescription(lang);
            info.authors = result.pack.metadata.authors;
            info.links = new LinkedHashMap<>(result.pack.metadata.links);
            info.iconBase64 = readPackIconBase64(result.pack.iconFile);
            info.format = result.pack.format.name();
            DlcPackMeta meta = installed.get(info.identifier);
            info.installed = meta != null && info.version != null && info.version.equals(meta.version);
            info.installedVersion = meta == null ? "" : meta.version;
            return info;
        } catch (Exception ignored) {
            return null;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private static UpdatePackActionResult installServerPack(String archivePath) {
        return installServerPack(archivePath, null, null);
    }

    private static UpdatePackActionResult installServerPack(String archivePath, DlcPackInstaller.ProgressListener progressListener,
                                                            DlcDownloadService.CancelChecker cancelChecker) {
        Path archive = resolvePackArchive(archivePath);
        if (archive == null || !Files.isRegularFile(archive)) {
            return UpdatePackActionResult.fail("invalid pack archive");
        }
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcConfig.getDlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                return UpdatePackActionResult.fail(result.error == null ? "invalid DLC Pack" : result.error);
            }
            DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcConfig.getDlcRoot());
            DlcPackInstaller.PrecheckResult precheck = installer.precheck(result.pack);
            if (precheck.hasBlockingIssues()) {
                return UpdatePackActionResult.fail(describePackPrecheck(precheck));
            }
            boolean replace = loadInstalledPacks().containsKey(result.pack.metadata.identifier);
            DlcPackInstaller.InstallOptions options = precheck.requiresRuntimeConfirmation()
                ? DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges()
                : DlcPackInstaller.InstallOptions.strict();
            options = options.withProgressListener(progressListener).withCancelChecker(cancelChecker);
            installer.install(result.pack, replace, options);
            return UpdatePackActionResult.ok("installed");
        } catch (Exception ex) {
            return UpdatePackActionResult.fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private static UpdatePackActionResult uninstallServerPack(String identifier) {
        return uninstallServerPack(identifier, null);
    }

    private static UpdatePackActionResult uninstallServerPack(String identifier, DlcPackInstaller.ProgressListener progressListener) {
        if (identifier == null || identifier.isBlank()) {
            return UpdatePackActionResult.fail("missing identifier");
        }
        try {
            DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcConfig.getDlcRoot());
            installer.uninstall(identifier, progressListener);
            return UpdatePackActionResult.ok("uninstalled");
        } catch (Exception ex) {
            return UpdatePackActionResult.fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private static UpdatePackActionResult deleteServerPack(String archivePath) {
        Path archive = resolvePackArchive(archivePath);
        if (archive == null || !Files.isRegularFile(archive)) {
            return UpdatePackActionResult.fail("invalid pack archive");
        }
        String identifier = null;
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcConfig.getDlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (result.valid && result.pack != null) {
                identifier = result.pack.metadata.identifier;
            }
        } catch (Exception ignored) {
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
        try {
            if (identifier != null) {
                DlcPackMeta installed = DlcPackMeta.load(DlcConfig.getDlcRoot().resolve("tmp").resolve("pack").resolve(identifier).resolve("meta.dc"));
                if (installed != null) {
                    DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcConfig.getDlcRoot());
                    installer.uninstall(identifier);
                }
            }
            Files.deleteIfExists(archive);
            return UpdatePackActionResult.ok("deleted");
        } catch (Exception ex) {
            return UpdatePackActionResult.fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private static Map<String, DlcPackMeta> loadInstalledPacks() {
        Path root = DlcConfig.getDlcRoot().resolve("tmp").resolve("pack");
        Map<String, DlcPackMeta> out = new HashMap<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                DlcPackMeta meta = DlcPackMeta.load(dir.resolve("meta.dc"));
                if (meta != null && meta.identifier != null && !meta.identifier.isBlank()) {
                    out.put(meta.identifier, meta);
                }
            });
        } catch (IOException ignored) {
        }
        return out;
    }

    private static Path packArchiveRoot() {
        return DlcConfig.getDlcRoot().resolve("pack").toAbsolutePath().normalize();
    }

    private static Path packOrderPath() {
        return DlcPaths.migrateLegacyConfigFile("order.dc");
    }

    private static String readPackIconBase64(Path iconFile) {
        if (iconFile == null || !Files.isRegularFile(iconFile)) {
            return null;
        }
        try {
            long size = Files.size(iconFile);
            if (size <= 0L || size > 1024L * 1024L) {
                return null;
            }
            return Base64.getEncoder().encodeToString(Files.readAllBytes(iconFile));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Path resolvePackArchive(String archivePath) {
        if (archivePath == null || archivePath.isBlank()) return null;
        try {
            Path local = packArchiveRoot();
            Path downloaded = DlcConfig.getDlcRoot().resolve("downloaded/DLCs").toAbsolutePath().normalize();
            Path target = local.resolve(archivePath).toAbsolutePath().normalize();
            return target.startsWith(local) || target.startsWith(downloaded) ? target : null;
        } catch (java.nio.file.InvalidPathException exception) { return null; }
    }

    private static String describePackPrecheck(DlcPackInstaller.PrecheckResult precheck) {
        List<String> lines = new ArrayList<>();
        if (!precheck.getIncompatibleVersions().isEmpty()) {
            lines.add("incompatible: " + String.join(", ", precheck.getIncompatibleVersions()));
        }
        if (!precheck.getMissingDependencies().isEmpty()) {
            lines.add("missing dependencies: " + String.join(", ", precheck.getMissingDependencies()));
        }
        if (!precheck.getDlcToEnable().isEmpty()) {
            lines.add("enable DLC: " + String.join(", ", precheck.getDlcToEnable()));
        }
        if (!precheck.getPresetsToEnable().isEmpty()) {
            lines.add("enable presets: " + String.join(", ", precheck.getPresetsToEnable()));
        }
        if (!precheck.getDlcToDisable().isEmpty()) {
            lines.add("disable DLC: " + String.join(", ", precheck.getDlcToDisable()));
        }
        if (!precheck.getPresetsToDisable().isEmpty()) {
            lines.add("disable presets: " + String.join(", ", precheck.getPresetsToDisable()));
        }
        return lines.isEmpty() ? "precheck failed" : String.join("; ", lines);
    }

    private static PushState handlePushInit(JsonObject request, BufferedWriter writer) throws IOException {
        String id = request.has("id") ? request.get("id").getAsString() : null;
        String mode = request.has("mode") ? request.get("mode").getAsString() : "overwrite";
        List<String> targets = new ArrayList<>();
        if (request.has("targets") && request.get("targets").isJsonArray()) {
            request.getAsJsonArray("targets").forEach(element -> {
                if (element.isJsonPrimitive()) {
                    targets.add(element.getAsString());
                }
            });
        }
        List<String> paths = new ArrayList<>();
        if (request.has("paths") && request.get("paths").isJsonArray()) {
            request.getAsJsonArray("paths").forEach(element -> {
                if (element.isJsonPrimitive()) {
                    paths.add(element.getAsString());
                }
            });
        }
        Path dlcRoot = DlcConfig.getDlcRoot();
        Path tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException ignored) {
        }
        Path tempFile = tempDir.resolve("push_" + id + ".bin");
        PushState state = new PushState(id, mode, targets, paths, tempFile);
        JsonObject response = new JsonObject();
        response.addProperty("type", "push_init");
        response.addProperty("ok", true);
        writeLine(writer, response);
        return state;
    }

    private static void handlePushChunk(JsonObject request, PushState state, BufferedWriter writer) throws IOException {
        if (state == null) {
            return;
        }
        String id = request.has("id") ? request.get("id").getAsString() : null;
        if (id == null || !id.equals(state.id)) {
            return;
        }
        String data = request.has("data") ? request.get("data").getAsString() : null;
        if (data == null || data.isBlank()) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(data);
        Files.write(state.tempFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void handlePushFinish(PushState state, BufferedWriter writer) throws IOException {
        boolean ok = false;
        if (state != null) {
            ok = applyPushZip(state);
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "push_finish");
        response.addProperty("ok", ok);
        writeLine(writer, response);
    }

    private static boolean applyPushZip(PushState state) {
        if (state.tempFile == null || !Files.exists(state.tempFile)) {
            return false;
        }
        Path dlcRoot = DlcConfig.getDlcRoot();
        Path configDir = DlcPlatformServices.configDir().resolve("dlc_manager");
        Path gameDir = DlcPlatformServices.gameDir();
        boolean includeAll = state.targets.stream().anyMatch(value -> "all".equalsIgnoreCase(value));
        boolean includeDlc = includeAll || state.targets.stream().anyMatch(value -> "dlc".equalsIgnoreCase(value));
        boolean includeConfig = includeAll || state.targets.stream().anyMatch(value -> "config".equalsIgnoreCase(value));
        boolean includeFiles = state.paths != null && !state.paths.isEmpty();
        String mode = state.mode == null ? "overwrite" : state.mode;
        try (DlcServerOps.ServerOpContext op = DlcServerOps.begin(dlcRoot, gameDir, "push")) {
            Files.createDirectories(configDir);
            try (InputStream in = Files.newInputStream(state.tempFile); ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.isBlank()) {
                        zip.closeEntry();
                        continue;
                    }
                    Path targetRoot = null;
                    String relativeName = null;
                    if (includeDlc && name.startsWith("dlc/")) {
                        targetRoot = dlcRoot;
                        relativeName = name.substring("dlc/".length());
                    } else if (includeConfig && name.startsWith("config/")) {
                        targetRoot = configDir;
                        relativeName = name.substring("config/".length());
                    } else if (includeFiles && name.startsWith("root/")) {
                        targetRoot = gameDir;
                        relativeName = name.substring("root/".length());
                    }
                    if (targetRoot == null || relativeName == null || relativeName.isBlank()) {
                        zip.closeEntry();
                        continue;
                    }
                    Path target = safeResolve(targetRoot, relativeName);
                    if (target == null) {
                        zip.closeEntry();
                        continue;
                    }
                    if (entry.isDirectory()) {
                        try {
                            Files.createDirectories(target);
                        } catch (IOException ignored) {
                        }
                        zip.closeEntry();
                        continue;
                    }
                    boolean exists = Files.exists(target);
                    if (exists && "diff".equalsIgnoreCase(mode)) {
                        drainEntry(zip);
                        op.log("SKIP " + op.displayPath(target));
                        zip.closeEntry();
                        continue;
                    }
                    if (exists) {
                        op.backupIfExists(target);
                    }
                    try {
                        Files.createDirectories(target.getParent());
                        writeEntry(zip, target);
                        op.log("WRITE " + op.displayPath(target));
                    } catch (IOException ignored) {
                    }
                    zip.closeEntry();
                }
            }
        } catch (IOException ignored) {
            return false;
        } finally {
            try {
                Files.deleteIfExists(state.tempFile);
            } catch (IOException ignored) {
            }
        }
        return true;
    }

    private static void handleFileList(JsonObject request, BufferedWriter writer) throws IOException {
        Path root = DlcPlatformServices.gameDir();
        String raw = request.has("path") ? request.get("path").getAsString() : "";
        Path target = safeResolve(root, raw);
        if (target == null) {
            writeError(writer, "invalid path");
            return;
        }
        JsonArray entries = new JsonArray();
        if (Files.exists(target) && Files.isDirectory(target)) {
            try (var stream = Files.list(target)) {
                stream.forEach(path -> {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", path.getFileName().toString());
                    entry.addProperty("dir", Files.isDirectory(path));
                    try {
                        entry.addProperty("size", Files.isDirectory(path) ? 0L : Files.size(path));
                    } catch (IOException ignored) {
                        entry.addProperty("size", 0L);
                    }
                    try {
                        entry.addProperty("modified", Files.getLastModifiedTime(path).toMillis());
                    } catch (IOException ignored) {
                        entry.addProperty("modified", 0L);
                    }
                    entries.add(entry);
                });
            } catch (IOException ignored) {
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "file_list");
        response.addProperty("ok", true);
        response.addProperty("path", raw == null ? "" : raw);
        response.add("entries", entries);
        writeLine(writer, response);
    }

    private static void handleFileDelete(JsonObject request, BufferedWriter writer) throws IOException {
        Path root = DlcPlatformServices.gameDir();
        String raw = request.has("path") ? request.get("path").getAsString() : "";
        Path target = safeResolve(root, raw);
        if (target == null || !Files.exists(target)) {
            writeError(writer, "invalid path");
            return;
        }
        Path dlcRoot = DlcConfig.getDlcRoot();
        try (DlcServerOps.ServerOpContext op = DlcServerOps.begin(dlcRoot, root, "delete")) {
            op.backupIfExists(target);
            op.deleteRecursive(target);
            JsonObject response = new JsonObject();
            response.addProperty("type", "file_delete");
            response.addProperty("ok", true);
            writeLine(writer, response);
        }
    }

    private static UploadState handleFileUploadInit(JsonObject request, BufferedWriter writer) throws IOException {
        String id = request.has("id") ? request.get("id").getAsString() : null;
        String raw = request.has("path") ? request.get("path").getAsString() : "";
        Path root = DlcPlatformServices.gameDir();
        Path target = safeResolve(root, raw);
        if (target == null) {
            writeError(writer, "invalid path");
            return null;
        }
        Path dlcRoot = DlcConfig.getDlcRoot();
        Path tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException ignored) {
        }
        Path tempFile = tempDir.resolve("upload_" + id + ".bin");
        UploadState state = new UploadState(id, tempFile, target);
        JsonObject response = new JsonObject();
        response.addProperty("type", "file_upload_init");
        response.addProperty("ok", true);
        writeLine(writer, response);
        return state;
    }

    private static void handleFileUploadChunk(JsonObject request, UploadState state, BufferedWriter writer) throws IOException {
        if (state == null) {
            return;
        }
        String id = request.has("id") ? request.get("id").getAsString() : null;
        if (id == null || !id.equals(state.id)) {
            return;
        }
        String data = request.has("data") ? request.get("data").getAsString() : null;
        if (data == null || data.isBlank()) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(data);
        Files.write(state.tempFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void handleFileUploadFinish(UploadState state, BufferedWriter writer) throws IOException {
        boolean ok = false;
        if (state != null && state.tempFile != null) {
            Path root = DlcPlatformServices.gameDir();
            Path dlcRoot = DlcConfig.getDlcRoot();
            try (DlcServerOps.ServerOpContext op = DlcServerOps.begin(dlcRoot, root, "upload")) {
                Path target = state.targetPath;
                if (target != null) {
                    if (Files.exists(target)) {
                        op.backupIfExists(target);
                    }
                    Files.createDirectories(target.getParent());
                    Files.move(state.tempFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    op.log("UPLOAD " + op.displayPath(target));
                    ok = true;
                }
            } catch (IOException ignored) {
                ok = false;
            } finally {
                try {
                    Files.deleteIfExists(state.tempFile);
                } catch (IOException ignored) {
                }
            }
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "file_upload_finish");
        response.addProperty("ok", ok);
        writeLine(writer, response);
    }

    private static void handleFileDownload(JsonObject request, BufferedWriter writer) throws IOException {
        Path root = DlcPlatformServices.gameDir();
        String raw = request.has("path") ? request.get("path").getAsString() : "";
        Path target = safeResolve(root, raw);
        if (target == null || !Files.exists(target)) {
            writeError(writer, "invalid path");
            return;
        }
        boolean isDir = Files.isDirectory(target);
        Path dlcRoot = DlcConfig.getDlcRoot();
        Path sendFile = target;
        Path tempZip = null;
        if (isDir) {
            Path tempDir = DlcTmpPaths.resolve(dlcRoot, "server_ops");
            try {
                Files.createDirectories(tempDir);
            } catch (IOException ignored) {
            }
            tempZip = tempDir.resolve("download_" + System.currentTimeMillis() + ".zip");
            try {
                zipDirectory(target, tempZip);
                sendFile = tempZip;
            } catch (IOException ignored) {
                writeError(writer, "zip failed");
                return;
            }
        }
        long size;
        try {
            size = Files.size(sendFile);
        } catch (IOException e) {
            writeError(writer, "invalid path");
            return;
        }
        try (DlcServerOps.ServerOpContext op = DlcServerOps.begin(dlcRoot, root, "download")) {
            op.log("DOWNLOAD " + op.displayPath(target));
        }
        JsonObject init = new JsonObject();
        init.addProperty("type", "file_download_init");
        init.addProperty("ok", true);
        init.addProperty("name", target.getFileName().toString());
        init.addProperty("size", size);
        init.addProperty("dir", isDir);
        writeLine(writer, init);
        try (InputStream in = Files.newInputStream(sendFile)) {
            byte[] buffer = new byte[FILE_CHUNK_SIZE];
            int len;
            while ((len = in.read(buffer)) >= 0) {
                JsonObject chunk = new JsonObject();
                chunk.addProperty("type", "file_download_chunk");
                String data = Base64.getEncoder().encodeToString(len == buffer.length ? buffer : Arrays.copyOf(buffer, len));
                chunk.addProperty("data", data);
                writeLine(writer, chunk);
            }
        } catch (IOException ignored) {
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
        }
        JsonObject finish = new JsonObject();
        finish.addProperty("type", "file_download_finish");
        finish.addProperty("ok", true);
        writeLine(writer, finish);
    }

    private static void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(sourceDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    Path relative = sourceDir.relativize(path);
                    ZipEntry entry = new ZipEntry(relative.toString().replace("\\", "/"));
                    try (InputStream in = Files.newInputStream(path)) {
                        zip.putNextEntry(entry);
                        byte[] buffer = new byte[FILE_CHUNK_SIZE];
                        int len;
                        while ((len = in.read(buffer)) >= 0) {
                            zip.write(buffer, 0, len);
                        }
                        zip.closeEntry();
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    private static void handleRconGet(BufferedWriter writer) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "rcon_get");
        response.addProperty("ok", true);
        response.addProperty("host", DlcConfig.getServerRconHost());
        response.addProperty("port", DlcConfig.getServerRconPort());
        response.addProperty("password", DlcConfig.getServerRconPassword());
        writeLine(writer, response);
    }

    private static void handleRconInfo(BufferedWriter writer) throws IOException {
        DlcServerProperties.RconInfo info = DlcServerProperties.readRconInfo();
        JsonObject response = new JsonObject();
        response.addProperty("type", "rcon_info");
        response.addProperty("ok", true);
        response.addProperty("enabled", info.enabled);
        response.addProperty("port", info.port);
        response.addProperty("passwordSet", info.passwordSet);
        writeLine(writer, response);
    }

    private static void handleRconSet(JsonObject request, BufferedWriter writer) throws IOException {
        String host = request.has("host") ? request.get("host").getAsString() : "";
        int port = request.has("port") ? request.get("port").getAsInt() : DlcConfig.getServerRconPort();
        String password = request.has("password") ? request.get("password").getAsString() : "";
        DlcConfig.setServerRconHost(host);
        DlcConfig.setServerRconPort(port);
        DlcConfig.setServerRconPassword(password);
        DlcConfig.saveServer();
        JsonObject response = new JsonObject();
        response.addProperty("type", "rcon_set");
        response.addProperty("ok", true);
        writeLine(writer, response);
    }

    private static void handleConfigGet(BufferedWriter writer) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "config_get");
        response.addProperty("ok", true);
        response.add("config", DlcConfig.buildServerConfigJson());
        writeLine(writer, response);
    }

    private static void handleConfigSet(JsonObject request, BufferedWriter writer) throws IOException {
        boolean ok = false;
        if (request.has("config") && request.get("config").isJsonObject()) {
            JsonObject config = request.getAsJsonObject("config");
            DlcConfig.applySharedConfigJson(config);
            DlcConfig.applyServerConfigJson(config);
            DlcConfig.saveShared();
            DlcConfig.saveServer();
            ok = true;
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "config_set");
        response.addProperty("ok", ok);
        writeLine(writer, response);
    }

    private static void handleRconExec(JsonObject request, BufferedWriter writer) throws IOException {
        String command = request.has("command") ? request.get("command").getAsString() : "";
        String message = null;
        boolean ok = false;
        String host = DlcConfig.getServerRconHost();
        String password = DlcConfig.getServerRconPassword();
        int port = DlcConfig.getServerRconPort();
        if (host != null && !host.isBlank() && password != null && !password.isBlank()) {
            message = DlcRconClient.execute(host, port, password, command, 5000);
            ok = message != null;
        } else {
            ok = executeServerCommand(command);
            message = ok ? "ok" : "failed";
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "rcon_exec");
        response.addProperty("ok", ok);
        response.addProperty("message", message == null ? "" : message);
        writeLine(writer, response);
    }

    private static void handleServerRestart(BufferedWriter writer) throws IOException {
        MinecraftServer server = currentServer;
        boolean ok = false;
        if (server != null) {
            LOGGER.info("[dlc-manager] Applying required resources/DLC, restarting server...");
            server.halt(false);
            ok = true;
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "server_restart");
        response.addProperty("ok", ok);
        writeLine(writer, response);
    }

    private static boolean executeServerCommand(String command) {
        return DlcServerCompat.executeCommand(currentServer, command);
    }

    private static Path safeResolve(Path root, String raw) {
        if (root == null) {
            return null;
        }
        String value = raw == null ? "" : raw.replace('\\', '/');
        Path resolved = value.isBlank() ? root : root.resolve(value);
        resolved = resolved.normalize();
        if (!resolved.startsWith(root.normalize())) {
            return null;
        }
        return resolved;
    }

    private static void writeEntry(InputStream zip, Path target) throws IOException {
        try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private static void drainEntry(InputStream zip) throws IOException {
        byte[] buffer = new byte[8192];
        while (zip.read(buffer) > 0) {
        }
    }

    private static void writeError(BufferedWriter writer, String message) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "error");
        response.addProperty("ok", false);
        response.addProperty("message", message);
        writeLine(writer, response);
    }

    private static void writeLine(BufferedWriter writer, JsonObject obj) throws IOException {
        writer.write(GSON.toJson(obj));
        writer.newLine();
        writer.flush();
    }

    private static class PushState {
        private final String id;
        private final String mode;
        private final List<String> targets;
        private final List<String> paths;
        private final Path tempFile;

        private PushState(String id, String mode, List<String> targets, List<String> paths, Path tempFile) {
            this.id = id;
            this.mode = mode;
            this.targets = targets == null ? List.of() : targets;
            this.paths = paths == null ? List.of() : paths;
            this.tempFile = tempFile;
        }
    }

    private static class UploadState {
        private final String id;
        private final Path tempFile;
        private final Path targetPath;

        private UploadState(String id, Path tempFile, Path targetPath) {
            this.id = id;
            this.tempFile = tempFile;
            this.targetPath = targetPath;
        }
    }

    private static class PackApplyJob {
        private final List<String> order;
        private final List<String> uninstallIdentifiers;
        private final List<String> installArchives;
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
        private final Object pauseLock = new Object();
        private volatile boolean running = true;
        private volatile boolean finished;
        private volatile boolean success;
        private volatile boolean canceled;
        private volatile boolean paused;
        private volatile int totalCount;
        private volatile int completedCount;
        private volatile float progress;
        private volatile String message = "dlc_manager.pack.operation_applying";
        private volatile String detail;
        private volatile String error;

        private PackApplyJob(List<String> order, List<String> uninstallIdentifiers, List<String> installArchives) {
            this.order = order == null ? List.of() : order;
            this.uninstallIdentifiers = uninstallIdentifiers == null ? List.of() : uninstallIdentifiers;
            this.installArchives = installArchives == null ? List.of() : installArchives;
            this.totalCount = Math.max(1, this.uninstallIdentifiers.size() + this.installArchives.size());
        }

        private boolean isRunning() {
            return running;
        }

        private void cancel() {
            cancelRequested.set(true);
            message = "dlc_manager.pack.canceling";
            detail = null;
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }

        private void pause() {
            if (!running || finished) {
                return;
            }
            pauseRequested.set(true);
            paused = true;
            message = "dlc_manager.pack.paused";
            detail = null;
        }

        private void resume() {
            pauseRequested.set(false);
            paused = false;
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }

        private void run() {
            try {
                checkCanceled();
                message = "dlc_manager.pack.operation_applying";
                detail = null;
                progress = 0.0F;
                if (!DlcPackOrder.save(packOrderPath(), order)) {
                    throw new IOException("save order failed");
                }
                boolean hasUninstallPhase = !uninstallIdentifiers.isEmpty();
                boolean hasInstallPhase = !installArchives.isEmpty();
                float uninstallTotalSpan = hasUninstallPhase && hasInstallPhase ? 0.35F : hasUninstallPhase ? 1.0F : 0.0F;
                float installTotalSpan = hasUninstallPhase && hasInstallPhase ? 0.65F : hasInstallPhase ? 1.0F : 0.0F;
                float uninstallSpan = uninstallIdentifiers.isEmpty() ? 0.0F : uninstallTotalSpan / (float) uninstallIdentifiers.size();
                float installSpan = installArchives.isEmpty() ? 0.0F : installTotalSpan / (float) installArchives.size();
                float phaseOffset = 0.0F;
                for (String identifier : uninstallIdentifiers) {
                    checkCanceled();
                    float offset = phaseOffset;
                    runStep("dlc_manager.pack.operation_uninstalling", offset, uninstallSpan, progressUpdater -> {
                        UpdatePackActionResult result = uninstallServerPack(identifier, (status, value) -> {
                            waitIfPaused();
                            detail = status;
                            progressUpdater.update(normalizeProgressStatus(status, "dlc_manager.pack.operation_uninstalling"), value);
                        });
                        if (!result.ok) {
                            throw new IOException(result.message);
                        }
                    });
                    phaseOffset += uninstallSpan;
                }
                for (String archive : installArchives) {
                    checkCanceled();
                    float offset = phaseOffset;
                    runStep("dlc_manager.pack.operation_installing", offset, installSpan, progressUpdater -> {
                        UpdatePackActionResult result = installServerPack(
                            archive,
                            (status, value) -> {
                                waitIfPaused();
                                detail = status;
                                progressUpdater.update(normalizeProgressStatus(status, "dlc_manager.pack.operation_installing"), value);
                            },
                            () -> {
                                waitIfPaused();
                                return cancelRequested.get();
                            }
                        );
                        if (!result.ok) {
                            throw new IOException(result.message);
                        }
                    });
                    phaseOffset += installSpan;
                }
                success = true;
                progress = 1.0F;
                message = "dlc_manager.pack.apply_success";
                detail = null;
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                canceled = true;
                error = "canceled";
                message = "dlc_manager.pack.canceled";
                detail = null;
            } catch (Exception ex) {
                error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                message = error;
            } finally {
                running = false;
                finished = true;
            }
        }

        private void runStep(String label, float offset, float span, ThrowingStep action) throws Exception {
            waitIfPaused();
            message = label;
            detail = null;
            updateProgress(offset, span, -1.0F);
            action.run((status, value) -> {
                waitIfPaused();
                message = status;
                updateProgress(offset, span, value);
            });
            completedCount++;
            updateProgress(offset, span, 1.0F);
        }

        private void updateProgress(float offset, float span, float stepProgress) {
            if (stepProgress < 0.0F) {
                progress = -1.0F;
                return;
            }
            progress = Math.max(0.0F, Math.min(1.0F, offset + Math.max(0.0F, Math.min(1.0F, stepProgress)) * span));
        }

        private void checkCanceled() throws DlcDownloadService.DownloadCanceledException {
            waitIfPaused();
            if (cancelRequested.get()) {
                throw new DlcDownloadService.DownloadCanceledException("canceled");
            }
        }

        private void waitIfPaused() {
            while (pauseRequested.get() && !cancelRequested.get()) {
                paused = true;
                message = "dlc_manager.pack.paused";
                detail = null;
                synchronized (pauseLock) {
                    try {
                        pauseLock.wait(200L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            paused = false;
        }

        private String normalizeProgressStatus(String status, String fallback) {
            String raw = status == null ? "" : status.trim();
            if (raw.isEmpty()) {
                return fallback;
            }
            if (raw.startsWith("dlc_manager.")) {
                return raw;
            }
            if (raw.startsWith("Uninstalling") || raw.startsWith("Restoring") || raw.startsWith("Uninstalled")) {
                return "dlc_manager.pack.operation_uninstalling";
            }
            if (raw.startsWith("Downloading") || raw.startsWith("Resolved") || raw.startsWith("Preparing")
                || raw.startsWith("Backing up") || raw.startsWith("Installing") || raw.startsWith("Writing metadata")
                || raw.startsWith("Installed")) {
                return "dlc_manager.pack.operation_installing";
            }
            if (raw.startsWith("Applying") || raw.startsWith("Saving") || raw.startsWith("Applied")) {
                return "dlc_manager.pack.operation_applying";
            }
            if (raw.startsWith("Canceling") || raw.startsWith("Canceled")) {
                return "dlc_manager.pack.canceling";
            }
            if (raw.startsWith("Paused")) {
                return "dlc_manager.pack.paused";
            }
            return fallback;
        }

        private DlcServerPackApplyStatus toStatus() {
            DlcServerPackApplyStatus status = new DlcServerPackApplyStatus();
            status.running = running;
            status.finished = finished;
            status.success = success;
            status.canceled = canceled;
            status.paused = paused;
            status.totalCount = totalCount;
            status.completedCount = completedCount;
            status.progress = progress;
            status.message = message;
            status.detail = detail;
            status.error = error;
            return status;
        }
    }

    private interface ProgressUpdater {
        void update(String status, float progress);
    }

    private interface ThrowingStep {
        void run(ProgressUpdater progressUpdater) throws Exception;
    }

    private static class UpdatePackActionResult {
        private final boolean ok;
        private final String message;

        private UpdatePackActionResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        private static UpdatePackActionResult ok(String message) {
            return new UpdatePackActionResult(true, message);
        }

        private static UpdatePackActionResult fail(String message) {
            return new UpdatePackActionResult(false, message);
        }
    }
}
