package com.cozooo.dlc_manager.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcVersionUtilTest {
    @Test
    void classifiesStableAndPrereleaseChannels() {
        assertEquals(DlcVersionUtil.Channel.STABLE, DlcVersionUtil.channelOf("5.0.0"));
        assertEquals(DlcVersionUtil.Channel.STABLE, DlcVersionUtil.channelOf("5.0.0-hotfix1"));
        assertEquals(DlcVersionUtil.Channel.BETA, DlcVersionUtil.channelOf("5.0.0-beta1"));
        assertEquals(DlcVersionUtil.Channel.BETA, DlcVersionUtil.channelOf("5.0.0-rc1"));
        assertEquals(DlcVersionUtil.Channel.ALPHA, DlcVersionUtil.channelOf("5.0.0-alpha1"));
        assertEquals(DlcVersionUtil.Channel.NIGHTLY, DlcVersionUtil.channelOf("5.0.0-build200"));
        assertEquals(DlcVersionUtil.Channel.NIGHTLY, DlcVersionUtil.channelOf("5.0.0-nightly200"));
        assertEquals(DlcVersionUtil.Channel.STABLE, DlcVersionUtil.channelOf("5.0.0-sp1"));
    }

    @Test
    void comparesPrereleaseOrderAndBuildNumbers() {
        assertTrue(DlcVersionUtil.compare("5.0.0-alpha1", "5.0.0-beta1") < 0);
        assertTrue(DlcVersionUtil.compare("5.0.0-beta1", "5.0.0") < 0);
        assertTrue(DlcVersionUtil.compare("5.0.0-build9", "5.0.0-build10") < 0);
        assertTrue(DlcVersionUtil.compare("5.0.0-fix1", "5.0.0") > 0);
        assertTrue(DlcVersionUtil.compare("5.0.1", "5.0.0-sp9") > 0);
        assertTrue(DlcVersionUtil.compare("5.0.0", "5.0.0-sp1") > 0);
        assertTrue(DlcVersionUtil.compare("5.0.0-sp1", "5.0.0-sp2") < 0);
        assertEquals(0, DlcVersionUtil.compare("5.0.0-build200", "5.0.0-nightly200"));
        assertTrue(DlcVersionUtil.compare("5.0.0-nightly200", "5.0.0-nightly201") < 0);
        assertEquals(0, DlcVersionUtil.compare("5.0.0", "5.0.0"));
    }

    @Test
    void channelVisibilityFollowsFilterPriority() {
        assertTrue(DlcVersionUtil.Channel.NIGHTLY.accepts(DlcVersionUtil.Channel.ALPHA));
        assertFalse(DlcVersionUtil.Channel.ALPHA.accepts(DlcVersionUtil.Channel.NIGHTLY));
        assertTrue(DlcVersionUtil.Channel.BETA.accepts(DlcVersionUtil.Channel.STABLE));
        assertFalse(DlcVersionUtil.Channel.STABLE.accepts(DlcVersionUtil.Channel.BETA));
    }

    @Test
    void filterParsingFallsBackToEverything() {
        assertEquals(DlcVersionUtil.Channel.STABLE, DlcVersionUtil.parseFilter("stable"));
        assertEquals(DlcVersionUtil.Channel.ALPHA, DlcVersionUtil.parseFilter("ALPHA"));
        assertEquals(DlcVersionUtil.Channel.NIGHTLY, DlcVersionUtil.parseFilter("all"));
        assertEquals(DlcVersionUtil.Channel.NIGHTLY, DlcVersionUtil.parseFilter("nightly"));
        assertEquals(DlcVersionUtil.Channel.NIGHTLY, DlcVersionUtil.parseFilter("release"));
        assertEquals(DlcVersionUtil.Channel.NIGHTLY, DlcVersionUtil.parseFilter("unknown"));
        assertTrue(DlcVersionUtil.isWithinFilter("5.0.1-beta1", DlcVersionUtil.Channel.BETA));
        assertFalse(DlcVersionUtil.isWithinFilter("5.0.1-build1", DlcVersionUtil.Channel.BETA));
    }
}
