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
        Layout layout = computeLayout();
        int panelWidth = layout.fieldWidth;
        int panelLeft = layout.fieldLeft;
        int startY = layout.startY;
        int rowGap = layout.rowGap;

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

        int buttonY = Math.min(layout.panelTop + layout.panelHeight - 28, otherY + 26);
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.save"), btn -> saveConfig())
            .bounds(panelLeft, buttonY, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.back"), btn -> onClose())
            .bounds(panelLeft + panelWidth - 70, buttonY, 70, 20).build());
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        Layout layout = computeLayout();
        int panelWidth = layout.panelWidth;
        int panelHeight = layout.panelHeight;
        int panelLeft = layout.panelLeft;
        int panelTop = layout.panelTop;
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

    private Layout computeLayout() {
        int margin = 6;
        int panelWidth = Math.min(360, this.width - margin * 2);
        int panelHeight = Math.min(320, this.height - margin * 2);
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = (this.height - panelHeight) / 2;
        int innerPadding = 12;
        int topMargin = 30;
        int bottomMargin = 54;
        int available = Math.max(0, panelHeight - topMargin - bottomMargin);
        int rowGap = Math.max(26, Math.min(36, available / 4));
        int startY = panelTop + topMargin;
        int fieldLeft = panelLeft + innerPadding;
        int fieldWidth = Math.max(120, panelWidth - innerPadding * 2);
        return new Layout(panelLeft, panelTop, panelWidth, panelHeight, startY, rowGap, fieldLeft, fieldWidth);
    }

    private static final class Layout {
        private final int panelLeft;
        private final int panelTop;
        private final int panelWidth;
        private final int panelHeight;
        private final int startY;
        private final int rowGap;
        private final int fieldLeft;
        private final int fieldWidth;

        private Layout(int panelLeft, int panelTop, int panelWidth, int panelHeight, int startY, int rowGap, int fieldLeft, int fieldWidth) {
            this.panelLeft = panelLeft;
            this.panelTop = panelTop;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.startY = startY;
            this.rowGap = rowGap;
            this.fieldLeft = fieldLeft;
            this.fieldWidth = fieldWidth;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
