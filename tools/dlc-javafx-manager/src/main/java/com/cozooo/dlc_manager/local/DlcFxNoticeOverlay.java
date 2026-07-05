package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;
import javafx.scene.Scene;

public final class DlcFxNoticeOverlay {
    private final com.cozooo.dlc_manager.ui.notice.DlcFxNoticeOverlay delegate;

    public DlcFxNoticeOverlay(Scene scene, DlcNoticeConfig config) {
        this.delegate = new com.cozooo.dlc_manager.ui.notice.DlcFxNoticeOverlay(scene, config);
    }

    public void show() {
        delegate.show();
    }
}
