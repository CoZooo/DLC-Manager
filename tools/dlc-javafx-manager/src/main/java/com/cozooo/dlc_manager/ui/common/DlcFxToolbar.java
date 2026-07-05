package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class DlcFxToolbar {
    private DlcFxToolbar() {
    }

    public static HBox create(Node... nodes) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        if (nodes != null) {
            box.getChildren().addAll(nodes);
        }
        DlcFxStyle.toolbar(box);
        return box;
    }

    public static HBox withRight(Node left, Node right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = create(left, spacer, right);
        return box;
    }

    public static VBox vertical(Node... rows) {
        VBox box = new VBox(8);
        if (rows != null) {
            box.getChildren().addAll(rows);
        }
        DlcFxStyle.toolbar(box);
        return box;
    }

    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
