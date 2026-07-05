package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxGroupCollapseModelTest {
    @Test
    void tracksCollapseAndAnimationSeparately() {
        DlcFxGroupCollapseModel model = new DlcFxGroupCollapseModel();

        model.beginCollapse("folder");

        assertTrue(model.isCollapsed("folder"));
        assertTrue(model.isAnimating("folder"));

        model.finishAnimation("folder");

        assertTrue(model.isCollapsed("folder"));
        assertFalse(model.isAnimating("folder"));
    }

    @Test
    void expandClearsCollapsedStateDuringAnimation() {
        DlcFxGroupCollapseModel model = new DlcFxGroupCollapseModel();
        model.beginCollapse("folder");
        model.finishAnimation("folder");

        model.beginExpand("folder");

        assertFalse(model.isCollapsed("folder"));
        assertTrue(model.isAnimating("folder"));
    }

    @Test
    void ignoresToggleRequestsWhileFolderIsAnimating() {
        DlcFxGroupCollapseModel model = new DlcFxGroupCollapseModel();

        assertTrue(model.beginCollapse("folder"));
        assertFalse(model.beginExpand("folder"));

        assertTrue(model.isCollapsed("folder"));
        assertTrue(model.isAnimating("folder"));
    }
}
