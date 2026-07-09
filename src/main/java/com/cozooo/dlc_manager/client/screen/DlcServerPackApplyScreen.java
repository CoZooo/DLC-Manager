package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerPackApplyStatus;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerPackApplyScreen extends Screen {
    private static final int TIMEOUT_MS = 8000;
    private static final int STATUS_TIMEOUT_MS = 3000;

    private final DlcServerPackManagerScreen manager;
    private final DlcServerEntry entry;
    private final List<String> order;
    private final List<String> uninstallIdentifiers;
    private final List<String> installArchives;
    private volatile DlcServerPackApplyStatus status;
    private volatile Component statusText = Component.translatable("dlc_manager.pack.applying");
    private volatile Component statusDetail;
    private volatile boolean running;
    private volatile boolean finished;
    private volatile boolean canceling;
    private volatile boolean paused;
    private volatile boolean pausing;
    private Button cancelButton;
    private Button backButton;

    public DlcServerPackApplyScreen(DlcServerPackManagerScreen manager, DlcServerEntry entry, List<String> order,
                                    List<String> uninstallIdentifiers, List<String> installArchives) {
        super(Component.translatable("dlc_manager.server.pack.manager_title"));
        this.manager = manager;
        this.entry = entry;
        this.order = order;
        this.uninstallIdentifiers = uninstallIdentifiers;
        this.installArchives = installArchives;
    }

    @Override
    protected void init() {
        int buttonWidth = 104;
        int gap = 8;
        int y = this.height - 28;
        int startX = this.width / 2 - buttonWidth - gap / 2;
        cancelButton = Button.builder(Component.translatable("dlc_manager.pack.pause_install"), button -> togglePauseApply())
            .bounds(startX, y, buttonWidth, 20)
            .build();
        backButton = Button.builder(Component.translatable("dlc_manager.pack.cancel_install"), button -> cancelApply())
            .bounds(startX + buttonWidth + gap, y, buttonWidth, 20)
            .build();
        addRenderableWidget(cancelButton);
        addRenderableWidget(backButton);
        if (!running && !finished) {
            startApply();
        }
    }

    private void startApply() {
        running = true;
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return DlcServerConnection.startPackApply(entry, TIMEOUT_MS, order, uninstallIdentifiers, installArchives);
                } catch (Exception ignored) {
                    return null;
                }
            })
            .whenComplete((started, throwable) -> {
                if (started == null) {
                    finish(false, "start failed");
                    return;
                }
                updateStatus(started);
                startPolling();
            });
    }

    private void startPolling() {
        Thread thread = new Thread(() -> {
            while (running) {
                DlcServerPackApplyStatus next;
                try {
                    next = DlcServerConnection.fetchPackApplyStatus(entry, STATUS_TIMEOUT_MS);
                } catch (Exception ignored) {
                    next = null;
                }
                if (next != null) {
                    updateStatus(next);
                    if (next.finished) {
                        finish(next.success, next.error == null ? next.message : next.error);
                        break;
                    }
                }
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }, "dlc-server-pack-apply-poll");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateStatus(DlcServerPackApplyStatus next) {
        status = next;
        paused = next.paused;
        if (paused) {
            pausing = false;
        }
        String message = next.message;
        statusText = formatRemoteStatus(message);
        statusDetail = operationDetailFor(next.detail);
        Minecraft.getInstance().execute(this::updateButtonState);
    }

    private Component formatRemoteStatus(String message) {
        if (message == null || message.isBlank()) {
            return Component.translatable("dlc_manager.pack.applying");
        }
        return message.startsWith("dlc_manager.") ? Component.translatable(message) : DlcTextFormatter.parse(message);
    }

    private void togglePauseApply() {
        if (canceling || finished) {
            return;
        }
        if (paused) {
            paused = false;
            pausing = false;
            statusText = Component.translatable("dlc_manager.pack.applying");
            updateButtonState();
            CompletableFuture.runAsync(() -> {
                try {
                    DlcServerConnection.resumePackApply(entry, TIMEOUT_MS);
                } catch (Exception ignored) {
                }
            });
            return;
        }
        pausing = true;
        statusText = Component.translatable("dlc_manager.pack.pausing");
        updateButtonState();
        CompletableFuture.runAsync(() -> {
            try {
                DlcServerConnection.pausePackApply(entry, TIMEOUT_MS);
            } catch (Exception ignored) {
            }
        });
    }

    private void cancelApply() {
        if (canceling || finished) {
            return;
        }
        canceling = true;
        statusText = Component.translatable("dlc_manager.pack.canceling");
        updateButtonState();
        CompletableFuture.runAsync(() -> {
            try {
                DlcServerConnection.cancelPackApply(entry, TIMEOUT_MS);
            } catch (Exception ignored) {
            }
        });
    }

    private void finish(boolean success, String message) {
        running = false;
        finished = true;
        Minecraft.getInstance().execute(() -> {
            if (cancelButton != null) {
                cancelButton.active = false;
            }
            if (backButton != null) {
                backButton.active = false;
            }
            manager.finishServerPackApply(success, message);
            if (success) {
                manager.showServerRestartPrompt();
            } else {
                Minecraft.getInstance().setScreen(manager);
            }
        });
    }

    private void updateButtonState() {
        if (cancelButton != null) {
            if (paused) {
                cancelButton.setMessage(Component.translatable("dlc_manager.pack.continue_install"));
            } else if (pausing) {
                cancelButton.setMessage(Component.translatable("dlc_manager.pack.pausing"));
            } else {
                cancelButton.setMessage(Component.translatable("dlc_manager.pack.pause_install"));
            }
            cancelButton.active = !canceling && !finished;
        }
        if (backButton != null) {
            backButton.setMessage(canceling ? Component.translatable("dlc_manager.pack.canceling") : Component.translatable("dlc_manager.pack.cancel_install"));
            backButton.active = !canceling && !finished;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        renderOperation(guiGraphics);
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderOperation(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        int y = this.height / 2 - 44;
        float progress = status == null ? -1.0F : status.progress;
        String progressText = progress >= 0.0F
            ? Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * 100.0F) + "%"
            : "...";
        guiGraphics.drawCenteredString(this.font, formatOperationTitle(statusText, progress, progressText), centerX, y, 0xE6E6E6);
        y += 24;
        drawProgress(guiGraphics, centerX - 120, y, 240, 8, progress);
        guiGraphics.drawCenteredString(this.font, Component.literal(progressText), centerX, y + 14, 0xC8C8C8);
        if (statusDetail != null) {
            guiGraphics.drawCenteredString(this.font, statusDetail, centerX, y + 28, 0x9A9A9A);
        }
    }

    private void drawProgress(GuiGraphics guiGraphics, int x, int y, int width, int height, float value) {
        int filled = value >= 0.0F ? Math.round(width * Math.max(0.0F, Math.min(1.0F, value))) : 0;
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        if (value < 0.0F) {
            int pulse = (int) ((Util.getMillis() / 12L) % width);
            guiGraphics.fill(x + Math.min(pulse, width - 24), y, x + Math.min(pulse + 24, width), y + height, 0xFF66CCFF);
        } else {
            guiGraphics.fill(x, y, x + filled, y + height, 0xFF66CCFF);
        }
    }

    private Component formatOperationTitle(Component message, float progress, String progressText) {
        String text = message.getString().replaceAll("[.。…]+$", "");
        return progress >= 0.0F ? Component.literal(text + "：" + progressText) : Component.literal(text + "...");
    }

    private Component operationDetailFor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "dlc_manager.pack.selecting_source".equals(value)) {
            return null;
        }
        if (value.startsWith("Downloading ")) {
            return Component.translatable("dlc_manager.pack.downloading_file", value.substring("Downloading ".length()));
        }
        if (value.startsWith("Resolved ")) {
            return Component.translatable("dlc_manager.pack.resolved_file", value.substring("Resolved ".length()));
        }
        if (value.startsWith("Installing ")) {
            return Component.translatable("dlc_manager.pack.installing", value.substring("Installing ".length()));
        }
        if (value.startsWith("Uninstalling installed DLC items")) {
            return Component.translatable("dlc_manager.pack.operation_uninstalling");
        }
        if (value.startsWith("Uninstalling ")) {
            return Component.translatable("dlc_manager.pack.uninstalling", value.substring("Uninstalling ".length()));
        }
        if (value.startsWith("Restoring ")) {
            return Component.translatable("dlc_manager.pack.restoring_file", value.substring("Restoring ".length()));
        }
        if (value.startsWith("Preparing ")) {
            return Component.translatable("dlc_manager.pack.preparing_pack", value.substring("Preparing ".length()));
        }
        if (value.startsWith("Backing up ")) {
            return Component.translatable("dlc_manager.pack.backing_up_pack", value.substring("Backing up ".length()));
        }
        if ("Applying runtime changes".equals(value)) {
            return Component.translatable("dlc_manager.pack.applying_runtime");
        }
        if ("Writing metadata".equals(value)) {
            return Component.translatable("dlc_manager.pack.writing_metadata");
        }
        if (value.startsWith("Installed ")) {
            return Component.translatable("dlc_manager.pack.installed_pack", value.substring("Installed ".length()));
        }
        if (value.startsWith("Uninstalled ")) {
            return Component.translatable("dlc_manager.pack.uninstalled_pack", value.substring("Uninstalled ".length()));
        }
        if (value.startsWith("dlc_manager.")) {
            return Component.translatable(value);
        }
        return DlcTextFormatter.parse(value);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return finished;
    }
}
