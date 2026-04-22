package com.cozooo.dlc_manager.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;

final class DlcNbtCompat {
    private static final Method MODERN_READ_COMPRESSED = findMethod("readCompressed", Path.class, loadClass("net.minecraft.nbt.NbtAccounter"));
    private static final Method LEGACY_READ_COMPRESSED = findMethod("readCompressed", java.io.File.class);
    private static final Method UNLIMITED_HEAP = findMethod(loadClass("net.minecraft.nbt.NbtAccounter"), "unlimitedHeap");
    private static final Method MODERN_WRITE_COMPRESSED = findMethod("writeCompressed", CompoundTag.class, Path.class);
    private static final Method LEGACY_WRITE_COMPRESSED = findMethod("writeCompressed", CompoundTag.class, java.io.File.class);

    private DlcNbtCompat() {
    }

    static CompoundTag readCompressed(Path path) throws IOException {
        try {
            if (MODERN_READ_COMPRESSED != null && UNLIMITED_HEAP != null) {
                Object accounter = UNLIMITED_HEAP.invoke(null);
                Object value = MODERN_READ_COMPRESSED.invoke(null, path, accounter);
                if (value instanceof CompoundTag tag) {
                    return tag;
                }
            }
            if (LEGACY_READ_COMPRESSED != null) {
                Object value = LEGACY_READ_COMPRESSED.invoke(null, path.toFile());
                if (value instanceof CompoundTag tag) {
                    return tag;
                }
            }
            throw new IOException("No compatible NBT read method found");
        } catch (ReflectiveOperationException ex) {
            throw new IOException("Failed to read compatible NBT", ex);
        }
    }

    static void writeCompressed(CompoundTag root, Path path) throws IOException {
        try {
            if (MODERN_WRITE_COMPRESSED != null) {
                MODERN_WRITE_COMPRESSED.invoke(null, root, path);
                return;
            }
            if (LEGACY_WRITE_COMPRESSED != null) {
                LEGACY_WRITE_COMPRESSED.invoke(null, root, path.toFile());
                return;
            }
            throw new IOException("No compatible NBT write method found");
        } catch (ReflectiveOperationException ex) {
            throw new IOException("Failed to write compatible NBT", ex);
        }
    }

    private static Method findMethod(String name, Class<?>... parameterTypes) {
        return findMethod(NbtIo.class, name, parameterTypes);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
