package com.cozooo.dlc_manager.world

import net.minecraft.nbt.CompoundTag
//? if >=1.21.1 {
/*import net.minecraft.nbt.NbtAccounter
*///?}
import net.minecraft.nbt.NbtIo
import java.io.IOException
import java.nio.file.Path

internal object DlcNbtCompat {
    @JvmStatic
    @Throws(IOException::class)
    fun readCompressed(path: Path): CompoundTag {
        //? if >=1.21.1 {
        /*return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
        *///?} else {
        return NbtIo.readCompressed(path.toFile())
        //?}
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeCompressed(root: CompoundTag, path: Path) {
        //? if >=1.21.1 {
        /*NbtIo.writeCompressed(root, path)
        *///?} else {
        NbtIo.writeCompressed(root, path.toFile())
        //?}
    }
}
