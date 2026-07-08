package com.cozooo.dlc_manager.world;

import net.minecraft.nbt.CompoundTag;
//? if >=1.21.1 {
/*import net.minecraft.nbt.NbtAccounter;
*///?}
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Path;

final class DlcNbtCompat {
    private DlcNbtCompat() {
    }

    static CompoundTag readCompressed(Path path) throws IOException {
        //? if >=1.21.1 {
        /*return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        *///?} else {
        return NbtIo.readCompressed(path.toFile());
        //?}
    }

    static void writeCompressed(CompoundTag root, Path path) throws IOException {
        //? if >=1.21.1 {
        /*NbtIo.writeCompressed(root, path);
        *///?} else {
        NbtIo.writeCompressed(root, path.toFile());
        //?}
    }
}
