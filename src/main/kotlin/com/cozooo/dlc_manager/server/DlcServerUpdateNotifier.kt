package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object DlcServerUpdateNotifier {
    private val checked = AtomicBoolean(false)

    @Volatile
    private var lastResult: DlcSelfUpdateService.SelfUpdateResult? = null

    @JvmStatic
    fun onPlayerJoin(player: ServerPlayer?) {
        if (!DlcConfig.isServerSupportOnServer()) {
            return
        }
        if (player == null || !player.hasPermissions(2)) {
            return
        }
        if (!DlcConfig.isSelfUpdateAutoCheckOn()) {
            return
        }
        val server = player.server
        if (server == null || !server.isDedicatedServer) {
            return
        }
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
                server.execute { sendIfUpdate(player) }
            }
        }
    }

    private fun sendIfUpdate(player: ServerPlayer) {
        val result = lastResult
        if (result == null || !result.hasUpdate) {
            return
        }
        val current = result.currentVersion ?: ""
        val latest = result.latestVersion ?: ""
        player.sendSystemMessage(Component.translatable("dlc_manager.server.self_update_available", current, latest))
    }
}
