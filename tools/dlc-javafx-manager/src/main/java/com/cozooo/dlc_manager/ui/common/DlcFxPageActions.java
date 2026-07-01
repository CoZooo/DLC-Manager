package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public final class DlcFxPageActions {
    private DlcFxPageActions() {
    }

    public static Button button(String text, Runnable action) {
        Button button = new Button(text);
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        return button;
    }

    public static Button primary(String text, Runnable action) {
        Button button = button(text, action);
        DlcFxStyle.primary(button);
        return button;
    }

    public static Button danger(String text, Runnable action) {
        Button button = button(text, action);
        DlcFxStyle.danger(button);
        return button;
    }

    public static HBox selectionActions(Runnable selectAll, Runnable selectNone, Runnable invert) {
        Button selectAllBtn = button(DlcFxI18n.tr("dlc_manager.local.select_all"), selectAll);
        Button selectNoneBtn = button(DlcFxI18n.tr("dlc_manager.local.select_none"), selectNone);
        Button invertBtn = button(DlcFxI18n.tr("dlc_manager.local.invert"), invert);
        HBox box = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
