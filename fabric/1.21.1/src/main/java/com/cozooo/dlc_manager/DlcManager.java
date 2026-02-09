package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap;
import com.cozooo.dlc_manager.platform.FabricPlatformServices;
import com.cozooo.dlc_manager.server.DlcServerCommandHandlers;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

public class DlcManager implements ModInitializer {
    public static final String MOD_ID = "dlc_manager";

    @Override
    public void onInitialize() {
        FabricPlatformServices.init();
        DlcBootstrap.initializeCore();
        DlcWorldSync.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
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
