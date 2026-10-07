package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.download.DlcLightHash;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcUpdateFileVerifierTest {
    @TempDir
    Path dlcRoot;

    @Test
    void acceptsRemoteFileResolvedBySharedDcLoader() throws Exception {
        Path config = config(false, "library.jar");
        Path target = file("mods/library.jar", "downloaded");

        assertDoesNotThrow(() -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download(config, target, false))));
    }

    @Test
    void acceptsRequiredFileResolvedBySharedDcLoader() throws Exception {
        Path config = config(true, "library.jar");
        Path target = file("required/library.jar", "downloaded");

        assertDoesNotThrow(() -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download(config, target, true))));
    }

    @Test
    void rejectsConfigPointingToDifferentFile() throws Exception {
        Path config = config(false, "other.jar");
        Path target = file("mods/library.jar", "downloaded");
        DlcUpdateFileVerifier.Download download = download(config, target, false);

        assertThrows(IOException.class, () -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download)));
    }

    @Test
    void rejectsSameSizeContentChange() throws Exception {
        Path config = config(false, "library.jar");
        Path target = file("mods/library.jar", "new");
        DlcUpdateFileVerifier.Download download = download(config, target, false);
        Files.writeString(target, "old");

        assertThrows(IOException.class, () -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download)));
    }

    @Test
    void rejectsChangedFileSize() throws Exception {
        Path config = config(true, "library.jar");
        Path target = file("required/library.jar", "downloaded");
        DlcUpdateFileVerifier.Download download = download(config, target, true);
        Files.writeString(target, "short");

        assertThrows(IOException.class, () -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download)));
    }

    @Test
    void stagedDownloadDoesNotValidateOldTarget() throws Exception {
        Path config = config(false, "library.jar");
        Path target = file("mods/library.jar", "old");
        Path staged = file("mods/library.jar.part", "new");
        DlcUpdateFileVerifier.Download download = new DlcUpdateFileVerifier.Download(
                config, target, Files.size(staged), DlcLightHash.compute(staged), false);

        assertThrows(IOException.class, () -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download)));
    }

    @Test
    void stagedDownloadDoesNotValidateMissingTarget() throws Exception {
        Path config = config(true, "library.jar");
        Path staged = file("required/library.jar.part", "downloaded");
        DlcUpdateFileVerifier.Download download = new DlcUpdateFileVerifier.Download(
                config, dlcRoot.resolve("required/library.jar"), Files.size(staged), DlcLightHash.compute(staged), true);

        assertThrows(IOException.class, () -> DlcUpdateFileVerifier.verify(dlcRoot, List.of(download)));
    }

    private Path config(boolean required, String fileName) {
        Path config = dlcRoot.resolve(required ? "required/library.dc" : "remote/mods/library.dc");
        JsonObject metadata = new JsonObject();
        metadata.addProperty("identifier", "library");
        metadata.addProperty("file_name", fileName);
        metadata.addProperty("type", "mod");
        assertTrue(DlcDcFormatUtil.write(config, metadata));
        return config;
    }

    private Path file(String relative, String content) throws IOException {
        Path file = dlcRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private DlcUpdateFileVerifier.Download download(Path config, Path target, boolean required) throws IOException {
        return new DlcUpdateFileVerifier.Download(config, target, Files.size(target), DlcLightHash.compute(target), required);
    }
}
