package com.cozooo.dlc_manager.ui.notice;

import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class DlcFxNoticeOverlay {
    private final Scene scene;
    private final DlcNoticeConfig config;
    private final Runnable onSaved;
    private StackPane overlay;
    private Label titleLabel;
    private Label subheadLabel;
    private ScrollPane bodyScroll;
    private String contentLang = "";
    private String resolvedContents = "";
    private CompletableFuture<String> contentFuture;

    public DlcFxNoticeOverlay(Scene scene, DlcNoticeConfig config) {
        this(scene, config, null);
    }

    public DlcFxNoticeOverlay(Scene scene, DlcNoticeConfig config, Runnable onSaved) {
        this.scene = scene;
        this.config = config;
        this.onSaved = onSaved;
    }

    public void show() {
        if (scene == null || config == null || !(scene.getRoot() instanceof StackPane root)) {
            return;
        }
        if (DlcFxModalOverlay.isShowing(scene)) {
            return;
        }
        overlay = new StackPane();
        overlay.getStyleClass().add("dlc-modal-overlay");
        overlay.setPickOnBounds(true);
        overlay.setMouseTransparent(false);
        overlay.setOnMouseClicked(event -> event.consume());

        BorderPane panel = new BorderPane();
        panel.getStyleClass().add("dlc-notice-panel");
        panel.setPrefWidth(Math.min(920, Math.max(680, scene.getWidth() - 120)));
        panel.setPrefHeight(Math.min(640, Math.max(460, scene.getHeight() - 120)));
        panel.setMaxWidth(920);
        panel.setMaxHeight(640);
        panel.setOnMouseClicked(event -> event.consume());

        panel.setTop(header());
        panel.setCenter(body());
        panel.setBottom(footer());

        overlay.getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.CENTER);
        root.getChildren().add(overlay);
        refreshContent();
    }

    private Node header() {
        titleLabel = new Label();
        titleLabel.getStyleClass().add("dlc-notice-title");
        titleLabel.setWrapText(true);

        subheadLabel = new Label();
        subheadLabel.getStyleClass().add("dlc-notice-subhead");
        subheadLabel.setWrapText(true);
        subheadLabel.setAlignment(Pos.BOTTOM_RIGHT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        HBox header = new HBox(18, titleLabel, subheadLabel);
        header.setAlignment(Pos.BOTTOM_LEFT);
        header.getStyleClass().add("dlc-notice-header");
        return header;
    }

    private Node body() {
        bodyScroll = new ScrollPane();
        bodyScroll.setFitToWidth(true);
        bodyScroll.getStyleClass().add("dlc-rich-text-pane");
        return bodyScroll;
    }

    private Node footer() {
        CheckBox neverShow = new CheckBox(DlcFxI18n.tr("dlc_manager.notice.never_show"));
        boolean showNever = config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE;
        neverShow.setVisible(showNever);
        neverShow.setManaged(showNever);
        neverShow.setSelected(config.isNeverShow());

        Button read = new Button(DlcFxI18n.tr("dlc_manager.notice.read"));
        DlcFxStyle.primary(read);
        read.setOnAction(event -> {
            if (showNever) {
                config.setNeverShow(neverShow.isSelected());
                config.save();
                if (onSaved != null) {
                    onSaved.run();
                }
            }
            close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, neverShow, spacer, read);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("dlc-notice-footer");
        return footer;
    }

    private void refreshContent() {
        String lang = CrossPlatformRuntime.languageCode();
        if (Objects.equals(contentLang, lang) && bodyScroll.getContent() != null) {
            return;
        }
        contentLang = lang == null ? "" : lang;
        titleLabel.setText(config.getTitle().resolve(contentLang));
        String subhead = config.getSubhead().text.resolve(contentLang);
        boolean showSubhead = config.getSubhead().enabled && subhead != null && !subhead.isBlank();
        subheadLabel.setText(showSubhead ? subhead : "");
        subheadLabel.setVisible(showSubhead);
        subheadLabel.setManaged(showSubhead);

        String fallback = config.getContents().resolve(contentLang);
        String url = config.getContentUrl(contentLang);
        if (url == null || url.isBlank()) {
            setBody(fallback);
            return;
        }
        setBody(DlcFxI18n.tr("dlc_manager.notice.loading"));
        String langKey = contentLang;
        contentFuture = CompletableFuture.supplyAsync(() -> {
            String remote = config.resolveContents(langKey);
            return (remote == null || remote.isBlank()) ? fallback : remote;
        });
        contentFuture.whenComplete((text, error) -> Platform.runLater(() -> {
            if (contentFuture == null || !Objects.equals(contentLang, langKey)) {
                return;
            }
            setBody(error == null ? text : fallback);
            contentFuture = null;
        }));
    }

    private void setBody(String markdown) {
        resolvedContents = markdown == null || markdown.isBlank() ? "" : markdown;
        TextFlow flow = DlcFxRichTextRenderer.markdown(resolvedContents);
        flow.minWidthProperty().bind(bodyScroll.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        flow.prefWidthProperty().bind(bodyScroll.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        VBox content = new VBox(flow);
        content.minWidthProperty().bind(bodyScroll.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        content.prefWidthProperty().bind(bodyScroll.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        content.getStyleClass().add("dlc-notice-body");
        bodyScroll.setContent(content);
    }

    private void close() {
        if (overlay != null && overlay.getParent() instanceof StackPane root) {
            root.getChildren().remove(overlay);
        }
        overlay = null;
    }
}
