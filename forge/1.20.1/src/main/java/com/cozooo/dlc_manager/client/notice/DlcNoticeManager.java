package com.cozooo.dlc_manager.client.notice;

import com.cozooo.dlc_manager.client.screen.DlcNoticeScreen;
import com.cozooo.dlc_manager.core.DlcNoticeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class DlcNoticeManager {
    private static boolean shownOnGameLaunch = false;
    private static boolean shownOnManagerOpen = false;

    public static boolean showFromTitle(Screen parent) {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled() || config.isNeverShow()) {
            return false;
        }
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.PER_LAUNCH_GAME) {
            return false;
        }
        if (shownOnGameLaunch) {
            return false;
        }
        shownOnGameLaunch = true;
        return openNotice(parent, config);
    }

    public static boolean maybeShowFromManager(Screen parent) {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled() || config.isNeverShow()) {
            return false;
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.PER_LAUNCH_MANAGER) {
            return openNotice(parent, config);
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.PER_GAME_SESSION) {
            if (shownOnManagerOpen) {
                return false;
            }
            shownOnManagerOpen = true;
            return openNotice(parent, config);
        }
        if (config.getShowFrequency() == DlcNoticeConfig.ShowFrequency.ONCE) {
            if (config.isNeverShow()) {
                return false;
            }
            config.setNeverShow(true);
            config.save();
            return openNotice(parent, config);
        }
        return false;
    }

    public static boolean forceShow(Screen parent) {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled()) {
            return false;
        }
        return openNotice(parent, config);
    }

    public static boolean isNoticeEnabled() {
        DlcNoticeConfig config = DlcNoticeConfig.load();
        return config.isNoticeEnabled();
    }

    private static boolean openNotice(Screen parent, DlcNoticeConfig config) {
        Minecraft.getInstance().setScreen(new DlcNoticeScreen(parent, config));
        return true;
    }
}
