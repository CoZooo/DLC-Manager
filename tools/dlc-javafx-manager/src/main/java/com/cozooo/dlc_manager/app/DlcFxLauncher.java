package com.cozooo.dlc_manager.app;

import javafx.application.Application;

/**
 * Non-JavaFX launcher class.
 * <p>
 * When the main class extends {@link Application}, the JavaFX runtime refuses
 * to start from a non-modular jar (the classic "Missing JavaFX runtime
 * components" error).  This launcher avoids that check by not subclassing
 * {@code Application} itself; it delegates to the explicit
 * {@link Application#launch(Class, String[])} overload, passing
 * {@link DlcFxManagerApp} as the Application subclass.
 */
public final class DlcFxLauncher {

    private DlcFxLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(DlcFxManagerApp.class, args);
    }
}
