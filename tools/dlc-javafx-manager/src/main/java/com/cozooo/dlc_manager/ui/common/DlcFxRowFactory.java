package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class DlcFxRowFactory {
    private static final double CHECK_SLOT_WIDTH = 28;

    private DlcFxRowFactory() {
    }

    public static StackPane checkSlot(CheckBox checkBox, boolean visible) {
        StackPane slot = new StackPane();
        slot.setMinWidth(CHECK_SLOT_WIDTH);
        slot.setPrefWidth(CHECK_SLOT_WIDTH);
        slot.setMaxWidth(CHECK_SLOT_WIDTH);
        if (checkBox != null) {
            checkBox.setVisible(visible);
            checkBox.setManaged(visible);
            slot.getChildren().add(checkBox);
        }
        return slot;
    }

    public static HBox standard(CheckBox checkBox, boolean showCheckBox, Label statusChip, Node main, Node right) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(checkSlot(checkBox, showCheckBox));
        if (statusChip != null) {
            row.getChildren().add(statusChip);
        }
        if (main != null) {
            row.getChildren().add(main);
            HBox.setHgrow(main, Priority.ALWAYS);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        if (right != null) {
            row.getChildren().add(right);
        }
        return row;
    }

    public static VBox titleSubtitle(String title, String subtitle) {
        Label titleLabel = new Label(title == null ? "" : title);
        Label subtitleLabel = new Label(subtitle == null ? "" : subtitle);
        DlcFxStyle.title(titleLabel);
        DlcFxStyle.muted(subtitleLabel);
        subtitleLabel.setWrapText(true);
        VBox box = new VBox(2, titleLabel, subtitleLabel);
        return box;
    }
}
