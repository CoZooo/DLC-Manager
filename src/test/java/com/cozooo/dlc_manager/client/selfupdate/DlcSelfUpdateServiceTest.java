package com.cozooo.dlc_manager.client.selfupdate;

import com.cozooo.dlc_manager.client.download.DlcCurseForgeClient;
import com.cozooo.dlc_manager.core.DlcVersionUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcSelfUpdateServiceTest {
    @Test
    void extractsVersionFromPublishedDisplayName() {
        assertEquals("5.0.0", DlcSelfUpdateService.extractPublishedVersion(
            "5.0.0 (Fabric 1.21.1)",
            null
        ));
    }

    @Test
    void extractsPrereleaseFromPublishedDisplayName() {
        assertEquals("5.0.0-beta1", DlcSelfUpdateService.extractPublishedVersion(
            "5.0.0-beta1 (Fabric 1.21.1)",
            null
        ));
    }

    @Test
    void ignoresLegacyDisplayNameLayouts() {
        assertNull(DlcSelfUpdateService.extractPublishedVersion("fabric-1.21.1-5.0.0", null));
        assertNull(DlcSelfUpdateService.extractPublishedVersion("DLC Manager 4.1.0 (Fabric 1.21.1)", null));
    }

    @Test
    void extractsVersionFromPublishedFileName() {
        assertEquals("4.1.0", DlcSelfUpdateService.extractPublishedVersion(
            null,
            "DLC-Manager-4.1.0-fabric-1.21.1.jar"
        ));
    }

    @Test
    void extractsPrereleaseFromPublishedFileName() {
        assertEquals("5.0.0-beta1", DlcSelfUpdateService.extractPublishedVersion(
            null,
            "DLC-Manager-5.0.0-beta1-fabric-1.21.1.jar"
        ));
        assertEquals("5.0.0", DlcSelfUpdateService.extractPublishedVersion(
            null,
            "DLC-Manager-5.0.0-fabric-1.21.1.jar"
        ));
        assertEquals("5.0.0-nightly200", DlcSelfUpdateService.extractPublishedVersion(
            null,
            "DLC-Manager-5.0.0-nightly200-fabric-1.21.1.jar"
        ));
    }

    @Test
    void prefersPublishedFileNameOverDisplayName() {
        assertEquals("4.1.0", DlcSelfUpdateService.extractPublishedVersion(
            "5.0.0 (Fabric 1.21.1)",
            "DLC-Manager-4.1.0-fabric-1.21.1.jar"
        ));
    }

    @Test
    void majorVersionChangeDoesNotOfferUpdate() {
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("1.0.0", "2.0.0", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("2.0.0", "3.0.0", file()));
    }

    @Test
    void minorAndPatchUpdatesStillOfferUpdate() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("1.0.0", "1.2.0", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("1.0.0", "1.0.1", file()));
    }

    @Test
    void prereleaseTransitionsFollowVersionOrder() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.0-beta2", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.0", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0", "5.0.0-beta1", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.0-beta1", file()));
    }

    @Test
    void comparesMajorVersionOnly() {
        assertTrue(DlcSelfUpdateService.isMajorVersionChanged("1.0.0", "2.0.0"));
        assertFalse(DlcSelfUpdateService.isMajorVersionChanged("5.0.0", "5.1.0"));
        assertFalse(DlcSelfUpdateService.isMajorVersionChanged("5.0.0-beta1", "5.0.0"));
        assertFalse(DlcSelfUpdateService.isMajorVersionChanged(null, "2.0.0"));
    }

    @Test
    void doesNotOfferDifferentMajorSeries() {
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("4.1.0", "5.0.0", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("4.1.0", "4.2.0", file()));
    }

    @Test
    void stableChannelIgnoresPrereleaseFiles() {
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0", "5.0.1-beta1", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0", "5.0.1-alpha1", file()));
    }

    @Test
    void betaChannelAcceptsStableButNotAlpha() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.1", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.0-beta2", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.1-alpha1", file()));
    }

    @Test
    void alphaChannelAcceptsEveryChannel() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-alpha1", "5.0.0-alpha2", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-alpha1", "5.0.0-beta1", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-alpha1", "5.0.1", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0-alpha1", "5.0.1-build1", file()));
    }

    @Test
    void buildChannelAcceptsEveryChannel() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-build200", "5.0.0-build201", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-build200", "5.0.1", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-build200", "5.0.1-beta1", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-build200", "5.0.1-alpha1", file()));
    }

    @Test
    void lowerChannelsCannotSeeBuildUpdates() {
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0", "5.0.1-build1", file()));
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta1", "5.0.1-build1", file()));
    }

    @Test
    void buildNumberOrderingIsNumeric() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-build9", "5.0.0-build10", file()));
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-build200", "5.0.0-build201", file()));
    }

    @Test
    void platformReleaseTypeOverridesVersionSuffix() {
        DlcCurseForgeClient.CurseForgeFile beta = file();
        beta.releaseType = 2;
        assertFalse(DlcSelfUpdateService.isUpdateAvailable("5.0.0", "5.0.1", beta));

        DlcCurseForgeClient.CurseForgeFile release = file();
        release.releaseType = 1;
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0", "5.0.1-rc1", release));
    }

    @Test
    void betaNumberOrderingIsNumeric() {
        assertTrue(DlcSelfUpdateService.isUpdateAvailable("5.0.0-beta2", "5.0.0-beta10", file()));
    }

    @Test
    void picksHighestCandidateWithinSameMajorSeries() {
        List<DlcSelfUpdateService.UpdateCandidate> candidates = List.of(
            candidate("4.0.0", "stable", "2026-01-01T00:00:00Z"),
            candidate("4.2.0", "stable", "2026-01-02T00:00:00Z"),
            candidate("5.0.0", "stable", "2026-01-03T00:00:00Z")
        );
        DlcSelfUpdateService.UpdateCandidate best = DlcSelfUpdateService.pickUpdateCandidate("4.1.0", candidates);
        assertEquals("4.2.0", best.version);
    }

    @Test
    void skipsCandidatesBlockedByChannelRules() {
        List<DlcSelfUpdateService.UpdateCandidate> candidates = List.of(
            candidate("5.0.0", "stable", "2026-01-01T00:00:00Z"),
            candidate("5.0.1-beta1", "beta", "2026-01-02T00:00:00Z"),
            candidate("5.0.2", "alpha", "2026-01-03T00:00:00Z")
        );
        assertNull(DlcSelfUpdateService.pickUpdateCandidate("5.0.0", candidates));
    }

    private static DlcSelfUpdateService.UpdateCandidate candidate(String version, String channel, String fileDate) {
        return new DlcSelfUpdateService.UpdateCandidate(
            version,
            DlcVersionUtil.channelByName(channel),
            "DLC-Manager-" + version + "-fabric-1.21.1.jar",
            version + " (Fabric 1.21.1)",
            "1",
            fileDate,
            "https://example.invalid/DLC-Manager.jar"
        );
    }

    private static DlcCurseForgeClient.CurseForgeFile file() {
        DlcCurseForgeClient.CurseForgeFile file = new DlcCurseForgeClient.CurseForgeFile();
        file.fileName = "DLC-Manager-5.0.0-fabric-1.21.1.jar";
        file.displayName = "5.0.0 (Fabric 1.21.1)";
        return file;
    }
}
