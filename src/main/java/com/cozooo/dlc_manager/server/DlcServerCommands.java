package com.cozooo.dlc_manager.server;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;

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
    private static boolean hasPermission(Object source) {
        try {
            Object value = source.getClass().getMethod("hasPermission", int.class).invoke(source, 2);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

}
