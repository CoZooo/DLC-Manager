package com.cozooo.dlc_manager.ui.update;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;

import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

import java.util.Collection;
import java.util.List;

public final class DlcFxUpdateShell<T> {
    public interface Adapter<T> {
        String transitionText(T row);
        String changelog(T row);
        Tag tag(T row);
        Action action(T row);
        void performAction(T row);
    }

    public enum Tag {
        MANAGER,
        AVAILABLE,
        ROLLED_BACK,
        HISTORY
    }

    public enum Action {
        NONE,
        APPLY,
        REAPPLY,
        ROLLBACK
    }

    private final Adapter<T> adapter;
    private final ObservableList<T> rows = FXCollections.observableArrayList();
    private final BorderPane root = new BorderPane();
    private final Label versionLabel = new Label(DlcFxI18n.tr("dlc_manager.update.version_empty"));
    private final Label rollbackInfoLabel = new Label();
    private final Label updateHintLabel = new Label();
    private final ListView<T> historyView = new ListView<>(rows);
    private final ScrollPane changelogPane = new ScrollPane();
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final Button refreshBtn;
    private final Button importBtn;
    private final Button importUrlBtn;
    private final Button checkPackUpdateBtn;
    private boolean busy;
    private boolean available;

    public DlcFxUpdateShell(Adapter<T> adapter,
                            Label statusLabel,
                            ProgressBar progressBar,
                            Runnable refreshAction,
                            Runnable importAction,
                            Runnable importUrlAction,
                            Runnable checkPackUpdateAction) {
        this(
            adapter,
            statusLabel,
            progressBar,
            DlcFxI18n.tr("dlc_manager.update.check_local"),
            DlcFxI18n.tr("dlc_manager.update.import_package"),
            DlcFxI18n.tr("dlc_manager.update.import_url"),
            DlcFxI18n.tr("dlc_manager.pack_update.check_button"),
            refreshAction,
            importAction,
            importUrlAction,
            checkPackUpdateAction
        );
    }

    public DlcFxUpdateShell(Adapter<T> adapter,
                            Label statusLabel,
                            ProgressBar progressBar,
                            String refreshText,
                            String importText,
                            String importUrlText,
                            String extraActionText,
                            Runnable refreshAction,
                            Runnable importAction,
                            Runnable importUrlAction,
                            Runnable checkPackUpdateAction) {
        this.adapter = adapter;
        this.statusLabel = statusLabel;
        this.progressBar = progressBar;
        this.refreshBtn = new Button(refreshText);
        this.importBtn = new Button(importText);
        this.importUrlBtn = new Button(importUrlText);
        this.checkPackUpdateBtn = new Button(extraActionText);

        DlcFxStyle.view(root);
        configureHeader();
        configureCenter();
        configureBottom(refreshAction, importAction, importUrlAction, checkPackUpdateAction);
        updateActionDisableState(true);
    }

    public Node root() {
        return root;
    }

    public ListView<T> listView() {
        return historyView;
    }

    public void bindAvailable(BooleanProperty availableProperty) {
        if (availableProperty == null) {
            return;
        }
        setAvailable(availableProperty.get());
        availableProperty.addListener((obs, oldValue, newValue) -> setAvailable(newValue));
    }

    public void setAvailable(boolean available) {
        this.available = available;
        updateActionDisableState(true);
    }

    public void setBusy(boolean busy, String status) {
        this.busy = busy;
        updateActionDisableState(true);
        historyView.refresh();
        if (busy) {
            progressBar.setVisible(true);
            if (status != null) {
                statusLabel.setText(status);
            }
        } else {
            progressBar.setVisible(false);
        }
    }

    public boolean isBusy() {
        return busy;
    }

    public void setRows(Collection<T> nextRows) {
        rows.setAll(nextRows == null ? List.of() : nextRows);
    }

    public ObservableList<T> rows() {
        return rows;
    }

    public void clearRows() {
        rows.clear();
    }

    public void refreshRows() {
        historyView.refresh();
    }

    public void setVersionText(String text) {
        versionLabel.setText(text == null ? "" : text);
    }

    public void setRollbackInfo(String text) {
        setOptionalHeaderText(rollbackInfoLabel, text);
    }

    public void setUpdateHint(String text) {
        setOptionalHeaderText(updateHintLabel, text);
    }

    public void setChangelogText(String text) {
        String value = text == null ? "" : text;
        if (value.isBlank()) {
            Label prompt = new Label(DlcFxI18n.tr("dlc_manager.update.changelog_prompt"));
            DlcFxStyle.muted(prompt);
            bindChangelogContentWidth(prompt);
            changelogPane.setContent(prompt);
            return;
        }
        TextFlow flow = DlcFxRichTextRenderer.markdown(value);
        bindChangelogContentWidth(flow);
        changelogPane.setContent(flow);
    }

