package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.rcon.DlcRconQuickCommandCatalog;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconQuickScreen extends DlcPageScreen {
    private final Screen parent;
    private final DlcServerEntry serverEntry;
    private final int port;
    private final String password;

    private final List<QuickEntry> quickEntries = new ArrayList<>();
    private String statusText;

    private Button backButton;
    private boolean draggingScrollbar;
    private int scroll;
    private int panelTop;
    private int panelBottom;
    private int panelLeft;
    private int panelRight;
    private int scrollbarX;
    private int buttonWidth;
    private int buttonHeight;
    private int buttonGap;

    public DlcServerRconQuickScreen(Screen parent, DlcServerEntry serverEntry, int port, String password) {
        super(DlcUiPage.SERVER_RCON_QUICK, parent, Component.translatable("dlc_manager.server.rcon_common"));
        this.parent = parent;
        this.serverEntry = serverEntry;
        this.port = port;
        this.password = password == null ? "" : password;
    }

    private void initControls() {
        quickEntries.clear();
        for (DlcRconQuickCommandCatalog.QuickCommandItem item : DlcRconQuickCommandCatalog.commandItems()) {
            if (item.separator()) {
                addSeparator(item.labelKey());
            } else {
                addCommand(item.labelKey(), item.command());
            }
        }

        int margin = 20;
        panelLeft = margin;
        panelRight = this.width - margin - 16;
        panelTop = 40;
        panelBottom = this.height - 50;
        scrollbarX = this.width - margin + 2;
        buttonWidth = Math.max(120, (this.width - margin * 2 - 6) / 2);
        buttonHeight = 20;
        buttonGap = 6;
        int x1 = this.width / 2 - buttonWidth - 3;
        int x2 = this.width / 2 + 3;
        int y = panelTop;

        int col = 0;
        for (QuickEntry quickEntry : quickEntries) {
            if (quickEntry.separator) {
                if (col != 0) {
                    y += buttonHeight + buttonGap;
                    col = 0;
                }
                quickEntry.baseY = y;
                y += buttonHeight + buttonGap;
                continue;
            }
            int x = (col == 0) ? x1 : x2;
            quickEntry.button = Button.builder(Component.translatable("dlc_manager.server.rcon_exec"), btn -> runCommand(quickEntry.command))
                .bounds(x, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(quickEntry.button);
            quickEntry.baseY = y;
            col++;
            if (col >= 2) {
                y += buttonHeight + buttonGap;
                col = 0;
            }
        }

        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 - 40, this.height - 30, 80, 20)
            .build();
        addRenderableWidget(backButton);


    }

    private void runCommand(String command) {
        String host = serverEntry == null ? "" : serverEntry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else {
                    statusText = Component.translatable("dlc_manager.server.rcon_response", response).getString();
                }
            }));
    }

    private void addCommand(String labelKey, String command) {
        quickEntries.add(QuickEntry.command(labelKey, command));
    }

    private void addSeparator(String labelKey) {
        quickEntries.add(QuickEntry.separator(labelKey));
    }

    private static class QuickEntry {
        private final boolean separator;
        private final String labelKey;
        private final String command;
        private Button button;
        private int baseY;
        private boolean visible;

        private QuickEntry(boolean separator, String labelKey, String command) {
            this.separator = separator;
            this.labelKey = labelKey;
            this.command = command;
        }

        private static QuickEntry command(String labelKey, String command) {
            return new QuickEntry(false, labelKey, command);
        }

        private static QuickEntry separator(String labelKey) {
            return new QuickEntry(true, labelKey, "");
        }
    }

    @Override protected void buildPage() {
        initControls();
        var group = section("", "");
        for (QuickEntry item : quickEntries) {
            if (item.separator) group = section(tr(item.labelKey), "");
            else mount(row(group, tr(item.labelKey), item.command), item.button);
        }

    }
    @Override protected void updatePage() { status(statusText); }

}
