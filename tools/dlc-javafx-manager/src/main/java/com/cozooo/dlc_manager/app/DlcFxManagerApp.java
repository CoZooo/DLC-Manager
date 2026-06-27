package com.cozooo.dlc_manager.app;

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.local.DlcFxLocalDlcManagementTab;
import com.cozooo.dlc_manager.local.DlcFxLocalPresetTab;
import com.cozooo.dlc_manager.local.DlcFxLocalRemoteDownloadTab;
import com.cozooo.dlc_manager.local.DlcFxLocalRequiredTab;
import com.cozooo.dlc_manager.local.DlcFxLocalUpdateTab;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.remote.DlcFxRemoteConnection;
import com.cozooo.dlc_manager.remote.DlcFxRemoteDlcManagementTab;
import com.cozooo.dlc_manager.remote.DlcFxRemoteFileManagerTab;
import com.cozooo.dlc_manager.remote.DlcFxRemoteHotUpdateTab;
import com.cozooo.dlc_manager.remote.DlcFxRemotePackTab;
import com.cozooo.dlc_manager.remote.DlcFxRemoteRconTab;
import com.cozooo.dlc_manager.remote.DlcFxRemoteRemoteRequiredTab;
import com.cozooo.dlc_manager.remote.DlcFxRemoteServerConfigTab;
import com.cozooo.dlc_manager.remote.DlcFxRemoteServerConnectionTab;
import com.cozooo.dlc_manager.ui.DlcFxI18n;
import com.cozooo.dlc_manager.ui.DlcFxStyle;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

public final class DlcFxManagerApp extends Application {

    static final DlcFxPlatformProvider PROVIDER = new DlcFxPlatformProvider();

    private Stage primaryStage;
    private DlcFxLocalDlcManagementTab localDlcManagementTab;
    private DlcFxLocalRemoteDownloadTab localRemoteDownloadTab;
    private DlcFxLocalRequiredTab localRequiredTab;
    private DlcFxLocalUpdateTab localUpdateTab;

    private Label localGameDirLabel;
    private boolean localGameDirSelected;

    private DlcFxRemoteConnection remoteConnection;
    private BorderPane shell;
    private Node homeView;
    private Node localView;
    private Node remoteView;
    private ViewMode currentView = ViewMode.HOME;

    @Override
    public void init() {
        PlatformServices.setProvider(PROVIDER);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        remoteConnection = new DlcFxRemoteConnection();
        shell = new BorderPane();
        shell.setBottom(statusBar());
        DlcFxStyle.add(shell, "dlc-app-root");

        homeView = buildHomeView();
        localView = buildLocalWorkspace();
        remoteView = buildRemoteWorkspace();
        showHome();

        Scene scene = new Scene(shell, 1100, 720);
        URL css = DlcFxManagerApp.class.getResource("/com/cozooo/dlc_manager/javafx/theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        installApplicationIcon(primaryStage);
        primaryStage.setTitle(DlcFxI18n.tr("dlc_manager.window_title"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void installApplicationIcon(Stage primaryStage) {
        URL icon = DlcFxManagerApp.class.getResource("/assets/dlc_manager/textures/gui/icon.png");
        if (icon == null) {
            return;
        }
        primaryStage.getIcons().add(new Image(icon.toExternalForm()));
        installTaskbarIcon(icon);
    }

    private void installTaskbarIcon(URL icon) {
        try {
            if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return;
            }
            java.awt.Image awtIcon = ImageIO.read(icon);
            if (awtIcon != null) {
                taskbar.setIconImage(awtIcon);
            }
        } catch (IOException | RuntimeException | LinkageError ignored) {
        }
    }

    private Node buildHomeView() {
        VBox root = new VBox(22);
        root.setPadding(new Insets(28, 28, 30, 28));
        DlcFxStyle.view(root);

        HBox topBar = new HBox(languagePicker());
        topBar.setAlignment(Pos.CENTER_RIGHT);

        Label title = new Label(DlcFxI18n.tr("dlc_manager.home.title"));
        Label subtitle = new Label(DlcFxI18n.tr("dlc_manager.home.subtitle"));
        DlcFxStyle.add(title, "dlc-hero-title");
        DlcFxStyle.muted(subtitle);

        Node localChoice = modeChoice(
                DlcFxI18n.tr("dlc_manager.home.local_title"),
                DlcFxI18n.tr("dlc_manager.home.local_desc"),
                DlcFxI18n.tr("dlc_manager.home.enter_local"),
                this::showLocalWorkspace);
        Node remoteChoice = modeChoice(
                DlcFxI18n.tr("dlc_manager.home.remote_title"),
                DlcFxI18n.tr("dlc_manager.home.remote_desc"),
                DlcFxI18n.tr("dlc_manager.home.enter_remote"),
                this::showRemoteWorkspace);
        HBox choices = new HBox(16, localChoice, remoteChoice);
        HBox.setHgrow(localChoice, Priority.ALWAYS);
        HBox.setHgrow(remoteChoice, Priority.ALWAYS);

        VBox content = new VBox(10, title, subtitle, choices);
        VBox.setVgrow(choices, Priority.ALWAYS);
        content.setAlignment(Pos.TOP_LEFT);
        root.getChildren().addAll(topBar, content);
        return root;
    }

    private Node modeChoice(String titleText, String descText, String actionText, Runnable action) {
        VBox box = new VBox(10);
        box.getStyleClass().add("dlc-mode-panel");
        box.setPadding(new Insets(18, 18, 18, 18));
        box.setMinHeight(180);
        box.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(titleText);
        Label desc = new Label(descText);
        DlcFxStyle.add(title, "dlc-mode-title");
        DlcFxStyle.muted(desc);
        desc.setWrapText(true);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button enter = new Button(actionText);
        DlcFxStyle.primary(enter);
        enter.setMaxWidth(Double.MAX_VALUE);
        enter.setOnAction(e -> action.run());

        box.getChildren().addAll(title, desc, spacer, enter);
        return box;
    }

    private Node workspaceHeader(String titleText, String descText, Runnable backAction) {
        Button back = new Button(DlcFxI18n.tr("dlc_manager.workspace.back"));
        DlcFxStyle.primary(back);
        back.setOnAction(e -> backAction.run());

        Label title = new Label(titleText);
        Label desc = new Label(descText);
        DlcFxStyle.add(title, "dlc-workspace-title");
        DlcFxStyle.muted(desc);

        VBox text = new VBox(4, title, desc);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, back, text, spacer, languagePicker());
        header.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(header);
        return header;
    }

    private TabPane buildLocalTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().add(localDlcManagementTab());
        tabPane.getTabs().add(localRemoteDownloadTab());
        tabPane.getTabs().add(localRequiredTab());
        tabPane.getTabs().add(localPresetTab());
        tabPane.getTabs().add(localUpdateTab());
        return tabPane;
    }

    private TabPane buildRemoteTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().add(new DlcFxRemoteServerConnectionTab(remoteConnection).build());
        tabPane.getTabs().add(new DlcFxRemoteDlcManagementTab(remoteConnection).build());
        tabPane.getTabs().add(new DlcFxRemoteRemoteRequiredTab(remoteConnection).build());
        tabPane.getTabs().add(new DlcFxRemoteHotUpdateTab(remoteConnection).build());
        tabPane.getTabs().add(new DlcFxRemotePackTab(remoteConnection).build());
        tabPane.getTabs().add(new DlcFxRemoteRconTab(remoteConnection).build());
        tabPane.getTabs().add(new DlcFxRemoteFileManagerTab(remoteConnection, primaryStage).build());
        tabPane.getTabs().add(new DlcFxRemoteServerConfigTab(remoteConnection).build());
        return tabPane;
    }

