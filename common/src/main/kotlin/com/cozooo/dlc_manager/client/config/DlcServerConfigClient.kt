package com.cozooo.dlc_manager.client.config

import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.config.options.ConfigString
import fi.dy.masa.malilib.util.StringUtils

object DlcServerConfigClient {
    private val SERVER_SUPPORT = DlcConfigBoolean(
        "serverSupport", true,
        "dlc_manager.config.comment.ui.serverSupport", "dlc_manager.config.ui.serverSupport"
    )
    private val SERVER_PORT = DlcConfigInteger(
        "serverPort", DlcPathConfig.DEFAULT_SERVER_PORT, 1, 65535,
        "dlc_manager.config.comment.ui.serverPort", "dlc_manager.config.ui.serverPort"
    )
    private val SERVER_PASSWORD = DlcConfigString(
        "serverPassword", "",
        "dlc_manager.config.comment.ui.serverPassword", "dlc_manager.config.ui.serverPassword"
    )
    private val API_ENABLED = DlcConfigBoolean(
        "apiEnabled", DlcPathConfig.DEFAULT_API_ENABLED,
        "dlc_manager.config.comment.ui.apiEnabled", "dlc_manager.config.ui.apiEnabled"
    )
    private val API_PORT = DlcConfigInteger(
        "apiPort", DlcPathConfig.DEFAULT_API_PORT, 0, 65535,
        "dlc_manager.config.comment.ui.apiPort", "dlc_manager.config.ui.apiPort"
    )
    private val API_TOKEN = DlcConfigString(
        "apiToken", "",
        "dlc_manager.config.comment.ui.apiToken", "dlc_manager.config.ui.apiToken"
    )
    private val UI_ENABLE_ALL = DlcConfigBoolean(
        "isEnableAllButtonShown", DlcPathConfig.DEFAULT_ENABLE_ALL_SHOWN,
        "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown"
    )
    private val UI_UPDATE_MANAGER = DlcConfigBoolean(
        "isUpdateManagerOn", DlcPathConfig.DEFAULT_UPDATE_MANAGER_ON,
        "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn"
    )
    private val UI_SELF_UPDATE = DlcConfigBoolean(
        "isSelfUpdateAutoCheckOn", DlcPathConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON,
        "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn"
    )
    private val UI_PRESET = DlcConfigBoolean(
        "isPresetFuncOn", DlcPathConfig.DEFAULT_PRESET_FUNC_ON,
        "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn"
    )
    private val UI_WAITING_TIME = DlcConfigInteger(
        "WaitingTime", DlcPathConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10,
        "dlc_manager.config.comment.ui.WaitingTime", "dlc_manager.config.ui.WaitingTime"
    )
    private val MODPACK = DlcConfigString("modpack", DlcPathConfig.DEFAULT_MODPACK, "dlc_manager.config.comment.modpack.modpack", "dlc_manager.config.modpack.modpack")
    private val MODPACK_URL = DlcConfigString("modpackUrl", DlcPathConfig.DEFAULT_MODPACK_URL, "dlc_manager.config.comment.modpack.modpackUrl", "dlc_manager.config.modpack.modpackUrl")
    private val UPDATE_GIT_REPO = DlcConfigString("updateGitRepo", DlcPathConfig.DEFAULT_UPDATE_GITHUB_REPO_URL, "dlc_manager.config.comment.modpack.updateGitRepo", "dlc_manager.config.modpack.updateGitRepo")

    private val DLC_ROOT = DlcConfigString(
        "dlcRoot", DlcPathConfig.DEFAULT_DLC_ROOT,
        "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot"
    )
    private val TACZ_ROOT = DlcConfigString(
        "taczRoot", DlcPathConfig.DEFAULT_TACZ_DLC_ROOT,
        "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot"
    )
    private val RESOURCEPACK_ROOT = DlcConfigString(
        "resourcepackRoot", DlcPathConfig.DEFAULT_RESOURCEPACK_ROOT,
        "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot"
    )
    private val DATAPACK_ROOT = DlcConfigString(
        "datapackRoot", DlcPathConfig.DEFAULT_DATAPACK_ROOT,
        "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot"
    )

