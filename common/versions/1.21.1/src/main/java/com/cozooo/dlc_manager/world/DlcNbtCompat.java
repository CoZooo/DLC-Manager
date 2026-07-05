package com.cozooo.dlc_manager.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Path;

final class DlcNbtCompat {
    private DlcNbtCompat() {
    }

    static CompoundTag readCompressed(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    static void writeCompressed(CompoundTag root, Path path) throws IOException {
        NbtIo.writeCompressed(root, path);
    }
}
