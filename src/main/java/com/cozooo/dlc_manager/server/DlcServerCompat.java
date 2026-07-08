package com.cozooo.dlc_manager.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class DlcServerCompat {
    private DlcServerCompat() {
    }

    public static boolean executeCommand(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) {
            return false;
        }
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
        return true;
    }

    public static boolean setStatusDescription(MinecraftServer server, Component description) {
        if (server == null || description == null) {
            return false;
        }
        server.setMotd(description.getString());
        return true;
    }
}
