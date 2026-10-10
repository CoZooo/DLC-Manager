package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconPlayersScreen extends DlcPageScreen {


    private final Screen parent;
    private final DlcServerEntry entry;
    private final int port;
    private final String password;

    private final List<String> players = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private String statusText = "";

    private Button refreshButton;
    private Button opButton;
    private Button deopButton;
    private Button kickButton;
    private Button banButton;
    private Button backButton;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private boolean draggingScrollbar;

    public DlcServerRconPlayersScreen(Screen parent, DlcServerEntry entry, int port, String password) {
        super(DlcUiPage.SERVER_RCON_PLAYERS, parent, DlcTextCompat.translatable("dlc_manager.server.rcon_players"));
        this.parent = parent;
        this.entry = entry;
        this.port = port;
        this.password = password == null ? "" : password;
    }

    private void initControls() {
        int margin = 20;
        listLeft = margin;
        listRight = this.width - margin;
        listTop = 60;
        listBottom = this.height - 60;

        int buttonY = 32;
        refreshButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_refresh"), btn -> refreshPlayers())
            .bounds(margin, buttonY, 80, 20)
            .build();
        opButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_op"), btn -> opSelected())
            .bounds(margin + 86, buttonY, 60, 20)
            .build();
        deopButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_deop"), btn -> deopSelected())
            .bounds(margin + 150, buttonY, 60, 20)
            .build();
        kickButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_kick"), btn -> kickSelected())
            .bounds(margin + 214, buttonY, 60, 20)
            .build();
        banButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.rcon_ban"), btn -> banSelected())
            .bounds(margin + 278, buttonY, 60, 20)
            .build();
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width - margin - 80, this.height - 30, 80, 20)
            .build();

        addRenderableWidget(refreshButton);
        addRenderableWidget(opButton);
        addRenderableWidget(deopButton);
        addRenderableWidget(kickButton);
        addRenderableWidget(banButton);
        addRenderableWidget(backButton);

        refreshPlayers();
        updateButtonStates();
    }

    private void refreshPlayers() {
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, "list", 8000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    statusText = DlcTextCompat.translatable("dlc_manager.server.rcon_connect_failed").getString();
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.rcon_connect_failed"), DlcTextCompat.empty());
                } else {
                    statusText = "";
                    updatePlayersFromList(response);
                }
                updateButtonStates();
            }));
    }

    private void updatePlayersFromList(String response) {
        players.clear();
        selected = -1;
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
    }

    private void kickSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("kick " + player, true);
    }

    private void banSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("ban " + player, true);
    }

    private void opSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("op " + player, true);
    }

    private void deopSelected() {
        String player = getSelectedPlayer();
        if (player == null) {
            return;
        }
        runCommand("deop " + player, true);
    }

    private void runCommand(String command, boolean refreshAfter) {
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(Minecraft.getInstance()),
                        DlcTextCompat.translatable("dlc_manager.server.rcon_exec_failed"), DlcTextCompat.empty());
                } else if (refreshAfter) {
                    refreshPlayers();
                }
            }));
    }

    private String getSelectedPlayer() {
        if (selected >= 0 && selected < players.size()) {
            return players.get(selected);
        }
        return null;
    }

    private void updateButtonStates() {
        boolean hasPlayer = getSelectedPlayer() != null;
        if (kickButton != null) {
            kickButton.active = hasPlayer;
        }
        if (banButton != null) {
            banButton.active = hasPlayer;
        }
        if (opButton != null) {
            opButton.active = hasPlayer;
        }
        if (deopButton != null) {
            deopButton.active = hasPlayer;
        }
    }

    private java.util.List<String> renderedPlayers = java.util.List.of();
    private int renderedSelection = -2;
    @Override protected void buildPage() {
        initControls();
        mount(toolbar, refreshButton);
        mount(footer, opButton); mount(footer, deopButton);
        mount(footer, kickButton).getElement().setAttribute("class", "button danger");
        mount(footer, banButton).getElement().setAttribute("class", "button danger");

        renderPlayers();
    }
    private void renderPlayers() {
        content.setInnerHTML(""); renderedPlayers = java.util.List.copyOf(players); renderedSelection = selected;
        for (int i = 0; i < players.size(); i++) {
            int index = i;
            var row = row(content, players.get(i), "");
            row.setAttribute("class", "page-row server-row" + (index == selected ? " selected" : ""));
            row.setAttribute("role", "option"); row.setAttribute("aria-selected", String.valueOf(index == selected));
            row.addEventListener("click", event -> { selected = index; updateButtonStates(); });
        }
        if (players.isEmpty()) text(content, tr("dlc_manager.ui.no_players"), "empty-state");
    }
    @Override protected void updatePage() {
        if (!renderedPlayers.equals(players) || renderedSelection != selected) renderPlayers();
        updateButtonStates(); status(statusText);
    }

}
