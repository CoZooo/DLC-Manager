package com.cozooo.dlc_manager.app;

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.local.DlcFxLocalDlcManagementTab;
import com.cozooo.dlc_manager.local.DlcFxLocalConfigOverlay;
import com.cozooo.dlc_manager.local.DlcFxLocalPackTab;
import com.cozooo.dlc_manager.local.DlcFxLocalPresetTab;
import com.cozooo.dlc_manager.local.DlcFxLocalRemoteDownloadTab;
import com.cozooo.dlc_manager.local.DlcFxLocalRequiredTab;
import com.cozooo.dlc_manager.local.DlcFxLocalUpdateTab;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.platform.DlcFxPlatformProvider;
import com.cozooo.dlc_manager.ui.DlcFxModalOverlay;
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
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

public final class DlcFxManagerApp extends Application {

    static final DlcFxPlatformProvider PROVIDER = new DlcFxPlatformProvider();
    private static DlcFxManagerApp INSTANCE;

    private Stage primaryStage;
    private DlcFxLocalDlcManagementTab localDlcManagementTab;
    private DlcFxLocalRemoteDownloadTab localRemoteDownloadTab;
    private DlcFxLocalRequiredTab localRequiredTabPage;
    private DlcFxLocalPackTab localPackTab;
    private DlcFxLocalPresetTab localPresetTabPage;
    private DlcFxLocalUpdateTab localUpdateTab;

    private Label localGameDirLabel;
    private Label remoteGameDirLabel;
    private Button localConfigButton;
    private Button remoteReadLocalButton;
    private boolean localGameDirSelected;

    private DlcFxRemoteConnection remoteConnection;
    private DlcFxRemoteServerConnectionTab remoteServerConnectionTab;
    private StackPane shell;
    private BorderPane shellContent;
    private Node homeView;
    private Node localView;
    private Node remoteView;
    private ViewMode currentView = ViewMode.HOME;
    private TabPane localTabPane;
    private Tab localRequiredTabNode;
    private Tab localDlcTabNode;
    private Tab localRemoteTabNode;
    private Tab localPresetTabNode;
    private Tab localPackTabNode;
    private Tab localUpdateTabNode;
    private boolean suppressLocalTabChangePrompt;
    private final Set<String> localPresetSnapshot = new LinkedHashSet<>();
    private final Set<String> localPresetOwnedDlcIds = new LinkedHashSet<>();
    private boolean localPresetSnapshotCaptured;

    @Override
    public void init() {
        PlatformServices.setProvider(PROVIDER);
    }

