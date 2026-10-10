package com.cozooo.dlc_manager.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class DlcTextCompat {
    private DlcTextCompat() {
    }

    public static MutableComponent literal(String value) {
        //? if >=1.19.2 {
        return Component.literal(value);
        //?} else {
        /*return construct("net.minecraft.network.chat.TextComponent", new Class<?>[] {String.class}, value);
        *///?}
    }

    public static MutableComponent translatable(String key, Object... args) {
        //? if >=1.19.2 {
        return Component.translatable(key, args);
        //?} else {
        /*return construct("net.minecraft.network.chat.TranslatableComponent", new Class<?>[] {String.class, Object[].class}, key, args);
        *///?}
    }

    public static MutableComponent empty() {
        return literal("");
    }

    private static MutableComponent construct(String name, Class<?>[] types, Object... args) {
        try {
            return (MutableComponent) Class.forName(name).getConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
