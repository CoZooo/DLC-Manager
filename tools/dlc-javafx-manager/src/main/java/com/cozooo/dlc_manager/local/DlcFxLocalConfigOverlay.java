package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.config.DlcFxConfigOverlay;
import com.cozooo.dlc_manager.ui.config.DlcFxConfigModel;
import javafx.scene.Scene;

public final class DlcFxLocalConfigOverlay {
    private final DlcFxConfigOverlay delegate;

    public DlcFxLocalConfigOverlay(Scene scene, Runnable onSaved) {
        this.delegate = new DlcFxConfigOverlay(
                scene,
                new DlcFxConfigModel(),
                "dlc_manager.config.local_desc",
                "dlc_manager.config.local_save",
                "dlc_manager.config.local_cancel",
                model -> {
                    DlcFxStyle.setStatus(DlcFxI18n.tr("dlc_manager.config.saved_local"));
                    if (onSaved != null) {
                        onSaved.run();
                    }
                });
    }

    public void show() {
        delegate.show();
    }
}