    private Node buildLocalWorkspace() {
        BorderPane root = new BorderPane();
        VBox top = new VBox(
                workspaceHeader(
                        DlcFxI18n.tr("dlc_manager.workspace.local_title"),
                        DlcFxI18n.tr("dlc_manager.workspace.local_desc"),
                        this::showHome),
                localDirectoryBar());
        root.setTop(top);
        root.setCenter(buildLocalTabs());
        DlcFxStyle.add(root, "dlc-workspace-root");
        return root;
    }

    private Node buildRemoteWorkspace() {
        BorderPane root = new BorderPane();
        root.setTop(workspaceHeader(
                DlcFxI18n.tr("dlc_manager.workspace.remote_title"),
                DlcFxI18n.tr("dlc_manager.workspace.remote_desc"),
                this::showHome));
        root.setCenter(buildRemoteTabs());
        DlcFxStyle.add(root, "dlc-workspace-root");
        return root;
    }

    private void showHome() {
        currentView = ViewMode.HOME;
        shell.setCenter(homeView);
    }

    private void showLocalWorkspace() {
        currentView = ViewMode.LOCAL;
        shell.setCenter(localView);
    }

    private void showRemoteWorkspace() {
        currentView = ViewMode.REMOTE;
        shell.setCenter(remoteView);
    }

    private Tab localDlcManagementTab() {
        localDlcManagementTab = new DlcFxLocalDlcManagementTab(PROVIDER);
        localDlcManagementTab.setGameDirSelected(localGameDirSelected);
        return localDlcManagementTab.build();
    }

    private void onLocalGameDirChanged() {
        if (localDlcManagementTab != null) {
            localDlcManagementTab.onGameDirChanged();
        }
        if (localRemoteDownloadTab != null) {
            localRemoteDownloadTab.onGameDirChanged();
        }
        if (localRequiredTab != null) {
            localRequiredTab.onGameDirChanged();
        }
        if (localUpdateTab != null) {
            localUpdateTab.onGameDirChanged();
        }
    }

    private Tab localRemoteDownloadTab() {
        localRemoteDownloadTab = new DlcFxLocalRemoteDownloadTab(PROVIDER);
        return localRemoteDownloadTab.build();
    }

    private Tab localRequiredTab() {
        localRequiredTab = new DlcFxLocalRequiredTab(PROVIDER);
        return localRequiredTab.build();
    }

