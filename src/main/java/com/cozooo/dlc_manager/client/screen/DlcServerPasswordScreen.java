package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerPasswordScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final boolean autoConnect;
    private final boolean openServerManagerOnSuccess;
    private EditBox portField;
    private EditBox passwordField;
    private Button connectButton;
    private Button backButton;
    private boolean connecting;

    public DlcServerPasswordScreen(Screen parent, DlcServerEntry entry, boolean autoConnect) {
        this(parent, entry, autoConnect, false);
    }

    public DlcServerPasswordScreen(Screen parent, DlcServerEntry entry, boolean autoConnect, boolean openServerManagerOnSuccess) {
        super(Component.translatable("dlc_manager.server.password_title"));
        this.parent = parent;
        this.entry = entry;
        this.autoConnect = autoConnect;
        this.openServerManagerOnSuccess = openServerManagerOnSuccess;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(260, this.width - 40);
        int fieldHeight = 18;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 32;

        portField = new EditBox(this.font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, Component.empty());
        portField.setValue(Integer.toString(entry.port));
        portField.setFilter(value -> value.chars().allMatch(Character::isDigit));
        addRenderableWidget(portField);

        passwordField = new EditBox(this.font, centerX - fieldWidth / 2, startY + 24, fieldWidth, fieldHeight, Component.empty());
        if (entry.password != null) {
            passwordField.setValue(entry.password);
        }
        addRenderableWidget(passwordField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = startY + 56;
        connectButton = Button.builder(Component.translatable("dlc_manager.server.connect"), btn -> startConnect())
            .bounds(centerX - buttonWidth - 6, buttonY, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX + 6, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(connectButton);
        addRenderableWidget(backButton);
        updateButtonState();
        if (autoConnect && entry.password != null && !entry.password.isBlank()) {
            startConnect();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, this.height / 2 - 60, 0xFFFFFF);
        String address = entry.host == null ? "" : entry.host;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.address", address), centerX, this.height / 2 - 48, 0xC8C8C8);
        drawFieldLabel(guiGraphics, "dlc_manager.server.port", portField);
        drawFieldLabel(guiGraphics, "dlc_manager.server.password", passwordField);
        updateButtonState();
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawFieldLabel(GuiGraphics guiGraphics, String key, EditBox field) {
        int labelWidth = this.font.width(Component.translatable(key));
        int x = field.getX() - labelWidth - 8;
        if (x < 8) {
            x = 8;
        }
        int y = field.getY() + (field.getHeight() - this.font.lineHeight) / 2;
        guiGraphics.drawString(this.font, Component.translatable(key), x, y, 0xC8C8C8, false);
    }

    private void updateButtonState() {
        if (connectButton != null) {
            connectButton.active = !connecting;
        }
        if (backButton != null) {
            backButton.active = !connecting;
        }
    }

    private void startConnect() {
        if (connecting) {
            return;
        }
        int port = parsePort();
        if (port <= 0) {
            return;
        }
        connecting = true;
        entry.port = port;
        entry.password = passwordField.getValue().trim();
        updateButtonState();
        CompletableFuture
            .supplyAsync(() -> fetchInfo(entry))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                connecting = false;
                updateButtonState();
                if (info == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.connect_failed"), Component.empty());
                    return;
                }
                saveEntry(entry);
                DlcServerCache.put(entry.getKey(), info);
                if (openServerManagerOnSuccess) {
                    Minecraft.getInstance().setScreen(new DlcServerManagerScreen(parent, entry, info, DlcServerManagerScreen.Tab.SERVERS, true));
                } else {
                    Minecraft.getInstance().setScreen(new DlcServerMaLiLibScreen(parent, entry, info));
                }
            }));
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveEntry(DlcServerEntry entry) {
        DlcServerList.load();
        List<DlcServerEntry> list = new ArrayList<>(DlcServerList.getEntries());
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            DlcServerEntry existing = list.get(i);
            if (existing != null && entry.getKey().equals(existing.getKey())) {
                existing.password = entry.password;
                existing.port = entry.port;
                if (entry.name != null && !entry.name.isBlank()) {
                    existing.name = entry.name;
                }
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(entry);
        }
        DlcServerList.setEntries(list);
        DlcServerList.setLastSelectedKey(entry.getKey());
        DlcServerList.save();
    }

    private int parsePort() {
        if (portField == null) {
            return entry.port;
        }
        String value = portField.getValue().trim();
        if (value.isEmpty()) {
            return entry.port;
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                    Component.translatable("dlc_manager.server.port_invalid"), Component.empty());
                return -1;
            }
            return port;
        } catch (NumberFormatException ex) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                Component.translatable("dlc_manager.server.port_invalid"), Component.empty());
            return -1;
        }
    }
}
