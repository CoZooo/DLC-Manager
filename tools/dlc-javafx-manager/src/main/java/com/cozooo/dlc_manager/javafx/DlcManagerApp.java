package com.cozooo.dlc_manager.javafx;

import com.cozooo.dlc_manager.platform.PlatformServices;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
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
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ── Local tabs ──────────────────────────────────────────────
        tabPane.getTabs().add(localDlcManagementTab());
        tabPane.getTabs().add(localRemoteDownloadTab());
        tabPane.getTabs().add(localRequiredTab());
        tabPane.getTabs().add(localPresetTab());
        tabPane.getTabs().add(localUpdateTab());

        // ── Remote tabs ─────────────────────────────────────────────
        remoteConnection = new RemoteConnection();
        tabPane.getTabs().add(new RemoteServerConnectionTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteDlcManagementTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteRemoteRequiredTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteHotUpdateTab(remoteConnection).build());
        tabPane.getTabs().add(new RemotePackTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteRconTab(remoteConnection).build());
        tabPane.getTabs().add(new RemoteFileManagerTab(remoteConnection, primaryStage).build());
        tabPane.getTabs().add(new RemoteServerConfigTab(remoteConnection).build());

        BorderPane root = new BorderPane(tabPane);
        root.setBottom(statusBar());

        Scene scene = new Scene(root, 1100, 720);
        primaryStage.setTitle("DLC Manager — JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
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
        Label localStatus = new Label("就绪 — 请在「本地 · DLC 管理」中选择游戏目录");
        Label remoteStatus = new Label();
        remoteStatus.textProperty().bind(
                remoteConnection != null ? remoteConnection.statusTextProperty() : new javafx.beans.property.SimpleStringProperty(""));

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(8, localStatus, remoteStatus);
        javafx.scene.layout.HBox.setHgrow(localStatus, javafx.scene.layout.Priority.ALWAYS);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
