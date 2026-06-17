package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.DlcApplyMode
import com.cozooo.dlc_manager.core.DlcApplier
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcRuntimeState
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import java.util.Locale

object DlcApiService {
    class ApiResult(
        val status: Int,
        val body: JsonObject?,
    ) {
        fun status(): Int = status
        fun body(): JsonObject? = body

        fun ok(): Boolean {
            return status in 200..299 && body != null && body.has("ok") && body.get("ok").asBoolean
        }

        fun data(): JsonObject {
            if (body == null || !body.has("data") || !body.get("data").isJsonObject) {
                return JsonObject()
            }
            return body.getAsJsonObject("data")
        }

        fun error(): JsonObject {
            if (body == null || !body.has("error") || !body.get("error").isJsonObject) {
                return JsonObject()
            }
            return body.getAsJsonObject("error")
        }
    }

    @JvmStatic
    fun setClientStateChangedListener(listener: Runnable?) {
        DlcServerApi.setClientStateChangedListener(listener)
    }

    @JvmStatic
    fun ping(): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val data = JsonObject()
        val server: MinecraftServer? = DlcServerApi.currentServer()
        data.addProperty("service", "dlc-api")
        data.addProperty("running", true)
        data.addProperty("httpRunning", DlcServerApi.isRunning())
        data.addProperty("mode", if (server != null && server.isDedicatedServer) "server" else "client")
        return ok(data)
    }

    @JvmStatic
    fun version(): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val dlcRoot = DlcConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(dlcRoot)
        val data = JsonObject()
        data.addProperty("base", version?.getBase() ?: "")
        data.addProperty("dlc", version?.getDlc() ?: "")
        data.addProperty("rollback_from", blank(version.getRollbackFrom()))
        data.addProperty("changelog_shown", blank(version.getChangelogShown()))
        data.addProperty("label", DlcServerService.getServerVersionLabel())
        return ok(data)
    }

    @JvmStatic
    fun state(): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val server = DlcServerApi.currentServer()
        val grouped = scanDlcItems()
        var total = 0
        var enabled = 0
        val counts = JsonObject()
        for (type in DlcType.values()) {
            val items = grouped[type] ?: emptyList()
            val count = items.size
            counts.addProperty(type.name.lowercase(Locale.ROOT), count)
            total += count
            for (item in items) {
                if (item.isEnabled()) {
                    enabled++
                }
            }
        }
        val data = JsonObject()
        data.addProperty("serverRunning", server != null && server.isRunning)
        data.addProperty("dedicatedServer", server != null && server.isDedicatedServer)
        data.addProperty("totalItems", total)
        data.addProperty("enabledItems", enabled)
        data.add("counts", counts)
        return ok(data)
    }

    @JvmStatic
    fun listDlc(): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val grouped = scanDlcItemsForApi()
        val data = JsonObject()
        val groups = JsonArray()
        for (type in DlcType.values()) {
            val group = JsonObject()
            group.addProperty("type", type.name.lowercase(Locale.ROOT))
            val items = JsonArray()
            for (item in grouped[type] ?: emptyList()) {
                val json = JsonObject()
                json.addProperty("id", blank(item.getId()))
                json.addProperty("identifier", blank(item.getIdentifier()))
                json.addProperty("name", blank(item.getName()))
                json.addProperty("type", item.getType()?.name?.lowercase(Locale.ROOT) ?: "")
                json.addProperty("enabled", item.isEnabled())
                json.addProperty("baselineEnabled", java.lang.Boolean.TRUE == DlcRuntimeState.getBaselineEnabled(item.getId()))
                json.addProperty("pending", DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled()))
                json.addProperty("hidden", item.isHidden())
                json.addProperty("configType", item.isConfigType())
                json.addProperty("alarm", item.isAlarm())
                json.addProperty("allowEnableByAll", item.AllowEnableByAll())
                json.addProperty("appliedTarget", blank(item.getAppliedTargetRaw()))
                json.addProperty("sourcePath", item.getSourcePath()?.toString() ?: "")
                json.add("sourcePaths", toStringArray(item.getSourcePaths()))
                json.add("displayNames", toJsonObject(item.getDisplayNames()))
                json.add("tips", toJsonObject(item.getTooltips()))
                json.add("dependencies", toStringArray(item.getDependencies()))
                json.add("conflicts", toStringArray(item.getConflicts()))
                items.add(json)
            }
            group.add("items", items)
            groups.add(group)
        }
        data.add("groups", groups)
        return ok(data)
    }

    @JvmStatic
    fun listPendingDlc(): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val grouped = scanDlcItemsForApi()
        val items = JsonArray()
        for (type in DlcType.values()) {
            for (item in grouped[type] ?: emptyList()) {
                if (item.getId().isNullOrBlank()) {
                    continue
                }
                if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                    continue
                }
                val json = JsonObject()
                json.addProperty("id", blank(item.getId()))
                json.addProperty("identifier", blank(item.getIdentifier()))
                json.addProperty("name", resolveApiDisplayName(item))
                json.addProperty("type", item.getType()?.name?.lowercase(Locale.ROOT) ?: "")
                json.addProperty("enabled", item.isEnabled())
                json.addProperty("baselineEnabled", java.lang.Boolean.TRUE == DlcRuntimeState.getBaselineEnabled(item.getId()))
                json.addProperty("pending", true)
                items.add(json)
            }
        }
        val data = JsonObject()
        data.add("items", items)
        data.addProperty("count", items.size())
        return ok(data)
    }

    @JvmStatic
    fun setDlcEnabled(request: JsonObject?, enabled: Boolean): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val grouped = scanDlcItemsForApi()
        val itemById = LinkedHashMap<String, DlcItem>()
        val itemsByIdentifier = LinkedHashMap<String, MutableList<DlcItem>>()
        for (group in grouped.values) {
            for (item in group) {
                item.getId()?.let { itemById[it] = item }
                val identifier = blank(item.getIdentifier())
                if (identifier.isNotBlank()) {
                    itemsByIdentifier.computeIfAbsent(identifier) { ArrayList() }.add(item)
                }
            }
        }

        val changed = JsonArray()
        val unknownIds = JsonArray()
        val unknownIdentifiers = JsonArray()
        val ids = ArrayList<String>()
        val identifiers = ArrayList<String>()
        val singleId = if (request == null) "" else blank(getString(request, "id"))
        if (singleId.isNotBlank()) {
            ids.add(singleId)
        }
        val singleIdentifier = if (request == null) "" else blank(getString(request, "identifier"))
        if (singleIdentifier.isNotBlank()) {
            identifiers.add(singleIdentifier)
        }
        ids.addAll(readStringArray(request, "ids"))
        identifiers.addAll(readStringArray(request, "identifiers"))
        if (ids.isEmpty() && identifiers.isEmpty()) {
            return error(400, "ids_empty", "No DLC id or identifier was provided.")
        }

        val ambiguousIdentifiers = JsonArray()
        for (identifier in identifiers) {
            if (identifier.isBlank()) {
                continue
            }
            val matches = itemsByIdentifier[identifier]
            if (matches == null || matches.size <= 1) {
                continue
            }
            val ambiguous = JsonObject()
            ambiguous.addProperty("identifier", identifier)
            val matchedIds = JsonArray()
            val matchedNames = JsonArray()
            for (match in matches) {
                matchedIds.add(blank(match.getId()))
                matchedNames.add(blank(match.getName()))
            }
            ambiguous.add("ids", matchedIds)
            ambiguous.add("names", matchedNames)
            ambiguousIdentifiers.add(ambiguous)
        }
        if (ambiguousIdentifiers.size() > 0) {
            val root = JsonObject()
            root.addProperty("ok", false)
            val error = JsonObject()
            error.addProperty("code", "ambiguous_identifier")
            error.addProperty("message", "One or more identifiers matched multiple DLC entries.")
            error.add("ambiguousIdentifiers", ambiguousIdentifiers)
            root.add("error", error)
            return ApiResult(409, root)
        }

        val requestedItems = LinkedHashMap<String, DlcItem>()
        for (id in ids) {
            if (id.isBlank()) {
                continue
            }
            val item = itemById[id]
            if (item == null) {
                unknownIds.add(id)
                continue
            }
            requestedItems.putIfAbsent(id, item)
        }
        for (identifier in identifiers) {
            if (identifier.isBlank()) {
                continue
            }
            val matches = itemsByIdentifier[identifier]
            if (matches.isNullOrEmpty()) {
                unknownIdentifiers.add(identifier)
                continue
            }
            val item = matches[0]
            item.getId()?.let { requestedItems.putIfAbsent(it, item) }
        }

        val linkedChanges = JsonArray()
        if (enabled) {
            val dependencyResult = DlcRuntimeState.collectMissingDependencies(requestedItems.values)
            val plannedItems = LinkedHashMap(requestedItems)
            for (item in dependencyResult.getItemsToEnable()) {
                plannedItems.putIfAbsent(item.getId(), item)
            }
            val conflictResult = DlcRuntimeState.collectConflicts(plannedItems.values)
            if (conflictResult.getLines().isNotEmpty()) {
                val root = JsonObject()
                root.addProperty("ok", false)
                val error = JsonObject()
                error.addProperty("code", "conflicts_detected")
                error.addProperty("message", "One or more DLC conflicts prevent enabling the requested items.")
                val conflicts = JsonArray()
                for (line in conflictResult.getLines()) {
                    val conflict = JsonObject()
                    val left = DlcRuntimeState.getItemByIdentifier(line.getLeftName())
                    val right = DlcRuntimeState.getItemByIdentifier(line.getRightName())
                    conflict.add("left", toConflictItemJson(left, line.getLeftName()))
                    conflict.add("right", toConflictItemJson(right, line.getRightName()))
                    conflicts.add(conflict)
                }
                error.add("conflicts", conflicts)
                error.add("unknownIds", unknownIds.deepCopy())
                error.add("unknownIdentifiers", unknownIdentifiers.deepCopy())
                root.add("error", error)
                return ApiResult(409, root)
            }
            for (item in requestedItems.values) {
                applyOverrideState(item, true)
                changed.add(toChangeJson(item, true, false))
            }
            for (item in dependencyResult.getItemsToEnable()) {
                if (requestedItems.containsKey(item.getId())) {
                    continue
                }
                applyOverrideState(item, true)
                linkedChanges.add(toChangeJson(item, true, true))
            }
        } else {
            val linkedToDisable = LinkedHashMap<String, DlcItem>()
            for (item in requestedItems.values) {
                for (dependent in DlcRuntimeState.collectDependentsRecursive(item)) {
                    val id = dependent.getId()
                    if (id != null) {
                        linkedToDisable.putIfAbsent(id, dependent)
                    }
                }
            }
            val hiddenDependencies = DlcRuntimeState.collectHiddenDependenciesToDisable(linkedToDisable.values)
            for (item in hiddenDependencies) {
                val id = item.getId()
                if (id != null) {
                    linkedToDisable.putIfAbsent(id, item)
                }
            }
            for (item in requestedItems.values) {
                applyOverrideState(item, false)
                changed.add(toChangeJson(item, false, false))
            }
            for (item in linkedToDisable.values) {
                if (requestedItems.containsKey(item.getId())) {
                    continue
                }
                applyOverrideState(item, false)
                linkedChanges.add(toChangeJson(item, false, true))
            }
        }

        val data = JsonObject()
        data.addProperty("enabled", enabled)
        data.add("changes", changed)
        data.add("linkedChanges", linkedChanges)
        data.add("unknownIds", unknownIds)
        data.add("unknownIdentifiers", unknownIdentifiers)
        DlcServerApi.notifyClientStateChanged()
        return ok(data)
    }

    @JvmStatic
    fun applyDlc(request: JsonObject?): ApiResult {
        val disabled = ensureEnabled()
        if (disabled != null) {
            return disabled
        }
        val normalizedMode = blank(getString(request, "mode")).lowercase(Locale.ROOT)
        val applyMode: DlcApplyMode
        val responseMode: String
        if (normalizedMode.isBlank() || normalizedMode == "restart") {
            applyMode = DlcApplyMode.RESTART_ONLY
            responseMode = "restart"
        } else if (normalizedMode == "reload") {
            applyMode = DlcApplyMode.TRY_HOT_RELOAD
            responseMode = "reload"
        } else {
            return error(400, "invalid_mode", "mode must be either 'restart' or 'reload'.")
        }

        val grouped = scanDlcItemsForApi()
        val items = ArrayList<DlcItem>()
        for (group in grouped.values) {
            items.addAll(group)
        }

        val appliedChanges = JsonArray()
        var changedCount = 0
        for (item in items) {
            if (item.getId() == null) {
                continue
            }
            if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                continue
            }
            changedCount++
            val applied = JsonObject()
            applied.addProperty("id", item.getId())
            applied.addProperty("enabled", item.isEnabled())
            appliedChanges.add(applied)
        }
        if (changedCount == 0) {
            return error(400, "changes_empty", "No pending DLC changes were provided.")
        }

        val result = DlcApplier.apply(items, applyMode)
        for (item in items) {
            if (item.getId() == null) {
                continue
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled())
            DlcRuntimeState.clearOverride(item.getId())
        }
        DlcServerApi.notifyClientStateChanged()
        val data = JsonObject()
        data.addProperty("mode", responseMode)
        data.addProperty("changedCount", changedCount)
        data.addProperty("requiresRestart", result.requiresRestart())
        data.addProperty("hotReloaded", result.hotReloaded())
        data.add("appliedChanges", appliedChanges)
        data.add("messages", toStringArray(result.messages()))
        return ok(data)
    }

    private fun scanDlcItems(): Map<DlcType, List<DlcItem>> {
        val raw = DlcScanner().scan()
        val grouped = LinkedHashMap<DlcType, List<DlcItem>>()
        for (type in DlcType.values()) {
            val items = raw[type]?.filterNotNull() ?: emptyList()
            grouped[type] = items
        }
        return grouped
    }

    private fun scanDlcItemsForApi(): Map<DlcType, List<DlcItem>> {
        val grouped = LinkedHashMap(scanDlcItems())
        val allItems = ArrayList<DlcItem>()
        for (group in grouped.values) {
            for (item in group) {
                allItems.add(item)
                DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled())
                val override = DlcRuntimeState.getOverride(item.getId())
                if (override != null) {
                    item.setEnabled(override)
                }
            }
        }
        DlcRuntimeState.updateItemIndex(allItems)
        return grouped
    }

    private fun applyOverrideState(item: DlcItem?, enabled: Boolean) {
        if (item == null || item.getId().isNullOrBlank()) {
            return
        }
        val baseline = DlcRuntimeState.getBaselineEnabled(item.getId())
        if (baseline != null && baseline == enabled) {
            DlcRuntimeState.clearOverride(item.getId())
        } else {
            DlcRuntimeState.setOverride(item.getId(), enabled)
        }
    }

    private fun toChangeJson(item: DlcItem?, enabled: Boolean, linked: Boolean): JsonObject {
        val entry = JsonObject()
        if (item == null) {
            entry.addProperty("enabled", enabled)
            entry.addProperty("linked", linked)
            return entry
        }
        entry.addProperty("id", blank(item.getId()))
        entry.addProperty("identifier", blank(item.getIdentifier()))
        entry.addProperty("name", resolveApiDisplayName(item))
        entry.addProperty("enabled", enabled)
        entry.addProperty("pending", DlcRuntimeState.isBaselineDifferent(item.getId(), enabled))
        entry.addProperty("linked", linked)
        return entry
    }

    private fun toConflictItemJson(item: DlcItem?, fallbackIdentifier: String?): JsonObject {
        val json = JsonObject()
        json.addProperty("id", if (item == null) "" else blank(item.getId()))
        json.addProperty("identifier", if (item == null) blank(fallbackIdentifier) else blank(item.getIdentifier()))
        json.addProperty("name", if (item == null) blank(fallbackIdentifier) else resolveApiDisplayName(item))
        return json
    }

    private fun resolveApiDisplayName(item: DlcItem?): String {
        if (item == null) {
            return ""
        }
        val displayNames = item.getDisplayNames()
        if (displayNames.isNotEmpty()) {
            val english = blank(displayNames["en_us"])
            if (english.isNotBlank()) {
                return english
            }
            for (value in displayNames.values) {
                if (!value.isNullOrBlank()) {
                    return value
                }
            }
        }
        val name = blank(item.getName())
        return if (name.isBlank()) blank(item.getIdentifier()) else name
    }

    private fun readStringArray(obj: JsonObject?, key: String): List<String> {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray) {
            return emptyList()
        }
        val values = ArrayList<String>()
        obj.getAsJsonArray(key).forEach { element ->
            if (element != null && element.isJsonPrimitive) {
                val value = blank(element.asString)
                if (value.isNotBlank()) {
                    values.add(value)
                }
            }
        }
        return values
    }

    private fun getString(obj: JsonObject?, key: String): String {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull) {
            return ""
        }
        return try {
            blank(obj.get(key).asString)
        } catch (_: Exception) {
            ""
        }
    }

    private fun toStringArray(values: List<*>?): JsonArray {
        val array = JsonArray()
        if (values == null) {
            return array
        }
        for (value in values) {
            array.add(value?.toString() ?: "")
        }
        return array
    }

    private fun toJsonObject(values: Map<String, String>?): JsonObject {
        val obj = JsonObject()
        if (values == null) {
            return obj
        }
        for ((key, value) in values) {
            obj.addProperty(key, blank(value))
        }
        return obj
    }

    private fun ok(data: JsonObject?): ApiResult {
        val root = JsonObject()
        root.addProperty("ok", true)
        root.add("data", data ?: JsonObject())
        return ApiResult(200, root)
    }

    private fun ensureEnabled(): ApiResult? {
        if (DlcConfig.isApiEnabled()) {
            return null
        }
        return error(503, "api_disabled", "API is disabled.")
    }

    private fun error(status: Int, code: String, message: String): ApiResult {
        val root = JsonObject()
        root.addProperty("ok", false)
        val error = JsonObject()
        error.addProperty("code", code)
        error.addProperty("message", message)
        root.add("error", error)
        return ApiResult(status, root)
    }

    private fun blank(value: String?): String {
        return value ?: ""
    }
}
