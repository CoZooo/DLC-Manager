package com.cozooo.dlc_manager.client.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcDownloadUtilTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsAndDecodesFileNameFromUrl() {
        assertEquals("hello world.jar", DlcDownloadUtil.extractFileNameFromUrl("https://example.com/files/hello%20world.jar?download=1"));
        assertNull(DlcDownloadUtil.extractFileNameFromUrl("https://example.com/files/"));
        assertNull(DlcDownloadUtil.extractFileNameFromUrl("not a url"));
    }

    @Test
    void sanitizesLikelyFileNamesAndRejectsInvalidNames() {
        assertEquals("mod.jar", DlcDownloadUtil.sanitizeFileName("mod.jar?x=1#hash"));
        assertNull(DlcDownloadUtil.sanitizeFileName("no-extension"));
        assertNull(DlcDownloadUtil.sanitizeFileName("archive.verylongextension"));
        assertTrue(DlcDownloadUtil.isLikelyFileName("config.toml"));
        assertTrue(!DlcDownloadUtil.isLikelyFileName("folder/name.jar"));
    }

    @Test
    void decodesOnlyValidPercentEscapes() {
        assertEquals("a b.jar", DlcDownloadUtil.decodeUrlPathSegment("a%20b.jar"));
        assertEquals("a%zzb.jar", DlcDownloadUtil.decodeUrlPathSegment("a%zzb.jar"));
        assertEquals("", DlcDownloadUtil.decodeUrlPathSegment(""));
    }

    @Test
    void computesHashesAndNormalizesHashText() throws Exception {
        Path file = tempDir.resolve("hash.txt");
        Files.writeString(file, "hello");

        assertEquals("5d41402abc4b2a76b9719d911017c592", DlcDownloadUtil.computeHashHex(file, "MD5"));
        assertEquals("XUFAKrxLKna5cZ2REBfFkg==", DlcDownloadUtil.computeMd5Base64(file));
        assertEquals("abc", DlcDownloadUtil.normalizeHash(" \"abc\" "));
        assertNull(DlcDownloadUtil.computeHashHex(file, "missing-algorithm"));
        assertNull(DlcDownloadUtil.computeHashHex(null, "MD5"));
    }
}
