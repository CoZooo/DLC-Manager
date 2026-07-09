package com.cozooo.dlc_manager.client.notice;

import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;

public final class DlcNoticeRuntime {
    private static boolean shownOnGameLaunch = false;
    private static boolean shownOnManagerOpen = false;

    private DlcNoticeRuntime() {
    }

    public static DlcNoticeConfig consumeTitleNoticeConfig() {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled() || config.isNeverShow()) {
            return null;
        }
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.PER_LAUNCH_GAME) {
            return null;
        }
        if (shownOnGameLaunch) {
            return null;
        }
        shownOnGameLaunch = true;
        return config;
    }

    public static DlcNoticeConfig consumeManagerNoticeConfig() {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled() || config.isNeverShow()) {
            return null;
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.PER_LAUNCH_MANAGER) {
            return config;
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.PER_GAME_SESSION) {
            if (shownOnManagerOpen) {
                return null;
            }
            shownOnManagerOpen = true;
            return config;
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.ONCE) {
            if (config.isNeverShow()) {
                return null;
            }
            config.setNeverShow(true);
            config.save();
            return config;
        }
        return null;
    }

    public static DlcNoticeConfig forceNoticeConfig() {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        return config.isNoticeEnabled() ? config : null;
    }

    public static boolean isNoticeEnabled() {
        return DlcNoticeConfig.load().isNoticeEnabled();
    }
}
