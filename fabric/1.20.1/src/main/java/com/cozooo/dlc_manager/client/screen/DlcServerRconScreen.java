package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerRconInfo;
import com.cozooo.dlc_manager.client.server.DlcServerTcpClient;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconScreen extends Screen {
    private static final int PLAYER_ROW_HEIGHT = 18;

    private final Screen parent;
    private final DlcServerEntry entry;

    private EditBox portField;
    private EditBox passwordField;
    private EditBox commandField;
    private Button connectButton;
    private Button refreshButton;
    private Button kickButton;
    private Button banButton;
    private Button backButton;
    private Button sendButton;
    private Button consoleButton;

    private boolean rconEnabled;
    private boolean passwordSet;
    private boolean connected;
    private boolean loadingInfo;
    private String statusText;
    private boolean connectOnly = true;
    private boolean autoConnectAttempted;
    private boolean connectingRcon;
    private int rconPort = 25575;
    private String rconPassword = "";

    private final List<String> players = new ArrayList<>();
    private int selectedPlayer = -1;
    private int playerScroll;

    private final List<String> consoleLines = new ArrayList<>();
    private int consoleScroll;

    private final List<QuickCommand> quickCommands = new ArrayList<>();

    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int playerListTop;
    private int playerListBottom;
    private int consoleTop;
    private int consoleBottom;
    private int consoleInputY;

    public DlcServerRconScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.server.rcon_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        clearWidgets();
        layout();
        loadSavedRcon();
        if (connectOnly) {
            initConnectPanel();
        } else {
            initRconPanel();
        }
    }

    private void layout() {
        int margin = 20;
        leftX = margin;
        leftWidth = (this.width - margin * 3) / 2;
        rightX = leftX + leftWidth + margin;
        rightWidth = this.width - rightX - margin;
        consoleTop = this.height - 110;
        consoleBottom = this.height - 34;
        consoleInputY = this.height - 28;
        playerListTop = 50 + 30 + 30 + 14;
        playerListBottom = consoleTop - 14;
        if (playerListBottom < playerListTop + PLAYER_ROW_HEIGHT) {
            playerListBottom = playerListTop + PLAYER_ROW_HEIGHT;
        }
    }

    private void buildQuickCommands() {
        quickCommands.clear();
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_keep_inventory_on", "gamerule keepInventory true"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_keep_inventory_off", "gamerule keepInventory false"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_daylight_off", "gamerule doDaylightCycle false"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_daylight_on", "gamerule doDaylightCycle true"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_mobspawning_off", "gamerule doMobSpawning false"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_mobspawning_on", "gamerule doMobSpawning true"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_weather_clear", "weather clear"));
        quickCommands.add(new QuickCommand("dlc_manager.server.rcon_cmd_time_day", "time set day"));

        int buttonWidth = Math.max(80, (rightWidth - 6) / 2);
        int buttonHeight = 20;
        int x1 = rightX;
        int x2 = rightX + buttonWidth + 6;
        int y = 74;
        for (int i = 0; i < quickCommands.size(); i++) {
            QuickCommand cmd = quickCommands.get(i);
            int x = (i % 2 == 0) ? x1 : x2;
            if (i % 2 == 0 && i > 0) {
                y += buttonHeight + 4;
            }
            cmd.button = Button.builder(Component.translatable(cmd.labelKey), btn -> runQuickCommand(cmd.command))
                .bounds(x, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(cmd.button);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, 20, 0xFFFFFF);

        String hostValue = entry == null || entry.host == null ? "" : entry.host;
        Component hostLabel = Component.translatable("dlc_manager.server.rcon_host");
        int hostLabelWidth = this.font.width(hostLabel);
        guiGraphics.drawString(this.font, hostLabel, leftX, 52, 0xC8C8C8, false);
        guiGraphics.drawString(this.font, hostValue, leftX + hostLabelWidth + 6, 52, 0xE6E6E6, false);

        if (connectOnly) {
            if (portField != null) {
                guiGraphics.drawString(this.font, Component.translatable("dlc_manager.server.rcon_port"), leftX, portField.getY() + 6, 0xC8C8C8, false);
            }
            if (passwordField != null) {
                guiGraphics.drawString(this.font, Component.translatable("dlc_manager.server.rcon_password"), leftX, passwordField.getY() + 6, 0xC8C8C8, false);
            }
            if (statusText != null && !statusText.isBlank()) {
                guiGraphics.drawString(this.font, statusText, leftX, (portField == null ? 58 : portField.getY() - 12), 0xC8C8C8, false);
            }
        } else {
            if (statusText != null && !statusText.isBlank()) {
                guiGraphics.drawString(this.font, statusText, leftX, 60, 0xC8C8C8, false);
            }
            guiGraphics.drawString(this.font, Component.translatable("dlc_manager.server.rcon_players"), leftX, playerListTop - 14, 0xC8C8C8, false);
            drawPlayerList(guiGraphics);
            guiGraphics.drawString(this.font, Component.translatable("dlc_manager.server.rcon_common"), rightX, 52, 0xC8C8C8, false);
            guiGraphics.drawString(this.font, Component.translatable("dlc_manager.server.rcon_console"), leftX + 90, consoleTop - 14, 0xC8C8C8, false);
            drawConsole(guiGraphics);
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
        if (sendButton != null && commandField != null) {
            sendButton.active = connected && !commandField.getValue().trim().isEmpty();
        }
    }

    private void drawPlayerList(GuiGraphics guiGraphics) {
        int height = playerListBottom - playerListTop;
        if (height <= 0) {
            return;
        }
        guiGraphics.fill(leftX, playerListTop, leftX + leftWidth, playerListBottom, 0x22000000);
        int maxVisible = Math.max(1, height / PLAYER_ROW_HEIGHT);
        int maxScroll = Math.max(0, players.size() - maxVisible);
        if (playerScroll > maxScroll) {
            playerScroll = maxScroll;
        }
        int rowY = playerListTop + 2;
        for (int i = 0; i < players.size(); i++) {
            if (i < playerScroll || i >= playerScroll + maxVisible) {
                continue;
            }
            boolean selected = i == selectedPlayer;
            if (selected) {
                guiGraphics.fill(leftX + 2, rowY - 1, leftX + leftWidth - 2, rowY + PLAYER_ROW_HEIGHT - 1, 0x33222222);
                guiGraphics.fill(leftX + 2, rowY - 1, leftX + leftWidth - 2, rowY, 0xFFFFFFFF);
                guiGraphics.fill(leftX + 2, rowY + PLAYER_ROW_HEIGHT - 2, leftX + leftWidth - 2, rowY + PLAYER_ROW_HEIGHT - 1, 0xFFFFFFFF);
            }
            guiGraphics.drawString(this.font, players.get(i), leftX + 6, rowY + 4, 0xE6E6E6, false);
            rowY += PLAYER_ROW_HEIGHT;
        }
    }

    private void drawConsole(GuiGraphics guiGraphics) {
        guiGraphics.fill(leftX, consoleTop, this.width - leftX, consoleBottom, 0x22000000);
        int lineHeight = this.font.lineHeight + 1;
        int maxVisible = Math.max(1, (consoleBottom - consoleTop - 4) / lineHeight);
        int maxScroll = Math.max(0, consoleLines.size() - maxVisible);
        if (consoleScroll > maxScroll) {
            consoleScroll = maxScroll;
        }
        int startY = consoleTop + 2;
        int index = consoleScroll;
        for (int i = 0; i < maxVisible && index + i < consoleLines.size(); i++) {
            String line = consoleLines.get(index + i);
            guiGraphics.drawString(this.font, line, leftX + 4, startY + i * lineHeight, 0xC8C8C8, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (connectOnly) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (mouseX >= leftX && mouseX <= leftX + leftWidth && mouseY >= playerListTop && mouseY <= playerListBottom) {
            int index = (int) ((mouseY - playerListTop) / PLAYER_ROW_HEIGHT) + playerScroll;
            if (index >= 0 && index < players.size()) {
                selectedPlayer = index;
                updateButtonStates();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (connectOnly) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (mouseX >= leftX && mouseX <= leftX + leftWidth && mouseY >= playerListTop && mouseY <= playerListBottom) {
            int height = playerListBottom - playerListTop;
            int maxVisible = Math.max(1, height / PLAYER_ROW_HEIGHT);
            int maxScroll = Math.max(0, players.size() - maxVisible);
            if (maxScroll > 0) {
                playerScroll = Math.max(0, Math.min(maxScroll, playerScroll + (delta > 0 ? -1 : 1)));
                return true;
            }
        }
        if (mouseX >= leftX && mouseX <= this.width - leftX && mouseY >= consoleTop && mouseY <= consoleBottom) {
            int lineHeight = this.font.lineHeight + 1;
            int maxVisible = Math.max(1, (consoleBottom - consoleTop - 4) / lineHeight);
            int maxScroll = Math.max(0, consoleLines.size() - maxVisible);
            if (maxScroll > 0) {
                consoleScroll = Math.max(0, Math.min(maxScroll, consoleScroll + (delta > 0 ? -1 : 1)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_connect_failed"), Component.empty());
                } else {
                    connected = true;
                    statusText = Component.translatable("dlc_manager.server.rcon_connected").getString();
                    updatePlayersFromList(response);
                    appendConsole("> list");
                    appendConsole(response);
                    saveRconConfig();
                    connectOnly = false;
                    init();
                }
                updateButtonStates();
            }));
    }

    private void refreshPlayers() {
        if (!connected) {
            connect();
            return;
        }
        runCommandInternal("list", true);
    }

    private void kickSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommandInternal("kick " + player, true);
    }

    private void banSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommandInternal("ban " + player, true);
    }

    private void runQuickCommand(String command) {
        runCommandInternal(command, false);
    }

    private void sendCommand() {
        String command = commandField.getValue().trim();
        if (command.isEmpty()) {
            return;
        }
        runCommandInternal(command, false);
        commandField.setValue("");
    }

    private void runCommandInternal(String command, boolean refreshAfter) {
        String host = entry == null ? "" : entry.host;
        int port = rconPort;
        String password = rconPassword;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                appendConsole("> " + command);
                if (response == null) {
                    appendConsole(Component.translatable("dlc_manager.server.rcon_exec_failed").getString());
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else {
                    appendConsole(response);
                    if ("list".equals(command)) {
                        updatePlayersFromList(response);
                    } else if (refreshAfter) {
                        refreshPlayers();
                    }
                }
            }));
    }

    private void updatePlayersFromList(String response) {
        players.clear();
        selectedPlayer = -1;
        if (response != null) {
            int colon = response.indexOf(':');
            if (colon >= 0 && colon + 1 < response.length()) {
                String list = response.substring(colon + 1).trim();
                if (!list.isEmpty()) {
                    String[] split = list.split(",");
                    for (String part : split) {
                        String name = part.trim();
                        if (!name.isEmpty()) {
                            players.add(name);
                        }
                    }
                }
            }
        }
        updateButtonStates();
    }

    private void appendConsole(String text) {
        if (text == null) {
            return;
        }
        String[] lines = text.split("\\r?\\n");
        Collections.addAll(consoleLines, lines);
        if (consoleLines.size() > 200) {
            int remove = consoleLines.size() - 200;
            consoleLines.subList(0, remove).clear();
        }
        int lineHeight = this.font.lineHeight + 1;
        int maxVisible = Math.max(1, (consoleBottom - consoleTop - 4) / lineHeight);
        consoleScroll = Math.max(0, consoleLines.size() - maxVisible);
    }

    private String getSelectedPlayer() {
        if (selectedPlayer >= 0 && selectedPlayer < players.size()) {
            return players.get(selectedPlayer);
        }
        return null;
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
                } else {
                    statusText = "";
                }
                updateButtonStates();
            }));
    }

    private DlcServerRconInfo fetchRconInfo() {
        try {
            return DlcServerTcpClient.fetchRconInfo(entry, 8000, getPlayerName(), getPlayerUuid());
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
        boolean hasPlayer = getSelectedPlayer() != null;
        if (kickButton != null) {
            kickButton.active = connected && hasPlayer;
        }
        if (banButton != null) {
            banButton.active = connected && hasPlayer;
        }
        if (refreshButton != null) {
            refreshButton.active = connected;
        }
        if (connectButton != null) {
            connectButton.active = true;
        }
        if (sendButton != null) {
            sendButton.active = connected && commandField != null && !commandField.getValue().trim().isEmpty();
        }
        for (QuickCommand cmd : quickCommands) {
            if (cmd.button != null) {
                cmd.button.active = connected;
            }
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

    private void initConnectPanel() {
        int fieldWidth = Math.max(80, leftWidth - 120);
        int fieldX = leftX + 110;
        int topY = 50;
        portField = new EditBox(this.font, fieldX, topY + 22, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_port"));
        passwordField = new EditBox(this.font, fieldX, topY + 46, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_password"));
        portField.setMaxLength(16);
        passwordField.setMaxLength(256);
        portField.setValue(Integer.toString(rconPort));
        passwordField.setValue(rconPassword == null ? "" : rconPassword);
        addRenderableWidget(portField);
        addRenderableWidget(passwordField);

        int buttonY = topY + 74;
        connectButton = Button.builder(Component.translatable("dlc_manager.server.rcon_connect"), btn -> connect())
            .bounds(leftX, buttonY, 90, 20)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(leftX + 96, buttonY, 70, 20)
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

    private void initRconPanel() {
        int buttonY = 50 + 74;
        refreshButton = Button.builder(Component.translatable("dlc_manager.server.rcon_refresh"), btn -> refreshPlayers())
            .bounds(leftX + 86, buttonY, 80, 20)
            .build();
        addRenderableWidget(refreshButton);

        int playerTitleY = buttonY + 30;
        int kickWidth = 56;
        int banWidth = 56;
        banButton = Button.builder(Component.translatable("dlc_manager.server.rcon_ban"), btn -> banSelected())
            .bounds(leftX + leftWidth - banWidth, playerTitleY - 2, banWidth, 20)
            .build();
        kickButton = Button.builder(Component.translatable("dlc_manager.server.rcon_kick"), btn -> kickSelected())
            .bounds(leftX + leftWidth - banWidth - kickWidth - 4, playerTitleY - 2, kickWidth, 20)
            .build();
        addRenderableWidget(kickButton);
        addRenderableWidget(banButton);

        buildQuickCommands();

        consoleButton = Button.builder(Component.translatable("dlc_manager.server.rcon_console"), btn -> {
            if (commandField != null) {
                commandField.setFocused(true);
            }
        })
            .bounds(leftX, consoleTop - 22, 80, 20)
            .build();
        addRenderableWidget(consoleButton);

        commandField = new EditBox(this.font, leftX, consoleInputY, Math.max(80, this.width - leftX - 110), 20,
            Component.translatable("dlc_manager.server.rcon_command"));
        commandField.setMaxLength(2048);
        addRenderableWidget(commandField);

        sendButton = Button.builder(Component.translatable("dlc_manager.server.rcon_send"), btn -> sendCommand())
            .bounds(this.width - 90, consoleInputY, 70, 20)
            .build();
        addRenderableWidget(sendButton);

        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width - 90, consoleInputY - 24, 70, 20)
            .build();
        addRenderableWidget(backButton);

        updateButtonStates();
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

    private static final class QuickCommand {
        private final String labelKey;
        private final String command;
        private Button button;

        private QuickCommand(String labelKey, String command) {
            this.labelKey = labelKey;
            this.command = command;
        }
    }
}
