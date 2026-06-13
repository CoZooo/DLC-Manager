package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.update.DlcUpdateFilePicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerFileManagerScreen extends DlcScreenCompat.MouseScroll {
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private final DlcServerEntry entry;
    private EditBox pathField;
    private List<DlcServerFileEntry> entries = new ArrayList<>();
    private int scrollIndex;
    private int selectedIndex = -1;
    private int lastClickIndex = -1;
    private long lastClickTime;
    private Button refreshButton;
    private Button upButton;
    private Button uploadButton;
    private Button saveButton;
    private Button deleteButton;
    private Button backButton;
    private boolean loading;
    private boolean draggingScroll;
    private int scrollDragOffset;

    public DlcServerFileManagerScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.server.files_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = this.width - 120;
        if (fieldWidth < 160) {
            fieldWidth = 160;
        }
        pathField = new EditBox(this.font, centerX - fieldWidth / 2, 36, fieldWidth, 20, Component.translatable("dlc_manager.server.files_path"));
        pathField.setMaxLength(512);
        addRenderableWidget(pathField);

        int panelLeft = 24;
        int panelRight = this.width - 24;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int topButtonsY = 62;
        int gap = (panelRight - panelLeft - buttonWidth * 4) / 5;
        if (gap < 6) {
            gap = 6;
        }
        int startX = panelLeft + gap;
        refreshButton = Button.builder(Component.translatable("dlc_manager.server.files_refresh"), btn -> refresh())
            .bounds(startX, topButtonsY, buttonWidth, buttonHeight)
            .build();
        deleteButton = Button.builder(Component.translatable("dlc_manager.server.files_delete"), btn -> confirmDelete())
            .bounds(startX + buttonWidth + gap, topButtonsY, buttonWidth, buttonHeight)
            .build();
        saveButton = Button.builder(Component.translatable("dlc_manager.server.files_save"), btn -> saveSelected())
            .bounds(startX + (buttonWidth + gap) * 2, topButtonsY, buttonWidth, buttonHeight)
            .build();
        uploadButton = Button.builder(Component.translatable("dlc_manager.server.files_upload"), btn -> uploadFile())
            .bounds(startX + (buttonWidth + gap) * 3, topButtonsY, buttonWidth, buttonHeight)
            .build();

        int bottomY = this.height - 28;
        upButton = Button.builder(Component.translatable("dlc_manager.server.files_up"), btn -> goUp())
            .bounds(centerX - buttonWidth - 6, bottomY, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(centerX + 6, bottomY, buttonWidth, buttonHeight)
            .build();

        addRenderableWidget(refreshButton);
        addRenderableWidget(upButton);
        addRenderableWidget(uploadButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(deleteButton);
        addRenderableWidget(backButton);

        updateSelectionState();
        refresh();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, 16, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.server.files_path"), pathField.getX(), pathField.getY() - 10, 0xC8C8C8, false);
        renderList(guiGraphics);
        if (loading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.files_loading"), centerX, this.height - 62, 0xC8C8C8);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics guiGraphics) {
        int listTop = 90;
        int listBottom = this.height - 40;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int totalLines = entries.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int rowY = listTop;
        for (int i = 0; i < entries.size(); i++) {
            if (i < scrollIndex || i >= scrollIndex + maxVisible) {
                continue;
            }
            DlcServerFileEntry entry = entries.get(i);
            boolean selected = i == selectedIndex;
            if (selected) {
                int left = 24;
                int right = this.width - 24;
                int top = rowY + 1;
                int bottom = rowY + ROW_HEIGHT - 3;
                guiGraphics.fill(left, top, right, bottom, 0x33222222);
                guiGraphics.fill(left, top, right, top + 1, 0xFFFFFFFF);
                guiGraphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
                guiGraphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
                guiGraphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
            }
            String label = entry.dir ? (entry.name + "/") : entry.name;
            int color = entry.dir ? 0x6BA7FF : 0xE6E6E6;
            guiGraphics.drawString(this.font, label, 32, rowY + 4, color, false);
            if (!entry.dir) {
                String size = formatSize(entry.size);
                guiGraphics.drawString(this.font, size, this.width - 90, rowY + 4, 0x9A9A9A, false);
            }
            rowY += ROW_HEIGHT;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = 90;
        int listBottom = this.height - 40;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - maxVisible);
        if (startScrollDrag(mouseX, mouseY, listTop, listBottom, entries.size(), maxVisible, maxScroll)) {
            return true;
        }
        if (mouseY >= listTop && mouseY <= listBottom) {
            int index = (int) ((mouseY - listTop) / ROW_HEIGHT) + scrollIndex;
            if (index >= 0 && index < entries.size()) {
                long now = System.currentTimeMillis();
                if (index == lastClickIndex && now - lastClickTime <= 250) {
                    selectedIndex = index;
                    openSelected();
                    lastClickIndex = -1;
                    lastClickTime = 0L;
                    return true;
                }
                selectedIndex = index;
                lastClickIndex = index;
                lastClickTime = now;
                updateSelectionState();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            int listTop = 90;
            int listBottom = this.height - 40;
            int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
            int maxScroll = Math.max(0, entries.size() - maxVisible);
            updateScrollFromDrag(mouseY, listTop, listBottom, entries.size(), maxVisible, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(mouseX, mouseY, deltaY);
    }

    private boolean handleMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        int listTop = 90;
        int listBottom = this.height - 40;
        if (mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - maxVisible);
        if (maxScroll > 0) {
            int step = verticalAmount > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return false;
    }

    private void refresh() {
        if (loading) {
            return;
        }
        loading = true;
        String path = pathField.getValue().trim();
        CompletableFuture
            .supplyAsync(() -> loadEntries(path))
            .whenComplete((list, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (list != null) {
                    entries = list;
                    selectedIndex = -1;
                    updateSelectionState();
                }
            }));
    }

    private List<DlcServerFileEntry> loadEntries(String path) {
        try {
            return DlcServerConnection.listFiles(entry, 10000, path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void goUp() {
        String path = pathField.getValue().trim();
        if (path.isEmpty()) {
            return;
        }
        String normalized = path.replace("\\", "/");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            pathField.setValue("");
        } else {
            pathField.setValue(normalized.substring(0, idx));
        }
        refresh();
    }

    private void openSelected() {
        DlcServerFileEntry entry = getSelectedEntry();
        if (entry == null || !entry.dir) {
            return;
        }
        String path = pathField.getValue().trim();
        String next = path.isEmpty() ? entry.name : path + "/" + entry.name;
        pathField.setValue(next);
        refresh();
    }

    private void uploadFile() {
        Path file = DlcUpdateFilePicker.pickAnyFile(Component.translatable("dlc_manager.server.files_upload").getString());
        if (file == null) {
            return;
        }
        String path = pathField.getValue().trim();
        String target = path.isEmpty() ? file.getFileName().toString() : path + "/" + file.getFileName().toString();
        loading = true;
        CompletableFuture
            .supplyAsync(() -> uploadPath(target, file))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (ok != null && ok) {
                    refresh();
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.files_upload_failed"), Component.empty());
                }
            }));
    }

    private boolean upload(String target, Path file) {
        try {
            return DlcServerConnection.uploadFile(entry, 20000, target, file);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean uploadPath(String target, Path source) {
        try {
            if (source == null || !Files.exists(source)) {
                return false;
            }
            if (!Files.isDirectory(source)) {
                return upload(target, source);
            }
            Path base = source;
            final String baseTarget = target;
            return Files.walk(base)
                .filter(Files::isRegularFile)
                .allMatch(file -> {
                    Path relative = base.relativize(file);
                    String rel = relative.toString().replace("\\", "/");
                    String childTarget = baseTarget.isEmpty() ? rel : baseTarget + "/" + rel;
                    try {
                        return upload(childTarget, file);
                    } catch (Exception ignored) {
                        return false;
                    }
                });
        } catch (Exception ignored) {
            return false;
        }
    }

    private void saveSelected() {
        DlcServerFileEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        String basePath = pathField.getValue().trim();
        String remotePath = basePath.isEmpty() ? entry.name : basePath + "/" + entry.name;
        String fileName = entry.dir ? (entry.name + ".zip") : entry.name;
        Path target = DlcUpdateFilePicker.pickSaveFile(Component.translatable("dlc_manager.server.files_save").getString(), fileName);
        if (target == null) {
            return;
        }
        if (Files.isDirectory(target)) {
            target = target.resolve(fileName);
        } else if (entry.dir && !target.toString().endsWith(".zip")) {
            target = target.resolveSibling(target.getFileName().toString() + ".zip");
        }
        Path finalTarget = target;
        loading = true;
        CompletableFuture
            .supplyAsync(() -> download(remotePath, finalTarget))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (ok != null && ok) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.files_save_success"), Component.empty());
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.files_save_failed"), Component.empty());
                }
            }));
    }

    private boolean download(String remotePath, Path target) {
        try {
            return DlcServerConnection.downloadFile(entry, 20000, remotePath, target);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateSelectionState() {
        if (saveButton != null) {
            saveButton.active = getSelectedEntry() != null;
        }
    }

    private void confirmDelete() {
        DlcServerFileEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        Component title = Component.translatable("dlc_manager.server.files_delete");
        Component message = Component.translatable("dlc_manager.server.files_delete_confirm", entry.name);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                confirmDeleteAgain(entry);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void confirmDeleteAgain(DlcServerFileEntry entry) {
        Component title = Component.translatable("dlc_manager.server.files_delete");
        Component message = Component.translatable("dlc_manager.server.files_delete_confirm_again", entry.name);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                deleteSelected(entry);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void deleteSelected(DlcServerFileEntry entry) {
        String path = pathField.getValue().trim();
        String target = path.isEmpty() ? entry.name : path + "/" + entry.name;
        loading = true;
        CompletableFuture
            .supplyAsync(() -> delete(target))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (ok != null && ok) {
                    refresh();
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.files_delete_failed"), Component.empty());
                }
            }));
    }

    private boolean delete(String path) {
        try {
            return DlcServerConnection.deleteFile(entry, 15000, path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private DlcServerFileEntry getSelectedEntry() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            return entries.get(selectedIndex);
        }
        return null;
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 20;
        int barWidth = 6;
        int barHeight = Math.max(20, (bottom - top) * maxVisible / Math.max(1, totalLines));
        int trackHeight = bottom - top;
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
    }

    private boolean startScrollDrag(double mouseX, double mouseY, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible) {
            return false;
        }
        int barX = this.width - 20;
        int barWidth = 6;
        int trackHeight = bottom - top;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, totalLines));
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
            draggingScroll = true;
            scrollDragOffset = (int) (mouseY - barY);
            return true;
        }
        return false;
    }

    private void updateScrollFromDrag(double mouseY, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible || maxScroll <= 0) {
            scrollIndex = 0;
            return;
        }
        int trackHeight = bottom - top;
        int barHeight = Math.max(20, trackHeight * maxVisible / Math.max(1, totalLines));
        int minY = top;
        int maxY = bottom - barHeight;
        int barY = (int) Math.max(minY, Math.min(maxY, mouseY - scrollDragOffset));
        int range = Math.max(1, maxY - minY);
        double ratio = (barY - minY) / (double) range;
        scrollIndex = Math.max(0, Math.min(maxScroll, (int) Math.round(ratio * maxScroll)));
    }

    private String formatSize(long size) {
        if (size <= 0) {
            return "";
        }
        if (size > 1024 * 1024) {
            return String.format("%.1f MB", size / 1024.0 / 1024.0);
        }
        if (size > 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return size + " B";
    }
}
