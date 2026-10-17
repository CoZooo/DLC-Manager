package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerPasswordScreen extends DlcPageScreen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final boolean autoConnect;
    private boolean autoConnectAttempted;
    private final boolean openServerManagerOnSuccess;
    private EditBox portField;
    private EditBox passwordField;
    private Button connectButton;
    private Button backButton;
    private boolean connecting;
    private boolean usePortMultiplexer;

    public DlcServerPasswordScreen(Screen parent, DlcServerEntry entry, boolean autoConnect) {
        this(parent, entry, autoConnect, false);
    }

    public DlcServerPasswordScreen(Screen parent, DlcServerEntry entry, boolean autoConnect, boolean openServerManagerOnSuccess) {
        super(DlcUiPage.SERVER_PASSWORD, parent, DlcTextCompat.translatable("dlc_manager.server.password_title"));
        this.parent = parent;
        this.entry = entry;
        this.autoConnect = autoConnect;
        this.openServerManagerOnSuccess = openServerManagerOnSuccess;
        this.usePortMultiplexer = entry.usePortMultiplexer;
    }

    private void initControls() {
        int fieldWidth = Math.min(260, this.width - 40);
        int fieldHeight = 18;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 32;

        portField = new EditBox(this.font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, DlcTextCompat.empty());
        portField.setValue(Integer.toString(entry.port));
        DlcScreenCompat.setNumericFilter(portField);
        addRenderableWidget(portField);

        passwordField = new EditBox(this.font, centerX - fieldWidth / 2, startY + 24, fieldWidth, fieldHeight, DlcTextCompat.empty());
        if (entry.password != null) {
            passwordField.setValue(entry.password);
        }
        addRenderableWidget(passwordField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = startY + 56;
        connectButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.connect"), btn -> startConnect())
            .bounds(centerX - buttonWidth - 6, buttonY, buttonWidth, buttonHeight)
            .build();
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX + 6, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(connectButton);
        addRenderableWidget(backButton);
        updateButtonState();
        if (autoConnect && !autoConnectAttempted && entry.password != null && !entry.password.isBlank()) {
            autoConnectAttempted = true;
            startConnect();
        }
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
        entry.usePortMultiplexer = usePortMultiplexer;
        updateButtonState();
        CompletableFuture
            .supplyAsync(() -> fetchInfo(entry))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                connecting = false;
                updateButtonState();
                if (info == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.connect_failed"), DlcTextCompat.empty());
                    return;
                }
                saveEntry(entry);
                DlcServerCache.put(entry.getKey(), info);
                if (openServerManagerOnSuccess) {
                    Minecraft.getInstance().setScreen(new DlcServerManagerScreen(parent, entry, info, DlcServerManagerScreen.Tab.SERVERS, true));
                } else {
                    Minecraft.getInstance().setScreen(new DlcServerScreen(parent, entry, info));
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
                existing.usePortMultiplexer = entry.usePortMultiplexer;
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
                DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                    DlcTextCompat.translatable("dlc_manager.server.port_invalid"), DlcTextCompat.empty());
                return -1;
            }
            return port;
        } catch (NumberFormatException ex) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                DlcTextCompat.translatable("dlc_manager.server.port_invalid"), DlcTextCompat.empty());
            return -1;
        }
    }

    @Override protected void buildPage() {
        String portDraft = portField == null ? null : portField.getValue();
        String passwordDraft = passwordField == null ? null : passwordField.getValue();
        initControls();
        if (portDraft != null && portField != null) portField.setValue(portDraft);
        if (passwordDraft != null && passwordField != null) passwordField.setValue(passwordDraft);
        var form = node(content, "div", "page-dialog");
        text(form, tr("dlc_manager.server.address", entry.host == null ? "" : entry.host), "page-description");
        mount(form, portField, tr("dlc_manager.server.port"), "text");
        mount(form, passwordField, tr("dlc_manager.server.password"), "password");
        toggle(form, tr("dlc_manager.server.use_port_multiplexer"), usePortMultiplexer, value -> { usePortMultiplexer = value; refreshPage(); });

        mount(footer, connectButton).getElement().setAttribute("class", "button primary");
    }
    @Override protected void updatePage() { updateButtonState(); status(connecting ? tr("dlc_manager.server.connecting") : ""); }
    @Override public void onClose() { if (!connecting) super.onClose(); }

    @Override public boolean shouldCloseOnEsc() { return !connecting; }
}
