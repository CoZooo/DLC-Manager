package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object DlcServerUpdateNotifier {
    private val checked = AtomicBoolean(false)

    @Volatile
    private var lastResult: DlcSelfUpdateService.SelfUpdateResult? = null

    @JvmStatic
    fun onPlayerJoin(player: ServerPlayer?) {
        if (!DlcConfig.isServerSupportOnServer() || player == null || !DlcConfig.isSelfUpdateAutoCheckOn()) return
        try {
            if (!invokeBoolean(player, "hasPermissions", 2)) return
            val server = invoke(player, "getServer") ?: return
            if (!invokeBoolean(server, "isDedicatedServer")) return
            if (checked.get()) {
                sendIfUpdate(player)
                return
            }
            if (!checked.compareAndSet(false, true)) {
                sendIfUpdate(player)
                return
            }
            CompletableFuture.runAsync {
                val result = DlcSelfUpdateService.checkForUpdate()
                lastResult = result
                if (result.hasUpdate) {
                    try {
                        invoke(server, "execute", Runnable { sendIfUpdate(player) })
                    } catch (_: ReflectiveOperationException) {
                    }
                }
            }
        } catch (_: ReflectiveOperationException) {
        }
    }

    private fun sendIfUpdate(player: ServerPlayer) {
        val result = lastResult ?: return
        if (!result.hasUpdate) return
        val current = result.currentVersion ?: ""
        val latest = result.latestVersion ?: ""
        try {
            invoke(player, "sendSystemMessage", DlcTextCompat.translatable("dlc_manager.server.self_update_available", current, latest))
        } catch (_: ReflectiveOperationException) {
        }
    }

    private fun invoke(target: Any, name: String, vararg args: Any): Any? {
        for (method in target.javaClass.methods) {
            if (method.name != name || method.parameterCount != args.size) continue
            try {
                return method.invoke(target, *args)
            } catch (_: IllegalArgumentException) {
            }
        }
        throw NoSuchMethodException(name)
    }

    private fun invokeBoolean(target: Any, name: String, vararg args: Any): Boolean = invoke(target, name, *args) as? Boolean ?: false
}
