package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import java.io.IOException;
import java.nio.file.Path;

public final class DlcStartupRequirements {
    public static final String READY_PROPERTY = "dlc_manager.early.ready";
    public static final String FAILURE_MESSAGE = "Required resource installation failed";

    private DlcStartupRequirements() {}

    public static boolean usesEarlyInstallation() {
        String loader = DlcPlatformServices.loaderName();
        return "forge".equalsIgnoreCase(loader) || "neoforge".equalsIgnoreCase(loader);
    }

    public static void markComplete(Path game) {
        System.setProperty(READY_PROPERTY, game.toAbsolutePath().normalize().toString());
    }

    public static void verifyBeforeGameLoad() {
        if (!usesEarlyInstallation()) return;
        String game = DlcPlatformServices.gameDir().toAbsolutePath().normalize().toString();
        if (!game.equals(System.getProperty(READY_PROPERTY))) {
            System.clearProperty(READY_PROPERTY);
            throw new IllegalStateException(DlcBootstrapI18n.text("failure.summary") + " " + DlcBootstrapI18n.text("early_incomplete"));
        }
        try {
            DlcEarlyInstaller.verifyInstalled();
        } catch (IOException | RuntimeException ex) {
            System.clearProperty(READY_PROPERTY);
            throw new IllegalStateException(DlcBootstrapI18n.text("failure.summary") + " " + DlcBootstrapI18n.text("verification_failed"), ex);
        }
    }
}