    @Override
    public void start(Stage primaryStage) {
        INSTANCE = this;
        loadBundledFonts();
        this.primaryStage = primaryStage;
        remoteConnection = new DlcFxRemoteConnection();
        initializeCurrentDirectoryGameDir();
        shellContent = new BorderPane();
        shellContent.setBottom(statusBar());
        DlcFxStyle.add(shellContent, "dlc-app-root");
        shell = new StackPane(shellContent);
        DlcFxStyle.add(shell, "dlc-app-shell");

        homeView = buildHomeView();
        localView = buildLocalWorkspace();
        remoteView = buildRemoteWorkspace();
        showHome();

        Scene scene = new Scene(shell, 1100, 720);
        URL css = DlcFxManagerApp.class.getResource("/javafx/theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        installGameDirectoryDropTarget(scene);
        installApplicationIcon(primaryStage);
        primaryStage.setTitle(DlcFxI18n.tr("dlc_manager.window_title"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void loadBundledFonts() {
        loadBundledFont("/javafx/fonts/NotoSansSC-Regular.ttf");
        loadBundledFont("/javafx/fonts/NotoSansSC-SemiBold.ttf");
        loadBundledFont("/javafx/fonts/JetbrainsMono-Regular.ttf");
    }

    private void loadBundledFont(String path) {
        try (InputStream font = DlcFxManagerApp.class.getResourceAsStream(path)) {
            if (font != null) {
                Font.loadFont(font, 13);
            }
        } catch (IOException ignored) {
        }
    }

    private void installApplicationIcon(Stage primaryStage) {
        URL icon = DlcFxManagerApp.class.getResource("/assets/dlc_manager/textures/gui/icon.png");
        if (icon != null) {
            primaryStage.getIcons().add(new Image(icon.toExternalForm()));
            installTaskbarIcon(icon);
        }
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
        DlcFxStyle.add(title, "dlc-hero-title");
        Label subtitle = new Label(DlcFxI18n.tr("dlc_manager.home.subtitle"));
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
        VBox choices = new VBox(16, localChoice, remoteChoice);
        VBox.setVgrow(localChoice, Priority.ALWAYS);
        VBox.setVgrow(remoteChoice, Priority.ALWAYS);

        VBox content = new VBox(10, title, subtitle, choices);
        VBox.setVgrow(choices, Priority.ALWAYS);
        content.setAlignment(Pos.TOP_LEFT);
        root.getChildren().addAll(topBar, content);
        return root;
    }

    private Node modeChoice(String titleText, String descText, String actionText, Runnable action) {
        HBox box = new HBox(12);
        box.getStyleClass().add("dlc-mode-panel");
        box.setPadding(new Insets(18, 18, 18, 18));
        box.setMinHeight(160);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setAlignment(Pos.CENTER_LEFT);

        VBox textBox = new VBox(8);
        textBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(titleText);
        Label desc = new Label(descText);
        DlcFxStyle.add(title, "dlc-mode-title");
        DlcFxStyle.muted(desc);
        desc.setWrapText(false);
        desc.setTextOverrun(OverrunStyle.CLIP);
        desc.setMaxWidth(Double.MAX_VALUE);
        textBox.getChildren().addAll(title, desc);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Button enter = new Button(actionText);
        DlcFxStyle.primary(enter);
        enter.setMinHeight(64);
        enter.setPrefHeight(64);
        enter.setMaxHeight(64);
        enter.setMinWidth(180);
        enter.setPrefWidth(180);
        enter.setMaxWidth(180);
        enter.setOnAction(e -> action.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        box.getChildren().addAll(textBox, spacer, enter);
        return box;
    }

    private Node workspaceHeader(String titleText, Runnable backAction) {
        return workspaceHeader(titleText, backAction, null);
    }

    private Node workspaceHeader(String titleText, Runnable backAction, Node extraRight) {
        Button back = new Button(DlcFxI18n.tr("dlc_manager.workspace.back"));
        DlcFxStyle.primary(back);
        back.setOnAction(e -> backAction.run());

        Label title = new Label(titleText);
        DlcFxStyle.add(title, "dlc-workspace-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, back, title, spacer);
        if (extraRight != null) {
            header.getChildren().add(extraRight);
        }
        header.getChildren().add(languagePicker());
        header.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.toolbar(header);
        return header;
    }

    private TabPane buildLocalTabs() {
        localTabPane = new TabPane();
        localTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        localDlcTabNode = localDlcManagementTab();
        localTabPane.getTabs().add(localDlcTabNode);
        localRemoteTabNode = localRemoteDownloadTab();
        localTabPane.getTabs().add(localRemoteTabNode);
        localRequiredTabNode = createLocalRequiredTab();
        localTabPane.getTabs().add(localRequiredTabNode);
        localPresetTabNode = localPresetTab();
        localTabPane.getTabs().add(localPresetTabNode);
        localPackTabNode = localPackTab();
        localTabPane.getTabs().add(localPackTabNode);
        localUpdateTabNode = localUpdateTab();
        localTabPane.getTabs().add(localUpdateTabNode);
        localTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> onLocalTabChanging(oldTab, newTab));
        return localTabPane;
    }

    private TabPane buildRemoteTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        remoteServerConnectionTab = new DlcFxRemoteServerConnectionTab(remoteConnection, primaryStage);
        tabPane.getTabs().add(remoteServerConnectionTab.build());
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
                        this::showHome),
                localDirectoryBar());
        root.setTop(top);
        root.setCenter(buildLocalTabs());
        DlcFxStyle.add(root, "dlc-workspace-root");
        return root;
    }

    private Node buildRemoteWorkspace() {
        BorderPane root = new BorderPane();
        VBox top = new VBox(
                workspaceHeader(
                        DlcFxI18n.tr("dlc_manager.workspace.remote_title"),
                        this::showHome,
                        remoteConnectionStatusBadge()),
                remoteLocalContextBar());
        root.setTop(top);
        root.setCenter(buildRemoteTabs());
        DlcFxStyle.add(root, "dlc-workspace-root");
        return root;
    }

    private void showHome() {
        currentView = ViewMode.HOME;
        shellContent.setCenter(homeView);
        refreshStatusForCurrentView();
    }

    private void showLocalWorkspace() {
        currentView = ViewMode.LOCAL;
        shellContent.setCenter(localView);
        refreshStatusForCurrentView();
        onLocalTabSelected(localTabPane == null ? null : localTabPane.getSelectionModel().getSelectedItem());
        promptMissingRequiredResources();
    }

    private void showRemoteWorkspace() {
        currentView = ViewMode.REMOTE;
        shellContent.setCenter(remoteView);
        refreshStatusForCurrentView();
    }

    private Tab localDlcManagementTab() {
        localDlcManagementTab = new DlcFxLocalDlcManagementTab(PROVIDER);
        localDlcManagementTab.setGameDirSelected(localGameDirSelected);
        localDlcManagementTab.setPresetSnapshotActions(
            this::captureLocalPresetSnapshot,
            this::restoreLocalPresetSnapshot,
            this::resetLocalPresetSnapshot,
            this::applyLocalPresetOwnedDlcOverrides
        );
        return localDlcManagementTab.build();
    }

    private void onLocalGameDirChanged() {
        clearLocalPresetTracking();
        if (localDlcManagementTab != null) {
            localDlcManagementTab.onGameDirChanged();
        }
        if (localRemoteDownloadTab != null) {
            localRemoteDownloadTab.onGameDirChanged();
        }
        if (localRequiredTabPage != null) {
            localRequiredTabPage.onGameDirChanged();
        }
        if (localPresetTabPage != null) {
            localPresetTabPage.onGameDirChanged();
        }
        if (localUpdateTab != null) {
            localUpdateTab.onGameDirChanged();
        }
        if (localPackTab != null) {
            localPackTab.onGameDirChanged();
        }
        if (currentView == ViewMode.LOCAL) {
            onLocalTabSelected(localTabPane == null ? null : localTabPane.getSelectionModel().getSelectedItem());
        }
        refreshStatusForCurrentView();
    }

    private Tab localRemoteDownloadTab() {
        localRemoteDownloadTab = new DlcFxLocalRemoteDownloadTab(PROVIDER);
        return localRemoteDownloadTab.build();
    }

    private Tab createLocalRequiredTab() {
        localRequiredTabPage = new DlcFxLocalRequiredTab(PROVIDER);
        return localRequiredTabPage.build();
    }

    private Tab localPresetTab() {
        localPresetTabPage = new DlcFxLocalPresetTab(PROVIDER);
        localPresetTabPage.setPresetStateActions(
            this::captureLocalPresetSnapshot,
            this::snapshotLocalDlcEffectiveState,
            this::syncLocalPresetOwnedDlcChanges
        );
        return localPresetTabPage.build();
    }

    private void captureLocalPresetSnapshot() {
        if (localPresetSnapshotCaptured || !DlcConfig.isPresetFuncOn()) {
            return;
        }
        localPresetSnapshot.clear();
        localPresetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds());
        localPresetSnapshotCaptured = true;
    }

    private void restoreLocalPresetSnapshot() {
        if (!DlcConfig.isPresetFuncOn()) {
            return;
        }
        if (!localPresetSnapshotCaptured) {
            captureLocalPresetSnapshot();
        }
        DlcPresetRuntimeState.restoreEnabledPresets(localPresetSnapshot);
        localPresetOwnedDlcIds.clear();
    }

    private void resetLocalPresetSnapshot() {
        localPresetSnapshot.clear();
        localPresetSnapshotCaptured = false;
    }

    private void clearLocalPresetTracking() {
        resetLocalPresetSnapshot();
        localPresetOwnedDlcIds.clear();
    }

    Map<String, Boolean> snapshotLocalDlcEffectiveState() {
        Map<String, Boolean> state = new LinkedHashMap<>();
        for (DlcItem item : currentLocalDlcItems()) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            state.put(item.getId(), override != null ? override : item.isEnabled());
        }
        return state;
    }

