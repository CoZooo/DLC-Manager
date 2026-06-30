package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
import com.cozooo.dlc_manager.ui.DlcFxStyle;
import com.cozooo.dlc_manager.ui.DlcFxTooltips;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class DlcFxLocalConfigOverlay {
    private final Scene scene;
    private final Runnable onSaved;
    private final DlcFxLocalConfigModel model = new DlcFxLocalConfigModel();
    private final VBox tabList = new VBox(6);
    private final VBox optionList = new VBox(8);
    private final Button saveButton = new Button(DlcFxI18n.tr("dlc_manager.config.local_save"));
    private final Button cancelButton = new Button(DlcFxI18n.tr("dlc_manager.config.local_cancel"));
    private StackPane overlay;
    private DlcFxLocalConfigModel.Tab currentTab = DlcFxLocalConfigModel.Tab.PATHS;

    public DlcFxLocalConfigOverlay(Scene scene, Runnable onSaved) {
        this.scene = scene;
        this.onSaved = onSaved;
    }

    public void show() {
        if (scene == null || !(scene.getRoot() instanceof StackPane root)) {
            return;
        }
        if (DlcFxModalOverlay.isShowing(scene)) {
            return;
        }
        model.loadFromConfig();
        overlay = new StackPane();
        overlay.getStyleClass().add("dlc-modal-overlay");
        overlay.setPickOnBounds(true);
        overlay.setMouseTransparent(false);
        overlay.setOnMouseClicked(event -> event.consume());

        BorderPane panel = new BorderPane();
        panel.getStyleClass().add("dlc-config-panel");
        panel.setMaxWidth(1040);
        panel.setMaxHeight(700);
        panel.setPrefWidth(Math.min(1040, Math.max(820, scene.getWidth() - 72)));
        panel.setPrefHeight(Math.min(700, Math.max(560, scene.getHeight() - 72)));
        panel.setOnMouseClicked(event -> event.consume());

        panel.setTop(header());
        panel.setLeft(leftTabs());
        panel.setCenter(centerOptions());
        panel.setBottom(footer());

        overlay.getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.CENTER);
        root.getChildren().add(overlay);
        selectTab(currentTab);
    }

    private Node header() {
        VBox box = new VBox(4);
        Label title = new Label(DlcFxI18n.tr("dlc_manager.config.menu.title"));
        title.getStyleClass().add("dlc-config-title");
        Label desc = new Label(DlcFxI18n.tr("dlc_manager.config.local_desc"));
        DlcFxStyle.muted(desc);
        desc.setWrapText(true);
        box.getChildren().addAll(title, desc);
        box.getStyleClass().add("dlc-config-header");
        return box;
    }

    private Node leftTabs() {
        tabList.getChildren().clear();
        for (DlcFxLocalConfigModel.Tab tab : DlcFxLocalConfigModel.Tab.values()) {
            Button button = new Button(tab.label());
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("dlc-config-tab-button");
            button.setOnAction(event -> selectTab(tab));
            button.setUserData(tab);
            tabList.getChildren().add(button);
        }
        tabList.getStyleClass().add("dlc-config-tabs");
        return tabList;
    }

    private Node centerOptions() {
        optionList.getStyleClass().add("dlc-config-option-list");
        ScrollPane scroll = new ScrollPane(optionList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dlc-config-scroll");
        return scroll;
    }

    private Node footer() {
        DlcFxStyle.primary(saveButton);
        saveButton.setOnAction(event -> saveAndClose());
        cancelButton.setOnAction(event -> cancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, spacer, cancelButton, saveButton);
        footer.getStyleClass().add("dlc-config-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        updateSaveButton();
        return footer;
    }

    private void selectTab(DlcFxLocalConfigModel.Tab tab) {
        currentTab = tab;
        for (Node child : tabList.getChildren()) {
            child.getStyleClass().remove("selected");
            if (child instanceof Button button && button.getUserData() == tab) {
                child.getStyleClass().add("selected");
            }
        }
        optionList.getChildren().setAll(model.options(tab).stream().map(this::optionRow).toList());
        updateSaveButton();
    }

    private Node optionRow(DlcFxLocalConfigModel.Option option) {
        Label label = new Label(option.label());
        label.getStyleClass().add("dlc-config-option-title");
        label.setTooltip(tooltip(option.comment()));
        label.setMaxWidth(Double.MAX_VALUE);

        Button reset = new Button(DlcFxI18n.tr("dlc_manager.config.reset"));
        reset.getStyleClass().add("dlc-config-reset-button");
        updateResetButton(reset, option);
        reset.setOnAction(event -> confirmReset(option));
        Node editor = editorFor(option, reset);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(12, editor, reset);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.getStyleClass().add("dlc-config-option-controls");

        HBox row = new HBox(12, label, spacer, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dlc-config-option-row");
        Tooltip rowTooltip = tooltip(option.comment());
        DlcFxTooltips.install(row, rowTooltip);
        return row;
    }

    private Tooltip tooltip(String text) {
        return DlcFxTooltips.plain(text);
    }

    private Node editorFor(DlcFxLocalConfigModel.Option option, Button resetButton) {
        return switch (option.type()) {
            case BOOLEAN -> booleanEditor(option, resetButton);
            case INTEGER -> integerEditor(option, resetButton);
            case CHOICE -> choiceEditor(option, resetButton);
            case STRING -> stringEditor(option, resetButton);
        };
    }

    private Node stringEditor(DlcFxLocalConfigModel.Option option, Button resetButton) {
        TextField field = new TextField(option.value());
        field.setPrefWidth(360);
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            option.setValueFromString(newValue);
            updateResetButton(resetButton, option);
            updateSaveButton();
        });
        return field;
    }

    private Node integerEditor(DlcFxLocalConfigModel.Option option, Button resetButton) {
        int min = option.min() == null ? Integer.MIN_VALUE : option.min();
        int max = option.max() == null ? Integer.MAX_VALUE : option.max();
        TextField field = new TextField(String.valueOf(option.integerValue()));
        field.getStyleClass().add("dlc-config-number-field");
        field.setPrefWidth(180);
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            option.setValueFromString(newValue);
            updateResetButton(resetButton, option);
            updateSaveButton();
        });
        field.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                int value = option.integerValue();
                value = Math.max(min, Math.min(max, value));
                option.setValueFromString(String.valueOf(value));
                field.setText(String.valueOf(value));
                updateResetButton(resetButton, option);
                updateSaveButton();
            }
        });
        return field;
    }

    private Node booleanEditor(DlcFxLocalConfigModel.Option option, Button resetButton) {
        CheckBox box = new CheckBox(DlcFxI18n.tr(option.booleanValue() ? "dlc_manager.common.yes" : "dlc_manager.common.no"));
        box.setSelected(option.booleanValue());
        box.setMinWidth(110);
        box.selectedProperty().addListener((obs, oldValue, newValue) -> {
            option.setValueFromString(String.valueOf(newValue));
            box.setText(DlcFxI18n.tr(newValue ? "dlc_manager.common.yes" : "dlc_manager.common.no"));
            updateResetButton(resetButton, option);
            updateSaveButton();
        });
        return box;
    }

    private Node choiceEditor(DlcFxLocalConfigModel.Option option, Button resetButton) {
        ComboBox<ChoiceItem> combo = new ComboBox<>();
        for (String value : option.choiceValues()) {
            combo.getItems().add(new ChoiceItem(value, option.choiceLabel(value)));
        }
        combo.setValue(combo.getItems().stream()
                .filter(item -> item.value().equals(option.value()))
                .findFirst()
                .orElse(combo.getItems().isEmpty() ? null : combo.getItems().get(0)));
        combo.setPrefWidth(320);
        combo.setOnAction(event -> {
            ChoiceItem item = combo.getValue();
            if (item != null) {
                option.setValueFromString(item.value());
                updateResetButton(resetButton, option);
                updateSaveButton();
            }
        });
        return combo;
    }

    private void updateResetButton(Button resetButton, DlcFxLocalConfigModel.Option option) {
        if (resetButton != null && option != null) {
            resetButton.setDisable(option.isDefaultValue());
        }
    }

    private void confirmReset(DlcFxLocalConfigModel.Option option) {
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.config.reset_title"),
                DlcFxI18n.tr("dlc_manager.config.reset_prompt"),
                () -> {
                    option.resetToDefault();
                    selectTab(currentTab);
                },
                DlcFxI18n.tr("dlc_manager.config.reset_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.config.reset_no")
        );
    }

    private void cancel() {
        if (!model.modified()) {
            close();
            return;
        }
        DlcFxModalOverlay.show(
                scene,
                DlcFxI18n.tr("dlc_manager.config.cancel_title"),
                DlcFxI18n.tr("dlc_manager.config.cancel_prompt"),
                () -> {
                    DlcConfig.load();
                    model.loadFromConfig();
                    close();
                },
                DlcFxI18n.tr("dlc_manager.config.cancel_yes"),
                () -> {},
                DlcFxI18n.tr("dlc_manager.config.cancel_no")
        );
    }

    private void saveAndClose() {
        model.applyToConfig();
        DlcConfig.save();
        DlcFxStyle.setStatus(DlcFxI18n.tr("dlc_manager.config.saved_local"));
        if (onSaved != null) {
            onSaved.run();
        }
        close();
    }

    private void close() {
        if (overlay == null || scene == null || !(scene.getRoot() instanceof StackPane root)) {
            return;
        }
        root.getChildren().remove(overlay);
        overlay = null;
    }

    private void updateSaveButton() {
        boolean modified = model.modified();
        saveButton.setDisable(!modified);
    }

    private record ChoiceItem(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
