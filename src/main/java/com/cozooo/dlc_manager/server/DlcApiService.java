package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class DlcApiService {
    private DlcApiService() {
    }

    public record ApiResult(int status, JsonObject body) {
        public boolean ok() {
            return status >= 200 && status < 300 && body != null && body.has("ok") && body.get("ok").getAsBoolean();
        }

        public JsonObject data() {
            if (body == null || !body.has("data") || !body.get("data").isJsonObject()) {
                return new JsonObject();
            }
            return body.getAsJsonObject("data");
        }

        public JsonObject error() {
            if (body == null || !body.has("error") || !body.get("error").isJsonObject()) {
                return new JsonObject();
            }
            return body.getAsJsonObject("error");
        }
    }

    public static void setClientStateChangedListener(Runnable listener) {
        DlcServerApi.setClientStateChangedListener(listener);
    }

    public static ApiResult ping() {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
        JsonObject data = new JsonObject();
        MinecraftServer server = DlcServerApi.currentServer();
        data.addProperty("service", "dlc-api");
        data.addProperty("running", true);
        data.addProperty("httpRunning", DlcServerApi.isRunning());
        data.addProperty("mode", server != null && server.isDedicatedServer() ? "server" : "client");
        return ok(data);
    }

    public static ApiResult version() {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
        var dlcRoot = DlcConfig.getDlcRoot();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        JsonObject data = new JsonObject();
        data.addProperty("base", version == null ? "" : version.getBase());
        data.addProperty("version", version == null ? "" : version.getVersion());
        data.addProperty("dlc", version == null ? "" : version.getDlc());
        data.addProperty("rollback_from", version == null ? "" : blank(version.getRollbackFrom()));
        data.addProperty("changelog_shown", version == null ? "" : blank(version.getChangelogShown()));
        data.addProperty("label", DlcServerService.getServerVersionLabel());
        return ok(data);
    }

    public static ApiResult state() {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
        MinecraftServer server = DlcServerApi.currentServer();
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
        data.addProperty("serverRunning", server != null && server.isRunning());
        data.addProperty("dedicatedServer", server != null && server.isDedicatedServer());
        data.addProperty("totalItems", total);
        data.addProperty("enabledItems", enabled);
        data.add("counts", counts);
        return ok(data);
    }

    public static ApiResult listDlc() {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
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
                json.add("alarmTips", toJsonObject(item.getAlarmTips()));
                json.add("dependencies", toStringArray(item.getDependencies()));
                json.add("conflicts", toStringArray(item.getConflicts()));
                items.add(json);
            }
            group.add("items", items);
            groups.add(group);
        }
        data.add("groups", groups);
        return ok(data);
    }

    public static ApiResult listPendingDlc() {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
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
        return ok(data);
    }

    public static ApiResult setDlcEnabled(JsonObject request, boolean enabled) {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
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
            return error(400, "ids_empty", "No DLC id or identifier was provided.");
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
            return new ApiResult(409, root);
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
                return new ApiResult(409, root);
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
        DlcServerApi.notifyClientStateChanged();
        return ok(data);
    }

    public static ApiResult applyDlc(JsonObject request) {
        ApiResult disabled = ensureEnabled();
        if (disabled != null) {
            return disabled;
        }
        String normalizedMode = blank(getString(request, "mode")).toLowerCase(Locale.ROOT);
        DlcApplyMode applyMode;
        String responseMode;
        if (normalizedMode.isBlank() || "restart".equals(normalizedMode)) {
            applyMode = DlcApplyMode.RESTART_ONLY;
            responseMode = "restart";
        } else if ("reload".equals(normalizedMode)) {
            applyMode = DlcApplyMode.TRY_HOT_RELOAD;
            responseMode = "reload";
        } else {
            return error(400, "invalid_mode", "mode must be either 'restart' or 'reload'.");
        }

        Map<DlcType, List<DlcItem>> grouped = scanDlcItemsForApi();
        List<DlcItem> items = new ArrayList<>();
        for (List<DlcItem> group : grouped.values()) {
            if (group == null) {
                continue;
            }
            items.addAll(group);
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
            return error(400, "changes_empty", "No pending DLC changes were provided.");
        }

        DlcApplier.ApplyResult result = DlcApplier.apply(items, applyMode);
        for (DlcItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled());
            DlcRuntimeState.clearOverride(item.getId());
        }
        DlcServerApi.notifyClientStateChanged();
        JsonObject data = new JsonObject();
        data.addProperty("mode", responseMode);
        data.addProperty("changedCount", changedCount);
        data.addProperty("requiresRestart", result.requiresRestart());
        data.addProperty("hotReloaded", result.hotReloaded());
        data.add("appliedChanges", appliedChanges);
        data.add("messages", toStringArray(result.messages()));
        return ok(data);
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

    private static List<String> readStringArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        obj.getAsJsonArray(key).forEach(element -> {
            if (element != null && element.isJsonPrimitive()) {
                String value = blank(element.getAsString());
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        });
        return values;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return blank(obj.get(key).getAsString());
        } catch (Exception ignored) {
            return "";
        }
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

    private static ApiResult ok(JsonObject data) {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.add("data", data == null ? new JsonObject() : data);
        return new ApiResult(200, root);
    }

    private static ApiResult ensureEnabled() {
        if (DlcConfig.isApiEnabled()) {
            return null;
        }
        return error(503, "api_disabled", "API is disabled.");
    }

    private static ApiResult error(int status, String code, String message) {
        JsonObject root = new JsonObject();
        root.addProperty("ok", false);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        root.add("error", error);
        return new ApiResult(status, root);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