    private val MAX_CONCURRENT = DlcConfigInteger(
        "maxConcurrentDownloads", DlcPathConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64,
        "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads"
    )
    private val MAX_SEGMENTS = DlcConfigInteger(
        "maxSegmentsPerFile", DlcPathConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64,
        "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile"
    )
    private val MIN_SEGMENT_MB = DlcConfigInteger(
        "minSegmentMB", DlcPathConfig.DEFAULT_MIN_SEGMENT_MB, 1, Int.MAX_VALUE,
        "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB"
    )
    private val MAX_RETRY = DlcConfigInteger(
        "maxRetryCount", DlcPathConfig.DEFAULT_MAX_RETRY, 1, 100,
        "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount"
    )
    private val CONNECT_TIMEOUT = DlcConfigInteger(
        "connectTimeoutSeconds", DlcPathConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000,
        "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds"
    )
    private val TASK_TIMEOUT = DlcConfigInteger(
        "taskTimeoutSeconds", DlcPathConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000,
        "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds"
    )
    private val DOWNLOAD_MIRROR_MODE = DlcConfigString(
        "downloadMirrorMode", DlcPathConfig.DEFAULT_DOWNLOAD_MIRROR_MODE,
        "dlc_manager.config.comment.download.downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode"
    )
    private val DOWNLOAD_MIRROR_PROBE_COUNT = DlcConfigInteger(
        "downloadMirrorProbeCount", DlcPathConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT, 1, 100,
        "dlc_manager.config.comment.download.downloadMirrorProbeCount", "dlc_manager.config.download.downloadMirrorProbeCount"
    )
    private val DOWNLOAD_MIRROR_PROBE_STRATEGY = DlcConfigString(
        "downloadMirrorProbeStrategy", DlcPathConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY,
        "dlc_manager.config.comment.download.downloadMirrorProbeStrategy", "dlc_manager.config.download.downloadMirrorProbeStrategy"
    )

    private val SERVER_CONFIGS: List<IConfigBase> = listOf(
        SERVER_SUPPORT,
        SERVER_PORT,
        SERVER_PASSWORD,
        API_ENABLED,
        API_PORT,
        API_TOKEN
    )

    private val MODPACK_CONFIGS: List<IConfigBase> = listOf(
        MODPACK,
        MODPACK_URL,
        UPDATE_GIT_REPO
    )

    private val UI_CONFIGS: List<IConfigBase> = listOf(
        UI_ENABLE_ALL,
        UI_UPDATE_MANAGER,
        UI_SELF_UPDATE,
        UI_PRESET,
        UI_WAITING_TIME
    )

    private val PATH_CONFIGS: List<IConfigBase> = listOf(
        DLC_ROOT,
        TACZ_ROOT,
        RESOURCEPACK_ROOT,
        DATAPACK_ROOT
    )

    private val DOWNLOAD_CONFIGS: List<IConfigBase> = listOf(
        MAX_CONCURRENT,
        MAX_SEGMENTS,
        MIN_SEGMENT_MB,
        MAX_RETRY,
        CONNECT_TIMEOUT,
        TASK_TIMEOUT,
        DOWNLOAD_MIRROR_MODE,
        DOWNLOAD_MIRROR_PROBE_COUNT,
        DOWNLOAD_MIRROR_PROBE_STRATEGY
    )

    @JvmStatic
    fun getServerConfigs(): List<IConfigBase> = SERVER_CONFIGS

    @JvmStatic
    fun getUiConfigs(): List<IConfigBase> = UI_CONFIGS

    @JvmStatic
    fun getModpackConfigs(): List<IConfigBase> = MODPACK_CONFIGS

    @JvmStatic
    fun getPathConfigs(): List<IConfigBase> = PATH_CONFIGS

    @JvmStatic
    fun getDownloadConfigs(): List<IConfigBase> = DOWNLOAD_CONFIGS

