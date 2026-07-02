package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.common.DlcFxTabFactory;
import com.cozooo.dlc_manager.ui.common.DlcFxTabPage;
import com.cozooo.dlc_manager.ui.common.DlcFxToolbar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

public final class DlcFxServerConnectionTab {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Preferences PREFS = Preferences.userNodeForPackage(DlcFxServerConnectionTab.class);
    private static final String PREF_NAME = "remote.server.name";
    private static final String PREF_HOST = "remote.server.host";
    private static final String PREF_PORT = "remote.server.port";
    private static final String PREF_PASSWORD = "remote.server.password";

    private final DlcFxServerConnection conn;
    private final Stage primaryStage;
    private final TextField nameField = new TextField();
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("29690");
    private final TextField passwordField = new TextField();
    private final Label infoLabel = new Label("");
    private final Label presetStatusLabel = new Label("");
    private final ComboBox<ServerPresetOption> presetBox = new ComboBox<>();
    private final ObservableList<ServerPresetOption> presetOptions = FXCollections.observableArrayList();
    private boolean applyingPreset;
    private boolean loadingConnectionState;

    public DlcFxServerConnectionTab(DlcFxServerConnection conn, Stage primaryStage) {
        this.conn = conn;
        this.primaryStage = primaryStage;
        nameField.setPrefColumnCount(14);
        hostField.setPrefColumnCount(18);
        portField.setPrefColumnCount(6);
        passwordField.setPrefColumnCount(20);
        infoLabel.textProperty().bind(conn.serverInfoTextProperty());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        hostField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        portField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        passwordField.textProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
        presetBox.valueProperty().addListener((obs, oldValue, newValue) -> saveConnectionState());
    }

    public Tab build() {
        DlcFxTabPage page = new DlcFxTabPage("");

        presetBox.setItems(presetOptions);
        presetBox.setPrefWidth(220);
        presetBox.setPromptText(DlcFxI18n.tr("dlc_manager.server_preset.select"));
        presetBox.setOnAction(e -> applySelectedPreset());
        loadPresets();
        loadConnectionState();

        Button connectBtn = new Button(DlcFxI18n.tr("dlc_manager.server.connect"));
        connectBtn.setOnAction(e -> connectCurrent());

        Button disconnectBtn = new Button(DlcFxI18n.tr("dlc_manager.server.disconnect"));
        disconnectBtn.disableProperty().bind(conn.connectedProperty().not());
        disconnectBtn.setOnAction(e -> conn.disconnect());
        DlcFxStyle.primary(connectBtn);

        Button savePresetBtn = new Button(DlcFxI18n.tr("dlc_manager.server_preset.save_current"));
        savePresetBtn.setOnAction(e -> saveCurrentPreset());

        Button deletePresetBtn = new Button(DlcFxI18n.tr("dlc_manager.server_preset.delete"));
        deletePresetBtn.setOnAction(e -> deleteSelectedPreset());
        DlcFxStyle.danger(deletePresetBtn);

        Button importPresetBtn = new Button(DlcFxI18n.tr("dlc_manager.server_preset.import"));
        importPresetBtn.setOnAction(e -> importPresets());

        Button exportPresetBtn = new Button(DlcFxI18n.tr("dlc_manager.server_preset.export"));
        exportPresetBtn.setOnAction(e -> exportPresets());

        HBox presetBoxRow = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.server_preset.label")),
                presetBox,
                savePresetBtn,
                deletePresetBtn,
                importPresetBtn,
                exportPresetBtn,
                presetStatusLabel);
        presetBoxRow.setAlignment(Pos.CENTER_LEFT);

        HBox formBox = new HBox(8,
                new Label(DlcFxI18n.tr("dlc_manager.server_preset.name")), nameField,
                new Label(DlcFxI18n.tr("dlc_manager.server.host")), hostField,
                new Label(DlcFxI18n.tr("dlc_manager.server.port")), portField,
                new Label(DlcFxI18n.tr("dlc_manager.server.password")), passwordField,
                connectBtn, disconnectBtn);
        formBox.setAlignment(Pos.CENTER_LEFT);

        page.setTop(DlcFxToolbar.vertical(presetBoxRow, formBox));

