package com.cozooo.dlc_manager.client.screen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiSaveIconsTest {
    @TempDir Path directory;

    private Path icon(String name, int size) throws Exception {
        Path file = directory.resolve(name).resolve("icon.png");
        Files.createDirectories(file.getParent());
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        Files.write(file, bytes.toByteArray());
        return file;
    }

    @Test void readsTheSaveIconAndSnapshotIconWithoutChangingThem() throws Exception {
        Path source = icon("save", 64);
        Path snapshot = icon("snapshot/save", 64);
        Path root = directory.toRealPath();
        assertArrayEquals(Files.readAllBytes(source), DlcUiSaveIcons.readIconBytes(root, Path.of("save")));
        assertArrayEquals(Files.readAllBytes(snapshot), DlcUiSaveIcons.readIconBytes(root, Path.of("snapshot/save")));
    }

    @Test void missingMalformedWrongSizeAndOversizedIconsUseThePlaceholder() throws Exception {
        Path root = directory.toRealPath();
        assertNull(DlcUiSaveIcons.readIconBytes(root, Path.of("missing")));
        Path malformed = icon("malformed", 64);
        Files.writeString(malformed, "not an image");
        assertNull(DlcUiSaveIcons.readIconBytes(root, Path.of("malformed")));
        icon("wrong-size", 128);
        assertNull(DlcUiSaveIcons.readIconBytes(root, Path.of("wrong-size")));
        Path oversized = icon("large", 64);
        Files.write(oversized, new byte[256 * 1024 + 1]);
        assertNull(DlcUiSaveIcons.readIconBytes(root, Path.of("large")));
    }

    @Test void doesNotReadOutsideTheSaveRootOrFollowSymbolicLinks() throws Exception {
        Path original = icon("outside", 64);
        Path root = directory.toRealPath();
        Files.createDirectories(root.resolve("saves"));
        assertNull(DlcUiSaveIcons.readIconBytes(root.resolve("saves"), Path.of("../outside")));
        Files.createDirectories(root.resolve("link"));
        Files.createSymbolicLink(root.resolve("link/icon.png"), original.toRealPath());
        assertNull(DlcUiSaveIcons.readIconBytes(root, Path.of("link")));
        Files.createSymbolicLink(root.resolve("linked-save"), original.getParent().toRealPath());
        assertNull(DlcUiSaveIcons.readIconBytes(root, Path.of("linked-save")));
    }
}
