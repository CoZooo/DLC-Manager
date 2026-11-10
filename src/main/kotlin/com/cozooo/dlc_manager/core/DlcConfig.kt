package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object DlcConfig {
    const val MOD_ID: String = "dlc_manager"
    private const val CONFIG_DIR: String = "dlc_manager"
    private const val CONFIG_NAME: String = "config"
    private const val CONFIG_SERVER_NAME: String = "config-server"

    const val DEFAULT_DLC_ROOT: String = "DLC"
    const val DEFAULT_TACZ_ROOT: String = "tacz"
    const val DEFAULT_RESOURCEPACK_ROOT: String = "resourcepacks"
    const val DEFAULT_DATAPACK_ROOT: String = "datapacks"
    const val DEFAULT_ENABLE_ALL_SHOWN: Boolean = true
    const val DEFAULT_CONFIG_BUTTON_SHOWN: Boolean = false
    const val DEFAULT_U_BUTTON_SHOWN: Boolean = true
    const val DEFAULT_UPDATE_MANAGER_ON: Boolean = true
    const val DEFAULT_SELF_UPDATE_AUTO_CHECK_ON: Boolean = false
    const val DEFAULT_PRESET_FUNC_ON: Boolean = true
    const val DEFAULT_PACK_FUNC_ON: Boolean = true
    const val DEFAULT_P_BUTTON_SHOWN: Boolean = true
    const val DEFAULT_SHOW_ON_FIRST_RUN: Boolean = false
    const val DEFAULT_SHOW_DEPENDENCY_REMINDER: Boolean = false
    const val DEFAULT_ALARM_WAITING_TIME: Int = 0
    const val DEFAULT_SERVER_PASSWORD: String = ""
    const val DEFAULT_API_ENABLED: Boolean = false
    const val DEFAULT_API_PORT: Int = 10086
    const val DEFAULT_API_TOKEN: String = ""
    const val DEFAULT_RCON_HOST: String = ""
    const val DEFAULT_RCON_PORT: Int = 25575
    const val DEFAULT_RCON_PASSWORD: String = ""
    const val DEFAULT_TITLE_ANCHOR: String = "auto"
    const val TITLE_ANCHOR_HIDDEN: String = "hidden"
    const val DEFAULT_TITLE_OFFSET_X: Int = 0
    const val DEFAULT_TITLE_OFFSET_Y: Int = 0
    const val DEFAULT_TITLE_UPDATE_ANCHOR: String = "auto"
    const val DEFAULT_TITLE_UPDATE_OFFSET_X: Int = 0
    const val DEFAULT_TITLE_UPDATE_OFFSET_Y: Int = 0
    const val DEFAULT_NOTICE_DISPLAY_MODE: String = "manager_button"
    const val DEFAULT_NOTICE_BUTTON_ANCHOR: String = "bottom_right"
    const val DEFAULT_NOTICE_BUTTON_OFFSET_X: Int = 0
    const val DEFAULT_NOTICE_BUTTON_OFFSET_Y: Int = 0
    const val DEFAULT_NOTICE_PANEL_ANCHOR: String = "top_left"
    const val DEFAULT_NOTICE_PANEL_OFFSET_X: Int = 0
    const val DEFAULT_NOTICE_PANEL_OFFSET_Y: Int = 0
    const val DEFAULT_NOTICE_PANEL_WIDTH: Int = 0
    const val DEFAULT_NOTICE_PANEL_HEIGHT: Int = 0
    const val MAX_NOTICE_OFFSET: Int = 1000
    const val MAX_NOTICE_PANEL_SIZE: Int = 1000
    const val DEFAULT_MAX_CONCURRENT_DOWNLOADS: Int = 5
    const val DEFAULT_MAX_SEGMENTS_PER_FILE: Int = 4
    const val DEFAULT_MIN_SEGMENT_MB: Int = 20
    const val DEFAULT_MAX_RETRY: Int = 2
    const val DEFAULT_CONNECT_TIMEOUT_SECONDS: Int = 5
    const val DEFAULT_TASK_TIMEOUT_SECONDS: Int = 60
    const val DOWNLOAD_MIRROR_OFFICIAL_ONLY: String = "official_only"
    const val DOWNLOAD_MIRROR_MIRROR_ONLY: String = "mirror_only"
    const val DOWNLOAD_MIRROR_OFFICIAL_FIRST: String = "official_first"
    const val DOWNLOAD_MIRROR_MIRROR_FIRST: String = "mirror_first"
    const val DEFAULT_DOWNLOAD_MIRROR_MODE: String = DOWNLOAD_MIRROR_OFFICIAL_ONLY
    const val DEFAULT_MODPACK: String = ""
    const val DEFAULT_MODPACK_URL: String = ""
    const val DEFAULT_UPDATE_GIT_REPO_URL: String = ""
    const val DEFAULT_MODPACK_AUTO_UPDATE: Boolean = false
    const val DEFAULT_MODPACK_FORCE_UPDATE: Boolean = false
    const val DEFAULT_PACK_UPDATE_CHANNEL: String = "all"

    private var dlcRoot = DEFAULT_DLC_ROOT
    private var taczRoot = DEFAULT_TACZ_ROOT
    private var resourcepackRoot = DEFAULT_RESOURCEPACK_ROOT
    private var datapackRoot = DEFAULT_DATAPACK_ROOT
    private var enableAllButtonShown = DEFAULT_ENABLE_ALL_SHOWN
    private var configButtonShown = DEFAULT_CONFIG_BUTTON_SHOWN
    private var uButtonShown = DEFAULT_U_BUTTON_SHOWN
    private var updateManagerOn = DEFAULT_UPDATE_MANAGER_ON
    private var selfUpdateAutoCheckOn = DEFAULT_SELF_UPDATE_AUTO_CHECK_ON
    private var presetFuncOn = DEFAULT_PRESET_FUNC_ON
    private var packFuncOn = DEFAULT_PACK_FUNC_ON
    private var pButtonShown = DEFAULT_P_BUTTON_SHOWN
    private var showOnFirstRun = DEFAULT_SHOW_ON_FIRST_RUN
    private var showDependencyReminder = DEFAULT_SHOW_DEPENDENCY_REMINDER
    private var alarmWaitingTime = DEFAULT_ALARM_WAITING_TIME
    private var serverPassword = DEFAULT_SERVER_PASSWORD
    private var apiEnabled = DEFAULT_API_ENABLED
    private var apiPort = DEFAULT_API_PORT
    private var apiToken = DEFAULT_API_TOKEN
    private var serverRconHost = DEFAULT_RCON_HOST
    private var serverRconPort = DEFAULT_RCON_PORT
    private var serverRconPassword = DEFAULT_RCON_PASSWORD
    private var titleAnchor = DEFAULT_TITLE_ANCHOR
    private var titleOffsetX = DEFAULT_TITLE_OFFSET_X
    private var titleOffsetY = DEFAULT_TITLE_OFFSET_Y
    private var titleUpdateAnchor = DEFAULT_TITLE_UPDATE_ANCHOR
    private var titleUpdateOffsetX = DEFAULT_TITLE_UPDATE_OFFSET_X
    private var titleUpdateOffsetY = DEFAULT_TITLE_UPDATE_OFFSET_Y
    private var noticeDisplayMode = DEFAULT_NOTICE_DISPLAY_MODE
    private var noticeButtonAnchor = DEFAULT_NOTICE_BUTTON_ANCHOR
    private var noticeButtonOffsetX = DEFAULT_NOTICE_BUTTON_OFFSET_X
    private var noticeButtonOffsetY = DEFAULT_NOTICE_BUTTON_OFFSET_Y
    private var noticePanelAnchor = DEFAULT_NOTICE_PANEL_ANCHOR
    private var noticePanelOffsetX = DEFAULT_NOTICE_PANEL_OFFSET_X
    private var noticePanelOffsetY = DEFAULT_NOTICE_PANEL_OFFSET_Y
    private var noticePanelWidth = DEFAULT_NOTICE_PANEL_WIDTH
    private var noticePanelHeight = DEFAULT_NOTICE_PANEL_HEIGHT
    private var modpack = DEFAULT_MODPACK
    private var modpackUrlClient = DEFAULT_MODPACK_URL
    private var updateGitRepoClient = DEFAULT_UPDATE_GIT_REPO_URL
    private var modpackAutoUpdate = DEFAULT_MODPACK_AUTO_UPDATE
    private var modpackForceUpdate = DEFAULT_MODPACK_FORCE_UPDATE
    private var packUpdateChannel = DEFAULT_PACK_UPDATE_CHANNEL
    private var maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS
    private var maxSegmentsPerFile = DEFAULT_MAX_SEGMENTS_PER_FILE
    private var minSegmentMb = DEFAULT_MIN_SEGMENT_MB
    private var maxRetryCount = DEFAULT_MAX_RETRY
    private var connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS
    private var taskTimeoutSeconds = DEFAULT_TASK_TIMEOUT_SECONDS
    private var downloadMirrorMode = DEFAULT_DOWNLOAD_MIRROR_MODE

    @JvmStatic fun load() = loadClient()
    @JvmStatic fun loadClient() {
        loadClientFromDisk()
        ensureClientConfigFile()
        migrateKnownConfigsToDc()
    }
    @JvmStatic fun loadServer() {
        loadServerFromDisk()
        ensureServerConfigFile()
        migrateKnownConfigsToDc()
    }
    @JvmStatic fun reloadFromDisk(server: Boolean) {
        if (server) loadServerFromDisk() else loadClientFromDisk()
    }
    @JvmStatic fun save() = saveClient()
    @JvmStatic fun saveClient() = saveClientToDisk()
    @JvmStatic fun saveServer() = saveServerToDisk()
    @JvmStatic fun saveShared() = saveClientToDisk()

    @JvmStatic
    fun buildServerConfigJson(): JsonObject {
        val root = JsonObject()
        writeShared(root)
        writeServer(root)
        return root
    }

    @JvmStatic fun buildSyncConfigJson(source: JsonObject?): JsonObject {
        val defaults = JsonObject()
        val paths = JsonObject()
        paths.addProperty("dlcRoot", DEFAULT_DLC_ROOT)
        paths.addProperty("taczRoot", DEFAULT_TACZ_ROOT)
        paths.addProperty("resourcepackRoot", DEFAULT_RESOURCEPACK_ROOT)
        paths.addProperty("datapackRoot", DEFAULT_DATAPACK_ROOT)
        defaults.add("paths", paths)
        val ui = JsonObject()
        ui.addProperty("isEnableAllButtonShown", DEFAULT_ENABLE_ALL_SHOWN)
        ui.addProperty("isUpdateManagerOn", DEFAULT_UPDATE_MANAGER_ON)
        ui.addProperty("isSelfUpdateAutoCheckOn", DEFAULT_SELF_UPDATE_AUTO_CHECK_ON)
        ui.addProperty("isPresetFuncOn", DEFAULT_PRESET_FUNC_ON)
        ui.addProperty("waitingTime", DEFAULT_ALARM_WAITING_TIME)
        defaults.add("ui", ui)
        val downloads = JsonObject()
        downloads.addProperty("maxConcurrentDownloads", DEFAULT_MAX_CONCURRENT_DOWNLOADS)
        downloads.addProperty("maxSegmentsPerFile", DEFAULT_MAX_SEGMENTS_PER_FILE)
        downloads.addProperty("minSegmentMB", DEFAULT_MIN_SEGMENT_MB)
        downloads.addProperty("maxRetryCount", DEFAULT_MAX_RETRY)
        downloads.addProperty("connectTimeoutSeconds", DEFAULT_CONNECT_TIMEOUT_SECONDS)
        downloads.addProperty("taskTimeoutSeconds", DEFAULT_TASK_TIMEOUT_SECONDS)
        downloads.addProperty("downloadMirrorMode", DEFAULT_DOWNLOAD_MIRROR_MODE)
        defaults.add("downloads", downloads)
        val modpack = JsonObject()
        modpack.addProperty("modpack", DEFAULT_MODPACK)
        modpack.addProperty("modpackUrl", DEFAULT_MODPACK_URL)
        modpack.addProperty("updateGitRepo", DEFAULT_UPDATE_GIT_REPO_URL)
        modpack.addProperty("autoUpdate", DEFAULT_MODPACK_AUTO_UPDATE)
        modpack.addProperty("forceUpdate", DEFAULT_MODPACK_FORCE_UPDATE)
        modpack.addProperty("packUpdateChannel", DEFAULT_PACK_UPDATE_CHANNEL)
        defaults.add("modpack", modpack)
        for ((section, entries) in defaults.entrySet()) {
            val incoming = DlcDcConfigIO.getObjectIgnoreCase(source, section)
            val values = entries.asJsonObject
            for (key in values.keySet().toList()) {
                val value = DlcDcConfigIO.getIgnoreCase(incoming, key)
                    ?: (if (section == "modpack" && key == "updateGitRepo") DlcDcConfigIO.getIgnoreCase(incoming, "updateGithubRepo") else null) ?: continue
                if (!value.isJsonPrimitive) continue
                val expected = values.getAsJsonPrimitive(key)
                val actual = value.asJsonPrimitive
                if (expected.isNumber && actual.isNumber) values.addProperty(key, value.asInt)
                else if (expected.isBoolean && actual.isBoolean || expected.isString && actual.isString) values.add(key, value.deepCopy())
            }
        }
        return defaults
    }

    @JvmStatic
    fun buildSharedConfigJson(): JsonObject {
        val root = JsonObject()
        writeShared(root)
        return root
    }

    @JvmStatic
    fun applyServerConfigJson(root: JsonObject?) {
        if (root == null) return
        readSection(root, "server", buildServerReaders())
    }

    @JvmStatic
    fun applySharedConfigJson(root: JsonObject?) {
        if (root == null) return
        readSection(root, "api", buildApiReaders())
        readSection(root, "paths", buildPathReaders())
        readSection(root, "ui", buildUiReaders())
        readSection(root, "downloads", buildDownloadReaders())
        readSection(root, "modpack", buildModpackReaders())
    }

    @JvmStatic fun getDlcRoot(): Path = getDlcRoots()[0]
    @JvmStatic fun getTaczRoot(): Path = getTaczRoots()[0]
    @JvmStatic fun getResourcepackRoot(): Path = getResourcepackRoots()[0]
    @JvmStatic fun getDatapackRoot(): Path = getDatapackRoots()[0]
    @JvmStatic fun getDlcRoots(): List<Path> = resolvePaths(getDlcRootRaw(), DEFAULT_DLC_ROOT)
    @JvmStatic fun getTaczRoots(): List<Path> = resolvePaths(getTaczRootRaw(), DEFAULT_TACZ_ROOT)
    @JvmStatic fun getResourcepackRoots(): List<Path> = resolvePaths(getResourcepackRootRaw(), DEFAULT_RESOURCEPACK_ROOT)
    @JvmStatic fun getDatapackRoots(): List<Path> = resolvePaths(getDatapackRootRaw(), DEFAULT_DATAPACK_ROOT)
    @JvmStatic fun getDlcRootRaw(): String = stringOrDefault(dlcRoot, DEFAULT_DLC_ROOT)
    @JvmStatic fun getTaczRootRaw(): String = stringOrDefault(taczRoot, DEFAULT_TACZ_ROOT)
    @JvmStatic fun getResourcepackRootRaw(): String = stringOrDefault(resourcepackRoot, DEFAULT_RESOURCEPACK_ROOT)
    @JvmStatic fun getDatapackRootRaw(): String = stringOrDefault(datapackRoot, DEFAULT_DATAPACK_ROOT)
    @JvmStatic fun isEnableAllButtonShown(): Boolean = enableAllButtonShown
    @JvmStatic fun isConfigButtonShown(): Boolean = configButtonShown
    @JvmStatic fun isUButtomShown(): Boolean = uButtonShown
    @JvmStatic fun isUpdateManagerOn(): Boolean = updateManagerOn
    @JvmStatic fun isSelfUpdateAutoCheckOn(): Boolean = selfUpdateAutoCheckOn
    @JvmStatic fun isPresetFuncOn(): Boolean = presetFuncOn
    @JvmStatic fun isPackFuncOn(): Boolean = packFuncOn
    @JvmStatic fun isPButtomShown(): Boolean = pButtonShown
    @JvmStatic fun isShowOnFirstRun(): Boolean = showOnFirstRun
    @JvmStatic fun isShowDependencyReminder(): Boolean = showDependencyReminder
    @JvmStatic fun getAlarmWaitingTime(): Int = alarmWaitingTime
    @JvmStatic fun isServerSupportOn(): Boolean = true
    @JvmStatic fun isServerSupportOnServer(): Boolean = true
    @JvmStatic fun getServerPassword(): String = serverPassword
    @JvmStatic fun isApiEnabled(): Boolean = apiEnabled
    @JvmStatic fun getApiPort(): Int = apiPort
    @JvmStatic fun getApiToken(): String = apiToken
    @JvmStatic fun getServerRconHost(): String = serverRconHost
    @JvmStatic fun getServerRconPort(): Int = serverRconPort
    @JvmStatic fun getServerRconPassword(): String = serverRconPassword
    @JvmStatic fun getTitleAnchor(): String = titleAnchor
    @JvmStatic fun isTitleAnchorHidden(): Boolean = titleAnchor.trim().equals(TITLE_ANCHOR_HIDDEN, ignoreCase = true)
    @JvmStatic fun getTitleOffsetX(): Int = titleOffsetX
    @JvmStatic fun getTitleOffsetY(): Int = titleOffsetY
    @JvmStatic fun getTitleUpdateAnchor(): String = titleUpdateAnchor
    @JvmStatic fun getTitleUpdateOffsetX(): Int = titleUpdateOffsetX
    @JvmStatic fun getTitleUpdateOffsetY(): Int = titleUpdateOffsetY
    @JvmStatic fun getNoticeDisplayMode(): String = noticeDisplayMode.ifBlank { DEFAULT_NOTICE_DISPLAY_MODE }
    @JvmStatic fun isNoticeDisplayModeManagerButton(): Boolean = getNoticeDisplayMode().equals("manager_button", ignoreCase = true)
    @JvmStatic fun isNoticeDisplayModeMainMenuButton(): Boolean = getNoticeDisplayMode().equals("main_menu_button", ignoreCase = true)
    @JvmStatic fun isNoticeDisplayModeMainMenuPanel(): Boolean = getNoticeDisplayMode().equals("main_menu_panel", ignoreCase = true)
    @JvmStatic fun getNoticeButtonAnchor(): String = noticeButtonAnchor.ifBlank { DEFAULT_NOTICE_BUTTON_ANCHOR }
    @JvmStatic fun getNoticeButtonOffsetX(): Int = noticeButtonOffsetX
    @JvmStatic fun getNoticeButtonOffsetY(): Int = noticeButtonOffsetY
    @JvmStatic fun getNoticePanelAnchor(): String = noticePanelAnchor.ifBlank { DEFAULT_NOTICE_PANEL_ANCHOR }
    @JvmStatic fun getNoticePanelOffsetX(): Int = noticePanelOffsetX
    @JvmStatic fun getNoticePanelOffsetY(): Int = noticePanelOffsetY
    @JvmStatic fun getNoticePanelWidth(): Int = maxOf(0, noticePanelWidth)
    @JvmStatic fun getNoticePanelHeight(): Int = maxOf(0, noticePanelHeight)
    @JvmStatic fun getModpack(): String = modpack
    @JvmStatic fun getModpackUrl(): String = modpackUrlClient
    @JvmStatic fun getUpdateGitRepoUrl(): String = updateGitRepoClient
    @JvmStatic fun getServerModpackUrl(): String = getModpackUrl()
    @JvmStatic fun getServerUpdateGitRepoUrl(): String = getUpdateGitRepoUrl()
    @JvmStatic fun isModpackAutoUpdateOn(): Boolean = modpackAutoUpdate
    @JvmStatic fun isModpackForceUpdateOn(): Boolean = modpackForceUpdate
    @JvmStatic fun getPackUpdateChannel(): String = packUpdateChannel

    @JvmStatic
    fun computeTitleButtonPosition(screenWidth: Int, screenHeight: Int, buttonWidth: Int, buttonHeight: Int, defaultX: Int, defaultY: Int): IntArray {
        return computeAnchoredPosition(getTitleAnchor(), getTitleOffsetX(), getTitleOffsetY(), screenWidth, screenHeight, buttonWidth, buttonHeight, defaultX, defaultY)
    }
    @JvmStatic
    fun computeTitleUpdateButtonPosition(screenWidth: Int, screenHeight: Int, buttonWidth: Int, buttonHeight: Int, defaultX: Int, defaultY: Int): IntArray {
        return computeAnchoredPosition(getTitleUpdateAnchor(), getTitleUpdateOffsetX(), getTitleUpdateOffsetY(), screenWidth, screenHeight, buttonWidth, buttonHeight, defaultX, defaultY)
    }
    @JvmStatic
    fun computeNoticeButtonPosition(screenWidth: Int, screenHeight: Int, buttonWidth: Int, buttonHeight: Int, defaultX: Int, defaultY: Int): IntArray {
        return computeAnchoredPosition(getNoticeButtonAnchor(), getNoticeButtonOffsetX(), getNoticeButtonOffsetY(), screenWidth, screenHeight, buttonWidth, buttonHeight, defaultX, defaultY)
    }
    @JvmStatic
    fun computeNoticePanelPosition(screenWidth: Int, screenHeight: Int, panelWidth: Int, panelHeight: Int, defaultX: Int, defaultY: Int): IntArray {
        return computeAnchoredPosition(getNoticePanelAnchor(), getNoticePanelOffsetX(), getNoticePanelOffsetY(), screenWidth, screenHeight, panelWidth, panelHeight, defaultX, defaultY)
    }
    @JvmStatic fun isTitleUpdateAnchorAuto(): Boolean = getTitleUpdateAnchor().isBlank() || getTitleUpdateAnchor().equals("auto", ignoreCase = true)
    @JvmStatic
    fun shouldPlaceTitleUpdateButtonLeftOfTitle(): Boolean {
        if (!isTitleUpdateAnchorAuto()) return false
        return when (getTitleAnchor().trim().lowercase(Locale.ROOT)) {
            "top_right", "right", "bottom_right" -> true
            else -> false
        }
    }

    private fun computeAnchoredPosition(anchor: String?, offsetX: Int, offsetY: Int, screenWidth: Int, screenHeight: Int, width: Int, height: Int, defaultX: Int, defaultY: Int): IntArray {
        if (anchor.isNullOrBlank() || anchor.equals("auto", ignoreCase = true)) {
            return intArrayOf(defaultX + offsetX, defaultY + offsetY)
        }
        val normalized = anchor.trim().lowercase(Locale.ROOT).replace(' ', '_')
        val xy = when (normalized) {
            "top_left", "topleft", "top-left" -> 0 to 0
            "top", "top_center", "topcenter" -> (screenWidth - width) / 2 to 0
            "top_right", "topright", "top-right" -> screenWidth - width to 0
            "left", "center_left", "centerleft" -> 0 to (screenHeight - height) / 2
            "center", "middle" -> (screenWidth - width) / 2 to (screenHeight - height) / 2
            "right", "center_right", "centerright" -> screenWidth - width to (screenHeight - height) / 2
            "bottom_left", "bottomleft", "bottom-left" -> 0 to screenHeight - height
            "bottom", "bottom_center", "bottomcenter" -> (screenWidth - width) / 2 to screenHeight - height
            "bottom_right", "bottomright", "bottom-right" -> screenWidth - width to screenHeight - height
            else -> defaultX to defaultY
        }
        return intArrayOf(xy.first + offsetX, xy.second + offsetY)
    }

    @JvmStatic fun getMaxConcurrentDownloads(): Int = if (maxConcurrentDownloads < 1) DEFAULT_MAX_CONCURRENT_DOWNLOADS else maxConcurrentDownloads
    @JvmStatic fun getMaxSegmentsPerFile(): Int = if (maxSegmentsPerFile < 1) DEFAULT_MAX_SEGMENTS_PER_FILE else maxSegmentsPerFile
    @JvmStatic fun getMinSegmentBytes(): Long = (if (minSegmentMb < 1) DEFAULT_MIN_SEGMENT_MB else minSegmentMb).toLong() * DlcFileSize.MIB
    @JvmStatic fun getMaxRetryCount(): Int = if (maxRetryCount < 1) DEFAULT_MAX_RETRY else maxRetryCount
    @JvmStatic fun getConnectTimeoutMs(): Int = (if (connectTimeoutSeconds < 1) DEFAULT_CONNECT_TIMEOUT_SECONDS else connectTimeoutSeconds) * 1000
    @JvmStatic fun getTaskTimeoutMs(): Int = (if (taskTimeoutSeconds < 1) DEFAULT_TASK_TIMEOUT_SECONDS else taskTimeoutSeconds) * 1000
    @JvmStatic fun getDownloadMirrorMode(): String = normalizeDownloadMirrorMode(downloadMirrorMode)

    @JvmStatic fun setDlcRoot(value: String?) { if (value != null) dlcRoot = value }
    @JvmStatic fun setTaczRoot(value: String?) { if (value != null) taczRoot = value }
    @JvmStatic fun setResourcepackRoot(value: String?) { if (value != null) resourcepackRoot = value }
    @JvmStatic fun setDatapackRoot(value: String?) { if (value != null) datapackRoot = value }
    @JvmStatic fun setEnableAllButtonShown(value: Boolean) { enableAllButtonShown = value }
    @JvmStatic fun setConfigButtonShown(value: Boolean) { configButtonShown = value }
    @JvmStatic fun setUButtomShown(value: Boolean) { uButtonShown = value }
    @JvmStatic fun setUpdateManagerOn(value: Boolean) { updateManagerOn = value }
    @JvmStatic fun setSelfUpdateAutoCheckOn(value: Boolean) { selfUpdateAutoCheckOn = value }
    @JvmStatic fun setPresetFuncOn(value: Boolean) { presetFuncOn = value }
    @JvmStatic fun setPackFuncOn(value: Boolean) { packFuncOn = value }
    @JvmStatic fun setPButtomShown(value: Boolean) { pButtonShown = value }
    @JvmStatic fun setShowOnFirstRun(value: Boolean) { showOnFirstRun = value }
    @JvmStatic fun setShowDependencyReminder(value: Boolean) { showDependencyReminder = value }
    @JvmStatic fun setAlarmWaitingTime(value: Int) { alarmWaitingTime = clampInt(value, 0, 10, DEFAULT_ALARM_WAITING_TIME) }
    @JvmStatic fun setServerSupportOnServer(@Suppress("UNUSED_PARAMETER") value: Boolean) {}
    @JvmStatic fun setServerPassword(value: String?) { serverPassword = value ?: DEFAULT_SERVER_PASSWORD }
    @JvmStatic fun setApiEnabled(value: Boolean) { apiEnabled = value }
    @JvmStatic fun setApiPort(value: Int) { apiPort = clampInt(value, 0, 65535, DEFAULT_API_PORT) }
    @JvmStatic fun setApiToken(value: String?) { apiToken = value ?: DEFAULT_API_TOKEN }
    @JvmStatic fun setServerRconHost(value: String?) { serverRconHost = value ?: DEFAULT_RCON_HOST }
    @JvmStatic fun setServerRconPort(value: Int) { serverRconPort = clampInt(value, 1, 65535, DEFAULT_RCON_PORT) }
    @JvmStatic fun setServerRconPassword(value: String?) { serverRconPassword = value ?: DEFAULT_RCON_PASSWORD }
    @JvmStatic fun setTitleAnchor(value: String?) { titleAnchor = value ?: DEFAULT_TITLE_ANCHOR }
    @JvmStatic fun setTitleOffsetX(value: Int) { titleOffsetX = value }
    @JvmStatic fun setTitleOffsetY(value: Int) { titleOffsetY = value }
    @JvmStatic fun setTitleUpdateAnchor(value: String?) { titleUpdateAnchor = value ?: DEFAULT_TITLE_UPDATE_ANCHOR }
    @JvmStatic fun setTitleUpdateOffsetX(value: Int) { titleUpdateOffsetX = value }
    @JvmStatic fun setTitleUpdateOffsetY(value: Int) { titleUpdateOffsetY = value }
    @JvmStatic fun setNoticeDisplayMode(value: String?) { noticeDisplayMode = normalizeNoticeDisplayMode(value) }
    @JvmStatic fun setNoticeButtonAnchor(value: String?) { noticeButtonAnchor = if (value.isNullOrBlank()) DEFAULT_NOTICE_BUTTON_ANCHOR else value }
    @JvmStatic fun setNoticeButtonOffsetX(value: Int) { noticeButtonOffsetX = value.coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) }
    @JvmStatic fun setNoticeButtonOffsetY(value: Int) { noticeButtonOffsetY = value.coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) }
    @JvmStatic fun setNoticePanelAnchor(value: String?) { noticePanelAnchor = if (value.isNullOrBlank()) DEFAULT_NOTICE_PANEL_ANCHOR else value }
    @JvmStatic fun setNoticePanelOffsetX(value: Int) { noticePanelOffsetX = value.coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) }
    @JvmStatic fun setNoticePanelOffsetY(value: Int) { noticePanelOffsetY = value.coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) }
    @JvmStatic fun setNoticePanelWidth(value: Int) { noticePanelWidth = value.coerceIn(0, MAX_NOTICE_PANEL_SIZE) }
    @JvmStatic fun setNoticePanelHeight(value: Int) { noticePanelHeight = value.coerceIn(0, MAX_NOTICE_PANEL_SIZE) }
    @JvmStatic fun setModpack(value: String?) { modpack = value ?: DEFAULT_MODPACK }
    @JvmStatic fun setModpackUrl(value: String?) { modpackUrlClient = value ?: DEFAULT_MODPACK_URL }
    @JvmStatic fun setUpdateGitRepoUrl(value: String?) { updateGitRepoClient = value ?: DEFAULT_UPDATE_GIT_REPO_URL }
    @JvmStatic fun setModpackAutoUpdateOn(value: Boolean) { modpackAutoUpdate = value }
    @JvmStatic fun setModpackForceUpdateOn(value: Boolean) { modpackForceUpdate = value }
    @JvmStatic fun setPackUpdateChannel(value: String?) { packUpdateChannel = normalizePackUpdateChannel(value) }

    private fun normalizePackUpdateChannel(value: String?): String {
        if (value.isNullOrBlank()) {
            return DEFAULT_PACK_UPDATE_CHANNEL
        }
        return when (value.trim().lowercase(Locale.ROOT)) {
            "stable", "beta", "alpha", "nightly" -> value.trim().lowercase(Locale.ROOT)
            else -> DEFAULT_PACK_UPDATE_CHANNEL
        }
    }
    @JvmStatic fun setServerModpackUrl(value: String?) = setModpackUrl(value)
    @JvmStatic fun setServerUpdateGitRepoUrl(value: String?) = setUpdateGitRepoUrl(value)
    @JvmStatic fun setMaxConcurrentDownloads(value: Int) { maxConcurrentDownloads = maxOf(1, value) }
    @JvmStatic fun setMaxSegmentsPerFile(value: Int) { maxSegmentsPerFile = maxOf(1, value) }
    @JvmStatic fun setMinSegmentBytes(value: Long) {
        val mb = maxOf(1L, DlcFileSize.ceilMiB(value))
        minSegmentMb = minOf(Int.MAX_VALUE.toLong(), mb).toInt()
    }
    @JvmStatic fun setMaxRetryCount(value: Int) { maxRetryCount = maxOf(1, value) }
    @JvmStatic fun setConnectTimeoutMs(value: Int) { connectTimeoutSeconds = maxOf(1, kotlin.math.ceil(value / 1000.0).toInt()) }
    @JvmStatic fun setTaskTimeoutMs(value: Int) { taskTimeoutSeconds = maxOf(1, kotlin.math.ceil(value / 1000.0).toInt()) }
    @JvmStatic fun setDownloadMirrorMode(value: String?) { downloadMirrorMode = normalizeDownloadMirrorMode(value) }

    private fun resetDefaults() {
        dlcRoot = DEFAULT_DLC_ROOT
        taczRoot = DEFAULT_TACZ_ROOT
        resourcepackRoot = DEFAULT_RESOURCEPACK_ROOT
        datapackRoot = DEFAULT_DATAPACK_ROOT
        enableAllButtonShown = DEFAULT_ENABLE_ALL_SHOWN
        configButtonShown = DEFAULT_CONFIG_BUTTON_SHOWN
        uButtonShown = DEFAULT_U_BUTTON_SHOWN
        updateManagerOn = DEFAULT_UPDATE_MANAGER_ON
        selfUpdateAutoCheckOn = DEFAULT_SELF_UPDATE_AUTO_CHECK_ON
        presetFuncOn = DEFAULT_PRESET_FUNC_ON
        packFuncOn = DEFAULT_PACK_FUNC_ON
        pButtonShown = DEFAULT_P_BUTTON_SHOWN
        showOnFirstRun = DEFAULT_SHOW_ON_FIRST_RUN
        showDependencyReminder = DEFAULT_SHOW_DEPENDENCY_REMINDER
        alarmWaitingTime = DEFAULT_ALARM_WAITING_TIME
        serverPassword = DEFAULT_SERVER_PASSWORD
        apiEnabled = DEFAULT_API_ENABLED
        apiPort = DEFAULT_API_PORT
        apiToken = DEFAULT_API_TOKEN
        serverRconHost = DEFAULT_RCON_HOST
        serverRconPort = DEFAULT_RCON_PORT
        serverRconPassword = DEFAULT_RCON_PASSWORD
        titleAnchor = DEFAULT_TITLE_ANCHOR
        titleOffsetX = DEFAULT_TITLE_OFFSET_X
        titleOffsetY = DEFAULT_TITLE_OFFSET_Y
        titleUpdateAnchor = DEFAULT_TITLE_UPDATE_ANCHOR
        titleUpdateOffsetX = DEFAULT_TITLE_UPDATE_OFFSET_X
        titleUpdateOffsetY = DEFAULT_TITLE_UPDATE_OFFSET_Y
        noticeDisplayMode = DEFAULT_NOTICE_DISPLAY_MODE
        noticeButtonAnchor = DEFAULT_NOTICE_BUTTON_ANCHOR
        noticeButtonOffsetX = DEFAULT_NOTICE_BUTTON_OFFSET_X
        noticeButtonOffsetY = DEFAULT_NOTICE_BUTTON_OFFSET_Y
        noticePanelAnchor = DEFAULT_NOTICE_PANEL_ANCHOR
        noticePanelOffsetX = DEFAULT_NOTICE_PANEL_OFFSET_X
        noticePanelOffsetY = DEFAULT_NOTICE_PANEL_OFFSET_Y
        noticePanelWidth = DEFAULT_NOTICE_PANEL_WIDTH
        noticePanelHeight = DEFAULT_NOTICE_PANEL_HEIGHT
        modpack = DEFAULT_MODPACK
        modpackUrlClient = DEFAULT_MODPACK_URL
        updateGitRepoClient = DEFAULT_UPDATE_GIT_REPO_URL
        modpackAutoUpdate = DEFAULT_MODPACK_AUTO_UPDATE
        modpackForceUpdate = DEFAULT_MODPACK_FORCE_UPDATE
        packUpdateChannel = DEFAULT_PACK_UPDATE_CHANNEL
        maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS
        maxSegmentsPerFile = DEFAULT_MAX_SEGMENTS_PER_FILE
        minSegmentMb = DEFAULT_MIN_SEGMENT_MB
        maxRetryCount = DEFAULT_MAX_RETRY
        connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS
        taskTimeoutSeconds = DEFAULT_TASK_TIMEOUT_SECONDS
        downloadMirrorMode = DEFAULT_DOWNLOAD_MIRROR_MODE
    }

    private fun resetServerDefaults() {
        serverPassword = DEFAULT_SERVER_PASSWORD
        serverRconHost = DEFAULT_RCON_HOST
        serverRconPort = DEFAULT_RCON_PORT
        serverRconPassword = DEFAULT_RCON_PASSWORD
    }

    private fun loadClientFromDisk() {
        resetDefaults()
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        val path = resolveManagerConfigPath(configDir, CONFIG_NAME)
        if (path != null && Files.exists(path)) {
            loadFromJson(path)
        }
    }

    private fun loadServerFromDisk() {
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        resetDefaults()
        val clientPath = resolveManagerConfigPath(configDir, CONFIG_NAME)
        if (clientPath != null && Files.exists(clientPath)) {
            loadFromJson(clientPath)
        }
        val path = resolveManagerConfigPath(configDir, CONFIG_SERVER_NAME)
        if (path != null && Files.exists(path)) {
            loadFromServerJson(path)
        }
    }

    private fun loadFromJson(path: Path) {
        val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return
        readSection(root, "api", buildApiReaders())
        readSection(root, "paths", buildPathReaders())
        readSection(root, "ui", buildUiReaders())
        readSection(root, "downloads", buildDownloadReaders())
        readSection(root, "modpack", buildModpackReaders())
    }

    private fun loadFromServerJson(path: Path) {
        resetServerDefaults()
        val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return
        readSection(root, "server", buildServerReaders())
    }

    private fun ensureClientConfigFile() {
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        val path = resolveManagerConfigPath(configDir, CONFIG_NAME)
        if (path != null && Files.exists(path)) {
            return
        }
        saveClientToDisk()
    }

    private fun ensureServerConfigFile() {
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        val path = resolveManagerConfigPath(configDir, CONFIG_SERVER_NAME)
        if (path != null && Files.exists(path)) {
            return
        }
        saveServerToDisk()
    }

    private fun saveClientToDisk() {
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        val path = managerConfigPath(configDir, CONFIG_NAME)
        try {
            Files.createDirectories(configDir)
        } catch (_: IOException) {
            return
        }
        val root = JsonObject()
        writeShared(root)
        writeJson(root, path)
    }

    private fun saveServerToDisk() {
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        val path = managerConfigPath(configDir, CONFIG_SERVER_NAME)
        try {
            Files.createDirectories(configDir)
        } catch (_: IOException) {
            return
        }
        val root = JsonObject()
        writeServer(root)
        writeJson(root, path)
    }

    private fun writeJson(root: JsonObject, path: Path) {
        DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC)
    }

    private fun writeShared(root: JsonObject) {
        writeApi(root)
        writePaths(root)
        writeUi(root)
        writeDownloads(root)
        writeModpack(root)
    }

    private fun resolveManagerConfigPath(configDir: Path?, baseName: String?): Path? {
        if (configDir == null || baseName.isNullOrBlank()) {
            return null
        }
        migrateManagerLegacyJson(configDir, baseName)
        return managerConfigPath(configDir, baseName)
    }

    private fun managerConfigPath(configDir: Path, baseName: String): Path {
        return configDir.resolve(baseName + DlcDcFormatUtil.DC_EXTENSION)
    }

    private fun migrateManagerLegacyJson(configDir: Path, baseName: String) {
    }

    private fun writePaths(root: JsonObject) {
        val obj = JsonObject()
        obj.addProperty("dlcRoot", dlcRoot)
        obj.addProperty("taczRoot", taczRoot)
        obj.addProperty("resourcepackRoot", resourcepackRoot)
        obj.addProperty("datapackRoot", datapackRoot)
        root.add("paths", obj)
    }

    private fun writeApi(root: JsonObject) {
        val obj = JsonObject()
        obj.addProperty("apiEnabled", apiEnabled)
        obj.addProperty("apiPort", apiPort)
        obj.addProperty("apiToken", apiToken)
        root.add("api", obj)
    }

    private fun writeUi(root: JsonObject) {
        val obj = JsonObject()
        obj.addProperty("isEnableAllButtonShown", enableAllButtonShown)
        obj.addProperty("isConfigButtonShown", configButtonShown)
        obj.addProperty("isUButtomShown", uButtonShown)
        obj.addProperty("isUpdateManagerOn", updateManagerOn)
        obj.addProperty("isSelfUpdateAutoCheckOn", selfUpdateAutoCheckOn)
        obj.addProperty("isPresetFuncOn", presetFuncOn)
        obj.addProperty("isPackFuncOn", packFuncOn)
        obj.addProperty("isPButtomShown", pButtonShown)
        obj.addProperty("showOnFirstRun", showOnFirstRun)
        obj.addProperty("showDependencyReminder", showDependencyReminder)
        obj.addProperty("waitingTime", alarmWaitingTime)
        obj.addProperty("titleAnchor", titleAnchor)
        obj.addProperty("titleOffsetX", titleOffsetX)
        obj.addProperty("titleOffsetY", titleOffsetY)
        obj.addProperty("titleUpdateAnchor", titleUpdateAnchor)
        obj.addProperty("titleUpdateOffsetX", titleUpdateOffsetX)
        obj.addProperty("titleUpdateOffsetY", titleUpdateOffsetY)
        obj.addProperty("noticeDisplayMode", noticeDisplayMode)
        obj.addProperty("noticeButtonAnchor", noticeButtonAnchor)
        obj.addProperty("noticeButtonOffsetX", noticeButtonOffsetX)
        obj.addProperty("noticeButtonOffsetY", noticeButtonOffsetY)
        obj.addProperty("noticePanelAnchor", noticePanelAnchor)
        obj.addProperty("noticePanelOffsetX", noticePanelOffsetX)
        obj.addProperty("noticePanelOffsetY", noticePanelOffsetY)
        obj.addProperty("noticePanelWidth", noticePanelWidth)
        obj.addProperty("noticePanelHeight", noticePanelHeight)
        root.add("ui", obj)
    }

    private fun writeDownloads(root: JsonObject) {
        val obj = JsonObject()
        obj.addProperty("maxConcurrentDownloads", maxConcurrentDownloads)
        obj.addProperty("maxSegmentsPerFile", maxSegmentsPerFile)
        obj.addProperty("minSegmentMB", minSegmentMb)
        obj.addProperty("maxRetryCount", maxRetryCount)
        obj.addProperty("connectTimeoutSeconds", connectTimeoutSeconds)
        obj.addProperty("taskTimeoutSeconds", taskTimeoutSeconds)
        obj.addProperty("downloadMirrorMode", getDownloadMirrorMode())
        root.add("downloads", obj)
    }

    private fun writeServer(root: JsonObject) {
        val obj = JsonObject()
        obj.addProperty("serverPassword", serverPassword)
        if (serverRconHost != DEFAULT_RCON_HOST || serverRconPort != DEFAULT_RCON_PORT || serverRconPassword != DEFAULT_RCON_PASSWORD) {
            obj.addProperty("rconHost", serverRconHost)
            obj.addProperty("rconPort", serverRconPort)
            obj.addProperty("rconPassword", serverRconPassword)
        }
        root.add("server", obj)
    }

    private fun writeModpack(root: JsonObject) {
        val obj = JsonObject()
        obj.addProperty("modpack", modpack)
        obj.addProperty("modpackUrl", modpackUrlClient)
        obj.addProperty("updateGitRepo", updateGitRepoClient)
        obj.addProperty("autoUpdate", modpackAutoUpdate)
        obj.addProperty("forceUpdate", modpackForceUpdate)
        obj.addProperty("packUpdateChannel", packUpdateChannel)
        root.add("modpack", obj)
    }

    private fun readSection(root: JsonObject?, section: String, keyMap: Map<String, (JsonElement) -> Unit>) {
        if (root == null || keyMap.isEmpty()) {
            return
        }
        val obj = DlcDcConfigIO.getObjectIgnoreCase(root, section) ?: return
        for ((key, consumer) in keyMap) {
            val value = DlcDcConfigIO.getIgnoreCase(obj, key)
            if (value != null) {
                consumer(value)
            }
        }
    }

    private fun buildPathReaders(): Map<String, (JsonElement) -> Unit> = mapOf(
        "dlcRoot" to { value -> dlcRoot = asString(value, DEFAULT_DLC_ROOT) },
        "taczRoot" to { value -> taczRoot = asString(value, DEFAULT_TACZ_ROOT) },
        "resourcepackRoot" to { value -> resourcepackRoot = asString(value, DEFAULT_RESOURCEPACK_ROOT) },
        "datapackRoot" to { value -> datapackRoot = asString(value, DEFAULT_DATAPACK_ROOT) },
    )

    private fun buildApiReaders(): Map<String, (JsonElement) -> Unit> = mapOf(
        "apiEnabled" to { value -> apiEnabled = asBoolean(value, DEFAULT_API_ENABLED) },
        "apiPort" to { value -> apiPort = clampInt(asInt(value, DEFAULT_API_PORT), 0, 65535, DEFAULT_API_PORT) },
        "apiToken" to { value -> apiToken = asString(value, DEFAULT_API_TOKEN) },
    )

    private fun buildUiReaders(): Map<String, (JsonElement) -> Unit> = mapOf(
        "isEnableAllButtonShown" to { value -> enableAllButtonShown = asBoolean(value, DEFAULT_ENABLE_ALL_SHOWN) },
        "isConfigButtonShown" to { value -> configButtonShown = asBoolean(value, DEFAULT_CONFIG_BUTTON_SHOWN) },
        "isUButtomShown" to { value -> uButtonShown = asBoolean(value, DEFAULT_U_BUTTON_SHOWN) },
        "isUpdateManagerOn" to { value -> updateManagerOn = asBoolean(value, DEFAULT_UPDATE_MANAGER_ON) },
        "isSelfUpdateAutoCheckOn" to { value -> selfUpdateAutoCheckOn = asBoolean(value, DEFAULT_SELF_UPDATE_AUTO_CHECK_ON) },
        "isPresetFuncOn" to { value -> presetFuncOn = asBoolean(value, DEFAULT_PRESET_FUNC_ON) },
        "isPackFuncOn" to { value -> packFuncOn = asBoolean(value, DEFAULT_PACK_FUNC_ON) },
        "isPButtomShown" to { value -> pButtonShown = asBoolean(value, DEFAULT_P_BUTTON_SHOWN) },
        "ShowOnFirstRun" to { value -> showOnFirstRun = asBoolean(value, DEFAULT_SHOW_ON_FIRST_RUN) },
        "showOnFirstRun" to { value -> showOnFirstRun = asBoolean(value, DEFAULT_SHOW_ON_FIRST_RUN) },
        "showDependencyReminder" to { value -> showDependencyReminder = asBoolean(value, DEFAULT_SHOW_DEPENDENCY_REMINDER) },
        "WaitingTime" to { value -> alarmWaitingTime = clampInt(asInt(value, DEFAULT_ALARM_WAITING_TIME), 0, 10, DEFAULT_ALARM_WAITING_TIME) },
        "waitingTime" to { value -> alarmWaitingTime = clampInt(asInt(value, DEFAULT_ALARM_WAITING_TIME), 0, 10, DEFAULT_ALARM_WAITING_TIME) },
        "titleAnchor" to { value -> titleAnchor = asString(value, DEFAULT_TITLE_ANCHOR) },
        "titleOffsetX" to { value -> titleOffsetX = asInt(value, DEFAULT_TITLE_OFFSET_X) },
        "titleOffsetY" to { value -> titleOffsetY = asInt(value, DEFAULT_TITLE_OFFSET_Y) },
        "titleUpdateAnchor" to { value -> titleUpdateAnchor = asString(value, DEFAULT_TITLE_UPDATE_ANCHOR) },
        "titleUpdateOffsetX" to { value -> titleUpdateOffsetX = asInt(value, DEFAULT_TITLE_UPDATE_OFFSET_X) },
        "titleUpdateOffsetY" to { value -> titleUpdateOffsetY = asInt(value, DEFAULT_TITLE_UPDATE_OFFSET_Y) },
        "noticeDisplayMode" to { value -> noticeDisplayMode = normalizeNoticeDisplayMode(asString(value, DEFAULT_NOTICE_DISPLAY_MODE)) },
        "noticeButtonAnchor" to { value -> noticeButtonAnchor = asString(value, DEFAULT_NOTICE_BUTTON_ANCHOR) },
        "noticeButtonOffsetX" to { value -> noticeButtonOffsetX = asInt(value, DEFAULT_NOTICE_BUTTON_OFFSET_X).coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) },
        "noticeButtonOffsetY" to { value -> noticeButtonOffsetY = asInt(value, DEFAULT_NOTICE_BUTTON_OFFSET_Y).coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) },
        "noticePanelAnchor" to { value -> noticePanelAnchor = asString(value, DEFAULT_NOTICE_PANEL_ANCHOR) },
        "noticePanelOffsetX" to { value -> noticePanelOffsetX = asInt(value, DEFAULT_NOTICE_PANEL_OFFSET_X).coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) },
        "noticePanelOffsetY" to { value -> noticePanelOffsetY = asInt(value, DEFAULT_NOTICE_PANEL_OFFSET_Y).coerceIn(-MAX_NOTICE_OFFSET, MAX_NOTICE_OFFSET) },
        "noticePanelWidth" to { value -> noticePanelWidth = asInt(value, DEFAULT_NOTICE_PANEL_WIDTH).coerceIn(0, MAX_NOTICE_PANEL_SIZE) },
        "noticePanelHeight" to { value -> noticePanelHeight = asInt(value, DEFAULT_NOTICE_PANEL_HEIGHT).coerceIn(0, MAX_NOTICE_PANEL_SIZE) },
        "modpackUrl" to { value -> modpackUrlClient = asString(value, DEFAULT_MODPACK_URL) },
        "updateGithubRepo" to { value -> updateGitRepoClient = asString(value, DEFAULT_UPDATE_GIT_REPO_URL) },
    )

    private fun buildServerReaders(): Map<String, (JsonElement) -> Unit> = mapOf(
        "serverPassword" to { value -> serverPassword = asString(value, DEFAULT_SERVER_PASSWORD) },
        "rconHost" to { value -> serverRconHost = asString(value, DEFAULT_RCON_HOST) },
        "rconPort" to { value -> serverRconPort = clampInt(asInt(value, DEFAULT_RCON_PORT), 1, 65535, DEFAULT_RCON_PORT) },
        "rconPassword" to { value -> serverRconPassword = asString(value, DEFAULT_RCON_PASSWORD) },
    )

    private fun buildModpackReaders(): Map<String, (JsonElement) -> Unit> = mapOf(
        "modpack" to { value -> modpack = asString(value, DEFAULT_MODPACK) },
        "modpackUrl" to { value -> modpackUrlClient = asString(value, DEFAULT_MODPACK_URL) },
        "updateGithubRepo" to { value -> updateGitRepoClient = asString(value, DEFAULT_UPDATE_GIT_REPO_URL) },
        "updateGitRepo" to { value -> updateGitRepoClient = asString(value, DEFAULT_UPDATE_GIT_REPO_URL) },
        "autoUpdate" to { value -> modpackAutoUpdate = asBoolean(value, DEFAULT_MODPACK_AUTO_UPDATE) },
        "forceUpdate" to { value -> modpackForceUpdate = asBoolean(value, DEFAULT_MODPACK_FORCE_UPDATE) },
        "packUpdateChannel" to { value -> packUpdateChannel = normalizePackUpdateChannel(asString(value, DEFAULT_PACK_UPDATE_CHANNEL)) },
    )

    private fun buildDownloadReaders(): Map<String, (JsonElement) -> Unit> = mapOf(
        "maxConcurrentDownloads" to { value -> maxConcurrentDownloads = maxOf(1, asInt(value, DEFAULT_MAX_CONCURRENT_DOWNLOADS)) },
        "maxSegmentsPerFile" to { value -> maxSegmentsPerFile = maxOf(1, asInt(value, DEFAULT_MAX_SEGMENTS_PER_FILE)) },
        "minSegmentMB" to { value -> minSegmentMb = maxOf(1, asInt(value, DEFAULT_MIN_SEGMENT_MB)) },
        "maxRetryCount" to { value -> maxRetryCount = maxOf(1, asInt(value, DEFAULT_MAX_RETRY)) },
        "connectTimeoutSeconds" to { value -> connectTimeoutSeconds = maxOf(1, asInt(value, DEFAULT_CONNECT_TIMEOUT_SECONDS)) },
        "taskTimeoutSeconds" to { value -> taskTimeoutSeconds = maxOf(1, asInt(value, DEFAULT_TASK_TIMEOUT_SECONDS)) },
        "downloadMirrorMode" to { value -> downloadMirrorMode = normalizeDownloadMirrorMode(asString(value, DEFAULT_DOWNLOAD_MIRROR_MODE)) },
    )

    @JvmStatic
    fun normalizeDownloadMirrorMode(value: String?): String {
        return when (value?.trim()?.lowercase(Locale.ROOT)?.replace('-', '_')) {
            DOWNLOAD_MIRROR_OFFICIAL_ONLY -> DOWNLOAD_MIRROR_OFFICIAL_ONLY
            DOWNLOAD_MIRROR_MIRROR_ONLY -> DOWNLOAD_MIRROR_MIRROR_ONLY
            DOWNLOAD_MIRROR_OFFICIAL_FIRST -> DOWNLOAD_MIRROR_OFFICIAL_FIRST
            DOWNLOAD_MIRROR_MIRROR_FIRST -> DOWNLOAD_MIRROR_MIRROR_FIRST
            else -> DEFAULT_DOWNLOAD_MIRROR_MODE
        }
    }


    private fun stringOrDefault(value: String?, fallback: String): String {
        return if (value.isNullOrBlank()) fallback else value
    }

    private fun asInt(element: JsonElement?, fallback: Int): Int {
        if (element == null || element.isJsonNull) {
            return fallback
        }
        return try {
            if (element.isJsonPrimitive) element.asInt else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun asBoolean(element: JsonElement?, fallback: Boolean): Boolean {
        if (element == null || element.isJsonNull) {
            return fallback
        }
        return try {
            if (element.isJsonPrimitive) element.asBoolean else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun asString(element: JsonElement?, fallback: String): String {
        if (element == null || element.isJsonNull) {
            return fallback
        }
        return try {
            if (element.isJsonPrimitive) element.asString ?: fallback else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun clampInt(value: Int, min: Int, max: Int, fallback: Int): Int {
        return if (value < min || value > max) fallback else value
    }

    private fun normalizeNoticeDisplayMode(value: String?): String {
        if (value.isNullOrBlank()) {
            return DEFAULT_NOTICE_DISPLAY_MODE
        }
        return when (value.trim().lowercase(Locale.ROOT).replace(' ', '_').replace('-', '_')) {
            "manager", "managerbutton", "manager_button", "dlc_manager_button" -> "manager_button"
            "mainmenubutton", "main_menu_button", "title_button" -> "main_menu_button"
            "mainmenupanel", "main_menu_panel", "title_panel" -> "main_menu_panel"
            else -> DEFAULT_NOTICE_DISPLAY_MODE
        }
    }

    private fun resolvePath(value: String?, fallback: String): Path {
        val raw = if (value.isNullOrBlank()) fallback else value
        var path = DlcPaths.parsePathLenient(raw)
        if (path == null) {
            path = DlcPaths.parsePathLenient(fallback)
        }
        if (path == null) {
            return DlcPlatformServices.gameDir().toAbsolutePath().normalize()
        }
        if (!path.isAbsolute) {
            path = DlcPlatformServices.gameDir().resolve(path)
        }
        return path.normalize()
    }

    private fun resolvePaths(value: String?, fallback: String): List<Path> {
        val raw = if (value.isNullOrBlank()) fallback else value
        val paths = ArrayList<Path>()
        for (part in raw.split(",")) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                paths.add(resolvePath(trimmed, fallback))
            }
        }
        if (paths.isEmpty()) {
            paths.add(resolvePath(fallback, fallback))
        }
        return paths
    }

    private fun migrateKnownConfigsToDc() {
        val configDir = DlcPlatformServices.configDir().resolve(CONFIG_DIR)
        migrateLocalConfigDirToDc(configDir)
        for (dlcRootPath in getDlcRoots()) {
            migrateDlcConfigTreeToDc(dlcRootPath)
        }
    }

    private fun migrateLocalConfigDirToDc(configDir: Path?) {
        if (configDir == null || !Files.isDirectory(configDir)) {
            return
        }
        try {
            Files.walk(configDir).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { path -> path.fileName != null }
                    .filter { path -> DlcDcFormatUtil.isDcName(path.fileName.toString()) }
                    .filter { path -> !isManagerConfigFile(path) }
                    .forEach { }
            }
        } catch (_: IOException) {
        }
    }

    private fun isManagerConfigFile(path: Path?): Boolean {
        if (path == null || path.fileName == null) {
            return false
        }
        val base = DlcDcFormatUtil.stripConfigExtension(path.fileName.toString())
        return CONFIG_NAME.equals(base, ignoreCase = true) || CONFIG_SERVER_NAME.equals(base, ignoreCase = true)
    }

    private fun migrateDlcConfigTreeToDc(dlcRootPath: Path?) {
        if (dlcRootPath == null || !Files.isDirectory(dlcRootPath)) {
            return
        }
        val configDirs = listOf("info", "remote", "required", "preset", "tmp", "update")
        for (dirName in configDirs) {
            val target = dlcRootPath.resolve(dirName)
            when (dirName) {
                "info" -> migrateInfoConfigsToDc(target)
                "remote" -> migrateRemoteConfigsToDc(target)
                else -> migrateTreeConfigsToDc(target)
            }
        }
    }

    private fun migrateTreeConfigsToDc(root: Path?) {
        if (root == null || !Files.isDirectory(root)) {
            return
        }
        try {
            Files.walk(root).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { path -> path.fileName != null }
                    .filter { path -> DlcDcFormatUtil.isDcName(path.fileName.toString()) }
                    .forEach { }
            }
        } catch (_: IOException) {
        }
    }

    private fun migrateInfoConfigsToDc(infoRoot: Path?) {
        if (infoRoot == null || !Files.isDirectory(infoRoot)) {
            return
        }
        try {
            Files.walk(infoRoot).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { path -> path.fileName != null }
                    .filter { path -> DlcDcFormatUtil.isDcName(path.fileName.toString()) }
                    .forEach { path ->
                        val baseName = DlcDcFormatUtil.stripConfigExtension(path.fileName.toString())
                        if (!baseName.equals("version", ignoreCase = true) && !baseName.equals("notice", ignoreCase = true)) {
                            migrateInfoEntryConfig(path)
                        }
                    }
            }
        } catch (_: IOException) {
        }
    }

    private fun migrateInfoEntryConfig(path: Path?) {
        if (path == null || !Files.exists(path) || path.fileName == null) {
            return
        }
        val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
        val fallbackName = DlcDcFormatUtil.stripConfigExtension(path.fileName.toString())
        val normalized = normalizeInfoEntry(obj, fallbackName)
        DlcDcConfigIO.write(path, normalized, DlcDcConfigIO.Layout.DLC_STRUCTURED)
    }

    private fun normalizeInfoEntry(input: JsonObject?, fallbackName: String?): JsonObject {
        val out = JsonObject()
        if (input != null) {
            for ((key, value) in input.entrySet()) {
                if (key.isNullOrBlank()) {
                    continue
                }
                out.add(key, value)
            }
        }
        var fileName = DlcDcConfigIO.getStringIgnoreCase(out, "file_name")
        if (fileName.isNullOrBlank()) {
            fileName = fallbackName ?: ""
            DlcDcConfigIO.removeIgnoreCase(out, "file_name")
            out.addProperty("file_name", fileName)
        }
        var identifier = DlcDcConfigIO.getStringIgnoreCase(out, "identifier")
        if (identifier.isNullOrBlank()) {
            identifier = fileName
            DlcDcConfigIO.removeIgnoreCase(out, "identifier")
            out.addProperty("identifier", identifier)
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "type")) out.addProperty("type", "others")
        if (!DlcDcConfigIO.hasIgnoreCase(out, "isHidden")) out.addProperty("isHidden", false)
        if (!DlcDcConfigIO.hasIgnoreCase(out, "AllowEnableByAll")) out.addProperty("AllowEnableByAll", true)
        if (!DlcDcConfigIO.hasIgnoreCase(out, "dependencies")) out.addProperty("dependencies", "")
        if (!DlcDcConfigIO.hasIgnoreCase(out, "conflicts")) out.addProperty("conflicts", "")
        if (!DlcDcConfigIO.hasIgnoreCase(out, "alarm")) out.addProperty("alarm", false)
        normalizeLocalizedField(out, "DisplayName", fileName)
        normalizeLocalizedField(out, "tip", null)
        return out
    }

    private fun normalizeLocalizedField(obj: JsonObject, key: String, defaultEnUs: String?) {
        val localized = JsonObject()
        val fromObject = DlcDcConfigIO.getObjectIgnoreCase(obj, key)
        if (fromObject != null) {
            for ((langKey, value) in fromObject.entrySet()) {
                if (value == null || !value.isJsonPrimitive) continue
                val lang = DlcDcConfigIO.normalizeLangKey(langKey)
                if (!lang.isNullOrBlank()) {
                    localized.addProperty(lang, value.asString)
                }
            }
        } else {
            val single = DlcDcConfigIO.getStringIgnoreCase(obj, key)
            if (!single.isNullOrBlank()) {
                localized.addProperty("en_us", single)
            }
        }
        if (localized.entrySet().isEmpty() && !defaultEnUs.isNullOrBlank()) {
            localized.addProperty("en_us", defaultEnUs)
        }
        DlcDcConfigIO.removeIgnoreCase(obj, key)
        obj.add(key, localized)
    }

    private fun migrateRemoteConfigsToDc(remoteRoot: Path?) {
        if (remoteRoot == null || !Files.isDirectory(remoteRoot)) {
            return
        }
        val redirectRoot = remoteRoot.resolve("redirect").normalize()
        try {
            Files.walk(remoteRoot).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { path -> path.fileName != null }
                    .filter { path -> DlcDcFormatUtil.isDcName(path.fileName.toString()) }
                    .forEach { path ->
                        if (!path.normalize().startsWith(redirectRoot)) {
                            migrateRemoteEntryConfig(path)
                        }
                    }
            }
        } catch (_: IOException) {
        }
    }

    private fun migrateRemoteEntryConfig(path: Path?) {
        if (path == null || !Files.exists(path) || path.fileName == null) {
            return
        }
        val lower = path.fileName.toString().lowercase(Locale.ROOT)
        if (lower.endsWith(DlcDcFormatUtil.DC_EXTENSION)) {
            val obj = readRemoteDcObject(path) ?: return
            DlcDcFormatUtil.write(path, obj)
        }
    }

    private fun readRemoteDcObject(path: Path?): JsonObject? {
        if (path == null || !Files.exists(path)) {
            return null
        }
        return DlcDcFormatUtil.read(path)
    }
}
