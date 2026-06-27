package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.platform.PlatformServices;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX desktop DLC Manager entry point.
 * <p>
 * Functionality is parity with the in-game client DLC manager, supporting both
 * <b>local</b> mode (operating on a user-selected game directory) and
 * <b>remote</b> mode (connecting to a server running the DLC Manager mod via
 * the .dc/TCP protocol on port 29690).
 * <p>
 * All validation logic, the encrypted CurseForge API, and the .dc/TCP protocol
 * are reused from {@code tools/dlc-external-controller} (pure Java) and ported
 * from {@code common} (Kotlin → Java, stripped of {@code net.minecraft} deps).
 */
public final class DlcManagerApp extends Application {

    /** Shared platform provider — the UI updates its game directory. */
    static final StandalonePlatformProvider PROVIDER = new StandalonePlatformProvider();

    /** Primary stage reference, used by tabs that need directory choosers. */
    private Stage primaryStage;

    /** Local DLC management tab — owns the game-directory chooser. */
    private LocalDlcManagementTab localDlcManagementTab;

    /** Local remote-download tab, notified when the game directory changes. */
    private LocalRemoteDownloadTab localRemoteDownloadTab;

    /** Local required tab, notified when the game directory changes. */
    private LocalRequiredTab localRequiredTab;

    /** Local hot-update tab, notified when the game directory changes. */
    private LocalUpdateTab localUpdateTab;

    /** Shared connection state for all remote-server tabs. */
    private RemoteConnection remoteConnection;

    /** Root shell that swaps between the home screen and each workspace. */
    private BorderPane shell;

    /** Top-level navigation surfaces. */
    private Node homeView;
    private Node localView;
    private Node remoteView;
    private ViewMode currentView = ViewMode.HOME;

