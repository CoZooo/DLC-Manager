package com.cozooo.dlc_manager.ui.required;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxSelectionModel;
import com.cozooo.dlc_manager.ui.common.DlcFxStatusChip;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTooltips;

import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxRequiredListShell<T> {
    public interface Adapter<T> {
        String id(T item);
        String title(T item);
        String statusText(T item);
        String statusStyle(T item);
        default String tooltip(T item) { return ""; }
        default boolean selectable(T item) { return true; }
        default boolean actionVisible(T item) { return false; }
        default String actionText(T item) { return ""; }
        default boolean actionDanger(T item) { return false; }
        default boolean actionDisabled(T item) { return false; }
        default boolean actionAllowedWhileBusy(T item) { return false; }
        default double progress(T item) { return Double.NaN; }
        default String progressText(T item) { return ""; }
    }

    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final DlcFxSelectionModel<T> selectionModel;
    private final Adapter<T> adapter;
    private final Label statusLabel;
    private final ProgressBar totalProgressBar;
    private final Label totalProgressLabel;
    private final Button refreshBtn;
    private final Button primaryBtn;
    private final Button selectedBtn;
    private final Button cancelBtn;
    private final ListView<T> listView = new ListView<>(items);
    private final HBox listFrame;
    private final Set<ResourceCell> activeCells = Collections.newSetFromMap(new WeakHashMap<>());
    private final Runnable refreshAction;
    private final Runnable primaryAction;
    private final Runnable selectedAction;
    private final Runnable cancelAction;
    private final Function<T, Runnable> itemActionFactory;
    private Predicate<T> selectableFilter;
    private boolean available = true;
    private boolean busy;

    public DlcFxRequiredListShell(Adapter<T> adapter,
                                  Label statusLabel,
                                  ProgressBar totalProgressBar,
                                  Label totalProgressLabel,
                                  String refreshText,
                                  String primaryText,
                                  String selectedText,
                                  Runnable refreshAction,
                                  Runnable primaryAction,
                                  Runnable selectedAction,
                                  Runnable cancelAction,
                                  Function<T, Runnable> itemActionFactory) {
        this.adapter = adapter;
        this.statusLabel = statusLabel;
        this.totalProgressBar = totalProgressBar;
        this.totalProgressLabel = totalProgressLabel;
        this.refreshBtn = new Button(refreshText);
        this.primaryBtn = new Button(primaryText);
        this.selectedBtn = new Button(selectedText);
        this.cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.common.cancel"));
        this.refreshAction = refreshAction;
        this.primaryAction = primaryAction;
        this.selectedAction = selectedAction;
        this.cancelAction = cancelAction;
        this.itemActionFactory = itemActionFactory;
        this.selectionModel = new DlcFxSelectionModel<>(adapter::id);
        this.selectableFilter = adapter::selectable;
        this.listFrame = DlcFxListViewSupport.wrap(listView);

        listView.setCellFactory(param -> new ResourceCell());
        DlcFxStyle.add(listView, "dlc-dlc-list");
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(primaryBtn);
        DlcFxStyle.add(selectedBtn, "dlc-neutral-button");
        DlcFxStyle.danger(cancelBtn);
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);
        refreshBtn.setOnAction(event -> {
            if (refreshAction != null) {
                refreshAction.run();
            }
        });
        primaryBtn.setOnAction(event -> {
            if (primaryAction != null) {
                primaryAction.run();
            }
        });
        selectedBtn.setOnAction(event -> {
            if (selectedAction != null) {
                selectedAction.run();
            }
        });
        cancelBtn.setOnAction(event -> {
            if (cancelAction != null) {
                cancelAction.run();
            }
        });
        DlcFxStyle.routeStatus(statusLabel);
        updateActionState();
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
        updateActionState();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        if (totalProgressBar != null) {
            totalProgressBar.setVisible(busy);
            totalProgressBar.setManaged(busy);
            totalProgressBar.setProgress(busy ? -1 : 0);
        }
        updateActionState();
    }

    public void setCancelVisible(boolean visible) {
        cancelBtn.setVisible(visible);
        cancelBtn.setManaged(visible);
        updateActionState();
    }

    public void setSelectableFilter(Predicate<T> selectableFilter) {
        this.selectableFilter = selectableFilter == null ? adapter::selectable : selectableFilter;
        selectionModel.pruneTo(items);
        syncVisibleItemCells();
        updateActionState();
    }

    public void setItems(Collection<T> nextItems) {
        items.setAll(nextItems == null ? List.of() : nextItems);
        selectionModel.pruneTo(items);
        refreshRows();
    }

    public ObservableList<T> items() {
        return items;
    }

    public List<T> selectedItems() {
        return selectionModel.selectedItems(items);
    }

    public List<String> selectedIds() {
        return selectedItems().stream()
                .filter(this::isSelectable)
                .map(adapter::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    public void clearSelection() {
        selectionModel.clear();
        syncVisibleItemCells();
        updateActionState();
    }

    public HBox topBar(Label countLabel, javafx.scene.Node... extraLeftNodes) {
        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        selectAllBtn.setOnAction(event -> {
            selectionModel.selectAll(items, this::isSelectable);
            syncVisibleItemCells();
            updateActionState();
        });
        selectNoneBtn.setOnAction(event -> {
            selectionModel.clear();
            syncVisibleItemCells();
            updateActionState();
        });
        invertBtn.setOnAction(event -> {
            selectionModel.invert(items, this::isSelectable);
            syncVisibleItemCells();
            updateActionState();
        });
        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);
        HBox left = new HBox(8, refreshBtn);
        if (extraLeftNodes != null) {
            left.getChildren().addAll(extraLeftNodes);
        }
        if (countLabel != null) {
            left.getChildren().add(countLabel);
        }
        left.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, left, spacer, selectionActions);
        top.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(top);
        return top;
    }

    public HBox listFrame() {
        return listFrame;
    }

    public BorderPane bottomBar() {
        HBox leftActions = new HBox(8, primaryBtn, selectedBtn);
        leftActions.setAlignment(Pos.CENTER_LEFT);
        HBox rightActions = new HBox(8, cancelBtn, totalProgressBar, totalProgressLabel, statusLabel);
        rightActions.setAlignment(Pos.CENTER_RIGHT);
        BorderPane panel = new BorderPane();
        panel.setLeft(leftActions);
        panel.setRight(rightActions);
        DlcFxStyle.actionPanel(panel);
        return panel;
    }

    public void refreshRows() {
        listView.refresh();
        updateActionState();
    }

    private void syncVisibleItemCells() {
        for (ResourceCell cell : activeCells) {
            cell.syncItemState();
        }
    }

    private void updateActionState() {
        boolean hasSelection = !selectedIds().isEmpty();
        refreshBtn.setDisable(!available || busy);
        primaryBtn.setDisable(!available || busy);
        selectedBtn.setDisable(!available || busy || !hasSelection);
        cancelBtn.setDisable(!available || !cancelBtn.isVisible());
    }

    private boolean isSelectable(T item) {
        return item != null && adapter.selectable(item) && selectableFilter.test(item);
    }

    private final class ResourceCell extends ListCell<T> {
        private final CheckBox selectBox = new CheckBox();
        private final Label statusTag = new Label();
        private final Label titleLabel = new Label();
        private final Label progressText = new Label();
        private final ProgressBar rowProgress = new ProgressBar(0);
        private final Button actionButton = new Button();
        private final Region spacer = new Region();
        private final HBox progressBox = new HBox(6, progressText, rowProgress);
        private final HBox row = new HBox(8, selectBox, statusTag, titleLabel, spacer, progressBox, actionButton);

        ResourceCell() {
            DlcFxStyle.title(titleLabel);
            DlcFxStatusChip.apply(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.muted(progressText);
            DlcFxStyle.add(row, "dlc-dlc-row");
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            rowProgress.setPrefWidth(94);
            actionButton.setMinWidth(82);
            actionButton.setPrefWidth(82);
            selectBox.setOnAction(event -> {
                T item = getItem();
                if (item != null && isSelectable(item)) {
                    selectionModel.setSelected(item, selectBox.isSelected());
                    syncVisibleItemCells();
                    updateActionState();
                }
                event.consume();
            });
            actionButton.setOnAction(event -> {
                T item = getItem();
                if (item != null && itemActionFactory != null) {
                    Runnable action = itemActionFactory.apply(item);
                    if (action != null) {
                        action.run();
                    }
                }
                event.consume();
            });
            row.setOnMouseClicked(event -> event.consume());
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            activeCells.remove(this);
            getStyleClass().removeAll("dlc-row-item", "dlc-dlc-selected", "dlc-row-selected");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            activeCells.add(this);
            getStyleClass().add("dlc-row-item");
            statusTag.setText(adapter.statusText(item));
            DlcFxStatusChip.apply(statusTag, adapter.statusStyle(item));
            titleLabel.setText(adapter.title(item));
            syncItemState();
            String tooltip = adapter.tooltip(item);
            setTooltip(tooltip == null || tooltip.isBlank() ? null : DlcFxTooltips.plain(tooltip));
            setText(null);
            setGraphic(row);
        }

        private void syncItemState() {
            T item = getItem();
            if (item == null) {
                return;
            }
            getStyleClass().remove("dlc-row-selected");
            boolean selectable = isSelectable(item);
            boolean selected = selectable && selectionModel.isSelected(item);
            if (selected) {
                getStyleClass().add("dlc-row-selected");
            }
            selectBox.setVisible(selectable);
            selectBox.setManaged(true);
            selectBox.setDisable(!selectable || busy);
            selectBox.setSelected(selected);
            statusTag.setText(adapter.statusText(item));
            DlcFxStatusChip.apply(statusTag, adapter.statusStyle(item));
            double progress = adapter.progress(item);
            boolean showProgress = !Double.isNaN(progress);
            progressBox.setVisible(showProgress);
            progressBox.setManaged(showProgress);
            rowProgress.setProgress(showProgress ? Math.max(0d, Math.min(1d, progress)) : 0);
            progressText.setText(showProgress ? adapter.progressText(item) : "");
            boolean actionVisible = adapter.actionVisible(item);
            actionButton.setVisible(actionVisible);
            actionButton.setManaged(actionVisible);
            actionButton.setDisable(adapter.actionDisabled(item) || (busy && !adapter.actionAllowedWhileBusy(item)));
            actionButton.setText(adapter.actionText(item));
            actionButton.getStyleClass().removeAll("danger", "dlc-neutral-button");
            if (adapter.actionDanger(item)) {
                DlcFxStyle.danger(actionButton);
            } else {
                DlcFxStyle.add(actionButton, "dlc-neutral-button");
            }
        }
    }
}
