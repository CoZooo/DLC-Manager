package com.cozooo.dlc_manager.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

public final class DlcFxListViewSupport {

    private static final double EXTERNAL_SCROLLBAR_SLOT_WIDTH = 12;

    private DlcFxListViewSupport() {
    }

    public static <T> HBox wrap(ListView<T> listView) {
        ScrollBar externalVerticalScrollbar = new ScrollBar();
        externalVerticalScrollbar.setOrientation(Orientation.VERTICAL);
        DlcFxStyle.add(externalVerticalScrollbar, "dlc-list-scrollbar-slot");
        externalVerticalScrollbar.setMinWidth(EXTERNAL_SCROLLBAR_SLOT_WIDTH);
        externalVerticalScrollbar.setPrefWidth(EXTERNAL_SCROLLBAR_SLOT_WIDTH);
        externalVerticalScrollbar.setMaxWidth(EXTERNAL_SCROLLBAR_SLOT_WIDTH);
        externalVerticalScrollbar.setDisable(true);
        externalVerticalScrollbar.setMouseTransparent(true);
        DlcFxStyle.add(listView, "dlc-list-with-external-scroll");

        Set<ScrollBar> linkedVerticalScrollbars = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        listView.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(() -> linkExternalVerticalScrollbar(listView, externalVerticalScrollbar, linkedVerticalScrollbars)));
        listView.sceneProperty().addListener((obs, oldScene, newScene) -> Platform.runLater(() -> linkExternalVerticalScrollbar(listView, externalVerticalScrollbar, linkedVerticalScrollbars)));
        Platform.runLater(() -> linkExternalVerticalScrollbar(listView, externalVerticalScrollbar, linkedVerticalScrollbars));

        HBox frame = new HBox(0, listView, externalVerticalScrollbar);
        DlcFxStyle.add(frame, "dlc-list-frame");
        HBox.setHgrow(listView, Priority.ALWAYS);
        return frame;
    }

    private static void linkExternalVerticalScrollbar(ListView<?> listView, ScrollBar external, Set<ScrollBar> linkedVerticalScrollbars) {
        if (listView == null || external == null || listView.getScene() == null) {
            return;
        }
        listView.applyCss();
        Set<Node> nodes = new HashSet<>(listView.lookupAll(".scroll-bar"));
        for (Node node : nodes) {
            if (node instanceof ScrollBar internal && internal.getOrientation() == Orientation.VERTICAL) {
                if (linkedVerticalScrollbars.add(internal)) {
                    external.minProperty().bind(internal.minProperty());
                    external.maxProperty().bind(internal.maxProperty());
                    external.valueProperty().bindBidirectional(internal.valueProperty());
                    external.visibleAmountProperty().bind(internal.visibleAmountProperty());
                    internal.visibleProperty().addListener((obs, oldValue, newValue) -> updateExternalVerticalScrollbar(external, internal));
                    internal.visibleAmountProperty().addListener((obs, oldValue, newValue) -> updateExternalVerticalScrollbar(external, internal));
                    internal.maxProperty().addListener((obs, oldValue, newValue) -> updateExternalVerticalScrollbar(external, internal));
                }
                updateExternalVerticalScrollbar(external, internal);
                return;
            }
        }
        Platform.runLater(() -> linkExternalVerticalScrollbar(listView, external, linkedVerticalScrollbars));
    }

    private static void updateExternalVerticalScrollbar(ScrollBar external, ScrollBar internal) {
        boolean scrollable = internal != null
            && internal.isVisible()
            && internal.getMax() > internal.getMin();
        external.setDisable(!scrollable);
        external.setMouseTransparent(!scrollable);
        external.setOpacity(scrollable ? 1 : 0);
    }
}
