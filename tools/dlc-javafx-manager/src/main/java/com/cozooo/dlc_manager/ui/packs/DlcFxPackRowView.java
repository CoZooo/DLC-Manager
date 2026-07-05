package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowState;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DlcFxPackRowView {
    private DlcFxPackRowView() {
    }

    public record Spec(
        DlcFxPackRowState state,
        Node title,
        Node author,
        Node versionLine,
        Node description,
        Node icon,
        String actionText,
        Runnable moveUpAction,
        Runnable moveDownAction,
        Runnable installAction,
        Runnable deleteAction,
        Runnable issueAction,
        Runnable sponsorAction
    ) {
    }

    public record VersionOption(String key, String text, boolean selected) {
    }

    public static Node create(Spec spec) {
        DlcFxPackRowState state = spec.state();
        Node title = spec.title();
        Node author = spec.author();
        Node versionLine = spec.versionLine();
        Node description = spec.description();
        allowWrap(title);
        allowWrap(author);
        allowWrap(description);

        javafx.scene.control.Label homepageHint = new javafx.scene.control.Label(DlcFxI18n.tr("dlc_manager.pack.double_click_homepage"));
        DlcFxStyle.add(homepageHint, "dlc-pack-homepage-hint");
        homepageHint.setVisible(state.showHomepageHint());
        homepageHint.setManaged(state.showHomepageHint());

        HBox titleRow = new HBox(10, title, homepageHint);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setPrefWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);

        VBox textBox = new VBox(4, titleRow, author, versionLine, description);
        DlcFxStyle.add(textBox, "dlc-pack-text");
        textBox.setMinWidth(0);
        textBox.setPrefWidth(0);
        textBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Button up = new Button("▲");
        up.setOnAction(event -> run(spec.moveUpAction()));
        Button down = new Button("▼");
        down.setOnAction(event -> run(spec.moveDownAction()));
        setReservedVisible(up, state.showMoveUp());
        setReservedVisible(down, state.showMoveDown());
        VBox orderButtons = new VBox(6, up, down);
        orderButtons.setAlignment(Pos.CENTER);
        DlcFxStyle.add(orderButtons, "dlc-pack-order-buttons");

        Button install = new Button(spec.actionText());
        install.setOnAction(event -> run(spec.installAction()));
        Button delete = new Button(DlcFxI18n.tr("dlc_manager.common.delete"));
        delete.setOnAction(event -> run(spec.deleteAction()));
        DlcFxStyle.danger(delete);
        Button issue = new Button(DlcFxI18n.tr("dlc_manager.pack.feedback"));
        issue.setOnAction(event -> run(spec.issueAction()));
        Button sponsor = new Button(DlcFxI18n.tr("dlc_manager.pack.support_author"));
        sponsor.setOnAction(event -> run(spec.sponsorAction()));

        HBox firstActions = new HBox(6, install, delete);
        HBox secondActions = new HBox(6);
        if (state.showIssueAction()) {
            secondActions.getChildren().add(issue);
        }
        if (state.showSponsorAction()) {
            secondActions.getChildren().add(sponsor);
        }
        VBox actions = new VBox(6, firstActions, secondActions);
        actions.setAlignment(Pos.CENTER_RIGHT);
        firstActions.setAlignment(Pos.CENTER_RIGHT);
        secondActions.setAlignment(Pos.CENTER_RIGHT);
        DlcFxStyle.add(actions, "dlc-pack-actions");

        HBox row = new HBox(12, orderButtons, spec.icon(), textBox, actions);
        DlcFxStyle.add(row, "dlc-pack-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        return row;
    }

    public static Node versionLine(String selectedText,
                                   boolean canSwitchVersion,
                                   List<VersionOption> options,
                                   Consumer<VersionOption> selectAction) {
        Label prefix = new Label(DlcFxI18n.tr("dlc_manager.pack.version_prefix"));
        DlcFxStyle.add(prefix, "dlc-pack-version-prefix");

        MenuButton versionButton = new MenuButton(selectedText == null ? "" : selectedText);
        DlcFxStyle.add(versionButton, "dlc-pack-version-button");
        versionButton.setDisable(!canSwitchVersion);
        for (VersionOption option : options == null ? List.<VersionOption>of() : options) {
            if (option == null) {
                continue;
            }
            MenuItem item = new MenuItem(option.text() == null ? "" : option.text());
            if (option.selected()) {
                item.getStyleClass().add("selected");
            }
            item.setOnAction(event -> {
                if (!option.selected() && selectAction != null) {
                    selectAction.accept(option);
                }
            });
            versionButton.getItems().add(item);
        }

        HBox line = new HBox(6, prefix, versionButton);
        DlcFxStyle.add(line, "dlc-pack-version-line");
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    public static Node packIcon(Image image, String fallbackName) {
        StackPane box = new StackPane();
        DlcFxStyle.add(box, "dlc-pack-icon");
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(58);
            view.setFitHeight(58);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            box.getChildren().add(view);
        } else {
            Label initial = new Label(packInitial(fallbackName));
            DlcFxStyle.add(initial, "dlc-pack-icon-initial");
            box.getChildren().add(initial);
        }
        return box;
    }

    public static void setReservedVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(true);
        button.setMouseTransparent(!visible);
        button.setDisable(!visible);
    }

    public static void allowWrap(Node node) {
        if (node instanceof javafx.scene.layout.Region region) {
            region.setMinWidth(0);
            region.setPrefWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private static String packInitial(String name) {
        if (name == null || name.isBlank()) {
            return "P";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
