package com.cozooo.dlc_manager.ui.common;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxSelectionModel;

import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

import java.util.Set;
import java.util.function.Function;

public final class DlcFxSelectableList<T> {
    private final ListView<T> listView;
    private final HBox frame;
    private final DlcFxSelectionModel<T> selectionModel;
    private Function<T, String> idResolver;

    public DlcFxSelectableList(ObservableList<T> items, Function<T, String> idResolver) {
        this.listView = new ListView<>(items);
        this.frame = DlcFxListViewSupport.wrap(listView);
        this.idResolver = idResolver == null ? item -> String.valueOf(item) : idResolver;
        this.selectionModel = new DlcFxSelectionModel<>(this.idResolver);
    }

    public ListView<T> listView() {
        return listView;
    }

    public HBox frame() {
        return frame;
    }

    public Set<String> selectedIds() {
        return selectionModel.selectedIds();
    }

    public boolean isSelected(T item) {
        return selectionModel.isSelected(item);
    }

    public void setSelected(T item, boolean selected) {
        selectionModel.setSelected(item, selected);
        listView.refresh();
    }

    public void toggle(T item) {
        setSelected(item, !isSelected(item));
    }

    public void selectAll(Iterable<T> items) {
        selectionModel.clear();
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    selectionModel.setSelected(item, true);
                }
            }
        }
        listView.refresh();
    }

    public void clearSelection() {
        selectionModel.clear();
        listView.refresh();
    }

    public void invert(Iterable<T> items) {
        java.util.List<T> visibleItems = new java.util.ArrayList<>();
        if (items != null) {
            for (T item : items) {
                if (item == null) {
                    continue;
                }
                visibleItems.add(item);
            }
        }
        selectionModel.invert(visibleItems, item -> true);
        listView.refresh();
    }

    public void applySelectedStyle(ListCell<T> cell, T item, String rowStyleClass) {
        if (cell == null) {
            return;
        }
        if (rowStyleClass != null && !cell.getStyleClass().contains(rowStyleClass)) {
            cell.getStyleClass().add(rowStyleClass);
        }
        cell.getStyleClass().remove("dlc-dlc-selected");
        if (isSelected(item)) {
            cell.getStyleClass().add("dlc-dlc-selected");
        }
    }

}