    private Tab localPresetTab() {
        return new DlcFxLocalPresetTab(PROVIDER).build();
    }

    private Tab localUpdateTab() {
        localUpdateTab = new DlcFxLocalUpdateTab(PROVIDER);
        return localUpdateTab.build();
    }

    private Node localDirectoryBar() {
        Button chooseDirBtn = new Button(DlcFxI18n.tr("dlc_manager.local.choose_game_dir"));
        chooseDirBtn.setOnAction(e -> chooseLocalGameDirectory());
        DlcFxStyle.primary(chooseDirBtn);

        localGameDirLabel = new Label(localGameDirSelected
                ? PROVIDER.gameDir().toString()
                : DlcFxI18n.tr("dlc_manager.local.no_game_dir"));
        DlcFxStyle.muted(localGameDirLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10,
                new Label(DlcFxI18n.tr("dlc_manager.local.game_dir")),
                localGameDirLabel,
                spacer,
                chooseDirBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.filterBar(bar);
        DlcFxStyle.add(bar, "dlc-local-context-bar");
        return bar;
    }

    private void chooseLocalGameDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(DlcFxI18n.tr("dlc_manager.local.choose_game_dir_title"));
        File current = new File(PROVIDER.gameDir().toString());
        if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File chosen = chooser.showDialog(primaryStage);
        if (chosen == null) {
            return;
        }

        Path gameDir = chosen.toPath();
        PROVIDER.setGameDir(gameDir);
        localGameDirSelected = true;
        if (localGameDirLabel != null) {
            localGameDirLabel.setText(gameDir.toString());
        }

        DlcConfig.load();
        DlcStateConfig.load();
        DlcRuntimeState.clearOverrides();
        DlcPresetRuntimeState.clearAll();
        onLocalGameDirChanged();
    }

    private javafx.scene.Node statusBar() {
        DlcFxStyle.setStatus(DlcFxI18n.tr("dlc_manager.status.ready"));
        Label localStatus = new Label();
        localStatus.textProperty().bind(DlcFxStyle.statusTextProperty());
        Label remoteStatus = new Label();
        remoteStatus.textProperty().bind(
                remoteConnection != null ? remoteConnection.statusTextProperty() : new javafx.beans.property.SimpleStringProperty(""));

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(8, localStatus, remoteStatus);
        javafx.scene.layout.HBox.setHgrow(localStatus, javafx.scene.layout.Priority.ALWAYS);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.add(bar, "dlc-status-root");
        DlcFxStyle.muted(localStatus);
        DlcFxStyle.muted(remoteStatus);
        return bar;
    }

    private Node languagePicker() {
        Label languageLabel = new Label(DlcFxI18n.tr("dlc_manager.language.label"));
        ComboBox<LanguageOption> languageBox = new ComboBox<>();
        languageBox.getItems().addAll(
                new LanguageOption("en_us", DlcFxI18n.tr("dlc_manager.language.en_us")),
                new LanguageOption("zh_cn", DlcFxI18n.tr("dlc_manager.language.zh_cn")));
        languageBox.setValue(optionForCurrentLanguage(languageBox));
        languageBox.setOnAction(event -> {
            LanguageOption option = languageBox.getValue();
            if (option != null && !option.code().equals(PROVIDER.languageOverride())) {
                PROVIDER.setLanguageOverride(option.code());
                rebuildForLanguageChange();
            }
        });

        HBox picker = new HBox(8, languageLabel, languageBox);
        languageBox.setPrefWidth(150);
        picker.setAlignment(Pos.CENTER_RIGHT);
        DlcFxStyle.add(picker, "dlc-language-picker");
        DlcFxStyle.muted(languageLabel);
        return picker;
    }

    private LanguageOption optionForCurrentLanguage(ComboBox<LanguageOption> languageBox) {
        String current = PROVIDER.languageCode();
        for (LanguageOption option : languageBox.getItems()) {
            if (option.code().equalsIgnoreCase(current)) {
                return option;
            }
        }
        return languageBox.getItems().isEmpty() ? null : languageBox.getItems().get(0);
    }

    private void rebuildForLanguageChange() {
        ViewMode target = currentView;
        primaryStage.setTitle(DlcFxI18n.tr("dlc_manager.window_title"));
        if (remoteConnection != null) {
            remoteConnection.refreshLanguage();
        }
        localDlcManagementTab = null;
        localRemoteDownloadTab = null;
        localRequiredTab = null;
        localUpdateTab = null;
        homeView = buildHomeView();
        localView = buildLocalWorkspace();
        remoteView = buildRemoteWorkspace();
        if (localGameDirSelected) {
            onLocalGameDirChanged();
        }
        shell.setBottom(statusBar());
        switch (target) {
            case LOCAL -> showLocalWorkspace();
            case REMOTE -> showRemoteWorkspace();
            case HOME -> showHome();
        }
    }

    private enum ViewMode {
        HOME,
        LOCAL,
        REMOTE
    }

    private record LanguageOption(String code, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
