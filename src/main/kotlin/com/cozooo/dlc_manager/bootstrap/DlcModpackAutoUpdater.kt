package com.cozooo.dlc_manager.bootstrap

import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.DlcConfig
import java.util.function.Consumer

object DlcModpackAutoUpdater {
    @JvmStatic
    fun runIfConfigured(server: Boolean, log: Consumer<String>?): Boolean {
        if (!DlcConfig.isModpackAutoUpdateOn() && !DlcConfig.isModpackForceUpdateOn()) return false
        val output = log ?: Consumer { }
        val side = if (server) DlcPackReleaseUpdateService.Side.SERVER else DlcPackReleaseUpdateService.Side.CLIENT
        val root = DlcConfig.getDlcRoot()
        val version = DlcUpdateVersion.load(root)
        val currentBase = version.getBase() ?: "1.0.0"
        val currentDlc = version.getDlc() ?: "1.0.0"
        val result = try {
            DlcPackReleaseUpdateService.checkForUpdates(
                DlcConfig.getModpackUrl(),
                DlcConfig.getUpdateGitRepoUrl(),
                currentBase,
                currentDlc,
                side,
            )
        } catch (exception: Exception) {
            output.accept("Modpack auto-update check failed: ${exception.message ?: exception.javaClass.simpleName}")
            return false
        }
        if (!result.error.isNullOrBlank()) {
            output.accept("Modpack auto-update check failed: ${result.error}")
            return false
        }
        if (!result.hasUpdate()) return false
        if (result.steps.isEmpty() || !result.canDownload()) {
            output.accept("Modpack update skipped because no complete downloadable update chain is available")
            return false
        }
        output.accept("Modpack update available: ${result.currentBase}+${result.currentDlc} -> ${result.downloadBase}+${result.downloadDlc}")
        val downloaded = DlcPackReleaseUpdateService.downloadLocalPackages(result, side, null, null)
        if (!downloaded.success) {
            output.accept("Modpack auto-update download failed: ${downloaded.message ?: "failed"}")
            return false
        }
        val local = DlcUpdateService.checkForUpdates(true)
        if (!local.hasUpdates()) {
            output.accept("Modpack auto-update package was downloaded but could not be applied")
            return false
        }
        val applied = DlcUpdateService.applyUpdates(local.updates, false, null)
        if (applied == null || !applied.isSuccess) {
            output.accept("Modpack auto-update apply failed: ${applied?.message ?: "failed"}")
            return false
        }
        output.accept(if (applied.isPendingUpdate) "Modpack update scheduled; restart is required" else "Modpack auto-update completed")
        return true
    }
}
