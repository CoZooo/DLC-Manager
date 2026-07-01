package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class DlcFxActionPanel {
    private DlcFxActionPanel() {
    }

    public static HBox row(Node... nodes) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        if (nodes != null) {
            row.getChildren().addAll(nodes);
        }
        return row;
    }

    public static VBox panel(Node... rows) {
        VBox panel = new VBox(8);
        if (rows != null) {
            panel.getChildren().addAll(rows);
        }
        DlcFxStyle.actionPanel(panel);
        return panel;
    }

    public static HBox statusRow(ProgressBar progressBar, Label progressLabel, Label statusLabel) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        if (progressBar != null) {
            row.getChildren().add(progressBar);
        }
        if (progressLabel != null) {
            row.getChildren().add(progressLabel);
        }
        if (statusLabel != null) {
            row.getChildren().add(statusLabel);
            HBox.setHgrow(statusLabel, Priority.ALWAYS);
            DlcFxStyle.routeStatus(statusLabel);
        }
        return row;
    }

    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
