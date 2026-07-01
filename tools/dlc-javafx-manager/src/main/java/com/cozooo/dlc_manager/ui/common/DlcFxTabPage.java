package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class DlcFxTabPage {
    private final BorderPane root = new BorderPane();
    private final Label statusLabel;
    private final ProgressBar progressBar;

    public DlcFxTabPage() {
        this("");
    }

    public DlcFxTabPage(String initialStatus) {
        this.statusLabel = new Label(initialStatus == null ? "" : initialStatus);
        this.progressBar = new ProgressBar(0);
        this.progressBar.setVisible(false);
        this.progressBar.setPrefWidth(180);
        DlcFxStyle.view(root);
    }

    public BorderPane root() {
        return root;
    }

    public Label statusLabel() {
        return statusLabel;
    }

    public ProgressBar progressBar() {
        return progressBar;
    }

    public void setTop(Node node) {
        root.setTop(node);
    }

    public void setCenter(Node node) {
        root.setCenter(node);
    }

    public void setBottom(Node node) {
        root.setBottom(node);
    }

    public VBox section(Node content) {
        if (content instanceof VBox existing && existing.getStyleClass().contains(DlcFxStyle.SECTION)) {
            return existing;
        }
        VBox box = new VBox(content);
        box.setPadding(new Insets(14, 16, 14, 16));
        DlcFxStyle.section(box);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    public static void grow(Node node) {
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        VBox.setVgrow(node, Priority.ALWAYS);
    }
}
