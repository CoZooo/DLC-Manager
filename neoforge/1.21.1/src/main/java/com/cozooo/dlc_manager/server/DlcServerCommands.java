package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.server.DlcServerCommandHandlers;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DlcServerCommands {
    private DlcServerCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
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
        );
    }
}
