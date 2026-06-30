package com.cozooo.dlc_manager.app;

import javafx.application.Application;

public final class DlcFxLauncher {

    private DlcFxLauncher() {
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        Application.launch(DlcFxManagerApp.class, args);
    }
}
