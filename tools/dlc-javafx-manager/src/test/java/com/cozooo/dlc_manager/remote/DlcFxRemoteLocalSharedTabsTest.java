package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.server.DlcFxServerDownloadTab;
import com.cozooo.dlc_manager.ui.remote.DlcFxRemoteDownloadShell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxRemoteLocalSharedTabsTest {
    @Test
    void remoteDownloadUsesTheSharedLocalDownloadShell() throws Exception {
        assertEquals(DlcFxRemoteDownloadShell.class, DlcFxServerDownloadTab.class.getDeclaredField("shell").getType());
    }
}
