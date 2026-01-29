package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerTcpClient;
import com.cozooo.dlc_manager.client.server.DlcServerVersionUtil;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.ApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DlcServerMismatchScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final String serverBase;
    private final String serverDlc;
    private final String localBase;
    private final String localDlc;
    private final boolean baseMatch;
    private final boolean dlcMatch;
    private final boolean versionMatch;

    private Button syncButton;
    private Button forceConnectButton;
    private Button connectButton;
    private Button cancelButton;
    private volatile boolean working;

    public DlcServerMismatchScreen(Screen parent, DlcServerEntry entry, DlcServerVersionUtil.VersionPair serverVersion, DlcUpdateVersion localVersion) {
        super(Component.translatable("dlc_manager.server.mismatch_title"));
        this.parent = parent;
        this.entry = entry;
        this.serverBase = serverVersion == null ? "" : serverVersion.base;
        this.serverDlc = serverVersion == null ? "" : serverVersion.dlc;
        this.localBase = localVersion == null ? "" : localVersion.getBase();
        this.localDlc = localVersion == null ? "" : localVersion.getDlc();
        this.baseMatch = serverVersion != null && localVersion != null
            && serverVersion.base != null && serverVersion.base.equals(localVersion.getBase());
        this.dlcMatch = serverVersion != null && localVersion != null
            && serverVersion.dlc != null && serverVersion.dlc.equals(localVersion.getDlc());
        this.versionMatch = baseMatch && dlcMatch;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int y = this.height / 2 + 40;
        forceConnectButton = Button.builder(Component.translatable("dlc_manager.server.mismatch_force_connect"), btn -> forceJoin())
            .bounds(centerX - buttonWidth - 6, y - 24, buttonWidth, buttonHeight)
            .build();
        syncButton = Button.builder(Component.translatable("dlc_manager.server.mismatch_sync"), btn -> startSync())
            .bounds(centerX - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        connectButton = Button.builder(Component.translatable("dlc_manager.server.mismatch_connect"), btn -> openConnect())
            .bounds(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight)
            .build();
        cancelButton = Button.builder(Component.translatable("dlc_manager.server.mismatch_cancel"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(forceConnectButton);
        addRenderableWidget(syncButton);
        addRenderableWidget(connectButton);
        addRenderableWidget(cancelButton);
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, 30, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.mismatch_server", serverBase + "+" + serverDlc), centerX, 60, 0xE0E0E0);
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.mismatch_local", localBase + "+" + localDlc), centerX, 75, 0xC8C8C8);
        if (!baseMatch) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.mismatch_base"), centerX, 95, 0xFF6666);
        }
        if (working) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.applying"), centerX, this.height / 2 + 18, 0xC8C8C8);
        }
        if (!versionMatch && syncButton != null && syncButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("dlc_manager.server.mismatch_sync_tip"), mouseX, mouseY);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void updateButtonStates() {
        if (syncButton != null) {
            syncButton.active = !working && versionMatch;
        }
        if (forceConnectButton != null) {
            forceConnectButton.active = !working;
        }
        if (connectButton != null) {
            connectButton.active = !working;
        }
        if (cancelButton != null) {
            cancelButton.active = !working;
        }
    }

    private void startSync() {
        if (working || !versionMatch) {
            return;
        }
        working = true;
        updateButtonStates();
        CompletableFuture
            .supplyAsync(this::syncWithServer)
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                working = false;
                updateButtonStates();
                if (ok != null && ok) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.sync_done"), Component.empty());
                } else {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.sync_failed"), Component.empty());
                }
            }));
    }

    private void forceJoin() {
        if (working) {
            return;
        }
        if (parent instanceof JoinMultiplayerScreen joinScreen) {
            joinSelectedServer(joinScreen);
            return;
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void joinSelectedServer(JoinMultiplayerScreen screen) {
        try {
            var method = screen.getClass().getDeclaredMethod("joinSelectedServer");
            method.setAccessible(true);
            method.invoke(screen);
            return;
        } catch (Throwable ignored) {
            // Fallback below.
        }
        try {
            var method = screen.getClass().getDeclaredMethod("joinSelected");
            method.setAccessible(true);
            method.invoke(screen);
        } catch (Throwable ignored) {
            // Best-effort fallback.
        }
    }

    private boolean syncWithServer() {
        try {
            DlcServerInfo info = DlcServerTcpClient.fetchServerInfo(entry, 8000, getPlayerName(), getPlayerUuid());
            if (info == null) {
                return false;
            }
            Map<String, Boolean> desired = new HashMap<>();
            if (info.items != null) {
                for (DlcServerItem item : info.items) {
                    if (item != null && item.id != null && !item.id.isBlank()) {
                        desired.put(item.id, item.enabled);
                    }
                }
            }
            Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
            List<DlcItem> items = new ArrayList<>();
            for (List<DlcItem> group : grouped.values()) {
                if (group != null) {
                    items.addAll(group);
                }
            }
            for (DlcItem item : items) {
                if (item == null) {
                    continue;
                }
                Boolean enabled = desired.get(item.getId());
                if (enabled != null) {
                    item.setEnabled(enabled);
                }
            }
            DlcApplier.apply(items, ApplyMode.RESTART_ONLY);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openConnect() {
        DlcServerEntry editing = entry == null ? new DlcServerEntry() : entry;
        if (editing.port <= 0) {
            editing.port = DlcPathConfig.getServerPort();
        }
        Minecraft.getInstance().setScreen(new DlcServerEditScreen(this, editing, saved -> {
            DlcServerList.load();
            boolean exists = false;
            for (DlcServerEntry existing : DlcServerList.getEntries()) {
                if (existing != null && existing.getKey().equals(saved.getKey())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                DlcServerList.addEntry(saved);
            }
            DlcServerList.setLastSelectedKey(saved.getKey());
            DlcServerList.save();
            CompletableFuture
                .supplyAsync(() -> fetchInfo(saved))
                .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (info == null) {
                        SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                            Component.translatable("dlc_manager.server.connect_failed"), Component.empty());
                        return;
                    }
                    DlcServerCache.put(saved.getKey(), info);
                    Minecraft.getInstance().setScreen(new DlcServerMaLiLibScreen(this, saved, info));
                }));
        }));
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerTcpClient.fetchServerInfo(entry, 5000, getPlayerName(), getPlayerUuid());
        } catch (Exception ignored) {
            return null;
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
}
