package com.cozooo.dlc_manager.client.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateServiceTest {
    @Test
    void compareVersionsComparesNumericPartsNumerically() {
        assertTrue(DlcUpdateService.compareVersions("1.10.0", "1.2.9") > 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0", "1.0") > 0);
        assertTrue(DlcUpdateService.compareVersions("26.1.x", "1.21.1") > 0);
    }

    @Test
    void compareVersionsOrdersKnownQualifiersBeforeReleaseAndHotfixAfterRelease() {
        assertTrue(DlcUpdateService.compareVersions("1.0.0-alpha1", "1.0.0-beta1") < 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-beta2", "1.0.0-rc1") < 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-rc1", "1.0.0") < 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-hotfix1", "1.0.0") > 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-build1", "1.0.0-hotfix1") > 0);
    }

    @Test
    void compareVersionsUsesQualifierNumber() {
        assertTrue(DlcUpdateService.compareVersions("1.0.0-beta2", "1.0.0-beta10") < 0);
        assertEquals(0, DlcUpdateService.compareVersions("1.0.0-rc1", "1.0.0-rc1"));
    }

    @Test
    void rollbackMarkerIsActiveWhenMarkerTargetDiffersFromCurrentVersion() {
        assertTrue(!DlcUpdateService.isRollbackMarkerActive("1.2.0+1.0.5", "1.2.0", "1.0.5"));
        assertTrue(!DlcUpdateService.isRollbackMarkerActive("1.0.5", "1.2.0", "1.0.5"));
        assertTrue(DlcUpdateService.isRollbackMarkerActive("1.1.0+1.0.5", "1.2.0", "1.0.5"));
        assertTrue(DlcUpdateService.isRollbackMarkerActive("1.0.4", "1.2.0", "1.0.5"));
    }
}
