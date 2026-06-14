package com.cozooo.dlc_manager.world

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import java.io.IOException
import java.nio.file.Path

internal object DlcNbtCompat {
    @JvmStatic
    @Throws(IOException::class)
    fun readCompressed(path: Path): CompoundTag {
        return NbtIo.readCompressed(path.toFile())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeCompressed(root: CompoundTag, path: Path) {
        NbtIo.writeCompressed(root, path.toFile())
    }
}
