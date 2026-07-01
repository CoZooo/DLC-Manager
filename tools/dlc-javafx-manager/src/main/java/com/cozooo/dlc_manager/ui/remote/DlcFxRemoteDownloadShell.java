package com.cozooo.dlc_manager.ui.remote;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxSelectionModel;
import com.cozooo.dlc_manager.ui.common.DlcFxStatusChip;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTooltips;
import com.cozooo.dlc_manager.ui.management.DlcFxGroupCollapseModel;
import com.cozooo.dlc_manager.ui.remote.DlcFxRemoteDownloadListModel;

import com.cozooo.dlc_manager.core.DlcType;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxRemoteDownloadShell<T> {
    private static final double LIST_ROW_WIDTH_RESERVED = 34;
    private static final double LIST_ITEM_CONTENT_MIN_WIDTH = 620;
    private static final double LIST_GROUP_CONTENT_MIN_WIDTH = 260;
    private static final double LIST_PROGRESS_TEXT_WIDTH = 40;
    private static final double LIST_PROGRESS_WIDTH = 140;
    private static final double LIST_DOWNLOAD_WIDTH = 82;
    private static final double LIST_STATUS_WIDTH = 54;

    public interface Adapter<T> {
        String id(T item);
        String title(T item);
        DlcType type(T item);
        String folder(T item);
        boolean missing(T item);
        default boolean hidden(T item) { return false; }
        default String tooltip(T item) { return ""; }
        default boolean itemActionVisible(T item) { return missing(item); }
        default boolean itemActionDisabled(T item) { return false; }
        default boolean itemActionDanger(T item) { return false; }
        default String itemActionText(T item) { return DlcFxI18n.tr("dlc_manager.remote.download"); }
        default double progress(T item) { return Double.NaN; }
        default String progressText(T item) { return ""; }
    }

    private final Adapter<T> adapter;
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final ObservableList<DlcFxRemoteDownloadListModel.Row<T>> rows = FXCollections.observableArrayList();
    private final DlcFxSelectionModel<T> selectionModel;
    private final DlcFxGroupCollapseModel collapseModel = new DlcFxGroupCollapseModel();
    private final Set<ItemCell> activeItemCells = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ItemCell> activeGroupCells = Collections.newSetFromMap(new WeakHashMap<>());
    private final ListView<DlcFxRemoteDownloadListModel.Row<T>> listView = new ListView<>(rows);
    private final HBox listFrame;
    private final Button refreshBtn;
    private final Button downloadMissingBtn;
    private final Button downloadSelectedBtn;
    private final Button cancelBtn;
    private final Label countLabel;
    private final Label statusLabel;
    private final ProgressBar totalProgressBar;
    private final Label totalProgressLabel;
    private final ComboBox<DlcTypeOption> typeFilter = new ComboBox<>();
    private final Runnable refreshAction;
    private final Runnable downloadMissingAction;
    private final Runnable downloadSelectedAction;
    private final Runnable cancelAction;
    private final Function<T, Runnable> itemActionFactory;
    private boolean available = true;
    private boolean busy;
    private DlcType currentTypeFilter;

    public DlcFxRemoteDownloadShell(Adapter<T> adapter,
                                    Label statusLabel,
                                    Label countLabel,
                                    ProgressBar totalProgressBar,
                                    Label totalProgressLabel,
                                    Runnable refreshAction,
                                    Runnable downloadMissingAction,
                                    Runnable downloadSelectedAction,
                                    Runnable cancelAction,
                                    Function<T, Runnable> itemActionFactory) {
        this.adapter = adapter;
        this.statusLabel = statusLabel;
        this.countLabel = countLabel;
        this.totalProgressBar = totalProgressBar;
        this.totalProgressLabel = totalProgressLabel;
        this.refreshAction = refreshAction;
        this.downloadMissingAction = downloadMissingAction;
        this.downloadSelectedAction = downloadSelectedAction;
        this.cancelAction = cancelAction;
        this.itemActionFactory = itemActionFactory;
        this.selectionModel = new DlcFxSelectionModel<>(adapter::id);
        this.listFrame = DlcFxListViewSupport.wrap(listView);

        this.refreshBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.refresh_download_list"));
        this.downloadMissingBtn = new Button(DlcFxI18n.tr("dlc_manager.remote.download_missing"));
        this.downloadSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.common.download_selected"));
        this.cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.common.cancel"));

        DlcFxStyle.add(listView, "dlc-dlc-list");
        listView.setCellFactory(param -> new ItemCell());
        listView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(downloadMissingBtn);
        DlcFxStyle.add(downloadSelectedBtn, "dlc-neutral-button");
        DlcFxStyle.danger(cancelBtn);
        DlcFxStyle.muted(countLabel);
        DlcFxStyle.muted(totalProgressLabel);
        DlcFxStyle.routeStatus(statusLabel);
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);

        refreshBtn.setOnAction(event -> { if (refreshAction != null) refreshAction.run(); });
        downloadMissingBtn.setOnAction(event -> { if (downloadMissingAction != null) downloadMissingAction.run(); });
        downloadSelectedBtn.setOnAction(event -> { if (downloadSelectedAction != null) downloadSelectedAction.run(); });
        cancelBtn.setOnAction(event -> { if (cancelAction != null) cancelAction.run(); });
        configureTypeFilter();
        updateRows();
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
        if (totalProgressBar != null && busy && !totalProgressBar.isVisible()) {
            totalProgressBar.setVisible(true);
            totalProgressBar.setManaged(true);
            totalProgressBar.setProgress(-1);
        }
        if (totalProgressBar != null && !busy) {
            totalProgressBar.setVisible(false);
            totalProgressBar.setManaged(false);
            totalProgressBar.setProgress(0);
        }
        updateActionState();
    }

    public void setCancelVisible(boolean visible) {
        cancelBtn.setVisible(visible);
        cancelBtn.setManaged(visible);
        updateActionState();
    }

    public void setItems(Collection<T> nextItems) {
        items.setAll(nextItems == null ? List.of() : nextItems);
        selectionModel.pruneTo(items);
        refreshTypeFilterItems();
        updateRows();
    }

    public List<T> selectedItems() {
        return selectionModel.selectedItems(items).stream().filter(adapter::missing).toList();
    }

    public List<String> selectedIds() {
        return selectedItems().stream()
                .map(adapter::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    public void clearSelection() {
        selectionModel.clear();
        syncVisibleItemCells();
        updateActionState();
    }

    public void refreshRows() {
        syncVisibleItemCells();
        updateActionState();
    }

    public HBox topBar() {
        Label typeLabel = new Label(DlcFxI18n.tr("dlc_manager.local.type"));
        DlcFxStyle.muted(typeLabel);
        Button selectAllBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_all"));
        Button selectNoneBtn = new Button(DlcFxI18n.tr("dlc_manager.local.select_none"));
        Button invertBtn = new Button(DlcFxI18n.tr("dlc_manager.local.invert"));
        selectAllBtn.setOnAction(event -> {
            selectionModel.selectAll(visibleItems(), adapter::missing);
            syncVisibleItemCells();
            updateActionState();
        });
        selectNoneBtn.setOnAction(event -> clearSelection());
        invertBtn.setOnAction(event -> {
            selectionModel.invertVisible(visibleItems(), adapter::missing);
            syncVisibleItemCells();
            updateActionState();
        });
        HBox selectionActions = new HBox(8, selectAllBtn, selectNoneBtn, invertBtn);
        selectionActions.setAlignment(Pos.CENTER_LEFT);
        HBox left = new HBox(8, refreshBtn, typeLabel, typeFilter, countLabel);
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
        HBox leftActions = new HBox(8, downloadMissingBtn, downloadSelectedBtn);
        leftActions.setAlignment(Pos.CENTER_LEFT);
        HBox rightActions = new HBox(8, cancelBtn, totalProgressBar, totalProgressLabel, statusLabel);
        rightActions.setAlignment(Pos.CENTER_RIGHT);
        BorderPane panel = new BorderPane();
        panel.setLeft(leftActions);
        panel.setRight(rightActions);
        DlcFxStyle.actionPanel(panel);
        return panel;
    }

    public int missingCount() {
        return (int) items.stream().filter(adapter::missing).count();
    }

    private void configureTypeFilter() {
        typeFilter.setPromptText(DlcFxI18n.tr("dlc_manager.local.all_types"));
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcTypeOption option) { return typeText(option); }
            @Override public DlcTypeOption fromString(String s) { return DlcTypeOption.all(); }
        });
        typeFilter.setButtonCell(new DlcTypeButtonCell(typeFilter));
        typeFilter.setCellFactory(param -> new DlcTypePopupCell());
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> {
            currentTypeFilter = typeOf(newValue);
            selectionModel.clear();
            updateRows();
        });
        refreshTypeFilterItems();
    }

    private void refreshTypeFilterItems() {
        DlcType selected = currentTypeFilter;
        typeFilter.getItems().clear();
        typeFilter.getItems().add(DlcTypeOption.all());
        for (DlcType type : visibleTypes()) {
            typeFilter.getItems().add(DlcTypeOption.of(type));
        }
        boolean keepSelected = selected != null && visibleTypes().contains(selected);
        typeFilter.setValue(keepSelected ? DlcTypeOption.of(selected) : DlcTypeOption.all());
    }

    private Set<DlcType> visibleTypes() {
        Set<DlcType> types = new LinkedHashSet<>();
        for (T item : items) {
            DlcType type = adapter.type(item);
            if (type != null) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            types.addAll(List.of(DlcType.values()));
        }
        return types;
    }

    private void updateRows() {
        rows.setAll(DlcFxRemoteDownloadListModel.rows(
                items,
                currentTypeFilter,
                adapter::folder,
                adapter::type,
                adapter::hidden,
                collapseModel::isCollapsed,
                this::folderLabel
        ));
        listView.refresh();
        updateActionState();
    }

    private boolean setFolderCollapsed(DlcFxRemoteDownloadListModel.Row<T> row, boolean collapsed) {
        if (row == null || row.groupKey == null || row.groupKey.isBlank()) {
            return false;
        }
        if (collapseModel.isAnimating(row.groupKey)) {
            return false;
        }
        int headerIndex = rows.indexOf(row);
        if (headerIndex < 0 || row.collapsed == collapsed) {
            return false;
        }
        if (collapsed) {
            animateFolderCollapse(row, headerIndex);
        } else {
            animateFolderExpand(row, headerIndex);
        }
        listView.getSelectionModel().clearSelection();
        return true;
    }

    private void animateFolderCollapse(DlcFxRemoteDownloadListModel.Row<T> row, int headerIndex) {
        if (!collapseModel.beginCollapse(row.groupKey)) {
            return;
        }
        row.collapsed = true;
        List<DlcFxRemoteDownloadListModel.Row<T>> targets = folderItemRowsAfter(headerIndex);
        if (targets.isEmpty()) {
            finishFolderAnimation(row.groupKey);
            return;
        }
        for (DlcFxRemoteDownloadListModel.Row<T> target : targets) {
            target.expanding = false;
            target.collapsing = true;
        }
        startVisibleFolderAnimations(row.groupKey);
        PauseTransition removeAfterAnimation = new PauseTransition(Duration.millis(260));
        removeAfterAnimation.setOnFinished(event -> {
            rows.removeAll(targets);
            for (DlcFxRemoteDownloadListModel.Row<T> target : targets) {
                if (target.collapsing && !target.expanding) {
                    target.collapsing = false;
                }
            }
            finishFolderAnimation(row.groupKey);
        });
        removeAfterAnimation.play();
    }

    private void animateFolderExpand(DlcFxRemoteDownloadListModel.Row<T> row, int headerIndex) {
        if (!collapseModel.beginExpand(row.groupKey)) {
            return;
        }
        row.collapsed = false;
        List<DlcFxRemoteDownloadListModel.Row<T>> folderRows = itemRowsForFolder(row.folderRaw);
        if (folderRows.isEmpty()) {
            finishFolderAnimation(row.groupKey);
            return;
        }
        for (DlcFxRemoteDownloadListModel.Row<T> item : folderRows) {
            item.expanding = true;
            item.collapsing = false;
        }
        rows.addAll(headerIndex + 1, folderRows);
        Platform.runLater(() -> startVisibleFolderAnimations(row.groupKey));
        PauseTransition finishExpansion = new PauseTransition(Duration.millis(260));
        finishExpansion.setOnFinished(event -> {
            for (DlcFxRemoteDownloadListModel.Row<T> item : folderRows) {
                if (item.expanding && !item.collapsing) {
                    item.expanding = false;
                }
            }
            finishFolderAnimation(row.groupKey);
        });
        finishExpansion.play();
    }

    private void finishFolderAnimation(String folderKey) {
        collapseModel.finishAnimation(folderKey);
        syncVisibleGroupHeaders(folderKey);
        syncVisibleItemCells();
    }

    private void startVisibleFolderAnimations(String folderKey) {
        for (ItemCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.startAnimationForFolder(folderKey);
            }
        }
    }

    private void syncVisibleGroupHeaders(String folderKey) {
        for (ItemCell cell : new ArrayList<>(activeGroupCells)) {
            if (cell != null) {
                cell.syncGroupState(folderKey);
            }
        }
    }

    private void syncVisibleItemCells() {
        for (ItemCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.syncItemState();
            }
        }
    }

    private List<DlcFxRemoteDownloadListModel.Row<T>> folderItemRowsAfter(int headerIndex) {
        List<DlcFxRemoteDownloadListModel.Row<T>> out = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            DlcFxRemoteDownloadListModel.Row<T> row = rows.get(i);
            if (row.type != DlcFxRemoteDownloadListModel.RowType.ITEM) {
                break;
            }
            out.add(row);
        }
        return out;
    }

    private List<DlcFxRemoteDownloadListModel.Row<T>> itemRowsForFolder(String rawFolder) {
        String normalized = DlcFxRemoteDownloadListModel.normalizeFolder(rawFolder);
        String groupKey = DlcFxRemoteDownloadListModel.normalizeFolder(normalized);
        return DlcFxRemoteDownloadListModel.rows(
                        items,
                        currentTypeFilter,
                        adapter::folder,
                        adapter::type,
                        adapter::hidden,
                        key -> false,
                        this::folderLabel
                ).stream()
                .filter(row -> row.type == DlcFxRemoteDownloadListModel.RowType.ITEM && groupKey.equals(row.groupKey))
                .toList();
    }

    private String folderLabel(String folder) {
        return DlcFxRemoteDownloadListModel.ROOT_GROUP.equals(folder) ? DlcFxI18n.tr("dlc_manager.group.root") : folder;
    }

    private List<T> visibleItems() {
        List<T> out = new ArrayList<>();
        for (DlcFxRemoteDownloadListModel.Row<T> row : rows) {
            if (row.type == DlcFxRemoteDownloadListModel.RowType.ITEM && row.item != null) {
                out.add(row.item);
            }
        }
        return out;
    }

    private void updateActionState() {
        boolean hasSelection = !selectedIds().isEmpty();
        refreshBtn.setDisable(!available || busy);
        downloadMissingBtn.setDisable(!available || busy);
        downloadSelectedBtn.setDisable(!available || busy || !hasSelection);
        cancelBtn.setDisable(!available || !cancelBtn.isVisible());
    }

    private final class ItemCell extends ListCell<DlcFxRemoteDownloadListModel.Row<T>> {
        private final CheckBox selectBox = new CheckBox();
        private final Label statusTag = new Label();
        private final Label titleLabel = new Label();
        private final Label groupLabel = new Label();
        private final Label progressText = new Label();
        private final ProgressBar rowProgress = new ProgressBar(0);
        private final Button actionButton = new Button(DlcFxI18n.tr("dlc_manager.remote.download"));
        private final Button collapseButton = new Button();
        private final SVGPath collapseIcon = new SVGPath();
        private final StackPane collapseIconBox = new StackPane(collapseIcon);
        private final Region trailingSpacer = new Region();
        private final HBox progressBox = new HBox(6, progressText, rowProgress);
        private final GridPane itemGrid = new GridPane();
        private final HBox itemRow = new HBox(0, itemGrid);
        private final BorderPane groupRow = new BorderPane();
        private final StackPane itemPane = new StackPane(itemRow);
        private final Rectangle clip = new Rectangle();
        private Timeline rowTimeline;
        private RotateTransition collapseTransition;
        private DlcFxRemoteDownloadListModel.Row<T> animatedRow;
        private Boolean animatedExpansion;
        private Tooltip rowTooltip;

        ItemCell() {
            DlcFxStyle.title(titleLabel);
            DlcFxStatusChip.apply(statusTag, DlcFxStyle.CHIP_NEUTRAL);
            DlcFxStyle.add(groupLabel, "dlc-dlc-group-header");
            DlcFxStyle.add(itemRow, "dlc-dlc-row");
            DlcFxStyle.add(groupRow, "dlc-dlc-path-row");
            DlcFxStyle.add(actionButton, "dlc-neutral-button");
            DlcFxStyle.add(collapseButton, "dlc-folder-collapse-button");
            DlcFxStyle.add(collapseIconBox, "dlc-folder-collapse-icon-box");
            DlcFxStyle.add(collapseIcon, "dlc-folder-collapse-icon");
            DlcFxStyle.muted(progressText);
            collapseIcon.setContent("M 3 10 L 8 5 L 13 10");
            collapseIcon.setFill(Color.TRANSPARENT);
            collapseIcon.setStroke(Color.web("#475467"));
            collapseIcon.setStrokeWidth(1.8);
            collapseIcon.setStrokeLineCap(StrokeLineCap.ROUND);
            collapseIcon.setStrokeLineJoin(StrokeLineJoin.ROUND);
            actionButton.setMinWidth(LIST_DOWNLOAD_WIDTH);
            actionButton.setPrefWidth(LIST_DOWNLOAD_WIDTH);
            actionButton.setMaxWidth(LIST_DOWNLOAD_WIDTH);
            progressText.setMinWidth(LIST_PROGRESS_TEXT_WIDTH);
            progressText.setPrefWidth(LIST_PROGRESS_TEXT_WIDTH);
            progressText.setMaxWidth(LIST_PROGRESS_TEXT_WIDTH);
            progressText.setAlignment(Pos.CENTER_RIGHT);
            trailingSpacer.setMinWidth(0);
            trailingSpacer.setPrefWidth(0);
            trailingSpacer.setMaxWidth(Double.MAX_VALUE);
            rowProgress.setPrefWidth(LIST_PROGRESS_WIDTH);
            rowProgress.setMinWidth(LIST_PROGRESS_WIDTH - LIST_PROGRESS_TEXT_WIDTH - 6);
            rowProgress.setMaxWidth(LIST_PROGRESS_WIDTH - LIST_PROGRESS_TEXT_WIDTH - 6);
            rowProgress.setVisible(false);
            rowProgress.setManaged(true);
            rowProgress.setProgress(0);
            progressBox.setAlignment(Pos.CENTER_RIGHT);
            itemGrid.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setHgap(8);
            itemGrid.setMaxWidth(Double.MAX_VALUE);
            itemGrid.add(selectBox, 0, 0);
            itemGrid.add(statusTag, 1, 0);
            itemGrid.add(titleLabel, 2, 0);
            itemGrid.add(trailingSpacer, 3, 0);
            itemGrid.add(progressBox, 4, 0);
            itemGrid.add(actionButton, 5, 0);
            GridPane.setHgrow(titleLabel, Priority.ALWAYS);
            GridPane.setHgrow(trailingSpacer, Priority.ALWAYS);
            GridPane.setHgrow(progressBox, Priority.NEVER);
            HBox.setHgrow(itemGrid, Priority.ALWAYS);
            itemGrid.getColumnConstraints().addAll(
                    column(28, 28, Priority.NEVER),
                    column(LIST_STATUS_WIDTH, LIST_STATUS_WIDTH, Priority.NEVER),
                    column(340, 540, Priority.ALWAYS),
                    column(0, 0, Priority.ALWAYS),
                    column(LIST_PROGRESS_WIDTH, LIST_PROGRESS_WIDTH, Priority.NEVER),
                    column(LIST_DOWNLOAD_WIDTH, LIST_DOWNLOAD_WIDTH, Priority.NEVER)
            );
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemPane.setAlignment(Pos.CENTER_LEFT);
            itemPane.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemPane.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_ITEM_CONTENT_MIN_WIDTH));
            clip.widthProperty().bind(itemPane.widthProperty());
            clip.heightProperty().bind(itemPane.heightProperty());
            itemPane.setClip(clip);
            groupRow.setCenter(groupLabel);
            groupRow.setRight(collapseButton);
            BorderPane.setAlignment(groupLabel, Pos.CENTER_LEFT);
            BorderPane.setAlignment(collapseButton, Pos.CENTER_RIGHT);
            groupLabel.setMaxWidth(Double.MAX_VALUE);
            groupRow.setMinWidth(LIST_GROUP_CONTENT_MIN_WIDTH);
            groupRow.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_GROUP_CONTENT_MIN_WIDTH));
            collapseButton.setMinWidth(28);
            collapseButton.setPrefWidth(28);
            collapseButton.setMaxWidth(28);
            collapseButton.setText(null);
            collapseButton.setGraphic(collapseIconBox);
            collapseButton.setOnAction(event -> {
                DlcFxRemoteDownloadListModel.Row<T> row = getItem();
                if (row != null && row.type == DlcFxRemoteDownloadListModel.RowType.GROUP && !collapseModel.isAnimating(row.groupKey)) {
                    boolean targetCollapsed = !row.collapsed;
                    if (setFolderCollapsed(row, targetCollapsed)) {
                        collapseButton.setDisable(true);
                        animateCollapseButton(targetCollapsed);
                    }
                }
                event.consume();
            });
            selectBox.setOnMouseClicked(event -> {
                DlcFxRemoteDownloadListModel.Row<T> row = getItem();
                if (row == null || row.item == null || !adapter.missing(row.item)) {
                    event.consume();
                    return;
                }
                if (collapseModel.isAnimating(row.groupKey)) {
                    selectBox.setSelected(selectionModel.isSelected(row.item));
                    event.consume();
                    return;
                }
                selectionModel.setSelected(row.item, selectBox.isSelected());
                syncVisibleItemCells();
                updateActionState();
                event.consume();
            });
            actionButton.setOnAction(event -> {
                DlcFxRemoteDownloadListModel.Row<T> row = getItem();
                if (row != null && row.item != null && itemActionFactory != null) {
                    if (collapseModel.isAnimating(row.groupKey)) {
                        event.consume();
                        return;
                    }
                    Runnable action = itemActionFactory.apply(row.item);
                    if (action != null) {
                        action.run();
                    }
                }
                event.consume();
            });
            itemRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), selectBox) && !isInside(event.getTarget(), actionButton)) {
                    event.consume();
                }
            });
            groupRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), collapseButton)) {
                    event.consume();
                }
                if (event.getButton() == MouseButton.PRIMARY) {
                    listView.getSelectionModel().clearSelection();
                }
            });
            DlcFxTooltips.followMouse(this, () -> rowTooltip);
        }

        @Override
        protected void updateItem(DlcFxRemoteDownloadListModel.Row<T> row, boolean empty) {
            super.updateItem(row, empty);
            activeItemCells.remove(this);
            activeGroupCells.remove(this);
            getStyleClass().removeAll("dlc-row-group", "dlc-row-item", "dlc-row-selected");
            rowTooltip = null;
            if (empty || row == null) {
                resetRowAnimation();
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            if (row.type == DlcFxRemoteDownloadListModel.RowType.GROUP) {
                resetRowAnimation();
                activeGroupCells.add(this);
                getStyleClass().add("dlc-row-group");
                groupLabel.setText(row.groupLabel);
                syncGroupState(row.groupKey);
                setText(null);
                setGraphic(groupRow);
                return;
            }
            resetRowAnimation();
            activeItemCells.add(this);
            T item = row.item;
            boolean missing = adapter.missing(item);
            getStyleClass().add("dlc-row-item");
            if (selectionModel.isSelected(item)) {
                getStyleClass().add("dlc-row-selected");
            }
            selectBox.setVisible(true);
            selectBox.setManaged(true);
            selectBox.setDisable(!missing || busy);
            selectBox.setSelected(missing && selectionModel.isSelected(item));
            statusTag.setText(missing ? DlcFxI18n.tr("dlc_manager.status.missing") : DlcFxI18n.tr("dlc_manager.status.present"));
            DlcFxStatusChip.apply(statusTag, missing ? DlcFxStyle.CHIP_ERROR : DlcFxStyle.CHIP_OK);
            DlcFxRichTextRenderer.setFormatted(titleLabel, adapter.title(item));
            syncItemState();
            String tooltip = adapter.tooltip(item);
            rowTooltip = tooltip == null || tooltip.isBlank() ? null : DlcFxRichTextRenderer.formattedTooltip(tooltip);
            setText(null);
            setGraphic(itemPane);
            startRowAnimationIfNeeded(row);
        }

        private void syncItemState() {
            DlcFxRemoteDownloadListModel.Row<T> row = getItem();
            if (row == null || row.type != DlcFxRemoteDownloadListModel.RowType.ITEM || row.item == null) {
                return;
            }
            T item = row.item;
            boolean missing = adapter.missing(item);
            boolean selected = missing && selectionModel.isSelected(item);
            getStyleClass().remove("dlc-row-selected");
            if (selected) {
                getStyleClass().add("dlc-row-selected");
            }
            selectBox.setVisible(true);
            selectBox.setManaged(true);
            selectBox.setDisable(collapseModel.isAnimating(row.groupKey) || !missing || busy);
            selectBox.setSelected(selected);
            statusTag.setText(missing ? DlcFxI18n.tr("dlc_manager.status.missing") : DlcFxI18n.tr("dlc_manager.status.present"));
            DlcFxStatusChip.apply(statusTag, missing ? DlcFxStyle.CHIP_ERROR : DlcFxStyle.CHIP_OK);
            double progress = adapter.progress(item);
            boolean showProgress = !Double.isNaN(progress);
            progressText.setVisible(showProgress);
            progressText.setManaged(showProgress);
            rowProgress.setVisible(showProgress);
            rowProgress.setManaged(showProgress);
            rowProgress.setProgress(showProgress ? Math.max(0d, Math.min(1d, progress)) : 0);
            progressText.setText(showProgress ? adapter.progressText(item) : "");
            boolean actionVisible = adapter.itemActionVisible(item);
            actionButton.setVisible(actionVisible);
            actionButton.setManaged(actionVisible);
            actionButton.setDisable(adapter.itemActionDisabled(item) || collapseModel.isAnimating(row.groupKey));
            actionButton.setText(adapter.itemActionText(item));
            actionButton.getStyleClass().removeAll("danger", "dlc-neutral-button");
            if (adapter.itemActionDanger(item)) {
                DlcFxStyle.danger(actionButton);
            } else {
                DlcFxStyle.add(actionButton, "dlc-neutral-button");
            }
            itemRow.setMouseTransparent(collapseModel.isAnimating(row.groupKey));
        }

        private void syncGroupState(String folderKey) {
            DlcFxRemoteDownloadListModel.Row<T> row = getItem();
            if (row == null || row.type != DlcFxRemoteDownloadListModel.RowType.GROUP) {
                return;
            }
            if (folderKey == null || folderKey.equals(row.groupKey)) {
                collapseIcon.setRotate(row.collapsed ? 180 : 0);
                collapseButton.setDisable(collapseModel.isAnimating(row.groupKey));
            }
        }

        private void startAnimationForFolder(String folderKey) {
            DlcFxRemoteDownloadListModel.Row<T> row = getItem();
            if (row == null || row.type != DlcFxRemoteDownloadListModel.RowType.ITEM || !folderKey.equals(row.groupKey)) {
                return;
            }
            syncItemState();
            startRowAnimationIfNeeded(row);
        }

        private void animateCollapseButton(boolean collapsing) {
            if (collapseTransition != null) {
                collapseTransition.stop();
            }
            collapseIcon.setRotate(collapsing ? 0 : 180);
            collapseTransition = new RotateTransition(Duration.millis(180), collapseIcon);
            collapseTransition.setInterpolator(Interpolator.EASE_BOTH);
            collapseTransition.setToAngle(collapsing ? 180 : 0);
            collapseTransition.play();
        }

        private void startRowAnimationIfNeeded(DlcFxRemoteDownloadListModel.Row<T> row) {
            if (row == null) {
                resetRowAnimation();
                return;
            }
            if (row.collapsing && !isCurrentAnimation(row, false)) {
                animateRow(row, false, Math.max(getCurrentAnimatedHeight(), 42), 0, 1, 0, null);
                return;
            }
            if (row.expanding && !isCurrentAnimation(row, true)) {
                animateRow(row, true, 0, 42, 0, 1, () -> {
                    if (getItem() == row && row.expanding && !row.collapsing) {
                        row.expanding = false;
                        resetRowAnimation();
                    }
                });
                return;
            }
            if (!row.expanding && !row.collapsing) {
                resetRowAnimation();
            }
        }

        private boolean isCurrentAnimation(DlcFxRemoteDownloadListModel.Row<T> row, boolean expandingAnimation) {
            return rowTimeline != null && animatedRow == row && animatedExpansion != null && animatedExpansion == expandingAnimation;
        }

        private void animateRow(DlcFxRemoteDownloadListModel.Row<T> row, boolean expandingAnimation, double fromHeight, double toHeight,
                                double fromOpacity, double toOpacity, Runnable afterFinished) {
            if (rowTimeline != null) {
                rowTimeline.stop();
            }
            animatedRow = row;
            animatedExpansion = expandingAnimation;
            itemPane.setClip(clip);
            itemPane.setMinHeight(fromHeight);
            itemPane.setPrefHeight(fromHeight);
            itemPane.setMaxHeight(fromHeight);
            itemPane.setOpacity(fromOpacity);
            rowTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(itemPane.minHeightProperty(), fromHeight),
                            new KeyValue(itemPane.prefHeightProperty(), fromHeight),
                            new KeyValue(itemPane.maxHeightProperty(), fromHeight),
                            new KeyValue(itemPane.opacityProperty(), fromOpacity)
                    ),
                    new KeyFrame(Duration.millis(240),
                            new KeyValue(itemPane.minHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                            new KeyValue(itemPane.prefHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                            new KeyValue(itemPane.maxHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                            new KeyValue(itemPane.opacityProperty(), toOpacity, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0))
                    )
            );
            rowTimeline.setOnFinished(event -> {
                if (getItem() != row || row.expanding != expandingAnimation || row.collapsing == expandingAnimation) {
                    return;
                }
                rowTimeline = null;
                animatedRow = null;
                animatedExpansion = null;
                if (afterFinished != null) {
                    afterFinished.run();
                }
            });
            rowTimeline.play();
        }

        private double getCurrentAnimatedHeight() {
            double height = itemPane.getHeight();
            if (Double.isNaN(height) || height <= 0) {
                height = itemPane.getPrefHeight();
            }
            if (Double.isNaN(height) || height <= 0 || height == Region.USE_COMPUTED_SIZE || height == Region.USE_PREF_SIZE) {
                return 42;
            }
            return height;
        }

        private void resetRowAnimation() {
            if (rowTimeline != null) {
                rowTimeline.stop();
                rowTimeline = null;
            }
            animatedRow = null;
            animatedExpansion = null;
            setMinHeight(Region.USE_PREF_SIZE);
            setPrefHeight(Region.USE_COMPUTED_SIZE);
            setMaxHeight(Region.USE_COMPUTED_SIZE);
            setOpacity(1);
            itemPane.setMinHeight(Region.USE_PREF_SIZE);
            itemPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
            itemPane.setOpacity(1);
            itemPane.setClip(null);
        }
    }

    private static ColumnConstraints column(double min, double pref, Priority grow) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(min);
        constraints.setPrefWidth(pref);
        constraints.setHgrow(grow);
        return constraints;
    }

    private static boolean isInside(Object target, Node parent) {
        if (!(target instanceof Node node) || parent == null) {
            return false;
        }
        while (node != null) {
            if (node == parent) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private static DlcType typeOf(DlcTypeOption option) {
        return option == null || option.all ? null : option.type;
    }

    private static String typeText(DlcTypeOption option) {
        DlcType type = typeOf(option);
        return type == null ? DlcFxI18n.tr("dlc_manager.local.all_types") : DlcFxI18n.tr(type.getDisplayKey());
    }

    private static final class DlcTypeButtonCell extends ListCell<DlcTypeOption> {
        private final ComboBox<DlcTypeOption> owner;

        private DlcTypeButtonCell(ComboBox<DlcTypeOption> owner) {
            this.owner = owner;
        }

        @Override
        protected void updateItem(DlcTypeOption item, boolean empty) {
            super.updateItem(item, empty);
            setText(typeText(owner == null ? item : owner.getValue()));
        }
    }

    private static final class DlcTypePopupCell extends ListCell<DlcTypeOption> {
        @Override
        protected void updateItem(DlcTypeOption item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "" : typeText(item));
        }
    }

    private static final class DlcTypeOption {
        private static final DlcTypeOption ALL = new DlcTypeOption(true, null);
        private final boolean all;
        private final DlcType type;

        private DlcTypeOption(boolean all, DlcType type) {
            this.all = all;
            this.type = type;
        }

        private static DlcTypeOption all() {
            return ALL;
        }

        private static DlcTypeOption of(DlcType type) {
            return type == null ? all() : new DlcTypeOption(false, type);
        }
    }
}