        Label infoTitle = new Label(DlcFxI18n.tr("dlc_manager.server.server_info"));
        DlcFxStyle.title(infoTitle);
        DlcFxStyle.muted(infoLabel);
        DlcFxStyle.muted(presetStatusLabel);
        VBox center = new VBox(8, infoTitle, infoLabel);
        page.setCenter(page.section(center));
        DlcFxStyle.routeStatus(infoLabel);
        DlcFxStyle.routeStatus(presetStatusLabel);

        return DlcFxTabFactory.create(DlcFxI18n.tr("dlc_manager.tab.connection"), page.root());
    }

    public void onStorageContextChanged() {
        loadPresets();
    }

    private void connectCurrent() {
        String host = hostField.getText().trim();
        int port = parsePort(portField.getText(), 29690);
        String password = passwordField.getText();
        saveConnectionState();
        conn.connectAsync(host, port, password, 10000, err -> {
            if (err == null) {
                DlcFxServerPresetStore.setLastSelectedKey(serverKey(host, port));
            }
        });
    }

    private void loadPresets() {
        refreshPresetOptions(DlcFxServerPresetStore.getLastSelectedKey());
    }

    private void refreshPresetOptions(String selectedKey) {
        applyingPreset = true;
        try {
            presetOptions.setAll(DlcFxServerPresetStore.getEntries().stream()
                    .map(ServerPresetOption::new)
                    .toList());
            ServerPresetOption selected = null;
            if (selectedKey != null && !selectedKey.isBlank()) {
                for (ServerPresetOption option : presetOptions) {
                    if (selectedKey.equals(option.entry().getKey())) {
                        selected = option;
                        break;
                    }
                }
            }
            presetBox.setValue(selected);
            if (selected != null) {
                fillFields(selected.entry());
            }
        } finally {
            applyingPreset = false;
        }
    }

    private void applySelectedPreset() {
        if (applyingPreset) {
            return;
        }
        ServerPresetOption option = presetBox.getValue();
        if (option == null) {
            return;
        }
        fillFields(option.entry());
        DlcFxServerPresetStore.setLastSelectedKey(option.entry().getKey());
        presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.loaded", option.entry().getDisplayName()));
        saveConnectionState();
    }

    private void fillFields(DlcServerEntry entry) {
        if (entry == null) {
            return;
        }
        nameField.setText(entry.name == null ? "" : entry.name);
        hostField.setText(entry.host == null || entry.host.isBlank() ? "127.0.0.1" : entry.host);
        portField.setText(Integer.toString(entry.port <= 0 ? 29690 : entry.port));
        passwordField.setText(entry.password == null ? "" : entry.password);
    }

    private void saveCurrentPreset() {
        DlcServerEntry entry = currentEntry();
        List<DlcServerEntry> merged = upsertByKey(DlcFxServerPresetStore.getEntries(), List.of(entry));
        DlcFxServerPresetStore.setEntries(merged);
        DlcFxServerPresetStore.setLastSelectedKey(entry.getKey());
        refreshPresetOptions(entry.getKey());
        presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.saved", entry.getDisplayName()));
        saveConnectionState();
    }

    private void deleteSelectedPreset() {
        ServerPresetOption option = presetBox.getValue();
        if (option == null) {
            presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.select_first"));
            return;
        }
        DlcFxServerPresetStore.removeEntry(option.entry().getKey());
        if (option.entry().getKey().equals(DlcFxServerPresetStore.getLastSelectedKey())) {
            DlcFxServerPresetStore.setLastSelectedKey(null);
        }
        refreshPresetOptions(null);
        presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.deleted", option.entry().getDisplayName()));
        saveConnectionState();
    }

    private void importPresets() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(DlcFxI18n.tr("dlc_manager.server_preset.import_title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DLC Config (*.dc)", "*.dc"));
        File file = chooser.showOpenDialog(primaryStage);
        if (file == null) {
            return;
        }
        try {
            ServerPresetData imported = readPresetData(file.toPath());
            List<DlcServerEntry> merged = upsertByKey(DlcFxServerPresetStore.getEntries(), imported.servers == null ? List.of() : imported.servers);
            DlcFxServerPresetStore.setEntries(merged);
            String selected = imported.lastSelected == null || imported.lastSelected.isBlank()
                    ? DlcFxServerPresetStore.getLastSelectedKey()
                    : imported.lastSelected;
            DlcFxServerPresetStore.setLastSelectedKey(selected);
            refreshPresetOptions(selected);
            presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.imported", imported.servers == null ? 0 : imported.servers.size()));
            saveConnectionState();
        } catch (Throwable t) {
            presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.import_failed", DlcFxServerConnection.safeMessage(t)));
        }
    }

    private void exportPresets() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(DlcFxI18n.tr("dlc_manager.server_preset.export_title"));
        chooser.setInitialFileName("servers.dc");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DLC Config (*.dc)", "*.dc"));
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) {
            return;
        }
        try {
            ServerPresetData data = new ServerPresetData();
            data.servers = new ArrayList<>(DlcFxServerPresetStore.getEntries());
            data.lastSelected = DlcFxServerPresetStore.getLastSelectedKey();
            JsonObject root = GSON.toJsonTree(data).getAsJsonObject();
            DlcDcConfigIO.write(file.toPath(), root, DlcDcConfigIO.Layout.GENERIC);
            presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.exported", data.servers.size()));
            saveConnectionState();
        } catch (Throwable t) {
            presetStatusLabel.setText(DlcFxI18n.tr("dlc_manager.server_preset.export_failed", DlcFxServerConnection.safeMessage(t)));
        }
    }

    private DlcServerEntry currentEntry() {
        DlcServerEntry entry = new DlcServerEntry();
        entry.name = nameField.getText().trim();
        entry.host = hostField.getText().trim().isBlank() ? "127.0.0.1" : hostField.getText().trim();
        entry.port = parsePort(portField.getText(), 29690);
        entry.password = passwordField.getText();
        entry.useTcp = true;
        return entry;
    }

    private static ServerPresetData readPresetData(Path path) {
        JsonObject root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        ServerPresetData data = root == null ? null : GSON.fromJson(root, ServerPresetData.class);
        if (data == null) {
            data = new ServerPresetData();
        }
        if (data.servers == null) {
            data.servers = List.of();
        }
        return data;
    }

    private static List<DlcServerEntry> upsertByKey(List<DlcServerEntry> current, List<DlcServerEntry> incoming) {
        Map<String, DlcServerEntry> merged = new LinkedHashMap<>();
        if (current != null) {
            for (DlcServerEntry entry : current) {
                if (entry != null && !entry.getKey().isBlank()) {
                    merged.put(entry.getKey(), entry);
                }
            }
        }
        if (incoming != null) {
            for (DlcServerEntry entry : incoming) {
                if (entry != null && !entry.getKey().isBlank()) {
                    merged.put(entry.getKey(), entry);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static int parsePort(String text, int fallback) {
        try {
            int port = Integer.parseInt(text == null ? "" : text.trim());
            return port <= 0 ? fallback : port;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void loadConnectionState() {
        loadingConnectionState = true;
        try {
            nameField.setText(PREFS.get(PREF_NAME, nameField.getText()));
            hostField.setText(PREFS.get(PREF_HOST, hostField.getText()));
            portField.setText(PREFS.get(PREF_PORT, portField.getText()));
            passwordField.setText(PREFS.get(PREF_PASSWORD, passwordField.getText()));
        } finally {
            loadingConnectionState = false;
        }
    }

    private void saveConnectionState() {
        if (loadingConnectionState) {
            return;
        }
        try {
            PREFS.put(PREF_NAME, nameField.getText() == null ? "" : nameField.getText());
            PREFS.put(PREF_HOST, hostField.getText() == null ? "" : hostField.getText());
            PREFS.put(PREF_PORT, portField.getText() == null ? "" : portField.getText());
            PREFS.put(PREF_PASSWORD, passwordField.getText() == null ? "" : passwordField.getText());
            PREFS.flush();
        } catch (Throwable ignored) {
        }
    }

    private static String serverKey(String host, int port) {
        return (host == null ? "" : host) + ":" + port;
    }

    private static final class ServerPresetData {
        List<DlcServerEntry> servers;
        String lastSelected;
    }

    private record ServerPresetOption(DlcServerEntry entry) {
        @Override
        public String toString() {
            return entry == null ? "" : entry.getDisplayName();
        }
    }
}
