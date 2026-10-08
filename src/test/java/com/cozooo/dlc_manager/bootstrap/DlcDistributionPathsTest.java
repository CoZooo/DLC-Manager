package com.cozooo.dlc_manager.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcDistributionPathsTest {
    @TempDir Path game;

    @AfterEach void clearProperties() {
        System.clearProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY);
        System.clearProperty(DlcDistributionPaths.PAYLOAD_PROPERTY);
    }

    @Test void selfUpdateTargetsOuterDistributionOnlyForExtractedManager() throws Exception {
        Path distribution = Files.writeString(game.resolve("DLC-Manager.jar"), "outer");
        Path payload = Files.writeString(game.resolve("payload.jar"), "inner");
        System.setProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY, distribution.toString());
        System.setProperty(DlcDistributionPaths.PAYLOAD_PROPERTY, payload.toString());
        assertEquals(distribution, DlcDistributionPaths.originalModFile("dlc_manager", payload));
        assertEquals(distribution, DlcDistributionPaths.originalModFile("dlc_manager", game.resolve("cache/../payload.jar")));
        assertEquals(payload, DlcDistributionPaths.originalModFile("other_mod", payload));
        assertEquals(game, DlcDistributionPaths.originalModFile("dlc_manager", game));
        Files.delete(distribution);
        assertEquals(payload, DlcDistributionPaths.originalModFile("dlc_manager", payload));
    }

    @Test void directModAndMissingPathKeepOriginalValues() {
        assertEquals(game, DlcDistributionPaths.originalModFile("dlc_manager", game));
        assertNull(DlcDistributionPaths.originalModFile("dlc_manager", null));
        System.setProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY, "\u0000");
        System.setProperty(DlcDistributionPaths.PAYLOAD_PROPERTY, game.toString());
        assertEquals(game, DlcDistributionPaths.originalModFile("dlc_manager", game));
    }
}
