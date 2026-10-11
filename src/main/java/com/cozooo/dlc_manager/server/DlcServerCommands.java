package com.cozooo.dlc_manager.server;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;
//? if >=26.1 {
/*import net.minecraft.server.permissions.Permissions;
*///?}

public final class DlcServerCommands {
    private DlcServerCommands() {
    }

    public static void register() {
        //? if >=1.19.2 {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("dlcmanager")
                .then(Commands.literal("server")
                    .requires(DlcServerCommands::hasPermission)
                    .executes(ctx -> DlcServerCommandHandlers.handleServerManager(ctx.getSource()))
                    .then(Commands.literal("update")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerUpdate(ctx.getSource())))
                    .then(Commands.literal("updatemanager")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerUpdateManager(ctx.getSource())))
                    .then(Commands.literal("selfupdate")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerSelfUpdate(ctx.getSource())))
                )
        ));
        //?} else {
        /*CommandRegistrationEvent.EVENT.register((dispatcher, environment) -> dispatcher.register(
            Commands.literal("dlcmanager")
                .then(Commands.literal("server")
                    .requires(DlcServerCommands::hasPermission)
                    .executes(ctx -> DlcServerCommandHandlers.handleServerManager(ctx.getSource()))
                    .then(Commands.literal("update")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerUpdate(ctx.getSource())))
                    .then(Commands.literal("updatemanager")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerUpdateManager(ctx.getSource())))
                    .then(Commands.literal("selfupdate")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerSelfUpdate(ctx.getSource())))
                )
        ));
        *///?}
    }
    private static boolean hasPermission(net.minecraft.commands.CommandSourceStack source) {
        //? if >=26.1 {
        /*return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return source.hasPermission(2);
        //?}
    }

}
