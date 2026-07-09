package com.cozooo.dlc_manager.client.notice;

import com.cozooo.dlc_manager.client.screen.DlcNoticeScreen;
import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class DlcNoticeManager {
    public static boolean showFromTitle(Screen parent) {
        DlcNoticeConfig config = DlcNoticeRuntime.consumeTitleNoticeConfig();
        return config != null && openNotice(parent, config);
    }

    public static boolean tryShowFromManager(Screen parent) {
        DlcNoticeConfig config = DlcNoticeRuntime.consumeManagerNoticeConfig();
        return config != null && openNotice(parent, config);
    }

    public static boolean forceShow(Screen parent) {
        DlcNoticeConfig config = DlcNoticeRuntime.forceNoticeConfig();
        return config != null && openNotice(parent, config);
    }

    public static boolean isNoticeEnabled() {
        return DlcNoticeRuntime.isNoticeEnabled();
    }

    private static boolean openNotice(Screen parent, DlcNoticeConfig config) {
        Minecraft.getInstance().setScreen(new DlcNoticeScreen(parent, config));
        return true;
    }
}
