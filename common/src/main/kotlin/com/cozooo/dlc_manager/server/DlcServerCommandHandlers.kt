package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcPathConfig
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

object DlcServerCommandHandlers {
    @JvmStatic
    fun handleServerManager(source: CommandSourceStack): Int {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"))
            return 0
        }
        if (!isServerSupportEnabled(source)) {
            return 0
        }
        sendSuccess(source, Component.translatable("dlc_manager.server.command_open_hint"))
        return 1
    }

    @JvmStatic
    fun handleServerUpdate(source: CommandSourceStack): Int {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"))
            return 0
        }
        if (!isServerSupportEnabled(source)) {
            return 0
        }
        sendSuccess(source, Component.translatable("dlc_manager.update.applying"))
        CompletableFuture.runAsync {
            val check = DlcUpdateService.checkForUpdates()
            if (!check.hasUpdates()) {
                execute(source) { sendSuccess(source, Component.translatable("dlc_manager.update.no_updates")) }
                return@runAsync
            }
            val result = DlcUpdateService.applyUpdates(check.updates, false)
            if (result.isSuccess) {
                execute(source) {
                    sendSuccess(source, Component.translatable("dlc_manager.update.success"))
                    sendSuccess(source, Component.translatable("dlc_manager.update.restart"))
                }
            } else if (result.isMissing) {
                execute(source) { sendFailure(source, Component.translatable("dlc_manager.update.missing_title")) }
            } else {
                execute(source) { sendFailure(source, Component.translatable("dlc_manager.update.failed")) }
            }
        }
        return 1
    }

    @JvmStatic
    fun handleServerUpdateManager(source: CommandSourceStack): Int {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"))
            return 0
        }
        if (!isServerSupportEnabled(source)) {
            return 0
        }
        if (!DlcPathConfig.isUpdateManagerOn()) {
            sendFailure(source, Component.translatable("dlc_manager.update.no_updates"))
            return 0
        }
        val check = DlcUpdateService.checkForUpdates()
        if (!check.hasUpdates()) {
            sendSuccess(source, Component.translatable("dlc_manager.update.no_updates"))
            return 0
        }
        val count = check.updates?.size ?: 0
        sendSuccess(source, Component.translatable("dlc_manager.server.command_update_list", count))
        return 1
    }

    @JvmStatic
    fun handleServerSelfUpdate(source: CommandSourceStack): Int {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"))
            return 0
        }
        if (!isServerSupportEnabled(source)) {
            return 0
        }
        sendSuccess(source, Component.translatable("dlc_manager.self_update.checking"))
        CompletableFuture.runAsync {
            val result = DlcSelfUpdateService.checkForUpdate()
            if (result == null || !result.hasUpdate) {
                execute(source) { sendSuccess(source, Component.translatable("dlc_manager.self_update.none")) }
                return@runAsync
            }
            val download = DlcSelfUpdateService.downloadUpdate(result)
            if (download.success) {
                execute(source) { sendSuccess(source, Component.translatable("dlc_manager.self_update.restart")) }
            } else {
                execute(source) { sendFailure(source, Component.translatable("dlc_manager.self_update.failed")) }
            }
        }
        return 1
    }

    @JvmStatic
    fun handleServerPush(source: CommandSourceStack): Int {
        if (!isDedicatedServer(source)) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_dedicated_only"))
            return 0
        }
        if (!isServerSupportEnabled(source)) {
            return 0
        }
        sendFailure(source, Component.translatable("dlc_manager.server.command_push_unavailable"))
        return 0
    }

    private fun isDedicatedServer(source: CommandSourceStack?): Boolean {
        if (source == null) {
            return false
        }
        val server = source.server
        return server != null && server.isDedicatedServer
    }

    private fun isServerSupportEnabled(source: CommandSourceStack): Boolean {
        if (!DlcPathConfig.isServerSupportOnServer()) {
            sendFailure(source, Component.translatable("dlc_manager.server.command_disabled"))
            return false
        }
        return true
    }

    private fun execute(source: CommandSourceStack, runnable: Runnable) {
        val server = source.server
        if (server != null) {
            server.execute(runnable)
        } else {
            runnable.run()
        }
    }

    private fun sendSuccess(source: CommandSourceStack, message: Component) {
        source.sendSuccess({ message }, false)
    }

    private fun sendFailure(source: CommandSourceStack, message: Component) {
        source.sendFailure(message)
    }
}
