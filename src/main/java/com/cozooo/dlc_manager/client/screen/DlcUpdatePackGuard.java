package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.client.update.DlcUpdateManifest;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class DlcUpdatePackGuard {
    private DlcUpdatePackGuard() {
    }

    public static void confirmBeforeUpdate(Screen returnScreen, List<DlcUpdatePackage> packages, DecisionHandler handler) {
        if (packages == null || packages.isEmpty()) { handler.proceed(null, false); return; }
        List<String> unknown = DlcUpdateService.unknownParameters(packages);
        if (!unknown.isEmpty()) {
            Minecraft.getInstance().setScreen(new DlcUpdateCompatibilityScreen(returnScreen, unknown, action -> {
                for (DlcUpdatePackage pkg : packages) if (pkg != null) pkg.setCompatibilityAction(action);
                confirmBeforeUpdate(returnScreen, packages, handler);
            }));
            return;
        }
        TargetVersion target = resolveTargetVersion(packages);
        DlcPackUpdateGuard.CheckResult check = DlcPackUpdateGuard.check(DlcPaths.dlcRoot(), target.base, target.dlc);
        if (check == null || !check.hasInstalledPacks() || !check.hasIncompatiblePacks()) {
            proceed(returnScreen, packages, handler, check, false);
            return;
        }
        Component title = DlcTextCompat.translatable("dlc_manager.pack.manager_title");
        Component message = DlcConfirmUtil.buildPackUpdateIncompatibleConfirmMessage(check);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            if (accepted) {
                Minecraft.getInstance().setScreen(returnScreen);
                proceed(returnScreen, packages, handler, check, true);
            } else {
                Minecraft.getInstance().setScreen(returnScreen);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private static void proceed(Screen returnScreen, List<DlcUpdatePackage> packages, DecisionHandler handler, DlcPackUpdateGuard.CheckResult check, boolean uninstall) {
        if (packages != null) for (DlcUpdatePackage pkg : packages) if (pkg != null) pkg.setAllowUninstallPacks(uninstall);
        Minecraft.getInstance().setScreen(new DlcWorldBackupScreen(returnScreen, packages, () -> handler.proceed(check, uninstall)));
    }

    public static TargetVersion resolveTargetVersion(List<DlcUpdatePackage> packages) {
        String targetBase = null;
        String targetDlc = null;
        if (packages != null) {
            for (DlcUpdatePackage pkg : packages) {
                if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                    continue;
                }
                DlcUpdateManifest.VersionInfo version = pkg.getManifest().getVersion();
                String base = normalize(version.getBase());
                String dlcOld = normalize(version.getDlcOld());
                String baseNew = normalize(version.getBaseNew());
                String dlcNew = normalize(version.getDlcNew());
                targetBase = baseNew == null ? base : baseNew;
                targetDlc = dlcNew == null ? dlcOld : dlcNew;
            }
        }
        return new TargetVersion(safeVersion(targetBase), safeVersion(targetDlc));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    @FunctionalInterface
    public interface DecisionHandler {
        void proceed(DlcPackUpdateGuard.CheckResult check, boolean uninstallIncompatible);
    }

    public record TargetVersion(String base, String dlc) {
    }
}
