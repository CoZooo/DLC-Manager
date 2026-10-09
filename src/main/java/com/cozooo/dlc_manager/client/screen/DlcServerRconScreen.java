package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerRconInfo;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconScreen extends DlcPageScreen {
    private final Screen parent;
    private final DlcServerEntry entry;

    private EditBox portField;
    private EditBox passwordField;
    private Button connectButton;
    private Button backButton;
    private Button playersButton;
    private Button consoleButton;
    private Button quickButton;

    private boolean rconEnabled;
    private boolean passwordSet;
    private boolean connected;
    private boolean loadingInfo;
    private boolean connectOnly = true;
    private boolean autoConnectAttempted;
    private boolean connectingRcon;
    private String statusText;

    private int rconPort = 25575;
    private String rconPassword = "";

    public DlcServerRconScreen(Screen parent, DlcServerEntry entry) {
        super(DlcUiPage.SERVER_RCON, parent, Component.translatable("dlc_manager.server.rcon_title"));
        this.parent = parent;
        this.entry = entry;
    }

    private void initControls() {
        clearWidgets();
        loadSavedRcon();
        if (connectOnly) {
            initConnectPanel();
        } else {
            initHubPanel();
        }
    }

    private void initConnectPanel() {
        int centerX = this.width / 2;
        int topY = 60;
        int fieldWidth = Math.min(220, this.width - 80);
        int fieldX = centerX - fieldWidth / 2;

        portField = new EditBox(this.font, fieldX, topY + 18, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_port"));
        passwordField = new EditBox(this.font, fieldX, topY + 44, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_password"));
        portField.setMaxLength(16);
        passwordField.setMaxLength(256);
        portField.setValue(Integer.toString(rconPort));
        passwordField.setValue(rconPassword == null ? "" : rconPassword);
        addRenderableWidget(portField);
        addRenderableWidget(passwordField);

        int buttonY = topY + 76;
        int buttonWidth = 90;
        int gap = 8;
        int totalWidth = buttonWidth * 2 + gap;
        int startX = centerX - totalWidth / 2;
        connectButton = Button.builder(Component.translatable("dlc_manager.server.rcon_connect"), btn -> connect())
            .bounds(startX, buttonY, buttonWidth, 20)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(startX + buttonWidth + gap, buttonY, buttonWidth, 20)
            .build();
        addRenderableWidget(connectButton);
        addRenderableWidget(backButton);

        loadRconInfo();
        updateButtonStates();
        if (!autoConnectAttempted && rconPassword != null && !rconPassword.isBlank()) {
            autoConnectAttempted = true;
            connect();
        }
    }

    private void initHubPanel() {
        int centerX = this.width / 2;
        int startY = 80;
        int buttonWidth = 140;
        int buttonHeight = 20;
        int gap = 8;
        playersButton = Button.builder(Component.translatable("dlc_manager.server.rcon_players"), btn -> openPlayers())
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build();
        consoleButton = Button.builder(Component.translatable("dlc_manager.server.rcon_console"), btn -> openConsole())
            .bounds(centerX - buttonWidth / 2, startY + buttonHeight + gap, buttonWidth, buttonHeight)
            .build();
        quickButton = Button.builder(Component.translatable("dlc_manager.server.rcon_common"), btn -> openQuickCommands())
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + gap) * 2, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + gap) * 3 + 6, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(playersButton);
        addRenderableWidget(consoleButton);
        addRenderableWidget(quickButton);
        addRenderableWidget(backButton);

        updateButtonStates();
    }

    private void connect() {
        String host = entry == null ? "" : entry.host;
        int port = parsePort();
        String password = passwordField == null ? "" : passwordField.getValue();
        if (host == null || host.isBlank()) {
            return;
        }
        connectingRcon = true;
        rconPort = port;
        rconPassword = password == null ? "" : password;
        updateButtonStates();
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, "list", 8000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                connectingRcon = false;
                if (response == null) {
                    connected = false;
                    statusText = Component.translatable("dlc_manager.server.rcon_connect_failed").getString();
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.rcon_connect_failed"), Component.empty());
                } else {
                    connected = true;
                    statusText = Component.translatable("dlc_manager.server.rcon_connected").getString();
                    saveRconConfig();
                    connectOnly = false;
                    refreshPage();
                }
                updateButtonStates();
            }));
    }

    private void openPlayers() {
        Minecraft.getInstance().setScreen(new DlcServerRconPlayersScreen(this, entry, rconPort, rconPassword));
    }

    private void openConsole() {
        Minecraft.getInstance().setScreen(new DlcServerRconConsoleScreen(this, entry, rconPort, rconPassword));
    }

    private void openQuickCommands() {
        Minecraft.getInstance().setScreen(new DlcServerRconQuickScreen(this, entry, rconPort, rconPassword));
    }

    private int parsePort() {
        if (portField == null) {
            return rconPort;
        }
        String text = portField.getValue().trim();
        int port = 25575;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
        }
        if (port < 1 || port > 65535) {
            port = 25575;
        }
        return port;
    }

    private void loadRconInfo() {
        if (loadingInfo) {
            return;
        }
        loadingInfo = true;
        CompletableFuture
            .supplyAsync(this::fetchRconInfo)
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                loadingInfo = false;
                if (info != null) {
                    rconEnabled = info.enabled;
                    passwordSet = info.passwordSet;
                    if (portField != null) {
                        portField.setValue(Integer.toString(info.port));
                    }
                }
                if (!rconEnabled) {
                    statusText = Component.translatable("dlc_manager.server.rcon_disabled").getString();
                } else if (!passwordSet) {
                    statusText = Component.translatable("dlc_manager.server.rcon_password_missing").getString();
                } else if (!connected) {
                    statusText = Component.translatable("dlc_manager.server.rcon_disconnected").getString();
                }
                updateButtonStates();
            }));
    }

    private DlcServerRconInfo fetchRconInfo() {
        try {
            return DlcServerConnection.fetchRconInfo(entry, 8000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateButtonStates() {
        if (connectOnly) {
            if (connectButton != null) {
                connectButton.active = !connectingRcon;
            }
            return;
        }
        boolean enable = connected;
        if (playersButton != null) {
            playersButton.active = enable;
        }
        if (consoleButton != null) {
            consoleButton.active = enable;
        }
        if (quickButton != null) {
            quickButton.active = enable;
        }
    }

    private void loadSavedRcon() {
        if (entry == null) {
            return;
        }
        if (entry.rconPort > 0) {
            rconPort = entry.rconPort;
        }
        if (entry.rconPassword != null) {
            rconPassword = entry.rconPassword;
        }
    }

    private void saveRconConfig() {
        if (entry == null) {
            return;
        }
        entry.rconPort = rconPort;
        entry.rconPassword = rconPassword == null ? "" : rconPassword;
        DlcServerList.load();
        List<DlcServerEntry> list = new ArrayList<>(DlcServerList.getEntries());
        boolean updated = false;
        for (DlcServerEntry item : list) {
            if (item != null && entry.getKey().equals(item.getKey())) {
                item.rconPort = rconPort;
                item.rconPassword = rconPassword == null ? "" : rconPassword;
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(entry);
        }
        DlcServerList.setEntries(list);
        DlcServerList.save();
    }

    @Override protected void buildPage() {
        String portDraft = portField == null ? null : portField.getValue();
        String passwordDraft = passwordField == null ? null : passwordField.getValue();
        initControls();
        if (portDraft != null && portField != null) portField.setValue(portDraft);
        if (passwordDraft != null && passwordField != null) passwordField.setValue(passwordDraft);
        var body = node(content, "div", "page-dialog");
        text(body, tr("dlc_manager.server.rcon_host") + "  " + (entry == null || entry.host == null ? "" : entry.host), "page-description");
        live(body, () -> statusText, "page-message");
        if (connectOnly) {
            mount(body, portField, tr("dlc_manager.server.rcon_port"), "text");
            mount(body, passwordField, tr("dlc_manager.server.rcon_password"), "password");
            mount(footer, connectButton).getElement().setAttribute("class", "button primary");
        } else {
            mount(row(body, tr("dlc_manager.server.rcon_players"), ""), playersButton);
            mount(row(body, tr("dlc_manager.server.rcon_console"), ""), consoleButton);
            mount(row(body, tr("dlc_manager.server.rcon_common"), ""), quickButton);
        }

    }
    @Override protected void updatePage() { updateButtonStates(); }

}
