package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcPathConfigScreen extends Screen {
    private final Screen parent;

    private EditBox dlcRootField;
    private EditBox taczRootField;
    private EditBox resourcepackRootField;
    private EditBox datapackRootField;
    private EditBox otherRootField;
    private String dlcRootValue = "";
    private String taczRootValue = "";
    private String resourcepackRootValue = "";
    private String datapackRootValue = "";
    private String otherRootValue = "";

    public DlcPathConfigScreen(Screen parent) {
        super(Component.translatable("dlc_manager.config_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = 260;
        int panelLeft = (this.width - panelWidth) / 2;
        int startY = 58;
        int rowGap = 36;

        dlcRootField = new EditBox(this.font, panelLeft, startY, panelWidth, 18, Component.empty());
        dlcRootField.setMaxLength(1024);
        dlcRootField.setValue(dlcRootValue.isBlank() ? DlcPathConfig.getDlcRootRaw() : dlcRootValue);
        addRenderableWidget(dlcRootField);

        int taczY = startY + rowGap;
        taczRootField = new EditBox(this.font, panelLeft, taczY, panelWidth, 18, Component.empty());
        taczRootField.setMaxLength(1024);
        taczRootField.setValue(taczRootValue.isBlank() ? DlcPathConfig.getTaczRootRaw() : taczRootValue);
        addRenderableWidget(taczRootField);

        int resourceY = startY + rowGap * 2;
        resourcepackRootField = new EditBox(this.font, panelLeft, resourceY, panelWidth, 18, Component.empty());
        resourcepackRootField.setMaxLength(1024);
        resourcepackRootField.setValue(resourcepackRootValue.isBlank() ? DlcPathConfig.getResourcepackRootRaw() : resourcepackRootValue);
        addRenderableWidget(resourcepackRootField);

        int dataY = startY + rowGap * 3;
        datapackRootField = new EditBox(this.font, panelLeft, dataY, panelWidth, 18, Component.empty());
        datapackRootField.setMaxLength(1024);
        datapackRootField.setValue(datapackRootValue.isBlank() ? DlcPathConfig.getDatapackRootRaw() : datapackRootValue);
        addRenderableWidget(datapackRootField);

        int otherY = startY + rowGap * 4;
        otherRootField = new EditBox(this.font, panelLeft, otherY, panelWidth, 18, Component.empty());
        otherRootField.setMaxLength(1024);
        otherRootField.setValue(otherRootValue.isBlank() ? DlcPathConfig.getOtherRootRaw() : otherRootValue);
        addRenderableWidget(otherRootField);

        int buttonY = otherY + 32;
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.save"), btn -> saveConfig())
            .bounds(panelLeft, buttonY, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.back"), btn -> onClose())
            .bounds(panelLeft + panelWidth - 70, buttonY, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        int panelWidth = 300;
        int panelHeight = 250;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = 34;
        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC101215);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, 0xFF2A2F36);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF2A2F36);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 8, 0xFFFFFF);
        drawFieldLabel(guiGraphics, dlcRootField, Component.translatable("dlc_manager.config.dlc_root"));
        drawFieldLabel(guiGraphics, taczRootField, Component.translatable("dlc_manager.config.tacz_root"));
        drawFieldLabel(guiGraphics, resourcepackRootField, Component.translatable("dlc_manager.config.resourcepack_root"));
        drawFieldLabel(guiGraphics, datapackRootField, Component.translatable("dlc_manager.config.datapack_root"));
        drawFieldLabel(guiGraphics, otherRootField, Component.translatable("dlc_manager.config.other_root"));

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void saveConfig() {
        DlcPathConfig.setDlcRoot(dlcRootField.getValue().trim());
        DlcPathConfig.setTaczRoot(taczRootField.getValue().trim());
        DlcPathConfig.setResourcepackRoot(resourcepackRootField.getValue().trim());
        DlcPathConfig.setDatapackRoot(datapackRootField.getValue().trim());
        DlcPathConfig.setOtherRoot(otherRootField.getValue().trim());
        DlcPathConfig.save();
        onClose();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        dlcRootValue = dlcRootField != null ? dlcRootField.getValue() : dlcRootValue;
        taczRootValue = taczRootField != null ? taczRootField.getValue() : taczRootValue;
        resourcepackRootValue = resourcepackRootField != null ? resourcepackRootField.getValue() : resourcepackRootValue;
        datapackRootValue = datapackRootField != null ? datapackRootField.getValue() : datapackRootValue;
        if (otherRootField != null) {
            otherRootValue = otherRootField.getValue();
        }
        super.resize(minecraft, width, height);
    }

    private void drawFieldLabel(GuiGraphics guiGraphics, EditBox field, Component label) {
        if (field == null) {
            return;
        }
        int x = field.getX();
        int y = field.getY() - 10;
        guiGraphics.drawString(this.font, label, x, y, 0xA0A0A0, false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
