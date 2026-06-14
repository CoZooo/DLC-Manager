package com.cozooo.dlc_manager.server

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

object DlcServerCompat {
    @JvmStatic
    fun executeCommand(server: MinecraftServer?, command: String?): Boolean {
        if (server == null || command.isNullOrBlank()) {
            return false
        }
        val source = server.createCommandSourceStack()
        server.commands.performPrefixedCommand(source, command)
        return true
    }

    @JvmStatic
    fun setStatusDescription(server: MinecraftServer?, description: Component?): Boolean {
        if (server == null || description == null) {
            return false
        }
        server.motd = description.string
        return true
    }
}
