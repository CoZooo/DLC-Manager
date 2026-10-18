package com.cozooo.dlc_manager.sync

import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Path
import java.util.function.BooleanSupplier

class SyncModel private constructor() {
    enum class Mode { CONFIG, FILES }
    enum class Direction { PUSH, PULL }
    enum class Category { ALL, DLC, REQUIRED, UPDATE, MANAGER_CONFIG, PRESET }
    data class Update(val baseOld: String, val dlcOld: String, val baseNew: String, val dlcNew: String, val platform: String) {
        fun baseOld() = baseOld
        fun dlcOld() = dlcOld
        fun baseNew() = baseNew
        fun dlcNew() = dlcNew
        fun platform() = platform
    }
    data class File(val id: String, val size: Long, val hash: String?) {
        fun id() = id
        fun size() = size
        fun hash() = hash
    }
    data class Item @JvmOverloads constructor(val key: String, val identifier: String?, val config: File, val resources: List<File>, val enabled: Boolean?, val displayNames: Map<String, String> = emptyMap(), val tooltips: Map<String, String> = emptyMap(), val update: Update? = null, val settings: JsonObject? = null) {
        fun key() = key
        fun identifier() = identifier
        fun config() = config
        fun resources() = resources
        fun enabled() = enabled
        fun displayNames() = displayNames
        fun tooltips() = tooltips
        fun settings() = settings
        fun update() = update
    }
    data class Row(val key: String, val source: Item, val target: Item?, val direction: Direction, val replacement: Boolean, val stateChanged: Boolean) {
        fun key() = key
        fun source() = source
        fun target() = target
        fun direction() = direction
        fun replacement() = replacement
        fun stateChanged() = stateChanged
    }
    data class Comparison(val rows: List<Row>, val statuses: Map<String, String>) {
        fun rows() = rows
        fun statuses() = statuses
        fun status(row: Row) = statuses[row.key]
    }
    data class Result(val completed: Int, val canceled: Boolean, val requiresRestart: Boolean, val errors: List<String>, val succeeded: List<String>) {
        fun completed() = completed
        fun canceled() = canceled
        fun requiresRestart() = requiresRestart
        fun errors() = errors
        fun succeeded() = succeeded
    }
    fun interface Peer : AutoCloseable { @Throws(IOException::class) fun call(request: JsonObject): JsonObject; @Throws(IOException::class) override fun close() {} }
    fun interface Progress { fun update(phase: String, resource: String, done: Long, total: Long) }
    interface Journal : AutoCloseable {
        @Throws(IOException::class) fun capture(path: Path)
        @Throws(IOException::class) fun rollback()
        @Throws(IOException::class) fun commit()
        override fun close()
    }
    fun interface Quiesce { @Throws(Exception::class) fun await(canceled: BooleanSupplier) }
    interface Adapter {
        fun workDirectory(): Path
        @Throws(IOException::class) fun manifest(): List<Item>
        @Throws(IOException::class) fun item(key: String): Item = manifest().firstOrNull { it.key == key } ?: throw SyncFailure("config_not_found", "$key")
        @Throws(IOException::class) fun resolve(id: String): Path
        @Throws(IOException::class) fun validateKey(key: String)
        @Throws(IOException::class) fun validateIncoming(item: Item) {}
        @Throws(IOException::class) fun writeSettings(key: String, settings: JsonObject, journal: Journal) { throw SyncFailure("settings_unavailable") }
        @Throws(IOException::class) fun journal(directory: Path): Journal
        @Throws(Exception::class) fun prepare(key: String, force: Boolean, canceled: BooleanSupplier, progress: Progress)
        @Throws(Exception::class) fun apply(key: String, enabled: Boolean?, journal: Journal, canceled: BooleanSupplier): Boolean
        @Throws(IOException::class) fun reload()
        @Throws(IOException::class) fun recover()
        @Throws(IOException::class) fun recoverOperation(operation: String)
        @Throws(Exception::class) fun quiesce(canceled: BooleanSupplier) {}
    }
    companion object {
        @JvmStatic fun sharedState(source: Item?, target: Item?): Boolean = source != null && target != null && source.enabled != null && target.enabled != null && !source.identifier.isNullOrBlank() && source.identifier == target.identifier && category(source) == category(target)
        @JvmStatic fun hasStateDifferences(local: List<Item>, remote: List<Item>): Boolean {
            val target = index(remote)
            return local.any { sharedState(it, target[it.key]) && it.enabled != target[it.key]?.enabled }
        }
        @JvmStatic fun plan(local: List<Item>, server: List<Item>, direction: Direction, states: Boolean): List<Row> = plan(local, server, direction, states, Mode.FILES)
        @JvmStatic fun plan(local: List<Item>, server: List<Item>, direction: Direction, states: Boolean, mode: Mode): List<Row> {
            val sourceItems = if (direction == Direction.PUSH) local else server
            val targetItems = if (direction == Direction.PUSH) server else local
            val left = index(sourceItems)
            val right = index(targetItems)
            val eligible = eligibleUpdates(sourceItems, targetItems)
            val reversedEligible = eligibleUpdates(targetItems, sourceItems)
            val rows = left.values.mapNotNull { source ->
                if (versionState(source) || category(source) == Category.UPDATE && (mode != Mode.FILES || source.key !in eligible)) return@mapNotNull null
                val target = right[source.key]
                val state = states && sharedState(source, target) && source.enabled != target?.enabled
                if (target != null && same(source.config, target.config) && (!usesResources(source) || resourcesMatch(source.resources, target.resources, mode)) && !state) null
                else {
                    val replacement = target != null && (!same(source.config, target.config) || state || usesResources(source) && target.resources.any { it.size >= 0 } && !resourcesMatch(source.resources, target.resources, mode))
                    Row(source.key, source, target, direction, replacement, state)
                }
            }.toMutableList()
            for (target in right.values) if (!left.containsKey(target.key) && !versionState(target) && (category(target) != Category.UPDATE || mode == Mode.FILES && target.key in reversedEligible)) rows.add(Row(target.key, target, null,
                if (direction == Direction.PUSH) Direction.PULL else Direction.PUSH, false, false))
            return rows.sortedBy { it.key }
        }
        @JvmStatic fun compare(local: List<Item>, server: List<Item>, direction: Direction, states: Boolean, mode: Mode): Comparison {
            val rows = plan(local, server, direction, states, mode)
            return Comparison(java.util.List.copyOf(rows), java.util.Map.copyOf(rows.associate { it.key to status(it, mode) }))
        }
        @JvmStatic fun same(a: File?, b: File?): Boolean = a != null && b != null && a.size >= 0 && a.size == b.size && a.id == b.id && a.hash != null && a.hash == b.hash
        @JvmStatic fun sameResources(a: List<File>, b: List<File>): Boolean {
            if (a.isEmpty() || b.isEmpty() || a.size != b.size) return false
            val indexed = b.associateBy { it.id }
            return a.all { same(it, indexed[it.id]) }
        }
        @JvmStatic fun resourcesMatch(source: List<File>, target: List<File>, mode: Mode): Boolean {
            if (mode == Mode.FILES) return sameResources(source, target)
            return true
        }
        @JvmStatic fun displayName(item: Item, language: String?): String {
            val name = localized(item.displayNames, language)
            if (name.isNotBlank()) return name
            if (!item.identifier.isNullOrBlank()) return item.identifier
            val fallback = item.key.substringAfterLast('/')
            val extension = fallback.lastIndexOf('.')
            return if (extension > 0) fallback.substring(0, extension) else fallback
        }
        @JvmStatic fun tooltip(item: Item, language: String?) = localized(item.tooltips, language)
        private fun localized(values: Map<String, String>?, language: String?): String {
            val names = values.orEmpty()
            val localized = names[language.orEmpty().lowercase(java.util.Locale.ROOT).replace('-', '_')]
            if (!localized.isNullOrBlank()) return localized
            val english = names["en_us"]
            if (!english.isNullOrBlank()) return english
            names.values.firstOrNull { !it.isNullOrBlank() }?.let { return it }
            return ""
        }
        @JvmStatic fun status(row: Row, mode: Mode): String {
            val target = row.target ?: return if (row.direction == Direction.PUSH) "server_missing" else "client_missing"
            if (mode == Mode.FILES && usesResources(row.source) && (row.source.resources.isEmpty() || target.resources.isEmpty() || row.source.resources.any { it.size < 0 } || target.resources.any { it.size < 0 } || row.source.resources.any { file -> target.resources.none { it.id == file.id } })) return "resources_missing"
            if (same(row.source.config, target.config) && (!usesResources(row.source) || resourcesMatch(row.source.resources, target.resources, mode))) return "state_changed"
            return "content_changed"
        }
        @JvmStatic fun differences(row: Row, mode: Mode): List<String> {
            val result = mutableListOf<String>()
            if (row.target == null) result.add("configuration_missing")
            else if (!same(row.source.config, row.target.config)) result.add("configuration_changed")
            if (mode == Mode.FILES && usesResources(row.source) && (row.target == null || !resourcesMatch(row.source.resources, row.target.resources, mode))) {
                val missing = row.target == null || row.target.resources.isEmpty() || row.target.resources.any { it.size < 0 } || row.source.resources.isEmpty() || row.source.resources.any { it.size < 0 }
                result.add(if (missing) "resources_missing" else "resources_changed")
            }
            if (row.stateChanged) result.add(if (row.source.enabled == true) "enable" else "disable")
            return result
        }
        private fun relative(key: String) = key.substringAfter(':')
        @JvmStatic fun category(item: Item): Category = when {
            item.key.startsWith("manager:") -> Category.MANAGER_CONFIG
            relative(item.key).startsWith("required/") -> Category.REQUIRED
            relative(item.key).startsWith("preset/") -> Category.PRESET
            relative(item.key).startsWith("update/") || relative(item.key) == "info/version.dc" -> Category.UPDATE
            else -> Category.DLC
        }
        @JvmStatic fun usesResources(item: Item) = category(item) == Category.DLC || category(item) == Category.REQUIRED
        @JvmStatic fun versionState(item: Item) = relative(item.key) == "info/version.dc"
        private fun root(item: Item) = item.key.substringBefore(':', "")
        private fun pair(base: String, dlc: String) = "$base\n$dlc"
        private fun advances(update: Update?): Boolean {
            if (update == null) return false
            val base = com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(update.baseNew, update.baseOld)
            return base > 0 || base == 0 && com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(update.dlcNew, update.dlcOld) > 0
        }
        @JvmStatic fun eligibleUpdates(source: List<Item>, target: List<Item>): Set<String> {
            val versions = target.filter { versionState(it) && it.update != null }.associate { root(it) to it.update!! }
            val reachable = versions.mapValues { (_, v) -> mutableSetOf(pair(v.baseOld, v.dlcOld)) }
            val candidates = source.filter { category(it) == Category.UPDATE && !versionState(it) }
            val sourceKeys = candidates.map { it.key }.toSet()
            val available = candidates + target.filter { category(it) == Category.UPDATE && !versionState(it) && it.key !in sourceKeys }
            val eligible = mutableSetOf<String>()
            do {
                var changed = false
                for (item in available) {
                    val current = versions[root(item)] ?: continue
                    val update = item.update ?: continue
                    if (!advances(update) || update.platform != "universal" && update.platform != current.platform) continue
                    val reached = reachable.getValue(root(item))
                    if (pair(update.baseOld, update.dlcOld) !in reached) continue
                    if (item.key in sourceKeys) eligible.add(item.key)
                    if (reached.add(pair(update.baseNew, update.dlcNew))) changed = true
                }
            } while (changed)
            return eligible
        }
        private fun applicationOrder(item: Item) = when (category(item)) { Category.UPDATE -> 2; Category.PRESET -> 1; else -> 0 }
        @JvmStatic fun ordered(rows: List<Row>): List<Row> = rows.sortedWith { a, b ->
            val left = applicationOrder(a.source)
            val right = applicationOrder(b.source)
            if (left != right) return@sortedWith left.compareTo(right)
            if (left == 1) return@sortedWith (a.source.enabled == true).compareTo(b.source.enabled == true)
            val first = a.source.update
            val second = b.source.update
            if (left != 2 || first == null || second == null) return@sortedWith 0
            val base = com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(first.baseOld, second.baseOld)
            if (base != 0) base else com.cozooo.dlc_manager.client.update.DlcUpdateService.compareVersions(first.dlcOld, second.dlcOld)
        }
        @JvmStatic fun index(items: List<Item>): Map<String, Item> {
            val result = sortedMapOf<String, Item>()
            for (item in items) require(result.put(item.key, item) == null) { "Duplicate DLC configuration: ${item.key}" }
            return result
        }
    }
}