    public void setProgressVisible(boolean visible) {
        progressBar.setVisible(visible);
    }

    public void setProgress(double progress) {
        progressBar.setProgress(progress);
    }

    public void setPackUpdateAvailable(boolean available) {
        checkPackUpdateBtn.setDisable(busy || !this.available || !available);
    }

    public void updateActionDisableState(boolean packUpdateAvailable) {
        boolean disabled = busy || !available;
        refreshBtn.setDisable(disabled);
        importBtn.setDisable(disabled);
        importUrlBtn.setDisable(disabled);
        checkPackUpdateBtn.setDisable(disabled || !packUpdateAvailable);
    }

    private void configureHeader() {
        DlcFxStyle.title(versionLabel);
        DlcFxStyle.muted(rollbackInfoLabel);
        DlcFxStyle.muted(updateHintLabel);
        VBox topBox = new VBox(2, versionLabel, rollbackInfoLabel, updateHintLabel);
        DlcFxStyle.toolbar(topBox);
        DlcFxStyle.add(topBox, "dlc-update-header");
        root.setTop(topBox);
        setOptionalHeaderText(rollbackInfoLabel, "");
        setOptionalHeaderText(updateHintLabel, "");
    }

    private void configureCenter() {
        changelogPane.setFitToWidth(true);
        changelogPane.setMinWidth(0);
        DlcFxStyle.add(changelogPane, "dlc-rich-text-pane");
        setChangelogText("");

        historyView.setCellFactory(param -> new UpdateRowCell());
        historyView.setSelectionModel(null);
        DlcFxStyle.add(historyView, "dlc-update-list");

        Label historyHeader = new Label(DlcFxI18n.tr("dlc_manager.update.version_list_header"));
        DlcFxStyle.title(historyHeader);
        VBox leftBox = new VBox(6, historyHeader, DlcFxListViewSupport.wrap(historyView));
        leftBox.setPadding(new Insets(0, 4, 0, 0));
        leftBox.setMinWidth(0);
        VBox.setVgrow(leftBox.getChildren().get(1), Priority.ALWAYS);

        Label changelogHeader = new Label(DlcFxI18n.tr("dlc_manager.update.changelog_header"));
        DlcFxStyle.title(changelogHeader);
        VBox rightBox = new VBox(6, changelogHeader, changelogPane);
        rightBox.setMinWidth(0);
        rightBox.setPadding(new Insets(0, 0, 0, 4));
        VBox.setVgrow(changelogPane, Priority.ALWAYS);

        HBox centerBox = new HBox(10, leftBox, rightBox);
        leftBox.prefWidthProperty().bind(centerBox.widthProperty().subtract(42).divide(2));
        rightBox.prefWidthProperty().bind(centerBox.widthProperty().subtract(42).divide(2));
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        centerBox.setPadding(new Insets(14, 16, 12, 16));
        DlcFxStyle.section(centerBox);
        root.setCenter(centerBox);
    }

