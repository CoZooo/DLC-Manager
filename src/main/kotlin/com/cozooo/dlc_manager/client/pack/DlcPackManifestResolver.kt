package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import java.io.IOException
import java.nio.file.Path

object DlcPackManifestResolver {
    @JvmStatic @Throws(IOException::class, InterruptedException::class)
    fun resolveManifestTargets(pack: DlcPackPackage): List<DlcPackInstallPlan.FileEntry> = if (pack.format == DlcPackPackage.Format.MODRINTH) DlcPackModrinthManifest.resolveManifestTargets(pack) else DlcPackCFResolver.resolveManifestTargets(pack)
    @JvmStatic @Throws(IOException::class, InterruptedException::class)
    fun downloadManifestFiles(pack: DlcPackPackage, staging: Path, packDir: Path, cancel: DlcDownloadService.CancelChecker?, progress: DlcPackInstaller.ProgressListener?): List<DlcPackInstallPlan.FileEntry> =
        if (pack.format == DlcPackPackage.Format.MODRINTH) DlcPackModrinthManifest.downloadManifestFiles(pack, staging, packDir, cancel, progress) else DlcPackCFResolver.downloadManifestFiles(pack, staging, packDir, cancel, progress)
}
