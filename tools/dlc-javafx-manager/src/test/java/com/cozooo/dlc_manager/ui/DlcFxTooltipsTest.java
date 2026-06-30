package com.cozooo.dlc_manager.ui;

import javafx.scene.control.Tooltip;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxTooltipsTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void plainTooltipShowsImmediatelyWithModernStyle() {
        Tooltip tooltip = DlcFxTooltips.plain("content");

        assertEquals(0, tooltip.getShowDelay().toMillis());
        assertEquals(420, tooltip.getMaxWidth());
        assertTrue(tooltip.isWrapText());
        assertTrue(tooltip.getStyleClass().contains("dlc-tooltip"));
    }

    @Test
    void formattedTooltipConstrainsRichTextWidth() {
        Tooltip tooltip = DlcFxTooltips.formatted("line");

        assertTrue(tooltip.getGraphic() instanceof TextFlow);
        TextFlow flow = (TextFlow) tooltip.getGraphic();
        assertEquals(340, flow.getMaxWidth());
        assertEquals(340, flow.getPrefWidth());
    }

    @Test
    void suppressesFloatingTooltipForInputsAndButtons() {
        TextField textField = new TextField();
        Button button = new Button("Run");
        HBox fakeChoiceBox = new HBox();
        fakeChoiceBox.getStyleClass().add("choice-box");
        HBox row = new HBox(textField, button, fakeChoiceBox);

        assertTrue(DlcFxTooltips.shouldSuppressForTarget(textField));
        assertTrue(DlcFxTooltips.shouldSuppressForTarget(button));
        assertTrue(DlcFxTooltips.shouldSuppressForTarget(fakeChoiceBox));
        assertFalse(DlcFxTooltips.shouldSuppressForTarget(row));
    }

    @Test
    void suppressesFloatingTooltipWhenScenePointHitsInteractiveChild() {
        Button button = new Button("Run");
        HBox row = new HBox(button);
        row.resizeRelocate(0, 0, 200, 40);
        button.resizeRelocate(20, 5, 80, 30);

        assertTrue(DlcFxTooltips.isInteractiveNodeAt(row, 30, 10));
        assertFalse(DlcFxTooltips.isInteractiveNodeAt(row, 150, 10));
    }
}
