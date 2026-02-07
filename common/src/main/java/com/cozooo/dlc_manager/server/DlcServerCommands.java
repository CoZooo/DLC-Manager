package com.cozooo.dlc_manager.server;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;

public final class DlcServerCommands {
    private DlcServerCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("dlcmanager")
                .then(Commands.literal("server")
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> DlcServerCommandHandlers.handleServerManager(ctx.getSource()))
                    .then(Commands.literal("update")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerUpdate(ctx.getSource())))
                    .then(Commands.literal("updatemanager")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerUpdateManager(ctx.getSource())))
                    .then(Commands.literal("selfupdate")
                        .executes(ctx -> DlcServerCommandHandlers.handleServerSelfUpdate(ctx.getSource())))
                )
        ));
    }
}
