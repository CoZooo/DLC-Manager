package com.cozooo.dlc_manager.server

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

object DlcServerCompat {
    @JvmStatic
    fun executeCommand(server: MinecraftServer?, command: String?): Boolean {
        if (server == null || command == null || command.isBlank()) return false
        return try {
            val commands = server.commands
            val source = server.createCommandSourceStack()
            val dispatcher = commands.javaClass.getMethod("getDispatcher").invoke(commands)
            val parseResults = dispatcher.javaClass.methods.firstOrNull { it.name == "parse" && it.parameterTypes.size == 2 }
                ?.invoke(dispatcher, command, source)
            for (name in arrayOf("performPrefixedCommand", "performCommand")) {
                for (method in commands.javaClass.methods.filter { it.name == name }) {
                    val types = method.parameterTypes
                    if (types.size == 2 && types[0].isInstance(source) && types[1] == String::class.java) {
                        method.invoke(commands, source, command)
                        return true
                    }
                    if (types.size == 1 && parseResults != null && types[0].isInstance(parseResults)) {
                        method.invoke(commands, parseResults)
                        return true
                    }
                }
            }
            false
        } catch (_: ReflectiveOperationException) {
            false
        }
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
