package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.server.DlcRconClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerRconConsoleScreen extends DlcPageScreen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final int port;
    private final String password;

    private final List<String> lines = new ArrayList<>();
    private int scroll;

    private EditBox commandField;
    private Button sendButton;
    private Button backButton;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private boolean draggingScrollbar;

    public DlcServerRconConsoleScreen(Screen parent, DlcServerEntry entry, int port, String password) {
        super(DlcUiPage.SERVER_RCON_CONSOLE, parent, Component.translatable("dlc_manager.server.rcon_console"));
        this.parent = parent;
        this.entry = entry;
        this.port = port;
        this.password = password == null ? "" : password;
    }

    private void initControls() {
        int margin = 20;
        listLeft = margin;
        listRight = this.width - margin;
        listTop = 50;
        int buttonY = this.height - 30;
        listBottom = buttonY - 10;

        int backWidth = 80;
        int sendWidth = 60;
        int gap = 6;
        int backX = margin;
        int sendX = this.width - margin - sendWidth;
        int fieldX = backX + backWidth + gap;
        int fieldWidth = sendX - gap - fieldX;
        if (fieldWidth < 80) {
            fieldWidth = Math.max(80, this.width - margin * 2 - sendWidth - gap);
            fieldX = this.width / 2 - fieldWidth / 2;
            backX = fieldX - backWidth - gap;
        }

        commandField = new EditBox(this.font, fieldX, buttonY, fieldWidth, 20, Component.translatable("dlc_manager.server.rcon_command"));
        commandField.setMaxLength(256);
        sendButton = Button.builder(Component.translatable("dlc_manager.server.rcon_send"), btn -> sendCommand())
            .bounds(sendX, buttonY, sendWidth, 20)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, buttonY, backWidth, 20)
            .build();

        addRenderableWidget(commandField);
        addRenderableWidget(sendButton);
        addRenderableWidget(backButton);
    }

    private void sendCommand() {
        if (commandField == null) {
            return;
        }
        String command = commandField.getValue().trim();
        if (command.isEmpty()) {
            return;
        }
        commandField.setValue("");
        appendLine("> " + command);
        String host = entry == null ? "" : entry.host;
        CompletableFuture
            .supplyAsync(() -> DlcRconClient.execute(host, port, password, command, 15000))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (response == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.rcon_exec_failed"), Component.empty());
                } else {
                    appendResponse(response);
                }
            }));
    }

    private void appendResponse(String response) {
        if (response == null || response.isBlank()) {
            appendLine(Component.translatable("dlc_manager.server.rcon_response", "").getString());
            return;
        }
        String[] parts = response.split("\\r?\\n");
        for (String part : parts) {
            appendLine(part);
        }
    }

    private void appendLine(String line) { if (line != null) lines.add(line); }

    private com.sighs.apricityui.init.Element output;
    private int renderedLines;
    private boolean scrollToEnd;
    @Override protected void buildPage() {
        String draft = commandField == null ? "" : commandField.getValue();
        initControls(); commandField.setValue(draft);
        content.setAttribute("class", "console-content");
        output = node(content, "div", "console-output"); renderedLines = 0;
        var command = node(content, "div", "console-command");
        var field = mount(command, commandField, tr("dlc_manager.server.rcon_command"), "text");
        field.addEventListener("keydown", event -> { if (event instanceof com.sighs.apricityui.event.KeyEvent key && key.key.equals("Enter")) { event.preventDefault(); sendCommand(); } });
        mount(command, sendButton).getElement().setAttribute("class", "button primary");

    }
    @Override protected void updatePage() {
        if (output == null) return;
        if (scrollToEnd) { DlcUiScroll.scrollToEnd(output); scrollToEnd = false; }
        if (renderedLines != lines.size()) {
            boolean follow = renderedLines == 0 || DlcUiScroll.isAtEnd(output, 24);
            while (renderedLines < lines.size()) text(output, lines.get(renderedLines++), "console-line");
            scrollToEnd = follow;
        }
    }

}
