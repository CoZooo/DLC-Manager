package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.core.Json5Util;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

public final class DlcApiService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static HttpServer httpServer;
    private static ExecutorService clientPool;
    private static volatile MinecraftServer currentServer;

    private DlcApiService() {
    }

    public static synchronized void start(MinecraftServer server) {
        currentServer = server;
        if (!DlcPathConfig.isApiEnabled()) {
            LOGGER.info("[dlc-manager] API disabled; skip DlcApi start");
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
                LOGGER.warn("[dlc-manager] DlcApi started on port {} with empty token; all requests will be rejected until apiToken is configured", DlcPathConfig.getApiPort());
            } else {
                LOGGER.info("[dlc-manager] DlcApi started on port {}", DlcPathConfig.getApiPort());
            }
        } catch (IOException error) {
            RUNNING.set(false);
            httpServer = null;
            if (clientPool != null) {
                clientPool.shutdownNow();
                clientPool = null;
            }
            LOGGER.warn("[dlc-manager] failed to start DlcApi", error);
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
                if ("POST".equals(method) && "/api/dc/detect-type".equals(path)) {
                    handleDetectType(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/dc/validate".equals(path)) {
                    handleValidate(exchange);
                    return;
                }
                sendError(exchange, 404, "not_found", "Unknown API route.");
            } catch (Exception error) {
                LOGGER.warn("[dlc-manager] DlcApi request failed", error);
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
        data.addProperty("port", DlcPathConfig.getApiPort());
        data.addProperty("serverVersionLabel", DlcServerService.getServerVersionLabel());
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
        data.addProperty("apiEnabled", DlcPathConfig.isApiEnabled());
        data.addProperty("apiPort", DlcPathConfig.getApiPort());
        data.addProperty("serverRunning", currentServer != null && currentServer.isRunning());
        data.addProperty("dedicatedServer", currentServer != null && currentServer.isDedicatedServer());
        data.addProperty("base", version == null ? "" : version.getBase());
        data.addProperty("dlc", version == null ? "" : version.getDlc());
        data.addProperty("totalItems", total);
        data.addProperty("enabledItems", enabled);
        data.add("counts", counts);
        sendOk(exchange, data);
    }

    private static void handleDlcList(HttpExchange exchange) throws IOException {
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
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
            temp = Files.createTempFile(stripExtension(fileName), Json5Util.DC_EXTENSION);
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
}