    @JvmStatic
    fun loadFromJson(root: JsonObject?) {
        if (root == null) {
            return
        }
        val server = asObject(root, "server")
        SERVER_SUPPORT.booleanValue = getBoolean(server, "serverSupport", SERVER_SUPPORT.booleanValue)
        SERVER_PORT.integerValue = getInt(server, "serverPort", SERVER_PORT.integerValue)
        SERVER_PASSWORD.setValueFromString(getString(server, "serverPassword", SERVER_PASSWORD.stringValue))
        MODPACK_URL.setValueFromString(getString(server, "modpackUrl", MODPACK_URL.stringValue))
        UPDATE_GIT_REPO.setValueFromString(getString(server, "updateGithubRepo", UPDATE_GIT_REPO.stringValue))

        val api = asObject(root, "api")
        API_ENABLED.booleanValue = getBoolean(api, "apiEnabled", API_ENABLED.booleanValue)
        API_PORT.integerValue = getInt(api, "apiPort", API_PORT.integerValue)
        API_TOKEN.setValueFromString(getString(api, "apiToken", API_TOKEN.stringValue))

        val ui = asObject(root, "ui")
        UI_ENABLE_ALL.booleanValue = getBoolean(ui, "isEnableAllButtonShown", UI_ENABLE_ALL.booleanValue)
        UI_UPDATE_MANAGER.booleanValue = getBoolean(ui, "isUpdateManagerOn", UI_UPDATE_MANAGER.booleanValue)
        UI_SELF_UPDATE.booleanValue = getBoolean(ui, "isSelfUpdateAutoCheckOn", UI_SELF_UPDATE.booleanValue)
        UI_PRESET.booleanValue = getBoolean(ui, "isPresetFuncOn", UI_PRESET.booleanValue)
        UI_WAITING_TIME.integerValue = getInt(ui, "WaitingTime", UI_WAITING_TIME.integerValue)

        val modpack = asObject(root, "modpack")
        MODPACK.setValueFromString(getString(modpack, "modpack", MODPACK.stringValue))
        MODPACK_URL.setValueFromString(getString(modpack, "modpackUrl", MODPACK_URL.stringValue))
        UPDATE_GIT_REPO.setValueFromString(getString(modpack, "updateGitRepo", getString(modpack, "updateGithubRepo", UPDATE_GIT_REPO.stringValue)))

        val paths = asObject(root, "paths")
        DLC_ROOT.setValueFromString(getString(paths, "dlcRoot", DLC_ROOT.stringValue))
        TACZ_ROOT.setValueFromString(getString(paths, "taczRoot", TACZ_ROOT.stringValue))
        RESOURCEPACK_ROOT.setValueFromString(getString(paths, "resourcepackRoot", RESOURCEPACK_ROOT.stringValue))
        DATAPACK_ROOT.setValueFromString(getString(paths, "datapackRoot", DATAPACK_ROOT.stringValue))

        val downloads = asObject(root, "downloads")
        MAX_CONCURRENT.integerValue = getInt(downloads, "maxConcurrentDownloads", MAX_CONCURRENT.integerValue)
        MAX_SEGMENTS.integerValue = getInt(downloads, "maxSegmentsPerFile", MAX_SEGMENTS.integerValue)
        MIN_SEGMENT_MB.integerValue = getInt(downloads, "minSegmentMB", MIN_SEGMENT_MB.integerValue)
        MAX_RETRY.integerValue = getInt(downloads, "maxRetryCount", MAX_RETRY.integerValue)
        CONNECT_TIMEOUT.integerValue = getInt(downloads, "connectTimeoutSeconds", CONNECT_TIMEOUT.integerValue)
        TASK_TIMEOUT.integerValue = getInt(downloads, "taskTimeoutSeconds", TASK_TIMEOUT.integerValue)
        DOWNLOAD_MIRROR_MODE.setValueFromString(DlcPathConfig.normalizeDownloadMirrorMode(getString(downloads, "downloadMirrorMode", DOWNLOAD_MIRROR_MODE.stringValue)))
        DOWNLOAD_MIRROR_PROBE_COUNT.integerValue = getInt(downloads, "downloadMirrorProbeCount", DOWNLOAD_MIRROR_PROBE_COUNT.integerValue)
        DOWNLOAD_MIRROR_PROBE_STRATEGY.setValueFromString(DlcPathConfig.normalizeDownloadMirrorProbeStrategy(getString(downloads, "downloadMirrorProbeStrategy", DOWNLOAD_MIRROR_PROBE_STRATEGY.stringValue)))
    }

