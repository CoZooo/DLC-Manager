package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.download.DlcLightHash;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class DlcUpdateFileVerifier {
    private DlcUpdateFileVerifier() {
    }

    public record Download(Path config, Path target, long size, String hash, boolean required) {
    }

    public static void verify(Path dlcRoot, Collection<Download> downloads) throws IOException {
        for (Download download : downloads) {
            Path resolved;
            if (download.required()) {
                DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(download.config(), dlcRoot);
                resolved = entry == null ? null : entry.resolveRequiredPath(entry.getResolvedFileName());
            } else {
                DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(download.config(), dlcRoot);
                resolved = entry == null ? null : entry.getTargetPath();
            }
            if (resolved == null || !resolved.toAbsolutePath().normalize()
                    .equals(download.target().toAbsolutePath().normalize())) {
                throw new IOException("download does not match DLC config: " + download.config());
            }
            Path target = download.target();
            if (!Files.isRegularFile(target) || Files.size(target) != download.size()
                    || !download.hash().equals(DlcLightHash.compute(target))) {
                throw new IOException("downloaded DLC file failed final verification: " + download.target().getFileName());
            }
        }
    }
}
