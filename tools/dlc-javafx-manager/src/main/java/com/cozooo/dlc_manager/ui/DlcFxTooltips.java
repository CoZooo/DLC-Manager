package com.cozooo.dlc_manager.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import javafx.stage.PopupWindow;
import javafx.util.Duration;

import java.util.function.Supplier;

public final class DlcFxTooltips {
    private static final double MAX_WIDTH = 420;
    private static final double RICH_MAX_WIDTH = 340;
    private static final double OFFSET_X = 14;
    private static final double OFFSET_Y = 18;
    private static final Duration SHOW_DELAY = Duration.ZERO;
    private static final Duration SHOW_DURATION = Duration.seconds(60);
    private static final Duration HIDE_DELAY = Duration.millis(80);
    private static final java.util.Set<FloatingTooltipState> ACTIVE_STATES =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private DlcFxTooltips() {
    }

    public static Tooltip plain(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Tooltip tooltip = plainAllowEmpty();
        tooltip.setText(text);
        return tooltip;
    }

    public static Tooltip plainAllowEmpty() {
        Tooltip tooltip = base();
        tooltip.setWrapText(true);
        return tooltip;
    }

    public static Tooltip formatted(String text) {
        return rich(DlcFxRichTextRenderer.formatted(text));
    }

    public static Tooltip markdown(String text) {
        return rich(DlcFxRichTextRenderer.markdown(text));
    }

    public static void install(Node node, Tooltip tooltip) {
        if (node != null && tooltip != null) {
            followMouse(node, () -> tooltip);
        }
    }

