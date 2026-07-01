package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DlcFxRemoteSyncTab {

    private final Supplier<String> gameDirTextSupplier;
    private final BooleanSupplier gameDirSelectedSupplier;
    private final Runnable chooseGameDirAction;
    private final Runnable readLocalFilesAction;
    private final Label gameDirLabel = new Label();
    private final Button readLocalButton = new Button(DlcFxI18n.tr("dlc_manager.remote.read_local_files"));
    private final Button chooseDirButton = new Button(DlcFxI18n.tr("dlc_manager.local.choose_game_dir"));

    public DlcFxRemoteSyncTab(Supplier<String> gameDirTextSupplier,
                              BooleanSupplier gameDirSelectedSupplier,
                              Runnable chooseGameDirAction,
                              Runnable readLocalFilesAction) {
        this.gameDirTextSupplier = gameDirTextSupplier == null ? () -> "" : gameDirTextSupplier;
        this.gameDirSelectedSupplier = gameDirSelectedSupplier == null ? () -> false : gameDirSelectedSupplier;
        this.chooseGameDirAction = chooseGameDirAction == null ? () -> {} : chooseGameDirAction;
        this.readLocalFilesAction = readLocalFilesAction == null ? () -> {} : readLocalFilesAction;
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage("");

        chooseDirButton.setOnAction(e -> chooseGameDirAction.run());
        DlcFxStyle.primary(chooseDirButton);
        readLocalButton.setOnAction(e -> readLocalFilesAction.run());

        refreshState();

        Node top = DlcFxToolbar.create(
                new Label(DlcFxI18n.tr("dlc_manager.remote.local_source")),
                gameDirLabel,
                DlcFxToolbar.spacer(),
                chooseDirButton,
                readLocalButton
        );
        page.setTop(top);

        Label hint = new Label(DlcFxI18n.tr("dlc_manager.remote.sync_hint"));
        DlcFxStyle.muted(hint);
        VBox center = new VBox(10, hint);
        center.setAlignment(Pos.TOP_LEFT);
        page.setCenter(page.section(center));

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.remote_sync"), page.root());
    }

    public void onStorageContextChanged() {
        refreshState();
    }

    public void onTabSelected() {
        refreshState();
    }

    private void refreshState() {
        boolean selected = gameDirSelectedSupplier.getAsBoolean();
        gameDirLabel.setText(selected ? safeText(gameDirTextSupplier.get()) : DlcFxI18n.tr("dlc_manager.local.no_game_dir"));
        readLocalButton.setDisable(!selected);
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
