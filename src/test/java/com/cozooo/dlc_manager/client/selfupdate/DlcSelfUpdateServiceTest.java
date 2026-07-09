package com.cozooo.dlc_manager.client.selfupdate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcSelfUpdateServiceTest {
    @Test
    void extractsModVersionFromPublishDisplayNameBeforeMinecraftVersion() {
        assertEquals("4.1.0", DlcSelfUpdateService.extractPublishedVersion(
            "DLC Manager 4.1.0 (Fabric 1.21.1)",
            null
        ));
    }

    @Test
    void stillPrefersVersionAtEndOfPublishedFileName() {
        assertEquals("4.1.0", DlcSelfUpdateService.extractPublishedVersion(
            "DLC Manager 4.1.0 (Fabric 1.21.1)",
            "DLC-Manager-fabric-1.21.1-4.1.0.jar"
        ));
    }
}
