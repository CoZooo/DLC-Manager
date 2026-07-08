package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.screen.DlcUpdateManagerScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class DlcUpdateClient {
    private DlcUpdateClient() {
    }

    public static boolean tryShowFromTitle(Screen parent) {
        UpdateCheckResult result = DlcUpdateRuntime.pollTitlePromptCandidate();
        if (result == null) {
            return false;
        }
        showPrompt(parent, result, true);
        return true;
    }

    public static boolean isPrompted() {
        return DlcUpdateRuntime.isPrompted();
    }

    public static void markPrompted() {
        DlcUpdateRuntime.markPrompted();
    }

    public static void showPrompt(Screen parent, UpdateCheckResult result) {
        showPrompt(parent, result, false);
    }

    public static void showPrompt(Screen parent, UpdateCheckResult result, boolean allowSkipThisUpdate) {
        DlcUpdateRuntime.markPrompted();
        Minecraft.getInstance().setScreen(new DlcUpdatePromptScreen(parent, result, allowSkipThisUpdate));
    }

    public static void openUpdateManager(Screen parent) {
        if (!DlcUpdateRuntime.isUpdateManagerEnabled()) {
            return;
        }
        Minecraft.getInstance().setScreen(new DlcUpdateManagerScreen(parent));
    }
}
