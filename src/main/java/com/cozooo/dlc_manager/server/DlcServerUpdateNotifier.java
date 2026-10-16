package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

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
        if (!DlcConfig.isServerSupportOnServer() || player == null || !DlcConfig.isSelfUpdateAutoCheckOn()) {
            return;
        }
        try {
            if (!invokeBoolean(player, "hasPermissions", 2)) return;
            Object server = invoke(player, "getServer");
            if (server == null || !invokeBoolean(server, "isDedicatedServer")) return;
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
                    try {
                        invoke(server, "execute", (Runnable) () -> sendIfUpdate(player));
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            });
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void sendIfUpdate(ServerPlayer player) {
        DlcSelfUpdateService.SelfUpdateResult result = lastResult;
        if (result == null || !result.hasUpdate) {
            return;
        }
        String current = result.currentVersion == null ? "" : result.currentVersion;
        String latest = result.latestVersion == null ? "" : result.latestVersion;
        try {
            invoke(player, "sendSystemMessage", DlcTextCompat.translatable("dlc_manager.server.self_update_available", current, latest));
        } catch (ReflectiveOperationException ignored) {
        }
    }
    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (var method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try {
                return method.invoke(target, args);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean invokeBoolean(Object target, String name, Object... args) throws ReflectiveOperationException {
        Object result = invoke(target, name, args);
        return result instanceof Boolean && (Boolean) result;
    }

}
