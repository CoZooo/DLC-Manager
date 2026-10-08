package com.cozooo.dlc_manager.ui.required;

import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

public final class DlcFxRequiredInstaller {
    @FunctionalInterface
    public interface Installation {
        void run() throws Exception;
    }

    private record Request(String key, Installation installation, Runnable onReady) {}

    private final StackPane root;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private final Map<Node, Boolean> disabled = new IdentityHashMap<>();
    private Request current;
    private StackPane overlay;
    private Label status;
    private ProgressBar progress;
    private Button retry;

    public DlcFxRequiredInstaller(StackPane root) {
        this.root = root;
    }

    public boolean isBlocking() {
        return current != null;
    }

    public void ensure(String key, Installation installation, Runnable onReady) {
        if ((current != null && current.key().equals(key)) || queue.stream().anyMatch(request -> request.key().equals(key))) return;
        queue.add(new Request(key, installation, onReady));
        if (current == null) next();
    }

    private void next() {
        current = queue.remove();
        for (Node node : root.getChildren()) {
            disabled.put(node, node.isDisable());
            node.setDisable(true);
        }
        Label title = new Label(DlcFxI18n.tr("dlc_manager.required.install_title"));
        title.getStyleClass().add("dlc-modal-title");
        status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(520);
        progress = new ProgressBar(-1);
        progress.setMaxWidth(Double.MAX_VALUE);
        retry = new Button(DlcFxI18n.tr("dlc_manager.required.retry"));
        DlcFxStyle.primary(retry);
        retry.setOnAction(event -> start());
        VBox panel = new VBox(14, title, status, progress, retry);
        panel.getStyleClass().add("dlc-modal-panel");
        panel.setMaxSize(560, VBox.USE_PREF_SIZE);
        overlay = new StackPane(panel);
        overlay.getStyleClass().add("dlc-modal-overlay");
        StackPane.setAlignment(panel, Pos.CENTER);
        overlay.setFocusTraversable(true);
        root.getChildren().add(overlay);
        overlay.requestFocus();
        start();
    }

    private void start() {
        status.setText(DlcFxI18n.tr("dlc_manager.required.installing"));
        progress.setVisible(true);
        progress.setManaged(true);
        retry.setDisable(true);
        retry.setVisible(false);
        retry.setManaged(false);
        Request request = current;
        Thread worker = new Thread(() -> {
            try {
                request.installation().run();
                Platform.runLater(() -> {
                    root.getChildren().remove(overlay);
                    disabled.forEach(Node::setDisable);
                    disabled.clear();
                    current = null;
                    if (!queue.isEmpty()) next();
                    request.onReady().run();
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    String message = exception.getMessage();
                    status.setText(DlcFxI18n.tr("dlc_manager.required.install_failed", message == null ? exception.getClass().getSimpleName() : message));
                    progress.setVisible(false);
                    progress.setManaged(false);
                    retry.setVisible(true);
                    retry.setManaged(true);
                    retry.setDisable(false);
                    retry.requestFocus();
                });
            }
        }, "dlc-required-install");
        worker.setDaemon(true);
        worker.start();
    }
}
