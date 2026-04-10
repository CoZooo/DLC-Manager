package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.ApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class DlcServerApi {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static HttpServer httpServer;
    private static ExecutorService clientPool;
    private static volatile MinecraftServer currentServer;
    private static volatile Runnable clientStateChangedListener;

    private DlcServerApi() {
    }

    public static synchronized void start(MinecraftServer server) {
        currentServer = server;
        if (!DlcPathConfig.isApiEnabled()) {
            LOGGER.info("[dlc-manager] API disabled; skip DlcServerApi start");
            return;
        }
        if (DlcPathConfig.getApiPort() == 0) {
            LOGGER.info("[dlc-manager] HTTP API disabled because apiPort is 0; Java API remains available");
            return;
        }
        if (RUNNING.get()) {
            if (httpServer != null) {
                return;
            }
            RUNNING.set(false);
        }
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress(DlcPathConfig.getApiPort()), 0);
            created.createContext("/api", new ApiHandler());
            clientPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "dlc-manager-api-client");
                thread.setDaemon(true);
                return thread;
            });
            created.setExecutor(clientPool);
            created.start();
            httpServer = created;
            RUNNING.set(true);
            if (DlcPathConfig.getApiToken() == null || DlcPathConfig.getApiToken().isBlank()) {
                LOGGER.warn("[dlc-manager] DlcServerApi started on port {} with empty token; all requests will be rejected until apiToken is configured", DlcPathConfig.getApiPort());
            } else {
                LOGGER.info("[dlc-manager] DlcServerApi started on port {}", DlcPathConfig.getApiPort());
            }
        } catch (IOException error) {
            RUNNING.set(false);
            httpServer = null;
            if (clientPool != null) {
                clientPool.shutdownNow();
                clientPool = null;
            }
            LOGGER.warn("[dlc-manager] failed to start DlcServerApi", error);
        }
    }

    public static synchronized void stop() {
        RUNNING.set(false);
        currentServer = null;
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (clientPool != null) {
            clientPool.shutdownNow();
            clientPool = null;
        }
    }

    public static void setClientStateChangedListener(Runnable listener) {
        clientStateChangedListener = listener;
    }

    static MinecraftServer currentServer() {
        return currentServer;
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    static void notifyClientStateChanged() {
        Runnable listener = clientStateChangedListener;
        if (listener == null) {
            return;
        }
        try {
            listener.run();
        } catch (Throwable error) {
            LOGGER.debug("[dlc-manager] client state changed listener failed", error);
        }
    }

    private static final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                addCors(exchange.getResponseHeaders());
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 204, null);
                    return;
                }
                if (!isAuthorized(exchange)) {
                    sendError(exchange, 401, "unauthorized", "Invalid or missing API token.");
                    return;
                }
                String path = normalizePath(exchange.getRequestURI());
                String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
                if ("GET".equals(method) && "/api/ping".equals(path)) {
                    handlePing(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/version".equals(path)) {
                    handleVersion(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/state".equals(path)) {
                    handleState(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/dlc/list".equals(path)) {
                    handleDlcList(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/dlc/pending".equals(path)) {
                    handleDlcPending(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/dlc/root/list".equals(path)) {
                    handleDlcRootList(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dlc/apply".equals(path)) {
                    handleDlcApply(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dlc/enable".equals(path)) {
                    handleDlcSetEnabled(exchange, true);
                    return;
                }
                if ("POST".equals(method) && "/api/dlc/disable".equals(path)) {
                    handleDlcSetEnabled(exchange, false);
                    return;
                }
                if ("GET".equals(method) && "/api/remote/list".equals(path)) {
                    handleRemoteList(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/remote/download".equals(path)) {
                    handleRemoteDownload(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/remote/download/start".equals(path)) {
                    handleRemoteDownloadStart(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/remote/download/status".equals(path)) {
                    handleRemoteDownloadStatus(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/required/list".equals(path)) {
                    handleRequiredList(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/required/download/start".equals(path)) {
                    handleRequiredDownloadStart(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/required/download/status".equals(path)) {
                    handleRequiredDownloadStatus(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/required/download/cancel".equals(path)) {
                    handleRequiredDownloadCancel(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/required/uninstall".equals(path)) {
                    handleRequiredUninstall(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/required/apply".equals(path)) {
                    handleRequiredApply(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/update/state".equals(path)) {
                    handleUpdateState(exchange);
                    return;
                }
                if ("GET".equals(method) && "/api/update/list".equals(path)) {
                    handleUpdateList(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/update/apply".equals(path)) {
                    handleUpdateApply(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/update/rollback".equals(path)) {
                    handleUpdateRollback(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/update/import".equals(path)) {
                    handleUpdateImport(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/update/import-url".equals(path)) {
                    handleUpdateImportUrl(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dc/detect-type".equals(path)) {
                    handleDetectType(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dc/validate".equals(path)) {
                    handleValidate(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dc/read".equals(path)) {
                    handleReadDc(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dc/write".equals(path)) {
                    handleWriteDc(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/notice/content".equals(path)) {
                    handleNoticeContent(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/file/add".equals(path)) {
                    handleFileAdd(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/file/del".equals(path)) {
                    handleFileDelete(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/file/mv".equals(path)) {
                    handleFileMove(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/file/rename".equals(path)) {
                    handleFileRename(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/file/upload".equals(path)) {
                    handleFileUpload(exchange);
                    return;
                }
                sendError(exchange, 404, "not_found", "Unknown API route.");
            } catch (Exception error) {
                LOGGER.warn("[dlc-manager] DlcServerApi request failed", error);
                sendError(exchange, 500, "internal_error", "Internal server error.");
            } finally {
                exchange.close();
            }
        }
    }

    private static void handlePing(HttpExchange exchange) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("service", "dlc-api");
        data.addProperty("running", RUNNING.get());
        data.addProperty("mode", currentServer != null && currentServer.isDedicatedServer() ? "server" : "client");
        sendOk(exchange, data);
    }

    private static void handleVersion(HttpExchange exchange) throws IOException {
        Path dlcRoot = DlcPathConfig.getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        JsonObject data = new JsonObject();
        data.addProperty("base", version == null ? "" : version.getBase());
        data.addProperty("dlc", version == null ? "" : version.getDlc());
        data.addProperty("rollback_from", version == null ? "" : blank(version.getRollbackFrom()));
        data.addProperty("changelog_shown", version == null ? "" : blank(version.getChangelogShown()));
        data.addProperty("label", DlcServerService.getServerVersionLabel());
        sendOk(exchange, data);
    }

    private static void handleState(HttpExchange exchange) throws IOException {
        Path dlcRoot = DlcPathConfig.getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
        int total = 0;
        int enabled = 0;
        JsonObject counts = new JsonObject();
        for (DlcType type : DlcType.values()) {
            List<DlcItem> items = grouped.getOrDefault(type, List.of());
            int count = items == null ? 0 : items.size();
            counts.addProperty(type.name().toLowerCase(Locale.ROOT), count);
            total += count;
            if (items != null) {
                for (DlcItem item : items) {
                    if (item != null && item.isEnabled()) {
                        enabled++;
                    }
                }
            }
        }
        JsonObject data = new JsonObject();
        data.addProperty("serverRunning", currentServer != null && currentServer.isRunning());
        data.addProperty("dedicatedServer", currentServer != null && currentServer.isDedicatedServer());
        data.addProperty("totalItems", total);
        data.addProperty("enabledItems", enabled);
        data.add("counts", counts);
        sendOk(exchange, data);
    }

    private static void handleDlcList(HttpExchange exchange) throws IOException {
        Map<DlcType, List<DlcItem>> grouped = scanDlcItemsForApi();
        JsonObject data = new JsonObject();
        JsonArray groups = new JsonArray();
        for (DlcType type : DlcType.values()) {
            JsonObject group = new JsonObject();
            group.addProperty("type", type.name().toLowerCase(Locale.ROOT));
            JsonArray items = new JsonArray();
            for (DlcItem item : grouped.getOrDefault(type, List.of())) {
                if (item == null) {
                    continue;
                }
                JsonObject json = new JsonObject();
                json.addProperty("id", blank(item.getId()));
                json.addProperty("identifier", blank(item.getIdentifier()));
                json.addProperty("name", blank(item.getName()));
                json.addProperty("type", item.getType().name().toLowerCase(Locale.ROOT));
                json.addProperty("enabled", item.isEnabled());
                json.addProperty("baselineEnabled", Boolean.TRUE.equals(DlcRuntimeState.getBaselineEnabled(item.getId())));
                json.addProperty("pending", DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled()));
                json.addProperty("hidden", item.isHidden());
                json.addProperty("configType", item.isConfigType());
                json.addProperty("alarm", item.isAlarm());
                json.addProperty("allowEnableByAll", item.AllowEnableByAll());
                json.addProperty("appliedTarget", blank(item.getAppliedTargetRaw()));
                json.addProperty("sourcePath", item.getSourcePath() == null ? "" : item.getSourcePath().toString());
                json.add("sourcePaths", toStringArray(item.getSourcePaths()));
                json.add("displayNames", toJsonObject(item.getDisplayNames()));
                json.add("tips", toJsonObject(item.getTooltips()));
                json.add("dependencies", toStringArray(item.getDependencies()));
                json.add("conflicts", toStringArray(item.getConflicts()));
                items.add(json);
            }
            group.add("items", items);
            groups.add(group);
        }
        data.add("groups", groups);
        sendOk(exchange, data);
    }

    private static void handleDlcPending(HttpExchange exchange) throws IOException {
        Map<DlcType, List<DlcItem>> grouped = scanDlcItemsForApi();
        JsonArray items = new JsonArray();
        for (DlcType type : DlcType.values()) {
            for (DlcItem item : grouped.getOrDefault(type, List.of())) {
                if (item == null || item.getId() == null || item.getId().isBlank()) {
                    continue;
                }
                if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                    continue;
                }
                JsonObject json = new JsonObject();
                json.addProperty("id", blank(item.getId()));
                json.addProperty("identifier", blank(item.getIdentifier()));
                json.addProperty("name", resolveApiDisplayName(item));
                json.addProperty("type", item.getType().name().toLowerCase(Locale.ROOT));
                json.addProperty("enabled", item.isEnabled());
                json.addProperty("baselineEnabled", Boolean.TRUE.equals(DlcRuntimeState.getBaselineEnabled(item.getId())));
                json.addProperty("pending", true);
                items.add(json);
            }
        }
        JsonObject data = new JsonObject();
        data.add("items", items);
        data.addProperty("count", items.size());
        sendOk(exchange, data);
    }

    private static void handleDlcRootList(HttpExchange exchange) throws IOException {
        JsonObject data = new JsonObject();
        JsonArray roots = new JsonArray();
        List<Path> dlcRoots = DlcPathConfig.getDlcRoots();
        for (int i = 0; i < dlcRoots.size(); i++) {
            Path root = dlcRoots.get(i);
            JsonObject rootJson = new JsonObject();
            rootJson.addProperty("rootIndex", i);
            rootJson.addProperty("rootPath", root == null ? "" : root.toString());
            JsonArray files = new JsonArray();
            for (Path relative : listEditableDcFiles(root)) {
                String relativePath = normalizeRelativePath(relative);
                JsonObject fileJson = new JsonObject();
                fileJson.addProperty("name", relative.getFileName() == null ? relativePath : relative.getFileName().toString());
                fileJson.addProperty("path", relativePath);
                String type = detectDcType(fileJson.get("name").getAsString(), relativePath, "");
                fileJson.addProperty("type", type);
                fileJson.addProperty("supported", isSupportedType(type));
                files.add(fileJson);
            }
            rootJson.add("files", files);
            roots.add(rootJson);
        }
        data.add("roots", roots);
        data.addProperty("rootCount", dlcRoots.size());
        sendOk(exchange, data);
    }

    private static Map<DlcType, List<DlcItem>> scanDlcItemsForApi() {
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
        List<DlcItem> allItems = new ArrayList<>();
        for (List<DlcItem> group : grouped.values()) {
            if (group == null) {
                continue;
            }
            for (DlcItem item : group) {
                if (item == null) {
                    continue;
                }
                allItems.add(item);
                DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled());
                Boolean override = DlcRuntimeState.getOverride(item.getId());
                if (override != null) {
                    item.setEnabled(override);
                }
            }
        }
        DlcRuntimeState.updateItemIndex(allItems);
        return grouped;
    }

    private static void handleDlcApply(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String modeRaw = request == null ? "" : blank(getString(request, "mode"));
        handleDlcApplyPending(exchange, modeRaw);
    }

    private static void handleDlcSetEnabled(HttpExchange exchange, boolean enabled) throws IOException {
        JsonObject request = readJsonBody(exchange);
        Map<DlcType, List<DlcItem>> grouped = scanDlcItemsForApi();
        Map<String, DlcItem> itemById = new java.util.LinkedHashMap<>();
        Map<String, java.util.List<DlcItem>> itemsByIdentifier = new java.util.LinkedHashMap<>();
        for (List<DlcItem> group : grouped.values()) {
            if (group == null) {
                continue;
            }
            for (DlcItem item : group) {
                if (item != null) {
                    itemById.put(item.getId(), item);
                    String identifier = blank(item.getIdentifier());
                    if (!identifier.isBlank()) {
                        itemsByIdentifier.computeIfAbsent(identifier, key -> new java.util.ArrayList<>()).add(item);
                    }
                }
            }
        }

        JsonArray changed = new JsonArray();
        JsonArray unknownIds = new JsonArray();
        JsonArray unknownIdentifiers = new JsonArray();
        List<String> ids = new ArrayList<>();
        List<String> identifiers = new ArrayList<>();
        String singleId = request == null ? "" : blank(getString(request, "id"));
        if (!singleId.isBlank()) {
            ids.add(singleId);
        }
        String singleIdentifier = request == null ? "" : blank(getString(request, "identifier"));
        if (!singleIdentifier.isBlank()) {
            identifiers.add(singleIdentifier);
        }
        ids.addAll(readStringArray(request, "ids"));
        identifiers.addAll(readStringArray(request, "identifiers"));
        if (ids.isEmpty() && identifiers.isEmpty()) {
            sendError(exchange, 400, "ids_empty", "No DLC id or identifier was provided.");
            return;
        }
        JsonArray ambiguousIdentifiers = new JsonArray();
        for (String identifier : identifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            java.util.List<DlcItem> matches = itemsByIdentifier.get(identifier);
            if (matches == null || matches.size() <= 1) {
                continue;
            }
            JsonObject ambiguous = new JsonObject();
            ambiguous.addProperty("identifier", identifier);
            JsonArray matchedIds = new JsonArray();
            JsonArray matchedNames = new JsonArray();
            for (DlcItem match : matches) {
                if (match == null) {
                    continue;
                }
                matchedIds.add(blank(match.getId()));
                matchedNames.add(blank(match.getName()));
            }
            ambiguous.add("ids", matchedIds);
            ambiguous.add("names", matchedNames);
            ambiguousIdentifiers.add(ambiguous);
        }
        if (ambiguousIdentifiers.size() > 0) {
            JsonObject root = new JsonObject();
            root.addProperty("ok", false);
            JsonObject error = new JsonObject();
            error.addProperty("code", "ambiguous_identifier");
            error.addProperty("message", "One or more identifiers matched multiple DLC entries.");
            error.add("ambiguousIdentifiers", ambiguousIdentifiers);
            root.add("error", error);
            sendJson(exchange, 409, root);
            return;
        }
        java.util.LinkedHashMap<String, DlcItem> requestedItems = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            DlcItem item = itemById.get(id);
            if (item == null) {
                unknownIds.add(id);
                continue;
            }
            requestedItems.putIfAbsent(id, item);
        }
        for (String identifier : identifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            java.util.List<DlcItem> matches = itemsByIdentifier.get(identifier);
            if (matches == null || matches.isEmpty()) {
                unknownIdentifiers.add(identifier);
                continue;
            }
            DlcItem item = matches.get(0);
            requestedItems.putIfAbsent(item.getId(), item);
        }

        JsonArray linkedChanges = new JsonArray();
        if (enabled) {
            DlcRuntimeState.DependencyBatchResult dependencyResult = DlcRuntimeState.collectMissingDependencies(requestedItems.values());
            java.util.LinkedHashMap<String, DlcItem> plannedItems = new java.util.LinkedHashMap<>(requestedItems);
            for (DlcItem item : dependencyResult.getItemsToEnable()) {
                if (item != null) {
                    plannedItems.putIfAbsent(item.getId(), item);
                }
            }
            DlcRuntimeState.ConflictBatchResult conflictResult = DlcRuntimeState.collectConflicts(plannedItems.values());
            if (!conflictResult.getLines().isEmpty()) {
                JsonObject root = new JsonObject();
                root.addProperty("ok", false);
                JsonObject error = new JsonObject();
                error.addProperty("code", "conflicts_detected");
                error.addProperty("message", "One or more DLC conflicts prevent enabling the requested items.");
                JsonArray conflicts = new JsonArray();
                for (DlcRuntimeState.ConflictLine line : conflictResult.getLines()) {
                    JsonObject conflict = new JsonObject();
                    DlcItem left = DlcRuntimeState.getItemByIdentifier(line.getLeftName());
                    DlcItem right = DlcRuntimeState.getItemByIdentifier(line.getRightName());
                    conflict.add("left", toConflictItemJson(left, line.getLeftName()));
                    conflict.add("right", toConflictItemJson(right, line.getRightName()));
                    conflicts.add(conflict);
                }
                error.add("conflicts", conflicts);
                error.add("unknownIds", unknownIds.deepCopy());
                error.add("unknownIdentifiers", unknownIdentifiers.deepCopy());
                root.add("error", error);
                sendJson(exchange, 409, root);
                return;
            }
            for (DlcItem item : requestedItems.values()) {
                applyOverrideState(item, true);
                changed.add(toChangeJson(item, true, false));
            }
            for (DlcItem item : dependencyResult.getItemsToEnable()) {
                if (item == null || requestedItems.containsKey(item.getId())) {
                    continue;
                }
                applyOverrideState(item, true);
                linkedChanges.add(toChangeJson(item, true, true));
            }
        } else {
            java.util.LinkedHashMap<String, DlcItem> linkedToDisable = new java.util.LinkedHashMap<>();
            for (DlcItem item : requestedItems.values()) {
                for (DlcItem dependent : DlcRuntimeState.collectDependentsRecursive(item)) {
                    if (dependent != null) {
                        linkedToDisable.putIfAbsent(dependent.getId(), dependent);
                    }
                }
            }
            java.util.Set<DlcItem> hiddenDependencies = DlcRuntimeState.collectHiddenDependenciesToDisable(linkedToDisable.values());
            for (DlcItem item : hiddenDependencies) {
                if (item != null) {
                    linkedToDisable.putIfAbsent(item.getId(), item);
                }
            }
            for (DlcItem item : requestedItems.values()) {
                applyOverrideState(item, false);
                changed.add(toChangeJson(item, false, false));
            }
            for (DlcItem item : linkedToDisable.values()) {
                if (item == null || requestedItems.containsKey(item.getId())) {
                    continue;
                }
                applyOverrideState(item, false);
                linkedChanges.add(toChangeJson(item, false, true));
            }
        }

        JsonObject data = new JsonObject();
        data.addProperty("enabled", enabled);
        data.add("changes", changed);
        data.add("linkedChanges", linkedChanges);
        data.add("unknownIds", unknownIds);
        data.add("unknownIdentifiers", unknownIdentifiers);
        notifyClientStateChanged();
        sendOk(exchange, data);
    }

    private static void applyOverrideState(DlcItem item, boolean enabled) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            return;
        }
        Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
        if (baseline != null && baseline == enabled) {
            DlcRuntimeState.clearOverride(item.getId());
        } else {
            DlcRuntimeState.setOverride(item.getId(), enabled);
        }
    }

    private static JsonObject toChangeJson(DlcItem item, boolean enabled, boolean linked) {
        JsonObject entry = new JsonObject();
        if (item == null) {
            entry.addProperty("enabled", enabled);
            entry.addProperty("linked", linked);
            return entry;
        }
        entry.addProperty("id", blank(item.getId()));
        entry.addProperty("identifier", blank(item.getIdentifier()));
        entry.addProperty("name", resolveApiDisplayName(item));
        entry.addProperty("enabled", enabled);
        entry.addProperty("pending", DlcRuntimeState.isBaselineDifferent(item.getId(), enabled));
        entry.addProperty("linked", linked);
        return entry;
    }

    private static JsonObject toConflictItemJson(DlcItem item, String fallbackIdentifier) {
        JsonObject json = new JsonObject();
        json.addProperty("id", item == null ? "" : blank(item.getId()));
        json.addProperty("identifier", item == null ? blank(fallbackIdentifier) : blank(item.getIdentifier()));
        json.addProperty("name", item == null ? blank(fallbackIdentifier) : resolveApiDisplayName(item));
        return json;
    }

    private static String resolveApiDisplayName(DlcItem item) {
        if (item == null) {
            return "";
        }
        Map<String, String> displayNames = item.getDisplayNames();
        if (displayNames != null && !displayNames.isEmpty()) {
            String english = blank(displayNames.get("en_us"));
            if (!english.isBlank()) {
                return english;
            }
            for (String value : displayNames.values()) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        String name = blank(item.getName());
        return name.isBlank() ? blank(item.getIdentifier()) : name;
    }

    private static void handleDlcApplyPending(HttpExchange exchange, String modeRaw) throws IOException {
        String normalizedMode = blank(modeRaw).toLowerCase(Locale.ROOT);
        ApplyMode applyMode;
        String responseMode;
        if (normalizedMode.isBlank() || "restart".equals(normalizedMode)) {
            applyMode = ApplyMode.RESTART_ONLY;
            responseMode = "restart";
        } else if ("reload".equals(normalizedMode)) {
            applyMode = ApplyMode.TRY_HOT_RELOAD;
            responseMode = "reload";
        } else {
            sendError(exchange, 400, "invalid_mode", "mode must be either 'restart' or 'reload'.");
            return;
        }

        Map<DlcType, List<DlcItem>> grouped = scanDlcItemsForApi();
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
        JsonArray appliedChanges = new JsonArray();
        int changedCount = 0;

        for (DlcItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                continue;
            }
            changedCount++;
            JsonObject applied = new JsonObject();
            applied.addProperty("id", item.getId());
            applied.addProperty("enabled", item.isEnabled());
            appliedChanges.add(applied);
        }

        if (changedCount == 0) {
            sendError(exchange, 400, "changes_empty", "No pending DLC changes were provided.");
            return;
        }

        DlcApplier.ApplyResult result = DlcApplier.apply(items, applyMode);
        for (DlcItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled());
            DlcRuntimeState.clearOverride(item.getId());
        }
        notifyClientStateChanged();
        JsonObject data = new JsonObject();
        data.addProperty("mode", responseMode);
        data.addProperty("changedCount", changedCount);
        data.addProperty("requiresRestart", result.requiresRestart());
        data.addProperty("hotReloaded", result.hotReloaded());
        data.add("appliedChanges", appliedChanges);
        data.add("messages", toStringArray(result.messages()));
        sendOk(exchange, data);
    }

    private static void handleRemoteList(HttpExchange exchange) throws IOException {
        String lang = blank(exchange.getRequestHeaders().getFirst("X-Dlc-Lang"));
        JsonObject data = new JsonObject();
        data.add("items", GSON.toJsonTree(DlcServerDownloadService.collectMissingRemoteItems(DlcPathConfig.getDlcRoot(), lang)));
        sendOk(exchange, data);
    }

    private static void handleRemoteDownload(HttpExchange exchange) throws IOException {
        DlcServerDownloadService.DownloadResult result = DlcServerDownloadService.downloadMissingRemote(DlcPathConfig.getDlcRoot());
        JsonObject data = new JsonObject();
        data.addProperty("success", result.success);
        data.addProperty("downloaded", result.downloadedCount);
        data.addProperty("error", result.error == null ? "" : result.error);
        sendOk(exchange, data);
    }

    private static void handleRemoteDownloadStart(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        List<String> keys = readStringArray(request, "keys");
        String lang = blank(getString(request, "lang"));
        DlcServerRemoteStatus status = DlcServerDownloadService.startRemoteDownload(DlcPathConfig.getDlcRoot(), keys, lang);
        JsonObject data = new JsonObject();
        data.add("status", GSON.toJsonTree(status));
        sendOk(exchange, data);
    }

    private static void handleRemoteDownloadStatus(HttpExchange exchange) throws IOException {
        String lang = blank(exchange.getRequestHeaders().getFirst("X-Dlc-Lang"));
        DlcServerRemoteStatus status = DlcServerDownloadService.getRemoteDownloadStatus(lang);
        JsonObject data = new JsonObject();
        data.add("status", GSON.toJsonTree(status));
        sendOk(exchange, data);
    }

    private static void handleRequiredList(HttpExchange exchange) throws IOException {
        String lang = blank(exchange.getRequestHeaders().getFirst("X-Dlc-Lang"));
        List<DlcServerRequiredItem> items = DlcServerDownloadService.collectRequiredItems(DlcPathConfig.getDlcRoot(), lang);
        JsonObject data = new JsonObject();
        data.add("items", GSON.toJsonTree(items));
        sendOk(exchange, data);
    }

    private static void handleRequiredDownloadStart(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        List<String> ids = readStringArray(request, "ids");
        String lang = blank(getString(request, "lang"));
        DlcServerRequiredStatus status = DlcServerDownloadService.startRequiredDownload(DlcPathConfig.getDlcRoot(), ids, lang);
        JsonObject data = new JsonObject();
        data.add("status", GSON.toJsonTree(status));
        sendOk(exchange, data);
    }

    private static void handleRequiredDownloadStatus(HttpExchange exchange) throws IOException {
        String lang = blank(exchange.getRequestHeaders().getFirst("X-Dlc-Lang"));
        DlcServerRequiredStatus status = DlcServerDownloadService.getRequiredDownloadStatus(lang);
        JsonObject data = new JsonObject();
        data.add("status", GSON.toJsonTree(status));
        sendOk(exchange, data);
    }

    private static void handleRequiredDownloadCancel(HttpExchange exchange) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("canceled", DlcServerDownloadService.cancelRequiredDownload());
        sendOk(exchange, data);
    }

    private static void handleRequiredUninstall(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        List<String> ids = readStringArray(request, "ids");
        JsonObject data = new JsonObject();
        data.addProperty("removed", DlcServerDownloadService.uninstallRequired(DlcPathConfig.getDlcRoot(), ids));
        sendOk(exchange, data);
    }

    private static void handleRequiredApply(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        List<String> ids = readStringArray(request, "ids");
        JsonObject data = new JsonObject();
        data.addProperty("applied", DlcServerDownloadService.applyRequiredByIds(DlcPathConfig.getDlcRoot(), ids));
        sendOk(exchange, data);
    }

    private static void handleDetectType(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String name = request == null ? "" : blank(getString(request, "name"));
        String path = request == null ? "" : blank(getString(request, "path"));
        String content = request == null ? "" : blank(getString(request, "content"));
        String type = detectDcType(name, path, content);
        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("supported", isSupportedType(type));
        sendOk(exchange, data);
    }

    private static void handleValidate(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String name = request == null ? "" : blank(getString(request, "name"));
        String path = request == null ? "" : blank(getString(request, "path"));
        String content = request == null ? "" : blank(getString(request, "content"));
        String explicitType = request == null ? "" : blank(getString(request, "type"));
        String type = explicitType.isBlank() ? detectDcType(name, path, content) : explicitType.trim().toLowerCase(Locale.ROOT);

        JsonArray issues = new JsonArray();
        if (content.isBlank()) {
            issues.add(issue("error", "schema", "content", "Content is empty."));
        }
        if ("unknown".equals(type)) {
            issues.add(issue("warning", "schema", "type", "Unable to detect config type."));
        }

        JsonObject parsed = null;
        if (!content.isBlank() && isSupportedType(type)) {
            parsed = parseDc(type, name, content);
            if (parsed == null) {
                issues.add(issue("error", "syntax", "content", "Failed to parse DC content."));
            }
        }
        if (parsed != null) {
            validateParsed(type, parsed, issues);
        }

        boolean valid = true;
        for (JsonElement element : issues) {
            if (element != null && element.isJsonObject()) {
                String severity = getString(element.getAsJsonObject(), "severity");
                if ("error".equalsIgnoreCase(severity)) {
                    valid = false;
                    break;
                }
            }
        }
        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("supported", isSupportedType(type));
        data.addProperty("valid", valid);
        data.add("issues", issues);
        sendOk(exchange, data);
    }

    private static void handleReadDc(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        ResolvedDcPath resolved = resolveRequestedDcPath(request, false);
        if (resolved == null) {
            sendError(exchange, 400, "invalid_path", "Requested path is outside the allowed DLC config workspace.");
            return;
        }
        if (!Files.exists(resolved.absolutePath()) || !Files.isRegularFile(resolved.absolutePath())) {
            sendError(exchange, 404, "not_found", "Requested DC file does not exist.");
            return;
        }
        String content = Files.readString(resolved.absolutePath(), StandardCharsets.UTF_8);
        String type = detectDcType(resolved.absolutePath().getFileName() == null ? "" : resolved.absolutePath().getFileName().toString(),
            resolved.relativePath(), content);
        JsonObject data = new JsonObject();
        data.addProperty("name", resolved.absolutePath().getFileName() == null ? resolved.relativePath() : resolved.absolutePath().getFileName().toString());
        data.addProperty("path", resolved.relativePath());
        data.addProperty("rootIndex", resolved.rootIndex());
        data.addProperty("type", type);
        data.addProperty("supported", isSupportedType(type));
        data.addProperty("content", content);
        sendOk(exchange, data);
    }

    private static void handleWriteDc(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String content = request == null ? "" : blank(getString(request, "content"));
        if (content.isBlank()) {
            sendError(exchange, 400, "content_empty", "Content is empty.");
            return;
        }
        ResolvedDcPath resolved = resolveRequestedDcPath(request, true);
        if (resolved == null) {
            sendError(exchange, 400, "invalid_path", "Requested path is outside the allowed DLC config workspace.");
            return;
        }
        String explicitType = request == null ? "" : blank(getString(request, "type"));
        String detectedType = explicitType.isBlank()
            ? detectDcType(resolved.absolutePath().getFileName() == null ? "" : resolved.absolutePath().getFileName().toString(), resolved.relativePath(), content)
            : explicitType.trim().toLowerCase(Locale.ROOT);
        if (isSupportedType(detectedType) && parseDc(detectedType, resolved.absolutePath().getFileName() == null ? "" : resolved.absolutePath().getFileName().toString(), content) == null) {
            sendError(exchange, 400, "parse_failed", "Failed to parse DC content.");
            return;
        }
        Path parent = resolved.absolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(resolved.absolutePath(), content, StandardCharsets.UTF_8);
        JsonObject data = new JsonObject();
        data.addProperty("name", resolved.absolutePath().getFileName() == null ? resolved.relativePath() : resolved.absolutePath().getFileName().toString());
        data.addProperty("path", resolved.relativePath());
        data.addProperty("rootIndex", resolved.rootIndex());
        data.addProperty("type", detectedType);
        data.addProperty("supported", isSupportedType(detectedType));
        data.addProperty("written", true);
        sendOk(exchange, data);
    }

    private static void handleNoticeContent(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String url = blank(getString(request, "url"));
        if (url.isBlank()) {
            sendError(exchange, 400, "url_missing", "URL is required.");
            return;
        }
        String content = fetchNoticeContent(url);
        JsonObject data = new JsonObject();
        data.addProperty("url", url);
        data.addProperty("content", content == null ? "" : content);
        sendOk(exchange, data);
    }

    private static void handleFileAdd(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String pathRaw = blank(getString(request, "path"));
        if (pathRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Target path is required.");
            return;
        }
        Path target = resolveWritableGamePath(pathRaw);
        if (target == null) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.");
            return;
        }
        String content = getString(request, "content");
        String base64 = blank(getString(request, "contentBase64"));
        String sourceRaw = blank(getString(request, "source"));
        boolean directory = getBoolean(request, "directory");
        if (directory) {
            Files.createDirectories(target);
            JsonObject data = new JsonObject();
            data.addProperty("path", toGameRelativePath(target));
            data.addProperty("created", true);
            data.addProperty("directory", true);
            sendOk(exchange, data);
            return;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!sourceRaw.isBlank()) {
            Path source = resolveReadableGamePath(sourceRaw);
            if (source == null || !Files.exists(source)) {
                sendError(exchange, 400, "invalid_source", "Source path is invalid.");
                return;
            }
            Files.copy(source, target);
        } else if (!base64.isBlank()) {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            Files.write(target, bytes);
        } else {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
        JsonObject data = new JsonObject();
        data.addProperty("path", toGameRelativePath(target));
        data.addProperty("created", true);
        sendOk(exchange, data);
    }

    private static void handleFileDelete(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String pathRaw = blank(getString(request, "path"));
        if (pathRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Target path is required.");
            return;
        }
        Path target = resolveReadableGamePath(pathRaw);
        if (target == null) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.");
            return;
        }
        boolean deleted = deleteRecursiveIfExists(target);
        JsonObject data = new JsonObject();
        data.addProperty("path", toGameRelativePath(target));
        data.addProperty("deleted", deleted);
        sendOk(exchange, data);
    }

    private static void handleFileMove(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String fromRaw = blank(getString(request, "from"));
        String toRaw = blank(getString(request, "to"));
        if (fromRaw.isBlank() || toRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Both from and to paths are required.");
            return;
        }
        Path source = resolveReadableGamePath(fromRaw);
        Path target = resolveWritableGamePath(toRaw);
        if (source == null || target == null) {
            sendError(exchange, 400, "invalid_path", "Path is outside the allowed game workspace.");
            return;
        }
        if (!Files.exists(source)) {
            sendError(exchange, 404, "not_found", "Source path does not exist.");
            return;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        JsonObject data = new JsonObject();
        data.addProperty("from", toGameRelativePath(source));
        data.addProperty("to", toGameRelativePath(target));
        data.addProperty("moved", true);
        sendOk(exchange, data);
    }

    private static void handleFileRename(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String pathRaw = blank(getString(request, "path"));
        String nameRaw = blank(getString(request, "name"));
        if (pathRaw.isBlank() || nameRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Path and new name are required.");
            return;
        }
        if (nameRaw.contains("/") || nameRaw.contains("\\") || ".".equals(nameRaw) || "..".equals(nameRaw)) {
            sendError(exchange, 400, "invalid_name", "New name is invalid.");
            return;
        }
        Path source = resolveReadableGamePath(pathRaw);
        if (source == null) {
            sendError(exchange, 400, "invalid_path", "Path is outside the allowed game workspace.");
            return;
        }
        if (!Files.exists(source)) {
            sendError(exchange, 404, "not_found", "Source path does not exist.");
            return;
        }
        Path parent = source.getParent();
        if (parent == null) {
            sendError(exchange, 400, "invalid_path", "Source path cannot be renamed.");
            return;
        }
        Path target = parent.resolve(nameRaw).normalize();
        if (!target.startsWith(DlcPaths.gameDir().normalize())) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.");
            return;
        }
        Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        JsonObject data = new JsonObject();
        data.addProperty("from", toGameRelativePath(source));
        data.addProperty("to", toGameRelativePath(target));
        data.addProperty("renamed", true);
        sendOk(exchange, data);
    }

    private static void handleFileUpload(HttpExchange exchange) throws IOException {
        String pathRaw = blank(getQueryParam(exchange.getRequestURI(), "path"));
        if (pathRaw.isBlank()) {
            pathRaw = blank(exchange.getRequestHeaders().getFirst("X-Dlc-Path"));
        }
        if (pathRaw.isBlank()) {
            sendError(exchange, 400, "path_missing", "Upload target path is required.");
            return;
        }
        Path target = resolveWritableGamePath(pathRaw);
        if (target == null) {
            sendError(exchange, 400, "invalid_path", "Target path is outside the allowed game workspace.");
            return;
        }
        byte[] body = exchange.getRequestBody().readAllBytes();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, body);
        JsonObject data = new JsonObject();
        data.addProperty("path", toGameRelativePath(target));
        data.addProperty("uploaded", true);
        data.addProperty("size", body.length);
        sendOk(exchange, data);
    }

    private static void handleUpdateState(HttpExchange exchange) throws IOException {
        Path dlcRoot = DlcPathConfig.getDlcRoot();
        DlcUpdateVersion version = dlcRoot == null ? null : DlcUpdateVersion.load(dlcRoot);
        String base = version == null ? "" : blank(version.getBase());
        String dlc = version == null ? "" : blank(version.getDlc());
        String rollbackFrom = version == null ? "" : blank(version.getRollbackFrom());
        boolean rolledBack = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, base, dlc);
        List<DlcUpdateMeta> history = rolledBack
            ? DlcUpdateService.listUpdateHistoryFrom(rollbackFrom)
            : DlcUpdateService.listUpdateHistory();
        JsonObject data = new JsonObject();
        data.addProperty("rollbackFrom", rollbackFrom);
        data.addProperty("rolledBack", rolledBack);
        data.add("history", GSON.toJsonTree(history == null ? List.of() : history));
        sendOk(exchange, data);
    }

    private static void handleUpdateList(HttpExchange exchange) throws IOException {
        DlcUpdateService.UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        JsonObject data = new JsonObject();
        JsonArray updates = new JsonArray();
        if (result != null && result.getUpdates() != null) {
            for (DlcUpdatePackage pkg : result.getUpdates()) {
                JsonObject row = toUpdatePackageJson(pkg);
                if (row != null) {
                    updates.add(row);
                }
            }
        }
        data.add("updates", updates);
        data.addProperty("hasUpdates", updates.size() > 0);
        sendOk(exchange, data);
    }

    private static void handleUpdateApply(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String baseOld = blank(getString(request, "baseOld"));
        String dlcOld = blank(getString(request, "dlcOld"));
        String baseNew = blank(getString(request, "baseNew"));
        String dlcNew = blank(getString(request, "dlcNew"));

        List<DlcUpdatePackage> updates;
        if (!baseOld.isBlank() && !dlcOld.isBlank() && !baseNew.isBlank() && !dlcNew.isBlank()) {
            updates = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
        } else {
            DlcUpdateService.UpdateCheckResult result = DlcUpdateService.checkForUpdates();
            updates = result == null ? List.of() : result.getUpdates();
        }
        DlcUpdateService.UpdateApplyResult apply = DlcUpdateService.applyUpdates(updates, false);
        JsonObject data = new JsonObject();
        data.addProperty("success", apply != null && apply.isSuccess());
        data.addProperty("missing", apply != null && apply.isMissing());
        data.addProperty("message", apply == null ? "" : blank(apply.getMessage()));
        data.add("missingPaths", toStringArray(apply == null ? List.of() : apply.getMissingPaths()));
        data.addProperty("addedRequired", apply != null && apply.hasRequiredAdd());
        data.addProperty("addedRemote", apply != null && apply.hasRemoteAdd());
        sendOk(exchange, data);
    }

    private static void handleUpdateRollback(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String target = blank(getString(request, "target"));
        if (target.isBlank()) {
            sendError(exchange, 400, "target_missing", "Rollback target is required.");
            return;
        }
        DlcUpdateService.UpdateRollbackResult result = DlcUpdateService.rollbackTo(target);
        JsonObject data = new JsonObject();
        data.addProperty("success", result != null && result.isSuccess());
        data.addProperty("message", result == null ? "" : blank(result.getMessage()));
        data.addProperty("version", result == null ? "" : blank(result.getVersion()));
        sendOk(exchange, data);
    }

    private static void handleUpdateImport(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String rawPath = blank(getString(request, "path"));
        Path source = resolveGameRelativePath(rawPath);
        if (source == null || !Files.exists(source) || !Files.isRegularFile(source)) {
            sendError(exchange, 400, "invalid_path", "Update package path is invalid.");
            return;
        }
        boolean ok = DlcUpdateService.importUpdatePackage(source);
        JsonObject data = new JsonObject();
        data.addProperty("success", ok);
        data.addProperty("path", source.toString());
        sendOk(exchange, data);
    }

    private static void handleUpdateImportUrl(HttpExchange exchange) throws IOException {
        JsonObject request = readJsonBody(exchange);
        String url = blank(getString(request, "url"));
        String fileName = blank(getString(request, "fileName"));
        if (url.isBlank()) {
            sendError(exchange, 400, "url_missing", "URL is required.");
            return;
        }
        Path updateDir = DlcUpdateService.getUpdateDir();
        if (updateDir == null) {
            sendError(exchange, 400, "missing_dlc_root", "Missing DLC root.");
            return;
        }
        boolean ok = false;
        String message = "";
        Path downloaded = null;
        try {
            downloaded = DlcDownloadService.download(url, updateDir, fileName, null, null);
            ok = DlcUpdateService.importUpdatePackage(downloaded);
            if (!ok) {
                message = "import_failed";
            }
        } catch (DlcDownloadService.DownloadCanceledException ex) {
            message = "canceled";
        } catch (Exception ex) {
            message = ex.getMessage() == null ? "download_failed" : ex.getMessage();
        }
        JsonObject data = new JsonObject();
        data.addProperty("success", ok);
        data.addProperty("message", message);
        data.addProperty("downloadedPath", downloaded == null ? "" : downloaded.toString());
        sendOk(exchange, data);
    }

    private static void validateParsed(String type, JsonObject root, JsonArray issues) {
        switch (type) {
            case "info" -> {
                requireString(issues, getSection(root, "basic"), "identifier", "[basic].identifier is required.");
                requireString(issues, getSection(root, "basic"), "file_name", "[basic].file_name is required.");
                String itemType = getString(getSection(root, "basic"), "type");
                if ("others".equalsIgnoreCase(blank(itemType)) || "config".equalsIgnoreCase(blank(itemType))) {
                    requireString(issues, root, "appliedTarget", "appliedTarget is required for info type others/config.");
                }
            }
            case "remote" -> {
                requireString(issues, getSection(root, "basic"), "identifier", "[basic].identifier is required.");
                requireString(issues, getSection(root, "basic"), "file_name", "[basic].file_name is required.");
                if (!hasAnyDownloadSource(getSection(root, "download"))) {
                    issues.add(issue("error", "schema", "download", "At least one download source is required."));
                }
                String itemType = getString(getSection(root, "basic"), "type");
                if ("others".equalsIgnoreCase(blank(itemType))) {
                    requireString(issues, root, "appliedTarget", "appliedTarget is required for remote type others.");
                }
            }
            case "required" -> {
                requireString(issues, getSection(root, "basic"), "identifier", "[basic].identifier is required.");
                requireString(issues, getSection(root, "basic"), "file_name", "[basic].file_name is required.");
                if (!hasAnyDownloadSource(getSection(root, "download"))) {
                    issues.add(issue("error", "schema", "download", "At least one download source is required."));
                }
            }
            case "redirect" -> requireString(issues, root, "RedirectUrl", "RedirectUrl is required.");
            case "version" -> {
                requireString(issues, root, "base", "base is required.");
                requireString(issues, root, "dlc", "dlc is required.");
            }
            case "update" -> {
                JsonObject version = getSection(root, "version");
                requireString(issues, version, "base", "[version].base is required.");
                requireString(issues, version, "dlc", "[version].dlc is required.");
                String baseNew = blank(getString(version, "base_new"));
                String dlcNew = blank(getString(version, "dlc_new"));
                if (baseNew.isBlank() && dlcNew.isBlank()) {
                    issues.add(issue("error", "schema", "version", "[version].base_new and [version].dlc_new cannot both be empty."));
                }
            }
            default -> {
            }
        }
    }

    private static boolean hasAnyDownloadSource(JsonObject download) {
        if (download == null) {
            return false;
        }
        return hasNonBlankString(download, "modrinth_projectid")
            || hasNonBlankString(download, "curseforge_projectid")
            || hasNonBlankString(download, "directUrl");
    }

    private static JsonObject parseDc(String type, String name, String content) {
        Path temp = null;
        try {
            String fileName = (name == null || name.isBlank()) ? defaultFileName(type) : name;
            temp = Files.createTempFile(stripExtension(fileName), DcFormatUtil.DC_EXTENSION);
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            DcConfigIO.Layout layout = usesStructuredLayout(type) ? DcConfigIO.Layout.DLC_STRUCTURED : DcConfigIO.Layout.GENERIC;
            return DcConfigIO.read(temp, layout);
        } catch (IOException ignored) {
            return null;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean usesStructuredLayout(String type) {
        return "info".equals(type) || "remote".equals(type) || "required".equals(type);
    }

    private static String defaultFileName(String type) {
        return switch (type) {
            case "version" -> "version.dc";
            case "notice" -> "notice.dc";
            case "redirect" -> "redirect.dc";
            case "update" -> "update.dc";
            case "required_notice" -> "notice.dc";
            default -> type + ".dc";
        };
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String detectDcType(String name, String path, String content) {
        String lowerName = blank(name).toLowerCase(Locale.ROOT);
        String lowerPath = blank(path).replace('\\', '/').toLowerCase(Locale.ROOT);
        String text = blank(content);
        if (lowerPath.contains("/required/notice.") || lowerName.equals("notice.dc")) {
            if (containsIgnoreCase(text, "[download_desc]") || containsIgnoreCase(text, "[manage_desc]")) {
                return "required_notice";
            }
        }
        if (lowerPath.contains("/info/version.") || lowerName.equals("version.dc")) {
            return "version";
        }
        if (lowerPath.contains("/remote/redirect/") || lowerName.equals("redirect.dc") || containsIgnoreCase(text, "RedirectUrl")) {
            return "redirect";
        }
        if (containsIgnoreCase(text, "[version]") || containsIgnoreCase(text, "[file]") || containsIgnoreCase(text, "[update]")) {
            return "update";
        }
        if (lowerPath.contains("/preset/")) {
            return "preset";
        }
        if (lowerPath.contains("/remote/")) {
            return "remote";
        }
        if (lowerPath.contains("/required/")) {
            return "required";
        }
        if (lowerPath.contains("/info/")) {
            return "info";
        }
        if (lowerName.equals("notice.dc") || containsIgnoreCase(text, "isNoticeEnabled") || containsIgnoreCase(text, "[Contents]")) {
            return "notice";
        }
        if (containsIgnoreCase(text, "modrinth_projectid") || containsIgnoreCase(text, "curseforge_projectid") || containsIgnoreCase(text, "directUrl")) {
            if (containsIgnoreCase(text, "type =") || containsIgnoreCase(text, "AllowEnableByAll") || containsIgnoreCase(text, "conflicts") || containsIgnoreCase(text, "alarm")) {
                return "remote";
            }
            return "required";
        }
        if (containsIgnoreCase(text, "[DisplayName]") && containsIgnoreCase(text, "enable")) {
            return "preset";
        }
        if (containsIgnoreCase(text, "file_name") || containsIgnoreCase(text, "identifier")) {
            return "info";
        }
        if (containsIgnoreCase(text, "base =") && containsIgnoreCase(text, "dlc =")) {
            return "version";
        }
        return "unknown";
    }

    private static boolean isSupportedType(String type) {
        return switch (type) {
            case "info", "remote", "required", "preset", "notice", "redirect", "update", "version" -> true;
            default -> false;
        };
    }

    private static JsonObject toUpdatePackageJson(DlcUpdatePackage pkg) {
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return null;
        }
        DlcUpdateManifest manifest = pkg.getManifest();
        DlcUpdateManifest.VersionInfo info = manifest.getVersion();
        JsonObject json = new JsonObject();
        json.addProperty("name", blank(pkg.getName()));
        json.addProperty("platform", blank(info.getPlatform()));
        json.addProperty("base", blank(info.getBase()));
        json.addProperty("base_new", blank(info.getBaseNew()));
        json.addProperty("dlc", blank(info.getDlcOld()));
        json.addProperty("dlc_new", blank(info.getDlcNew()));
        json.addProperty("hidden", manifest.isHidden());
        json.add("changelog", GSON.toJsonTree(manifest.getChangelog()));
        return json;
    }

    private static List<Path> listEditableDcFiles(Path root) {
        List<Path> files = new ArrayList<>();
        if (root == null) {
            return files;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path rootNotice = normalizedRoot.resolve("notice.dc").normalize();
        if (Files.isRegularFile(rootNotice)) {
            files.add(normalizedRoot.relativize(rootNotice));
        }
        for (String dirName : List.of("info", "remote", "required", "preset", "update")) {
            Path dir = normalizedRoot.resolve(dirName).normalize();
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> DcConfigIO.isDc(path) || path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dc"))
                    .map(path -> normalizedRoot.relativize(path.normalize()))
                    .sorted(Comparator.comparing(DlcServerApi::normalizeRelativePath))
                    .forEach(files::add);
            } catch (IOException ignored) {
            }
        }
        files.sort(Comparator.comparing(DlcServerApi::normalizeRelativePath));
        return files;
    }

    private static ResolvedDcPath resolveRequestedDcPath(JsonObject request, boolean allowCreate) {
        String rawPath = request == null ? "" : blank(getString(request, "path"));
        if (rawPath.isBlank()) {
            return null;
        }
        List<Path> roots = DlcPathConfig.getDlcRoots();
        if (roots.isEmpty()) {
            return null;
        }
        Integer requestedRootIndex = getOptionalInt(request, "rootIndex");
        Path candidate = DlcPaths.parsePathLenient(rawPath);
        if (candidate == null) {
            return null;
        }

        if (candidate.isAbsolute()) {
            Path absolute = candidate.normalize();
            for (int i = 0; i < roots.size(); i++) {
                if (requestedRootIndex != null && requestedRootIndex != i) {
                    continue;
                }
                Path root = roots.get(i).toAbsolutePath().normalize();
                if (absolute.startsWith(root) && isAllowedDlcConfigPath(root, absolute, allowCreate)) {
                    return new ResolvedDcPath(i, root.relativize(absolute), absolute);
                }
            }
            return null;
        }

        int start = requestedRootIndex == null ? 0 : requestedRootIndex;
        int end = requestedRootIndex == null ? roots.size() : Math.min(roots.size(), requestedRootIndex + 1);
        for (int i = start; i < end; i++) {
            Path root = roots.get(i).toAbsolutePath().normalize();
            Path absolute = root.resolve(candidate).normalize();
            if (isAllowedDlcConfigPath(root, absolute, allowCreate)) {
                return new ResolvedDcPath(i, root.relativize(absolute), absolute);
            }
        }
        return null;
    }

    private static boolean isAllowedDlcConfigPath(Path root, Path target, boolean allowCreate) {
        if (root == null || target == null) {
            return false;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            return false;
        }
        if (normalizedTarget.getFileName() == null) {
            return false;
        }
        String fileName = normalizedTarget.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".dc")) {
            return false;
        }
        Path relative = normalizedRoot.relativize(normalizedTarget);
        if (relative.getNameCount() == 1) {
            return "notice.dc".equalsIgnoreCase(fileName);
        }
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        if (!List.of("info", "remote", "required", "preset", "update").contains(first)) {
            return false;
        }
        if (!allowCreate && !Files.exists(normalizedTarget)) {
            return false;
        }
        return true;
    }

    private static Path resolveGameRelativePath(String rawPath) {
        Path root = DlcPaths.gameDir();
        if (root == null) {
            return null;
        }
        String value = rawPath == null ? "" : rawPath.replace('\\', '/');
        Path resolved = value.isBlank() ? root : root.resolve(value);
        resolved = resolved.normalize();
        if (!resolved.startsWith(root.normalize())) {
            return null;
        }
        return resolved;
    }

    private static Path resolveReadableGamePath(String rawPath) {
        return resolveGameRelativePath(rawPath);
    }

    private static Path resolveWritableGamePath(String rawPath) {
        return resolveGameRelativePath(rawPath);
    }

    private static String toGameRelativePath(Path path) {
        if (path == null) {
            return "";
        }
        Path gameDir = DlcPaths.gameDir();
        if (gameDir == null) {
            return path.toString();
        }
        try {
            return normalizeRelativePath(gameDir.normalize().relativize(path.toAbsolutePath().normalize()));
        } catch (Exception ignored) {
            return path.toString();
        }
    }

    private static boolean deleteRecursiveIfExists(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(target -> {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException error) {
                        throw new RuntimeException(error);
                    }
                });
            } catch (RuntimeException error) {
                if (error.getCause() instanceof IOException io) {
                    throw io;
                }
                throw error;
            }
            return true;
        }
        return Files.deleteIfExists(path);
    }

    private static String fetchNoticeContent(String rawUrl) {
        String url = blank(rawUrl);
        if (url.isBlank()) {
            return null;
        }
        java.net.HttpURLConnection connection = null;
        try {
            java.net.URL target = new java.net.URL(url);
            connection = (java.net.HttpURLConnection) target.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "DLC-Manager-NoticeApi/1.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            try (java.io.InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1);
                }
                return text.isBlank() ? null : text;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean getBoolean(JsonObject obj, String key) {
        if (obj == null) {
            return false;
        }
        JsonElement element = JsonCaseUtil.getIgnoreCase(obj, key);
        if (element == null || element.isJsonNull()) {
            return false;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeRelativePath(Path relative) {
        return relative == null ? "" : relative.toString().replace('\\', '/');
    }

    private static Integer getOptionalInt(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement element = JsonCaseUtil.getIgnoreCase(obj, key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        String value = blank(element.getAsString());
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> readStringArray(JsonObject obj, String key) {
        JsonArray array = obj == null ? null : JsonCaseUtil.getArrayIgnoreCase(obj, key);
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                String value = blank(element.getAsString());
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static JsonObject issue(String severity, String source, String field, String message) {
        JsonObject issue = new JsonObject();
        issue.addProperty("severity", severity);
        issue.addProperty("source", source);
        issue.addProperty("field", field);
        issue.addProperty("message", message);
        return issue;
    }

    private static void requireString(JsonArray issues, JsonObject section, String key, String message) {
        if (!hasNonBlankString(section, key)) {
            issues.add(issue("error", "schema", key, message));
        }
    }

    private static boolean hasNonBlankString(JsonObject obj, String key) {
        return !blank(getString(obj, key)).isBlank();
    }

    private static JsonObject getSection(JsonObject root, String key) {
        return root == null ? null : JsonCaseUtil.getObjectIgnoreCase(root, key);
    }

    private static String getString(JsonObject obj, String key) {
        return obj == null ? "" : blank(JsonCaseUtil.getStringIgnoreCase(obj, key));
    }

    private static JsonObject readJsonBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new JsonObject();
        }
        JsonObject parsed = GSON.fromJson(body, JsonObject.class);
        return parsed == null ? new JsonObject() : parsed;
    }

    private static void sendOk(HttpExchange exchange, JsonObject data) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.add("data", data == null ? new JsonObject() : data);
        sendJson(exchange, 200, root);
    }

    private static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("ok", false);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        root.add("error", error);
        sendJson(exchange, status, root);
    }

    private static void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        if (body != null) {
            headers.set("Content-Type", "application/json; charset=utf-8");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Authorization,Content-Type");
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        String expected = DlcPathConfig.getApiToken();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String provided = authorization.substring(7).trim();
        return Objects.equals(expected, provided);
    }

    private static String normalizePath(URI uri) {
        if (uri == null || uri.getPath() == null || uri.getPath().isBlank()) {
            return "/";
        }
        String path = uri.getPath();
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String getQueryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String[] parts = uri.getQuery().split("&");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            int index = part.indexOf('=');
            String rawKey = index >= 0 ? part.substring(0, index) : part;
            if (!key.equals(rawKey)) {
                continue;
            }
            String rawValue = index >= 0 ? part.substring(index + 1) : "";
            try {
                return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return rawValue;
            }
        }
        return "";
    }

    private static JsonArray toStringArray(List<?> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (Object value : values) {
            array.add(value == null ? "" : value.toString());
        }
        return array;
    }

    private static JsonObject toJsonObject(Map<String, String> values) {
        JsonObject object = new JsonObject();
        if (values == null) {
            return object;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            object.addProperty(entry.getKey(), blank(entry.getValue()));
        }
        return object;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private record ResolvedDcPath(int rootIndex, Path relative, Path absolutePath) {
        private String relativePath() {
            return normalizeRelativePath(relative);
        }
    }
}
