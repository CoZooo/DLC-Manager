package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.CompletableFuture;

public final class DlcServerCommandHandlers {
    private DlcServerCommandHandlers() {
    }

    public static int handleServerManager(CommandSourceStack source) {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"));
            return 0;
        }
        if (!isServerSupportEnabled(source)) {
            return 0;
        }
        sendSuccess(source, Component.translatable("dlc_manager.server.command_open_hint"));
        return 1;
    }

    public static int handleServerUpdate(CommandSourceStack source) {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"));
            return 0;
        }
        if (!isServerSupportEnabled(source)) {
            return 0;
        }
        sendSuccess(source, Component.translatable("dlc_manager.update.applying"));
        CompletableFuture.runAsync(() -> {
            DlcUpdateService.UpdateCheckResult check = DlcUpdateService.checkForUpdates();
            boolean hasUpdate = check != null && check.hasUpdates();
            if (!hasUpdate) {
                execute(source, () -> sendSuccess(source, Component.translatable("dlc_manager.update.no_updates")));
                return;
            }
            DlcUpdateService.UpdateApplyResult result = DlcUpdateService.applyUpdates(check.getUpdates(), false);
            if (result != null && result.isSuccess()) {
                execute(source, () -> {
                    sendSuccess(source, Component.translatable("dlc_manager.update.success"));
                    sendSuccess(source, Component.translatable("dlc_manager.update.restart"));
                });
            } else if (result != null && result.isMissing()) {
                execute(source, () -> sendFailure(source, Component.translatable("dlc_manager.update.missing_title")));
            } else {
                execute(source, () -> sendFailure(source, Component.translatable("dlc_manager.update.failed")));
            }
        });
        return 1;
    }

    public static int handleServerUpdateManager(CommandSourceStack source) {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"));
            return 0;
        }
        if (!isServerSupportEnabled(source)) {
            return 0;
        }
        if (!DlcPathConfig.isUpdateManagerOn()) {
            sendFailure(source, Component.translatable("dlc_manager.update.no_updates"));
            return 0;
        }
        DlcUpdateService.UpdateCheckResult check = DlcUpdateService.checkForUpdates();
        if (check == null || !check.hasUpdates()) {
            sendSuccess(source, Component.translatable("dlc_manager.update.no_updates"));
            return 0;
        }
        int count = check.getUpdates() == null ? 0 : check.getUpdates().size();
        sendSuccess(source, Component.translatable("dlc_manager.server.command_update_list", count));
        return 1;
    }

    public static int handleServerSelfUpdate(CommandSourceStack source) {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"));
            return 0;
        }
        if (!isServerSupportEnabled(source)) {
            return 0;
        }
        sendSuccess(source, Component.translatable("dlc_manager.self_update.checking"));
        CompletableFuture.runAsync(() -> {
            DlcSelfUpdateService.SelfUpdateResult result = DlcSelfUpdateService.checkForUpdate();
            if (result == null || !result.hasUpdate) {
                execute(source, () -> sendSuccess(source, Component.translatable("dlc_manager.self_update.none")));
                return;
            }
            DlcSelfUpdateService.DownloadResult download = DlcSelfUpdateService.downloadUpdate(result);
            if (download.success) {
                execute(source, () -> sendSuccess(source, Component.translatable("dlc_manager.self_update.restart")));
            } else {
                execute(source, () -> sendFailure(source, Component.translatable("dlc_manager.self_update.failed")));
            }
        });
        return 1;
    }

    public static int handleServerPush(CommandSourceStack source) {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"));
            return 0;
        }
        if (!isServerSupportEnabled(source)) {
            return 0;
        }
        sendFailure(source, Component.translatable("dlc_manager.server.command_push_unavailable"));
        return 0;
    }

    private static boolean isDedicatedServer(CommandSourceStack source) {
        if (source == null) {
            return false;
        }
        MinecraftServer server = source.getServer();
        return server != null && server.isDedicatedServer();
    }

    private static boolean isServerSupportEnabled(CommandSourceStack source) {
        if (!DlcPathConfig.isServerSupportOnServer()) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_disabled"));
            return false;
        }
        return true;
    }

    private static void execute(CommandSourceStack source, Runnable runnable) {
        MinecraftServer server = source.getServer();
        if (server != null) {
            server.execute(runnable);
        } else {
            runnable.run();
        }
    }

    private static void sendSuccess(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void sendFailure(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }
}
