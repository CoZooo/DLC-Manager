package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;


public final class DlcUpdateClient {
    private static boolean promptedThisSession = false;

    private DlcUpdateClient() {
    }

    public static boolean maybeShowFromTitle(Screen parent) {
        if (promptedThisSession) {
            return false;
        }
        UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        if (!result.hasUpdates()) {
            return false;
        }
        showPrompt(parent, result);
        return true;
    }

    public static boolean isPrompted() {
        return promptedThisSession;
    }

    public static void markPrompted() {
        promptedThisSession = true;
    }

    public static void showPrompt(Screen parent, UpdateCheckResult result) {
        promptedThisSession = true;
        Minecraft.getInstance().setScreen(new DlcUpdatePromptScreen(parent, result));
    }

    public static void openUpdateManager(Screen parent) {
        Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcUpdateManagerScreen(parent));
    }
}
