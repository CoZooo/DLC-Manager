package com.cozooo.dlc_manager.client.preset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPresetStatusTest {
    @Test
    void enabledStatusesAreOnAndPart() {
        assertTrue(DlcPresetStatus.ON.isEnabled());
        assertTrue(DlcPresetStatus.PART.isEnabled());
        assertFalse(DlcPresetStatus.OFF.isEnabled());
    }

    @Test
    void convertsToConfigValues() {
        assertEquals("On", DlcPresetStatus.ON.toConfigValue());
        assertEquals("Part", DlcPresetStatus.PART.toConfigValue());
        assertEquals("Off", DlcPresetStatus.OFF.toConfigValue());
    }

    @Test
    void parsesStatusCaseInsensitivelyAndFallsBackToOff() {
        assertEquals(DlcPresetStatus.ON, DlcPresetStatus.fromString(" on "));
        assertEquals(DlcPresetStatus.PART, DlcPresetStatus.fromString("PART"));
        assertEquals(DlcPresetStatus.OFF, DlcPresetStatus.fromString("off"));
        assertEquals(DlcPresetStatus.OFF, DlcPresetStatus.fromString("unknown"));
        assertEquals(DlcPresetStatus.OFF, DlcPresetStatus.fromString(null));
    }
}
