package com.cozooo.dlc_manager.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPackRowViewTest {
    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void createsSharedPackRowStructureAndFiltersOptionalActions() throws Exception {
        DlcFxPackRowState state = DlcFxPackRowState.of(1, 3, 2, Map.of(
            "homepage", "https://example.test",
            "sponsor", "https://sponsor.test"
        ), false);
        CountDownLatch latch = new CountDownLatch(1);
        Node[] rowRef = new Node[1];
        Platform.runLater(() -> {
            rowRef[0] = DlcFxPackRowView.create(new DlcFxPackRowView.Spec(
                state,
                DlcFxRichTextRenderer.formatted("Pack"),
                DlcFxRichTextRenderer.formatted("Author"),
                DlcFxRichTextRenderer.formatted("Version"),
                DlcFxRichTextRenderer.markdown("Description"),
                new javafx.scene.layout.StackPane(),
                "Install",
                () -> {},
                () -> {},
                () -> {},
                () -> {},
                () -> {},
                () -> {}
            ));
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        HBox row = assertInstanceOf(HBox.class, rowRef[0]);
        assertEquals(4, row.getChildren().size());
        VBox actions = assertInstanceOf(VBox.class, row.getChildren().get(3));
        HBox optionalActions = assertInstanceOf(HBox.class, actions.getChildren().get(1));
        assertEquals(1, optionalActions.getChildren().size());
        assertFalse(optionalActions.getChildren().getFirst().isDisabled());
    }

    @Test
    void createsLocalStyleVersionSelector() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Node[] lineRef = new Node[1];
        AtomicInteger selectedCount = new AtomicInteger();
        Platform.runLater(() -> {
            lineRef[0] = DlcFxPackRowView.versionLine(
                "1.0.0",
                true,
                java.util.List.of(
                    new DlcFxPackRowView.VersionOption("1.0.0", "1.0.0", true),
                    new DlcFxPackRowView.VersionOption("2.0.0", "2.0.0", false)
                ),
                option -> selectedCount.incrementAndGet()
            );
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        HBox line = assertInstanceOf(HBox.class, lineRef[0]);
        MenuButton button = assertInstanceOf(MenuButton.class, line.getChildren().get(1));
        assertFalse(button.isDisabled());
        assertEquals("1.0.0", button.getText());
        assertTrue(button.getItems().getFirst().getStyleClass().contains("selected"));

        runMenuAction(button.getItems().getFirst());
        runMenuAction(button.getItems().get(1));

        assertEquals(1, selectedCount.get());
    }

    @Test
    void disablesVersionSelectorWhenOnlyOneVersionExists() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Node[] lineRef = new Node[1];
        Platform.runLater(() -> {
            lineRef[0] = DlcFxPackRowView.versionLine(
                "1.0.0",
                false,
                java.util.List.of(new DlcFxPackRowView.VersionOption("1.0.0", "1.0.0", true)),
                option -> {}
            );
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        HBox line = assertInstanceOf(HBox.class, lineRef[0]);
        MenuButton button = assertInstanceOf(MenuButton.class, line.getChildren().get(1));
        assertTrue(button.isDisabled());
    }

    @Test
    void createsSharedPackIconWithImageOrInitial() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Node[] imageIcon = new Node[1];
        Node[] initialIcon = new Node[1];
        Platform.runLater(() -> {
            Image image = new Image("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
            imageIcon[0] = DlcFxPackRowView.packIcon(image, "Pack");
            initialIcon[0] = DlcFxPackRowView.packIcon(null, "remote");
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        StackPane imageBox = assertInstanceOf(StackPane.class, imageIcon[0]);
        ImageView view = assertInstanceOf(ImageView.class, imageBox.getChildren().getFirst());
        assertEquals(58, view.getFitWidth());
        assertEquals(58, view.getFitHeight());
        StackPane initialBox = assertInstanceOf(StackPane.class, initialIcon[0]);
        javafx.scene.control.Label initial = assertInstanceOf(javafx.scene.control.Label.class, initialBox.getChildren().getFirst());
        assertEquals("R", initial.getText());
    }

    private static void runMenuAction(MenuItem item) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            item.fire();
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
