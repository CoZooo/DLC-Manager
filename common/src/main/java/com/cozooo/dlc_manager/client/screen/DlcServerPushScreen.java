package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DlcServerPushScreen extends DlcScreenCompat.MouseScroll {
    private static final int LINE_HEIGHT = 10;
    private static final int CHECKBOX_SIZE = 8;
    private static final int CHECKBOX_X = 12;

    private final Screen parent;
    private final DlcServerEntry entry;
    private final Path rootDir;
    private Path currentDir;

    private Button modeButton;
    private Button pushButton;
    private Button backButton;
    private Button upButton;
    private Button clearButton;

    private boolean overwriteMode = true;
    private boolean pushing;
    private String statusText;
    private String errorText;
    private final Set<String> selectedFiles = new LinkedHashSet<>();
    private final Map<Path, List<String>> dirFilesCache = new HashMap<>();
    private List<FileEntry> entries = List.of();

    private int scrollIndex;
    private boolean draggingScrollBar;

    public DlcServerPushScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.server.push_title"));
        this.parent = parent;
        this.entry = entry;
        this.rootDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        this.currentDir = rootDir;
    }

    @Override
    protected void init() {
        int buttonHeight = 20;
        int bottomY = this.height - 28;
        int modeWidth = 96;
        int pushWidth = 96;
        int backWidth = 96;
        int gap = 6;

        Component modeLabel = Component.translatable("dlc_manager.server.push_mode");
        int modeLabelWidth = this.font.width(modeLabel);
        int modeX = 20 + modeLabelWidth + 6;
        modeButton = Button.builder(Component.empty(), btn -> toggleMode())
            .bounds(modeX, bottomY, modeWidth, buttonHeight)
            .build();

        int rightMargin = 10;
        int backX = this.width - rightMargin - backWidth;
        int pushX = backX - gap - pushWidth;
        pushButton = Button.builder(Component.translatable("dlc_manager.server.push_start"), btn -> confirmPush())
            .bounds(pushX, bottomY, pushWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, bottomY, backWidth, buttonHeight)
            .build();

        int topButtonY = 56;
        upButton = Button.builder(Component.translatable("dlc_manager.server.push_up"), btn -> goUp())
            .bounds(20, topButtonY, 70, buttonHeight)
            .build();
        clearButton = Button.builder(Component.translatable("dlc_manager.server.push_clear_selection", 0), btn -> clearSelection())
            .bounds(this.width - 20 - 140, topButtonY, 140, buttonHeight)
            .build();

        addRenderableWidget(modeButton);
        addRenderableWidget(pushButton);
        addRenderableWidget(backButton);
        addRenderableWidget(upButton);
        addRenderableWidget(clearButton);
        refreshEntries();
        updateLabels();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, 20, 0xFFFFFF);
        guiGraphics.drawString(this.font,
            Component.translatable("dlc_manager.server.push_current_dir", getCurrentDirLabel()),
            20, 36, 0xC8C8C8, false);
        if (!selectedFiles.isEmpty()) {
            guiGraphics.drawString(this.font,
                Component.translatable("dlc_manager.server.push_selected_count", selectedFiles.size()),
                20, 48, 0xC8C8C8, false);
        }
        if (errorText != null && !errorText.isBlank()) {
            guiGraphics.drawString(this.font, errorText, 20, 48, 0xE6E6E6, false);
        }

        int listTop = 80;
        int listBottom = this.height - 60;
        int maxVisible = Math.max(1, (listBottom - listTop) / LINE_HEIGHT);
        int totalLines = entries.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            FileEntry entry = entries.get(i);
            int rowY = listTop + (i - scrollIndex) * LINE_HEIGHT;
            SelectionState state = getEntryState(entry);
            drawCheckbox(guiGraphics, CHECKBOX_X, rowY + 1, CHECKBOX_SIZE, state);
            int color = entry.isDir ? 0x66A8FF : 0xE6E6E6;
            String label = entry.isDir ? (entry.name + "/") : entry.name;
            guiGraphics.drawString(this.font, label, CHECKBOX_X + CHECKBOX_SIZE + 6, rowY, color, false);
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible);

        Component modeLabel = Component.translatable("dlc_manager.server.push_mode");
        guiGraphics.drawString(this.font, modeLabel, 20, this.height - 23, 0xC8C8C8, false);
        if (pushing) {
            String text = statusText == null ? "" : statusText;
            guiGraphics.drawCenteredString(this.font, text, centerX, this.height - 48, 0xC8C8C8);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = 80;
            int listBottom = this.height - 60;
            int maxVisible = Math.max(1, (listBottom - listTop) / LINE_HEIGHT);
            int index = (int) ((mouseY - listTop) / LINE_HEIGHT) + scrollIndex;
            if (mouseY >= listTop && mouseY <= listBottom && index >= 0 && index < entries.size()) {
                FileEntry entry = entries.get(index);
                if (mouseX >= CHECKBOX_X && mouseX <= CHECKBOX_X + CHECKBOX_SIZE + 4) {
                    toggleEntry(entry);
                    return true;
                }
                if (entry.isDir) {
                    openDirectory(entry.path);
                    return true;
                }
            }
            int barX = this.width - 10;
            if (mouseX >= barX && mouseX <= barX + 6 && mouseY >= listTop && mouseY <= listBottom) {
                draggingScrollBar = true;
                updateScrollFromDrag(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScrollBar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollBar) {
            updateScrollFromDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(deltaY);
    }

    private boolean handleMouseScrolled(double deltaY) {
        if (deltaY != 0) {
            scrollIndex = Math.max(0, scrollIndex - (int) Math.signum(deltaY));
            return true;
        }
        return false;
    }

    private void updateScrollFromDrag(double mouseY) {
        int listTop = 80;
        int listBottom = this.height - 60;
        int total = entries.size();
        int visible = Math.max(1, (listBottom - listTop) / LINE_HEIGHT);
        int maxScroll = Math.max(0, total - visible);
        if (maxScroll <= 0) {
            scrollIndex = 0;
            return;
        }
        int barHeight = Math.max(10, (listBottom - listTop) * visible / total);
        int maxBarTravel = (listBottom - listTop) - barHeight;
        int relY = (int) mouseY - listTop - barHeight / 2;
        relY = Math.max(0, Math.min(maxBarTravel, relY));
        scrollIndex = (int) Math.round(relY * (maxScroll / (double) maxBarTravel));
    }

    private void toggleMode() {
        if (pushing) {
            return;
        }
        overwriteMode = !overwriteMode;
        updateLabels();
    }

    private void updateLabels() {
        if (modeButton != null) {
            Component modeLabel = overwriteMode
                ? Component.translatable("dlc_manager.server.push_mode_overwrite")
                : Component.translatable("dlc_manager.server.push_mode_diff");
            modeButton.setMessage(modeLabel);
            modeButton.active = !pushing;
        }
        if (pushButton != null) {
            pushButton.active = !pushing;
        }
        if (backButton != null) {
            backButton.active = !pushing;
        }
        if (clearButton != null) {
            clearButton.setMessage(Component.translatable("dlc_manager.server.push_clear_selection", selectedFiles.size()));
            clearButton.active = !pushing && !selectedFiles.isEmpty();
        }
        if (upButton != null) {
            upButton.active = !pushing && !currentDir.equals(rootDir);
        }
    }

    private void confirmPush() {
        if (selectedFiles.isEmpty()) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                Component.translatable("dlc_manager.server.push_no_selection"), Component.empty());
            return;
        }
        Component title = Component.translatable("dlc_manager.server.push_title");
        Component message = Component.translatable("dlc_manager.server.push_confirm", buildTargetLabel(), getModeLabel());
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                confirmPushAgain();
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmPushAgain() {
        Component title = Component.translatable("dlc_manager.server.push_title");
        Component message = Component.translatable("dlc_manager.server.push_confirm_again");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startPush();
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void startPush() {
        if (pushing) {
            return;
        }
        pushing = true;
        statusText = Component.translatable("dlc_manager.server.pushing").getString();
        updateLabels();
        CompletableFuture
            .supplyAsync(this::doPush)
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                pushing = false;
                if (ok != null && ok) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.push_success"), Component.empty());
                } else {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.push_failed"), Component.empty());
                }
                updateLabels();
            }));
    }

    private boolean doPush() {
        try {
            List<String> files = new ArrayList<>(selectedFiles);
            if (files.isEmpty()) {
                return false;
            }
            return DlcServerConnection.push(entry, 20000, List.of(), files, overwriteMode ? "overwrite" : "diff",
                CrossPlatformRuntime.playerName(), CrossPlatformRuntime.playerUuid());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String buildTargetLabel() {
        return Component.translatable("dlc_manager.server.push_selected_count", selectedFiles.size()).getString();
    }

    private String getModeLabel() {
        return overwriteMode
            ? Component.translatable("dlc_manager.server.push_mode_overwrite").getString()
            : Component.translatable("dlc_manager.server.push_mode_diff").getString();
    }

    private void refreshEntries() {
        entries = listEntries(currentDir);
        scrollIndex = 0;
        updateLabels();
    }

    private List<FileEntry> listEntries(Path dir) {
        List<FileEntry> result = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (path == null) {
                    continue;
                }
                boolean isDir = Files.isDirectory(path);
                String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
                result.add(new FileEntry(path, name, isDir));
            }
        } catch (Exception ignored) {
        }
        result.sort(Comparator.comparing((FileEntry entry) -> !entry.isDir).thenComparing(entry -> entry.name.toLowerCase()));
        return result;
    }

    private void openDirectory(Path dir) {
        if (dir == null) {
            return;
        }
        Path normalized = dir.toAbsolutePath().normalize();
        if (!normalized.startsWith(rootDir) || !Files.isDirectory(normalized)) {
            return;
        }
        currentDir = normalized;
        refreshEntries();
    }

    private void goUp() {
        if (currentDir == null || currentDir.equals(rootDir)) {
            return;
        }
        Path parentDir = currentDir.getParent();
        if (parentDir == null || !parentDir.startsWith(rootDir)) {
            return;
        }
        currentDir = parentDir;
        refreshEntries();
    }

    private SelectionState getEntryState(FileEntry entry) {
        if (entry == null) {
            return SelectionState.NONE;
        }
        if (!entry.isDir) {
            String rel = toRelative(entry.path);
            return rel != null && selectedFiles.contains(rel) ? SelectionState.ALL : SelectionState.NONE;
        }
        List<String> files = getDirFiles(entry.path);
        if (files.isEmpty()) {
            return SelectionState.NONE;
        }
        boolean anySelected = false;
        for (String file : files) {
            if (selectedFiles.contains(file)) {
                anySelected = true;
            } else if (anySelected) {
                return SelectionState.PARTIAL;
            }
        }
        if (anySelected) {
            return SelectionState.ALL;
        }
        return SelectionState.NONE;
    }

    private void toggleEntry(FileEntry entry) {
        if (entry == null) {
            return;
        }
        if (!entry.isDir) {
            String rel = toRelative(entry.path);
            if (rel == null || rel.isBlank()) {
                return;
            }
            if (!selectedFiles.add(rel)) {
                selectedFiles.remove(rel);
            }
            updateLabels();
            return;
        }
        List<String> files = getDirFiles(entry.path);
        if (files.isEmpty()) {
            return;
        }
        boolean allSelected = true;
        for (String file : files) {
            if (!selectedFiles.contains(file)) {
                allSelected = false;
                break;
            }
        }
        if (allSelected) {
            selectedFiles.removeAll(files);
        } else {
            selectedFiles.addAll(files);
        }
        updateLabels();
    }

    private List<String> getDirFiles(Path dir) {
        if (dir == null) {
            return List.of();
        }
        List<String> cached = dirFilesCache.get(dir);
        if (cached != null) {
            return cached;
        }
        List<String> files = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = toRelative(path);
                if (rel != null && !rel.isBlank()) {
                    files.add(rel);
                }
            });
        } catch (Exception ignored) {
        }
        dirFilesCache.put(dir, files);
        return files;
    }

    private String toRelative(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(rootDir)) {
            return null;
        }
        Path relative = rootDir.relativize(normalized);
        String rel = relative.toString();
        if (rel.isBlank()) {
            return null;
        }
        return rel.replace('\\', '/');
    }

    private void clearSelection() {
        selectedFiles.clear();
        updateLabels();
    }

    private String getCurrentDirLabel() {
        if (currentDir == null || currentDir.equals(rootDir)) {
            return "/";
        }
        String rel = rootDir.relativize(currentDir).toString().replace('\\', '/');
        return "/" + rel;
    }

    private void drawCheckbox(GuiGraphics guiGraphics, int x, int y, int size, SelectionState state) {
        boolean checked = state == SelectionState.ALL;
        int color = checked ? 0xFF66FF66 : 0xFF777777;
        guiGraphics.fill(x, y, x + size, y + size, color);
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF000000);
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF66FF66);
        } else if (state == SelectionState.PARTIAL) {
            int barY = y + size / 2 - 1;
            guiGraphics.fill(x + 2, barY, x + size - 2, barY + 2, 0xFFBBBBBB);
        }
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int total, int visible) {
        if (total <= visible) {
            return;
        }
        int barHeight = Math.max(10, (bottom - top) * visible / total);
        int barX = this.width - 10;
        int maxScroll = total - visible;
        int barY = top + (bottom - top - barHeight) * scrollIndex / Math.max(1, maxScroll);
        guiGraphics.fill(barX, top, barX + 6, bottom, 0xFF2B2B2B);
        guiGraphics.fill(barX, barY, barX + 6, barY + barHeight, 0xFFCCCCCC);
    }
    private static final class FileEntry {
        private final Path path;
        private final String name;
        private final boolean isDir;

        private FileEntry(Path path, String name, boolean isDir) {
            this.path = path;
            this.name = name;
            this.isDir = isDir;
        }
    }

    private enum SelectionState {
        NONE,
        PARTIAL,
        ALL
    }
}
