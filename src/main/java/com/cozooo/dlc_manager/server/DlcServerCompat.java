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
        try {
            Object commands = server.getCommands();
            CommandSourceStack source = server.createCommandSourceStack();
            Object dispatcher = commands.getClass().getMethod("getDispatcher").invoke(commands);
            Object parseResults = null;
            for (java.lang.reflect.Method method : dispatcher.getClass().getMethods()) {
                if (method.getName().equals("parse") && method.getParameterTypes().length == 2) {
                    parseResults = method.invoke(dispatcher, command, source);
                    break;
                }
            }
            for (String name : new String[] {"performPrefixedCommand", "performCommand"}) {
                for (java.lang.reflect.Method method : commands.getClass().getMethods()) {
                    if (!method.getName().equals(name)) continue;
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length == 2 && types[0].isInstance(source) && types[1] == String.class) {
                        method.invoke(commands, source, command);
                        return true;
                    }
                    if (types.length == 1 && parseResults != null && types[0].isInstance(parseResults)) {
                        method.invoke(commands, parseResults);
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    public static boolean setStatusDescription(MinecraftServer server, Component description) {
        if (server == null || description == null) {
            return false;
        }
        server.setMotd(description.getString());
        return true;
    }
}
