package com.cozooo.dlc_manager.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

public final class DlcServerCompat {
    private static final Method PERFORM_PREFIXED_COMMAND = findMethod("net.minecraft.commands.Commands", "performPrefixedCommand", CommandSourceStack.class, String.class);
    private static final Method PERFORM_COMMAND = findMethod("net.minecraft.commands.Commands", "performCommand", CommandSourceStack.class, String.class);
    private static final Method SERVER_STATUS_SET_DESCRIPTION = findMethod("net.minecraft.network.protocol.status.ServerStatus", "setDescription", Component.class);

    private DlcServerCompat() {
    }

    public static boolean executeCommand(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) {
            return false;
        }
        try {
            CommandSourceStack source = server.createCommandSourceStack();
            Object commands = server.getCommands();
            if (PERFORM_PREFIXED_COMMAND != null) {
                PERFORM_PREFIXED_COMMAND.invoke(commands, source, command);
                return true;
            }
            if (PERFORM_COMMAND != null) {
                PERFORM_COMMAND.invoke(commands, source, command);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    public static boolean setStatusDescription(MinecraftServer server, Component description) {
        if (server == null || description == null) {
            return false;
        }
        try {
            Object status = server.getStatus();
            if (status == null || SERVER_STATUS_SET_DESCRIPTION == null) {
                return false;
            }
            SERVER_STATUS_SET_DESCRIPTION.invoke(status, description);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Method findMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Class<?> type = Class.forName(className);
            return type.getMethod(methodName, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