    public static void followMouse(Node node, Supplier<Tooltip> tooltipSupplier) {
        if (node == null || tooltipSupplier == null) {
            return;
        }
        final FloatingTooltipState state = new FloatingTooltipState();
        ACTIVE_STATES.add(state);
        installSceneSuppressor(node);
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event ->
            showOrMove(node, tooltipSupplier, state, event));
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event ->
            showOrMove(node, tooltipSupplier, state, event));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> hide(state));
    }

    private static Tooltip rich(TextFlow flow) {
        if (flow == null || flow.getChildren().isEmpty()) {
            return null;
        }
        flow.setMaxWidth(RICH_MAX_WIDTH);
        flow.setPrefWidth(RICH_MAX_WIDTH);
        Tooltip tooltip = base();
        tooltip.setText(null);
        tooltip.setGraphic(flow);
        return tooltip;
    }

    private static void showOrMove(Node owner, Supplier<Tooltip> tooltipSupplier,
                                   FloatingTooltipState state, MouseEvent event) {
        if (shouldSuppressForTarget(event.getTarget())
            || isInteractiveNodeAt(owner, event.getSceneX(), event.getSceneY())) {
            hide(state);
            return;
        }
        Tooltip tooltip = tooltipSupplier.get();
        if (tooltip == null) {
            hide(state);
            return;
        }
        StackPane root = root(owner);
        if (root == null) {
            return;
        }
        if (state.tooltip != tooltip || state.panel == null) {
            hide(state);
            state.tooltip = tooltip;
            state.panel = panelFor(tooltip);
            root.getChildren().add(state.panel);
            measure(state);
        }
        move(root, state, event);
    }

    private static StackPane root(Node owner) {
        Scene scene = owner == null ? null : owner.getScene();
        if (scene != null && scene.getRoot() instanceof StackPane root) {
            return root;
        }
        return null;
    }

    private static StackPane panelFor(Tooltip tooltip) {
        StackPane panel = new StackPane();
        panel.getStyleClass().add("dlc-floating-tooltip");
        panel.setMouseTransparent(true);
        panel.setPickOnBounds(false);
        panel.setManaged(false);
        Node graphic = tooltip.getGraphic();
        if (graphic instanceof TextFlow flow) {
            flow.setMaxWidth(RICH_MAX_WIDTH);
            flow.setPrefWidth(RICH_MAX_WIDTH);
            if (flow.getParent() instanceof StackPane previousParent) {
                previousParent.getChildren().remove(flow);
            }
            panel.getChildren().add(flow);
        } else {
            javafx.scene.control.Label label = new javafx.scene.control.Label(tooltip.getText());
            label.setWrapText(true);
            label.setMaxWidth(RICH_MAX_WIDTH);
            panel.getChildren().add(label);
        }
        return panel;
    }

    private static void measure(FloatingTooltipState state) {
        if (state.panel == null) {
            return;
        }
        state.panel.applyCss();
        state.panel.autosize();
        state.width = Math.min(RICH_MAX_WIDTH + 24, state.panel.prefWidth(-1));
        state.height = state.panel.prefHeight(state.width);
    }

    private static void move(StackPane root, FloatingTooltipState state, MouseEvent event) {
        StackPane panel = state.panel;
        double width = state.width;
        double height = state.height;
        double x = event.getSceneX() + OFFSET_X;
        double y = event.getSceneY() + OFFSET_Y;
        double rootWidth = root.getWidth();
        double rootHeight = root.getHeight();
        if (rootWidth > 0 && x + width > rootWidth - 8) {
            x = Math.max(8, event.getSceneX() - width - OFFSET_X);
        }
        if (rootHeight > 0 && y + height > rootHeight - 8) {
            y = Math.max(8, event.getSceneY() - height - OFFSET_Y);
        }
        panel.resizeRelocate(x, y, width, height);
    }

    private static void hide(FloatingTooltipState state) {
        if (state.panel != null && state.panel.getParent() instanceof StackPane root) {
            root.getChildren().remove(state.panel);
        }
        state.panel = null;
        state.tooltip = null;
    }

    static boolean shouldSuppressForTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (isInteractiveNode(current)) {
                return true;
            }
        }
        return false;
    }

    static boolean isInteractiveNodeAt(Node owner, double sceneX, double sceneY) {
        if (owner == null) {
            return false;
        }
        return isInteractiveNodeAtRecursive(owner, sceneX, sceneY);
    }

    private static boolean isInteractiveNodeAtRecursive(Node node, double sceneX, double sceneY) {
        if (node == null || !node.isVisible() || node.isMouseTransparent()) {
            return false;
        }
        if (isInteractiveNode(node) && containsScenePoint(node, sceneX, sceneY)) {
            return true;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (isInteractiveNodeAtRecursive(child, sceneX, sceneY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInteractiveNode(Node node) {
        return node instanceof TextInputControl
            || node instanceof ButtonBase
            || node instanceof ComboBoxBase
            || node instanceof ChoiceBox
            || node instanceof Spinner
            || node instanceof Slider
            || hasAnyStyleClass(node, "choice-box", "combo-box-base", "spinner", "slider");
    }

    private static boolean hasAnyStyleClass(Node node, String... styleClasses) {
        for (String styleClass : styleClasses) {
            if (node.getStyleClass().contains(styleClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsScenePoint(Node node, double sceneX, double sceneY) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return bounds != null
            && sceneX >= bounds.getMinX()
            && sceneX <= bounds.getMaxX()
            && sceneY >= bounds.getMinY()
            && sceneY <= bounds.getMaxY();
    }

    private static Tooltip base() {
        Tooltip tooltip = new Tooltip();
        tooltip.setAnchorLocation(PopupWindow.AnchorLocation.WINDOW_TOP_LEFT);
        tooltip.setShowDelay(SHOW_DELAY);
        tooltip.setShowDuration(SHOW_DURATION);
        tooltip.setHideDelay(HIDE_DELAY);
        tooltip.setMaxWidth(MAX_WIDTH);
        tooltip.getStyleClass().add("dlc-tooltip");
        return tooltip;
    }

    private static void installSceneSuppressor(Node node) {
        Scene scene = node.getScene();
        if (scene != null) {
            installSceneSuppressor(scene);
            return;
        }
        node.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                installSceneSuppressor(newScene);
            }
        });
    }

    private static void installSceneSuppressor(Scene scene) {
        Object marker = scene.getProperties().get(DlcFxTooltips.class);
        if (Boolean.TRUE.equals(marker)) {
            return;
        }
        scene.getProperties().put(DlcFxTooltips.class, Boolean.TRUE);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (shouldSuppressForEvent(scene, event)) {
                hideAll();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, event -> {
            if (shouldSuppressForEvent(scene, event)) {
                hideAll();
            }
        });
    }

    private static boolean shouldSuppressForEvent(Scene scene, MouseEvent event) {
        return shouldSuppressForTarget(event.getTarget())
            || (scene != null && isInteractiveNodeAt(scene.getRoot(), event.getSceneX(), event.getSceneY()));
    }

    private static void hideAll() {
        for (FloatingTooltipState state : java.util.List.copyOf(ACTIVE_STATES)) {
            hide(state);
        }
    }

    private static final class FloatingTooltipState {
        private Tooltip tooltip;
        private StackPane panel;
        private double width;
        private double height;
    }
}
