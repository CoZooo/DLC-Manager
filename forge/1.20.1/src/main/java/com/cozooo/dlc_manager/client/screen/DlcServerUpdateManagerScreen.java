package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerTcpClient;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateState;
import com.cozooo.dlc_manager.client.server.DlcServerTcpClient.UpdateResponse;
import com.cozooo.dlc_manager.client.update.DlcUpdateFilePicker;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcServerUpdateManagerScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private Button importButton;
    private Button urlButton;
    private Button backButton;
    private int scrollIndex;
    private List<DlcUpdateMeta> rows = List.of();
    private List<RowAction> rowActions = List.of();
    private List<RowType> rowTypes = List.of();
    private List<String> actionOld = List.of();
    private List<String> actionNew = List.of();
    private final List<Button> actionButtons = new ArrayList<>();
    private String currentVersion = "";
    private String baseVersion = "";
    private String rollbackFrom;
    private boolean rolledBackMode;
    private boolean hasVisibleUpdates;
    private boolean loading;
    private boolean loadFailed;
    private boolean applying;

    private enum RowAction {
        NONE,
        ROLLBACK,
        REUPDATE,
        UPDATE
    }

    private enum RowType {
        UPDATE
    }

    public DlcServerUpdateManagerScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.update.manager_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        refreshData();
    }

    void refreshData() {
        loading = true;
        loadFailed = false;
        applying = false;
        rows = List.of();
        rowActions = List.of();
        rowTypes = List.of();
        actionOld = List.of();
        actionNew = List.of();
        buildWidgets();
        new Thread(() -> {
            DlcServerUpdateState state;
            try {
                state = DlcServerTcpClient.fetchUpdateState(entry, 8000, getPlayerName(), getPlayerUuid());
            } catch (Exception ex) {
                state = null;
            }
            DlcServerUpdateState finalState = state;
            Minecraft.getInstance().execute(() -> {
                loading = false;
                if (finalState == null) {
                    loadFailed = true;
                    buildWidgets();
                    return;
                }
                loadFailed = false;
                baseVersion = finalState.baseVersion == null ? "" : finalState.baseVersion;
                currentVersion = finalState.dlcVersion == null ? "" : finalState.dlcVersion;
                rollbackFrom = finalState.rollbackFrom;
                rolledBackMode = rollbackFrom != null && !rollbackFrom.isBlank() && !rollbackFrom.equals(currentVersion);
                buildRows(finalState);
                buildWidgets();
            });
        }, "dlc-server-update-refresh").start();
    }

    private void buildRows(DlcServerUpdateState state) {
        List<DlcUpdateMeta> baseHistory = filterVisible(state.history);
        List<DlcUpdateMeta> list = new ArrayList<>();
        List<RowAction> actions = new ArrayList<>();
        List<RowType> types = new ArrayList<>();
        List<String> olds = new ArrayList<>();
        List<String> news = new ArrayList<>();
        hasVisibleUpdates = false;

        for (DlcUpdateMeta meta : baseHistory) {
            if (meta == null) {
                continue;
            }
            list.add(meta);
            olds.add(meta.getDlcOld());
            news.add(meta.getDlcNew());
            if (rolledBackMode && DlcUpdateService.compareVersions(meta.getDlcNew(), currentVersion) > 0) {
                actions.add(RowAction.REUPDATE);
            } else {
                actions.add(RowAction.ROLLBACK);
            }
            types.add(RowType.UPDATE);
        }

        if (!rolledBackMode && state.updates != null) {
            for (DlcServerUpdateInfo info : state.updates) {
                if (info == null || info.hidden) {
                    continue;
                }
                DlcUpdateMeta meta = new DlcUpdateMeta();
                meta.setDlcOld(info.dlcOld);
                meta.setDlcNew(info.dlcNew);
                list.add(meta);
                olds.add(info.dlcOld);
                news.add(info.dlcNew);
                actions.add(RowAction.UPDATE);
                types.add(RowType.UPDATE);
                hasVisibleUpdates = true;
            }
        }

        rows = list;
        rowActions = actions;
        rowTypes = types;
        actionOld = olds;
        actionNew = news;
    }

    private List<DlcUpdateMeta> filterVisible(List<DlcUpdateMeta> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<DlcUpdateMeta> out = new ArrayList<>();
        for (DlcUpdateMeta meta : input) {
            if (meta != null && !meta.isHidden()) {
                out.add(meta);
            }
        }
        return out;
    }

    private void buildWidgets() {
        clearWidgets();
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        int spacing = 10;
        int totalWidth = buttonWidth * 3 + spacing * 2;
        int startX = Math.max(20, (this.width - totalWidth) / 2);
        importButton = Button.builder(Component.translatable("dlc_manager.update.import"), btn -> importUpdate())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        int urlX = startX + buttonWidth + spacing;
        urlButton = Button.builder(Component.translatable("dlc_manager.update.import_url"),
                btn -> Minecraft.getInstance().setScreen(new DlcServerUpdateUrlScreen(this, entry)))
            .bounds(urlX, y, buttonWidth, buttonHeight)
            .build();
        int backX = urlX + buttonWidth + spacing;
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(backButton);

        actionButtons.clear();
        for (int i = 0; i < rows.size(); i++) {
            RowAction action = i < rowActions.size() ? rowActions.get(i) : RowAction.NONE;
            Component label = switch (action) {
                case REUPDATE -> Component.translatable("dlc_manager.update.reapply");
                case UPDATE -> Component.translatable("dlc_manager.update.apply");
                default -> Component.translatable("dlc_manager.update.rollback");
            };
            int buttonW = (action == RowAction.REUPDATE || action == RowAction.UPDATE) ? 80 : 50;
            Button actionButton = Button.builder(label, btn -> {
                int idx = actionButtons.indexOf(btn);
                if (idx < 0 || idx >= rowActions.size()) {
                    return;
                }
                RowAction chosen = rowActions.get(idx);
                String oldVersion = idx < actionOld.size() ? actionOld.get(idx) : null;
                String newVersion = idx < actionNew.size() ? actionNew.get(idx) : null;
                if (chosen == RowAction.ROLLBACK) {
                    confirmRollback(oldVersion);
                } else if (chosen == RowAction.REUPDATE) {
                    confirmReupdate(oldVersion, newVersion);
                } else if (chosen == RowAction.UPDATE) {
                    confirmApply(oldVersion, newVersion);
                }
            }).bounds(-10000, -10000, buttonW, 16).build();
            actionButton.visible = false;
            addRenderableWidget(actionButton);
            actionButtons.add(actionButton);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 16;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.base", baseVersion), centerX, y, 0xC8C8C8);
        y += 14;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.current", currentVersion), centerX, y, 0xC8C8C8);
        y += 14;
        if (rolledBackMode) {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.update.rollback_info", rollbackFrom, currentVersion),
                centerX, y, 0xE6E6E6);
            y += 12;
        }
        if (hasVisibleUpdates) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.desc"), centerX, y, 0xE6E6E6);
            y += 12;
        }
        drawHistoryList(guiGraphics, y, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawHistoryList(GuiGraphics guiGraphics, int startY, int mouseX, int mouseY) {
        int listTop = startY;
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = rows.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        if (loading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.update_checking"),
                this.width / 2, (listTop + listBottom) / 2, 0xC8C8C8);
            return;
        }
        if (loadFailed) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.update_failed"),
                this.width / 2, (listTop + listBottom) / 2, 0xFF7777);
            return;
        }
        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.update_empty"),
                this.width / 2, (listTop + listBottom) / 2, 0xC8C8C8);
            return;
        }
        int y = listTop;
        int visibleEnd = Math.min(totalLines, scrollIndex + maxVisible);
        for (int i = scrollIndex; i < visibleEnd; i++) {
            DlcUpdateMeta meta = rows.get(i);
            String label = meta.getDlcOld() + " -> " + meta.getDlcNew();
            if (i < rowActions.size() && rowActions.get(i) == RowAction.REUPDATE) {
                label = label + " " + Component.translatable("dlc_manager.update.rolled_back").getString();
            }
            guiGraphics.drawString(this.font, label, 24, y + 6, 0xE6E6E6, false);
            int buttonIndex = i;
            int buttonWidth = buttonIndex < rowActions.size()
                && (rowActions.get(buttonIndex) == RowAction.REUPDATE || rowActions.get(buttonIndex) == RowAction.UPDATE)
                ? 80
                : 50;
            int buttonHeight = 16;
            int buttonX = this.width - 24 - buttonWidth;
            int buttonY = y + 2;
            if (buttonIndex >= 0 && buttonIndex < actionButtons.size()) {
                Button actionButton = actionButtons.get(buttonIndex);
                RowAction action = buttonIndex < rowActions.size() ? rowActions.get(buttonIndex) : RowAction.NONE;
                boolean enabled = action != RowAction.NONE && !applying;
                actionButton.visible = enabled;
                actionButton.active = enabled;
                if (enabled) {
                    actionButton.setX(buttonX);
                    actionButton.setY(buttonY);
                } else {
                    actionButton.setX(-10000);
                    actionButton.setY(-10000);
                }
            }
            y += lineHeight;
        }
        for (int i = 0; i < actionButtons.size(); i++) {
            if (i < scrollIndex || i >= visibleEnd) {
                Button actionButton = actionButtons.get(i);
                actionButton.visible = false;
                actionButton.setX(-10000);
                actionButton.setY(-10000);
            }
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = 20 + 16 + 14
            + (rolledBackMode ? 12 : 0)
            + (hasVisibleUpdates ? 12 : 0);
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = rows.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = delta > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void confirmRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            return;
        }
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_rollback", targetVersion);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startRollback(targetVersion);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void confirmReupdate(String dlcOld, String dlcNew) {
        if (dlcOld == null || dlcNew == null) {
            return;
        }
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_reapply", dlcOld, dlcNew);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startApply(dlcOld, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void confirmApply(String dlcOld, String dlcNew) {
        if (dlcOld == null || dlcNew == null) {
            return;
        }
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_apply");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startApply(dlcOld, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void startRollback(String targetVersion) {
        setApplying(true);
        new Thread(() -> {
            UpdateResponse result;
            try {
                result = DlcServerTcpClient.rollbackUpdate(entry, 8000, targetVersion, getPlayerName(), getPlayerUuid());
            } catch (Exception ex) {
                result = null;
            }
            UpdateResponse finalResult = result;
            Minecraft.getInstance().execute(() -> handleUpdateResult(finalResult));
        }, "dlc-server-update-rollback").start();
    }

    private void startApply(String dlcOld, String dlcNew) {
        setApplying(true);
        new Thread(() -> {
            UpdateResponse result;
            try {
                result = DlcServerTcpClient.applyUpdate(entry, 8000, dlcOld, dlcNew, getPlayerName(), getPlayerUuid());
            } catch (Exception ex) {
                result = null;
            }
            UpdateResponse finalResult = result;
            Minecraft.getInstance().execute(() -> handleUpdateResult(finalResult));
        }, "dlc-server-update-apply").start();
    }

    private void handleUpdateResult(UpdateResponse result) {
        setApplying(false);
        if (result == null || !result.ok) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
            return;
        }
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.server.update.restart");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                restartServer();
            } else {
                refreshData();
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.restart_now"), Component.translatable("dlc_manager.restart_later")));
    }

    private void restartServer() {
        new Thread(() -> {
            try {
                DlcServerTcpClient.restartServer(entry, 8000, getPlayerName(), getPlayerUuid());
            } catch (Exception ignored) {
                // Best-effort.
            }
            Minecraft.getInstance().execute(() -> {
                refreshData();
                Minecraft.getInstance().setScreen(this);
            });
        }, "dlc-server-restart").start();
    }

    private void setApplying(boolean value) {
        applying = value;
        if (importButton != null) {
            importButton.active = !value;
        }
        if (urlButton != null) {
            urlButton.active = !value;
        }
        if (backButton != null) {
            backButton.active = !value;
        }
        for (Button button : actionButtons) {
            button.active = !value;
        }
    }

    private void importUpdate() {
        if (applying || loading) {
            return;
        }
        Path zip = DlcUpdateFilePicker.pickAnyFile(Component.translatable("dlc_manager.update.import").getString());
        if (zip == null) {
            return;
        }
        setApplying(true);
        new Thread(() -> {
            boolean ok;
            try {
                ok = DlcServerTcpClient.importUpdatePackage(entry, 8000, zip, getPlayerName(), getPlayerUuid());
            } catch (Exception ex) {
                ok = false;
            }
            boolean success = ok;
            Minecraft.getInstance().execute(() -> {
                setApplying(false);
                if (!success) {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
                } else {
                    refreshData();
                    Minecraft.getInstance().setScreen(this);
                }
            });
        }, "dlc-server-update-import").start();
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 18;
        int barWidth = 6;
        int barHeight = Math.max(20, (bottom - top) * maxVisible / Math.max(1, totalLines));
        int trackHeight = bottom - top;
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
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
