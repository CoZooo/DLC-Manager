package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcConfig;
import java.util.function.Consumer;

public final class DlcModpackAutoUpdater {
    private DlcModpackAutoUpdater() {
    }

    public static boolean runIfConfigured(boolean server, Consumer<String> log) {
        if (!DlcConfig.isModpackAutoUpdateOn() && !DlcConfig.isModpackForceUpdateOn()) {
            return false;
        }
        Consumer<String> output = log == null ? ignored -> {} : log;
        DlcPackReleaseUpdateService.Side side = server
            ? DlcPackReleaseUpdateService.Side.SERVER
            : DlcPackReleaseUpdateService.Side.CLIENT;
        DlcUpdateVersion version = DlcUpdateVersion.load(DlcConfig.getDlcRoot());
        String currentBase = version.getBase() == null ? "1.0.0" : version.getBase();
        String currentDlc = version.getDlc() == null ? "1.0.0" : version.getDlc();
        DlcPackReleaseUpdateService.CheckResult result;
        try {
            result = DlcPackReleaseUpdateService.checkForUpdates(
                DlcConfig.getModpackUrl(), DlcConfig.getUpdateGitRepoUrl(), currentBase, currentDlc, side);
        } catch (Exception ex) {
            output.accept("Modpack auto-update check failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return false;
        }
        if (result.error != null && !result.error.isBlank()) {
            output.accept("Modpack auto-update check failed: " + result.error);
            return false;
        }
        if (!result.hasUpdate()) {
            return false;
        }
        if (result.steps == null || result.steps.isEmpty() || !result.canDownload()) {
            output.accept("Modpack update skipped because no complete downloadable update chain is available");
            return false;
        }
        output.accept("Modpack update available: " + result.currentBase + "+" + result.currentDlc
            + " -> " + result.downloadBase + "+" + result.downloadDlc);
        DlcPackReleaseUpdateService.DownloadResult downloaded =
            DlcPackReleaseUpdateService.downloadLocalPackages(result, side, null, null);
        if (!downloaded.success) {
            output.accept("Modpack auto-update download failed: " + (downloaded.message == null ? "failed" : downloaded.message));
            return false;
        }
        DlcUpdateService.UpdateCheckResult local = DlcUpdateService.checkForUpdates(true);
        if (!local.hasUpdates()) {
            output.accept("Modpack auto-update package was downloaded but could not be applied");
            return false;
        }
        DlcUpdateService.UpdateApplyResult applied = DlcUpdateService.applyUpdates(local.getUpdates(), false, null);
        if (applied == null || !applied.isSuccess()) {
            output.accept("Modpack auto-update apply failed: " + (applied == null || applied.getMessage() == null ? "failed" : applied.getMessage()));
            return false;
        }
        output.accept(applied.isPendingUpdate()
            ? "Modpack update scheduled; restart is required"
            : "Modpack auto-update completed");
        return true;
    }
}
