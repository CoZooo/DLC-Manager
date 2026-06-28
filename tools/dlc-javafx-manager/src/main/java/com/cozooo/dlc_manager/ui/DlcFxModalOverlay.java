package com.cozooo.dlc_manager.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class DlcFxModalOverlay {
    private DlcFxModalOverlay() {
    }

    public static boolean isShowing(Scene scene) {
        if (scene == null || !(scene.getRoot() instanceof StackPane root)) {
            return false;
        }
        return root.getChildren().size() > 1;
    }

    public static void show(Scene scene, String title, String message, Runnable confirmAction, String confirmText) {
        show(scene, title, message, confirmAction, confirmText, null, null);
    }

    public static void show(Scene scene, String title, String message, Runnable confirmAction, String confirmText, Runnable cancelAction, String cancelText) {
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMinWidth(Region.USE_PREF_SIZE);
        messageLabel.setPrefWidth(preferredDialogWidth(scene));
        messageLabel.setMaxWidth(preferredDialogWidth(scene));
        DlcFxStyle.muted(messageLabel);
        show(scene, title, messageLabel, confirmAction, confirmText, cancelAction, cancelText);
    }

    public static void show(Scene scene, String title, Node contentNode, Runnable confirmAction, String confirmText, Runnable cancelAction, String cancelText) {
        show(scene, title, contentNode, confirmAction, confirmText, cancelAction, cancelText, null);
    }

    public static void show(
        Scene scene,
        String title,
        Node contentNode,
        Runnable confirmAction,
        String confirmText,
        Runnable cancelAction,
        String cancelText,
        Consumer<Button> confirmButtonConfigurer
    ) {
        if (scene == null) {
            if (confirmAction != null) {
                confirmAction.run();
            }
            return;
        }
        if (!(scene.getRoot() instanceof StackPane root)) {
            if (confirmAction != null) {
                confirmAction.run();
            }
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("dlc-modal-overlay");
        overlay.setPickOnBounds(true);
        overlay.setMouseTransparent(false);

        VBox panel = new VBox(14);
        panel.getStyleClass().add("dlc-modal-panel");
        panel.setFillWidth(true);
        panel.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        panel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dlc-modal-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(preferredDialogWidth(scene));
        Node contentBody = contentNode == null ? new Region() : contentNode;
        VBox content = new VBox(10, titleLabel, contentBody);
        content.getStyleClass().add("dlc-modal-content");
        content.setFillWidth(true);

        Button confirm = new Button(confirmText);
        DlcFxStyle.primary(confirm);
        if (confirmButtonConfigurer != null) {
            confirmButtonConfigurer.accept(confirm);
        }
        confirm.setOnAction(e -> {
            root.getChildren().remove(overlay);
            if (confirmAction != null) {
                confirmAction.run();
            }
        });

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_RIGHT);
        if (cancelAction != null && cancelText != null && !cancelText.isBlank()) {
            Button cancel = new Button(cancelText);
            cancel.setOnAction(e -> {
                root.getChildren().remove(overlay);
                cancelAction.run();
            });
            footer.getChildren().add(cancel);
        }
        footer.getChildren().add(confirm);
        footer.setAlignment(Pos.CENTER_RIGHT);

        panel.getChildren().addAll(content, footer);
        StackPane.setAlignment(panel, Pos.CENTER);
        overlay.getChildren().add(panel);

        overlay.setOnMouseClicked(e -> e.consume());
        panel.setOnMouseClicked(e -> e.consume());
        root.getChildren().add(overlay);
    }

    private static double preferredDialogWidth(Scene scene) {
        double sceneWidth = scene == null ? 720 : scene.getWidth();
        if (sceneWidth <= 0) {
            sceneWidth = 720;
        }
        return Math.max(420, Math.min(620, sceneWidth - 96));
    }
}
