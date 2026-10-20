package com.cozooo.dlc_manager.author;

import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.layout.BorderPane;

import java.util.function.Supplier;

public final class DlcFxAuthorToolsPage {
    private final Supplier<Scene> scene;
    private WebEngine engine;
    private Node builtView;

    public DlcFxAuthorToolsPage(Supplier<Scene> scene) {
        this.scene = scene;
    }

    public Node build() {
        if (builtView != null) {
            return builtView;
        }
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.setMinSize(0, 0);
        webView.setPrefSize(0, 0);
        webView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        engine = webView.getEngine();
        DlcFxDcEditorLauncher.installBridge(engine, scene, DlcPlatformServices.languageCode());
        String index = DlcFxDcEditorLauncher.indexUrl(DlcPlatformServices.languageCode());
        if (index == null) {
            engine.loadContent("<html><body><h2>DC Editor</h2><p>DC Editor frontend is not included in this build.</p></body></html>");
        } else {
            engine.load(index);
        }
        BorderPane root = new BorderPane(webView);
        root.setPadding(new Insets(0));
        root.setMinSize(0, 0);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        DlcFxStyle.add(root, "dlc-workspace-root");
        builtView = root;
        return builtView;
    }

    public void setLocale(String locale) {
        if (engine != null) {
            DlcFxDcEditorLauncher.setLocale(engine, locale);
        }
    }
}
