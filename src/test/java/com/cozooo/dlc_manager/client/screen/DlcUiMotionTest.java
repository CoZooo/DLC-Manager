package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiMotionTest {
    @Test
    void reversingMidFlightContinuesFromCurrentPositionForFullDuration() {
        DlcUiMotion motion = new DlcUiMotion(1);
        motion.animateTo(0, 0);
        double current = motion.valueAt(90_000_000L);
        assertTrue(current > 0 && current < 1);
        motion.animateTo(1, 90_000_000L);
        assertEquals(current, motion.valueAt(90_000_000L));
        assertTrue(motion.valueAt(150_000_000L) > current);
        assertTrue(motion.valueAt(240_000_000L) < 1);
        assertEquals(1, motion.valueAt(330_000_000L));
    }

    @Test
    void repeatedReversalsStayContinuousAndWithinBounds() {
        DlcUiMotion motion = new DlcUiMotion(1);
        for (int i = 0; i < 20; i++) {
            long now = i * 25_000_000L;
            double current = motion.valueAt(now);
            motion.animateTo(i % 2, now);
            assertEquals(current, motion.valueAt(now));
            for (int frame = 0; frame < 25; frame++) {
                double value = motion.valueAt(now + frame * 1_000_000L);
                assertTrue(value >= 0 && value <= 1);
            }
        }
        assertEquals(motion.getTarget(), motion.valueAt(1_000_000_000L));
    }

    @Test
    void frameSamplingAndRepeatedTargetDoNotChangeDuration() {
        DlcUiMotion motion = new DlcUiMotion(0);
        motion.animateTo(1, 1_000_000L);
        double middle = motion.valueAt(121_000_000L);
        motion.valueAt(10_000_000L);
        motion.animateTo(1, 121_000_000L);
        assertEquals(middle, motion.valueAt(121_000_000L));
        assertEquals(1, motion.valueAt(241_000_000L));
    }
}