    private void configureBottom(Runnable refreshAction,
                                 Runnable importAction,
                                 Runnable importUrlAction,
                                 Runnable checkPackUpdateAction) {
        refreshBtn.setOnAction(e -> run(refreshAction));
        importBtn.setOnAction(e -> run(importAction));
        importUrlBtn.setOnAction(e -> run(importUrlAction));
        checkPackUpdateBtn.setOnAction(e -> run(checkPackUpdateAction));
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(checkPackUpdateBtn);
        HBox actions = new HBox(8, refreshBtn, importBtn, importUrlBtn, checkPackUpdateBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionPanel = new HBox(8, actions, spacer, progressBar);
        actionPanel.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.actionPanel(actionPanel);
        DlcFxStyle.routeStatus(statusLabel);
        root.setBottom(actionPanel);
    }

    private void bindChangelogContentWidth(Region content) {
        content.minWidthProperty().bind(changelogPane.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        content.prefWidthProperty().bind(changelogPane.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
        content.maxWidthProperty().bind(changelogPane.viewportBoundsProperty().map(bounds -> bounds.getWidth()));
    }

    private static void setOptionalHeaderText(Label label, String text) {
        String value = text == null ? "" : text;
        label.setText(value);
        boolean visible = !value.isBlank();
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    public static String describeTransition(DlcUpdateRowPlanner.UpdateRow item) {
        String label = safe(item.displayOldVersion) + " -> " + safe(item.displayNewVersion);
        if (item.type == DlcUpdateRowPlanner.Type.MANAGER) {
            return DlcFxI18n.tr("dlc_manager.update.manager_prefix") + label;
        }
        DlcUpdateMeta meta = item.meta;
        String sourceBase = meta == null ? null : meta.getBaseOld();
        String sourceDlc = meta == null ? null : meta.getDlcOld();
        String targetBase = meta == null ? null : meta.getBaseNew();
        String targetDlc = meta == null ? null : meta.getDlcNew();
        if (targetBase == null || targetBase.isBlank()) {
            targetBase = sourceBase;
        }
        if (targetDlc == null || targetDlc.isBlank()) {
            targetDlc = sourceDlc;
        }
        boolean baseChanged = !normalizeVersion(sourceBase).equals(normalizeVersion(targetBase));
        boolean dlcChanged = !normalizeVersion(sourceDlc).equals(normalizeVersion(targetDlc));
        if (baseChanged && !dlcChanged) {
            String prefixKey = (sourceDlc == null || sourceDlc.isBlank())
                && (targetDlc == null || targetDlc.isBlank())
                ? "dlc_manager.pack_update.version"
                : "dlc_manager.update.base_prefix";
            label = DlcFxI18n.tr(prefixKey) + normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase);
        } else if (!baseChanged && dlcChanged) {
            label = DlcFxI18n.tr("dlc_manager.update.dlc_prefix") + normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc);
        }
        return label;
    }

    public static Tag tagFor(DlcUpdateRowPlanner.UpdateRow item) {
        if (item.type == DlcUpdateRowPlanner.Type.MANAGER) {
            return Tag.MANAGER;
        }
        if (item.action == DlcUpdateRowPlanner.Action.UPDATE) {
            return Tag.AVAILABLE;
        }
        if (item.action == DlcUpdateRowPlanner.Action.REUPDATE) {
            return Tag.ROLLED_BACK;
        }
        return Tag.HISTORY;
    }

    public static Action actionFor(DlcUpdateRowPlanner.UpdateRow item) {
        return switch (item.action) {
            case UPDATE -> Action.APPLY;
            case REUPDATE -> Action.REAPPLY;
            case ROLLBACK, MANAGER_ROLLBACK -> Action.ROLLBACK;
            default -> Action.NONE;
        };
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private final class UpdateRowCell extends javafx.scene.control.ListCell<T> {
        private final Label transitionLabel = new Label();
        private final Label tagLabel = new Label();
        private final Button actionButton = new Button();
        private final HBox row = new HBox(10);

        UpdateRowCell() {
            DlcFxStyle.title(transitionLabel);
            transitionLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(transitionLabel, Priority.ALWAYS);
            actionButton.setOnAction(e -> {
                T item = getItem();
                if (item != null) {
                    adapter.performAction(item);
                }
            });
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().setAll(tagLabel, transitionLabel, actionButton);
            DlcFxStyle.add(row, "dlc-update-row");
            setOnMouseEntered(e -> {
                T item = getItem();
                setChangelogText(item == null ? "" : adapter.changelog(item));
            });
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            transitionLabel.setText(adapter.transitionText(item));
            configureTag(adapter.tag(item));
            configureAction(adapter.action(item));
            setGraphic(row);
            setText(null);
        }

        private void configureTag(Tag tag) {
            switch (tag) {
                case MANAGER -> {
                    tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_manager"));
                    DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_NEUTRAL);
                }
                case AVAILABLE -> {
                    tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_available"));
                    DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_OK);
                }
                case ROLLED_BACK -> {
                    tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_rolled_back"));
                    DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_WARN);
                }
                case HISTORY -> {
                    tagLabel.setText(DlcFxI18n.tr("dlc_manager.update.tag_history"));
                    DlcFxStyle.chip(tagLabel, DlcFxStyle.CHIP_NEUTRAL);
                }
            }
        }

        private void configureAction(Action action) {
            actionButton.getStyleClass().remove(DlcFxStyle.PRIMARY);
            actionButton.getStyleClass().remove(DlcFxStyle.DANGER);
            switch (action) {
                case APPLY -> {
                    actionButton.setText(DlcFxI18n.tr("dlc_manager.update.apply"));
                    DlcFxStyle.primary(actionButton);
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);
                }
                case REAPPLY -> {
                    actionButton.setText(DlcFxI18n.tr("dlc_manager.update.reapply"));
                    DlcFxStyle.primary(actionButton);
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);
                }
                case ROLLBACK -> {
                    actionButton.setText(DlcFxI18n.tr("dlc_manager.update.rollback"));
                    DlcFxStyle.danger(actionButton);
                    actionButton.setVisible(true);
                    actionButton.setManaged(true);
                }
                case NONE -> {
                    actionButton.setVisible(false);
                    actionButton.setManaged(false);
                }
            }
            actionButton.setDisable(busy);
        }
    }
}