    boolean syncLocalPresetOwnedDlcChanges(Map<String, Boolean> beforeState) {
        if (beforeState == null) {
            beforeState = Map.of();
        }
        Set<String> desiredIdentifiers = currentLocalPresetDesiredDlcIdentifiers();
        boolean pending = false;
        for (DlcItem item : currentLocalDlcItems()) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
            boolean before = beforeState.getOrDefault(item.getId(), item.isEnabled());
            if (!before && item.isEnabled() && desiredIdentifiers.contains(item.getIdentifier())) {
                localPresetOwnedDlcIds.add(item.getId());
            }
        }
        applyLocalPresetOwnedDlcOverrides();
        for (DlcItem item : currentLocalDlcItems()) {
            if (item != null && DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                pending = true;
            }
        }
        return pending;
    }

    void applyLocalPresetOwnedDlcOverrides() {
        if (localPresetOwnedDlcIds.isEmpty()) {
            return;
        }
        Set<String> desiredIdentifiers = currentLocalPresetDesiredDlcIdentifiers();
        for (DlcItem item : currentLocalDlcItems()) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            if (!localPresetOwnedDlcIds.contains(item.getId())) {
                continue;
            }
            if (desiredIdentifiers.contains(item.getIdentifier())) {
                continue;
            }
            DlcRuntimeState.setOverride(item.getId(), false);
            item.setEnabled(false);
        }
        localPresetOwnedDlcIds.removeIf(id -> {
            DlcItem item = indexedItemById(id);
            return item == null || !desiredIdentifiers.contains(item.getIdentifier());
        });
    }

    void rememberLocalPresetOwnedDlcForTest(String id) {
        if (id != null && !id.isBlank()) {
            localPresetOwnedDlcIds.add(id);
        }
    }

    boolean hasLocalPresetOwnedDlcForTest(String id) {
        return id != null && localPresetOwnedDlcIds.contains(id);
    }

    void restoreLocalPresetSnapshotForTest() {
        restoreLocalPresetSnapshot();
    }

    private Set<String> currentLocalPresetDesiredDlcIdentifiers() {
        Set<String> identifiers = new LinkedHashSet<>();
        for (String presetId : DlcPresetRuntimeState.getEnabledPresetIds()) {
            identifiers.addAll(DlcPresetRuntimeState.resolveEffectiveEnableIdentifiers(presetId));
        }
        return identifiers;
    }

    private DlcItem indexedItemById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (DlcItem item : currentLocalDlcItems()) {
            if (item != null && id.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    private List<DlcItem> currentLocalDlcItems() {
        if (localDlcManagementTab != null) {
            List<DlcItem> items = localDlcManagementTab.currentItemsSnapshot();
            if (!items.isEmpty()) {
                return items;
            }
        }
        return List.copyOf(DlcRuntimeState.getIndexedItems());
    }

    private Tab localPackTab() {
        localPackTab = new DlcFxLocalPackTab(PROVIDER);
        return localPackTab.build();
    }

    private Tab localUpdateTab() {
        localUpdateTab = new DlcFxLocalUpdateTab(PROVIDER);
        return localUpdateTab.build();
    }

    private void onLocalTabSelected(Tab selected) {
        if (!localGameDirSelected || selected == null) {
            return;
        }
        if (selected == localDlcTabNode && localDlcManagementTab != null) {
            localDlcManagementTab.onTabSelected();
        } else if (selected == localRemoteTabNode && localRemoteDownloadTab != null) {
            localRemoteDownloadTab.onTabSelected();
        } else if (selected == localRequiredTabNode && localRequiredTabPage != null) {
            localRequiredTabPage.onTabSelected();
        } else if (selected == localPresetTabNode && localPresetTabPage != null) {
            localPresetTabPage.onTabSelected();
        } else if (selected == localPackTabNode && localPackTab != null) {
            localPackTab.onTabSelected();
        } else if (selected == localUpdateTabNode && localUpdateTab != null) {
            localUpdateTab.onTabSelected();
        }
    }

    private void onLocalTabChanging(Tab oldTab, Tab newTab) {
        if (suppressLocalTabChangePrompt || oldTab == null || newTab == null || oldTab == newTab) {
            onLocalTabSelected(newTab);
            return;
        }
        if (!localTabHasPendingChanges(oldTab)) {
            onLocalTabSelected(newTab);
            return;
        }
        suppressLocalTabChangePrompt = true;
        localTabPane.getSelectionModel().select(oldTab);
        suppressLocalTabChangePrompt = false;
        showPendingLocalTabPrompt(oldTab, newTab);
    }

    private boolean localTabHasPendingChanges(Tab tab) {
        if (tab == localDlcTabNode && localDlcManagementTab != null) {
            return localDlcManagementTab.hasPendingChanges();
        }
        if (tab == localPresetTabNode && localPresetTabPage != null) {
            return localPresetTabPage.hasPendingChanges();
        }
        return false;
    }

    private void applyPendingLocalTab(Tab tab) {
        if (tab == localDlcTabNode && localDlcManagementTab != null) {
            localDlcManagementTab.applyPendingChanges();
        } else if (tab == localPresetTabNode && localPresetTabPage != null) {
            localPresetTabPage.applyPendingChanges();
        }
    }

    private void cancelPendingLocalTab(Tab tab) {
        if (tab == localDlcTabNode && localDlcManagementTab != null) {
            localDlcManagementTab.cancelPendingChanges();
        } else if (tab == localPresetTabNode && localPresetTabPage != null) {
            localPresetTabPage.cancelPendingChanges();
        }
    }

    private void showPendingLocalTabPrompt(Tab sourceTab, Tab targetTab) {
        if (DlcFxModalOverlay.isShowing(primaryStage == null ? null : primaryStage.getScene())) {
            return;
        }
        DlcFxModalOverlay.show(
                primaryStage == null ? null : primaryStage.getScene(),
                DlcFxI18n.tr("dlc_manager.pending.unapplied_title"),
                DlcFxI18n.tr("dlc_manager.pending.unapplied_message"),
                () -> {
                    applyPendingLocalTab(sourceTab);
                    selectLocalTabWithoutPrompt(targetTab);
                },
                DlcFxI18n.tr("dlc_manager.local.apply_changes"),
                () -> {
                    cancelPendingLocalTab(sourceTab);
                    selectLocalTabWithoutPrompt(targetTab);
                },
                DlcFxI18n.tr("dlc_manager.local.cancel_changes")
        );
    }

    private void selectLocalTabWithoutPrompt(Tab tab) {
        if (localTabPane == null || tab == null) {
            return;
        }
        suppressLocalTabChangePrompt = true;
        localTabPane.getSelectionModel().select(tab);
        suppressLocalTabChangePrompt = false;
        onLocalTabSelected(tab);
    }

    private Node localDirectoryBar() {
        localConfigButton = new Button(DlcFxI18n.tr("dlc_manager.local.config"));
        localConfigButton.setDisable(!localGameDirSelected);
        localConfigButton.setOnAction(e -> showLocalConfigOverlay());

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
                localConfigButton,
                chooseDirBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.filterBar(bar);
        DlcFxStyle.add(bar, "dlc-local-context-bar");
        return bar;
    }

    private void showLocalConfigOverlay() {
        if (!localGameDirSelected) {
            refreshStatusForCurrentView();
            return;
        }
        new DlcFxLocalConfigOverlay(scene(), () -> {
            onLocalGameDirChanged();
            if (remoteServerConnectionTab != null) {
                remoteServerConnectionTab.onStorageContextChanged();
            }
        }).show();
    }

    private Node remoteLocalContextBar() {
        Button chooseDirBtn = new Button(DlcFxI18n.tr("dlc_manager.local.choose_game_dir"));
        chooseDirBtn.setOnAction(e -> chooseLocalGameDirectory());
        DlcFxStyle.primary(chooseDirBtn);

        remoteReadLocalButton = new Button(DlcFxI18n.tr("dlc_manager.remote.read_local_files"));
        remoteReadLocalButton.setOnAction(e -> scanLocalFilesFromRemoteWorkspace());
        remoteReadLocalButton.setDisable(!localGameDirSelected);

        remoteGameDirLabel = new Label(localGameDirSelected
                ? PROVIDER.gameDir().toString()
                : DlcFxI18n.tr("dlc_manager.local.no_game_dir"));
        DlcFxStyle.muted(remoteGameDirLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10,
                new Label(DlcFxI18n.tr("dlc_manager.remote.local_source")),
                remoteGameDirLabel,
                spacer,
                chooseDirBtn,
                remoteReadLocalButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.filterBar(bar);
        DlcFxStyle.add(bar, "dlc-local-context-bar");
        return bar;
    }

    private Node remoteConnectionStatusBadge() {
        Label badge = new Label();
        badge.textProperty().bind(remoteConnection.statusTextProperty());
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(remoteConnection.statusTextProperty());
        badge.setTooltip(tooltip);
        updateRemoteConnectionBadgeStyle(badge, remoteConnection.connectionStateProperty().get());
        remoteConnection.connectionStateProperty().addListener((obs, oldValue, newValue) ->
                updateRemoteConnectionBadgeStyle(badge, newValue));
        return badge;
    }

    private void updateRemoteConnectionBadgeStyle(Label badge, DlcFxRemoteConnection.ConnectionState state) {
        if (badge == null) {
            return;
        }
        badge.getStyleClass().removeAll(
                DlcFxStyle.CHIP,
                DlcFxStyle.CHIP_OK,
                DlcFxStyle.CHIP_WARN,
                DlcFxStyle.CHIP_ERROR,
                DlcFxStyle.CHIP_NEUTRAL,
                "dlc-remote-connection-badge");
        String chipState = switch (state == null ? DlcFxRemoteConnection.ConnectionState.DISCONNECTED : state) {
            case CONNECTED -> DlcFxStyle.CHIP_OK;
            case CONNECTING -> DlcFxStyle.CHIP_WARN;
            case FAILED -> DlcFxStyle.CHIP_ERROR;
            case DISCONNECTED -> DlcFxStyle.CHIP_NEUTRAL;
        };
        DlcFxStyle.chip(badge, chipState);
        DlcFxStyle.add(badge, "dlc-remote-connection-badge");
    }

    private void scanLocalFilesFromRemoteWorkspace() {
        if (!localGameDirSelected) {
            refreshStatusForCurrentView();
            return;
        }
        if (localDlcManagementTab == null) {
            localDlcManagementTab();
        }
        if (localDlcManagementTab != null) {
            localDlcManagementTab.setGameDirSelected(true);
            localDlcManagementTab.scanSelectedGameDir();
            DlcFxStyle.setStatus(DlcFxI18n.tr("dlc_manager.remote.read_local_started"));
        }
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

        setLocalGameDirectory(chosen.toPath(), true, true);
    }

    private void initializeCurrentDirectoryGameDir() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (isGameDirectory(current)) {
            PROVIDER.setGameDir(current);
            localGameDirSelected = true;
            DlcConfig.load();
            DlcStateConfig.load();
            DlcRuntimeState.clearOverrides();
            DlcPresetRuntimeState.clearAll();
        }
    }

    private void installGameDirectoryDropTarget(Scene scene) {
        scene.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (firstDraggedDirectory(dragboard) != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            Path gameDir = firstDraggedDirectory(dragboard);
            boolean success = false;
            if (gameDir != null) {
                setLocalGameDirectory(gameDir, true, true);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private Path firstDraggedDirectory(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasFiles()) {
            return null;
        }
        for (File file : dragboard.getFiles()) {
            if (file == null) {
                continue;
            }
            Path path = file.toPath().toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    private boolean isGameDirectory(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        return Files.isDirectory(path.resolve("mods"))
            || Files.isDirectory(path.resolve("config"))
            || Files.isRegularFile(path.resolve("options.txt"));
    }

    private void setLocalGameDirectory(Path gameDir, boolean promptMissingRequired, boolean showInvalidDialog) {
        if (gameDir == null) {
            return;
        }
        gameDir = gameDir.toAbsolutePath().normalize();
        if (!isGameDirectory(gameDir)) {
            if (showInvalidDialog) {
                showInvalidGameDirectoryDialog(gameDir);
            }
            return;
        }
        PROVIDER.setGameDir(gameDir);
        localGameDirSelected = true;
        if (localGameDirLabel != null) {
            localGameDirLabel.setText(gameDir.toString());
        }
        if (remoteGameDirLabel != null) {
            remoteGameDirLabel.setText(gameDir.toString());
        }
        if (remoteReadLocalButton != null) {
            remoteReadLocalButton.setDisable(false);
        }
        if (localConfigButton != null) {
            localConfigButton.setDisable(false);
        }

        DlcConfig.load();
        DlcStateConfig.load();
        DlcRuntimeState.clearOverrides();
        DlcPresetRuntimeState.clearAll();
        onLocalGameDirChanged();
        if (remoteServerConnectionTab != null) {
            remoteServerConnectionTab.onStorageContextChanged();
        }
        if (promptMissingRequired) {
            promptMissingRequiredResources();
        }
    }

    private void showInvalidGameDirectoryDialog(Path path) {
        DlcFxModalOverlay.show(
                scene(),
                DlcFxI18n.tr("dlc_manager.local.invalid_game_dir_title"),
                DlcFxI18n.tr("dlc_manager.local.invalid_game_dir", path),
                () -> {},
                DlcFxI18n.tr("dlc_manager.confirm_yes"));
    }

    private javafx.scene.Node statusBar() {
        refreshStatusForCurrentView();
        Label localStatus = new Label();
        localStatus.textProperty().bind(DlcFxStyle.statusTextProperty());

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(8, localStatus);
        javafx.scene.layout.HBox.setHgrow(localStatus, javafx.scene.layout.Priority.ALWAYS);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        DlcFxStyle.add(bar, "dlc-status-root");
        DlcFxStyle.muted(localStatus);
        return bar;
    }

    private void refreshStatusForCurrentView() {
        if (currentView == ViewMode.LOCAL && !localGameDirSelected) {
            DlcFxStyle.setStatus(DlcFxI18n.tr("dlc_manager.status.select_game_dir"));
            return;
        }
        DlcFxStyle.setStatus(DlcFxI18n.tr("dlc_manager.status.ready"));
    }

    private Node languagePicker() {
        ComboBox<LanguageOption> languageBox = new ComboBox<>();
        languageBox.getItems().addAll(
                new LanguageOption("en_us", DlcFxI18n.tr("dlc_manager.language.en_us")),
                new LanguageOption("zh_cn", DlcFxI18n.tr("dlc_manager.language.zh_cn")));
        languageBox.setValue(optionForCurrentLanguage(languageBox));
        languageBox.setOnAction(event -> {
            LanguageOption option = languageBox.getValue();
            if (option != null && !option.code().equalsIgnoreCase(PROVIDER.languageCode())) {
                PROVIDER.setLanguageOverride(option.code());
                rebuildForLanguageChange();
            }
        });

        Label label = new Label(DlcFxI18n.tr("dlc_manager.language.label"));
        DlcFxStyle.muted(label);
        HBox picker = new HBox(8, label, languageBox);
        languageBox.setPrefWidth(150);
        picker.setAlignment(Pos.CENTER_RIGHT);
        DlcFxStyle.add(picker, "dlc-language-picker");
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
        localRequiredTabPage = null;
        localPresetTabPage = null;
        localDlcTabNode = null;
        localRemoteTabNode = null;
        localRequiredTabNode = null;
        localPresetTabNode = null;
        localPackTabNode = null;
        localUpdateTabNode = null;
        localPackTab = null;
        localUpdateTab = null;
        remoteServerConnectionTab = null;
        homeView = buildHomeView();
        localView = buildLocalWorkspace();
        remoteView = buildRemoteWorkspace();
        if (localGameDirSelected) {
            onLocalGameDirChanged();
        }
        shellContent.setBottom(statusBar());
        switch (target) {
            case LOCAL -> showLocalWorkspace();
            case REMOTE -> showRemoteWorkspace();
            case HOME -> showHome();
        }
    }

    public void showRequiredTabWithPrompt() {
        showLocalWorkspace();
        if (localTabPane != null && localRequiredTabNode != null) {
            selectLocalTabWithoutPrompt(localRequiredTabNode);
        }
    }

    public void showLocalDlcManagementTab() {
        showLocalWorkspace();
        if (localDlcTabNode != null) {
            selectLocalTabWithoutPrompt(localDlcTabNode);
        }
    }

    public static DlcFxManagerApp instance() {
        return INSTANCE;
    }

    private void promptMissingRequiredResources() {
        if (!localGameDirSelected || localRequiredTabPage == null) {
            return;
        }
        if (DlcFxModalOverlay.isShowing(scene())) {
            return;
        }
        List<DlcRequiredEntry> missing = DlcRequiredRuntime.collectMissingDownloadsForPrompt();
        if (missing.isEmpty()) {
            return;
        }
        DlcRequiredRuntime.markPrompted();
        String title = DlcFxI18n.tr("dlc_manager.required.missing_title");
        String message = DlcFxI18n.tr("dlc_manager.required.missing_prompt", missing.size());
        boolean forceOnly = DlcRequiredRuntime.hasForceMarker();
        if (forceOnly) {
            DlcFxModalOverlay.show(scene(), title, message, () -> {
                showRequiredTabWithPrompt();
                localRequiredTabPage.forceDownloadAndInstall(missing);
            }, DlcFxI18n.tr("dlc_manager.required.download_install"));
            return;
        }
        DlcFxModalOverlay.show(scene(), title, message, () -> {
            showRequiredTabWithPrompt();
            localRequiredTabPage.forceDownloadAndInstall(missing);
        }, DlcFxI18n.tr("dlc_manager.required.download_install"), () -> {
            DlcRequiredRuntime.markSkipped();
        }, DlcFxI18n.tr("dlc_manager.required.skip"));
    }

    private Scene scene() {
        return shell == null ? null : shell.getScene();
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
