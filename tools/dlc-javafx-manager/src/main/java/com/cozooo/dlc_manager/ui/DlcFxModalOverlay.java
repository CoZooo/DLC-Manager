package com.cozooo.dlc_manager.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
        if (scene == null) {
            if (confirmAction != null) {
                confirmAction.run();
            }
            return;
        }
        StackPane root = (StackPane) scene.getRoot();
        if (root == null) {
            if (confirmAction != null) {
                confirmAction.run();
            }
            return;
        }
        Region blocker = new Region();
        blocker.setStyle("-fx-background-color: rgba(16, 20, 28, 0.45);");
        blocker.setMouseTransparent(false);
        blocker.setPickOnBounds(true);
        blocker.setMinSize(Double.MAX_VALUE, Double.MAX_VALUE);
        blocker.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        BorderPane panel = new BorderPane();
        panel.getStyleClass().add("dlc-modal-panel");
        panel.setMaxWidth(520);
        panel.setMinWidth(420);
        panel.setPadding(new Insets(18));

        Label titleLabel = new Label(title);
        DlcFxStyle.title(titleLabel);
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        DlcFxStyle.muted(messageLabel);

        VBox content = new VBox(10, titleLabel, messageLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        panel.setCenter(content);

        Button confirm = new Button(confirmText);
        DlcFxStyle.primary(confirm);
        confirm.setOnAction(e -> {
            root.getChildren().remove(blocker);
            root.getChildren().remove(panel);
            if (confirmAction != null) {
                confirmAction.run();
            }
        });

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_RIGHT);
        if (cancelAction != null && cancelText != null && !cancelText.isBlank()) {
            Button cancel = new Button(cancelText);
            cancel.setOnAction(e -> {
                root.getChildren().remove(blocker);
                root.getChildren().remove(panel);
                cancelAction.run();
            });
            footer.getChildren().add(cancel);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(spacer, confirm);
        footer.setAlignment(Pos.CENTER_RIGHT);
        panel.setBottom(footer);
        StackPane.setAlignment(panel, Pos.CENTER);

        blocker.setOnMouseClicked(e -> e.consume());
        panel.setOnMouseClicked(e -> e.consume());
        root.getChildren().addAll(blocker, panel);
    }
}
