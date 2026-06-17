package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DlcPackVersionSelectScreen extends DlcScreenCompat.MouseScroll {
    private final DlcPackManagerScreen parent;
    private final String identifier;
    private final List<String> versions;
    private final String selectedKey;
    private final String installedVersion;

    public DlcPackVersionSelectScreen(
        DlcPackManagerScreen parent,
        String identifier,
        List<String> versions,
        String selectedKey,
        String installedVersion
    ) {
        super(Component.translatable("dlc_manager.pack.version_select_title"));
        this.parent = parent;
        this.identifier = identifier;
        this.versions = versions;
        this.selectedKey = selectedKey;
        this.installedVersion = installedVersion;
    }

    @Override
    protected void init() {
        clearWidgets();
        int buttonWidth = 180;
        int buttonHeight = 20;
        int y = 48;
        for (String versionKey : versions) {
            String version = versionKey.substring(versionKey.lastIndexOf('@') + 1);
            Component label = version.equals(installedVersion)
                ? Component.translatable("dlc_manager.pack.version_installed", version)
                : Component.literal(version);
            Button button = Button.builder(label, ignored -> {
                    parent.selectPackVersion(identifier, versionKey);
                    Minecraft.getInstance().setScreen(parent);
                })
                .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build();
            button.active = !versionKey.equals(selectedKey);
            addRenderableWidget(button);
            y += buttonHeight + 6;
        }
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.pack.back"), ignored -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 - buttonWidth / 2, Math.max(y + 8, this.height - 32), buttonWidth, buttonHeight)
            .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal(identifier), this.width / 2, 32, 0xC8C8C8);
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
