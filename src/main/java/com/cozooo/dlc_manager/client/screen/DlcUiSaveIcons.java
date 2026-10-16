package com.cozooo.dlc_manager.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.sighs.apricityui.init.Element;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class DlcUiSaveIcons {
    private static final int MAX_BYTES = 256 * 1024;
    private static final long PNG_SIGNATURE = 0x89504e470d0a1a0aL;
    private static final AtomicLong SERIAL = new AtomicLong();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "dlc-save-icons");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Path, Entry> entries = new HashMap<>();
    private final AtomicLong generation = new AtomicLong();

    void bind(Element element, Path root, Path relative) {
        element.setAttribute("src", "minecraft:textures/misc/unknown_server.png");
        Path key = root.toAbsolutePath().normalize().resolve(relative).normalize();
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry();
            entries.put(key, entry);
            Entry target = entry;
            long request = generation.get();
            WORKER.execute(() -> load(root, relative, target, request));
        }
        if (entry.location != null) element.setAttribute("src", entry.location.toString());
        else if (!entry.complete) {
            entry.targets.removeIf(target -> !target.isConnected());
            entry.targets.add(element);
        }
    }

    private void load(Path root, Path relative, Entry entry, long request) {
        if (generation.get() != request) return;
        NativeImage image = null;
        try {
            byte[] bytes = readIconBytes(root, relative);
            if (bytes != null) image = NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Exception ignored) { }
        NativeImage loaded = image;
        Minecraft.getInstance().execute(() -> apply(entry, loaded, request));
    }

    private void apply(Entry entry, NativeImage image, long request) {
        if (generation.get() != request) {
            if (image != null) image.close();
            return;
        }
        entry.complete = true;
        if (image != null) {
            DynamicTexture texture = null;
            try {
                String name = "dlc_save_icon/" + SERIAL.incrementAndGet();
                texture = DlcScreenCompat.createDynamicTexture(name, image);
                entry.location = DlcScreenCompat.registerTexture(Minecraft.getInstance().getTextureManager(), name, texture);
            } catch (Exception ignored) {
                if (texture != null) texture.close();
                else image.close();
            }
        }
        if (entry.location != null) {
            for (Element target : entry.targets) {
                if (target.isConnected()) target.setAttribute("src", entry.location.toString());
            }
        }
        entry.targets.clear();
    }

    void clear() {
        generation.incrementAndGet();
        for (Entry entry : entries.values()) {
            entry.targets.clear();
            if (entry.location != null) DlcScreenCompat.releaseTexture(Minecraft.getInstance().getTextureManager(), entry.location);
        }
        entries.clear();
    }

    static byte[] readIconBytes(Path root, Path relative) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path icon = base.resolve(relative).resolve("icon.png").normalize();
        if (!icon.startsWith(base)) return null;
        for (Path part = icon; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) return null;
        }
        if (!Files.isRegularFile(icon, LinkOption.NOFOLLOW_LINKS) || Files.size(icon) > MAX_BYTES) return null;
        byte[] bytes;
        try (var input = Files.newInputStream(icon)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length < 24 || bytes.length > MAX_BYTES) return null;
        ByteBuffer header = ByteBuffer.wrap(bytes);
        if (header.getLong(0) != PNG_SIGNATURE || header.getInt(8) != 13 || header.getInt(12) != 0x49484452
                || header.getInt(16) != 64 || header.getInt(20) != 64) return null;
        return bytes;
    }

    private static final class Entry {
        private final List<Element> targets = new ArrayList<>();
        private Object location;
        private boolean complete;
    }
}
