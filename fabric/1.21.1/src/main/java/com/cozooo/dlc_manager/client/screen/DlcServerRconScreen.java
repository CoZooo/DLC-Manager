package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerRconInfo;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconScreen extends Screen {
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
        super(Component.translatable("dlc_manager.server.rcon_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 20, 0xFFFFFF);

        String hostValue = entry == null || entry.host == null ? "" : entry.host;
        Component hostLabel = Component.translatable("dlc_manager.server.rcon_host");
        int hostLabelWidth = this.font.width(hostLabel);
        int hostX = this.width / 2 - 120;
        guiGraphics.drawString(this.font, hostLabel, hostX, 42, 0xC8C8C8, false);
        guiGraphics.drawString(this.font, hostValue, hostX + hostLabelWidth + 6, 42, 0xE6E6E6, false);

        if (statusText != null && !statusText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, statusText, this.width / 2, 60, 0xC8C8C8);
        }

        if (connectOnly) {
            if (portField != null) {
                Component portLabel = Component.translatable("dlc_manager.server.rcon_port");
                int labelX = portField.getX() - this.font.width(portLabel) - 6;
                guiGraphics.drawString(this.font, portLabel, labelX, portField.getY() + 6, 0xC8C8C8, false);
            }
            if (passwordField != null) {
                Component passwordLabel = Component.translatable("dlc_manager.server.rcon_password");
                int labelX = passwordField.getX() - this.font.width(passwordLabel) - 6;
                guiGraphics.drawString(this.font, passwordLabel, labelX, passwordField.getY() + 6, 0xC8C8C8, false);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
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
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_connect_failed"), Component.empty());
                } else {
                    connected = true;
                    statusText = Component.translatable("dlc_manager.server.rcon_connected").getString();
                    saveRconConfig();
                    connectOnly = false;
                    init();
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
            return DlcServerConnection.fetchRconInfo(entry, 8000, getPlayerName(), getPlayerUuid());
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

    private String getPlayerName() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        return Minecraft.getInstance().player.getGameProfile().getName();
    }

    private String getPlayerUuid() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        return Minecraft.getInstance().player.getUUID().toString();
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
            entry.rconPort = rconPort;
            entry.rconPassword = rconPassword == null ? "" : rconPassword;
            list.add(entry);
        }
        DlcServerList.setEntries(list);
        DlcServerList.save();
    }
}
