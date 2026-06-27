package com.cozooo.dlc_manager.javafx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

final class DlcFxStyle {
    static final String VIEW = "dlc-view";
    static final String TOOLBAR = "dlc-toolbar";
    static final String FILTER_BAR = "dlc-filter-bar";
    static final String STATUS_BAR = "dlc-status-strip";
    static final String SECTION = "dlc-section";
    static final String SECTION_TITLE = "dlc-section-title";
    static final String PRIMARY = "primary";
    static final String DANGER = "danger";
    static final String MUTED = "muted";
    static final String CHIP = "status-chip";
    static final String CHIP_OK = "ok";
    static final String CHIP_WARN = "warn";
    static final String CHIP_ERROR = "error";
    static final String CHIP_NEUTRAL = "neutral";

    private DlcFxStyle() {
    }

    static void view(Region region) {
        add(region, VIEW);
    }

    static void toolbar(Region region) {
        add(region, TOOLBAR);
        region.setPadding(new Insets(14, 16, 12, 16));
    }

    static void filterBar(Region region) {
        add(region, FILTER_BAR);
        region.setPadding(new Insets(10, 12, 10, 12));
    }

    static void section(Region region) {
        add(region, SECTION);
    }

    static void statusStrip(Region region) {
        add(region, STATUS_BAR);
        region.setPadding(new Insets(10, 16, 10, 16));
    }

    static void title(Label label) {
        add(label, SECTION_TITLE);
    }

    static void muted(Label label) {
        add(label, MUTED);
    }

    static void primary(Button button) {
        add(button, PRIMARY);
    }

    static void danger(Button button) {
        add(button, DANGER);
    }

    static void chip(Label label, String state) {
        label.getStyleClass().removeAll(CHIP, CHIP_OK, CHIP_WARN, CHIP_ERROR, CHIP_NEUTRAL);
        label.getStyleClass().add(CHIP);
        if (state != null && !state.isBlank()) {
            label.getStyleClass().add(state);
        }
        label.setAlignment(Pos.CENTER);
    }

    static void add(Node node, String styleClass) {
        if (node != null && styleClass != null && !node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }
}
