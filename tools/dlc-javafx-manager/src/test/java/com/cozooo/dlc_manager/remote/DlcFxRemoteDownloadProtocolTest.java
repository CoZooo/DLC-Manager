package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.tools.externalsync.DlcTcpFacade;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxRemoteDownloadProtocolTest {
    @Test
    void exposesRemoteDownloadCancelLikeRequiredDownloadCancel() {
        boolean hasCancel = Arrays.stream(DlcTcpFacade.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("remoteDownloadCancel")
                        && method.getParameterCount() == 0);

        assertTrue(hasCancel, "Remote DLC download must expose cancel through the JavaFX TCP facade");
    }
}