    @Override
    public void init() {
        // Install the standalone provider BEFORE any common logic is touched.
        // Every class ported from common calls CrossPlatformRuntime.gameDir() /
        // configDir(), which delegates here.  This is the key architectural
        // seam that lets us reuse the engine without MC dependencies.
        PlatformServices.setProvider(PROVIDER);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        remoteConnection = new RemoteConnection();
        shell = new BorderPane();
        shell.setBottom(statusBar());
        DlcFxStyle.add(shell, "dlc-app-root");

        homeView = buildHomeView();
        localView = buildLocalWorkspace();
        remoteView = buildRemoteWorkspace();
        showHome();

        Scene scene = new Scene(shell, 1100, 720);
        java.net.URL css = DlcManagerApp.class.getResource("theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        primaryStage.setTitle(DlcFxI18n.tr("dlc_manager.javafx.window_title"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Node buildHomeView() {
        VBox root = new VBox(22);
        root.setPadding(new Insets(28, 28, 30, 28));
        DlcFxStyle.view(root);

        Label title = new Label(DlcFxI18n.tr("dlc_manager.javafx.home.title"));
        Label subtitle = new Label(DlcFxI18n.tr("dlc_manager.javafx.home.subtitle"));
        DlcFxStyle.add(title, "dlc-hero-title");
        DlcFxStyle.muted(subtitle);

        Node localChoice = modeChoice(
                DlcFxI18n.tr("dlc_manager.javafx.home.local_title"),
                DlcFxI18n.tr("dlc_manager.javafx.home.local_desc"),
                DlcFxI18n.tr("dlc_manager.javafx.home.enter_local"),
                this::showLocalWorkspace);
        Node remoteChoice = modeChoice(
                DlcFxI18n.tr("dlc_manager.javafx.home.remote_title"),
                DlcFxI18n.tr("dlc_manager.javafx.home.remote_desc"),
                DlcFxI18n.tr("dlc_manager.javafx.home.enter_remote"),
                this::showRemoteWorkspace);
        HBox choices = new HBox(16, localChoice, remoteChoice);
        HBox.setHgrow(localChoice, Priority.ALWAYS);
        HBox.setHgrow(remoteChoice, Priority.ALWAYS);

        VBox content = new VBox(10, title, subtitle, choices);
        VBox.setVgrow(choices, Priority.ALWAYS);
        content.setAlignment(Pos.TOP_LEFT);
        root.getChildren().add(content);
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
        Button back = new Button(DlcFxI18n.tr("dlc_manager.javafx.workspace.back"));
        DlcFxStyle.primary(back);
        back.setOnAction(e -> backAction.run());

        Label title = new Label(titleText);
        Label desc = new Label(descText);
        DlcFxStyle.add(title, "dlc-workspace-title");
        DlcFxStyle.muted(desc);

        VBox text = new VBox(4, title, desc);
        HBox header = new HBox(12, back, text);
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
        tabPane.getTabs().add(new RemoteServerConnectionTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteDlcManagementTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteRemoteRequiredTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteHotUpdateTab(remoteConnection).build());
        tabPane.getTabs().add(new RemotePackTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteRconTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteFileManagerTab(remoteConnection, primaryStage).build());
        tabPane.getTabs().add(new RemoteServerConfigTab(remoteConnection).build());
        return tabPane;
    }

    private Node buildLocalWorkspace() {
        BorderPane root = new BorderPane();
        root.setTop(workspaceHeader(
                DlcFxI18n.tr("dlc_manager.javafx.workspace.local_title"),
                DlcFxI18n.tr("dlc_manager.javafx.workspace.local_desc"),
                this::showHome));
        root.setCenter(buildLocalTabs());
        DlcFxStyle.add(root, "dlc-workspace-root");
        return root;
    }

    private Node buildRemoteWorkspace() {
        BorderPane root = new BorderPane();
        root.setTop(workspaceHeader(
                DlcFxI18n.tr("dlc_manager.javafx.workspace.remote_title"),
                DlcFxI18n.tr("dlc_manager.javafx.workspace.remote_desc"),
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

    // ───────────────────────────────────────────────────────────────
    //  Local tabs
    // ───────────────────────────────────────────────────────────────

    /** [本地] DLC 管理 — directory chooser + scan + list + enable/disable/apply */
    private Tab localDlcManagementTab() {
        localDlcManagementTab = new LocalDlcManagementTab(PROVIDER);
        // When the user picks a new game directory, notify the other local tabs
        // so they drop stale state and prompt for a refresh.
        localDlcManagementTab.setOnGameDirChanged(this::onLocalGameDirChanged);
        return localDlcManagementTab.build(primaryStage);
    }

    /** Forwards a game-directory change to the sibling local tabs. */
    private void onLocalGameDirChanged() {
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

    /** [本地] Remote 远程下载 — CurseForge/Modrinth/Direct + 缺失检测 + 校验 */
    private Tab localRemoteDownloadTab() {
        localRemoteDownloadTab = new LocalRemoteDownloadTab(PROVIDER);
        return localRemoteDownloadTab.build();
    }

    /** [本地] Required 必装资源 — 本地缺失检测 + 下载 + 安装到目标目录 */
    private Tab localRequiredTab() {
        localRequiredTab = new LocalRequiredTab(PROVIDER);
        return localRequiredTab.build();
    }

    /** [本地] Preset 预设 — 切换一组 DLC 状态 */
    private Tab localPresetTab() {
        return new LocalPresetTab(PROVIDER).build();
    }

    /** [本地] 热更新 — 检测本地热更新包 + 同步远程仓库（Layer A + Layer B） */
    private Tab localUpdateTab() {
        localUpdateTab = new LocalUpdateTab(PROVIDER);
        return localUpdateTab.build();
    }

    // ───────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────

    private javafx.scene.Node statusBar() {
        // Right side mirrors the remote connection state (live-bound), so the
        // user can tell at a glance whether TCP ops will reach the server.
        Label localStatus = new Label(DlcFxI18n.tr("dlc_manager.javafx.status.ready_select_game_dir"));
        Label remoteStatus = new Label();
        remoteStatus.textProperty().bind(
                remoteConnection != null ? remoteConnection.statusTextProperty() : new javafx.beans.property.SimpleStringProperty(""));

        Label languageLabel = new Label(DlcFxI18n.tr("dlc_manager.javafx.language.label"));
        ComboBox<LanguageOption> languageBox = new ComboBox<>();
        languageBox.getItems().addAll(
                new LanguageOption("en_us", DlcFxI18n.tr("dlc_manager.javafx.language.en_us")),
                new LanguageOption("zh_cn", DlcFxI18n.tr("dlc_manager.javafx.language.zh_cn")));
        languageBox.setValue(optionForCurrentLanguage(languageBox));
        languageBox.setOnAction(event -> {
            LanguageOption option = languageBox.getValue();
            if (option != null && !option.code().equals(PROVIDER.languageOverride())) {
                PROVIDER.setLanguageOverride(option.code());
                rebuildForLanguageChange();
            }
        });

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(8, localStatus, remoteStatus, languageLabel, languageBox);
        javafx.scene.layout.HBox.setHgrow(localStatus, javafx.scene.layout.Priority.ALWAYS);
        languageBox.setPrefWidth(150);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.add(bar, "dlc-status-root");
        DlcFxStyle.muted(localStatus);
        DlcFxStyle.muted(remoteStatus);
        DlcFxStyle.muted(languageLabel);
        return bar;
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
        primaryStage.setTitle(DlcFxI18n.tr("dlc_manager.javafx.window_title"));
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
