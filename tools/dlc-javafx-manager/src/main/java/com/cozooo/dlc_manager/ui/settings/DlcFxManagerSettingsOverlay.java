package com.cozooo.dlc_manager.ui.settings;

import com.cozooo.dlc_manager.server.DlcFxServerSyncConfig;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public final class DlcFxManagerSettingsOverlay {
    private final Scene scene;
    private final DlcFxPlatformProvider provider;
    private final Runnable onLanguageChanged;

    public DlcFxManagerSettingsOverlay(Scene scene, DlcFxPlatformProvider provider, Runnable onLanguageChanged) {
        this.scene = scene;
        this.provider = provider;
        this.onLanguageChanged = onLanguageChanged;
    }

    public void show() {
        if (scene == null || !(scene.getRoot() instanceof StackPane root)) {
            return;
        }
        if (DlcFxModalOverlay.isShowing(scene)) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("dlc-modal-overlay");
        overlay.setPickOnBounds(true);
        overlay.setMouseTransparent(false);
        overlay.setOnMouseClicked(event -> event.consume());

        BorderPane panel = new BorderPane();
        panel.getStyleClass().add("dlc-manager-settings-panel");
        panel.setMaxWidth(860);
        panel.setMaxHeight(560);
        panel.setPrefWidth(Math.min(860, Math.max(680, scene.getWidth() - 96)));
        panel.setPrefHeight(Math.min(560, Math.max(420, scene.getHeight() - 96)));
        panel.setOnMouseClicked(event -> event.consume());

        panel.setTop(header());
        panel.setCenter(content());
        panel.setBottom(footer(overlay));

        overlay.getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.CENTER);
        root.getChildren().add(overlay);
    }

    public static Canvas gearIcon() {
        Canvas canvas = new Canvas(18, 18);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(0.75, 0.75);
        g.setStroke(Color.web("#0f172a"));
        g.setLineWidth(2.6);
        g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        g.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        g.beginPath();
        g.moveTo(12.0, 2.0);
        g.bezierCurveTo(13.4, 2.0, 14.3, 3.0, 14.3, 4.3);
        g.lineTo(14.3, 5.4);
        g.bezierCurveTo(14.3, 6.1, 15.0, 6.4, 15.5, 5.9);
        g.lineTo(16.3, 5.1);
        g.bezierCurveTo(17.3, 4.2, 18.8, 4.2, 19.7, 5.1);
        g.bezierCurveTo(20.6, 6.0, 20.6, 7.5, 19.7, 8.5);
        g.lineTo(18.9, 9.2);
        g.bezierCurveTo(18.5, 9.6, 18.8, 10.4, 19.4, 10.4);
        g.lineTo(20.6, 10.4);
        g.bezierCurveTo(21.9, 10.4, 22.9, 11.3, 22.9, 12.6);
        g.bezierCurveTo(22.9, 13.9, 21.9, 14.8, 20.6, 14.8);
        g.lineTo(19.4, 14.8);
        g.bezierCurveTo(18.8, 14.8, 18.5, 15.5, 18.9, 16.0);
        g.lineTo(19.7, 16.7);
        g.bezierCurveTo(20.6, 17.7, 20.6, 19.2, 19.7, 20.1);
        g.bezierCurveTo(18.8, 21.0, 17.3, 21.0, 16.3, 20.1);
        g.lineTo(15.5, 19.3);
        g.bezierCurveTo(15.0, 18.9, 14.3, 19.2, 14.3, 19.8);
        g.lineTo(14.3, 20.9);
        g.bezierCurveTo(14.3, 22.2, 13.4, 23.2, 12.0, 23.2);
        g.bezierCurveTo(10.6, 23.2, 9.7, 22.2, 9.7, 20.9);
        g.lineTo(9.7, 19.8);
        g.bezierCurveTo(9.7, 19.2, 9.0, 18.9, 8.5, 19.3);
        g.lineTo(7.7, 20.1);
        g.bezierCurveTo(6.7, 21.0, 5.2, 21.0, 4.3, 20.1);
        g.bezierCurveTo(3.4, 19.2, 3.4, 17.7, 4.3, 16.7);
        g.lineTo(5.1, 16.0);
        g.bezierCurveTo(5.5, 15.5, 5.2, 14.8, 4.6, 14.8);
        g.lineTo(3.4, 14.8);
        g.bezierCurveTo(2.1, 14.8, 1.1, 13.9, 1.1, 12.6);
        g.bezierCurveTo(1.1, 11.3, 2.1, 10.4, 3.4, 10.4);
        g.lineTo(4.6, 10.4);
        g.bezierCurveTo(5.2, 10.4, 5.5, 9.6, 5.1, 9.2);
        g.lineTo(4.3, 8.5);
        g.bezierCurveTo(3.4, 7.5, 3.4, 6.0, 4.3, 5.1);
        g.bezierCurveTo(5.2, 4.2, 6.7, 4.2, 7.7, 5.1);
        g.lineTo(8.5, 5.9);
        g.bezierCurveTo(9.0, 6.4, 9.7, 6.1, 9.7, 5.4);
        g.lineTo(9.7, 4.3);
        g.bezierCurveTo(9.7, 3.0, 10.6, 2.0, 12.0, 2.0);
        g.stroke();
        g.strokeOval(9.2, 9.8, 5.6, 5.6);
        return canvas;
    }

    private Node header() {
        VBox box = new VBox(4);
        Label title = new Label(DlcFxI18n.tr("dlc_manager.manager_settings.title"));
        title.getStyleClass().add("dlc-config-title");
        box.getChildren().add(title);
        box.getStyleClass().add("dlc-config-header");
        return box;
    }

    private Node content() {
        VBox content = new VBox(14);
        content.setFillWidth(true);
        content.getStyleClass().add("dlc-manager-settings-content");
        DlcFxServerSyncConfig syncConfig = DlcFxServerSyncConfig.load();

        Label sectionTitle = new Label(DlcFxI18n.tr("dlc_manager.manager_settings.general"));
        DlcFxStyle.add(sectionTitle, "dlc-section-title");

        HBox languageRow = new HBox(16);
        languageRow.getStyleClass().add("dlc-manager-settings-row");
        languageRow.setAlignment(Pos.CENTER_LEFT);
        languageRow.setMaxWidth(Double.MAX_VALUE);
        Label label = new Label(DlcFxI18n.tr("dlc_manager.language.label"));
        DlcFxStyle.add(label, "dlc-config-option-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node combo = languageComboBox();
        languageRow.getChildren().addAll(label, spacer, combo);

        Label syncSectionTitle = new Label(DlcFxI18n.tr("dlc_manager.manager_settings.external_sync"));
        DlcFxStyle.add(syncSectionTitle, "dlc-section-title");
        Label syncHint = new Label(DlcFxI18n.tr("dlc_manager.manager_settings.external_sync_hint"));
        DlcFxStyle.muted(syncHint);

        HBox transportRow = new HBox(16);
        transportRow.getStyleClass().add("dlc-manager-settings-row");
        transportRow.setAlignment(Pos.CENTER_LEFT);
        transportRow.setMaxWidth(Double.MAX_VALUE);
        Label transportLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_transport"));
        DlcFxStyle.add(transportLabel, "dlc-config-option-name");
        Region transportSpacer = new Region();
        HBox.setHgrow(transportSpacer, Priority.ALWAYS);
        transportRow.getChildren().addAll(transportLabel, transportSpacer, syncTransportComboBox(syncConfig));

        HBox externalAddressRow = new HBox(16);
        externalAddressRow.getStyleClass().add("dlc-manager-settings-row");
        externalAddressRow.setAlignment(Pos.CENTER_LEFT);
        externalAddressRow.setMaxWidth(Double.MAX_VALUE);
        Label addressLabel = new Label(DlcFxI18n.tr("dlc_manager.server.sync_external_address"));
        DlcFxStyle.add(addressLabel, "dlc-config-option-name");
        Region addressSpacer = new Region();
        HBox.setHgrow(addressSpacer, Priority.ALWAYS);
        TextField externalAddress = new TextField(syncConfig.externalAddress());
        externalAddress.setPromptText(DlcFxI18n.tr("dlc_manager.server.sync_external_address_prompt"));
        externalAddress.setMinWidth(320);
        externalAddress.setPrefWidth(360);
        externalAddress.textProperty().addListener((obs, oldValue, newValue) -> {
            syncConfig.setExternalAddress(newValue);
            syncConfig.save();
        });
        externalAddressRow.getChildren().addAll(addressLabel, addressSpacer, externalAddress);

        content.getChildren().addAll(sectionTitle, languageRow, syncSectionTitle, syncHint, transportRow, externalAddressRow);
        return content;
    }

    private Node footer(StackPane overlay) {
        Button close = new Button(DlcFxI18n.tr("dlc_manager.close"));
        close.setOnAction(event -> close(overlay));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, spacer, close);
        footer.getStyleClass().add("dlc-config-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }

    private Node languageComboBox() {
        ComboBox<LanguageOption> languageBox = new ComboBox<>();
        languageBox.getItems().addAll(
                new LanguageOption("en_us", DlcFxI18n.tr("dlc_manager.language.en_us")),
                new LanguageOption("zh_cn", DlcFxI18n.tr("dlc_manager.language.zh_cn")));
        languageBox.setValue(optionForCurrentLanguage(languageBox));
        languageBox.setOnAction(event -> {
            LanguageOption option = languageBox.getValue();
            if (option != null && !option.code().equalsIgnoreCase(provider.languageCode())) {
                provider.setLanguageOverride(option.code());
                closeTopOverlay();
                if (onLanguageChanged != null) {
                    onLanguageChanged.run();
                }
                new DlcFxManagerSettingsOverlay(scene, provider, onLanguageChanged).show();
            }
        });
        languageBox.setMinWidth(190);
        languageBox.setPrefWidth(190);
        return languageBox;
    }

    private Node syncTransportComboBox(DlcFxServerSyncConfig config) {
        ComboBox<SyncTransportOption> transportBox = new ComboBox<>();
        transportBox.getItems().addAll(
                new SyncTransportOption(DlcFxServerSyncConfig.TransportMode.DLC_TCP, DlcFxI18n.tr("dlc_manager.server.sync_transport_dlc_tcp")),
                new SyncTransportOption(DlcFxServerSyncConfig.TransportMode.WEBRTC_WSS, DlcFxI18n.tr("dlc_manager.server.sync_transport_webrtc_wss")));
        transportBox.setValue(optionForTransport(transportBox, config.transportMode()));
        transportBox.setOnAction(event -> {
            SyncTransportOption option = transportBox.getValue();
            config.setTransportMode(option == null ? DlcFxServerSyncConfig.TransportMode.DLC_TCP : option.mode());
            config.save();
        });
        transportBox.setMinWidth(190);
        transportBox.setPrefWidth(190);
        return transportBox;
    }

    private SyncTransportOption optionForTransport(ComboBox<SyncTransportOption> transportBox, DlcFxServerSyncConfig.TransportMode mode) {
        for (SyncTransportOption option : transportBox.getItems()) {
            if (option.mode() == mode) {
                return option;
            }
        }
        return transportBox.getItems().isEmpty() ? null : transportBox.getItems().get(0);
    }

    private LanguageOption optionForCurrentLanguage(ComboBox<LanguageOption> languageBox) {
        String current = provider.languageCode();
        for (LanguageOption option : languageBox.getItems()) {
            if (option.code().equalsIgnoreCase(current)) {
                return option;
            }
        }
        return languageBox.getItems().isEmpty() ? null : languageBox.getItems().get(0);
    }

    private void close(StackPane overlay) {
        if (overlay.getParent() instanceof StackPane root) {
            root.getChildren().remove(overlay);
        }
    }

    private void closeTopOverlay() {
        if (scene == null || !(scene.getRoot() instanceof StackPane root) || root.getChildren().size() <= 1) {
            return;
        }
        root.getChildren().remove(root.getChildren().size() - 1);
    }

    private record LanguageOption(String code, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private record SyncTransportOption(DlcFxServerSyncConfig.TransportMode mode, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
