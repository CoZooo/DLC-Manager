package com.cozooo.dlc_manager.client.config

import com.cozooo.dlc_manager.core.DlcPathConfig
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.config.options.ConfigString
import fi.dy.masa.malilib.util.StringUtils

object DlcPathConfigClient {
    private val DLC_ROOT = DlcConfigString("dlcRoot", DlcPathConfig.DEFAULT_DLC_ROOT, "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot")
    private val TACZ_ROOT = DlcConfigString("taczRoot", DlcPathConfig.DEFAULT_TACZ_DLC_ROOT, "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot")
    private val RESOURCEPACK_ROOT = DlcConfigString("resourcepackRoot", DlcPathConfig.DEFAULT_RESOURCEPACK_ROOT, "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot")
    private val DATAPACK_ROOT = DlcConfigString("datapackRoot", DlcPathConfig.DEFAULT_DATAPACK_ROOT, "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot")

    private val ENABLE_ALL_BUTTON = DlcConfigBoolean("isEnableAllButtonShown", DlcPathConfig.DEFAULT_ENABLE_ALL_SHOWN, "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown")
    private val CONFIG_BUTTON = DlcConfigBoolean("isConfigButtonShown", DlcPathConfig.DEFAULT_CONFIG_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isConfigButtonShown", "dlc_manager.config.ui.isConfigButtonShown")
    private val U_BUTTON = DlcConfigBoolean("isUButtomShown", DlcPathConfig.DEFAULT_U_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isUButtomShown", "dlc_manager.config.ui.isUButtomShown")
    private val UPDATE_MANAGER_ON = DlcConfigBoolean("isUpdateManagerOn", DlcPathConfig.DEFAULT_UPDATE_MANAGER_ON, "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn")
    private val SELF_UPDATE_AUTO_CHECK_ON = DlcConfigBoolean("isSelfUpdateAutoCheckOn", DlcPathConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON, "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn")
    private val PRESET_FUNC_ON = DlcConfigBoolean("isPresetFuncOn", DlcPathConfig.DEFAULT_PRESET_FUNC_ON, "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn")
    private val PACK_FUNC_ON = DlcConfigBoolean("isPackFuncOn", DlcPathConfig.DEFAULT_PACK_FUNC_ON, "dlc_manager.config.comment.ui.isPackFuncOn", "dlc_manager.config.ui.isPackFuncOn")
    private val P_BUTTON = DlcConfigBoolean("isPButtomShown", DlcPathConfig.DEFAULT_P_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isPButtomShown", "dlc_manager.config.ui.isPButtomShown")
    private val SHOW_ON_FIRST_RUN = DlcConfigBoolean("ShowOnFirstRun", DlcPathConfig.DEFAULT_SHOW_ON_FIRST_RUN, "dlc_manager.config.comment.ui.ShowOnFirstRun", "dlc_manager.config.ui.ShowOnFirstRun")
    private val ALARM_WAITING_TIME = DlcConfigInteger("WaitingTime", DlcPathConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10, "dlc_manager.config.comment.ui.WaitingTime", "dlc_manager.config.ui.WaitingTime")
    private val SERVER_SUPPORT_CLIENT = DlcConfigBoolean("serverSupport", DlcPathConfig.DEFAULT_SERVER_SUPPORT, "dlc_manager.config.comment.ui.serverSupport", "dlc_manager.config.ui.serverSupport")
    private val MODPACK_URL = DlcConfigString("modpackUrl", DlcPathConfig.DEFAULT_MODPACK_URL, "dlc_manager.config.comment.ui.modpackUrl", "dlc_manager.config.ui.modpackUrl")
    private val UPDATE_GITHUB_REPO = DlcConfigString("updateGithubRepo", DlcPathConfig.DEFAULT_UPDATE_GITHUB_REPO_URL, "dlc_manager.config.comment.ui.updateGithubRepo", "dlc_manager.config.ui.updateGithubRepo")
    private val API_ENABLED = DlcConfigBoolean("apiEnabled", DlcPathConfig.DEFAULT_API_ENABLED, "dlc_manager.config.comment.ui.apiEnabled", "dlc_manager.config.ui.apiEnabled")
    private val API_PORT = DlcConfigInteger("apiPort", DlcPathConfig.DEFAULT_API_PORT, 0, 65535, "dlc_manager.config.comment.ui.apiPort", "dlc_manager.config.ui.apiPort")
    private val API_TOKEN = DlcConfigString("apiToken", DlcPathConfig.DEFAULT_API_TOKEN, "dlc_manager.config.comment.ui.apiToken", "dlc_manager.config.ui.apiToken")
    private val NOTICE_DISPLAY_MODE = DlcConfigString("noticeDisplayMode", DlcPathConfig.DEFAULT_NOTICE_DISPLAY_MODE, "dlc_manager.config.comment.ui.noticeDisplayMode", "dlc_manager.config.ui.noticeDisplayMode")
    private val NOTICE_BUTTON_ANCHOR = DlcConfigString("noticeButtonAnchor", DlcPathConfig.DEFAULT_NOTICE_BUTTON_ANCHOR, "dlc_manager.config.comment.ui.noticeButtonAnchor", "dlc_manager.config.ui.noticeButtonAnchor")
    private val NOTICE_BUTTON_OFFSET_X = DlcConfigInteger("noticeButtonOffsetX", DlcPathConfig.DEFAULT_NOTICE_BUTTON_OFFSET_X, -DlcPathConfig.MAX_NOTICE_OFFSET, DlcPathConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticeButtonOffsetX", "dlc_manager.config.ui.noticeButtonOffsetX")
    private val NOTICE_BUTTON_OFFSET_Y = DlcConfigInteger("noticeButtonOffsetY", DlcPathConfig.DEFAULT_NOTICE_BUTTON_OFFSET_Y, -DlcPathConfig.MAX_NOTICE_OFFSET, DlcPathConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticeButtonOffsetY", "dlc_manager.config.ui.noticeButtonOffsetY")
    private val NOTICE_PANEL_ANCHOR = DlcConfigString("noticePanelAnchor", DlcPathConfig.DEFAULT_NOTICE_PANEL_ANCHOR, "dlc_manager.config.comment.ui.noticePanelAnchor", "dlc_manager.config.ui.noticePanelAnchor")
    private val NOTICE_PANEL_OFFSET_X = DlcConfigInteger("noticePanelOffsetX", DlcPathConfig.DEFAULT_NOTICE_PANEL_OFFSET_X, -DlcPathConfig.MAX_NOTICE_OFFSET, DlcPathConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticePanelOffsetX", "dlc_manager.config.ui.noticePanelOffsetX")
    private val NOTICE_PANEL_OFFSET_Y = DlcConfigInteger("noticePanelOffsetY", DlcPathConfig.DEFAULT_NOTICE_PANEL_OFFSET_Y, -DlcPathConfig.MAX_NOTICE_OFFSET, DlcPathConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticePanelOffsetY", "dlc_manager.config.ui.noticePanelOffsetY")
    private val NOTICE_PANEL_WIDTH = DlcConfigInteger("noticePanelWidth", DlcPathConfig.DEFAULT_NOTICE_PANEL_WIDTH, 0, DlcPathConfig.MAX_NOTICE_PANEL_SIZE, "dlc_manager.config.comment.ui.noticePanelWidth", "dlc_manager.config.ui.noticePanelWidth")
    private val NOTICE_PANEL_HEIGHT = DlcConfigInteger("noticePanelHeight", DlcPathConfig.DEFAULT_NOTICE_PANEL_HEIGHT, 0, DlcPathConfig.MAX_NOTICE_PANEL_SIZE, "dlc_manager.config.comment.ui.noticePanelHeight", "dlc_manager.config.ui.noticePanelHeight")
    private val TITLE_ANCHOR = DlcConfigString("titleAnchor", DlcPathConfig.DEFAULT_TITLE_ANCHOR, "dlc_manager.config.comment.ui.titleAnchor", "dlc_manager.config.ui.titleAnchor")
    private val TITLE_OFFSET_X = DlcConfigInteger("titleOffsetX", DlcPathConfig.DEFAULT_TITLE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetX", "dlc_manager.config.ui.titleOffsetX")
    private val TITLE_OFFSET_Y = DlcConfigInteger("titleOffsetY", DlcPathConfig.DEFAULT_TITLE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetY", "dlc_manager.config.ui.titleOffsetY")
    private val TITLE_UPDATE_ANCHOR = DlcConfigString("titleUpdateAnchor", DlcPathConfig.DEFAULT_TITLE_UPDATE_ANCHOR, "dlc_manager.config.comment.ui.titleUpdateAnchor", "dlc_manager.config.ui.titleUpdateAnchor")
    private val TITLE_UPDATE_OFFSET_X = DlcConfigInteger("titleUpdateOffsetX", DlcPathConfig.DEFAULT_TITLE_UPDATE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleUpdateOffsetX", "dlc_manager.config.ui.titleUpdateOffsetX")
    private val TITLE_UPDATE_OFFSET_Y = DlcConfigInteger("titleUpdateOffsetY", DlcPathConfig.DEFAULT_TITLE_UPDATE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleUpdateOffsetY", "dlc_manager.config.ui.titleUpdateOffsetY")

    private val MAX_CONCURRENT_DOWNLOADS = DlcConfigInteger("maxConcurrentDownloads", DlcPathConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64, "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads")
    private val MAX_SEGMENTS_PER_FILE = DlcConfigInteger("maxSegmentsPerFile", DlcPathConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64, "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile")
    private val MIN_SEGMENT_MB = DlcConfigInteger("minSegmentMB", DlcPathConfig.DEFAULT_MIN_SEGMENT_MB, 1, Int.MAX_VALUE, "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB")
    private val MAX_RETRY = DlcConfigInteger("maxRetryCount", DlcPathConfig.DEFAULT_MAX_RETRY, 1, 100, "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount")
    private val CONNECT_TIMEOUT_SECONDS = DlcConfigInteger("connectTimeoutSeconds", DlcPathConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds")
    private val TASK_TIMEOUT_SECONDS = DlcConfigInteger("taskTimeoutSeconds", DlcPathConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds")
    private val DOWNLOAD_MIRROR_MODE = DlcConfigString("downloadMirrorMode", DlcPathConfig.DEFAULT_DOWNLOAD_MIRROR_MODE, "dlc_manager.config.comment.download.downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode")

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
        MODPACK_URL,
        UPDATE_GITHUB_REPO,
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
        DOWNLOAD_MIRROR_MODE
    )

    @JvmStatic
    fun getPathConfigs(): List<IConfigBase> = PATH_CONFIGS

    @JvmStatic
    fun getUiConfigs(): List<IConfigBase> = UI_CONFIGS

    @JvmStatic
    fun getServerConfigs(): List<IConfigBase> = SERVER_CONFIGS

    @JvmStatic
    fun getDownloadConfigs(): List<IConfigBase> = DOWNLOAD_CONFIGS

    @JvmStatic
    fun syncFromConfig() {
        DLC_ROOT.setValueFromString(DlcPathConfig.getDlcRootRaw())
        TACZ_ROOT.setValueFromString(DlcPathConfig.getTaczRootRaw())
        RESOURCEPACK_ROOT.setValueFromString(DlcPathConfig.getResourcepackRootRaw())
        DATAPACK_ROOT.setValueFromString(DlcPathConfig.getDatapackRootRaw())

        ENABLE_ALL_BUTTON.booleanValue = DlcPathConfig.isEnableAllButtonShown()
        CONFIG_BUTTON.booleanValue = DlcPathConfig.isConfigButtonShown()
        U_BUTTON.booleanValue = DlcPathConfig.isUButtomShown()
        UPDATE_MANAGER_ON.booleanValue = DlcPathConfig.isUpdateManagerOn()
        SELF_UPDATE_AUTO_CHECK_ON.booleanValue = DlcPathConfig.isSelfUpdateAutoCheckOn()
        PRESET_FUNC_ON.booleanValue = DlcPathConfig.isPresetFuncOn()
        PACK_FUNC_ON.booleanValue = DlcPathConfig.isPackFuncOn()
        P_BUTTON.booleanValue = DlcPathConfig.isPButtomShown()
        SHOW_ON_FIRST_RUN.booleanValue = DlcPathConfig.isShowOnFirstRun()
        ALARM_WAITING_TIME.integerValue = DlcPathConfig.getAlarmWaitingTime()
        SERVER_SUPPORT_CLIENT.booleanValue = DlcPathConfig.isServerSupportOn()
        MODPACK_URL.setValueFromString(DlcPathConfig.getModpackUrl())
        UPDATE_GITHUB_REPO.setValueFromString(DlcPathConfig.getUpdateGithubRepoUrl())
        API_ENABLED.booleanValue = DlcPathConfig.isApiEnabled()
        API_PORT.integerValue = DlcPathConfig.getApiPort()
        API_TOKEN.setValueFromString(DlcPathConfig.getApiToken())
        NOTICE_DISPLAY_MODE.setValueFromString(DlcPathConfig.getNoticeDisplayMode())
        NOTICE_BUTTON_ANCHOR.setValueFromString(DlcPathConfig.getNoticeButtonAnchor())
        NOTICE_BUTTON_OFFSET_X.integerValue = DlcPathConfig.getNoticeButtonOffsetX()
        NOTICE_BUTTON_OFFSET_Y.integerValue = DlcPathConfig.getNoticeButtonOffsetY()
        NOTICE_PANEL_ANCHOR.setValueFromString(DlcPathConfig.getNoticePanelAnchor())
        NOTICE_PANEL_OFFSET_X.integerValue = DlcPathConfig.getNoticePanelOffsetX()
        NOTICE_PANEL_OFFSET_Y.integerValue = DlcPathConfig.getNoticePanelOffsetY()
        NOTICE_PANEL_WIDTH.integerValue = DlcPathConfig.getNoticePanelWidth()
        NOTICE_PANEL_HEIGHT.integerValue = DlcPathConfig.getNoticePanelHeight()
        TITLE_ANCHOR.setValueFromString(DlcPathConfig.getTitleAnchor())
        TITLE_OFFSET_X.integerValue = DlcPathConfig.getTitleOffsetX()
        TITLE_OFFSET_Y.integerValue = DlcPathConfig.getTitleOffsetY()
        TITLE_UPDATE_ANCHOR.setValueFromString(DlcPathConfig.getTitleUpdateAnchor())
        TITLE_UPDATE_OFFSET_X.integerValue = DlcPathConfig.getTitleUpdateOffsetX()
        TITLE_UPDATE_OFFSET_Y.integerValue = DlcPathConfig.getTitleUpdateOffsetY()

        MAX_CONCURRENT_DOWNLOADS.integerValue = DlcPathConfig.getMaxConcurrentDownloads()
        MAX_SEGMENTS_PER_FILE.integerValue = DlcPathConfig.getMaxSegmentsPerFile()
        MIN_SEGMENT_MB.integerValue = toMb(DlcPathConfig.getMinSegmentBytes())
        MAX_RETRY.integerValue = DlcPathConfig.getMaxRetryCount()
        CONNECT_TIMEOUT_SECONDS.integerValue = kotlin.math.max(1, DlcPathConfig.getConnectTimeoutMs() / 1000)
        TASK_TIMEOUT_SECONDS.integerValue = kotlin.math.max(1, DlcPathConfig.getTaskTimeoutMs() / 1000)
        DOWNLOAD_MIRROR_MODE.setValueFromString(DlcPathConfig.getDownloadMirrorMode())
    }

    @JvmStatic
    fun applyToConfig() {
        DlcPathConfig.setDlcRoot(DLC_ROOT.stringValue)
        DlcPathConfig.setTaczRoot(TACZ_ROOT.stringValue)
        DlcPathConfig.setResourcepackRoot(RESOURCEPACK_ROOT.stringValue)
        DlcPathConfig.setDatapackRoot(DATAPACK_ROOT.stringValue)

        DlcPathConfig.setEnableAllButtonShown(ENABLE_ALL_BUTTON.booleanValue)
        DlcPathConfig.setConfigButtonShown(CONFIG_BUTTON.booleanValue)
        DlcPathConfig.setUButtomShown(U_BUTTON.booleanValue)
        DlcPathConfig.setUpdateManagerOn(UPDATE_MANAGER_ON.booleanValue)
        DlcPathConfig.setSelfUpdateAutoCheckOn(SELF_UPDATE_AUTO_CHECK_ON.booleanValue)
        DlcPathConfig.setPresetFuncOn(PRESET_FUNC_ON.booleanValue)
        DlcPathConfig.setPackFuncOn(PACK_FUNC_ON.booleanValue)
        DlcPathConfig.setPButtomShown(P_BUTTON.booleanValue)
        DlcPathConfig.setShowOnFirstRun(SHOW_ON_FIRST_RUN.booleanValue)
        DlcPathConfig.setAlarmWaitingTime(ALARM_WAITING_TIME.integerValue)
        DlcPathConfig.setServerSupportOn(SERVER_SUPPORT_CLIENT.booleanValue)
        DlcPathConfig.setModpackUrl(MODPACK_URL.stringValue)
        DlcPathConfig.setUpdateGithubRepoUrl(UPDATE_GITHUB_REPO.stringValue)
        DlcPathConfig.setApiEnabled(API_ENABLED.booleanValue)
        DlcPathConfig.setApiPort(API_PORT.integerValue)
        DlcPathConfig.setApiToken(API_TOKEN.stringValue)
        DlcPathConfig.setNoticeDisplayMode(NOTICE_DISPLAY_MODE.stringValue)
        DlcPathConfig.setNoticeButtonAnchor(NOTICE_BUTTON_ANCHOR.stringValue)
        DlcPathConfig.setNoticeButtonOffsetX(NOTICE_BUTTON_OFFSET_X.integerValue)
        DlcPathConfig.setNoticeButtonOffsetY(NOTICE_BUTTON_OFFSET_Y.integerValue)
        DlcPathConfig.setNoticePanelAnchor(NOTICE_PANEL_ANCHOR.stringValue)
        DlcPathConfig.setNoticePanelOffsetX(NOTICE_PANEL_OFFSET_X.integerValue)
        DlcPathConfig.setNoticePanelOffsetY(NOTICE_PANEL_OFFSET_Y.integerValue)
        DlcPathConfig.setNoticePanelWidth(NOTICE_PANEL_WIDTH.integerValue)
        DlcPathConfig.setNoticePanelHeight(NOTICE_PANEL_HEIGHT.integerValue)
        DlcPathConfig.setTitleAnchor(TITLE_ANCHOR.stringValue)
        DlcPathConfig.setTitleOffsetX(TITLE_OFFSET_X.integerValue)
        DlcPathConfig.setTitleOffsetY(TITLE_OFFSET_Y.integerValue)
        DlcPathConfig.setTitleUpdateAnchor(TITLE_UPDATE_ANCHOR.stringValue)
        DlcPathConfig.setTitleUpdateOffsetX(TITLE_UPDATE_OFFSET_X.integerValue)
        DlcPathConfig.setTitleUpdateOffsetY(TITLE_UPDATE_OFFSET_Y.integerValue)

        DlcPathConfig.setMaxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS.integerValue)
        DlcPathConfig.setMaxSegmentsPerFile(MAX_SEGMENTS_PER_FILE.integerValue)
        DlcPathConfig.setMinSegmentBytes(kotlin.math.max(1, MIN_SEGMENT_MB.integerValue).toLong() * 1024L * 1024L)
        DlcPathConfig.setMaxRetryCount(MAX_RETRY.integerValue)
        DlcPathConfig.setConnectTimeoutMs(CONNECT_TIMEOUT_SECONDS.integerValue * 1000)
        DlcPathConfig.setTaskTimeoutMs(TASK_TIMEOUT_SECONDS.integerValue * 1000)
        DlcPathConfig.setDownloadMirrorMode(DOWNLOAD_MIRROR_MODE.stringValue)
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
