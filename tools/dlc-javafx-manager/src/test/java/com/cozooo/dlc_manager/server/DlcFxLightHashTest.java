package com.cozooo.dlc_manager.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxLightHashTest {
    @TempDir
    Path temp;

    @Test
    void smallFilesAreHashedCompletely() throws Exception {
        Path left = Files.writeString(temp.resolve("a.dc"), "identifier = \"a\"\n");
        Path right = Files.writeString(temp.resolve("b.dc"), "identifier = \"b\"\n");
        Path copy = Files.writeString(temp.resolve("c.dc"), "identifier = \"a\"\n");

        assertTrue(DlcFxLightHash.compute(left).startsWith("lh1-"));
        assertEquals(DlcFxLightHash.compute(left), DlcFxLightHash.compute(copy));
        assertNotEquals(DlcFxLightHash.compute(left), DlcFxLightHash.compute(right));
    }

    @Test
    void largeFilesUseSizeHeadAndTail() throws Exception {
        byte[] data = new byte[DlcFxLightHash.SEGMENT * 4];
        Arrays.fill(data, (byte) 7);
        Path base = Files.write(temp.resolve("base.jar"), data);

        byte[] middle = data.clone();
        middle[DlcFxLightHash.SEGMENT * 2] = 1;
        byte[] tail = data.clone();
        tail[tail.length - 1] = 1;
        byte[] longer = Arrays.copyOf(data, data.length + 1);
        longer[longer.length - 1] = 7;

        String baseHash = DlcFxLightHash.compute(base);
        assertEquals(baseHash, DlcFxLightHash.compute(Files.write(temp.resolve("middle.jar"), middle)));
        assertNotEquals(baseHash, DlcFxLightHash.compute(Files.write(temp.resolve("tail.jar"), tail)));
        assertNotEquals(baseHash, DlcFxLightHash.compute(Files.write(temp.resolve("longer.jar"), longer)));
    }

    @Test
    void cacheInvalidatesOnSizeOrMtimeAndPersistsAsDc() throws Exception {
        Path cachePath = temp.resolve("tmp/dlc_sync/hash_cache.dc");
        Path file = Files.writeString(temp.resolve("mod.jar"), "one");
        DlcFxLightHash.Cache cache = DlcFxLightHash.Cache.load(cachePath);
        String first = cache.local("remote/mod.dc", file);
        cache.putServer("host:1|remote/mod.jar", 3L, 1000L, first);
        assertTrue(cache.save());
        assertTrue(Files.readString(cachePath).contains("hash"));

        DlcFxLightHash.Cache reloaded = DlcFxLightHash.Cache.load(cachePath);
        assertEquals(first, reloaded.server("host:1|remote/mod.jar", 3L, 1000L));
        assertNull(reloaded.server("host:1|remote/mod.jar", 3L, 2000L));
        assertNull(reloaded.server("host:1|remote/mod.jar", 4L, 1000L));

        Files.writeString(file, "two");
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000L));
        assertNotEquals(first, reloaded.local("remote/mod.dc", file));
    }
}