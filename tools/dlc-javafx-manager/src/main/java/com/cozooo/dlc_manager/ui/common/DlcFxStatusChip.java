package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import javafx.scene.control.Label;

public final class DlcFxStatusChip {
    private DlcFxStatusChip() {
    }

    public static Label create(String text, String state) {
        Label label = new Label(text == null ? "" : text);
        apply(label, state);
        return label;
    }

    public static void apply(Label label, String state) {
        if (label == null) {
            return;
        }
        DlcFxStyle.chip(label, state == null ? DlcFxStyle.CHIP_NEUTRAL : state);
        label.setStyle("-fx-text-fill: white;");
    }
}
