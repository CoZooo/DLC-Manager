package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

public class DlcAlarmConfirmScreen extends Screen {
    private final Screen parent;
    private final String tip;
    private final Runnable onConfirm;
    private Button confirmButton;
    private List<FormattedCharSequence> tipLines = List.of();
    private int checkboxX;
    private int checkboxY;
    private int checkboxSize = 12;
    private int checkboxTextWidth;
    private boolean acknowledged;

    public DlcAlarmConfirmScreen(Screen parent, String tip, Runnable onConfirm) {
        super(Component.translatable("dlc_manager.alarm.title"));
        this.parent = parent;
        this.tip = tip == null ? "" : tip;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int maxWidth = Math.min(420, Math.max(180, this.width - 80));
        this.tipLines = wrapTip(maxWidth);

        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonGap = 10;
        int totalWidth = buttonWidth * 2 + buttonGap;
        int leftX = (this.width - totalWidth) / 2;
        int buttonY = this.height - 36;

        this.confirmButton = Button.builder(Component.translatable("dlc_manager.alarm.confirm"), btn -> handleConfirm())
            .bounds(leftX, buttonY, buttonWidth, buttonHeight)
            .build();
        this.confirmButton.active = acknowledged;
        addRenderableWidget(this.confirmButton);

        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.alarm.cancel"),
                btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(leftX + buttonWidth + buttonGap, buttonY, buttonWidth, buttonHeight)
            .build());

        Component checkboxText = Component.translatable("dlc_manager.alarm.read_ack");
        this.checkboxTextWidth = this.font.width(checkboxText);
        int totalCheckboxWidth = checkboxSize + 6 + checkboxTextWidth;
        this.checkboxX = (this.width - totalCheckboxWidth) / 2;
        this.checkboxY = buttonY - 24;
    }

    private void handleConfirm() {
        if (!acknowledged) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(parent);
        if (onConfirm != null) {
            minecraft.execute(onConfirm);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideCheckbox(mouseX, mouseY)) {
            acknowledged = !acknowledged;
            if (confirmButton != null) {
                confirmButton.active = acknowledged;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInsideCheckbox(double mouseX, double mouseY) {
        int totalWidth = checkboxSize + 6 + checkboxTextWidth;
        int hitHeight = Math.max(checkboxSize, this.font.lineHeight + 2);
        return mouseX >= checkboxX && mouseX < checkboxX + totalWidth
            && mouseY >= checkboxY && mouseY < checkboxY + hitHeight;
    }

    private List<FormattedCharSequence> wrapTip(int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (tip.isBlank()) {
            return lines;
        }
        for (String line : tip.split("\\R", -1)) {
            if (line.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(this.font.split(Component.literal(line), maxWidth));
            }
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int titleY = 36;
        Component title = Component.translatable("dlc_manager.alarm.title")
            .withStyle(style -> style.withBold(true).withColor(0xFF5555));
        float titleScale = 1.25f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        guiGraphics.drawCenteredString(this.font, title, Math.round(centerX / titleScale), Math.round(titleY / titleScale), 0xFF5555);
        guiGraphics.pose().popPose();

        int lineHeight = this.font.lineHeight + 2;
        int tipTotalHeight = tipLines.isEmpty() ? 0 : (tipLines.size() * lineHeight - 2);
        int tipAreaTop = 72;
        int tipAreaBottom = checkboxY - 12;
        int tipAreaCenter = (tipAreaTop + tipAreaBottom) / 2;
        int tipY = Math.max(tipAreaTop, tipAreaCenter - tipTotalHeight / 2);
        for (FormattedCharSequence line : tipLines) {
            if (tipY + this.font.lineHeight > tipAreaBottom) {
                break;
            }
            guiGraphics.drawCenteredString(this.font, line, centerX, tipY, 0xE6E6E6);
            tipY += lineHeight;
        }

        int boxColor = acknowledged ? 0xFF66FF66 : 0xFFB0B0B0;
        guiGraphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, 0xFF111111);
        guiGraphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + 1, boxColor);
        guiGraphics.fill(checkboxX, checkboxY + checkboxSize - 1, checkboxX + checkboxSize, checkboxY + checkboxSize, boxColor);
        guiGraphics.fill(checkboxX, checkboxY, checkboxX + 1, checkboxY + checkboxSize, boxColor);
        guiGraphics.fill(checkboxX + checkboxSize - 1, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, boxColor);
        if (acknowledged) {
            int innerPadding = 3;
            guiGraphics.fill(checkboxX + innerPadding, checkboxY + innerPadding,
                checkboxX + checkboxSize - innerPadding, checkboxY + checkboxSize - innerPadding, 0xFF66FF66);
        }
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.alarm.read_ack"),
            checkboxX + checkboxSize + 6, checkboxY + (checkboxSize - this.font.lineHeight) / 2, 0xE6E6E6, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
