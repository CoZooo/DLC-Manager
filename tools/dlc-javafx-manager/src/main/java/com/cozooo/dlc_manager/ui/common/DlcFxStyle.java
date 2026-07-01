package com.cozooo.dlc_manager.ui.common;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

public final class DlcFxStyle {
    public static final String VIEW = "dlc-view";
    public static final String TOOLBAR = "dlc-toolbar";
    public static final String FILTER_BAR = "dlc-filter-bar";
    public static final String STATUS_BAR = "dlc-status-strip";
    public static final String SECTION = "dlc-section";
    public static final String SECTION_TITLE = "dlc-section-title";
    public static final String ACTION_PANEL = "dlc-action-panel";
    public static final String ACTION_PANEL_TITLE = "dlc-action-panel-title";
    public static final String PRIMARY = "primary";
    public static final String DANGER = "danger";
    public static final String MUTED = "muted";
    public static final String CHIP = "status-chip";
    public static final String CHIP_OK = "ok";
    public static final String CHIP_WARN = "warn";
    public static final String CHIP_ERROR = "error";
    public static final String CHIP_NEUTRAL = "neutral";
    private static final ReadOnlyStringWrapper STATUS_TEXT = new ReadOnlyStringWrapper("");

    private DlcFxStyle() {
    }

    public static void view(Region region) {
        add(region, VIEW);
    }

    public static void toolbar(Region region) {
        add(region, TOOLBAR);
        region.setPadding(new Insets(14, 16, 12, 16));
    }

    public static void filterBar(Region region) {
        add(region, FILTER_BAR);
        region.setPadding(new Insets(10, 12, 10, 12));
    }

    public static void section(Region region) {
        add(region, SECTION);
    }

    public static void actionPanel(Region region) {
        add(region, ACTION_PANEL);
        region.setPadding(new Insets(12, 14, 6, 14));
    }

    public static void statusStrip(Region region) {
        add(region, STATUS_BAR);
        region.setPadding(new Insets(8, 16, 8, 16));
        boolean hasVisibleNonStatusContent = registerStatusLabels(region);
        if (!hasVisibleNonStatusContent) {
            region.setManaged(false);
            region.setVisible(false);
        }
    }

    public static void routeStatus(Label label) {
        registerStatusLabels(label);
    }

    public static void title(Label label) {
        add(label, SECTION_TITLE);
    }

    public static void actionTitle(Label label) {
        add(label, ACTION_PANEL_TITLE);
    }

    public static void muted(Label label) {
        add(label, MUTED);
    }

    public static void primary(Button button) {
        add(button, PRIMARY);
    }

    public static void danger(Button button) {
        add(button, DANGER);
    }

    public static ReadOnlyStringProperty statusTextProperty() {
        return STATUS_TEXT.getReadOnlyProperty();
    }

    public static void setStatus(String text) {
        if (text != null && !text.isBlank()) {
            STATUS_TEXT.set(text);
        }
    }

    public static void chip(Label label, String state) {
        label.getStyleClass().removeAll(CHIP, CHIP_OK, CHIP_WARN, CHIP_ERROR, CHIP_NEUTRAL);
        label.getStyleClass().add(CHIP);
        if (state != null && !state.isBlank()) {
            label.getStyleClass().add(state);
        }
        label.setAlignment(Pos.CENTER);
    }

    public static void add(Node node, String styleClass) {
        if (node != null && styleClass != null && !node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    private static boolean registerStatusLabels(Node node) {
        boolean hasVisibleNonStatusContent = false;
        if (node instanceof Label label) {
            label.textProperty().addListener((obs, oldValue, newValue) -> setStatus(newValue));
            setStatus(label.getText());
            label.setManaged(false);
            label.setVisible(false);
            return false;
        }
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                hasVisibleNonStatusContent |= registerStatusLabels(child);
            }
            return hasVisibleNonStatusContent;
        }
        node.managedProperty().bind(node.visibleProperty());
        return true;
    }
}
