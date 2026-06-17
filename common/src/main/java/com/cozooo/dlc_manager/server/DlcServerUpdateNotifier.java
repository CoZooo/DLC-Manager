package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.core.DlcConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DlcServerUpdateNotifier {
    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);
    private static volatile DlcSelfUpdateService.SelfUpdateResult lastResult;

    private DlcServerUpdateNotifier() {
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (!DlcConfig.isServerSupportOnServer()) {
            return;
        }
        if (player == null || !player.hasPermissions(2)) {
            return;
        }
        if (!DlcConfig.isSelfUpdateAutoCheckOn()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !server.isDedicatedServer()) {
            return;
        }
        if (CHECKED.get()) {
            sendIfUpdate(player);
            return;
        }
        if (!CHECKED.compareAndSet(false, true)) {
            sendIfUpdate(player);
            return;
        }
        CompletableFuture.runAsync(() -> {
            DlcSelfUpdateService.SelfUpdateResult result = DlcSelfUpdateService.checkForUpdate();
            lastResult = result;
            if (result != null && result.hasUpdate) {
                server.execute(() -> sendIfUpdate(player));
            }
        });
    }

    private static void sendIfUpdate(ServerPlayer player) {
        DlcSelfUpdateService.SelfUpdateResult result = lastResult;
        if (result == null || !result.hasUpdate) {
            return;
        }
        String current = result.currentVersion == null ? "" : result.currentVersion;
        String latest = result.latestVersion == null ? "" : result.latestVersion;
        player.sendSystemMessage(Component.translatable("dlc_manager.server.self_update_available", current, latest));
    }
}
