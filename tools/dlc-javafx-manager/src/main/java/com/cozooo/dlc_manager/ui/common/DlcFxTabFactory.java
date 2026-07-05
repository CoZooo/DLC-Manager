package com.cozooo.dlc_manager.ui.common;

import javafx.scene.Node;
import javafx.scene.control.Tab;

public final class DlcFxTabFactory {
    private DlcFxTabFactory() {
    }

    public static Tab create(String title, Node content) {
        Tab tab = new Tab(title);
        tab.setClosable(false);
        tab.setContent(content);
        return tab;
    }
}