    @JvmStatic
    fun toJson(): JsonObject {
        val root = JsonObject()
        val server = JsonObject()
        server.addProperty("serverSupport", SERVER_SUPPORT.booleanValue)
        server.addProperty("serverPort", SERVER_PORT.integerValue)
        server.addProperty("serverPassword", SERVER_PASSWORD.stringValue)
        root.add("server", server)

        val api = JsonObject()
        api.addProperty("apiEnabled", API_ENABLED.booleanValue)
        api.addProperty("apiPort", API_PORT.integerValue)
        api.addProperty("apiToken", API_TOKEN.stringValue)
        root.add("api", api)

        val ui = JsonObject()
        ui.addProperty("isEnableAllButtonShown", UI_ENABLE_ALL.booleanValue)
        ui.addProperty("isUpdateManagerOn", UI_UPDATE_MANAGER.booleanValue)
        ui.addProperty("isSelfUpdateAutoCheckOn", UI_SELF_UPDATE.booleanValue)
        ui.addProperty("isPresetFuncOn", UI_PRESET.booleanValue)
        ui.addProperty("WaitingTime", UI_WAITING_TIME.integerValue)
        root.add("ui", ui)

        val modpack = JsonObject()
        modpack.addProperty("modpack", MODPACK.stringValue)
        modpack.addProperty("modpackUrl", MODPACK_URL.stringValue)
        modpack.addProperty("updateGitRepo", UPDATE_GIT_REPO.stringValue)
        root.add("modpack", modpack)

        val paths = JsonObject()
        paths.addProperty("dlcRoot", DLC_ROOT.stringValue)
        paths.addProperty("taczRoot", TACZ_ROOT.stringValue)
        paths.addProperty("resourcepackRoot", RESOURCEPACK_ROOT.stringValue)
        paths.addProperty("datapackRoot", DATAPACK_ROOT.stringValue)
        root.add("paths", paths)

        val downloads = JsonObject()
        downloads.addProperty("maxConcurrentDownloads", MAX_CONCURRENT.integerValue)
        downloads.addProperty("maxSegmentsPerFile", MAX_SEGMENTS.integerValue)
        downloads.addProperty("minSegmentMB", MIN_SEGMENT_MB.integerValue)
        downloads.addProperty("maxRetryCount", MAX_RETRY.integerValue)
        downloads.addProperty("connectTimeoutSeconds", CONNECT_TIMEOUT.integerValue)
        downloads.addProperty("taskTimeoutSeconds", TASK_TIMEOUT.integerValue)
        downloads.addProperty("downloadMirrorMode", DlcPathConfig.normalizeDownloadMirrorMode(DOWNLOAD_MIRROR_MODE.stringValue))
        downloads.addProperty("downloadMirrorProbeCount", maxOf(1, DOWNLOAD_MIRROR_PROBE_COUNT.integerValue))
        downloads.addProperty("downloadMirrorProbeStrategy", DlcPathConfig.normalizeDownloadMirrorProbeStrategy(DOWNLOAD_MIRROR_PROBE_STRATEGY.stringValue))
        root.add("downloads", downloads)

        return root
    }

    private fun asObject(root: JsonObject?, key: String): JsonObject {
        if (root == null) {
            return JsonObject()
        }
        val element = DcConfigIO.getIgnoreCase(root, key)
        if (element != null && element.isJsonObject) {
            return element.asJsonObject
        }
        return JsonObject()
    }

    private fun getBoolean(obj: JsonObject, key: String, fallback: Boolean): Boolean {
        val element = DcConfigIO.getIgnoreCase(obj, key)
        if (element != null && element.isJsonPrimitive) {
            return try {
                element.asBoolean
            } catch (_: Exception) {
                fallback
            }
        }
        return fallback
    }

    private fun getInt(obj: JsonObject, key: String, fallback: Int): Int {
        val element = DcConfigIO.getIgnoreCase(obj, key)
        if (element != null && element.isJsonPrimitive) {
            return try {
                element.asInt
            } catch (_: Exception) {
                fallback
            }
        }
        return fallback
    }

    private fun getString(obj: JsonObject, key: String, fallback: String?): String? {
        val element = DcConfigIO.getIgnoreCase(obj, key)
        if (element != null && element.isJsonPrimitive) {
            return try {
                element.asString
            } catch (_: Exception) {
                fallback
            }
        }
        return fallback
    }

    private class DlcConfigString(
        name: String,
        defaultValue: String?,
        private val commentKey: String?,
        private val translationKey: String,
    ) : ConfigString(name, defaultValue, "") {
        override fun getConfigGuiDisplayName(): String {
            return StringUtils.translate(translationKey)
        }

        override fun getComment(): String {
            if (commentKey.isNullOrBlank()) {
                return ""
            }
            return StringUtils.translate(commentKey)
        }
    }

    private class DlcConfigBoolean(
        name: String,
        defaultValue: Boolean,
        private val commentKey: String?,
        private val translationKey: String,
    ) : ConfigBoolean(name, defaultValue, "", name) {
        override fun getConfigGuiDisplayName(): String {
            return StringUtils.translate(translationKey)
        }

        override fun getComment(): String {
            if (commentKey.isNullOrBlank()) {
                return ""
            }
            return StringUtils.translate(commentKey)
        }
    }

    private class DlcConfigInteger(
        name: String,
        defaultValue: Int,
        min: Int,
        max: Int,
        private val commentKey: String?,
        private val translationKey: String,
    ) : ConfigInteger(name, defaultValue, min, max, "") {
        override fun getConfigGuiDisplayName(): String {
            return StringUtils.translate(translationKey)
        }

        override fun getComment(): String {
            if (commentKey.isNullOrBlank()) {
                return ""
            }
            return StringUtils.translate(commentKey)
        }
    }
}
