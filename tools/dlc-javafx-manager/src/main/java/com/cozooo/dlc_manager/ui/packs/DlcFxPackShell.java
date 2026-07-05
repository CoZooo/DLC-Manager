package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.common.DlcFxListViewSupport;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackActionState;

import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class DlcFxPackShell<T> {
    private final BorderPane root = new BorderPane();
    private final ObservableList<T> items;
    private final Label countLabel;
    private final Label statusLabel;
    private final Label operationStatusLabel;
    private final ProgressBar progressBar;
    private final Button refreshBtn;
    private final Button importBtn;
    private final Button importUrlBtn;
    private final Button applyBtn;
    private final Button cancelBtn;
    private final ListView<T> listView;

    public DlcFxPackShell(ObservableList<T> items,
                          Label countLabel,
                          Label statusLabel,
                          Label operationStatusLabel,
                          ProgressBar progressBar,
                          Button refreshBtn,
                          Button importBtn,
                          Button importUrlBtn,
                          Button applyBtn,
                          Button cancelBtn) {
        this.items = items;
        this.countLabel = countLabel;
        this.statusLabel = statusLabel;
        this.operationStatusLabel = operationStatusLabel;
        this.progressBar = progressBar;
        this.refreshBtn = refreshBtn;
        this.importBtn = importBtn;
        this.importUrlBtn = importUrlBtn;
        this.applyBtn = applyBtn;
        this.cancelBtn = cancelBtn;
        this.listView = new ListView<>(items);

        configure();
    }

    public BorderPane root() {
        return root;
    }

    public ListView<T> listView() {
        return listView;
    }

    public void applyActionState(DlcFxPackActionState state) {
        if (state == null) {
            return;
        }
        refreshBtn.setDisable(state.refreshDisabled());
        importBtn.setDisable(state.importDisabled());
        importUrlBtn.setDisable(state.importUrlDisabled());
        applyBtn.setDisable(state.applyDisabled());
        cancelBtn.setDisable(state.cancelDisabled());
        cancelBtn.setVisible(state.cancelVisible());
        cancelBtn.setManaged(state.cancelVisible());
    }

    private void configure() {
        DlcFxStyle.view(root);
        DlcFxStyle.primary(refreshBtn);
        DlcFxStyle.primary(applyBtn);
        DlcFxStyle.routeStatus(statusLabel);
        DlcFxStyle.muted(operationStatusLabel);

        HBox topRow = new HBox(8, refreshBtn, importBtn, importUrlBtn, countLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox topBar = new HBox(8, topRow);
        topBar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(topBar);

        DlcFxStyle.add(listView, "dlc-pack-list");
        HBox listFrame = DlcFxListViewSupport.wrap(listView);
        VBox listBox = new VBox(8, listFrame);
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox.setVgrow(listFrame, Priority.ALWAYS);
        listView.setMaxHeight(Double.MAX_VALUE);
        listBox.setPadding(new javafx.geometry.Insets(14, 16, 12, 16));
        DlcFxStyle.section(listBox);

        HBox actionRow = new HBox(8, applyBtn, cancelBtn, progressBar, operationStatusLabel, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.actionPanel(actionRow);

        root.setTop(topBar);
        root.setCenter(listBox);
        root.setBottom(actionRow);
    }
}
