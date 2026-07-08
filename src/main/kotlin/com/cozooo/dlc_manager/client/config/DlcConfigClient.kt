package com.cozooo.dlc_manager.client.config

import com.cozooo.dlc_manager.core.DlcConfig
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.config.options.ConfigString
import fi.dy.masa.malilib.util.StringUtils

object DlcConfigClient {
    private val DLC_ROOT = DlcConfigString("dlcRoot", DlcConfig.DEFAULT_DLC_ROOT, "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot")
    private val TACZ_ROOT = DlcConfigString("taczRoot", DlcConfig.DEFAULT_TACZ_DLC_ROOT, "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot")
    private val RESOURCEPACK_ROOT = DlcConfigString("resourcepackRoot", DlcConfig.DEFAULT_RESOURCEPACK_ROOT, "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot")
    private val DATAPACK_ROOT = DlcConfigString("datapackRoot", DlcConfig.DEFAULT_DATAPACK_ROOT, "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot")

    private val ENABLE_ALL_BUTTON = DlcConfigBoolean("isEnableAllButtonShown", DlcConfig.DEFAULT_ENABLE_ALL_SHOWN, "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown")
    private val CONFIG_BUTTON = DlcConfigBoolean("isConfigButtonShown", DlcConfig.DEFAULT_CONFIG_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isConfigButtonShown", "dlc_manager.config.ui.isConfigButtonShown")
    private val U_BUTTON = DlcConfigBoolean("isUButtomShown", DlcConfig.DEFAULT_U_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isUButtomShown", "dlc_manager.config.ui.isUButtomShown")
    private val UPDATE_MANAGER_ON = DlcConfigBoolean("isUpdateManagerOn", DlcConfig.DEFAULT_UPDATE_MANAGER_ON, "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn")
    private val SELF_UPDATE_AUTO_CHECK_ON = DlcConfigBoolean("isSelfUpdateAutoCheckOn", DlcConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON, "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn")
    private val PRESET_FUNC_ON = DlcConfigBoolean("isPresetFuncOn", DlcConfig.DEFAULT_PRESET_FUNC_ON, "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn")
    private val PACK_FUNC_ON = DlcConfigBoolean("isPackFuncOn", DlcConfig.DEFAULT_PACK_FUNC_ON, "dlc_manager.config.comment.ui.isPackFuncOn", "dlc_manager.config.ui.isPackFuncOn")
    private val P_BUTTON = DlcConfigBoolean("isPButtomShown", DlcConfig.DEFAULT_P_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isPButtomShown", "dlc_manager.config.ui.isPButtomShown")
    private val SHOW_ON_FIRST_RUN = DlcConfigBoolean("ShowOnFirstRun", DlcConfig.DEFAULT_SHOW_ON_FIRST_RUN, "dlc_manager.config.comment.ui.ShowOnFirstRun", "dlc_manager.config.ui.ShowOnFirstRun")
    private val ALARM_WAITING_TIME = DlcConfigInteger("WaitingTime", DlcConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10, "dlc_manager.config.comment.ui.WaitingTime", "dlc_manager.config.ui.WaitingTime")
    private val SERVER_SUPPORT_CLIENT = DlcConfigBoolean("serverSupport", DlcConfig.DEFAULT_SERVER_SUPPORT, "dlc_manager.config.comment.ui.serverSupport", "dlc_manager.config.ui.serverSupport")
    private val API_ENABLED = DlcConfigBoolean("apiEnabled", DlcConfig.DEFAULT_API_ENABLED, "dlc_manager.config.comment.ui.apiEnabled", "dlc_manager.config.ui.apiEnabled")
    private val API_PORT = DlcConfigInteger("apiPort", DlcConfig.DEFAULT_API_PORT, 0, 65535, "dlc_manager.config.comment.ui.apiPort", "dlc_manager.config.ui.apiPort")
    private val API_TOKEN = DlcConfigString("apiToken", DlcConfig.DEFAULT_API_TOKEN, "dlc_manager.config.comment.ui.apiToken", "dlc_manager.config.ui.apiToken")
    private val NOTICE_DISPLAY_MODE = DlcConfigString("noticeDisplayMode", DlcConfig.DEFAULT_NOTICE_DISPLAY_MODE, "dlc_manager.config.comment.ui.noticeDisplayMode", "dlc_manager.config.ui.noticeDisplayMode")
    private val NOTICE_BUTTON_ANCHOR = DlcConfigString("noticeButtonAnchor", DlcConfig.DEFAULT_NOTICE_BUTTON_ANCHOR, "dlc_manager.config.comment.ui.noticeButtonAnchor", "dlc_manager.config.ui.noticeButtonAnchor")
    private val NOTICE_BUTTON_OFFSET_X = DlcConfigInteger("noticeButtonOffsetX", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticeButtonOffsetX", "dlc_manager.config.ui.noticeButtonOffsetX")
    private val NOTICE_BUTTON_OFFSET_Y = DlcConfigInteger("noticeButtonOffsetY", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticeButtonOffsetY", "dlc_manager.config.ui.noticeButtonOffsetY")
    private val NOTICE_PANEL_ANCHOR = DlcConfigString("noticePanelAnchor", DlcConfig.DEFAULT_NOTICE_PANEL_ANCHOR, "dlc_manager.config.comment.ui.noticePanelAnchor", "dlc_manager.config.ui.noticePanelAnchor")
    private val NOTICE_PANEL_OFFSET_X = DlcConfigInteger("noticePanelOffsetX", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticePanelOffsetX", "dlc_manager.config.ui.noticePanelOffsetX")
    private val NOTICE_PANEL_OFFSET_Y = DlcConfigInteger("noticePanelOffsetY", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticePanelOffsetY", "dlc_manager.config.ui.noticePanelOffsetY")
    private val NOTICE_PANEL_WIDTH = DlcConfigInteger("noticePanelWidth", DlcConfig.DEFAULT_NOTICE_PANEL_WIDTH, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE, "dlc_manager.config.comment.ui.noticePanelWidth", "dlc_manager.config.ui.noticePanelWidth")
    private val NOTICE_PANEL_HEIGHT = DlcConfigInteger("noticePanelHeight", DlcConfig.DEFAULT_NOTICE_PANEL_HEIGHT, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE, "dlc_manager.config.comment.ui.noticePanelHeight", "dlc_manager.config.ui.noticePanelHeight")
    private val TITLE_ANCHOR = DlcConfigString("titleAnchor", DlcConfig.DEFAULT_TITLE_ANCHOR, "dlc_manager.config.comment.ui.titleAnchor", "dlc_manager.config.ui.titleAnchor")
    private val TITLE_OFFSET_X = DlcConfigInteger("titleOffsetX", DlcConfig.DEFAULT_TITLE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetX", "dlc_manager.config.ui.titleOffsetX")
    private val TITLE_OFFSET_Y = DlcConfigInteger("titleOffsetY", DlcConfig.DEFAULT_TITLE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetY", "dlc_manager.config.ui.titleOffsetY")
    private val TITLE_UPDATE_ANCHOR = DlcConfigString("titleUpdateAnchor", DlcConfig.DEFAULT_TITLE_UPDATE_ANCHOR, "dlc_manager.config.comment.ui.titleUpdateAnchor", "dlc_manager.config.ui.titleUpdateAnchor")
    private val TITLE_UPDATE_OFFSET_X = DlcConfigInteger("titleUpdateOffsetX", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleUpdateOffsetX", "dlc_manager.config.ui.titleUpdateOffsetX")
    private val TITLE_UPDATE_OFFSET_Y = DlcConfigInteger("titleUpdateOffsetY", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleUpdateOffsetY", "dlc_manager.config.ui.titleUpdateOffsetY")
    private val MODPACK = DlcConfigString("modpack", DlcConfig.DEFAULT_MODPACK, "dlc_manager.config.comment.modpack.modpack", "dlc_manager.config.modpack.modpack")
    private val MODPACK_URL = DlcConfigString("modpackUrl", DlcConfig.DEFAULT_MODPACK_URL, "dlc_manager.config.comment.modpack.modpackUrl", "dlc_manager.config.modpack.modpackUrl")
    private val UPDATE_GIT_REPO = DlcConfigString("updateGitRepo", DlcConfig.DEFAULT_UPDATE_GIT_REPO_URL, "dlc_manager.config.comment.modpack.updateGitRepo", "dlc_manager.config.modpack.updateGitRepo")

    private val MAX_CONCURRENT_DOWNLOADS = DlcConfigInteger("maxConcurrentDownloads", DlcConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64, "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads")
    private val MAX_SEGMENTS_PER_FILE = DlcConfigInteger("maxSegmentsPerFile", DlcConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64, "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile")
    private val MIN_SEGMENT_MB = DlcConfigInteger("minSegmentMB", DlcConfig.DEFAULT_MIN_SEGMENT_MB, 1, Int.MAX_VALUE, "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB")
    private val MAX_RETRY = DlcConfigInteger("maxRetryCount", DlcConfig.DEFAULT_MAX_RETRY, 1, 100, "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount")
    private val CONNECT_TIMEOUT_SECONDS = DlcConfigInteger("connectTimeoutSeconds", DlcConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds")
    private val TASK_TIMEOUT_SECONDS = DlcConfigInteger("taskTimeoutSeconds", DlcConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds")
    private val DOWNLOAD_MIRROR_MODE = DlcConfigString("downloadMirrorMode", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_MODE, "dlc_manager.config.comment.download.downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode")
    private val DOWNLOAD_MIRROR_PROBE_COUNT = DlcConfigInteger("downloadMirrorProbeCount", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT, 1, 10, "dlc_manager.config.comment.download.downloadMirrorProbeCount", "dlc_manager.config.download.downloadMirrorProbeCount")
    private val DOWNLOAD_MIRROR_PROBE_STRATEGY = DlcConfigString("downloadMirrorProbeStrategy", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY, "dlc_manager.config.comment.download.downloadMirrorProbeStrategy", "dlc_manager.config.download.downloadMirrorProbeStrategy")

    private val PATH_CONFIGS: List<IConfigBase> = listOf(DLC_ROOT, TACZ_ROOT, RESOURCEPACK_ROOT, DATAPACK_ROOT)

    private val UI_CONFIGS: List<IConfigBase> = listOf(
        ENABLE_ALL_BUTTON,
        CONFIG_BUTTON,
        U_BUTTON,
        UPDATE_MANAGER_ON,
        SELF_UPDATE_AUTO_CHECK_ON,
        PRESET_FUNC_ON,
        PACK_FUNC_ON,
        P_BUTTON,
        SHOW_ON_FIRST_RUN,
        ALARM_WAITING_TIME,
        NOTICE_DISPLAY_MODE,
        NOTICE_BUTTON_ANCHOR,
        NOTICE_BUTTON_OFFSET_X,
        NOTICE_BUTTON_OFFSET_Y,
        NOTICE_PANEL_ANCHOR,
        NOTICE_PANEL_OFFSET_X,
        NOTICE_PANEL_OFFSET_Y,
        NOTICE_PANEL_WIDTH,
        NOTICE_PANEL_HEIGHT,
        TITLE_ANCHOR,
        TITLE_OFFSET_X,
        TITLE_OFFSET_Y,
        TITLE_UPDATE_ANCHOR,
        TITLE_UPDATE_OFFSET_X,
        TITLE_UPDATE_OFFSET_Y
    )

    private val MODPACK_CONFIGS: List<IConfigBase> = listOf(
        MODPACK,
        MODPACK_URL,
        UPDATE_GIT_REPO
    )

    private val SERVER_CONFIGS: List<IConfigBase> = listOf(
        SERVER_SUPPORT_CLIENT,
        API_ENABLED,
        API_PORT,
        API_TOKEN
    )

    private val DOWNLOAD_CONFIGS: List<IConfigBase> = listOf(
        MAX_CONCURRENT_DOWNLOADS,
        MAX_SEGMENTS_PER_FILE,
        MIN_SEGMENT_MB,
        MAX_RETRY,
        CONNECT_TIMEOUT_SECONDS,
        TASK_TIMEOUT_SECONDS,
        DOWNLOAD_MIRROR_MODE,
        DOWNLOAD_MIRROR_PROBE_COUNT,
        DOWNLOAD_MIRROR_PROBE_STRATEGY
    )

    @JvmStatic
    fun getPathConfigs(): List<IConfigBase> = PATH_CONFIGS

    @JvmStatic
    fun getUiConfigs(): List<IConfigBase> = UI_CONFIGS

    @JvmStatic
    fun getModpackConfigs(): List<IConfigBase> = MODPACK_CONFIGS

    @JvmStatic
    fun getServerConfigs(): List<IConfigBase> = SERVER_CONFIGS

    @JvmStatic
    fun getDownloadConfigs(): List<IConfigBase> = DOWNLOAD_CONFIGS

    @JvmStatic
    fun syncFromConfig() {
        DLC_ROOT.setValueFromString(DlcConfig.getDlcRootRaw())
        TACZ_ROOT.setValueFromString(DlcConfig.getTaczRootRaw())
        RESOURCEPACK_ROOT.setValueFromString(DlcConfig.getResourcepackRootRaw())
        DATAPACK_ROOT.setValueFromString(DlcConfig.getDatapackRootRaw())

        ENABLE_ALL_BUTTON.booleanValue = DlcConfig.isEnableAllButtonShown()
        CONFIG_BUTTON.booleanValue = DlcConfig.isConfigButtonShown()
        U_BUTTON.booleanValue = DlcConfig.isUButtomShown()
        UPDATE_MANAGER_ON.booleanValue = DlcConfig.isUpdateManagerOn()
        SELF_UPDATE_AUTO_CHECK_ON.booleanValue = DlcConfig.isSelfUpdateAutoCheckOn()
        PRESET_FUNC_ON.booleanValue = DlcConfig.isPresetFuncOn()
        PACK_FUNC_ON.booleanValue = DlcConfig.isPackFuncOn()
        P_BUTTON.booleanValue = DlcConfig.isPButtomShown()
        SHOW_ON_FIRST_RUN.booleanValue = DlcConfig.isShowOnFirstRun()
        ALARM_WAITING_TIME.integerValue = DlcConfig.getAlarmWaitingTime()
        SERVER_SUPPORT_CLIENT.booleanValue = DlcConfig.isServerSupportOn()
        MODPACK.setValueFromString(DlcConfig.getModpack())
        MODPACK_URL.setValueFromString(DlcConfig.getModpackUrl())
        UPDATE_GIT_REPO.setValueFromString(DlcConfig.getUpdateGitRepoUrl())
        API_ENABLED.booleanValue = DlcConfig.isApiEnabled()
        API_PORT.integerValue = DlcConfig.getApiPort()
        API_TOKEN.setValueFromString(DlcConfig.getApiToken())
        NOTICE_DISPLAY_MODE.setValueFromString(DlcConfig.getNoticeDisplayMode())
        NOTICE_BUTTON_ANCHOR.setValueFromString(DlcConfig.getNoticeButtonAnchor())
        NOTICE_BUTTON_OFFSET_X.integerValue = DlcConfig.getNoticeButtonOffsetX()
        NOTICE_BUTTON_OFFSET_Y.integerValue = DlcConfig.getNoticeButtonOffsetY()
        NOTICE_PANEL_ANCHOR.setValueFromString(DlcConfig.getNoticePanelAnchor())
        NOTICE_PANEL_OFFSET_X.integerValue = DlcConfig.getNoticePanelOffsetX()
        NOTICE_PANEL_OFFSET_Y.integerValue = DlcConfig.getNoticePanelOffsetY()
        NOTICE_PANEL_WIDTH.integerValue = DlcConfig.getNoticePanelWidth()
        NOTICE_PANEL_HEIGHT.integerValue = DlcConfig.getNoticePanelHeight()
        TITLE_ANCHOR.setValueFromString(DlcConfig.getTitleAnchor())
        TITLE_OFFSET_X.integerValue = DlcConfig.getTitleOffsetX()
        TITLE_OFFSET_Y.integerValue = DlcConfig.getTitleOffsetY()
        TITLE_UPDATE_ANCHOR.setValueFromString(DlcConfig.getTitleUpdateAnchor())
        TITLE_UPDATE_OFFSET_X.integerValue = DlcConfig.getTitleUpdateOffsetX()
        TITLE_UPDATE_OFFSET_Y.integerValue = DlcConfig.getTitleUpdateOffsetY()

        MAX_CONCURRENT_DOWNLOADS.integerValue = DlcConfig.getMaxConcurrentDownloads()
        MAX_SEGMENTS_PER_FILE.integerValue = DlcConfig.getMaxSegmentsPerFile()
        MIN_SEGMENT_MB.integerValue = toMb(DlcConfig.getMinSegmentBytes())
        MAX_RETRY.integerValue = DlcConfig.getMaxRetryCount()
        CONNECT_TIMEOUT_SECONDS.integerValue = kotlin.math.max(1, DlcConfig.getConnectTimeoutMs() / 1000)
        TASK_TIMEOUT_SECONDS.integerValue = kotlin.math.max(1, DlcConfig.getTaskTimeoutMs() / 1000)
        DOWNLOAD_MIRROR_MODE.setValueFromString(DlcConfig.getDownloadMirrorMode())
        DOWNLOAD_MIRROR_PROBE_COUNT.integerValue = DlcConfig.getDownloadMirrorProbeCount()
        DOWNLOAD_MIRROR_PROBE_STRATEGY.setValueFromString(DlcConfig.getDownloadMirrorProbeStrategy())
    }

    @JvmStatic
    fun applyToConfig() {
        DlcConfig.setDlcRoot(DLC_ROOT.stringValue)
        DlcConfig.setTaczRoot(TACZ_ROOT.stringValue)
        DlcConfig.setResourcepackRoot(RESOURCEPACK_ROOT.stringValue)
        DlcConfig.setDatapackRoot(DATAPACK_ROOT.stringValue)

        DlcConfig.setEnableAllButtonShown(ENABLE_ALL_BUTTON.booleanValue)
        DlcConfig.setConfigButtonShown(CONFIG_BUTTON.booleanValue)
        DlcConfig.setUButtomShown(U_BUTTON.booleanValue)
        DlcConfig.setUpdateManagerOn(UPDATE_MANAGER_ON.booleanValue)
        DlcConfig.setSelfUpdateAutoCheckOn(SELF_UPDATE_AUTO_CHECK_ON.booleanValue)
        DlcConfig.setPresetFuncOn(PRESET_FUNC_ON.booleanValue)
        DlcConfig.setPackFuncOn(PACK_FUNC_ON.booleanValue)
        DlcConfig.setPButtomShown(P_BUTTON.booleanValue)
        DlcConfig.setShowOnFirstRun(SHOW_ON_FIRST_RUN.booleanValue)
        DlcConfig.setAlarmWaitingTime(ALARM_WAITING_TIME.integerValue)
        DlcConfig.setServerSupportOn(SERVER_SUPPORT_CLIENT.booleanValue)
        DlcConfig.setModpack(MODPACK.stringValue)
        DlcConfig.setModpackUrl(MODPACK_URL.stringValue)
        DlcConfig.setUpdateGitRepoUrl(UPDATE_GIT_REPO.stringValue)
        DlcConfig.setApiEnabled(API_ENABLED.booleanValue)
        DlcConfig.setApiPort(API_PORT.integerValue)
        DlcConfig.setApiToken(API_TOKEN.stringValue)
        DlcConfig.setNoticeDisplayMode(NOTICE_DISPLAY_MODE.stringValue)
        DlcConfig.setNoticeButtonAnchor(NOTICE_BUTTON_ANCHOR.stringValue)
        DlcConfig.setNoticeButtonOffsetX(NOTICE_BUTTON_OFFSET_X.integerValue)
        DlcConfig.setNoticeButtonOffsetY(NOTICE_BUTTON_OFFSET_Y.integerValue)
        DlcConfig.setNoticePanelAnchor(NOTICE_PANEL_ANCHOR.stringValue)
        DlcConfig.setNoticePanelOffsetX(NOTICE_PANEL_OFFSET_X.integerValue)
        DlcConfig.setNoticePanelOffsetY(NOTICE_PANEL_OFFSET_Y.integerValue)
        DlcConfig.setNoticePanelWidth(NOTICE_PANEL_WIDTH.integerValue)
        DlcConfig.setNoticePanelHeight(NOTICE_PANEL_HEIGHT.integerValue)
        DlcConfig.setTitleAnchor(TITLE_ANCHOR.stringValue)
        DlcConfig.setTitleOffsetX(TITLE_OFFSET_X.integerValue)
        DlcConfig.setTitleOffsetY(TITLE_OFFSET_Y.integerValue)
        DlcConfig.setTitleUpdateAnchor(TITLE_UPDATE_ANCHOR.stringValue)
        DlcConfig.setTitleUpdateOffsetX(TITLE_UPDATE_OFFSET_X.integerValue)
        DlcConfig.setTitleUpdateOffsetY(TITLE_UPDATE_OFFSET_Y.integerValue)

        DlcConfig.setMaxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS.integerValue)
        DlcConfig.setMaxSegmentsPerFile(MAX_SEGMENTS_PER_FILE.integerValue)
        DlcConfig.setMinSegmentBytes(kotlin.math.max(1, MIN_SEGMENT_MB.integerValue).toLong() * 1024L * 1024L)
        DlcConfig.setMaxRetryCount(MAX_RETRY.integerValue)
        DlcConfig.setConnectTimeoutMs(CONNECT_TIMEOUT_SECONDS.integerValue * 1000)
        DlcConfig.setTaskTimeoutMs(TASK_TIMEOUT_SECONDS.integerValue * 1000)
        DlcConfig.setDownloadMirrorMode(DOWNLOAD_MIRROR_MODE.stringValue)
        DlcConfig.setDownloadMirrorProbeCount(DOWNLOAD_MIRROR_PROBE_COUNT.integerValue)
        DlcConfig.setDownloadMirrorProbeStrategy(DOWNLOAD_MIRROR_PROBE_STRATEGY.stringValue)
    }

    private fun toMb(bytes: Long): Int {
        val mb = (bytes + 1024L * 1024L - 1) / (1024L * 1024L)
        if (mb < 1) {
            return 1
        }
        if (mb > Int.MAX_VALUE) {
            return Int.MAX_VALUE
        }
        return mb.toInt()
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
