package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.client.server.DlcServerSession;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxRemoteDownloadProtocolTest {
    @Test
    void exposesRemoteDownloadCancelLikeRequiredDownloadCancel() {
        boolean hasCancel = Arrays.stream(DlcServerSession.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("remoteDownloadCancel")
                        && method.getParameterCount() == 0);

        assertTrue(hasCancel, "Remote DLC download must expose cancel through the shared TCP session");
    }
}
