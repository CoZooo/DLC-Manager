package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.screen.DlcUpdateManagerScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class DlcUpdateClient {
    private DlcUpdateClient() {
    }

    private static boolean pendingProcessed;

    public static boolean startPendingFromTitle(Screen parent) {
        if (pendingProcessed || com.cozooo.dlc_manager.bootstrap.DlcStartupRequirements.usesEarlyInstallation()) return false;
        try {
            if (!DlcPendingUpdate.hasPending()) return false;
        } catch (java.io.IOException exception) {
            pendingProcessed = true;
            Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcUpdateResultScreen(parent, false,
                com.cozooo.dlc_manager.platform.DlcTextCompat.literal(exception.getMessage())));
            return true;
        }
        pendingProcessed = true;
        var progress = new com.cozooo.dlc_manager.client.screen.DlcPendingUpdateScreen(parent);
        Minecraft.getInstance().setScreen(progress);
        new Thread(() -> {
            String failure = null;
            try {
                DlcPendingUpdate.process(status -> Minecraft.getInstance().execute(() -> progress.setStatus(
                    com.cozooo.dlc_manager.client.core.DlcMessageAdapter.toProgressText(status))), true);
            } catch (java.io.IOException exception) { failure = exception.getMessage(); }
            String error = failure;
            Minecraft.getInstance().execute(() -> {
                if (showPendingResult(parent)) return;
                if (error != null) Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcUpdateResultScreen(parent, false,
                    com.cozooo.dlc_manager.platform.DlcTextCompat.literal(error)));
                else Minecraft.getInstance().setScreen(parent);
            });
        }, "dlc-pending-update").start();
        return true;
    }

    public static boolean showPendingResult(Screen parent) {
        try {
            var result = DlcPendingUpdate.readResult();
            if (result == null) return false;
            boolean success = result.get("success").getAsBoolean();
            boolean restart = result.has("restart_required") && result.get("restart_required").getAsBoolean();
            var message = success
                ? com.cozooo.dlc_manager.platform.DlcTextCompat.translatable(restart ? "dlc_manager.update.pending_fabric_restart" : "dlc_manager.update.success")
                : com.cozooo.dlc_manager.platform.DlcTextCompat.translatable(result.has("rollback_complete") && !result.get("rollback_complete").getAsBoolean()
                    ? "dlc_manager.update.pending_recovery_failed" : "dlc_manager.update.pending_failed", result.get("message").getAsString());
            if (success && !restart) {
                Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcSingleButtonScreen(parent,
                    com.cozooo.dlc_manager.platform.DlcTextCompat.translatable("dlc_manager.update.title"), message,
                    com.cozooo.dlc_manager.platform.DlcTextCompat.translatable("gui.done")));
            } else Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcUpdateResultScreen(parent, success, message));
            DlcPendingUpdate.acknowledgeResult();
            return true;
        } catch (java.io.IOException exception) {
            return false;
        }
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
