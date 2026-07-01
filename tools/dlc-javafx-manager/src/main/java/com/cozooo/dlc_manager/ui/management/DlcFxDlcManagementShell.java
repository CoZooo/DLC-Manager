package com.cozooo.dlc_manager.ui.management;
import com.cozooo.dlc_manager.ui.common.DlcFxActionPanel;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxPageActions;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTooltips;
import com.cozooo.dlc_manager.ui.management.DlcFxDlcManagementListModel;
import com.cozooo.dlc_manager.ui.management.DlcFxPendingToggleModel;

import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.core.DlcType;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.scene.control.TextField;
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
import java.util.function.Consumer;

public final class DlcFxDlcManagementShell<T> {
    private static final double LIST_ROW_WIDTH_RESERVED = 34;
    private static final double LIST_ITEM_CONTENT_MIN_WIDTH = 652;
    private static final double LIST_PATH_CONTENT_MIN_WIDTH = 360;

    public interface Adapter<T> {
        String id(T item);
        String displayName(T item);
        String typeText(T item);
        boolean baselineEnabled(T item);
        boolean effectiveEnabled(T item);
        void setEffectiveEnabled(T item, boolean enabled);
        default DlcType type(T item) { return null; }
        default String folder(T item) { return DlcFxDlcManagementListModel.ROOT_GROUP; }
        default String folderLabel(String raw) {
            return DlcFxDlcManagementListModel.ROOT_GROUP.equals(raw)
                    ? DlcFxI18n.tr("dlc_manager.group.root")
                    : raw;
        }
        default boolean visibleType(DlcType type) { return type != DlcType.DLC_PACK; }
        default boolean canEnable(T item) { return true; }
        default boolean canDisable(T item) { return true; }
        default boolean missingDependencies(T item) { return false; }
        default boolean blocked(T item) { return false; }
        default String blockedText(T item) { return ""; }
        default String tooltip(T item) { return ""; }
        default void openRedirect(T item) {}
    }

    private final Adapter<T> adapter;
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final ObservableList<DlcRedirectEntry> redirectEntries = FXCollections.observableArrayList();
    private final ObservableList<DlcFxDlcManagementListModel.Row<T>> rows = FXCollections.observableArrayList();
    private final DlcFxPendingToggleModel<T> model;
    private final Set<String> collapsedFolders = new LinkedHashSet<>();
    private final Set<String> animatingFolders = new LinkedHashSet<>();
    private final Set<ItemCell> activeItemCells = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ItemCell> activeAnimatedCells = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ItemCell> activePathCells = Collections.newSetFromMap(new WeakHashMap<>());
    private final ListView<DlcFxDlcManagementListModel.Row<T>> listView = new ListView<>(rows);
    private final HBox listFrame;
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final TextField searchField = new TextField();
    private final ComboBox<DlcTypeOption> typeFilter = new ComboBox<>();
    private final Button applyBtn;
    private final Button cancelBtn;
    private final Button enableSelectedBtn;
    private final Button disableSelectedBtn;
    private final Label pendingWarningLabel;
    private final Consumer<Collection<T>> enableHandler;
    private final Consumer<Collection<T>> disableHandler;
    private Consumer<DlcRedirectEntry> redirectOpenHandler = entry -> {};
    private boolean available = true;
    private DlcType currentTypeFilter;
    private String currentSearchText = "";

    public DlcFxDlcManagementShell(Adapter<T> adapter,
                                   Label statusLabel,
                                   ProgressBar progressBar,
                                   Runnable applyAction,
                                   Runnable cancelAction,
                                   Consumer<Collection<T>> enableHandler,
                                   Consumer<Collection<T>> disableHandler) {
        this.adapter = adapter;
        this.statusLabel = statusLabel;
        this.progressBar = progressBar;
        this.enableHandler = enableHandler == null ? targetItems -> setEffectiveEnabled(targetItems, true) : enableHandler;
        this.disableHandler = disableHandler == null ? targetItems -> setEffectiveEnabled(targetItems, false) : disableHandler;
        this.model = new DlcFxPendingToggleModel<>(
                items,
                adapter::id,
                adapter::baselineEnabled,
                adapter::effectiveEnabled,
                adapter::setEffectiveEnabled
        );
        this.listFrame = DlcFxListViewSupport.wrap(listView);

        this.applyBtn = new Button(DlcFxI18n.tr("dlc_manager.local.apply_changes"));
        this.cancelBtn = new Button(DlcFxI18n.tr("dlc_manager.local.cancel_changes"));
        this.enableSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.local.enable_selected"));
        this.disableSelectedBtn = new Button(DlcFxI18n.tr("dlc_manager.local.disable_selected"));
        this.pendingWarningLabel = new Label(DlcFxI18n.tr("dlc_manager.pending.unapplied_changes"));

        configureList();
        configureFilter();
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.primary(enableSelectedBtn);
        DlcFxStyle.add(pendingWarningLabel, "dlc-pending-warning");
        pendingWarningLabel.setVisible(false);
        pendingWarningLabel.setManaged(false);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        applyBtn.setOnAction(event -> {
            if (applyAction != null) {
                applyAction.run();
            }
        });
        cancelBtn.setOnAction(event -> {
            if (cancelAction != null) {
                cancelAction.run();
            } else {
                resetChanges();
            }
        });
        enableSelectedBtn.setOnAction(event -> enableSelected());
        disableSelectedBtn.setOnAction(event -> disableSelected());
        updateRows();
        updateActionState();
    }

