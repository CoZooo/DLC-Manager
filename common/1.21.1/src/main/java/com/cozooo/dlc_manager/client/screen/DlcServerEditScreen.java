package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcServerEditScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry editing;
    private final java.util.function.Consumer<DlcServerEntry> onSave;

    private EditBox nameField;
    private EditBox hostField;
    private EditBox portField;
    private EditBox passwordField;
    private Button saveButton;
    private Button cancelButton;

    public DlcServerEditScreen(Screen parent, DlcServerEntry editing, java.util.function.Consumer<DlcServerEntry> onSave) {
        super(Component.translatable("dlc_manager.server.edit_title"));
        this.parent = parent;
        this.editing = editing;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(260, this.width - 40);
        int fieldHeight = 18;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int gap = 22;

        nameField = new EditBox(this.font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, Component.empty());
        hostField = new EditBox(this.font, centerX - fieldWidth / 2, startY + gap, fieldWidth, fieldHeight, Component.empty());
        portField = new EditBox(this.font, centerX - fieldWidth / 2, startY + gap * 2, fieldWidth, fieldHeight, Component.empty());
        passwordField = new EditBox(this.font, centerX - fieldWidth / 2, startY + gap * 3, fieldWidth, fieldHeight, Component.empty());

        if (editing != null) {
            if (editing.name != null) {
                nameField.setValue(editing.name);
            }
            if (editing.host != null) {
                hostField.setValue(editing.host);
            }
            if (editing.port > 0) {
                portField.setValue(String.valueOf(editing.port));
            }
            if (editing.password != null) {
                passwordField.setValue(editing.password);
            }
        } else {
            portField.setValue(String.valueOf(DlcPathConfig.getServerPort()));
        }

        addRenderableWidget(nameField);
        addRenderableWidget(hostField);
        addRenderableWidget(portField);
        addRenderableWidget(passwordField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = this.height / 2 + 40;
        saveButton = Button.builder(Component.translatable("dlc_manager.server.save"), btn -> handleSave())
            .bounds(centerX - buttonWidth - 6, buttonY, buttonWidth, buttonHeight)
            .build();
        cancelButton = Button.builder(Component.translatable("gui.cancel"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX + 6, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(saveButton);
        addRenderableWidget(cancelButton);
        updateSaveButtonState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, this.height / 2 - 90, 0xFFFFFF);
        drawFieldLabel(guiGraphics, "dlc_manager.server.name", nameField);
        drawFieldLabel(guiGraphics, "dlc_manager.server.host", hostField);
        drawFieldLabel(guiGraphics, "dlc_manager.server.port", portField);
        drawFieldLabel(guiGraphics, "dlc_manager.server.password", passwordField);
        updateSaveButtonState();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (hostField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (portField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (passwordField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameField.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (hostField.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (portField.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (passwordField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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

    private void updateSaveButtonState() {
        if (saveButton != null) {
            saveButton.active = hostField != null && !hostField.getValue().isBlank();
        }
    }

    private void handleSave() {
        DlcServerEntry entry = editing == null ? new DlcServerEntry() : editing;
        entry.name = nameField.getValue().trim();
        entry.host = hostField.getValue().trim();
        entry.password = passwordField.getValue().trim();
        entry.useTcp = true;
        int port = DlcPathConfig.getServerPort();
        try {
            port = Integer.parseInt(portField.getValue().trim());
        } catch (NumberFormatException ignored) {
        }
        entry.port = port;
        if (onSave != null) {
            onSave.accept(entry);
        }
        Minecraft.getInstance().setScreen(parent);
    }
}
