package com.cozooo.dlc_manager.server

import dev.architectury.event.events.common.CommandRegistrationEvent
import net.minecraft.commands.Commands
//? if >=26.1 {
/*import net.minecraft.server.permissions.Permissions
*///?}

object DlcServerCommands {
    @JvmStatic
    fun register() {
        //? if >=1.19.2 {
        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("dlcmanager")
                    .then(
                        Commands.literal("server")
                            .requires { source ->
                                //? if >=26.1 {
                                /*source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                                *///?}
                                //? if <26.1 {
                                source.hasPermission(2)
                                //?}
                            }
                            .executes { ctx -> DlcServerCommandHandlers.handleServerManager(ctx.source) }
                            .then(
                                Commands.literal("update")
                                    .executes { ctx -> DlcServerCommandHandlers.handleServerUpdate(ctx.source) }
                            )
                            .then(
                                Commands.literal("updatemanager")
                                    .executes { ctx -> DlcServerCommandHandlers.handleServerUpdateManager(ctx.source) }
                            )
                            .then(
                                Commands.literal("selfupdate")
                                    .executes { ctx -> DlcServerCommandHandlers.handleServerSelfUpdate(ctx.source) }
                            )
                    )
            )
        }
        //?} else {
        /*CommandRegistrationEvent.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                Commands.literal("dlcmanager")
                    .then(
                        Commands.literal("server")
                            .requires { source ->
                                //? if >=26.1 {
                                /*source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                                *///?}
                                //? if <26.1 {
                                source.hasPermission(2)
                                //?}
                            }
                            .executes { ctx -> DlcServerCommandHandlers.handleServerManager(ctx.source) }
                    )
            )
        }
        *///?}
    }
}
