package com.cozooo.dlc_manager.client.notice

import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig

object DlcNoticeRuntime {
    private var shownOnGameLaunch = false
    private var shownOnManagerOpen = false

    @JvmStatic
    fun consumeTitleNoticeConfig(): DlcNoticeConfig? {
        val config = DlcNoticeConfig.load()
        if (!config.isNoticeEnabled() || config.isNeverShow()) {
            return null
        }
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.PER_LAUNCH_GAME) {
            return null
        }
        if (shownOnGameLaunch) {
            return null
        }
        shownOnGameLaunch = true
        return config
    }

    @JvmStatic
    fun consumeManagerNoticeConfig(): DlcNoticeConfig? {
        val config = DlcNoticeConfig.load()
        if (!config.isNoticeEnabled() || config.isNeverShow()) {
            return null
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.PER_LAUNCH_MANAGER) {
            return config
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.PER_GAME_SESSION) {
            if (shownOnManagerOpen) {
                return null
            }
            shownOnManagerOpen = true
            return config
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.ONCE) {
            if (config.isNeverShow()) {
                return null
            }
            config.setNeverShow(true)
            config.save()
            return config
        }
        return null
    }

    @JvmStatic
    fun forceNoticeConfig(): DlcNoticeConfig? {
        val config = DlcNoticeConfig.load()
        return if (config.isNoticeEnabled()) config else null
    }

    @JvmStatic
    fun isNoticeEnabled(): Boolean {
        return DlcNoticeConfig.load().isNoticeEnabled()
    }
}
