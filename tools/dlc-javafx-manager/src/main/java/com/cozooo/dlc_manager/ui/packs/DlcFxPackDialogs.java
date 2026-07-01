package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;

import javafx.scene.Scene;

public final class DlcFxPackDialogs {
    private DlcFxPackDialogs() {
    }

    public static void confirm(Scene scene, String title, String message, Runnable onConfirm) {
        DlcFxModalOverlay.show(
            scene,
            title,
            DlcFxRichTextRenderer.formatted(message),
            onConfirm,
            DlcFxI18n.tr("dlc_manager.common.confirm"),
            () -> {},
            DlcFxI18n.tr("dlc_manager.common.cancel")
        );
    }
}
