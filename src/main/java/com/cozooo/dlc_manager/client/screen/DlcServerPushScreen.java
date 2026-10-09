package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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

public class DlcServerPushScreen extends DlcFilePageScreen {




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
        super(DlcUiPage.SERVER_PUSH, parent, Component.translatable("dlc_manager.server.push_title"));
        this.parent = parent;
        this.entry = entry;
        this.rootDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        this.currentDir = rootDir;
    }

    private void initControls() {
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
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
                DlcPlatformServices.playerName(), DlcPlatformServices.playerUuid());
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
        long count = files.stream().filter(selectedFiles::contains).count();
        return count == 0 ? SelectionState.NONE : count == files.size() ? SelectionState.ALL : SelectionState.PARTIAL;
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

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash(entries, pushing);

        mount(toolbar, upButton); mount(toolbar, clearButton);
        var list = fileList(getCurrentDirLabel());
        for (var file : entries) {
            fileRow(list, file.name + (file.isDir ? "/" : ""), null,
                () -> switch (getEntryState(file)) { case ALL -> "true"; case PARTIAL -> "mixed"; case NONE -> "false"; },
                () -> !pushing, () -> toggleEntry(file), file.isDir ? () -> openDirectory(file.path) : null);
        }
        live(content, () -> statusText, "page-description"); live(content, () -> errorText, "page-error"); mount(footer, modeButton); mount(footer, pushButton).getElement().setAttribute("class", "button primary");

    }
    @Override protected void updatePage() {
        updateLabels();
        if (pageRevision != java.util.Objects.hash(entries, pushing)) refreshPage();
        super.updatePage();
    }

    @Override public boolean shouldCloseOnEsc() { return !pushing; }
}