    public ObservableList<T> items() {
        return items;
    }

    public DlcFxPendingToggleModel<T> model() {
        return model;
    }

    public ListView<DlcFxDlcManagementListModel.Row<T>> listView() {
        return listView;
    }

    public HBox listFrame() {
        return listFrame;
    }

    public Node filterBar() {
        Label searchLabel = new Label(DlcFxI18n.tr("dlc_manager.local.filter"));
        Label typeLabel = new Label(DlcFxI18n.tr("dlc_manager.local.type"));
        DlcFxStyle.muted(searchLabel);
        DlcFxStyle.muted(typeLabel);
        HBox filterBox = new HBox(8, searchLabel, searchField, typeLabel, typeFilter);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.filterBar(filterBox);
        return filterBox;
    }

    public Node selectionActions() {
        return DlcFxPageActions.selectionActions(
                () -> {
                    model.selectAll(visibleItems());
                    syncVisibleItemCells();
                    updateActionState();
                },
                () -> {
                    model.clearSelection();
                    syncVisibleItemCells();
                    updateActionState();
                },
                () -> {
                    model.invertSelection(visibleItems());
                    syncVisibleItemCells();
                    updateActionState();
                }
        );
    }

    public VBox bottomPanel() {
        HBox actions = new HBox(8, applyBtn, cancelBtn, enableSelectedBtn, disableSelectedBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox statusRow = new HBox(18, progressBar, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        VBox pendingRow = new VBox(4, actions, pendingWarningLabel);
        return DlcFxActionPanel.panel(new HBox(18, pendingRow, statusRow));
    }

    public Button applyButton() {
        return applyBtn;
    }

    public Button cancelButton() {
        return cancelBtn;
    }

    public void setAvailable(boolean available) {
        this.available = available;
        updateActionState();
    }

    public void setItems(Collection<T> nextItems) {
        items.setAll(nextItems == null ? List.of() : nextItems);
        model.resetItems(items);
        refreshTypeFilterItems();
        updateRows();
    }

    public void setRedirectEntries(Collection<DlcRedirectEntry> nextEntries) {
        redirectEntries.setAll(nextEntries == null ? List.of() : nextEntries);
        updateRows();
    }

    public void setRedirectOpenHandler(Consumer<DlcRedirectEntry> redirectOpenHandler) {
        this.redirectOpenHandler = redirectOpenHandler == null ? entry -> {} : redirectOpenHandler;
    }

    public List<T> changedItems() {
        return model.changedItems();
    }

    public boolean hasPendingChanges() {
        return model.hasPendingChanges();
    }

    public void commitCurrentState() {
        model.commitCurrentState();
        updateRows();
    }

    public void resetChanges() {
        model.resetChanges();
        updateRows();
    }

    public void setBusy(boolean busy) {
        progressBar.setVisible(busy);
        progressBar.setProgress(busy ? -1 : 0);
        updateActionState();
    }

    public void refreshRows() {
        syncVisibleItemCells();
        syncVisibleFolderHeaders(null);
        updateActionState();
    }

    private void configureList() {
        DlcFxStyle.add(listView, "dlc-dlc-list");
        listView.setCellFactory(param -> new ItemCell());
        listView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
    }

    private void configureFilter() {
        searchField.setPromptText(DlcFxI18n.tr("dlc_manager.local.search_prompt"));
        searchField.setPrefColumnCount(30);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            currentSearchText = newValue == null ? "" : newValue;
            updateRows();
        });
        typeFilter.setPromptText(DlcFxI18n.tr("dlc_manager.local.all_types"));
        typeFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DlcTypeOption option) { return typeText(option); }
            @Override public DlcTypeOption fromString(String s) { return DlcTypeOption.all(); }
        });
        typeFilter.setButtonCell(new DlcTypeButtonCell(typeFilter));
        typeFilter.setCellFactory(param -> new DlcTypePopupCell());
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> {
            currentTypeFilter = typeOf(newValue);
            model.clearSelection();
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
            if (type != null && adapter.visibleType(type)) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            for (DlcType type : DlcType.values()) {
                if (adapter.visibleType(type)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    private void updateRows() {
        List<DlcFxDlcManagementListModel.Row<T>> nextRows = new ArrayList<>();
        if (currentTypeFilter == null && !redirectEntries.isEmpty()) {
            boolean redirectCollapsed = collapsedFolders.contains(DlcFxDlcManagementListModel.REDIRECT_FOLDER_KEY);
            nextRows.add(DlcFxDlcManagementListModel.Row.redirectHeader(
                    DlcFxI18n.tr("dlc_manager.redirect.header"),
                    DlcFxDlcManagementListModel.REDIRECT_FOLDER_KEY,
                    redirectCollapsed
            ));
            if (!redirectCollapsed) {
                for (DlcRedirectEntry entry : redirectEntries) {
                    nextRows.add(DlcFxDlcManagementListModel.Row.redirectItem(entry, DlcFxDlcManagementListModel.REDIRECT_FOLDER_KEY));
                }
            }
        }
        nextRows.addAll(DlcFxDlcManagementListModel.rows(
                items.stream().filter(item -> adapter.type(item) != null).toList(),
                currentSearchText,
                currentTypeFilter,
                adapter::visibleType,
                adapter::folder,
                adapter::displayName,
                adapter::type,
                adapter::folderLabel,
                collapsedFolders::contains,
                model::isDirty
        ));
        rows.setAll(nextRows);
        listView.refresh();
        updateActionState();
    }

    private List<T> visibleItems() {
        List<T> visible = new ArrayList<>();
        for (DlcFxDlcManagementListModel.Row<T> row : rows) {
            if (row.type == DlcFxDlcManagementListModel.RowType.ITEM && row.item != null) {
                visible.add(row.item);
            }
        }
        return visible;
    }

    private void enableSelected() {
        List<T> targets = new ArrayList<>();
        for (T item : model.selectedItems()) {
            if (adapter.canEnable(item)) {
                targets.add(item);
            }
        }
        enableHandler.accept(targets);
        model.clearSelection();
        syncVisibleItemCells();
        updateRows();
    }

    private void disableSelected() {
        List<T> targets = new ArrayList<>();
        for (T item : model.selectedItems()) {
            if (adapter.canDisable(item)) {
                targets.add(item);
            }
        }
        disableHandler.accept(targets);
        model.clearSelection();
        syncVisibleItemCells();
        updateRows();
    }

    private void setEffectiveEnabled(T item, boolean enabled) {
        model.setEffectiveEnabled(item, enabled);
    }

    private void setEffectiveEnabled(Collection<T> targetItems, boolean enabled) {
        model.setEffectiveEnabled(targetItems, enabled);
    }

    private void updateActionState() {
        boolean busy = progressBar != null && progressBar.isVisible();
        boolean pending = available && model.hasPendingChanges();
        applyBtn.setDisable(!pending || busy);
        cancelBtn.setDisable(!pending || busy);
        pendingWarningLabel.setVisible(pending);
        pendingWarningLabel.setManaged(pending);
        boolean hasSelection = !model.selectedItems().isEmpty();
        enableSelectedBtn.setVisible(hasSelection);
        enableSelectedBtn.setManaged(hasSelection);
        enableSelectedBtn.setDisable(!available || !hasSelection || busy);
        disableSelectedBtn.setVisible(hasSelection);
        disableSelectedBtn.setManaged(hasSelection);
        disableSelectedBtn.setDisable(!available || !hasSelection || busy);
    }

    private boolean setFolderCollapsed(DlcFxDlcManagementListModel.Row<T> row, boolean collapsed) {
        if (row == null || row.folderKey == null || row.folderKey.isBlank()) {
            return false;
        }
        if (animatingFolders.contains(row.folderKey)) {
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

    private void animateFolderCollapse(DlcFxDlcManagementListModel.Row<T> row, int headerIndex) {
        animatingFolders.add(row.folderKey);
        collapsedFolders.add(row.folderKey);
        row.collapsed = true;
        List<DlcFxDlcManagementListModel.Row<T>> targets = folderItemRowsAfter(headerIndex);
        if (targets.isEmpty()) {
            finishFolderAnimation(row.folderKey);
            return;
        }
        for (DlcFxDlcManagementListModel.Row<T> target : targets) {
            target.expanding = false;
            target.collapsing = true;
        }
        startVisibleFolderAnimations(row.folderKey);
        PauseTransition removeAfterAnimation = new PauseTransition(Duration.millis(260));
        removeAfterAnimation.setOnFinished(event -> {
            rows.removeAll(targets);
            for (DlcFxDlcManagementListModel.Row<T> target : targets) {
                if (target.collapsing && !target.expanding) {
                    target.collapsing = false;
                }
            }
            finishFolderAnimation(row.folderKey);
        });
        removeAfterAnimation.play();
    }

    private void animateFolderExpand(DlcFxDlcManagementListModel.Row<T> row, int headerIndex) {
        animatingFolders.add(row.folderKey);
        collapsedFolders.remove(row.folderKey);
        row.collapsed = false;
        List<DlcFxDlcManagementListModel.Row<T>> nextRows = itemRowsForFolder(row);
        if (nextRows.isEmpty()) {
            finishFolderAnimation(row.folderKey);
            return;
        }
        for (DlcFxDlcManagementListModel.Row<T> item : nextRows) {
            item.expanding = true;
            item.collapsing = false;
        }
        rows.addAll(headerIndex + 1, nextRows);
        Platform.runLater(() -> startVisibleFolderAnimations(row.folderKey));
        PauseTransition finishExpansion = new PauseTransition(Duration.millis(260));
        finishExpansion.setOnFinished(event -> {
            for (DlcFxDlcManagementListModel.Row<T> item : nextRows) {
                if (item.expanding && !item.collapsing) {
                    item.expanding = false;
                }
            }
            finishFolderAnimation(row.folderKey);
        });
        finishExpansion.play();
    }

    private void finishFolderAnimation(String folderKey) {
        if (folderKey != null) {
            animatingFolders.remove(folderKey);
        }
        syncVisibleFolderHeaders(folderKey);
        syncVisibleItemCells();
    }

    private void startVisibleFolderAnimations(String folderKey) {
        for (ItemCell cell : new ArrayList<>(activeItemCells)) {
            if (cell != null) {
                cell.startAnimationForFolder(folderKey);
            }
        }
        for (ItemCell cell : new ArrayList<>(activeAnimatedCells)) {
            if (cell != null) {
                cell.startAnimationForFolder(folderKey);
            }
        }
    }

    private void syncVisibleFolderHeaders(String folderKey) {
        for (ItemCell cell : new ArrayList<>(activePathCells)) {
            if (cell != null) {
                cell.syncPathState(folderKey);
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

    private boolean isFolderAnimating(String folderKey) {
        return folderKey != null && animatingFolders.contains(folderKey);
    }

    private List<DlcFxDlcManagementListModel.Row<T>> folderItemRowsAfter(int headerIndex) {
        List<DlcFxDlcManagementListModel.Row<T>> out = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            DlcFxDlcManagementListModel.Row<T> row = rows.get(i);
            if (row.type != DlcFxDlcManagementListModel.RowType.ITEM
                    && row.type != DlcFxDlcManagementListModel.RowType.REDIRECT_ITEM) {
                break;
            }
            out.add(row);
        }
        return out;
    }

    private List<DlcFxDlcManagementListModel.Row<T>> itemRowsForFolder(DlcFxDlcManagementListModel.Row<T> folderRow) {
        if (folderRow == null || folderRow.folderKey == null) {
            return List.of();
        }
        List<DlcFxDlcManagementListModel.Row<T>> allRows = DlcFxDlcManagementListModel.rows(
                items.stream().filter(item -> adapter.type(item) != null).toList(),
                currentSearchText,
                currentTypeFilter,
                adapter::visibleType,
                adapter::folder,
                adapter::displayName,
                adapter::type,
                adapter::folderLabel,
                key -> false,
                model::isDirty
        );
        List<DlcFxDlcManagementListModel.Row<T>> out = new ArrayList<>();
        for (DlcFxDlcManagementListModel.Row<T> row : allRows) {
            if (row.type == DlcFxDlcManagementListModel.RowType.ITEM && folderRow.folderKey.equals(row.itemFolderKey)) {
                out.add(row);
            }
        }
        return out;
    }

    private final class ItemCell extends ListCell<DlcFxDlcManagementListModel.Row<T>> {
        private final CheckBox selectBox = new CheckBox();
        private final Region accent = new Region();
        private final Label typeLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label redirectLabel = new Label();
        private final Label pendingLabel = new Label();
        private final Label blockedLabel = new Label();
        private final Button toggleButton = new Button();
        private final Button redirectButton = new Button();
        private final GridPane itemGrid = new GridPane();
        private final GridPane redirectGrid = new GridPane();
        private final HBox nameBox = new HBox(8, selectBox, accent, nameLabel, pendingLabel, blockedLabel);
        private final HBox itemRow = new HBox(0, itemGrid);
        private final HBox redirectRow = new HBox(0, redirectGrid);
        private final StackPane itemAnimationPane = new StackPane(itemRow);
        private final Label groupLabel = new Label();
        private final Label pathLabel = new Label();
        private final Button collapseButton = new Button();
        private final SVGPath collapseIcon = new SVGPath();
        private final StackPane collapseIconBox = new StackPane(collapseIcon);
        private javafx.scene.control.Tooltip rowTooltip;
        private final BorderPane pathRow = new BorderPane();
        private final Rectangle rowClip = new Rectangle();
        private Timeline rowTimeline;
        private RotateTransition collapseTransition;
        private DlcFxDlcManagementListModel.Row<T> animatedRow;
        private Boolean animatedExpansion;

        ItemCell() {
            DlcFxStyle.title(nameLabel);
            DlcFxStyle.title(redirectLabel);
            DlcFxStyle.muted(pathLabel);
            DlcFxStyle.add(pendingLabel, "dlc-pending-badge");
            DlcFxStyle.add(blockedLabel, "dlc-preset-disabled-badge");
            DlcFxStyle.add(accent, "dlc-row-accent");
            DlcFxStyle.add(itemRow, "dlc-dlc-row");
            DlcFxStyle.add(redirectRow, "dlc-dlc-row");
            DlcFxStyle.add(typeLabel, "dlc-dlc-type-label");
            DlcFxStyle.add(groupLabel, "dlc-dlc-group-header");
            DlcFxStyle.add(redirectButton, "dlc-redirect-open-button");
            DlcFxStyle.add(pathLabel, "dlc-dlc-path-header");
            DlcFxStyle.add(pathRow, "dlc-dlc-path-row");
            DlcFxStyle.add(collapseButton, "dlc-folder-collapse-button");
            DlcFxStyle.add(collapseIconBox, "dlc-folder-collapse-icon-box");
            DlcFxStyle.add(collapseIcon, "dlc-folder-collapse-icon");
            collapseIcon.setContent("M 3 10 L 8 5 L 13 10");
            collapseIcon.setFill(Color.TRANSPARENT);
            collapseIcon.setStroke(Color.web("#475467"));
            collapseIcon.setStrokeWidth(1.8);
            collapseIcon.setStrokeLineCap(StrokeLineCap.ROUND);
            collapseIcon.setStrokeLineJoin(StrokeLineJoin.ROUND);
            rowClip.widthProperty().bind(itemAnimationPane.widthProperty());
            rowClip.heightProperty().bind(itemAnimationPane.heightProperty());
            pendingLabel.setMinWidth(64);
            pendingLabel.setPrefWidth(64);
            pendingLabel.setMaxWidth(64);
            pendingLabel.setOpacity(0);
            blockedLabel.setMinWidth(88);
            blockedLabel.setPrefWidth(88);
            blockedLabel.setMaxWidth(88);
            blockedLabel.setOpacity(0);
            toggleButton.setMinWidth(96);
            toggleButton.setPrefWidth(96);
            toggleButton.setMaxWidth(96);
            redirectButton.setMinWidth(96);
            redirectButton.setPrefWidth(96);
            redirectButton.setMaxWidth(96);
            nameBox.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setAlignment(Pos.CENTER_LEFT);
            itemGrid.setHgap(12);
            itemGrid.add(nameBox, 0, 0);
            itemGrid.add(typeLabel, 1, 0);
            itemGrid.add(toggleButton, 2, 0);
            GridPane.setHgrow(nameBox, Priority.ALWAYS);
            itemGrid.getColumnConstraints().addAll(
                    column(420, 520, Priority.ALWAYS),
                    column(112, 150, Priority.NEVER),
                    column(96, 96, Priority.NEVER)
            );
            itemGrid.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemGrid.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_ITEM_CONTENT_MIN_WIDTH));
            redirectGrid.setAlignment(Pos.CENTER_LEFT);
            redirectGrid.setHgap(12);
            redirectGrid.add(redirectLabel, 0, 0);
            redirectGrid.add(redirectButton, 1, 0);
            GridPane.setHgrow(redirectLabel, Priority.ALWAYS);
            redirectGrid.getColumnConstraints().addAll(
                    column(540, 640, Priority.ALWAYS),
                    column(96, 96, Priority.NEVER)
            );
            redirectGrid.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            redirectGrid.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_ITEM_CONTENT_MIN_WIDTH));
            HBox.setHgrow(itemGrid, Priority.ALWAYS);
            HBox.setHgrow(redirectGrid, Priority.ALWAYS);
            itemRow.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            redirectRow.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            redirectRow.setAlignment(Pos.CENTER_LEFT);
            itemAnimationPane.setAlignment(Pos.CENTER_LEFT);
            itemAnimationPane.setMinWidth(LIST_ITEM_CONTENT_MIN_WIDTH);
            itemAnimationPane.prefWidthProperty().bind(itemGrid.prefWidthProperty());
            itemRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (isInside(event.getTarget(), selectBox) || isInside(event.getTarget(), toggleButton)) {
                    return;
                }
                DlcFxDlcManagementListModel.Row<T> row = super.getItem();
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() >= 2
                        && row != null
                        && row.type == DlcFxDlcManagementListModel.RowType.ITEM
                        && row.item != null
                        && !isFolderAnimating(row.itemFolderKey)) {
                    adapter.openRedirect(row.item);
                }
                event.consume();
            });
            itemRow.setOnMouseClicked(event -> event.consume());
            redirectRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), redirectButton)) {
                    event.consume();
                }
            });
            redirectRow.setOnMouseClicked(event -> event.consume());
            pathRow.setCenter(pathLabel);
            pathRow.setRight(collapseButton);
            BorderPane.setAlignment(pathLabel, Pos.CENTER_LEFT);
            BorderPane.setAlignment(collapseButton, Pos.CENTER_RIGHT);
            pathLabel.setMaxWidth(Double.MAX_VALUE);
            pathRow.setMinWidth(LIST_PATH_CONTENT_MIN_WIDTH);
            pathRow.prefWidthProperty().bind(Bindings.max(widthProperty().subtract(LIST_ROW_WIDTH_RESERVED), LIST_PATH_CONTENT_MIN_WIDTH));
            pathRow.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!isInside(event.getTarget(), collapseButton)) {
                    event.consume();
                }
                if (event.getButton() == MouseButton.PRIMARY) {
                    listView.getSelectionModel().clearSelection();
                }
            });
            collapseButton.setMinWidth(28);
            collapseButton.setPrefWidth(28);
            collapseButton.setMaxWidth(28);
            collapseButton.setText(null);
            collapseButton.setGraphic(collapseIconBox);
            collapseButton.setOnAction(event -> {
                DlcFxDlcManagementListModel.Row<T> current = getItem();
                if (current != null && (current.folderKey == null || !animatingFolders.contains(current.folderKey))) {
                    boolean targetCollapsed = !current.collapsed;
                    if (setFolderCollapsed(current, targetCollapsed)) {
                        collapseButton.setDisable(true);
                        animateCollapseButton(targetCollapsed);
                    }
                }
                event.consume();
            });
            selectBox.setOnMouseClicked(event -> {
                DlcFxDlcManagementListModel.Row<T> row = super.getItem();
                if (row == null || row.type != DlcFxDlcManagementListModel.RowType.ITEM || row.item == null) {
                    event.consume();
                    return;
                }
                if (isFolderAnimating(row.itemFolderKey)) {
                    selectBox.setSelected(model.isSelected(row.item));
                    event.consume();
                    return;
                }
                model.setSelected(row.item, selectBox.isSelected());
                syncVisibleItemCells();
                updateActionState();
                event.consume();
            });
            DlcFxTooltips.followMouse(this, () -> rowTooltip);
            toggleButton.setOnAction(e -> {
                T item = currentItem();
                DlcFxDlcManagementListModel.Row<T> row = super.getItem();
                if (item != null && (row == null || !isFolderAnimating(row.itemFolderKey))) {
                    if (model.effectiveEnabled(item)) {
                        disableHandler.accept(List.of(item));
                    } else {
                        enableHandler.accept(List.of(item));
                    }
                    model.clearSelection();
                    syncVisibleItemCells();
                    updateRows();
                }
            });
            redirectButton.setText(DlcFxI18n.tr("dlc_manager.redirect.open"));
            redirectButton.setOnAction(event -> {
                DlcFxDlcManagementListModel.Row<T> row = super.getItem();
                if (row != null && row.type == DlcFxDlcManagementListModel.RowType.REDIRECT_ITEM) {
                    redirectOpenHandler.accept(row.redirectEntry);
                }
                event.consume();
            });
        }

        @Override
        protected void updateItem(DlcFxDlcManagementListModel.Row<T> row, boolean empty) {
            super.updateItem(row, empty);
            activeItemCells.remove(this);
            activeAnimatedCells.remove(this);
            activePathCells.remove(this);
            getStyleClass().removeAll("dlc-row-group", "dlc-row-path", "dlc-row-item", "dlc-dlc-selected");
            rowTooltip = null;
            if (empty || row == null) {
                resetCellAnimation();
                setMouseTransparent(false);
                setGraphic(null);
                setText(null);
                return;
            }
            if (row.type == DlcFxDlcManagementListModel.RowType.GROUP_HEADER) {
                resetCellAnimation();
                setMouseTransparent(false);
                getStyleClass().add("dlc-row-group");
                groupLabel.setText(DlcFxI18n.tr(row.groupType.getDisplayKey()));
                setGraphic(groupLabel);
                setText(null);
                return;
            }
            if (row.type == DlcFxDlcManagementListModel.RowType.REDIRECT_HEADER
                    || row.type == DlcFxDlcManagementListModel.RowType.PATH_HEADER) {
                resetCellAnimation();
                setMouseTransparent(false);
                activePathCells.add(this);
                getStyleClass().add("dlc-row-path");
                pathLabel.setText(row.label);
                syncPathState(row.folderKey);
                setGraphic(pathRow);
                setText(null);
                return;
            }
            if (row.type == DlcFxDlcManagementListModel.RowType.REDIRECT_ITEM) {
                resetCellAnimation();
                setMouseTransparent(false);
                activeAnimatedCells.add(this);
                getStyleClass().add("dlc-row-item");
                String lang = com.cozooo.dlc_manager.platform.CrossPlatformRuntime.languageCode();
                DlcFxRichTextRenderer.setFormatted(redirectLabel, row.redirectEntry == null ? "" : row.redirectEntry.resolveDisplayName(lang));
                String tip = row.redirectEntry == null ? "" : row.redirectEntry.resolveTip(lang);
                rowTooltip = tip == null || tip.isBlank() ? null : DlcFxRichTextRenderer.formattedTooltip(tip);
                itemAnimationPane.getChildren().setAll(redirectRow);
                setGraphic(itemAnimationPane);
                setText(null);
                startRowAnimationIfNeeded(row);
                return;
            }
            T item = row.item;
            activeItemCells.add(this);
            activeAnimatedCells.add(this);
            getStyleClass().add("dlc-row-item");
            setMouseTransparent(false);
            DlcType type = adapter.type(item);
            typeLabel.getStyleClass().removeAll("type-mod", "type-resource", "type-data", "type-tacz", "type-unknown");
            typeLabel.getStyleClass().add(typeStyleClass(type));
            typeLabel.setText(type == null ? adapter.typeText(item) : DlcFxI18n.tr(type.getDisplayKey()));
            DlcFxRichTextRenderer.setFormatted(nameLabel, adapter.displayName(item));
            String tooltip = adapter.tooltip(item);
            rowTooltip = tooltip == null || tooltip.isBlank() ? null : DlcFxRichTextRenderer.formattedTooltip(tooltip);
            setText(null);
            itemAnimationPane.getChildren().setAll(itemRow);
            setGraphic(itemAnimationPane);
            syncItemState();
            startRowAnimationIfNeeded(row);
        }

        private void syncPathState(String folderKey) {
            DlcFxDlcManagementListModel.Row<T> row = super.getItem();
            if (row == null || (row.type != DlcFxDlcManagementListModel.RowType.PATH_HEADER
                    && row.type != DlcFxDlcManagementListModel.RowType.REDIRECT_HEADER)) {
                return;
            }
            if (folderKey == null || folderKey.equals(row.folderKey)) {
                collapseIcon.setRotate(row.collapsed ? 180 : 0);
                collapseButton.setDisable(isFolderAnimating(row.folderKey));
            }
        }

        private void syncItemState() {
            T item = currentItem();
            if (item == null) {
                return;
            }
            boolean dirty = model.isDirty(item);
            boolean selected = model.isSelected(item);
            getStyleClass().removeAll("dlc-dlc-selected");
            if (selected) {
                getStyleClass().add("dlc-dlc-selected");
            }
            selectBox.setSelected(selected);
            accent.getStyleClass().removeAll("enabled", "pending", "off");
            accent.getStyleClass().add(dirty ? "pending" : model.effectiveEnabled(item) ? "enabled" : "off");
            pendingLabel.setText(dirty ? DlcFxI18n.tr("dlc_manager.pending.apply") : "");
            pendingLabel.setManaged(true);
            pendingLabel.setVisible(true);
            pendingLabel.setOpacity(dirty ? 1 : 0);
            boolean blocked = adapter.blocked(item);
            blockedLabel.setText(blocked ? adapter.blockedText(item) : "");
            blockedLabel.setManaged(blocked);
            blockedLabel.setVisible(blocked);
            blockedLabel.setOpacity(blocked ? 1 : 0);
            toggleButton.setText(model.effectiveEnabled(item)
                    ? DlcFxI18n.tr("dlc_manager.toggle.on")
                    : DlcFxI18n.tr("dlc_manager.toggle.off"));
            toggleButton.getStyleClass().removeAll("dlc-toggle-on", "dlc-toggle-off", "dlc-toggle-pending");
            toggleButton.getStyleClass().add(dirty ? "dlc-toggle-pending" : model.effectiveEnabled(item) ? "dlc-toggle-on" : "dlc-toggle-off");
            DlcFxDlcManagementListModel.Row<T> row = super.getItem();
            boolean folderAnimating = row != null && isFolderAnimating(row.itemFolderKey);
            itemRow.setMouseTransparent(folderAnimating);
            selectBox.setDisable(folderAnimating);
            toggleButton.setDisable(folderAnimating
                    || blocked
                    || (!model.effectiveEnabled(item) && (adapter.missingDependencies(item) || !adapter.canEnable(item)))
                    || (model.effectiveEnabled(item) && !adapter.canDisable(item)));
        }

        private T currentItem() {
            DlcFxDlcManagementListModel.Row<T> row = super.getItem();
            return row == null ? null : row.item;
        }

        private void startAnimationForFolder(String folderKey) {
            DlcFxDlcManagementListModel.Row<T> row = super.getItem();
            if (row == null || !folderKey.equals(row.itemFolderKey)
                    || (row.type != DlcFxDlcManagementListModel.RowType.ITEM
                    && row.type != DlcFxDlcManagementListModel.RowType.REDIRECT_ITEM)) {
                return;
            }
            if (row.type == DlcFxDlcManagementListModel.RowType.ITEM) {
                syncItemState();
            }
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

        private void startRowAnimationIfNeeded(DlcFxDlcManagementListModel.Row<T> row) {
            if (row == null) {
                resetCellAnimation();
                return;
            }
            if (row.collapsing && !isCurrentAnimation(row, false)) {
                animateRow(row, false, Math.max(getCurrentAnimatedHeight(), 42), 0, 1, 0, null);
                return;
            }
            if (row.expanding && !isCurrentAnimation(row, true)) {
                animateRow(row, true, 0, 42, 0, 1, () -> {
                    if (super.getItem() == row && row.expanding && !row.collapsing) {
                        row.expanding = false;
                        resetCellAnimation();
                    }
                });
                return;
            }
            if (!row.expanding && !row.collapsing) {
                resetCellAnimation();
            }
        }

        private boolean isCurrentAnimation(DlcFxDlcManagementListModel.Row<T> row, boolean expandingAnimation) {
            return rowTimeline != null && animatedRow == row && animatedExpansion != null && animatedExpansion == expandingAnimation;
        }

        private void animateRow(DlcFxDlcManagementListModel.Row<T> row, boolean expandingAnimation, double fromHeight, double toHeight,
                                double fromOpacity, double toOpacity, Runnable afterFinished) {
            if (rowTimeline != null) {
                rowTimeline.stop();
            }
            animatedRow = row;
            animatedExpansion = expandingAnimation;
            itemAnimationPane.setClip(rowClip);
            itemAnimationPane.setMinHeight(fromHeight);
            itemAnimationPane.setPrefHeight(fromHeight);
            itemAnimationPane.setMaxHeight(fromHeight);
            itemAnimationPane.setOpacity(fromOpacity);
            rowTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(itemAnimationPane.minHeightProperty(), fromHeight),
                            new KeyValue(itemAnimationPane.prefHeightProperty(), fromHeight),
                            new KeyValue(itemAnimationPane.maxHeightProperty(), fromHeight),
                            new KeyValue(itemAnimationPane.opacityProperty(), fromOpacity)
                    ),
                    new KeyFrame(Duration.millis(240),
                            new KeyValue(itemAnimationPane.minHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                            new KeyValue(itemAnimationPane.prefHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                            new KeyValue(itemAnimationPane.maxHeightProperty(), toHeight, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)),
                            new KeyValue(itemAnimationPane.opacityProperty(), toOpacity, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0))
                    )
            );
            rowTimeline.setOnFinished(event -> {
                if (super.getItem() != row || row.expanding != expandingAnimation || row.collapsing == expandingAnimation) {
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
            double height = itemAnimationPane.getHeight();
            if (Double.isNaN(height) || height <= 0) {
                height = itemAnimationPane.getPrefHeight();
            }
            if (Double.isNaN(height) || height <= 0 || height == Region.USE_COMPUTED_SIZE || height == Region.USE_PREF_SIZE) {
                return 42;
            }
            return height;
        }

        private void resetCellAnimation() {
            if (rowTimeline != null) {
                rowTimeline.stop();
                rowTimeline = null;
            }
            animatedRow = null;
            animatedExpansion = null;
            itemAnimationPane.setMinHeight(Region.USE_PREF_SIZE);
            itemAnimationPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            itemAnimationPane.setMaxHeight(Region.USE_COMPUTED_SIZE);
            itemAnimationPane.setOpacity(1);
            itemAnimationPane.setClip(null);
        }

        private boolean isInside(Object target, Node parent) {
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
    }

    private static String typeStyleClass(DlcType type) {
        if (type == null) {
            return "type-unknown";
        }
        return switch (type) {
            case MOD -> "type-mod";
            case RESOURCE_PACK -> "type-resource";
            case DATA_PACK -> "type-data";
            case TACZ_PACK -> "type-tacz";
            default -> "type-unknown";
        };
    }

    private static DlcType typeOf(DlcTypeOption option) {
        return option == null || option.all ? null : option.type;
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

    private static String typeText(DlcTypeOption option) {
        DlcType type = typeOf(option);
        return type == null ? DlcFxI18n.tr("dlc_manager.local.all_types") : DlcFxI18n.tr(type.getDisplayKey());
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

        @Override
        public String toString() {
            return typeText(this);
        }
    }

    private static ColumnConstraints column(double min, double pref, Priority grow) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(min);
        constraints.setPrefWidth(pref);
        constraints.setHgrow(grow);
        return constraints;
    }
}
