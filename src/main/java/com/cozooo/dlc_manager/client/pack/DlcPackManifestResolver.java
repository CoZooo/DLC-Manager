package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class DlcPackManifestResolver {
    private DlcPackManifestResolver() {}
    public static List<DlcPackInstallPlan.FileEntry> resolveManifestTargets(DlcPackPackage pack) throws IOException, InterruptedException {
        return pack.format == DlcPackPackage.Format.MODRINTH ? DlcPackModrinthManifest.resolveManifestTargets(pack) : DlcPackCFResolver.resolveManifestTargets(pack);
    }
    public static List<DlcPackInstallPlan.FileEntry> downloadManifestFiles(DlcPackPackage pack, Path staging, Path packDir,
        DlcDownloadService.CancelChecker cancel, DlcPackInstaller.ProgressListener progress) throws IOException, InterruptedException {
        return pack.format == DlcPackPackage.Format.MODRINTH ? DlcPackModrinthManifest.downloadManifestFiles(pack, staging, packDir, cancel, progress)
            : DlcPackCFResolver.downloadManifestFiles(pack, staging, packDir, cancel, progress);
    }
}
