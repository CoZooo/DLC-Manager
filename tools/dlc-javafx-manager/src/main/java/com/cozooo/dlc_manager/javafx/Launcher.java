package com.cozooo.dlc_manager.javafx;

import javafx.application.Application;

/**
 * Non-JavaFX launcher class.
 * <p>
 * When the main class extends {@link Application}, the JavaFX runtime refuses
 * to start from a non-modular jar (the classic "Missing JavaFX runtime
 * components" error).  This launcher avoids that check by not subclassing
 * {@code Application} itself; it delegates to the explicit
 * {@link Application#launch(Class, String[])} overload, passing
 * {@link DlcManagerApp} as the Application subclass.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(DlcManagerApp.class, args);
    }
}
