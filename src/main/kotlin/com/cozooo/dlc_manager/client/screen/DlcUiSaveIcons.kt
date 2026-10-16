package com.cozooo.dlc_manager.client.screen

import com.mojang.blaze3d.platform.NativeImage
import com.sighs.apricityui.init.Element
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture

class DlcUiSaveIcons {
    private val entries = mutableMapOf<Path, Entry>()
    private val generation = AtomicLong()

    fun bind(element: Element, root: Path, relative: Path) {
        element.setAttribute("src", "minecraft:textures/misc/unknown_server.png")
        val key = root.toAbsolutePath().normalize().resolve(relative).normalize()
        val entry = entries.getOrPut(key) {
            Entry().also { target ->
                val request = generation.get()
                WORKER.execute { load(root, relative, target, request) }
            }
        }
        if (entry.location != null) element.setAttribute("src", entry.location.toString())
        else if (!entry.complete) {
            entry.targets.removeIf { !it.isConnected }
            entry.targets.add(element)
        }
    }

    private fun load(root: Path, relative: Path, entry: Entry, request: Long) {
        if (generation.get() != request) return
        val image = try {
            readIconBytes(root, relative)?.let { NativeImage.read(ByteArrayInputStream(it)) }
        } catch (_: Exception) { null }
        Minecraft.getInstance().execute { apply(entry, image, request) }
    }

    private fun apply(entry: Entry, image: NativeImage?, request: Long) {
        if (generation.get() != request) {
            image?.close()
            return
        }
        entry.complete = true
        if (image != null) {
            var texture: DynamicTexture? = null
            try {
                val name = "dlc_save_icon/${SERIAL.incrementAndGet()}"
                texture = DlcScreenCompat.createDynamicTexture(name, image)
                entry.location = DlcScreenCompat.registerTexture(Minecraft.getInstance().textureManager, name, texture)
            } catch (_: Exception) {
                if (texture != null) texture.close() else image.close()
            }
        }
        entry.location?.let { location ->
            for (target in entry.targets) if (target.isConnected) target.setAttribute("src", location.toString())
        }
        entry.targets.clear()
    }

    fun clear() {
        generation.incrementAndGet()
        for (entry in entries.values) {
            entry.targets.clear()
            entry.location?.let { DlcScreenCompat.releaseTexture(Minecraft.getInstance().textureManager, it) }
        }
        entries.clear()
    }

    private class Entry {
        val targets = mutableListOf<Element>()
        var location: Any? = null
        var complete = false
    }

    companion object {
        private const val MAX_BYTES = 256 * 1024
        private val PNG_SIGNATURE = java.lang.Long.parseUnsignedLong("89504e470d0a1a0a", 16)
        private val SERIAL = AtomicLong()
        private val WORKER = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "dlc-save-icons").apply { isDaemon = true }
        }

        @JvmStatic fun readIconBytes(root: Path, relative: Path): ByteArray? {
            val base = root.toAbsolutePath().normalize()
            val icon = base.resolve(relative).resolve("icon.png").normalize()
            if (!icon.startsWith(base)) return null
            var part: Path? = icon
            while (part != null) {
                if (Files.isSymbolicLink(part)) return null
                part = part.parent
            }
            if (!Files.isRegularFile(icon, LinkOption.NOFOLLOW_LINKS) || Files.size(icon) > MAX_BYTES) return null
            val bytes = Files.newInputStream(icon).use { it.readNBytes(MAX_BYTES + 1) }
            if (bytes.size < 24 || bytes.size > MAX_BYTES) return null
            val header = ByteBuffer.wrap(bytes)
            if (header.getLong(0) != PNG_SIGNATURE || header.getInt(8) != 13 || header.getInt(12) != 0x49484452
                || header.getInt(16) != 64 || header.getInt(20) != 64) return null
            return bytes
        }
    }
}
